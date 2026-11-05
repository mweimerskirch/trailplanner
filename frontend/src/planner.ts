import { computed, ref, shallowRef, toRaw, watch } from 'vue'
import { api, ApiError } from './api'
import { DEFAULT_PREFERENCES } from './constants'
import { deriveWaypoints } from './convert'
import type {
  GeoPoint,
  LegBreakdown,
  RouteDetails,
  RouteDraft,
  RouteLeg,
  RoutingPreferences,
  Surface,
  TrackPoint,
  WayCategory,
} from './types'

/**
 * The planner's state, shared by all components. Waypoints are the source of truth; legs are
 * recalculated from them (each leg between two consecutive waypoints, cached by position and
 * preferences, so moving one point only recalculates its two legs).
 *
 * Imported routes that have not been converted yet are shown as a fixed track (`fixedTrack`)
 * instead: no waypoints, nothing to recalculate, and editing is blocked until the rider converts
 * the track with `editImportedRoute()`.
 */

/** An imported track shown as-is. Distance and climbing come from the import. */
export interface FixedTrack {
  points: TrackPoint[]
  distance: number
  ascent: number
}

export type LegState =
  { status: 'loading' } | { status: 'ok'; leg: RouteLeg } | { status: 'error'; message: string }

export interface ProfilePoint {
  distance: number
  ele: number | null
  lng: number
  lat: number
}

export interface StatusMessage {
  text: string
  kind: 'info' | 'error'
}

const STORAGE_KEY = 'trailplan.planner'
const MAX_HISTORY = 80
const MAX_PARALLEL_LEGS = 3
const MAX_CACHED_LEGS = 500

interface StoredState {
  waypoints?: GeoPoint[]
  preferences?: Partial<RoutingPreferences>
  routeName?: string
  routeId?: number | null
  speed?: number
  fixedTrack?: FixedTrack | null
}

function loadStored(): StoredState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as StoredState) : {}
  } catch {
    return {}
  }
}

function mergePreferences(stored: Partial<RoutingPreferences> | undefined): RoutingPreferences {
  return {
    ...DEFAULT_PREFERENCES,
    ...stored,
    levels: { ...DEFAULT_PREFERENCES.levels, ...stored?.levels },
  }
}

const stored = loadStored()

export const waypoints = ref<GeoPoint[]>(stored.waypoints ?? [])
export const preferences = ref<RoutingPreferences>(mergePreferences(stored.preferences))
export const routeName = ref(stored.routeName ?? 'MTB tour')
/** Id of the saved route being edited, or null for a new, unsaved route. */
export const routeId = ref<number | null>(stored.routeId ?? null)
/** Average speed on the flat in km/h, for the time estimate only. */
export const speed = ref(stored.speed ?? 16)
/** Set while an imported, not yet converted route is open (view mode). */
export const fixedTrack = shallowRef<FixedTrack | null>(stored.fixedTrack ?? null)
export const viewOnly = computed(() => fixedTrack.value !== null)

export const legs = shallowRef<LegState[]>([])
export const status = ref<StatusMessage | null>(null)
export const hoverIndex = ref<number | null>(null)
/** Bumped when the map should zoom to the whole route. */
export const fitRequest = ref(0)
export const flyTarget = ref<GeoPoint | null>(null)
/** Bumped when the saved-route list must be reloaded. */
export const libraryVersion = ref(0)

/**
 * One undo step. `route` is only recorded when the step switches routes (open, new), so undoing
 * it also restores which saved route is being edited; otherwise "Save changes" after an undo
 * would overwrite the opened route with the previous route's waypoints.
 */
interface HistoryEntry {
  waypoints: GeoPoint[]
  route?: {
    id: number | null
    name: string
    preferences: RoutingPreferences
    fixedTrack: FixedTrack | null
  }
}

const history: HistoryEntry[] = []
export const canUndo = ref(false)

watch(
  [waypoints, preferences, routeName, routeId, speed, fixedTrack],
  () => {
    const state: StoredState = {
      waypoints: waypoints.value,
      preferences: preferences.value,
      routeName: routeName.value,
      routeId: routeId.value,
      speed: speed.value,
      fixedTrack: fixedTrack.value,
    }
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
    } catch {
      // Storage full or disabled: the planner still works, it just won't survive a reload.
    }
  },
  { deep: true },
)

export function setStatus(text: string | null, kind: StatusMessage['kind'] = 'info', clearAfterMs = 0) {
  status.value = text ? { text, kind } : null
  if (text && clearAfterMs > 0) {
    setTimeout(() => {
      if (status.value?.text === text) status.value = null
    }, clearAfterMs)
  }
}

// ---------- editing ----------

export function remember(includeRoute = false) {
  history.push({
    waypoints: waypoints.value.map((p) => ({ ...p })),
    ...(includeRoute
      ? {
          route: {
            id: routeId.value,
            name: routeName.value,
            preferences: structuredClone(toRaw(preferences.value)),
            fixedTrack: fixedTrack.value,
          },
        }
      : {}),
  })
  if (history.length > MAX_HISTORY) history.shift()
  canUndo.value = true
}

function edit(change: (points: GeoPoint[]) => void, recordHistory = true) {
  if (fixedTrack.value) {
    setStatus('This route was imported. Press “Edit route” to change it.', 'info', 4000)
    return
  }
  if (recordHistory) remember()
  const next = waypoints.value.map((p) => ({ ...p }))
  change(next)
  waypoints.value = next
}

export const addWaypoint = (p: GeoPoint) => {
  edit((pts) => pts.push(p))
}
export const insertWaypoint = (index: number, p: GeoPoint) => {
  edit((pts) => pts.splice(index, 0, p))
}
export const removeWaypoint = (index: number) => {
  edit((pts) => pts.splice(index, 1))
}
/** For marker drags: the history entry is recorded when the drag starts. */
export const moveWaypoint = (index: number, p: GeoPoint) => {
  edit((pts) => pts.splice(index, 1, p), false)
}
export const reverseRoute = () => {
  if (waypoints.value.length > 1) edit((pts) => pts.reverse())
}
export const backToStart = () => {
  const first = waypoints.value[0]
  if (first && waypoints.value.length > 1) edit((pts) => pts.push({ ...first }))
}
export const clearRoute = () => {
  if (waypoints.value.length > 0) edit((pts) => pts.splice(0))
}

export function undo() {
  const previous = history.pop()
  if (previous) {
    waypoints.value = previous.waypoints
    if (previous.route) {
      routeId.value = previous.route.id
      routeName.value = previous.route.name
      preferences.value = previous.route.preferences
      fixedTrack.value = previous.route.fixedTrack
    }
  }
  canUndo.value = history.length > 0
}

export function newRoute() {
  if (waypoints.value.length > 0 || routeId.value !== null || fixedTrack.value) remember(true)
  fixedTrack.value = null
  edit((pts) => pts.splice(0), false)
  routeId.value = null
  routeName.value = 'MTB tour'
}

export function openRoute(route: RouteDetails) {
  remember(true)
  fixedTrack.value = route.editable
    ? null
    : { points: route.track, distance: route.distance, ascent: route.ascent }
  waypoints.value = route.waypoints.map((p) => ({ ...p }))
  preferences.value = mergePreferences(route.preferences)
  routeName.value = route.name
  routeId.value = route.id
  fitRequest.value++
}

/**
 * Converts the open imported route into waypoints. The route is then recalculated with the
 * rider's preferences, so it may differ slightly from the original; saving makes it editable for
 * good. Undo goes back to the original track.
 */
export function editImportedRoute() {
  const track = fixedTrack.value
  if (!track) return
  remember(true)
  const points = deriveWaypoints(track.points)
  fixedTrack.value = null
  waypoints.value = points
  setStatus(
    `Converted to ${points.length} waypoints. The route now follows your preferences and may differ slightly from the original; save to keep it.`,
    'info',
    8000,
  )
}

export function draft(): RouteDraft {
  return {
    name: routeName.value.trim() || 'Route',
    waypoints: waypoints.value,
    preferences: preferences.value,
  }
}

// ---------- routing ----------

const legCache = new Map<string, RouteLeg>()
let generation = 0
let controller: AbortController | null = null

const pointKey = (p: GeoPoint) => `${p.lat.toFixed(6)},${p.lng.toFixed(6)}`

async function recalculate() {
  const run = ++generation
  controller?.abort()
  controller = new AbortController()
  const signal = controller.signal

  const pts = waypoints.value
  const prefs = preferences.value
  const prefsKey = JSON.stringify(prefs)
  const keys = pts.slice(1).map((to, i) => `${prefsKey}|${pointKey(pts[i] ?? to)}|${pointKey(to)}`)
  const next: LegState[] = keys.map((key) => {
    const cached = legCache.get(key)
    return cached ? { status: 'ok', leg: cached } : { status: 'loading' }
  })
  legs.value = next

  const todo = keys.flatMap((_key, i) => (next[i]?.status === 'loading' ? [i] : []))
  if (todo.length === 0) return

  setStatus('Calculating route…')
  const worker = async () => {
    for (let i = todo.shift(); i !== undefined; i = todo.shift()) {
      const from = pts[i]
      const to = pts[i + 1]
      const key = keys[i]
      if (!from || !to || key === undefined) continue
      let state: LegState
      try {
        const leg = await api.routeLeg(from, to, prefs, signal)
        legCache.set(key, leg)
        // Each leg holds its full track; drop the oldest entries so long sessions stay small.
        for (const oldest of legCache.keys()) {
          if (legCache.size <= MAX_CACHED_LEGS) break
          legCache.delete(oldest)
        }
        state = { status: 'ok', leg }
      } catch (error) {
        if (signal.aborted) return
        state = { status: 'error', message: error instanceof ApiError ? error.message : 'Routing failed.' }
      }
      if (run !== generation) return
      const copy = [...legs.value]
      copy[i] = state
      legs.value = copy
    }
  }
  await Promise.all(Array.from({ length: Math.min(MAX_PARALLEL_LEGS, todo.length) }, worker))
  if (run !== generation) return

  const failed = legs.value.find((l) => l.status === 'error')
  setStatus(failed?.status === 'error' ? failed.message : null, 'error')
}

let preferenceTimer: ReturnType<typeof setTimeout> | undefined
watch(waypoints, () => void recalculate(), { immediate: true })
watch(
  preferences,
  () => {
    clearTimeout(preferenceTimer)
    preferenceTimer = setTimeout(() => void recalculate(), 400)
  },
  { deep: true },
)

// ---------- derived route data ----------

/** What the map and the statistics show: the fixed track in view mode, else the calculated legs. */
export const displayLegs = computed<LegState[]>(() => {
  const track = fixedTrack.value
  if (!track) return legs.value
  const leg: RouteLeg = {
    points: track.points,
    distance: track.distance,
    ascent: track.ascent,
    breakdown: { wayTypes: {}, surfaces: {}, other: 0 },
  }
  return [{ status: 'ok', leg }]
})

export const routeComplete = computed(
  () => displayLegs.value.length > 0 && displayLegs.value.every((l) => l.status === 'ok'),
)

const okLegs = computed(() => displayLegs.value.flatMap((l) => (l.status === 'ok' ? [l.leg] : [])))

export const totals = computed(() => {
  const distance = okLegs.value.reduce((sum, leg) => sum + leg.distance, 0)
  const ascent = okLegs.value.reduce((sum, leg) => sum + leg.ascent, 0)
  const hours = distance / 1000 / Math.max(5, speed.value) + ascent / 600
  return { distance, ascent, hours }
})

export const breakdown = computed<LegBreakdown>(() => {
  const wayTypes: Partial<Record<WayCategory, number>> = {}
  const surfaces: Partial<Record<Surface, number>> = {}
  let other = 0
  for (const leg of okLegs.value) {
    for (const [key, metres] of Object.entries(leg.breakdown.wayTypes) as [WayCategory, number][]) {
      wayTypes[key] = (wayTypes[key] ?? 0) + metres
    }
    for (const [key, metres] of Object.entries(leg.breakdown.surfaces) as [Surface, number][]) {
      surfaces[key] = (surfaces[key] ?? 0) + metres
    }
    other += leg.breakdown.other
  }
  return { wayTypes, surfaces, other }
})

function haversine(a: ProfilePoint, b: ProfilePoint): number {
  const rad = Math.PI / 180
  const dLat = (b.lat - a.lat) * rad
  const dLng = (b.lng - a.lng) * rad
  const s = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * rad) * Math.cos(b.lat * rad) * Math.sin(dLng / 2) ** 2
  return 2 * 6_371_000 * Math.asin(Math.sqrt(s))
}

/** The calculated track as one list with cumulative distance, for the elevation chart. */
export const profile = computed<ProfilePoint[]>(() => {
  const result: ProfilePoint[] = []
  for (const leg of okLegs.value) {
    leg.points.forEach(([lng, lat, ele], index) => {
      if (index === 0 && result.length > 0) return
      const point: ProfilePoint = { lng, lat, ele, distance: 0 }
      const previous = result.at(-1)
      point.distance = previous ? previous.distance + haversine(previous, point) : 0
      result.push(point)
    })
  }
  return result
})
