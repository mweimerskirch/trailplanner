// Mirrors the backend API (app.trailplan.*). Keep in sync with the Java records.

export type WayCategory =
  'TRAIL' | 'ROUGH_TRACK' | 'GRAVEL_TRACK' | 'FOOTWAY' | 'CYCLEWAY' | 'QUIET_ROAD' | 'MAIN_ROAD' | 'BUSY_ROAD'

export type PreferenceLevel = 'NEVER' | 'AVOID' | 'NEUTRAL' | 'PREFER' | 'FAVOURITE'
export type TrailDifficulty = 'S0' | 'S1' | 'S2' | 'S3' | 'S4' | 'S5' | 'ANY'
export type WoodsPreference = 'INDIFFERENT' | 'PREFER' | 'STRONGLY_PREFER'
export type Surface = 'PAVED' | 'UNPAVED' | 'UNKNOWN'

export interface RoutingPreferences {
  levels: Record<WayCategory, PreferenceLevel>
  maxDifficulty: TrailDifficulty
  woods: WoodsPreference
  avoidClimbs: boolean
}

export interface GeoPoint {
  lat: number
  lng: number
}

/** [lng, lat, elevation] – elevation is null where the elevation model has no data. */
export type TrackPoint = [number, number, number | null]

export interface LegBreakdown {
  wayTypes: Partial<Record<WayCategory, number>>
  other: number
  surfaces: Partial<Record<Surface, number>>
}

export interface RouteLeg {
  points: TrackPoint[]
  distance: number
  ascent: number
  breakdown: LegBreakdown
}

export interface RouteDraft {
  name: string
  waypoints: GeoPoint[]
  preferences: RoutingPreferences
}

export interface RouteSummary {
  id: number
  name: string
  distance: number
  ascent: number
  updatedAt: string
  wahooSyncedAt: string | null
}

export interface RouteDetails {
  id: number
  name: string
  waypoints: GeoPoint[]
  preferences: RoutingPreferences
  distance: number
  ascent: number
  createdAt: string
  updatedAt: string
  wahooSyncedAt: string | null
}

export interface WahooStatus {
  enabled: boolean
  connected: boolean
}

export interface WahooSyncResult {
  wahooRouteId: number
  syncedAt: string
}
