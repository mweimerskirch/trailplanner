<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { TRAIL_COLORS } from '@/map/trails'
import type { Activity } from '@/types'

const { activity = 'MTB' } = defineProps<{ activity?: Activity }>()
const { t } = useI18n()

/** Key to the trail overlay's colours. */
const GRADES = [
  { label: 'S0', color: TRAIL_COLORS.s0 },
  { label: 'S1', color: TRAIL_COLORS.s1 },
  { label: 'S2', color: TRAIL_COLORS.s2 },
  { label: 'S3+', color: TRAIL_COLORS.s3 },
]
</script>

<template>
  <ul class="trail-legend" :aria-label="t('map.legend.label')">
    <li v-for="g in GRADES" :key="g.label" :title="t('map.legend.graded', { grade: g.label })">
      <span class="line" :style="{ background: g.color }" />{{ g.label }}
    </li>
    <li :title="t('map.legend.unratedHint')">
      <span class="line dashed" :style="{ color: TRAIL_COLORS.unrated }" />{{ t('map.legend.unrated') }}
    </li>
    <li v-if="activity === 'HORSE'" :title="t('map.legend.noAccessHint')">
      <span class="line dotted" :style="{ color: TRAIL_COLORS.forbidden }" />{{ t('map.legend.noAccess') }}
    </li>
    <li v-else :title="t('map.legend.noBikesHint')">
      <span class="line dotted" :style="{ color: TRAIL_COLORS.forbidden }" />{{ t('map.legend.noBikes') }}
    </li>
  </ul>
</template>

<style scoped>
.trail-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 12px;
  color: var(--muted);
}
li {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}
.line {
  display: inline-block;
  width: 16px;
  height: 4px;
  border-radius: 2px;
}
.dashed {
  background: repeating-linear-gradient(90deg, currentColor 0 6px, transparent 6px 9px);
}
.dotted {
  background: repeating-linear-gradient(90deg, currentColor 0 3px, transparent 3px 6px);
}
</style>
