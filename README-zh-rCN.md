# Etar 日历
Etar（源自阿拉伯语: `إِيتَار`）是一款为每个人设计的开源 Material Design 日历！

[<img src="metadata/en_fdroid.png" height="60" alt="在 F-Droid 上获取">](https://f-droid.org/packages/ws.xsoh.etar/)[<img src="metadata/en_google_play.png" height="60" alt="在 Google Play 上获取">](https://play.google.com/store/apps/details?id=ws.xsoh.etar)

> **Release 版本是另一个应用。** 本仓库的 Release 包名为 `org.lineageos.etar`，使用 release 签名。调试版包名为 `org.lineageos.etar.debug`。从调试版切换到 release 版需要先卸载调试版，数据会被删除。此前的 Release 1.0（`ws.xsoh.etar.debug`）是另一个包，可以同时保留。

> **注意：** 上方的商店链接指向上游 Etar（`ws.xsoh.etar`）。本仓库的 Release 使用 `org.lineageos.etar`，无法更新或替换该应用。

![Etar 日历](metadata/animation.gif)

## 为什么？
我想要一个简单、遵循 Material Design、先进且任何人都可以改进的开源日历。

## 特别感谢

该应用程序是 AOSP 日历的增强版。没有 [Free Software for Android](https://github.com/Free-Software-for-Android/Standalone-Calendar) 团队的帮助，这个应用可能只会是一个梦想。所以，非常感谢他们！

## 功能
- 月视图。
- 周、日和日程视图。
- 使用 Android 日历存储来显示所有已同步的日历。
- Material Design。
- 深色和浅色主题。
- 支持离线日历。
- 日程小部件。
- 纪念日和倒数日事件类型。
- 支持通过 ICS 共享日历，并能够从 SD 卡导入/导出。
- 多语言 UI。

## 如何使用 Etar
仅在手机上存储您的日历：
  - 创建一个离线日历。

将您的日历同步到服务器：
  - 云同步的日历可以是 Google 日历，但您也可以使用任何其他公共 CalDAV 服务器，甚至托管您自己的服务器（这将是完全控制您的数据，并且仍然可以在不同设备上使用同一个日历的唯一方法）。要将这样的日历同步到某个服务器，您需要另一个应用，例如 DAVx5。这是必要的，因为 Etar 不包含 CalDAV 客户端。

### 技术说明
在 Android 上有“日历提供者”。这些可以是与云服务同步的日历或本地日历。基本上任何应用都可以提供一个日历。Etar 可以使用那些“提供”的日历。您甚至可以在 Etar 中配置显示哪些日历，以及在添加事件时应将其添加到哪个日历。

### Etar 需要的重要权限
- READ_EXTERNAL_STORAGE 和 WRITE_EXTERNAL_STORAGE
-> 导入和导出 ics 日历文件
- READ_CONTACTS (可选)
  首次创建约会时会查询，可以拒绝。但之后搜索和位置建议将不再有效。
-> 允许在向活动添加访客时提供搜索和位置建议
- READ_CALENDAR 和 WRITE_CALENDAR
-> 读取和创建日历事件

### ICS 导入功能的已知问题

Etar 可以导入 ICS 文件，例如，从邀请邮件中收到的文件。
导入功能不稳定，并存在一些已知错误。
有关这些错误的参考，请参阅 https://github.com/Etar-Group/Etar-Calendar/pull/653。
请谨慎使用，特别是当您的日历提供商会自动发送邀请邮件时。

## 贡献
### 翻译
有兴趣帮助翻译 Etar 吗？请在这里贡献：https://hosted.weblate.org/projects/etar-calendar/strings/

##### Google Play 应用描述：
您可以在[这里](metadata)更新/添加您自己的语言和所有艺术作品文件。

### 构建说明
安装并解压 Android SDK 命令行工具。
```
tools/bin/sdkmanager platform-tools
export ANDROID_HOME=/path/to/android-sdk/
gradle :app:assembleDebug
```
调试版包名为 `org.lineageos.etar.debug`，使用本机的调试签名。其他机器构建的调试版可能无法覆盖安装。

### 构建与发布
Release APK 由 [docs/WORKFLOW.md](docs/WORKFLOW.md) 中描述的 GitHub 工作流构建。本地构建不需要这些配置。

- **Secrets**（仓库设置）：`KEYSTORE_BASE64`（keystore 文件的 base64）、`KEYSTORE_PASS`、`KEY_ALIAS`、`KEY_PASS`。
- **Keystore**，由所有者创建一次：
  ```
  keytool -genkeypair -v -keystore release.jks -alias gkui-release -keyalg RSA -keysize 4096 -validity 10000
  base64 -w0 release.jks
  ```
  请妥善保管文件和口令。丢失后只能换新密钥，已安装的版本将无法升级。
- **`release-signer.sha256`** 位于仓库根目录，内容为 release 证书的 SHA-256（64 位十六进制）。发布脚本会拒绝由其他密钥签名的 APK。由所有者在首次运行打印该值后写入一次。
- **versionCode** 为 `git rev-list --count HEAD`，即提交数。提交数只会增长，因此每个版本都比上一个新。这需要完整克隆（`fetch-depth: 0`）。**versionName** 为 `yyyyMMdd.<versionCode>`，可由 `VERSION_NAME` 变量覆盖。
- **本地构建不受影响。** `gradle :app:assembleDebug` 不需要任何 secret。若缺少四个变量，本地的 `assembleRelease` 会使用调试签名并打印警告，此类 APK 不要分发。CI 在缺少 release 密钥时拒绝构建 release。
- **Release 产物：** 包名 `org.lineageos.etar`，不可调试，文件为 `GKUICalendar-<versionName>.apk`、对应的 `.sha256` 和 `build-info.json`。

## 许可证

Copyright (c) 2005-2013, The Android Open Source Project

Copyright (c) 2013, Dominik Schürmann

Copyright (c) 2015-, The Etar Project

在 GPLv3 下许可：https://www.gnu.org/licenses/gpl-3.0.html
除非另有说明。

Google Play 和 Google Play 徽标是 Google Inc. 的商标。
