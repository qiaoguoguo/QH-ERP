import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { Component } from 'vue'
import { useAuthStore } from '../../stores/authStore'
import SalesInvoiceDetailView from './SalesInvoiceDetailView.vue'
import PurchaseInvoiceDetailView from './PurchaseInvoiceDetailView.vue'
import GlVoucherDetailView from '../gl/GlVoucherDetailView.vue'

const financeInvoiceApiMock = vi.hoisted(() => ({
  salesInvoices: {
    get: vi.fn(),
  },
  purchaseInvoices: {
    get: vi.fn(),
  },
}))

const glApiMock = vi.hoisted(() => ({
  vouchers: {
    get: vi.fn(),
  },
}))

vi.mock('../../shared/api/financeInvoiceApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/financeInvoiceApi')>()),
  financeInvoiceApi: financeInvoiceApiMock,
}))

vi.mock('../../shared/api/glApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/glApi')>()),
  glApi: glApiMock,
}))

vi.mock('../../shared/ui/confirmDialog', () => ({
  confirmAction: vi.fn(async () => true),
}))

vi.mock('../platform/components/FixedPrintAction.vue', () => ({
  default: {
    name: 'FixedPrintAction',
    props: ['objectType', 'objectId', 'objectNo', 'objectStatus', 'allowedObjectStatuses', 'title'],
    template: '<section data-test="fixed-print-entry">{{ objectType }} {{ objectId }} {{ objectNo }} {{ title }}</section>',
  },
}))

const salesInvoice = {
  id: 810,
  invoiceNo: 'SI-034-001',
  status: 'CONFIRMED',
  settlementStatus: 'UNSETTLED',
  customerName: '华东客户',
  totalAmount: '1000.00',
  unsettledAmount: '1000.00',
  allowedActions: [],
  version: 2,
  receivableLinks: [],
  settlements: [],
  voucherDrafts: [],
  sources: [],
}

const purchaseInvoice = {
  id: 820,
  invoiceNo: 'PI-034-001',
  status: 'CONFIRMED',
  matchStatus: 'MATCHED',
  settlementStatus: 'UNSETTLED',
  sourceType: 'PURCHASE_RECEIPT',
  supplierName: '华东五金',
  totalAmount: '600.00',
  unsettledAmount: '600.00',
  allowedActions: [],
  version: 3,
  matchDifferences: [],
  payableLinks: [],
  settlements: [],
  voucherDrafts: [],
  sources: [],
}

const glVoucher = {
  id: 830,
  draftNo: 'VD-034-001',
  voucherNo: '记-034-001',
  voucherDate: '2026-07-21',
  accountingPeriodCode: '2026-07',
  status: 'POSTED',
  sourceType: 'FIN_VOUCHER_DRAFT',
  sourceNo: 'VD-034-001',
  sourceVisible: true,
  amountVisible: true,
  debitTotal: '1000.00',
  creditTotal: '1000.00',
  allowedActions: [],
  actionDisabledReasons: {},
  version: 4,
  lines: [],
  auditSummary: [],
}

async function mountDetail(path: string, component: Component) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions: [
      'finance:sales-invoice:view',
      'finance:purchase-invoice:view',
      'gl:voucher:view',
      'platform:print:generate',
    ],
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/finance/sales-invoices/:id', name: 'finance-sales-invoice-detail', component },
      { path: '/finance/purchase-invoices/:id', name: 'finance-purchase-invoice-detail', component },
      { path: '/gl/vouchers/:id', name: 'gl-voucher-detail', component },
      { path: '/finance/sales-invoices', name: 'finance-sales-invoices', component: { render: () => null } },
      { path: '/finance/purchase-invoices', name: 'finance-purchase-invoices', component: { render: () => null } },
      { path: '/gl/vouchers', name: 'gl-vouchers', component: { render: () => null } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(component, {
    global: {
      plugins: [pinia, router, ElementPlus],
      stubs: {
        FinanceSourceTracePanel: true,
      },
    },
  })
  await flushPromises()
  return wrapper
}

describe('034 财务与总账固定打印入口', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    financeInvoiceApiMock.salesInvoices.get.mockResolvedValue(salesInvoice)
    financeInvoiceApiMock.purchaseInvoices.get.mockResolvedValue(purchaseInvoice)
    glApiMock.vouchers.get.mockResolvedValue(glVoucher)
  })

  it('销售发票详情接入固定打印入口', async () => {
    const wrapper = await mountDetail('/finance/sales-invoices/810', SalesInvoiceDetailView)

    const entry = wrapper.findComponent({ name: 'FixedPrintAction' })
    expect(entry.exists()).toBe(true)
    expect(entry.props()).toEqual(expect.objectContaining({
      objectType: 'SALES_INVOICE',
      objectId: 810,
      objectNo: 'SI-034-001',
      objectStatus: 'CONFIRMED',
      allowedObjectStatuses: ['CONFIRMED', 'POSTED', 'CLOSED'],
      title: '销售发票固定打印',
    }))
  })

  it('采购发票详情接入固定打印入口', async () => {
    const wrapper = await mountDetail('/finance/purchase-invoices/820', PurchaseInvoiceDetailView)

    const entry = wrapper.findComponent({ name: 'FixedPrintAction' })
    expect(entry.exists()).toBe(true)
    expect(entry.props()).toEqual(expect.objectContaining({
      objectType: 'PURCHASE_INVOICE',
      objectId: 820,
      objectNo: 'PI-034-001',
      objectStatus: 'CONFIRMED',
      allowedObjectStatuses: ['CONFIRMED', 'POSTED', 'CLOSED'],
      title: '采购发票固定打印',
    }))
  })

  it('会计凭证详情接入固定打印入口', async () => {
    const wrapper = await mountDetail('/gl/vouchers/830', GlVoucherDetailView)

    const entry = wrapper.findComponent({ name: 'FixedPrintAction' })
    expect(entry.exists()).toBe(true)
    expect(entry.props()).toEqual(expect.objectContaining({
      objectType: 'ACCOUNTING_VOUCHER',
      objectId: 830,
      objectNo: '记-034-001',
      objectStatus: 'POSTED',
      allowedObjectStatuses: ['POSTED', 'DRAFT'],
      title: '会计凭证固定打印',
    }))
  })
})
