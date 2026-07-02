<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/authStore'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const form = reactive({
  username: '',
  password: '',
})
const errors = reactive({
  username: '',
  password: '',
})
const submitError = ref('')
const loading = ref(false)

function validate() {
  errors.username = form.username.trim() ? '' : '请输入登录账号'
  errors.password = form.password ? '' : '请输入登录密码'
  return !errors.username && !errors.password
}

async function submit() {
  submitError.value = ''
  if (!validate()) {
    return
  }

  loading.value = true
  try {
    await authStore.login({
      username: form.username.trim(),
      password: form.password,
    })
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (error) {
    submitError.value = error instanceof Error ? error.message : '登录失败，请检查账号和密码'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-panel" aria-labelledby="login-title">
      <div class="login-brand">
        <strong>QH ERP</strong>
        <span>制造业生产管理 ERP</span>
      </div>
      <h1 id="login-title">账号登录</h1>
      <p class="login-subtitle">进入企业内部账号与权限基础模块</p>

      <el-alert v-if="submitError" class="form-alert" type="error" :title="submitError" show-icon :closable="false" />

      <el-form class="login-form" label-position="top" @submit.prevent="submit">
        <el-form-item label="登录账号" :error="errors.username">
          <el-input
            v-model="form.username"
            name="username"
            autocomplete="username"
            placeholder="请输入登录账号"
            @keyup.enter="submit"
          />
          <div v-if="errors.username" class="field-error">{{ errors.username }}</div>
        </el-form-item>
        <el-form-item label="登录密码" :error="errors.password">
          <el-input
            v-model="form.password"
            name="password"
            type="password"
            autocomplete="current-password"
            placeholder="请输入登录密码"
            show-password
            @keyup.enter="submit"
          />
          <div v-if="errors.password" class="field-error">{{ errors.password }}</div>
        </el-form-item>
        <el-button
          data-test="login-submit"
          class="login-submit"
          type="primary"
          :loading="loading"
          :disabled="loading"
          @click="submit"
        >
          {{ loading ? '登录中' : '登录' }}
        </el-button>
      </el-form>
    </section>
  </main>
</template>
