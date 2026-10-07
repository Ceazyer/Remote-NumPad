# Android 原生端协议 v2

本文说明 Android 原生输入端与 Windows 服务间的 WebSocket 协议。端点为 `ws://<电脑私有 IPv4>:<实际生效端口>/ws`，默认端口 8765，可在 PC GUI 修改；UTF-8 文本帧，完整消息上限为 4096 字节。服务端会等待 `EndOfMessage` 后再解析，拒绝超限、二进制及无效 UTF-8 消息。

## 握手

客户端为每个安装生成并本地持久保存一个随机 `clientId`，连接后发送：

```json
{"v":2,"type":"hello","clientId":"9fb7a2a2-8d1d-4f52-93c9-5b624093a6ca"}
```

服务端在每次进程启动时生成新的 `serverInstanceId`，回复：

```json
{"v":2,"type":"welcome","clientId":"9fb7a2a2-8d1d-4f52-93c9-5b624093a6ca","serverInstanceId":"..."}
```

客户端会把 `clientId` 和队列保存在应用私有 SQLite 数据库中；主机地址保存在私有 SharedPreferences。工作簿和单元格内容不写入数据库、日志或协议。

## 命令与 ACK

每次按钮点击先持久化，再按严格递增 `seq` FIFO 发送。客户端同时只允许一条命令在途：

```json
{"v":2,"type":"command","clientId":"9fb7a2a2-8d1d-4f52-93c9-5b624093a6ca","seq":1,"command":"7"}
```

只有服务端确认注入结果后才回复 ACK：

```json
{"v":2,"type":"ack","clientId":"9fb7a2a2-8d1d-4f52-93c9-5b624093a6ca","seq":1,"result":"injected","error":null}
```

合法 `command` 白名单与 Windows `KeyboardService.GetCommandActions()` 一致：

`0`–`9`、`.`、`-`、`BACKSPACE`、`DELETE`、`EDIT`、`ENTER`、`PREV_CELL`、`NEXT_CELL`、`UP`、`DOWN`、`LEFT`、`RIGHT`、`UNDO`、`COPY`、`PASTE`、`SAVE`、`SAVE_AS`、`AUTO_SUM`、`FORMULA_AVERAGE`、`FORMULA_MAX`、`FORMULA_MIN`、`FORMULA_ROUND`、`FORMULA_IF`。`SAVE` 为 Ctrl+S，`SAVE_AS` 为 F12；保存对话框由电脑端处理。

命令结果：

- `injected`：该命令需要的所有 `SendInput` 事件均完整插入 Windows 输入流。服务端只缓存每客户端最新已消费序号及 ACK；仅该最新 `clientId + seq + command` 重试返回同一 ACK、不再次注入，更旧序号返回 `stale_sequence`。
- `failed`：`SendInput` 报告零事件插入。服务端不消费该序号，客户端暂停；用户显式重试时可以用相同序号再试。
- `uncertain`：只插入部分事件，或复合动作后续阶段失败。服务端缓存此结果防止重放；客户端暂停并要求人工核对，不能自动重试。

超前序号返回 `sequence_gap`，同序号换命令返回 `sequence_conflict`，旧序号返回 `stale_sequence`。服务端命令注入串行化。新服务进程没有旧去重缓存，所以 `serverInstanceId` 变化时，客户端必须先让用户确认；若应用重启时仍有待发送输入，也必须确认后才能继续。

服务端 ACK 表示 Windows 注入端结果，不表示 Excel/WPS 已把数字写入目标单元格；程序无法通过 `SendInput` 得知目标应用焦点、单元格或编辑模式。

无客户端缓存时，首个合法正序号作为基线，不要求从 1 开始；已建立缓存后要求连续递增。服务进程重启会丢失缓存，但客户端有未确认输入仍需人工审核，不能据此自动重放。

### 已知待验证限制：清空后的序号断档

源码审阅发现：`SQLiteCommandStore.clear()` 只删待发送项，不重置自增序号或 clientId；同一服务进程已缓存该客户端时，后续序号必须连续。例如服务已消费 1，安卓清掉未消费的 2、3，下一条 4 会被拒绝为 `sequence_gap`。仅重连同一进程不清掉服务缓存。

此项为源码推导，未执行回归复现/修复，追踪 `ISSUE-SEQ-CLEAR`。清空不是通用恢复手段；断档后停止输入、保留状态，不反复重试/清空、擅自卸载或自动重放。“不确定项人工核对后跳过”不能推广为任意丢弃序号；后续修复须单独确认范围并先补最小失败回归。

## 兼容、错误与诊断

- 旧浏览器继续发送原有裸文本命令，不要求升级，也不等待 ACK。JSON v2 与旧文本分流。
- 格式错误返回 `type:"error"` 和错误类别；未知白名单命令返回 `result:"failed"`。不记录表格内容。
- Android 设置中的“显示本次启动的链路计数”默认关闭，显示点击、入队、发送和客户端已确认数量。
- 当前 Windows GUI 提供在线连接数与有界服务状态日志，不输出按键/ACK 累积计数。旧命令行版本的 `REMOTENUMPAD_DIAGNOSTICS` 说明不适用于当前 GUI；在线连接数不能证明输入数量正确。
- 本协议使用无 TLS 的局域网 WebSocket，不提供身份认证。只在可信局域网连接，不要发送敏感表格数据。

## 二维码与端口切换

PC 根据实际状态生成 `http://<私有IPv4>:<实际端口>/?app=remotenumpad&v=1`，停止接收禁用可用二维码，端口修改成功后刷新。安卓仅接受约定格式、私有 IPv4 与有效端口，识别后确认，v2 welcome 成功后保存地址。手机连接不随端口无感迁移，需重新扫码/手动修改；有待发送队列须明确处理，不能自动转到另一台电脑。扫码不是身份认证。
