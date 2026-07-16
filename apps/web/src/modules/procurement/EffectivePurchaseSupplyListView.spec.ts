import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PageResult } from '../../shared/api/accountPermissionApi'
import type { EffectivePurchaseSupplyRecord } from '../../shared/api/procurementApi'
import { expectNoBareIdFilters, expectStandardListPage } from '../../test/pageGovernanceAssertions'
import { useAuthStore } from '../../stores/authStore'
import EffectivePurchaseSupplyListView from './EffectivePurchaseSupplyListView.vue'

const procurementApiMock = vi.hoisted(() => ({
  orders: {
    list: vi.fn(),
  },
  effectiveSupplies: {
    list: vi.fn(),
  },
}))

const masterDataApiMock = vi.hoisted(() => ({
  suppliers: { list: vi.fn() },
  materials: { list: vi.fn() },
}))

const salesProjectApiMock = vi.hoisted(() => ({
  projects: { list: vi.fn() },
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
    createProcurementEffectiveSupplies: vi.fn(),
  },
}))

vi.mock('../../shared/api/procurementApi', () => ({
  procurementApi: procurementApiMock,
}))

vi.mock('../../shared/api/masterDataApi', () => ({
  masterDataApi: masterDataApiMock,
}))

vi.mock('../../shared/api/salesProjectApi', () => ({
  salesProjectApi: salesProjectApiMock,
}))

vi.mock('../../shared/api/documentPlatformApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/documentPlatformApi')>()),
  documentPlatformApi: documentPlatformApiMock,
  createIdempotencyKey: () => 'supply-export-key',
}))

const supplies: EffectivePurchaseSupplyRecord[] = [
  {
    id: 'S-1',
    sourceType: 'SCHEDULE',
    sourceId: 601,
    orderId: 501,
    orderNo: 'PO-PRJ-001',
    scheduleId: 601,
    scheduleSeq: 10,
    procurementMode: 'PROJECT',
    purchaseMode: 'PROJECT',
    projectId: 30,
    projectCode: 'PRJ-024',
    projectName: '华东产线改造',
    supplierId: 100,
    supplierName: '华东五金',
    materialId: 5,
    materialCode: 'M-100',
    materialName: '伺服电机',
    expectedArrivalDate: '2026-07-25',
    remainingQuantity: '60.000000',
    countedAsEffectiveSupply: true,
    status: 'PLANNED',
    statusName: '计划到货',
    priceSourceTypeName: '最低有效报价',
    priceSourceType: 'QUOTE',
    sourceNo: 'PQT-024-001',
    priceSourceNo: null,
    costVisible: false,
    taxExcludedAmount: null,
    allowedActions: [],
  } as EffectivePurchaseSupplyRecord & { sourceNo: string },
  {
    id: 'S-2',
    sourceType: 'ORDER_LINE',
    sourceId: 502,
    orderId: 502,
    orderNo: 'PO-CLOSED-001',
    procurementMode: 'PUBLIC',
    projectId: null,
    projectCode: null,
    projectName: null,
    supplierId: 101,
    supplierName: '华北机电',
    materialId: 6,
    materialCode: 'M-200',
    materialName: '铜排',
    expectedArrivalDate: '2026-07-28',
    remainingQuantity: '20.000000',
    countedAsEffectiveSupply: false,
    notCountedReason: '订单已结案',
    status: 'CLOSED',
    statusName: '已结案',
    priceSourceTypeName: '公共直采例外',
    costVisible: true,
    taxExcludedAmount: '2000.000000',
    allowedActions: [],
  },
]

describe('有效采购供给页面', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    procurementApiMock.effectiveSupplies.list.mockResolvedValue({
      items: supplies,
      page: 1,
      pageSize: 10,
      total: 2,
      totalPages: 1,
    } satisfies PageResult<EffectivePurchaseSupplyRecord>)
    procurementApiMock.orders.list.mockResolvedValue({ items: [], page: 1, pageSize: 50, total: 0, totalPages: 0 })
    masterDataApiMock.suppliers.list.mockResolvedValue({ items: [{ id: 100, code: 'SUP-100', name: '华东五金', status: 'ENABLED' }], page: 1, pageSize: 50, total: 1, totalPages: 1 })
    masterDataApiMock.materials.list.mockResolvedValue({ items: [{ id: 5, code: 'M-100', name: '伺服电机', materialType: 'RAW_MATERIAL', sourceType: 'PURCHASED', trackingMethod: 'NONE', trackingMethodName: '不追踪', categoryId: 1, unitId: 1, unitName: '台', status: 'ENABLED' }], page: 1, pageSize: 50, total: 1, totalPages: 1 })
    salesProjectApiMock.projects.list.mockResolvedValue({ items: [{ id: 30, projectNo: 'PRJ-024', name: '华东产线改造', customerId: 1000, customerCode: 'CUS-A', customerName: '华东客户', ownerUserId: 1, ownerUsername: 'planner', ownerDisplayName: '计划员', status: 'ACTIVE', targetRevenue: '0.000000', targetCost: '0.000000', contractSummaryRestricted: false, salesOrderSummaryRestricted: false, createdByName: '计划员', createdAt: '2026-07-01T09:00:00+08:00', updatedAt: '2026-07-15T09:00:00+08:00', version: 2 }], page: 1, pageSize: 50, total: 1, totalPages: 1 })
    documentPlatformApiMock.exports.createProcurementEffectiveSupplies.mockResolvedValue({
      id: 907,
      taskNo: 'TASK-SUPPLY-EXPORT',
      taskType: 'PROCUREMENT_SUPPLY_EXPORT',
      direction: 'EXPORT',
      stage: 'EXPORT',
      status: 'QUEUED',
      availableActions: ['CANCEL'],
      version: 1,
    })
  })

  it('只读展示 026 可消费供给字段，不出现缺料净算或自动建议', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'planner', displayName: '计划员', status: 'ENABLED' },
      menus: [],
      permissions: ['procurement:supply:view', 'platform:document-task:create', 'procurement:document:export'],
    })

    const wrapper = mount(EffectivePurchaseSupplyListView, { global: { plugins: [pinia, ElementPlus] } })
    await flushPromises()

    expectStandardListPage(wrapper)
    expectNoBareIdFilters(wrapper, ['项目 ID', '物料 ID', '供应商 ID'])
    expect(procurementApiMock.effectiveSupplies.list).toHaveBeenCalledWith({
      projectId: undefined,
      materialId: undefined,
      supplierId: undefined,
      procurementMode: undefined,
      status: undefined,
      expectedDateFrom: '',
      expectedDateTo: '',
      countedOnly: true,
      page: 1,
      pageSize: 10,
    })
    expect(wrapper.text()).toContain('项目专采 · PRJ-024/华东产线改造')
    expect(wrapper.text()).toContain('最低有效报价 PQT-024-001')
    expect(wrapper.text()).toContain('公共采购')
    expect(wrapper.text()).toContain('PO-PRJ-001')
    expect(wrapper.text()).toContain('华东五金')
    expect(wrapper.text()).toContain('M-100 伺服电机')
    expect(wrapper.text()).toContain('预计到货 2026-07-25')
    expect(wrapper.text()).toContain('剩余 60')
    expect(wrapper.text()).toContain('计入有效供给')
    expect(wrapper.text()).toContain('订单已结案')
    expect(wrapper.text()).toContain('成本无权限')
    expect(wrapper.text()).not.toContain('缺料')
    expect(wrapper.text()).not.toContain('自动建议')

    await wrapper.find('[data-test="export-effective-supplies"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.exports.createProcurementEffectiveSupplies).toHaveBeenCalledWith({
      projectId: undefined,
      materialId: undefined,
      supplierId: undefined,
      procurementMode: undefined,
      status: undefined,
      expectedDateFrom: '',
      expectedDateTo: '',
      countedOnly: true,
      idempotencyKey: 'supply-export-key',
    })
    expect(wrapper.text()).toContain('TASK-SUPPLY-EXPORT')
  })
})
