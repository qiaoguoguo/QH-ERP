import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from './accountPermissionApi'
import {
  createBusinessReportingApi,
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
})
