import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from './accountPermissionApi'
import {
  createMaterialRequirementApi,
  type MaterialRequirementSuggestionConversionRecord,
  type MaterialRequirementRunRecord,
  type MaterialRequirementSuggestionRecord,
} from './materialRequirementApi'
import apiSource from './materialRequirementApi.ts?raw'

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
      traceId: 'trace-026',
    }),
  }
}

function apiFailure(status = 409) {
  return {
    ok: false,
    status,
    json: async () => ({
      success: false,
      code: 'MATERIAL_REQUIREMENT_SOURCE_CHANGED',
      message: '来源已变化，请重新获取快照',
      data: null,
      traceId: 'trace-026-conflict',
    }),
  }
}

describe('026 订单缺料分析 API', () => {
  const materialRequirementTypeContract = {
    runHasShortageCount: true as AssertTrue<
      'shortageMaterialCount' extends keyof MaterialRequirementRunRecord ? true : false
    >,
    suggestionQuantityIsString: true as AssertTrue<
      MaterialRequirementSuggestionRecord extends { suggestedQuantity: string } ? true : false
    >,
    suggestionHasAllowedActionsAndVersion: true as AssertTrue<
      MaterialRequirementSuggestionRecord extends { allowedActions?: string[]; version: number } ? true : false
    >,
    conversionResponseHasTargetRoute: true as AssertTrue<
      MaterialRequirementSuggestionConversionRecord extends {
        suggestionId: unknown
        targetObjectType: unknown
        targetObjectId: unknown
        targetObjectNo: unknown
        targetRoute: string
        version: number
      } ? true : false
    >,
    runUsesStableFailureSummary: true as AssertTrue<
      'failureSummary' extends keyof MaterialRequirementRunRecord ? true : false
    >,
  }

  it('声明运行与建议类型保留状态、十进制字符串、allowedActions 和 version', () => {
    expect(materialRequirementTypeContract).toMatchObject({
      runHasShortageCount: true,
      suggestionQuantityIsString: true,
      suggestionHasAllowedActionsAndVersion: true,
      conversionResponseHasTargetRoute: true,
      runUsesStableFailureSummary: true,
    })
    expect(apiSource).toContain('failureSummary')
    expect(apiSource).not.toContain('failureMessage')
  })

  it('按冻结路径分页查询运行、明细和建议，并过滤空查询值', async () => {
    const fetcher = vi.fn().mockResolvedValue(apiResponse({ items: [], total: 0, page: 1, pageSize: 10 }))
    const api = createMaterialRequirementApi({ fetcher })

    await api.runs.list({
      projectId: 20,
      customerId: undefined,
      contractId: null,
      orderId: 88,
      materialId: '',
      requiredDateTo: '2026-08-30',
      status: 'COMPLETED',
      expired: false,
      page: 1,
      pageSize: 10,
    })
    await api.runs.requirements(1001, { materialId: 31, coverageStatus: 'SHORTAGE', page: 2, pageSize: 20 })
    await api.runs.allocations(1001, { requirementLineId: 'R-1', page: 1, pageSize: 50 })
    await api.runs.suggestions(1001, { status: 'OPEN', suggestionType: 'PURCHASE_REQUISITION', page: 1, pageSize: 10 })
    await api.runs.substituteHints(1001, { requirementLineId: 'R-1', page: 1, pageSize: 10 })

    expect(fetcher).toHaveBeenNthCalledWith(
      1,
      '/api/admin/planning/material-requirement-runs?projectId=20&orderId=88&requiredDateTo=2026-08-30&status=COMPLETED&expired=false&page=1&pageSize=10',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      2,
      '/api/admin/planning/material-requirement-runs/1001/requirements?materialId=31&coverageStatus=SHORTAGE&page=2&pageSize=20',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      3,
      '/api/admin/planning/material-requirement-runs/1001/allocations?requirementLineId=R-1&page=1&pageSize=50',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      4,
      '/api/admin/planning/material-requirement-runs/1001/suggestions?status=OPEN&suggestionType=PURCHASE_REQUISITION&page=1&pageSize=10',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      5,
      '/api/admin/planning/material-requirement-runs/1001/substitute-hints?requirementLineId=R-1&page=1&pageSize=10',
      expect.objectContaining({ method: 'GET' }),
    )
  })

  it('运行、重算和建议写动作先取 CSRF，携带 version 与幂等键且不自动重放 409', async () => {
    const fetcher = vi.fn()
    const tokens = [
      'csrf-create-run',
      'csrf-recalculate',
      'csrf-confirm',
      'csrf-dismiss',
      'csrf-convert',
      'csrf-convert-work-order',
      'csrf-convert-outsourcing',
    ]
    tokens.forEach((token) => {
      fetcher.mockResolvedValueOnce(apiResponse({ token, headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      fetcher.mockResolvedValueOnce(apiResponse({ id: token, version: 8 }))
    })
    const api = createMaterialRequirementApi({ fetcher })

    await api.runs.create({
      projectId: 20,
      requiredDateTo: '2026-08-30',
      includePublicDemand: true,
      idempotencyKey: 'run-create-key',
    })
    await api.runs.recalculate(1001, { version: 4, idempotencyKey: 'run-recalculate-key' })
    await api.suggestions.confirm('SUG-1', { version: 2, idempotencyKey: 'suggestion-confirm-key' })
    await api.suggestions.dismiss('SUG-1', {
      version: 3,
      reason: '人工确认不采购',
      idempotencyKey: 'suggestion-dismiss-key',
    })
    await api.suggestions.convertRequisition('SUG-2', { version: 4, idempotencyKey: 'suggestion-convert-key' })
    await api.suggestions.convertWorkOrder('SUG-3', { version: 5, idempotencyKey: 'suggestion-work-order-key' })
    await api.suggestions.convertOutsourcingOrder('SUG-4', {
      version: 6,
      idempotencyKey: 'suggestion-outsourcing-key',
    })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/planning/material-requirement-runs', {
      body: JSON.stringify({
        projectId: 20,
        requiredDateTo: '2026-08-30',
        includePublicDemand: true,
        idempotencyKey: 'run-create-key',
      }),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-create-run',
      },
      method: 'POST',
    })
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/planning/material-requirement-runs/1001/recalculate', expect.objectContaining({
      body: JSON.stringify({ version: 4, idempotencyKey: 'run-recalculate-key' }),
      method: 'POST',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/planning/material-requirement-suggestions/SUG-1/confirm', expect.objectContaining({
      body: JSON.stringify({ version: 2, idempotencyKey: 'suggestion-confirm-key' }),
      method: 'PUT',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(8, '/api/admin/planning/material-requirement-suggestions/SUG-1/dismiss', expect.objectContaining({
      body: JSON.stringify({
        version: 3,
        reason: '人工确认不采购',
        idempotencyKey: 'suggestion-dismiss-key',
      }),
      method: 'PUT',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(10, '/api/admin/planning/material-requirement-suggestions/SUG-2/convert-requisition', expect.objectContaining({
      body: JSON.stringify({ version: 4, idempotencyKey: 'suggestion-convert-key' }),
      method: 'POST',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(12, '/api/admin/planning/material-requirement-suggestions/SUG-3/convert-work-order', expect.objectContaining({
      body: JSON.stringify({ version: 5, idempotencyKey: 'suggestion-work-order-key' }),
      method: 'POST',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(14, '/api/admin/planning/material-requirement-suggestions/SUG-4/convert-outsourcing-order', expect.objectContaining({
      body: JSON.stringify({ version: 6, idempotencyKey: 'suggestion-outsourcing-key' }),
      method: 'POST',
    }))

    const conflictFetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-conflict', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiFailure())
    const conflictApi = createMaterialRequirementApi({ fetcher: conflictFetcher })
    const request = conflictApi.suggestions.confirm('SUG-1', { version: 2, idempotencyKey: 'same-key' })

    await expect(request).rejects.toMatchObject({
      code: 'MATERIAL_REQUIREMENT_SOURCE_CHANGED',
      status: 409,
      traceId: 'trace-026-conflict',
    })
    await expect(request).rejects.toBeInstanceOf(AccountPermissionApiError)
    expect(conflictFetcher).toHaveBeenCalledTimes(2)
  })

  it('写动作缺少非空幂等键时拒绝发送', async () => {
    const fetcher = vi.fn()
    const api = createMaterialRequirementApi({ fetcher }) as any

    await expect(api.runs.create({ projectId: 20 })).rejects.toThrow('幂等键不能为空')
    await expect(api.suggestions.confirm('SUG-1', { version: 2, idempotencyKey: '' })).rejects.toThrow('幂等键不能为空')
    expect(fetcher).not.toHaveBeenCalled()
  })
})
