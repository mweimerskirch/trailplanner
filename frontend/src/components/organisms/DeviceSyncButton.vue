<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/api'
import { askConfirm } from '@/confirm'
import { myDevice } from '@/devices'
import * as planner from '@/planner'
import type { DeviceInfo, SyncPlan } from '@/types'

/**
 * "Sync to ELEMNT": makes the USB-connected device hold exactly the rider's active routes. New and
 * changed routes are copied; files on the device that are not active routes are deleted only after
 * the rider confirms them. Shown only if the rider's device syncs over USB.
 */
const busyId = ref<string | null>(null)
const syncable = computed(() => (myDevice.value?.usbSync ? [myDevice.value] : []))

const { t } = useI18n()

async function confirmRemovals(device: DeviceInfo, plan: SyncPlan): Promise<string[]> {
  if (!plan.remove.length) return []
  const files = plan.remove.map((f) => f.fileName)
  const ok = await askConfirm({
    title: t('library.sync.removeTitle', { n: files.length, device: plan.device }, files.length),
    message: [
      t('library.sync.removeMessage', { folder: device.usbSync?.folder ?? '' }),
      device.usbSync?.removalNote,
    ]
      .filter(Boolean)
      .join(' '),
    details: files,
    confirmLabel: t('library.sync.removeConfirm'),
    cancelLabel: t('library.sync.removeCancel'),
    danger: true,
  })
  return ok ? files : []
}

async function sync(device: DeviceInfo) {
  const label = device.usbSync?.label ?? device.name
  busyId.value = device.id
  try {
    planner.setStatus(t('library.sync.checking', { device: label }), 'info')
    const plan = await api.syncPlan(device.id)
    const remove = await confirmRemovals(device, plan)
    if (!plan.add.length && !plan.update.length && !remove.length) {
      planner.setStatus(
        plan.remove.length
          ? t('library.sync.nothingToCopy', { device: plan.device, n: plan.unchanged }, plan.unchanged)
          : t('library.sync.upToDate', { device: plan.device, n: plan.unchanged }, plan.unchanged),
        'info',
        4000,
      )
      return
    }
    planner.setStatus(t('library.sync.syncing', { device: plan.device }), 'info')
    const result = await api.sync(device.id, remove)
    const parts = [
      result.added && t('library.sync.added', { n: result.added }, result.added),
      result.updated && t('library.sync.updated', { n: result.updated }, result.updated),
      result.removed && t('library.sync.removed', { n: result.removed }, result.removed),
      result.unchanged && t('library.sync.unchanged', { n: result.unchanged }, result.unchanged),
    ].filter(Boolean)
    const notes = [device.usbSync?.afterSync, result.removed ? device.usbSync?.removalNote : null]
    const after = notes
      .filter(Boolean)
      .map((n) => ` ${n ?? ''}`)
      .join('')
    planner.setStatus(
      t('library.sync.done', { device: result.device, parts: parts.join(', ') }) + after,
      'info',
      8000,
    )
  } catch (error) {
    planner.setStatus(
      error instanceof Error ? error.message : t('library.sync.failed', { device: label }),
      'error',
    )
  } finally {
    busyId.value = null
  }
}
</script>

<template>
  <button
    v-for="d in syncable"
    :key="d.id"
    type="button"
    :disabled="busyId !== null"
    :title="t('library.sync.title', { device: d.usbSync?.label ?? '' })"
    @click="sync(d)"
  >
    {{
      busyId === d.id ? t('library.sync.busy') : t('library.sync.button', { device: d.usbSync?.label ?? '' })
    }}
  </button>
</template>
