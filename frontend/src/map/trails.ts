import type { ExpressionSpecification, FilterSpecification, Map as MapLibreMap } from 'maplibre-gl'
import type { Activity, TrailDifficulty } from '@/types'

/**
 * Trail overlay drawn from the OpenStreetMap data in the vector base map (OpenMapTiles schema,
 * layer "transportation"), so it can be styled freely and shows on every base map. The tiles stop at
 * zoom 14; closer in, MapLibre enlarges them.
 */

/** The vector source of the OpenFreeMap style. */
const SOURCE = 'openmaptiles'
const SOURCE_LAYER = 'transportation'
const MIN_ZOOM = 12

/** Colours of the mtb:scale grades, as on ski runs: S0 green, S1 blue, S2 red, S3 and harder black. */
export const TRAIL_COLORS = {
  s0: '#2E9E44',
  s1: '#1F6FD1',
  s2: '#D7261E',
  s3: '#1B1B1B',
  unrated: '#8B5A2B',
  forbidden: '#8C8C8C',
} as const

export const TRAIL_LAYERS = ['trails-halo', 'trails-forbidden', 'trails-unrated', 'trails-rated'] as const

/** mtb:scale as a number (0–6; "1+" counts as 1), or -1 when the trail has none. */
const GRADE: ExpressionSpecification = [
  'case',
  ['all', ['has', 'mtb_scale'], ['!=', ['get', 'mtb_scale'], '']],
  ['to-number', ['slice', ['to-string', ['get', 'mtb_scale']], 0, 1], -1],
  -1,
]

/** Paths and tracks; not steps, platforms or pedestrian areas. */
const TRAILS: ExpressionSpecification = [
  'any',
  ['==', ['get', 'class'], 'track'],
  [
    'all',
    ['==', ['get', 'class'], 'path'],
    [
      'in',
      ['coalesce', ['get', 'subclass'], 'path'],
      ['literal', ['path', 'footway', 'cycleway', 'bridleway']],
    ],
  ],
]

const NO_ACCESS: ExpressionSpecification = [
  'in',
  ['coalesce', ['get', 'access'], ''],
  ['literal', ['no', 'private']],
]

/**
 * Closed to the activity: access=no/private, and for bikes bicycle=no. The tiles carry no horse
 * tag, so for horses only general access counts.
 */
function forbidden(activity: Activity): ExpressionSpecification {
  return activity === 'HORSE'
    ? NO_ACCESS
    : ['any', NO_ACCESS, ['==', ['coalesce', ['get', 'bicycle'], ''], 'no']]
}

const PAVED: ExpressionSpecification = ['==', ['coalesce', ['get', 'surface'], ''], 'paved']

function filters(activity: Activity): Record<'forbidden' | 'rated' | 'unrated', FilterSpecification> {
  const closed = forbidden(activity)
  return {
    forbidden: ['all', TRAILS, closed],
    rated: ['all', TRAILS, ['!', closed], ['>=', GRADE, 0]],
    // Unrated paths and tracks that are not paved: paved ones are on every base map already.
    unrated: ['all', TRAILS, ['!', closed], ['<', GRADE, 0], ['!', PAVED]],
  }
}
const haloFilter: FilterSpecification = ['all', TRAILS, ['any', ['>=', GRADE, 0], ['!', PAVED]]]

const width = (base: number): ExpressionSpecification => [
  'interpolate',
  ['linear'],
  ['zoom'],
  MIN_ZOOM,
  base * 0.6,
  15,
  base * 1.3,
  18,
  base * 2.5,
]

const GRADE_COLOR: ExpressionSpecification = [
  'step',
  GRADE,
  TRAIL_COLORS.s0,
  1,
  TRAIL_COLORS.s1,
  2,
  TRAIL_COLORS.s2,
  3,
  TRAIL_COLORS.s3,
]

const MAX_GRADE: Record<TrailDifficulty, number> = { S0: 0, S1: 1, S2: 2, S3: 3, S4: 4, S5: 5, ANY: 99 }

/** What the overlay adapts to: the planned activity and, for mountain bikes, the hardest grade. */
export interface TrailStyle {
  activity: Activity
  maxDifficulty: TrailDifficulty
}

const ANY_STYLE: TrailStyle = { activity: 'MTB', maxDifficulty: 'ANY' }

/** Mountain bikes: trails harder than the rider allows are faded. */
function ratedOpacity(style: TrailStyle): ExpressionSpecification {
  const max = style.activity === 'MTB' ? MAX_GRADE[style.maxDifficulty] : MAX_GRADE.ANY
  return ['case', ['>', GRADE, max], 0.3, 0.95]
}

/** Adds the (hidden) trail layers. Call after the raster layers, so they are drawn on top of them. */
export function addTrailLayers(map: MapLibreMap, style: TrailStyle = ANY_STYLE) {
  if (!map.getSource(SOURCE)) return
  const filter = filters(style.activity)
  const common = { source: SOURCE, 'source-layer': SOURCE_LAYER, minzoom: MIN_ZOOM } as const
  const layout = { visibility: 'none', 'line-join': 'round', 'line-cap': 'round' } as const
  map.addLayer({
    id: 'trails-halo',
    type: 'line',
    ...common,
    filter: haloFilter,
    layout,
    paint: { 'line-color': '#ffffff', 'line-width': width(4.5), 'line-opacity': 0.55 },
  })
  map.addLayer({
    id: 'trails-forbidden',
    type: 'line',
    ...common,
    filter: filter.forbidden,
    layout: { ...layout, 'line-cap': 'butt' },
    paint: {
      'line-color': TRAIL_COLORS.forbidden,
      'line-width': width(2),
      'line-dasharray': [1, 1.5],
      'line-opacity': 0.8,
    },
  })
  map.addLayer({
    id: 'trails-unrated',
    type: 'line',
    ...common,
    filter: filter.unrated,
    layout: { ...layout, 'line-cap': 'butt' },
    paint: {
      'line-color': TRAIL_COLORS.unrated,
      'line-width': width(2),
      'line-dasharray': [3, 1.5],
      'line-opacity': 0.9,
    },
  })
  map.addLayer({
    id: 'trails-rated',
    type: 'line',
    ...common,
    filter: filter.rated,
    layout,
    paint: {
      'line-color': GRADE_COLOR,
      'line-width': width(3),
      'line-opacity': ratedOpacity(style),
    },
  })
}

export function showTrails(map: MapLibreMap, visible: boolean) {
  for (const id of TRAIL_LAYERS) {
    if (map.getLayer(id)) map.setLayoutProperty(id, 'visibility', visible ? 'visible' : 'none')
  }
}

export function setTrailStyle(map: MapLibreMap, style: TrailStyle) {
  if (!map.getLayer('trails-rated')) return
  const filter = filters(style.activity)
  map.setFilter('trails-forbidden', filter.forbidden)
  map.setFilter('trails-rated', filter.rated)
  map.setFilter('trails-unrated', filter.unrated)
  map.setPaintProperty('trails-rated', 'line-opacity', ratedOpacity(style))
}
