<script setup lang="ts">
/** The Trail Planner mark (same as the favicon): hills with a route line. */
withDefaults(defineProps<{ size?: number }>(), { size: 30 })
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 32 32" aria-hidden="true">
    <rect width="32" height="32" rx="7" fill="#2F5E3B" />
    <path d="M5 24 L12 12 L17 19 L21 14 L27 24 Z" fill="#E9EDE7" />
    <path
      d="M6 26 C 12 21, 18 27, 26 20"
      stroke="#C2186B"
      stroke-width="2.5"
      fill="none"
      stroke-linecap="round"
    />
  </svg>
</template>
