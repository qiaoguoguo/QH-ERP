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
})
