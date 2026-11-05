import type {
  GeoPoint,
  RouteDetails,
  RouteDraft,
  RouteLeg,
  RouteSummary,
  RoutingPreferences,
  WahooStatus,
  WahooSyncResult,
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
  if (!headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
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

  async deleteRoute(id: number) {
    await send(`/api/routes/${id}`, { method: 'DELETE' })
  },

  async gpx(draft: RouteDraft): Promise<Blob> {
    const response = await send('/api/export/gpx', {
      method: 'POST',
      body: JSON.stringify(draft),
      headers: { Accept: 'application/gpx+xml' },
    })
    return response.blob()
  },

  wahooStatus() {
    return json<WahooStatus>('/api/wahoo/status')
  },

  syncToWahoo(id: number) {
    return json<WahooSyncResult>(`/api/routes/${id}/wahoo`, { method: 'POST' })
  },

  async disconnectWahoo() {
    await send('/api/wahoo/connection', { method: 'DELETE' })
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
