import { describe, expect, it, vi } from 'vitest'
import { createFinanceExpenseApi, type ExpensePayload } from './financeExpenseApi'

function apiResponse<T>(data: T) {
  return { ok: true, status: 200, json: async () => ({ success: true, code: 'OK', message: '成功', data }) }
}

describe('028 费用分域 API', () => {
  it('查询费用、分类和来源候选时使用独立资源端点', async () => {
    const fetcher = vi.fn()
    Array.from({ length: 3 }).forEach(() => {
      fetcher.mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
    })
    const api = createFinanceExpenseApi({ fetcher })

    await api.expenses.list({
      keyword: 'EXP',
      supplierId: 9,
      categoryId: 3,
      ownershipType: 'PROJECT',
      sourceType: 'OUTSOURCING_RECEIPT',
      status: 'DRAFT',
      settlementStatus: 'UNSETTLED',
      businessDateFrom: '2026-08-01',
      businessDateTo: '',
      costRestricted: true,
      page: 1,
      pageSize: 20,
    })
    await api.expenseCategories.list({ keyword: '运费', status: 'ENABLED', page: 1, pageSize: 100 })
    await api.expenseSourceCandidates.list({
      keyword: 'OS',
      sourceType: 'OUTSOURCING_ORDER',
      supplierId: 9,
      ownershipType: 'PROJECT',
      projectId: 18,
      businessDateFrom: '2026-08-01',
      businessDateTo: '',
      page: 1,
      pageSize: 50,
    })

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/admin/finance/expenses?keyword=EXP&supplierId=9&categoryId=3&ownershipType=PROJECT&sourceType=OUTSOURCING_RECEIPT&status=DRAFT&settlementStatus=UNSETTLED&businessDateFrom=2026-08-01&costRestricted=true&page=1&pageSize=20', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/finance/expenses/categories?keyword=%E8%BF%90%E8%B4%B9&status=ENABLED&page=1&pageSize=100', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(3, '/api/admin/finance/expenses/source-candidates?keyword=OS&sourceType=OUTSOURCING_ORDER&supplierId=9&ownershipType=PROJECT&projectId=18&businessDateFrom=2026-08-01&page=1&pageSize=50', expect.objectContaining({ method: 'GET' }))
  })

  it('创建、更新、确认和取消费用单时保留字符串金额与非正式成本边界', async () => {
    const fetcher = vi.fn()
    Array.from({ length: 4 }).forEach((_, index) => {
      fetcher.mockResolvedValueOnce(apiResponse({ token: `csrf-${index}`, headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      fetcher.mockResolvedValueOnce(apiResponse({ id: index + 1 }))
    })
    const api = createFinanceExpenseApi({ fetcher })
    const payload: ExpensePayload = {
      supplierId: 9,
      ownershipType: 'PROJECT',
      projectId: 18,
      categoryId: 3,
      businessDate: '2026-08-05',
      lines: [{ categoryId: 3, pretaxAmount: '100.00', taxRate: '0.060000', taxAmount: '6.00', totalAmount: '106.00' }],
      remark: '项目费用',
      version: 0,
      idempotencyKey: 'idem-exp',
    }

    await api.expenses.create(payload)
    await api.expenses.update(31, payload)
    await api.expenses.confirm(31, { version: 1, idempotencyKey: 'confirm-exp' })
    await api.expenses.cancel(31, { version: 2, idempotencyKey: 'cancel-exp' })

    expect(JSON.parse(fetcher.mock.calls[1][1].body as string).lines[0].taxRate).toBe('0.060000')
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/finance/expenses/31/confirm', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ version: 1, idempotencyKey: 'confirm-exp' }),
    }))
  })
})
