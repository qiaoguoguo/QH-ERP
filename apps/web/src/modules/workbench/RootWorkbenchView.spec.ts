import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../../stores/authStore'
import RootWorkbenchView from './RootWorkbenchView.vue'

const documentPlatformApiMock = vi.hoisted(() => ({
  approvalTasks: { list: vi.fn() },
  documentTasks: { list: vi.fn() },
}))

const businessReportingApiMock = vi.hoisted(() => ({
  overview: { get: vi.fn() },
  operatingFinanceOverview: { get: vi.fn() },
  exceptions: { list: vi.fn() },
}))

vi.mock('../../shared/api/documentPlatformApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/documentPlatformApi')>()),
  documentPlatformApi: documentPlatformApiMock,
}))

vi.mock('../../shared/api/businessReportingApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/businessReportingApi')>()),
  businessReportingApi: businessReportingApiMock,
}))

const fullPermissions = [
  'platform:todo:view',
  'platform:document-task:view',
  'report:overview:view',
  'report:operating-finance:view',
  'report:exception:view',
  'sales:project:create',
  'procurement:requisition:create',
  'production:work-order:create',
  'inventory:document:create',
  'quality:inspection:view',
  'system:business-period-close:view',
  'gl:period:view',
  'financial-close:period:view',
]

function page<T>(items: T[], total = items.length) {
  return {
    items,
    page: 1,
    pageSize: 10,
    total,
  }
}

function documentTask(status: string, id: number, overrides: Record<string, unknown> = {}) {
  return {
    id,
    taskNo: `TASK-${id}`,
    taskType: 'MATERIAL_IMPORT',
    direction: 'IMPORT',
    stage: 'VALIDATE',
    status,
    progressPercent: status === 'RUNNING' ? 65 : 100,
    createdAt: `2026-07-23T0${id % 10}:00:00+08:00`,
    version: 1,
    ...overrides,
  }
}

function setDefaultResponses() {
  documentPlatformApiMock.approvalTasks.list.mockResolvedValue(page([{
    id: 3,
    taskNo: 'AT-001',
    sceneCode: 'SALES_PROJECT_CONTRACT_ACTIVATION',
    objectType: 'SALES_PROJECT_CONTRACT',
    objectId: 55,
    objectNo: 'SC-001',
    objectName: '主合同',
    status: 'PENDING',
    currentStepName: '合同激活审批',
    applicantName: '销售主管',
    assignedAt: '2026-07-23T09:12:00+08:00',
    availableActions: [],
    version: 4,
  }], 6))

  documentPlatformApiMock.documentTasks.list.mockImplementation(({ status }: { status: string }) => {
    const records: Record<string, ReturnType<typeof documentTask>[]> = {
      QUEUED: [documentTask('QUEUED', 91)],
      RUNNING: [documentTask('RUNNING', 92)],
      READY_TO_COMMIT: [],
      VALIDATION_FAILED: [documentTask('VALIDATION_FAILED', 93, { errorMessage: '物料编码重复' })],
      FAILED: [documentTask('FAILED', 94, { errorMessage: '文件解析失败' })],
    }
    return Promise.resolve(page(records[status] ?? [], status === 'RUNNING' ? 3 : (records[status]?.length ?? 0)))
  })

  businessReportingApiMock.overview.get.mockResolvedValue({
    period: { dateFrom: '2026-07-01', dateTo: '2026-07-31' },
    salesShipmentAmount: '1286400.00',
    purchaseReceiptAmount: '764300.00',
    inventoryInQuantity: '500.000',
    inventoryOutQuantity: '320.000',
    productionPlannedQuantity: '420.000',
    productionCompletedQuantity: '386.000',
    costAmount: '518600.00',
    receivableBalance: '428000.00',
    payableBalance: '300000.00',
    receivedAmount: '750000.00',
    paidAmount: '500000.00',
    exceptionCount: 12,
    formalAccounting: false,
  })

  businessReportingApiMock.operatingFinanceOverview.get.mockResolvedValue({
    periodCode: '2026-07',
    analysisMode: 'LIVE',
    businessPeriodStatus: 'OPEN',
    accountingPeriodStatus: 'OPEN',
    financialCloseStatus: 'NOT_CLOSED',
    finalityStatus: 'PREVIEW',
    freshnessStatus: 'CURRENT',
    projectProfitAmount: '200000.00',
    contractUnreceivedAmount: '128000.00',
    procurementVarianceAmount: '1000.00',
    inventoryCapitalAmount: '600000.00',
    receivablePayableBalanceAmount: '128000.00',
    accountingDifferenceAmount: '0.00',
    sourceCount: 10,
  })

  businessReportingApiMock.exceptions.list.mockResolvedValue({
    items: [{
      exceptionType: 'INVENTORY_SHORTAGE',
      severity: 'CRITICAL',
      sourceType: 'INVENTORY_BALANCE',
      sourceId: 1,
      sourceNo: 'M-100045',
      businessDate: '2026-07-23',
      objectName: '关键物料 M-100045',
      description: '当前库存低于安全库存',
      sourceCount: 1,
      canViewResource: true,
      traceKey: 'inventory-shortage:1',
    }],
    summary: {
      exceptionCount: 12,
      criticalCount: 3,
      warningCount: 9,
      countsByType: { INVENTORY_SHORTAGE: 5 },
    },
    page: 1,
    pageSize: 10,
    total: 12,
    totalPages: 2,
  })
}

async function mountWorkbench(permissions = fullPermissions) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '超级管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  const emptyComponent = { template: '<div />' }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: RootWorkbenchView },
      { path: '/sales/projects/create', component: emptyComponent },
      { path: '/procurement/requisitions/create', component: emptyComponent },
      { path: '/production/work-orders/create', component: emptyComponent },
      { path: '/inventory/documents/create', component: emptyComponent },
      { path: '/quality/inspections', component: emptyComponent },
      { path: '/platform/approvals', component: emptyComponent },
      { path: '/platform/document-tasks', component: emptyComponent },
      { path: '/reports/overview', component: emptyComponent },
      { path: '/reports/exceptions', component: emptyComponent },
      { path: '/period-close/runs', component: emptyComponent },
      { path: '/gl/accounting-periods', component: emptyComponent },
      { path: '/gl/financial-close', component: emptyComponent },
    ],
  })
  await router.push('/')
  await router.isReady()
  const wrapper = mount(RootWorkbenchView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('根工作台', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setDefaultResponses()
  })

  it('按全量权限加载五类只读数据并呈现选定的工作台结构', async () => {
    const { wrapper } = await mountWorkbench()

    expect(wrapper.get('[data-test="workbench-title"]').text()).toContain('工作台')
    expect(wrapper.findAll('[data-test="workbench-quick-entry"]')).toHaveLength(5)
    expect(documentPlatformApiMock.approvalTasks.list).toHaveBeenCalledWith({
      scope: 'TODO',
      page: 1,
      pageSize: 10,
    })
    expect(documentPlatformApiMock.documentTasks.list).toHaveBeenCalledTimes(5)
    expect(documentPlatformApiMock.documentTasks.list.mock.calls.map(([query]) => query.status))
      .toEqual(expect.arrayContaining(['QUEUED', 'RUNNING', 'READY_TO_COMMIT', 'VALIDATION_FAILED', 'FAILED']))
    expect(businessReportingApiMock.overview.get).toHaveBeenCalledOnce()
    expect(businessReportingApiMock.operatingFinanceOverview.get)
      .toHaveBeenCalledWith({ analysisMode: 'LIVE' })
    expect(businessReportingApiMock.exceptions.list)
      .toHaveBeenCalledWith({ page: 1, pageSize: 10 })

    expect(wrapper.text()).toContain('我的工作流')
    expect(wrapper.text()).toContain('等待我处理')
    expect(wrapper.text()).toContain('系统处理中')
    expect(wrapper.text()).toContain('需要重新处理')
    expect(wrapper.text()).toContain('本月经营概况')
    expect(wrapper.text()).toContain('关键状态')
    expect(wrapper.text()).toContain('业务关注')
    expect(wrapper.text()).toContain('¥1,286,400.00')
    expect(wrapper.text()).toContain('386')
    expect(wrapper.text()).toContain('库存不足')
    expect(wrapper.text()).not.toContain('RUNNING')
    expect(wrapper.text()).not.toContain('CRITICAL')
    expect(wrapper.text()).not.toContain('NOT_CLOSED')
  })

  it('只请求和展示当前账号有权限的分区与快捷入口', async () => {
    const { wrapper } = await mountWorkbench([
      'platform:todo:view',
      'sales:project:create',
    ])

    expect(documentPlatformApiMock.approvalTasks.list).toHaveBeenCalledOnce()
    expect(documentPlatformApiMock.documentTasks.list).not.toHaveBeenCalled()
    expect(businessReportingApiMock.overview.get).not.toHaveBeenCalled()
    expect(businessReportingApiMock.operatingFinanceOverview.get).not.toHaveBeenCalled()
    expect(businessReportingApiMock.exceptions.list).not.toHaveBeenCalled()
    expect(wrapper.findAll('[data-test="workbench-quick-entry"]')).toHaveLength(1)
    expect(wrapper.text()).toContain('主合同')
    expect(wrapper.text()).not.toContain('本月经营概况')
    expect(wrapper.text()).not.toContain('关键状态')
    expect(wrapper.text()).not.toContain('业务关注')
  })

  it('隔离单分区失败且不把失败数据伪装为零', async () => {
    businessReportingApiMock.overview.get.mockRejectedValueOnce(new Error('经营概况加载失败'))

    const { wrapper } = await mountWorkbench()

    expect(wrapper.text()).toContain('经营概况加载失败')
    expect(wrapper.text()).toContain('主合同')
    expect(wrapper.text()).toContain('关键物料 M-100045')
    expect(wrapper.text()).not.toContain('¥0.00')
  })

  it('为成功空数据展示紧凑中文空态', async () => {
    documentPlatformApiMock.approvalTasks.list.mockResolvedValueOnce(page([], 0))
    documentPlatformApiMock.documentTasks.list.mockResolvedValue(page([], 0))
    businessReportingApiMock.overview.get.mockResolvedValueOnce({
      period: { dateFrom: '2026-07-01', dateTo: '2026-07-31' },
      salesShipmentAmount: '0',
      purchaseReceiptAmount: '0',
      inventoryInQuantity: '0',
      inventoryOutQuantity: '0',
      productionPlannedQuantity: '0',
      productionCompletedQuantity: '0',
      costAmount: '0',
      receivableBalance: '0',
      payableBalance: '0',
      receivedAmount: '0',
      paidAmount: '0',
      exceptionCount: 0,
      formalAccounting: false,
    })
    businessReportingApiMock.exceptions.list.mockResolvedValueOnce({
      items: [],
      summary: { exceptionCount: 0, criticalCount: 0, warningCount: 0, countsByType: {} },
      page: 1,
      pageSize: 10,
      total: 0,
      totalPages: 0,
    })

    const { wrapper } = await mountWorkbench()

    expect(wrapper.text()).toContain('当前没有需要处理的事项')
    expect(wrapper.text()).toContain('当前期间暂无经营概况')
    expect(wrapper.text()).toContain('当前没有需要关注的经营异常')
  })

  it('点击刷新后重新加载所有授权分区', async () => {
    const { wrapper } = await mountWorkbench()

    await wrapper.get('[data-test="workbench-refresh"]').trigger('click')
    await flushPromises()

    expect(documentPlatformApiMock.approvalTasks.list).toHaveBeenCalledTimes(2)
    expect(documentPlatformApiMock.documentTasks.list).toHaveBeenCalledTimes(10)
    expect(businessReportingApiMock.overview.get).toHaveBeenCalledTimes(2)
    expect(businessReportingApiMock.operatingFinanceOverview.get).toHaveBeenCalledTimes(2)
    expect(businessReportingApiMock.exceptions.list).toHaveBeenCalledTimes(2)
  })
})
