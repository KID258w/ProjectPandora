<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi, fetchAllPages, getApiErrorMessage } from '../services/http'

const logs = ref([])
const loading = ref(false)
const filters = reactive({ start_date: '', end_date: '', actor_id: '', module: '' })
const modules = [{ value: 'departments', label: '部门' }, { value: 'teams', label: '团队' }, { value: 'users', label: '员工' }]
const actionLabel = (action) => ({ create: '创建', update: '修改', activate: '启用', deactivate: '停用' }[action] || action || '—')
const moduleLabel = (module) => modules.find((item) => item.value === module)?.label || module || '—'
function dateLabel(value) { return value ? new Date(value).toLocaleString('zh-CN') : '—' }
async function loadLogs() {
  const actorId = String(filters.actor_id ?? '').trim()
  if (actorId && (!/^\d+$/.test(actorId) || !Number.isSafeInteger(Number(actorId)) || Number(actorId) < 1)) {
    ElMessage.warning('管理员 ID 必须是正整数')
    return
  }
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== ''))
    if (actorId) params.actor_id = actorId
    logs.value = await fetchAllPages((pageParams) => adminApi.auditLogs(pageParams), params)
  } catch (error) { ElMessage.error(getApiErrorMessage(error)) }
  finally { loading.value = false }
}
function clearFilters() { Object.assign(filters, { start_date: '', end_date: '', actor_id: '', module: '' }); loadLogs() }
onMounted(loadLogs)
</script>

<template>
  <section class="page-heading"><div><p class="eyebrow">SYSTEM</p><h1>管理员操作记录</h1><p class="page-description">查看部门、团队和员工管理操作。</p></div><el-button :loading="loading" @click="loadLogs">刷新</el-button></section>
  <el-card class="data-card" shadow="never"><div class="table-toolbar users-toolbar"><div><h2>操作记录</h2><p>共 {{ logs.length }} 条记录</p></div><div class="filter-controls audit-filters"><el-date-picker v-model="filters.start_date" type="date" value-format="YYYY-MM-DD" placeholder="开始日期"/><el-date-picker v-model="filters.end_date" type="date" value-format="YYYY-MM-DD" placeholder="结束日期"/><el-input v-model="filters.actor_id" type="number" :min="1" :step="1" inputmode="numeric" placeholder="管理员 ID" class="actor-filter"/><el-select v-model="filters.module" clearable placeholder="全部模块" class="toolbar-select"><el-option v-for="module in modules" :key="module.value" :label="module.label" :value="module.value"/></el-select><el-button type="primary" :loading="loading" @click="loadLogs">筛选</el-button><el-button @click="clearFilters">清除</el-button></div></div>
    <el-table v-loading="loading" :data="logs" class="admin-table" empty-text="暂无操作记录"><el-table-column label="时间" min-width="190"><template #default="{ row }">{{ dateLabel(row.created_at) }}</template></el-table-column><el-table-column label="管理员" min-width="150"><template #default="{ row }">{{ row.actor_name || `管理员 ${row.actor_id}` }}</template></el-table-column><el-table-column label="模块" width="110"><template #default="{ row }">{{ moduleLabel(row.module) }}</template></el-table-column><el-table-column label="操作" width="100"><template #default="{ row }">{{ actionLabel(row.action) }}</template></el-table-column><el-table-column label="对象" min-width="160"><template #default="{ row }">{{ row.object_type || '—' }} · {{ row.object_id || '—' }}</template></el-table-column><el-table-column label="结果" width="100"><template #default="{ row }"><el-tag :type="row.result === 'success' ? 'success' : 'danger'">{{ row.result === 'success' ? '成功' : row.result || '—' }}</el-tag></template></el-table-column></el-table><div class="table-footnote">该页面仅展示管理操作元数据；不显示密码、完整身份证号、业务任务或员工日志。</div></el-card>
</template>
