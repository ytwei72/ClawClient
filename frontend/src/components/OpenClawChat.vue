<template>
  <div class="openclaw-chat">
    <header class="chat-header">
      <span>🦞</span>
      <h1>OpenClaw WebSocket Chat</h1>
      <span :class="['ws-status', wsStatusClass]" id="wsStatus">{{ wsStatusText }}</span>
    </header>

    <div class="config-bar">
      <label>Gateway URL</label>
      <input v-model="form.gatewayUrl" type="text" placeholder="ws://127.0.0.1:16232" />
      <label>Token</label>
      <input v-model="form.token" type="password" placeholder="OPENCLAW_GATEWAY_TOKEN" />
      <label>Agent</label>
      <input v-model="form.agentId" type="text" placeholder="main" />
      <button
        :class="['connect-btn', { disconnect: connected }]"
        @click="toggleConnect"
      >
        {{ connected ? '断开' : '连接' }}
      </button>
    </div>

    <div class="proto-hint">
      协议流程：服务端→ connect.challenge &nbsp;→&nbsp; 客户端→ connect(req) &nbsp;→&nbsp; 服务端→ hello-ok &nbsp;→&nbsp; 发送 chat.send &nbsp;→&nbsp; 监听 agent 事件
    </div>

    <div ref="messagesRef" class="messages">
      <div
        v-for="msg in messages"
        :key="msg.id"
        :class="['msg', msg.role, { typing: msg.typing }]"
      >
        <div v-if="msg.role === 'user' || msg.role === 'assistant'" class="lbl">
          {{ msg.role === 'user' ? '你' : 'OpenClaw' }}
        </div>
        <span class="msg-body">{{ msg.content }}</span>
      </div>
    </div>

    <div class="input-area">
      <textarea
        v-model="inputText"
        placeholder="输入消息，Enter 发送（Shift+Enter 换行）"
        rows="1"
        :disabled="!connected"
        @keydown.enter.exact.prevent="handleSend"
        @input="adjustTextareaHeight"
      />
      <button
        class="debug-btn"
        :class="{ active: showDebug }"
        title="显示/隐藏协议调试帧"
        @click="toggleDebug"
      >
        调试
      </button>
      <button class="send-btn" :disabled="!connected || sending" @click="handleSend">
        发送
      </button>
    </div>
  </div>
</template>

<script setup>
/**
 * OpenClaw WebSocket 客户端
 * 协议版本: v3
 * 参考: openclaw-websocket-chat.html + https://docs.openclaw.ai/gateway/protocol
 */
import { ref, reactive, computed, onUnmounted, nextTick } from 'vue'
import { OPENCLAW_DEFAULT } from '../config'
import {
  loadOrCreateDeviceIdentity,
  buildConnectDevice,
} from '../utils/openclaw-device-auth'

const form = reactive({
  gatewayUrl: OPENCLAW_DEFAULT.gatewayUrl,
  token: OPENCLAW_DEFAULT.token,
  agentId: OPENCLAW_DEFAULT.agentId,
})

const messages = ref([])
const inputText = ref('')
const messagesRef = ref(null)
const showDebug = ref(false)
const sending = ref(false)

let ws = null
let connected = ref(false)
let pendingMsgId = null
let reqCounter = 0
let currentSessionKey = ''

// ── 工具 ──────────────────────────────────────────────
function genId() {
  return `web-${Date.now()}-${++reqCounter}`
}

// ── 状态与消息 ────────────────────────────────────────
const wsStatusText = computed(() => {
  if (connected.value) return '● 已连接'
  if (ws?.readyState === WebSocket.CONNECTING) return '● 连接中…'
  if (ws?.readyState === WebSocket.CLOSING) return '● 断开中…'
  return '● 未连接'
})

const wsStatusClass = computed(() => {
  if (connected.value) return 'connected'
  if (ws?.readyState === WebSocket.CONNECTING) return 'connecting'
  if (ws?.readyState === 1) return 'connected'
  return ''
})

function appendMsg(role, text, opts = {}) {
  const id = `msg-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`
  const msg = {
    id,
    role,
    content: text,
    typing: opts.typing ?? false,
  }
  messages.value.push(msg)
  scrollToBottom()
  return { id, msg }
}

function appendDebug(label, data) {
  if (!showDebug.value) return
  const id = `debug-${Date.now()}`
  messages.value.push({
    id,
    role: 'debug',
    content: `[${label}] ${JSON.stringify(data, null, 2)}`,
    isDebug: true,
  })
  scrollToBottom()
}

function scrollToBottom() {
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

function updatePendingMsg(content, removeTyping = false) {
  const idx = messages.value.findIndex((m) => m.id === pendingMsgId)
  if (idx >= 0) {
    messages.value[idx].content = content
    if (removeTyping) messages.value[idx].typing = false
  }
}

function readChatTextFromMessage(message) {
  if (!message) return ''
  if (typeof message === 'string') return message
  if (Array.isArray(message?.content)) {
    return message.content
      .filter((item) => item?.type === 'text' && typeof item?.text === 'string')
      .map((item) => item.text)
      .join('')
  }
  if (typeof message?.text === 'string') return message.text
  return ''
}

// ── WebSocket 连接 ────────────────────────────────────
function connect() {
  const url = form.gatewayUrl.trim()
  const token = form.token.trim()

  appendMsg('system', `正在连接 ${url} …`)

  ws = new WebSocket(url)

  ws.onopen = () => {
    appendMsg('system', 'WebSocket 已建立，等待服务端握手挑战…')
  }

  ws.onmessage = (event) => {
    let frame
    try {
      frame = JSON.parse(event.data)
    } catch {
      return
    }
    appendDebug('←', frame)
    handleFrame(frame, token)
  }

  ws.onerror = () => {
    appendMsg('error', 'WebSocket 连接失败。请检查 Gateway 是否运行，URL 是否正确。')
  }

  ws.onclose = (e) => {
    connected.value = false
    appendMsg('system', `连接已关闭 (code: ${e.code})`)
  }
}

function disconnect() {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.close()
  }
  ws = null
  connected.value = false
}

function toggleConnect() {
  if (ws && ws.readyState === WebSocket.OPEN) {
    disconnect()
  } else {
    connect()
  }
}

// ── 处理接收帧 ─────────────────────────────────────────
function handleFrame(frame, token) {
  if (frame.type === 'event' && frame.event === 'connect.challenge') {
    appendMsg('system', `收到握手挑战 (nonce: ${frame.payload?.nonce?.slice(0, 8)}…)`)
    sendConnect(token, frame.payload)
    return
  }

  if (frame.type === 'res' && frame.payload?.type === 'hello-ok') {
    connected.value = true
    appendMsg('system', `握手成功，协议版本 v${frame.payload.protocol}。可以开始对话了。`)
    if (frame.payload.auth?.deviceToken) {
      localStorage.setItem('openclaw_device_token', frame.payload.auth.deviceToken)
    }
    return
  }

  if (frame.type === 'res' && frame.ok === false) {
    appendMsg('error', `连接被拒绝: ${JSON.stringify(frame.error)}`)
    return
  }

  if (frame.type === 'event') {
    const ev = frame.event
    const p = frame.payload ?? {}

    // chat 事件（ChatEventSchema: state=delta|final|aborted|error, message）
    if (ev === 'chat') {
      const state = p.state
      const eventSessionKey = typeof p.sessionKey === 'string' ? p.sessionKey : ''
      if (currentSessionKey && eventSessionKey && eventSessionKey !== currentSessionKey) {
        return
      }
      const text = readChatTextFromMessage(p.message)

      if (state === 'delta' && text) {
        if (!pendingMsgId) {
          const r = appendMsg('assistant', '', { typing: true })
          pendingMsgId = r.id
        }
        const idx = messages.value.findIndex((m) => m.id === pendingMsgId)
        if (idx >= 0) {
          // 服务端 delta 发送的是“当前累计文本”，这里直接覆盖
          messages.value[idx].content = text
        }
        scrollToBottom()
      }

      if ((state === 'final' || state === 'aborted') && text) {
        if (pendingMsgId) {
          updatePendingMsg(text, true)
          pendingMsgId = null
        } else {
          appendMsg('assistant', text)
        }
      }

      if (state === 'error') {
        const errMsg = p.errorMessage || '会话执行失败'
        appendMsg('error', `OpenClaw 错误: ${errMsg}`)
      }

      if (state === 'final' || state === 'aborted' || state === 'error') {
        if (pendingMsgId) {
          const idx = messages.value.findIndex((m) => m.id === pendingMsgId)
          if (idx >= 0) messages.value[idx].typing = false
          pendingMsgId = null
        }
        sending.value = false
      }
      return
    }

    // agent / message 事件（旧格式: delta, text, done）
    if (ev === 'agent' || ev === 'message') {
      if (p.delta !== undefined) {
        if (!pendingMsgId) {
          const r = appendMsg('assistant', '', { typing: true })
          pendingMsgId = r.id
        }
        const idx = messages.value.findIndex((m) => m.id === pendingMsgId)
        if (idx >= 0) {
          messages.value[idx].content += p.delta
        }
        scrollToBottom()
      }

      if (p.text !== undefined && p.delta === undefined) {
        if (pendingMsgId) {
          updatePendingMsg(p.text, true)
          pendingMsgId = null
        } else {
          appendMsg('assistant', p.text)
        }
      }

      if (p.done === true || p.finished === true) {
        if (pendingMsgId) {
          const idx = messages.value.findIndex((m) => m.id === pendingMsgId)
          if (idx >= 0) messages.value[idx].typing = false
          pendingMsgId = null
        }
        sending.value = false
      }
    }

    if (ev === 'typing' || ev === 'agent.start') {
      if (!pendingMsgId) {
        const r = appendMsg('assistant', '', { typing: true })
        pendingMsgId = r.id
      }
    }
  }
}

// ── 发送 connect 握手帧 ───────────────────────────────
// 参考 OpenClaw 规范：client.id/client.mode 须为允许值，device 须含 publicKey/signature/signedAt
async function sendConnect(token, challenge) {
  const nonce = challenge?.nonce ?? ''
  const tokenStr = token?.trim() || undefined

  let device = undefined
  const isSecureContext = typeof crypto !== 'undefined' && !!crypto.subtle
  if (isSecureContext) {
    try {
      const deviceIdentity = await loadOrCreateDeviceIdentity()
      device = await buildConnectDevice({
        deviceIdentity,
        clientId: 'webchat',
        clientMode: 'webchat',
        role: 'operator',
        scopes: ['operator.read', 'operator.write'],
        authToken: tokenStr,
        nonce,
      })
    } catch (err) {
      appendMsg('error', `设备签名失败: ${err?.message || err}`)
      return
    }
  } else {
    appendMsg('error', '当前为非安全上下文（非 HTTPS/localhost），无法使用 WebCrypto 进行设备签名。请使用 https 或 127.0.0.1 访问。')
    return
  }

  const connectReq = {
    type: 'req',
    id: genId(),
    method: 'connect',
    params: {
      minProtocol: 3,
      maxProtocol: 3,
      client: {
        id: 'webchat',
        version: '1.0.0',
        platform: 'web',
        mode: 'webchat',
      },
      role: 'operator',
      scopes: ['operator.read', 'operator.write'],
      caps: ['tool-events'],
      auth: { token: tokenStr },
      locale: navigator.language || 'zh-CN',
      userAgent: 'openclaw-web/1.0.0',
      device,
    },
  }

  appendDebug('→', connectReq)
  ws.send(JSON.stringify(connectReq))
}

// ── 发送 chat.send ────────────────────────────────────
// 规范: sessionKey (agent:agentId:main), message, idempotencyKey
// 参考: src/gateway/protocol/schema/logs-chat.ts ChatSendParamsSchema
function sendMessage(text) {
  const agentId = form.agentId.trim() || 'main'
  const sessionKey = `agent:${agentId}:main`
  currentSessionKey = sessionKey
  const req = {
    type: 'req',
    id: genId(),
    method: 'chat.send',
    params: {
      sessionKey,
      message: text,
      idempotencyKey: `msg-${Date.now()}`,
    },
  }
  appendDebug('→', req)
  ws.send(JSON.stringify(req))
}

// ── 主发送逻辑 ─────────────────────────────────────────
function handleSend() {
  const text = inputText.value.trim()
  if (!text || !connected.value || sending.value) return

  appendMsg('user', text)
  inputText.value = ''
  adjustTextareaHeight()
  sending.value = true

  const r = appendMsg('assistant', '', { typing: true })
  pendingMsgId = r.id

  sendMessage(text)
}

// ── 输入框与调试 ──────────────────────────────────────
function adjustTextareaHeight() {
  nextTick(() => {
    const ta = document.querySelector('.input-area textarea')
    if (ta) {
      ta.style.height = 'auto'
      ta.style.height = Math.min(ta.scrollHeight, 110) + 'px'
    }
  })
}

function toggleDebug() {
  showDebug.value = !showDebug.value
  if (!showDebug.value) {
    messages.value = messages.value.filter((m) => !m.isDebug)
  }
}

// 初始提示
if (messages.value.length === 0) {
  messages.value.push({
    id: 'init',
    role: 'system',
    content:
      '填写 Gateway URL 和 Token 后点击「连接」。\n无需开启 HTTP 端点，直接使用原生 WebSocket 协议。',
  })
}

onUnmounted(() => {
  disconnect()
})
</script>

<style scoped>
.openclaw-chat {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  background: #0f1117;
  color: #e2e8f0;
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 480px;
}

.chat-header {
  padding: 14px 24px;
  background: #1a1d27;
  border-bottom: 1px solid #2d3748;
  display: flex;
  align-items: center;
  gap: 10px;
}

.chat-header h1 {
  font-size: 1.05rem;
  font-weight: 600;
}

.ws-status {
  margin-left: auto;
  font-size: 0.75rem;
  padding: 3px 12px;
  border-radius: 20px;
  border: 1px solid #4a5568;
  color: #a0aec0;
  background: #2d3748;
  transition: all 0.2s;
}

.ws-status.connected {
  color: #68d391;
  border-color: #38a169;
  background: #1c4532;
}

.ws-status.connecting {
  color: #f6e05e;
  border-color: #d69e2e;
  background: #2d2a14;
}

.ws-status.error {
  color: #fc8181;
  border-color: #c53030;
  background: #2d1515;
}

.config-bar {
  display: flex;
  gap: 8px;
  padding: 10px 24px;
  background: #161922;
  border-bottom: 1px solid #2d3748;
  flex-wrap: wrap;
  align-items: center;
}

.config-bar label {
  font-size: 0.78rem;
  color: #a0aec0;
}

.config-bar input {
  background: #1a1d27;
  border: 1px solid #2d3748;
  color: #e2e8f0;
  padding: 5px 10px;
  border-radius: 6px;
  font-size: 0.82rem;
  outline: none;
}

.config-bar input:focus {
  border-color: #4299e1;
}

.config-bar input[type='text']:first-of-type {
  width: 220px;
}

.config-bar input[type='password'] {
  width: 180px;
}

.config-bar input[placeholder='main'] {
  width: 80px;
}

.connect-btn {
  background: #2b6cb0;
  color: #fff;
  border: none;
  padding: 6px 16px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.82rem;
}

.connect-btn:hover {
  background: #2c5282;
}

.connect-btn.disconnect {
  background: #742a2a;
}

.connect-btn.disconnect:hover {
  background: #9b2c2c;
}

.proto-hint {
  font-size: 0.72rem;
  color: #4a5568;
  padding: 4px 24px 6px;
  background: #161922;
  border-bottom: 1px solid #1a202c;
  font-family: monospace;
  white-space: nowrap;
  overflow: hidden;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 200px;
}

.msg {
  max-width: 75%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 0.88rem;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.msg .lbl {
  font-size: 0.65rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  opacity: 0.55;
  margin-bottom: 3px;
}

.msg.user {
  align-self: flex-end;
  background: #2b6cb0;
  color: #fff;
  border-bottom-right-radius: 3px;
}

.msg.assistant {
  align-self: flex-start;
  background: #1a1d27;
  border: 1px solid #2d3748;
  border-bottom-left-radius: 3px;
}

.msg.error {
  align-self: center;
  background: #742a2a;
  border: 1px solid #e53e3e;
  color: #fed7d7;
  font-size: 0.8rem;
}

.msg.system {
  align-self: center;
  background: transparent;
  border: 1px dashed #4a5568;
  color: #718096;
  font-size: 0.76rem;
  text-align: center;
  padding: 5px 12px;
}

.msg.debug {
  align-self: flex-start;
  background: #1a202c;
  border: 1px solid #4a5568;
  color: #718096;
  font-size: 0.72rem;
  font-family: monospace;
  max-width: 90%;
}

.msg.typing::after {
  content: '▋';
  animation: blink 0.75s infinite;
}

@keyframes blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
}

.input-area {
  padding: 14px 24px;
  background: #1a1d27;
  border-top: 1px solid #2d3748;
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.input-area textarea {
  flex: 1;
  background: #0f1117;
  border: 1px solid #2d3748;
  color: #e2e8f0;
  padding: 9px 13px;
  border-radius: 10px;
  font-size: 0.88rem;
  resize: none;
  min-height: 40px;
  max-height: 110px;
  outline: none;
  font-family: inherit;
  line-height: 1.5;
}

.input-area textarea:focus {
  border-color: #4299e1;
}

.input-area textarea:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.send-btn {
  background: #2b6cb0;
  color: #fff;
  border: none;
  padding: 9px 18px;
  border-radius: 10px;
  cursor: pointer;
  font-size: 0.88rem;
}

.send-btn:disabled {
  background: #4a5568;
  cursor: not-allowed;
}

.send-btn:not(:disabled):hover {
  background: #2c5282;
}

.debug-btn {
  background: #2d3748;
  color: #a0aec0;
  border: none;
  padding: 9px 12px;
  border-radius: 10px;
  cursor: pointer;
  font-size: 0.8rem;
}

.debug-btn.active {
  background: #2d6a4f;
  color: #68d391;
}

.messages::-webkit-scrollbar {
  width: 5px;
}

.messages::-webkit-scrollbar-thumb {
  background: #2d3748;
  border-radius: 3px;
}
</style>
