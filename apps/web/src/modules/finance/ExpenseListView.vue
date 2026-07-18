<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { financeExpenseApi, type ExpenseRecord, type ExpenseSourceType, type ExpenseStatus } from '../../shared/api/financeExpenseApi'
import { useAuthStore } from '../../stores/authStore'
import type { OwnershipType } from '../../shared/api/financeStage028ApiCore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import { financeErrorMessage, financePermissions, financeSourceTypeText, formatFinanceAmount, invoiceStatusText, ownershipTypeText, settlementStatusText } from './financePageHelpers'
import './Finance028Shared.css'

const router = useRouter()
const authStore = useAuthStore()
const filters = reactive({
  keyword: '',
  supplierId: '',
  categoryId: '',
  ownershipType: '' as '' | OwnershipType,
  sourceType: '' as '' | ExpenseSourceType,
  status: '' as '' | ExpenseStatus,
  settlementStatus: '' as '' | 'UNSETTLED' | 'PARTIALLY_SETTLED' | 'SETTLED',
  businessDateFrom: '',
  businessDateTo: '',
  costRestricted: false,
})
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<ExpenseRecord[]>([])
const error = ref('')
const loading = ref(false)
const canCreate = computed(() => authStore.hasPermission(financePermissions.expenseCreate))

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await financeExpenseApi.expenses.list({
      keyword: filters.keyword,
      supplierId: filters.supplierId || undefined,
      categoryId: filters.categoryId || undefined,
      ownershipType: filters.ownershipType || undefined,
      sourceType: filters.sourceType || undefined,
      status: filters.status || undefined,
      settlementStatus: filters.settlementStatus || undefined,
      businessDateFrom: filters.businessDateFrom,
      businessDateTo: filters.businessDateTo,
      costRestricted: filters.costRestricted || undefined,
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
  filters.categoryId = ''
  filters.ownershipType = ''
  filters.sourceType = ''
  filters.status = ''
  filters.settlementStatus = ''
  filters.businessDateFrom = ''
  filters.businessDateTo = ''
  filters.costRestricted = false
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
  <MasterDataTableView title="费用单" description="记录项目/公共供应商费用归属，供后续成本和凭证阶段消费，不形成正式项目成本。">
    <template #actions><el-button v-if="canCreate" data-test="create-expense" type="primary" @click="router.push({ name: 'finance-expense-create' })">新增费用单</el-button></template>
    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="费用单、供应商或来源" /></el-form-item>
        <el-form-item label="供应商"><el-input v-model="filters.supplierId" clearable placeholder="选择供应商" /></el-form-item>
        <el-form-item label="费用分类"><el-input v-model="filters.categoryId" clearable placeholder="选择费用分类" /></el-form-item>
        <el-form-item label="项目/公共归属">
          <el-select v-model="filters.ownershipType" clearable placeholder="全部归属">
            <el-option label="项目" value="PROJECT" />
            <el-option label="公共" value="PUBLIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源类型">
          <el-select v-model="filters.sourceType" clearable placeholder="全部来源">
            <el-option label="采购入库" value="PURCHASE_RECEIPT" />
            <el-option label="外协订单" value="OUTSOURCING_ORDER" />
            <el-option label="外协收货" value="OUTSOURCING_RECEIPT" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
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
        <el-form-item label="业务日期"><el-date-picker v-model="filters.businessDateFrom" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="起始日期" /></el-form-item>
        <el-form-item><el-date-picker v-model="filters.businessDateTo" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="截止日期" /></el-form-item>
        <el-form-item label="成本受限"><el-switch v-model="filters.costRestricted" /></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button data-test="reset-expenses" @click="resetSearch">重置</el-button></el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" type="info" title="费用单加载中" :closable="false" />
    </template>
    <el-empty v-if="!loading && records.length === 0" description="暂无费用单" />
    <div class="table-scroll">
      <el-table :data="records" stripe :empty-text="loading ? '加载中' : '暂无费用单'">
        <el-table-column prop="expenseNo" label="费用单号" min-width="150" />
        <el-table-column prop="supplierName" label="供应商" min-width="150" />
        <el-table-column prop="categoryName" label="费用分类" min-width="130" />
        <el-table-column label="项目/公共" min-width="130"><template #default="{ row }">{{ ownershipTypeText(row.ownershipType) }} {{ row.projectName ?? '' }}</template></el-table-column>
        <el-table-column label="来源类型" min-width="120"><template #default="{ row }">{{ financeSourceTypeText(row.sourceType ?? 'NONE') }}</template></el-table-column>
        <el-table-column prop="sourceNo" label="来源编号" min-width="140" />
        <el-table-column prop="businessDate" label="业务日期" min-width="110" />
        <el-table-column label="状态" min-width="100"><template #default="{ row }">{{ invoiceStatusText(row.status) }}</template></el-table-column>
        <el-table-column label="结算状态" min-width="110"><template #default="{ row }">{{ settlementStatusText(row.settlementStatus) }}</template></el-table-column>
        <el-table-column label="未税金额" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ formatFinanceAmount(row.pretaxAmount) }}</span></template></el-table-column>
        <el-table-column label="税额" min-width="100" align="right"><template #default="{ row }"><span class="numeric-cell">{{ formatFinanceAmount(row.taxAmount) }}</span></template></el-table-column>
        <el-table-column label="含税金额" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ formatFinanceAmount(row.totalAmount) }}</span></template></el-table-column>
        <el-table-column label="未结余额" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ formatFinanceAmount(row.unsettledAmount) }}</span></template></el-table-column>
        <el-table-column label="操作" fixed="right" min-width="110"><template #default="{ row }"><el-button text @click="router.push({ name: 'finance-expense-detail', params: { id: row.id } })">详情</el-button></template></el-table-column>
      </el-table>
    </div>
    <el-pagination class="table-pagination" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" :total="pagination.total" :page-size="pagination.pageSize" :current-page="pagination.page" @current-change="changePage" @size-change="changePageSize" />
  </MasterDataTableView>
</template>
