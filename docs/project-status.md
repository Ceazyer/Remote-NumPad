# 项目进度与导航

## 当前基线

- 已确认正式版：Windows / Android **1.3.0**（Android versionCode 6），来源：`RemoteNumPad.csproj`、`android/app/build.gradle.kts`。
- 1.3.0 源码已推送至 `origin/main`，该版本一次推送授权已使用。本次文档整理不是新正式版，不触发再次推送。
- Windows 原生 GUI、托盘/显式退出、端口修改/冲突回退、网卡选择、二维码刷新、单实例与状态日志已实现。
- 安卓是 Kotlin 原生端，不是套壳；SQLite FIFO、单条在途、序号/ACK、人工恢复、扫码与可选轻触反馈已实现。网页仍是裸命令兼容，没有同等持久队列保障。
- 网页/安卓统一黑白软拟物双页、确认的计算器数字布局、向心五边形方向键、等尺寸四宫格辅助区与 Light 图标。

## 封包与本机文件

- `publish/v1.3.0/` 保留 `RemoteNumPad-1.3.0-release.apk`、`RemoteNumPad-1.3.0-windows-x64.zip`、`android-symbols/mapping.txt`、构建元数据及 `previews/`。
- 当前没有保留解压后的 `windows/RemoteNumPad.exe`，运行请解压 ZIP；构建脚本可以重新生成该目录。
- 历史封包与无关构建缓存已清理，源码、SDK/Gradle/模拟器工具和私有签名保留。不要为核对文档重新构建缓存。
- Android 正式签名沿用现有密钥，私钥/密码不得读取或上传；见 [签名备份](release-signing.md)。Windows 未配置商业 Authenticode 签名。
- 源码已推送不等于创建了 GitHub Release/上传了二进制附件；没有已创建公开二进制 Release 的记录。

## 验证与待验收

- 已有 .NET、Android JVM/模拟器、浏览器布局、GUI 与正式 APK 签名历史验证；见 [验证台账](verification-log.md)，不是本次新跑结果。
- 真机待验收：高速实体触摸、相机对焦、震动手感、真实网络中断、Excel/WPS 焦点与单元格结果。合成点击/ACK 不代替这些验收。
- `ISSUE-SEQ-CLEAR`：源码审阅发现清空未被同一 PC 进程消费的队列项可能造成序号断档；未执行回归复现/修复。见 [协议限制](android-protocol.md)，不要用反复重试/清空掩盖问题。
- 当前仅整理 Agent 导航及开发/验证/推送规则，无功能代码变化和新版本封包。后续修复同步更新 Unreleased 与对应主文档。

## 范围与授权

- **飞书开发取消**：仅评估讨论，未实施飞书应用、云转发或飞书长连接；不要继续。
- iOS 仅讨论、未立项；极差计算提案已取消，不替换当前公式。
- 日常修复/小功能不自动 push。用户明确确认具体正式版本即授权该版本一次源码 push，无需再下令；用户也可独立手动要求 push。

## 按需导航

根目录 [AGENTS.md](../AGENTS.md) 是入口，不需每次加载全部历史。

| 事项 | 主文档 |
|---|---|
| 安装、连接、使用 | [README](../README.md)、[安卓验收](android-testing.md) |
| 产品约束、修复与发布门槛 | [开发规则](development-rules.md) |
| 验证证据、失效与补测 | [验证台账](verification-log.md) |
| v2 协议与重放边界 | [协议](android-protocol.md) |
| 键位、主题、触控 | [UI 规范](soft-ui-design.md) |
| 正式版更新/迁移 | [1.3.0 更新](release-1.3.0.md) |
| 签名/许可 | [签名](release-signing.md)、[第三方声明](../THIRD_PARTY_NOTICES.md) |
| 日常摘要/版本历史 | [CHANGELOG](../CHANGELOG.md) |
