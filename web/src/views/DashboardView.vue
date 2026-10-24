<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi, getApiErrorMessage, unwrapResponse } from '../services/http'

const summary = ref(null)
const loading = ref(false)
async function loadSummary() {
  loading.value = true
  try { summary.value = unwrapResponse(await adminApi.dashboard()) }
  catch (error) { ElMessage.error(getApiErrorMessage(error)) }
  finally { loading.value = false }
}
onMounted(loadSummary)
</script>

<template>
  <section class="page-heading"><div><p class="eyebrow">PANDORA ADMIN</p><h1>工作台</h1><p class="page-description">查看组织与账号管理概况。</p></div><el-button :loading="loading" @click="loadSummary">刷新</el-button></section>
  <el-skeleton v-if="loading && !summary" :rows="3" animated />
  <el-alert v-else-if="!summary" title="暂时无法获取管理概况" description="请检查后端管理概况接口并重试。" type="error" :closable="false" show-icon />
  <section v-else class="summary-cards">
    <el-card shadow="never" class="summary-card"><span>启用员工</span><strong>{{ summary.active_users ?? 0 }}</strong><small>当前启用的业务账号</small></el-card>
    <el-card shadow="never" class="summary-card"><span>启用部门</span><strong>{{ summary.departments ?? 0 }}</strong><small>当前启用的组织部门</small></el-card>
    <el-card shadow="never" class="summary-card"><span>启用团队</span><strong>{{ summary.teams ?? 0 }}</strong><small>当前启用的组织团队</small></el-card>
    <el-card shadow="never" class="summary-card"><span>已发布公司事项</span><strong>{{ summary.published_company_items ?? 0 }}</strong><small>管理概况统计</small></el-card>
  </section>
  <el-alert class="summary-scope-note" title="管理工作台不展示业务任务或员工日志数据。" type="info" :closable="false" show-icon />
</template>
