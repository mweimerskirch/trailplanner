<script setup lang="ts">
import type { StatusMessage } from '@/planner'

/** The planner's status line: floating over the map, or inline at the top of a page. */
withDefaults(defineProps<{ message: StatusMessage | null; floating?: boolean }>(), { floating: false })
</script>

<template>
  <div v-if="message" class="status" :class="[message.kind, { floating }]" role="status" aria-live="polite">
    {{ message.text }}
  </div>
</template>

<style scoped>
.status {
  margin: 0 0 12px;
  padding: 8px 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--panel);
}
.floating {
  position: absolute;
  top: 56px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 3;
  max-width: min(560px, 86%);
  margin: 0;
  border-radius: 20px;
  padding: 6px 14px;
  font-size: 14px;
  text-align: center;
  box-shadow: var(--shadow);
}
.error {
  border-color: var(--warn);
  color: var(--warn);
}
</style>
