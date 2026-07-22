<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type DocumentTaskRecord,
} from '../../shared/api/documentPlatformApi'
import { procurementApi, type PurchaseOrderDetailRecord, type ResourceId } from '../../shared/api/procurementApi'
import { currentRouteReturnTo, queryWithReturnTo, returnLocation, routeReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import PurchaseOrderStatusTag from './PurchaseOrderStatusTag.vue'
import {
  formatProcurementDateTime,
  formatProcurementAmount,
  formatProcurementQuantity,
  procurementOwnershipDisplay,
  procurementPriceSourceDisplay,
  procurementApprovalStatusLabel,
  procurementErrorMessage,
  purchaseInTransitStatusLabel,
  purchaseReceiptStatusLabel,
  purchaseReceiptStatusTagType,
  purchaseScheduleStatusLabel,
} from './procurementPageHelpers'
import { confirmAction } from '../../shared/ui/confirmDialog'
import ProcurementDocumentTaskPanel from './ProcurementDocumentTaskPanel.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<PurchaseOrderDetailRecord | null>(null)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const latestDocumentTask = ref<DocumentTaskRecord | null>(null)

function allowed(action: string): boolean {
  return Boolean(record.value?.allowedActions?.includes(action))
}

const canEdit = computed(() => (
  allowed('UPDATE') && authStore.hasPermission('procurement:order:update')
))
const canConfirm = computed(() => (
  allowed('CONFIRM') && authStore.hasPermission('procurement:order:confirm')
))
const canSubmitException = computed(() => (
  (allowed('SUBMIT_EXCEPTION') || allowed('EXCEPTION_SUBMIT')) && authStore.hasPermission('procurement:order:exception-submit')
))
const canCancel = computed(() => (
  allowed('CANCEL') && authStore.hasPermission('procurement:order:cancel')
))
const canClose = computed(() => (
  allowed('CLOSE') && authStore.hasPermission('procurement:order:close')
))
const canCreateReceipt = computed(() => (
  allowed('CREATE_RECEIPT') && authStore.hasPermission('procurement:receipt:create')
))
const canManageSchedules = computed(() => (
  allowed('UPDATE_SCHEDULES') && authStore.hasPermission('procurement:order:update')
))
const canPrint = computed(() => (
  allowed('PRINT') && authStore.hasPermission('procurement:order:print')
))
const canViewReceipt = computed(() => authStore.hasPermission('procurement:receipt:view'))

function purchaseOrderPriceSourceText() {
  return record.value ? procurementPriceSourceDisplay(record.value) : '价格来源未返回'
}

function purchaseOrderLinePriceSourceText(line: PurchaseOrderDetailRecord['lines'][number]) {
  return procurementPriceSourceDisplay(line)
}

function purchaseOrderLineTaxText(
  line: Pick<PurchaseOrderDetailRecord['lines'][number], 'taxExcludedUnitPrice' | 'taxIncludedUnitPrice' | 'taxRate' | 'currency'>,
  fallbackCurrency?: string | null,
) {
  if (!line.taxExcludedUnitPrice && !line.taxIncludedUnitPrice && !line.taxRate) {
    return '税价未返回'
  }
  return [
    `未税单价 ${formatProcurementAmount(line.taxExcludedUnitPrice)}`,
    `含税单价 ${formatProcurementAmount(line.taxIncludedUnitPrice)}`,
    `税率 ${formatProcurementAmount(line.taxRate)}`,
    line.currency || fallbackCurrency || 'CNY',
  ].join(' / ')
}

function purchaseOrderTaxSummaryText(order: PurchaseOrderDetailRecord) {
  const lines = order.lines ?? []
  if (lines.length === 1) {
    return purchaseOrderLineTaxText(lines[0], order.currency)
  }
  if (lines.length > 1) {
    return `${lines.length} 行税价见明细`
  }
  return '税价未返回'
}

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await procurementApi.orders.get(route.params.id as ResourceId)
  } catch (caught) {
    record.value = null
    error.value = procurementErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function backToList() {
  void router.push(returnLocation(route, { name: 'procurement-orders' }))
}

function editOrder() {
  if (!record.value) {
    return
  }
  void router.push({
    name: 'procurement-order-edit',
    params: { id: String(record.value.id) },
    query: queryWithReturnTo({}, routeReturnTo(route)),
  })
}

function createReceipt() {
  if (!record.value) {
    return
  }
  void router.push({ name: 'procurement-receipt-create', params: { orderId: String(record.value.id) } })
}

function manageSchedules() {
  if (!record.value) {
    return
  }
  void router.push({
    name: 'procurement-order-schedules',
    params: { id: String(record.value.id) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function viewReceipt(receiptId: ResourceId) {
  void router.push({
    name: 'procurement-receipt-detail',
    params: { id: String(receiptId) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

async function printOrder() {
  if (!record.value || actionLoading.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    latestDocumentTask.value = await documentPlatformApi.printTasks.create({
      objectType: 'PROCUREMENT_ORDER',
      objectId: record.value.id,
      templateCode: 'PROCUREMENT_ORDER_V1',
      idempotencyKey: createIdempotencyKey('procurement-order-print'),
    })
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function runOrderAction(action: 'confirm' | 'cancel' | 'close') {
  if (!record.value || actionLoading.value) {
    return
  }
  const actionLabels = {
    confirm: '确认',
    cancel: '取消',
    close: '关闭',
  }
  if (!(await confirmAction(`确认${actionLabels[action]}采购订单“${record.value.orderNo}”？`))) {
    return
  }

  actionError.value = ''
  actionLoading.value = true
  try {
    const actionPayload = {
      version: record.value.version,
      idempotencyKey: createIdempotencyKey(`purchase-order-${action}`),
    }
    if (action === 'confirm') {
      await procurementApi.orders.confirm(record.value.id, actionPayload)
    } else if (action === 'cancel') {
      await procurementApi.orders.cancel(record.value.id, actionPayload)
    } else {
      await procurementApi.orders.close(record.value.id, actionPayload)
    }
    await loadRecord()
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
    await loadRecord()
  } finally {
    actionLoading.value = false
  }
}

async function submitException() {
  if (!record.value || actionLoading.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await procurementApi.orders.submitException(record.value.id, {
      version: record.value.version,
      reason: record.value.exceptionReason || record.value.priceSourceReason || '提交采购订单例外审批',
      idempotencyKey: createIdempotencyKey('purchase-order-exception-submit'),
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
  <MasterDataTableView title="采购订单详情" description="查看采购订单主表、明细、入库进度和来源追溯摘要。">
    <template #actions>
      <el-button @click="backToList">返回列表</el-button>
      <el-button v-if="canEdit" data-test="edit-purchase-order-detail" type="primary" @click="editOrder">
        编辑
      </el-button>
      <el-button
        v-if="canConfirm"
        data-test="confirm-purchase-order-detail"
        type="success"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="runOrderAction('confirm')"
      >
        确认
      </el-button>
      <el-button
        v-if="canSubmitException"
        data-test="submit-purchase-order-exception"
        type="primary"
        plain
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="submitException"
      >
        提交例外审批
      </el-button>
      <el-button
        v-if="canCancel"
        data-test="cancel-purchase-order-detail"
        type="danger"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="runOrderAction('cancel')"
      >
        取消
      </el-button>
      <el-button
        v-if="canClose"
        data-test="close-purchase-order-detail"
        type="warning"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="runOrderAction('close')"
      >
        关闭
      </el-button>
      <el-button
        v-if="canCreateReceipt"
        data-test="create-purchase-receipt-detail"
        type="primary"
        plain
        @click="createReceipt"
      >
        创建入库
      </el-button>
      <el-button
        v-if="canManageSchedules"
        data-test="manage-purchase-schedules-detail"
        plain
        @click="manageSchedules"
      >
        到货计划
      </el-button>
      <el-button
        v-if="canPrint"
        data-test="print-purchase-order-detail"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="printOrder"
      >
        固定打印
      </el-button>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="采购订单详情加载中" :closable="false" />
      <el-alert v-if="!loading && error" class="state-alert" type="warning" title="采购订单详情加载失败" :closable="false" />
    </template>

    <div v-if="record" class="purchase-order-detail">
      <ProcurementDocumentTaskPanel :task="latestDocumentTask" />

      <section class="summary-strip">
        <div>
          <span>总数量</span>
          <strong>{{ formatProcurementQuantity(record.totalQuantity) }}</strong>
        </div>
        <div>
          <span>已入库</span>
          <strong>{{ formatProcurementQuantity(record.receivedQuantity) }}</strong>
        </div>
        <div>
          <span>未入库</span>
          <strong>{{ formatProcurementQuantity(record.remainingQuantity) }}</strong>
        </div>
        <div>
          <span>采购在途参考</span>
          <strong>{{ formatProcurementQuantity(record.inTransitQuantity) }}</strong>
        </div>
        <div>
          <span>在途状态</span>
          <strong>{{ purchaseInTransitStatusLabel(record.inTransitStatus, record.inTransitStatusName) }}</strong>
        </div>
        <div>
          <span>订单日期</span>
          <strong>{{ record.orderDate }}</strong>
        </div>
        <div>
          <span>状态</span>
          <PurchaseOrderStatusTag :status="record.status" />
        </div>
        <div>
          <span>下一到货日</span>
          <strong>{{ record.nextArrivalDate || record.expectedArrivalDate || '-' }}</strong>
        </div>
      </section>

      <dl class="purchase-order-detail-list">
        <dt>订单号</dt>
        <dd>{{ record.orderNo }}</dd>
        <dt>采购模式</dt>
        <dd>{{ procurementOwnershipDisplay(record) }}</dd>
        <dt>供应商</dt>
        <dd>{{ record.supplierCode }} {{ record.supplierName }}</dd>
        <dt>审批状态</dt>
        <dd>审批状态：{{ procurementApprovalStatusLabel(record.approvalStatus, record.approvalStatusName) }}</dd>
        <dt>价格来源</dt>
        <dd>价格来源：{{ purchaseOrderPriceSourceText() }}</dd>
        <dt>例外原因</dt>
        <dd>例外原因：{{ record.exceptionReason || record.priceSourceReason || '无' }}</dd>
        <dt>税价</dt>
        <dd>
          {{ purchaseOrderTaxSummaryText(record) }}
        </dd>
        <dt>预计到货</dt>
        <dd>{{ record.expectedArrivalDate || '-' }}</dd>
        <dt>明细行数</dt>
        <dd>{{ record.lineCount }}</dd>
        <dt>创建人</dt>
        <dd>{{ record.createdByName }}</dd>
        <dt>创建时间</dt>
        <dd>{{ formatProcurementDateTime(record.createdAt) }}</dd>
        <dt>更新时间</dt>
        <dd>{{ formatProcurementDateTime(record.updatedAt) }}</dd>
        <dt>备注</dt>
        <dd>{{ record.remark || '未填写' }}</dd>
        <dt>结案原因</dt>
        <dd>结案原因：{{ record.closeReason || '未结案' }}</dd>
      </dl>

      <section class="section-block">
        <div class="section-title">采购明细</div>
        <div class="table-scroll">
          <el-table :data="record.lines" empty-text="暂无采购明细" stripe>
            <el-table-column prop="lineNo" label="行号" width="78" />
            <el-table-column label="物料" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.materialCode }} {{ row.materialName }}
              </template>
            </el-table-column>
            <el-table-column prop="materialSpec" label="规格" min-width="120" show-overflow-tooltip />
            <el-table-column prop="unitName" label="单位" min-width="90" />
            <el-table-column label="项目/公共" min-width="190" show-overflow-tooltip>
              <template #default="{ row }">
                {{ procurementOwnershipDisplay({ ...record, ...row }) }}
              </template>
            </el-table-column>
            <el-table-column label="来源" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.requisitionNo || '-' }} / {{ purchaseOrderLinePriceSourceText(row) }}
              </template>
            </el-table-column>
            <el-table-column label="采购数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.quantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="已入库" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.receivedQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="未入库" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.remainingQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="行在途参考" min-width="130" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.inTransitQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="在途状态" min-width="120">
              <template #default="{ row }">
                {{ purchaseInTransitStatusLabel(row.inTransitStatus, row.inTransitStatusName) }}
              </template>
            </el-table-column>
            <el-table-column label="采购单价" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.unitPrice) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="税价" min-width="260">
              <template #default="{ row }">
                {{ purchaseOrderLineTaxText(row, record.currency) }}
              </template>
            </el-table-column>
            <el-table-column label="到货计划" min-width="260">
              <template #default="{ row }">
                <div v-for="schedule in row.schedules ?? []" :key="schedule.id">
                  计划/已入库/剩余：{{ formatProcurementQuantity(schedule.plannedQuantity) }}/{{
                    formatProcurementQuantity(schedule.receivedQuantity)
                  }}/{{ formatProcurementQuantity(schedule.remainingQuantity) }}
                  · {{ schedule.expectedArrivalDate }} · {{ purchaseScheduleStatusLabel(schedule.status, schedule.statusName) }}
                </div>
                <span v-if="!(row.schedules ?? []).length">未拆分到货计划</span>
              </template>
            </el-table-column>
            <el-table-column prop="expectedArrivalDate" label="预计到货" min-width="120" />
            <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
          </el-table>
        </div>
      </section>

      <section class="section-block">
        <div class="section-title">入库记录</div>
        <el-empty v-if="record.receipts.length === 0" description="暂无入库记录" />
        <div v-else class="table-scroll">
          <el-table :data="record.receipts" empty-text="暂无入库记录" stripe>
            <el-table-column prop="receiptNo" label="入库单号" min-width="170" show-overflow-tooltip />
            <el-table-column prop="warehouseName" label="仓库" min-width="130" show-overflow-tooltip />
            <el-table-column prop="businessDate" label="业务日期" min-width="110" />
            <el-table-column label="状态" min-width="90">
              <template #default="{ row }">
                <el-tag :type="purchaseReceiptStatusTagType(row.status)" size="small">
                  {{ purchaseReceiptStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="入库数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.totalQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="postedByName" label="过账人" min-width="100" />
            <el-table-column label="过账时间" min-width="150">
              <template #default="{ row }">
                {{ formatProcurementDateTime(row.postedAt) }}
              </template>
            </el-table-column>
            <el-table-column v-if="canViewReceipt" label="操作" fixed="right" width="184">
              <template #default="{ row }">
                <el-button size="small" text data-test="view-purchase-receipt-summary" @click="viewReceipt(row.id)">
                  详情
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.purchase-order-detail {
  padding: 14px;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
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

.purchase-order-detail-list {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) 96px minmax(0, 1fr);
  gap: 10px 14px;
  margin: 0 0 16px;
}

.purchase-order-detail-list dt {
  color: var(--qherp-muted);
}

.purchase-order-detail-list dd {
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

@media (max-width: 900px) {
  .summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .purchase-order-detail-list {
    grid-template-columns: 88px minmax(0, 1fr);
  }
}
</style>
