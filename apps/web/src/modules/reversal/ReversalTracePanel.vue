<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { ReversalTraceRecord } from '../../shared/api/returnRefundReversalApi'

defineProps<{
  visible: boolean
  rows: ReversalTraceRecord[]
  loading?: boolean
  error?: string
}>()

const emit = defineEmits<{
  close: []
}>()

const router = useRouter()

function restricted(row: ReversalTraceRecord) {
  return row.restricted || !row.canViewResource
}

function routeValues(values?: Record<string, string | number | boolean>) {
  return Object.fromEntries(Object.entries(values ?? {}).map(([key, value]) => [key, String(value)]))
}

function sourceTypeLabel(sourceType?: string) {
  const labels: Record<string, string> = {
    SALES_SHIPMENT: '销售出库来源',
    SALES_SHIPMENT_LINE: '销售出库行',
    SALES_RETURN: '销售退货',
    INVENTORY_MOVEMENT: '库存流水',
    RECEIVABLE: '应收冲减',
    SETTLEMENT_ADJUSTMENT: '往来冲减',
  }
  return sourceType ? labels[sourceType] ?? sourceType : '-'
}

function traceImpactType(row: ReversalTraceRecord) {
  if (row.inventoryMovementId || row.source.sourceType === 'INVENTORY_MOVEMENT' || row.reverse.sourceType === 'INVENTORY_MOVEMENT') {
    return '库存入库影响'
  }
  if (row.settlementAdjustmentId || row.source.sourceType === 'RECEIVABLE' || row.reverse.sourceType === 'RECEIVABLE') {
    return '应收冲减'
  }
  if (row.source.sourceType === 'SALES_SHIPMENT' || row.reverse.sourceType === 'SALES_SHIPMENT') {
    return '销售出库来源'
  }
  return sourceTypeLabel(row.reverse.sourceType || row.source.sourceType)
}

function viewResource(row: ReversalTraceRecord) {
  if (restricted(row) || !row.resourceRouteName) {
    return
  }
  void router.push({
    name: row.resourceRouteName,
    params: routeValues(row.resourceRouteParams),
    query: routeValues(row.resourceRouteQuery),
  })
}
</script>

<template>
  <section v-if="visible" class="reversal-trace-panel">
    <div class="reversal-trace-panel__header">
      <h2>反向追溯</h2>
      <el-button link type="primary" @click="emit('close')">关闭</el-button>
    </div>
    <el-alert v-if="error" class="state-alert" :title="error" type="error" show-icon :closable="false" />
    <div v-if="loading" class="reversal-state">追溯加载中</div>
    <el-empty v-else-if="rows.length === 0" description="暂无反向追溯" />
    <div v-else class="table-scroll">
      <el-table :data="rows" stripe>
        <el-table-column label="影响类型" min-width="130">
          <template #default="{ row }">
            {{ traceImpactType(row) }}
          </template>
        </el-table-column>
        <el-table-column label="来源类型" min-width="130">
          <template #default="{ row }">
            {{ sourceTypeLabel(row.source.sourceType) }}
          </template>
        </el-table-column>
        <el-table-column label="影响资源" min-width="130">
          <template #default="{ row }">
            {{ sourceTypeLabel(row.reverse.sourceType) }}
          </template>
        </el-table-column>
        <el-table-column label="来源单据" min-width="180">
          <template #default="{ row }">
            <span v-if="restricted(row)">{{ row.restrictedMessage || '当前账号没有查看来源详情的权限' }}</span>
            <el-button
              v-else-if="row.resourceRouteName"
              data-test="view-reversal-resource"
              link
              type="primary"
              @click="viewResource(row)"
            >
              {{ row.source.sourceNo || row.resourceRouteName }}
            </el-button>
            <span v-else>{{ row.source.sourceNo || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="方向" min-width="140">
          <template #default="{ row }">
            <span v-if="!restricted(row)">{{ row.direction === 'REVERSE_TO_SOURCE' ? '反向到来源' : '来源到反向单据' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="业务日期" min-width="120">
          <template #default="{ row }">
            <span v-if="!restricted(row)">{{ row.businessDate }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="110">
          <template #default="{ row }">
            <span v-if="!restricted(row)">{{ row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column label="数量" min-width="110" align="right">
          <template #default="{ row }">
            <span v-if="!restricted(row)" class="numeric-cell">{{ row.quantity || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="金额" min-width="120" align="right">
          <template #default="{ row }">
            <span v-if="!restricted(row)" class="numeric-cell">{{ row.amount || '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>

<style scoped>
.reversal-trace-panel {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  margin-top: 16px;
  padding: 14px;
}

.reversal-trace-panel__header {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.reversal-trace-panel__header h2 {
  font-size: 16px;
  margin: 0;
}

.reversal-state {
  color: #606266;
  padding: 16px 0;
}

.table-scroll {
  overflow-x: auto;
}

.numeric-cell {
  font-variant-numeric: tabular-nums;
}
</style>
