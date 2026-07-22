<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { financeInvoiceApi, type MatchStatus, type PurchaseInvoiceRecord, type PurchaseInvoiceSourceType } from '../../shared/api/financeInvoiceApi'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import { financeErrorMessage, financePermissions, financeSourceTypeText, formatFinanceAmount, invoiceStatusText, matchStatusText, ownershipTypeText } from './financePageHelpers'
import './Finance028Shared.css'

const router = useRouter()
const authStore = useAuthStore()
const filters = reactive({
  keyword: '',
  sourceType: '' as '' | PurchaseInvoiceSourceType,
  status: '' as '' | 'DRAFT' | 'CONFIRMED' | 'CANCELLED',
  matchStatus: '' as '' | MatchStatus,
  settlementStatus: '' as '' | 'UNSETTLED' | 'PARTIALLY_SETTLED' | 'SETTLED',
  invoiceDateFrom: '',
  invoiceDateTo: '',
})
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<PurchaseInvoiceRecord[]>([])
const loading = ref(false)
const error = ref('')
const canCreate = computed(() => authStore.hasPermission(financePermissions.purchaseInvoiceCreate))

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await financeInvoiceApi.purchaseInvoices.list({
      keyword: filters.keyword,
      sourceType: filters.sourceType || undefined,
      status: filters.status || undefined,
      matchStatus: filters.matchStatus || undefined,
      settlementStatus: filters.settlementStatus || undefined,
      invoiceDateFrom: filters.invoiceDateFrom,
      invoiceDateTo: filters.invoiceDateTo,
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
  filters.sourceType = ''
  filters.status = ''
  filters.matchStatus = ''
  filters.settlementStatus = ''
  filters.invoiceDateFrom = ''
  filters.invoiceDateTo = ''
  pagination.page = 1
  void loadRecords()
}

function changePage(pageNumber: number) {
  pagination.page = pageNumber
  void loadRecords()
}

function changePageSize(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1
  void loadRecords()
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="采购发票" description="区分标准采购发票与外协供应商结算，零容差匹配通过后衔接应付。">
    <template #actions>
      <el-button v-if="canCreate" data-test="create-purchase-invoice" type="primary" @click="router.push({ name: 'finance-purchase-invoice-create' })">新增采购发票</el-button>
    </template>
    <template #filters>
      <el-form class="query-form">
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="发票号、供应商或来源" /></el-form-item>
        <el-form-item label="来源类型">
          <el-select v-model="filters.sourceType" clearable placeholder="全部来源">
            <el-option label="采购入库" value="PURCHASE_RECEIPT" />
            <el-option label="外协收货" value="OUTSOURCING_RECEIPT" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配状态">
          <el-select v-model="filters.matchStatus" clearable placeholder="全部匹配状态">
            <el-option label="未匹配" value="UNMATCHED" />
            <el-option label="匹配通过" value="MATCHED" />
            <el-option label="存在差异" value="EXCEPTION" />
          </el-select>
        </el-form-item>
        <el-form-item label="发票状态">
          <el-select v-model="filters.status" clearable placeholder="全部发票状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已确认" value="CONFIRMED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="结算状态">
          <el-select v-model="filters.settlementStatus" clearable placeholder="全部结算状态">
            <el-option label="未结清" value="UNSETTLED" />
            <el-option label="部分结清" value="PARTIALLY_SETTLED" />
            <el-option label="已结清" value="SETTLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="发票日期"><el-date-picker v-model="filters.invoiceDateFrom" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="起始日期" /></el-form-item>
        <el-form-item><el-date-picker v-model="filters.invoiceDateTo" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="截止日期" /></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button data-test="reset-purchase-invoices" @click="resetSearch">重置</el-button></el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" type="info" title="采购发票加载中" :closable="false" />
    </template>
    <el-empty v-if="!loading && records.length === 0" description="暂无采购发票" />
    <div class="table-scroll">
      <el-table :data="records" stripe :empty-text="loading ? '加载中' : '暂无采购发票'">
        <el-table-column prop="invoiceNo" label="发票号" min-width="150" />
        <el-table-column prop="externalInvoiceNo" label="外部发票号码" min-width="160" />
        <el-table-column prop="supplierName" label="供应商" min-width="150" show-overflow-tooltip />
        <el-table-column label="来源类型" min-width="120"><template #default="{ row }">{{ financeSourceTypeText(row.sourceType) }}</template></el-table-column>
        <el-table-column label="项目/公共" min-width="130"><template #default="{ row }">{{ ownershipTypeText(row.ownershipType) }} {{ row.projectName ?? '' }}</template></el-table-column>
        <el-table-column prop="purchaseOrderNo" label="采购订单/外协订单" min-width="160" show-overflow-tooltip />
        <el-table-column prop="receiptSummary" label="入库/收货摘要" min-width="160" show-overflow-tooltip />
        <el-table-column prop="invoiceDate" label="发票日期" min-width="110" />
        <el-table-column label="状态" min-width="95"><template #default="{ row }">{{ invoiceStatusText(row.status) }}</template></el-table-column>
        <el-table-column label="匹配状态" min-width="110"><template #default="{ row }"><el-tag :type="row.matchStatus === 'EXCEPTION' ? 'danger' : 'success'">{{ matchStatusText(row.matchStatus) }}</el-tag></template></el-table-column>
        <el-table-column label="含税金额" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ formatFinanceAmount(row.totalAmount) }}</span></template></el-table-column>
        <el-table-column label="未结余额" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ formatFinanceAmount(row.unsettledAmount) }}</span></template></el-table-column>
        <el-table-column prop="differenceCount" label="差异数" min-width="90" />
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ row }"><el-button text @click="router.push({ name: 'finance-purchase-invoice-detail', params: { id: row.id } })">详情</el-button></template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination class="table-pagination" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" :total="pagination.total" :page-size="pagination.pageSize" :current-page="pagination.page" @current-change="changePage" @size-change="changePageSize" />
  </MasterDataTableView>
</template>
