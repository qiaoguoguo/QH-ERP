<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  qualityInventoryStatusApi,
  type QualityInspectionRecord,
  type QualityInspectionStatus,
} from '../../shared/api/qualityInventoryStatusApi'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { errorMessage, pageItems } from '../system/shared/pageHelpers'
import { formatQuantity } from '../inventory/inventoryPageHelpers'
import TrackingAllocationReadonlyTable from '../inventory/tracking/TrackingAllocationReadonlyTable.vue'
import { inferTrackingMethodFromAllocations } from '../inventory/tracking/trackingPayloadHelpers'
import QualityStatusTag from './QualityStatusTag.vue'
import QualityInspectionProcessDrawer from './QualityInspectionProcessDrawer.vue'
import { qualityInspectionSourceTypeLabel, qualityInspectionStatusLabel } from './qualityPageHelpers'

const authStore = useAuthStore()
const filters = reactive<{
  keyword: string
  sourceType?: string
  status?: QualityInspectionStatus
  businessDateFrom: string
  businessDateTo: string
}>({
  keyword: '',
  sourceType: undefined,
  status: undefined,
  businessDateFrom: '',
  businessDateTo: '',
})
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
})
const records = ref<QualityInspectionRecord[]>([])
const loading = ref(false)
const error = ref('')
const processDrawerVisible = ref(false)
const processInspectionId = ref<QualityInspectionRecord['id'] | null>(null)
const canProcess = computed(() => authStore.hasPermission('quality:inspection:process'))

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await qualityInventoryStatusApi.inspections.list({
      keyword: filters.keyword,
      sourceType: filters.sourceType,
      status: filters.status,
      qualityStatus: 'PENDING_INSPECTION',
      businessDateFrom: filters.businessDateFrom,
      businessDateTo: filters.businessDateTo,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = pageItems(page)
    pagination.total = Number(page.total)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = errorMessage(caught)
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
  filters.sourceType = undefined
  filters.status = undefined
  filters.businessDateFrom = ''
  filters.businessDateTo = ''
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

function openProcess(record: QualityInspectionRecord) {
  processInspectionId.value = record.id
  processDrawerVisible.value = true
}

async function handleProcessed() {
  await loadRecords()
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 16)
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView
    title="质量确认"
    description="待检库存经质量确认转为合格、不合格或冻结，合格库存才参与出库和领料。"
  >
    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            name="quality-inspection-keyword"
            clearable
            placeholder="确认单号、来源单号或物料"
          />
        </el-form-item>
        <el-form-item label="来源类型">
          <el-select
            v-model="filters.sourceType"
            data-test="quality-inspection-source-type"
            clearable
            placeholder="全部来源"
          >
            <el-option label="采购入库" value="PURCHASE_RECEIPT" />
            <el-option label="完工入库" value="PRODUCTION_COMPLETION" />
            <el-option label="销售退货" value="SALES_RETURN" />
            <el-option label="生产退料" value="PRODUCTION_RETURN" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理状态">
          <el-select
            v-model="filters.status"
            data-test="quality-inspection-status"
            clearable
            placeholder="全部状态"
          >
            <el-option label="待处理" value="PENDING" />
            <el-option label="已处理" value="COMPLETED" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker
            v-model="filters.businessDateFrom"
            name="quality-inspection-date-from"
            type="date"
            format="YYYY-MM-DD"
            value-on-clear=""
            value-format="YYYY-MM-DD"
            placeholder="起始日期"
          />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker
            v-model="filters.businessDateTo"
            name="quality-inspection-date-to"
            type="date"
            format="YYYY-MM-DD"
            value-on-clear=""
            value-format="YYYY-MM-DD"
            placeholder="截止日期"
          />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-quality-inspections" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-quality-inspections" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="质量确认列表加载中" :closable="false" />
    </template>

    <el-empty v-if="!loading && records.length === 0" description="暂无质量确认记录" />
    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无质量确认记录'" stripe>
        <el-table-column prop="inspectionNo" label="确认单号" min-width="170" show-overflow-tooltip />
        <el-table-column label="来源类型" min-width="110">
          <template #default="{ row }">
            {{ qualityInspectionSourceTypeLabel(row.sourceType, row.sourceTypeName) }}
          </template>
        </el-table-column>
        <el-table-column prop="sourceDocumentNo" label="来源单号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="warehouseName" label="仓库" min-width="130" show-overflow-tooltip />
        <el-table-column label="物料" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.materialCode }} {{ row.materialName }}
          </template>
        </el-table-column>
        <el-table-column prop="unitName" label="单位" min-width="80" />
        <el-table-column label="质量状态" min-width="100">
          <template #default>
            <QualityStatusTag quality-status="PENDING_INSPECTION" quality-status-name="待检" />
          </template>
        </el-table-column>
        <el-table-column label="待检数量" min-width="120" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatQuantity(row.remainingQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="批次/序列" min-width="240">
          <template #default="{ row }">
            <TrackingAllocationReadonlyTable
              v-if="inferTrackingMethodFromAllocations(row.trackingAllocations) !== 'NONE'"
              :tracking-method="inferTrackingMethodFromAllocations(row.trackingAllocations)"
              :allocations="row.trackingAllocations ?? []"
            />
            <span v-else class="tracking-empty-text">不追踪</span>
          </template>
        </el-table-column>
        <el-table-column label="合格/不合格/冻结" min-width="180" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">
              {{ formatQuantity(row.qualifiedQuantity) }} /
              {{ formatQuantity(row.rejectedQuantity) }} /
              {{ formatQuantity(row.frozenQuantity) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="处理状态" min-width="100">
          <template #default="{ row }">
            {{ qualityInspectionStatusLabel(row.status, row.statusName) }}
          </template>
        </el-table-column>
        <el-table-column prop="businessDate" label="业务日期" min-width="110" />
        <el-table-column label="创建时间" min-width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="180">
          <template #default="{ row }">
            <el-button
              v-if="canProcess"
              size="small"
              text
              data-test="process-quality-inspection"
              :disabled="!row.canProcess"
              @click="openProcess(row)"
            >
              处理
            </el-button>
            <span v-if="!row.canProcess && row.disabledReason" class="disabled-reason">
              {{ row.disabledReason }}
            </span>
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

    <QualityInspectionProcessDrawer
      v-model="processDrawerVisible"
      :inspection-id="processInspectionId"
      @processed="handleProcessed"
    />
  </MasterDataTableView>
</template>

<style scoped>
.numeric-cell {
  display: inline-block;
  min-width: 72px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.disabled-reason {
  color: var(--qherp-muted);
  display: inline-block;
  font-size: 12px;
  line-height: 1.4;
  max-width: 128px;
  white-space: normal;
}

.tracking-empty-text {
  color: var(--qherp-muted);
  font-size: 12px;
}
</style>
