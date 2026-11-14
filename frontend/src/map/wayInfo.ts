import * as maplibregl from 'maplibre-gl'
import type { GeoJSONSource, Map as MapLibreMap, MapGeoJSONFeature, MapMouseEvent } from 'maplibre-gl'

type Position = number[]
import type { Activity } from '@/types'
import { i18n, t } from '@/i18n'
import { TRAIL_COLORS } from './trails'

/**
 * Hover info for the ways in the vector tiles (OpenMapTiles "transportation" layer): name, type,
 * difficulty, surface, bike access, in the chosen language (OSM values without a translation are
 * shown as they are). The tiles carry nothing more (no tracktype, smoothness or
 * width).
 */

type Props = Record<string, unknown>

/** The translation of an OSM value under a message group, or null if there is none. */
function label(group: string, value: string): string | null {
  const key = `map.way.${group}.${value}`
  return i18n.global.te(key) ? t(key) : null
}

const text = (value: unknown): string | null =>
  typeof value === 'string' && value !== '' ? value : typeof value === 'number' ? String(value) : null

export interface WayInfo {
  title: string
  /** The type, when the title is the way's name. */
  type: string | null
  rows: { label: string; value: string; color?: string | undefined }[]
}

/** The type of way ("Path", "Track", "Minor road" …), or null for what is not a way (rail, piers). */
export function wayType(props: Props): string | null {
  const cls = text(props.class)
  if (!cls) return null
  const construction = cls.endsWith('_construction')
  const base = construction ? cls.slice(0, -'_construction'.length) : cls
  let type: string | null
  if (base === 'path') type = label('subclass', text(props.subclass) ?? 'path') ?? t('map.way.subclass.path')
  else type = label('class', base)
  if (!type) return null
  const service = text(props.service)
  const serviceLabel = service ? label('service', service) : null
  if (serviceLabel) type = t('map.way.withService', { type, service: serviceLabel })
  return construction ? t('map.way.construction', { type }) : type
}

export function gradeColor(grade: number): string {
  if (grade <= 0) return TRAIL_COLORS.s0
  if (grade === 1) return TRAIL_COLORS.s1
  if (grade === 2) return TRAIL_COLORS.s2
  return TRAIL_COLORS.s3
}

/** What to show for a way, or null if the feature is not a way. */
export function describeWay(props: Props, name: string | null, activity: Activity = 'MTB'): WayInfo | null {
  const type = wayType(props)
  if (!type) return null
  const rows: WayInfo['rows'] = []
  const scale = text(props.mtb_scale)
  if (scale) {
    const grade = Number.parseInt(scale, 10)
    rows.push({
      label: t('map.way.row.difficulty'),
      value: `S${scale}`,
      color: Number.isNaN(grade) ? undefined : gradeColor(grade),
    })
  }
  const surface = text(props.surface)
  if (surface) rows.push({ label: t('map.way.row.surface'), value: label('surface', surface) ?? surface })
  const bicycle = text(props.bicycle)
  // The tiles have no horse tag; bike access says nothing about horses.
  if (bicycle && activity !== 'HORSE')
    rows.push({ label: t('map.way.row.bikes'), value: label('bicycle', bicycle) ?? bicycle })
  const access = text(props.access)
  if (access && access !== 'yes')
    rows.push({ label: t('map.way.row.access'), value: label('access', access) ?? access })
  const brunnel = text(props.brunnel)
  const extras = [
    brunnel === 'bridge' || brunnel === 'tunnel' || brunnel === 'ford' ? t(`map.way.extra.${brunnel}`) : null,
    props.oneway === 1 || props.oneway === -1 ? t('map.way.extra.oneway') : null,
  ].filter((x): x is string => x !== null)
  if (extras.length) rows.push({ label: t('map.way.row.note'), value: extras.join(', ') })
  return name ? { title: name, type, rows } : { title: type, type: null, rows }
}

interface Pixel {
  x: number
  y: number
}

/** Distance from p to the polyline, in the polyline's units. */
export function distanceToLine(p: Pixel, line: Pixel[]): number {
  if (line.length === 1 && line[0]) return Math.hypot(p.x - line[0].x, p.y - line[0].y)
  let best = Infinity
  for (let i = 1; i < line.length; i++) {
    const a = line[i - 1]
    const b = line[i]
    if (!a || !b) continue
    const dx = b.x - a.x
    const dy = b.y - a.y
    const length2 = dx * dx + dy * dy
    const t = length2 === 0 ? 0 : Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / length2))
    best = Math.min(best, Math.hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy)))
  }
  return best
}

function escape(value: string): string {
  return value.replace(/[&<>"']/g, (c) => `&#${String(c.charCodeAt(0))};`)
}

export function wayInfoHtml(info: WayInfo): string {
  const rows = info.rows
    .map((r) => {
      const chip = r.color ? `<span class="chip" style="background:${r.color}"></span>` : ''
      return `<dt>${escape(r.label)}</dt><dd>${chip}${escape(r.value)}</dd>`
    })
    .join('')
  return (
    `<strong>${escape(info.title)}</strong>` +
    (info.type ? `<span class="type">${escape(info.type)}</span>` : '') +
    (rows ? `<dl>${rows}</dl>` : '')
  )
}

const SOURCE = 'openmaptiles'
const NAME_LAYER = 'way-info-names'
const HIGHLIGHT = 'way-info-highlight'
const EMPTY = { type: 'FeatureCollection' as const, features: [] }
/** Highlight colour of the hovered way: a glow under the trail colours, visible on any map. */
export const HIGHLIGHT_COLOR = '#FFD400'
const TOLERANCE = 5

/**
 * Shows a tooltip for the way under the mouse, and highlights it, while enabled() is true. blocked(event) lets the
 * caller suppress it (e.g. over the route, which has its own hover). Returns a function that
 * removes it.
 */
export function attachWayInfo(
  map: MapLibreMap,
  options: {
    enabled: () => boolean
    blocked?: (event: MapMouseEvent) => boolean
    /** The planned activity (default mountain bike): decides which access rows are shown. */
    activity?: () => Activity
  },
): () => void {
  if (!map.getSource(SOURCE)) return () => undefined
  // Names are in a separate tile layer; this invisible line layer makes them queryable.
  if (!map.getLayer(NAME_LAYER)) {
    map.addLayer({
      id: NAME_LAYER,
      type: 'line',
      source: SOURCE,
      'source-layer': 'transportation_name',
      minzoom: 12,
      paint: { 'line-opacity': 0, 'line-width': 8 },
    })
  }
  // The hovered way: under the trail overlay (so its colours stay readable) and under any route.
  if (!map.getSource(HIGHLIGHT)) {
    map.addSource(HIGHLIGHT, { type: 'geojson', data: EMPTY })
    const below = ['trails-halo', 'route-casing', 'track-casing'].find((id) => map.getLayer(id))
    map.addLayer(
      {
        id: HIGHLIGHT,
        type: 'line',
        source: HIGHLIGHT,
        layout: { 'line-join': 'round', 'line-cap': 'round' },
        paint: {
          'line-color': HIGHLIGHT_COLOR,
          'line-width': ['interpolate', ['linear'], ['zoom'], 12, 10, 16, 18, 18, 24],
          'line-opacity': 0.75,
          'line-blur': 1,
        },
      },
      below,
    )
  }
  const highlight = (line: Position[] | null) => {
    void map
      .getSource<GeoJSONSource>(HIGHLIGHT)
      ?.setData(
        line
          ? { type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: line } }
          : EMPTY,
      )
  }
  let highlighted: Position[] | null = null

  const popup = new maplibregl.Popup({
    closeButton: false,
    closeOnClick: false,
    className: 'way-info',
    offset: 14,
    maxWidth: '240px',
  })
  let frame = 0
  let last: MapMouseEvent | null = null

  const hide = () => {
    popup.remove()
    if (highlighted) {
      highlighted = null
      highlight(null)
    }
  }

  function update() {
    frame = 0
    const event = last
    if (!event) return
    const target = event.originalEvent.target
    if (
      !options.enabled() ||
      event.originalEvent.buttons !== 0 ||
      target !== map.getCanvas() ||
      map.getZoom() < 12 ||
      options.blocked?.(event)
    ) {
      hide()
      return
    }
    const { x, y } = event.point
    const box: [[number, number], [number, number]] = [
      [x - TOLERANCE, y - TOLERANCE],
      [x + TOLERANCE, y + TOLERANCE],
    ]
    const features = map.queryRenderedFeatures(box)
    let info: WayInfo | null = null
    let line: Position[] | null = null
    for (const f of features) {
      if (f.source !== SOURCE || f.sourceLayer !== 'transportation') continue
      const named = nameFeature(f, features)
      info = describeWay(
        f.properties,
        named ? (text(named.properties.name) ?? text(named.properties.ref)) : null,
        options.activity?.() ?? 'MTB',
      )
      if (!info) continue
      // The tiles merge neighbouring ways with the same attributes into one feature. Highlight
      // the line nearest the mouse: of the named street if there is one, else of the way.
      line = nearestLine(named ?? f, event.point) ?? nearestLine(f, event.point)
      break
    }
    if (!info || !line) {
      hide()
      return
    }
    if (JSON.stringify(line) !== JSON.stringify(highlighted)) {
      highlighted = line
      highlight(line)
    }
    popup.setLngLat(event.lngLat).setHTML(wayInfoHtml(info))
    if (!popup.isOpen()) popup.addTo(map)
  }

  function nameFeature(way: MapGeoJSONFeature, features: MapGeoJSONFeature[]): MapGeoJSONFeature | null {
    return (
      features.find((f) => f.layer.id === NAME_LAYER && f.properties.class === way.properties.class) ?? null
    )
  }

  /** The part of a (multi)line feature closest to the point, in screen pixels. */
  function nearestLine(feature: MapGeoJSONFeature, point: Pixel): Position[] | null {
    const g = feature.geometry
    const parts: Position[][] =
      g.type === 'LineString' ? [g.coordinates] : g.type === 'MultiLineString' ? g.coordinates : []
    let best: Position[] | null = null
    let bestDistance = Infinity
    for (const part of parts) {
      const screen = part.map(([lng, lat]) => map.project([lng ?? 0, lat ?? 0]))
      const distance = distanceToLine(point, screen)
      if (distance < bestDistance) {
        bestDistance = distance
        best = part
      }
    }
    return best
  }

  const onMove = (event: MapMouseEvent) => {
    last = event
    if (!frame) frame = requestAnimationFrame(update)
  }
  map.on('mousemove', onMove)
  map.on('mouseout', hide)
  map.on('movestart', hide)
  return () => {
    if (frame) cancelAnimationFrame(frame)
    map.off('mousemove', onMove)
    map.off('mouseout', hide)
    map.off('movestart', hide)
    hide()
  }
}
