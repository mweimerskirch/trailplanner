<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '@/api'
import * as planner from '@/planner'
import type { RouteSummary } from '@/types'

/** The three most recently changed routes, for quick access; the full library is its own page. */
const recent = ref<RouteSummary[]>([])
const total = ref(0)

async function load() {
  try {
    const routes = await api.listRoutes()
    total.value = routes.length
    recent.value = routes.slice(0, 3)
  } catch {
    recent.value = []
  }
}

async function open(id: number) {
  try {
    planner.openRoute(await api.getRoute(id))
  } catch (error) {
    planner.setStatus(error instanceof Error ? error.message : 'Could not open the route.', 'error')
  }
}

onMounted(load)
watch(planner.libraryVersion, load)
</script>

<template>
  <section aria-labelledby="library-shortcut-heading">
    <h2 id="library-shortcut-heading">Library</h2>
    <ul v-if="recent.length">
      <li v-for="r in recent" :key="r.id" :class="{ current: r.id === planner.routeId.value }">
        <button type="button" class="name" :title="`Open ${r.name}`" @click="open(r.id)">{{ r.name }}</button>
        <span class="meta">{{ (r.distance / 1000).toFixed(1) }} km</span>
      </li>
    </ul>
    <p v-else class="empty">Saved and imported routes appear here.</p>
    <RouterLink class="button" to="/library">
      Open library<template v-if="total"> ({{ total }} routes)</template>
    </RouterLink>
  </section>
</template>

<style scoped>
ul {
  list-style: none;
  margin: 0 0 10px;
  padding: 0;
}
li {
  display: flex;
  gap: 8px;
  align-items: baseline;
  padding: 4px 0;
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
}
</style>
