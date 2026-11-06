import { computed, nextTick, ref, shallowRef, toRaw, watch } from 'vue'
import { api, ApiError } from './api'
import { DEFAULT_PREFERENCES } from './constants'
import type {
  GeoPoint,
  LegBreakdown,
  RouteDetails,
  RouteDraft,
  RouteLeg,
  RoutingPreferences,
  Surface,
  RouteWarning,
  TrackPoint,
  Waypoint,
  WayCategory,
} from './types'
import { createHistory } from './history'
import { haversine, type ProfilePoint } from './profile'
import { inserted, legType, removed, reversed, withLegType } from './waypoints'

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

export type { ProfilePoint }

export interface StatusMessage {
  text: string
  kind: 'info' | 'error'
}

/** Planner state is stored per rider: `trailplan.planner.<riderId>`. */
const STORAGE_PREFIX = 'trailplan.planner.'
/** Where the state lived before there were riders; it moves to the first rider who opens it. */
const LEGACY_STORAGE_KEY = 'trailplan.planner'
const MAX_HISTORY = 80
const MAX_PARALLEL_LEGS = 3
const MAX_CACHED_LEGS = 500

interface StoredState {
  waypoints?: Waypoint[]
  preferences?: Partial<RoutingPreferences>
  routeName?: string
  routeId?: number | null
  routeUpdatedAt?: string | null
  hasOriginal?: boolean
  favorite?: boolean
  speed?: number
  /** An imported route is open in view mode; its track is fetched again on load. */
  viewOnly?: boolean
}

function loadStored(key: string): StoredState {
  try {
    let raw = localStorage.getItem(key)
    const legacy = localStorage.getItem(LEGACY_STORAGE_KEY)
    if (raw === null && legacy !== null) {
      raw = legacy
      localStorage.removeItem(LEGACY_STORAGE_KEY)
    }
    return raw ? (JSON.parse(raw) as StoredState) : {}
  } catch {
    return {}
  }
}

export function mergePreferences(stored: Partial<RoutingPreferences> | undefined): RoutingPreferences {
  return {
    ...DEFAULT_PREFERENCES,
    ...stored,
    levels: { ...DEFAULT_PREFERENCES.levels, ...stored?.levels },
  }
}

/** Storage key of the active rider; null until a rider is chosen (nothing is saved before). */
let storageKey: string | null = null

export const waypoints = ref<Waypoint[]>([])
export const preferences = ref<RoutingPreferences>(mergePreferences(undefined))
export const routeName = ref('MTB tour')
/** Id of the saved route being edited, or null for a new, unsaved route. */
export const routeId = ref<number | null>(null)
/** The open route's updatedAt as last loaded or saved; sent with saves to detect conflicts. */
export const routeUpdatedAt = ref<string | null>(null)
/** The open route is an import whose original track is kept (restorable, downloadable). */
export const hasOriginal = ref(false)
/** The open saved route is starred. */
export const favorite = ref(false)
/** Average speed on the flat in km/h, for the time estimate only. */
export const speed = ref(16)
/** Set while an imported, not yet converted route is open (view mode). */
export const fixedTrack = shallowRef<FixedTrack | null>(null)
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
 * One undo step: the whole planner state, so undo never mixes one route's waypoints with another
 * route's identity, and preference changes are undoable too.
 */
interface Snapshot {
  waypoints: Waypoint[]
  routeId: number | null
  routeName: string
  routeUpdatedAt: string | null
  hasOriginal: boolean
  favorite: boolean
  preferences: RoutingPreferences
  fixedTrack: FixedTrack | null
}

const history = createHistory<Snapshot>(MAX_HISTORY)
export const canUndo = ref(false)
/** True while the planner applies a snapshot or loads state; such changes are not undo steps. */
let restoring = false

watch(
  [waypoints, preferences, routeName, routeId, routeUpdatedAt, hasOriginal, favorite, speed, fixedTrack],
  () => {
    if (!storageKey) return
    const state: StoredState = {
      waypoints: waypoints.value,
      preferences: preferences.value,
      routeName: routeName.value,
      routeId: routeId.value,
      routeUpdatedAt: routeUpdatedAt.value,
      hasOriginal: hasOriginal.value,
      favorite: favorite.value,
      speed: speed.value,
      viewOnly: fixedTrack.value !== null,
    }
    try {
      localStorage.setItem(storageKey, JSON.stringify(state))
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

/**
 * Switches the planner to a rider's own state (waypoints, preferences, open route), as it was
 * when they last used this browser. Undo history does not carry over between riders.
 */
export function activateRider(riderId: number) {
  const key = STORAGE_PREFIX + String(riderId)
  if (storageKey === key) return
  storageKey = key
  const stored = loadStored(key)
  applyQuietly(() => {
    fixedTrack.value = null
    waypoints.value = stored.waypoints ?? []
    preferences.value = mergePreferences(stored.preferences)
    routeName.value = stored.routeName ?? 'MTB tour'
    routeId.value = stored.routeId ?? null
    routeUpdatedAt.value = stored.routeUpdatedAt ?? null
    hasOriginal.value = stored.hasOriginal ?? false
    favorite.value = stored.favorite ?? false
    speed.value = stored.speed ?? 16
  })
  // An imported route was open in view mode: fetch its track again (it is not kept in storage).
  if (stored.viewOnly && stored.routeId != null) void reloadViewOnly(stored.routeId)
  history.clear()
  canUndo.value = false
  status.value = null
  hoverIndex.value = null
  libraryVersion.value++
  fitRequest.value++
}

// ---------- editing ----------

function snapshot(): Snapshot {
  return {
    waypoints: waypoints.value.map((p) => ({ ...p })),
    routeId: routeId.value,
    routeName: routeName.value,
    routeUpdatedAt: routeUpdatedAt.value,
    hasOriginal: hasOriginal.value,
    favorite: favorite.value,
    preferences: structuredClone(toRaw(preferences.value)),
    fixedTrack: fixedTrack.value,
  }
}

/** Records the current state as an undo step, before a change. */
export function remember() {
  history.push(snapshot())
  canUndo.value = true
}

/** Applies state without recording it as an undo step (undo, opening a route, loading). */
function applyQuietly(change: () => void) {
  restoring = true
  change()
  void nextTick(() => {
    restoring = false
    preferencesBefore = structuredClone(toRaw(preferences.value))
  })
}

/** Applies a change to the waypoint list (as a new list, so watchers see it). */
function edit(change: (points: Waypoint[]) => Waypoint[], recordHistory = true) {
  if (fixedTrack.value) {
    setStatus('This route was imported. Press “Edit route” to change it.', 'info', 4000)
    return
  }
  if (recordHistory) remember()
  waypoints.value = change(waypoints.value)
}

export const addWaypoint = (p: GeoPoint) => {
  edit((pts) => inserted(pts, pts.length, p))
}
/** Adds a via point into the leg before `index`; it keeps that leg's type (e.g. forced). */
export const insertWaypoint = (index: number, p: GeoPoint) => {
  edit((pts) => inserted(pts, index, p))
}
export const removeWaypoint = (index: number) => {
  edit((pts) => removed(pts, index))
}
/** For marker drags: the history entry is recorded when the drag starts. */
export const moveWaypoint = (index: number, p: GeoPoint) => {
  edit((pts) => pts.map((w, i) => (i === index ? { ...w, lat: p.lat, lng: p.lng } : w)), false)
}
/** Sets how the leg from waypoint `index` to the next one is calculated. */
export const setLegType = (index: number, type: Waypoint['leg'] & string) => {
  edit((pts) => withLegType(pts, index, type))
}
export const reverseRoute = () => {
  if (waypoints.value.length > 1) edit(reversed)
}
export const backToStart = () => {
  const first = waypoints.value[0]
  if (first && waypoints.value.length > 1)
    edit((pts) => inserted(pts, pts.length, { lat: first.lat, lng: first.lng }))
}
export const clearRoute = () => {
  if (waypoints.value.length > 0) edit(() => [])
}

export function undo() {
  const previous = history.pop()
  if (previous) {
    applyQuietly(() => {
      waypoints.value = previous.waypoints
      routeId.value = previous.routeId
      routeName.value = previous.routeName
      routeUpdatedAt.value = previous.routeUpdatedAt
      hasOriginal.value = previous.hasOriginal
      favorite.value = previous.favorite
      preferences.value = previous.preferences
      fixedTrack.value = previous.fixedTrack
    })
  }
  canUndo.value = history.size > 0
}

export function newRoute() {
  if (waypoints.value.length > 0 || routeId.value !== null || fixedTrack.value) remember()
  fixedTrack.value = null
  edit(() => [], false)
  routeId.value = null
  routeUpdatedAt.value = null
  hasOriginal.value = false
  favorite.value = false
  routeName.value = 'MTB tour'
}

export function openRoute(route: RouteDetails) {
  remember()
  applyQuietly(() => {
    fixedTrack.value = route.editable
      ? null
      : { points: route.track, distance: route.distance, ascent: route.ascent }
    waypoints.value = route.waypoints.map((p) => ({ ...p }))
    preferences.value = mergePreferences(route.preferences)
    routeName.value = route.name
    routeId.value = route.id
    routeUpdatedAt.value = route.updatedAt
    hasOriginal.value = route.hasOriginal
    favorite.value = route.favorite
  })
  fitRequest.value++
}

async function reloadViewOnly(id: number) {
  try {
    const route = await api.getRoute(id)
    if (!route.editable && routeId.value === id) {
      applyQuietly(() => {
        fixedTrack.value = { points: route.track, distance: route.distance, ascent: route.ascent }
      })
    }
  } catch {
    // Deleted meanwhile: the planner stays empty for this route.
  }
}

/** True while the server converts an imported route into waypoints. */
export const converting = ref(false)

/**
 * Converts the open imported route into waypoints. The server places as few waypoints as possible
 * and checks every leg against the router; where the original uses a way that is closed in the map
 * data the leg is forced (with a warning), where no way exists it is a straight line. Undo goes
 * back to the original track; saving keeps the converted route.
 */
export async function editImportedRoute() {
  const track = fixedTrack.value
  const id = routeId.value
  if (!track || id === null || converting.value) return
  converting.value = true
  setStatus('Matching the original track with the router…')
  try {
    const result = await api.convertRoute(id, preferences.value)
    remember()
    fixedTrack.value = null
    waypoints.value = result.waypoints
    const notes = [
      result.forcedLegs > 0
        ? `${String(result.forcedLegs)} leg${result.forcedLegs === 1 ? ' uses' : 's use'} closed or restricted ways (orange, see the warnings)`
        : '',
      result.straightLegs > 0
        ? `${String(result.straightLegs)} straight line${result.straightLegs === 1 ? '' : 's'} where no way exists (dotted)`
        : '',
    ].filter(Boolean)
    setStatus(
      `Converted to ${String(result.waypoints.length)} waypoints${notes.length ? `; ${notes.join(', ')}` : ''}. Save to keep it.`,
      'info',
      10000,
    )
  } catch (error) {
    setStatus(error instanceof Error ? error.message : 'Converting the route failed.', 'error')
  } finally {
    converting.value = false
  }
}

/** Remembers a saved version of the open route (after saving or renaming). */
export function savedAs(route: RouteDetails) {
  routeId.value = route.id
  routeName.value = route.name
  routeUpdatedAt.value = route.updatedAt
  hasOriginal.value = route.hasOriginal
  favorite.value = route.favorite
}

/**
 * Puts the open imported route's original track back. Undo returns to the edited version, which
 * can then be saved as a new route.
 */
export async function revertToOriginal() {
  const id = routeId.value
  if (id === null) return
  try {
    openRoute(await api.revertRoute(id))
    setStatus('Back to the original track.', 'info', 3000)
  } catch (error) {
    setStatus(error instanceof Error ? error.message : 'Could not restore the original.', 'error')
  }
}

/** Stars or unstars the open saved route. */
export async function toggleFavorite() {
  const id = routeId.value
  if (id === null) return
  try {
    const route = await api.setFavorite(id, !favorite.value)
    favorite.value = route.favorite
    libraryVersion.value++
  } catch (error) {
    setStatus(error instanceof Error ? error.message : 'Could not change the favorite.', 'error')
  }
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
/** The status text routing set last, so routing only ever clears its own messages. */
let routingStatus: string | null = null
let controller: AbortController | null = null

const pointKey = (p: GeoPoint) => `${p.lat.toFixed(6)},${p.lng.toFixed(6)}`

/** A straight leg needs no router; way type and elevation are unknown. */
function straightLeg(from: GeoPoint, to: GeoPoint): RouteLeg {
  const distance = haversine(from, to)
  return {
    points: [
      [from.lng, from.lat, null],
      [to.lng, to.lat, null],
    ],
    distance,
    ascent: 0,
    breakdown: { wayTypes: {}, surfaces: { UNKNOWN: distance }, other: distance },
    warnings: [],
  }
}

async function recalculate() {
  const run = ++generation
  controller?.abort()
  controller = new AbortController()
  const signal = controller.signal

  const pts = waypoints.value
  const prefs = preferences.value
  const prefsKey = JSON.stringify(prefs)
  const keys = pts
    .slice(1)
    .map((to, i) => `${prefsKey}|${legType(pts[i])}|${pointKey(pts[i] ?? to)}|${pointKey(to)}`)
  const next: LegState[] = keys.map((key, i) => {
    const from = pts[i]
    const to = pts[i + 1]
    if (from && to && legType(from) === 'STRAIGHT') return { status: 'ok', leg: straightLeg(from, to) }
    const cached = legCache.get(key)
    return cached ? { status: 'ok', leg: cached } : { status: 'loading' }
  })
  legs.value = next

  const todo = keys.flatMap((_key, i) => (next[i]?.status === 'loading' ? [i] : []))
  if (todo.length === 0) {
    // Nothing to calculate (e.g. undo to cached legs): drop a stale "Calculating…" or leg error,
    // but not messages from elsewhere (e.g. "route not found").
    if (routingStatus !== null && status.value?.text === routingStatus) setStatus(null)
    routingStatus = null
    return
  }

  routingStatus = 'Calculating route…'
  setStatus(routingStatus)
  const worker = async () => {
    for (let i = todo.shift(); i !== undefined; i = todo.shift()) {
      const from = pts[i]
      const to = pts[i + 1]
      const key = keys[i]
      if (!from || !to || key === undefined) continue
      let state: LegState
      try {
        const leg = await api.routeLeg(from, to, prefs, legType(from), signal)
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
  routingStatus = failed?.status === 'error' ? failed.message : null
  setStatus(routingStatus, 'error')
}

let preferenceTimer: ReturnType<typeof setTimeout> | undefined
watch(waypoints, () => void recalculate(), { immediate: true })
/** Preferences before the current burst of changes (e.g. while a slider is being dragged). */
let preferencesBefore = structuredClone(toRaw(preferences.value))
let preferenceBurst = false
watch(
  preferences,
  () => {
    // A burst of preference changes is one undo step, holding the preferences from before it.
    if (!restoring && !preferenceBurst) {
      history.push({ ...snapshot(), preferences: preferencesBefore })
      canUndo.value = true
      preferenceBurst = true
    }
    clearTimeout(preferenceTimer)
    preferenceTimer = setTimeout(() => {
      preferenceBurst = false
      preferencesBefore = structuredClone(toRaw(preferences.value))
      void recalculate()
    }, 400)
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
    warnings: [],
  }
  return [{ status: 'ok', leg }]
})

export const routeComplete = computed(
  () => displayLegs.value.length > 0 && displayLegs.value.every((l) => l.status === 'ok'),
)

/** Closed or restricted ways the route uses (only forced legs can). */
export const warnings = computed<RouteWarning[]>(() =>
  displayLegs.value.flatMap((l) => (l.status === 'ok' ? l.leg.warnings : [])),
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

/** The calculated track as one list with cumulative distance, for the elevation chart. */
export const profile = computed<ProfilePoint[]>(() => {
  const result: ProfilePoint[] = []
  for (const leg of okLegs.value) {
    leg.points.forEach(([lng, lat, ele], index) => {
      if (index === 0 && result.length > 0) {
        // Shared junction point: keep its elevation if the previous (straight) leg has none.
        const last = result.at(-1)
        if (last?.ele === null) last.ele = ele
        return
      }
      const point: ProfilePoint = { lng, lat, ele, distance: 0 }
      const previous = result.at(-1)
      point.distance = previous ? previous.distance + haversine(previous, point) : 0
      result.push(point)
    })
  }
  return result
})
