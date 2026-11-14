// Mirrors the backend API (app.trailplanner.*). Keep in sync with the Java records.

export type WayCategory =
  'TRAIL' | 'ROUGH_TRACK' | 'GRAVEL_TRACK' | 'FOOTWAY' | 'CYCLEWAY' | 'QUIET_ROAD' | 'MAIN_ROAD' | 'BUSY_ROAD'

export type PreferenceLevel = 'NEVER' | 'AVOID' | 'NEUTRAL' | 'PREFER' | 'FAVOURITE'
export type TrailDifficulty = 'S0' | 'S1' | 'S2' | 'S3' | 'S4' | 'S5' | 'ANY'
export type WoodsPreference = 'INDIFFERENT' | 'PREFER' | 'STRONGLY_PREFER'
export type Surface = 'PAVED' | 'UNPAVED' | 'UNKNOWN'
/** What a route is planned for (routing.Activity): access rules and surfaces depend on it. */
export type Activity = 'MTB' | 'ROAD' | 'HORSE'

export interface RoutingPreferences {
  levels: Record<WayCategory, PreferenceLevel>
  maxDifficulty: TrailDifficulty
  woods: WoodsPreference
  avoidClimbs: boolean
  activity: Activity
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
  /** English text, for codes the app does not know. */
  reason: string
  /** Stable reason code (UNDER_CONSTRUCTION, NO_BIKES …), translated in routing.warning. */
  code?: string | null
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

export type RouteSource = 'PLANNER' | 'GPX' | 'FIT' | 'KOMOOT' | 'RIDE'

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
  /** Starred by the rider. */
  favorite: boolean
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
  favorite: boolean
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

/** A device plugin as described by the backend (app.trailplanner.devices). */
export interface DeviceInfo {
  id: string
  name: string
  formats: FormatInfo[]
  transferMethods: TransferMethod[]
  importInstructions: string | null
  /** Syncing all active routes over USB, if the device supports it. */
  usbSync: UsbSyncInfo | null
}

export interface UsbSyncInfo {
  /** Short device name for buttons, e.g. "ELEMNT". */
  label: string
  folder: string
  afterSync: string | null
  /** What deleting a file does not do on the device, e.g. remove a route it already loaded. */
  removalNote: string | null
  /** Whether recorded rides can be imported from the device. */
  importsRides: boolean
}

/**
 * A recorded ride (mirrors rides.RideSummary). Durations in seconds, distances in metres, speeds
 * in m/s; values the device did not record are null.
 */
export interface RideSummary {
  id: number
  name: string
  /** e.g. "cycling", "running". */
  sport: string | null
  startedAt: string
  /** Local time minus UTC at the ride, in seconds. */
  utcOffset: number | null
  elapsed: number
  moving: number
  distance: number
  ascent: number
  descent: number
  /** Distance over moving time. */
  avgSpeed: number | null
  maxSpeed: number | null
  avgHeartRate: number | null
  maxHeartRate: number | null
  avgPower: number | null
  calories: number | null
  fileName: string
}

export interface RideDetails {
  ride: RideSummary
  /** [lng, lat, ele] per recorded position. */
  track: [number, number, number | null][]
  /** The library route made from this ride, if any. */
  routeId: number | null
}

export interface RideImportResult {
  fileName: string
  status: 'IMPORTED' | 'ALREADY_IMPORTED' | 'FAILED'
  rideId: number | null
  name: string | null
  message: string | null
}

export interface DeviceImportResult {
  device: string
  imported: number
  /** Files imported before (also of deleted rides). */
  skipped: number
  /** Recordings without GPS positions (e.g. started by accident), not imported. */
  withoutTrack: number
  failed: RideImportResult[]
}

/** A route file on the device; routeName is null for files that are not active routes. */
export interface SyncFile {
  fileName: string
  routeName: string | null
}

/** What syncing would change (app.trailplanner.devices.DeviceSyncService). */
export interface SyncPlan {
  /** The connected device's model, e.g. "ELEMNT-BOLT2". */
  device: string
  add: SyncFile[]
  update: SyncFile[]
  /** Files that are not active routes; deleted only if confirmed. */
  remove: SyncFile[]
  unchanged: number
}

export interface SyncResult {
  device: string
  added: number
  updated: number
  removed: number
  unchanged: number
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

/** A named riding style of the rider (routing preferences). */
export interface Preset {
  id: number
  name: string
  preferences: RoutingPreferences
  position: number
}

/** Where the map opens before anything is planned (config.StartArea). */
export interface StartArea {
  lat: number
  lng: number
  zoom: number
}

/** Server settings (config.SettingsController). */
export interface Settings {
  area: StartArea
}
