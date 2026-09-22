# Changelog

## [0.2.0] - 2026-09-22

### Added

- Excel 录入面板的中文移动端界面。
- 数字、小数点、减号、退格、清空、编辑和 Enter 操作。
- 单元格方向导航、上一格、下一格、撤销、复制、粘贴和自动求和。
- 平均值、最大值、最小值、四舍五入公式输入。
- 语义化 WebSocket 命令和 Unicode 文本输入。

### Fixed

- 修复“下一格”命令未发送 TAB 的问题。
- 公式输入避免残留 Shift 状态。

### Distribution

- Windows x64 自包含单文件发布：`publish/win-x64/RemoteNumPad.exe`。
- 发布内容不包含内部开发说明、NuGet 配置、构建缓存或本地隐私文件。
