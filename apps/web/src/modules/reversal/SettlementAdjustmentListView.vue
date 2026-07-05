<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  returnRefundReversalApi,
  type SettlementAdjustmentSourceType,
  type SettlementAdjustmentSummary,
  type SettlementAdjustmentType,
  type SettlementSide,
  type ReversalStatus,
} from '../../shared/api/returnRefundReversalApi'
import { useAuthStore } from '../../stores/authStore'
import { financeErrorMessage, financePermissions, formatFinanceAmount } from '../finance/financePageHelpers'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import ReversalStatusTag from './ReversalStatusTag.vue'

const router = useRouter()
const authStore = useAuthStore()
const filters = reactive<{
  keyword: string
  settlementSide?: SettlementSide
  adjustmentType?: SettlementAdjustmentType
  sourceType?: SettlementAdjustmentSourceType
  status?: ReversalStatus
  dateFrom: string
  dateTo: string
}>({
  keyword: '',
  settlementSide: undefined,
  adjustmentType: undefined,
  sourceType: undefined,
  status: undefined,
  dateFrom: '',
  dateTo: '',
})
const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0,
})
const records = ref<SettlementAdjustmentSummary[]>([])
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const canCreate = computed(() => authStore.hasPermission(financePermissions.settlementAdjustmentCreate))
const canUpdate = computed(() => authStore.hasPermission(financePermissions.settlementAdjustmentUpdate))
const canPost = computed(() => authStore.hasPermission(financePermissions.settlementAdjustmentPost))
const canCancel = computed(() => authStore.hasPermission(financePermissions.settlementAdjustmentCancel))

function settlementSideText(value: SettlementSide | string) {
  return value === 'PAYABLE' ? '应付冲减' : '应收冲减'
}

function adjustmentTypeText(value: SettlementAdjustmentType | string) {
  const labels: Record<string, string> = {
    RETURN_OFFSET: '退货冲减',
    REFUND: '退款记录',
    PAYMENT_OFFSET: '付款冲减',
  }
  return labels[value] ?? value
}

function sourceTypeText(value: SettlementAdjustmentSourceType | string) {
  const labels: Record<string, string> = {
    SALES_RETURN: '销售退货',
    PURCHASE_RETURN: '采购退货',
    RECEIPT: '收款记录',
    PAYMENT: '付款记录',
    SETTLEMENT_ADJUSTMENT: '往来冲减',
  }
  return labels[value] ?? value
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await returnRefundReversalApi.settlementAdjustments.list({
      keyword: filters.keyword,
      settlementSide: filters.settlementSide,
      adjustmentType: filters.adjustmentType,
      sourceType: filters.sourceType,
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
    error.value = financeErrorMessage(caught)
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
  filters.settlementSide = undefined
  filters.adjustmentType = undefined
  filters.sourceType = undefined
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

function createSettlementAdjustment() {
  void router.push({ name: 'finance-settlement-adjustment-create' })
}

function viewSettlementAdjustment(record: SettlementAdjustmentSummary) {
  void router.push({ name: 'finance-settlement-adjustment-detail', params: { id: String(record.id) } })
}

function editSettlementAdjustment(record: SettlementAdjustmentSummary) {
  void router.push({ name: 'finance-settlement-adjustment-edit', params: { id: String(record.id) } })
}

async function postSettlementAdjustment(record: SettlementAdjustmentSummary) {
  if (actionLoading.value || !window.confirm(`确认过账往来冲减“${record.adjustmentNo}”？`)) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await returnRefundReversalApi.settlementAdjustments.post(record.id)
    await loadRecords()
  } catch (caught) {
    actionError.value = financeErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function cancelSettlementAdjustment(record: SettlementAdjustmentSummary) {
  if (actionLoading.value || !window.confirm(`确认取消往来冲减“${record.adjustmentNo}”？`)) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await returnRefundReversalApi.settlementAdjustments.cancel(record.id)
    await loadRecords()
  } catch (caught) {
    actionError.value = financeErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(() => {
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="往来冲减" description="维护退款记录和应收、应付冲减草稿，过账后更新目标往来余额。">
    <template #actions>
      <el-button
        v-if="canCreate"
        data-test="create-settlement-adjustment"
        type="primary"
        @click="createSettlementAdjustment"
      >
        新建冲减
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            name="settlement-adjustment-keyword"
            clearable
            placeholder="冲减单号或来源单号"
          />
        </el-form-item>
        <el-form-item label="往来方向">
          <el-select v-model="filters.settlementSide" clearable placeholder="全部方向" style="width: 130px">
            <el-option label="应收冲减" value="RECEIVABLE" />
            <el-option label="应付冲减" value="PAYABLE" />
          </el-select>
        </el-form-item>
        <el-form-item label="冲减类型">
          <el-select v-model="filters.adjustmentType" clearable placeholder="全部类型" style="width: 130px">
            <el-option label="退货冲减" value="RETURN_OFFSET" />
            <el-option label="退款记录" value="REFUND" />
            <el-option label="付款冲减" value="PAYMENT_OFFSET" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源类型">
          <el-select v-model="filters.sourceType" clearable placeholder="全部来源" style="width: 130px">
            <el-option label="销售退货" value="SALES_RETURN" />
            <el-option label="采购退货" value="PURCHASE_RETURN" />
            <el-option label="收款记录" value="RECEIPT" />
            <el-option label="付款记录" value="PAYMENT" />
            <el-option label="往来冲减" value="SETTLEMENT_ADJUSTMENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 120px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已过账" value="POSTED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务日期">
          <el-input v-model="filters.dateFrom" name="settlement-adjustment-date-from" placeholder="起始日期" style="width: 130px" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="filters.dateTo" name="settlement-adjustment-date-to" placeholder="截止日期" style="width: 130px" />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-settlement-adjustments" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-settlement-adjustments" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="往来冲减加载中" :closable="false" />
    </template>

    <el-empty v-if="!loading && records.length === 0" description="暂无往来冲减" />
    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无往来冲减'" stripe>
        <el-table-column prop="adjustmentNo" label="冲减单号" min-width="170" show-overflow-tooltip />
        <el-table-column label="往来方向" min-width="100">
          <template #default="{ row }">{{ settlementSideText(row.settlementSide) }}</template>
        </el-table-column>
        <el-table-column label="冲减类型" min-width="100">
          <template #default="{ row }">{{ adjustmentTypeText(row.adjustmentType) }}</template>
        </el-table-column>
        <el-table-column label="来源" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.source.restricted || !row.source.canViewSource ? row.source.restrictedMessage || '来源无查看权限' : `${sourceTypeText(row.source.sourceType)} ${row.source.sourceNo || ''}` }}
          </template>
        </el-table-column>
        <el-table-column prop="targetNo" label="目标单号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="businessDate" label="业务日期" min-width="110" />
        <el-table-column label="状态" min-width="95">
          <template #default="{ row }">
            <ReversalStatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="冲减金额" min-width="110" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatFinanceAmount(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="过账后余额" min-width="120" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatFinanceAmount(row.targetRemainingAmountAfterPost) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="220">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-settlement-adjustment" @click="viewSettlementAdjustment(row)">详情</el-button>
            <el-button
              v-if="canUpdate && row.status === 'DRAFT'"
              size="small"
              text
              data-test="edit-settlement-adjustment"
              @click="editSettlementAdjustment(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="canPost && row.status === 'DRAFT'"
              size="small"
              text
              type="success"
              data-test="post-settlement-adjustment"
              :disabled="actionLoading"
              @click="postSettlementAdjustment(row)"
            >
              过账
            </el-button>
            <el-button
              v-if="canCancel && row.status === 'DRAFT'"
              size="small"
              text
              type="danger"
              data-test="cancel-settlement-adjustment"
              :disabled="actionLoading"
              @click="cancelSettlementAdjustment(row)"
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
