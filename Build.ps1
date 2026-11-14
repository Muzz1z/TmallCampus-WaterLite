[CmdletBinding()]
param(
    [string]$OriginalApk,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AndroidSdk = $env:ANDROID_SDK_ROOT,
    [string]$AndroidJar,
    [string]$BuildToolsDirectory,
    [string]$XposedApiJar = (Join-Path $PSScriptRoot '.tools\xposed-api.jar'),
    [string]$EcjJar = (Join-Path $PSScriptRoot '.tools\ecj.jar'),
    [string]$LspatchJar = (Join-Path $PSScriptRoot '.tools\lspatch.jar'),
    [string]$Keystore = (Join-Path $PSScriptRoot '.local\keys\waterlite.p12'),
    [string]$OutputDirectory = (Join-Path $PSScriptRoot 'dist')
)
$ErrorActionPreference = 'Stop'
$previousSigningPassword = $env:WATERLITE_SIGN_PASS
try {
    if (-not $JavaHome) { throw 'Set JAVA_HOME or provide -JavaHome pointing to JDK 21.' }
    if (-not $AndroidSdk) { $AndroidSdk = $env:ANDROID_HOME }
    if (-not $AndroidJar) {
        if (-not $AndroidSdk) { throw 'Set ANDROID_SDK_ROOT or provide -AndroidJar and -BuildToolsDirectory.' }
        $AndroidJar = Join-Path $AndroidSdk 'platforms\android-36\android.jar'
    }
    if (-not $BuildToolsDirectory) {
        if (-not $AndroidSdk) { throw 'Provide -BuildToolsDirectory.' }
        $BuildToolsDirectory = Join-Path $AndroidSdk 'build-tools\36.0.0'
    }
    $java = Join-Path $JavaHome 'bin\java.exe'
    $jar = Join-Path $JavaHome 'bin\jar.exe'
    $keytool = Join-Path $JavaHome 'bin\keytool.exe'
    $d8 = Join-Path $BuildToolsDirectory 'lib\d8.jar'
    $apksigner = Join-Path $BuildToolsDirectory 'lib\apksigner.jar'
    $aapt2 = Join-Path $BuildToolsDirectory 'aapt2.exe'
    foreach ($required in @($java, $jar, $keytool, $AndroidJar, $XposedApiJar, $EcjJar, $d8, $apksigner, $aapt2)) {
        if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw ('Missing required file: ' + $required) }
    }
    if ($OriginalApk) {
        $OriginalApk = (Resolve-Path -LiteralPath $OriginalApk).Path
        $expectedHash = '0369A1E2DC0820C41036766D7224E97189DF0E2911ECA1669D7EA90E9D50DCF8'
        if ((Get-FileHash -LiteralPath $OriginalApk -Algorithm SHA256).Hash -ne $expectedHash) { throw 'APK differs from the verified 5.7.2 input. Audit the new version before patching.' }
        if (-not (Test-Path -LiteralPath $LspatchJar -PathType Leaf)) { throw 'LSPatch CLI is missing. Run Get-Dependencies.ps1.' }
    }
    $build = Join-Path $PSScriptRoot ('.local\build\' + [guid]::NewGuid().ToString('N'))
    $classes = Join-Path $build 'classes'
    $dex = Join-Path $build 'dex'
    $assets = Join-Path $build 'assets'
    New-Item -ItemType Directory -Force -Path $classes,$dex,$assets,$OutputDirectory | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'xposed_init') -Destination (Join-Path $assets 'xposed_init')
    & $java -jar $EcjJar -encoding UTF-8 -source 8 -target 8 -classpath ($AndroidJar + ';' + $XposedApiJar) -d $classes (Join-Path $PSScriptRoot 'WaterLite.java')
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
    if (-not (Test-Path -LiteralPath (Join-Path $classes 'local\waterlite\module\WaterLite.class'))) { throw 'Compiled module entry is missing.' }
    $classJar = Join-Path $build 'module-classes.jar'
    & $jar cf $classJar -C $classes .
    if ($LASTEXITCODE -ne 0) { throw 'Class packaging failed.' }
    & $java -cp $d8 com.android.tools.r8.D8 --min-api 26 --lib $AndroidJar --lib $XposedApiJar --output $dex $classJar
    if ($LASTEXITCODE -ne 0) { throw 'DEX compilation failed.' }
    $unsigned = Join-Path $build 'unsigned.apk'
    & $aapt2 link -o $unsigned -I $AndroidJar --manifest (Join-Path $PSScriptRoot 'AndroidManifest.xml') -A $assets
    if ($LASTEXITCODE -ne 0) { throw 'Manifest packaging failed.' }
    & $jar uf $unsigned -C $dex classes.dex
    if ($LASTEXITCODE -ne 0) { throw 'DEX packaging failed.' }
    $keyDirectory = Split-Path -Parent $Keystore
    New-Item -ItemType Directory -Force -Path $keyDirectory | Out-Null
    $localPassword = Join-Path $PSScriptRoot '.local\keys\signing-password.txt'
    if (-not (Test-Path -LiteralPath $Keystore)) {
        if (-not $env:WATERLITE_SIGN_PASS) {
            $keyBytes = New-Object byte[] 32
            $random = [Security.Cryptography.RandomNumberGenerator]::Create()
            try { $random.GetBytes($keyBytes) } finally { $random.Dispose() }
            $env:WATERLITE_SIGN_PASS = [Convert]::ToBase64String($keyBytes)
            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $localPassword) | Out-Null
            [IO.File]::WriteAllText($localPassword, $env:WATERLITE_SIGN_PASS)
        }
        & $keytool -genkeypair -keystore $Keystore -storetype PKCS12 -storepass:env WATERLITE_SIGN_PASS -keypass:env WATERLITE_SIGN_PASS -alias waterlite -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=WaterLite Personal Build'
        if ($LASTEXITCODE -ne 0) { throw 'Key generation failed.' }
    } elseif (-not $env:WATERLITE_SIGN_PASS) {
        if (-not (Test-Path -LiteralPath $localPassword)) { throw 'Set WATERLITE_SIGN_PASS for the selected existing keystore.' }
        $env:WATERLITE_SIGN_PASS = [IO.File]::ReadAllText($localPassword).Trim()
    }
    $module = Join-Path $OutputDirectory 'WaterLite-Module-0.1.0.apk'
    & $java -jar $apksigner sign --ks $Keystore --ks-key-alias waterlite --ks-pass env:WATERLITE_SIGN_PASS --out $module $unsigned
    if ($LASTEXITCODE -ne 0) { throw 'Module signing failed.' }
    & $java -jar $apksigner verify --verbose $module
    if ($LASTEXITCODE -ne 0) { throw 'Module verification failed.' }
    if ($OriginalApk) {
        $patchedDirectory = Join-Path $build 'patched'
        $displayName = -join [char[]]@(0x626b, 0x7801, 0x63a5, 0x6c34)
        & $java -jar $LspatchJar -m $module -l 2 --name $displayName -k $Keystore $env:WATERLITE_SIGN_PASS waterlite $env:WATERLITE_SIGN_PASS -o $patchedDirectory $OriginalApk
        if ($LASTEXITCODE -ne 0) { throw 'Patching failed.' }
        $patchedFiles = @(Get-ChildItem -LiteralPath $patchedDirectory -Filter '*.apk')
        if ($patchedFiles.Count -ne 1) { throw 'Unexpected patch output count.' }
        $patched = Join-Path $OutputDirectory 'WaterLite-TmallCampus-5.7.2-0.1.0.apk'
        Copy-Item -LiteralPath $patchedFiles[0].FullName -Destination $patched
        & $java -jar $apksigner verify --verbose $patched
        if ($LASTEXITCODE -ne 0) { throw 'Patched APK verification failed.' }
        Get-FileHash -LiteralPath $patched -Algorithm SHA256
    }
    Get-FileHash -LiteralPath $module -Algorithm SHA256
    Write-Host ('Build complete: ' + $OutputDirectory)
} finally {
    $env:WATERLITE_SIGN_PASS = $previousSigningPassword
}
