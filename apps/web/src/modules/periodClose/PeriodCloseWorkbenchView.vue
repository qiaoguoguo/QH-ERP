<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createIdempotencyKey } from '../../shared/api/documentPlatformApi'
import {
  businessPeriodCloseApi,
  type BusinessPeriodCloseCheckResult,
  type BusinessPeriodCloseRunRecord,
  type BusinessPeriodCloseStatus,
} from '../../shared/api/businessPeriodCloseApi'
import { currentRouteReturnTo, queryWithReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems, pageTotal } from '../system/shared/pageHelpers'
import PeriodCloseStatusTag from './PeriodCloseStatusTag.vue'
import {
  formatPeriodCloseAmount,
  formatPeriodCloseDateTime,
  businessPeriodStatusLabel,
  periodCloseActionDisabledReason,
  periodCloseAllowed,
  periodCloseErrorMessage,
  restrictedMoneyReason,
} from './periodClosePageHelpers'
import './PeriodCloseShared.css'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const filters = reactive<{
  periodCode: string
  startDate: string
  endDate: string
  closeStatus?: BusinessPeriodCloseStatus
  checkResult?: BusinessPeriodCloseCheckResult
  hasBlocking?: boolean
}>({
  periodCode: typeof route.query.periodCode === 'string' ? route.query.periodCode : '',
  startDate: '',
  endDate: '',
  closeStatus: undefined,
  checkResult: undefined,
  hasBlocking: undefined,
})
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<BusinessPeriodCloseRunRecord[]>([])
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoadingId = ref<string | null>(null)

const canCheck = computed(() => authStore.hasPermission('system:business-period-close:check'))
const metricPending = computed(() => records.value.filter((item) => item.closeStatus === 'PENDING_CHECK').length)
const metricBlocked = computed(() => records.value.filter((item) => item.closeStatus === 'BLOCKED').length)
const metricReady = computed(() => records.value.filter((item) => item.closeStatus === 'READY').length)
const metricClosed = computed(() => records.value.filter((item) => item.closeStatus === 'CLOSED').length)

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await businessPeriodCloseApi.runs.list({
      periodCode: filters.periodCode,
      startDate: filters.startDate,
      endDate: filters.endDate,
      closeStatus: filters.closeStatus,
      checkResult: filters.checkResult,
      hasBlocking: filters.hasBlocking,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = pageItems(page)
    pagination.total = pageTotal(page)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = periodCloseErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function search() {
  pagination.page = 1
  void loadRecords()
}

function resetSearch() {
  filters.periodCode = ''
  filters.startDate = ''
  filters.endDate = ''
  filters.closeStatus = undefined
  filters.checkResult = undefined
  filters.hasBlocking = undefined
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

function viewRun(record: BusinessPeriodCloseRunRecord) {
  if (!record.runId) {
    return
  }
  void router.push({
    name: 'period-close-run-detail',
    params: { runId: String(record.runId) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

async function runCheck(record: BusinessPeriodCloseRunRecord) {
  if (!canCheck.value || actionLoadingId.value || !periodCloseAllowed(record, 'CHECK')) {
    return
  }
  actionError.value = ''
  actionLoadingId.value = String(record.periodId)
  try {
    await businessPeriodCloseApi.checks.create({
      periodId: record.periodId,
      idempotencyKey: createIdempotencyKey('period-close-check'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = periodCloseErrorMessage(caught)
  } finally {
    actionLoadingId.value = null
  }
}

function canRunCheck(record: BusinessPeriodCloseRunRecord) {
  return canCheck.value && record.periodStatus === 'OPEN' && periodCloseAllowed(record, 'CHECK')
}

function canViewRun(record: BusinessPeriodCloseRunRecord) {
  return Boolean(record.runId)
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView
    title="业务月结"
    description="冻结业务期间的库存、在制、项目成本和经营报表基线，不是财务关账。"
  >
    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="期间编码">
          <el-input v-model="filters.periodCode" name="period-close-period-code" clearable placeholder="例如 2026-07" />
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="filters.startDate" value-on-clear="" name="period-close-start-date" type="date" value-format="YYYY-MM-DD" placeholder="起始日期" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="filters.endDate" value-on-clear="" name="period-close-end-date" type="date" value-format="YYYY-MM-DD" placeholder="截止日期" />
        </el-form-item>
        <el-form-item label="月结状态">
          <el-select v-model="filters.closeStatus" clearable placeholder="全部状态">
            <el-option label="待检查" value="PENDING_CHECK" />
            <el-option label="检查未通过" value="BLOCKED" />
            <el-option label="可月结" value="READY" />
            <el-option label="已月结" value="CLOSED" />
            <el-option label="已重开" value="REOPENED" />
          </el-select>
        </el-form-item>
        <el-form-item label="检查结果">
          <el-select v-model="filters.checkResult" clearable placeholder="全部结果">
            <el-option label="阻断" value="BLOCKING" />
            <el-option label="警告" value="WARNING" />
            <el-option label="通过" value="PASSED" />
          </el-select>
        </el-form-item>
        <el-form-item label="阻断筛选">
          <el-select v-model="filters.hasBlocking" clearable placeholder="全部">
            <el-option label="有阻断" :value="true" />
            <el-option label="无阻断" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作">
          <el-button data-test="period-close-search" type="primary" @click="search">查询</el-button>
          <el-button data-test="period-close-reset" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="业务月结数据加载中" :closable="false" />
    </template>

    <section class="period-close-summary-strip">
      <div><span>待检查</span><strong>{{ metricPending }}</strong></div>
      <div><span>检查未通过</span><strong>{{ metricBlocked }}</strong></div>
      <div><span>可月结</span><strong>{{ metricReady }}</strong></div>
      <div><span>已月结</span><strong>{{ metricClosed }}</strong></div>
    </section>

    <el-empty v-if="!loading && records.length === 0" description="暂无业务月结记录" />
    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无业务月结记录'" stripe>
        <el-table-column prop="periodCode" label="期间编码" min-width="120" />
        <el-table-column prop="periodName" label="期间名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="startDate" label="开始日期" min-width="120" />
        <el-table-column prop="endDate" label="结束日期" min-width="120" />
        <el-table-column label="月结状态" min-width="120">
          <template #default="{ row }">
            <PeriodCloseStatusTag :status="row.closeStatus" :label="row.closeStatusName" />
          </template>
        </el-table-column>
        <el-table-column label="期间状态" min-width="130">
          <template #default="{ row }">{{ businessPeriodStatusLabel(row.periodStatus, row.periodStatusName) }}</template>
        </el-table-column>
        <el-table-column prop="revisionNo" label="版本" min-width="90" align="right" />
        <el-table-column label="阻断/警告" min-width="110">
          <template #default="{ row }">{{ row.blockingCount }} / {{ row.warningCount }}</template>
        </el-table-column>
        <el-table-column label="最近检查" min-width="170">
          <template #default="{ row }">{{ formatPeriodCloseDateTime(row.latestCheckedAt) }}</template>
        </el-table-column>
        <el-table-column label="快照价值" min-width="150" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatPeriodCloseAmount(row.snapshotValueAmount, restrictedMoneyReason(row)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ row }">
            <el-button v-if="canViewRun(row)" size="small" text data-test="period-close-run-detail" @click="viewRun(row)">详情</el-button>
            <span v-else class="period-close-muted">无月结快照，请先解锁期间后重新检查</span>
            <el-button
              v-if="canRunCheck(row)"
              size="small"
              text
              type="primary"
              data-test="period-close-run-check"
              :loading="actionLoadingId === String(row.periodId)"
              :title="periodCloseActionDisabledReason(row, 'CHECK')"
              @click="runCheck(row)"
            >
              发起检查
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination
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
