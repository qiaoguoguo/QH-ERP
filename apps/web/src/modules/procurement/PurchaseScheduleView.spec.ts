import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PageResult } from '../../shared/api/accountPermissionApi'
import type {
  PurchaseOrderDetailRecord,
  PurchaseScheduleRecord,
} from '../../shared/api/procurementApi'
import { useAuthStore } from '../../stores/authStore'
import PurchaseScheduleView from './PurchaseScheduleView.vue'

const procurementApiMock = vi.hoisted(() => ({
  orders: {
    get: vi.fn(),
  },
  schedules: {
    list: vi.fn(),
    replace: vi.fn(),
    close: vi.fn(),
  },
}))

const documentPlatformApiMock = vi.hoisted(() => ({
  documentTasks: {
    get: vi.fn(),
    errors: vi.fn(),
    download: vi.fn(),
    cancel: vi.fn(),
  },
  imports: {
    confirm: vi.fn(),
  },
  exports: {
    createProcurementSchedules: vi.fn(),
  },
}))

vi.mock('../../shared/api/procurementApi', () => ({
  procurementApi: procurementApiMock,
}))

vi.mock('../../shared/api/documentPlatformApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/documentPlatformApi')>()),
  documentPlatformApi: documentPlatformApiMock,
  createIdempotencyKey: () => 'schedule-task-key',
}))

const orderRecord: PurchaseOrderDetailRecord = {
  id: 99,
  orderNo: 'PO-20260704-001',
  supplierId: 100,
  supplierCode: 'SUP-A',
  supplierName: '华东五金',
  orderDate: '2026-07-04',
  expectedArrivalDate: '2026-07-20',
  status: 'CONFIRMED',
  lineCount: 1,
  totalQuantity: '12.500000',
  receivedQuantity: '5.000000',
  remainingQuantity: '7.500000',
  inTransitQuantity: '7.500000',
  inTransitStatus: 'DUE_SOON',
  inTransitStatusName: '临近到货',
  currency: 'CNY',
  allowedActions: ['UPDATE_SCHEDULES'],
  version: 7,
  createdByName: '采购员',
  createdAt: '2026-07-04T08:00:00+08:00',
  updatedAt: '2026-07-04T09:00:00+08:00',
  lines: [{
    id: 501,
    lineNo: 10,
    materialId: 10,
    materialCode: 'RM-001',
    materialName: '冷轧钢板',
    materialSpec: '1.5mm',
    unitId: 2,
    unitName: '千克',
    quantity: '12.500000',
    receivedQuantity: '5.000000',
    remainingQuantity: '7.500000',
    inTransitQuantity: '7.500000',
    inTransitStatus: 'DUE_SOON',
    inTransitStatusName: '临近到货',
    currency: 'CNY',
    unitPrice: '3.100000',
    expectedArrivalDate: '2026-07-20',
  }],
  receipts: [],
}

const schedules: PurchaseScheduleRecord[] = [{
  id: 601,
  orderId: 99,
  orderLineId: 501,
  orderNo: 'PO-20260704-001',
  lineNo: 10,
  materialId: 10,
  materialCode: 'RM-001',
  materialName: '冷轧钢板',
  scheduleSeq: 10,
  expectedArrivalDate: '2026-07-20',
  plannedQuantity: '12.500000',
  receivedQuantity: '5.000000',
  remainingQuantity: '7.500000',
  status: 'PARTIALLY_RECEIVED',
  statusName: '部分到货',
  closeReason: null,
  remark: '首批到货',
  allowedActions: ['CLOSE'],
  version: 31,
}]

function pageResult(items: PurchaseScheduleRecord[]): PageResult<PurchaseScheduleRecord> {
  return {
    items,
    total: items.length,
    page: 1,
    pageSize: 50,
  }
}

async function mountScheduleView(permissions = [
  'procurement:order:view',
  'procurement:order:update',
  'procurement:order:close',
  'platform:document-task:create',
  'procurement:document:export',
]) {
  procurementApiMock.orders.get.mockResolvedValue(orderRecord)
    procurementApiMock.schedules.list.mockResolvedValue(pageResult(schedules))
    procurementApiMock.schedules.replace.mockResolvedValue(pageResult(schedules))
    procurementApiMock.schedules.close.mockResolvedValue({ ...schedules[0], status: 'CLOSED', closeReason: '项目设计变更' })
    documentPlatformApiMock.exports.createProcurementSchedules.mockResolvedValue({
      id: 904,
      taskNo: 'TASK-SCHEDULE-EXPORT',
      taskType: 'PROCUREMENT_SCHEDULE_EXPORT',
      direction: 'EXPORT',
      stage: 'EXPORT',
      status: 'QUEUED',
      availableActions: ['CANCEL'],
      version: 1,
    })

  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/procurement/orders/:id/schedules', name: 'procurement-order-schedules', component: PurchaseScheduleView },
      { path: '/procurement/orders/:id', name: 'procurement-order-detail', component: { render: () => null } },
    ],
  })
  await router.push('/procurement/orders/99/schedules')
  await router.isReady()
  const wrapper = mount(PurchaseScheduleView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('采购到货计划页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载订单到货计划并展示首屏决策字段', async () => {
    const { wrapper } = await mountScheduleView()

    expect(procurementApiMock.orders.get).toHaveBeenCalledWith('99')
    expect(procurementApiMock.schedules.list).toHaveBeenCalledWith('99', {
      status: undefined,
      expectedDateFrom: '',
      expectedDateTo: '',
      page: 1,
      pageSize: 50,
    })
    expect(wrapper.text()).toContain('PO-20260704-001')
    expect(wrapper.text()).toContain('计划序号')
    expect(wrapper.text()).toContain('RM-001 冷轧钢板')
    expect(wrapper.text()).toContain('12.5')
    expect(wrapper.text()).toContain('5')
    expect(wrapper.text()).toContain('7.5')
    expect(wrapper.text()).toContain('2026-07-20')
    expect(wrapper.text()).toContain('部分到货')
    expect(wrapper.text()).toContain('关闭原因：未关闭')
  })

  it('批量保存使用订单 version、字符串数量和非空幂等键，失败后只刷新不重放', async () => {
    procurementApiMock.schedules.replace.mockRejectedValueOnce(new Error('到货计划版本已变化'))
    const { wrapper } = await mountScheduleView()

    await wrapper.find('[data-test="schedule-quantity-601"]').setValue('12.500000')
    await wrapper.find('[data-test="save-purchase-schedules"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.schedules.replace).toHaveBeenCalledTimes(1)
    expect(procurementApiMock.schedules.replace).toHaveBeenCalledWith(99, expect.objectContaining({
      version: 7,
      idempotencyKey: expect.any(String),
      lines: [expect.objectContaining({
        orderLineId: 501,
        scheduleSeq: 10,
        expectedArrivalDate: '2026-07-20',
        plannedQuantity: '12.500000',
      })],
    }))
    expect(procurementApiMock.schedules.replace.mock.calls[0][1].idempotencyKey).not.toHaveLength(0)
    expect(wrapper.text()).toContain('到货计划版本已变化')
    expect(procurementApiMock.schedules.list).toHaveBeenCalledTimes(2)
  })

  it('关闭计划只按 allowedActions 展示动作，并携带计划 version、原因和幂等键', async () => {
    const { wrapper } = await mountScheduleView()

    expect(wrapper.find('[data-test="close-purchase-schedule-601"]').exists()).toBe(true)
    await wrapper.find('[data-test="schedule-close-reason-601"]').setValue('项目设计变更')
    await wrapper.find('[data-test="close-purchase-schedule-601"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.schedules.close).toHaveBeenCalledWith(99, 601, expect.objectContaining({
      version: 31,
      reason: '项目设计变更',
      idempotencyKey: expect.any(String),
    }))
    expect(procurementApiMock.schedules.close.mock.calls[0][2].idempotencyKey).not.toHaveLength(0)

    vi.clearAllMocks()
    const readonly = await mountScheduleView(['procurement:order:view'])
    expect(readonly.wrapper.find('[data-test="save-purchase-schedules"]').exists()).toBe(false)
    expect(readonly.wrapper.find('[data-test="close-purchase-schedule-601"]').exists()).toBe(false)
  })

  it('按订单上下文创建到货计划当前筛选导出任务并显示任务面板', async () => {
    const { wrapper } = await mountScheduleView()

    const statusSelect = wrapper.findAllComponents({ name: 'ElSelect' })[0]
    statusSelect.vm.$emit('update:modelValue', 'PARTIALLY_RECEIVED')
    await wrapper.find('input[name="schedule-expected-date-from"]').setValue('2026-07-01')
    await wrapper.find('input[name="schedule-expected-date-to"]').setValue('2026-07-31')
    await wrapper.find('[data-test="search-purchase-schedules"]').trigger('click')
    await flushPromises()
    expect(procurementApiMock.schedules.list).toHaveBeenLastCalledWith('99', {
      status: 'PARTIALLY_RECEIVED',
      expectedDateFrom: '2026-07-01',
      expectedDateTo: '2026-07-31',
      page: 1,
      pageSize: 50,
    })

    await wrapper.find('[data-test="export-purchase-schedules"]').trigger('click')
    await flushPromises()

    expect(documentPlatformApiMock.exports.createProcurementSchedules).toHaveBeenCalledWith(99, {
      status: 'PARTIALLY_RECEIVED',
      expectedDateFrom: '2026-07-01',
      expectedDateTo: '2026-07-31',
      idempotencyKey: 'schedule-task-key',
    })
    expect(wrapper.text()).toContain('TASK-SCHEDULE-EXPORT')
    expect(wrapper.text()).toContain('到货计划导出')

    const readonly = await mountScheduleView(['procurement:order:view'])
    expect(readonly.wrapper.find('[data-test="export-purchase-schedules"]').exists()).toBe(false)
  })
})
