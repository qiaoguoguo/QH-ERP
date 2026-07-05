import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PageResult } from '../../shared/api/accountPermissionApi'
import type { InventoryDocumentDetailRecord, InventoryDocumentSummaryRecord } from '../../shared/api/inventoryApi'
import { useConfirmActionMock } from '../../test/setup'
import { useAuthStore } from '../../stores/authStore'
import InventoryDocumentListView from './InventoryDocumentListView.vue'

const confirmActionMock = useConfirmActionMock()

const inventoryApiMock = vi.hoisted(() => ({
  documents: {
    list: vi.fn(),
    post: vi.fn(),
  },
}))

vi.mock('../../shared/api/inventoryApi', () => ({
  inventoryApi: inventoryApiMock,
}))

const draftDocument: InventoryDocumentSummaryRecord = {
  id: 1,
  documentNo: 'INV-OPEN-001',
  documentType: 'OPENING',
  status: 'DRAFT',
  businessDate: '2026-07-03',
  reason: '期初导入',
  remark: null,
  lineCount: 2,
  createdByName: '管理员',
  createdAt: '2026-07-03T09:00:00+08:00',
  updatedAt: '2026-07-03T09:30:00+08:00',
  postedByName: null,
  postedAt: null,
}

const postedDocument: InventoryDocumentSummaryRecord = {
  ...draftDocument,
  id: 2,
  documentNo: 'INV-ADJ-001',
  documentType: 'ADJUSTMENT',
  status: 'POSTED',
  reason: '库存修正',
  postedByName: '管理员',
  postedAt: '2026-07-03T10:00:00+08:00',
}

const documentPage: PageResult<InventoryDocumentSummaryRecord> = {
  items: [draftDocument, postedDocument],
  page: 1,
  pageSize: 10,
  total: 2,
  totalPages: 1,
}

const postedDetail: InventoryDocumentDetailRecord = {
  ...draftDocument,
  status: 'POSTED',
  postedByName: '管理员',
  postedAt: '2026-07-03T11:00:00+08:00',
  lines: [],
}

async function setSelectValue(wrapper: VueWrapper, dataTest: string, value: unknown) {
  const select = wrapper.findComponent(`[data-test="${dataTest}"]`) as VueWrapper
  expect(select.exists()).toBe(true)
  select.vm.$emit('update:modelValue', value)
  await flushPromises()
}

async function mountDocuments(permissions = [
  'inventory:document:view',
  'inventory:document:create',
  'inventory:document:update',
  'inventory:document:post',
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
      { path: '/inventory/documents', name: 'inventory-documents', component: InventoryDocumentListView },
      { path: '/inventory/documents/create', name: 'inventory-document-create', component: { render: () => null } },
      { path: '/inventory/documents/:id', name: 'inventory-document-detail', component: { render: () => null } },
      { path: '/inventory/documents/:id/edit', name: 'inventory-document-edit', component: { render: () => null } },
    ],
  })
  await router.push('/inventory/documents')
  await router.isReady()
  const wrapper = mount(InventoryDocumentListView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('库存单据列表页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    inventoryApiMock.documents.list.mockResolvedValue(documentPage)
    inventoryApiMock.documents.post.mockResolvedValue(postedDetail)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('展示库存单据列表、状态标签和创建入口权限', async () => {
    const { wrapper } = await mountDocuments()

    expect(wrapper.text()).toContain('库存单据')
    expect(wrapper.text()).toContain('INV-OPEN-001')
    expect(wrapper.text()).toContain('期初库存')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.find('[data-test="create-opening-document"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="create-adjustment-document"]').exists()).toBe(true)

    const readonly = await mountDocuments(['inventory:document:view'])
    expect(readonly.wrapper.find('[data-test="create-opening-document"]').exists()).toBe(false)
    expect(readonly.wrapper.find('[data-test="create-adjustment-document"]').exists()).toBe(false)
  })

  it('新增期初和新增调整入口进入创建路由并固定单据类型', async () => {
    const { wrapper, router } = await mountDocuments()

    await wrapper.find('[data-test="create-opening-document"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('inventory-document-create')
    expect(router.currentRoute.value.query.type).toBe('OPENING')

    await router.push('/inventory/documents')
    await flushPromises()
    await wrapper.find('[data-test="create-adjustment-document"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('inventory-document-create')
    expect(router.currentRoute.value.query.type).toBe('ADJUSTMENT')
  })

  it('草稿按权限显示编辑和过账，已过账只显示详情', async () => {
    const { wrapper } = await mountDocuments()

    expect(wrapper.findAll('[data-test="edit-inventory-document"]')).toHaveLength(1)
    expect(wrapper.findAll('[data-test="post-inventory-document"]')).toHaveLength(1)
    expect(wrapper.findAll('[data-test="view-inventory-document"]')).toHaveLength(2)
  })

  it('按单据类型、状态和业务日期查询', async () => {
    const { wrapper } = await mountDocuments()

    await wrapper.find('input[name="inventory-document-keyword"]').setValue('INV')
    await setSelectValue(wrapper, 'inventory-document-type-filter', 'OPENING')
    await setSelectValue(wrapper, 'inventory-document-status-filter', 'DRAFT')
    await wrapper.find('input[name="inventory-document-date-from"]').setValue('2026-07-01')
    await wrapper.find('input[name="inventory-document-date-to"]').setValue('2026-07-03')
    await wrapper.find('[data-test="search-inventory-documents"]').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.documents.list).toHaveBeenLastCalledWith({
      keyword: 'INV',
      documentType: 'OPENING',
      status: 'DRAFT',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-03',
      page: 1,
      pageSize: 10,
    })
  })

  it('过账前二次确认，失败时错误可见且列表不被清空', async () => {
    inventoryApiMock.documents.post.mockRejectedValueOnce(new Error('库存不足，调减后库存不能小于 0'))
    const { wrapper } = await mountDocuments()

    await wrapper.find('[data-test="post-inventory-document"]').trigger('click')
    await flushPromises()

    expect(confirmActionMock).toHaveBeenCalledWith(expect.stringContaining('会影响库存余额且不可撤销'))
    expect(inventoryApiMock.documents.post).toHaveBeenCalledWith(1)
    expect(wrapper.text()).toContain('库存不足，调减后库存不能小于 0')
    expect(wrapper.text()).toContain('INV-OPEN-001')
  })
})
