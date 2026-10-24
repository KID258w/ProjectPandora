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
const filterDepartment = ref('')
const filterTeam = ref('')
const filterActive = ref('')
const dialogVisible = ref(false)
const credentialsDialogVisible = ref(false)
const detailVisible = ref(false)
const editing = ref(false)
const detail = ref(null)
const initialCredentials = ref(null)
const loading = ref(false)
const submitting = ref(false)
const saving = ref(false)
const formRef = ref()
const form = reactive({ username: '', id_number: '', display_name: '', business_role: '', department_id: '', team_id: '' })
const editForm = reactive({ business_role: '', department_id: '', team_id: '', is_active: true })
const isFounder = computed(() => form.business_role === 'founder')
const needsTeam = computed(() => ['team_leader', 'employee'].includes(form.business_role))
const availableTeams = computed(() => teams.value.filter((team) => team.department_id === form.department_id && team.is_active))
const availableDepartments = computed(() => departments.value.filter((department) => department.is_active))
const rows = computed(() => users.value.map((user) => ({
  ...user,
  department_name: user.department_name || departments.value.find((department) => department.id === user.department_id)?.name || '—',
  team_name: user.team_name || teams.value.find((team) => team.id === user.team_id)?.name || '—',
})))
const userRules = {
  username: [{ required: true, message: '请输入手机号', trigger: 'blur' }, { max: 150, message: '手机号不能超过 150 个字符', trigger: 'blur' }],
  id_number: [{ required: true, message: '请输入身份证号', trigger: 'blur' }, { max: 64, message: '身份证号不能超过 64 个字符', trigger: 'blur' }],
  display_name: [{ required: true, message: '请输入员工姓名', trigger: 'blur' }, { max: 100, message: '姓名不能超过 100 个字符', trigger: 'blur' }],
  business_role: [{ required: true, message: '请选择业务身份', trigger: 'change' }],
  department_id: [{ validator: (_rule, value, callback) => isFounder.value || value ? callback() : callback(new Error('请选择所属部门')), trigger: 'change' }],
  team_id: [{ validator: (_rule, value, callback) => !needsTeam.value || value ? callback() : callback(new Error('请选择所属团队')), trigger: 'change' }],
}

async function loadData() {
  loading.value = true
  try {
    const params = {}
    if (searchText.value.trim()) params.keyword = searchText.value.trim()
    if (filterRole.value) params.business_role = filterRole.value
    if (filterDepartment.value) params.department_id = filterDepartment.value
    if (filterTeam.value) params.team_id = filterTeam.value
    if (filterActive.value !== '') params.is_active = filterActive.value === 'active'
    const [userRows, departmentRows, teamRows] = await Promise.all([
      fetchAllPages((pageParams) => adminApi.users(pageParams), params),
      fetchAllPages((pageParams) => adminApi.departments(pageParams)),
      fetchAllPages((pageParams) => adminApi.teams(pageParams)),
    ])
    users.value = userRows; departments.value = departmentRows; teams.value = teamRows
  } catch (error) { ElMessage.error(getApiErrorMessage(error)) }
  finally { loading.value = false }
}
function resetCreateForm() {
  Object.assign(form, { username: '', id_number: '', display_name: '', business_role: '', department_id: '', team_id: '' })
  formRef.value?.clearValidate()
}
function openCreate() { resetCreateForm(); initialCredentials.value = null; dialogVisible.value = true }
function clearInitialCredentials() { initialCredentials.value = null }
async function createUser() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid || submitting.value) return
  const payload = { username: form.username.trim(), id_number: form.id_number.trim(), display_name: form.display_name.trim(), business_role: form.business_role }
  if (!isFounder.value) payload.department_id = form.department_id
  if (needsTeam.value) payload.team_id = form.team_id
  submitting.value = true
  try {
    const created = unwrapResponse(await adminApi.createUser(payload))
    initialCredentials.value = { phone: created.username, password: created.initial_password }
    ElMessage.success(`已创建账号，直属上级：${created.manager_name ?? '无'}`)
    dialogVisible.value = false; credentialsDialogVisible.value = true; await loadData()
  } catch (error) { ElMessage.error(getApiErrorMessage(error)) }
  finally { submitting.value = false }
}
async function openDetail(row) {
  try {
    const user = unwrapResponse(await adminApi.user(row.id))
    detail.value = { ...user, department_name: user.department_name || departments.value.find((item) => item.id === user.department_id)?.name || '—', team_name: user.team_name || teams.value.find((item) => item.id === user.team_id)?.name || '—' }
    detailVisible.value = true
  }
  catch (error) { ElMessage.error(getApiErrorMessage(error)) }
}
function openEdit(row) {
  detail.value = { ...row }
  Object.assign(editForm, { business_role: row.business_role, department_id: row.department_id ?? '', team_id: row.team_id ?? '', is_active: row.is_active })
  editing.value = true
}
const editNeedsTeam = computed(() => ['team_leader', 'employee'].includes(editForm.business_role))
const editTeams = computed(() => teams.value.filter((team) => team.is_active && team.department_id === editForm.department_id))
function editTeamLabel(id) { return teams.value.find((team) => team.id === id)?.name || '当前组织已停用' }
function editDepartmentLabel(id) { return departments.value.find((department) => department.id === id)?.name || '当前组织已停用' }
async function saveEdit() {
  if (saving.value || !detail.value) return
  const role = editForm.business_role
  if (role !== 'founder' && !editForm.department_id) { ElMessage.warning('请选择启用中的部门'); return }
  if (editNeedsTeam.value && !editForm.team_id) { ElMessage.warning('请选择启用中的团队'); return }
  const payload = { is_active: editForm.is_active }
  if (role !== detail.value.business_role) payload.business_role = role
  if ((editForm.department_id || null) !== (detail.value.department_id || null)) payload.department_id = editForm.department_id || null
  if ((editForm.team_id || null) !== (detail.value.team_id || null)) payload.team_id = editForm.team_id || null
  saving.value = true
  try {
    await adminApi.updateUser(detail.value.id, payload)
    ElMessage.success('员工管理信息已更新'); editing.value = false; detailVisible.value = false; await loadData()
  } catch (error) { ElMessageBox.alert(getApiErrorMessage(error), '员工信息未更改', { type: 'error' }) }
  finally { saving.value = false }
}
watch(() => form.business_role, (role) => { if (role === 'founder') { form.department_id = ''; form.team_id = '' } else if (!['team_leader', 'employee'].includes(role)) form.team_id = '' })
watch(() => form.department_id, () => { if (form.team_id && !availableTeams.value.some((team) => team.id === form.team_id)) form.team_id = '' })
watch(() => editForm.business_role, (role) => { if (role === 'founder') { editForm.department_id = ''; editForm.team_id = '' } else if (!['team_leader', 'employee'].includes(role)) editForm.team_id = '' })
watch(() => editForm.department_id, () => { if (editForm.team_id && !editTeams.value.some((team) => team.id === editForm.team_id)) editForm.team_id = '' })
watch([filterRole, filterDepartment, filterTeam, filterActive], () => loadData())
onMounted(loadData)
</script>

<template>
  <section class="page-heading"><div><p class="eyebrow">PEOPLE & ACCESS</p><h1>员工管理</h1><p class="page-description">查看员工资料并调整业务身份、组织归属和启用状态。</p></div><el-button type="primary" @click="openCreate"><span class="button-plus">＋</span> 录入员工</el-button></section>
  <section class="summary-strip"><div><span>当前结果</span><strong>{{ users.length }}</strong></div><div><span>启用中</span><strong>{{ users.filter((user) => user.is_active).length }}</strong></div><div class="summary-note">个人资料与密码只读；身份、组织和状态由管理员维护</div></section>
  <el-card class="data-card" shadow="never"><div class="table-toolbar users-toolbar"><div><h2>员工账号</h2><p>共 {{ users.length }} 条匹配记录</p></div><div class="filter-controls"><el-input v-model="searchText" clearable placeholder="搜索姓名或手机号" class="user-search" @keyup.enter="loadData" @clear="loadData"/><el-select v-model="filterRole" clearable placeholder="全部身份" class="toolbar-select"><el-option v-for="role in businessRoles" :key="role.value" :label="role.label" :value="role.value"/></el-select><el-select v-model="filterDepartment" clearable placeholder="全部部门" class="toolbar-select"><el-option v-for="department in departments" :key="department.id" :label="department.name" :value="department.id"/></el-select><el-select v-model="filterTeam" clearable placeholder="全部团队" class="toolbar-select"><el-option v-for="team in teams" :key="team.id" :label="team.name" :value="team.id"/></el-select><el-select v-model="filterActive" clearable placeholder="全部状态" class="status-select"><el-option label="启用中" value="active"/><el-option label="已停用" value="inactive"/></el-select><el-button text :loading="loading" @click="loadData">筛选</el-button></div></div>
    <el-table v-loading="loading" :data="rows" class="admin-table" empty-text="没有符合条件的员工"><el-table-column label="员工" min-width="205"><template #default="{ row }"><div class="user-primary"><span class="user-avatar">{{ row.display_name?.slice(0, 1) }}</span><span><span class="primary-cell">{{ row.display_name }}</span><span class="secondary-cell">{{ row.username }}</span></span></div></template></el-table-column><el-table-column label="身份证号" min-width="170"><template #default="{ row }">{{ row.id_number_masked ?? '—' }}</template></el-table-column><el-table-column label="业务身份" min-width="135"><template #default="{ row }"><el-tag class="role-tag" effect="plain">{{ roleLabel(row.business_role) }}</el-tag></template></el-table-column><el-table-column label="所属部门" min-width="145" prop="department_name"><template #default="{ row }">{{ row.department_name || '—' }}</template></el-table-column><el-table-column label="所属团队" min-width="145" prop="team_name"><template #default="{ row }">{{ row.team_name || '—' }}</template></el-table-column><el-table-column label="账号状态" width="115"><template #default="{ row }"><el-tag :type="row.is_active ? 'success' : 'info'" effect="light">{{ row.is_active ? '启用中' : '已停用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="130" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情</el-button><el-button link type="primary" @click="openEdit(row)">调整</el-button></template></el-table-column></el-table><div class="table-footnote">身份证号仅显示脱敏值；员工姓名、手机号、身份证号和密码不允许在 Web 修改。</div></el-card>
  <el-dialog v-model="dialogVisible" title="录入员工" width="min(640px, 94vw)" destroy-on-close @closed="resetCreateForm"><p class="dialog-intro">选择业务身份后填写对应组织。直属上级由后端自动关联，不由表单提交。</p><el-form ref="formRef" :model="form" :rules="userRules" label-position="top"><div class="form-grid"><el-form-item label="员工姓名" prop="display_name"><el-input v-model="form.display_name" maxlength="100" placeholder="请输入真实姓名"/></el-form-item><el-form-item label="手机号" prop="username"><el-input v-model="form.username" type="tel" maxlength="150" autocomplete="tel" placeholder="请输入手机号"/><p class="field-hint">该手机号同时作为员工登录 App 的账号。</p></el-form-item></div><el-form-item label="身份证号" prop="id_number"><el-input v-model="form.id_number" maxlength="64" autocomplete="off" placeholder="请输入员工身份证号"/></el-form-item><el-form-item label="业务身份" prop="business_role"><el-select v-model="form.business_role" placeholder="请选择一种业务身份" class="full-width"><el-option v-for="role in businessRoles" :key="role.value" :label="role.label" :value="role.value"/></el-select></el-form-item><template v-if="!isFounder"><el-form-item label="所属部门" prop="department_id"><el-select v-model="form.department_id" placeholder="请选择部门" class="full-width"><el-option v-for="department in availableDepartments" :key="department.id" :label="department.name" :value="department.id"/></el-select></el-form-item><el-form-item v-if="needsTeam" label="所属团队" prop="team_id"><el-select v-model="form.team_id" :disabled="!form.department_id" :placeholder="form.department_id ? '请选择团队' : '请先选择部门'" class="full-width"><el-option v-for="team in availableTeams" :key="team.id" :label="team.name" :value="team.id"/></el-select></el-form-item></template><el-alert class="manager-hint" title="负责人唯一性及组织关系由后端校验；员工管理不提供个人资料编辑或密码重置。" type="info" :closable="false" show-icon/></el-form><template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="createUser">创建账号</el-button></template></el-dialog>
  <el-dialog v-model="credentialsDialogVisible" title="员工账号创建成功" width="min(520px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="false" :show-close="false" @closed="clearInitialCredentials"><p class="dialog-intro">请将以下信息转交员工。关闭后，初始密码无法再次查看。</p><div class="credential-row"><span>登录手机号</span><strong>{{ initialCredentials?.phone }}</strong></div><div class="credential-row"><span>初始密码</span><code>{{ initialCredentials?.password }}</code></div><template #footer><el-button type="primary" @click="credentialsDialogVisible = false">我已记录</el-button></template></el-dialog>
  <el-dialog v-model="detailVisible" :title="`${detail?.display_name || '员工'}资料`" width="min(620px, 94vw)"><div v-if="detail" class="detail-grid"><div><span>姓名</span><strong>{{ detail.display_name }}</strong></div><div><span>手机号</span><strong>{{ detail.username }}</strong></div><div><span>身份证号</span><strong>{{ detail.id_number_masked || '—' }}</strong></div><div><span>业务身份</span><strong>{{ roleLabel(detail.business_role) }}</strong></div><div><span>所属部门</span><strong>{{ detail.department_name || '—' }}</strong></div><div><span>所属团队</span><strong>{{ detail.team_name || '—' }}</strong></div><div><span>直属上级</span><strong>{{ detail.manager_name || '—' }}</strong></div><div><span>状态</span><el-tag :type="detail.is_active ? 'success' : 'info'">{{ detail.is_active ? '启用中' : '已停用' }}</el-tag></div></div><template #footer><el-button @click="detailVisible = false">关闭</el-button><el-button type="primary" @click="detailVisible = false; openEdit(detail)">调整身份与组织</el-button></template></el-dialog>
  <el-dialog v-model="editing" :title="`调整员工：${detail?.display_name || ''}`" width="min(560px, 94vw)"><el-form v-if="detail" label-position="top"><el-form-item label="业务身份"><el-select v-model="editForm.business_role" class="full-width"><el-option v-for="role in businessRoles" :key="role.value" :label="role.label" :value="role.value"/></el-select></el-form-item><el-form-item v-if="editForm.business_role !== 'founder'" label="所属部门"><el-select v-model="editForm.department_id" clearable placeholder="请选择启用中的部门" class="full-width"><el-option v-for="department in availableDepartments" :key="department.id" :label="department.name" :value="department.id"/><el-option v-if="editForm.department_id && !availableDepartments.some((item) => item.id === editForm.department_id)" :label="`${editDepartmentLabel(editForm.department_id)}（保留现有归属）`" :value="editForm.department_id" disabled/></el-select></el-form-item><el-form-item v-if="editNeedsTeam" label="所属团队"><el-select v-model="editForm.team_id" clearable placeholder="请选择启用中的团队" class="full-width"><el-option v-for="team in editTeams" :key="team.id" :label="team.name" :value="team.id"/><el-option v-if="editForm.team_id && !editTeams.some((item) => item.id === editForm.team_id)" :label="`${editTeamLabel(editForm.team_id)}（保留现有归属）`" :value="editForm.team_id" disabled/></el-select></el-form-item><el-form-item label="账号状态"><el-switch v-model="editForm.is_active" active-text="启用" inactive-text="停用"/></el-form-item><el-alert title="负责人变更通过身份及组织归属完成。姓名、手机号、身份证号和密码为只读资料。" type="info" :closable="false" show-icon/></el-form><template #footer><el-button @click="editing = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveEdit">保存调整</el-button></template></el-dialog>
</template>
