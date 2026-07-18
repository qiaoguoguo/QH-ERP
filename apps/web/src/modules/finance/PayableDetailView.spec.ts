import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PayableDetailRecord } from '../../shared/api/financeApi'
import PayableDetailView from './PayableDetailView.vue'
import { mountFinanceView } from './financeTestHelpers'

const financeApiMock = vi.hoisted(() => ({
  payables: { get: vi.fn(), close: vi.fn() },
}))
vi.mock('../../shared/api/financeApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/financeApi')>()),
  financeApi: financeApiMock,
}))

const detail: PayableDetailRecord = {
  id: 1,
  payableNo: 'AP-001',
  supplierId: 100,
  supplierCode: 'SUP-A',
  supplierName: '华东供应商',
  sourceType: 'PURCHASE_RECEIPT',
  sourceId: 200,
  sourceNo: 'PR-001',
  purchaseOrderId: 300,
  purchaseOrderNo: 'PO-001',
  businessDate: '2026-07-04',
  dueDate: '2026-07-31',
  totalAmount: '900.50',
  paidAmount: '100.00',
  unpaidAmount: '800.50',
  status: 'PARTIALLY_PAID',
  createdByName: '财务员',
  createdAt: '2026-07-04T08:00:00+08:00',
  updatedAt: '2026-07-04T09:00:00+08:00',
  sources: [{
    id: 10,
    sourceType: 'PURCHASE_RECEIPT',
    sourceId: 200,
    sourceNo: 'PR-001',
    sourceLineId: 201,
    sourceLineNo: 1,
    sourceBusinessDate: '2026-07-04',
    purchaseOrderId: 300,
    purchaseOrderNo: 'PO-001',
    purchaseOrderLineId: 301,
    materialId: 400,
    materialCode: 'MAT-A',
    materialName: '制造件',
    unitName: '件',
    quantity: '3',
    unitPrice: '300.166666',
    sourceAmount: '900.50',
  }],
  payments: [{
    id: 20,
    paymentNo: 'PY-001',
    payableId: 1,
    payableNo: 'AP-001',
    supplierId: 100,
    supplierName: '华东供应商',
    paymentDate: '2026-07-10',
    amount: '100.00',
    method: 'BANK_TRANSFER',
    status: 'POSTED',
    createdByName: '财务员',
    postedByName: '财务主管',
    postedAt: '2026-07-10T09:00:00+08:00',
    version: 3,
  }],
  auditSummary: [{ action: '确认应付', operatorName: '财务员' }],
}

describe('应付详情页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    financeApiMock.payables.get.mockResolvedValue(detail)
    financeApiMock.payables.close.mockResolvedValue({ ...detail, status: 'CLOSED' })
  })

  afterEach(() => vi.unstubAllGlobals())

  it('展示基础信息、采购来源追溯和付款记录', async () => {
    const { wrapper, router } = await mountFinanceView(PayableDetailView, [
      'finance:payable:view',
      'finance:payable:close',
      'finance:payment:create',
      'finance:payment:view',
      'procurement:receipt:view',
      'procurement:order:view',
    ], '/finance/payables/1')

    expect(wrapper.text()).toContain('AP-001')
    expect(wrapper.text()).toContain('华东供应商')
    expect(wrapper.text()).toContain('部分付款')
    expect(wrapper.text()).toContain('来源追溯')
    expect(wrapper.text()).toContain('采购入库')
    expect(wrapper.text()).toContain('采购订单')
    expect(wrapper.text()).toContain('PY-001')
    expect(wrapper.text()).toContain('银行转账')
    expect(wrapper.text()).toContain('2026-07-10 09:00')
    expect(wrapper.text()).not.toContain('BANK_TRANSFER')
    expect(wrapper.text()).not.toContain('2026-07-10T09:00:00')
    expect(wrapper.text()).toContain('确认应付')

    await wrapper.find('[data-test="view-source-purchase-receipt"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('procurement-receipt-detail')
    expect(router.currentRoute.value.params.id).toBe('200')
  })

  it('按状态和权限显示关闭动作', async () => {
    const { wrapper } = await mountFinanceView(PayableDetailView, ['finance:payable:view', 'finance:payable:close'], '/finance/payables/1')

    expect(wrapper.find('[data-test="close-payable-detail"]').exists()).toBe(true)
    await wrapper.find('[data-test="close-payable-detail"]').trigger('click')
    await flushPromises()
    expect(financeApiMock.payables.close).toHaveBeenCalledWith(1)
  })
})
