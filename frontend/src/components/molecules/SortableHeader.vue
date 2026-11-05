<script setup lang="ts">
/**
 * A table column header that sorts by its column: click to sort, click again to reverse. Exposes
 * the current order to screen readers with aria-sort.
 */
const props = withDefaults(
  defineProps<{
    label: string
    column: string
    sortKey: string
    sortDir: 'asc' | 'desc'
    numeric?: boolean
  }>(),
  { numeric: false },
)
defineEmits<{ sort: [column: string] }>()

const active = () => props.sortKey === props.column
const ariaSort = () => (active() ? (props.sortDir === 'asc' ? 'ascending' : 'descending') : 'none')
</script>

<template>
  <th scope="col" :aria-sort="ariaSort()" :class="{ num: numeric }">
    <button type="button" class="sort" :class="{ active: active() }" @click="$emit('sort', column)">
      {{ label
      }}<span class="arrow" aria-hidden="true">{{ active() ? (sortDir === 'asc' ? '▲' : '▼') : '' }}</span>
    </button>
  </th>
</template>

<style scoped>
.sort {
  border: none;
  background: none;
  padding: 0;
  font: inherit;
  font-weight: 600;
  color: inherit;
  cursor: pointer;
  white-space: nowrap;
}
.sort:hover,
.sort.active {
  color: var(--ink);
}
.arrow {
  display: inline-block;
  width: 1em;
  margin-left: 3px;
  font-size: 10px;
}
.num {
  text-align: right;
}
</style>
