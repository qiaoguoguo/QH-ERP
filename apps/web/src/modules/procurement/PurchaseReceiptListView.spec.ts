import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PageResult } from '../../shared/api/accountPermissionApi'
import type { PartnerRecord, WarehouseRecord } from '../../shared/api/masterDataApi'
import type { PurchaseReceiptSummaryRecord } from '../../shared/api/procurementApi'
import { useAuthStore } from '../../stores/authStore'
import PurchaseReceiptListView from './PurchaseReceiptListView.vue'

const procurementApiMock = vi.hoisted(() => ({
  receipts: {
    list: vi.fn(),
    post: vi.fn(),
  },
}))

const masterDataApiMock = vi.hoisted(() => ({
  suppliers: {
    list: vi.fn(),
  },
  warehouses: {
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

const warehouseA: WarehouseRecord = {
  id: 30,
  code: 'WH-RM',
  name: '原料仓',
  status: 'ENABLED',
}

const draftReceipt: PurchaseReceiptSummaryRecord = {
  id: 700,
  receiptNo: 'PR-20260705-001',
  orderId: 99,
  orderNo: 'PO-20260704-001',
  procurementMode: 'PROJECT',
  projectId: 30,
  projectCode: 'PRJ-024',
  projectName: '华东产线改造',
  supplierId: 100,
  supplierName: '华东五金',
  warehouseId: 30,
  warehouseName: '原料仓',
  businessDate: '2026-07-05',
  status: 'DRAFT',
  lineCount: 1,
  totalQuantity: '2.500000',
  valuationState: 'VALUED',
  valuationStateName: '已估值',
  costVisible: false,
  taxExcludedAmount: null,
  allowedActions: ['UPDATE', 'POST'],
  version: 21,
  remark: '首批入库',
  createdByName: '仓管员',
  createdAt: '2026-07-05T08:00:00+08:00',
  updatedAt: '2026-07-05T08:30:00+08:00',
  postedByName: null,
  postedAt: null,
}

const postedReceipt: PurchaseReceiptSummaryRecord = {
  ...draftReceipt,
  id: 701,
  receiptNo: 'PR-20260705-002',
  status: 'POSTED',
  procurementMode: 'PUBLIC',
  projectId: null,
  projectCode: null,
  projectName: null,
  totalQuantity: '5.000000',
  valuationStateName: '已过账估值',
  costVisible: true,
  taxExcludedAmount: '500.000000',
  allowedActions: [],
  version: 22,
  postedByName: '仓管员',
  postedAt: '2026-07-05T09:00:00+08:00',
}

const receiptPage: PageResult<PurchaseReceiptSummaryRecord> = {
  items: [draftReceipt, postedReceipt],
  page: 1,
  pageSize: 10,
  total: 2,
  totalPages: 1,
}

const emptyReceiptPage: PageResult<PurchaseReceiptSummaryRecord> = {
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
  'procurement:receipt:view',
  'procurement:receipt:update',
  'procurement:receipt:post',
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
      { path: '/procurement/receipts', name: 'procurement-receipts', component: PurchaseReceiptListView },
      { path: '/procurement/receipts/:id', name: 'procurement-receipt-detail', component: { render: () => null } },
      { path: '/procurement/receipts/:id/edit', name: 'procurement-receipt-edit', component: { render: () => null } },
    ],
  })
  await router.push('/procurement/receipts')
  await router.isReady()
  const wrapper = mount(PurchaseReceiptListView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('采购入库列表页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    procurementApiMock.receipts.list.mockResolvedValue(receiptPage)
    procurementApiMock.receipts.post.mockResolvedValue(postedReceipt)
    masterDataApiMock.suppliers.list.mockResolvedValue({
      items: [supplierA],
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

  it('初始加载供应商、仓库和采购入库并展示关键字段', async () => {
    const { wrapper } = await mountList()

    expect(masterDataApiMock.suppliers.list).toHaveBeenCalledWith({
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
    expect(procurementApiMock.receipts.list).toHaveBeenCalledWith({
      keyword: '',
      supplierId: undefined,
      warehouseId: undefined,
      status: undefined,
      dateFrom: '',
      dateTo: '',
      orderId: undefined,
      page: 1,
      pageSize: 10,
    })
    expect(wrapper.text()).toContain('采购入库')
    expect(wrapper.text()).toContain('PR-20260705-001')
    expect(wrapper.text()).toContain('PO-20260704-001')
    expect(wrapper.text()).toContain('项目专采 · PRJ-024/华东产线改造')
    expect(wrapper.text()).toContain('公共采购')
    expect(wrapper.text()).toContain('估值状态：已估值')
    expect(wrapper.text()).toContain('成本无权限')
    expect(wrapper.text()).toContain('未税金额 500')
    expect(wrapper.text()).toContain('华东五金')
    expect(wrapper.text()).toContain('原料仓')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.text()).toContain('已过账')
    expect(wrapper.text()).not.toContain('POSTED')
    expect(wrapper.text()).not.toContain('DRAFT')
  })

  it('支持按关键词、供应商、仓库、状态、业务日期和来源订单筛选并重置', async () => {
    const { wrapper } = await mountList()

    await wrapper.find('input[name="purchase-receipt-keyword"]').setValue('PR')
    await setSelectValue(wrapper, 0, 100)
    await setSelectValue(wrapper, 1, 30)
    await setSelectValue(wrapper, 2, 'DRAFT')
    await wrapper.find('input[name="purchase-receipt-date-from"]').setValue('2026-07-01')
    await wrapper.find('input[name="purchase-receipt-date-to"]').setValue('2026-07-31')
    await wrapper.find('input[name="purchase-receipt-order-id"]').setValue('99')
    await wrapper.find('[data-test="search-purchase-receipts"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.receipts.list).toHaveBeenLastCalledWith({
      keyword: 'PR',
      supplierId: 100,
      warehouseId: 30,
      status: 'DRAFT',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-31',
      orderId: 99,
      page: 1,
      pageSize: 10,
    })

    await wrapper.find('[data-test="reset-purchase-receipts"]').trigger('click')
    await flushPromises()
    expect(procurementApiMock.receipts.list).toHaveBeenLastCalledWith({
      keyword: '',
      supplierId: undefined,
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
    procurementApiMock.receipts.list.mockResolvedValueOnce(emptyReceiptPage)
    const { wrapper } = await mountList()

    expect(wrapper.text()).toContain('暂无采购入库')

    procurementApiMock.receipts.list.mockRejectedValueOnce(new Error('采购入库接口异常'))
    await wrapper.find('[data-test="search-purchase-receipts"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('采购入库接口异常')
    expect(wrapper.text()).toContain('暂无采购入库')
  })

  it('按权限和状态展示采购入库行操作', async () => {
    const { wrapper, router } = await mountList()

    expect(buttonsByText(wrapper, '详情')).toHaveLength(2)
    expect(buttonsByText(wrapper, '编辑')).toHaveLength(1)
    expect(buttonsByText(wrapper, '过账')).toHaveLength(1)

    await wrapper.find('[data-test="view-purchase-receipt"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('procurement-receipt-detail')
    expect(router.currentRoute.value.params.id).toBe('700')
  })

  it('只读权限仅展示详情入口', async () => {
    const { wrapper } = await mountList(['procurement:receipt:view'])

    expect(buttonsByText(wrapper, '详情')).toHaveLength(2)
    expect(buttonsByText(wrapper, '编辑')).toHaveLength(0)
    expect(buttonsByText(wrapper, '过账')).toHaveLength(0)
  })

  it('状态允许但 allowedActions 未授权时不展示编辑和过账动作', async () => {
    procurementApiMock.receipts.list.mockResolvedValueOnce({
      ...receiptPage,
      items: [{ ...draftReceipt, allowedActions: [] }],
      total: 1,
    })
    const { wrapper } = await mountList()

    expect(buttonsByText(wrapper, '详情')).toHaveLength(1)
    expect(buttonsByText(wrapper, '编辑')).toHaveLength(0)
    expect(buttonsByText(wrapper, '过账')).toHaveLength(0)
  })

  it('过账动作成功后刷新列表，失败时显示错误', async () => {
    const { wrapper } = await mountList()

    await wrapper.find('[data-test="post-purchase-receipt"]').trigger('click')
    await flushPromises()
    expect(procurementApiMock.receipts.post).toHaveBeenCalledWith(700, expect.objectContaining({
      version: 21,
      idempotencyKey: expect.any(String),
    }))
    expect(procurementApiMock.receipts.post.mock.calls[0][1].idempotencyKey).not.toHaveLength(0)
    expect(procurementApiMock.receipts.list).toHaveBeenCalledTimes(2)

    procurementApiMock.receipts.post.mockRejectedValueOnce(new Error('采购入库已过账，不能重复过账'))
    await wrapper.find('[data-test="post-purchase-receipt"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('采购入库已过账，不能重复过账')
    expect(procurementApiMock.receipts.list).toHaveBeenCalledTimes(3)
  })
})
