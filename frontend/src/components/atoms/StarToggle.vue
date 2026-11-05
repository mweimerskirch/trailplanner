<script setup lang="ts">
/** Star / unstar (favorite). `label` names the thing being starred for screen readers. */
const props = defineProps<{ starred: boolean; label: string }>()
defineEmits<{ toggle: [] }>()
const title = () =>
  props.starred ? `Remove ${props.label} from favorites` : `Add ${props.label} to favorites`
</script>

<template>
  <button
    type="button"
    class="star"
    :class="{ starred }"
    :aria-pressed="starred"
    :aria-label="title()"
    :title="title()"
    @click="$emit('toggle')"
  >
    {{ starred ? '★' : '☆' }}
  </button>
</template>

<style scoped>
.star {
  border: none;
  background: none;
  padding: 0 4px;
  font-size: 19px;
  line-height: 1;
  color: var(--muted);
  cursor: pointer;
}
.star:hover {
  color: #d9a400;
}
.starred {
  color: #e0a800;
}
</style>
