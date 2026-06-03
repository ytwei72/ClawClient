# ClawClient

OpenClaw / OpenIM 多端客户端仓库，用于连接云端 **OpenClaw Gateway** 与 **OpenIM** 渠道，与 AI Agent 进行对话，并在 Android 端暴露设备能力（相机、语音、屏幕、通知等）供 Gateway 调用。

## 项目能做什么

| 模块 | 说明 |
|------|------|
| **Android 客户端**（`android/`） | 原生 Kotlin + Jetpack Compose 应用。支持 Gateway 连接（Setup Code / 手动 / 扫码）、Chat 流式对话、语音交互、Canvas 屏幕、相机与多种设备 Node 命令；含 `play`（Play 商店合规）与 `thirdParty`（完整权限）两种构建风味。 |
| **Web 前端**（`frontend/`） | Vue 3 + Vite 会话页。可在同一界面切换 **OpenIM 私聊 Bot** 与 **OpenClaw HTTP API**（OpenAI 兼容 `/v1/chat/completions`）两种对话方式。 |
| **配置样例**（`config/`） | OpenClaw Gateway 配置参考与导入导出说明。 |
| **小工具**（`toolkits/`） | Python 脚本，例如查询 OpenAI 兼容 API 的模型列表。 |
| **文档**（`docs/`） | 连接参数、前端/Android 搭建、数据结构规范等详细指南。 |

典型使用场景：

1. **手机 App 连 Gateway**：在 Android 上安装 App，连接本机或远程 OpenClaw Gateway，与 Agent 聊天并授权设备能力。
2. **OpenIM 渠道对话**：通过 OpenIM 与已接入 Gateway 的 Bot 用户私聊（无需改 Gateway 网络配置）。
3. **浏览器直连 Gateway**：启动本地 Gateway 后，用 Web 前端经 HTTP API 对话（开发时由 Vite 代理规避 CORS）。

## 目录结构

```
ClawClient/
├── android/          # OpenClaw Android 应用（在 Android Studio 中打开此目录）
├── frontend/         # Vue 3 Web 会话客户端
├── config/           # OpenClaw 配置样例
├── docs/             # 项目文档
├── toolkits/         # Python 辅助脚本
└── reference/        # 参考页面与示例
```

## 环境要求

| 模块 | 要求 |
|------|------|
| Android | JDK 17、Android Studio、Android SDK（`minSdk = 31`） |
| Web 前端 | Node.js ≥ 16、npm 或 yarn |
| 小工具 | Python ≥ 3.10（可选，见 `pyproject.toml`） |
| 运行时 | 已部署或可本地启动的 **OpenClaw Gateway**；使用 OpenIM 时需可访问的 OpenIM 服务 |

## 开发与使用

### Android 客户端

1. 用 **Android Studio** 打开 `android/` 目录。
2. 构建并安装调试包（PowerShell，在 `android` 目录下）：

```powershell
.\gradlew :app:assemblePlayDebug
.\gradlew :app:installPlayDebug
```

3. 启动 Gateway（在 Gateway 所在机器）并连接 App：**Connect** 页使用 Setup Code 或手动填写 Host / Port / Token。
4. 首次配对需在 Gateway 侧批准设备：

```powershell
openclaw devices list
openclaw devices approve <requestId>
```

USB 调试时可使用端口转发，使手机访问本机 Gateway：

```powershell
adb reverse tcp:18789 tcp:18789
```

更多内容见 [android/README.md](android/README.md)、[docs/android-setup-and-apk-build-guide.md](docs/android-setup-and-apk-build-guide.md)。

### Web 前端

```powershell
Set-Location frontend
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`。OpenIM / OpenClaw 参数可在页面表单或 `src/config.js` 中配置；开发环境下 OpenClaw 默认经 `/openclaw-api` 代理到 `http://127.0.0.1:16232`。

生产构建：`npm run build`，产物在 `frontend/dist/`。

详见 [docs/frontend-setup-guide.md](docs/frontend-setup-guide.md)。

### Gateway 与 OpenIM 配置

- Gateway WebSocket、Token、OpenIM 地址等参数说明：[docs/openclaw-connection-guide.md](docs/openclaw-connection-guide.md)
- OpenClaw 配置文件导入导出：[config/openclaw/OpenClaw配置导入导出.md](config/openclaw/OpenClaw配置导入导出.md)
- Chat 会话数据结构约定：[docs/项目数据结构规范.md](docs/项目数据结构规范.md)

### 小工具（可选）

```powershell
python toolkits\list_openai_models.py --base-url <API_BASE> --api-key <KEY>
```

## 相关文档索引

| 文档 | 内容 |
|------|------|
| [android/README.md](android/README.md) | Android 构建、连接、权限、集成测试 |
| [android/docs/常用操作命令.md](android/docs/常用操作命令.md) | ADB 安装、Logcat、语音唤醒调试 |
| [android/docs/Android 客户端框架选型.md](android/docs/Android 客户端框架选型.md) | 基线选型与仓库来源说明 |
| [docs/frontend-setup-guide.md](docs/frontend-setup-guide.md) | 前端安装、配置与功能说明 |
| [docs/android-setup-and-apk-build-guide.md](docs/android-setup-and-apk-build-guide.md) | Windows 下 Android 环境与 APK 构建 |
| [docs/openclaw-connection-guide.md](docs/openclaw-connection-guide.md) | Gateway / OpenIM / Tailscale 对接方案 |

## 说明

- Android 工程基于 [openclaw/openclaw](https://github.com/openclaw/openclaw) 官方 `apps/android` 二次开发，当前状态仍为 **Alpha**，功能持续迭代中。
- `config/` 与文档中的连接参数仅供团队内部参考；请勿将含密钥的配置提交到公开仓库。
