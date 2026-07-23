<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createIdempotencyKey } from '../../shared/api/documentPlatformApi'
import { queryWithReturnTo, routeReturnTo } from '../../shared/navigation/navigationReturn'
import {
  returnRefundReversalApi,
  type ProductionMaterialSupplementDetail,
  type ProductionMaterialSupplementPayload,
  type ProductionMaterialSupplementSource,
  type ProductionMaterialSupplementSourceMaterial,
  type ProductionMaterialSupplementUpdatePayload,
  type ProductionMaterialSupplementUpdatePayloadLine,
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
import { formatSalesAmount } from '../sales/salesPageHelpers'
import {
  formatProductionQuantity,
  productionErrorMessage,
  todayText,
  validateProductionQuantity,
  workOrderStatusLabel,
} from '../production/productionPageHelpers'

interface MaterialSupplementLineDraft {
  id?: ResourceId
  workOrderMaterialId?: ResourceId
  lineNo: number
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitName: string
  ownershipType?: string | null
  projectId?: ResourceId | null
  projectNo?: string | null
  projectName?: string | null
  costLayerId?: ResourceId | null
  plannedQuantity: string
  issuedQuantity: string
  supplementedQuantity: string
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
const title = computed(() => (isEdit.value ? '编辑生产补料' : '新建生产补料'))
const description = computed(() => (isEdit.value ? '更新草稿生产补料的补料数量和原因。' : '从生产工单用料选择补料明细创建补料草稿。'))
const form = reactive({
  workOrderId: '' as string | number | '',
  warehouseId: '' as string | number | '',
  businessDate: '',
  remark: '',
  clientRequestId: '',
})
const sourceFilters = reactive({
  keyword: '',
})
const sources = ref<ProductionMaterialSupplementSource[]>([])
const editDetail = ref<ProductionMaterialSupplementDetail | null>(null)
const lines = ref<MaterialSupplementLineDraft[]>([])
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

const selectedSource = computed(() => sources.value.find((source) =>
  String(source.workOrderId) === String(form.workOrderId)
    && String(source.warehouseId) === String(form.warehouseId)))
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
  return selectedSource.value?.workOrderNo || editDetail.value?.source.sourceNo || '-'
})

function sourceRestricted(source?: ReversalSourceView | null) {
  return !source || source.restricted || !source.canViewSource
}

function lineInputKey(line: MaterialSupplementLineDraft) {
  return line.workOrderMaterialId ?? line.id ?? line.lineNo
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

async function lineDraftFromSource(line: ProductionMaterialSupplementSourceMaterial): Promise<MaterialSupplementLineDraft> {
  const tracking = await materialTracking(line.materialId)
  return {
    workOrderMaterialId: line.workOrderMaterialId,
    lineNo: line.lineNo,
    materialId: line.materialId,
    materialCode: line.materialCode,
    materialName: line.materialName,
    unitName: line.unitName,
    ownershipType: line.ownershipType ?? null,
    projectId: line.projectId ?? null,
    projectNo: line.projectNo ?? null,
    projectName: line.projectName ?? null,
    costLayerId: line.costLayerId ?? null,
    plannedQuantity: line.plannedQuantity,
    issuedQuantity: line.issuedQuantity,
    supplementedQuantity: line.supplementedQuantity,
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
    trackingMethod: tracking.trackingMethod,
    trackingMethodName: tracking.trackingMethodName,
    trackingAllocations: [],
    quantity: '',
    reason: '',
  }
}

function lineDraftFromDetail(line: ReversalDocumentLine): MaterialSupplementLineDraft {
  const restricted = sourceRestricted(line.source)
  return {
    id: line.id,
    workOrderMaterialId: restricted ? undefined : line.sourceLineId,
    lineNo: line.lineNo,
    materialId: line.materialId,
    materialCode: line.materialCode,
    materialName: line.materialName,
    unitName: line.unitName,
    ownershipType: line.ownershipType ?? null,
    projectId: line.projectId ?? null,
    projectNo: line.projectNo ?? line.projectCode ?? null,
    projectName: line.projectName ?? null,
    costLayerId: line.costLayerId ?? null,
    plannedQuantity: '',
    issuedQuantity: '',
    supplementedQuantity: '',
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
    trackingMethod: inferTrackingMethodFromAllocations(line.trackingAllocations),
    trackingMethodName: inferTrackingMethodFromAllocations(line.trackingAllocations) === 'SERIAL' ? '序列号管理' : '批次管理',
    trackingAllocations: line.trackingAllocations ?? [],
    quantity: line.quantity,
    reason: line.reason ?? '',
  }
}

function numericLineValue(line: MaterialSupplementLineDraft, key: 'maxSelectableQuantity'): number | null {
  const value = line[key]
  if (value === null || value === undefined || value === '') {
    return null
  }
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : null
}

function lineDisabledReason(line: MaterialSupplementLineDraft): string {
  return line.disabledReason ?? '该候选库存不可生产补料'
}

function lineUnavailable(line: MaterialSupplementLineDraft): boolean {
  const maxSelectableQuantity = numericLineValue(line, 'maxSelectableQuantity')
  return line.selectable === false || (maxSelectableQuantity !== null && maxSelectableQuantity <= 0)
}

async function loadSources() {
  const page = await returnRefundReversalApi.productionMaterialSupplementSources.list({
    keyword: sourceFilters.keyword,
    page: 1,
    pageSize: 20,
  })
  sources.value = pageItems(page)
  if (!isEdit.value && sources.value.length > 0 && form.workOrderId === '') {
    await chooseSource(sources.value[0])
  }
}

async function loadDetail() {
  if (!routeId.value) {
    return
  }
  const detail = await returnRefundReversalApi.productionMaterialSupplements.get(routeId.value)
  editDetail.value = detail
  form.workOrderId = sourceRestricted(detail.source) ? '' : detail.source.sourceId ?? ''
  form.warehouseId = sourceRestricted(detail.source) ? '' : detail.warehouseId
  form.businessDate = detail.businessDate
  form.remark = detail.remark ?? ''
  form.clientRequestId = detail.clientRequestId ?? `material-supplement-${detail.id}`
  if (detail.status !== 'DRAFT') {
    nonEditableStatus.value = detail.status
    lines.value = []
    return
  }
  nonEditableStatus.value = null
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
      form.businessDate = todayText()
      form.clientRequestId = `material-supplement-${Date.now()}`
      await loadSources()
    }
  } catch (caught) {
    sources.value = []
    lines.value = []
    error.value = productionErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function chooseSource(source: ProductionMaterialSupplementSource) {
  form.workOrderId = source.workOrderId
  form.warehouseId = source.warehouseId
  form.businessDate = todayText()
  lines.value = await Promise.all(source.materials.map(lineDraftFromSource))
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
    error.value = productionErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function updateLine(index: number, patch: Partial<MaterialSupplementLineDraft>) {
  lines.value = lines.value.map((line, currentIndex) => (currentIndex === index ? { ...line, ...patch } : line))
}

async function openTrackingPicker(index: number) {
  const line = lines.value[index]
  if (!line || line.trackingMethod === 'NONE' || !form.warehouseId) {
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
        warehouseId: form.warehouseId,
        onlyAvailable: false,
        page: 1,
        pageSize: 20,
      })
      trackingCandidates.value = pageItems(page).map(mapBatchCandidate)
    } else {
      const page = await inventoryApi.serials.list({
        materialId: line.materialId,
        warehouseId: form.warehouseId,
        onlyAvailable: false,
        page: 1,
        pageSize: 20,
      })
      trackingCandidates.value = pageItems(page).map(mapSerialCandidate)
    }
  } catch (caught) {
    trackingCandidates.value = []
    trackingCandidateError.value = productionErrorMessage(caught)
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

function buildPayload(): ProductionMaterialSupplementPayload | ProductionMaterialSupplementUpdatePayload | null {
  if (!canEditForm.value) {
    submitError.value = `当前生产补料${nonEditableStatusText.value}，不可编辑`
    return null
  }
  const payloadLines: ProductionMaterialSupplementUpdatePayloadLine[] = []
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
    const quantity = validateProductionQuantity(line.quantity)
    if (quantity.message || !quantity.payloadValue) {
      submitError.value = `${line.materialName}：${quantity.message}`
      return null
    }
    const maxSelectableQuantity = numericLineValue(line, 'maxSelectableQuantity')
    if (maxSelectableQuantity !== null && quantity.value !== null && quantity.value > maxSelectableQuantity) {
      submitError.value = `${line.materialName}：补料数量不能超过最大可选数量`
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
    const payloadLine: ProductionMaterialSupplementUpdatePayloadLine = {
      quantity: quantity.payloadValue,
      reason: line.reason,
    }
    if (line.ownershipType) {
      payloadLine.ownershipType = line.ownershipType
    }
    if (line.projectId !== null && line.projectId !== undefined) {
      payloadLine.projectId = line.projectId
    }
    if (line.costLayerId !== null && line.costLayerId !== undefined) {
      payloadLine.costLayerId = line.costLayerId
    }
    const trackingPayload = outboundTrackingAllocationsPayload(line.trackingMethod, line.trackingAllocations, quantity.payloadValue)
    if (trackingPayload) {
      payloadLine.trackingAllocations = trackingPayload
    }
    if (isEdit.value && line.id !== undefined) {
      payloadLine.id = line.id
    }
    if (line.workOrderMaterialId !== undefined) {
      payloadLine.workOrderMaterialId = line.workOrderMaterialId
    }
    if (!isEdit.value && payloadLine.workOrderMaterialId === undefined) {
      submitError.value = `${line.materialName}：工单用料行缺失`
      return null
    }
    if (isEdit.value && payloadLine.id === undefined && payloadLine.workOrderMaterialId === undefined) {
      submitError.value = `${line.materialName}：补料行标识缺失`
      return null
    }
    payloadLines.push(payloadLine)
  }
  if (!isEdit.value && (!form.workOrderId || !form.warehouseId)) {
    submitError.value = '请选择来源生产工单'
    return null
  }
  if (!form.businessDate) {
    submitError.value = '业务日期不能为空'
    return null
  }
  if (!payloadLines.length) {
    submitError.value = firstUnavailableReason || '至少填写一行补料数量'
    return null
  }

  const basePayload = {
    businessDate: form.businessDate,
    clientRequestId: form.clientRequestId || `material-supplement-${Date.now()}`,
    idempotencyKey: createIdempotencyKey('production-material-supplement-save'),
    remark: form.remark,
    lines: payloadLines,
  }
  if (isEdit.value) {
    if (editDetail.value?.version === undefined) {
      submitError.value = '生产补料版本缺失，请刷新后重试'
      return null
    }
    return {
      ...basePayload,
      ...(form.workOrderId ? { workOrderId: form.workOrderId } : {}),
      ...(form.warehouseId ? { warehouseId: form.warehouseId } : {}),
      version: editDetail.value.version,
    }
  }
  return {
    ...basePayload,
    workOrderId: form.workOrderId,
    warehouseId: form.warehouseId,
  } as ProductionMaterialSupplementPayload
}

function isSupplementUpdatePayload(
  payload: ProductionMaterialSupplementPayload | ProductionMaterialSupplementUpdatePayload,
): payload is ProductionMaterialSupplementUpdatePayload {
  return 'version' in payload
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
    let detail: ProductionMaterialSupplementDetail
    if (isEdit.value && routeId.value) {
      if (!isSupplementUpdatePayload(payload)) {
        submitError.value = '生产补料版本缺失，请刷新后重试'
        return
      }
      detail = await returnRefundReversalApi.productionMaterialSupplements.update(routeId.value, payload)
    } else {
      if (isSupplementUpdatePayload(payload)) {
        submitError.value = '生产补料创建参数异常，请刷新后重试'
        return
      }
      detail = await returnRefundReversalApi.productionMaterialSupplements.create(payload)
    }
    await router.push({
      name: 'production-material-supplement-detail',
      params: { id: String(detail.id) },
      query: queryWithReturnTo({}, routeReturnTo(route)),
    })
  } catch (caught) {
    submitError.value = productionErrorMessage(caught)
  } finally {
    submitting.value = false
  }
}

function backToList() {
  void router.push({ name: 'production-material-supplements' })
}

function returnToDetail() {
  if (!routeId.value) {
    return
  }
  void router.push({
    name: 'production-material-supplement-detail',
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
            name="material-supplement-source-keyword"
            clearable
            placeholder="工单号或物料"
          />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-material-supplement-sources" type="primary" @click="searchSources">查询来源</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="submitError" class="state-alert" type="error" :title="submitError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="生产补料表单加载中" :closable="false" />
    </template>

    <el-result
      v-if="!canEditForm"
      icon="warning"
      :title="`当前生产补料${nonEditableStatusText}，不可编辑`"
      sub-title="已过账或已取消的生产补料只能查看，不能继续修改草稿内容。"
    >
      <template #extra>
        <el-button data-test="return-material-supplement-detail" type="primary" @click="returnToDetail">返回详情</el-button>
      </template>
    </el-result>

    <div v-else class="form-layout">
      <el-card v-if="!isEdit" class="section-card" shadow="never">
        <template #header>补料来源</template>
        <el-empty v-if="!loading && sources.length === 0" description="暂无可补料生产工单" />
        <div
          v-else
          data-test="material-supplement-source-table-scroll"
          class="table-scroll form-table-scroll form-table-scroll--source"
        >
          <el-table :data="sources" :empty-text="loading ? '加载中' : '暂无可补料生产工单'" stripe>
            <el-table-column label="选择" width="80">
              <template #default="{ row }">
                <el-radio
                  :model-value="`${form.workOrderId}-${form.warehouseId}`"
                  :label="`${row.workOrderId}-${row.warehouseId}`"
                  @change="chooseSource(row)"
                >
                  选择
                </el-radio>
              </template>
            </el-table-column>
            <el-table-column prop="workOrderNo" label="生产工单" min-width="170" show-overflow-tooltip />
            <el-table-column label="工单状态" min-width="110">
              <template #default="{ row }">{{ workOrderStatusLabel(row.workOrderStatus) }}</template>
            </el-table-column>
            <el-table-column prop="warehouseName" label="仓库" min-width="130" show-overflow-tooltip />
          </el-table>
        </div>
      </el-card>

      <el-card class="section-card" shadow="never">
        <template #header>补料信息</template>
        <el-form class="document-form" label-width="96px">
          <el-form-item label="来源工单">
            <span>{{ sourceDisplayText }}</span>
          </el-form-item>
          <el-form-item label="业务日期">
            <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD"
              v-model="form.businessDate"
              name="material-supplement-business-date"
              placeholder="选择日期"
              style="width: 180px"
            />
          </el-form-item>
          <el-form-item label="备注">
            <el-input
              v-model="form.remark"
              name="material-supplement-remark"
              type="textarea"
              :rows="2"
              placeholder="补料说明"
            />
          </el-form-item>
        </el-form>
      </el-card>

      <el-card class="section-card" shadow="never">
        <template #header>补料明细</template>
        <div data-test="material-supplement-line-table-scroll" class="table-scroll form-table-scroll form-table-scroll--lines">
          <el-table :data="lines" :empty-text="loading ? '加载中' : '暂无补料明细'" stripe>
            <el-table-column prop="lineNo" label="行号" width="80" />
            <el-table-column label="物料" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.materialCode }} {{ row.materialName }}
              </template>
            </el-table-column>
            <el-table-column prop="unitName" label="单位" width="80" />
            <el-table-column label="项目来源" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <template v-if="row.ownershipType === 'PROJECT'">
                  {{ row.projectNo || row.projectId || '-' }} {{ row.projectName || '' }}
                </template>
                <template v-else>公共库存</template>
                <div v-if="row.costLayerId" class="tracking-empty-text">
                  成本层 #{{ row.costLayerId }}
                </div>
              </template>
            </el-table-column>
            <el-table-column label="计划用量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProductionQuantity(row.plannedQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="已领数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProductionQuantity(row.issuedQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="已补数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProductionQuantity(row.supplementedQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="可用库存" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProductionQuantity(row.availableStockQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="质量状态" min-width="110">
              <template #default="{ row }">
                <QualityStatusTag :quality-status="row.qualityStatus" :quality-status-name="row.qualityStatusName" />
              </template>
            </el-table-column>
            <el-table-column label="现存数量" min-width="120" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProductionQuantity(row.quantityOnHand) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="合格可用" min-width="120" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProductionQuantity(row.availableQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="最大可选" min-width="120" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProductionQuantity(row.maxSelectableQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="禁用原因" min-width="190" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="candidate-disabled-reason">{{ row.disabledReason || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="参考单价" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesAmount(row.unitPrice) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="批次/序列" min-width="260">
              <template #default="{ row, $index }">
                <template v-if="row.trackingMethod !== 'NONE'">
                  <el-button
                    size="small"
                    :data-test="`open-material-supplement-tracking-${$index}`"
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
            <el-table-column label="补料数量" min-width="150">
              <template #default="{ row }">
                <el-input
                  v-model="row.quantity"
                  :name="`material-supplement-line-quantity-${lineInputKey(row)}`"
                  placeholder="0.000000"
                  :disabled="lineUnavailable(row)"
                />
              </template>
            </el-table-column>
            <el-table-column label="补料原因" min-width="170">
              <template #default="{ row }">
                <el-input
                  v-model="row.reason"
                  :name="`material-supplement-line-reason-${lineInputKey(row)}`"
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

      <div data-test="material-supplement-form-actions" class="form-actions form-actions--stable">
        <el-button @click="backToList">取消</el-button>
        <el-button
          data-test="submit-material-supplement"
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

.form-table-scroll {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
}

.form-table-scroll--source {
  max-height: 280px;
  overflow: auto;
}

.form-table-scroll--lines {
  max-height: 420px;
  overflow: auto;
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

.tracking-empty-text {
  color: var(--qherp-muted);
  font-size: 12px;
}

.form-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

.form-actions--stable {
  background: var(--qherp-surface);
  border-top: 1px solid #dcdfe6;
  bottom: 0;
  padding-top: 12px;
  position: sticky;
  z-index: 2;
}
</style>
