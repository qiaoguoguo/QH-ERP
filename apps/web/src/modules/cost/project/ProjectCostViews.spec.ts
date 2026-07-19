import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PageResult } from '../../../shared/api/accountPermissionApi'
import {
  type ProjectCostAdjustmentRecord,
  type ProjectCostCalculationDetail,
  type ProjectCostProjectDetail,
  type ProjectCostPublicExpenseCandidate,
  type ProjectCostSourceRecord,
  type ProjectCostVarianceRecord,
  type ProjectCostWorkbenchRecord,
} from '../../../shared/api/projectCostApi'
import { useAuthStore } from '../../../stores/authStore'
import { useConfirmActionMock } from '../../../test/setup'
import ProjectCostAdjustmentDetailView from './ProjectCostAdjustmentDetailView.vue'
import ProjectCostAdjustmentFormView from './ProjectCostAdjustmentFormView.vue'
import ProjectCostAdjustmentListView from './ProjectCostAdjustmentListView.vue'
import ProjectCostCalculationDetailView from './ProjectCostCalculationDetailView.vue'
import ProjectCostProjectDetailView from './ProjectCostProjectDetailView.vue'
import ProjectCostVarianceListView from './ProjectCostVarianceListView.vue'
import ProjectCostWorkbenchView from './ProjectCostWorkbenchView.vue'

const projectCostApiMock = vi.hoisted(() => ({
  projectCosts: {
    list: vi.fn(),
    getProject: vi.fn(),
    createCalculation: vi.fn(),
  },
  calculations: {
    get: vi.fn(),
    sources: vi.fn(),
    entries: vi.fn(),
    variances: vi.fn(),
    recalculate: vi.fn(),
    confirm: vi.fn(),
    cancel: vi.fn(),
  },
  adjustments: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    submit: vi.fn(),
    cancel: vi.fn(),
    publicExpenseCandidates: vi.fn(),
  },
}))
const confirmActionMock = useConfirmActionMock()

vi.mock('../../../shared/api/projectCostApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../shared/api/projectCostApi')>()),
  projectCostApi: projectCostApiMock,
}))

vi.mock('../../../shared/api/documentPlatformApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../shared/api/documentPlatformApi')>()),
  createIdempotencyKey: () => 'project-cost-key',
}))

function page<T>(items: T[], pageNumber = 1, pageSize = 10): PageResult<T> {
  return { items, total: items.length, page: pageNumber, pageSize }
}

const workbenchRecord: ProjectCostWorkbenchRecord = {
  projectId: 12,
  projectNo: 'SP-202607-001',
  projectName: '华东扩产项目',
  customerName: '华东客户',
  ownerDisplayName: '负责人',
  projectStatus: 'ACTIVE',
  calculationId: 91,
  calculationNo: 'PCC-202607-001',
  calculationStatus: 'CALCULATED',
  freshnessStatus: 'STALE',
  completenessStatus: 'INCOMPLETE',
  cutoffDate: '2026-07-31',
  totalCost: '838.000000',
  wipCost: '12.000000',
  deliveredCost: '826.000000',
  shipmentPretaxRevenue: '10000.000000',
  shipmentGrossMargin: '9162.000000',
  shipmentGrossMarginRate: '0.916200',
  openVarianceCount: 2,
  blockingVarianceCount: 1,
  provisionalSourceCount: 1,
  unpricedSourceCount: 1,
  amountVisible: true,
  sourceVisible: true,
  restrictedReason: null,
  allowedActions: ['CALCULATE', 'RECALCULATE'],
  actionDisabledReasons: {
    CONFIRM: '来源已变化，请重算后再确认。',
  },
  version: 7,
  sourceFingerprint: 'fp-029',
}

const restrictedWorkbenchRecord: ProjectCostWorkbenchRecord = {
  ...workbenchRecord,
  projectId: 13,
  projectNo: 'SP-202607-002',
  projectName: '金额受限项目',
  calculationId: null,
  calculationNo: null,
  calculationStatus: 'DRAFT',
  freshnessStatus: 'CURRENT',
  amountVisible: false,
  sourceVisible: false,
  restrictedReason: '无权查看成本金额',
  totalCost: null,
  wipCost: null,
  deliveredCost: null,
  shipmentPretaxRevenue: null,
  shipmentGrossMargin: null,
  shipmentGrossMarginRate: null,
  openVarianceCount: null,
  blockingVarianceCount: null,
  provisionalSourceCount: null,
  unpricedSourceCount: null,
  allowedActions: [],
  actionDisabledReasons: {},
}

const projectDetail: ProjectCostProjectDetail = {
  ...workbenchRecord,
  latestCalculationId: 91,
  latestCalculationNo: 'PCC-202607-001',
  finishedCost: '0.000000',
  adjustmentAmount: '50.000000',
  invoicePretaxRevenue: '0.000000',
  invoiceGrossMargin: '0.000000',
  targetRevenue: '100000.000000',
  targetGrossMargin: '99162.000000',
  categorySummaries: [
    { category: 'MATERIAL', amount: '168.000000', sourceCount: 4 },
    { category: 'LABOR', amount: '300.000000', sourceCount: 1 },
    { category: 'OUTSOURCING', amount: '120.000000', sourceCount: 1 },
    { category: 'MANUFACTURING_OVERHEAD', amount: '50.000000', sourceCount: 1 },
    { category: 'PROJECT_EXPENSE', amount: '200.000000', sourceCount: 1 },
  ],
  stageSummaries: [
    { stage: 'WIP', amount: '12.000000' },
    { stage: 'DELIVERED', amount: '826.000000' },
  ],
  calculations: [{ id: 91, calculationNo: 'PCC-202607-001', status: 'CALCULATED', cutoffDate: '2026-07-31', calculatedAt: '2026-07-31T10:00:00+08:00' }],
  auditSummary: [{ action: 'PROJECT_COST_CALCULATE', operatorUsername: 'admin', createdAt: '2026-07-31T10:00:00+08:00' }],
}

const calculationDetail: ProjectCostCalculationDetail = {
  id: 91,
  projectId: 12,
  projectNo: 'SP-202607-001',
  projectName: '华东扩产项目',
  calculationNo: 'PCC-202607-001',
  status: 'CALCULATED',
  freshnessStatus: 'STALE',
  completenessStatus: 'INCOMPLETE',
  cutoffDate: '2026-07-31',
  isCurrent: true,
  sourceFingerprint: 'fp-029',
  amountVisible: true,
  sourceVisible: true,
  restrictedReason: null,
  totalCost: '838.000000',
  shipmentPretaxRevenue: '10000.000000',
  shipmentGrossMargin: '9162.000000',
  shipmentGrossMarginRate: '0.916200',
  openVarianceCount: 2,
  blockingVarianceCount: 1,
  provisionalSourceCount: 1,
  unpricedSourceCount: 1,
  calculatedByName: '成本会计',
  calculatedAt: '2026-07-31T10:00:00+08:00',
  confirmedByName: null,
  confirmedAt: null,
  version: 7,
  allowedActions: ['RECALCULATE', 'CONFIRM', 'CANCEL'],
  actionDisabledReasons: {},
}

const sourceRecord: ProjectCostSourceRecord = {
  id: 501,
  calculationId: 91,
  projectId: 12,
  category: 'MATERIAL',
  stage: 'WIP',
  sourceStatus: 'ACTUAL',
  sourceType: 'INVENTORY_ISSUE',
  sourceNo: 'MI-001',
  sourceSummary: '生产领料 MI-001',
  sourceRoute: { name: 'inventory-document-detail', params: { id: 501 } },
  businessDate: '2026-07-12',
  materialCode: 'RM-001',
  materialName: '原材料 A',
  unitName: '千克',
  quantity: '80.000000',
  unitPrice: '2.100000',
  sourceAmount: '168.000000',
  amountVisible: true,
  sourceVisible: true,
  restrictedReason: null,
}

const restrictedSourceRecord: ProjectCostSourceRecord = {
  ...sourceRecord,
  id: 502,
  sourceStatus: 'RESTRICTED',
  sourceType: 'OUTSOURCING_RECEIPT',
  sourceNo: null,
  sourceSummary: '已纳入一条受限来源',
  sourceRoute: null,
  quantity: null,
  unitPrice: null,
  sourceAmount: null,
  amountVisible: false,
  sourceVisible: false,
  restrictedReason: '来源权限受限，仅显示脱敏摘要',
}

const varianceRecord: ProjectCostVarianceRecord = {
  id: 701,
  calculationId: 91,
  projectId: 12,
  projectNo: 'SP-202607-001',
  projectName: '华东扩产项目',
  varianceType: 'OUTSOURCING_ESTIMATE_ACTUAL',
  severity: 'BLOCKING',
  sourceType: 'OUTSOURCING_RECEIPT',
  sourceNo: 'OSR-001',
  sourceSummary: '外协收货实际价替换暂估',
  expectedAmount: '100.000000',
  actualAmount: '120.000000',
  differenceAmount: '20.000000',
  status: 'OPEN',
  resolvedAdjustmentNo: null,
  description: '外协实际金额与暂估金额存在差异',
  amountVisible: true,
  sourceVisible: true,
  restrictedReason: null,
}

const adjustmentRecord: ProjectCostAdjustmentRecord = {
  id: 301,
  adjustmentNo: 'PCA-202607-001',
  adjustmentType: 'PUBLIC_EXPENSE_ALLOCATION',
  status: 'DRAFT',
  businessDate: '2026-07-31',
  reason: '公共制造费用分配',
  approvalStatus: 'DRAFT',
  rejectedReason: null,
  originalAdjustmentNo: null,
  amountVisible: true,
  sourceVisible: true,
  restrictedReason: null,
  totalAmount: '50.000000',
  version: 3,
  allowedActions: ['UPDATE', 'SUBMIT', 'CANCEL'],
  actionDisabledReasons: {},
  lines: [{
    id: 401,
    projectId: 12,
    projectNo: 'SP-202607-001',
    projectName: '华东扩产项目',
    costCategory: 'MANUFACTURING_OVERHEAD',
    costStage: 'DIRECT_PROJECT',
    direction: 'INCREASE',
    amount: '50.000000',
    publicExpenseLineId: 801,
    sourceNo: 'EXP-001',
    reason: '公共费用分配',
  }],
}

const restrictedAdjustmentRecord: ProjectCostAdjustmentRecord = {
  ...adjustmentRecord,
  adjustmentNo: 'PCA-RESTRICTED',
  amountVisible: false,
  sourceVisible: false,
  restrictedReason: '无权查看成本金额',
  totalAmount: null,
  lines: [{
    ...adjustmentRecord.lines[0],
    amount: null,
    publicExpenseLineId: null,
    sourceNo: null,
  }],
}

const publicExpenseCandidate: ProjectCostPublicExpenseCandidate = {
  expenseLineId: 801,
  expenseNo: 'EXP-001',
  supplierName: '公共供应商',
  categoryName: '制造费用',
  businessDate: '2026-07-30',
  taxExcludedAmount: '200.000000',
  allocatedAmount: '150.000000',
  availableAmount: '50.000000',
  amountVisible: true,
  sourceVisible: true,
  restrictedReason: null,
}

const restrictedPublicExpenseCandidate: ProjectCostPublicExpenseCandidate = {
  ...publicExpenseCandidate,
  expenseLineId: null,
  expenseNo: '',
  supplierName: null,
  categoryName: null,
  taxExcludedAmount: null,
  allocatedAmount: null,
  availableAmount: null,
  amountVisible: false,
  sourceVisible: false,
  restrictedReason: '来源权限受限，仅显示脱敏摘要',
}

async function mountCostView(component: object, path: string, permissions = [
  'cost:project-cost:view',
  'cost:project-cost:source-view',
  'cost:project-cost:amount-view',
  'cost:project-cost:calculate',
  'cost:project-cost:confirm',
  'cost:project-cost:cancel',
  'cost:project-cost-adjustment:view',
  'cost:project-cost-adjustment:create',
  'cost:project-cost-adjustment:update',
  'cost:project-cost-adjustment:submit',
  'cost:project-cost-adjustment:cancel',
  'cost:project-cost-variance:view',
]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'cost_user', displayName: '成本用户', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/cost/project-costs', name: 'cost-project-costs', component },
      { path: '/cost/project-costs/:projectId', name: 'cost-project-cost-detail', component },
      { path: '/cost/project-cost-calculations/:id', name: 'cost-project-cost-calculation-detail', component },
      { path: '/cost/project-cost-adjustments', name: 'cost-project-cost-adjustments', component },
      { path: '/cost/project-cost-adjustments/create', name: 'cost-project-cost-adjustment-create', component },
      { path: '/cost/project-cost-adjustments/:id', name: 'cost-project-cost-adjustment-detail', component },
      { path: '/cost/project-cost-adjustments/:id/edit', name: 'cost-project-cost-adjustment-edit', component },
      { path: '/cost/project-cost-variances', name: 'cost-project-cost-variances', component },
      { path: '/inventory/documents/:id', name: 'inventory-document-detail', component: { render: () => null } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(component, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('029 项目成本页面族', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    projectCostApiMock.projectCosts.list.mockResolvedValue(page([workbenchRecord, restrictedWorkbenchRecord], 1, 10))
    projectCostApiMock.projectCosts.getProject.mockResolvedValue(projectDetail)
    projectCostApiMock.projectCosts.createCalculation.mockResolvedValue(calculationDetail)
    projectCostApiMock.calculations.get.mockResolvedValue(calculationDetail)
    projectCostApiMock.calculations.sources.mockResolvedValue(page([sourceRecord, restrictedSourceRecord], 1, 10))
    projectCostApiMock.calculations.entries.mockResolvedValue(page([{
      id: 601,
      calculationId: 91,
      category: 'MATERIAL',
      stage: 'WIP',
      direction: 'DEBIT',
      amount: '168.000000',
      description: '材料净额',
      sourceCount: 4,
    }], 1, 10))
    projectCostApiMock.calculations.variances.mockResolvedValue(page([varianceRecord], 1, 10))
    projectCostApiMock.calculations.recalculate.mockResolvedValue({ ...calculationDetail, version: 8 })
    projectCostApiMock.calculations.confirm.mockResolvedValue({ ...calculationDetail, status: 'CONFIRMED', allowedActions: [] })
    projectCostApiMock.calculations.cancel.mockResolvedValue({ ...calculationDetail, status: 'CANCELLED', allowedActions: [] })
    projectCostApiMock.adjustments.list.mockResolvedValue(page([adjustmentRecord], 1, 10))
    projectCostApiMock.adjustments.get.mockResolvedValue(adjustmentRecord)
    projectCostApiMock.adjustments.create.mockResolvedValue(adjustmentRecord)
    projectCostApiMock.adjustments.update.mockResolvedValue(adjustmentRecord)
    projectCostApiMock.adjustments.submit.mockResolvedValue({ ...adjustmentRecord, status: 'SUBMITTED', allowedActions: [] })
    projectCostApiMock.adjustments.cancel.mockResolvedValue({ ...adjustmentRecord, status: 'CANCELLED', allowedActions: [] })
    projectCostApiMock.adjustments.publicExpenseCandidates.mockResolvedValue(page([publicExpenseCandidate], 1, 10))
  })

  it('工作台复用统一页面壳层、查询分页、宽表和权限金额态', async () => {
    const { wrapper, router } = await mountCostView(ProjectCostWorkbenchView, '/cost/project-costs')

    expect(projectCostApiMock.projectCosts.list).toHaveBeenCalledWith(expect.objectContaining({
      keyword: '',
      page: 1,
      pageSize: 10,
    }))
    expect(wrapper.text()).toContain('项目成本核算')
    expect(wrapper.text()).toContain('当前项目成本')
    expect(wrapper.text()).toContain('在制成本')
    expect(wrapper.text()).toContain('发货口径经营毛利')
    expect(wrapper.text()).toContain('838.00')
    expect(wrapper.text()).toContain('9162.00')
    expect(wrapper.text()).toContain('无权查看成本金额')
    expect(wrapper.text()).toContain('毛利不完整：存在暂估、未定价、在制或阻断差异')
    expect(wrapper.text()).toContain('来源已变化，请重算后再确认。')
    expect(wrapper.find('.module-page').exists()).toBe(true)
    expect(wrapper.find('.query-form').exists()).toBe(true)
    expect(wrapper.find('.table-scroll').exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'ElPagination' }).props('pageSizes')).toEqual([10, 20, 50, 100])
    expect(wrapper.findAllComponents({ name: 'ElTableColumn' })).toHaveLength(14)

    await wrapper.find('input[name="project-cost-keyword"]').setValue('华东')
    await wrapper.find('input[name="project-cost-owner-user-id"]').setValue('7')
    await wrapper.find('[data-test="search-project-costs"]').trigger('click')
    await flushPromises()
    expect(projectCostApiMock.projectCosts.list).toHaveBeenLastCalledWith(expect.objectContaining({
      keyword: '华东',
      ownerUserId: '7',
      page: 1,
      pageSize: 10,
    }))

    await wrapper.find('[data-test="calculate-project-cost"]').trigger('click')
    await flushPromises()
    expect(projectCostApiMock.projectCosts.createCalculation).toHaveBeenCalledWith(12, {
      cutoffDate: '2026-07-31',
      idempotencyKey: 'project-cost-key',
    })

    await wrapper.find('[data-test="view-project-cost"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('cost-project-cost-detail')
    expect(router.currentRoute.value.params.projectId).toBe('12')
    expect(router.currentRoute.value.query.returnTo).toBe('/cost/project-costs')
  })

  it('项目详情展示分类、阶段、三种口径和运行跳转，不装载完整核算表', async () => {
    const { wrapper, router } = await mountCostView(ProjectCostProjectDetailView, '/cost/project-costs/12')

    expect(projectCostApiMock.projectCosts.getProject).toHaveBeenCalledWith('12')
    expect(wrapper.text()).toContain('项目成本详情')
    expect(wrapper.text()).toContain('材料')
    expect(wrapper.text()).toContain('人工')
    expect(wrapper.text()).toContain('外协')
    expect(wrapper.text()).toContain('制造费用')
    expect(wrapper.text()).toContain('项目费用')
    expect(wrapper.text()).toContain('在制')
    expect(wrapper.text()).toContain('已交付')
    expect(wrapper.text()).toContain('发货经营口径')
    expect(wrapper.text()).toContain('开票辅助口径')
    expect(wrapper.text()).toContain('目标辅助口径')
    expect(wrapper.text()).not.toContain('来源明细表')

    await wrapper.find('[data-test="trace-project-cost-category"]').trigger('click')
    await flushPromises()
    expect(projectCostApiMock.calculations.sources).toHaveBeenLastCalledWith(91, expect.objectContaining({
      category: 'MATERIAL',
      page: 1,
      pageSize: 10,
    }))

    await wrapper.find('[data-test="view-cost-calculation"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('cost-project-cost-calculation-detail')
    expect(router.currentRoute.value.params.id).toBe('91')
    expect(router.currentRoute.value.query.returnTo).toBe('/cost/project-costs/12')
  })

  it('项目详情在后端明细数组缺失时保持可访问并显示空态', async () => {
    projectCostApiMock.projectCosts.getProject.mockResolvedValueOnce({
      ...projectDetail,
      categorySummaries: undefined,
      stageSummaries: undefined,
      calculations: undefined,
      auditSummary: undefined,
    })
    const { wrapper } = await mountCostView(ProjectCostProjectDetailView, '/cost/project-costs/12')

    expect(wrapper.text()).toContain('项目成本详情')
    expect(wrapper.text()).toContain('暂无分类成本')
    expect(wrapper.text()).toContain('暂无阶段成本')
    expect(wrapper.text()).toContain('暂无核算运行')
    expect(wrapper.text()).toContain('暂无审计记录')
  })

  it('运行详情分页加载来源、分录和差异，来源抽屉显示受限态并按动作提交指纹', async () => {
    const { wrapper } = await mountCostView(ProjectCostCalculationDetailView, '/cost/project-cost-calculations/91')

    expect(projectCostApiMock.calculations.get).toHaveBeenCalledWith('91')
    expect(projectCostApiMock.calculations.sources).toHaveBeenCalledWith('91', expect.objectContaining({ page: 1, pageSize: 10 }))
    expect(projectCostApiMock.calculations.entries).toHaveBeenCalledWith('91', expect.objectContaining({ page: 1, pageSize: 10 }))
    expect(projectCostApiMock.calculations.variances).toHaveBeenCalledWith('91', expect.objectContaining({ page: 1, pageSize: 10 }))
    expect(wrapper.text()).toContain('核算运行详情')
    expect(wrapper.text()).toContain('来源指纹')
    expect(wrapper.text()).toContain('fp-029')
    expect(wrapper.text()).toContain('材料净额')

    await wrapper.find('[data-test="open-source-trace"]').trigger('click')
    await flushPromises()
    expect(wrapper.findComponent({ name: 'ElDrawer' }).props('size')).toBe('min(880px, 94vw)')
    expect(projectCostApiMock.calculations.sources).toHaveBeenCalledTimes(2)
    expect(projectCostApiMock.calculations.sources).toHaveBeenLastCalledWith('91', expect.objectContaining({ page: 1, pageSize: 10 }))
    expect(wrapper.findAllComponents({ name: 'ElPagination' }).length).toBeGreaterThanOrEqual(4)
    expect(wrapper.text()).toContain('来源权限受限，仅显示脱敏摘要')
    expect(wrapper.text()).toContain('无权查看成本金额')

    await wrapper.find('[data-test="confirm-project-cost-calculation"]').trigger('click')
    await flushPromises()
    expect(confirmActionMock).toHaveBeenCalledWith(
      '确认核算运行“PCC-202607-001”？确认后将形成项目成本历史快照。',
      expect.objectContaining({ title: '确认项目成本', type: 'success', risk: 'project-cost-confirm' }),
    )
    expect(projectCostApiMock.calculations.confirm).toHaveBeenCalledWith(91, {
      version: 7,
      sourceFingerprint: 'fp-029',
      idempotencyKey: 'project-cost-key',
    })
  })

  it('调整表单使用独立公共费用候选池并以字符串金额创建分配', async () => {
    const { wrapper, router } = await mountCostView(ProjectCostAdjustmentFormView, '/cost/project-cost-adjustments/create')

    expect(projectCostApiMock.adjustments.publicExpenseCandidates).toHaveBeenCalledWith(expect.objectContaining({
      page: 1,
      pageSize: 10,
    }))
    expect(wrapper.text()).toContain('新增成本调整/分配')
    expect(wrapper.text()).toContain('项目候选')
    expect(wrapper.text()).toContain('公共费用候选')
    expect(wrapper.text()).toContain('剩余可分配金额')
    expect(wrapper.text()).toContain('50.00')
    expect(wrapper.find('.table-scroll').exists()).toBe(true)
    expect(projectCostApiMock.projectCosts.list).toHaveBeenCalledWith(expect.objectContaining({
      page: 1,
      pageSize: 10,
    }))

    await wrapper.find('[data-test="select-public-expense-candidate"]').trigger('click')
    await wrapper.find('input[name="project-cost-adjustment-project-id"]').setValue('12')
    await wrapper.find('input[name="project-cost-adjustment-amount"]').setValue('50.000000')
    await wrapper.find('[data-test="save-project-cost-adjustment"]').trigger('click')
    await flushPromises()

    expect(projectCostApiMock.adjustments.create).toHaveBeenCalledWith(expect.objectContaining({
      adjustmentType: 'PUBLIC_EXPENSE_ALLOCATION',
      idempotencyKey: 'project-cost-key',
      sourceFingerprint: 'candidate-801-50.000000',
      lines: [expect.objectContaining({
        amount: '50.000000',
        costCategory: 'MANUFACTURING_OVERHEAD',
        costStage: 'DIRECT_PROJECT',
        publicExpenseLineId: 801,
        reason: '公共费用分配',
      })],
    }))
    expect(router.currentRoute.value.name).toBe('cost-project-cost-adjustment-detail')
    expect(router.currentRoute.value.params.id).toBe('301')
  })

  it('调整表单对受限调整行显示中文权限态且不提交零金额', async () => {
    projectCostApiMock.adjustments.get.mockResolvedValueOnce(restrictedAdjustmentRecord)
    projectCostApiMock.adjustments.publicExpenseCandidates.mockResolvedValueOnce(page([], 1, 10))
    const { wrapper } = await mountCostView(ProjectCostAdjustmentFormView, '/cost/project-cost-adjustments/301/edit')

    expect(wrapper.text()).toContain('无权查看成本金额')
    expect(wrapper.text()).not.toContain('0.00')

    await wrapper.find('input[name="project-cost-adjustment-amount"]').setValue('0.000000')
    await wrapper.find('[data-test="save-project-cost-adjustment"]').trigger('click')
    await flushPromises()

    expect(projectCostApiMock.adjustments.update).not.toHaveBeenCalled()
  })

  it('调整表单禁止选择受限公共费用候选并阻止构造公共费用行载荷', async () => {
    projectCostApiMock.adjustments.publicExpenseCandidates.mockResolvedValueOnce(page([restrictedPublicExpenseCandidate], 1, 10))
    const { wrapper } = await mountCostView(ProjectCostAdjustmentFormView, '/cost/project-cost-adjustments/create')

    expect(wrapper.text()).toContain('来源权限受限，仅显示脱敏摘要')
    expect(wrapper.text()).not.toContain('0.00')

    await wrapper.find('[data-test="select-public-expense-candidate"]').trigger('click')
    await wrapper.find('input[name="project-cost-adjustment-project-id"]').setValue('12')
    await wrapper.find('input[name="project-cost-adjustment-amount"]').setValue('10.000000')
    await wrapper.find('[data-test="save-project-cost-adjustment"]').trigger('click')
    await flushPromises()

    expect(projectCostApiMock.adjustments.create).not.toHaveBeenCalled()
  })

  it('调整列表和详情按 allowedActions 展示状态动作并保留金额字符串', async () => {
    const list = await mountCostView(ProjectCostAdjustmentListView, '/cost/project-cost-adjustments')

    expect(projectCostApiMock.adjustments.list).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 10 }))
    expect(list.wrapper.text()).toContain('成本调整/分配')
    expect(list.wrapper.text()).toContain('PCA-202607-001')
    expect(list.wrapper.text()).toContain('50.00')

    await list.wrapper.find('[data-test="submit-project-cost-adjustment"]').trigger('click')
    await flushPromises()
    expect(confirmActionMock).toHaveBeenCalledWith(
      '提交成本调整/分配“PCA-202607-001”？',
      expect.objectContaining({ title: '提交成本调整', type: 'warning', risk: 'project-cost-adjustment-submit' }),
    )
    expect(projectCostApiMock.adjustments.submit).toHaveBeenCalledWith(301, {
      version: 3,
      sourceFingerprint: 'adjustment-301-v3',
      idempotencyKey: 'project-cost-key',
    })

    const detail = await mountCostView(ProjectCostAdjustmentDetailView, '/cost/project-cost-adjustments/301')
    expect(detail.wrapper.text()).toContain('成本调整/分配详情')
    expect(detail.wrapper.text()).toContain('公共制造费用分配')
    expect(detail.wrapper.text()).toContain('制造费用')
    expect(detail.wrapper.text()).toContain('直接项目')
    expect(detail.wrapper.text()).toContain('801')
    expect(detail.wrapper.text()).toContain('审批状态')
    expect(detail.wrapper.text()).toContain('草稿')
  })

  it('差异清单展示严重级别、来源、差额和受限态，不把空金额显示为 0', async () => {
    projectCostApiMock.calculations.variances.mockResolvedValueOnce(page([
      varianceRecord,
      {
        ...varianceRecord,
        id: 702,
        sourceNo: null,
        sourceVisible: false,
        amountVisible: false,
        expectedAmount: null,
        actualAmount: null,
        differenceAmount: null,
        restrictedReason: '来源权限受限，仅显示脱敏摘要',
      },
    ], 1, 10))
    const { wrapper } = await mountCostView(ProjectCostVarianceListView, '/cost/project-cost-variances')

    expect(projectCostApiMock.calculations.variances).toHaveBeenCalledWith(undefined, expect.objectContaining({
      page: 1,
      pageSize: 10,
    }))
    expect(wrapper.text()).toContain('项目成本差异')
    expect(wrapper.text()).toContain('阻断')
    expect(wrapper.text()).toContain('外协暂估差异')
    expect(wrapper.text()).toContain('20.00')
    expect(wrapper.text()).toContain('来源权限受限，仅显示脱敏摘要')
    expect(wrapper.text()).not.toContain('差额 0')

    await wrapper.find('input[name="project-cost-variance-project-id"]').setValue('12')
    await wrapper.find('[data-test="search-project-cost-variances"]').trigger('click')
    await flushPromises()
    expect(projectCostApiMock.calculations.variances).toHaveBeenLastCalledWith(undefined, expect.objectContaining({
      projectId: '12',
      page: 1,
      pageSize: 10,
    }))
  })
})
