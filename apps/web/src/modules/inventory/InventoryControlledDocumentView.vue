<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createIdempotencyKey } from '../../shared/api/documentPlatformApi'
import type { PageResult } from '../../shared/api/accountPermissionApi'
import {
  inventoryApi,
  type InventoryAllowedAction,
  type InventoryControlledDocumentActionPayload,
  type InventoryControlledDocumentSummaryRecord,
  type InventoryOwnershipConversionPayload,
  type InventoryOwnershipConversionRecord,
  type InventoryStocktakeLineRecord,
  type InventoryStocktakeLineUpdatePayload,
  type InventoryStocktakePayload,
  type InventoryStocktakeRecord,
  type InventoryValuationAdjustmentPayload,
  type InventoryValuationAdjustmentRecord,
  type InventoryWarehouseTransferPayload,
  type InventoryWarehouseTransferRecord,
  type ResourceId,
} from '../../shared/api/inventoryApi'
import { useAuthStore } from '../../stores/authStore'
import ApprovalStatusPanel from '../platform/components/ApprovalStatusPanel.vue'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { errorMessage, pageItems } from '../system/shared/pageHelpers'
import {
  formatInventoryAmount,
  formatQuantity,
  inventoryActionLabel,
  ownershipTypeLabel,
} from './inventoryPageHelpers'

type DocumentKind = 'warehouseTransfer' | 'ownershipConversion' | 'stocktake' | 'valuationAdjustment'
type Mode = 'list' | 'create' | 'detail' | 'edit'
type ApiBucket = {
  list(params: { keyword?: string; status?: string; page: number; pageSize: number }): Promise<PageResult<InventoryRecord>>
  get(id: ResourceId): Promise<InventoryRecord>
  create?(payload: object): Promise<InventoryRecord>
  update?(id: ResourceId, payload: object): Promise<InventoryRecord>
  post?(id: ResourceId, payload: InventoryControlledDocumentActionPayload): Promise<InventoryRecord>
  submitApproval?(id: ResourceId, payload: InventoryControlledDocumentActionPayload): Promise<InventoryRecord>
  withdraw?(id: ResourceId, payload: InventoryControlledDocumentActionPayload): Promise<InventoryRecord>
  start?(id: ResourceId, payload: InventoryControlledDocumentActionPayload): Promise<InventoryRecord>
  updateLines?(id: ResourceId, payload: InventoryStocktakeLineUpdatePayload): Promise<InventoryRecord>
  reconcile?(id: ResourceId, payload: InventoryControlledDocumentActionPayload): Promise<InventoryRecord>
  completeZeroVariance?(id: ResourceId, payload: InventoryControlledDocumentActionPayload): Promise<InventoryRecord>
  cancel?(id: ResourceId, payload: InventoryControlledDocumentActionPayload): Promise<InventoryRecord>
}
type InventoryRecord =
  | InventoryWarehouseTransferRecord
  | InventoryOwnershipConversionRecord
  | InventoryStocktakeRecord
  | InventoryValuationAdjustmentRecord

interface DocumentConfig {
  kind: DocumentKind
  title: string
  description: string
  listRouteName: string
  createRouteName: string
  detailRouteName: string
  editRouteName: string
  createPermission: string
  updatePermission: string
  postPermission?: string
  submitPermission?: string
  withdrawPermission?: string
  cancelPermission: string
  api: ApiBucket
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const configs: Record<DocumentKind, DocumentConfig> = {
  warehouseTransfer: {
    kind: 'warehouseTransfer',
    title: '仓库调拨',
    description: '维护仓库间数量调拨草稿，过账后成对形成出入库流水且不改变企业总库存价值。',
    listRouteName: 'inventory-warehouse-transfers',
    createRouteName: 'inventory-warehouse-transfer-create',
    detailRouteName: 'inventory-warehouse-transfer-detail',
    editRouteName: 'inventory-warehouse-transfer-edit',
    createPermission: 'inventory:warehouse-transfer:create',
    updatePermission: 'inventory:warehouse-transfer:update',
    postPermission: 'inventory:warehouse-transfer:post',
    cancelPermission: 'inventory:warehouse-transfer:cancel',
    api: inventoryApi.warehouseTransfers,
  },
  ownershipConversion: {
    kind: 'ownershipConversion',
    title: '所有权转换',
    description: '维护公共库存和项目库存之间的受控转换，提交固定审批后由后端原子过账。',
    listRouteName: 'inventory-ownership-conversions',
    createRouteName: 'inventory-ownership-conversion-create',
    detailRouteName: 'inventory-ownership-conversion-detail',
    editRouteName: 'inventory-ownership-conversion-edit',
    createPermission: 'inventory:ownership-conversion:create',
    updatePermission: 'inventory:ownership-conversion:update',
    submitPermission: 'inventory:ownership-conversion:submit',
    withdrawPermission: 'inventory:ownership-conversion:withdraw',
    cancelPermission: 'inventory:ownership-conversion:cancel',
    api: inventoryApi.ownershipConversions,
  },
  stocktake: {
    kind: 'stocktake',
    title: '库存盘点',
    description: '维护盘点快照、实盘录入和差异确认，未盘与实盘为零必须明确区分。',
    listRouteName: 'inventory-stocktakes',
    createRouteName: 'inventory-stocktake-create',
    detailRouteName: 'inventory-stocktake-detail',
    editRouteName: 'inventory-stocktake-edit',
    createPermission: 'inventory:stocktake:create',
    updatePermission: 'inventory:stocktake:update',
    submitPermission: 'inventory:stocktake:submit',
    cancelPermission: 'inventory:stocktake:cancel',
    api: inventoryApi.stocktakes,
  },
  valuationAdjustment: {
    kind: 'valuationAdjustment',
    title: '估值调整',
    description: '维护历史期初估值和暂估调整，数量不由前端重算，金额以后端响应为准。',
    listRouteName: 'inventory-valuation-adjustments',
    createRouteName: 'inventory-valuation-adjustment-create',
    detailRouteName: 'inventory-valuation-adjustment-detail',
    editRouteName: 'inventory-valuation-adjustment-edit',
    createPermission: 'inventory:valuation-adjustment:create',
    updatePermission: 'inventory:valuation-adjustment:update',
    submitPermission: 'inventory:valuation-adjustment:submit',
    withdrawPermission: 'inventory:valuation-adjustment:withdraw',
    cancelPermission: 'inventory:valuation-adjustment:cancel',
    api: inventoryApi.valuationAdjustments,
  },
}

const routeNameText = computed(() => String(route.name ?? ''))
const config = computed<DocumentConfig>(() => {
  const name = routeNameText.value
  if (name.includes('ownership-conversion')) {
    return configs.ownershipConversion
  }
  if (name.includes('stocktake')) {
    return configs.stocktake
  }
  if (name.includes('valuation-adjustment')) {
    return configs.valuationAdjustment
  }
  return configs.warehouseTransfer
})
const mode = computed<Mode>(() => {
  const name = routeNameText.value
  if (name.endsWith('-create')) {
    return 'create'
  }
  if (name.endsWith('-edit')) {
    return 'edit'
  }
  if (name.endsWith('-detail')) {
    return 'detail'
  }
  return 'list'
})
const isList = computed(() => mode.value === 'list')
const isForm = computed(() => mode.value === 'create' || mode.value === 'edit')
const recordId = computed(() => route.params.id as ResourceId | undefined)

const filters = reactive({ keyword: '', status: '' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<InventoryRecord[]>([])
const record = ref<InventoryRecord | null>(null)
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')
const stocktakeActualInputs = reactive<Record<string, string>>({})
const form = reactive({
  businessDate: '',
  reason: '',
  remark: '',
  sourceWarehouseId: '',
  targetWarehouseId: '',
  warehouseId: '',
  materialId: '',
  unitId: '',
  ownershipType: 'PUBLIC' as 'PUBLIC' | 'PROJECT',
  projectId: '',
  sourceOwnershipType: 'PUBLIC' as 'PUBLIC' | 'PROJECT',
  targetOwnershipType: 'PROJECT' as 'PUBLIC' | 'PROJECT',
  sourceProjectId: '',
  targetProjectId: '',
  sourceCostLayerId: '',
  sourceUnitCost: '',
  costLayerId: '',
  adjustmentType: 'LEGACY_OPENING' as 'LEGACY_OPENING' | 'PROVISIONAL_REVALUATION',
  quantity: '',
  unitCost: '',
  adjustmentAmount: '',
})

const canCreate = computed(() => authStore.hasPermission(config.value.createPermission))
const canUpdate = computed(() => authStore.hasPermission(config.value.updatePermission))
const canCancel = computed(() => authStore.hasPermission(config.value.cancelPermission))
const canPost = computed(() => !config.value.postPermission || authStore.hasPermission(config.value.postPermission))
const canSubmit = computed(() => !config.value.submitPermission || authStore.hasPermission(config.value.submitPermission))
const canWithdraw = computed(() => !config.value.withdrawPermission || authStore.hasPermission(config.value.withdrawPermission))

function normalizeId(value: ResourceId | ''): ResourceId {
  const text = String(value).trim()
  return /^\d+$/.test(text) ? Number(text) : text
}

function normalizeOptionalId(value: ResourceId | ''): ResourceId | undefined {
  const text = String(value).trim()
  return text ? normalizeId(text) : undefined
}

function stringifyOptional(value: unknown) {
  return value === null || value === undefined ? '' : String(value)
}

function allowed(recordValue: InventoryControlledDocumentSummaryRecord, action: InventoryAllowedAction | string): boolean {
  return (recordValue.allowedActions ?? []).includes(action)
}

function actionVisible(recordValue: InventoryRecord, action: InventoryAllowedAction | string): boolean {
  if (!allowed(recordValue, action)) {
    return false
  }
  if (action === 'UPDATE') {
    return canUpdate.value
  }
  if (action === 'POST') {
    return canPost.value
  }
  if (action === 'START' || action === 'UPDATE_LINES' || action === 'RECONCILE' || action === 'COMPLETE_ZERO_VARIANCE') {
    return canUpdate.value
  }
  if (action === 'SUBMIT_APPROVAL') {
    return canSubmit.value
  }
  if (action === 'WITHDRAW') {
    return canWithdraw.value
  }
  if (action === 'CANCEL') {
    return canCancel.value
  }
  return true
}

function actionPayload(recordValue: InventoryRecord): InventoryControlledDocumentActionPayload {
  return {
    version: recordValue.version,
    idempotencyKey: createIdempotencyKey(`inventory-${config.value.kind}`),
  }
}

function documentNo(recordValue: InventoryRecord) {
  return recordValue.documentNo
}

function statusText(recordValue: InventoryRecord) {
  return recordValue.statusName || String(recordValue.status)
}

function approvalStatusText(recordValue: InventoryRecord) {
  const status = recordValue.approvalSummary?.status
  if (!status) {
    return '-'
  }
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    SUBMITTED: '审批中',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    WITHDRAWN: '已撤回',
    CANCELLED: '已取消',
  }
  return labels[String(status)] ?? String(status)
}

function amountImpactText(recordValue: InventoryRecord) {
  return recordValue.amountImpactSummary || recordValue.keyInfoSummary || '-'
}

function formattedDateTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}

function routeToCreate() {
  void router.push({ name: config.value.createRouteName })
}

function routeToDetail(recordValue: InventoryRecord) {
  void router.push({ name: config.value.detailRouteName, params: { id: String(recordValue.id) } })
}

function routeToEdit(recordValue: InventoryRecord) {
  void router.push({ name: config.value.editRouteName, params: { id: String(recordValue.id) } })
}

async function loadList() {
  loading.value = true
  error.value = ''
  try {
    const page = await config.value.api.list({
      keyword: filters.keyword.trim(),
      status: filters.status,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = pageItems(page)
    pagination.total = Number((page as { total?: number }).total ?? 0)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

function fillStocktakeInputs(lines: InventoryStocktakeLineRecord[] = []) {
  Object.keys(stocktakeActualInputs).forEach((key) => {
    delete stocktakeActualInputs[key]
  })
  lines.forEach((line) => {
    stocktakeActualInputs[String(line.id)] = line.countedQuantity ?? ''
  })
}

function resetForm() {
  form.businessDate = ''
  form.reason = ''
  form.remark = ''
  form.sourceWarehouseId = ''
  form.targetWarehouseId = ''
  form.warehouseId = ''
  form.materialId = ''
  form.unitId = ''
  form.ownershipType = 'PUBLIC'
  form.projectId = ''
  form.sourceOwnershipType = 'PUBLIC'
  form.targetOwnershipType = 'PROJECT'
  form.sourceProjectId = ''
  form.targetProjectId = ''
  form.sourceCostLayerId = ''
  form.sourceUnitCost = ''
  form.costLayerId = ''
  form.adjustmentType = 'LEGACY_OPENING'
  form.quantity = ''
  form.unitCost = ''
  form.adjustmentAmount = ''
}

function fillFormFromRecord(detail: InventoryRecord) {
  resetForm()
  form.businessDate = detail.businessDate
  form.reason = detail.reason
  form.remark = stringifyOptional((detail as { remark?: string | null }).remark)
  if (config.value.kind === 'warehouseTransfer') {
    const line = (detail as InventoryWarehouseTransferRecord).lines?.[0]
    form.sourceWarehouseId = stringifyOptional(line?.sourceWarehouseId)
    form.targetWarehouseId = stringifyOptional(line?.targetWarehouseId)
    form.materialId = stringifyOptional(line?.materialId)
    form.unitId = stringifyOptional(line?.unitId)
    form.ownershipType = line?.ownershipType === 'PROJECT' ? 'PROJECT' : 'PUBLIC'
    form.projectId = stringifyOptional(line?.projectId)
    form.sourceCostLayerId = stringifyOptional(line?.sourceCostLayerId)
    form.quantity = stringifyOptional(line?.quantity)
    return
  }
  if (config.value.kind === 'ownershipConversion') {
    const line = (detail as InventoryOwnershipConversionRecord).lines?.[0]
    form.sourceOwnershipType = line?.sourceOwnershipType === 'PROJECT' ? 'PROJECT' : 'PUBLIC'
    form.targetOwnershipType = line?.targetOwnershipType === 'PUBLIC' ? 'PUBLIC' : 'PROJECT'
    form.sourceWarehouseId = stringifyOptional(line?.sourceWarehouseId)
    form.targetWarehouseId = stringifyOptional(line?.targetWarehouseId)
    form.sourceProjectId = stringifyOptional(line?.sourceProjectId)
    form.targetProjectId = stringifyOptional(line?.targetProjectId)
    form.materialId = stringifyOptional(line?.materialId)
    form.unitId = stringifyOptional(line?.unitId)
    form.sourceCostLayerId = stringifyOptional(line?.sourceCostLayerId)
    form.sourceUnitCost = stringifyOptional(line?.sourceUnitCost)
    form.quantity = stringifyOptional(line?.quantity)
    return
  }
  if (config.value.kind === 'stocktake') {
    const stocktake = detail as InventoryStocktakeRecord
    form.warehouseId = stringifyOptional(stocktake.warehouseId)
    return
  }
  const adjustment = detail as InventoryValuationAdjustmentRecord
  const line = adjustment.lines?.[0]
  form.adjustmentType = adjustment.adjustmentType === 'PROVISIONAL_REVALUATION'
    ? 'PROVISIONAL_REVALUATION'
    : 'LEGACY_OPENING'
  form.materialId = stringifyOptional(line?.materialId)
  form.ownershipType = line?.ownershipType === 'PROJECT' || line?.projectId ? 'PROJECT' : 'PUBLIC'
  form.projectId = stringifyOptional(line?.projectId)
  form.costLayerId = stringifyOptional(line?.costLayerId)
  form.quantity = stringifyOptional(line?.quantity)
  form.unitCost = stringifyOptional(line?.unitCost)
  form.adjustmentAmount = stringifyOptional(line?.adjustmentAmount)
}

async function loadDetail() {
  if (!recordId.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const detail = await config.value.api.get(recordId.value)
    record.value = detail
    if (mode.value === 'edit') {
      fillFormFromRecord(detail)
    }
    if (config.value.kind === 'stocktake') {
      fillStocktakeInputs((detail as InventoryStocktakeRecord).lines ?? [])
    }
  } catch (caught) {
    record.value = null
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

function search() {
  pagination.page = 1
  void loadList()
}

function resetSearch() {
  filters.keyword = ''
  filters.status = ''
  pagination.page = 1
  void loadList()
}

function changePage(page: number) {
  pagination.page = page
  void loadList()
}

function changePageSize(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1
  void loadList()
}

async function refreshCurrent() {
  if (isList.value) {
    await loadList()
  } else {
    await loadDetail()
  }
}

async function runAction(target: InventoryRecord, action: InventoryAllowedAction | string) {
  actionLoading.value = true
  actionError.value = ''
  try {
    if (action === 'POST' && config.value.api.post) {
      await config.value.api.post(target.id, actionPayload(target))
    } else if (action === 'SUBMIT_APPROVAL' && config.value.api.submitApproval) {
      await config.value.api.submitApproval(target.id, actionPayload(target))
    } else if (action === 'WITHDRAW' && config.value.api.withdraw) {
      await config.value.api.withdraw(target.id, actionPayload(target))
    } else if (action === 'START' && config.value.api.start) {
      await config.value.api.start(target.id, actionPayload(target))
    } else if (action === 'RECONCILE' && config.value.api.reconcile) {
      await config.value.api.reconcile(target.id, actionPayload(target))
    } else if (action === 'COMPLETE_ZERO_VARIANCE' && config.value.api.completeZeroVariance) {
      await config.value.api.completeZeroVariance(target.id, actionPayload(target))
    } else if (action === 'CANCEL' && config.value.api.cancel) {
      await config.value.api.cancel(target.id, actionPayload(target))
    }
    await refreshCurrent()
  } catch (caught) {
    const status = (caught as { status?: number }).status
    actionError.value = status === 409 ? '数据已过期，请刷新后重试' : errorMessage(caught)
    if (status === 409) {
      await refreshCurrent()
    }
  } finally {
    actionLoading.value = false
  }
}

function stocktakeActualText(line: InventoryStocktakeLineRecord) {
  return line.countedQuantity === null || line.countedQuantity === undefined ? '未盘' : formatQuantity(line.countedQuantity)
}

function editableStocktakeLines() {
  if (!record.value || config.value.kind !== 'stocktake') {
    return []
  }
  return ((record.value as InventoryStocktakeRecord).lines ?? []).filter((line) => {
    const value = stocktakeActualInputs[String(line.id)]
    const original = line.countedQuantity ?? ''
    return value !== undefined && value !== original
  })
}

async function saveStocktakeLines() {
  if (!record.value || !config.value.api.updateLines) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await config.value.api.updateLines(record.value.id, {
      version: record.value.version,
      lines: editableStocktakeLines().map((line) => ({
        id: line.id,
        version: line.version,
        countedQuantity: stocktakeActualInputs[String(line.id)] === ''
          ? null
          : stocktakeActualInputs[String(line.id)],
      })),
    })
    await loadDetail()
  } catch (caught) {
    actionError.value = errorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function buildPayload(): object {
  const common = {
    idempotencyKey: createIdempotencyKey(`inventory-${config.value.kind}`),
    ...(mode.value === 'edit' && record.value ? { version: record.value.version } : {}),
    businessDate: form.businessDate,
    reason: form.reason,
    ...(form.remark ? { remark: form.remark } : {}),
  }
  if (config.value.kind === 'warehouseTransfer') {
    const projectId = normalizeOptionalId(form.projectId)
    return {
      ...common,
      lines: [{
        lineNo: 10,
        sourceWarehouseId: normalizeId(form.sourceWarehouseId),
        targetWarehouseId: normalizeId(form.targetWarehouseId),
        materialId: normalizeId(form.materialId),
        unitId: normalizeId(form.unitId),
        quantity: form.quantity,
        ownershipType: projectId ? 'PROJECT' : form.ownershipType,
        ...(projectId ? { projectId } : {}),
        ...(normalizeOptionalId(form.sourceCostLayerId) ? { sourceCostLayerId: normalizeOptionalId(form.sourceCostLayerId) } : {}),
      }],
    } satisfies InventoryWarehouseTransferPayload
  }
  if (config.value.kind === 'ownershipConversion') {
    return {
      ...common,
      lines: [{
        lineNo: 10,
        sourceOwnershipType: form.sourceOwnershipType,
        targetOwnershipType: form.targetOwnershipType,
        sourceWarehouseId: normalizeId(form.sourceWarehouseId),
        targetWarehouseId: normalizeId(form.targetWarehouseId),
        ...(normalizeOptionalId(form.sourceProjectId) ? { sourceProjectId: normalizeOptionalId(form.sourceProjectId) } : {}),
        ...(normalizeOptionalId(form.targetProjectId) ? { targetProjectId: normalizeOptionalId(form.targetProjectId) } : {}),
        materialId: normalizeId(form.materialId),
        unitId: normalizeId(form.unitId),
        quantity: form.quantity,
        ...(normalizeOptionalId(form.sourceCostLayerId) ? { sourceCostLayerId: normalizeOptionalId(form.sourceCostLayerId) } : {}),
        ...(form.sourceUnitCost ? { sourceUnitCost: form.sourceUnitCost } : {}),
      }],
    } satisfies InventoryOwnershipConversionPayload
  }
  if (config.value.kind === 'stocktake') {
    return {
      ...common,
      scopeType: 'WAREHOUSE',
      warehouseId: normalizeOptionalId(form.warehouseId),
    } satisfies InventoryStocktakePayload
  }
  const adjustmentProjectId = normalizeOptionalId(form.projectId)
  return {
    ...common,
    adjustmentType: form.adjustmentType,
    lines: [{
      lineNo: 10,
      materialId: normalizeId(form.materialId),
      ownershipType: adjustmentProjectId ? 'PROJECT' : form.ownershipType,
      ...(adjustmentProjectId ? { projectId: adjustmentProjectId } : {}),
      quantity: form.quantity,
      unitCost: form.unitCost,
      adjustmentAmount: form.adjustmentAmount,
      ...(normalizeOptionalId(form.costLayerId) ? { costLayerId: normalizeOptionalId(form.costLayerId) } : {}),
    }],
  } satisfies InventoryValuationAdjustmentPayload
}

async function saveForm() {
  actionLoading.value = true
  actionError.value = ''
  try {
    const saved = mode.value === 'edit' && recordId.value && config.value.api.update
      ? await config.value.api.update(normalizeId(recordId.value), buildPayload())
      : await config.value.api.create?.(buildPayload())
    if (saved) {
      await router.push({ name: config.value.detailRouteName, params: { id: String(saved.id) } })
    }
  } catch (caught) {
    actionError.value = errorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function cancelForm() {
  void router.push({ name: config.value.listRouteName })
}

function rowProjectText(row: { projectNo?: string | null; projectName?: string | null }) {
  const target = row as {
    projectNo?: string | null
    projectName?: string | null
    targetProjectNo?: string | null
    targetProjectName?: string | null
  }
  if (target.projectNo) {
    return `${target.projectNo} ${target.projectName || ''}`.trim()
  }
  return target.targetProjectNo ? `${target.targetProjectNo} ${target.targetProjectName || ''}`.trim() : '-'
}

watch(() => route.name, () => {
  records.value = []
  record.value = null
  actionError.value = ''
  if (isList.value) {
    void loadList()
  } else if (isForm.value && mode.value === 'create') {
    resetForm()
  } else if (isForm.value && mode.value === 'edit') {
    void loadDetail()
  } else if (mode.value === 'detail') {
    void loadDetail()
  }
})

onMounted(() => {
  if (isList.value) {
    void loadList()
  } else if (mode.value === 'create') {
    resetForm()
  } else if (mode.value === 'detail' || mode.value === 'edit') {
    void loadDetail()
  }
})
</script>

<template>
  <MasterDataTableView :title="config.title" :description="config.description">
    <template v-if="isList" #actions>
      <el-button v-if="canCreate" type="primary" data-test="create-inventory-controlled-document" @click="routeToCreate">
        新建
      </el-button>
    </template>
    <template v-if="isList" #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="inventory-controlled-keyword" clearable placeholder="单号或原因" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" data-test="inventory-controlled-status" clearable placeholder="全部状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="盘点中" value="COUNTING" />
            <el-option label="已确认差异" value="RECONCILED" />
            <el-option label="已过账" value="POSTED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-inventory-controlled" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-inventory-controlled" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" :title="`${config.title}加载中`" :closable="false" />
    </template>

    <div v-if="isList" class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : `暂无${config.title}`" stripe>
        <el-table-column prop="documentNo" label="单据编号" min-width="170" show-overflow-tooltip />
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ statusText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批状态" min-width="110">
          <template #default="{ row }">
            {{ approvalStatusText(row) }}
          </template>
        </el-table-column>
        <el-table-column label="金额影响" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ amountImpactText(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="businessDate" label="业务日期" min-width="110" />
        <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip />
        <el-table-column prop="lineCount" label="明细数" min-width="80" />
        <el-table-column label="更新时间" min-width="150">
          <template #default="{ row }">
            {{ formattedDateTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="260">
          <template #default="{ row }">
            <el-button size="small" text @click="routeToDetail(row)">详情</el-button>
            <el-button v-if="actionVisible(row, 'UPDATE')" size="small" text @click="routeToEdit(row)">编辑</el-button>
            <el-button
              v-if="actionVisible(row, 'POST')"
              size="small"
              text
              type="success"
              :loading="actionLoading"
              @click="runAction(row, 'POST')"
            >
              {{ inventoryActionLabel('POST') }}
            </el-button>
            <el-button
              v-if="actionVisible(row, 'START')"
              size="small"
              text
              type="primary"
              :loading="actionLoading"
              @click="runAction(row, 'START')"
            >
              {{ inventoryActionLabel('START') }}
            </el-button>
            <el-button
              v-if="actionVisible(row, 'SUBMIT_APPROVAL')"
              size="small"
              text
              type="primary"
              :loading="actionLoading"
              @click="runAction(row, 'SUBMIT_APPROVAL')"
            >
              {{ inventoryActionLabel('SUBMIT_APPROVAL') }}
            </el-button>
            <el-button
              v-if="actionVisible(row, 'WITHDRAW')"
              size="small"
              text
              :loading="actionLoading"
              @click="runAction(row, 'WITHDRAW')"
            >
              {{ inventoryActionLabel('WITHDRAW') }}
            </el-button>
            <el-button
              v-if="actionVisible(row, 'CANCEL')"
              size="small"
              text
              type="danger"
              :loading="actionLoading"
              @click="runAction(row, 'CANCEL')"
            >
              {{ inventoryActionLabel('CANCEL') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="table-pagination"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        :page-size="pagination.pageSize"
        :current-page="pagination.page"
        @current-change="changePage"
        @size-change="changePageSize"
      />
    </div>

    <div v-else-if="isForm" class="inventory-controlled-form">
      <el-form label-position="top">
        <div class="inventory-controlled-grid">
          <el-form-item label="业务日期">
            <el-input v-model="form.businessDate" name="inventory-controlled-business-date" placeholder="YYYY-MM-DD" />
          </el-form-item>
          <el-form-item label="原因">
            <el-input v-model="form.reason" name="inventory-controlled-reason" placeholder="必填原因" />
          </el-form-item>
          <el-form-item v-if="config.kind === 'valuationAdjustment'" label="调整类型">
            <el-select
              v-model="form.adjustmentType"
              data-test="inventory-controlled-adjustment-type"
              placeholder="请选择调整类型"
            >
              <el-option label="历史期初估值" value="LEGACY_OPENING" />
              <el-option label="暂估重估" value="PROVISIONAL_REVALUATION" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="config.kind === 'ownershipConversion'" label="来源所有权">
            <el-select
              v-model="form.sourceOwnershipType"
              data-test="inventory-controlled-source-ownership-type"
              placeholder="来源所有权"
            >
              <el-option label="公共库存" value="PUBLIC" />
              <el-option label="项目库存" value="PROJECT" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="config.kind === 'ownershipConversion'" label="目标所有权">
            <el-select
              v-model="form.targetOwnershipType"
              data-test="inventory-controlled-target-ownership-type"
              placeholder="目标所有权"
            >
              <el-option label="公共库存" value="PUBLIC" />
              <el-option label="项目库存" value="PROJECT" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="config.kind === 'warehouseTransfer' || config.kind === 'valuationAdjustment'" label="所有权">
            <el-select
              v-model="form.ownershipType"
              data-test="inventory-controlled-ownership-type"
              placeholder="所有权"
            >
              <el-option label="公共库存" value="PUBLIC" />
              <el-option label="项目库存" value="PROJECT" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="config.kind === 'valuationAdjustment'" label="调整金额">
            <el-input v-model="form.adjustmentAmount" name="inventory-controlled-adjustment-amount" placeholder="0.00" />
          </el-form-item>
          <el-form-item v-if="config.kind === 'valuationAdjustment'" label="数量">
            <el-input v-model="form.quantity" name="inventory-controlled-quantity" placeholder="0.000000" />
          </el-form-item>
          <el-form-item v-else label="数量">
            <el-input v-model="form.quantity" name="inventory-controlled-quantity" placeholder="0.000000" />
          </el-form-item>
          <el-form-item v-if="config.kind === 'warehouseTransfer' || config.kind === 'ownershipConversion'" label="来源仓库">
            <el-input v-model="form.sourceWarehouseId" name="inventory-controlled-source-warehouse-id" placeholder="来源仓库标识" />
          </el-form-item>
          <el-form-item v-if="config.kind === 'warehouseTransfer' || config.kind === 'ownershipConversion'" label="目标仓库">
            <el-input v-model="form.targetWarehouseId" name="inventory-controlled-target-warehouse-id" placeholder="目标仓库标识" />
          </el-form-item>
          <el-form-item v-if="config.kind === 'stocktake'" label="仓库">
            <el-input v-model="form.warehouseId" name="inventory-controlled-warehouse-id" placeholder="仓库标识" />
          </el-form-item>
          <el-form-item v-if="config.kind === 'warehouseTransfer' || config.kind === 'valuationAdjustment'" label="项目">
            <el-input v-model="form.projectId" name="inventory-controlled-project-id" placeholder="项目标识，公共库存留空" />
          </el-form-item>
          <el-form-item v-if="config.kind === 'ownershipConversion'" label="来源项目">
            <el-input v-model="form.sourceProjectId" name="inventory-controlled-source-project-id" placeholder="来源项目标识，公共库存留空" />
          </el-form-item>
          <el-form-item v-if="config.kind === 'ownershipConversion'" label="目标项目">
            <el-input v-model="form.targetProjectId" name="inventory-controlled-target-project-id" placeholder="目标项目标识，公共库存留空" />
          </el-form-item>
          <el-form-item label="物料">
            <el-input v-model="form.materialId" name="inventory-controlled-material-id" placeholder="物料标识" />
          </el-form-item>
          <el-form-item v-if="config.kind === 'warehouseTransfer' || config.kind === 'ownershipConversion'" label="单位">
            <el-input v-model="form.unitId" name="inventory-controlled-unit-id" placeholder="单位标识" />
          </el-form-item>
          <el-form-item v-if="config.kind === 'valuationAdjustment'" label="单价">
            <el-input v-model="form.unitCost" name="inventory-controlled-unit-cost" placeholder="0.000000" />
          </el-form-item>
          <el-form-item v-if="config.kind === 'warehouseTransfer' || config.kind === 'ownershipConversion'" label="来源成本层">
            <el-input v-model="form.sourceCostLayerId" name="inventory-controlled-source-cost-layer-id" placeholder="项目库存成本层标识" />
          </el-form-item>
          <el-form-item v-if="config.kind === 'ownershipConversion'" label="来源单位成本">
            <el-input v-model="form.sourceUnitCost" name="inventory-controlled-source-unit-cost" placeholder="0.000000" />
          </el-form-item>
          <el-form-item v-if="config.kind === 'valuationAdjustment'" label="成本层">
            <el-input v-model="form.costLayerId" name="inventory-controlled-cost-layer-id" placeholder="成本层标识" />
          </el-form-item>
        </div>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="可选" />
        </el-form-item>
      </el-form>
      <div class="form-footer">
        <el-button @click="cancelForm">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="saveForm">保存</el-button>
      </div>
    </div>

    <div v-else-if="record" class="inventory-controlled-detail">
      <section class="summary-strip">
        <div>
          <span>单据编号</span>
          <strong>{{ documentNo(record) }}</strong>
        </div>
        <div>
          <span>状态</span>
          <strong>{{ statusText(record) }}</strong>
        </div>
        <div>
          <span>业务日期</span>
          <strong>{{ record.businessDate }}</strong>
        </div>
        <div>
          <span>版本</span>
          <strong>{{ record.version }}</strong>
        </div>
      </section>

      <ApprovalStatusPanel
        v-if="config.kind !== 'warehouseTransfer'"
        :approval-instance-id="record.approvalSummary?.id"
        :approval-status="record.approvalSummary?.status"
        :submitted-at="record.approvalSummary?.submittedAt"
      />

      <section class="detail-actions">
        <el-button
          v-if="actionVisible(record, 'UPDATE')"
          @click="routeToEdit(record)"
        >
          编辑
        </el-button>
        <el-button
          v-if="actionVisible(record, 'POST')"
          type="success"
          :loading="actionLoading"
          @click="runAction(record, 'POST')"
        >
          过账
        </el-button>
        <el-button
          v-if="actionVisible(record, 'START')"
          type="primary"
          :loading="actionLoading"
          @click="runAction(record, 'START')"
        >
          开始盘点
        </el-button>
        <el-button
          v-if="actionVisible(record, 'SUBMIT_APPROVAL')"
          type="primary"
          :loading="actionLoading"
          @click="runAction(record, 'SUBMIT_APPROVAL')"
        >
          提交审批
        </el-button>
        <el-button
          v-if="actionVisible(record, 'WITHDRAW')"
          :loading="actionLoading"
          @click="runAction(record, 'WITHDRAW')"
        >
          撤回
        </el-button>
        <el-button
          v-if="actionVisible(record, 'RECONCILE')"
          type="primary"
          :loading="actionLoading"
          @click="runAction(record, 'RECONCILE')"
        >
          确认差异
        </el-button>
        <el-button
          v-if="actionVisible(record, 'COMPLETE_ZERO_VARIANCE')"
          type="success"
          :loading="actionLoading"
          @click="runAction(record, 'COMPLETE_ZERO_VARIANCE')"
        >
          结束零差异盘点
        </el-button>
        <el-button
          v-if="actionVisible(record, 'CANCEL')"
          type="danger"
          :loading="actionLoading"
          @click="runAction(record, 'CANCEL')"
        >
          取消
        </el-button>
      </section>

      <div v-if="config.kind === 'warehouseTransfer'" class="table-scroll">
        <el-table :data="(record as InventoryWarehouseTransferRecord).lines ?? []" empty-text="暂无调拨明细" stripe>
          <el-table-column prop="lineNo" label="行号" min-width="80" />
          <el-table-column prop="sourceWarehouseName" label="来源仓库" min-width="140" show-overflow-tooltip />
          <el-table-column prop="targetWarehouseName" label="目标仓库" min-width="140" show-overflow-tooltip />
          <el-table-column label="物料" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template>
          </el-table-column>
          <el-table-column label="所有权" min-width="110">
            <template #default="{ row }">{{ row.ownershipTypeName || ownershipTypeLabel(row.ownershipType) }}</template>
          </el-table-column>
          <el-table-column label="数量" min-width="110" align="right">
            <template #default="{ row }">{{ formatQuantity(row.quantity) }}</template>
          </el-table-column>
        </el-table>
      </div>

      <div v-else-if="config.kind === 'ownershipConversion'" class="table-scroll">
        <el-table :data="(record as InventoryOwnershipConversionRecord).lines ?? []" empty-text="暂无转换明细" stripe>
          <el-table-column prop="lineNo" label="行号" min-width="80" />
          <el-table-column label="来源所有权" min-width="120">
            <template #default="{ row }">{{ ownershipTypeLabel(row.sourceOwnershipType) }}</template>
          </el-table-column>
          <el-table-column label="目标所有权" min-width="120">
            <template #default="{ row }">{{ ownershipTypeLabel(row.targetOwnershipType) }}</template>
          </el-table-column>
          <el-table-column label="目标项目" min-width="170" show-overflow-tooltip>
            <template #default="{ row }">{{ rowProjectText(row) }}</template>
          </el-table-column>
          <el-table-column label="物料" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template>
          </el-table-column>
          <el-table-column label="数量" min-width="110" align="right">
            <template #default="{ row }">{{ formatQuantity(row.quantity) }}</template>
          </el-table-column>
        </el-table>
      </div>

      <div v-else-if="config.kind === 'stocktake'" class="table-scroll">
        <el-button
          v-if="actionVisible(record, 'UPDATE_LINES')"
          class="stocktake-save"
          type="primary"
          :loading="actionLoading"
          @click="saveStocktakeLines"
        >
          保存实盘
        </el-button>
        <el-table :data="(record as InventoryStocktakeRecord).lines ?? []" empty-text="暂无盘点行" stripe>
          <el-table-column prop="lineNo" label="行号" min-width="80" />
          <el-table-column prop="warehouseName" label="仓库" min-width="130" show-overflow-tooltip />
          <el-table-column label="物料" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template>
          </el-table-column>
          <el-table-column label="账面数" min-width="110" align="right">
            <template #default="{ row }">{{ formatQuantity(row.bookQuantity) }}</template>
          </el-table-column>
          <el-table-column label="实盘数" min-width="160" align="right">
            <template #default="{ row }">
              <span :data-test="`stocktake-line-actual-${row.id}`">{{ stocktakeActualText(row) }}</span>
              <el-input
                v-if="actionVisible(record, 'UPDATE_LINES')"
                v-model="stocktakeActualInputs[String(row.id)]"
                class="stocktake-actual-input"
                :name="`stocktake-line-actual-${row.id}`"
                placeholder="留空为未盘"
              />
            </template>
          </el-table-column>
          <el-table-column label="差异数" min-width="110" align="right">
            <template #default="{ row }">{{ row.varianceQuantity === null || row.varianceQuantity === undefined ? '-' : formatQuantity(row.varianceQuantity) }}</template>
          </el-table-column>
        </el-table>
      </div>

      <div v-else class="table-scroll">
        <el-table :data="(record as InventoryValuationAdjustmentRecord).lines ?? []" empty-text="暂无估值明细" stripe>
          <el-table-column prop="lineNo" label="行号" min-width="80" />
          <el-table-column label="物料" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template>
          </el-table-column>
          <el-table-column label="数量" min-width="110" align="right">
            <template #default="{ row }">{{ formatQuantity(row.quantity) }}</template>
          </el-table-column>
          <el-table-column label="单价" min-width="120" align="right">
            <template #default="{ row }">{{ formatInventoryAmount(row.unitCost, 6) }}</template>
          </el-table-column>
          <el-table-column label="金额" min-width="120" align="right">
            <template #default="{ row }">{{ formatInventoryAmount(row.adjustmentAmount) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.inventory-controlled-form {
  padding: 14px;
}

.inventory-controlled-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 14px;
}

.form-footer,
.detail-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin: 12px 0;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: 14px;
}

.summary-strip > div {
  border: 1px solid var(--qherp-border);
  border-radius: 8px;
  min-width: 0;
  padding: 12px;
}

.summary-strip span {
  color: var(--qherp-muted);
  display: block;
  font-size: 12px;
}

.summary-strip strong {
  display: block;
  margin-top: 4px;
  word-break: break-word;
}

.stocktake-save {
  margin: 0 0 10px;
}

.stocktake-actual-input {
  margin-top: 6px;
  max-width: 140px;
}

@media (max-width: 900px) {
  .inventory-controlled-grid,
  .summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .inventory-controlled-grid,
  .summary-strip {
    grid-template-columns: 1fr;
  }
}
</style>
