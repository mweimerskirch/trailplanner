import type {
  Activity,
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
  /** Label and hint for activities where the mountain-bike wording does not fit. */
  for?: Partial<Record<Activity, { label: string; hint: string }>>
  /** Activities whose routing ignores this category (no slider is shown). */
  hiddenFor?: readonly Activity[]
}

export interface ActivityInfo {
  value: Activity
  label: string
  /** Default average speed on the flat in km/h, for the time estimate. */
  speed: number
  /** Name of a new route. */
  routeName: string
}

const MOUNTAIN_BIKE: ActivityInfo = { value: 'MTB', label: 'Mountain bike', speed: 16, routeName: 'MTB tour' }

export const ACTIVITIES: readonly ActivityInfo[] = [
  MOUNTAIN_BIKE,
  { value: 'ROAD', label: 'Road bike', speed: 25, routeName: 'Road ride' },
  { value: 'HORSE', label: 'Horseback', speed: 7, routeName: 'Horse ride' },
]

export function activityInfo(activity: Activity): ActivityInfo {
  return ACTIVITIES.find((a) => a.value === activity) ?? MOUNTAIN_BIKE
}

export const CATEGORIES: readonly CategoryInfo[] = [
  {
    key: 'TRAIL',
    label: 'Singletrails & paths',
    hint: 'path, bridleway',
    color: '#7B3FA0',
    for: {
      ROAD: {
        label: 'Paved paths',
        hint: 'path, bridleway (shared foot and cycle paths count as cycleways)',
      },
      HORSE: { label: 'Bridleways & paths', hint: 'bridleway, path' },
    },
  },
  {
    key: 'ROUGH_TRACK',
    label: 'Rough forest tracks',
    hint: 'track, grade 3–5 or unknown',
    color: '#6B4F3A',
    hiddenFor: ['ROAD'],
  },
  {
    key: 'GRAVEL_TRACK',
    label: 'Gravel forest roads',
    hint: 'track, grade 1–2',
    color: '#B8923F',
    for: { ROAD: { label: 'Farm & forest roads', hint: 'track, grade 1–2, where paved or compacted' } },
  },
  {
    key: 'FOOTWAY',
    label: 'Footways',
    hint: 'footway, pedestrian, steps',
    color: '#3F8A75',
    for: { HORSE: { label: 'Footways', hint: 'footway, pedestrian; only where horses are allowed' } },
  },
  {
    key: 'CYCLEWAY',
    label: 'Cycleways',
    hint: 'cycleway',
    color: '#2E6FB0',
    for: { HORSE: { label: 'Cycleways', hint: 'cycleway; only where horses are allowed' } },
  },
  { key: 'QUIET_ROAD', label: 'Quiet roads', hint: 'residential, service, unclassified', color: '#8C968F' },
  { key: 'MAIN_ROAD', label: 'Main roads', hint: 'secondary, tertiary', color: '#D9822B' },
  { key: 'BUSY_ROAD', label: 'Busy roads', hint: 'primary, trunk', color: '#B3261E' },
]

/** A category's label and hint for an activity. */
export function categoryText(category: CategoryInfo, activity: Activity): { label: string; hint: string } {
  return category.for?.[activity] ?? category
}

/** The categories the activity's routing uses. */
export function categoriesFor(activity: Activity): readonly CategoryInfo[] {
  return CATEGORIES.filter((c) => !c.hiddenFor?.includes(activity))
}

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
  maxDifficulty: 'S2', // matches the built-in "Flowy trails" preset, so new routes start on it
  woods: 'PREFER',
  avoidClimbs: false,
  activity: 'MTB',
}

/** Luxembourg City – the initial view before the rider has planned anything. */
export const DEFAULT_CENTER: [number, number] = [6.13, 49.61]
