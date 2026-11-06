<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppLogo from '@/components/atoms/AppLogo.vue'
import RiderMenu from '@/components/molecules/RiderMenu.vue'
import { routeId } from '@/planner'
import { currentRider } from '@/session'

/** The app bar on every page: logo, main navigation and the rider menu. */
const route = useRoute()
/** "Planner" goes back to the open route, if any, not to an empty planner. */
const plannerLink = computed(() => (routeId.value === null ? '/' : `/routes/${String(routeId.value)}`))
const section = computed(() => {
  if (route.name === 'library' || route.name === 'riders') return route.name
  if (route.name === 'rides' || route.name === 'ride') return 'rides'
  return 'planner'
})
</script>

<template>
  <header class="app-header">
    <RouterLink :to="currentRider ? '/library' : '/riders'" class="brand" aria-label="Trailplan home">
      <AppLogo />
      <span class="name">Trailplan</span>
    </RouterLink>

    <template v-if="currentRider">
      <nav class="main" aria-label="Main">
        <RouterLink to="/library" :class="{ current: section === 'library' }">Library</RouterLink>
        <RouterLink :to="plannerLink" :class="{ current: section === 'planner' }">Planner</RouterLink>
        <RouterLink to="/rides" :class="{ current: section === 'rides' }">Rides</RouterLink>
      </nav>
      <RiderMenu :rider="currentRider" class="menu" />
    </template>
  </header>
</template>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  gap: 24px;
  height: 52px;
  padding: 0 16px;
  background: var(--panel);
  border-bottom: 1px solid var(--line);
  position: relative;
  z-index: 20;
}
.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  color: var(--ink);
  text-decoration: none;
}
.name {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 24px;
  line-height: 1;
}
.main {
  display: flex;
  gap: 2px;
  height: 100%;
}
.main a {
  display: flex;
  align-items: center;
  padding: 0 12px;
  color: var(--muted);
  text-decoration: none;
  font-weight: 500;
  border-bottom: 3px solid transparent;
  border-top: 3px solid transparent;
}
.main a:hover {
  color: var(--ink);
}
.main a.current {
  color: var(--ink);
  border-bottom-color: var(--moss);
}
.menu {
  margin-left: auto;
}
@media (max-width: 560px) {
  .app-header {
    gap: 10px;
    padding: 0 10px;
  }
  .name {
    display: none;
  }
  .main a {
    padding: 0 8px;
  }
}
</style>
