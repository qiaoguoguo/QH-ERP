<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  qualityInventoryStatusApi,
  type QualityInspectionDetail,
  type QualityInspectionProcessPayload,
  type ResourceId,
} from '../../shared/api/qualityInventoryStatusApi'
import type { InventoryTrackingAllocationPayload } from '../../shared/api/inventoryApi'
import { errorMessage } from '../system/shared/pageHelpers'
import { formatQuantity } from '../inventory/inventoryPageHelpers'
import TrackingAllocationReadonlyTable from '../inventory/tracking/TrackingAllocationReadonlyTable.vue'
import { inferTrackingMethodFromAllocations } from '../inventory/tracking/trackingPayloadHelpers'
import QualityStatusTag from './QualityStatusTag.vue'

const props = defineProps<{
  modelValue: boolean
  inspectionId?: ResourceId | null
}>()

const emit = defineEmits<{
  'update:modelValue': [visible: boolean]
  processed: []
}>()

const detail = ref<QualityInspectionDetail | null>(null)
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const formError = ref('')
const form = reactive({
  businessDate: '',
  qualifiedQuantity: '',
  rejectedQuantity: '',
  frozenQuantity: '',
  reason: '',
  remark: '',
})
const trackingRows = ref<Array<InventoryTrackingAllocationPayload & {
  qualifiedQuantity: string
  rejectedQuantity: string
  frozenQuantity: string
}>>([])
const drawerSize = 'min(560px, calc(100vw - 16px))'

const drawerVisible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})
const detailQualityStatus = computed(() => detail.value?.currentQualityStatus ?? detail.value?.qualityStatus ?? null)
const detailQualityStatusName = computed(() =>
  detail.value?.currentQualityStatusName ?? detail.value?.qualityStatusName ?? null)
const detailTrackingMethod = computed(() => inferTrackingMethodFromAllocations(detail.value?.trackingAllocations))

function resetForm(nextDetail: QualityInspectionDetail) {
  form.businessDate = nextDetail.businessDate
  form.qualifiedQuantity = nextDetail.remainingQuantity
  form.rejectedQuantity = '0.000000'
  form.frozenQuantity = '0.000000'
  form.reason = ''
  form.remark = nextDetail.remark ?? ''
  trackingRows.value = (nextDetail.trackingAllocations ?? []).map((allocation) => ({
    ...allocation,
    qualifiedQuantity: String(allocation.quantity ?? '0.000000'),
    rejectedQuantity: '0.000000',
    frozenQuantity: '0.000000',
  }))
  formError.value = ''
  error.value = ''
}

async function loadDetail() {
  if (!props.modelValue || props.inspectionId === undefined || props.inspectionId === null) {
    return
  }
  loading.value = true
  error.value = ''
  formError.value = ''
  try {
    const nextDetail = await qualityInventoryStatusApi.inspections.get(props.inspectionId)
    detail.value = nextDetail
    resetForm(nextDetail)
  } catch (caught) {
    detail.value = null
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

function parseQuantityToMicro(value: string): bigint | null {
  const trimmed = value.trim()
  if (!/^\d+(?:\.\d{1,6})?$/.test(trimmed)) {
    return null
  }
  const [integerPart, decimalPart = ''] = trimmed.split('.')
  return BigInt(integerPart) * 1_000_000n + BigInt(decimalPart.padEnd(6, '0'))
}

function positiveQuantity(value: string) {
  const parsed = parseQuantityToMicro(value)
  return parsed !== null && parsed > 0n
}

function formatMicroQuantity(value: bigint): string {
  const integerPart = value / 1_000_000n
  const decimalPart = value % 1_000_000n
  if (decimalPart === 0n) {
    return `${integerPart}.000000`
  }
  return `${integerPart}.${decimalPart.toString().padStart(6, '0')}`
}

function trackingIdentity(sourceAllocation: InventoryTrackingAllocationPayload) {
  return {
    ...(sourceAllocation.sourceAllocationId ? { sourceAllocationId: sourceAllocation.sourceAllocationId } : {}),
    ...(sourceAllocation.batchId ? { batchId: sourceAllocation.batchId } : {}),
    ...(!sourceAllocation.batchId && sourceAllocation.batchNo ? { batchNo: sourceAllocation.batchNo } : {}),
    ...(sourceAllocation.serialId ? { serialId: sourceAllocation.serialId } : {}),
    ...(!sourceAllocation.serialId && sourceAllocation.serialNo ? { serialNo: sourceAllocation.serialNo } : {}),
  }
}

function singleTrackingAllocationsForProcess(): QualityInspectionProcessPayload['trackingAllocations'] {
  if (!detail.value) {
    return undefined
  }
  const sourceAllocations = detail.value.trackingAllocations ?? []
  if (sourceAllocations.length !== 1) {
    return undefined
  }
  const sourceAllocation = sourceAllocations[0]
  const identity = trackingIdentity(sourceAllocation)
  if (Object.keys(identity).length === 0) {
    return undefined
  }
  const rows: NonNullable<QualityInspectionProcessPayload['trackingAllocations']> = []
  if (positiveQuantity(form.qualifiedQuantity)) {
    rows.push({ ...identity, quantity: form.qualifiedQuantity.trim(), qualityStatus: 'QUALIFIED' })
  }
  if (positiveQuantity(form.rejectedQuantity)) {
    rows.push({ ...identity, quantity: form.rejectedQuantity.trim(), qualityStatus: 'REJECTED' })
  }
  if (positiveQuantity(form.frozenQuantity)) {
    rows.push({ ...identity, quantity: form.frozenQuantity.trim(), qualityStatus: 'FROZEN' })
  }
  return rows.length > 0 ? rows : undefined
}

function rowTrackingProcessResult() {
  const rows: NonNullable<QualityInspectionProcessPayload['trackingAllocations']> = []
  let qualifiedTotal = 0n
  let rejectedTotal = 0n
  let frozenTotal = 0n

  for (let index = 0; index < trackingRows.value.length; index += 1) {
    const row = trackingRows.value[index]
    const sourceQuantity = parseQuantityToMicro(String(row.quantity ?? ''))
    const qualified = parseQuantityToMicro(row.qualifiedQuantity)
    const rejected = parseQuantityToMicro(row.rejectedQuantity)
    const frozen = parseQuantityToMicro(row.frozenQuantity)
    if (sourceQuantity === null || qualified === null || rejected === null || frozen === null) {
      formError.value = `第 ${index + 1} 条追踪身份数量必须为非负十进制，最多 6 位小数`
      return null
    }
    if (qualified + rejected + frozen !== sourceQuantity) {
      formError.value = `第 ${index + 1} 条追踪身份三类数量合计必须等于来源待检数量`
      return null
    }
    const identity = trackingIdentity(row)
    if (Object.keys(identity).length === 0) {
      formError.value = `第 ${index + 1} 条追踪身份缺少批次或序列号`
      return null
    }
    if (qualified > 0n) {
      rows.push({ ...identity, quantity: formatMicroQuantity(qualified), qualityStatus: 'QUALIFIED' })
    }
    if (rejected > 0n) {
      rows.push({ ...identity, quantity: formatMicroQuantity(rejected), qualityStatus: 'REJECTED' })
    }
    if (frozen > 0n) {
      rows.push({ ...identity, quantity: formatMicroQuantity(frozen), qualityStatus: 'FROZEN' })
    }
    qualifiedTotal += qualified
    rejectedTotal += rejected
    frozenTotal += frozen
  }

  return {
    qualifiedQuantity: formatMicroQuantity(qualifiedTotal),
    rejectedQuantity: formatMicroQuantity(rejectedTotal),
    frozenQuantity: formatMicroQuantity(frozenTotal),
    trackingAllocations: rows.length > 0 ? rows : undefined,
  }
}

function validateProcessPayload() {
  if (!detail.value) {
    formError.value = '质量确认详情未加载'
    return null
  }
  if (!form.businessDate.trim()) {
    formError.value = '业务日期不能为空'
    return null
  }
  if (detailTrackingMethod.value !== 'NONE' && trackingRows.value.length > 1) {
    const trackingResult = rowTrackingProcessResult()
    const expected = parseQuantityToMicro(detail.value.remainingQuantity)
    if (!trackingResult || expected === null) {
      return null
    }
    const total = parseQuantityToMicro(trackingResult.qualifiedQuantity)! + parseQuantityToMicro(trackingResult.rejectedQuantity)! + parseQuantityToMicro(trackingResult.frozenQuantity)!
    if (total !== expected) {
      formError.value = '合格、不合格和冻结数量合计必须等于待检数量'
      return null
    }
    const reason = form.reason.trim()
    if (!reason) {
      formError.value = '原因不能为空'
      return null
    }
    formError.value = ''
    return {
      businessDate: form.businessDate.trim(),
      qualifiedQuantity: trackingResult.qualifiedQuantity,
      rejectedQuantity: trackingResult.rejectedQuantity,
      frozenQuantity: trackingResult.frozenQuantity,
      ...(trackingResult.trackingAllocations ? { trackingAllocations: trackingResult.trackingAllocations } : {}),
      reason,
      ...(form.remark.trim() ? { remark: form.remark.trim() } : {}),
    }
  }
  const qualified = parseQuantityToMicro(form.qualifiedQuantity)
  const rejected = parseQuantityToMicro(form.rejectedQuantity)
  const frozen = parseQuantityToMicro(form.frozenQuantity)
  const expected = parseQuantityToMicro(detail.value.remainingQuantity)
  if (qualified === null || rejected === null || frozen === null || expected === null) {
    formError.value = '数量必须为非负十进制，最多 6 位小数'
    return null
  }
  if (qualified + rejected + frozen !== expected) {
    formError.value = '合格、不合格和冻结数量合计必须等于待检数量'
    return null
  }
  const reason = form.reason.trim()
  if (!reason) {
    formError.value = '原因不能为空'
    return null
  }
  formError.value = ''
  const trackingAllocations = detailTrackingMethod.value === 'NONE' ? undefined : singleTrackingAllocationsForProcess()
  return {
    businessDate: form.businessDate.trim(),
    qualifiedQuantity: form.qualifiedQuantity.trim(),
    rejectedQuantity: form.rejectedQuantity.trim(),
    frozenQuantity: form.frozenQuantity.trim(),
    ...(trackingAllocations ? { trackingAllocations } : {}),
    reason,
    ...(form.remark.trim() ? { remark: form.remark.trim() } : {}),
  }
}

async function submitProcess() {
  if (!detail.value || submitting.value) {
    return
  }
  const payload = validateProcessPayload()
  if (!payload) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await qualityInventoryStatusApi.inspections.process(detail.value.id, payload)
    emit('processed')
    drawerVisible.value = false
  } catch (caught) {
    error.value = errorMessage(caught)
  } finally {
    submitting.value = false
  }
}

watch(() => [props.modelValue, props.inspectionId], () => {
  void loadDetail()
})
</script>

<template>
  <el-drawer v-model="drawerVisible" title="处理质量确认" :size="drawerSize" class="quality-process-drawer">
    <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
    <el-alert v-if="formError" class="state-alert" type="error" :title="formError" :closable="false" />
    <el-alert v-if="loading" class="state-alert" type="info" title="质量确认详情加载中" :closable="false" />

    <div v-if="detail" class="process-drawer-body">
      <section class="inspection-summary">
        <div>
          <span>质量确认单</span>
          <strong>{{ detail.inspectionNo }}</strong>
        </div>
        <div>
          <span>来源单据</span>
          <strong>{{ detail.sourceDocumentNo }}</strong>
        </div>
        <div>
          <span>物料</span>
          <strong>{{ detail.materialCode }} {{ detail.materialName }}</strong>
        </div>
        <div>
          <span>当前质量状态</span>
          <QualityStatusTag
            :quality-status="detailQualityStatus"
            :quality-status-name="detailQualityStatusName"
          />
        </div>
        <div>
          <span>待检数量</span>
          <strong class="numeric-cell">{{ formatQuantity(detail.remainingQuantity) }}</strong>
        </div>
      </section>

      <section v-if="detailTrackingMethod !== 'NONE'" class="tracking-section">
        <div class="section-title">批次/序列</div>
        <TrackingAllocationReadonlyTable
          :tracking-method="detailTrackingMethod"
          :allocations="detail.trackingAllocations ?? []"
        />
        <el-table
          v-if="trackingRows.length > 1"
          :data="trackingRows"
          empty-text="暂无待检追踪身份"
          stripe
        >
          <el-table-column v-if="detailTrackingMethod === 'BATCH'" prop="batchNo" label="批次号" min-width="150" />
          <el-table-column v-if="detailTrackingMethod === 'SERIAL'" prop="serialNo" label="序列号" min-width="170" />
          <el-table-column label="待检数量" min-width="110" align="right">
            <template #default="{ row }">
              <span class="numeric-cell">{{ formatQuantity(row.quantity) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="合格" min-width="130">
            <template #default="{ row, $index }">
              <el-input
                v-model="row.qualifiedQuantity"
                :name="`quality-process-allocation-qualified-${$index}`"
                class="quantity-input"
                inputmode="decimal"
              />
            </template>
          </el-table-column>
          <el-table-column label="不合格" min-width="130">
            <template #default="{ row, $index }">
              <el-input
                v-model="row.rejectedQuantity"
                :name="`quality-process-allocation-rejected-${$index}`"
                class="quantity-input"
                inputmode="decimal"
              />
            </template>
          </el-table-column>
          <el-table-column label="冻结" min-width="130">
            <template #default="{ row, $index }">
              <el-input
                v-model="row.frozenQuantity"
                :name="`quality-process-allocation-frozen-${$index}`"
                class="quantity-input"
                inputmode="decimal"
              />
            </template>
          </el-table-column>
        </el-table>
      </section>

      <el-form class="process-form" label-position="top">
        <el-form-item label="业务日期">
          <el-date-picker
            v-model="form.businessDate"
            name="quality-process-business-date"
            type="date"
            value-on-clear=""
            value-format="YYYY-MM-DD"
            placeholder="选择业务日期"
          />
        </el-form-item>
        <el-form-item label="合格数量">
          <el-input
            v-model="form.qualifiedQuantity"
            name="quality-process-qualified-quantity"
            class="quantity-input"
            inputmode="decimal"
            placeholder="0.000000"
          />
        </el-form-item>
        <el-form-item label="不合格数量">
          <el-input
            v-model="form.rejectedQuantity"
            name="quality-process-rejected-quantity"
            class="quantity-input"
            inputmode="decimal"
            placeholder="0.000000"
          />
        </el-form-item>
        <el-form-item label="冻结数量">
          <el-input
            v-model="form.frozenQuantity"
            name="quality-process-frozen-quantity"
            class="quantity-input"
            inputmode="decimal"
            placeholder="0.000000"
          />
        </el-form-item>
        <el-form-item label="原因">
          <el-input
            v-model="form.reason"
            name="quality-process-reason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请填写质量确认原因"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            name="quality-process-remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="可选"
          />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="drawerVisible = false">取消</el-button>
      <el-button
        data-test="submit-quality-process"
        type="primary"
        :loading="submitting"
        :disabled="submitting || loading || !detail"
        @click="submitProcess"
      >
        提交质量确认
      </el-button>
    </template>
  </el-drawer>
</template>

<style scoped>
.process-drawer-body {
  display: grid;
  gap: 14px;
}

.inspection-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.inspection-summary > div {
  border: 1px solid var(--qherp-border);
  border-radius: 6px;
  min-width: 0;
  padding: 10px 12px;
}

.inspection-summary span {
  color: var(--qherp-muted);
  display: block;
  font-size: 12px;
  margin-bottom: 5px;
}

.inspection-summary strong {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.process-form {
  display: grid;
  gap: 0 12px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.tracking-section {
  display: grid;
  gap: 8px;
}

.section-title {
  font-weight: 600;
}

.process-form :deep(.el-form-item:first-child),
.process-form :deep(.el-form-item:nth-last-child(-n + 2)) {
  grid-column: 1 / -1;
}

.quantity-input :deep(input),
.numeric-cell {
  font-variant-numeric: tabular-nums;
  text-align: right;
}

@media (max-width: 640px) {
  .inspection-summary,
  .process-form {
    grid-template-columns: 1fr;
  }
}
</style>
