<script setup lang="ts">
import { computed, nextTick, onMounted, ref, useTemplateRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api'
import Badge from '@/components/atoms/Badge.vue'
import StarToggle from '@/components/atoms/StarToggle.vue'
import IconButton from '@/components/atoms/IconButton.vue'
import SortableHeader from '@/components/molecules/SortableHeader.vue'
import StatusMessage from '@/components/molecules/StatusMessage.vue'
import TabBar from '@/components/molecules/TabBar.vue'
import DeviceSyncButton from '@/components/organisms/DeviceSyncButton.vue'
import ImportDialog from '@/components/organisms/ImportDialog.vue'
import PageLayout from '@/components/templates/PageLayout.vue'
import { askConfirm } from '@/confirm'
import { saveFile } from '@/files'
import { day, km, num } from '@/format'
import * as planner from '@/planner'
import type { RouteSource, RouteSummary } from '@/types'

const { t } = useI18n()

const sourceLabel = (source: RouteSource) => t(`library.source.${source}`)

type SortKey = 'updated' | 'name' | 'distance' | 'ascent' | 'source' | 'archived'
type SortDir = 'asc' | 'desc'
const SORT_STORAGE = 'trailplan.librarySort'
/** Direction of a column's first click: names A–Z, numbers and dates biggest/newest first. */
const FIRST_DIR: Record<SortKey, SortDir> = {
  updated: 'desc',
  name: 'asc',
  distance: 'desc',
  ascent: 'desc',
  source: 'asc',
  archived: 'desc',
}
const COMPARE: Record<SortKey, (a: RouteSummary, b: RouteSummary) => number> = {
  updated: (a, b) => a.updatedAt.localeCompare(b.updatedAt),
  name: (a, b) => a.name.localeCompare(b.name),
  distance: (a, b) => a.distance - b.distance,
  ascent: (a, b) => a.ascent - b.ascent,
  source: (a, b) => sourceLabel(a.source).localeCompare(sourceLabel(b.source)),
  archived: (a, b) => (a.archivedAt ?? '').localeCompare(b.archivedAt ?? ''),
}
type SourceFilter = 'all' | 'favorites' | 'planned' | 'imported'

const router = useRouter()
const route = useRoute()

type Tab = 'library' | 'archive' | 'trash'
/** The tab is part of the address: /library, /library/archived, /library/trash. */
const tab = computed<Tab>(() => {
  const param = route.params.tab
  return param === 'archived' ? 'archive' : param === 'trash' ? 'trash' : 'library'
})
function setTab(next: Tab) {
  const path = { library: '/library', archive: '/library/archived', trash: '/library/trash' }[next]
  if (route.path !== path) void router.push(path)
}
const routes = ref<RouteSummary[]>([])
const archived = ref<RouteSummary[]>([])
const trash = ref<RouteSummary[]>([])
const loading = ref(true)
const failed = ref(false)
const query = ref('')
/** Sort order of the route tables, shared by column headers and the dropdown; remembered. */
const sort = ref<{ key: SortKey; dir: SortDir }>(loadSort())
watch(sort, (value) => {
  localStorage.setItem(SORT_STORAGE, JSON.stringify(value))
})

function loadSort(): { key: SortKey; dir: SortDir } {
  try {
    // Stored by an earlier visit, so validate it like any outside input.
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
  return { key: 'updated', dir: 'desc' }
}

/** Header click: sort by that column, or reverse it if it already is the sort column. */
function sortBy(column: string) {
  const key = column as SortKey
  sort.value =
    sort.value.key === key
      ? { key, dir: sort.value.dir === 'asc' ? 'desc' : 'asc' }
      : { key, dir: FIRST_DIR[key] }
}

/** The dropdown's value, e.g. "distance:desc" (it offers the common orders). */
const sortChoice = computed({
  get: () => `${sort.value.key}:${sort.value.dir}`,
  set: (value: string) => {
    const [key, dir] = value.split(':') as [SortKey, SortDir]
    sort.value = { key, dir }
  },
})

function sorted(list: RouteSummary[]): RouteSummary[] {
  const compare = COMPARE[sort.value.key]
  const factor = sort.value.dir === 'asc' ? 1 : -1
  return [...list].sort((a, b) => factor * compare(a, b) || a.name.localeCompare(b.name))
}

const sortedArchive = computed(() => sorted(archived.value))
const sourceFilter = ref<SourceFilter>('all')
const renamingId = ref<number | null>(null)
const renameText = ref('')
const renameInput = ref<HTMLInputElement[]>([])
/** The import dialog exposes only open(). */
const importDialog = useTemplateRef<{ open: () => Promise<void> }>('importDialogRef')

const visible = computed(() => {
  const q = query.value.trim().toLocaleLowerCase()
  const list = routes.value.filter(
    (r) =>
      (!q || r.name.toLocaleLowerCase().includes(q)) &&
      (sourceFilter.value === 'all' ||
        (sourceFilter.value === 'favorites'
          ? r.favorite
          : (r.source === 'PLANNER') === (sourceFilter.value === 'planned'))),
  )
  return sorted(list)
})

async function load() {
  try {
    const [active, archivedRoutes, trashed] = await Promise.all([
      api.listRoutes(),
      api.listArchive(),
      api.listTrash(),
    ])
    routes.value = active
    archived.value = archivedRoutes
    trash.value = trashed
    failed.value = false
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

async function act(task: () => Promise<void>, failure: () => string) {
  try {
    await task()
  } catch (error) {
    planner.setStatus(error instanceof Error ? error.message : failure(), 'error')
  }
}

const open = (id: number) =>
  act(
    async () => {
      planner.openRoute(await api.getRoute(id))
      await router.push(`/routes/${String(id)}`)
    },
    () => t('library.error.open'),
  )

async function startRename(route: RouteSummary) {
  renamingId.value = route.id
  renameText.value = route.name
  await nextTick()
  renameInput.value[0]?.select()
}

const finishRename = (route: RouteSummary) =>
  act(
    async () => {
      const name = renameText.value.trim()
      if (renamingId.value !== route.id) return
      renamingId.value = null
      if (!name || name === route.name) return
      const renamed = await api.renameRoute(route.id, name)
      if (planner.routeId.value === route.id) planner.routeName.value = renamed.name
      planner.libraryVersion.value++
    },
    () => t('library.error.rename'),
  )

const duplicate = (route: RouteSummary) =>
  act(
    async () => {
      const copy = await api.duplicateRoute(route.id)
      planner.setStatus(t('library.status.duplicated', { name: copy.name }), 'info', 2500)
      planner.libraryVersion.value++
    },
    () => t('library.error.duplicate'),
  )

const download = (route: RouteSummary) =>
  act(
    async () => {
      saveFile(await api.exportSaved(route.id, 'gpx'))
    },
    () => t('library.error.download'),
  )

const toggleFavorite = (route: RouteSummary) =>
  act(
    async () => {
      const updated = await api.setFavorite(route.id, !route.favorite)
      if (planner.routeId.value === route.id) planner.favorite.value = updated.favorite
      planner.libraryVersion.value++
    },
    () => t('library.error.favorite'),
  )

const archive = (route: RouteSummary) =>
  act(
    async () => {
      await api.archiveRoute(route.id)
      planner.setStatus(t('library.status.archived', { name: route.name }), 'info', 3000)
      planner.libraryVersion.value++
    },
    () => t('library.error.archive'),
  )

const unarchive = (route: RouteSummary) =>
  act(
    async () => {
      await api.unarchiveRoute(route.id)
      planner.setStatus(t('library.status.unarchived', { name: route.name }), 'info', 2500)
      planner.libraryVersion.value++
    },
    () => t('library.error.unarchive'),
  )

const moveToTrash = (route: RouteSummary) =>
  act(
    async () => {
      const ok = await askConfirm({
        title: t('library.confirm.trash.title'),
        message: t('library.confirm.trash.message', { name: route.name }),
        confirmLabel: t('library.confirm.trash.confirm'),
      })
      if (!ok) return
      await api.deleteRoute(route.id)
      if (planner.routeId.value === route.id) {
        // The open route went to the trash: an imported (view-only) one is closed; an edited one
        // stays in the planner as a new, unsaved route the rider can still keep.
        if (planner.viewOnly.value) planner.newRoute()
        else {
          planner.routeId.value = null
          planner.routeUpdatedAt.value = null
          planner.hasOriginal.value = false
        }
      }
      planner.setStatus(t('library.status.trashed', { name: route.name }), 'info', 3000)
      planner.libraryVersion.value++
    },
    () => t('library.error.delete'),
  )

const restore = (route: RouteSummary) =>
  act(
    async () => {
      await api.restoreRoute(route.id)
      planner.setStatus(t('library.status.restored', { name: route.name }), 'info', 2500)
      planner.libraryVersion.value++
    },
    () => t('library.error.restore'),
  )

const deleteForever = (route: RouteSummary) =>
  act(
    async () => {
      if (
        !(await askConfirm({
          title: t('library.confirm.deleteForever.title'),
          message: t('library.confirm.deleteForever.message', { name: route.name }),
          confirmLabel: t('library.confirm.deleteForever.confirm'),
          danger: true,
        }))
      ) {
        return
      }
      await api.deleteRoutePermanently(route.id)
      planner.libraryVersion.value++
    },
    () => t('library.error.delete'),
  )

const emptyTrash = () =>
  act(
    async () => {
      const n = trash.value.length
      if (
        !(await askConfirm({
          title: t('library.confirm.emptyTrash.title'),
          message: t('library.confirm.emptyTrash.message', { n }, n),
          confirmLabel: t('library.confirm.emptyTrash.confirm'),
          danger: true,
        }))
      ) {
        return
      }
      await api.emptyTrash()
      planner.libraryVersion.value++
      setTab('library')
    },
    () => t('library.error.emptyTrash'),
  )

function onImported(openId: number | null) {
  planner.libraryVersion.value++
  if (openId !== null) void open(openId)
}

onMounted(load)
watch(planner.libraryVersion, load)
</script>

<template>
  <PageLayout>
    <div class="toolbar">
      <TabBar
        large
        :tabs="[
          { id: 'library', label: t('library.tab.routes'), count: routes.length },
          { id: 'archive', label: t('library.tab.archived'), count: archived.length },
          { id: 'trash', label: t('library.tab.trash'), count: trash.length },
        ]"
        :model-value="tab"
        @update:model-value="setTab($event as Tab)"
      />
      <div class="actions">
        <DeviceSyncButton />
        <button type="button" class="primary" @click="void importDialog?.open()">
          {{ t('library.importRoutes') }}
        </button>
      </div>
    </div>

    <StatusMessage :message="planner.status.value" />

    <p v-if="loading" class="empty">{{ t('common.loading') }}</p>
    <p v-else-if="failed" class="empty">{{ t('library.unavailable') }}</p>

    <template v-else-if="tab === 'library'">
      <div class="filters">
        <input
          v-model="query"
          type="search"
          :placeholder="t('library.filter.search')"
          :aria-label="t('library.filter.searchLabel')"
        />
        <select v-model="sourceFilter" :aria-label="t('library.filter.which')">
          <option value="all">{{ t('library.filter.all') }}</option>
          <option value="favorites">★ {{ t('library.filter.favorites') }}</option>
          <option value="planned">{{ t('library.filter.planned') }}</option>
          <option value="imported">{{ t('library.filter.imported') }}</option>
        </select>
        <select v-model="sortChoice" :aria-label="t('library.sort.label')">
          <option value="updated:desc">{{ t('library.sort.updatedDesc') }}</option>
          <option value="updated:asc">{{ t('library.sort.updatedAsc') }}</option>
          <option value="name:asc">{{ t('library.sort.nameAsc') }}</option>
          <option value="name:desc">{{ t('library.sort.nameDesc') }}</option>
          <option value="distance:desc">{{ t('library.sort.distanceDesc') }}</option>
          <option value="distance:asc">{{ t('library.sort.distanceAsc') }}</option>
          <option value="ascent:desc">{{ t('library.sort.ascentDesc') }}</option>
          <option value="ascent:asc">{{ t('library.sort.ascentAsc') }}</option>
          <option value="source:asc">{{ t('library.column.source') }}</option>
        </select>
      </div>

      <div v-if="routes.length === 0" class="blank">
        <p>{{ t('library.blank') }}</p>
        <button type="button" class="primary" @click="void importDialog?.open()">
          {{ t('library.importRoutes') }}
        </button>
      </div>
      <p v-else-if="visible.length === 0" class="empty">{{ t('library.noMatch') }}</p>
      <table v-else class="routes">
        <thead>
          <tr>
            <th scope="col" class="star-col">
              <span class="visually-hidden">{{ t('common.favorite') }}</span>
            </th>
            <SortableHeader
              :label="t('library.column.name')"
              column="name"
              :sort-key="sort.key"
              :sort-dir="sort.dir"
              @sort="sortBy"
            />
            <SortableHeader
              :label="t('library.column.distance')"
              column="distance"
              numeric
              :sort-key="sort.key"
              :sort-dir="sort.dir"
              @sort="sortBy"
            />
            <SortableHeader
              :label="t('library.column.climbing')"
              column="ascent"
              numeric
              :sort-key="sort.key"
              :sort-dir="sort.dir"
              @sort="sortBy"
            />
            <SortableHeader
              :label="t('library.column.source')"
              column="source"
              class="hide-narrow"
              :sort-key="sort.key"
              :sort-dir="sort.dir"
              @sort="sortBy"
            />
            <SortableHeader
              :label="t('library.column.changed')"
              column="updated"
              class="hide-narrow"
              :sort-key="sort.key"
              :sort-dir="sort.dir"
              @sort="sortBy"
            />
            <th scope="col">
              <span class="visually-hidden">{{ t('library.column.actions') }}</span>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in visible" :key="r.id" :class="{ current: r.id === planner.routeId.value }">
            <td class="star-col">
              <StarToggle :starred="r.favorite" :label="r.name" @toggle="toggleFavorite(r)" />
            </td>
            <td class="name-cell">
              <input
                v-if="renamingId === r.id"
                ref="renameInput"
                v-model="renameText"
                type="text"
                maxlength="120"
                :aria-label="t('library.newName')"
                @keydown.enter.prevent="finishRename(r)"
                @keydown.esc.prevent="renamingId = null"
                @blur="finishRename(r)"
              />
              <button
                v-else
                type="button"
                class="name"
                :title="t('library.row.open', { name: r.name })"
                @click="open(r.id)"
              >
                {{ r.name }}
              </button>
              <Badge v-if="!r.editable" class="original" :title="t('library.originalTitle')">{{
                t('library.original')
              }}</Badge>
            </td>
            <td class="num">{{ km(r.distance) }} {{ t('common.unit.km') }}</td>
            <td class="num">{{ num(Math.round(r.ascent)) }} {{ t('common.unit.m') }}</td>
            <td class="hide-narrow">{{ sourceLabel(r.source) }}</td>
            <td class="hide-narrow">{{ day(r.updatedAt) }}</td>
            <td class="actions">
              <button type="button" @click="open(r.id)">{{ t('common.action.open') }}</button>
              <IconButton :label="t('library.row.download', { name: r.name })" @click="download(r)"
                >GPX</IconButton
              >
              <IconButton :label="t('library.row.rename', { name: r.name })" @click="startRename(r)"
                >✎</IconButton
              >
              <IconButton :label="t('library.row.duplicate', { name: r.name })" @click="duplicate(r)"
                >⧉</IconButton
              >
              <IconButton :label="t('library.row.archive', { name: r.name })" @click="archive(r)">{{
                t('common.action.archive')
              }}</IconButton>
              <IconButton :label="t('library.row.trash', { name: r.name })" @click="moveToTrash(r)"
                >✕</IconButton
              >
            </td>
          </tr>
        </tbody>
      </table>
    </template>

    <template v-else-if="tab === 'archive'">
      <p class="empty">
        {{ t('library.archiveIntro') }}
      </p>
      <table v-if="archived.length" class="routes">
        <thead>
          <tr>
            <th scope="col" class="star-col">
              <span class="visually-hidden">{{ t('common.favorite') }}</span>
            </th>
            <SortableHeader
              :label="t('library.column.name')"
              column="name"
              :sort-key="sort.key"
              :sort-dir="sort.dir"
              @sort="sortBy"
            />
            <SortableHeader
              :label="t('library.column.distance')"
              column="distance"
              numeric
              :sort-key="sort.key"
              :sort-dir="sort.dir"
              @sort="sortBy"
            />
            <SortableHeader
              :label="t('library.column.climbing')"
              column="ascent"
              numeric
              :sort-key="sort.key"
              :sort-dir="sort.dir"
              @sort="sortBy"
            />
            <SortableHeader
              :label="t('library.column.archived')"
              column="archived"
              class="hide-narrow"
              :sort-key="sort.key"
              :sort-dir="sort.dir"
              @sort="sortBy"
            />
            <th scope="col">
              <span class="visually-hidden">{{ t('library.column.actions') }}</span>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in sortedArchive" :key="r.id">
            <td class="star-col">
              <StarToggle :starred="r.favorite" :label="r.name" @toggle="toggleFavorite(r)" />
            </td>
            <td class="name-cell">
              <button
                type="button"
                class="name"
                :title="t('library.row.open', { name: r.name })"
                @click="open(r.id)"
              >
                {{ r.name }}
              </button>
            </td>
            <td class="num">{{ km(r.distance) }} {{ t('common.unit.km') }}</td>
            <td class="num">{{ num(Math.round(r.ascent)) }} {{ t('common.unit.m') }}</td>
            <td class="hide-narrow">{{ day(r.archivedAt) }}</td>
            <td class="actions">
              <button type="button" @click="open(r.id)">{{ t('common.action.open') }}</button>
              <button type="button" @click="unarchive(r)">{{ t('common.action.unarchive') }}</button>
              <IconButton :label="t('library.row.trash', { name: r.name })" @click="moveToTrash(r)"
                >✕</IconButton
              >
            </td>
          </tr>
        </tbody>
      </table>
    </template>

    <template v-else>
      <p class="empty">{{ t('library.trashIntro') }}</p>
      <table v-if="trash.length" class="routes">
        <thead>
          <tr>
            <th scope="col">{{ t('library.column.name') }}</th>
            <th scope="col" class="num">{{ t('library.column.distance') }}</th>
            <th scope="col" class="hide-narrow">{{ t('library.column.deleted') }}</th>
            <th scope="col">
              <span class="visually-hidden">{{ t('library.column.actions') }}</span>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in trash" :key="r.id">
            <td class="name-cell">
              <span class="name static">{{ r.name }}</span>
            </td>
            <td class="num">{{ km(r.distance) }} {{ t('common.unit.km') }}</td>
            <td class="hide-narrow">{{ day(r.deletedAt) }}</td>
            <td class="actions">
              <button type="button" @click="restore(r)">{{ t('common.action.restore') }}</button>
              <button type="button" class="danger" @click="deleteForever(r)">
                {{ t('library.confirm.deleteForever.confirm') }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <button v-if="trash.length" type="button" class="danger empty-trash" @click="emptyTrash">
        {{ t('library.confirm.emptyTrash.confirm') }}
      </button>
    </template>

    <ImportDialog ref="importDialogRef" @imported="onImported" />
  </PageLayout>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin: 4px 0 14px;
  border-bottom: 1px solid var(--line);
}
.toolbar .actions {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.filters {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 8px;
  margin-bottom: 12px;
}
.filters select {
  width: auto;
}
.blank {
  padding: 40px 20px;
  text-align: center;
  color: var(--muted);
  border: 2px dashed var(--line);
  border-radius: 12px;
}
.routes {
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
tr.current .name {
  font-weight: 600;
}
.original {
  margin-left: 8px;
}
td.hide-narrow {
  white-space: nowrap;
}
.star-col {
  width: 1%;
  padding-right: 0;
}
.num {
  text-align: right;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.name-cell {
  max-width: 0;
  width: 45%;
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
.name.static {
  display: block;
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
.danger {
  border-color: var(--warn);
  color: var(--warn);
}
.empty-trash {
  margin-top: 12px;
}
@media (max-width: 720px) {
  .hide-narrow {
    display: none;
  }
  .filters {
    grid-template-columns: 1fr;
  }
  .filters select {
    width: 100%;
  }
}
</style>
