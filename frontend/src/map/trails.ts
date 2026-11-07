import type { ExpressionSpecification, FilterSpecification, Map as MapLibreMap } from 'maplibre-gl'
import type { TrailDifficulty } from '@/types'

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

/** Closed to bikes: access=no/private or bicycle=no. */
const FORBIDDEN: ExpressionSpecification = [
  'any',
  ['in', ['coalesce', ['get', 'access'], ''], ['literal', ['no', 'private']]],
  ['==', ['coalesce', ['get', 'bicycle'], ''], 'no'],
]

const PAVED: ExpressionSpecification = ['==', ['coalesce', ['get', 'surface'], ''], 'paved']

const forbiddenFilter: FilterSpecification = ['all', TRAILS, FORBIDDEN]
const ratedFilter: FilterSpecification = ['all', TRAILS, ['!', FORBIDDEN], ['>=', GRADE, 0]]
/** Unrated paths and tracks that are not paved: paved ones are on every base map already. */
const unratedFilter: FilterSpecification = ['all', TRAILS, ['!', FORBIDDEN], ['<', GRADE, 0], ['!', PAVED]]
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

/** Trails harder than the rider allows are faded. */
function ratedOpacity(maxDifficulty: TrailDifficulty): ExpressionSpecification {
  return ['case', ['>', GRADE, MAX_GRADE[maxDifficulty]], 0.3, 0.95]
}

/** Adds the (hidden) trail layers. Call after the raster layers, so they are drawn on top of them. */
export function addTrailLayers(map: MapLibreMap, maxDifficulty: TrailDifficulty = 'ANY') {
  if (!map.getSource(SOURCE)) return
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
    filter: forbiddenFilter,
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
    filter: unratedFilter,
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
    filter: ratedFilter,
    layout,
    paint: {
      'line-color': GRADE_COLOR,
      'line-width': width(3),
      'line-opacity': ratedOpacity(maxDifficulty),
    },
  })
}

export function showTrails(map: MapLibreMap, visible: boolean) {
  for (const id of TRAIL_LAYERS) {
    if (map.getLayer(id)) map.setLayoutProperty(id, 'visibility', visible ? 'visible' : 'none')
  }
}

export function setTrailDifficulty(map: MapLibreMap, maxDifficulty: TrailDifficulty) {
  if (map.getLayer('trails-rated')) {
    map.setPaintProperty('trails-rated', 'line-opacity', ratedOpacity(maxDifficulty))
  }
}
