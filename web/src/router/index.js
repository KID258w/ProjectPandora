import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import DashboardView from '../views/DashboardView.vue'
import PlaceholderView from '../views/PlaceholderView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: AdminLayout,
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'dashboard', component: DashboardView, meta: { title: '工作台' } },
        { path: 'organization/departments', name: 'departments', component: PlaceholderView, meta: { title: '部门管理' } },
        { path: 'organization/teams', name: 'teams', component: PlaceholderView, meta: { title: '团队管理' } },
        { path: 'organization/users', name: 'users', component: PlaceholderView, meta: { title: '用户管理' } },
        { path: 'organization/roles', name: 'roles', component: PlaceholderView, meta: { title: '角色与权限' } },
        { path: 'company-items', name: 'company-items', component: PlaceholderView, meta: { title: '公司重要事项' } },
        { path: 'system/audit-logs', name: 'audit-logs', component: PlaceholderView, meta: { title: '管理员操作记录' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

export default router
