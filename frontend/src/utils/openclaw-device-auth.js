/**
 * OpenClaw 设备身份与签名（参考 openclaw/ui device-identity.ts + device-auth.ts）
 * 协议: v2 payload 格式，Ed25519 签名
 */
import { getPublicKeyAsync, signAsync, utils } from '@noble/ed25519'

const STORAGE_KEY = 'openclaw-device-identity-v1'

function base64UrlEncode(bytes) {
  let binary = ''
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/, '')
}

function base64UrlDecode(input) {
  const normalized = input.replaceAll('-', '+').replaceAll('_', '/')
  const padded = normalized + '='.repeat((4 - (normalized.length % 4)) % 4)
  const binary = atob(padded)
  const out = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) {
    out[i] = binary.charCodeAt(i)
  }
  return out
}

function bytesToHex(bytes) {
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

async function fingerprintPublicKey(publicKey) {
  const hash = await crypto.subtle.digest('SHA-256', publicKey.slice().buffer)
  return bytesToHex(new Uint8Array(hash))
}

async function generateIdentity() {
  const privateKey = utils.randomSecretKey()
  const publicKey = await getPublicKeyAsync(privateKey)
  const deviceId = await fingerprintPublicKey(publicKey)
  return {
    deviceId,
    publicKey: base64UrlEncode(publicKey),
    privateKey: base64UrlEncode(privateKey),
  }
}

/**
 * 加载或创建设备身份（持久化到 localStorage）
 */
export async function loadOrCreateDeviceIdentity() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      if (
        parsed?.version === 1 &&
        typeof parsed.deviceId === 'string' &&
        typeof parsed.publicKey === 'string' &&
        typeof parsed.privateKey === 'string'
      ) {
        const derivedId = await fingerprintPublicKey(base64UrlDecode(parsed.publicKey))
        if (derivedId !== parsed.deviceId) {
          const updated = { ...parsed, deviceId: derivedId }
          localStorage.setItem(STORAGE_KEY, JSON.stringify(updated))
          return { deviceId: derivedId, publicKey: parsed.publicKey, privateKey: parsed.privateKey }
        }
        return {
          deviceId: parsed.deviceId,
          publicKey: parsed.publicKey,
          privateKey: parsed.privateKey,
        }
      }
    }
  } catch {
    // fall through
  }

  const identity = await generateIdentity()
  const stored = {
    version: 1,
    deviceId: identity.deviceId,
    publicKey: identity.publicKey,
    privateKey: identity.privateKey,
    createdAtMs: Date.now(),
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(stored))
  return identity
}

/**
 * 构建 v2 设备认证 payload（参考 OpenClaw buildDeviceAuthPayload）
 */
export function buildDeviceAuthPayload(params) {
  const { deviceId, clientId, clientMode, role, scopes, signedAtMs, token, nonce } = params
  const scopeStr = Array.isArray(scopes) ? scopes.join(',') : String(scopes || '')
  const tokenStr = token ?? ''
  return ['v2', deviceId, clientId, clientMode, role, scopeStr, String(signedAtMs), tokenStr, nonce].join('|')
}

/**
 * 用私钥对 payload 签名
 */
export async function signDevicePayload(privateKeyBase64Url, payload) {
  const key = base64UrlDecode(privateKeyBase64Url)
  const data = new TextEncoder().encode(payload)
  const sig = await signAsync(data, key)
  return base64UrlEncode(sig)
}

/**
 * 构建完整的 device 对象用于 connect 请求
 * @param {Object} params
 * @param {{ deviceId, publicKey, privateKey }} params.deviceIdentity
 * @param {string} params.clientId - 如 "webchat" | "webchat-ui" | "openclaw-control-ui"
 * @param {string} params.clientMode - 如 "webchat" | "ui"
 * @param {string} params.role - 如 "operator"
 * @param {string[]} params.scopes - 如 ["operator.read", "operator.write"]
 * @param {string} params.authToken - 与 connect.auth.token 一致的 token
 * @param {string} params.nonce - 服务端 challenge 的 nonce
 */
export async function buildConnectDevice(params) {
  const { deviceIdentity, clientId, clientMode, role, scopes, authToken, nonce } = params
  if (!deviceIdentity) return undefined

  const signedAtMs = Date.now()
  const nonceStr = nonce ?? ''
  const payload = buildDeviceAuthPayload({
    deviceId: deviceIdentity.deviceId,
    clientId,
    clientMode,
    role,
    scopes,
    signedAtMs,
    token: authToken ?? null,
    nonce: nonceStr,
  })
  const signature = await signDevicePayload(deviceIdentity.privateKey, payload)
  return {
    id: deviceIdentity.deviceId,
    publicKey: deviceIdentity.publicKey,
    signature,
    signedAt: signedAtMs,
    nonce: nonceStr,
  }
}
