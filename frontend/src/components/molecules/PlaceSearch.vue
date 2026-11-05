<script setup lang="ts">
import { ref } from 'vue'
import { searchPlaces, type Place } from '@/api'
import { flyTarget } from '@/planner'

const query = ref('')
const results = ref<Place[]>([])
const message = ref('')

async function search() {
  const q = query.value.trim()
  if (!q) return
  message.value = 'Searching…'
  results.value = []
  try {
    results.value = await searchPlaces(q)
    message.value = results.value.length ? '' : 'No places found. Try a town or landmark.'
  } catch (error) {
    message.value = error instanceof Error ? error.message : 'Search failed.'
  }
}

function choose(place: Place) {
  flyTarget.value = { lat: place.lat, lng: place.lng }
  results.value = []
}
</script>

<template>
  <form class="row" role="search" @submit.prevent="search">
    <input
      v-model="query"
      type="search"
      placeholder="Search a place"
      aria-label="Search a place"
      class="grow"
    />
    <button type="submit">Find</button>
  </form>
  <p v-if="message" class="empty">{{ message }}</p>
  <ul v-if="results.length" class="results">
    <li v-for="r in results" :key="`${r.lat},${r.lng}`">
      <button type="button" @click="choose(r)">{{ r.name }}</button>
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
