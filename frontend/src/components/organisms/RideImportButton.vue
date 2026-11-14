<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/api'
import * as planner from '@/planner'
import type { DeviceInfo } from '@/types'

/**
 * "Import from ELEMNT": imports the rides recorded on a USB-connected device that are not imported
 * yet. One button per device that has recorded rides.
 */
const emit = defineEmits<{ imported: [] }>()

const { t, locale } = useI18n()
const devices = ref<DeviceInfo[]>([])
const busyId = ref<string | null>(null)
const withRides = computed(() => devices.value.filter((d) => d.usbSync?.importsRides))

async function importFrom(device: DeviceInfo) {
  const label = device.usbSync?.label ?? device.name
  busyId.value = device.id
  try {
    planner.setStatus(t('rides.device.reading', { device: label }), 'info')
    const result = await api.importRidesFromDevice(device.id)
    const n = result.failed.length
    const failed = n
      ? t('rides.device.failed', { n, files: result.failed.map((f) => f.fileName).join(', ') }, n)
      : ''
    const message = result.imported
      ? t('rides.device.imported', { n: result.imported, device: result.device }, result.imported)
      : t('rides.device.none', { device: result.device })
    const empty = result.withoutTrack
      ? t('rides.device.withoutTrack', { n: result.withoutTrack }, result.withoutTrack)
      : ''
    const text = [message, empty, failed].filter(Boolean).join(' ')
    planner.setStatus(text, failed ? 'error' : 'info', failed ? undefined : 6000)
    if (result.imported) emit('imported')
  } catch (error) {
    planner.setStatus(
      error instanceof Error ? error.message : t('rides.device.error', { device: label }),
      'error',
    )
  } finally {
    busyId.value = null
  }
}

async function loadDevices() {
  try {
    devices.value = await api.devices()
  } catch {
    devices.value = []
  }
}

onMounted(loadDevices)
// Device texts come from the server in the chosen language.
watch(locale, () => void loadDevices())
</script>

<template>
  <button
    v-for="d in withRides"
    :key="d.id"
    type="button"
    :disabled="busyId !== null"
    :title="t('rides.device.hint', { device: d.usbSync?.label ?? d.name })"
    @click="importFrom(d)"
  >
    {{
      busyId === d.id
        ? t('rides.importing')
        : t('rides.device.button', { device: d.usbSync?.label ?? d.name })
    }}
  </button>
</template>
