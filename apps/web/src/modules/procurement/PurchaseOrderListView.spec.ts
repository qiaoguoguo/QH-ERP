import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PageResult } from '../../shared/api/accountPermissionApi'
import type { PartnerRecord } from '../../shared/api/masterDataApi'
import type { PurchaseOrderSummaryRecord } from '../../shared/api/procurementApi'
import { useAuthStore } from '../../stores/authStore'
import PurchaseOrderListView from './PurchaseOrderListView.vue'

const procurementApiMock = vi.hoisted(() => ({
  orders: {
    list: vi.fn(),
    confirm: vi.fn(),
    cancel: vi.fn(),
    close: vi.fn(),
  },
}))

const masterDataApiMock = vi.hoisted(() => ({
  suppliers: {
    list: vi.fn(),
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
    createProcurementOrders: vi.fn(),
  },
}))

vi.mock('../../shared/api/procurementApi', () => ({
  procurementApi: procurementApiMock,
}))

vi.mock('../../shared/api/masterDataApi', () => ({
  masterDataApi: masterDataApiMock,
}))

vi.mock('../../shared/api/documentPlatformApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/documentPlatformApi')>()),
  documentPlatformApi: documentPlatformApiMock,
  createIdempotencyKey: () => 'order-export-key',
}))

const supplierA: PartnerRecord = {
  id: 100,
  code: 'SUP-A',
  name: '华东五金',
  status: 'ENABLED',
}

const draftOrder: PurchaseOrderSummaryRecord = {
  id: 1,
  orderNo: 'PO-DRAFT-001',
  supplierId: 100,
  supplierCode: 'SUP-A',
  supplierName: '华东五金',
  orderDate: '2026-07-04',
  expectedArrivalDate: '2026-07-10',
  status: 'DRAFT',
  procurementMode: 'PROJECT',
  projectId: 30,
  projectCode: 'PRJ-024',
  projectName: '华东产线改造',
  requisitionNo: 'REQ-024-001',
  inquiryNo: 'INQ-024-001',
  quoteNo: 'SQ-024-001',
  agreementNo: 'PA-024-001',
  approvalStatusName: '待提交',
  exceptionApprovalStatus: 'NOT_REQUIRED',
  exceptionReason: null,
  priceSourceType: 'QUOTE',
  priceSourceTypeName: '供应商报价',
  priceSourceNo: 'SQ-024-001',
  priceSourceReason: '最低有效报价',
  taxExcludedUnitPrice: '10.000000',
  taxIncludedUnitPrice: '11.300000',
  taxRate: '0.130000',
  lineCount: 2,
  totalQuantity: '100.000000',
  receivedQuantity: '0.000000',
  remainingQuantity: '100.000000',
  inTransitQuantity: '0.000000',
  inTransitStatus: 'NOT_COUNTED',
  inTransitStatusName: '不计入在途',
  nextArrivalDate: '2026-07-09',
  currency: 'CNY',
  allowedActions: ['UPDATE', 'CONFIRM', 'CANCEL'],
  version: 11,
  remark: '首批采购',
  createdByName: '采购员',
  createdAt: '2026-07-04T08:00:00+08:00',
  updatedAt: '2026-07-04T09:00:00+08:00',
}

const confirmedOrder: PurchaseOrderSummaryRecord = {
  ...draftOrder,
  id: 2,
  orderNo: 'PO-CONF-001',
  status: 'CONFIRMED',
  totalQuantity: '50.000000',
  receivedQuantity: '0.000000',
  remainingQuantity: '50.000000',
  inTransitQuantity: '50.000000',
  inTransitStatus: 'NORMAL',
  inTransitStatusName: '正常在途',
  allowedActions: ['CREATE_RECEIPT'],
  version: 12,
}

const partialOrder: PurchaseOrderSummaryRecord = {
  ...draftOrder,
  id: 3,
  orderNo: 'PO-PART-001',
  status: 'PARTIALLY_RECEIVED',
  totalQuantity: '80.000000',
  receivedQuantity: '30.000000',
  remainingQuantity: '50.000000',
  inTransitQuantity: '50.000000',
  inTransitStatus: 'DUE_SOON',
  inTransitStatusName: '临近到货',
  closeReason: '供应商延期，转补采',
  allowedActions: ['CLOSE'],
  version: 13,
}

const receivedOrder: PurchaseOrderSummaryRecord = {
  ...draftOrder,
  id: 4,
  orderNo: 'PO-RECV-001',
  status: 'RECEIVED',
  totalQuantity: '40.000000',
  receivedQuantity: '40.000000',
  remainingQuantity: '0.000000',
  inTransitQuantity: '0.000000',
  inTransitStatus: 'NOT_COUNTED',
  inTransitStatusName: '不计入在途',
  allowedActions: [],
  version: 14,
}

const orderPage: PageResult<PurchaseOrderSummaryRecord> = {
  items: [draftOrder, confirmedOrder, partialOrder, receivedOrder],
  page: 1,
  pageSize: 10,
  total: 4,
  totalPages: 1,
}

const emptyOrderPage: PageResult<PurchaseOrderSummaryRecord> = {
  items: [],
  page: 1,
  pageSize: 10,
  total: 0,
  totalPages: 0,
}

function buttonsByText(wrapper: VueWrapper, text: string): VueWrapper[] {
  return wrapper.findAllComponents({ name: 'ElButton' }).filter((button) => button.text().trim() === text)
}

async function setSelectValue(wrapper: VueWrapper, index: number, value: unknown) {
  const select = wrapper.findAllComponents({ name: 'ElSelect' })[index] as VueWrapper | undefined
  expect(select?.exists()).toBe(true)
  select?.vm.$emit('update:modelValue', value)
  await flushPromises()
}

async function mountList(permissions = [
  'procurement:order:view',
  'procurement:order:create',
  'procurement:order:update',
  'procurement:order:confirm',
  'procurement:order:cancel',
  'procurement:order:close',
  'procurement:receipt:create',
  'platform:document-task:create',
  'procurement:document:export',
]) {
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
      { path: '/procurement/orders', name: 'procurement-orders', component: PurchaseOrderListView },
      { path: '/procurement/orders/create', name: 'procurement-order-create', component: { render: () => null } },
      { path: '/procurement/orders/:id', name: 'procurement-order-detail', component: { render: () => null } },
      { path: '/procurement/orders/:id/edit', name: 'procurement-order-edit', component: { render: () => null } },
      {
        path: '/procurement/orders/:orderId/receipts/create',
        name: 'procurement-receipt-create',
        component: { render: () => null },
      },
    ],
  })
  await router.push('/procurement/orders')
  await router.isReady()
  const wrapper = mount(PurchaseOrderListView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('采购订单列表页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    procurementApiMock.orders.list.mockResolvedValue(orderPage)
    procurementApiMock.orders.confirm.mockResolvedValue({ ...draftOrder, status: 'CONFIRMED' })
    procurementApiMock.orders.cancel.mockResolvedValue({ ...draftOrder, status: 'CANCELLED' })
    procurementApiMock.orders.close.mockResolvedValue({ ...confirmedOrder, status: 'CLOSED' })
    masterDataApiMock.suppliers.list.mockResolvedValue({
      items: [supplierA],
      page: 1,
      pageSize: 200,
      total: 1,
      totalPages: 1,
    })
    documentPlatformApiMock.exports.createProcurementOrders.mockResolvedValue({
      id: 908,
      taskNo: 'TASK-ORDER-EXPORT',
      taskType: 'PROCUREMENT_ORDER_EXPORT',
      direction: 'EXPORT',
      stage: 'EXPORT',
      status: 'QUEUED',
      availableActions: ['CANCEL'],
      version: 1,
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('初始加载供应商和采购订单并展示关键字段', async () => {
    const { wrapper } = await mountList()

    expect(masterDataApiMock.suppliers.list).toHaveBeenCalledWith({
      keyword: '',
      status: 'ENABLED',
      page: 1,
      pageSize: 200,
    })
    expect(procurementApiMock.orders.list).toHaveBeenCalledWith({
      keyword: '',
      supplierId: undefined,
      status: undefined,
      dateFrom: '',
      dateTo: '',
      expectedDateFrom: '',
      expectedDateTo: '',
      page: 1,
      pageSize: 10,
    })
    expect(wrapper.text()).toContain('PO-DRAFT-001')
    expect(wrapper.text()).toContain('华东五金')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.text()).toContain('项目专采 · PRJ-024/华东产线改造')
    expect(wrapper.text()).toContain('请购 REQ-024-001')
    expect(wrapper.text()).toContain('供应商报价 SQ-024-001')
    expect(wrapper.text()).toContain('协议 PA-024-001')
    expect(wrapper.text()).toContain('审批状态：待提交')
    expect(wrapper.text()).toContain('例外审批：不需要')
    expect(wrapper.text()).toContain('未税单价 10')
    expect(wrapper.text()).toContain('含税单价 11.3')
    expect(wrapper.text()).toContain('税率 0.13')
    expect(wrapper.text()).toContain('CNY')
    expect(wrapper.text()).toContain('采购在途参考')
    expect(wrapper.text()).toContain('在途状态')
    expect(wrapper.text()).toContain('正常在途')
    expect(wrapper.text()).toContain('下一到货：2026-07-09')
    expect(wrapper.text()).toContain('结案原因：供应商延期，转补采')
    expect(wrapper.text()).toContain('100')
    expect(wrapper.text()).toContain('采购员')
    expect(wrapper.find('[data-test="create-purchase-order"]').exists()).toBe(true)

    await wrapper.find('[data-test="export-purchase-orders"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.exports.createProcurementOrders).toHaveBeenCalledWith({
      keyword: '',
      supplierId: undefined,
      status: undefined,
      dateFrom: '',
      dateTo: '',
      expectedDateFrom: '',
      expectedDateTo: '',
      idempotencyKey: 'order-export-key',
    })
    expect(wrapper.text()).toContain('TASK-ORDER-EXPORT')
  })

  it('订单列表从可用行快照展示税价摘要，没有头级税价时不显示横线伪值', async () => {
    procurementApiMock.orders.list.mockResolvedValueOnce({
      items: [{
        ...draftOrder,
        taxRate: undefined,
        taxExcludedUnitPrice: undefined,
        taxIncludedUnitPrice: undefined,
        lines: [
          {
            id: 501,
            lineNo: 10,
            materialId: 10,
            materialCode: 'RM-001',
            materialName: '冷轧钢板',
            unitId: 2,
            unitName: '千克',
            quantity: '12.500000',
            receivedQuantity: '0.000000',
            remainingQuantity: '12.500000',
            unitPrice: '100.000000',
            taxRate: '0.130000',
            taxExcludedUnitPrice: '100.000000',
            taxIncludedUnitPrice: '113.000000',
            currency: 'CNY',
          },
          {
            id: 502,
            lineNo: 20,
            materialId: 11,
            materialCode: 'RM-002',
            materialName: '铜排',
            unitId: 2,
            unitName: '千克',
            quantity: '2.000000',
            receivedQuantity: '0.000000',
            remainingQuantity: '2.000000',
            unitPrice: '200.000000',
            taxRate: '0.060000',
            taxExcludedUnitPrice: '200.000000',
            taxIncludedUnitPrice: '212.000000',
            currency: 'CNY',
          },
        ],
      }],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
    })

    const { wrapper } = await mountList()

    expect(wrapper.text()).toContain('2 行税价见明细')
    expect(wrapper.text()).not.toContain('未税单价 -')
    expect(wrapper.text()).not.toContain('含税单价 -')
    expect(wrapper.text()).not.toContain('税率 -')
  })

  it('订单列表价格来源使用后端 type-matched sourceNo，不回退到错误来源编号', async () => {
    procurementApiMock.orders.list.mockResolvedValueOnce({
      items: [{
        ...draftOrder,
        priceSourceType: 'QUOTE',
        priceSourceTypeName: '供应商报价',
        priceSourceNo: null,
        sourceNo: 'PQT-024-001',
        quoteNo: 'PRQ-024-001',
      } as PurchaseOrderSummaryRecord & { sourceNo: string }],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
    })

    const { wrapper } = await mountList()

    expect(wrapper.text()).toContain('供应商报价 PQT-024-001')
    expect(wrapper.text()).not.toContain('供应商报价 PRQ-024-001')
  })

  it('支持按关键词、供应商、状态和日期范围筛选并重置', async () => {
    const { wrapper } = await mountList()

    await wrapper.find('input[name="purchase-order-keyword"]').setValue('PO')
    await setSelectValue(wrapper, 0, 100)
    await setSelectValue(wrapper, 1, 'CONFIRMED')
    await wrapper.find('input[name="purchase-order-date-from"]').setValue('2026-07-01')
    await wrapper.find('input[name="purchase-order-date-to"]').setValue('2026-07-31')
    await wrapper.find('input[name="purchase-order-expected-date-from"]').setValue('2026-07-08')
    await wrapper.find('input[name="purchase-order-expected-date-to"]').setValue('2026-07-20')
    await wrapper.find('[data-test="search-purchase-orders"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.orders.list).toHaveBeenLastCalledWith({
      keyword: 'PO',
      supplierId: 100,
      status: 'CONFIRMED',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-31',
      expectedDateFrom: '2026-07-08',
      expectedDateTo: '2026-07-20',
      page: 1,
      pageSize: 10,
    })

    await wrapper.find('[data-test="reset-purchase-orders"]').trigger('click')
    await flushPromises()
    expect(procurementApiMock.orders.list).toHaveBeenLastCalledWith({
      keyword: '',
      supplierId: undefined,
      status: undefined,
      dateFrom: '',
      dateTo: '',
      expectedDateFrom: '',
      expectedDateTo: '',
      page: 1,
      pageSize: 10,
    })
  })

  it('无数据和加载失败时显示明确状态', async () => {
    procurementApiMock.orders.list.mockResolvedValueOnce(emptyOrderPage)
    const { wrapper } = await mountList()

    expect(wrapper.text()).toContain('暂无采购订单')

    procurementApiMock.orders.list.mockRejectedValueOnce(new Error('采购订单接口异常'))
    await wrapper.find('[data-test="search-purchase-orders"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('采购订单接口异常')
    expect(wrapper.text()).toContain('暂无采购订单')
  })

  it('按权限和状态展示采购订单行操作', async () => {
    const { wrapper, router } = await mountList()

    expect(buttonsByText(wrapper, '详情')).toHaveLength(4)
    expect(buttonsByText(wrapper, '编辑')).toHaveLength(1)
    expect(buttonsByText(wrapper, '确认')).toHaveLength(1)
    expect(buttonsByText(wrapper, '取消')).toHaveLength(1)
    expect(buttonsByText(wrapper, '关闭')).toHaveLength(1)
    expect(buttonsByText(wrapper, '创建入库')).toHaveLength(1)

    await wrapper.find('[data-test="create-purchase-receipt"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('procurement-receipt-create')
    expect(router.currentRoute.value.params.orderId).toBe('2')
  })

  it('只读权限仅展示详情入口', async () => {
    const { wrapper } = await mountList(['procurement:order:view'])

    expect(wrapper.find('[data-test="create-purchase-order"]').exists()).toBe(false)
    expect(buttonsByText(wrapper, '详情')).toHaveLength(4)
    expect(buttonsByText(wrapper, '编辑')).toHaveLength(0)
    expect(buttonsByText(wrapper, '确认')).toHaveLength(0)
    expect(buttonsByText(wrapper, '取消')).toHaveLength(0)
    expect(buttonsByText(wrapper, '关闭')).toHaveLength(0)
    expect(buttonsByText(wrapper, '创建入库')).toHaveLength(0)
    expect(wrapper.find('[data-test="export-purchase-orders"]').exists()).toBe(false)
  })

  it('状态允许但 allowedActions 未授权时不展示复杂动作', async () => {
    procurementApiMock.orders.list.mockResolvedValueOnce({
      ...orderPage,
      items: [{ ...draftOrder, allowedActions: [] }],
      total: 1,
    })
    const { wrapper } = await mountList()

    expect(buttonsByText(wrapper, '详情')).toHaveLength(1)
    expect(buttonsByText(wrapper, '编辑')).toHaveLength(0)
    expect(buttonsByText(wrapper, '确认')).toHaveLength(0)
    expect(buttonsByText(wrapper, '取消')).toHaveLength(0)
    expect(buttonsByText(wrapper, '关闭')).toHaveLength(0)
    expect(buttonsByText(wrapper, '创建入库')).toHaveLength(0)
  })

  it('确认、取消和关闭动作成功后刷新列表，失败时显示错误', async () => {
    const { wrapper } = await mountList()

    await wrapper.find('[data-test="confirm-purchase-order"]').trigger('click')
    await flushPromises()
    expect(procurementApiMock.orders.confirm).toHaveBeenCalledWith(1, expect.objectContaining({
      version: 11,
      idempotencyKey: expect.any(String),
    }))
    expect(procurementApiMock.orders.confirm.mock.calls[0][1].idempotencyKey).not.toHaveLength(0)

    await wrapper.find('[data-test="cancel-purchase-order"]').trigger('click')
    await flushPromises()
    expect(procurementApiMock.orders.cancel).toHaveBeenCalledWith(1, expect.objectContaining({
      version: 11,
      idempotencyKey: expect.any(String),
    }))
    expect(procurementApiMock.orders.cancel.mock.calls[0][1].idempotencyKey).not.toHaveLength(0)

    await wrapper.find('[data-test="close-purchase-order"]').trigger('click')
    await flushPromises()
    expect(procurementApiMock.orders.close).toHaveBeenCalledWith(3, expect.objectContaining({
      version: 13,
      idempotencyKey: expect.any(String),
    }))
    expect(procurementApiMock.orders.close.mock.calls[0][1].idempotencyKey).not.toHaveLength(0)

    procurementApiMock.orders.close.mockRejectedValueOnce(new Error('采购订单状态不允许关闭'))
    await wrapper.find('[data-test="close-purchase-order"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('采购订单状态不允许关闭')
    expect(procurementApiMock.orders.list).toHaveBeenCalledTimes(5)
  })
})
