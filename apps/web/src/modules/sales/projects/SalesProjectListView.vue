<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { masterDataApi, type PartnerRecord } from '../../../shared/api/masterDataApi'
import {
  salesProjectApi,
  type ProjectOwnerCandidate,
  type SalesProjectStatus,
  type SalesProjectSummary,
} from '../../../shared/api/salesProjectApi'
import { useAuthStore } from '../../../stores/authStore'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { pageItems } from '../../system/shared/pageHelpers'
import SalesProjectStatusTag from './SalesProjectStatusTag.vue'
import { formatProjectAmount, formatProjectDateTime, projectApiErrorMessage, normalizeProjectOptionalId } from './salesProjectPageHelpers'

const router = useRouter()
const authStore = useAuthStore()
const filters = reactive<{
  keyword: string
  customerId: string | number | ''
  ownerUserId: string | number | ''
  status?: SalesProjectStatus
  plannedStartFrom: string
  plannedStartTo: string
  plannedFinishFrom: string
  plannedFinishTo: string
}>({
  keyword: '',
  customerId: '',
  ownerUserId: '',
  status: undefined,
  plannedStartFrom: '',
  plannedStartTo: '',
  plannedFinishFrom: '',
  plannedFinishTo: '',
})
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<SalesProjectSummary[]>([])
const customers = ref<PartnerRecord[]>([])
const owners = ref<ProjectOwnerCandidate[]>([])
const loading = ref(true)
const referenceLoading = ref(true)
const error = ref('')
const referenceError = ref('')

const canCreate = computed(() => authStore.hasPermission('sales:project:create'))
const canUpdate = computed(() => authStore.hasPermission('sales:project:update'))
const showMainEmpty = computed(() => !loading.value && !error.value && records.value.length === 0)
const showTable = computed(() => !error.value && (loading.value || records.value.length > 0))
const showPagination = computed(() => !error.value && pagination.total > 0)

async function loadReferences() {
  referenceLoading.value = true
  referenceError.value = ''
  try {
    const [customerPage, ownerPage] = await Promise.all([
      masterDataApi.customers.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
      salesProjectApi.ownerCandidates({ keyword: '', page: 1, pageSize: 200 }),
    ])
    customers.value = pageItems(customerPage)
    owners.value = pageItems(ownerPage)
  } catch (caught) {
    customers.value = []
    owners.value = []
    referenceError.value = projectApiErrorMessage(caught)
  } finally {
    referenceLoading.value = false
  }
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await salesProjectApi.projects.list({
      keyword: filters.keyword,
      customerId: normalizeProjectOptionalId(filters.customerId),
      ownerUserId: normalizeProjectOptionalId(filters.ownerUserId),
      status: filters.status,
      plannedStartFrom: filters.plannedStartFrom,
      plannedStartTo: filters.plannedStartTo,
      plannedFinishFrom: filters.plannedFinishFrom,
      plannedFinishTo: filters.plannedFinishTo,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = pageItems(page)
    pagination.total = Number(page.total)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = projectApiErrorMessage(caught)
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
  filters.ownerUserId = ''
  filters.status = undefined
  filters.plannedStartFrom = ''
  filters.plannedStartTo = ''
  filters.plannedFinishFrom = ''
  filters.plannedFinishTo = ''
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

function createProject() {
  void router.push({ name: 'sales-project-create' })
}

function viewProject(record: SalesProjectSummary) {
  void router.push({ name: 'sales-project-detail', params: { id: String(record.id) } })
}

function editProject(record: SalesProjectSummary) {
  void router.push({ name: 'sales-project-edit', params: { id: String(record.id) } })
}

onMounted(() => {
  void loadReferences()
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="销售项目" description="维护销售项目、合同摘要和关联销售订单归属。">
    <template #actions>
      <el-button v-if="canCreate" data-test="create-sales-project" type="primary" @click="createProject">
        新建销售项目
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="sales-project-keyword" clearable placeholder="项目编号、名称、客户或负责人" />
        </el-form-item>
        <el-form-item label="客户">
          <el-select v-model="filters.customerId" clearable filterable placeholder="全部客户">
            <el-option
              v-for="customer in customers"
              :key="customer.id"
              :label="`${customer.code} ${customer.name}`"
              :value="customer.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="filters.ownerUserId" clearable filterable placeholder="全部负责人">
            <el-option
              v-for="owner in owners"
              :key="owner.userId"
              :label="`${owner.username} ${owner.displayName}`"
              :value="owner.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="执行中" value="ACTIVE" />
            <el-option label="已关闭" value="CLOSED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划开始">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.plannedStartFrom" name="sales-project-planned-start-from" placeholder="起始日期" />
        </el-form-item>
        <el-form-item>
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.plannedStartTo" name="sales-project-planned-start-to" placeholder="截止日期" />
        </el-form-item>
        <el-form-item label="计划结束">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.plannedFinishFrom" name="sales-project-planned-finish-from" placeholder="起始日期" />
        </el-form-item>
        <el-form-item>
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.plannedFinishTo" name="sales-project-planned-finish-to" placeholder="截止日期" />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-sales-projects" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-sales-projects" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="referenceError" class="state-alert" type="error" :title="referenceError" :closable="false" />
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading || referenceLoading" class="state-alert" type="info" title="销售项目加载中" :closable="false" />
    </template>

    <el-empty v-if="showMainEmpty" description="暂无销售项目" />
    <div v-if="showTable" class="table-scroll sales-project-table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无销售项目'" stripe>
        <el-table-column prop="projectNo" label="项目编号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="name" label="项目名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="客户" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.customerCode }} {{ row.customerName }}</template>
        </el-table-column>
        <el-table-column prop="ownerDisplayName" label="负责人" min-width="120" />
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }"><SalesProjectStatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="目标收入" min-width="120" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ formatProjectAmount(row.targetRevenue) }}</span></template>
        </el-table-column>
        <el-table-column label="目标成本" min-width="120" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ formatProjectAmount(row.targetCost) }}</span></template>
        </el-table-column>
        <el-table-column label="计划周期" min-width="180">
          <template #default="{ row }">{{ row.plannedStartDate || '-' }} 至 {{ row.plannedFinishDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="合同摘要" min-width="170">
          <template #default="{ row }">
            <span v-if="row.contractSummaryRestricted">合同摘要受限</span>
            <span v-else>{{ row.mainContractNo || '未维护主合同' }} / {{ row.contractCount ?? '-' }} 份</span>
          </template>
        </el-table-column>
        <el-table-column label="订单摘要" min-width="130">
          <template #default="{ row }">
            <span v-if="row.salesOrderSummaryRestricted">订单摘要受限</span>
            <span v-else>{{ row.salesOrderCount ?? '-' }} 单</span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="150">
          <template #default="{ row }">{{ formatProjectDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="150">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-sales-project" @click="viewProject(row)">详情</el-button>
            <el-button v-if="canUpdate && row.status !== 'CLOSED' && row.status !== 'CANCELLED'" size="small" text data-test="edit-sales-project" @click="editProject(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination
      v-if="showPagination"
      class="table-pagination"
      layout="total, sizes, prev, pager, next"
      :page-sizes="[10, 20, 50, 100]"
      :total="pagination.total"
      :page-size="pagination.pageSize"
      :current-page="pagination.page"
      @current-change="changePage"
      @size-change="changePageSize"
    />
  </MasterDataTableView>
</template>

<style scoped>
.numeric-cell {
  display: inline-block;
  min-width: 82px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
</style>
