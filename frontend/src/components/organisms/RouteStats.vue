<script setup lang="ts">
import { computed } from 'vue'
import { CATEGORIES, OTHER_WAYS, SURFACES } from '@/constants'
import * as planner from '@/planner'
import BreakdownBar, { type BreakdownItem } from '@/components/molecules/BreakdownBar.vue'
import StatTile from '@/components/molecules/StatTile.vue'
import WarningList from '@/components/molecules/WarningList.vue'

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
        <StatTile :value="(planner.totals.value.distance / 1000).toFixed(1)" label="km" />
        <StatTile :value="Math.round(planner.totals.value.ascent)" label="m climbing" />
        <StatTile :value="time" label="moving time" />
      </div>
      <label class="speed">
        Moving time at
        <input
          v-model.number="planner.speed.value"
          type="number"
          min="5"
          max="40"
          step="1"
          aria-label="Average speed on the flat in km/h"
        />
        km/h on the flat
      </label>
      <BreakdownBar :items="wayTypes" label="Way types along the route" />
      <BreakdownBar :items="surfaces" label="Surfaces along the route" />
      <WarningList
        :warnings="planner.warnings.value"
        @show="(w) => (planner.flyTarget.value = { lat: w.lat, lng: w.lng })"
      />
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
      <button type="button" :disabled="!planner.canUndo.value" title="Undo (Ctrl+Z)" @click="planner.undo">
        Undo
      </button>
      <template v-if="!planner.viewOnly.value">
        <button type="button" @click="planner.reverseRoute">Reverse</button>
        <button type="button" @click="planner.backToStart">Back to start</button>
      </template>
      <button type="button" @click="planner.newRoute">New</button>
      <button
        v-if="planner.hasOriginal.value && !planner.viewOnly.value && planner.routeId.value !== null"
        type="button"
        title="Put the track back as it was imported"
        @click="void planner.revertToOriginal()"
      >
        Back to original
      </button>
    </div>
  </section>
</template>

<style scoped>
.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4px;
}
.speed {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--muted);
}
.speed input {
  width: 58px;
  padding: 2px 6px;
}
.actions {
  margin-top: 14px;
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
