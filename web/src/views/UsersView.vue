<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { accountManagementDemo, businessRoles, departmentName, roleLabel, teamName } from '../data/accountManagementDemo'

const searchText = ref('')
const filterRole = ref('')
const filterActive = ref('')
const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({ username: '', name: '', password: '', role: '', departmentId: '', teamId: '', active: true })

const isFounder = computed(() => form.role === 'founder')
const needsTeam = computed(() => ['team_leader', 'employee'].includes(form.role))
const availableTeams = computed(() => accountManagementDemo.teams.filter((team) => team.active && team.departmentId === form.departmentId))

const rows = computed(() => {
  const keyword = searchText.value.trim().toLocaleLowerCase()
  return accountManagementDemo.users.filter((user) => {
    const matchesKeyword = !keyword || `${user.name} ${user.username}`.toLocaleLowerCase().includes(keyword)
    const matchesRole = !filterRole.value || user.role === filterRole.value
    const matchesStatus = filterActive.value === '' || user.active === (filterActive.value === 'active')
    return matchesKeyword && matchesRole && matchesStatus
  })
})

const userRules = {
  username: [
    { required: true, message: '请输入登录账号', trigger: 'blur' },
    { min: 3, max: 64, message: '登录账号长度为 3–64 个字符', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9._-]+$/, message: '仅支持英文字母、数字、点、下划线和连字符', trigger: 'blur' },
  ],
  name: [{ required: true, message: '请输入员工姓名', trigger: 'blur' }, { max: 50, message: '姓名不能超过 50 个字符', trigger: 'blur' }],
  password: [{ required: true, message: '请设置初始密码', trigger: 'blur' }, { min: 8, message: '密码至少 8 位', trigger: 'blur' }],
  role: [{ required: true, message: '请选择业务身份', trigger: 'change' }],
  departmentId: [{ validator: (_rule, value, callback) => form.role === 'founder' || value ? callback() : callback(new Error('请选择所属部门')), trigger: 'change' }],
  teamId: [{ validator: (_rule, value, callback) => !needsTeam.value || value ? callback() : callback(new Error('请选择所属团队')), trigger: 'change' }],
}

watch(() => form.role, (role) => {
  if (role === 'founder') {
    form.departmentId = ''
    form.teamId = ''
  } else if (!['team_leader', 'employee'].includes(role)) {
    form.teamId = ''
  }
})

watch(() => form.departmentId, () => {
  if (form.teamId && !availableTeams.value.some((team) => team.id === form.teamId)) form.teamId = ''
})

function openCreate() {
  Object.assign(form, { username: '', name: '', password: '', role: '', departmentId: '', teamId: '', active: true })
  dialogVisible.value = true
}

function createUser() {
  formRef.value.validate((valid) => {
    if (!valid) return
    if (accountManagementDemo.users.some((user) => user.username.toLocaleLowerCase() === form.username.trim().toLocaleLowerCase())) {
      ElMessage.warning('登录账号已存在，请使用其他账号')
      return
    }
    const department = accountManagementDemo.departments.find((item) => item.id === form.departmentId && item.active)
    const team = accountManagementDemo.teams.find((item) => item.id === form.teamId && item.active)
    if (form.role === 'department_manager' && !department) {
      ElMessage.warning('请选择有效部门')
      return
    }
    if (needsTeam.value && (!department || !team || team.departmentId !== department.id)) {
      ElMessage.warning('所选团队必须属于当前部门')
      return
    }
    if (form.role === 'team_leader' && !accountManagementDemo.users.some((user) => user.active && user.role === 'department_manager' && user.departmentId === department.id)) {
      ElMessage.warning('请先为该部门录入启用中的部门经理')
      return
    }
    if (form.role === 'employee' && !accountManagementDemo.users.some((user) => user.active && user.role === 'team_leader' && user.teamId === team.id)) {
      ElMessage.warning('请先为该团队录入启用中的团队长')
      return
    }
    if (form.role === 'team_leader' && accountManagementDemo.users.some((user) => user.active && user.role === 'team_leader' && user.teamId === team.id)) {
      ElMessage.warning('该团队已有启用中的团队长')
      return
    }
    if (form.role === 'department_manager' && accountManagementDemo.users.some((user) => user.active && user.role === 'department_manager' && user.departmentId === department.id)) {
      ElMessage.warning('该部门已有启用中的部门经理')
      return
    }

    accountManagementDemo.users.push({
      id: `USR-${String(accountManagementDemo.users.length + 1).padStart(3, '0')}`,
      username: form.username.trim(),
      name: form.name.trim(),
      role: form.role,
      departmentId: form.role === 'founder' ? '' : form.departmentId,
      teamId: needsTeam.value ? form.teamId : '',
      active: form.active,
    })
    dialogVisible.value = false
    ElMessage.success('已添加到本次界面演示数据；登录账号将在接口联调后由后端创建')
  })
}
</script>

<template>
  <section class="page-heading">
    <div>
      <p class="eyebrow">PEOPLE & ACCESS</p>
      <h1>用户管理</h1>
      <p class="page-description">为员工创建业务账号，并根据身份配置所属组织。</p>
    </div>
    <el-button type="primary" @click="openCreate"><span class="button-plus">＋</span> 录入员工</el-button>
  </section>

  <section class="summary-strip">
    <div><span>业务账号</span><strong>{{ accountManagementDemo.users.length }}</strong></div>
    <div><span>启用中</span><strong>{{ accountManagementDemo.users.filter((user) => user.active).length }}</strong></div>
    <div class="summary-note">一个账号只配置一种主要业务身份</div>
  </section>

  <el-card class="data-card" shadow="never">
    <div class="table-toolbar users-toolbar">
      <div><h2>员工账号</h2><p>共 {{ rows.length }} 条记录</p></div>
      <div class="filter-controls">
        <el-input v-model="searchText" clearable placeholder="搜索姓名或登录账号" class="user-search"><template #prefix><span class="search-glyph">⌕</span></template></el-input>
        <el-select v-model="filterRole" clearable placeholder="全部身份" class="toolbar-select">
          <el-option v-for="role in businessRoles" :key="role.value" :label="role.label" :value="role.value" />
        </el-select>
        <el-select v-model="filterActive" clearable placeholder="全部状态" class="status-select">
          <el-option label="启用中" value="active" />
          <el-option label="已停用" value="inactive" />
        </el-select>
      </div>
    </div>
    <el-table :data="rows" class="admin-table" empty-text="没有符合条件的账号">
      <el-table-column label="员工" min-width="205">
        <template #default="{ row }"><div class="user-primary"><span class="user-avatar">{{ row.name.slice(0, 1) }}</span><span><span class="primary-cell">{{ row.name }}</span><span class="secondary-cell">{{ row.username }}</span></span></div></template>
      </el-table-column>
      <el-table-column label="业务身份" min-width="135"><template #default="{ row }"><el-tag class="role-tag" effect="plain">{{ roleLabel(row.role) }}</el-tag></template></el-table-column>
      <el-table-column label="所属部门" min-width="145"><template #default="{ row }">{{ departmentName(row.departmentId) }}</template></el-table-column>
      <el-table-column label="所属团队" min-width="145"><template #default="{ row }">{{ teamName(row.teamId) }}</template></el-table-column>
      <el-table-column label="账号状态" width="115"><template #default="{ row }"><el-tag :type="row.active ? 'success' : 'info'" effect="light">{{ row.active ? '启用中' : '已停用' }}</el-tag></template></el-table-column>
    </el-table>
    <div class="table-footnote">此页面仅展示业务用户账号；系统管理员通过独立的 Web 登录入口访问。</div>
  </el-card>

  <el-dialog v-model="dialogVisible" title="录入员工" width="min(640px, 94vw)" destroy-on-close>
    <p class="dialog-intro">先选择业务身份，系统会按身份显示对应的组织信息。</p>
    <el-form ref="formRef" :model="form" :rules="userRules" label-position="top" class="user-form">
      <div class="form-grid">
        <el-form-item label="员工姓名" prop="name"><el-input v-model="form.name" maxlength="50" placeholder="请输入真实姓名" /></el-form-item>
        <el-form-item label="登录账号" prop="username"><el-input v-model="form.username" maxlength="64" placeholder="字母、数字或 . _ -" /></el-form-item>
      </div>
      <el-form-item label="初始密码" prop="password"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" placeholder="至少 8 位" /></el-form-item>
      <el-form-item label="业务身份" prop="role"><el-select v-model="form.role" placeholder="请选择一种业务身份" class="full-width"><el-option v-for="role in businessRoles" :key="role.value" :label="role.label" :value="role.value" /></el-select></el-form-item>

      <template v-if="!isFounder">
        <el-form-item label="所属部门" prop="departmentId">
          <el-select v-model="form.departmentId" placeholder="请选择部门" class="full-width">
            <el-option v-for="department in accountManagementDemo.departments.filter((item) => item.active)" :key="department.id" :label="department.name" :value="department.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="needsTeam" label="所属团队" prop="teamId">
          <el-select v-model="form.teamId" :disabled="!form.departmentId" :placeholder="form.departmentId ? '请选择团队' : '请先选择部门'" class="full-width">
            <el-option v-for="team in availableTeams" :key="team.id" :label="team.name" :value="team.id" />
          </el-select>
        </el-form-item>
      </template>

      <el-alert
        class="manager-hint"
        :title="isFounder ? '创始人无需设置部门、团队或直属上级。' : form.role === 'department_manager' ? '直属上级将自动关联创始人。' : needsTeam ? (form.role === 'team_leader' ? '直属上级将自动关联所选部门的部门经理。' : '直属上级将自动关联所选团队的团队长。') : '请为该账号选择所属部门。'"
        type="info"
        :closable="false"
        show-icon
      />
      <el-form-item label="账号状态" class="status-form-item"><el-switch v-model="form.active" active-text="启用" inactive-text="停用" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="createUser">创建账号</el-button></template>
  </el-dialog>
</template>
