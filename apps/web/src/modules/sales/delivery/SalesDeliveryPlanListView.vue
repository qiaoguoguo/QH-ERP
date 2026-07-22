<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type DocumentTaskRecord,
} from '../../../shared/api/documentPlatformApi'
import { masterDataApi, type MaterialRecord, type PartnerRecord } from '../../../shared/api/masterDataApi'
import { salesApi, type SalesOrderSummaryRecord } from '../../../shared/api/salesApi'
import { salesFulfillmentApi, type SalesDeliveryPlanRecord, type SalesDeliveryPlanStatus } from '../../../shared/api/salesFulfillmentApi'
import { salesProjectApi, type SalesOrderProjectContractCandidate, type SalesProjectSummary } from '../../../shared/api/salesProjectApi'
import { useAuthStore } from '../../../stores/authStore'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import BusinessReferenceSelect from '../../system/shared/BusinessReferenceSelect.vue'
import type { BusinessReferenceOption } from '../../system/shared/businessReferenceSelectTypes'
import { pageItems, pageTotal } from '../../system/shared/pageHelpers'
import SalesDocumentTaskPanel from '../SalesDocumentTaskPanel.vue'
import {
  deliveryPlanStatusLabel,
  deliveryPlanDate,
  formatSalesDecimal,
  optionalSalesId,
  salesFulfillmentErrorMessage,
} from '../salesFulfillmentPageHelpers'

const authStore = useAuthStore()
const router = useRouter()
const records = ref<SalesDeliveryPlanRecord[]>([])
const total = ref(0)
const latestDocumentTask = ref<DocumentTaskRecord | null>(null)
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const filters = reactive({
  keyword: '',
  customerId: '' as string | number | '',
  projectId: '' as string | number | '',
  contractId: '' as string | number | '',
  orderId: '' as string | number | '',
  materialId: '' as string | number | '',
  status: undefined as SalesDeliveryPlanStatus | undefined,
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

function orderOption(order: SalesOrderSummaryRecord): BusinessReferenceOption {
  return { id: order.id, label: `${order.orderNo} ${order.customerName}` }
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

async function loadOrderOptions(keyword: string) {
  const page = await salesApi.orders.list({ keyword, page: 1, pageSize: 50 })
  return pageItems(page).map(orderOption)
}

async function loadContractOptions(keyword: string) {
  const page = await salesProjectApi.listOrderLinkCandidates({ keyword, page: 1, pageSize: 50 })
  return pageItems(page).map(contractOption)
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await salesFulfillmentApi.deliveryPlans.list({
      keyword: filters.keyword,
      customerId: optionalSalesId(filters.customerId),
      projectId: optionalSalesId(filters.projectId),
      contractId: optionalSalesId(filters.contractId),
      orderId: optionalSalesId(filters.orderId),
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
  filters.keyword = ''
  filters.customerId = ''
  filters.projectId = ''
  filters.contractId = ''
  filters.orderId = ''
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

async function exportPlans() {
  if (actionLoading.value) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    latestDocumentTask.value = await documentPlatformApi.exports.createSalesDeliveryPlans({
      keyword: filters.keyword,
      customerId: optionalSalesId(filters.customerId),
      projectId: optionalSalesId(filters.projectId),
      contractId: optionalSalesId(filters.contractId),
      orderId: optionalSalesId(filters.orderId),
      materialId: optionalSalesId(filters.materialId),
      status: filters.status,
      expectedDateFrom: filters.expectedDateFrom,
      expectedDateTo: filters.expectedDateTo,
      countedOnly: filters.countedOnly,
      idempotencyKey: createIdempotencyKey('sales-delivery-plan-export'),
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
    title="交付计划"
    description="全局只读查看销售订单交付计划；维护动作回到订单详情。"
  >
    <template #actions>
      <el-button v-if="canExport()" data-test="export-sales-delivery-plans" :loading="actionLoading" @click="exportPlans">
        当前筛选导出
      </el-button>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
      <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
      <SalesDocumentTaskPanel :task="latestDocumentTask" />
    </template>

    <template #filters>
    <el-form class="query-form" label-position="top">
      <el-form-item label="关键词">
        <el-input v-model="filters.keyword" placeholder="订单、客户或物料" clearable />
      </el-form-item>
      <el-form-item label="项目">
        <BusinessReferenceSelect v-model="filters.projectId" placeholder="搜索项目编号或名称" :load-options="loadProjectOptions" />
      </el-form-item>
      <el-form-item label="客户">
        <BusinessReferenceSelect v-model="filters.customerId" placeholder="搜索客户编码或名称" :load-options="loadCustomerOptions" />
      </el-form-item>
      <el-form-item label="合同">
        <BusinessReferenceSelect v-model="filters.contractId" placeholder="搜索合同编号或名称" :load-options="loadContractOptions" />
      </el-form-item>
      <el-form-item label="订单">
        <BusinessReferenceSelect v-model="filters.orderId" placeholder="搜索订单号或客户" :load-options="loadOrderOptions" />
      </el-form-item>
      <el-form-item label="物料">
        <BusinessReferenceSelect v-model="filters.materialId" placeholder="搜索物料编码或名称" :load-options="loadMaterialOptions" />
      </el-form-item>
      <el-form-item label="计划状态">
        <el-select v-model="filters.status" clearable placeholder="计划状态">
          <el-option label="计划中" value="PLANNED" />
          <el-option label="部分出库" value="PARTIALLY_SHIPPED" />
          <el-option label="已全部出库" value="SHIPPED" />
          <el-option label="已关闭" value="CLOSED" />
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
        <el-button data-test="search-sales-delivery-plans" type="primary" @click="searchRecords">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>
    </template>

    <div class="table-scroll">
      <el-table v-loading="loading" :data="records" row-key="id" :empty-text="loading ? '加载中' : '暂无交付计划'">
        <el-table-column label="订单与项目" min-width="260">
          <template #default="{ row }">
            <strong>{{ row.orderNo }} / 行 {{ row.lineNo }}</strong>
            <span>{{ row.projectNo }} {{ row.projectName }}</span>
            <span>{{ row.contractNo || '合同信息受限' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="物料与状态" min-width="240">
          <template #default="{ row }">
            <span>{{ row.materialCode }} {{ row.materialName }}</span>
            <span>预计日期：{{ deliveryPlanDate(row) }}</span>
            <span>{{ deliveryPlanStatusLabel(row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="计划进度" min-width="280">
          <template #default="{ row }">
            <span>
              计划/已发/剩余：{{ formatSalesDecimal(row.plannedQuantity) }}/{{
                formatSalesDecimal(row.shippedQuantity)
              }}/{{ formatSalesDecimal(row.remainingQuantity) }}
            </span>
            <span v-if="row.legacyDeliveryPlanCompatible">历史兼容计划</span>
            <span v-if="row.closeReason">关闭原因：{{ row.closeReason }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="180">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button text type="primary" @click="router.push({ name: 'sales-order-detail', params: { id: String(row.orderId) } })">
                进入订单详情维护
              </el-button>
              <span v-if="row.actionDisabledReason">{{ row.actionDisabledReason }}</span>
            </div>
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
.table-scroll span {
  display: block;
}

.row-actions {
  display: grid;
  gap: 8px;
  justify-items: end;
}
</style>
