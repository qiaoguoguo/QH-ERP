<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { financeSettlementApi, type AdvanceFundRecord, type AdvanceFundStatus } from '../../shared/api/financeSettlementApi'
import { useAuthStore } from '../../stores/authStore'
import type { OwnershipType, SettlementStatus } from '../../shared/api/financeStage028ApiCore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import { financeErrorMessage, financePermissions, formatFinanceAmount, ownershipTypeText, settlementStatusText } from './financePageHelpers'
import './Finance028Shared.css'

const router = useRouter()
const authStore = useAuthStore()
const filters = reactive({
  keyword: '',
  supplierId: '',
  ownershipType: '' as '' | OwnershipType,
  projectId: '',
  status: '' as '' | AdvanceFundStatus,
  settlementStatus: '' as '' | SettlementStatus,
  businessDateFrom: '',
  businessDateTo: '',
  availableOnly: false,
})
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<AdvanceFundRecord[]>([])
const error = ref('')
const loading = ref(false)
const canCreate = computed(() => authStore.hasPermission(financePermissions.prepaymentCreate))

async function loadRecords() {
  loading.value = true
  try {
    const page = await financeSettlementApi.prepayments.list({
      keyword: filters.keyword,
      supplierId: filters.supplierId || undefined,
      ownershipType: filters.ownershipType || undefined,
      projectId: filters.ownershipType === 'PROJECT' && filters.projectId ? filters.projectId : undefined,
      status: filters.status || undefined,
      settlementStatus: filters.settlementStatus || undefined,
      businessDateFrom: filters.businessDateFrom,
      businessDateTo: filters.businessDateTo,
      availableOnly: filters.availableOnly || undefined,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = pageItems(page)
    pagination.total = Number(page.total)
  } catch (caught) {
    records.value = []
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
  filters.ownershipType = ''
  filters.projectId = ''
  filters.status = ''
  filters.settlementStatus = ''
  filters.businessDateFrom = ''
  filters.businessDateTo = ''
  filters.availableOnly = false
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
  <MasterDataTableView title="预付款" description="展示已过账付款形成的供应商未核销余额。">
    <template #actions><el-button v-if="canCreate" data-test="create-prepayment" type="primary" @click="router.push({ name: 'finance-prepayment-create' })">登记预付款</el-button></template>
    <template #filters>
      <el-form class="query-form">
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="预付单号、付款单号或供应商" /></el-form-item>
        <el-form-item label="供应商"><el-input v-model="filters.supplierId" clearable placeholder="选择供应商" /></el-form-item>
        <el-form-item label="项目/公共">
          <el-select v-model="filters.ownershipType" clearable placeholder="全部归属">
            <el-option label="项目" value="PROJECT" />
            <el-option label="公共" value="PUBLIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目"><el-input v-model="filters.projectId" clearable :disabled="filters.ownershipType === 'PUBLIC'" placeholder="选择项目" /></el-form-item>
        <el-form-item label="资金状态">
          <el-select v-model="filters.status" clearable placeholder="全部资金状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="可用" value="AVAILABLE" />
            <el-option label="部分核销" value="PARTIALLY_APPLIED" />
            <el-option label="已核销" value="APPLIED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="核销状态">
          <el-select v-model="filters.settlementStatus" clearable placeholder="全部核销状态">
            <el-option label="未结清" value="UNSETTLED" />
            <el-option label="部分结清" value="PARTIALLY_SETTLED" />
            <el-option label="已结清" value="SETTLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="付款日期"><el-date-picker v-model="filters.businessDateFrom" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="起始日期" /></el-form-item>
        <el-form-item><el-date-picker v-model="filters.businessDateTo" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="截止日期" /></el-form-item>
        <el-form-item label="可用余额大于 0"><el-switch v-model="filters.availableOnly" /></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="resetSearch">重置</el-button></el-form-item>
      </el-form>
    </template>
    <template #alerts><el-alert v-if="error" type="error" :title="error" :closable="false" /><el-alert v-if="loading" type="info" title="预付款加载中" :closable="false" /></template>
    <el-empty v-if="!loading && records.length === 0" description="暂无预付款" />
    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无预付款'">
        <el-table-column prop="advanceNo" label="预付单号" min-width="150" />
        <el-table-column prop="fundNo" label="付款单号" min-width="150" />
        <el-table-column prop="partnerName" label="供应商" min-width="150" />
        <el-table-column label="项目/公共" min-width="130"><template #default="{ row }">{{ ownershipTypeText(row.ownershipType) }} {{ row.projectName ?? '' }}</template></el-table-column>
        <el-table-column prop="businessDate" label="付款日期" min-width="110" />
        <el-table-column label="付款金额" min-width="120" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.amount) }}</template></el-table-column>
        <el-table-column label="已核销金额" min-width="120" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.allocatedAmount) }}</template></el-table-column>
        <el-table-column label="可用余额" min-width="120" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.availableAmount) }}</template></el-table-column>
        <el-table-column label="状态" min-width="110"><template #default="{ row }">{{ settlementStatusText(row.settlementStatus ?? row.status) }}</template></el-table-column>
        <el-table-column prop="lastAllocatedAt" label="最近核销时间" min-width="160" />
        <el-table-column label="操作" fixed="right" width="184"><template #default="{ row }"><el-button text @click="router.push({ name: 'finance-prepayment-detail', params: { id: row.id } })">详情</el-button></template></el-table-column>
      </el-table>
    </div>
    <el-pagination class="table-pagination" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" :total="pagination.total" :page-size="pagination.pageSize" :current-page="pagination.page" @current-change="changePage" @size-change="changePageSize" />
  </MasterDataTableView>
</template>
