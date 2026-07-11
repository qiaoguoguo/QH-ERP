<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  inventoryApi,
  type InventoryDirection,
  type InventoryMovementRecord,
  type InventoryMovementType,
  type InventoryQualityStatus,
  type InventoryTraceDetailRecord,
  type InventoryTrackingMethod,
  type ResourceId,
} from '../../shared/api/inventoryApi'
import { masterDataApi, type MaterialRecord, type WarehouseRecord } from '../../shared/api/masterDataApi'
import { currentRouteReturnTo, queryWithReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { trackingMethodLabel } from '../master/shared/masterPageHelpers'
import { errorMessage, pageItems } from '../system/shared/pageHelpers'
import InventoryDirectionTag from './InventoryDirectionTag.vue'
import { formatQuantity, movementTypeLabel } from './inventoryPageHelpers'
import InventoryTraceDrawer from './tracking/InventoryTraceDrawer.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const routeMovementTypes = new Set<string>([
  'OPENING',
  'ADJUSTMENT_INCREASE',
  'ADJUSTMENT_DECREASE',
  'PRODUCTION_ISSUE',
  'PRODUCTION_RECEIPT',
  'PURCHASE_RECEIPT',
  'SALES_SHIPMENT',
])
const routeQualityStatuses = new Set<string>(['PENDING_INSPECTION', 'QUALIFIED', 'REJECTED', 'FROZEN'])
const routeTrackingMethods = new Set<string>(['NONE', 'BATCH', 'SERIAL'])
const filters = reactive<{
  keyword: string
  warehouseId: ResourceId | ''
  materialId: ResourceId | ''
  movementType?: InventoryMovementType
  direction?: InventoryDirection
  qualityStatus?: InventoryQualityStatus
  trackingMethod?: InventoryTrackingMethod
  batchId: ResourceId | ''
  batchNo: string
  serialId: ResourceId | ''
  serialNo: string
  dateFrom: string
  dateTo: string
}>({
  keyword: '',
  warehouseId: normalizeRouteId(route.query.warehouseId),
  materialId: normalizeRouteId(route.query.materialId),
  movementType: normalizeRouteMovementType(route.query.movementType),
  direction: undefined,
  qualityStatus: normalizeRouteQualityStatus(route.query.qualityStatus),
  trackingMethod: normalizeRouteTrackingMethod(route.query.trackingMethod),
  batchId: normalizeRouteId(route.query.batchId),
  batchNo: normalizeRouteText(route.query.batchNo),
  serialId: normalizeRouteId(route.query.serialId),
  serialNo: normalizeRouteText(route.query.serialNo),
  dateFrom: '',
  dateTo: '',
})
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
})
const records = ref<InventoryMovementRecord[]>([])
const warehouses = ref<WarehouseRecord[]>([])
const materials = ref<MaterialRecord[]>([])
const loading = ref(true)
const referenceLoading = ref(true)
const error = ref('')
const referenceError = ref('')
const traceDrawerVisible = ref(false)
const traceDetail = ref<InventoryTraceDetailRecord | null>(null)
const traceLoading = ref(false)
const traceError = ref('')
const canViewTrace = computed(() => authStore.hasPermission('inventory:trace:view'))

function normalizeRouteId(value: unknown): ResourceId | '' {
  if (Array.isArray(value)) {
    return normalizeRouteId(value[0])
  }
  if (value === '' || value === null || value === undefined) {
    return ''
  }
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue : String(value)
}

function normalizeRouteMovementType(value: unknown): InventoryMovementType | undefined {
  if (Array.isArray(value)) {
    return normalizeRouteMovementType(value[0])
  }
  if (typeof value !== 'string') {
    return undefined
  }
  return routeMovementTypes.has(value) ? value as InventoryMovementType : undefined
}

function normalizeRouteQualityStatus(value: unknown): InventoryQualityStatus | undefined {
  if (Array.isArray(value)) {
    return normalizeRouteQualityStatus(value[0])
  }
  if (typeof value !== 'string') {
    return undefined
  }
  return routeQualityStatuses.has(value) ? value as InventoryQualityStatus : undefined
}

function normalizeRouteTrackingMethod(value: unknown): InventoryTrackingMethod | undefined {
  if (Array.isArray(value)) {
    return normalizeRouteTrackingMethod(value[0])
  }
  if (typeof value !== 'string') {
    return undefined
  }
  return routeTrackingMethods.has(value) ? value as InventoryTrackingMethod : undefined
}

function normalizeRouteText(value: unknown): string {
  if (Array.isArray(value)) {
    return normalizeRouteText(value[0])
  }
  return typeof value === 'string' ? value : ''
}

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
    const page = await inventoryApi.movements.list({
      keyword: filters.keyword,
      warehouseId: normalizeOptionalId(filters.warehouseId),
      materialId: normalizeOptionalId(filters.materialId),
      movementType: filters.movementType,
      direction: filters.direction,
      qualityStatus: filters.qualityStatus,
      trackingMethod: filters.trackingMethod,
      batchId: normalizeOptionalId(filters.batchId),
      batchNo: filters.batchNo.trim(),
      serialId: normalizeOptionalId(filters.serialId),
      serialNo: filters.serialNo.trim(),
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
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
  filters.movementType = undefined
  filters.direction = undefined
  filters.qualityStatus = undefined
  filters.trackingMethod = undefined
  filters.batchId = ''
  filters.batchNo = ''
  filters.serialId = ''
  filters.serialNo = ''
  filters.dateFrom = ''
  filters.dateTo = ''
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

function canViewSourceDocument(record: InventoryMovementRecord) {
  if (!record.sourceType || !record.sourceId) {
    return false
  }
  if (record.sourceType === 'PURCHASE_RECEIPT') {
    return authStore.hasPermission('procurement:receipt:view')
  }
  if (record.sourceType === 'SALES_SHIPMENT') {
    return authStore.hasPermission('sales:shipment:view')
  }
  if (record.sourceType === 'INVENTORY_DOCUMENT') {
    return authStore.hasPermission('inventory:document:view')
  }
  return false
}

function viewSourceDocument(record: InventoryMovementRecord) {
  if (record.sourceType === 'PURCHASE_RECEIPT') {
    void router.push({
      name: 'procurement-receipt-detail',
      params: { id: String(record.sourceId) },
      query: queryWithReturnTo({}, currentRouteReturnTo(route)),
    })
    return
  }
  if (record.sourceType === 'SALES_SHIPMENT') {
    void router.push({
      name: 'sales-shipment-detail',
      params: { id: String(record.sourceId) },
      query: queryWithReturnTo({}, currentRouteReturnTo(route)),
    })
    return
  }
  if (record.sourceType === 'INVENTORY_DOCUMENT') {
    void router.push({
      name: 'inventory-document-detail',
      params: { id: String(record.sourceId) },
      query: queryWithReturnTo({}, currentRouteReturnTo(route)),
    })
  }
}

function canTraceRecord(record: InventoryMovementRecord) {
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

function traceStatusText(record: InventoryMovementRecord) {
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

function sourceDocumentStatusText(record: InventoryMovementRecord) {
  if (!record.sourceType || !record.sourceId) {
    return '无来源单据'
  }
  if (!canViewSourceDocument(record)) {
    return '无来源单据权限'
  }
  return ''
}

async function loadTrace(record: InventoryMovementRecord) {
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

function viewTrace(record: InventoryMovementRecord) {
  if (!canTraceRecord(record)) {
    return
  }
  traceDetail.value = null
  traceDrawerVisible.value = true
  void loadTrace(record)
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
  <MasterDataTableView title="库存变动流水" description="追溯期初和库存调整产生的每一笔库存变化。">
    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            name="inventory-movement-keyword"
            clearable
            placeholder="物料或单据"
          />
        </el-form-item>
        <el-form-item label="仓库">
          <el-select
            v-model="filters.warehouseId"
            data-test="inventory-movement-warehouse-id"
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
            data-test="inventory-movement-material-id"
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
        <el-form-item label="变动类型">
          <el-select
            v-model="filters.movementType"
            data-test="inventory-movement-type"
            clearable
            placeholder="全部类型"
          >
            <el-option label="期初" value="OPENING" />
            <el-option label="调增" value="ADJUSTMENT_INCREASE" />
            <el-option label="调减" value="ADJUSTMENT_DECREASE" />
            <el-option label="生产领料" value="PRODUCTION_ISSUE" />
            <el-option label="完工入库" value="PRODUCTION_RECEIPT" />
            <el-option label="采购入库" value="PURCHASE_RECEIPT" />
            <el-option label="销售出库" value="SALES_SHIPMENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="方向">
          <el-select
            v-model="filters.direction"
            data-test="inventory-movement-direction"
            clearable
            placeholder="全部方向"
          >
            <el-option label="入库" value="IN" />
            <el-option label="出库" value="OUT" />
          </el-select>
        </el-form-item>
        <el-form-item label="质量状态">
          <el-select
            v-model="filters.qualityStatus"
            data-test="inventory-movement-quality-status"
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
            data-test="inventory-movement-tracking-method"
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
            name="inventory-movement-batch-no"
            clearable
            placeholder="批次号"
          />
        </el-form-item>
        <el-form-item label="序列号">
          <el-input
            v-model="filters.serialNo"
            name="inventory-movement-serial-no"
            clearable
            placeholder="序列号"
          />
        </el-form-item>
        <el-form-item label="业务日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD"
            v-model="filters.dateFrom"
            name="inventory-movement-date-from"
            placeholder="起始日期"
          />
        </el-form-item>
        <el-form-item>
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD"
            v-model="filters.dateTo"
            name="inventory-movement-date-to"
            placeholder="截止日期"
          />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-inventory-movements" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-inventory-movements" @click="resetSearch">重置</el-button>
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
        title="库存变动流水加载中"
        :closable="false"
      />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无库存变动流水'" stripe>
        <el-table-column label="发生时间" min-width="150">
          <template #default="{ row }">
            {{ formatDateTime(row.occurredAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="businessDate" label="业务日期" min-width="110" />
        <el-table-column prop="warehouseName" label="仓库" min-width="140" show-overflow-tooltip />
        <el-table-column label="物料" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.materialCode }} {{ row.materialName }}
          </template>
        </el-table-column>
        <el-table-column label="变动类型" min-width="100">
          <template #default="{ row }">
            <span data-test="movement-type-cell">{{ movementTypeLabel(row.movementType) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="方向" min-width="90">
          <template #default="{ row }">
            <InventoryDirectionTag :direction="row.direction" />
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
        <el-table-column label="变动前" min-width="110" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatQuantity(row.beforeQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="变动数量" min-width="110" align="right">
          <template #default="{ row }">
            <span data-test="movement-quantity-cell" class="numeric-cell">{{ formatQuantity(row.quantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="变动后" min-width="110" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatQuantity(row.afterQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来源单据" min-width="140">
          <template #default="{ row }">
            <el-button
              v-if="canViewSourceDocument(row)"
              size="small"
              text
              data-test="view-source-document"
              @click="viewSourceDocument(row)"
            >
              查看单据
            </el-button>
            <span v-else class="operation-status">{{ sourceDocumentStatusText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="目标单号" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.targetDocumentNo || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="追溯" min-width="140">
          <template #default="{ row }">
            <el-button
              v-if="canTraceRecord(row)"
              size="small"
              text
              data-test="view-movement-trace"
              @click="viewTrace(row)"
            >
              追溯
            </el-button>
            <span v-else class="operation-status">{{ traceStatusText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="160" show-overflow-tooltip />
        <el-table-column prop="operatorName" label="操作人" min-width="110" />
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
    <InventoryTraceDrawer
      v-model="traceDrawerVisible"
      :detail="traceDetail"
      :loading="traceLoading"
      :error="traceError"
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

.operation-status {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  color: var(--qherp-muted);
  font-size: 13px;
}
</style>
