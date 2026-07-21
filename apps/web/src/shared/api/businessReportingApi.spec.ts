import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from './accountPermissionApi'
import {
  createBusinessReportingApi,
  type ContractCollectionReportRow,
  type FinancialSummaryRecord,
  type InventoryCapitalReportRow,
  type InventoryCapitalReportSummary,
  type OperatingAccountingReconciliationReportRow,
  type OperatingFinanceOverviewRecord,
  type ProcurementVarianceReportRow,
  type ProjectProfitDetailRecord,
  type ProjectProfitReportRow,
  type ReceivablePayableReportRow,
  type ReportTraceRecord,
  type SalesReportRow,
  type SalesReportSummary,
  type SettlementReportRow,
} from './businessReportingApi'

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
      message: '无报表权限',
      data: null,
      traceId: 'trace-report',
    }),
  }
}

describe('经营报表 API', () => {
  it('按固定路径获取经营概览和七类固定报表并过滤空查询值', async () => {
    const fetcher = vi.fn()
    Array.from({ length: 8 }).forEach(() => {
      fetcher.mockResolvedValueOnce(apiResponse({ summary: { sourceCount: 0 }, items: [], page: 1, pageSize: 20, total: 0, totalPages: 0 }))
    })
    const api = createBusinessReportingApi({ fetcher })

    await api.overview.get({ dateFrom: '2026-07-01', dateTo: '' })
    await api.sales.list({ dateFrom: '2026-07-01', dateTo: null, customerId: 8, materialId: undefined, status: 'POSTED', keyword: '销售', page: 1, pageSize: 20 })
    await api.procurement.list({ dateFrom: '2026-07-01', dateTo: '', supplierId: 9, materialId: 31, status: 'POSTED', keyword: '', page: 1, pageSize: 20 })
    await api.inventory.list({ dateFrom: '2026-07-01', dateTo: null, warehouseId: 1, materialId: 31, keyword: '库存', page: 1, pageSize: 20 })
    await api.production.list({ dateFrom: '2026-07-01', dateTo: '', workOrderId: 1001, materialId: 31, status: 'IN_PROGRESS', keyword: null, page: 1, pageSize: 20 })
    await api.cost.list({ dateFrom: '2026-07-01', dateTo: null, workOrderId: 1001, materialId: 31, status: 'POSTED', keyword: '成本', page: 1, pageSize: 20 })
    await api.settlement.list({ dateFrom: '2026-07-01', dateTo: '', customerId: 8, supplierId: null, status: 'CONFIRMED', keyword: '往来', page: 1, pageSize: 20 })
    await api.exceptions.list({ dateFrom: '2026-07-01', dateTo: null, type: 'INVENTORY_SHORTAGE', keyword: '异常', page: 1, pageSize: 20 })

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/admin/reports/overview?dateFrom=2026-07-01', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
    expect(fetcher).toHaveBeenNthCalledWith(
      2,
      '/api/admin/reports/sales-summary?dateFrom=2026-07-01&customerId=8&status=POSTED&keyword=%E9%94%80%E5%94%AE&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      3,
      '/api/admin/reports/procurement-summary?dateFrom=2026-07-01&supplierId=9&materialId=31&status=POSTED&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      4,
      '/api/admin/reports/inventory-stock-flow?dateFrom=2026-07-01&warehouseId=1&materialId=31&keyword=%E5%BA%93%E5%AD%98&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      5,
      '/api/admin/reports/production-execution?dateFrom=2026-07-01&workOrderId=1001&materialId=31&status=IN_PROGRESS&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      6,
      '/api/admin/reports/cost-collection?dateFrom=2026-07-01&workOrderId=1001&materialId=31&status=POSTED&keyword=%E6%88%90%E6%9C%AC&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      7,
      '/api/admin/reports/settlement-summary?dateFrom=2026-07-01&customerId=8&status=CONFIRMED&keyword=%E5%BE%80%E6%9D%A5&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      8,
      '/api/admin/reports/exceptions?dateFrom=2026-07-01&type=INVENTORY_SHORTAGE&keyword=%E5%BC%82%E5%B8%B8&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
  })

  it('固定报表响应保留 summary + items，金额和数量保持字符串', async () => {
    const summary: SalesReportSummary = {
      shipmentQuantity: '10.000',
      shipmentAmount: '12000.00',
      salesOriginalAmount: '12000.00',
      salesReturnAmount: '2400.00',
      salesNetAmount: '9600.00',
      salesOriginalQuantity: '10.000',
      salesReturnQuantity: '2.000',
      salesNetQuantity: '8.000',
      sourceCount: 1,
    }
    const salesReturnRow: SalesReportRow = {
      sourceType: 'SALES_RETURN',
      sourceId: 5001,
      sourceNo: 'SR202607050001',
      customerName: '示例客户',
      materialName: '示例成品',
      businessDate: '2026-07-05',
      quantity: '2.000',
      amount: '2400.00',
      salesOriginalAmount: '0.00',
      salesReturnAmount: '2400.00',
      salesNetAmount: '-2400.00',
      salesOriginalQuantity: '0.000',
      salesReturnQuantity: '2.000',
      salesNetQuantity: '-2.000',
      sourceCount: 1,
      traceKey: 'sales-summary:SALES_RETURN:5001',
    }
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({
      summary,
      items: [salesReturnRow],
      page: 1,
      pageSize: 20,
      total: 1,
      totalPages: 1,
    }))
    const api = createBusinessReportingApi({ fetcher })

    const result = await api.sales.list({ page: 1, pageSize: 20 })

    expect(result.summary.shipmentAmount).toBe('12000.00')
    expect(typeof result.summary.shipmentAmount).toBe('string')
    expect(result.summary.salesReturnAmount).toBe('2400.00')
    expect(typeof result.summary.salesNetQuantity).toBe('string')
    expect(result.items[0].sourceType).toBe('SALES_RETURN')
    expect(result.items[0].quantity).toBe('2.000')
    expect(typeof result.items[0].quantity).toBe('string')
    expect(result.items[0].salesNetAmount).toBe('-2400.00')
    expect(result.totalPages).toBe(1)
  })

  it('往来报表行和追溯响应兼容反向来源类型', async () => {
    const settlementAdjustmentRow: SettlementReportRow = {
      settlementType: 'SETTLEMENT_ADJUSTMENT',
      sourceType: 'SETTLEMENT_ADJUSTMENT',
      sourceId: 8001,
      sourceNo: 'SA202607050001',
      partyType: 'CUSTOMER',
      partyId: 8,
      partyName: '示例客户',
      businessDate: '2026-07-05',
      totalAmount: '60.00',
      settledAmount: '60.00',
      unsettledAmount: '0.00',
      receivableOriginalAmount: '500.00',
      receivableAdjustmentAmount: '60.00',
      receivableNetAmount: '440.00',
      payableOriginalAmount: '0.00',
      payableAdjustmentAmount: '0.00',
      payableNetAmount: '0.00',
      settlementRemainingAmount: '440.00',
      status: 'POSTED',
      sourceCount: 1,
      traceKey: 'settlement-summary:SETTLEMENT_ADJUSTMENT:8001',
    }
    const traceRows: ReportTraceRecord[] = [
      {
        sourceType: 'SALES_RETURN',
        sourceId: 5001,
        sourceNo: 'SR202607050001',
        sourceLineId: null,
        businessDate: '2026-07-05',
        status: 'POSTED',
        quantity: '2.000',
        amount: '2400.00',
        resourceRouteName: 'sales-return-detail',
        resourceRouteParams: { id: 5001 },
        resourceRouteQuery: null,
        canViewResource: true,
        restricted: false,
        restrictedMessage: null,
      },
      {
        sourceType: 'PURCHASE_RETURN',
        sourceId: 6001,
        sourceNo: 'PR202607050001',
        sourceLineId: null,
        businessDate: '2026-07-05',
        status: 'POSTED',
        quantity: '1.000',
        amount: '800.00',
        resourceRouteName: 'procurement-return-detail',
        resourceRouteParams: { id: 6001 },
        resourceRouteQuery: null,
        canViewResource: true,
        restricted: false,
        restrictedMessage: null,
      },
      {
        sourceType: 'PRODUCTION_MATERIAL_RETURN',
        sourceId: 7001,
        sourceNo: 'PMR202607050001',
        sourceLineId: null,
        businessDate: '2026-07-05',
        status: 'POSTED',
        quantity: '3.000',
        amount: null,
        resourceRouteName: 'production-material-return-detail',
        resourceRouteParams: { id: 7001 },
        resourceRouteQuery: null,
        canViewResource: true,
        restricted: false,
        restrictedMessage: null,
      },
      {
        sourceType: 'PRODUCTION_MATERIAL_SUPPLEMENT',
        sourceId: 7002,
        sourceNo: 'PMS202607050001',
        sourceLineId: null,
        businessDate: '2026-07-05',
        status: 'POSTED',
        quantity: '4.000',
        amount: null,
        resourceRouteName: 'production-material-supplement-detail',
        resourceRouteParams: { id: 7002 },
        resourceRouteQuery: null,
        canViewResource: true,
        restricted: false,
        restrictedMessage: null,
      },
      {
        sourceType: 'SETTLEMENT_ADJUSTMENT',
        sourceId: 8001,
        sourceNo: 'SA202607050001',
        sourceLineId: null,
        businessDate: '2026-07-05',
        status: 'POSTED',
        quantity: null,
        amount: '60.00',
        resourceRouteName: 'finance-settlement-adjustment-detail',
        resourceRouteParams: { id: 8001 },
        resourceRouteQuery: null,
        canViewResource: true,
        restricted: false,
        restrictedMessage: null,
      },
    ]
    const fetcher = vi.fn()
      .mockResolvedValueOnce(apiResponse({
        summary: {
          receivableAmount: '500.00',
          receivedAmount: '0.00',
          unreceivedAmount: '440.00',
          payableAmount: '0.00',
          paidAmount: '0.00',
          unpaidAmount: '0.00',
          receivableOriginalAmount: '500.00',
          receivableAdjustmentAmount: '60.00',
          receivableNetAmount: '440.00',
          payableOriginalAmount: '0.00',
          payableAdjustmentAmount: '0.00',
          payableNetAmount: '0.00',
          settlementRemainingAmount: '440.00',
          sourceCount: 1,
        },
        items: [settlementAdjustmentRow],
        page: 1,
        pageSize: 20,
        total: 1,
        totalPages: 1,
      }))
      .mockResolvedValueOnce(apiResponse({ items: traceRows, page: 1, pageSize: 20, total: 5, totalPages: 1 }))
    const api = createBusinessReportingApi({ fetcher })

    const settlement = await api.settlement.list({ page: 1, pageSize: 20 })
    const traces = await api.settlement.traces.list({ traceKey: 'settlement-summary:SETTLEMENT_ADJUSTMENT:8001', page: 1, pageSize: 20 })

    expect(settlement.items[0].settlementType).toBe('SETTLEMENT_ADJUSTMENT')
    expect(settlement.items[0].sourceType).toBe('SETTLEMENT_ADJUSTMENT')
    expect(settlement.items[0].receivableAdjustmentAmount).toBe('60.00')
    expect(traces.items.map((item) => item.sourceType)).toEqual([
      'SALES_RETURN',
      'PURCHASE_RETURN',
      'PRODUCTION_MATERIAL_RETURN',
      'PRODUCTION_MATERIAL_SUPPLEMENT',
      'SETTLEMENT_ADJUSTMENT',
    ])
    expect(traces.items.map((item) => item.resourceRouteName)).toEqual([
      'sales-return-detail',
      'procurement-return-detail',
      'production-material-return-detail',
      'production-material-supplement-detail',
      'finance-settlement-adjustment-detail',
    ])
  })

  it('覆盖全部追溯接口并支持期间敏感追溯参数', async () => {
    const fetcher = vi.fn()
    Array.from({ length: 7 }).forEach(() => {
      fetcher.mockResolvedValueOnce(apiResponse({ items: [], page: 1, pageSize: 20, total: 0, totalPages: 0 }))
    })
    const api = createBusinessReportingApi({ fetcher })

    await api.sales.traces.list({ traceKey: 'sales-summary:SALES_SHIPMENT:1001', page: 1, pageSize: 20 })
    await api.procurement.traces.list({ traceKey: 'procurement-summary:PURCHASE_RECEIPT:2001', page: 1, pageSize: 20 })
    await api.inventory.traces.list({ traceKey: 'inventory-stock-flow:1:31', dateFrom: '2026-07-01', dateTo: '2026-07-31', page: 1, pageSize: 20 })
    await api.production.traces.list({ traceKey: 'production-execution:WORK_ORDER:1001', dateFrom: '2026-07-01', dateTo: '', page: 1, pageSize: 20 })
    await api.cost.traces.list({ traceKey: 'cost-collection:COST_RECORD:3001', dateFrom: '2026-07-01', dateTo: null, page: 1, pageSize: 20 })
    await api.settlement.traces.list({ traceKey: 'settlement-summary:RECEIVABLE:1001', page: 1, pageSize: 20 })
    await api.exceptions.traces.list({ traceKey: 'exceptions:INVENTORY_SHORTAGE:INVENTORY_BALANCE:1:31', dateFrom: '2026-07-01', dateTo: '2026-07-31', page: 1, pageSize: 20 })

    expect(fetcher).toHaveBeenNthCalledWith(
      1,
      '/api/admin/reports/sales-summary/traces?traceKey=sales-summary%3ASALES_SHIPMENT%3A1001&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      2,
      '/api/admin/reports/procurement-summary/traces?traceKey=procurement-summary%3APURCHASE_RECEIPT%3A2001&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      3,
      '/api/admin/reports/inventory-stock-flow/traces?traceKey=inventory-stock-flow%3A1%3A31&dateFrom=2026-07-01&dateTo=2026-07-31&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      4,
      '/api/admin/reports/production-execution/traces?traceKey=production-execution%3AWORK_ORDER%3A1001&dateFrom=2026-07-01&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      5,
      '/api/admin/reports/cost-collection/traces?traceKey=cost-collection%3ACOST_RECORD%3A3001&dateFrom=2026-07-01&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      6,
      '/api/admin/reports/settlement-summary/traces?traceKey=settlement-summary%3ARECEIVABLE%3A1001&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      7,
      '/api/admin/reports/exceptions/traces?traceKey=exceptions%3AINVENTORY_SHORTAGE%3AINVENTORY_BALANCE%3A1%3A31&dateFrom=2026-07-01&dateTo=2026-07-31&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
  })

  it('追溯响应支持路由参数、查询参数和来源受限脱敏状态', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({
      items: [
        {
          sourceType: 'INVENTORY_MOVEMENT',
          sourceId: 901,
          sourceNo: 'IM202607040001',
          sourceLineId: 902,
          businessDate: '2026-07-04',
          status: 'POSTED',
          quantity: '5.000',
          amount: null,
          resourceRouteName: 'inventory-movements',
          resourceRouteParams: {},
          resourceRouteQuery: { sourceId: 901 },
          canViewResource: true,
          restricted: false,
          restrictedMessage: null,
        },
        {
          sourceType: 'SALES_SHIPMENT',
          sourceId: null,
          sourceNo: null,
          sourceLineId: null,
          businessDate: null,
          status: null,
          quantity: null,
          amount: null,
          resourceRouteName: null,
          resourceRouteParams: null,
          resourceRouteQuery: null,
          canViewResource: false,
          restricted: true,
          restrictedMessage: '当前账号没有查看来源详情的权限',
        },
      ],
      page: 1,
      pageSize: 20,
      total: 2,
      totalPages: 1,
    }))
    const api = createBusinessReportingApi({ fetcher })

    const result = await api.inventory.traces.list({ traceKey: 'inventory-stock-flow:1:31', page: 1, pageSize: 20 })

    expect(result.items[0].resourceRouteName).toBe('inventory-movements')
    expect(result.items[0].resourceRouteParams).toEqual({})
    expect(result.items[0].resourceRouteQuery).toEqual({ sourceId: 901 })
    expect(result.items[1].canViewResource).toBe(false)
    expect(result.items[1].sourceNo).toBeNull()
    expect(result.items[1].resourceRouteQuery).toBeNull()
  })

  it('后端返回非成功 envelope 时抛出统一 API 错误', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiFailure())
    const api = createBusinessReportingApi({ fetcher })
    const request = api.overview.get({})

    await expect(request).rejects.toMatchObject({
      code: 'AUTH_FORBIDDEN',
      status: 403,
      traceId: 'trace-report',
    })
    await expect(request).rejects.toBeInstanceOf(AccountPermissionApiError)
  })

  it('033 经营财务报表沿用 /api/admin/reports 前缀和 GET 查询契约', async () => {
    const fetcher = vi.fn()
    Array.from({ length: 16 }).forEach(() => {
      fetcher.mockResolvedValueOnce(apiResponse({ summary: { sourceCount: 0 }, items: [], page: 1, pageSize: 10, total: 0, totalPages: 0 }))
    })
    const api = createBusinessReportingApi({ fetcher })

    await api.operatingFinanceOverview.get({ periodCode: '2026-07', analysisMode: 'LIVE' })
    await api.projectProfit.list({ periodCode: '2026-07', analysisMode: 'LIVE', projectId: 1, completenessStatus: 'INCOMPLETE', page: 1, pageSize: 10 })
    await api.projectProfit.detail.get(1, { periodCode: '2026-07', analysisMode: 'BUSINESS_SNAPSHOT' })
    await api.projectProfit.traces.list({ projectId: 1, traceKey: 'project-profit:PROJECT_COST:1', periodCode: '2026-07', analysisMode: 'LIVE', page: 1, pageSize: 10 })
    await api.contractCollection.list({ periodCode: '2026-07', projectId: 1, contractId: 2, page: 1, pageSize: 10 })
    await api.contractCollection.traces.list({ traceKey: 'contract-collection:RECEIPT:9', periodCode: '2026-07', page: 1, pageSize: 10 })
    await api.procurementVariance.list({ periodCode: '2026-07', projectId: 1, basis: 'PROJECT', reconciliationStatus: 'DIFFERENT', page: 1, pageSize: 10 })
    await api.procurementVariance.traces.list({ traceKey: 'procurement-variance:PURCHASE_RECEIPT:3', periodCode: '2026-07', page: 1, pageSize: 10 })
    await api.inventoryCapital.list({ periodCode: '2026-07', projectId: 1, analysisMode: 'BUSINESS_SNAPSHOT', page: 1, pageSize: 10 })
    await api.inventoryCapital.traces.list({ traceKey: 'inventory-capital:INVENTORY_BALANCE:1:31', periodCode: '2026-07', analysisMode: 'BUSINESS_SNAPSHOT', page: 1, pageSize: 10 })
    await api.receivablePayable.list({ periodCode: '2026-07', projectId: 1, page: 1, pageSize: 10 })
    await api.receivablePayable.traces.list({ traceKey: 'receivable-payable:RECEIVABLE:4', periodCode: '2026-07', page: 1, pageSize: 10 })
    await api.operatingAccountingReconciliation.list({ periodCode: '2026-07', projectId: 1, finalityStatus: 'PREVIEW', page: 1, pageSize: 10 })
    await api.operatingAccountingReconciliation.traces.list({ traceKey: 'operating-accounting:GL_VOUCHER:5', periodCode: '2026-07', page: 1, pageSize: 10 })
    await api.financialSummary.get({ periodCode: '2026-07', finalityStatus: 'FINAL' })
    await api.financialSummary.traces.list({ traceKey: 'financial-summary:TAX_SUMMARY:6', periodCode: '2026-07', page: 1, pageSize: 10 })

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/admin/reports/operating-finance-overview?periodCode=2026-07&analysisMode=LIVE', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/reports/project-profit?periodCode=2026-07&projectId=1&analysisMode=LIVE&completenessStatus=INCOMPLETE&page=1&pageSize=10', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(3, '/api/admin/reports/project-profit/1?periodCode=2026-07&analysisMode=BUSINESS_SNAPSHOT', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/reports/project-profit/1/traces?traceKey=project-profit%3APROJECT_COST%3A1&periodCode=2026-07&analysisMode=LIVE&page=1&pageSize=10', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(5, '/api/admin/reports/contract-collections?periodCode=2026-07&projectId=1&contractId=2&page=1&pageSize=10', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/reports/contract-collections/traces?traceKey=contract-collection%3ARECEIPT%3A9&periodCode=2026-07&page=1&pageSize=10', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(7, '/api/admin/reports/procurement-variances?periodCode=2026-07&projectId=1&basis=PROJECT&reconciliationStatus=DIFFERENT&page=1&pageSize=10', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(8, '/api/admin/reports/procurement-variances/traces?traceKey=procurement-variance%3APURCHASE_RECEIPT%3A3&periodCode=2026-07&page=1&pageSize=10', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(9, '/api/admin/reports/inventory-capital?periodCode=2026-07&projectId=1&analysisMode=BUSINESS_SNAPSHOT&page=1&pageSize=10', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(10, '/api/admin/reports/inventory-capital/traces?traceKey=inventory-capital%3AINVENTORY_BALANCE%3A1%3A31&periodCode=2026-07&analysisMode=BUSINESS_SNAPSHOT&page=1&pageSize=10', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(11, '/api/admin/reports/receivable-payable?periodCode=2026-07&projectId=1&page=1&pageSize=10', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(12, '/api/admin/reports/receivable-payable/traces?traceKey=receivable-payable%3ARECEIVABLE%3A4&periodCode=2026-07&page=1&pageSize=10', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(13, '/api/admin/reports/operating-accounting-reconciliation?periodCode=2026-07&projectId=1&finalityStatus=PREVIEW&page=1&pageSize=10', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(14, '/api/admin/reports/operating-accounting-reconciliation/traces?traceKey=operating-accounting%3AGL_VOUCHER%3A5&periodCode=2026-07&page=1&pageSize=10', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(15, '/api/admin/reports/financial-summary?periodCode=2026-07&finalityStatus=FINAL', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(16, '/api/admin/reports/financial-summary/traces?traceKey=financial-summary%3ATAX_SUMMARY%3A6&periodCode=2026-07&page=1&pageSize=10', expect.objectContaining({ method: 'GET' }))
  })

  it('033 DTO 保留字符串金额、null 受限态和固定状态字段', async () => {
    const overview: OperatingFinanceOverviewRecord = {
      periodCode: '2026-07',
      analysisMode: 'LIVE',
      businessPeriodStatus: 'OPEN',
      accountingPeriodStatus: 'OPEN',
      financialCloseStatus: 'OPEN',
      finalityStatus: 'PREVIEW',
      freshnessStatus: 'CURRENT',
      projectProfitAmount: '1600.00',
      contractUnreceivedAmount: '300.00',
      procurementVarianceAmount: null,
      inventoryCapitalAmount: '12000.00',
      receivablePayableBalanceAmount: '500.00',
      accountingDifferenceAmount: null,
      restrictedReason: '缺少采购差异金额权限',
      sourceCount: 7,
    }
    const projectRow: ProjectProfitReportRow = {
      projectId: 1,
      projectNo: 'PRJ-001',
      projectName: '示例项目',
      customerName: '示例客户',
      shipmentRevenueAmount: '1600.00',
      invoiceRevenueAmount: '1200.00',
      targetRevenueAmount: '2000.00',
      projectCostAmount: '0.00',
      operatingGrossProfitAmount: '1600.00',
      operatingGrossProfitRate: null,
      accountingRevenueAmount: null,
      accountingCostAmount: null,
      accountingProfitAmount: null,
      differenceAmount: null,
      completenessStatus: 'INCOMPLETE',
      freshnessStatus: 'STALE',
      reconciliationStatus: 'UNAVAILABLE',
      finalityStatus: 'UNAVAILABLE',
      sourceCount: 1,
      traceKey: 'project-profit:PROJECT:1',
    }
    const detail: ProjectProfitDetailRecord = {
      ...projectRow,
      costStageEntries: [{ stage: 'DELIVERED', amount: '0.00', status: 'INCOMPLETE' }],
      revenueEntries: [{ basis: 'SHIPMENT', amount: '1600.00', description: '发货经营收入' }],
      accountingEntries: [],
      varianceReasons: [{ reasonCode: 'NO_ACCOUNTING_FACT', description: '无会计事实', amount: null }],
    }
    const collectionRow: ContractCollectionReportRow = {
      projectId: 1,
      projectNo: 'PRJ-001',
      contractId: 2,
      contractNo: 'CT-001',
      customerName: '示例客户',
      contractAmount: '2000.00',
      invoiceAmount: '1200.00',
      receivedAmount: '900.00',
      allocatedAmount: '900.00',
      unreceivedAmount: '300.00',
      advanceReceiptAmount: '0.00',
      overdueAmount: '300.00',
      collectionRate: '75.00%',
      status: 'OVERDUE',
      sourceCount: 3,
      traceKey: 'contract-collection:CONTRACT:2',
    }
    const procurementRow: ProcurementVarianceReportRow = {
      sourceNo: 'PO-001',
      supplierName: '示例供应商',
      projectNo: 'PRJ-001',
      basis: 'PROJECT',
      orderAmount: '800.00',
      receiptAmount: '700.00',
      invoiceAmount: '650.00',
      paidAmount: '400.00',
      unreceivedOrderAmount: '100.00',
      receivedUninvoicedAmount: '50.00',
      invoiceReceiptDifferenceAmount: '-50.00',
      unpaidAmount: '250.00',
      matchVarianceAmount: '50.00',
      outsourcingUnsettledAmount: '0.00',
      reconciliationStatus: 'DIFFERENT',
      sourceCount: 2,
      traceKey: 'procurement-variance:PURCHASE_ORDER:3',
    }
    const inventoryRow: InventoryCapitalReportRow = {
      ownerType: 'PROJECT',
      projectNo: 'PRJ-001',
      warehouseName: '主仓',
      materialName: '示例物料',
      qualityStatus: 'QUALIFIED',
      freezeStatus: 'AVAILABLE',
      valuationStatus: 'UNVALUED',
      quantity: '5.000',
      amount: null,
      snapshotAmount: '500.00',
      differenceAmount: null,
      riskQuantity: '5.000',
      unknownValuationQuantity: '5.000',
      completenessStatus: 'INCOMPLETE',
      freshnessStatus: 'LEGACY_NOT_INCLUDED',
      sourceCount: 1,
      traceKey: 'inventory-capital:INVENTORY_BALANCE:1:31',
    }
    const inventorySummary: InventoryCapitalReportSummary = {
      quantity: '5.000',
      amount: null,
      snapshotAmount: '500.00',
      differenceAmount: null,
      riskQuantity: '5.000',
      knownValuationAmount: null,
      unknownValuationQuantity: '5.000',
      completenessStatus: 'INCOMPLETE',
      sourceCount: 1,
    }
    const receivableRow: ReceivablePayableReportRow = {
      partyType: 'CUSTOMER',
      partyName: '示例客户',
      projectNo: 'PRJ-001',
      receivableAmount: '1200.00',
      payableAmount: '0.00',
      advanceReceiptAmount: '0.00',
      prepaymentAmount: '0.00',
      settledAmount: '900.00',
      balanceAmount: '300.00',
      notDueAmount: '0.00',
      aging1To30Amount: '300.00',
      aging31To60Amount: '0.00',
      aging61To90Amount: '0.00',
      agingOver90Amount: '0.00',
      overdueAmount: '300.00',
      sourceCount: 2,
      traceKey: 'receivable-payable:CUSTOMER:8',
    }
    const accountingRow: OperatingAccountingReconciliationReportRow = {
      projectId: 1,
      projectNo: 'PRJ-001',
      projectName: '示例项目',
      operatingRevenueAmount: '1600.00',
      operatingCostAmount: '0.00',
      operatingProfitAmount: '1600.00',
      accountingRevenueAmount: null,
      accountingCostAmount: null,
      accountingProfitAmount: null,
      publicUnallocatedAmount: null,
      differenceAmount: null,
      reconciliationStatus: 'UNAVAILABLE',
      finalityStatus: 'PREVIEW',
      varianceReason: '无会计事实',
      sourceCount: 1,
      traceKey: 'operating-accounting:PROJECT:1',
    }
    const summary: FinancialSummaryRecord = {
      periodCode: '2026-07',
      finalityStatus: 'FINAL',
      businessPeriodStatus: 'CLOSED',
      accountingPeriodStatus: 'CLOSED',
      financialCloseStatus: 'CLOSED',
      revenueAmount: '1600.00',
      mainCostAmount: '0.00',
      periodExpenseAmount: '0.00',
      otherProfitLossAmount: '0.00',
      incomeTaxExpenseAmount: '0.00',
      operatingResultAmount: '1600.00',
      assetBalanceAmount: '12000.00',
      liabilityBalanceAmount: '300.00',
      equityBalanceAmount: '11700.00',
      trialBalanceStatus: 'MATCHED',
      bankReconciliationStatus: 'UNAVAILABLE',
      taxSummaryStatus: 'UNAVAILABLE',
      sourceCount: 5,
      traceKey: 'financial-summary:PERIOD:2026-07',
      analysisMode: 'LIVE',
      legalReport: false,
      disclaimer: '固定经营财务摘要不是资产负债表、利润表或现金流量表。',
    }
    const fetcher = vi.fn()
      .mockResolvedValueOnce(apiResponse(overview))
      .mockResolvedValueOnce(apiResponse({ summary: { projectCount: 1, operatingGrossProfitAmount: '1600.00', sourceCount: 1 }, items: [projectRow], page: 1, pageSize: 10, total: 1, totalPages: 1 }))
      .mockResolvedValueOnce(apiResponse(detail))
      .mockResolvedValueOnce(apiResponse({ summary: { contractAmount: '2000.00', sourceCount: 1 }, items: [collectionRow], page: 1, pageSize: 10, total: 1, totalPages: 1 }))
      .mockResolvedValueOnce(apiResponse({ summary: { orderAmount: '800.00', sourceCount: 1 }, items: [procurementRow], page: 1, pageSize: 10, total: 1, totalPages: 1 }))
      .mockResolvedValueOnce(apiResponse({ summary: inventorySummary, items: [inventoryRow], page: 1, pageSize: 10, total: 1, totalPages: 1 }))
      .mockResolvedValueOnce(apiResponse({ summary: { balanceAmount: '300.00', sourceCount: 1 }, items: [receivableRow], page: 1, pageSize: 10, total: 1, totalPages: 1 }))
      .mockResolvedValueOnce(apiResponse({ summary: { operatingProfitAmount: '1600.00', differenceAmount: null, sourceCount: 1 }, items: [accountingRow], page: 1, pageSize: 10, total: 1, totalPages: 1 }))
      .mockResolvedValueOnce(apiResponse(summary))
    const api = createBusinessReportingApi({ fetcher })

    expect((await api.operatingFinanceOverview.get({})).procurementVarianceAmount).toBeNull()
    const projectProfit = await api.projectProfit.list({ page: 1, pageSize: 10 })
    expect(projectProfit.items[0].operatingGrossProfitRate).toBeNull()
    expect(projectProfit.items[0].accountingCostAmount).toBeNull()
    expect((await api.projectProfit.detail.get(1, {})).varianceReasons[0].amount).toBeNull()
    expect((await api.contractCollection.list({ page: 1, pageSize: 10 })).items[0].collectionRate).toBe('75.00%')
    expect((await api.procurementVariance.list({ page: 1, pageSize: 10 })).items[0].invoiceReceiptDifferenceAmount).toBe('-50.00')
    const inventoryCapital = await api.inventoryCapital.list({ page: 1, pageSize: 10 })
    expect(inventoryCapital.summary.knownValuationAmount).toBeNull()
    expect(inventoryCapital.summary.unknownValuationQuantity).toBe('5.000')
    expect(inventoryCapital.summary.completenessStatus).toBe('INCOMPLETE')
    expect(inventoryCapital.items[0].amount).toBeNull()
    expect(inventoryCapital.items[0].unknownValuationQuantity).toBe('5.000')
    expect(inventoryCapital.items[0].completenessStatus).toBe('INCOMPLETE')
    expect((await api.receivablePayable.list({ page: 1, pageSize: 10 })).items[0].aging1To30Amount).toBe('300.00')
    expect((await api.operatingAccountingReconciliation.list({ page: 1, pageSize: 10 })).items[0].accountingProfitAmount).toBeNull()
    const financialSummary = await api.financialSummary.get({})
    expect(financialSummary.operatingResultAmount).toBe('1600.00')
    expect(financialSummary.analysisMode).toBe('LIVE')
    expect(financialSummary.legalReport).toBe(false)
    expect(financialSummary.disclaimer).toContain('不是资产负债表')
  })
})
