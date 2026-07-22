import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import FinancialCloseWorkbenchView from './FinancialCloseWorkbenchView.vue'
import FinancialCloseRunDetailView from './FinancialCloseRunDetailView.vue'
import ProfitLossCarryforwardView from './ProfitLossCarryforwardView.vue'
import BankAccountsView from './BankAccountsView.vue'
import BankStatementsView from './BankStatementsView.vue'
import BankReconciliationView from './BankReconciliationView.vue'
import TaxSettingsView from './TaxSettingsView.vue'
import TaxSummaryView from './TaxSummaryView.vue'
import TaxPaymentsView from './TaxPaymentsView.vue'
import { useAuthStore } from '../../stores/authStore'
import { useConfirmActionMock } from '../../test/setup'

const financialCloseApiMock = vi.hoisted(() => ({
  periods: { list: vi.fn(), get: vi.fn(), startCheck: vi.fn() },
  checkRuns: { get: vi.fn(), close: vi.fn() },
  closeRuns: { requestReopen: vi.fn() },
  reopenRequests: { get: vi.fn() },
  profitLoss: { list: vi.fn(), preview: vi.fn(), generate: vi.fn() },
  bankAccounts: { list: vi.fn(), create: vi.fn(), update: vi.fn(), disable: vi.fn() },
  bankStatements: { list: vi.fn(), get: vi.fn(), create: vi.fn(), importPreview: vi.fn(), importConfirm: vi.fn(), ignoreLine: vi.fn() },
  bankReconciliations: { list: vi.fn(), get: vi.fn(), candidates: vi.fn(), create: vi.fn(), createMatch: vi.fn(), deleteMatch: vi.fn(), createException: vi.fn(), calculate: vi.fn(), confirm: vi.fn(), reopen: vi.fn() },
  taxProfiles: { current: vi.fn(), update: vi.fn() },
  taxRateRules: { list: vi.fn(), create: vi.fn() },
  taxInvoiceTypes: { list: vi.fn(), create: vi.fn() },
  taxSummaries: { list: vi.fn(), get: vi.fn(), create: vi.fn(), calculate: vi.fn(), addAdjustment: vi.fn(), confirm: vi.fn(), createVoucherDraft: vi.fn() },
  taxPayments: { list: vi.fn(), create: vi.fn(), correct: vi.fn() },
}))

const confirmActionMock = useConfirmActionMock()

vi.mock('../../shared/api/financialCloseApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/financialCloseApi')>()),
  financialCloseApi: financialCloseApiMock,
}))

function page<T>(items: T[], pageNumber = 1, pageSize = 10) {
  return { items, total: items.length, page: pageNumber, pageSize }
}

async function mountFinancialCloseView(
  component: object,
  path: string,
  permissions: string[] = [
    'financial-close:period:view',
    'financial-close:period:check',
    'financial-close:period:close',
    'financial-close:period:reopen',
    'financial-close:profit-loss:view',
    'financial-close:profit-loss:generate',
    'financial-close:bank-account:view',
    'financial-close:bank-account:manage',
    'financial-close:bank-reconciliation:view',
    'financial-close:bank-reconciliation:import',
    'financial-close:bank-reconciliation:match',
    'financial-close:bank-reconciliation:confirm',
    'financial-close:bank-reconciliation:reopen',
    'financial-close:tax-profile:view',
    'financial-close:tax-profile:manage',
    'financial-close:tax-summary:view',
    'financial-close:tax-summary:calculate',
    'financial-close:tax-summary:confirm',
    'financial-close:tax-summary:generate-voucher',
    'financial-close:tax-payment:view',
    'financial-close:tax-payment:manage',
    'financial-close:amount:view',
    'financial-close:source:view',
    'financial-close:bank-sensitive:view',
  ],
) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'fin_close_user', displayName: '结账会计', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/gl/financial-close', name: 'gl-financial-close', component },
      { path: '/gl/financial-close/:runId', name: 'gl-financial-close-run-detail', component },
      { path: '/gl/profit-loss-carryforward', name: 'gl-profit-loss-carryforward', component },
      { path: '/gl/bank-accounts', name: 'gl-bank-accounts', component },
      { path: '/gl/bank-statements', name: 'gl-bank-statements', component },
      { path: '/gl/bank-reconciliation', name: 'gl-bank-reconciliation', component },
      { path: '/gl/tax-settings', name: 'gl-tax-settings', component },
      { path: '/gl/tax-summary', name: 'gl-tax-summary', component },
      { path: '/gl/tax-payments', name: 'gl-tax-payments', component },
      { path: '/gl/vouchers/:id', name: 'gl-voucher-detail', component: { template: '<div />' } },
      { path: '/platform/approvals', name: 'platform-approvals', component: { template: '<div />' } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(component, {
    attachTo: document.body,
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

async function openMoreActions(wrapper: VueWrapper) {
  const moreButton = wrapper.findAll('button').find((button) => button.text() === '更多')
  expect(moreButton).toBeTruthy()
  await moreButton!.trigger('click')
  await flushPromises()
}

function teleportedAction(testId: string) {
  const actions = Array.from(document.body.querySelectorAll<HTMLElement>(`[data-test="${testId}"]`))
  const action = actions.at(-1)
  expect(action).not.toBeNull()
  return action!
}

async function clickTeleportedAction(wrapper: VueWrapper, testId: string) {
  await openMoreActions(wrapper)
  teleportedAction(testId).click()
  await flushPromises()
}

const periodReady = {
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
  sourceVisible: true,
  bankSensitiveVisible: true,
  version: 5,
  allowedActions: ['CHECK', 'CLOSE'],
  actionDisabledReasons: { REOPEN: '期间尚未关闭' },
}

const blockedRun = {
  id: 31,
  periodCode: '2026-07',
  status: 'BLOCKED',
  sourceFingerprint: 'fp-close-001',
  closeVersion: null,
  amountVisible: true,
  sourceVisible: false,
  bankSensitiveVisible: false,
  version: 3,
  allowedActions: [],
  actionDisabledReasons: { CLOSE: '业务月结前置未满足' },
  checkItems: [
    {
      code: 'BUSINESS_PERIOD_CLOSED',
      severity: 'BLOCKER',
      status: 'BLOCKED',
      conclusion: '业务月结未关闭',
      actualValue: 'OPEN',
      expectedValue: 'CLOSED',
      sourceType: null,
      sourceId: null,
      sourceVisible: false,
    },
    {
      code: 'BANK_RECONCILIATIONS_CONFIRMED',
      severity: 'BLOCKER',
      status: 'PASSED',
      conclusion: '银行对账已确认',
      actualValue: '2',
      expectedValue: '2',
      sourceType: 'BANK_RECONCILIATION',
      sourceId: 201,
      sourceNo: 'BR-202607-001',
      sourceVisible: true,
    },
  ],
  closeSnapshot: {
    voucherCount: 8,
    trialBalanceDebitTotal: '1000.00',
    trialBalanceCreditTotal: '1000.00',
    bankReconciliationVersions: ['BR-202607-001'],
    taxSummaryVersions: ['TAX-202607-001'],
  },
  reopenRequests: [{ id: 41, status: 'SUBMITTED', reason: '调整凭证', approvalSceneCode: 'FINANCIAL_PERIOD_REOPEN' }],
}

describe('032 财务结账前端页面族', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  beforeEach(() => {
    vi.clearAllMocks()
    confirmActionMock.mockResolvedValue(true)
    financialCloseApiMock.periods.list.mockResolvedValue(page([periodReady]))
    financialCloseApiMock.periods.get.mockResolvedValue(periodReady)
    financialCloseApiMock.periods.startCheck.mockResolvedValue(blockedRun)
    financialCloseApiMock.checkRuns.get.mockResolvedValue(blockedRun)
    financialCloseApiMock.checkRuns.close.mockResolvedValue({ id: 51, status: 'CLOSED', periodCode: '2026-07', version: 6 })
    financialCloseApiMock.closeRuns.requestReopen.mockResolvedValue({ id: 41, status: 'SUBMITTED', sceneCode: 'FINANCIAL_PERIOD_REOPEN' })
    financialCloseApiMock.profitLoss.list.mockResolvedValue(page([{
      id: 61,
      periodCode: '2026-07',
      status: 'POSTED',
      debitTotal: '880.00',
      creditTotal: '880.00',
      voucherId: 91,
      voucherNo: 'GLD-202607-0091',
      sourceFingerprint: 'fp-pl-001',
      amountVisible: true,
      sourceVisible: true,
      bankSensitiveVisible: false,
      version: 2,
      allowedActions: ['VIEW_VOUCHER'],
      actionDisabledReasons: { GENERATE: '最新结转已记账' },
    }]))
    financialCloseApiMock.profitLoss.preview.mockResolvedValue({
      id: 62,
      periodCode: '2026-07',
      status: 'PREVIEWED',
      debitTotal: '88.80',
      creditTotal: '88.80',
      lines: [{ accountCode: '6602', accountName: '管理费用', transferAmount: '88.80', direction: 'CREDIT' }],
      amountVisible: true,
      sourceVisible: true,
      bankSensitiveVisible: false,
      version: 1,
      allowedActions: ['GENERATE'],
      actionDisabledReasons: {},
    })
    financialCloseApiMock.profitLoss.generate.mockResolvedValue({ id: 62, status: 'DRAFT', voucherId: 92, voucherNo: 'GLD-202607-0092', version: 2 })
    financialCloseApiMock.bankAccounts.list.mockResolvedValue(page([{
      id: 101,
      accountName: '基本户',
      accountType: 'BASIC',
      bankName: '中国银行',
      accountNoMasked: '****1234',
      glAccountId: 100201,
      glAccountCode: '1002.01',
      enabled: true,
      amountVisible: true,
      sourceVisible: true,
      bankSensitiveVisible: false,
      version: 3,
      allowedActions: ['UPDATE', 'DISABLE'],
      actionDisabledReasons: {},
    }]))
    financialCloseApiMock.bankAccounts.disable.mockResolvedValue({ id: 101, enabled: false, version: 4 })
    financialCloseApiMock.bankStatements.list.mockResolvedValue(page([{
      id: 151,
      statementNo: 'BS-202607-001',
      bankAccountName: '基本户',
      transactionDate: '2026-07-20',
      postingDate: '2026-07-20',
      direction: 'CREDIT',
      amount: '120.00',
      status: 'PARTIALLY_MATCHED',
      duplicate: false,
      amountVisible: true,
      sourceVisible: true,
      bankSensitiveVisible: false,
      version: 1,
      allowedActions: ['IGNORE'],
      actionDisabledReasons: {},
    }]))
    financialCloseApiMock.bankStatements.importPreview.mockResolvedValue({
      batchNo: 'IMPORT-001',
      validRows: 2,
      errorRows: 1,
      lines: [{ rowNo: 3, status: 'ERROR', errors: ['金额格式错误'], message: '金额格式错误' }],
    })
    financialCloseApiMock.bankStatements.importConfirm.mockResolvedValue({ statementId: 701, importedCount: 2 })
    financialCloseApiMock.bankStatements.create.mockResolvedValue({ id: 152, statementNo: 'BS-202607-002', version: 1 })
    financialCloseApiMock.bankStatements.get.mockResolvedValue({
      id: 151,
      statementNo: 'BS-202607-001',
      bankAccountName: '基本户',
      transactionDate: '2026-07-20',
      postingDate: '2026-07-20',
      direction: 'CREDIT',
      amount: '120.00',
      status: 'PARTIALLY_MATCHED',
      counterpartyName: '齐辉客户',
      summary: '银行收款',
      bankTransactionId: 'BTX-001',
      referenceNo: 'REF-001',
      amountVisible: true,
      sourceVisible: true,
      bankSensitiveVisible: false,
      version: 1,
      allowedActions: ['IGNORE'],
      actionDisabledReasons: {},
    })
    financialCloseApiMock.bankStatements.ignoreLine.mockResolvedValue({ id: 151, status: 'IGNORED', version: 2 })
    financialCloseApiMock.bankReconciliations.list.mockResolvedValue(page([{
      id: 201,
      reconciliationNo: 'BR-202607-001',
      periodCode: '2026-07',
      bankAccountName: '基本户',
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
      allowedActions: ['MATCH', 'CALCULATE', 'CONFIRM', 'REOPEN'],
      actionDisabledReasons: {},
    }]))
    financialCloseApiMock.bankReconciliations.get.mockResolvedValue({
      id: 201,
      reconciliationNo: 'BR-202607-001',
      periodCode: '2026-07',
      status: 'BALANCED',
      bankEndingBalance: '1000.10',
      glEndingBalance: '999.90',
      adjustedBankBalance: '1000.10',
      adjustedBookBalance: '1000.10',
      difference: '0.00',
      matches: [{ matchGroupNo: 'MATCH-1', statementLineId: 151, ledgerEntryId: 301, amount: '60.00' }],
      exceptions: [{ exceptionType: 'BANK_ONLY_CREDIT', amount: '0.20', reason: '银行已收企业未入账' }],
      amountVisible: true,
      sourceVisible: true,
      bankSensitiveVisible: false,
      version: 4,
      allowedActions: ['MATCH', 'CALCULATE', 'CONFIRM', 'REOPEN'],
      actionDisabledReasons: {},
    })
    financialCloseApiMock.bankReconciliations.candidates.mockResolvedValue({
      statementLines: [
        { id: 151, statementNo: 'BS-202607-001', amount: '120.00', remainingAmount: '60.00', selected: false },
        { id: 152, statementNo: 'BS-202607-002', amount: '80.00', remainingAmount: '60.00', selected: false },
      ],
      ledgerEntries: [
        { id: 301, voucherNo: '记-202607-0001', amount: '120.00', remainingAmount: '60.00', selected: false },
        { id: 302, voucherNo: '记-202607-0002', amount: '80.00', remainingAmount: '60.00', selected: false },
      ],
      page: 1,
      pageSize: 20,
      total: 25,
    })
    financialCloseApiMock.bankReconciliations.createMatch.mockResolvedValue({ id: 901, version: 1 })
    financialCloseApiMock.bankReconciliations.deleteMatch.mockResolvedValue({ id: 201, version: 5 })
    financialCloseApiMock.bankReconciliations.createException.mockResolvedValue({ id: 201, version: 6 })
    financialCloseApiMock.bankReconciliations.calculate.mockResolvedValue({ id: 201, status: 'BALANCED', version: 7, difference: '0.00' })
    financialCloseApiMock.bankReconciliations.confirm.mockResolvedValue({ id: 201, status: 'CONFIRMED', version: 5 })
    financialCloseApiMock.bankReconciliations.reopen.mockResolvedValue({ id: 202, status: 'DRAFT', reopenedFromId: 201, version: 1 })
    financialCloseApiMock.taxProfiles.current.mockResolvedValue({
      id: 401,
      taxpayerType: 'GENERAL',
      creditCode: '91330000123456789X',
      unifiedSocialCreditCodeMasked: '91330000123456789X',
      taxAuthority: '杭州市税务局',
      vatPeriodicity: 'MONTHLY',
      incomeTaxRate: '0.2500',
      urbanMaintenanceRate: '0.0700',
      cityMaintenanceTaxRate: '0.0700',
      effectiveFrom: '2026-01-01',
      amountVisible: true,
      sourceVisible: true,
      bankSensitiveVisible: false,
      version: 2,
      allowedActions: ['UPDATE'],
      actionDisabledReasons: {},
    })
    financialCloseApiMock.taxProfiles.update.mockResolvedValue({ id: 401, taxpayerType: 'GENERAL', version: 3 })
    financialCloseApiMock.taxRateRules.list.mockResolvedValue(page([{ id: 411, taxType: 'VAT', rateCode: 'VAT_13', rateValue: '0.1300', effectiveFrom: '2026-01-01', status: 'ENABLED' }], 1, 10))
    financialCloseApiMock.taxRateRules.create.mockResolvedValue({ id: 412, taxType: 'VAT', rateCode: 'VAT_09', rateValue: '0.0900', version: 1 })
    financialCloseApiMock.taxInvoiceTypes.list.mockResolvedValue(page([{ id: 421, code: 'DIGITAL_VAT_SPECIAL', name: '数电专票', direction: 'OUTPUT', deductible: true, status: 'ENABLED' }], 1, 10))
    financialCloseApiMock.taxInvoiceTypes.create.mockResolvedValue({ id: 422, code: 'DIGITAL_VAT_NORMAL', name: '数电普票', version: 1 })
    financialCloseApiMock.taxSummaries.list.mockResolvedValue(page([{
      id: 501,
      periodCode: '2026-07',
      taxType: 'VAT',
      status: 'CALCULATED',
      disclaimer: '基础汇总/估算，非正式申报',
      outputTaxAmount: '500.00',
      outputVat: '500.00',
      inputTaxAmount: '120.00',
      inputVat: '120.00',
      payableTaxAmount: '380.00',
      vatPayable: '380.00',
      retainedTaxAmount: '0.00',
      estimatedIncomeTaxAmount: '200.00',
      incomeTaxEstimated: '200.00',
      adjustmentAmount: '0.00',
      stale: false,
      current: true,
      amountVisible: true,
      sourceVisible: false,
      bankSensitiveVisible: false,
      version: 6,
      allowedActions: ['CALCULATE', 'ADJUST', 'CONFIRM', 'GENERATE_VOUCHER'],
      actionDisabledReasons: {},
    }]))
    financialCloseApiMock.taxSummaries.create.mockResolvedValue({ id: 502, periodCode: '2026-07', taxType: 'VAT', status: 'DRAFT', version: 1 })
    financialCloseApiMock.taxSummaries.calculate.mockResolvedValue({ id: 501, status: 'CALCULATED', payableTaxAmount: '380.00', version: 7 })
    financialCloseApiMock.taxSummaries.addAdjustment.mockResolvedValue({ id: 501, status: 'CALCULATED', adjustmentAmount: '1.00', version: 7 })
    financialCloseApiMock.taxSummaries.confirm.mockResolvedValue({ id: 501, status: 'CONFIRMED', version: 8 })
    financialCloseApiMock.taxSummaries.createVoucherDraft.mockResolvedValue({ id: 501, voucherId: 93, voucherNo: 'GLD-202607-0093', version: 9 })
    financialCloseApiMock.taxPayments.list.mockResolvedValue(page([{
      id: 601,
      periodCode: '2026-07',
      taxType: 'VAT',
      paymentDate: '2026-07-25',
      amount: '380.00',
      voucherId: 94,
      voucherNo: '记-202607-0094',
      paymentSourceType: 'GL_VOUCHER',
      bankAccountMasked: '****1234',
      bankAccountDisplay: '中国银行 基本户 ****1234',
      amountVisible: true,
      sourceVisible: true,
      bankSensitiveVisible: false,
      version: 2,
      allowedActions: ['CORRECT'],
      actionDisabledReasons: {},
    }]))
    financialCloseApiMock.taxPayments.create.mockResolvedValue({ id: 602, amount: '380.00', version: 1 })
    financialCloseApiMock.taxPayments.correct.mockResolvedValue({ id: 601, amount: '390.00', version: 3 })
  })

  it('财务结账工作台和检查详情展示阻断、关闭/反结账禁用原因、来源脱敏和安全 returnTo', async () => {
    const { wrapper, router } = await mountFinancialCloseView(FinancialCloseWorkbenchView, '/gl/financial-close')

    expect(wrapper.text()).toContain('财务结账工作台')
    expect(wrapper.text()).toContain('不同于 030 业务月结')
    expect(wrapper.text()).toContain('2026-07')
    expect(wrapper.text()).toContain('1234.56')
    expect(wrapper.text()).toContain('期间尚未关闭')
    expect(wrapper.find('.query-form').exists()).toBe(true)
    expect(wrapper.find('.table-scroll').exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'ElPagination' }).props('pageSizes')).toEqual([10, 20, 50, 100])

    await wrapper.find('[data-test="start-close-check"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.periods.startCheck).toHaveBeenCalledWith(7, expect.objectContaining({
      version: 5,
      idempotencyKey: expect.stringContaining('financial-close-check-'),
    }))

    await wrapper.find('[data-test="financial-close-detail"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('gl-financial-close-run-detail')
    expect(router.currentRoute.value.query.returnTo).toBe('/gl/financial-close')

    const detail = await mountFinancialCloseView(FinancialCloseRunDetailView, '/gl/financial-close/31?returnTo=/gl/accounting-periods')
    expect(detail.wrapper.text()).toContain('结账检查详情')
    expect(detail.wrapper.text()).toContain('业务月结未关闭')
    expect(detail.wrapper.text()).toContain('来源受限')
    expect(detail.wrapper.text()).toContain('业务月结前置未满足')
    expect(detail.wrapper.text()).toContain('FINANCIAL_PERIOD_REOPEN')
    expect(detail.wrapper.find('[data-test="return-from-close-run"]').exists()).toBe(true)
  })

  it('损益结转页面展示预览、幂等生成、审批状态、来源变化和查看凭证', async () => {
    const { wrapper, router } = await mountFinancialCloseView(ProfitLossCarryforwardView, '/gl/profit-loss-carryforward')

    expect(wrapper.text()).toContain('期末损益结转')
    expect(wrapper.text()).toContain('GL_VOUCHER_POST')
    expect(wrapper.text()).toContain('GLD-202607-0091')
    expect(wrapper.text()).toContain('最新结转已记账')

    await wrapper.find('[data-test="preview-profit-loss"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.profitLoss.preview).toHaveBeenCalledWith(7, expect.objectContaining({
      idempotencyKey: expect.stringContaining('profit-loss-preview-'),
    }))
    expect(wrapper.text()).toContain('管理费用')
    expect(wrapper.text()).toContain('88.80')

    await wrapper.find('[data-test="generate-profit-loss"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.profitLoss.generate).toHaveBeenCalledWith(7, expect.objectContaining({
      idempotencyKey: expect.stringContaining('profit-loss-generate-'),
    }))

    await wrapper.find('[data-test="profit-loss-voucher-link"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('gl-voucher-detail')
    expect(router.currentRoute.value.query.returnTo).toBe('/gl/profit-loss-carryforward')

    wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('change', 2, 10)
    await flushPromises()
    expect(financialCloseApiMock.profitLoss.list).toHaveBeenCalledWith(7, expect.objectContaining({
      page: 2,
      pageSize: 10,
    }))
  })

  it('银行账户页面以表单完成新增、编辑、停用，并按权限和 allowedActions 失败关闭', async () => {
    const accounts = await mountFinancialCloseView(BankAccountsView, '/gl/bank-accounts')
    expect(accounts.wrapper.text()).toContain('银行账户')
    expect(accounts.wrapper.text()).toContain('****1234')
    expect(accounts.wrapper.text()).toContain('1002.01')
    expect(accounts.wrapper.find('.table-scroll').exists()).toBe(true)

    await accounts.wrapper.find('[data-test="open-bank-account-create"]').trigger('click')
    await flushPromises()
    expect(accounts.wrapper.text()).toContain('银行账户维护')
    await accounts.wrapper.find('input[name="bank-account-name"]').setValue('一般户')
    await accounts.wrapper.find('input[name="bank-account-bank-name"]').setValue('建设银行')
    await accounts.wrapper.find('input[name="bank-account-gl-account-id"]').setValue('100201')
    await accounts.wrapper.find('input[name="bank-account-no"]').setValue('6222000011112222')
    await accounts.wrapper.find('[data-test="save-bank-account"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.bankAccounts.create).toHaveBeenCalledWith(expect.objectContaining({
      accountName: '一般户',
      accountType: 'BASIC',
      bankName: '建设银行',
      currency: 'CNY',
      glAccountId: 100201,
      accountNo: '6222000011112222',
      idempotencyKey: expect.stringContaining('bank-account-save-'),
    }))

    await accounts.wrapper.find('[data-test="open-bank-account-edit"]').trigger('click')
    await flushPromises()
    await accounts.wrapper.find('input[name="bank-account-name"]').setValue('基本户-更新')
    await accounts.wrapper.find('[data-test="save-bank-account"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.bankAccounts.update).toHaveBeenCalledWith(101, expect.objectContaining({
      accountName: '基本户-更新',
      glAccountId: 100201,
      version: 3,
      idempotencyKey: expect.stringContaining('bank-account-save-'),
    }))
    expect(JSON.stringify(financialCloseApiMock.bankAccounts.update.mock.calls[0][1])).not.toContain('1002.01')

    await accounts.wrapper.find('[data-test="disable-bank-account"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.bankAccounts.disable).toHaveBeenCalledWith(101, expect.objectContaining({
      version: 3,
      reason: expect.stringContaining('停用'),
      idempotencyKey: expect.stringContaining('bank-account-disable-'),
    }))

    financialCloseApiMock.bankAccounts.list.mockResolvedValueOnce(page([{
      id: 102,
      accountName: '只读户',
      enabled: true,
      bankSensitiveVisible: false,
      version: 1,
      allowedActions: [],
      actionDisabledReasons: { DISABLE: '无银行账户维护权限' },
    }]))
    const readonlyAccounts = await mountFinancialCloseView(BankAccountsView, '/gl/bank-accounts', ['financial-close:bank-account:view'])
    expect(readonlyAccounts.wrapper.find('[data-test="open-bank-account-create"]').attributes('disabled')).toBeDefined()
    expect(readonlyAccounts.wrapper.find('[data-test="disable-bank-account"]').attributes('disabled')).toBeDefined()
    expect(readonlyAccounts.wrapper.text()).toContain('无银行账户维护权限')
  })

  it('银行流水页面完成 CSV 预览后确认导入、手工录入、详情查看和忽略', async () => {
    const statements = await mountFinancialCloseView(BankStatementsView, '/gl/bank-statements')
    expect(statements.wrapper.text()).toContain('银行流水')
    expect(statements.wrapper.text()).toContain('部分匹配')
    expect(statements.wrapper.text()).not.toContain('PARTIALLY_MATCHED / PARTIALLY_MATCHED')

    await statements.wrapper.find('[data-test="open-bank-statement-create"]').trigger('click')
    await flushPromises()
    await statements.wrapper.find('input[name="bank-statement-bank-account-id"]').setValue('101')
    await statements.wrapper.find('input[name="bank-statement-amount"]').setValue('88.00')
    await statements.wrapper.find('[data-test="save-bank-statement"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.bankStatements.create).toHaveBeenCalledWith(expect.objectContaining({
      bankAccountId: 101,
      direction: 'CREDIT',
      amount: '88.00',
      idempotencyKey: expect.stringContaining('bank-statement-create-'),
    }))

    await statements.wrapper.find('[data-test="preview-bank-statement-import"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.bankStatements.importPreview).toHaveBeenCalledWith(expect.objectContaining({
      bankAccountId: 101,
      fileName: 'bank-statement.csv',
      csvContent: expect.stringContaining('交易日期'),
      idempotencyKey: expect.stringContaining('bank-statement-preview-'),
    }))
    expect(statements.wrapper.text()).toContain('金额格式错误')
    await statements.wrapper.find('[data-test="confirm-bank-statement-import"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.bankStatements.importConfirm).toHaveBeenCalledWith(expect.objectContaining({
      bankAccountId: 101,
      csvContent: expect.stringContaining('交易日期'),
      idempotencyKey: expect.stringContaining('bank-statement-confirm-'),
    }))

    await statements.wrapper.find('[data-test="open-bank-statement-detail"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.bankStatements.get).toHaveBeenCalledWith(151)
    expect(statements.wrapper.text()).toContain('银行收款')

    await statements.wrapper.find('[data-test="ignore-bank-statement-line"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.bankStatements.ignoreLine).toHaveBeenCalledWith(151, expect.objectContaining({
      reason: expect.stringContaining('忽略'),
    }))
  })

  it('银行对账页面要求用户显式多选候选，支持部分金额、取消匹配、四类未达、重算、确认和重开', async () => {
    const reconciliation = await mountFinancialCloseView(BankReconciliationView, '/gl/bank-reconciliation')
    expect(reconciliation.wrapper.text()).toContain('银行对账工作台')
    expect(reconciliation.wrapper.text()).toContain('候选池不受主列表十条分页限制')
    expect(reconciliation.wrapper.text()).toContain('差额')
    expect(reconciliation.wrapper.text()).toContain('0.00')
    expect(financialCloseApiMock.bankReconciliations.candidates).toHaveBeenCalledWith(201, expect.objectContaining({ page: 1, pageSize: 20 }))

    await reconciliation.wrapper.find('[data-test="create-bank-match"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.bankReconciliations.createMatch).not.toHaveBeenCalled()
    expect(reconciliation.wrapper.text()).toContain('请选择银行流水和总账分录')

    const statementSelections = reconciliation.wrapper.findAll('[data-test="select-statement-candidate"]')
    const ledgerSelections = reconciliation.wrapper.findAll('[data-test="select-ledger-candidate"]')
    await statementSelections[0].setValue(true)
    await statementSelections[1].setValue(true)
    await ledgerSelections[0].setValue(true)
    await ledgerSelections[1].setValue(true)
    await flushPromises()
    const matchDetailAmounts = reconciliation.wrapper.findAll('input[name="bank-match-detail-amount"]')
    await matchDetailAmounts[0].setValue('60.00')
    await matchDetailAmounts[1].setValue('60.00')
    await reconciliation.wrapper.find('[data-test="create-bank-match"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.bankReconciliations.createMatch).toHaveBeenCalledWith(201, expect.objectContaining({
      matches: [
        { statementLineId: 151, ledgerEntryId: 301, amount: '60.00' },
        { statementLineId: 152, ledgerEntryId: 302, amount: '60.00' },
      ],
      idempotencyKey: expect.stringContaining('bank-reconciliation-match-'),
    }))
    expect(JSON.stringify(financialCloseApiMock.bankReconciliations.createMatch.mock.calls[0][1])).not.toContain('statementLineIds')

    await reconciliation.wrapper.find('[data-test="cancel-bank-match"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.bankReconciliations.deleteMatch).toHaveBeenCalledWith(201, 'MATCH-1', expect.objectContaining({
      reason: expect.stringContaining('取消匹配'),
      idempotencyKey: expect.stringContaining('bank-reconciliation-cancel-match-'),
    }))

    await reconciliation.wrapper.findAll('[data-test="select-statement-candidate"]')[0].setValue(true)
    await reconciliation.wrapper.findAll('[data-test="select-ledger-candidate"]')[0].setValue(true)
    await flushPromises()
    for (const exceptionType of ['BANK_ONLY_CREDIT', 'BANK_ONLY_DEBIT', 'BOOK_ONLY_DEBIT', 'BOOK_ONLY_CREDIT']) {
      await reconciliation.wrapper.find(`[data-test="classify-bank-exception-${exceptionType}"]`).trigger('click')
      await flushPromises()
    }
    expect(financialCloseApiMock.bankReconciliations.createException).toHaveBeenCalledTimes(4)
    expect(financialCloseApiMock.bankReconciliations.createException.mock.calls.map((call) => call[1].exceptionType)).toEqual([
      'BANK_ONLY_CREDIT',
      'BANK_ONLY_DEBIT',
      'BOOK_ONLY_DEBIT',
      'BOOK_ONLY_CREDIT',
    ])

    await reconciliation.wrapper.find('[data-test="calculate-bank-reconciliation"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.bankReconciliations.calculate).toHaveBeenCalledWith(201, expect.objectContaining({
      version: 4,
      reason: expect.stringContaining('重算'),
      idempotencyKey: expect.stringContaining('bank-reconciliation-calculate-'),
    }))
    await reconciliation.wrapper.find('[data-test="confirm-bank-reconciliation"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.bankReconciliations.confirm).toHaveBeenCalledWith(201, expect.objectContaining({
      version: 4,
      reason: expect.stringContaining('确认对账'),
      idempotencyKey: expect.stringContaining('bank-reconciliation-confirm-'),
    }))
    await reconciliation.wrapper.find('[data-test="reopen-bank-reconciliation"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.bankReconciliations.reopen).toHaveBeenCalledWith(201, expect.objectContaining({
      reason: expect.stringContaining('重开对账'),
      idempotencyKey: expect.stringContaining('bank-reconciliation-reopen-'),
    }))
  })

  it('银行匹配明细支持 1 对多和多对 1，并拒绝累计匹配金额超过任一侧剩余金额', async () => {
    financialCloseApiMock.bankReconciliations.candidates.mockResolvedValue({
      statementLines: [
        { id: 151, statementNo: 'BS-202607-001', amount: '120.00', remainingAmount: '120.00' },
        { id: 152, statementNo: 'BS-202607-002', amount: '60.00', remainingAmount: '60.00' },
      ],
      ledgerEntries: [
        { id: 301, voucherNo: '记-202607-0001', amount: '60.00', remainingAmount: '60.00' },
        { id: 302, voucherNo: '记-202607-0002', amount: '60.00', remainingAmount: '60.00' },
        { id: 303, voucherNo: '记-202607-0003', amount: '120.00', remainingAmount: '120.00' },
      ],
      page: 1,
      pageSize: 20,
      total: 3,
    })
    const oneToMany = await mountFinancialCloseView(BankReconciliationView, '/gl/bank-reconciliation')

    await oneToMany.wrapper.findAll('[data-test="select-statement-candidate"]')[0].setValue(true)
    await oneToMany.wrapper.findAll('[data-test="select-ledger-candidate"]')[0].setValue(true)
    await oneToMany.wrapper.findAll('[data-test="select-ledger-candidate"]')[1].setValue(true)
    await flushPromises()
    const oneToManyAmounts = oneToMany.wrapper.findAll('input[name="bank-match-detail-amount"]')
    expect(oneToManyAmounts).toHaveLength(2)
    await oneToManyAmounts[0].setValue('60.00')
    await oneToManyAmounts[1].setValue('60.00')
    await oneToMany.wrapper.find('[data-test="create-bank-match"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.bankReconciliations.createMatch).toHaveBeenLastCalledWith(201, expect.objectContaining({
      matches: [
        { statementLineId: 151, ledgerEntryId: 301, amount: '60.00' },
        { statementLineId: 151, ledgerEntryId: 302, amount: '60.00' },
      ],
    }))

    financialCloseApiMock.bankReconciliations.createMatch.mockClear()
    const manyToOne = await mountFinancialCloseView(BankReconciliationView, '/gl/bank-reconciliation')
    await manyToOne.wrapper.findAll('[data-test="select-statement-candidate"]')[0].setValue(true)
    await manyToOne.wrapper.findAll('[data-test="select-statement-candidate"]')[1].setValue(true)
    await manyToOne.wrapper.findAll('[data-test="select-ledger-candidate"]')[2].setValue(true)
    await flushPromises()
    const manyToOneAmounts = manyToOne.wrapper.findAll('input[name="bank-match-detail-amount"]')
    expect(manyToOneAmounts).toHaveLength(2)
    await manyToOneAmounts[0].setValue('120.01')
    await flushPromises()
    expect(manyToOne.wrapper.find('[data-test="create-bank-match"]').attributes('disabled')).toBeDefined()
    expect(manyToOne.wrapper.text()).toContain('银行流水 BS-202607-001 匹配金额超过剩余金额')
    await manyToOneAmounts[0].setValue('60.00')
    await manyToOneAmounts[1].setValue('60.00')
    await manyToOne.wrapper.find('[data-test="create-bank-match"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.bankReconciliations.createMatch).toHaveBeenLastCalledWith(201, expect.objectContaining({
      matches: [
        { statementLineId: 151, ledgerEntryId: 303, amount: '60.00' },
        { statementLineId: 152, ledgerEntryId: 303, amount: '60.00' },
      ],
    }))
  })

  it('银行未达分类必须显式选择正确侧候选，并提交选中对象与真实剩余金额', async () => {
    const reconciliation = await mountFinancialCloseView(BankReconciliationView, '/gl/bank-reconciliation')

    expect(reconciliation.wrapper.find('[data-test="classify-bank-exception-BANK_ONLY_CREDIT"]').attributes('disabled')).toBeDefined()
    expect(reconciliation.wrapper.find('[data-test="classify-bank-exception-BOOK_ONLY_DEBIT"]').attributes('disabled')).toBeDefined()
    expect(reconciliation.wrapper.text()).toContain('请选择银行流水候选')
    expect(reconciliation.wrapper.text()).toContain('请选择总账分录候选')

    await reconciliation.wrapper.findAll('[data-test="select-ledger-candidate"]')[1].setValue(true)
    await flushPromises()
    expect(reconciliation.wrapper.find('[data-test="classify-bank-exception-BANK_ONLY_CREDIT"]').attributes('disabled')).toBeDefined()
    await reconciliation.wrapper.find('[data-test="classify-bank-exception-BOOK_ONLY_DEBIT"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.bankReconciliations.createException).toHaveBeenCalledWith(201, expect.objectContaining({
      exceptionType: 'BOOK_ONLY_DEBIT',
      statementLineId: null,
      ledgerEntryId: 302,
      amount: '60.00',
    }))
    expect(JSON.stringify(financialCloseApiMock.bankReconciliations.createException.mock.calls.at(-1)?.[1])).not.toContain('0.20')
  })

  it('税务设置页面维护档案、税率和票种，并展示后端真实 DTO 字段', async () => {
    const settings = await mountFinancialCloseView(TaxSettingsView, '/gl/tax-settings')
    expect(settings.wrapper.text()).toContain('税务基础设置')
    expect(settings.wrapper.text()).toContain('基础汇总/估算，非正式申报')
    expect(settings.wrapper.text()).toContain('91330000123456789X')
    expect(settings.wrapper.text()).toContain('0.1300')
    expect(settings.wrapper.text()).toContain('数电专票')
    expect(settings.wrapper.text()).toContain('启用')
    expect(settings.wrapper.text()).toContain('一般纳税人')
    expect(settings.wrapper.text()).not.toContain('报送成功')
    expect(settings.wrapper.text()).not.toContain('税控同步')
    const taxPaginations = settings.wrapper.findAllComponents({ name: 'ElPagination' })
    expect(taxPaginations).toHaveLength(2)
    expect(taxPaginations.map((pagination) => pagination.props('pageSizes'))).toEqual([
      [10, 20, 50, 100],
      [10, 20, 50, 100],
    ])
    expect(financialCloseApiMock.taxRateRules.list).toHaveBeenCalledWith({ page: 1, pageSize: 10 })
    expect(financialCloseApiMock.taxInvoiceTypes.list).toHaveBeenCalledWith({ page: 1, pageSize: 10 })

    taxPaginations[0].vm.$emit('size-change', 20)
    await flushPromises()
    expect(financialCloseApiMock.taxRateRules.list).toHaveBeenLastCalledWith({ page: 1, pageSize: 20 })
    expect(financialCloseApiMock.taxInvoiceTypes.list).toHaveBeenLastCalledWith({ page: 1, pageSize: 10 })

    taxPaginations[1].vm.$emit('current-change', 2)
    await flushPromises()
    expect(financialCloseApiMock.taxRateRules.list).toHaveBeenLastCalledWith({ page: 1, pageSize: 20 })
    expect(financialCloseApiMock.taxInvoiceTypes.list).toHaveBeenLastCalledWith({ page: 2, pageSize: 10 })

    await settings.wrapper.find('[data-test="open-tax-settings-maintenance"]').trigger('click')
    await flushPromises()
    await settings.wrapper.find('input[name="tax-profile-credit-code"]').setValue('91330000999999999X')
    await settings.wrapper.find('[data-test="save-tax-profile"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.taxProfiles.update).toHaveBeenCalledWith(expect.objectContaining({
      taxpayerType: 'GENERAL',
      creditCode: '91330000999999999X',
      vatPeriodicity: 'MONTHLY',
      incomeTaxRate: '0.2500',
      urbanMaintenanceRate: '0.0700',
      version: 2,
      idempotencyKey: expect.stringContaining('tax-profile-save-'),
    }))

    await settings.wrapper.find('[data-test="create-tax-rate-rule"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.taxRateRules.create).toHaveBeenCalledWith(expect.objectContaining({
      taxType: 'VAT',
      rateCode: 'VAT_13',
      rateValue: '0.1300',
      idempotencyKey: expect.stringContaining('tax-rate-rule-create-'),
    }))

    await settings.wrapper.find('[data-test="create-tax-invoice-type"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.taxInvoiceTypes.create).toHaveBeenCalledWith(expect.objectContaining({
      code: 'DIGITAL_VAT_SPECIAL',
      name: '数电专票',
      direction: 'OUTPUT',
      deductible: true,
      idempotencyKey: expect.stringContaining('tax-invoice-type-create-'),
    }))
  })

  it('税额汇总页面支持创建、计算、调整、确认、生成凭证和状态权限禁用', async () => {
    const summary = await mountFinancialCloseView(TaxSummaryView, '/gl/tax-summary')
    expect(summary.wrapper.text()).toContain('税额汇总')
    expect(summary.wrapper.text()).toContain('基础汇总/估算，非正式申报')
    expect(summary.wrapper.text()).toContain('380.00')
    expect(summary.wrapper.text()).toContain('来源受限')
    expect(summary.wrapper.text()).not.toContain('失效需后端公开端点')
    expect(summary.wrapper.text()).not.toContain('失效')
    await summary.wrapper.find('[data-test="create-tax-summary"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.taxSummaries.create).toHaveBeenCalledWith(expect.objectContaining({
      periodCode: '2026-07',
      taxType: 'VAT',
      idempotencyKey: expect.stringContaining('tax-summary-create-'),
    }))
    await summary.wrapper.find('[data-test="calculate-tax-summary"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.taxSummaries.calculate).toHaveBeenCalledWith(501, expect.objectContaining({
      version: 6,
      idempotencyKey: expect.stringContaining('tax-summary-calculate-'),
    }))
    await summary.wrapper.find('[data-test="add-tax-summary-adjustment"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.taxSummaries.addAdjustment).toHaveBeenCalledWith(501, expect.objectContaining({
      version: 6,
      adjustmentType: 'VAT_INCREASE',
      amount: '1.00',
      reason: expect.stringContaining('调整'),
      idempotencyKey: expect.stringContaining('tax-summary-adjust-'),
    }))
    await clickTeleportedAction(summary.wrapper, 'confirm-tax-summary')
    expect(financialCloseApiMock.taxSummaries.confirm).toHaveBeenCalledWith(501, expect.objectContaining({
      reason: expect.stringContaining('确认税额基础汇总'),
    }))
    await clickTeleportedAction(summary.wrapper, 'create-tax-voucher-draft')
    expect(financialCloseApiMock.taxSummaries.createVoucherDraft).toHaveBeenCalledWith(501, expect.objectContaining({
      idempotencyKey: expect.stringContaining('tax-voucher-draft-'),
    }))

    financialCloseApiMock.taxSummaries.list.mockResolvedValueOnce(page([{
      id: 503,
      periodCode: '2026-07',
      taxType: 'VAT',
      status: 'CONFIRMED',
      outputTaxAmount: '500.00',
      inputTaxAmount: '120.00',
      payableTaxAmount: '380.00',
      amountVisible: true,
      sourceVisible: true,
      bankSensitiveVisible: false,
      version: 9,
      allowedActions: [],
      actionDisabledReasons: { CALCULATE: '已确认不可重新计算', CONFIRM: '已确认不可重复确认', GENERATE_VOUCHER: '无生成凭证权限' },
    }]))
    const readonlySummary = await mountFinancialCloseView(TaxSummaryView, '/gl/tax-summary', ['financial-close:tax-summary:view'])
    expect(readonlySummary.wrapper.find('[data-test="calculate-tax-summary"]').attributes('disabled')).toBeDefined()
    await openMoreActions(readonlySummary.wrapper)
    expect(teleportedAction('confirm-tax-summary').getAttribute('disabled')).not.toBeNull()
    expect(readonlySummary.wrapper.text()).toContain('已确认不可重新计算')
    expect(readonlySummary.wrapper.text()).toContain('无生成凭证权限')
  })

  it('税额汇总只消费后端 allowedActions 和 actionDisabledReasons，缺失动作字段时失败关闭', async () => {
    financialCloseApiMock.taxSummaries.list.mockResolvedValueOnce(page([
      {
        id: 504,
        periodCode: '2026-07',
        taxType: 'VAT',
        status: 'CALCULATED',
        payableTaxAmount: '380.00',
        amountVisible: true,
        sourceVisible: true,
        bankSensitiveVisible: false,
        version: 3,
      },
      {
        id: 505,
        periodCode: '2026-07',
        taxType: 'VAT',
        status: 'CALCULATED',
        payableTaxAmount: '390.00',
        amountVisible: true,
        sourceVisible: true,
        bankSensitiveVisible: false,
        version: 4,
        allowedActions: ['CALCULATE'],
        actionDisabledReasons: { CONFIRM: '后端冻结为不可确认' },
      },
    ]))
    const summary = await mountFinancialCloseView(TaxSummaryView, '/gl/tax-summary')
    const calculateButtons = summary.wrapper.findAll('[data-test="calculate-tax-summary"]')

    expect(calculateButtons[0].attributes('disabled')).toBeDefined()
    expect(summary.wrapper.text()).toContain('当前状态不允许操作')
    expect(calculateButtons[1].attributes('disabled')).toBeUndefined()
    expect(summary.wrapper.text()).toContain('后端冻结为不可确认')

    await calculateButtons[1].trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.taxSummaries.calculate).toHaveBeenCalledWith(505, expect.objectContaining({
      version: 4,
      idempotencyKey: expect.stringContaining('tax-summary-calculate-'),
    }))
  })

  it('税款台账页面显示权限受控银行脱敏标识，支持登记缴纳和更正', async () => {
    const paymentsWithData = await mountFinancialCloseView(TaxPaymentsView, '/gl/tax-payments')
    expect(paymentsWithData.wrapper.text()).toContain('税款缴纳台账')
    expect(paymentsWithData.wrapper.text()).toContain('增值税')
    expect(paymentsWithData.wrapper.text()).toContain('中国银行 基本户 ****1234')
    expect(paymentsWithData.wrapper.text()).toContain('账号已脱敏')
    expect(paymentsWithData.wrapper.text()).not.toContain('VAT')
    await paymentsWithData.wrapper.find('[data-test="open-tax-payment-create"]').trigger('click')
    await flushPromises()
    await paymentsWithData.wrapper.find('input[name="tax-payment-summary-id"]').setValue('501')
    await paymentsWithData.wrapper.find('[data-test="save-tax-payment"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.taxPayments.create).toHaveBeenCalledWith(expect.objectContaining({
      summaryId: 501,
      taxType: 'VAT',
      amount: '380.00',
      paymentDate: '2026-07-25',
      reason: expect.stringContaining('登记税款缴纳'),
      idempotencyKey: expect.stringContaining('tax-payment-create-'),
    }))

    await paymentsWithData.wrapper.find('[data-test="correct-tax-payment"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.taxPayments.correct).toHaveBeenCalledWith(601, expect.objectContaining({
      amount: '390.00',
      reason: expect.stringContaining('更正税款缴纳'),
      idempotencyKey: expect.stringContaining('tax-payment-correct-'),
    }))

    financialCloseApiMock.taxPayments.list.mockResolvedValueOnce(page([]))
    const payments = await mountFinancialCloseView(TaxPaymentsView, '/gl/tax-payments')
    expect(payments.wrapper.text()).toContain('税款缴纳台账')
    expect(payments.wrapper.text()).toContain('只追溯已记账凭证或合法付款')
    expect(payments.wrapper.text()).toContain('暂无税款缴纳记录')
  })

  it('写动作统一展示 401、403、409、422 后端反馈，并保持按钮失败关闭', async () => {
    financialCloseApiMock.taxSummaries.calculate
      .mockRejectedValueOnce(new Error('未登录或会话已失效（401）'))
      .mockRejectedValueOnce(new Error('无权执行税额计算（403）'))
      .mockRejectedValueOnce(new Error('版本冲突，请刷新后重试（409）'))
      .mockRejectedValueOnce(new Error('调整原因至少 2 个中文字符（422）'))
    const summary = await mountFinancialCloseView(TaxSummaryView, '/gl/tax-summary')

    for (const expected of ['未登录或会话已失效（401）', '无权执行税额计算（403）', '版本冲突，请刷新后重试（409）', '调整原因至少 2 个中文字符（422）']) {
      await summary.wrapper.find('[data-test="calculate-tax-summary"]').trigger('click')
      await flushPromises()
      expect(summary.wrapper.text()).toContain(expected)
    }
  })
})
