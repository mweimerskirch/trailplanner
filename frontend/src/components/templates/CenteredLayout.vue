<script setup lang="ts">
/** A single card in the middle of the page, e.g. the rider selection. */
</script>

<template>
  <main class="centered-layout">
    <div class="card"><slot /></div>
  </main>
</template>

<style scoped>
.centered-layout {
  min-height: 100%;
  display: grid;
  place-items: center;
  padding: 24px 12px;
  background: var(--bg);
}
.card {
  width: min(460px, 100%);
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 14px;
  box-shadow: var(--shadow);
  padding: 26px 26px 20px;
}
</style>
