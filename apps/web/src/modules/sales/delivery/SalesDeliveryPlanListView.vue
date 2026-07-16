<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type DocumentTaskRecord,
} from '../../../shared/api/documentPlatformApi'
import { salesFulfillmentApi, type SalesDeliveryPlanRecord, type SalesDeliveryPlanStatus } from '../../../shared/api/salesFulfillmentApi'
import { useAuthStore } from '../../../stores/authStore'
import { pageItems } from '../../system/shared/pageHelpers'
import SalesDocumentTaskPanel from '../SalesDocumentTaskPanel.vue'
import {
  deliveryPlanStatusLabel,
  deliveryPlanDate,
  formatSalesDecimal,
  optionalSalesId,
  salesFulfillmentErrorMessage,
} from '../salesFulfillmentPageHelpers'

const authStore = useAuthStore()
const records = ref<SalesDeliveryPlanRecord[]>([])
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
  } catch (caught) {
    records.value = []
    error.value = salesFulfillmentErrorMessage(caught)
  } finally {
    loading.value = false
  }
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
  <section class="sales-list-page">
    <header class="page-header">
      <div>
        <h1>交付计划</h1>
        <p>全局只读查看销售订单交付计划；维护动作回到订单详情。</p>
      </div>
      <el-button v-if="canExport()" data-test="export-sales-delivery-plans" :loading="actionLoading" @click="exportPlans">
        当前筛选导出
      </el-button>
    </header>

    <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
    <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
    <SalesDocumentTaskPanel :task="latestDocumentTask" />

    <div class="filter-strip">
      <el-input v-model="filters.keyword" placeholder="订单、客户或物料" clearable />
      <el-input v-model="filters.projectId" placeholder="项目 ID" clearable />
      <el-select v-model="filters.status" clearable placeholder="计划状态">
        <el-option label="计划中" value="PLANNED" />
        <el-option label="部分出库" value="PARTIALLY_SHIPPED" />
        <el-option label="已全部出库" value="SHIPPED" />
        <el-option label="已关闭" value="CLOSED" />
      </el-select>
      <el-button data-test="search-sales-delivery-plans" type="primary" @click="loadRecords">查询</el-button>
    </div>

    <el-empty v-if="!loading && records.length === 0" description="暂无交付计划" />
    <div class="sales-record-grid" v-loading="loading">
      <article v-for="record in records" :key="record.id" class="sales-record-row">
        <div class="decision-column">
          <strong>{{ record.orderNo }} / 行 {{ record.lineNo }}</strong>
          <span>{{ record.projectNo }} {{ record.projectName }}</span>
          <span>{{ record.contractNo || '合同信息受限' }}</span>
        </div>
        <div class="state-column">
          <span>{{ record.materialCode }} {{ record.materialName }}</span>
          <span>预计日期：{{ deliveryPlanDate(record) }}</span>
          <span>{{ deliveryPlanStatusLabel(record.status) }}</span>
        </div>
        <div class="progress-column">
          <span>
            计划/已发/剩余：{{ formatSalesDecimal(record.plannedQuantity) }}/{{
              formatSalesDecimal(record.shippedQuantity)
            }}/{{ formatSalesDecimal(record.remainingQuantity) }}
          </span>
          <span v-if="record.legacyDeliveryPlanCompatible">历史兼容计划</span>
          <span v-if="record.closeReason">关闭原因：{{ record.closeReason }}</span>
        </div>
        <div class="action-column">
          <el-button text type="primary">进入订单详情维护</el-button>
          <span v-if="record.actionDisabledReason">{{ record.actionDisabledReason }}</span>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.sales-list-page,
.sales-record-grid {
  display: grid;
  gap: 12px;
}

.page-header,
.filter-strip {
  align-items: flex-start;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.filter-strip {
  flex-wrap: wrap;
  justify-content: flex-start;
}

.page-header h1 {
  font-size: 22px;
  margin: 0 0 6px;
}

.page-header p {
  color: #606266;
  margin: 0;
}

.sales-record-row {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(250px, 1.2fr) minmax(210px, 1fr) minmax(220px, 1fr) minmax(160px, auto);
  padding: 12px;
}

.decision-column,
.state-column,
.progress-column,
.action-column {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.action-column {
  align-items: flex-end;
  position: sticky;
  right: 0;
}
</style>
