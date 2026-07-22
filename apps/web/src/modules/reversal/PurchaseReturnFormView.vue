<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { queryWithReturnTo, routeReturnTo } from '../../shared/navigation/navigationReturn'
import {
  returnRefundReversalApi,
  type PurchaseReturnCreatePayloadLine,
  type PurchaseReturnDetail,
  type PurchaseReturnPayload,
  type PurchaseReturnSource,
  type PurchaseReturnSourceLine,
  type PurchaseReturnUpdatePayload,
  type PurchaseReturnUpdatePayloadLine,
  type ResourceId,
  type ReversalDocumentLine,
  type ReversalSourceView,
  type ReversalStatus,
} from '../../shared/api/returnRefundReversalApi'
import { inventoryApi, type InventoryQualityStatus, type InventoryTrackingAllocationPayload, type InventoryTrackingMethod } from '../../shared/api/inventoryApi'
import { masterDataApi, type MaterialRecord } from '../../shared/api/masterDataApi'
import TrackingAllocationReadonlyTable from '../inventory/tracking/TrackingAllocationReadonlyTable.vue'
import TrackingPickerDrawer from '../inventory/tracking/TrackingPickerDrawer.vue'
import {
  inferTrackingMethodFromAllocations,
  mapBatchCandidate,
  mapSerialCandidate,
  outboundTrackingAllocationsPayload,
  validateOutboundTrackingAllocations,
  type TrackingCandidateRecord,
} from '../inventory/tracking/trackingPayloadHelpers'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import QualityStatusTag from '../quality/QualityStatusTag.vue'
import {
  decimalCompare,
  formatProcurementAmount,
  formatProcurementQuantity,
  procurementModeDisplay,
  validatePurchaseQuantity,
} from '../procurement/procurementPageHelpers'
import {
  salesErrorMessage,
} from '../sales/salesPageHelpers'

interface PurchaseReturnLineDraft {
  id?: ResourceId
  sourceReceiptLineId?: ResourceId
  lineNo: number
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitName: string
  receivedQuantity: string
  returnedQuantity: string
  returnableQuantity: string
  availableStockQuantity: string
  qualityStatus?: InventoryQualityStatus | null
  qualityStatusName?: string | null
  quantityOnHand?: string | null
  availableQuantity?: string | null
  selectable?: boolean | null
  disabledReasonCode?: string | null
  disabledReason?: string | null
  maxSelectableQuantity?: string | null
  unitPrice: string
  returnableAmount: string
  procurementMode?: string | null
  projectCode?: string | null
  projectName?: string | null
  originalCostLayerNo?: string | null
  originalValueMovementNo?: string | null
  costVisible?: boolean | null
  trackingMethod: InventoryTrackingMethod
  trackingMethodName: string
  trackingAllocations: InventoryTrackingAllocationPayload[]
  quantity: string
  reason: string
}

const route = useRoute()
const router = useRouter()
const routeId = computed(() => route.params.id as string | undefined)
const isEdit = computed(() => Boolean(routeId.value))
const title = computed(() => (isEdit.value ? '编辑采购退货' : '新建采购退货'))
const description = computed(() => (isEdit.value ? '更新草稿采购退货的退货数量和原因。' : '从已过账采购入库选择可退明细创建采购退货草稿。'))
const form = reactive({
  sourceReceiptId: '' as string | number | '',
  businessDate: '',
  remark: '',
  clientRequestId: '',
})
const sourceFilters = reactive({
  keyword: '',
})
const sources = ref<PurchaseReturnSource[]>([])
const editDetail = ref<PurchaseReturnDetail | null>(null)
const lines = ref<PurchaseReturnLineDraft[]>([])
const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const submitError = ref('')
const nonEditableStatus = ref<ReversalStatus | null>(null)
const materialTrackingCache = new Map<string, Pick<MaterialRecord, 'trackingMethod' | 'trackingMethodName'>>()
const trackingPickerVisible = ref(false)
const trackingPickerLineIndex = ref<number | null>(null)
const trackingCandidates = ref<TrackingCandidateRecord[]>([])
const trackingCandidateLoading = ref(false)
const trackingCandidateError = ref('')

const selectedSource = computed(() => sources.value.find((source) => String(source.receiptId) === String(form.sourceReceiptId)))
const canEditForm = computed(() => !isEdit.value || nonEditableStatus.value === null)
const nonEditableStatusText = computed(() => {
  if (nonEditableStatus.value === 'POSTED') {
    return '已过账'
  }
  if (nonEditableStatus.value === 'CANCELLED') {
    return '已取消'
  }
  return ''
})
const sourceDisplayText = computed(() => {
  if (isEdit.value && sourceRestricted(editDetail.value?.source)) {
    return editDetail.value?.source.restrictedMessage || '来源无查看权限'
  }
  return selectedSource.value?.receiptNo || editDetail.value?.source.sourceNo || '-'
})

function sourceRestricted(source?: ReversalSourceView | null) {
  return !source || source.restricted || !source.canViewSource
}

function lineInputKey(line: PurchaseReturnLineDraft) {
  return line.sourceReceiptLineId ?? line.id ?? line.lineNo
}

async function materialTracking(materialId: ResourceId) {
  const key = String(materialId)
  const cached = materialTrackingCache.get(key)
  if (cached) {
    return cached
  }
  const material = await masterDataApi.materials.get(materialId)
  const tracking = {
    trackingMethod: material.trackingMethod,
    trackingMethodName: material.trackingMethodName,
  }
  materialTrackingCache.set(key, tracking)
  return tracking
}

async function lineDraftFromSource(line: PurchaseReturnSourceLine): Promise<PurchaseReturnLineDraft> {
  const tracking = await materialTracking(line.materialId)
  return {
    sourceReceiptLineId: line.receiptLineId,
    lineNo: line.lineNo,
    materialId: line.materialId,
    materialCode: line.materialCode,
    materialName: line.materialName,
    unitName: line.unitName,
    receivedQuantity: line.receivedQuantity,
    returnedQuantity: line.returnedQuantity,
    returnableQuantity: line.returnableQuantity,
    availableStockQuantity: line.availableStockQuantity,
    qualityStatus: line.qualityStatus ?? null,
    qualityStatusName: line.qualityStatusName ?? null,
    quantityOnHand: line.quantityOnHand ?? null,
    availableQuantity: line.availableQuantity ?? null,
    selectable: line.selectable ?? null,
    disabledReasonCode: line.disabledReasonCode ?? null,
    disabledReason: line.disabledReason ?? null,
    maxSelectableQuantity: line.maxSelectableQuantity ?? null,
    unitPrice: line.unitPrice,
    returnableAmount: line.returnableAmount,
    procurementMode: line.procurementMode ?? null,
    projectCode: line.projectCode ?? null,
    projectName: line.projectName ?? null,
    originalCostLayerNo: line.originalCostLayerNo ?? null,
    originalValueMovementNo: line.originalValueMovementNo ?? null,
    costVisible: line.costVisible ?? null,
    trackingMethod: tracking.trackingMethod,
    trackingMethodName: tracking.trackingMethodName,
    trackingAllocations: [],
    quantity: '',
    reason: '',
  }
}

function lineDraftFromDetail(line: ReversalDocumentLine): PurchaseReturnLineDraft {
  const restricted = sourceRestricted(line.source)
  return {
    id: line.id,
    sourceReceiptLineId: restricted ? undefined : line.sourceLineId,
    lineNo: line.lineNo,
    materialId: line.materialId,
    materialCode: line.materialCode,
    materialName: line.materialName,
    unitName: line.unitName,
    receivedQuantity: restricted ? '' : line.source.quantity ?? '',
    returnedQuantity: line.returnedQuantityBefore ?? '',
    returnableQuantity: line.returnableQuantityBefore ?? '',
    availableStockQuantity: '',
    qualityStatus: line.qualityStatus ?? null,
    qualityStatusName: line.qualityStatusName ?? null,
    quantityOnHand: null,
    availableQuantity: null,
    selectable: null,
    disabledReasonCode: null,
    disabledReason: null,
    maxSelectableQuantity: null,
    unitPrice: line.unitPrice ?? '',
    returnableAmount: line.amount ?? '',
    procurementMode: line.procurementMode ?? null,
    projectCode: line.projectCode ?? null,
    projectName: line.projectName ?? null,
    originalCostLayerNo: line.originalCostLayerNo ?? null,
    originalValueMovementNo: line.originalValueMovementNo ?? null,
    costVisible: line.costVisible ?? null,
    trackingMethod: inferTrackingMethodFromAllocations(line.trackingAllocations),
    trackingMethodName: inferTrackingMethodFromAllocations(line.trackingAllocations) === 'SERIAL' ? '序列号管理' : '批次管理',
    trackingAllocations: line.trackingAllocations ?? [],
    quantity: line.quantity,
    reason: line.reason ?? '',
  }
}

function lineDecimalValue(line: PurchaseReturnLineDraft, key: 'maxSelectableQuantity'): string | null {
  const value = line[key]
  if (value === null || value === undefined || value === '') {
    return null
  }
  const normalizedValue = String(value).trim()
  return /^\d+(?:\.\d+)?$/.test(normalizedValue) ? normalizedValue : null
}

function lineDisabledReason(line: PurchaseReturnLineDraft): string {
  return line.disabledReason ?? '该候选库存不可采购退货'
}

function lineUnavailable(line: PurchaseReturnLineDraft): boolean {
  const maxSelectableQuantity = lineDecimalValue(line, 'maxSelectableQuantity')
  return line.selectable === false || (maxSelectableQuantity !== null && decimalCompare(maxSelectableQuantity, '0') <= 0)
}

function ownershipText(value?: {
  procurementMode?: string | null
  projectCode?: string | null
  projectName?: string | null
} | null) {
  return procurementModeDisplay(
    value?.procurementMode === 'PROJECT' ? 'PROJECT' : value?.procurementMode === 'PUBLIC' ? 'PUBLIC' : undefined,
    value?.projectCode,
    value?.projectName,
  )
}

function costSourceText(value?: {
  originalCostLayerNo?: string | null
  originalValueMovementNo?: string | null
  costVisible?: boolean | null
} | null) {
  if (value?.costVisible === false) {
    return '成本无权限'
  }
  return `原成本层 ${value?.originalCostLayerNo || '-'} / 原价值流水 ${value?.originalValueMovementNo || '-'}`
}

async function loadSources() {
  const page = await returnRefundReversalApi.purchaseReturnSources.list({
    keyword: sourceFilters.keyword,
    page: 1,
    pageSize: 20,
  })
  sources.value = pageItems(page)
  if (!isEdit.value && sources.value.length > 0 && form.sourceReceiptId === '') {
    await chooseSource(sources.value[0])
  }
}

async function loadDetail() {
  if (!routeId.value) {
    return
  }
  const detail = await returnRefundReversalApi.purchaseReturns.get(routeId.value)
  editDetail.value = detail
  if (detail.status !== 'DRAFT') {
    nonEditableStatus.value = detail.status
    form.sourceReceiptId = sourceRestricted(detail.source) ? '' : detail.source.sourceId ?? ''
    form.businessDate = detail.businessDate
    form.remark = detail.remark ?? ''
    form.clientRequestId = detail.clientRequestId ?? `purchase-return-${detail.id}`
    lines.value = []
    return
  }
  nonEditableStatus.value = null
  form.sourceReceiptId = sourceRestricted(detail.source) ? '' : detail.source.sourceId ?? ''
  form.businessDate = detail.businessDate
  form.remark = detail.remark ?? ''
  form.clientRequestId = detail.clientRequestId ?? `purchase-return-${detail.id}`
  lines.value = detail.lines.map(lineDraftFromDetail)
}

async function loadForm() {
  loading.value = true
  error.value = ''
  nonEditableStatus.value = null
  try {
    if (isEdit.value) {
      await loadDetail()
    } else {
      form.businessDate = new Date().toISOString().slice(0, 10)
      form.clientRequestId = `purchase-return-${Date.now()}`
      await loadSources()
    }
  } catch (caught) {
    sources.value = []
    lines.value = []
    error.value = salesErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function chooseSource(source: PurchaseReturnSource) {
  form.sourceReceiptId = source.receiptId
  form.businessDate = source.businessDate
  lines.value = await Promise.all(source.lines.map(lineDraftFromSource))
}

async function searchSources() {
  if (isEdit.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    await loadSources()
  } catch (caught) {
    error.value = salesErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function updateLine(index: number, patch: Partial<PurchaseReturnLineDraft>) {
  lines.value = lines.value.map((line, currentIndex) => (currentIndex === index ? { ...line, ...patch } : line))
}

function selectedWarehouseId(): ResourceId | null {
  return selectedSource.value?.warehouseId ?? editDetail.value?.warehouseId ?? null
}

async function openTrackingPicker(index: number) {
  const line = lines.value[index]
  const warehouseId = selectedWarehouseId()
  if (!line || line.trackingMethod === 'NONE' || !warehouseId) {
    return
  }
  trackingPickerLineIndex.value = index
  trackingPickerVisible.value = true
  trackingCandidateLoading.value = true
  trackingCandidateError.value = ''
  try {
    if (line.trackingMethod === 'BATCH') {
      const page = await inventoryApi.batches.list({
        materialId: line.materialId,
        warehouseId,
        onlyAvailable: false,
        page: 1,
        pageSize: 20,
      })
      trackingCandidates.value = pageItems(page).map(mapBatchCandidate)
    } else {
      const page = await inventoryApi.serials.list({
        materialId: line.materialId,
        warehouseId,
        onlyAvailable: false,
        page: 1,
        pageSize: 20,
      })
      trackingCandidates.value = pageItems(page).map(mapSerialCandidate)
    }
  } catch (caught) {
    trackingCandidates.value = []
    trackingCandidateError.value = salesErrorMessage(caught)
  } finally {
    trackingCandidateLoading.value = false
  }
}

function selectTrackingCandidate(candidate: TrackingCandidateRecord) {
  const index = trackingPickerLineIndex.value
  if (index === null) {
    return
  }
  const line = lines.value[index]
  if (!line || line.trackingMethod === 'NONE') {
    return
  }
  const allocation = line.trackingMethod === 'BATCH'
    ? { batchId: candidate.id, batchNo: candidate.trackingNo, quantity: line.quantity || String(candidate.availableQuantity ?? '') }
    : { serialId: candidate.id, serialNo: candidate.trackingNo, quantity: '1' }
  updateLine(index, { trackingAllocations: [allocation] })
  trackingPickerVisible.value = false
}

function confirmTrackingAllocations(allocations: InventoryTrackingAllocationPayload[]) {
  const index = trackingPickerLineIndex.value
  if (index === null) {
    return
  }
  updateLine(index, { trackingAllocations: allocations })
  trackingPickerVisible.value = false
}

function buildPayload(): PurchaseReturnPayload | PurchaseReturnUpdatePayload | null {
  if (!canEditForm.value) {
    submitError.value = `当前采购退货${nonEditableStatusText.value}，不可编辑`
    return null
  }
  const payloadLines: PurchaseReturnUpdatePayloadLine[] = []
  let firstUnavailableReason = ''
  for (const line of lines.value) {
    if (lineUnavailable(line)) {
      firstUnavailableReason ||= `${line.materialName}：${lineDisabledReason(line)}`
      if (line.quantity) {
        submitError.value = `${line.materialName}：${lineDisabledReason(line)}`
        return null
      }
      continue
    }
    if (!line.quantity) {
      continue
    }
    const quantity = validatePurchaseQuantity(line.quantity)
    if (quantity.message || !quantity.payloadValue) {
      submitError.value = `${line.materialName}：${quantity.message}`
      return null
    }
    const maxSelectableQuantity = lineDecimalValue(line, 'maxSelectableQuantity')
    if (maxSelectableQuantity !== null && decimalCompare(quantity.payloadValue, maxSelectableQuantity) > 0) {
      submitError.value = `${line.materialName}：退货数量不能超过最大可选数量`
      return null
    }
    if ((line.trackingAllocations ?? []).length > 0) {
      const trackingMessages = validateOutboundTrackingAllocations(
        line.trackingMethod,
        line.trackingAllocations,
        quantity.payloadValue,
      )
      if (trackingMessages.length > 0) {
        submitError.value = `${line.materialName}：${trackingMessages[0]}`
        return null
      }
    }
    const payloadLine: PurchaseReturnUpdatePayloadLine = {
      quantity: quantity.payloadValue,
      reason: line.reason,
    }
    const trackingPayload = outboundTrackingAllocationsPayload(line.trackingMethod, line.trackingAllocations, quantity.payloadValue)
    if (trackingPayload) {
      payloadLine.trackingAllocations = trackingPayload
    }
    if (line.qualityStatus) {
      payloadLine.qualityStatus = line.qualityStatus
    }
    if (isEdit.value && line.id !== undefined) {
      payloadLine.id = line.id
    }
    if (line.sourceReceiptLineId !== undefined) {
      payloadLine.sourceReceiptLineId = line.sourceReceiptLineId
    }
    if (!isEdit.value && payloadLine.sourceReceiptLineId === undefined) {
      submitError.value = `${line.materialName}：来源采购入库行缺失`
      return null
    }
    if (isEdit.value && payloadLine.id === undefined && payloadLine.sourceReceiptLineId === undefined) {
      submitError.value = `${line.materialName}：退货行标识缺失`
      return null
    }
    payloadLines.push(payloadLine)
  }
  if (!isEdit.value && !form.sourceReceiptId) {
    submitError.value = '请选择来源采购入库'
    return null
  }
  if (!form.businessDate) {
    submitError.value = '业务日期不能为空'
    return null
  }
  if (!payloadLines.length) {
    submitError.value = firstUnavailableReason || '至少填写一行退货数量'
    return null
  }

  if (isEdit.value) {
    if (!editDetail.value) {
      submitError.value = '采购退货加载失败，不能保存'
      return null
    }
    const payload: PurchaseReturnUpdatePayload = {
      businessDate: form.businessDate,
      clientRequestId: form.clientRequestId || `purchase-return-${Date.now()}`,
      remark: form.remark,
      lines: payloadLines,
      version: editDetail.value.version,
    }
    if (form.sourceReceiptId) {
      payload.sourceReceiptId = form.sourceReceiptId
    }
    return payload
  }
  const createPayloadLines = payloadLines.map((line): PurchaseReturnCreatePayloadLine => ({
    sourceReceiptLineId: line.sourceReceiptLineId!,
    qualityStatus: line.qualityStatus,
    quantity: line.quantity,
    trackingAllocations: line.trackingAllocations,
    reason: line.reason,
  }))
  const payload: PurchaseReturnPayload = {
    sourceReceiptId: form.sourceReceiptId,
    businessDate: form.businessDate,
    clientRequestId: form.clientRequestId || `purchase-return-${Date.now()}`,
    remark: form.remark,
    lines: createPayloadLines,
  }
  return payload
}

async function submit() {
  if (submitting.value) {
    return
  }
  submitError.value = ''
  const payload = buildPayload()
  if (!payload) {
    return
  }

  submitting.value = true
  try {
    const detail = isEdit.value && routeId.value
      ? await returnRefundReversalApi.purchaseReturns.update(routeId.value, payload as PurchaseReturnUpdatePayload)
      : await returnRefundReversalApi.purchaseReturns.create(payload as PurchaseReturnPayload)
    await router.push({
      name: 'procurement-return-detail',
      params: { id: String(detail.id) },
      query: queryWithReturnTo({}, routeReturnTo(route)),
    })
  } catch (caught) {
    submitError.value = salesErrorMessage(caught)
  } finally {
    submitting.value = false
  }
}

function backToList() {
  void router.push({ name: 'procurement-returns' })
}

function returnToDetail() {
  if (!routeId.value) {
    return
  }
  void router.push({
    name: 'procurement-return-detail',
    params: { id: routeId.value },
    query: queryWithReturnTo({}, routeReturnTo(route)),
  })
}

onMounted(() => {
  void loadForm()
})
</script>

<template>
  <MasterDataTableView :title="title" :description="description">
    <template #actions>
      <el-button @click="backToList">返回列表</el-button>
    </template>

    <template v-if="!isEdit" #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="候选来源">
          <el-input
            v-model="sourceFilters.keyword"
            name="purchase-return-source-keyword"
            clearable
            placeholder="入库单号或供应商"
          />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-purchase-return-sources" type="primary" @click="searchSources">查询来源</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="submitError" class="state-alert" type="error" :title="submitError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="采购退货表单加载中" :closable="false" />
    </template>

    <el-result
      v-if="!canEditForm"
      icon="warning"
      :title="`当前采购退货${nonEditableStatusText}，不可编辑`"
      sub-title="已过账或已取消的采购退货只能查看，不能继续修改草稿内容。"
    >
      <template #extra>
        <el-button data-test="return-purchase-return-detail" type="primary" @click="returnToDetail">返回详情</el-button>
      </template>
    </el-result>

    <div v-else class="form-layout">
      <el-card v-if="!isEdit" class="section-card" shadow="never">
        <template #header>可退来源</template>
        <el-empty v-if="!loading && sources.length === 0" description="暂无可退采购入库" />
        <div v-else class="table-scroll">
          <el-table :data="sources" :empty-text="loading ? '加载中' : '暂无可退采购入库'" stripe>
            <el-table-column label="选择" width="80">
              <template #default="{ row }">
                <el-radio
                  :model-value="String(form.sourceReceiptId)"
                  :label="String(row.receiptId)"
                  @change="chooseSource(row)"
                >
                  选择
                </el-radio>
              </template>
            </el-table-column>
            <el-table-column prop="receiptNo" label="入库单号" min-width="170" show-overflow-tooltip />
            <el-table-column label="原采购模式/项目" min-width="210" show-overflow-tooltip>
              <template #default="{ row }">
                {{ ownershipText(row) }}
              </template>
            </el-table-column>
            <el-table-column label="原成本来源" min-width="260" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="stacked-cell">
                  <span>{{ costSourceText(row) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="supplierName" label="供应商" min-width="150" show-overflow-tooltip />
            <el-table-column prop="warehouseName" label="仓库" min-width="130" show-overflow-tooltip />
            <el-table-column prop="businessDate" label="业务日期" min-width="110" />
          </el-table>
        </div>
      </el-card>

      <el-card class="section-card" shadow="never">
        <template #header>退货信息</template>
        <el-form class="document-form" label-width="96px">
          <el-form-item label="来源入库">
            <span>{{ sourceDisplayText }}</span>
          </el-form-item>
          <el-form-item label="原采购模式">
            <span>{{ ownershipText(selectedSource ?? editDetail) }}</span>
          </el-form-item>
          <el-form-item label="原成本来源">
            <span>{{ costSourceText(selectedSource ?? editDetail) }}</span>
          </el-form-item>
          <el-form-item label="业务日期">
            <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD"
              v-model="form.businessDate"
              name="purchase-return-business-date"
              placeholder="选择日期"
              style="width: 180px"
            />
          </el-form-item>
          <el-form-item label="备注">
            <el-input
              v-model="form.remark"
              name="purchase-return-remark"
              type="textarea"
              :rows="2"
              placeholder="退货说明"
            />
          </el-form-item>
        </el-form>
      </el-card>

      <el-card class="section-card" shadow="never">
        <template #header>退货明细</template>
        <div class="table-scroll">
          <el-table :data="lines" :empty-text="loading ? '加载中' : '暂无可退明细'" stripe>
            <el-table-column prop="lineNo" label="行号" width="80" />
            <el-table-column label="物料" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.materialCode }} {{ row.materialName }}
              </template>
            </el-table-column>
            <el-table-column label="原采购模式/项目" min-width="210" show-overflow-tooltip>
              <template #default="{ row }">
                {{ ownershipText(row) }}
              </template>
            </el-table-column>
            <el-table-column label="原成本来源" min-width="260" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="stacked-cell">
                  <span>{{ costSourceText(row) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="unitName" label="单位" width="80" />
            <el-table-column label="入库数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.receivedQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="已退数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.returnedQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="可退数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.returnableQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="可用库存" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.availableStockQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="质量状态" min-width="110">
              <template #default="{ row }">
                <QualityStatusTag :quality-status="row.qualityStatus" :quality-status-name="row.qualityStatusName" />
              </template>
            </el-table-column>
            <el-table-column label="现存数量" min-width="120" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.quantityOnHand) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="合格可用" min-width="120" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.availableQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="最大可选" min-width="120" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.maxSelectableQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="禁用原因" min-width="190" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="candidate-disabled-reason">{{ row.disabledReason || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="可退金额" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementAmount(row.returnableAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="批次/序列" min-width="260">
              <template #default="{ row, $index }">
                <template v-if="row.trackingMethod !== 'NONE'">
                  <el-button
                    size="small"
                    :data-test="`open-purchase-return-tracking-${$index}`"
                    :disabled="lineUnavailable(row)"
                    @click="openTrackingPicker($index)"
                  >
                    {{ row.trackingMethod === 'BATCH' ? '选择批次' : '选择序列号' }}
                  </el-button>
                  <TrackingAllocationReadonlyTable
                    v-if="row.trackingAllocations.length > 0"
                    class="line-tracking-readonly"
                    :tracking-method="row.trackingMethod"
                    :allocations="row.trackingAllocations"
                  />
                </template>
                <span v-else class="tracking-empty-text">不追踪</span>
              </template>
            </el-table-column>
            <el-table-column label="退货数量" min-width="150">
              <template #default="{ row }">
                <el-input
                  v-model="row.quantity"
                  :name="`purchase-return-line-quantity-${lineInputKey(row)}`"
                  placeholder="0.000000"
                  :disabled="lineUnavailable(row)"
                />
              </template>
            </el-table-column>
            <el-table-column label="退货原因" min-width="170">
              <template #default="{ row }">
                <el-input
                  v-model="row.reason"
                  :name="`purchase-return-line-reason-${lineInputKey(row)}`"
                  placeholder="原因"
                />
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-card>

      <TrackingPickerDrawer
        v-model="trackingPickerVisible"
        :tracking-method="trackingPickerLineIndex === null ? 'NONE' : lines[trackingPickerLineIndex]?.trackingMethod ?? 'NONE'"
        :candidates="trackingCandidates"
        :selected-allocations="trackingPickerLineIndex === null ? [] : (lines[trackingPickerLineIndex]?.trackingAllocations ?? [])"
        :expected-quantity="trackingPickerLineIndex === null ? null : lines[trackingPickerLineIndex]?.quantity"
        :loading="trackingCandidateLoading"
        :error="trackingCandidateError"
        @select="selectTrackingCandidate"
        @confirm="confirmTrackingAllocations"
      />

      <div class="form-actions">
        <el-button @click="backToList">取消</el-button>
        <el-button
          data-test="submit-purchase-return"
          type="primary"
          :loading="submitting"
          :disabled="submitting"
          @click="submit"
        >
          保存草稿
        </el-button>
      </div>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.form-layout {
  display: grid;
  gap: 14px;
}

.section-card {
  border-radius: 6px;
}

.document-form {
  max-width: 720px;
}

.table-scroll {
  overflow-x: auto;
}

.numeric-cell {
  display: inline-block;
  min-width: 72px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.candidate-disabled-reason {
  color: var(--el-color-danger);
  font-size: 12px;
}

.line-tracking-readonly {
  margin-top: 8px;
}

.stacked-cell {
  display: grid;
  gap: 2px;
  line-height: 1.35;
}

.tracking-empty-text {
  color: var(--qherp-muted);
  font-size: 12px;
}

.form-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}
</style>
