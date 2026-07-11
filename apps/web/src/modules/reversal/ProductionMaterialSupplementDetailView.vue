<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  returnRefundReversalApi,
  type ProductionMaterialSupplementDetail,
  type ReversalDocumentLine,
  type ReversalSourceView,
  type ReversalTraceRecord,
} from '../../shared/api/returnRefundReversalApi'
import { currentRouteReturnTo, queryWithReturnTo, returnLocation, routeReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import TrackingAllocationReadonlyTable from '../inventory/tracking/TrackingAllocationReadonlyTable.vue'
import { inferTrackingMethodFromAllocations } from '../inventory/tracking/trackingPayloadHelpers'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { formatSalesAmount } from '../sales/salesPageHelpers'
import { formatProductionDateTime, formatProductionQuantity, productionErrorMessage } from '../production/productionPageHelpers'
import ReversalStatusTag from './ReversalStatusTag.vue'
import ReversalTracePanel from './ReversalTracePanel.vue'
import { confirmAction } from '../../shared/ui/confirmDialog'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<ProductionMaterialSupplementDetail | null>(null)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const traceRows = ref<ReversalTraceRecord[]>([])
const traceVisible = ref(false)
const traceLoading = ref(false)
const traceError = ref('')

const routeId = computed(() => route.params.id as string)
const canUpdate = computed(() => authStore.hasPermission('production:material-supplement:update'))
const canPost = computed(() => authStore.hasPermission('production:material-supplement:post'))
const canCancel = computed(() => authStore.hasPermission('production:material-supplement:cancel'))
const canTrace = computed(() => authStore.hasPermission('business:reversal:view'))
const isDraft = computed(() => record.value?.status === 'DRAFT')
const inventoryImpactRows = computed(() => (record.value?.traces ?? []).filter((trace) => Boolean(trace.inventoryMovementId)))
const costImpactRows = computed(() => (record.value?.traces ?? []).filter((trace) => Boolean(trace.costRecordId)))

function sourceRestricted(source?: ReversalSourceView | null) {
  return !source || source.restricted || !source.canViewSource
}

function routeValues(values?: Record<string, string | number | boolean>) {
  return Object.fromEntries(Object.entries(values ?? {}).map(([key, value]) => [key, String(value)]))
}

function impactRestricted(trace: ReversalTraceRecord) {
  return trace.restricted || !trace.canViewResource
}

function impactResourceText(trace: ReversalTraceRecord, type: 'inventory' | 'cost') {
  if (impactRestricted(trace)) {
    return trace.restrictedMessage || '来源无查看权限'
  }
  if (type === 'inventory') {
    return trace.inventoryMovementId ? `库存流水 #${trace.inventoryMovementId}` : '-'
  }
  return trace.costRecordId ? `成本记录 #${trace.costRecordId}` : '-'
}

function impactDisplayText(trace: ReversalTraceRecord, value?: string) {
  return impactRestricted(trace) ? '' : value || '-'
}

function impactQuantityText(trace: ReversalTraceRecord) {
  return impactRestricted(trace) ? '' : formatProductionQuantity(trace.quantity)
}

function impactAmountText(trace: ReversalTraceRecord) {
  return impactRestricted(trace) ? '' : formatSalesAmount(trace.amount)
}

async function loadDetail() {
  loading.value = true
  error.value = ''
  try {
    record.value = await returnRefundReversalApi.productionMaterialSupplements.get(routeId.value)
  } catch (caught) {
    record.value = null
    error.value = productionErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function backToList() {
  void router.push(returnLocation(route, { name: 'production-material-supplements' }))
}

function editMaterialSupplement() {
  if (!record.value) {
    return
  }
  void router.push({
    name: 'production-material-supplement-edit',
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

async function postMaterialSupplement() {
  if (!record.value || actionLoading.value || !(await confirmAction(`确认过账生产补料“${record.value.supplementNo}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    record.value = await returnRefundReversalApi.productionMaterialSupplements.post(record.value.id)
  } catch (caught) {
    actionError.value = productionErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function cancelMaterialSupplement() {
  if (!record.value || actionLoading.value || !(await confirmAction(`确认取消生产补料“${record.value.supplementNo}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    record.value = await returnRefundReversalApi.productionMaterialSupplements.cancel(record.value.id)
  } catch (caught) {
    actionError.value = productionErrorMessage(caught)
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
      sourceType: 'PRODUCTION_MATERIAL_SUPPLEMENT',
      sourceId: record.value.id,
      direction: 'REVERSE_TO_SOURCE',
      includeRestricted: true,
    })
  } catch (caught) {
    traceRows.value = []
    traceError.value = productionErrorMessage(caught)
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
  <MasterDataTableView title="生产补料详情" description="查看生产补料来源、明细、库存出库、成本影响和反向追溯。">
    <template #actions>
      <el-button @click="backToList">返回列表</el-button>
      <el-button
        v-if="record && canUpdate && isDraft"
        data-test="edit-material-supplement-detail"
        @click="editMaterialSupplement"
      >
        编辑
      </el-button>
      <el-button
        v-if="record && canPost && isDraft"
        data-test="post-material-supplement-detail"
        type="success"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="postMaterialSupplement"
      >
        过账
      </el-button>
      <el-button
        v-if="record && canCancel && isDraft"
        data-test="cancel-material-supplement-detail"
        type="danger"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="cancelMaterialSupplement"
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
      <el-alert v-if="loading" class="state-alert" type="info" title="生产补料详情加载中" :closable="false" />
    </template>

    <el-empty v-if="!loading && !record" description="生产补料不存在或无权查看" />
    <div v-else-if="record" class="detail-layout">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="补料单号">{{ record.supplementNo }}</el-descriptions-item>
        <el-descriptions-item label="生产工单">{{ record.workOrderNo }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ record.warehouseName }}</el-descriptions-item>
        <el-descriptions-item label="业务日期">{{ record.businessDate }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <ReversalStatusTag :status="record.status" />
        </el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatProductionDateTime(record.updatedAt) }}</el-descriptions-item>
        <el-descriptions-item label="补料数量">
          <span class="numeric-cell">{{ formatProductionQuantity(record.totalQuantity) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="影响金额">
          <span class="numeric-cell">{{ formatSalesAmount(record.totalAmount) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注">{{ record.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-card class="section-card" shadow="never">
        <template #header>来源生产工单</template>
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
        </el-descriptions>
      </el-card>

      <el-card class="section-card" shadow="never">
        <template #header>补料明细</template>
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
            <el-table-column label="补料数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProductionQuantity(row.quantity) }}</span>
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
        <template #header>库存出库影响</template>
        <el-empty v-if="inventoryImpactRows.length === 0" description="暂无库存出库影响" />
        <div v-else class="table-scroll">
          <el-table :data="inventoryImpactRows" stripe>
            <el-table-column label="库存流水" min-width="170" show-overflow-tooltip>
              <template #default="{ row }">
                {{ impactResourceText(row, 'inventory') }}
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
        <template #header>成本影响</template>
        <el-empty v-if="costImpactRows.length === 0" description="暂无成本影响" />
        <div v-else class="table-scroll">
          <el-table :data="costImpactRows" stripe>
            <el-table-column label="成本记录" min-width="170" show-overflow-tooltip>
              <template #default="{ row }">
                {{ impactResourceText(row, 'cost') }}
              </template>
            </el-table-column>
            <el-table-column label="业务日期" min-width="110">
              <template #default="{ row }">
                {{ impactDisplayText(row, row.businessDate) }}
              </template>
            </el-table-column>
            <el-table-column label="补料数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ impactQuantityText(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="增加金额" min-width="110" align="right">
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
