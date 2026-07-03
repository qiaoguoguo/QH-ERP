<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  inventoryApi,
  type InventoryDocumentDetailRecord,
  type InventoryDocumentPayload,
  type InventoryDocumentType,
  type ResourceId,
} from '../../shared/api/inventoryApi'
import { masterDataApi, type MaterialRecord, type UnitRecord, type WarehouseRecord } from '../../shared/api/masterDataApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { errorMessage, pageItems } from '../system/shared/pageHelpers'
import InventoryDocumentLineEditor from './InventoryDocumentLineEditor.vue'
import {
  documentTypeLabel,
  newInventoryLine,
  validateInventoryQuantity,
  type InventoryLineDraft,
} from './inventoryPageHelpers'

const route = useRoute()
const router = useRouter()
const warehouses = ref<WarehouseRecord[]>([])
const materials = ref<MaterialRecord[]>([])
const units = ref<UnitRecord[]>([])
const loading = ref(false)
const referenceLoading = ref(true)
const referenceError = ref('')
const formError = ref('')
const formSubmitting = ref(false)
const lineErrors = ref<Record<number, string>>({})
const editingRecord = ref<InventoryDocumentDetailRecord | null>(null)
const form = reactive({
  documentType: routeDocumentType(),
  businessDate: '',
  reason: '',
  remark: '',
})
const lines = ref<InventoryLineDraft[]>([newInventoryLine()])

const isEdit = computed(() => Boolean(route.params.id))
const pageTitle = computed(() => (isEdit.value ? `编辑${documentTypeLabel(form.documentType)}` : `新增${documentTypeLabel(form.documentType)}`))
const isPostedRecord = computed(() => editingRecord.value?.status === 'POSTED')

function routeDocumentType(): InventoryDocumentType {
  return route.query.type === 'ADJUSTMENT' ? 'ADJUSTMENT' : 'OPENING'
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

async function loadReferences() {
  referenceLoading.value = true
  referenceError.value = ''
  try {
    const [warehousePage, materialPage, unitPage] = await Promise.all([
      masterDataApi.warehouses.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 100 }),
      masterDataApi.materials.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 100 }),
      masterDataApi.units.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 100 }),
    ])
    warehouses.value = pageItems(warehousePage)
    materials.value = pageItems(materialPage)
    units.value = pageItems(unitPage)
  } catch (caught) {
    warehouses.value = []
    materials.value = []
    units.value = []
    referenceError.value = errorMessage(caught)
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
    const detail = await inventoryApi.documents.get(route.params.id as ResourceId)
    editingRecord.value = detail
    form.documentType = detail.documentType
    form.businessDate = detail.businessDate
    form.reason = detail.reason
    form.remark = detail.remark ?? ''
    lines.value = detail.lines.map((line) => ({
      lineNo: line.lineNo,
      warehouseId: line.warehouseId,
      materialId: line.materialId,
      unitId: line.unitId,
      unitName: line.unitName,
      adjustmentDirection: line.adjustmentDirection ?? '',
      quantity: String(line.quantity),
      remark: line.remark ?? '',
    }))
  } catch (caught) {
    formError.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

function validateForm(): InventoryDocumentPayload | null {
  if (!form.businessDate.trim() || !form.reason.trim()) {
    formError.value = '请填写业务日期和原因'
    lineErrors.value = {}
    return null
  }
  if (lines.value.length === 0) {
    formError.value = '库存单据明细不能为空'
    lineErrors.value = {}
    return null
  }

  const nextLineErrors: Record<number, string> = {}
  const duplicateKeys = new Set<string>()
  const payloadLines = []

  for (const line of lines.value) {
    const warehouseId = normalizeRequiredId(line.warehouseId)
    if (warehouseId === null) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行请选择仓库`
      continue
    }
    const materialId = normalizeRequiredId(line.materialId)
    if (materialId === null) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行请选择物料`
      continue
    }
    const quantityResult = validateInventoryQuantity(line.quantity)
    if (quantityResult.payloadValue === null) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行${quantityResult.message ?? '数量不正确'}`
      continue
    }
    if (form.documentType === 'ADJUSTMENT' && !line.adjustmentDirection) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行请选择调整方向`
      continue
    }
    const duplicateKey = `${warehouseId}:${materialId}`
    if (duplicateKeys.has(duplicateKey)) {
      formError.value = '同一单据内仓库和物料不能重复'
      lineErrors.value = {}
      return null
    }
    duplicateKeys.add(duplicateKey)
    const unitId = normalizeOptionalId(line.unitId)
    payloadLines.push({
      lineNo: line.lineNo,
      warehouseId,
      materialId,
      ...(unitId !== undefined ? { unitId } : {}),
      quantity: quantityResult.payloadValue,
      ...(form.documentType === 'ADJUSTMENT' && line.adjustmentDirection
        ? { adjustmentDirection: line.adjustmentDirection }
        : {}),
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
    documentType: form.documentType,
    businessDate: form.businessDate.trim(),
    reason: form.reason.trim(),
    ...(form.remark.trim() ? { remark: form.remark.trim() } : {}),
    lines: payloadLines,
  }
}

async function saveDocument() {
  if (formSubmitting.value) {
    return
  }
  if (editingRecord.value?.status === 'POSTED') {
    formError.value = '已过账，不可编辑'
    return
  }
  const payload = validateForm()
  if (!payload) {
    return
  }

  formSubmitting.value = true
  try {
    const result = editingRecord.value
      ? await inventoryApi.documents.update(editingRecord.value.id, payload)
      : await inventoryApi.documents.create(payload)
    await router.push({ name: 'inventory-document-detail', params: { id: String(result.id) } })
  } catch (caught) {
    formError.value = errorMessage(caught)
  } finally {
    formSubmitting.value = false
  }
}

function cancel() {
  if (editingRecord.value) {
    void router.push({ name: 'inventory-document-detail', params: { id: String(editingRecord.value.id) } })
    return
  }
  void router.push({ name: 'inventory-documents' })
}

onMounted(() => {
  void loadReferences()
  void loadRecord()
})
</script>

<template>
  <MasterDataTableView :title="pageTitle" description="保存库存期初或库存调整草稿，过账后才影响库存余额。">
    <template #alerts>
      <el-alert v-if="referenceError" class="state-alert" type="error" :title="referenceError" :closable="false" />
      <el-alert v-if="formError" class="state-alert" type="error" :title="formError" :closable="false" />
      <el-alert
        v-if="isPostedRecord"
        class="state-alert"
        type="warning"
        title="已过账，不可编辑"
        :closable="false"
      />
      <el-alert
        v-if="loading || referenceLoading"
        class="state-alert"
        type="info"
        title="库存单据表单加载中"
        :closable="false"
      />
    </template>

    <el-form label-position="top" class="inventory-document-form">
      <div class="inventory-form-grid">
        <el-form-item label="单据类型">
          <el-input :model-value="documentTypeLabel(form.documentType)" name="inventory-document-type" disabled />
        </el-form-item>
        <el-form-item label="业务日期">
          <el-input
            v-model="form.businessDate"
            name="inventory-document-business-date"
            placeholder="YYYY-MM-DD"
            :disabled="isPostedRecord"
          />
        </el-form-item>
        <el-form-item label="原因">
          <el-input
            v-model="form.reason"
            name="inventory-document-reason"
            placeholder="请输入库存原因"
            :disabled="isPostedRecord"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" name="inventory-document-remark" placeholder="可选" :disabled="isPostedRecord" />
        </el-form-item>
      </div>
      <el-form-item label="库存明细">
        <InventoryDocumentLineEditor
          v-model:lines="lines"
          :document-type="form.documentType"
          :warehouses="warehouses"
          :materials="materials"
          :errors="lineErrors"
          :read-only="isPostedRecord"
        />
      </el-form-item>
    </el-form>

    <div class="form-footer">
      <el-button @click="cancel">取消</el-button>
      <el-button
        data-test="save-inventory-document"
        type="primary"
        :loading="formSubmitting"
        :disabled="formSubmitting || isPostedRecord"
        @click="saveDocument"
      >
        保存
      </el-button>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.inventory-document-form {
  padding: 14px;
}

.inventory-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 14px 14px;
  border-top: 1px solid var(--qherp-border);
}

@media (max-width: 760px) {
  .inventory-form-grid {
    grid-template-columns: 1fr;
  }

  .form-footer {
    align-items: stretch;
    flex-direction: column-reverse;
  }
}
</style>
