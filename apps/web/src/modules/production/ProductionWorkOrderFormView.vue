<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { queryWithReturnTo, routeReturnTo } from '../../shared/navigation/navigationReturn'
import { bomApi, type BomDetailRecord, type BomSummaryRecord } from '../../shared/api/bomApi'
import { masterDataApi, type MaterialRecord, type WarehouseRecord } from '../../shared/api/masterDataApi'
import {
  projectProductionApi,
  type ProductionOwnershipType,
  type ProjectProductionWorkOrderDetailRecord,
  type ProjectProductionWorkOrderPayload,
  type ResourceId,
} from '../../shared/api/projectProductionApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { materialTypeLabel } from '../master/shared/masterPageHelpers'
import { pageItems } from '../system/shared/pageHelpers'
import ProductionWorkOrderStatusTag from './ProductionWorkOrderStatusTag.vue'
import {
  formatProductionQuantity,
  createProductionIdempotencyKey,
  productionErrorMessage,
  validateProductionQuantity,
} from './productionPageHelpers'

const route = useRoute()
const router = useRouter()
const materials = ref<MaterialRecord[]>([])
const bomOptions = ref<BomSummaryRecord[]>([])
const warehouses = ref<WarehouseRecord[]>([])
const selectedBomDetail = ref<BomDetailRecord | null>(null)
const editingRecord = ref<ProjectProductionWorkOrderDetailRecord | null>(null)
const referenceLoading = ref(true)
const loading = ref(false)
const loadingRecord = ref(false)
const referenceError = ref('')
const formError = ref('')
const formSubmitting = ref(false)
const bomLoading = ref(false)
const bomOptionsLoading = ref(false)
const bomSelectionWarning = ref('')
const bomOptionsError = ref('')

const BOM_EFFECTIVE_DATE_INVALID_MESSAGE = '所选 BOM 在计划开工日期不生效，请选择有效 BOM 或调整计划开工日期'
const BOM_CANDIDATE_ERROR_PREFIX = 'BOM 候选加载失败：'

let bomOptionsRequestId = 0
let bomDetailRequestId = 0

const form = reactive({
  ownershipType: 'PUBLIC' as ProductionOwnershipType,
  projectId: '' as ResourceId | '',
  productMaterialId: '' as ResourceId | '',
  bomId: '' as ResourceId | '',
  plannedQuantity: '',
  plannedStartDate: '',
  plannedFinishDate: '',
  issueWarehouseId: '' as ResourceId | '',
  receiptWarehouseId: '' as ResourceId | '',
  remark: '',
})

const isEdit = computed(() => Boolean(route.params.id))
const isDraftRecord = computed(() => !editingRecord.value || editingRecord.value.status === 'DRAFT')
const sourceLocked = computed(() => Boolean(editingRecord.value?.sourceMrpSuggestionId || editingRecord.value?.sourceSummary?.sourceMrpSuggestionId))
const projectContextDisabled = computed(() => !isDraftRecord.value || sourceLocked.value)
const pageTitle = computed(() => (isEdit.value ? '编辑生产工单' : '新建生产工单'))
const productMaterials = computed(() => materials.value.filter((material) => (
  material.materialType === 'FINISHED_GOOD' || material.materialType === 'SEMI_FINISHED'
)))
const availableBoms = computed(() => bomOptions.value)
const hasBomContext = computed(() => Boolean(form.productMaterialId && form.plannedStartDate.trim()))
const bomSelectDisabled = computed(() => !isDraftRecord.value || !hasBomContext.value || sourceLocked.value)
const bomSelectPlaceholder = computed(() => (hasBomContext.value ? '请选择有效 BOM' : '请先选择产品物料和计划开工日期'))
const saveDisabled = computed(() => formSubmitting.value || !isDraftRecord.value || bomOptionsLoading.value || bomLoading.value)
const bomSelectionHint = computed(() => {
  if (!isDraftRecord.value) {
    return ''
  }
  if (bomOptionsError.value) {
    return bomOptionsError.value
  }
  if (bomSelectionWarning.value) {
    return bomSelectionWarning.value
  }
  if (!hasBomContext.value) {
    return '请先选择产品物料和计划开工日期'
  }
  if (!bomOptionsLoading.value && availableBoms.value.length === 0) {
    return '该产品在所选计划开工日期无有效 BOM'
  }
  return ''
})

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

function isBomScopedFormError() {
  return formError.value === BOM_EFFECTIVE_DATE_INVALID_MESSAGE
    || formError.value.startsWith(BOM_CANDIDATE_ERROR_PREFIX)
}

function clearBomScopedErrors() {
  bomSelectionWarning.value = ''
  bomOptionsError.value = ''
  if (isBomScopedFormError()) {
    formError.value = ''
  }
}

function setBomSelectionInvalid() {
  bomSelectionWarning.value = BOM_EFFECTIVE_DATE_INVALID_MESSAGE
  bomOptionsError.value = ''
  if (!formError.value || isBomScopedFormError()) {
    formError.value = BOM_EFFECTIVE_DATE_INVALID_MESSAGE
  }
}

function setBomOptionsError(caught: unknown) {
  bomOptionsError.value = `${BOM_CANDIDATE_ERROR_PREFIX}${productionErrorMessage(caught)}`
  bomSelectionWarning.value = ''
  if (!formError.value || isBomScopedFormError()) {
    formError.value = bomOptionsError.value
  }
}

function isLatestBomOptionsRequest(requestId: number, productMaterialId: ResourceId, effectiveDate: string) {
  return requestId === bomOptionsRequestId
    && String(normalizeOptionalId(form.productMaterialId)) === String(productMaterialId)
    && form.plannedStartDate.trim() === effectiveDate
}

function isLatestBomDetailRequest(requestId: number, bomId: ResourceId) {
  return requestId === bomDetailRequestId && String(normalizeOptionalId(form.bomId)) === String(bomId)
}

type WorkOrderBomContext = ProjectProductionWorkOrderDetailRecord & {
  bomId: ResourceId
  bomCode: string
  bomVersionCode: string
}

function hasWorkOrderBomContext(detail: ProjectProductionWorkOrderDetailRecord): detail is WorkOrderBomContext {
  return detail.bomId !== null
    && detail.bomId !== undefined
    && Boolean(detail.bomCode)
    && Boolean(detail.bomVersionCode)
}

function workOrderBomOption(detail: WorkOrderBomContext): BomSummaryRecord {
  return {
    id: detail.bomId,
    bomCode: detail.bomCode,
    parentMaterialId: detail.productMaterialId,
    parentMaterialCode: detail.productMaterialCode,
    parentMaterialName: detail.productMaterialName,
    versionCode: detail.bomVersionCode,
    name: detail.bomCode,
    baseQuantity: '',
    baseUnitId: '',
    baseUnitName: '',
    status: 'ENABLED',
    effectiveFrom: null,
    effectiveTo: null,
    itemCount: detail.materials.length,
    version: 0,
  }
}

async function loadReferences() {
  referenceLoading.value = true
  referenceError.value = ''
  try {
    const [materialPage, warehousePage] = await Promise.all([
      masterDataApi.materials.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
      masterDataApi.warehouses.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
    ])
    materials.value = pageItems(materialPage)
    warehouses.value = pageItems(warehousePage)
  } catch (caught) {
    materials.value = []
    warehouses.value = []
    referenceError.value = productionErrorMessage(caught)
  } finally {
    referenceLoading.value = false
  }
}

async function loadRecord() {
  if (!route.params.id) {
    return
  }
  loading.value = true
  loadingRecord.value = true
  formError.value = ''
  try {
    const detail = await projectProductionApi.workOrders.get(route.params.id as ResourceId)
    editingRecord.value = detail
    form.ownershipType = detail.ownershipType ?? (detail.projectId ? 'PROJECT' : 'PUBLIC')
    form.projectId = detail.projectId ?? ''
    form.productMaterialId = detail.productMaterialId
    form.bomId = detail.bomId ?? ''
    form.plannedQuantity = String(detail.plannedQuantity)
    form.plannedStartDate = detail.plannedStartDate ?? ''
    form.plannedFinishDate = detail.plannedFinishDate ?? ''
    form.issueWarehouseId = detail.issueWarehouseId ?? ''
    form.receiptWarehouseId = detail.receiptWarehouseId ?? ''
    form.remark = detail.remark ?? ''
    if (!isDraftRecord.value && hasWorkOrderBomContext(detail)) {
      bomOptions.value = [workOrderBomOption(detail)]
    } else if (!isDraftRecord.value) {
      bomOptions.value = []
    } else {
      await loadEffectiveBoms()
    }
    if (form.bomId) {
      await loadBomDetail(form.bomId)
    }
  } catch (caught) {
    formError.value = productionErrorMessage(caught)
  } finally {
    loadingRecord.value = false
    loading.value = false
  }
}

async function loadBomDetail(bomId: ResourceId | '') {
  const requestId = ++bomDetailRequestId
  const normalizedBomId = normalizeOptionalId(bomId)
  selectedBomDetail.value = null
  if (normalizedBomId === undefined) {
    return
  }
  bomLoading.value = true
  try {
    const detail = await bomApi.get(normalizedBomId)
    if (!isLatestBomDetailRequest(requestId, normalizedBomId)) {
      return
    }
    selectedBomDetail.value = detail
    clearBomScopedErrors()
  } catch (caught) {
    if (isLatestBomDetailRequest(requestId, normalizedBomId)) {
      formError.value = productionErrorMessage(caught)
    }
  } finally {
    if (requestId === bomDetailRequestId) {
      bomLoading.value = false
    }
  }
}

function formatBomEffectiveRange(record: BomSummaryRecord) {
  return `${record.effectiveFrom || '-'} 至 ${record.effectiveTo || '-'}`
}

async function loadEffectiveBoms() {
  const requestId = ++bomOptionsRequestId
  const productMaterialId = normalizeOptionalId(form.productMaterialId)
  const effectiveDate = form.plannedStartDate.trim()
  clearBomScopedErrors()
  if (productMaterialId === undefined || !effectiveDate) {
    bomOptions.value = []
    bomOptionsLoading.value = false
    return
  }

  bomOptionsLoading.value = true
  try {
    const page = await bomApi.list({
      keyword: '',
      status: 'ENABLED',
      parentMaterialId: productMaterialId,
      effectiveDate,
      page: 1,
      pageSize: 20,
    })
    if (!isLatestBomOptionsRequest(requestId, productMaterialId, effectiveDate)) {
      return
    }
    const items = pageItems(page)
    bomOptions.value = items
    const selectedBomId = normalizeOptionalId(form.bomId)
    if (selectedBomId !== undefined && !items.some((item) => String(item.id) === String(selectedBomId))) {
      form.bomId = ''
      selectedBomDetail.value = null
      setBomSelectionInvalid()
    }
  } catch (caught) {
    if (isLatestBomOptionsRequest(requestId, productMaterialId, effectiveDate)) {
      bomOptions.value = []
      setBomOptionsError(caught)
    }
  } finally {
    if (requestId === bomOptionsRequestId) {
      bomOptionsLoading.value = false
    }
  }
}

function clearSelectedBom() {
  bomDetailRequestId += 1
  form.bomId = ''
  selectedBomDetail.value = null
}

function clearBomForContextChange() {
  clearSelectedBom()
  bomOptions.value = []
  clearBomScopedErrors()
}

function validateForm(): Omit<ProjectProductionWorkOrderPayload, 'idempotencyKey'> | null {
  const productMaterialId = normalizeRequiredId(form.productMaterialId)
  const bomId = normalizeRequiredId(form.bomId)
  const issueWarehouseId = normalizeRequiredId(form.issueWarehouseId)
  const receiptWarehouseId = normalizeRequiredId(form.receiptWarehouseId)
  const projectId = normalizeOptionalId(form.projectId)

  if (bomOptionsLoading.value || bomLoading.value) {
    formError.value = 'BOM 信息加载中，请稍后保存'
    return null
  }
  if (bomOptionsError.value) {
    formError.value = bomOptionsError.value
    return null
  }
  if (bomSelectionWarning.value) {
    formError.value = bomSelectionWarning.value
    return null
  }
  if (productMaterialId === null || bomId === null || issueWarehouseId === null || receiptWarehouseId === null) {
    formError.value = '请完整选择产品物料、BOM、领料仓库和入库仓库'
    return null
  }
  if (form.ownershipType === 'PROJECT' && projectId === undefined) {
    formError.value = '项目工单必须填写项目 ID'
    return null
  }
  if (!availableBoms.value.some((item) => String(item.id) === String(bomId))) {
    setBomSelectionInvalid()
    return null
  }
  if (!form.plannedStartDate.trim() || !form.plannedFinishDate.trim()) {
    formError.value = '请填写计划开工日期和计划完工日期'
    return null
  }
  if (form.plannedFinishDate < form.plannedStartDate) {
    formError.value = '计划完工日期不得早于计划开工日期'
    return null
  }
  const quantityResult = validateProductionQuantity(form.plannedQuantity)
  if (quantityResult.payloadValue === null) {
    formError.value = quantityResult.message ?? '计划数量不正确'
    return null
  }

  formError.value = ''
  return {
    ownershipType: form.ownershipType,
    ...(form.ownershipType === 'PROJECT' ? { projectId } : { projectId: null }),
    productMaterialId,
    bomId,
    plannedQuantity: quantityResult.payloadValue,
    issueWarehouseId,
    receiptWarehouseId,
    plannedStartDate: form.plannedStartDate.trim(),
    plannedFinishDate: form.plannedFinishDate.trim(),
    ...(form.remark.trim() ? { remark: form.remark.trim() } : {}),
  }
}

async function saveWorkOrder() {
  if (formSubmitting.value || bomOptionsLoading.value || bomLoading.value) {
    return
  }
  if (!isDraftRecord.value) {
    formError.value = '仅草稿工单可编辑'
    return
  }
  const payload = validateForm()
  if (!payload) {
    return
  }
    formSubmitting.value = true
  try {
    const idempotencyKey = createProductionIdempotencyKey('production-work-order-save')
    const result = editingRecord.value
      ? await projectProductionApi.workOrders.update(editingRecord.value.id, {
        ...payload,
        version: editingRecord.value.version,
        idempotencyKey,
      })
      : await projectProductionApi.workOrders.create({
        ...payload,
        idempotencyKey,
      })
    await router.push({
      name: 'production-work-order-detail',
      params: { id: String(result.id) },
      query: queryWithReturnTo({}, routeReturnTo(route)),
    })
  } catch (caught) {
    formError.value = productionErrorMessage(caught)
  } finally {
    formSubmitting.value = false
  }
}

function cancel() {
  if (editingRecord.value) {
    void router.push({
      name: 'production-work-order-detail',
      params: { id: String(editingRecord.value.id) },
      query: queryWithReturnTo({}, routeReturnTo(route)),
    })
    return
  }
  void router.push({ name: 'production-work-orders' })
}

watch(() => form.productMaterialId, () => {
  if (loadingRecord.value || !isDraftRecord.value || sourceLocked.value) {
    return
  }
  clearBomForContextChange()
  void loadEffectiveBoms()
})

watch(() => form.plannedStartDate, () => {
  if (loadingRecord.value || !isDraftRecord.value || sourceLocked.value) {
    return
  }
  clearBomForContextChange()
  void loadEffectiveBoms()
})

watch(() => form.bomId, (bomId) => {
  if (loadingRecord.value) {
    return
  }
  void loadBomDetail(bomId)
})

onMounted(async () => {
  await loadReferences()
  await loadRecord()
})
</script>

<template>
  <MasterDataTableView :title="pageTitle" description="维护生产工单草稿，发布后生成 BOM 用料快照并进入执行。">
    <template #alerts>
      <el-alert v-if="referenceError" class="state-alert" type="error" :title="referenceError" :closable="false" />
      <el-alert v-if="formError" class="state-alert" type="error" :title="formError" :closable="false" />
      <el-alert v-if="loading || referenceLoading" class="state-alert" type="info" title="生产工单表单加载中" :closable="false" />
      <el-alert v-if="editingRecord && !isDraftRecord" class="state-alert" type="warning" title="仅草稿工单可编辑" :closable="false" />
    </template>

    <el-form label-position="top" class="production-form">
      <div v-if="editingRecord" class="edit-status">
        <span>当前状态</span>
        <ProductionWorkOrderStatusTag :status="editingRecord.status" />
      </div>
      <section v-if="editingRecord?.sourceMrpSuggestionId || editingRecord?.sourceSummary || editingRecord?.projectId" class="source-summary">
        <div>
          <span>项目归属</span>
          <strong>
            {{ form.ownershipType === 'PROJECT' ? `${editingRecord?.projectNo || form.projectId || '-'} ${editingRecord?.projectName || ''}` : '公共工单' }}
          </strong>
        </div>
        <div>
          <span>来源建议</span>
          <strong>{{ editingRecord?.sourceSummary?.sourceSuggestionNo || editingRecord?.sourceSuggestionNo || '-' }}</strong>
        </div>
        <div>
          <span>来源计划</span>
          <strong>{{ editingRecord?.sourceSummary?.sourceRunNo || editingRecord?.sourceMrpRunId || '-' }}</strong>
        </div>
      </section>
      <div class="production-form-grid">
        <el-form-item label="生产归属">
          <el-radio-group
            v-model="form.ownershipType"
            data-test="production-ownership-type"
            :disabled="projectContextDisabled"
          >
            <el-radio-button value="PUBLIC">公共工单</el-radio-button>
            <el-radio-button value="PROJECT">项目工单</el-radio-button>
          </el-radio-group>
          <div v-if="sourceLocked" class="field-hint">来源建议已绑定，归属不可改</div>
        </el-form-item>
        <el-form-item label="项目 ID">
          <el-input
            v-model="form.projectId"
            name="production-project-id"
            placeholder="项目工单必填"
            :disabled="projectContextDisabled || form.ownershipType !== 'PROJECT'"
          />
        </el-form-item>
        <el-form-item label="产品物料">
          <el-select
            v-model="form.productMaterialId"
            data-test="production-product-material-id"
            filterable
            placeholder="请选择成品或半成品"
            style="width: 100%"
            :disabled="!isDraftRecord || sourceLocked"
          >
            <el-option
              v-for="material in productMaterials"
              :key="material.id"
              :label="`${material.code} ${material.name}`"
              :value="material.id"
            >
              <span>{{ material.code }} {{ material.name }}</span>
              <span class="option-meta">{{ materialTypeLabel(material.materialType) }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="BOM">
          <el-select
            v-model="form.bomId"
            data-test="production-bom-id"
            filterable
            :loading="bomOptionsLoading"
            :placeholder="bomSelectPlaceholder"
            style="width: 100%"
            :disabled="bomSelectDisabled"
          >
            <el-option
              v-for="bom in availableBoms"
              :key="bom.id"
              :label="`${bom.bomCode} / ${bom.versionCode} / ${formatBomEffectiveRange(bom)}`"
              :value="bom.id"
            >
              <span>{{ bom.bomCode }} / {{ bom.versionCode }}</span>
              <span class="option-meta">{{ formatBomEffectiveRange(bom) }}</span>
            </el-option>
          </el-select>
          <div v-if="bomSelectionHint" class="field-hint">{{ bomSelectionHint }}</div>
        </el-form-item>
        <el-form-item label="计划数量">
          <el-input v-model="form.plannedQuantity" name="production-planned-quantity" placeholder="大于 0，最多 6 位小数" :disabled="!isDraftRecord" />
        </el-form-item>
        <el-form-item label="计划开工日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="form.plannedStartDate" name="production-planned-start-date" placeholder="选择日期" :disabled="!isDraftRecord" />
        </el-form-item>
        <el-form-item label="计划完工日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="form.plannedFinishDate" name="production-planned-finish-date" placeholder="选择日期" :disabled="!isDraftRecord" />
        </el-form-item>
        <el-form-item label="领料仓库">
          <el-select v-model="form.issueWarehouseId" filterable placeholder="请选择领料仓库" style="width: 100%" :disabled="!isDraftRecord">
            <el-option v-for="warehouse in warehouses" :key="warehouse.id" :label="warehouse.name" :value="warehouse.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="入库仓库">
          <el-select v-model="form.receiptWarehouseId" filterable placeholder="请选择入库仓库" style="width: 100%" :disabled="!isDraftRecord">
            <el-option v-for="warehouse in warehouses" :key="warehouse.id" :label="warehouse.name" :value="warehouse.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" name="production-work-order-remark" placeholder="可选" :disabled="!isDraftRecord" />
        </el-form-item>
      </div>
    </el-form>

    <section class="section-block">
      <div class="section-title">BOM 明细预览</div>
      <el-alert v-if="bomLoading" class="state-alert" type="info" title="BOM 明细加载中" :closable="false" />
      <el-empty v-if="!selectedBomDetail && !bomLoading" description="选择 BOM 后显示用料明细" />
      <div v-if="selectedBomDetail" class="table-scroll">
        <el-table :data="selectedBomDetail.items" empty-text="暂无 BOM 明细" stripe>
          <el-table-column prop="lineNo" label="行号" width="78" />
          <el-table-column label="子项物料" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.childMaterialCode }} {{ row.childMaterialName }}
            </template>
          </el-table-column>
          <el-table-column label="用量" min-width="110" align="right">
            <template #default="{ row }">
              <span class="numeric-cell">{{ formatProductionQuantity(row.businessQuantity ?? row.quantity) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="单位" min-width="90">
            <template #default="{ row }">
              {{ row.businessUnitName ?? row.unitName ?? '-' }}
            </template>
          </el-table-column>
          <el-table-column label="损耗率" min-width="100" align="right">
            <template #default="{ row }">
              <span class="numeric-cell">{{ formatProductionQuantity(row.lossRate) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        </el-table>
      </div>
    </section>

    <div class="form-footer">
      <el-button @click="cancel">取消</el-button>
      <el-button
        data-test="save-production-work-order"
        type="primary"
        :loading="formSubmitting"
        :disabled="saveDisabled"
        @click="saveWorkOrder"
      >
        保存
      </el-button>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.production-form {
  padding: 14px;
}

.production-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.edit-status {
  align-items: center;
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.edit-status span {
  color: var(--qherp-muted);
}

.source-summary {
  border: 1px solid var(--qherp-border);
  border-radius: 6px;
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-bottom: 12px;
  padding: 10px;
}

.source-summary span {
  color: var(--qherp-muted);
  display: block;
  font-size: 12px;
}

.source-summary strong {
  display: block;
  margin-top: 4px;
  word-break: break-word;
}

.section-block {
  padding: 0 14px 14px;
}

.section-title {
  font-weight: 600;
  margin-bottom: 10px;
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 14px 14px;
  border-top: 1px solid var(--qherp-border);
}

.numeric-cell {
  display: inline-block;
  min-width: 72px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.option-meta {
  color: var(--qherp-muted);
  float: right;
  font-size: 12px;
  margin-left: 12px;
}

@media (max-width: 760px) {
  .production-form-grid {
    grid-template-columns: 1fr;
  }

  .source-summary {
    grid-template-columns: 1fr;
  }

  .form-footer {
    align-items: stretch;
    flex-direction: column-reverse;
  }
}
</style>
