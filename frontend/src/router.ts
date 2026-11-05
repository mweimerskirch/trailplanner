import { createRouter, createWebHistory } from 'vue-router'
import LibraryView from './views/LibraryView.vue'
import PlannerView from './views/PlannerView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'planner', component: PlannerView },
    { path: '/library', name: 'library', component: LibraryView },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})
