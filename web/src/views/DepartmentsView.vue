<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { accountManagementDemo, departmentManager } from '../data/accountManagementDemo'

const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({ name: '', description: '' })
const rules = {
  name: [
    { required: true, message: '请输入部门名称', trigger: 'blur' },
    { max: 100, message: '部门名称不能超过 100 个字符', trigger: 'blur' },
  ],
}

const rows = computed(() => accountManagementDemo.departments.map((department) => ({
  ...department,
  manager: departmentManager(department.id),
  teamCount: accountManagementDemo.teams.filter((team) => team.departmentId === department.id).length,
  memberCount: accountManagementDemo.users.filter((user) => user.departmentId === department.id).length,
})))

function openCreate() {
  form.name = ''
  form.description = ''
  dialogVisible.value = true
}

function createDepartment() {
  formRef.value.validate((valid) => {
    if (!valid) return
    if (accountManagementDemo.departments.some((department) => department.name.trim() === form.name.trim())) {
      ElMessage.warning('部门名称已存在')
      return
    }
    accountManagementDemo.departments.push({
      id: `DEP-${String(accountManagementDemo.departments.length + 1).padStart(3, '0')}`,
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
      <h1>部门管理</h1>
      <p class="page-description">维护公司部门结构，部门经理由对应业务账号角色确定。</p>
    </div>
    <el-button type="primary" @click="openCreate"><span class="button-plus">＋</span> 新建部门</el-button>
  </section>

  <section class="summary-strip">
    <div><span>部门总数</span><strong>{{ rows.length }}</strong></div>
    <div><span>启用部门</span><strong>{{ rows.filter((row) => row.active).length }}</strong></div>
    <div class="summary-note">组织和账号数据目前为界面演示数据</div>
  </section>

  <el-card class="data-card" shadow="never">
    <div class="table-toolbar">
      <div><h2>部门列表</h2><p>共 {{ rows.length }} 个部门</p></div>
      <el-tag type="info" effect="plain">静态数据演示</el-tag>
    </div>
    <el-table :data="rows" class="admin-table" empty-text="暂无部门，请先新建部门">
      <el-table-column label="部门名称" min-width="190">
        <template #default="{ row }"><div class="primary-cell">{{ row.name }}</div><div class="secondary-cell">{{ row.id }}</div></template>
      </el-table-column>
      <el-table-column label="部门经理" min-width="150">
        <template #default="{ row }">{{ row.manager?.name ?? '暂未设置' }}</template>
      </el-table-column>
      <el-table-column prop="teamCount" label="团队数" width="100" align="center" />
      <el-table-column prop="memberCount" label="成员数" width="100" align="center" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }"><el-tag :type="row.active ? 'success' : 'info'" effect="light">{{ row.active ? '启用中' : '已停用' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="说明" prop="description" min-width="220" show-overflow-tooltip />
    </el-table>
    <div class="table-footnote">真实部门信息、负责关系和操作记录将在后端联调阶段接入。</div>
  </el-card>

  <el-dialog v-model="dialogVisible" title="新建部门" width="min(520px, 92vw)" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="部门名称" prop="name"><el-input v-model="form.name" maxlength="100" show-word-limit placeholder="例如：产品中心" /></el-form-item>
      <el-form-item label="部门说明"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="简要说明部门职责（选填）" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="createDepartment">创建部门</el-button></template>
  </el-dialog>
</template>
