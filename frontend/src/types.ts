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

/**
 * How the leg from a waypoint to the next is calculated: ROUTED with the rider's preferences,
 * FORCED allowing closed and restricted ways (with warnings), or a STRAIGHT line.
 */
export type LegType = 'ROUTED' | 'FORCED' | 'STRAIGHT'

/** A waypoint; `leg` (default ROUTED) applies to the leg towards the next waypoint. */
export interface Waypoint extends GeoPoint {
  leg?: LegType
}

/** A closed or restricted way on a forced leg. */
export interface RouteWarning {
  lng: number
  lat: number
  reason: string
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
  warnings: RouteWarning[]
}

export interface RouteDraft {
  name: string
  waypoints: Waypoint[]
  preferences: RoutingPreferences
  /** When saving changes: the route's updatedAt as last seen; refused if saved elsewhere since. */
  expectedUpdatedAt?: string | null
}

export type RouteSource = 'PLANNER' | 'GPX' | 'FIT' | 'KOMOOT'

export interface RouteSummary {
  id: number
  name: string
  distance: number
  ascent: number
  updatedAt: string
  source: RouteSource
  /** False for imported routes that have not been converted to waypoints yet. */
  editable: boolean
  /** Set while the route is in the trash. */
  deletedAt: string | null
  /** Set while the route is archived (out of the main list, but kept). */
  archivedAt: string | null
}

export interface RouteDetails {
  id: number
  name: string
  /** Empty for imported routes that are not editable yet. */
  waypoints: Waypoint[]
  preferences: RoutingPreferences
  /** The stored track; for imported routes the original one. */
  track: TrackPoint[]
  distance: number
  ascent: number
  descent: number
  createdAt: string
  updatedAt: string
  source: RouteSource
  editable: boolean
  /** Imported routes: the original track is kept and can be restored or downloaded. */
  hasOriginal: boolean
  deletedAt: string | null
  archivedAt: string | null
}

export interface FormatInfo {
  id: string
  label: string
  extension: string
}

export interface TransferMethod {
  title: string
  steps: string[]
}

export interface CloudInfo {
  serviceName: string
  enabled: boolean
  connected: boolean
  connectPath: string
}

/** A device plugin as described by the backend (app.trailplan.devices). */
export interface DeviceInfo {
  id: string
  name: string
  formats: FormatInfo[]
  transferMethods: TransferMethod[]
  importInstructions: string | null
  cloud: CloudInfo | null
}

export interface CloudSyncResult {
  service: string
  remoteId: string
  syncedAt: string
}

export interface ImportResult {
  /** File name or Komoot tour id. */
  source: string
  status: 'IMPORTED' | 'ALREADY_IMPORTED' | 'FAILED'
  routeId: number | null
  name: string | null
  message: string | null
}

export interface KomootStatus {
  connected: boolean
  displayName: string | null
}

export interface KomootTour {
  id: number
  name: string
  planned: boolean
  sport: string
  distance: number
  ascent: number
  date: string | null
  imported: boolean
}

/** A rider (no password); each has their own library. */
export interface Rider {
  id: number
  name: string
}

export interface RiderSummary extends Rider {
  /** Routes in the library, not counting the trash. */
  routes: number
}

/** Waypoints for editing an imported route, checked against the router. */
export interface ConversionResult {
  waypoints: Waypoint[]
  forcedLegs: number
  straightLegs: number
}
