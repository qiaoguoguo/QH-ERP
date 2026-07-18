import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Component } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { AccountPermissionApiError } from '../../../shared/api/accountPermissionApi'
import type {
  OutsourcingDocumentDetailRecord,
  OutsourcingOrderDetailRecord,
  OutsourcingOrderSummaryRecord,
} from '../../../shared/api/productionOutsourcingApi'
import { useAuthStore } from '../../../stores/authStore'
import ProductionOutsourcingIssueView from './ProductionOutsourcingIssueView.vue'
import ProductionOutsourcingOrderDetailView from './ProductionOutsourcingOrderDetailView.vue'
import ProductionOutsourcingOrderFormView from './ProductionOutsourcingOrderFormView.vue'
import ProductionOutsourcingOrderListView from './ProductionOutsourcingOrderListView.vue'
import ProductionOutsourcingReceiptView from './ProductionOutsourcingReceiptView.vue'

const outsourcingApiMock = vi.hoisted(() => ({
  orders: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    release: vi.fn(),
    close: vi.fn(),
    cancel: vi.fn(),
  },
  materialIssues: {
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    post: vi.fn(),
    cancel: vi.fn(),
  },
  receipts: {
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    post: vi.fn(),
    cancel: vi.fn(),
  },
}))

const salesProjectApiMock = vi.hoisted(() => ({
  projects: { list: vi.fn() },
}))

const masterDataApiMock = vi.hoisted(() => ({
  suppliers: { list: vi.fn() },
  materials: { list: vi.fn() },
  warehouses: { list: vi.fn() },
}))

const bomApiMock = vi.hoisted(() => ({
  list: vi.fn(),
}))

vi.mock('../../../shared/api/productionOutsourcingApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../shared/api/productionOutsourcingApi')>()),
  productionOutsourcingApi: outsourcingApiMock,
}))

vi.mock('../../../shared/api/salesProjectApi', () => ({
  salesProjectApi: salesProjectApiMock,
}))

vi.mock('../../../shared/api/masterDataApi', () => ({
  masterDataApi: masterDataApiMock,
}))

vi.mock('../../../shared/api/bomApi', () => ({
  bomApi: bomApiMock,
}))

vi.mock('../../../shared/api/documentPlatformApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../shared/api/documentPlatformApi')>()),
  createIdempotencyKey: () => 'outsourcing-key',
}))

vi.mock('../../../shared/ui/confirmDialog', () => ({
  confirmAction: vi.fn().mockResolvedValue(true),
}))

const outsourcingOrder: OutsourcingOrderDetailRecord = {
  id: 77,
  orderNo: 'OS-027-001',
  ownershipType: 'PROJECT',
  projectId: 20,
  projectNo: 'PRJ-027',
  projectName: '项目生产闭环',
  supplierId: 30,
  supplierCode: 'SUP-027',
  supplierName: '外协供应商',
  productMaterialId: 40,
  productMaterialCode: 'SF-027',
  productMaterialName: '外协半成品',
  unitName: '件',
  bomId: 50,
  bomCode: 'BOM-SF-027',
  bomVersionCode: 'V1',
  plannedQuantity: '10.000000',
  issuedQuantity: '4.000000',
  receivedQuantity: '2.000000',
  acceptedQuantity: '2.000000',
  rejectedQuantity: '0.000000',
  issueWarehouseId: 60,
  issueWarehouseName: '原料仓',
  receiptWarehouseId: 61,
  receiptWarehouseName: '半成品仓',
  plannedStartDate: '2026-07-18',
  plannedFinishDate: '2026-07-25',
  plannedIssueDate: '2026-07-18',
  plannedReceiptDate: '2026-07-25',
  status: 'RELEASED',
  statusName: '已发布',
  sourceMrpRunId: 1001,
  sourceMrpSuggestionId: 'SUG-027',
  sourceMrpRequirementLineId: 'REQ-027',
  sourceSuggestionNo: 'MS-027-004',
  costVisible: true,
  costRestrictedReason: '无库存估值权限',
  actionDisabledReason: null,
  allowedActions: ['RELEASE', 'CLOSE', 'CANCEL', 'ISSUE', 'RECEIPT'],
  createdByName: '计划员',
  createdAt: '2026-07-17T09:00:00+08:00',
  updatedAt: '2026-07-17T10:00:00+08:00',
  version: 6,
  materials: [
    {
      id: 501,
      lineNo: 10,
      bomItemId: 9001,
      materialId: 41,
      materialCode: 'RM-027',
      materialName: '外协材料',
      unitName: '件',
      requiredQuantity: '20.000000',
      issuedQuantity: '4.000000',
      lossRate: '0.000000',
    },
  ],
  materialIssues: [
    {
      id: 701,
      issueNo: 'OSI-027-001',
      outsourcingOrderId: 77,
      status: 'DRAFT',
      businessDate: '2026-07-18',
      lineCount: 1,
      allowedActions: ['UPDATE', 'POST', 'CANCEL'],
      version: 2,
    },
  ],
  receipts: [
    {
      id: 801,
      receiptNo: 'OSR-027-001',
      outsourcingOrderId: 77,
      status: 'DRAFT',
      businessDate: '2026-07-20',
      lineCount: 1,
      allowedActions: ['UPDATE', 'POST', 'CANCEL'],
      version: 3,
    },
  ],
  traceLinks: [
    { label: '来源建议 MS-027-004', routePath: '/planning/material-requirements/1001', targetId: 1001 },
    { label: '受限销售项目', restricted: true, restrictedReason: '无销售项目查看权限', targetId: 20 },
  ],
}

const {
  materials: _outsourcingOrderMaterials,
  materialIssues: _outsourcingOrderMaterialIssues,
  receipts: _outsourcingOrderReceipts,
  traceLinks: _outsourcingOrderTraceLinks,
  ...outsourcingSummaryRecord
} = outsourcingOrder
const outsourcingSummary: OutsourcingOrderSummaryRecord = outsourcingSummaryRecord

const materialIssueDraft: OutsourcingDocumentDetailRecord = {
  id: 701,
  issueNo: 'OSI-027-001',
  outsourcingOrderId: 77,
  status: 'DRAFT',
  businessDate: '2026-07-18',
  warehouseId: 60,
  warehouseName: '原料仓',
  lineCount: 1,
  allowedActions: ['UPDATE', 'POST', 'CANCEL'],
  version: 2,
  remark: '发料详情备注',
  lines: [{
    id: 901,
    orderMaterialId: 501,
    lineNo: 10,
    materialId: 41,
    materialCode: 'RM-027',
    materialName: '外协材料',
    quantity: '4.000000',
    ownershipType: 'PROJECT',
    projectId: 20,
    projectNo: 'PRJ-027',
    costLayerId: 3001,
    trackingAllocations: [{ batchId: 900, batchNo: 'B-027', quantity: '4.000000' }],
  }],
}

const receiptDraft: OutsourcingDocumentDetailRecord = {
  id: 801,
  receiptNo: 'OSR-027-001',
  outsourcingOrderId: 77,
  status: 'DRAFT',
  businessDate: '2026-07-20',
  receiptWarehouseId: 61,
  receiptWarehouseName: '半成品仓',
  lineCount: 1,
  allowedActions: ['UPDATE', 'POST', 'CANCEL'],
  version: 3,
  remark: '收货详情备注',
  lines: [{
    id: 902,
    lineNo: 1,
    acceptedQuantity: '2.000000',
    rejectedQuantity: '0.000000',
    provisionalUnitCost: '88.000000',
    trackingAllocations: [{ serialNo: 'SN-027-001', quantity: '1.000000' }],
  }],
}

function page<T>(items: T[]) {
  return { items, total: items.length, page: 1, pageSize: 10, totalPages: 1 }
}

function setSession(permissions: string[]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  return pinia
}

async function mountWithRouter(component: Component, path: string, permissions: string[]) {
  const pinia = setSession(permissions)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/production/outsourcing-orders', name: 'production-outsourcing-orders', component: ProductionOutsourcingOrderListView },
      { path: '/production/outsourcing-orders/create', name: 'production-outsourcing-order-create', component: ProductionOutsourcingOrderFormView },
      { path: '/production/outsourcing-orders/:id', name: 'production-outsourcing-order-detail', component: ProductionOutsourcingOrderDetailView },
      { path: '/production/outsourcing-orders/:id/edit', name: 'production-outsourcing-order-edit', component: ProductionOutsourcingOrderFormView },
      { path: '/production/outsourcing-orders/:id/material-issues', name: 'production-outsourcing-order-material-issues', component: ProductionOutsourcingIssueView },
      { path: '/production/outsourcing-orders/:id/receipts', name: 'production-outsourcing-order-receipts', component: ProductionOutsourcingReceiptView },
      { path: '/planning/material-requirements/:id', name: 'material-requirement-run-detail', component: { render: () => null } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(component, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

async function setSelect(wrapper: VueWrapper, testId: string, value: unknown) {
  const select = wrapper.findComponent(`[data-test="${testId}"]`) as VueWrapper
  expect(select.exists()).toBe(true)
  select.vm.$emit('update:modelValue', value)
  await flushPromises()
}

async function setDatePicker(wrapper: VueWrapper, testId: string, value: string) {
  const picker = datePickerByName(wrapper, testId)
  expect(picker?.exists()).toBe(true)
  picker?.vm.$emit('update:modelValue', value)
  await flushPromises()
}

function expectButtonDisabled(wrapper: VueWrapper, testId: string, disabled: boolean) {
  const button = wrapper.findComponent(`[data-test="${testId}"]`) as VueWrapper
  expect(button.exists()).toBe(true)
  expect(Boolean((button.props() as { disabled?: boolean }).disabled)).toBe(disabled)
}

function expectSelectPlaceholder(wrapper: VueWrapper, testId: string, placeholder: string) {
  const select = wrapper.findComponent(`[data-test="${testId}"]`) as VueWrapper
  expect(select.exists()).toBe(true)
  expect((select.props() as { placeholder?: string }).placeholder).toBe(placeholder)
}

function expectDatePickerContract(wrapper: VueWrapper, testId: string, placeholder: string) {
  const picker = datePickerByName(wrapper, testId)
  expect(picker?.exists()).toBe(true)
  expect(picker?.props()).toMatchObject({
    type: 'date',
    format: 'YYYY-MM-DD',
    valueFormat: 'YYYY-MM-DD',
    valueOnClear: '',
    placeholder,
  })
}

function datePickerByName(wrapper: VueWrapper, name: string): VueWrapper | undefined {
  return wrapper.findAllComponents({ name: 'ElDatePicker' })
    .find((picker) => picker.props('name') === name)
}

describe('027 外协执行页面族', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    outsourcingApiMock.orders.list.mockResolvedValue(page([outsourcingSummary]))
    outsourcingApiMock.orders.get.mockResolvedValue(outsourcingOrder)
    outsourcingApiMock.orders.create.mockResolvedValue(outsourcingOrder)
    outsourcingApiMock.orders.update.mockResolvedValue(outsourcingOrder)
    outsourcingApiMock.orders.release.mockResolvedValue({ ...outsourcingOrder, status: 'RELEASED', version: 7 })
    outsourcingApiMock.orders.close.mockResolvedValue({ ...outsourcingOrder, status: 'CLOSED', version: 7 })
    outsourcingApiMock.orders.cancel.mockResolvedValue({ ...outsourcingOrder, status: 'CANCELLED', version: 7 })
    outsourcingApiMock.materialIssues.get.mockResolvedValue(materialIssueDraft)
    outsourcingApiMock.materialIssues.create.mockResolvedValue(materialIssueDraft)
    outsourcingApiMock.materialIssues.update.mockResolvedValue(materialIssueDraft)
    outsourcingApiMock.materialIssues.post.mockResolvedValue({ ...materialIssueDraft, status: 'POSTED', version: 3 })
    outsourcingApiMock.materialIssues.cancel.mockResolvedValue({ ...materialIssueDraft, status: 'CANCELLED', version: 3 })
    outsourcingApiMock.receipts.get.mockResolvedValue(receiptDraft)
    outsourcingApiMock.receipts.create.mockResolvedValue(receiptDraft)
    outsourcingApiMock.receipts.update.mockResolvedValue(receiptDraft)
    outsourcingApiMock.receipts.post.mockResolvedValue({ ...receiptDraft, status: 'POSTED', version: 4 })
    outsourcingApiMock.receipts.cancel.mockResolvedValue({ ...receiptDraft, status: 'CANCELLED', version: 4 })
    salesProjectApiMock.projects.list.mockResolvedValue(page([{ id: 20, projectNo: 'PRJ-027', name: '项目生产闭环' }]))
    masterDataApiMock.suppliers.list.mockResolvedValue(page([{ id: 30, code: 'SUP-027', name: '外协供应商', status: 'ENABLED' }]))
    masterDataApiMock.materials.list.mockResolvedValue(page([{ id: 40, code: 'SF-027', name: '外协半成品', status: 'ENABLED' }]))
    masterDataApiMock.warehouses.list.mockResolvedValue(page([
      { id: 60, code: 'WH-RAW', name: '原料仓', status: 'ENABLED' },
      { id: 61, code: 'WH-SF', name: '半成品仓', status: 'ENABLED' },
    ]))
    bomApiMock.list.mockResolvedValue(page([{ id: 50, bomCode: 'BOM-SF-027', versionCode: 'V1', name: '外协 BOM' }]))
  })

  it('列表按项目、供应商、状态和来源展示外协订单，并按权限提供新建与详情入口', async () => {
    const { wrapper, router } = await mountWithRouter(ProductionOutsourcingOrderListView, '/production/outsourcing-orders', [
      'production:outsourcing:view',
      'production:outsourcing:create',
    ])

    expect(outsourcingApiMock.orders.list).toHaveBeenCalledWith({
      keyword: '',
      projectId: undefined,
      supplierId: undefined,
      productMaterialId: undefined,
      status: undefined,
      plannedDateFrom: '',
      plannedDateTo: '',
      page: 1,
      pageSize: 10,
    })
    expect(wrapper.text()).toContain('外协执行')
    expect(wrapper.text()).toContain('OS-027-001')
    expect(wrapper.text()).toContain('PRJ-027 项目生产闭环')
    expect(wrapper.text()).toContain('外协供应商')
    expect(wrapper.text()).toContain('MS-027-004')
    expect(wrapper.find('[data-test="create-outsourcing-order"]').exists()).toBe(true)

    await wrapper.find('input[name="outsourcing-keyword"]').setValue('OS-027')
    await setSelect(wrapper, 'outsourcing-status-filter', 'RELEASED')
    await wrapper.find('input[name="outsourcing-project"]').setValue('20')
    await wrapper.find('input[name="outsourcing-supplier"]').setValue('30')
    await wrapper.find('input[name="outsourcing-product-material"]').setValue('40')
    await wrapper.find('[data-test="search-outsourcing-orders"]').trigger('click')
    await flushPromises()
    expect(outsourcingApiMock.orders.list).toHaveBeenLastCalledWith(expect.objectContaining({
      keyword: 'OS-027',
      projectId: 20,
      supplierId: 30,
      productMaterialId: 40,
      status: 'RELEASED',
    }))

    await wrapper.find('[data-test="view-outsourcing-order"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('production-outsourcing-order-detail')
    expect(router.currentRoute.value.params.id).toBe('77')
  })

  it('列表读取并监听路由项目上下文，销售项目入口进入后保留项目筛选', async () => {
    const { router } = await mountWithRouter(ProductionOutsourcingOrderListView, '/production/outsourcing-orders?projectId=20', [
      'production:outsourcing:view',
    ])

    expect(outsourcingApiMock.orders.list).toHaveBeenLastCalledWith(expect.objectContaining({
      projectId: 20,
      productMaterialId: undefined,
    }))

    await router.push('/production/outsourcing-orders?projectId=21')
    await flushPromises()
    expect(outsourcingApiMock.orders.list).toHaveBeenLastCalledWith(expect.objectContaining({
      projectId: 21,
    }))
  })

  it('创建和编辑表单维护项目、供应商、BOM、仓库和来源归属，保存时携带幂等键与 version', async () => {
    const { wrapper, router } = await mountWithRouter(ProductionOutsourcingOrderFormView, '/production/outsourcing-orders/create', [
      'production:outsourcing:view',
      'production:outsourcing:create',
      'production:outsourcing:update',
    ])

    await setSelect(wrapper, 'outsourcing-ownership-type', 'PROJECT')
    await setSelect(wrapper, 'outsourcing-project-id', 20)
    await setSelect(wrapper, 'outsourcing-supplier-id', 30)
    await setSelect(wrapper, 'outsourcing-product-material-id', 40)
    await setSelect(wrapper, 'outsourcing-bom-id', 50)
    await setSelect(wrapper, 'outsourcing-issue-warehouse-id', 60)
    await setSelect(wrapper, 'outsourcing-receipt-warehouse-id', 61)
    await wrapper.find('input[name="outsourcing-planned-quantity"]').setValue('10.000000')
    await setDatePicker(wrapper, 'outsourcing-planned-issue-date', '2026-07-18')
    await setDatePicker(wrapper, 'outsourcing-planned-receipt-date', '2026-07-25')
    await wrapper.find('[data-test="save-outsourcing-order"]').trigger('click')
    await flushPromises()

    expect(outsourcingApiMock.orders.create).toHaveBeenCalledWith({
      ownershipType: 'PROJECT',
      projectId: 20,
      supplierId: 30,
      productMaterialId: 40,
      bomId: 50,
      plannedQuantity: '10.000000',
      issueWarehouseId: 60,
      receiptWarehouseId: 61,
      plannedIssueDate: '2026-07-18',
      plannedReceiptDate: '2026-07-25',
      remark: '',
      idempotencyKey: 'outsourcing-key',
    })
    expect(router.currentRoute.value.name).toBe('production-outsourcing-order-detail')

    const edit = await mountWithRouter(ProductionOutsourcingOrderFormView, '/production/outsourcing-orders/77/edit', [
      'production:outsourcing:view',
      'production:outsourcing:update',
    ])
    await edit.wrapper.find('input[name="outsourcing-planned-quantity"]').setValue('12.000000')
    await edit.wrapper.find('[data-test="save-outsourcing-order"]').trigger('click')
    await flushPromises()
    expect(outsourcingApiMock.orders.update).toHaveBeenCalledWith(77, expect.objectContaining({
      version: 6,
      plannedQuantity: '12.000000',
      idempotencyKey: 'outsourcing-key',
    }))
  })

  it('创建表单所有业务选择控件提供明确中文占位', async () => {
    const { wrapper } = await mountWithRouter(ProductionOutsourcingOrderFormView, '/production/outsourcing-orders/create', [
      'production:outsourcing:view',
      'production:outsourcing:create',
    ])

    expectSelectPlaceholder(wrapper, 'outsourcing-ownership-type', '请选择归属')
    expectSelectPlaceholder(wrapper, 'outsourcing-project-id', '请选择项目')
    expectSelectPlaceholder(wrapper, 'outsourcing-supplier-id', '请选择供应商')
    expectSelectPlaceholder(wrapper, 'outsourcing-product-material-id', '请选择成品物料')
    expectSelectPlaceholder(wrapper, 'outsourcing-bom-id', '请选择 BOM')
    expectSelectPlaceholder(wrapper, 'outsourcing-issue-warehouse-id', '请选择发料仓库')
    expectSelectPlaceholder(wrapper, 'outsourcing-receipt-warehouse-id', '请选择收货仓库')
    expectDatePickerContract(wrapper, 'outsourcing-planned-issue-date', '请选择计划发料日期')
    expectDatePickerContract(wrapper, 'outsourcing-planned-receipt-date', '请选择计划收货日期')
    expect(wrapper.find('[data-test="outsourcing-order-form-bottom-actions"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="bottom-save-outsourcing-order"]').exists()).toBe(true)
  })

  it('外协订单计划日期使用日期选择器且非法日期不提交', async () => {
    const { wrapper } = await mountWithRouter(ProductionOutsourcingOrderFormView, '/production/outsourcing-orders/create', [
      'production:outsourcing:view',
      'production:outsourcing:create',
    ])

    expectDatePickerContract(wrapper, 'outsourcing-planned-issue-date', '请选择计划发料日期')
    expectDatePickerContract(wrapper, 'outsourcing-planned-receipt-date', '请选择计划收货日期')
    await setSelect(wrapper, 'outsourcing-ownership-type', 'PROJECT')
    await setSelect(wrapper, 'outsourcing-project-id', 20)
    await setSelect(wrapper, 'outsourcing-supplier-id', 30)
    await setSelect(wrapper, 'outsourcing-product-material-id', 40)
    await setSelect(wrapper, 'outsourcing-bom-id', 50)
    await setSelect(wrapper, 'outsourcing-issue-warehouse-id', 60)
    await setSelect(wrapper, 'outsourcing-receipt-warehouse-id', 61)
    await wrapper.find('input[name="outsourcing-planned-quantity"]').setValue('10.000000')
    await setDatePicker(wrapper, 'outsourcing-planned-issue-date', '2026-7-18')
    await setDatePicker(wrapper, 'outsourcing-planned-receipt-date', '2026-07-25')
    await wrapper.find('[data-test="save-outsourcing-order"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('计划发料日期格式必须为 YYYY-MM-DD')
    expect(outsourcingApiMock.orders.create).not.toHaveBeenCalled()
  })

  it('外协订单编辑页加载失败时关闭表单并只保留返回', async () => {
    outsourcingApiMock.orders.get.mockRejectedValueOnce(new AccountPermissionApiError(
      '外协订单不存在或无权查看',
      'PRODUCTION_OUTSOURCING_ORDER_NOT_FOUND',
      404,
      'trace-outsourcing-order',
    ))
    const { wrapper } = await mountWithRouter(ProductionOutsourcingOrderFormView, '/production/outsourcing-orders/999/edit', [
      'production:outsourcing:view',
      'production:outsourcing:update',
    ])

    expect(wrapper.text()).toContain('外协订单不存在或无权查看')
    expect(wrapper.find('[data-test="save-outsourcing-order"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="bottom-save-outsourcing-order"]').exists()).toBe(false)
    expect(wrapper.find('input[name="outsourcing-planned-quantity"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('外协订单加载中')
  })

  it('详情展示项目、供应商、来源、BOM、用料和执行单据，并按 allowedActions 执行发布关闭取消', async () => {
    const { wrapper, router } = await mountWithRouter(ProductionOutsourcingOrderDetailView, '/production/outsourcing-orders/77', [
      'production:outsourcing:view',
      'production:outsourcing:release',
      'production:outsourcing:close',
      'production:outsourcing:cancel',
      'production:outsourcing-issue:view',
      'production:outsourcing-receipt:view',
    ])

    expect(wrapper.text()).toContain('OS-027-001')
    expect(wrapper.text()).toContain('PRJ-027 项目生产闭环')
    expect(wrapper.text()).toContain('外协供应商')
    expect(wrapper.text()).toContain('BOM-SF-027')
    expect(wrapper.text()).toContain('MS-027-004')
    expect(wrapper.text()).toContain('RM-027 外协材料')
    expect(wrapper.text()).toContain('OSI-027-001')
    expect(wrapper.text()).toContain('OSR-027-001')
    expect(wrapper.text()).toContain('可查看')
    expect(wrapper.text()).toContain('无销售项目查看权限')
    const traceLinks = wrapper.findAll('[data-test="outsourcing-trace-link"]')
    expect(traceLinks).toHaveLength(1)
    await traceLinks[0].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/planning/material-requirements/1001')
    await router.push('/production/outsourcing-orders/77')
    await flushPromises()
    expectButtonDisabled(wrapper, 'release-outsourcing-order', false)
    expectButtonDisabled(wrapper, 'close-outsourcing-order', false)

    await wrapper.find('[data-test="release-outsourcing-order"]').trigger('click')
    await flushPromises()
    expect(outsourcingApiMock.orders.release).toHaveBeenCalledWith(77, {
      version: 6,
      idempotencyKey: 'outsourcing-key',
    })

    outsourcingApiMock.orders.close.mockRejectedValueOnce(new AccountPermissionApiError(
      '外协订单状态不允许关闭',
      'PRODUCTION_OUTSOURCING_STATUS_INVALID',
      409,
      'trace-close',
    ))
    await wrapper.find('[data-test="close-outsourcing-order"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('外协订单状态不允许关闭')

    await wrapper.find('[data-test="cancel-outsourcing-order"]').trigger('click')
    await flushPromises()
    expect(outsourcingApiMock.orders.cancel).toHaveBeenCalledWith(77, {
      version: 6,
      idempotencyKey: 'outsourcing-key',
    })
  })

  it('详情关闭动作只依据 CLOSE，不能用 RELEASE 误开关闭入口', async () => {
    outsourcingApiMock.orders.get.mockResolvedValueOnce({
      ...outsourcingOrder,
      allowedActions: ['RELEASE'],
      actionDisabledReason: '外协订单尚未达到关闭条件',
    })
    const { wrapper } = await mountWithRouter(ProductionOutsourcingOrderDetailView, '/production/outsourcing-orders/77', [
      'production:outsourcing:view',
      'production:outsourcing:release',
      'production:outsourcing:close',
    ])

    expectButtonDisabled(wrapper, 'release-outsourcing-order', false)
    expectButtonDisabled(wrapper, 'close-outsourcing-order', true)
    expect(wrapper.text()).toContain('外协订单尚未达到关闭条件')
  })

  it('详情执行单据状态显示中文，不裸露 DRAFT 或 POSTED', async () => {
    outsourcingApiMock.orders.get.mockResolvedValueOnce({
      ...outsourcingOrder,
      materialIssues: [{ ...outsourcingOrder.materialIssues[0], status: 'DRAFT' }],
      receipts: [{ ...outsourcingOrder.receipts[0], status: 'POSTED' }],
    })
    const { wrapper } = await mountWithRouter(ProductionOutsourcingOrderDetailView, '/production/outsourcing-orders/77', [
      'production:outsourcing:view',
      'production:outsourcing-issue:view',
      'production:outsourcing-receipt:view',
    ])

    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.text()).toContain('已过账')
    expect(wrapper.text()).not.toContain('DRAFT')
    expect(wrapper.text()).not.toContain('POSTED')
  })

  it('订单级创建入口只接受后端 canonical ISSUE 和 RECEIPT 动作码', async () => {
    outsourcingApiMock.orders.get.mockResolvedValueOnce({
      ...outsourcingOrder,
      allowedActions: ['ISSUE', 'RECEIPT'],
    })
    const { wrapper, router } = await mountWithRouter(ProductionOutsourcingOrderDetailView, '/production/outsourcing-orders/77', [
      'production:outsourcing:view',
      'production:outsourcing-issue:create',
      'production:outsourcing-receipt:create',
    ])

    expectButtonDisabled(wrapper, 'create-outsourcing-issue', false)
    expectButtonDisabled(wrapper, 'create-outsourcing-receipt', false)
    await wrapper.find('[data-test="create-outsourcing-issue"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/production/outsourcing-orders/77/material-issues')
    await router.push('/production/outsourcing-orders/77')
    await flushPromises()
    await wrapper.find('[data-test="create-outsourcing-receipt"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/production/outsourcing-orders/77/receipts')
  })

  it('订单级创建入口不再接受前端旧私有码', async () => {
    const legacyIssueAction = ['ISSUE', 'CREATE'].join('_')
    const legacyReceiptAction = ['RECEIPT', 'CREATE'].join('_')
    outsourcingApiMock.orders.get.mockResolvedValueOnce({
      ...outsourcingOrder,
      allowedActions: [legacyIssueAction, legacyReceiptAction],
    })
    const { wrapper } = await mountWithRouter(ProductionOutsourcingOrderDetailView, '/production/outsourcing-orders/77', [
      'production:outsourcing:view',
      'production:outsourcing-issue:create',
      'production:outsourcing-receipt:create',
    ])

    expectButtonDisabled(wrapper, 'create-outsourcing-issue', true)
    expectButtonDisabled(wrapper, 'create-outsourcing-receipt', true)
  })

  it('只读权限用户不显示外协发料和收货可写入口', async () => {
    const { wrapper } = await mountWithRouter(ProductionOutsourcingOrderDetailView, '/production/outsourcing-orders/77', [
      'production:outsourcing:view',
    ])

    expect(wrapper.find('[data-test="create-outsourcing-issue"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="create-outsourcing-receipt"]').exists()).toBe(false)
  })

  it('只读用户直达外协发料或收货页面时不进入可写表单', async () => {
    const issue = await mountWithRouter(ProductionOutsourcingIssueView, '/production/outsourcing-orders/77/material-issues', [
      'production:outsourcing:view',
    ])
    expect(issue.wrapper.find('[data-test="save-outsourcing-issue"]').exists()).toBe(false)
    expect(issue.wrapper.text()).toContain('没有外协发料写入权限')

    const receipt = await mountWithRouter(ProductionOutsourcingReceiptView, '/production/outsourcing-orders/77/receipts', [
      'production:outsourcing:view',
    ])
    expect(receipt.wrapper.find('[data-test="save-outsourcing-receipt"]').exists()).toBe(false)
    expect(receipt.wrapper.text()).toContain('没有外协收货写入权限')
  })

  it('外协发料和收货表单提供中文占位', async () => {
    const issue = await mountWithRouter(ProductionOutsourcingIssueView, '/production/outsourcing-orders/77/material-issues', [
      'production:outsourcing:view',
      'production:outsourcing-issue:create',
    ])
    expectDatePickerContract(issue.wrapper, 'outsourcing-issue-business-date', '请选择业务日期')
    expectSelectPlaceholder(issue.wrapper, 'outsourcing-issue-warehouse-id', '请选择发料仓库')
    expectSelectPlaceholder(issue.wrapper, 'outsourcing-issue-line-material', '请选择材料行')
    expectSelectPlaceholder(issue.wrapper, 'outsourcing-issue-line-ownership', '请选择库存来源')

    const receipt = await mountWithRouter(ProductionOutsourcingReceiptView, '/production/outsourcing-orders/77/receipts', [
      'production:outsourcing:view',
      'production:outsourcing-receipt:create',
    ])
    expectDatePickerContract(receipt.wrapper, 'outsourcing-receipt-business-date', '请选择业务日期')
    expectSelectPlaceholder(receipt.wrapper, 'outsourcing-receipt-warehouse-id', '请选择收货仓库')
  })

  it('外协发料和收货业务日期使用日期选择器且非法日期不提交', async () => {
    const issue = await mountWithRouter(ProductionOutsourcingIssueView, '/production/outsourcing-orders/77/material-issues', [
      'production:outsourcing:view',
      'production:outsourcing-issue:create',
    ])
    expectDatePickerContract(issue.wrapper, 'outsourcing-issue-business-date', '请选择业务日期')
    await setDatePicker(issue.wrapper, 'outsourcing-issue-business-date', '2026/07/18')
    await setSelect(issue.wrapper, 'outsourcing-issue-warehouse-id', 60)
    await setSelect(issue.wrapper, 'outsourcing-issue-line-material', 501)
    await issue.wrapper.find('input[name="outsourcing-issue-line-quantity"]').setValue('4.000000')
    await issue.wrapper.find('[data-test="save-outsourcing-issue"]').trigger('click')
    await flushPromises()
    expect(issue.wrapper.text()).toContain('业务日期格式必须为 YYYY-MM-DD')
    expect(outsourcingApiMock.materialIssues.create).not.toHaveBeenCalled()

    const receipt = await mountWithRouter(ProductionOutsourcingReceiptView, '/production/outsourcing-orders/77/receipts', [
      'production:outsourcing:view',
      'production:outsourcing-receipt:create',
    ])
    expectDatePickerContract(receipt.wrapper, 'outsourcing-receipt-business-date', '请选择业务日期')
    await setDatePicker(receipt.wrapper, 'outsourcing-receipt-business-date', '2026/07/20')
    await setSelect(receipt.wrapper, 'outsourcing-receipt-warehouse-id', 61)
    await receipt.wrapper.find('input[name="outsourcing-receipt-accepted-quantity"]').setValue('2.000000')
    await receipt.wrapper.find('input[name="outsourcing-receipt-rejected-quantity"]').setValue('0.000000')
    await receipt.wrapper.find('[data-test="save-outsourcing-receipt"]').trigger('click')
    await flushPromises()
    expect(receipt.wrapper.text()).toContain('业务日期格式必须为 YYYY-MM-DD')
    expect(outsourcingApiMock.receipts.create).not.toHaveBeenCalled()
  })

  it('外协发料或收货对象不存在时关闭草稿表单并只保留返回', async () => {
    outsourcingApiMock.orders.get.mockRejectedValueOnce(new AccountPermissionApiError(
      '外协订单不存在或无权查看',
      'PRODUCTION_OUTSOURCING_ORDER_NOT_FOUND',
      404,
      'trace-outsourcing-issue-order',
    ))
    const missingIssueOrder = await mountWithRouter(ProductionOutsourcingIssueView, '/production/outsourcing-orders/999/material-issues', [
      'production:outsourcing:view',
      'production:outsourcing-issue:create',
    ])
    expect(missingIssueOrder.wrapper.text()).toContain('外协订单不存在或无权查看')
    expect(missingIssueOrder.wrapper.find('[data-test="save-outsourcing-issue"]').exists()).toBe(false)
    expect(datePickerByName(missingIssueOrder.wrapper, 'outsourcing-issue-business-date')).toBeUndefined()

    outsourcingApiMock.materialIssues.get.mockRejectedValueOnce(new AccountPermissionApiError(
      '外协发料不存在或无权查看',
      'PRODUCTION_OUTSOURCING_ISSUE_NOT_FOUND',
      404,
      'trace-outsourcing-issue-document',
    ))
    const missingIssueDocument = await mountWithRouter(ProductionOutsourcingIssueView, '/production/outsourcing-orders/77/material-issues?documentId=999', [
      'production:outsourcing:view',
      'production:outsourcing-issue:update',
      'production:outsourcing-issue:post',
      'production:outsourcing-issue:cancel',
    ])
    expect(missingIssueDocument.wrapper.text()).toContain('外协发料不存在或无权查看')
    expect(missingIssueDocument.wrapper.find('[data-test="save-outsourcing-issue"]').exists()).toBe(false)
    expect(missingIssueDocument.wrapper.find('[data-test="post-outsourcing-issue"]').exists()).toBe(false)
    expect(missingIssueDocument.wrapper.find('[data-test="cancel-outsourcing-issue"]').exists()).toBe(false)
    expect(datePickerByName(missingIssueDocument.wrapper, 'outsourcing-issue-business-date')).toBeUndefined()

    outsourcingApiMock.orders.get.mockRejectedValueOnce(new AccountPermissionApiError(
      '外协订单不存在或无权查看',
      'PRODUCTION_OUTSOURCING_ORDER_NOT_FOUND',
      404,
      'trace-outsourcing-receipt-order',
    ))
    const missingReceiptOrder = await mountWithRouter(ProductionOutsourcingReceiptView, '/production/outsourcing-orders/999/receipts', [
      'production:outsourcing:view',
      'production:outsourcing-receipt:create',
    ])
    expect(missingReceiptOrder.wrapper.text()).toContain('外协订单不存在或无权查看')
    expect(missingReceiptOrder.wrapper.find('[data-test="save-outsourcing-receipt"]').exists()).toBe(false)
    expect(datePickerByName(missingReceiptOrder.wrapper, 'outsourcing-receipt-business-date')).toBeUndefined()

    outsourcingApiMock.receipts.get.mockRejectedValueOnce(new AccountPermissionApiError(
      '外协收货不存在或无权查看',
      'PRODUCTION_OUTSOURCING_RECEIPT_NOT_FOUND',
      404,
      'trace-outsourcing-receipt-document',
    ))
    const missingReceiptDocument = await mountWithRouter(ProductionOutsourcingReceiptView, '/production/outsourcing-orders/77/receipts?documentId=999', [
      'production:outsourcing:view',
      'production:outsourcing-receipt:update',
      'production:outsourcing-receipt:post',
      'production:outsourcing-receipt:cancel',
    ])
    expect(missingReceiptDocument.wrapper.text()).toContain('外协收货不存在或无权查看')
    expect(missingReceiptDocument.wrapper.find('[data-test="save-outsourcing-receipt"]').exists()).toBe(false)
    expect(missingReceiptDocument.wrapper.find('[data-test="post-outsourcing-receipt"]').exists()).toBe(false)
    expect(missingReceiptDocument.wrapper.find('[data-test="cancel-outsourcing-receipt"]').exists()).toBe(false)
    expect(datePickerByName(missingReceiptDocument.wrapper, 'outsourcing-receipt-business-date')).toBeUndefined()
  })

  it('外协发料支持草稿创建、编辑、过账和取消，记录项目/公共来源、成本层与批次分配', async () => {
    const { wrapper } = await mountWithRouter(ProductionOutsourcingIssueView, '/production/outsourcing-orders/77/material-issues', [
      'production:outsourcing:view',
      'production:outsourcing-issue:create',
      'production:outsourcing-issue:update',
      'production:outsourcing-issue:post',
      'production:outsourcing-issue:cancel',
    ])

    await setDatePicker(wrapper, 'outsourcing-issue-business-date', '2026-07-18')
    await setSelect(wrapper, 'outsourcing-issue-warehouse-id', 60)
    await setSelect(wrapper, 'outsourcing-issue-line-material', 501)
    await wrapper.find('input[name="outsourcing-issue-line-quantity"]').setValue('4.000000')
    await setSelect(wrapper, 'outsourcing-issue-line-ownership', 'PROJECT')
    await wrapper.find('input[name="outsourcing-issue-line-project"]').setValue('20')
    await wrapper.find('input[name="outsourcing-issue-line-cost-layer"]').setValue('3001')
    await wrapper.find('input[name="outsourcing-issue-line-batch"]').setValue('B-027')
    await wrapper.find('[data-test="save-outsourcing-issue"]').trigger('click')
    await flushPromises()
    expect(outsourcingApiMock.materialIssues.create).toHaveBeenCalledWith(77, {
      version: 6,
      businessDate: '2026-07-18',
      warehouseId: 60,
      remark: '',
      idempotencyKey: 'outsourcing-key',
      lines: [{
        orderMaterialId: 501,
        lineNo: 10,
        quantity: '4.000000',
        ownershipType: 'PROJECT',
        projectId: 20,
        costLayerId: 3001,
        trackingAllocations: [{ batchNo: 'B-027', quantity: '4.000000' }],
      }],
    })

    const edit = await mountWithRouter(ProductionOutsourcingIssueView, '/production/outsourcing-orders/77/material-issues?documentId=701', [
      'production:outsourcing:view',
      'production:outsourcing-issue:update',
      'production:outsourcing-issue:post',
      'production:outsourcing-issue:cancel',
    ])
    await edit.wrapper.find('input[name="outsourcing-issue-line-quantity"]').setValue('5.000000')
    await edit.wrapper.find('input[name="outsourcing-issue-line-batch-quantity"]').setValue('5.000000')
    await edit.wrapper.find('[data-test="save-outsourcing-issue"]').trigger('click')
    await flushPromises()
    expect(outsourcingApiMock.materialIssues.update).toHaveBeenCalledWith(77, 701, expect.objectContaining({
      version: 2,
      remark: '发料详情备注',
      idempotencyKey: 'outsourcing-key',
      lines: [expect.objectContaining({ quantity: '5.000000' })],
    }))
    await edit.wrapper.find('[data-test="post-outsourcing-issue"]').trigger('click')
    await flushPromises()
    expect(outsourcingApiMock.materialIssues.post).toHaveBeenCalledWith(77, 701, {
      version: 2,
      idempotencyKey: 'outsourcing-key',
    })
    await edit.wrapper.find('[data-test="cancel-outsourcing-issue"]').trigger('click')
    await flushPromises()
    expect(outsourcingApiMock.materialIssues.cancel).toHaveBeenCalledWith(77, 701, {
      version: 3,
      idempotencyKey: 'outsourcing-key',
    })
  })

  it('外协发料校验批次数量合计，成本受限时不渲染也不提交成本层', async () => {
    outsourcingApiMock.orders.get.mockResolvedValueOnce({
      ...outsourcingOrder,
      costVisible: false,
      costRestrictedReason: '无库存估值权限',
    })
    const { wrapper } = await mountWithRouter(ProductionOutsourcingIssueView, '/production/outsourcing-orders/77/material-issues', [
      'production:outsourcing:view',
      'production:outsourcing-issue:create',
    ])

    expect(wrapper.text()).toContain('无库存估值权限')
    expect(wrapper.find('input[name="outsourcing-issue-line-cost-layer"]').exists()).toBe(false)
    await wrapper.find('input[name="outsourcing-issue-line-quantity"]').setValue('4.000000')
    await wrapper.find('input[name="outsourcing-issue-line-batch"]').setValue('B-027')
    await wrapper.find('input[name="outsourcing-issue-line-batch-quantity"]').setValue('3.000000')
    await wrapper.find('[data-test="save-outsourcing-issue"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('批次数量合计必须等于发料数量')
    expect(outsourcingApiMock.materialIssues.create).not.toHaveBeenCalled()

    await wrapper.find('input[name="outsourcing-issue-line-batch-quantity"]').setValue('4.000000')
    await wrapper.find('[data-test="save-outsourcing-issue"]').trigger('click')
    await flushPromises()
    const payload = outsourcingApiMock.materialIssues.create.mock.calls[0][1]
    expect(payload.lines[0]).not.toHaveProperty('costLayerId')
    expect(payload.lines[0].trackingAllocations).toEqual([{ batchNo: 'B-027', quantity: '4.000000' }])
  })

  it('外协收货支持草稿创建、编辑、过账和取消，记录暂估单价、批次/序列和数量状态', async () => {
    const { wrapper } = await mountWithRouter(ProductionOutsourcingReceiptView, '/production/outsourcing-orders/77/receipts', [
      'production:outsourcing:view',
      'production:outsourcing-receipt:create',
      'production:outsourcing-receipt:update',
      'production:outsourcing-receipt:post',
      'production:outsourcing-receipt:cancel',
    ])

    await setDatePicker(wrapper, 'outsourcing-receipt-business-date', '2026-07-20')
    await setSelect(wrapper, 'outsourcing-receipt-warehouse-id', 61)
    await wrapper.find('input[name="outsourcing-receipt-accepted-quantity"]').setValue('2.000000')
    await wrapper.find('input[name="outsourcing-receipt-rejected-quantity"]').setValue('0.000000')
    await wrapper.find('input[name="outsourcing-receipt-provisional-unit-cost"]').setValue('88.000000')
    await wrapper.find('input[name="outsourcing-receipt-serial"]').setValue('SN-027-001')
    await wrapper.find('[data-test="save-outsourcing-receipt"]').trigger('click')
    await flushPromises()
    expect(outsourcingApiMock.receipts.create).toHaveBeenCalledWith(77, {
      version: 6,
      businessDate: '2026-07-20',
      receiptWarehouseId: 61,
      remark: '',
      idempotencyKey: 'outsourcing-key',
      lines: [{
        lineNo: 1,
        acceptedQuantity: '2.000000',
        rejectedQuantity: '0.000000',
        provisionalUnitCost: '88.000000',
        trackingAllocations: [{ serialNo: 'SN-027-001', quantity: '1.000000' }],
      }],
    })

    const edit = await mountWithRouter(ProductionOutsourcingReceiptView, '/production/outsourcing-orders/77/receipts?documentId=801', [
      'production:outsourcing:view',
      'production:outsourcing-receipt:update',
      'production:outsourcing-receipt:post',
      'production:outsourcing-receipt:cancel',
    ])
    await edit.wrapper.find('input[name="outsourcing-receipt-accepted-quantity"]').setValue('3.000000')
    await edit.wrapper.find('[data-test="save-outsourcing-receipt"]').trigger('click')
    await flushPromises()
    expect(outsourcingApiMock.receipts.update).toHaveBeenCalledWith(77, 801, expect.objectContaining({
      version: 3,
      remark: '收货详情备注',
      idempotencyKey: 'outsourcing-key',
      lines: [expect.objectContaining({ acceptedQuantity: '3.000000' })],
    }))
    await edit.wrapper.find('[data-test="post-outsourcing-receipt"]').trigger('click')
    await flushPromises()
    expect(outsourcingApiMock.receipts.post).toHaveBeenCalledWith(77, 801, {
      version: 3,
      idempotencyKey: 'outsourcing-key',
    })
    await edit.wrapper.find('[data-test="cancel-outsourcing-receipt"]').trigger('click')
    await flushPromises()
    expect(outsourcingApiMock.receipts.cancel).toHaveBeenCalledWith(77, 801, {
      version: 4,
      idempotencyKey: 'outsourcing-key',
    })
  })

  it('外协收货校验序列数量为 1，成本受限时不渲染也不提交暂估单价', async () => {
    outsourcingApiMock.orders.get.mockResolvedValueOnce({
      ...outsourcingOrder,
      costVisible: false,
      costRestrictedReason: '无库存估值权限',
    })
    const { wrapper } = await mountWithRouter(ProductionOutsourcingReceiptView, '/production/outsourcing-orders/77/receipts', [
      'production:outsourcing:view',
      'production:outsourcing-receipt:create',
    ])

    expect(wrapper.text()).toContain('无库存估值权限')
    expect(wrapper.find('input[name="outsourcing-receipt-provisional-unit-cost"]').exists()).toBe(false)
    await wrapper.find('input[name="outsourcing-receipt-accepted-quantity"]').setValue('2.000000')
    await wrapper.find('input[name="outsourcing-receipt-serial"]').setValue('SN-027-001')
    await wrapper.find('input[name="outsourcing-receipt-serial-quantity"]').setValue('2.000000')
    await wrapper.find('[data-test="save-outsourcing-receipt"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('序列数量必须为 1')
    expect(outsourcingApiMock.receipts.create).not.toHaveBeenCalled()

    await wrapper.find('input[name="outsourcing-receipt-serial-quantity"]').setValue('1.000000')
    await wrapper.find('[data-test="save-outsourcing-receipt"]').trigger('click')
    await flushPromises()
    const payload = outsourcingApiMock.receipts.create.mock.calls[0][1]
    expect(payload.lines[0]).not.toHaveProperty('provisionalUnitCost')
    expect(payload.lines[0].trackingAllocations).toEqual([{ serialNo: 'SN-027-001', quantity: '1.000000' }])
  })
})
