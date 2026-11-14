<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Avatar from '@/components/atoms/Avatar.vue'
import StatusMessage from '@/components/molecules/StatusMessage.vue'
import PageLayout from '@/components/templates/PageLayout.vue'
import { devices, FILES_ONLY } from '@/devices'
import { currentRider, renameRider, setRiderDevice } from '@/session'
import type { StatusMessage as Status } from '@/planner'
import type { DeviceInfo } from '@/types'

/** The chosen rider's own settings: name and device. Each change is saved right away. */
const { t } = useI18n()

const name = ref(currentRider.value?.name ?? '')
watch(
  () => currentRider.value?.name,
  (n) => {
    name.value = n ?? ''
  },
)
const nameChanged = computed(() => name.value.trim() !== '' && name.value.trim() !== currentRider.value?.name)
const deviceId = computed(() => currentRider.value?.device ?? FILES_ONLY)

const status = ref<Status | null>(null)
let clear: ReturnType<typeof setTimeout> | undefined

async function save(task: () => Promise<void>, done: string) {
  clearTimeout(clear)
  try {
    await task()
    status.value = { text: done, kind: 'info' }
    clear = setTimeout(() => (status.value = null), 2500)
  } catch (error) {
    status.value = { text: error instanceof Error ? error.message : String(error), kind: 'error' }
  }
}

const saveName = () => save(() => renameRider(name.value.trim()), t('profile.name.saved'))
const chooseDevice = (id: string) =>
  save(() => setRiderDevice(id === FILES_ONLY ? null : id), t('profile.device.saved'))

/** One line on what choosing the device gives. */
function summary(device: DeviceInfo): string {
  if (device.id === FILES_ONLY) return t('profile.device.files')
  return device.usbSync ? t('profile.device.usb') : t('profile.device.manual')
}
</script>

<template>
  <PageLayout>
    <div v-if="currentRider" class="profile">
      <header>
        <Avatar :name="currentRider.name" size="lg" />
        <div>
          <h1>{{ t('profile.title') }}</h1>
          <p class="sub">{{ t('profile.sub', { name: currentRider.name }) }}</p>
        </div>
      </header>

      <section aria-labelledby="name-heading">
        <h2 id="name-heading">{{ t('profile.name.heading') }}</h2>
        <form class="name" @submit.prevent="saveName">
          <input v-model="name" type="text" maxlength="60" :aria-label="t('profile.name.heading')" />
          <button type="submit" class="primary" :disabled="!nameChanged">{{ t('profile.name.save') }}</button>
        </form>
      </section>

      <section aria-labelledby="device-heading">
        <h2 id="device-heading">{{ t('profile.device.heading') }}</h2>
        <p class="sub">{{ t('profile.device.sub') }}</p>
        <div class="devices" role="radiogroup" aria-labelledby="device-heading">
          <label v-for="d in devices" :key="d.id" class="device" :class="{ chosen: d.id === deviceId }">
            <input
              type="radio"
              name="device"
              :value="d.id"
              :checked="d.id === deviceId"
              @change="chooseDevice(d.id)"
            />
            <span>
              <span class="device-name">{{ d.name }}</span>
              <span class="device-summary">{{ summary(d) }}</span>
            </span>
          </label>
        </div>
      </section>

      <StatusMessage :message="status" class="saved" />
    </div>
  </PageLayout>
</template>

<style scoped>
.profile {
  max-width: 640px;
}
header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 8px;
}
h1 {
  margin: 0;
}
.sub {
  color: var(--muted);
  margin: 4px 0 0;
}
section {
  margin-top: 28px;
}
.name {
  display: flex;
  gap: 8px;
}
.name input {
  flex: 1;
  max-width: 320px;
}
.devices {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}
.device {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--panel);
  cursor: pointer;
}
.device.chosen {
  border-color: var(--moss);
}
.device input {
  margin-top: 3px;
}
.device-name {
  display: block;
  font-weight: 600;
}
.saved {
  margin-top: 20px;
}
.device-summary {
  display: block;
  color: var(--muted);
  font-size: 14px;
  margin-top: 2px;
}
</style>
