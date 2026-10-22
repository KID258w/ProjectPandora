export const businessRoles = [
  { value: 'founder', label: '创始人' },
  { value: 'department_manager', label: '部门经理' },
  { value: 'team_leader', label: '团队长' },
  { value: 'employee', label: '普通员工' },
]

export function roleLabel(role) {
  return businessRoles.find((item) => item.value === role)?.label ?? '未设置'
}
