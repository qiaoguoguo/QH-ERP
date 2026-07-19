<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { projectCostApi, type ProjectCostVarianceRecord, type ProjectCostVarianceSeverity, type ProjectCostVarianceStatus } from '../../../shared/api/projectCostApi'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { pageItems, pageTotal } from '../../system/shared/pageHelpers'
import SourceStatusTag from './SourceStatusTag.vue'
import VarianceSeverityTag from './VarianceSeverityTag.vue'
import {
  formatProjectCostAmount,
  projectCostErrorMessage,
  projectCostMessages,
  projectCostSourceTypeOptions,
  projectCostSourceTypeLabel,
  projectCostVarianceStatusLabel,
  projectCostVarianceTypeLabel,
  restrictedMoneyReason,
  restrictedSourceReason,
} from './projectCostPageHelpers'
import './ProjectCostShared.css'

const filters = reactive<{
  projectId: string
  varianceType: string
  severity?: ProjectCostVarianceSeverity
  status?: ProjectCostVarianceStatus
  sourceType: string
  businessDateFrom: string
  businessDateTo: string
  sourceRestricted?: boolean
}>({
  projectId: '',
  varianceType: '',
  severity: undefined,
  status: undefined,
  sourceType: '',
  businessDateFrom: '',
  businessDateTo: '',
  sourceRestricted: undefined,
})
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<ProjectCostVarianceRecord[]>([])
const loading = ref(true)
const error = ref('')

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await projectCostApi.calculations.variances(undefined, {
      projectId: filters.projectId,
      varianceType: filters.varianceType,
      severity: filters.severity,
      status: filters.status,
      sourceType: filters.sourceType,
      businessDateFrom: filters.businessDateFrom,
      businessDateTo: filters.businessDateTo,
      sourceRestricted: filters.sourceRestricted,
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
  filters.projectId = ''
  filters.varianceType = ''
  filters.severity = undefined
  filters.status = undefined
  filters.sourceType = ''
  filters.businessDateFrom = ''
  filters.businessDateTo = ''
  filters.sourceRestricted = undefined
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

function sourceText(row: ProjectCostVarianceRecord) {
  return restrictedSourceReason(row) || row.sourceNo || row.sourceSummary || projectCostMessages.sourceRestricted
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="项目成本差异" description="跟踪项目成本核算中的暂估、未定价、来源变化和阻断差异。">
    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="项目"><el-input v-model="filters.projectId" name="project-cost-variance-project-id" clearable placeholder="项目 ID" /></el-form-item>
        <el-form-item label="差异类型"><el-input v-model="filters.varianceType" clearable placeholder="差异类型" /></el-form-item>
        <el-form-item label="严重级别">
          <el-select v-model="filters.severity" clearable placeholder="全部级别">
            <el-option label="提示" value="INFO" />
            <el-option label="警告" value="WARNING" />
            <el-option label="阻断" value="BLOCKING" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="待处理" value="OPEN" />
            <el-option label="已解决" value="RESOLVED" />
            <el-option label="已替代" value="SUPERSEDED" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源类型">
          <el-select v-model="filters.sourceType" clearable placeholder="全部来源">
            <el-option v-for="option in projectCostSourceTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务日期起"><el-date-picker v-model="filters.businessDateFrom" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="起始日期" /></el-form-item>
        <el-form-item label="业务日期止"><el-date-picker v-model="filters.businessDateTo" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="截止日期" /></el-form-item>
        <el-form-item label="受限">
          <el-select v-model="filters.sourceRestricted" clearable placeholder="全部">
            <el-option label="来源受限" :value="true" />
            <el-option label="来源可见" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作">
          <el-button data-test="search-project-cost-variances" type="primary" @click="search">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="项目成本差异加载中" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无项目成本差异'" stripe>
        <el-table-column label="项目" min-width="210" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectNo }} {{ row.projectName }}</template>
        </el-table-column>
        <el-table-column label="严重级别" min-width="110"><template #default="{ row }"><VarianceSeverityTag :severity="row.severity" /></template></el-table-column>
        <el-table-column label="差异类型" min-width="160"><template #default="{ row }">{{ projectCostVarianceTypeLabel(row.varianceType) }}</template></el-table-column>
        <el-table-column label="来源类型" min-width="130"><template #default="{ row }">{{ projectCostSourceTypeLabel(row.sourceType) }}</template></el-table-column>
        <el-table-column label="来源" min-width="190" show-overflow-tooltip><template #default="{ row }">{{ sourceText(row) }}</template></el-table-column>
        <el-table-column label="预计金额" min-width="130" align="right"><template #default="{ row }"><span class="numeric-cell">{{ formatProjectCostAmount(row.expectedAmount, restrictedMoneyReason(row) || undefined) }}</span></template></el-table-column>
        <el-table-column label="实际金额" min-width="130" align="right"><template #default="{ row }"><span class="numeric-cell">{{ formatProjectCostAmount(row.actualAmount, restrictedMoneyReason(row) || undefined) }}</span></template></el-table-column>
        <el-table-column label="差额" min-width="130" align="right"><template #default="{ row }"><span class="numeric-cell">{{ formatProjectCostAmount(row.differenceAmount, restrictedMoneyReason(row) || undefined) }}</span></template></el-table-column>
        <el-table-column label="状态" min-width="110"><template #default="{ row }">{{ projectCostVarianceStatusLabel(row.status) }}</template></el-table-column>
        <el-table-column prop="resolvedAdjustmentNo" label="关联调整" min-width="150" show-overflow-tooltip />
        <el-table-column prop="description" label="说明" min-width="210" show-overflow-tooltip />
        <el-table-column label="来源状态" min-width="110"><template #default="{ row }"><SourceStatusTag :status="row.sourceVisible === false ? 'RESTRICTED' : 'ACTUAL'" /></template></el-table-column>
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
