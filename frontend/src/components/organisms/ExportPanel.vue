<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/api'
import StarToggle from '@/components/atoms/StarToggle.vue'
import { myDevice as device } from '@/devices'
import { saveFile } from '@/files'
import * as planner from '@/planner'
import type { FormatInfo, RouteDetails } from '@/types'

const { t } = useI18n()

const busy = ref(false)
const canShareFiles = ref(false)

// The formats and instructions are the rider's device's (set in the rider menu).
const ready = computed(() => !busy.value && planner.routeComplete.value)

async function run(task: () => Promise<void>) {
  busy.value = true
  try {
    await task()
  } catch (error) {
    planner.setStatus(error instanceof Error ? error.message : t('planner.export.failed'), 'error')
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
      : await api.renameRoute(id, planner.routeName.value.trim() || t('common.route'))
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
    planner.setStatus(t('planner.export.saved', { name: saved.name }), 'info', 2500)
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

onMounted(() => {
  try {
    const probe = new File(['x'], 'probe.gpx', { type: 'application/gpx+xml' })
    canShareFiles.value = typeof navigator.canShare === 'function' && navigator.canShare({ files: [probe] })
  } catch {
    canShareFiles.value = false
  }
})
</script>

<template>
  <section aria-labelledby="export-heading">
    <h2 id="export-heading">{{ t('planner.export.heading') }}</h2>
    <div class="field">
      <label for="route-name">{{ t('planner.export.routeName') }}</label>
      <div class="name-row">
        <input id="route-name" v-model="planner.routeName.value" type="text" maxlength="120" />
        <StarToggle
          v-if="planner.routeId.value !== null"
          :starred="planner.favorite.value"
          :label="planner.routeName.value || t('planner.export.thisRoute')"
          @toggle="void planner.toggleFavorite()"
        />
      </div>
    </div>
    <div class="row">
      <button type="button" class="primary" :disabled="!ready" @click="save(false)">
        {{
          planner.routeId.value === null
            ? t('planner.export.saveToLibrary')
            : planner.viewOnly.value
              ? t('planner.export.saveName')
              : t('planner.export.saveChanges')
        }}
      </button>
      <button v-if="planner.routeId.value !== null" type="button" :disabled="!ready" @click="save(true)">
        {{ t('planner.export.saveAsNew') }}
      </button>
    </div>

    <template v-if="device">
      <div class="row">
        <button
          v-for="format in device.formats"
          :key="format.id"
          type="button"
          :disabled="!ready"
          @click="download(format)"
        >
          {{ t('planner.export.download', { format: format.label }) }}
        </button>
        <button
          v-if="planner.hasOriginal.value && !planner.viewOnly.value && device.formats[0]"
          type="button"
          :disabled="busy || planner.routeId.value === null"
          :title="t('planner.export.downloadOriginalTitle')"
          @click="download(device.formats[0], true)"
        >
          {{ t('planner.export.downloadOriginal') }}
        </button>
        <button
          v-if="canShareFiles && device.formats[0]"
          type="button"
          :disabled="!ready"
          @click="share(device.formats[0])"
        >
          {{ t('planner.export.share', { format: device.formats[0].label }) }}
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
.row + .row {
  margin-top: 8px;
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
