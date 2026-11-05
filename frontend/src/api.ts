import type {
  CloudSyncResult,
  DeviceInfo,
  GeoPoint,
  ImportResult,
  KomootStatus,
  KomootTour,
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
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

interface ProblemDetail {
  title?: string
  detail?: string
}

async function errorMessage(response: Response): Promise<string> {
  try {
    const problem = (await response.json()) as ProblemDetail
    return problem.detail ?? problem.title ?? `Request failed (${response.status})`
  } catch {
    return `Request failed (${response.status})`
  }
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
  if (!response.ok) throw new ApiError(await errorMessage(response), response.status)
  return response
}

async function json<T>(path: string, init?: RequestInit, signal?: AbortSignal): Promise<T> {
  const response = await send(path, init, signal)
  return (await response.json()) as T
}

export const api = {
  routeLeg(from: GeoPoint, to: GeoPoint, preferences: RoutingPreferences, signal?: AbortSignal) {
    return json<RouteLeg>(
      '/api/routing/leg',
      { method: 'POST', body: JSON.stringify({ from, to, preferences }) },
      signal,
    )
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
