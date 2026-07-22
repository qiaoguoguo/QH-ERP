import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MaterialItemListView from './MaterialItemListView.vue'
import type { PageResult } from '../../../shared/api/accountPermissionApi'
import type { CategoryRecord, MaterialRecord, UnitRecord } from '../../../shared/api/masterDataApi'
import { useAuthStore } from '../../../stores/authStore'

const apiMock = vi.hoisted(() => ({
  materials: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    enable: vi.fn(),
    disable: vi.fn(),
  },
  codingRules: {
    generate: vi.fn(),
  },
  units: {
    list: vi.fn(),
  },
  categories: {
    list: vi.fn(),
  },
}))

const documentPlatformApiMock = vi.hoisted(() => ({
  importTemplates: {
    downloadMaterials: vi.fn(),
  },
  imports: {
    uploadMaterials: vi.fn(),
  },
  exports: {
    createMaterials: vi.fn(),
  },
}))

vi.mock('../../../shared/api/masterDataApi', () => ({
  masterDataApi: apiMock,
}))

vi.mock('../../../shared/api/documentPlatformApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../shared/api/documentPlatformApi')>()),
  documentPlatformApi: documentPlatformApiMock,
}))

vi.mock('../../../shared/file/download', () => ({
  downloadFile: vi.fn(),
  triggerBrowserDownload: vi.fn(),
}))

const category: CategoryRecord = {
  id: 1,
  code: 'RAW',
  name: '原材料',
  parentId: null,
  sortOrder: 1,
  status: 'ENABLED',
}
const unit: UnitRecord = {
  id: 1,
  code: 'KG',
  name: '千克',
  precisionScale: 2,
  sortOrder: 1,
  status: 'ENABLED',
}
const material: MaterialRecord = {
  id: 1,
  code: 'MAT-RAW-001',
  name: '冷轧钢板',
  specification: '1.5mm',
  materialType: 'RAW_MATERIAL',
  sourceType: 'PURCHASED',
  trackingMethod: 'BATCH',
  trackingMethodName: '批次管理',
  categoryId: 1,
  categoryName: '原材料',
  unitId: 1,
  unitName: '千克',
  businessUnitSummary: '卷、箱',
  baseUnitImmutableReason: '已有库存流水，基本单位不可修改',
  costCategory: 'DIRECT_MATERIAL',
  inventoryValuationCategory: 'VALUATED_MATERIAL',
  inventoryValueEnabled: true,
  projectCostEnabled: true,
  costAttributeCompleted: true,
  costRemark: '主材',
  version: 3,
  status: 'ENABLED',
}
const emptyMaterialPage: PageResult<MaterialRecord> = {
  items: [],
  page: 1,
  pageSize: 10,
  total: 0,
  totalPages: 0,
}
const categoryPage: PageResult<CategoryRecord> = {
  items: [category],
  page: 1,
  pageSize: 100,
  total: 1,
  totalPages: 1,
}
const unitPage: PageResult<UnitRecord> = {
  items: [unit],
  page: 1,
  pageSize: 100,
  total: 1,
  totalPages: 1,
}

function mountMaterials(permissions = [
  'master:material:view',
  'master:material:create',
  'master:material:update',
  'master:material-cost:view',
  'master:material-cost:update',
]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  return mount(MaterialItemListView, {
    global: {
      plugins: [pinia, ElementPlus],
    },
  })
}

async function setSelectValue(wrapper: VueWrapper, dataTest: string, value: unknown) {
  const select = wrapper.findComponent(`[data-test="${dataTest}"]`) as VueWrapper
  expect(select.exists()).toBe(true)
  select.vm.$emit('update:modelValue', value)
  await flushPromises()
}

function expectQueryFormsUseStandardGrid(wrapper: VueWrapper) {
  const queryForms = wrapper.findAllComponents({ name: 'ElForm' })
    .filter((form) => String(form.attributes('class') ?? '').split(/\s+/).includes('query-form'))
  expect(queryForms.length).toBeGreaterThan(0)
  queryForms.forEach((form) => {
    expect(form.props('inline')).not.toBe(true)
    expect(form.props('labelPosition')).toBe('top')
  })
}

function expectDefaultTableKeepsStatusScannable(wrapper: VueWrapper) {
  const columns = wrapper.findAllComponents({ name: 'ElTableColumn' }).map((column) => column.props() as Record<string, unknown>)
  expect(columns.slice(0, 5).map((column) => column.label)).toEqual([
    '编码',
    '名称',
    '状态',
    '规格型号',
    '物料类型',
  ])
  expect(columns.at(-1)?.label).toBe('操作')
  expect(columns.at(-1)?.fixed).not.toBe('right')
  expect(Number(columns.at(-1)?.minWidth)).toBeLessThanOrEqual(210)
}

async function fillValidMaterialForm(wrapper: VueWrapper) {
  await wrapper.find('input[name="material-code"]').setValue('MAT-RAW-001')
  await wrapper.find('input[name="material-name"]').setValue('冷轧钢板')
  await wrapper.find('input[name="material-specification"]').setValue('1.5mm')
  await setSelectValue(wrapper, 'material-type', 'RAW_MATERIAL')
  await setSelectValue(wrapper, 'material-source-type', 'PURCHASED')
  await setSelectValue(wrapper, 'material-tracking-method', 'BATCH')
  await setSelectValue(wrapper, 'material-category-id', '1')
  await setSelectValue(wrapper, 'material-unit-id', '1')
  await setSelectValue(wrapper, 'material-cost-category', 'DIRECT_MATERIAL')
  await setSelectValue(wrapper, 'material-inventory-valuation-category', 'VALUATED_MATERIAL')
  await setSelectValue(wrapper, 'material-status', 'ENABLED')
}

describe('物料档案页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMock.materials.list.mockResolvedValue(emptyMaterialPage)
    apiMock.materials.create.mockResolvedValue(material)
    apiMock.materials.update.mockResolvedValue(material)
    apiMock.materials.enable.mockResolvedValue(material)
    apiMock.materials.disable.mockResolvedValue(material)
    apiMock.codingRules.generate.mockResolvedValue({
      objectType: 'MATERIAL',
      ruleId: 1,
      generatedCode: 'MAT-202607-0001',
      generatedAt: '2026-07-13T10:00:00+08:00',
    })
    apiMock.units.list.mockResolvedValue(unitPage)
    apiMock.categories.list.mockResolvedValue(categoryPage)
    documentPlatformApiMock.importTemplates.downloadMaterials.mockResolvedValue({
      blob: new Blob(['template']),
      fileName: '物料新增模板.xlsx',
    })
    documentPlatformApiMock.imports.uploadMaterials.mockResolvedValue({
      id: 91,
      taskNo: 'TASK-001',
      taskType: 'MATERIAL_IMPORT',
      status: 'QUEUED',
      version: 1,
    })
    documentPlatformApiMock.exports.createMaterials.mockResolvedValue({
      id: 92,
      taskNo: 'TASK-002',
      taskType: 'MATERIAL_EXPORT',
      status: 'QUEUED',
      version: 1,
    })
  })

  it('新增物料时提交分类和单位等核心字段', async () => {
    const wrapper = mountMaterials()
    await flushPromises()

    await wrapper.find('[data-test="create-material"]').trigger('click')
    await flushPromises()
    await fillValidMaterialForm(wrapper)
    await wrapper.find('[data-test="submit-material"]').trigger('click')
    await flushPromises()

    expect(apiMock.materials.create).toHaveBeenCalledWith(expect.objectContaining({
      code: 'MAT-RAW-001',
      name: '冷轧钢板',
      materialType: 'RAW_MATERIAL',
      sourceType: 'PURCHASED',
      trackingMethod: 'BATCH',
      categoryId: 1,
      unitId: 1,
      costCategory: 'DIRECT_MATERIAL',
      inventoryValuationCategory: 'VALUATED_MATERIAL',
      inventoryValueEnabled: true,
      projectCostEnabled: true,
      status: 'ENABLED',
    }))
  })

  it('创建物料时可调用后端生成编码并只填充编码字段', async () => {
    const wrapper = mountMaterials([
      'master:material:view',
      'master:material:create',
      'master:material:update',
      'master:coding-rule:generate',
    ])
    await flushPromises()

    await wrapper.find('[data-test="create-material"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="generate-material-code"]').trigger('click')
    await flushPromises()

    expect(apiMock.codingRules.generate).toHaveBeenCalledWith({ objectType: 'MATERIAL' })
    expect((wrapper.find('input[name="material-code"]').element as HTMLInputElement).value).toBe('MAT-202607-0001')
    expect(apiMock.materials.create).not.toHaveBeenCalled()
  })

  it('没有创建权限时隐藏新增按钮', async () => {
    const wrapper = mountMaterials(['master:material:view', 'master:material:update'])
    await flushPromises()

    expect(wrapper.find('[data-test="create-material"]').exists()).toBe(false)
  })

  it('缺少分类或单位时不提交并展示校验提示', async () => {
    const wrapper = mountMaterials()
    await flushPromises()

    await wrapper.find('[data-test="create-material"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="material-code"]').setValue('MAT-RAW-001')
    await wrapper.find('input[name="material-name"]').setValue('冷轧钢板')
    await setSelectValue(wrapper, 'material-type', 'RAW_MATERIAL')
    await setSelectValue(wrapper, 'material-source-type', 'PURCHASED')
    await wrapper.find('[data-test="submit-material"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('分类和单位为必填')
    expect(wrapper.findComponent({ name: 'ElDialog' }).props('modelValue')).toBe(true)
    expect(apiMock.materials.create).not.toHaveBeenCalled()
  })

  it('详情抽屉展示物料分类、单位、类型和来源文案', async () => {
    apiMock.materials.list.mockResolvedValue({ items: [material], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    const wrapper = mountMaterials()
    await flushPromises()

    await wrapper.find('[data-test="view-material"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('冷轧钢板')
    expect(wrapper.text()).toContain('1.5mm')
    expect(wrapper.text()).toContain('原材料')
    expect(wrapper.text()).toContain('千克')
    expect(wrapper.text()).toContain('卷、箱')
    expect(wrapper.text()).toContain('直接材料')
    expect(wrapper.text()).toContain('计价物料')
    expect(wrapper.text()).toContain('参与库存价值')
    expect(wrapper.text()).toContain('允许进入项目成本')
    expect(wrapper.text()).toContain('外购')
    expect(wrapper.text()).toContain('批次管理')
  })

  it('编辑有业务事实的物料时基本单位字段禁用并显示锁定原因', async () => {
    apiMock.materials.list.mockResolvedValue({ items: [material], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    const wrapper = mountMaterials()
    await flushPromises()

    await wrapper.find('[data-test="edit-material"]').trigger('click')
    await flushPromises()

    const unitSelect = wrapper.findComponent('[data-test="material-unit-id"]') as VueWrapper
    expect((unitSelect.props() as Record<string, unknown>).disabled).toBe(true)
    expect(wrapper.text()).toContain('已有库存流水，基本单位不可修改')
  })

  it('物料编辑弹窗和详情抽屉使用响应式宽度', async () => {
    apiMock.materials.list.mockResolvedValue({ items: [material], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    const wrapper = mountMaterials()
    await flushPromises()

    await wrapper.find('[data-test="create-material"]').trigger('click')
    await flushPromises()
    expect(wrapper.findComponent({ name: 'ElDialog' }).props('width')).toBe('min(640px, 96vw)')

    await wrapper.find('[data-test="view-material"]').trigger('click')
    await flushPromises()
    expect(wrapper.findComponent({ name: 'ElDrawer' }).props('size')).toBe('min(420px, 92vw)')
  })

  it('保存失败时保留弹窗并恢复按钮状态', async () => {
    apiMock.materials.create.mockRejectedValue(new Error('物料编码重复'))
    const wrapper = mountMaterials()
    await flushPromises()

    await wrapper.find('[data-test="create-material"]').trigger('click')
    await flushPromises()
    await fillValidMaterialForm(wrapper)
    await wrapper.find('[data-test="submit-material"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('物料编码重复')
    expect(wrapper.findComponent({ name: 'ElDialog' }).props('modelValue')).toBe(true)
    expect(wrapper.find('[data-test="submit-material"]').attributes('disabled')).toBeUndefined()
  })

  it('没有成本维护权限时编辑物料不提交成本字段', async () => {
    apiMock.materials.list.mockResolvedValue({ items: [material], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    const wrapper = mountMaterials([
      'master:material:view',
      'master:material:update',
    ])
    await flushPromises()

    await wrapper.find('[data-test="edit-material"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="material-name"]').setValue('冷轧钢板A')
    await wrapper.find('[data-test="submit-material"]').trigger('click')
    await flushPromises()

    expect(apiMock.materials.update).toHaveBeenCalled()
    const payload = apiMock.materials.update.mock.calls.at(-1)?.[1]
    expect(payload).not.toHaveProperty('costCategory')
    expect(payload).not.toHaveProperty('inventoryValuationCategory')
    expect(payload).not.toHaveProperty('inventoryValueEnabled')
    expect(payload).not.toHaveProperty('projectCostEnabled')
    expect(payload).not.toHaveProperty('costRemark')
  })

  it('启用物料成本分类未完善时不提交并展示稳定提示', async () => {
    const wrapper = mountMaterials()
    await flushPromises()

    await wrapper.find('[data-test="create-material"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="material-code"]').setValue('MAT-RAW-002')
    await wrapper.find('input[name="material-name"]').setValue('热轧钢板')
    await setSelectValue(wrapper, 'material-type', 'RAW_MATERIAL')
    await setSelectValue(wrapper, 'material-source-type', 'PURCHASED')
    await setSelectValue(wrapper, 'material-tracking-method', 'BATCH')
    await setSelectValue(wrapper, 'material-category-id', '1')
    await setSelectValue(wrapper, 'material-unit-id', '1')
    await setSelectValue(wrapper, 'material-status', 'ENABLED')
    await wrapper.find('[data-test="submit-material"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('启用物料必须维护完整成本分类和库存计价分类')
    expect(apiMock.materials.create).not.toHaveBeenCalled()
  })

  it('按分类、物料类型、来源和追踪方式筛选时不发送 unitId 查询字段', async () => {
    const wrapper = mountMaterials()
    await flushPromises()

    await wrapper.find('input[name="material-keyword"]').setValue('钢板')
    await setSelectValue(wrapper, 'filter-material-category-id', '1')
    await setSelectValue(wrapper, 'filter-material-type', 'RAW_MATERIAL')
    await setSelectValue(wrapper, 'filter-source-type', 'PURCHASED')
    await setSelectValue(wrapper, 'filter-tracking-method', 'BATCH')
    await wrapper.find('[data-test="search-material"]').trigger('click')
    await flushPromises()

    const calls = apiMock.materials.list.mock.calls
    const lastQuery = calls[calls.length - 1][0]
    expect(lastQuery).toEqual(expect.objectContaining({
      keyword: '钢板',
      status: undefined,
      page: 1,
      pageSize: 10,
      categoryId: 1,
      materialType: 'RAW_MATERIAL',
      sourceType: 'PURCHASED',
      trackingMethod: 'BATCH',
    }))
    expect(lastQuery).not.toHaveProperty('unitId')
  })

  it('物料页保留手工维护并增加模板下载、新增导入和当前筛选导出', async () => {
    const wrapper = mountMaterials([
      'master:material:view',
      'master:material:create',
      'master:material:update',
      'master:material:import',
      'master:material:export',
    ])
    await flushPromises()

    expect(wrapper.find('[data-test="create-material"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="download-material-template"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="open-material-import"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="export-materials"]').exists()).toBe(true)

    await (wrapper.vm as unknown as { downloadMaterialTemplate: () => Promise<void> }).downloadMaterialTemplate()
    await flushPromises()
    expect(documentPlatformApiMock.importTemplates.downloadMaterials).toHaveBeenCalled()

    await wrapper.find('input[name="material-keyword"]').setValue('钢板')
    await setSelectValue(wrapper, 'filter-material-status', 'ENABLED')
    await setSelectValue(wrapper, 'filter-material-type', 'RAW_MATERIAL')
    await (wrapper.vm as unknown as { exportMaterials: () => Promise<void> }).exportMaterials()
    await flushPromises()
    expect(documentPlatformApiMock.exports.createMaterials).toHaveBeenCalledWith({
      keyword: '钢板',
      status: 'ENABLED',
      categoryId: undefined,
      materialType: 'RAW_MATERIAL',
      sourceType: undefined,
      trackingMethod: undefined,
      idempotencyKey: expect.any(String),
    })

    ;(wrapper.vm as unknown as { openImport: () => void }).openImport()
    await flushPromises()
    const file = new File(['xlsx'], 'materials.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const fileInput = wrapper.find('input[data-test="material-import-file"]').element as HTMLInputElement
    Object.defineProperty(fileInput, 'files', { value: [file], configurable: true })
    await wrapper.find('input[data-test="material-import-file"]').trigger('change')
    await (wrapper.vm as unknown as { submitMaterialImport: () => Promise<void> }).submitMaterialImport()
    await flushPromises()

    expect(documentPlatformApiMock.imports.uploadMaterials).toHaveBeenCalledWith({
      file,
      idempotencyKey: expect.any(String),
    })
    expect(wrapper.text()).toContain('TASK-001')
  })

  it('034 主列表同时提供固定历史导入和可执行批量状态入口', async () => {
    apiMock.materials.list.mockResolvedValue({ items: [material], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    const wrapper = mountMaterials([
      'master:material:view',
      'platform:history-import:view',
      'platform:batch-tool:view',
    ])
    await flushPromises()

    expectQueryFormsUseStandardGrid(wrapper)
    expectDefaultTableKeepsStatusScannable(wrapper)
    expect(wrapper.find('[data-test="material-history-import-entry"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="material-batch-status-entry"]').exists()).toBe(true)
  })
})
