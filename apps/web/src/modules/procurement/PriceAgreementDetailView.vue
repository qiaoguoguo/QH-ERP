<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { createIdempotencyKey } from '../../shared/api/documentPlatformApi'
import { procurementApi, type PriceAgreementDetailRecord, type ResourceId } from '../../shared/api/procurementApi'
import { useAuthStore } from '../../stores/authStore'
import {
  formatProcurementAmount,
  priceAgreementStatusLabel,
  procurementApprovalStatusLabel,
  procurementErrorMessage,
  procurementOwnershipDisplay,
} from './procurementPageHelpers'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'

const route = useRoute()
const authStore = useAuthStore()
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const record = ref<PriceAgreementDetailRecord | null>(null)
const pageTitle = computed(() => record.value?.agreementNo ?? '价格协议详情')
const pageDescription = computed(() => record.value ? procurementOwnershipDisplay(record.value) : '查看价格协议税价、来源链、审批和审计信息。')
const detailRows = computed(() => (record.value ? [record.value] : []))

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
  <MasterDataTableView :title="pageTitle" :description="pageDescription">
    <template #actions>
      <template v-if="record">
        <div class="state-box">
          <span>业务状态：{{ priceAgreementStatusLabel(record.status, record.statusName) }}</span>
          <span>审批状态：{{ procurementApprovalStatusLabel(record.approvalStatus, record.approvalStatusName) }}</span>
        </div>
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
      </template>
    </template>
    <template #alerts>
      <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
      <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
      <el-alert v-if="loading" class="page-alert" type="info" title="价格协议详情加载中" show-icon :closable="false" />
    </template>
    <div v-if="record" class="detail-stack">
      <section class="section-block">
        <div class="section-heading">
          <div>
            <h2>协议税价明细</h2>
            <p>展示协议适用的供应商、物料、税价及有效期限。</p>
          </div>
        </div>
        <div class="table-scroll">
          <el-table :data="detailRows" empty-text="暂无价格协议明细" stripe>
            <el-table-column label="供应商" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.supplierName }}</template>
            </el-table-column>
            <el-table-column label="物料" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template>
            </el-table-column>
            <el-table-column label="未税单价" min-width="120" align="right">
              <template #default="{ row }">{{ formatProcurementAmount(row.taxExcludedUnitPrice) }}</template>
            </el-table-column>
            <el-table-column label="含税单价" min-width="120" align="right">
              <template #default="{ row }">{{ formatProcurementAmount(row.taxIncludedUnitPrice) }}</template>
            </el-table-column>
            <el-table-column label="税率/币种" min-width="130">
              <template #default="{ row }">{{ formatProcurementAmount(row.taxRate) }} / {{ row.currency }}</template>
            </el-table-column>
            <el-table-column label="有效期" min-width="210">
              <template #default="{ row }">{{ row.validFrom }} 至 {{ row.validTo }}</template>
            </el-table-column>
          </el-table>
        </div>
      </section>
      <section class="trace-grid" aria-label="协议关联信息">
        <article class="trace-card">
          <h2>来源链</h2>
          <div v-if="record.sourceChain?.length" class="trace-list">
            <p v-for="source in record.sourceChain" :key="`${source.sourceType}-${source.sourceNo}`">
              <strong>{{ source.sourceNo }}</strong>
              <span>{{ source.summary }}</span>
            </p>
          </div>
          <p v-else class="empty-text">暂无来源记录</p>
        </article>
        <article class="trace-card">
          <h2>审批</h2>
          <p class="info-row">
            <span>审批状态</span>
            <strong>{{ procurementApprovalStatusLabel(record.approvalStatus, record.approvalStatusName) }}</strong>
          </p>
        </article>
        <article class="trace-card">
          <h2>附件</h2>
          <p class="empty-text">协议附件复用平台附件能力。</p>
        </article>
        <article class="trace-card">
          <h2>审计</h2>
          <p class="info-row"><span>创建人</span><strong>{{ record.createdByName || '-' }}</strong></p>
          <p class="info-row"><span>更新时间</span><strong>{{ record.updatedAt || '-' }}</strong></p>
        </article>
      </section>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.trace-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(4, minmax(180px, 1fr));
}

.detail-stack {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.trace-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  min-width: 0;
  padding: 14px;
}

.trace-card h2 {
  font-size: 16px;
  margin: 0 0 12px;
}

.trace-card p {
  margin: 0;
}

.trace-list {
  display: grid;
  gap: 8px;
}

.trace-list p {
  display: grid;
  gap: 3px;
}

.trace-list span,
.empty-text,
.section-heading p {
  color: #606266;
}

.info-row {
  align-items: flex-start;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.info-row + .info-row {
  margin-top: 8px;
}

.info-row span {
  color: #606266;
  flex: 0 0 auto;
}

.info-row strong {
  min-width: 0;
  text-align: right;
  word-break: break-word;
}

.state-box {
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 12px;
  text-align: right;
}

.section-block {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  display: grid;
  gap: 12px;
  padding: 14px;
}

.section-block h2 {
  font-size: 16px;
  margin: 0;
}

.section-heading p {
  margin: 6px 0 0;
}

@media (max-width: 1200px) {
  .trace-grid {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
  }
}

@media (max-width: 720px) {
  .trace-grid {
    grid-template-columns: 1fr;
  }

  .state-box {
    text-align: left;
  }
}
</style>
