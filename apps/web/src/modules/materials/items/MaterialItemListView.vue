<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  masterDataApi,
  type CategoryRecord,
  type CostCategory,
  type InventoryValuationCategory,
  type MasterDataStatus,
  type MaterialPayload,
  type MaterialRecord,
  type MaterialSourceType,
  type MaterialTrackingMethod,
  type MaterialType,
  type ResourceId,
  type UnitRecord,
} from '../../../shared/api/masterDataApi'
import { createIdempotencyKey, documentPlatformApi, type DocumentTaskRecord } from '../../../shared/api/documentPlatformApi'
import { downloadFile } from '../../../shared/file/download'
import { useAuthStore } from '../../../stores/authStore'
import { errorMessage, pageItems, statusTagType } from '../../system/shared/pageHelpers'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import {
  masterStatusLabel,
  costCategoryLabel,
  inventoryValuationCategoryLabel,
  materialTypeLabel,
  sourceTypeLabel,
  trackingMethodLabel,
} from '../../master/shared/masterPageHelpers'
import { confirmAction } from '../../../shared/ui/confirmDialog'

const materialTypeOptions: Array<{ label: string; value: MaterialType }> = [
  { label: '原材料', value: 'RAW_MATERIAL' },
  { label: '半成品', value: 'SEMI_FINISHED' },
  { label: '成品', value: 'FINISHED_GOOD' },
  { label: '辅料', value: 'AUXILIARY' },
]
const sourceTypeOptions: Array<{ label: string; value: MaterialSourceType }> = [
  { label: '外购', value: 'PURCHASED' },
  { label: '自制', value: 'SELF_MADE' },
  { label: '委外', value: 'OUTSOURCED' },
]
const trackingMethodOptions: Array<{ label: string; value: MaterialTrackingMethod }> = [
  { label: '不追踪', value: 'NONE' },
  { label: '批次管理', value: 'BATCH' },
  { label: '序列号管理', value: 'SERIAL' },
]
const costCategoryOptions: Array<{ label: string; value: CostCategory }> = [
  { label: '直接材料', value: 'DIRECT_MATERIAL' },
  { label: '辅助材料', value: 'AUXILIARY_MATERIAL' },
  { label: '半成品', value: 'SEMI_FINISHED' },
  { label: '产成品', value: 'FINISHED_GOOD' },
  { label: '委外', value: 'OUTSOURCING' },
  { label: '服务', value: 'SERVICE' },
  { label: '未分类', value: 'UNCLASSIFIED' },
]
const inventoryValuationCategoryOptions: Array<{ label: string; value: InventoryValuationCategory }> = [
  { label: '计价物料', value: 'VALUATED_MATERIAL' },
  { label: '非计价消耗品', value: 'NON_VALUATED_CONSUMABLE' },
  { label: '服务非库存', value: 'SERVICE_NON_STOCK' },
  { label: '未分类', value: 'UNCLASSIFIED' },
]

const authStore = useAuthStore()
const filters = reactive<{
  keyword: string
  status?: MasterDataStatus
  categoryId: ResourceId | ''
  materialType?: MaterialType
  sourceType?: MaterialSourceType
  trackingMethod?: MaterialTrackingMethod
}>({
  keyword: '',
  status: undefined,
  categoryId: '',
  materialType: undefined,
  sourceType: undefined,
  trackingMethod: undefined,
})
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
})
const records = ref<MaterialRecord[]>([])
const categories = ref<CategoryRecord[]>([])
const units = ref<UnitRecord[]>([])
const loading = ref(true)
const referenceLoading = ref(true)
const error = ref('')
const referenceError = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const formVisible = ref(false)
const formSubmitting = ref(false)
const codeGenerating = ref(false)
const formError = ref('')
const editingRecord = ref<MaterialRecord | null>(null)
const detailVisible = ref(false)
const detailRecord = ref<MaterialRecord | null>(null)
const importVisible = ref(false)
const importFile = ref<File | null>(null)
const importSubmitting = ref(false)
const importError = ref('')
const latestDocumentTask = ref<DocumentTaskRecord | null>(null)
const form = reactive<{
  code: string
  name: string
  specification: string
  materialType: MaterialType | ''
  sourceType: MaterialSourceType | ''
  trackingMethod: MaterialTrackingMethod
  categoryId: ResourceId | ''
  unitId: ResourceId | ''
  costCategory: CostCategory | ''
  inventoryValuationCategory: InventoryValuationCategory | ''
  inventoryValueEnabled: boolean
  projectCostEnabled: boolean
  costRemark: string
  status: MasterDataStatus
  remark: string
}>({
  code: '',
  name: '',
  specification: '',
  materialType: '',
  sourceType: '',
  trackingMethod: 'NONE',
  categoryId: '',
  unitId: '',
  costCategory: 'UNCLASSIFIED',
  inventoryValuationCategory: 'UNCLASSIFIED',
  inventoryValueEnabled: true,
  projectCostEnabled: true,
  costRemark: '',
  status: 'ENABLED',
  remark: '',
})

const canCreate = computed(() => authStore.hasPermission('master:material:create'))
const canUpdate = computed(() => authStore.hasPermission('master:material:update'))
const canUpdateCost = computed(() => authStore.hasPermission('master:material-cost:update'))
const canGenerateCode = computed(() => canCreate.value && authStore.hasPermission('master:coding-rule:generate'))
const canImportMaterials = computed(() => authStore.hasPermission('master:material:import'))
const canExportMaterials = computed(() => authStore.hasPermission('master:material:export'))

async function loadReferences() {
  referenceLoading.value = true
  referenceError.value = ''
  try {
    const [unitPage, categoryPage] = await Promise.all([
      masterDataApi.units.list({ page: 1, pageSize: 100, status: 'ENABLED', keyword: '' }),
      masterDataApi.categories.list({ page: 1, pageSize: 100, status: 'ENABLED', keyword: '' }),
    ])
    units.value = pageItems(unitPage)
    categories.value = pageItems(categoryPage)
  } catch (caught) {
    units.value = []
    categories.value = []
    referenceError.value = errorMessage(caught)
  } finally {
    referenceLoading.value = false
  }
}

function normalizeOptionalId(value: ResourceId | ''): ResourceId | undefined {
  if (value === '' || value === null || value === undefined) {
    return undefined
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : undefined
  }

  const trimmedValue = String(value).trim()
  if (!trimmedValue) {
    return undefined
  }
  const numericValue = Number(trimmedValue)
  return Number.isFinite(numericValue) ? numericValue : trimmedValue
}

function normalizeRequiredId(value: ResourceId | ''): ResourceId | null {
  return normalizeOptionalId(value) ?? null
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await masterDataApi.materials.list({
      keyword: filters.keyword,
      status: filters.status,
      page: pagination.page,
      pageSize: pagination.pageSize,
      categoryId: normalizeOptionalId(filters.categoryId),
      materialType: filters.materialType,
      sourceType: filters.sourceType,
      trackingMethod: filters.trackingMethod,
    })
    records.value = pageItems(page)
    pagination.total = Number(page.total)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

function search() {
  pagination.page = 1
  void loadRecords()
}

function resetSearch() {
  filters.keyword = ''
  filters.status = undefined
  filters.categoryId = ''
  filters.materialType = undefined
  filters.sourceType = undefined
  filters.trackingMethod = undefined
  pagination.page = 1
  void loadRecords()
}

function changePage(page: number) {
  pagination.page = page
  void loadRecords()
}

function changePageSize(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1
  void loadRecords()
}

function resetForm(record?: MaterialRecord) {
  Object.assign(form, {
    code: record?.code ?? '',
    name: record?.name ?? '',
    specification: record?.specification ?? '',
    materialType: record?.materialType ?? '',
    sourceType: record?.sourceType ?? '',
    trackingMethod: record?.trackingMethod ?? 'NONE',
    categoryId: record?.categoryId ?? '',
    unitId: record?.unitId ?? '',
    costCategory: record?.costCategory ?? '',
    inventoryValuationCategory: record?.inventoryValuationCategory ?? '',
    inventoryValueEnabled: record?.inventoryValueEnabled ?? true,
    projectCostEnabled: record?.projectCostEnabled ?? true,
    costRemark: record?.costRemark ?? '',
    status: record?.status ?? 'ENABLED',
    remark: record?.remark ?? '',
  })
  formError.value = ''
}

function openCreate() {
  editingRecord.value = null
  resetForm()
  formVisible.value = true
}

function openEdit(record: MaterialRecord) {
  editingRecord.value = record
  resetForm(record)
  formVisible.value = true
}

function openDetail(record: MaterialRecord) {
  detailRecord.value = record
  detailVisible.value = true
}

function trackingMethodImmutableReason(record: MaterialRecord | null): string {
  return record?.trackingMethodImmutableReason ?? ''
}

function baseUnitImmutableReason(record: MaterialRecord | null): string {
  return record?.baseUnitImmutableReason ?? ''
}

function validateMaterialForm(): {
  materialType: MaterialType
  sourceType: MaterialSourceType
  trackingMethod: MaterialTrackingMethod
  categoryId: ResourceId
  unitId: ResourceId
  costCategory?: CostCategory
  inventoryValuationCategory?: InventoryValuationCategory
} | null {
  if (!form.code.trim() || !form.name.trim()) {
    formError.value = '请完整填写编码和名称'
    return null
  }
  if (!form.materialType || !form.sourceType) {
    formError.value = '物料类型和来源属性为必填'
    return null
  }

  const categoryId = normalizeRequiredId(form.categoryId)
  const unitId = normalizeRequiredId(form.unitId)
  if (categoryId === null || unitId === null) {
    formError.value = '分类和单位为必填'
    return null
  }
  if (!canUpdateCost.value && !editingRecord.value && form.status === 'ENABLED') {
    formError.value = '缺少成本维护权限，不能创建启用物料'
    return null
  }
  if (canUpdateCost.value && form.status === 'ENABLED') {
    if (
      !form.costCategory ||
      form.costCategory === 'UNCLASSIFIED' ||
      !form.inventoryValuationCategory ||
      form.inventoryValuationCategory === 'UNCLASSIFIED'
    ) {
      formError.value = '启用物料必须维护完整成本分类和库存计价分类'
      return null
    }
  }

  formError.value = ''
  return {
    materialType: form.materialType,
    sourceType: form.sourceType,
    trackingMethod: form.trackingMethod,
    categoryId,
    unitId,
    ...(canUpdateCost.value && form.costCategory ? { costCategory: form.costCategory } : {}),
    ...(canUpdateCost.value && form.inventoryValuationCategory ? {
      inventoryValuationCategory: form.inventoryValuationCategory,
    } : {}),
  }
}

async function saveRecord() {
  if (formSubmitting.value) {
    return
  }
  const validatedForm = validateMaterialForm()
  if (!validatedForm) {
    return
  }

  const payload: MaterialPayload = {
    code: form.code.trim(),
    name: form.name.trim(),
    materialType: validatedForm.materialType,
    sourceType: validatedForm.sourceType,
    trackingMethod: validatedForm.trackingMethod,
    categoryId: validatedForm.categoryId,
    unitId: validatedForm.unitId,
    status: form.status,
  }
  if (canUpdateCost.value) {
    payload.costCategory = validatedForm.costCategory
    payload.inventoryValuationCategory = validatedForm.inventoryValuationCategory
    payload.inventoryValueEnabled = form.inventoryValueEnabled
    payload.projectCostEnabled = form.projectCostEnabled
    payload.costRemark = form.costRemark.trim() || null
  }
  if (editingRecord.value) {
    payload.version = editingRecord.value.version
  }
  const specification = form.specification.trim()
  const remark = form.remark.trim()
  if (specification) {
    payload.specification = specification
  }
  if (remark) {
    payload.remark = remark
  }

  formSubmitting.value = true
  try {
    if (editingRecord.value) {
      await masterDataApi.materials.update(editingRecord.value.id, payload)
    } else {
      await masterDataApi.materials.create(payload)
    }
    formVisible.value = false
    await loadRecords()
  } catch (caught) {
    formError.value = errorMessage(caught)
  } finally {
    formSubmitting.value = false
  }
}

async function generateCode() {
  if (codeGenerating.value) {
    return
  }
  formError.value = ''
  codeGenerating.value = true
  try {
    const result = await masterDataApi.codingRules.generate({ objectType: 'MATERIAL' })
    form.code = result.generatedCode
  } catch (caught) {
    formError.value = errorMessage(caught)
  } finally {
    codeGenerating.value = false
  }
}

function currentMaterialExportFilters(): Record<string, unknown> {
  return {
    keyword: filters.keyword,
    status: filters.status,
    categoryId: normalizeOptionalId(filters.categoryId),
    materialType: filters.materialType,
    sourceType: filters.sourceType,
    trackingMethod: filters.trackingMethod,
  }
}

async function downloadMaterialTemplate() {
  actionError.value = ''
  actionLoading.value = true
  try {
    downloadFile(await documentPlatformApi.importTemplates.downloadMaterials())
  } catch (caught) {
    actionError.value = errorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function exportMaterials() {
  actionError.value = ''
  actionLoading.value = true
  try {
    latestDocumentTask.value = await documentPlatformApi.exports.createMaterials({
      filters: currentMaterialExportFilters(),
      idempotencyKey: createIdempotencyKey('material-export'),
    })
  } catch (caught) {
    actionError.value = errorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function openImport() {
  importVisible.value = true
  importFile.value = null
  importError.value = ''
}

function selectImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  importFile.value = input.files?.[0] ?? null
}

async function submitMaterialImport() {
  if (!importFile.value || importSubmitting.value) {
    importError.value = '请选择 .xlsx 导入文件'
    return
  }
  importSubmitting.value = true
  importError.value = ''
  try {
    latestDocumentTask.value = await documentPlatformApi.imports.uploadMaterials({
      file: importFile.value,
      idempotencyKey: createIdempotencyKey('material-import'),
    })
    importVisible.value = false
  } catch (caught) {
    importError.value = errorMessage(caught)
  } finally {
    importSubmitting.value = false
  }
}

async function changeStatus(record: MaterialRecord) {
  const nextAction = record.status === 'DISABLED' ? '启用' : '停用'
  if (!(await confirmAction(`确认${nextAction}物料“${record.name}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    if (record.status === 'DISABLED') {
      await masterDataApi.materials.enable(record.id, record.version === undefined ? undefined : { version: record.version })
    } else {
      await masterDataApi.materials.disable(record.id, record.version === undefined ? undefined : { version: record.version })
    }
    await loadRecords()
  } catch (caught) {
    actionError.value = errorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(() => {
  void loadReferences()
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="物料档案" description="维护生产、采购、库存和 BOM 使用的物料主数据。">
    <template #actions>
      <el-button v-if="canImportMaterials" data-test="download-material-template" @click="downloadMaterialTemplate">
        下载模板
      </el-button>
      <el-button v-if="canImportMaterials" data-test="open-material-import" @click="openImport">
        导入物料
      </el-button>
      <el-button v-if="canExportMaterials" data-test="export-materials" :loading="actionLoading" @click="exportMaterials">
        导出当前筛选
      </el-button>
      <el-button v-if="canCreate" data-test="create-material" type="primary" @click="openCreate">
        新增物料
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="material-keyword" clearable placeholder="编码或名称" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" data-test="filter-material-status" clearable placeholder="全部状态">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select
            v-model="filters.categoryId"
            data-test="filter-material-category-id"
            clearable
            placeholder="全部分类"
          >
            <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="物料类型">
          <el-select
            v-model="filters.materialType"
            data-test="filter-material-type"
            clearable
            placeholder="全部类型"
          >
            <el-option
              v-for="option in materialTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="来源属性">
          <el-select
            v-model="filters.sourceType"
            data-test="filter-source-type"
            clearable
            placeholder="全部来源"
          >
            <el-option
              v-for="option in sourceTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="追踪方式">
          <el-select
            v-model="filters.trackingMethod"
            data-test="filter-tracking-method"
            clearable
            placeholder="全部方式"
          >
            <el-option
              v-for="option in trackingMethodOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-material" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-material" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="referenceError" class="state-alert" type="error" :title="referenceError" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert
        v-if="latestDocumentTask"
        class="state-alert"
        type="success"
        :title="`已创建文档任务 ${latestDocumentTask.taskNo}`"
        :closable="false"
      />
      <el-alert
        v-if="loading || referenceLoading"
        class="state-alert"
        type="info"
        title="物料档案数据加载中"
        :closable="false"
      />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无物料档案数据'" stripe>
        <el-table-column prop="code" label="编码" min-width="150" show-overflow-tooltip />
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="specification" label="规格型号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="categoryName" label="分类" min-width="120" show-overflow-tooltip />
        <el-table-column prop="unitName" label="基本单位" min-width="100" show-overflow-tooltip />
        <el-table-column prop="businessUnitSummary" label="业务单位" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.businessUnitSummary || '仅基本单位' }}
          </template>
        </el-table-column>
        <el-table-column label="基本单位锁定" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.baseUnitImmutableReason" type="warning" size="small">已锁定</el-tag>
            <span v-else>可维护</span>
          </template>
        </el-table-column>
        <el-table-column label="成本分类" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.costCategory === 'UNCLASSIFIED' || !row.costCategory" type="warning" size="small">
              未分类
            </el-tag>
            <span v-else>{{ costCategoryLabel(row.costCategory) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="库存计价分类" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ inventoryValuationCategoryLabel(row.inventoryValuationCategory) }}
          </template>
        </el-table-column>
        <el-table-column label="成本完整性" min-width="120">
          <template #default="{ row }">
            <el-tag :type="row.costAttributeCompleted ? 'success' : 'warning'" size="small">
              {{ row.costAttributeCompleted ? '完整' : '待完善' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="物料类型" min-width="110">
          <template #default="{ row }">
            {{ materialTypeLabel(row.materialType) }}
          </template>
        </el-table-column>
        <el-table-column label="来源属性" min-width="110">
          <template #default="{ row }">
            {{ sourceTypeLabel(row.sourceType) }}
          </template>
        </el-table-column>
        <el-table-column label="追踪方式" min-width="120">
          <template #default="{ row }">
            {{ row.trackingMethodName || trackingMethodLabel(row.trackingMethod) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ masterStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" fixed="right" min-width="210">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-material" @click="openDetail(row)">详情</el-button>
            <el-button v-if="canUpdate" size="small" text data-test="edit-material" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button
              v-if="canUpdate"
              size="small"
              text
              :disabled="actionLoading"
              :type="row.status === 'DISABLED' ? 'success' : 'danger'"
              :data-test="row.status === 'DISABLED' ? 'enable-material' : 'disable-material'"
              @click="changeStatus(row)"
            >
              {{ row.status === 'DISABLED' ? '启用' : '停用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination
      class="table-pagination"
      layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]"
      :total="pagination.total"
      :page-size="pagination.pageSize"
      :current-page="pagination.page"
      @current-change="changePage" @size-change="changePageSize"
    />

    <el-dialog v-model="formVisible" :title="editingRecord ? '编辑物料' : '新增物料'" width="min(640px, 96vw)">
      <el-alert v-if="formError" class="form-alert" type="error" :title="formError" :closable="false" />
      <el-form label-position="top">
        <el-form-item label="物料编码">
          <div class="field-with-action">
            <el-input v-model="form.code" name="material-code" :disabled="Boolean(editingRecord)" />
            <el-button
              v-if="!editingRecord && canGenerateCode"
              data-test="generate-material-code"
              :loading="codeGenerating"
              :disabled="codeGenerating"
              @click="generateCode"
            >
              生成编码
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="物料名称">
          <el-input v-model="form.name" name="material-name" />
        </el-form-item>
        <el-form-item label="规格型号">
          <el-input v-model="form.specification" name="material-specification" />
        </el-form-item>
        <el-form-item label="物料类型">
          <el-select v-model="form.materialType" data-test="material-type" placeholder="请选择物料类型" style="width: 100%">
            <el-option
              v-for="option in materialTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="来源属性">
          <el-select
            v-model="form.sourceType"
            data-test="material-source-type"
            placeholder="请选择来源属性"
            style="width: 100%"
          >
            <el-option
              v-for="option in sourceTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="追踪方式">
          <el-alert
            v-if="trackingMethodImmutableReason(editingRecord)"
            class="form-alert"
            type="warning"
            :title="trackingMethodImmutableReason(editingRecord)"
            :closable="false"
          />
          <el-select
            v-model="form.trackingMethod"
            data-test="material-tracking-method"
            :disabled="Boolean(trackingMethodImmutableReason(editingRecord))"
            placeholder="请选择追踪方式"
            style="width: 100%"
          >
            <el-option
              v-for="option in trackingMethodOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="物料分类">
          <el-select
            v-model="form.categoryId"
            data-test="material-category-id"
            placeholder="请选择物料分类"
            style="width: 100%"
          >
            <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="计量单位">
          <el-alert
            v-if="baseUnitImmutableReason(editingRecord)"
            class="form-alert"
            type="warning"
            :title="baseUnitImmutableReason(editingRecord)"
            :closable="false"
          />
          <el-select
            v-model="form.unitId"
            data-test="material-unit-id"
            placeholder="请选择计量单位"
            :disabled="Boolean(baseUnitImmutableReason(editingRecord))"
            style="width: 100%"
          >
            <el-option v-for="unit in units" :key="unit.id" :label="unit.name" :value="unit.id" />
          </el-select>
        </el-form-item>
        <el-divider content-position="left">成本属性</el-divider>
        <el-alert
          v-if="!canUpdateCost"
          class="form-alert"
          type="warning"
          title="无成本维护权限，成本属性只读且不会随本次保存提交。"
          :closable="false"
        />
        <div class="material-cost-grid">
          <el-form-item label="成本分类">
            <el-select
              v-model="form.costCategory"
              data-test="material-cost-category"
              :disabled="!canUpdateCost"
              placeholder="请选择成本分类"
              style="width: 100%"
            >
              <el-option v-for="option in costCategoryOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="库存计价分类">
            <el-select
              v-model="form.inventoryValuationCategory"
              data-test="material-inventory-valuation-category"
              :disabled="!canUpdateCost"
              placeholder="请选择库存计价分类"
              style="width: 100%"
            >
              <el-option
                v-for="option in inventoryValuationCategoryOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="参与库存价值">
            <el-switch
              v-model="form.inventoryValueEnabled"
              data-test="material-inventory-value-enabled"
              :disabled="!canUpdateCost"
              active-text="参与"
              inactive-text="不参与"
            />
          </el-form-item>
          <el-form-item label="允许进入项目成本">
            <el-switch
              v-model="form.projectCostEnabled"
              data-test="material-project-cost-enabled"
              :disabled="!canUpdateCost"
              active-text="允许"
              inactive-text="不允许"
            />
          </el-form-item>
        </div>
        <el-form-item label="成本备注">
          <el-input
            v-model="form.costRemark"
            name="material-cost-remark"
            :disabled="!canUpdateCost"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" data-test="material-status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" name="material-remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button
          data-test="submit-material"
          type="primary"
          :loading="formSubmitting"
          :disabled="formSubmitting"
          @click="saveRecord"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="物料详情" size="min(420px, 92vw)">
      <dl v-if="detailRecord" class="material-detail-list">
        <dt>编码</dt>
        <dd>{{ detailRecord.code }}</dd>
        <dt>名称</dt>
        <dd>{{ detailRecord.name }}</dd>
        <dt>规格型号</dt>
        <dd>{{ detailRecord.specification || '未填写' }}</dd>
        <dt>分类</dt>
        <dd>{{ detailRecord.categoryName || '未匹配' }}</dd>
        <dt>基本单位</dt>
        <dd>{{ detailRecord.unitName || '未匹配' }}</dd>
        <dt>业务单位</dt>
        <dd>{{ detailRecord.businessUnitSummary || '仅基本单位' }}</dd>
        <dt>基本单位锁定</dt>
        <dd>{{ detailRecord.baseUnitImmutableReason || '未锁定' }}</dd>
        <dt>物料类型</dt>
        <dd>{{ materialTypeLabel(detailRecord.materialType) }}</dd>
        <dt>来源属性</dt>
        <dd>{{ sourceTypeLabel(detailRecord.sourceType) }}</dd>
        <dt>追踪方式</dt>
        <dd>{{ detailRecord.trackingMethodName || trackingMethodLabel(detailRecord.trackingMethod) }}</dd>
        <dt>状态</dt>
        <dd>{{ masterStatusLabel(detailRecord.status) }}</dd>
        <dt>成本分类</dt>
        <dd>{{ costCategoryLabel(detailRecord.costCategory) }}</dd>
        <dt>库存计价分类</dt>
        <dd>{{ inventoryValuationCategoryLabel(detailRecord.inventoryValuationCategory) }}</dd>
        <dt>库存价值</dt>
        <dd>{{ detailRecord.inventoryValueEnabled ? '参与库存价值' : '不参与库存价值' }}</dd>
        <dt>项目成本</dt>
        <dd>{{ detailRecord.projectCostEnabled ? '允许进入项目成本' : '不允许进入项目成本' }}</dd>
        <dt>成本备注</dt>
        <dd>{{ detailRecord.costRemark || '未填写' }}</dd>
        <dt>备注</dt>
        <dd>{{ detailRecord.remark || '未填写' }}</dd>
      </dl>
    </el-drawer>

    <el-dialog v-model="importVisible" title="导入物料" width="min(520px, 94vw)">
      <el-alert v-if="importError" class="form-alert" type="error" :title="importError" :closable="false" />
      <el-alert
        class="form-alert"
        type="info"
        title="导入仅新增物料，校验无误后在任务中心确认提交；不会批量更新或启停既有物料。"
        :closable="false"
      />
      <input data-test="material-import-file" type="file" accept=".xlsx" @change="selectImportFile">
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button
          data-test="submit-material-import"
          type="primary"
          :loading="importSubmitting"
          :disabled="importSubmitting"
          @click="submitMaterialImport"
        >
          上传并校验
        </el-button>
      </template>
    </el-dialog>
  </MasterDataTableView>
</template>

<style scoped>
.material-detail-list {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 10px 14px;
  margin: 0;
}

.material-detail-list dt {
  color: var(--qherp-muted);
}

.material-detail-list dd {
  min-width: 0;
  margin: 0;
  word-break: break-word;
}

.field-with-action {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
}

.material-cost-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

@media (max-width: 760px) {
  .field-with-action,
  .material-cost-grid {
    grid-template-columns: 1fr;
  }
}
</style>
