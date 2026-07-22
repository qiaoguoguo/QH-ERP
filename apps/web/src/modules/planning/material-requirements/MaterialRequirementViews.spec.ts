import ElementPlus from 'element-plus'
import { flushPromises, mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { AccountPermissionApiError } from '../../../shared/api/accountPermissionApi'
import type { PageResult } from '../../../shared/api/accountPermissionApi'
import type {
  MaterialRequirementAllocationRecord,
  MaterialRequirementRequirementLineRecord,
  MaterialRequirementRunDetailRecord,
  MaterialRequirementRunRecord,
  MaterialRequirementSubstituteHintRecord,
  MaterialRequirementSuggestionRecord,
} from '../../../shared/api/materialRequirementApi'
import {
  expectDrawerWideTableGoverned,
  expectNoBareIdFilters,
  expectStandardListPage,
} from '../../../test/pageGovernanceAssertions'
import { useAuthStore } from '../../../stores/authStore'
import MaterialRequirementRunDetailView from './MaterialRequirementRunDetailView.vue'
import MaterialRequirementRunListView from './MaterialRequirementRunListView.vue'
import detailSource from './MaterialRequirementRunDetailView.vue?raw'
import listSource from './MaterialRequirementRunListView.vue?raw'

const materialRequirementApiMock = vi.hoisted(() => ({
  runs: {
    create: vi.fn(),
    list: vi.fn(),
    get: vi.fn(),
    recalculate: vi.fn(),
    requirements: vi.fn(),
    allocations: vi.fn(),
    suggestions: vi.fn(),
    substituteHints: vi.fn(),
  },
  suggestions: {
    confirm: vi.fn(),
    dismiss: vi.fn(),
    convertRequisition: vi.fn(),
    convertWorkOrder: vi.fn(),
    convertOutsourcingOrder: vi.fn(),
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
    createMaterialRequirementRuns: vi.fn(),
  },
}))

const salesProjectApiMock = vi.hoisted(() => ({
  projects: { list: vi.fn() },
  listOrderLinkCandidates: vi.fn(),
}))

const masterDataApiMock = vi.hoisted(() => ({
  customers: { list: vi.fn() },
  materials: { list: vi.fn() },
}))

const salesApiMock = vi.hoisted(() => ({
  orders: { list: vi.fn() },
}))

vi.mock('../../../shared/api/materialRequirementApi', () => ({
  materialRequirementApi: materialRequirementApiMock,
}))

vi.mock('../../../shared/api/documentPlatformApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../shared/api/documentPlatformApi')>()),
  documentPlatformApi: documentPlatformApiMock,
  createIdempotencyKey: () => 'material-requirement-key',
}))

vi.mock('../../../shared/api/salesProjectApi', () => ({
  salesProjectApi: salesProjectApiMock,
}))

vi.mock('../../../shared/api/masterDataApi', () => ({
  masterDataApi: masterDataApiMock,
}))

vi.mock('../../../shared/api/salesApi', () => ({
  salesApi: salesApiMock,
}))

const completedRun: MaterialRequirementRunRecord = {
  id: 1001,
  runNo: 'MRP-026-0001',
  status: 'COMPLETED',
  statusName: '已完成',
  scopeSummary: '项目 PRJ-026 / 需求截至 2026-08-30',
  projectCount: 1,
  requirementLineCount: 3,
  shortageMaterialCount: 2,
  purchaseSuggestionCount: 1,
  productionSuggestionCount: 1,
  exceptionCount: 1,
  asOfBusinessDate: '2026-07-16',
  asOfTime: '2026-07-16T10:00:00+08:00',
  completedAt: '2026-07-16T10:01:00+08:00',
  expiresAt: '2026-07-16T10:30:00+08:00',
  createdByName: '计划员',
  stale: false,
  expired: false,
  allowedActions: ['RECALCULATE', 'EXPORT'],
  version: 4,
}

const staleRun: MaterialRequirementRunRecord = {
  ...completedRun,
  id: 1002,
  runNo: 'MRP-026-0002',
  status: 'STALE',
  statusName: '来源已变化',
  stale: true,
  expired: false,
  sourceChangedReason: '有效销售需求已变化',
  allowedActions: ['RECALCULATE'],
  version: 5,
}

const runDetail: MaterialRequirementRunDetailRecord = {
  ...completedRun,
  sourceFingerprint: 'fp-026',
  sourceCounts: {
    salesDemand: 2,
    bomComponent: 5,
    projectStock: 1,
    publicStock: 3,
    projectPurchase: 1,
    publicPurchase: 2,
    workOrder: 4,
  },
  previousRunId: null,
  failureCode: null,
  failureSummary: null,
}

const requirementLine: MaterialRequirementRequirementLineRecord = {
  id: 'REQ-1',
  lineNo: 10,
  demandSourceNo: 'SO-026/SDP-001',
  orderNo: 'SO-026',
  deliveryPlanNo: 'SDP-001',
  projectId: 20,
  projectNo: 'PRJ-026',
  projectName: '华东产线',
  finishedMaterialCode: 'FG-026',
  finishedMaterialName: '控制柜',
  bomVersionNo: 'BOM-FG-026-V2',
  bomPath: 'FG-026>M-900',
  materialId: 31,
  materialCode: 'M-900',
  materialName: '伺服电机',
  unitName: '台',
  requiredQuantity: '12.500000',
  coveredQuantity: '4.000000',
  shortageQuantity: '8.500000',
  requiredDate: '2026-08-20',
  estimatedAvailableDate: '2026-08-25',
  coverageStatus: 'SHORTAGE',
  suggestionType: 'PURCHASE_REQUISITION',
  exceptionReasonCode: 'SUPPLY_LATE',
  allowedActions: ['TRACE'],
}

const allocation: MaterialRequirementAllocationRecord = {
  id: 'ALLOC-1',
  requirementLineId: 'REQ-1',
  supplyType: 'PUBLIC_STOCK',
  supplyTypeName: '公共合格库存',
  ownershipType: 'PUBLIC',
  projectNo: null,
  warehouseName: '成品仓',
  sourceNo: 'INV-AVL-001',
  availableDate: '2026-07-16',
  allocatedQuantity: '4.000000',
  onTime: true,
  excludedReasonCode: null,
}

const lateAllocation: MaterialRequirementAllocationRecord = {
  ...allocation,
  id: 'ALLOC-2',
  supplyType: 'PURCHASE_SUPPLY',
  supplyTypeName: '公共采购供给',
  sourceNo: 'PO-026-001',
  availableDate: '2026-08-25',
  allocatedQuantity: '8.500000',
  onTime: false,
  excludedReasonCode: 'SUPPLY_LATE',
}

const workOrderAllocation: MaterialRequirementAllocationRecord = {
  ...allocation,
  id: 'ALLOC-3',
  supplyType: 'WORK_ORDER_SUPPLY',
  supplyTypeName: '项目工单供给',
  ownershipType: 'PROJECT',
  projectNo: 'PRJ-026',
  sourceNo: 'WO-026-001',
  availableDate: '2026-08-22',
  allocatedQuantity: '2.000000',
  onTime: false,
  excludedReasonCode: 'SUPPLY_LATE',
}

const reservedAllocation: MaterialRequirementAllocationRecord = {
  ...allocation,
  id: 'ALLOC-4',
  supplyType: 'RESERVED_STOCK',
  supplyTypeName: '预留占用库存',
  ownershipType: 'PUBLIC',
  sourceNo: 'RES-026-001',
  allocatedQuantity: '1.000000',
  excludedReasonCode: 'STOCK_RESERVED_OR_OCCUPIED',
}

const otherRequirementAllocation: MaterialRequirementAllocationRecord = {
  ...allocation,
  id: 'ALLOC-OTHER',
  requirementLineId: 'REQ-OTHER',
  supplyTypeName: '其他需求行库存',
  sourceNo: 'INV-OTHER',
}

const substituteHint: MaterialRequirementSubstituteHintRecord = {
  id: 'HINT-1',
  requirementLineId: 'REQ-1',
  mainMaterialId: 31,
  mainMaterialCode: 'M-900',
  mainMaterialName: '伺服电机',
  substituteMaterialId: 32,
  substituteMaterialCode: 'M-901',
  substituteMaterialName: '替代伺服电机',
  substituteRate: '1.000000',
  priority: 1,
  hintMessage: '仅供人工评估，不自动抵扣缺料',
}

const otherRequirementHint: MaterialRequirementSubstituteHintRecord = {
  ...substituteHint,
  id: 'HINT-OTHER',
  requirementLineId: 'REQ-OTHER',
  substituteMaterialCode: 'M-999',
  substituteMaterialName: '其他需求行替代料',
}

const openSuggestion: MaterialRequirementSuggestionRecord = {
  id: 'SUG-1',
  suggestionNo: 'MS-026-001',
  runId: 1001,
  suggestionType: 'PURCHASE_REQUISITION',
  status: 'OPEN',
  statusName: '待处理',
  materialSourceType: 'PURCHASED',
  projectId: 20,
  projectNo: 'PRJ-026',
  projectName: '华东产线',
  materialId: 31,
  materialCode: 'M-900',
  materialName: '伺服电机',
  unitName: '台',
  suggestedQuantity: '8.500000',
  suggestedDate: '2026-08-18',
  reasonCode: 'SUPPLY_LATE',
  reasonMessage: '准时供给不足',
  conversionAllowed: true,
  convertedRequisitionId: null,
  convertedRequisitionNo: null,
  actionDisabledReason: null,
  allowedActions: ['CONFIRM', 'DISMISS'],
  version: 2,
}

const confirmedSuggestion: MaterialRequirementSuggestionRecord = {
  ...openSuggestion,
  id: 'SUG-2',
  suggestionNo: 'MS-026-002',
  status: 'CONFIRMED',
  statusName: '已确认',
  allowedActions: ['CONVERT_REQUISITION', 'DISMISS'],
  version: 3,
}

const confirmedProductionSuggestion: MaterialRequirementSuggestionRecord = {
  ...openSuggestion,
  id: 'SUG-3',
  suggestionNo: 'MS-026-003',
  suggestionType: 'PRODUCTION_ORDER',
  status: 'CONFIRMED',
  statusName: '已确认',
  materialSourceType: 'MANUFACTURED',
  materialCode: 'FG-027',
  materialName: '项目自制柜',
  suggestedQuantity: '3.000000',
  conversionAllowed: true,
  allowedActions: ['CONVERT_WORK_ORDER'],
  version: 4,
}

const confirmedOutsourcingSuggestion: MaterialRequirementSuggestionRecord = {
  ...openSuggestion,
  id: 'SUG-4',
  suggestionNo: 'MS-026-004',
  suggestionType: 'PRODUCTION_ORDER',
  status: 'CONFIRMED',
  statusName: '已确认',
  materialSourceType: 'OUTSOURCED',
  materialCode: 'SF-027',
  materialName: '项目外协半成品',
  suggestedQuantity: '2.000000',
  conversionAllowed: true,
  allowedActions: ['CONVERT_OUTSOURCING_ORDER'],
  version: 5,
}

const productionSuggestion: MaterialRequirementSuggestionRecord = {
  ...openSuggestion,
  id: 'SUG-3',
  suggestionNo: 'MS-026-003',
  suggestionType: 'PRODUCTION_ORDER',
  status: 'OPEN',
  statusName: '待处理',
  materialSourceType: 'OUTSOURCED',
  materialCode: 'SF-026',
  materialName: '外协半成品',
  suggestedQuantity: '2.000000',
  conversionAllowed: false,
  actionDisabledReason: null,
  allowedActions: ['CONFIRM'],
  version: 1,
}

function page<T>(items: T[], pageSize = 10): PageResult<T> {
  return { items, total: items.length, page: 1, pageSize, totalPages: 1 }
}

function setup(permissions: string[]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'planner', displayName: '计划员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  return pinia
}

async function routerFor(path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/planning/material-requirements', name: 'planning-material-requirements', component: MaterialRequirementRunListView },
      { path: '/planning/material-requirements/:id', name: 'planning-material-requirement-detail', component: MaterialRequirementRunDetailView },
      { path: '/procurement/requisitions/:id', name: 'procurement-requisition-detail', component: { render: () => null } },
      { path: '/production/work-orders/:id', name: 'production-work-order-detail', component: { render: () => null } },
      { path: '/production/outsourcing-orders/:id', name: 'production-outsourcing-order-detail', component: { render: () => null } },
    ],
  })
  await router.push(path)
  await router.isReady()
  return router
}

function suggestionRow(wrapper: VueWrapper, texts: string[]) {
  const rows = wrapper.findAll('tr').filter((row) =>
    texts.every((text) => row.text().includes(text)),
  )
  expect(rows).toHaveLength(1)
  return rows[0]
}

async function openMoreActions(row: DOMWrapper<HTMLTableRowElement>) {
  const moreButton = row.findAll('button').find((button) => button.text() === '更多')
  expect(moreButton?.exists()).toBe(true)
  await moreButton!.trigger('click')
  await flushPromises()
}

function visiblePoppers() {
  return Array.from(document.body.querySelectorAll<HTMLElement>('.el-popper')).filter((popper) =>
    popper.getAttribute('aria-hidden') !== 'true' && popper.style.display !== 'none',
  )
}

async function waitFor<T>(condition: () => T | false | null | undefined, description: string, timeoutMs = 1000): Promise<T> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() <= deadline) {
    const result = condition()
    if (result) {
      return result
    }
    await new Promise((resolve) => setTimeout(resolve, 10))
    await flushPromises()
  }
  throw new Error(`等待${description}超时`)
}

function visiblePopperButtonByText(text: string) {
  return visiblePoppers()
    .flatMap((popper) => Array.from(popper.querySelectorAll<HTMLElement>('button')))
    .find((button) => button.textContent?.trim() === text)
}

function visiblePopperAction(testId: string) {
  return visiblePoppers()
    .flatMap((popper) => Array.from(popper.querySelectorAll<HTMLElement>(`[data-test="${testId}"]`)))
    .at(-1)
}

async function expectTeleportedButtonText(row: DOMWrapper<HTMLTableRowElement>, text: string) {
  await openMoreActions(row)
  const action = await waitFor(() => visiblePopperButtonByText(text), `可见更多菜单动作“${text}”`)
  expect(action.textContent?.trim()).toBe(text)
}

async function clickTeleportedAction(row: DOMWrapper<HTMLTableRowElement>, testId: string) {
  await openMoreActions(row)
  const action = await waitFor(() => visiblePopperAction(testId), `可见更多菜单动作 ${testId}`)
  action.click()
  await flushPromises()
}

describe('026 订单缺料分析页面', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  beforeEach(() => {
    vi.clearAllMocks()
    materialRequirementApiMock.runs.list.mockResolvedValue(page([completedRun, staleRun]))
    materialRequirementApiMock.runs.create.mockResolvedValue(completedRun)
    materialRequirementApiMock.runs.get.mockResolvedValue(runDetail)
    materialRequirementApiMock.runs.recalculate.mockResolvedValue({ ...completedRun, id: 1003, runNo: 'MRP-026-0003' })
    materialRequirementApiMock.runs.requirements.mockResolvedValue(page([requirementLine]))
    materialRequirementApiMock.runs.allocations.mockResolvedValue(page([
      allocation,
      lateAllocation,
      workOrderAllocation,
      reservedAllocation,
      otherRequirementAllocation,
    ], 50))
    materialRequirementApiMock.runs.suggestions.mockResolvedValue(page([
      openSuggestion,
      confirmedSuggestion,
      confirmedProductionSuggestion,
      confirmedOutsourcingSuggestion,
      productionSuggestion,
    ]))
    materialRequirementApiMock.runs.substituteHints.mockResolvedValue(page([substituteHint, otherRequirementHint]))
    materialRequirementApiMock.suggestions.confirm.mockResolvedValue({ ...openSuggestion, status: 'CONFIRMED', version: 3 })
    materialRequirementApiMock.suggestions.dismiss.mockResolvedValue({ ...openSuggestion, status: 'DISMISSED', version: 3 })
    materialRequirementApiMock.suggestions.convertRequisition.mockResolvedValue({
      ...confirmedSuggestion,
      status: 'CONVERTED',
      convertedRequisitionId: 9001,
      convertedRequisitionNo: 'PR-026-001',
      version: 4,
    })
    materialRequirementApiMock.suggestions.convertWorkOrder.mockResolvedValue({
      suggestionId: 'SUG-3',
      status: 'CONVERTED',
      targetObjectType: 'WORK_ORDER',
      targetObjectId: 7001,
      targetObjectNo: 'WO-027-001',
      targetRoute: '/production/work-orders/7001?sourceMrpSuggestionId=SUG-3',
      version: 6,
    })
    materialRequirementApiMock.suggestions.convertOutsourcingOrder.mockResolvedValue({
      suggestionId: 'SUG-4',
      status: 'CONVERTED',
      targetObjectType: 'OUTSOURCING_ORDER',
      targetObjectId: 8001,
      targetObjectNo: 'OS-027-001',
      targetRoute: '/production/outsourcing-orders/8001?sourceMrpSuggestionId=SUG-4',
      version: 7,
    })
    documentPlatformApiMock.exports.createMaterialRequirementRuns.mockResolvedValue({
      id: 801,
      taskNo: 'TASK-MRP-EXPORT',
      taskType: 'MATERIAL_REQUIREMENT_RUN_EXPORT',
      direction: 'EXPORT',
      stage: 'EXPORT',
      status: 'QUEUED',
      availableActions: ['CANCEL'],
      version: 1,
    })
    salesProjectApiMock.projects.list.mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 50, totalPages: 0 })
    salesProjectApiMock.listOrderLinkCandidates.mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 50, totalPages: 0 })
    masterDataApiMock.customers.list.mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 50, totalPages: 0 })
    masterDataApiMock.materials.list.mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 50, totalPages: 0 })
    salesApiMock.orders.list.mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 50, totalPages: 0 })
  })

  it('快照列表展示运行范围、状态、核心数量和受控操作，不出现范围外自动化文案', async () => {
    const pinia = setup([
      'planning:material-requirement:view',
      'planning:material-requirement:calculate',
      'planning:material-requirement:export',
      'platform:document-task:create',
    ])
    const router = await routerFor('/planning/material-requirements')
    const wrapper = mount(MaterialRequirementRunListView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expectStandardListPage(wrapper)
    expectNoBareIdFilters(wrapper, ['项目 ID', '客户 ID', '合同 ID', '销售订单 ID', '物料 ID'])
    expect(materialRequirementApiMock.runs.list).toHaveBeenCalledWith({
      projectId: undefined,
      customerId: undefined,
      contractId: undefined,
      orderId: undefined,
      materialId: undefined,
      requiredDateTo: '',
      status: undefined,
      expired: undefined,
      page: 1,
      pageSize: 10,
    })
    expect(wrapper.text()).toContain('订单缺料分析')
    expect(wrapper.text()).toContain('MRP-026-0001')
    expect(wrapper.text()).toContain('项目 PRJ-026 / 需求截至 2026-08-30')
    expect(wrapper.text()).toContain('需求行 3')
    expect(wrapper.text()).toContain('短缺物料 2')
    expect(wrapper.text()).toContain('采购建议 1')
    expect(wrapper.text()).toContain('生产建议 1')
    expect(wrapper.text()).toContain('来源已变化')
    expect(wrapper.text()).not.toContain('自动采购订单')
    expect(wrapper.text()).not.toContain('自动建工单')
    expect(wrapper.text()).not.toContain('齐套承诺')
    expect(wrapper.text()).not.toContain('APS')

    await wrapper.find('[data-test="export-material-requirements"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.exports.createMaterialRequirementRuns).toHaveBeenCalledWith(expect.objectContaining({
      requiredDateTo: '',
      idempotencyKey: 'material-requirement-key',
    }))
    expect(wrapper.text()).toContain('TASK-MRP-EXPORT')

    await wrapper.find('[data-test="recalculate-run-1001"]').trigger('click')
    await flushPromises()
    expect(materialRequirementApiMock.runs.recalculate).toHaveBeenCalledWith(1001, {
      version: 4,
      idempotencyKey: 'material-requirement-key',
    })
  })

  it('仅规划导出权限即可显示导出入口，不额外依赖平台文档创建权限', async () => {
    const pinia = setup([
      'planning:material-requirement:view',
      'planning:material-requirement:export',
    ])
    const router = await routerFor('/planning/material-requirements')
    const wrapper = mount(MaterialRequirementRunListView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expect(wrapper.find('[data-test="export-material-requirements"]').exists()).toBe(true)
  })

  it('筛选引用接口 403 被当作受限选项处理，不产生未处理控制台错误', async () => {
    const pinia = setup([
      'planning:material-requirement:view',
      'planning:material-requirement:export',
    ])
    const router = await routerFor('/planning/material-requirements')
    const wrapper = mount(MaterialRequirementRunListView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    salesProjectApiMock.projects.list.mockRejectedValueOnce(new AccountPermissionApiError(
      '缺少项目查看权限',
      'AUTH_FORBIDDEN',
      403,
      'trace-project-options',
    ))

    const projectSelect = wrapper.findAllComponents({ name: 'BusinessReferenceSelect' })[0]
    try {
      await expect((projectSelect.vm as unknown as { remoteSearch(keyword: string): Promise<void> })
        .remoteSearch('PRJ')).resolves.toBeUndefined()
      expect(consoleError).not.toHaveBeenCalled()
    } finally {
      consoleError.mockRestore()
    }
  })

  it('详情工作台展示汇总、缺料结果、追溯抽屉和建议动作双门禁', async () => {
    const pinia = setup([
      'planning:material-requirement:view',
      'planning:material-requirement:manage-suggestion',
      'planning:material-requirement:convert-requisition',
      'planning:material-requirement:convert-production',
      'planning:material-requirement:convert-outsourcing',
      'procurement:requisition:create',
      'production:work-order:create',
      'production:outsourcing:create',
      'sales:effective-demand:view',
      'material:bom:view',
      'inventory:balance:view',
      'procurement:supply:view',
      'production:work-order:view',
    ])
    const router = await routerFor('/planning/material-requirements/1001')
    const wrapper = mount(MaterialRequirementRunDetailView, { attachTo: document.body, global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expect(wrapper.find('.page-heading').exists()).toBe(true)
    expect(wrapper.find('.summary-strip').exists()).toBe(true)
    expect(wrapper.text()).toContain('MRP-026-0001')
    expect(wrapper.text()).toContain('需求行数3')
    expect(wrapper.text()).toContain('短缺物料2')
    expect(wrapper.text()).toContain('采购建议1')
    expect(wrapper.text()).toContain('生产建议1')
    expect(wrapper.text()).toContain('销售需求2')
    expect(wrapper.text()).toContain('BOM 组件5')
    expect(wrapper.text()).toContain('项目库存1')
    expect(wrapper.text()).toContain('公共库存3')
    expect(wrapper.text()).toContain('项目采购1')
    expect(wrapper.text()).toContain('公共采购2')
    expect(wrapper.text()).toContain('工单供给4')
    expect(wrapper.text()).toContain('PRJ-026 华东产线')
    expect(wrapper.text()).toContain('SO-026/SDP-001')
    expect(wrapper.text()).toContain('FG-026 控制柜')
    expect(wrapper.text()).toContain('M-900 伺服电机')
    expect(wrapper.text()).toContain('需求 12.5')
    expect(wrapper.text()).toContain('覆盖 4')
    expect(wrapper.text()).toContain('短缺 8.5')
    expect(wrapper.text()).toContain('预计可用 2026-08-25')
    expect(wrapper.text()).toContain('采购请购建议')
    expect(wrapper.text()).toContain('生产建议')
    expect(wrapper.text()).toContain('项目自制柜')
    expect(wrapper.text()).toContain('项目外协半成品')
    await expectTeleportedButtonText(
      suggestionRow(wrapper, ['MS-026-003', 'FG-027', '项目自制柜']),
      '转生产工单',
    )
    await expectTeleportedButtonText(
      suggestionRow(wrapper, ['MS-026-004', 'SF-027', '项目外协半成品']),
      '转外协订单',
    )
    expect(wrapper.text()).not.toContain('生成采购订单')
    expect(wrapper.text()).not.toContain('自动建工单')
    expect(wrapper.text()).not.toContain('齐套承诺')
    expect(wrapper.text()).not.toContain('APS')

    await wrapper.find('[data-test="trace-requirement-REQ-1"]').trigger('click')
    await flushPromises()
    expectDrawerWideTableGoverned(wrapper)
    expect(materialRequirementApiMock.runs.allocations).toHaveBeenCalledWith(1001, {
      requirementLineId: 'REQ-1',
      page: 1,
      pageSize: 50,
    })
    expect(wrapper.text()).toContain('需求链')
    expect(wrapper.text()).toContain('BOM')
    expect(wrapper.text()).toContain('项目/公共库存')
    expect(wrapper.text()).toContain('采购供给')
    expect(wrapper.text()).toContain('工单供给')
    expect(wrapper.text()).toContain('预留占用')
    expect(wrapper.text()).toContain('替代料提示')
    expect(wrapper.text()).toContain('排除原因')
    expect(wrapper.text()).toContain('公共合格库存')
    expect(wrapper.text()).toContain('公共采购供给')
    expect(wrapper.text()).toContain('项目工单供给')
    expect(wrapper.text()).toContain('预留占用库存')
    expect(wrapper.text()).toContain('库存已预留或占用')
    expect(wrapper.text()).toContain('替代伺服电机')
    expect(wrapper.text()).toContain('替代比例')
    expect(wrapper.text()).toContain('1')
    expect(wrapper.text()).toContain('仅供人工评估，不自动抵扣缺料')
    expect(wrapper.text()).not.toContain('其他需求行库存')
    expect(wrapper.text()).not.toContain('其他需求行替代料')

    await wrapper.find('[data-test="confirm-suggestion-SUG-1"]').trigger('click')
    await flushPromises()
    expect(materialRequirementApiMock.suggestions.confirm).toHaveBeenCalledWith('SUG-1', {
      version: 2,
      idempotencyKey: 'material-requirement-key',
    })

    await clickTeleportedAction(suggestionRow(wrapper, ['MS-026-001', 'M-900']), 'dismiss-suggestion-SUG-1')
    await flushPromises()
    await wrapper.find('[data-test="dismiss-suggestion-reason"]').setValue('客户改期，暂不采购')
    await wrapper.find('[data-test="submit-dismiss-suggestion"]').trigger('click')
    await flushPromises()
    expect(materialRequirementApiMock.suggestions.dismiss).toHaveBeenCalledWith('SUG-1', {
      version: 2,
      reason: '客户改期，暂不采购',
      idempotencyKey: 'material-requirement-key',
    })

    await wrapper.find('[data-test="convert-suggestion-SUG-2"]').trigger('click')
    await flushPromises()
    expect(materialRequirementApiMock.suggestions.convertRequisition).toHaveBeenCalledWith('SUG-2', {
      version: 3,
      idempotencyKey: 'material-requirement-key',
    })

    await clickTeleportedAction(
      suggestionRow(wrapper, ['MS-026-003', 'FG-027', '项目自制柜']),
      'convert-work-order-suggestion-SUG-3',
    )
    await flushPromises()
    expect(materialRequirementApiMock.suggestions.convertWorkOrder).toHaveBeenCalledWith('SUG-3', {
      version: 4,
      idempotencyKey: 'material-requirement-key',
    })
    expect(router.currentRoute.value.name).toBe('production-work-order-detail')
    expect(router.currentRoute.value.params.id).toBe('7001')
    expect(router.currentRoute.value.query.sourceMrpSuggestionId).toBe('SUG-3')

    await router.push('/planning/material-requirements/1001')
    await flushPromises()
    await clickTeleportedAction(
      suggestionRow(wrapper, ['MS-026-004', 'SF-027', '项目外协半成品']),
      'convert-outsourcing-suggestion-SUG-4',
    )
    await flushPromises()
    expect(materialRequirementApiMock.suggestions.convertOutsourcingOrder).toHaveBeenCalledWith('SUG-4', {
      version: 5,
      idempotencyKey: 'material-requirement-key',
    })
    expect(router.currentRoute.value.name).toBe('production-outsourcing-order-detail')
    expect(router.currentRoute.value.params.id).toBe('8001')
    expect(router.currentRoute.value.query.sourceMrpSuggestionId).toBe('SUG-4')
  })

  it('来源受限时主表与建议使用受限文案，不把空字段渲染成未返回或来源明文', async () => {
    materialRequirementApiMock.runs.requirements.mockResolvedValueOnce(page([{
      ...requirementLine,
      demandSourceNo: null,
      orderNo: null,
      deliveryPlanNo: null,
      projectId: null,
      projectNo: null,
      projectName: null,
      finishedMaterialCode: null,
      finishedMaterialName: null,
      bomVersionNo: null,
      bomPath: null,
    }]))
    materialRequirementApiMock.runs.suggestions.mockResolvedValueOnce(page([{
      ...openSuggestion,
      projectNo: 'PRJ-026',
      projectName: '华东产线',
    }]))
    const pinia = setup(['planning:material-requirement:view'])
    const router = await routerFor('/planning/material-requirements/1001')
    const wrapper = mount(MaterialRequirementRunDetailView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('来源权限受限')
    expect(wrapper.text()).not.toContain('项目未返回')
    expect(wrapper.text()).not.toContain('订单/计划未返回')
    expect(wrapper.text()).not.toContain('成品未返回')
    expect(wrapper.text()).not.toContain('BOM 未返回')
    expect(wrapper.text()).not.toContain('PRJ-026 华东产线')
  })

  it('有 BOM 来源权限时显示真实 BOM 编码，缺来源时不再渲染旧占位', async () => {
    materialRequirementApiMock.runs.requirements.mockResolvedValueOnce(page([
      {
        ...requirementLine,
        id: 'REQ-FG',
        materialId: 30,
        materialCode: 'FG-026',
        materialName: '控制柜',
        finishedMaterialCode: 'FG-026',
        finishedMaterialName: '控制柜',
        bomVersionNo: 'BOM-FG-026-V2',
      },
      {
        ...requirementLine,
        id: 'REQ-NO-BOM-SOURCE',
        materialId: 32,
        materialCode: 'M-NO-BOM',
        materialName: '无 BOM 来源物料',
        finishedMaterialCode: 'FG-026',
        finishedMaterialName: '控制柜',
        bomVersionNo: null,
        bomPath: null,
      },
    ]))
    const pinia = setup([
      'planning:material-requirement:view',
      'sales:effective-demand:view',
      'material:bom:view',
    ])
    const router = await routerFor('/planning/material-requirements/1001')
    const wrapper = mount(MaterialRequirementRunDetailView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('BOM-FG-026-V2')
    expect(wrapper.text()).toContain('暂无 BOM 来源')
    expect(wrapper.text()).not.toContain('BOM 未返回')
  })

  it('空预计可用日期显示业务文案，真实 availableDate 继续正常展示', async () => {
    materialRequirementApiMock.runs.requirements.mockResolvedValueOnce(page([
      { ...requirementLine, estimatedAvailableDate: null },
      {
        ...requirementLine,
        id: 'REQ-2',
        materialId: 32,
        materialCode: 'M-901',
        materialName: '编码器',
        estimatedAvailableDate: '2026-09-02',
      },
    ]))
    materialRequirementApiMock.runs.allocations.mockResolvedValueOnce(page([
      { ...allocation, availableDate: null, excludedReasonCode: 'SUPPLY_LATE' },
      { ...lateAllocation, availableDate: '2026-08-25' },
    ], 50))
    const pinia = setup([
      'planning:material-requirement:view',
      'sales:effective-demand:view',
      'material:bom:view',
      'inventory:balance:view',
      'procurement:supply:view',
      'production:work-order:view',
    ])
    const router = await routerFor('/planning/material-requirements/1001')
    const wrapper = mount(MaterialRequirementRunDetailView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('暂无可承诺日期')
    expect(wrapper.text()).toContain('预计可用 2026-09-02')

    await wrapper.find('[data-test="trace-requirement-REQ-1"]').trigger('click')
    await flushPromises()
    const drawerText = wrapper.find('.el-drawer').text()
    expect(drawerText).toContain('暂无可承诺日期')
    expect(drawerText).toContain('2026-08-25')
  })

  it('来源变化或过期运行禁用建议写动作，且页面源码不做前端净算', async () => {
    materialRequirementApiMock.runs.get.mockResolvedValueOnce({
      ...runDetail,
      status: 'STALE',
      stale: true,
      allowedActions: ['RECALCULATE'],
    })
    const pinia = setup([
      'planning:material-requirement:view',
      'planning:material-requirement:manage-suggestion',
      'planning:material-requirement:convert-requisition',
      'procurement:requisition:create',
    ])
    const router = await routerFor('/planning/material-requirements/1001')
    const wrapper = mount(MaterialRequirementRunDetailView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('来源已变化，建议动作已禁用')
    expect(wrapper.find('[data-test="confirm-suggestion-SUG-1"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="convert-suggestion-SUG-2"]').exists()).toBe(false)
    expect(listSource).not.toMatch(/parseFloat|parseInt|Number\(/)
    expect(detailSource).not.toMatch(/parseFloat|parseInt|Number\(/)
  })

  it('追溯抽屉按来源权限脱敏，不把无权限来源显示为 0 或真实数据', async () => {
    const pinia = setup(['planning:material-requirement:view'])
    const router = await routerFor('/planning/material-requirements/1001')
    const wrapper = mount(MaterialRequirementRunDetailView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    await wrapper.find('[data-test="trace-requirement-REQ-1"]').trigger('click')
    await flushPromises()

    const drawerText = wrapper.find('.el-drawer').text()
    expect(drawerText).toContain('销售来源权限受限')
    expect(drawerText).toContain('BOM 来源权限受限')
    expect(drawerText).toContain('库存来源权限受限')
    expect(drawerText).toContain('采购来源权限受限')
    expect(drawerText).toContain('生产来源权限受限')
    expect(drawerText).not.toContain('SO-026/SDP-001')
    expect(drawerText).not.toContain('BOM-FG-026-V2')
    expect(drawerText).not.toContain('公共合格库存')
    expect(drawerText).not.toContain('公共采购供给')
    expect(drawerText).not.toContain('项目工单供给')
    expect(drawerText).not.toContain('预留占用库存')
    expect(drawerText).not.toContain('替代伺服电机')
    expect(drawerText).not.toContain('INV-AVL-001')
    expect(drawerText).not.toContain('PO-026-001')
    expect(drawerText).not.toContain('WO-026-001')
  })

  it('替代料主物料和替代物料脱敏为空时显示来源权限受限，不用 ID 或待补全冒充摘要', async () => {
    materialRequirementApiMock.runs.substituteHints.mockResolvedValueOnce(page([{
      ...substituteHint,
      mainMaterialCode: null,
      mainMaterialName: null,
      substituteMaterialCode: null,
      substituteMaterialName: null,
    }]))
    const pinia = setup([
      'planning:material-requirement:view',
      'sales:effective-demand:view',
      'material:bom:view',
      'inventory:balance:view',
      'procurement:supply:view',
      'production:work-order:view',
    ])
    const router = await routerFor('/planning/material-requirements/1001')
    const wrapper = mount(MaterialRequirementRunDetailView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    await wrapper.find('[data-test="trace-requirement-REQ-1"]').trigger('click')
    await flushPromises()

    const drawerText = wrapper.find('.el-drawer').text()
    expect(drawerText).toContain('来源权限受限')
    expect(drawerText).not.toContain('主物料 #31')
    expect(drawerText).not.toContain('替代物料 #32')
    expect(drawerText).not.toContain('信息待补全')
  })

  it('失败快照使用 failureCode 与 failureSummary 展示稳定中文摘要', async () => {
    materialRequirementApiMock.runs.get.mockResolvedValueOnce({
      ...runDetail,
      status: 'FAILED',
      statusName: '失败',
      failureCode: 'BOM_NOT_FOUND',
      failureSummary: '物料 M-900 缺少有效 BOM',
      allowedActions: ['RECALCULATE'],
    })
    const pinia = setup(['planning:material-requirement:view'])
    const router = await routerFor('/planning/material-requirements/1001')
    const wrapper = mount(MaterialRequirementRunDetailView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('未找到 BOM')
    expect(wrapper.text()).toContain('物料 M-900 缺少有效 BOM')
  })

  it('页面不使用会触发未解析告警的 el-checkbox 与 v-loading 语法', () => {
    expect(listSource).not.toContain('<el-checkbox')
    expect(listSource).not.toContain('v-loading')
    expect(detailSource).not.toContain('v-loading')
  })
})
