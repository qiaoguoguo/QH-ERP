<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  returnRefundReversalApi,
  type ReversalStatus,
  type SalesReturnSummary,
} from '../../shared/api/returnRefundReversalApi'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import { formatSalesAmount, formatSalesQuantity, normalizeOptionalId, salesErrorMessage } from '../sales/salesPageHelpers'
import ReversalStatusTag from './ReversalStatusTag.vue'

const router = useRouter()
const authStore = useAuthStore()
const filters = reactive<{
  keyword: string
  customerId: string | number | ''
  warehouseId: string | number | ''
  status?: ReversalStatus
  dateFrom: string
  dateTo: string
}>({
  keyword: '',
  customerId: '',
  warehouseId: '',
  status: undefined,
  dateFrom: '',
  dateTo: '',
})
const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0,
})
const records = ref<SalesReturnSummary[]>([])
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const canCreate = computed(() => authStore.hasPermission('sales:return:create'))
const canUpdate = computed(() => authStore.hasPermission('sales:return:update'))
const canPost = computed(() => authStore.hasPermission('sales:return:post'))
const canCancel = computed(() => authStore.hasPermission('sales:return:cancel'))

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await returnRefundReversalApi.salesReturns.list({
      keyword: filters.keyword,
      customerId: normalizeOptionalId(filters.customerId),
      warehouseId: normalizeOptionalId(filters.warehouseId),
      status: filters.status,
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
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
  pagination.page = 1
  void loadRecords()
}

function changePage(page: number) {
  pagination.page = page
  void loadRecords()
}

function createSalesReturn() {
  void router.push({ name: 'sales-return-create' })
}

function viewSalesReturn(record: SalesReturnSummary) {
  void router.push({ name: 'sales-return-detail', params: { id: String(record.id) } })
}

function editSalesReturn(record: SalesReturnSummary) {
  void router.push({ name: 'sales-return-edit', params: { id: String(record.id) } })
}

async function postSalesReturn(record: SalesReturnSummary) {
  if (actionLoading.value || !window.confirm(`确认过账销售退货“${record.returnNo}”？`)) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await returnRefundReversalApi.salesReturns.post(record.id)
    await loadRecords()
  } catch (caught) {
    actionError.value = salesErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function cancelSalesReturn(record: SalesReturnSummary) {
  if (actionLoading.value || !window.confirm(`确认取消销售退货“${record.returnNo}”？`)) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await returnRefundReversalApi.salesReturns.cancel(record.id)
    await loadRecords()
  } catch (caught) {
    actionError.value = salesErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(() => {
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="销售退货" description="维护销售出库退货草稿，过账后形成库存反向入库并冲减应收。">
    <template #actions>
      <el-button
        v-if="canCreate"
        data-test="create-sales-return"
        type="primary"
        @click="createSalesReturn"
      >
        新建退货
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            name="sales-return-keyword"
            clearable
            placeholder="退货单号、出库单号或客户"
          />
        </el-form-item>
        <el-form-item label="客户 ID">
          <el-input
            v-model="filters.customerId"
            name="sales-return-customer-id"
            clearable
            placeholder="客户 ID"
            style="width: 120px"
          />
        </el-form-item>
        <el-form-item label="仓库 ID">
          <el-input
            v-model="filters.warehouseId"
            name="sales-return-warehouse-id"
            clearable
            placeholder="仓库 ID"
            style="width: 120px"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 120px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已过账" value="POSTED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务日期">
          <el-input v-model="filters.dateFrom" name="sales-return-date-from" placeholder="起始日期" style="width: 130px" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="filters.dateTo" name="sales-return-date-to" placeholder="截止日期" style="width: 130px" />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-sales-returns" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-sales-returns" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="销售退货加载中" :closable="false" />
    </template>

    <el-empty v-if="!loading && records.length === 0" description="暂无销售退货" />
    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无销售退货'" stripe>
        <el-table-column prop="returnNo" label="退货单号" min-width="170" show-overflow-tooltip />
        <el-table-column label="来源出库" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.source.restricted || !row.source.canViewSource ? row.source.restrictedMessage || '来源无查看权限' : row.source.sourceNo }}
          </template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户" min-width="160" show-overflow-tooltip />
        <el-table-column prop="warehouseName" label="仓库" min-width="130" show-overflow-tooltip />
        <el-table-column prop="businessDate" label="业务日期" min-width="110" />
        <el-table-column label="状态" min-width="95">
          <template #default="{ row }">
            <ReversalStatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="退货数量" min-width="110" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatSalesQuantity(row.totalQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="退货金额" min-width="120" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatSalesAmount(row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="220">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-sales-return" @click="viewSalesReturn(row)">详情</el-button>
            <el-button
              v-if="canUpdate && row.status === 'DRAFT'"
              size="small"
              text
              data-test="edit-sales-return"
              @click="editSalesReturn(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="canPost && row.status === 'DRAFT'"
              size="small"
              text
              type="success"
              data-test="post-sales-return"
              :disabled="actionLoading"
              @click="postSalesReturn(row)"
            >
              过账
            </el-button>
            <el-button
              v-if="canCancel && row.status === 'DRAFT'"
              size="small"
              text
              type="danger"
              data-test="cancel-sales-return"
              :disabled="actionLoading"
              @click="cancelSalesReturn(row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination
      class="table-pagination"
      layout="total, prev, pager, next"
      :total="pagination.total"
      :page-size="pagination.pageSize"
      :current-page="pagination.page"
      @current-change="changePage"
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

.table-scroll {
  overflow-x: auto;
}
</style>
