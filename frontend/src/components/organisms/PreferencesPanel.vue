<script setup lang="ts">
import {
  CATEGORIES,
  DEFAULT_PREFERENCES,
  DIFFICULTIES,
  LEVEL_LABELS,
  LEVELS,
  WOODS_OPTIONS,
} from '@/constants'
import { preferences, speed } from '@/planner'
import type { PreferenceLevel, WayCategory } from '@/types'

function levelIndex(category: WayCategory): number {
  return LEVELS.indexOf(preferences.value.levels[category])
}

function setLevel(category: WayCategory, event: Event) {
  const index = Number((event.target as HTMLInputElement).value)
  const level: PreferenceLevel | undefined = LEVELS[index]
  if (level) preferences.value.levels[category] = level
}

function reset() {
  preferences.value = structuredClone(DEFAULT_PREFERENCES)
}
</script>

<template>
  <section aria-labelledby="prefs-heading">
    <h2 id="prefs-heading">Way preferences</h2>
    <div v-for="c in CATEGORIES" :key="c.key" class="pref">
      <label :for="`level-${c.key}`">{{ c.label }}</label>
      <output :for="`level-${c.key}`" :class="`level-${preferences.levels[c.key].toLowerCase()}`">
        {{ LEVEL_LABELS[preferences.levels[c.key]] }}
      </output>
      <small>{{ c.hint }}</small>
      <input
        :id="`level-${c.key}`"
        type="range"
        min="0"
        :max="LEVELS.length - 1"
        step="1"
        :value="levelIndex(c.key)"
        :aria-valuetext="LEVEL_LABELS[preferences.levels[c.key]]"
        @input="setLevel(c.key, $event)"
      />
    </div>

    <div class="field">
      <label for="difficulty">Hardest trail allowed</label>
      <select id="difficulty" v-model="preferences.maxDifficulty">
        <option v-for="d in DIFFICULTIES" :key="d.value" :value="d.value">{{ d.label }}</option>
      </select>
      <small>Only applies where trails are tagged with a difficulty (mtb:scale).</small>
    </div>
    <div class="field">
      <label for="woods">Woods</label>
      <select id="woods" v-model="preferences.woods">
        <option v-for="w in WOODS_OPTIONS" :key="w.value" :value="w.value">{{ w.label }}</option>
      </select>
    </div>
    <div class="field check">
      <input id="climbs" v-model="preferences.avoidClimbs" type="checkbox" />
      <label for="climbs">Avoid steep climbs</label>
    </div>
    <div class="field">
      <label for="speed">Average speed on the flat (km/h)</label>
      <input id="speed" v-model.number="speed" type="number" min="5" max="40" step="1" />
    </div>
    <button type="button" @click="reset">Reset to defaults</button>
  </section>
</template>

<style scoped>
.pref {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 0 10px;
  align-items: baseline;
  margin-bottom: 10px;
}
.pref label {
  font-weight: 500;
}
.pref small {
  grid-column: 1 / -1;
  color: var(--muted);
  font-size: 12.5px;
}
.pref output {
  font-size: 13px;
  font-weight: 600;
}
.pref input {
  grid-column: 1 / -1;
  width: 100%;
  accent-color: var(--moss);
}
.level-never {
  color: var(--warn);
}
.level-favourite {
  color: var(--moss);
}
.field small {
  display: block;
  color: var(--muted);
  font-size: 12.5px;
  margin-top: 3px;
}
</style>
