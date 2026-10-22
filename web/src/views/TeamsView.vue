<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
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
const rules = { department_id: [{ required: true, message: '请选择所属部门', trigger: 'change' }], name: [{ required: true, message: '请输入团队名称', trigger: 'blur' }, { max: 100, message: '团队名称不能超过 100 个字符', trigger: 'blur' }] }
const rows = computed(() => teams.value.filter((team) => !filterDepartment.value || team.department_id === filterDepartment.value).map((team) => ({
  ...team,
  department: departments.value.find((department) => department.id === team.department_id)?.name ?? '—',
  leader: users.value.find((user) => user.is_active && user.business_role === 'team_leader' && user.team_id === team.id)?.display_name,
  memberCount: users.value.filter((user) => user.team_id === team.id).length,
})))

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
  <section class="summary-strip"><div><span>团队总数</span><strong>{{ rows.length }}</strong></div><div><span>涉及部门</span><strong>{{ new Set(rows.map((team) => team.department_id)).size }}</strong></div><div class="summary-note">团队及负责人数据来自服务端</div></section>
  <el-card class="data-card" shadow="never"><div class="table-toolbar"><div><h2>团队列表</h2><p>按所属部门查看团队和团队长</p></div><div class="filter-controls"><el-select v-model="filterDepartment" clearable placeholder="全部部门" class="toolbar-select"><el-option v-for="department in departments" :key="department.id" :label="department.name" :value="department.id"/></el-select><el-button text :loading="loading" @click="loadData">刷新</el-button></div></div>
    <el-table v-loading="loading" :data="rows" class="admin-table" empty-text="暂无团队"><el-table-column label="团队名称" min-width="190"><template #default="{ row }"><div class="primary-cell">{{ row.name }}</div><div class="secondary-cell">ID {{ row.id }}</div></template></el-table-column><el-table-column label="所属部门" min-width="160" prop="department"/><el-table-column label="团队长" min-width="140"><template #default="{ row }">{{ row.leader ?? '暂未设置' }}</template></el-table-column><el-table-column prop="memberCount" label="成员数" width="100" align="center"/><el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip/></el-table><div class="table-footnote">后端根据已启用账号计算直属上级；当前接口不提供团队编辑或删除。</div>
  </el-card>
  <el-dialog v-model="dialogVisible" title="新建团队" width="min(520px, 92vw)" destroy-on-close><el-form ref="formRef" :model="form" :rules="rules" label-position="top"><el-form-item label="所属部门" prop="department_id"><el-select v-model="form.department_id" placeholder="请选择部门" class="full-width"><el-option v-for="department in departments" :key="department.id" :label="department.name" :value="department.id"/></el-select></el-form-item><el-form-item label="团队名称" prop="name"><el-input v-model="form.name" maxlength="100" show-word-limit placeholder="例如：产品研发组"/></el-form-item><el-form-item label="团队说明"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="300" show-word-limit/></el-form-item></el-form><template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="createTeam">创建团队</el-button></template></el-dialog>
</template>
