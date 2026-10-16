import { reactive } from 'vue'

// Interface-review fixtures only. PR 5 will replace these local records with API responses.
export const accountManagementDemo = reactive({
  departments: [
    { id: 'DEP-001', name: '产品中心', description: '负责产品规划与研发交付', active: true },
    { id: 'DEP-002', name: '运营中心', description: '负责客户运营与业务支持', active: true },
  ],
  teams: [
    { id: 'TEAM-001', departmentId: 'DEP-001', name: '产品研发组', description: '产品设计与应用研发', active: true },
    { id: 'TEAM-002', departmentId: 'DEP-001', name: '质量保障组', description: '质量验证与发布保障', active: true },
    { id: 'TEAM-003', departmentId: 'DEP-002', name: '客户运营组', description: '客户服务与日常运营', active: true },
  ],
  users: [
    { id: 'USR-001', username: 'founder.demo', name: '林越', role: 'founder', departmentId: '', teamId: '', active: true },
    { id: 'USR-002', username: 'product.manager', name: '周宁', role: 'department_manager', departmentId: 'DEP-001', teamId: '', active: true },
    { id: 'USR-003', username: 'ops.manager', name: '陈沐', role: 'department_manager', departmentId: 'DEP-002', teamId: '', active: true },
    { id: 'USR-004', username: 'design.lead', name: '许安', role: 'team_leader', departmentId: 'DEP-001', teamId: 'TEAM-001', active: true },
    { id: 'USR-005', username: 'qa.lead', name: '沈禾', role: 'team_leader', departmentId: 'DEP-001', teamId: 'TEAM-002', active: true },
    { id: 'USR-006', username: 'designer.demo', name: '唐可', role: 'employee', departmentId: 'DEP-001', teamId: 'TEAM-001', active: true },
    { id: 'USR-007', username: 'operator.demo', name: '顾言', role: 'employee', departmentId: 'DEP-002', teamId: 'TEAM-003', active: false },
  ],
})

export const businessRoles = [
  { value: 'founder', label: '创始人' },
  { value: 'department_manager', label: '部门经理' },
  { value: 'team_leader', label: '团队长' },
  { value: 'employee', label: '普通员工' },
]

export function roleLabel(role) {
  return businessRoles.find((item) => item.value === role)?.label ?? '未设置'
}

export function departmentName(departmentId) {
  return accountManagementDemo.departments.find((item) => item.id === departmentId)?.name ?? '—'
}

export function teamName(teamId) {
  return accountManagementDemo.teams.find((item) => item.id === teamId)?.name ?? '—'
}

export function departmentManager(departmentId) {
  return accountManagementDemo.users.find((user) => user.active && user.role === 'department_manager' && user.departmentId === departmentId)
}

export function teamLeader(teamId) {
  return accountManagementDemo.users.find((user) => user.active && user.role === 'team_leader' && user.teamId === teamId)
}
