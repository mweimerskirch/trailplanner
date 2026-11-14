<script setup lang="ts">
import * as maplibregl from 'maplibre-gl'
import type { GeoJSONSource, LngLat, Map as MapLibreMap } from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?url'
import TrailLegend from '@/components/molecules/TrailLegend.vue'
import { onActivated, onBeforeUnmount, onMounted, ref, useTemplateRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { DEFAULT_CENTER, warningText } from '@/constants'
import {
  addRasterLayers,
  addRouteLayers,
  BASE_MAPS,
  type BaseMapId,
  setRouteColor,
  showBaseMap,
  showMtbOverlay,
  VECTOR_STYLE_URL,
} from '@/map/layers'
import { addTrailLayers, setTrailStyle, showTrails, type TrailStyle } from '@/map/trails'
import { attachWayInfo } from '@/map/wayInfo'
import { addPlaceLayers, showPlace } from '@/map/place'
import * as planner from '@/planner'
import type { GeoPoint, LegType } from '@/types'

// MapLibre 6 looks for its worker next to its own module (import.meta.url). Once Vite has bundled
// it, that is /assets/maplibre-gl-worker.mjs, which does not exist, and no tiles load. Let Vite emit
// the worker as an asset and tell MapLibre where it is.
maplibregl.setWorkerUrl(workerUrl)

const container = useTemplateRef<HTMLDivElement>('container')
const { t, locale } = useI18n()
const baseMap = ref<BaseMapId>((localStorage.getItem('trailplan.basemap') as BaseMapId | null) ?? 'cyclosm')
const mtbOverlay = ref(localStorage.getItem('trailplan.mtbOverlay') === 'true')
const trails = ref(localStorage.getItem('trailplan.trails') === 'true')

let map: MapLibreMap | null = null
let ready = false
let markers: maplibregl.Marker[] = []
let suppressNextClick = false
let detachWayInfo: (() => void) | null = null

const toPoint = (ll: LngLat): GeoPoint => ({ lat: ll.lat, lng: ll.lng })
const cssVar = (name: string) => getComputedStyle(document.documentElement).getPropertyValue(name).trim()
const darkQuery = window.matchMedia('(prefers-color-scheme: dark)')

function geoJsonSource(id: string): GeoJSONSource | undefined {
  return map?.getSource<GeoJSONSource>(id)
}

// ---------- route ----------

function renderRoute() {
  const source = geoJsonSource('route')
  if (!source) return
  const pts = planner.waypoints.value
  // displayLegs is the fixed track in view mode (imported routes), else the calculated legs.
  const features = planner.displayLegs.value.flatMap((state, i) => {
    let coordinates: number[][]
    if (state.status === 'ok') {
      coordinates = state.leg.points.map(([lng, lat]) => [lng, lat])
    } else {
      // Not calculated (yet): a straight dashed line between the two waypoints.
      const from = pts[i]
      const to = pts[i + 1]
      if (!from || !to) return []
      coordinates = [
        [from.lng, from.lat],
        [to.lng, to.lat],
      ]
    }
    return [
      {
        type: 'Feature' as const,
        // kind: how the leg is calculated (ROUTED, FORCED, STRAIGHT); imported tracks count as routed.
        properties: { leg: i, state: state.status, kind: pts[i]?.leg ?? 'ROUTED' },
        geometry: { type: 'LineString' as const, coordinates },
      },
    ]
  })
  void source.setData({ type: 'FeatureCollection', features })
}

function renderHover() {
  const source = geoJsonSource('hover')
  if (!source) return
  const index = planner.hoverIndex.value
  const point = index === null ? undefined : planner.profile.value[index]
  void source.setData({
    type: 'FeatureCollection',
    features: point
      ? [
          {
            type: 'Feature',
            properties: {},
            geometry: { type: 'Point', coordinates: [point.lng, point.lat] },
          },
        ]
      : [],
  })
}

function setDragPreview(lines: [number, number][][]) {
  void geoJsonSource('drag-preview')?.setData({
    type: 'FeatureCollection',
    features: lines.map((coordinates) => ({
      type: 'Feature',
      properties: {},
      geometry: { type: 'LineString', coordinates },
    })),
  })
}

// ---------- markers ----------

/** With many waypoints (e.g. a converted import) numbered markers would pile up: show dots. */
const COMPACT_MARKERS_FROM = 15

function markerElement(index: number, count: number): HTMLElement {
  const el = document.createElement('button')
  el.type = 'button'
  el.className = 'wp'
  if (index > 0 && index < count - 1 && count >= COMPACT_MARKERS_FROM) {
    el.classList.add('wp-small')
  } else if (index === 0) {
    el.classList.add('wp-start')
    el.textContent = 'A'
  } else if (index === count - 1) {
    el.classList.add('wp-end')
    el.textContent = 'B'
  } else {
    el.textContent = String(index)
  }
  el.setAttribute('aria-label', t('map.waypoint.aria', { n: index + 1 }))
  return el
}

const LEG_CHOICES: readonly LegType[] = ['ROUTED', 'FORCED', 'STRAIGHT']

/** Popup of a waypoint: how its next leg is calculated, and removing it. */
function waypointPopup(index: number): maplibregl.Popup {
  const pts = planner.waypoints.value
  const content = document.createElement('div')
  content.className = 'wp-popup'
  const title = document.createElement('strong')
  title.textContent = t('map.waypoint.title', { n: index + 1 })
  content.append(title)
  const popup = new maplibregl.Popup({ offset: 16, closeButton: true })
  if (index < pts.length - 1) {
    const label = document.createElement('label')
    label.textContent = t('map.waypoint.nextLeg')
    const select = document.createElement('select')
    for (const choice of LEG_CHOICES) {
      const option = document.createElement('option')
      option.value = choice
      option.textContent = t(`map.waypoint.leg.${choice}`)
      option.selected = (pts[index]?.leg ?? 'ROUTED') === choice
      select.append(option)
    }
    select.addEventListener('change', () => {
      popup.remove()
      planner.setLegType(index, select.value as LegType)
    })
    label.append(select)
    content.append(label)
  }
  const button = document.createElement('button')
  button.type = 'button'
  button.className = 'popup-button'
  button.textContent = t('map.waypoint.remove')
  button.addEventListener('click', () => {
    popup.remove()
    planner.removeWaypoint(index)
  })
  content.append(button)
  return popup.setDOMContent(content)
}

// ---------- warnings ----------

let warningMarkers: maplibregl.Marker[] = []

/** A marker at every closed or restricted way the route uses, explaining why. */
function renderWarnings() {
  const m = map
  if (!m) return
  for (const marker of warningMarkers) marker.remove()
  warningMarkers = planner.warnings.value.map((w) => {
    const el = document.createElement('button')
    el.type = 'button'
    el.className = 'route-warning'
    el.textContent = '!'
    el.setAttribute('aria-label', t('map.warning.aria', { reason: warningText(w) }))
    const text = document.createElement('div')
    text.className = 'warning-popup'
    const strong = document.createElement('strong')
    strong.textContent = warningText(w)
    const note = document.createElement('p')
    note.textContent = t('map.warning.note')
    text.append(strong, note)
    const popup = new maplibregl.Popup({ offset: 14 }).setDOMContent(text)
    // Pinned just above the route, so the waypoint that often sits at the same spot stays visible
    // and can still be dragged (waypoints also stack above warnings, see style.css).
    return new maplibregl.Marker({ element: el, anchor: 'bottom', offset: [0, -6] })
      .setLngLat([w.lng, w.lat])
      .setPopup(popup)
      .addTo(m)
  })
}

function renderMarkers() {
  if (!map) return
  for (const m of markers) m.remove()
  const pts = planner.waypoints.value
  markers = pts.map((p, index) => {
    const element = markerElement(index, pts.length)
    const marker = new maplibregl.Marker({ element, draggable: true }).setLngLat([p.lng, p.lat])
    let dragged = false

    marker.on('dragstart', () => {
      dragged = true
      planner.remember()
    })
    marker.on('drag', () => {
      const ll = marker.getLngLat()
      const lines: [number, number][][] = []
      const before = pts[index - 1]
      const after = pts[index + 1]
      if (before)
        lines.push([
          [before.lng, before.lat],
          [ll.lng, ll.lat],
        ])
      if (after)
        lines.push([
          [ll.lng, ll.lat],
          [after.lng, after.lat],
        ])
      setDragPreview(lines)
    })
    marker.on('dragend', () => {
      setDragPreview([])
      planner.moveWaypoint(index, toPoint(marker.getLngLat()))
    })

    element.addEventListener('pointerdown', () => {
      dragged = false
    })
    element.addEventListener('click', (event) => {
      event.stopPropagation()
      if (dragged || !map) return
      waypointPopup(index).setLngLat([p.lng, p.lat]).addTo(map)
    })
    element.addEventListener('contextmenu', (event) => {
      event.preventDefault()
      event.stopPropagation()
      planner.removeWaypoint(index)
    })

    return map ? marker.addTo(map) : marker
  })
}

// ---------- interaction ----------

function routeLegAt(point: maplibregl.PointLike): number | null {
  if (!map) return null
  const hit = map.queryRenderedFeatures(point, { layers: ['route-hit'] })[0]
  const leg: unknown = hit?.properties.leg
  return typeof leg === 'number' ? leg : null
}

/** Dragging the route line creates a new via point where it is dropped. */
function startRouteDrag(legIndex: number, start: LngLat, touch: boolean) {
  if (!map) return
  const m = map
  const ghost = document.createElement('div')
  ghost.className = 'wp-ghost'
  const ghostMarker = new maplibregl.Marker({ element: ghost }).setLngLat(start).addTo(m)
  let moved = false

  const onMove = (e: maplibregl.MapMouseEvent | maplibregl.MapTouchEvent) => {
    moved = true
    ghostMarker.setLngLat(e.lngLat)
    const from = planner.waypoints.value[legIndex]
    const to = planner.waypoints.value[legIndex + 1]
    if (from && to) {
      setDragPreview([
        [
          [from.lng, from.lat],
          [e.lngLat.lng, e.lngLat.lat],
          [to.lng, to.lat],
        ],
      ])
    }
  }
  const onEnd = () => {
    m.off(touch ? 'touchmove' : 'mousemove', onMove)
    ghostMarker.remove()
    setDragPreview([])
    if (moved) {
      // The browser may fire a click right after the drag; it must not add another point.
      suppressNextClick = true
      setTimeout(() => {
        suppressNextClick = false
      }, 0)
      planner.insertWaypoint(legIndex + 1, toPoint(ghostMarker.getLngLat()))
    }
  }
  m.on(touch ? 'touchmove' : 'mousemove', onMove)
  m.once(touch ? 'touchend' : 'mouseup', onEnd)
}

/**
 * MapLibre markers have no mouse listeners of their own: a marker drag starts from the map's
 * mousedown/touchstart event. So pressing a waypoint that lies on the route line also reaches the
 * map's route handlers, which would start a route drag as well and insert an extra via point on
 * release. Map-level handlers must ignore events that start on a marker.
 */
function startedOnMarker(e: { originalEvent: Event }): boolean {
  const target = e.originalEvent.target
  return target instanceof Element && target.closest('.maplibregl-marker') !== null
}

function setupInteraction(m: MapLibreMap) {
  m.on('click', (e) => {
    if (startedOnMarker(e)) return
    if (suppressNextClick) {
      suppressNextClick = false
      return
    }
    const leg = routeLegAt(e.point)
    if (leg === null) planner.addWaypoint(toPoint(e.lngLat))
    else planner.insertWaypoint(leg + 1, toPoint(e.lngLat))
  })
  m.on('mousedown', 'route-hit', (e) => {
    if (e.originalEvent.button !== 0 || startedOnMarker(e) || planner.viewOnly.value) return
    const leg = routeLegAt(e.point)
    if (leg === null) return
    e.preventDefault() // keeps the map from panning
    startRouteDrag(leg, e.lngLat, false)
  })
  m.on('touchstart', 'route-hit', (e) => {
    if (e.points.length !== 1 || startedOnMarker(e) || planner.viewOnly.value) return
    const leg = routeLegAt(e.point)
    if (leg === null) return
    e.preventDefault()
    startRouteDrag(leg, e.lngLat, true)
  })
  m.on('mouseenter', 'route-hit', () => {
    m.getCanvas().style.cursor = 'grab'
  })
  m.on('mouseleave', 'route-hit', () => {
    m.getCanvas().style.cursor = ''
  })
  m.on('moveend', () => {
    const c = m.getCenter()
    publishBounds(m)
    localStorage.setItem('trailplan.view', JSON.stringify({ lng: c.lng, lat: c.lat, zoom: m.getZoom() }))
  })
}

function publishBounds(m: MapLibreMap) {
  const b = m.getBounds()
  planner.mapBounds.value = { west: b.getWest(), south: b.getSouth(), east: b.getEast(), north: b.getNorth() }
}

/**
 * Zoom requests can arrive before the map exists or while the planner page is hidden (a route
 * opened from the library); they are remembered and carried out once the map can be sized.
 */
let fitHandled = 0
function fitIfRequested() {
  if (!map || !ready || planner.fitRequest.value === fitHandled || !container.value?.offsetParent) return
  fitHandled = planner.fitRequest.value
  fitToRoute()
}

function fitToRoute() {
  if (!map) return
  const pts = planner.profile.value.length > 1 ? planner.profile.value : planner.waypoints.value
  if (pts.length === 0) return
  const bounds = new maplibregl.LngLatBounds()
  for (const p of pts) bounds.extend([p.lng, p.lat])
  map.fitBounds(bounds, { padding: { top: 60, bottom: 180, left: 60, right: 60 }, maxZoom: 15 })
}

function savedView(): { center: [number, number]; zoom: number } {
  try {
    const raw = localStorage.getItem('trailplan.view')
    if (raw) {
      const v = JSON.parse(raw) as { lng: number; lat: number; zoom: number }
      return { center: [v.lng, v.lat], zoom: v.zoom }
    }
  } catch {
    // ignore corrupt value
  }
  return { center: DEFAULT_CENTER, zoom: 10 }
}

const onColorSchemeChange = () => {
  if (map && ready) setRouteColor(map, cssVar('--route'))
}

onMounted(() => {
  if (!container.value) return
  const view = savedView()
  const m = new maplibregl.Map({
    container: container.value,
    style: VECTOR_STYLE_URL,
    center: view.center,
    zoom: view.zoom,
    attributionControl: { compact: true },
  })
  map = m
  m.addControl(new maplibregl.NavigationControl(), 'top-right')
  m.addControl(
    new maplibregl.GeolocateControl({
      positionOptions: { enableHighAccuracy: true },
      trackUserLocation: false,
    }),
    'top-right',
  )
  m.addControl(new maplibregl.ScaleControl({ unit: 'metric' }), 'bottom-left')
  m.doubleClickZoom.disable() // double clicks would add two waypoints

  m.on('load', () => {
    addRasterLayers(m)
    addTrailLayers(m, trailStyle())
    addPlaceLayers(m)
    addRouteLayers(m, cssVar('--route'))
    showBaseMap(m, baseMap.value)
    showMtbOverlay(m, mtbOverlay.value)
    showTrails(m, trails.value)
    ready = true
    setupInteraction(m)
    publishBounds(m)
    showPlace(m, planner.placeHighlight.value)
    // Info on the way under the mouse while the trail overlay is on; the route has its own hover.
    detachWayInfo = attachWayInfo(m, {
      enabled: () => trails.value,
      blocked: (e) => m.queryRenderedFeatures(e.point, { layers: ['route-hit'] }).length > 0,
      activity: () => planner.preferences.value.activity,
    })
    renderRoute()
    renderMarkers()
    renderWarnings()
    fitIfRequested()
  })
  darkQuery.addEventListener('change', onColorSchemeChange)
})

// The planner page is kept alive while the library is open; its size may have changed meanwhile.
onActivated(() => {
  map?.resize()
  fitIfRequested()
})

onBeforeUnmount(() => {
  darkQuery.removeEventListener('change', onColorSchemeChange)
  detachWayInfo?.()
  map?.remove()
  map = null
})

// Markers and warning popups are plain DOM built with the texts of the moment: rebuild them.
watch(locale, () => {
  if (!ready) return
  renderMarkers()
  renderWarnings()
})
watch(planner.waypoints, () => {
  if (ready) renderMarkers()
})
watch(planner.displayLegs, () => {
  if (ready) {
    renderRoute()
    renderWarnings()
  }
})
watch(planner.hoverIndex, () => {
  if (ready) renderHover()
})
watch(planner.fitRequest, fitIfRequested)
watch(planner.flyTarget, (target) => {
  if (!target || !map) return
  const b = target.bounds
  if (b) {
    map.fitBounds(
      [
        [b.west, b.south],
        [b.east, b.north],
      ],
      { padding: 80, maxZoom: 15 },
    )
  } else {
    map.flyTo({ center: [target.lng, target.lat], zoom: 13 })
  }
})
watch(planner.placeHighlight, (geometry) => {
  if (map && ready) showPlace(map, geometry)
})
watch(baseMap, (id) => {
  localStorage.setItem('trailplan.basemap', id)
  if (map && ready) showBaseMap(map, id)
})
watch(mtbOverlay, (visible) => {
  localStorage.setItem('trailplan.mtbOverlay', String(visible))
  if (map && ready) showMtbOverlay(map, visible)
})
watch(trails, (visible) => {
  localStorage.setItem('trailplan.trails', String(visible))
  if (map && ready) showTrails(map, visible)
})
/** The overlay follows the activity (which access counts) and the hardest grade allowed. */
function trailStyle(): TrailStyle {
  const { activity, maxDifficulty } = planner.preferences.value
  return { activity, maxDifficulty }
}
watch(
  () => [planner.preferences.value.activity, planner.preferences.value.maxDifficulty],
  () => {
    if (map && ready) setTrailStyle(map, trailStyle())
  },
)
</script>

<template>
  <div class="map-wrap">
    <div ref="container" class="map" />
    <div class="map-layers">
      <label class="visually-hidden" for="basemap">{{ t('map.layers.baseMap') }}</label>
      <select id="basemap" v-model="baseMap">
        <option v-for="b in BASE_MAPS" :key="b.id" :value="b.id">{{ b.label }}</option>
      </select>
      <label class="check">
        <input v-model="mtbOverlay" type="checkbox" />
        {{ t('map.layers.mtbRoutes') }}
      </label>
      <label class="check" :title="t('map.layers.trailsHint')">
        <input v-model="trails" type="checkbox" />
        {{ t('map.layers.trails') }}
      </label>
      <TrailLegend v-if="trails" class="legend" :activity="planner.preferences.value.activity" />
    </div>
  </div>
</template>

<style scoped>
.map-wrap {
  position: absolute;
  inset: 0;
}
.map {
  position: absolute;
  inset: 0;
}
.map-layers {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 2;
  display: flex;
  flex-wrap: wrap;
  max-width: calc(100% - 70px);
  gap: 6px 10px;
  align-items: center;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 6px 10px;
  box-shadow: var(--shadow);
  font-size: 14px;
}
.map-layers select {
  width: auto;
  padding: 4px 6px;
}
.legend {
  flex-basis: 100%;
  /* wraps within the width of the controls instead of widening the panel */
  contain: inline-size;
}
.check {
  display: flex;
  gap: 6px;
  align-items: center;
  white-space: nowrap;
}
</style>
