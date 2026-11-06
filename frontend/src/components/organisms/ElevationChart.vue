<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, useTemplateRef, watch } from 'vue'
import type { ProfilePoint } from '@/profile'

/**
 * Elevation over distance. Pointing at the chart sets the hovered point (v-model:hover-index), so
 * the map can mark it; a point hovered elsewhere is marked here.
 */
const props = withDefaults(defineProps<{ points: ProfilePoint[]; label?: string }>(), {
  label: 'Elevation profile of the route',
})
const hoverIndex = defineModel<number | null>('hoverIndex', { default: null })

const canvas = useTemplateRef<HTMLCanvasElement>('canvas')
const PAD = { left: 46, right: 12, top: 22, bottom: 20 }
let resizeObserver: ResizeObserver | null = null

const tooltip = computed(() => {
  const p = hoverIndex.value === null ? undefined : props.points[hoverIndex.value]
  if (!p) return ''
  return `${(p.distance / 1000).toFixed(2)} km · ${p.ele === null ? '–' : `${Math.round(p.ele)} m`}`
})

function cssVar(name: string) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

function draw() {
  const el = canvas.value
  const points = props.points
  if (!el || points.length < 2) return
  const { width, height } = el.getBoundingClientRect()
  if (width === 0) return
  const dpr = window.devicePixelRatio || 1
  el.width = Math.round(width * dpr)
  el.height = Math.round(height * dpr)
  const ctx = el.getContext('2d')
  if (!ctx) return
  ctx.scale(dpr, dpr)

  // A loop, not Math.min(...array): spreading a long track can exceed the engine's argument limit.
  let lo = Infinity
  let hi = -Infinity
  for (const p of points) {
    if (p.ele === null) continue
    if (p.ele < lo) lo = p.ele
    if (p.ele > hi) hi = p.ele
  }
  if (lo > hi) return
  if (hi - lo < 50) {
    const mid = (hi + lo) / 2
    lo = mid - 25
    hi = mid + 25
  }
  const total = points.at(-1)?.distance ?? 1
  const x = (d: number) => PAD.left + (d / total) * (width - PAD.left - PAD.right)
  const y = (e: number) => PAD.top + (1 - (e - lo) / (hi - lo)) * (height - PAD.top - PAD.bottom)

  const muted = cssVar('--muted')
  const route = cssVar('--route')
  ctx.font = '12px Barlow, system-ui, sans-serif'
  ctx.fillStyle = muted
  ctx.strokeStyle = cssVar('--line')
  ctx.lineWidth = 1
  for (const v of [lo, (lo + hi) / 2, hi]) {
    ctx.beginPath()
    ctx.moveTo(PAD.left, y(v))
    ctx.lineTo(width - PAD.right, y(v))
    ctx.stroke()
    ctx.fillText(`${Math.round(v)} m`, 4, y(v) + 4)
  }
  ctx.fillText('0', PAD.left, height - 5)
  const label = `${(total / 1000).toFixed(1)} km`
  ctx.fillText(label, width - PAD.right - ctx.measureText(label).width, height - 5)

  ctx.beginPath()
  // Straight legs have no elevation: carry the last known height across them.
  let known = points.find((p) => p.ele !== null)?.ele ?? lo
  points.forEach((p, i) => {
    if (p.ele !== null) known = p.ele
    const py = y(known)
    if (i === 0) ctx.moveTo(x(p.distance), py)
    else ctx.lineTo(x(p.distance), py)
  })
  ctx.strokeStyle = route
  ctx.lineWidth = 2
  ctx.stroke()
  ctx.lineTo(x(total), height - PAD.bottom)
  ctx.lineTo(PAD.left, height - PAD.bottom)
  ctx.closePath()
  ctx.globalAlpha = 0.15
  ctx.fillStyle = route
  ctx.fill()
  ctx.globalAlpha = 1

  const hover = hoverIndex.value === null ? undefined : points[hoverIndex.value]
  if (hover) {
    ctx.strokeStyle = cssVar('--ink')
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.moveTo(x(hover.distance), PAD.top)
    ctx.lineTo(x(hover.distance), height - PAD.bottom)
    ctx.stroke()
  }
}

function onPointerMove(event: PointerEvent) {
  const el = canvas.value
  const points = props.points
  if (!el || points.length < 2) return
  const rect = el.getBoundingClientRect()
  const total = points.at(-1)?.distance ?? 0
  const ratio = (event.clientX - rect.left - PAD.left) / (rect.width - PAD.left - PAD.right)
  const target = Math.max(0, Math.min(1, ratio)) * total
  let lo = 0
  let hi = points.length - 1
  while (lo < hi) {
    const mid = (lo + hi) >> 1
    if ((points[mid]?.distance ?? 0) < target) lo = mid + 1
    else hi = mid
  }
  hoverIndex.value = lo
}

function onPointerLeave() {
  hoverIndex.value = null
}

watch([() => props.points, hoverIndex], draw)
onMounted(() => {
  if (canvas.value) {
    resizeObserver = new ResizeObserver(draw)
    resizeObserver.observe(canvas.value)
  }
})
onBeforeUnmount(() => resizeObserver?.disconnect())
</script>

<template>
  <div class="elevation" role="img" :aria-label="label">
    <canvas ref="canvas" @pointermove="onPointerMove" @pointerleave="onPointerLeave" />
    <div class="tip">{{ tooltip }}</div>
  </div>
</template>

<style scoped>
.elevation {
  position: relative;
  height: 130px;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 10px;
}
canvas {
  display: block;
  width: 100%;
  height: 100%;
  cursor: crosshair;
  touch-action: none;
}
.tip {
  position: absolute;
  top: 6px;
  right: 10px;
  font-size: 13px;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
  pointer-events: none;
}
</style>
