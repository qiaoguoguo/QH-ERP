import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthSession } from '../shared/api/accountPermissionApi'
import { useAuthStore } from './authStore'

const adminSession: AuthSession = {
  user: { id: '1', username: 'admin', displayName: '管理员', status: 'ENABLED' },
  roles: [{ id: 'role-1', code: 'SYSTEM_ADMIN', name: '系统管理员', status: 'ENABLED' }],
  menus: [{ id: 'menu-1', code: 'system:user:view', name: '用户管理', type: 'MENU', routePath: '/accounts/users' }],
  permissions: ['system:user:view'],
}

function apiResponse<T>(data: T) {
  return {
    ok: true,
    json: async () => ({
      success: true,
      code: 'OK',
      message: '成功',
      data,
      traceId: 'trace-id',
      timestamp: '2026-07-02T00:00:00+08:00',
    }),
  } as Response
}

function apiFailure(code = 'AUTH_UNAUTHORIZED') {
  return {
    ok: false,
    status: 401,
    json: async () => ({
      success: false,
      code,
      message: '未登录或会话已失效',
      data: null,
      traceId: 'trace-id',
      timestamp: '2026-07-02T00:00:00+08:00',
    }),
  } as Response
}

describe('认证状态 store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('退出本地会话时清空当前用户、菜单和权限', () => {
    const store = useAuthStore()
    store.setSession(adminSession)

    store.clearSession()

    expect(store.currentUser).toBeNull()
    expect(store.menus).toEqual([])
    expect(store.permissions).toEqual([])
    expect(store.csrfToken).toBeNull()
  })

  it('登录前先获取 CSRF，并在登录请求中携带 CSRF header 与 cookie', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse(adminSession))
    vi.stubGlobal('fetch', fetcher)
    const store = useAuthStore()

    await store.login({ username: 'admin', password: 'Qherp@2026!' })

    expect(fetcher).toHaveBeenNthCalledWith(
      1,
      '/api/auth/csrf',
      expect.objectContaining({ credentials: 'include', method: 'GET' }),
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      2,
      '/api/auth/login',
      expect.objectContaining({
        credentials: 'include',
        method: 'POST',
        headers: expect.objectContaining({
          'Content-Type': 'application/json',
          'X-CSRF-TOKEN': 'csrf-token',
        }),
      }),
    )
    expect(store.currentUser?.username).toBe('admin')
    expect(store.permissions).toEqual(['system:user:view'])
    expect(store.csrfToken).toBe('csrf-token')
  })

  it('退出登录前获取 CSRF，请求成功后清空本地会话', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({ token: 'logout-csrf', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({}))
    vi.stubGlobal('fetch', fetcher)
    const store = useAuthStore()
    store.setSession(adminSession)

    await store.logout()

    expect(fetcher).toHaveBeenNthCalledWith(
      2,
      '/api/auth/logout',
      expect.objectContaining({
        credentials: 'include',
        method: 'POST',
        headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'logout-csrf' }),
      }),
    )
    expect(store.currentUser).toBeNull()
    expect(store.permissions).toEqual([])
  })

  it('拉取当前用户后更新本地会话，并按权限码判断权限', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(apiResponse(adminSession)))
    const store = useAuthStore()

    await store.fetchCurrentUser()

    expect(store.currentUser?.displayName).toBe('管理员')
    expect(store.hasPermission('system:user:view')).toBe(true)
    expect(store.hasPermission('system:role:view')).toBe(false)
  })

  it('拉取当前用户失败时清空旧用户和权限后继续抛出错误', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(apiFailure()))
    const store = useAuthStore()
    store.setSession(adminSession)

    await expect(store.fetchCurrentUser()).rejects.toThrow('未登录或会话已失效')

    expect(store.currentUser).toBeNull()
    expect(store.permissions).toEqual([])
    expect(store.menus).toEqual([])
  })
})
