<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { masterDataApi, type PartnerRecord } from '../../shared/api/masterDataApi'
import { financeApi, type ReceiptStatus, type ReceiptSummaryRecord } from '../../shared/api/financeApi'
import { currentRouteReturnTo, queryWithReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import ReceiptStatusTag from './ReceiptStatusTag.vue'
import { financeErrorMessage, financeMethodText, financePermissions, formatFinanceAmount, normalizeOptionalId } from './financePageHelpers'
import { confirmAction } from '../../shared/ui/confirmDialog'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const filters = reactive<{
  keyword: string
  customerId: string | number | ''
  status?: ReceiptStatus
  dateFrom: string
  dateTo: string
  receivableId: string
}>({
  keyword: '',
  customerId: '',
  status: undefined,
  dateFrom: '',
  dateTo: '',
  receivableId: '',
})
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const customers = ref<PartnerRecord[]>([])
const records = ref<ReceiptSummaryRecord[]>([])
const loading = ref(true)
const actionLoading = ref(false)
const referenceLoading = ref(true)
const error = ref('')
const referenceError = ref('')

async function loadCustomers() {
  referenceLoading.value = true
  referenceError.value = ''
  try {
    customers.value = pageItems(await masterDataApi.customers.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }))
  } catch (caught) {
    customers.value = []
    referenceError.value = financeErrorMessage(caught)
  } finally {
    referenceLoading.value = false
  }
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await financeApi.receipts.list({
      keyword: filters.keyword,
      customerId: normalizeOptionalId(filters.customerId),
      status: filters.status,
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      receivableId: normalizeOptionalId(filters.receivableId),
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
  filters.customerId = ''
  filters.status = undefined
  filters.dateFrom = ''
  filters.dateTo = ''
  filters.receivableId = ''
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

function viewReceipt(record: ReceiptSummaryRecord) {
  void router.push({
    name: 'finance-receipt-detail',
    params: { id: String(record.id) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function editReceipt(record: ReceiptSummaryRecord) {
  void router.push({ name: 'finance-receipt-edit', params: { id: String(record.id) } })
}

function canUpdateReceipt(record: ReceiptSummaryRecord) {
  return record.status === 'DRAFT' && authStore.hasPermission(financePermissions.receiptUpdate)
}

function canPostReceipt(record: ReceiptSummaryRecord) {
  return record.status === 'DRAFT' && authStore.hasPermission(financePermissions.receiptPost)
}

function canCancelReceipt(record: ReceiptSummaryRecord) {
  return record.status === 'DRAFT' && authStore.hasPermission(financePermissions.receiptCancel)
}

async function runReceiptAction(record: ReceiptSummaryRecord, action: 'post' | 'cancel') {
  if (actionLoading.value) {
    return
  }
  const label = action === 'post' ? '过账' : '取消'
  if (!(await confirmAction(`确认${label}收款“${record.receiptNo}”，金额 ${formatFinanceAmount(record.amount)}？`))) {
    return
  }
  actionLoading.value = true
  error.value = ''
  try {
    if (action === 'post') {
      await financeApi.receipts.post(record.id)
    } else {
      await financeApi.receipts.cancel(record.id)
    }
    await loadRecords()
  } catch (caught) {
    error.value = financeErrorMessage(caught)
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
  <MasterDataTableView title="收款记录" description="扫描收款草稿和已过账收款，核对应收余额变化。">
    <template #filters>
      <el-form class="query-form">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="receipt-keyword" clearable placeholder="收款单、应收单、客户" />
        </el-form-item>
        <el-form-item label="客户">
          <el-select v-model="filters.customerId" clearable filterable placeholder="全部客户">
            <el-option v-for="customer in customers" :key="customer.id" :label="`${customer.code} ${customer.name}`" :value="customer.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已过账" value="POSTED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="收款日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.dateFrom" name="receipt-date-from" placeholder="起始日期" />
        </el-form-item>
        <el-form-item>
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.dateTo" name="receipt-date-to" placeholder="截止日期" />
        </el-form-item>
        <el-form-item label="应收 ID">
          <el-input v-model="filters.receivableId" name="receipt-receivable-id" clearable placeholder="应收标识" />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-receipts" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-receipts" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="referenceError" class="state-alert" type="error" :title="referenceError" :closable="false" />
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading || referenceLoading" class="state-alert" type="info" title="收款记录加载中" :closable="false" />
    </template>

    <el-empty v-if="!loading && records.length === 0" description="暂无收款记录" />
    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无收款记录'" stripe>
        <el-table-column prop="receiptNo" label="收款单号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="receivableNo" label="应收单号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="customerName" label="客户" min-width="160" show-overflow-tooltip />
        <el-table-column prop="receiptDate" label="收款日期" min-width="110" />
        <el-table-column label="收款金额" min-width="140" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ formatFinanceAmount(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="收款方式" min-width="120"><template #default="{ row }">{{ financeMethodText(row.method) }}</template></el-table-column>
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }"><ReceiptStatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column prop="createdByName" label="创建人" min-width="100" />
        <el-table-column prop="postedByName" label="过账人" min-width="100" />
        <el-table-column prop="postedAt" label="过账时间" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="210">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-receipt" @click="viewReceipt(row)">详情</el-button>
            <el-button v-if="canUpdateReceipt(row)" size="small" text data-test="edit-receipt" @click="editReceipt(row)">编辑</el-button>
            <el-button
              v-if="canPostReceipt(row)"
              size="small"
              text
              type="success"
              data-test="post-receipt"
              :loading="actionLoading"
              :disabled="actionLoading"
              @click="runReceiptAction(row, 'post')"
            >
              过账
            </el-button>
            <el-button
              v-if="canCancelReceipt(row)"
              size="small"
              text
              type="danger"
              data-test="cancel-receipt"
              :loading="actionLoading"
              :disabled="actionLoading"
              @click="runReceiptAction(row, 'cancel')"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination class="table-pagination" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" :total="pagination.total" :page-size="pagination.pageSize" :current-page="pagination.page" @current-change="changePage" @size-change="changePageSize" />
  </MasterDataTableView>
</template>

<style scoped>
.numeric-cell {
  display: inline-block;
  min-width: 84px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
</style>
