<script setup lang="ts">
import { computed, nextTick, ref, toRaw, useTemplateRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/api'
import { askConfirm } from '@/confirm'
import {
  ACTIVITIES,
  categoriesFor,
  categoryText,
  DEFAULT_PREFERENCES,
  DIFFICULTIES,
  levelLabel,
  LEVELS,
  WOODS_OPTIONS,
} from '@/constants'
import { mergePreferences, preferences, setStatus } from '@/planner'
import { loadPresets, matchingPreset, presets, presetsFor } from '@/presets'
import type { Activity, PreferenceLevel, Preset, WayCategory } from '@/types'

/**
 * Riding style: how the route is calculated. Compact by default (a preset and a one-line
 * summary); "Customize" opens the individual settings. Changes recalculate the route at once, so
 * this stays next to the map.
 */

const { t, locale } = useI18n()
const open = ref(false)
const newName = ref('')
const nameInput = useTemplateRef<HTMLInputElement>('nameInput')
const naming = ref(false)
/** The preset last chosen, so "Update" knows which one to overwrite after adjustments. */
const basePresetId = ref<number | null>(null)

const activity = computed(() => preferences.value.activity)
/** The presets of the current activity, in the rider's order. */
const shown = computed(() => presetsFor(activity.value))
const categories = computed(() => categoriesFor(activity.value))
const current = computed(() => matchingPreset(preferences.value))
const base = computed(() => presets.value.find((p) => p.id === basePresetId.value))

/** The dropdown: a matching preset, or "custom" when the settings match none. */
const selected = computed({
  get: () => (current.value ? String(current.value.id) : 'custom'),
  set: (value: string) => {
    const preset = presets.value.find((p) => String(p.id) === value)
    if (preset) apply(preset)
  },
})

const summary = computed(() => {
  const p = preferences.value
  const parts: string[] = []
  if (p.activity === 'MTB')
    parts.push(
      p.maxDifficulty === 'ANY'
        ? t('planner.style.summary.anyDifficulty')
        : t('planner.style.summary.maxDifficulty', { difficulty: p.maxDifficulty }),
    )
  if (p.activity === 'ROAD') parts.push(t('planner.style.summary.pavedOnly'))
  if (p.activity !== 'ROAD' && p.woods === 'PREFER') parts.push(t('planner.style.summary.prefersWoods'))
  if (p.activity !== 'ROAD' && p.woods === 'STRONGLY_PREFER')
    parts.push(t('planner.style.summary.stronglyPrefersWoods'))
  if (p.avoidClimbs) parts.push(t('planner.style.summary.avoidsClimbs'))
  const favourites = categories.value
    .filter((c) => p.levels[c.key] === 'FAVOURITE')
    // Lower case inside the sentence, except in German, where nouns keep their capital.
    .map((c) => {
      const label = categoryText(c, p.activity).label
      return locale.value === 'de' ? label : label.toLocaleLowerCase(locale.value)
    })
  if (favourites.length) parts.push(t('planner.style.summary.loves', { ways: favourites.join(', ') }))
  return parts.join(' · ')
})

function apply(preset: Preset) {
  // A plain copy: presets are reactive, so the preset itself must not be edited by the sliders.
  preferences.value = mergePreferences(structuredClone(toRaw(preset.preferences)))
  basePresetId.value = preset.id
}

/** Switching activity starts from its first preset (or the defaults if the rider deleted them all). */
function setActivity(value: Activity) {
  if (value === activity.value) return
  const first = presetsFor(value)[0]
  if (first) {
    apply(first)
  } else {
    preferences.value = mergePreferences({ ...structuredClone(DEFAULT_PREFERENCES), activity: value })
    basePresetId.value = null
  }
}

function levelIndex(category: WayCategory): number {
  return LEVELS.indexOf(preferences.value.levels[category])
}

function setLevel(category: WayCategory, event: Event) {
  const index = Number((event.target as HTMLInputElement).value)
  const level: PreferenceLevel | undefined = LEVELS[index]
  if (level) preferences.value.levels[category] = level
}

async function act(task: () => Promise<void>) {
  try {
    await task()
  } catch (error) {
    setStatus(error instanceof Error ? error.message : t('planner.style.saveFailed'), 'error')
  }
}

async function startNaming() {
  naming.value = true
  newName.value = ''
  await nextTick()
  nameInput.value?.focus()
}

const saveAsNew = () =>
  act(async () => {
    const name = newName.value.trim()
    if (!name) return
    const preset = await api.createPreset(name, preferences.value)
    await loadPresets()
    basePresetId.value = preset.id
    naming.value = false
    setStatus(t('planner.style.saved', { name: preset.name }), 'info', 2500)
  })

const updateBase = () =>
  act(async () => {
    const preset = base.value
    if (!preset) return
    await api.updatePreset(preset.id, preset.name, preferences.value)
    await loadPresets()
    setStatus(t('planner.style.updated', { name: preset.name }), 'info', 2500)
  })

const remove = (preset: Preset) =>
  act(async () => {
    const ok = await askConfirm({
      title: t('planner.style.confirmDelete.title'),
      message: t('planner.style.confirmDelete.message', { name: preset.name }),
      confirmLabel: t('planner.style.confirmDelete.confirm'),
      danger: true,
    })
    if (!ok) return
    await api.deletePreset(preset.id)
    if (basePresetId.value === preset.id) basePresetId.value = null
    await loadPresets()
  })
</script>

<template>
  <section aria-labelledby="prefs-heading">
    <h2 id="prefs-heading">{{ t('planner.style.heading') }}</h2>
    <div class="activities" role="radiogroup" :aria-label="t('planner.style.activity')">
      <button
        v-for="a in ACTIVITIES"
        :key="a.value"
        type="button"
        role="radio"
        :aria-checked="activity === a.value"
        :class="{ active: activity === a.value }"
        @click="setActivity(a.value)"
      >
        {{ a.label }}
      </button>
    </div>
    <div class="row">
      <label class="visually-hidden" for="preset">{{ t('planner.style.preset') }}</label>
      <select id="preset" v-model="selected" class="grow">
        <option v-for="p in shown" :key="p.id" :value="String(p.id)">{{ p.name }}</option>
        <option v-if="!current" value="custom" disabled>
          {{ base ? t('planner.style.customBasedOn', { name: base.name }) : t('planner.style.custom') }}
        </option>
      </select>
      <button type="button" :aria-expanded="open" aria-controls="prefs-details" @click="open = !open">
        {{ open ? t('planner.style.done') : t('planner.style.customize') }}
      </button>
    </div>
    <p class="summary">{{ summary }}</p>

    <div v-if="open" id="prefs-details" class="details">
      <p v-if="activity === 'ROAD'" class="note">{{ t('planner.style.noteRoad') }}</p>
      <p v-else-if="activity === 'HORSE'" class="note">{{ t('planner.style.noteHorse') }}</p>
      <div v-for="c in categories" :key="c.key" class="pref">
        <label :for="`level-${c.key}`">{{ categoryText(c, activity).label }}</label>
        <output :for="`level-${c.key}`" :class="`level-${preferences.levels[c.key].toLowerCase()}`">
          {{ levelLabel(preferences.levels[c.key]) }}
        </output>
        <small>{{ categoryText(c, activity).hint }}</small>
        <input
          :id="`level-${c.key}`"
          type="range"
          min="0"
          :max="LEVELS.length - 1"
          step="1"
          :value="levelIndex(c.key)"
          :aria-valuetext="levelLabel(preferences.levels[c.key])"
          @input="setLevel(c.key, $event)"
        />
      </div>

      <div v-if="activity === 'MTB'" class="field">
        <label for="difficulty">{{ t('planner.style.hardest') }}</label>
        <select id="difficulty" v-model="preferences.maxDifficulty">
          <option v-for="d in DIFFICULTIES" :key="d.value" :value="d.value">{{ d.label }}</option>
        </select>
        <small>{{ t('planner.style.hardestHint') }}</small>
      </div>
      <div v-if="activity !== 'ROAD'" class="field">
        <label for="woods">{{ t('planner.style.woods') }}</label>
        <select id="woods" v-model="preferences.woods">
          <option v-for="w in WOODS_OPTIONS" :key="w.value" :value="w.value">{{ w.label }}</option>
        </select>
      </div>
      <div class="field check">
        <input id="climbs" v-model="preferences.avoidClimbs" type="checkbox" />
        <label for="climbs">{{ t('planner.style.avoidClimbs') }}</label>
      </div>

      <div class="preset-actions">
        <template v-if="!current">
          <button v-if="base" type="button" @click="updateBase">
            {{ t('planner.style.update', { name: base.name }) }}
          </button>
          <button v-if="!naming" type="button" @click="startNaming">
            {{ t('planner.style.saveAsNew') }}
          </button>
          <form v-else class="row" @submit.prevent="saveAsNew">
            <label class="visually-hidden" for="preset-name">{{ t('planner.style.presetName') }}</label>
            <input
              id="preset-name"
              ref="nameInput"
              v-model="newName"
              type="text"
              maxlength="60"
              :placeholder="t('planner.style.presetName')"
              class="grow"
              @keydown.esc="naming = false"
            />
            <button type="submit" class="primary" :disabled="!newName.trim()">
              {{ t('common.action.save') }}
            </button>
          </form>
        </template>
        <button v-else type="button" class="link danger" @click="remove(current)">
          {{ t('planner.style.deletePreset', { name: current.name }) }}
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.activities {
  display: flex;
  gap: 4px;
  margin-bottom: 8px;
}
.activities button {
  flex: 1;
  padding: 5px 4px;
  font-size: 13px;
}
.activities button.active {
  background: var(--moss);
  border-color: var(--moss);
  color: #fff;
}
.note {
  margin: 0 0 12px;
  color: var(--muted);
  font-size: 12.5px;
}
.grow {
  flex: 1;
  min-width: 0;
}
.summary {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 13px;
}
.details {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}
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
.preset-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  margin-top: 12px;
}
.preset-actions form {
  width: 100%;
}
.danger {
  color: var(--warn);
}
</style>
