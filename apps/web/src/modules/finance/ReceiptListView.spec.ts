import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PartnerRecord } from '../../shared/api/masterDataApi'
import type { ReceiptSummaryRecord } from '../../shared/api/financeApi'
import { useConfirmActionMock } from '../../test/setup'
import ReceiptListView from './ReceiptListView.vue'
import { clickTeleportedAction, mountFinanceView, openMoreActions, page, setSelectValue, teleportedAction } from './financeTestHelpers'

const confirmActionMock = useConfirmActionMock()

const financeApiMock = vi.hoisted(() => ({
  receipts: { list: vi.fn(), post: vi.fn(), cancel: vi.fn() },
}))
const masterDataApiMock = vi.hoisted(() => ({
  customers: { list: vi.fn() },
}))
vi.mock('../../shared/api/financeApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/financeApi')>()),
  financeApi: financeApiMock,
}))
vi.mock('../../shared/api/masterDataApi', () => ({ masterDataApi: masterDataApiMock }))

const customer: PartnerRecord = { id: 100, code: 'CUS-A', name: '华东客户', status: 'ENABLED' }
const receipt: ReceiptSummaryRecord = {
  id: 1,
  receiptNo: 'RC-001',
  receivableId: 10,
  receivableNo: 'AR-001',
  customerId: 100,
  customerName: '华东客户',
  receiptDate: '2026-07-10',
  amount: '100.00',
  method: 'BANK_TRANSFER',
  status: 'POSTED',
  remark: '收款',
  createdByName: '财务员',
  postedByName: '财务主管',
  postedAt: '2026-07-10T09:00:00+08:00',
  version: 3,
}
const draftReceipt: ReceiptSummaryRecord = {
  ...receipt,
  id: 2,
  receiptNo: 'RC-DRAFT',
  amount: '250.00',
  status: 'DRAFT',
  postedByName: undefined,
  postedAt: undefined,
}

describe('收款记录列表页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    masterDataApiMock.customers.list.mockResolvedValue(page([customer]))
    financeApiMock.receipts.list.mockResolvedValue(page([receipt]))
    financeApiMock.receipts.post.mockResolvedValue({ ...draftReceipt, status: 'POSTED' })
    financeApiMock.receipts.cancel.mockResolvedValue({ ...draftReceipt, status: 'CANCELLED' })
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
  })

  it('加载收款记录并支持筛选、空态和错误态', async () => {
    const { wrapper } = await mountFinanceView(ReceiptListView, ['finance:receipt:view'], '/finance/receipts')

    expect(wrapper.text()).toContain('收款记录')
    expect(wrapper.text()).toContain('RC-001')
    expect(wrapper.text()).toContain('AR-001')
    expect(wrapper.text()).toContain('已过账')

    await wrapper.find('input[name="receipt-keyword"]').setValue('RC')
    await setSelectValue(wrapper, 0, 100)
    await setSelectValue(wrapper, 1, 'POSTED')
    await wrapper.find('input[name="receipt-date-from"]').setValue('2026-07-01')
    await wrapper.find('input[name="receipt-date-to"]').setValue('2026-07-31')
    await wrapper.find('input[name="receipt-receivable-id"]').setValue('10')
    await wrapper.find('[data-test="search-receipts"]').trigger('click')
    await flushPromises()

    expect(financeApiMock.receipts.list).toHaveBeenLastCalledWith({
      keyword: 'RC',
      customerId: 100,
      status: 'POSTED',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-31',
      receivableId: '10',
      page: 1,
      pageSize: 10,
    })

    financeApiMock.receipts.list.mockResolvedValueOnce(page([]))
    await wrapper.find('[data-test="reset-receipts"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('暂无收款记录')

    financeApiMock.receipts.list.mockRejectedValueOnce(new Error('收款接口异常'))
    await wrapper.find('[data-test="search-receipts"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('收款接口异常')
  })

  it('只读权限不显示草稿编辑、过账和取消按钮', async () => {
    financeApiMock.receipts.list.mockResolvedValue(page([draftReceipt]))
    const { wrapper } = await mountFinanceView(ReceiptListView, ['finance:receipt:view'], '/finance/receipts')

    expect(wrapper.find('[data-test="edit-receipt"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="post-receipt"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="cancel-receipt"]').exists()).toBe(false)
  })

  it('切换分页时按第二页重新查询', async () => {
    financeApiMock.receipts.list.mockResolvedValue({
      items: [receipt],
      page: 1,
      pageSize: 10,
      total: 40,
      totalPages: 2,
    })
    const { wrapper } = await mountFinanceView(ReceiptListView, ['finance:receipt:view'], '/finance/receipts')

    wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 2)
    await flushPromises()

    expect(financeApiMock.receipts.list).toHaveBeenLastCalledWith({
      keyword: '',
      customerId: undefined,
      status: undefined,
      dateFrom: '',
      dateTo: '',
      receivableId: undefined,
      page: 2,
      pageSize: 10,
    })
  })

  it('草稿收款按权限显示编辑、过账和取消，并调用操作接口后刷新列表', async () => {
    financeApiMock.receipts.list.mockResolvedValue(page([draftReceipt]))
    const { wrapper, router } = await mountFinanceView(ReceiptListView, [
      'finance:receipt:view',
      'finance:receipt:update',
      'finance:receipt:post',
      'finance:receipt:cancel',
    ], '/finance/receipts', { attachTo: document.body })

    expect(wrapper.find('[data-test="edit-receipt"]').exists()).toBe(true)
    await openMoreActions(wrapper)
    expect(teleportedAction('post-receipt')).toBeTruthy()
    expect(teleportedAction('cancel-receipt')).toBeTruthy()

    await wrapper.find('[data-test="edit-receipt"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('finance-receipt-edit')
    expect(router.currentRoute.value.params.id).toBe('2')

    await router.push('/finance/receipts')
    await flushPromises()
    await clickTeleportedAction(wrapper, 'post-receipt')
    expect(confirmActionMock).toHaveBeenCalledWith('确认过账收款“RC-DRAFT”，金额 250.00？')
    expect(financeApiMock.receipts.post).toHaveBeenCalledWith(2)
    expect(financeApiMock.receipts.list).toHaveBeenCalledTimes(2)

    await clickTeleportedAction(wrapper, 'cancel-receipt')
    expect(confirmActionMock).toHaveBeenCalledWith('确认取消收款“RC-DRAFT”，金额 250.00？')
    expect(financeApiMock.receipts.cancel).toHaveBeenCalledWith(2)
    expect(financeApiMock.receipts.list).toHaveBeenCalledTimes(3)
  })
})
