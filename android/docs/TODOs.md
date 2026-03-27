# Android 客户端待办

## 唤醒模型双引擎（Vosk + Sherpa-ONNX）

- [ ] **在保留现有 Vosk 唤醒的基础上，增加 Sherpa-ONNX（KWS）作为第二套唤醒引擎**；用户在 App 内**二选一**作为当前使用的唤醒模型；**Release/Debug 构建将 Vosk 与 Sherpa 所需的 native 库、ONNX 模型、词表等资源一并打入 APK**（不设「仅下发其中一套」的裁剪目标，以体积换可切换与离线可用）。
  - **运行时契约不变：** 仍通过 `HotwordService` / `WAKE_TRIGGERED` / `NodeRuntime` 的启停与麦克风协作；实现上可按所选引擎分支初始化（或抽象统一接口），切换引擎时需重新装载模型并重启热词服务。
  - **设置与持久化：** 增加「唤醒引擎」选项（例如 Vosk / Sherpa-ONNX），写入 `SecurePrefs`（或等价存储），变更后触发 `refreshHotwordServiceState()` 一类逻辑以应用新引擎。
  - **本机参考工程（Sherpa 应对照 Kws，而非 ASR 示例）：** `E:\OpenSource\AI\sherpa-onnx\android\SherpaOnnxKws`  
    - 同目录下 `SherpaOnnx` 为流式 **ASR** 示例；`android\README.md` 中 **SherpaOnnxKws** 对应 keyword spotting。
  - **官方文档（分工查阅）：**
    - [Build sherpa-onnx for Android（预编译 `.so`、jniLibs、assets 模型等）](https://k2-fsa.github.io/sherpa/onnx/android/build-sherpa-onnx.html#download-sherpa-onnx) — Windows 可直接使用 [Release](https://github.com/k2-fsa/sherpa-onnx/releases) 的 `sherpa-onnx-v*-android.tar.bz2`；集成本项目时把示例工程名换为 **`SherpaOnnxKws`** 对照即可。
    - [Keyword spotting（KWS 原理、keywords、`text2token`、阈值与 boosting）](https://k2-fsa.github.io/sherpa/onnx/kws/index.html)
    - [KWS 预训练模型与定制关键词](https://k2-fsa.github.io/sherpa/onnx/kws/pretrained_models/index.html)
    - [Android 示例总索引](https://k2-fsa.github.io/sherpa/onnx/android/index.html)

# Android 客户端已完成

## 语音唤醒（Vosk / HotwordService）

- [x] **参考本机克隆的 `openclaw-assistant` 实现离线热词与后台监听**，按需移植到当前官方基线（`ai.openclaw.app`）中，而非整体替换工程。
  - **对照代码路径（本机示例）：** `E:\OpenSource\AI\openclaw-assistant`
  - **优先阅读：** `app/src/main/java/com/openclaw/assistant/service/HotwordService.kt`、`HotwordDebugLogger.kt`，以及 `app/build.gradle.kts` 中的 `vosk-android` 依赖与相关 Manifest / 权限。
  - **注意：** 与官方 App 现有 **Voice / 单次麦克风** 流程并存时，需处理麦克风占用与暂停/恢复热词（可参考 assistant 中 `ChatActivity`、`OpenClawSession` 对 `HotwordService` 的暂停逻辑）。

（选型背景见同目录下的《Android 客户端框架选型.md》。）
