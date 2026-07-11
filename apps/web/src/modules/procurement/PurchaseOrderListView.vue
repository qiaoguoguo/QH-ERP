<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { masterDataApi, type PartnerRecord } from '../../shared/api/masterDataApi'
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
} from './procurementPageHelpers'
import { confirmAction } from '../../shared/ui/confirmDialog'

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

const canCreate = computed(() => authStore.hasPermission('procurement:order:create'))
const canUpdate = computed(() => authStore.hasPermission('procurement:order:update'))
const canConfirm = computed(() => authStore.hasPermission('procurement:order:confirm'))
const canCancelPermission = computed(() => authStore.hasPermission('procurement:order:cancel'))
const canClosePermission = computed(() => authStore.hasPermission('procurement:order:close'))
const canCreateReceiptPermission = computed(() => authStore.hasPermission('procurement:receipt:create'))

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

function canCancel(record: PurchaseOrderSummaryRecord) {
  return record.status === 'DRAFT' || (record.status === 'CONFIRMED' && Number(record.receivedQuantity) <= 0)
}

function canClose(record: PurchaseOrderSummaryRecord) {
  return record.status === 'CONFIRMED' || record.status === 'PARTIALLY_RECEIVED' || record.status === 'RECEIVED'
}

function canCreateReceipt(record: PurchaseOrderSummaryRecord) {
  return record.status === 'CONFIRMED' || record.status === 'PARTIALLY_RECEIVED'
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
    if (action === 'confirm') {
      await procurementApi.orders.confirm(record.id)
    } else if (action === 'cancel') {
      await procurementApi.orders.cancel(record.id)
    } else {
      await procurementApi.orders.close(record.id)
    }
    await loadRecords()
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
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
    </template>

    <template #filters>
      <el-form class="query-form" inline>
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

    <el-empty v-if="!loading && records.length === 0" description="暂无采购订单" />
    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无采购订单'" stripe>
        <el-table-column prop="orderNo" label="订单号" min-width="170" show-overflow-tooltip />
        <el-table-column label="供应商" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.supplierCode }} {{ row.supplierName }}
          </template>
        </el-table-column>
        <el-table-column prop="orderDate" label="订单日期" min-width="110" />
        <el-table-column label="预计到货" min-width="110">
          <template #default="{ row }">
            {{ row.expectedArrivalDate || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="105">
          <template #default="{ row }">
            <PurchaseOrderStatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="总数量" min-width="100" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatProcurementQuantity(row.totalQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="已入库" min-width="100" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatProcurementQuantity(row.receivedQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="未入库" min-width="100" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatProcurementQuantity(row.remainingQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="采购在途参考" min-width="130" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatProcurementQuantity(row.inTransitQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="在途状态" min-width="120">
          <template #default="{ row }">
            {{ row.inTransitStatusName || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="lineCount" label="行数" min-width="80" />
        <el-table-column prop="createdByName" label="创建人" min-width="100" />
        <el-table-column label="更新时间" min-width="150">
          <template #default="{ row }">
            {{ formatProcurementDateTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="330">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-purchase-order" @click="viewOrder(row)">详情</el-button>
            <el-button
              v-if="canUpdate && row.status === 'DRAFT'"
              size="small"
              text
              data-test="edit-purchase-order"
              @click="editOrder(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="canConfirm && row.status === 'DRAFT'"
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
              v-if="canCancelPermission && canCancel(row)"
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
              v-if="canClosePermission && canClose(row)"
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
              v-if="canCreateReceiptPermission && canCreateReceipt(row)"
              size="small"
              text
              data-test="create-purchase-receipt"
              @click="createReceipt(row)"
            >
              创建入库
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
