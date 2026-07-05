import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RoleListView from './RoleListView.vue'
import type { PageResult, RoleRecord } from '../../../shared/api/accountPermissionApi'
import { useAuthStore } from '../../../stores/authStore'

const apiMock = vi.hoisted(() => ({
  roles: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    enable: vi.fn(),
    disable: vi.fn(),
  },
}))

vi.mock('../../../shared/api/accountPermissionApi', () => ({
  accountPermissionApi: apiMock,
}))

const role: RoleRecord = {
  id: 1,
  code: 'PLANNER',
  name: '计划员',
  description: '生产计划维护',
  status: 'ENABLED',
  permissionIds: [1, 2],
}
const disabledRole: RoleRecord = { ...role, id: 2, code: 'DISABLED_ROLE', name: '停用角色', status: 'DISABLED' }
const emptyPage: PageResult<RoleRecord> = { items: [], page: 1, pageSize: 10, total: 0, totalPages: 0 }

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/accounts/roles', name: 'system-roles', component: RoleListView },
      { path: '/accounts/roles/:id/permissions', name: 'system-role-permissions', component: { template: '<div>权限配置</div>' } },
    ],
  })
}

async function mountRoles(
  permissions = [
    'system:role:view',
    'system:permission:view',
    'system:role:create',
    'system:role:update',
    'system:role:assign-permission',
  ],
) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createTestRouter()
  await router.push('/accounts/roles')
  await router.isReady()
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  const wrapper = mount(RoleListView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  return { router, wrapper }
}

describe('角色管理页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMock.roles.list.mockResolvedValue(emptyPage)
    apiMock.roles.create.mockResolvedValue(role)
    apiMock.roles.update.mockResolvedValue(role)
    apiMock.roles.enable.mockResolvedValue(undefined)
    apiMock.roles.disable.mockResolvedValue(undefined)
  })

  it('展示角色列表、查询和停用状态标签', async () => {
    apiMock.roles.list.mockResolvedValue({ items: [disabledRole], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    const { wrapper } = await mountRoles()
    await flushPromises()

    expect(wrapper.text()).toContain('DISABLED_ROLE')
    expect(wrapper.text()).toContain('停用')

    await wrapper.find('input[name="role-keyword"]').setValue('计划')
    await wrapper.find('[data-test="role-search"]').trigger('click')
    await flushPromises()

    expect(apiMock.roles.list).toHaveBeenLastCalledWith({ keyword: '计划', status: undefined, page: 1, pageSize: 10 })
  })

  it('角色表单空值提交时显示校验提示', async () => {
    const { wrapper } = await mountRoles()
    await flushPromises()

    await wrapper.find('[data-test="create-role"]').trigger('click')
    await wrapper.find('[data-test="submit-role"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('请输入角色编码')
    expect(wrapper.text()).toContain('请输入角色名称')
    expect(apiMock.roles.create).not.toHaveBeenCalled()
  })

  it('角色保存失败后恢复提交按钮状态并保留错误提示', async () => {
    let rejectCreate!: (error: Error) => void
    apiMock.roles.create.mockReturnValue(new Promise((_resolve, reject) => {
      rejectCreate = reject
    }))
    const { wrapper } = await mountRoles()
    await flushPromises()

    await wrapper.find('[data-test="create-role"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="role-form-code"]').setValue('QUALITY')
    await wrapper.find('input[name="role-form-name"]').setValue('质量员')
    await wrapper.find('[data-test="submit-role"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="submit-role"]').attributes('disabled')).toBeDefined()

    rejectCreate(new Error('角色编码已存在'))
    await flushPromises()

    expect(wrapper.text()).toContain('角色编码已存在')
    expect(wrapper.find('[data-test="submit-role"]').attributes('disabled')).toBeUndefined()
  })

  it('权限配置入口按权限出现并跳转配置页', async () => {
    apiMock.roles.list.mockResolvedValue({ items: [role], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    const { router, wrapper } = await mountRoles()
    await flushPromises()

    await wrapper.find('[data-test="configure-permission"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/accounts/roles/1/permissions')
  })

  it('没有分配权限时隐藏权限配置入口', async () => {
    apiMock.roles.list.mockResolvedValue({ items: [role], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    const { wrapper } = await mountRoles(['system:role:view'])
    await flushPromises()

    expect(wrapper.find('[data-test="configure-permission"]').exists()).toBe(false)
  })

  it('缺少角色查看或权限树查看权限时隐藏权限配置入口', async () => {
    apiMock.roles.list.mockResolvedValue({ items: [role], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    const { wrapper } = await mountRoles(['system:role:view', 'system:role:assign-permission'])
    await flushPromises()

    expect(wrapper.find('[data-test="configure-permission"]').exists()).toBe(false)
  })

  it('角色启停失败时展示错误并保留页面状态', async () => {
    apiMock.roles.list.mockResolvedValue({ items: [role], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    apiMock.roles.disable.mockRejectedValue(new Error('角色已被使用，不能停用'))
    const { wrapper } = await mountRoles()
    await flushPromises()

    await wrapper.find('[data-test="disable-role"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('角色已被使用，不能停用')
  })
})
