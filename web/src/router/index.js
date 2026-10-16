import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import AdminLoginView from '../views/AdminLoginView.vue'
import DashboardView from '../views/DashboardView.vue'
import DepartmentsView from '../views/DepartmentsView.vue'
import TeamsView from '../views/TeamsView.vue'
import UsersView from '../views/UsersView.vue'
import PlaceholderView from '../views/PlaceholderView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'admin-login', component: AdminLoginView, meta: { title: '管理员登录' } },
    {
      path: '/',
      component: AdminLayout,
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'dashboard', component: DashboardView, meta: { title: '工作台' } },
        { path: 'organization/departments', name: 'departments', component: DepartmentsView, meta: { title: '部门管理' } },
        { path: 'organization/teams', name: 'teams', component: TeamsView, meta: { title: '团队管理' } },
        { path: 'organization/users', name: 'users', component: UsersView, meta: { title: '用户管理' } },
        { path: 'organization/roles', name: 'roles', component: PlaceholderView, meta: { title: '角色与权限' } },
        { path: 'company-items', name: 'company-items', component: PlaceholderView, meta: { title: '公司重要事项' } },
        { path: 'system/audit-logs', name: 'audit-logs', component: PlaceholderView, meta: { title: '管理员操作记录' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

export default router
