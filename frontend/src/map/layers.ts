import { t } from '@/i18n'
import type { ExpressionSpecification, Map as MapLibreMap, RasterSourceSpecification } from 'maplibre-gl'

/** Free vector base map (no API key). Raster maps below are drawn on top of it when chosen. */
export const VECTOR_STYLE_URL = 'https://tiles.openfreemap.org/styles/liberty'

const OSM = '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'

export type BaseMapId = 'vector' | 'cyclosm' | 'topo' | 'satellite'

export interface BaseMap {
  id: BaseMapId
  /** In the chosen language; read it when rendering (a getter, so it follows a language change). */
  readonly label: string
  source?: RasterSourceSpecification
}

/** The label is a getter, so the list defined at module load still follows the language. */
function baseMap(id: BaseMapId, label: () => string, source?: RasterSourceSpecification): BaseMap {
  return {
    id,
    get label() {
      return label()
    },
    ...(source ? { source } : {}),
  }
}

export const BASE_MAPS: readonly BaseMap[] = [
  baseMap('vector', () => t('map.layers.standard')),
  baseMap('cyclosm', () => t('map.layers.cyclosm'), {
    type: 'raster',
    tiles: ['a', 'b', 'c'].map((s) => `https://${s}.tile-cyclosm.openstreetmap.fr/cyclosm/{z}/{x}/{y}.png`),
    tileSize: 256,
    maxzoom: 20,
    attribution: `${OSM}, style <a href="https://www.cyclosm.org">CyclOSM</a>`,
  }),
  baseMap('topo', () => 'OpenTopoMap', {
    type: 'raster',
    tiles: ['a', 'b', 'c'].map((s) => `https://${s}.tile.opentopomap.org/{z}/{x}/{y}.png`),
    tileSize: 256,
    maxzoom: 17,
    attribution: `${OSM}, SRTM, style © <a href="https://opentopomap.org">OpenTopoMap</a> (CC-BY-SA)`,
  }),
  baseMap('satellite', () => t('map.layers.satellite'), {
    type: 'raster',
    tiles: ['https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}'],
    tileSize: 256,
    maxzoom: 19,
    attribution: 'Imagery © Esri',
  }),
]

export const MTB_OVERLAY: RasterSourceSpecification = {
  type: 'raster',
  tiles: ['https://tile.waymarkedtrails.org/mtb/{z}/{x}/{y}.png'],
  tileSize: 256,
  maxzoom: 18,
  attribution: '<a href="https://mtb.waymarkedtrails.org">Waymarked Trails</a>',
}

export function addRasterLayers(map: MapLibreMap) {
  for (const base of BASE_MAPS) {
    if (!base.source) continue
    map.addSource(`base-${base.id}`, base.source)
    map.addLayer({
      id: `base-${base.id}`,
      type: 'raster',
      source: `base-${base.id}`,
      layout: { visibility: 'none' },
    })
  }
  map.addSource('overlay-mtb', MTB_OVERLAY)
  map.addLayer({
    id: 'overlay-mtb',
    type: 'raster',
    source: 'overlay-mtb',
    layout: { visibility: 'none' },
    paint: { 'raster-opacity': 0.85 },
  })
}

export function showBaseMap(map: MapLibreMap, id: BaseMapId) {
  for (const base of BASE_MAPS) {
    if (base.source) {
      map.setLayoutProperty(`base-${base.id}`, 'visibility', base.id === id ? 'visible' : 'none')
    }
  }
}

export function showMtbOverlay(map: MapLibreMap, visible: boolean) {
  map.setLayoutProperty('overlay-mtb', 'visibility', visible ? 'visible' : 'none')
}

/** Colour of forced legs (closed or restricted ways allowed). */
export const FORCED_COLOR = '#E07B00'

/** Route colour by leg kind: forced legs stand out. */
function lineColor(routeColor: string): ExpressionSpecification {
  return ['case', ['==', ['get', 'kind'], 'FORCED'], FORCED_COLOR, routeColor]
}

export function addRouteLayers(map: MapLibreMap, routeColor: string) {
  const empty = { type: 'FeatureCollection' as const, features: [] }
  map.addSource('route', { type: 'geojson', data: empty })
  map.addSource('drag-preview', { type: 'geojson', data: empty })
  map.addSource('hover', { type: 'geojson', data: empty })

  map.addLayer({
    id: 'route-casing',
    type: 'line',
    source: 'route',
    filter: ['==', ['get', 'state'], 'ok'],
    layout: { 'line-join': 'round', 'line-cap': 'round' },
    paint: { 'line-color': '#ffffff', 'line-width': 9, 'line-opacity': 0.9 },
  })
  map.addLayer({
    id: 'route-line',
    type: 'line',
    source: 'route',
    filter: ['all', ['==', ['get', 'state'], 'ok'], ['!=', ['get', 'kind'], 'STRAIGHT']],
    layout: { 'line-join': 'round', 'line-cap': 'round' },
    paint: { 'line-color': lineColor(routeColor), 'line-width': 5 },
  })
  // Straight legs (no mapped way): dotted, so they read as "not a real path".
  map.addLayer({
    id: 'route-straight',
    type: 'line',
    source: 'route',
    filter: ['all', ['==', ['get', 'state'], 'ok'], ['==', ['get', 'kind'], 'STRAIGHT']],
    layout: { 'line-cap': 'round' },
    paint: { 'line-color': routeColor, 'line-width': 5, 'line-dasharray': [0.1, 1.8] },
  })
  map.addLayer({
    id: 'route-pending',
    type: 'line',
    source: 'route',
    filter: ['!=', ['get', 'state'], 'ok'],
    paint: {
      'line-color': ['case', ['==', ['get', 'state'], 'error'], '#B3261E', routeColor],
      'line-width': 4,
      'line-dasharray': [2, 2],
      'line-opacity': 0.8,
    },
  })
  // Wide invisible line that makes the route easy to grab.
  map.addLayer({
    id: 'route-hit',
    type: 'line',
    source: 'route',
    paint: { 'line-color': '#000000', 'line-width': 20, 'line-opacity': 0 },
  })
  map.addLayer({
    id: 'drag-preview',
    type: 'line',
    source: 'drag-preview',
    paint: { 'line-color': routeColor, 'line-width': 3, 'line-dasharray': [2, 2] },
  })
  map.addLayer({
    id: 'hover',
    type: 'circle',
    source: 'hover',
    paint: {
      'circle-radius': 7,
      'circle-color': routeColor,
      'circle-stroke-color': '#ffffff',
      'circle-stroke-width': 3,
    },
  })
}

export function setRouteColor(map: MapLibreMap, color: string) {
  map.setPaintProperty('route-line', 'line-color', lineColor(color))
  map.setPaintProperty('route-straight', 'line-color', color)
  map.setPaintProperty('drag-preview', 'line-color', color)
  map.setPaintProperty('hover', 'circle-color', color)
  map.setPaintProperty('route-pending', 'line-color', [
    'case',
    ['==', ['get', 'state'], 'error'],
    '#B3261E',
    color,
  ])
}
