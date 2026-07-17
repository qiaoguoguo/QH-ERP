<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createIdempotencyKey } from '../../../shared/api/documentPlatformApi'
import {
  productionOutsourcingApi,
  type OutsourcingDocumentDetailRecord,
  type OutsourcingOrderDetailRecord,
  type OutsourcingReceiptLineRecord,
} from '../../../shared/api/productionOutsourcingApi'
import type { ResourceId } from '../../../shared/api/projectProductionApi'
import { confirmAction } from '../../../shared/ui/confirmDialog'
import { useAuthStore } from '../../../stores/authStore'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { productionErrorMessage, validateProductionQuantity } from '../productionPageHelpers'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const order = ref<OutsourcingOrderDetailRecord | null>(null)
const documentRecord = ref<OutsourcingDocumentDetailRecord | null>(null)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const form = reactive({
  businessDate: '',
  receiptWarehouseId: '' as ResourceId | '',
  acceptedQuantity: '',
  rejectedQuantity: '0.000000',
  provisionalUnitCost: '',
  serialNo: '',
  serialQuantity: '1.000000',
  remark: '',
})

const documentId = computed(() => route.query.documentId as ResourceId | undefined)
const isEdit = computed(() => documentId.value !== undefined && documentId.value !== '')
const costVisible = computed(() => order.value?.costVisible !== false)
const costRestrictedReason = computed(() => order.value?.costRestrictedReason || '无库存估值权限')
const canSaveDocument = computed(() => {
  if (!order.value) {
    return false
  }
  if (isEdit.value) {
    return authStore.hasPermission('production:outsourcing-receipt:update')
      && Boolean(documentRecord.value?.allowedActions?.includes('UPDATE'))
  }
  return authStore.hasPermission('production:outsourcing-receipt:create')
    && Boolean(order.value.allowedActions?.includes('RECEIPT'))
})
const canPostDocument = computed(() => authStore.hasPermission('production:outsourcing-receipt:post')
  && Boolean(documentRecord.value?.allowedActions?.includes('POST')))
const canCancelDocument = computed(() => authStore.hasPermission('production:outsourcing-receipt:cancel')
  && Boolean(documentRecord.value?.allowedActions?.includes('CANCEL')))

function normalizeId(value: ResourceId | ''): ResourceId | null {
  if (value === '' || value === null || value === undefined) {
    return null
  }
  if (typeof value === 'number') {
    return value
  }
  const trimmed = String(value).trim()
  if (!trimmed) {
    return null
  }
  return /^\d+$/.test(trimmed) ? Number(trimmed) : trimmed
}

async function loadPage() {
  loading.value = true
  error.value = ''
  try {
    order.value = await productionOutsourcingApi.orders.get(route.params.id as ResourceId)
    form.businessDate = new Date().toISOString().slice(0, 10)
    form.receiptWarehouseId = order.value.receiptWarehouseId ?? ''
    form.acceptedQuantity = order.value.plannedQuantity
    if (isEdit.value) {
      documentRecord.value = await productionOutsourcingApi.receipts.get(
        order.value.id,
        normalizeId(documentId.value as ResourceId) as ResourceId,
      )
      form.businessDate = documentRecord.value.businessDate
      form.receiptWarehouseId = documentRecord.value.receiptWarehouseId ?? order.value.receiptWarehouseId ?? ''
      form.remark = documentRecord.value.remark ?? ''
      const line = documentRecord.value.lines[0] as OutsourcingReceiptLineRecord | undefined
      if (line) {
        form.acceptedQuantity = line.acceptedQuantity
        form.rejectedQuantity = line.rejectedQuantity
        form.provisionalUnitCost = line.provisionalUnitCost ?? ''
        form.serialNo = line.trackingAllocations?.[0]?.serialNo ?? ''
        form.serialQuantity = line.trackingAllocations?.[0]?.quantity ?? '1.000000'
      }
    }
  } catch (caught) {
    error.value = productionErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function buildPayload() {
  if (!order.value) {
    throw new Error('外协订单未加载')
  }
  const accepted = validateProductionQuantity(form.acceptedQuantity)
  const rejected = validateProductionQuantity(form.rejectedQuantity, { allowZero: true })
  if (accepted.message || accepted.payloadValue === null) {
    throw new Error(accepted.message ?? '合格数量不能为空')
  }
  if (rejected.message || rejected.payloadValue === null) {
    throw new Error(rejected.message ?? '不合格数量不能为空')
  }
  const receiptWarehouseId = normalizeId(form.receiptWarehouseId)
  if (!receiptWarehouseId) {
    throw new Error('收货仓库不能为空')
  }
  const trackingAllocations = form.serialNo.trim()
    ? [{ serialNo: form.serialNo.trim(), quantity: form.serialQuantity || '1.000000' }]
    : []
  if (trackingAllocations.length > 0) {
    const serialQuantity = validateProductionQuantity(trackingAllocations[0].quantity)
    if (serialQuantity.message || serialQuantity.payloadValue === null) {
      throw new Error(serialQuantity.message ?? '序列数量不能为空')
    }
    if (Number(serialQuantity.payloadValue) !== 1) {
      throw new Error('序列数量必须为 1')
    }
    trackingAllocations[0].quantity = serialQuantity.payloadValue
  }
  return {
    version: documentRecord.value?.version ?? order.value.version,
    businessDate: form.businessDate,
    receiptWarehouseId,
    remark: form.remark,
    idempotencyKey: createIdempotencyKey('production-outsourcing-receipt-save'),
    lines: [{
      lineNo: 1,
      acceptedQuantity: accepted.payloadValue,
      rejectedQuantity: rejected.payloadValue,
      ...(costVisible.value ? { provisionalUnitCost: form.provisionalUnitCost || undefined } : {}),
      trackingAllocations,
    }],
  }
}

async function saveDocument() {
  if (!order.value || saving.value) {
    return
  }
  if (!canSaveDocument.value) {
    error.value = isEdit.value ? '没有外协收货更新权限' : '没有外协收货写入权限'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const payload = buildPayload()
    documentRecord.value = isEdit.value
      ? await productionOutsourcingApi.receipts.update(
        order.value.id,
        normalizeId(documentId.value as ResourceId) as ResourceId,
        payload,
      )
      : await productionOutsourcingApi.receipts.create(order.value.id, payload)
  } catch (caught) {
    error.value = productionErrorMessage(caught)
  } finally {
    saving.value = false
  }
}

async function runDocumentAction(action: 'post' | 'cancel') {
  if (!order.value || !documentRecord.value) {
    return
  }
  const label = action === 'post' ? '过账' : '取消'
  if (!(await confirmAction(`确认${label}外协收货单？`))) {
    return
  }
  error.value = ''
  try {
    const payload = {
      version: Number(documentRecord.value.version ?? 0),
      idempotencyKey: createIdempotencyKey(`production-outsourcing-receipt-${action}`),
    }
    documentRecord.value = action === 'post'
      ? await productionOutsourcingApi.receipts.post(order.value.id, documentRecord.value.id, payload)
      : await productionOutsourcingApi.receipts.cancel(order.value.id, documentRecord.value.id, payload)
  } catch (caught) {
    error.value = productionErrorMessage(caught)
  }
}

onMounted(loadPage)
</script>

<template>
  <MasterDataTableView title="外协收货" description="记录外协合格/不合格数量、暂估单价、批次/序列和项目库存入库事实。">
    <template #actions>
      <el-button @click="router.push({ name: 'production-outsourcing-order-detail', params: { id: route.params.id } })">返回订单</el-button>
      <el-button v-if="canSaveDocument" data-test="save-outsourcing-receipt" type="primary" :loading="saving" @click="saveDocument">保存草稿</el-button>
      <el-button
        v-if="documentRecord"
        data-test="post-outsourcing-receipt"
        :disabled="!canPostDocument"
        @click="runDocumentAction('post')"
      >
        过账
      </el-button>
      <el-button
        v-if="documentRecord"
        data-test="cancel-outsourcing-receipt"
        type="danger"
        :disabled="!canCancelDocument"
        @click="runDocumentAction('cancel')"
      >
        取消
      </el-button>
    </template>
    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" type="info" title="外协收货加载中" :closable="false" />
      <el-alert v-if="order && !canSaveDocument && !isEdit" type="warning" title="没有外协收货写入权限" :closable="false" />
      <el-alert v-if="order && !costVisible" type="warning" :title="costRestrictedReason" :closable="false" />
    </template>

    <section class="section-block">
      <h2>{{ order?.orderNo || '外协订单' }} 收货草稿</h2>
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="业务日期">
            <el-input v-model="form.businessDate" name="outsourcing-receipt-business-date" />
          </el-form-item>
          <el-form-item label="收货仓库">
            <el-select v-model="form.receiptWarehouseId" data-test="outsourcing-receipt-warehouse-id">
              <el-option v-if="order?.receiptWarehouseId" :label="order.receiptWarehouseName || String(order.receiptWarehouseId)" :value="order.receiptWarehouseId" />
            </el-select>
          </el-form-item>
          <el-form-item label="合格数量">
            <el-input v-model="form.acceptedQuantity" name="outsourcing-receipt-accepted-quantity" />
          </el-form-item>
          <el-form-item label="不合格数量">
            <el-input v-model="form.rejectedQuantity" name="outsourcing-receipt-rejected-quantity" />
          </el-form-item>
          <el-form-item v-if="costVisible" label="暂估单价">
            <el-input v-model="form.provisionalUnitCost" name="outsourcing-receipt-provisional-unit-cost" />
          </el-form-item>
          <el-form-item label="序列号">
            <el-input v-model="form.serialNo" name="outsourcing-receipt-serial" />
          </el-form-item>
          <el-form-item label="序列数量">
            <el-input v-model="form.serialQuantity" name="outsourcing-receipt-serial-quantity" />
          </el-form-item>
        </div>
      </el-form>
    </section>
  </MasterDataTableView>
</template>
