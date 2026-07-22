<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { masterDataApi, type PartnerRecord } from '../../shared/api/masterDataApi'
import { financeApi, type ReceivableStatus, type ReceivableSummaryRecord } from '../../shared/api/financeApi'
import { currentRouteReturnTo, queryWithReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import ReceivableStatusTag from './ReceivableStatusTag.vue'
import {
  compareFinanceAmount,
  financeErrorMessage,
  financePermissions,
  formatFinanceAmount,
  normalizeOptionalId,
  voucherDraftStatusText,
} from './financePageHelpers'
import { confirmAction } from '../../shared/ui/confirmDialog'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const filters = reactive<{
  keyword: string
  customerId: string | number | ''
  status?: ReceivableStatus
  dateFrom: string
  dateTo: string
  dueDateFrom: string
  dueDateTo: string
  sourceNo: string
}>({
  keyword: '',
  customerId: '',
  status: undefined,
  dateFrom: '',
  dateTo: '',
  dueDateFrom: '',
  dueDateTo: '',
  sourceNo: '',
})
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const customers = ref<PartnerRecord[]>([])
const records = ref<ReceivableSummaryRecord[]>([])
const loading = ref(true)
const referenceLoading = ref(true)
const error = ref('')
const referenceError = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const canCreate = computed(() => authStore.hasPermission(financePermissions.receivableCreate))
const canUpdate = computed(() => authStore.hasPermission(financePermissions.receivableUpdate))
const canConfirm = computed(() => authStore.hasPermission(financePermissions.receivableConfirm))
const canCancelPermission = computed(() => authStore.hasPermission(financePermissions.receivableCancel))
const canClosePermission = computed(() => authStore.hasPermission(financePermissions.receivableClose))
const canCreateReceiptPermission = computed(() => authStore.hasPermission(financePermissions.receiptCreate))

async function loadCustomers() {
  referenceLoading.value = true
  referenceError.value = ''
  try {
    customers.value = pageItems(await masterDataApi.customers.list({
      keyword: '',
      status: 'ENABLED',
      page: 1,
      pageSize: 200,
    }))
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
    const page = await financeApi.receivables.list({
      keyword: filters.keyword,
      customerId: normalizeOptionalId(filters.customerId),
      status: filters.status,
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      dueDateFrom: filters.dueDateFrom,
      dueDateTo: filters.dueDateTo,
      sourceNo: filters.sourceNo,
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
  filters.dueDateFrom = ''
  filters.dueDateTo = ''
  filters.sourceNo = ''
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

function createReceivable() {
  void router.push({ name: 'finance-receivable-create' })
}

function viewReceivable(record: ReceivableSummaryRecord) {
  void router.push({
    name: 'finance-receivable-detail',
    params: { id: String(record.id) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function editReceivable(record: ReceivableSummaryRecord) {
  void router.push({ name: 'finance-receivable-edit', params: { id: String(record.id) } })
}

function createReceipt(record: ReceivableSummaryRecord) {
  void router.push({ name: 'finance-receipt-create', params: { id: String(record.id) } })
}

function canCancel(record: ReceivableSummaryRecord) {
  return record.status === 'DRAFT' || (record.status === 'CONFIRMED' && compareFinanceAmount(record.receivedAmount, '0.00') !== 1)
}

function canClose(record: ReceivableSummaryRecord) {
  return record.status === 'CONFIRMED' || record.status === 'PARTIALLY_RECEIVED'
}

function canCreateReceipt(record: ReceivableSummaryRecord) {
  return record.status === 'CONFIRMED' || record.status === 'PARTIALLY_RECEIVED'
}

function invoiceLinkSummary(record: ReceivableSummaryRecord) {
  if (!record.invoiceLinks?.length) {
    return '未关联发票'
  }
  return record.invoiceLinks.map((link) => `${link.invoiceNo} ${formatFinanceAmount(link.amount)}`).join('；')
}

function allocationSummary(record: ReceivableSummaryRecord) {
  const summary = record.allocationSummary
  if (summary) {
    const targetCount = summary.targetCount ?? 0
    return `${targetCount} 个目标，已核销 ${formatFinanceAmount(summary.allocatedAmount ?? record.receivedAmount)}，可用 ${formatFinanceAmount(summary.availableAmount ?? '0.00')}`
  }
  return `历史收款 ${formatFinanceAmount(record.receivedAmount)}，未收 ${formatFinanceAmount(record.unreceivedAmount)}`
}

function voucherDraftSummary(record: ReceivableSummaryRecord) {
  if (!record.voucherDrafts?.length) {
    return '暂无草稿'
  }
  return record.voucherDrafts.map((draft) => `${draft.draftNo} ${voucherDraftStatusText(draft.status)}`).join('；')
}

async function runReceivableAction(record: ReceivableSummaryRecord, action: 'confirm' | 'cancel' | 'close') {
  if (actionLoading.value) {
    return
  }
  const labels = { confirm: '确认', cancel: '取消', close: '关闭' }
  if (!(await confirmAction(`确认${labels[action]}应收“${record.receivableNo}”？`))) {
    return
  }

  actionError.value = ''
  actionLoading.value = true
  try {
    if (action === 'confirm') {
      await financeApi.receivables.confirm(record.id)
    } else if (action === 'cancel') {
      await financeApi.receivables.cancel(record.id)
    } else {
      await financeApi.receivables.close(record.id)
    }
    await loadRecords()
  } catch (caught) {
    actionError.value = financeErrorMessage(caught)
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
  <MasterDataTableView title="应收台账" description="按客户、来源和到期日期追踪销售出库形成的应收余额；含历史业务台账，028 发票/费用通过链接衔接。">
    <template #actions>
      <el-button v-if="canCreate" data-test="create-receivable" type="primary" @click="createReceivable">
        生成应收
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="receivable-keyword" clearable placeholder="应收单、客户或来源" />
        </el-form-item>
        <el-form-item label="客户">
          <el-select v-model="filters.customerId" clearable filterable placeholder="全部客户">
            <el-option v-for="customer in customers" :key="customer.id" :label="`${customer.code} ${customer.name}`" :value="customer.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="待收款" value="CONFIRMED" />
            <el-option label="部分收款" value="PARTIALLY_RECEIVED" />
            <el-option label="已收清" value="RECEIVED" />
            <el-option label="已关闭" value="CLOSED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.dateFrom" name="receivable-date-from" placeholder="起始日期" />
        </el-form-item>
        <el-form-item>
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.dateTo" name="receivable-date-to" placeholder="截止日期" />
        </el-form-item>
        <el-form-item label="到期日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.dueDateFrom" name="receivable-due-date-from" placeholder="起始日期" />
        </el-form-item>
        <el-form-item>
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.dueDateTo" name="receivable-due-date-to" placeholder="截止日期" />
        </el-form-item>
        <el-form-item label="来源单号">
          <el-input v-model="filters.sourceNo" name="receivable-source-no" clearable placeholder="销售出库" />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-receivables" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-receivables" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="referenceError" class="state-alert" type="error" :title="referenceError" :closable="false" />
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading || referenceLoading" class="state-alert" type="info" title="应收台账加载中" :closable="false" />
    </template>

    <el-empty v-if="!loading && records.length === 0" description="暂无应收台账" />
    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无应收台账'" stripe>
        <el-table-column prop="receivableNo" label="应收单号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="customerName" label="客户" min-width="170" show-overflow-tooltip />
        <el-table-column prop="sourceNo" label="来源销售出库" min-width="160" show-overflow-tooltip />
        <el-table-column prop="salesOrderNo" label="来源销售订单" min-width="160" show-overflow-tooltip />
        <el-table-column prop="businessDate" label="业务日期" min-width="110" />
        <el-table-column prop="dueDate" label="到期日期" min-width="110" />
        <el-table-column label="状态" min-width="105">
          <template #default="{ row }"><ReceivableStatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="应收金额" min-width="140" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ formatFinanceAmount(row.totalAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="已收金额" min-width="140" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ formatFinanceAmount(row.receivedAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="未收金额" min-width="140" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ formatFinanceAmount(row.unreceivedAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="发票/费用链接" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ invoiceLinkSummary(row) }}</template>
        </el-table-column>
        <el-table-column label="核销摘要" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ allocationSummary(row) }}</template>
        </el-table-column>
        <el-table-column label="凭证草稿" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ voucherDraftSummary(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="330">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-receivable" @click="viewReceivable(row)">详情</el-button>
            <el-button v-if="canUpdate && row.status === 'DRAFT'" size="small" text data-test="edit-receivable" @click="editReceivable(row)">编辑</el-button>
            <el-button v-if="canConfirm && row.status === 'DRAFT'" size="small" text type="success" data-test="confirm-receivable" :disabled="actionLoading" @click="runReceivableAction(row, 'confirm')">确认</el-button>
            <el-button v-if="canCancelPermission && canCancel(row)" size="small" text type="danger" data-test="cancel-receivable" :disabled="actionLoading" @click="runReceivableAction(row, 'cancel')">取消</el-button>
            <el-button v-if="canClosePermission && canClose(row)" size="small" text type="warning" data-test="close-receivable" :disabled="actionLoading" @click="runReceivableAction(row, 'close')">关闭</el-button>
            <el-button v-if="canCreateReceiptPermission && canCreateReceipt(row)" size="small" text data-test="create-receipt" @click="createReceipt(row)">登记收款</el-button>
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
  min-width: 96px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
</style>
