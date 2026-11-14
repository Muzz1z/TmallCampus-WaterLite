# TmallCampus-WaterLite · 扫码接水

针对 **天猫校园 5.7.2** 的 Android 界面精简模块。打开应用只显示“开始扫码”和必要的“账户与设置”，保留原版扫码、登录和用水服务流程。

Unofficial UI-only module for Tmall Campus 5.7.2. Keep the original water-service flow behind a minimal scanner entry.

## 功能

- 精简首页，隐藏商城、社区和其他首页入口。
- 跳过冷启动广告、关闭热启动广告页，停用已定位的广告初始化任务和开关。
- 隐藏扫码页广告、推广图标和功能切换栏。
- 保留原版登录、扫码结果处理和设备服务页面；不新增权限，不自动建单、接水或付款。

这是原版应用的界面精简模块，原版组件仍然保留，不是从零重写的小体积独立客户端。服务端下发页面的全部广告、未来版本和其他学校的设备兼容性没有保证。

## 下载与适配

[下载独立模块 APK](./WaterLite-Module-0.1.0.apk) · [下载文件校验值](./SHA256SUMS) · [修改位置校验](./hook-verification.json)

独立模块 **不能单独接水**。已使用 LSPosed 的用户可将模块作用于 `com.tmall.campus.and` 后强制停止并重启目标应用；此安装方式未单独做真机验证。不建议仅为本模块去 root 手机。

免 root 用户可自行提供原版 APK，在本机使用下述脚本生成内嵌模块的安装包。仓库不包含原版 APK、重打包的完整天猫校园 APK、账户数据、手机日志或签名私钥。

| 项目 | 已验证范围 |
| --- | --- |
| 原版应用 | 5.7.2 / versionCode 50007002 |
| 原版 APK SHA-256 | `0369A1E2DC0820C41036766D7224E97189DF0E2911ECA1669D7EA90E9D50DCF8` |
| 真机环境 | Android 16；单台手机、单校设备 |
| 免 root 方式 | JingMatrix/LSPatch v1.2，签名兼容级别 2 |
| 静态检查 | 15 处方法位置已在原版 DEX 中确认；APK 签名检查通过 |
| 真机检查 | 启动、登录、精简首页、相机扫码；测试者已确认正常接水并结束 |
| 未核对 | 具体扣费账单、其他 Android 版本及其他学校设备 |

校验记录来自 2026-11-14 的本机测试，不代表适配所有 5.7.2 安装包。

## Windows 本地构建

需要 **JDK 21**、Android SDK Platform 36 和 Build Tools 36.0.0。可通过 Android Studio 的 SDK Manager 安装 Android 组件。设置 `JAVA_HOME` 和 `ANDROID_SDK_ROOT` 后，在 PowerShell 中运行：

```powershell
# 下载编译接口、ECJ 和 LSPatch CLI；逐项校验固定 SHA-256
.\Get-Dependencies.ps1

# 仅构建独立模块，输出到 dist/
.\Build.ps1

# 使用自己合法取得的已验证原版 APK 生成免 root 安装包
.\Build.ps1 -OriginalApk 'C:\path\to\original.apk'
```

所有路径都可通过参数指定：

```powershell
.\Build.ps1 -JavaHome 'C:\path\to\jdk-21' `
  -AndroidJar 'C:\path\to\android.jar' `
  -BuildToolsDirectory 'C:\path\to\build-tools\36.0.0'
```

脚本只接受上述已验证 SHA-256 的原 APK。其他版本须重新检查类名、方法、资源和业务行为后再适配，不应直接关闭校验继续打包。

首次构建会在 `.local/keys/` 生成本地签名密钥和密码。它们已加入 `.gitignore`，请保留在本机用于后续同签名更新。使用自己的已有密钥时，可通过 `-Keystore` 和环境变量 `WATERLITE_SIGN_PASS` 提供，别把密码写进命令行或提交到仓库。

## 安装与恢复

免 root 重打包版本保留原包名，但签名不同，通常不能直接覆盖官方版。**卸载会清除本机应用数据和登录态，需要重新登录。** 安装前请自行保存官方安装包，并确认可以正常登录。

恢复时需卸载重打包版本，再通过官方渠道重新安装并登录。普通 APK 备份不能恢复原来的登录态。用水和支付始终由使用者在原服务页面确认。

## 源码结构

- `WaterLite.java`：界面和广告入口的 Hook，不接管账户、设备或支付接口。
- `AndroidManifest.xml`、`xposed_init`：独立模块声明与入口。
- `Build.ps1`：编译、签名及可选本地重打包。
- `Get-Dependencies.ps1`：固定版本的依赖下载与摘要校验。
- `hook-verification.json`：已验证原版中的方法签名。

## 依赖与许可

本项目编写的模块和脚本使用 [MIT License](./LICENSE)。MIT 许可不覆盖天猫校园程序、商标或第三方依赖。

- [JingMatrix/LSPatch v1.2](https://github.com/JingMatrix/LSPatch/releases/tag/v1.2)：免 root 补丁框架，按其仓库许可使用。
- [Eclipse ECJ](https://eclipse.dev/jdt/core/index.php)：Java 编译器，按 Eclipse 项目许可使用。
- [Android SDK](https://developer.android.com/tools)：编译平台和构建工具，按 Android SDK 条款使用。
- Xposed 编译接口包下载自 [CleanTMXY 的接口文件](https://github.com/xunfen/CleanTMXY/blob/main/build/xposed-api.jar)，使用固定摘要校验，仅用于编译，不将其实现类打进模块。Xposed 接口的上游为 [XposedBridge](https://github.com/rovo89/XposedBridge)。

非官方个人项目，与天猫校园及其服务商没有隶属或合作关系。反馈时请提供应用版本、系统版本和脱敏后的报错文字，不要上传 Cookie、验证码、账号凭证、完整订单信息或签名密钥。
