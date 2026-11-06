import { createRouter, createWebHistory } from 'vue-router'
import { onNoRider } from './api'
import { currentRider, forgetRider, loadSession } from './session'
import LibraryPage from './pages/LibraryPage.vue'
import PlannerPage from './pages/PlannerPage.vue'
import RidePage from './pages/RidePage.vue'
import RidesPage from './pages/RidesPage.vue'
import RidersPage from './pages/RidersPage.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/riders', name: 'riders', component: RidersPage },
    // The planner: '/' for a new route, '/routes/<id>' while a saved route is open.
    { path: '/', name: 'planner', component: PlannerPage },
    { path: '/routes/:id(\\d+)', name: 'route', component: PlannerPage },
    // The library: '/library', '/library/archived' or '/library/trash'.
    { path: '/library/:tab(archived|trash)?', name: 'library', component: LibraryPage },
    // Recorded rides: '/rides', and one ride at '/rides/<id>'.
    { path: '/rides', name: 'rides', component: RidesPage },
    { path: '/rides/:id(\\d+)', name: 'ride', component: RidePage },
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
