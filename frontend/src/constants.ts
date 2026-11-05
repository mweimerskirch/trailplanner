import type {
  PreferenceLevel,
  RoutingPreferences,
  Surface,
  TrailDifficulty,
  WayCategory,
  WoodsPreference,
} from './types'

export interface CategoryInfo {
  key: WayCategory
  label: string
  hint: string
  color: string
}

export const CATEGORIES: readonly CategoryInfo[] = [
  { key: 'TRAIL', label: 'Singletrails & paths', hint: 'path, bridleway', color: '#7B3FA0' },
  { key: 'ROUGH_TRACK', label: 'Rough forest tracks', hint: 'track, grade 3–5 or unknown', color: '#6B4F3A' },
  { key: 'GRAVEL_TRACK', label: 'Gravel forest roads', hint: 'track, grade 1–2', color: '#B8923F' },
  { key: 'FOOTWAY', label: 'Footways', hint: 'footway, pedestrian, steps', color: '#3F8A75' },
  { key: 'CYCLEWAY', label: 'Cycleways', hint: 'cycleway', color: '#2E6FB0' },
  { key: 'QUIET_ROAD', label: 'Quiet roads', hint: 'residential, service, unclassified', color: '#8C968F' },
  { key: 'MAIN_ROAD', label: 'Main roads', hint: 'secondary, tertiary', color: '#D9822B' },
  { key: 'BUSY_ROAD', label: 'Busy roads', hint: 'primary, trunk', color: '#B3261E' },
]

export const OTHER_WAYS = { label: 'Other', color: '#BFC6BD' }

export const LEVELS: readonly PreferenceLevel[] = ['NEVER', 'AVOID', 'NEUTRAL', 'PREFER', 'FAVOURITE']

export const LEVEL_LABELS: Record<PreferenceLevel, string> = {
  NEVER: 'Never',
  AVOID: 'Avoid',
  NEUTRAL: 'Neutral',
  PREFER: 'Prefer',
  FAVOURITE: 'Favourite',
}

export const DIFFICULTIES: readonly { value: TrailDifficulty; label: string }[] = [
  { value: 'S0', label: 'S0 – flowing, no obstacles' },
  { value: 'S1', label: 'S1 – small roots and rocks' },
  { value: 'S2', label: 'S2 – bigger roots, steps' },
  { value: 'S3', label: 'S3 – blocky, switchbacks' },
  { value: 'S4', label: 'S4 – very steep, technical' },
  { value: 'S5', label: 'S5 – extreme' },
  { value: 'ANY', label: 'No limit' },
]

export const WOODS_OPTIONS: readonly { value: WoodsPreference; label: string }[] = [
  { value: 'INDIFFERENT', label: 'Don’t care' },
  { value: 'PREFER', label: 'Prefer riding through woods' },
  { value: 'STRONGLY_PREFER', label: 'Strongly prefer woods' },
]

export const SURFACES: readonly { key: Surface; label: string; color: string }[] = [
  { key: 'UNPAVED', label: 'Unpaved', color: '#8A6A3F' },
  { key: 'PAVED', label: 'Paved', color: '#5F6B73' },
  { key: 'UNKNOWN', label: 'Surface unknown', color: '#C9CFC6' },
]

export const DEFAULT_PREFERENCES: RoutingPreferences = {
  levels: {
    TRAIL: 'FAVOURITE',
    ROUGH_TRACK: 'PREFER',
    GRAVEL_TRACK: 'PREFER',
    FOOTWAY: 'NEUTRAL',
    CYCLEWAY: 'NEUTRAL',
    QUIET_ROAD: 'AVOID',
    MAIN_ROAD: 'AVOID',
    BUSY_ROAD: 'AVOID',
  },
  maxDifficulty: 'S3',
  woods: 'PREFER',
  avoidClimbs: false,
}

/** Luxembourg City – the initial view before the rider has planned anything. */
export const DEFAULT_CENTER: [number, number] = [6.13, 49.61]
