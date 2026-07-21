<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createIdempotencyKey } from '../../shared/api/documentPlatformApi'
import { procurementApi, type PurchaseReceiptDetailRecord, type ResourceId } from '../../shared/api/procurementApi'
import { currentRouteReturnTo, queryWithReturnTo, returnLocation, routeReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import TrackingAllocationReadonlyTable from '../inventory/tracking/TrackingAllocationReadonlyTable.vue'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import FixedPrintAction from '../platform/components/FixedPrintAction.vue'
import PurchaseOrderStatusTag from './PurchaseOrderStatusTag.vue'
import PurchaseReceiptStatusTag from './PurchaseReceiptStatusTag.vue'
import {
  formatProcurementDateTime,
  formatProcurementAmount,
  formatProcurementQuantity,
  procurementModeDisplay,
  procurementErrorMessage,
} from './procurementPageHelpers'
import { confirmAction } from '../../shared/ui/confirmDialog'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<PurchaseReceiptDetailRecord | null>(null)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const canEdit = computed(() => (
  allowed('UPDATE') && authStore.hasPermission('procurement:receipt:update')
))
const canPost = computed(() => (
  allowed('POST') && authStore.hasPermission('procurement:receipt:post')
))
const canViewSourceOrder = computed(() => authStore.hasPermission('procurement:order:view'))
const movementTraceRestricted = computed(() => record.value?.costVisible === false)
const movements = computed(() => record.value?.inventoryMovements ?? [])

function receiptLineCostText(row: {
  costVisible?: boolean | null
  costLayerNo?: string | null
  valueMovementNo?: string | null
  taxExcludedAmount?: string | null
}) {
  if (row.costVisible === false) {
    return '成本无权限'
  }
  return `成本层 ${row.costLayerNo || '-'} / 价值流水 ${row.valueMovementNo || '-'} / 未税金额 ${formatProcurementAmount(row.taxExcludedAmount)}`
}

function allowed(action: string) {
  return (record.value?.allowedActions ?? []).includes(action)
}

function movementTypeLabel(value: string): string {
  const labels: Record<string, string> = {
    PURCHASE_RECEIPT: '采购入库',
    OPENING: '期初',
    ADJUSTMENT_INCREASE: '调增',
    ADJUSTMENT_DECREASE: '调减',
    PRODUCTION_ISSUE: '生产领料',
    PRODUCTION_RECEIPT: '完工入库',
  }
  return labels[value] ?? value
}

function movementDirectionLabel(value: string): string {
  if (value === 'IN') {
    return '入库'
  }
  if (value === 'OUT') {
    return '出库'
  }
  return value
}

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await procurementApi.receipts.get(route.params.id as ResourceId)
  } catch (caught) {
    record.value = null
    error.value = procurementErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function backToList() {
  void router.push(returnLocation(route, { name: 'procurement-receipts' }))
}

function editReceipt() {
  if (!record.value) {
    return
  }
  void router.push({
    name: 'procurement-receipt-edit',
    params: { id: String(record.value.id) },
    query: queryWithReturnTo({}, routeReturnTo(route)),
  })
}

function viewSourceOrder() {
  if (!record.value) {
    return
  }
  void router.push({
    name: 'procurement-order-detail',
    params: { id: String(record.value.orderId) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

async function postReceipt() {
  if (!record.value || actionLoading.value) {
    return
  }
  if (!(await confirmAction(`确认过账采购入库“${record.value.receiptNo}”？`))) {
    return
  }

  actionError.value = ''
  actionLoading.value = true
  try {
    await procurementApi.receipts.post(record.value.id, {
      version: record.value.version,
      idempotencyKey: createIdempotencyKey('purchase-receipt-post'),
    })
    await loadRecord()
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
    await loadRecord()
  } finally {
    actionLoading.value = false
  }
}

onMounted(loadRecord)
</script>

<template>
  <MasterDataTableView title="采购入库详情" description="查看采购入库主表、明细、来源订单和库存流水追溯。">
    <template #actions>
      <el-button @click="backToList">返回列表</el-button>
      <el-button v-if="canViewSourceOrder && record" data-test="view-source-purchase-order" @click="viewSourceOrder">
        查看来源订单
      </el-button>
      <el-button v-if="canEdit" data-test="edit-purchase-receipt-detail" type="primary" @click="editReceipt">
        编辑
      </el-button>
      <el-button
        v-if="canPost"
        data-test="post-purchase-receipt-detail"
        type="success"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="postReceipt"
      >
        过账
      </el-button>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="采购入库详情加载中" :closable="false" />
      <el-alert v-if="record?.status === 'POSTED'" class="state-alert" type="info" title="已过账采购入库只读" :closable="false" />
      <el-alert v-if="!loading && error" class="state-alert" type="warning" title="采购入库详情加载失败" :closable="false" />
    </template>

    <div v-if="record" class="purchase-receipt-detail">
      <section class="summary-strip">
        <div>
          <span>总数量</span>
          <strong>{{ formatProcurementQuantity(record.totalQuantity) }}</strong>
        </div>
        <div>
          <span>业务日期</span>
          <strong>{{ record.businessDate }}</strong>
        </div>
        <div>
          <span>仓库</span>
          <strong>{{ record.warehouseName }}</strong>
        </div>
        <div>
          <span>状态</span>
          <PurchaseReceiptStatusTag :status="record.status" />
        </div>
        <div>
          <span>采购模式</span>
          <strong>{{ procurementModeDisplay(record.procurementMode, record.projectCode, record.projectName) }}</strong>
        </div>
        <div>
          <span>估值状态</span>
          <strong>估值状态：{{ record.valuationStateName || record.valuationState || '未返回' }}</strong>
        </div>
        <div>
          <span>成本</span>
          <strong>{{ record.costVisible === false ? '成本无权限' : `未税金额 ${formatProcurementAmount(record.taxExcludedAmount)}` }}</strong>
        </div>
      </section>

      <dl class="purchase-receipt-detail-list">
        <dt>入库单号</dt>
        <dd>{{ record.receiptNo }}</dd>
        <dt>供应商</dt>
        <dd>{{ record.supplierName }}</dd>
        <dt>明细行数</dt>
        <dd>{{ record.lineCount }}</dd>
        <dt>创建人</dt>
        <dd>{{ record.createdByName }}</dd>
        <dt>创建时间</dt>
        <dd>{{ formatProcurementDateTime(record.createdAt) }}</dd>
        <dt>更新时间</dt>
        <dd>{{ formatProcurementDateTime(record.updatedAt) }}</dd>
        <dt>过账人</dt>
        <dd>{{ record.postedByName || '-' }}</dd>
        <dt>过账时间</dt>
        <dd>{{ formatProcurementDateTime(record.postedAt) }}</dd>
        <dt>备注</dt>
        <dd>{{ record.remark || '未填写' }}</dd>
      </dl>

      <FixedPrintAction
        class="section-block"
        object-type="PROCUREMENT_RECEIPT"
        :object-id="record.id"
        :object-no="record.receiptNo"
        :object-status="record.status"
        :allowed-object-statuses="['POSTED', 'REVERSED']"
        title="采购入库固定打印"
      />

      <section class="section-block">
        <div class="section-title">来源订单</div>
        <dl class="purchase-receipt-detail-list">
          <dt>订单号</dt>
          <dd>{{ record.orderSummary.orderNo }}</dd>
          <dt>供应商</dt>
          <dd>{{ record.orderSummary.supplierName }}</dd>
          <dt>订单日期</dt>
          <dd>{{ record.orderSummary.orderDate }}</dd>
          <dt>订单状态</dt>
          <dd><PurchaseOrderStatusTag :status="record.orderSummary.status" /></dd>
          <dt>采购模式</dt>
          <dd>{{ procurementModeDisplay(record.orderSummary.procurementMode ?? record.procurementMode, record.orderSummary.projectCode ?? record.projectCode, record.orderSummary.projectName ?? record.projectName) }}</dd>
          <dt>价格来源</dt>
          <dd>价格来源：{{ record.orderSummary.priceSourceTypeName || record.orderSummary.priceSourceType || '未返回' }}</dd>
          <dt>订单总数量</dt>
          <dd>{{ formatProcurementQuantity(record.orderSummary.totalQuantity) }}</dd>
          <dt>订单未入库</dt>
          <dd>{{ formatProcurementQuantity(record.orderSummary.remainingQuantity) }}</dd>
        </dl>
      </section>

      <section class="section-block">
        <div class="section-title">采购入库明细</div>
        <div class="table-scroll">
          <el-table :data="record.lines" empty-text="暂无采购入库明细" stripe>
            <el-table-column prop="lineNo" label="行号" width="78" />
            <el-table-column label="物料" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.materialCode }} {{ row.materialName }}
              </template>
            </el-table-column>
            <el-table-column prop="unitName" label="单位" min-width="90" />
            <el-table-column label="到货计划" min-width="120">
              <template #default="{ row }">
                {{ row.scheduleSeq ? `到货计划 ${row.scheduleSeq}` : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="订单数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.orderedQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="入库前已收" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.receivedQuantityBefore) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="入库前未收" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.remainingQuantityBefore) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="本次入库数量" min-width="130" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.quantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="库存变动前" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.beforeQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="库存变动后" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.afterQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="成本来源" min-width="240">
              <template #default="{ row }">
                <span>{{ receiptLineCostText(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="批次/序列" min-width="240">
              <template #default="{ row }">
                <TrackingAllocationReadonlyTable
                  v-if="row.trackingMethod && row.trackingMethod !== 'NONE'"
                  :tracking-method="row.trackingMethod"
                  :allocations="row.trackingAllocations ?? []"
                />
                <span v-else class="tracking-empty-text">不追踪</span>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
          </el-table>
        </div>
      </section>

      <section class="section-block">
        <div class="section-title">库存流水追溯</div>
        <el-alert
          v-if="movementTraceRestricted"
          class="state-alert"
          type="info"
          title="内部估值编号已隐藏"
          description="成本无权限，仅显示方向、仓库、物料、数量等普通库存追溯字段。"
          :closable="false"
        />
        <el-empty v-if="movements.length === 0" description="暂无库存流水追溯" />
        <div v-else class="table-scroll">
          <el-table :data="movements" empty-text="暂无库存流水追溯" stripe>
            <el-table-column
              v-if="!movementTraceRestricted"
              prop="movementNo"
              label="流水号"
              min-width="170"
              show-overflow-tooltip
            />
            <el-table-column label="类型" min-width="100">
              <template #default="{ row }">
                {{ movementTypeLabel(row.movementType) }}
              </template>
            </el-table-column>
            <el-table-column label="方向" min-width="90">
              <template #default="{ row }">
                {{ movementDirectionLabel(row.direction) }}
              </template>
            </el-table-column>
            <el-table-column prop="warehouseName" label="仓库" min-width="130" show-overflow-tooltip />
            <el-table-column label="物料" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.materialCode }} {{ row.materialName }}
              </template>
            </el-table-column>
            <el-table-column label="变动前" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.beforeQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="变动数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.quantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="变动后" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.afterQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="businessDate" label="业务日期" min-width="110" />
            <el-table-column label="发生时间" min-width="150">
              <template #default="{ row }">
                {{ formatProcurementDateTime(row.occurredAt) }}
              </template>
            </el-table-column>
            <el-table-column prop="operatorName" label="操作人" min-width="100" />
          </el-table>
        </div>
      </section>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.purchase-receipt-detail {
  padding: 14px;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.summary-strip > div {
  border: 1px solid var(--qherp-border);
  border-radius: 6px;
  padding: 10px 12px;
}

.summary-strip span {
  color: var(--qherp-muted);
  display: block;
  font-size: 12px;
  margin-bottom: 6px;
}

.summary-strip strong {
  font-size: 18px;
  font-variant-numeric: tabular-nums;
}

.purchase-receipt-detail-list {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) 96px minmax(0, 1fr);
  gap: 10px 14px;
  margin: 0 0 16px;
}

.purchase-receipt-detail-list dt {
  color: var(--qherp-muted);
}

.purchase-receipt-detail-list dd {
  min-width: 0;
  margin: 0;
  word-break: break-word;
}

.section-block {
  margin-top: 16px;
}

.section-title {
  font-weight: 600;
  margin-bottom: 10px;
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

@media (max-width: 900px) {
  .summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .summary-strip,
  .purchase-receipt-detail-list {
    grid-template-columns: 1fr;
  }
}
</style>
