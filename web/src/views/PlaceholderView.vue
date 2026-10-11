<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => route.meta.title ?? '管理模块')
</script>

<template>
  <section class="page-heading">
    <div>
      <p class="eyebrow">PANDORA ADMIN</p>
      <h1>{{ title }}</h1>
      <p class="page-description">页面路由已预留，功能将在需求和接口确认后实现。</p>
    </div>
    <el-tag type="info" effect="plain">待实现</el-tag>
  </section>

  <el-empty description="此模块目前只有页面骨架" />
</template>
