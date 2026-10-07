# 产品开发、验证与推送规则

## 1. 固定产品边界

- 产品为可信局域网内 Windows 接收端、Android 原生端与网页。飞书已取消；云通信、iOS、公网接入等须重新确认。
- 安卓不得变成 WebView；保持先持久入队、FIFO 上限 200、单条在途、序号/ACK 与人工恢复；禁止为防连点加入吞键去抖。
- 不擅改已确认的键位、黑白双页、向心方向键、左右辅助区等尺寸、单屏大按键。设计只维护在 `soft-ui-design.md`，相关修改核对两端一致性。
- ACK 只证明 Windows 完整插入键盘事件；轻触反馈只代表持久入队，不证明工作簿已输入正确。
- 断线/换电脑/服务重启/不确定结果先人工核对，不盲目重放。PC 关闭面板进托盘，显式退出停止服务；二维码只跟随实际端口，冲突不结束别人的程序。
- 不读取/记录/上传私钥、密码、实际按键/工作簿、剪贴板、扫码画面；不擅自卸载真机应用、清队列、改防火墙或开机启动。

## 2. 日常修改顺序

1. 先读 `project-status.md`，再读相关主文档/源码，不把长聊天或执行说明全文作为固定上下文。
2. Bug 先确认现象、复现条件与根因。能自动化就先补最小失败回归，再修复；依赖真机就记录精确步骤与未完成项，不假装已跑失败测试。仅要求诊断时不直接改功能。
3. 定义影响范围与最小验证集合，查台账中的有效同状态证据；实施范围内修改。
4. 运行受影响回归、必要编译/静态检查一次，记录结果与边界。失败先分析，不不断重复全量检查。
5. 更新 `CHANGELOG.md` Unreleased、对应主题文档、进度和验证台账。协议/UI/签名未变化就不扩写无关文档。
6. 核对差异、空白及文档链接，交付完成/未验证项。日常工作不自动提交/推送/封包，不自动升正式版本号。

## 3. 避免重复验证

有效证据需包含检查标识、相关源码状态（提交/差异/指纹）、测试/配置/依赖状态、环境、实际命令/人工步骤、结果与局限。只有“上次通过”不足以复用。

- 上述输入与覆盖范围相同，已通过检查可引用，不必重跑，注明“引用旧证据”。
- 输入变化只作废受影响证据，不作废全部历史；代码/接口/依赖变动须相关编译，不能用旧结果掩盖编译错误。
- 实时连接、当前焦点、真机震动/相机、工作簿结果是状态性检查，旧记录不证明本次健康。
- 不同环境、缺少指纹/实际命令或来源不明的日志只作参考；新封包签名、调试标志与内容必须重新检查。
- 重试写明原因：源码修正、配置/环境恢复或明确偶发失败；不能只靠反复重跑变绿。
- **纯文档**仅核对内容、引用、边界与差异，不运行应用测试/构建，不封包、不重建缓存。

| 改动范围 | 通常选择（不是全量必跑） |
|---|---|
| 命令映射/下一格 | 对应 .NET 映射或 NextCell 回归；共享命令核对两端 |
| v2 队列/ACK/重连 | .NET 协议 + Android 队列；涉及持久化/触摸再补对应模拟器检查 |
| PC 端口/服务/QR/托盘 | ReceiverServer/设置；GUI 展示变化再补 GuiSmoke |
| 网页布局/点击/图标 | 对应契约/图标/mobile-viewport；新封包再验证嵌入网页 |
| 安卓布局/触摸 | JVM 布局/命令 + 受影响模拟器布局/触摸；真机另记 |
| 扫码/反馈 | QR 解析或反馈策略单元测试；链路变动补 ConnectionExtras/扫码验收 |
| 新正式包 | 构建、版本/内容/许可/签名/非调试检查，保留对应 R8 mapping |

安全：默认过滤会真实发送数字的 `SendKeyDoesNotThrowWhenWindowsRejectsInput`；其余键盘回归用假注入。仪器测试只选择已确认归属的可丢弃模拟器，禁止不区分设备安装、卸载或清数据。

专项精确入口（仓库根目录按需选择，不依次全跑）：

```powershell
dotnet test .\tests\RemoteNumPad.V02Backend.Tests\RemoteNumPad.V02Backend.Tests.csproj # ON FAIL: 定位失败的命令映射断言，不调用真实键盘输入。
dotnet test .\tests\RemoteNumPad.NextCell.Tests\RemoteNumPad.NextCell.Tests.csproj # ON FAIL: 检查 NEXT_CELL 动作映射和回归，不向用户文档发键。
dotnet test .\tests\RemoteNumPad.V02Contract.Tests\RemoteNumPad.V02Contract.Tests.csproj # ON FAIL: 对照网页与服务端命令契约修正受影响源文件。
dotnet test .\tests\RemoteNumPad.AndroidProtocol.Tests\RemoteNumPad.AndroidProtocol.Tests.csproj --filter "FullyQualifiedName~ProtocolReliabilityTests" # ON FAIL: 检查对应去重/序号/重启断言，不盲目重试或清队列。
dotnet test .\tests\RemoteNumPad.AndroidProtocol.Tests\RemoteNumPad.AndroidProtocol.Tests.csproj --filter "FullyQualifiedName~DesktopSettingsTests" # ON FAIL: 检查测试临时目录与设置校验，不修改用户实际配置。
.\android\gradlew.bat -p android testDebugUnitTest --tests "*ConnectionQrParserTest*" # ON FAIL: 检查具体 QR 格式/地址断言，不放宽公网限制。
.\android\gradlew.bat -p android testDebugUnitTest --tests "*HapticPolicyTest*" # ON FAIL: 核对持久入队成功条件，不添加去抖。
node --test .\tests\icon-assets.test.cjs # ON FAIL: 检查报告的图标路径/尺寸，不重新生成无关资产。
```

端口/服务、队列与网页视口入口见根目录 `AGENTS.md`；GUI 专项为 `tests/RemoteNumPad.GuiSmoke/RemoteNumPad.GuiSmoke.csproj`（启动原生窗口，仅 GUI 变动且授权环境适合时运行）。Android 精确仪器目标在 `MainActivityLayoutTest`、`MainActivityClickQueueTest`、`ConnectionExtrasTest`、`SQLiteCommandStorePersistenceTest`，按 [指定模拟器流程](android-testing.md) 选择，不能无设备区分全跑。

## 4. 文档职责

- `AGENTS.md`：稳定规则/导航，无版本历史或长日志；`CLAUDE.md` 仅导入入口。
- `project-status.md`：当前基线、完成/待验收/取消事项、包的位置、推送授权使用状态。
- 主题文档：协议/UI/安装验收/签名各自唯一维护处，其他文档链接，不复制实现细节。
- `verification-log.md`：简短证据/失效理由，不放用户输入、私人路径、大日志或未验证的“全部通过”。
- `CHANGELOG.md`：日常条目归 Unreleased，正式确认后归版本；保留旧历史，不把文档改动说成程序功能。

## 5. 正式版与一次自动 push

**用户明确确认某个具体版本为正式版，即授权该版本一次源码 push，无需另外下令。** 模型判断已完成、普通方案确认、日常 Bug 或历史正式版都不是触发条件。

1. 将确认对应到具体版本/源码，核对 PC/Android 版本、更新说明、相关验证与明确未验收项。对象不清楚先问，不能把无关更改纳入授权。
2. 若当前任务包含封包，沿用现有 Android 密钥并使用 Release 压缩/签名，不用 Debug 冒充。构建脚本目录目前固定 `publish/v1.3.0/windows`，新版本先同步目录/命名；不重建密钥、不自动卸载旧包。
3. 提交前审查待提交及已跟踪路径/差异、忽略规则、许可与产物内容，排除私钥/密码、本机配置、数据库/日志、缓存、个人执行说明/隐私。`.gitignore` 不是充分保证，禁止无审查打包全目录。
4. 只提交该版本任务范围内源码/公共文档，推送核实的远端/目标分支；保留用户更改，不强推、不重写历史、不绕过钩子。分支偏离或权限不足，停止报告，不扩大授权。
5. 记录版本、提交、目标分支及成功状态；成功一次后授权消耗，后续小修不能再次自动 push。**1.3.0 已推送，本次规则确认不能重触发。**

网络超时/结果不明先只读核对远端提交，避免已成功却重复操作；未成功则记录阻碍，不建定时任务/无限重试。范围不变且明确未成功的必要重试不算第二次发布，成功仍只一次。

用户单独明确要求 push 可另授权一次指定范围推送。正式版授权不自动包含 GitHub 二进制附件公开上传、云部署、历史分支清理或购买证书。
