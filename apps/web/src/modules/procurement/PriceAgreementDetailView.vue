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
          <span>业务状态：{{ record.statusName || record.status }}</span>
          <span>审批状态：{{ record.approvalStatusName || record.approvalStatus || '未提交' }}</span>
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
    <template v-if="record">
      <section class="section-block">
        <h2>协议税价明细</h2>
        <div class="table-scroll">
          <el-table :data="detailRows" empty-text="暂无价格协议明细" stripe>
            <el-table-column label="供应商" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.supplierName }}</template>
            </el-table-column>
            <el-table-column label="物料" min-width="190" show-overflow-tooltip>
              <template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template>
            </el-table-column>
            <el-table-column label="未税单价" min-width="120" align="right">
              <template #default="{ row }">未税单价 {{ formatProcurementAmount(row.taxExcludedUnitPrice) }}</template>
            </el-table-column>
            <el-table-column label="含税单价" min-width="120" align="right">
              <template #default="{ row }">含税单价 {{ formatProcurementAmount(row.taxIncludedUnitPrice) }}</template>
            </el-table-column>
            <el-table-column label="税率/币种" min-width="130">
              <template #default="{ row }">税率 {{ formatProcurementAmount(row.taxRate) }} / {{ row.currency }}</template>
            </el-table-column>
            <el-table-column label="有效期" min-width="190">
              <template #default="{ row }">有效期：{{ row.validFrom }} 至 {{ row.validTo }}</template>
            </el-table-column>
          </el-table>
        </div>
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
  </MasterDataTableView>
</template>

<style scoped>
.trace-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(4, minmax(180px, 1fr));
}

.state-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
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
</style>
