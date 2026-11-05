<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, useTemplateRef, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { currentRider } from '@/session'

const route = useRoute()
const menuOpen = ref(false)
const menu = useTemplateRef<HTMLElement>('menu')

// Close the rider menu on navigation, outside clicks and Escape.
watch(
  () => route.fullPath,
  () => {
    menuOpen.value = false
  },
)
function onDocumentClick(event: MouseEvent) {
  if (menuOpen.value && !menu.value?.contains(event.target as Node)) menuOpen.value = false
}
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') menuOpen.value = false
}
onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onKeydown)
})

const initial = (name: string) => name.charAt(0).toLocaleUpperCase()
</script>

<template>
  <header class="app-header">
    <RouterLink :to="currentRider ? '/library' : '/riders'" class="brand" aria-label="Trailplan home">
      <svg class="logo" viewBox="0 0 32 32" aria-hidden="true">
        <rect width="32" height="32" rx="7" fill="#2F5E3B" />
        <path d="M5 24 L12 12 L17 19 L21 14 L27 24 Z" fill="#E9EDE7" />
        <path
          d="M6 26 C 12 21, 18 27, 26 20"
          stroke="#C2186B"
          stroke-width="2.5"
          fill="none"
          stroke-linecap="round"
        />
      </svg>
      <span class="name">Trailplan</span>
    </RouterLink>

    <template v-if="currentRider">
      <nav class="main" aria-label="Main">
        <RouterLink to="/">Planner</RouterLink>
        <RouterLink to="/library">Library</RouterLink>
      </nav>

      <div ref="menu" class="rider-menu">
        <button
          type="button"
          class="rider"
          aria-haspopup="menu"
          :aria-expanded="menuOpen"
          @click="menuOpen = !menuOpen"
        >
          <span class="avatar" aria-hidden="true">{{ initial(currentRider.name) }}</span>
          <span class="rider-name">{{ currentRider.name }}</span>
          <span class="caret" aria-hidden="true">▾</span>
        </button>
        <div v-if="menuOpen" class="dropdown" role="menu">
          <p class="signed-in">
            Planning as <b>{{ currentRider.name }}</b>
          </p>
          <RouterLink to="/riders" role="menuitem">Switch rider</RouterLink>
          <RouterLink :to="{ path: '/riders', query: { manage: '1' } }" role="menuitem"
            >Manage riders</RouterLink
          >
        </div>
      </div>
    </template>
  </header>
</template>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  gap: 24px;
  height: 52px;
  padding: 0 16px;
  background: var(--panel);
  border-bottom: 1px solid var(--line);
  position: relative;
  z-index: 20;
}
.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  color: var(--ink);
  text-decoration: none;
}
.logo {
  width: 30px;
  height: 30px;
}
.name {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 24px;
  line-height: 1;
}
.main {
  display: flex;
  gap: 2px;
  height: 100%;
}
.main a {
  display: flex;
  align-items: center;
  padding: 0 12px;
  color: var(--muted);
  text-decoration: none;
  font-weight: 500;
  border-bottom: 3px solid transparent;
  border-top: 3px solid transparent;
}
.main a:hover {
  color: var(--ink);
}
.main a.router-link-exact-active {
  color: var(--ink);
  border-bottom-color: var(--moss);
}
.rider-menu {
  margin-left: auto;
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
.avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--moss);
  color: var(--moss-ink);
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 15px;
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
  .app-header {
    gap: 10px;
    padding: 0 10px;
  }
  .name,
  .rider-name {
    display: none;
  }
  .main a {
    padding: 0 8px;
  }
}
</style>
