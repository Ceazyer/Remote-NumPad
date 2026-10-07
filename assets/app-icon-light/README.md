# Remote NumPad · Light 应用图标

2026-10-07 使用内置 `image_gen` 图片生成工具创作。视觉元素为浅色软拟物 1、2、3、Enter 按键与无线标识，契合 Light 面板的暖金色功能键。没有打包参考网站图片或第三方品牌标志。

- `source.png`：最终生成的方形源图。
- `remote-numpad-light.ico`：Windows 16 / 20 / 24 / 32 / 48 / 64 / 128 / 256px 八尺寸图标。
- `preview-*.png`：对应尺寸的检查图。
- 安卓密度资源与自适应图标放在 `android/app/src/main/res`，两端使用同一个源图。

原始透明图版本存在边缘残留，因此最终通过图片生成工具改成干净的浅色满版背景；安卓由系统自适应遮罩决定圆形/圆角外形，Windows 使用浅色方形底板。仅进行机械缩放和文件格式编码，不使用程序重新绘制图案。

重新导出：在项目根目录执行 `./scripts/export-app-icons.ps1`，验证执行 `node --test tests/icon-assets.test.cjs`。设计与生成记录见 `generation-prompts.md`。
