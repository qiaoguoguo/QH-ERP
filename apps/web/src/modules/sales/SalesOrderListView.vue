<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { masterDataApi, type PartnerRecord } from '../../shared/api/masterDataApi'
import {
  salesApi,
  type SalesOrderStatus,
  type SalesOrderSummaryRecord,
} from '../../shared/api/salesApi'
import { currentRouteReturnTo, queryWithReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import SalesOrderStatusTag from './SalesOrderStatusTag.vue'
import {
  formatSalesDateTime,
  formatSalesQuantity,
  normalizeOptionalId,
  salesErrorMessage,
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
}>({
  keyword: '',
  customerId: '',
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

function canCancel(record: SalesOrderSummaryRecord) {
  return record.status === 'DRAFT' || (record.status === 'CONFIRMED' && Number(record.shippedQuantity) <= 0)
}

function canClose(record: SalesOrderSummaryRecord) {
  return record.status === 'CONFIRMED' || record.status === 'PARTIALLY_SHIPPED' || record.status === 'SHIPPED'
}

function canCreateShipment(record: SalesOrderSummaryRecord) {
  return record.status === 'CONFIRMED' || record.status === 'PARTIALLY_SHIPPED'
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
  try {
    if (action === 'confirm') {
      await salesApi.orders.confirm(record.id)
    } else if (action === 'cancel') {
      await salesApi.orders.cancel(record.id)
    } else {
      await salesApi.orders.close(record.id)
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
  <MasterDataTableView title="销售订单" description="维护销售订单草稿、确认订单并追踪出库进度。">
    <template #actions>
      <el-button v-if="canCreate" data-test="create-sales-order" type="primary" @click="createOrder">
        新建销售订单
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" inline>
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
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无销售订单'" stripe>
        <el-table-column prop="orderNo" label="订单号" min-width="170" show-overflow-tooltip />
        <el-table-column label="客户" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.customerCode }} {{ row.customerName }}
          </template>
        </el-table-column>
        <el-table-column prop="orderDate" label="订单日期" min-width="110" />
        <el-table-column label="预计交付" min-width="110">
          <template #default="{ row }">
            {{ row.expectedShipDate || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="105">
          <template #default="{ row }">
            <SalesOrderStatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="总数量" min-width="100" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatSalesQuantity(row.totalQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="已出库" min-width="100" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatSalesQuantity(row.shippedQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="未出库" min-width="100" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatSalesQuantity(row.remainingQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="lineCount" label="行数" min-width="80" />
        <el-table-column prop="createdByName" label="创建人" min-width="100" />
        <el-table-column label="更新时间" min-width="150">
          <template #default="{ row }">
            {{ formatSalesDateTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="330">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-sales-order" @click="viewOrder(row)">详情</el-button>
            <el-button
              v-if="canUpdate && row.status === 'DRAFT'"
              size="small"
              text
              data-test="edit-sales-order"
              @click="editOrder(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="canConfirm && row.status === 'DRAFT'"
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
.numeric-cell {
  display: inline-block;
  min-width: 76px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
</style>
