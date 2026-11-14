<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, useTemplateRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import Avatar from '@/components/atoms/Avatar.vue'
import type { Rider } from '@/types'

/** The rider's avatar and name, opening a menu with their profile and to switch or manage riders. */
defineProps<{ rider: Rider }>()

const route = useRoute()
const { t } = useI18n()
const open = ref(false)
const root = useTemplateRef<HTMLElement>('root')

// Close on navigation, outside clicks and Escape.
watch(
  () => route.fullPath,
  () => {
    open.value = false
  },
)
function onDocumentClick(event: MouseEvent) {
  if (open.value && !root.value?.contains(event.target as Node)) open.value = false
}
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') open.value = false
}
onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div ref="root" class="rider-menu">
    <button type="button" class="rider" aria-haspopup="menu" :aria-expanded="open" @click="open = !open">
      <Avatar :name="rider.name" />
      <span class="rider-name">{{ rider.name }}</span>
      <span class="caret" aria-hidden="true">▾</span>
    </button>
    <div v-if="open" class="dropdown" role="menu">
      <p class="signed-in">
        <i18n-t keypath="riders.menu.planningAs" scope="global">
          <template #name>
            <b>{{ rider.name }}</b>
          </template>
        </i18n-t>
      </p>
      <RouterLink to="/profile" role="menuitem">{{ t('riders.menu.profile') }}</RouterLink>
      <RouterLink to="/riders" role="menuitem">{{ t('riders.menu.switch') }}</RouterLink>
      <RouterLink :to="{ path: '/riders', query: { manage: '1' } }" role="menuitem">{{
        t('riders.manage')
      }}</RouterLink>
    </div>
  </div>
</template>

<style scoped>
.rider-menu {
  position: relative;
}
.rider {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px 4px 4px;
  border-radius: 20px;
  background: none;
}
.rider-name {
  font-weight: 500;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.caret {
  color: var(--muted);
  font-size: 12px;
}
.dropdown {
  position: absolute;
  right: 0;
  top: calc(100% + 6px);
  min-width: 200px;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 10px;
  box-shadow: var(--shadow);
  padding: 6px;
  display: flex;
  flex-direction: column;
}
.signed-in {
  margin: 4px 8px 6px;
  font-size: 13px;
  color: var(--muted);
}
.dropdown a {
  padding: 7px 8px;
  border-radius: 6px;
  color: var(--ink);
  text-decoration: none;
}
.dropdown a:hover {
  background: var(--line);
}
@media (max-width: 560px) {
  .rider-name {
    display: none;
  }
}
</style>
