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
      <div v-if="planner.warnings.value.length" class="warnings" role="note">
        <p class="warnings-title">
          <span class="badge" aria-hidden="true">!</span>
          {{
            planner.warnings.value.length === 1
              ? 'One closed or restricted way'
              : `${planner.warnings.value.length} closed or restricted ways`
          }}
          on this route
        </p>
        <ul>
          <li v-for="(w, i) in planner.warnings.value" :key="i">
            <span>{{ w.reason }}</span>
            <button type="button" class="link" @click="planner.flyTarget.value = { lat: w.lat, lng: w.lng }">
              Show
            </button>
          </li>
        </ul>
        <p class="hint">Orange legs are forced through these ways. Check on site before you ride.</p>
      </div>
      <div v-if="planner.viewOnly.value" class="imported">
        <p>
          Imported route, shown as recorded. Editing places as few waypoints as needed and checks every leg
          against the original. Where the original uses a way that is closed in the map data now, the route is
          forced through it and marked with a warning.
        </p>
        <button
          type="button"
          class="primary"
          :disabled="planner.converting.value"
          @click="void planner.editImportedRoute()"
        >
          {{ planner.converting.value ? 'Matching the original…' : 'Edit route' }}
        </button>
      </div>
    </template>
    <div class="row actions">
      <button type="button" :disabled="!planner.canUndo.value" @click="planner.undo">Undo</button>
      <template v-if="!planner.viewOnly.value">
        <button type="button" @click="planner.reverseRoute">Reverse</button>
        <button type="button" @click="planner.backToStart">Back to start</button>
      </template>
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
.warnings {
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid #e07b00;
  border-radius: 8px;
  background: color-mix(in srgb, #e07b00 8%, var(--field));
}
.warnings-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 6px;
  font-weight: 600;
}
.badge {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: #e07b00;
  color: #ffffff;
  font-weight: 800;
  font-size: 13px;
  flex: none;
}
.warnings ul {
  list-style: none;
  margin: 0;
  padding: 0;
  font-size: 13.5px;
}
.warnings li {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding: 2px 0;
}
.warnings .hint {
  margin: 6px 0 0;
  font-size: 12.5px;
  color: var(--muted);
}
.imported {
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--field);
}
.imported p {
  margin: 0 0 8px;
  font-size: 13.5px;
  color: var(--muted);
}
</style>
