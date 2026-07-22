<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type {
  InventoryQualityStatus,
  InventoryStockStatus,
  InventoryTrackingAllocationPayload,
  InventoryTrackingMethod,
  ResourceId,
} from '../../../shared/api/inventoryApi'
import { formatQuantity, inventoryStockStatusLabel, qualityStatusLabel } from '../inventoryPageHelpers'
import { validateOutboundTrackingAllocations } from './trackingPayloadHelpers'

interface TrackingCandidateRecord {
  id: ResourceId
  trackingNo: string
  materialCode?: string | null
  materialName?: string | null
  warehouseName?: string | null
  qualityStatusName?: string | null
  qualityStatus?: InventoryQualityStatus | null
  stockStatusName?: string | null
  stockStatus?: InventoryStockStatus | null
  availableQuantity?: string | number | null
  disabled?: boolean
  disabledReasonCode?: string | null
  disabledReason?: string | null
}

const props = withDefaults(defineProps<{
  modelValue: boolean
  trackingMethod: InventoryTrackingMethod
  candidates: TrackingCandidateRecord[]
  selectedAllocations?: InventoryTrackingAllocationPayload[]
  expectedQuantity?: string | number | null
  loading?: boolean
  error?: string
}>(), {
  selectedAllocations: () => [],
  expectedQuantity: null,
  loading: false,
  error: '',
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  select: [value: TrackingCandidateRecord]
  confirm: [value: InventoryTrackingAllocationPayload[]]
}>()

const draftAllocations = ref<InventoryTrackingAllocationPayload[]>([])
const localError = ref('')

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => {
    emit('update:modelValue', value)
    if (!value) {
      localError.value = ''
    }
  },
})

const drawerTitle = computed(() => {
  if (props.trackingMethod === 'BATCH') {
    return '选择批次'
  }
  if (props.trackingMethod === 'SERIAL') {
    return '选择序列号'
  }
  return '选择追踪身份'
})

const selectedTitle = computed(() => {
  if (props.trackingMethod === 'BATCH') {
    return '已选批次'
  }
  if (props.trackingMethod === 'SERIAL') {
    return '已选序列号'
  }
  return '已选追踪身份'
})

const displayError = computed(() => localError.value || props.error)

function cloneAllocations() {
  draftAllocations.value = (props.selectedAllocations ?? []).map((allocation) => ({ ...allocation }))
  localError.value = ''
}

function identityKey(allocation: Partial<InventoryTrackingAllocationPayload>) {
  if (props.trackingMethod === 'BATCH') {
    return allocation.batchId ? `batch-id:${allocation.batchId}` : `batch-no:${allocation.batchNo ?? ''}`
  }
  if (props.trackingMethod === 'SERIAL') {
    return allocation.serialId ? `serial-id:${allocation.serialId}` : `serial-no:${allocation.serialNo ?? ''}`
  }
  return ''
}

function candidateAllocation(row: TrackingCandidateRecord): InventoryTrackingAllocationPayload {
  if (props.trackingMethod === 'SERIAL') {
    return {
      serialId: row.id,
      serialNo: row.trackingNo,
      quantity: '1',
      ...(row.qualityStatus ? { qualityStatus: row.qualityStatus } : {}),
      ...(row.qualityStatusName ? { qualityStatusName: row.qualityStatusName } : {}),
    }
  }
  return {
    batchId: row.id,
    batchNo: row.trackingNo,
    quantity: '',
    ...(row.qualityStatus ? { qualityStatus: row.qualityStatus } : {}),
    ...(row.qualityStatusName ? { qualityStatusName: row.qualityStatusName } : {}),
  }
}

function rowSelected(row: TrackingCandidateRecord) {
  return draftAllocations.value.some((allocation) => identityKey(allocation) === identityKey(candidateAllocation(row)))
}

function candidateDisabledReason(row: TrackingCandidateRecord) {
  if (row.disabledReason) {
    return row.disabledReason
  }
  const labels: Record<string, string> = {
    NON_QUALIFIED_NOT_AVAILABLE: '待检库存不可领料',
  }
  if (row.disabledReasonCode) {
    return labels[row.disabledReasonCode] ?? '该候选库存暂不可选择'
  }
  return row.disabled ? '该候选库存暂不可选择' : ''
}

function toggleCandidate(row: TrackingCandidateRecord) {
  if (row.disabled) {
    return
  }
  const allocation = candidateAllocation(row)
  if (rowSelected(row)) {
    draftAllocations.value = draftAllocations.value.filter((item) => identityKey(item) !== identityKey(allocation))
    return
  }
  draftAllocations.value = [...draftAllocations.value, allocation]
}

function selectCandidate(row: TrackingCandidateRecord) {
  if (!row.disabled) {
    emit('select', row)
  }
}

function updateAllocation(index: number, patch: Partial<InventoryTrackingAllocationPayload>) {
  draftAllocations.value = draftAllocations.value.map((allocation, currentIndex) => (
    currentIndex === index ? { ...allocation, ...patch } : allocation
  ))
}

function removeAllocation(index: number) {
  draftAllocations.value = draftAllocations.value.filter((_, currentIndex) => currentIndex !== index)
}

function confirmSelection() {
  const messages = props.expectedQuantity === null || props.expectedQuantity === undefined
    ? []
    : validateOutboundTrackingAllocations(
      props.trackingMethod,
      draftAllocations.value,
      props.expectedQuantity,
    )
  if (messages.length > 0) {
    localError.value = messages[0]
    return
  }
  emit('confirm', draftAllocations.value.map((allocation) => ({ ...allocation })))
  visible.value = false
}

watch(() => props.modelValue, (nextVisible) => {
  if (nextVisible) {
    cloneAllocations()
  }
})

watch(() => props.selectedAllocations, () => {
  if (props.modelValue) {
    cloneAllocations()
  }
}, { deep: true })
</script>

<template>
  <el-drawer v-model="visible" :title="drawerTitle" size="min(680px, 94vw)">
    <el-alert
      v-if="trackingMethod === 'NONE'"
      class="state-alert"
      type="info"
      title="不追踪物料无需选择批次或序列号"
      :closable="false"
    />
    <el-alert v-if="displayError" class="state-alert" type="error" :title="displayError" :closable="false" />
    <el-table
      v-loading="loading"
      :data="trackingMethod === 'NONE' ? [] : candidates"
      empty-text="暂无可选追踪库存"
      stripe
    >
      <el-table-column label="追踪身份" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ 'candidate-disabled-text': row.disabled }">{{ row.trackingNo }}</span>
        </template>
      </el-table-column>
      <el-table-column label="物料" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.materialCode || '-' }} {{ row.materialName || '' }}
        </template>
      </el-table-column>
      <el-table-column prop="warehouseName" label="仓库" min-width="120" show-overflow-tooltip />
      <el-table-column label="质量状态" min-width="100">
        <template #default="{ row }">
          {{ qualityStatusLabel(row.qualityStatus, row.qualityStatusName) }}
        </template>
      </el-table-column>
      <el-table-column label="库存状态" min-width="100">
        <template #default="{ row }">
          {{ inventoryStockStatusLabel(row.stockStatus, row.stockStatusName) }}
        </template>
      </el-table-column>
      <el-table-column label="可用量" min-width="110" align="right">
        <template #default="{ row }">
          <span class="numeric-cell">{{ formatQuantity(row.availableQuantity) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="不可用原因" min-width="180">
        <template #default="{ row }">
          <el-tag
            v-if="candidateDisabledReason(row)"
            class="candidate-disabled-reason"
            data-test="tracking-candidate-disabled-reason"
            type="danger"
            effect="plain"
          >
            {{ candidateDisabledReason(row) }}
          </el-tag>
          <span v-else class="candidate-available-text">可选择</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text :disabled="row.disabled" @click="toggleCandidate(row)">
            {{ rowSelected(row) ? '已选' : '选择' }}
          </el-button>
          <el-button class="legacy-select-button" text @click="selectCandidate(row)">兼容选择</el-button>
        </template>
      </el-table-column>
    </el-table>

    <section v-if="trackingMethod !== 'NONE'" class="selected-section">
      <div class="selected-header">
        <strong>{{ selectedTitle }}</strong>
        <span v-if="expectedQuantity !== null && expectedQuantity !== undefined">
          业务数量 {{ formatQuantity(expectedQuantity) }}
        </span>
      </div>
      <el-table :data="draftAllocations" empty-text="尚未选择追踪身份" stripe>
        <el-table-column
          v-if="trackingMethod === 'BATCH'"
          prop="batchNo"
          label="批次号"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column
          v-if="trackingMethod === 'SERIAL'"
          prop="serialNo"
          label="序列号"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column label="质量状态" min-width="110">
          <template #default="{ row }">
            {{ qualityStatusLabel(row.qualityStatus, row.qualityStatusName) }}
          </template>
        </el-table-column>
        <el-table-column label="本次分配" min-width="140" align="right">
          <template #default="{ row, $index }">
            <el-input
              v-if="trackingMethod === 'BATCH'"
              :model-value="row.quantity"
              class="quantity-input"
              :name="`tracking-picker-batch-quantity-${$index}`"
              placeholder="0.000000"
              @update:model-value="updateAllocation($index, { quantity: String($event) })"
            />
            <span v-else class="numeric-cell">1</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="88">
          <template #default="{ $index }">
            <el-button size="small" text type="danger" @click="removeAllocation($index)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button data-test="confirm-tracking-picker" type="primary" @click="confirmSelection">
        确认分配
      </el-button>
    </template>
  </el-drawer>
</template>

<style scoped>
.state-alert {
  margin-bottom: 12px;
}

.numeric-cell {
  display: inline-block;
  min-width: 64px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.candidate-disabled-text {
  color: var(--qherp-muted);
  text-decoration: line-through;
}

.candidate-disabled-reason {
  max-width: 100%;
  height: auto;
  min-height: 24px;
  white-space: normal;
  line-height: 1.4;
}

.candidate-available-text {
  color: var(--qherp-muted);
}

.legacy-select-button {
  display: none;
}

.selected-section {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.selected-header {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}

.selected-header span {
  color: var(--qherp-muted);
  font-size: 12px;
}

.quantity-input {
  max-width: 126px;
}
</style>
