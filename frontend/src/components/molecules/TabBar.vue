<script setup lang="ts">
import Badge from '@/components/atoms/Badge.vue'

export interface Tab {
  id: string
  label: string
  /** Shown as a count badge when set. */
  count?: number
}

/** A row of tabs with an underline on the active one. `large` for page sections. */
withDefaults(defineProps<{ tabs: Tab[]; modelValue: string; large?: boolean }>(), { large: false })
defineEmits<{ 'update:modelValue': [id: string] }>()
</script>

<template>
  <div class="tabs" :class="{ large }" role="tablist">
    <button
      v-for="tab in tabs"
      :key="tab.id"
      type="button"
      role="tab"
      :aria-selected="modelValue === tab.id"
      :class="{ active: modelValue === tab.id }"
      @click="$emit('update:modelValue', tab.id)"
    >
      {{ tab.label
      }}<Badge v-if="tab.count !== undefined" variant="count" class="count">{{ tab.count }}</Badge>
    </button>
  </div>
</template>

<style scoped>
.tabs {
  display: flex;
  gap: 4px;
}
.tabs button {
  border: none;
  border-bottom: 3px solid transparent;
  border-radius: 0;
  background: none;
  padding: 6px 10px;
}
.tabs button.active {
  border-bottom-color: var(--moss);
  font-weight: 600;
}
.large button {
  padding: 8px 12px;
  font-family: var(--font-display);
  font-size: 19px;
  font-weight: 600;
}
.count {
  margin-left: 6px;
}
</style>
