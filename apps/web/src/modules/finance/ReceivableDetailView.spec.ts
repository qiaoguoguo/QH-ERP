import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReceivableDetailRecord } from '../../shared/api/financeApi'
import ReceivableDetailView from './ReceivableDetailView.vue'
import { buttonsByText, mountFinanceView } from './financeTestHelpers'

const financeApiMock = vi.hoisted(() => ({
  receivables: { get: vi.fn(), confirm: vi.fn(), cancel: vi.fn(), close: vi.fn() },
}))
vi.mock('../../shared/api/financeApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/financeApi')>()),
  financeApi: financeApiMock,
}))

const receivableDetail: ReceivableDetailRecord = {
  id: 1,
  receivableNo: 'AR-001',
  customerId: 100,
  customerCode: 'CUS-A',
  customerName: '华东客户',
  sourceType: 'SALES_SHIPMENT',
  sourceId: 200,
  sourceNo: 'SS-001',
  salesOrderId: 300,
  salesOrderNo: 'SO-001',
  businessDate: '2026-07-04',
  dueDate: '2026-07-31',
  totalAmount: '900.50',
  receivedAmount: '100.00',
  unreceivedAmount: '800.50',
  status: 'PARTIALLY_RECEIVED',
  remark: '应收详情',
  createdByName: '财务员',
  createdAt: '2026-07-04T08:00:00+08:00',
  updatedAt: '2026-07-04T09:00:00+08:00',
  sources: [{
    id: 10,
    sourceType: 'SALES_SHIPMENT',
    sourceId: 200,
    sourceNo: 'SS-001',
    sourceLineId: 201,
    sourceLineNo: 1,
    sourceBusinessDate: '2026-07-04',
    salesOrderId: 300,
    salesOrderNo: 'SO-001',
    salesOrderLineId: 301,
    materialId: 400,
    materialCode: 'MAT-A',
    materialName: '制造件',
    unitName: '件',
    quantity: '3',
    unitPrice: '300.166666',
    sourceAmount: '900.50',
  }],
  receipts: [{
    id: 20,
    receiptNo: 'RC-001',
    receivableId: 1,
    receivableNo: 'AR-001',
    customerId: 100,
    customerName: '华东客户',
    receiptDate: '2026-07-10',
    amount: '100.00',
    method: 'BANK_TRANSFER',
    status: 'POSTED',
    createdByName: '财务员',
    postedByName: '财务主管',
    postedAt: '2026-07-10T09:00:00+08:00',
  }],
  auditSummary: [{ action: '确认应收', operatorName: '财务员' }],
}

describe('应收详情页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('confirm', vi.fn(() => true))
    financeApiMock.receivables.get.mockResolvedValue(receivableDetail)
    financeApiMock.receivables.close.mockResolvedValue({ ...receivableDetail, status: 'CLOSED' })
  })

  afterEach(() => vi.unstubAllGlobals())

  it('展示基础信息、金额摘要、来源追溯、收款记录和审计摘要', async () => {
    const { wrapper, router } = await mountFinanceView(ReceivableDetailView, [
      'finance:receivable:view',
      'finance:receivable:close',
      'finance:receipt:create',
      'sales:shipment:view',
      'sales:order:view',
    ], '/finance/receivables/1')

    expect(financeApiMock.receivables.get).toHaveBeenCalledWith('1')
    expect(wrapper.text()).toContain('AR-001')
    expect(wrapper.text()).toContain('华东客户')
    expect(wrapper.text()).toContain('部分收款')
    expect(wrapper.text()).toContain('900.50')
    expect(wrapper.text()).toContain('来源追溯')
    expect(wrapper.text()).toContain('销售出库')
    expect(wrapper.text()).toContain('SS-001')
    expect(wrapper.text()).toContain('销售订单')
    expect(wrapper.text()).toContain('SO-001')
    expect(wrapper.text()).toContain('RC-001')
    expect(wrapper.text()).toContain('确认应收')

    await wrapper.find('[data-test="view-source-shipment"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('sales-shipment-detail')
    expect(router.currentRoute.value.params.id).toBe('200')
  })

  it('按状态和权限显示动作并调用关闭接口', async () => {
    const { wrapper } = await mountFinanceView(ReceivableDetailView, [
      'finance:receivable:view',
      'finance:receivable:close',
      'finance:receipt:create',
    ], '/finance/receivables/1')

    expect(buttonsByText(wrapper, '登记收款')).toHaveLength(1)
    expect(buttonsByText(wrapper, '关闭')).toHaveLength(1)
    await wrapper.find('[data-test="close-receivable-detail"]').trigger('click')
    await flushPromises()
    expect(financeApiMock.receivables.close).toHaveBeenCalledWith(1)
  })
})
