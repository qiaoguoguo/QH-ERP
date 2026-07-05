<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  costCollectionApi,
  type CostBasisType,
  type CostRecordDetailRecord,
  type CostRecordPayload,
  type CostType,
  type ResourceId,
} from '../../shared/api/costCollectionApi'
import { masterDataApi, type UnitRecord } from '../../shared/api/masterDataApi'
import { productionApi, type ProductionWorkOrderSummaryRecord } from '../../shared/api/productionApi'
import { queryWithReturnTo, routeReturnTo } from '../../shared/navigation/navigationReturn'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import CostSourceTypeTag from './CostSourceTypeTag.vue'
import CostTypeTag from './CostTypeTag.vue'
import {
  basisTypeLabel,
  costErrorMessage,
  formatCostAmount,
  formatCostQuantity,
  todayText,
  validateCostDecimal,
} from './costPageHelpers'

interface WorkOrderOption {
  id: ResourceId
  workOrderNo: string
  productMaterialCode: string
  productMaterialName: string
  status: string
}

const route = useRoute()
const router = useRouter()
const workOrders = ref<WorkOrderOption[]>([])
const units = ref<UnitRecord[]>([])
const editingRecord = ref<CostRecordDetailRecord | null>(null)
const loading = ref(false)
const referenceLoading = ref(true)
const referenceError = ref('')
const formError = ref('')
const formSubmitting = ref(false)

const form = reactive({
  workOrderId: '' as ResourceId | '',
  costType: 'MANUFACTURING_OVERHEAD' as CostType,
  basisType: 'MANUAL_AMOUNT' as CostBasisType,
  businessDate: todayText(),
  quantity: '',
  unitId: '' as ResourceId | '',
  unitPrice: '',
  amount: '',
  sourceDocumentNo: '',
  remark: '',
})

const isEdit = computed(() => Boolean(route.params.id))
const pageTitle = computed(() => (isEdit.value ? '编辑成本记录' : '新增成本记录'))
const editableManualRecord = computed(() => !isEdit.value || (Boolean(editingRecord.value) && editingRecord.value?.sourceType === 'MANUAL_ENTRY'))
const saveDisabled = computed(() => (
  formSubmitting.value ||
  loading.value ||
  (isEdit.value && !editingRecord.value) ||
  !editableManualRecord.value
))
const selectedWorkOrder = computed(() => workOrders.value.find((item) => String(item.id) === String(form.workOrderId)))
const needsAmount = computed(() => form.basisType === 'MANUAL_AMOUNT')
const needsUnitPriceQuantity = computed(() => form.basisType === 'MANUAL_UNIT_PRICE_QUANTITY')

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

function addWorkOrderOption(option: WorkOrderOption) {
  if (!workOrders.value.some((item) => String(item.id) === String(option.id))) {
    workOrders.value = [option, ...workOrders.value]
  }
}

function summaryToOption(record: ProductionWorkOrderSummaryRecord): WorkOrderOption {
  return {
    id: record.id,
    workOrderNo: record.workOrderNo,
    productMaterialCode: record.productMaterialCode,
    productMaterialName: record.productMaterialName,
    status: record.status,
  }
}

async function loadReferences() {
  referenceLoading.value = true
  referenceError.value = ''
  try {
    const [workOrderPage, unitPage] = await Promise.all([
      productionApi.workOrders.list({ keyword: '', page: 1, pageSize: 100 }),
      masterDataApi.units.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
    ])
    workOrders.value = pageItems(workOrderPage).map(summaryToOption)
    units.value = pageItems(unitPage)
  } catch (caught) {
    workOrders.value = []
    units.value = []
    referenceError.value = costErrorMessage(caught)
  } finally {
    referenceLoading.value = false
  }
}

async function loadInitialWorkOrder() {
  const rawWorkOrderId = Array.isArray(route.query.workOrderId) ? route.query.workOrderId[0] : route.query.workOrderId
  const queryWorkOrderId = normalizeOptionalId(rawWorkOrderId === null || rawWorkOrderId === undefined ? '' : rawWorkOrderId)
  if (queryWorkOrderId === undefined || isEdit.value) {
    return
  }
  try {
    const detail = await productionApi.workOrders.get(queryWorkOrderId)
    addWorkOrderOption({
      id: detail.id,
      workOrderNo: detail.workOrderNo,
      productMaterialCode: detail.productMaterialCode,
      productMaterialName: detail.productMaterialName,
      status: detail.status,
    })
    form.workOrderId = detail.id
  } catch (caught) {
    formError.value = costErrorMessage(caught)
  }
}

async function loadRecord() {
  if (!isEdit.value) {
    return
  }
  loading.value = true
  formError.value = ''
  try {
    const detail = await costCollectionApi.records.get(route.params.id as ResourceId)
    editingRecord.value = detail
    addWorkOrderOption({
      id: detail.workOrderId,
      workOrderNo: detail.workOrderNo,
      productMaterialCode: detail.productMaterialCode,
      productMaterialName: detail.productMaterialName,
      status: detail.workOrderStatus,
    })
    form.workOrderId = detail.workOrderId
    form.costType = detail.costType
    form.basisType = detail.basisType === 'MANUAL_UNIT_PRICE_QUANTITY' ? 'MANUAL_UNIT_PRICE_QUANTITY' : 'MANUAL_AMOUNT'
    form.businessDate = detail.businessDate
    form.quantity = detail.quantity === null || detail.quantity === undefined ? '' : String(detail.quantity)
    form.unitId = detail.unitId ?? ''
    form.unitPrice = detail.unitPrice === null || detail.unitPrice === undefined ? '' : String(detail.unitPrice)
    form.amount = detail.amount === null || detail.amount === undefined ? '' : String(detail.amount)
    form.sourceDocumentNo = detail.sourceDocumentNo ?? ''
    form.remark = detail.remark ?? ''
  } catch (caught) {
    formError.value = costErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function validateForm(): CostRecordPayload | null {
  if (!editableManualRecord.value) {
    formError.value = '自动来源成本记录不可编辑'
    return null
  }
  const workOrderId = normalizeRequiredId(form.workOrderId)
  if (workOrderId === null) {
    formError.value = '请选择生产工单'
    return null
  }
  if (!form.businessDate.trim()) {
    formError.value = '请填写业务日期'
    return null
  }
  if (!form.remark.trim()) {
    formError.value = '请填写手工记录说明'
    return null
  }

  const payload: CostRecordPayload = {
    workOrderId,
    costType: form.costType,
    basisType: form.basisType,
    businessDate: form.businessDate.trim(),
    remark: form.remark.trim(),
    ...(form.sourceDocumentNo.trim() ? { sourceDocumentNo: form.sourceDocumentNo.trim() } : {}),
  }

  if (needsAmount.value) {
    const amountResult = validateCostDecimal(form.amount, '金额', { allowZero: true })
    if (amountResult.payloadValue === null) {
      formError.value = amountResult.message ?? '金额不正确'
      return null
    }
    payload.amount = amountResult.payloadValue
  }

  if (needsUnitPriceQuantity.value) {
    const quantityResult = validateCostDecimal(form.quantity, '数量', { allowZero: false })
    if (quantityResult.payloadValue === null) {
      formError.value = quantityResult.message ?? '数量不正确'
      return null
    }
    const unitPriceResult = validateCostDecimal(form.unitPrice, '单价', { allowZero: true })
    if (unitPriceResult.payloadValue === null) {
      formError.value = unitPriceResult.message ?? '单价不正确'
      return null
    }
    payload.quantity = quantityResult.payloadValue
    payload.unitPrice = unitPriceResult.payloadValue
    const unitId = normalizeOptionalId(form.unitId)
    if (unitId !== undefined) {
      payload.unitId = unitId
    }
  }

  formError.value = ''
  return payload
}

async function saveRecord() {
  if (formSubmitting.value) {
    return
  }
  if (isEdit.value && !editingRecord.value) {
    formError.value = '成本记录未加载完成，不能保存'
    return
  }
  const payload = validateForm()
  if (!payload) {
    return
  }
  formSubmitting.value = true
  try {
    const result = isEdit.value
      ? await costCollectionApi.records.update(editingRecord.value!.id, payload)
      : await costCollectionApi.records.create(payload)
    await router.push({
      name: 'cost-record-detail',
      params: { id: String(result.id) },
      query: queryWithReturnTo({}, routeReturnTo(route)),
    })
  } catch (caught) {
    formError.value = costErrorMessage(caught)
  } finally {
    formSubmitting.value = false
  }
}

function cancel() {
  if (editingRecord.value) {
    void router.push({
      name: 'cost-record-detail',
      params: { id: String(editingRecord.value.id) },
      query: queryWithReturnTo({}, routeReturnTo(route)),
    })
    return
  }
  void router.push({ name: 'cost-records' })
}

onMounted(async () => {
  await loadReferences()
  await loadInitialWorkOrder()
  await loadRecord()
})
</script>

<template>
  <MasterDataTableView :title="pageTitle" description="维护手工成本业务记录，仅用于归集和追溯。">
    <template #alerts>
      <el-alert v-if="referenceError" class="state-alert" type="error" :title="referenceError" :closable="false" />
      <el-alert v-if="formError" class="state-alert" type="error" :title="formError" :closable="false" />
      <el-alert v-if="loading || referenceLoading" class="state-alert" type="info" title="成本记录表单加载中" :closable="false" />
      <el-alert
        v-if="editingRecord && !editableManualRecord"
        class="state-alert"
        type="warning"
        title="自动来源成本记录不可编辑"
        :closable="false"
      />
    </template>

    <el-form label-position="top" class="cost-form">
      <div v-if="editingRecord" class="edit-status">
        <span>当前记录</span>
        <CostTypeTag :type="editingRecord.costType" />
        <CostSourceTypeTag :type="editingRecord.sourceType" />
      </div>
      <div v-if="selectedWorkOrder" class="work-order-summary">
        <div>
          <span>工单编号</span>
          <strong>{{ selectedWorkOrder.workOrderNo }}</strong>
        </div>
        <div>
          <span>产品</span>
          <strong>{{ selectedWorkOrder.productMaterialCode }} {{ selectedWorkOrder.productMaterialName }}</strong>
        </div>
        <div>
          <span>工单状态</span>
          <strong>{{ selectedWorkOrder.status }}</strong>
        </div>
      </div>

      <div class="cost-form-grid">
        <el-form-item label="生产工单">
          <el-select
            v-model="form.workOrderId"
            filterable
            placeholder="请选择生产工单"
            style="width: 100%"
            :disabled="isEdit || !editableManualRecord"
          >
            <el-option
              v-for="workOrder in workOrders"
              :key="workOrder.id"
              :label="`${workOrder.workOrderNo} ${workOrder.productMaterialCode} ${workOrder.productMaterialName}`"
              :value="workOrder.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="成本类型">
          <el-select v-model="form.costType" style="width: 100%" :disabled="!editableManualRecord">
            <el-option label="材料" value="MATERIAL" />
            <el-option label="人工" value="LABOR" />
            <el-option label="制造费用" value="MANUFACTURING_OVERHEAD" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="口径类型">
          <el-select v-model="form.basisType" style="width: 100%" :disabled="!editableManualRecord">
            <el-option label="手工金额" value="MANUAL_AMOUNT" />
            <el-option label="手工单价数量" value="MANUAL_UNIT_PRICE_QUANTITY" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="form.businessDate" name="cost-business-date" placeholder="选择日期" :disabled="!editableManualRecord" />
        </el-form-item>
        <el-form-item v-if="needsUnitPriceQuantity" label="数量">
          <el-input v-model="form.quantity" name="cost-quantity" placeholder="大于 0，最多 6 位小数" :disabled="!editableManualRecord" />
        </el-form-item>
        <el-form-item v-if="needsUnitPriceQuantity" label="单位（可选）">
          <el-select v-model="form.unitId" filterable placeholder="请选择单位（可选）" style="width: 100%" :disabled="!editableManualRecord">
            <el-option v-for="unit in units" :key="unit.id" :label="`${unit.code} ${unit.name}`" :value="unit.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="needsUnitPriceQuantity" label="单价">
          <el-input v-model="form.unitPrice" name="cost-unit-price" placeholder="允许 0，最多 6 位小数" :disabled="!editableManualRecord" />
        </el-form-item>
        <el-form-item v-if="needsAmount" label="金额">
          <el-input v-model="form.amount" name="cost-amount" placeholder="允许 0，最多 6 位小数" :disabled="!editableManualRecord" />
        </el-form-item>
        <el-form-item label="来源说明">
          <el-input v-model="form.sourceDocumentNo" name="cost-source-document-no" placeholder="可填写内部或外部来源号" :disabled="!editableManualRecord" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" name="cost-remark" placeholder="请填写手工记录说明" :disabled="!editableManualRecord" />
        </el-form-item>
      </div>
    </el-form>

    <section v-if="editingRecord" class="readonly-summary">
      <div>原数量：{{ formatCostQuantity(editingRecord.quantity) }}</div>
      <div>原金额：{{ formatCostAmount(editingRecord.amount) }}</div>
      <div>原口径：{{ basisTypeLabel(editingRecord.basisType) }}</div>
    </section>

    <div class="form-footer">
      <el-button @click="cancel">取消</el-button>
      <el-button
        type="primary"
        data-test="save-cost-record"
        :loading="formSubmitting"
        :disabled="saveDisabled"
        @click="saveRecord"
      >
        保存
      </el-button>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.cost-form {
  padding: 14px;
}

.cost-form-grid {
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

.work-order-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.work-order-summary > div,
.readonly-summary {
  border: 1px solid var(--qherp-border);
  border-radius: 8px;
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

.readonly-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin: 0 14px 14px;
  color: var(--qherp-muted);
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 14px 14px;
  border-top: 1px solid var(--qherp-border);
}

@media (max-width: 900px) {
  .work-order-summary {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .cost-form-grid {
    grid-template-columns: 1fr;
  }

  .form-footer {
    align-items: stretch;
    flex-direction: column-reverse;
  }
}
</style>
