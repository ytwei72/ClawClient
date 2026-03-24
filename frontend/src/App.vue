<template>
  <div class="app">
    <header class="app-header">
      <h1>Claw 客户端</h1>
      <p class="subtitle">OpenIM & OpenClaw 会话</p>
    </header>
    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>

<script setup>
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

:root {
  --bg-dark: #0f0f12;
  --bg-card: #1a1a20;
  --bg-input: #252530;
  --accent: #6366f1;
  --accent-hover: #818cf8;
  --text-primary: #f4f4f5;
  --text-secondary: #a1a1aa;
  --border: #27272a;
  --success: #22c55e;
  --error: #ef4444;
}

body {
  font-family: 'JetBrains Mono', 'Fira Code', 'SF Mono', monospace;
  background: var(--bg-dark);
  color: var(--text-primary);
  min-height: 100vh;
  line-height: 1.6;
}

.app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-header {
  padding: 1.5rem 2rem;
  border-bottom: 1px solid var(--border);
  background: linear-gradient(180deg, rgba(99, 102, 241, 0.08) 0%, transparent 100%);
}

.app-header h1 {
  font-size: 1.5rem;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.app-header .subtitle {
  margin-top: 0.25rem;
  color: var(--text-secondary);
  font-size: 0.875rem;
}

.app-main {
  flex: 1;
  padding: 2rem;
  max-width: 900px;
  margin: 0 auto;
  width: 100%;
}
</style>
