<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type DocumentTaskRecord,
} from '../../shared/api/documentPlatformApi'
import { masterDataApi, type MaterialRecord, type PartnerRecord } from '../../shared/api/masterDataApi'
import { procurementApi, type PriceAgreementSummaryRecord } from '../../shared/api/procurementApi'
import { salesProjectApi, type SalesProjectSummary } from '../../shared/api/salesProjectApi'
import { pageItems } from '../system/shared/pageHelpers'
import {
  formatProcurementAmount,
  normalizeOptionalId,
  priceAgreementStatusLabel,
  procurementApprovalStatusLabel,
  procurementErrorMessage,
  procurementOwnershipDisplay,
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
const records = ref<PriceAgreementSummaryRecord[]>([])
const latestDocumentTask = ref<DocumentTaskRecord | null>(null)
const total = ref(0)
const filters = reactive({
  keyword: '',
  supplierId: '' as string | number | '',
  materialId: '' as string | number | '',
  procurementMode: undefined as 'PUBLIC' | 'PROJECT' | undefined,
  projectId: '' as string | number | '',
  status: undefined as PriceAgreementSummaryRecord['status'] | undefined,
  page: 1,
  pageSize: 10,
})

const canExport = computed(() => (
  authStore.hasPermission('procurement:price-agreement:view')
  && authStore.hasPermission('platform:document-task:create')
  && authStore.hasPermission('procurement:document:export')
))

function allowed(record: PriceAgreementSummaryRecord, action: string): boolean {
  return (record.allowedActions ?? []).includes(action)
}

function canSubmitActivation(record: PriceAgreementSummaryRecord): boolean {
  return (allowed(record, 'SUBMIT') || allowed(record, 'SUBMIT_ACTIVATION'))
    && authStore.hasPermission('procurement:price-agreement:submit')
}

function supplierOption(supplier: PartnerRecord): BusinessReferenceOption {
  return { id: supplier.id, label: `${supplier.code} ${supplier.name}` }
}

function materialOption(material: MaterialRecord): BusinessReferenceOption {
  return { id: material.id, label: `${material.code} ${material.name}` }
}

function projectOption(project: SalesProjectSummary): BusinessReferenceOption {
  return { id: project.id, label: `${project.projectNo} ${project.name}` }
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

async function loadProjectOptions(keyword: string) {
  const page = await salesProjectApi.projects.list({
    keyword,
    status: 'ACTIVE',
    page: 1,
    pageSize: 50,
  })
  return pageItems(page).map(projectOption)
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await procurementApi.priceAgreements.list({
      keyword: filters.keyword,
      supplierId: normalizeOptionalId(filters.supplierId),
      materialId: normalizeOptionalId(filters.materialId),
      procurementMode: filters.procurementMode,
      projectId: normalizeOptionalId(filters.projectId),
      status: filters.status,
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

async function submitActivation(record: PriceAgreementSummaryRecord) {
  if (actionLoading.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await procurementApi.priceAgreements.submitActivation(record.id, {
      version: record.version,
      reason: '提交价格协议激活审批',
      idempotencyKey: createIdempotencyKey('price-agreement-submit-activation'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
    await loadRecords()
  } finally {
    actionLoading.value = false
  }
}

async function exportPriceAgreements() {
  if (actionLoading.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    latestDocumentTask.value = await documentPlatformApi.exports.createProcurementPriceAgreements({
      keyword: filters.keyword,
      supplierId: normalizeOptionalId(filters.supplierId),
      materialId: normalizeOptionalId(filters.materialId),
      procurementMode: filters.procurementMode,
      projectId: normalizeOptionalId(filters.projectId),
      status: filters.status,
      idempotencyKey: createIdempotencyKey('procurement-price-agreement-export'),
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
  filters.keyword = ''
  filters.supplierId = ''
  filters.materialId = ''
  filters.procurementMode = undefined
  filters.projectId = ''
  filters.status = undefined
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
  <MasterDataTableView title="价格协议" description="价格协议激活审批后才可作为采购订单价格来源。">
    <template #actions>
      <el-button v-if="canExport" data-test="export-price-agreements" :loading="actionLoading" @click="exportPriceAgreements">
        当前筛选导出
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" clearable placeholder="协议号、供应商、物料" />
        </el-form-item>
        <el-form-item label="供应商">
          <BusinessReferenceSelect
            v-model="filters.supplierId"
            data-test="price-agreement-supplier-filter"
            placeholder="供应商编码或名称"
            :load-options="loadSupplierOptions"
          />
        </el-form-item>
        <el-form-item label="物料">
          <BusinessReferenceSelect
            v-model="filters.materialId"
            data-test="price-agreement-material-filter"
            placeholder="物料编码或名称"
            :load-options="loadMaterialOptions"
          />
        </el-form-item>
        <el-form-item label="采购模式">
          <el-select v-model="filters.procurementMode" clearable placeholder="全部模式">
            <el-option label="公共采购" value="PUBLIC" />
            <el-option label="项目专采" value="PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目">
          <BusinessReferenceSelect
            v-model="filters.projectId"
            data-test="price-agreement-project-filter"
            placeholder="项目编号或名称"
            :load-options="loadProjectOptions"
          />
        </el-form-item>
        <el-form-item label="业务状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="生效" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作">
          <el-button data-test="search-price-agreements" type="primary" @click="searchRecords">查询</el-button>
          <el-button data-test="reset-price-agreements" @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
      <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
      <ProcurementDocumentTaskPanel :task="latestDocumentTask" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无价格协议'" stripe v-loading="loading">
        <el-table-column prop="agreementNo" label="协议号" min-width="150" show-overflow-tooltip />
        <el-table-column label="适用范围" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ procurementOwnershipDisplay(row) }}</template>
        </el-table-column>
        <el-table-column label="供应商/物料" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.supplierName }} / {{ row.materialCode }} {{ row.materialName }}</template>
        </el-table-column>
        <el-table-column label="业务/审批状态" min-width="190">
          <template #default="{ row }">
            <div>业务状态：{{ priceAgreementStatusLabel(row.status, row.statusName) }}</div>
            <div>审批状态：{{ procurementApprovalStatusLabel(row.approvalStatus, row.approvalStatusName) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="税价" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <div>未税单价 {{ formatProcurementAmount(row.taxExcludedUnitPrice) }}</div>
            <div>含税单价 {{ formatProcurementAmount(row.taxIncludedUnitPrice) }}</div>
            <div>税率 {{ formatProcurementAmount(row.taxRate) }} / {{ row.currency }}</div>
          </template>
        </el-table-column>
        <el-table-column label="有效期" min-width="200">
          <template #default="{ row }">{{ row.validFrom }} 至 {{ row.validTo }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ row }">
            <el-button
              v-if="canSubmitActivation(row)"
              data-test="submit-price-agreement-activation-list"
              text
              type="primary"
              :loading="actionLoading"
              :disabled="actionLoading"
              @click="submitActivation(row)"
            >
              提交激活审批
            </el-button>
            <router-link :to="{ name: 'procurement-price-agreement-detail', params: { id: String(row.id) } }">详情</router-link>
          </template>
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
