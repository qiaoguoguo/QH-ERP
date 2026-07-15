<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createIdempotencyKey } from '../../shared/api/documentPlatformApi'
import {
  returnRefundReversalApi,
  type PurchaseReturnDetail,
  type ReversalDocumentLine,
  type ReversalSourceView,
  type ReversalTraceRecord,
} from '../../shared/api/returnRefundReversalApi'
import { currentRouteReturnTo, queryWithReturnTo, returnLocation, routeReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import TrackingAllocationReadonlyTable from '../inventory/tracking/TrackingAllocationReadonlyTable.vue'
import { inferTrackingMethodFromAllocations } from '../inventory/tracking/trackingPayloadHelpers'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { formatProcurementAmount, formatProcurementQuantity, procurementModeDisplay } from '../procurement/procurementPageHelpers'
import { formatSalesDateTime, salesErrorMessage } from '../sales/salesPageHelpers'
import ReversalStatusTag from './ReversalStatusTag.vue'
import ReversalTracePanel from './ReversalTracePanel.vue'
import { confirmAction } from '../../shared/ui/confirmDialog'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<PurchaseReturnDetail | null>(null)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const traceRows = ref<ReversalTraceRecord[]>([])
const traceVisible = ref(false)
const traceLoading = ref(false)
const traceError = ref('')

const routeId = computed(() => route.params.id as string)
const canUpdate = computed(() => authStore.hasPermission('procurement:return:update') && allowed('UPDATE'))
const canPost = computed(() => authStore.hasPermission('procurement:return:post') && allowed('POST'))
const canCancel = computed(() => authStore.hasPermission('procurement:return:cancel') && allowed('CANCEL'))
const canTrace = computed(() => authStore.hasPermission('business:reversal:view'))
const isDraft = computed(() => record.value?.status === 'DRAFT')
const inventoryImpactRows = computed(() => {
  const traceRows = (record.value?.traces ?? []).filter(isInventoryImpact)
  return traceRows.length > 0 ? traceRows : purchaseReturnLineInventoryImpactRows(record.value)
})
const costReversalImpactRows = computed(() => (record.value?.traces ?? []).filter(isCostReversalImpact))

type PurchaseReturnFallbackLine = ReversalDocumentLine & {
  postedQuantity?: string | null
  returnQuantity?: string | null
}

function allowed(action: string) {
  return (record.value?.allowedActions ?? []).includes(action)
}

function sourceRestricted(source?: ReversalSourceView | null) {
  return !source || source.restricted || !source.canViewSource
}

function routeValues(values?: Record<string, string | number | boolean>) {
  return Object.fromEntries(Object.entries(values ?? {}).map(([key, value]) => [key, String(value)]))
}

function isInventoryImpact(trace: ReversalTraceRecord) {
  if (trace.inventoryMovementId || trace.movementNo) {
    return true
  }
  const marker = [trace.effectType, trace.resourceType]
    .filter(Boolean)
    .join('|')
    .toUpperCase()
  if (marker.includes('INVENTORY') || marker.includes('STOCK_MOVEMENT')) {
    return true
  }
  return trace.direction === 'SOURCE_TO_REVERSE'
    && Boolean(trace.quantity)
    && Boolean(trace.warehouseName || trace.materialCode || trace.materialName)
}

function isCostReversalImpact(trace: ReversalTraceRecord) {
  return Boolean(trace.settlementAdjustmentId || trace.costRecordId)
}

function purchaseReturnLineQuantity(line: PurchaseReturnFallbackLine) {
  return line.postedQuantity || line.returnQuantity || line.quantity
}

function purchaseReturnReverseSource(recordValue: PurchaseReturnDetail, quantity?: string | null): ReversalSourceView {
  return {
    sourceType: 'PURCHASE_RETURN',
    sourceId: recordValue.id,
    sourceNo: recordValue.returnNo,
    businessDate: recordValue.businessDate,
    status: recordValue.status,
    quantity: quantity ?? undefined,
    canViewSource: true,
    restricted: false,
    resourceRouteName: 'procurement-return-detail',
    resourceRouteParams: { id: recordValue.id },
  }
}

function purchaseReturnLineInventoryImpactRows(recordValue?: PurchaseReturnDetail | null): ReversalTraceRecord[] {
  if (!recordValue || recordValue.status !== 'POSTED') {
    return []
  }
  const rows: ReversalTraceRecord[] = []
  for (const line of recordValue.lines) {
    const quantity = purchaseReturnLineQuantity(line)
    if (!quantity) {
      continue
    }
    rows.push({
      traceKey: `PURCHASE_RETURN:${recordValue.id}:LINE:${line.id}:INVENTORY_OUTBOUND`,
      direction: 'SOURCE_TO_REVERSE',
      effectType: 'PURCHASE_RETURN_OUTBOUND',
      resourceType: 'PURCHASE_RETURN_LINE',
      source: line.source,
      reverse: purchaseReturnReverseSource(recordValue, quantity),
      warehouseName: recordValue.warehouseName,
      materialCode: line.materialCode,
      materialName: line.materialName,
      businessDate: recordValue.businessDate,
      quantity,
      amount: recordValue.costVisible === false || line.costVisible === false ? undefined : line.amount,
      status: recordValue.status,
      canViewResource: true,
      restricted: false,
    })
  }
  return rows
}

function impactSourceNo(trace: ReversalTraceRecord, type: 'inventory' | 'cost') {
  if (impactRestricted(trace)) {
    return trace.restrictedMessage || '来源无查看权限'
  }
  if (impactCostRestricted()) {
    return type === 'inventory' ? '内部库存流水编号已隐藏' : '内部成本反冲编号已隐藏'
  }
  if (type === 'inventory') {
    if (trace.inventoryMovementId) {
      return `库存流水 #${trace.inventoryMovementId}`
    }
    return trace.movementNo || '-'
  }
  if (trace.settlementAdjustmentId) {
    return `成本反冲 #${trace.settlementAdjustmentId}`
  }
  return trace.costRecordId ? `成本记录 #${trace.costRecordId}` : '-'
}

function impactTypeText(type: 'inventory' | 'cost') {
  return type === 'inventory' ? '库存流水' : '原入库成本反冲'
}

function impactRestricted(trace: ReversalTraceRecord) {
  return trace.restricted || !trace.canViewResource
}

function impactCostRestricted() {
  return record.value?.costVisible === false
}

function impactDisplayText(trace: ReversalTraceRecord, value?: string) {
  return impactRestricted(trace) ? '' : value || '-'
}

function impactMaterialText(trace: ReversalTraceRecord) {
  if (impactRestricted(trace)) {
    return ''
  }
  const label = [trace.materialCode, trace.materialName].filter(Boolean).join(' / ')
  return label || '-'
}

function impactDirectionText(trace: ReversalTraceRecord) {
  if (impactRestricted(trace)) {
    return ''
  }
  if (trace.effectType === 'PURCHASE_RETURN_OUTBOUND') {
    return '反向出库'
  }
  if (trace.direction === 'SOURCE_TO_REVERSE') {
    return '原入库到退货'
  }
  if (trace.direction === 'REVERSE_TO_SOURCE') {
    return '退货回写原入库'
  }
  return '-'
}

function impactQuantityText(trace: ReversalTraceRecord) {
  return impactRestricted(trace) ? '' : formatProcurementQuantity(trace.quantity)
}

function impactAmountText(trace: ReversalTraceRecord) {
  if (impactCostRestricted()) {
    return '成本无权限'
  }
  return impactRestricted(trace) ? '' : formatProcurementAmount(trace.amount)
}

async function loadDetail() {
  loading.value = true
  error.value = ''
  try {
    record.value = await returnRefundReversalApi.purchaseReturns.get(routeId.value)
  } catch (caught) {
    record.value = null
    error.value = salesErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function backToList() {
  void router.push(returnLocation(route, { name: 'procurement-returns' }))
}

function editPurchaseReturn() {
  if (!record.value) {
    return
  }
  void router.push({
    name: 'procurement-return-edit',
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

async function postPurchaseReturn() {
  if (!record.value || actionLoading.value || !(await confirmAction(`确认过账采购退货“${record.value.returnNo}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    record.value = await returnRefundReversalApi.purchaseReturns.post(record.value.id, {
      version: record.value.version,
      idempotencyKey: createIdempotencyKey('purchase-return-post'),
    })
  } catch (caught) {
    actionError.value = salesErrorMessage(caught)
    await loadDetail()
  } finally {
    actionLoading.value = false
  }
}

async function cancelPurchaseReturn() {
  if (!record.value || actionLoading.value || !(await confirmAction(`确认取消采购退货“${record.value.returnNo}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    record.value = await returnRefundReversalApi.purchaseReturns.cancel(record.value.id, {
      version: record.value.version,
      idempotencyKey: createIdempotencyKey('purchase-return-cancel'),
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
      sourceType: 'PURCHASE_RETURN',
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

function purchaseReturnOwnershipText(value?: {
  procurementMode?: string | null
  projectCode?: string | null
  projectName?: string | null
} | null) {
  return procurementModeDisplay(
    value?.procurementMode === 'PROJECT' ? 'PROJECT' : value?.procurementMode === 'PUBLIC' ? 'PUBLIC' : undefined,
    value?.projectCode,
    value?.projectName,
  )
}

function purchaseReturnCostSourceText(value?: {
  originalCostLayerNo?: string | null
  originalValueMovementNo?: string | null
  costVisible?: boolean | null
} | null) {
  if (value?.costVisible === false) {
    return '成本无权限'
  }
  return `原成本层 ${value?.originalCostLayerNo || '-'} / 原价值流水 ${value?.originalValueMovementNo || '-'}`
}

function purchaseReturnAmountText(value?: { amount?: string | null; totalAmount?: string | null; costVisible?: boolean | null } | null) {
  if (value?.costVisible === false) {
    return '成本无权限'
  }
  return formatProcurementAmount(value?.amount ?? value?.totalAmount)
}

function sourceCostSourceText(recordValue: PurchaseReturnDetail) {
  return purchaseReturnCostSourceText({
    ...recordValue.source,
    costVisible: recordValue.source.costVisible ?? recordValue.costVisible,
  })
}

function sourceAmountText(recordValue: PurchaseReturnDetail) {
  return purchaseReturnAmountText({
    amount: recordValue.source.amount,
    costVisible: recordValue.source.costVisible ?? recordValue.costVisible,
  })
}

onMounted(() => {
  void loadDetail()
})
</script>

<template>
  <MasterDataTableView title="采购退货详情" description="查看采购退货来源、明细、过账状态和反向追溯。">
    <template #actions>
      <el-button @click="backToList">返回列表</el-button>
      <el-button
        v-if="record && canUpdate && isDraft"
        data-test="edit-purchase-return-detail"
        @click="editPurchaseReturn"
      >
        编辑
      </el-button>
      <el-button
        v-if="record && canPost && isDraft"
        data-test="post-purchase-return-detail"
        type="success"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="postPurchaseReturn"
      >
        过账
      </el-button>
      <el-button
        v-if="record && canCancel && isDraft"
        data-test="cancel-purchase-return-detail"
        type="danger"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="cancelPurchaseReturn"
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
      <el-alert v-if="loading" class="state-alert" type="info" title="采购退货详情加载中" :closable="false" />
    </template>

    <el-empty v-if="!loading && !record" description="采购退货不存在或无权查看" />
    <div v-else-if="record" class="detail-layout">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="退货单号">{{ record.returnNo }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ record.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ record.warehouseName }}</el-descriptions-item>
        <el-descriptions-item label="业务日期">{{ record.businessDate }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <ReversalStatusTag :status="record.status" />
        </el-descriptions-item>
        <el-descriptions-item label="采购模式">
          {{ purchaseReturnOwnershipText(record) }}
        </el-descriptions-item>
        <el-descriptions-item label="原成本层">
          {{ purchaseReturnCostSourceText(record) }}
        </el-descriptions-item>
        <el-descriptions-item label="原价值流水">
          {{ purchaseReturnCostSourceText(record) }}
        </el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatSalesDateTime(record.updatedAt) }}</el-descriptions-item>
        <el-descriptions-item label="退货数量">
          <span class="numeric-cell">{{ formatProcurementQuantity(record.totalQuantity) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="退货金额">
          <span class="numeric-cell">{{ purchaseReturnAmountText(record) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注">{{ record.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-card class="section-card" shadow="never">
        <template #header>来源采购入库</template>
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
          <el-descriptions-item label="状态">{{ record.source.status || '-' }}</el-descriptions-item>
          <el-descriptions-item label="原所有权">
            {{ purchaseReturnOwnershipText(record.source) }}
          </el-descriptions-item>
          <el-descriptions-item label="原成本来源">
            {{ sourceCostSourceText(record) }}
          </el-descriptions-item>
          <el-descriptions-item label="来源数量">
            <span class="numeric-cell">{{ formatProcurementQuantity(record.source.quantity) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="来源金额">
            <span class="numeric-cell">{{ sourceAmountText(record) }}</span>
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
            <el-table-column label="原所有权" min-width="210" show-overflow-tooltip>
              <template #default="{ row }">
                {{ purchaseReturnOwnershipText(row) }}
              </template>
            </el-table-column>
            <el-table-column prop="unitName" label="单位" width="80" />
            <el-table-column label="退货数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.quantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="单价" min-width="100" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementAmount(row.unitPrice) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="金额" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ purchaseReturnAmountText(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="原成本来源" min-width="260" show-overflow-tooltip>
              <template #default="{ row }">
                {{ purchaseReturnCostSourceText(row) }}
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
        <template #header>库存出库影响</template>
        <el-empty v-if="inventoryImpactRows.length === 0" description="暂无库存出库影响" />
        <div v-else class="table-scroll">
          <el-table :data="inventoryImpactRows" stripe>
            <el-table-column label="影响类型" min-width="120">
              <template #default>
                {{ impactTypeText('inventory') }}
              </template>
            </el-table-column>
            <el-table-column label="方向" min-width="130">
              <template #default="{ row }">
                {{ impactDirectionText(row) }}
              </template>
            </el-table-column>
            <el-table-column label="库存流水" min-width="170" show-overflow-tooltip>
              <template #default="{ row }">
                {{ impactSourceNo(row, 'inventory') }}
              </template>
            </el-table-column>
            <el-table-column label="仓库" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">
                {{ impactDisplayText(row, row.warehouseName) }}
              </template>
            </el-table-column>
            <el-table-column label="物料" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">
                {{ impactMaterialText(row) }}
              </template>
            </el-table-column>
            <el-table-column label="业务日期" min-width="110">
              <template #default="{ row }">
                {{ impactDisplayText(row, row.businessDate) }}
              </template>
            </el-table-column>
            <el-table-column label="出库数量" min-width="110" align="right">
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
        <template #header>原入库库存与成本反冲</template>
        <el-empty v-if="costReversalImpactRows.length === 0" description="暂无原入库成本反冲影响" />
        <div v-else class="table-scroll">
          <el-table :data="costReversalImpactRows" stripe>
            <el-table-column label="影响类型" min-width="120">
              <template #default>
                {{ impactTypeText('cost') }}
              </template>
            </el-table-column>
            <el-table-column label="成本反冲资源" min-width="170" show-overflow-tooltip>
              <template #default="{ row }">
                {{ impactSourceNo(row, 'cost') }}
              </template>
            </el-table-column>
            <el-table-column label="业务日期" min-width="110">
              <template #default="{ row }">
                {{ impactDisplayText(row, row.businessDate) }}
              </template>
            </el-table-column>
            <el-table-column label="反冲金额" min-width="120" align="right">
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
