<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { api } from '@/api'
import * as planner from '@/planner'
import type { RouteSummary } from '@/types'

const routes = ref<RouteSummary[]>([])
const loading = ref(true)
const failed = ref(false)

async function load() {
  try {
    routes.value = await api.listRoutes()
    failed.value = false
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

async function open(id: number) {
  try {
    planner.openRoute(await api.getRoute(id))
  } catch (error) {
    planner.setStatus(error instanceof Error ? error.message : 'Could not open the route.', 'error')
  }
}

async function remove(route: RouteSummary) {
  if (!confirm(`Delete “${route.name}”?`)) return
  try {
    await api.deleteRoute(route.id)
    if (planner.routeId.value === route.id) planner.routeId.value = null
    await load()
  } catch (error) {
    planner.setStatus(error instanceof Error ? error.message : 'Could not delete the route.', 'error')
  }
}

onMounted(load)
watch(planner.libraryVersion, load)
</script>

<template>
  <section aria-labelledby="library-heading">
    <h2 id="library-heading">Library</h2>
    <p v-if="loading" class="empty">Loading…</p>
    <p v-else-if="failed" class="empty">The library is unavailable. Is the backend running?</p>
    <p v-else-if="routes.length === 0" class="empty">Saved routes appear here.</p>
    <ul v-else>
      <li v-for="r in routes" :key="r.id" :class="{ current: r.id === planner.routeId.value }">
        <button type="button" class="name" :title="`Open ${r.name}`" @click="open(r.id)">{{ r.name }}</button>
        <span class="meta">{{ (r.distance / 1000).toFixed(1) }} km · {{ Math.round(r.ascent) }} m</span>
        <button type="button" class="delete" :aria-label="`Delete ${r.name}`" @click="remove(r)">✕</button>
      </li>
    </ul>
  </section>
</template>

<style scoped>
ul {
  list-style: none;
  margin: 0;
  padding: 0;
}
li {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 5px 0;
  border-bottom: 1px solid var(--line);
  font-size: 14px;
}
li.current .name {
  font-weight: 600;
}
.name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: left;
  background: none;
  border: none;
  padding: 2px;
}
.meta {
  color: var(--muted);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.delete {
  padding: 2px 8px;
}
</style>
