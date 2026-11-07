import type { GeoJSONSource, Map as MapLibreMap } from 'maplibre-gl'
import type { Geometry } from 'geojson'
import { HIGHLIGHT_COLOR } from './wayInfo'

/** The place search result under the mouse: its road segment, outline or point, in yellow. */

const SOURCE = 'place-highlight'
const EMPTY = { type: 'FeatureCollection' as const, features: [] }

/** Adds the (empty) layers. Call before the route layers, so the route stays on top. */
export function addPlaceLayers(map: MapLibreMap) {
  map.addSource(SOURCE, { type: 'geojson', data: EMPTY })
  map.addLayer({
    id: 'place-fill',
    type: 'fill',
    source: SOURCE,
    filter: ['==', ['geometry-type'], 'Polygon'],
    paint: { 'fill-color': HIGHLIGHT_COLOR, 'fill-opacity': 0.15 },
  })
  map.addLayer({
    id: 'place-line',
    type: 'line',
    source: SOURCE,
    filter: ['!=', ['geometry-type'], 'Point'],
    layout: { 'line-join': 'round', 'line-cap': 'round' },
    paint: {
      'line-color': HIGHLIGHT_COLOR,
      'line-width': ['interpolate', ['linear'], ['zoom'], 10, 6, 16, 14, 18, 20],
      'line-opacity': 0.85,
    },
  })
  map.addLayer({
    id: 'place-point',
    type: 'circle',
    source: SOURCE,
    filter: ['==', ['geometry-type'], 'Point'],
    paint: {
      'circle-radius': 9,
      'circle-color': HIGHLIGHT_COLOR,
      'circle-opacity': 0.85,
      'circle-stroke-color': '#ffffff',
      'circle-stroke-width': 2,
    },
  })
}

export function showPlace(map: MapLibreMap, geometry: Geometry | null) {
  void map
    .getSource<GeoJSONSource>(SOURCE)
    ?.setData(geometry ? { type: 'Feature', properties: {}, geometry } : EMPTY)
}
