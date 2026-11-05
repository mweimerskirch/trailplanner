<script setup lang="ts">
import { computed } from 'vue'
import AppNav from '@/components/AppNav.vue'
import ElevationChart from '@/components/ElevationChart.vue'
import ExportPanel from '@/components/ExportPanel.vue'
import LibraryShortcut from '@/components/LibraryShortcut.vue'
import MapView from '@/components/MapView.vue'
import PlaceSearch from '@/components/PlaceSearch.vue'
import PreferencesPanel from '@/components/PreferencesPanel.vue'
import RouteStats from '@/components/RouteStats.vue'
import { profile, status, viewOnly, waypoints } from '@/planner'

const hint = computed(() => {
  if (viewOnly.value) return ''
  if (waypoints.value.length === 0) return 'Click the map to set your start point'
  if (waypoints.value.length === 1) return 'Now click where you want to go'
  return ''
})
</script>

<template>
  <div class="planner">
    <aside class="panel">
      <AppNav />
      <PlaceSearch />
      <RouteStats />
      <ExportPanel />
      <LibraryShortcut />
      <PreferencesPanel />
      <p class="credits">
        Map data © OpenStreetMap contributors. Routing by BRouter. Difficulty limits apply only to trails with
        an mtb:scale tag.
      </p>
    </aside>
    <main class="stage">
      <MapView />
      <div v-if="status" class="status" :class="status.kind" role="status" aria-live="polite">
        {{ status.text }}
      </div>
      <div v-if="hint" class="hint">{{ hint }}</div>
      <ElevationChart v-if="profile.length > 1" />
    </main>
  </div>
</template>

<style scoped>
.planner {
  height: 100%;
  display: grid;
  grid-template-columns: 370px 1fr;
}
.panel {
  background: var(--panel);
  border-right: 1px solid var(--line);
  overflow-y: auto;
  padding: 20px 20px 40px;
}
.panel :deep(.app-nav) {
  margin-bottom: 16px;
}
.credits {
  margin-top: 28px;
  color: var(--muted);
  font-size: 12.5px;
}
.stage {
  position: relative;
  min-height: 0;
}
.status {
  position: absolute;
  top: 56px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 3;
  max-width: min(560px, 86%);
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 20px;
  padding: 6px 14px;
  font-size: 14px;
  text-align: center;
  box-shadow: var(--shadow);
}
.status.error {
  border-color: var(--warn);
  color: var(--warn);
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
@media (max-width: 820px) {
  .planner {
    height: auto;
    grid-template-columns: 1fr;
  }
  .stage {
    height: 62vh;
    order: -1;
    position: sticky;
    top: 0;
    z-index: 5;
  }
  .panel {
    border-right: none;
    border-top: 1px solid var(--line);
  }
}
</style>
