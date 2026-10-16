<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { accountManagementDemo, departmentName, teamLeader } from '../data/accountManagementDemo'

const filterDepartment = ref('')
const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({ departmentId: '', name: '', description: '' })
const rules = {
  departmentId: [{ required: true, message: '请选择所属部门', trigger: 'change' }],
  name: [
    { required: true, message: '请输入团队名称', trigger: 'blur' },
    { max: 100, message: '团队名称不能超过 100 个字符', trigger: 'blur' },
  ],
}

const rows = computed(() => accountManagementDemo.teams
  .filter((team) => !filterDepartment.value || team.departmentId === filterDepartment.value)
  .map((team) => ({
    ...team,
    department: departmentName(team.departmentId),
    leader: teamLeader(team.id),
    memberCount: accountManagementDemo.users.filter((user) => user.teamId === team.id).length,
  })))

function openCreate() {
  form.departmentId = filterDepartment.value || ''
  form.name = ''
  form.description = ''
  dialogVisible.value = true
}

function createTeam() {
  formRef.value.validate((valid) => {
    if (!valid) return
    const duplicate = accountManagementDemo.teams.some((team) => team.departmentId === form.departmentId && team.name.trim() === form.name.trim())
    if (duplicate) {
      ElMessage.warning('该部门下已存在同名团队')
      return
    }
    accountManagementDemo.teams.push({
      id: `TEAM-${String(accountManagementDemo.teams.length + 1).padStart(3, '0')}`,
      departmentId: form.departmentId,
      name: form.name.trim(),
      description: form.description.trim(),
      active: true,
    })
    dialogVisible.value = false
    ElMessage.success('已添加到本次界面演示数据')
  })
}
</script>

<template>
  <section class="page-heading">
    <div>
      <p class="eyebrow">ORGANIZATION</p>
      <h1>团队管理</h1>
      <p class="page-description">团队归属于一个部门；创建时先选择部门，再填写团队信息。</p>
    </div>
    <el-button type="primary" @click="openCreate"><span class="button-plus">＋</span> 新建团队</el-button>
  </section>

  <section class="summary-strip">
    <div><span>团队总数</span><strong>{{ accountManagementDemo.teams.length }}</strong></div>
    <div><span>涉及部门</span><strong>{{ new Set(accountManagementDemo.teams.map((team) => team.departmentId)).size }}</strong></div>
    <div class="summary-note">每个团队的部门归属由管理端选择</div>
  </section>

  <el-card class="data-card" shadow="never">
    <div class="table-toolbar">
      <div><h2>团队列表</h2><p>按所属部门查看团队和团队长</p></div>
      <el-select v-model="filterDepartment" clearable placeholder="全部部门" class="toolbar-select">
        <el-option v-for="department in accountManagementDemo.departments" :key="department.id" :label="department.name" :value="department.id" />
      </el-select>
    </div>
    <el-table :data="rows" class="admin-table" empty-text="暂无团队">
      <el-table-column label="团队名称" min-width="190">
        <template #default="{ row }"><div class="primary-cell">{{ row.name }}</div><div class="secondary-cell">{{ row.id }}</div></template>
      </el-table-column>
      <el-table-column label="所属部门" min-width="160"><template #default="{ row }">{{ row.department }}</template></el-table-column>
      <el-table-column label="团队长" min-width="140"><template #default="{ row }">{{ row.leader?.name ?? '暂未设置' }}</template></el-table-column>
      <el-table-column prop="memberCount" label="成员数" width="100" align="center" />
      <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.active ? 'success' : 'info'" effect="light">{{ row.active ? '启用中' : '已停用' }}</el-tag></template></el-table-column>
      <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
    </el-table>
    <div class="table-footnote">团队长及其管理范围由对应业务账号角色关联；本阶段不连接后端。</div>
  </el-card>

  <el-dialog v-model="dialogVisible" title="新建团队" width="min(520px, 92vw)" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="所属部门" prop="departmentId">
        <el-select v-model="form.departmentId" placeholder="请选择部门" class="full-width">
          <el-option v-for="department in accountManagementDemo.departments.filter((item) => item.active)" :key="department.id" :label="department.name" :value="department.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="团队名称" prop="name"><el-input v-model="form.name" maxlength="100" show-word-limit placeholder="例如：产品研发组" /></el-form-item>
      <el-form-item label="团队说明"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="简要说明团队职责（选填）" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="createTeam">创建团队</el-button></template>
  </el-dialog>
</template>
