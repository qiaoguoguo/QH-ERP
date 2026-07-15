<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type DocumentTaskRecord,
} from '../../shared/api/documentPlatformApi'
import { procurementApi, type PriceAgreementSummaryRecord } from '../../shared/api/procurementApi'
import { pageItems } from '../system/shared/pageHelpers'
import {
  formatProcurementAmount,
  procurementErrorMessage,
  procurementOwnershipDisplay,
} from './procurementPageHelpers'
import ProcurementDocumentTaskPanel from './ProcurementDocumentTaskPanel.vue'
import { useAuthStore } from '../../stores/authStore'

const authStore = useAuthStore()
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const records = ref<PriceAgreementSummaryRecord[]>([])
const latestDocumentTask = ref<DocumentTaskRecord | null>(null)
const filters = reactive({
  keyword: '',
  supplierId: undefined as string | number | undefined,
  materialId: undefined as string | number | undefined,
  procurementMode: undefined as 'PUBLIC' | 'PROJECT' | undefined,
  projectId: undefined as string | number | undefined,
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

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await procurementApi.priceAgreements.list({
      keyword: filters.keyword,
      supplierId: filters.supplierId,
      materialId: filters.materialId,
      procurementMode: filters.procurementMode,
      projectId: filters.projectId,
      status: filters.status,
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
      supplierId: filters.supplierId,
      materialId: filters.materialId,
      procurementMode: filters.procurementMode,
      projectId: filters.projectId,
      status: filters.status,
      idempotencyKey: createIdempotencyKey('procurement-price-agreement-export'),
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
        <h1>价格协议</h1>
        <p>价格协议激活审批后才可作为采购订单价格来源。</p>
      </div>
      <el-button v-if="canExport" data-test="export-price-agreements" :loading="actionLoading" @click="exportPriceAgreements">
        当前筛选导出
      </el-button>
    </header>
    <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
    <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
    <ProcurementDocumentTaskPanel :task="latestDocumentTask" />
    <el-empty v-if="!loading && records.length === 0" description="暂无价格协议" />
    <div class="procurement-table" v-loading="loading">
      <article v-for="record in records" :key="record.id" class="procurement-row">
        <div class="decision-column">
          <strong>{{ record.agreementNo }}</strong>
          <span>{{ procurementOwnershipDisplay(record) }}</span>
          <span>{{ record.supplierName }} / {{ record.materialCode }} {{ record.materialName }}</span>
        </div>
        <div class="state-column">
          <span>业务状态：{{ record.statusName || record.status }}</span>
          <span>审批状态：{{ record.approvalStatusName || record.approvalStatus || '未提交' }}</span>
          <span>{{ record.validFrom }} 至 {{ record.validTo }}</span>
        </div>
        <div class="price-column">
          <span>未税单价 {{ formatProcurementAmount(record.taxExcludedUnitPrice) }}</span>
          <span>含税单价 {{ formatProcurementAmount(record.taxIncludedUnitPrice) }}</span>
          <span>税率 {{ formatProcurementAmount(record.taxRate) }} / {{ record.currency }}</span>
        </div>
        <div class="action-column">
          <el-button
            v-if="canSubmitActivation(record)"
            data-test="submit-price-agreement-activation-list"
            text
            type="primary"
            :loading="actionLoading"
            :disabled="actionLoading"
            @click="submitActivation(record)"
          >
            提交激活审批
          </el-button>
          <router-link :to="{ name: 'procurement-price-agreement-detail', params: { id: String(record.id) } }">详情</router-link>
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
  grid-template-columns: minmax(260px, 1.3fr) minmax(190px, 1fr) minmax(190px, 1fr) minmax(150px, auto);
  padding: 12px;
}

.decision-column,
.state-column,
.price-column,
.action-column {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
