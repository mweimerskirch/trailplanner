<script setup lang="ts">
import { computed, nextTick, onMounted, ref, useTemplateRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api'
import ImportDialog from '@/components/ImportDialog.vue'
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
const tab = ref<'library' | 'trash'>('library')
const routes = ref<RouteSummary[]>([])
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
    const [active, trashed] = await Promise.all([api.listRoutes(), api.listTrash()])
    routes.value = active
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
    await router.push('/')
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
    tab.value = 'library'
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
  <div class="library-page">
    <div class="inner">
      <div class="toolbar">
        <div class="tabs" role="tablist">
          <button
            type="button"
            role="tab"
            :aria-selected="tab === 'library'"
            :class="{ active: tab === 'library' }"
            @click="tab = 'library'"
          >
            Routes<span class="count">{{ routes.length }}</span>
          </button>
          <button
            type="button"
            role="tab"
            :aria-selected="tab === 'trash'"
            :class="{ active: tab === 'trash' }"
            @click="tab = 'trash'"
          >
            Trash<span class="count">{{ trash.length }}</span>
          </button>
        </div>
        <button type="button" class="primary" @click="void importDialog?.open()">Import routes…</button>
      </div>

      <div v-if="planner.status.value" class="status" :class="planner.status.value.kind" role="status">
        {{ planner.status.value.text }}
      </div>

      <p v-if="loading" class="empty">Loading…</p>
      <p v-else-if="failed" class="empty">The library is unavailable. Is the backend running?</p>

      <template v-else-if="tab === 'library'">
        <div class="filters">
          <input
            v-model="query"
            type="search"
            placeholder="Search routes"
            aria-label="Search routes by name"
          />
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
          <p>
            No routes yet. Plan one on the map and save it, or import routes from Komoot or GPX/FIT files.
          </p>
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
                <span v-if="!r.editable" class="badge" title="Imported track, not edited yet"
                  >original track</span
                >
              </td>
              <td class="num">{{ km(r.distance) }} km</td>
              <td class="num">{{ Math.round(r.ascent) }} m</td>
              <td class="hide-narrow">{{ SOURCE_LABELS[r.source] }}</td>
              <td class="hide-narrow">{{ day(r.updatedAt) }}</td>
              <td class="actions">
                <button type="button" @click="open(r.id)">Open</button>
                <button
                  type="button"
                  title="Download GPX"
                  :aria-label="`Download ${r.name} as GPX`"
                  @click="download(r)"
                >
                  GPX
                </button>
                <button type="button" title="Rename" :aria-label="`Rename ${r.name}`" @click="startRename(r)">
                  ✎
                </button>
                <button
                  type="button"
                  title="Duplicate"
                  :aria-label="`Duplicate ${r.name}`"
                  @click="duplicate(r)"
                >
                  ⧉
                </button>
                <button
                  type="button"
                  title="Move to trash"
                  :aria-label="`Move ${r.name} to the trash`"
                  @click="moveToTrash(r)"
                >
                  ✕
                </button>
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
    </div>

    <ImportDialog ref="importDialogRef" @imported="onImported" />
  </div>
</template>

<style scoped>
.library-page {
  min-height: 100%;
  background: var(--bg);
  overflow-y: auto;
  height: 100%;
}
.inner {
  max-width: 1100px;
  margin: 0 auto;
  padding: 20px 24px 60px;
}
.toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin: 4px 0 14px;
  border-bottom: 1px solid var(--line);
}
.tabs {
  display: flex;
  gap: 4px;
}
.tabs button {
  border: none;
  border-bottom: 3px solid transparent;
  border-radius: 0;
  background: none;
  padding: 8px 12px;
  font-family: var(--font-display);
  font-size: 19px;
  font-weight: 600;
}
.tabs button.active {
  border-bottom-color: var(--moss);
}
.count {
  margin-left: 6px;
  padding: 0 7px;
  border-radius: 10px;
  background: var(--line);
  font-family: var(--font-body);
  font-size: 12.5px;
  font-weight: 500;
  vertical-align: middle;
}
.toolbar .primary {
  margin-bottom: 8px;
}
.status {
  margin: 0 0 12px;
  padding: 8px 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--panel);
}
.status.error {
  border-color: var(--warn);
  color: var(--warn);
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
.badge {
  display: inline-block;
  margin-left: 8px;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--line);
  color: var(--muted);
  font-size: 11.5px;
  line-height: 18px;
  white-space: nowrap;
}
.actions {
  text-align: right;
  white-space: nowrap;
}
.actions button {
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
  .inner {
    padding: 16px 12px 40px;
  }
}
</style>
