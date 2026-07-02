import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import type { UserProfile } from '../shared/api/accountPermissionApi'
import { useAuthStore } from '../stores/authStore'
import { createQhErpRouter } from './index'

const user: UserProfile = { id: '1', username: 'admin', displayName: '管理员', status: 'ENABLED' }

describe('账号权限路由守卫', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('未登录访问受保护路由时跳转登录页并保留来源地址', async () => {
    const router = createQhErpRouter()

    await router.push('/accounts/users')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/accounts/users')
  })

  it('已登录访问登录页时跳转首页', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['system:user:view'] })

    await router.push('/login')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('home')
  })

  it('已登录但缺少权限时跳转无权限页', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: [] })

    await router.push('/accounts/users')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('forbidden')
  })

  it('已登录且拥有权限时允许访问目标路由', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['system:user:view'] })

    await router.push('/accounts/users')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('system-users')
  })
})
