<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, fetchAllPages, getApiErrorMessage, unwrapResponse } from '../services/http'

const departments = ref([])
const teams = ref([])
const users = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const form = reactive({ name: '', description: '' })
const rules = { name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }, { max: 100, message: '部门名称不能超过 100 个字符', trigger: 'blur' }] }
const rows = computed(() => departments.value.map((department) => ({
  ...department,
  manager: users.value.find((user) => user.is_active && user.business_role === 'department_manager' && user.department_id === department.id)?.display_name,
  teamCount: teams.value.filter((team) => team.department_id === department.id).length,
  memberCount: users.value.filter((user) => user.department_id === department.id).length,
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
  } catch (error) { await ElMessageBox.alert(getApiErrorMessage(error), '无法创建部门', { type: 'error', confirmButtonText: '知道了' }) }
  finally { submitting.value = false }
}
onMounted(loadData)
</script>

<template>
  <section class="page-heading"><div><p class="eyebrow">ORGANIZATION</p><h1>部门管理</h1><p class="page-description">维护公司部门结构，部门经理由后端按业务身份关联。</p></div><el-button type="primary" @click="openCreate"><span class="button-plus">＋</span> 新建部门</el-button></section>
  <section class="summary-strip"><div><span>部门总数</span><strong>{{ rows.length }}</strong></div><div><span>启用部门</span><strong>{{ rows.length }}</strong></div><div class="summary-note">部门信息来自服务端</div></section>
  <el-card class="data-card" shadow="never"><div class="table-toolbar"><div><h2>部门列表</h2><p>共 {{ rows.length }} 个部门</p></div><el-button text :loading="loading" @click="loadData">刷新</el-button></div>
    <el-table v-loading="loading" :data="rows" class="admin-table" empty-text="暂无部门，请先新建部门">
      <el-table-column label="部门名称" min-width="190"><template #default="{ row }"><div class="primary-cell">{{ row.name }}</div><div class="secondary-cell">ID {{ row.id }}</div></template></el-table-column>
      <el-table-column label="部门经理" min-width="150"><template #default="{ row }">{{ row.manager ?? '暂未设置' }}</template></el-table-column>
      <el-table-column prop="teamCount" label="团队数" width="100" align="center"/><el-table-column prop="memberCount" label="成员数" width="100" align="center"/><el-table-column label="说明" prop="description" min-width="220" show-overflow-tooltip/>
    </el-table><div class="table-footnote">负责人和组织统计根据服务端业务账号计算；本接口范围不包含编辑或删除。</div>
  </el-card>
  <el-dialog v-model="dialogVisible" title="新建部门" width="min(520px, 92vw)" destroy-on-close><el-form ref="formRef" :model="form" :rules="rules" label-position="top"><el-form-item label="部门名称" prop="name"><el-input v-model="form.name" maxlength="100" show-word-limit placeholder="例如：产品中心"/></el-form-item><el-form-item label="部门说明"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="300" show-word-limit/></el-form-item></el-form><template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="createDepartment">创建部门</el-button></template></el-dialog>
</template>
