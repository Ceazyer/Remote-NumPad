# Remote NumPad v0.2

Remote NumPad 是一个仅供局域网使用的手机 Excel 录入面板。电脑运行 ASP.NET Core 服务，手机浏览器通过 WebSocket 发送语义化按键命令，Windows 端使用 `SendInput` 将操作交给当前活动窗口。

## 运行 v0.2

Windows x64 自包含单文件程序仍可直接运行：

```powershell
.\RemoteNumPad.exe
```

程序会显示手机访问地址。手机和电脑连接同一个 Wi-Fi 后，在手机浏览器打开该地址即可使用。

面板提供数字、小数点、减号、退格、清空、编辑、Enter、单元格导航、撤销、复制、粘贴和公式工具。公式按钮会在当前 Excel 单元格中输入对应公式前缀，后续参数仍由用户在 Excel 中完成。

## 开发环境与测试

- Windows 10 / Windows 11
- .NET 8 SDK
- 手机和电脑连接同一个局域网 / Wi-Fi

```bash
dotnet build
dotnet test tests/RemoteNumPad.Tests/RemoteNumPad.Tests.csproj
```

## 重新发布 EXE

```powershell
dotnet publish .\RemoteNumPad.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:IncludeAllContentForSelfExtract=true -o .\publish\win-x64
```

最终文件为 `publish\win-x64\RemoteNumPad.exe`。

## License

This project is released under the [MIT License](LICENSE).
