import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import AdminLoginView from '../views/AdminLoginView.vue'
import DashboardView from '../views/DashboardView.vue'
import DepartmentsView from '../views/DepartmentsView.vue'
import TeamsView from '../views/TeamsView.vue'
import UsersView from '../views/UsersView.vue'
import AuditLogsView from '../views/AuditLogsView.vue'
import PlaceholderView from '../views/PlaceholderView.vue'
import { ACCESS_TOKEN_KEY } from '../services/http'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'admin-login', component: AdminLoginView, meta: { title: '管理员登录', guestOnly: true } },
    {
      path: '/',
      component: AdminLayout,
      redirect: '/login',
      meta: { requiresAdmin: true },
      children: [
        { path: 'dashboard', name: 'dashboard', component: DashboardView, meta: { title: '工作台', requiresAdmin: true } },
        { path: 'organization/departments', name: 'departments', component: DepartmentsView, meta: { title: '部门管理', requiresAdmin: true } },
        { path: 'organization/teams', name: 'teams', component: TeamsView, meta: { title: '团队管理', requiresAdmin: true } },
        { path: 'organization/users', name: 'users', component: UsersView, meta: { title: '用户管理', requiresAdmin: true } },
        { path: 'organization/roles', name: 'roles', component: PlaceholderView, meta: { title: '角色与权限' } },
        { path: 'company-items', name: 'company-items', component: PlaceholderView, meta: { title: '公司重要事项' } },
        { path: 'system/audit-logs', name: 'audit-logs', component: AuditLogsView, meta: { title: '管理员操作记录', requiresAdmin: true } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/login' },
  ],
})

export default router

router.beforeEach((to) => {
  const authenticated = Boolean(localStorage.getItem(ACCESS_TOKEN_KEY))
  if (to.matched.some((record) => record.meta.requiresAdmin) && !authenticated) return '/login'
  if (to.meta.guestOnly && authenticated) return '/dashboard'
})

window.addEventListener('pandora:auth-expired', () => {
  router.replace('/login')
})
