import { createRouter, createWebHistory } from 'vue-router'
import { isAdmin } from '@/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/admin',
      name: 'admin',
      component: () => import('@/views/AdminView.vue'),
      beforeEnter: () => isAdmin() || '/',
    },
    {
      path: '/',
      name: 'chat',
      component: () => import('@/views/ChatView.vue'),
    },
  ],
})

export default router
