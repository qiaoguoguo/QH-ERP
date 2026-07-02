import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import UnitListView from './UnitListView.vue'
import type { PageResult } from '../../../shared/api/accountPermissionApi'
import type { UnitRecord } from '../../../shared/api/masterDataApi'
import { useAuthStore } from '../../../stores/authStore'

const apiMock = vi.hoisted(() => ({
  units: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    enable: vi.fn(),
    disable: vi.fn(),
  },
}))

vi.mock('../../../shared/api/masterDataApi', () => ({
  masterDataApi: apiMock,
}))

const unit: UnitRecord = {
  id: 1,
  code: 'PCS',
  name: '件',
  precisionScale: 0,
  sortOrder: 1,
  status: 'ENABLED',
  remark: '基本计件单位',
}
const disabledUnit: UnitRecord = { ...unit, id: 2, code: 'KG', name: '千克', status: 'DISABLED' }
const emptyPage: PageResult<UnitRecord> = { items: [], page: 1, pageSize: 20, total: 0, totalPages: 0 }

function mountUnits(permissions = ['master:unit:view', 'master:unit:create', 'master:unit:update']) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  return mount(UnitListView, {
    global: {
      plugins: [pinia, ElementPlus],
    },
  })
}

describe('计量单位列表页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMock.units.list.mockResolvedValue(emptyPage)
    apiMock.units.create.mockResolvedValue(unit)
    apiMock.units.update.mockResolvedValue(unit)
    apiMock.units.enable.mockResolvedValue(unit)
    apiMock.units.disable.mockResolvedValue(unit)
  })

  it('新增单位时将用户输入的精度和排序转为数字提交', async () => {
    const wrapper = mountUnits()
    await flushPromises()

    expect(wrapper.text()).toContain('计量单位')

    await wrapper.find('[data-test="create-record"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="record-code"]').setValue('PCS')
    await wrapper.find('input[name="record-name"]').setValue('件')
    await wrapper.find('input[name="record-precision-scale"]').setValue('0')
    await wrapper.find('input[name="record-sort-order"]').setValue('1')
    await wrapper.find('[data-test="submit-record"]').trigger('click')
    await flushPromises()

    expect(apiMock.units.create).toHaveBeenCalledWith(expect.objectContaining({
      code: 'PCS',
      name: '件',
      precisionScale: 0,
      sortOrder: 1,
    }))
  })

  it('只有查看权限时隐藏新增单位按钮', async () => {
    const wrapper = mountUnits(['master:unit:view'])
    await flushPromises()

    expect(wrapper.find('[data-test="create-record"]').exists()).toBe(false)
  })

  it('缺失精度或排序时不提交并展示校验提示', async () => {
    const wrapper = mountUnits()
    await flushPromises()

    await wrapper.find('[data-test="create-record"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="record-code"]').setValue('PCS')
    await wrapper.find('input[name="record-name"]').setValue('件')
    await wrapper.find('[data-test="submit-record"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('精度和排序为必填')
    expect(apiMock.units.create).not.toHaveBeenCalled()
  })

  it('支持关键词查询、重置和分页请求', async () => {
    apiMock.units.list.mockResolvedValue({ ...emptyPage, total: 21, totalPages: 2 })
    const wrapper = mountUnits()
    await flushPromises()

    await wrapper.find('input[name="record-keyword"]').setValue('件')
    await wrapper.find('[data-test="search-record"]').trigger('click')
    await flushPromises()

    expect(apiMock.units.list).toHaveBeenLastCalledWith({ keyword: '件', status: undefined, page: 1, pageSize: 20 })

    wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 2)
    await flushPromises()
    expect(apiMock.units.list).toHaveBeenLastCalledWith({ keyword: '件', status: undefined, page: 2, pageSize: 20 })

    await wrapper.find('[data-test="reset-record"]').trigger('click')
    await flushPromises()
    expect(apiMock.units.list).toHaveBeenLastCalledWith({ keyword: '', status: undefined, page: 1, pageSize: 20 })
  })

  it('单位启停失败时展示错误提示', async () => {
    apiMock.units.list.mockResolvedValue({ items: [disabledUnit], page: 1, pageSize: 20, total: 1, totalPages: 1 })
    apiMock.units.enable.mockRejectedValue(new Error('单位状态已变化'))
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const wrapper = mountUnits()
    await flushPromises()

    await wrapper.find('[data-test="enable-record"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('单位状态已变化')
  })
})
