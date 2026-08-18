<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { masterDataApi, type PartnerRecord } from '../../shared/api/masterDataApi'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type DocumentTaskRecord,
} from '../../shared/api/documentPlatformApi'
import {
  procurementApi,
  type PurchaseOrderStatus,
  type PurchaseOrderSummaryRecord,
} from '../../shared/api/procurementApi'
import { currentRouteReturnTo, queryWithReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import PurchaseOrderStatusTag from './PurchaseOrderStatusTag.vue'
import {
  formatProcurementDateTime,
  formatProcurementQuantity,
  normalizeOptionalId,
  procurementErrorMessage,
  procurementOwnershipDisplay,
  purchaseInTransitStatusLabel,
} from './procurementPageHelpers'
import { confirmAction } from '../../shared/ui/confirmDialog'
import ProcurementDocumentTaskPanel from './ProcurementDocumentTaskPanel.vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const filters = reactive<{
  keyword: string
  supplierId: string | number | ''
  status?: PurchaseOrderStatus
  dateFrom: string
  dateTo: string
  expectedDateFrom: string
  expectedDateTo: string
}>({
  keyword: '',
  supplierId: '',
  status: undefined,
  dateFrom: '',
  dateTo: '',
  expectedDateFrom: '',
  expectedDateTo: '',
})
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
})
const suppliers = ref<PartnerRecord[]>([])
const records = ref<PurchaseOrderSummaryRecord[]>([])
const loading = ref(true)
const referenceLoading = ref(true)
const error = ref('')
const referenceError = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const latestDocumentTask = ref<DocumentTaskRecord | null>(null)

const canCreate = computed(() => authStore.hasPermission('procurement:order:create'))
const canUpdate = computed(() => authStore.hasPermission('procurement:order:update'))
const canConfirm = computed(() => authStore.hasPermission('procurement:order:confirm'))
const canCancelPermission = computed(() => authStore.hasPermission('procurement:order:cancel'))
const canClosePermission = computed(() => authStore.hasPermission('procurement:order:close'))
const canCreateReceiptPermission = computed(() => authStore.hasPermission('procurement:receipt:create'))
const canExport = computed(() => (
  authStore.hasPermission('procurement:order:view')
  && authStore.hasPermission('platform:document-task:create')
  && authStore.hasPermission('procurement:document:export')
))

async function loadSuppliers() {
  referenceLoading.value = true
  referenceError.value = ''
  try {
    const page = await masterDataApi.suppliers.list({
      keyword: '',
      status: 'ENABLED',
      page: 1,
      pageSize: 200,
    })
    suppliers.value = pageItems(page)
  } catch (caught) {
    suppliers.value = []
    referenceError.value = procurementErrorMessage(caught)
  } finally {
    referenceLoading.value = false
  }
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await procurementApi.orders.list({
      keyword: filters.keyword,
      supplierId: normalizeOptionalId(filters.supplierId),
      status: filters.status,
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      expectedDateFrom: filters.expectedDateFrom,
      expectedDateTo: filters.expectedDateTo,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = pageItems(page)
    pagination.total = Number(page.total)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = procurementErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function search() {
  pagination.page = 1
  void loadRecords()
}

function resetSearch() {
  filters.keyword = ''
  filters.supplierId = ''
  filters.status = undefined
  filters.dateFrom = ''
  filters.dateTo = ''
  filters.expectedDateFrom = ''
  filters.expectedDateTo = ''
  pagination.page = 1
  void loadRecords()
}

function changePage(page: number) {
  pagination.page = page
  void loadRecords()
}

function changePageSize(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1
  void loadRecords()
}

function createOrder() {
  void router.push({ name: 'procurement-order-create' })
}

async function exportOrders() {
  if (actionLoading.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    latestDocumentTask.value = await documentPlatformApi.exports.createProcurementOrders({
      keyword: filters.keyword,
      supplierId: normalizeOptionalId(filters.supplierId),
      status: filters.status,
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      expectedDateFrom: filters.expectedDateFrom,
      expectedDateTo: filters.expectedDateTo,
      idempotencyKey: createIdempotencyKey('procurement-order-export'),
    })
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
    await loadRecords()
  } finally {
    actionLoading.value = false
  }
}

function viewOrder(record: PurchaseOrderSummaryRecord) {
  void router.push({
    name: 'procurement-order-detail',
    params: { id: String(record.id) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function editOrder(record: PurchaseOrderSummaryRecord) {
  void router.push({ name: 'procurement-order-edit', params: { id: String(record.id) } })
}

function createReceipt(record: PurchaseOrderSummaryRecord) {
  void router.push({ name: 'procurement-receipt-create', params: { orderId: String(record.id) } })
}

function allowed(record: PurchaseOrderSummaryRecord, action: string) {
  return (record.allowedActions ?? []).includes(action)
}

function exceptionApprovalText(record: PurchaseOrderSummaryRecord) {
  if (record.exceptionApprovalStatus === 'NOT_REQUIRED') {
    return ''
  }
  const labels: Record<string, string> = {
    NOT_SUBMITTED: '未提交',
    SUBMITTED: '审批中',
    PENDING: '审批中',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    CANCELLED: '已取消',
    CANCELED: '已取消',
  }
  const status = textValue(record.exceptionApprovalStatus)
  return labels[status] || status || '未提交'
}

type TextRecord = Record<string, unknown>
type PurchaseBasisKind = 'agreement' | 'quote' | 'direct' | 'manual' | 'mixed' | 'unknown'
type PurchaseBasisItem = {
  kind: PurchaseBasisKind
  text: string
}

const purchaseBasisLabels: Record<PurchaseBasisKind, string> = {
  agreement: '价格协议',
  quote: '询价选价',
  direct: '公共直采',
  manual: '手工录价',
  mixed: '混合来源',
  unknown: '采购依据未返回',
}

function textValue(value: unknown) {
  if (value === null || value === undefined) {
    return ''
  }
  return String(value).trim()
}

function fieldText(source: TextRecord, keys: string[]) {
  for (const key of keys) {
    const value = textValue(source[key])
    if (value) {
      return value
    }
  }
  return ''
}

function isMixedValue(value: unknown) {
  return textValue(value).toUpperCase() === 'MIXED'
}

function hasMixedPurchaseBasis(source: TextRecord) {
  return [
    'requisitionNo',
    'purchaseRequisitionNo',
    'sourceRequisitionNo',
    'quoteNo',
    'quotationNo',
    'inquiryNo',
    'selectedQuoteNo',
    'agreementNo',
    'priceAgreementNo',
    'agreementCode',
    'priceSourceType',
  ].some((key) => isMixedValue(source[key]))
}

function sourceKind(value: string): PurchaseBasisKind | '' {
  const source = value.toUpperCase()
  if (source === 'MIXED') {
    return 'mixed'
  }
  if (source === 'PUBLIC_DIRECT' || source === '公共直采') {
    return 'direct'
  }
  if (source === 'MANUAL' || source === '手工录价' || source === '手工录入') {
    return 'manual'
  }
  if (source.includes('AGREEMENT') || source.includes('协议')) {
    return 'agreement'
  }
  if (
    source.includes('QUOTE')
    || source.includes('QUOTATION')
    || source.includes('INQUIRY')
    || source.includes('询价')
    || source.includes('报价')
  ) {
    return 'quote'
  }
  return ''
}

function purchaseBasisItem(kind: PurchaseBasisKind, no = ''): PurchaseBasisItem {
  const label = purchaseBasisLabels[kind]
  return {
    kind,
    text: no && kind !== 'mixed' && kind !== 'unknown' ? `${label} ${no}` : label,
  }
}

function purchaseBasisItemFrom(source: TextRecord): PurchaseBasisItem | null {
  if (hasMixedPurchaseBasis(source)) {
    return purchaseBasisItem('mixed')
  }

  const agreementNo = fieldText(source, ['agreementNo', 'priceAgreementNo', 'agreementCode'])
  const quoteNo = fieldText(source, ['quoteNo', 'quotationNo', 'inquiryNo', 'selectedQuoteNo'])
  const requisitionNo = fieldText(source, ['requisitionNo', 'purchaseRequisitionNo', 'sourceRequisitionNo'])
  const kind = sourceKind(fieldText(source, ['priceSourceType', 'priceSource', 'sourceType', 'basisType']))

  if (kind === 'mixed') {
    return purchaseBasisItem('mixed')
  }
  if (kind === 'manual') {
    return purchaseBasisItem('manual')
  }
  if (kind === 'direct') {
    return purchaseBasisItem('direct', requisitionNo)
  }
  if (kind === 'agreement' || agreementNo) {
    return purchaseBasisItem('agreement', agreementNo)
  }
  if (kind === 'quote' || quoteNo) {
    return purchaseBasisItem('quote', quoteNo)
  }
  return null
}

function uniqueBasisItems(items: PurchaseBasisItem[]) {
  const seen = new Set<string>()
  return items.filter((item) => {
    if (seen.has(item.text)) {
      return false
    }
    seen.add(item.text)
    return true
  })
}

function purchaseBasisItems(record: PurchaseOrderSummaryRecord) {
  const items = [purchaseBasisItemFrom(record as unknown as TextRecord) || purchaseBasisItem('unknown')]
  return uniqueBasisItems(items)
}

function purchaseBasisTitle(record: PurchaseOrderSummaryRecord) {
  const items = purchaseBasisItems(record)
  if (items.some((item) => item.kind === 'mixed') || items.length > 1) {
    return '混合来源'
  }
  return items[0]?.text || purchaseBasisLabels.unknown
}

function purchaseBasisDetailLines(record: PurchaseOrderSummaryRecord) {
  const items = purchaseBasisItems(record)
  const lines: string[] = []
  if (items.some((item) => item.kind === 'mixed')) {
    lines.push('包含多个采购依据，详见明细')
  } else if (items.length > 1) {
    const labels = Array.from(new Set(items.map((item) => purchaseBasisLabels[item.kind])))
    lines.push(`包含：${labels.join('、')}`)
    lines.push(`${items.length} 个依据，详见明细`)
  }
  const reason = fieldText(record as unknown as TextRecord, ['priceSourceReason', 'selectionReason', 'sourceReason'])
  if (reason) {
    lines.push(`选价原因：${reason}`)
  }
  return lines
}

function orderLineCount(record: PurchaseOrderSummaryRecord) {
  const lineCount = Number(record.lineCount)
  if (Number.isFinite(lineCount) && lineCount >= 0) {
    return lineCount
  }
  return null
}

function orderLineCountText(record: PurchaseOrderSummaryRecord) {
  const lineCount = orderLineCount(record)
  return lineCount === null ? '明细数未返回' : `${lineCount}条明细`
}

function quantityValue(value: unknown) {
  const quantity = Number(value)
  return Number.isFinite(quantity) ? quantity : 0
}

function hasRemainingQuantity(record: PurchaseOrderSummaryRecord) {
  return quantityValue(record.remainingQuantity) > 0
}

function isFullyReceived(record: PurchaseOrderSummaryRecord) {
  const status = String(record.status || '').toUpperCase()
  if (status === 'FULLY_RECEIVED' || status === 'RECEIVED') {
    return true
  }
  const totalQuantity = quantityValue(record.totalQuantity)
  return totalQuantity > 0
    && quantityValue(record.remainingQuantity) <= 0
    && quantityValue(record.receivedQuantity) >= totalQuantity
}

function isClosed(record: PurchaseOrderSummaryRecord) {
  return String(record.status || '').toUpperCase() === 'CLOSED'
}

function closeReasonText(record: PurchaseOrderSummaryRecord) {
  return fieldText(record as unknown as TextRecord, ['closeReason'])
}

function arrivalLines(record: PurchaseOrderSummaryRecord) {
  if (isClosed(record)) {
    const lines = ['已关闭']
    const reason = closeReasonText(record)
    if (reason) {
      lines.push(`关闭原因：${reason}`)
    }
    return lines
  }
  if (isFullyReceived(record)) {
    return ['已全部入库']
  }
  const lines = [`计划到货：${record.expectedArrivalDate || '未设置'}`]
  if (hasRemainingQuantity(record)) {
    const nextArrival = record.nextArrivalDate
    lines.push(`下一到货：${nextArrival || '待排期'}`)
  }
  return lines
}

function shouldShowExceptionApproval(record: PurchaseOrderSummaryRecord) {
  const status = textValue(record.exceptionApprovalStatus)
  return Boolean(status && status !== 'NOT_REQUIRED')
}

function statusExceptionLines(record: PurchaseOrderSummaryRecord) {
  const lines: string[] = []
  if (isClosed(record)) {
    const reason = closeReasonText(record)
    if (reason) {
      lines.push(`关闭原因：${reason}`)
    }
  } else if (isFullyReceived(record)) {
    lines.push('已全部入库')
  }
  if (shouldShowExceptionApproval(record)) {
    lines.push(`例外审批：${exceptionApprovalText(record)}`)
    if (record.exceptionReason) {
      lines.push(`例外原因：${record.exceptionReason}`)
    }
  }
  if (lines.length === 0) {
    lines.push('无例外')
  }
  return lines
}

async function runOrderAction(record: PurchaseOrderSummaryRecord, action: 'confirm' | 'cancel' | 'close') {
  if (actionLoading.value) {
    return
  }
  const actionLabels = {
    confirm: '确认',
    cancel: '取消',
    close: '关闭',
  }
  if (!(await confirmAction(`确认${actionLabels[action]}采购订单“${record.orderNo}”？`))) {
    return
  }

  actionError.value = ''
  actionLoading.value = true
  try {
    const actionPayload = {
      version: record.version,
      idempotencyKey: createIdempotencyKey(`purchase-order-${action}`),
    }
    if (action === 'confirm') {
      await procurementApi.orders.confirm(record.id, actionPayload)
    } else if (action === 'cancel') {
      await procurementApi.orders.cancel(record.id, actionPayload)
    } else {
      await procurementApi.orders.close(record.id, actionPayload)
    }
    await loadRecords()
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
    await loadRecords()
  } finally {
    actionLoading.value = false
  }
}

onMounted(() => {
  void loadSuppliers()
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="采购订单" description="维护采购订单草稿、确认订单并追踪入库进度。">
    <template #actions>
      <el-button v-if="canCreate" data-test="create-purchase-order" type="primary" @click="createOrder">
        新建采购订单
      </el-button>
      <el-button v-if="canExport" data-test="export-purchase-orders" :loading="actionLoading" @click="exportOrders">
        当前筛选导出
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="purchase-order-keyword" clearable placeholder="订单号、供应商或物料" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-select
            v-model="filters.supplierId"
            clearable
            filterable
            placeholder="全部供应商"
          >
            <el-option
              v-for="supplier in suppliers"
              :key="supplier.id"
              :label="`${supplier.code} ${supplier.name}`"
              :value="supplier.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已确认" value="CONFIRMED" />
            <el-option label="部分入库" value="PARTIALLY_RECEIVED" />
            <el-option label="全部入库" value="RECEIVED" />
            <el-option label="已关闭" value="CLOSED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="订单日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.dateFrom" name="purchase-order-date-from" placeholder="起始日期" />
        </el-form-item>
        <el-form-item>
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.dateTo" name="purchase-order-date-to" placeholder="截止日期" />
        </el-form-item>
        <el-form-item label="预计到货">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD"
            v-model="filters.expectedDateFrom"
            name="purchase-order-expected-date-from"
            placeholder="起始日期"
          />
        </el-form-item>
        <el-form-item>
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD"
            v-model="filters.expectedDateTo"
            name="purchase-order-expected-date-to"
            placeholder="截止日期"
          />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-purchase-orders" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-purchase-orders" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="referenceError" class="state-alert" type="error" :title="referenceError" :closable="false" />
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading || referenceLoading" class="state-alert" type="info" title="采购订单加载中" :closable="false" />
    </template>

    <ProcurementDocumentTaskPanel :task="latestDocumentTask" />

    <el-empty v-if="!loading && records.length === 0" description="暂无采购订单" />
    <div class="table-scroll">
      <el-table class="purchase-order-table" :data="records" :empty-text="loading ? '加载中' : '暂无采购订单'" stripe>
        <el-table-column label="订单信息" min-width="230" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="stacked-cell">
              <span class="cell-primary">{{ row.orderNo }}</span>
              <span class="cell-muted">订单日期：{{ row.orderDate || '未设置' }}</span>
              <span class="cell-muted">明细：{{ orderLineCountText(row) }}</span>
              <span class="cell-muted">更新：{{ formatProcurementDateTime(row.updatedAt) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="供应商" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="stacked-cell">
              <span class="cell-primary">{{ row.supplierName || row.supplierCode || '未指定供应商' }}</span>
              <span v-if="row.supplierCode" class="cell-muted">{{ row.supplierCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="采购范围" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="stacked-cell">
              <span class="cell-primary">{{ procurementOwnershipDisplay(row) }}</span>
              <span class="cell-muted">采购明细：{{ orderLineCountText(row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="采购依据" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="stacked-cell">
              <span class="cell-primary">{{ purchaseBasisTitle(row) }}</span>
              <span v-for="line in purchaseBasisDetailLines(row)" :key="line" class="cell-muted">
                {{ line }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="履约进度" min-width="260">
          <template #default="{ row }">
            <div class="progress-cell">
              <span class="metric-chip">总量 <strong>{{ formatProcurementQuantity(row.totalQuantity) }}</strong></span>
              <span class="metric-chip">已入库 <strong>{{ formatProcurementQuantity(row.receivedQuantity) }}</strong></span>
              <span class="metric-chip">未入库 <strong>{{ formatProcurementQuantity(row.remainingQuantity) }}</strong></span>
              <span class="metric-chip">在途 <strong>{{ formatProcurementQuantity(row.inTransitQuantity) }}</strong></span>
              <span class="progress-status">
                {{ purchaseInTransitStatusLabel(row.inTransitStatus, row.inTransitStatusName) }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="计划到货" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="stacked-cell">
              <span v-for="line in arrivalLines(row)" :key="line">{{ line }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态/例外" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="stacked-cell">
              <PurchaseOrderStatusTag :status="row.status" />
              <span v-for="line in statusExceptionLines(row)" :key="line" class="cell-muted">
                {{ line }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="190">
          <template #default="{ row }">
            <div class="actions-cell">
              <el-button size="small" text data-test="view-purchase-order" @click="viewOrder(row)">详情</el-button>
              <el-button
                v-if="canUpdate && allowed(row, 'UPDATE')"
                size="small"
                text
                data-test="edit-purchase-order"
                @click="editOrder(row)"
              >
                编辑
              </el-button>
              <el-dropdown trigger="click" class="table-actions-more" v-if="(canConfirm && allowed(row, 'CONFIRM')) || (canCancelPermission && allowed(row, 'CANCEL')) || (canClosePermission && allowed(row, 'CLOSE')) || (canCreateReceiptPermission && allowed(row, 'CREATE_RECEIPT'))">
                <el-button size="small" text>更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu class="table-actions-more-menu">
                    <el-button
                      v-if="canConfirm && allowed(row, 'CONFIRM')"
                      size="small"
                      text
                      type="success"
                      data-test="confirm-purchase-order"
                      :disabled="actionLoading"
                      @click="runOrderAction(row, 'confirm')"
                    >
                      确认
                    </el-button>
                    <el-button
                      v-if="canCancelPermission && allowed(row, 'CANCEL')"
                      size="small"
                      text
                      type="danger"
                      data-test="cancel-purchase-order"
                      :disabled="actionLoading"
                      @click="runOrderAction(row, 'cancel')"
                    >
                      取消
                    </el-button>
                    <el-button
                      v-if="canClosePermission && allowed(row, 'CLOSE')"
                      size="small"
                      text
                      type="warning"
                      data-test="close-purchase-order"
                      :disabled="actionLoading"
                      @click="runOrderAction(row, 'close')"
                    >
                      关闭
                    </el-button>
                    <el-button
                      v-if="canCreateReceiptPermission && allowed(row, 'CREATE_RECEIPT')"
                      size="small"
                      text
                      data-test="create-purchase-receipt"
                      @click="createReceipt(row)"
                    >
                      创建入库
                    </el-button>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination
      class="table-pagination"
      layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]"
      :total="pagination.total"
      :page-size="pagination.pageSize"
      :current-page="pagination.page"
      @current-change="changePage" @size-change="changePageSize"
    />
  </MasterDataTableView>
</template>

<style scoped>
.stacked-cell {
  display: grid;
  gap: 2px;
  line-height: 1.35;
}

.purchase-order-table :deep(.el-table__cell) {
  vertical-align: top;
}

.cell-primary {
  color: #111827;
  font-weight: 600;
}

.cell-muted {
  color: #6b7280;
  font-size: 12px;
}

.progress-cell {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.metric-chip {
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  padding: 2px 8px;
  color: #374151;
  background: #f9fafb;
  font-size: 12px;
}

.metric-chip strong {
  color: #111827;
  font-variant-numeric: tabular-nums;
}

.progress-status {
  grid-column: 1 / -1;
  color: #6b7280;
  font-size: 12px;
}

.actions-cell {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}
</style>
