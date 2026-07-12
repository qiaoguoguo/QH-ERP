<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { queryWithReturnTo, routeReturnTo } from '../../shared/navigation/navigationReturn'
import { masterDataApi, type MaterialRecord, type PartnerRecord, type WarehouseRecord } from '../../shared/api/masterDataApi'
import {
  salesApi,
  type ResourceId,
  type SalesOrderCreatePayload,
  type SalesOrderDetailRecord,
  type SalesOrderUpdatePayload,
} from '../../shared/api/salesApi'
import {
  salesProjectApi,
  type SalesOrderProjectContractCandidate,
} from '../../shared/api/salesProjectApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import SalesOrderLineEditor from './SalesOrderLineEditor.vue'
import SalesOrderStatusTag from './SalesOrderStatusTag.vue'
import {
  type SalesOrderLineDraft,
  newSalesOrderLine,
  normalizeRequiredId,
  salesErrorMessage,
  salesOrderLineDraftFromRecord,
  validateSalesQuantity,
  validateSalesUnitPrice,
} from './salesPageHelpers'

const route = useRoute()
const router = useRouter()
const customers = ref<PartnerRecord[]>([])
const materials = ref<MaterialRecord[]>([])
const warehouses = ref<WarehouseRecord[]>([])
const orderLinkCandidates = ref<SalesOrderProjectContractCandidate[]>([])
const orderLinkCandidateLoading = ref(false)
const orderLinkCandidateError = ref('')
const editingRecord = ref<SalesOrderDetailRecord | null>(null)
const referenceLoading = ref(true)
const loading = ref(false)
const referenceError = ref('')
const formError = ref('')
const formSubmitting = ref(false)
const lineErrors = ref<Record<number, string>>({})
const form = reactive({
  customerId: '' as ResourceId | '',
  orderDate: '',
  expectedShipDate: '',
  remark: '',
})
const lines = ref<SalesOrderLineDraft[]>([newSalesOrderLine()])
const selectedProjectContractKey = ref('')

const isEdit = computed(() => Boolean(route.params.id))
const isDraftRecord = computed(() => !editingRecord.value || editingRecord.value.status === 'DRAFT')
const canEditForm = computed(() => isDraftRecord.value && (!isEdit.value || Boolean(editingRecord.value)))
const canSubmit = computed(() => !formSubmitting.value && canEditForm.value && !orderLinkCandidateError.value)
const pageTitle = computed(() => (isEdit.value ? '编辑销售订单' : '新建销售订单'))
const sellableMaterials = computed(() => materials.value.filter((material) =>
  material.materialType === 'FINISHED_GOOD' || material.materialType === 'SEMI_FINISHED'))
const selectedProjectContract = computed(() => orderLinkCandidates.value.find((candidate) =>
  projectContractKey(candidate) === selectedProjectContractKey.value))

function projectContractKey(candidate: SalesOrderProjectContractCandidate) {
  return `${candidate.projectId}:${candidate.contractId}`
}

function projectContractLabel(candidate: SalesOrderProjectContractCandidate) {
  const contractTypeLabel = candidate.contractType === 'SUPPLEMENT' ? '补充合同' : '主合同'
  return [
    candidate.projectNo,
    candidate.projectName,
    '/',
    candidate.contractNo,
    candidate.contractName,
    contractTypeLabel,
  ].filter(Boolean).join(' ')
}

function normalizeOptionalId(value: ResourceId | '') {
  if (value === '' || value === null || value === undefined) {
    return null
  }
  return typeof value === 'string' && value.trim() === '' ? null : value
}

function addCurrentProjectContract(detail: SalesOrderDetailRecord) {
  if (!detail.projectId || !detail.contractId) {
    selectedProjectContractKey.value = ''
    return
  }
  const candidate: SalesOrderProjectContractCandidate = {
    projectId: detail.projectId,
    projectNo: detail.projectNo ?? '',
    projectName: detail.projectName ?? '',
    customerId: detail.customerId,
    customerName: detail.customerName,
    contractId: detail.contractId,
    contractNo: detail.contractNo ?? '',
    externalContractNo: detail.externalContractNo ?? null,
    contractName: detail.contractName ?? detail.contractNo ?? '',
    contractType: detail.contractType ?? 'MAIN',
  }
  if (!orderLinkCandidates.value.some((item) => projectContractKey(item) === projectContractKey(candidate))) {
    orderLinkCandidates.value = [candidate, ...orderLinkCandidates.value]
  }
  selectedProjectContractKey.value = projectContractKey(candidate)
}

async function loadReferences() {
  referenceLoading.value = true
  referenceError.value = ''
  try {
    const [customerPage, materialPage, warehousePage] = await Promise.all([
      masterDataApi.customers.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
      masterDataApi.materials.list({
        keyword: '',
        status: 'ENABLED',
        page: 1,
        pageSize: 200,
      }),
      masterDataApi.warehouses.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
    ])
    customers.value = pageItems(customerPage)
    materials.value = pageItems(materialPage)
    warehouses.value = pageItems(warehousePage)
  } catch (caught) {
    customers.value = []
    materials.value = []
    warehouses.value = []
    referenceError.value = salesErrorMessage(caught)
  } finally {
    referenceLoading.value = false
  }
}

async function loadRecord() {
  if (!route.params.id) {
    return
  }
  loading.value = true
  formError.value = ''
  try {
    const detail = await salesApi.orders.get(route.params.id as ResourceId)
    editingRecord.value = detail
    form.customerId = detail.customerId
    form.orderDate = detail.orderDate
    form.expectedShipDate = detail.expectedShipDate ?? ''
    form.remark = detail.remark ?? ''
    lines.value = detail.lines.map(salesOrderLineDraftFromRecord)
    addCurrentProjectContract(detail)
  } catch (caught) {
    formError.value = salesErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function loadOrderLinkCandidates(keyword = '') {
  const customerId = normalizeOptionalId(form.customerId)
  if (customerId === null) {
    orderLinkCandidates.value = []
    selectedProjectContractKey.value = ''
    orderLinkCandidateError.value = ''
    return
  }
  orderLinkCandidateLoading.value = true
  orderLinkCandidateError.value = ''
  try {
    const page = await salesProjectApi.listOrderLinkCandidates({
      customerId,
      keyword,
      page: 1,
      pageSize: 20,
    })
    const candidates = pageItems(page)
    const currentRecord = editingRecord.value
    if (currentRecord?.projectId && currentRecord.contractId) {
      orderLinkCandidates.value = candidates
      addCurrentProjectContract(currentRecord)
    } else {
      orderLinkCandidates.value = candidates
    }
  } catch (caught) {
    orderLinkCandidateError.value = salesErrorMessage(caught)
    if (editingRecord.value?.projectId && editingRecord.value.contractId) {
      addCurrentProjectContract(editingRecord.value)
    } else {
      orderLinkCandidates.value = []
    }
  } finally {
    orderLinkCandidateLoading.value = false
  }
}

function clearProjectContractSelection() {
  selectedProjectContractKey.value = ''
  orderLinkCandidateError.value = ''
}

function validateForm(): SalesOrderCreatePayload | null {
  const customerId = normalizeRequiredId(form.customerId)
  if (customerId === null || !form.orderDate.trim() || lines.value.length === 0) {
    formError.value = '请完整填写客户、订单日期和明细'
    lineErrors.value = {}
    return null
  }

  const nextLineErrors: Record<number, string> = {}
  const duplicateMaterials = new Set<string>()
  const payloadLines = []

  for (const line of lines.value) {
    const materialId = normalizeRequiredId(line.materialId)
    if (materialId === null) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行请选择物料`
      continue
    }
    const material = materials.value.find((item) => String(item.id) === String(materialId))
    if (!material || (material.materialType !== 'FINISHED_GOOD' && material.materialType !== 'SEMI_FINISHED')) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行本阶段仅支持成品和半成品作为销售订单明细物料`
      continue
    }
    const quantityResult = validateSalesQuantity(line.quantity)
    if (quantityResult.payloadValue === null) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行${quantityResult.message ?? '数量不正确'}`
      continue
    }
    const unitPriceResult = validateSalesUnitPrice(line.unitPrice)
    if (unitPriceResult.payloadValue === null) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行${unitPriceResult.message ?? '单价不正确'}`
      continue
    }
    const duplicateKey = String(materialId)
    if (duplicateMaterials.has(duplicateKey)) {
      formError.value = '同一销售订单内物料不能重复'
      lineErrors.value = {}
      return null
    }
    duplicateMaterials.add(duplicateKey)
    const unitId = normalizeRequiredId(line.unitId)
    const reservationWarehouseId = normalizeRequiredId(line.reservationWarehouseId)
    if (reservationWarehouseId === null) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行请选择预留仓库，销售订单确认会按该仓库预留现货库存`
      continue
    }
    payloadLines.push({
      lineNo: line.lineNo,
      materialId,
      ...(unitId !== null ? { unitId } : {}),
      reservationWarehouseId,
      quantity: quantityResult.payloadValue,
      unitPrice: unitPriceResult.payloadValue,
      ...(line.expectedShipDate.trim() ? { expectedShipDate: line.expectedShipDate.trim() } : {}),
      ...(line.remark.trim() ? { remark: line.remark.trim() } : {}),
    })
  }

  lineErrors.value = nextLineErrors
  if (Object.keys(nextLineErrors).length > 0) {
    formError.value = ''
    return null
  }

  const projectContract = selectedProjectContract.value
  formError.value = ''
  return {
    customerId,
    orderDate: form.orderDate.trim(),
    ...(form.expectedShipDate.trim() ? { expectedShipDate: form.expectedShipDate.trim() } : {}),
    ...(form.remark.trim() ? { remark: form.remark.trim() } : {}),
    ...(projectContract ? {
      projectId: projectContract.projectId,
      contractId: projectContract.contractId,
    } : {}),
    lines: payloadLines,
  }
}

async function saveOrder() {
  if (formSubmitting.value || orderLinkCandidateError.value) {
    return
  }
  const currentRecord = editingRecord.value
  if (isEdit.value && !currentRecord) {
    formError.value = '销售订单加载失败，不能保存'
    return
  }
  if (currentRecord && currentRecord.status !== 'DRAFT') {
    formError.value = '仅草稿销售订单可编辑'
    return
  }
  const payload = validateForm()
  if (!payload) {
    return
  }

  formSubmitting.value = true
  try {
    let result: SalesOrderDetailRecord
    if (isEdit.value) {
      if (!currentRecord) {
        formError.value = '销售订单加载失败，不能保存'
        return
      }
      const updatePayload: SalesOrderUpdatePayload = {
        ...payload,
        version: currentRecord.version,
      }
      result = await salesApi.orders.update(currentRecord.id, updatePayload)
    } else {
      result = await salesApi.orders.create(payload)
    }
    await router.push({
      name: 'sales-order-detail',
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
      name: 'sales-order-detail',
      params: { id: String(editingRecord.value.id) },
      query: queryWithReturnTo({}, routeReturnTo(route)),
    })
    return
  }
  void router.push({ name: 'sales-orders' })
}

onMounted(async () => {
  await loadReferences()
  await loadRecord()
  await loadOrderLinkCandidates()
})

watch(() => form.customerId, () => {
  if (!canEditForm.value) {
    return
  }
  void loadOrderLinkCandidates()
})
</script>

<template>
  <MasterDataTableView :title="pageTitle" description="维护销售订单草稿，确认后可基于订单创建销售出库。">
    <template #alerts>
      <el-alert v-if="referenceError" class="state-alert" type="error" :title="referenceError" :closable="false" />
      <el-alert v-if="formError" class="state-alert" type="error" :title="formError" :closable="false" />
      <el-alert
        v-if="editingRecord && !isDraftRecord"
        class="state-alert"
        type="warning"
        title="仅草稿销售订单可编辑"
        :closable="false"
      />
      <el-alert
        v-if="loading || referenceLoading"
        class="state-alert"
        type="info"
        title="销售订单表单加载中"
        :closable="false"
      />
      <el-alert
        v-if="orderLinkCandidateError"
        class="state-alert"
        type="error"
        :title="orderLinkCandidateError"
        :closable="false"
      />
    </template>

    <el-form label-position="top" class="sales-order-form">
      <div v-if="editingRecord" class="edit-status">
        <span>{{ editingRecord.orderNo }}</span>
        <SalesOrderStatusTag :status="editingRecord.status" />
      </div>
      <div class="sales-order-form-grid">
        <el-form-item label="客户">
          <el-select
            v-model="form.customerId"
            data-test="sales-order-customer-id"
            filterable
            placeholder="请选择启用客户"
            style="width: 100%"
            :disabled="!canEditForm"
          >
            <el-option
              v-for="customer in customers"
              :key="customer.id"
              :label="`${customer.code} ${customer.name}`"
              :value="customer.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="订单日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD"
            v-model="form.orderDate"
            name="sales-order-date"
            placeholder="选择日期"
            :disabled="!canEditForm"
          />
        </el-form-item>
        <el-form-item label="默认预计交付日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD"
            v-model="form.expectedShipDate"
            name="sales-order-expected-date"
            placeholder="选择日期"
            :disabled="!canEditForm"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" name="sales-order-remark" placeholder="可选" :disabled="!canEditForm" />
        </el-form-item>
      </div>
      <el-form-item label="销售明细">
        <SalesOrderLineEditor
          v-model:lines="lines"
          :materials="sellableMaterials"
          :warehouses="warehouses"
          :errors="lineErrors"
          :read-only="!canEditForm"
        />
      </el-form-item>
      <el-form-item label="项目合同归属">
        <el-select
          v-if="canEditForm"
          v-model="selectedProjectContractKey"
          clearable
          filterable
          remote
          :remote-method="loadOrderLinkCandidates"
          :loading="orderLinkCandidateLoading"
          placeholder="可选，项目和合同同选同清"
          style="width: 100%"
          @clear="clearProjectContractSelection"
        >
          <el-option
            v-for="candidate in orderLinkCandidates"
            :key="projectContractKey(candidate)"
            :label="projectContractLabel(candidate)"
            :value="projectContractKey(candidate)"
          />
        </el-select>
        <div v-else class="readonly-project-link">
          <span v-if="editingRecord?.projectId && editingRecord.contractId">
            {{ editingRecord.projectNo }} {{ editingRecord.projectName }} / {{ editingRecord.contractNo }}
            {{ editingRecord.contractName }} {{ editingRecord.contractType === 'SUPPLEMENT' ? '补充合同' : '主合同' }}
          </span>
          <span v-else>未关联项目</span>
        </div>
      </el-form-item>
    </el-form>

    <div class="form-footer">
      <el-button @click="cancel">取消</el-button>
      <el-button
        data-test="save-sales-order"
        type="primary"
        :loading="formSubmitting"
        :disabled="!canSubmit"
        @click="saveOrder"
      >
        保存
      </el-button>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.sales-order-form {
  padding: 14px;
}

.sales-order-form-grid {
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

.readonly-project-link {
  min-height: 32px;
  color: var(--qherp-text);
  line-height: 32px;
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 14px 14px;
  border-top: 1px solid var(--qherp-border);
}

@media (max-width: 760px) {
  .sales-order-form-grid {
    grid-template-columns: 1fr;
  }

  .form-footer {
    align-items: stretch;
    flex-direction: column-reverse;
  }
}
</style>
