<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { businessReportingApi, type ReportTraceRecord, type SalesReportRow, type SalesReportSummary } from '../../shared/api/businessReportingApi'
import ReportFilterBar, { type ReportFilterField } from './ReportFilterBar.vue'
import ReportMetricStrip from './ReportMetricStrip.vue'
import ReportTracePanel from './ReportTracePanel.vue'
import { reportSourceTypeText } from './reportPageHelpers'

const filters = reactive<Record<string, string>>({
  dateFrom: '',
  dateTo: '',
  keyword: '',
  status: '',
  customerId: '',
  materialId: '',
})
const fields: ReportFilterField[] = [
  { key: 'dateFrom', label: '开始日期', name: 'report-date-from', type: 'date' },
  { key: 'dateTo', label: '结束日期', name: 'report-date-to', type: 'date' },
  { key: 'keyword', label: '关键字', name: 'report-keyword', placeholder: '单号、客户或物料' },
  { key: 'status', label: '状态', name: 'report-status' },
  { key: 'customerId', label: '客户 ID', name: 'report-customer-id' },
  { key: 'materialId', label: '物料 ID', name: 'report-material-id' },
]
const loading = ref(false)
const error = ref('')
const rows = ref<SalesReportRow[]>([])
const summary = ref<SalesReportSummary | null>(null)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const traceVisible = ref(false)
const traceRows = ref<ReportTraceRecord[]>([])
const traceLoading = ref(false)
const traceError = ref('')

const metrics = computed(() => summary.value ? [
  { label: '原发生金额', value: summary.value.salesOriginalAmount ?? summary.value.shipmentAmount ?? '0.00' },
  { label: '退货金额', value: summary.value.salesReturnAmount ?? '0.00' },
  { label: '销售净额', value: summary.value.salesNetAmount ?? summary.value.shipmentAmount ?? '0.00' },
  { label: '原发生数量', value: summary.value.salesOriginalQuantity ?? summary.value.shipmentQuantity ?? '0.000' },
  { label: '退货数量', value: summary.value.salesReturnQuantity ?? '0.000' },
  { label: '净数量', value: summary.value.salesNetQuantity ?? summary.value.shipmentQuantity ?? '0.000' },
  { label: '应收金额', value: summary.value.receivableAmount ?? '0.00' },
  { label: '已收金额', value: summary.value.receivedAmount ?? '0.00' },
  { label: '未收金额', value: summary.value.unreceivedAmount ?? '0.00' },
  { label: '来源数量', value: summary.value.sourceCount },
] : [])

function updateFilters(value: Record<string, string | number | undefined>) {
  Object.assign(filters, value)
}

async function loadReport(targetPage = page.value) {
  loading.value = true
  error.value = ''
  try {
    const result = await businessReportingApi.sales.list({
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      keyword: filters.keyword,
      status: filters.status,
      customerId: filters.customerId || undefined,
      materialId: filters.materialId || undefined,
      page: targetPage,
      pageSize: pageSize.value,
    })
    rows.value = result.items
    summary.value = result.summary
    page.value = result.page
    total.value = result.total
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '销售经营报表加载失败'
    rows.value = []
    summary.value = null
  } finally {
    loading.value = false
  }
}

function search() {
  void loadReport(1)
}

function reset() {
  filters.dateFrom = ''
  filters.dateTo = ''
  filters.keyword = ''
  filters.status = ''
  filters.customerId = ''
  filters.materialId = ''
  void loadReport(1)
}

function changePageSize(size: number) {
  pageSize.value = size
  void loadReport(1)
}

async function openTrace(row: SalesReportRow) {
  traceVisible.value = true
  traceLoading.value = true
  traceError.value = ''
  try {
    const result = await businessReportingApi.sales.traces.list({
      traceKey: row.traceKey,
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      page: 1,
      pageSize: pageSize.value,
    })
    traceRows.value = result.items
  } catch (cause) {
    traceError.value = cause instanceof Error ? cause.message : '来源追溯加载失败'
    traceRows.value = []
  } finally {
    traceLoading.value = false
  }
}

onMounted(() => {
  void loadReport(1)
})
</script>

<template>
  <section class="report-page">
    <header class="report-page__header">
      <h1>销售经营</h1>
      <p>销售经营汇总以已过账销售出库为主要经营发生口径，金额不表达为正式收入确认。</p>
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
      <el-table :data="rows" empty-text="暂无销售经营数据" stripe>
        <el-table-column label="来源类型" min-width="110">
          <template #default="{ row }">{{ reportSourceTypeText(row.sourceType) }}</template>
        </el-table-column>
        <el-table-column prop="sourceNo" label="来源单号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="salesOrderNo" label="销售订单" min-width="160" show-overflow-tooltip />
        <el-table-column prop="customerName" label="客户" min-width="140" show-overflow-tooltip />
        <el-table-column prop="materialName" label="物料" min-width="140" show-overflow-tooltip />
        <el-table-column prop="businessDate" label="业务日期" min-width="110" />
        <el-table-column label="原发生数量" min-width="120" align="right">
          <template #default="{ row }">{{ row.salesOriginalQuantity ?? row.quantity ?? '0.000' }}</template>
        </el-table-column>
        <el-table-column label="退货数量" min-width="120" align="right">
          <template #default="{ row }">{{ row.salesReturnQuantity ?? '0.000' }}</template>
        </el-table-column>
        <el-table-column label="净数量" min-width="110" align="right">
          <template #default="{ row }">{{ row.salesNetQuantity ?? row.quantity ?? '0.000' }}</template>
        </el-table-column>
        <el-table-column label="原发生金额" min-width="130" align="right">
          <template #default="{ row }">{{ row.salesOriginalAmount ?? row.amount ?? '0.00' }}</template>
        </el-table-column>
        <el-table-column label="退货金额" min-width="120" align="right">
          <template #default="{ row }">{{ row.salesReturnAmount ?? '0.00' }}</template>
        </el-table-column>
        <el-table-column label="销售净额" min-width="120" align="right">
          <template #default="{ row }">{{ row.salesNetAmount ?? row.amount ?? '0.00' }}</template>
        </el-table-column>
        <el-table-column label="来源" width="100">
          <template #default="{ row }">
            <el-button data-test="open-report-trace" link type="primary" @click="openTrace(row)">追溯</el-button>
          </template>
        </el-table-column>
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
