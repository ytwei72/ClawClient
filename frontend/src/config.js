/**
 * 默认配置（参考 docs/openclaw-connection-guide.md）
 */

// OpenIM 配置（参考 docs/openclaw-connection-guide.md）
// 注意：文档中的 Secret 是 Bot 的登录凭证，不能用作客户端用户的 Token
// serverSecret 用于 get_admin_token，来自文档 OpenIM 渠道配置的 Secret
// 若遇 CORS，开发时可改用 /openim-api（由 vite 代理转发到 jvs-im）
export const OPENIM_DEFAULT = {
  apiAddr: 'https://jvs-im.wuyinggw.com',
  wsAddr: 'wss://jvs-im-ws.wuyinggw.com',
  botUserID: 'ws-0c2zklyy893a8qkhl',
  platformID: 5, // Web 平台
  defaultUserID: '1483341842551141',
  defaultToken: '',
  serverSecret:
    'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJVc2VySUQiOiJ3cy0wYzJ6a2x5eTg5M2E4cWtobCIsIlBsYXRmb3JtSUQiOjEsImV4cCI6MTc4MTc2OTE3NiwibmJmIjoxNzczOTkyODc2LCJpYXQiOjE3NzM5OTMxNzZ9.HE2dkLPM_c0hTt6dgxomgIiuI-ilLTeHAyA4A8SdjNU',
  // API 路径前缀，404 时可尝试 /api 或留空（jvs-im 可能未暴露 admin API）
  apiPathPrefix: '',
}

// OpenClaw WebSocket 配置（参考 docs/openclaw-connection-guide.md + openclaw-websocket-chat.html）
// 协议：connect.challenge → connect(req) → hello-ok → chat.send → agent 事件
// 开发时需本地 Gateway 运行或建立 SSH 隧道
export const OPENCLAW_DEFAULT = {
  gatewayUrl: 'ws://127.0.0.1:16232',
  token: '1d8bb9d66e60b6a35e2c2d098811af129629def393969ed6',
  agentId: 'main',
}
