import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError, type PageResult } from './accountPermissionApi'
import {
  createProductionApi,
  type ProductionApi,
  type ProductionCompletionReceiptPayload,
  type ProductionCompletionReceiptRecord,
  type ProductionDocumentListParams,
  type ProductionMaterialIssueDetailRecord,
  type ProductionMaterialIssueLineRecord,
  type ProductionMaterialIssueLinePayload,
  type ProductionMaterialIssuePayload,
  type ProductionMaterialIssueSummaryRecord,
  type ProductionWorkOrderMaterialRecord,
  type ProductionWorkOrderDetailRecord,
  type ProductionWorkOrderPayload,
  type ProductionWorkReportPayload,
  type ProductionWorkReportRecord,
} from './productionApi'
import type { InventoryTrackingAllocationPayload } from './inventoryApi'

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
  const productionApiTypeContract = {
    detailIncludesMovements: true as AssertTrue<
      'movements' extends keyof ProductionWorkOrderDetailRecord ? true : false
    >,
    materialIssuesAcceptPagination: true as AssertTrue<
      Parameters<ProductionApi['materialIssues']['list']>[1] extends ProductionDocumentListParams ? true : false
    >,
    materialIssuesListReturnSummaryPage: true as AssertTrue<
      Awaited<ReturnType<ProductionApi['materialIssues']['list']>> extends PageResult<ProductionMaterialIssueSummaryRecord>
        ? true
        : false
    >,
    materialIssueSummaryHasNoLines: true as AssertTrue<
      'lines' extends keyof ProductionMaterialIssueSummaryRecord ? false : true
    >,
    materialIssueDetailHasLines: true as AssertTrue<
      Awaited<ReturnType<ProductionApi['materialIssues']['get']>> extends ProductionMaterialIssueDetailRecord
        ? true
        : false
    >,
    workOrderDetailUsesMaterialIssueSummary: true as AssertTrue<
      ProductionWorkOrderDetailRecord['materialIssues'][number] extends ProductionMaterialIssueSummaryRecord
        ? true
        : false
    >,
    workOrderIncludesWarehouseAndCancelFields: true as AssertTrue<
      'issueWarehouseName' extends keyof ProductionWorkOrderDetailRecord
        ? 'receiptWarehouseName' extends keyof ProductionWorkOrderDetailRecord
          ? 'cancelledByName' extends keyof ProductionWorkOrderDetailRecord
            ? 'cancelledAt' extends keyof ProductionWorkOrderDetailRecord
              ? true
              : false
            : false
          : false
        : false
    >,
    materialIssueLineIncludesNames: true as AssertTrue<
      'warehouseName' extends keyof ProductionMaterialIssueDetailRecord['lines'][number]
        ? 'materialCode' extends keyof ProductionMaterialIssueDetailRecord['lines'][number]
          ? 'materialName' extends keyof ProductionMaterialIssueDetailRecord['lines'][number]
            ? 'unitName' extends keyof ProductionMaterialIssueDetailRecord['lines'][number]
              ? true
              : false
            : false
          : false
        : false
    >,
    reportsReturnPage: true as AssertTrue<
      Awaited<ReturnType<ProductionApi['reports']['list']>> extends PageResult<ProductionWorkReportRecord> ? true : false
    >,
    reportsIncludeAuditFields: true as AssertTrue<
      'createdByName' extends keyof ProductionWorkReportRecord
        ? 'updatedAt' extends keyof ProductionWorkReportRecord
          ? 'postedByName' extends keyof ProductionWorkReportRecord
            ? true
            : false
          : false
        : false
    >,
    completionReceiptsReturnPage: true as AssertTrue<
      Awaited<ReturnType<ProductionApi['completionReceipts']['list']>> extends PageResult<ProductionCompletionReceiptRecord>
        ? true
        : false
    >,
    completionReceiptsIncludeWarehouseAndAuditFields: true as AssertTrue<
      'receiptWarehouseName' extends keyof ProductionCompletionReceiptRecord
        ? 'createdByName' extends keyof ProductionCompletionReceiptRecord
          ? 'updatedAt' extends keyof ProductionCompletionReceiptRecord
            ? 'postedByName' extends keyof ProductionCompletionReceiptRecord
              ? true
              : false
            : false
          : false
        : false
    >,
    reportPayloadIncludesReporterName: true as AssertTrue<
      'reporterName' extends keyof ProductionWorkReportPayload ? true : false
    >,
    workOrderMaterialHasAvailabilityFields: true as AssertTrue<
      'reservedQuantity' extends keyof ProductionWorkOrderMaterialRecord
        ? 'occupiedQuantity' extends keyof ProductionWorkOrderMaterialRecord
          ? 'availableToPromiseQuantity' extends keyof ProductionWorkOrderMaterialRecord
            ? true
            : false
          : false
        : false
    >,
    materialIssueLineAcceptsTrackingAllocations: true as AssertTrue<
      ProductionMaterialIssueLinePayload extends {
        trackingAllocations?: InventoryTrackingAllocationPayload[]
      } ? true : false
    >,
    materialIssueLineReturnsTrackingAllocations: true as AssertTrue<
      ProductionMaterialIssueLineRecord extends {
        trackingMethod?: unknown
        trackingMethodName?: unknown
        trackingAllocations?: InventoryTrackingAllocationPayload[]
      } ? true : false
    >,
    completionReceiptAcceptsTrackingAllocations: true as AssertTrue<
      ProductionCompletionReceiptPayload extends {
        trackingAllocations?: InventoryTrackingAllocationPayload[]
      } ? true : false
    >,
    completionReceiptReturnsTrackingAllocations: true as AssertTrue<
      ProductionCompletionReceiptRecord extends {
        trackingMethod?: unknown
        trackingMethodName?: unknown
        trackingAllocations?: InventoryTrackingAllocationPayload[]
      } ? true : false
    >,
    completionReceiptAcceptsProvisionalUnitCost: true as AssertTrue<
      ProductionCompletionReceiptPayload extends {
        provisionalUnitCost?: string
      } ? true : false
    >,
    completionReceiptReturnsValuationStatus: true as AssertTrue<
      ProductionCompletionReceiptRecord extends {
        valuationMethod?: unknown
        valuationState?: unknown
        unitCost?: unknown
        amount?: unknown
      } ? true : false
    >,
    workOrderDetailReturnsCompletionValuationHint: true as AssertTrue<
      ProductionWorkOrderDetailRecord extends {
        completionValuationState?: unknown
        requiresManualProvisionalUnitCost?: unknown
        currentAverageUnitCost?: unknown
        costVisible?: unknown
      } ? true : false
    >,
  }

  it('声明生产详情和执行记录列表的类型契约', () => {
    expect(productionApiTypeContract).toMatchObject({
      completionReceiptsReturnPage: true,
      completionReceiptsIncludeWarehouseAndAuditFields: true,
      detailIncludesMovements: true,
      materialIssuesAcceptPagination: true,
      materialIssueDetailHasLines: true,
      materialIssueLineIncludesNames: true,
      materialIssueSummaryHasNoLines: true,
      materialIssuesListReturnSummaryPage: true,
      reportPayloadIncludesReporterName: true,
      reportsIncludeAuditFields: true,
      reportsReturnPage: true,
      workOrderMaterialHasAvailabilityFields: true,
      materialIssueLineAcceptsTrackingAllocations: true,
      materialIssueLineReturnsTrackingAllocations: true,
      completionReceiptAcceptsTrackingAllocations: true,
      completionReceiptReturnsTrackingAllocations: true,
      completionReceiptAcceptsProvisionalUnitCost: true,
      completionReceiptReturnsValuationStatus: true,
      workOrderDetailReturnsCompletionValuationHint: true,
      workOrderDetailUsesMaterialIssueSummary: true,
      workOrderIncludesWarehouseAndCancelFields: true,
    })
  })

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

  it('按生产工单获取详情时保留库存流水摘要', async () => {
    const detail = {
      id: 9,
      workOrderNo: 'WO-20260703-001',
      movements: [
        {
          id: 1001,
          movementNo: 'MOV-1',
          movementType: 'PRODUCTION_ISSUE',
          direction: 'OUT',
          warehouseId: 2,
          warehouseName: '原料仓',
          materialId: 3,
          materialCode: 'RM-001',
          materialName: '原材料',
          unitId: 4,
          unitName: '千克',
          quantity: 12.5,
          beforeQuantity: 100,
          afterQuantity: 87.5,
          sourceType: 'PRODUCTION_MATERIAL_ISSUE',
          sourceId: 11,
          sourceLineId: 111,
          businessDate: '2026-07-03',
          reason: '生产领料',
          remark: '首批领料',
          operatorName: '管理员',
          occurredAt: '2026-07-03T10:00:00+08:00',
        },
      ],
    }
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse(detail))
    const api = createProductionApi({ fetcher })

    await expect(api.workOrders.get(9)).resolves.toMatchObject({
      movements: [{ movementType: 'PRODUCTION_ISSUE', sourceType: 'PRODUCTION_MATERIAL_ISSUE' }],
    })

    expect(fetcher).toHaveBeenCalledWith('/api/admin/production/work-orders/9', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
  })

  it('按分页参数获取领料、报工和完工入库列表', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({
        items: [
          {
            id: 11,
            issueNo: 'MI-1',
            workOrderId: 9,
            status: 'DRAFT',
            businessDate: '2026-07-03',
            reason: '生产领料',
            remark: '首批领料',
            lineCount: 2,
            createdByName: '管理员',
            createdAt: '2026-07-03T09:00:00+08:00',
            updatedAt: '2026-07-03T09:10:00+08:00',
            postedByName: null,
            postedAt: null,
          },
        ],
        total: 21,
        page: 2,
        pageSize: 10,
      }))
      .mockResolvedValueOnce(apiResponse({
        items: [
          {
            id: 12,
            reportNo: 'WR-1',
            workOrderId: 9,
            status: 'POSTED',
            businessDate: '2026-07-03',
            qualifiedQuantity: 10,
            defectiveQuantity: 0.5,
            totalQuantity: 10.5,
            reporterName: '张三',
            remark: '首批报工',
            createdByName: '管理员',
            createdAt: '2026-07-03T10:00:00+08:00',
            updatedAt: '2026-07-03T10:05:00+08:00',
            postedByName: '管理员',
            postedAt: '2026-07-03T10:10:00+08:00',
          },
        ],
        total: 12,
        page: 3,
        pageSize: 5,
      }))
      .mockResolvedValueOnce(apiResponse({
        items: [
          {
            id: 13,
            receiptNo: 'CR-1',
            workOrderId: 9,
            status: 'POSTED',
            businessDate: '2026-07-03',
            receiptWarehouseId: 3,
            receiptWarehouseName: '成品仓',
            quantity: 10,
            beforeQuantity: 100,
            afterQuantity: 110,
            remark: '首批入库',
            createdByName: '管理员',
            createdAt: '2026-07-03T11:00:00+08:00',
            updatedAt: '2026-07-03T11:05:00+08:00',
            postedByName: '管理员',
            postedAt: '2026-07-03T11:10:00+08:00',
          },
        ],
        total: 8,
        page: 4,
        pageSize: 2,
      }))
    const api = createProductionApi({ fetcher })

    const materialIssuesPage = await api.materialIssues.list(9, { page: 2, pageSize: 10 })
    const reportsPage = await api.reports.list(9, { page: 3, pageSize: 5 })
    const completionReceiptsPage = await api.completionReceipts.list(9, { page: 4, pageSize: 2 })

    expect(materialIssuesPage).toMatchObject({ total: 21 })
    expect(materialIssuesPage.items[0]).toMatchObject({ lineCount: 2, updatedAt: '2026-07-03T09:10:00+08:00' })
    expect(materialIssuesPage.items[0]).not.toHaveProperty('lines')
    expect(reportsPage).toMatchObject({ total: 12 })
    expect(reportsPage.items[0]).toMatchObject({
      createdByName: '管理员',
      postedByName: '管理员',
      updatedAt: '2026-07-03T10:05:00+08:00',
    })
    expect(completionReceiptsPage).toMatchObject({ total: 8 })
    expect(completionReceiptsPage.items[0]).toMatchObject({
      createdByName: '管理员',
      postedByName: '管理员',
      receiptWarehouseName: '成品仓',
      updatedAt: '2026-07-03T11:05:00+08:00',
    })

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/admin/production/work-orders/9/material-issues?page=2&pageSize=10', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/production/work-orders/9/reports?page=3&pageSize=5', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
    expect(fetcher).toHaveBeenNthCalledWith(
      3,
      '/api/admin/production/work-orders/9/completion-receipts?page=4&pageSize=2',
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
      lines: [{
        workOrderMaterialId: 101,
        lineNo: 1,
        warehouseId: 2,
        quantity: '12.500000',
        trackingAllocations: [{ batchId: 31, quantity: '12.500000' }],
      }],
    }
    const reportPayload: ProductionWorkReportPayload = {
      businessDate: '2026-07-03',
      qualifiedQuantity: '10.000000',
      defectiveQuantity: '0.500000',
      reporterName: '张三',
    }
    const receiptPayload: ProductionCompletionReceiptPayload = {
      businessDate: '2026-07-03',
      receiptWarehouseId: 3,
      quantity: '10.000000',
      provisionalUnitCost: '12.345678',
      trackingAllocations: [{ batchNo: 'B-WO-001', quantity: '10.000000' }],
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

  it('完工入库暂估单价保持十进制字符串发送给后端', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-receipt-create', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 13, receiptNo: 'CR-1' }))
    const api = createProductionApi({ fetcher })

    await api.completionReceipts.create(9, {
      businessDate: '2026-07-03',
      receiptWarehouseId: 3,
      quantity: '10.000000',
      provisionalUnitCost: '999999999999.999999',
    })

    expect(JSON.parse(fetcher.mock.calls[1][1].body as string)).toMatchObject({
      quantity: '10.000000',
      provisionalUnitCost: '999999999999.999999',
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
