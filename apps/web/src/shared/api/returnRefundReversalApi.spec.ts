import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from './accountPermissionApi'
import {
  createReturnRefundReversalApi,
  type PurchaseReturnDetail,
  type PurchaseReturnPayload,
  type ReversalDocumentPayload,
  type SalesReturnDetail,
} from './returnRefundReversalApi'

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

function apiFailure(status = 409) {
  return {
    ok: false,
    status,
    json: async () => ({
      success: false,
      code: 'REVERSAL_QUANTITY_EXCEEDS_AVAILABLE',
      message: '退货数量超过可退数量',
      data: null,
      traceId: 'trace-reversal',
    }),
  }
}

const salesReturnDetail: SalesReturnDetail = {
  id: 1,
  returnNo: 'SR202607050001',
  customerId: 8,
  customerName: '示例客户',
  warehouseId: 2,
  warehouseName: '成品仓',
  businessDate: '2026-07-05',
  status: 'DRAFT',
  totalQuantity: '2.000000',
  totalAmount: '100.00',
  source: {
    sourceType: 'SALES_SHIPMENT',
    sourceId: 10,
    sourceNo: 'SS202607050001',
    canViewSource: true,
    restricted: false,
    resourceRouteName: 'sales-shipment-detail',
    resourceRouteParams: { id: 10 },
  },
  createdAt: '2026-07-05T10:00:00+08:00',
  updatedAt: '2026-07-05T10:00:00+08:00',
  clientRequestId: 'sales-return-client-1',
  remark: '客户退货',
  lines: [
    {
      id: 11,
      lineNo: 10,
      sourceLineId: 101,
      materialId: 31,
      materialCode: 'FG-001',
      materialName: '示例成品',
      unitId: 1,
      unitName: '件',
      returnedQuantityBefore: '0.000000',
      returnableQuantityBefore: '10.000000',
      quantity: '2.000000',
      unitPrice: '50.00',
      amount: '100.00',
      reason: '客户退回',
      source: {
        sourceType: 'SALES_SHIPMENT_LINE',
        sourceId: 10,
        sourceLineId: 101,
        sourceNo: 'SS202607050001',
        lineNo: 10,
        quantity: '10.000000',
        amount: '500.00',
        canViewSource: true,
        restricted: false,
      },
    },
  ],
  traces: [],
}

const purchaseReturnDetail: PurchaseReturnDetail = {
  id: 2,
  returnNo: 'PR202607050001',
  supplierId: 9,
  supplierName: '示例供应商',
  warehouseId: 3,
  warehouseName: '原料仓',
  businessDate: '2026-07-05',
  status: 'DRAFT',
  totalQuantity: '1.500000',
  totalAmount: '120.00',
  source: {
    sourceType: 'PURCHASE_RECEIPT',
    sourceId: 20,
    sourceNo: 'RC202607050001',
    canViewSource: true,
    restricted: false,
    resourceRouteName: 'procurement-receipt-detail',
    resourceRouteParams: { id: 20 },
  },
  createdAt: '2026-07-05T11:00:00+08:00',
  updatedAt: '2026-07-05T11:00:00+08:00',
  clientRequestId: 'purchase-return-client-1',
  remark: '来料退回',
  lines: [
    {
      id: 21,
      lineNo: 10,
      sourceLineId: 201,
      materialId: 41,
      materialCode: 'RM-001',
      materialName: '示例原料',
      unitId: 1,
      unitName: 'kg',
      returnedQuantityBefore: '0.000000',
      returnableQuantityBefore: '8.000000',
      quantity: '1.500000',
      unitPrice: '80.00',
      amount: '120.00',
      reason: '来料退回',
      source: {
        sourceType: 'PURCHASE_RECEIPT_LINE',
        sourceId: 20,
        sourceLineId: 201,
        sourceNo: 'RC202607050001',
        lineNo: 10,
        quantity: '8.000000',
        amount: '640.00',
        canViewSource: true,
        restricted: false,
      },
    },
  ],
  traces: [],
}

describe('退货退款与反冲 API', () => {
  it('按查询条件获取销售退货、候选来源和追溯，并过滤空查询值', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
      .mockResolvedValueOnce(apiResponse([]))
    const api = createReturnRefundReversalApi({ fetcher })

    await api.salesReturns.list({
      keyword: 'SR',
      customerId: 8,
      warehouseId: '',
      status: 'DRAFT',
      dateFrom: '2026-07-01',
      dateTo: null,
      page: 1,
      pageSize: 20,
    })
    await api.salesReturnSources.list({
      keyword: 'SS',
      customerId: '',
      warehouseId: 2,
      dateFrom: '2026-07-01',
      dateTo: '',
      page: 1,
      pageSize: 20,
    })
    await api.traces.list({
      sourceType: 'SALES_RETURN',
      sourceId: 1,
      sourceLineId: '',
      direction: 'REVERSE_TO_SOURCE',
      includeRestricted: true,
    })

    expect(fetcher).toHaveBeenNthCalledWith(
      1,
      '/api/admin/sales/returns?keyword=SR&customerId=8&status=DRAFT&dateFrom=2026-07-01&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      2,
      '/api/admin/sales/return-sources?keyword=SS&warehouseId=2&dateFrom=2026-07-01&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      3,
      '/api/admin/reversal-traces?sourceType=SALES_RETURN&sourceId=1&direction=REVERSE_TO_SOURCE&includeRestricted=true',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
  })

  it('销售退货写操作先获取 CSRF，数量金额保持字符串，无 body 操作不发送空 JSON', async () => {
    const fetcher = vi.fn()
    const csrfTokens = ['create', 'update', 'post', 'cancel']
    csrfTokens.forEach((token) => {
      fetcher.mockResolvedValueOnce(apiResponse({ token: `csrf-${token}`, headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      fetcher.mockResolvedValueOnce(apiResponse(salesReturnDetail))
    })
    const api = createReturnRefundReversalApi({ fetcher })
    const payload: ReversalDocumentPayload = {
      sourceShipmentId: 10,
      businessDate: '2026-07-05',
      clientRequestId: 'sales-return-client-1',
      remark: '客户退货',
      lines: [{ sourceShipmentLineId: 101, quantity: '2.000000', reason: '客户退回' }],
    }

    await api.salesReturns.create(payload)
    await api.salesReturns.update(1, payload)
    await api.salesReturns.post(1)
    await api.salesReturns.cancel(1)

    expect(JSON.parse(fetcher.mock.calls[1][1].body as string).lines[0].quantity).toBe('2.000000')
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/sales/returns', expect.objectContaining({
      body: JSON.stringify(payload),
      headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-create' }),
      method: 'POST',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/sales/returns/1', expect.objectContaining({
      body: JSON.stringify(payload),
      headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-update' }),
      method: 'PUT',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/sales/returns/1/post', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-post',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(8, '/api/admin/sales/returns/1/cancel', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-cancel',
      },
      method: 'PUT',
    })
  })

  it('按查询条件获取采购退货和候选采购入库，并过滤空查询值', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
    const api = createReturnRefundReversalApi({ fetcher })

    await api.purchaseReturns.list({
      keyword: 'PR',
      supplierId: 9,
      warehouseId: '',
      status: 'DRAFT',
      dateFrom: '2026-07-01',
      dateTo: null,
      page: 1,
      pageSize: 20,
    })
    await api.purchaseReturnSources.list({
      keyword: 'RC',
      supplierId: '',
      warehouseId: 3,
      dateFrom: '2026-07-01',
      dateTo: '',
      page: 1,
      pageSize: 20,
    })

    expect(fetcher).toHaveBeenNthCalledWith(
      1,
      '/api/admin/procurement/returns?keyword=PR&supplierId=9&status=DRAFT&dateFrom=2026-07-01&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      2,
      '/api/admin/procurement/return-sources?keyword=RC&warehouseId=3&dateFrom=2026-07-01&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
  })

  it('采购退货写操作使用采购入库来源字段，数量金额保持字符串', async () => {
    const fetcher = vi.fn()
    const csrfTokens = ['create', 'update', 'post', 'cancel']
    csrfTokens.forEach((token) => {
      fetcher.mockResolvedValueOnce(apiResponse({ token: `csrf-${token}`, headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      fetcher.mockResolvedValueOnce(apiResponse(purchaseReturnDetail))
    })
    const api = createReturnRefundReversalApi({ fetcher })
    const payload: PurchaseReturnPayload = {
      sourceReceiptId: 20,
      businessDate: '2026-07-05',
      clientRequestId: 'purchase-return-client-1',
      remark: '来料退回',
      lines: [{ sourceReceiptLineId: 201, quantity: '1.500000', reason: '来料退回' }],
    }

    await api.purchaseReturns.create(payload)
    await api.purchaseReturns.update(2, payload)
    await api.purchaseReturns.post(2)
    await api.purchaseReturns.cancel(2)

    expect(JSON.parse(fetcher.mock.calls[1][1].body as string).sourceReceiptId).toBe(20)
    expect(JSON.parse(fetcher.mock.calls[1][1].body as string).lines[0].quantity).toBe('1.500000')
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/procurement/returns', expect.objectContaining({
      body: JSON.stringify(payload),
      headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-create' }),
      method: 'POST',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/procurement/returns/2', expect.objectContaining({
      body: JSON.stringify(payload),
      headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-update' }),
      method: 'PUT',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/procurement/returns/2/post', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-post',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(8, '/api/admin/procurement/returns/2/cancel', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-cancel',
      },
      method: 'PUT',
    })
  })

  it('按标识获取采购退货详情并保留来源受限结构', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({
      ...purchaseReturnDetail,
      source: {
        sourceType: 'PURCHASE_RECEIPT',
        canViewSource: false,
        restricted: true,
        restrictedMessage: '来源无查看权限',
      },
    }))
    const api = createReturnRefundReversalApi({ fetcher })

    const detail = await api.purchaseReturns.get(2)

    expect(fetcher).toHaveBeenCalledWith('/api/admin/procurement/returns/2', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
    expect(detail.source.canViewSource).toBe(false)
    expect(detail.source.sourceNo).toBeUndefined()
    expect(detail.source.resourceRouteName).toBeUndefined()
  })

  it('按标识获取销售退货详情并保留来源受限结构', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({
      ...salesReturnDetail,
      source: {
        sourceType: 'SALES_SHIPMENT',
        canViewSource: false,
        restricted: true,
        restrictedMessage: '来源无查看权限',
      },
    }))
    const api = createReturnRefundReversalApi({ fetcher })

    const detail = await api.salesReturns.get(1)

    expect(fetcher).toHaveBeenCalledWith('/api/admin/sales/returns/1', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
    expect(detail.source.canViewSource).toBe(false)
    expect(detail.source.sourceNo).toBeUndefined()
    expect(detail.source.resourceRouteName).toBeUndefined()
  })

  it('后端返回非成功 envelope 时抛出统一 API 错误', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiFailure())
    const api = createReturnRefundReversalApi({ fetcher })
    const request = api.salesReturns.list({ page: 1, pageSize: 20 })

    await expect(request).rejects.toMatchObject({
      code: 'REVERSAL_QUANTITY_EXCEEDS_AVAILABLE',
      status: 409,
      traceId: 'trace-reversal',
    })
    await expect(request).rejects.toBeInstanceOf(AccountPermissionApiError)
  })
})
