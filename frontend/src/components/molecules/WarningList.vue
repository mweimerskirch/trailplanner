<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Badge from '@/components/atoms/Badge.vue'
import { warningText } from '@/constants'
import type { RouteWarning } from '@/types'

/** The closed or restricted ways a route is forced through, each with a button to show it. */
defineProps<{ warnings: RouteWarning[] }>()
defineEmits<{ show: [warning: RouteWarning] }>()

const { t } = useI18n()
</script>

<template>
  <div v-if="warnings.length" class="warnings" role="note">
    <p class="title">
      <Badge variant="warning" aria-hidden="true">!</Badge>
      {{ t('planner.warnings.title', { n: warnings.length }, warnings.length) }}
    </p>
    <ul>
      <li v-for="(w, i) in warnings" :key="i">
        <span>{{ warningText(w) }}</span>
        <button type="button" class="link" @click="$emit('show', w)">{{ t('planner.warnings.show') }}</button>
      </li>
    </ul>
    <p class="hint">{{ t('planner.warnings.hint') }}</p>
  </div>
</template>

<style scoped>
.warnings {
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid #e07b00;
  border-radius: 8px;
  background: color-mix(in srgb, #e07b00 8%, var(--field));
}
.title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 6px;
  font-weight: 600;
}
ul {
  list-style: none;
  margin: 0;
  padding: 0;
  font-size: 13.5px;
}
li {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding: 2px 0;
}
.hint {
  margin: 6px 0 0;
  font-size: 12.5px;
  color: var(--muted);
}
</style>
