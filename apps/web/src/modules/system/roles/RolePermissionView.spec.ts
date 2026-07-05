import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RolePermissionView from './RolePermissionView.vue'
import type { PermissionNode, RoleRecord } from '../../../shared/api/accountPermissionApi'
import { useConfirmActionMock } from '../../../test/setup'
import { useAuthStore } from '../../../stores/authStore'

const confirmActionMock = useConfirmActionMock()

const apiMock = vi.hoisted(() => ({
  roles: {
    get: vi.fn(),
    savePermissions: vi.fn(),
  },
  permissions: {
    tree: vi.fn(),
  },
}))

vi.mock('../../../shared/api/accountPermissionApi', () => ({
  accountPermissionApi: apiMock,
}))

const role: RoleRecord = {
  id: 1,
  code: 'PLANNER',
  name: '计划员',
  status: 'ENABLED',
  permissionIds: [2],
}
const permissionTree: PermissionNode[] = [
  {
    id: 1,
    code: 'system',
    name: '系统管理',
    type: 'MENU',
    parentId: null,
    routePath: '/system',
    sortOrder: 1,
    children: [
      { id: 2, code: 'system:user:view', name: '查看用户', type: 'ACTION', parentId: 1, routePath: '/system/users', sortOrder: 2, children: [] },
      { id: 3, code: 'system:role:view', name: '查看角色', type: 'ACTION', parentId: 1, routePath: '/system/roles', sortOrder: 3, children: [] },
    ],
  },
]

async function mountPermissions() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/accounts/roles', name: 'system-roles', component: { template: '<div>角色管理</div>' } },
      { path: '/accounts/roles/:id/permissions', name: 'system-role-permissions', component: RolePermissionView },
    ],
  })
  await router.push('/accounts/roles/1/permissions')
  await router.isReady()
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions: ['system:role:assign-permission'],
  })
  const wrapper = mount(RolePermissionView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  return { router, wrapper }
}

describe('角色权限配置页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMock.roles.get.mockResolvedValue(role)
    apiMock.permissions.tree.mockResolvedValue(permissionTree)
    apiMock.roles.savePermissions.mockResolvedValue(undefined)
  })

  it('展示权限树和已选状态', async () => {
    const { wrapper } = await mountPermissions()
    await flushPromises()

    expect(wrapper.text()).toContain('计划员')
    expect(wrapper.text()).toContain('系统管理')
    expect(wrapper.text()).toContain('查看用户')
    expect(wrapper.text()).toContain('已选择 1 个权限点')
    expect((wrapper.find('[data-test="permission-checkbox-2"]').element as HTMLInputElement).checked).toBe(true)
  })

  it('保存成功时提交明确 permissionIds', async () => {
    const { wrapper } = await mountPermissions()
    await flushPromises()

    await wrapper.find('[data-test="permission-checkbox-3"]').setValue(true)
    await wrapper.find('[data-test="save-permissions"]').trigger('click')
    await flushPromises()

    expect(apiMock.roles.savePermissions).toHaveBeenCalledWith('1', { permissionIds: [1, 2, 3] })
    expect(wrapper.text()).toContain('权限已保存')
  })

  it('保存失败时展示后端错误信息', async () => {
    apiMock.roles.savePermissions.mockRejectedValue(new Error('权限点不存在'))
    const { wrapper } = await mountPermissions()
    await flushPromises()

    await wrapper.find('[data-test="permission-checkbox-3"]').setValue(true)
    await wrapper.find('[data-test="save-permissions"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('权限点不存在')
  })

  it('有未保存修改时取消会二次确认', async () => {
    confirmActionMock.mockResolvedValueOnce(false)
    const { router, wrapper } = await mountPermissions()
    await flushPromises()

    await wrapper.find('[data-test="permission-checkbox-3"]').setValue(true)
    await wrapper.find('[data-test="cancel-permissions"]').trigger('click')
    await flushPromises()

    expect(confirmActionMock).toHaveBeenCalledWith('存在未保存的权限修改，确认取消？')
    expect(router.currentRoute.value.fullPath).toBe('/accounts/roles/1/permissions')
  })
})
