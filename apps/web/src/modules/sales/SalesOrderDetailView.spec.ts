import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PageResult } from '../../shared/api/accountPermissionApi'
import type {
  SalesDeliveryPlanRecord,
  SalesOrderChangeRecord,
} from '../../shared/api/salesFulfillmentApi'
import type { SalesOrderDetailRecord } from '../../shared/api/salesApi'
import { useAuthStore } from '../../stores/authStore'
import SalesOrderDetailView from './SalesOrderDetailView.vue'

const salesFulfillmentApiMock = vi.hoisted(() => ({
  deliveryPlans: {
    listByOrder: vi.fn(),
    replaceForOrder: vi.fn(),
    close: vi.fn(),
  },
  orderChanges: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    submitApproval: vi.fn(),
    cancel: vi.fn(),
  },
  orders: {
    submitCreditOverride: vi.fn(),
    submitShortClose: vi.fn(),
  },
}))

const salesApiMock = vi.hoisted(() => ({
  orders: {
    get: vi.fn(),
    confirm: vi.fn(),
    cancel: vi.fn(),
    close: vi.fn(),
  },
}))

vi.mock('../../shared/api/salesApi', () => ({
  salesApi: salesApiMock,
}))

vi.mock('../../shared/api/salesFulfillmentApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/salesFulfillmentApi')>()),
  salesFulfillmentApi: salesFulfillmentApiMock,
}))

vi.mock('../../shared/api/documentPlatformApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/documentPlatformApi')>()),
  createIdempotencyKey: () => 'sales-order-key',
}))

const draftOrder: SalesOrderDetailRecord = {
  id: 99,
  orderNo: 'SO-20260704-001',
  customerId: 100,
  customerCode: 'CUS-A',
  customerName: '华东客户',
  orderDate: '2026-07-04',
  expectedShipDate: '2026-07-12',
  projectId: 12,
  projectNo: 'SP-202607-001',
  projectName: '华东扩产项目',
  contractId: 55,
  contractNo: 'SC-001',
  externalContractNo: 'EXT-001',
  status: 'DRAFT',
  lineCount: 1,
  totalQuantity: 12.5,
  shippedQuantity: 0,
  remainingQuantity: 12.5,
  totalUntaxedAmount: '1101.250000',
  totalTaxAmount: '143.162500',
  totalTaxIncludedAmount: '1244.412500',
  currency: 'CNY',
  priceSourceType: 'MANUAL',
  priceSourceNo: null,
  creditStatusName: '信用通过',
  creditRestricted: false,
  contractRestricted: false,
  amountRestricted: false,
  allowedActions: ['UPDATE', 'CONFIRM', 'CANCEL'],
  actionDisabledReason: null,
  remark: '首批销售',
  createdByName: '销售员',
  createdAt: '2026-07-04T08:00:00+08:00',
  updatedAt: '2026-07-04T09:00:00+08:00',
  version: 4,
  lines: [
    {
      id: 501,
      lineNo: 10,
      materialId: 10,
      materialCode: 'FG-001',
      materialName: '标准成品',
      materialSpec: 'A1',
      unitId: 2,
      unitName: '件',
      quantity: '12.500000',
      shippedQuantity: 0,
      remainingQuantity: 12.5,
      reservationWarehouseId: 30,
      reservationWarehouseName: '成品仓',
      unitPrice: '88.100000',
      priceSourceType: 'MANUAL',
      priceSourceNo: null,
      untaxedUnitPrice: '78.000000',
      taxIncludedUnitPrice: '88.100000',
      taxRate: '0.130000',
      untaxedAmount: '975.000000',
      taxAmount: '126.750000',
      taxIncludedAmount: '1101.250000',
      expectedShipDate: '2026-07-12',
      remark: '按周发货',
    },
  ],
  shipments: [
    {
      id: 700,
      shipmentNo: 'SS-20260705-001',
      orderId: 99,
      orderNo: 'SO-20260704-001',
      customerId: 100,
      customerName: '华东客户',
      warehouseId: 30,
      warehouseName: '成品仓',
      businessDate: '2026-07-05',
      status: 'POSTED',
      lineCount: 1,
      totalQuantity: 5,
      remark: null,
      createdByName: '仓管员',
      createdAt: '2026-07-05T08:00:00+08:00',
      updatedAt: '2026-07-05T09:00:00+08:00',
      postedByName: '仓管员',
      postedAt: '2026-07-05T09:00:00+08:00',
    },
    {
      id: 701,
      shipmentNo: 'SS-20260706-001',
      orderId: 99,
      orderNo: 'SO-20260704-001',
      customerId: 100,
      customerName: '华东客户',
      warehouseId: 30,
      warehouseName: '成品仓',
      businessDate: '2026-07-06',
      status: 'DRAFT',
      lineCount: 1,
      totalQuantity: 3,
      remark: null,
      createdByName: '仓管员',
      createdAt: '2026-07-06T08:00:00+08:00',
      updatedAt: '2026-07-06T08:30:00+08:00',
      postedByName: null,
      postedAt: null,
    },
  ],
}

const confirmedOrder: SalesOrderDetailRecord = {
  ...draftOrder,
  status: 'CONFIRMED',
  shippedQuantity: 0,
  remainingQuantity: 12.5,
  allowedActions: ['CANCEL', 'CLOSE', 'CREATE_SHIPMENT'],
}

const partialOrder: SalesOrderDetailRecord = {
  ...draftOrder,
  status: 'PARTIALLY_SHIPPED',
  shippedQuantity: 5,
  remainingQuantity: 7.5,
  allowedActions: ['CLOSE', 'CREATE_SHIPMENT', 'SUBMIT_SHORT_CLOSE', 'SUBMIT_CREDIT_OVERRIDE'],
}

const deliveryPlans = [
  {
    id: 910,
    planNo: 'SDP-001',
    orderId: 99,
    orderNo: 'SO-20260704-001',
    orderLineId: 501,
    lineNo: 10,
    customerId: 100,
    customerName: '华东客户',
    projectId: 12,
    projectNo: 'SP-202607-001',
    projectName: '华东扩产项目',
    contractId: 55,
    contractNo: 'SC-001',
    materialId: 10,
    materialCode: 'FG-001',
    materialName: '标准成品',
    unitName: '件',
    planDate: undefined,
    plannedDate: '2026-07-15',
    plannedQuantity: '8.000000',
    shippedQuantity: '3.000000',
    remainingQuantity: '5.000000',
    status: 'PARTIALLY_SHIPPED',
    closeReason: null,
    allowedActions: ['CLOSE'],
    actionDisabledReason: null,
    version: 2,
  },
] as unknown as SalesDeliveryPlanRecord[]

const orderChangesPage: PageResult<SalesOrderChangeRecord> = {
  items: [{
    id: 920,
    changeNo: 'SOC-001',
    orderId: 99,
    orderNo: 'SO-20260704-001',
    status: 'DRAFT',
    approvalStatus: 'SUBMITTED',
    reason: '客户调整交期',
    allowedActions: ['UPDATE', 'SUBMIT_APPROVAL', 'CANCEL'],
    actionDisabledReason: null,
    version: 3,
  }],
  page: 1,
  pageSize: 20,
  total: 1,
  totalPages: 1,
}

function buttonsByText(wrapper: VueWrapper, text: string): VueWrapper[] {
  return wrapper.findAllComponents({ name: 'ElButton' }).filter((button) => button.text().trim() === text)
}

async function mountDetail(
  record: SalesOrderDetailRecord = draftOrder,
  permissions = [
    'sales:order:view',
    'sales:order:update',
    'sales:order:confirm',
    'sales:order:cancel',
    'sales:order:close',
    'sales:order:short-close-submit',
    'sales:shipment:create',
    'sales:shipment:view',
    'sales:delivery-plan:view',
    'sales:delivery-plan:manage',
    'sales:order-change:view',
    'sales:order-change:create',
    'sales:order-change:update',
    'sales:order-change:submit',
    'sales:order-change:cancel',
    'sales:credit:override-submit',
  ],
) {
  salesApiMock.orders.get.mockResolvedValue(record)
  salesFulfillmentApiMock.deliveryPlans.listByOrder.mockResolvedValue({ lines: deliveryPlans })
  salesFulfillmentApiMock.orderChanges.list.mockResolvedValue(orderChangesPage)
  salesFulfillmentApiMock.orderChanges.get.mockResolvedValue({
    ...orderChangesPage.items[0],
    lines: [{
      orderLineId: 501,
      targetQuantity: '14.000000',
      untaxedUnitPrice: '80.000000',
      taxIncludedUnitPrice: '90.400000',
      taxRate: '0.130000',
      plannedDate: '2026-08-01',
    }],
  })
  salesFulfillmentApiMock.deliveryPlans.replaceForOrder.mockResolvedValue({ lines: deliveryPlans })
  salesFulfillmentApiMock.deliveryPlans.close.mockResolvedValue({ ...deliveryPlans[0], status: 'CLOSED', closeReason: '客户调整' })
  salesFulfillmentApiMock.orderChanges.create.mockResolvedValue(orderChangesPage.items[0])
  salesFulfillmentApiMock.orderChanges.update.mockResolvedValue({ ...orderChangesPage.items[0], version: 4 })
  salesFulfillmentApiMock.orderChanges.submitApproval.mockResolvedValue({ id: 31, status: 'SUBMITTED' })
  salesFulfillmentApiMock.orderChanges.cancel.mockResolvedValue({ ...orderChangesPage.items[0], status: 'CANCELLED', version: 4 })
  salesFulfillmentApiMock.orders.submitCreditOverride.mockResolvedValue({ id: 1 })
  salesFulfillmentApiMock.orders.submitShortClose.mockResolvedValue({ id: 2 })
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
      { path: '/sales/orders', name: 'sales-orders', component: { render: () => null } },
      { path: '/sales/orders/:id', name: 'sales-order-detail', component: SalesOrderDetailView },
      { path: '/sales/orders/:id/edit', name: 'sales-order-edit', component: { render: () => null } },
      {
        path: '/sales/orders/:orderId/shipments/create',
        name: 'sales-shipment-create',
        component: { render: () => null },
      },
      { path: '/sales/shipments/:id', name: 'sales-shipment-detail', component: { render: () => null } },
    ],
  })
  await router.push('/sales/orders/99')
  await router.isReady()
  const wrapper = mount(SalesOrderDetailView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('销售订单详情页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    salesApiMock.orders.confirm.mockResolvedValue(confirmedOrder)
    salesApiMock.orders.cancel.mockResolvedValue({ ...draftOrder, status: 'CANCELLED' })
    salesApiMock.orders.close.mockResolvedValue({ ...confirmedOrder, status: 'CLOSED' })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('加载销售订单详情并展示汇总、基础信息、明细和出库摘要', async () => {
    const { wrapper } = await mountDetail()

    expect(salesApiMock.orders.get).toHaveBeenCalledWith('99')
    expect(wrapper.text()).toContain('SO-20260704-001')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.text()).toContain('总数量')
    expect(wrapper.text()).toContain('12.5')
    expect(wrapper.text()).toContain('已出库')
    expect(wrapper.text()).toContain('未出库')
    expect(wrapper.text()).toContain('华东客户')
    expect(wrapper.text()).toContain('手工订单 -> 交付计划 -> 出库 -> 退货/关闭')
    expect(wrapper.text()).toContain('含税金额')
    expect(wrapper.text()).toContain('1244.4125')
    expect(wrapper.text()).toContain('信用通过')
    expect(wrapper.text()).toContain('项目合同')
    expect(wrapper.text()).toContain('SP-202607-001 华东扩产项目 / SC-001')
    expect(wrapper.text()).toContain('FG-001 标准成品')
    expect(wrapper.text()).toContain('预留仓库')
    expect(wrapper.text()).toContain('成品仓')
    expect(wrapper.text()).toContain('含税单价')
    expect(wrapper.text()).toContain('88.1')
    expect(wrapper.text()).toContain('税率')
    expect(wrapper.text()).toContain('0.13')
    expect(wrapper.text()).toContain('件')
    expect(wrapper.text()).toContain('出库记录')
    expect(wrapper.text()).toContain('SS-20260705-001')
    expect(wrapper.text()).toContain('SS-20260706-001')
    expect(wrapper.text()).toContain('成品仓')
    expect(wrapper.text()).toContain('已过账')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.text()).not.toContain('POSTED')
    expect(wrapper.text()).not.toContain('DRAFT')
  })

  it('订单详情头部税价消费后端 canonical taxIncludedAmount 字段', async () => {
    const canonicalOrder = {
      ...draftOrder,
      totalTaxIncludedAmount: '0.000000',
      taxIncludedAmount: '3200.000000',
    } as unknown as SalesOrderDetailRecord

    const { wrapper } = await mountDetail(canonicalOrder)

    expect(wrapper.text()).toContain('含税 3200 CNY')
    expect(wrapper.text()).not.toContain('含税 - CNY')
    expect(wrapper.text()).not.toContain('含税 0 CNY')
  })

  it('历史未关联项目合同的销售订单显示明确空态', async () => {
    const legacyOrder: SalesOrderDetailRecord = {
      ...draftOrder,
      projectId: null,
      projectNo: null,
      projectName: null,
      contractId: null,
      contractNo: null,
      externalContractNo: null,
    }
    const { wrapper } = await mountDetail(legacyOrder)

    expect(wrapper.text()).toContain('项目合同')
    expect(wrapper.text()).toContain('未关联项目')
  })

  it('按权限和状态展示操作按钮并进入销售出库占位路由', async () => {
    const { wrapper, router } = await mountDetail(confirmedOrder)

    expect(buttonsByText(wrapper, '编辑')).toHaveLength(0)
    expect(buttonsByText(wrapper, '确认')).toHaveLength(0)
    expect(buttonsByText(wrapper, '取消')).toHaveLength(1)
    expect(buttonsByText(wrapper, '关闭')).toHaveLength(1)
    expect(buttonsByText(wrapper, '创建出库')).toHaveLength(1)
    expect(wrapper.find('[data-test="view-sales-shipment-summary"]').exists()).toBe(true)

    await wrapper.find('[data-test="create-sales-shipment-detail"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('sales-shipment-create')
    expect(router.currentRoute.value.params.orderId).toBe('99')

    const readonly = await mountDetail(confirmedOrder, ['sales:order:view'])
    expect(buttonsByText(readonly.wrapper, '取消')).toHaveLength(0)
    expect(buttonsByText(readonly.wrapper, '关闭')).toHaveLength(0)
    expect(buttonsByText(readonly.wrapper, '创建出库')).toHaveLength(0)
    expect(readonly.wrapper.find('[data-test="view-sales-shipment-summary"]').exists()).toBe(false)
  })

  it('确认、取消和关闭动作成功后刷新详情', async () => {
    const draft = await mountDetail(draftOrder)
    await draft.wrapper.find('[data-test="confirm-sales-order-detail"]').trigger('click')
    await flushPromises()
    expect(salesApiMock.orders.confirm).toHaveBeenCalledWith(99, expect.objectContaining({
      version: 4,
      idempotencyKey: expect.any(String),
    }))
    expect(salesApiMock.orders.get).toHaveBeenCalledTimes(2)

    vi.clearAllMocks()
    const cancellable = await mountDetail(confirmedOrder)
    await cancellable.wrapper.find('[data-test="cancel-sales-order-detail"]').trigger('click')
    await flushPromises()
    expect(salesApiMock.orders.cancel).toHaveBeenCalledWith(99, expect.objectContaining({
      version: 4,
      reason: '客户取消',
      idempotencyKey: expect.any(String),
    }))
    expect(salesApiMock.orders.get).toHaveBeenCalledTimes(2)

    vi.clearAllMocks()
    const closable = await mountDetail(partialOrder)
    await closable.wrapper.find('[data-test="close-sales-order-detail"]').trigger('click')
    await flushPromises()
    expect(salesApiMock.orders.close).toHaveBeenCalledWith(99, expect.objectContaining({
      version: 4,
      reason: '履约完成',
      idempotencyKey: expect.any(String),
    }))
    expect(salesApiMock.orders.get).toHaveBeenCalledTimes(2)
  })

  it('确认前发现明细缺少预留仓库时显示业务提示并阻止调用确认接口', async () => {
    const legacyDraftOrder: SalesOrderDetailRecord = {
      ...draftOrder,
      lines: draftOrder.lines.map((line) => ({
        ...line,
        reservationWarehouseId: null,
        reservationWarehouseName: null,
      })),
    }
    const { wrapper } = await mountDetail(legacyDraftOrder)

    await wrapper.find('[data-test="confirm-sales-order-detail"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('销售订单确认前每行必须选择预留仓库，确认只会按预留仓库现货库存预留，不使用采购在途')
    expect(salesApiMock.orders.confirm).not.toHaveBeenCalled()
  })

  it('展示订单交付计划、订单变更、短交关闭和信用例外动作', async () => {
    const { wrapper } = await mountDetail(partialOrder)

    expect(salesFulfillmentApiMock.deliveryPlans.listByOrder).toHaveBeenCalledWith(99)
    expect(salesFulfillmentApiMock.orderChanges.list).toHaveBeenCalledWith(99, {
      status: undefined,
      page: 1,
      pageSize: 20,
    })
    expect(wrapper.text()).toContain('交付计划')
    expect(wrapper.text()).toContain('SDP-001')
    expect(wrapper.text()).toContain('2026-07-15')
    expect(wrapper.text()).toContain('计划/已发/剩余')
    expect(wrapper.text()).toContain('8/3/5')
    expect(wrapper.text()).toContain('订单变更')
    expect(wrapper.text()).toContain('SOC-001')
    expect(wrapper.text()).toContain('已提交')
    expect(buttonsByText(wrapper, '提交短交关闭审批')).toHaveLength(1)
    expect(buttonsByText(wrapper, '提交信用例外审批')).toHaveLength(1)

    await buttonsByText(wrapper, '提交短交关闭审批')[0].trigger('click')
    await flushPromises()
    await wrapper.find('textarea[name="sales-order-short-close-reason"]').setValue('客户接受短交')
    await wrapper.find('[data-test="confirm-sales-order-short-close"]').trigger('click')
    await flushPromises()
    expect(salesFulfillmentApiMock.orders.submitShortClose).toHaveBeenCalledWith(99, {
      version: 4,
      reason: '客户接受短交',
      idempotencyKey: 'sales-order-key',
    })

    await buttonsByText(wrapper, '提交信用例外审批')[0].trigger('click')
    await flushPromises()
    await wrapper.find('textarea[name="sales-order-credit-override-reason"]').setValue('客户临时超限')
    await wrapper.find('[data-test="confirm-sales-order-credit-override"]').trigger('click')
    await flushPromises()
    expect(salesFulfillmentApiMock.orders.submitCreditOverride).toHaveBeenCalledWith(99, {
      version: 4,
      reason: '客户临时超限',
      idempotencyKey: 'sales-order-key',
    })
    expect(salesApiMock.orders.get).toHaveBeenCalledTimes(3)
  })

  it('后端缺省数组或交付计划分页形状时显示空态，不让详情永久加载或抛出 undefined.length', async () => {
    const sparseOrder = {
      ...partialOrder,
      shipments: undefined,
    } as unknown as SalesOrderDetailRecord
    salesFulfillmentApiMock.deliveryPlans.listByOrder.mockResolvedValueOnce({ items: [], total: 0, page: 1, pageSize: 20 })
    salesFulfillmentApiMock.orderChanges.list.mockResolvedValueOnce({ page: 1, pageSize: 20, total: 0 })

    const { wrapper } = await mountDetail(sparseOrder)

    expect(wrapper.text()).toContain('暂无交付计划')
    expect(wrapper.text()).toContain('暂无订单变更')
    expect(wrapper.text()).toContain('暂无出库记录')
    expect(wrapper.text()).not.toContain('交付计划加载中')
    expect(wrapper.text()).not.toContain('订单变更加载中')
  })

  it('订单详情提供订单变更创建、更新、提交审批和取消闭环', async () => {
    const actionableOrder = {
      ...partialOrder,
      allowedActions: [...(partialOrder.allowedActions ?? []), 'CREATE_CHANGE'],
    } as SalesOrderDetailRecord
    const { wrapper } = await mountDetail(actionableOrder)

    await wrapper.find('[data-test="create-sales-order-change"]').trigger('click')
    await flushPromises()
    await wrapper.find('textarea[name="sales-order-change-reason"]').setValue('客户调整数量')
    await wrapper.find('input[name="sales-order-change-target-501"]').setValue('15.000000')
    await wrapper.find('[data-test="confirm-sales-order-change"]').trigger('click')
    await flushPromises()

    expect(salesFulfillmentApiMock.orderChanges.create).toHaveBeenCalledWith(99, {
      version: 4,
      reason: '客户调整数量',
      idempotencyKey: 'sales-order-key',
      lines: [{ orderLineId: 501, targetQuantity: '15.000000' }],
    })
    expect(wrapper.find('textarea[name="sales-order-change-reason"]').exists()).toBe(false)
    expect(salesFulfillmentApiMock.orderChanges.list).toHaveBeenCalledTimes(2)

    await wrapper.find('[data-test="edit-sales-order-change-920"]').trigger('click')
    await flushPromises()
    expect(salesFulfillmentApiMock.orderChanges.get).toHaveBeenCalledWith(920)
    expect((wrapper.find('input[name="sales-order-change-target-501"]').element as HTMLInputElement).value).toBe('14.000000')
    expect((wrapper.find('input[name="sales-order-change-untaxed-price-501"]').element as HTMLInputElement).value).toBe('80.000000')
    expect((wrapper.find('input[name="sales-order-change-tax-included-price-501"]').element as HTMLInputElement).value).toBe('90.400000')
    expect((wrapper.find('input[name="sales-order-change-tax-rate-501"]').element as HTMLInputElement).value).toBe('0.130000')
    expect((wrapper.find('input[name="sales-order-change-planned-date-501"]').element as HTMLInputElement).value).toBe('2026-08-01')
    await wrapper.find('textarea[name="sales-order-change-reason"]').setValue('更新变更原因')
    await wrapper.find('[data-test="confirm-sales-order-change"]').trigger('click')
    await flushPromises()
    expect(salesFulfillmentApiMock.orderChanges.update).toHaveBeenCalledWith(920, {
      version: 3,
      reason: '更新变更原因',
      idempotencyKey: 'sales-order-key',
      lines: [{
        orderLineId: 501,
        targetQuantity: '14.000000',
        untaxedUnitPrice: '80.000000',
        taxIncludedUnitPrice: '90.400000',
        taxRate: '0.130000',
        plannedDate: '2026-08-01',
      }],
    })

    await wrapper.find('[data-test="submit-sales-order-change-920"]').trigger('click')
    await flushPromises()
    expect(salesFulfillmentApiMock.orderChanges.submitApproval).toHaveBeenCalledWith(920, {
      version: 3,
      reason: '提交订单变更审批',
      idempotencyKey: 'sales-order-key',
    })

    await wrapper.find('[data-test="cancel-sales-order-change-920"]').trigger('click')
    await flushPromises()
    expect(salesFulfillmentApiMock.orderChanges.cancel).toHaveBeenCalledWith(920, {
      version: 3,
      reason: '取消订单变更',
      idempotencyKey: 'sales-order-key',
    })
  })

  it('订单变更编辑入口只使用 update 权限，create 权限不能代替', async () => {
    const actionableOrder = {
      ...partialOrder,
      allowedActions: [...(partialOrder.allowedActions ?? []), 'CREATE_CHANGE'],
    } as SalesOrderDetailRecord
    const createOnly = await mountDetail(actionableOrder, [
      'sales:order:view',
      'sales:order-change:view',
      'sales:order-change:create',
    ])
    expect(createOnly.wrapper.find('[data-test="create-sales-order-change"]').exists()).toBe(true)
    expect(createOnly.wrapper.find('[data-test="edit-sales-order-change-920"]').exists()).toBe(false)

    const updateOnly = await mountDetail(actionableOrder, [
      'sales:order:view',
      'sales:order-change:view',
      'sales:order-change:update',
    ])
    expect(updateOnly.wrapper.find('[data-test="create-sales-order-change"]').exists()).toBe(false)
    expect(updateOnly.wrapper.find('[data-test="edit-sales-order-change-920"]').exists()).toBe(true)
  })

  it('订单详情提供交付计划拆分调整和关闭动作，按计划 allowedActions 与权限双门禁', async () => {
    const actionableOrder = {
      ...partialOrder,
      allowedActions: [...(partialOrder.allowedActions ?? []), 'UPDATE_DELIVERY_PLAN'],
    } as SalesOrderDetailRecord
    const { wrapper } = await mountDetail(actionableOrder)

    await wrapper.find('[data-test="adjust-sales-delivery-plans"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="sales-delivery-plan-date-501"]').setValue('2026-08-02')
    await wrapper.find('input[name="sales-delivery-plan-quantity-501"]').setValue('6.000000')
    await wrapper.find('[data-test="confirm-sales-delivery-plan-adjust"]').trigger('click')
    await flushPromises()

    expect(salesFulfillmentApiMock.deliveryPlans.replaceForOrder).toHaveBeenCalledWith(99, {
      version: 4,
      reason: '调整交付计划',
      idempotencyKey: 'sales-order-key',
      lines: [{ orderLineId: 501, planDate: '2026-08-02', quantity: '6.000000' }],
    })

    await wrapper.find('[data-test="close-sales-delivery-plan-910"]').trigger('click')
    await flushPromises()
    await wrapper.find('textarea[name="sales-delivery-plan-close-reason"]').setValue('客户调整')
    await wrapper.find('[data-test="confirm-sales-delivery-plan-close"]').trigger('click')
    await flushPromises()

    expect(salesFulfillmentApiMock.deliveryPlans.close).toHaveBeenCalledWith(99, 910, {
      version: 2,
      reason: '客户调整',
      idempotencyKey: 'sales-order-key',
    })

    const readonly = await mountDetail(partialOrder, ['sales:order:view', 'sales:delivery-plan:view'])
    expect(readonly.wrapper.find('[data-test="adjust-sales-delivery-plans"]').exists()).toBe(false)
    expect(readonly.wrapper.find('[data-test="close-sales-delivery-plan-910"]').exists()).toBe(false)
  })

  it('历史订单交付计划为空时不伪造计划，但在订单允许时提供初始化入口并刷新', async () => {
    const actionableOrder = {
      ...partialOrder,
      allowedActions: [...(partialOrder.allowedActions ?? []), 'UPDATE_DELIVERY_PLAN'],
    } as SalesOrderDetailRecord
    salesFulfillmentApiMock.deliveryPlans.listByOrder
      .mockResolvedValueOnce({ items: [], total: 0, page: 1, pageSize: 20 })
      .mockResolvedValueOnce({ items: deliveryPlans, total: 1, page: 1, pageSize: 20 })
    const { wrapper } = await mountDetail(actionableOrder)

    expect(wrapper.text()).toContain('暂无交付计划')
    expect(wrapper.find('[data-test="adjust-sales-delivery-plans"]').text()).toContain('初始化/拆分交付计划')

    await wrapper.find('[data-test="adjust-sales-delivery-plans"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="sales-delivery-plan-date-501"]').setValue('2026-08-02')
    await wrapper.find('input[name="sales-delivery-plan-quantity-501"]').setValue('6.000000')
    await wrapper.find('[data-test="confirm-sales-delivery-plan-adjust"]').trigger('click')
    await flushPromises()

    expect(salesFulfillmentApiMock.deliveryPlans.replaceForOrder).toHaveBeenCalledWith(99, {
      version: 4,
      reason: '调整交付计划',
      idempotencyKey: 'sales-order-key',
      lines: [{ orderLineId: 501, planDate: '2026-08-02', quantity: '6.000000' }],
    })
    expect(salesFulfillmentApiMock.deliveryPlans.listByOrder).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('SDP-001')
  })

  it('状态操作失败时显示错误并保留详情', async () => {
    salesApiMock.orders.confirm.mockRejectedValueOnce(new Error('客户已停用，不能确认销售订单'))
    const { wrapper } = await mountDetail(draftOrder)

    await wrapper.find('[data-test="confirm-sales-order-detail"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('客户已停用，不能确认销售订单')
    expect(wrapper.text()).toContain('SO-20260704-001')
  })

  it('详情加载失败时显示错误状态', async () => {
    salesApiMock.orders.get.mockRejectedValueOnce(new Error('销售订单不存在'))
    const { wrapper } = await mountDetail(draftOrder)

    expect(wrapper.text()).toContain('销售订单不存在')
    expect(wrapper.text()).toContain('销售订单详情加载失败')
  })
})
