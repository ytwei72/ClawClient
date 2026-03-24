import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 开发时若遇 CORS，可将 API 地址改为 /openim-api，由代理转发
      '/openim-api': {
        target: 'https://jvs-im.wuyinggw.com',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/openim-api/, ''),
      },
      // 本地 OpenClaw：/openclaw-api -> http://127.0.0.1:16232
      '/openclaw-api': {
        target: 'http://127.0.0.1:16232',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/openclaw-api/, ''),
      },
    },
  },
})
