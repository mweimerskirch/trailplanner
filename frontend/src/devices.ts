import { computed, ref, watch } from 'vue'
import { api } from './api'
import { i18n } from './i18n'
import { currentRider } from './session'
import type { DeviceInfo } from './types'

/** The id of the device for riders without one: plain GPX and FIT files. */
export const FILES_ONLY = 'generic'

/**
 * All devices the server supports, in its order, with texts in the UI language. Reloaded when the
 * rider or the language changes.
 */
export const devices = ref<DeviceInfo[]>([])

async function load() {
  try {
    devices.value = currentRider.value ? await api.devices() : []
  } catch {
    devices.value = []
  }
}

watch([() => currentRider.value?.id, i18n.global.locale], () => void load(), { immediate: true })

/**
 * The chosen rider's device, else files only. Device buttons (sync, ride import) and instructions
 * are shown only for this one.
 */
export const myDevice = computed<DeviceInfo | undefined>(() => {
  const id = currentRider.value?.device ?? FILES_ONLY
  return devices.value.find((d) => d.id === id) ?? devices.value.find((d) => d.id === FILES_ONLY)
})
