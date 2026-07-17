import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError, type PageResult } from './accountPermissionApi'
import {
  createProjectProductionApi,
  type ProjectProductionApi,
  type ProjectProductionCompletionReceiptPayload,
  type ProjectProductionMaterialIssuePayload,
  type ProjectProductionWorkOrderDetailRecord,
  type ProjectProductionWorkOrderListParams,
  type ProjectProductionWorkOrderSummaryRecord,
  type ProductionOwnershipType,
  type VersionedIdempotentPayload,
} from './projectProductionApi'

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
      traceId: 'trace-027-production',
    }),
  }
}

function apiFailure(status = 409) {
  return {
    ok: false,
    status,
    json: async () => ({
      success: false,
      code: 'PRODUCTION_PROJECT_MISMATCH',
      message: '不能消耗其他项目库存',
      data: null,
      traceId: 'trace-027-production-conflict',
    }),
  }
}

describe('027 项目生产 API', () => {
  const typeContract = {
    ownershipIsExplicit: true as AssertTrue<ProductionOwnershipType extends 'PUBLIC' | 'PROJECT' ? true : false>,
    listAcceptsProjectAndSourceFilters: true as AssertTrue<
      ProjectProductionWorkOrderListParams extends {
        projectId?: unknown
        ownershipType?: unknown
        sourceMrpSuggestionId?: unknown
      } ? true : false
    >,
    workOrderCarriesSourceAllowedActionsAndVersion: true as AssertTrue<
      ProjectProductionWorkOrderSummaryRecord extends {
        ownershipType: unknown
        projectId?: unknown
        sourceMrpSuggestionId?: unknown
        allowedActions?: string[]
        actionDisabledReason?: string | null
        version: number
      } ? true : false
    >,
    detailKeepsExecutionTrace: true as AssertTrue<
      ProjectProductionWorkOrderDetailRecord extends {
        sourceSummary?: unknown
        executionSummary?: unknown
        traceLinks?: unknown
      } ? true : false
    >,
    issueLinesUseStringQuantitiesAndOwnershipSource: true as AssertTrue<
      ProjectProductionMaterialIssuePayload['lines'][number] extends {
        quantity: string
        ownershipType: ProductionOwnershipType
        projectId?: unknown
        costLayerId?: unknown
      } ? true : false
    >,
    completionUsesBackendProvisionalHint: true as AssertTrue<
      ProjectProductionCompletionReceiptPayload extends {
        quantity: string
        provisionalUnitCost?: string
      } ? true : false
    >,
    writeActionsCarryVersionAndIdempotency: true as AssertTrue<
      VersionedIdempotentPayload extends { version: number; idempotencyKey: string } ? true : false
    >,
  }

  it('声明项目工单、执行、归属、来源、allowedActions 和十进制字符串契约', () => {
    expect(typeContract).toMatchObject({
      completionUsesBackendProvisionalHint: true,
      detailKeepsExecutionTrace: true,
      issueLinesUseStringQuantitiesAndOwnershipSource: true,
      listAcceptsProjectAndSourceFilters: true,
      ownershipIsExplicit: true,
      workOrderCarriesSourceAllowedActionsAndVersion: true,
      writeActionsCarryVersionAndIdempotency: true,
    })
  })

  it('按项目、公共归属和来源建议筛选工单，不把空值写入查询', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse<PageResult<ProjectProductionWorkOrderSummaryRecord>>({
      items: [],
      total: 0,
      page: 1,
      pageSize: 20,
    }))
    const api = createProjectProductionApi({ fetcher, baseUrl: '/erp' })

    await api.workOrders.list({
      keyword: 'WO-027',
      projectId: 20,
      ownershipType: 'PROJECT',
      sourceMrpSuggestionId: 'SUG-027',
      status: 'RELEASED',
      productMaterialId: undefined,
      dateFrom: '',
      dateTo: null,
      page: 1,
      pageSize: 20,
    })

    expect(fetcher).toHaveBeenCalledWith(
      '/erp/api/admin/production/work-orders?keyword=WO-027&projectId=20&ownershipType=PROJECT&sourceMrpSuggestionId=SUG-027&status=RELEASED&page=1&pageSize=20',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
  })

  it('项目工单和执行写动作携带 version/idempotencyKey，409 不自动重放', async () => {
    const fetcher = vi.fn()
    const tokens = [
      'csrf-release',
      'csrf-issue',
      'csrf-post-issue',
      'csrf-report',
      'csrf-receipt',
      'csrf-post-receipt',
    ]
    tokens.forEach((token) => {
      fetcher.mockResolvedValueOnce(apiResponse({ token, headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      fetcher.mockResolvedValueOnce(apiResponse({ id: token, version: 8 }))
    })
    const api = createProjectProductionApi({ fetcher })

    await api.workOrders.release(27, { version: 3, idempotencyKey: 'release-key' })
    await api.materialIssues.create(27, {
      version: 4,
      businessDate: '2026-07-17',
      reason: '项目领料',
      idempotencyKey: 'issue-key',
      lines: [{
        workOrderMaterialId: 91,
        lineNo: 1,
        warehouseId: 2,
        quantity: '2.500000',
        ownershipType: 'PROJECT',
        projectId: 20,
        costLayerId: 3001,
      }],
    })
    await api.materialIssues.post(27, 701, { version: 5, idempotencyKey: 'post-issue-key' })
    await api.reports.create(27, {
      version: 6,
      businessDate: '2026-07-17',
      qualifiedQuantity: '1.000000',
      defectiveQuantity: '0.000000',
      idempotencyKey: 'report-key',
    })
    await api.completionReceipts.create(27, {
      version: 7,
      businessDate: '2026-07-17',
      receiptWarehouseId: 9,
      quantity: '1.000000',
      provisionalUnitCost: '128.450000',
      idempotencyKey: 'receipt-key',
    })
    await api.completionReceipts.post(27, 801, { version: 8, idempotencyKey: 'post-receipt-key' })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/production/work-orders/27/release', expect.objectContaining({
      body: JSON.stringify({ version: 3, idempotencyKey: 'release-key' }),
      method: 'PUT',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/production/work-orders/27/material-issues', expect.objectContaining({
      body: JSON.stringify({
        version: 4,
        businessDate: '2026-07-17',
        reason: '项目领料',
        idempotencyKey: 'issue-key',
        lines: [{
          workOrderMaterialId: 91,
          lineNo: 1,
          warehouseId: 2,
          quantity: '2.500000',
          ownershipType: 'PROJECT',
          projectId: 20,
          costLayerId: 3001,
        }],
      }),
      method: 'POST',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/production/work-orders/27/material-issues/701/post', expect.objectContaining({
      body: JSON.stringify({ version: 5, idempotencyKey: 'post-issue-key' }),
      method: 'PUT',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(8, '/api/admin/production/work-orders/27/reports', expect.objectContaining({
      body: JSON.stringify({
        version: 6,
        businessDate: '2026-07-17',
        qualifiedQuantity: '1.000000',
        defectiveQuantity: '0.000000',
        idempotencyKey: 'report-key',
      }),
      method: 'POST',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(10, '/api/admin/production/work-orders/27/completion-receipts', expect.objectContaining({
      body: JSON.stringify({
        version: 7,
        businessDate: '2026-07-17',
        receiptWarehouseId: 9,
        quantity: '1.000000',
        provisionalUnitCost: '128.450000',
        idempotencyKey: 'receipt-key',
      }),
      method: 'POST',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(12, '/api/admin/production/work-orders/27/completion-receipts/801/post', expect.objectContaining({
      body: JSON.stringify({ version: 8, idempotencyKey: 'post-receipt-key' }),
      method: 'PUT',
    }))

    const conflictFetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-conflict', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiFailure())
    const conflictApi = createProjectProductionApi({ fetcher: conflictFetcher })
    const request = conflictApi.workOrders.release(27, { version: 3, idempotencyKey: 'release-key' })

    await expect(request).rejects.toMatchObject({
      code: 'PRODUCTION_PROJECT_MISMATCH',
      status: 409,
      traceId: 'trace-027-production-conflict',
    })
    await expect(request).rejects.toBeInstanceOf(AccountPermissionApiError)
    expect(conflictFetcher).toHaveBeenCalledTimes(2)
  })

  it('写动作缺少非空幂等键时拒绝发送', async () => {
    const fetcher = vi.fn()
    const api = createProjectProductionApi({ fetcher }) as ProjectProductionApi

    await expect(api.workOrders.release(27, { version: 1, idempotencyKey: '' })).rejects.toThrow('幂等键不能为空')
    await expect(api.materialIssues.post(27, 701, { version: 1, idempotencyKey: '   ' })).rejects.toThrow('幂等键不能为空')
    expect(fetcher).not.toHaveBeenCalled()
  })
})
