import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from './accountPermissionApi'
import {
  createProcurementApi,
  type PurchaseReceiptLinePayload,
  type PurchaseOrderLineRecord,
  type PurchaseOrderPayload,
  type PurchaseOrderSummaryRecord,
  type PurchaseReceiptLineRecord,
  type PurchaseReceiptPayload,
} from './procurementApi'
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

function apiFailure(status = 409) {
  return {
    ok: false,
    status,
    json: async () => ({
      success: false,
      code: 'PROCUREMENT_RECEIPT_EXCEEDS_ORDER',
      message: '采购入库数量超过订单未入库数量',
      data: null,
      traceId: 'trace-procurement',
    }),
  }
}

describe('采购 API', () => {
  const procurementInTransitTypeContract = {
    orderSummaryHasInTransitFields: true as AssertTrue<
      'inTransitQuantity' extends keyof PurchaseOrderSummaryRecord
        ? 'inTransitStatus' extends keyof PurchaseOrderSummaryRecord
          ? 'inTransitStatusName' extends keyof PurchaseOrderSummaryRecord
            ? true
            : false
          : false
        : false
    >,
    orderLineHasInTransitFields: true as AssertTrue<
      'inTransitQuantity' extends keyof PurchaseOrderLineRecord
        ? 'inTransitStatus' extends keyof PurchaseOrderLineRecord
          ? 'inTransitStatusName' extends keyof PurchaseOrderLineRecord
            ? true
            : false
          : false
        : false
    >,
    receiptLineHasInTransitFields: true as AssertTrue<
      'inTransitQuantity' extends keyof PurchaseReceiptLineRecord
        ? 'inTransitStatus' extends keyof PurchaseReceiptLineRecord
          ? 'inTransitStatusName' extends keyof PurchaseReceiptLineRecord
            ? true
            : false
          : false
        : false
    >,
    receiptPayloadLineAcceptsTrackingAllocations: true as AssertTrue<
      PurchaseReceiptLinePayload extends { trackingAllocations?: InventoryTrackingAllocationPayload[] } ? true : false
    >,
  }

  it('声明采购在途字段类型契约', () => {
    expect(procurementInTransitTypeContract).toMatchObject({
      orderSummaryHasInTransitFields: true,
      orderLineHasInTransitFields: true,
      receiptLineHasInTransitFields: true,
      receiptPayloadLineAcceptsTrackingAllocations: true,
    })
  })

  it('按查询条件分页获取采购订单并过滤空查询值', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 2, pageSize: 50 }))
    const api = createProcurementApi({ fetcher })

    await api.orders.list({
      keyword: '供应商',
      supplierId: 8,
      status: 'CONFIRMED',
      dateFrom: '2026-07-01',
      dateTo: '',
      expectedDateFrom: undefined,
      expectedDateTo: '2026-07-10',
      page: 2,
      pageSize: 50,
    })

    expect(fetcher).toHaveBeenCalledWith(
      '/api/admin/procurement/orders?keyword=%E4%BE%9B%E5%BA%94%E5%95%86&supplierId=8&status=CONFIRMED&dateFrom=2026-07-01&expectedDateTo=2026-07-10&page=2&pageSize=50',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
  })

  it('按查询条件分页获取采购入库并过滤空查询值', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
    const api = createProcurementApi({ fetcher })

    await api.receipts.list({
      keyword: 'PR',
      supplierId: 8,
      warehouseId: 3,
      status: 'DRAFT',
      dateFrom: '2026-07-01',
      dateTo: null,
      orderId: 12,
      page: 1,
      pageSize: 20,
    })

    expect(fetcher).toHaveBeenCalledWith(
      '/api/admin/procurement/receipts?keyword=PR&supplierId=8&warehouseId=3&status=DRAFT&dateFrom=2026-07-01&orderId=12&page=1&pageSize=20',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
  })

  it('按标识获取采购订单和采购入库详情', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({ id: 9, orderNo: 'PO-1' }))
      .mockResolvedValueOnce(apiResponse({ id: 10, receiptNo: 'PR-1' }))
    const api = createProcurementApi({ fetcher })

    await api.orders.get(9)
    await api.receipts.get(10)

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/admin/procurement/orders/9', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/procurement/receipts/10', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
  })

  it('写操作先获取 CSRF，业务数量和单价使用字符串，无 body 操作不发送空 JSON', async () => {
    const fetcher = vi.fn()
    const csrfTokens = [
      'csrf-create-order',
      'csrf-update-order',
      'csrf-confirm-order',
      'csrf-cancel-order',
      'csrf-close-order',
      'csrf-create-receipt',
      'csrf-update-receipt',
      'csrf-post-receipt',
    ]
    csrfTokens.forEach((token) => {
      fetcher.mockResolvedValueOnce(apiResponse({ token, headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      fetcher.mockResolvedValueOnce(apiResponse({ id: token }))
    })
    const api = createProcurementApi({ fetcher })
    const orderPayload: PurchaseOrderPayload = {
      supplierId: 1,
      orderDate: '2026-07-04',
      expectedArrivalDate: '2026-07-10',
      remark: '采购订单',
      lines: [
        {
          lineNo: 1,
          materialId: 2,
          unitId: 3,
          quantity: '999999999999.999999',
          unitPrice: '123456789012.123456',
          expectedArrivalDate: '2026-07-10',
          remark: '采购订单行',
        },
      ],
    }
    const receiptPayload: PurchaseReceiptPayload = {
      warehouseId: 4,
      businessDate: '2026-07-05',
      remark: '采购入库',
      lines: [{
        lineNo: 1,
        orderLineId: 5,
        materialId: 2,
        unitId: 3,
        quantity: '1.500000',
        trackingAllocations: [{ batchNo: 'B-PR-001', quantity: '1.500000' }],
      }],
    }

    await api.orders.create(orderPayload)
    await api.orders.update(11, orderPayload)
    await api.orders.confirm(11)
    await api.orders.cancel(11)
    await api.orders.close(11)
    await api.receipts.create(11, receiptPayload)
    await api.receipts.update(12, receiptPayload)
    await api.receipts.post(12)

    expect(JSON.parse(fetcher.mock.calls[1][1].body as string).lines[0]).toMatchObject({
      quantity: '999999999999.999999',
      unitPrice: '123456789012.123456',
    })
    expect(JSON.parse(fetcher.mock.calls[11][1].body as string).lines[0]).toMatchObject({
      quantity: '1.500000',
      trackingAllocations: [{ batchNo: 'B-PR-001', quantity: '1.500000' }],
    })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/procurement/orders', {
      body: JSON.stringify(orderPayload),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-create-order',
      },
      method: 'POST',
    })
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/procurement/orders/11', {
      body: JSON.stringify(orderPayload),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-update-order',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/procurement/orders/11/confirm', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-confirm-order',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(8, '/api/admin/procurement/orders/11/cancel', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-cancel-order',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(10, '/api/admin/procurement/orders/11/close', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-close-order',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(12, '/api/admin/procurement/orders/11/receipts', {
      body: JSON.stringify(receiptPayload),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-create-receipt',
      },
      method: 'POST',
    })
    expect(fetcher).toHaveBeenNthCalledWith(14, '/api/admin/procurement/receipts/12', {
      body: JSON.stringify(receiptPayload),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-update-receipt',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(16, '/api/admin/procurement/receipts/12/post', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-post-receipt',
      },
      method: 'PUT',
    })
  })

  it('后端返回非成功 envelope 时抛出统一 API 错误', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiFailure())
    const api = createProcurementApi({ fetcher })
    const request = api.orders.list({ page: 1, pageSize: 20 })

    await expect(request).rejects.toMatchObject({
      code: 'PROCUREMENT_RECEIPT_EXCEEDS_ORDER',
      status: 409,
      traceId: 'trace-procurement',
    })
    await expect(request).rejects.toBeInstanceOf(AccountPermissionApiError)
  })
})
