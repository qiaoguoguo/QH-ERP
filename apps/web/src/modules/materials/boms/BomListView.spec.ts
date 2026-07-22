import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Plugin } from 'vue'
import type { PageResult } from '../../../shared/api/accountPermissionApi'
import type { BomDetailRecord, BomSummaryRecord } from '../../../shared/api/bomApi'
import type { MaterialRecord, UnitRecord } from '../../../shared/api/masterDataApi'
import { installElementPlus } from '../../../elementPlus'
import { useAuthStore } from '../../../stores/authStore'
import BomListView from './BomListView.vue'

const bomApiMock = vi.hoisted(() => ({
  list: vi.fn(),
  get: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  copy: vi.fn(),
  enable: vi.fn(),
  disable: vi.fn(),
  materialCandidates: vi.fn(),
  unitCandidates: vi.fn(),
  engineeringChanges: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    apply: vi.fn(),
    cancel: vi.fn(),
    sourceBomCandidates: vi.fn(),
    targetBomCandidates: vi.fn(),
  },
  substitutes: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    enable: vi.fn(),
    disable: vi.fn(),
    materialCandidates: vi.fn(),
    bomCandidates: vi.fn(),
  },
}))

const masterDataApiMock = vi.hoisted(() => ({
  materials: {
    list: vi.fn(),
  },
  units: {
    list: vi.fn(),
  },
  codingRules: {
    generate: vi.fn(),
  },
}))

const documentPlatformApiMock = vi.hoisted(() => ({
  approvals: {
    submitBomEcoApplication: vi.fn(),
  },
  attachments: {
    list: vi.fn(),
    upload: vi.fn(),
    download: vi.fn(),
    delete: vi.fn(),
  },
  importTemplates: {
    downloadBomDrafts: vi.fn(),
  },
  imports: {
    uploadBomDraft: vi.fn(),
  },
  exports: {
    createBomDraft: vi.fn(),
  },
  printTemplates: {
    list: vi.fn(),
  },
  printPreviews: {
    get: vi.fn(),
  },
  printTasks: {
    create: vi.fn(),
  },
}))

vi.mock('../../../shared/api/bomApi', () => ({
  bomApi: bomApiMock,
}))

vi.mock('../../../shared/api/masterDataApi', () => ({
  masterDataApi: masterDataApiMock,
}))

vi.mock('../../../shared/api/documentPlatformApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../shared/api/documentPlatformApi')>()),
  documentPlatformApi: documentPlatformApiMock,
}))

vi.mock('../../../shared/file/download', () => ({
  downloadFile: vi.fn(),
  triggerBrowserDownload: vi.fn(),
}))

const ECO_DRAFT_ACTION_COLUMN_MIN_WIDTH = 300

const finishedGood: MaterialRecord = {
  id: 1,
  code: 'FG-A',
  name: '成品A',
  materialType: 'FINISHED_GOOD',
  sourceType: 'SELF_MADE',
  trackingMethod: 'NONE',
  trackingMethodName: '不追踪',
  categoryId: 1,
  unitId: 1,
  unitName: '件',
  status: 'ENABLED',
}
const rawMaterial: MaterialRecord = {
  id: 2,
  code: 'RM-STEEL',
  name: '冷轧钢板',
  materialType: 'RAW_MATERIAL',
  sourceType: 'PURCHASED',
  trackingMethod: 'NONE',
  trackingMethodName: '不追踪',
  categoryId: 2,
  unitId: 2,
  unitName: '千克',
  status: 'ENABLED',
}
const unitEach: UnitRecord = {
  id: 1,
  code: 'PCS',
  name: '件',
  precisionScale: 0,
  sortOrder: 1,
  status: 'ENABLED',
}
const unitKg: UnitRecord = {
  id: 2,
  code: 'KG',
  name: '千克',
  precisionScale: 2,
  sortOrder: 2,
  status: 'ENABLED',
}
const draftBom: BomSummaryRecord = {
  id: 1,
  bomCode: 'BOM-FG-A',
  parentMaterialId: 1,
  parentMaterialCode: 'FG-A',
  parentMaterialName: '成品A',
  versionCode: 'V1.0',
  name: '成品A标准 BOM',
  baseQuantity: '1.0000',
  baseUnitId: 1,
  baseUnitName: '件',
  status: 'DRAFT',
  effectiveFrom: '2026-07-01',
  effectiveTo: null,
  itemCount: 1,
  updatedAt: '2026-07-03T05:00:00+08:00',
  version: 3,
}
const enabledBom: BomSummaryRecord = {
  ...draftBom,
  id: 2,
  bomCode: 'BOM-FG-A-V2',
  versionCode: 'V2.0',
  status: 'ENABLED',
}
const draftDetail: BomDetailRecord = {
  ...draftBom,
  items: [
    {
      id: 10,
      lineNo: 10,
      childMaterialId: 2,
      childMaterialCode: 'RM-STEEL',
      childMaterialName: '冷轧钢板',
      childMaterialType: 'RAW_MATERIAL',
      businessUnitId: 2,
      businessUnitName: '千克',
      businessQuantity: '2.5000',
      baseUnitId: 2,
      baseUnitName: '千克',
      baseQuantity: '2.5000',
      conversionRateSnapshot: '1.0000',
      quantityScaleSnapshot: 4,
      roundingModeSnapshot: 'HALF_UP',
      quantityBasis: 'BASE_UNIT',
      lossRate: '0.0200',
    },
  ],
  historyRelations: [],
}
const enabledDetail: BomDetailRecord = {
  ...draftDetail,
  ...enabledBom,
  items: draftDetail.items,
}
const futureBom: BomSummaryRecord = {
  ...enabledBom,
  id: 3,
  bomCode: 'BOM-FG-A-V3',
  versionCode: 'V3.0',
  effectiveFrom: '9999-01-01',
  effectiveTo: null,
}
const expiredBom: BomSummaryRecord = {
  ...enabledBom,
  id: 4,
  bomCode: 'BOM-FG-A-V0',
  versionCode: 'V0.9',
  effectiveFrom: null,
  effectiveTo: '0001-01-01',
}
const ecoRecord = {
  id: 100,
  ecoNo: 'ECO-202607-0001',
  sourceBomId: 2,
  sourceBomCode: 'BOM-FG-A',
  sourceVersionCode: 'V1.0',
  targetBomId: 1,
  targetBomCode: 'BOM-FG-A-V2',
  targetVersionCode: 'V2.0',
  parentMaterialId: 1,
  parentMaterialCode: 'FG-A',
  parentMaterialName: '成品A',
  effectiveFrom: '2026-07-13',
  effectiveTo: null,
  changeReason: '材料优化',
  impactScope: '后续生产',
  changeSummary: '替换冷轧钢板规格',
  status: 'DRAFT',
  appliedBy: null,
  appliedAt: null,
  createdAt: '2026-07-13T09:00:00+08:00',
  updatedAt: '2026-07-13T09:00:00+08:00',
  version: 2,
}
const substituteRecord = {
  id: 200,
  mainMaterialId: 2,
  mainMaterialCode: 'RM-STEEL',
  mainMaterialName: '冷轧钢板',
  substituteMaterialId: 3,
  substituteMaterialCode: 'RM-STEEL-B',
  substituteMaterialName: '镀锌钢板',
  scopeType: 'BOM',
  scopeId: 1,
  scopeCode: 'BOM-FG-A',
  scopeName: '成品A标准 BOM',
  priority: 1,
  substituteRate: '1.0000',
  effectiveFrom: '2026-07-13',
  effectiveTo: null,
  status: 'ENABLED',
  remark: '人工识别替代',
  updatedAt: '2026-07-13T09:30:00+08:00',
  version: 4,
}
const bomPage: PageResult<BomSummaryRecord> = {
  items: [draftBom, enabledBom],
  page: 1,
  pageSize: 10,
  total: 2,
  totalPages: 1,
}
const emptyBomPage: PageResult<BomSummaryRecord> = {
  items: [],
  page: 1,
  pageSize: 10,
  total: 0,
  totalPages: 0,
}

function mountBoms(
  permissions = [
    'material:bom:view',
    'material:bom:create',
    'material:bom:update',
    'material:bom:copy',
    'material:bom:enable',
    'material:bom:disable',
    'material:bom-eco:view',
    'material:bom-eco:create',
    'material:bom-eco:update',
    'material:bom-eco:apply',
    'material:bom-eco:cancel',
    'material:substitute:view',
    'material:substitute:create',
    'material:substitute:update',
    'material:substitute:enable',
    'material:substitute:disable',
  ],
  elementPlusPlugin: Plugin = ElementPlus,
) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })

  return mount(BomListView, {
    global: {
      plugins: [pinia, elementPlusPlugin],
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

function getBomVersionTableColumnProps(wrapper: VueWrapper) {
  const tables = wrapper.findAllComponents({ name: 'ElTable' })
  expect(tables.length).toBeGreaterThan(0)
  return tables[0].findAllComponents({ name: 'ElTableColumn' }).map((column) => column.props() as Record<string, unknown>)
}

function getActiveTableColumnProps(wrapper: VueWrapper) {
  const tables = wrapper.findAllComponents({ name: 'ElTable' })
  expect(tables.length).toBe(1)
  return tables[0].findAllComponents({ name: 'ElTableColumn' }).map((column) => column.props() as Record<string, unknown>)
}

async function fillValidBomForm(wrapper: VueWrapper) {
  await wrapper.find('input[name="bom-code"]').setValue('BOM-FG-A')
  await wrapper.find('input[name="bom-version-code"]').setValue('V1.0')
  await wrapper.find('input[name="bom-name"]').setValue('成品A标准 BOM')
  await wrapper.find('input[name="bom-base-quantity"]').setValue('1')
  await setSelectValue(wrapper, 'bom-parent-material-id', 1)
  await setSelectValue(wrapper, 'bom-base-unit-id', 1)
  await setSelectValue(wrapper, 'bom-line-child-material-id-0', 2)
  await setSelectValue(wrapper, 'bom-line-unit-id-0', 2)
  await wrapper.find('input[name="bom-line-quantity-0"]').setValue('2.5')
  await wrapper.find('input[name="bom-line-loss-rate-0"]').setValue('0.02')
}

describe('BOM 管理页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    bomApiMock.list.mockResolvedValue(bomPage)
    bomApiMock.get.mockResolvedValue(draftDetail)
    bomApiMock.create.mockResolvedValue(draftDetail)
    bomApiMock.update.mockResolvedValue(draftDetail)
    bomApiMock.copy.mockResolvedValue({ ...draftDetail, id: 3, bomCode: 'BOM-FG-A-V11', versionCode: 'V1.1' })
    bomApiMock.enable.mockResolvedValue({ ...draftDetail, status: 'ENABLED' })
    bomApiMock.disable.mockResolvedValue({ ...draftDetail, status: 'DISABLED' })
    bomApiMock.materialCandidates.mockResolvedValue({
      items: [
        { id: 1, code: 'FG-A', name: '成品A', status: 'ENABLED', summary: '成品' },
        { id: 2, code: 'RM-STEEL', name: '冷轧钢板', status: 'ENABLED', summary: '原材料' },
      ],
      selectedItems: [],
      page: 1,
      pageSize: 20,
      total: 2,
      totalPages: 1,
    })
    bomApiMock.unitCandidates.mockResolvedValue({
      items: [
        { id: 1, code: 'PCS', name: '件', status: 'ENABLED' },
        { id: 2, code: 'KG', name: '千克', status: 'ENABLED' },
      ],
      selectedItems: [],
      page: 1,
      pageSize: 20,
      total: 2,
      totalPages: 1,
    })
    bomApiMock.engineeringChanges.list.mockResolvedValue({ items: [ecoRecord], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    bomApiMock.engineeringChanges.get.mockResolvedValue(ecoRecord)
    bomApiMock.engineeringChanges.create.mockResolvedValue(ecoRecord)
    bomApiMock.engineeringChanges.update.mockResolvedValue(ecoRecord)
    bomApiMock.engineeringChanges.apply.mockResolvedValue({
      ...ecoRecord,
      status: 'APPLIED',
      appliedAt: '2026-07-13T10:00:00+08:00',
      appliedBy: 'admin',
      sourceBomBefore: { bomCode: 'BOM-FG-A', versionCode: 'V1.0', status: 'ENABLED', effectiveFrom: '2026-07-01', effectiveTo: null },
      sourceBomAfter: { bomCode: 'BOM-FG-A', versionCode: 'V1.0', status: 'DISABLED', effectiveFrom: '2026-07-01', effectiveTo: '2026-07-12' },
      targetBomBefore: { bomCode: 'BOM-FG-A-V2', versionCode: 'V2.0', status: 'DRAFT', effectiveFrom: '2026-07-13', effectiveTo: null },
      targetBomAfter: { bomCode: 'BOM-FG-A-V2', versionCode: 'V2.0', status: 'ENABLED', effectiveFrom: '2026-07-13', effectiveTo: null },
      version: 3,
    })
    bomApiMock.engineeringChanges.cancel.mockResolvedValue({ ...ecoRecord, status: 'CANCELLED', version: 3 })
    bomApiMock.engineeringChanges.sourceBomCandidates.mockResolvedValue({
      items: [{ id: 2, code: 'BOM-FG-A', name: 'V1.0', status: 'ENABLED', summary: '成品A' }],
      selectedItems: [],
      page: 1,
      pageSize: 20,
      total: 1,
      totalPages: 1,
    })
    bomApiMock.engineeringChanges.targetBomCandidates.mockResolvedValue({
      items: [{ id: 1, code: 'BOM-FG-A-V2', name: 'V2.0', status: 'DRAFT', summary: '成品A' }],
      selectedItems: [],
      page: 1,
      pageSize: 20,
      total: 1,
      totalPages: 1,
    })
    bomApiMock.substitutes.list.mockResolvedValue({ items: [substituteRecord], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    bomApiMock.substitutes.get.mockResolvedValue(substituteRecord)
    bomApiMock.substitutes.create.mockResolvedValue(substituteRecord)
    bomApiMock.substitutes.update.mockResolvedValue(substituteRecord)
    bomApiMock.substitutes.enable.mockResolvedValue({ ...substituteRecord, status: 'ENABLED', version: 5 })
    bomApiMock.substitutes.disable.mockResolvedValue({ ...substituteRecord, status: 'DISABLED', version: 5 })
    bomApiMock.substitutes.materialCandidates.mockResolvedValue({
      items: [
        { id: 2, code: 'RM-STEEL', name: '冷轧钢板', status: 'ENABLED' },
        { id: 3, code: 'RM-STEEL-B', name: '镀锌钢板', status: 'ENABLED' },
      ],
      selectedItems: [],
      page: 1,
      pageSize: 20,
      total: 2,
      totalPages: 1,
    })
    bomApiMock.substitutes.bomCandidates.mockResolvedValue({
      items: [{ id: 1, code: 'BOM-FG-A', name: 'V1.0', status: 'ENABLED', summary: '成品A' }],
      selectedItems: [],
      page: 1,
      pageSize: 20,
      total: 1,
      totalPages: 1,
    })
    masterDataApiMock.codingRules.generate.mockResolvedValue({
      objectType: 'BOM_ECO',
      ruleId: 3,
      generatedCode: 'ECO-202607-0002',
      generatedAt: '2026-07-13T10:30:00+08:00',
    })
    documentPlatformApiMock.approvals.submitBomEcoApplication.mockResolvedValue({
      id: 901,
      status: 'SUBMITTED',
      version: 1,
    })
    documentPlatformApiMock.attachments.list.mockResolvedValue([])
    documentPlatformApiMock.importTemplates.downloadBomDrafts.mockResolvedValue({
      blob: new Blob(['template']),
      fileName: 'BOM草稿模板.xlsx',
    })
    documentPlatformApiMock.imports.uploadBomDraft.mockResolvedValue({
      id: 93,
      taskNo: 'TASK-BOM-IMPORT',
      taskType: 'BOM_DRAFT_IMPORT',
      status: 'QUEUED',
      version: 1,
    })
    documentPlatformApiMock.exports.createBomDraft.mockResolvedValue({
      id: 94,
      taskNo: 'TASK-BOM-EXPORT',
      taskType: 'BOM_DRAFT_EXPORT',
      status: 'QUEUED',
      version: 1,
    })
    documentPlatformApiMock.printTemplates.list.mockResolvedValue([
      {
        templateCode: 'BOM_ECO_APPLICATION_APPROVAL_V1',
        name: 'BOM ECO 应用审批单',
        sceneCode: 'BOM_ECO_APPLICATION',
        templateVersion: 1,
      },
    ])
    masterDataApiMock.materials.list.mockResolvedValue({
      items: [finishedGood, rawMaterial],
      page: 1,
      pageSize: 100,
      total: 2,
      totalPages: 1,
    })
    masterDataApiMock.units.list.mockResolvedValue({
      items: [unitEach, unitKg],
      page: 1,
      pageSize: 100,
      total: 2,
      totalPages: 1,
    })
  })

  it('加载列表后显示 BOM 编码、父项物料、版本和状态', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    expect(wrapper.text()).toContain('BOM-FG-A')
    expect(wrapper.text()).toContain('BOM 版本')
    expect(wrapper.text()).toContain('工程变更')
    expect(wrapper.text()).toContain('替代料')
    expect(wrapper.text()).toContain('成品A')
    expect(wrapper.text()).toContain('V1.0')
    expect(wrapper.text()).toContain('草稿')
  })

  it('BOM 版本表格优先展示双状态并锁定桌面列结构', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    const columns = getBomVersionTableColumnProps(wrapper)

    expect(columns.map((column) => column.label)).toEqual([
      'BOM 编码',
      '状态',
      '时效状态',
      '有效期',
      '版本',
      '父项物料',
      '名称',
      '基准数量',
      '单位',
      '明细数',
      '更新时间',
      '操作',
    ])
    expect(columns.slice(0, 4).map((column) => column.label)).toEqual(['BOM 编码', '状态', '时效状态', '有效期'])
    expect(columns.some((column) => column.fixed === 'left')).toBe(false)

    expect(Number(columns[0].width)).toBe(170)
    expect(Number(columns[1].width)).toBe(90)
    expect(Number(columns[2].width)).toBe(110)
    expect(Number(columns[3].width)).toBe(160)
    expect(Number(columns[4].width)).toBe(90)
    expect(Number(columns[5].width)).toBe(190)
    expect(Number(columns[6].width)).toBe(180)
    expect(Number(columns[7].width)).toBe(100)
    expect(Number(columns[8].width)).toBe(120)
    expect(Number(columns[9].width)).toBe(80)
    expect(Number(columns[10].width)).toBe(150)
    expect(columns[11].fixed).not.toBe('right')
    expect(Number(columns[11].minWidth)).toBeGreaterThanOrEqual(320)
    expect(Number(columns[11].minWidth)).toBeLessThanOrEqual(360)

    for (const index of [0, 4, 5, 6, 8]) {
      expect(columns[index].showOverflowTooltip).toBe(true)
    }
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.text()).toContain('当前有效')
  })

  it('工程变更子表不固定操作列且保留状态和关键字段', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    await wrapper.find('[data-test="bom-tab-eco"]').trigger('click')
    await flushPromises()

    const columns = getActiveTableColumnProps(wrapper)

    expect(columns.map((column) => column.label)).toEqual([
      '变更编号',
      '来源 BOM',
      '目标 BOM',
      '父项物料',
      '生效日期',
      '变更摘要',
      '状态',
      '应用人',
      '应用时间',
      '操作',
    ])
    expect(columns.some((column) => column.label === '状态')).toBe(true)
    expect(columns.at(-1)?.label).toBe('操作')
    expect(columns.at(-1)?.fixed).not.toBe('right')
    expect(Number(columns.at(-1)?.minWidth)).toBeGreaterThanOrEqual(ECO_DRAFT_ACTION_COLUMN_MIN_WIDTH)
    const rowActionLabels = wrapper.findAllComponents({ name: 'ElButton' })
      .map((button) => button.text())
      .filter((label) => ['详情', '编辑', '提交应用审批', '取消'].includes(label))
    expect(rowActionLabels).toEqual(['详情', '编辑', '提交应用审批', '取消'])
    expect(wrapper.find('[data-test="edit-bom-eco"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="apply-bom-eco"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="cancel-bom-eco"]').exists()).toBe(true)
  })

  it('替代料子表不固定操作列且保留状态、关键字段和行操作', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    await wrapper.find('[data-test="bom-tab-substitutes"]').trigger('click')
    await flushPromises()

    const columns = getActiveTableColumnProps(wrapper)

    expect(columns.map((column) => column.label)).toEqual([
      '主物料',
      '替代物料',
      '适用范围',
      '优先级',
      '替代比例',
      '有效期',
      '状态',
      '操作',
    ])
    expect(columns.slice(0, 3).map((column) => column.label)).toEqual(['主物料', '替代物料', '适用范围'])
    expect(columns[6].label).toBe('状态')
    expect(columns.at(-1)?.label).toBe('操作')
    expect(columns.at(-1)?.fixed).not.toBe('right')
    expect(Number(columns.at(-1)?.minWidth)).toBeGreaterThanOrEqual(210)
    expect(wrapper.find('[data-test="edit-substitute"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="disable-substitute"]').exists()).toBe(true)
  })

  it('BOM 列表和详情同时展示发布状态与时效状态，并支持有效日期筛选', async () => {
    bomApiMock.list.mockResolvedValueOnce({
      items: [enabledBom, futureBom, expiredBom],
      page: 1,
      pageSize: 10,
      total: 3,
      totalPages: 1,
    })
    bomApiMock.get.mockResolvedValueOnce({
      ...enabledDetail,
      effectiveFrom: null,
      effectiveTo: null,
    })
    const wrapper = mountBoms()
    await flushPromises()

    expect(wrapper.text()).toContain('已发布')
    expect(wrapper.text()).toContain('当前有效')
    expect(wrapper.text()).toContain('未来生效')
    expect(wrapper.text()).toContain('历史失效')

    await wrapper.find('input[name="bom-effective-date"]').setValue('2026-07-13')
    await setSelectValue(wrapper, 'filter-bom-include-history', true)
    await wrapper.find('[data-test="search-bom"]').trigger('click')
    await flushPromises()

    expect(bomApiMock.list).toHaveBeenLastCalledWith({
      keyword: '',
      status: undefined,
      parentMaterialId: undefined,
      effectiveDate: '2026-07-13',
      includeHistory: true,
      page: 1,
      pageSize: 10,
    })

    await wrapper.findAll('[data-test="view-bom"]')[0].trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('发布状态')
    expect(wrapper.text()).toContain('时效状态')
    expect(wrapper.text()).toContain('当前有效')
  })

  it('生产入口组件注册下有查看权限时渲染并切换 BOM 版本、工程变更和替代料页签', async () => {
    const wrapper = mountBoms([
      'material:bom:view',
      'material:bom-eco:view',
      'material:substitute:view',
    ], installElementPlus)
    await flushPromises()

    expect(wrapper.find('[data-test="bom-tab-versions"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="bom-tab-eco"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="bom-tab-substitutes"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('BOM-FG-A')

    await wrapper.find('[data-test="bom-tab-eco"]').trigger('click')
    await flushPromises()
    expect(bomApiMock.engineeringChanges.list).toHaveBeenCalledWith(expect.objectContaining({
      page: 1,
      pageSize: 10,
    }))
    expect(wrapper.text()).toContain('ECO-202607-0001')
    expect(wrapper.text()).toContain('替换冷轧钢板规格')

    await wrapper.find('[data-test="bom-tab-substitutes"]').trigger('click')
    await flushPromises()
    expect(bomApiMock.substitutes.list).toHaveBeenCalledWith(expect.objectContaining({
      page: 1,
      pageSize: 10,
    }))
    expect(wrapper.text()).toContain('冷轧钢板')
    expect(wrapper.text()).toContain('镀锌钢板')
  })

  it('点击查询会按关键词、状态和父项物料调用列表接口', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    expect(bomApiMock.list).toHaveBeenLastCalledWith({
      keyword: '',
      status: undefined,
      parentMaterialId: undefined,
      includeHistory: false,
      page: 1,
      pageSize: 10,
    })

    await wrapper.find('input[name="bom-keyword"]').setValue('成品A')
    await setSelectValue(wrapper, 'filter-bom-status', 'DRAFT')
    await setSelectValue(wrapper, 'filter-bom-parent-material-id', 1)
    await wrapper.find('[data-test="search-bom"]').trigger('click')
    await flushPromises()

    const calls = bomApiMock.list.mock.calls
    expect(calls[calls.length - 1][0]).toEqual({
      keyword: '成品A',
      status: 'DRAFT',
      parentMaterialId: 1,
      includeHistory: false,
      page: 1,
      pageSize: 10,
    })
  })

  it('无数据时显示空状态', async () => {
    bomApiMock.list.mockResolvedValue(emptyBomPage)
    const wrapper = mountBoms()
    await flushPromises()

    expect(wrapper.text()).toContain('暂无 BOM 数据')
  })

  it('有创建权限时显示新建按钮，无创建权限时隐藏', async () => {
    const wrapper = mountBoms()
    await flushPromises()
    expect(wrapper.find('[data-test="create-bom"]').exists()).toBe(true)

    const readonlyWrapper = mountBoms(['material:bom:view'])
    await flushPromises()
    expect(readonlyWrapper.find('[data-test="create-bom"]').exists()).toBe(false)
  })

  it('BOM 表单和筛选使用候选分页接口并保留禁用原因', async () => {
    bomApiMock.materialCandidates.mockResolvedValue({
      items: [{ id: 1, code: 'FG-A', name: '成品A', status: 'ENABLED', disabled: true, disabledReason: '已有启用版本' }],
      selectedItems: [{ id: 2, code: 'FG-B', name: '成品B', status: 'ENABLED' }],
      page: 1,
      pageSize: 20,
      total: 2,
      totalPages: 1,
    })
    const wrapper = mountBoms()
    await flushPromises()

    expect(masterDataApiMock.materials.list).not.toHaveBeenCalled()
    expect(masterDataApiMock.units.list).not.toHaveBeenCalled()
    expect(bomApiMock.materialCandidates).toHaveBeenCalledWith(expect.objectContaining({
      page: 1,
      pageSize: 20,
      selectedIds: [],
    }))
    expect(bomApiMock.unitCandidates).toHaveBeenCalledWith(expect.objectContaining({
      page: 1,
      pageSize: 20,
      selectedIds: [],
    }))

    await wrapper.find('[data-test="create-bom"]').trigger('click')
    await flushPromises()
    const disabledOption = wrapper.findAllComponents({ name: 'ElOption' }).find((option) => option.props('value') === 1)
    expect(disabledOption?.props('disabled')).toBe(true)
    const parentSelect = wrapper.findComponent('[data-test="bom-parent-material-id"]') as VueWrapper
    const loadParentCandidates = (parentSelect.props() as Record<string, unknown>).remoteMethod as (keyword: string) => void
    loadParentCandidates('成品')
    await flushPromises()

    expect(bomApiMock.materialCandidates).toHaveBeenLastCalledWith(expect.objectContaining({
      keyword: '成品',
      page: 1,
      pageSize: 20,
    }))
  })

  it('草稿行显示编辑和启用按钮，启用行不显示编辑按钮', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    expect(wrapper.findAll('[data-test="edit-bom"]')).toHaveLength(1)
    expect(wrapper.find('[data-test="publish-bom"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('发布')
  })

  it('表单必填为空时展示错误并不提交', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    await wrapper.find('[data-test="create-bom"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="submit-bom"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('请完整填写 BOM 编码、版本、名称和父项物料')
    expect(bomApiMock.create).not.toHaveBeenCalled()
  })

  it('新增 BOM 弹窗为明细操作列提供响应式宽度和横向滚动容器', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    await wrapper.find('[data-test="create-bom"]').trigger('click')
    await flushPromises()

    const dialog = wrapper.findComponent({ name: 'ElDialog' })
    expect(dialog.props('width')).toBe('min(1120px, calc(100vw - 48px))')
    expect(wrapper.find('[data-test="bom-line-scroll"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="remove-bom-line"]').exists()).toBe(true)
  })

  it('明细用量为 0 时展示错误并不提交', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    await wrapper.find('[data-test="create-bom"]').trigger('click')
    await flushPromises()
    await fillValidBomForm(wrapper)
    await wrapper.find('input[name="bom-line-quantity-0"]').setValue('0')
    await wrapper.find('[data-test="submit-bom"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('第 10 行用量必须大于 0')
    expect(bomApiMock.create).not.toHaveBeenCalled()
  })

  it('重复子项时展示错误并不提交', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    await wrapper.find('[data-test="create-bom"]').trigger('click')
    await flushPromises()
    await fillValidBomForm(wrapper)
    await wrapper.find('[data-test="add-bom-line"]').trigger('click')
    await flushPromises()
    await setSelectValue(wrapper, 'bom-line-child-material-id-1', 2)
    await setSelectValue(wrapper, 'bom-line-unit-id-1', 2)
    await wrapper.find('input[name="bom-line-quantity-1"]').setValue('1')
    await wrapper.find('[data-test="submit-bom"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('BOM 明细子项不能重复')
    expect(bomApiMock.create).not.toHaveBeenCalled()
  })

  it('保存失败时表单仍然可见并恢复按钮状态', async () => {
    bomApiMock.create.mockRejectedValue(new Error('BOM 编码已存在'))
    const wrapper = mountBoms()
    await flushPromises()

    await wrapper.find('[data-test="create-bom"]').trigger('click')
    await flushPromises()
    await fillValidBomForm(wrapper)
    await wrapper.find('[data-test="submit-bom"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('BOM 编码已存在')
    expect(wrapper.find('[data-test="submit-bom"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.findComponent({ name: 'ElDialog' }).props('modelValue')).toBe(true)
  })

  it('点击复制会提交新编码和版本', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    await wrapper.find('[data-test="copy-bom"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="copy-bom-code"]').setValue('BOM-FG-A-V11')
    await wrapper.find('input[name="copy-bom-version-code"]').setValue('V1.1')
    await wrapper.find('input[name="copy-bom-name"]').setValue('成品A标准 BOM V1.1')
    await wrapper.find('[data-test="submit-copy-bom"]').trigger('click')
    await flushPromises()

    expect(bomApiMock.copy).toHaveBeenCalledWith(1, {
      bomCode: 'BOM-FG-A-V11',
      versionCode: 'V1.1',
      name: '成品A标准 BOM V1.1',
    })
  })

  it('点击启用和停用会调用对应接口', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    await wrapper.find('[data-test="publish-bom"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="disable-bom"]').trigger('click')
    await flushPromises()

    expect(bomApiMock.enable).toHaveBeenCalledWith(1, { version: 3 })
    expect(bomApiMock.disable).toHaveBeenCalledWith(1, { version: 3 })
  })

  it('已发布 BOM 详情显示只读提示且普通编辑不可见', async () => {
    bomApiMock.get.mockResolvedValueOnce(enabledDetail)
    const wrapper = mountBoms()
    await flushPromises()

    await wrapper.findAll('[data-test="view-bom"]')[1].trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('已发布 BOM 不允许普通编辑')
    expect(wrapper.text()).toContain('复制新版本')
    expect(wrapper.findAll('[data-test="edit-bom"]')).toHaveLength(1)
  })

  it('BOM 详情按真实工程变更关系展示历史版本信息', async () => {
    bomApiMock.get.mockResolvedValueOnce({
      ...enabledDetail,
      historyRelations: [
        {
          ecoId: 100,
          ecoNo: 'ECO-202607-0001',
          relationType: 'SOURCE',
          sourceBomId: 2,
          sourceBomCode: 'BOM-FG-A',
          sourceVersionCode: 'V1.0',
          targetBomId: 1,
          targetBomCode: 'BOM-FG-A-V2',
          targetVersionCode: 'V2.0',
          status: 'APPLIED',
          effectiveFrom: '2026-07-13',
          effectiveTo: null,
          appliedBy: 'admin',
          appliedAt: '2026-07-13T10:00:00+08:00',
        },
      ],
    })
    const wrapper = mountBoms()
    await flushPromises()

    await wrapper.findAll('[data-test="view-bom"]')[1].trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('ECO-202607-0001')
    expect(wrapper.text()).toContain('作为来源')
    expect(wrapper.text()).toContain('来源版本')
    expect(wrapper.text()).toContain('BOM-FG-A / V1.0')
    expect(wrapper.text()).toContain('目标版本')
    expect(wrapper.text()).toContain('BOM-FG-A-V2 / V2.0')
    expect(wrapper.text()).toContain('已应用')
    expect(wrapper.text()).toContain('2026-07-13')
    expect(wrapper.text()).toContain('admin')
    expect(wrapper.text()).not.toContain('已发布版本')
    expect(wrapper.text()).not.toContain('未发布版本')
  })

  it('工程变更页签展示来源目标 BOM，草稿应用改为提交审批且不调用直通应用接口', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    await wrapper.find('[data-test="bom-tab-eco"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('ECO-202607-0001')
    expect(wrapper.text()).toContain('替换冷轧钢板规格')

    await wrapper.find('[data-test="apply-bom-eco"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="confirm-bom-eco-approval"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('审批提交原因必填')
    expect(documentPlatformApiMock.approvals.submitBomEcoApplication).not.toHaveBeenCalled()

    await wrapper.find('[data-test="eco-approval-submit-reason"]').setValue('材料替代方案已确认')
    await wrapper.find('[data-test="confirm-bom-eco-approval"]').trigger('click')
    await flushPromises()

    expect(documentPlatformApiMock.approvals.submitBomEcoApplication).toHaveBeenCalledWith(100, {
      version: 2,
      reason: '材料替代方案已确认',
      idempotencyKey: expect.any(String),
    })
    expect(bomApiMock.engineeringChanges.apply).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('审批状态')
    expect(wrapper.text()).toContain('审批实例')
    expect(wrapper.text()).toContain('901')
    expect(wrapper.find('input[data-test="attachment-file"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('ECO 附件')
    expect(wrapper.text()).toContain('BOM ECO 应用审批单')
    expect(documentPlatformApiMock.printTemplates.list).toHaveBeenCalledWith({
      sceneCode: 'BOM_ECO_APPLICATION',
    })
  })

  it('创建工程变更时生成 ECO 编码并按候选上下文提交', async () => {
    const wrapper = mountBoms([
      'material:bom:view',
      'material:bom-eco:view',
      'material:bom-eco:create',
      'master:coding-rule:generate',
    ])
    await flushPromises()

    await wrapper.find('[data-test="create-eco-from-bom"]').trigger('click')
    await flushPromises()
    expect(bomApiMock.engineeringChanges.sourceBomCandidates).toHaveBeenCalledWith(expect.objectContaining({
      parentMaterialId: 1,
      selectedIds: [2],
    }))
    expect(bomApiMock.engineeringChanges.targetBomCandidates).toHaveBeenCalledWith(expect.objectContaining({
      sourceBomId: 2,
    }))

    const sourceSelect = wrapper.findComponent('[data-test="eco-source-bom-id"]') as VueWrapper
    const searchSourceBoms = (sourceSelect.props() as Record<string, unknown>).remoteMethod as (keyword: string) => void
    searchSourceBoms('BOM-FG')
    await flushPromises()
    expect(bomApiMock.engineeringChanges.sourceBomCandidates).toHaveBeenLastCalledWith(expect.objectContaining({
      keyword: 'BOM-FG',
      parentMaterialId: 1,
      selectedIds: [2],
    }))

    await wrapper.find('[data-test="generate-eco-code"]').trigger('click')
    await flushPromises()
    expect(masterDataApiMock.codingRules.generate).toHaveBeenCalledWith({ objectType: 'BOM_ECO' })
    expect((wrapper.find('input[name="eco-no"]').element as HTMLInputElement).value).toBe('ECO-202607-0002')

    bomApiMock.engineeringChanges.create.mockResolvedValueOnce({ ...ecoRecord, ecoNo: 'ECO-202607-0002' })
    await setSelectValue(wrapper, 'eco-target-bom-id', 1)
    await wrapper.find('input[name="eco-effective-from"]').setValue('2026-07-13')
    await wrapper.find('input[name="eco-change-reason"]').setValue('材料优化')
    await wrapper.find('input[name="eco-impact-scope"]').setValue('后续生产')
    await wrapper.find('textarea[name="eco-change-summary"]').setValue('替换冷轧钢板规格')
    await wrapper.find('[data-test="submit-bom-eco"]').trigger('click')
    await flushPromises()

    expect(bomApiMock.engineeringChanges.create).toHaveBeenCalledWith(expect.objectContaining({
      ecoNo: 'ECO-202607-0002',
      sourceBomId: 2,
      targetBomId: 1,
      effectiveFrom: '2026-07-13',
      changeReason: '材料优化',
    }))
  })

  it('ECO 保存返回编号与生成编号不一致时阻止静默关闭', async () => {
    bomApiMock.engineeringChanges.create.mockResolvedValueOnce({ ...ecoRecord, ecoNo: 'ECO-202607-9999' })
    const wrapper = mountBoms([
      'material:bom:view',
      'material:bom-eco:view',
      'material:bom-eco:create',
      'master:coding-rule:generate',
    ])
    await flushPromises()

    await wrapper.find('[data-test="create-eco-from-bom"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="generate-eco-code"]').trigger('click')
    await flushPromises()
    await setSelectValue(wrapper, 'eco-target-bom-id', 1)
    await wrapper.find('input[name="eco-effective-from"]').setValue('2026-07-13')
    await wrapper.find('input[name="eco-change-reason"]').setValue('材料优化')
    await wrapper.find('input[name="eco-impact-scope"]').setValue('后续生产')
    await wrapper.find('textarea[name="eco-change-summary"]').setValue('替换冷轧钢板规格')

    await wrapper.find('[data-test="submit-bom-eco"]').trigger('click')
    await flushPromises()

    expect(bomApiMock.engineeringChanges.create).toHaveBeenCalledWith(expect.objectContaining({
      ecoNo: 'ECO-202607-0002',
    }))
    expect(wrapper.text()).toContain('保存返回的工程变更编号与已生成编号不一致')
  })

  it('草稿工程变更可编辑并以当前 version 更新，取消必须输入原因', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    await wrapper.find('[data-test="bom-tab-eco"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="edit-bom-eco"]').trigger('click')
    await flushPromises()
    const sourceSelect = wrapper.findComponent('[data-test="eco-source-bom-id"]') as VueWrapper
    const searchSourceBoms = (sourceSelect.props() as Record<string, unknown>).remoteMethod as (keyword: string) => void
    searchSourceBoms('BOM-FG')
    await flushPromises()
    expect(bomApiMock.engineeringChanges.sourceBomCandidates).toHaveBeenLastCalledWith(expect.objectContaining({
      keyword: 'BOM-FG',
      parentMaterialId: 1,
      selectedIds: [2],
    }))

    await wrapper.find('input[name="eco-impact-scope"]').setValue('后续订单')
    await wrapper.find('[data-test="submit-bom-eco"]').trigger('click')
    await flushPromises()

    expect(bomApiMock.engineeringChanges.update).toHaveBeenCalledWith(100, expect.objectContaining({
      version: 2,
      impactScope: '后续订单',
    }))

    await wrapper.find('[data-test="cancel-bom-eco"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="submit-cancel-bom-eco"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('取消原因必填')
    expect(bomApiMock.engineeringChanges.cancel).not.toHaveBeenCalled()

    await wrapper.find('textarea[name="eco-cancel-reason"]').setValue('目标版本作废')
    await wrapper.find('[data-test="submit-cancel-bom-eco"]').trigger('click')
    await flushPromises()
    expect(bomApiMock.engineeringChanges.cancel).toHaveBeenCalledWith(100, {
      version: 2,
      reason: '目标版本作废',
    })
  })

  it('替代料页签展示主物料、替代物料、范围和停用动作', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    await wrapper.find('[data-test="bom-tab-substitutes"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('冷轧钢板')
    expect(wrapper.text()).toContain('镀锌钢板')
    expect(wrapper.text()).toContain('BOM 范围')
    await wrapper.find('[data-test="disable-substitute"]').trigger('click')
    await flushPromises()

    expect(bomApiMock.substitutes.disable).toHaveBeenCalledWith(200, { version: 4 })
  })

  it('替代料可按冻结字段筛选并支持编辑 version 更新', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    await wrapper.find('[data-test="bom-tab-substitutes"]').trigger('click')
    await flushPromises()
    await setSelectValue(wrapper, 'filter-substitute-main-material-id', 2)
    await setSelectValue(wrapper, 'filter-substitute-material-id', 3)
    await setSelectValue(wrapper, 'filter-substitute-scope-type', 'BOM')
    await setSelectValue(wrapper, 'filter-substitute-scope-id', 1)
    await wrapper.find('input[name="substitute-effective-date"]').setValue('2026-07-13')
    await wrapper.find('[data-test="search-substitute"]').trigger('click')
    await flushPromises()

    expect(bomApiMock.substitutes.list).toHaveBeenLastCalledWith({
      keyword: '',
      mainMaterialId: 2,
      substituteMaterialId: 3,
      scopeType: 'BOM',
      scopeId: 1,
      status: undefined,
      effectiveDate: '2026-07-13',
      page: 1,
      pageSize: 10,
    })

    await wrapper.find('[data-test="edit-substitute"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="substitute-rate"]').setValue('1.2500')
    await wrapper.find('[data-test="submit-substitute"]').trigger('click')
    await flushPromises()

    expect(bomApiMock.substitutes.update).toHaveBeenCalledWith(200, expect.objectContaining({
      version: 4,
      substituteRate: '1.2500',
    }))
    expect(bomApiMock.substitutes.bomCandidates).toHaveBeenCalledWith(expect.objectContaining({
      parentMaterialId: 2,
      selectedIds: [1],
    }))
  })

  it('BOM 版本页签增加草稿模板、CREATE/UPDATE_DRAFT 导入和草稿导出', async () => {
    const wrapper = mountBoms([
      'material:bom:view',
      'material:bom:import',
      'material:bom:export',
    ])
    await flushPromises()

    expect(wrapper.find('[data-test="download-bom-draft-template"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="open-bom-draft-import"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="export-bom-draft"]').exists()).toBe(true)

    await (wrapper.vm as unknown as { downloadBomDraftTemplate: () => Promise<void> }).downloadBomDraftTemplate()
    await flushPromises()
    expect(documentPlatformApiMock.importTemplates.downloadBomDrafts).toHaveBeenCalled()

    await (wrapper.vm as unknown as { exportBomDraft: (record: BomSummaryRecord) => Promise<void> }).exportBomDraft(draftBom)
    await flushPromises()
    expect(documentPlatformApiMock.exports.createBomDraft).toHaveBeenCalledWith(1, {
      idempotencyKey: expect.any(String),
    })

    ;(wrapper.vm as unknown as { openBomDraftImport: () => void }).openBomDraftImport()
    await flushPromises()
    expect(wrapper.text()).toContain('业务单位')
    expect(wrapper.text()).toContain('业务数量')
    expect(wrapper.text()).toContain('保留列，当前阶段必须留空；非空将校验失败')
    const file = new File(['xlsx'], 'bom-draft.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const fileInput = wrapper.find('input[data-test="bom-draft-import-file"]').element as HTMLInputElement
    Object.defineProperty(fileInput, 'files', { value: [file], configurable: true })
    await wrapper.find('input[data-test="bom-draft-import-file"]').trigger('change')
    await setSelectValue(wrapper, 'bom-draft-import-mode', 'UPDATE_DRAFT')
    await setSelectValue(wrapper, 'bom-draft-import-target', 1)
    await (wrapper.vm as unknown as { submitBomDraftImport: () => Promise<void> }).submitBomDraftImport()
    await flushPromises()

    expect(documentPlatformApiMock.imports.uploadBomDraft).toHaveBeenCalledWith({
      mode: 'UPDATE_DRAFT',
      bomId: 1,
      version: 3,
      file,
      idempotencyKey: expect.any(String),
    })
    expect(wrapper.text()).toContain('TASK-BOM-IMPORT')
  })

  it('BOM 版本页签提供 034 固定历史导入入口', async () => {
    const wrapper = mountBoms([
      'material:bom:view',
      'platform:history-import:view',
    ])
    await flushPromises()

    expectQueryFormsUseStandardGrid(wrapper)
    const entry = wrapper.find('[data-test="bom-history-import-entry"]')
    expect(entry.exists()).toBe(true)
    expect(entry.attributes('href')).toBe('/platform/history-imports?adapterCode=BOM_DRAFT_V1')
  })
})
