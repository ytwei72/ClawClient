# OpenClaw 连接参数配置指南

> **文档生成时间**: 2026-03-23 16:24 (Asia/Shanghai)  
> **服务器**: iZ2zeeon6ni8sopset7gk3Z (10.0.88.60)  
> **OpenClaw 版本**: 2026.2.26

---

## 一、Gateway 配置（核心）

### 🔌 WebSocket 连接参数

| 参数 | 值 | 说明 |
|------|-----|------|
| **WebSocket URL** | `ws://127.0.0.1:18789` | 当前绑定在 loopback |
| **端口** | `18789` | Gateway 监听端口 |
| **绑定模式** | `loopback` | ⚠️ 仅本地访问 |
| **认证模式** | `token` | Token 认证 |
| **Gateway Token** | `b7434006-6056-458f-acad-31100361dda3` | 连接认证 Token |
| **模式** | `local` | 本地模式 |

### 🌐 HTTP API 参数（OpenAI 兼容）

| 参数 | 值 |
|------|-----|
| **Base URL** | `http://127.0.0.1:18789/v1` |
| **API Key** | `eyJhbGciOiJIUzI1NiIsImtpZCI6Ind1eWluZy1rZXktMSJ9.eyJqdGkiOiJmTVNIYUVuVU5RZk9xRm82WHk0bDRBIiwiaWF0IjoxNzc0MjUyMzgxLCJleHAiOjE3NzQyNjMxODEsIm5iZiI6MTc3NDI1MjMyMSwidWlkIjoicThyc2R4aXlqYTFlN29iNDV1bWxndDlrbjJ3dmYwaDYiLCJhbGlVaWQiOiIxNDgzMzQxODQyNTUxMTQxIiwicmVzb3VyY2VUeXBlIjoiQ2xhd1BsYW4iLCJpbnN0YW5jZUlkIjoiZW50LWQxMDU3YzNiMTQyZGM4ZDgifQ.yuiVZ81nsVYHm-B8JuN7vPVRWIhgClnrNKkWEI-mzH4` |
| **Model** | `gateway/qwen3.5-plus` |

---

## 二、OpenIM 渠道配置

### 📱 OpenIM 连接参数

| 参数 | 值 |
|------|-----|
| **API 地址** | `https://jvs-im.wuyinggw.com` |
| **WebSocket 地址** | `wss://jvs-im-ws.wuyinggw.com` |
| **Secret** | `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJVc2VySUQiOiJ3cy0wYzJ6a2x5eTg5M2E4cWtobCIsIlBsYXRmb3JtSUQiOjEsImV4cCI6MTc4MTc2OTE3NiwibmJmIjoxNzczOTkyODc2LCJpYXQiOjE3NzM5OTMxNzZ9.HE2dkLPM_c0hTt6dgxomgIiuI-ilLTeHAyA4A8SdjNU` |
| **Bot User ID** | `ws-0c2zklyy893a8qkhl` |
| **状态** | ✅ 已启用 |

---

## 三、Tailscale 配置

| 参数 | 值 | 说明 |
|------|-----|------|
| **Tailscale 模式** | `off` | ⚠️ **当前未启用** |
| **重置退出** | `false` | - |

**如需启用 Tailscale**：
```bash
openclaw gateway tailscale on
```

---

## 四、服务器信息

| 参数 | 值 |
|------|-----|
| **主机名** | `iZ2zeeon6ni8sopset7gk3Z` |
| **内网 IP** | `10.0.88.60` |
| **操作系统** | Linux 5.15.0-144-generic (x64) |

---

## 五、三种对接方案

### 方案 A：通过 OpenIM 对话（最简单 ✅）

**无需修改 Gateway 配置**，直接通过 OpenIM 与 Agent 对话。

```javascript
// APP 连接 OpenIM，OpenIM 转发给 OpenClaw
const openimConfig = {
  apiAddr: 'https://jvs-im.wuyinggw.com',
  wsAddr: 'wss://jvs-im-ws.wuyinggw.com',
  secret: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJVc2VySUQiOiJ3cy0wYzJ6a2x5eTg5M2E4cWtobCIsIlBsYXRmb3JtSUQiOjEsImV4cCI6MTc4MTc2OTE3NiwibmJmIjoxNzczOTkyODc2LCJpYXQiOjE3NzM5OTMxNzZ9.HE2dkLPM_c0hTt6dgxomgIiuI-ilLTeHAyA4A8SdjNU',
  botUserID: 'ws-0c2zklyy893a8qkhl'
};

// APP 作为 OpenIM 用户与 Bot 对话
// Bot 会自动转发给 OpenClaw Agent 处理
```

**优点**：
- ✅ 无需修改 Gateway 配置
- ✅ 已有完整的 IM 功能（消息历史、群组等）
- ✅ 生产环境已验证

**缺点**：
- 需要实现 OpenIM 客户端协议

---

### 方案 B：启用 Tailscale 远程访问

**步骤 1：启用 Tailscale**
```bash
openclaw gateway tailscale on
```

**步骤 2：获取 Tailscale IP**
```bash
tailscale ip
# 输出类似：100.x.x.x
```

**步骤 3：APP 连接参数**
```javascript
const openclawConfig = {
  websocket: {
    url: 'ws://100.x.x.x:18789', // Tailscale IP
    token: 'b7434006-6056-458f-acad-31100361dda3'
  },
  http: {
    baseURL: 'http://100.x.x.x:18789/v1',
    apiKey: 'eyJhbGci...yuiVZ81nsVYHm-B8JuN7vPVRWIhgClnrNKkWEI-mzH4',
    model: 'gateway/qwen3.5-plus'
  }
};
```

**优点**：
- ✅ 安全的私有网络
- ✅ 无需公网 IP
- ✅ 直接 WebSocket 连接

**缺点**：
- APP 需要安装 Tailscale 或使用 Tailscale SDK

---

### 方案 C：修改 Gateway 绑定（不推荐用于生产）

**步骤 1：修改配置**
```bash
# 编辑 ~/.openclaw/openclaw.json
# 修改：
"gateway": {
  "port": 18789,
  "mode": "local",
  "bind": "any",  # 从 loopback 改为 any
  "auth": {
    "mode": "token",
    "token": "b7434006-6056-458f-acad-31100361dda3"
  }
}
```

**步骤 2：重启 Gateway**
```bash
openclaw gateway restart
```

**步骤 3：APP 连接参数**
```javascript
const openclawConfig = {
  websocket: {
    url: 'ws://10.0.88.60:18789', // 服务器内网 IP
    token: 'b7434006-6056-458f-acad-31100361dda3'
  },
  http: {
    baseURL: 'http://10.0.88.60:18789/v1',
    apiKey: 'eyJhbGci...yuiVZ81nsVYHm-B8JuN7vPVRWIhgClnrNKkWEI-mzH4',
    model: 'gateway/qwen3.5-plus'
  }
};
```

**⚠️ 安全警告**：
- 仅在内网使用
- 生产环境请配合防火墙/VPN

---

## 六、推荐方案对比

| 方案 | 适用场景 | 难度 | 安全性 |
|------|----------|------|--------|
| **OpenIM** | 企业 IM 集成 | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Tailscale** | 私有设备访问 | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **直接绑定** | 内网测试 | ⭐ | ⭐⭐ |

---

## 七、快速开始代码（HTTP API）

```javascript
// 方案 A：通过 SSH 隧道测试（最简单）
// 1. 建立隧道
// ssh -N -L 18789:127.0.0.1:18789 admin@10.0.88.60

// 2. APP 连接
const OPENCLAW = {
  baseURL: 'http://localhost:18789/v1',
  apiKey: 'eyJhbGciOiJIUzI1NiIsImtpZCI6Ind1eWluZy1rZXktMSJ9.eyJqdGkiOiJmTVNIYUVuVU5RZk9xRm82WHk0bDRBIiwiaWF0IjoxNzc0MjUyMzgxLCJleHAiOjE3NzQyNjMxODEsIm5iZiI6MTc3NDI1MjMyMSwidWlkIjoicThyc2R4aXlqYTFlN29iNDV1bWxndDlrbjJ3dmYwaDYiLCJhbGlVaWQiOiIxNDgzMzQxODQyNTUxMTQxIiwicmVzb3VyY2VUeXBlIjoiQ2xhd1BsYW4iLCJpbnN0YW5jZUlkIjoiZW50LWQxMDU3YzNiMTQyZGM4ZDgifQ.yuiVZ81nsVYHm-B8JuN7vPVRWIhgClnrNKkWEI-mzH4',
  model: 'gateway/qwen3.5-plus'
};

async function chat(message) {
  const response = await fetch(`${OPENCLAW.baseURL}/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${OPENCLAW.apiKey}`
    },
    body: JSON.stringify({
      model: OPENCLAW.model,
      messages: [{ role: 'user', content: message }]
    })
  });
  
  const data = await response.json();
  return data.choices[0].message.content;
}

// 测试
chat('你好').then(reply => console.log(reply));
```

---

## 八、当前配置摘要

```json
{
  "gateway": {
    "websocket": "ws://127.0.0.1:18789",
    "http_api": "http://127.0.0.1:18789/v1",
    "token": "b7434006-6056-458f-acad-31100361dda3",
    "bind": "loopback (需修改才能远程访问)"
  },
  "openim": {
    "api": "https://jvs-im.wuyinggw.com",
    "ws": "wss://jvs-im-ws.wuyinggw.com",
    "secret": "eyJhbGci...HE2dkLPM_c0hTt6dgxomgIiuI-ilLTeHAyA4A8SdjNU",
    "bot_id": "ws-0c2zklyy893a8qkhl"
  },
  "tailscale": "off (需启用)",
  "server": {
    "hostname": "iZ2zeeon6ni8sopset7gk3Z",
    "internal_ip": "10.0.88.60"
  }
}
```

---

## 九、配置文件位置

| 文件 | 路径 |
|------|------|
| **主配置文件** | `/home/admin/.openclaw/openclaw.json` |
| **模型配置** | `/home/admin/.openclaw/agents/main/agent/models.json` |
| **会话数据** | `/home/admin/.openclaw/agents/main/sessions/sessions.json` |
| **工作目录** | `/home/admin/openclaw/workspace` |

---

## 十、常用命令

```bash
# 查看 Gateway 状态
openclaw gateway status

# 查看完整状态
openclaw status

# 查看日志
openclaw logs --follow

# 重启 Gateway
openclaw gateway restart

# 安全审计
openclaw security audit

# 启用 Tailscale
openclaw gateway tailscale on

# 查看渠道状态
openclaw channels status --probe
```

---

## 十一、安全建议

1. **使用 HTTPS/WSS**：生产环境务必启用 TLS
2. **设备配对**：启用设备身份验证和配对审批
3. **Token 轮换**：定期更新 Gateway Token
4. **最小权限**：只申请必要的 scopes
5. **网络隔离**：使用 Tailscale/VPN 而非公网暴露
6. **配置文件权限**：建议设置为 600
   ```bash
   chmod 600 /home/admin/.openclaw/openclaw.json
   ```

---

## 十二、故障排查

| 问题 | 可能原因 | 解决方案 |
|------|----------|----------|
| 连接被拒绝 | Gateway 未启动 | `openclaw gateway status` 检查状态 |
| 认证失败 | Token 错误 | 检查 `gateway.auth.token` 配置 |
| 无法远程访问 | bind=loopback | 修改为 `bind: "any"` 或启用 Tailscale |
| WebSocket 断开 | 网络问题 | 检查防火墙/安全组设置 |

---

**文档来源**: OpenClaw Configuration Export  
**配置文件**: `/home/admin/.openclaw/openclaw.json`  
**生成工具**: OpenClaw Assistant
