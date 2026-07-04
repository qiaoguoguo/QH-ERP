import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PageResult } from '../../shared/api/accountPermissionApi'
import type { PartnerRecord } from '../../shared/api/masterDataApi'
import type { SalesOrderSummaryRecord } from '../../shared/api/salesApi'
import { useAuthStore } from '../../stores/authStore'
import SalesOrderListView from './SalesOrderListView.vue'

const salesApiMock = vi.hoisted(() => ({
  orders: {
    list: vi.fn(),
    confirm: vi.fn(),
    cancel: vi.fn(),
    close: vi.fn(),
  },
}))

const masterDataApiMock = vi.hoisted(() => ({
  customers: {
    list: vi.fn(),
  },
}))

vi.mock('../../shared/api/salesApi', () => ({
  salesApi: salesApiMock,
}))

vi.mock('../../shared/api/masterDataApi', () => ({
  masterDataApi: masterDataApiMock,
}))

const customerA: PartnerRecord = {
  id: 100,
  code: 'CUS-A',
  name: '华东客户',
  status: 'ENABLED',
}

const draftOrder: SalesOrderSummaryRecord = {
  id: 1,
  orderNo: 'SO-DRAFT-001',
  customerId: 100,
  customerCode: 'CUS-A',
  customerName: '华东客户',
  orderDate: '2026-07-04',
  expectedShipDate: '2026-07-10',
  status: 'DRAFT',
  lineCount: 2,
  totalQuantity: 100,
  shippedQuantity: 0,
  remainingQuantity: 100,
  remark: '首批销售',
  createdByName: '销售员',
  createdAt: '2026-07-04T08:00:00+08:00',
  updatedAt: '2026-07-04T09:00:00+08:00',
}

const confirmedOrder: SalesOrderSummaryRecord = {
  ...draftOrder,
  id: 2,
  orderNo: 'SO-CONF-001',
  status: 'CONFIRMED',
  totalQuantity: 50,
  shippedQuantity: 0,
  remainingQuantity: 50,
}

const partialOrder: SalesOrderSummaryRecord = {
  ...draftOrder,
  id: 3,
  orderNo: 'SO-PART-001',
  status: 'PARTIALLY_SHIPPED',
  totalQuantity: 80,
  shippedQuantity: 30,
  remainingQuantity: 50,
}

const shippedOrder: SalesOrderSummaryRecord = {
  ...draftOrder,
  id: 4,
  orderNo: 'SO-SHIP-001',
  status: 'SHIPPED',
  totalQuantity: 40,
  shippedQuantity: 40,
  remainingQuantity: 0,
}

const orderPage: PageResult<SalesOrderSummaryRecord> = {
  items: [draftOrder, confirmedOrder, partialOrder, shippedOrder],
  page: 1,
  pageSize: 20,
  total: 4,
  totalPages: 1,
}

const emptyOrderPage: PageResult<SalesOrderSummaryRecord> = {
  items: [],
  page: 1,
  pageSize: 20,
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
  'sales:order:view',
  'sales:order:create',
  'sales:order:update',
  'sales:order:confirm',
  'sales:order:cancel',
  'sales:order:close',
  'sales:shipment:create',
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
      { path: '/sales/orders', name: 'sales-orders', component: SalesOrderListView },
      { path: '/sales/orders/create', name: 'sales-order-create', component: { render: () => null } },
      { path: '/sales/orders/:id', name: 'sales-order-detail', component: { render: () => null } },
      { path: '/sales/orders/:id/edit', name: 'sales-order-edit', component: { render: () => null } },
      {
        path: '/sales/orders/:orderId/shipments/create',
        name: 'sales-shipment-create',
        component: { render: () => null },
      },
    ],
  })
  await router.push('/sales/orders')
  await router.isReady()
  const wrapper = mount(SalesOrderListView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('销售订单列表页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('confirm', vi.fn(() => true))
    salesApiMock.orders.list.mockResolvedValue(orderPage)
    salesApiMock.orders.confirm.mockResolvedValue({ ...draftOrder, status: 'CONFIRMED' })
    salesApiMock.orders.cancel.mockResolvedValue({ ...draftOrder, status: 'CANCELLED' })
    salesApiMock.orders.close.mockResolvedValue({ ...confirmedOrder, status: 'CLOSED' })
    masterDataApiMock.customers.list.mockResolvedValue({
      items: [customerA],
      page: 1,
      pageSize: 200,
      total: 1,
      totalPages: 1,
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('初始加载客户和销售订单并展示关键字段', async () => {
    const { wrapper } = await mountList()

    expect(masterDataApiMock.customers.list).toHaveBeenCalledWith({
      keyword: '',
      status: 'ENABLED',
      page: 1,
      pageSize: 200,
    })
    expect(salesApiMock.orders.list).toHaveBeenCalledWith({
      keyword: '',
      customerId: undefined,
      status: undefined,
      dateFrom: '',
      dateTo: '',
      expectedDateFrom: '',
      expectedDateTo: '',
      page: 1,
      pageSize: 20,
    })
    expect(wrapper.text()).toContain('SO-DRAFT-001')
    expect(wrapper.text()).toContain('华东客户')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.text()).toContain('100')
    expect(wrapper.text()).toContain('销售员')
    expect(wrapper.find('[data-test="create-sales-order"]').exists()).toBe(true)
  })

  it('支持按关键词、客户、状态和日期范围筛选并重置', async () => {
    const { wrapper } = await mountList()

    await wrapper.find('input[name="sales-order-keyword"]').setValue('SO')
    await setSelectValue(wrapper, 0, 100)
    await setSelectValue(wrapper, 1, 'CONFIRMED')
    await wrapper.find('input[name="sales-order-date-from"]').setValue('2026-07-01')
    await wrapper.find('input[name="sales-order-date-to"]').setValue('2026-07-31')
    await wrapper.find('input[name="sales-order-expected-date-from"]').setValue('2026-07-08')
    await wrapper.find('input[name="sales-order-expected-date-to"]').setValue('2026-07-20')
    await wrapper.find('[data-test="search-sales-orders"]').trigger('click')
    await flushPromises()

    expect(salesApiMock.orders.list).toHaveBeenLastCalledWith({
      keyword: 'SO',
      customerId: 100,
      status: 'CONFIRMED',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-31',
      expectedDateFrom: '2026-07-08',
      expectedDateTo: '2026-07-20',
      page: 1,
      pageSize: 20,
    })

    await wrapper.find('[data-test="reset-sales-orders"]').trigger('click')
    await flushPromises()
    expect(salesApiMock.orders.list).toHaveBeenLastCalledWith({
      keyword: '',
      customerId: undefined,
      status: undefined,
      dateFrom: '',
      dateTo: '',
      expectedDateFrom: '',
      expectedDateTo: '',
      page: 1,
      pageSize: 20,
    })
  })

  it('无数据和加载失败时显示明确状态', async () => {
    salesApiMock.orders.list.mockResolvedValueOnce(emptyOrderPage)
    const { wrapper } = await mountList()

    expect(wrapper.text()).toContain('暂无销售订单')

    salesApiMock.orders.list.mockRejectedValueOnce(new Error('销售订单接口异常'))
    await wrapper.find('[data-test="search-sales-orders"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('销售订单接口异常')
    expect(wrapper.text()).toContain('暂无销售订单')
  })

  it('按权限和状态展示销售订单行操作', async () => {
    const { wrapper, router } = await mountList()

    expect(buttonsByText(wrapper, '详情')).toHaveLength(4)
    expect(buttonsByText(wrapper, '编辑')).toHaveLength(1)
    expect(buttonsByText(wrapper, '确认')).toHaveLength(1)
    expect(buttonsByText(wrapper, '取消')).toHaveLength(2)
    expect(buttonsByText(wrapper, '关闭')).toHaveLength(3)
    expect(buttonsByText(wrapper, '创建出库')).toHaveLength(2)

    await wrapper.find('[data-test="create-sales-shipment"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('sales-shipment-create')
    expect(router.currentRoute.value.params.orderId).toBe('2')
  })

  it('只读权限仅展示详情入口', async () => {
    const { wrapper } = await mountList(['sales:order:view'])

    expect(wrapper.find('[data-test="create-sales-order"]').exists()).toBe(false)
    expect(buttonsByText(wrapper, '详情')).toHaveLength(4)
    expect(buttonsByText(wrapper, '编辑')).toHaveLength(0)
    expect(buttonsByText(wrapper, '确认')).toHaveLength(0)
    expect(buttonsByText(wrapper, '取消')).toHaveLength(0)
    expect(buttonsByText(wrapper, '关闭')).toHaveLength(0)
    expect(buttonsByText(wrapper, '创建出库')).toHaveLength(0)
  })

  it('确认、取消和关闭动作成功后刷新列表，失败时显示错误', async () => {
    const { wrapper } = await mountList()

    await wrapper.find('[data-test="confirm-sales-order"]').trigger('click')
    await flushPromises()
    expect(salesApiMock.orders.confirm).toHaveBeenCalledWith(1)

    await wrapper.find('[data-test="cancel-sales-order"]').trigger('click')
    await flushPromises()
    expect(salesApiMock.orders.cancel).toHaveBeenCalledWith(1)

    await wrapper.find('[data-test="close-sales-order"]').trigger('click')
    await flushPromises()
    expect(salesApiMock.orders.close).toHaveBeenCalledWith(2)

    salesApiMock.orders.close.mockRejectedValueOnce(new Error('销售订单状态不允许关闭'))
    await wrapper.find('[data-test="close-sales-order"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('销售订单状态不允许关闭')
    expect(salesApiMock.orders.list).toHaveBeenCalledTimes(4)
  })
})
