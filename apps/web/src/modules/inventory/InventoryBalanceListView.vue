<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  inventoryApi,
  type InventoryBalanceRecord,
  type InventoryReservationSummaryRecord,
  type InventoryQualityStatus,
  type InventoryTraceDetailRecord,
  type InventoryTrackingMethod,
  type ResourceId,
} from '../../shared/api/inventoryApi'
import { masterDataApi, type MaterialRecord, type MaterialType, type WarehouseRecord } from '../../shared/api/masterDataApi'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { materialTypeLabel, trackingMethodLabel } from '../master/shared/masterPageHelpers'
import { errorMessage, pageItems } from '../system/shared/pageHelpers'
import QualityStatusTag from '../quality/QualityStatusTag.vue'
import { formatQuantity } from './inventoryPageHelpers'
import InventoryTraceDrawer from './tracking/InventoryTraceDrawer.vue'

const router = useRouter()
const authStore = useAuthStore()
const filters = reactive<{
  keyword: string
  warehouseId: ResourceId | ''
  materialId: ResourceId | ''
  materialType?: MaterialType
  qualityStatus?: InventoryQualityStatus
  trackingMethod?: InventoryTrackingMethod
  batchNo: string
  serialNo: string
  onlyPositive: boolean
  includeZeroQualityStatuses: boolean
}>({
  keyword: '',
  warehouseId: '',
  materialId: '',
  materialType: undefined,
  qualityStatus: undefined,
  trackingMethod: undefined,
  batchNo: '',
  serialNo: '',
  onlyPositive: false,
  includeZeroQualityStatuses: false,
})
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
})
const records = ref<InventoryBalanceRecord[]>([])
const warehouses = ref<WarehouseRecord[]>([])
const materials = ref<MaterialRecord[]>([])
const loading = ref(true)
const referenceLoading = ref(true)
const error = ref('')
const referenceError = ref('')
const reservationDrawerVisible = ref(false)
const inTransitDrawerVisible = ref(false)
const selectedBalance = ref<InventoryBalanceRecord | null>(null)
const reservationRecords = ref<InventoryReservationSummaryRecord[]>([])
const reservationLoading = ref(false)
const reservationError = ref('')
const traceDrawerVisible = ref(false)
const traceDetail = ref<InventoryTraceDetailRecord | null>(null)
const traceLoading = ref(false)
const traceError = ref('')
const availabilityDrawerSize = 'min(520px, 92vw)'
const canViewReservations = computed(() => authStore.hasPermission('inventory:reservation:view'))
const canViewTrace = computed(() => authStore.hasPermission('inventory:trace:view'))

function normalizeOptionalId(value: ResourceId | ''): ResourceId | undefined {
  if (value === '' || value === null || value === undefined) {
    return undefined
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : undefined
  }
  const trimmedValue = String(value).trim()
  if (!trimmedValue) {
    return undefined
  }
  const numericValue = Number(trimmedValue)
  return Number.isFinite(numericValue) ? numericValue : trimmedValue
}

async function loadReferences() {
  referenceLoading.value = true
  referenceError.value = ''
  try {
    const [warehousePage, materialPage] = await Promise.all([
      masterDataApi.warehouses.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 100 }),
      masterDataApi.materials.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 100 }),
    ])
    warehouses.value = pageItems(warehousePage)
    materials.value = pageItems(materialPage)
  } catch (caught) {
    warehouses.value = []
    materials.value = []
    referenceError.value = errorMessage(caught)
  } finally {
    referenceLoading.value = false
  }
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await inventoryApi.balances.list({
      keyword: filters.keyword,
      warehouseId: normalizeOptionalId(filters.warehouseId),
      materialId: normalizeOptionalId(filters.materialId),
      materialType: filters.materialType,
      qualityStatus: filters.qualityStatus,
      trackingMethod: filters.trackingMethod,
      batchNo: filters.batchNo.trim(),
      serialNo: filters.serialNo.trim(),
      onlyPositive: filters.onlyPositive,
      includeZeroQualityStatuses: filters.includeZeroQualityStatuses,
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
  filters.warehouseId = ''
  filters.materialId = ''
  filters.materialType = undefined
  filters.qualityStatus = undefined
  filters.trackingMethod = undefined
  filters.batchNo = ''
  filters.serialNo = ''
  filters.onlyPositive = false
  filters.includeZeroQualityStatuses = false
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

function viewMovements(record: InventoryBalanceRecord) {
  void router.push({
    path: '/inventory/movements',
    query: {
      warehouseId: String(record.warehouseId),
      materialId: String(record.materialId),
      ...(record.qualityStatus ? { qualityStatus: String(record.qualityStatus) } : {}),
      ...(record.trackingMethod ? { trackingMethod: String(record.trackingMethod) } : {}),
      ...(record.batchId ? { batchId: String(record.batchId) } : {}),
      ...(record.batchNo ? { batchNo: String(record.batchNo) } : {}),
      ...(record.serialId ? { serialId: String(record.serialId) } : {}),
      ...(record.serialNo ? { serialNo: String(record.serialNo) } : {}),
    },
  })
}

function canTraceRecord(record: InventoryBalanceRecord) {
  if (!canViewTrace.value) {
    return false
  }
  if (record.trackingMethod === 'BATCH') {
    return Boolean(record.batchId)
  }
  if (record.trackingMethod === 'SERIAL') {
    return Boolean(record.serialId)
  }
  return false
}

function traceStatusText(record: InventoryBalanceRecord) {
  if (!canViewTrace.value) {
    return '无追溯权限'
  }
  if (record.trackingMethod === 'NONE') {
    return '不追踪物料无追溯'
  }
  if (record.trackingMethod === 'BATCH' && !record.batchId) {
    return '缺少批次或序列身份'
  }
  if (record.trackingMethod === 'SERIAL' && !record.serialId) {
    return '缺少批次或序列身份'
  }
  return '暂无追溯入口'
}

async function loadTrace(record: InventoryBalanceRecord) {
  traceLoading.value = true
  traceError.value = ''
  try {
    if (record.trackingMethod === 'BATCH' && record.batchId) {
      traceDetail.value = await inventoryApi.traces.getBatchTrace(record.batchId)
      return
    }
    if (record.trackingMethod === 'SERIAL' && record.serialId) {
      traceDetail.value = await inventoryApi.traces.getSerialTrace(record.serialId)
      return
    }
    traceDetail.value = null
  } catch (caught) {
    traceDetail.value = null
    traceError.value = errorMessage(caught)
  } finally {
    traceLoading.value = false
  }
}

function viewTrace(record: InventoryBalanceRecord) {
  if (!canTraceRecord(record)) {
    return
  }
  traceDetail.value = null
  traceDrawerVisible.value = true
  void loadTrace(record)
}

async function loadReservations(record: InventoryBalanceRecord) {
  reservationLoading.value = true
  reservationError.value = ''
  try {
    const page = await inventoryApi.reservations.list({
      warehouseId: record.warehouseId,
      materialId: record.materialId,
      status: 'ACTIVE',
      page: 1,
      pageSize: 20,
    })
    reservationRecords.value = pageItems(page)
  } catch (caught) {
    reservationRecords.value = []
    reservationError.value = errorMessage(caught)
  } finally {
    reservationLoading.value = false
  }
}

function viewReservations(record: InventoryBalanceRecord) {
  if (!canViewReservations.value) {
    return
  }
  selectedBalance.value = record
  reservationRecords.value = []
  reservationDrawerVisible.value = true
  void loadReservations(record)
}

function viewInTransit(record: InventoryBalanceRecord) {
  selectedBalance.value = record
  inTransitDrawerVisible.value = true
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 16)
}

onMounted(() => {
  void loadReferences()
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="库存余额" description="展示账面库存、合格现存、占用预留、现货净可用、物料级采购在途参考、可承诺量和净需求缺口。">
    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            name="inventory-balance-keyword"
            clearable
            placeholder="物料编码或名称"
          />
        </el-form-item>
        <el-form-item label="仓库">
          <el-select
            v-model="filters.warehouseId"
            data-test="inventory-balance-warehouse-id"
            filterable
            clearable
            placeholder="全部仓库"
          >
            <el-option
              v-for="warehouse in warehouses"
              :key="warehouse.id"
              :label="`${warehouse.code} ${warehouse.name}`"
              :value="warehouse.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="物料">
          <el-select
            v-model="filters.materialId"
            data-test="inventory-balance-material-id"
            filterable
            clearable
            placeholder="全部物料"
          >
            <el-option
              v-for="material in materials"
              :key="material.id"
              :label="`${material.code} ${material.name}`"
              :value="material.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="物料类型">
          <el-select
            v-model="filters.materialType"
            data-test="inventory-balance-material-type"
            clearable
            placeholder="全部类型"
          >
            <el-option label="原材料" value="RAW_MATERIAL" />
            <el-option label="半成品" value="SEMI_FINISHED" />
            <el-option label="成品" value="FINISHED_GOOD" />
            <el-option label="辅料" value="AUXILIARY" />
          </el-select>
        </el-form-item>
        <el-form-item label="质量状态">
          <el-select
            v-model="filters.qualityStatus"
            data-test="inventory-balance-quality-status"
            clearable
            placeholder="全部状态"
          >
            <el-option label="待检" value="PENDING_INSPECTION" />
            <el-option label="合格" value="QUALIFIED" />
            <el-option label="不合格" value="REJECTED" />
            <el-option label="冻结" value="FROZEN" />
          </el-select>
        </el-form-item>
        <el-form-item label="追踪方式">
          <el-select
            v-model="filters.trackingMethod"
            data-test="inventory-balance-tracking-method"
            clearable
            placeholder="全部方式"
          >
            <el-option label="不追踪" value="NONE" />
            <el-option label="批次管理" value="BATCH" />
            <el-option label="序列号管理" value="SERIAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="批次号">
          <el-input
            v-model="filters.batchNo"
            name="inventory-balance-batch-no"
            clearable
            placeholder="批次号"
          />
        </el-form-item>
        <el-form-item label="序列号">
          <el-input
            v-model="filters.serialNo"
            name="inventory-balance-serial-no"
            clearable
            placeholder="序列号"
          />
        </el-form-item>
        <el-form-item>
          <label class="checkbox-filter">
            <input v-model="filters.onlyPositive" name="inventory-balance-only-positive" type="checkbox" />
            只看有库存
          </label>
        </el-form-item>
        <el-form-item>
          <label class="checkbox-filter">
            <input
              v-model="filters.includeZeroQualityStatuses"
              name="inventory-balance-include-zero-quality-statuses"
              type="checkbox"
            />
            包含零数量质量状态
          </label>
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-inventory-balances" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-inventory-balances" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="referenceError" class="state-alert" type="error" :title="referenceError" :closable="false" />
      <el-alert
        v-if="loading || referenceLoading"
        class="state-alert"
        type="info"
        title="库存余额加载中"
        :closable="false"
      />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无库存余额'" stripe>
        <el-table-column prop="warehouseName" label="仓库" min-width="150" show-overflow-tooltip />
        <el-table-column prop="materialCode" label="物料编码" min-width="140" show-overflow-tooltip />
        <el-table-column prop="materialName" label="物料名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="materialSpec" label="规格" min-width="130" show-overflow-tooltip />
        <el-table-column label="物料类型" min-width="110">
          <template #default="{ row }">
            {{ materialTypeLabel(row.materialType) }}
          </template>
        </el-table-column>
        <el-table-column prop="unitName" label="单位" min-width="90" />
        <el-table-column label="质量状态" min-width="100">
          <template #default="{ row }">
            <QualityStatusTag :quality-status="row.qualityStatus" :quality-status-name="row.qualityStatusName" />
          </template>
        </el-table-column>
        <el-table-column label="追踪方式" min-width="120">
          <template #default="{ row }">
            {{ row.trackingMethodName || trackingMethodLabel(row.trackingMethod) }}
          </template>
        </el-table-column>
        <el-table-column label="批次/序列" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.batchNo || row.serialNo || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="追踪数量" min-width="120" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatQuantity(row.traceableQuantity ?? row.quantityOnHand) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="账面库存" min-width="120" align="right">
          <template #default="{ row }">
            <span data-test="book-quantity-cell" class="numeric-cell">
              {{ formatQuantity(row.bookQuantity ?? row.totalQuantityOnHand ?? row.quantityOnHand) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="合格现存" min-width="120" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatQuantity(row.qualifiedQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="占用库存" min-width="120" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatQuantity(row.occupiedQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="预留库存" min-width="120" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatQuantity(row.reservedQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="冻结库存" min-width="120" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatQuantity(row.frozenQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="现货净可用" min-width="130" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatQuantity(row.availableQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="采购在途参考" min-width="130" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatQuantity(row.inTransitQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="可承诺量" min-width="120" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatQuantity(row.availableToPromiseQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="净需求缺口" min-width="130" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatQuantity(row.netRequirementShortageQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="待检" min-width="110" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatQuantity(row.pendingInspectionQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="不合格" min-width="110" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatQuantity(row.rejectedQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="unavailableReason" label="不可用说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="更新时间" min-width="150">
          <template #default="{ row }">
            {{ formatDateTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="260">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-inventory-movements" @click="viewMovements(row)">
              查看流水
            </el-button>
            <el-button
              v-if="canTraceRecord(row)"
              size="small"
              text
              data-test="view-inventory-trace"
              @click="viewTrace(row)"
            >
              追溯
            </el-button>
            <span v-else class="operation-status">{{ traceStatusText(row) }}</span>
            <el-button v-if="canViewReservations" size="small" text data-test="view-inventory-reservations" @click="viewReservations(row)">
              占用预留
            </el-button>
            <el-button size="small" text data-test="view-inventory-in-transit" @click="viewInTransit(row)">
              在途参考
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination
      class="table-pagination"
      layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]"
      :total="pagination.total"
      :page-size="pagination.pageSize"
      :current-page="pagination.page"
      @current-change="changePage" @size-change="changePageSize"
    />

    <el-drawer v-model="reservationDrawerVisible" title="占用预留追溯" :size="availabilityDrawerSize">
      <dl v-if="selectedBalance" class="availability-detail-list">
        <dt>仓库</dt>
        <dd>{{ selectedBalance.warehouseName }}</dd>
        <dt>物料</dt>
        <dd>{{ selectedBalance.materialCode }} {{ selectedBalance.materialName }}</dd>
        <dt>占用库存</dt>
        <dd>{{ formatQuantity(selectedBalance.occupiedQuantity) }}</dd>
        <dt>预留库存</dt>
        <dd>{{ formatQuantity(selectedBalance.reservedQuantity) }}</dd>
        <dt>现货净可用</dt>
        <dd>{{ formatQuantity(selectedBalance.availableQuantity) }}</dd>
      </dl>
      <el-alert v-if="reservationError" class="state-alert" type="error" :title="reservationError" :closable="false" />
      <el-table
        v-loading="reservationLoading"
        class="reservation-source-table"
        :data="reservationRecords"
        empty-text="暂无生效占用预留来源"
        stripe
      >
        <el-table-column prop="reservationNo" label="台账编号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="reservationTypeName" label="类型" min-width="100" />
        <el-table-column prop="statusName" label="状态" min-width="90" />
        <el-table-column prop="sourceTypeName" label="来源类型" min-width="110" show-overflow-tooltip />
        <el-table-column prop="sourceDocumentNo" label="来源单号" min-width="160" show-overflow-tooltip />
        <el-table-column label="剩余数量" min-width="110" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatQuantity(row.remainingQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="businessDate" label="业务日期" min-width="110" />
      </el-table>
    </el-drawer>

    <el-drawer v-model="inTransitDrawerVisible" title="采购在途参考摘要" :size="availabilityDrawerSize">
      <el-alert
        class="state-alert"
        type="info"
        title="采购在途按物料汇总展示，不代表当前仓库现货可用。"
        :closable="false"
      />
      <dl v-if="selectedBalance" class="availability-detail-list">
        <dt>库存行仓库</dt>
        <dd>{{ selectedBalance.warehouseName }}</dd>
        <dt>物料</dt>
        <dd>{{ selectedBalance.materialCode }} {{ selectedBalance.materialName }}</dd>
        <dt>采购在途参考</dt>
        <dd>{{ formatQuantity(selectedBalance.inTransitQuantity) }}</dd>
        <dt>可承诺量</dt>
        <dd>{{ formatQuantity(selectedBalance.availableToPromiseQuantity) }}</dd>
      </dl>
    </el-drawer>

    <InventoryTraceDrawer
      v-model="traceDrawerVisible"
      :detail="traceDetail"
      :loading="traceLoading"
      :error="traceError"
    />
  </MasterDataTableView>
</template>

<style scoped>
.checkbox-filter {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  color: var(--qherp-text);
}

.numeric-cell {
  display: inline-block;
  min-width: 72px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.operation-status {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  color: var(--qherp-muted);
  font-size: 13px;
}

.availability-detail-list {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: 10px 12px;
  margin: 0;
}

.availability-detail-list dt {
  color: var(--qherp-muted);
}

.availability-detail-list dd {
  margin: 0;
  min-width: 0;
  word-break: break-word;
}
</style>
