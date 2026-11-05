<script setup lang="ts">
import { computed } from 'vue'
import { CATEGORIES, OTHER_WAYS, SURFACES } from '@/constants'
import * as planner from '@/planner'
import BreakdownBar, { type BreakdownItem } from './BreakdownBar.vue'

const time = computed(() => {
  const minutes = Math.round(planner.totals.value.hours * 60)
  return `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, '0')}`
})

const wayTypes = computed<BreakdownItem[]>(() => [
  ...CATEGORIES.map((c) => ({
    label: c.label,
    color: c.color,
    metres: planner.breakdown.value.wayTypes[c.key] ?? 0,
  })),
  { ...OTHER_WAYS, metres: planner.breakdown.value.other },
])

const surfaces = computed<BreakdownItem[]>(() =>
  SURFACES.map((s) => ({
    label: s.label,
    color: s.color,
    metres: planner.breakdown.value.surfaces[s.key] ?? 0,
  })),
)

const hasRoute = computed(() => planner.profile.value.length > 1)
</script>

<template>
  <section aria-labelledby="route-heading">
    <h2 id="route-heading">Route</h2>
    <p v-if="!hasRoute" class="empty">
      Click the map to set a start point, then click where you want to go. Drag points to move them, drag the
      route to add a via point, right-click a point to remove it.
    </p>
    <template v-else>
      <div class="stats">
        <div>
          <b>{{ (planner.totals.value.distance / 1000).toFixed(1) }}</b
          ><span>km</span>
        </div>
        <div>
          <b>{{ Math.round(planner.totals.value.ascent) }}</b
          ><span>m climbing</span>
        </div>
        <div>
          <b>{{ time }}</b
          ><span>moving time</span>
        </div>
      </div>
      <BreakdownBar :items="wayTypes" label="Way types along the route" />
      <BreakdownBar :items="surfaces" label="Surfaces along the route" />
    </template>
    <div class="row actions">
      <button type="button" :disabled="!planner.canUndo.value" @click="planner.undo">Undo</button>
      <button type="button" @click="planner.reverseRoute">Reverse</button>
      <button type="button" @click="planner.backToStart">Back to start</button>
      <button type="button" @click="planner.newRoute">New</button>
    </div>
  </section>
</template>

<style scoped>
.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4px;
}
.stats b {
  display: block;
  font-family: var(--font-display);
  font-size: 32px;
  font-weight: 600;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.stats span {
  color: var(--muted);
  font-size: 13px;
}
.actions {
  margin-top: 14px;
}
</style>
