<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { businessReportingApi, type ProcurementReportRow, type ProcurementReportSummary, type ReportTraceRecord } from '../../shared/api/businessReportingApi'
import ReportFilterBar, { type ReportFilterField } from './ReportFilterBar.vue'
import ReportMetricStrip from './ReportMetricStrip.vue'
import ReportTracePanel from './ReportTracePanel.vue'

const filters = reactive<Record<string, string>>({ dateFrom: '', dateTo: '', keyword: '', status: '', supplierId: '', materialId: '' })
const fields: ReportFilterField[] = [
  { key: 'dateFrom', label: '开始日期', name: 'report-date-from', type: 'date' },
  { key: 'dateTo', label: '结束日期', name: 'report-date-to', type: 'date' },
  { key: 'keyword', label: '关键字', name: 'report-keyword', placeholder: '单号、供应商或物料' },
  { key: 'status', label: '状态', name: 'report-status' },
  { key: 'supplierId', label: '供应商 ID', name: 'report-supplier-id' },
  { key: 'materialId', label: '物料 ID', name: 'report-material-id' },
]
const loading = ref(false)
const error = ref('')
const rows = ref<ProcurementReportRow[]>([])
const summary = ref<ProcurementReportSummary | null>(null)
const page = ref(1)
const pageSize = 20
const total = ref(0)
const traceVisible = ref(false)
const traceRows = ref<ReportTraceRecord[]>([])
const traceLoading = ref(false)
const traceError = ref('')
const metrics = computed(() => summary.value ? [
  { label: '入库数量', value: summary.value.receiptQuantity },
  { label: '入库金额', value: summary.value.receiptAmount },
  { label: '应付金额', value: summary.value.payableAmount ?? '0.00' },
  { label: '已付金额', value: summary.value.paidAmount ?? '0.00' },
  { label: '未付金额', value: summary.value.unpaidAmount ?? '0.00' },
  { label: '来源数量', value: summary.value.sourceCount },
] : [])

function updateFilters(value: Record<string, string | number | undefined>) {
  Object.assign(filters, value)
}

async function loadReport(targetPage = page.value) {
  loading.value = true
  error.value = ''
  try {
    const result = await businessReportingApi.procurement.list({
      ...filters,
      supplierId: filters.supplierId || undefined,
      materialId: filters.materialId || undefined,
      page: targetPage,
      pageSize,
    })
    rows.value = result.items
    summary.value = result.summary
    page.value = result.page
    total.value = result.total
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '采购经营报表加载失败'
    rows.value = []
    summary.value = null
  } finally {
    loading.value = false
  }
}
function search() { void loadReport(1) }
function reset() {
  Object.assign(filters, { dateFrom: '', dateTo: '', keyword: '', status: '', supplierId: '', materialId: '' })
  void loadReport(1)
}
async function openTrace(row: ProcurementReportRow) {
  traceVisible.value = true
  traceLoading.value = true
  traceError.value = ''
  try {
    const result = await businessReportingApi.procurement.traces.list({ traceKey: row.traceKey, dateFrom: filters.dateFrom, dateTo: filters.dateTo, page: 1, pageSize })
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
      <h1>采购经营</h1>
      <p>采购经营汇总以已过账采购入库为主要经营发生口径，不表达为正式成本入账。</p>
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
      <el-table :data="rows" empty-text="暂无采购经营数据" stripe>
        <el-table-column prop="sourceNo" label="采购入库" min-width="160" show-overflow-tooltip />
        <el-table-column prop="purchaseOrderNo" label="采购订单" min-width="160" show-overflow-tooltip />
        <el-table-column prop="supplierName" label="供应商" min-width="140" show-overflow-tooltip />
        <el-table-column prop="materialName" label="物料" min-width="140" show-overflow-tooltip />
        <el-table-column prop="quantity" label="数量" min-width="110" align="right" />
        <el-table-column prop="amount" label="经营金额" min-width="130" align="right" />
        <el-table-column label="来源" width="100"><template #default="{ row }"><el-button data-test="open-report-trace" link type="primary" @click="openTrace(row)">追溯</el-button></template></el-table-column>
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
