<script setup lang="ts">
import type { InventoryTrackingMethod, ResourceId } from '../../../shared/api/inventoryApi'
import { formatQuantity, qualityStatusLabel } from '../inventoryPageHelpers'

interface TrackingAllocationReadonlyRow {
  batchId?: ResourceId | null
  batchNo?: string | null
  serialId?: ResourceId | null
  serialNo?: string | null
  quantity?: string | number | null
  qualityStatus?: string | null
  qualityStatusName?: string | null
  sourceDocumentNo?: string | null
}

withDefaults(defineProps<{
  trackingMethod: InventoryTrackingMethod
  allocations: TrackingAllocationReadonlyRow[]
}>(), {
  allocations: () => [],
})
</script>

<template>
  <div class="tracking-readonly-table">
    <el-alert
      v-if="trackingMethod === 'NONE'"
      type="info"
      title="不追踪物料无批次或序列号明细"
      :closable="false"
    />
    <el-table v-else :data="allocations" empty-text="暂无追踪分配" stripe>
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
      <el-table-column label="数量" min-width="120" align="right">
        <template #default="{ row }">
          <span class="numeric-cell">{{ formatQuantity(row.quantity ?? (trackingMethod === 'SERIAL' ? 1 : null)) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="sourceDocumentNo" label="来源单号" min-width="160" show-overflow-tooltip />
    </el-table>
  </div>
</template>

<style scoped>
.numeric-cell {
  display: inline-block;
  min-width: 72px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
</style>
