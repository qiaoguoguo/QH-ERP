import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import UserListView from './UserListView.vue'
import type { PageResult, RoleRecord, UserRecord } from '../../../shared/api/accountPermissionApi'
import { useAuthStore } from '../../../stores/authStore'

const apiMock = vi.hoisted(() => ({
  users: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    resetPassword: vi.fn(),
    enable: vi.fn(),
    disable: vi.fn(),
  },
  roles: {
    list: vi.fn(),
  },
}))

vi.mock('../../../shared/api/accountPermissionApi', () => ({
  accountPermissionApi: apiMock,
}))

const enabledUser: UserRecord = {
  id: 1,
  username: 'planner',
  displayName: '计划员',
  phone: '13800000000',
  email: 'planner@qh-erp.local',
  status: 'ENABLED',
  roles: [{ id: 1, code: 'PLANNER', name: '计划员' }],
  createdAt: '2026-07-02T08:00:00+08:00',
  updatedAt: '2026-07-02T08:00:00+08:00',
}
const disabledUser: UserRecord = { ...enabledUser, id: 2, username: 'disabled', displayName: '停用用户', status: 'DISABLED' }
const createdUser: UserRecord = {
  ...enabledUser,
  id: 3,
  username: 'new_user',
  displayName: '新用户',
  roles: [{ id: 1, code: 'PLANNER', name: '计划员' }],
}
const emptyPage: PageResult<UserRecord> = { items: [], page: 1, pageSize: 10, total: 0, totalPages: 0 }
const rolePage: PageResult<RoleRecord> = {
  items: [{ id: 1, code: 'PLANNER', name: '计划员', status: 'ENABLED' }],
  page: 1,
  pageSize: 100,
  total: 1,
  totalPages: 1,
}

function mountUsers(permissions = ['system:user:view', 'system:user:create', 'system:user:update', 'system:user:reset-password']) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  return mount(UserListView, {
    global: {
      plugins: [pinia, ElementPlus],
    },
  })
}

describe('用户管理页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMock.users.list.mockResolvedValue(emptyPage)
    apiMock.roles.list.mockResolvedValue(rolePage)
    apiMock.users.create.mockResolvedValue(enabledUser)
    apiMock.users.update.mockResolvedValue(enabledUser)
    apiMock.users.resetPassword.mockResolvedValue(undefined)
    apiMock.users.enable.mockResolvedValue(undefined)
    apiMock.users.disable.mockResolvedValue(undefined)
  })

  it('展示加载状态并在空数据时显示空状态', async () => {
    let resolveList!: (page: PageResult<UserRecord>) => void
    apiMock.users.list.mockReturnValueOnce(new Promise((resolve) => {
      resolveList = resolve
    }))
    const wrapper = mountUsers()

    expect(wrapper.text()).toContain('用户数据加载中')
    resolveList(emptyPage)
    await flushPromises()

    expect(wrapper.text()).toContain('暂无用户数据')
  })

  it('支持查询、重置和分页请求', async () => {
    apiMock.users.list.mockResolvedValue({ ...emptyPage, total: 21, totalPages: 2 })
    const wrapper = mountUsers()
    await flushPromises()

    await wrapper.find('input[name="user-keyword"]').setValue('计划')
    await wrapper.find('[data-test="user-search"]').trigger('click')
    await flushPromises()

    expect(apiMock.users.list).toHaveBeenLastCalledWith({ keyword: '计划', status: undefined, page: 1, pageSize: 10 })

    wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 2)
    await flushPromises()
    expect(apiMock.users.list).toHaveBeenLastCalledWith({ keyword: '计划', status: undefined, page: 2, pageSize: 10 })

    await wrapper.find('[data-test="user-reset"]').trigger('click')
    await flushPromises()
    expect(apiMock.users.list).toHaveBeenLastCalledWith({ keyword: '', status: undefined, page: 1, pageSize: 10 })
  })

  it('没有创建权限时隐藏新增用户按钮', async () => {
    const wrapper = mountUsers(['system:user:view'])
    await flushPromises()

    expect(wrapper.find('[data-test="create-user"]').exists()).toBe(false)
  })

  it('展示停用状态标签并可触发停用操作', async () => {
    apiMock.users.list.mockResolvedValue({ items: [disabledUser], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    const wrapper = mountUsers()
    await flushPromises()

    expect(wrapper.text()).toContain('停用')
    await wrapper.find('[data-test="enable-user"]').trigger('click')
    await flushPromises()

    expect(apiMock.users.enable).toHaveBeenCalledWith(2)
  })

  it('关键操作打开弹窗或调用接口', async () => {
    apiMock.users.list.mockResolvedValue({ items: [enabledUser], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    const wrapper = mountUsers()
    await flushPromises()

    await wrapper.find('[data-test="create-user"]').trigger('click')
    expect(wrapper.text()).toContain('新增用户')

    await wrapper.find('[data-test="assign-role"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('分配角色')
  })

  it('重置密码使用弹窗输入的新密码', async () => {
    apiMock.users.list.mockResolvedValue({ items: [enabledUser], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    const wrapper = mountUsers()
    await flushPromises()

    await wrapper.find('[data-test="reset-password"]').trigger('click')
    await flushPromises()

    await wrapper.find('input[name="new-password"]').setValue('NewStrong@2026')
    await wrapper.find('input[name="confirm-password"]').setValue('NewStrong@2026')
    await wrapper.find('[data-test="submit-reset-password"]').trigger('click')
    await flushPromises()

    expect(apiMock.users.resetPassword).toHaveBeenCalledWith(1, { newPassword: 'NewStrong@2026' })
  })

  it('新增用户带角色保存成功后提交 roleIds、关闭弹窗并刷新列表', async () => {
    apiMock.users.list
      .mockResolvedValueOnce(emptyPage)
      .mockResolvedValueOnce({ items: [createdUser], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    apiMock.users.create.mockResolvedValue(createdUser)
    const wrapper = mountUsers()
    await flushPromises()

    await wrapper.find('[data-test="create-user"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="user-form-username"]').setValue('new_user')
    await wrapper.find('input[name="user-form-display-name"]').setValue('新用户')
    await wrapper.find('input[name="user-form-initial-password"]').setValue('NewStrong@2026')
    const createFormSelects = wrapper.findAllComponents({ name: 'ElSelect' })
    createFormSelects.at(-1)?.vm.$emit('update:modelValue', ['1'])
    await flushPromises()

    await wrapper.find('[data-test="submit-user"]').trigger('click')
    await flushPromises()

    expect(apiMock.users.create).toHaveBeenCalledWith(expect.objectContaining({
      username: 'new_user',
      displayName: '新用户',
      initialPassword: 'NewStrong@2026',
      roleIds: [1],
    }))
    expect(wrapper.findAllComponents({ name: 'ElDialog' })[0]?.props('modelValue')).toBe(false)
    expect(apiMock.users.list).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('新用户')
  })

  it('用户保存失败后恢复提交按钮状态并保留错误提示', async () => {
    apiMock.users.list.mockResolvedValue({ items: [enabledUser], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    let rejectCreate!: (error: Error) => void
    apiMock.users.create.mockReturnValue(new Promise((_resolve, reject) => {
      rejectCreate = reject
    }))
    const wrapper = mountUsers()
    await flushPromises()

    await wrapper.find('[data-test="create-user"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="user-form-username"]').setValue('new_user')
    await wrapper.find('input[name="user-form-display-name"]').setValue('新用户')
    await wrapper.find('[data-test="submit-user"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="submit-user"]').attributes('disabled')).toBeDefined()

    rejectCreate(new Error('账号已存在'))
    await flushPromises()

    expect(wrapper.text()).toContain('账号已存在')
    expect(wrapper.find('[data-test="submit-user"]').attributes('disabled')).toBeUndefined()
  })

  it('重置密码弱密码不提交并显示校验提示', async () => {
    apiMock.users.list.mockResolvedValue({ items: [enabledUser], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    const wrapper = mountUsers()
    await flushPromises()

    await wrapper.find('[data-test="reset-password"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="new-password"]').setValue('weak')
    await wrapper.find('input[name="confirm-password"]').setValue('weak')
    await wrapper.find('[data-test="submit-reset-password"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('密码至少 8 位，并包含大小写字母、数字和特殊字符')
    expect(apiMock.users.resetPassword).not.toHaveBeenCalled()
  })

  it('用户启停失败时展示错误提示', async () => {
    apiMock.users.list.mockResolvedValue({ items: [disabledUser], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    apiMock.users.enable.mockRejectedValue(new Error('账号状态已变化'))
    const wrapper = mountUsers()
    await flushPromises()

    await wrapper.find('[data-test="enable-user"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('账号状态已变化')
  })

  it('角色分配失败时保留弹窗并展示错误提示', async () => {
    apiMock.users.list.mockResolvedValue({ items: [enabledUser], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    apiMock.users.update.mockRejectedValue(new Error('角色已停用'))
    const wrapper = mountUsers()
    await flushPromises()

    await wrapper.find('[data-test="assign-role"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="submit-role-assignment"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('角色已停用')
    expect(wrapper.text()).toContain('分配角色')
  })
})
