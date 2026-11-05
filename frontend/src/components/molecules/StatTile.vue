<script setup lang="ts">
/** A big number with a unit or label below it. */
defineProps<{ value: string | number; label: string }>()
</script>

<template>
  <div class="stat">
    <b>{{ value }}</b
    ><span>{{ label }}</span>
  </div>
</template>

<style scoped>
b {
  display: block;
  font-family: var(--font-display);
  font-size: 32px;
  font-weight: 600;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
span {
  color: var(--muted);
  font-size: 13px;
}
</style>
