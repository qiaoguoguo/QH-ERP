import { describe, expect, it, vi } from 'vitest'
import { createFinanceVoucherDraftApi, type VoucherSourceType } from './financeVoucherDraftApi'

type AssertTrue<T extends true> = T

function apiResponse<T>(data: T) {
  return { ok: true, status: 200, json: async () => ({ success: true, code: 'OK', message: '成功', data }) }
}

describe('028 凭证草稿 API', () => {
  it('凭证来源类型不包含后端不支持的预收预付业务语义', () => {
    const typeContract: {
      advanceReceiptDoesNotExposeAsVoucherSource: AssertTrue<'ADVANCE_RECEIPT' extends VoucherSourceType ? false : true>
      prepaymentDoesNotExposeAsVoucherSource: AssertTrue<'PREPAYMENT' extends VoucherSourceType ? false : true>
    } = {
      advanceReceiptDoesNotExposeAsVoucherSource: true,
      prepaymentDoesNotExposeAsVoucherSource: true,
    }

    expect(typeContract).toEqual({
      advanceReceiptDoesNotExposeAsVoucherSource: true,
      prepaymentDoesNotExposeAsVoucherSource: true,
    })
  })

  it('查询与操作凭证草稿时只使用非正式草稿端点', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
      .mockResolvedValueOnce(apiResponse({ id: 1 }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-generate', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 2 }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-ready', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 2 }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-cancel', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 2 }))
    const api = createFinanceVoucherDraftApi({ fetcher })

    await api.voucherDrafts.list({
      keyword: 'VD',
      sourceType: 'SALES_INVOICE',
      sourceNo: 'SI',
      partnerId: 8,
      status: 'DRAFT',
      balanced: false,
      businessDateFrom: '2026-08-01',
      businessDateTo: '',
      generatedAtFrom: '2026-08-02',
      generatedAtTo: '',
      page: 1,
      pageSize: 20,
    })
    await api.voucherDrafts.get(2)
    await api.voucherDrafts.generate({ sourceType: 'SALES_INVOICE', sourceId: 10, version: 2, idempotencyKey: 'gen-vd' })
    await api.voucherDrafts.markReady(2, { version: 3, idempotencyKey: 'ready-vd' })
    await api.voucherDrafts.cancel(2, { version: 4, idempotencyKey: 'cancel-vd' })

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/admin/finance/voucher-drafts?keyword=VD&sourceType=SALES_INVOICE&sourceNo=SI&partnerId=8&status=DRAFT&balanced=false&businessDateFrom=2026-08-01&generatedAtFrom=2026-08-02&page=1&pageSize=20', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/finance/voucher-drafts/2', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/finance/voucher-drafts/generate', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ sourceType: 'SALES_INVOICE', sourceId: 10, version: 2, idempotencyKey: 'gen-vd' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/finance/voucher-drafts/2/ready', expect.objectContaining({ method: 'PUT' }))
    expect(fetcher).toHaveBeenNthCalledWith(8, '/api/admin/finance/voucher-drafts/2/cancel', expect.objectContaining({ method: 'PUT' }))
  })

  it('详情优先消费冻结 DTO 的借贷合计、来源摘要和生成版本', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({
      id: 3,
      draftNo: 'VD-003',
      sourceType: 'PAYMENT',
      sourceId: 91,
      sourceNo: 'PM-POSTED-001',
      businessDate: '2026-08-06',
      status: 'DRAFT',
      debitTotal: '260.00',
      creditTotal: '260.00',
      balanced: true,
      generationVersion: 4,
      version: 1,
      allowedActions: ['READY'],
      sourceSummary: { sourceType: 'PAYMENT', sourceNo: 'PM-POSTED-001', summary: '付款 PM-POSTED-001', restricted: false },
      lines: [
        { direction: 'DEBIT', businessCategory: '预付', summary: '冲预付', pretaxAmount: '260.00', taxAmount: '0.00', totalAmount: '260.00' },
      ],
    }))
    const api = createFinanceVoucherDraftApi({ fetcher })

    const detail = await api.voucherDrafts.get(3)

    expect(detail.debitTotal).toBe('260.00')
    expect(detail.creditTotal).toBe('260.00')
    expect(detail.generationVersion).toBe(4)
    expect(detail.sourceSummary).toEqual(expect.objectContaining({ sourceType: 'PAYMENT', sourceNo: 'PM-POSTED-001', summary: '付款 PM-POSTED-001' }))
    expect(detail.lines?.[0]).toEqual(expect.objectContaining({ totalAmount: '260.00' }))
  })

  it('详情兼容后端冻结的借贷金额、分录金额和来源摘要 DTO', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({
      id: 2,
      draftNo: 'VD-002',
      sourceType: 'RECEIPT',
      sourceId: 81,
      sourceNo: 'RC-POSTED-001',
      businessDate: '2026-08-06',
      status: 'DRAFT',
      debitAmount: '120.00',
      creditAmount: '120.00',
      balanced: true,
      generationVersion: 3,
      version: 1,
      allowedActions: ['READY'],
      sourceSummary: '收款 RC-POSTED-001',
      lines: [
        { direction: 'DEBIT', businessCategory: '收款', summary: '收款形成现金', amount: '120.00', sourceType: 'RECEIPT', sourceId: 81 },
      ],
    }))
    const api = createFinanceVoucherDraftApi({ fetcher })

    const detail = await api.voucherDrafts.get(2)

    expect(detail.debitTotal).toBe('120.00')
    expect(detail.creditTotal).toBe('120.00')
    expect(detail.sourceSummary).toEqual(expect.objectContaining({ sourceType: 'RECEIPT', sourceNo: 'RC-POSTED-001', summary: '收款 RC-POSTED-001' }))
    expect(detail.lines?.[0]).toEqual(expect.objectContaining({ totalAmount: '120.00', amount: '120.00' }))
  })
})
