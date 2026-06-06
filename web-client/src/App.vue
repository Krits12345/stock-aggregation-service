<template>
  <div class="page">
    <header>
      <div>
        <h1>Stock Candles Viewer</h1>
        <p class="sub">OHLCV candlestick data aggregated to a requested timeframe</p>
      </div>
      <div class="actions">
        <button class="ghost" @click="toggleTheme" :title="dark ? 'Switch to day' : 'Switch to night'">
          {{ dark ? '☀ Day' : '🌙 Night' }}
        </button>
        <template v-if="user">
          <span class="who">{{ user.email }}</span>
          <button class="ghost" @click="logout">Log out</button>
        </template>
      </div>
    </header>

    <AuthForm v-if="!user" @authenticated="onAuth" />
    <CandlesView v-else :dark="dark" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AuthForm from './components/AuthForm.vue'
import CandlesView from './components/CandlesView.vue'
import { getToken, setToken } from './api.mjs'

const THEME_KEY = 'theme'
const dark = ref(false)
const user = ref(null)

function applyTheme() {
  document.documentElement.setAttribute('data-theme', dark.value ? 'dark' : 'light')
}
function toggleTheme() {
  dark.value = !dark.value
  localStorage.setItem(THEME_KEY, dark.value ? 'dark' : 'light')
  applyTheme()
}

function onAuth({ token, email }) {
  setToken(token)
  localStorage.setItem('user_email', email || '')
  user.value = { email }
}
function logout() {
  setToken(null)
  localStorage.removeItem('user_email')
  user.value = null
}

onMounted(() => {
  dark.value = localStorage.getItem(THEME_KEY) === 'dark'
  applyTheme()
  if (getToken()) user.value = { email: localStorage.getItem('user_email') || '' }
})
</script>

<style>
:root {
  --bg: #f5f6f8;
  --card: #ffffff;
  --text: #1f2430;
  --muted: #6b7280;
  --border: #d1d5db;
  --accent: #2563eb;
  font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
}
:root[data-theme='dark'] {
  --bg: #11141a;
  --card: #1b1f27;
  --text: #e5e7eb;
  --muted: #9ca3af;
  --border: #374151;
  --accent: #3b82f6;
}
body { margin: 0; background: var(--bg); color: var(--text); transition: background .2s, color .2s; }
.page { max-width: 1000px; margin: 0 auto; padding: 24px 16px 48px; }
header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 20px; }
header h1 { margin: 0 0 4px; font-size: 1.6rem; }
.sub { margin: 0; color: var(--muted); }
.actions { display: flex; align-items: center; gap: 10px; }
.who { color: var(--muted); font-size: .85rem; }
.ghost {
  padding: 7px 12px; border: 1px solid var(--border); border-radius: 6px;
  background: var(--card); color: var(--text); cursor: pointer; font-size: .85rem;
}
.ghost:hover { border-color: var(--accent); }
</style>
