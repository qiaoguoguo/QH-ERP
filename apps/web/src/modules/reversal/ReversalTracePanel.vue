<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import type { ReversalRouteValue, ReversalSourceView, ReversalTraceRecord } from '../../shared/api/returnRefundReversalApi'
import { currentRouteReturnTo, queryWithReturnTo } from '../../shared/navigation/navigationReturn'
import {
  reversalSourceTypeLabel,
  reversalTraceDirectionLabel,
  reversalTraceStatusLabel,
} from './reversalPageHelpers'

interface ImpactResourceItem {
  key: string
  label: string
  routeName?: string
  routeParams?: Record<string, ReversalRouteValue>
  routeQuery?: Record<string, ReversalRouteValue>
}

withDefaults(defineProps<{
  visible: boolean
  rows: ReversalTraceRecord[]
  loading?: boolean
  error?: string
  contentOnly?: boolean
}>(), {
  contentOnly: false,
})

const emit = defineEmits<{
  close: []
}>()

const router = useRouter()
const route = useRoute()

function restricted(row: ReversalTraceRecord) {
  return row.restricted || !row.canViewResource
}

function sourceRestricted(source?: ReversalSourceView | null) {
  return !source || source.restricted || !source.canViewSource
}

function routeValues(values?: Record<string, ReversalRouteValue>) {
  return Object.fromEntries(Object.entries(values ?? {}).map(([key, value]) => [key, String(value)]))
}

function isPurchaseTrace(row: ReversalTraceRecord) {
  const routeName = String(row.resourceRouteName ?? '')
  return row.source.sourceType?.startsWith('PURCHASE_')
    || row.reverse.sourceType?.startsWith('PURCHASE_')
    || routeName === 'finance-payable-detail'
}

function isProductionMaterialReturnTrace(row: ReversalTraceRecord) {
  return row.source.sourceType?.startsWith('PRODUCTION_MATERIAL_ISSUE')
    || row.reverse.sourceType === 'PRODUCTION_MATERIAL_RETURN'
}

function isProductionMaterialSupplementTrace(row: ReversalTraceRecord) {
  return row.source.sourceType?.startsWith('PRODUCTION_WORK_ORDER')
    || row.reverse.sourceType === 'PRODUCTION_MATERIAL_SUPPLEMENT'
}

function traceImpactType(row: ReversalTraceRecord) {
  if (row.inventoryMovementId) {
    if (isProductionMaterialReturnTrace(row)) {
      return '库存入库影响'
    }
    if (isProductionMaterialSupplementTrace(row)) {
      return '库存出库影响'
    }
    return isPurchaseTrace(row) ? '库存出库影响' : '库存入库影响'
  }
  if (row.costRecordId) {
    return '成本影响'
  }
  if (row.settlementAdjustmentId) {
    return isPurchaseTrace(row) ? '原入库成本反冲' : '应收冲减'
  }
  if (row.source.sourceType === 'SALES_SHIPMENT' || row.source.sourceType === 'SALES_SHIPMENT_LINE') {
    return '销售出库来源'
  }
  if (row.source.sourceType === 'PURCHASE_RECEIPT' || row.source.sourceType === 'PURCHASE_RECEIPT_LINE') {
    return '采购入库来源'
  }
  if (row.source.sourceType === 'PRODUCTION_MATERIAL_ISSUE' || row.source.sourceType === 'PRODUCTION_MATERIAL_ISSUE_LINE') {
    return '生产领料来源'
  }
  if (row.source.sourceType === 'PRODUCTION_WORK_ORDER' || row.source.sourceType === 'PRODUCTION_WORK_ORDER_MATERIAL') {
    return '生产工单来源'
  }
  return reversalSourceTypeLabel(traceStatusSourceType(row))
}

function traceStatusSourceType(row: ReversalTraceRecord) {
  return row.reverse.sourceType ? row.reverse.sourceType : row.source.sourceType
}

function traceStatusText(row: ReversalTraceRecord) {
  return reversalTraceStatusLabel({ sourceType: traceStatusSourceType(row), status: row.status })
}

function viewSource(row: ReversalTraceRecord) {
  if (sourceRestricted(row.source) || !row.source.resourceRouteName) {
    return
  }
  void router.push({
    name: row.source.resourceRouteName,
    params: routeValues(row.source.resourceRouteParams),
    query: queryWithReturnTo(routeValues(row.source.resourceRouteQuery), currentRouteReturnTo(route)),
  })
}

function viewReverse(row: ReversalTraceRecord) {
  if (sourceRestricted(row.reverse) || !row.reverse.resourceRouteName) {
    return
  }
  void router.push({
    name: row.reverse.resourceRouteName,
    params: routeValues(row.reverse.resourceRouteParams),
    query: queryWithReturnTo(routeValues(row.reverse.resourceRouteQuery), currentRouteReturnTo(route)),
  })
}

function viewImpactResource(resource: ImpactResourceItem) {
  if (!resource.routeName) {
    return
  }
  void router.push({
    name: resource.routeName,
    params: routeValues(resource.routeParams),
    query: queryWithReturnTo(routeValues(resource.routeQuery), currentRouteReturnTo(route)),
  })
}

function settlementImpactLabel(row: ReversalTraceRecord) {
  if (isPurchaseTrace(row)) {
    return '成本反冲'
  }
  if (row.source.sourceType?.startsWith('SALES_') || row.reverse.sourceType?.startsWith('SALES_')) {
    return '应收冲减'
  }
  return '结算调整'
}

function topLevelRoute(row: ReversalTraceRecord) {
  return {
    routeName: row.resourceRouteName,
    routeParams: row.resourceRouteParams,
    routeQuery: row.resourceRouteQuery,
  }
}

function inventoryRoute(row: ReversalTraceRecord) {
  return row.resourceRouteName === 'inventory-movements' ? topLevelRoute(row) : {}
}

function settlementRoute(row: ReversalTraceRecord) {
  const routeName = String(row.resourceRouteName ?? '')
  return routeName.startsWith('finance-') ? topLevelRoute(row) : {}
}

function costRoute(row: ReversalTraceRecord) {
  if (!row.costRecordId) {
    return {}
  }
  if (row.resourceRouteName === 'cost-record-detail') {
    return topLevelRoute(row)
  }
  return {
    routeName: 'cost-record-detail',
    routeParams: { id: row.costRecordId },
  }
}

function impactResources(row: ReversalTraceRecord): ImpactResourceItem[] {
  if (restricted(row)) {
    return [{ key: 'restricted', label: row.restrictedMessage || '来源无查看权限' }]
  }
  const resources: ImpactResourceItem[] = []
  if (row.inventoryMovementId) {
    resources.push({
      key: 'inventory',
      label: `库存流水 #${row.inventoryMovementId}`,
      ...inventoryRoute(row),
    })
  }
  if (row.settlementAdjustmentId) {
    resources.push({
      key: 'settlement',
      label: `${settlementImpactLabel(row)} #${row.settlementAdjustmentId}`,
      ...settlementRoute(row),
    })
  }
  if (row.costRecordId) {
    resources.push({
      key: 'cost',
      label: `成本记录 #${row.costRecordId}`,
      ...costRoute(row),
    })
  }
  return resources.length ? resources : [{ key: 'empty', label: '-' }]
}

function sourceNo(source: ReversalSourceView) {
  return source.sourceNo || reversalSourceTypeLabel(source.sourceType)
}
</script>

<template>
  <section
    v-if="visible"
    class="reversal-trace-panel"
    :class="{ 'reversal-trace-panel--content-only': contentOnly }"
  >
    <div v-if="!contentOnly" class="reversal-trace-panel__header">
      <h2>反向追溯</h2>
      <el-button link type="primary" @click="emit('close')">关闭</el-button>
    </div>
    <el-alert v-if="error" class="state-alert" :title="error" type="error" show-icon :closable="false" />
    <div v-if="loading" class="reversal-state">追溯加载中</div>
    <el-empty v-else-if="rows.length === 0" description="暂无反向追溯" />
    <div v-else data-test="production-reversal-trace-table-scroll" class="table-scroll">
      <el-table :data="rows" stripe>
        <el-table-column label="影响类型" min-width="130">
          <template #default="{ row }">
            {{ traceImpactType(row) }}
          </template>
        </el-table-column>
        <el-table-column label="来源类型" min-width="130">
          <template #default="{ row }">
            {{ reversalSourceTypeLabel(row.source.sourceType) }}
          </template>
        </el-table-column>
        <el-table-column label="来源单据" min-width="180">
          <template #default="{ row }">
            <span v-if="sourceRestricted(row.source)">{{ row.source.restrictedMessage || '来源无查看权限' }}</span>
            <el-button
              v-else-if="row.source.resourceRouteName"
              data-test="view-reversal-source"
              link
              type="primary"
              @click="viewSource(row)"
            >
              {{ sourceNo(row.source) }}
            </el-button>
            <span v-else>{{ sourceNo(row.source) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="反向单据" min-width="180">
          <template #default="{ row }">
            <span v-if="sourceRestricted(row.reverse)">{{ row.reverse.restrictedMessage || '来源无查看权限' }}</span>
            <el-button
              v-else-if="row.reverse.resourceRouteName"
              data-test="view-reversal-reverse"
              link
              type="primary"
              @click="viewReverse(row)"
            >
              {{ sourceNo(row.reverse) }}
            </el-button>
            <span v-else>{{ sourceNo(row.reverse) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="影响资源" min-width="160">
          <template #default="{ row }">
            <div class="impact-resource-list">
              <template v-for="resource in impactResources(row)" :key="resource.key">
                <el-button
                  v-if="resource.routeName"
                  data-test="view-reversal-impact-resource"
                  link
                  type="primary"
                  @click="viewImpactResource(resource)"
                >
                  {{ resource.label }}
                </el-button>
                <span v-else>{{ resource.label }}</span>
              </template>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="方向" min-width="140">
          <template #default="{ row }">
            <span v-if="!restricted(row)">{{ reversalTraceDirectionLabel(row.direction) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="业务日期" min-width="120">
          <template #default="{ row }">
            <span v-if="!restricted(row)">{{ row.businessDate || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="110">
          <template #default="{ row }">
            <span v-if="!restricted(row)">{{ traceStatusText(row) }}</span>
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

.reversal-trace-panel--content-only {
  border: 0;
  margin-top: 0;
  padding: 0;
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

.impact-resource-list {
  align-items: flex-start;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
</style>
