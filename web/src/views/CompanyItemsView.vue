<script setup>
import { computed, h, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, fetchAllPages, getApiErrorMessage, unwrapResponse } from '../services/http'
import { showOperationError } from '../services/feedback'

const items = ref([])
const loading = ref(false)
const saving = ref(false)
const search = ref('')
const statusFilter = ref('')
const editorVisible = ref(false)
const previewVisible = ref(false)
const detailVisible = ref(false)
const editingId = ref(null)
const selectedItem = ref(null)
const editorRef = ref()
const form = reactive({ title: '', summary: '', body: '' })
const rules = {
  title: [{ required: true, message: '请输入事项标题', trigger: 'blur' }, { max: 200, message: '标题不能超过 200 个字符', trigger: 'blur' }],
  summary: [{ required: true, message: '请输入事项摘要', trigger: 'blur' }, { max: 300, message: '摘要不能超过 300 个字符', trigger: 'blur' }],
  body: [{ required: true, message: '请输入事项正文', trigger: 'blur' }],
}
const visibleItems = computed(() => items.value.filter((item) => {
  const matchesStatus = !statusFilter.value || item.status === statusFilter.value
  const matchesSearch = !search.value.trim() || item.title.toLocaleLowerCase().includes(search.value.trim().toLocaleLowerCase())
  return matchesStatus && matchesSearch
}).sort((left, right) => Number(right.status === 'published') - Number(left.status === 'published')))
const orderedPublishedItems = computed(() => items.value
  .filter((item) => item.status === 'published')
  .sort((left, right) => (left.sort_order || 0) - (right.sort_order || 0) || left.id - right.id))
const publishedCount = computed(() => orderedPublishedItems.value.length)
const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '草稿', value: 'draft' },
  { label: '已发布', value: 'published' },
  { label: '已下线', value: 'offline' },
]
const statusMap = {
  draft: { label: '草稿', type: 'info' },
  published: { label: '已发布', type: 'success' },
  offline: { label: '已下线', type: 'warning' },
}

async function loadItems() {
  loading.value = true
  try {
    items.value = await fetchAllPages((pageParams) => adminApi.companyItems(pageParams))
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error))
  } finally {
    loading.value = false
  }
}

function clearForm() {
  Object.assign(form, { title: '', summary: '', body: '' })
  editingId.value = null
  editorRef.value?.clearValidate()
}

function openCreate() {
  clearForm()
  editorVisible.value = true
}

async function openEdit(row) {
  try {
    const detail = unwrapResponse(await adminApi.companyItem(row.id))
    selectedItem.value = detail
    Object.assign(form, { title: detail.title, summary: detail.summary, body: detail.body })
    editingId.value = detail.id
    editorVisible.value = true
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error))
  }
}

async function saveDraft() {
  const valid = await editorRef.value?.validate().catch(() => false)
  if (!valid || saving.value) return
  const isEditing = Boolean(editingId.value)
  saving.value = true
  try {
    const payload = { title: form.title.trim(), summary: form.summary.trim(), body: form.body.trim() }
    const response = editingId.value
      ? await adminApi.updateCompanyItem(editingId.value, payload)
      : await adminApi.createCompanyItem(payload)
    const saved = unwrapResponse(response)
    ElMessage.success(isEditing ? '事项内容已更新' : '事项草稿已创建')
    editorVisible.value = false
    await loadItems()
    if (saved?.id) selectedItem.value = unwrapResponse(await adminApi.companyItem(saved.id))
  } catch (error) {
    showOperationError('事项未保存', getApiErrorMessage(error))
  } finally {
    saving.value = false
  }
}

async function publish(row) {
  try {
    await ElMessageBox.confirm(actionPrompt({
      tone: 'publish',
      eyebrow: '内容面向全体用户',
      title: '确认发布这条事项？',
      description: '发布后，这条公司事件将展示给全体 App 用户。',
      itemTitle: row.title,
      note: `当前已发布 ${publishedCount.value} / 10 条；发布后将占用一个展示名额。`,
    }), '发布公司事项', {
      customClass: 'company-item-action-dialog company-item-action-dialog--publish',
      modalClass: 'operation-feedback-mask',
      confirmButtonText: '确认发布',
      cancelButtonText: '再检查一下',
      confirmButtonClass: 'company-item-action__confirm',
      cancelButtonClass: 'company-item-action__cancel',
      showClose: false,
      closeOnClickModal: false,
      closeOnPressEscape: true,
      lockScroll: true,
    })
  } catch { return }
  try {
    await adminApi.publishCompanyItem(row.id)
    ElMessage.success('事项已发布')
    await loadItems()
  } catch (error) {
    showOperationError('事项未发布', getApiErrorMessage(error))
  }
}

async function offline(row) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(actionPrompt({
      tone: 'offline',
      eyebrow: '调整 App 展示内容',
      title: '确认下线这条事项？',
      description: '下线后，这条事项会立即从 App 中移除。',
      itemTitle: row.title,
      note: '事项记录仍保留在管理列表中。下线原因可以选填。',
    }), '下线公司事项', {
      customClass: 'company-item-action-dialog company-item-action-dialog--offline',
      modalClass: 'operation-feedback-mask',
      confirmButtonText: '确认下线',
      cancelButtonText: '暂不下线',
      confirmButtonClass: 'company-item-action__confirm',
      cancelButtonClass: 'company-item-action__cancel',
      inputPlaceholder: '下线原因（选填，最多 200 字）',
      inputValidator: (value) => value.length <= 200 || '下线原因不能超过 200 个字符',
      showClose: false,
      closeOnClickModal: false,
      closeOnPressEscape: true,
      lockScroll: true,
    })
    reason = result.value.trim()
  } catch { return }
  try {
    await adminApi.offlineCompanyItem(row.id, { reason })
    ElMessage.success('事项已下线，记录仍保留在管理列表中')
    await loadItems()
  } catch (error) {
    showOperationError('事项未下线', getApiErrorMessage(error))
  }
}

function actionPrompt({ tone, eyebrow, title, description, itemTitle, note }) {
  return h('div', { class: `company-item-action company-item-action--${tone}` }, [
    h('div', { class: 'company-item-action__heading' }, [
      h('span', { class: 'company-item-action__icon', 'aria-hidden': 'true' }, tone === 'publish' ? '↗' : '↓'),
      h('div', { class: 'company-item-action__heading-copy' }, [
        h('p', { class: 'company-item-action__eyebrow' }, eyebrow),
        h('h2', { class: 'company-item-action__title' }, title),
      ]),
    ]),
    h('p', { class: 'company-item-action__description' }, description),
    h('div', { class: 'company-item-action__item' }, [
      h('span', '事项标题'),
      h('strong', itemTitle),
    ]),
    h('div', { class: 'company-item-action__note' }, [
      h('span', { 'aria-hidden': 'true' }, 'i'),
      h('p', note),
    ]),
  ])
}

function publishedIndex(row) {
  return orderedPublishedItems.value.findIndex((item) => item.id === row.id)
}

function publishedSequence(row) {
  const index = publishedIndex(row)
  return index < 0 ? '未设置' : index + 1
}

async function saveOrder(orderedItems) {
  if (orderedItems.length < 2) return
  try {
    const response = unwrapResponse(await adminApi.orderCompanyItems(orderedItems.map((item) => item.id)))
    const orderById = new Map((response?.items ?? []).map((item) => [item.id, item.sort_order]))
    items.value = items.value.map((item) => ({ ...item, sort_order: orderById.get(item.id) ?? item.sort_order }))
    ElMessage.success('事项展示顺序已保存')
    await loadItems()
  } catch (error) {
    showOperationError('排序未保存', getApiErrorMessage(error))
    await loadItems()
  }
}

async function moveItem(row, direction) {
  if (search.value.trim() || statusFilter.value) {
    ElMessage.warning('请先清除搜索和状态筛选，再调整已发布事项顺序')
    return
  }
  const publishedRows = orderedPublishedItems.value
  const index = publishedRows.findIndex((item) => item.id === row.id)
  const target = index + direction
  if (index < 0 || target < 0 || target >= publishedRows.length) return
  const next = [...publishedRows]
  ;[next[index], next[target]] = [next[target], next[index]]
  const reorderedPublished = next.map((item, orderIndex) => ({ ...item, sort_order: orderIndex + 1 }))
  items.value = [...reorderedPublished, ...items.value.filter((item) => item.status !== 'published')]
  await saveOrder(next)
}

async function openDetail(row) {
  try {
    selectedItem.value = unwrapResponse(await adminApi.companyItem(row.id))
    detailVisible.value = true
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error))
  }
}

async function openPreview(row) {
  try {
    selectedItem.value = unwrapResponse(await adminApi.companyItem(row.id))
    previewVisible.value = true
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error))
  }
}

function formatDate(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

onMounted(loadItems)
</script>

<template>
  <section class="page-heading">
    <div><p class="eyebrow">COMPANY INFORMATION</p><h1>事件纪</h1><p class="page-description">维护全体 App 用户共同可见的公司事件。草稿和下线内容不会出现在 App。</p></div>
    <el-button type="primary" @click="openCreate"><span class="button-plus">＋</span> 新建事项</el-button>
  </section>

  <section class="summary-strip company-item-summary">
    <div><span>事项总数</span><strong>{{ items.length }}</strong></div>
    <div><span>已发布</span><strong>{{ publishedCount }}<small> / 10</small></strong></div>
    <div class="summary-note">App 首页展示前 5 条，查看全部最多 10 条</div>
  </section>

  <el-card class="data-card company-items-card" shadow="never">
    <div class="table-toolbar company-items-toolbar">
      <div><h2>事项列表</h2><p>按展示顺序维护已发布内容</p></div>
      <div class="company-items-filters">
        <el-input v-model="search" clearable placeholder="按标题搜索" class="item-search" @keyup.enter="loadItems" @clear="loadItems" />
        <el-select v-model="statusFilter" class="item-status-select" @change="loadItems">
          <el-option v-for="option in statusOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-button :loading="loading" @click="loadItems">查询</el-button>
      </div>
    </div>
    <el-table v-loading="loading" :data="visibleItems" class="admin-table company-items-table" empty-text="暂无公司事件">
      <el-table-column label="顺序" width="116" align="center">
        <template #default="{ row }">
          <div v-if="row.status === 'published'" class="order-controls"><span>{{ publishedSequence(row) }}</span><el-button link :disabled="publishedIndex(row) === 0 || loading || Boolean(search.trim()) || Boolean(statusFilter)" aria-label="上移" @click="moveItem(row, -1)">↑</el-button><el-button link :disabled="publishedIndex(row) === publishedCount - 1 || loading || Boolean(search.trim()) || Boolean(statusFilter)" aria-label="下移" @click="moveItem(row, 1)">↓</el-button></div>
        </template>
      </el-table-column>
      <el-table-column label="事项内容" min-width="320">
        <template #default="{ row }"><div class="item-title-cell"><strong>{{ row.title }}</strong><span>{{ row.summary }}</span></div></template>
      </el-table-column>
      <el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="statusMap[row.status]?.type || 'info'" effect="light">{{ statusMap[row.status]?.label || row.status }}</el-tag></template></el-table-column>
      <el-table-column label="发布时间" min-width="175"><template #default="{ row }">{{ formatDate(row.published_at) }}</template></el-table-column>
      <el-table-column label="操作" min-width="240" fixed="right">
        <template #default="{ row }">
          <div class="item-actions"><el-button link type="primary" @click="openDetail(row)">详情</el-button><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button v-if="row.status !== 'published'" link type="success" :disabled="publishedCount >= 10" @click="publish(row)">发布</el-button><el-button v-else link type="warning" @click="offline(row)">下线</el-button><el-button link type="primary" @click="openPreview(row)">预览</el-button></div>
        </template>
      </el-table-column>
    </el-table>
    <div class="table-footnote">排序仅作用于已发布事项；请先清除搜索和状态筛选再调整。草稿和已下线事项不会参与排序。发布上限由后端再次校验，达到 10 条时请先下线一条已发布事项。</div>
  </el-card>

  <el-dialog v-model="editorVisible" :title="editingId ? '编辑公司事件' : '新建公司事件'" width="min(760px, 94vw)" destroy-on-close @closed="clearForm">
    <p class="dialog-intro">事项作为公司信息发布给全体 App 用户，不包含任务负责人、进度或验收信息。新建内容以草稿保存；编辑现有事项会保留其当前发布状态。</p>
    <el-form ref="editorRef" :model="form" :rules="rules" label-position="top" class="item-editor-form">
      <el-form-item label="事项标题" prop="title"><el-input v-model="form.title" maxlength="200" show-word-limit placeholder="输入清晰、简短的标题" /></el-form-item>
      <el-form-item label="内容摘要" prop="summary"><el-input v-model="form.summary" type="textarea" :rows="2" maxlength="300" show-word-limit placeholder="用于列表和首页卡片展示" /></el-form-item>
      <el-form-item label="事项正文" prop="body"><el-input v-model="form.body" type="textarea" :rows="9" placeholder="填写事项背景、安排或说明" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="editorVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveDraft">{{ editingId ? '保存修改' : '保存草稿' }}</el-button></template>
  </el-dialog>

  <el-dialog v-model="detailVisible" :title="selectedItem?.title || '事项详情'" width="min(760px, 94vw)">
    <div v-if="selectedItem" class="item-detail">
      <div class="item-detail-meta"><el-tag :type="statusMap[selectedItem.status]?.type || 'info'">{{ statusMap[selectedItem.status]?.label || selectedItem.status }}</el-tag><span v-if="selectedItem.status === 'published'">展示顺序 {{ publishedSequence(selectedItem) }}</span><span v-if="selectedItem.published_at">发布于 {{ formatDate(selectedItem.published_at) }}</span></div>
      <h3>{{ selectedItem.summary }}</h3><p class="item-detail-body">{{ selectedItem.body }}</p>
      <div v-if="selectedItem.offline_reason" class="offline-reason"><strong>下线原因：</strong>{{ selectedItem.offline_reason }}</div>
    </div>
    <template #footer><el-button @click="detailVisible = false">关闭</el-button><el-button type="primary" @click="detailVisible = false; openEdit(selectedItem)">编辑</el-button></template>
  </el-dialog>

  <el-dialog v-model="previewVisible" title="App 事项样式预览" width="min(470px, 94vw)" class="item-preview-dialog">
    <div v-if="selectedItem" class="mobile-preview-shell"><div class="mobile-preview-status"><span>9:41</span><span>● ● ●</span></div><div class="mobile-preview-header"><span>‹ 首页</span><span>Pandora</span></div><div class="mobile-preview-list"><div class="mobile-preview-list-heading"><strong>事件纪</strong><span>已发布事项</span></div><article class="mobile-preview-expanded-item"><div class="preview-date">{{ selectedItem.published_at ? formatDate(selectedItem.published_at) : '预览' }}</div><h3>{{ selectedItem.title }} <span>⌃</span></h3><p class="preview-summary">{{ selectedItem.summary }}</p><p class="preview-body">{{ selectedItem.body }}</p></article></div></div>
    <template #footer><el-button @click="previewVisible = false">关闭预览</el-button></template>
  </el-dialog>
</template>

<style scoped>
.company-item-summary small { color: #9aa3b2; font-size: 13px; font-weight: 500; }
.company-items-toolbar { flex-wrap: wrap; }
.company-items-filters { display: flex; flex-wrap: wrap; align-items: center; gap: 9px; }
.item-search { width: 220px; }
.item-status-select { width: 130px; }
.item-title-cell { display: grid; gap: 5px; }
.item-title-cell strong { color: #27334a; font-size: 14px; }
.item-title-cell span { display: -webkit-box; overflow: hidden; color: #828da1; font-size: 12px; line-height: 1.5; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.order-controls { display: inline-flex; align-items: center; gap: 2px; color: #536078; }
.order-controls > span { min-width: 18px; margin-right: 4px; color: #27334a; font-size: 13px; font-weight: 650; }
.item-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 2px; }
.item-editor-form :deep(.el-textarea__inner) { line-height: 1.65; }
.item-detail-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; color: #8a94a7; font-size: 12px; }
.item-detail h3 { margin: 20px 0 10px; color: #455269; font-size: 14px; font-weight: 600; line-height: 1.6; }
.item-detail-body { margin: 0; color: #37445b; font-size: 14px; line-height: 1.85; white-space: pre-wrap; overflow-wrap: anywhere; }
.offline-reason { margin-top: 18px; padding: 12px; border-radius: 8px; background: #fff8ed; color: #7d6543; font-size: 13px; }
.mobile-preview-shell { max-width: 350px; margin: 0 auto; overflow: hidden; border: 7px solid #202a3b; border-radius: 28px; background: #f4f6fa; box-shadow: 0 12px 28px #1d29401a; }
.mobile-preview-status { display: flex; justify-content: space-between; padding: 12px 20px 8px; color: #263249; font-size: 10px; font-weight: 700; }
.mobile-preview-header { display: flex; justify-content: space-between; padding: 11px 18px 15px; color: #263249; background: #fff; font-size: 14px; font-weight: 700; }
.mobile-preview-list { margin: 10px; padding: 14px; border: 1px solid #edf0f5; border-radius: 12px; background: #fff; }
.mobile-preview-list-heading strong { color: #263249; font-size: 13px; }
.preview-date { color: #9aa4b6; font-size: 9px; }
.mobile-preview-expanded-item p { margin: 0; color: #758198; font-size: 11px; line-height: 1.6; white-space: pre-wrap; overflow-wrap: anywhere; }
.mobile-preview-list-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.mobile-preview-list-heading span { color: #9aa4b6; font-size: 9px; }
.mobile-preview-expanded-item { padding: 11px; border: 1px solid #edf0f5; border-radius: 9px; background: #fff; }
.mobile-preview-expanded-item h3 { display: flex; justify-content: space-between; margin: 6px 0; color: #293650; font-size: 13px; }
.mobile-preview-expanded-item h3 span { color: #3269e8; }
.mobile-preview-expanded-item .preview-summary { padding-bottom: 8px; color: #9aa4b6; }
.mobile-preview-expanded-item .preview-body { padding-top: 8px; border-top: 1px solid #edf0f5; color: #455269; }
@media (max-width: 760px) {
  .company-items-filters { width: 100%; }
  .item-search { flex: 1; min-width: 140px; }
  .item-status-select { width: 118px; }
  .company-items-table { min-width: 760px; }
}
</style>
