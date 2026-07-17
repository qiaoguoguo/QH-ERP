import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError, type PageResult } from './accountPermissionApi'
import {
  createProductionOutsourcingApi,
  type OutsourcingDocumentVersionPayload,
  type OutsourcingMaterialIssuePayload,
  type OutsourcingOrderDetailRecord,
  type OutsourcingOrderListParams,
  type OutsourcingOrderPayload,
  type OutsourcingOrderSummaryRecord,
  type OutsourcingReceiptPayload,
  type ProductionOutsourcingApi,
} from './productionOutsourcingApi'
import type { ProductionOwnershipType } from './projectProductionApi'

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
      traceId: 'trace-027-outsourcing',
    }),
  }
}

function apiFailure(status = 409) {
  return {
    ok: false,
    status,
    json: async () => ({
      success: false,
      code: 'PRODUCTION_OUTSOURCING_STATUS_INVALID',
      message: '外协订单状态不允许执行该动作',
      data: null,
      traceId: 'trace-027-outsourcing-conflict',
    }),
  }
}

describe('027 外协执行 API', () => {
  const typeContract = {
    listAcceptsProjectSupplierMaterialAndDateFilters: true as AssertTrue<
      OutsourcingOrderListParams extends {
        projectId?: unknown
        supplierId?: unknown
        productMaterialId?: unknown
        status?: unknown
        plannedDateFrom?: unknown
        plannedDateTo?: unknown
        keyword?: unknown
      } ? true : false
    >,
    orderReturnsProgressAllowedActionsAndVersion: true as AssertTrue<
      OutsourcingOrderSummaryRecord extends {
        orderNo: string
        ownershipType: ProductionOwnershipType
        projectId?: unknown
        supplierId: unknown
        sourceMrpSuggestionId?: unknown
        issuedQuantity: string
        receivedQuantity: string
        plannedIssueDate?: string | null
        plannedReceiptDate?: string | null
        allowedActions?: string[]
        actionDisabledReason?: string | null
        version: number
      } ? true : false
    >,
    detailContainsMaterialsIssuesReceiptsAndTrace: true as AssertTrue<
      OutsourcingOrderDetailRecord extends {
        materials: unknown[]
        materialIssues: unknown[]
        receipts: unknown[]
        traceLinks?: unknown
      } ? true : false
    >,
    receiptUsesStringQuantitiesAndProvisionalCost: true as AssertTrue<
      OutsourcingReceiptPayload['lines'][number] extends {
        acceptedQuantity: string
        rejectedQuantity: string
        provisionalUnitCost?: string
        trackingAllocations?: unknown
      } ? true : false
    >,
    orderPayloadUsesAuthoritativePlannedDates: true as AssertTrue<
      OutsourcingOrderPayload extends {
        plannedIssueDate: string
        plannedReceiptDate: string
      } ? true : false
    >,
    issuePayloadSupportsTrackingAllocations: true as AssertTrue<
      OutsourcingMaterialIssuePayload['lines'][number] extends {
        trackingAllocations?: unknown
      } ? true : false
    >,
    documentActionsCarryVersionAndIdempotency: true as AssertTrue<
      OutsourcingDocumentVersionPayload extends { version: number; idempotencyKey: string } ? true : false
    >,
  }

  it('声明外协订单、发料、收货、项目归属、供应商、进度和 allowedActions 契约', () => {
    expect(typeContract).toMatchObject({
      detailContainsMaterialsIssuesReceiptsAndTrace: true,
      documentActionsCarryVersionAndIdempotency: true,
      issuePayloadSupportsTrackingAllocations: true,
      listAcceptsProjectSupplierMaterialAndDateFilters: true,
      orderPayloadUsesAuthoritativePlannedDates: true,
      orderReturnsProgressAllowedActionsAndVersion: true,
      receiptUsesStringQuantitiesAndProvisionalCost: true,
    })
  })

  it('按外协列表筛选条件访问冻结路径并过滤空值', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse<PageResult<OutsourcingOrderSummaryRecord>>({
      items: [],
      total: 0,
      page: 1,
      pageSize: 10,
    }))
    const api = createProductionOutsourcingApi({ baseUrl: '/erp', fetcher })

    await api.orders.list({
      keyword: 'OS-027',
      projectId: 20,
      supplierId: 30,
      productMaterialId: 40,
      status: 'RELEASED',
      plannedDateFrom: '2026-07-17',
      plannedDateTo: null,
      page: 1,
      pageSize: 10,
    })

    expect(fetcher).toHaveBeenCalledWith(
      '/erp/api/admin/production/outsourcing-orders?keyword=OS-027&projectId=20&supplierId=30&productMaterialId=40&status=RELEASED&plannedDateFrom=2026-07-17&page=1&pageSize=10',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
  })

  it('外协订单、发料和收货写动作携带 version/idempotencyKey，409 不自动重放', async () => {
    const fetcher = vi.fn()
    const tokens = ['csrf-release', 'csrf-issue', 'csrf-post-issue', 'csrf-receipt', 'csrf-post-receipt', 'csrf-close']
    tokens.forEach((token) => {
      fetcher.mockResolvedValueOnce(apiResponse({ token, headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      fetcher.mockResolvedValueOnce(apiResponse({ id: token, version: 8 }))
    })
    const api = createProductionOutsourcingApi({ fetcher })

    await api.orders.release(27, { version: 2, idempotencyKey: 'release-outsourcing' })
    await api.materialIssues.create(27, {
      version: 3,
      businessDate: '2026-07-17',
      warehouseId: 4,
      idempotencyKey: 'outsourcing-issue',
      lines: [{
        orderMaterialId: 5,
        lineNo: 1,
        quantity: '6.000000',
        ownershipType: 'PROJECT',
        projectId: 20,
        costLayerId: 3001,
        trackingAllocations: [{ batchNo: 'B-027', quantity: '6.000000' }],
      }],
    })
    await api.materialIssues.post(27, 501, { version: 4, idempotencyKey: 'post-outsourcing-issue' })
    await api.receipts.create(27, {
      version: 5,
      businessDate: '2026-07-17',
      receiptWarehouseId: 9,
      idempotencyKey: 'outsourcing-receipt',
      lines: [{
        lineNo: 1,
        acceptedQuantity: '3.000000',
        rejectedQuantity: '0.000000',
        provisionalUnitCost: '88.000000',
        trackingAllocations: [{ serialNo: 'SN-027-001', quantity: '1.000000' }],
      }],
    })
    await api.receipts.post(27, 601, { version: 6, idempotencyKey: 'post-outsourcing-receipt' })
    await api.orders.close(27, { version: 7, idempotencyKey: 'close-outsourcing' })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/production/outsourcing-orders/27/release', expect.objectContaining({
      body: JSON.stringify({ version: 2, idempotencyKey: 'release-outsourcing' }),
      method: 'PUT',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/production/outsourcing-orders/27/material-issues', expect.objectContaining({
      body: JSON.stringify({
        version: 3,
        businessDate: '2026-07-17',
        warehouseId: 4,
        idempotencyKey: 'outsourcing-issue',
        lines: [{
          orderMaterialId: 5,
          lineNo: 1,
          quantity: '6.000000',
          ownershipType: 'PROJECT',
          projectId: 20,
          costLayerId: 3001,
          trackingAllocations: [{ batchNo: 'B-027', quantity: '6.000000' }],
        }],
      }),
      method: 'POST',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/production/outsourcing-orders/27/material-issues/501/post', expect.objectContaining({
      body: JSON.stringify({ version: 4, idempotencyKey: 'post-outsourcing-issue' }),
      method: 'PUT',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(8, '/api/admin/production/outsourcing-orders/27/receipts', expect.objectContaining({
      body: JSON.stringify({
        version: 5,
        businessDate: '2026-07-17',
        receiptWarehouseId: 9,
        idempotencyKey: 'outsourcing-receipt',
        lines: [{
          lineNo: 1,
          acceptedQuantity: '3.000000',
          rejectedQuantity: '0.000000',
          provisionalUnitCost: '88.000000',
          trackingAllocations: [{ serialNo: 'SN-027-001', quantity: '1.000000' }],
        }],
      }),
      method: 'POST',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(10, '/api/admin/production/outsourcing-orders/27/receipts/601/post', expect.objectContaining({
      body: JSON.stringify({ version: 6, idempotencyKey: 'post-outsourcing-receipt' }),
      method: 'PUT',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(12, '/api/admin/production/outsourcing-orders/27/close', expect.objectContaining({
      body: JSON.stringify({ version: 7, idempotencyKey: 'close-outsourcing' }),
      method: 'PUT',
    }))

    const conflictFetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-conflict', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiFailure())
    const conflictApi = createProductionOutsourcingApi({ fetcher: conflictFetcher })
    const request = conflictApi.orders.close(27, { version: 7, idempotencyKey: 'close-outsourcing' })

    await expect(request).rejects.toMatchObject({
      code: 'PRODUCTION_OUTSOURCING_STATUS_INVALID',
      status: 409,
      traceId: 'trace-027-outsourcing-conflict',
    })
    await expect(request).rejects.toBeInstanceOf(AccountPermissionApiError)
    expect(conflictFetcher).toHaveBeenCalledTimes(2)
  })

  it('写动作缺少非空幂等键时拒绝发送', async () => {
    const fetcher = vi.fn()
    const api = createProductionOutsourcingApi({ fetcher }) as ProductionOutsourcingApi

    await expect(api.orders.release(27, { version: 1, idempotencyKey: '' })).rejects.toThrow('幂等键不能为空')
    await expect(api.receipts.post(27, 601, { version: 1, idempotencyKey: '  ' })).rejects.toThrow('幂等键不能为空')
    expect(fetcher).not.toHaveBeenCalled()
  })
})
