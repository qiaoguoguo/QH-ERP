<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createIdempotencyKey } from '../../../shared/api/documentPlatformApi'
import {
  productionOutsourcingApi,
  type OutsourcingDocumentDetailRecord,
  type OutsourcingMaterialIssueLineRecord,
  type OutsourcingOrderDetailRecord,
} from '../../../shared/api/productionOutsourcingApi'
import type { ProductionOwnershipType, ResourceId } from '../../../shared/api/projectProductionApi'
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
  warehouseId: '' as ResourceId | '',
  remark: '',
  orderMaterialId: '' as ResourceId | '',
  quantity: '',
  ownershipType: 'PROJECT' as ProductionOwnershipType,
  projectId: '',
  costLayerId: '',
  batchNo: '',
  batchQuantity: '',
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
    return authStore.hasPermission('production:outsourcing-issue:update')
      && Boolean(documentRecord.value?.allowedActions?.includes('UPDATE'))
  }
  return authStore.hasPermission('production:outsourcing-issue:create')
    && Boolean(order.value.allowedActions?.includes('ISSUE'))
})
const canPostDocument = computed(() => authStore.hasPermission('production:outsourcing-issue:post')
  && Boolean(documentRecord.value?.allowedActions?.includes('POST')))
const canCancelDocument = computed(() => authStore.hasPermission('production:outsourcing-issue:cancel')
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

function lineNoForMaterial(id: ResourceId | '') {
  const material = order.value?.materials.find((item) => String(item.id) === String(id))
  return material?.lineNo ?? 1
}

async function loadPage() {
  loading.value = true
  error.value = ''
  try {
    order.value = await productionOutsourcingApi.orders.get(route.params.id as ResourceId)
    form.businessDate = new Date().toISOString().slice(0, 10)
    form.warehouseId = order.value.issueWarehouseId ?? ''
    form.ownershipType = order.value.ownershipType
    form.projectId = order.value.projectId === null || order.value.projectId === undefined ? '' : String(order.value.projectId)
    if (order.value.materials[0]) {
      form.orderMaterialId = order.value.materials[0].id
      form.quantity = order.value.materials[0].requiredQuantity
    }
    if (isEdit.value) {
      documentRecord.value = await productionOutsourcingApi.materialIssues.get(
        order.value.id,
        normalizeId(documentId.value as ResourceId) as ResourceId,
      )
      form.businessDate = documentRecord.value.businessDate
      form.warehouseId = documentRecord.value.warehouseId ?? order.value.issueWarehouseId ?? ''
      form.remark = documentRecord.value.remark ?? ''
      const line = documentRecord.value.lines[0] as OutsourcingMaterialIssueLineRecord | undefined
      if (line) {
        form.orderMaterialId = line.orderMaterialId
        form.quantity = line.quantity
        form.ownershipType = line.ownershipType
        form.projectId = line.projectId === null || line.projectId === undefined ? '' : String(line.projectId)
        form.costLayerId = line.costLayerId === null || line.costLayerId === undefined ? '' : String(line.costLayerId)
        form.batchNo = line.trackingAllocations?.[0]?.batchNo ?? ''
        form.batchQuantity = line.trackingAllocations?.[0]?.quantity ?? line.quantity
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
  const quantity = validateProductionQuantity(form.quantity)
  if (quantity.message || quantity.payloadValue === null) {
    throw new Error(quantity.message ?? '数量不能为空')
  }
  const warehouseId = normalizeId(form.warehouseId)
  const orderMaterialId = normalizeId(form.orderMaterialId)
  if (!warehouseId || !orderMaterialId) {
    throw new Error('发料仓库和材料行不能为空')
  }
  const trackingAllocations = form.batchNo.trim()
    ? [{
      batchNo: form.batchNo.trim(),
      quantity: validateProductionQuantity(form.batchQuantity || form.quantity).payloadValue ?? '',
    }]
    : []
  if (trackingAllocations.length > 0) {
    const trackedQuantity = validateProductionQuantity(trackingAllocations[0].quantity)
    if (trackedQuantity.message || trackedQuantity.payloadValue === null) {
      throw new Error(trackedQuantity.message ?? '批次数量不能为空')
    }
    if (Number(trackedQuantity.payloadValue) !== Number(quantity.payloadValue)) {
      throw new Error('批次数量合计必须等于发料数量')
    }
    trackingAllocations[0].quantity = trackedQuantity.payloadValue
  }
  return {
    version: documentRecord.value?.version ?? order.value.version,
    businessDate: form.businessDate,
    warehouseId,
    remark: form.remark,
    idempotencyKey: createIdempotencyKey('production-outsourcing-issue-save'),
    lines: [{
      orderMaterialId,
      lineNo: lineNoForMaterial(form.orderMaterialId),
      quantity: quantity.payloadValue,
      ownershipType: form.ownershipType,
      projectId: form.ownershipType === 'PROJECT' ? normalizeId(form.projectId) : null,
      ...(costVisible.value ? { costLayerId: normalizeId(form.costLayerId) } : {}),
      trackingAllocations,
    }],
  }
}

async function saveDocument() {
  if (!order.value || saving.value) {
    return
  }
  if (!canSaveDocument.value) {
    error.value = isEdit.value ? '没有外协发料更新权限' : '没有外协发料写入权限'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const payload = buildPayload()
    documentRecord.value = isEdit.value
      ? await productionOutsourcingApi.materialIssues.update(
        order.value.id,
        normalizeId(documentId.value as ResourceId) as ResourceId,
        payload,
      )
      : await productionOutsourcingApi.materialIssues.create(order.value.id, payload)
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
  if (!(await confirmAction(`确认${label}外协发料单？`))) {
    return
  }
  error.value = ''
  try {
    const payload = {
      version: Number(documentRecord.value.version ?? 0),
      idempotencyKey: createIdempotencyKey(`production-outsourcing-issue-${action}`),
    }
    documentRecord.value = action === 'post'
      ? await productionOutsourcingApi.materialIssues.post(order.value.id, documentRecord.value.id, payload)
      : await productionOutsourcingApi.materialIssues.cancel(order.value.id, documentRecord.value.id, payload)
  } catch (caught) {
    error.value = productionErrorMessage(caught)
  }
}

onMounted(loadPage)
</script>

<template>
  <MasterDataTableView title="外协发料" description="按同项目或公共库存来源发料，记录成本层、批次/序列和来源追溯。">
    <template #actions>
      <el-button @click="router.push({ name: 'production-outsourcing-order-detail', params: { id: route.params.id } })">返回订单</el-button>
      <el-button v-if="canSaveDocument" data-test="save-outsourcing-issue" type="primary" :loading="saving" @click="saveDocument">保存草稿</el-button>
      <el-button
        v-if="documentRecord"
        data-test="post-outsourcing-issue"
        :disabled="!canPostDocument"
        @click="runDocumentAction('post')"
      >
        过账
      </el-button>
      <el-button
        v-if="documentRecord"
        data-test="cancel-outsourcing-issue"
        type="danger"
        :disabled="!canCancelDocument"
        @click="runDocumentAction('cancel')"
      >
        取消
      </el-button>
    </template>
    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" type="info" title="外协发料加载中" :closable="false" />
      <el-alert v-if="order && !canSaveDocument && !isEdit" type="warning" title="没有外协发料写入权限" :closable="false" />
      <el-alert v-if="order && !costVisible" type="warning" :title="costRestrictedReason" :closable="false" />
    </template>

    <section class="section-block">
      <h2>{{ order?.orderNo || '外协订单' }} 发料草稿</h2>
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="业务日期">
            <el-input v-model="form.businessDate" name="outsourcing-issue-business-date" />
          </el-form-item>
          <el-form-item label="发料仓库">
            <el-select v-model="form.warehouseId" data-test="outsourcing-issue-warehouse-id">
              <el-option v-if="order?.issueWarehouseId" :label="order.issueWarehouseName || String(order.issueWarehouseId)" :value="order.issueWarehouseId" />
            </el-select>
          </el-form-item>
          <el-form-item label="材料行">
            <el-select v-model="form.orderMaterialId" data-test="outsourcing-issue-line-material">
              <el-option
                v-for="material in order?.materials ?? []"
                :key="material.id"
                :label="`${material.materialCode} ${material.materialName}`"
                :value="material.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="发料数量">
            <el-input v-model="form.quantity" name="outsourcing-issue-line-quantity" />
          </el-form-item>
          <el-form-item label="库存来源">
            <el-select v-model="form.ownershipType" data-test="outsourcing-issue-line-ownership">
              <el-option label="项目库存" value="PROJECT" />
              <el-option label="公共库存" value="PUBLIC" />
            </el-select>
          </el-form-item>
          <el-form-item label="项目">
            <el-input v-model="form.projectId" name="outsourcing-issue-line-project" :disabled="form.ownershipType === 'PUBLIC'" />
          </el-form-item>
          <el-form-item v-if="costVisible" label="成本层">
            <el-input v-model="form.costLayerId" name="outsourcing-issue-line-cost-layer" />
          </el-form-item>
          <el-form-item label="批次">
            <el-input v-model="form.batchNo" name="outsourcing-issue-line-batch" />
          </el-form-item>
          <el-form-item label="批次数量">
            <el-input v-model="form.batchQuantity" name="outsourcing-issue-line-batch-quantity" />
          </el-form-item>
        </div>
      </el-form>
    </section>
  </MasterDataTableView>
</template>
