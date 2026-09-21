# Remote NumPad v1.0

Remote NumPad 是一个仅供局域网使用的手机数字小键盘。电脑运行 ASP.NET Core 服务，手机浏览器通过 WebSocket 发送按键，Windows 端使用 `SendInput` 将按键交给当前活动窗口。

## 直接运行 v1.0 EXE

发布包为 Windows x64 自包含单文件程序，不需要目标电脑预装 .NET；网页资源已嵌入 EXE。

双击 `RemoteNumPad.exe`，或在 PowerShell 中执行：

```powershell
.\RemoteNumPad.exe
```

程序会显示手机访问地址。手机和电脑连接同一个 Wi-Fi 后，在手机浏览器打开该地址即可使用。

## 开发环境要求

- Windows 10 / Windows 11
- .NET 8 SDK
- 手机和电脑连接同一个局域网 / Wi-Fi

## 启动

开发环境运行时，在项目根目录执行：

```bash
dotnet run
```

程序会显示类似下面的手机访问地址：

```text
Remote NumPad Server Started

Open this address on your phone:

http://192.168.x.x:8765
```

支持按键：`0`-`9`、`.`、`Enter`、`Backspace`。

## 重新发布 EXE

使用 .NET 8 SDK 在项目根目录执行：

```powershell
dotnet publish .\RemoteNumPad.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:IncludeAllContentForSelfExtract=true -o .\publish\win-x64
```

最终文件为：

```text
publish\win-x64\RemoteNumPad.exe
```

## 测试

编译：

```bash
dotnet build
```

运行自动化测试：

```bash
dotnet test tests/RemoteNumPad.Tests/RemoteNumPad.Tests.csproj
```

手动验证时，先打开记事本或 Excel 并点击输入区域，再使用手机依次点击数字键。Excel 的小数点显示会遵循 Windows 当前区域设置。

## Windows 防火墙

第一次运行时 Windows 可能询问是否允许网络访问。只需允许“专用网络”，不需要开放“公用网络”。本项目不会自动修改防火墙规则。

如果电脑本机可以打开 `http://127.0.0.1:8765`，但手机无法连接，请先检查 Windows 防火墙以及手机和电脑是否连接同一 Wi-Fi。

## License

This project is released under the [MIT License](LICENSE).

