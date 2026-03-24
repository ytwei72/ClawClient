<template>
  <div class="chat-view">
    <div class="tabs">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        :class="['tab', { active: activeTab === tab.id }]"
        @click="activeTab = tab.id"
      >
        {{ tab.label }}
      </button>
    </div>

    <div class="tab-content">
      <OpenIMChat v-if="activeTab === 'openim'" />
      <OpenClawChat v-else-if="activeTab === 'openclaw'" />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import OpenIMChat from '../components/OpenIMChat.vue'
import OpenClawChat from '../components/OpenClawChat.vue'

const activeTab = ref('openclaw')

const tabs = [
  { id: 'openclaw', label: 'OpenClaw 会话' },
  { id: 'openim', label: 'JVS OpenIM 会话' },
]
</script>

<style scoped>
.chat-view {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.tabs {
  display: flex;
  gap: 0.5rem;
  padding: 0.25rem;
  background: var(--bg-card);
  border-radius: 0.75rem;
  border: 1px solid var(--border);
}

.tab {
  flex: 1;
  padding: 0.75rem 1.25rem;
  border: none;
  border-radius: 0.5rem;
  background: transparent;
  color: var(--text-secondary);
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.tab:hover {
  color: var(--text-primary);
}

.tab.active {
  background: var(--accent);
  color: white;
}

.tab-content {
  flex: 1;
  min-height: 400px;
}
</style>
