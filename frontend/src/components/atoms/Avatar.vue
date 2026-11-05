<script setup lang="ts">
/** A rider's initial in a circle. Decorative: the name is always shown or announced next to it. */
const props = withDefaults(defineProps<{ name: string; size?: 'sm' | 'md' | 'lg'; muted?: boolean }>(), {
  size: 'md',
  muted: false,
})
const initial = () => props.name.charAt(0).toLocaleUpperCase()
</script>

<template>
  <span class="avatar" :class="[size, { muted }]" aria-hidden="true">{{ initial() }}</span>
</template>

<style scoped>
.avatar {
  flex: none;
  border-radius: 50%;
  display: inline-grid;
  place-items: center;
  background: var(--moss);
  color: var(--moss-ink);
  font-family: var(--font-display);
  font-weight: 700;
}
.sm {
  width: 22px;
  height: 22px;
  font-size: 12px;
}
.md {
  width: 28px;
  height: 28px;
  font-size: 15px;
}
.lg {
  width: 38px;
  height: 38px;
  font-size: 20px;
}
.muted {
  background: var(--line);
  color: var(--ink);
}
</style>
