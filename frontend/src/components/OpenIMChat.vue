<template>
  <div class="chat-panel">
    <!-- 登录区域 -->
    <div v-if="!isConnected" class="login-section">
      <h3>OpenIM 连接</h3>
      <p class="hint">
        使用 OpenIM 与 Bot 对话，Bot 会自动转发给 OpenClaw Agent 处理。请使用有效的 User ID 和 Token 登录。
      </p>
      <div class="form-grid">
        <div class="field">
          <label>API 地址</label>
          <input v-model="form.apiAddr" placeholder="https://jvs-im.wuyinggw.com" />
        </div>
        <div class="field">
          <label>WebSocket 地址</label>
          <input v-model="form.wsAddr" placeholder="wss://jvs-im-ws.wuyinggw.com" />
        </div>
        <div class="field">
          <label>User ID</label>
          <input v-model="form.userID" placeholder="你的 OpenIM 用户 ID" />
        </div>
        <div class="field">
          <label>Token</label>
          <input v-model="form.token" type="password" placeholder="OpenIM 用户 Token" />
        </div>
      </div>
      <div class="get-token-section">
        <button type="button" class="toggle-link" @click="showGetToken = !showGetToken">
          {{ showGetToken ? '▼' : '▶' }} 如何获取 User ID 和 Token？
        </button>
        <div v-if="showGetToken" class="get-token-content">
          <p class="notice">
            <strong>Token 获取方式</strong>：① 若 jvs-im 暴露了 admin API，可在下方填写并点击「获取 Token」。② 若返回 404，说明该接口未对公网开放，需向<strong>运维/业务后端</strong>获取 User ID 和 Token，并手动填入上方表单。
          </p>
          <div class="field">
            <label>Server Secret（来自 OpenIM config/share.yaml）</label>
            <input
              v-model="tokenForm.serverSecret"
              type="password"
              placeholder="如 openIM123，需向运维获取"
            />
          </div>
          <div class="field">
            <label>API 路径前缀（404 时尝试 /api 或留空）</label>
            <input v-model="tokenForm.apiPathPrefix" placeholder="/api 或留空" />
          </div>
          <div class="field">
            <label>User ID（不存在时自动注册）</label>
            <input v-model="tokenForm.targetUserID" placeholder="如 test-user-001" />
          </div>
          <button
            class="btn secondary"
            :disabled="fetchingToken"
            @click="handleFetchToken"
          >
            {{ fetchingToken ? '获取中...' : '获取 Token' }}
          </button>
          <p v-if="tokenError" class="error-msg">{{ tokenError }}</p>
          <p v-if="tokenSuccess" class="success-msg">{{ tokenSuccess }}</p>
        </div>
      </div>
      <div class="actions">
        <button class="btn primary" :disabled="connecting" @click="handleLogin">
          {{ connecting ? '连接中...' : '连接' }}
        </button>
      </div>
      <p v-if="error" class="error-msg">{{ error }}</p>
    </div>

    <!-- 会话区域 -->
    <div v-else class="session-section">
      <div class="session-header">
        <span class="status connected">已连接</span>
        <span class="bot-info">与 Bot ({{ botUserID }}) 对话</span>
        <button class="btn secondary small" @click="handleLogout">断开</button>
      </div>

      <div ref="messagesRef" class="messages">
        <div
          v-for="msg in messages"
          :key="msg.id"
          :class="['message', msg.isSelf ? 'self' : 'other']"
        >
          <div class="message-content">{{ msg.content }}</div>
          <div class="message-meta">{{ msg.time }}</div>
        </div>
      </div>

      <div class="input-area">
        <textarea
          v-model="inputText"
          placeholder="输入消息..."
          rows="2"
          @keydown.enter.exact.prevent="sendMessage"
        />
        <button class="btn primary" :disabled="sending" @click="sendMessage">
          {{ sending ? '发送中...' : '发送' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { getSDK, CbEvents } from '@openim/client-sdk'
import { OPENIM_DEFAULT } from '../config'

const IMSDK = getSDK()

const form = reactive({
  apiAddr: OPENIM_DEFAULT.apiAddr,
  wsAddr: OPENIM_DEFAULT.wsAddr,
  userID: OPENIM_DEFAULT.defaultUserID || '',
  token: OPENIM_DEFAULT.defaultToken || '',
})

const botUserID = OPENIM_DEFAULT.botUserID
const isConnected = ref(false)
const connecting = ref(false)
const sending = ref(false)
const error = ref('')
const inputText = ref('')
const messages = ref([])
const messagesRef = ref(null)

const showGetToken = ref(false)
const fetchingToken = ref(false)
const tokenError = ref('')
const tokenSuccess = ref('')
const tokenForm = reactive({
  serverSecret: OPENIM_DEFAULT.serverSecret || '',
  apiPathPrefix: OPENIM_DEFAULT.apiPathPrefix || '',
  targetUserID: OPENIM_DEFAULT.defaultUserID || '',
})

const formatTime = () => {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

const parseJsonResponse = async (res, label) => {
  const text = await res.text()
  try {
    if (!text?.trim()) {
      throw new Error(`${label}: 响应为空`)
    }
    return JSON.parse(text)
  } catch (e) {
    if (e instanceof SyntaxError) {
      const preview = text.length > 80 ? text.slice(0, 80) + '...' : text
      if (res.status === 404) {
        throw new Error(
          `404：jvs-im 未暴露 get_admin_token 接口（该接口通常仅限服务端调用）。请向运维/后端获取 User ID 和 Token，或使用有权限访问 OpenIM 的内部服务获取。`
        )
      }
      throw new Error(`${label}: 响应非 JSON (${res.status})。内容: ${preview}`)
    }
    throw e
  }
}

const handleFetchToken = async () => {
  const base = form.apiAddr.trim().replace(/\/$/, '')
  const prefix = (tokenForm.apiPathPrefix || '').trim().replace(/\/$/, '')
  const apiBase = prefix ? `${base}${prefix}` : base
  const secret = tokenForm.serverSecret?.trim()
  const userID = tokenForm.targetUserID?.trim()
  if (!secret || !userID) {
    tokenError.value = '请输入 Server Secret 和 User ID'
    tokenSuccess.value = ''
    return
  }
  tokenError.value = ''
  tokenSuccess.value = ''
  fetchingToken.value = true
  try {
    const opId = Date.now().toString()
    const adminRes = await fetch(`${apiBase}/auth/get_admin_token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', operationID: opId },
      body: JSON.stringify({ secret, userID: 'imAdmin' }),
    })
    const adminData = await parseJsonResponse(adminRes, 'get_admin_token')
    if (adminData.errCode !== 0) {
      throw new Error(adminData.errMsg || '获取 Admin Token 失败')
    }
    const adminToken = adminData.data?.token
    if (!adminToken) throw new Error('Admin Token 为空')

    const userRes = await fetch(`${apiBase}/auth/get_user_token`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        operationID: (Date.now() + 1).toString(),
        token: adminToken,
      },
      body: JSON.stringify({ platformID: OPENIM_DEFAULT.platformID, userID }),
    })
    let userData = await parseJsonResponse(userRes, 'get_user_token')
    if (userData.errCode !== 0) {
      const isNotFound =
        userData.errCode === 1004 || userData.errMsg?.includes('RecordNotFound')
      if (isNotFound) {
        const registerRes = await fetch(`${apiBase}/user/user_register`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            operationID: (Date.now() + 2).toString(),
            token: adminToken,
          },
          body: JSON.stringify({
            users: [{ userID: userID, nickname: userID, faceURL: '' }],
          }),
        })
        const regData = await parseJsonResponse(registerRes, 'user_register')
        if (regData.errCode !== 0) {
          throw new Error(regData.errMsg || `注册用户失败`)
        }
        const retryRes = await fetch(`${apiBase}/auth/get_user_token`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            operationID: (Date.now() + 3).toString(),
            token: adminToken,
          },
          body: JSON.stringify({ platformID: OPENIM_DEFAULT.platformID, userID }),
        })
        userData = await parseJsonResponse(retryRes, 'get_user_token')
      }
      if (userData.errCode !== 0) {
        throw new Error(userData.errMsg || '获取 User Token 失败')
      }
    }
    const userToken = userData.data?.token
    if (!userToken) throw new Error('User Token 为空')

    form.userID = userID
    form.token = userToken
    tokenSuccess.value = `已获取 Token，User ID 和 Token 已填入上方表单，可直接点击「连接」`
  } catch (err) {
    tokenError.value = err?.message || '获取 Token 失败'
  } finally {
    fetchingToken.value = false
  }
}

const handleLogin = async () => {
  if (!form.userID?.trim() || !form.token?.trim()) {
    error.value = '请输入 User ID 和 Token'
    return
  }
  error.value = ''
  connecting.value = true

  try {
    await IMSDK.login({
      userID: form.userID.trim(),
      token: form.token.trim(),
      platformID: OPENIM_DEFAULT.platformID,
      apiAddr: form.apiAddr.trim(),
      wsAddr: form.wsAddr.trim(),
    })
    // 不在 login() 返回后立即视为已连接，需等待 OnConnectSuccess
    // 否则会触发 "Resource load not complete" (errCode 10004)
    connectTimeoutId = setTimeout(() => {
      if (connecting.value && !isConnected.value) {
        connectTimeoutId = null
        connecting.value = false
        error.value = '连接超时，请检查网络后重试'
      }
    }, 15000)
  } catch (err) {
    error.value = err?.errMsg || err?.message || '连接失败'
    connecting.value = false
  }
}

const handleLogout = async () => {
  try {
    await IMSDK.logout()
  } catch (_) {
    // ignore
  }
  isConnected.value = false
  messages.value = []
}

const sendMessage = async () => {
  const text = inputText.value?.trim()
  if (!text || sending.value) return

  const tempId = `temp-${Date.now()}`
  messages.value.push({
    id: tempId,
    content: text,
    isSelf: true,
    time: formatTime(),
  })
  inputText.value = ''
  scrollToBottom()

  sending.value = true
  try {
    const { data: message } = await IMSDK.createTextMessage(text)
    await IMSDK.sendMessage({
      recvID: botUserID,
      groupID: '',
      message,
    })
    // 更新为服务器返回的正式消息（可选）
    const idx = messages.value.findIndex((m) => m.id === tempId)
    if (idx >= 0) messages.value[idx].id = message.clientMsgID || tempId
  } catch (err) {
    messages.value.push({
      id: `err-${Date.now()}`,
      content: `发送失败: ${err?.errMsg || err?.message || '未知错误'}`,
      isSelf: true,
      time: formatTime(),
    })
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

const onRecvNewMessages = ({ data: msgs }) => {
  for (const msg of msgs) {
    if (msg.sendID !== botUserID) continue
    const content = msg.content?.content || String(msg.content || '')
    if (!content) continue
    messages.value.push({
      id: msg.clientMsgID,
      content,
      isSelf: false,
      time: formatTime(),
    })
  }
  scrollToBottom()
}

let connectTimeoutId = null

const handleConnectSuccess = () => {
  if (connectTimeoutId) {
    clearTimeout(connectTimeoutId)
    connectTimeoutId = null
  }
  isConnected.value = true
  connecting.value = false
  error.value = ''
}

const handleConnectFailed = (e) => {
  if (connectTimeoutId) {
    clearTimeout(connectTimeoutId)
    connectTimeoutId = null
  }
  connecting.value = false
  error.value = e?.errMsg || '连接失败'
}

onMounted(() => {
  IMSDK.on(CbEvents.OnRecvNewMessages, onRecvNewMessages)
  IMSDK.on(CbEvents.OnConnectSuccess, handleConnectSuccess)
  IMSDK.on(CbEvents.OnConnectFailed, handleConnectFailed)
})

onUnmounted(() => {
  if (connectTimeoutId) {
    clearTimeout(connectTimeoutId)
    connectTimeoutId = null
  }
  if (typeof IMSDK.off === 'function') {
    IMSDK.off(CbEvents.OnRecvNewMessages, onRecvNewMessages)
    IMSDK.off(CbEvents.OnConnectSuccess, handleConnectSuccess)
    IMSDK.off(CbEvents.OnConnectFailed, handleConnectFailed)
  }
})
</script>

<style scoped>
.chat-panel {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 1rem;
  padding: 1.5rem;
  min-height: 480px;
  display: flex;
  flex-direction: column;
}

.login-section h3,
.session-header {
  margin-bottom: 1rem;
  font-size: 1.1rem;
}

.hint {
  color: var(--text-secondary);
  font-size: 0.85rem;
  margin-bottom: 1.25rem;
  line-height: 1.5;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
  margin-bottom: 1.25rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.field label {
  font-size: 0.8rem;
  color: var(--text-secondary);
}

.field input {
  padding: 0.6rem 0.85rem;
  background: var(--bg-input);
  border: 1px solid var(--border);
  border-radius: 0.5rem;
  color: var(--text-primary);
  font-size: 0.9rem;
}

.field input:focus {
  outline: none;
  border-color: var(--accent);
}

.actions {
  margin-bottom: 0.75rem;
}

.btn {
  padding: 0.6rem 1.25rem;
  border: none;
  border-radius: 0.5rem;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.btn.primary {
  background: var(--accent);
  color: white;
}

.btn.primary:hover:not(:disabled) {
  background: var(--accent-hover);
}

.btn.primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn.secondary {
  background: var(--bg-input);
  color: var(--text-primary);
  border: 1px solid var(--border);
}

.btn.secondary:hover {
  border-color: var(--accent);
}

.btn.small {
  padding: 0.4rem 0.85rem;
  font-size: 0.8rem;
}

.get-token-section {
  margin-bottom: 1rem;
  padding: 0.75rem;
  background: var(--bg-input);
  border-radius: 0.5rem;
  border: 1px solid var(--border);
}

.toggle-link {
  background: none;
  border: none;
  color: var(--accent);
  cursor: pointer;
  font-size: 0.85rem;
  padding: 0;
}

.toggle-link:hover {
  text-decoration: underline;
}

.get-token-content {
  margin-top: 0.75rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--border);
}

.get-token-content p {
  color: var(--text-secondary);
  font-size: 0.85rem;
  margin-bottom: 0.75rem;
}

.get-token-content p.notice {
  color: var(--text-primary);
  background: rgba(99, 102, 241, 0.1);
  padding: 0.6rem;
  border-radius: 0.5rem;
  border-left: 3px solid var(--accent);
}

.get-token-content .field {
  margin-bottom: 0.75rem;
}

.get-token-content .field label {
  font-size: 0.8rem;
}

.success-msg {
  color: var(--success);
  font-size: 0.85rem;
  margin-top: 0.5rem;
}

.error-msg {
  color: var(--error);
  font-size: 0.85rem;
}

.session-section {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.session-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1rem;
}

.status {
  font-size: 0.8rem;
  padding: 0.25rem 0.6rem;
  border-radius: 9999px;
}

.status.connected {
  background: rgba(34, 197, 94, 0.2);
  color: var(--success);
}

.bot-info {
  flex: 1;
  color: var(--text-secondary);
  font-size: 0.85rem;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 1rem 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  min-height: 200px;
  max-height: 320px;
}

.message {
  max-width: 85%;
  align-self: flex-start;
}

.message.self {
  align-self: flex-end;
}

.message-content {
  padding: 0.65rem 1rem;
  border-radius: 0.75rem;
  font-size: 0.9rem;
  background: var(--bg-input);
  white-space: pre-wrap;
  word-break: break-word;
}

.message.self .message-content {
  background: var(--accent);
  color: white;
}

.message-meta {
  font-size: 0.7rem;
  color: var(--text-secondary);
  margin-top: 0.25rem;
  margin-left: 0.5rem;
}

.message.self .message-meta {
  margin-left: 0;
  margin-right: 0.5rem;
  text-align: right;
}

.input-area {
  display: flex;
  gap: 0.75rem;
  padding-top: 1rem;
  border-top: 1px solid var(--border);
}

.input-area textarea {
  flex: 1;
  padding: 0.65rem 1rem;
  background: var(--bg-input);
  border: 1px solid var(--border);
  border-radius: 0.5rem;
  color: var(--text-primary);
  font-size: 0.9rem;
  font-family: inherit;
  resize: none;
}

.input-area textarea:focus {
  outline: none;
  border-color: var(--accent);
}
</style>
