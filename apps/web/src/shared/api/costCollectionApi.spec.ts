import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError, type PageResult } from './accountPermissionApi'
import {
  createCostCollectionApi,
  type CostBasisType,
  type CostCollectionApi,
  type CostAmountSummaryRecord,
  type CostQuantitySummaryRecord,
  type CostRecordAuditSummary,
  type CostRecordDetailRecord,
  type CostRecordOutputTrace,
  type CostRecordPayload,
  type CostRecordSourceSummary,
  type CostRecordStatus,
  type CostRecordSummaryRecord,
  type CostSourceDocumentType,
  type CostSourceType,
  type CostType,
  type WorkOrderCostSummaryRecord,
} from './costCollectionApi'

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
      message: '无权访问成本记录',
      data: null,
      traceId: 'trace-cost',
    }),
  }
}

describe('成本归集 API', () => {
  const costCollectionApiTypeContract = {
    listReturnsSummaryPage: true as AssertTrue<
      Awaited<ReturnType<CostCollectionApi['records']['list']>> extends PageResult<CostRecordSummaryRecord>
        ? true
        : false
    >,
    detailIncludesSourceSummary: true as AssertTrue<
      'sourceSummary' extends keyof CostRecordDetailRecord ? true : false
    >,
    detailOutputTraceUsesBackendArray: true as AssertTrue<
      CostRecordDetailRecord['outputTrace'] extends CostRecordOutputTrace[] | undefined ? true : false
    >,
    sourceSummaryUsesBackendFields: true as AssertTrue<
      'sourceStatus' extends keyof CostRecordSourceSummary
        ? 'sourceDocumentNo' extends keyof CostRecordSourceSummary
          ? 'materialId' extends keyof CostRecordSourceSummary
            ? 'unitId' extends keyof CostRecordSourceSummary
              ? true
              : false
            : false
          : false
        : false
    >,
    auditSummaryUsesBackendFields: true as AssertTrue<
      'id' extends keyof CostRecordAuditSummary
        ? 'operatorUsername' extends keyof CostRecordAuditSummary
          ? 'createdAt' extends keyof CostRecordAuditSummary
            ? true
            : false
          : false
        : false
    >,
    outputTraceUsesBackendFields: true as AssertTrue<
      'receiptId' extends keyof CostRecordOutputTrace
        ? 'workOrderId' extends keyof CostRecordOutputTrace
          ? 'receiptWarehouseId' extends keyof CostRecordOutputTrace
            ? 'beforeQuantity' extends keyof CostRecordOutputTrace
              ? 'afterQuantity' extends keyof CostRecordOutputTrace
                ? 'postedAt' extends keyof CostRecordOutputTrace
                  ? true
                  : false
                : false
              : false
            : false
          : false
        : false
    >,
    payloadKeepsDecimalStrings: true as AssertTrue<
      CostRecordPayload['quantity'] extends string | undefined
        ? CostRecordPayload['unitPrice'] extends string | undefined
          ? CostRecordPayload['amount'] extends string | undefined
            ? true
            : false
          : false
        : false
    >,
    summaryContainsFormalAccounting: true as AssertTrue<
      'formalAccounting' extends keyof WorkOrderCostSummaryRecord ? true : false
    >,
    summaryUsesBackendSummaryFields: true as AssertTrue<
      'amountSummaries' extends keyof WorkOrderCostSummaryRecord
        ? 'quantitySummaries' extends keyof WorkOrderCostSummaryRecord
          ? 'totalsByCostType' extends keyof WorkOrderCostSummaryRecord
            ? false
            : true
          : false
        : false
    >,
    amountSummaryKeepsBackendShape: true as AssertTrue<
      CostAmountSummaryRecord extends { costType: CostType; amount: number } ? true : false
    >,
    quantitySummaryKeepsBackendShape: true as AssertTrue<
      CostQuantitySummaryRecord extends { costType: CostType; quantity: number } ? true : false
    >,
    enumsUseExpectedValues: true as AssertTrue<
      CostType extends 'MATERIAL' | 'LABOR' | 'MANUFACTURING_OVERHEAD' | 'OTHER'
        ? CostSourceType extends 'AUTO_PRODUCTION' | 'MANUAL_ENTRY'
          ? CostSourceDocumentType extends
              | 'PRODUCTION_MATERIAL_ISSUE'
              | 'PRODUCTION_WORK_REPORT'
              | 'PRODUCTION_COMPLETION_RECEIPT'
              | 'MANUAL_COST_RECORD'
            ? CostBasisType extends
                | 'SOURCE_QUANTITY_ONLY'
                | 'MANUAL_AMOUNT'
                | 'MANUAL_UNIT_PRICE_QUANTITY'
                | 'OUTPUT_QUANTITY_TRACE'
              ? CostRecordStatus extends 'ACTIVE' | 'VOIDED'
                ? true
                : false
              : false
            : false
          : false
        : false
    >,
  }

  it('声明成本记录和工单汇总的类型契约', () => {
    expect(costCollectionApiTypeContract).toMatchObject({
      auditSummaryUsesBackendFields: true,
      detailOutputTraceUsesBackendArray: true,
      detailIncludesSourceSummary: true,
      enumsUseExpectedValues: true,
      listReturnsSummaryPage: true,
      outputTraceUsesBackendFields: true,
      payloadKeepsDecimalStrings: true,
      quantitySummaryKeepsBackendShape: true,
      amountSummaryKeepsBackendShape: true,
      summaryContainsFormalAccounting: true,
      summaryUsesBackendSummaryFields: true,
      sourceSummaryUsesBackendFields: true,
    })
  })

  it('按查询条件分页获取成本记录', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 2, pageSize: 50 }))
    const api = createCostCollectionApi({ baseUrl: '/erp', fetcher })

    await api.records.list({
      keyword: 'COST',
      workOrderId: 9,
      productMaterialId: 10,
      costType: 'MATERIAL',
      sourceType: 'AUTO_PRODUCTION',
      sourceDocumentType: 'PRODUCTION_MATERIAL_ISSUE',
      sourceDocumentNo: 'MFG-ISS-001',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-03',
      page: 2,
      pageSize: 50,
    })

    expect(fetcher).toHaveBeenCalledWith(
      '/erp/api/admin/cost/records?keyword=COST&workOrderId=9&productMaterialId=10&costType=MATERIAL&sourceType=AUTO_PRODUCTION&sourceDocumentType=PRODUCTION_MATERIAL_ISSUE&sourceDocumentNo=MFG-ISS-001&dateFrom=2026-07-01&dateTo=2026-07-03&page=2&pageSize=50',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
  })

  it('按成本记录标识获取详情', async () => {
    const detail: CostRecordDetailRecord = {
      id: 7,
      recordNo: 'COST-001',
      workOrderId: 9,
      workOrderNo: 'WO-001',
      productMaterialId: 10,
      productMaterialCode: 'FG-001',
      productMaterialName: '成品 A',
      costType: 'LABOR',
      sourceType: 'AUTO_PRODUCTION',
      sourceDocumentType: 'PRODUCTION_WORK_REPORT',
      basisType: 'SOURCE_QUANTITY_ONLY',
      businessDate: '2026-07-03',
      status: 'ACTIVE',
      recordedByName: '管理员',
      recordedAt: '2026-07-03T10:00:00+08:00',
      createdByName: '管理员',
      createdAt: '2026-07-03T10:00:00+08:00',
      updatedAt: '2026-07-03T10:00:00+08:00',
      workOrderStatus: 'IN_PROGRESS',
      sourceSummary: {
        sourceStatus: 'POSTED',
        sourceDocumentNo: 'MFG-RPT-001',
        quantity: 10,
        materialId: null,
        materialCode: null,
        materialName: null,
        unitId: 3,
        unitName: '件',
      },
      outputTrace: [
        {
          receiptId: 13,
          receiptNo: 'MFG-RCP-001',
          workOrderId: 9,
          businessDate: '2026-07-03',
          receiptWarehouseId: 6,
          receiptWarehouseName: '成品仓',
          quantity: 10,
          beforeQuantity: 20,
          afterQuantity: 30,
          postedByName: '管理员',
          postedAt: '2026-07-03T12:00:00+08:00',
        },
      ],
      auditSummary: [
        {
          id: 21,
          operatorUsername: 'admin',
          action: 'MFG_COST_RECORD_AUTO_CREATE',
          createdAt: '2026-07-03T10:00:00+08:00',
        },
      ],
    }
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse(detail))
    const api = createCostCollectionApi({ fetcher })

    await expect(api.records.get(7)).resolves.toMatchObject({
      recordNo: 'COST-001',
      sourceSummary: { sourceDocumentNo: 'MFG-RPT-001' },
      outputTrace: [{ receiptNo: 'MFG-RCP-001' }],
      auditSummary: [{ operatorUsername: 'admin' }],
    })

    expect(fetcher).toHaveBeenCalledWith('/api/admin/cost/records/7', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
  })

  it('创建和更新手工成本记录会先获取 CSRF 且数值 payload 保持字符串', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-create', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 11, recordNo: 'COST-001' }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-update', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 11, recordNo: 'COST-001' }))
    const api = createCostCollectionApi({ fetcher })
    const payload: CostRecordPayload = {
      workOrderId: 9,
      costType: 'MANUFACTURING_OVERHEAD',
      basisType: 'MANUAL_UNIT_PRICE_QUANTITY',
      businessDate: '2026-07-03',
      quantity: '12.500000',
      unitId: 3,
      unitPrice: '88.123456',
      amount: '1101.543200',
      sourceDocumentNo: 'MANUAL-001',
      remark: '制造费用业务记录',
    }

    await api.records.create(payload)
    await api.records.update(11, payload)

    expect(JSON.parse(fetcher.mock.calls[1][1].body as string)).toMatchObject({
      amount: '1101.543200',
      quantity: '12.500000',
      unitPrice: '88.123456',
    })
    expect(JSON.parse(fetcher.mock.calls[3][1].body as string)).toMatchObject({
      amount: '1101.543200',
      quantity: '12.500000',
      unitPrice: '88.123456',
    })
    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/auth/csrf', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/cost/records', {
      body: JSON.stringify(payload),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-create',
      },
      method: 'POST',
    })
    expect(fetcher).toHaveBeenNthCalledWith(3, '/api/auth/csrf', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/cost/records/11', {
      body: JSON.stringify(payload),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-update',
      },
      method: 'PUT',
    })
  })

  it('按生产工单标识获取成本汇总并保留非正式核算标记', async () => {
    const summary: WorkOrderCostSummaryRecord = {
      workOrderId: 9,
      workOrderNo: 'WO-001',
      productMaterialId: 10,
      productMaterialCode: 'FG-001',
      productMaterialName: '成品 A',
      formalAccounting: false,
      records: [],
      amountSummaries: [{ costType: 'MANUFACTURING_OVERHEAD', amount: 100 }],
      quantitySummaries: [{ costType: 'MATERIAL', quantity: 12.5 }],
      outputTraces: [
        {
          receiptId: 13,
          receiptNo: 'MFG-RCP-001',
          workOrderId: 9,
          businessDate: '2026-07-03',
          receiptWarehouseId: 6,
          receiptWarehouseName: '成品仓',
          quantity: 10,
          beforeQuantity: 20,
          afterQuantity: 30,
          postedByName: '管理员',
          postedAt: '2026-07-03T12:00:00+08:00',
        },
      ],
    }
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse(summary))
    const api = createCostCollectionApi({ fetcher })

    await expect(api.workOrders.summary(9)).resolves.toMatchObject({
      amountSummaries: [{ costType: 'MANUFACTURING_OVERHEAD', amount: 100 }],
      formalAccounting: false,
      quantitySummaries: [{ costType: 'MATERIAL', quantity: 12.5 }],
    })

    expect(fetcher).toHaveBeenCalledWith('/api/admin/cost/work-orders/9/summary', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
  })

  it('后端返回错误 envelope 时抛出统一 API 错误', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiFailure())
    const api = createCostCollectionApi({ fetcher })
    const request = api.records.list({ page: 1, pageSize: 20 })

    await expect(request).rejects.toMatchObject({
      code: 'AUTH_FORBIDDEN',
      status: 403,
      traceId: 'trace-cost',
    })
    await expect(request).rejects.toBeInstanceOf(AccountPermissionApiError)
  })
})
