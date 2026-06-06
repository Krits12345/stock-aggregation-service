<template>
  <div>
    <form class="controls" @submit.prevent="load">
      <label>
        Symbol
        <input v-model.trim="form.symbol" placeholder="RELIANCE" required />
      </label>
      <label>
        Timeframe
        <select v-model="form.timeframe">
          <option v-for="tf in timeframes" :key="tf" :value="tf">{{ tf }}</option>
        </select>
      </label>
      <label>
        Start date
        <input v-model="form.startDate" placeholder="2026-01-01 09:15:00" required />
      </label>
      <label>
        End date
        <input v-model="form.endDate" placeholder="2026-01-05 15:30:00" required />
      </label>
      <button type="submit" :disabled="loading">{{ loading ? 'Loading…' : 'Load chart' }}</button>
    </form>

    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="meta" class="meta">
      <strong>{{ meta.symbol }}</strong> · {{ meta.timeframe }} · {{ meta.total }} candles
    </div>

    <CandleChart
      v-if="candles.length"
      :symbol="meta.symbol"
      :timeframe="meta.timeframe"
      :candles="candles"
      :dark="dark"
    />
    <p v-else-if="!loading && requested" class="empty">No candles for the selected range.</p>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import CandleChart from './CandleChart.vue'
import { fetchCandles } from '../api.mjs'

defineProps({ dark: { type: Boolean, default: false } })

const timeframes = ['1m', '5m', '15m', '30m', '1h', '1d']

const form = reactive({
  symbol: 'RELIANCE',
  timeframe: '15m',
  startDate: '2026-01-01 09:15:00',
  endDate: '2026-01-05 15:30:00',
})

const candles = ref([])
const meta = ref(null)
const error = ref('')
const loading = ref(false)
const requested = ref(false)

async function load() {
  loading.value = true
  error.value = ''
  requested.value = true
  try {
    const data = await fetchCandles(form)
    candles.value = data.candles || []
    meta.value = {
      symbol: data.symbol,
      timeframe: data.timeframe,
      total: data.pagination ? data.pagination.total_candles : data.count,
    }
  } catch (e) {
    error.value = e.message
    candles.value = []
    meta.value = null
  } finally {
    loading.value = false
  }
}

load()
</script>

<style scoped>
.controls {
  display: flex; flex-wrap: wrap; gap: 12px; align-items: flex-end;
  background: var(--card); border: 1px solid var(--border);
  padding: 16px; border-radius: 10px; margin-bottom: 16px;
}
.controls label { display: flex; flex-direction: column; font-size: .8rem; color: var(--muted); gap: 4px; }
.controls input, .controls select {
  padding: 8px 10px; border: 1px solid var(--border); border-radius: 6px;
  background: var(--bg); color: var(--text); font-size: .9rem; min-width: 160px;
}
.controls button {
  padding: 9px 18px; border: 0; border-radius: 6px; background: var(--accent);
  color: #fff; font-weight: 600; cursor: pointer;
}
.controls button:disabled { opacity: .6; cursor: default; }
.error { color: #dc2626; background: rgba(220, 38, 38, .12); padding: 10px 14px; border-radius: 6px; }
.meta { margin: 8px 0 12px; color: var(--muted); }
.empty { color: var(--muted); }
</style>
