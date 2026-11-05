<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import ConfirmDialog from './components/molecules/ConfirmDialog.vue'
import AppHeader from './components/organisms/AppHeader.vue'
import { setStatus } from './planner'

onMounted(() => {
  // Result of a cloud login redirect (e.g. Wahoo's OAuth callback sends the browser to /?wahoo=…).
  const params = new URLSearchParams(window.location.search)
  const wahoo = params.get('wahoo')
  if (wahoo === 'connected') setStatus('Wahoo connected.', 'info', 3000)
  if (wahoo === 'failed') setStatus('Connecting to Wahoo failed. Please try again.', 'error')
  if (wahoo === 'disabled') setStatus('Wahoo sync is not set up on this server.', 'error')
  if (wahoo) history.replaceState(null, '', window.location.pathname)
})
</script>

<template>
  <div class="shell">
    <AppHeader />
    <div class="page">
      <!-- The planner stays alive while the library is open, so the map and routing state survive. -->
      <RouterView v-slot="{ Component }">
        <KeepAlive include="PlannerPage">
          <component :is="Component" />
        </KeepAlive>
      </RouterView>
    </div>
    <ConfirmDialog />
  </div>
</template>

<style scoped>
.shell {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.page {
  flex: 1;
  min-height: 0;
}
@media (max-width: 820px) {
  .shell {
    height: auto;
  }
}
</style>
