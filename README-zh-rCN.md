# Etar 日历
Etar（源自阿拉伯语: `إِيتَار`）是一款为每个人设计的开源 Material Design 日历！

[<img src="metadata/en_fdroid.png" height="60" alt="在 F-Droid 上获取">](https://f-droid.org/packages/ws.xsoh.etar/)[<img src="metadata/en_google_play.png" height="60" alt="在 Google Play 上获取">](https://play.google.com/store/apps/details?id=ws.xsoh.etar)

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
## 许可证

Copyright (c) 2005-2013, The Android Open Source Project

Copyright (c) 2013, Dominik Schürmann

Copyright (c) 2015-, The Etar Project

在 GPLv3 下许可：https://www.gnu.org/licenses/gpl-3.0.html
除非另有说明。

Google Play 和 Google Play 徽标是 Google Inc. 的商标。
