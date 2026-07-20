import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SalesInvoiceListView from './SalesInvoiceListView.vue'
import SalesInvoiceFormView from './SalesInvoiceFormView.vue'
import SalesInvoiceDetailView from './SalesInvoiceDetailView.vue'
import PurchaseInvoiceListView from './PurchaseInvoiceListView.vue'
import PurchaseInvoiceFormView from './PurchaseInvoiceFormView.vue'
import PurchaseInvoiceDetailView from './PurchaseInvoiceDetailView.vue'
import PurchaseInvoiceMatchingView from './PurchaseInvoiceMatchingView.vue'
import ExpenseListView from './ExpenseListView.vue'
import ExpenseFormView from './ExpenseFormView.vue'
import ExpenseDetailView from './ExpenseDetailView.vue'
import AdvanceReceiptListView from './AdvanceReceiptListView.vue'
import AdvanceReceiptFormView from './AdvanceReceiptFormView.vue'
import AdvanceReceiptDetailView from './AdvanceReceiptDetailView.vue'
import PrepaymentListView from './PrepaymentListView.vue'
import PrepaymentFormView from './PrepaymentFormView.vue'
import PrepaymentDetailView from './PrepaymentDetailView.vue'
import SettlementWorkbenchView from './SettlementWorkbenchView.vue'
import VoucherDraftListView from './VoucherDraftListView.vue'
import VoucherDraftDetailView from './VoucherDraftDetailView.vue'
import { buttonsByText, mountFinanceView, page, setSelectValue } from './financeTestHelpers'
import { useConfirmActionMock } from '../../test/setup'

const invoiceApiMock = vi.hoisted(() => ({
  salesInvoices: { list: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), confirm: vi.fn(), cancel: vi.fn() },
  salesInvoiceCandidates: { list: vi.fn() },
  purchaseInvoices: { list: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), match: vi.fn(), confirm: vi.fn(), cancel: vi.fn() },
  purchaseInvoiceCandidates: { list: vi.fn() },
  purchaseInvoiceMatching: { get: vi.fn() },
}))
const expenseApiMock = vi.hoisted(() => ({
  expenses: { list: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), confirm: vi.fn(), cancel: vi.fn() },
  expenseCategories: { list: vi.fn() },
  expenseSourceCandidates: { list: vi.fn() },
}))
const settlementApiMock = vi.hoisted(() => ({
  advanceReceipts: { list: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), post: vi.fn(), cancel: vi.fn() },
  prepayments: { list: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), post: vi.fn(), cancel: vi.fn() },
  settlementWorkbench: { funds: vi.fn(), targets: vi.fn(), allocations: vi.fn(), get: vi.fn(), create: vi.fn(), post: vi.fn(), cancel: vi.fn() },
}))
const voucherApiMock = vi.hoisted(() => ({
  voucherDrafts: { list: vi.fn(), get: vi.fn(), generate: vi.fn(), markReady: vi.fn(), cancel: vi.fn() },
}))
const glApiMock = vi.hoisted(() => ({
  vouchers: { list: vi.fn(), fromFinanceDraft: vi.fn() },
}))
const financeApiMock = vi.hoisted(() => ({
  receipts: { list: vi.fn() },
  payments: { list: vi.fn() },
}))
const masterDataApiMock = vi.hoisted(() => ({
  customers: { list: vi.fn() },
  suppliers: { list: vi.fn() },
}))
const salesProjectApiMock = vi.hoisted(() => ({
  projects: { list: vi.fn() },
}))
const confirmActionMock = useConfirmActionMock()

vi.mock('../../shared/api/financeInvoiceApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/financeInvoiceApi')>()),
  financeInvoiceApi: invoiceApiMock,
}))
vi.mock('../../shared/api/financeExpenseApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/financeExpenseApi')>()),
  financeExpenseApi: expenseApiMock,
}))
vi.mock('../../shared/api/financeSettlementApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/financeSettlementApi')>()),
  financeSettlementApi: settlementApiMock,
}))
vi.mock('../../shared/api/financeVoucherDraftApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/financeVoucherDraftApi')>()),
  financeVoucherDraftApi: voucherApiMock,
}))
vi.mock('../../shared/api/glApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/glApi')>()),
  glApi: glApiMock,
}))
vi.mock('../../shared/api/financeApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/financeApi')>()),
  financeApi: financeApiMock,
}))
vi.mock('../../shared/api/masterDataApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/masterDataApi')>()),
  masterDataApi: masterDataApiMock,
}))
vi.mock('../../shared/api/salesProjectApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/salesProjectApi')>()),
  salesProjectApi: salesProjectApiMock,
}))

const salesInvoice = {
  id: 11,
  invoiceNo: 'SI-001',
  externalInvoiceNo: 'EXT-SI-001',
  partyId: 88,
  sourceId: 501,
  sourceNo: 'SS-001',
  customerName: '华东客户',
  partyName: '华东客户',
  partnerName: '华东客户',
  ownershipType: 'PROJECT',
  projectId: 188,
  projectName: '项目 A',
  contractNo: 'CT-001',
  orderNo: 'SO-001',
  invoiceDate: '2026-08-03',
  status: 'DRAFT',
  settlementStatus: 'PARTIALLY_SETTLED',
  invoiceType: 'SPECIAL_VAT',
  pretaxAmount: '100.00',
  taxAmount: '13.00',
  totalAmount: '113.00',
  unsettledAmount: '43.00',
  updatedAt: '2026-08-03T09:00:00+08:00',
  version: 2,
  allowedActions: ['UPDATE', 'CONFIRM', 'CANCEL'],
  sources: [{ sourceType: 'SALES_SHIPMENT', sourceId: 501, sourceNo: 'SS-001', sourceSummary: '销售出库 SS-001', businessDate: '2026-08-03', amount: '113.00', restricted: false, restrictedReasons: [] }],
  receivableLinks: [{ receivableId: 301, receivableNo: 'AR-001', status: 'PARTIALLY_RECEIVED', totalAmount: '113.00', receivedAmount: '70.00', unreceivedAmount: '43.00', linkMode: 'AUTO' }],
  settlements: [{ documentNo: 'RC-001', amount: '70.00' }],
  voucherDrafts: [{ draftNo: 'VD-001', status: 'DRAFT' }],
  lines: [{
    lineNo: 1,
    sourceLineId: 1001,
    quantity: '2.500000',
    taxRate: '0.130000',
    taxExcludedUnitPrice: '40.000000',
    taxExcludedAmount: '100.00',
    taxAmount: '13.00',
    taxIncludedAmount: '113.00',
  }],
  auditSummary: [],
}
const salesCandidate = {
  sourceLineId: 1001,
  customerId: 88,
  customerName: '真实客户',
  ownershipType: 'PROJECT',
  projectId: 188,
  projectName: '真实项目',
  sourceNo: 'SS-001',
  lineNo: 1,
  materialCode: 'MAT-A',
  materialName: '产品 A',
  unitName: '件',
  availableQuantity: '2.500000',
  invoicedQuantity: '0.000000',
  invoiceQuantity: '2.500000',
  pretaxUnitPrice: '40.000000',
  taxRate: '0.130000',
  pretaxAmount: '100.00',
  taxAmount: '13.00',
  totalAmount: '113.00',
  availableAmount: '113.00',
}
const purchaseCandidate = {
  sourceLineId: 2002,
  orderLineId: 2001,
  receiptLineId: 2002,
  supplierId: 99,
  supplierName: '真实供应商',
  ownershipType: 'PROJECT',
  projectId: 188,
  projectName: '真实项目',
  sourceType: 'PURCHASE_RECEIPT',
  sourceNo: 'PR-001',
  lineNo: 1,
  materialCode: 'MAT-B',
  materialName: '原料 B',
  unitName: '千克',
  availableQuantity: '3.000000',
  invoicedQuantity: '1.000000',
  invoiceQuantity: '3.000000',
  pretaxUnitPrice: '66.666667',
  taxRate: '0.130000',
  pretaxAmount: '200.00',
  taxAmount: '26.00',
  totalAmount: '226.00',
  availableAmount: '226.00',
}
const purchaseInvoice = {
  id: 21,
  invoiceNo: 'PI-001',
  externalInvoiceNo: 'EXT-PI-001',
  partyId: 99,
  sourceId: 601,
  sourceNo: 'PR-001',
  supplierName: '外协供应商',
  partyName: '外协供应商',
  partnerName: '外协供应商',
  invoiceType: 'GENERAL_VAT',
  sourceType: 'PURCHASE_RECEIPT',
  ownershipType: 'PUBLIC',
  projectId: null,
  purchaseOrderNo: 'PO-001',
  receiptSummary: 'PR-001',
  invoiceDate: '2026-08-04',
  status: 'DRAFT',
  matchStatus: 'EXCEPTION',
  settlementStatus: 'UNSETTLED',
  pretaxAmount: '200.00',
  taxAmount: '26.00',
  totalAmount: '226.00',
  unsettledAmount: '226.00',
  differenceCount: 2,
  version: 1,
  allowedActions: ['UPDATE', 'MATCH', 'CONFIRM', 'CANCEL'],
  matching: {
    status: 'EXCEPTION',
    rows: [{
      key: 'PR-001-1',
      materialCode: 'MAT-B',
      materialName: '原料 B',
      order: { quantity: '3.000000', taxExcludedUnitPrice: '66.666667', taxIncludedUnitPrice: '75.333333', taxRate: '0.130000', taxExcludedAmount: '200.00', taxAmount: '26.00', taxIncludedAmount: '226.00' },
      receipt: { quantity: '3.000000', taxExcludedUnitPrice: '66.666667', taxIncludedUnitPrice: '75.333333', taxRate: '0.130000', taxExcludedAmount: '200.00', taxAmount: '26.00', taxIncludedAmount: '226.00' },
      invoice: { quantity: '3.000000', taxExcludedUnitPrice: '67.800000', taxIncludedUnitPrice: '76.613333', taxRate: '0.130000', taxExcludedAmount: '203.40', taxAmount: '26.44', taxIncludedAmount: '229.84' },
      matchStatus: 'EXCEPTION',
    }],
    differences: [{ type: 'PRICE', message: '未税单价差异', orderValue: '66.666667', receiptValue: '66.666667', invoiceValue: '67.800000' }],
  },
  sources: [{ sourceType: 'PURCHASE_RECEIPT', sourceId: 601, sourceNo: 'PR-001', sourceSummary: '采购入库 PR-001', businessDate: '2026-08-04', amount: '226.00', restricted: false, restrictedReasons: [] }],
  payableLinks: [{ payableId: 401, payableNo: 'AP-001', status: 'PARTIALLY_PAID', totalAmount: '226.00', paidAmount: '40.00', unpaidAmount: '186.00', linkMode: 'AUTO' }],
  settlements: [],
  voucherDrafts: [],
  lines: [{
    lineNo: 1,
    sourceLineId: 2002,
    purchaseOrderLineId: 2001,
    receiptLineId: 2002,
    quantity: '3.000000',
    taxRate: '0.130000',
    taxExcludedUnitPrice: '66.666667',
    taxExcludedAmount: '200.00',
    taxAmount: '26.00',
    taxIncludedAmount: '226.00',
  }],
  auditSummary: [],
}
const expense = {
  id: 31,
  expenseNo: 'EXP-001',
  supplierName: '费用供应商',
  categoryName: '项目运费',
  ownershipType: 'PROJECT',
  projectName: '项目 A',
  sourceType: 'OUTSOURCING_RECEIPT',
  sourceNo: 'OSR-001',
  businessDate: '2026-08-05',
  status: 'CONFIRMED',
  settlementStatus: 'UNSETTLED',
  pretaxAmount: '100.00',
  taxAmount: '6.00',
  totalAmount: '106.00',
  unsettledAmount: '106.00',
  version: 1,
  allowedActions: ['GENERATE_VOUCHER_DRAFT'],
  lines: [{ categoryName: '项目运费', pretaxAmount: '100.00', taxRate: '0.060000', taxAmount: '6.00', totalAmount: '106.00' }],
  sources: [{ sourceType: 'OUTSOURCING_RECEIPT', sourceNo: 'OSR-001', summary: '外协收货 OSR-001' }],
  payableLinks: [{ payableNo: 'AP-001', amount: '106.00' }],
  settlements: [],
  voucherDrafts: [],
  auditSummary: [],
}
const advanceReceipt = {
  id: 41,
  advanceNo: 'ADV-R-001',
  fundNo: 'RC-001',
  partnerId: 88,
  customerId: 88,
  partnerName: '华东客户',
  ownershipType: 'PROJECT',
  projectId: 188,
  projectName: '项目 A',
  businessDate: '2026-08-06',
  amount: '500.00',
  allocatedAmount: '120.00',
  availableAmount: '380.00',
  status: 'AVAILABLE',
  settlementStatus: 'PARTIALLY_APPLIED',
  lastAllocatedAt: '2026-08-06T12:00:00+08:00',
  version: 1,
  allowedActions: ['ALLOCATE'],
  allocations: [{ targetType: 'SALES_INVOICE', targetNo: 'SI-001', amount: '120.00' }],
  voucherDrafts: [],
  auditSummary: [],
}
const prepayment = { ...advanceReceipt, id: 51, advanceNo: 'ADV-P-001', fundNo: 'PM-001', partnerId: 99, customerId: undefined, supplierId: 99, partnerName: '外协供应商' }
const voucherDraft = {
  id: 61,
  draftNo: 'VD-001',
  sourceType: 'SALES_INVOICE',
  sourceNo: 'SI-001',
  businessDate: '2026-08-03',
  partnerName: '华东客户',
  ownershipType: 'PROJECT',
  projectName: '项目 A',
  status: 'DRAFT',
  debitTotal: '113.00',
  creditTotal: '112.00',
  balanced: false,
  generationVersion: 1,
  updatedAt: '2026-08-03T13:00:00+08:00',
  version: 1,
  allowedActions: ['READY', 'CANCEL'],
  lines: [{ direction: 'DEBIT', businessCategory: '销售发票', summary: '非正式建议', pretaxAmount: '100.00', taxAmount: '13.00', totalAmount: '113.00', partnerName: '华东客户', projectName: '项目 A' }],
  sourceSummary: { sourceType: 'SALES_INVOICE', sourceNo: 'SI-001', restricted: true, restrictedReason: '来源受限' },
  auditSummary: [],
}
const customer = { id: 88, code: 'CUS-088', name: '真实客户', status: 'ENABLED' }
const supplier = { id: 99, code: 'SUP-099', name: '真实供应商', status: 'ENABLED' }
const project = {
  id: 188,
  projectNo: 'SP-188',
  name: '真实项目',
  customerId: 88,
  customerCode: 'CUS-088',
  customerName: '真实客户',
  ownerUserId: 1,
  ownerUsername: 'owner',
  ownerDisplayName: '项目经理',
  status: 'ACTIVE',
  targetRevenue: '0.00',
  targetCost: '0.00',
  contractSummaryRestricted: false,
  salesOrderSummaryRestricted: false,
  createdByName: '项目经理',
  createdAt: '2026-08-01T09:00:00+08:00',
  updatedAt: '2026-08-01T09:00:00+08:00',
  version: 1,
}

describe('028 财务页面', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    confirmActionMock.mockResolvedValue(true)
    masterDataApiMock.customers.list.mockResolvedValue(page([customer], 1, 200))
    masterDataApiMock.suppliers.list.mockResolvedValue(page([supplier], 1, 200))
    salesProjectApiMock.projects.list.mockResolvedValue(page([project], 1, 200))
    invoiceApiMock.salesInvoices.list.mockResolvedValue(page([salesInvoice]))
    invoiceApiMock.salesInvoices.get.mockResolvedValue(salesInvoice)
    invoiceApiMock.salesInvoices.create.mockResolvedValue(salesInvoice)
    invoiceApiMock.salesInvoices.update.mockResolvedValue(salesInvoice)
    invoiceApiMock.salesInvoices.confirm.mockResolvedValue({ ...salesInvoice, status: 'CONFIRMED' })
    invoiceApiMock.salesInvoiceCandidates.list.mockResolvedValue(page([salesCandidate], 1, 50))
    invoiceApiMock.purchaseInvoices.list.mockResolvedValue(page([purchaseInvoice]))
    invoiceApiMock.purchaseInvoices.get.mockResolvedValue(purchaseInvoice)
    invoiceApiMock.purchaseInvoices.create.mockResolvedValue(purchaseInvoice)
    invoiceApiMock.purchaseInvoices.update.mockResolvedValue(purchaseInvoice)
    invoiceApiMock.purchaseInvoices.match.mockResolvedValue(purchaseInvoice)
    invoiceApiMock.purchaseInvoices.confirm.mockResolvedValue({ ...purchaseInvoice, status: 'CONFIRMED', matchStatus: 'MATCHED', allowedActions: [] })
    invoiceApiMock.purchaseInvoices.cancel.mockResolvedValue({ ...purchaseInvoice, status: 'CANCELLED', allowedActions: [] })
    invoiceApiMock.purchaseInvoiceCandidates.list.mockResolvedValue(page([purchaseCandidate], 1, 10))
    invoiceApiMock.purchaseInvoiceMatching.get.mockResolvedValue(purchaseInvoice.matching)
    expenseApiMock.expenses.list.mockResolvedValue(page([expense]))
    expenseApiMock.expenses.get.mockResolvedValue(expense)
    expenseApiMock.expenses.create.mockResolvedValue(expense)
    expenseApiMock.expenses.update.mockResolvedValue(expense)
    expenseApiMock.expenses.confirm.mockResolvedValue({ ...expense, status: 'CONFIRMED', allowedActions: [] })
    expenseApiMock.expenses.cancel.mockResolvedValue({ ...expense, status: 'CANCELLED', allowedActions: [] })
    expenseApiMock.expenseCategories.list.mockResolvedValue(page([{ id: 3, name: '项目运费', status: 'ENABLED' }], 1, 100))
    expenseApiMock.expenseSourceCandidates.list.mockResolvedValue(page([{
      sourceId: 7001,
      sourceType: 'OUTSOURCING_RECEIPT',
      sourceNo: 'OSR-001',
      supplierId: 99,
      supplierName: '真实供应商',
      ownershipType: 'PROJECT',
      projectId: 188,
      projectName: '真实项目',
      businessDate: '2026-07-03T16:00:00.000Z',
      availableAmount: '106.00',
      summary: '外协收货 OSR-001',
    }], 1, 10))
    settlementApiMock.advanceReceipts.list.mockResolvedValue(page([advanceReceipt]))
    settlementApiMock.advanceReceipts.get.mockResolvedValue(advanceReceipt)
    settlementApiMock.advanceReceipts.create.mockResolvedValue(advanceReceipt)
    settlementApiMock.advanceReceipts.update.mockResolvedValue(advanceReceipt)
    settlementApiMock.advanceReceipts.post.mockResolvedValue({ ...advanceReceipt, status: 'AVAILABLE', allowedActions: ['ALLOCATE'] })
    settlementApiMock.advanceReceipts.cancel.mockResolvedValue({ ...advanceReceipt, status: 'CANCELLED', allowedActions: [] })
    settlementApiMock.prepayments.list.mockResolvedValue(page([prepayment]))
    settlementApiMock.prepayments.get.mockResolvedValue(prepayment)
    settlementApiMock.prepayments.create.mockResolvedValue(prepayment)
    settlementApiMock.prepayments.update.mockResolvedValue(prepayment)
    settlementApiMock.prepayments.post.mockResolvedValue({ ...prepayment, status: 'AVAILABLE', allowedActions: ['ALLOCATE'] })
    settlementApiMock.prepayments.cancel.mockResolvedValue({ ...prepayment, status: 'CANCELLED', allowedActions: [] })
    settlementApiMock.settlementWorkbench.funds.mockResolvedValue(page([advanceReceipt], 1, 50))
    settlementApiMock.settlementWorkbench.targets.mockResolvedValue(page([
      { targetType: 'SALES_INVOICE', targetId: 11, targetNo: 'SI-001', originalAmount: '113.00', settledAmount: '70.00', adjustedAmount: '0.00', allocatedAmount: '0.00', unsettledAmount: '43.00', status: 'CONFIRMED', sourceSummary: '销售发票 SI-001', version: 2 },
      { targetType: 'RECEIVABLE', targetId: 12, targetNo: 'AR-002', originalAmount: '200.00', settledAmount: '0.00', adjustedAmount: '0.00', allocatedAmount: '0.00', unsettledAmount: '200.00', status: 'PARTIALLY_RECEIVED', sourceSummary: '应收 AR-002', version: 3 },
    ], 1, 50))
    settlementApiMock.settlementWorkbench.allocations.mockResolvedValue(page([{
      id: 71,
      allocationNo: 'ALLOC-001',
      partnerName: '真实客户',
      ownershipType: 'PROJECT',
      projectName: '真实项目',
      totalAmount: '120.00',
      status: 'POSTED',
      version: 2,
      allowedActions: [],
      lines: [],
    }], 1, 20))
    settlementApiMock.settlementWorkbench.get.mockResolvedValue({
      id: 71,
      allocationNo: 'ALLOC-001',
      direction: 'CUSTOMER',
      status: 'POSTED',
      version: 2,
      partnerName: '真实客户',
      ownershipType: 'PROJECT',
      projectName: '真实项目',
      amount: '120.00',
      allowedActions: [],
      lines: [
        { targetType: 'SALES_INVOICE', targetNo: 'SI-001', amount: '43.00' },
        { targetType: 'RECEIVABLE', targetNo: 'AR-002', amount: '77.00' },
      ],
    })
    settlementApiMock.settlementWorkbench.create.mockResolvedValue({ id: 71, allocationNo: 'ALLOC-001' })
    settlementApiMock.settlementWorkbench.post.mockResolvedValue({ id: 71, allocationNo: 'ALLOC-001' })
    settlementApiMock.settlementWorkbench.cancel.mockResolvedValue({ id: 71, allocationNo: 'ALLOC-001' })
    voucherApiMock.voucherDrafts.list.mockResolvedValue(page([voucherDraft]))
    voucherApiMock.voucherDrafts.get.mockResolvedValue(voucherDraft)
    voucherApiMock.voucherDrafts.generate.mockResolvedValue(voucherDraft)
    voucherApiMock.voucherDrafts.markReady.mockResolvedValue({ ...voucherDraft, status: 'READY', allowedActions: [] })
    voucherApiMock.voucherDrafts.cancel.mockResolvedValue({ ...voucherDraft, status: 'CANCELLED', allowedActions: [] })
    glApiMock.vouchers.list.mockResolvedValue(page([]))
    glApiMock.vouchers.fromFinanceDraft.mockResolvedValue({
      id: 91,
      draftNo: 'GLD-202607-0001',
      status: 'DRAFT',
      sourceType: 'FIN_VOUCHER_DRAFT',
      sourceId: 61,
      debitTotal: '113.00',
      creditTotal: '113.00',
      version: 1,
      allowedActions: ['UPDATE', 'SUBMIT'],
      actionDisabledReasons: {},
    })
    financeApiMock.receipts.list.mockResolvedValue(page([{ id: 81, receiptNo: 'RC-POSTED-001', receivableId: 11, receivableNo: 'AR-001', customerId: 88, customerName: '真实客户', receiptDate: '2026-08-06', amount: '500.00', method: 'BANK_TRANSFER', status: 'POSTED', createdByName: '财务用户', version: 4 }]))
    financeApiMock.payments.list.mockResolvedValue(page([{ id: 91, paymentNo: 'PM-POSTED-001', payableId: 21, payableNo: 'AP-001', supplierId: 99, supplierName: '真实供应商', paymentDate: '2026-08-06', amount: '260.00', method: 'BANK_TRANSFER', status: 'POSTED', createdByName: '财务用户', version: 5 }]))
  })

  it('销售发票列表、表单和详情使用来源候选、金额字符串、allowedActions 和非历史回写文案', async () => {
    const { wrapper: listWrapper } = await mountFinanceView(SalesInvoiceListView, ['finance:sales-invoice:view', 'finance:sales-invoice:create'], '/finance/sales-invoices')
    expect(listWrapper.text()).toContain('销售发票')
    expect(listWrapper.text()).toContain('基于已过账销售出库和税价快照开票，不回写历史出库')
    expect(listWrapper.text()).toContain('SI-001')
    expect(listWrapper.text()).toContain('部分结清')
    expect(listWrapper.find('[data-test="create-sales-invoice"]').exists()).toBe(true)
    expect(invoiceApiMock.salesInvoices.list).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 10, invoiceDateFrom: '', invoiceDateTo: '' }))

    const { wrapper: formWrapper } = await mountFinanceView(SalesInvoiceFormView, ['finance:sales-invoice:create'], '/finance/sales-invoices/create')
    expect(masterDataApiMock.customers.list).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 200 }))
    expect(salesProjectApiMock.projects.list).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 200 }))
    expect(invoiceApiMock.salesInvoiceCandidates.list).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 10 }))
    expect(formWrapper.text()).toContain('来源出库净可开票余额')
    expect(formWrapper.text()).toContain('SS-001')
    expect(formWrapper.text()).toContain('真实客户')
    await formWrapper.find('input[name="sales-invoice-date"]').setValue('2026-08-03')
    await formWrapper.find('input[name="sales-invoice-external-no"]').setValue('EXT-SI-001')
    await formWrapper.find('[data-test="select-source-line"]').trigger('click')
    await formWrapper.find('[data-test="save-sales-invoice"]').trigger('click')
    await flushPromises()
    expect(invoiceApiMock.salesInvoices.create).toHaveBeenCalledWith(expect.objectContaining({
      invoiceDate: '2026-08-03',
      invoiceType: 'GENERAL_VAT',
      externalInvoiceNo: 'EXT-SI-001',
      customerId: 88,
      ownershipType: 'PROJECT',
      projectId: 188,
      sourceLines: [expect.objectContaining({ invoiceQuantity: '2.500000' })],
    }))

    invoiceApiMock.salesInvoices.get.mockResolvedValueOnce({
      ...salesInvoice,
      customerName: '',
      partyName: '后端往来客户',
      sources: [{ sourceType: 'SALES_SHIPMENT', sourceId: 501, sourceNo: 'SS-DTO-001', sourceSummary: '销售出库 SS-DTO-001', businessDate: '2026-08-03', amount: '113.00', restricted: false, restrictedReasons: [] }],
      receivableLinks: [{ receivableId: 302, receivableNo: 'AR-DTO-001', status: 'PARTIALLY_RECEIVED', totalAmount: '113.00', receivedAmount: '70.00', unreceivedAmount: '43.00', linkMode: 'AUTO' }],
    })
    const { wrapper: detailWrapper } = await mountFinanceView(SalesInvoiceDetailView, ['finance:sales-invoice:view', 'finance:sales-invoice:confirm'], '/finance/sales-invoices/11')
    expect(detailWrapper.text()).toContain('确认后不可普通编辑')
    expect(detailWrapper.text()).toContain('后端往来客户')
    expect(detailWrapper.text()).toContain('应收链接')
    expect(detailWrapper.text()).toContain('AR-DTO-001')
    expect(detailWrapper.text()).toContain('未收 43.00')
    expect(detailWrapper.text()).toContain('销售出库 SS-DTO-001')
    expect(detailWrapper.text()).toContain('2026-08-03')
    expect(detailWrapper.find('[data-test="confirm-sales-invoice"]').exists()).toBe(true)
    await detailWrapper.find('[data-test="confirm-sales-invoice"]').trigger('click')
    await flushPromises()
    expect(invoiceApiMock.salesInvoices.confirm).toHaveBeenCalledWith(11, expect.objectContaining({ version: 2, idempotencyKey: expect.any(String) }))
  })

  it('财务新增类列表主操作消费对应创建权限', async () => {
    const { wrapper: salesList } = await mountFinanceView(SalesInvoiceListView, ['finance:sales-invoice:view'], '/finance/sales-invoices')
    expect(salesList.find('[data-test="create-sales-invoice"]').exists()).toBe(false)

    const { wrapper: purchaseList } = await mountFinanceView(PurchaseInvoiceListView, ['finance:purchase-invoice:view'], '/finance/purchase-invoices')
    expect(purchaseList.find('[data-test="create-purchase-invoice"]').exists()).toBe(false)

    const { wrapper: expenseList } = await mountFinanceView(ExpenseListView, ['finance:expense:view'], '/finance/expenses')
    expect(expenseList.find('[data-test="create-expense"]').exists()).toBe(false)

    const { wrapper: advanceList } = await mountFinanceView(AdvanceReceiptListView, ['finance:advance-receipt:view'], '/finance/advance-receipts')
    expect(buttonsByText(advanceList, '登记预收款')).toHaveLength(0)

    const { wrapper: prepaymentList } = await mountFinanceView(PrepaymentListView, ['finance:prepayment:view'], '/finance/prepayments')
    expect(buttonsByText(prepaymentList, '登记预付款')).toHaveLength(0)
  })

  it('销售发票编辑从详情 partyId/sourceId/source lines 恢复主体和已选来源', async () => {
    const { wrapper } = await mountFinanceView(
      SalesInvoiceFormView,
      ['finance:sales-invoice:view', 'finance:sales-invoice:create'],
      '/finance/sales-invoices/11/edit',
    )

    expect(invoiceApiMock.salesInvoiceCandidates.list).toHaveBeenLastCalledWith(expect.objectContaining({
      sourceId: 501,
      customerId: 88,
      ownershipType: 'PROJECT',
      projectId: 188,
      page: 1,
      pageSize: 10,
    }))
    expect(wrapper.text()).toContain('真实客户')
    expect(wrapper.text()).toContain('SS-001')
    expect(wrapper.text()).toContain('113.00')
    await wrapper.find('[data-test="save-sales-invoice"]').trigger('click')
    await flushPromises()

    expect(invoiceApiMock.salesInvoices.update).toHaveBeenCalledWith('11', expect.objectContaining({
      customerId: 88,
      sourceId: 501,
      version: 2,
      sourceLines: [expect.objectContaining({ sourceLineId: 1001, invoiceQuantity: '2.500000' })],
    }))
  })

  it('已确认销售发票直达编辑路由时进入只读态且保存不可达', async () => {
    invoiceApiMock.salesInvoices.get.mockResolvedValueOnce({
      ...salesInvoice,
      status: 'CONFIRMED',
      allowedActions: [],
    })
    const { wrapper } = await mountFinanceView(
      SalesInvoiceFormView,
      ['finance:sales-invoice:view', 'finance:sales-invoice:update'],
      '/finance/sales-invoices/11/edit',
    )

    expect(wrapper.text()).toContain('确认后不可普通编辑')
    expect(wrapper.find('[data-test="save-sales-invoice"]').attributes('disabled')).toBeDefined()
    expect(wrapper.findAllComponents({ name: 'ElSelect' })[0].props('disabled')).toBe(true)
    expect(wrapper.find('input[placeholder="出库号、物料或客户"]').attributes('disabled')).toBeDefined()
    expect(buttonsByText(wrapper, '查询来源')[0].props('disabled')).toBe(true)
    expect(wrapper.find('[data-test="select-source-line"]').attributes('disabled')).toBeDefined()
    invoiceApiMock.salesInvoices.update.mockClear()
    await wrapper.find('[data-test="save-sales-invoice"]').trigger('click')
    await flushPromises()
    expect(invoiceApiMock.salesInvoices.update).not.toHaveBeenCalled()
  })

  it('采购发票与三单匹配页面展示零容差差异、外协来源和确认禁用原因', async () => {
    const { wrapper: listWrapper } = await mountFinanceView(PurchaseInvoiceListView, ['finance:purchase-invoice:view', 'finance:purchase-invoice:create'], '/finance/purchase-invoices')
    expect(listWrapper.text()).toContain('采购发票')
    expect(listWrapper.text()).toContain('区分标准采购发票与外协供应商结算')
    expect(listWrapper.text()).toContain('存在差异')
    expect(listWrapper.find('[data-test="reset-purchase-invoices"]').exists()).toBe(true)

    const { wrapper: formWrapper } = await mountFinanceView(PurchaseInvoiceFormView, ['finance:purchase-invoice:create'], '/finance/purchase-invoices/create')
    expect(masterDataApiMock.suppliers.list).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 200 }))
    expect(salesProjectApiMock.projects.list).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 200 }))
    expect(invoiceApiMock.purchaseInvoiceCandidates.list).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 10 }))
    expect(formWrapper.text()).toContain('标准采购发票')
    expect(formWrapper.text()).toContain('外协结算')
    expect(formWrapper.text()).toContain('净可开余额')
    await formWrapper.find('[data-test="select-purchase-source-line"]').trigger('click')
    await formWrapper.find('input[name="purchase-invoice-date"]').setValue('2026-08-04')
    await formWrapper.find('[data-test="save-purchase-invoice"]').trigger('click')
    await flushPromises()
    expect(invoiceApiMock.purchaseInvoices.create).toHaveBeenCalledWith(expect.objectContaining({
      supplierId: 99,
      sourceType: 'PURCHASE_RECEIPT',
      ownershipType: 'PROJECT',
      projectId: 188,
      sourceLines: [expect.objectContaining({ orderLineId: 2001, receiptLineId: 2002, invoiceQuantity: '3.000000' })],
    }))

    invoiceApiMock.purchaseInvoices.get.mockResolvedValueOnce({
      ...purchaseInvoice,
      supplierName: '',
      partyName: '后端供应商',
      differenceCount: 0,
      matchDifferences: [
        { type: 'TAX_EXCLUDED_UNIT_PRICE', message: '未税单价差异', expectedValue: '66.666667', actualValue: '67.800000' },
        { type: 'TAX_INCLUDED_UNIT_PRICE', message: '含税单价差异', expectedValue: '75.333333', actualValue: '76.613333' },
        { type: 'TAX_EXCLUDED_AMOUNT', message: '未税金额差异', expectedValue: '200.00', actualValue: '203.40' },
        { type: 'TAX_INCLUDED_AMOUNT', message: '含税金额差异', expectedValue: '226.00', actualValue: '229.84' },
      ],
      sources: [{ sourceType: 'PURCHASE_RECEIPT', sourceId: 601, sourceNo: 'PR-DTO-001', sourceSummary: '采购入库 PR-DTO-001', businessDate: '2026-08-04', amount: '226.00', restricted: false, restrictedReasons: [] }],
      payableLinks: [{ payableId: 402, payableNo: 'AP-DTO-001', status: 'PARTIALLY_PAID', totalAmount: '226.00', paidAmount: '40.00', unpaidAmount: '186.00', linkMode: 'AUTO' }],
    })
    const { wrapper: detailWrapper } = await mountFinanceView(PurchaseInvoiceDetailView, ['finance:purchase-invoice:view', 'finance:purchase-invoice:match', 'finance:purchase-invoice:confirm'], '/finance/purchase-invoices/21')
    expect(detailWrapper.text()).toContain('存在三单差异，零容差规则禁止确认')
    expect(detailWrapper.text()).toContain('后端供应商')
    expect(detailWrapper.text()).toContain('AP-DTO-001')
    expect(detailWrapper.text()).toContain('未付 186.00')
    expect(detailWrapper.text()).toContain('差异数 4')
    expect(detailWrapper.text()).not.toContain('差异数 0')
    expect(detailWrapper.text()).toContain('采购入库 PR-DTO-001')
    expect(detailWrapper.find('[data-test="confirm-purchase-invoice"]').attributes('disabled')).toBeDefined()
    await detailWrapper.find('[data-test="match-purchase-invoice"]').trigger('click')
    await flushPromises()
    expect(invoiceApiMock.purchaseInvoices.match).toHaveBeenCalledWith(21, expect.objectContaining({ version: 1, idempotencyKey: expect.any(String) }))

    const { wrapper: matchingWrapper } = await mountFinanceView(PurchaseInvoiceMatchingView, ['finance:purchase-invoice:view', 'finance:purchase-invoice:match'], '/finance/purchase-invoices/21/matching')
    expect(matchingWrapper.text()).toContain('订单事实')
    expect(matchingWrapper.text()).toContain('入库事实')
    expect(matchingWrapper.text()).toContain('发票事实')
    expect(matchingWrapper.text()).toContain('66.666667')
    expect(matchingWrapper.text()).toContain('67.800000')
    expect(matchingWrapper.text()).toContain('203.40')
    expect(matchingWrapper.text()).toContain('26.44')
    expect(matchingWrapper.text()).toContain('229.84')
    expect(matchingWrapper.text()).toContain('存在差异')
    expect(matchingWrapper.text()).toContain('未税单价差异')
  })

  it('发票详情应收应付编号提供可点击链接并携带返回链路', async () => {
    const { wrapper: salesDetail, router: salesRouter } = await mountFinanceView(SalesInvoiceDetailView, ['finance:sales-invoice:view'], '/finance/sales-invoices/11')
    await salesDetail.find('[data-test="view-linked-receivable"]').trigger('click')
    await flushPromises()
    expect(salesRouter.currentRoute.value.name).toBe('finance-receivable-detail')
    expect(salesRouter.currentRoute.value.params.id).toBe('301')
    expect(salesRouter.currentRoute.value.query.returnTo).toBe('/finance/sales-invoices/11')

    const { wrapper: purchaseDetail, router: purchaseRouter } = await mountFinanceView(PurchaseInvoiceDetailView, ['finance:purchase-invoice:view'], '/finance/purchase-invoices/21')
    await purchaseDetail.find('[data-test="view-linked-payable"]').trigger('click')
    await flushPromises()
    expect(purchaseRouter.currentRoute.value.name).toBe('finance-payable-detail')
    expect(purchaseRouter.currentRoute.value.params.id).toBe('401')
    expect(purchaseRouter.currentRoute.value.query.returnTo).toBe('/finance/purchase-invoices/21')
  })

  it('采购发票编辑回填供应商摘要和已选来源，候选池展示后端净可开事实', async () => {
    invoiceApiMock.purchaseInvoices.get.mockResolvedValueOnce({
      ...purchaseInvoice,
      supplierName: '真实供应商',
      partyId: 99,
      sourceId: 601,
      sourceNo: 'PR-001',
      ownershipType: 'PROJECT',
      projectId: 188,
      projectName: '真实项目',
    })
    const { wrapper } = await mountFinanceView(
      PurchaseInvoiceFormView,
      ['finance:purchase-invoice:view', 'finance:purchase-invoice:create'],
      '/finance/purchase-invoices/21/edit',
    )

    expect(invoiceApiMock.purchaseInvoiceCandidates.list).toHaveBeenLastCalledWith(expect.objectContaining({
      sourceType: 'PURCHASE_RECEIPT',
      sourceId: 601,
      supplierId: 99,
      ownershipType: 'PROJECT',
      projectId: 188,
      page: 1,
      pageSize: 10,
    }))
    expect(wrapper.text()).toContain('真实供应商')
    expect(wrapper.text()).toContain('PR-001')
    expect(wrapper.text()).toContain('226.00')
    await wrapper.find('[data-test="save-purchase-invoice"]').trigger('click')
    await flushPromises()

    expect(invoiceApiMock.purchaseInvoices.update).toHaveBeenCalledWith('21', expect.objectContaining({
      supplierId: 99,
      sourceId: 601,
      version: 1,
      sourceLines: [expect.objectContaining({ sourceLineId: 2002, receiptLineId: 2002, invoiceQuantity: '3.000000' })],
    }))
  })

  it('已确认采购发票直达编辑路由时进入只读态且保存不可达', async () => {
    invoiceApiMock.purchaseInvoices.get.mockResolvedValueOnce({
      ...purchaseInvoice,
      status: 'CONFIRMED',
      allowedActions: [],
    })
    const { wrapper } = await mountFinanceView(
      PurchaseInvoiceFormView,
      ['finance:purchase-invoice:view', 'finance:purchase-invoice:update'],
      '/finance/purchase-invoices/21/edit',
    )

    expect(wrapper.text()).toContain('确认后不可普通编辑')
    expect(wrapper.find('[data-test="save-purchase-invoice"]').attributes('disabled')).toBeDefined()
    expect(wrapper.findAllComponents({ name: 'ElSelect' })[0].props('disabled')).toBe(true)
    expect(wrapper.findAllComponents({ name: 'ElSelect' })[1].props('disabled')).toBe(true)
    expect(wrapper.find('input[placeholder="入库、外协收货或物料"]').attributes('disabled')).toBeDefined()
    expect(buttonsByText(wrapper, '查询来源')[0].props('disabled')).toBe(true)
    expect(wrapper.find('[data-test="select-purchase-source-line"]').attributes('disabled')).toBeDefined()
    invoiceApiMock.purchaseInvoices.update.mockClear()
    await wrapper.find('[data-test="save-purchase-invoice"]').trigger('click')
    await flushPromises()
    expect(invoiceApiMock.purchaseInvoices.update).not.toHaveBeenCalled()
  })

  it('费用单页面表达项目公共归属、供应商费用和非正式成本边界', async () => {
    expenseApiMock.expenses.list.mockResolvedValueOnce(page([{ ...expense, settlementStatus: 'UNLINKED' }]))
    const { wrapper: listWrapper } = await mountFinanceView(ExpenseListView, ['finance:expense:view', 'finance:expense:create'], '/finance/expenses')
    expect(listWrapper.text()).toContain('记录项目/公共供应商费用归属')
    expect(listWrapper.text()).toContain('不形成正式项目成本')
    expect(listWrapper.text()).toContain('EXP-001')
    expect(listWrapper.text()).toContain('未关联结算')
    expect(listWrapper.text()).not.toContain('UNLINKED')
    expect(listWrapper.find('[data-test="reset-expenses"]').exists()).toBe(true)

    const { wrapper: formWrapper } = await mountFinanceView(ExpenseFormView, ['finance:expense:create'], '/finance/expenses/create')
    expect(expenseApiMock.expenseCategories.list).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 100 }))
    expect(masterDataApiMock.suppliers.list).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 200 }))
    expect(salesProjectApiMock.projects.list).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 200 }))
    expect(expenseApiMock.expenseSourceCandidates.list).not.toHaveBeenCalled()
    expect(formWrapper.text()).not.toContain('外协订单')
    expect(formWrapper.text()).toContain('选择采购入库或外协收货后加载来源候选')
    expect(formWrapper.text()).toContain('来源净可用金额')
    expect(formWrapper.text()).toContain('0.00')
    await setSelectValue(formWrapper, 0, 'PROJECT')
    await setSelectValue(formWrapper, 2, 188)
    await setSelectValue(formWrapper, 4, 'PURCHASE_RECEIPT')
    expect(formWrapper.find('input[name="expense-source-date-from"]').exists()).toBe(true)
    expect(formWrapper.find('input[name="expense-source-date-to"]').exists()).toBe(true)
    await formWrapper.find('input[name="expense-source-date-from"]').setValue('2026-07-04')
    await formWrapper.find('input[name="expense-source-date-to"]').setValue('2026-07-04')
    await buttonsByText(formWrapper, '查询来源')[0].trigger('click')
    await flushPromises()
    expect(expenseApiMock.expenseSourceCandidates.list).toHaveBeenLastCalledWith(expect.objectContaining({
      sourceType: 'PURCHASE_RECEIPT',
      supplierId: 99,
      ownershipType: 'PROJECT',
      projectId: 188,
      businessDateFrom: '2026-07-04',
      businessDateTo: '2026-07-04',
      page: 1,
      pageSize: 10,
    }))
    expect(formWrapper.text()).toContain('2026-07-04')
    expect(formWrapper.text()).not.toContain('2026-07-03')
    await formWrapper.find('input[name="expense-business-date"]').setValue('2026-08-05')
    await formWrapper.find('input[name="expense-pretax-amount"]').setValue('120.00')
    await formWrapper.find('input[name="expense-tax-rate"]').setValue('0.060000')
    await formWrapper.find('[data-test="save-expense"]').trigger('click')
    await flushPromises()
    expect(expenseApiMock.expenses.create).toHaveBeenCalledWith(expect.objectContaining({
      supplierId: 99,
      ownershipType: 'PROJECT',
      projectId: 188,
      lines: [expect.objectContaining({ sourceType: null, sourceId: null, pretaxAmount: '120.00', taxRate: '0.060000' })],
    }))

    expenseApiMock.expenses.create.mockClear()
    const { wrapper: publicForm } = await mountFinanceView(ExpenseFormView, ['finance:expense:create'], '/finance/expenses/create')
    await publicForm.find('input[name="expense-business-date"]').setValue('2026-08-05')
    await publicForm.find('input[name="expense-pretax-amount"]').setValue('80.00')
    await publicForm.find('input[name="expense-tax-rate"]').setValue('0.060000')
    await publicForm.find('[data-test="save-expense"]').trigger('click')
    await flushPromises()
    expect(expenseApiMock.expenses.create).toHaveBeenCalledWith(expect.objectContaining({
      ownershipType: 'PUBLIC',
      projectId: null,
    }))

    const { wrapper: detailWrapper } = await mountFinanceView(ExpenseDetailView, ['finance:expense:view'], '/finance/expenses/31')
    expect(detailWrapper.text()).toContain('028 不写库存价值、工单成本或项目利润')
    expect(detailWrapper.text()).toContain('应付链接')
  })

  it('预收预付页面展示可用余额、多目标核销摘要和受控资金方式', async () => {
    const { wrapper: advanceList } = await mountFinanceView(AdvanceReceiptListView, ['finance:advance-receipt:view'], '/finance/advance-receipts')
    expect(advanceList.text()).toContain('展示已过账收款形成的客户未核销余额')
    expect(advanceList.text()).toContain('资金状态')
    expect(advanceList.text()).toContain('核销状态')
    expect(advanceList.text()).toContain('可用余额大于 0')
    expect(advanceList.text()).toContain('380.00')

    const { wrapper: prepaymentList } = await mountFinanceView(PrepaymentListView, ['finance:prepayment:view'], '/finance/prepayments')
    expect(prepaymentList.text()).toContain('展示已过账付款形成的供应商未核销余额')
    expect(prepaymentList.text()).toContain('资金状态')
    expect(prepaymentList.text()).toContain('核销状态')
    expect(prepaymentList.text()).toContain('可用余额大于 0')
    expect(prepaymentList.text()).toContain('ADV-P-001')

    const { wrapper: advanceForm } = await mountFinanceView(AdvanceReceiptFormView, ['finance:advance-receipt:create'], '/finance/advance-receipts/create')
    expect(advanceForm.find('[data-test="fund-method-select"]').exists()).toBe(true)
    expect(masterDataApiMock.customers.list).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 200 }))
    await advanceForm.find('input[name="advance-business-date"]').setValue('2026-08-06')
    await advanceForm.find('input[name="advance-amount"]').setValue('650.00')
    await advanceForm.find('[data-test="save-advance-receipt"]').trigger('click')
    await flushPromises()
    expect(settlementApiMock.advanceReceipts.create).toHaveBeenCalledWith(expect.objectContaining({
      partnerId: 88,
      projectId: 188,
      amount: '650.00',
      allocations: expect.any(Array),
    }))

    const { wrapper: prepaymentForm } = await mountFinanceView(PrepaymentFormView, ['finance:prepayment:create'], '/finance/prepayments/create')
    expect(prepaymentForm.find('[data-test="fund-method-select"]').exists()).toBe(true)
    expect(masterDataApiMock.suppliers.list).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 200 }))

    settlementApiMock.advanceReceipts.get.mockResolvedValueOnce({
      ...advanceReceipt,
      partnerName: '',
      customerName: '稳定客户',
      fundNo: '',
      sourceSummary: '收款 RC-REAL-001 已过账',
    })
    const { wrapper: advanceDetail } = await mountFinanceView(AdvanceReceiptDetailView, ['finance:advance-receipt:view', 'finance:settlement-allocation:create'], '/finance/advance-receipts/41')
    expect(advanceDetail.text()).toContain('发起核销')
    expect(advanceDetail.text()).toContain('已核销目标')
    expect(advanceDetail.text()).toContain('稳定客户')
    expect(advanceDetail.text()).toContain('收款 RC-REAL-001 已过账')

    settlementApiMock.prepayments.get.mockResolvedValueOnce({
      ...prepayment,
      partnerName: '',
      supplierName: '稳定供应商',
      fundNo: '',
      sourceSummary: '付款 PM-REAL-001 已过账',
    })
    const { wrapper: prepaymentDetail } = await mountFinanceView(PrepaymentDetailView, ['finance:prepayment:view'], '/finance/prepayments/51')
    expect(prepaymentDetail.text()).toContain('可用余额')
    expect(prepaymentDetail.text()).toContain('稳定供应商')
    expect(prepaymentDetail.text()).toContain('付款 PM-REAL-001 已过账')
  })

  it('对账核销工作台支持独立候选池、多目标选择、超额禁用和统一确认提交', async () => {
    const { wrapper } = await mountFinanceView(SettlementWorkbenchView, ['finance:settlement-allocation:view', 'finance:settlement-allocation:create', 'finance:settlement-allocation:post'], '/finance/settlement-workbench')

    expect(wrapper.text()).toContain('按往来方和项目/公共归属核销资金余额与应收应付，不新增现金发生额')
    expect(settlementApiMock.settlementWorkbench.funds).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 50 }))
    expect(settlementApiMock.settlementWorkbench.targets).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 50 }))
    await wrapper.find('[data-test="select-settlement-fund"]').trigger('click')
    await wrapper.find('[data-test="select-settlement-target"]').trigger('click')
    expect(wrapper.text()).not.toContain('单目标核销金额')
    expect(wrapper.text()).not.toContain('项目 ID')
    expect(wrapper.text()).toContain('应收账款')
    expect(wrapper.text()).toContain('部分收款')
    expect(wrapper.text()).not.toContain('RECEIVABLE')
    expect(wrapper.text()).not.toContain('PARTIALLY_RECEIVED')
    expect(wrapper.find('input[name="settlement-allocation-amount"]').exists()).toBe(false)
    await wrapper.find('input[name="settlement-target-amount-11"]').setValue('999.00')
    expect(wrapper.text()).toContain('本次核销金额不能超过资金可用余额或目标未结余额')
    expect(wrapper.find('[data-test="save-settlement-allocation"]').attributes('disabled')).toBeDefined()
    await wrapper.find('input[name="settlement-target-amount-11"]').setValue('43.00')
    await wrapper.find('[data-test="save-settlement-allocation"]').trigger('click')
    await flushPromises()
    expect(settlementApiMock.settlementWorkbench.create).toHaveBeenCalledWith(expect.objectContaining({
      version: 0,
      cashSourceType: 'RECEIPT',
      funds: [expect.objectContaining({ amount: '43.00' })],
      targets: [expect.objectContaining({ amount: '43.00' })],
      lines: [expect.objectContaining({ targetType: 'SALES_INVOICE', targetId: 11, amount: '43.00' })],
    }))
  })

  it('采购、费用、资金和凭证详情动作按 allowedActions 调用真实 API 并在终态隐藏', async () => {
    invoiceApiMock.purchaseInvoices.get.mockResolvedValueOnce({
      ...purchaseInvoice,
      status: 'DRAFT',
      matchStatus: 'MATCHED',
      differenceCount: 0,
      allowedActions: ['CONFIRM', 'CANCEL'],
    })
    const { wrapper: purchaseDetail } = await mountFinanceView(
      PurchaseInvoiceDetailView,
      ['finance:purchase-invoice:view', 'finance:purchase-invoice:confirm', 'finance:purchase-invoice:cancel'],
      '/finance/purchase-invoices/21',
    )
    expect(purchaseDetail.text()).toContain('匹配通过，差异数 0')
    await purchaseDetail.find('[data-test="confirm-purchase-invoice"]').trigger('click')
    await flushPromises()
    expect(confirmActionMock).toHaveBeenCalledWith('确认采购发票“PI-001”？')
    expect(invoiceApiMock.purchaseInvoices.confirm).toHaveBeenCalledWith(21, expect.objectContaining({ version: 1, idempotencyKey: expect.any(String) }))

    expenseApiMock.expenses.get.mockResolvedValueOnce({ ...expense, status: 'DRAFT', allowedActions: ['CONFIRM', 'CANCEL'] })
    const { wrapper: expenseDetail } = await mountFinanceView(
      ExpenseDetailView,
      ['finance:expense:view', 'finance:expense:confirm', 'finance:expense:cancel'],
      '/finance/expenses/31',
    )
    await expenseDetail.find('[data-test="confirm-expense"]').trigger('click')
    await flushPromises()
    expect(expenseApiMock.expenses.confirm).toHaveBeenCalledWith(31, expect.objectContaining({ version: 1, idempotencyKey: expect.any(String) }))
    expenseApiMock.expenses.get.mockResolvedValueOnce({ ...expense, status: 'DRAFT', allowedActions: ['CANCEL'] })
    const { wrapper: expenseCancelDetail } = await mountFinanceView(
      ExpenseDetailView,
      ['finance:expense:view', 'finance:expense:cancel'],
      '/finance/expenses/31',
    )
    await expenseCancelDetail.find('[data-test="cancel-expense"]').trigger('click')
    await flushPromises()
    expect(expenseApiMock.expenses.cancel).toHaveBeenCalledWith(31, expect.objectContaining({ version: 1, idempotencyKey: expect.any(String) }))

    const draftAdvance = { ...advanceReceipt, status: 'DRAFT', allowedActions: ['POST', 'CANCEL'], settlementStatus: 'AVAILABLE' }
    settlementApiMock.advanceReceipts.get.mockResolvedValueOnce(draftAdvance)
    const { wrapper: advanceDetail } = await mountFinanceView(
      AdvanceReceiptDetailView,
      ['finance:advance-receipt:view', 'finance:advance-receipt:post', 'finance:advance-receipt:cancel'],
      '/finance/advance-receipts/41',
    )
    await advanceDetail.find('[data-test="post-advance-receipt"]').trigger('click')
    await flushPromises()
    expect(settlementApiMock.advanceReceipts.post).toHaveBeenCalledWith(41, expect.objectContaining({ version: 1, idempotencyKey: expect.any(String) }))
    settlementApiMock.advanceReceipts.get.mockResolvedValueOnce(draftAdvance)
    const { wrapper: advanceCancelDetail } = await mountFinanceView(
      AdvanceReceiptDetailView,
      ['finance:advance-receipt:view', 'finance:advance-receipt:cancel'],
      '/finance/advance-receipts/41',
    )
    await advanceCancelDetail.find('[data-test="cancel-advance-receipt"]').trigger('click')
    await flushPromises()
    expect(settlementApiMock.advanceReceipts.cancel).toHaveBeenCalledWith(41, expect.objectContaining({ version: 1, idempotencyKey: expect.any(String) }))

    const draftPrepayment = { ...prepayment, status: 'DRAFT', allowedActions: ['POST', 'CANCEL'], settlementStatus: 'AVAILABLE' }
    settlementApiMock.prepayments.get.mockResolvedValueOnce(draftPrepayment)
    const { wrapper: prepaymentDetail } = await mountFinanceView(
      PrepaymentDetailView,
      ['finance:prepayment:view', 'finance:prepayment:post', 'finance:prepayment:cancel'],
      '/finance/prepayments/51',
    )
    await prepaymentDetail.find('[data-test="post-prepayment"]').trigger('click')
    await flushPromises()
    expect(settlementApiMock.prepayments.post).toHaveBeenCalledWith(51, expect.objectContaining({ version: 1, idempotencyKey: expect.any(String) }))
    settlementApiMock.prepayments.get.mockResolvedValueOnce(draftPrepayment)
    const { wrapper: prepaymentCancelDetail } = await mountFinanceView(
      PrepaymentDetailView,
      ['finance:prepayment:view', 'finance:prepayment:cancel'],
      '/finance/prepayments/51',
    )
    await prepaymentCancelDetail.find('[data-test="cancel-prepayment"]').trigger('click')
    await flushPromises()
    expect(settlementApiMock.prepayments.cancel).toHaveBeenCalledWith(51, expect.objectContaining({ version: 1, idempotencyKey: expect.any(String) }))

    const { wrapper: voucherDetail } = await mountFinanceView(
      VoucherDraftDetailView,
      ['finance:voucher-draft:view', 'finance:voucher-draft:ready', 'finance:voucher-draft:cancel'],
      '/finance/voucher-drafts/61',
    )
    await voucherDetail.find('[data-test="ready-voucher-draft"]').trigger('click')
    await flushPromises()
    expect(voucherApiMock.voucherDrafts.markReady).toHaveBeenCalledWith(61, expect.objectContaining({ version: 1, idempotencyKey: expect.any(String) }))
    await voucherDetail.find('[data-test="cancel-voucher-draft"]').trigger('click')
    await flushPromises()
    expect(voucherApiMock.voucherDrafts.cancel).toHaveBeenCalledWith(61, expect.objectContaining({ version: 1, idempotencyKey: expect.any(String) }))

    voucherApiMock.voucherDrafts.get.mockResolvedValueOnce({ ...voucherDraft, status: 'READY', allowedActions: [] })
    const { wrapper: readyVoucher } = await mountFinanceView(VoucherDraftDetailView, ['finance:voucher-draft:view', 'finance:voucher-draft:ready', 'finance:voucher-draft:cancel'], '/finance/voucher-drafts/61')
    expect(readyVoucher.find('[data-test="ready-voucher-draft"]').exists()).toBe(false)
    expect(readyVoucher.find('[data-test="cancel-voucher-draft"]').exists()).toBe(false)
  })

  it('核销工作台消费详情 query、多目标分配并进入核销详情页', async () => {
    const { wrapper, router } = await mountFinanceView(
      SettlementWorkbenchView,
      ['finance:settlement-allocation:view', 'finance:settlement-allocation:create', 'finance:settlement-allocation:post'],
      '/finance/settlement-workbench?direction=CUSTOMER&fundType=ADVANCE_RECEIPT&fundId=41&returnTo=/finance/advance-receipts/41',
    )

    expect(settlementApiMock.settlementWorkbench.funds).toHaveBeenCalledWith(expect.objectContaining({ direction: 'CUSTOMER', page: 1, pageSize: 50 }))
    expect(wrapper.text()).toContain('来源资金：RC-001')
    await wrapper.findAll('[data-test="select-settlement-target"]')[0].trigger('click')
    await wrapper.findAll('[data-test="select-settlement-target"]')[1].trigger('click')
    await wrapper.find('input[name="settlement-target-amount-11"]').setValue('43.00')
    await wrapper.find('input[name="settlement-target-amount-12"]').setValue('77.00')
    expect(wrapper.text()).toContain('已选目标 2 个')
    expect(wrapper.text()).toContain('剩余可分配 260.00')
    await wrapper.find('[data-test="save-settlement-allocation"]').trigger('click')
    await flushPromises()

    expect(settlementApiMock.settlementWorkbench.create).toHaveBeenCalledWith(expect.objectContaining({
      version: 0,
      cashSourceType: 'RECEIPT',
      funds: [expect.objectContaining({ fundId: 41, fundType: 'ADVANCE_RECEIPT', amount: '120.00' })],
      targets: [
        expect.objectContaining({ targetId: 11, amount: '43.00' }),
        expect.objectContaining({ targetId: 12, amount: '77.00' }),
      ],
      lines: [
        expect.objectContaining({ targetId: 11, amount: '43.00' }),
        expect.objectContaining({ targetId: 12, amount: '77.00' }),
      ],
    }))
    expect(router.currentRoute.value.name).toBe('finance-settlement-allocation-detail')
    expect(router.currentRoute.value.params.id).toBe('71')

    const { default: SettlementAllocationDetailView } = await import('./SettlementAllocationDetailView.vue')
    const { wrapper: detailWrapper } = await mountFinanceView(
      SettlementAllocationDetailView,
      ['finance:settlement-allocation:view'],
      '/finance/settlement-workbench/allocations/71',
    )
    expect(detailWrapper.text()).toContain('ALLOC-001')
    expect(detailWrapper.text()).toContain('SI-001')
    expect(detailWrapper.text()).toContain('AR-002')
  })

  it('凭证草稿列表从真实来源生成草稿并携带版本与幂等键', async () => {
    voucherApiMock.voucherDrafts.list.mockResolvedValueOnce(page([{ ...voucherDraft, partnerName: '', partyName: '后端稳定往来方' }]))
    invoiceApiMock.salesInvoices.list.mockResolvedValueOnce(page([{ ...salesInvoice, status: 'CONFIRMED', allowedActions: [] }]))
    const { wrapper } = await mountFinanceView(
      VoucherDraftListView,
      ['finance:voucher-draft:view', 'finance:voucher-draft:generate'],
      '/finance/voucher-drafts',
    )

    expect(wrapper.text()).not.toContain('预收款')
    expect(wrapper.text()).not.toContain('预付款')
    expect(wrapper.find('input[name="voucher-source-id"]').exists()).toBe(false)
    expect(wrapper.find('input[name="voucher-source-version"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('后端稳定往来方')
    await setSelectValue(wrapper, 2, 'RECEIPT')
    await buttonsByText(wrapper, '查询')[0].trigger('click')
    await flushPromises()
    expect(voucherApiMock.voucherDrafts.list).toHaveBeenLastCalledWith(expect.objectContaining({ sourceType: 'RECEIPT' }))
    await setSelectValue(wrapper, 2, 'PAYMENT')
    await buttonsByText(wrapper, '查询')[0].trigger('click')
    await flushPromises()
    expect(voucherApiMock.voucherDrafts.list).toHaveBeenLastCalledWith(expect.objectContaining({ sourceType: 'PAYMENT' }))
    expect(wrapper.text()).toContain('SI-001')
    await setSelectValue(wrapper, 1, 'SALES_INVOICE:11')
    await wrapper.find('[data-test="generate-voucher-draft"]').trigger('click')
    await flushPromises()

    expect(voucherApiMock.voucherDrafts.generate).toHaveBeenCalledWith({
      sourceType: 'SALES_INVOICE',
      sourceId: 11,
      version: 2,
      idempotencyKey: expect.any(String),
    })
  })

  it('凭证草稿生成可通过已过账收款业务候选提交 RECEIPT 来源和版本', async () => {
    const { wrapper } = await mountFinanceView(
      VoucherDraftListView,
      ['finance:voucher-draft:view', 'finance:voucher-draft:generate'],
      '/finance/voucher-drafts',
    )

    await setSelectValue(wrapper, 0, 'RECEIPT')
    await buttonsByText(wrapper, '查询来源')[0].trigger('click')
    await flushPromises()
    expect(financeApiMock.receipts.list).toHaveBeenCalledWith(expect.objectContaining({ status: 'POSTED', page: 1, pageSize: 20 }))
    await setSelectValue(wrapper, 1, 'RECEIPT:81')
    await wrapper.find('[data-test="generate-voucher-draft"]').trigger('click')
    await flushPromises()

    expect(voucherApiMock.voucherDrafts.generate).toHaveBeenCalledWith({
      sourceType: 'RECEIPT',
      sourceId: 81,
      version: 4,
      idempotencyKey: expect.any(String),
    })
  })

  it('凭证草稿生成可通过已过账付款和已过账核销业务候选提交真实版本', async () => {
    const { wrapper } = await mountFinanceView(
      VoucherDraftListView,
      ['finance:voucher-draft:view', 'finance:voucher-draft:generate'],
      '/finance/voucher-drafts',
    )

    await setSelectValue(wrapper, 0, 'PAYMENT')
    await buttonsByText(wrapper, '查询来源')[0].trigger('click')
    await flushPromises()
    expect(financeApiMock.payments.list).toHaveBeenCalledWith(expect.objectContaining({ status: 'POSTED', page: 1, pageSize: 20 }))
    await setSelectValue(wrapper, 1, 'PAYMENT:91')
    await wrapper.find('[data-test="generate-voucher-draft"]').trigger('click')
    await flushPromises()
    expect(voucherApiMock.voucherDrafts.generate).toHaveBeenLastCalledWith({
      sourceType: 'PAYMENT',
      sourceId: 91,
      version: 5,
      idempotencyKey: expect.any(String),
    })

    voucherApiMock.voucherDrafts.generate.mockClear()
    await setSelectValue(wrapper, 0, 'SETTLEMENT_ALLOCATION')
    await buttonsByText(wrapper, '查询来源')[0].trigger('click')
    await flushPromises()
    expect(settlementApiMock.settlementWorkbench.allocations).toHaveBeenCalledWith(expect.objectContaining({ status: 'POSTED', page: 1, pageSize: 20 }))
    await setSelectValue(wrapper, 1, 'SETTLEMENT_ALLOCATION:71')
    await wrapper.find('[data-test="generate-voucher-draft"]').trigger('click')
    await flushPromises()
    expect(voucherApiMock.voucherDrafts.generate).toHaveBeenCalledWith({
      sourceType: 'SETTLEMENT_ALLOCATION',
      sourceId: 71,
      version: 2,
      idempotencyKey: expect.any(String),
    })
  })

  it('凭证草稿来源缺真实版本时不启用生成按钮且不提交伪造版本', async () => {
    financeApiMock.receipts.list.mockResolvedValueOnce(page([{
      id: 82,
      receiptNo: 'RC-NO-VERSION',
      receivableId: 12,
      receivableNo: 'AR-002',
      customerId: 88,
      customerName: '真实客户',
      receiptDate: '2026-08-06',
      amount: '100.00',
      method: 'BANK_TRANSFER',
      status: 'POSTED',
      createdByName: '财务用户',
    }]))
    const { wrapper } = await mountFinanceView(
      VoucherDraftListView,
      ['finance:voucher-draft:view', 'finance:voucher-draft:generate'],
      '/finance/voucher-drafts',
    )

    await setSelectValue(wrapper, 0, 'RECEIPT')
    await buttonsByText(wrapper, '查询来源')[0].trigger('click')
    await flushPromises()
    await setSelectValue(wrapper, 1, 'RECEIPT:82')

    expect(wrapper.find('[data-test="generate-voucher-draft"]').attributes('disabled')).toBeDefined()
    voucherApiMock.voucherDrafts.generate.mockClear()
    await wrapper.find('[data-test="generate-voucher-draft"]').trigger('click')
    await flushPromises()
    expect(voucherApiMock.voucherDrafts.generate).not.toHaveBeenCalled()
  })

  it('费用详情和发票详情使用多态来源追溯并显示真实受限原因', async () => {
    expenseApiMock.expenses.get.mockResolvedValueOnce({
      ...expense,
      supplierName: '',
      partyName: '稳定费用供应商',
      categoryName: '',
      categorySummary: '稳定费用分类',
      businessDate: '2026-08-05T11:22:33+08:00',
      sourceType: 'OUTSOURCING_RECEIPT',
      sourceNo: undefined,
      sourceSummary: '外协收货 OSR-001 运费',
      sources: [{ sourceType: 'OUTSOURCING_RECEIPT', sourceNo: 'OSR-001', restricted: true, restrictedReason: '来源权限受限' }],
    })
    const { wrapper: expenseDetail } = await mountFinanceView(ExpenseDetailView, ['finance:expense:view'], '/finance/expenses/31')
    expect(expenseDetail.text()).toContain('稳定费用供应商')
    expect(expenseDetail.text()).toContain('稳定费用分类')
    expect(expenseDetail.text()).toContain('2026-08-05')
    expect(expenseDetail.text()).not.toContain('2026-08-05T11:22:33')
    expect(expenseDetail.text()).toContain('外协收货 OSR-001 运费')
    expect(expenseDetail.text()).toContain('外协收货')
    expect(expenseDetail.text()).toContain('来源权限受限')
    expect(expenseDetail.text()).not.toContain('采购入库 PR-001')

    invoiceApiMock.salesInvoices.get.mockResolvedValueOnce({
      ...salesInvoice,
      sources: [{ sourceType: 'SALES_SHIPMENT', sourceNo: 'SS-001', restricted: true, restrictedReason: '销售出库权限受限' }],
    })
    const { wrapper: salesDetail } = await mountFinanceView(SalesInvoiceDetailView, ['finance:sales-invoice:view'], '/finance/sales-invoices/11')
    expect(salesDetail.text()).toContain('销售出库权限受限')
  })

  it('凭证草稿页面显著非正式边界且不出现正式凭证动作', async () => {
    const { wrapper: listWrapper } = await mountFinanceView(VoucherDraftListView, ['finance:voucher-draft:view', 'finance:voucher-draft:generate'], '/finance/voucher-drafts')
    expect(listWrapper.text()).toContain('仅为 031 正式制证提供业务分类建议，不是正式凭证')
    expect(listWrapper.text()).toContain('借贷不平衡')
    expect(listWrapper.text()).not.toContain('审核')
    expect(listWrapper.text()).not.toContain('记账')

    const { wrapper: detailWrapper } = await mountFinanceView(VoucherDraftDetailView, ['finance:voucher-draft:view', 'finance:voucher-draft:ready'], '/finance/voucher-drafts/61')
    expect(detailWrapper.text()).toContain('非正式凭证草稿')
    expect(detailWrapper.text()).toContain('来源受限')
    expect(detailWrapper.text()).not.toContain('正式科目编码')
    expect(detailWrapper.text()).not.toContain('正式凭证号')
  })

  it('凭证详情按冻结 DTO 渲染借贷合计、分录金额、来源摘要和生成版本', async () => {
    voucherApiMock.voucherDrafts.get.mockResolvedValueOnce({
      ...voucherDraft,
      debitTotal: undefined,
      creditTotal: undefined,
      debitAmount: '120.00',
      creditAmount: '120.00',
      generationVersion: 3,
      sourceSummary: { sourceType: 'RECEIPT', sourceNo: 'RC-POSTED-001', summary: '收款 RC-POSTED-001', restricted: false },
      lines: [
        { direction: 'DEBIT', businessCategory: 'CASH_DRAFT', summary: '收款形成现金', amount: '120.00', sourceType: 'RECEIPT', sourceId: 81 },
        { direction: 'CREDIT', businessCategory: 'ADVANCE_RECEIPT_DRAFT', summary: '形成预收余额', amount: '120.00', sourceType: 'RECEIPT', sourceId: 81 },
      ],
    })
    const { wrapper } = await mountFinanceView(VoucherDraftDetailView, ['finance:voucher-draft:view'], '/finance/voucher-drafts/61')

    expect(wrapper.text()).toContain('借方 120.00')
    expect(wrapper.text()).toContain('贷方 120.00')
    expect(wrapper.text()).toContain('第 3 版建议')
    expect(wrapper.text()).toContain('收款 RC-POSTED-001')
    expect(wrapper.text()).toContain('现金草稿')
    expect(wrapper.text()).toContain('预收草稿')
    expect(wrapper.text()).not.toContain('CASH_DRAFT')
    expect(wrapper.text()).not.toContain('ADVANCE_RECEIPT_DRAFT')
    expect(wrapper.text()).toContain('收款形成现金')
  })

  it('READY 凭证草稿按 GL 权限提供正式制证入口并保留 returnTo', async () => {
    voucherApiMock.voucherDrafts.list.mockResolvedValueOnce(page([{ ...voucherDraft, status: 'READY', balanced: true, debitTotal: '113.00', creditTotal: '113.00' }]))
    const { wrapper, router } = await mountFinanceView(
      VoucherDraftListView,
      ['finance:voucher-draft:view', 'gl:voucher:convert', 'gl:voucher:view'],
      '/finance/voucher-drafts',
    )

    expect(glApiMock.vouchers.list).toHaveBeenCalledWith(expect.objectContaining({
      sourceType: 'FIN_VOUCHER_DRAFT',
      sourceId: 61,
      page: 1,
      pageSize: 10,
    }))
    expect(wrapper.text()).toContain('生成正式凭证草稿')

    await wrapper.find('[data-test="convert-gl-voucher"]').trigger('click')
    await flushPromises()

    expect(glApiMock.vouchers.fromFinanceDraft).toHaveBeenCalledWith(61, expect.objectContaining({
      version: 1,
      idempotencyKey: expect.stringContaining('gl-convert-finance-draft-'),
    }))
    expect(router.currentRoute.value.name).toBe('gl-voucher-detail')
    expect(router.currentRoute.value.params.id).toBe('91')
    expect(router.currentRoute.value.query.returnTo).toBe('/finance/voucher-drafts')
  })

  it('已关联正式凭证的 028 草稿只展示查看正式凭证入口，不重复转换', async () => {
    glApiMock.vouchers.list.mockResolvedValueOnce(page([{
      id: 90,
      draftNo: 'GLD-202607-0000',
      voucherNo: '记-202607-0000',
      status: 'POSTED',
      sourceType: 'FIN_VOUCHER_DRAFT',
      sourceId: 999,
      debitTotal: '999.00',
      creditTotal: '999.00',
      version: 2,
      allowedActions: [],
      actionDisabledReasons: {},
    }, {
      id: 91,
      draftNo: 'GLD-202607-0001',
      voucherNo: '记-202607-0001',
      status: 'POSTED',
      sourceType: 'FIN_VOUCHER_DRAFT',
      sourceId: 61,
      debitTotal: '113.00',
      creditTotal: '113.00',
      version: 2,
      allowedActions: [],
      actionDisabledReasons: {},
    }]))

    const { wrapper, router } = await mountFinanceView(
      VoucherDraftDetailView,
      ['finance:voucher-draft:view', 'gl:voucher:convert', 'gl:voucher:view'],
      '/finance/voucher-drafts/61',
    )

    expect(wrapper.text()).toContain('已生成正式凭证')
    expect(wrapper.text()).toContain('记-202607-0001')
    expect(wrapper.text()).not.toContain('记-202607-0000')
    expect(wrapper.text()).not.toContain('生成正式凭证草稿')

    await wrapper.find('[data-test="view-gl-voucher"]').trigger('click')
    await flushPromises()

    expect(glApiMock.vouchers.fromFinanceDraft).not.toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('gl-voucher-detail')
    expect(router.currentRoute.value.params.id).toBe('91')
    expect(router.currentRoute.value.query.returnTo).toBe('/finance/voucher-drafts/61')
  })

  it('来源候选为空时明确提示暂无可生成来源并引导调整筛选', async () => {
    invoiceApiMock.salesInvoices.list.mockResolvedValue(page([]))
    const { wrapper } = await mountFinanceView(
      VoucherDraftListView,
      ['finance:voucher-draft:view', 'finance:voucher-draft:generate'],
      '/finance/voucher-drafts',
    )

    await buttonsByText(wrapper, '查询来源')[0].trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('暂无可生成来源')
    expect(wrapper.text()).toContain('请调整筛选条件')
    expect(wrapper.find('[data-test="generate-voucher-draft"]').attributes('disabled')).toBeDefined()
  })
})
