<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { masterDataApi, type PartnerRecord } from '../../shared/api/masterDataApi'
import { financeApi, type PaymentStatus, type PaymentSummaryRecord } from '../../shared/api/financeApi'
import { currentRouteReturnTo, queryWithReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import PaymentStatusTag from './PaymentStatusTag.vue'
import { financeErrorMessage, financePermissions, formatFinanceAmount, normalizeOptionalId } from './financePageHelpers'
import { confirmAction } from '../../shared/ui/confirmDialog'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const filters = reactive<{
  keyword: string
  supplierId: string | number | ''
  status?: PaymentStatus
  dateFrom: string
  dateTo: string
  payableId: string
}>({
  keyword: '',
  supplierId: '',
  status: undefined,
  dateFrom: '',
  dateTo: '',
  payableId: '',
})
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const suppliers = ref<PartnerRecord[]>([])
const records = ref<PaymentSummaryRecord[]>([])
const loading = ref(true)
const actionLoading = ref(false)
const referenceLoading = ref(true)
const error = ref('')
const referenceError = ref('')

async function loadSuppliers() {
  referenceLoading.value = true
  referenceError.value = ''
  try {
    suppliers.value = pageItems(await masterDataApi.suppliers.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }))
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
    const page = await financeApi.payments.list({
      keyword: filters.keyword,
      supplierId: normalizeOptionalId(filters.supplierId),
      status: filters.status,
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      payableId: normalizeOptionalId(filters.payableId),
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
  filters.payableId = ''
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

function viewPayment(record: PaymentSummaryRecord) {
  void router.push({
    name: 'finance-payment-detail',
    params: { id: String(record.id) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function editPayment(record: PaymentSummaryRecord) {
  void router.push({ name: 'finance-payment-edit', params: { id: String(record.id) } })
}

function canUpdatePayment(record: PaymentSummaryRecord) {
  return record.status === 'DRAFT' && authStore.hasPermission(financePermissions.paymentUpdate)
}

function canPostPayment(record: PaymentSummaryRecord) {
  return record.status === 'DRAFT' && authStore.hasPermission(financePermissions.paymentPost)
}

function canCancelPayment(record: PaymentSummaryRecord) {
  return record.status === 'DRAFT' && authStore.hasPermission(financePermissions.paymentCancel)
}

async function runPaymentAction(record: PaymentSummaryRecord, action: 'post' | 'cancel') {
  if (actionLoading.value) {
    return
  }
  const label = action === 'post' ? '过账' : '取消'
  if (!(await confirmAction(`确认${label}付款“${record.paymentNo}”，金额 ${formatFinanceAmount(record.amount)}？`))) {
    return
  }
  actionLoading.value = true
  error.value = ''
  try {
    if (action === 'post') {
      await financeApi.payments.post(record.id)
    } else {
      await financeApi.payments.cancel(record.id)
    }
    await loadRecords()
  } catch (caught) {
    error.value = financeErrorMessage(caught)
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
  <MasterDataTableView title="付款记录" description="扫描付款草稿和已过账付款，核对应付余额变化。">
    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="payment-keyword" clearable placeholder="付款单、应付单、供应商" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="filters.supplierId" clearable filterable placeholder="全部供应商">
            <el-option v-for="supplier in suppliers" :key="supplier.id" :label="`${supplier.code} ${supplier.name}`" :value="supplier.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已过账" value="POSTED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="付款日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.dateFrom" name="payment-date-from" placeholder="起始日期" />
        </el-form-item>
        <el-form-item>
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.dateTo" name="payment-date-to" placeholder="截止日期" />
        </el-form-item>
        <el-form-item label="应付 ID">
          <el-input v-model="filters.payableId" name="payment-payable-id" clearable placeholder="应付标识" />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-payments" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-payments" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="referenceError" class="state-alert" type="error" :title="referenceError" :closable="false" />
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading || referenceLoading" class="state-alert" type="info" title="付款记录加载中" :closable="false" />
    </template>

    <el-empty v-if="!loading && records.length === 0" description="暂无付款记录" />
    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无付款记录'" stripe>
        <el-table-column prop="paymentNo" label="付款单号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="payableNo" label="应付单号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="supplierName" label="供应商" min-width="160" show-overflow-tooltip />
        <el-table-column prop="paymentDate" label="付款日期" min-width="110" />
        <el-table-column label="付款金额" min-width="140" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ formatFinanceAmount(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column prop="method" label="付款方式" min-width="120" />
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }"><PaymentStatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column prop="createdByName" label="创建人" min-width="100" />
        <el-table-column prop="postedByName" label="过账人" min-width="100" />
        <el-table-column prop="postedAt" label="过账时间" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" fixed="right" width="210">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-payment" @click="viewPayment(row)">详情</el-button>
            <el-button v-if="canUpdatePayment(row)" size="small" text data-test="edit-payment" @click="editPayment(row)">编辑</el-button>
            <el-button
              v-if="canPostPayment(row)"
              size="small"
              text
              type="success"
              data-test="post-payment"
              :loading="actionLoading"
              :disabled="actionLoading"
              @click="runPaymentAction(row, 'post')"
            >
              过账
            </el-button>
            <el-button
              v-if="canCancelPayment(row)"
              size="small"
              text
              type="danger"
              data-test="cancel-payment"
              :loading="actionLoading"
              :disabled="actionLoading"
              @click="runPaymentAction(row, 'cancel')"
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
