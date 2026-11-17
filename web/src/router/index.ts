import { createRouter, createWebHistory } from 'vue-router'

/** 四个页面路由；组局页独立成路由，便于刷新后恢复现场。 */
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'lobby',
      component: () => import('@/pages/Lobby.vue'),
    },
    {
      path: '/discussions/:id',
      name: 'live',
      component: () => import('@/pages/Live.vue'),
      props: true,
    },
    {
      path: '/discussions/:id/panel',
      name: 'panel',
      component: () => import('@/pages/Panel.vue'),
      props: true,
    },
    {
      path: '/discussions/:id/minutes',
      name: 'minutes',
      component: () => import('@/pages/Minutes.vue'),
      props: true,
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
  scrollBehavior() {
    return { top: 0 }
  },
})
