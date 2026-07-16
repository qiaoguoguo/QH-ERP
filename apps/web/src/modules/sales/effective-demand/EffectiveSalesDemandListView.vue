<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type DocumentTaskRecord,
} from '../../../shared/api/documentPlatformApi'
import { masterDataApi, type MaterialRecord, type PartnerRecord } from '../../../shared/api/masterDataApi'
import {
  salesFulfillmentApi,
  type SalesEffectiveDemandRecord,
  type SalesEffectiveDemandStatus,
} from '../../../shared/api/salesFulfillmentApi'
import { salesProjectApi, type SalesOrderProjectContractCandidate, type SalesProjectSummary } from '../../../shared/api/salesProjectApi'
import { useAuthStore } from '../../../stores/authStore'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import BusinessReferenceSelect from '../../system/shared/BusinessReferenceSelect.vue'
import type { BusinessReferenceOption } from '../../system/shared/businessReferenceSelectTypes'
import { pageItems, pageTotal } from '../../system/shared/pageHelpers'
import SalesDocumentTaskPanel from '../SalesDocumentTaskPanel.vue'
import { formatSalesDecimal, optionalSalesId, salesFulfillmentErrorMessage, salesSourceChainLabel } from '../salesFulfillmentPageHelpers'

const authStore = useAuthStore()
const records = ref<SalesEffectiveDemandRecord[]>([])
const total = ref(0)
const latestDocumentTask = ref<DocumentTaskRecord | null>(null)
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const filters = reactive({
  projectId: '' as string | number | '',
  customerId: '' as string | number | '',
  contractId: '' as string | number | '',
  materialId: '' as string | number | '',
  status: undefined as SalesEffectiveDemandStatus | undefined,
  expectedDateFrom: '',
  expectedDateTo: '',
  countedOnly: true,
  page: 1,
  pageSize: 10,
})

function canExport() {
  return authStore.hasPermission('platform:document-task:create') && authStore.hasPermission('sales:document:export')
}

function customerOption(customer: PartnerRecord): BusinessReferenceOption {
  return { id: customer.id, label: `${customer.code} ${customer.name}` }
}

function materialOption(material: MaterialRecord): BusinessReferenceOption {
  return { id: material.id, label: `${material.code} ${material.name}` }
}

function projectOption(project: SalesProjectSummary): BusinessReferenceOption {
  return { id: project.id, label: `${project.projectNo} ${project.name}` }
}

function contractOption(candidate: SalesOrderProjectContractCandidate): BusinessReferenceOption {
  return {
    id: candidate.contractId,
    label: `${candidate.contractNo} ${candidate.contractName} / ${candidate.projectNo} ${candidate.projectName}`,
  }
}

async function loadCustomerOptions(keyword: string) {
  const page = await masterDataApi.customers.list({ keyword, status: 'ENABLED', page: 1, pageSize: 50 })
  return pageItems(page).map(customerOption)
}

async function loadMaterialOptions(keyword: string) {
  const page = await masterDataApi.materials.list({ keyword, status: 'ENABLED', page: 1, pageSize: 50 })
  return pageItems(page).map(materialOption)
}

async function loadProjectOptions(keyword: string) {
  const page = await salesProjectApi.projects.list({ keyword, status: 'ACTIVE', page: 1, pageSize: 50 })
  return pageItems(page).map(projectOption)
}

async function loadContractOptions(keyword: string) {
  const page = await salesProjectApi.listOrderLinkCandidates({ keyword, page: 1, pageSize: 50 })
  return pageItems(page).map(contractOption)
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await salesFulfillmentApi.effectiveDemands.list({
      projectId: optionalSalesId(filters.projectId),
      customerId: optionalSalesId(filters.customerId),
      contractId: optionalSalesId(filters.contractId),
      materialId: optionalSalesId(filters.materialId),
      status: filters.status,
      expectedDateFrom: filters.expectedDateFrom,
      expectedDateTo: filters.expectedDateTo,
      countedOnly: filters.countedOnly,
      page: filters.page,
      pageSize: filters.pageSize,
    })
    records.value = pageItems(page)
    total.value = pageTotal(page)
  } catch (caught) {
    records.value = []
    total.value = 0
    error.value = salesFulfillmentErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function searchRecords() {
  filters.page = 1
  await loadRecords()
}

async function resetFilters() {
  filters.projectId = ''
  filters.customerId = ''
  filters.contractId = ''
  filters.materialId = ''
  filters.status = undefined
  filters.expectedDateFrom = ''
  filters.expectedDateTo = ''
  filters.countedOnly = true
  filters.page = 1
  await loadRecords()
}

async function changePage(page: number) {
  filters.page = page
  await loadRecords()
}

async function changePageSize(pageSize: number) {
  filters.pageSize = pageSize
  filters.page = 1
  await loadRecords()
}

async function diagnose() {
  filters.countedOnly = false
  await loadRecords()
}

async function restoreCountedOnly() {
  filters.countedOnly = true
  await loadRecords()
}

async function exportDemands() {
  if (actionLoading.value) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    latestDocumentTask.value = await documentPlatformApi.exports.createSalesEffectiveDemands({
      projectId: optionalSalesId(filters.projectId),
      customerId: optionalSalesId(filters.customerId),
      contractId: optionalSalesId(filters.contractId),
      materialId: optionalSalesId(filters.materialId),
      status: filters.status,
      expectedDateFrom: filters.expectedDateFrom,
      expectedDateTo: filters.expectedDateTo,
      countedOnly: filters.countedOnly,
      idempotencyKey: createIdempotencyKey('sales-effective-demand-export'),
    })
  } catch (caught) {
    actionError.value = salesFulfillmentErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView
    title="有效销售需求"
    description="026 只读消费视图，默认仅显示真实开放需求。"
  >
    <template #actions>
      <div class="header-actions">
        <el-button data-test="diagnose-effective-demands" @click="diagnose">诊断未计入候选</el-button>
        <el-button
          v-if="!filters.countedOnly"
          data-test="restore-counted-effective-demands"
          type="primary"
          plain
          @click="restoreCountedOnly"
        >
          恢复默认有效需求
        </el-button>
        <el-button v-if="canExport()" data-test="export-sales-effective-demands" :loading="actionLoading" @click="exportDemands">
          当前筛选导出
        </el-button>
      </div>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
      <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
      <el-alert
        v-if="!filters.countedOnly"
        class="page-alert"
        type="warning"
        title="诊断模式：含未计入候选，不能作为 026 有效输入"
        show-icon
        :closable="false"
      />
      <SalesDocumentTaskPanel :task="latestDocumentTask" />
    </template>

    <template #filters>
    <el-form class="query-form" label-position="top">
      <el-form-item label="项目">
        <BusinessReferenceSelect v-model="filters.projectId" placeholder="搜索项目编号或名称" :load-options="loadProjectOptions" />
      </el-form-item>
      <el-form-item label="客户">
        <BusinessReferenceSelect v-model="filters.customerId" placeholder="搜索客户编码或名称" :load-options="loadCustomerOptions" />
      </el-form-item>
      <el-form-item label="合同">
        <BusinessReferenceSelect v-model="filters.contractId" placeholder="搜索合同编号或名称" :load-options="loadContractOptions" />
      </el-form-item>
      <el-form-item label="物料">
        <BusinessReferenceSelect v-model="filters.materialId" placeholder="搜索物料编码或名称" :load-options="loadMaterialOptions" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="filters.status" clearable placeholder="需求状态">
          <el-option label="开放" value="OPEN" />
          <el-option label="部分履约" value="PARTIALLY_SHIPPED" />
          <el-option label="逾期" value="OVERDUE" />
          <el-option label="已排除" value="EXCLUDED" />
        </el-select>
      </el-form-item>
      <el-form-item label="预计起">
        <el-date-picker
          v-model="filters.expectedDateFrom"
          type="date"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          value-on-clear=""
          placeholder="预计起"
        />
      </el-form-item>
      <el-form-item label="预计止">
        <el-date-picker
          v-model="filters.expectedDateTo"
          type="date"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          value-on-clear=""
          placeholder="预计止"
        />
      </el-form-item>
      <el-form-item label="只看有效">
        <el-checkbox v-model="filters.countedOnly">仅计入有效需求</el-checkbox>
      </el-form-item>
      <el-form-item class="query-actions" label="操作">
        <el-button data-test="search-sales-effective-demands" type="primary" @click="searchRecords">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>
    </template>

    <div class="table-scroll">
      <el-table v-loading="loading" :data="records" row-key="id" :empty-text="loading ? '加载中' : '暂无有效销售需求'">
        <el-table-column label="订单与来源" min-width="280">
          <template #default="{ row }">
            <strong>{{ row.orderNo }} / {{ row.sourceNo }}</strong>
            <span>{{ row.projectNo }} {{ row.projectName }}</span>
            <span>{{ row.customerName }} / {{ row.contractNo || '合同信息受限' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="物料与日期" min-width="240">
          <template #default="{ row }">
            <span>{{ row.materialCode }} {{ row.materialName }}</span>
            <span>{{ salesSourceChainLabel(Boolean(row.quoteId)) }}</span>
            <span>预计日期：{{ row.expectedDate }}</span>
          </template>
        </el-table-column>
        <el-table-column label="履约数量" min-width="280">
          <template #default="{ row }">
            <span>订单 {{ formatSalesDecimal(row.orderQuantity) }} / 计划 {{ formatSalesDecimal(row.plannedQuantity) }}</span>
            <span>已发 {{ formatSalesDecimal(row.shippedQuantity) }} / 退货 {{ formatSalesDecimal(row.returnedQuantity) }}</span>
            <span>开放需求 {{ formatSalesDecimal(row.openQuantity) }}</span>
            <span v-if="!row.countedAsEffectiveDemand">排除原因：{{ row.excludedReasonCode || '未返回' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="120" fixed="right">
          <template #default>
            <span>只读视图</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-pagination
      class="table-pagination"
      layout="total, sizes, prev, pager, next"
      :total="total"
      :current-page="filters.page"
      :page-size="filters.pageSize"
      :page-sizes="[10, 20, 50, 100]"
      @current-change="changePage"
      @size-change="changePageSize"
    />
  </MasterDataTableView>
</template>

<style scoped>
.header-actions {
  align-items: flex-start;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.table-scroll span {
  display: block;
}
</style>
