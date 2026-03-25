# Android 客户端待办

## 语音唤醒（Vosk / HotwordService）

- [ ] **参考本机克隆的 `openclaw-assistant` 实现离线热词与后台监听**，按需移植到当前官方基线（`ai.openclaw.app`）中，而非整体替换工程。
  - **对照代码路径（本机示例）：** `E:\OpenSource\AI\openclaw-assistant`
  - **优先阅读：** `app/src/main/java/com/openclaw/assistant/service/HotwordService.kt`、`HotwordDebugLogger.kt`，以及 `app/build.gradle.kts` 中的 `vosk-android` 依赖与相关 Manifest / 权限。
  - **注意：** 与官方 App 现有 **Voice / 单次麦克风** 流程并存时，需处理麦克风占用与暂停/恢复热词（可参考 assistant 中 `ChatActivity`、`OpenClawSession` 对 `HotwordService` 的暂停逻辑）。

（选型背景见同目录下的《Android 客户端框架选型.md》。）
