import { createRouter, createWebHistory } from 'vue-router'
import { onNoRider } from './api'
import { currentRider, forgetRider, loadSession } from './session'
import LibraryView from './views/LibraryView.vue'
import PlannerView from './views/PlannerView.vue'
import RidersView from './views/RidersView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/riders', name: 'riders', component: RidersView },
    // The planner: '/' for a new route, '/routes/<id>' while a saved route is open.
    { path: '/', name: 'planner', component: PlannerView },
    { path: '/routes/:id(\\d+)', name: 'route', component: PlannerView },
    // The library: '/library', '/library/archived' or '/library/trash'.
    { path: '/library/:tab(archived|trash)?', name: 'library', component: LibraryView },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

/** Every page except the rider selection needs a chosen rider. */
router.beforeEach(async (to) => {
  if (to.name === 'riders') return true
  try {
    await loadSession()
  } catch {
    // Server unreachable: show the page; its own requests will report the problem.
    return true
  }
  return currentRider.value ? true : { name: 'riders', query: { next: to.fullPath } }
})

// The chosen rider was deleted (e.g. in another browser): go back to the selection.
onNoRider(() => {
  forgetRider()
  if (router.currentRoute.value.name !== 'riders') void router.push({ name: 'riders' })
})
