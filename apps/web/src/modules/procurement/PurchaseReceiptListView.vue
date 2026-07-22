<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { masterDataApi, type PartnerRecord, type WarehouseRecord } from '../../shared/api/masterDataApi'
import { createIdempotencyKey } from '../../shared/api/documentPlatformApi'
import {
  procurementApi,
  type PurchaseReceiptStatus,
  type PurchaseReceiptSummaryRecord,
} from '../../shared/api/procurementApi'
import { currentRouteReturnTo, queryWithReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import { valuationStateLabel } from '../inventory/inventoryPageHelpers'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import {
  formatProcurementAmount,
  formatProcurementDateTime,
  formatProcurementQuantity,
  normalizeOptionalId,
  procurementErrorMessage,
  procurementModeDisplay,
} from './procurementPageHelpers'
import PurchaseReceiptStatusTag from './PurchaseReceiptStatusTag.vue'
import { confirmAction } from '../../shared/ui/confirmDialog'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const filters = reactive<{
  keyword: string
  supplierId: string | number | ''
  warehouseId: string | number | ''
  status?: PurchaseReceiptStatus
  dateFrom: string
  dateTo: string
  orderId: string | number | ''
}>({
  keyword: '',
  supplierId: '',
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
const suppliers = ref<PartnerRecord[]>([])
const warehouses = ref<WarehouseRecord[]>([])
const records = ref<PurchaseReceiptSummaryRecord[]>([])
const loading = ref(true)
const referenceLoading = ref(true)
const error = ref('')
const referenceError = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const canUpdate = computed(() => authStore.hasPermission('procurement:receipt:update'))
const canPostPermission = computed(() => authStore.hasPermission('procurement:receipt:post'))

async function loadReferences() {
  referenceLoading.value = true
  referenceError.value = ''
  try {
    const [supplierPage, warehousePage] = await Promise.all([
      masterDataApi.suppliers.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
      masterDataApi.warehouses.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
    ])
    suppliers.value = pageItems(supplierPage)
    warehouses.value = pageItems(warehousePage)
  } catch (caught) {
    suppliers.value = []
    warehouses.value = []
    referenceError.value = procurementErrorMessage(caught)
  } finally {
    referenceLoading.value = false
  }
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await procurementApi.receipts.list({
      keyword: filters.keyword,
      supplierId: normalizeOptionalId(filters.supplierId),
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

function viewReceipt(record: PurchaseReceiptSummaryRecord) {
  void router.push({
    name: 'procurement-receipt-detail',
    params: { id: String(record.id) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function editReceipt(record: PurchaseReceiptSummaryRecord) {
  void router.push({ name: 'procurement-receipt-edit', params: { id: String(record.id) } })
}

function allowed(record: PurchaseReceiptSummaryRecord, action: string) {
  return (record.allowedActions ?? []).includes(action)
}

async function postReceipt(record: PurchaseReceiptSummaryRecord) {
  if (actionLoading.value) {
    return
  }
  if (!(await confirmAction(`确认过账采购入库“${record.receiptNo}”？`))) {
    return
  }

  actionError.value = ''
  actionLoading.value = true
  try {
    await procurementApi.receipts.post(record.id, {
      version: record.version,
      idempotencyKey: createIdempotencyKey('purchase-receipt-post'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
    await loadRecords()
  } finally {
    actionLoading.value = false
  }
}

onMounted(() => {
  void loadReferences()
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="采购入库" description="维护采购入库草稿，过账后形成库存入库流水。">
    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            name="purchase-receipt-keyword"
            clearable
            placeholder="入库单号、来源订单或供应商"
          />
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
            name="purchase-receipt-date-from"
            placeholder="起始日期"
          />
        </el-form-item>
        <el-form-item>
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD"
            v-model="filters.dateTo"
            name="purchase-receipt-date-to"
            placeholder="截止日期"
          />
        </el-form-item>
        <el-form-item label="来源订单 ID">
          <el-input
            v-model="filters.orderId"
            name="purchase-receipt-order-id"
            clearable
            placeholder="订单 ID"
          />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-purchase-receipts" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-purchase-receipts" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="referenceError" class="state-alert" type="error" :title="referenceError" :closable="false" />
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert
        v-if="loading || referenceLoading"
        class="state-alert"
        type="info"
        title="采购入库加载中"
        :closable="false"
      />
    </template>

    <el-empty v-if="!loading && records.length === 0" description="暂无采购入库" />
    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无采购入库'" stripe>
        <el-table-column prop="receiptNo" label="入库单号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="orderNo" label="来源订单号" min-width="170" show-overflow-tooltip />
        <el-table-column label="采购模式/项目" min-width="210" show-overflow-tooltip>
          <template #default="{ row }">
            {{ procurementModeDisplay(row.procurementMode, row.projectCode, row.projectName) }}
          </template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商" min-width="160" show-overflow-tooltip />
        <el-table-column prop="warehouseName" label="仓库" min-width="130" show-overflow-tooltip />
        <el-table-column prop="businessDate" label="业务日期" min-width="110" />
        <el-table-column label="状态" min-width="95">
          <template #default="{ row }">
            <PurchaseReceiptStatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="估值/成本" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="stacked-cell">
              <span>估值状态：{{ valuationStateLabel(row.valuationState, row.valuationStateName) }}</span>
              <span v-if="row.costVisible === false">成本无权限</span>
              <span v-else>未税金额 {{ formatProcurementAmount(row.taxExcludedAmount) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="总数量" min-width="100" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatProcurementQuantity(row.totalQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="lineCount" label="行数" min-width="80" />
        <el-table-column prop="createdByName" label="创建人" min-width="100" />
        <el-table-column prop="postedByName" label="过账人" min-width="100" />
        <el-table-column label="过账时间" min-width="150">
          <template #default="{ row }">
            {{ formatProcurementDateTime(row.postedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-purchase-receipt" @click="viewReceipt(row)">详情</el-button>
            <el-button
              v-if="canUpdate && allowed(row, 'UPDATE')"
              size="small"
              text
              data-test="edit-purchase-receipt"
              @click="editReceipt(row)"
            >
              编辑
            </el-button>
            <el-dropdown trigger="click" class="table-actions-more" v-if="(canPostPermission && allowed(row, 'POST'))">
              <el-button size="small" text>更多</el-button>
              <template #dropdown>
                <el-dropdown-menu class="table-actions-more-menu">
                  <el-button
                    v-if="canPostPermission && allowed(row, 'POST')"
                    size="small"
                    text
                    type="success"
                    data-test="post-purchase-receipt"
                    :disabled="actionLoading"
                    @click="postReceipt(row)"
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

.stacked-cell {
  display: grid;
  gap: 2px;
  line-height: 1.35;
}
</style>
