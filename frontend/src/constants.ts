import type {
  Activity,
  PreferenceLevel,
  RoutingPreferences,
  RouteWarning,
  Surface,
  TrailDifficulty,
  WayCategory,
  WoodsPreference,
} from './types'
import { allTranslations, i18n, t } from './i18n'

/*
 * Labels are getters that read the current language, so lists defined here follow a language
 * change wherever they are rendered.
 */

export interface CategoryInfo {
  key: WayCategory
  readonly label: string
  readonly hint: string
  color: string
  /** Activities with their own wording for this category (message key `<KEY>_<ACTIVITY>`). */
  ownWording?: readonly Activity[]
  /** Activities whose routing ignores this category (no slider is shown). */
  hiddenFor?: readonly Activity[]
}

export interface ActivityInfo {
  value: Activity
  readonly label: string
  /** Default average speed on the flat in km/h, for the time estimate. */
  speed: number
  /** Name of a new route. */
  readonly routeName: string
}

function activity(value: Activity, speed: number): ActivityInfo {
  return {
    value,
    speed,
    get label() {
      return t(`routing.activity.${value}.label`)
    },
    get routeName() {
      return t(`routing.activity.${value}.routeName`)
    },
  }
}

const MOUNTAIN_BIKE = activity('MTB', 16)

export const ACTIVITIES: readonly ActivityInfo[] = [MOUNTAIN_BIKE, activity('ROAD', 25), activity('HORSE', 7)]

export function activityInfo(activity: Activity): ActivityInfo {
  return ACTIVITIES.find((a) => a.value === activity) ?? MOUNTAIN_BIKE
}

/** The default name of a new route for any activity, in any language. */
export function isDefaultRouteName(name: string): boolean {
  return ACTIVITIES.some((a) => allTranslations(`routing.activity.${a.value}.routeName`).includes(name))
}

type CategoryKey =
  WayCategory | 'TRAIL_ROAD' | 'TRAIL_HORSE' | 'GRAVEL_TRACK_ROAD' | 'FOOTWAY_HORSE' | 'CYCLEWAY_HORSE'

function category(
  key: WayCategory,
  color: string,
  options: { ownWording?: readonly Activity[]; hiddenFor?: readonly Activity[] } = {},
): CategoryInfo {
  return {
    key,
    color,
    ...options,
    get label() {
      return t(`routing.category.${key}.label`)
    },
    get hint() {
      return t(`routing.category.${key}.hint`)
    },
  }
}

export const CATEGORIES: readonly CategoryInfo[] = [
  category('TRAIL', '#7B3FA0', { ownWording: ['ROAD', 'HORSE'] }),
  category('ROUGH_TRACK', '#6B4F3A', { hiddenFor: ['ROAD'] }),
  category('GRAVEL_TRACK', '#B8923F', { ownWording: ['ROAD'] }),
  category('FOOTWAY', '#3F8A75', { ownWording: ['HORSE'] }),
  category('CYCLEWAY', '#2E6FB0', { ownWording: ['HORSE'] }),
  category('QUIET_ROAD', '#8C968F'),
  category('MAIN_ROAD', '#D9822B'),
  category('BUSY_ROAD', '#B3261E'),
]

/** A category's label and hint for an activity. */
export function categoryText(category: CategoryInfo, activity: Activity): { label: string; hint: string } {
  if (!category.ownWording?.includes(activity)) return { label: category.label, hint: category.hint }
  const key = `${category.key}_${activity}` as CategoryKey
  return { label: t(`routing.category.${key}.label`), hint: t(`routing.category.${key}.hint`) }
}

/** The categories the activity's routing uses. */
export function categoriesFor(activity: Activity): readonly CategoryInfo[] {
  return CATEGORIES.filter((c) => !c.hiddenFor?.includes(activity))
}

export const OTHER_WAYS = {
  get label() {
    return t('routing.category.OTHER.label')
  },
  color: '#BFC6BD',
}

export const LEVELS: readonly PreferenceLevel[] = ['NEVER', 'AVOID', 'NEUTRAL', 'PREFER', 'FAVOURITE']

export const levelLabel = (level: PreferenceLevel) => t(`routing.level.${level}`)

function labelled<T extends string>(value: T, key: () => string): { value: T; readonly label: string } {
  return {
    value,
    get label() {
      return key()
    },
  }
}

const DIFFICULTY_VALUES: readonly TrailDifficulty[] = ['S0', 'S1', 'S2', 'S3', 'S4', 'S5', 'ANY']

export const DIFFICULTIES = DIFFICULTY_VALUES.map((d) => labelled(d, () => t(`routing.difficulty.${d}`)))

const WOODS_VALUES: readonly WoodsPreference[] = ['INDIFFERENT', 'PREFER', 'STRONGLY_PREFER']

export const WOODS_OPTIONS = WOODS_VALUES.map((w) => labelled(w, () => t(`routing.woods.${w}`)))

const SURFACE_COLORS: Record<Surface, string> = { UNPAVED: '#8A6A3F', PAVED: '#5F6B73', UNKNOWN: '#C9CFC6' }

export const SURFACES: readonly { key: Surface; readonly label: string; color: string }[] = (
  Object.keys(SURFACE_COLORS) as Surface[]
).map((key) => ({
  key,
  color: SURFACE_COLORS[key],
  get label() {
    return t(`routing.surface.${key}`)
  },
}))

/** A warning's reason in the chosen language; the server's English text for unknown codes. */
export function warningText(warning: RouteWarning): string {
  const key = `routing.warning.${warning.code ?? ''}`
  return warning.code && i18n.global.te(key) ? t(key) : warning.reason
}

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

/** The whole world: the view until the server's start area (TRAILPLANNER_AREA) has arrived. */
export const WORLD_VIEW = { center: [0, 20] as [number, number], zoom: 1 }
