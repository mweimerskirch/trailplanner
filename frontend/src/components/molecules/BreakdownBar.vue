<script setup lang="ts">
import { computed } from 'vue'
import { km } from '@/format'

export interface BreakdownItem {
  label: string
  color: string
  metres: number
}

const props = defineProps<{ items: BreakdownItem[]; label: string }>()

const total = computed(() => props.items.reduce((sum, i) => sum + i.metres, 0))
const rows = computed(() =>
  props.items
    .filter((i) => i.metres > 0)
    .sort((a, b) => b.metres - a.metres)
    .map((i) => ({ ...i, percent: total.value ? (i.metres / total.value) * 100 : 0 })),
)
</script>

<template>
  <div v-if="total > 0" class="breakdown">
    <div class="bar" role="img" :aria-label="label">
      <i v-for="row in rows" :key="row.label" :style="{ width: `${row.percent}%`, background: row.color }" />
    </div>
    <ul>
      <li v-for="row in rows" :key="row.label">
        <span class="swatch" :style="{ background: row.color }" />
        {{ row.label }}
        <span class="value">{{ km(row.metres) }} km · {{ Math.round(row.percent) }}%</span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.breakdown {
  margin-top: 12px;
}
.bar {
  display: flex;
  height: 12px;
  border-radius: 6px;
  overflow: hidden;
  background: var(--line);
  margin-bottom: 8px;
}
.bar i {
  display: block;
  height: 100%;
}
ul {
  list-style: none;
  margin: 0;
  padding: 0;
  font-size: 13.5px;
}
li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 0;
}
.swatch {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  flex: none;
}
.value {
  margin-left: auto;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}
</style>
