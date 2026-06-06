<template>
  <div class="auth-card">
    <div class="tabs">
      <button :class="{ active: mode === 'login' }" @click="mode = 'login'">Log in</button>
      <button :class="{ active: mode === 'signup' }" @click="mode = 'signup'">Sign up</button>
    </div>

    <form @submit.prevent="submit">
      <label>
        Email
        <input v-model.trim="email" type="email" placeholder="you@example.com" required />
      </label>
      <label>
        Password
        <input v-model="password" type="password" placeholder="at least 6 characters" required />
      </label>

      <p v-if="error" class="error">{{ error }}</p>

      <button class="primary" type="submit" :disabled="busy">
        {{ busy ? 'Please wait…' : (mode === 'login' ? 'Log in' : 'Create account') }}
      </button>
    </form>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { login, signup } from '../api.mjs'

const emit = defineEmits(['authenticated'])

const mode = ref('login')
const email = ref('')
const password = ref('')
const error = ref('')
const busy = ref(false)

async function submit() {
  busy.value = true
  error.value = ''
  try {
    const fn = mode.value === 'login' ? login : signup
    const data = await fn(email.value, password.value)
    emit('authenticated', { token: data.token, email: data.email })
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.auth-card {
  max-width: 380px;
  margin: 48px auto;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, .08);
}
.tabs { display: flex; gap: 8px; margin-bottom: 20px; }
.tabs button {
  flex: 1; padding: 8px; border: 0; border-radius: 6px; cursor: pointer;
  background: transparent; color: var(--muted); font-weight: 600;
}
.tabs button.active { background: var(--accent); color: #fff; }
form { display: flex; flex-direction: column; gap: 14px; }
label { display: flex; flex-direction: column; gap: 4px; font-size: .82rem; color: var(--muted); }
input {
  padding: 9px 11px; border: 1px solid var(--border); border-radius: 6px;
  background: var(--bg); color: var(--text); font-size: .9rem;
}
.primary {
  padding: 10px; border: 0; border-radius: 6px; background: var(--accent);
  color: #fff; font-weight: 600; cursor: pointer;
}
.primary:disabled { opacity: .6; cursor: default; }
.error { color: #dc2626; margin: 0; font-size: .85rem; }
</style>
