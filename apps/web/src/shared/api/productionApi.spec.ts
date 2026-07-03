import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from './accountPermissionApi'
import {
  createProductionApi,
  type ProductionCompletionReceiptPayload,
  type ProductionMaterialIssuePayload,
  type ProductionWorkOrderPayload,
  type ProductionWorkReportPayload,
} from './productionApi'

function apiResponse<T>(data: T, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => ({
      success: true,
      code: 'OK',
      message: '成功',
      data,
    }),
  }
}

function apiFailure(status = 403) {
  return {
    ok: false,
    status,
    json: async () => ({
      success: false,
      code: 'AUTH_FORBIDDEN',
      message: '无权访问生产工单',
      data: null,
      traceId: 'trace-production',
    }),
  }
}

describe('生产执行 API', () => {
  it('按查询条件分页获取生产工单', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 2, pageSize: 50 }))
    const api = createProductionApi({ baseUrl: '/erp', fetcher })

    await api.workOrders.list({
      keyword: 'WO',
      status: 'RELEASED',
      productMaterialId: 9,
      dateFrom: '2026-07-01',
      dateTo: '2026-07-03',
      page: 2,
      pageSize: 50,
    })

    expect(fetcher).toHaveBeenCalledWith(
      '/erp/api/admin/production/work-orders?keyword=WO&status=RELEASED&productMaterialId=9&dateFrom=2026-07-01&dateTo=2026-07-03&page=2&pageSize=50',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
  })

  it('创建生产工单写接口会先获取 CSRF 且数量 payload 保持字符串', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-create', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 1, workOrderNo: 'WO-1' }))
    const api = createProductionApi({ fetcher })
    const payload: ProductionWorkOrderPayload = {
      productMaterialId: 1,
      bomId: 2,
      plannedQuantity: '999999999999.999999',
      issueWarehouseId: 3,
      receiptWarehouseId: 4,
      plannedStartDate: '2026-07-03',
      plannedFinishDate: '2026-07-10',
      remark: '首批生产',
    }

    await api.workOrders.create(payload)

    expect(JSON.parse(fetcher.mock.calls[1][1].body as string)).toMatchObject({
      plannedQuantity: '999999999999.999999',
    })
    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/auth/csrf', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/production/work-orders', {
      body: JSON.stringify(payload),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-create',
      },
      method: 'POST',
    })
  })

  it('发布、完成和取消生产工单路径正确', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-release', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 7, status: 'RELEASED' }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-complete', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 7, status: 'COMPLETED' }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-cancel', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 8, status: 'CANCELLED' }))
    const api = createProductionApi({ fetcher })

    await api.workOrders.release(7)
    await api.workOrders.complete(7)
    await api.workOrders.cancel(8)

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/production/work-orders/7/release', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-release',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/production/work-orders/7/complete', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-complete',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/production/work-orders/8/cancel', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-cancel',
      },
      method: 'PUT',
    })
  })

  it('领料、报工和完工入库创建与过账路径正确', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-issue-create', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 11, issueNo: 'MI-1' }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-issue-post', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 11, status: 'POSTED' }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-report-create', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 12, reportNo: 'WR-1' }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-report-post', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 12, status: 'POSTED' }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-receipt-create', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 13, receiptNo: 'CR-1' }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-receipt-post', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 13, status: 'POSTED' }))
    const api = createProductionApi({ fetcher })
    const issuePayload: ProductionMaterialIssuePayload = {
      businessDate: '2026-07-03',
      reason: '生产领料',
      lines: [{ workOrderMaterialId: 101, lineNo: 1, warehouseId: 2, quantity: '12.500000' }],
    }
    const reportPayload: ProductionWorkReportPayload = {
      businessDate: '2026-07-03',
      qualifiedQuantity: '10.000000',
      defectiveQuantity: '0.500000',
    }
    const receiptPayload: ProductionCompletionReceiptPayload = {
      businessDate: '2026-07-03',
      receiptWarehouseId: 3,
      quantity: '10.000000',
    }

    await api.materialIssues.create(9, issuePayload)
    await api.materialIssues.post(9, 11)
    await api.reports.create(9, reportPayload)
    await api.reports.post(9, 12)
    await api.completionReceipts.create(9, receiptPayload)
    await api.completionReceipts.post(9, 13)

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/production/work-orders/9/material-issues', {
      body: JSON.stringify(issuePayload),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-issue-create',
      },
      method: 'POST',
    })
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/production/work-orders/9/material-issues/11/post', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-issue-post',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/production/work-orders/9/reports', {
      body: JSON.stringify(reportPayload),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-report-create',
      },
      method: 'POST',
    })
    expect(fetcher).toHaveBeenNthCalledWith(8, '/api/admin/production/work-orders/9/reports/12/post', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-report-post',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(10, '/api/admin/production/work-orders/9/completion-receipts', {
      body: JSON.stringify(receiptPayload),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-receipt-create',
      },
      method: 'POST',
    })
    expect(fetcher).toHaveBeenNthCalledWith(12, '/api/admin/production/work-orders/9/completion-receipts/13/post', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-receipt-post',
      },
      method: 'PUT',
    })
  })

  it('后端返回错误 envelope 时抛出统一 API 错误', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiFailure())
    const api = createProductionApi({ fetcher })
    const request = api.workOrders.list({ page: 1, pageSize: 20 })

    await expect(request).rejects.toMatchObject({
      code: 'AUTH_FORBIDDEN',
      status: 403,
      traceId: 'trace-production',
    })
    await expect(request).rejects.toBeInstanceOf(AccountPermissionApiError)
  })
})
