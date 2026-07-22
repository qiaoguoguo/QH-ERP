import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PartnerRecord } from '../../shared/api/masterDataApi'
import type { PayableSummaryRecord } from '../../shared/api/financeApi'
import PayableListView from './PayableListView.vue'
import { clickTeleportedAction, mountFinanceView, openMoreActions, page, setSelectValue, teleportedAction } from './financeTestHelpers'

const financeApiMock = vi.hoisted(() => ({
  payables: { list: vi.fn(), confirm: vi.fn(), cancel: vi.fn(), close: vi.fn() },
}))
const masterDataApiMock = vi.hoisted(() => ({
  suppliers: { list: vi.fn() },
}))
vi.mock('../../shared/api/financeApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/financeApi')>()),
  financeApi: financeApiMock,
}))
vi.mock('../../shared/api/masterDataApi', () => ({ masterDataApi: masterDataApiMock }))

const supplier: PartnerRecord = { id: 100, code: 'SUP-A', name: '华东供应商', status: 'ENABLED' }
const payable: PayableSummaryRecord = {
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
  paidAmount: '0.00',
  unpaidAmount: '900.50',
  status: 'DRAFT',
  createdByName: '财务员',
  createdAt: '2026-07-04T08:00:00+08:00',
  updatedAt: '2026-07-04T09:00:00+08:00',
}

describe('应付台账列表页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    masterDataApiMock.suppliers.list.mockResolvedValue(page([supplier]))
    financeApiMock.payables.list.mockResolvedValue(page([payable]))
    financeApiMock.payables.confirm.mockResolvedValue({ ...payable, status: 'CONFIRMED' })
    financeApiMock.payables.cancel.mockResolvedValue({ ...payable, status: 'CANCELLED' })
    financeApiMock.payables.close.mockResolvedValue({ ...payable, status: 'CLOSED' })
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
  })

  it('加载应付台账并支持筛选、分页、空态和错误态', async () => {
    const { wrapper } = await mountFinanceView(PayableListView, ['finance:payable:view'], '/finance/payables')

    expect(wrapper.text()).toContain('应付台账')
    expect(wrapper.text()).toContain('含历史业务台账，028 发票/费用通过链接衔接')
    expect(wrapper.text()).toContain('AP-001')
    expect(wrapper.text()).toContain('PR-001')
    expect(wrapper.text()).toContain('采购订单')
    expect(wrapper.text()).toContain('发票/费用链接')
    expect(wrapper.text()).toContain('核销摘要')
    expect(wrapper.text()).toContain('凭证草稿')

    await wrapper.find('input[name="payable-keyword"]').setValue('AP')
    await setSelectValue(wrapper, 0, 100)
    await setSelectValue(wrapper, 1, 'DRAFT')
    await wrapper.find('input[name="payable-date-from"]').setValue('2026-07-01')
    await wrapper.find('input[name="payable-date-to"]').setValue('2026-07-31')
    await wrapper.find('input[name="payable-due-date-from"]').setValue('2026-08-01')
    await wrapper.find('input[name="payable-due-date-to"]').setValue('2026-08-31')
    await wrapper.find('input[name="payable-source-no"]').setValue('PR')
    await wrapper.find('[data-test="search-payables"]').trigger('click')
    await flushPromises()

    expect(financeApiMock.payables.list).toHaveBeenLastCalledWith({
      keyword: 'AP',
      supplierId: 100,
      status: 'DRAFT',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-31',
      dueDateFrom: '2026-08-01',
      dueDateTo: '2026-08-31',
      sourceNo: 'PR',
      page: 1,
      pageSize: 10,
    })

    wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 2)
    await flushPromises()
    expect(financeApiMock.payables.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 }))

    financeApiMock.payables.list.mockResolvedValueOnce(page([]))
    await wrapper.find('[data-test="reset-payables"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('暂无应付台账')

    financeApiMock.payables.list.mockRejectedValueOnce(new Error('应付接口异常'))
    await wrapper.find('[data-test="search-payables"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('应付接口异常')
  })

  it('按权限显示草稿操作并调用确认接口', async () => {
    const { wrapper } = await mountFinanceView(PayableListView, [
      'finance:payable:view',
      'finance:payable:update',
      'finance:payable:confirm',
      'finance:payable:cancel',
    ], '/finance/payables', { attachTo: document.body })

    expect(wrapper.find('[data-test="edit-payable"]').exists()).toBe(true)
    await openMoreActions(wrapper)
    expect(teleportedAction('confirm-payable')).toBeTruthy()
    expect(teleportedAction('cancel-payable')).toBeTruthy()
    await clickTeleportedAction(wrapper, 'confirm-payable')
    expect(financeApiMock.payables.confirm).toHaveBeenCalledWith(1)
  })
})
