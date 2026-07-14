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

function trackingText(row: InventoryCostLayerRecord) {
  if (row.batchNo && row.serialNo) {
    return `${row.batchNo} / ${row.serialNo}`
  }
  return row.batchNo || row.serialNo || '-'
}

function sourceTypeText(row: InventoryCostLayerRecord) {
  if (row.sourceTypeName) {
    return row.sourceTypeName
  }
  const labels: Record<string, string> = {
    WAREHOUSE_TRANSFER: '仓库调拨',
    OWNERSHIP_CONVERSION: '所有权转换',
    STOCKTAKE: '库存盘点',
    VALUATION_ADJUSTMENT: '估值调整',
    PURCHASE_RECEIPT: '采购入库',
    PRODUCTION_RECEIPT: '完工入库',
  }
  return row.sourceType ? labels[String(row.sourceType)] ?? String(row.sourceType) : '-'
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
      <el-table-column label="批次/序列" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">
          {{ trackingText(row) }}
        </template>
      </el-table-column>
      <el-table-column label="原始数量" min-width="110" align="right">
        <template #default="{ row }">
          <span class="numeric-cell">{{ formatQuantity(row.originalQuantity) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="原始金额" min-width="120" align="right">
        <template #default="{ row }">
          <span class="numeric-cell">{{ formatInventoryAmount(row.originalAmount) }}</span>
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
      <el-table-column label="父层" min-width="110" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.parentLayerNo || row.parentLayerId || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="来源类型" min-width="120">
        <template #default="{ row }">
          {{ sourceTypeText(row) }}
        </template>
      </el-table-column>
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
