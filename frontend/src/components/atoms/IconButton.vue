<script setup lang="ts">
/** A compact button whose content is an icon or a short word; `label` names it for everyone. */
defineProps<{ label: string; danger?: boolean }>()
defineEmits<{ click: [event: MouseEvent] }>()
</script>

<template>
  <button
    type="button"
    class="icon-button"
    :class="{ danger }"
    :title="label"
    :aria-label="label"
    @click="$emit('click', $event)"
  >
    <slot />
  </button>
</template>

<style scoped>
.icon-button {
  padding: 3px 9px;
  font-size: 13px;
  min-width: 28px;
}
.danger {
  border-color: var(--warn);
  color: var(--warn);
}
</style>
