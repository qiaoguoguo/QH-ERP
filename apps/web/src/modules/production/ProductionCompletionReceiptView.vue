<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { InventoryTrackingAllocationPayload, InventoryTrackingMethod } from '../../shared/api/inventoryApi'
import { masterDataApi, type MaterialRecord, type WarehouseRecord } from '../../shared/api/masterDataApi'
import {
  projectProductionApi,
  type ProjectProductionCompletionReceiptPayload,
  type ProjectProductionWorkOrderDetailRecord,
  type ResourceId,
} from '../../shared/api/projectProductionApi'
import { useAuthStore } from '../../stores/authStore'
import TrackingAllocationEditor from '../inventory/tracking/TrackingAllocationEditor.vue'
import { validateInboundTrackingAllocations } from '../inventory/tracking/trackingPayloadHelpers'
import { formatInventoryAmount, validateInventoryMoney, valuationStateLabel } from '../inventory/inventoryPageHelpers'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import ProductionWorkOrderStatusTag from './ProductionWorkOrderStatusTag.vue'
import {
  formatProductionQuantity,
  createProductionIdempotencyKey,
  productionErrorMessage,
  todayText,
  validateProductionQuantity,
} from './productionPageHelpers'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const workOrder = ref<ProjectProductionWorkOrderDetailRecord | null>(null)
const warehouses = ref<WarehouseRecord[]>([])
const productTracking = ref<Pick<MaterialRecord, 'trackingMethod' | 'trackingMethodName'>>({
  trackingMethod: 'NONE',
  trackingMethodName: '不追踪',
})
const trackingAllocations = ref<InventoryTrackingAllocationPayload[]>([])
const loading = ref(true)
const referenceLoading = ref(true)
const error = ref('')
const formError = ref('')
const formSubmitting = ref(false)
const form = reactive({
  businessDate: todayText(),
  receiptWarehouseId: '' as ResourceId | '',
  quantity: '',
  provisionalUnitCost: '',
  remark: '',
})

const executable = computed(() => workOrder.value?.status === 'RELEASED' || workOrder.value?.status === 'IN_PROGRESS')
const canCreateReceipt = computed(() => authStore.hasPermission('production:receipt:create'))
const canSubmitReceipt = computed(() => executable.value && canCreateReceipt.value)
const remainingReceiptQuantity = computed(() => {
  if (!workOrder.value) {
    return 0
  }
  return Math.max(0, Number(workOrder.value.qualifiedQuantity) - Number(workOrder.value.receivedQuantity))
})
const requiresManualProvisionalUnitCost = computed(() => Boolean(workOrder.value?.requiresManualProvisionalUnitCost))
const valuationStateText = computed(() => valuationStateLabel(workOrder.value?.completionValuationState))

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

async function loadReferences() {
  referenceLoading.value = true
  try {
    const warehousePage = await masterDataApi.warehouses.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 })
    warehouses.value = pageItems(warehousePage)
  } catch (caught) {
    warehouses.value = []
    error.value = productionErrorMessage(caught)
  } finally {
    referenceLoading.value = false
  }
}

async function loadWorkOrder() {
  loading.value = true
  error.value = ''
  try {
    const detail = await projectProductionApi.workOrders.get(route.params.id as ResourceId)
    const material = await masterDataApi.materials.get(detail.productMaterialId)
    workOrder.value = detail
    form.receiptWarehouseId = detail.receiptWarehouseId ?? ''
    form.provisionalUnitCost = ''
    productTracking.value = {
      trackingMethod: material.trackingMethod,
      trackingMethodName: material.trackingMethodName,
    }
    trackingAllocations.value = []
  } catch (caught) {
    workOrder.value = null
    productTracking.value = {
      trackingMethod: 'NONE',
      trackingMethodName: '不追踪',
    }
    trackingAllocations.value = []
    error.value = productionErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function trackingAllocationsPayload(trackingMethod: InventoryTrackingMethod) {
  if (trackingMethod === 'NONE') {
    return undefined
  }
  const allocations = trackingAllocations.value
    .map((allocation) => ({
      ...(trackingMethod === 'BATCH' && String(allocation.batchNo ?? '').trim() ? { batchNo: String(allocation.batchNo).trim() } : {}),
      ...(trackingMethod === 'SERIAL' && String(allocation.serialNo ?? '').trim() ? { serialNo: String(allocation.serialNo).trim() } : {}),
      quantity: String(allocation.quantity ?? (trackingMethod === 'SERIAL' ? '1' : '')).trim(),
    }))
    .filter((allocation) => (allocation.batchNo || allocation.serialNo) && allocation.quantity)
  return allocations.length > 0 ? allocations : undefined
}

function validateForm(): ProjectProductionCompletionReceiptPayload | null {
  if (!workOrder.value) {
    formError.value = '生产工单未加载'
    return null
  }
  if (!canCreateReceipt.value) {
    formError.value = '缺少完工入库创建权限'
    return null
  }
  if (!executable.value) {
    formError.value = '仅已下达或生产中的工单可完工入库'
    return null
  }
  if (!form.businessDate.trim()) {
    formError.value = '请填写入库日期'
    return null
  }
  const receiptWarehouseId = normalizeRequiredId(form.receiptWarehouseId)
  if (receiptWarehouseId === null) {
    formError.value = '请选择入库仓库'
    return null
  }
  const quantityResult = validateProductionQuantity(form.quantity)
  if (quantityResult.payloadValue === null) {
    formError.value = quantityResult.message ?? '入库数量不正确'
    return null
  }
  if (quantityResult.value !== null && quantityResult.value > remainingReceiptQuantity.value) {
    formError.value = '入库数量不能超过累计合格报工减已入库数量'
    return null
  }
  if (productTracking.value.trackingMethod !== 'NONE') {
    const trackingMessages = validateInboundTrackingAllocations(
      productTracking.value.trackingMethod,
      trackingAllocations.value,
      quantityResult.payloadValue,
    )
    if (trackingMessages.length > 0) {
      formError.value = trackingMessages[0]
      return null
    }
  }
  if (requiresManualProvisionalUnitCost.value) {
    const provisionalResult = validateInventoryMoney(form.provisionalUnitCost, 6)
    if (provisionalResult.payloadValue === null) {
      formError.value = provisionalResult.message === '金额不能为空'
        ? '请输入暂估单价'
        : provisionalResult.message?.replace('金额', '暂估单价') ?? '暂估单价不正确'
      return null
    }
  }

  formError.value = ''
  const trackingPayload = trackingAllocationsPayload(productTracking.value.trackingMethod)
  const provisionalUnitCost = requiresManualProvisionalUnitCost.value
    ? validateInventoryMoney(form.provisionalUnitCost, 6).payloadValue
    : null
  return {
    version: workOrder.value.version,
    idempotencyKey: createProductionIdempotencyKey('production-completion-receipt-save'),
    businessDate: form.businessDate.trim(),
    receiptWarehouseId,
    quantity: quantityResult.payloadValue,
    ...(provisionalUnitCost ? { provisionalUnitCost } : {}),
    ...(trackingPayload ? { trackingAllocations: trackingPayload } : {}),
    ...(form.remark.trim() ? { remark: form.remark.trim() } : {}),
  }
}

async function submitReceipt() {
  if (!workOrder.value || formSubmitting.value) {
    return
  }
  if (!canCreateReceipt.value) {
    formError.value = '缺少完工入库创建权限'
    return
  }
  const payload = validateForm()
  if (!payload) {
    return
  }
  formSubmitting.value = true
  try {
    await projectProductionApi.completionReceipts.create(workOrder.value.id, payload)
    await router.push({ name: 'production-work-order-detail', params: { id: String(workOrder.value.id) } })
  } catch (caught) {
    formError.value = productionErrorMessage(caught)
  } finally {
    formSubmitting.value = false
  }
}

function cancel() {
  void router.push({ name: 'production-work-order-detail', params: { id: String(route.params.id) } })
}

onMounted(() => {
  void loadReferences()
  void loadWorkOrder()
})
</script>

<template>
  <MasterDataTableView title="完工入库" description="从生产工单创建完工入库草稿，过账后增加产品库存。">
    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="formError" class="state-alert" type="error" :title="formError" :closable="false" />
      <el-alert v-if="loading || referenceLoading" class="state-alert" type="info" title="完工入库页面加载中" :closable="false" />
      <el-alert v-if="workOrder && !canCreateReceipt" class="state-alert" type="warning" title="缺少完工入库创建权限，无法保存入库单" :closable="false" />
      <el-alert v-if="workOrder && !executable" class="state-alert" type="warning" title="当前工单状态不可完工入库" :closable="false" />
    </template>

    <div v-if="workOrder" class="production-execution-page">
      <section class="work-order-summary">
        <div>
          <span>工单编号</span>
          <strong>{{ workOrder.workOrderNo }}</strong>
        </div>
        <div>
          <span>产品</span>
          <strong>{{ workOrder.productMaterialCode }} {{ workOrder.productMaterialName }}</strong>
        </div>
        <div>
          <span>状态</span>
          <ProductionWorkOrderStatusTag :status="workOrder.status" />
        </div>
        <div>
          <span>剩余可入库</span>
          <strong>{{ formatProductionQuantity(remainingReceiptQuantity) }}</strong>
        </div>
        <div>
          <span>计价状态</span>
          <strong>{{ valuationStateText }}</strong>
        </div>
      </section>
      <el-alert
        v-if="requiresManualProvisionalUnitCost"
        class="state-alert"
        type="warning"
        title="首次完工需要录入暂估单价，暂估库存后续出库价值不会被重写。"
        :closable="false"
      />
        <el-alert
        v-else-if="workOrder.costVisible !== false && workOrder.currentAverageUnitCost"
        class="state-alert"
        type="info"
        :title="`沿用当前公共平均价 ${formatInventoryAmount(workOrder.currentAverageUnitCost, 6)}，正式完工成本留待后续阶段处理。`"
        :closable="false"
      />

      <el-form label-position="top" class="execution-form">
        <div class="execution-form-grid">
          <el-form-item label="入库日期">
            <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="form.businessDate" name="production-receipt-date" placeholder="选择日期" :disabled="!canSubmitReceipt" />
          </el-form-item>
          <el-form-item label="入库仓库">
            <el-select v-model="form.receiptWarehouseId" filterable placeholder="请选择入库仓库" style="width: 100%" :disabled="!canSubmitReceipt">
              <el-option v-for="warehouse in warehouses" :key="warehouse.id" :label="warehouse.name" :value="warehouse.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="入库数量">
            <el-input v-model="form.quantity" name="production-receipt-quantity" placeholder="0.000000" :disabled="!canSubmitReceipt" />
          </el-form-item>
          <el-form-item v-if="requiresManualProvisionalUnitCost" label="暂估单价">
            <el-input
              v-model="form.provisionalUnitCost"
              name="production-receipt-provisional-unit-cost"
              placeholder="0.000000"
              :disabled="!canSubmitReceipt"
            />
          </el-form-item>
        </div>
        <el-form-item label="备注">
          <el-input v-model="form.remark" name="production-receipt-remark" type="textarea" :rows="3" placeholder="可选" :disabled="!canSubmitReceipt" />
        </el-form-item>
        <el-form-item v-if="productTracking.trackingMethod !== 'NONE'" :label="productTracking.trackingMethodName">
          <TrackingAllocationEditor
            v-model="trackingAllocations"
            :tracking-method="productTracking.trackingMethod"
            :expected-quantity="form.quantity"
            :disabled="!canSubmitReceipt"
          />
        </el-form-item>
      </el-form>
    </div>

    <div class="form-footer">
      <el-button @click="cancel">取消</el-button>
      <el-button
        type="primary"
        :loading="formSubmitting"
        :disabled="formSubmitting || !canSubmitReceipt"
        :title="!canCreateReceipt ? '缺少完工入库创建权限' : ''"
        @click="submitReceipt"
      >
        保存入库单
      </el-button>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.production-execution-page {
  padding: 14px;
}

.work-order-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.work-order-summary > div {
  border: 1px solid var(--qherp-border);
  border-radius: 8px;
  min-width: 0;
  padding: 12px;
}

.work-order-summary span {
  color: var(--qherp-muted);
  display: block;
  font-size: 12px;
}

.work-order-summary strong {
  display: block;
  margin-top: 4px;
  word-break: break-word;
}

.execution-form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 14px;
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 14px 14px;
  border-top: 1px solid var(--qherp-border);
}

@media (max-width: 900px) {
  .execution-form-grid,
  .work-order-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .execution-form-grid,
  .work-order-summary {
    grid-template-columns: 1fr;
  }

  .form-footer {
    align-items: stretch;
    flex-direction: column-reverse;
  }
}
</style>
