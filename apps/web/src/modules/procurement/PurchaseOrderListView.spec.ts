import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PageResult } from '../../shared/api/accountPermissionApi'
import type { PartnerRecord } from '../../shared/api/masterDataApi'
import type { PurchaseOrderSummaryRecord } from '../../shared/api/procurementApi'
import { useAuthStore } from '../../stores/authStore'
import PurchaseOrderListView from './PurchaseOrderListView.vue'

const procurementApiMock = vi.hoisted(() => ({
  orders: {
    list: vi.fn(),
    confirm: vi.fn(),
    cancel: vi.fn(),
    close: vi.fn(),
  },
}))

const masterDataApiMock = vi.hoisted(() => ({
  suppliers: {
    list: vi.fn(),
  },
}))

vi.mock('../../shared/api/procurementApi', () => ({
  procurementApi: procurementApiMock,
}))

vi.mock('../../shared/api/masterDataApi', () => ({
  masterDataApi: masterDataApiMock,
}))

const supplierA: PartnerRecord = {
  id: 100,
  code: 'SUP-A',
  name: '华东五金',
  status: 'ENABLED',
}

const draftOrder: PurchaseOrderSummaryRecord = {
  id: 1,
  orderNo: 'PO-DRAFT-001',
  supplierId: 100,
  supplierCode: 'SUP-A',
  supplierName: '华东五金',
  orderDate: '2026-07-04',
  expectedArrivalDate: '2026-07-10',
  status: 'DRAFT',
  lineCount: 2,
  totalQuantity: 100,
  receivedQuantity: 0,
  remainingQuantity: 100,
  remark: '首批采购',
  createdByName: '采购员',
  createdAt: '2026-07-04T08:00:00+08:00',
  updatedAt: '2026-07-04T09:00:00+08:00',
}

const confirmedOrder: PurchaseOrderSummaryRecord = {
  ...draftOrder,
  id: 2,
  orderNo: 'PO-CONF-001',
  status: 'CONFIRMED',
  totalQuantity: 50,
  receivedQuantity: 0,
  remainingQuantity: 50,
}

const partialOrder: PurchaseOrderSummaryRecord = {
  ...draftOrder,
  id: 3,
  orderNo: 'PO-PART-001',
  status: 'PARTIALLY_RECEIVED',
  totalQuantity: 80,
  receivedQuantity: 30,
  remainingQuantity: 50,
}

const receivedOrder: PurchaseOrderSummaryRecord = {
  ...draftOrder,
  id: 4,
  orderNo: 'PO-RECV-001',
  status: 'RECEIVED',
  totalQuantity: 40,
  receivedQuantity: 40,
  remainingQuantity: 0,
}

const orderPage: PageResult<PurchaseOrderSummaryRecord> = {
  items: [draftOrder, confirmedOrder, partialOrder, receivedOrder],
  page: 1,
  pageSize: 10,
  total: 4,
  totalPages: 1,
}

const emptyOrderPage: PageResult<PurchaseOrderSummaryRecord> = {
  items: [],
  page: 1,
  pageSize: 10,
  total: 0,
  totalPages: 0,
}

function buttonsByText(wrapper: VueWrapper, text: string): VueWrapper[] {
  return wrapper.findAllComponents({ name: 'ElButton' }).filter((button) => button.text().trim() === text)
}

async function setSelectValue(wrapper: VueWrapper, index: number, value: unknown) {
  const select = wrapper.findAllComponents({ name: 'ElSelect' })[index] as VueWrapper | undefined
  expect(select?.exists()).toBe(true)
  select?.vm.$emit('update:modelValue', value)
  await flushPromises()
}

async function mountList(permissions = [
  'procurement:order:view',
  'procurement:order:create',
  'procurement:order:update',
  'procurement:order:confirm',
  'procurement:order:cancel',
  'procurement:order:close',
  'procurement:receipt:create',
]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/procurement/orders', name: 'procurement-orders', component: PurchaseOrderListView },
      { path: '/procurement/orders/create', name: 'procurement-order-create', component: { render: () => null } },
      { path: '/procurement/orders/:id', name: 'procurement-order-detail', component: { render: () => null } },
      { path: '/procurement/orders/:id/edit', name: 'procurement-order-edit', component: { render: () => null } },
      {
        path: '/procurement/orders/:orderId/receipts/create',
        name: 'procurement-receipt-create',
        component: { render: () => null },
      },
    ],
  })
  await router.push('/procurement/orders')
  await router.isReady()
  const wrapper = mount(PurchaseOrderListView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('采购订单列表页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    procurementApiMock.orders.list.mockResolvedValue(orderPage)
    procurementApiMock.orders.confirm.mockResolvedValue({ ...draftOrder, status: 'CONFIRMED' })
    procurementApiMock.orders.cancel.mockResolvedValue({ ...draftOrder, status: 'CANCELLED' })
    procurementApiMock.orders.close.mockResolvedValue({ ...confirmedOrder, status: 'CLOSED' })
    masterDataApiMock.suppliers.list.mockResolvedValue({
      items: [supplierA],
      page: 1,
      pageSize: 200,
      total: 1,
      totalPages: 1,
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('初始加载供应商和采购订单并展示关键字段', async () => {
    const { wrapper } = await mountList()

    expect(masterDataApiMock.suppliers.list).toHaveBeenCalledWith({
      keyword: '',
      status: 'ENABLED',
      page: 1,
      pageSize: 200,
    })
    expect(procurementApiMock.orders.list).toHaveBeenCalledWith({
      keyword: '',
      supplierId: undefined,
      status: undefined,
      dateFrom: '',
      dateTo: '',
      expectedDateFrom: '',
      expectedDateTo: '',
      page: 1,
      pageSize: 10,
    })
    expect(wrapper.text()).toContain('PO-DRAFT-001')
    expect(wrapper.text()).toContain('华东五金')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.text()).toContain('100')
    expect(wrapper.text()).toContain('采购员')
    expect(wrapper.find('[data-test="create-purchase-order"]').exists()).toBe(true)
  })

  it('支持按关键词、供应商、状态和日期范围筛选并重置', async () => {
    const { wrapper } = await mountList()

    await wrapper.find('input[name="purchase-order-keyword"]').setValue('PO')
    await setSelectValue(wrapper, 0, 100)
    await setSelectValue(wrapper, 1, 'CONFIRMED')
    await wrapper.find('input[name="purchase-order-date-from"]').setValue('2026-07-01')
    await wrapper.find('input[name="purchase-order-date-to"]').setValue('2026-07-31')
    await wrapper.find('input[name="purchase-order-expected-date-from"]').setValue('2026-07-08')
    await wrapper.find('input[name="purchase-order-expected-date-to"]').setValue('2026-07-20')
    await wrapper.find('[data-test="search-purchase-orders"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.orders.list).toHaveBeenLastCalledWith({
      keyword: 'PO',
      supplierId: 100,
      status: 'CONFIRMED',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-31',
      expectedDateFrom: '2026-07-08',
      expectedDateTo: '2026-07-20',
      page: 1,
      pageSize: 10,
    })

    await wrapper.find('[data-test="reset-purchase-orders"]').trigger('click')
    await flushPromises()
    expect(procurementApiMock.orders.list).toHaveBeenLastCalledWith({
      keyword: '',
      supplierId: undefined,
      status: undefined,
      dateFrom: '',
      dateTo: '',
      expectedDateFrom: '',
      expectedDateTo: '',
      page: 1,
      pageSize: 10,
    })
  })

  it('无数据和加载失败时显示明确状态', async () => {
    procurementApiMock.orders.list.mockResolvedValueOnce(emptyOrderPage)
    const { wrapper } = await mountList()

    expect(wrapper.text()).toContain('暂无采购订单')

    procurementApiMock.orders.list.mockRejectedValueOnce(new Error('采购订单接口异常'))
    await wrapper.find('[data-test="search-purchase-orders"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('采购订单接口异常')
    expect(wrapper.text()).toContain('暂无采购订单')
  })

  it('按权限和状态展示采购订单行操作', async () => {
    const { wrapper, router } = await mountList()

    expect(buttonsByText(wrapper, '详情')).toHaveLength(4)
    expect(buttonsByText(wrapper, '编辑')).toHaveLength(1)
    expect(buttonsByText(wrapper, '确认')).toHaveLength(1)
    expect(buttonsByText(wrapper, '取消')).toHaveLength(2)
    expect(buttonsByText(wrapper, '关闭')).toHaveLength(3)
    expect(buttonsByText(wrapper, '创建入库')).toHaveLength(2)

    await wrapper.find('[data-test="create-purchase-receipt"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('procurement-receipt-create')
    expect(router.currentRoute.value.params.orderId).toBe('2')
  })

  it('只读权限仅展示详情入口', async () => {
    const { wrapper } = await mountList(['procurement:order:view'])

    expect(wrapper.find('[data-test="create-purchase-order"]').exists()).toBe(false)
    expect(buttonsByText(wrapper, '详情')).toHaveLength(4)
    expect(buttonsByText(wrapper, '编辑')).toHaveLength(0)
    expect(buttonsByText(wrapper, '确认')).toHaveLength(0)
    expect(buttonsByText(wrapper, '取消')).toHaveLength(0)
    expect(buttonsByText(wrapper, '关闭')).toHaveLength(0)
    expect(buttonsByText(wrapper, '创建入库')).toHaveLength(0)
  })

  it('确认、取消和关闭动作成功后刷新列表，失败时显示错误', async () => {
    const { wrapper } = await mountList()

    await wrapper.find('[data-test="confirm-purchase-order"]').trigger('click')
    await flushPromises()
    expect(procurementApiMock.orders.confirm).toHaveBeenCalledWith(1)

    await wrapper.find('[data-test="cancel-purchase-order"]').trigger('click')
    await flushPromises()
    expect(procurementApiMock.orders.cancel).toHaveBeenCalledWith(1)

    await wrapper.find('[data-test="close-purchase-order"]').trigger('click')
    await flushPromises()
    expect(procurementApiMock.orders.close).toHaveBeenCalledWith(2)

    procurementApiMock.orders.close.mockRejectedValueOnce(new Error('采购订单状态不允许关闭'))
    await wrapper.find('[data-test="close-purchase-order"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('采购订单状态不允许关闭')
    expect(procurementApiMock.orders.list).toHaveBeenCalledTimes(4)
  })
})
