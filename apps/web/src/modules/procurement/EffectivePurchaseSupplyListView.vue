<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type DocumentTaskRecord,
} from '../../shared/api/documentPlatformApi'
import { masterDataApi, type MaterialRecord, type PartnerRecord } from '../../shared/api/masterDataApi'
import { procurementApi, type EffectivePurchaseSupplyRecord } from '../../shared/api/procurementApi'
import { salesProjectApi, type SalesProjectSummary } from '../../shared/api/salesProjectApi'
import { pageItems } from '../system/shared/pageHelpers'
import {
  formatProcurementAmount,
  formatProcurementQuantity,
  normalizeOptionalId,
  procurementErrorMessage,
  procurementOwnershipDisplay,
  procurementPriceSourceDisplay,
} from './procurementPageHelpers'
import ProcurementDocumentTaskPanel from './ProcurementDocumentTaskPanel.vue'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import BusinessReferenceSelect from '../system/shared/BusinessReferenceSelect.vue'
import type { BusinessReferenceOption } from '../system/shared/businessReferenceSelectTypes'

const authStore = useAuthStore()
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const records = ref<EffectivePurchaseSupplyRecord[]>([])
const latestDocumentTask = ref<DocumentTaskRecord | null>(null)
const total = ref(0)
const filters = reactive({
  projectId: '' as string | number | '',
  materialId: '' as string | number | '',
  supplierId: '' as string | number | '',
  procurementMode: undefined as 'PUBLIC' | 'PROJECT' | undefined,
  status: undefined as string | undefined,
  expectedDateFrom: '',
  expectedDateTo: '',
  countedOnly: true,
  page: 1,
  pageSize: 10,
})

const canExport = computed(() => (
  authStore.hasPermission('procurement:supply:view')
  && authStore.hasPermission('platform:document-task:create')
  && authStore.hasPermission('procurement:document:export')
))

function costText(record: EffectivePurchaseSupplyRecord): string {
  if (record.costVisible === false) {
    return '成本无权限'
  }
  return record.taxExcludedAmount ? `未税金额 ${formatProcurementAmount(record.taxExcludedAmount)}` : '未税金额未返回'
}

function projectOption(project: SalesProjectSummary): BusinessReferenceOption {
  return { id: project.id, label: `${project.projectNo} ${project.name}` }
}

function materialOption(material: MaterialRecord): BusinessReferenceOption {
  return { id: material.id, label: `${material.code} ${material.name}` }
}

function supplierOption(supplier: PartnerRecord): BusinessReferenceOption {
  return { id: supplier.id, label: `${supplier.code} ${supplier.name}` }
}

async function loadProjectOptions(keyword: string) {
  const page = await salesProjectApi.projects.list({
    keyword,
    status: 'ACTIVE',
    page: 1,
    pageSize: 50,
  })
  return pageItems(page).map(projectOption)
}

async function loadMaterialOptions(keyword: string) {
  const page = await masterDataApi.materials.list({
    keyword,
    status: 'ENABLED',
    sourceType: 'PURCHASED',
    page: 1,
    pageSize: 50,
  })
  return pageItems(page).map(materialOption)
}

async function loadSupplierOptions(keyword: string) {
  const page = await masterDataApi.suppliers.list({
    keyword,
    status: 'ENABLED',
    page: 1,
    pageSize: 50,
  })
  return pageItems(page).map(supplierOption)
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await procurementApi.effectiveSupplies.list({
      projectId: normalizeOptionalId(filters.projectId),
      materialId: normalizeOptionalId(filters.materialId),
      supplierId: normalizeOptionalId(filters.supplierId),
      procurementMode: filters.procurementMode,
      status: filters.status,
      expectedDateFrom: filters.expectedDateFrom,
      expectedDateTo: filters.expectedDateTo,
      countedOnly: filters.countedOnly,
      page: filters.page,
      pageSize: filters.pageSize,
    })
    records.value = pageItems(page)
    total.value = page.total
  } catch (caught) {
    records.value = []
    error.value = procurementErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function exportEffectiveSupplies() {
  if (actionLoading.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    latestDocumentTask.value = await documentPlatformApi.exports.createProcurementEffectiveSupplies({
      projectId: normalizeOptionalId(filters.projectId),
      materialId: normalizeOptionalId(filters.materialId),
      supplierId: normalizeOptionalId(filters.supplierId),
      procurementMode: filters.procurementMode,
      status: filters.status,
      expectedDateFrom: filters.expectedDateFrom,
      expectedDateTo: filters.expectedDateTo,
      countedOnly: filters.countedOnly,
      idempotencyKey: createIdempotencyKey('procurement-effective-supply-export'),
    })
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function searchRecords() {
  filters.page = 1
  void loadRecords()
}

function resetFilters() {
  filters.projectId = ''
  filters.materialId = ''
  filters.supplierId = ''
  filters.procurementMode = undefined
  filters.status = undefined
  filters.expectedDateFrom = ''
  filters.expectedDateTo = ''
  filters.countedOnly = true
  filters.page = 1
  filters.pageSize = 10
  void loadRecords()
}

function changePage(page: number) {
  filters.page = page
  void loadRecords()
}

function changePageSize(pageSize: number) {
  filters.pageSize = pageSize
  filters.page = 1
  void loadRecords()
}

onMounted(() => {
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="有效采购供给" description="只读供给视图，供后续阶段消费；本页不做计算和推荐。">
    <template #actions>
      <el-button v-if="canExport" data-test="export-effective-supplies" :loading="actionLoading" @click="exportEffectiveSupplies">
        当前筛选导出
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="项目">
          <BusinessReferenceSelect
            v-model="filters.projectId"
            data-test="effective-supply-project-filter"
            placeholder="项目编号或名称"
            :load-options="loadProjectOptions"
          />
        </el-form-item>
        <el-form-item label="物料">
          <BusinessReferenceSelect
            v-model="filters.materialId"
            data-test="effective-supply-material-filter"
            placeholder="物料编码或名称"
            :load-options="loadMaterialOptions"
          />
        </el-form-item>
        <el-form-item label="供应商">
          <BusinessReferenceSelect
            v-model="filters.supplierId"
            data-test="effective-supply-supplier-filter"
            placeholder="供应商编码或名称"
            :load-options="loadSupplierOptions"
          />
        </el-form-item>
        <el-form-item label="采购模式">
          <el-select v-model="filters.procurementMode" clearable placeholder="全部模式">
            <el-option label="公共采购" value="PUBLIC" />
            <el-option label="项目专采" value="PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务状态">
          <el-input v-model="filters.status" clearable placeholder="状态代码" />
        </el-form-item>
        <el-form-item label="预计起始">
          <el-date-picker v-model="filters.expectedDateFrom" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="起始日期" />
        </el-form-item>
        <el-form-item label="预计截止">
          <el-date-picker v-model="filters.expectedDateTo" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="截止日期" />
        </el-form-item>
        <el-form-item label="计入有效供给">
          <el-select v-model="filters.countedOnly" placeholder="计入口径">
            <el-option label="仅计入" :value="true" />
            <el-option label="含排除项" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作">
          <el-button data-test="search-effective-supplies" type="primary" @click="searchRecords">查询</el-button>
          <el-button data-test="reset-effective-supplies" @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
      <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
      <ProcurementDocumentTaskPanel :task="latestDocumentTask" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无有效采购供给'" stripe v-loading="loading">
        <el-table-column prop="orderNo" label="订单号" min-width="160" show-overflow-tooltip />
        <el-table-column label="采购模式/项目" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ procurementOwnershipDisplay(row) }}</template>
        </el-table-column>
        <el-table-column label="供应商" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.supplierName }}</template>
        </el-table-column>
        <el-table-column label="物料" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template>
        </el-table-column>
        <el-table-column label="价格来源" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ procurementPriceSourceDisplay(row) }}</template>
        </el-table-column>
        <el-table-column label="预计到货" min-width="120">
          <template #default="{ row }">预计到货 {{ row.expectedArrivalDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="剩余数量" min-width="120" align="right">
          <template #default="{ row }">剩余 {{ formatProcurementQuantity(row.remainingQuantity) }}</template>
        </el-table-column>
        <el-table-column label="有效供给" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ row.countedAsEffectiveSupply ? '计入有效供给' : (row.notCountedReason || '不计入有效供给') }}</template>
        </el-table-column>
        <el-table-column label="成本" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ costText(row) }}</template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination
      class="table-pagination"
      background
      layout="total, sizes, prev, pager, next"
      :current-page="filters.page"
      :page-size="filters.pageSize"
      :page-sizes="[10, 20, 50, 100]"
      :total="total"
      @current-change="changePage"
      @size-change="changePageSize"
    />
  </MasterDataTableView>
</template>

<style scoped>
</style>
