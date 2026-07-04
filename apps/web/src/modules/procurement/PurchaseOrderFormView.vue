<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { masterDataApi, type MaterialRecord, type PartnerRecord } from '../../shared/api/masterDataApi'
import {
  procurementApi,
  type PurchaseOrderDetailRecord,
  type PurchaseOrderPayload,
  type ResourceId,
} from '../../shared/api/procurementApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import PurchaseOrderLineEditor from './PurchaseOrderLineEditor.vue'
import PurchaseOrderStatusTag from './PurchaseOrderStatusTag.vue'
import {
  type PurchaseOrderLineDraft,
  newPurchaseOrderLine,
  normalizeRequiredId,
  procurementErrorMessage,
  validatePurchaseQuantity,
  validatePurchaseUnitPrice,
} from './procurementPageHelpers'

const route = useRoute()
const router = useRouter()
const suppliers = ref<PartnerRecord[]>([])
const materials = ref<MaterialRecord[]>([])
const editingRecord = ref<PurchaseOrderDetailRecord | null>(null)
const referenceLoading = ref(true)
const loading = ref(false)
const referenceError = ref('')
const formError = ref('')
const formSubmitting = ref(false)
const lineErrors = ref<Record<number, string>>({})
const form = reactive({
  supplierId: '' as ResourceId | '',
  orderDate: '',
  expectedArrivalDate: '',
  remark: '',
})
const lines = ref<PurchaseOrderLineDraft[]>([newPurchaseOrderLine()])

const isEdit = computed(() => Boolean(route.params.id))
const isDraftRecord = computed(() => !editingRecord.value || editingRecord.value.status === 'DRAFT')
const canEditForm = computed(() => isDraftRecord.value && (!isEdit.value || Boolean(editingRecord.value)))
const canSubmit = computed(() => !formSubmitting.value && canEditForm.value)
const pageTitle = computed(() => (isEdit.value ? '编辑采购订单' : '新建采购订单'))

async function loadReferences() {
  referenceLoading.value = true
  referenceError.value = ''
  try {
    const [supplierPage, materialPage] = await Promise.all([
      masterDataApi.suppliers.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
      masterDataApi.materials.list({
        keyword: '',
        status: 'ENABLED',
        sourceType: 'PURCHASED',
        page: 1,
        pageSize: 200,
      }),
    ])
    suppliers.value = pageItems(supplierPage)
    materials.value = pageItems(materialPage)
  } catch (caught) {
    suppliers.value = []
    materials.value = []
    referenceError.value = procurementErrorMessage(caught)
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
    const detail = await procurementApi.orders.get(route.params.id as ResourceId)
    editingRecord.value = detail
    form.supplierId = detail.supplierId
    form.orderDate = detail.orderDate
    form.expectedArrivalDate = detail.expectedArrivalDate ?? ''
    form.remark = detail.remark ?? ''
    lines.value = detail.lines.map((line) => ({
      lineNo: line.lineNo,
      materialId: line.materialId,
      unitId: line.unitId,
      unitName: line.unitName,
      quantity: String(line.quantity),
      unitPrice: String(line.unitPrice),
      expectedArrivalDate: line.expectedArrivalDate ?? '',
      remark: line.remark ?? '',
    }))
  } catch (caught) {
    formError.value = procurementErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function validateForm(): PurchaseOrderPayload | null {
  const supplierId = normalizeRequiredId(form.supplierId)
  if (supplierId === null || !form.orderDate.trim() || lines.value.length === 0) {
    formError.value = '请完整填写供应商、订单日期和明细'
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
    const quantityResult = validatePurchaseQuantity(line.quantity)
    if (quantityResult.payloadValue === null) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行${quantityResult.message ?? '数量不正确'}`
      continue
    }
    const unitPriceResult = validatePurchaseUnitPrice(line.unitPrice)
    if (unitPriceResult.payloadValue === null) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行${unitPriceResult.message ?? '单价不正确'}`
      continue
    }
    const duplicateKey = String(materialId)
    if (duplicateMaterials.has(duplicateKey)) {
      formError.value = '同一采购订单内物料不能重复'
      lineErrors.value = {}
      return null
    }
    duplicateMaterials.add(duplicateKey)
    const unitId = normalizeRequiredId(line.unitId)
    payloadLines.push({
      lineNo: line.lineNo,
      materialId,
      ...(unitId !== null ? { unitId } : {}),
      quantity: quantityResult.payloadValue,
      unitPrice: unitPriceResult.payloadValue,
      ...(line.expectedArrivalDate.trim() ? { expectedArrivalDate: line.expectedArrivalDate.trim() } : {}),
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
    supplierId,
    orderDate: form.orderDate.trim(),
    ...(form.expectedArrivalDate.trim() ? { expectedArrivalDate: form.expectedArrivalDate.trim() } : {}),
    ...(form.remark.trim() ? { remark: form.remark.trim() } : {}),
    lines: payloadLines,
  }
}

async function saveOrder() {
  if (formSubmitting.value) {
    return
  }
  const currentRecord = editingRecord.value
  if (isEdit.value && !currentRecord) {
    formError.value = '采购订单加载失败，不能保存'
    return
  }
  if (currentRecord && currentRecord.status !== 'DRAFT') {
    formError.value = '仅草稿采购订单可编辑'
    return
  }
  const payload = validateForm()
  if (!payload) {
    return
  }

  formSubmitting.value = true
  try {
    let result: PurchaseOrderDetailRecord
    if (isEdit.value) {
      if (!currentRecord) {
        formError.value = '采购订单加载失败，不能保存'
        return
      }
      result = await procurementApi.orders.update(currentRecord.id, payload)
    } else {
      result = await procurementApi.orders.create(payload)
    }
    await router.push({ name: 'procurement-order-detail', params: { id: String(result.id) } })
  } catch (caught) {
    formError.value = procurementErrorMessage(caught)
  } finally {
    formSubmitting.value = false
  }
}

function cancel() {
  if (editingRecord.value) {
    void router.push({ name: 'procurement-order-detail', params: { id: String(editingRecord.value.id) } })
    return
  }
  void router.push({ name: 'procurement-orders' })
}

onMounted(async () => {
  await loadReferences()
  await loadRecord()
})
</script>

<template>
  <MasterDataTableView :title="pageTitle" description="维护采购订单草稿，确认后可基于订单创建采购入库。">
    <template #alerts>
      <el-alert v-if="referenceError" class="state-alert" type="error" :title="referenceError" :closable="false" />
      <el-alert v-if="formError" class="state-alert" type="error" :title="formError" :closable="false" />
      <el-alert
        v-if="editingRecord && !isDraftRecord"
        class="state-alert"
        type="warning"
        title="仅草稿采购订单可编辑"
        :closable="false"
      />
      <el-alert
        v-if="loading || referenceLoading"
        class="state-alert"
        type="info"
        title="采购订单表单加载中"
        :closable="false"
      />
    </template>

    <el-form label-position="top" class="purchase-order-form">
      <div v-if="editingRecord" class="edit-status">
        <span>{{ editingRecord.orderNo }}</span>
        <PurchaseOrderStatusTag :status="editingRecord.status" />
      </div>
      <div class="purchase-order-form-grid">
        <el-form-item label="供应商">
          <el-select
            v-model="form.supplierId"
            data-test="purchase-order-supplier-id"
            filterable
            placeholder="请选择启用供应商"
            style="width: 100%"
            :disabled="!canEditForm"
          >
            <el-option
              v-for="supplier in suppliers"
              :key="supplier.id"
              :label="`${supplier.code} ${supplier.name}`"
              :value="supplier.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="订单日期">
          <el-input
            v-model="form.orderDate"
            name="purchase-order-date"
            placeholder="YYYY-MM-DD"
            :disabled="!canEditForm"
          />
        </el-form-item>
        <el-form-item label="默认预计到货日期">
          <el-input
            v-model="form.expectedArrivalDate"
            name="purchase-order-expected-date"
            placeholder="YYYY-MM-DD"
            :disabled="!canEditForm"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" name="purchase-order-remark" placeholder="可选" :disabled="!canEditForm" />
        </el-form-item>
      </div>
      <el-form-item label="采购明细">
        <PurchaseOrderLineEditor
          v-model:lines="lines"
          :materials="materials"
          :errors="lineErrors"
          :read-only="!canEditForm"
        />
      </el-form-item>
    </el-form>

    <div class="form-footer">
      <el-button @click="cancel">取消</el-button>
      <el-button
        data-test="save-purchase-order"
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
.purchase-order-form {
  padding: 14px;
}

.purchase-order-form-grid {
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

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 14px 14px;
  border-top: 1px solid var(--qherp-border);
}

@media (max-width: 760px) {
  .purchase-order-form-grid {
    grid-template-columns: 1fr;
  }

  .form-footer {
    align-items: stretch;
    flex-direction: column-reverse;
  }
}
</style>
