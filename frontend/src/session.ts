import { ref } from 'vue'
import { api } from './api'
import { activateRider } from './planner'
import type { Rider } from './types'

/** The rider chosen in this browser, or null. Loaded once, then kept in sync by chooseRider(). */
export const currentRider = ref<Rider | null>(null)
let loaded: Promise<void> | null = null

/** Asks the server once which rider this browser has chosen (it is stored in a cookie). */
export function loadSession(): Promise<void> {
  loaded ??= api.session().then(({ user }) => {
    setRider(user)
  })
  return loaded
}

export async function chooseRider(id: number) {
  const { user } = await api.chooseRider(id)
  setRider(user)
}

/** The server no longer knows the chosen rider (deleted elsewhere): forget it. */
export function forgetRider() {
  currentRider.value = null
}

function setRider(user: Rider | null) {
  currentRider.value = user
  if (user) activateRider(user.id)
}
