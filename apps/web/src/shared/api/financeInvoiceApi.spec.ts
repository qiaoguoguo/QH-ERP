import { describe, expect, it, vi } from 'vitest'
import { createFinanceInvoiceApi, type InvoiceType, type PurchaseInvoicePayload, type SalesInvoicePayload } from './financeInvoiceApi'
import type { FinanceAmount } from './financeStage028ApiCore'

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

describe('028 发票分域 API', () => {
  it('发票 DTO 使用冻结普通发票枚举和十进制字符串金额', () => {
    const typeChecks = {
      amountIsString: true as AssertTrue<FinanceAmount extends string ? true : false>,
      invoiceTypeIncludesGeneralVat: true as AssertTrue<'GENERAL_VAT' extends InvoiceType ? true : false>,
      invoiceTypeDoesNotExposeNormalVat: true as AssertTrue<'NORMAL_VAT' extends InvoiceType ? false : true>,
    }

    expect(typeChecks).toEqual({
      amountIsString: true,
      invoiceTypeIncludesGeneralVat: true,
      invoiceTypeDoesNotExposeNormalVat: true,
    })
  })

  it('按独立端点查询销售和采购发票及候选来源，并过滤空查询值', async () => {
    const fetcher = vi.fn()
    Array.from({ length: 4 }).forEach(() => {
      fetcher.mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
    })
    const api = createFinanceInvoiceApi({ fetcher })

    await api.salesInvoices.list({
      keyword: 'SI',
      customerId: 8,
      projectId: '',
      status: 'CONFIRMED',
      settlementStatus: 'PARTIALLY_SETTLED',
      invoiceType: 'SPECIAL_VAT',
      invoiceDateFrom: '2026-08-01',
      invoiceDateTo: '',
      externalInvoiceNo: 'EXT',
      sourceShipmentNo: 'SS',
      page: 1,
      pageSize: 20,
    })
    const salesCandidateParams = {
      keyword: 'SS',
      sourceId: 501,
      customerId: 8,
      ownershipType: 'PROJECT' as const,
      projectId: 18,
      contractNo: 'CT',
      orderNo: 'SO',
      shipmentDateFrom: '2026-08-01',
      shipmentDateTo: '',
      page: 1,
      pageSize: 50,
    }
    await api.salesInvoiceCandidates.list(salesCandidateParams)
    await api.purchaseInvoices.list({
      keyword: 'PI',
      supplierId: 9,
      sourceType: 'PURCHASE_RECEIPT',
      matchStatus: 'EXCEPTION',
      invoiceDateFrom: '2026-08-01',
      invoiceDateTo: null,
      page: 1,
      pageSize: 20,
    })
    const purchaseCandidateParams = {
      keyword: 'PO',
      sourceId: 601,
      supplierId: 9,
      ownershipType: 'PROJECT' as const,
      projectId: 18,
      sourceType: 'OUTSOURCING_RECEIPT' as const,
      businessDateFrom: '2026-08-01',
      businessDateTo: '',
      page: 1,
      pageSize: 50,
    }
    await api.purchaseInvoiceCandidates.list(purchaseCandidateParams)

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/admin/finance/sales-invoices?keyword=SI&customerId=8&status=CONFIRMED&settlementStatus=PARTIALLY_SETTLED&invoiceType=SPECIAL_VAT&invoiceDateFrom=2026-08-01&externalInvoiceNo=EXT&sourceShipmentNo=SS&page=1&pageSize=20', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/finance/sales-invoices/candidates?keyword=SS&sourceId=501&customerId=8&ownershipType=PROJECT&projectId=18&page=1&pageSize=50', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(3, '/api/admin/finance/purchase-invoices?keyword=PI&supplierId=9&sourceType=PURCHASE_RECEIPT&matchStatus=EXCEPTION&invoiceDateFrom=2026-08-01&page=1&pageSize=20', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/finance/purchase-invoices/candidates?keyword=PO&sourceType=OUTSOURCING_RECEIPT&sourceId=601&supplierId=9&ownershipType=PROJECT&projectId=18&page=1&pageSize=50', expect.objectContaining({ method: 'GET' }))
  })

  it('写操作携带十进制字符串、版本和幂等键，并调用匹配与确认端点', async () => {
    const fetcher = vi.fn()
    Array.from({ length: 8 }).forEach((_, index) => {
      fetcher.mockResolvedValueOnce(apiResponse({ token: `csrf-${index}`, headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      fetcher.mockResolvedValueOnce(apiResponse({ id: index + 1 }))
    })
    const api = createFinanceInvoiceApi({ fetcher })
    const salesPayload: SalesInvoicePayload = {
      invoiceDate: '2026-08-03',
      invoiceType: 'SPECIAL_VAT',
      externalInvoiceNo: 'EXT-SI',
      customerId: 8,
      ownershipType: 'PROJECT',
      projectId: 18,
      sourceLines: [{ sourceLineId: 1001, invoiceQuantity: '2.500000' }],
      remark: '销售发票',
      version: 0,
      idempotencyKey: 'idem-si',
    }
    const purchasePayload: PurchaseInvoicePayload = {
      invoiceDate: '2026-08-04',
      invoiceType: 'SPECIAL_VAT',
      externalInvoiceNo: 'EXT-PI',
      supplierId: 9,
      sourceType: 'PURCHASE_RECEIPT',
      ownershipType: 'PUBLIC',
      sourceLines: [{ orderLineId: 2001, receiptLineId: 2002, invoiceQuantity: '3.000000', taxRate: '0.130000' }],
      remark: '采购发票',
      version: 0,
      idempotencyKey: 'idem-pi',
    }

    await api.salesInvoices.create(salesPayload)
    await api.salesInvoices.update(11, salesPayload)
    await api.salesInvoices.confirm(11, { version: 2, idempotencyKey: 'confirm-si' })
    await api.salesInvoices.cancel(11, { version: 3, idempotencyKey: 'cancel-si' })
    await api.purchaseInvoices.create(purchasePayload)
    await api.purchaseInvoices.update(21, purchasePayload)
    await api.purchaseInvoices.match(21, { version: 2, idempotencyKey: 'match-pi' })
    await api.purchaseInvoices.confirm(21, { version: 3, idempotencyKey: 'confirm-pi' })

    expect(JSON.parse(fetcher.mock.calls[1][1].body as string).sourceLines[0].invoiceQuantity).toBe('2.500000')
    expect(JSON.parse(fetcher.mock.calls[9][1].body as string).sourceLines[0].taxRate).toBe('0.130000')
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/finance/sales-invoices/11/confirm', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ version: 2, idempotencyKey: 'confirm-si' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(14, '/api/admin/finance/purchase-invoices/21/match', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ version: 2, idempotencyKey: 'match-pi' }),
    }))
  })
})
