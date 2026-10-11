<script setup>
const modules = [
  { title: '部门', note: '组织信息' },
  { title: '团队', note: '部门下属团队' },
  { title: '用户', note: '账号与业务角色' },
  { title: '公司重要事项', note: '移动端首页内容' },
]
</script>

<template>
  <section class="page-heading">
    <div>
      <p class="eyebrow">PANDORA ADMIN</p>
      <h1>工作台</h1>
      <p class="page-description">管理端前端骨架已就绪，业务数据将在接口联调后接入。</p>
    </div>
    <el-tag type="info" effect="plain">骨架阶段</el-tag>
  </section>

  <el-alert
    class="scaffold-alert"
    title="当前为前端工程骨架"
    description="后端管理端业务 API 尚未实现；下方模块和统计仅作导航结构，不代表真实数据。"
    type="info"
    :closable="false"
    show-icon
  />

  <section class="module-grid" aria-label="管理模块导航">
    <el-card v-for="(module, index) in modules" :key="module.title" shadow="never" class="module-card">
      <div class="module-card-top">
        <span class="module-index">0{{ index + 1 }}</span>
        <el-tag size="small" type="info" effect="plain">待实现</el-tag>
      </div>
      <h2>{{ module.title }}</h2>
      <p>{{ module.note }}</p>
    </el-card>
  </section>
</template>
