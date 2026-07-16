import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from './accountPermissionApi'
import {
  createSalesFulfillmentApi,
  type SalesCreditProfilePayload,
  type SalesOrderDeliveryPlanReplacePayload,
  type SalesOrderChangeCreatePayload,
  type SalesQuoteCreatePayload,
} from './salesFulfillmentApi'

function apiResponse<T>(data: T, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => ({
      success: status >= 200 && status < 300,
      code: status >= 200 && status < 300 ? 'OK' : 'ERROR',
      message: status >= 200 && status < 300 ? '成功' : '失败',
      data,
      traceId: 'trace-025',
    }),
  } as Response
}

function apiFailure() {
  return {
    ok: false,
    status: 409,
    json: async () => ({
      success: false,
      code: 'SALES_VERSION_CONFLICT',
      message: '对象已被其他用户修改',
      data: null,
      traceId: 'trace-025',
    }),
  } as Response
}

function csrfResponse(token = 'csrf-token') {
  return apiResponse({ token, headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' })
}

describe('025 销售履约 API', () => {
  it('报价接口冻结路径、十进制字符串、版本和幂等请求体', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
      .mockResolvedValueOnce(apiResponse({ id: 9, quoteNo: 'SQ-001', allowedActions: ['SUBMIT_APPROVAL'], version: 3 }))
      .mockResolvedValueOnce(csrfResponse('csrf-create'))
      .mockResolvedValueOnce(apiResponse({ id: 10, quoteNo: 'SQ-002', version: 1 }))
      .mockResolvedValueOnce(csrfResponse('csrf-update'))
      .mockResolvedValueOnce(apiResponse({ id: 9, version: 4 }))
      .mockResolvedValueOnce(csrfResponse('csrf-submit'))
      .mockResolvedValueOnce(apiResponse({ id: 31, status: 'SUBMITTED' }))
      .mockResolvedValueOnce(csrfResponse('csrf-cancel'))
      .mockResolvedValueOnce(apiResponse({ id: 9, status: 'CANCELLED', version: 5 }))
      .mockResolvedValueOnce(csrfResponse('csrf-order'))
      .mockResolvedValueOnce(apiResponse({ id: 88, orderNo: 'SO-001' }))
      .mockResolvedValueOnce(csrfResponse('csrf-contract'))
      .mockResolvedValueOnce(apiResponse({ id: 55, contractNo: 'SC-001' }))
    const api = createSalesFulfillmentApi({ fetcher })
    const payload: SalesQuoteCreatePayload = {
      customerId: 1,
      projectId: 20,
      quoteDate: '2026-07-15',
      validUntil: '2026-08-15',
      deliveryCommitment: '30 天内交付',
      currency: 'CNY',
      priceMode: 'TAX_INCLUDED',
      defaultTaxRate: '0.130000',
      settlementMethod: 'MONTHLY',
      paymentTermDays: 30,
      remark: '项目报价',
      lines: [{
        lineNo: 1,
        materialId: 2,
        unitId: 3,
        quantity: '12.500000',
        untaxedUnitPrice: '100.000000',
        taxIncludedUnitPrice: '113.000000',
        taxRate: '0.130000',
        untaxedAmount: '1250.000000',
        taxAmount: '162.500000',
        taxIncludedAmount: '1412.500000',
        promisedDate: '2026-08-01',
      }],
    }

    await api.quotes.list({ keyword: 'SQ', customerId: 1, status: 'APPROVED', page: 1, pageSize: 20 })
    await api.quotes.get(9)
    await api.quotes.create(payload)
    await api.quotes.update(9, { ...payload, version: 3 })
    await api.quotes.submitApproval(9, { version: 3, reason: '报价确认', idempotencyKey: 'submit-key' })
    await api.quotes.cancel(9, { version: 4, reason: '客户取消', idempotencyKey: 'cancel-key' })
    await api.quotes.convertOrder(9, { version: 4, projectId: 20, contractId: 55, idempotencyKey: 'order-key' })
    await api.quotes.convertContract(9, { version: 4, projectId: 20, contractType: 'MAIN', idempotencyKey: 'contract-key' })

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/admin/sales/quotes?keyword=SQ&customerId=1&status=APPROVED&page=1&pageSize=20', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/sales/quotes/9', expect.objectContaining({ method: 'GET' }))
    expect(JSON.parse(fetcher.mock.calls[3][1].body as string).lines[0]).toMatchObject({
      quantity: '12.500000',
      untaxedUnitPrice: '100.000000',
      taxIncludedUnitPrice: '113.000000',
      taxRate: '0.130000',
      taxIncludedAmount: '1412.500000',
    })
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/sales/quotes', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-create' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/sales/quotes/9', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ ...payload, version: 3 }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(8, '/api/admin/sales/quotes/9/submit-approval', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 3, reason: '报价确认', idempotencyKey: 'submit-key' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(10, '/api/admin/sales/quotes/9/cancel', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 4, reason: '客户取消', idempotencyKey: 'cancel-key' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(12, '/api/admin/sales/quotes/9/convert-order', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 4, projectId: 20, contractId: 55, idempotencyKey: 'order-key' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(14, '/api/admin/sales/quotes/9/convert-contract', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 4, projectId: 20, contractType: 'MAIN', idempotencyKey: 'contract-key' }),
    }))
  })

  it('交付计划、订单变更、信用、项目履约和有效需求使用冻结接口', async () => {
    const fetcher = vi.fn()
    const csrfTokens = [
      'csrf-plan',
      'csrf-close-plan',
      'csrf-change',
      'csrf-update-change',
      'csrf-submit-change',
      'csrf-credit',
      'csrf-credit-override',
      'csrf-close-order',
      'csrf-short-close',
      'csrf-close-fulfillment',
    ]
    fetcher
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
      .mockResolvedValueOnce(apiResponse({ customerId: 8, creditRestricted: true, creditLimit: null }))
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
      .mockResolvedValueOnce(apiResponse({ status: 'OPEN', contractRestricted: true }))
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
    csrfTokens.forEach((token) => {
      fetcher.mockResolvedValueOnce(csrfResponse(token))
      fetcher.mockResolvedValueOnce(apiResponse({ id: token, version: 2 }))
    })
    const api = createSalesFulfillmentApi({ fetcher })
    const planPayload: SalesOrderDeliveryPlanReplacePayload = {
      version: 3,
      idempotencyKey: 'plan-key',
      reason: '调整计划',
      lines: [{ orderLineId: 21, planDate: '2026-08-01', quantity: '5.000000' }],
    }
    const creditPayload: SalesCreditProfilePayload = {
      customerId: 8,
      creditLimit: '100000.000000',
      frozen: false,
      blockOverdue: true,
      reviewDate: '2026-07-15',
      remark: '年度额度',
    }

    await api.deliveryPlans.list({ customerId: 8, status: 'PLANNED', countedOnly: true, page: 1, pageSize: 20 })
    const orderPlans = await api.deliveryPlans.listByOrder(88)
    await api.orderChanges.list(88, { status: 'DRAFT', page: 1, pageSize: 20 })
    await api.creditProfiles.get(8)
    await api.creditProfiles.list({ keyword: '客户', page: 1, pageSize: 20 })
    await api.projectFulfillment.get(20)
    await api.effectiveDemands.list({ projectId: 20, countedOnly: false, page: 1, pageSize: 20 })
    await api.effectiveDemands.list({ projectId: 20, page: 1, pageSize: 20 })
    await api.deliveryPlans.replaceForOrder(88, planPayload)
    await api.deliveryPlans.close(88, 90, { version: 1, reason: '客户取消', idempotencyKey: 'close-plan-key' })
    const changePayload: SalesOrderChangeCreatePayload = {
      version: 3,
      idempotencyKey: 'change-key',
      reason: '客户追加数量',
      lines: [{ orderLineId: 21, targetQuantity: '8.000000', taxIncludedUnitPrice: '113.000000', taxRate: '0.130000', plannedDate: '2026-08-10' }],
    }
    await api.orderChanges.create(88, changePayload)
    await api.orderChanges.update(91, { ...changePayload, version: 4, idempotencyKey: 'update-change-key' })
    await api.orderChanges.submitApproval(91, { version: 1, reason: '提交审批', idempotencyKey: 'submit-change-key' })
    await api.creditProfiles.upsert(8, creditPayload)
    await api.orders.submitCreditOverride(88, { version: 3, reason: '客户临时超限', idempotencyKey: 'credit-override-key' })
    await api.orders.close(88, { version: 3, reason: '履约完成', idempotencyKey: 'close-order-key' })
    await api.orders.submitShortClose(88, { version: 3, reason: '客户接受短交', idempotencyKey: 'short-close-key' })
    await api.projectFulfillment.close(20, { version: 6, reason: '项目销售履约完成', idempotencyKey: 'fulfillment-key' })

    expect(orderPlans).toEqual({ items: [], total: 0, page: 1, pageSize: 20 })
    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/admin/sales/delivery-plans?customerId=8&status=PLANNED&countedOnly=true&page=1&pageSize=20', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/sales/orders/88/delivery-plans', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(3, '/api/admin/sales/orders/88/changes?status=DRAFT&page=1&pageSize=20', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/sales/credit-profiles/8', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(5, '/api/admin/sales/credit-profiles?keyword=%E5%AE%A2%E6%88%B7&page=1&pageSize=20', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/sales-projects/20/fulfillment', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(7, '/api/admin/sales/effective-demands?projectId=20&countedOnly=false&page=1&pageSize=20', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(8, '/api/admin/sales/effective-demands?projectId=20&countedOnly=true&page=1&pageSize=20', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(10, '/api/admin/sales/orders/88/delivery-plans', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify(planPayload),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(12, '/api/admin/sales/orders/88/delivery-plans/90/close', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ version: 1, reason: '客户取消', idempotencyKey: 'close-plan-key' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(14, '/api/admin/sales/orders/88/changes', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify(changePayload),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(16, '/api/admin/sales/order-changes/91', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ ...changePayload, version: 4, idempotencyKey: 'update-change-key' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(18, '/api/admin/sales/order-changes/91/submit-approval', expect.objectContaining({ method: 'POST' }))
    expect(fetcher).toHaveBeenNthCalledWith(20, '/api/admin/sales/credit-profiles', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify(creditPayload),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(22, '/api/admin/sales/orders/88/submit-credit-override', expect.objectContaining({ method: 'POST' }))
    expect(fetcher).toHaveBeenNthCalledWith(24, '/api/admin/sales/orders/88/close', expect.objectContaining({ method: 'PUT' }))
    expect(fetcher).toHaveBeenNthCalledWith(26, '/api/admin/sales/orders/88/submit-short-close', expect.objectContaining({ method: 'POST' }))
    expect(fetcher).toHaveBeenNthCalledWith(28, '/api/admin/sales-projects/20/close-sales-fulfillment', expect.objectContaining({ method: 'POST' }))
  })

  it('后端 409 envelope 抛出统一错误，前端页面可据此刷新不重放', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiFailure())
    const api = createSalesFulfillmentApi({ fetcher })
    const request = api.effectiveDemands.list({ countedOnly: true, page: 1, pageSize: 20 })

    await expect(request).rejects.toMatchObject({
      code: 'SALES_VERSION_CONFLICT',
      status: 409,
      traceId: 'trace-025',
    })
    await expect(request).rejects.toBeInstanceOf(AccountPermissionApiError)
  })
})
