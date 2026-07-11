<script setup lang="ts">
import { computed } from 'vue'
import type { InventoryTrackingMethod, ResourceId } from '../../../shared/api/inventoryApi'
import { formatQuantity } from '../inventoryPageHelpers'

interface TrackingCandidateRecord {
  id: ResourceId
  trackingNo: string
  materialCode?: string | null
  materialName?: string | null
  warehouseName?: string | null
  qualityStatusName?: string | null
  stockStatusName?: string | null
  availableQuantity?: string | number | null
  disabled?: boolean
  disabledReason?: string | null
}

const props = withDefaults(defineProps<{
  modelValue: boolean
  trackingMethod: InventoryTrackingMethod
  candidates: TrackingCandidateRecord[]
  loading?: boolean
  error?: string
}>(), {
  loading: false,
  error: '',
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  select: [value: TrackingCandidateRecord]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
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

function selectCandidate(row: TrackingCandidateRecord) {
  if (row.disabled) {
    return
  }
  emit('select', row)
}
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
    <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
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
      <el-table-column prop="qualityStatusName" label="质量状态" min-width="100" />
      <el-table-column prop="stockStatusName" label="库存状态" min-width="100" />
      <el-table-column label="可用量" min-width="110" align="right">
        <template #default="{ row }">
          <span class="numeric-cell">{{ formatQuantity(row.availableQuantity) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="不可用原因" min-width="180">
        <template #default="{ row }">
          <el-tag
            v-if="row.disabledReason"
            class="candidate-disabled-reason"
            data-test="tracking-candidate-disabled-reason"
            type="danger"
            effect="plain"
          >
            {{ row.disabledReason }}
          </el-tag>
          <span v-else class="candidate-available-text">可选择</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button size="small" text :disabled="row.disabled" @click="selectCandidate(row)">
            选择
          </el-button>
        </template>
      </el-table-column>
    </el-table>
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
</style>
