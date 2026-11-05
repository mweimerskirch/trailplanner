import type {
  CloudSyncResult,
  ConversionResult,
  LegType,
  DeviceInfo,
  GeoPoint,
  ImportResult,
  KomootStatus,
  KomootTour,
  Rider,
  RiderSummary,
  RouteDetails,
  RouteDraft,
  RouteLeg,
  RouteSummary,
  RoutingPreferences,
} from './types'

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
    const message = problem.detail ?? problem.title ?? `Request failed (${response.status})`
    return new ApiError(message, response.status, problem.reason ?? null)
  } catch {
    return new ApiError(`Request failed (${response.status})`, response.status)
  }
}

let noRiderHandler: (() => void) | null = null

/** Called when the server says no rider is chosen (e.g. the rider was deleted meanwhile). */
export function onNoRider(handler: () => void) {
  noRiderHandler = handler
}

async function send(path: string, init: RequestInit = {}, signal?: AbortSignal): Promise<Response> {
  const headers = new Headers(init.headers)
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
    throw new ApiError('The Trailplan server is not reachable.', 0)
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
  async exportDraft(format: string, draft: RouteDraft): Promise<Blob> {
    const response = await send(`/api/export/${format}`, {
      method: 'POST',
      body: JSON.stringify(draft),
      headers: { Accept: '*/*' },
    })
    return response.blob()
  },

  /** A saved route as a file, with its stored track (the original one for imported routes). */
  async exportSaved(id: number, format: string): Promise<Blob> {
    const response = await send(`/api/routes/${id}/export/${format}`, { headers: { Accept: '*/*' } })
    return response.blob()
  },

  devices() {
    return json<DeviceInfo[]>('/api/devices')
  },

  sendToCloud(deviceId: string, routeId: number) {
    return json<CloudSyncResult>(`/api/devices/${deviceId}/cloud/routes/${routeId}`, { method: 'POST' })
  },

  async disconnectCloud(deviceId: string) {
    await send(`/api/devices/${deviceId}/cloud/connection`, { method: 'DELETE' })
  },

  importFiles(files: File[]) {
    const form = new FormData()
    for (const file of files) form.append('files', file)
    return json<ImportResult[]>('/api/import/files', { method: 'POST', body: form })
  },

  riders() {
    return json<RiderSummary[]>('/api/users')
  },

  createRider(name: string) {
    return json<Rider>('/api/users', { method: 'POST', body: JSON.stringify({ name }) })
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

export interface Place {
  name: string
  lat: number
  lng: number
}

interface NominatimResult {
  display_name: string
  lat: string
  lon: string
}

/** Place search via OpenStreetMap Nominatim (fair use: only on explicit search). */
export async function searchPlaces(query: string): Promise<Place[]> {
  const url = `https://nominatim.openstreetmap.org/search?format=json&limit=6&q=${encodeURIComponent(query)}`
  const response = await fetch(url, { headers: { Accept: 'application/json' } })
  if (!response.ok) throw new ApiError('Place search is unavailable right now.', response.status)
  const results = (await response.json()) as NominatimResult[]
  return results.map((r) => ({ name: r.display_name, lat: Number(r.lat), lng: Number(r.lon) }))
}
