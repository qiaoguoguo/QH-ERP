import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from './accountPermissionApi'
import {
  createSalesApi,
  type SalesOrderLineRecord,
  type SalesOrderPayload,
  type SalesShipmentLineRecord,
  type SalesShipmentLinePayload,
  type SalesShipmentPayload,
} from './salesApi'
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
      code: 'SALES_SHIPMENT_EXCEEDS_ORDER',
      message: '出库数量超过订单未出库数量',
      data: null,
      traceId: 'trace-sales',
    }),
  }
}

describe('销售 API', () => {
  const salesAvailabilityTypeContract = {
    orderLineHasReservationWarehouseFields: true as AssertTrue<
      'reservationWarehouseId' extends keyof SalesOrderLineRecord
        ? 'reservationWarehouseName' extends keyof SalesOrderLineRecord
          ? true
          : false
        : false
    >,
    orderPayloadLineHasReservationWarehouse: true as AssertTrue<
      SalesOrderPayload['lines'][number] extends { reservationWarehouseId: unknown } ? true : false
    >,
    orderLineHasAvailabilityFields: true as AssertTrue<
      'reservedQuantity' extends keyof SalesOrderLineRecord
        ? 'occupiedQuantity' extends keyof SalesOrderLineRecord
          ? 'availableToPromiseQuantity' extends keyof SalesOrderLineRecord
            ? true
            : false
          : false
        : false
    >,
    shipmentLineHasAvailabilityFields: true as AssertTrue<
      'reservedQuantity' extends keyof SalesShipmentLineRecord
        ? 'occupiedQuantity' extends keyof SalesShipmentLineRecord
          ? 'availableToPromiseQuantity' extends keyof SalesShipmentLineRecord
            ? true
            : false
          : false
        : false
    >,
    shipmentLineHasReservationWarehouseFields: true as AssertTrue<
      'reservationWarehouseId' extends keyof SalesShipmentLineRecord
        ? 'reservationWarehouseName' extends keyof SalesShipmentLineRecord
          ? true
          : false
        : false
    >,
    shipmentPayloadLineAcceptsTrackingAllocations: true as AssertTrue<
      SalesShipmentLinePayload extends { trackingAllocations?: InventoryTrackingAllocationPayload[] } ? true : false
    >,
    shipmentLineReturnsTrackingAllocations: true as AssertTrue<
      SalesShipmentLineRecord extends {
        trackingMethod?: unknown
        trackingMethodName?: unknown
        trackingAllocations?: InventoryTrackingAllocationPayload[]
      } ? true : false
    >,
  }

  it('声明销售候选库存占用预留字段类型契约', () => {
    expect(salesAvailabilityTypeContract).toMatchObject({
      orderLineHasReservationWarehouseFields: true,
      orderPayloadLineHasReservationWarehouse: true,
      orderLineHasAvailabilityFields: true,
      shipmentLineHasAvailabilityFields: true,
      shipmentLineHasReservationWarehouseFields: true,
      shipmentPayloadLineAcceptsTrackingAllocations: true,
      shipmentLineReturnsTrackingAllocations: true,
    })
  })

  it('按查询条件分页获取销售订单并过滤空查询值', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 2, pageSize: 50 }))
    const api = createSalesApi({ fetcher })

    await api.orders.list({
      keyword: '客户',
      customerId: 8,
      status: 'CONFIRMED',
      dateFrom: '2026-07-01',
      dateTo: '',
      expectedDateFrom: undefined,
      expectedDateTo: '2026-07-10',
      page: 2,
      pageSize: 50,
    })

    expect(fetcher).toHaveBeenCalledWith(
      '/api/admin/sales/orders?keyword=%E5%AE%A2%E6%88%B7&customerId=8&status=CONFIRMED&dateFrom=2026-07-01&expectedDateTo=2026-07-10&page=2&pageSize=50',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
  })

  it('按查询条件分页获取销售出库并过滤空查询值', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
    const api = createSalesApi({ fetcher })

    await api.shipments.list({
      keyword: 'SO',
      customerId: 8,
      warehouseId: 3,
      status: 'DRAFT',
      dateFrom: '2026-07-01',
      dateTo: null,
      orderId: 12,
      page: 1,
      pageSize: 20,
    })

    expect(fetcher).toHaveBeenCalledWith(
      '/api/admin/sales/shipments?keyword=SO&customerId=8&warehouseId=3&status=DRAFT&dateFrom=2026-07-01&orderId=12&page=1&pageSize=20',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
  })

  it('按标识获取销售订单和销售出库详情', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({ id: 9, orderNo: 'SO-1' }))
      .mockResolvedValueOnce(apiResponse({ id: 10, shipmentNo: 'SS-1' }))
    const api = createSalesApi({ fetcher })

    await api.orders.get(9)
    await api.shipments.get(10)

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/admin/sales/orders/9', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/sales/shipments/10', {
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
      'csrf-create-shipment',
      'csrf-update-shipment',
      'csrf-post-shipment',
    ]
    csrfTokens.forEach((token) => {
      fetcher.mockResolvedValueOnce(apiResponse({ token, headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      fetcher.mockResolvedValueOnce(apiResponse({ id: token }))
    })
    const api = createSalesApi({ fetcher })
    const orderPayload: SalesOrderPayload = {
      customerId: 1,
      orderDate: '2026-07-04',
      expectedShipDate: '2026-07-10',
      remark: '销售订单',
      lines: [
        {
          lineNo: 1,
          materialId: 2,
          unitId: 3,
          reservationWarehouseId: 4,
          quantity: '999999999999.999999',
          unitPrice: '123456789012.123456',
          expectedShipDate: '2026-07-10',
          remark: '销售订单行',
        },
      ],
    }
    const shipmentPayload: SalesShipmentPayload = {
      warehouseId: 4,
      businessDate: '2026-07-05',
      remark: '销售出库',
      lines: [{
        lineNo: 1,
        orderLineId: 5,
        materialId: 2,
        unitId: 3,
        quantity: '1.500000',
        trackingAllocations: [{ batchId: 31, quantity: '1.500000' }],
      }],
    }

    await api.orders.create(orderPayload)
    await api.orders.update(11, orderPayload)
    await api.orders.confirm(11)
    await api.orders.cancel(11)
    await api.orders.close(11)
    await api.shipments.create(11, shipmentPayload)
    await api.shipments.update(12, shipmentPayload)
    await api.shipments.post(12)

    expect(JSON.parse(fetcher.mock.calls[1][1].body as string).lines[0]).toMatchObject({
      reservationWarehouseId: 4,
      quantity: '999999999999.999999',
      unitPrice: '123456789012.123456',
    })
    expect(JSON.parse(fetcher.mock.calls[11][1].body as string).lines[0]).toMatchObject({
      quantity: '1.500000',
      trackingAllocations: [{ batchId: 31, quantity: '1.500000' }],
    })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/sales/orders', {
      body: JSON.stringify(orderPayload),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-create-order',
      },
      method: 'POST',
    })
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/sales/orders/11', {
      body: JSON.stringify(orderPayload),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-update-order',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/sales/orders/11/confirm', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-confirm-order',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(8, '/api/admin/sales/orders/11/cancel', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-cancel-order',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(10, '/api/admin/sales/orders/11/close', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-close-order',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(12, '/api/admin/sales/orders/11/shipments', {
      body: JSON.stringify(shipmentPayload),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-create-shipment',
      },
      method: 'POST',
    })
    expect(fetcher).toHaveBeenNthCalledWith(14, '/api/admin/sales/shipments/12', {
      body: JSON.stringify(shipmentPayload),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-update-shipment',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(16, '/api/admin/sales/shipments/12/post', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-post-shipment',
      },
      method: 'PUT',
    })
  })

  it('后端返回非成功 envelope 时抛出统一 API 错误', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiFailure())
    const api = createSalesApi({ fetcher })
    const request = api.orders.list({ page: 1, pageSize: 20 })

    await expect(request).rejects.toMatchObject({
      code: 'SALES_SHIPMENT_EXCEEDS_ORDER',
      status: 409,
      traceId: 'trace-sales',
    })
    await expect(request).rejects.toBeInstanceOf(AccountPermissionApiError)
  })
})
