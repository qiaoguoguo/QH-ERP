<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { businessReportingApi, type InventoryReportRow, type InventoryReportSummary, type ReportTraceRecord } from '../../shared/api/businessReportingApi'
import ReportFilterBar, { type ReportFilterField } from './ReportFilterBar.vue'
import ReportMetricStrip from './ReportMetricStrip.vue'
import ReportTracePanel from './ReportTracePanel.vue'

const filters = reactive<Record<string, string>>({
  dateFrom: '',
  dateTo: '',
  keyword: '',
  warehouseId: '',
  materialId: '',
})
const fields: ReportFilterField[] = [
  { key: 'dateFrom', label: '开始日期', name: 'report-date-from', type: 'date' },
  { key: 'dateTo', label: '结束日期', name: 'report-date-to', type: 'date' },
  { key: 'keyword', label: '关键字', name: 'report-keyword', placeholder: '仓库或物料' },
  { key: 'warehouseId', label: '仓库 ID', name: 'report-warehouse-id' },
  { key: 'materialId', label: '物料 ID', name: 'report-material-id' },
]
const loading = ref(false)
const error = ref('')
const rows = ref<InventoryReportRow[]>([])
const summary = ref<InventoryReportSummary | null>(null)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const traceVisible = ref(false)
const traceRows = ref<ReportTraceRecord[]>([])
const traceLoading = ref(false)
const traceError = ref('')

const metrics = computed(() => summary.value ? [
  { label: '期初数量', value: summary.value.openingQuantity },
  { label: '入库原发生', value: summary.value.inboundOriginalQuantity ?? summary.value.inQuantity ?? '0.000' },
  { label: '入库反向发生', value: summary.value.inboundReverseQuantity ?? '0.000' },
  { label: '入库净额', value: summary.value.inboundNetQuantity ?? summary.value.inQuantity ?? '0.000' },
  { label: '出库原发生', value: summary.value.outboundOriginalQuantity ?? summary.value.outQuantity ?? '0.000' },
  { label: '出库反向发生', value: summary.value.outboundReverseQuantity ?? '0.000' },
  { label: '出库净额', value: summary.value.outboundNetQuantity ?? summary.value.outQuantity ?? '0.000' },
  { label: '库存净变化', value: summary.value.inventoryNetChangeQuantity ?? '0.000' },
  { label: '调整数量', value: summary.value.adjustQuantity },
  { label: '期末数量', value: summary.value.closingQuantity },
  { label: '来源数量', value: summary.value.sourceCount },
] : [])

function updateFilters(value: Record<string, string | number | undefined>) {
  Object.assign(filters, value)
}

async function loadReport(targetPage = page.value) {
  loading.value = true
  error.value = ''
  try {
    const result = await businessReportingApi.inventory.list({
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      keyword: filters.keyword,
      warehouseId: filters.warehouseId || undefined,
      materialId: filters.materialId || undefined,
      page: targetPage,
      pageSize: pageSize.value,
    })
    rows.value = result.items
    summary.value = result.summary
    page.value = result.page
    total.value = result.total
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '库存收发存报表加载失败'
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
  filters.warehouseId = ''
  filters.materialId = ''
  void loadReport(1)
}

function changePageSize(size: number) {
  pageSize.value = size
  void loadReport(1)
}

async function openTrace(row: InventoryReportRow) {
  traceVisible.value = true
  traceLoading.value = true
  traceError.value = ''
  try {
    const result = await businessReportingApi.inventory.traces.list({
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
      <h1>库存收发存</h1>
      <p>库存收发存按库存流水和当前余额推算，第一版不使用期间快照。</p>
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
      <el-table :data="rows" empty-text="暂无库存收发存数据" stripe>
        <el-table-column prop="warehouseName" label="仓库" min-width="140" />
        <el-table-column prop="materialName" label="物料" min-width="160" show-overflow-tooltip />
        <el-table-column prop="openingQuantity" label="期初" min-width="110" align="right" />
        <el-table-column label="入库原发生" min-width="120" align="right">
          <template #default="{ row }">{{ row.inboundOriginalQuantity ?? row.inQuantity ?? '0.000' }}</template>
        </el-table-column>
        <el-table-column label="入库反向发生" min-width="130" align="right">
          <template #default="{ row }">{{ row.inboundReverseQuantity ?? '0.000' }}</template>
        </el-table-column>
        <el-table-column label="入库净额" min-width="110" align="right">
          <template #default="{ row }">{{ row.inboundNetQuantity ?? row.inQuantity ?? '0.000' }}</template>
        </el-table-column>
        <el-table-column label="出库原发生" min-width="120" align="right">
          <template #default="{ row }">{{ row.outboundOriginalQuantity ?? row.outQuantity ?? '0.000' }}</template>
        </el-table-column>
        <el-table-column label="出库反向发生" min-width="130" align="right">
          <template #default="{ row }">{{ row.outboundReverseQuantity ?? '0.000' }}</template>
        </el-table-column>
        <el-table-column label="出库净额" min-width="110" align="right">
          <template #default="{ row }">{{ row.outboundNetQuantity ?? row.outQuantity ?? '0.000' }}</template>
        </el-table-column>
        <el-table-column label="库存净变化" min-width="120" align="right">
          <template #default="{ row }">{{ row.inventoryNetChangeQuantity ?? '0.000' }}</template>
        </el-table-column>
        <el-table-column prop="adjustQuantity" label="调整" min-width="110" align="right" />
        <el-table-column prop="closingQuantity" label="期末" min-width="110" align="right" />
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
