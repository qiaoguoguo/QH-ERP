import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type {
  SalesCreditProfileRecord,
  SalesDeliveryPlanRecord,
  SalesEffectiveDemandRecord,
} from '../../shared/api/salesFulfillmentApi'
import { expectNoBareIdFilters, expectStandardListPage } from '../../test/pageGovernanceAssertions'
import { useAuthStore } from '../../stores/authStore'
import SalesCreditProfileListView from './credit/SalesCreditProfileListView.vue'
import SalesDeliveryPlanListView from './delivery/SalesDeliveryPlanListView.vue'
import EffectiveSalesDemandListView from './effective-demand/EffectiveSalesDemandListView.vue'
import effectiveSalesDemandSource from './effective-demand/EffectiveSalesDemandListView.vue?raw'

const salesFulfillmentApiMock = vi.hoisted(() => ({
  deliveryPlans: {
    list: vi.fn(),
  },
  creditProfiles: {
    list: vi.fn(),
    get: vi.fn(),
    upsert: vi.fn(),
  },
  effectiveDemands: {
    list: vi.fn(),
  },
}))

const masterDataApiMock = vi.hoisted(() => ({
  customers: { list: vi.fn() },
  materials: { list: vi.fn() },
}))

const salesApiMock = vi.hoisted(() => ({
  orders: { list: vi.fn() },
}))

const salesProjectApiMock = vi.hoisted(() => ({
  projects: { list: vi.fn() },
  listOrderLinkCandidates: vi.fn(),
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
    createSalesDeliveryPlans: vi.fn(),
    createSalesEffectiveDemands: vi.fn(),
  },
}))

vi.mock('../../shared/api/salesFulfillmentApi', () => ({
  salesFulfillmentApi: salesFulfillmentApiMock,
}))

vi.mock('../../shared/api/masterDataApi', () => ({
  masterDataApi: masterDataApiMock,
}))

vi.mock('../../shared/api/salesApi', () => ({
  salesApi: salesApiMock,
}))

vi.mock('../../shared/api/salesProjectApi', () => ({
  salesProjectApi: salesProjectApiMock,
}))

vi.mock('../../shared/api/documentPlatformApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/documentPlatformApi')>()),
  documentPlatformApi: documentPlatformApiMock,
  createIdempotencyKey: () => 'sales-fulfillment-key',
}))

const plan = {
  id: 501,
  planNo: 'SDP-001',
  orderId: 88,
  orderNo: 'SO-001',
  orderLineId: 881,
  lineNo: 1,
  customerId: 8,
  customerName: '华东客户',
  projectId: 20,
  projectNo: 'PRJ-025',
  projectName: '华东产线',
  contractId: 55,
  contractNo: 'SC-001',
  materialId: 31,
  materialCode: 'FG-001',
  materialName: '控制柜',
  unitName: '台',
  planDate: undefined,
  plannedDate: '2026-08-01',
  plannedQuantity: '10.000000',
  shippedQuantity: '4.000000',
  remainingQuantity: '6.000000',
  status: 'PARTIALLY_SHIPPED',
  closeReason: null,
  allowedActions: ['CREATE_SHIPMENT'],
  actionDisabledReason: null,
  legacyDeliveryPlanCompatible: false,
  version: 2,
} as unknown as SalesDeliveryPlanRecord

const credit: SalesCreditProfileRecord = {
  customerId: 8,
  customerCode: 'CUS-008',
  customerName: '华东客户',
  creditLimit: '100000.000000',
  frozen: false,
  blockOverdue: true,
  reviewDate: '2026-07-15',
  remark: '年度额度',
  exposure: {
    orderCommitmentAmount: '30000.000000',
    unsettledShipmentAmount: '10000.000000',
    receivableOutstandingAmount: '20000.000000',
    usedCredit: '60000.000000',
    availableCredit: '40000.000000',
    overdueRisk: true,
  },
  creditRestricted: false,
  allowedActions: ['UPDATE'],
  actionDisabledReason: null,
  version: 7,
}

const restrictedCredit: SalesCreditProfileRecord = {
  ...credit,
  customerId: 9,
  customerName: '受限客户',
  creditLimit: null,
  exposure: {
    orderCommitmentAmount: null,
    unsettledShipmentAmount: null,
    receivableOutstandingAmount: null,
    usedCredit: null,
    availableCredit: null,
    overdueRisk: null,
  },
  creditRestricted: true,
}

const demand: SalesEffectiveDemandRecord = {
  id: 701,
  sourceType: 'SALES_ORDER_DELIVERY_PLAN',
  sourceId: 501,
  sourceNo: 'SO-001/SDP-001',
  sourceVersion: 2,
  orderId: 88,
  orderNo: 'SO-001',
  orderLineId: 881,
  deliveryPlanId: 501,
  projectId: 20,
  projectNo: 'PRJ-025',
  projectName: '华东产线',
  customerId: 8,
  customerName: '华东客户',
  contractId: 55,
  contractNo: 'SC-001',
  quoteId: 9,
  quoteNo: 'SQ-001',
  materialId: 31,
  materialCode: 'FG-001',
  materialName: '控制柜',
  unitName: '台',
  orderQuantity: '10.000000',
  plannedQuantity: '10.000000',
  shippedQuantity: '4.000000',
  returnedQuantity: '1.000000',
  netQuantity: '3.000000',
  openQuantity: '6.000000',
  expectedDate: '2026-08-01',
  status: 'OPEN',
  countedAsEffectiveDemand: true,
  excludedReasonCode: null,
  updatedAt: '2026-07-16T10:00:00+08:00',
}

const customer = { id: 8, code: 'CUS-008', name: '华东客户', status: 'ENABLED' }
const material = {
  id: 31,
  code: 'FG-001',
  name: '控制柜',
  materialType: 'FINISHED_GOOD',
  sourceType: 'SELF_MADE',
  trackingMethod: 'NONE',
  trackingMethodName: '不追踪',
  categoryId: 1,
  unitId: 2,
  unitName: '台',
  status: 'ENABLED',
}
const project = {
  id: 20,
  projectNo: 'PRJ-025',
  name: '华东产线',
  customerId: 8,
  customerCode: 'CUS-008',
  customerName: '华东客户',
  ownerUserId: 1,
  ownerUsername: 'seller',
  ownerDisplayName: '销售员',
  status: 'ACTIVE',
  targetRevenue: '0.000000',
  targetCost: '0.000000',
  contractSummaryRestricted: false,
  salesOrderSummaryRestricted: false,
  createdByName: '销售员',
  createdAt: '2026-07-01T09:00:00+08:00',
  updatedAt: '2026-07-15T09:00:00+08:00',
  version: 1,
}
const order = {
  id: 88,
  orderNo: 'SO-001',
  customerId: 8,
  customerCode: 'CUS-008',
  customerName: '华东客户',
  orderDate: '2026-07-16',
  status: 'CONFIRMED',
  lineCount: 1,
  totalQuantity: '10.000000',
  shippedQuantity: '4.000000',
  remainingQuantity: '6.000000',
  createdByName: '销售员',
  createdAt: '2026-07-16T09:00:00+08:00',
  updatedAt: '2026-07-16T10:00:00+08:00',
  version: 2,
}
const contractCandidate = {
  projectId: 20,
  projectNo: 'PRJ-025',
  projectName: '华东产线',
  customerId: 8,
  customerName: '华东客户',
  contractId: 55,
  contractNo: 'SC-001',
  contractName: '华东合同',
  contractType: 'MAIN',
}

function setup(permissions: string[]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'seller', displayName: '销售员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  return pinia
}

function routerFor(path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/sales/delivery-plans', name: 'sales-delivery-plans', component: SalesDeliveryPlanListView },
      { path: '/sales/credit-profiles', name: 'sales-credit-profiles', component: SalesCreditProfileListView },
      { path: '/sales/effective-demands', name: 'sales-effective-demands', component: EffectiveSalesDemandListView },
      { path: '/sales/orders/:id', name: 'sales-order-detail', component: { render: () => null } },
    ],
  })
  return router.push(path).then(() => router.isReady()).then(() => router)
}

describe('025 销售履约页面', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    salesFulfillmentApiMock.deliveryPlans.list.mockResolvedValue({ items: [plan], total: 1, page: 1, pageSize: 10 })
    salesFulfillmentApiMock.creditProfiles.list.mockResolvedValue({ items: [credit, restrictedCredit], total: 2, page: 1, pageSize: 10 })
    salesFulfillmentApiMock.creditProfiles.get.mockResolvedValue(credit)
    salesFulfillmentApiMock.creditProfiles.upsert.mockResolvedValue({ ...credit, version: 8 })
    salesFulfillmentApiMock.effectiveDemands.list.mockResolvedValue({ items: [demand], total: 1, page: 1, pageSize: 10 })
    masterDataApiMock.customers.list.mockResolvedValue({ items: [customer], total: 1, page: 1, pageSize: 50, totalPages: 1 })
    masterDataApiMock.materials.list.mockResolvedValue({ items: [material], total: 1, page: 1, pageSize: 50, totalPages: 1 })
    salesApiMock.orders.list.mockResolvedValue({ items: [order], total: 1, page: 1, pageSize: 50, totalPages: 1 })
    salesProjectApiMock.projects.list.mockResolvedValue({ items: [project], total: 1, page: 1, pageSize: 50, totalPages: 1 })
    salesProjectApiMock.listOrderLinkCandidates.mockResolvedValue({ items: [contractCandidate], total: 1, page: 1, pageSize: 50, totalPages: 1 })
    documentPlatformApiMock.exports.createSalesDeliveryPlans.mockResolvedValue({
      id: 801,
      taskNo: 'TASK-DELIVERY-EXPORT',
      taskType: 'SALES_DELIVERY_PLAN_EXPORT',
      direction: 'EXPORT',
      stage: 'EXPORT',
      status: 'QUEUED',
      availableActions: ['CANCEL'],
      version: 1,
    })
    documentPlatformApiMock.exports.createSalesEffectiveDemands.mockResolvedValue({
      id: 802,
      taskNo: 'TASK-DEMAND-EXPORT',
      taskType: 'SALES_EFFECTIVE_DEMAND_EXPORT',
      direction: 'EXPORT',
      stage: 'EXPORT',
      status: 'QUEUED',
      availableActions: ['CANCEL'],
      version: 1,
    })
  })

  it('全局交付计划只读页展示计划/已发/剩余、项目合同和当前筛选导出', async () => {
    const pinia = setup(['sales:delivery-plan:view', 'platform:document-task:create', 'sales:document:export'])
    const router = await routerFor('/sales/delivery-plans')
    const wrapper = mount(SalesDeliveryPlanListView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expectStandardListPage(wrapper)
    expectNoBareIdFilters(wrapper, ['项目 ID', '客户 ID', '合同 ID', '订单 ID', '物料 ID'])
    expect(salesFulfillmentApiMock.deliveryPlans.list).toHaveBeenCalledWith(expect.objectContaining({ countedOnly: true }))
    expect(wrapper.text()).toContain('PRJ-025 华东产线')
    expect(wrapper.text()).toContain('SC-001')
    expect(wrapper.text()).toContain('FG-001 控制柜')
    expect(wrapper.text()).toContain('预计日期：2026-08-01')
    expect(wrapper.text()).toContain('计划/已发/剩余：10/4/6')
    expect(wrapper.text()).toContain('部分出库')

    await wrapper.find('[data-test="export-sales-delivery-plans"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.exports.createSalesDeliveryPlans).toHaveBeenCalledWith(expect.objectContaining({
      countedOnly: true,
      idempotencyKey: 'sales-fulfillment-key',
    }))
    expect(wrapper.text()).toContain('TASK-DELIVERY-EXPORT')
  })

  it('信用档案页展示三段信用占用并对无权限字段显示受限，不用 0 伪装', async () => {
    const pinia = setup(['sales:credit:view', 'sales:credit:manage'])
    const router = await routerFor('/sales/credit-profiles')
    const wrapper = mount(SalesCreditProfileListView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expectStandardListPage(wrapper)
    expectNoBareIdFilters(wrapper, ['客户 ID'])
    expect(wrapper.text()).toContain('订单承诺 30000')
    expect(wrapper.text()).toContain('待建应收出库 10000')
    expect(wrapper.text()).toContain('基础应收未收 20000')
    expect(wrapper.text()).toContain('信用信息受限')
    expect(wrapper.text()).not.toContain('受限客户 0')

    await wrapper.find('[data-test="credit-limit-8"]').setValue('120000.000000')
    await wrapper.find('[data-test="save-credit-profile-8"]').trigger('click')
    await flushPromises()
    expect(salesFulfillmentApiMock.creditProfiles.upsert).toHaveBeenCalledWith(8, expect.objectContaining({
      version: 7,
      creditLimit: '120000.000000',
    }))
  })

  it('信用档案查看和保存拆分权限，管理员可达但只有 manage 能保存', async () => {
    const viewer = setup(['sales:credit:view'])
    const viewerRouter = await routerFor('/sales/credit-profiles')
    const viewerWrapper = mount(SalesCreditProfileListView, { global: { plugins: [viewer, viewerRouter, ElementPlus] } })
    await flushPromises()

    expect(viewerWrapper.text()).toContain('华东客户')
    expect(viewerWrapper.find('[data-test="save-credit-profile-8"]').exists()).toBe(false)
    expect(salesFulfillmentApiMock.creditProfiles.list).toHaveBeenCalled()

    const admin = setup([])
    useAuthStore().roles = [{ id: 1, code: 'SYSTEM_ADMIN', name: '系统管理员', status: 'ENABLED' }]
    const adminRouter = await routerFor('/sales/credit-profiles')
    const adminWrapper = mount(SalesCreditProfileListView, { global: { plugins: [admin, adminRouter, ElementPlus] } })
    await flushPromises()

    expect(adminWrapper.text()).toContain('华东客户')
    expect(adminWrapper.find('[data-test="save-credit-profile-8"]').exists()).toBe(false)
  })

  it('有效需求默认 countedOnly=true，只读展示来源链和诊断排除开关，不出现缺料建议', async () => {
    const pinia = setup(['sales:effective-demand:view', 'platform:document-task:create', 'sales:document:export'])
    const router = await routerFor('/sales/effective-demands')
    const wrapper = mount(EffectiveSalesDemandListView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expectStandardListPage(wrapper)
    expectNoBareIdFilters(wrapper, ['项目 ID', '客户 ID', '合同 ID', '物料 ID'])
    expect(salesFulfillmentApiMock.effectiveDemands.list).toHaveBeenCalledWith(expect.objectContaining({ countedOnly: true }))
    expect(wrapper.text()).toContain('报价 -> 合同/订单 -> 交付计划 -> 出库 -> 退货/关闭')
    expect(wrapper.text()).toContain('开放需求 6')
    expect(wrapper.text()).toContain('FG-001 控制柜')
    expect(wrapper.text()).not.toContain('缺料')
    expect(wrapper.text()).not.toContain('自动建议')

    await wrapper.find('[data-test="diagnose-effective-demands"]').trigger('click')
    await flushPromises()
    expect(salesFulfillmentApiMock.effectiveDemands.list).toHaveBeenLastCalledWith(expect.objectContaining({ countedOnly: false }))
    expect(wrapper.text()).toContain('诊断模式')
    expect(wrapper.text()).toContain('含未计入候选')

    await wrapper.find('[data-test="restore-counted-effective-demands"]').trigger('click')
    await flushPromises()
    expect(salesFulfillmentApiMock.effectiveDemands.list).toHaveBeenLastCalledWith(expect.objectContaining({ countedOnly: true }))

    await wrapper.find('[data-test="export-sales-effective-demands"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.exports.createSalesEffectiveDemands).toHaveBeenCalledWith(expect.objectContaining({
      countedOnly: true,
      idempotencyKey: 'sales-fulfillment-key',
    }))
    expect(wrapper.text()).toContain('TASK-DEMAND-EXPORT')
  })

  it('有效销售需求状态筛选使用稳定枚举且不保留 never 兜底', async () => {
    expect(effectiveSalesDemandSource).not.toContain('as never')
    const pinia = setup(['sales:effective-demand:view'])
    const router = await routerFor('/sales/effective-demands')
    const wrapper = mount(EffectiveSalesDemandListView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    const optionValues = wrapper.findAllComponents({ name: 'ElOption' }).map((option) => option.props('value'))
    expect(optionValues).toEqual(expect.arrayContaining(['OPEN', 'PARTIALLY_SHIPPED', 'OVERDUE', 'EXCLUDED']))
    expect(optionValues).not.toContain('PARTIAL')
    expect(optionValues).not.toContain('FULFILLED')
    expect(optionValues).not.toContain('CLOSED')

    const statusSelect = wrapper
      .findAllComponents({ name: 'ElSelect' })
      .find((select) => select.props('placeholder') === '需求状态')
    expect(statusSelect?.exists()).toBe(true)
    statusSelect?.vm.$emit('update:modelValue', 'PARTIALLY_SHIPPED')
    await wrapper.find('[data-test="search-sales-effective-demands"]').trigger('click')
    await flushPromises()

    expect(salesFulfillmentApiMock.effectiveDemands.list).toHaveBeenLastCalledWith(expect.objectContaining({
      status: 'PARTIALLY_SHIPPED',
    }))
  })
})
