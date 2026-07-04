<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { businessReportingApi, type CostReportRow, type CostReportSummary, type ReportTraceRecord } from '../../shared/api/businessReportingApi'
import ReportFilterBar, { type ReportFilterField } from './ReportFilterBar.vue'
import ReportMetricStrip from './ReportMetricStrip.vue'
import ReportTracePanel from './ReportTracePanel.vue'

const filters = reactive<Record<string, string>>({ dateFrom: '', dateTo: '', keyword: '', status: '', workOrderId: '', materialId: '' })
const fields: ReportFilterField[] = [
  { key: 'dateFrom', label: '开始日期', name: 'report-date-from', type: 'date' },
  { key: 'dateTo', label: '结束日期', name: 'report-date-to', type: 'date' },
  { key: 'keyword', label: '关键字', name: 'report-keyword', placeholder: '成本单、工单或产品' },
  { key: 'status', label: '状态', name: 'report-status' },
  { key: 'workOrderId', label: '工单 ID', name: 'report-work-order-id' },
  { key: 'materialId', label: '物料 ID', name: 'report-material-id' },
]
const loading = ref(false)
const error = ref('')
const rows = ref<CostReportRow[]>([])
const summary = ref<CostReportSummary | null>(null)
const page = ref(1)
const pageSize = 20
const total = ref(0)
const traceVisible = ref(false)
const traceRows = ref<ReportTraceRecord[]>([])
const traceLoading = ref(false)
const traceError = ref('')
const metrics = computed(() => summary.value ? [
  { label: '材料成本', value: summary.value.materialCostAmount },
  { label: '人工成本', value: summary.value.laborCostAmount },
  { label: '制造费用', value: summary.value.manufacturingOverheadAmount },
  { label: '其他成本', value: summary.value.otherCostAmount },
  { label: '成本合计', value: summary.value.totalCostAmount },
  { label: '来源数量', value: summary.value.sourceCount },
] : [])

function updateFilters(value: Record<string, string | number | undefined>) {
  Object.assign(filters, value)
}

async function loadReport(targetPage = page.value) {
  loading.value = true
  error.value = ''
  try {
    const result = await businessReportingApi.cost.list({
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      keyword: filters.keyword,
      status: filters.status,
      workOrderId: filters.workOrderId || undefined,
      materialId: filters.materialId || undefined,
      page: targetPage,
      pageSize,
    })
    rows.value = result.items
    summary.value = result.summary
    page.value = result.page
    total.value = result.total
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '成本归集报表加载失败'
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
async function openTrace(row: CostReportRow) {
  traceVisible.value = true
  traceLoading.value = true
  traceError.value = ''
  try {
    const result = await businessReportingApi.cost.traces.list({ traceKey: row.traceKey, dateFrom: filters.dateFrom, dateTo: filters.dateTo, page: 1, pageSize })
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
    <header class="report-page__header">
      <h1>成本归集</h1>
      <p>成本归集展示生产相关经营成本归集口径，formalAccounting=false，不等同正式财务成本入账。</p>
    </header>
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
      <el-table :data="rows" empty-text="暂无成本归集数据" stripe>
        <el-table-column prop="recordNo" label="成本单号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="workOrderNo" label="生产工单" min-width="150" show-overflow-tooltip />
        <el-table-column prop="productMaterialName" label="产品" min-width="150" show-overflow-tooltip />
        <el-table-column prop="costType" label="成本类型" min-width="120" />
        <el-table-column prop="sourceDocumentNo" label="来源单据" min-width="150" show-overflow-tooltip />
        <el-table-column prop="amount" label="成本金额" min-width="120" align="right" />
        <el-table-column prop="formalAccounting" label="正式入账" min-width="100">
          <template #default="{ row }">{{ row.formalAccounting ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="来源" width="100">
          <template #default="{ row }">
            <el-button data-test="open-report-trace" link type="primary" @click="openTrace(row)">追溯</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination :current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next, total" @current-change="loadReport" />
    <ReportTracePanel :visible="traceVisible" :rows="traceRows" :loading="traceLoading" :error="traceError" @close="traceVisible = false" />
  </section>
</template>

<style scoped>
.report-page__header h1 { font-size: 22px; margin: 0 0 6px; }
.report-page__header p { color: #606266; margin: 0; }
.report-table-scroll { overflow-x: auto; }
</style>
