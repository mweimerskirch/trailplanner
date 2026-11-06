<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api'
import ElevationChart from '@/components/organisms/ElevationChart.vue'
import ExportPanel from '@/components/organisms/ExportPanel.vue'
import MapView from '@/components/organisms/MapView.vue'
import StatusMessage from '@/components/molecules/StatusMessage.vue'
import PlannerLayout from '@/components/templates/PlannerLayout.vue'
import PlaceSearch from '@/components/molecules/PlaceSearch.vue'
import PreferencesPanel from '@/components/organisms/PreferencesPanel.vue'
import RouteStats from '@/components/organisms/RouteStats.vue'
import {
  hoverIndex,
  openRoute,
  profile,
  routeId,
  setStatus,
  status,
  viewOnly,
  waypoints,
} from '@/planner'

// ---------- URL <-> open route ----------
// The address shows the open route (/routes/<id>), or '/' for a new one, so it can be bookmarked,
// reloaded and used with the back button. The page stays alive while the library is open, so
// both directions only act while the planner is the current page.

const route = useRoute()
const router = useRouter()
const onPlanner = () => route.name === 'planner' || route.name === 'route'

async function openFromUrl(id: number) {
  if (routeId.value === id) return
  try {
    openRoute(await api.getRoute(id))
  } catch (error) {
    setStatus(error instanceof Error ? error.message : 'Could not open the route.', 'error')
    await router.replace('/')
  }
}

watch(
  () => [route.name, route.params.id],
  () => {
    if (route.name === 'route') void openFromUrl(Number(route.params.id))
    else if (route.name === 'planner' && routeId.value !== null)
      void router.replace(`/routes/${String(routeId.value)}`)
  },
  { immediate: true },
)

watch(routeId, (id) => {
  if (!onPlanner()) return
  const target = id === null ? '/' : `/routes/${String(id)}`
  if (route.path !== target) void router.replace(target)
})

const hint = computed(() => {
  if (viewOnly.value) return ''
  if (waypoints.value.length === 0) return 'Click the map to set your start point'
  if (waypoints.value.length === 1) return 'Now click where you want to go'
  return ''
})

/** The point hovered on the chart or map (shared with MapView through the planner). */
const hover = hoverIndex
</script>

<template>
  <PlannerLayout>
    <template #sidebar>
      <PlaceSearch />
      <PreferencesPanel />
      <RouteStats />
      <ExportPanel />
      <p class="credits">
        Map data © OpenStreetMap contributors. Routing by BRouter. Difficulty limits apply only to trails with
        an mtb:scale tag.
      </p>
    </template>
    <template #stage>
      <MapView />
      <StatusMessage :message="status" floating />
      <div v-if="hint" class="hint">{{ hint }}</div>
      <ElevationChart
        v-if="profile.length > 1"
        v-model:hover-index="hover"
        :points="profile"
        class="elevation"
      />
    </template>
  </PlannerLayout>
</template>

<style scoped>
.elevation {
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 28px;
  z-index: 2;
  box-shadow: var(--shadow);
}
@media (max-width: 820px) {
  .elevation {
    height: 100px;
  }
}
.credits {
  margin-top: 28px;
  color: var(--muted);
  font-size: 12.5px;
}
.hint {
  position: absolute;
  bottom: 40px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2;
  pointer-events: none;
  background: var(--panel);
  border-radius: 10px;
  padding: 10px 16px;
  box-shadow: var(--shadow);
}
</style>
