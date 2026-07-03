import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { WorkOrderCostSummaryRecord } from '../../shared/api/costCollectionApi'
import type { ProductionWorkOrderDetailRecord } from '../../shared/api/productionApi'
import { useAuthStore } from '../../stores/authStore'
import ProductionWorkOrderDetailView from './ProductionWorkOrderDetailView.vue'

const productionApiMock = vi.hoisted(() => ({
  workOrders: {
    get: vi.fn(),
    release: vi.fn(),
    complete: vi.fn(),
    cancel: vi.fn(),
  },
  materialIssues: {
    post: vi.fn(),
  },
  reports: {
    post: vi.fn(),
  },
  completionReceipts: {
    post: vi.fn(),
  },
}))

vi.mock('../../shared/api/productionApi', () => ({
  productionApi: productionApiMock,
}))

const costCollectionApiMock = vi.hoisted(() => ({
  workOrders: {
    summary: vi.fn(),
  },
}))

vi.mock('../../shared/api/costCollectionApi', () => ({
  costCollectionApi: costCollectionApiMock,
}))

const detailRecord: ProductionWorkOrderDetailRecord = {
  id: 9,
  workOrderNo: 'WO-20260703-001',
  productMaterialId: 10,
  productMaterialCode: 'FG-001',
  productMaterialName: '成品 A',
  bomId: 20,
  bomCode: 'BOM-FG-001',
  bomVersionCode: 'V1',
  plannedQuantity: 100,
  reportedQuantity: 60,
  qualifiedQuantity: 55,
  defectiveQuantity: 5,
  receivedQuantity: 40,
  issueWarehouseId: 30,
  issueWarehouseName: '原料仓',
  receiptWarehouseId: 31,
  receiptWarehouseName: '成品仓',
  plannedStartDate: '2026-07-03',
  plannedFinishDate: '2026-07-10',
  status: 'IN_PROGRESS',
  remark: '首批试产',
  createdByName: '管理员',
  createdAt: '2026-07-03T08:00:00+08:00',
  updatedAt: '2026-07-03T09:00:00+08:00',
  materials: [
    {
      id: 100,
      lineNo: 10,
      bomItemId: 200,
      materialId: 11,
      materialCode: 'RM-001',
      materialName: '原材料 A',
      materialType: 'RAW_MATERIAL',
      unitId: 3,
      unitName: '千克',
      requiredQuantity: 120,
      issuedQuantity: 80,
      remainingQuantity: 40,
      lossRate: 0.05,
      remark: '标准用料',
    },
  ],
  materialIssues: [
    {
      id: 300,
      issueNo: 'MI-001',
      workOrderId: 9,
      status: 'POSTED',
      businessDate: '2026-07-04',
      reason: '生产领料',
      remark: null,
      lineCount: 1,
      createdByName: '管理员',
      createdAt: '2026-07-04T08:00:00+08:00',
      updatedAt: '2026-07-04T08:30:00+08:00',
      postedByName: '仓管员',
      postedAt: '2026-07-04T09:00:00+08:00',
    },
  ],
  reports: [
    {
      id: 400,
      reportNo: 'WR-001',
      workOrderId: 9,
      status: 'DRAFT',
      businessDate: '2026-07-04',
      qualifiedQuantity: 55,
      defectiveQuantity: 5,
      totalQuantity: 60,
      reporterName: '张三',
      remark: '首批报工',
      createdByName: '管理员',
      createdAt: '2026-07-04T10:00:00+08:00',
      updatedAt: '2026-07-04T10:30:00+08:00',
      postedByName: null,
      postedAt: null,
    },
  ],
  completionReceipts: [
    {
      id: 500,
      receiptNo: 'CR-001',
      workOrderId: 9,
      status: 'POSTED',
      businessDate: '2026-07-05',
      receiptWarehouseId: 31,
      receiptWarehouseName: '成品仓',
      quantity: 40,
      beforeQuantity: 10,
      afterQuantity: 50,
      remark: '首批入库',
      createdByName: '管理员',
      createdAt: '2026-07-05T08:00:00+08:00',
      updatedAt: '2026-07-05T08:30:00+08:00',
      postedByName: '仓管员',
      postedAt: '2026-07-05T09:00:00+08:00',
    },
  ],
  movements: [
    {
      id: 600,
      movementNo: 'MOV-001',
      movementType: 'PRODUCTION_ISSUE',
      direction: 'OUT',
      warehouseId: 30,
      warehouseName: '原料仓',
      materialId: 11,
      materialCode: 'RM-001',
      materialName: '原材料 A',
      unitId: 3,
      unitName: '千克',
      quantity: 80,
      beforeQuantity: 100,
      afterQuantity: 20,
      sourceType: 'PRODUCTION_MATERIAL_ISSUE',
      sourceId: 300,
      sourceLineId: 301,
      businessDate: '2026-07-04',
      reason: '生产领料',
      remark: null,
      operatorName: '仓管员',
      occurredAt: '2026-07-04T09:00:00+08:00',
    },
  ],
}

const draftExecutionRecord: ProductionWorkOrderDetailRecord = {
  ...detailRecord,
  materialIssues: [
    {
      id: 301,
      issueNo: 'MI-DRAFT-001',
      workOrderId: 9,
      status: 'DRAFT',
      businessDate: '2026-07-04',
      reason: '生产领料',
      remark: null,
      lineCount: 1,
      createdByName: '管理员',
      createdAt: '2026-07-04T08:00:00+08:00',
      updatedAt: '2026-07-04T08:30:00+08:00',
      postedByName: null,
      postedAt: null,
    },
  ],
  reports: [
    {
      id: 401,
      reportNo: 'WR-DRAFT-001',
      workOrderId: 9,
      status: 'DRAFT',
      businessDate: '2026-07-04',
      qualifiedQuantity: 55,
      defectiveQuantity: 5,
      totalQuantity: 60,
      reporterName: '张三',
      remark: '首批报工',
      createdByName: '管理员',
      createdAt: '2026-07-04T10:00:00+08:00',
      updatedAt: '2026-07-04T10:30:00+08:00',
      postedByName: null,
      postedAt: null,
    },
  ],
  completionReceipts: [
    {
      id: 501,
      receiptNo: 'CR-DRAFT-001',
      workOrderId: 9,
      status: 'DRAFT',
      businessDate: '2026-07-05',
      receiptWarehouseId: 31,
      receiptWarehouseName: '成品仓',
      quantity: 40,
      beforeQuantity: null,
      afterQuantity: null,
      remark: '首批入库',
      createdByName: '管理员',
      createdAt: '2026-07-05T08:00:00+08:00',
      updatedAt: '2026-07-05T08:30:00+08:00',
      postedByName: null,
      postedAt: null,
    },
  ],
}

const postedExecutionRecord: ProductionWorkOrderDetailRecord = {
  ...draftExecutionRecord,
  materialIssues: draftExecutionRecord.materialIssues.map((issue) => ({
    ...issue,
    status: 'POSTED',
    postedByName: '仓管员',
    postedAt: '2026-07-04T09:00:00+08:00',
  })),
  reports: draftExecutionRecord.reports.map((report) => ({
    ...report,
    status: 'POSTED',
    postedByName: '生产主管',
    postedAt: '2026-07-04T11:00:00+08:00',
  })),
  completionReceipts: draftExecutionRecord.completionReceipts.map((receipt) => ({
    ...receipt,
    status: 'POSTED',
    postedByName: '仓管员',
    postedAt: '2026-07-05T09:00:00+08:00',
  })),
}

const costSummaryRecord: WorkOrderCostSummaryRecord = {
  workOrderId: 9,
  workOrderNo: 'WO-20260703-001',
  productMaterialId: 10,
  productMaterialCode: 'FG-001',
  productMaterialName: '成品 A',
  formalAccounting: false,
  records: [
    {
      id: 701,
      recordNo: 'COST-001',
      workOrderId: 9,
      workOrderNo: 'WO-20260703-001',
      productMaterialId: 10,
      productMaterialCode: 'FG-001',
      productMaterialName: '成品 A',
      costType: 'MATERIAL',
      sourceType: 'AUTO_PRODUCTION',
      sourceDocumentType: 'PRODUCTION_MATERIAL_ISSUE',
      sourceDocumentNo: 'MI-001',
      sourceDocumentId: 300,
      sourceLineId: 301,
      basisType: 'SOURCE_QUANTITY_ONLY',
      materialId: 11,
      materialCode: 'RM-001',
      materialName: '原材料 A',
      unitId: 3,
      unitName: '千克',
      quantity: 80,
      unitPrice: null,
      amount: null,
      businessDate: '2026-07-04',
      status: 'ACTIVE',
      remark: null,
      recordedByName: '仓管员',
      recordedAt: '2026-07-04T09:00:00+08:00',
      createdByName: '仓管员',
      createdAt: '2026-07-04T09:00:00+08:00',
      updatedAt: '2026-07-04T09:00:00+08:00',
    },
    {
      id: 702,
      recordNo: 'COST-002',
      workOrderId: 9,
      workOrderNo: 'WO-20260703-001',
      productMaterialId: 10,
      productMaterialCode: 'FG-001',
      productMaterialName: '成品 A',
      costType: 'MANUFACTURING_OVERHEAD',
      sourceType: 'MANUAL_ENTRY',
      sourceDocumentType: 'MANUAL_COST_RECORD',
      sourceDocumentNo: 'MANUAL-001',
      sourceDocumentId: null,
      sourceLineId: null,
      basisType: 'MANUAL_AMOUNT',
      materialId: null,
      materialCode: null,
      materialName: null,
      unitId: null,
      unitName: null,
      quantity: null,
      unitPrice: null,
      amount: 300,
      businessDate: '2026-07-05',
      status: 'ACTIVE',
      remark: '制造费用业务记录',
      recordedByName: '成本管理员',
      recordedAt: '2026-07-05T10:00:00+08:00',
      createdByName: '成本管理员',
      createdAt: '2026-07-05T10:00:00+08:00',
      updatedAt: '2026-07-05T10:00:00+08:00',
    },
  ],
  amountSummaries: [{ costType: 'MANUFACTURING_OVERHEAD', amount: 300 }],
  quantitySummaries: [{ costType: 'MATERIAL', quantity: 80 }],
  outputTraces: [
    {
      receiptId: 500,
      receiptNo: 'CR-001',
      workOrderId: 9,
      businessDate: '2026-07-05',
      receiptWarehouseId: 31,
      receiptWarehouseName: '成品仓',
      quantity: 40,
      beforeQuantity: 10,
      afterQuantity: 50,
      postedByName: '仓管员',
      postedAt: '2026-07-05T09:00:00+08:00',
    },
  ],
}

function buttonsByText(wrapper: VueWrapper, text: string): VueWrapper[] {
  return wrapper.findAllComponents({ name: 'ElButton' }).filter((button) => button.text().trim() === text)
}

async function mountDetail(
  record: ProductionWorkOrderDetailRecord,
  permissions = [
    'production:work-order:view',
    'production:work-order:update',
    'production:work-order:release',
    'production:work-order:complete',
    'production:work-order:cancel',
    'production:issue:create',
    'production:report:create',
    'production:receipt:create',
  ],
) {
  productionApiMock.workOrders.get.mockResolvedValue(record)
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
      { path: '/production/work-orders/:id', name: 'production-work-order-detail', component: ProductionWorkOrderDetailView },
      { path: '/production/work-orders/:id/edit', name: 'production-work-order-edit', component: { render: () => null } },
      { path: '/production/work-orders/:id/material-issues', name: 'production-work-order-material-issues', component: { render: () => null } },
      { path: '/production/work-orders/:id/reports', name: 'production-work-order-reports', component: { render: () => null } },
      { path: '/production/work-orders/:id/completion-receipts', name: 'production-work-order-completion-receipts', component: { render: () => null } },
      { path: '/cost/records/:id', name: 'cost-record-detail', component: { render: () => null } },
      { path: '/cost/records/:id/edit', name: 'cost-record-edit', component: { render: () => null } },
      { path: '/cost/records/create', name: 'cost-record-create', component: { render: () => null } },
    ],
  })
  await router.push('/production/work-orders/9')
  await router.isReady()
  const wrapper = mount(ProductionWorkOrderDetailView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('生产工单详情页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('confirm', vi.fn(() => true))
    productionApiMock.workOrders.release.mockResolvedValue(detailRecord)
    productionApiMock.workOrders.complete.mockResolvedValue({ ...detailRecord, status: 'COMPLETED' })
    productionApiMock.workOrders.cancel.mockResolvedValue({ ...detailRecord, status: 'CANCELLED' })
    productionApiMock.materialIssues.post.mockResolvedValue(postedExecutionRecord.materialIssues[0])
    productionApiMock.reports.post.mockResolvedValue(postedExecutionRecord.reports[0])
    productionApiMock.completionReceipts.post.mockResolvedValue(postedExecutionRecord.completionReceipts[0])
    costCollectionApiMock.workOrders.summary.mockResolvedValue(costSummaryRecord)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('展示状态标签、数量摘要和工单基础信息', async () => {
    const { wrapper } = await mountDetail(detailRecord)

    expect(wrapper.text()).toContain('生产中')
    expect(wrapper.text()).toContain('计划数量')
    expect(wrapper.text()).toContain('100')
    expect(wrapper.text()).toContain('累计报工')
    expect(wrapper.text()).toContain('60')
    expect(wrapper.text()).toContain('合格数量')
    expect(wrapper.text()).toContain('55')
    expect(wrapper.text()).toContain('累计入库')
    expect(wrapper.text()).toContain('40')
    expect(wrapper.text()).toContain('WO-20260703-001')
    expect(wrapper.text()).toContain('FG-001 成品 A')
  })

  it('展示 BOM 快照、执行记录和库存流水摘要', async () => {
    const { wrapper } = await mountDetail(detailRecord)

    expect(wrapper.text()).toContain('BOM 用料快照')
    expect(wrapper.text()).toContain('RM-001 原材料 A')
    expect(wrapper.text()).toContain('MI-001')
    expect(wrapper.text()).toContain('生产领料')
    expect(wrapper.text()).toContain('WR-001')
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).toContain('CR-001')
    expect(wrapper.text()).toContain('成品仓')
    expect(wrapper.text()).toContain('MOV-001')
    expect(wrapper.text()).toContain('PRODUCTION_ISSUE')
  })

  it('只读用户不展示写操作按钮', async () => {
    const { wrapper } = await mountDetail(detailRecord, ['production:work-order:view'])

    expect(buttonsByText(wrapper, '编辑')).toHaveLength(0)
    expect(buttonsByText(wrapper, '发布')).toHaveLength(0)
    expect(buttonsByText(wrapper, '领料')).toHaveLength(0)
    expect(buttonsByText(wrapper, '报工')).toHaveLength(0)
    expect(buttonsByText(wrapper, '完工入库')).toHaveLength(0)
    expect(buttonsByText(wrapper, '完成')).toHaveLength(0)
    expect(buttonsByText(wrapper, '取消')).toHaveLength(0)
  })

  it('已完成工单不展示写操作按钮', async () => {
    const completed = {
      ...detailRecord,
      status: 'COMPLETED' as const,
      reportedQuantity: 100,
      qualifiedQuantity: 100,
      receivedQuantity: 100,
    }
    const { wrapper } = await mountDetail(completed)

    expect(wrapper.text()).toContain('已完成')
    expect(buttonsByText(wrapper, '编辑')).toHaveLength(0)
    expect(buttonsByText(wrapper, '发布')).toHaveLength(0)
    expect(buttonsByText(wrapper, '领料')).toHaveLength(0)
    expect(buttonsByText(wrapper, '报工')).toHaveLength(0)
    expect(buttonsByText(wrapper, '完工入库')).toHaveLength(0)
    expect(buttonsByText(wrapper, '完成')).toHaveLength(0)
    expect(buttonsByText(wrapper, '取消')).toHaveLength(0)
  })

  it('草稿执行记录在具备过账权限时显示过账按钮并调用对应 API', async () => {
    const { wrapper } = await mountDetail(draftExecutionRecord, [
      'production:work-order:view',
      'production:issue:post',
      'production:report:post',
      'production:receipt:post',
    ])

    expect(wrapper.find('[data-test="post-production-material-issue"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="post-production-work-report"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="post-production-completion-receipt"]').exists()).toBe(true)

    await wrapper.find('[data-test="post-production-material-issue"]').trigger('click')
    await flushPromises()
    expect(productionApiMock.materialIssues.post).toHaveBeenCalledWith(9, 301)

    await wrapper.find('[data-test="post-production-work-report"]').trigger('click')
    await flushPromises()
    expect(productionApiMock.reports.post).toHaveBeenCalledWith(9, 401)

    await wrapper.find('[data-test="post-production-completion-receipt"]').trigger('click')
    await flushPromises()
    expect(productionApiMock.completionReceipts.post).toHaveBeenCalledWith(9, 501)
    expect(productionApiMock.workOrders.get).toHaveBeenCalledTimes(4)
  })

  it('已过账执行记录不显示过账按钮', async () => {
    const { wrapper } = await mountDetail(postedExecutionRecord, [
      'production:work-order:view',
      'production:issue:post',
      'production:report:post',
      'production:receipt:post',
    ])

    expect(wrapper.find('[data-test="post-production-material-issue"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="post-production-work-report"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="post-production-completion-receipt"]').exists()).toBe(false)
  })

  it('只读用户不显示执行单过账按钮', async () => {
    const { wrapper } = await mountDetail(draftExecutionRecord, ['production:work-order:view'])

    expect(wrapper.find('[data-test="post-production-material-issue"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="post-production-work-report"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="post-production-completion-receipt"]').exists()).toBe(false)
  })

  it('执行单过账失败时错误可见且详情数据仍保留', async () => {
    productionApiMock.reports.post.mockRejectedValueOnce(new Error('报工单已过账，不能重复过账'))
    const { wrapper } = await mountDetail(draftExecutionRecord, [
      'production:work-order:view',
      'production:report:post',
    ])

    await wrapper.find('[data-test="post-production-work-report"]').trigger('click')
    await flushPromises()

    expect(productionApiMock.reports.post).toHaveBeenCalledWith(9, 401)
    expect(wrapper.text()).toContain('报工单已过账，不能重复过账')
    expect(wrapper.text()).toContain('WO-20260703-001')
    expect(wrapper.text()).toContain('WR-DRAFT-001')
  })

  it('有成本查看权限时展示成本归集业务记录和非正式核算提示', async () => {
    const { wrapper } = await mountDetail(detailRecord, [
      'production:work-order:view',
      'cost:record:view',
      'cost:record:create',
      'cost:record:update',
    ])

    expect(costCollectionApiMock.workOrders.summary).toHaveBeenCalledWith(9)
    expect(wrapper.text()).toContain('成本归集记录')
    expect(wrapper.text()).toContain('当前为业务归集记录，不是正式财务核算结果')
    expect(wrapper.text()).toContain('COST-001')
    expect(wrapper.text()).toContain('COST-002')
    expect(wrapper.text()).toContain('制造费用')
    expect(wrapper.text()).toContain('新增手工成本')
    expect(wrapper.find('[data-test="edit-work-order-cost-record"]').exists()).toBe(true)
  })

  it('成本只读权限展示成本分区但隐藏创建和编辑入口', async () => {
    const { wrapper } = await mountDetail(detailRecord, [
      'production:work-order:view',
      'cost:record:view',
    ])

    expect(costCollectionApiMock.workOrders.summary).toHaveBeenCalledWith(9)
    expect(wrapper.text()).toContain('成本归集记录')
    expect(wrapper.text()).toContain('COST-001')
    expect(wrapper.text()).not.toContain('新增手工成本')
    expect(wrapper.find('[data-test="edit-work-order-cost-record"]').exists()).toBe(false)
  })

  it('成本分区创建和编辑入口进入对应成本页面', async () => {
    const created = await mountDetail(detailRecord, [
      'production:work-order:view',
      'cost:record:view',
      'cost:record:create',
      'cost:record:update',
    ])

    await buttonsByText(created.wrapper, '新增手工成本')[0].trigger('click')
    await flushPromises()
    expect(created.router.currentRoute.value.name).toBe('cost-record-create')
    expect(created.router.currentRoute.value.query.workOrderId).toBe('9')

    const edited = await mountDetail(detailRecord, [
      'production:work-order:view',
      'cost:record:view',
      'cost:record:update',
    ])
    await edited.wrapper.find('[data-test="edit-work-order-cost-record"]').trigger('click')
    await flushPromises()
    expect(edited.router.currentRoute.value.name).toBe('cost-record-edit')
    expect(edited.router.currentRoute.value.params.id).toBe('702')
  })

  it('成本汇总加载失败不影响工单详情展示', async () => {
    costCollectionApiMock.workOrders.summary.mockRejectedValueOnce(new Error('成本汇总加载失败'))
    const { wrapper } = await mountDetail(detailRecord, [
      'production:work-order:view',
      'cost:record:view',
    ])

    expect(wrapper.text()).toContain('WO-20260703-001')
    expect(wrapper.text()).toContain('BOM 用料快照')
    expect(wrapper.text()).toContain('成本归集记录')
    expect(wrapper.text()).toContain('成本汇总加载失败')
  })

  it('无成本查看权限时不请求也不展示成本分区', async () => {
    const { wrapper } = await mountDetail(detailRecord, ['production:work-order:view'])

    expect(costCollectionApiMock.workOrders.summary).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('成本归集记录')
    expect(wrapper.text()).not.toContain('新增手工成本')
  })
})
