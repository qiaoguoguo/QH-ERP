<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  returnRefundReversalApi,
  type ProductionMaterialReturnSummary,
  type ReversalStatus,
} from '../../shared/api/returnRefundReversalApi'
import { createIdempotencyKey } from '../../shared/api/documentPlatformApi'
import { currentRouteReturnTo, queryWithReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import { formatSalesAmount, normalizeOptionalId } from '../sales/salesPageHelpers'
import { formatProductionQuantity, productionErrorMessage } from '../production/productionPageHelpers'
import ReversalStatusTag from './ReversalStatusTag.vue'
import { confirmAction } from '../../shared/ui/confirmDialog'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const filters = reactive<{
  keyword: string
  workOrderId: string | number | ''
  warehouseId: string | number | ''
  status?: ReversalStatus
  dateFrom: string
  dateTo: string
}>({
  keyword: '',
  workOrderId: '',
  warehouseId: '',
  status: undefined,
  dateFrom: '',
  dateTo: '',
})
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
})
const records = ref<ProductionMaterialReturnSummary[]>([])
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const canCreate = computed(() => authStore.hasPermission('production:material-return:create'))
const canUpdate = computed(() => authStore.hasPermission('production:material-return:update'))
const canPost = computed(() => authStore.hasPermission('production:material-return:post'))
const canCancel = computed(() => authStore.hasPermission('production:material-return:cancel'))

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await returnRefundReversalApi.productionMaterialReturns.list({
      keyword: filters.keyword,
      workOrderId: normalizeOptionalId(filters.workOrderId),
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
    error.value = productionErrorMessage(caught)
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
  filters.workOrderId = ''
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

function changePageSize(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1
  void loadRecords()
}

function createMaterialReturn() {
  void router.push({ name: 'production-material-return-create' })
}

function viewMaterialReturn(record: ProductionMaterialReturnSummary) {
  void router.push({
    name: 'production-material-return-detail',
    params: { id: String(record.id) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function editMaterialReturn(record: ProductionMaterialReturnSummary) {
  void router.push({ name: 'production-material-return-edit', params: { id: String(record.id) } })
}

async function postMaterialReturn(record: ProductionMaterialReturnSummary) {
  if (actionLoading.value || !(await confirmAction(`确认过账生产退料“${record.returnNo}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await returnRefundReversalApi.productionMaterialReturns.post(record.id, {
      version: record.version,
      idempotencyKey: createIdempotencyKey('production-material-return-post'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = productionErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function cancelMaterialReturn(record: ProductionMaterialReturnSummary) {
  if (actionLoading.value || !(await confirmAction(`确认取消生产退料“${record.returnNo}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await returnRefundReversalApi.productionMaterialReturns.cancel(record.id, {
      version: record.version,
      idempotencyKey: createIdempotencyKey('production-material-return-cancel'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = productionErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(() => {
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="生产退料" description="维护生产领料退回草稿，过账后形成库存入库和成本冲减。">
    <template #actions>
      <el-button
        v-if="canCreate"
        data-test="create-material-return"
        type="primary"
        @click="createMaterialReturn"
      >
        新建退料
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            name="material-return-keyword"
            clearable
            placeholder="退料单号、领料单号或工单"
          />
        </el-form-item>
        <el-form-item label="工单 ID">
          <el-input
            v-model="filters.workOrderId"
            name="material-return-work-order-id"
            clearable
            placeholder="工单 ID"
          />
        </el-form-item>
        <el-form-item label="仓库 ID">
          <el-input
            v-model="filters.warehouseId"
            name="material-return-warehouse-id"
            clearable
            placeholder="仓库 ID"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已过账" value="POSTED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.dateFrom" name="material-return-date-from" placeholder="起始日期" />
        </el-form-item>
        <el-form-item>
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.dateTo" name="material-return-date-to" placeholder="截止日期" />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-material-returns" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-material-returns" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="生产退料加载中" :closable="false" />
    </template>

    <el-empty v-if="!loading && records.length === 0" description="暂无生产退料" />
    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无生产退料'" stripe>
        <el-table-column prop="returnNo" label="退料单号" min-width="170" show-overflow-tooltip />
        <el-table-column label="来源领料" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.source.restricted || !row.source.canViewSource ? row.source.restrictedMessage || '来源无查看权限' : row.source.sourceNo }}
          </template>
        </el-table-column>
        <el-table-column prop="workOrderNo" label="生产工单" min-width="160" show-overflow-tooltip />
        <el-table-column prop="warehouseName" label="仓库" min-width="120" show-overflow-tooltip />
        <el-table-column prop="businessDate" label="业务日期" min-width="110" />
        <el-table-column label="状态" min-width="95">
          <template #default="{ row }">
            <ReversalStatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="退料数量" min-width="110" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatProductionQuantity(row.totalQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="影响金额" min-width="120" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatSalesAmount(row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="220">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-material-return" @click="viewMaterialReturn(row)">详情</el-button>
            <el-button
              v-if="canUpdate && row.status === 'DRAFT'"
              size="small"
              text
              data-test="edit-material-return"
              @click="editMaterialReturn(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="canPost && row.status === 'DRAFT'"
              size="small"
              text
              type="success"
              data-test="post-material-return"
              :disabled="actionLoading"
              @click="postMaterialReturn(row)"
            >
              过账
            </el-button>
            <el-button
              v-if="canCancel && row.status === 'DRAFT'"
              size="small"
              text
              type="danger"
              data-test="cancel-material-return"
              :disabled="actionLoading"
              @click="cancelMaterialReturn(row)"
            >
              取消
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
  min-width: 72px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.table-scroll {
  overflow-x: auto;
}
</style>
