<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createIdempotencyKey } from '../../../shared/api/documentPlatformApi'
import {
  projectCostApi,
  type ProjectCostCompletenessStatus,
  type ProjectCostFreshnessStatus,
  type ProjectCostVarianceStatus,
  type ProjectCostWorkbenchRecord,
} from '../../../shared/api/projectCostApi'
import { currentRouteReturnTo, queryWithReturnTo } from '../../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../../stores/authStore'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { pageItems, pageTotal } from '../../system/shared/pageHelpers'
import CompletenessTag from './CompletenessTag.vue'
import ProjectCostCalculationStatusTag from './ProjectCostCalculationStatusTag.vue'
import {
  formatProjectCostAmount,
  projectCostActionDisabledReason,
  projectCostAllowed,
  projectCostErrorMessage,
  projectCostFreshnessLabel,
  projectCostMessages,
  projectCostProjectStatusLabel,
  restrictedMoneyReason,
} from './projectCostPageHelpers'
import './ProjectCostShared.css'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const filters = reactive<{
  keyword: string
  ownerUserId: string
  projectStatus?: string
  freshnessStatus?: ProjectCostFreshnessStatus
  varianceStatus?: ProjectCostVarianceStatus
  completenessStatus?: ProjectCostCompletenessStatus
  cutoffDateFrom: string
  cutoffDateTo: string
}>({
  keyword: '',
  ownerUserId: '',
  projectStatus: undefined,
  freshnessStatus: undefined,
  varianceStatus: undefined,
  completenessStatus: undefined,
  cutoffDateFrom: '',
  cutoffDateTo: '',
})
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<ProjectCostWorkbenchRecord[]>([])
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoadingId = ref<string | null>(null)

const canCalculate = computed(() => authStore.hasPermission('cost:project-cost:calculate'))
const visibleMetric = computed(() => records.value.find((item) => item.amountVisible !== false) ?? records.value[0] ?? null)
const amountRestrictedReason = computed(() => restrictedMoneyReason(visibleMetric.value))

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await projectCostApi.projectCosts.list({
      keyword: filters.keyword,
      ownerUserId: filters.ownerUserId,
      projectStatus: filters.projectStatus,
      freshnessStatus: filters.freshnessStatus,
      varianceStatus: filters.varianceStatus,
      completenessStatus: filters.completenessStatus,
      cutoffDateFrom: filters.cutoffDateFrom,
      cutoffDateTo: filters.cutoffDateTo,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = pageItems(page)
    pagination.total = pageTotal(page)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = projectCostErrorMessage(caught)
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
  filters.ownerUserId = ''
  filters.projectStatus = undefined
  filters.freshnessStatus = undefined
  filters.varianceStatus = undefined
  filters.completenessStatus = undefined
  filters.cutoffDateFrom = ''
  filters.cutoffDateTo = ''
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

function viewProject(record: ProjectCostWorkbenchRecord) {
  void router.push({
    name: 'cost-project-cost-detail',
    params: { projectId: String(record.projectId) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

async function calculateProject(record: ProjectCostWorkbenchRecord) {
  if (!canCalculate.value || actionLoadingId.value || !projectCostAllowed(record, 'CALCULATE')) {
    return
  }
  actionLoadingId.value = String(record.projectId)
  actionError.value = ''
  try {
    await projectCostApi.projectCosts.createCalculation(record.projectId, {
      cutoffDate: record.cutoffDate || filters.cutoffDateTo || new Date().toISOString().slice(0, 10),
      idempotencyKey: createIdempotencyKey('project-cost-calculate'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = projectCostErrorMessage(caught)
  } finally {
    actionLoadingId.value = null
  }
}

function canRunCalculate(record: ProjectCostWorkbenchRecord) {
  return canCalculate.value && projectCostAllowed(record, 'CALCULATE')
}

function actionReason(record: ProjectCostWorkbenchRecord, action: string) {
  return projectCostActionDisabledReason(record, action)
}

function varianceText(record: ProjectCostWorkbenchRecord) {
  if (record.sourceVisible === false) {
    return record.restrictedReason || projectCostMessages.sourceRestricted
  }
  const open = record.openVarianceCount ?? 0
  const blocking = record.blockingVarianceCount ?? 0
  return `待处理 ${open} / 阻断 ${blocking}`
}

function completenessText(record: ProjectCostWorkbenchRecord) {
  return record.completenessStatus === 'INCOMPLETE'
    ? projectCostMessages.incompleteMargin
    : '成本毛利完整'
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="项目成本核算" description="按项目查看材料、人工、外协、制造费用、调整和发货经营毛利。">
    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="project-cost-keyword" clearable placeholder="项目编号、名称、客户" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="filters.ownerUserId" name="project-cost-owner-user-id" clearable placeholder="负责人 ID" />
        </el-form-item>
        <el-form-item label="项目状态">
          <el-select v-model="filters.projectStatus" clearable placeholder="全部状态">
            <el-option label="执行中" value="ACTIVE" />
            <el-option label="已关闭" value="CLOSED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="当前性">
          <el-select v-model="filters.freshnessStatus" clearable placeholder="全部当前性">
            <el-option label="当前" value="CURRENT" />
            <el-option label="历史快照" value="STALE" />
          </el-select>
        </el-form-item>
        <el-form-item label="差异">
          <el-select v-model="filters.varianceStatus" clearable placeholder="全部差异">
            <el-option label="待处理" value="OPEN" />
            <el-option label="已解决" value="RESOLVED" />
          </el-select>
        </el-form-item>
        <el-form-item label="完整性">
          <el-select v-model="filters.completenessStatus" clearable placeholder="全部完整性">
            <el-option label="完整" value="COMPLETE" />
            <el-option label="不完整" value="INCOMPLETE" />
          </el-select>
        </el-form-item>
        <el-form-item label="截止日期起">
          <el-date-picker v-model="filters.cutoffDateFrom" value-on-clear="" name="project-cost-cutoff-from" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="起始日期" />
        </el-form-item>
        <el-form-item label="截止日期止">
          <el-date-picker v-model="filters.cutoffDateTo" value-on-clear="" name="project-cost-cutoff-to" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="截止日期" />
        </el-form-item>
        <el-form-item label="操作">
          <el-button data-test="search-project-costs" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-project-costs" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="项目成本加载中" :closable="false" />
    </template>

    <section class="project-cost-summary-strip">
      <div>
        <span>当前项目成本</span>
        <strong>{{ formatProjectCostAmount(visibleMetric?.totalCost, amountRestrictedReason || undefined) }}</strong>
      </div>
      <div>
        <span>在制成本</span>
        <strong>{{ formatProjectCostAmount(visibleMetric?.wipCost, amountRestrictedReason || undefined) }}</strong>
      </div>
      <div>
        <span>未解决差异</span>
        <strong>{{ visibleMetric?.sourceVisible === false ? projectCostMessages.sourceRestricted : (visibleMetric?.openVarianceCount ?? '-') }}</strong>
      </div>
      <div>
        <span>发货口径经营毛利</span>
        <strong>{{ formatProjectCostAmount(visibleMetric?.shipmentGrossMargin, amountRestrictedReason || undefined) }}</strong>
      </div>
    </section>

    <el-empty v-if="!loading && records.length === 0" description="暂无项目成本核算记录" />
    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无项目成本核算记录'" stripe>
        <el-table-column label="项目" min-width="210" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectNo }} {{ row.projectName }}</template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户" min-width="140" show-overflow-tooltip />
        <el-table-column prop="ownerDisplayName" label="负责人" min-width="110" show-overflow-tooltip />
        <el-table-column label="项目状态" min-width="100">
          <template #default="{ row }">{{ projectCostProjectStatusLabel(row.projectStatus) }}</template>
        </el-table-column>
        <el-table-column label="核算状态" min-width="110">
          <template #default="{ row }"><ProjectCostCalculationStatusTag :status="row.calculationStatus" /></template>
        </el-table-column>
        <el-table-column label="当前性" min-width="100">
          <template #default="{ row }">{{ projectCostFreshnessLabel(row.freshnessStatus) }}</template>
        </el-table-column>
        <el-table-column label="完整性" min-width="120">
          <template #default="{ row }">
            <CompletenessTag :status="row.completenessStatus" />
            <span class="project-cost-muted-note">{{ completenessText(row) }}</span>
            <span v-if="actionReason(row, 'CONFIRM')" class="project-cost-danger-note">{{ actionReason(row, 'CONFIRM') }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="cutoffDate" label="截止日期" min-width="110" />
        <el-table-column label="总成本" min-width="130" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ formatProjectCostAmount(row.totalCost, restrictedMoneyReason(row) || undefined) }}</span></template>
        </el-table-column>
        <el-table-column label="在制成本" min-width="130" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ formatProjectCostAmount(row.wipCost, restrictedMoneyReason(row) || undefined) }}</span></template>
        </el-table-column>
        <el-table-column label="发货收入" min-width="130" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ formatProjectCostAmount(row.shipmentPretaxRevenue, restrictedMoneyReason(row) || undefined) }}</span></template>
        </el-table-column>
        <el-table-column label="发货经营毛利" min-width="150" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ formatProjectCostAmount(row.shipmentGrossMargin, restrictedMoneyReason(row) || undefined) }}</span></template>
        </el-table-column>
        <el-table-column label="差异" min-width="140">
          <template #default="{ row }">{{ varianceText(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="170">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-project-cost" @click="viewProject(row)">详情</el-button>
            <el-button
              v-if="canRunCalculate(row)"
              size="small"
              text
              type="primary"
              data-test="calculate-project-cost"
              :loading="actionLoadingId === String(row.projectId)"
              :title="actionReason(row, 'CALCULATE')"
              @click="calculateProject(row)"
            >
              核算
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
