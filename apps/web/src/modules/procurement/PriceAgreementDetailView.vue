<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { createIdempotencyKey } from '../../shared/api/documentPlatformApi'
import { procurementApi, type PriceAgreementDetailRecord, type ResourceId } from '../../shared/api/procurementApi'
import { useAuthStore } from '../../stores/authStore'
import {
  formatProcurementAmount,
  procurementErrorMessage,
  procurementOwnershipDisplay,
} from './procurementPageHelpers'

const route = useRoute()
const authStore = useAuthStore()
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const record = ref<PriceAgreementDetailRecord | null>(null)

function allowed(action: string): boolean {
  return Boolean(record.value?.allowedActions?.includes(action))
}

const canSubmitActivation = computed(() => (
  (allowed('SUBMIT') || allowed('SUBMIT_ACTIVATION')) && authStore.hasPermission('procurement:price-agreement:submit')
))

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await procurementApi.priceAgreements.get(route.params.id as ResourceId)
  } catch (caught) {
    record.value = null
    error.value = procurementErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadRecord()
})

async function submitActivation() {
  if (!record.value || actionLoading.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await procurementApi.priceAgreements.submitActivation(record.value.id, {
      version: record.value.version,
      reason: '提交价格协议激活审批',
      idempotencyKey: createIdempotencyKey('price-agreement-submit-activation'),
    })
    await loadRecord()
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
    await loadRecord()
  } finally {
    actionLoading.value = false
  }
}
</script>

<template>
  <section class="procurement-detail-page" v-loading="loading">
    <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
    <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
    <template v-if="record">
      <header class="detail-header">
        <div>
          <h1>{{ record.agreementNo }}</h1>
          <p>{{ procurementOwnershipDisplay(record) }}</p>
        </div>
        <div class="state-box">
          <span>业务状态：{{ record.statusName || record.status }}</span>
          <span>审批状态：{{ record.approvalStatusName || record.approvalStatus || '未提交' }}</span>
        </div>
      </header>
      <div class="action-bar">
        <el-button
          v-if="canSubmitActivation"
          data-test="submit-price-agreement-activation"
          type="primary"
          :loading="actionLoading"
          :disabled="actionLoading"
          @click="submitActivation"
        >
          提交激活审批
        </el-button>
      </div>
      <section class="summary-grid">
        <div>供应商：{{ record.supplierName }}</div>
        <div>物料：{{ record.materialCode }} {{ record.materialName }}</div>
        <div>未税单价 {{ formatProcurementAmount(record.taxExcludedUnitPrice) }}</div>
        <div>含税单价 {{ formatProcurementAmount(record.taxIncludedUnitPrice) }}</div>
        <div>税率 {{ formatProcurementAmount(record.taxRate) }} / {{ record.currency }}</div>
        <div>有效期：{{ record.validFrom }} 至 {{ record.validTo }}</div>
      </section>
      <section class="trace-grid">
        <div>
          <h2>来源链</h2>
          <p v-for="source in record.sourceChain ?? []" :key="`${source.sourceType}-${source.sourceNo}`">
            {{ source.sourceNo }} {{ source.summary }}
          </p>
        </div>
        <div>
          <h2>审批</h2>
          <p>审批状态：{{ record.approvalStatusName || record.approvalStatus || '未提交' }}</p>
        </div>
        <div>
          <h2>附件</h2>
          <p>协议附件复用 022 平台附件。</p>
        </div>
        <div>
          <h2>审计</h2>
          <p>创建人：{{ record.createdByName }}，更新时间：{{ record.updatedAt }}</p>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.procurement-detail-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-header,
.summary-grid,
.trace-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(4, minmax(180px, 1fr));
}

.detail-header {
  grid-template-columns: 1fr auto;
}

.detail-header h1 {
  font-size: 22px;
  margin: 0 0 6px;
}

.state-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
  text-align: right;
}

.action-bar {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
</style>
