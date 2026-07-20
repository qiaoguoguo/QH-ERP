import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from './accountPermissionApi'
import { createGlApi } from './glApi'

function apiResponse<T>(data: T) {
  return {
    ok: true,
    status: 200,
    json: async () => ({
      success: true,
      code: 'OK',
      message: '成功',
      data,
    }),
  } as Response
}

function apiFailure(code: string, message: string, status = 409) {
  return {
    ok: false,
    status,
    json: async () => ({
      success: false,
      code,
      message,
      traceId: 'trace-gl-1',
      data: null,
    }),
  } as Response
}

const csrf = {
  token: 'csrf-token',
  headerName: 'X-CSRF-TOKEN',
  parameterName: '_csrf',
}

describe('glApi', () => {
  it('按冻结 /api/admin/gl 契约读取基础设置、凭证和账簿分页', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(apiResponse({
        ledgerCode: 'MAIN',
        ledgerName: '总账',
        baseCurrency: 'CNY',
        initialized: true,
        startPeriodCode: '2026-07',
      }))
      .mockResolvedValueOnce(apiResponse({
        items: [{
          id: 1002,
          code: '1002',
          name: '银行存款',
          category: 'ASSET',
          level: 1,
          isLeaf: true,
          postable: true,
          balanceDirection: 'DEBIT',
          enabled: true,
          auxiliaryRequirements: [{ dimensionCode: 'CUSTOMER', requirement: 'OPTIONAL' }],
          version: 3,
          allowedActions: ['UPDATE'],
          actionDisabledReasons: { DISABLE: '已被凭证使用' },
        }],
        total: 1,
        page: 1,
        pageSize: 10,
      }))
      .mockResolvedValueOnce(apiResponse({
        items: [{
          id: 91,
          draftNo: 'GLD-202607-0001',
          voucherType: 'GENERAL',
          voucherNo: null,
          voucherDate: '2026-07-20',
          accountingPeriodCode: '2026-07',
          status: 'DRAFT',
          summary: '手工凭证',
          sourceType: 'MANUAL',
          currency: 'CNY',
          debitTotal: '120.00',
          creditTotal: '120.00',
          amountVisible: true,
          sourceVisible: true,
          version: 2,
          allowedActions: ['UPDATE', 'SUBMIT', 'CANCEL'],
          actionDisabledReasons: {},
        }],
        total: 1,
        page: 1,
        pageSize: 10,
      }))
      .mockResolvedValueOnce(apiResponse({
        items: [{
          periodCode: '2026-07',
          accountCode: '1002',
          accountName: '银行存款',
          openingDebit: '0.00',
          openingCredit: '0.00',
          periodDebit: '120.00',
          periodCredit: '0.00',
          endingDebit: '120.00',
          endingCredit: '0.00',
          balanceDirection: 'DEBIT',
          balanced: true,
          restricted: false,
        }],
        total: 1,
        page: 1,
        pageSize: 10,
      }))
      .mockResolvedValueOnce(apiResponse({
        balanced: true,
        openingDebitTotal: '0.00',
        openingCreditTotal: '0.00',
        periodDebitTotal: '120.00',
        periodCreditTotal: '120.00',
        endingDebitTotal: '120.00',
        endingCreditTotal: '120.00',
        differences: [],
        restricted: false,
      }))

    const api = createGlApi({ fetcher, baseUrl: '' })

    await expect(api.ledger.get()).resolves.toMatchObject({ ledgerCode: 'MAIN', baseCurrency: 'CNY' })
    await expect(api.accounts.list({ keyword: '银行', page: 1, pageSize: 10 })).resolves.toMatchObject({ total: 1 })
    await expect(api.vouchers.list({ status: 'DRAFT', page: 1, pageSize: 10 })).resolves.toMatchObject({
      items: [expect.objectContaining({ debitTotal: '120.00', allowedActions: ['UPDATE', 'SUBMIT', 'CANCEL'] })],
    })
    await expect(api.ledgers.general({ periodCode: '2026-07', page: 1, pageSize: 10 })).resolves.toMatchObject({
      items: [expect.objectContaining({ periodDebit: '120.00', restricted: false })],
    })
    await expect(api.trialBalance.get({ periodCode: '2026-07' })).resolves.toMatchObject({
      balanced: true,
      periodDebitTotal: '120.00',
    })

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/admin/gl/ledger', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/gl/accounts?keyword=%E9%93%B6%E8%A1%8C&page=1&pageSize=10', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(3, '/api/admin/gl/vouchers?status=DRAFT&page=1&pageSize=10', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/gl/ledgers/general?periodCode=2026-07&page=1&pageSize=10', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(5, '/api/admin/gl/trial-balance?periodCode=2026-07', expect.objectContaining({ method: 'GET' }))
  })

  it('写动作携带 CSRF、version、idempotencyKey，并保持十进制字符串不重算', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(apiResponse(csrf))
      .mockResolvedValueOnce(apiResponse({ id: 1, initialized: true, startPeriodCode: '2026-07' }))
      .mockResolvedValueOnce(apiResponse(csrf))
      .mockResolvedValueOnce(apiResponse({
        id: 91,
        draftNo: 'GLD-202607-0001',
        status: 'DRAFT',
        debitTotal: '100.10',
        creditTotal: '100.10',
        version: 1,
        allowedActions: ['SUBMIT'],
        actionDisabledReasons: {},
      }))
      .mockResolvedValueOnce(apiResponse(csrf))
      .mockResolvedValueOnce(apiResponse({
        id: 92,
        draftNo: 'GLD-202607-0002',
        sourceType: 'FIN_VOUCHER_DRAFT',
        sourceId: 61,
        status: 'DRAFT',
        debitTotal: '120.00',
        creditTotal: '120.00',
        version: 1,
        allowedActions: ['UPDATE', 'SUBMIT'],
        actionDisabledReasons: {},
      }))
      .mockResolvedValueOnce(apiResponse(csrf))
      .mockResolvedValueOnce(apiResponse({ id: 91, status: 'SUBMITTED', approvalSummary: { sceneCode: 'GL_VOUCHER_POST' } }))

    const api = createGlApi({ fetcher })
    await api.ledger.initialize({ startYearMonth: '2026-07', idempotencyKey: 'init-key' })
    await api.vouchers.create({
      voucherType: 'GENERAL',
      voucherDate: '2026-07-20',
      summary: '手工凭证',
      lines: [
        { lineNo: 1, summary: '借方', accountId: 1002, debitAmount: '100.10', creditAmount: '0.00', auxiliaryItems: [] },
        { lineNo: 2, summary: '贷方', accountId: 6001, debitAmount: '0.00', creditAmount: '100.10', auxiliaryItems: [] },
      ],
      idempotencyKey: 'create-key',
    })
    await api.vouchers.fromFinanceDraft(61, { version: 3, idempotencyKey: 'convert-key' })
    await api.vouchers.submit(91, { version: 1, idempotencyKey: 'submit-key' })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/gl/ledger/initialize', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({ 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'csrf-token' }),
      body: JSON.stringify({ startYearMonth: '2026-07', idempotencyKey: 'init-key' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/gl/vouchers', expect.objectContaining({
      method: 'POST',
      body: expect.stringContaining('"debitAmount":"100.10"'),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/gl/vouchers/from-finance-draft/61', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 3, idempotencyKey: 'convert-key' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(8, '/api/admin/gl/vouchers/91/submit', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 1, idempotencyKey: 'submit-key' }),
    }))
  })

  it('409 envelope 使用稳定业务错误码失败关闭并保留 traceId', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiFailure('GL_SOURCE_ALREADY_ACCOUNTED', '来源已生成正式凭证', 409))
    const api = createGlApi({ fetcher })

    await expect(api.vouchers.get(91)).rejects.toMatchObject({
      code: 'GL_SOURCE_ALREADY_ACCOUNTED',
      status: 409,
      traceId: 'trace-gl-1',
    } satisfies Partial<AccountPermissionApiError>)
  })
})
