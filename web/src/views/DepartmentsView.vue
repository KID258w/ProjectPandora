<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi, fetchAllPages, getApiErrorMessage, unwrapResponse } from '../services/http'
import { showOperationError } from '../services/feedback'

const departments = ref([])
const teams = ref([])
const users = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const form = reactive({ name: '', description: '' })
const detail = ref(null)
const detailVisible = ref(false)
const editing = ref(false)
const saving = ref(false)
const rules = { name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }, { max: 100, message: '部门名称不能超过 100 个字符', trigger: 'blur' }] }
const rows = computed(() => departments.value.map((department) => ({
  ...department,
  manager: department.manager?.display_name ?? users.value.find((user) => user.is_active && user.business_role === 'department_manager' && user.department_id === department.id)?.display_name,
  teamCount: teams.value.filter((team) => team.department_id === department.id).length,
  memberCount: department.active_users_count ?? users.value.filter((user) => user.department_id === department.id && user.is_active).length,
})))

async function loadData() {
  loading.value = true
  try {
    const [departmentRows, teamRows, userRows] = await Promise.all([
      fetchAllPages((params) => adminApi.departments(params)),
      fetchAllPages((params) => adminApi.teams(params)),
      fetchAllPages((params) => adminApi.users(params)),
    ])
    departments.value = departmentRows
    teams.value = teamRows
    users.value = userRows
  } catch (error) { ElMessage.error(getApiErrorMessage(error)) }
  finally { loading.value = false }
}

function memberName(member) { return member.display_name || member.name || member.username || `员工 ${member.id}` }
function openEdit(department) { detail.value = { ...department }; editing.value = true }
async function openDetail(department) {
  try { detail.value = unwrapResponse(await adminApi.department(department.id)); detailVisible.value = true }
  catch (error) { ElMessage.error(getApiErrorMessage(error)) }
}
async function saveDetail() {
  saving.value = true
  try {
    detail.value = unwrapResponse(await adminApi.updateDepartment(detail.value.id, { name: detail.value.name.trim(), description: detail.value.description?.trim() ?? '' }))
    ElMessage.success('部门信息已更新'); editing.value = false; await loadData()
  } catch (error) { showOperationError('部门信息未保存', getApiErrorMessage(error)) }
  finally { saving.value = false }
}
async function toggleActive(row, value) {
  try { await adminApi.updateDepartment(row.id, { is_active: value }); ElMessage.success(value ? '部门已启用' : '部门已停用'); await loadData() }
  catch (error) { showOperationError('部门状态未更改', getApiErrorMessage(error)); await loadData() }
}

function openCreate() { Object.assign(form, { name: '', description: '' }); dialogVisible.value = true }
async function createDepartment() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid || submitting.value) return
  submitting.value = true
  try {
    unwrapResponse(await adminApi.createDepartment({ name: form.name.trim(), description: form.description.trim() }))
    ElMessage.success('部门创建成功')
    dialogVisible.value = false
    await loadData()
  } catch (error) { showOperationError('部门未创建', getApiErrorMessage(error)) }
  finally { submitting.value = false }
}
onMounted(loadData)
</script>

<template>
  <section class="page-heading"><div><p class="eyebrow">ORGANIZATION</p><h1>部门管理</h1><p class="page-description">维护公司部门结构，部门经理由后端按业务身份关联。</p></div><el-button type="primary" @click="openCreate"><span class="button-plus">＋</span> 新建部门</el-button></section>
  <section class="summary-strip"><div><span>部门总数</span><strong>{{ rows.length }}</strong></div><div><span>启用部门</span><strong>{{ rows.filter((row) => row.is_active).length }}</strong></div><div class="summary-note">停用部门仍保留在列表中</div></section>
  <el-card class="data-card" shadow="never"><div class="table-toolbar"><div><h2>部门列表</h2><p>共 {{ rows.length }} 个部门</p></div><el-button text :loading="loading" @click="loadData">刷新</el-button></div>
    <el-table v-loading="loading" :data="rows" class="admin-table" empty-text="暂无部门，请先新建部门">
      <el-table-column label="部门名称" min-width="190"><template #default="{ row }"><button class="table-link" @click="openDetail(row)">{{ row.name }}</button><div class="secondary-cell">ID {{ row.id }}</div></template></el-table-column>
      <el-table-column label="部门经理" min-width="150"><template #default="{ row }">{{ row.manager ?? '暂未设置' }}</template></el-table-column>
      <el-table-column prop="teamCount" label="团队数" width="100" align="center"/><el-table-column prop="memberCount" label="成员数" width="100" align="center"/><el-table-column label="说明" prop="description" min-width="180" show-overflow-tooltip/>
      <el-table-column label="状态" width="115"><template #default="{ row }"><el-switch :model-value="row.is_active" :active-value="true" :inactive-value="false" @change="(value) => toggleActive(row, value)"/><span class="switch-label">{{ row.is_active ? '启用' : '停用' }}</span></template></el-table-column>
      <el-table-column label="操作" width="120" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情</el-button><el-button link type="primary" @click="openEdit(row)">编辑</el-button></template></el-table-column>
    </el-table><div class="table-footnote">负责人和成员在详情中只读；停用前的成员和团队限制由后端校验。</div>
  </el-card>
  <el-dialog v-model="dialogVisible" title="新建部门" width="min(520px, 92vw)" destroy-on-close><el-form ref="formRef" :model="form" :rules="rules" label-position="top"><el-form-item label="部门名称" prop="name"><el-input v-model="form.name" maxlength="100" show-word-limit placeholder="例如：产品中心"/></el-form-item><el-form-item label="部门说明"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="300" show-word-limit/></el-form-item></el-form><template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="createDepartment">创建部门</el-button></template></el-dialog>
  <el-dialog v-model="detailVisible" :title="`${detail?.name || '部门'}详情`" width="min(720px, 94vw)"><div v-if="detail" class="detail-grid"><div><span>部门名称</span><strong>{{ detail.name }}</strong></div><div><span>状态</span><el-tag :type="detail.is_active ? 'success' : 'info'">{{ detail.is_active ? '启用中' : '已停用' }}</el-tag></div><div><span>部门经理</span><strong>{{ detail.manager?.display_name || detail.manager_name || '无' }}</strong></div><div><span>启用成员</span><strong>{{ detail.active_users_count ?? detail.members?.length ?? 0 }}</strong></div><div class="detail-wide"><span>说明</span><p>{{ detail.description || '暂无说明' }}</p></div><div class="detail-wide"><span>下属团队</span><div class="detail-chip-list"><el-tag v-for="team in detail.teams || []" :key="team.id" effect="plain">{{ team.name }} · {{ team.is_active ? '启用' : '停用' }}</el-tag><span v-if="!detail.teams?.length" class="muted-text">暂无团队</span></div></div><div class="detail-wide"><span>成员</span><div class="detail-chip-list"><el-tag v-for="member in detail.members || []" :key="member.id" effect="plain">{{ memberName(member) }}</el-tag><span v-if="!detail.members?.length" class="muted-text">暂无成员</span></div></div></div><template #footer><el-button @click="detailVisible = false">关闭</el-button><el-button type="primary" @click="detailVisible = false; openEdit(detail)">编辑部门信息</el-button></template></el-dialog>
  <el-dialog v-model="editing" title="编辑部门信息" width="min(520px, 92vw)"><el-form v-if="detail" label-position="top"><el-form-item label="部门名称"><el-input v-model="detail.name" maxlength="100"/></el-form-item><el-form-item label="部门说明"><el-input v-model="detail.description" type="textarea" :rows="3" maxlength="300"/></el-form-item></el-form><template #footer><el-button @click="editing = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveDetail">保存</el-button></template></el-dialog>
</template>
