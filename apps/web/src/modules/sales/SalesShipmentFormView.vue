<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { queryWithReturnTo, routeReturnTo } from '../../shared/navigation/navigationReturn'
import { masterDataApi, type MaterialRecord, type WarehouseRecord } from '../../shared/api/masterDataApi'
import { inventoryApi, type InventoryTrackingAllocationPayload } from '../../shared/api/inventoryApi'
import {
  salesApi,
  type ResourceId,
  type SalesOrderDetailRecord,
  type SalesOrderStatus,
  type SalesShipmentDetailRecord,
  type SalesShipmentLineRecord,
  type SalesShipmentPayload,
} from '../../shared/api/salesApi'
import {
  salesFulfillmentApi,
  type SalesDeliveryPlanRecord,
} from '../../shared/api/salesFulfillmentApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import TrackingPickerDrawer from '../inventory/tracking/TrackingPickerDrawer.vue'
import {
  mapBatchCandidate,
  mapSerialCandidate,
  outboundTrackingAllocationsPayload,
  validateOutboundTrackingAllocations,
  type TrackingCandidateRecord,
} from '../inventory/tracking/trackingPayloadHelpers'
import { pageItems } from '../system/shared/pageHelpers'
import SalesOrderStatusTag from './SalesOrderStatusTag.vue'
import SalesShipmentLineEditor from './SalesShipmentLineEditor.vue'
import SalesShipmentStatusTag from './SalesShipmentStatusTag.vue'
import {
  type SalesShipmentLineDraft,
  type SalesShipmentSourceLine,
  formatSalesQuantity,
  newSalesShipmentLine,
  normalizeRequiredId,
  salesErrorMessage,
  salesShipmentSourceFromOrderLine,
  salesShipmentSourceFromShipmentLine,
  validateSalesQuantity,
} from './salesPageHelpers'

const allowedSourceStatuses: SalesOrderStatus[] = ['CONFIRMED', 'PARTIALLY_SHIPPED']
const route = useRoute()
const router = useRouter()
const warehouses = ref<WarehouseRecord[]>([])
const sourceOrder = ref<SalesOrderDetailRecord | null>(null)
const editingRecord = ref<SalesShipmentDetailRecord | null>(null)
const sourceLines = ref<SalesShipmentSourceLine[]>([])
const deliveryPlans = ref<SalesDeliveryPlanRecord[]>([])
const referenceLoading = ref(true)
const loading = ref(false)
const referenceError = ref('')
const formError = ref('')
const formSubmitting = ref(false)
const lineErrors = ref<Record<number, string>>({})
const materialTrackingCache = new Map<string, Pick<MaterialRecord, 'trackingMethod' | 'trackingMethodName'>>()
const trackingPickerVisible = ref(false)
const trackingPickerLineIndex = ref<number | null>(null)
const trackingCandidates = ref<TrackingCandidateRecord[]>([])
const trackingCandidateLoading = ref(false)
const trackingCandidateError = ref('')
const form = reactive({
  warehouseId: '' as ResourceId | '',
  businessDate: '',
  remark: '',
})
const lines = ref<SalesShipmentLineDraft[]>([newSalesShipmentLine()])

const isEdit = computed(() => Boolean(route.params.id))
const isPostedRecord = computed(() => editingRecord.value?.status === 'POSTED')
const sourceOrderAllowed = computed(() => (
  isEdit.value
    ? Boolean(sourceOrder.value)
    : Boolean(sourceOrder.value && allowedSourceStatuses.includes(sourceOrder.value.status))
))
const canEditForm = computed(() => (
  !isPostedRecord.value
  && (!isEdit.value || Boolean(editingRecord.value))
  && sourceOrderAllowed.value
))
const missingDeliveryPlansForNewOrder = computed(() => (
  !isEdit.value
  && Boolean(sourceOrder.value)
  && allowedSourceStatuses.includes(sourceOrder.value!.status)
  && deliveryPlans.value.length === 0
))
const canSubmit = computed(() => !formSubmitting.value && canEditForm.value && !missingDeliveryPlansForNewOrder.value)
const pageTitle = computed(() => (isEdit.value ? '编辑销售出库' : '新建销售出库'))
const orderSummary = computed(() => editingRecord.value?.orderSummary ?? sourceOrder.value)

async function loadReferences() {
  referenceLoading.value = true
  referenceError.value = ''
  try {
    const warehousePage = await masterDataApi.warehouses.list({
      keyword: '',
      status: 'ENABLED',
      page: 1,
      pageSize: 200,
    })
    warehouses.value = pageItems(warehousePage)
  } catch (caught) {
    warehouses.value = []
    referenceError.value = salesErrorMessage(caught)
  } finally {
    referenceLoading.value = false
  }
}

async function loadSourceOrder() {
  if (!route.params.orderId) {
    return
  }
  loading.value = true
  formError.value = ''
  try {
    const detail = await salesApi.orders.get(route.params.orderId as ResourceId)
    deliveryPlans.value = await loadOrderDeliveryPlans(detail.id)
    sourceOrder.value = detail
    if (allowedSourceStatuses.includes(detail.status) && deliveryPlans.value.length === 0) {
      sourceLines.value = []
      formError.value = '该订单尚未初始化交付计划，请返回订单详情初始化/拆分交付计划后再创建出库'
      return
    }
    sourceLines.value = await enrichSourceLineTracking(detail.lines
      .filter((line) => Number(line.remainingQuantity) > 0)
      .map((line) => withDeliveryPlan(salesShipmentSourceFromOrderLine(line)))
      .filter((line) => Boolean(line.deliveryPlanId)))
    if (!allowedSourceStatuses.includes(detail.status)) {
      formError.value = '仅已确认或部分出库销售订单可创建销售出库'
    }
  } catch (caught) {
    formError.value = salesErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function loadOrderDeliveryPlans(orderId: ResourceId): Promise<SalesDeliveryPlanRecord[]> {
  const response = await salesFulfillmentApi.deliveryPlans.listByOrder(orderId)
  const payload = response as { items?: SalesDeliveryPlanRecord[]; lines?: SalesDeliveryPlanRecord[] }
  return Array.isArray(payload.items) ? payload.items : (Array.isArray(payload.lines) ? payload.lines : [])
}

function withDeliveryPlan(line: SalesShipmentSourceLine): SalesShipmentSourceLine {
  const plan = deliveryPlans.value.find((item) => (
    String(item.orderLineId) === String(line.id)
    && item.status !== 'CLOSED'
    && item.status !== 'CANCELLED'
  ))
  if (!plan) {
    return line
  }
  return {
    ...line,
    deliveryPlanId: plan.id,
    deliveryPlanNo: plan.planNo ?? String(plan.id),
    deliveryPlanDate: plan.planDate ?? plan.plannedDate ?? '',
  }
}

function draftFromShipmentLine(line: SalesShipmentLineRecord): SalesShipmentLineDraft {
  return {
    lineNo: line.lineNo,
    deliveryPlanId: line.deliveryPlanId ?? '',
    deliveryPlanNo: '',
    deliveryPlanDate: '',
    orderLineId: line.orderLineId,
    materialId: line.materialId,
    materialCode: line.materialCode,
    materialName: line.materialName,
    trackingMethod: line.trackingMethod ?? 'NONE',
    trackingMethodName: line.trackingMethodName ?? '不追踪',
    unitId: line.unitId,
    unitName: line.unitName,
    orderedQuantity: Number(line.orderedQuantity) || 0,
    shippedQuantityBefore: Number(line.shippedQuantityBefore) || 0,
    remainingQuantityBefore: Number(line.remainingQuantityBefore) || 0,
    reservationWarehouseId: line.reservationWarehouseId ?? null,
    reservationWarehouseName: line.reservationWarehouseName ?? null,
    qualityStatus: line.qualityStatus ?? null,
    qualityStatusName: line.qualityStatusName ?? null,
    quantityOnHand: line.quantityOnHand ?? null,
    reservedQuantity: line.reservedQuantity ?? null,
    occupiedQuantity: line.occupiedQuantity ?? null,
    availableQuantity: line.availableQuantity ?? null,
    availableToPromiseQuantity: line.availableToPromiseQuantity ?? null,
    selectable: line.selectable ?? null,
    disabledReasonCode: line.disabledReasonCode ?? null,
    disabledReason: line.disabledReason ?? null,
    maxSelectableQuantity: line.maxSelectableQuantity ?? null,
    trackingAllocations: line.trackingAllocations ?? [],
    quantity: String(line.quantity),
    remark: line.remark ?? '',
  }
}

function mergeSourceLines(
  existingSourceLines: SalesShipmentSourceLine[],
  orderDetail: SalesOrderDetailRecord,
): SalesShipmentSourceLine[] {
  const mergedLines: SalesShipmentSourceLine[] = []
  const seenOrderLineIds = new Set<string>()
  const currentSourceLines = orderDetail.lines.map((line) => withDeliveryPlan(salesShipmentSourceFromOrderLine(line)))
  const currentSourceLineById = new Map(currentSourceLines.map((line) => [String(line.id), line]))
  const appendLine = (line: SalesShipmentSourceLine) => {
    const key = String(line.id)
    if (seenOrderLineIds.has(key)) {
      return
    }
    seenOrderLineIds.add(key)
    mergedLines.push(line)
  }

  existingSourceLines
    .map((line) => currentSourceLineById.get(String(line.id)) ?? line)
    .forEach(appendLine)
  currentSourceLines
    .filter((line) => Number(line.remainingQuantityBefore) > 0)
    .forEach(appendLine)

  return mergedLines
}

function refreshDraftLinesWithCurrentOrder(
  draftLines: SalesShipmentLineDraft[],
  orderDetail: SalesOrderDetailRecord,
): SalesShipmentLineDraft[] {
  const currentSourceLineById = new Map(orderDetail.lines
    .map((line) => withDeliveryPlan(salesShipmentSourceFromOrderLine(line)))
    .map((line) => [String(line.id), line]))

  return draftLines.map((line) => {
    const currentSourceLine = currentSourceLineById.get(String(line.orderLineId))
    if (!currentSourceLine) {
      return line
    }
    return {
      ...line,
      deliveryPlanId: line.deliveryPlanId || currentSourceLine.deliveryPlanId || '',
      deliveryPlanNo: line.deliveryPlanNo || currentSourceLine.deliveryPlanNo || '',
      deliveryPlanDate: line.deliveryPlanDate || currentSourceLine.deliveryPlanDate || '',
      materialId: currentSourceLine.materialId,
      materialCode: currentSourceLine.materialCode,
      materialName: currentSourceLine.materialName,
      trackingMethod: currentSourceLine.trackingMethod ?? line.trackingMethod ?? 'NONE',
      trackingMethodName: currentSourceLine.trackingMethodName ?? line.trackingMethodName ?? '不追踪',
      unitId: currentSourceLine.unitId,
      unitName: currentSourceLine.unitName,
      orderedQuantity: currentSourceLine.orderedQuantity,
      shippedQuantityBefore: currentSourceLine.shippedQuantityBefore,
      remainingQuantityBefore: currentSourceLine.remainingQuantityBefore,
      reservationWarehouseId: currentSourceLine.reservationWarehouseId,
      reservationWarehouseName: currentSourceLine.reservationWarehouseName,
      qualityStatus: currentSourceLine.qualityStatus,
      qualityStatusName: currentSourceLine.qualityStatusName,
      quantityOnHand: currentSourceLine.quantityOnHand,
      reservedQuantity: currentSourceLine.reservedQuantity,
      occupiedQuantity: currentSourceLine.occupiedQuantity,
      availableQuantity: currentSourceLine.availableQuantity,
      availableToPromiseQuantity: currentSourceLine.availableToPromiseQuantity,
      selectable: currentSourceLine.selectable,
      disabledReasonCode: currentSourceLine.disabledReasonCode,
      disabledReason: currentSourceLine.disabledReason,
      maxSelectableQuantity: currentSourceLine.maxSelectableQuantity,
    }
  })
}

function sourceLineForDraft(line: SalesShipmentLineDraft): SalesShipmentSourceLine | undefined {
  return sourceLines.value.find((sourceLine) => String(sourceLine.id) === String(line.orderLineId))
}

function reservationWarehouseIdForLine(line: SalesShipmentLineDraft): ResourceId | null {
  return line.reservationWarehouseId ?? sourceLineForDraft(line)?.reservationWarehouseId ?? null
}

function syncWarehouseWithSelectedReservationLines() {
  if (form.warehouseId !== '') {
    return
  }
  const selectedReservationWarehouseIds = Array.from(new Set(lines.value
    .map((line) => reservationWarehouseIdForLine(line))
    .filter((value): value is ResourceId => value !== null && value !== undefined && value !== '')))
  if (selectedReservationWarehouseIds.length === 1) {
    form.warehouseId = selectedReservationWarehouseIds[0]
  }
}

async function materialTracking(materialId: ResourceId) {
  const key = String(materialId)
  const cached = materialTrackingCache.get(key)
  if (cached) {
    return cached
  }
  const material = await masterDataApi.materials.get(materialId)
  const result = {
    trackingMethod: material.trackingMethod,
    trackingMethodName: material.trackingMethodName,
  }
  materialTrackingCache.set(key, result)
  return result
}

async function enrichSourceLineTracking(sourceLines: SalesShipmentSourceLine[]): Promise<SalesShipmentSourceLine[]> {
  return Promise.all(sourceLines.map(async (line) => {
    if (line.trackingMethod) {
      return line
    }
    const tracking = await materialTracking(line.materialId)
    return {
      ...line,
      trackingMethod: tracking.trackingMethod,
      trackingMethodName: tracking.trackingMethodName,
    }
  }))
}

function numericCandidateValue(line: SalesShipmentLineDraft, key: 'maxSelectableQuantity'): number | null {
  const value = line[key] ?? sourceLineForDraft(line)?.[key]
  if (value === null || value === undefined || value === '') {
    return null
  }
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : null
}

function candidateDisabledReason(line: SalesShipmentLineDraft): string {
  return line.disabledReason
    ?? sourceLineForDraft(line)?.disabledReason
    ?? '该候选库存不可销售出库'
}

function candidateUnavailable(line: SalesShipmentLineDraft): boolean {
  const sourceLine = sourceLineForDraft(line)
  const maxSelectableQuantity = numericCandidateValue(line, 'maxSelectableQuantity')
  return line.selectable === false
    || sourceLine?.selectable === false
    || (maxSelectableQuantity !== null && maxSelectableQuantity <= 0)
}

function trackingAllocationsPayload(line: SalesShipmentLineDraft) {
  return outboundTrackingAllocationsPayload(line.trackingMethod ?? 'NONE', line.trackingAllocations, line.quantity)
}

function updateLine(index: number, patch: Partial<SalesShipmentLineDraft>) {
  lines.value = lines.value.map((line, currentIndex) => (currentIndex === index ? { ...line, ...patch } : line))
}

async function openTrackingPicker(index: number) {
  const line = lines.value[index]
  if (!line || !line.trackingMethod || line.trackingMethod === 'NONE') {
    return
  }
  const warehouseId = normalizeRequiredId(form.warehouseId)
  const materialId = normalizeRequiredId(line.materialId)
  if (warehouseId === null || materialId === null) {
    formError.value = '请先选择出库仓库和来源订单行'
    return
  }
  trackingPickerLineIndex.value = index
  trackingPickerVisible.value = true
  trackingCandidateLoading.value = true
  trackingCandidateError.value = ''
  try {
    if (line.trackingMethod === 'BATCH') {
      const page = await inventoryApi.batches.list({
        materialId,
        warehouseId,
        onlyAvailable: false,
        page: 1,
        pageSize: 20,
      })
      trackingCandidates.value = pageItems(page).map(mapBatchCandidate)
    } else {
      const page = await inventoryApi.serials.list({
        materialId,
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

function selectTrackingCandidate(candidate: { id: ResourceId; trackingNo: string; availableQuantity?: string | number | null }) {
  const index = trackingPickerLineIndex.value
  if (index === null) {
    return
  }
  const line = lines.value[index]
  if (!line || !line.trackingMethod || line.trackingMethod === 'NONE') {
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

async function loadRecord() {
  if (!route.params.id) {
    return
  }
  loading.value = true
  formError.value = ''
  try {
    const detail = await salesApi.shipments.get(route.params.id as ResourceId)
    editingRecord.value = detail
    form.warehouseId = detail.warehouseId
    form.businessDate = detail.businessDate
    form.remark = detail.remark ?? ''
    const shipmentSourceLines = detail.lines.map((line) => salesShipmentSourceFromShipmentLine(line))
    sourceLines.value = shipmentSourceLines
    lines.value = detail.lines.map((line) => draftFromShipmentLine(line))
    if (detail.status === 'POSTED') {
      formError.value = '已过账销售出库不可编辑'
      return
    }
    const orderDetail = await salesApi.orders.get(detail.orderId)
    deliveryPlans.value = await loadOrderDeliveryPlans(orderDetail.id)
    sourceOrder.value = orderDetail
    sourceLines.value = await enrichSourceLineTracking(mergeSourceLines(shipmentSourceLines, orderDetail))
    lines.value = refreshDraftLinesWithCurrentOrder(lines.value, orderDetail)
  } catch (caught) {
    formError.value = salesErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function validateForm(): SalesShipmentPayload | null {
  const warehouseId = normalizeRequiredId(form.warehouseId)
  if (warehouseId === null || !form.businessDate.trim() || lines.value.length === 0) {
    formError.value = '请完整填写出库仓库、业务日期和明细'
    lineErrors.value = {}
    return null
  }

  const nextLineErrors: Record<number, string> = {}
  const duplicateSourceLines = new Set<string>()
  const payloadLines = []

  for (const line of lines.value) {
    const orderLineId = normalizeRequiredId(line.orderLineId)
    if (orderLineId === null) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行请选择来源订单行`
      continue
    }
    if (candidateUnavailable(line)) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行${candidateDisabledReason(line)}`
      continue
    }
    const quantityResult = validateSalesQuantity(line.quantity)
    if (quantityResult.payloadValue === null || quantityResult.value === null) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行${quantityResult.message ?? '数量不正确'}`
      continue
    }
    if (quantityResult.value > Number(line.remainingQuantityBefore)) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行本次出库数量不能超过未出库数量`
      continue
    }
    const maxSelectableQuantity = numericCandidateValue(line, 'maxSelectableQuantity')
    if (maxSelectableQuantity !== null && quantityResult.value > maxSelectableQuantity) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行本次出库数量不能超过本次最多出库`
      continue
    }
    const reservationWarehouseId = reservationWarehouseIdForLine(line)
    if (!reservationWarehouseId) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行来源订单行缺少预留仓库，不能创建销售出库`
      continue
    }
    if (String(reservationWarehouseId) !== String(warehouseId)) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行出库仓库必须与来源订单行预留仓库一致`
      continue
    }
    if ((line.trackingAllocations ?? []).length > 0) {
      const trackingMessages = validateOutboundTrackingAllocations(
        line.trackingMethod ?? 'NONE',
        line.trackingAllocations,
        quantityResult.payloadValue,
      )
      if (trackingMessages.length > 0) {
        nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行${trackingMessages[0]}`
        continue
      }
    }
    const duplicateKey = String(orderLineId)
    if (duplicateSourceLines.has(duplicateKey)) {
      formError.value = '同一销售出库内来源订单行不能重复'
      lineErrors.value = {}
      return null
    }
    duplicateSourceLines.add(duplicateKey)
    const materialId = normalizeRequiredId(line.materialId)
    const unitId = normalizeRequiredId(line.unitId)
    const deliveryPlanId = normalizeRequiredId(line.deliveryPlanId)
    payloadLines.push({
      lineNo: line.lineNo,
      orderLineId,
      ...(deliveryPlanId !== null ? { deliveryPlanId } : {}),
      ...(materialId !== null ? { materialId } : {}),
      ...(unitId !== null ? { unitId } : {}),
      quantity: quantityResult.payloadValue,
      ...(trackingAllocationsPayload(line) ? { trackingAllocations: trackingAllocationsPayload(line) } : {}),
      ...(line.remark.trim() ? { remark: line.remark.trim() } : {}),
    })
  }

  lineErrors.value = nextLineErrors
  if (Object.keys(nextLineErrors).length > 0) {
    formError.value = ''
    return null
  }

  formError.value = ''
  return {
    warehouseId,
    businessDate: form.businessDate.trim(),
    ...(form.remark.trim() ? { remark: form.remark.trim() } : {}),
    lines: payloadLines,
  }
}

async function saveShipment() {
  if (formSubmitting.value) {
    return
  }
  if (isEdit.value && !editingRecord.value) {
    formError.value = '销售出库加载失败，不能保存'
    return
  }
  if (editingRecord.value?.status === 'POSTED') {
    formError.value = '已过账销售出库不可编辑'
    return
  }
  if (!isEdit.value && !sourceOrderAllowed.value) {
    formError.value = '仅已确认或部分出库销售订单可创建销售出库'
    return
  }
  if (missingDeliveryPlansForNewOrder.value) {
    formError.value = '该订单尚未初始化交付计划，请返回订单详情初始化/拆分交付计划后再创建出库'
    return
  }
  const payload = validateForm()
  if (!payload) {
    return
  }

  formSubmitting.value = true
  try {
    let result: SalesShipmentDetailRecord
    if (isEdit.value) {
      result = await salesApi.shipments.update(editingRecord.value!.id, payload)
    } else {
      result = await salesApi.shipments.create(route.params.orderId as ResourceId, payload)
    }
    await router.push({
      name: 'sales-shipment-detail',
      params: { id: String(result.id) },
      query: queryWithReturnTo({}, routeReturnTo(route)),
    })
  } catch (caught) {
    formError.value = salesErrorMessage(caught)
  } finally {
    formSubmitting.value = false
  }
}

function cancel() {
  if (editingRecord.value) {
    void router.push({
      name: 'sales-shipment-detail',
      params: { id: String(editingRecord.value.id) },
      query: queryWithReturnTo({}, routeReturnTo(route)),
    })
    return
  }
  void router.push({ name: 'sales-shipments' })
}

function returnToSourceOrderDetail() {
  if (!sourceOrder.value) {
    return
  }
  void router.push({
    name: 'sales-order-detail',
    params: { id: String(sourceOrder.value.id) },
  })
}

onMounted(async () => {
  await Promise.all([
    loadReferences(),
    isEdit.value ? loadRecord() : loadSourceOrder(),
  ])
})

watch(lines, syncWarehouseWithSelectedReservationLines, { deep: true })
</script>

<template>
  <MasterDataTableView :title="pageTitle" description="维护销售出库草稿，过账前可调整出库仓库、业务日期和明细数量。">
    <template #alerts>
      <el-alert v-if="referenceError" class="state-alert" type="error" :title="referenceError" :closable="false" />
      <el-alert v-if="formError" class="state-alert" type="error" :title="formError" :closable="false" />
      <el-button
        v-if="missingDeliveryPlansForNewOrder"
        data-test="return-sales-order-detail"
        type="primary"
        plain
        @click="returnToSourceOrderDetail"
      >
        返回订单详情初始化
      </el-button>
      <el-alert
        v-if="editingRecord && isPostedRecord"
        class="state-alert"
        type="warning"
        title="已过账销售出库不可编辑"
        :closable="false"
      />
      <el-alert
        v-if="loading || referenceLoading"
        class="state-alert"
        type="info"
        title="销售出库表单加载中"
        :closable="false"
      />
    </template>

    <div v-if="orderSummary" class="source-summary">
      <div>
        <span>来源订单</span>
        <strong>{{ orderSummary.orderNo }}</strong>
      </div>
      <div>
        <span>客户</span>
        <strong>{{ orderSummary.customerName }}</strong>
      </div>
      <div>
        <span>订单未出库</span>
        <strong>{{ formatSalesQuantity(orderSummary.remainingQuantity) }}</strong>
      </div>
      <div>
        <span>订单状态</span>
        <SalesOrderStatusTag :status="orderSummary.status" />
      </div>
    </div>

    <el-form label-position="top" class="sales-shipment-form">
      <div v-if="editingRecord" class="edit-status">
        <span>{{ editingRecord.shipmentNo }}</span>
        <SalesShipmentStatusTag :status="editingRecord.status" />
      </div>
      <div class="sales-shipment-form-grid">
        <el-form-item label="出库仓库">
          <el-select
            v-model="form.warehouseId"
            data-test="sales-shipment-warehouse-id"
            filterable
            placeholder="请选择启用仓库"
            style="width: 100%"
            :disabled="!canEditForm"
          >
            <el-option
              v-for="warehouse in warehouses"
              :key="warehouse.id"
              :label="`${warehouse.code} ${warehouse.name}`"
              :value="warehouse.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="业务日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD"
            v-model="form.businessDate"
            name="sales-shipment-business-date"
            placeholder="选择日期"
            :disabled="!canEditForm"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" name="sales-shipment-remark" placeholder="可选" :disabled="!canEditForm" />
        </el-form-item>
      </div>
      <el-form-item
        label="销售出库明细"
        class="sales-shipment-line-form-item"
        data-test="sales-shipment-line-form-item"
      >
        <SalesShipmentLineEditor
          v-model:lines="lines"
          :source-lines="sourceLines"
          :errors="lineErrors"
          :read-only="!canEditForm"
          @open-tracking-picker="openTrackingPicker"
        />
      </el-form-item>
    </el-form>

    <TrackingPickerDrawer
      v-model="trackingPickerVisible"
      :tracking-method="trackingPickerLineIndex === null ? 'NONE' : (lines[trackingPickerLineIndex]?.trackingMethod ?? 'NONE')"
      :candidates="trackingCandidates"
      :selected-allocations="trackingPickerLineIndex === null ? [] : (lines[trackingPickerLineIndex]?.trackingAllocations ?? [])"
      :expected-quantity="trackingPickerLineIndex === null ? null : lines[trackingPickerLineIndex]?.quantity"
      :loading="trackingCandidateLoading"
      :error="trackingCandidateError"
      @select="selectTrackingCandidate"
      @confirm="confirmTrackingAllocations"
    />

    <div class="form-footer">
      <el-button @click="cancel">取消</el-button>
      <el-button
        data-test="save-sales-shipment"
        type="primary"
        :loading="formSubmitting"
        :disabled="!canSubmit"
        @click="saveShipment"
      >
        保存销售出库
      </el-button>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.source-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  max-width: 100%;
  min-width: 0;
  padding: 14px 14px 0;
}

.source-summary > div {
  border: 1px solid var(--qherp-border);
  border-radius: 6px;
  min-width: 0;
  padding: 10px 12px;
}

.source-summary span {
  color: var(--qherp-muted);
  display: block;
  font-size: 12px;
  margin-bottom: 6px;
}

.source-summary strong {
  display: block;
  font-size: 16px;
  font-variant-numeric: tabular-nums;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sales-shipment-form {
  max-width: 100%;
  min-width: 0;
  overflow-x: hidden;
  padding: 14px;
}

.sales-shipment-form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
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

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 14px 14px;
  border-top: 1px solid var(--qherp-border);
}

.sales-shipment-line-form-item,
.sales-shipment-line-form-item :deep(.el-form-item__content) {
  max-width: 100%;
  min-width: 0;
}

.sales-shipment-line-form-item :deep(.sales-shipment-line-editor) {
  width: 100%;
}

@media (max-width: 900px) {
  .source-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .sales-shipment-form-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .source-summary {
    grid-template-columns: 1fr;
  }

  .form-footer {
    align-items: stretch;
    flex-direction: column-reverse;
  }
}
</style>
