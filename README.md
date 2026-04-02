# DualCam App

Android 双摄像头同时录制应用，支持画中画预览和多焦段后置摄像头切换。

## 功能特性

- **双摄同时录制** - 前置和后置摄像头同步录像
- **画中画预览** - 主预览画面 + 小窗口副预览
- **多焦段切换** - 支持广角、中焦、长焦后置摄像头切换
- **音频波形** - 实时显示录音波形
- **录制指示器** - 录制计时和状态指示

## 支持设备

- Vivo X200 Ultra (已测试)
- 其他支持 Camera2 API 的 Android 设备

## 系统要求

- Android 10 (API 29) 或更高版本
- Camera2 API 支持
- 摄像头权限

## 下载安装

下载 APK: [app-debug.apk](./releases/download/v1.1/app-debug.apk)

```bash
adb install app-debug.apk
```

## 构建

```bash
./gradlew assembleDebug
```

## 使用说明

1. 打开应用后，主预览显示后置摄像头，副窗口显示前置摄像头
2. 点击录制按钮开始同时录制前后摄像头
3. 点击切换按钮可在后置摄像头焦段间切换（广角/中焦/长焦）
4. 再次点击录制按钮停止录制

## 技术栈

- Kotlin
- Camera2 API
- MediaRecorder
- TextureView
- AndroidX

## License

MIT
