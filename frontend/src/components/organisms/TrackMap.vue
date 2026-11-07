<script setup lang="ts">
import * as maplibregl from 'maplibre-gl'
import type { GeoJSONSource, Map as MapLibreMap } from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?url'
import { onBeforeUnmount, onMounted, useTemplateRef, watch } from 'vue'
import { addRasterLayers, type BaseMapId, showBaseMap, showMtbOverlay, VECTOR_STYLE_URL } from '@/map/layers'
import { addTrailLayers, showTrails } from '@/map/trails'
import { attachWayInfo } from '@/map/wayInfo'

/**
 * A map that shows one fixed track (e.g. a recorded ride) with its start and end, on the base map
 * chosen in the planner (with the trail overlay if it is on there). Nothing on it can be edited.
 */
const props = defineProps<{
  track: [number, number, number | null][]
  /** A point to mark, e.g. the one pointed at on the elevation chart. */
  hoverPoint?: { lng: number; lat: number } | null
}>()

maplibregl.setWorkerUrl(workerUrl)

const container = useTemplateRef<HTMLDivElement>('container')
let map: MapLibreMap | null = null
let markers: maplibregl.Marker[] = []

const cssVar = (name: string) => getComputedStyle(document.documentElement).getPropertyValue(name).trim()

function marker(className: string, text: string, at: [number, number]): maplibregl.Marker {
  const el = document.createElement('div')
  el.className = `wp ${className}`
  el.textContent = text
  return new maplibregl.Marker({ element: el }).setLngLat(at)
}

function renderHover() {
  const p = props.hoverPoint
  void map?.getSource<GeoJSONSource>('hover')?.setData({
    type: 'FeatureCollection',
    features: p
      ? [{ type: 'Feature', properties: {}, geometry: { type: 'Point', coordinates: [p.lng, p.lat] } }]
      : [],
  })
}

function render() {
  const m = map
  if (!m) return
  const coordinates = props.track.map(([lng, lat]) => [lng, lat])
  void m.getSource<GeoJSONSource>('track')?.setData({
    type: 'Feature',
    properties: {},
    geometry: { type: 'LineString', coordinates },
  })
  for (const old of markers) old.remove()
  markers = []
  const first = props.track[0]
  const last = props.track[props.track.length - 1]
  if (!first || !last) return
  markers = [marker('wp-start', 'A', [first[0], first[1]]), marker('wp-end', 'B', [last[0], last[1]])]
  for (const mk of markers) mk.addTo(m)
  const bounds = new maplibregl.LngLatBounds()
  for (const [lng, lat] of props.track) bounds.extend([lng, lat])
  m.fitBounds(bounds, { padding: 40, duration: 0 })
}

onMounted(() => {
  if (!container.value) return
  const m = new maplibregl.Map({
    container: container.value,
    style: VECTOR_STYLE_URL,
    center: [6.13, 49.61],
    zoom: 10,
    attributionControl: { compact: true },
  })
  map = m
  m.addControl(new maplibregl.NavigationControl(), 'top-right')
  m.addControl(new maplibregl.ScaleControl({ unit: 'metric' }), 'bottom-left')
  m.on('load', () => {
    addRasterLayers(m)
    showBaseMap(m, (localStorage.getItem('trailplan.basemap') as BaseMapId | null) ?? 'cyclosm')
    showMtbOverlay(m, localStorage.getItem('trailplan.mtbOverlay') === 'true')
    addTrailLayers(m)
    const trails = localStorage.getItem('trailplan.trails') === 'true'
    showTrails(m, trails)
    if (trails) attachWayInfo(m, { enabled: () => true })
    m.addSource('track', { type: 'geojson', data: { type: 'FeatureCollection', features: [] } })
    m.addLayer({
      id: 'track-casing',
      type: 'line',
      source: 'track',
      layout: { 'line-join': 'round', 'line-cap': 'round' },
      paint: { 'line-color': '#ffffff', 'line-width': 8, 'line-opacity': 0.9 },
    })
    m.addLayer({
      id: 'track-line',
      type: 'line',
      source: 'track',
      layout: { 'line-join': 'round', 'line-cap': 'round' },
      paint: { 'line-color': cssVar('--route'), 'line-width': 4.5 },
    })
    m.addSource('hover', { type: 'geojson', data: { type: 'FeatureCollection', features: [] } })
    m.addLayer({
      id: 'hover',
      type: 'circle',
      source: 'hover',
      paint: {
        'circle-radius': 7,
        'circle-color': cssVar('--route'),
        'circle-stroke-color': '#ffffff',
        'circle-stroke-width': 3,
      },
    })
    render()
  })
})

watch(() => props.track, render)
watch(() => props.hoverPoint, renderHover)

onBeforeUnmount(() => {
  map?.remove()
  map = null
})
</script>

<template>
  <div ref="container" class="track-map" />
</template>

<style scoped>
.track-map {
  width: 100%;
  height: 100%;
}
</style>
