<script setup lang="ts">
import { computed, nextTick, ref, toRaw, useTemplateRef } from 'vue'
import { api } from '@/api'
import { askConfirm } from '@/confirm'
import { CATEGORIES, DIFFICULTIES, LEVEL_LABELS, LEVELS, WOODS_OPTIONS } from '@/constants'
import { mergePreferences, preferences, setStatus } from '@/planner'
import { loadPresets, matchingPreset, presets } from '@/presets'
import type { PreferenceLevel, Preset, WayCategory } from '@/types'

/**
 * Riding style: how the route is calculated. Compact by default (a preset and a one-line
 * summary); "Customize" opens the individual settings. Changes recalculate the route at once, so
 * this stays next to the map.
 */

const open = ref(false)
const newName = ref('')
const nameInput = useTemplateRef<HTMLInputElement>('nameInput')
const naming = ref(false)
/** The preset last chosen, so "Update" knows which one to overwrite after adjustments. */
const basePresetId = ref<number | null>(null)

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
  const parts = [p.maxDifficulty === 'ANY' ? 'any difficulty' : `${p.maxDifficulty} max`]
  if (p.woods === 'PREFER') parts.push('prefers woods')
  if (p.woods === 'STRONGLY_PREFER') parts.push('strongly prefers woods')
  if (p.avoidClimbs) parts.push('avoids steep climbs')
  const favourites = CATEGORIES.filter((c) => p.levels[c.key] === 'FAVOURITE').map((c) =>
    c.label.toLowerCase(),
  )
  if (favourites.length) parts.push(`loves ${favourites.join(', ')}`)
  return parts.join(' · ')
})

function apply(preset: Preset) {
  // A plain copy: presets are reactive, so the preset itself must not be edited by the sliders.
  preferences.value = mergePreferences(structuredClone(toRaw(preset.preferences)))
  basePresetId.value = preset.id
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
    setStatus(error instanceof Error ? error.message : 'Could not save the preset.', 'error')
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
    setStatus(`Saved the preset “${preset.name}”.`, 'info', 2500)
  })

const updateBase = () =>
  act(async () => {
    const preset = base.value
    if (!preset) return
    await api.updatePreset(preset.id, preset.name, preferences.value)
    await loadPresets()
    setStatus(`Updated “${preset.name}”.`, 'info', 2500)
  })

const remove = (preset: Preset) =>
  act(async () => {
    const ok = await askConfirm({
      title: 'Delete preset?',
      message: `The preset “${preset.name}” will be deleted. Routes planned with it keep their settings.`,
      confirmLabel: 'Delete preset',
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
    <h2 id="prefs-heading">Riding style</h2>
    <div class="row">
      <label class="visually-hidden" for="preset">Preset</label>
      <select id="preset" v-model="selected" class="grow">
        <option v-for="p in presets" :key="p.id" :value="String(p.id)">{{ p.name }}</option>
        <option v-if="!current" value="custom" disabled>
          {{ base ? `Custom (based on ${base.name})` : 'Custom' }}
        </option>
      </select>
      <button type="button" :aria-expanded="open" aria-controls="prefs-details" @click="open = !open">
        {{ open ? 'Done' : 'Customize…' }}
      </button>
    </div>
    <p class="summary">{{ summary }}</p>

    <div v-if="open" id="prefs-details" class="details">
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

      <div class="preset-actions">
        <template v-if="!current">
          <button v-if="base" type="button" @click="updateBase">Update “{{ base.name }}”</button>
          <button v-if="!naming" type="button" @click="startNaming">Save as new preset…</button>
          <form v-else class="row" @submit.prevent="saveAsNew">
            <label class="visually-hidden" for="preset-name">Preset name</label>
            <input
              id="preset-name"
              ref="nameInput"
              v-model="newName"
              type="text"
              maxlength="60"
              placeholder="Preset name"
              class="grow"
              @keydown.esc="naming = false"
            />
            <button type="submit" class="primary" :disabled="!newName.trim()">Save</button>
          </form>
        </template>
        <button v-else type="button" class="link danger" @click="remove(current)">
          Delete preset “{{ current.name }}”
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
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
