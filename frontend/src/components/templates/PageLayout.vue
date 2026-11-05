<script setup lang="ts">
/** A scrolling page with content at a comfortable reading width, e.g. the library. */
</script>

<template>
  <div class="page-layout">
    <main class="inner"><slot /></main>
  </div>
</template>

<style scoped>
.page-layout {
  height: 100%;
  overflow-y: auto;
  background: var(--bg);
}
.inner {
  max-width: 1100px;
  margin: 0 auto;
  padding: 20px 24px 60px;
}
@media (max-width: 720px) {
  .inner {
    padding: 16px 12px 40px;
  }
}
</style>
