<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { api, ApiError } from '@/api'
import TabBar from '@/components/molecules/TabBar.vue'
import { day, km, num } from '@/format'
import type { DeviceInfo, ImportResult, KomootStatus, KomootTour } from '@/types'

const emit = defineEmits<{ imported: [openId: number | null] }>()
const { t } = useI18n()

const dialog = ref<HTMLDialogElement | null>(null)
const tab = ref<string>('files')
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
    (tour) =>
      (tourFilter.value === 'all' || tour.planned === (tourFilter.value === 'planned')) &&
      (!q || tour.name.toLocaleLowerCase().includes(q)),
  )
})
const selectable = computed(() => visibleTours.value.filter((tour) => !tour.imported))
const imported = computed(() => results.value.filter((r) => r.status === 'IMPORTED'))

async function open() {
  error.value = ''
  results.value = []
  dialog.value?.showModal()
  try {
    ;[devices.value, komoot.value] = await Promise.all([api.devices(), api.komootStatus()])
    if (komoot.value.connected && tours.value.length === 0) await loadTours()
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.error.unreachable')
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
    error.value = e instanceof Error ? e.message : t('library.import.failed')
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
  const all = selectable.value.every((tour) => selected.value.has(tour.id))
  selected.value = all ? new Set() : new Set(selectable.value.map((tour) => tour.id))
}

/** Tours per request: large selections are imported in batches, with progress. */
const BATCH = 20
const progress = ref('')

const importTours = () =>
  run(async () => {
    const ids = [...selected.value]
    const all: ImportResult[] = []
    for (let i = 0; i < ids.length; i += BATCH) {
      progress.value = t('library.import.progress', {
        done: Math.min(i + BATCH, ids.length),
        total: ids.length,
      })
      all.push(...(await api.komootImport(ids.slice(i, i + BATCH))))
    }
    progress.value = ''
    finish(all)
    tours.value = await api.komootTours()
    selected.value = new Set()
  })

function openRoute(id: number) {
  close()
  emit('imported', id)
}

const resultIcon = (r: ImportResult) => ({ IMPORTED: '✓', ALREADY_IMPORTED: '=', FAILED: '✕' })[r.status]

defineExpose({ open })
</script>

<template>
  <dialog ref="dialog" class="import" aria-labelledby="import-heading" @click.self="close">
    <div class="box">
      <header>
        <h2 id="import-heading">{{ t('library.import.title') }}</h2>
        <button type="button" class="icon" :aria-label="t('common.action.close')" @click="close">✕</button>
      </header>
      <TabBar
        v-model="tab"
        class="dialog-tabs"
        :tabs="[
          { id: 'files', label: t('library.import.filesTab') },
          { id: 'komoot', label: 'Komoot' },
        ]"
      />

      <div v-if="tab === 'files'" class="pane">
        <label
          class="drop"
          :class="{ dragging }"
          @dragover.prevent="dragging = true"
          @dragleave="dragging = false"
          @drop.prevent="onDrop"
        >
          <input type="file" multiple accept=".gpx,.fit" class="visually-hidden" @change="onFileInput" />
          <strong>{{ t('library.import.drop') }}</strong>
          <span>{{ t('library.import.dropHint') }}</span>
        </label>
        <div v-if="deviceHints.length" class="hints">
          <p v-for="d in deviceHints" :key="d.id">
            <b>{{ d.name }}:</b> {{ d.importInstructions }}
          </p>
          <p><b>Komoot:</b> {{ t('library.import.komootHint') }}</p>
        </div>
      </div>

      <div v-else class="pane">
        <form v-if="!komoot.connected" class="login" @submit.prevent="login">
          <div class="field">
            <label for="komoot-email">{{ t('library.komoot.email') }}</label>
            <input id="komoot-email" v-model="email" type="email" autocomplete="username" required />
          </div>
          <div class="field">
            <label for="komoot-password">{{ t('library.komoot.password') }}</label>
            <input
              id="komoot-password"
              v-model="password"
              type="password"
              autocomplete="current-password"
              required
            />
          </div>
          <button type="submit" class="primary" :disabled="busy">{{ t('library.komoot.signIn') }}</button>
          <p class="note">{{ t('library.komoot.note') }}</p>
        </form>

        <template v-else>
          <p class="signed-in">
            <i18n-t keypath="library.komoot.signedIn" tag="span">
              <template #name>
                <b>{{ komoot.displayName }}</b>
              </template>
            </i18n-t>
            <button type="button" class="link" @click="logout">{{ t('library.komoot.signOut') }}</button>
          </p>
          <div class="row filters">
            <select v-model="tourFilter" :aria-label="t('library.komoot.which')">
              <option value="planned">{{ t('library.komoot.planned') }}</option>
              <option value="recorded">{{ t('library.komoot.recorded') }}</option>
              <option value="all">{{ t('library.komoot.all') }}</option>
            </select>
            <input
              v-model="tourQuery"
              type="search"
              :placeholder="t('library.komoot.filter')"
              :aria-label="t('library.komoot.filterLabel')"
            />
          </div>
          <p v-if="busy && tours.length === 0" class="empty">{{ t('library.komoot.loading') }}</p>
          <p v-else-if="visibleTours.length === 0" class="empty">{{ t('library.komoot.none') }}</p>
          <template v-else>
            <label class="select-all">
              <input
                type="checkbox"
                :checked="selectable.length > 0 && selectable.every((tour) => selected.has(tour.id))"
                :disabled="selectable.length === 0"
                @change="selectAll"
              />
              {{ t('library.komoot.selectAll', { n: selectable.length }) }}
            </label>
            <ul class="tours">
              <li v-for="tour in visibleTours" :key="tour.id" :class="{ done: tour.imported }">
                <label>
                  <input
                    type="checkbox"
                    :checked="selected.has(tour.id)"
                    :disabled="tour.imported"
                    @change="toggle(tour.id)"
                  />
                  <span class="tour-name">{{ tour.name }}</span>
                  <span class="meta">
                    {{ km(tour.distance) }} {{ t('common.unit.km') }} · {{ num(Math.round(tour.ascent)) }}
                    {{ t('common.unit.m') }} · {{ day(tour.date) }}
                    <template v-if="tour.imported"> · {{ t('library.komoot.inLibrary') }}</template>
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
              {{ t('library.komoot.import', { n: selected.size }, selected.size) }}
            </button>
          </template>
        </template>
      </div>

      <p v-if="busy && tab === 'files'" class="empty">{{ t('library.import.importing') }}</p>
      <p v-if="progress" class="empty" aria-live="polite">{{ progress }}</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <div v-if="results.length" class="results" aria-live="polite">
        <p>
          {{ t('library.import.imported', { n: imported.length }, imported.length)
          }}<template v-if="results.length > imported.length">
            ·
            {{
              t(
                'library.import.skipped',
                { n: results.length - imported.length },
                results.length - imported.length,
              )
            }}</template
          >
        </p>
        <ul>
          <li v-for="(r, i) in results" :key="i" :class="r.status.toLowerCase()">
            <span class="status" aria-hidden="true">{{ resultIcon(r) }}</span>
            <span class="what">{{ r.name ?? r.source }}</span>
            <span v-if="r.message" class="meta">{{ r.message }}</span>
            <button v-if="r.routeId !== null" type="button" class="link" @click="openRoute(r.routeId)">
              {{ t('common.action.open') }}
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
.dialog-tabs {
  margin: 12px 0;
  border-bottom: 1px solid var(--line);
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
