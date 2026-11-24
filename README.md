# Sony LUT Studio

Sony LUT Studio 是一个面向 Android 设备的 JPEG 3D LUT 色彩处理应用。当前 V0.1 是项目骨架版本，提供 Liquid Glass 风格首页和 GitHub Actions APK 构建流程。

## 当前版本

- Version: 0.1
- Minimum Android: Android 10 (API 29)
- Language: Kotlin
- UI: Jetpack Compose
- Build: Gradle 8.9 / Android Gradle Plugin 8.7.3 / Kotlin 2.0.21
- JDK: 17

## 当前已实现

- Android 应用基础结构
- 深色半透明玻璃风格首页
- 底部导航栏视觉框架
- GitHub Actions 自动构建 Debug APK

## 尚未实现

首页的“选择照片”和“导入 LUT 文件”按钮目前是界面占位，不会执行实际导入。`.cube` 解析、3D LUT 插值、JPEG 预览和导出将在后续版本实现。

## GitHub Actions

推送到 `main` 分支后会自动构建。也可以在 GitHub 仓库的 Actions 页面手动运行 `Android APK Build`。

构建产物位于 Actions 运行记录底部的 Artifacts 区域，名称为 `Sony-LUT-Studio-V0.1`。

## 本地构建

需要 JDK 17、Android SDK 35 和 Gradle 8.9：

```bash
gradle assembleDebug
```

APK 输出路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```
