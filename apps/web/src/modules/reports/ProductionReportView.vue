<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { businessReportingApi, type ProductionReportRow, type ProductionReportSummary, type ReportTraceRecord } from '../../shared/api/businessReportingApi'
import ReportFilterBar, { type ReportFilterField } from './ReportFilterBar.vue'
import ReportMetricStrip from './ReportMetricStrip.vue'
import ReportTracePanel from './ReportTracePanel.vue'
import { reportStatusText } from './reportPageHelpers'

const filters = reactive<Record<string, string>>({ dateFrom: '', dateTo: '', keyword: '', status: '', workOrderId: '', materialId: '' })
const fields: ReportFilterField[] = [
  { key: 'dateFrom', label: '开始日期', name: 'report-date-from', type: 'date' },
  { key: 'dateTo', label: '结束日期', name: 'report-date-to', type: 'date' },
  { key: 'keyword', label: '关键字', name: 'report-keyword', placeholder: '工单或产品' },
  { key: 'status', label: '状态', name: 'report-status' },
  { key: 'workOrderId', label: '工单 ID', name: 'report-work-order-id' },
  { key: 'materialId', label: '物料 ID', name: 'report-material-id' },
]
const loading = ref(false)
const error = ref('')
const rows = ref<ProductionReportRow[]>([])
const summary = ref<ProductionReportSummary | null>(null)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const traceVisible = ref(false)
const traceRows = ref<ReportTraceRecord[]>([])
const traceLoading = ref(false)
const traceError = ref('')
const metrics = computed(() => summary.value ? [
  { label: '工单数量', value: summary.value.workOrderCount },
  { label: '计划数量', value: summary.value.plannedQuantity },
  { label: '领料原发生', value: summary.value.issuedOriginalQuantity ?? summary.value.issuedQuantity ?? '0.000' },
  { label: '退料数量', value: summary.value.materialReturnQuantity ?? '0.000' },
  { label: '补料数量', value: summary.value.materialSupplementQuantity ?? '0.000' },
  { label: '领料净数量', value: summary.value.issuedNetQuantity ?? summary.value.issuedQuantity ?? '0.000' },
  { label: '完成数量', value: summary.value.completedQuantity ?? summary.value.completionReceiptQuantity ?? '0.000' },
  { label: '报工数量', value: summary.value.reportedQuantity },
  { label: '合格数量', value: summary.value.qualifiedQuantity },
  { label: '完工入库数量', value: summary.value.completionReceiptQuantity },
  { label: '完工率', value: summary.value.completionRate },
] : [])

function updateFilters(value: Record<string, string | number | undefined>) {
  Object.assign(filters, value)
}

async function loadReport(targetPage = page.value) {
  loading.value = true
  error.value = ''
  try {
    const result = await businessReportingApi.production.list({
      ...filters,
      workOrderId: filters.workOrderId || undefined,
      materialId: filters.materialId || undefined,
      page: targetPage,
      pageSize: pageSize.value,
    })
    rows.value = result.items
    summary.value = result.summary
    page.value = result.page
    total.value = result.total
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '生产执行报表加载失败'
    rows.value = []
    summary.value = null
  } finally {
    loading.value = false
  }
}
function search() { void loadReport(1) }
function reset() {
  Object.assign(filters, { dateFrom: '', dateTo: '', keyword: '', status: '', workOrderId: '', materialId: '' })
  void loadReport(1)
}
function changePageSize(size: number) {
  pageSize.value = size
  void loadReport(1)
}

async function openTrace(row: ProductionReportRow) {
  traceVisible.value = true
  traceLoading.value = true
  traceError.value = ''
  try {
    const result = await businessReportingApi.production.traces.list({ traceKey: row.traceKey, dateFrom: filters.dateFrom, dateTo: filters.dateTo, page: 1, pageSize: pageSize.value })
    traceRows.value = result.items
  } catch (cause) {
    traceError.value = cause instanceof Error ? cause.message : '来源追溯加载失败'
    traceRows.value = []
  } finally {
    traceLoading.value = false
  }
}
onMounted(() => { void loadReport(1) })
</script>

<template>
  <section class="report-page">
    <header class="report-page__header"><h1>生产执行</h1><p>生产执行以工单、领料、报工和完工入库为依据。</p></header>
    <ReportFilterBar
      :model-value="filters"
      :fields="fields"
      :loading="loading"
      @update:model-value="updateFilters"
      @search="search"
      @reset="reset"
    />
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <ReportMetricStrip :metrics="metrics" />
    <div class="report-table-scroll">
      <el-table :data="rows" empty-text="暂无生产执行数据" stripe>
        <el-table-column prop="workOrderNo" label="工单" min-width="160" show-overflow-tooltip />
        <el-table-column prop="productMaterialName" label="产品" min-width="160" show-overflow-tooltip />
        <el-table-column prop="plannedQuantity" label="计划" min-width="100" align="right" />
        <el-table-column label="领料原发生" min-width="120" align="right">
          <template #default="{ row }">{{ row.issuedOriginalQuantity ?? row.issuedQuantity ?? '0.000' }}</template>
        </el-table-column>
        <el-table-column label="退料数量" min-width="110" align="right">
          <template #default="{ row }">{{ row.materialReturnQuantity ?? '0.000' }}</template>
        </el-table-column>
        <el-table-column label="补料数量" min-width="110" align="right">
          <template #default="{ row }">{{ row.materialSupplementQuantity ?? '0.000' }}</template>
        </el-table-column>
        <el-table-column label="领料净数量" min-width="120" align="right">
          <template #default="{ row }">{{ row.issuedNetQuantity ?? row.issuedQuantity ?? '0.000' }}</template>
        </el-table-column>
        <el-table-column prop="reportedQuantity" label="报工" min-width="100" align="right" />
        <el-table-column label="完成数量" min-width="110" align="right">
          <template #default="{ row }">{{ row.completedQuantity ?? row.completionReceiptQuantity ?? '0.000' }}</template>
        </el-table-column>
        <el-table-column prop="completionReceiptQuantity" label="完工入库" min-width="120" align="right" />
        <el-table-column prop="completionRate" label="完工率" min-width="100" align="right" />
        <el-table-column label="状态" min-width="120">
          <template #default="{ row }">{{ reportStatusText(row.status) }}</template>
        </el-table-column>
        <el-table-column label="来源" width="100"><template #default="{ row }"><el-button data-test="open-report-trace" link type="primary" @click="openTrace(row)">追溯</el-button></template></el-table-column>
      </el-table>
    </div>
    <el-pagination :current-page="page" :page-size="pageSize" :page-sizes="[10, 20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next" @current-change="loadReport" @size-change="changePageSize" />
    <ReportTracePanel :visible="traceVisible" :rows="traceRows" :loading="traceLoading" :error="traceError" @close="traceVisible = false" />
  </section>
</template>

<style scoped>
.report-page__header h1 { font-size: 22px; margin: 0 0 6px; }
.report-page__header p { color: var(--qherp-steel); margin: 0; }
.report-table-scroll { overflow-x: auto; }
</style>
