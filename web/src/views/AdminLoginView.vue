<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

const router = useRouter()
const formRef = ref()
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

function submit() {
  formRef.value.validate((valid) => {
    if (!valid) return
    ElMessage.info('当前为界面评审版，管理员身份验证将在接口联调阶段接入。')
    router.push('/dashboard')
  })
}
</script>

<template>
  <main class="login-page">
    <section class="login-brand-panel">
      <div class="login-brand">
        <span class="brand-mark">P</span>
        <span>Pandora</span>
      </div>
      <div class="login-intro">
        <p class="eyebrow">WORKSPACE ADMINISTRATION</p>
        <h1>让组织与账号管理<br />清晰有序</h1>
        <p>为团队建立可靠的组织基础，支持员工顺利进入日常工作。</p>
      </div>
      <div class="login-panel-foot">Project Pandora · 管理控制台</div>
    </section>

    <section class="login-form-panel">
      <div class="login-form-wrap">
        <div class="login-mobile-brand"><span class="brand-mark">P</span><span>Pandora</span></div>
        <p class="eyebrow">ADMIN CONSOLE</p>
        <h2>管理员登录</h2>
        <p class="login-subtitle">使用系统管理员账号继续</p>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
          <el-form-item label="管理员账号" prop="username">
            <el-input v-model="form.username" size="large" placeholder="请输入管理员账号" autocomplete="username" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" size="large" type="password" placeholder="请输入密码" show-password autocomplete="current-password" @keyup.enter="submit" />
          </el-form-item>
          <el-button class="login-submit" type="primary" size="large" @click="submit">登录管理后台</el-button>
        </el-form>

        <el-alert
          class="login-demo-note"
          title="界面评审模式"
          description="本页面暂不连接认证服务。管理员账号由系统初始化，不提供自助注册。"
          type="info"
          :closable="false"
          show-icon
        />
      </div>
      <p class="login-privacy">仅授权的系统管理员可以使用此管理端</p>
    </section>
  </main>
</template>
