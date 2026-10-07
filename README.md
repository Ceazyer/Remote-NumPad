# Remote NumPad — Windows GUI 接收端 / Android 原生端 v1.3.0

Remote NumPad 是一个仅供局域网使用的 Excel/WPS 录入面板。电脑运行 ASP.NET Core 服务，Windows 端使用 `SendInput` 将操作交给当前活动窗口。手机可使用原有浏览器端，或安装 Android 原生 APK；原生端不是 WebView 套壳。

开发入口见 [AGENTS.md](AGENTS.md)，当前进度见 [项目状态](docs/project-status.md)，开发与发布规则见 [开发规则](docs/development-rules.md)，已有证据见 [验证台账](docs/verification-log.md)。按任务读取相关文档即可，不需重复加载全部历史。

## 运行 Windows 接收端 v1.3.0

Windows x64 自包含单文件程序仍可直接运行：

```powershell
.\RemoteNumPad.exe
```

程序打开原生 GUI，不再显示命令行窗口。可选择局域网网卡、修改端口、启动/停止/重启接收、复制地址、打开网页或显示连接二维码；默认端口 8765，记住上次成功设置。手机和电脑连接同一个 Wi-Fi 后，可在安卓 APK 设置中扫码连接，也可用普通扫码软件打开网页。

端口冲突不会关闭面板，可修改或查找建议端口；实际启用成功后地址与二维码自动刷新，失败则尝试恢复原端口。关闭窗口/最小化默认隐藏到托盘，右键托盘“退出程序”才停止服务并释放端口。再次运行 EXE 会打开已有面板。录入前请切回 Excel/WPS。

面板提供数字、小数点、负号、退格、Del、编辑、Enter、单元格导航、撤销、复制、粘贴和公式工具。Del 对应电脑 Delete 键，实际删除行为取决于目标应用的编辑状态。公式按钮会在当前 Excel 单元格中输入对应公式前缀，后续参数仍由用户在 Excel 中完成。

网页与安卓端采用一致的黑白双主题软拟物界面。顶部切换“数字 · 导航 / 公式 · 编辑”；两页上方左侧均为复制、粘贴、保存、另存四宫格，右侧为十字方向键。保存发送 Ctrl+S，另存发送 Excel/WPS 常用 F12，文件名和路径在电脑端操作。五行数字键盘靠近底部，0 横跨前两列，小数点位于最后一行第三列。第二页保留编辑、撤销和公式。功能键使用自绘矢量图标，月亮/太阳切换主题并保存选择，输入页无需滚动。

应用图标与 Light 按键风格统一，两端共用图片生成的浅色数字键与金色 Enter 图案。图标源图、ICO 与生成提示词见 [`assets/app-icon-light`](assets/app-icon-light/README.md)。安卓使用自适应图标，Windows 内置多尺寸 ICO。

方向键为圆角五边形，四个尖头共同朝向中心，中心留空；方向键组与左侧四宫格等宽等高并对齐。键内箭头仍表示实际移动方向，透明角落不触发命令，两页位置一致。

## Android 原生端 v1.3.0

- 电脑端须解压 `publish/v1.3.0/RemoteNumPad-1.3.0-windows-x64.zip` 并运行其中的 `RemoteNumPad.exe`（重新构建时输出到 `publish/v1.3.0/windows/`）。在手机连接设置中扫码确认电脑地址/端口，或者手动填写；握手成功后保存设置。端口修改后重新扫码即可。
- Android 工程位于 [`android`](android)，使用 Kotlin、原生 Android Views 和 OkHttp WebSocket，不包含 WebView、电脑 EXE 或工作簿数据。
- 项目源代码使用 MIT 许可；OkHttp 依赖采用 Apache-2.0，许可证文本同时随源码与 APK 提供，见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。
- Android Studio 打开 `android` 目录，选择 **Build > Build APK(s)**。命令行可运行 `cd android; .\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest`。
- 开发调试 APK 为 `android/app/build/outputs/apk/debug/app-debug.apk`；正式发布使用经过压缩和独立发布签名的 `android/app/build/outputs/apk/release/app-release.apk`，不得把调试包当作正式包。
- **正式版签名不同于旧调试版：先处理完待发送输入、记下设置，再自行卸载旧调试版并安装正式 APK。** 后续正式版本保留同一签名。密钥只在本机私有目录，备份与构建见 [`docs/release-signing.md`](docs/release-signing.md)。
- 可选“按键轻触反馈”默认关闭、遵循系统设置，只在持久入队成功后触发，不等待网络确认、不增加去抖；不代表 Excel 已录入。扫码仅在本机识别，首次使用才请求相机权限，拒绝可手动连接。
- 首次打开点“设置”，填写 Windows 服务显示的电脑私有 IPv4 地址与端口（默认 `8765`）。仅允许 `10.x`、`172.16–31.x`、`192.168.x` 私有地址。
- 主界面为黑白可切换双页：首屏同屏显示方向导航与五行数字区，“下一格”位于数字 3 右侧，Backspace 位于 9 右侧。第二屏同屏显示编辑与公式。页面不滚动，适合单手连续录入。每个标准按钮点击先进入 SQLite 持久 FIFO 队列，单条在途等待 ACK，队列上限 200；离线不丢弃，满队列会拒绝新输入并提示。切换主题或页面不会重启连接或清空待发送队列。
- 断线、重连、服务端重启或注入结果不确定时会暂停，不会盲目重放旧数字；用户可以确认继续、重试明确失败项、核对后跳过不确定项或清空队列。
- APK 包名 `com.remotenumpad`、`minSdk 26`、`targetSdk 36`。扫码只接受规定格式、私有 IPv4 与有效端口；存在待发送项时需明确处理，不自动转移到另一台电脑。发布密钥不提供在公共仓库中。

### 局域网与输入确认边界

手机与电脑应处于可信同一局域网。当前服务使用明文 `ws://`，没有身份验证或 TLS，不要在不可信公共网络中使用。客户端只保存主机地址、端口和待发送按键，不保存单元格/工作簿内容。

协议 ACK 只说明 Windows `SendInput` 已完整插入该命令的键盘事件；它**不能证明** Excel/WPS 已接受输入，也不能确认焦点、目标单元格或编辑状态正确。请先在测试工作簿验证。完整失败可由用户显式重试；部分插入则标记为“不确定”并暂停，避免重复输入。

详细字段和重放规则见 [`docs/android-protocol.md`](docs/android-protocol.md)。
安装步骤和真机验收清单见 [`docs/android-testing.md`](docs/android-testing.md)。

## 开发环境与测试

- Windows 10 / Windows 11
- .NET 8 SDK
- 手机和电脑连接同一个局域网 / Wi-Fi

以下是按需示例，不是每次修改都必须全跑。纯文档不运行应用测试/构建；功能修改选择相关回归并记录证据。基础集合含真实按键测试，默认必须排除，避免向当前用户窗口输入。

```powershell
dotnet build
dotnet test tests/RemoteNumPad.Tests/RemoteNumPad.Tests.csproj --filter "FullyQualifiedName!~SendKeyDoesNotThrowWhenWindowsRejectsInput"
```

Android 本地队列/命令映射测试与 APK 构建：

```powershell
cd .\android
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

原生触摸与 SQLite 仪器测试仅在明确选择的可丢弃模拟器运行；不要对未指定的连接设备批量安装/清数据，见 [安卓验收](docs/android-testing.md)。

## 重新发布 EXE

仅在当前任务授权正式封包时执行；新版本须同步目录与版本名。用户明确确认具体正式版本后，按开发规则自动授权该版本一次源码 push，不自动授权上传公开二进制附件。

```powershell
dotnet publish .\RemoteNumPad.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:IncludeAllContentForSelfExtract=true -o .\publish\v1.3.0\windows
```

最终文件为 `publish/v1.3.0/windows/RemoteNumPad.exe`。完整更新与安装说明见 [`docs/release-1.3.0.md`](docs/release-1.3.0.md)，UI 规范见 [`docs/soft-ui-design.md`](docs/soft-ui-design.md)。Windows 未使用商业代码签名证书，系统可能提示未知发布者。

## License

This project is released under the [MIT License](LICENSE).
