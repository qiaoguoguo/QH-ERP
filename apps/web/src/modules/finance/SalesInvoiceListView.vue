<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { financeInvoiceApi, type InvoiceStatus, type SalesInvoiceRecord } from '../../shared/api/financeInvoiceApi'
import { useAuthStore } from '../../stores/authStore'
import type { SettlementStatus } from '../../shared/api/financeStage028ApiCore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import { financeErrorMessage, financePermissions, formatFinanceAmount, invoiceStatusText, ownershipTypeText, settlementStatusText } from './financePageHelpers'
import './Finance028Shared.css'

const router = useRouter()
const authStore = useAuthStore()
const filters = reactive({
  keyword: '',
  status: '' as '' | InvoiceStatus,
  settlementStatus: '' as '' | SettlementStatus,
  invoiceDateFrom: '',
  invoiceDateTo: '',
})
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<SalesInvoiceRecord[]>([])
const loading = ref(false)
const error = ref('')
const canCreate = computed(() => authStore.hasPermission(financePermissions.salesInvoiceCreate))

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await financeInvoiceApi.salesInvoices.list({
      keyword: filters.keyword,
      status: filters.status || undefined,
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
  filters.status = ''
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
  <MasterDataTableView title="销售发票" description="基于已过账销售出库和税价快照开票，不回写历史出库。">
    <template #actions>
      <el-button v-if="canCreate" data-test="create-sales-invoice" type="primary" @click="router.push({ name: 'finance-sales-invoice-create' })">
        新增销售发票
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="发票号、客户或来源" /></el-form-item>
        <el-form-item label="发票状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
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
        <el-form-item label="开票日期">
          <el-date-picker v-model="filters.invoiceDateFrom" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="起始日期" />
        </el-form-item>
        <el-form-item>
          <el-date-picker v-model="filters.invoiceDateTo" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="截止日期" />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-sales-invoices" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-sales-invoices" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" type="info" title="销售发票加载中" :closable="false" />
    </template>

    <el-empty v-if="!loading && records.length === 0" description="暂无销售发票" />
    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无销售发票'" stripe>
        <el-table-column prop="invoiceNo" label="发票号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="externalInvoiceNo" label="外部发票号码" min-width="160" show-overflow-tooltip />
        <el-table-column prop="customerName" label="客户" min-width="150" show-overflow-tooltip />
        <el-table-column label="项目/公共" min-width="130"><template #default="{ row }">{{ ownershipTypeText(row.ownershipType) }} {{ row.projectName ?? '' }}</template></el-table-column>
        <el-table-column prop="orderNo" label="合同/订单" min-width="140" show-overflow-tooltip />
        <el-table-column prop="invoiceDate" label="开票日期" min-width="110" />
        <el-table-column label="状态" min-width="100"><template #default="{ row }"><el-tag>{{ invoiceStatusText(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="结算状态" min-width="110"><template #default="{ row }"><el-tag type="warning">{{ settlementStatusText(row.settlementStatus) }}</el-tag></template></el-table-column>
        <el-table-column label="未税金额" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ formatFinanceAmount(row.pretaxAmount) }}</span></template></el-table-column>
        <el-table-column label="税额" min-width="100" align="right"><template #default="{ row }"><span class="numeric-cell">{{ formatFinanceAmount(row.taxAmount) }}</span></template></el-table-column>
        <el-table-column label="含税金额" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ formatFinanceAmount(row.totalAmount) }}</span></template></el-table-column>
        <el-table-column label="未结余额" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ formatFinanceAmount(row.unsettledAmount) }}</span></template></el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" fixed="right" min-width="110">
          <template #default="{ row }">
            <el-button text data-test="view-sales-invoice" @click="router.push({ name: 'finance-sales-invoice-detail', params: { id: row.id } })">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination class="table-pagination" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" :total="pagination.total" :page-size="pagination.pageSize" :current-page="pagination.page" @current-change="changePage" @size-change="changePageSize" />
  </MasterDataTableView>
</template>
