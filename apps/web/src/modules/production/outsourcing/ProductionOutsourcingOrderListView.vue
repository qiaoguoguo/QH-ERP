<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  productionOutsourcingApi,
  type OutsourcingOrderStatus,
  type OutsourcingOrderSummaryRecord,
} from '../../../shared/api/productionOutsourcingApi'
import { useAuthStore } from '../../../stores/authStore'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { pageItems } from '../../system/shared/pageHelpers'
import { productionErrorMessage, formatProductionQuantity, outsourcingOrderStatusLabel } from '../productionPageHelpers'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const filters = reactive<{
  keyword: string
  status?: OutsourcingOrderStatus
  projectId: string
  supplierId: string
  productMaterialId: string
  plannedDateFrom: string
  plannedDateTo: string
}>({
  keyword: '',
  status: undefined,
  projectId: '',
  supplierId: '',
  productMaterialId: '',
  plannedDateFrom: '',
  plannedDateTo: '',
})
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
})
const records = ref<OutsourcingOrderSummaryRecord[]>([])
const loading = ref(false)
const error = ref('')
const canCreate = computed(() => authStore.hasPermission('production:outsourcing:create'))

function optionalId(value: string) {
  const trimmed = value.trim()
  if (!trimmed) {
    return undefined
  }
  return /^\d+$/.test(trimmed) ? Number(trimmed) : trimmed
}

function routeProjectId() {
  const value = route.query.projectId
  return Array.isArray(value) ? String(value[0] ?? '') : String(value ?? '')
}

function applyProjectContextFromRoute() {
  filters.projectId = routeProjectId()
}

function ownershipText(row: OutsourcingOrderSummaryRecord) {
  if (row.ownershipType === 'PROJECT') {
    return [row.projectNo, row.projectName].filter(Boolean).join(' ') || '项目未返回'
  }
  return '公共/未绑定'
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await productionOutsourcingApi.orders.list({
      keyword: filters.keyword,
      status: filters.status,
      projectId: optionalId(filters.projectId),
      supplierId: optionalId(filters.supplierId),
      productMaterialId: optionalId(filters.productMaterialId),
      plannedDateFrom: filters.plannedDateFrom,
      plannedDateTo: filters.plannedDateTo,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = pageItems(page)
    pagination.total = Number(page.total)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = productionErrorMessage(caught)
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
  filters.status = undefined
  filters.projectId = routeProjectId()
  filters.supplierId = ''
  filters.productMaterialId = ''
  filters.plannedDateFrom = ''
  filters.plannedDateTo = ''
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

function createOrder() {
  void router.push({ name: 'production-outsourcing-order-create' })
}

function viewOrder(row: OutsourcingOrderSummaryRecord) {
  void router.push({ name: 'production-outsourcing-order-detail', params: { id: String(row.id) } })
}

watch(
  () => route.query.projectId,
  () => {
    const nextProjectId = routeProjectId()
    if (filters.projectId === nextProjectId) {
      return
    }
    filters.projectId = nextProjectId
    pagination.page = 1
    void loadRecords()
  },
)

onMounted(() => {
  applyProjectContextFromRoute()
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="外协执行" description="管理项目或公共外协订单、发料、收货、供应商和来源追溯。">
    <template #actions>
      <el-button v-if="canCreate" data-test="create-outsourcing-order" type="primary" @click="createOrder">
        新建外协单
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="outsourcing-keyword" clearable placeholder="单号、物料、项目" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" data-test="outsourcing-status-filter" clearable placeholder="全部状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已下达" value="RELEASED" />
            <el-option label="加工中" value="IN_PROGRESS" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已关闭" value="CLOSED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目">
          <el-input v-model="filters.projectId" name="outsourcing-project" clearable placeholder="项目编号/ID" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="filters.supplierId" name="outsourcing-supplier" clearable placeholder="供应商编号/ID" />
        </el-form-item>
        <el-form-item label="物料">
          <el-input v-model="filters.productMaterialId" name="outsourcing-product-material" clearable placeholder="物料编号/ID" />
        </el-form-item>
        <el-form-item label="计划日期">
          <el-date-picker
            v-model="filters.plannedDateFrom"
            value-on-clear=""
            type="date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            placeholder="起始日期"
          />
        </el-form-item>
        <el-form-item>
          <el-date-picker
            v-model="filters.plannedDateTo"
            value-on-clear=""
            type="date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            placeholder="截止日期"
          />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-outsourcing-orders" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-outsourcing-orders" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="外协订单加载中" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无外协订单'" stripe>
        <el-table-column prop="orderNo" label="外协单号" min-width="160" fixed show-overflow-tooltip />
        <el-table-column label="项目/归属" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ ownershipText(row) }}</template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商" min-width="150" show-overflow-tooltip />
        <el-table-column label="物料" min-width="210" show-overflow-tooltip>
          <template #default="{ row }">{{ row.productMaterialCode }} {{ row.productMaterialName }}</template>
        </el-table-column>
        <el-table-column label="计划/已发/已收" min-width="170" align="right">
          <template #default="{ row }">
            {{ formatProductionQuantity(row.plannedQuantity) }} /
            {{ formatProductionQuantity(row.issuedQuantity) }} /
            {{ formatProductionQuantity(row.receivedQuantity) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="110">
          <template #default="{ row }">{{ outsourcingOrderStatusLabel(row.status, row.statusName) }}</template>
        </el-table-column>
        <el-table-column label="来源建议" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.sourceSuggestionNo || row.sourceMrpSuggestionId || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button data-test="view-outsourcing-order" text type="primary" @click="viewOrder(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination
      class="table-pagination"
      background
      layout="total, sizes, prev, pager, next"
      :current-page="pagination.page"
      :page-size="pagination.pageSize"
      :page-sizes="[10, 20, 50, 100]"
      :total="pagination.total"
      @current-change="changePage"
      @size-change="changePageSize"
    />
  </MasterDataTableView>
</template>
