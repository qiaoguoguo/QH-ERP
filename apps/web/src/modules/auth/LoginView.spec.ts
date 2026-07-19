// @ts-expect-error 当前前端 tsconfig 不包含 Node 类型，本测试只读取本地 CSS 源码。
import { readFileSync } from 'node:fs'
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LoginView from './LoginView.vue'
import { useAuthStore } from '../../stores/authStore'

const appStyle = readFileSync('src/style.css', 'utf-8')

function getCssRule(selector: string) {
  const escapedSelector = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = appStyle.match(new RegExp(`${escapedSelector}\\s*\\{([\\s\\S]*?)\\n\\}`))
  if (!match) {
    throw new Error(`未找到 CSS 规则：${selector}`)
  }
  return match[1]
}

function createLoginRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: LoginView },
      { path: '/', name: 'home', component: { template: '<div>工作台</div>' } },
      { path: '/accounts/users', name: 'system-users', component: { template: '<div>用户管理</div>' } },
    ],
  })
}

async function mountLogin(redirect = '/accounts/users') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createLoginRouter()
  await router.push({ path: '/login', query: { redirect } })
  await router.isReady()
  const wrapper = mount(LoginView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  return { router, store: useAuthStore(), wrapper }
}

describe('登录页', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('使用齐慧电气背景和右侧雾面登录框且保留登录表单入口', async () => {
    const { wrapper } = await mountLogin()

    const page = wrapper.get('[data-test="login-page"]')
    const panel = wrapper.get('[data-test="login-panel"]')

    expect(page.classes()).toContain('login-page--qihui-background')
    expect(page.attributes('style')).toContain('--login-background-image: url(')
    expect(page.attributes('style')).toContain('qihui-electric-login-background')
    expect(panel.classes()).toContain('login-panel--frosted')
    expect(panel.text()).toContain('QH ERP 企业管理系统')
    expect(wrapper.get('#login-title').text()).toBe('欢迎登录')
    expect(panel.text()).toContain('企业内部管理入口')
    expect(panel.text()).not.toContain('制造业生产管理 ERP')
    expect(wrapper.find('input[name="username"]').exists()).toBe(true)
    expect(wrapper.find('input[name="password"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="login-submit"]').exists()).toBe(true)
  })

  it('登录背景完整可见且桌面登录框右对齐并保留安全间距', () => {
    const pageRule = getCssRule('.login-page')
    const panelRule = getCssRule('.login-panel')

    expect(pageRule).toContain('background-position: left center, center center;')
    expect(pageRule).toContain('background-size: contain, cover;')
    expect(pageRule).not.toContain('background-size: cover;')
    expect(panelRule).toContain('justify-self: end;')
    expect(panelRule).toContain('margin-right: clamp(')
  })

  it('空账号和空密码提交时显示校验提示', async () => {
    const { wrapper, store } = await mountLogin()
    vi.spyOn(store, 'login').mockResolvedValue()

    await wrapper.find('[data-test="login-submit"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('请输入登录账号')
    expect(wrapper.text()).toContain('请输入登录密码')
    expect(store.login).not.toHaveBeenCalled()
  })

  it('登录失败时显示后端错误信息', async () => {
    const { wrapper, store } = await mountLogin()
    vi.spyOn(store, 'login').mockRejectedValue(new Error('账号已停用'))

    await wrapper.find('input[name="username"]').setValue('disabled-user')
    await wrapper.find('input[name="password"]').setValue('Qherp@2026!')
    await wrapper.find('[data-test="login-submit"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('账号已停用')
  })

  it('登录请求进行中显示加载态并禁用提交', async () => {
    const { wrapper, store } = await mountLogin()
    vi.spyOn(store, 'login').mockImplementation(() => new Promise(() => undefined))

    await wrapper.find('input[name="username"]').setValue('admin')
    await wrapper.find('input[name="password"]').setValue('Qherp@2026!')
    await wrapper.find('[data-test="login-submit"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('登录中')
    expect(wrapper.find('[data-test="login-submit"]').attributes('disabled')).toBeDefined()
  })

  it('登录成功后调用 store 并跳转 redirect 地址', async () => {
    const { router, wrapper, store } = await mountLogin('/accounts/users')
    vi.spyOn(store, 'login').mockResolvedValue()

    await wrapper.find('input[name="username"]').setValue('admin')
    await wrapper.find('input[name="password"]').setValue('Qherp@2026!')
    await wrapper.find('[data-test="login-submit"]').trigger('click')
    await flushPromises()

    expect(store.login).toHaveBeenCalledWith({ username: 'admin', password: 'Qherp@2026!' })
    expect(router.currentRoute.value.fullPath).toBe('/accounts/users')
  })
})
