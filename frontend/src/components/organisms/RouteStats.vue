<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { CATEGORIES, categoryText, OTHER_WAYS, SURFACES } from '@/constants'
import { km, num } from '@/format'
import * as planner from '@/planner'
import BreakdownBar, { type BreakdownItem } from '@/components/molecules/BreakdownBar.vue'
import StatTile from '@/components/molecules/StatTile.vue'
import WarningList from '@/components/molecules/WarningList.vue'

const { t } = useI18n()

const time = computed(() => {
  const minutes = Math.round(planner.totals.value.hours * 60)
  return `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, '0')}`
})

const wayTypes = computed<BreakdownItem[]>(() => [
  ...CATEGORIES.map((c) => ({
    label: categoryText(c, planner.preferences.value.activity).label,
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
    <h2 id="route-heading">{{ t('planner.stats.heading') }}</h2>
    <p v-if="!hasRoute" class="empty">{{ t('planner.stats.empty') }}</p>
    <template v-else>
      <div class="stats">
        <StatTile :value="km(planner.totals.value.distance)" :label="t('common.unit.km')" />
        <StatTile :value="num(planner.totals.value.ascent)" :label="t('planner.stats.climbing')" />
        <StatTile :value="time" :label="t('planner.stats.movingTime')" />
      </div>
      <label class="speed">
        {{ t('planner.stats.speedBefore') }}
        <input
          v-model.number="planner.speed.value"
          type="number"
          min="3"
          max="50"
          step="1"
          :aria-label="t('planner.stats.speedLabel')"
        />
        {{ t('planner.stats.speedAfter') }}
      </label>
      <BreakdownBar :items="wayTypes" :label="t('planner.stats.wayTypes')" />
      <BreakdownBar :items="surfaces" :label="t('planner.stats.surfaces')" />
      <WarningList
        :warnings="planner.warnings.value"
        @show="(w) => (planner.flyTarget.value = { lat: w.lat, lng: w.lng })"
      />
      <div v-if="planner.viewOnly.value" class="imported">
        <p>{{ t('planner.stats.imported') }}</p>
        <button
          type="button"
          class="primary"
          :disabled="planner.converting.value"
          @click="void planner.editImportedRoute()"
        >
          {{ planner.converting.value ? t('planner.stats.matching') : t('planner.stats.editRoute') }}
        </button>
      </div>
    </template>
    <div class="row actions">
      <button
        type="button"
        :disabled="!planner.canUndo.value"
        :title="t('planner.stats.undoTitle')"
        @click="planner.undo"
      >
        {{ t('planner.stats.undo') }}
      </button>
      <template v-if="!planner.viewOnly.value">
        <button type="button" @click="planner.reverseRoute">{{ t('planner.stats.reverse') }}</button>
        <button type="button" @click="planner.backToStart">{{ t('planner.stats.backToStart') }}</button>
      </template>
      <button type="button" @click="planner.newRoute">{{ t('planner.stats.new') }}</button>
      <button
        v-if="planner.hasOriginal.value && !planner.viewOnly.value && planner.routeId.value !== null"
        type="button"
        :title="t('planner.stats.backToOriginalTitle')"
        @click="void planner.revertToOriginal()"
      >
        {{ t('planner.stats.backToOriginal') }}
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
