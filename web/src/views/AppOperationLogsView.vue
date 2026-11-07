<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi, fetchAllPages, getApiErrorMessage, unwrapResponse } from '../services/http'
import { roleLabel } from '../data/accountManagementDemo'

const logs = ref([])
const employees = ref([])
const selectedActorId = ref('')
const loading = ref(false)
const employeeLoading = ref(false)
const detailLoading = ref(false)
const detailVisible = ref(false)
const selectedLog = ref(null)
const total = ref(0)
const page = ref(1)
const pageSize = 20

const moduleLabels = {
  auth: '登录', profile: '个人资料', tasks: '任务', logs: '日志', tags: '标签',
}
const actionLabels = {
  login: '登录', create: '创建', update: '修改', resubmit: '重新提交', approve: '审批通过',
  return: '退回', progress: '更新进展', submit: '提交完成', accept: '验收通过',
  cancel: '取消', comment: '评论', deactivate: '停用',
}
const fieldLabels = {
  title: '标题', description: '说明', assignee_id: '负责人', due_at: '截止时间',
  estimated_start_at: '预计开始时间', required_materials: '预期材料', association_note: '关联说明',
  tag_ids: '标签', completion_status: '完成状态', lifecycle_status: '任务状态',
  approval_status: '审批状态', approval_round: '审批轮次', completed_work: '今日完成',
  next_day_plan: '明日计划', tomorrow_plan: '明日计划', status: '日志状态',
  log_date: '日志日期', content: '内容', body: '正文', text: '内容', reason: '原因',
  decision: '处理结果', comment: '审批意见', cancellation_reason: '取消原因',
  completion_note: '完成说明', materials_note: '材料说明', review_status: '验收状态',
  review_comment: '验收意见', name: '标签名称', color: '标签颜色', is_active: '启用状态',
  display_name: '姓名', username: '手机号', id_number: '身份证号（值未记录）',
  password: '密码（值未记录）', avatar: '头像（图片未记录）', avatar_url: '头像（图片未记录）',
}
const safeFields = computed(() => selectedLog.value?.changed_fields || [])
const employeeOptions = computed(() => employees.value.map((employee) => ({
  id: employee.id,
  label: `${employee.display_name || '员工'}（ID ${employee.id}）`,
})))

function isSensitiveField(field) {
  return /(password|passwd|token|secret|id.?number|avatar)/i.test(String(field))
}

function fieldLabel(field) {
  return fieldLabels[field] || String(field).replaceAll('_', ' ')
}

function safeValue(source, field) {
  if (!source || typeof source !== 'object' || isSensitiveField(field)) return undefined
  const value = source[field]
  if (value === undefined || value === null || value === '') return undefined
  if (field === 'tag_ids') return formatTagSnapshots(value)
  if (field === 'assignee_id') return formatPersonSnapshot(value)
  if (typeof value === 'string') return value
  return JSON.stringify(value, null, 2)
}

function formatTagSnapshots(value) {
  if (!Array.isArray(value) || value.length === 0) return '（无）'
  return value.map((tag) => {
    const id = tag && typeof tag === 'object' ? tag.id : tag
    const name = tag && typeof tag === 'object' ? tag.name : ''
    const label = name || '未知标签'
    return id === undefined || id === null ? label : `${label}（ID ${id}）`
  }).join('、')
}

function formatPersonSnapshot(value) {
  if (value && typeof value === 'object') {
    const id = value.id
    const name = value.display_name || value.name || '未知成员'
    return id === undefined || id === null ? name : `${name}（ID ${id}）`
  }
  return `未知成员（ID ${value}）`
}

function displayValue(source, field) {
  if (isSensitiveField(field)) return '敏感字段仅记录字段名称，不保存具体值'
  return safeValue(source, field) ?? '（无）'
}

function dateLabel(value) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'medium' }).format(date)
}

function actorRoleLabel(value) {
  return value ? roleLabel(value) : '—'
}

function moduleLabel(value) { return moduleLabels[value] || value || '—' }
function actionLabel(value) { return actionLabels[value] || value || '—' }

async function loadEmployees() {
  employeeLoading.value = true
  try {
    employees.value = await fetchAllPages((params) => adminApi.users(params))
  } catch (error) {
    ElMessage.error(`员工筛选选项加载失败：${getApiErrorMessage(error)}`)
  } finally {
    employeeLoading.value = false
  }
}

async function loadLogs() {
  loading.value = true
  try {
    const params = { page: page.value, page_size: pageSize }
    if (selectedActorId.value) params.actor_id = selectedActorId.value
    const data = unwrapResponse(await adminApi.appOperationLogs(params))
    logs.value = data?.results ?? []
    total.value = data?.count ?? logs.value.length
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error))
    logs.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function filterByEmployee() {
  page.value = 1
  loadLogs()
}

function clearFilter() {
  selectedActorId.value = ''
  page.value = 1
  loadLogs()
}

function changePage(value) {
  page.value = value
  loadLogs()
}

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  selectedLog.value = null
  try {
    selectedLog.value = unwrapResponse(await adminApi.appOperationLog(row.id))
  } catch (error) {
    detailVisible.value = false
    ElMessage.error(`操作详情加载失败：${getApiErrorMessage(error)}`)
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  loadEmployees()
  loadLogs()
})
</script>

<template>
  <section class="page-heading">
    <div>
      <p class="eyebrow">SYSTEM AUDIT</p>
      <h1>员工 App 操作记录</h1>
      <p class="page-description">核查员工在 App 中成功完成的操作。列表仅显示摘要，修改前后值需打开单条记录查看。</p>
    </div>
    <el-button :loading="loading" @click="loadLogs">刷新</el-button>
  </section>

  <el-alert class="audit-scope-alert" type="info" :closable="false" show-icon>
    此页面仅供历史核查，不能恢复或修改业务数据，也不提供常规任务、日志浏览和数据导出。
  </el-alert>

  <el-card class="data-card app-operation-card" shadow="never">
    <div class="table-toolbar operation-toolbar">
      <div><h2>操作记录</h2><p>共 {{ total }} 条，默认按最新操作时间排列</p></div>
      <div class="operation-filters">
        <el-select
          v-model="selectedActorId"
          class="employee-filter"
          clearable
          filterable
          :loading="employeeLoading"
          placeholder="全部员工"
          @change="filterByEmployee"
        >
          <el-option v-for="employee in employeeOptions" :key="employee.id" :label="employee.label" :value="employee.id" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="filterByEmployee">筛选</el-button>
        <el-button :disabled="!selectedActorId" @click="clearFilter">清除</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="logs" class="admin-table operation-table" empty-text="暂无员工 App 操作记录">
      <el-table-column label="操作时间" min-width="185"><template #default="{ row }">{{ dateLabel(row.occurred_at) }}</template></el-table-column>
      <el-table-column label="操作人" min-width="150"><template #default="{ row }"><strong class="operation-actor">{{ row.actor_name || `员工 ${row.actor_id}` }}</strong><span class="operation-subline">ID {{ row.actor_id }}</span></template></el-table-column>
      <el-table-column label="职位与组织" min-width="195"><template #default="{ row }"><span>{{ actorRoleLabel(row.actor_business_role) }}</span><span class="operation-subline">{{ [row.actor_department_name, row.actor_team_name].filter(Boolean).join(' / ') || '—' }}</span></template></el-table-column>
      <el-table-column label="模块 / 操作" min-width="135"><template #default="{ row }"><el-tag effect="plain">{{ moduleLabel(row.module) }}</el-tag><span class="operation-subline">{{ actionLabel(row.action) }}</span></template></el-table-column>
      <el-table-column label="结果" width="90"><template #default="{ row }"><el-tag type="success" effect="light">{{ row.result === 'success' ? '成功' : row.result || '—' }}</el-tag></template></el-table-column>
      <el-table-column label="目标对象" min-width="190"><template #default="{ row }"><span>{{ row.object_label || (row.object_type ? `${row.object_type} #${row.object_id}` : '—') }}</span><span v-if="row.object_type" class="operation-subline">{{ row.object_type }}<template v-if="row.object_id"> · #{{ row.object_id }}</template></span></template></el-table-column>
      <el-table-column label="变更字段" min-width="200"><template #default="{ row }"><div v-if="row.changed_fields?.length" class="changed-field-tags"><el-tag v-for="field in row.changed_fields" :key="field" size="small" effect="light">{{ fieldLabel(field) }}</el-tag></div><span v-else class="operation-subline">无字段变更</span></template></el-table-column>
      <el-table-column label="详情" width="90" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">查看</el-button></template></el-table-column>
    </el-table>

    <div class="operation-pagination"><el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="total, prev, pager, next, jumper" @current-change="changePage" /></div>
    <div class="table-footnote">历史信息按操作发生时保存。密码、完整身份证号、认证令牌和头像二进制不会展示。</div>
  </el-card>

  <el-dialog v-model="detailVisible" title="员工 App 操作详情" width="min(820px, 94vw)" destroy-on-close>
    <div v-loading="detailLoading" class="operation-detail">
      <template v-if="selectedLog">
        <div class="operation-detail-heading">
          <div><p class="eyebrow">{{ moduleLabel(selectedLog.module) }} · {{ actionLabel(selectedLog.action) }}</p><h2>{{ selectedLog.object_label || '操作记录' }}</h2></div>
          <el-tag type="success" effect="light">操作成功</el-tag>
        </div>
        <div class="operation-snapshot-grid">
          <div><span>操作时间</span><strong>{{ dateLabel(selectedLog.occurred_at) }}</strong></div>
          <div><span>操作人快照</span><strong>{{ selectedLog.actor_name || `员工 ${selectedLog.actor_id}` }}</strong></div>
          <div><span>操作时职位</span><strong>{{ actorRoleLabel(selectedLog.actor_business_role) }}</strong></div>
          <div><span>操作时组织</span><strong>{{ [selectedLog.actor_department_name, selectedLog.actor_team_name].filter(Boolean).join(' / ') || '—' }}</strong></div>
          <div><span>目标对象</span><strong>{{ selectedLog.object_type || '—' }}<template v-if="selectedLog.object_id"> · #{{ selectedLog.object_id }}</template></strong></div>
          <div><span>变更字段</span><strong>{{ safeFields.length ? safeFields.map(fieldLabel).join('、') : '无字段变更' }}</strong></div>
        </div>
        <section v-if="safeFields.length" class="before-after-section">
          <div class="before-after-heading"><h3>字段变更记录</h3><span>用于人工核查；此页面不会写回数据</span></div>
          <div v-for="field in safeFields" :key="field" class="before-after-row">
            <h4>{{ fieldLabel(field) }}</h4>
            <div class="before-after-values">
              <div><span>修改前</span><pre>{{ displayValue(selectedLog.before_data, field) }}</pre></div>
              <div><span>修改后</span><pre>{{ displayValue(selectedLog.after_data, field) }}</pre></div>
            </div>
          </div>
        </section>
        <el-empty v-else description="该操作没有可展示的字段值" :image-size="72" />
        <el-alert class="detail-privacy-alert" type="info" :closable="false" show-icon>历史值仅供核查，请通过正常业务流程人工处理；系统不会在此页面恢复、编辑或导出数据。</el-alert>
      </template>
    </div>
    <template #footer><el-button @click="detailVisible = false">关闭</el-button></template>
  </el-dialog>
</template>

<style scoped>
.audit-scope-alert { margin: 0 0 18px; }
.operation-toolbar { flex-wrap: wrap; }
.operation-filters { display: flex; align-items: center; gap: 9px; }
.employee-filter { width: 280px; }
.operation-subline { display: block; margin-top: 4px; color: #929aac; font-size: 11px; line-height: 1.45; }
.operation-actor { color: #27334a; font-size: 13px; }
.changed-field-tags { display: flex; flex-wrap: wrap; gap: 5px; }
.operation-pagination { display: flex; justify-content: flex-end; padding: 16px 20px; border-top: 1px solid #eff1f5; }
.operation-detail { min-height: 100px; }
.operation-detail-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding-bottom: 18px; border-bottom: 1px solid #edf0f5; }
.operation-detail-heading .eyebrow { margin-bottom: 5px; }
.operation-detail-heading h2 { margin: 0; color: #27334a; font-size: 18px; overflow-wrap: anywhere; }
.operation-snapshot-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px 24px; padding: 18px 0; }
.operation-snapshot-grid > div { display: grid; min-width: 0; gap: 5px; }
.operation-snapshot-grid span, .before-after-values > div > span { color: #929aac; font-size: 11px; }
.operation-snapshot-grid strong { color: #35425a; font-size: 13px; font-weight: 600; overflow-wrap: anywhere; }
.before-after-section { padding-top: 16px; border-top: 1px solid #edf0f5; }
.before-after-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.before-after-heading h3 { margin: 0; color: #27334a; font-size: 14px; }
.before-after-heading span { color: #929aac; font-size: 11px; }
.before-after-row { padding: 14px 0; border-top: 1px solid #f0f2f6; }
.before-after-row h4 { margin: 0 0 10px; color: #48556c; font-size: 13px; }
.before-after-values { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.before-after-values > div { min-width: 0; padding: 11px; border: 1px solid #edf0f5; border-radius: 8px; background: #fafbfc; }
.before-after-values pre { min-height: 20px; max-height: 240px; margin: 7px 0 0; overflow: auto; color: #39465d; font: 12px/1.65 Inter, "Microsoft YaHei", sans-serif; white-space: pre-wrap; overflow-wrap: anywhere; }
.detail-privacy-alert { margin-top: 16px; }
@media (max-width: 760px) {
  .operation-filters { width: 100%; }
  .employee-filter { flex: 1; min-width: 160px; }
  .operation-table { min-width: 1080px; }
  .operation-pagination { justify-content: center; overflow-x: auto; }
  .operation-snapshot-grid, .before-after-values { grid-template-columns: 1fr; }
  .before-after-heading { align-items: flex-start; flex-direction: column; }
}
</style>
