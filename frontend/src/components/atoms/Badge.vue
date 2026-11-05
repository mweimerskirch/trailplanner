<script setup lang="ts">
/**
 * A small label: `count` for numbers next to tabs, `neutral` for notes like "original track",
 * `warning` for the round "!" of closed or restricted ways.
 */
withDefaults(defineProps<{ variant?: 'count' | 'neutral' | 'warning' }>(), { variant: 'neutral' })
</script>

<template>
  <span class="badge" :class="variant"><slot /></span>
</template>

<style scoped>
.badge {
  display: inline-block;
  white-space: nowrap;
}
.count {
  padding: 0 7px;
  border-radius: 10px;
  background: var(--line);
  font-family: var(--font-body);
  font-size: 12.5px;
  font-weight: 500;
  vertical-align: middle;
}
.neutral {
  padding: 0 6px;
  border-radius: 4px;
  background: var(--line);
  color: var(--muted);
  font-size: 11.5px;
  line-height: 18px;
}
.warning {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: inline-grid;
  place-items: center;
  background: #e07b00;
  color: #ffffff;
  font-weight: 800;
  font-size: 13px;
  flex: none;
}
</style>
