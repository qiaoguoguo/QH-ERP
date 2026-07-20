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
      body: JSON.stringify({
        voucherType: 'GENERAL',
        voucherDate: '2026-07-20',
        summary: '手工凭证',
        lines: [
          { lineNo: 1, summary: '借方', accountId: 1002, debitAmount: '100.10', creditAmount: '0.00', auxiliaryItems: [] },
          { lineNo: 2, summary: '贷方', accountId: 6001, debitAmount: '0.00', creditAmount: '100.10', auxiliaryItems: [] },
        ],
        idempotencyKey: 'create-key',
      }),
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

  it('归一化真实后端 DTO：动作编码、两层来源、科目/规则别名和试算三组 totals', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(apiResponse({
        id: 91,
        draftNo: 'GLD-202607-0001',
        status: 'DRAFT',
        sourceType: 'FIN_VOUCHER_DRAFT',
        sourceId: 61,
        sourceNo: 'VD-001',
        sourceOriginalType: 'SALES_INVOICE',
        sourceOriginalId: 11,
        sourceOriginalNo: 'SI-001',
        sourceOriginalVersion: 7,
        sourceOriginalFingerprint: 'fp-si-001',
        version: 4,
        allowedActions: [
          { code: 'UPDATE', label: '维护', enabled: true },
          { code: 'SUBMIT', label: '提交', enabled: true },
        ],
        actionDisabledReasons: { CANCEL: '审批中不能取消' },
      }))
      .mockResolvedValueOnce(apiResponse({
        items: [{
          id: 1002,
          code: '1002',
          name: '银行存款',
          category: 'ASSET',
          levelNo: 1,
          isLeaf: true,
          postable: true,
          balanceDirection: 'DEBIT',
          enabled: true,
          version: 3,
          auxiliaryRequirements: [{ dimensionCode: 'CUSTOMER', dimensionName: '客户', requirementType: 'OPTIONAL' }],
          allowedActions: [{ code: 'UPDATE', label: '维护', enabled: true }],
          actionDisabledReasons: { DISABLE: '已被凭证使用' },
        }],
        total: 1,
        page: 1,
        pageSize: 10,
      }))
      .mockResolvedValueOnce(apiResponse({
        items: [{
          id: 1,
          sourceType: 'SALES_INVOICE',
          sourceVariant: 'STANDARD',
          ruleVersion: 2,
          status: 'ACTIVE',
          validationStatus: 'VALID',
          lineCount: 3,
          allowedActions: [{ code: 'DISABLE', label: '停用', enabled: true }],
          actionDisabledReasons: {},
        }],
        total: 1,
        page: 1,
        pageSize: 10,
      }))
      .mockResolvedValueOnce(apiResponse({
        balanced: false,
        opening: { debitTotal: '0.00', creditTotal: '0.00', differenceAmount: '0.00' },
        period: { debitTotal: '120.00', creditTotal: '100.00', differenceAmount: '20.00' },
        ending: { debitTotal: '120.00', creditTotal: '100.00', differenceAmount: '20.00' },
        differences: [{ accountCode: '2221.01', accountName: '应交税费-销项税额', differenceAmount: '20.00' }],
        amountVisible: true,
      }))

    const api = createGlApi({ fetcher })

    await expect(api.vouchers.get(91)).resolves.toMatchObject({
      sourceNo: 'VD-001',
      sourceOriginalNo: 'SI-001',
      sourceOriginalVersion: 7,
      sourceOriginalFingerprint: 'fp-si-001',
      allowedActions: ['UPDATE', 'SUBMIT'],
      actionDisabledReasons: { CANCEL: '审批中不能取消' },
    })
    await expect(api.accounts.list({ page: 1, pageSize: 10 })).resolves.toMatchObject({
      items: [expect.objectContaining({
        level: 1,
        auxiliaryRequirements: [expect.objectContaining({ requirement: 'OPTIONAL' })],
        allowedActions: ['UPDATE'],
      })],
    })
    await expect(api.postingRules.list({ page: 1, pageSize: 10 })).resolves.toMatchObject({
      items: [expect.objectContaining({ versionNo: 2, allowedActions: ['DISABLE'] })],
    })
    await expect(api.trialBalance.get({ periodCode: '2026-07' })).resolves.toMatchObject({
      balanced: false,
      openingDebitTotal: '0.00',
      periodDebitTotal: '120.00',
      periodCreditTotal: '100.00',
      endingDebitTotal: '120.00',
      differenceAmount: '20.00',
      differences: [expect.objectContaining({ accountCode: '2221.01' })],
    })
  })

  it('消费独立科目与辅助候选接口，保留后段搜索和已选回显参数', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(apiResponse({
        items: [{
          accountId: 160,
          accountCode: '6602.160',
          accountName: '第 160 项费用',
          category: 'PROFIT_LOSS',
          levelNo: 2,
          isLeaf: true,
          postable: true,
          balanceDirection: 'DEBIT',
          enabled: true,
          version: 1,
          auxiliaryRequirements: [{ dimensionCode: 'PROJECT', dimensionName: '项目', requirementType: 'REQUIRED' }],
          allowedActions: [{ code: 'UPDATE', enabled: true }],
          actionDisabledReasons: { DISABLE: '已被规则引用' },
        }],
        total: 1,
        page: 1,
        pageSize: 20,
      }))
      .mockResolvedValueOnce(apiResponse({
        items: [{
          objectId: 501,
          objectCode: 'PRJ-501',
          objectName: '已选项目',
          restricted: false,
        }],
        total: 1,
        page: 1,
        pageSize: 20,
      }))

    const api = createGlApi({ fetcher })

    await expect(api.accounts.candidates({
      keyword: '第 160',
      selectedIds: '1002,160',
      page: 1,
      pageSize: 20,
    })).resolves.toMatchObject({
      items: [expect.objectContaining({
        id: 160,
        code: '6602.160',
        name: '第 160 项费用',
        auxiliaryRequirements: [expect.objectContaining({ dimensionCode: 'PROJECT', requirement: 'REQUIRED' })],
      })],
    })
    await expect(api.auxDimensions.candidates('PROJECT', {
      keyword: '已选',
      selectedIds: '501',
      page: 1,
      pageSize: 20,
    })).resolves.toMatchObject({
      items: [expect.objectContaining({ objectId: 501, objectName: '已选项目' })],
    })

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/admin/gl/accounts/candidates?keyword=%E7%AC%AC+160&selectedIds=1002%2C160&page=1&pageSize=20', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/gl/aux-dimensions/PROJECT/candidates?keyword=%E5%B7%B2%E9%80%89&selectedIds=501&page=1&pageSize=20', expect.objectContaining({ method: 'GET' }))
  })

  it('写入自定义辅助项目和制证规则时只发送后端冻结 DTO 字段', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(apiResponse(csrf))
      .mockResolvedValueOnce(apiResponse({ objectId: 502, objectCode: 'REG-502', objectName: '华南区', enabled: true, version: 1 }))
      .mockResolvedValueOnce(apiResponse(csrf))
      .mockResolvedValueOnce(apiResponse({ id: 3, name: '采购发票默认规则', sourceType: 'PURCHASE_INVOICE', sourceVariant: 'DEFAULT', versionNo: 1, status: 'DRAFT', allowedActions: ['UPDATE'], actionDisabledReasons: {} }))
      .mockResolvedValueOnce(apiResponse(csrf))
      .mockResolvedValueOnce(apiResponse({ id: 3, name: '采购发票默认规则', sourceType: 'PURCHASE_INVOICE', sourceVariant: 'DEFAULT', versionNo: 1, status: 'DRAFT', validationStatus: 'VALID', allowedActions: ['ACTIVATE'], actionDisabledReasons: {} }))

    const api = createGlApi({ fetcher })

    await api.auxDimensions.createItem(2, {
      code: 'REG-502',
      name: '华南区',
      enabled: true,
      version: 0,
      idempotencyKey: 'aux-item-key',
    })
    await api.postingRules.create({
      name: '采购发票默认规则',
      description: '采购发票生成应付规则',
      effectiveFrom: '2026-07-01',
      effectiveTo: null,
      sourceType: 'PURCHASE_INVOICE',
      sourceVariant: 'DEFAULT',
      lines: [{
        normalizedFactCode: 'PURCHASE_PAYABLE',
        direction: 'CREDIT',
        accountId: 2202,
        summaryTemplate: '确认应付',
        auxiliaryMappings: [{ dimensionCode: 'SUPPLIER', sourceField: 'supplierId' }],
      }],
      version: 0,
      idempotencyKey: 'rule-create-key',
    })
    await api.postingRules.validate(3, {
      version: 1,
      sourceType: 'PURCHASE_INVOICE',
      sourceId: 77,
      sourceVersion: 4,
      idempotencyKey: 'rule-validate-key',
    })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/gl/aux-dimensions/2/items', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        code: 'REG-502',
        name: '华南区',
        enabled: true,
        version: 0,
        idempotencyKey: 'aux-item-key',
      }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/gl/posting-rules', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        name: '采购发票默认规则',
        description: '采购发票生成应付规则',
        effectiveFrom: '2026-07-01',
        effectiveTo: null,
        sourceType: 'PURCHASE_INVOICE',
        sourceVariant: 'DEFAULT',
        lines: [{
          normalizedFactCode: 'PURCHASE_PAYABLE',
          direction: 'CREDIT',
          accountId: 2202,
          summaryTemplate: '确认应付',
          auxiliaryMappings: [{ dimensionCode: 'SUPPLIER', sourceField: 'supplierId' }],
        }],
        version: 0,
        idempotencyKey: 'rule-create-key',
      }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/gl/posting-rules/3/validate', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        version: 1,
        sourceType: 'PURCHASE_INVOICE',
        sourceId: 77,
        sourceVersion: 4,
        idempotencyKey: 'rule-validate-key',
      }),
    }))
  })
})
