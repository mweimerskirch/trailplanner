<script setup lang="ts">
/**
 * Sidebar on the left, map stage on the right. On phones the map comes first and stays at the top
 * while the sidebar scrolls below it.
 */
</script>

<template>
  <div class="planner-layout">
    <aside class="panel"><slot name="sidebar" /></aside>
    <main class="stage"><slot name="stage" /></main>
  </div>
</template>

<style scoped>
.planner-layout {
  height: 100%;
  display: grid;
  grid-template-columns: 370px 1fr;
}
.panel {
  background: var(--panel);
  border-right: 1px solid var(--line);
  overflow-y: auto;
  min-height: 0;
  padding: 16px 20px 40px;
}
.stage {
  position: relative;
  min-height: 0;
}
@media (max-width: 820px) {
  .planner-layout {
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
