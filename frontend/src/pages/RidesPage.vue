<script setup lang="ts">
import { computed, onMounted, ref, useTemplateRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { api } from '@/api'
import IconButton from '@/components/atoms/IconButton.vue'
import SortableHeader from '@/components/molecules/SortableHeader.vue'
import StatusMessage from '@/components/molecules/StatusMessage.vue'
import RideImportButton from '@/components/organisms/RideImportButton.vue'
import PageLayout from '@/components/templates/PageLayout.vue'
import { askConfirm } from '@/confirm'
import { duration, km, kmh, num, rideTime } from '@/format'
import * as planner from '@/planner'
import type { RideSummary } from '@/types'

type SortKey = 'date' | 'name' | 'distance' | 'moving' | 'ascent' | 'speed'
type SortDir = 'asc' | 'desc'
const SORT_STORAGE = 'trailplan.ridesSort'
const FIRST_DIR: Record<SortKey, SortDir> = {
  date: 'desc',
  name: 'asc',
  distance: 'desc',
  moving: 'desc',
  ascent: 'desc',
  speed: 'desc',
}
const COMPARE: Record<SortKey, (a: RideSummary, b: RideSummary) => number> = {
  date: (a, b) => a.startedAt.localeCompare(b.startedAt),
  name: (a, b) => a.name.localeCompare(b.name),
  distance: (a, b) => a.distance - b.distance,
  moving: (a, b) => a.moving - b.moving,
  ascent: (a, b) => a.ascent - b.ascent,
  speed: (a, b) => (a.avgSpeed ?? 0) - (b.avgSpeed ?? 0),
}

const router = useRouter()
const { t } = useI18n()
const rides = ref<RideSummary[]>([])
const loading = ref(true)
const failed = ref(false)
const query = ref('')
const uploading = ref(false)
const fileInput = useTemplateRef<HTMLInputElement>('fileInput')
const sort = ref<{ key: SortKey; dir: SortDir }>(loadSort())
watch(sort, (value) => {
  localStorage.setItem(SORT_STORAGE, JSON.stringify(value))
})

function loadSort(): { key: SortKey; dir: SortDir } {
  try {
    const stored = JSON.parse(localStorage.getItem(SORT_STORAGE) ?? 'null') as {
      key?: unknown
      dir?: unknown
    } | null
    const key = stored?.key
    const dir = stored?.dir
    if (typeof key === 'string' && key in COMPARE && (dir === 'asc' || dir === 'desc')) {
      return { key: key as SortKey, dir }
    }
  } catch {
    // ignore a corrupt value
  }
  return { key: 'date', dir: 'desc' }
}

function sortBy(column: string) {
  const key = column as SortKey
  sort.value =
    sort.value.key === key
      ? { key, dir: sort.value.dir === 'asc' ? 'desc' : 'asc' }
      : { key, dir: FIRST_DIR[key] }
}

const visible = computed(() => {
  const q = query.value.trim().toLocaleLowerCase()
  const list = rides.value.filter((r) => !q || r.name.toLocaleLowerCase().includes(q))
  const compare = COMPARE[sort.value.key]
  const factor = sort.value.dir === 'asc' ? 1 : -1
  return [...list].sort((a, b) => factor * compare(a, b) || b.startedAt.localeCompare(a.startedAt))
})

/** Totals of the rides shown. */
const totals = computed(() => ({
  distance: visible.value.reduce((sum, r) => sum + r.distance, 0),
  moving: visible.value.reduce((sum, r) => sum + r.moving, 0),
  ascent: visible.value.reduce((sum, r) => sum + r.ascent, 0),
}))

const date = (r: RideSummary) =>
  rideTime(r.startedAt, r.utcOffset, { day: 'numeric', month: 'short', year: 'numeric' })
const time = (r: RideSummary) => rideTime(r.startedAt, r.utcOffset, { hour: '2-digit', minute: '2-digit' })

async function load() {
  try {
    rides.value = await api.listRides()
    failed.value = false
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

async function upload(event: Event) {
  const input = event.target as HTMLInputElement
  const files = [...(input.files ?? [])]
  input.value = ''
  if (!files.length) return
  uploading.value = true
  try {
    const results = await api.importRideFiles(files)
    const imported = results.filter((r) => r.status === 'IMPORTED').length
    const problems = results.filter((r) => r.status === 'FAILED')
    const already = results.length - imported - problems.length
    const failedText = problems
      .map((p) => `${p.fileName}: ${p.message ?? t('rides.upload.notReadable')}`)
      .join('; ')
    const parts = [
      t('rides.upload.imported', { n: imported }, imported),
      already ? t('rides.upload.already', { n: already }, already) : '',
      failedText ? t('rides.upload.notImported', { list: failedText }) : '',
    ].filter(Boolean)
    planner.setStatus(parts.join(' '), problems.length ? 'error' : 'info', problems.length ? 0 : 5000)
    await load()
  } catch (error) {
    planner.setStatus(error instanceof Error ? error.message : t('rides.upload.failed'), 'error')
  } finally {
    uploading.value = false
  }
}

async function remove(r: RideSummary) {
  const ok = await askConfirm({
    title: t('rides.confirmDelete.title', { name: r.name }),
    message: t('rides.confirmDelete.message'),
    confirmLabel: t('rides.confirmDelete.confirm'),
    danger: true,
  })
  if (!ok) return
  try {
    await api.deleteRide(r.id)
    rides.value = rides.value.filter((x) => x.id !== r.id)
  } catch (error) {
    planner.setStatus(error instanceof Error ? error.message : t('rides.error.delete'), 'error')
  }
}

const open = (r: RideSummary) => void router.push(`/rides/${String(r.id)}`)

onMounted(load)
</script>

<template>
  <PageLayout>
    <div class="toolbar">
      <h1>
        {{ t('rides.title') }} <span v-if="rides.length" class="count">{{ rides.length }}</span>
      </h1>
      <div class="actions">
        <RideImportButton @imported="load" />
        <button type="button" class="primary" :disabled="uploading" @click="fileInput?.click()">
          {{ uploading ? t('rides.importing') : t('rides.importFiles') }}
        </button>
        <input
          ref="fileInput"
          type="file"
          accept=".fit"
          multiple
          hidden
          :aria-label="t('rides.filesLabel')"
          @change="upload"
        />
      </div>
    </div>

    <StatusMessage :message="planner.status.value" />

    <p v-if="loading" class="empty">{{ t('common.loading') }}</p>
    <p v-else-if="failed" class="empty">{{ t('rides.unavailable') }}</p>
    <div v-else-if="rides.length === 0" class="blank">
      <p>{{ t('rides.none') }}</p>
    </div>
    <template v-else>
      <div class="filters">
        <input
          v-model="query"
          type="search"
          :placeholder="t('rides.search')"
          :aria-label="t('rides.searchLabel')"
        />
        <p class="totals">
          {{
            t('rides.totals', {
              distance: km(totals.distance),
              moving: duration(totals.moving),
              ascent: num(totals.ascent),
            })
          }}
        </p>
      </div>
      <p v-if="visible.length === 0" class="empty">{{ t('rides.noMatch', { query }) }}</p>
      <table v-else class="rides">
        <thead>
          <tr>
            <SortableHeader
              :label="t('rides.column.date')"
              column="date"
              :sort-key="sort.key"
              :sort-dir="sort.dir"
              @sort="sortBy"
            />
            <SortableHeader
              :label="t('rides.column.name')"
              column="name"
              :sort-key="sort.key"
              :sort-dir="sort.dir"
              @sort="sortBy"
            />
            <SortableHeader
              :label="t('rides.column.distance')"
              column="distance"
              numeric
              :sort-key="sort.key"
              :sort-dir="sort.dir"
              @sort="sortBy"
            />
            <SortableHeader
              :label="t('rides.column.moving')"
              column="moving"
              numeric
              :sort-key="sort.key"
              :sort-dir="sort.dir"
              @sort="sortBy"
            />
            <SortableHeader
              :label="t('rides.column.ascent')"
              column="ascent"
              numeric
              :sort-key="sort.key"
              :sort-dir="sort.dir"
              @sort="sortBy"
            />
            <SortableHeader
              :label="t('rides.column.speed')"
              column="speed"
              numeric
              class="hide-narrow"
              :sort-key="sort.key"
              :sort-dir="sort.dir"
              @sort="sortBy"
            />
            <th scope="col">
              <span class="visually-hidden">{{ t('rides.column.actions') }}</span>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in visible" :key="r.id">
            <td class="date">
              {{ date(r) }} <span class="time">{{ time(r) }}</span>
            </td>
            <td class="name-cell">
              <button
                type="button"
                class="name"
                :title="t('rides.showRide', { name: r.name })"
                @click="open(r)"
              >
                {{ r.name }}
              </button>
            </td>
            <td class="num">{{ km(r.distance) }} {{ t('common.unit.km') }}</td>
            <td class="num">{{ duration(r.moving) }}</td>
            <td class="num">{{ num(r.ascent) }} {{ t('common.unit.m') }}</td>
            <td class="num hide-narrow">
              {{ r.avgSpeed === null ? '–' : `${kmh(r.avgSpeed)} ${t('common.unit.kmh')}` }}
            </td>
            <td class="actions">
              <button type="button" @click="open(r)">{{ t('rides.show') }}</button>
              <IconButton :label="t('rides.deleteRide', { name: r.name })" @click="remove(r)">✕</IconButton>
            </td>
          </tr>
        </tbody>
      </table>
    </template>
  </PageLayout>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin: 4px 0 14px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--line);
}
h1 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 26px;
}
.count {
  font-size: 15px;
  font-weight: 500;
  color: var(--muted);
}
.toolbar .actions {
  display: flex;
  gap: 8px;
}
.filters {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.totals {
  margin: 0;
  color: var(--muted);
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}
.blank {
  padding: 40px 20px;
  text-align: center;
  color: var(--muted);
  border: 2px dashed var(--line);
  border-radius: 12px;
}
.rides {
  width: 100%;
  border-collapse: collapse;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 10px;
  overflow: hidden;
  font-size: 14.5px;
}
th {
  text-align: left;
  font-weight: 600;
  font-size: 13px;
  color: var(--muted);
  padding: 9px 12px;
  border-bottom: 1px solid var(--line);
}
td {
  padding: 7px 12px;
  border-bottom: 1px solid var(--line);
  vertical-align: middle;
}
tr:last-child td {
  border-bottom: none;
}
tbody tr:hover {
  background: color-mix(in srgb, var(--line) 35%, transparent);
}
.date {
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.time {
  color: var(--muted);
  font-size: 13px;
  margin-left: 4px;
}
.num {
  text-align: right;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.name-cell {
  max-width: 0;
  width: 35%;
}
.name {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: none;
  border: none;
  padding: 2px 0;
  text-align: left;
  font-weight: 500;
}
.name:hover {
  text-decoration: underline;
}
.actions {
  text-align: right;
  white-space: nowrap;
}
.actions button,
.actions :deep(.icon-button) {
  padding: 3px 9px;
  font-size: 13px;
  margin-left: 3px;
}
.empty {
  color: var(--muted);
}
@media (max-width: 720px) {
  .hide-narrow {
    display: none;
  }
  .filters {
    grid-template-columns: 1fr;
  }
}
</style>
