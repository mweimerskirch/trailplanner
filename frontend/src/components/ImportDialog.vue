<script setup lang="ts">
import { computed, ref } from 'vue'
import { api, ApiError } from '@/api'
import type { DeviceInfo, ImportResult, KomootStatus, KomootTour } from '@/types'

const emit = defineEmits<{ imported: [openId: number | null] }>()

const dialog = ref<HTMLDialogElement | null>(null)
const tab = ref<'files' | 'komoot'>('files')
const busy = ref(false)
const error = ref('')
const results = ref<ImportResult[]>([])
const dragging = ref(false)

const devices = ref<DeviceInfo[]>([])
const deviceHints = computed(() => devices.value.filter((d) => d.importInstructions))

const komoot = ref<KomootStatus>({ connected: false, displayName: null })
const email = ref('')
const password = ref('')
const tours = ref<KomootTour[]>([])
const tourFilter = ref<'planned' | 'recorded' | 'all'>('planned')
const tourQuery = ref('')
const selected = ref<Set<number>>(new Set())

const visibleTours = computed(() => {
  const q = tourQuery.value.trim().toLocaleLowerCase()
  return tours.value.filter(
    (t) =>
      (tourFilter.value === 'all' || t.planned === (tourFilter.value === 'planned')) &&
      (!q || t.name.toLocaleLowerCase().includes(q)),
  )
})
const selectable = computed(() => visibleTours.value.filter((t) => !t.imported))
const imported = computed(() => results.value.filter((r) => r.status === 'IMPORTED'))

async function open() {
  error.value = ''
  results.value = []
  dialog.value?.showModal()
  try {
    ;[devices.value, komoot.value] = await Promise.all([api.devices(), api.komootStatus()])
    if (komoot.value.connected && tours.value.length === 0) await loadTours()
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'The server is not reachable.'
  }
}

function close() {
  dialog.value?.close()
}

async function run(task: () => Promise<void>) {
  busy.value = true
  error.value = ''
  try {
    await task()
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Something went wrong.'
    if (e instanceof ApiError && e.status === 401 && tab.value === 'komoot') {
      komoot.value = { connected: false, displayName: null }
    }
  } finally {
    busy.value = false
  }
}

function finish(list: ImportResult[]) {
  results.value = list
  const done = list.filter((r) => r.status === 'IMPORTED')
  if (done.length > 0) emit('imported', null)
}

const importFiles = (files: File[]) =>
  run(async () => {
    if (files.length === 0) return
    finish(await api.importFiles(files))
  })

function onFileInput(event: Event) {
  const input = event.target as HTMLInputElement
  void importFiles([...(input.files ?? [])])
  input.value = ''
}

function onDrop(event: DragEvent) {
  dragging.value = false
  void importFiles([...(event.dataTransfer?.files ?? [])])
}

const loadTours = () =>
  run(async () => {
    tours.value = await api.komootTours()
    selected.value = new Set()
  })

const login = () =>
  run(async () => {
    komoot.value = await api.komootLogin(email.value, password.value)
    password.value = ''
    await api.komootTours().then((list) => {
      tours.value = list
    })
  })

const logout = () =>
  run(async () => {
    await api.komootLogout()
    komoot.value = { connected: false, displayName: null }
    tours.value = []
  })

function toggle(id: number) {
  const next = new Set(selected.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selected.value = next
}

function selectAll() {
  const all = selectable.value.every((t) => selected.value.has(t.id))
  selected.value = all ? new Set() : new Set(selectable.value.map((t) => t.id))
}

const importTours = () =>
  run(async () => {
    finish(await api.komootImport([...selected.value]))
    tours.value = await api.komootTours()
    selected.value = new Set()
  })

function openRoute(id: number) {
  close()
  emit('imported', id)
}

const km = (m: number) => (m / 1000).toFixed(1)
const date = (iso: string | null) => (iso ? new Date(iso).toLocaleDateString() : '')
const resultIcon = (r: ImportResult) => ({ IMPORTED: '✓', ALREADY_IMPORTED: '=', FAILED: '✕' })[r.status]

defineExpose({ open })
</script>

<template>
  <dialog ref="dialog" class="import" aria-labelledby="import-heading" @click.self="close">
    <div class="box">
      <header>
        <h2 id="import-heading">Import routes</h2>
        <button type="button" class="icon" aria-label="Close" @click="close">✕</button>
      </header>
      <div class="tabs" role="tablist">
        <button
          type="button"
          role="tab"
          :aria-selected="tab === 'files'"
          :class="{ active: tab === 'files' }"
          @click="tab = 'files'"
        >
          Files (GPX, FIT)
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="tab === 'komoot'"
          :class="{ active: tab === 'komoot' }"
          @click="tab = 'komoot'"
        >
          Komoot
        </button>
      </div>

      <div v-if="tab === 'files'" class="pane">
        <label
          class="drop"
          :class="{ dragging }"
          @dragover.prevent="dragging = true"
          @dragleave="dragging = false"
          @drop.prevent="onDrop"
        >
          <input type="file" multiple accept=".gpx,.fit" class="visually-hidden" @change="onFileInput" />
          <strong>Drop GPX or FIT files here</strong>
          <span>or click to choose files. Several at once are fine.</span>
        </label>
        <div v-if="deviceHints.length" class="hints">
          <p v-for="d in deviceHints" :key="d.id">
            <b>{{ d.name }}:</b> {{ d.importInstructions }}
          </p>
          <p><b>Komoot:</b> use the Komoot tab, or download GPX files from komoot.com and drop them here.</p>
        </div>
      </div>

      <div v-else class="pane">
        <form v-if="!komoot.connected" class="login" @submit.prevent="login">
          <div class="field">
            <label for="komoot-email">Komoot email</label>
            <input id="komoot-email" v-model="email" type="email" autocomplete="username" required />
          </div>
          <div class="field">
            <label for="komoot-password">Password</label>
            <input
              id="komoot-password"
              v-model="password"
              type="password"
              autocomplete="current-password"
              required
            />
          </div>
          <button type="submit" class="primary" :disabled="busy">Sign in to Komoot</button>
          <p class="note">
            Your password goes to Komoot only and is not stored; Trailplan keeps Komoot's session token until
            you sign out or close the browser. This uses Komoot's unofficial web API, which may stop working
            if Komoot changes it. If it does, download GPX files from komoot.com and use the Files tab.
          </p>
        </form>

        <template v-else>
          <p class="signed-in">
            Signed in as <b>{{ komoot.displayName }}</b
            >.
            <button type="button" class="link" @click="logout">Sign out</button>
          </p>
          <div class="row filters">
            <select v-model="tourFilter" aria-label="Which tours">
              <option value="planned">Planned tours</option>
              <option value="recorded">Recorded tours</option>
              <option value="all">All tours</option>
            </select>
            <input v-model="tourQuery" type="search" placeholder="Filter" aria-label="Filter tours by name" />
          </div>
          <p v-if="busy && tours.length === 0" class="empty">Loading your tours…</p>
          <p v-else-if="visibleTours.length === 0" class="empty">No tours here.</p>
          <template v-else>
            <label class="select-all">
              <input
                type="checkbox"
                :checked="selectable.length > 0 && selectable.every((t) => selected.has(t.id))"
                :disabled="selectable.length === 0"
                @change="selectAll"
              />
              Select all not yet imported ({{ selectable.length }})
            </label>
            <ul class="tours">
              <li v-for="t in visibleTours" :key="t.id" :class="{ done: t.imported }">
                <label>
                  <input
                    type="checkbox"
                    :checked="selected.has(t.id)"
                    :disabled="t.imported"
                    @change="toggle(t.id)"
                  />
                  <span class="tour-name">{{ t.name }}</span>
                  <span class="meta">
                    {{ km(t.distance) }} km · {{ Math.round(t.ascent) }} m · {{ date(t.date) }}
                    <template v-if="t.imported"> · in library</template>
                  </span>
                </label>
              </li>
            </ul>
            <button
              type="button"
              class="primary"
              :disabled="busy || selected.size === 0"
              @click="importTours"
            >
              Import {{ selected.size || '' }} tour{{ selected.size === 1 ? '' : 's' }}
            </button>
          </template>
        </template>
      </div>

      <p v-if="busy && tab === 'files'" class="empty">Importing…</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <div v-if="results.length" class="results" aria-live="polite">
        <p>
          {{ imported.length }} imported<template v-if="results.length > imported.length">
            · {{ results.length - imported.length }} skipped or failed</template
          >
        </p>
        <ul>
          <li v-for="(r, i) in results" :key="i" :class="r.status.toLowerCase()">
            <span class="status" aria-hidden="true">{{ resultIcon(r) }}</span>
            <span class="what">{{ r.name ?? r.source }}</span>
            <span v-if="r.message" class="meta">{{ r.message }}</span>
            <button v-if="r.routeId !== null" type="button" class="link" @click="openRoute(r.routeId)">
              Open
            </button>
          </li>
        </ul>
      </div>
    </div>
  </dialog>
</template>

<style scoped>
.import {
  width: min(560px, 94vw);
  max-height: 88vh;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--panel);
  color: var(--ink);
  box-shadow: var(--shadow);
}
.import::backdrop {
  background: rgb(0 0 0 / 35%);
}
.box {
  padding: 16px 18px 18px;
}
header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
header h2 {
  margin: 0;
}
.icon {
  padding: 2px 9px;
}
.tabs {
  display: flex;
  gap: 4px;
  margin: 12px 0;
  border-bottom: 1px solid var(--line);
}
.tabs button {
  border: none;
  border-bottom: 3px solid transparent;
  border-radius: 0;
  background: none;
  padding: 6px 10px;
}
.tabs button.active {
  border-bottom-color: var(--moss);
  font-weight: 600;
}
.drop {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 26px 12px;
  border: 2px dashed var(--line);
  border-radius: 10px;
  cursor: pointer;
  text-align: center;
}
.drop span {
  color: var(--muted);
  font-size: 13.5px;
}
.drop.dragging,
.drop:hover {
  border-color: var(--moss);
}
.hints p,
.note {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--muted);
}
.login .primary {
  margin-top: 4px;
}
.signed-in {
  margin: 0 0 8px;
}
.filters select,
.filters input {
  flex: 1;
}
.select-all {
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 10px 0 4px;
  font-size: 13.5px;
}
.tours {
  list-style: none;
  margin: 0 0 10px;
  padding: 0;
  max-height: 38vh;
  overflow-y: auto;
  border: 1px solid var(--line);
  border-radius: 8px;
}
.tours li + li {
  border-top: 1px solid var(--line);
}
.tours label {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0 8px;
  padding: 6px 10px;
  cursor: pointer;
}
.tours .meta {
  grid-column: 2;
}
.tours li.done {
  opacity: 0.6;
}
.tour-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meta {
  color: var(--muted);
  font-size: 12.5px;
}
.error {
  color: var(--warn);
  margin: 10px 0 0;
}
.results {
  margin-top: 12px;
  border-top: 1px solid var(--line);
  padding-top: 8px;
}
.results p {
  margin: 0 0 6px;
  font-weight: 600;
}
.results ul {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 24vh;
  overflow-y: auto;
}
.results li {
  display: flex;
  gap: 8px;
  align-items: baseline;
  padding: 3px 0;
  font-size: 13.5px;
}
.results .what {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.results .link {
  margin-left: auto;
}
.status {
  width: 14px;
  font-weight: 700;
}
.imported .status {
  color: var(--moss);
}
.failed .status {
  color: var(--warn);
}
</style>
