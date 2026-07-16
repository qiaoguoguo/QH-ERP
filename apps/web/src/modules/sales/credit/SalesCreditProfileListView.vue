<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { salesFulfillmentApi, type SalesCreditProfileRecord } from '../../../shared/api/salesFulfillmentApi'
import { useAuthStore } from '../../../stores/authStore'
import { pageItems } from '../../system/shared/pageHelpers'
import { formatSalesDecimal, normalizeSalesId, salesFulfillmentErrorMessage } from '../salesFulfillmentPageHelpers'

const authStore = useAuthStore()
const records = ref<SalesCreditProfileRecord[]>([])
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const drafts = reactive<Record<string, { creditLimit: string; frozen: boolean; blockOverdue: boolean; reviewDate: string; remark: string }>>({})
const filters = reactive({ keyword: '', page: 1, pageSize: 10 })

function isSystemAdmin() {
  return authStore.roles.some((role) => role.code === 'SYSTEM_ADMIN')
}

function canViewCredit() {
  return authStore.hasPermission('sales:credit:view') || isSystemAdmin()
}

function canManageCredit(record: SalesCreditProfileRecord) {
  return authStore.hasPermission('sales:credit:manage')
    && !record.creditRestricted
    && (record.allowedActions ?? []).includes('UPDATE')
}

function ensureDraft(record: SalesCreditProfileRecord) {
  const key = String(record.customerId)
  if (!drafts[key]) {
    drafts[key] = {
      creditLimit: record.creditLimit ?? '',
      frozen: Boolean(record.frozen),
      blockOverdue: Boolean(record.blockOverdue),
      reviewDate: record.reviewDate ?? '',
      remark: record.remark ?? '',
    }
  }
  return drafts[key]
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    if (!canViewCredit()) {
      records.value = []
      error.value = '无信用档案查看权限'
      return
    }
    const page = await salesFulfillmentApi.creditProfiles.list({
      keyword: filters.keyword,
      page: filters.page,
      pageSize: filters.pageSize,
    })
    records.value = pageItems(page)
    records.value.forEach(ensureDraft)
  } catch (caught) {
    records.value = []
    error.value = salesFulfillmentErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function saveRecord(record: SalesCreditProfileRecord) {
  if (actionLoading.value || !canManageCredit(record)) {
    return
  }
  const draft = ensureDraft(record)
  actionLoading.value = true
  actionError.value = ''
  try {
    await salesFulfillmentApi.creditProfiles.upsert(record.customerId, {
      customerId: normalizeSalesId(record.customerId),
      creditLimit: draft.creditLimit,
      frozen: draft.frozen,
      blockOverdue: draft.blockOverdue,
      reviewDate: draft.reviewDate,
      remark: draft.remark,
      version: record.version,
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = salesFulfillmentErrorMessage(caught)
    await loadRecords()
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
        <h1>信用档案</h1>
        <p>商业信用额度、三段占用、逾期风险和权限受限状态。</p>
      </div>
    </header>
    <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
    <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />

    <el-empty v-if="!loading && records.length === 0" description="暂无信用档案" />
    <div class="sales-record-grid" v-loading="loading">
      <article v-for="record in records" :key="record.customerId" class="sales-record-row">
        <div class="decision-column">
          <strong>{{ record.customerCode }} {{ record.customerName }}</strong>
          <span v-if="record.creditRestricted">信用信息受限</span>
          <span v-else>商业信用额度 {{ formatSalesDecimal(record.creditLimit) }} CNY</span>
        </div>
        <div class="state-column">
          <template v-if="!record.creditRestricted">
            <span>订单承诺 {{ formatSalesDecimal(record.exposure?.orderCommitmentAmount) }}</span>
            <span>待建应收出库 {{ formatSalesDecimal(record.exposure?.unsettledShipmentAmount) }}</span>
            <span>基础应收未收 {{ formatSalesDecimal(record.exposure?.receivableOutstandingAmount) }}</span>
            <span>可用额度 {{ formatSalesDecimal(record.exposure?.availableCredit) }}</span>
          </template>
          <span v-else>额度、占用、逾期和例外原因已脱敏</span>
        </div>
        <div class="edit-column">
          <template v-if="!record.creditRestricted">
            <input
              v-model="ensureDraft(record).creditLimit"
              :data-test="`credit-limit-${record.customerId}`"
            />
            <span>冻结：{{ ensureDraft(record).frozen ? '是' : '否' }} / 逾期阻断：{{ ensureDraft(record).blockOverdue ? '是' : '否' }}</span>
          </template>
        </div>
        <div class="action-column">
          <el-button
            v-if="canManageCredit(record)"
            :data-test="`save-credit-profile-${record.customerId}`"
            text
            type="primary"
            :disabled="actionLoading"
            @click="saveRecord(record)"
          >
            保存
          </el-button>
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
  grid-template-columns: minmax(220px, 1fr) minmax(250px, 1.2fr) minmax(210px, 1fr) minmax(120px, auto);
  padding: 12px;
}

.decision-column,
.state-column,
.edit-column,
.action-column {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

input {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  min-height: 30px;
  padding: 0 8px;
}

.action-column {
  align-items: flex-end;
  position: sticky;
  right: 0;
}
</style>
