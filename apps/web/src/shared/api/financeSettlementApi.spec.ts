import { describe, expect, it, vi } from 'vitest'
import { createFinanceSettlementApi, type AdvanceFundPayload, type SettlementAllocationPayload } from './financeSettlementApi'

function apiResponse<T>(data: T) {
  return { ok: true, status: 200, json: async () => ({ success: true, code: 'OK', message: '成功', data }) }
}

describe('028 预收预付与核销 API', () => {
  it('查询预收预付、资金池和目标池时使用分域端点与独立分页', async () => {
    const fetcher = vi.fn()
    Array.from({ length: 5 }).forEach(() => {
      fetcher.mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 50 }))
    })
    const api = createFinanceSettlementApi({ fetcher })

    await api.advanceReceipts.list({ keyword: 'RC', customerId: 8, availableOnly: true, page: 1, pageSize: 20 })
    await api.prepayments.list({ keyword: 'PM', supplierId: 9, availableOnly: true, page: 1, pageSize: 20 })
    await api.settlementWorkbench.funds({ direction: 'CUSTOMER', partnerId: 8, ownershipType: 'PROJECT', projectId: 18, page: 1, pageSize: 50 })
    await api.settlementWorkbench.targets({ direction: 'CUSTOMER', partnerId: 8, ownershipType: 'PROJECT', projectId: 18, page: 1, pageSize: 50 })
    await api.settlementWorkbench.get(61)

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/admin/finance/advance-receipts?keyword=RC&customerId=8&availableOnly=true&page=1&pageSize=20', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/finance/prepayments?keyword=PM&supplierId=9&availableOnly=true&page=1&pageSize=20', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(3, '/api/admin/finance/settlement-workbench/funds?direction=CUSTOMER&partnerId=8&ownershipType=PROJECT&projectId=18&page=1&pageSize=50', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/finance/settlement-workbench/targets?direction=CUSTOMER&partnerId=8&ownershipType=PROJECT&projectId=18&page=1&pageSize=50', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(5, '/api/admin/finance/settlement-workbench/allocations/61', expect.objectContaining({ method: 'GET' }))
  })

  it('资金草稿和多目标核销写操作携带版本、幂等键与字符串金额', async () => {
    const fetcher = vi.fn()
    Array.from({ length: 6 }).forEach((_, index) => {
      fetcher.mockResolvedValueOnce(apiResponse({ token: `csrf-${index}`, headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      fetcher.mockResolvedValueOnce(apiResponse({ id: index + 1 }))
    })
    const api = createFinanceSettlementApi({ fetcher })
    const advancePayload: AdvanceFundPayload = {
      partnerId: 8,
      ownershipType: 'PROJECT',
      projectId: 18,
      businessDate: '2026-08-06',
      amount: '500.00',
      method: 'BANK_TRANSFER',
      allocations: [{ targetType: 'SALES_INVOICE', targetId: 10, amount: '100.00' }],
      version: 0,
      idempotencyKey: 'advance-idem',
    }
    const allocationPayload: SettlementAllocationPayload = {
      version: 0,
      settlementSide: 'RECEIVABLE',
      cashSourceType: 'RECEIPT',
      cashSourceId: 1,
      businessDate: '2026-08-06',
      direction: 'CUSTOMER',
      partnerId: 8,
      ownershipType: 'PROJECT',
      projectId: 18,
      funds: [{ fundType: 'ADVANCE_RECEIPT', fundId: 1, version: 2, amount: '300.00' }],
      targets: [{ targetType: 'RECEIVABLE', targetId: 2, version: 3, amount: '300.00' }],
      lines: [{ targetType: 'RECEIVABLE', targetId: 2, amount: '300.00' }],
      idempotencyKey: 'alloc-idem',
    }

    await api.advanceReceipts.create(advancePayload)
    await api.advanceReceipts.post(41, { version: 1, idempotencyKey: 'post-adv' })
    await api.prepayments.create(advancePayload)
    await api.prepayments.post(51, { version: 1, idempotencyKey: 'post-prepay' })
    await api.settlementWorkbench.create(allocationPayload)
    await api.settlementWorkbench.post(61, { version: 4, idempotencyKey: 'post-alloc' })

    expect(JSON.parse(fetcher.mock.calls[1][1].body as string).amount).toBe('500.00')
    expect(JSON.parse(fetcher.mock.calls[9][1].body as string).targets[0].amount).toBe('300.00')
    expect(JSON.parse(fetcher.mock.calls[9][1].body as string)).toEqual(expect.objectContaining({
      version: 0,
      cashSourceType: 'RECEIPT',
      lines: [expect.objectContaining({ targetType: 'RECEIVABLE', targetId: 2, amount: '300.00' })],
    }))
    expect(fetcher).toHaveBeenNthCalledWith(10, '/api/admin/finance/settlement-workbench/allocations', expect.objectContaining({ method: 'POST' }))
    expect(fetcher).toHaveBeenNthCalledWith(12, '/api/admin/finance/settlement-workbench/allocations/61/post', expect.objectContaining({ method: 'PUT' }))
  })
})
