# JVS 前端安装与配置指南

> 基于 Vue 3 + Vite 的 OpenIM & OpenClaw 会话客户端

---

## 一、环境要求

- **Node.js** ≥ 16.x
- **npm** 或 **yarn** 包管理器

---

## 二、依赖安装

### 1. 进入前端目录

```bash
cd frontend
```

### 2. 安装依赖

```bash
npm install
```

或使用 yarn：

```bash
yarn install
```

### 3. 核心依赖说明

| 包名 | 版本 | 用途 |
|------|------|------|
| `vue` | ^3.4.x | Vue 3 框架 |
| `vue-router` | ^4.3.x | 路由管理 |
| `@openim/client-sdk` | ^3.8.x | OpenIM 即时通讯 SDK |
| `vite` | ^5.2.x | 构建工具 |
| `@vitejs/plugin-vue` | ^5.0.x | Vue 插件 |

---

## 三、开发与构建

### 启动开发服务器

```bash
npm run dev
```

默认访问地址：`http://localhost:5173`

### 构建生产版本

```bash
npm run build
```

产物输出到 `dist/` 目录。

### 本地预览构建结果

```bash
npm run preview
```

---

## 四、配置说明

### 1. OpenIM 配置

OpenIM 会话需要用户登录，配置项位于 `src/config.js` 或页面内表单：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `apiAddr` | `https://jvs-im.wuyinggw.com` | OpenIM API 地址 |
| `wsAddr` | `wss://jvs-im-ws.wuyinggw.com` | OpenIM WebSocket 地址 |
| `botUserID` | `ws-0c2zklyy893a8qkhl` | Bot 用户 ID（对话对象） |

**用户登录所需：**

- **User ID**：OpenIM 客户端用户 ID（非 Bot 的 `ws-0c2zklyy893a8qkhl`）
- **Token**：该用户的登录 Token

**重要区分**：文档中的 **Secret** 是 Bot 的登录凭证，**不能**用作客户端用户的 Token。使用 Secret 登录会报 `TokenInvalidError`。

**获取 Token 的两种方式：**

**方式一：页内「获取 Token」**（需 jvs-im 暴露 admin API）

1. 在 OpenIM 会话页面展开「如何获取 User ID 和 Token？」
2. 填写 **Server Secret**、**API 路径前缀**（404 时可试 `/api` 或留空）、**User ID**
3. 点击「获取 Token」
4. 若返回 **404**：说明 jvs-im 未对公网暴露 `get_admin_token`（常见于生产部署），请改用方式二

**方式二：业务后端调用**（推荐）

1. 后端调用 `POST {API_ADDRESS}/auth/get_admin_token`，请求体 `{ "secret": "<server_secret>", "userID": "imAdmin" }`
2. 使用返回的 Admin Token，调用 `POST {API_ADDRESS}/auth/get_user_token`，请求头 `token: <admin_token>`，请求体 `{ "platformID": 5, "userID": "<user_id>" }`
3. 将返回的 Token 下发到前端

### 2. OpenClaw 配置

依 [OpenClaw 官网文档](https://openclawlab.com/zh-cn/docs/gateway/openai-http-api/)，通过 `POST /v1/chat/completions` 与会话。配置项位于 `src/config.js` 或页面内表单：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `baseURL` | `/openclaw-api/v1` | 开发时用代理避免 CORS；直连填 `http://127.0.0.1:16232/v1` |
| `token` | `gateway.auth.token` | Bearer Token；ClawPlan 可填 connection-guide 中的 API Key |
| `agentId` | `main` | 对应请求头 `x-openclaw-agent-id` |
| `model` | `gateway/qwen3.5-plus` | ClawPlan 用此值；标准 OpenClaw 用 `openclaw` |

**前置条件**：① gateway 配置中启用 `chatCompletions: { enabled: true }`；② 本地 OpenClaw 已启动（端口 16232）。

**Failed to fetch**：多因 CORS。开发时保持 Base URL 为 `/openclaw-api/v1`，由 Vite 代理转发。

---

## 五、项目结构

```
frontend/
├── index.html
├── package.json
├── vite.config.js
└── src/
    ├── main.js
    ├── App.vue
    ├── config.js          # 默认配置（OpenIM、OpenClaw）
    ├── router/
    │   └── index.js
    ├── views/
    │   └── ChatView.vue   # 主会话视图（Tab 切换）
    └── components/
        ├── OpenIMChat.vue   # OpenIM 会话组件
        └── OpenClawChat.vue # OpenClaw HTTP 会话组件
```

---

## 六、功能说明

### OpenIM 会话

1. 填写 API 地址、WebSocket 地址、User ID、Token
2. 点击「连接」完成登录
3. 与 Bot（`ws-0c2zklyy893a8qkhl`）进行私聊
4. Bot 会将消息转发给 OpenClaw Agent 处理

### OpenClaw 会话

1. 配置 Base URL、API Key、Model（可修改为自定义地址）
2. 直接通过 HTTP API 与 OpenClaw 对话
3. 支持多轮对话（自动携带历史消息）

---

## 七、故障排查

| 问题 | 可能原因 | 解决方案 |
|------|----------|----------|
| OpenIM TokenInvalidError | 误用 Secret 作为 Token | Secret 是 Bot 凭证，需通过 API 获取用户 Token |
| OpenIM 连接失败 | Token 无效或过期 | 重新获取用户 Token |
| OpenIM 连接失败 | 地址错误或网络不通 | 检查 apiAddr、wsAddr 及网络 |
| OpenClaw 请求失败 | baseURL 无法访问 | 使用 SSH 隧道或调整 Gateway 绑定 |
| OpenClaw 401 | API Key 错误 | 检查 config 中的 apiKey |
| 跨域错误 | 后端未配置 CORS | 配置 OpenClaw/代理允许跨域 |

---

## 八、相关文档

- [OpenClaw 连接参数配置指南](./openclaw-connection-guide.md)
- [OpenIM 官方文档](https://docs.openim.io/)
- [@openim/client-sdk](https://www.npmjs.com/package/@openim/client-sdk)

---

**文档生成时间**：2026-03-23
