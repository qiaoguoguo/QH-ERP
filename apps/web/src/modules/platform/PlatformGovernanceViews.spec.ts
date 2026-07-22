import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Component } from 'vue'
import DataRepairListView from './dataRepairs/DataRepairListView.vue'
import DataRepairCreateView from './dataRepairs/DataRepairCreateView.vue'
import DataRepairDetailView from './dataRepairs/DataRepairDetailView.vue'
import HistoryImportListView from './historyImports/HistoryImportListView.vue'
import HistoryImportDetailView from './historyImports/HistoryImportDetailView.vue'
import DeliveryAssetsView from './deliveryAssets/DeliveryAssetsView.vue'
import BatchStatusToolPanel from './components/BatchStatusToolPanel.vue'
import DocumentTaskCenterView from './documentTasks/DocumentTaskCenterView.vue'
import { expectStandardDetailPage, expectStandardFormPage, expectStandardListPage } from '../../test/pageGovernanceAssertions'
import { useConfirmActionMock } from '../../test/setup'
import { useAuthStore } from '../../stores/authStore'

const platformGovernanceApiMock = vi.hoisted(() => ({
  dataRepairAdapters: { list: vi.fn() },
  dataRepairs: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    submit: vi.fn(),
    execute: vi.fn(),
    verify: vi.fn(),
    cancel: vi.fn(),
  },
  historyImportAdapters: {
    list: vi.fn(),
    downloadTemplate: vi.fn(),
  },
  historyImports: {
    list: vi.fn(),
    get: vi.fn(),
    upload: vi.fn(),
    confirm: vi.fn(),
    cancel: vi.fn(),
    errors: vi.fn(),
  },
  batchTools: {
    list: vi.fn(),
    preview: vi.fn(),
  },
  batchOperations: {
    get: vi.fn(),
    execute: vi.fn(),
  },
  deliveryAssets: { get: vi.fn() },
}))

const documentPlatformApiMock = vi.hoisted(() => ({
  documentTasks: {
    list: vi.fn(),
    get: vi.fn(),
    errors: vi.fn(),
    cancel: vi.fn(),
    download: vi.fn(),
  },
  imports: { confirm: vi.fn() },
  attachments: {
    list: vi.fn(),
    upload: vi.fn(),
    download: vi.fn(),
    delete: vi.fn(),
  },
}))

const masterDataApiMock = vi.hoisted(() => ({
  customers: { list: vi.fn() },
  suppliers: { list: vi.fn() },
  materials: { list: vi.fn() },
}))

vi.mock('../../shared/api/platformGovernanceApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/platformGovernanceApi')>()),
  platformGovernanceApi: platformGovernanceApiMock,
}))

vi.mock('../../shared/api/documentPlatformApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/documentPlatformApi')>()),
  documentPlatformApi: documentPlatformApiMock,
}))

vi.mock('../../shared/api/masterDataApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/masterDataApi')>()),
  masterDataApi: masterDataApiMock,
}))

vi.mock('../../shared/file/download', () => ({
  downloadFile: vi.fn(),
  triggerBrowserDownload: vi.fn(),
}))

const confirmActionMock = useConfirmActionMock()

function mountWithAuth(component: Component, permissions: string[], props: Record<string, unknown> = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  return mount(component, {
    props,
    global: {
      plugins: [pinia, ElementPlus],
      stubs: {
        RouterLink: {
          props: ['to'],
          template: '<a :data-to="to"><slot /></a>',
        },
      },
    },
  })
}

function buttonByTest(wrapper: ReturnType<typeof mountWithAuth>, testId: string) {
  const button = wrapper.findAllComponents({ name: 'ElButton' })
    .find((item) => item.attributes('data-test') === testId)
  expect(button?.exists()).toBe(true)
  return button!
}

function expectQueryFormsUseStandardGrid(wrapper: ReturnType<typeof mountWithAuth>) {
  const queryForms = wrapper.findAllComponents({ name: 'ElForm' })
    .filter((form) => String(form.attributes('class') ?? '').split(/\s+/).includes('query-form'))
  expect(queryForms.length).toBeGreaterThan(0)
  queryForms.forEach((form) => {
    expect(form.props('inline')).not.toBe(true)
    expect(form.props('labelPosition')).toBe('top')
  })
}

async function clickButtonByTest(wrapper: ReturnType<typeof mountWithAuth>, testId: string) {
  const button = buttonByTest(wrapper, testId)
  const onClick = (button.props() as Record<string, unknown>).onClick
  if (typeof onClick === 'function') {
    onClick(new MouseEvent('click'))
  } else {
    button.vm.$emit('click', new MouseEvent('click'))
  }
  await flushPromises()
}

async function setComponentModel(wrapper: ReturnType<typeof mountWithAuth>, testId: string, value: unknown) {
  const component = wrapper.findComponent(`[data-test="${testId}"]`)
  expect(component.exists()).toBe(true)
  ;(component as unknown as { vm: { $emit: (event: string, value: unknown) => void } }).vm.$emit('update:modelValue', value)
  await flushPromises()
}

const dataRepairDetail = {
  id: 501,
  requestNo: 'DR-202607-0001',
  adapterCode: 'MATERIAL_PROFILE_CORRECTION_V1',
  targetObjectType: 'MATERIAL',
  targetObjectId: 31,
  targetObjectNo: 'MAT-001',
  targetObjectSummary: '旧规格电机',
  targetObjectVersion: 7,
  reason: '历史引用后需留痕更正',
  riskSummary: '修正物料规格说明',
  status: 'READY_TO_EXECUTE',
  createdByUserId: 8,
  createdByUsername: '资料员',
  createdAt: '2026-07-21T10:00:00+08:00',
  submittedAt: '2026-07-21T10:10:00+08:00',
  version: 3,
  availableActions: ['EXECUTE', 'CANCEL'],
  beforeSummary: { specification: '旧规格', remark: '旧备注' },
  afterSummary: { specification: '新规格', remark: '新备注' },
  changes: [{ fieldName: 'specification', beforeValueSummary: '旧规格', afterValueSummary: '新规格' }],
  checks: [{
    checkType: 'PRECHECK',
    status: 'PASSED',
    code: null,
    message: '对象版本已固化',
    detail: { target: 'MAT-001' },
    createdAt: '2026-07-21T10:01:00+08:00',
  }],
  events: [{
    eventType: 'SUBMIT',
    operatorUsername: '资料员',
    statusBefore: 'DRAFT',
    statusAfter: 'PENDING_APPROVAL',
    detail: { comment: '提交审批' },
    createdAt: '2026-07-21T10:10:00+08:00',
  }],
  approvalSummary: { id: 70, status: 'APPROVED', version: 1, taskId: 701, taskVersion: 16 },
  auditSummary: { auditLogId: 90, summary: '已写入脱敏审计' },
  attachmentObjectType: 'DATA_REPAIR_REQUEST',
  attachmentObjectId: 501,
}

const materialCandidate = {
  id: 31,
  code: 'MAT-001',
  name: '旧规格电机',
  status: 'ENABLED',
  version: 7,
  materialType: 'RAW_MATERIAL',
  sourceType: 'PURCHASED',
  trackingMethod: 'NONE',
  trackingMethodName: '不追踪',
  categoryId: 1,
  unitId: 1,
}

const customerCandidate = {
  id: 41,
  code: 'CUS-001',
  name: '华南设备客户',
  status: 'ENABLED',
  version: 5,
}

const supplierCandidate = {
  id: 51,
  code: 'SUP-001',
  name: '华东五金供应商',
  status: 'ENABLED',
  version: 4,
}

const deliveryAssetCatalog = {
  stageCode: '034',
  environmentCode: '034-ISOLATED',
  generatedAt: '2026-07-21T12:00:00+08:00',
  manual: { version: '034-manual-v1', updatedAt: '2026-07-21T10:00:00+08:00' },
  demoData: { version: '034-demo-v1', status: 'NOT_VERIFIED', verifiedAt: null },
  historyImportAdapters: [
    { code: 'CUSTOMER_MASTER_V1', name: '客户历史导入', targetObjectType: 'CUSTOMER', status: 'ACTIVE', version: 1 },
    { code: 'SUPPLIER_MASTER_V1', name: '供应商历史导入', targetObjectType: 'SUPPLIER', status: 'ACTIVE', version: 1 },
    { code: 'MATERIAL_MASTER_V1', name: '物料历史导入', targetObjectType: 'MATERIAL', status: 'ACTIVE', version: 1 },
  ],
  dataRepairAdapters: [
    { code: 'CUSTOMER_PROFILE_CORRECTION_V1', name: '客户资料修复', targetObjectType: 'CUSTOMER', status: 'ACTIVE', version: 1 },
    { code: 'SUPPLIER_PROFILE_CORRECTION_V1', name: '供应商资料修复', targetObjectType: 'SUPPLIER', status: 'ACTIVE', version: 1 },
    { code: 'MATERIAL_PROFILE_CORRECTION_V1', name: '物料资料修复', targetObjectType: 'MATERIAL', status: 'ACTIVE', version: 1 },
    { code: 'BOM_PROFILE_CORRECTION_V1', name: 'BOM 资料修复', targetObjectType: 'BOM', status: 'ACTIVE', version: 1 },
    { code: 'SALES_PROJECT_PROFILE_CORRECTION_V1', name: '销售项目资料修复', targetObjectType: 'SALES_PROJECT', status: 'ACTIVE', version: 1 },
  ],
  batchTools: [
    { code: 'CUSTOMER_STATUS_CHANGE_V1', name: '客户状态批量变更', targetObjectType: 'CUSTOMER', actionCode: 'STATUS_CHANGE', status: 'ACTIVE', version: 1 },
    { code: 'SUPPLIER_STATUS_CHANGE_V1', name: '供应商状态批量变更', targetObjectType: 'SUPPLIER', actionCode: 'STATUS_CHANGE', status: 'ACTIVE', version: 1 },
    { code: 'MATERIAL_STATUS_CHANGE_V1', name: '物料状态批量变更', targetObjectType: 'MATERIAL', actionCode: 'STATUS_CHANGE', status: 'ACTIVE', version: 1 },
    { code: 'FIXED_PRINT_BATCH_V1', name: '固定打印批量工具', targetObjectType: 'DOCUMENT', actionCode: 'PRINT', status: 'ACTIVE', version: 1 },
  ],
  printTemplates: Array.from({ length: 14 }, (_, index) => ({
    templateCode: index === 0 ? 'SALES_ORDER_V1' : `FIXED_PRINT_TEMPLATE_${index + 1}`,
    sceneCode: index === 0 ? 'SALES_ORDER_PRINT' : `FIXED_PRINT_SCENE_${index + 1}`,
    name: index === 0 ? '销售订单' : `固定打印模板 ${index + 1}`,
    objectType: index === 0 ? 'SALES_ORDER' : `OBJECT_${index + 1}`,
    templateVersion: 1,
    status: 'ACTIVE',
  })),
  staticAssets: [
    { code: 'OPERATION_MANUAL', path: 'docs/manual/system-operation-manual.md', note: '操作手册只读目录引用' },
    { code: 'DEMO_DATA_TOOLS', path: 'tools/demo-data', note: '演示数据工具只读目录引用' },
  ],
}

const historyImportDetail = {
  id: 700,
  taskId: 601,
  taskNo: 'HI-202607-0001',
  adapterCode: 'CUSTOMER_MASTER_V1',
  adapterName: '客户历史导入',
  sourceFileName: '客户历史.xlsx',
  sourceSha256: 'sha256',
  templateCode: 'CUSTOMER_MASTER_V1_TEMPLATE',
  templateVersion: 1,
  status: 'READY_TO_COMMIT',
  stage: 'VALIDATE',
  progressPercent: 100,
  totalRows: 20,
  successRows: 20,
  failedRows: 0,
  createdByName: '管理员',
  createdAt: '2026-07-21T11:00:00+08:00',
  expiresAt: '2026-07-28T11:00:00+08:00',
  version: 4,
  availableActions: ['CONFIRM', 'CANCEL', 'ERRORS', 'DOWNLOAD'],
  validationSummary: { totalRows: 20, successRows: 20, failedRows: 0, summary: '预检通过' },
  errorSummary: { totalErrors: 0 },
  auditSummary: { auditLogId: 91, summary: '已记录导入预检' },
  relatedTaskId: 601,
}

const batchOperationDetail = {
  id: 701,
  operationNo: 'BOP20260721120000001',
  toolCode: 'CUSTOMER_STATUS_CHANGE_V1',
  targetObjectType: 'CUSTOMER',
  actionCode: 'STATUS_CHANGE',
  status: 'PRECHECKED',
  totalRows: 2,
  successRows: 0,
  failedRows: 0,
  errorMessage: null,
  createdByName: '管理员',
  executedByName: null,
  executedAt: null,
  createdAt: '2026-07-21T12:00:00+08:00',
  version: 1,
  availableActions: ['EXECUTE'],
  items: [
    { lineNo: 1, targetObjectType: 'CUSTOMER', targetObjectId: 1, targetObjectNo: 'CUS-001', targetObjectSummary: '华南设备客户', targetObjectVersion: 5, status: 'READY', message: '预检通过' },
    { lineNo: 2, targetObjectType: 'CUSTOMER', targetObjectId: 2, targetObjectNo: 'CUS-002', targetObjectSummary: '华东客户', targetObjectVersion: 6, status: 'READY', message: '预检通过' },
  ],
}

const batchOperationBlocked = {
  ...batchOperationDetail,
  status: 'PRECHECK_FAILED',
  failedRows: 1,
  availableActions: ['ERRORS'],
  items: [
    { lineNo: 1, targetObjectType: 'CUSTOMER', targetObjectId: 1, targetObjectNo: 'CUS-001', targetObjectSummary: '华南设备客户', targetObjectVersion: 5, status: 'READY', message: '预检通过' },
    { lineNo: 2, targetObjectType: 'CUSTOMER', targetObjectId: 2, targetObjectNo: 'CUS-002', targetObjectSummary: '华东客户', targetObjectVersion: 7, status: 'BLOCKED', message: '目标版本已变化' },
  ],
}

describe('034 平台治理页面', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.history.pushState({}, '', '/')
    platformGovernanceApiMock.dataRepairAdapters.list.mockResolvedValue([
      { adapterCode: 'MATERIAL_PROFILE_CORRECTION_V1', name: '物料资料修复', targetObjectType: 'MATERIAL', requiredPermissionCode: 'master:material:update', allowedFields: ['specification'], version: 1 },
      { adapterCode: 'CUSTOMER_PROFILE_CORRECTION_V1', name: '客户资料修复', targetObjectType: 'CUSTOMER', requiredPermissionCode: 'master:customer:update', allowedFields: ['contactName'], version: 1 },
      { adapterCode: 'SUPPLIER_PROFILE_CORRECTION_V1', name: '供应商资料修复', targetObjectType: 'SUPPLIER', requiredPermissionCode: 'master:supplier:update', allowedFields: ['contactName'], version: 1 },
    ])
    platformGovernanceApiMock.dataRepairs.list.mockResolvedValue({
      items: [dataRepairDetail],
      total: 1,
      page: 1,
      pageSize: 10,
    })
    platformGovernanceApiMock.dataRepairs.get.mockResolvedValue(dataRepairDetail)
    platformGovernanceApiMock.dataRepairs.create.mockResolvedValue({ ...dataRepairDetail, status: 'DRAFT', availableActions: ['SUBMIT'] })
    platformGovernanceApiMock.dataRepairs.execute.mockResolvedValue({ ...dataRepairDetail, status: 'EXECUTING', availableActions: [] })
    platformGovernanceApiMock.dataRepairs.verify.mockResolvedValue({ ...dataRepairDetail, status: 'VERIFIED', availableActions: [] })
    platformGovernanceApiMock.dataRepairs.cancel.mockResolvedValue({ ...dataRepairDetail, status: 'CANCELLED', availableActions: [] })
    documentPlatformApiMock.attachments.list.mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 20 })
    masterDataApiMock.materials.list.mockResolvedValue({ items: [materialCandidate], total: 1, page: 1, pageSize: 20, totalPages: 1 })
    masterDataApiMock.customers.list.mockResolvedValue({ items: [customerCandidate], total: 1, page: 1, pageSize: 20, totalPages: 1 })
    masterDataApiMock.suppliers.list.mockResolvedValue({ items: [supplierCandidate], total: 1, page: 1, pageSize: 20, totalPages: 1 })

    platformGovernanceApiMock.historyImportAdapters.list.mockResolvedValue([
      { adapterCode: 'CUSTOMER_MASTER_V1', name: '客户历史导入', targetObjectType: 'CUSTOMER', templateCode: 'CUSTOMER_MASTER_V1_TEMPLATE', templateVersion: 1, maxRows: 10000, requiredPermissionCode: 'master:customer:create', version: 1 },
    ])
    platformGovernanceApiMock.historyImportAdapters.downloadTemplate.mockResolvedValue({ blob: new Blob(['xlsx']), fileName: '客户历史模板.xlsx' })
    platformGovernanceApiMock.historyImports.list.mockResolvedValue({ items: [historyImportDetail], total: 1, page: 1, pageSize: 10 })
    platformGovernanceApiMock.historyImports.get.mockResolvedValue(historyImportDetail)
    platformGovernanceApiMock.historyImports.confirm.mockResolvedValue({ ...historyImportDetail, status: 'RUNNING', availableActions: [] })
    platformGovernanceApiMock.historyImports.cancel.mockResolvedValue({ ...historyImportDetail, status: 'CANCELLED', availableActions: [] })
    platformGovernanceApiMock.historyImports.errors.mockResolvedValue({
      items: [{ rowNo: 3, columnName: '客户编码', code: 'HISTORY_IMPORT_ALREADY_EXISTS', message: '客户编码已存在', suggestion: '改用新增编码' }],
      total: 1,
      page: 1,
      pageSize: 10,
    })

    platformGovernanceApiMock.batchTools.list.mockResolvedValue([
      {
        toolCode: 'CUSTOMER_STATUS_CHANGE_V1',
        name: '客户状态批量变更',
        targetObjectType: 'CUSTOMER',
        actionCode: 'STATUS_CHANGE',
        maxItems: 500,
        requiredPermissionCode: 'master:customer:update',
        enabled: true,
      },
    ])
    platformGovernanceApiMock.batchTools.preview.mockResolvedValue(batchOperationDetail)
    platformGovernanceApiMock.batchOperations.get.mockResolvedValue(batchOperationDetail)
    platformGovernanceApiMock.batchOperations.execute.mockResolvedValue({
      ...batchOperationDetail,
      status: 'SUCCEEDED',
      successRows: 2,
      availableActions: [],
      version: 3,
    })
    platformGovernanceApiMock.deliveryAssets.get.mockResolvedValue(deliveryAssetCatalog)

    documentPlatformApiMock.documentTasks.list.mockResolvedValue({
      items: [{
        id: 91,
        taskNo: 'TASK-034-001',
        taskType: 'DATA_REPAIR_EXECUTE',
        businessDomain: 'DATA_REPAIR',
        objectType: 'MATERIAL',
        objectNo: 'MAT-001',
        objectName: '旧规格电机',
        direction: 'IMPORT',
        stage: 'COMMIT',
        status: 'READY_TO_COMMIT',
        progressPercent: 100,
        totalRows: 2,
        successRows: 2,
        failedRows: 0,
        createdByName: '资料员',
        createdAt: '2026-07-21T10:00:00+08:00',
        expiresAt: '2026-07-28T10:00:00+08:00',
        availableActions: ['CONFIRM', 'DOWNLOAD'],
        version: 2,
      }],
      total: 1,
      page: 1,
      pageSize: 10,
    })
    documentPlatformApiMock.documentTasks.errors.mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 10 })
    documentPlatformApiMock.documentTasks.get.mockResolvedValue({ id: 91, status: 'SUCCEEDED', version: 3 })
  })

  it('数据修复列表按 page-standards 展示适配器、状态、对象和只由 availableActions 控制动作', async () => {
    const wrapper = mountWithAuth(DataRepairListView, [
      'platform:data-repair:view',
      'platform:data-repair:create',
    ])
    await flushPromises()

    expectStandardListPage(wrapper)
    expectQueryFormsUseStandardGrid(wrapper)
    expect(platformGovernanceApiMock.dataRepairAdapters.list).toHaveBeenCalled()
    expect(platformGovernanceApiMock.dataRepairs.list).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 10 }))
    expect(wrapper.text()).toContain('数据修复记录')
    expect(wrapper.text()).toContain('物料资料修复')
    expect(wrapper.text()).toContain('修正物料规格说明')
    expect(wrapper.text()).toContain('待执行')
    expect(wrapper.find('[data-test="create-data-repair"]').exists()).toBe(true)
    expect(wrapper.find('[data-to="/platform/data-repairs/501?returnTo=%2Fplatform%2Fdata-repairs"]').exists()).toBe(true)

    const vm = wrapper.vm as unknown as {
      filters: { keyword: string; adapterCode: string; status: string }
      search: () => void
    }
    vm.filters.keyword = 'MAT'
    vm.filters.adapterCode = 'MATERIAL_PROFILE_CORRECTION_V1'
    vm.filters.status = 'READY_TO_EXECUTE'
    vm.search()
    await flushPromises()
    expect(platformGovernanceApiMock.dataRepairs.list).toHaveBeenLastCalledWith({
      keyword: 'MAT',
      adapterCode: 'MATERIAL_PROFILE_CORRECTION_V1',
      targetObjectType: 'MATERIAL',
      status: 'READY_TO_EXECUTE',
      page: 1,
      pageSize: 10,
    })
  })

  it('数据修复创建页按适配器冻结字段提交申请，不暴露自由规则输入', async () => {
    const wrapper = mountWithAuth(DataRepairCreateView, [
      'platform:data-repair:view',
      'platform:data-repair:create',
    ])
    await flushPromises()

    expectStandardFormPage(wrapper)
    expect(wrapper.text()).toContain('新增修复申请')
    expect(wrapper.text()).toContain('物料资料修复')
    expect(wrapper.text()).not.toMatch(/SQL|脚本|自定义表达式|自由字段/)
    expect(wrapper.find('input[name="data-repair-target-object-version"]').exists()).toBe(false)

    await setComponentModel(wrapper, 'data-repair-create-adapter-code', 'MATERIAL_PROFILE_CORRECTION_V1')
    await wrapper.find('input[name="data-repair-target-keyword"]').setValue('电机')
    await clickButtonByTest(wrapper, 'search-data-repair-targets')
    expect(masterDataApiMock.materials.list).toHaveBeenCalledWith(expect.objectContaining({
      keyword: '电机',
      page: 1,
      pageSize: 20,
    }))
    await clickButtonByTest(wrapper, 'select-data-repair-target-31')
    expect(wrapper.text()).toContain('MAT-001')
    expect(wrapper.text()).toContain('V7')
    await wrapper.find('input[name="data-repair-title"]').setValue('修正物料规格说明')
    await wrapper.find('textarea[name="data-repair-reason"]').setValue('历史引用后需留痕更正')
    await wrapper.find('input[name="data-repair-change-specification"]').setValue('新规格')
    await clickButtonByTest(wrapper, 'submit-data-repair-create')

    expect(platformGovernanceApiMock.dataRepairs.create).toHaveBeenCalledWith({
      adapterCode: 'MATERIAL_PROFILE_CORRECTION_V1',
      targetObjectType: 'MATERIAL',
      targetObjectId: 31,
      targetVersion: 7,
      riskSummary: '修正物料规格说明',
      reason: '历史引用后需留痕更正',
      changes: [{ fieldName: 'specification', afterValue: '新规格' }],
      idempotencyKey: expect.stringContaining('data-repair-create-'),
    })
    expect(wrapper.text()).toContain('DR-202607-0001')
  })

  it('数据修复创建切换适配器会清理不兼容目标，并用客户/供应商公开列表候选自动携带版本', async () => {
    const wrapper = mountWithAuth(DataRepairCreateView, [
      'platform:data-repair:view',
      'platform:data-repair:create',
    ])
    await flushPromises()

    await setComponentModel(wrapper, 'data-repair-create-adapter-code', 'MATERIAL_PROFILE_CORRECTION_V1')
    await clickButtonByTest(wrapper, 'search-data-repair-targets')
    await clickButtonByTest(wrapper, 'select-data-repair-target-31')
    expect(wrapper.text()).toContain('MAT-001')

    await setComponentModel(wrapper, 'data-repair-create-adapter-code', 'CUSTOMER_PROFILE_CORRECTION_V1')
    await flushPromises()
    expect(wrapper.text()).not.toContain('MAT-001')
    expect(wrapper.text()).toContain('请选择目标对象')
    await wrapper.find('input[name="data-repair-target-keyword"]').setValue('华南')
    await clickButtonByTest(wrapper, 'search-data-repair-targets')
    expect(masterDataApiMock.customers.list).toHaveBeenCalledWith(expect.objectContaining({
      keyword: '华南',
      page: 1,
      pageSize: 20,
    }))
    await clickButtonByTest(wrapper, 'select-data-repair-target-41')
    await wrapper.find('input[name="data-repair-title"]').setValue('修正客户联系人')
    await wrapper.find('textarea[name="data-repair-reason"]').setValue('历史资料留痕修正')
    await wrapper.find('input[name="data-repair-change-contactName"]').setValue('新联系人')
    await clickButtonByTest(wrapper, 'submit-data-repair-create')

    expect(platformGovernanceApiMock.dataRepairs.create).toHaveBeenCalledWith(expect.objectContaining({
      adapterCode: 'CUSTOMER_PROFILE_CORRECTION_V1',
      targetObjectType: 'CUSTOMER',
      targetObjectId: 41,
      targetVersion: 5,
      changes: [{ fieldName: 'contactName', afterValue: '新联系人' }],
    }))

    await setComponentModel(wrapper, 'data-repair-create-adapter-code', 'SUPPLIER_PROFILE_CORRECTION_V1')
    await wrapper.find('input[name="data-repair-target-keyword"]').setValue('五金')
    await clickButtonByTest(wrapper, 'search-data-repair-targets')
    expect(masterDataApiMock.suppliers.list).toHaveBeenCalledWith(expect.objectContaining({
      keyword: '五金',
      page: 1,
      pageSize: 20,
    }))
  })

  it('数据修复详情展示前后摘要、证据、审批、验证和不可变时间线，高风险执行走确认和版本幂等', async () => {
    const wrapper = mountWithAuth(DataRepairDetailView, [
      'platform:data-repair:view',
      'platform:data-repair:execute',
      'platform:data-repair:cancel',
      'platform:attachment:view',
    ], { repairId: 501 })
    await flushPromises()

    expectStandardDetailPage(wrapper)
    expect(platformGovernanceApiMock.dataRepairs.get).toHaveBeenCalledWith(501)
    expect(wrapper.text()).toContain('修正物料规格说明')
    expect(wrapper.text()).toContain('前后摘要')
    expect(wrapper.text()).toContain('旧规格')
    expect(wrapper.text()).toContain('新规格')
    expect(wrapper.text()).toContain('证据附件')
    expect(wrapper.text()).toContain('固定审批')
    expect(wrapper.text()).toContain('不可变时间线')

    await wrapper.find('[data-test="data-repair-action-comment"]').setValue('审批后执行')
    await clickButtonByTest(wrapper, 'execute-data-repair')

    expect(confirmActionMock).toHaveBeenCalledWith(expect.stringContaining('确认执行数据修复'), expect.objectContaining({ risk: 'danger' }))
    expect(platformGovernanceApiMock.dataRepairs.execute).toHaveBeenCalledWith(501, {
      version: 3,
      comment: '审批后执行',
      idempotencyKey: expect.stringContaining('data-repair-execute-'),
    })
  })

  it('数据修复验证必须显式发送通过或失败结果', async () => {
    platformGovernanceApiMock.dataRepairs.get.mockResolvedValueOnce({
      ...dataRepairDetail,
      status: 'EXECUTED',
      availableActions: ['VERIFY'],
      version: 5,
    })
    const wrapper = mountWithAuth(DataRepairDetailView, [
      'platform:data-repair:view',
      'platform:data-repair:verify',
      'platform:attachment:view',
    ], { repairId: 501 })
    await flushPromises()

    await wrapper.find('[data-test="data-repair-action-comment"]').setValue('复核通过')
    await clickButtonByTest(wrapper, 'verify-data-repair')
    expect(platformGovernanceApiMock.dataRepairs.verify).toHaveBeenCalledWith(501, {
      version: 5,
      comment: '复核通过',
      passed: true,
      idempotencyKey: expect.stringContaining('data-repair-verify-'),
    })

    platformGovernanceApiMock.dataRepairs.get.mockResolvedValueOnce({
      ...dataRepairDetail,
      status: 'EXECUTED',
      availableActions: ['VERIFY'],
      version: 6,
    })
    const failedWrapper = mountWithAuth(DataRepairDetailView, [
      'platform:data-repair:view',
      'platform:data-repair:verify',
      'platform:attachment:view',
    ], { repairId: 501 })
    await flushPromises()

    await failedWrapper.find('[data-test="data-repair-action-comment"]').setValue('抽查未通过')
    await clickButtonByTest(failedWrapper, 'fail-data-repair-verification')
    expect(platformGovernanceApiMock.dataRepairs.verify).toHaveBeenLastCalledWith(501, {
      version: 6,
      comment: '抽查未通过',
      passed: false,
      idempotencyKey: expect.stringContaining('data-repair-verify-'),
    })
  })

  it('数据修复附件上传和删除入口叠加附件权限与只读态', async () => {
    platformGovernanceApiMock.dataRepairs.get.mockResolvedValueOnce({
      ...dataRepairDetail,
      availableActions: ['CANCEL'],
    })
    documentPlatformApiMock.attachments.list.mockResolvedValueOnce({
      items: [{
        id: 6,
        objectType: 'DATA_REPAIR_REQUEST',
        objectId: 501,
        fileName: '修复证据.pdf',
        fileSize: 1024,
        contentType: 'application/pdf',
        uploadedByName: '资料员',
        uploadedAt: '2026-07-21T10:00:00+08:00',
        status: 'AVAILABLE',
        availableActions: ['DOWNLOAD', 'DELETE'],
        version: 2,
      }],
      total: 1,
      page: 1,
      pageSize: 20,
    })
    const wrapper = mountWithAuth(DataRepairDetailView, [
      'platform:data-repair:view',
      'platform:data-repair:cancel',
      'platform:attachment:view',
    ], { repairId: 501 })
    await flushPromises()

    expect(wrapper.text()).toContain('修复证据.pdf')
    expect(wrapper.find('[data-test="upload-attachment"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="delete-attachment"]').exists()).toBe(false)
  })

  it('数据修复详情加载失败进入错误态，不停留在加载中', async () => {
    platformGovernanceApiMock.dataRepairs.get.mockRejectedValueOnce(new Error('数据修复详情不可用'))
    const wrapper = mountWithAuth(DataRepairDetailView, ['platform:data-repair:view'], { repairId: 501 })
    await flushPromises()

    expect(wrapper.text()).toContain('数据修复详情不可用')
    expect(wrapper.text()).not.toContain('数据修复详情加载中')
    expect(wrapper.find('.section-block').exists()).toBe(false)
  })

  it('数据修复详情即使状态待执行，缺少 EXECUTE 动作也不显示执行按钮', async () => {
    platformGovernanceApiMock.dataRepairs.get.mockResolvedValueOnce({ ...dataRepairDetail, availableActions: [] })
    const wrapper = mountWithAuth(DataRepairDetailView, [
      'platform:data-repair:view',
      'platform:data-repair:execute',
    ], { repairId: 501 })
    await flushPromises()

    expect(wrapper.text()).toContain('待执行')
    expect(wrapper.find('[data-test="execute-data-repair"]').exists()).toBe(false)
  })

  it('数据修复草稿和已取消详情提供稳定浏览器状态样本', async () => {
    platformGovernanceApiMock.dataRepairs.get.mockResolvedValueOnce({
      ...dataRepairDetail,
      status: 'DRAFT',
      availableActions: ['SUBMIT', 'CANCEL'],
      version: 7,
    })
    const draftWrapper = mountWithAuth(DataRepairDetailView, [
      'platform:data-repair:view',
      'platform:data-repair:submit',
      'platform:data-repair:cancel',
      'platform:attachment:view',
    ], { repairId: 501 })
    await flushPromises()

    expect(draftWrapper.text()).toContain('草稿')
    expect(draftWrapper.find('[data-test="submit-data-repair"]').exists()).toBe(true)
    expect(draftWrapper.find('[data-test="cancel-data-repair"]').exists()).toBe(true)
    expect(draftWrapper.find('[data-test="execute-data-repair"]').exists()).toBe(false)

    platformGovernanceApiMock.dataRepairs.get.mockResolvedValueOnce({
      ...dataRepairDetail,
      status: 'CANCELLED',
      availableActions: [],
      version: 8,
    })
    const cancelledWrapper = mountWithAuth(DataRepairDetailView, [
      'platform:data-repair:view',
      'platform:attachment:view',
    ], { repairId: 501 })
    await flushPromises()

    expect(cancelledWrapper.text()).toContain('已取消')
    expect(cancelledWrapper.text()).toContain('当前记录为只读状态')
    expect(cancelledWrapper.find('[data-test="submit-data-repair"]').exists()).toBe(false)
    expect(cancelledWrapper.find('[data-test="cancel-data-repair"]').exists()).toBe(false)
    expect(cancelledWrapper.find('[data-test="execute-data-repair"]').exists()).toBe(false)
  })

  it('历史导入列表展示适配器目录、批次和模板下载，不提供运行时字段映射编辑', async () => {
    const wrapper = mountWithAuth(HistoryImportListView, [
      'platform:history-import:view',
      'platform:history-import:create',
      'master:customer:create',
    ])
    await flushPromises()

    expectStandardListPage(wrapper)
    expectQueryFormsUseStandardGrid(wrapper)
    expect(wrapper.text()).toContain('历史数据导入')
    expect(wrapper.text()).toContain('客户历史导入')
    expect(wrapper.text()).toContain('HI-202607-0001')
    expect(wrapper.text()).not.toContain('字段映射设计')
    expect(wrapper.find('[data-test="upload-history-file"]').exists()).toBe(true)

    await clickButtonByTest(wrapper, 'download-history-template')
    expect(platformGovernanceApiMock.historyImportAdapters.downloadTemplate).toHaveBeenCalledWith('CUSTOMER_MASTER_V1')

    const vm = wrapper.vm as unknown as {
      filters: { keyword: string; adapterCode: string; status: string }
      search: () => void
    }
    vm.filters.keyword = '客户历史.xlsx'
    vm.filters.adapterCode = 'CUSTOMER_MASTER_V1'
    vm.filters.status = 'READY_TO_COMMIT'
    vm.search()
    await flushPromises()
    expect(platformGovernanceApiMock.historyImports.list).toHaveBeenLastCalledWith({
      keyword: '客户历史.xlsx',
      adapterCode: 'CUSTOMER_MASTER_V1',
      status: 'READY_TO_COMMIT',
      page: 1,
      pageSize: 10,
    })
  })

  it('历史导入列表消费 adapterCode 深链并预置适配器筛选', async () => {
    window.history.pushState({}, '', '/platform/history-imports?adapterCode=CUSTOMER_MASTER_V1')
    const wrapper = mountWithAuth(HistoryImportListView, ['platform:history-import:view'])
    await flushPromises()

    expectQueryFormsUseStandardGrid(wrapper)
    expect(platformGovernanceApiMock.historyImports.list).toHaveBeenCalledWith(expect.objectContaining({
      adapterCode: 'CUSTOMER_MASTER_V1',
      page: 1,
      pageSize: 10,
    }))
    expect((wrapper.vm as unknown as { filters: { adapterCode: string } }).filters.adapterCode).toBe('CUSTOMER_MASTER_V1')
  })

  it('历史导入上传入口只对本地创建权限和适配器业务权限同时满足者开放', async () => {
    const wrapper = mountWithAuth(HistoryImportListView, ['platform:history-import:view'])
    await flushPromises()

    expect(wrapper.text()).toContain('下载模板')
    expect(wrapper.find('[data-test="upload-history-file"]').exists()).toBe(false)
  })

  it('历史导入详情展示预检、错误明细、文件、任务和过期，确认与取消只消费 availableActions', async () => {
    const wrapper = mountWithAuth(HistoryImportDetailView, [
      'platform:history-import:view',
      'platform:history-import:confirm',
      'platform:history-import:cancel',
    ], { taskId: 601 })
    await flushPromises()

    expectStandardDetailPage(wrapper)
    expect(wrapper.text()).toContain('客户历史.xlsx')
    expect(wrapper.text()).toContain('预检通过')
    expect(wrapper.text()).toContain('结果过期')
    expect(wrapper.text()).toContain('关联任务')

    await clickButtonByTest(wrapper, 'view-history-import-errors')
    expect(platformGovernanceApiMock.historyImports.errors).toHaveBeenCalledWith(601, { page: 1, pageSize: 10 })
    expect(wrapper.text()).toContain('客户编码已存在')

    const errorPagination = wrapper.findComponent({ name: 'ElPagination' })
    errorPagination.vm.$emit('size-change', 20)
    await flushPromises()
    expect(platformGovernanceApiMock.historyImports.errors).toHaveBeenLastCalledWith(601, { page: 1, pageSize: 20 })

    await clickButtonByTest(wrapper, 'confirm-history-import')
    expect(platformGovernanceApiMock.historyImports.confirm).toHaveBeenCalledWith(601, {
      version: 4,
      idempotencyKey: expect.stringContaining('history-import-confirm-'),
    })
  })

  it('历史导入错误明细支持稳定多页浏览器样本', async () => {
    platformGovernanceApiMock.historyImports.get.mockResolvedValueOnce({
      ...historyImportDetail,
      failedRows: 23,
      errorSummary: { totalErrors: 23, summary: '存在 23 条错误' },
      availableActions: ['ERRORS'],
    })
    platformGovernanceApiMock.historyImports.errors
      .mockResolvedValueOnce({
        items: [{ rowNo: 3, columnName: '客户编码', code: 'HISTORY_IMPORT_ALREADY_EXISTS', message: '客户编码已存在', suggestion: '改用新增编码' }],
        total: 23,
        page: 1,
        pageSize: 10,
      })
      .mockResolvedValueOnce({
        items: [{ rowNo: 13, columnName: '客户名称', code: 'HISTORY_IMPORT_FIELD_REQUIRED', message: '客户名称必填', suggestion: '补充客户名称' }],
        total: 23,
        page: 2,
        pageSize: 10,
      })
    const wrapper = mountWithAuth(HistoryImportDetailView, ['platform:history-import:view'], { taskId: 601 })
    await flushPromises()

    await clickButtonByTest(wrapper, 'view-history-import-errors')
    const errorPagination = wrapper.findComponent({ name: 'ElPagination' })
    errorPagination.vm.$emit('current-change', 2)
    await flushPromises()

    expect(platformGovernanceApiMock.historyImports.errors).toHaveBeenLastCalledWith(601, { page: 2, pageSize: 10 })
    expect(wrapper.text()).toContain('客户名称必填')
  })

  it('历史导入错误汇总大于 0 但明细为空时显示异常，不伪装为空表', async () => {
    platformGovernanceApiMock.historyImports.get.mockResolvedValueOnce({
      ...historyImportDetail,
      failedRows: 3,
      errorSummary: { totalErrors: 3, summary: '存在 3 条错误' },
      availableActions: ['ERRORS'],
    })
    platformGovernanceApiMock.historyImports.errors.mockResolvedValueOnce({ items: [], total: 0, page: 1, pageSize: 10 })
    const wrapper = mountWithAuth(HistoryImportDetailView, ['platform:history-import:view'], { taskId: 601 })
    await flushPromises()

    await clickButtonByTest(wrapper, 'view-history-import-errors')

    expect(wrapper.text()).toContain('错误汇总存在但明细为空')
    expect(wrapper.text()).not.toContain('暂无错误明细')
  })

  it('历史导入错误明细加载失败时显示失败原因和汇总异常', async () => {
    platformGovernanceApiMock.historyImports.get.mockResolvedValueOnce({
      ...historyImportDetail,
      failedRows: 2,
      errorSummary: { totalErrors: 2, summary: '存在 2 条错误' },
      availableActions: ['ERRORS'],
    })
    platformGovernanceApiMock.historyImports.errors.mockRejectedValueOnce(new Error('错误明细读取失败'))
    const wrapper = mountWithAuth(HistoryImportDetailView, ['platform:history-import:view'], { taskId: 601 })
    await flushPromises()

    await clickButtonByTest(wrapper, 'view-history-import-errors')

    expect(wrapper.text()).toContain('错误明细读取失败')
    expect(wrapper.text()).toContain('错误汇总存在但明细不可读取')
  })

  it('交付资料页为只读目录，展示模板、适配器、批量工具、手册和演示数据版本', async () => {
    const wrapper = mountWithAuth(DeliveryAssetsView, ['platform:delivery-asset:view'])
    await flushPromises()

    expectStandardDetailPage(wrapper)
    expect(platformGovernanceApiMock.deliveryAssets.get).toHaveBeenCalled()
    expect(wrapper.text()).toContain('交付资料')
    expect(wrapper.text()).toContain('SALES_ORDER_V1')
    expect(wrapper.text()).toContain('CUSTOMER_MASTER_V1')
    expect(wrapper.text()).toContain('CUSTOMER_STATUS_CHANGE_V1')
    expect(wrapper.text()).toContain('操作手册版本')
    expect(wrapper.text()).toContain('034-manual-v1')
    expect(wrapper.text()).toContain('演示数据版本')
    expect(wrapper.text()).toContain('034-demo-v1')
    expect(wrapper.text()).toContain('NOT_VERIFIED')
    expect(wrapper.text()).not.toContain('DeliveryAssetCatalog 未提供')
    expect(wrapper.text()).not.toMatch(/重建|删除|脚本|配置保存/)
  })

  it('批量状态工具面板用真实候选搜索和分页追加预检，按 availableActions 确认执行', async () => {
    masterDataApiMock.customers.list
      .mockResolvedValueOnce({ items: [customerCandidate], total: 2, page: 1, pageSize: 20, totalPages: 2 })
      .mockResolvedValueOnce({
        items: [{ ...customerCandidate, id: 42, code: 'CUS-002', name: '华东客户', version: 6 }],
        total: 2,
        page: 2,
        pageSize: 20,
        totalPages: 2,
      })
    const wrapper = mountWithAuth(BatchStatusToolPanel, [
      'platform:batch-tool:view',
      'platform:batch-tool:preview',
      'platform:batch-tool:execute',
      'master:customer:update',
    ], {
      toolCode: 'CUSTOMER_STATUS_CHANGE_V1',
      title: '客户批量状态',
      buttonTestId: 'customer-batch-status-entry',
      defaultCandidates: [
        { id: 99, code: 'CUS-NV', name: '缺版本客户', status: 'ENABLED' },
      ],
    })
    await flushPromises()

    await clickButtonByTest(wrapper, 'customer-batch-status-entry')
    expect(wrapper.find('[data-test="batch-dialog-scroll-region"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('对象ID,版本,编码,名称')
    expect(wrapper.find('[data-test="add-manual-batch-candidates"]').exists()).toBe(false)

    await clickButtonByTest(wrapper, 'select-current-batch-candidates')
    expect(wrapper.text()).toContain('候选对象 CUS-NV 缺少稳定版本')
    await wrapper.find('input[name="batch-candidate-keyword"]').setValue('华南')
    await clickButtonByTest(wrapper, 'search-batch-candidates')
    expect(masterDataApiMock.customers.list).toHaveBeenCalledWith(expect.objectContaining({
      keyword: '华南',
      page: 1,
      pageSize: 20,
    }))
    await clickButtonByTest(wrapper, 'select-batch-candidate-41')
    await clickButtonByTest(wrapper, 'load-more-batch-candidates')
    expect(masterDataApiMock.customers.list).toHaveBeenLastCalledWith(expect.objectContaining({
      keyword: '华南',
      page: 2,
      pageSize: 20,
    }))
    await clickButtonByTest(wrapper, 'select-batch-candidate-42')
    await setComponentModel(wrapper, 'batch-target-status', 'DISABLED')
    await wrapper.find('textarea[name="batch-operation-reason"]').setValue('停用历史客户')
    await clickButtonByTest(wrapper, 'preview-batch-tool')

    expect(platformGovernanceApiMock.batchTools.preview).toHaveBeenCalledWith('CUSTOMER_STATUS_CHANGE_V1', expect.objectContaining({
      actionCode: 'STATUS_CHANGE',
      targetStatus: 'DISABLED',
      reason: '停用历史客户',
      targets: [
        { targetObjectId: 41, version: 5 },
        { targetObjectId: 42, version: 6 },
      ],
      idempotencyKey: expect.stringContaining('batch-tool-preview-'),
    }))
    expect(wrapper.text()).toContain('预检通过')
    expect(wrapper.text()).toContain('可执行 2')

    await clickButtonByTest(wrapper, 'execute-batch-operation')
    expect(confirmActionMock).toHaveBeenCalledWith(expect.stringContaining('确认执行批量状态'), expect.objectContaining({ risk: 'danger' }))
    expect(platformGovernanceApiMock.batchOperations.execute).toHaveBeenCalledWith(701, {
      version: 1,
      idempotencyKey: expect.stringContaining('batch-tool-execute-'),
    })

    platformGovernanceApiMock.batchTools.preview.mockResolvedValueOnce(batchOperationBlocked)
    await clickButtonByTest(wrapper, 'preview-batch-tool')
    expect(wrapper.text()).toContain('目标版本已变化')
    expect(wrapper.find('[data-test="execute-batch-operation"]').exists()).toBe(false)
  })

  it('批量状态工具按客户、供应商、物料工具代码使用对应公开列表候选查询', async () => {
    platformGovernanceApiMock.batchTools.list
      .mockResolvedValueOnce([{
        toolCode: 'SUPPLIER_STATUS_CHANGE_V1',
        name: '供应商状态批量变更',
        targetObjectType: 'SUPPLIER',
        actionCode: 'STATUS_CHANGE',
        maxItems: 500,
        requiredPermissionCode: 'master:supplier:update',
        enabled: true,
      }])
      .mockResolvedValueOnce([{
        toolCode: 'MATERIAL_STATUS_CHANGE_V1',
        name: '物料状态批量变更',
        targetObjectType: 'MATERIAL',
        actionCode: 'STATUS_CHANGE',
        maxItems: 500,
        requiredPermissionCode: 'master:material:update',
        enabled: true,
      }])

    const supplierWrapper = mountWithAuth(BatchStatusToolPanel, [
      'platform:batch-tool:view',
      'platform:batch-tool:preview',
      'master:supplier:update',
    ], {
      toolCode: 'SUPPLIER_STATUS_CHANGE_V1',
      title: '供应商批量状态',
      buttonTestId: 'supplier-batch-status-entry',
      defaultCandidates: [],
    })
    await clickButtonByTest(supplierWrapper, 'supplier-batch-status-entry')
    await supplierWrapper.find('input[name="batch-candidate-keyword"]').setValue('五金')
    await clickButtonByTest(supplierWrapper, 'search-batch-candidates')
    expect(masterDataApiMock.suppliers.list).toHaveBeenCalledWith(expect.objectContaining({
      keyword: '五金',
      page: 1,
      pageSize: 20,
    }))

    const materialWrapper = mountWithAuth(BatchStatusToolPanel, [
      'platform:batch-tool:view',
      'platform:batch-tool:preview',
      'master:material:update',
    ], {
      toolCode: 'MATERIAL_STATUS_CHANGE_V1',
      title: '物料批量状态',
      buttonTestId: 'material-batch-status-entry',
      defaultCandidates: [],
    })
    await clickButtonByTest(materialWrapper, 'material-batch-status-entry')
    await materialWrapper.find('input[name="batch-candidate-keyword"]').setValue('电机')
    await clickButtonByTest(materialWrapper, 'search-batch-candidates')
    expect(masterDataApiMock.materials.list).toHaveBeenCalledWith(expect.objectContaining({
      keyword: '电机',
      page: 1,
      pageSize: 20,
    }))
  })

  it('批量候选搜索返回无稳定 version 时失败关闭，不允许加入或预检', async () => {
    masterDataApiMock.customers.list.mockResolvedValue({
      items: [{ ...customerCandidate, id: 88, code: 'CUS-NV', version: undefined }],
      total: 1,
      page: 1,
      pageSize: 20,
      totalPages: 1,
    })
    const wrapper = mountWithAuth(BatchStatusToolPanel, [
      'platform:batch-tool:view',
      'platform:batch-tool:preview',
      'master:customer:update',
    ], {
      toolCode: 'CUSTOMER_STATUS_CHANGE_V1',
      title: '客户批量状态',
      buttonTestId: 'customer-batch-status-entry',
      defaultCandidates: [],
    })
    await clickButtonByTest(wrapper, 'customer-batch-status-entry')
    await wrapper.find('input[name="batch-candidate-keyword"]').setValue('无版本')
    await clickButtonByTest(wrapper, 'search-batch-candidates')

    expect(wrapper.text()).toContain('候选对象 CUS-NV 缺少稳定版本')
    expect(wrapper.find('[data-test="select-batch-candidate-88"]').exists()).toBe(false)
    await clickButtonByTest(wrapper, 'preview-batch-tool')
    expect(platformGovernanceApiMock.batchTools.preview).not.toHaveBeenCalled()
  })

  it('文档任务中心增强 034 业务域、对象、发起人、时间和状态筛选，分页补齐 100', async () => {
    const wrapper = mountWithAuth(DocumentTaskCenterView, [
      'platform:document-task:view',
      'platform:document-task:download',
    ])
    await flushPromises()

    expectStandardListPage(wrapper)
    expectQueryFormsUseStandardGrid(wrapper)
    expect(wrapper.text()).toContain('业务域')
    expect(wrapper.text()).toContain('发起人')
    expect(wrapper.text()).toContain('创建日期')
    expect(wrapper.text()).toContain('数据修复执行')
    expect(wrapper.text()).toContain('成功数')
    expect(wrapper.text()).toContain('资料员')

    const vm = wrapper.vm as unknown as {
      filters: {
        domain: string
        taskType: string
        objectKeyword: string
        createdByKeyword: string
        createdAtRange: [string, string]
        status: string
      }
      search: () => void
    }
    vm.filters.domain = 'DATA_REPAIR'
    vm.filters.taskType = 'DATA_REPAIR_EXECUTE'
    vm.filters.objectKeyword = 'MAT-001'
    vm.filters.createdByKeyword = '资料员'
    vm.filters.createdAtRange = ['2026-07-01', '2026-07-21']
    vm.filters.status = 'READY_TO_COMMIT'
    vm.search()
    await flushPromises()

    expect(documentPlatformApiMock.documentTasks.list).toHaveBeenLastCalledWith(expect.objectContaining({
      domain: 'DATA_REPAIR',
      taskType: 'DATA_REPAIR_EXECUTE',
      objectKeyword: 'MAT-001',
      createdByKeyword: '资料员',
      createdAtFrom: '2026-07-01',
      createdAtTo: '2026-07-21',
      status: 'READY_TO_COMMIT',
    }))
  })

  it('文档任务中心消费深链 taskId 与 batchOperationId 作为服务端精确筛选并回到第一页', async () => {
    window.history.pushState({}, '', '/platform/document-tasks?taskId=601&batchOperationId=701')
    const wrapper = mountWithAuth(DocumentTaskCenterView, [
      'platform:document-task:view',
      'platform:document-task:download',
    ])
    await flushPromises()

    expect(documentPlatformApiMock.documentTasks.list).toHaveBeenCalledWith(expect.objectContaining({
      taskId: '601',
      batchOperationId: '701',
      page: 1,
      pageSize: 10,
    }))

    const vm = wrapper.vm as unknown as {
      pagination: { page: number }
      filters: { status: string }
      search: () => void
    }
    vm.pagination.page = 4
    vm.filters.status = 'SUCCEEDED'
    vm.search()
    await flushPromises()

    expect(documentPlatformApiMock.documentTasks.list).toHaveBeenLastCalledWith(expect.objectContaining({
      taskId: '601',
      batchOperationId: '701',
      status: 'SUCCEEDED',
      page: 1,
    }))
  })

  it('文档任务中心对 034 历史导入任务使用专用确认和取消 API', async () => {
    const historyTask = {
      id: 601,
      taskNo: 'HI-202607-0001',
      taskType: 'CUSTOMER_MASTER_V1_HISTORY_IMPORT',
      businessDomain: 'HISTORY_IMPORT',
      objectType: 'CUSTOMER',
      objectNo: '客户历史.xlsx',
      direction: 'IMPORT',
      stage: 'VALIDATE',
      status: 'READY_TO_COMMIT',
      progressPercent: 100,
      totalRows: 10,
      successRows: 10,
      failedRows: 0,
      createdByName: '资料员',
      createdAt: '2026-07-21T10:00:00+08:00',
      expiresAt: '2026-07-28T10:00:00+08:00',
      availableActions: ['CONFIRM', 'CANCEL'],
      version: 4,
    }
    documentPlatformApiMock.documentTasks.list.mockResolvedValueOnce({
      items: [historyTask],
      total: 1,
      page: 1,
      pageSize: 10,
    })
    const wrapper = mountWithAuth(DocumentTaskCenterView, [
      'platform:document-task:view',
      'platform:document-task:cancel',
    ])
    await flushPromises()

    await clickButtonByTest(wrapper, 'confirm-document-task')
    expect(platformGovernanceApiMock.historyImports.confirm).toHaveBeenCalledWith(601, {
      version: 4,
      idempotencyKey: expect.stringContaining('history-import-confirm-'),
    })
    expect(documentPlatformApiMock.imports.confirm).not.toHaveBeenCalled()

    documentPlatformApiMock.documentTasks.list.mockResolvedValueOnce({
      items: [historyTask],
      total: 1,
      page: 1,
      pageSize: 10,
    })
    const cancelWrapper = mountWithAuth(DocumentTaskCenterView, [
      'platform:document-task:view',
      'platform:document-task:cancel',
    ])
    await flushPromises()

    await clickButtonByTest(cancelWrapper, 'cancel-document-task')
    expect(platformGovernanceApiMock.historyImports.cancel).toHaveBeenCalledWith(601, {
      version: 4,
      idempotencyKey: expect.stringContaining('history-import-cancel-'),
    })
    expect(documentPlatformApiMock.documentTasks.cancel).not.toHaveBeenCalled()
  })
})
