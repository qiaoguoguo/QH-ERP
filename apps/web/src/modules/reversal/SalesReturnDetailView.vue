<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createIdempotencyKey } from '../../shared/api/documentPlatformApi'
import {
  returnRefundReversalApi,
  type ReversalDocumentLine,
  type ReversalSourceView,
  type ReversalTraceRecord,
  type SalesReturnAction,
  type SalesReturnDetail,
} from '../../shared/api/returnRefundReversalApi'
import { currentRouteReturnTo, queryWithReturnTo, returnLocation, routeReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import TrackingAllocationReadonlyTable from '../inventory/tracking/TrackingAllocationReadonlyTable.vue'
import { inferTrackingMethodFromAllocations } from '../inventory/tracking/trackingPayloadHelpers'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { formatSalesAmount, formatSalesDateTime, formatSalesQuantity, salesErrorMessage } from '../sales/salesPageHelpers'
import ReversalStatusTag from './ReversalStatusTag.vue'
import ReversalTracePanel from './ReversalTracePanel.vue'
import { reversalTraceStatusLabel } from './reversalPageHelpers'
import { confirmAction } from '../../shared/ui/confirmDialog'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<SalesReturnDetail | null>(null)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const traceRows = ref<ReversalTraceRecord[]>([])
const traceVisible = ref(false)
const traceLoading = ref(false)
const traceError = ref('')

const routeId = computed(() => route.params.id as string)
const canUpdate = computed(() => authStore.hasPermission('sales:return:update') && allowed('UPDATE'))
const canPost = computed(() => authStore.hasPermission('sales:return:post') && allowed('POST'))
const canCancel = computed(() => authStore.hasPermission('sales:return:cancel') && allowed('CANCEL'))
const canTrace = computed(() => authStore.hasPermission('business:reversal:view'))
const inventoryImpactRows = computed(() => {
  const traceRows = (record.value?.traces ?? []).filter(isInventoryImpact).map(enrichInventoryImpactTrace)
  if (traceRows.length > 0 || !record.value) {
    return traceRows
  }
  if (record.value.status !== 'POSTED') {
    return []
  }
  return record.value.lines.map((line): ReversalTraceRecord => ({
    traceKey: `SALES_RETURN_LINE:${record.value?.id}:${line.id}:INVENTORY_IMPACT`,
    direction: 'SOURCE_TO_REVERSE',
    source: line.source,
    reverse: record.value?.source ?? line.source,
    effectType: 'INVENTORY_IN',
    resourceType: 'INVENTORY_MOVEMENT',
    inventoryMovementId: line.stockMovementId ?? null,
    warehouseName: record.value?.warehouseName,
    materialCode: line.materialCode,
    materialName: line.materialName,
    businessDate: record.value?.businessDate,
    quantity: line.quantity,
    amount: line.amount,
    status: record.value?.status,
    canViewResource: true,
    restricted: false,
  }))
})
const receivableImpactRows = computed(() => (record.value?.traces ?? []).filter(isReceivableImpact))

function allowed(action: SalesReturnAction) {
  return (record.value?.allowedActions ?? []).includes(action)
}

function sourceRestricted(source?: ReversalSourceView | null) {
  return !source || source.restricted || !source.canViewSource
}

function routeValues(values?: Record<string, string | number | boolean>) {
  return Object.fromEntries(Object.entries(values ?? {}).map(([key, value]) => [key, String(value)]))
}

function isInventoryImpact(trace: ReversalTraceRecord) {
  return Boolean(
    trace.inventoryMovementId
    || trace.movementNo
    || trace.resourceType === 'INVENTORY_MOVEMENT'
    || trace.effectType?.includes('INVENTORY')
    || trace.warehouseName
    || trace.materialCode
  )
}

function lineMatchesTrace(line: ReversalDocumentLine, trace: ReversalTraceRecord) {
  const sourceLineIds = [
    trace.source?.sourceLineId,
    trace.reverse?.sourceLineId,
  ].filter((value) => value !== null && value !== undefined).map(String)
  if (sourceLineIds.includes(String(line.sourceLineId)) || sourceLineIds.includes(String(line.id))) {
    return true
  }
  const lineNos = [
    trace.source?.lineNo,
    trace.reverse?.lineNo,
  ].filter((value) => value !== null && value !== undefined).map(String)
  return lineNos.includes(String(line.lineNo))
}

function matchingLineForTrace(trace: ReversalTraceRecord) {
  const lines = record.value?.lines ?? []
  return lines.find((line) => lineMatchesTrace(line, trace)) ?? (lines.length === 1 ? lines[0] : undefined)
}

function enrichInventoryImpactTrace(trace: ReversalTraceRecord): ReversalTraceRecord {
  if (!record.value || impactRestricted(trace)) {
    return trace
  }
  const line = matchingLineForTrace(trace)
  if (!line) {
    return {
      ...trace,
      warehouseName: trace.warehouseName ?? record.value.warehouseName,
      businessDate: trace.businessDate ?? record.value.businessDate,
    }
  }
  return {
    ...trace,
    warehouseName: trace.warehouseName ?? record.value.warehouseName,
    materialCode: trace.materialCode ?? line.materialCode,
    materialName: trace.materialName ?? line.materialName,
    businessDate: trace.businessDate ?? record.value.businessDate,
    quantity: trace.quantity ?? line.quantity,
    amount: trace.amount ?? line.amount,
  }
}

function isReceivableImpact(trace: ReversalTraceRecord) {
  return Boolean(trace.settlementAdjustmentId || trace.resourceType === 'SETTLEMENT_ADJUSTMENT' || trace.effectType?.includes('RECEIVABLE'))
}

function impactSourceNo(trace: ReversalTraceRecord, type: 'inventory' | 'receivable') {
  if (impactRestricted(trace)) {
    return trace.restrictedMessage || '来源无查看权限'
  }
  if (type === 'inventory') {
    if (trace.inventoryMovementId) {
      return `库存流水 #${trace.inventoryMovementId}`
    }
    if (trace.movementNo) {
      return trace.movementNo
    }
    return '内部库存流水编号已隐藏'
  }
  return trace.settlementAdjustmentId ? `应收冲减 #${trace.settlementAdjustmentId}` : '-'
}

function impactTypeText(type: 'inventory' | 'receivable') {
  return type === 'inventory' ? '库存流水' : '应收冲减'
}

function impactRestricted(trace: ReversalTraceRecord) {
  return trace.restricted || !trace.canViewResource
}

function impactDisplayText(trace: ReversalTraceRecord, value?: string) {
  return impactRestricted(trace) ? '' : value || '-'
}

function impactQuantityText(trace: ReversalTraceRecord) {
  return impactRestricted(trace) ? '' : formatSalesQuantity(trace.quantity)
}

function impactAmountText(trace: ReversalTraceRecord) {
  return impactRestricted(trace) ? '' : formatSalesAmount(trace.amount)
}

async function loadDetail() {
  loading.value = true
  error.value = ''
  try {
    record.value = await returnRefundReversalApi.salesReturns.get(routeId.value)
  } catch (caught) {
    record.value = null
    error.value = salesErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function backToList() {
  void router.push(returnLocation(route, { name: 'sales-returns' }))
}

function editSalesReturn() {
  if (!record.value) {
    return
  }
  void router.push({
    name: 'sales-return-edit',
    params: { id: String(record.value.id) },
    query: queryWithReturnTo({}, routeReturnTo(route)),
  })
}

function viewSourceDocument(source: ReversalSourceView) {
  if (sourceRestricted(source) || !source.resourceRouteName) {
    return
  }
  void router.push({
    name: source.resourceRouteName,
    params: routeValues(source.resourceRouteParams),
    query: queryWithReturnTo(routeValues(source.resourceRouteQuery), currentRouteReturnTo(route)),
  })
}

async function postSalesReturn() {
  if (!record.value || actionLoading.value || !(await confirmAction(`确认过账销售退货“${record.value.returnNo}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    record.value = await returnRefundReversalApi.salesReturns.post(record.value.id, {
      version: record.value.version,
      idempotencyKey: createIdempotencyKey('sales-return-post'),
    })
  } catch (caught) {
    actionError.value = salesErrorMessage(caught)
    await loadDetail()
  } finally {
    actionLoading.value = false
  }
}

async function cancelSalesReturn() {
  if (!record.value || actionLoading.value || !(await confirmAction(`确认取消销售退货“${record.value.returnNo}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    record.value = await returnRefundReversalApi.salesReturns.cancel(record.value.id, {
      version: record.value.version,
      reason: '用户取消销售退货',
      idempotencyKey: createIdempotencyKey('sales-return-cancel'),
    })
  } catch (caught) {
    actionError.value = salesErrorMessage(caught)
    await loadDetail()
  } finally {
    actionLoading.value = false
  }
}

async function openTrace() {
  if (!record.value || traceLoading.value) {
    return
  }
  traceVisible.value = true
  traceLoading.value = true
  traceError.value = ''
  try {
    traceRows.value = await returnRefundReversalApi.traces.list({
      sourceType: 'SALES_RETURN',
      sourceId: record.value.id,
      direction: 'REVERSE_TO_SOURCE',
      includeRestricted: true,
    })
  } catch (caught) {
    traceRows.value = []
    traceError.value = salesErrorMessage(caught)
  } finally {
    traceLoading.value = false
  }
}

function closeTrace() {
  traceVisible.value = false
}

function lineSourceText(line: ReversalDocumentLine) {
  if (sourceRestricted(line.source)) {
    return line.source.restrictedMessage || '来源无查看权限'
  }
  return `${line.source.sourceNo ?? '-'} / 行 ${line.source.lineNo ?? '-'}`
}

onMounted(() => {
  void loadDetail()
})
</script>

<template>
  <MasterDataTableView title="销售退货详情" description="查看销售退货来源、明细、过账状态和反向追溯。">
    <template #actions>
      <el-button @click="backToList">返回列表</el-button>
      <el-button
        v-if="record && canUpdate"
        data-test="edit-sales-return-detail"
        @click="editSalesReturn"
      >
        编辑
      </el-button>
      <el-button
        v-if="record && canPost"
        data-test="post-sales-return-detail"
        type="success"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="postSalesReturn"
      >
        过账
      </el-button>
      <el-button
        v-if="record && canCancel"
        data-test="cancel-sales-return-detail"
        type="danger"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="cancelSalesReturn"
      >
        取消
      </el-button>
      <el-button
        v-if="record && canTrace"
        data-test="open-reversal-trace"
        type="primary"
        plain
        @click="openTrace"
      >
        反向追溯
      </el-button>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="销售退货详情加载中" :closable="false" />
    </template>

    <el-empty v-if="!loading && !record" description="销售退货不存在或无权查看" />
    <div v-else-if="record" class="detail-layout">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="退货单号">{{ record.returnNo }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ record.customerName }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ record.warehouseName }}</el-descriptions-item>
        <el-descriptions-item label="业务日期">{{ record.businessDate }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <ReversalStatusTag :status="record.status" />
        </el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatSalesDateTime(record.updatedAt) }}</el-descriptions-item>
        <el-descriptions-item label="退货数量">
          <span class="numeric-cell">{{ formatSalesQuantity(record.totalQuantity) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="退货金额">
          <span class="numeric-cell">{{ formatSalesAmount(record.totalAmount) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注">{{ record.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-card class="section-card" shadow="never">
        <template #header>来源销售出库</template>
        <el-alert
          v-if="sourceRestricted(record.source)"
          type="warning"
          :title="record.source.restrictedMessage || '来源无查看权限'"
          :closable="false"
          show-icon
        />
        <el-descriptions v-else :column="3" border>
          <el-descriptions-item label="来源单号">
            <el-button
              v-if="record.source.resourceRouteName"
              data-test="view-source-document"
              link
              type="primary"
              @click="viewSourceDocument(record.source)"
            >
              {{ record.source.sourceNo }}
            </el-button>
            <span v-else>{{ record.source.sourceNo }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="业务日期">{{ record.source.businessDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ reversalTraceStatusLabel({ sourceType: record.source.sourceType, status: record.source.status }) }}</el-descriptions-item>
          <el-descriptions-item label="来源数量">
            <span class="numeric-cell">{{ formatSalesQuantity(record.source.quantity) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="来源金额">
            <span class="numeric-cell">{{ formatSalesAmount(record.source.amount) }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card class="section-card" shadow="never">
        <template #header>退货明细</template>
        <div class="table-scroll">
          <el-table :data="record.lines" stripe>
            <el-table-column prop="lineNo" label="行号" width="80" />
            <el-table-column label="来源行" min-width="190" show-overflow-tooltip>
              <template #default="{ row }">
                {{ lineSourceText(row) }}
              </template>
            </el-table-column>
            <el-table-column label="物料" min-width="190" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.materialCode }} {{ row.materialName }}
              </template>
            </el-table-column>
            <el-table-column prop="unitName" label="单位" width="80" />
            <el-table-column label="退货数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesQuantity(row.quantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="单价" min-width="100" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesAmount(row.unitPrice) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="金额" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesAmount(row.amount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="批次/序列" min-width="240">
              <template #default="{ row }">
                <TrackingAllocationReadonlyTable
                  v-if="inferTrackingMethodFromAllocations(row.trackingAllocations) !== 'NONE'"
                  :tracking-method="inferTrackingMethodFromAllocations(row.trackingAllocations)"
                  :allocations="row.trackingAllocations ?? []"
                />
                <span v-else class="tracking-empty-text">不追踪</span>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="原因" min-width="160" show-overflow-tooltip />
          </el-table>
        </div>
      </el-card>

      <el-card class="section-card" shadow="never">
        <template #header>库存入库影响</template>
        <el-empty v-if="inventoryImpactRows.length === 0" description="暂无库存入库影响" />
        <div v-else class="table-scroll">
          <el-table :data="inventoryImpactRows" stripe>
            <el-table-column label="影响类型" min-width="120">
              <template #default>
                {{ impactTypeText('inventory') }}
              </template>
            </el-table-column>
            <el-table-column label="库存流水" min-width="170" show-overflow-tooltip>
              <template #default="{ row }">
                {{ impactSourceNo(row, 'inventory') }}
              </template>
            </el-table-column>
            <el-table-column label="仓库" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">
                {{ impactDisplayText(row, row.warehouseName || record.warehouseName) }}
              </template>
            </el-table-column>
            <el-table-column label="物料" min-width="190" show-overflow-tooltip>
              <template #default="{ row }">
                {{ impactDisplayText(row, [row.materialCode, row.materialName].filter(Boolean).join(' ')) }}
              </template>
            </el-table-column>
            <el-table-column label="业务日期" min-width="110">
              <template #default="{ row }">
                {{ impactDisplayText(row, row.businessDate) }}
              </template>
            </el-table-column>
            <el-table-column label="入库数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ impactQuantityText(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="影响金额" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ impactAmountText(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" min-width="100">
              <template #default="{ row }">
                {{ impactDisplayText(row, row.status) }}
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-card>

      <el-card class="section-card" shadow="never">
        <template #header>应收冲减影响</template>
        <el-empty v-if="receivableImpactRows.length === 0" description="暂无应收冲减影响" />
        <div v-else class="table-scroll">
          <el-table :data="receivableImpactRows" stripe>
            <el-table-column label="影响类型" min-width="120">
              <template #default>
                {{ impactTypeText('receivable') }}
              </template>
            </el-table-column>
            <el-table-column label="应收单据" min-width="170" show-overflow-tooltip>
              <template #default="{ row }">
                {{ impactSourceNo(row, 'receivable') }}
              </template>
            </el-table-column>
            <el-table-column label="业务日期" min-width="110">
              <template #default="{ row }">
                {{ impactDisplayText(row, row.businessDate) }}
              </template>
            </el-table-column>
            <el-table-column label="冲减金额" min-width="120" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ impactAmountText(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" min-width="100">
              <template #default="{ row }">
                {{ impactDisplayText(row, row.status) }}
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-card>

      <ReversalTracePanel
        :visible="traceVisible"
        :rows="traceRows"
        :loading="traceLoading"
        :error="traceError"
        @close="closeTrace"
      />
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.detail-layout {
  display: grid;
  gap: 14px;
}

.section-card {
  border-radius: 6px;
}

.table-scroll {
  overflow-x: auto;
}

.numeric-cell {
  display: inline-block;
  min-width: 72px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.tracking-empty-text {
  color: var(--qherp-muted);
  font-size: 12px;
}
</style>
