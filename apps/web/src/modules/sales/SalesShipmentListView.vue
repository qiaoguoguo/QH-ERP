<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { masterDataApi, type PartnerRecord, type WarehouseRecord } from '../../shared/api/masterDataApi'
import {
  salesApi,
  type SalesShipmentAction,
  type SalesShipmentStatus,
  type SalesShipmentSummaryRecord,
} from '../../shared/api/salesApi'
import { currentRouteReturnTo, queryWithReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import {
  formatSalesDateTime,
  formatSalesQuantity,
  normalizeOptionalId,
  salesErrorMessage,
} from './salesPageHelpers'
import SalesShipmentStatusTag from './SalesShipmentStatusTag.vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const filters = reactive<{
  keyword: string
  customerId: string | number | ''
  warehouseId: string | number | ''
  status?: SalesShipmentStatus
  dateFrom: string
  dateTo: string
  orderId: string | number | ''
}>({
  keyword: '',
  customerId: '',
  warehouseId: '',
  status: undefined,
  dateFrom: '',
  dateTo: '',
  orderId: '',
})
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
})
const customers = ref<PartnerRecord[]>([])
const warehouses = ref<WarehouseRecord[]>([])
const records = ref<SalesShipmentSummaryRecord[]>([])
const loading = ref(true)
const referenceLoading = ref(true)
const error = ref('')
const referenceError = ref('')

const canUpdate = computed(() => authStore.hasPermission('sales:shipment:update'))
const canPostPermission = computed(() => authStore.hasPermission('sales:shipment:post'))

async function loadReferences() {
  referenceLoading.value = true
  referenceError.value = ''
  try {
    const [customerPage, warehousePage] = await Promise.all([
      masterDataApi.customers.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
      masterDataApi.warehouses.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
    ])
    customers.value = pageItems(customerPage)
    warehouses.value = pageItems(warehousePage)
  } catch (caught) {
    customers.value = []
    warehouses.value = []
    referenceError.value = salesErrorMessage(caught)
  } finally {
    referenceLoading.value = false
  }
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await salesApi.shipments.list({
      keyword: filters.keyword,
      customerId: normalizeOptionalId(filters.customerId),
      warehouseId: normalizeOptionalId(filters.warehouseId),
      status: filters.status,
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      orderId: normalizeOptionalId(filters.orderId),
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
  filters.warehouseId = ''
  filters.status = undefined
  filters.dateFrom = ''
  filters.dateTo = ''
  filters.orderId = ''
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

function viewShipment(record: SalesShipmentSummaryRecord) {
  void router.push({
    name: 'sales-shipment-detail',
    params: { id: String(record.id) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function editShipment(record: SalesShipmentSummaryRecord) {
  void router.push({ name: 'sales-shipment-edit', params: { id: String(record.id) } })
}

function hasAllowedAction(record: SalesShipmentSummaryRecord, action: SalesShipmentAction) {
  return (record.allowedActions ?? []).includes(action)
}

function postShipment(record: SalesShipmentSummaryRecord) {
  viewShipment(record)
}

onMounted(() => {
  void loadReferences()
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="销售出库" description="维护销售出库草稿，过账后扣减库存并形成销售出库流水。">
    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            name="sales-shipment-keyword"
            clearable
            placeholder="出库单号、来源订单或客户"
          />
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
        <el-form-item label="仓库">
          <el-select
            v-model="filters.warehouseId"
            clearable
            filterable
            placeholder="全部仓库"
          >
            <el-option
              v-for="warehouse in warehouses"
              :key="warehouse.id"
              :label="`${warehouse.code} ${warehouse.name}`"
              :value="warehouse.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已过账" value="POSTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD"
            v-model="filters.dateFrom"
            name="sales-shipment-date-from"
            placeholder="起始日期"
          />
        </el-form-item>
        <el-form-item>
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD"
            v-model="filters.dateTo"
            name="sales-shipment-date-to"
            placeholder="截止日期"
          />
        </el-form-item>
        <el-form-item label="来源订单 ID">
          <el-input
            v-model="filters.orderId"
            name="sales-shipment-order-id"
            clearable
            placeholder="订单 ID"
          />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-sales-shipments" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-sales-shipments" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="referenceError" class="state-alert" type="error" :title="referenceError" :closable="false" />
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert
        v-if="loading || referenceLoading"
        class="state-alert"
        type="info"
        title="销售出库加载中"
        :closable="false"
      />
    </template>

    <el-empty v-if="!loading && records.length === 0" description="暂无销售出库" />
    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无销售出库'" stripe>
        <el-table-column prop="shipmentNo" label="出库单号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="orderNo" label="来源订单号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="customerName" label="客户" min-width="160" show-overflow-tooltip />
        <el-table-column prop="warehouseName" label="仓库" min-width="130" show-overflow-tooltip />
        <el-table-column prop="businessDate" label="业务日期" min-width="110" />
        <el-table-column label="状态" min-width="95">
          <template #default="{ row }">
            <SalesShipmentStatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="总数量" min-width="100" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatSalesQuantity(row.totalQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="lineCount" label="行数" min-width="80" />
        <el-table-column prop="createdByName" label="创建人" min-width="100" />
        <el-table-column prop="postedByName" label="过账人" min-width="100" />
        <el-table-column label="过账时间" min-width="150">
          <template #default="{ row }">
            {{ formatSalesDateTime(row.postedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-sales-shipment" @click="viewShipment(row)">详情</el-button>
            <el-button
              v-if="canUpdate && hasAllowedAction(row, 'UPDATE')"
              size="small"
              text
              data-test="edit-sales-shipment"
              @click="editShipment(row)"
            >
              编辑
            </el-button>
            <el-dropdown trigger="click" class="table-actions-more" v-if="(canPostPermission && hasAllowedAction(row, 'POST'))">
              <el-button size="small" text>更多</el-button>
              <template #dropdown>
                <el-dropdown-menu class="table-actions-more-menu">
                  <el-button
                    v-if="canPostPermission && hasAllowedAction(row, 'POST')"
                    size="small"
                    text
                    type="success"
                    data-test="post-sales-shipment"
                    @click="postShipment(row)"
                  >
                    过账
                  </el-button>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
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
  min-width: 72px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
</style>
