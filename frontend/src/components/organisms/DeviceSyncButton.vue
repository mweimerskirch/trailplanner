<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '@/api'
import { askConfirm } from '@/confirm'
import * as planner from '@/planner'
import type { DeviceInfo, SyncPlan } from '@/types'

/**
 * "Sync to ELEMNT": makes the USB-connected device hold exactly the rider's active routes. New and
 * changed routes are copied; files on the device that are not active routes are deleted only after
 * the rider confirms them.
 */
const devices = ref<DeviceInfo[]>([])
const busyId = ref<string | null>(null)
const syncable = computed(() => devices.value.filter((d) => d.usbSync))

const plural = (n: number, word: string) => `${String(n)} ${word}${n === 1 ? '' : 's'}`

async function confirmRemovals(device: DeviceInfo, plan: SyncPlan): Promise<string[]> {
  if (!plan.remove.length) return []
  const files = plan.remove.map((f) => f.fileName)
  const ok = await askConfirm({
    title: `Delete ${plural(files.length, 'file')} from the ${plan.device}?`,
    message:
      `These files in the ${device.usbSync?.folder ?? ''} folder are not active routes in ` +
      'Trailplan (they may be archived, deleted or from another app). New and changed routes are ' +
      'copied either way.' +
      (device.usbSync?.removalNote ? ` ${device.usbSync.removalNote}` : ''),
    details: files,
    confirmLabel: 'Delete and sync',
    cancelLabel: 'Keep them',
    danger: true,
  })
  return ok ? files : []
}

async function sync(device: DeviceInfo) {
  const label = device.usbSync?.label ?? device.name
  busyId.value = device.id
  try {
    planner.setStatus(`Checking the ${label}…`, 'info')
    const plan = await api.syncPlan(device.id)
    const remove = await confirmRemovals(device, plan)
    if (!plan.add.length && !plan.update.length && !remove.length) {
      planner.setStatus(
        plan.remove.length
          ? `Nothing to copy: the ${plan.device} has all ${plural(plan.unchanged, 'active route')}.`
          : `The ${plan.device} is up to date (${plural(plan.unchanged, 'route')}).`,
        'info',
        4000,
      )
      return
    }
    planner.setStatus(`Syncing the ${plan.device}…`, 'info')
    const result = await api.sync(device.id, remove)
    const parts = [
      result.added && `${String(result.added)} added`,
      result.updated && `${String(result.updated)} updated`,
      result.removed && `${String(result.removed)} deleted`,
      result.unchanged && `${String(result.unchanged)} unchanged`,
    ].filter(Boolean)
    const notes = [device.usbSync?.afterSync, result.removed ? device.usbSync?.removalNote : null]
    const after = notes
      .filter(Boolean)
      .map((n) => ` ${n ?? ''}`)
      .join('')
    planner.setStatus(`Synced the ${result.device}: ${parts.join(', ')}.${after}`, 'info', 8000)
  } catch (error) {
    planner.setStatus(error instanceof Error ? error.message : `Could not sync the ${label}.`, 'error')
  } finally {
    busyId.value = null
  }
}

onMounted(async () => {
  try {
    devices.value = await api.devices()
  } catch {
    devices.value = []
  }
})
</script>

<template>
  <button
    v-for="d in syncable"
    :key="d.id"
    type="button"
    :disabled="busyId !== null"
    :title="`Copy all active routes to the ${d.usbSync?.label} connected over USB`"
    @click="sync(d)"
  >
    {{ busyId === d.id ? 'Syncing…' : `Sync to ${d.usbSync?.label}` }}
  </button>
</template>
