# Android 1.3.0 安装与安全验收

## 安装正式包

正式包为 `publish/v1.3.0/RemoteNumPad-1.3.0-release.apk`，支持 Android API 26 及以上、目标 API 36。复制到手机打开安装；系统可能要求允许当前文件管理器安装应用。

正式签名与旧 Debug 不同：先处理完待发送输入、记下连接设置，再由用户自行卸载旧调试版。卸载删除设置与队列；代理不得擅自卸载/清数据。后续正式版沿用同一签名，见 [签名说明](release-signing.md)。

## 连接设置

1. 解压 `publish/v1.3.0/RemoteNumPad-1.3.0-windows-x64.zip` 运行 `RemoteNumPad.exe`。GUI 显示实际接收状态、网卡地址/端口；默认 8765，但以实际生效值为准。
2. 手机与 PC 使用可信同一私有 Wi-Fi。安卓设置扫码或手动填私有 IPv4/端口，确认连接；握手成功才保存。有队列先明确处理方式。
3. PC 修改端口后 QR 自动刷新，安卓需重新扫码/改端口。关闭/最小化面板只是隐藏到托盘，托盘“退出程序”才释放端口，不再输入 Q/exit。
4. 录入前切到可丢弃记事本文档或测试工作簿，避免测试键落到真实数据窗口。安卓可选显示本次点击/入队/发送/确认计数，但不证明 Excel 的最终结果。
5. Windows GUI 有连接数与状态日志，没有旧环境变量开启的控制台命令/ACK 累积计数；不能拿连接数证明按键数量。

一直重连时核对服务正在接收、实际地址/端口一致、网络互通。手机浏览器先打开面板显示的 `http://私有IPv4:端口/`；打不开再排查网络隔离/入站策略，不自动改防火墙。网页可打开却卡在原生校验时，核对运行的是新版 v2 兼容 EXE。网络变化后 PC 地址可能变化。

## 真机手动验收（可丢弃目标）

记录开始/结束计数及电脑实际文本/单元格结果；历史模拟器通过不代表以下完成。

1. 同一数字快速点 100 次，再交替数字点 100 次，核对顺序、数量、无重复。链路计数仅作辅助证据。
2. 输入短数字后 Enter、下一格/方向移动，在测试工作簿核对结果；保存/另存只对该测试文件操作。
3. 断网/停服务后点击应保留队列；恢复先核对目标及人工提示，不盲目继续。重启 PC/安卓后未完成项须审核。
4. 完整失败可显式重试；不确定项先核对再使用明确的跳过操作，不能自动重放。清空未消费项可能造成 `sequence_gap`，不是通用恢复方案；断档时停止，不反复重试/清空或卸载，见 [协议限制](android-protocol.md)。
5. 可选轻触反馈仅成功持久入队触发，队列满/非法输入不反馈；系统可能合并高频震动，不能证明网络或 Excel 成功。
6. 首次扫码按需申请权限，拒绝可手动连接，返回释放相机；测试真实对焦、无效/外网 QR 拒绝和修改端口后的新 QR。

## 按需自动检查

选择与复用见 [开发规则](development-rules.md)、[验证台账](verification-log.md)。不要每次修改都全跑；纯文档不执行下面命令。

仓库根目录，先配置 Android Studio JBR (`JAVA_HOME`) 与 SDK (`ANDROID_HOME` 或私有 `android/local.properties`)：

```powershell
.\android\gradlew.bat -p android testDebugUnitTest
.\android\gradlew.bat -p android :app:lintDebug
```

需要调试构建/仪器测试包时：

```powershell
.\android\gradlew.bat -p android assembleDebug assembleDebugAndroidTest
```

Debug APK 为 `android/app/build/outputs/apk/debug/app-debug.apk`，不是正式包。仪器测试包含 SQLite 操作，仅在已确认归属的可丢弃模拟器安装/运行。下面通过 `-s` 明确目标；先核对列表，再填写实际模拟器序列号，不能改成未经检查的真机：

```powershell
$numpadAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
& $numpadAdb devices
$numpadTestSerial = Read-Host '输入已确认归属的可丢弃模拟器序列号'
& $numpadAdb -s $numpadTestSerial install -r .\android\app\build\outputs\apk\debug\app-debug.apk
& $numpadAdb -s $numpadTestSerial install -r .\android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
& $numpadAdb -s $numpadTestSerial shell am instrument -w com.remotenumpad.test/android.test.InstrumentationTestRunner
```

不同签名导致安装失败时停止，不自动卸载；改选已批准的可丢弃模拟器或询问用户。不要执行不区分设备的批量连接测试；合成触摸不替代真机验收。

## 边界

局域网 `ws://` 明文且无身份认证，二维码不是安全配对。ACK 只确认完整键盘事件插入，不确认焦点、Excel/WPS 接收或工作簿内容；只在可信网络及测试数据上验收。
