import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from './accountPermissionApi'
import { createFinancialCloseApi } from './financialCloseApi'

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
      traceId: 'trace-fin-close-1',
      data: null,
    }),
  } as Response
}

const csrf = {
  token: 'csrf-token',
  headerName: 'X-CSRF-TOKEN',
  parameterName: '_csrf',
}

describe('financialCloseApi', () => {
  it('按冻结 API 读取财务结账、银行和税务分页，并保持金额字符串与权限脱敏字段', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(apiResponse({
        items: [{
          id: 7,
          periodCode: '2026-07',
          status: 'OPEN',
          closeStatus: 'READY',
          latestCheckRunId: 31,
          latestCheckStatus: 'READY',
          closeRunId: null,
          voucherCount: 8,
          bankDifference: '0.00',
          taxPayableAmount: '1234.56',
          amountVisible: true,
          sourceVisible: false,
          bankSensitiveVisible: false,
          version: 5,
          allowedActions: [{ code: 'CLOSE', label: '关闭', enabled: true }],
          actionDisabledReasons: { REOPEN: '期间尚未关闭' },
        }],
        total: 1,
        page: 1,
        pageSize: 10,
      }))
      .mockResolvedValueOnce(apiResponse({
        id: 31,
        status: 'BLOCKED',
        periodCode: '2026-07',
        sourceFingerprint: 'fp-001',
        checkItems: [{
          code: 'BUSINESS_PERIOD_CLOSED',
          severity: 'BLOCKER',
          status: 'BLOCKED',
          conclusion: '业务月结未关闭',
          actualValue: 'OPEN',
          expectedValue: 'CLOSED',
          sourceVisible: false,
          sourceType: null,
          sourceId: null,
        }],
        closeVersion: null,
        amountVisible: true,
        sourceVisible: false,
        bankSensitiveVisible: false,
        version: 2,
        allowedActions: [],
        actionDisabledReasons: { CLOSE: '业务月结前置未满足' },
      }))
      .mockResolvedValueOnce(apiResponse({
        items: [{
          id: 101,
          accountName: '基本户',
          accountType: 'BASIC',
          glAccountCode: '1002.01',
          bankName: '中国银行',
          accountNoMasked: '****1234',
          accountNoLast4: '1234',
          accountNoFingerprint: null,
          enabled: true,
          amountVisible: true,
          sourceVisible: true,
          bankSensitiveVisible: false,
          version: 1,
          allowedActions: ['UPDATE', 'DISABLE'],
          actionDisabledReasons: {},
        }],
        total: 1,
        page: 1,
        pageSize: 10,
      }))
      .mockResolvedValueOnce(apiResponse({
        items: [{
          id: 201,
          periodCode: '2026-07',
          bankAccountId: 101,
          status: 'READY',
          bankEndingBalance: '1000.10',
          glEndingBalance: '999.90',
          adjustedBankBalance: '1000.10',
          adjustedBookBalance: '1000.10',
          difference: '0.00',
          amountVisible: true,
          sourceVisible: true,
          bankSensitiveVisible: false,
          version: 4,
          allowedActions: ['CONFIRM'],
          actionDisabledReasons: {},
        }],
        total: 1,
        page: 1,
        pageSize: 10,
      }))
      .mockResolvedValueOnce(apiResponse({
        items: [{
          id: 301,
          periodCode: '2026-07',
          status: 'CALCULATED',
          disclaimer: '基础汇总/估算，非正式申报',
          outputTaxAmount: '500.00',
          inputTaxAmount: '120.00',
          payableTaxAmount: '380.00',
          retainedTaxAmount: '0.00',
          estimatedIncomeTaxAmount: '200.00',
          amountVisible: false,
          restrictedReason: '无权查看税额',
          sourceVisible: false,
          bankSensitiveVisible: false,
          version: 6,
          allowedActions: [{ code: 'CONFIRM', enabled: true }],
          actionDisabledReasons: {},
        }],
        total: 1,
        page: 1,
        pageSize: 10,
      }))

    const api = createFinancialCloseApi({ fetcher, baseUrl: '' })

    await expect(api.periods.list({ page: 1, pageSize: 10, year: '2026' })).resolves.toMatchObject({
      items: [expect.objectContaining({
        bankDifference: '0.00',
        taxPayableAmount: '1234.56',
        allowedActions: ['CLOSE'],
        sourceVisible: false,
        bankSensitiveVisible: false,
      })],
    })
    await expect(api.checkRuns.get(31)).resolves.toMatchObject({
      checkItems: [expect.objectContaining({ code: 'BUSINESS_PERIOD_CLOSED', sourceType: null })],
      actionDisabledReasons: { CLOSE: '业务月结前置未满足' },
    })
    await expect(api.bankAccounts.list({ page: 1, pageSize: 10 })).resolves.toMatchObject({
      items: [expect.objectContaining({ accountNoMasked: '****1234', bankSensitiveVisible: false })],
    })
    await expect(api.bankReconciliations.list({ page: 1, pageSize: 10 })).resolves.toMatchObject({
      items: [expect.objectContaining({ adjustedBookBalance: '1000.10', difference: '0.00' })],
    })
    await expect(api.taxSummaries.list({ page: 1, pageSize: 10, periodCode: '2026-07' })).resolves.toMatchObject({
      items: [expect.objectContaining({
        payableTaxAmount: '380.00',
        restrictedReason: '无权查看税额',
        allowedActions: ['CONFIRM'],
      })],
    })

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/admin/financial-closes/periods?page=1&pageSize=10&year=2026', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/financial-closes/check-runs/31', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(3, '/api/admin/bank-accounts?page=1&pageSize=10', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/bank-reconciliations?page=1&pageSize=10', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(5, '/api/admin/tax-summaries?page=1&pageSize=10&periodCode=2026-07', expect.objectContaining({ method: 'GET' }))
  })

  it('写动作携带 CSRF、version、reason、idempotencyKey，不重组金额字段', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(apiResponse(csrf))
      .mockResolvedValueOnce(apiResponse({ id: 31, status: 'READY', version: 3, allowedActions: ['CLOSE'], actionDisabledReasons: {} }))
      .mockResolvedValueOnce(apiResponse(csrf))
      .mockResolvedValueOnce(apiResponse({ id: 51, status: 'CLOSED', version: 4, allowedActions: ['REOPEN'], actionDisabledReasons: {} }))
      .mockResolvedValueOnce(apiResponse(csrf))
      .mockResolvedValueOnce(apiResponse({ id: 52, status: 'SUBMITTED', sceneCode: 'FINANCIAL_PERIOD_REOPEN', version: 1 }))
      .mockResolvedValueOnce(apiResponse(csrf))
      .mockResolvedValueOnce(apiResponse({ id: 61, status: 'PREVIEWED', debitTotal: '88.80', creditTotal: '88.80', version: 1 }))
      .mockResolvedValueOnce(apiResponse(csrf))
      .mockResolvedValueOnce(apiResponse({ id: 201, status: 'CONFIRMED', difference: '0.00', version: 5 }))
      .mockResolvedValueOnce(apiResponse(csrf))
      .mockResolvedValueOnce(apiResponse({ id: 301, status: 'CONFIRMED', payableTaxAmount: '380.00', version: 7 }))

    const api = createFinancialCloseApi({ fetcher })

    await api.periods.startCheck(7, { version: 5, idempotencyKey: 'check-key' })
    await api.checkRuns.close(31, { version: 3, reason: '月末关闭', idempotencyKey: 'close-key' })
    await api.closeRuns.requestReopen(51, { version: 4, reason: '调整凭证', idempotencyKey: 'reopen-key' })
    await api.profitLoss.preview(7, { version: 5, idempotencyKey: 'pl-key' })
    await api.bankReconciliations.confirm(201, { version: 4, reason: '确认对账', idempotencyKey: 'bank-key' })
    await api.taxSummaries.confirm(301, { version: 6, reason: '确认税额基础汇总', idempotencyKey: 'tax-key' })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/financial-closes/periods/7/checks', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({ 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'csrf-token' }),
      body: JSON.stringify({ version: 5, idempotencyKey: 'check-key' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/financial-closes/check-runs/31/close', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 3, reason: '月末关闭', idempotencyKey: 'close-key' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/financial-closes/close-runs/51/reopen-requests', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 4, reason: '调整凭证', idempotencyKey: 'reopen-key' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(8, '/api/admin/financial-closes/periods/7/profit-loss-transfers/preview', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 5, idempotencyKey: 'pl-key' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(10, '/api/admin/bank-reconciliations/201/confirm', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 4, reason: '确认对账', idempotencyKey: 'bank-key' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(12, '/api/admin/tax-summaries/301/confirm', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 6, reason: '确认税额基础汇总', idempotencyKey: 'tax-key' }),
    }))
  })

  it('409 envelope 保留财务结账稳定错误码和 traceId', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiFailure('FIN_CLOSE_STALE', '检查已失效，请重新检查', 409))
    const api = createFinancialCloseApi({ fetcher })

    await expect(api.periods.get(7)).rejects.toMatchObject({
      code: 'FIN_CLOSE_STALE',
      status: 409,
      traceId: 'trace-fin-close-1',
    } satisfies Partial<AccountPermissionApiError>)
  })

  it('兼容后端 032 税务 DTO 字段并按冻结路径发送银行、税务维护动作', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(apiResponse({
        id: 401,
        taxpayerType: 'GENERAL',
        creditCode: '91330000123456789X',
        taxAuthority: '杭州市税务局',
        vatPeriodicity: 'MONTHLY',
        incomeTaxRate: '0.2500',
        urbanMaintenanceRate: '0.0700',
        effectiveFrom: '2026-01-01',
        version: 2,
      }))
      .mockResolvedValueOnce(apiResponse({
        items: [{
          id: 501,
          periodCode: '2026-11',
          taxType: 'VAT',
          status: 'CONFIRMED',
          outputVat: '13.00',
          inputVat: '6.00',
          adjustmentAmount: '1.00',
          vatPayable: '8.00',
          urbanMaintenanceTax: '0.56',
          incomeTaxEstimated: '38.75',
          stale: false,
          current: true,
          version: 5,
          allowedActions: ['GENERATE_VOUCHER'],
          actionDisabledReasons: { CONFIRM: '已确认不可重复确认' },
        }],
        total: 1,
        page: 1,
        pageSize: 10,
      }))
      .mockResolvedValueOnce(apiResponse({
        items: [{
          id: 601,
          taxType: 'VAT',
          paymentDate: '2026-11-28',
          amount: '8.00',
          bankAccountId: 2,
          bankAccountMasked: '账号已脱敏',
          bankAccountDisplay: '中国银行 基本户 账号已脱敏',
          bankSensitiveVisible: false,
          version: 2,
        }],
        total: 1,
        page: 1,
        pageSize: 10,
      }))
      .mockResolvedValueOnce(apiResponse(csrf))
      .mockResolvedValueOnce(apiResponse({ id: 101, version: 1 }))
      .mockResolvedValueOnce(apiResponse(csrf))
      .mockResolvedValueOnce(apiResponse({ id: 151, version: 1 }))
      .mockResolvedValueOnce(apiResponse(csrf))
      .mockResolvedValueOnce(apiResponse({ id: 201, version: 4 }))
      .mockResolvedValueOnce(apiResponse(csrf))
      .mockResolvedValueOnce(apiResponse({ id: 202, version: 5 }))
      .mockResolvedValueOnce(apiResponse(csrf))
      .mockResolvedValueOnce(apiResponse({ id: 501, version: 6 }))
      .mockResolvedValueOnce(apiResponse(csrf))
      .mockResolvedValueOnce(apiResponse({ id: 601, version: 3 }))

    const api = createFinancialCloseApi({ fetcher })

    await expect(api.taxProfiles.current()).resolves.toMatchObject({
      creditCode: '91330000123456789X',
      unifiedSocialCreditCodeMasked: '91330000123456789X',
      urbanMaintenanceRate: '0.0700',
      cityMaintenanceTaxRate: '0.0700',
    })
    await expect(api.taxSummaries.list({ page: 1, pageSize: 10 })).resolves.toMatchObject({
      items: [expect.objectContaining({
        outputTaxAmount: '13.00',
        inputTaxAmount: '6.00',
        payableTaxAmount: '8.00',
        estimatedIncomeTaxAmount: '38.75',
      })],
    })
    await expect(api.taxPayments.list({ page: 1, pageSize: 10 })).resolves.toMatchObject({
      items: [expect.objectContaining({
        bankAccountMasked: '账号已脱敏',
        bankAccountDisplay: '中国银行 基本户 账号已脱敏',
      })],
    })

    await api.bankAccounts.create({ accountName: '一般户', accountType: 'GENERAL', bankName: '中国银行', currency: 'CNY', glAccountId: 100201, openedOn: '2026-01-01', accountNo: '6222000011112222', idempotencyKey: 'bank-account-create-key' })
    await api.bankStatements.create({ bankAccountId: 101, transactionDate: '2026-11-01', postingDate: '2026-11-01', direction: 'CREDIT', amount: '8.00', counterpartyName: '税务局', summary: '退税', bankTransactionId: 'BTX-1', referenceNo: 'REF-1', idempotencyKey: 'bank-statement-create-key' })
    await api.bankReconciliations.createMatch(201, { version: 4, matches: [{ statementLineId: 151, ledgerEntryId: 301, amount: '8.00' }], idempotencyKey: 'match-key' })
    await api.bankReconciliations.deleteMatch(201, 'MATCH-1', { version: 5, reason: '取消错误匹配', idempotencyKey: 'cancel-match-key' })
    await api.taxSummaries.addAdjustment(501, { version: 5, adjustmentType: 'VAT_INCREASE', amount: '1.00', reason: '补充调整', idempotencyKey: 'tax-adjust-key' })
    await api.taxPayments.correct(601, { amount: '9.00', reason: '更正缴纳金额', idempotencyKey: 'tax-payment-correct-key' })

    expect(fetcher).toHaveBeenCalledWith('/api/admin/bank-reconciliations/201/matches', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 4, matches: [{ statementLineId: 151, ledgerEntryId: 301, amount: '8.00' }], idempotencyKey: 'match-key' }),
    }))
    expect(fetcher).toHaveBeenCalledWith('/api/admin/bank-reconciliations/201/matches?matchGroupNo=MATCH-1', expect.objectContaining({
      method: 'DELETE',
      body: JSON.stringify({ version: 5, reason: '取消错误匹配', idempotencyKey: 'cancel-match-key' }),
    }))
    expect(fetcher).toHaveBeenCalledWith('/api/admin/tax-summaries/501/adjustments', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 5, adjustmentType: 'VAT_INCREASE', amount: '1.00', reason: '补充调整', idempotencyKey: 'tax-adjust-key' }),
    }))
  })

  it('以冻结追溯字段为主并兼容旧检查字段，银行账户保留真实科目主键', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(apiResponse({
        items: [{
          id: 7,
          periodCode: '2026-07',
          status: 'OPEN',
          closeStatus: 'READY',
          latestCheckId: 71,
          version: 2,
          allowedActions: ['CHECK'],
          actionDisabledReasons: {},
        }],
        total: 1,
        page: 1,
        pageSize: 10,
      }))
      .mockResolvedValueOnce(apiResponse({
        id: 71,
        periodCode: '2026-07',
        status: 'READY',
        items: [{ code: 'NO_INCOMPLETE_VOUCHERS', status: 'PASSED', conclusion: '无未完成凭证' }],
        version: 3,
        allowedActions: ['CLOSE'],
        actionDisabledReasons: {},
      }))
      .mockResolvedValueOnce(apiResponse({
        items: [{
          id: 101,
          accountName: '基本户',
          glAccountId: 100201,
          glAccountCode: '1002.01',
          version: 4,
          allowedActions: ['UPDATE'],
          actionDisabledReasons: {},
        }],
        total: 1,
        page: 1,
        pageSize: 10,
      }))

    const api = createFinancialCloseApi({ fetcher })

    await expect(api.periods.list({ page: 1, pageSize: 10 })).resolves.toMatchObject({
      items: [expect.objectContaining({
        latestCheckRunId: 71,
        latestCheckId: 71,
      })],
    })
    await expect(api.checkRuns.get(71)).resolves.toMatchObject({
      checkItems: [expect.objectContaining({ code: 'NO_INCOMPLETE_VOUCHERS' })],
      items: [expect.objectContaining({ code: 'NO_INCOMPLETE_VOUCHERS' })],
    })
    await expect(api.bankAccounts.list({ page: 1, pageSize: 10 })).resolves.toMatchObject({
      items: [expect.objectContaining({
        glAccountId: 100201,
        glAccountCode: '1002.01',
      })],
    })
  })
})
