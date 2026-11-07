<script setup lang="ts">
import { ref } from 'vue'
import { searchPlaces, type Place } from '@/api'
import { flyTarget, mapBounds, placeHighlight } from '@/planner'
import { searchArea } from '@/search'
import type { Geometry } from 'geojson'

/**
 * Searches around the visible map (the view plus half its size on every side, at least 30 km
 * across). If nothing is found there, a wider area (at least 300 km across); never the whole
 * world, where a short name like "PC6" matches places on other continents.
 */
const NEAR = { margin: 0.5, minKm: 30 }
const WIDER = { margin: 2, minKm: 300 }

const query = ref('')
const results = ref<Place[]>([])
const message = ref('')

async function search() {
  const q = query.value.trim()
  if (!q) return
  message.value = 'Searching…'
  results.value = []
  chosen = null
  placeHighlight.value = null
  const view = mapBounds.value
  try {
    if (!view) {
      results.value = await searchPlaces(q)
    } else {
      results.value = await searchPlaces(q, searchArea(view, NEAR.margin, NEAR.minKm))
      if (!results.value.length) {
        message.value = 'Nothing near the map; searching a wider area…'
        results.value = await searchPlaces(q, searchArea(view, WIDER.margin, WIDER.minKm))
      }
    }
    message.value = results.value.length
      ? ''
      : view
        ? 'No places found around the map. Move or zoom out the map to search elsewhere.'
        : 'No places found. Try a town or landmark.'
  } catch (error) {
    message.value = error instanceof Error ? error.message : 'Search failed.'
  }
}

const shape = (place: Place): Geometry =>
  place.geometry ?? { type: 'Point', coordinates: [place.lng, place.lat] }

/** The result picked last: it stays highlighted until the next search. */
let chosen: Place | null = null

/** Shows the result on the map while it is hovered or focused. */
function preview(place: Place | null) {
  const shown = place ?? chosen
  placeHighlight.value = shown ? shape(shown) : null
}

function clearIfEmpty() {
  if (query.value.trim()) return
  chosen = null
  placeHighlight.value = null
}

function choose(place: Place) {
  chosen = place
  preview(place)
  flyTarget.value = { lat: place.lat, lng: place.lng, bounds: place.bounds }
  results.value = []
}
</script>

<template>
  <form class="row" role="search" @submit.prevent="search">
    <input
      v-model="query"
      type="search"
      placeholder="Search a place"
      title="Searches around the visible part of the map"
      aria-label="Search a place"
      class="grow"
      @input="clearIfEmpty"
    />
    <button type="submit">Find</button>
  </form>
  <p v-if="message" class="empty">{{ message }}</p>
  <ul v-if="results.length" class="results">
    <li v-for="r in results" :key="`${r.lat},${r.lng}`">
      <button
        type="button"
        @click="choose(r)"
        @mouseenter="preview(r)"
        @mouseleave="preview(null)"
        @focus="preview(r)"
        @blur="preview(null)"
      >
        {{ r.name }}
      </button>
    </li>
  </ul>
</template>

<style scoped>
.grow {
  flex: 1;
}
.results {
  list-style: none;
  margin: 6px 0 0;
  padding: 0;
}
.results button {
  width: 100%;
  text-align: left;
  border: none;
  border-bottom: 1px solid var(--line);
  border-radius: 0;
  background: none;
  padding: 7px 2px;
  font-size: 14px;
}
</style>
