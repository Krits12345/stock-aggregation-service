// API wrapper. In dev, requests go to /api and Vite proxies them to the backend.
// Override the base with VITE_API_BASE for a deployed backend.
const BASE = import.meta.env.VITE_API_BASE || ''

const TOKEN_KEY = 'auth_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}
export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

async function postJson(path, body) {
  const resp = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  const data = await resp.json().catch(() => ({}))
  if (!resp.ok) throw new Error(data.message || `Request failed (HTTP ${resp.status})`)
  return data
}

export function signup(email, password) {
  return postJson('/api/v1/auth/signup', { email, password })
}
export function login(email, password) {
  return postJson('/api/v1/auth/login', { email, password })
}

export async function fetchCandles({ symbol, timeframe, startDate, endDate }) {
  const params = new URLSearchParams({
    symbol,
    timeframe,
    start_date: startDate,
    end_date: endDate,
  })
  const headers = {}
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const resp = await fetch(`${BASE}/api/v1/candles?${params.toString()}`, { headers })
  const data = await resp.json().catch(() => ({}))
  if (!resp.ok) throw new Error(data.message || `Request failed (HTTP ${resp.status})`)
  return data
}
