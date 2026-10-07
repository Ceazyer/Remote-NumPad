# 正式发布签名与备份

## Android

1.3.0 为经过 R8 压缩、关闭调试标记的 Release APK，不再使用 Android 的调试签名。

由用户授权新建的长期发布密钥仅保存在开发电脑的 `.private/android-signing/`。该目录包含 `remotenumpad-release.p12` 和含密码的 `secrets.json`，权限限制为当前 Windows 用户；目录及所有 keystore / p12 / pfx 均被 Git 忽略。密码不会写入源码、日志、APK 或公开发布包。

**必须把这个完整目录备份到你自己的加密离线介质或安全密码管理系统；只在同一硬盘上留副本不算可靠备份。密钥与密码都不能丢失，后续版本必须使用同一密钥。不要把它们发到聊天、GitHub 或发布附件中。**

构建需要设置四个本机环境变量：`REMOTE_NUMPAD_STORE_FILE`、`REMOTE_NUMPAD_STORE_PASS`、`REMOTE_NUMPAD_KEY_ALIAS`、`REMOTE_NUMPAD_KEY_PASS`。可运行 `scripts/build-release.ps1` 在本机读取私有配置完成构建。未配置正式签名时 Release 构建会失败，不会偷偷产生调试签名包。

发布证书 SHA-256 指纹：

`BAE66C09332EBAAFA03440E73BC5A9F556D52C33DAC0416A66B1A737D1D34655`

**旧版调试 APK 与正式版签名不同，通常不能覆盖安装。先处理完待发送输入、记下连接设置，再自行卸载旧版并安装正式版。卸载会删除旧版设置和队列。今后同签名的正式版可正常覆盖升级。**

APK 验证入口：Android SDK 的 `apksigner verify --verbose --print-certs`；应显示验证通过、一个正式证书，`aapt dump badging` 中不得出现 `application-debuggable`。

## Windows

Windows 使用 Release 配置、自包含 x64 单 EXE、原生 GUI 和托盘；不是 Debug 构建，也没有命令行窗口。当前没有商业 Authenticode 代码签名证书，因此 Windows / SmartScreen 仍可能提示“未知发布者”。Release 构建不等于商业代码签名或商店认证。

Windows 端的运行配置仅写到用户本机的 `%LOCALAPPDATA%/RemoteNumPad/settings.json`，不会随源码或发布 ZIP 上传。
