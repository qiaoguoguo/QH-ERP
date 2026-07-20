import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
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
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
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
      direction: 'INFLOW',
      amount: '120.00',
      status: 'PARTIAL_MATCHED',
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
      rows: [{ rowNo: 3, valid: false, message: '金额格式错误' }],
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
      allowedActions: ['MATCH', 'CONFIRM'],
      actionDisabledReasons: {},
    }]))
    financialCloseApiMock.bankReconciliations.get.mockResolvedValue({
      id: 201,
      reconciliationNo: 'BR-202607-001',
      periodCode: '2026-07',
      status: 'READY',
      bankEndingBalance: '1000.10',
      glEndingBalance: '999.90',
      adjustedBankBalance: '1000.10',
      adjustedBookBalance: '1000.10',
      difference: '0.00',
      exceptions: [{ category: 'BANK_RECEIVED_NOT_BOOKED', amount: '0.20', explanation: '银行已收企业未入账' }],
      amountVisible: true,
      sourceVisible: true,
      bankSensitiveVisible: false,
      version: 4,
      allowedActions: ['MATCH', 'CONFIRM'],
      actionDisabledReasons: {},
    })
    financialCloseApiMock.bankReconciliations.candidates.mockResolvedValue({
      statementLines: [{ id: 151, statementNo: 'BS-202607-001', amount: '120.00', selected: false }],
      ledgerEntries: [{ id: 301, voucherNo: '记-202607-0001', amount: '120.00', selected: false }],
      page: 1,
      pageSize: 20,
      total: 25,
    })
    financialCloseApiMock.bankReconciliations.createMatch.mockResolvedValue({ id: 901, version: 1 })
    financialCloseApiMock.bankReconciliations.confirm.mockResolvedValue({ id: 201, status: 'CONFIRMED', version: 5 })
    financialCloseApiMock.taxProfiles.current.mockResolvedValue({
      id: 401,
      companyName: '齐辉制造',
      taxpayerType: 'GENERAL',
      unifiedSocialCreditCodeMasked: '9133********1234',
      cityMaintenanceTaxRate: '7',
      amountVisible: true,
      sourceVisible: true,
      bankSensitiveVisible: false,
      version: 2,
      allowedActions: ['UPDATE'],
      actionDisabledReasons: {},
    })
    financialCloseApiMock.taxRateRules.list.mockResolvedValue(page([{ id: 411, taxType: 'VAT', rate: '13', effectiveFrom: '2026-01-01', enabled: true }], 1, 10))
    financialCloseApiMock.taxInvoiceTypes.list.mockResolvedValue(page([{ id: 421, code: 'DIGITAL_VAT_SPECIAL', name: '数电专票', enabled: true }], 1, 10))
    financialCloseApiMock.taxSummaries.list.mockResolvedValue(page([{
      id: 501,
      periodCode: '2026-07',
      status: 'CALCULATED',
      disclaimer: '基础汇总/估算，非正式申报',
      outputTaxAmount: '500.00',
      inputTaxAmount: '120.00',
      payableTaxAmount: '380.00',
      retainedTaxAmount: '0.00',
      estimatedIncomeTaxAmount: '200.00',
      amountVisible: true,
      sourceVisible: false,
      bankSensitiveVisible: false,
      version: 6,
      allowedActions: ['CALCULATE', 'CONFIRM', 'GENERATE_VOUCHER'],
      actionDisabledReasons: {},
    }]))
    financialCloseApiMock.taxSummaries.calculate.mockResolvedValue({ id: 501, status: 'CALCULATED', payableTaxAmount: '380.00', version: 7 })
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
      amountVisible: true,
      sourceVisible: true,
      bankSensitiveVisible: false,
      version: 2,
      allowedActions: ['CORRECT'],
      actionDisabledReasons: {},
    }]))
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
  })

  it('银行账户、流水和对账页面覆盖账号脱敏、CSV 错误、候选池不受十条限制、多对多匹配和确认只读', async () => {
    const accounts = await mountFinancialCloseView(BankAccountsView, '/gl/bank-accounts')
    expect(accounts.wrapper.text()).toContain('银行账户')
    expect(accounts.wrapper.text()).toContain('****1234')
    expect(accounts.wrapper.text()).toContain('1002.01')
    expect(accounts.wrapper.find('.table-scroll').exists()).toBe(true)
    await accounts.wrapper.find('[data-test="disable-bank-account"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.bankAccounts.disable).toHaveBeenCalledWith(101, expect.objectContaining({
      version: 3,
      reason: expect.stringContaining('停用'),
      idempotencyKey: expect.stringContaining('bank-account-disable-'),
    }))

    const statements = await mountFinancialCloseView(BankStatementsView, '/gl/bank-statements')
    expect(statements.wrapper.text()).toContain('银行流水')
    expect(statements.wrapper.text()).toContain('PARTIAL_MATCHED')
    await statements.wrapper.find('[data-test="preview-bank-statement-import"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.bankStatements.importPreview).toHaveBeenCalledWith(expect.objectContaining({
      fileName: 'bank-statement.csv',
      idempotencyKey: expect.stringContaining('bank-statement-preview-'),
    }))
    expect(statements.wrapper.text()).toContain('金额格式错误')
    await statements.wrapper.find('[data-test="ignore-bank-statement-line"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.bankStatements.ignoreLine).toHaveBeenCalledWith(151, expect.objectContaining({
      reason: expect.stringContaining('忽略'),
    }))

    const reconciliation = await mountFinancialCloseView(BankReconciliationView, '/gl/bank-reconciliation')
    expect(reconciliation.wrapper.text()).toContain('银行对账工作台')
    expect(reconciliation.wrapper.text()).toContain('候选池不受主列表十条分页限制')
    expect(reconciliation.wrapper.text()).toContain('差额')
    expect(reconciliation.wrapper.text()).toContain('0.00')
    expect(financialCloseApiMock.bankReconciliations.candidates).toHaveBeenCalledWith(201, expect.objectContaining({ page: 1, pageSize: 20 }))
    await reconciliation.wrapper.find('[data-test="create-bank-match"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.bankReconciliations.createMatch).toHaveBeenCalledWith(201, expect.objectContaining({
      statementLineIds: [151],
      ledgerEntryIds: [301],
      idempotencyKey: expect.stringContaining('bank-reconciliation-match-'),
    }))
    await reconciliation.wrapper.find('[data-test="confirm-bank-reconciliation"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.bankReconciliations.confirm).toHaveBeenCalledWith(201, expect.objectContaining({
      version: 4,
      reason: expect.stringContaining('确认对账'),
      idempotencyKey: expect.stringContaining('bank-reconciliation-confirm-'),
    }))
  })

  it('税务设置、税额汇总和税款台账固定非申报边界，展示金额/来源权限和零数据状态', async () => {
    const settings = await mountFinancialCloseView(TaxSettingsView, '/gl/tax-settings')
    expect(settings.wrapper.text()).toContain('税务基础设置')
    expect(settings.wrapper.text()).toContain('基础汇总/估算，非正式申报')
    expect(settings.wrapper.text()).toContain('9133********1234')
    expect(settings.wrapper.text()).toContain('13')
    expect(settings.wrapper.text()).toContain('数电专票')
    expect(settings.wrapper.text()).not.toContain('报送成功')
    expect(settings.wrapper.text()).not.toContain('税控同步')

    const summary = await mountFinancialCloseView(TaxSummaryView, '/gl/tax-summary')
    expect(summary.wrapper.text()).toContain('税额汇总')
    expect(summary.wrapper.text()).toContain('基础汇总/估算，非正式申报')
    expect(summary.wrapper.text()).toContain('380.00')
    expect(summary.wrapper.text()).toContain('来源受限')
    await summary.wrapper.find('[data-test="calculate-tax-summary"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.taxSummaries.calculate).toHaveBeenCalledWith(501, expect.objectContaining({
      version: 6,
      idempotencyKey: expect.stringContaining('tax-summary-calculate-'),
    }))
    await summary.wrapper.find('[data-test="confirm-tax-summary"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.taxSummaries.confirm).toHaveBeenCalledWith(501, expect.objectContaining({
      reason: expect.stringContaining('确认税额基础汇总'),
    }))
    await summary.wrapper.find('[data-test="create-tax-voucher-draft"]').trigger('click')
    await flushPromises()
    expect(financialCloseApiMock.taxSummaries.createVoucherDraft).toHaveBeenCalledWith(501, expect.objectContaining({
      idempotencyKey: expect.stringContaining('tax-voucher-draft-'),
    }))

    financialCloseApiMock.taxPayments.list.mockResolvedValueOnce(page([]))
    const payments = await mountFinancialCloseView(TaxPaymentsView, '/gl/tax-payments')
    expect(payments.wrapper.text()).toContain('税款缴纳台账')
    expect(payments.wrapper.text()).toContain('只追溯已记账凭证或合法付款')
    expect(payments.wrapper.text()).toContain('暂无税款缴纳记录')
  })
})
