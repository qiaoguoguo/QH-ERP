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
    receiptLineReturnsTrackingAllocations: true as AssertTrue<
      PurchaseReceiptLineRecord extends {
        trackingMethod?: unknown
        trackingMethodName?: unknown
        trackingAllocations?: InventoryTrackingAllocationPayload[]
      } ? true : false
    >,
  }

  it('声明采购在途字段类型契约', () => {
    expect(procurementInTransitTypeContract).toMatchObject({
      orderSummaryHasInTransitFields: true,
      orderLineHasInTransitFields: true,
      receiptLineHasInTransitFields: true,
      receiptPayloadLineAcceptsTrackingAllocations: true,
      receiptLineReturnsTrackingAllocations: true,
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
    await api.orders.update(11, { ...orderPayload, version: 7 })
    await api.orders.confirm(11, { version: 8, idempotencyKey: 'order-confirm-11' })
    await api.orders.cancel(11, { version: 9, idempotencyKey: 'order-cancel-11' })
    await api.orders.close(11, { version: 10, reason: '供应计划调整', idempotencyKey: 'order-close-11' })
    await api.receipts.create(11, receiptPayload)
    await api.receipts.update(12, { ...receiptPayload, version: 12 })
    await api.receipts.post(12, { version: 13, idempotencyKey: 'receipt-post-12' })

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
      body: JSON.stringify({ ...orderPayload, version: 7 }),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-update-order',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/procurement/orders/11/confirm', {
      body: JSON.stringify({ version: 8, idempotencyKey: 'order-confirm-11' }),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-confirm-order',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(8, '/api/admin/procurement/orders/11/cancel', {
      body: JSON.stringify({ version: 9, idempotencyKey: 'order-cancel-11' }),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-cancel-order',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(10, '/api/admin/procurement/orders/11/close', {
      body: JSON.stringify({ version: 10, reason: '供应计划调整', idempotencyKey: 'order-close-11' }),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
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
      body: JSON.stringify({ ...receiptPayload, version: 12 }),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-update-receipt',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(16, '/api/admin/procurement/receipts/12/post', {
      body: JSON.stringify({ version: 13, idempotencyKey: 'receipt-post-12' }),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
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

  it('024 采购深化资源使用冻结接口路径并过滤空查询值', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValue(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
    const api = createProcurementApi({ fetcher }) as any

    await api.requisitions.list({
      keyword: '项目专采',
      procurementMode: 'PROJECT',
      projectId: 30,
      status: 'APPROVED',
      approvalStatus: '',
      page: 1,
      pageSize: 20,
    })
    await api.inquiries.list({ keyword: '询价', procurementMode: 'PUBLIC', status: 'RELEASED', page: 2, pageSize: 10 })
    await api.quotes.list(77, { status: 'VALID', supplierId: null, page: 1, pageSize: 50 })
    await api.priceAgreements.list({ keyword: '协议', procurementMode: 'PROJECT', projectId: 30, status: 'ACTIVE', page: 1, pageSize: 20 })
    await api.schedules.list(11, { status: 'PLANNED', expectedDateFrom: '2026-07-10', expectedDateTo: '', page: 1, pageSize: 20 })
    await api.effectiveSupplies.list({ projectId: 30, materialId: 5, procurementMode: 'PROJECT', countedOnly: true, page: 1, pageSize: 20 })

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/admin/procurement/requisitions?keyword=%E9%A1%B9%E7%9B%AE%E4%B8%93%E9%87%87&procurementMode=PROJECT&projectId=30&status=APPROVED&page=1&pageSize=20', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/procurement/inquiries?keyword=%E8%AF%A2%E4%BB%B7&procurementMode=PUBLIC&status=RELEASED&page=2&pageSize=10', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(3, '/api/admin/procurement/inquiries/77/quotes?status=VALID&page=1&pageSize=50', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/procurement/price-agreements?keyword=%E5%8D%8F%E8%AE%AE&procurementMode=PROJECT&projectId=30&status=ACTIVE&page=1&pageSize=20', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(5, '/api/admin/procurement/orders/11/schedules?status=PLANNED&expectedDateFrom=2026-07-10&page=1&pageSize=20', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/procurement/effective-supplies?projectId=30&materialId=5&procurementMode=PROJECT&countedOnly=true&page=1&pageSize=20', expect.objectContaining({ method: 'GET' }))
  })

  it('024 写操作保持十进制字符串并通过 allowedActions 对应动作路径提交', async () => {
    const fetcher = vi.fn()
    const tokens = [
      'csrf-requisition-create',
      'csrf-requisition-submit-approval',
      'csrf-inquiry-release',
      'csrf-quote-select',
      'csrf-agreement-submit-activation',
      'csrf-order-exception',
      'csrf-schedule-replace',
      'csrf-schedule-close',
    ]
    tokens.forEach((token) => {
      fetcher.mockResolvedValueOnce(apiResponse({ token, headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      fetcher.mockResolvedValueOnce(apiResponse({ id: token }))
    })
    const api = createProcurementApi({ fetcher }) as any

    await api.requisitions.create({
      procurementMode: 'PROJECT',
      projectId: 30,
      requiredDate: '2026-07-20',
      lines: [{ lineNo: 10, materialId: 5, unitId: 2, quantity: '999999999999.123456', taxRate: '0.130000' }],
    })
    await api.requisitions.submitApproval(100, {
      version: 3,
      reason: '项目专采请购审批',
      idempotencyKey: 'req-submit-100',
    })
    await api.inquiries.release(200, { version: 5, idempotencyKey: 'inquiry-release-200' })
    await api.quotes.select(200, 300, {
      version: 6,
      reason: '最低有效报价',
      selectedQuantity: '12.500000',
      idempotencyKey: 'quote-select-300',
    })
    await api.priceAgreements.submitActivation(400, {
      version: 4,
      reason: '价格协议激活',
      idempotencyKey: 'agreement-activation-400',
    })
    await api.orders.submitException(500, {
      version: 7,
      reason: '公共直采例外',
      deviationAmount: '0.010000',
      idempotencyKey: 'order-exception-500',
    })
    await api.schedules.replace(500, {
      version: 9,
      idempotencyKey: 'schedule-replace-500',
      lines: [{
        orderLineId: 900,
        scheduleSeq: 10,
        expectedArrivalDate: '2026-07-22',
        plannedQuantity: '12.500000',
        remark: '首批到货',
      }],
    })
    await api.schedules.close(500, 600, {
      version: 8,
      closeReason: '项目设计变更，剩余数量不再采购',
      idempotencyKey: 'schedule-close-600',
    })

    expect(JSON.parse(fetcher.mock.calls[1][1].body as string).lines[0]).toMatchObject({
      quantity: '999999999999.123456',
      taxRate: '0.130000',
    })
    expect(JSON.parse(fetcher.mock.calls[7][1].body as string)).toMatchObject({
      version: 6,
      reason: '最低有效报价',
      selectedQuantity: '12.500000',
      idempotencyKey: 'quote-select-300',
    })
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/procurement/requisitions', expect.objectContaining({ method: 'POST' }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/procurement/requisitions/100/submit-approval', expect.objectContaining({ method: 'POST' }))
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/procurement/inquiries/200/release', expect.objectContaining({
      body: JSON.stringify({ version: 5, idempotencyKey: 'inquiry-release-200' }),
      method: 'PUT',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(8, '/api/admin/procurement/inquiries/200/quotes/300/select', expect.objectContaining({ method: 'PUT' }))
    expect(fetcher).toHaveBeenNthCalledWith(10, '/api/admin/procurement/price-agreements/400/submit-activation', expect.objectContaining({ method: 'POST' }))
    expect(fetcher).toHaveBeenNthCalledWith(12, '/api/admin/procurement/orders/500/submit-exception', expect.objectContaining({
      body: JSON.stringify({
        version: 7,
        reason: '公共直采例外',
        deviationAmount: '0.010000',
        idempotencyKey: 'order-exception-500',
      }),
      method: 'POST',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(14, '/api/admin/procurement/orders/500/schedules', expect.objectContaining({
      body: JSON.stringify({
        version: 9,
        idempotencyKey: 'schedule-replace-500',
        lines: [{
          orderLineId: 900,
          scheduleSeq: 10,
          expectedArrivalDate: '2026-07-22',
          plannedQuantity: '12.500000',
          remark: '首批到货',
        }],
      }),
      method: 'PUT',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(16, '/api/admin/procurement/orders/500/schedules/600/close', expect.objectContaining({
      body: JSON.stringify({
        version: 8,
        closeReason: '项目设计变更，剩余数量不再采购',
        idempotencyKey: 'schedule-close-600',
      }),
      method: 'PUT',
    }))
  })

  it('采购业务状态动作缺少非空幂等键时拒绝发送', async () => {
    const fetcher = vi.fn()
    const api = createProcurementApi({ fetcher }) as any

    await expect(api.orders.confirm(11, { version: 8 })).rejects.toThrow('幂等键不能为空')
    await expect(api.orders.confirm(11, { version: 8, idempotencyKey: '' })).rejects.toThrow('幂等键不能为空')
    expect(fetcher).not.toHaveBeenCalled()
  })
})
