import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError, type PageResult } from './accountPermissionApi'
import {
  createProjectCostApi,
  type ProjectCostActionPayload,
  type ProjectCostAdjustmentPayload,
  type ProjectCostAdjustmentRecord,
  type ProjectCostCalculationDetail,
  type ProjectCostEntryRecord,
  type ProjectCostProjectDetail,
  type ProjectCostPublicExpenseCandidate,
  type ProjectCostSourceRecord,
  type ProjectCostVarianceRecord,
  type ProjectCostWorkbenchRecord,
} from './projectCostApi'

type AssertTrue<T extends true> = T

function apiResponse<T>(data: T, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => ({
      success: true,
      code: 'OK',
      message: '成功',
      data,
      traceId: 'trace-cost',
    }),
  } as Response
}

function apiFailure(status = 409) {
  return {
    ok: false,
    status,
    json: async () => ({
      success: false,
      code: 'PROJECT_COST_SOURCE_CHANGED',
      message: '来源已变化，请重算后再确认。',
      data: null,
      traceId: 'trace-conflict',
    }),
  } as Response
}

describe('项目成本核算 API', () => {
  const typeContract = {
    workbenchDecimalsStayStrings: true as AssertTrue<
      ProjectCostWorkbenchRecord['totalCost'] extends string | null
        ? ProjectCostWorkbenchRecord['shipmentGrossMargin'] extends string | null
          ? ProjectCostWorkbenchRecord['shipmentGrossMarginRate'] extends string | null
            ? true
            : false
          : false
        : false
    >,
    detailsExposePermissionState: true as AssertTrue<
      ProjectCostProjectDetail extends { amountVisible: boolean; sourceVisible: boolean; restrictedReason?: string | null }
        ? ProjectCostCalculationDetail extends { version: number; sourceFingerprint: string; allowedActions: string[]; actionDisabledReasons: Record<string, string> }
          ? true
          : false
        : false
    >,
    sourceAndEntryDecimalsStayStrings: true as AssertTrue<
      ProjectCostSourceRecord['sourceAmount'] extends string | null
        ? ProjectCostEntryRecord['amount'] extends string | null
          ? true
          : false
        : false
    >,
    varianceDecimalsStayStrings: true as AssertTrue<
      ProjectCostVarianceRecord['expectedAmount'] extends string | null
        ? ProjectCostVarianceRecord['actualAmount'] extends string | null
          ? ProjectCostVarianceRecord['differenceAmount'] extends string | null
            ? true
            : false
          : false
        : false
    >,
    adjustmentPayloadUsesDecimalStrings: true as AssertTrue<
      ProjectCostAdjustmentPayload['lines'][number]['amount'] extends string ? true : false
    >,
    publicExpenseCandidateUsesDecimalStrings: true as AssertTrue<
      ProjectCostPublicExpenseCandidate['availableAmount'] extends string | null
        ? ProjectCostPublicExpenseCandidate['taxExcludedAmount'] extends string | null
          ? true
          : false
        : false
    >,
    actionsCarryVersionFingerprintAndIdempotencyKey: true as AssertTrue<
      ProjectCostActionPayload extends { version: number; sourceFingerprint?: string; idempotencyKey: string } ? true : false
    >,
  }

  it('声明 029 前端契约：十进制字符串、权限态、来源指纹和动作载荷', () => {
    expect(typeContract).toMatchObject({
      actionsCarryVersionFingerprintAndIdempotencyKey: true,
      adjustmentPayloadUsesDecimalStrings: true,
      detailsExposePermissionState: true,
      publicExpenseCandidateUsesDecimalStrings: true,
      sourceAndEntryDecimalsStayStrings: true,
      varianceDecimalsStayStrings: true,
      workbenchDecimalsStayStrings: true,
    })
  })

  it('按筛选条件分页获取项目成本工作台且不转换金额字符串', async () => {
    const page: PageResult<ProjectCostWorkbenchRecord> = {
      items: [{
        projectId: 12,
        projectNo: 'SP-001',
        projectName: '华东扩产项目',
        customerName: '华东客户',
        ownerDisplayName: '负责人',
        projectStatus: 'ACTIVE',
        calculationId: 90,
        calculationNo: 'PCC-001',
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
        openVarianceCount: 1,
        blockingVarianceCount: 1,
        provisionalSourceCount: 1,
        unpricedSourceCount: 1,
        amountVisible: true,
        sourceVisible: true,
        restrictedReason: null,
        allowedActions: ['RECALCULATE', 'CONFIRM'],
        actionDisabledReasons: { CONFIRM: '来源已变化，请重算后再确认。' },
        version: 7,
        sourceFingerprint: 'fp-029',
      }],
      total: 1,
      page: 1,
      pageSize: 10,
    }
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse(page))
    const api = createProjectCostApi({ baseUrl: '/erp', fetcher })

    await expect(api.projectCosts.list({
      keyword: '华东',
      ownerUserId: 7,
      projectStatus: 'ACTIVE',
      freshnessStatus: 'STALE',
      varianceStatus: 'OPEN',
      completenessStatus: 'INCOMPLETE',
      cutoffDateFrom: '2026-07-01',
      cutoffDateTo: '2026-07-31',
      page: 1,
      pageSize: 10,
    })).resolves.toMatchObject({
      items: [{ totalCost: '838.000000', shipmentGrossMargin: '9162.000000' }],
    })

    expect(fetcher).toHaveBeenCalledWith(
      '/erp/api/admin/cost/project-costs?keyword=%E5%8D%8E%E4%B8%9C&ownerUserId=7&projectStatus=ACTIVE&freshnessStatus=STALE&varianceStatus=OPEN&completenessStatus=INCOMPLETE&cutoffDateFrom=2026-07-01&cutoffDateTo=2026-07-31&page=1&pageSize=10',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
  })

  it('显式适配真实后端 DTO 字段并保护缺失明细数组', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(apiResponse({
        items: [{
          projectId: 12,
          projectNo: 'SP-001',
          projectName: '华东扩产项目',
          projectStatus: 'ACTIVE',
          status: 'CALCULATED',
          isCurrent: false,
          marginCompleteness: 'INCOMPLETE',
          projectCostTotal: '838.000000',
          wipCost: '12.000000',
          deliveredCost: '826.000000',
          shipmentRevenue: '10000.000000',
          shipmentGrossMargin: '9162.000000',
          shipmentGrossMarginRate: '0.916200',
          amountVisible: true,
          sourceVisible: true,
          version: 7,
          allowedActions: ['RECALCULATE'],
          actionDisabledReasons: {},
        }],
        total: 1,
        page: 1,
        pageSize: 10,
      }))
      .mockResolvedValueOnce(apiResponse({
        projectId: 12,
        projectNo: 'SP-001',
        projectName: '华东扩产项目',
        projectStatus: 'ACTIVE',
        status: 'CALCULATED',
        isCurrent: true,
        marginCompleteness: 'COMPLETE',
        projectCostTotal: '900.000000',
        shipmentRevenue: '10000.000000',
        amountVisible: true,
        sourceVisible: true,
        version: 8,
        allowedActions: [],
        actionDisabledReasons: {},
      }))
      .mockResolvedValueOnce(apiResponse({
        items: [{
          id: 501,
          calculationId: 91,
          projectId: 12,
          costCategory: 'MATERIAL',
          costStage: 'WIP',
          sourceStatus: 'UNPRICED',
          sourceType: 'INVENTORY_ISSUE',
          sourceRestricted: true,
          sourceSummary: '已纳入一条受限来源',
          quantity: '80.000000',
          unitCost: '2.100000',
          calculatedAmount: '168.000000',
          amountVisible: false,
          restrictedReason: '来源权限受限，仅显示脱敏摘要',
        }],
        total: 1,
        page: 1,
        pageSize: 10,
      }))
      .mockResolvedValueOnce(apiResponse({
        items: [{
          expenseLineId: 801,
          expenseNo: 'EXP-001',
          taxExcludedAmount: '200.000000',
          allocatedAmount: '150.000000',
          availableAmount: '50.000000',
          amountVisible: true,
          sourceVisible: true,
        }],
        total: 1,
        page: 1,
        pageSize: 10,
      }))
    const api = createProjectCostApi({ fetcher })

    await expect(api.projectCosts.list({ page: 1, pageSize: 10 })).resolves.toMatchObject({
      items: [{
        calculationStatus: 'CALCULATED',
        freshnessStatus: 'STALE',
        completenessStatus: 'INCOMPLETE',
        totalCost: '838.000000',
        shipmentPretaxRevenue: '10000.000000',
      }],
    })
    await expect(api.projectCosts.getProject(12)).resolves.toMatchObject({
      categorySummaries: [],
      stageSummaries: [],
      calculations: [],
      auditSummary: [],
      totalCost: '900.000000',
      shipmentPretaxRevenue: '10000.000000',
    })
    await expect(api.calculations.sources(91, { page: 1, pageSize: 10 })).resolves.toMatchObject({
      items: [{
        category: 'MATERIAL',
        stage: 'WIP',
        unitPrice: '2.100000',
        sourceAmount: '168.000000',
        sourceVisible: false,
      }],
    })
    await expect(api.adjustments.publicExpenseCandidates({ page: 1, pageSize: 10 })).resolves.toMatchObject({
      items: [{
        taxExcludedAmount: '200.000000',
        allocatedAmount: '150.000000',
        availableAmount: '50.000000',
      }],
    })
  })

  it('创建、重算、确认和取消核算运行按真实路径提交版本、来源指纹和幂等键', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValue(apiResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-create', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 91, calculationNo: 'PCC-001' }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-recalc', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 91, version: 8 }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-confirm', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 91, status: 'CONFIRMED' }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-cancel', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 91, status: 'CANCELLED' }))
    const api = createProjectCostApi({ fetcher })

    await api.projectCosts.createCalculation(12, { cutoffDate: '2026-07-31', idempotencyKey: 'create-key' })
    await api.calculations.recalculate(91, { version: 7, sourceFingerprint: 'fp-029', idempotencyKey: 'recalc-key' })
    await api.calculations.confirm(91, { version: 8, sourceFingerprint: 'fp-029-new', idempotencyKey: 'confirm-key' })
    await api.calculations.cancel(91, { version: 8, sourceFingerprint: 'fp-029-new', idempotencyKey: 'cancel-key' })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/cost/project-costs/projects/12/calculations', expect.objectContaining({
      body: JSON.stringify({ cutoffDate: '2026-07-31', idempotencyKey: 'create-key' }),
      method: 'POST',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/cost/project-cost-calculations/91/recalculate', expect.objectContaining({
      body: JSON.stringify({ version: 7, sourceFingerprint: 'fp-029', idempotencyKey: 'recalc-key' }),
      method: 'PUT',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/cost/project-cost-calculations/91/confirm', expect.objectContaining({
      body: JSON.stringify({ version: 8, sourceFingerprint: 'fp-029-new', idempotencyKey: 'confirm-key' }),
      method: 'PUT',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(8, '/api/admin/cost/project-cost-calculations/91/cancel', expect.objectContaining({
      body: JSON.stringify({ version: 8, sourceFingerprint: 'fp-029-new', idempotencyKey: 'cancel-key' }),
      method: 'PUT',
    }))
  })

  it('分页查询详情、来源、分录、差异、调整和公共费用候选', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(apiResponse({ projectId: 12 }))
      .mockResolvedValueOnce(apiResponse({ id: 91 }))
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 2, pageSize: 20 }))
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 10 }))
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 10 }))
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 10 }))
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 10 }))
      .mockResolvedValueOnce(apiResponse({ id: 301 }))
    const api = createProjectCostApi({ fetcher })

    await api.projectCosts.getProject(12)
    await api.calculations.get(91)
    await api.calculations.sources(91, { category: 'MATERIAL', stage: 'WIP', sourceStatus: 'PROVISIONAL', sourceType: 'INVENTORY_ISSUE', sourceRestricted: true, page: 2, pageSize: 20 })
    await api.calculations.entries(91, { category: 'MATERIAL', stage: 'FINISHED', page: 1, pageSize: 10 })
    await api.calculations.variances(91, { severity: 'BLOCKING', status: 'OPEN', projectId: 12, businessDateFrom: '2026-07-01', businessDateTo: '2026-07-31', sourceRestricted: false, page: 1, pageSize: 10 })
    await api.adjustments.list({ keyword: 'ALLOC', status: 'DRAFT', projectId: 12, page: 1, pageSize: 10 })
    await api.adjustments.publicExpenseCandidates({ keyword: '费用', supplierId: 88, businessDateFrom: '2026-07-01', businessDateTo: '2026-07-31', page: 1, pageSize: 10 })
    await api.adjustments.get(301)

    expect(fetcher.mock.calls.map((call) => call[0])).toEqual([
      '/api/admin/cost/project-costs/projects/12',
      '/api/admin/cost/project-cost-calculations/91',
      '/api/admin/cost/project-cost-calculations/91/sources?category=MATERIAL&stage=WIP&sourceStatus=PROVISIONAL&sourceType=INVENTORY_ISSUE&sourceRestricted=true&page=2&pageSize=20',
      '/api/admin/cost/project-cost-calculations/91/entries?category=MATERIAL&stage=FINISHED&page=1&pageSize=10',
      '/api/admin/cost/project-cost-calculations/91/variances?projectId=12&severity=BLOCKING&status=OPEN&businessDateFrom=2026-07-01&businessDateTo=2026-07-31&sourceRestricted=false&page=1&pageSize=10',
      '/api/admin/cost/project-cost-adjustments?keyword=ALLOC&status=DRAFT&projectId=12&page=1&pageSize=10',
      '/api/admin/cost/project-cost-adjustments/candidates/public-expenses?keyword=%E8%B4%B9%E7%94%A8&supplierId=88&businessDateFrom=2026-07-01&businessDateTo=2026-07-31&page=1&pageSize=10',
      '/api/admin/cost/project-cost-adjustments/301',
    ])
  })

  it('调整创建、更新、提交和取消保留金额字符串并抛出 409 来源变化错误', async () => {
    const adjustment: ProjectCostAdjustmentRecord = {
      id: 301,
      adjustmentNo: 'PCA-001',
      adjustmentType: 'PUBLIC_EXPENSE_ALLOCATION',
      status: 'DRAFT',
      businessDate: '2026-07-31',
      amountVisible: true,
      sourceVisible: true,
      restrictedReason: null,
      totalAmount: '50.000000',
      version: 3,
      allowedActions: ['UPDATE', 'SUBMIT', 'CANCEL'],
      actionDisabledReasons: {},
      lines: [],
    }
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-create', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse(adjustment))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-update', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse(adjustment))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-submit', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ ...adjustment, status: 'SUBMITTED' }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-cancel', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiFailure(409))
    const api = createProjectCostApi({ fetcher })
    const payload: ProjectCostAdjustmentPayload = {
      adjustmentType: 'PUBLIC_EXPENSE_ALLOCATION',
      businessDate: '2026-07-31',
      reason: '公共制造费用分配',
      version: 3,
      sourceFingerprint: 'fp-adjustment',
      idempotencyKey: 'adjustment-key',
      lines: [{
        projectId: 12,
        costCategory: 'MANUFACTURING_OVERHEAD',
        costStage: 'DIRECT_PROJECT',
        direction: 'INCREASE',
        amount: '50.000000',
        publicExpenseLineId: 8001,
        reason: '公共费用分配',
      }],
    }

    await api.adjustments.create(payload)
    await api.adjustments.update(301, payload)
    await api.adjustments.submit(301, { version: 3, sourceFingerprint: 'fp-adjustment', idempotencyKey: 'submit-key' })
    const conflictRequest = api.adjustments.cancel(301, { version: 4, sourceFingerprint: 'fp-new', idempotencyKey: 'cancel-key' })
    await expect(conflictRequest)
      .rejects.toMatchObject({
        code: 'PROJECT_COST_SOURCE_CHANGED',
        status: 409,
        message: '来源已变化，请重算后再确认。',
      })
    await expect(conflictRequest).rejects.toBeInstanceOf(AccountPermissionApiError)

    expect(JSON.parse(fetcher.mock.calls[1][1].body as string).lines[0]).toMatchObject({
      amount: '50.000000',
      costCategory: 'MANUFACTURING_OVERHEAD',
      costStage: 'DIRECT_PROJECT',
      publicExpenseLineId: 8001,
      reason: '公共费用分配',
    })
    expect(JSON.parse(fetcher.mock.calls[1][1].body as string).lines[0]).not.toHaveProperty('sourceExpenseLineId')
    expect(JSON.parse(fetcher.mock.calls[3][1].body as string).lines[0].amount).toBe('50.000000')
  })
})
