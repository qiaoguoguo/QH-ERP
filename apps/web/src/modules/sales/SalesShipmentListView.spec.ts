import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PageResult } from '../../shared/api/accountPermissionApi'
import type { PartnerRecord, WarehouseRecord } from '../../shared/api/masterDataApi'
import type { SalesShipmentSummaryRecord } from '../../shared/api/salesApi'
import { useConfirmActionMock } from '../../test/setup'
import { useAuthStore } from '../../stores/authStore'
import SalesShipmentListView from './SalesShipmentListView.vue'

const confirmActionMock = useConfirmActionMock()

const salesApiMock = vi.hoisted(() => ({
  shipments: {
    list: vi.fn(),
    post: vi.fn(),
  },
}))

const masterDataApiMock = vi.hoisted(() => ({
  customers: {
    list: vi.fn(),
  },
  warehouses: {
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

const warehouseA: WarehouseRecord = {
  id: 30,
  code: 'WH-FG',
  name: '成品仓',
  status: 'ENABLED',
}

const draftShipment: SalesShipmentSummaryRecord = {
  id: 700,
  shipmentNo: 'SS-20260705-001',
  orderId: 99,
  orderNo: 'SO-20260704-001',
  customerId: 100,
  customerName: '华东客户',
  warehouseId: 30,
  warehouseName: '成品仓',
  businessDate: '2026-07-05',
  status: 'DRAFT',
  lineCount: 1,
  totalQuantity: 2.5,
  allowedActions: ['UPDATE', 'POST'],
  actionDisabledReason: null,
  version: 5,
  remark: '首批销售出库',
  createdByName: '仓管员',
  createdAt: '2026-07-05T08:00:00+08:00',
  updatedAt: '2026-07-05T08:30:00+08:00',
  postedByName: null,
  postedAt: null,
}

const postedShipment: SalesShipmentSummaryRecord = {
  ...draftShipment,
  id: 701,
  shipmentNo: 'SS-20260705-002',
  status: 'POSTED',
  totalQuantity: 5,
  allowedActions: [],
  postedByName: '仓管员',
  postedAt: '2026-07-05T09:00:00+08:00',
}

const shipmentPage: PageResult<SalesShipmentSummaryRecord> = {
  items: [draftShipment, postedShipment],
  page: 1,
  pageSize: 10,
  total: 2,
  totalPages: 1,
}

const emptyShipmentPage: PageResult<SalesShipmentSummaryRecord> = {
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
  'sales:shipment:view',
  'sales:shipment:update',
  'sales:shipment:post',
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
      { path: '/sales/shipments', name: 'sales-shipments', component: SalesShipmentListView },
      { path: '/sales/shipments/:id', name: 'sales-shipment-detail', component: { render: () => null } },
      { path: '/sales/shipments/:id/edit', name: 'sales-shipment-edit', component: { render: () => null } },
    ],
  })
  await router.push('/sales/shipments')
  await router.isReady()
  const wrapper = mount(SalesShipmentListView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('销售出库列表页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    salesApiMock.shipments.list.mockResolvedValue(shipmentPage)
    salesApiMock.shipments.post.mockResolvedValue(postedShipment)
    masterDataApiMock.customers.list.mockResolvedValue({
      items: [customerA],
      page: 1,
      pageSize: 200,
      total: 1,
      totalPages: 1,
    })
    masterDataApiMock.warehouses.list.mockResolvedValue({
      items: [warehouseA],
      page: 1,
      pageSize: 200,
      total: 1,
      totalPages: 1,
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('初始加载客户、仓库和销售出库并展示关键字段', async () => {
    const { wrapper } = await mountList()

    expect(masterDataApiMock.customers.list).toHaveBeenCalledWith({
      keyword: '',
      status: 'ENABLED',
      page: 1,
      pageSize: 200,
    })
    expect(masterDataApiMock.warehouses.list).toHaveBeenCalledWith({
      keyword: '',
      status: 'ENABLED',
      page: 1,
      pageSize: 200,
    })
    expect(salesApiMock.shipments.list).toHaveBeenCalledWith({
      keyword: '',
      customerId: undefined,
      warehouseId: undefined,
      status: undefined,
      dateFrom: '',
      dateTo: '',
      orderId: undefined,
      page: 1,
      pageSize: 10,
    })
    expect(wrapper.text()).toContain('销售出库')
    expect(wrapper.text()).toContain('SS-20260705-001')
    expect(wrapper.text()).toContain('SO-20260704-001')
    expect(wrapper.text()).toContain('华东客户')
    expect(wrapper.text()).toContain('成品仓')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.text()).toContain('已过账')
    expect(wrapper.text()).not.toContain('POSTED')
    expect(wrapper.text()).not.toContain('DRAFT')
  })

  it('支持按关键词、客户、仓库、状态、业务日期和来源订单筛选并重置', async () => {
    const { wrapper } = await mountList()

    await wrapper.find('input[name="sales-shipment-keyword"]').setValue('SS')
    await setSelectValue(wrapper, 0, 100)
    await setSelectValue(wrapper, 1, 30)
    await setSelectValue(wrapper, 2, 'DRAFT')
    await wrapper.find('input[name="sales-shipment-date-from"]').setValue('2026-07-01')
    await wrapper.find('input[name="sales-shipment-date-to"]').setValue('2026-07-31')
    await wrapper.find('input[name="sales-shipment-order-id"]').setValue('99')
    await wrapper.find('[data-test="search-sales-shipments"]').trigger('click')
    await flushPromises()

    expect(salesApiMock.shipments.list).toHaveBeenLastCalledWith({
      keyword: 'SS',
      customerId: 100,
      warehouseId: 30,
      status: 'DRAFT',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-31',
      orderId: 99,
      page: 1,
      pageSize: 10,
    })

    await wrapper.find('[data-test="reset-sales-shipments"]').trigger('click')
    await flushPromises()
    expect(salesApiMock.shipments.list).toHaveBeenLastCalledWith({
      keyword: '',
      customerId: undefined,
      warehouseId: undefined,
      status: undefined,
      dateFrom: '',
      dateTo: '',
      orderId: undefined,
      page: 1,
      pageSize: 10,
    })
  })

  it('无数据和加载失败时显示明确状态', async () => {
    salesApiMock.shipments.list.mockResolvedValueOnce(emptyShipmentPage)
    const { wrapper } = await mountList()

    expect(wrapper.text()).toContain('暂无销售出库')

    salesApiMock.shipments.list.mockRejectedValueOnce(new Error('销售出库接口异常'))
    await wrapper.find('[data-test="search-sales-shipments"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('销售出库接口异常')
    expect(wrapper.text()).toContain('暂无销售出库')
  })

  it('按权限和状态展示销售出库行操作', async () => {
    const { wrapper, router } = await mountList()

    expect(buttonsByText(wrapper, '详情')).toHaveLength(2)
    expect(buttonsByText(wrapper, '编辑')).toHaveLength(1)
    expect(buttonsByText(wrapper, '过账')).toHaveLength(1)

    await wrapper.find('[data-test="view-sales-shipment"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('sales-shipment-detail')
    expect(router.currentRoute.value.params.id).toBe('700')
  })

  it('只读权限仅展示详情入口', async () => {
    const { wrapper } = await mountList(['sales:shipment:view'])

    expect(buttonsByText(wrapper, '详情')).toHaveLength(2)
    expect(buttonsByText(wrapper, '编辑')).toHaveLength(0)
    expect(buttonsByText(wrapper, '过账')).toHaveLength(0)
  })

  it('列表过账入口进入详情处理提前交付原因，不直接调用无原因过账', async () => {
    const { wrapper, router } = await mountList()

    await wrapper.find('[data-test="post-sales-shipment"]').trigger('click')
    await flushPromises()

    expect(confirmActionMock).not.toHaveBeenCalled()
    expect(salesApiMock.shipments.post).not.toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('sales-shipment-detail')
    expect(router.currentRoute.value.params.id).toBe('700')
  })
})
