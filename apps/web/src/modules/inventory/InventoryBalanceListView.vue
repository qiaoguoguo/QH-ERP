<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { inventoryApi, type InventoryBalanceRecord, type ResourceId } from '../../shared/api/inventoryApi'
import { masterDataApi, type MaterialRecord, type MaterialType, type WarehouseRecord } from '../../shared/api/masterDataApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { materialTypeLabel } from '../master/shared/masterPageHelpers'
import { errorMessage, pageItems } from '../system/shared/pageHelpers'
import { formatQuantity } from './inventoryPageHelpers'

const router = useRouter()
const filters = reactive<{
  keyword: string
  warehouseId: ResourceId | ''
  materialId: ResourceId | ''
  materialType?: MaterialType
  onlyPositive: boolean
}>({
  keyword: '',
  warehouseId: '',
  materialId: '',
  materialType: undefined,
  onlyPositive: false,
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
      onlyPositive: filters.onlyPositive,
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
  filters.onlyPositive = false
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
    },
  })
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
  <MasterDataTableView title="库存余额" description="按仓库和物料查询当前现存、锁定和可用库存。">
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
        <el-form-item>
          <label class="checkbox-filter">
            <input v-model="filters.onlyPositive" name="inventory-balance-only-positive" type="checkbox" />
            只看有库存
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

    <el-empty v-if="!loading && records.length === 0" description="暂无库存余额" />
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
        <el-table-column label="现存数量" min-width="120" align="right">
          <template #default="{ row }">
            <span data-test="quantity-on-hand-cell" class="numeric-cell">{{ formatQuantity(row.quantityOnHand) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="锁定数量" min-width="120" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatQuantity(row.lockedQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="可用数量" min-width="120" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatQuantity(row.availableQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="150">
          <template #default="{ row }">
            {{ formatDateTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="110">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-inventory-movements" @click="viewMovements(row)">
              查看流水
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
</style>
