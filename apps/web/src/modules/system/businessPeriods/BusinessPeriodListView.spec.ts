import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useConfirmActionMock } from '../../../test/setup'
import { useAuthStore } from '../../../stores/authStore'
import BusinessPeriodListView from './BusinessPeriodListView.vue'
import type { BusinessPeriodRecord, BusinessPeriodPageResult } from '../../../shared/api/businessPeriodApi'

const confirmActionMock = useConfirmActionMock()

const apiMock = vi.hoisted(() => ({
  list: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  generateMonthly: vi.fn(),
  lock: vi.fn(),
  unlock: vi.fn(),
}))

vi.mock('../../../shared/api/businessPeriodApi', () => ({
  businessPeriodApi: apiMock,
}))

const openPeriod: BusinessPeriodRecord = {
  id: 1,
  periodCode: '2026-07',
  periodName: '2026年07月',
  startDate: '2026-07-01',
  endDate: '2026-07-31',
  status: 'OPEN',
  statusName: '开放',
  lockedBy: null,
  lockedAt: null,
  lockReason: null,
  unlockedBy: null,
  unlockedAt: null,
  unlockReason: null,
}

const lockedPeriod: BusinessPeriodRecord = {
  ...openPeriod,
  id: 2,
  periodCode: '2026-06',
  periodName: '2026年06月',
  startDate: '2026-06-01',
  endDate: '2026-06-30',
  status: 'LOCKED',
  statusName: '已锁定',
  lockedBy: 'admin',
  lockedAt: '2026-07-01T08:00:00+08:00',
  lockReason: '月度经营数据核对完成',
}

const emptyPage: BusinessPeriodPageResult = {
  items: [],
  page: 1,
  pageSize: 10,
  total: 0,
  totalPages: 0,
}

function page(items: BusinessPeriodRecord[]): BusinessPeriodPageResult {
  return {
    items,
    page: 1,
    pageSize: 10,
    total: items.length,
    totalPages: 1,
  }
}

function mountPeriods(permissions = [
  'system:business-period:view',
  'system:business-period:create',
  'system:business-period:update',
  'system:business-period:lock',
  'system:business-period:unlock',
]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  return mount(BusinessPeriodListView, {
    global: {
      plugins: [pinia, ElementPlus],
    },
  })
}

describe('业务期间管理页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMock.list.mockResolvedValue(emptyPage)
    apiMock.create.mockResolvedValue(openPeriod)
    apiMock.update.mockResolvedValue(openPeriod)
    apiMock.generateMonthly.mockResolvedValue([openPeriod])
    apiMock.lock.mockResolvedValue(lockedPeriod)
    apiMock.unlock.mockResolvedValue(openPeriod)
  })

  it('展示业务期间列表、锁定状态和行操作', async () => {
    apiMock.list.mockResolvedValue(page([openPeriod, lockedPeriod]))
    const wrapper = mountPeriods()
    await flushPromises()

    expect(wrapper.text()).toContain('业务期间')
    expect(wrapper.text()).toContain('2026-07')
    expect(wrapper.text()).toContain('开放')
    expect(wrapper.text()).toContain('2026-06')
    expect(wrapper.text()).toContain('已锁定')
    expect(wrapper.text()).toContain('锁定')
    expect(wrapper.text()).toContain('解锁')
  })

  it('支持筛选、重置和分页请求', async () => {
    apiMock.list.mockResolvedValue(page([lockedPeriod]))
    const wrapper = mountPeriods()
    await flushPromises()

    await wrapper.find('input[name="business-period-code"]').setValue('2026')
    wrapper.findAllComponents({ name: 'ElSelect' })[0]?.vm.$emit('update:modelValue', 'LOCKED')
    await wrapper.find('input[name="business-period-start-date"]').setValue('2026-06-01')
    await wrapper.find('[data-test="business-period-search"]').trigger('click')
    await flushPromises()

    expect(apiMock.list).toHaveBeenLastCalledWith({
      periodCode: '2026',
      status: 'LOCKED',
      startDate: '2026-06-01',
      endDate: '',
      page: 1,
      pageSize: 10,
    })

    wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('size-change', 20)
    await flushPromises()
    expect(apiMock.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, pageSize: 20 }))

    await wrapper.find('[data-test="business-period-reset"]').trigger('click')
    await flushPromises()
    expect(apiMock.list).toHaveBeenLastCalledWith({
      periodCode: '',
      status: undefined,
      startDate: '',
      endDate: '',
      page: 1,
      pageSize: 20,
    })
  })

  it('创建、编辑和按月生成后刷新列表', async () => {
    apiMock.list.mockResolvedValue(page([openPeriod]))
    const wrapper = mountPeriods()
    await flushPromises()

    await wrapper.find('[data-test="create-business-period"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="period-code"]').setValue('2026-08')
    await wrapper.find('input[name="period-name"]').setValue('2026年08月')
    await wrapper.find('input[name="period-start-date"]').setValue('2026-08-01')
    await wrapper.find('input[name="period-end-date"]').setValue('2026-08-31')
    await wrapper.find('[data-test="submit-business-period"]').trigger('click')
    await flushPromises()

    expect(apiMock.create).toHaveBeenCalledWith({
      periodCode: '2026-08',
      periodName: '2026年08月',
      startDate: '2026-08-01',
      endDate: '2026-08-31',
    })

    await wrapper.find('[data-test="edit-business-period"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="submit-business-period"]').trigger('click')
    await flushPromises()
    expect(apiMock.update).toHaveBeenCalledWith(1, expect.objectContaining({ periodCode: '2026-07' }))

    await wrapper.find('[data-test="generate-business-periods"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="generate-start-month"]').setValue('2026-09')
    await wrapper.find('input[name="generate-end-month"]').setValue('2026-12')
    await wrapper.find('[data-test="submit-generate-business-periods"]').trigger('click')
    await flushPromises()
    expect(apiMock.generateMonthly).toHaveBeenCalledWith({ startMonth: '2026-09', endMonth: '2026-12' })
    expect(apiMock.list).toHaveBeenCalledTimes(4)
  })

  it('锁定和解锁必须填写原因并使用统一确认弹窗', async () => {
    apiMock.list.mockResolvedValue(page([openPeriod, lockedPeriod]))
    const wrapper = mountPeriods()
    await flushPromises()

    await wrapper.find('[data-test="lock-business-period"]').trigger('click')
    await flushPromises()
    await wrapper.find('textarea[name="period-action-reason"]').setValue('月度经营数据核对完成')
    await wrapper.find('[data-test="submit-period-action"]').trigger('click')
    await flushPromises()

    expect(confirmActionMock).toHaveBeenCalledWith('确认锁定业务期间“2026-07”？锁定后该期间业务日期的写入会被拒绝。')
    expect(apiMock.lock).toHaveBeenCalledWith(1, { reason: '月度经营数据核对完成' })

    await wrapper.find('[data-test="unlock-business-period"]').trigger('click')
    await flushPromises()
    await wrapper.find('textarea[name="period-action-reason"]').setValue('补录已审批反向业务')
    await wrapper.find('[data-test="submit-period-action"]').trigger('click')
    await flushPromises()

    expect(apiMock.unlock).toHaveBeenCalledWith(2, { reason: '补录已审批反向业务' })
  })

  it('无权限时隐藏对应操作按钮', async () => {
    apiMock.list.mockResolvedValue(page([openPeriod, lockedPeriod]))
    const wrapper = mountPeriods(['system:business-period:view'])
    await flushPromises()

    expect(wrapper.find('[data-test="create-business-period"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="generate-business-periods"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="edit-business-period"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="lock-business-period"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="unlock-business-period"]').exists()).toBe(false)
  })

  it('后端返回锁定错误时展示可见提示并保留弹窗', async () => {
    apiMock.list.mockResolvedValue(page([openPeriod]))
    apiMock.lock.mockRejectedValue(new Error('业务日期 2026-07-10 所属期间 2026-07 已锁定'))
    const wrapper = mountPeriods()
    await flushPromises()

    await wrapper.find('[data-test="lock-business-period"]').trigger('click')
    await flushPromises()
    await wrapper.find('textarea[name="period-action-reason"]').setValue('月度经营数据核对完成')
    await wrapper.find('[data-test="submit-period-action"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('业务日期 2026-07-10 所属期间 2026-07 已锁定')
    expect(wrapper.text()).toContain('锁定业务期间')
  })
})
