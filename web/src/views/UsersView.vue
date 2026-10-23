<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, fetchAllPages, getApiErrorMessage, unwrapResponse } from '../services/http'
import { businessRoles, roleLabel } from '../data/accountManagementDemo'

const users = ref([])
const departments = ref([])
const teams = ref([])
const searchText = ref('')
const filterRole = ref('')
const filterActive = ref('')
const dialogVisible = ref(false)
const loading = ref(false)
const submitting = ref(false)
const formRef = ref()
const form = reactive({ username: '', display_name: '', password: '', business_role: '', department_id: '', team_id: '', is_active: true })
const isFounder = computed(() => form.business_role === 'founder')
const needsTeam = computed(() => ['team_leader', 'employee'].includes(form.business_role))
const availableTeams = computed(() => teams.value.filter((team) => team.department_id === form.department_id))
const rows = computed(() => {
  const keyword = searchText.value.trim().toLocaleLowerCase()
  return users.value.filter((user) => (!keyword || `${user.display_name} ${user.username}`.toLocaleLowerCase().includes(keyword)) && (!filterRole.value || user.business_role === filterRole.value) && (filterActive.value === '' || user.is_active === (filterActive.value === 'active')))
})
const userRules = {
  username: [{ required: true, message: '请输入登录账号', trigger: 'blur' }, { max: 150, message: '登录账号不能超过 150 个字符', trigger: 'blur' }],
  display_name: [{ required: true, message: '请输入员工姓名', trigger: 'blur' }, { max: 100, message: '姓名不能超过 100 个字符', trigger: 'blur' }],
  password: [{ required: true, message: '请设置初始密码', trigger: 'blur' }, { min: 8, max: 128, message: '密码长度需为 8–128 个字符', trigger: 'blur' }],
  business_role: [{ required: true, message: '请选择业务身份', trigger: 'change' }],
  department_id: [{ validator: (_rule, value, callback) => isFounder.value || value ? callback() : callback(new Error('请选择所属部门')), trigger: 'change' }],
  team_id: [{ validator: (_rule, value, callback) => !needsTeam.value || value ? callback() : callback(new Error('请选择所属团队')), trigger: 'change' }],
}

async function loadData() {
  loading.value = true
  try {
    const [userRows, departmentRows, teamRows] = await Promise.all([
      fetchAllPages((params) => adminApi.users(params)),
      fetchAllPages((params) => adminApi.departments(params)),
      fetchAllPages((params) => adminApi.teams(params)),
    ])
    users.value = userRows; departments.value = departmentRows; teams.value = teamRows
  } catch (error) { ElMessage.error(getApiErrorMessage(error)) }
  finally { loading.value = false }
}
function openCreate() { Object.assign(form, { username: '', display_name: '', password: '', business_role: '', department_id: '', team_id: '', is_active: true }); dialogVisible.value = true }
async function createUser() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid || submitting.value) return
  const payload = { username: form.username.trim(), display_name: form.display_name.trim(), password: form.password, business_role: form.business_role, is_active: form.is_active }
  if (!isFounder.value) payload.department_id = form.department_id
  if (needsTeam.value) payload.team_id = form.team_id
  submitting.value = true
  try {
    const created = unwrapResponse(await adminApi.createUser(payload))
    ElMessage.success(`已创建账号，直属上级：${created.manager_name ?? '无'}`)
    dialogVisible.value = false
    await loadData()
  } catch (error) {
    const backendMessage = getApiErrorMessage(error)
    const message = form.business_role === 'department_manager' && backendMessage.includes('已存在经理')
      ? '该部门已有启用中的部门经理，不允许重复添加。'
      : backendMessage
    await ElMessageBox.alert(message, '无法创建账号', { type: 'error', confirmButtonText: '知道了' })
  }
  finally { submitting.value = false }
}
watch(() => form.business_role, (role) => { if (role === 'founder') { form.department_id = ''; form.team_id = '' } else if (!['team_leader', 'employee'].includes(role)) form.team_id = '' })
watch(() => form.department_id, () => { if (form.team_id && !availableTeams.value.some((team) => team.id === form.team_id)) form.team_id = '' })
onMounted(loadData)
</script>

<template>
  <section class="page-heading"><div><p class="eyebrow">PEOPLE & ACCESS</p><h1>用户管理</h1><p class="page-description">为员工创建业务账号；身份和组织关系由服务端校验。</p></div><el-button type="primary" @click="openCreate"><span class="button-plus">＋</span> 录入员工</el-button></section>
  <section class="summary-strip"><div><span>业务账号</span><strong>{{ users.length }}</strong></div><div><span>启用中</span><strong>{{ users.filter((user) => user.is_active).length }}</strong></div><div class="summary-note">直属上级由后端按组织规则自动关联</div></section>
  <el-card class="data-card" shadow="never"><div class="table-toolbar users-toolbar"><div><h2>员工账号</h2><p>共 {{ rows.length }} 条记录</p></div><div class="filter-controls"><el-input v-model="searchText" clearable placeholder="搜索姓名或登录账号" class="user-search"/><el-select v-model="filterRole" clearable placeholder="全部身份" class="toolbar-select"><el-option v-for="role in businessRoles" :key="role.value" :label="role.label" :value="role.value"/></el-select><el-select v-model="filterActive" clearable placeholder="全部状态" class="status-select"><el-option label="启用中" value="active"/><el-option label="已停用" value="inactive"/></el-select><el-button text :loading="loading" @click="loadData">刷新</el-button></div></div>
    <el-table v-loading="loading" :data="rows" class="admin-table" empty-text="没有符合条件的账号"><el-table-column label="员工" min-width="205"><template #default="{ row }"><div class="user-primary"><span class="user-avatar">{{ row.display_name?.slice(0, 1) }}</span><span><span class="primary-cell">{{ row.display_name }}</span><span class="secondary-cell">{{ row.username }}</span></span></div></template></el-table-column><el-table-column label="业务身份" min-width="135"><template #default="{ row }"><el-tag class="role-tag" effect="plain">{{ roleLabel(row.business_role) }}</el-tag></template></el-table-column><el-table-column label="所属部门" min-width="145" prop="department_name"/><el-table-column label="所属团队" min-width="145" prop="team_name"/><el-table-column label="直属上级" min-width="140" prop="manager_name"><template #default="{ row }">{{ row.manager_name ?? '—' }}</template></el-table-column><el-table-column label="账号状态" width="115"><template #default="{ row }"><el-tag :type="row.is_active ? 'success' : 'info'" effect="light">{{ row.is_active ? '启用中' : '已停用' }}</el-tag></template></el-table-column></el-table><div class="table-footnote">管理员和业务用户使用不同登录入口；当前接口不提供账号编辑、停用或删除。</div></el-card>
  <el-dialog v-model="dialogVisible" title="录入员工" width="min(640px, 94vw)" destroy-on-close><p class="dialog-intro">选择业务身份后填写对应组织。直属上级由后端自动关联，不由表单提交。</p><el-form ref="formRef" :model="form" :rules="userRules" label-position="top"><div class="form-grid"><el-form-item label="员工姓名" prop="display_name"><el-input v-model="form.display_name" maxlength="100" placeholder="请输入真实姓名"/></el-form-item><el-form-item label="登录账号" prop="username"><el-input v-model="form.username" maxlength="150" autocomplete="username" placeholder="请输入登录账号"/></el-form-item></div><el-form-item label="初始密码" prop="password"><el-input v-model="form.password" type="password" maxlength="128" show-password autocomplete="new-password" placeholder="至少 8 位；后端会校验密码强度"/></el-form-item><el-form-item label="业务身份" prop="business_role"><el-select v-model="form.business_role" placeholder="请选择一种业务身份" class="full-width"><el-option v-for="role in businessRoles" :key="role.value" :label="role.label" :value="role.value"/></el-select></el-form-item><template v-if="!isFounder"><el-form-item label="所属部门" prop="department_id"><el-select v-model="form.department_id" placeholder="请选择部门" class="full-width"><el-option v-for="department in departments" :key="department.id" :label="department.name" :value="department.id"/></el-select></el-form-item><el-form-item v-if="needsTeam" label="所属团队" prop="team_id"><el-select v-model="form.team_id" :disabled="!form.department_id" :placeholder="form.department_id ? '请选择团队' : '请先选择部门'" class="full-width"><el-option v-for="team in availableTeams" :key="team.id" :label="team.name" :value="team.id"/></el-select></el-form-item></template><el-alert class="manager-hint" :title="isFounder ? '创始人无需部门、团队或直属上级。' : form.business_role === 'department_manager' ? '部门经理的直属上级由后端关联启用中的创始人。' : needsTeam ? (form.business_role === 'team_leader' ? '团队长的直属上级由后端关联该部门经理。' : '员工的直属上级由后端关联该团队长。') : '请先选择业务身份。'" type="info" :closable="false" show-icon/><el-form-item label="账号状态" class="status-form-item"><el-switch v-model="form.is_active" active-text="启用" inactive-text="停用"/></el-form-item></el-form><template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="createUser">创建账号</el-button></template></el-dialog>
</template>
