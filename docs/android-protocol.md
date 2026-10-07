# Android 原生端协议 v2

本文说明 Android 原生输入端与 Windows 服务间的 WebSocket 协议。端点仍为 `ws://<电脑私有 IPv4>:8765/ws`，UTF-8 文本帧；完整消息上限为 4096 字节。服务端会等待 `EndOfMessage` 后再解析，拒绝超限、二进制及无效 UTF-8 消息。

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

`0`–`9`、`.`、`-`、`BACKSPACE`、`DELETE`、`EDIT`、`ENTER`、`PREV_CELL`、`NEXT_CELL`、`UP`、`DOWN`、`LEFT`、`RIGHT`、`UNDO`、`COPY`、`PASTE`、`AUTO_SUM`、`FORMULA_AVERAGE`、`FORMULA_MAX`、`FORMULA_MIN`、`FORMULA_ROUND`、`FORMULA_IF`。

命令结果：

- `injected`：该命令需要的所有 `SendInput` 事件均完整插入 Windows 输入流。服务端缓存最新序号及 ACK；同一 `clientId + seq + command` 重试会返回相同 ACK，不会再次注入。
- `failed`：`SendInput` 报告零事件插入。服务端不消费该序号，客户端暂停；用户显式重试时可以用相同序号再试。
- `uncertain`：只插入部分事件，或复合动作后续阶段失败。服务端缓存此结果防止重放；客户端暂停并要求人工核对，不能自动重试。

超前序号返回 `sequence_gap`，同序号换命令返回 `sequence_conflict`，旧序号返回 `stale_sequence`。服务端命令注入串行化。新服务进程没有旧去重缓存，所以 `serverInstanceId` 变化时，客户端必须先让用户确认；若应用重启时仍有待发送输入，也必须确认后才能继续。

服务端 ACK 表示 Windows 注入端结果，不表示 Excel/WPS 已把数字写入目标单元格；程序无法通过 `SendInput` 得知目标应用焦点、单元格或编辑模式。

## 兼容、错误与诊断

- 旧浏览器继续发送原有裸文本命令，不要求升级，也不等待 ACK。JSON v2 与旧文本分流。
- 格式错误返回 `type:"error"` 和错误类别；未知白名单命令返回 `result:"failed"`。不记录表格内容。
- Android 设置中的“显示本次启动的链路计数”默认关闭，显示点击、入队、发送和客户端已确认数量。
- Windows 服务的 v2 累积诊断默认关闭。仅在启动服务前设置环境变量 `REMOTENUMPAD_DIAGNOSTICS=1` 才会按 ACK 输出累计的服务端接收、完整注入成功和 ACK 发送数；未开启时不输出诊断计数。
- 本协议使用无 TLS 的局域网 WebSocket，不提供身份认证。只在可信局域网连接，不要发送敏感表格数据。
