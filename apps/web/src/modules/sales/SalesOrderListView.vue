<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { masterDataApi, type PartnerRecord } from '../../shared/api/masterDataApi'
import {
  salesApi,
  type SalesOrderAction,
  type SalesOrderStatus,
  type SalesOrderSummaryRecord,
} from '../../shared/api/salesApi'
import { createIdempotencyKey } from '../../shared/api/documentPlatformApi'
import { currentRouteReturnTo, queryWithReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import SalesOrderStatusTag from './SalesOrderStatusTag.vue'
import { formatSalesDecimal, salesSourceChainLabel } from './salesFulfillmentPageHelpers'
import {
  formatSalesDateTime,
  formatSalesQuantity,
  normalizeOptionalId,
  salesErrorMessage,
  salesOrderTaxIncludedAmount,
  salesPriceSourceLabel,
} from './salesPageHelpers'
import { confirmAction } from '../../shared/ui/confirmDialog'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const filters = reactive<{
  keyword: string
  customerId: string | number | ''
  status?: SalesOrderStatus
  dateFrom: string
  dateTo: string
  expectedDateFrom: string
  expectedDateTo: string
  projectId: string | number | ''
  contractId: string | number | ''
  projectLinked?: boolean
}>({
  keyword: '',
  customerId: '',
  status: undefined,
  dateFrom: '',
  dateTo: '',
  expectedDateFrom: '',
  expectedDateTo: '',
  projectId: '',
  contractId: '',
  projectLinked: undefined,
})
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
})
const customers = ref<PartnerRecord[]>([])
const records = ref<SalesOrderSummaryRecord[]>([])
const loading = ref(true)
const referenceLoading = ref(true)
const error = ref('')
const referenceError = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const canCreate = computed(() => authStore.hasPermission('sales:order:create'))
const canUpdate = computed(() => authStore.hasPermission('sales:order:update'))
const canConfirm = computed(() => authStore.hasPermission('sales:order:confirm'))
const canCancelPermission = computed(() => authStore.hasPermission('sales:order:cancel'))
const canClosePermission = computed(() => authStore.hasPermission('sales:order:close'))
const canCreateShipmentPermission = computed(() => authStore.hasPermission('sales:shipment:create'))

async function loadCustomers() {
  referenceLoading.value = true
  referenceError.value = ''
  try {
    const page = await masterDataApi.customers.list({
      keyword: '',
      status: 'ENABLED',
      page: 1,
      pageSize: 200,
    })
    customers.value = pageItems(page)
  } catch (caught) {
    customers.value = []
    referenceError.value = salesErrorMessage(caught)
  } finally {
    referenceLoading.value = false
  }
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await salesApi.orders.list({
      keyword: filters.keyword,
      customerId: normalizeOptionalId(filters.customerId),
      status: filters.status,
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      expectedDateFrom: filters.expectedDateFrom,
      expectedDateTo: filters.expectedDateTo,
      projectId: normalizeOptionalId(filters.projectId),
      contractId: normalizeOptionalId(filters.contractId),
      projectLinked: filters.projectLinked,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = pageItems(page)
    pagination.total = Number(page.total)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = salesErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function search() {
  if (filters.projectLinked !== undefined && (
    normalizeOptionalId(filters.projectId) !== undefined || normalizeOptionalId(filters.contractId) !== undefined
  )) {
    error.value = '项目关联筛选不能与项目或合同同时使用'
    return
  }
  pagination.page = 1
  void loadRecords()
}

function resetSearch() {
  filters.keyword = ''
  filters.customerId = ''
  filters.status = undefined
  filters.dateFrom = ''
  filters.dateTo = ''
  filters.expectedDateFrom = ''
  filters.expectedDateTo = ''
  filters.projectId = ''
  filters.contractId = ''
  filters.projectLinked = undefined
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
  void router.push({ name: 'sales-order-create' })
}

function viewOrder(record: SalesOrderSummaryRecord) {
  void router.push({
    name: 'sales-order-detail',
    params: { id: String(record.id) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function editOrder(record: SalesOrderSummaryRecord) {
  void router.push({ name: 'sales-order-edit', params: { id: String(record.id) } })
}

function createShipment(record: SalesOrderSummaryRecord) {
  void router.push({ name: 'sales-shipment-create', params: { orderId: String(record.id) } })
}

function hasAllowedAction(record: SalesOrderSummaryRecord, action: SalesOrderAction) {
  return (record.allowedActions ?? []).includes(action)
}

function canCancel(record: SalesOrderSummaryRecord) {
  return hasAllowedAction(record, 'CANCEL')
}

function canClose(record: SalesOrderSummaryRecord) {
  return hasAllowedAction(record, 'CLOSE')
}

function canCreateShipment(record: SalesOrderSummaryRecord) {
  return hasAllowedAction(record, 'CREATE_SHIPMENT')
}

function sourceSummary(record: SalesOrderSummaryRecord) {
  if (record.priceSourceType || record.priceSourceNo) {
    return salesPriceSourceLabel(record)
  }
  if (record.sourceQuoteNo) {
    return `报价带入 ${record.sourceQuoteNo}`
  }
  if (record.sourceQuoteId) {
    return '报价带入'
  }
  return '手工录入'
}

function quoteSummary(record: SalesOrderSummaryRecord) {
  if (record.sourceQuoteNo) {
    return `报价单：${record.sourceQuoteNo}`
  }
  if (record.sourceQuoteId) {
    return `报价单 ID：${record.sourceQuoteId}`
  }
  return ''
}

function sourceChainSummary(record: SalesOrderSummaryRecord) {
  return salesSourceChainLabel(Boolean(record.sourceQuoteId))
}

function projectSummary(record: SalesOrderSummaryRecord) {
  if (record.contractRestricted) {
    return '合同信息受限'
  }
  if (record.projectId) {
    return `${record.projectNo ?? '项目未返回'} ${record.projectName ?? ''}`.trim()
  }
  return '未关联项目'
}

function contractSummary(record: SalesOrderSummaryRecord) {
  if (record.contractRestricted) {
    return '合同信息受限'
  }
  const contractNo = record.externalContractNo || record.contractNo
  return contractNo ? `合同 ${contractNo}` : '未关联合同'
}

function amountSummary(record: SalesOrderSummaryRecord) {
  if (record.amountRestricted) {
    return '金额受限'
  }
  return `${formatSalesDecimal(salesOrderTaxIncludedAmount(record))} ${record.currency ?? 'CNY'}`
}

function taxSummary(record: SalesOrderSummaryRecord) {
  if (record.amountRestricted) {
    return '税额受限'
  }
  return `未税 ${formatSalesDecimal(record.taxExcludedAmount)} / 税额 ${formatSalesDecimal(record.taxAmount)}`
}

function creditSummary(record: SalesOrderSummaryRecord) {
  if (record.creditRestricted) {
    return '信用信息受限'
  }
  if (record.creditStatusName) {
    return record.creditStatusName
  }
  if (['CANCELLED', 'CLOSED', 'SHIPPED'].includes(record.status)) {
    return '不占用信用'
  }
  if (record.status === 'DRAFT') {
    return '待确认检查'
  }
  return '信用状态待同步'
}

function creditTagType(record: SalesOrderSummaryRecord) {
  const text = creditSummary(record)
  if (['信用正常', '额度充足', '不占用信用'].includes(text)) {
    return 'success'
  }
  if (['信用档案缺失', '信用冻结或停用', '逾期阻断', '额度不足'].includes(text)) {
    return 'danger'
  }
  return 'warning'
}

function shipmentProgressSummary(record: SalesOrderSummaryRecord) {
  return `${formatSalesQuantity(record.shippedQuantity)} / ${formatSalesQuantity(record.totalQuantity)}`
}

function remainingSummary(record: SalesOrderSummaryRecord) {
  return `未出库 ${formatSalesQuantity(record.remainingQuantity)}`
}

async function runOrderAction(record: SalesOrderSummaryRecord, action: 'confirm' | 'cancel' | 'close') {
  if (actionLoading.value) {
    return
  }
  const actionLabels = {
    confirm: '确认',
    cancel: '取消',
    close: '关闭',
  }
  if (!(await confirmAction(`确认${actionLabels[action]}销售订单“${record.orderNo}”？`))) {
    return
  }

  actionError.value = ''
  actionLoading.value = true
  const payload = {
    version: record.version,
    idempotencyKey: createIdempotencyKey(`sales-order-${action}`),
    ...(action === 'cancel' ? { reason: '客户取消' } : {}),
    ...(action === 'close' ? { reason: '履约完成' } : {}),
  }
  try {
    if (action === 'confirm') {
      await salesApi.orders.confirm(record.id, payload)
    } else if (action === 'cancel') {
      await salesApi.orders.cancel(record.id, payload)
    } else {
      await salesApi.orders.close(record.id, payload)
    }
    await loadRecords()
  } catch (caught) {
    actionError.value = salesErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(() => {
  void loadCustomers()
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="销售订单" description="维护销售订单草稿，确认后形成有效销售需求并追踪出库进度。">
    <template #actions>
      <el-button v-if="canCreate" data-test="create-sales-order" type="primary" @click="createOrder">
        新建销售订单
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="sales-order-keyword" clearable placeholder="订单号、客户或物料" />
        </el-form-item>
        <el-form-item label="客户">
          <el-select
            v-model="filters.customerId"
            clearable
            filterable
            placeholder="全部客户"
          >
            <el-option
              v-for="customer in customers"
              :key="customer.id"
              :label="`${customer.code} ${customer.name}`"
              :value="customer.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已确认" value="CONFIRMED" />
            <el-option label="部分出库" value="PARTIALLY_SHIPPED" />
            <el-option label="全部出库" value="SHIPPED" />
            <el-option label="已关闭" value="CLOSED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="订单日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.dateFrom" name="sales-order-date-from" placeholder="起始日期" />
        </el-form-item>
        <el-form-item>
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.dateTo" name="sales-order-date-to" placeholder="截止日期" />
        </el-form-item>
        <el-form-item label="预计交付">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD"
            v-model="filters.expectedDateFrom"
            name="sales-order-expected-date-from"
            placeholder="起始日期"
          />
        </el-form-item>
        <el-form-item>
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD"
            v-model="filters.expectedDateTo"
            name="sales-order-expected-date-to"
            placeholder="截止日期"
          />
        </el-form-item>
        <el-form-item label="项目 ID">
          <el-input v-model="filters.projectId" name="sales-order-project-id" clearable placeholder="项目标识" />
        </el-form-item>
        <el-form-item label="合同 ID">
          <el-input v-model="filters.contractId" name="sales-order-contract-id" clearable placeholder="合同标识" />
        </el-form-item>
        <el-form-item label="项目关联">
          <el-select v-model="filters.projectLinked" clearable placeholder="全部">
            <el-option label="已关联项目" :value="true" />
            <el-option label="未关联项目" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-sales-orders" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-sales-orders" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="referenceError" class="state-alert" type="error" :title="referenceError" :closable="false" />
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading || referenceLoading" class="state-alert" type="info" title="销售订单加载中" :closable="false" />
    </template>

    <el-empty v-if="!loading && records.length === 0" description="暂无销售订单" />
    <div class="table-scroll">
      <el-table class="sales-order-table" :data="records" :empty-text="loading ? '加载中' : '暂无销售订单'" stripe>
        <el-table-column label="订单 / 客户" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="order-main-cell">
              <strong>{{ row.orderNo }}</strong>
              <span>{{ row.customerCode }} {{ row.customerName }}</span>
              <small>创建人：{{ row.createdByName || '-' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="日期" min-width="150">
          <template #default="{ row }">
            <div class="stack-cell">
              <span>订单：{{ row.orderDate || '-' }}</span>
              <span>交付：{{ row.expectedShipDate || '-' }}</span>
              <small>更新：{{ formatSalesDateTime(row.updatedAt) }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="履约进度" min-width="180">
          <template #default="{ row }">
            <div class="stack-cell">
              <SalesOrderStatusTag :status="row.status" />
              <strong>{{ shipmentProgressSummary(row) }}</strong>
              <small>{{ remainingSummary(row) }} / {{ row.lineCount }} 行</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="来源 / 价格" min-width="210" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="stack-cell">
              <strong>{{ sourceChainSummary(row) }}</strong>
              <span>{{ sourceSummary(row) }}</span>
              <small v-if="quoteSummary(row)">{{ quoteSummary(row) }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="项目 / 合同" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="stack-cell">
              <strong>{{ projectSummary(row) }}</strong>
              <span>{{ contractSummary(row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="金额 / 信用" min-width="220" align="right">
          <template #default="{ row }">
            <div class="amount-credit-cell">
              <strong>{{ amountSummary(row) }}</strong>
              <small>{{ taxSummary(row) }}</small>
              <el-tag size="small" :type="creditTagType(row)" effect="plain">
                {{ creditSummary(row) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="190">
          <template #default="{ row }">
            <div class="actions-cell">
            <el-button size="small" text data-test="view-sales-order" @click="viewOrder(row)">详情</el-button>
            <el-button
              v-if="canUpdate && hasAllowedAction(row, 'UPDATE')"
              size="small"
              text
              data-test="edit-sales-order"
              @click="editOrder(row)"
            >
              编辑
            </el-button>
            <el-dropdown trigger="click" class="table-actions-more" v-if="(canConfirm && hasAllowedAction(row, 'CONFIRM')) || (canCancelPermission && canCancel(row)) || (canClosePermission && canClose(row)) || (canCreateShipmentPermission && canCreateShipment(row))">
              <el-button size="small" text>更多</el-button>
              <template #dropdown>
                <el-dropdown-menu class="table-actions-more-menu">
                  <el-button
                    v-if="canConfirm && hasAllowedAction(row, 'CONFIRM')"
                    size="small"
                    text
                    type="success"
                    data-test="confirm-sales-order"
                    :disabled="actionLoading"
                    @click="runOrderAction(row, 'confirm')"
                  >
                    确认
                  </el-button>
                  <el-button
                    v-if="canCancelPermission && canCancel(row)"
                    size="small"
                    text
                    type="danger"
                    data-test="cancel-sales-order"
                    :disabled="actionLoading"
                    @click="runOrderAction(row, 'cancel')"
                  >
                    取消
                  </el-button>
                  <el-button
                    v-if="canClosePermission && canClose(row)"
                    size="small"
                    text
                    type="warning"
                    data-test="close-sales-order"
                    :disabled="actionLoading"
                    @click="runOrderAction(row, 'close')"
                  >
                    关闭
                  </el-button>
                  <el-button
                    v-if="canCreateShipmentPermission && canCreateShipment(row)"
                    size="small"
                    text
                    data-test="create-sales-shipment"
                    @click="createShipment(row)"
                  >
                    创建出库
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
.sales-order-table :deep(.el-table__cell) {
  vertical-align: top;
}

.order-main-cell,
.stack-cell,
.amount-credit-cell {
  display: grid;
  gap: 4px;
  line-height: 1.45;
}

.order-main-cell strong,
.stack-cell strong,
.amount-credit-cell strong {
  color: #111827;
  font-weight: 700;
}

.order-main-cell span,
.stack-cell span,
.order-main-cell small,
.stack-cell small,
.amount-credit-cell small {
  color: #64748b;
}

.amount-credit-cell {
  justify-items: end;
  font-variant-numeric: tabular-nums;
}

.actions-cell {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: flex-end;
}
</style>
