<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '@/api'
import * as planner from '@/planner'
import type { DeviceInfo } from '@/types'

/**
 * "Import from ELEMNT": imports the rides recorded on a USB-connected device that are not imported
 * yet. One button per device that has recorded rides.
 */
const emit = defineEmits<{ imported: [] }>()

const devices = ref<DeviceInfo[]>([])
const busyId = ref<string | null>(null)
const withRides = computed(() => devices.value.filter((d) => d.usbSync?.importsRides))

const plural = (n: number, word: string) => `${String(n)} ${word}${n === 1 ? '' : 's'}`

async function importFrom(device: DeviceInfo) {
  const label = device.usbSync?.label ?? device.name
  busyId.value = device.id
  try {
    planner.setStatus(`Reading the rides on the ${label}… The first import can take a minute.`, 'info')
    const result = await api.importRidesFromDevice(device.id)
    const failed = result.failed.length
      ? ` ${plural(result.failed.length, 'file')} could not be read: ${result.failed.map((f) => f.fileName).join(', ')}.`
      : ''
    const message = result.imported
      ? `Imported ${plural(result.imported, 'new ride')} from the ${result.device}.`
      : `No new rides on the ${result.device}.`
    const empty = result.withoutTrack
      ? ` ${plural(result.withoutTrack, 'recording')} without a GPS track (e.g. started by accident) ${result.withoutTrack === 1 ? 'was' : 'were'} skipped.`
      : ''
    planner.setStatus(message + empty + failed, failed ? 'error' : 'info', failed ? undefined : 6000)
    if (result.imported) emit('imported')
  } catch (error) {
    planner.setStatus(error instanceof Error ? error.message : `Could not read the ${label}.`, 'error')
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
    v-for="d in withRides"
    :key="d.id"
    type="button"
    :disabled="busyId !== null"
    :title="`Import the rides recorded on the ${d.usbSync?.label} connected over USB`"
    @click="importFrom(d)"
  >
    {{ busyId === d.id ? 'Importing…' : `Import from ${d.usbSync?.label}` }}
  </button>
</template>
