<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { masterDataApi, type PartnerRecord } from '../../shared/api/masterDataApi'
import { financeApi, type PayableStatus, type PayableSummaryRecord } from '../../shared/api/financeApi'
import { currentRouteReturnTo, queryWithReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import PayableStatusTag from './PayableStatusTag.vue'
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
  supplierId: string | number | ''
  status?: PayableStatus
  dateFrom: string
  dateTo: string
  dueDateFrom: string
  dueDateTo: string
  sourceNo: string
}>({
  keyword: '',
  supplierId: '',
  status: undefined,
  dateFrom: '',
  dateTo: '',
  dueDateFrom: '',
  dueDateTo: '',
  sourceNo: '',
})
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const suppliers = ref<PartnerRecord[]>([])
const records = ref<PayableSummaryRecord[]>([])
const loading = ref(true)
const referenceLoading = ref(true)
const error = ref('')
const referenceError = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const canCreate = computed(() => authStore.hasPermission(financePermissions.payableCreate))
const canUpdate = computed(() => authStore.hasPermission(financePermissions.payableUpdate))
const canConfirm = computed(() => authStore.hasPermission(financePermissions.payableConfirm))
const canCancelPermission = computed(() => authStore.hasPermission(financePermissions.payableCancel))
const canClosePermission = computed(() => authStore.hasPermission(financePermissions.payableClose))
const canCreatePaymentPermission = computed(() => authStore.hasPermission(financePermissions.paymentCreate))

async function loadSuppliers() {
  referenceLoading.value = true
  referenceError.value = ''
  try {
    suppliers.value = pageItems(await masterDataApi.suppliers.list({
      keyword: '',
      status: 'ENABLED',
      page: 1,
      pageSize: 200,
    }))
  } catch (caught) {
    suppliers.value = []
    referenceError.value = financeErrorMessage(caught)
  } finally {
    referenceLoading.value = false
  }
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await financeApi.payables.list({
      keyword: filters.keyword,
      supplierId: normalizeOptionalId(filters.supplierId),
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
  filters.supplierId = ''
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

function createPayable() {
  void router.push({ name: 'finance-payable-create' })
}

function viewPayable(record: PayableSummaryRecord) {
  void router.push({
    name: 'finance-payable-detail',
    params: { id: String(record.id) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function editPayable(record: PayableSummaryRecord) {
  void router.push({ name: 'finance-payable-edit', params: { id: String(record.id) } })
}

function createPayment(record: PayableSummaryRecord) {
  void router.push({ name: 'finance-payment-create', params: { id: String(record.id) } })
}

function canCancel(record: PayableSummaryRecord) {
  return record.status === 'DRAFT' || (record.status === 'CONFIRMED' && compareFinanceAmount(record.paidAmount, '0.00') !== 1)
}

function canClose(record: PayableSummaryRecord) {
  return record.status === 'CONFIRMED' || record.status === 'PARTIALLY_PAID'
}

function canCreatePayment(record: PayableSummaryRecord) {
  return record.status === 'CONFIRMED' || record.status === 'PARTIALLY_PAID'
}

function payableLinkSummary(record: PayableSummaryRecord) {
  const invoiceLinks = record.invoiceLinks ?? []
  const expenseLinks = record.expenseLinks ?? []
  const parts = [
    ...invoiceLinks.map((link) => `${link.invoiceNo} ${formatFinanceAmount(link.amount)}`),
    ...expenseLinks.map((link) => `${link.expenseNo} ${formatFinanceAmount(link.amount)}`),
  ]
  return parts.length ? parts.join('；') : '未关联发票/费用'
}

function allocationSummary(record: PayableSummaryRecord) {
  const summary = record.allocationSummary
  if (summary) {
    const targetCount = summary.targetCount ?? 0
    return `${targetCount} 个目标，已核销 ${formatFinanceAmount(summary.allocatedAmount ?? record.paidAmount)}，可用 ${formatFinanceAmount(summary.availableAmount ?? '0.00')}`
  }
  return `历史付款 ${formatFinanceAmount(record.paidAmount)}，未付 ${formatFinanceAmount(record.unpaidAmount)}`
}

function voucherDraftSummary(record: PayableSummaryRecord) {
  if (!record.voucherDrafts?.length) {
    return '暂无草稿'
  }
  return record.voucherDrafts.map((draft) => `${draft.draftNo} ${voucherDraftStatusText(draft.status)}`).join('；')
}

async function runPayableAction(record: PayableSummaryRecord, action: 'confirm' | 'cancel' | 'close') {
  if (actionLoading.value) {
    return
  }
  const labels = { confirm: '确认', cancel: '取消', close: '关闭' }
  if (!(await confirmAction(`确认${labels[action]}应付“${record.payableNo}”？`))) {
    return
  }

  actionError.value = ''
  actionLoading.value = true
  try {
    if (action === 'confirm') {
      await financeApi.payables.confirm(record.id)
    } else if (action === 'cancel') {
      await financeApi.payables.cancel(record.id)
    } else {
      await financeApi.payables.close(record.id)
    }
    await loadRecords()
  } catch (caught) {
    actionError.value = financeErrorMessage(caught)
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
  <MasterDataTableView title="应付台账" description="按供应商、来源和到期日期追踪采购入库形成的应付余额；含历史业务台账，028 发票/费用通过链接衔接。">
    <template #actions>
      <el-button v-if="canCreate" data-test="create-payable" type="primary" @click="createPayable">
        生成应付
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="payable-keyword" clearable placeholder="应付单、供应商或来源" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="filters.supplierId" clearable filterable placeholder="全部供应商">
            <el-option v-for="supplier in suppliers" :key="supplier.id" :label="`${supplier.code} ${supplier.name}`" :value="supplier.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="待付款" value="CONFIRMED" />
            <el-option label="部分付款" value="PARTIALLY_PAID" />
            <el-option label="已付清" value="PAID" />
            <el-option label="已关闭" value="CLOSED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.dateFrom" name="payable-date-from" placeholder="起始日期" />
        </el-form-item>
        <el-form-item>
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.dateTo" name="payable-date-to" placeholder="截止日期" />
        </el-form-item>
        <el-form-item label="到期日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.dueDateFrom" name="payable-due-date-from" placeholder="起始日期" />
        </el-form-item>
        <el-form-item>
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.dueDateTo" name="payable-due-date-to" placeholder="截止日期" />
        </el-form-item>
        <el-form-item label="来源单号">
          <el-input v-model="filters.sourceNo" name="payable-source-no" clearable placeholder="采购入库" />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-payables" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-payables" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="referenceError" class="state-alert" type="error" :title="referenceError" :closable="false" />
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading || referenceLoading" class="state-alert" type="info" title="应付台账加载中" :closable="false" />
    </template>

    <el-empty v-if="!loading && records.length === 0" description="暂无应付台账" />
    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无应付台账'" stripe>
        <el-table-column prop="payableNo" label="应付单号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="supplierName" label="供应商" min-width="170" show-overflow-tooltip />
        <el-table-column prop="sourceNo" label="来源采购入库" min-width="160" show-overflow-tooltip />
        <el-table-column prop="purchaseOrderNo" label="来源采购订单" min-width="160" show-overflow-tooltip />
        <el-table-column prop="businessDate" label="业务日期" min-width="110" />
        <el-table-column prop="dueDate" label="到期日期" min-width="110" />
        <el-table-column label="状态" min-width="105">
          <template #default="{ row }"><PayableStatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="应付金额" min-width="140" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ formatFinanceAmount(row.totalAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="已付金额" min-width="140" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ formatFinanceAmount(row.paidAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="未付金额" min-width="140" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ formatFinanceAmount(row.unpaidAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="发票/费用链接" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ payableLinkSummary(row) }}</template>
        </el-table-column>
        <el-table-column label="核销摘要" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ allocationSummary(row) }}</template>
        </el-table-column>
        <el-table-column label="凭证草稿" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ voucherDraftSummary(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="330">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-payable" @click="viewPayable(row)">详情</el-button>
            <el-button v-if="canUpdate && row.status === 'DRAFT'" size="small" text data-test="edit-payable" @click="editPayable(row)">编辑</el-button>
            <el-button v-if="canConfirm && row.status === 'DRAFT'" size="small" text type="success" data-test="confirm-payable" :disabled="actionLoading" @click="runPayableAction(row, 'confirm')">确认</el-button>
            <el-button v-if="canCancelPermission && canCancel(row)" size="small" text type="danger" data-test="cancel-payable" :disabled="actionLoading" @click="runPayableAction(row, 'cancel')">取消</el-button>
            <el-button v-if="canClosePermission && canClose(row)" size="small" text type="warning" data-test="close-payable" :disabled="actionLoading" @click="runPayableAction(row, 'close')">关闭</el-button>
            <el-button v-if="canCreatePaymentPermission && canCreatePayment(row)" size="small" text data-test="create-payment" @click="createPayment(row)">登记付款</el-button>
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
