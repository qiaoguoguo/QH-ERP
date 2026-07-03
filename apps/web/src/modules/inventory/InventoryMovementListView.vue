<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  inventoryApi,
  type InventoryDirection,
  type InventoryMovementRecord,
  type InventoryMovementType,
  type ResourceId,
} from '../../shared/api/inventoryApi'
import { masterDataApi, type MaterialRecord, type WarehouseRecord } from '../../shared/api/masterDataApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { errorMessage, pageItems } from '../system/shared/pageHelpers'
import InventoryDirectionTag from './InventoryDirectionTag.vue'
import { formatQuantity, movementTypeLabel } from './inventoryPageHelpers'

const route = useRoute()
const router = useRouter()
const filters = reactive<{
  keyword: string
  warehouseId: ResourceId | ''
  materialId: ResourceId | ''
  movementType?: InventoryMovementType
  direction?: InventoryDirection
  dateFrom: string
  dateTo: string
}>({
  keyword: '',
  warehouseId: normalizeRouteId(route.query.warehouseId),
  materialId: normalizeRouteId(route.query.materialId),
  movementType: undefined,
  direction: undefined,
  dateFrom: '',
  dateTo: '',
})
const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0,
})
const records = ref<InventoryMovementRecord[]>([])
const warehouses = ref<WarehouseRecord[]>([])
const materials = ref<MaterialRecord[]>([])
const loading = ref(true)
const referenceLoading = ref(true)
const error = ref('')
const referenceError = ref('')

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
  filters.dateFrom = ''
  filters.dateTo = ''
  pagination.page = 1
  void loadRecords()
}

function changePage(page: number) {
  pagination.page = page
  void loadRecords()
}

function viewSourceDocument(record: InventoryMovementRecord) {
  void router.push({ name: 'inventory-document-detail', params: { id: String(record.sourceId) } })
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
            style="width: 170px"
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
            style="width: 190px"
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
            style="width: 140px"
          >
            <el-option label="期初" value="OPENING" />
            <el-option label="调增" value="ADJUSTMENT_INCREASE" />
            <el-option label="调减" value="ADJUSTMENT_DECREASE" />
          </el-select>
        </el-form-item>
        <el-form-item label="方向">
          <el-select
            v-model="filters.direction"
            data-test="inventory-movement-direction"
            clearable
            placeholder="全部方向"
            style="width: 120px"
          >
            <el-option label="入库" value="IN" />
            <el-option label="出库" value="OUT" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务日期">
          <el-input
            v-model="filters.dateFrom"
            name="inventory-movement-date-from"
            placeholder="起始日期"
            style="width: 130px"
          />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="filters.dateTo"
            name="inventory-movement-date-to"
            placeholder="截止日期"
            style="width: 130px"
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

    <el-empty v-if="!loading && records.length === 0" description="暂无库存变动流水" />
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
            {{ movementTypeLabel(row.movementType) }}
          </template>
        </el-table-column>
        <el-table-column label="方向" min-width="90">
          <template #default="{ row }">
            <InventoryDirectionTag :direction="row.direction" />
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
            <el-button size="small" text data-test="view-source-document" @click="viewSourceDocument(row)">
              查看单据
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="160" show-overflow-tooltip />
        <el-table-column prop="operatorName" label="操作人" min-width="110" />
      </el-table>
    </div>
    <el-pagination
      class="table-pagination"
      layout="total, prev, pager, next"
      :total="pagination.total"
      :page-size="pagination.pageSize"
      :current-page="pagination.page"
      @current-change="changePage"
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
</style>
