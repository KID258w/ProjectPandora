<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, fetchAllPages, getApiErrorMessage, unwrapResponse } from '../services/http'

const departments = ref([])
const teams = ref([])
const users = ref([])
const filterDepartment = ref('')
const dialogVisible = ref(false)
const loading = ref(false)
const submitting = ref(false)
const formRef = ref()
const form = reactive({ department_id: '', name: '', description: '' })
const activeDepartments = computed(() => departments.value.filter((department) => department.is_active))
const detail = ref(null)
const detailVisible = ref(false)
const editing = ref(false)
const saving = ref(false)
const rules = { department_id: [{ required: true, message: '请选择所属部门', trigger: 'change' }], name: [{ required: true, message: '请输入团队名称', trigger: 'blur' }, { max: 100, message: '团队名称不能超过 100 个字符', trigger: 'blur' }] }
const rows = computed(() => teams.value.filter((team) => !filterDepartment.value || team.department_id === filterDepartment.value).map((team) => ({
  ...team,
  department: departments.value.find((department) => department.id === team.department_id)?.name ?? '—',
  leader: team.leader?.display_name ?? users.value.find((user) => user.is_active && user.business_role === 'team_leader' && user.team_id === team.id)?.display_name,
  memberCount: team.active_users_count ?? users.value.filter((user) => user.team_id === team.id && user.is_active).length,
})))

function memberName(member) { return member.display_name || member.name || member.username || `员工 ${member.id}` }
function openEdit(team) { detail.value = { ...team }; editing.value = true }
async function openDetail(team) {
  try { detail.value = unwrapResponse(await adminApi.team(team.id)); detailVisible.value = true }
  catch (error) { ElMessage.error(getApiErrorMessage(error)) }
}
async function saveDetail() {
  saving.value = true
  try {
    detail.value = unwrapResponse(await adminApi.updateTeam(detail.value.id, { name: detail.value.name.trim(), description: detail.value.description?.trim() ?? '' }))
    ElMessage.success('团队信息已更新'); editing.value = false; await loadData()
  } catch (error) { ElMessageBox.alert(getApiErrorMessage(error), '保存失败', { type: 'error' }) }
  finally { saving.value = false }
}
async function toggleActive(row, value) {
  try { await adminApi.updateTeam(row.id, { is_active: value }); ElMessage.success(value ? '团队已启用' : '团队已停用'); await loadData() }
  catch (error) { ElMessageBox.alert(getApiErrorMessage(error), '团队状态未更改', { type: 'error' }); await loadData() }
}

async function loadData() {
  loading.value = true
  try {
    const [departmentRows, teamRows, userRows] = await Promise.all([
      fetchAllPages((params) => adminApi.departments(params)),
      fetchAllPages((params) => adminApi.teams(params)),
      fetchAllPages((params) => adminApi.users(params)),
    ])
    departments.value = departmentRows; teams.value = teamRows; users.value = userRows
  } catch (error) { ElMessage.error(getApiErrorMessage(error)) }
  finally { loading.value = false }
}
function openCreate() { Object.assign(form, { department_id: filterDepartment.value || '', name: '', description: '' }); dialogVisible.value = true }
async function createTeam() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid || submitting.value) return
  submitting.value = true
  try {
    unwrapResponse(await adminApi.createTeam({ name: form.name.trim(), department_id: form.department_id, description: form.description.trim() }))
    ElMessage.success('团队创建成功'); dialogVisible.value = false; await loadData()
  } catch (error) { ElMessage.error(getApiErrorMessage(error)) }
  finally { submitting.value = false }
}
watch(filterDepartment, async (departmentId) => {
  // Keep the displayed team list consistent with the backend department filter.
  try { teams.value = await fetchAllPages((params) => adminApi.teams(params), departmentId ? { department_id: departmentId } : {}) }
  catch (error) { ElMessage.error(getApiErrorMessage(error)) }
})
onMounted(loadData)
</script>

<template>
  <section class="page-heading"><div><p class="eyebrow">ORGANIZATION</p><h1>团队管理</h1><p class="page-description">团队归属于一个部门；创建时选择所属部门，具体归属由服务端校验。</p></div><el-button type="primary" @click="openCreate"><span class="button-plus">＋</span> 新建团队</el-button></section>
  <section class="summary-strip"><div><span>团队总数</span><strong>{{ rows.length }}</strong></div><div><span>启用团队</span><strong>{{ rows.filter((team) => team.is_active).length }}</strong></div><div class="summary-note">涉及部门 {{ new Set(rows.map((team) => team.department_id)).size }} 个</div></section>
  <el-card class="data-card" shadow="never"><div class="table-toolbar"><div><h2>团队列表</h2><p>按所属部门查看团队和团队长</p></div><div class="filter-controls"><el-select v-model="filterDepartment" clearable placeholder="全部部门" class="toolbar-select"><el-option v-for="department in departments" :key="department.id" :label="department.name" :value="department.id"/></el-select><el-button text :loading="loading" @click="loadData">刷新</el-button></div></div>
    <el-table v-loading="loading" :data="rows" class="admin-table" empty-text="暂无团队"><el-table-column label="团队名称" min-width="190"><template #default="{ row }"><button class="table-link" @click="openDetail(row)">{{ row.name }}</button><div class="secondary-cell">ID {{ row.id }}</div></template></el-table-column><el-table-column label="所属部门" min-width="160" prop="department"/><el-table-column label="团队长" min-width="140"><template #default="{ row }">{{ row.leader ?? '无' }}</template></el-table-column><el-table-column prop="memberCount" label="启用成员" width="105" align="center"/><el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip/><el-table-column label="状态" width="115"><template #default="{ row }"><el-switch :model-value="row.is_active" :active-value="true" :inactive-value="false" @change="(value) => toggleActive(row, value)"/><span class="switch-label">{{ row.is_active ? '启用' : '停用' }}</span></template></el-table-column><el-table-column label="操作" width="120" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情</el-button><el-button link type="primary" @click="openEdit(row)">编辑</el-button></template></el-table-column></el-table><div class="table-footnote">团队详情中的所属部门、负责人和成员只读；停用限制由后端校验。</div>
  </el-card>
  <el-dialog v-model="dialogVisible" title="新建团队" width="min(520px, 92vw)" destroy-on-close><el-form ref="formRef" :model="form" :rules="rules" label-position="top"><el-form-item label="所属部门" prop="department_id"><el-select v-model="form.department_id" placeholder="请选择启用中的部门" class="full-width"><el-option v-for="department in activeDepartments" :key="department.id" :label="department.name" :value="department.id"/></el-select></el-form-item><el-form-item label="团队名称" prop="name"><el-input v-model="form.name" maxlength="100" show-word-limit placeholder="例如：产品研发组"/></el-form-item><el-form-item label="团队说明"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="300" show-word-limit/></el-form-item></el-form><template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="createTeam">创建团队</el-button></template></el-dialog>
  <el-dialog v-model="detailVisible" :title="`${detail?.name || '团队'}详情`" width="min(720px, 94vw)"><div v-if="detail" class="detail-grid"><div><span>团队名称</span><strong>{{ detail.name }}</strong></div><div><span>状态</span><el-tag :type="detail.is_active ? 'success' : 'info'">{{ detail.is_active ? '启用中' : '已停用' }}</el-tag></div><div><span>所属部门</span><strong>{{ detail.department_name || departments.find((item) => item.id === detail.department_id)?.name || '—' }}</strong></div><div><span>团队长</span><strong>{{ detail.leader?.display_name || detail.leader_name || '无' }}</strong></div><div class="detail-wide"><span>说明</span><p>{{ detail.description || '暂无说明' }}</p></div><div class="detail-wide"><span>成员</span><div class="detail-chip-list"><el-tag v-for="member in detail.members || []" :key="member.id" effect="plain">{{ memberName(member) }}</el-tag><span v-if="!detail.members?.length" class="muted-text">暂无成员</span></div></div></div><template #footer><el-button @click="detailVisible = false">关闭</el-button><el-button type="primary" @click="detailVisible = false; openEdit(detail)">编辑团队信息</el-button></template></el-dialog>
  <el-dialog v-model="editing" title="编辑团队信息" width="min(520px, 92vw)"><el-form v-if="detail" label-position="top"><el-form-item label="团队名称"><el-input v-model="detail.name" maxlength="100"/></el-form-item><el-form-item label="团队说明"><el-input v-model="detail.description" type="textarea" :rows="3" maxlength="300"/></el-form-item></el-form><template #footer><el-button @click="editing = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveDetail">保存</el-button></template></el-dialog>
</template>
