# 更新日志 / Changelog

<!--
发布说明的来源。scripts/ci-build.sh 会把下面第一个 "## " 小节原样放进 GitHub Release 正文，
后面再自动附上安装说明和构建信息。发布前把这一节写好，两种语言都要写。

规则：
- 只有第一个 "## " 小节会被发布，历史版本往下放。
- 面向使用者写：说清楚"能看到什么变化"，不要写提交哈希、任务名或内部重构。
- 中英文条目一一对应，顺序一致。
- 小节为空或文件缺失时，release 构建会在 Gradle 启动前失败，不会发出空白说明。
- 发布后把 "未发布 / Unreleased" 改成版本号，并在上面新建一个空的未发布小节。

This file is the source of the release body. ci-build.sh copies the first "## " section into
the GitHub Release notes and appends the install notes and the build information. Keep both
languages in sync; an empty section fails the release build before Gradle starts.
-->

## 未发布 / Unreleased

### 本次更新

- **更稳定**：修复了平板日程卡片在部分屏幕配置下取不到文字宽度资源的问题，避免由此引发的显示异常。
- **更干净**：清理了已下线的"导出到 SD 卡"遗留文案。导出事件现在统一使用系统文件选择器，可保存到任意位置。
- **更可靠的安装包**：正式包在构建前就会校验签名配置，并在打包后核对签名证书指纹，确保你下载到的安装包来自同一把发布密钥。

### What's new

- **More stable**: fixed the tablet agenda card failing to resolve its text-width resource in some screen configurations, which could lead to display glitches.
- **Cleaner**: removed the leftover "Export to SD card" strings. Exporting an event now always uses the system file picker, so you can save anywhere.
- **A more trustworthy package**: release builds now verify the signing setup before building and check the signing certificate afterwards, so every download comes from the same release key.
