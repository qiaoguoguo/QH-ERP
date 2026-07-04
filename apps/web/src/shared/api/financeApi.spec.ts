import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from './accountPermissionApi'
import {
  createFinanceApi,
  type PayablePayload,
  type PaymentPayload,
  type ReceivablePayload,
  type ReceiptPayload,
} from './financeApi'

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
      code: 'FINANCE_SOURCE_DUPLICATED',
      message: '来源明细已生成往来',
      data: null,
      traceId: 'trace-finance',
    }),
  }
}

describe('财务往来 API', () => {
  it('按查询条件分页获取应收、收款、应付、付款并过滤空查询值', async () => {
    const fetcher = vi.fn()
    Array.from({ length: 4 }).forEach(() => {
      fetcher.mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
    })
    const api = createFinanceApi({ fetcher })

    await api.receivables.list({
      keyword: '客户',
      customerId: 8,
      status: 'CONFIRMED',
      dateFrom: '2026-07-01',
      dateTo: '',
      dueDateFrom: undefined,
      dueDateTo: '2026-07-31',
      sourceNo: 'SS-1',
      page: 1,
      pageSize: 20,
    })
    await api.receipts.list({
      keyword: 'RC',
      customerId: 8,
      status: 'POSTED',
      dateFrom: '2026-07-01',
      dateTo: null,
      receivableId: 12,
      page: 1,
      pageSize: 20,
    })
    await api.payables.list({
      keyword: '供应商',
      supplierId: 9,
      status: 'PARTIALLY_PAID',
      dateFrom: '2026-07-01',
      dateTo: '',
      dueDateFrom: undefined,
      dueDateTo: '2026-07-31',
      sourceNo: 'PR-1',
      page: 1,
      pageSize: 20,
    })
    await api.payments.list({
      keyword: 'PM',
      supplierId: 9,
      status: 'DRAFT',
      dateFrom: '2026-07-01',
      dateTo: null,
      payableId: 13,
      page: 1,
      pageSize: 20,
    })

    expect(fetcher).toHaveBeenNthCalledWith(
      1,
      '/api/admin/finance/receivables?keyword=%E5%AE%A2%E6%88%B7&customerId=8&status=CONFIRMED&dateFrom=2026-07-01&dueDateTo=2026-07-31&sourceNo=SS-1&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      2,
      '/api/admin/finance/receipts?keyword=RC&customerId=8&status=POSTED&dateFrom=2026-07-01&receivableId=12&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      3,
      '/api/admin/finance/payables?keyword=%E4%BE%9B%E5%BA%94%E5%95%86&supplierId=9&status=PARTIALLY_PAID&dateFrom=2026-07-01&dueDateTo=2026-07-31&sourceNo=PR-1&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      4,
      '/api/admin/finance/payments?keyword=PM&supplierId=9&status=DRAFT&dateFrom=2026-07-01&payableId=13&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
  })

  it('按查询条件分页获取应收和应付候选来源', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
    const api = createFinanceApi({ fetcher })

    await api.sources.receivableCandidates.list({
      keyword: 'SO',
      customerId: 8,
      dateFrom: '2026-07-01',
      dateTo: '',
      settlementGenerated: false,
      page: 1,
      pageSize: 20,
    })
    await api.sources.payableCandidates.list({
      keyword: 'PO',
      supplierId: 9,
      dateFrom: '2026-07-01',
      dateTo: null,
      settlementGenerated: false,
      page: 1,
      pageSize: 20,
    })

    expect(fetcher).toHaveBeenNthCalledWith(
      1,
      '/api/admin/finance/receivable-sources?keyword=SO&customerId=8&dateFrom=2026-07-01&settlementGenerated=false&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      2,
      '/api/admin/finance/payable-sources?keyword=PO&supplierId=9&dateFrom=2026-07-01&settlementGenerated=false&page=1&pageSize=20',
      { credentials: 'include', headers: { Accept: 'application/json' }, method: 'GET' },
    )
  })

  it('按标识获取四类详情', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(apiResponse({ id: 1, receivableNo: 'AR-1' }))
      .mockResolvedValueOnce(apiResponse({ id: 2, receiptNo: 'RC-1' }))
      .mockResolvedValueOnce(apiResponse({ id: 3, payableNo: 'AP-1' }))
      .mockResolvedValueOnce(apiResponse({ id: 4, paymentNo: 'PM-1' }))
    const api = createFinanceApi({ fetcher })

    await api.receivables.get(1)
    await api.receipts.get(2)
    await api.payables.get(3)
    await api.payments.get(4)

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/admin/finance/receivables/1', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/finance/receipts/2', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
    expect(fetcher).toHaveBeenNthCalledWith(3, '/api/admin/finance/payables/3', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/finance/payments/4', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
  })

  it('写操作先获取 CSRF，金额 payload 使用字符串，无 body 操作不发送空 JSON', async () => {
    const fetcher = vi.fn()
    const csrfTokens = [
      'csrf-create-receivable',
      'csrf-update-receivable',
      'csrf-confirm-receivable',
      'csrf-cancel-receivable',
      'csrf-close-receivable',
      'csrf-create-receipt',
      'csrf-update-receipt',
      'csrf-post-receipt',
      'csrf-cancel-receipt',
      'csrf-create-payable',
      'csrf-update-payable',
      'csrf-confirm-payable',
      'csrf-cancel-payable',
      'csrf-close-payable',
      'csrf-create-payment',
      'csrf-update-payment',
      'csrf-post-payment',
      'csrf-cancel-payment',
    ]
    csrfTokens.forEach((token) => {
      fetcher.mockResolvedValueOnce(apiResponse({ token, headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      fetcher.mockResolvedValueOnce(apiResponse({ id: token }))
    })
    const api = createFinanceApi({ fetcher })
    const receivablePayload: ReceivablePayload = {
      sourceType: 'SALES_SHIPMENT',
      sourceId: 1,
      dueDate: '2026-07-31',
      remark: '应收',
    }
    const receiptPayload: ReceiptPayload = {
      receiptDate: '2026-07-10',
      amount: '123456789012.12',
      method: 'BANK_TRANSFER',
      remark: '收款',
    }
    const payablePayload: PayablePayload = {
      sourceType: 'PURCHASE_RECEIPT',
      sourceId: 2,
      dueDate: '2026-07-31',
      remark: '应付',
    }
    const paymentPayload: PaymentPayload = {
      paymentDate: '2026-07-11',
      amount: '99.99',
      method: 'BANK_TRANSFER',
      remark: '付款',
    }

    await api.receivables.create(receivablePayload)
    await api.receivables.update(11, { dueDate: '2026-08-01', remark: '更新应收' })
    await api.receivables.confirm(11)
    await api.receivables.cancel(11)
    await api.receivables.close(11)
    await api.receipts.create(11, receiptPayload)
    await api.receipts.update(12, receiptPayload)
    await api.receipts.post(12)
    await api.receipts.cancel(12)
    await api.payables.create(payablePayload)
    await api.payables.update(21, { dueDate: '2026-08-02', remark: '更新应付' })
    await api.payables.confirm(21)
    await api.payables.cancel(21)
    await api.payables.close(21)
    await api.payments.create(21, paymentPayload)
    await api.payments.update(22, paymentPayload)
    await api.payments.post(22)
    await api.payments.cancel(22)

    expect(JSON.parse(fetcher.mock.calls[11][1].body as string).amount).toBe('123456789012.12')
    expect(JSON.parse(fetcher.mock.calls[29][1].body as string).amount).toBe('99.99')
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/finance/receivables', expect.objectContaining({
      body: JSON.stringify(receivablePayload),
      method: 'POST',
      headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-create-receivable' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/finance/receivables/11/confirm', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-confirm-receivable',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(12, '/api/admin/finance/receivables/11/receipts', expect.objectContaining({
      body: JSON.stringify(receiptPayload),
      method: 'POST',
      headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-create-receipt' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(20, '/api/admin/finance/payables', expect.objectContaining({
      body: JSON.stringify(payablePayload),
      method: 'POST',
      headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-create-payable' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(30, '/api/admin/finance/payables/21/payments', expect.objectContaining({
      body: JSON.stringify(paymentPayload),
      method: 'POST',
      headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-create-payment' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(36, '/api/admin/finance/payments/22/cancel', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-cancel-payment',
      },
      method: 'PUT',
    })
  })

  it('后端返回非成功 envelope 时抛出统一 API 错误', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiFailure())
    const api = createFinanceApi({ fetcher })
    const request = api.receivables.list({ page: 1, pageSize: 20 })

    await expect(request).rejects.toMatchObject({
      code: 'FINANCE_SOURCE_DUPLICATED',
      status: 409,
      traceId: 'trace-finance',
    })
    await expect(request).rejects.toBeInstanceOf(AccountPermissionApiError)
  })
})
