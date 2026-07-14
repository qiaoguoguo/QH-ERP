<script setup lang="ts">
import type { InventoryCostLayerRecord } from '../../shared/api/inventoryApi'
import { formatInventoryAmount, formatQuantity, ownershipTypeLabel } from './inventoryPageHelpers'

defineProps<{
  modelValue: boolean
  records: InventoryCostLayerRecord[]
  loading: boolean
  error: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

function updateVisible(value: boolean) {
  emit('update:modelValue', value)
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="成本层追溯"
    size="min(760px, 94vw)"
    @update:model-value="updateVisible"
  >
    <el-alert
      v-if="error"
      class="state-alert"
      type="error"
      :title="error"
      :closable="false"
    />
    <el-table
      v-loading="loading"
      :data="records"
      empty-text="暂无成本层"
      stripe
    >
      <el-table-column prop="layerNo" label="成本层" min-width="140" show-overflow-tooltip />
      <el-table-column label="所有权" min-width="100">
        <template #default="{ row }">
          {{ row.ownershipTypeName || ownershipTypeLabel(row.ownershipType) }}
        </template>
      </el-table-column>
      <el-table-column label="项目" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.projectNo ? `${row.projectNo} ${row.projectName || ''}` : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="原始数量" min-width="110" align="right">
        <template #default="{ row }">
          <span class="numeric-cell">{{ formatQuantity(row.originalQuantity) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="剩余数量" min-width="110" align="right">
        <template #default="{ row }">
          <span class="numeric-cell">{{ formatQuantity(row.remainingQuantity) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="剩余金额" min-width="120" align="right">
        <template #default="{ row }">
          <span class="numeric-cell">{{ formatInventoryAmount(row.remainingAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="单位成本" min-width="120" align="right">
        <template #default="{ row }">
          <span class="numeric-cell">{{ formatInventoryAmount(row.unitCost, 6) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="statusName" label="状态" min-width="90" />
      <el-table-column prop="sourceDocumentNo" label="来源单据" min-width="150" show-overflow-tooltip />
    </el-table>
  </el-drawer>
</template>

<style scoped>
.numeric-cell {
  display: inline-block;
  min-width: 72px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
</style>
