<script setup lang="ts">
import { computed, nextTick, ref, useTemplateRef, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api } from '@/api'
import StatTile from '@/components/molecules/StatTile.vue'
import StatusMessage from '@/components/molecules/StatusMessage.vue'
import ElevationChart from '@/components/organisms/ElevationChart.vue'
import TrackMap from '@/components/organisms/TrackMap.vue'
import PageLayout from '@/components/templates/PageLayout.vue'
import { askConfirm } from '@/confirm'
import { saveFile } from '@/files'
import { duration, km, kmh, rideDate } from '@/format'
import * as planner from '@/planner'
import { trackProfile } from '@/profile'
import type { RideDetails } from '@/types'

/** One recorded ride: its track on the map and its figures. */
const route = useRoute()
const router = useRouter()
const details = ref<RideDetails | null>(null)
const error = ref<string | null>(null)
const busy = ref(false)
const renaming = ref(false)
const nameText = ref('')
const nameInput = useTemplateRef<HTMLInputElement>('nameInput')

const ride = computed(() => details.value?.ride ?? null)
const profile = computed(() => trackProfile(details.value?.track ?? []))
const hasElevation = computed(() => profile.value.some((p) => p.ele !== null))
const hoverIndex = ref<number | null>(null)
const hoverPoint = computed(() =>
  hoverIndex.value === null ? null : (profile.value[hoverIndex.value] ?? null),
)

/** The figures shown as tiles; those the device did not record are left out. */
const tiles = computed(() => {
  const r = ride.value
  if (!r) return []
  const all: { value: string; label: string; show: boolean }[] = [
    { value: km(r.distance), label: 'km', show: true },
    { value: duration(r.moving), label: 'moving', show: true },
    { value: duration(r.elapsed), label: 'elapsed', show: Math.abs(r.elapsed - r.moving) >= 60 },
    { value: String(Math.round(r.ascent)), label: 'm up', show: true },
    { value: String(Math.round(r.descent)), label: 'm down', show: true },
    { value: r.avgSpeed === null ? '' : kmh(r.avgSpeed), label: 'km/h avg', show: r.avgSpeed !== null },
    { value: r.maxSpeed === null ? '' : kmh(r.maxSpeed), label: 'km/h max', show: r.maxSpeed !== null },
    { value: String(r.avgHeartRate ?? ''), label: 'bpm avg', show: r.avgHeartRate !== null },
    { value: String(r.maxHeartRate ?? ''), label: 'bpm max', show: r.maxHeartRate !== null },
    { value: String(r.avgPower ?? ''), label: 'W avg', show: r.avgPower !== null },
    { value: String(r.calories ?? ''), label: 'kcal', show: r.calories !== null },
  ]
  return all.filter((t) => t.show)
})

async function load() {
  const id = Number(route.params.id)
  details.value = null
  hoverIndex.value = null
  error.value = null
  try {
    details.value = await api.getRide(id)
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'The ride is unavailable.'
  }
}

async function act(task: () => Promise<void>, failure: string) {
  busy.value = true
  try {
    await task()
  } catch (e) {
    planner.setStatus(e instanceof Error ? e.message : failure, 'error')
  } finally {
    busy.value = false
  }
}

async function startRename() {
  if (!ride.value) return
  nameText.value = ride.value.name
  renaming.value = true
  await nextTick()
  nameInput.value?.select()
}

async function finishRename() {
  if (!renaming.value || !details.value) return
  renaming.value = false
  const name = nameText.value.trim()
  if (!name || name === details.value.ride.name) return
  await act(async () => {
    if (!details.value) return
    details.value.ride = await api.renameRide(details.value.ride.id, name)
  }, 'Could not rename the ride.')
}

const saveAsRoute = () =>
  act(async () => {
    if (!details.value) return
    const result = await api.saveRideAsRoute(details.value.ride.id)
    details.value.routeId = result.routeId
    planner.libraryVersion.value++
    planner.setStatus(
      result.status === 'IMPORTED' ? `Added “${result.name ?? ''}” to the library.` : (result.message ?? ''),
      'info',
      4000,
    )
  }, 'Could not add the ride to the library.')

const download = () =>
  act(async () => {
    if (ride.value) saveFile(await api.rideFile(ride.value.id))
  }, 'Could not download the ride.')

async function remove() {
  const r = ride.value
  if (!r) return
  const ok = await askConfirm({
    title: `Delete “${r.name}”?`,
    message:
      'The ride is deleted from Trailplan, not from the device. Importing from the device will not ' +
      'bring it back; uploading its FIT file will.',
    confirmLabel: 'Delete ride',
    danger: true,
  })
  if (!ok) return
  await act(async () => {
    await api.deleteRide(r.id)
    await router.push('/rides')
  }, 'Could not delete the ride.')
}

watch(() => route.params.id, load, { immediate: true })
</script>

<template>
  <PageLayout>
    <RouterLink to="/rides" class="back">← Rides</RouterLink>

    <p v-if="error" class="empty">{{ error }}</p>
    <p v-else-if="!details || !ride" class="empty">Loading…</p>
    <template v-else>
      <header class="head">
        <div class="title">
          <input
            v-if="renaming"
            ref="nameInput"
            v-model="nameText"
            type="text"
            maxlength="120"
            aria-label="Ride name"
            @keydown.enter.prevent="finishRename"
            @keydown.esc.prevent="renaming = false"
            @blur="finishRename"
          />
          <h1 v-else>
            <button type="button" class="name" title="Rename" @click="startRename">{{ ride.name }}</button>
          </h1>
          <p class="when">{{ rideDate(ride.startedAt, ride.utcOffset) }}</p>
        </div>
        <div class="actions">
          <RouterLink
            v-if="details.routeId !== null"
            :to="`/routes/${String(details.routeId)}`"
            class="button"
          >
            Open as route
          </RouterLink>
          <button
            v-else
            type="button"
            class="primary"
            :disabled="busy"
            title="Add this track to the library, to ride it again or edit it"
            @click="saveAsRoute"
          >
            Save as route
          </button>
          <button type="button" :disabled="busy" title="The FIT file as recorded" @click="download">
            Download FIT
          </button>
          <button type="button" class="danger" :disabled="busy" @click="remove">Delete</button>
        </div>
      </header>

      <StatusMessage :message="planner.status.value" />

      <div class="stats">
        <StatTile v-for="t in tiles" :key="t.label" :value="t.value" :label="t.label" />
      </div>

      <div class="map">
        <TrackMap :track="details.track" :hover-point="hoverPoint" />
      </div>
      <ElevationChart
        v-if="hasElevation && profile.length > 1"
        v-model:hover-index="hoverIndex"
        :points="profile"
        label="Elevation profile of the ride"
        class="elevation"
      />
    </template>
  </PageLayout>
</template>

<style scoped>
.back {
  display: inline-block;
  margin: 2px 0 10px;
  color: var(--muted);
  text-decoration: none;
  font-size: 14px;
}
.back:hover {
  color: var(--ink);
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.title {
  min-width: 0;
  flex: 1;
}
h1 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 26px;
}
.title input {
  font-size: 20px;
  width: 100%;
  max-width: 480px;
}
.name {
  background: none;
  border: none;
  padding: 0;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: text;
}
.name:hover {
  text-decoration: underline dotted;
}
.when {
  margin: 4px 0 0;
  color: var(--muted);
}
.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.button {
  display: inline-flex;
  align-items: center;
  padding: 7px 14px;
  border: 1px solid var(--moss);
  border-radius: 8px;
  color: var(--ink);
  text-decoration: none;
  font-weight: 500;
}
.danger {
  border-color: var(--warn);
  color: var(--warn);
}
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
  gap: 8px;
  margin-bottom: 14px;
}
.map {
  height: min(52vh, 560px);
  border: 1px solid var(--line);
  border-radius: 10px;
  overflow: hidden;
}
.elevation {
  margin-top: 12px;
  height: 150px;
}
.empty {
  color: var(--muted);
}
</style>
