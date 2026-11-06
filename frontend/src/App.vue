<script setup lang="ts">
import { RouterView } from 'vue-router'
import ConfirmDialog from './components/molecules/ConfirmDialog.vue'
import AppHeader from './components/organisms/AppHeader.vue'
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
