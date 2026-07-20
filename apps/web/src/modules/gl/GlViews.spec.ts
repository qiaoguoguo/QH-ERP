import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import GlAccountingPeriodsView from './GlAccountingPeriodsView.vue'
import GlAccountsView from './GlAccountsView.vue'
import GlAuxiliariesView from './GlAuxiliariesView.vue'
import GlPostingRulesView from './GlPostingRulesView.vue'
import GlVoucherWorkbenchView from './GlVoucherWorkbenchView.vue'
import GlVoucherFormView from './GlVoucherFormView.vue'
import GlVoucherDetailView from './GlVoucherDetailView.vue'
import GlGeneralLedgerView from './GlGeneralLedgerView.vue'
import GlDetailLedgerView from './GlDetailLedgerView.vue'
import GlAccountBalanceView from './GlAccountBalanceView.vue'
import GlTrialBalanceView from './GlTrialBalanceView.vue'
import { useAuthStore } from '../../stores/authStore'
import { useConfirmActionMock } from '../../test/setup'

const glApiMock = vi.hoisted(() => ({
  ledger: { get: vi.fn(), initialize: vi.fn() },
  accountingPeriods: { list: vi.fn(), create: vi.fn() },
  accounts: { list: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), disable: vi.fn() },
  auxDimensions: { list: vi.fn(), create: vi.fn(), update: vi.fn(), items: vi.fn(), candidates: vi.fn() },
  postingRules: { list: vi.fn(), get: vi.fn(), create: vi.fn(), newVersion: vi.fn(), update: vi.fn(), validate: vi.fn(), activate: vi.fn(), disable: vi.fn() },
  vouchers: { list: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), fromFinanceDraft: vi.fn(), refreshSource: vi.fn(), submit: vi.fn(), withdraw: vi.fn(), cancel: vi.fn(), createReversal: vi.fn() },
  ledgers: { general: vi.fn(), detail: vi.fn() },
  accountBalances: { list: vi.fn() },
  trialBalance: { get: vi.fn() },
}))

const confirmActionMock = useConfirmActionMock()

vi.mock('../../shared/api/glApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/glApi')>()),
  glApi: glApiMock,
}))

function page<T>(items: T[], pageNumber = 1, pageSize = 10) {
  return { items, total: items.length, page: pageNumber, pageSize }
}

async function mountGlView(component: object, path = '/gl', permissions: string[] = [
  'gl:period:view',
  'gl:period:initialize',
  'gl:period:create',
  'gl:account:view',
  'gl:account:create',
  'gl:account:update',
  'gl:account:disable',
  'gl:auxiliary:view',
  'gl:auxiliary:manage',
  'gl:rule:view',
  'gl:rule:manage',
  'gl:voucher:view',
  'gl:voucher:create',
  'gl:voucher:update',
  'gl:voucher:submit',
  'gl:voucher:cancel',
  'gl:voucher:reverse',
  'gl:ledger:view',
  'gl:balance:view',
  'gl:amount:view',
  'gl:source:view',
]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'gl_user', displayName: '会计用户', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/gl/accounting-periods', name: 'gl-accounting-periods', component },
      { path: '/gl/accounts', name: 'gl-accounts', component },
      { path: '/gl/auxiliaries', name: 'gl-auxiliaries', component },
      { path: '/gl/posting-rules', name: 'gl-posting-rules', component },
      { path: '/gl/vouchers', name: 'gl-vouchers', component },
      { path: '/gl/vouchers/create', name: 'gl-voucher-create', component },
      { path: '/gl/vouchers/:id', name: 'gl-voucher-detail', component },
      { path: '/gl/vouchers/:id/edit', name: 'gl-voucher-edit', component },
      { path: '/gl/ledgers/general', name: 'gl-ledger-general', component },
      { path: '/gl/ledgers/detail', name: 'gl-ledger-detail', component },
      { path: '/gl/account-balances', name: 'gl-account-balances', component },
      { path: '/gl/trial-balance', name: 'gl-trial-balance', component },
      { path: '/finance/voucher-drafts/:id', name: 'finance-voucher-draft-detail', component: { template: '<div />' } },
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

const accountRecord = {
  id: 1002,
  code: '1002',
  name: '银行存款',
  category: 'ASSET',
  level: 1,
  parentId: null,
  isLeaf: true,
  postable: true,
  balanceDirection: 'DEBIT',
  enabled: true,
  auxiliaryRequirements: [{ dimensionCode: 'CUSTOMER', dimensionName: '客户', requirement: 'OPTIONAL' }],
  version: 2,
  allowedActions: ['UPDATE', 'DISABLE'],
  actionDisabledReasons: { DISABLE: '已被凭证使用' },
}

const voucherRecord = {
  id: 91,
  draftNo: 'GLD-202607-0001',
  voucherType: 'GENERAL',
  voucherNo: null,
  voucherDate: '2026-07-20',
  accountingPeriodCode: '2026-07',
  status: 'DRAFT',
  summary: '销售发票转正式凭证',
  sourceType: 'FIN_VOUCHER_DRAFT',
  sourceId: 61,
  sourceNo: 'VD-001',
  sourceOriginalType: 'SALES_INVOICE',
  sourceOriginalId: 11,
  sourceOriginalNo: 'SI-001',
  sourceOriginalVersion: 7,
  sourceOriginalFingerprint: 'fp-si-001',
  currency: 'CNY',
  debitTotal: '120.00',
  creditTotal: '120.00',
  amountVisible: true,
  sourceVisible: true,
  version: 4,
  allowedActions: ['UPDATE', 'SUBMIT', 'CANCEL'],
  actionDisabledReasons: {},
  lines: [
    { lineNo: 1, summary: '确认应收', accountId: 1122, accountCode: '1122', accountName: '应收账款', debitAmount: '120.00', creditAmount: '0.00', auxiliaryItems: [{ dimensionCode: 'CUSTOMER', objectName: '齐辉客户' }], normalizedFactCode: 'SALES_RECEIVABLE' },
    { lineNo: 2, summary: '确认收入', accountId: 6001, accountCode: '6001', accountName: '主营业务收入', debitAmount: '0.00', creditAmount: '100.00', auxiliaryItems: [], normalizedFactCode: 'SALES_REVENUE' },
    { lineNo: 3, summary: '销项税额', accountId: 222101, accountCode: '2221.01', accountName: '应交税费-销项税额', debitAmount: '0.00', creditAmount: '20.00', auxiliaryItems: [], normalizedFactCode: 'OUTPUT_TAX' },
  ],
  approvalSummary: { sceneCode: 'GL_VOUCHER_POST', status: 'SUBMITTED', submittedAt: '2026-07-20T10:00:00+08:00' },
  reversalSummary: null,
  auditSummary: [{ action: 'CREATE', operatorName: '会计', createdAt: '2026-07-20T10:00:00+08:00', comment: '创建' }],
}

describe('031 会计核算页面族', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    confirmActionMock.mockResolvedValue(true)
    glApiMock.ledger.get.mockResolvedValue({ ledgerCode: 'MAIN', ledgerName: '总账', baseCurrency: 'CNY', initialized: true, startPeriodCode: '2026-07' })
    glApiMock.accountingPeriods.list.mockResolvedValue(page([{ id: 1, periodCode: '2026-07', startDate: '2026-07-01', endDate: '2026-07-31', status: 'OPEN', voucherCount: 2, lastPostedAt: '2026-07-20T10:00:00+08:00' }]))
    glApiMock.accountingPeriods.create.mockResolvedValue({ id: 2, periodCode: '2026-08', startDate: '2026-08-01', endDate: '2026-08-31', status: 'OPEN' })
    glApiMock.accounts.list.mockResolvedValue(page([accountRecord]))
    glApiMock.accounts.get.mockResolvedValue(accountRecord)
    glApiMock.accounts.create.mockResolvedValue({ ...accountRecord, id: 100201, code: '100201', name: '新增下级科目', parentId: 1002, level: 2 })
    glApiMock.accounts.update.mockResolvedValue({ ...accountRecord, name: '银行存款-更新' })
    glApiMock.accounts.disable.mockResolvedValue({ ...accountRecord, enabled: false })
    glApiMock.auxDimensions.list.mockResolvedValue(page([{ id: 1, code: 'CUSTOMER', name: '客户', dimensionType: 'SYSTEM', enabled: true, itemCount: 3, version: 1, allowedActions: [], actionDisabledReasons: {} }]))
    glApiMock.auxDimensions.items.mockResolvedValue(page([{ objectId: 88, objectCode: 'CUS-088', objectName: '齐辉客户', restricted: false }], 1, 100))
    glApiMock.auxDimensions.candidates.mockImplementation((_code: string, params: { keyword?: string; page?: number; pageSize?: number }) => {
      if (params.keyword === '后段') {
        return Promise.resolve(page([{ objectId: 150, objectCode: 'CUS-150', objectName: '第 150 项客户', restricted: false }], 1, params.pageSize ?? 20))
      }
      return Promise.resolve(page([{ objectId: 88, objectCode: 'CUS-088', objectName: '齐辉客户', restricted: false }], 1, params.pageSize ?? 20))
    })
    glApiMock.auxDimensions.create.mockResolvedValue({ id: 2, code: 'REGION', name: '区域', dimensionType: 'CUSTOM', enabled: true, itemCount: 0, version: 1, allowedActions: ['UPDATE'], actionDisabledReasons: {} })
    glApiMock.auxDimensions.update.mockResolvedValue({ id: 1, code: 'CUSTOMER', name: '客户', dimensionType: 'SYSTEM', enabled: false, itemCount: 3, version: 2, allowedActions: [], actionDisabledReasons: { UPDATE: '系统维度不可改编码' } })
    glApiMock.postingRules.list.mockResolvedValue(page([{ id: 1, sourceType: 'SALES_INVOICE', sourceVariant: 'STANDARD', versionNo: 2, status: 'ACTIVE', validationStatus: 'VALID', lineCount: 3, allowedActions: ['DISABLE'], actionDisabledReasons: {} }]))
    glApiMock.postingRules.get.mockResolvedValue({ id: 1, sourceType: 'SALES_INVOICE', sourceVariant: 'STANDARD', versionNo: 2, status: 'ACTIVE', validationStatus: 'VALID', lineCount: 3, allowedActions: ['NEW_VERSION', 'VALIDATE', 'DISABLE'], actionDisabledReasons: {}, lines: [{ factCode: 'SALES_RECEIVABLE', direction: 'DEBIT', accountCode: '1122' }] })
    glApiMock.postingRules.newVersion.mockResolvedValue({ id: 2, sourceType: 'SALES_INVOICE', sourceVariant: 'STANDARD', versionNo: 3, status: 'DRAFT', allowedActions: ['UPDATE', 'VALIDATE', 'ACTIVATE'], actionDisabledReasons: {} })
    glApiMock.postingRules.validate.mockResolvedValue({ id: 1, sourceType: 'SALES_INVOICE', sourceVariant: 'STANDARD', versionNo: 2, status: 'ACTIVE', validationStatus: 'VALID', allowedActions: ['DISABLE'], actionDisabledReasons: {} })
    glApiMock.postingRules.activate.mockResolvedValue({ id: 1, sourceType: 'SALES_INVOICE', sourceVariant: 'STANDARD', versionNo: 2, status: 'ACTIVE', allowedActions: ['DISABLE'], actionDisabledReasons: {} })
    glApiMock.postingRules.disable.mockResolvedValue({ id: 1, sourceType: 'SALES_INVOICE', sourceVariant: 'STANDARD', versionNo: 2, status: 'DISABLED', allowedActions: [], actionDisabledReasons: {} })
    glApiMock.vouchers.list.mockResolvedValue(page([voucherRecord]))
    glApiMock.vouchers.get.mockResolvedValue(voucherRecord)
    glApiMock.ledgers.general.mockResolvedValue(page([{ periodCode: '2026-07', accountCode: '1002', accountName: '银行存款', openingDebit: '0.00', openingCredit: '0.00', periodDebit: '120.00', periodCredit: '0.00', endingDebit: '120.00', endingCredit: '0.00', balanceDirection: 'DEBIT', balanced: true, restricted: false }]))
    glApiMock.ledgers.detail.mockResolvedValue(page([{ voucherDate: '2026-07-20', voucherNo: '记-202607-0001', summary: '销售收款', accountCode: '1002', accountName: '银行存款', debitAmount: '120.00', creditAmount: '0.00', runningBalance: '120.00', balanceDirection: 'DEBIT', voucherId: 91, sourceSummary: '收款 RC-001', restricted: false }]))
    glApiMock.accountBalances.list.mockResolvedValue(page([{ periodCode: '2026-07', accountCode: '1002', accountName: '银行存款', openingDebit: '0.00', openingCredit: '0.00', periodDebit: null, periodCredit: null, endingDebit: null, endingCredit: null, balanceDirection: 'DEBIT', restricted: true, restrictedReason: '无权查看GL金额' }]))
    glApiMock.trialBalance.get.mockResolvedValue({ balanced: false, openingDebitTotal: '0.00', openingCreditTotal: '0.00', periodDebitTotal: '120.00', periodCreditTotal: '100.00', endingDebitTotal: '120.00', endingCreditTotal: '100.00', differenceAmount: '20.00', differences: [{ accountCode: '2221.01', accountName: '应交税费-销项税额', differenceAmount: '20.00' }], restricted: false })
  })

  it('会计期间页表达总账启用和 OPEN 期间边界，不出现 032 关闭动作', async () => {
    glApiMock.ledger.get.mockResolvedValueOnce({ ledgerCode: 'MAIN', ledgerName: '总账', baseCurrency: 'CNY', initialized: false, startPeriodCode: null })
    const { wrapper } = await mountGlView(GlAccountingPeriodsView, '/gl/accounting-periods')

    expect(wrapper.text()).toContain('会计期间')
    expect(wrapper.text()).toContain('总账未启用')
    expect(wrapper.text()).toContain('初始化总账')
    expect(wrapper.text()).toContain('不同于业务月结')
    expect(wrapper.text()).not.toContain('反关账')
    expect(wrapper.text()).not.toContain('损益结转')
    expect(wrapper.find('input[name="gl-ledger-start-month"]').exists()).toBe(true)
    expect(wrapper.find('.query-form').exists()).toBe(true)
    expect(wrapper.find('.table-scroll').exists()).toBe(true)

    await wrapper.find('input[name="gl-ledger-start-month"]').setValue('2026-07')
    await wrapper.find('[data-test="initialize-gl-ledger"]').trigger('click')
    await flushPromises()
    expect(glApiMock.ledger.initialize).toHaveBeenCalledWith(expect.objectContaining({
      startYearMonth: '2026-07',
      idempotencyKey: expect.stringContaining('gl-ledger-init-'),
    }))

    glApiMock.ledger.get.mockResolvedValueOnce({ ledgerCode: 'MAIN', ledgerName: '总账', baseCurrency: 'CNY', initialized: true, startPeriodCode: '2026-07' })
    const initialized = await mountGlView(GlAccountingPeriodsView, '/gl/accounting-periods')
    await initialized.wrapper.find('[data-test="create-next-gl-period"]').trigger('click')
    await flushPromises()
    expect(glApiMock.accountingPeriods.create).toHaveBeenCalledWith(expect.objectContaining({
      periodCode: '2026-08',
      idempotencyKey: expect.stringContaining('gl-period-create-'),
    }))
  })

  it('科目、辅助和规则页面按 page-standards 渲染宽表、状态和动作禁用原因', async () => {
    const accounts = await mountGlView(GlAccountsView, '/gl/accounts')
    expect(accounts.wrapper.text()).toContain('会计科目')
    expect(accounts.wrapper.text()).toContain('银行存款')
    expect(accounts.wrapper.text()).toContain('客户')
    expect(accounts.wrapper.text()).toContain('已被凭证使用')
    expect(accounts.wrapper.findComponent({ name: 'ElPagination' }).props('pageSizes')).toEqual([10, 20, 50, 100])
    expect(accounts.wrapper.find('.table-scroll').exists()).toBe(true)
    await accounts.wrapper.find('[data-test="create-child-account"]').trigger('click')
    await flushPromises()
    expect(accounts.wrapper.text()).toContain('新增下级科目')
    await accounts.wrapper.find('input[name="gl-account-code"]').setValue('100201')
    await accounts.wrapper.find('input[name="gl-account-name"]').setValue('新增下级科目')
    await accounts.wrapper.find('[data-test="save-gl-account"]').trigger('click')
    await flushPromises()
    expect(glApiMock.accounts.create).toHaveBeenCalledWith(expect.objectContaining({
      parentId: 1002,
      code: '100201',
      name: '新增下级科目',
      idempotencyKey: expect.stringContaining('gl-account-save-'),
    }))
    await accounts.wrapper.find('[data-test="disable-gl-account"]').trigger('click')
    await flushPromises()
    expect(glApiMock.accounts.disable).toHaveBeenCalledWith(1002, expect.objectContaining({
      version: 2,
      reason: expect.stringContaining('停用'),
      idempotencyKey: expect.stringContaining('gl-account-disable-'),
    }))

    const auxiliaries = await mountGlView(GlAuxiliariesView, '/gl/auxiliaries')
    expect(auxiliaries.wrapper.text()).toContain('辅助核算')
    expect(auxiliaries.wrapper.text()).toContain('候选不受主列表分页限制')
    expect(auxiliaries.wrapper.text()).toContain('客户')
    await auxiliaries.wrapper.find('[data-test="view-aux-candidates"]').trigger('click')
    await flushPromises()
    expect(glApiMock.auxDimensions.candidates).toHaveBeenCalledWith('CUSTOMER', expect.objectContaining({ page: 1, pageSize: 20 }))
    expect(auxiliaries.wrapper.text()).toContain('齐辉客户')
    await auxiliaries.wrapper.find('input[name="gl-aux-candidate-keyword"]').setValue('后段')
    await auxiliaries.wrapper.find('[data-test="search-aux-candidates"]').trigger('click')
    await flushPromises()
    expect(glApiMock.auxDimensions.candidates).toHaveBeenLastCalledWith('CUSTOMER', expect.objectContaining({ keyword: '后段', page: 1, pageSize: 20 }))
    expect(auxiliaries.wrapper.text()).toContain('第 150 项客户')
    await auxiliaries.wrapper.find('[data-test="create-aux-dimension"]').trigger('click')
    await flushPromises()
    await auxiliaries.wrapper.find('input[name="gl-aux-code"]').setValue('REGION')
    await auxiliaries.wrapper.find('input[name="gl-aux-name"]').setValue('区域')
    await auxiliaries.wrapper.find('[data-test="save-aux-dimension"]').trigger('click')
    await flushPromises()
    expect(glApiMock.auxDimensions.create).toHaveBeenCalledWith(expect.objectContaining({
      code: 'REGION',
      name: '区域',
      idempotencyKey: expect.stringContaining('gl-aux-save-'),
    }))

    const rules = await mountGlView(GlPostingRulesView, '/gl/posting-rules')
    expect(rules.wrapper.text()).toContain('自动制证规则')
    expect(rules.wrapper.text()).toContain('SALES_INVOICE')
    expect(rules.wrapper.text()).toContain('预览不制证')
    await rules.wrapper.find('[data-test="view-posting-rule"]').trigger('click')
    await flushPromises()
    expect(glApiMock.postingRules.get).toHaveBeenCalledWith(1)
    expect(rules.wrapper.text()).toContain('SALES_RECEIVABLE')
    await rules.wrapper.find('[data-test="validate-posting-rule"]').trigger('click')
    await flushPromises()
    expect(glApiMock.postingRules.validate).toHaveBeenCalledWith(1, expect.objectContaining({
      version: 2,
      idempotencyKey: expect.stringContaining('gl-rule-validate-'),
    }))
  })

  it('凭证工作台展示正式凭证边界、allowedActions、returnTo 和受限金额语义', async () => {
    glApiMock.vouchers.list.mockResolvedValueOnce(page([{ ...voucherRecord, amountVisible: false, debitTotal: null, creditTotal: null, restrictedReason: '无权查看GL金额', allowedActions: ['SUBMIT'], actionDisabledReasons: { CANCEL: '审批中不能取消' } }]))
    const { wrapper, router } = await mountGlView(GlVoucherWorkbenchView, '/gl/vouchers')

    expect(wrapper.text()).toContain('正式凭证工作台')
    expect(wrapper.text()).toContain('GLD-202607-0001')
    expect(wrapper.text()).toContain('无权查看GL金额')
    expect(wrapper.text()).toContain('审批中不能取消')
    expect(wrapper.find('.query-form').exists()).toBe(true)
    expect(wrapper.find('.table-scroll').exists()).toBe(true)

    const detailButton = wrapper.find('[data-test="gl-voucher-detail"]')
    await detailButton.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('gl-voucher-detail')
    expect(router.currentRoute.value.query.returnTo).toBe('/gl/vouchers')
  })

  it('正式凭证来源层与真实业务来源层分开展示，不混用 FIN_VOUCHER_DRAFT 和业务单号', async () => {
    const convertedVoucher = {
      ...voucherRecord,
      sourceType: 'FIN_VOUCHER_DRAFT',
      sourceId: 61,
      sourceOriginalType: 'SALES_INVOICE',
      sourceOriginalId: 11,
      sourceNo: 'VD-001',
      sourceOriginalNo: 'SI-001',
      sourceOriginalVersion: 7,
      sourceOriginalFingerprint: 'fp-si-001',
    }
    glApiMock.vouchers.list.mockResolvedValueOnce(page([convertedVoucher]))
    const workbench = await mountGlView(GlVoucherWorkbenchView, '/gl/vouchers')
    expect(workbench.wrapper.text()).toContain('正式来源 FIN_VOUCHER_DRAFT')
    expect(workbench.wrapper.text()).toContain('业务来源 SALES_INVOICE SI-001')
    expect(workbench.wrapper.text()).toContain('来源版本 7')
    expect(workbench.wrapper.text()).toContain('fp-si-001')
    expect(workbench.wrapper.text()).not.toContain('FIN_VOUCHER_DRAFT SI-001')

    glApiMock.vouchers.get.mockResolvedValueOnce(convertedVoucher)
    const detail = await mountGlView(GlVoucherDetailView, '/gl/vouchers/91')
    expect(detail.wrapper.text()).toContain('正式来源 FIN_VOUCHER_DRAFT')
    expect(detail.wrapper.text()).toContain('业务来源 SALES_INVOICE SI-001')
    expect(detail.wrapper.text()).toContain('业务来源版本')
    expect(detail.wrapper.text()).toContain('fp-si-001')
    expect(detail.wrapper.text()).not.toContain('FIN_VOUCHER_DRAFT SI-001')
  })

  it('凭证编辑页以分录宽表、科目候选、辅助核算和借贷差额禁用原因完成有效草稿保存', async () => {
    glApiMock.vouchers.create.mockResolvedValueOnce({ ...voucherRecord, id: 101 })
    const { wrapper } = await mountGlView(GlVoucherFormView, '/gl/vouchers/create')

    expect(wrapper.text()).toContain('凭证编辑')
    expect(wrapper.text()).toContain('借方合计')
    expect(wrapper.text()).toContain('贷方合计')
    expect(wrapper.text()).toContain('差额')
    expect(wrapper.text()).toContain('会计期间')
    expect(glApiMock.accounts.list).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 20 }))
    await wrapper.find('input[name="gl-account-candidate-search"]').setValue('后段科目')
    await flushPromises()
    expect(glApiMock.accounts.list).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '后段科目', page: 1, pageSize: 20 }))
    expect(wrapper.find('[data-test="add-gl-voucher-line"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="remove-gl-voucher-line"]').exists()).toBe(true)
    expect(wrapper.find('input[name="gl-line-account-1"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('1002 银行存款')
    expect(wrapper.text()).toContain('客户 可选')
    expect(wrapper.find('[data-test="gl-voucher-lines-table"]').exists()).toBe(true)
    expect(wrapper.find('.table-scroll').exists()).toBe(true)

    const datePicker = wrapper.findComponent({ name: 'ElDatePicker' })
    datePicker.vm.$emit('update:modelValue', null)
    await flushPromises()
    await wrapper.find('[data-test="save-gl-voucher"]').trigger('click')
    await flushPromises()

    expect(glApiMock.vouchers.create).toHaveBeenCalledWith(expect.objectContaining({
      accountingPeriodCode: '2026-07',
      voucherDate: '',
      lines: expect.arrayContaining([
        expect.objectContaining({ debitAmount: '100.00', creditAmount: '0.00' }),
        expect.objectContaining({ debitAmount: '0.00', creditAmount: '100.00' }),
      ]),
    }))
  })

  it('凭证详情按后端动作展示提交、撤回、取消和冲销，不提供直接记账', async () => {
    glApiMock.vouchers.get.mockResolvedValueOnce({ ...voucherRecord, status: 'POSTED', voucherNo: '记-202607-0001', allowedActions: ['REVERSE'], actionDisabledReasons: { SUBMIT: '已记账不可提交' } })
    glApiMock.vouchers.createReversal.mockResolvedValueOnce({ ...voucherRecord, id: 102, draftNo: 'GLD-202607-0002', sourceType: 'REVERSAL' })
    const { wrapper } = await mountGlView(GlVoucherDetailView, '/gl/vouchers/91')

    expect(wrapper.text()).toContain('凭证详情')
    expect(wrapper.text()).toContain('POSTED 凭证不可删除、插入或修改')
    expect(wrapper.text()).toContain('记-202607-0001')
    expect(wrapper.text()).toContain('审批摘要')
    expect(wrapper.text()).toContain('来源追溯')
    expect(wrapper.text()).toContain('操作日志')
    expect(wrapper.text()).not.toContain('直接记账')

    await wrapper.find('[data-test="reverse-gl-voucher"]').trigger('click')
    await flushPromises()
    expect(glApiMock.vouchers.createReversal).toHaveBeenCalledWith(91, expect.objectContaining({
      version: 4,
      reason: expect.stringContaining('冲销'),
      idempotencyKey: expect.stringContaining('gl-reversal-'),
    }))
  })

  it('总账、明细账、科目余额和试算平衡页面展示只读宽表、追溯和失败关闭状态', async () => {
    const general = await mountGlView(GlGeneralLedgerView, '/gl/ledgers/general')
    expect(general.wrapper.text()).toContain('总账')
    expect(general.wrapper.text()).toContain('银行存款')
    expect(general.wrapper.text()).toContain('120.00')
    expect(general.wrapper.find('.table-scroll').exists()).toBe(true)

    const detail = await mountGlView(GlDetailLedgerView, '/gl/ledgers/detail')
    expect(detail.wrapper.text()).toContain('明细账')
    expect(detail.wrapper.text()).toContain('记-202607-0001')
    expect(detail.wrapper.text()).toContain('收款 RC-001')
    expect(detail.wrapper.text()).toContain('余额方向')
    expect(detail.wrapper.find('input[placeholder="来源类型"]').exists()).toBe(true)
    expect(detail.wrapper.find('input[placeholder="正式凭证号"]').exists()).toBe(true)
    await detail.wrapper.find('[data-test="ledger-voucher-link"]').trigger('click')
    await flushPromises()
    expect(detail.router.currentRoute.value.query.returnTo).toBe('/gl/ledgers/detail')

    const balances = await mountGlView(GlAccountBalanceView, '/gl/account-balances')
    expect(balances.wrapper.text()).toContain('科目余额')
    expect(balances.wrapper.text()).toContain('无权查看GL金额')
    expect(balances.wrapper.text()).not.toContain('NaN')

    const trial = await mountGlView(GlTrialBalanceView, '/gl/trial-balance')
    expect(trial.wrapper.text()).toContain('试算平衡')
    expect(trial.wrapper.text()).toContain('试算不平衡')
    expect(trial.wrapper.text()).toContain('差额 20.00')
    expect(trial.wrapper.text()).toContain('应交税费-销项税额')
  })
})
