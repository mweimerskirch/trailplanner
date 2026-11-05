<script setup lang="ts">
import { computed, nextTick, onMounted, ref, useTemplateRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api'
import Badge from '@/components/atoms/Badge.vue'
import IconButton from '@/components/atoms/IconButton.vue'
import StatusMessage from '@/components/molecules/StatusMessage.vue'
import TabBar from '@/components/molecules/TabBar.vue'
import ImportDialog from '@/components/organisms/ImportDialog.vue'
import PageLayout from '@/components/templates/PageLayout.vue'
import * as planner from '@/planner'
import type { RouteSource, RouteSummary } from '@/types'

const SOURCE_LABELS: Record<RouteSource, string> = {
  PLANNER: 'Planned',
  GPX: 'GPX file',
  FIT: 'FIT file',
  KOMOOT: 'Komoot',
}

type Sort = 'updated' | 'name' | 'distance' | 'ascent'
type SourceFilter = 'all' | 'planned' | 'imported'

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
const sort = ref<Sort>('updated')
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
      (sourceFilter.value === 'all' || (r.source === 'PLANNER') === (sourceFilter.value === 'planned')),
  )
  const by: Record<Sort, (a: RouteSummary, b: RouteSummary) => number> = {
    updated: (a, b) => b.updatedAt.localeCompare(a.updatedAt),
    name: (a, b) => a.name.localeCompare(b.name),
    distance: (a, b) => b.distance - a.distance,
    ascent: (a, b) => b.ascent - a.ascent,
  }
  return [...list].sort(by[sort.value])
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

async function act(task: () => Promise<void>, failure: string) {
  try {
    await task()
  } catch (error) {
    planner.setStatus(error instanceof Error ? error.message : failure, 'error')
  }
}

const open = (id: number) =>
  act(async () => {
    planner.openRoute(await api.getRoute(id))
    await router.push(`/routes/${String(id)}`)
  }, 'Could not open the route.')

async function startRename(route: RouteSummary) {
  renamingId.value = route.id
  renameText.value = route.name
  await nextTick()
  renameInput.value[0]?.select()
}

const finishRename = (route: RouteSummary) =>
  act(async () => {
    const name = renameText.value.trim()
    if (renamingId.value !== route.id) return
    renamingId.value = null
    if (!name || name === route.name) return
    const renamed = await api.renameRoute(route.id, name)
    if (planner.routeId.value === route.id) planner.routeName.value = renamed.name
    planner.libraryVersion.value++
  }, 'Could not rename the route.')

const duplicate = (route: RouteSummary) =>
  act(async () => {
    const copy = await api.duplicateRoute(route.id)
    planner.setStatus(`Created “${copy.name}”.`, 'info', 2500)
    planner.libraryVersion.value++
  }, 'Could not duplicate the route.')

const download = (route: RouteSummary) =>
  act(async () => {
    const blob = await api.exportSaved(route.id, 'gpx')
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${route.name.replace(/[^\p{L}\p{N} _-]+/gu, '_') || 'route'}.gpx`
    document.body.append(link)
    link.click()
    link.remove()
    setTimeout(() => {
      URL.revokeObjectURL(url)
    }, 2000)
  }, 'Could not download the route.')

const archive = (route: RouteSummary) =>
  act(async () => {
    await api.archiveRoute(route.id)
    planner.setStatus(`Archived “${route.name}”. Find it under Archived.`, 'info', 3000)
    planner.libraryVersion.value++
  }, 'Could not archive the route.')

const unarchive = (route: RouteSummary) =>
  act(async () => {
    await api.unarchiveRoute(route.id)
    planner.setStatus(`“${route.name}” is back in your routes.`, 'info', 2500)
    planner.libraryVersion.value++
  }, 'Could not unarchive the route.')

const moveToTrash = (route: RouteSummary) =>
  act(async () => {
    await api.deleteRoute(route.id)
    if (planner.routeId.value === route.id) planner.routeId.value = null
    planner.setStatus(`Moved “${route.name}” to the trash.`, 'info', 3000)
    planner.libraryVersion.value++
  }, 'Could not delete the route.')

const restore = (route: RouteSummary) =>
  act(async () => {
    await api.restoreRoute(route.id)
    planner.setStatus(`Restored “${route.name}”.`, 'info', 2500)
    planner.libraryVersion.value++
  }, 'Could not restore the route.')

const deleteForever = (route: RouteSummary) =>
  act(async () => {
    if (!confirm(`Delete “${route.name}” for good? This cannot be undone.`)) return
    await api.deleteRoutePermanently(route.id)
    planner.libraryVersion.value++
  }, 'Could not delete the route.')

const emptyTrash = () =>
  act(async () => {
    const n = trash.value.length
    if (!confirm(`Delete ${n} route${n === 1 ? '' : 's'} in the trash for good? This cannot be undone.`)) {
      return
    }
    await api.emptyTrash()
    planner.libraryVersion.value++
    setTab('library')
  }, 'Could not empty the trash.')

function onImported(openId: number | null) {
  planner.libraryVersion.value++
  if (openId !== null) void open(openId)
}

const km = (m: number) => (m / 1000).toFixed(1)
const day = (iso: string | null) => (iso ? new Date(iso).toLocaleDateString() : '')

onMounted(load)
watch(planner.libraryVersion, load)
</script>

<template>
  <PageLayout>
    <div class="toolbar">
      <TabBar
        large
        :tabs="[
          { id: 'library', label: 'Routes', count: routes.length },
          { id: 'archive', label: 'Archived', count: archived.length },
          { id: 'trash', label: 'Trash', count: trash.length },
        ]"
        :model-value="tab"
        @update:model-value="setTab($event as Tab)"
      />
      <button type="button" class="primary" @click="void importDialog?.open()">Import routes…</button>
    </div>

    <StatusMessage :message="planner.status.value" />

    <p v-if="loading" class="empty">Loading…</p>
    <p v-else-if="failed" class="empty">The library is unavailable. Is the backend running?</p>

    <template v-else-if="tab === 'library'">
      <div class="filters">
        <input v-model="query" type="search" placeholder="Search routes" aria-label="Search routes by name" />
        <select v-model="sourceFilter" aria-label="Which routes">
          <option value="all">All routes</option>
          <option value="planned">Planned in Trailplan</option>
          <option value="imported">Imported</option>
        </select>
        <select v-model="sort" aria-label="Sort by">
          <option value="updated">Last changed</option>
          <option value="name">Name</option>
          <option value="distance">Longest first</option>
          <option value="ascent">Most climbing first</option>
        </select>
      </div>

      <div v-if="routes.length === 0" class="blank">
        <p>No routes yet. Plan one on the map and save it, or import routes from Komoot or GPX/FIT files.</p>
        <button type="button" class="primary" @click="void importDialog?.open()">Import routes…</button>
      </div>
      <p v-else-if="visible.length === 0" class="empty">No route matches these filters.</p>
      <table v-else class="routes">
        <thead>
          <tr>
            <th scope="col">Name</th>
            <th scope="col" class="num">Distance</th>
            <th scope="col" class="num">Climbing</th>
            <th scope="col" class="hide-narrow">Source</th>
            <th scope="col" class="hide-narrow">Changed</th>
            <th scope="col"><span class="visually-hidden">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in visible" :key="r.id" :class="{ current: r.id === planner.routeId.value }">
            <td class="name-cell">
              <input
                v-if="renamingId === r.id"
                ref="renameInput"
                v-model="renameText"
                type="text"
                maxlength="120"
                aria-label="New name"
                @keydown.enter.prevent="finishRename(r)"
                @keydown.esc.prevent="renamingId = null"
                @blur="finishRename(r)"
              />
              <button
                v-else
                type="button"
                class="name"
                :title="`Open ${r.name} in the planner`"
                @click="open(r.id)"
              >
                {{ r.name }}
              </button>
              <Badge v-if="!r.editable" class="original" title="Imported track, not edited yet"
                >original track</Badge
              >
            </td>
            <td class="num">{{ km(r.distance) }} km</td>
            <td class="num">{{ Math.round(r.ascent) }} m</td>
            <td class="hide-narrow">{{ SOURCE_LABELS[r.source] }}</td>
            <td class="hide-narrow">{{ day(r.updatedAt) }}</td>
            <td class="actions">
              <button type="button" @click="open(r.id)">Open</button>
              <IconButton :label="`Download ${r.name} as GPX`" @click="download(r)">GPX</IconButton>
              <IconButton :label="`Rename ${r.name}`" @click="startRename(r)">✎</IconButton>
              <IconButton :label="`Duplicate ${r.name}`" @click="duplicate(r)">⧉</IconButton>
              <IconButton :label="`Archive ${r.name}`" @click="archive(r)">Archive</IconButton>
              <IconButton :label="`Move ${r.name} to the trash`" @click="moveToTrash(r)">✕</IconButton>
            </td>
          </tr>
        </tbody>
      </table>
    </template>

    <template v-else-if="tab === 'archive'">
      <p class="empty">
        Routes you no longer want in your list, kept for good. The trash never touches them.
      </p>
      <table v-if="archived.length" class="routes">
        <thead>
          <tr>
            <th scope="col">Name</th>
            <th scope="col" class="num">Distance</th>
            <th scope="col" class="num">Climbing</th>
            <th scope="col" class="hide-narrow">Archived</th>
            <th scope="col"><span class="visually-hidden">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in archived" :key="r.id">
            <td class="name-cell">
              <button type="button" class="name" :title="`Open ${r.name} in the planner`" @click="open(r.id)">
                {{ r.name }}
              </button>
            </td>
            <td class="num">{{ km(r.distance) }} km</td>
            <td class="num">{{ Math.round(r.ascent) }} m</td>
            <td class="hide-narrow">{{ day(r.archivedAt) }}</td>
            <td class="actions">
              <button type="button" @click="open(r.id)">Open</button>
              <button type="button" @click="unarchive(r)">Unarchive</button>
              <IconButton :label="`Move ${r.name} to the trash`" @click="moveToTrash(r)">✕</IconButton>
            </td>
          </tr>
        </tbody>
      </table>
    </template>

    <template v-else>
      <p class="empty">Deleted routes stay here until you restore them or empty the trash.</p>
      <table v-if="trash.length" class="routes">
        <thead>
          <tr>
            <th scope="col">Name</th>
            <th scope="col" class="num">Distance</th>
            <th scope="col" class="hide-narrow">Deleted</th>
            <th scope="col"><span class="visually-hidden">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in trash" :key="r.id">
            <td class="name-cell">
              <span class="name static">{{ r.name }}</span>
            </td>
            <td class="num">{{ km(r.distance) }} km</td>
            <td class="hide-narrow">{{ day(r.deletedAt) }}</td>
            <td class="actions">
              <button type="button" @click="restore(r)">Restore</button>
              <button type="button" class="danger" @click="deleteForever(r)">Delete for good</button>
            </td>
          </tr>
        </tbody>
      </table>
      <button v-if="trash.length" type="button" class="danger empty-trash" @click="emptyTrash">
        Empty trash
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
.toolbar .primary {
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
