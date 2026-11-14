[CmdletBinding()]
param([string]$Destination = (Join-Path $PSScriptRoot '.tools'))
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $Destination | Out-Null
$downloads = @(
    @{ Name = 'ecj.jar'; Url = 'https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.46.100/ecj-3.46.100.jar'; Sha256 = '74c93172475fee0cd4e6eefb44233516dd40525642da35d009146d97ea598229' },
    @{ Name = 'xposed-api.jar'; Url = 'https://raw.githubusercontent.com/xunfen/CleanTMXY/main/build/xposed-api.jar'; Sha256 = '8991ef645f3a8ca0e8060c94d7d5cbc204853aec31c2cbef0b9b83d1ff104ea4' },
    @{ Name = 'lspatch.jar'; Url = 'https://github.com/JingMatrix/LSPatch/releases/download/v1.2/lspatch-v1.2-487-release.jar'; Sha256 = 'd238fdc414d121b7fa454d8b4ccf420df3a8c97d563761861ff92bd9c5da2165' }
)
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
foreach ($item in $downloads) {
    $target = Join-Path $Destination $item.Name
    if ((Test-Path -LiteralPath $target) -and ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -eq $item.Sha256)) {
        Write-Host ($item.Name + ': already verified')
        continue
    }
    $temporary = Join-Path $Destination ($item.Name + '.' + [guid]::NewGuid().ToString('N') + '.download')
    Invoke-WebRequest -Uri $item.Url -OutFile $temporary -UseBasicParsing
    if ((Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash -ne $item.Sha256) {
        throw ('Checksum mismatch: ' + $item.Name + '. The untrusted download remains at ' + $temporary)
    }
    Move-Item -LiteralPath $temporary -Destination $target -Force
    Write-Host ($item.Name + ': SHA-256 verified')
}
