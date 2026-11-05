<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, ApiError } from '@/api'
import * as planner from '@/planner'
import type { WahooStatus } from '@/types'

const busy = ref(false)
const wahoo = ref<WahooStatus>({ enabled: false, connected: false })
const canShareFiles = ref(false)

function fileName() {
  const slug = planner.routeName.value
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .replace(/[^A-Za-z0-9-]+/g, '_')
    .replace(/^_+|_+$/g, '')
  return `${slug || 'route'}.gpx`
}

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

/** Creates or updates the route; recreates it if the saved copy was deleted meanwhile. */
async function saveDraft(asNew = false) {
  const id = planner.routeId.value
  let saved
  if (id === null || asNew) {
    saved = await api.createRoute(planner.draft())
  } else {
    try {
      saved = await api.updateRoute(id, planner.draft())
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 404)) throw error
      saved = await api.createRoute(planner.draft())
    }
  }
  planner.routeId.value = saved.id
  planner.libraryVersion.value++
  return saved
}

const save = (asNew: boolean) =>
  run(async () => {
    const saved = await saveDraft(asNew)
    planner.setStatus(`Saved “${saved.name}”.`, 'info', 2500)
  })

async function gpxFile(): Promise<File> {
  const blob = await api.gpx(planner.draft())
  return new File([blob], fileName(), { type: 'application/gpx+xml' })
}

const download = () =>
  run(async () => {
    const file = await gpxFile()
    const url = URL.createObjectURL(file)
    const link = document.createElement('a')
    link.href = url
    link.download = file.name
    document.body.append(link)
    link.click()
    link.remove()
    setTimeout(() => {
      URL.revokeObjectURL(url)
    }, 2000)
  })

const share = () =>
  run(async () => {
    const file = await gpxFile()
    try {
      await navigator.share({ files: [file], title: planner.routeName.value })
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return
      throw error
    }
  })

const sendToWahoo = () =>
  run(async () => {
    const saved = await saveDraft()
    await api.syncToWahoo(saved.id)
    planner.libraryVersion.value++
    planner.setStatus('Sent to your Wahoo account. Sync the BOLT to load it.', 'info', 4000)
  })

const disconnect = () =>
  run(async () => {
    await api.disconnectWahoo()
    wahoo.value = { ...wahoo.value, connected: false }
  })

onMounted(async () => {
  try {
    const probe = new File(['x'], 'probe.gpx', { type: 'application/gpx+xml' })
    canShareFiles.value = typeof navigator.canShare === 'function' && navigator.canShare({ files: [probe] })
  } catch {
    canShareFiles.value = false
  }
  try {
    wahoo.value = await api.wahooStatus()
  } catch (error) {
    if (!(error instanceof ApiError)) throw error
  }
})
</script>

<template>
  <section aria-labelledby="export-heading">
    <h2 id="export-heading">Save &amp; send to the BOLT</h2>
    <div class="field">
      <label for="route-name">Route name</label>
      <input id="route-name" v-model="planner.routeName.value" type="text" maxlength="120" />
    </div>
    <div class="row">
      <button
        type="button"
        class="primary"
        :disabled="busy || !planner.routeComplete.value"
        @click="save(false)"
      >
        {{ planner.routeId.value === null ? 'Save to library' : 'Save changes' }}
      </button>
      <button
        v-if="planner.routeId.value !== null"
        type="button"
        :disabled="busy || !planner.routeComplete.value"
        @click="save(true)"
      >
        Save as new
      </button>
    </div>
    <div class="row">
      <button type="button" :disabled="busy || !planner.routeComplete.value" @click="download">
        Download GPX
      </button>
      <button
        v-if="canShareFiles"
        type="button"
        :disabled="busy || !planner.routeComplete.value"
        @click="share"
      >
        Share GPX
      </button>
      <template v-if="wahoo.enabled">
        <button
          v-if="wahoo.connected"
          type="button"
          :disabled="busy || !planner.routeComplete.value"
          @click="sendToWahoo"
        >
          Send to Wahoo
        </button>
        <a v-else class="button" href="/api/wahoo/connect">Connect Wahoo</a>
      </template>
    </div>
    <p v-if="wahoo.enabled && wahoo.connected" class="small">
      Wahoo connected. <button type="button" class="link" @click="disconnect">Disconnect</button>
    </p>

    <details>
      <summary>Copy to the BOLT over USB</summary>
      <ol>
        <li>Switch the BOLT on and connect it with a USB data cable.</li>
        <li>
          Copy the GPX into the device’s <code>routes</code> folder. Windows: it appears under “This PC”.
          macOS: use an MTP app such as OpenMTP. Linux: your file manager, or
          <code>scripts/copy-to-bolt.sh</code>.
        </li>
        <li>On the BOLT, open the map page, choose ROUTE, and press SYNC if the route isn’t listed.</li>
      </ol>
    </details>
    <details>
      <summary>Import with the ELEMNT app</summary>
      <p>
        On your phone, open the GPX (or use “Share GPX”) and choose the ELEMNT app. The route syncs to the
        BOLT over Bluetooth or Wi‑Fi.
      </p>
    </details>
  </section>
</template>

<style scoped>
.row + .row {
  margin-top: 8px;
}
.small {
  font-size: 13.5px;
  color: var(--muted);
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
