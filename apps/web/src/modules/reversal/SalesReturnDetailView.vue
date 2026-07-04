<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  returnRefundReversalApi,
  type ReversalDocumentLine,
  type ReversalSourceView,
  type ReversalTraceRecord,
  type SalesReturnDetail,
} from '../../shared/api/returnRefundReversalApi'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { formatSalesAmount, formatSalesDateTime, formatSalesQuantity, salesErrorMessage } from '../sales/salesPageHelpers'
import ReversalStatusTag from './ReversalStatusTag.vue'
import ReversalTracePanel from './ReversalTracePanel.vue'

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
const canUpdate = computed(() => authStore.hasPermission('sales:return:update'))
const canPost = computed(() => authStore.hasPermission('sales:return:post'))
const canCancel = computed(() => authStore.hasPermission('sales:return:cancel'))
const canTrace = computed(() => authStore.hasPermission('business:reversal:view'))
const isDraft = computed(() => record.value?.status === 'DRAFT')
const inventoryImpactRows = computed(() => (record.value?.traces ?? []).filter(isInventoryImpact))
const receivableImpactRows = computed(() => (record.value?.traces ?? []).filter(isReceivableImpact))

function sourceRestricted(source?: ReversalSourceView | null) {
  return !source || source.restricted || !source.canViewSource
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

function isInventoryImpact(trace: ReversalTraceRecord) {
  return Boolean(trace.inventoryMovementId)
    || trace.source.sourceType === 'INVENTORY_MOVEMENT'
    || trace.reverse.sourceType === 'INVENTORY_MOVEMENT'
    || trace.resourceRouteName === 'inventory-movements'
}

function isReceivableImpact(trace: ReversalTraceRecord) {
  return Boolean(trace.settlementAdjustmentId)
    || trace.source.sourceType === 'RECEIVABLE'
    || trace.reverse.sourceType === 'RECEIVABLE'
    || trace.resourceRouteName === 'finance-receivable-detail'
}

function impactTarget(trace: ReversalTraceRecord, type: 'inventory' | 'receivable') {
  const matches = type === 'inventory'
    ? (source: ReversalSourceView) => source.sourceType === 'INVENTORY_MOVEMENT'
    : (source: ReversalSourceView) => source.sourceType === 'RECEIVABLE' || source.sourceType === 'SETTLEMENT_ADJUSTMENT'
  return [trace.reverse, trace.source].find(matches) ?? trace.reverse
}

function impactSourceNo(trace: ReversalTraceRecord, type: 'inventory' | 'receivable') {
  const source = impactTarget(trace, type)
  if (impactRestricted(trace, type)) {
    return source.restrictedMessage || '来源无查看权限'
  }
  return source.sourceNo || '-'
}

function impactTypeText(trace: ReversalTraceRecord, type: 'inventory' | 'receivable') {
  return sourceTypeLabel(impactTarget(trace, type).sourceType)
}

function impactRestricted(trace: ReversalTraceRecord, type: 'inventory' | 'receivable') {
  return trace.restricted || sourceRestricted(impactTarget(trace, type))
}

function impactDisplayText(trace: ReversalTraceRecord, type: 'inventory' | 'receivable', value?: string) {
  return impactRestricted(trace, type) ? '' : value || '-'
}

function impactQuantityText(trace: ReversalTraceRecord, type: 'inventory' | 'receivable') {
  return impactRestricted(trace, type) ? '' : formatSalesQuantity(trace.quantity)
}

function impactAmountText(trace: ReversalTraceRecord, type: 'inventory' | 'receivable') {
  return impactRestricted(trace, type) ? '' : formatSalesAmount(trace.amount)
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
  void router.push({ name: 'sales-returns' })
}

function editSalesReturn() {
  if (!record.value) {
    return
  }
  void router.push({ name: 'sales-return-edit', params: { id: String(record.value.id) } })
}

function viewSourceDocument(source: ReversalSourceView) {
  if (sourceRestricted(source) || !source.resourceRouteName) {
    return
  }
  void router.push({
    name: source.resourceRouteName,
    params: routeValues(source.resourceRouteParams),
    query: routeValues(source.resourceRouteQuery),
  })
}

async function postSalesReturn() {
  if (!record.value || actionLoading.value || !window.confirm(`确认过账销售退货“${record.value.returnNo}”？`)) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    record.value = await returnRefundReversalApi.salesReturns.post(record.value.id)
  } catch (caught) {
    actionError.value = salesErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function cancelSalesReturn() {
  if (!record.value || actionLoading.value || !window.confirm(`确认取消销售退货“${record.value.returnNo}”？`)) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    record.value = await returnRefundReversalApi.salesReturns.cancel(record.value.id)
  } catch (caught) {
    actionError.value = salesErrorMessage(caught)
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
        v-if="record && canUpdate && isDraft"
        data-test="edit-sales-return-detail"
        @click="editSalesReturn"
      >
        编辑
      </el-button>
      <el-button
        v-if="record && canPost && isDraft"
        data-test="post-sales-return-detail"
        type="success"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="postSalesReturn"
      >
        过账
      </el-button>
      <el-button
        v-if="record && canCancel && isDraft"
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
          <el-descriptions-item label="状态">{{ record.source.status || '-' }}</el-descriptions-item>
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
              <template #default="{ row }">
                {{ impactTypeText(row, 'inventory') }}
              </template>
            </el-table-column>
            <el-table-column label="库存流水" min-width="170" show-overflow-tooltip>
              <template #default="{ row }">
                {{ impactSourceNo(row, 'inventory') }}
              </template>
            </el-table-column>
            <el-table-column label="业务日期" min-width="110">
              <template #default="{ row }">
                {{ impactDisplayText(row, 'inventory', row.businessDate) }}
              </template>
            </el-table-column>
            <el-table-column label="入库数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ impactQuantityText(row, 'inventory') }}</span>
              </template>
            </el-table-column>
            <el-table-column label="影响金额" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ impactAmountText(row, 'inventory') }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" min-width="100">
              <template #default="{ row }">
                {{ impactDisplayText(row, 'inventory', row.status) }}
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
              <template #default="{ row }">
                {{ impactTypeText(row, 'receivable') }}
              </template>
            </el-table-column>
            <el-table-column label="应收单据" min-width="170" show-overflow-tooltip>
              <template #default="{ row }">
                {{ impactSourceNo(row, 'receivable') }}
              </template>
            </el-table-column>
            <el-table-column label="业务日期" min-width="110">
              <template #default="{ row }">
                {{ impactDisplayText(row, 'receivable', row.businessDate) }}
              </template>
            </el-table-column>
            <el-table-column label="冲减金额" min-width="120" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ impactAmountText(row, 'receivable') }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" min-width="100">
              <template #default="{ row }">
                {{ impactDisplayText(row, 'receivable', row.status) }}
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
</style>
