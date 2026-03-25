# Android 客户端框架选型

本文档汇总对 OpenClaw 相关开源 Android 实现的调研结论，用于在「连接云端 Gateway 进行对话」场景下选择基线与扩展方向。

---

## 结论摘要

**建议基于官方仓库 `openclaw/openclaw` 中的 `apps/android` 进行二次开发。**

---

## 候选项对比

### 候选项一：官方 `openclaw/apps/android`（推荐首选）

原生 **Kotlin + Jetpack Compose**，`minSdk = 31`，支持 Android Studio **Live Edit** 与 **Apply Changes**，便于快速迭代。

**功能完整度最高：**

- 提供 **Connect / Chat / Voice** 三大核心 Tab，以及 **Canvas、Camera、Screen capture**。
- 暴露较完整的 Android 设备命令能力（如 notifications、contacts、calendar、callLog、motion 等）。

**语音与连接：**

- 语音采用单次麦克风开关流程，支持转录捕获与 **TTS** 播放（优先 **ElevenLabs**，可降级为系统 TTS）。
- 连接支持 **Setup Code**、**Manual** 模式；跨网络可通过 **Tailscale + Wide-Area Bonjour** 等方式解决。

| 优势 | 劣势 |
|------|------|
| 与官方 Gateway 协议同步维护，是唯一随 OpenClaw 主仓演进的 Android 代码 | 尚未在应用商店公开发布，需本地 `./gradlew :app:assembleDebug` 自行编译 |
| Jetpack Compose 利于现代化 UI，未来面向 Play 商店的版本也预期以此为基础 | — |

---

### 候选项二：`yuga-hashimoto/openclaw-assistant`（语音体验突出的第三方）

深度集成 Android 系统，偏「真实 AI 助手」形态：

- 长按 Home 激活、基于 **Vosk** 的**离线唤醒词**、流式响应、**连续对话**、**Wear OS** 手表端。
- 通知、相机、联系人、日历、WiFi、屏幕共享等系统控制能力。

**聊天与多 Agent：**

- 完整 Chat 界面，**Markdown**、消息时间戳、文件/图片附件。
- **多 Agent** 从 Gateway 动态拉取；本地持久化聊天记录；支持创建/切换/删除会话。

| 优势 | 劣势 |
|------|------|
| 唤醒词、Wear OS、系统操控等体验强于当前官方 App | 个人维护，与官方 Gateway 协议存在**同步滞后**风险 |
| 功能更贴近「随身助手」产品形态 | — |

---

### 其余候选项（本场景不推荐）

| 项目 | 定位 | 不推荐原因 |
|------|------|------------|
| `mithun50/openclaw-termux` | 在手机上**运行 Gateway**，非 Node 典型部署 | 目标场景为本地/Termux 跑服务，与「纯客户端连云端 Gateway」不符 |
| `friuns2/openclaw-android-assistant`（AnyClaw） | **本地自托管** Gateway | 目标场景不符 |
| `AidanPark/openclaw-android` | **本地自托管** Gateway | 目标场景不符 |

---

## 最终建议

**业务目标若为：连接云端 Gateway 对话 → 以官方 `apps/android` 为基线做二次开发。**

**理由简述：**

1. **Kotlin + Jetpack Compose** 的原生现代栈，与 Gateway 协议随主仓持续对齐。
2. 当前开源方案中**功能覆盖面最广**（Chat / Voice / Canvas / Camera / 设备命令），架构清晰，扩展成本相对可控。
3. 若需强化「语音唤醒」类体验，可**参考** `yuga-hashimoto/openclaw-assistant` 中 **Vosk 离线模型** 等实现，按需移植到官方基线之上，而非整体替换基线。

---

## 文档维护

选型结论随上游仓库与 Gateway 协议变更可能需更新；重大协议或官方 App 发布策略变化时，建议复核本页表格与建议段落。

---

## 本仓库 `android/` 目录说明（ClawClient）

本目录在 **官方** `openclaw` 仓库的 `apps/android` 基础上复制，便于在 ClawClient 内独立打开与二次开发，并与选型结论一致。

| 路径 | 说明 |
|------|------|
| `app/`、`benchmark/`、Gradle 包装器等 | 与官方 `apps/android` 对齐的应用与基准测试工程 |
| `shared/OpenClawKit/Sources/OpenClawKit/Resources/` | 从官方 `apps/shared/OpenClawKit/.../Resources` 同步的 **assets 资源**（满足 `app/build.gradle.kts` 中的相对路径引用） |
| `docs/` | 本文档、`TODOs.md` 等说明材料 |

**构建：** 使用 **Android Studio** 打开本目录即可；Gradle 使用 IDE 内置 JDK，一般无需单独配置本机 `JAVA_HOME`。命令行示例：

```text
gradlew.bat :app:assembleThirdPartyDebug
```

（亦可使用 `assemblePlayDebug`；`thirdParty` flavor 在官方工程中默认开启更多设备能力相关的编译开关。）

**与上游同步：** 若官方协议或资源有更新，请分别从本机 `openclaw` 仓库中的 `apps/android` 与 `apps/shared/OpenClawKit/Sources/OpenClawKit/Resources` 合并。

**openclaw-assistant 对照：** 不在本仓库内保留副本；请使用本机克隆路径（例如 `E:\OpenSource\AI\openclaw-assistant`）阅读 **Vosk / HotwordService** 等实现。具体移植项见 `TODOs.md`。
