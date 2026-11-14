import { ref, watch } from 'vue'
import { api } from './api'
import { mergePreferences } from './planner'
import { currentRider } from './session'
import type { Activity, Preset, RoutingPreferences } from './types'

/** The chosen rider's riding-style presets, in their order. Reloaded when the rider changes. */
export const presets = ref<Preset[]>([])

export async function loadPresets() {
  presets.value = currentRider.value ? await api.presets() : []
}

watch(
  currentRider,
  () => {
    void loadPresets().catch(() => undefined)
  },
  { immediate: true },
)

/** A comparable form: complete (defaults filled in), with keys in a fixed order. */
function key(prefs: Partial<RoutingPreferences>): string {
  const p = mergePreferences(prefs)
  const levels = Object.keys(p.levels)
    .sort()
    .map((k) => `${k}=${p.levels[k as keyof typeof p.levels]}`)
    .join(',')
  return `${p.activity}|${levels}|${p.maxDifficulty}|${p.woods}|${String(p.avoidClimbs)}`
}

/** The preset whose preferences are exactly these, if any. */
export function matchingPreset(prefs: RoutingPreferences): Preset | undefined {
  const k = key(prefs)
  return presets.value.find((p) => key(p.preferences) === k)
}

/** The presets for one activity (presets saved before activities existed are mountain-bike ones). */
export function presetsFor(activity: Activity): Preset[] {
  return presets.value.filter((p) => mergePreferences(p.preferences).activity === activity)
}
