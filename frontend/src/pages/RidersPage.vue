<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api'
import Avatar from '@/components/atoms/Avatar.vue'
import CenteredLayout from '@/components/templates/CenteredLayout.vue'
import { chooseRider, currentRider, forgetRider, loadSession } from '@/session'
import type { RiderSummary } from '@/types'

const route = useRoute()
const router = useRouter()
const riders = ref<RiderSummary[]>([])
const loading = ref(true)
const error = ref('')
const newName = ref('')
/** The rider menu's "Manage riders" opens this page in manage mode. */
const managing = ref(route.query.manage === '1')
watch(
  () => route.query.manage,
  (manage) => {
    managing.value = manage === '1'
  },
)
const renamingId = ref<number | null>(null)
const renameText = ref('')
const renameInput = ref<HTMLInputElement[]>([])

async function load() {
  try {
    riders.value = await api.riders()
    error.value = ''
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'The server is not reachable.'
  } finally {
    loading.value = false
  }
}

async function act(task: () => Promise<void>) {
  error.value = ''
  try {
    await task()
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Something went wrong.'
  }
}

/**
 * Picks the rider and opens the library. A deep link that led here (e.g. a bookmarked page other
 * than the start page) is honoured instead.
 */
const pick = (rider: RiderSummary) =>
  act(async () => {
    await chooseRider(rider.id)
    const next = typeof route.query.next === 'string' ? route.query.next : ''
    const deepLink = next.startsWith('/') && next !== '/' && !next.startsWith('/riders')
    await router.push(deepLink ? next : '/library')
  })

const add = () =>
  act(async () => {
    const name = newName.value.trim()
    if (!name) return
    const rider = await api.createRider(name)
    newName.value = ''
    await load()
    const created = riders.value.find((r) => r.id === rider.id)
    if (created && !managing.value) await pick(created)
  })

async function startRename(rider: RiderSummary) {
  renamingId.value = rider.id
  renameText.value = rider.name
  await nextTick()
  renameInput.value[0]?.select()
}

const finishRename = (rider: RiderSummary) =>
  act(async () => {
    const name = renameText.value.trim()
    if (renamingId.value !== rider.id) return
    renamingId.value = null
    if (!name || name === rider.name) return
    const renamed = await api.renameRider(rider.id, name)
    if (currentRider.value?.id === rider.id) currentRider.value = renamed
    await load()
  })

const remove = (rider: RiderSummary) =>
  act(async () => {
    const routes = rider.routes === 1 ? '1 route' : `${String(rider.routes)} routes`
    if (
      !confirm(`Delete rider “${rider.name}” and ${routes} (including the trash)? This cannot be undone.`)
    ) {
      return
    }
    await api.deleteRider(rider.id)
    if (currentRider.value?.id === rider.id) forgetRider()
    await load()
  })

onMounted(async () => {
  await loadSession().catch(() => undefined)
  await load()
  if (riders.value.length === 0) await nextTick(() => document.getElementById('new-rider')?.focus())
})
</script>

<template>
  <CenteredLayout>
    <h1>Who's planning?</h1>
    <p class="sub">Pick your name to open your library.</p>

    <p v-if="loading" class="empty">Loading…</p>
    <ul v-else-if="riders.length" class="riders">
      <li v-for="r in riders" :key="r.id">
        <template v-if="renamingId === r.id">
          <input
            ref="renameInput"
            v-model="renameText"
            type="text"
            maxlength="60"
            aria-label="New name"
            @keydown.enter.prevent="finishRename(r)"
            @keydown.esc.prevent="renamingId = null"
            @blur="finishRename(r)"
          />
        </template>
        <button
          v-else
          type="button"
          class="rider"
          :class="{ current: currentRider?.id === r.id }"
          :disabled="managing"
          @click="pick(r)"
        >
          <Avatar :name="r.name" size="lg" />
          <span class="who">
            <span class="name">{{ r.name }}</span>
            <span class="meta">{{ r.routes === 1 ? '1 route' : `${r.routes} routes` }}</span>
          </span>
        </button>
        <span v-if="managing && renamingId !== r.id" class="manage">
          <button type="button" :aria-label="`Rename ${r.name}`" @click="startRename(r)">Rename</button>
          <button type="button" class="danger" :aria-label="`Delete ${r.name}`" @click="remove(r)">
            Delete
          </button>
        </span>
      </li>
    </ul>

    <form class="add" @submit.prevent="add">
      <label for="new-rider">{{ riders.length ? 'Add a rider' : 'Add the first rider' }}</label>
      <div class="row">
        <input
          id="new-rider"
          v-model="newName"
          type="text"
          maxlength="60"
          placeholder="Name"
          autocomplete="off"
        />
        <button type="submit" class="primary" :disabled="!newName.trim()">Add</button>
      </div>
    </form>

    <p v-if="error" class="error" role="alert">{{ error }}</p>

    <div class="foot">
      <button v-if="riders.length" type="button" class="link" @click="managing = !managing">
        {{ managing ? 'Done' : 'Manage riders' }}
      </button>
      <span class="note">No passwords: everyone on this network can open every library.</span>
    </div>
  </CenteredLayout>
</template>

<style scoped>
h1 {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 30px;
  line-height: 1;
  margin: 0;
}
.sub {
  margin: 6px 0 18px;
  color: var(--muted);
  font-size: 15px;
}
.riders {
  list-style: none;
  margin: 0 0 18px;
  padding: 0;
  display: grid;
  gap: 8px;
}
.riders li {
  display: flex;
  gap: 8px;
  align-items: center;
}
.rider {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  text-align: left;
  border-radius: 10px;
}
.rider:hover:not(:disabled) {
  border-color: var(--moss);
}
.rider.current {
  border-color: var(--moss);
  box-shadow: inset 0 0 0 1px var(--moss);
}
.who {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.name {
  font-weight: 600;
  font-size: 16px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meta {
  color: var(--muted);
  font-size: 13px;
}
.manage {
  display: flex;
  gap: 4px;
}
.manage button {
  padding: 4px 9px;
  font-size: 13px;
}
.danger {
  border-color: var(--warn);
  color: var(--warn);
}
.add label {
  display: block;
  font-weight: 500;
  margin-bottom: 4px;
}
.add input {
  flex: 1;
}
.error {
  color: var(--warn);
  margin: 10px 0 0;
}
.foot {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 12px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}
.foot .link {
  white-space: nowrap;
}
.note {
  color: var(--muted);
  font-size: 12.5px;
  text-align: right;
}
</style>
