import type {
  ConversionResult,
  DeviceImportResult,
  DeviceInfo,
  GeoPoint,
  ImportResult,
  KomootStatus,
  KomootTour,
  LegType,
  Preset,
  RideDetails,
  RideImportResult,
  RideSummary,
  Rider,
  RiderSummary,
  RouteDetails,
  RouteDraft,
  RouteLeg,
  RouteSummary,
  RoutingPreferences,
  Settings,
  SyncPlan,
  SyncResult,
} from './types'
import type { Geometry } from 'geojson'
import type { Bounds } from './search'
import { currentLocale, t } from './i18n'

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    /** Machine-readable reason from the problem response, e.g. NO_USER. */
    readonly reason: string | null = null,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

interface ProblemDetail {
  title?: string
  detail?: string
  reason?: string
}

async function toError(response: Response): Promise<ApiError> {
  try {
    const problem = (await response.json()) as ProblemDetail
    const message =
      problem.detail ?? problem.title ?? t('common.error.requestFailed', { status: response.status })
    return new ApiError(message, response.status, problem.reason ?? null)
  } catch {
    return new ApiError(t('common.error.requestFailed', { status: response.status }), response.status)
  }
}

let noRiderHandler: (() => void) | null = null

/** Called when the server says no rider is chosen (e.g. the rider was deleted meanwhile). */
export function onNoRider(handler: () => void) {
  noRiderHandler = handler
}

let riderHeader: string | null = null

/**
 * The rider this page shows. Sent with every request, so that a tab still showing another rider
 * (switched in a different tab) is stopped instead of saving into the wrong library.
 */
export function setApiRider(id: number | null) {
  riderHeader = id === null ? null : String(id)
}

async function send(path: string, init: RequestInit = {}, signal?: AbortSignal): Promise<Response> {
  const headers = new Headers(init.headers)
  // The server only accepts changes with this header (protection against other websites).
  headers.set('X-TrailPlanner-Client', 'web')
  if (riderHeader !== null) headers.set('X-TrailPlanner-Rider', riderHeader)
  // The server localises its texts (device instructions, built-in presets of a new rider).
  headers.set('Accept-Language', currentLocale())
  // FormData bodies (file uploads) need the browser's multipart Content-Type with its boundary.
  if (!headers.has('Content-Type') && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  if (!headers.has('Accept')) headers.set('Accept', 'application/json')
  let response: Response
  try {
    response = await fetch(path, { ...init, ...(signal ? { signal } : {}), headers })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(t('common.error.unreachable'), 0)
  }
  if (!response.ok) {
    const error = await toError(response)
    if (error.reason === 'NO_USER') noRiderHandler?.()
    throw error
  }
  return response
}

async function json<T>(path: string, init?: RequestInit, signal?: AbortSignal): Promise<T> {
  const response = await send(path, init, signal)
  return (await response.json()) as T
}

export const api = {
  routeLeg(
    from: GeoPoint,
    to: GeoPoint,
    preferences: RoutingPreferences,
    type: LegType = 'ROUTED',
    signal?: AbortSignal,
  ) {
    return json<RouteLeg>(
      '/api/routing/leg',
      {
        method: 'POST',
        body: JSON.stringify({
          from: { lat: from.lat, lng: from.lng },
          to: { lat: to.lat, lng: to.lng },
          preferences,
          type,
        }),
      },
      signal,
    )
  },

  /** Waypoints for editing an imported route; nothing is saved. */
  convertRoute(id: number, preferences: RoutingPreferences) {
    return json<ConversionResult>(`/api/routes/${id}/conversion`, {
      method: 'POST',
      body: JSON.stringify({ preferences }),
    })
  },

  listRoutes() {
    return json<RouteSummary[]>('/api/routes')
  },

  getRoute(id: number) {
    return json<RouteDetails>(`/api/routes/${id}`)
  },

  createRoute(draft: RouteDraft) {
    return json<RouteDetails>('/api/routes', { method: 'POST', body: JSON.stringify(draft) })
  },

  updateRoute(id: number, draft: RouteDraft) {
    return json<RouteDetails>(`/api/routes/${id}`, { method: 'PUT', body: JSON.stringify(draft) })
  },

  renameRoute(id: number, name: string) {
    return json<RouteDetails>(`/api/routes/${id}`, { method: 'PATCH', body: JSON.stringify({ name }) })
  },

  duplicateRoute(id: number) {
    return json<RouteDetails>(`/api/routes/${id}/duplicate`, { method: 'POST' })
  },

  /** Moves the route to the trash. */
  async deleteRoute(id: number) {
    await send(`/api/routes/${id}`, { method: 'DELETE' })
  },

  setFavorite(id: number, favorite: boolean) {
    return json<RouteDetails>(`/api/routes/${id}/favorite`, { method: favorite ? 'PUT' : 'DELETE' })
  },

  listArchive() {
    return json<RouteSummary[]>('/api/routes/archive')
  },

  archiveRoute(id: number) {
    return json<RouteDetails>(`/api/routes/${id}/archive`, { method: 'POST' })
  },

  unarchiveRoute(id: number) {
    return json<RouteDetails>(`/api/routes/${id}/archive`, { method: 'DELETE' })
  },

  listTrash() {
    return json<RouteSummary[]>('/api/routes/trash')
  },

  restoreRoute(id: number) {
    return json<RouteDetails>(`/api/routes/${id}/restore`, { method: 'POST' })
  },

  async deleteRoutePermanently(id: number) {
    await send(`/api/routes/${id}/permanent`, { method: 'DELETE' })
  },

  emptyTrash() {
    return json<{ deleted: number }>('/api/routes/trash', { method: 'DELETE' })
  },

  /** The route in the planner as a file in the given format; the track is recalculated. */
  async exportDraft(format: string, draft: RouteDraft): Promise<File> {
    const response = await send(`/api/export/${format}`, {
      method: 'POST',
      body: JSON.stringify(draft),
      headers: { Accept: '*/*' },
    })
    return toFile(response)
  },

  /**
   * A saved route as a file with its stored track; with `original`, an imported route's original
   * track as imported, even after it was edited.
   */
  async exportSaved(id: number, format: string, original = false): Promise<File> {
    const query = original ? '?original=true' : ''
    const response = await send(`/api/routes/${id}/export/${format}${query}`, { headers: { Accept: '*/*' } })
    return toFile(response)
  },

  /** Puts an imported route's original track back; it is view-only again. */
  revertRoute(id: number) {
    return json<RouteDetails>(`/api/routes/${id}/revert`, { method: 'POST' })
  },

  devices() {
    return json<DeviceInfo[]>('/api/devices')
  },

  /** What syncing the connected device would change; changes nothing. */
  syncPlan(deviceId: string) {
    return json<SyncPlan>(`/api/devices/${deviceId}/sync`)
  },

  /** Copies new and changed routes; deletes only the listed files that are not active routes. */
  sync(deviceId: string, remove: string[]) {
    return json<SyncResult>(`/api/devices/${deviceId}/sync`, {
      method: 'POST',
      body: JSON.stringify({ remove }),
    })
  },

  importFiles(files: File[]) {
    const form = new FormData()
    for (const file of files) form.append('files', file)
    return json<ImportResult[]>('/api/import/files', { method: 'POST', body: form })
  },

  listRides() {
    return json<RideSummary[]>('/api/rides')
  },

  getRide(id: number) {
    return json<RideDetails>(`/api/rides/${id}`)
  },

  renameRide(id: number, name: string) {
    return json<RideSummary>(`/api/rides/${id}`, { method: 'PATCH', body: JSON.stringify({ name }) })
  },

  /** Deletes the ride; importing from the device does not bring it back. */
  async deleteRide(id: number) {
    await send(`/api/rides/${id}`, { method: 'DELETE' })
  },

  /** Adds the ride's track to the library as a route (once). */
  saveRideAsRoute(id: number) {
    return json<ImportResult>(`/api/rides/${id}/route`, { method: 'POST' })
  },

  /** The ride's FIT file as recorded. */
  async rideFile(id: number): Promise<File> {
    return toFile(await send(`/api/rides/${id}/file`, { headers: { Accept: '*/*' } }))
  },

  importRideFiles(files: File[]) {
    const form = new FormData()
    for (const file of files) form.append('files', file)
    return json<RideImportResult[]>('/api/rides/files', { method: 'POST', body: form })
  },

  /** Imports the rides on the connected device that are not imported yet. */
  importRidesFromDevice(deviceId: string) {
    return json<DeviceImportResult>(`/api/rides/device/${deviceId}`, { method: 'POST' })
  },

  settings() {
    return json<Settings>('/api/settings')
  },

  presets() {
    return json<Preset[]>('/api/presets')
  },

  createPreset(name: string, preferences: RoutingPreferences) {
    return json<Preset>('/api/presets', { method: 'POST', body: JSON.stringify({ name, preferences }) })
  },

  updatePreset(id: number, name: string, preferences: RoutingPreferences) {
    return json<Preset>(`/api/presets/${id}`, { method: 'PUT', body: JSON.stringify({ name, preferences }) })
  },

  async deletePreset(id: number) {
    await send(`/api/presets/${id}`, { method: 'DELETE' })
  },

  riders() {
    return json<RiderSummary[]>('/api/users')
  },

  createRider(name: string) {
    return json<Rider>('/api/users', { method: 'POST', body: JSON.stringify({ name }) })
  },

  /** null: files only. */
  setRiderDevice(id: number, device: string | null) {
    return json<Rider>(`/api/users/${id}/device`, { method: 'PUT', body: JSON.stringify({ device }) })
  },

  renameRider(id: number, name: string) {
    return json<Rider>(`/api/users/${id}`, { method: 'PATCH', body: JSON.stringify({ name }) })
  },

  /** Deletes the rider with all their routes. */
  async deleteRider(id: number) {
    await send(`/api/users/${id}`, { method: 'DELETE' })
  },

  session() {
    return json<{ user: Rider | null }>('/api/session')
  },

  chooseRider(userId: number) {
    return json<{ user: Rider }>('/api/session', { method: 'PUT', body: JSON.stringify({ userId }) })
  },

  komootStatus() {
    return json<KomootStatus>('/api/komoot/status')
  },

  komootLogin(email: string, password: string) {
    return json<KomootStatus>('/api/komoot/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
  },

  async komootLogout() {
    await send('/api/komoot/login', { method: 'DELETE' })
  },

  komootTours() {
    return json<KomootTour[]>('/api/komoot/tours')
  },

  komootImport(tourIds: number[]) {
    return json<ImportResult[]>('/api/komoot/import', {
      method: 'POST',
      body: JSON.stringify({ tourIds }),
    })
  },
}

/** The response as a file named the way the server suggests (Content-Disposition). */
async function toFile(response: Response): Promise<File> {
  const disposition = response.headers.get('Content-Disposition') ?? ''
  const name = /filename="?([^";]+)"?/.exec(disposition)?.[1] ?? 'route'
  const blob = await response.blob()
  return new File([blob], name, { type: blob.type })
}

export interface Place {
  name: string
  lat: number
  lng: number
  /** The place's outline or line (e.g. a road segment), simplified to a few metres. */
  geometry: Geometry | null
  bounds: Bounds | null
}

interface NominatimResult {
  display_name: string
  lat: string
  lon: string
  /** south, north, west, east */
  boundingbox?: [string, string, string, string]
  geojson?: Geometry
}

/**
 * Place search via OpenStreetMap Nominatim (fair use: only on explicit search). With an area, only
 * places inside it are returned.
 */
export async function searchPlaces(query: string, area?: Bounds): Promise<Place[]> {
  const params = new URLSearchParams({
    format: 'json',
    limit: '8',
    q: query,
    polygon_geojson: '1',
    polygon_threshold: '0.00005',
    'accept-language': currentLocale(), // place names in the app's language, where OSM has them
  })
  if (area) {
    params.set('viewbox', [area.west, area.north, area.east, area.south].map((v) => v.toFixed(5)).join(','))
    params.set('bounded', '1')
  }
  const url = `https://nominatim.openstreetmap.org/search?${params.toString()}`
  const response = await fetch(url, { headers: { Accept: 'application/json' } })
  if (!response.ok) throw new ApiError(t('common.error.placeSearch'), response.status)
  const results = (await response.json()) as NominatimResult[]
  return results.map((r) => {
    const box = r.boundingbox?.map(Number)
    return {
      name: r.display_name,
      lat: Number(r.lat),
      lng: Number(r.lon),
      geometry: r.geojson ?? null,
      bounds:
        box?.length === 4 && box.every(Number.isFinite)
          ? { south: box[0] ?? 0, north: box[1] ?? 0, west: box[2] ?? 0, east: box[3] ?? 0 }
          : null,
    }
  })
}
