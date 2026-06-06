<template>
  <div ref="chartEl" class="chart"></div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import Highcharts from 'highcharts/highstock'

const props = defineProps({
  symbol: { type: String, default: '' },
  timeframe: { type: String, default: '' },
  candles: { type: Array, default: () => [] },
  dark: { type: Boolean, default: false },
})

const chartEl = ref(null)
let chart = null

function buildSeries(candles) {
  const ohlc = []
  const volume = []
  for (const c of candles) {
    const t = Date.parse(c.datetime)
    ohlc.push([t, c.open, c.high, c.low, c.close])
    volume.push([t, c.volume])
  }
  return { ohlc, volume }
}

function render() {
  if (!chartEl.value) return
  const { ohlc, volume } = buildSeries(props.candles)

  // Minimal dark palette so the chart matches night mode.
  const fg = props.dark ? '#e5e7eb' : '#1f2430'
  const grid = props.dark ? '#374151' : '#e5e7eb'
  const themed = props.dark
    ? { chart: { backgroundColor: '#1b1f27' } }
    : { chart: { backgroundColor: '#ffffff' } }

  chart = Highcharts.stockChart(chartEl.value, {
    ...themed,
    rangeSelector: { enabled: false },
    navigator: { enabled: true },
    title: { text: `${props.symbol} — ${props.timeframe}`, style: { color: fg } },
    xAxis: { labels: { style: { color: fg } }, lineColor: grid, gridLineColor: grid },
    yAxis: [
      { labels: { align: 'right', x: -3, style: { color: fg } }, title: { text: 'OHLC', style: { color: fg } }, gridLineColor: grid, height: '70%', lineWidth: 2, resize: { enabled: true } },
      { labels: { align: 'right', x: -3, style: { color: fg } }, title: { text: 'Volume', style: { color: fg } }, gridLineColor: grid, top: '72%', height: '28%', offset: 0, lineWidth: 2 },
    ],
    tooltip: { split: true },
    series: [
      { type: 'candlestick', name: props.symbol, data: ohlc, id: 'ohlc' },
      { type: 'column', name: 'Volume', data: volume, yAxis: 1 },
    ],
  })
}

onMounted(render)
onBeforeUnmount(() => chart && chart.destroy())
watch(() => [props.candles, props.dark], () => {
  if (chart) chart.destroy()
  render()
})
</script>

<style scoped>
.chart {
  width: 100%;
  height: 520px;
}
</style>
