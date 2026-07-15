<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type DocumentTaskRecord,
} from '../../shared/api/documentPlatformApi'
import { procurementApi, type EffectivePurchaseSupplyRecord } from '../../shared/api/procurementApi'
import { pageItems } from '../system/shared/pageHelpers'
import {
  formatProcurementAmount,
  formatProcurementQuantity,
  procurementErrorMessage,
  procurementOwnershipDisplay,
  procurementPriceSourceDisplay,
} from './procurementPageHelpers'
import ProcurementDocumentTaskPanel from './ProcurementDocumentTaskPanel.vue'
import { useAuthStore } from '../../stores/authStore'

const authStore = useAuthStore()
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const records = ref<EffectivePurchaseSupplyRecord[]>([])
const latestDocumentTask = ref<DocumentTaskRecord | null>(null)
const filters = reactive({
  projectId: undefined as string | number | undefined,
  materialId: undefined as string | number | undefined,
  supplierId: undefined as string | number | undefined,
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

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await procurementApi.effectiveSupplies.list({
      projectId: filters.projectId,
      materialId: filters.materialId,
      supplierId: filters.supplierId,
      procurementMode: filters.procurementMode,
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
      projectId: filters.projectId,
      materialId: filters.materialId,
      supplierId: filters.supplierId,
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

onMounted(() => {
  void loadRecords()
})
</script>

<template>
  <section class="procurement-list-page">
    <header class="page-header">
      <div>
        <h1>有效采购供给</h1>
        <p>只读供给视图，供后续阶段消费；本页不做计算和推荐。</p>
      </div>
      <el-button v-if="canExport" data-test="export-effective-supplies" :loading="actionLoading" @click="exportEffectiveSupplies">
        当前筛选导出
      </el-button>
    </header>
    <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
    <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
    <ProcurementDocumentTaskPanel :task="latestDocumentTask" />
    <el-empty v-if="!loading && records.length === 0" description="暂无有效采购供给" />
    <div class="procurement-table" v-loading="loading">
      <article v-for="record in records" :key="record.id" class="procurement-row">
        <div class="decision-column">
          <strong>{{ record.orderNo }}</strong>
          <span>{{ procurementOwnershipDisplay(record) }}</span>
          <span>{{ record.materialCode }} {{ record.materialName }}</span>
        </div>
        <div class="state-column">
          <span>{{ record.supplierName }}</span>
          <span>业务状态：{{ record.statusName || record.status }}</span>
          <span>{{ procurementPriceSourceDisplay(record) }}</span>
        </div>
        <div class="progress-column">
          <span>预计到货 {{ record.expectedArrivalDate || '-' }}</span>
          <span>剩余 {{ formatProcurementQuantity(record.remainingQuantity) }}</span>
          <span>{{ record.countedAsEffectiveSupply ? '计入有效供给' : (record.notCountedReason || '不计入有效供给') }}</span>
        </div>
        <div class="cost-column">
          <span>{{ costText(record) }}</span>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.procurement-list-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header,
.procurement-row {
  display: grid;
  gap: 12px;
  grid-template-columns: 1fr auto;
}

.procurement-table {
  display: grid;
  gap: 10px;
}

.procurement-row {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  grid-template-columns: minmax(260px, 1.3fr) minmax(200px, 1fr) minmax(220px, 1fr) minmax(150px, auto);
  padding: 12px;
}

.decision-column,
.state-column,
.progress-column,
.cost-column {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
