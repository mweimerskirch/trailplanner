<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import { setStatus } from './planner'

onMounted(() => {
  // Result of a cloud login redirect (e.g. Wahoo's OAuth callback sends the browser to /?wahoo=…).
  const params = new URLSearchParams(window.location.search)
  const wahoo = params.get('wahoo')
  if (wahoo === 'connected') setStatus('Wahoo connected.', 'info', 3000)
  if (wahoo === 'failed') setStatus('Connecting to Wahoo failed. Please try again.', 'error')
  if (wahoo) history.replaceState(null, '', window.location.pathname)
})
</script>

<template>
  <!-- The planner stays alive while the library is open, so the map and routing state survive. -->
  <RouterView v-slot="{ Component }">
    <KeepAlive include="PlannerView">
      <component :is="Component" />
    </KeepAlive>
  </RouterView>
</template>
