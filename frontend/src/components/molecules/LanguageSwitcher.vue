<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { LOCALES, setLocale, type Locale } from '@/i18n'

/** Choice of the interface language (English, French, German), remembered per browser. */
const { t, locale } = useI18n()

const value = computed({
  get: () => locale.value as Locale,
  set: (l: Locale) => {
    setLocale(l)
  },
})
</script>

<template>
  <label class="language">
    <span class="visually-hidden">{{ t('common.language') }}</span>
    <select v-model="value" :title="t('common.language')">
      <!-- Short codes keep the header narrow. -->
      <option v-for="l in LOCALES" :key="l.code" :value="l.code" :lang="l.code" :title="l.name">
        {{ l.code.toUpperCase() }}
      </option>
    </select>
  </label>
</template>

<style scoped>
.language {
  display: flex;
  align-items: center;
}
select {
  appearance: none;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.04em;
  padding: 4px 8px;
  cursor: pointer;
}
select:hover,
select:focus-visible {
  color: var(--ink);
  border-color: var(--muted);
}
</style>
