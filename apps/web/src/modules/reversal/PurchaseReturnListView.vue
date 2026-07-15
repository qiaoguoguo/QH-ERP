<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createIdempotencyKey } from '../../shared/api/documentPlatformApi'
import {
  returnRefundReversalApi,
  type PurchaseReturnSummary,
  type ReversalStatus,
} from '../../shared/api/returnRefundReversalApi'
import { currentRouteReturnTo, queryWithReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { formatProcurementAmount, formatProcurementQuantity, procurementModeDisplay } from '../procurement/procurementPageHelpers'
import { pageItems } from '../system/shared/pageHelpers'
import { normalizeOptionalId, salesErrorMessage } from '../sales/salesPageHelpers'
import ReversalStatusTag from './ReversalStatusTag.vue'
import { confirmAction } from '../../shared/ui/confirmDialog'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const filters = reactive<{
  keyword: string
  supplierId: string | number | ''
  warehouseId: string | number | ''
  status?: ReversalStatus
  dateFrom: string
  dateTo: string
}>({
  keyword: '',
  supplierId: '',
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
const records = ref<PurchaseReturnSummary[]>([])
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const canCreate = computed(() => authStore.hasPermission('procurement:return:create'))
const canUpdate = computed(() => authStore.hasPermission('procurement:return:update'))
const canPost = computed(() => authStore.hasPermission('procurement:return:post'))
const canCancel = computed(() => authStore.hasPermission('procurement:return:cancel'))

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await returnRefundReversalApi.purchaseReturns.list({
      keyword: filters.keyword,
      supplierId: normalizeOptionalId(filters.supplierId),
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
  filters.supplierId = ''
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

function createPurchaseReturn() {
  void router.push({ name: 'procurement-return-create' })
}

function viewPurchaseReturn(record: PurchaseReturnSummary) {
  void router.push({
    name: 'procurement-return-detail',
    params: { id: String(record.id) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function editPurchaseReturn(record: PurchaseReturnSummary) {
  void router.push({ name: 'procurement-return-edit', params: { id: String(record.id) } })
}

function allowed(record: PurchaseReturnSummary, action: string) {
  return (record.allowedActions ?? []).includes(action)
}

function ownershipText(value?: {
  procurementMode?: string | null
  projectCode?: string | null
  projectName?: string | null
} | null) {
  return procurementModeDisplay(
    value?.procurementMode === 'PROJECT' ? 'PROJECT' : value?.procurementMode === 'PUBLIC' ? 'PUBLIC' : undefined,
    value?.projectCode,
    value?.projectName,
  )
}

function costSourceText(value?: {
  originalCostLayerNo?: string | null
  originalValueMovementNo?: string | null
  costVisible?: boolean | null
} | null) {
  if (value?.costVisible === false) {
    return '成本无权限'
  }
  return `原成本层 ${value?.originalCostLayerNo || '-'} / 原价值流水 ${value?.originalValueMovementNo || '-'}`
}

function returnAmountText(value: PurchaseReturnSummary) {
  if (value.costVisible === false) {
    return '成本无权限'
  }
  return formatProcurementAmount(value.totalAmount)
}

async function postPurchaseReturn(record: PurchaseReturnSummary) {
  if (actionLoading.value || !(await confirmAction(`确认过账采购退货“${record.returnNo}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await returnRefundReversalApi.purchaseReturns.post(record.id, {
      version: record.version,
      idempotencyKey: createIdempotencyKey('purchase-return-post'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = salesErrorMessage(caught)
    await loadRecords()
  } finally {
    actionLoading.value = false
  }
}

async function cancelPurchaseReturn(record: PurchaseReturnSummary) {
  if (actionLoading.value || !(await confirmAction(`确认取消采购退货“${record.returnNo}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await returnRefundReversalApi.purchaseReturns.cancel(record.id, {
      version: record.version,
      idempotencyKey: createIdempotencyKey('purchase-return-cancel'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = salesErrorMessage(caught)
    await loadRecords()
  } finally {
    actionLoading.value = false
  }
}

onMounted(() => {
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="采购退货" description="维护采购入库退货草稿，过账后反冲原入库库存与成本，不直接处理应付。">
    <template #actions>
      <el-button
        v-if="canCreate"
        data-test="create-purchase-return"
        type="primary"
        @click="createPurchaseReturn"
      >
        新建退货
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            name="purchase-return-keyword"
            clearable
            placeholder="退货单号、入库单号或供应商"
          />
        </el-form-item>
        <el-form-item label="供应商 ID">
          <el-input
            v-model="filters.supplierId"
            name="purchase-return-supplier-id"
            clearable
            placeholder="供应商 ID"
          />
        </el-form-item>
        <el-form-item label="仓库 ID">
          <el-input
            v-model="filters.warehouseId"
            name="purchase-return-warehouse-id"
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
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.dateFrom" name="purchase-return-date-from" placeholder="起始日期" />
        </el-form-item>
        <el-form-item>
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.dateTo" name="purchase-return-date-to" placeholder="截止日期" />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-purchase-returns" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-purchase-returns" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="采购退货加载中" :closable="false" />
    </template>

    <el-empty v-if="!loading && records.length === 0" description="暂无采购退货" />
    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无采购退货'" stripe>
        <el-table-column prop="returnNo" label="退货单号" min-width="170" show-overflow-tooltip />
        <el-table-column label="来源入库" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.source.restricted || !row.source.canViewSource ? row.source.restrictedMessage || '来源无查看权限' : row.source.sourceNo }}
          </template>
        </el-table-column>
        <el-table-column label="原采购模式/项目" min-width="210" show-overflow-tooltip>
          <template #default="{ row }">
            {{ ownershipText(row) }}
          </template>
        </el-table-column>
        <el-table-column label="原成本来源" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="stacked-cell">
              <span>{{ costSourceText(row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商" min-width="160" show-overflow-tooltip />
        <el-table-column prop="warehouseName" label="仓库" min-width="130" show-overflow-tooltip />
        <el-table-column prop="businessDate" label="业务日期" min-width="110" />
        <el-table-column label="状态" min-width="95">
          <template #default="{ row }">
            <ReversalStatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="退货数量" min-width="110" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatProcurementQuantity(row.totalQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="退货金额" min-width="120" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ returnAmountText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="220">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-purchase-return" @click="viewPurchaseReturn(row)">详情</el-button>
            <el-button
              v-if="canUpdate && allowed(row, 'UPDATE')"
              size="small"
              text
              data-test="edit-purchase-return"
              @click="editPurchaseReturn(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="canPost && allowed(row, 'POST')"
              size="small"
              text
              type="success"
              data-test="post-purchase-return"
              :disabled="actionLoading"
              @click="postPurchaseReturn(row)"
            >
              过账
            </el-button>
            <el-button
              v-if="canCancel && allowed(row, 'CANCEL')"
              size="small"
              text
              type="danger"
              data-test="cancel-purchase-return"
              :disabled="actionLoading"
              @click="cancelPurchaseReturn(row)"
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

.stacked-cell {
  display: grid;
  gap: 2px;
  line-height: 1.35;
}
</style>
