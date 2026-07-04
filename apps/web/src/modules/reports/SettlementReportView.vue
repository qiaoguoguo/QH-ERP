<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { businessReportingApi, type ReportTraceRecord, type SettlementReportRow, type SettlementReportSummary } from '../../shared/api/businessReportingApi'
import ReportFilterBar, { type ReportFilterField } from './ReportFilterBar.vue'
import ReportMetricStrip from './ReportMetricStrip.vue'
import ReportTracePanel from './ReportTracePanel.vue'

const filters = reactive<Record<string, string>>({ dateFrom: '', dateTo: '', keyword: '', status: '', customerId: '', supplierId: '' })
const fields: ReportFilterField[] = [
  { key: 'dateFrom', label: '开始日期', name: 'report-date-from', type: 'date' },
  { key: 'dateTo', label: '结束日期', name: 'report-date-to', type: 'date' },
  { key: 'keyword', label: '关键字', name: 'report-keyword', placeholder: '单号、客户或供应商' },
  { key: 'status', label: '状态', name: 'report-status' },
  { key: 'customerId', label: '客户 ID', name: 'report-customer-id' },
  { key: 'supplierId', label: '供应商 ID', name: 'report-supplier-id' },
]
const loading = ref(false)
const error = ref('')
const rows = ref<SettlementReportRow[]>([])
const summary = ref<SettlementReportSummary | null>(null)
const page = ref(1)
const pageSize = 20
const total = ref(0)
const traceVisible = ref(false)
const traceRows = ref<ReportTraceRecord[]>([])
const traceLoading = ref(false)
const traceError = ref('')
const metrics = computed(() => summary.value ? [
  { label: '应收金额', value: summary.value.receivableAmount },
  { label: '已收金额', value: summary.value.receivedAmount },
  { label: '未收金额', value: summary.value.unreceivedAmount },
  { label: '应付金额', value: summary.value.payableAmount },
  { label: '已付金额', value: summary.value.paidAmount },
  { label: '未付金额', value: summary.value.unpaidAmount },
] : [])

function updateFilters(value: Record<string, string | number | undefined>) {
  Object.assign(filters, value)
}

function settlementTypeText(type: SettlementReportRow['settlementType']) {
  const map: Record<SettlementReportRow['settlementType'], string> = {
    RECEIVABLE: '应收',
    PAYABLE: '应付',
    RECEIPT: '收款',
    PAYMENT: '付款',
  }
  return map[type] ?? type
}

async function loadReport(targetPage = page.value) {
  loading.value = true
  error.value = ''
  try {
    const result = await businessReportingApi.settlement.list({
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      keyword: filters.keyword,
      status: filters.status,
      customerId: filters.customerId || undefined,
      supplierId: filters.supplierId || undefined,
      page: targetPage,
      pageSize,
    })
    rows.value = result.items
    summary.value = result.summary
    page.value = result.page
    total.value = result.total
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '往来收付报表加载失败'
    rows.value = []
    summary.value = null
  } finally {
    loading.value = false
  }
}
function search() { void loadReport(1) }
function reset() {
  Object.assign(filters, { dateFrom: '', dateTo: '', keyword: '', status: '', customerId: '', supplierId: '' })
  void loadReport(1)
}
async function openTrace(row: SettlementReportRow) {
  traceVisible.value = true
  traceLoading.value = true
  traceError.value = ''
  try {
    const result = await businessReportingApi.settlement.traces.list({ traceKey: row.traceKey, dateFrom: filters.dateFrom, dateTo: filters.dateTo, page: 1, pageSize })
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
      <h1>往来收付</h1>
      <p>往来收付汇总应收、应付、收款和付款经营状态，用于查看业务收付进展。</p>
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
      <el-table :data="rows" empty-text="暂无往来收付数据" stripe>
        <el-table-column label="类型" min-width="90">
          <template #default="{ row }">{{ settlementTypeText(row.settlementType) }}</template>
        </el-table-column>
        <el-table-column prop="sourceNo" label="来源单号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="partyName" label="往来对象" min-width="150" show-overflow-tooltip />
        <el-table-column prop="businessDate" label="业务日期" min-width="120" />
        <el-table-column prop="dueDate" label="到期日" min-width="120" />
        <el-table-column prop="totalAmount" label="总金额" min-width="120" align="right" />
        <el-table-column prop="settledAmount" label="已结金额" min-width="120" align="right" />
        <el-table-column prop="unsettledAmount" label="未结金额" min-width="120" align="right" />
        <el-table-column prop="status" label="状态" min-width="110" />
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
