<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { api, ApiError } from '@/api'
import StarToggle from '@/components/atoms/StarToggle.vue'
import { saveFile } from '@/files'
import * as planner from '@/planner'
import type { DeviceInfo, FormatInfo, RouteDetails } from '@/types'

const DEVICE_KEY = 'trailplan.device'

const busy = ref(false)
const devices = ref<DeviceInfo[]>([])
const deviceId = ref(localStorage.getItem(DEVICE_KEY) ?? '')
const canShareFiles = ref(false)

/** The chosen device; the first one (the backend's order) if nothing was chosen yet. */
const device = computed(() => devices.value.find((d) => d.id === deviceId.value) ?? devices.value[0])
const ready = computed(() => !busy.value && planner.routeComplete.value)

watch(deviceId, (id) => {
  localStorage.setItem(DEVICE_KEY, id)
})

async function run(task: () => Promise<void>) {
  busy.value = true
  try {
    await task()
  } catch (error) {
    planner.setStatus(error instanceof Error ? error.message : 'Something went wrong.', 'error')
  } finally {
    busy.value = false
  }
}

/**
 * Creates or updates the route. A save that would overwrite a newer version (saved elsewhere) or a
 * route in the trash is refused by the server with a message; nothing is recreated behind the
 * rider's back. Imported routes in view mode have no waypoints to save, so only their name is.
 */
async function saveDraft(asNew = false): Promise<RouteDetails> {
  const id = planner.routeId.value
  let saved: RouteDetails
  if (planner.viewOnly.value && id !== null) {
    saved = asNew
      ? await api.duplicateRoute(id)
      : await api.renameRoute(id, planner.routeName.value.trim() || 'Route')
  } else if (id === null || asNew) {
    saved = await api.createRoute(planner.draft())
  } else {
    saved = await api.updateRoute(id, { ...planner.draft(), expectedUpdatedAt: planner.routeUpdatedAt.value })
  }
  planner.savedAs(saved)
  planner.libraryVersion.value++
  return saved
}

const save = (asNew: boolean) =>
  run(async () => {
    const saved = await saveDraft(asNew)
    planner.setStatus(`Saved “${saved.name}”.`, 'info', 2500)
  })

/** Imported routes in view mode are exported as stored; planned ones are recalculated. */
async function file(format: FormatInfo, original = false): Promise<File> {
  const id = planner.routeId.value
  return (planner.viewOnly.value || original) && id !== null
    ? await api.exportSaved(id, format.id, original)
    : await api.exportDraft(format.id, planner.draft())
}

const download = (format: FormatInfo, original = false) =>
  run(async () => {
    saveFile(await file(format, original))
  })

const share = (format: FormatInfo) =>
  run(async () => {
    const f = await file(format)
    try {
      await navigator.share({ files: [f], title: planner.routeName.value })
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return
      throw error
    }
  })

async function loadDevices() {
  try {
    devices.value = await api.devices()
    // Nothing chosen yet (or a device that no longer exists): show the first one as selected.
    const first = devices.value[0]
    if (first && !devices.value.some((d) => d.id === deviceId.value)) deviceId.value = first.id
  } catch (error) {
    if (!(error instanceof ApiError)) throw error
  }
}

onMounted(async () => {
  try {
    const probe = new File(['x'], 'probe.gpx', { type: 'application/gpx+xml' })
    canShareFiles.value = typeof navigator.canShare === 'function' && navigator.canShare({ files: [probe] })
  } catch {
    canShareFiles.value = false
  }
  await loadDevices()
})
</script>

<template>
  <section aria-labelledby="export-heading">
    <h2 id="export-heading">Save &amp; send</h2>
    <div class="field">
      <label for="route-name">Route name</label>
      <div class="name-row">
        <input id="route-name" v-model="planner.routeName.value" type="text" maxlength="120" />
        <StarToggle
          v-if="planner.routeId.value !== null"
          :starred="planner.favorite.value"
          :label="planner.routeName.value || 'this route'"
          @toggle="void planner.toggleFavorite()"
        />
      </div>
    </div>
    <div class="row">
      <button type="button" class="primary" :disabled="!ready" @click="save(false)">
        {{
          planner.routeId.value === null
            ? 'Save to library'
            : planner.viewOnly.value
              ? 'Save name'
              : 'Save changes'
        }}
      </button>
      <button v-if="planner.routeId.value !== null" type="button" :disabled="!ready" @click="save(true)">
        Save as new
      </button>
    </div>

    <template v-if="device">
      <div v-if="devices.length > 1" class="field device">
        <label for="device">Send to</label>
        <select id="device" v-model="deviceId">
          <option v-for="d in devices" :key="d.id" :value="d.id">{{ d.name }}</option>
        </select>
      </div>
      <div class="row">
        <button
          v-for="format in device.formats"
          :key="format.id"
          type="button"
          :disabled="!ready"
          @click="download(format)"
        >
          Download {{ format.label }}
        </button>
        <button
          v-if="planner.hasOriginal.value && !planner.viewOnly.value && device.formats[0]"
          type="button"
          :disabled="busy || planner.routeId.value === null"
          title="The track as imported, before your edits"
          @click="download(device.formats[0], true)"
        >
          Download original
        </button>
        <button
          v-if="canShareFiles && device.formats[0]"
          type="button"
          :disabled="!ready"
          @click="share(device.formats[0])"
        >
          Share {{ device.formats[0].label }}
        </button>
      </div>

      <details v-for="method in device.transferMethods" :key="method.title">
        <summary>{{ method.title }}</summary>
        <ol>
          <li v-for="(step, i) in method.steps" :key="i">{{ step }}</li>
        </ol>
      </details>
    </template>
  </section>
</template>

<style scoped>
.name-row {
  display: flex;
  align-items: center;
  gap: 4px;
}
.row + .row,
.device + .row {
  margin-top: 8px;
}
.device {
  margin-top: 12px;
}
details {
  margin-top: 12px;
}
details ol {
  padding-left: 20px;
  margin: 8px 0;
}
details li {
  margin-bottom: 6px;
}
</style>
