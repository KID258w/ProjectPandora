<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const pageTitle = computed(() => route.meta.title ?? '工作台')
</script>

<template>
  <el-container class="admin-shell">
    <el-aside class="sidebar" width="240px">
      <div class="brand">
        <div class="brand-mark">P</div>
        <div>
          <div class="brand-name">Pandora</div>
          <div class="brand-caption">管理后台</div>
        </div>
      </div>

      <div class="menu-caption">管理</div>
      <el-menu :default-active="route.path" router class="side-menu">
        <el-menu-item index="/dashboard">
          <span class="menu-symbol">总</span>
          <span>工作台</span>
        </el-menu-item>
        <el-sub-menu index="organization">
          <template #title>
            <span class="menu-symbol">组</span>
            <span>组织与人员</span>
          </template>
          <el-menu-item index="/organization/departments">部门管理</el-menu-item>
          <el-menu-item index="/organization/teams">团队管理</el-menu-item>
          <el-menu-item index="/organization/users">用户管理</el-menu-item>
          <el-menu-item index="/organization/roles">角色与权限</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/company-items">
          <span class="menu-symbol">事</span>
          <span>公司重要事项</span>
        </el-menu-item>
        <el-sub-menu index="system">
          <template #title>
            <span class="menu-symbol">系</span>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/system/audit-logs">管理员操作记录</el-menu-item>
        </el-sub-menu>
      </el-menu>

      <div class="sidebar-footer">Project Pandora · Web</div>
    </el-aside>

    <el-container class="content-shell">
      <el-header class="topbar" height="68px">
        <div class="breadcrumb">Pandora <span>/</span> {{ pageTitle }}</div>
        <div class="admin-chip">
          <div class="avatar">管</div>
          <span>系统管理员</span>
          <el-button class="logout-button" text @click="router.push('/login')">退出</el-button>
        </div>
      </el-header>
      <el-main class="page-content">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>
