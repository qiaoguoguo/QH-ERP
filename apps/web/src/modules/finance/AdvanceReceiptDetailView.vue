<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { financeSettlementApi, type AdvanceFundRecord } from '../../shared/api/financeSettlementApi'
import { useAuthStore } from '../../stores/authStore'
import { confirmAction } from '../../shared/ui/confirmDialog'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import {
  financeErrorMessage,
  financePermissions,
  formatFinanceAmount,
  isPositiveFinanceAmount,
  ownershipTypeText,
  settlementStatusText,
  voucherDraftStatusText,
} from './financePageHelpers'
import './Finance028Shared.css'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<AdvanceFundRecord | null>(null)
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const canEdit = computed(() => record.value?.allowedActions?.includes('UPDATE') && authStore.hasPermission(financePermissions.advanceReceiptUpdate))
const canPost = computed(() => record.value?.allowedActions?.includes('POST') && authStore.hasPermission(financePermissions.advanceReceiptPost))
const canCancel = computed(() => record.value?.allowedActions?.includes('CANCEL') && authStore.hasPermission(financePermissions.advanceReceiptCancel))
const canAllocate = computed(() => Boolean(record.value && isPositiveFinanceAmount(record.value.availableAmount)) && authStore.hasPermission(financePermissions.settlementAllocationCreate))
const allocationDisabledReason = computed(() => {
  if (!record.value) {
    return ''
  }
  if (!isPositiveFinanceAmount(record.value.availableAmount)) {
    return '未核销余额为 0，不能发起核销'
  }
  if (!authStore.hasPermission(financePermissions.settlementAllocationCreate)) {
    return '缺少核销创建权限，仅可查看余额'
  }
  return ''
})

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await financeSettlementApi.advanceReceipts.get(route.params.id as string)
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function openSettlementWorkbench() {
  if (!record.value) {
    return
  }
  void router.push({
    name: 'finance-settlement-workbench',
    query: {
      direction: 'CUSTOMER',
      fundType: 'ADVANCE_RECEIPT',
      fundId: String(record.value.id),
      returnTo: route.fullPath,
    },
  })
}

async function runFundAction(action: 'post' | 'cancel') {
  if (!record.value || actionLoading.value) {
    return
  }
  const label = action === 'post' ? '过账' : '取消'
  if (!(await confirmAction(`${label}预收款“${record.value.advanceNo}”？`))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    const payload = {
      version: record.value.version,
      idempotencyKey: `${action}-advance-receipt-${record.value.id}-${Date.now()}`,
    }
    if (action === 'post') {
      await financeSettlementApi.advanceReceipts.post(record.value.id, payload)
    } else {
      await financeSettlementApi.advanceReceipts.cancel(record.value.id, payload)
    }
    await loadRecord()
  } catch (caught) {
    actionError.value = financeErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(loadRecord)
</script>

<template>
  <MasterDataTableView title="预收款详情" description="查看客户真实收款资金、可用余额、已核销目标和凭证草稿摘要。">
    <template #actions>
      <el-button @click="router.push({ name: 'finance-advance-receipts' })">返回列表</el-button>
      <el-button v-if="canEdit" @click="router.push({ name: 'finance-advance-receipt-edit', params: { id: route.params.id } })">编辑草稿</el-button>
      <el-button v-if="canPost" data-test="post-advance-receipt" type="success" :loading="actionLoading" :disabled="actionLoading" @click="runFundAction('post')">过账</el-button>
      <el-button v-if="canCancel" data-test="cancel-advance-receipt" type="danger" :loading="actionLoading" :disabled="actionLoading" @click="runFundAction('cancel')">取消草稿</el-button>
    </template>
    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" type="info" title="预收款详情加载中" :closable="false" />
      <el-alert v-if="allocationDisabledReason" type="warning" :title="allocationDisabledReason" :closable="false" />
    </template>

    <div v-if="record" class="finance-summary-strip">
      <div><span>资金状态</span><strong>{{ settlementStatusText(record.status) }}</strong></div>
      <div><span>客户</span><strong>{{ record.partnerName }}</strong></div>
      <div><span>项目/公共</span><strong>{{ ownershipTypeText(record.ownershipType) }} {{ record.projectName ?? '' }}</strong></div>
      <div><span>业务日期</span><strong>{{ record.businessDate }}</strong></div>
      <div><span>收款金额</span><strong>{{ formatFinanceAmount(record.amount) }}</strong></div>
      <div><span>已核销金额</span><strong>{{ formatFinanceAmount(record.allocatedAmount) }}</strong></div>
      <div><span>可用余额</span><strong>{{ formatFinanceAmount(record.availableAmount) }}</strong></div>
      <div><span>核销状态</span><strong>{{ settlementStatusText(record.settlementStatus ?? record.status) }}</strong></div>
    </div>

    <div v-if="record" class="finance-section-grid">
      <section class="finance-section">
        <span class="finance-section-title">资金事实</span>
        <p>{{ record.advanceNo }} 由收款单 {{ record.fundNo }} 形成，不新增现金发生额。</p>
      </section>
      <section class="finance-section">
        <span class="finance-section-title">可用余额</span>
        <p>{{ formatFinanceAmount(record.availableAmount) }}</p>
        <el-button data-test="start-advance-allocation" type="primary" :disabled="!canAllocate" @click="openSettlementWorkbench">发起核销</el-button>
      </section>
      <section class="finance-section">
        <span class="finance-section-title">已核销目标</span>
        <p v-if="!record.allocations?.length">暂无核销目标</p>
        <p v-for="item in record.allocations" :key="`${item.targetType}-${item.targetNo}`">{{ item.targetNo }} {{ formatFinanceAmount(item.amount) }}</p>
      </section>
      <section class="finance-section">
        <span class="finance-section-title">未核销余额</span>
        <p>{{ formatFinanceAmount(record.availableAmount) }}</p>
      </section>
      <section class="finance-section">
        <span class="finance-section-title">凭证草稿</span>
        <p v-if="!record.voucherDrafts?.length">暂无凭证草稿</p>
        <p v-for="draft in record.voucherDrafts" :key="draft.draftNo">{{ draft.draftNo }} {{ voucherDraftStatusText(draft.status) }}</p>
      </section>
      <section class="finance-section">
        <span class="finance-section-title">审计</span>
        <p>{{ record.auditSummary?.length ? '已有审计记录' : '暂无审计摘要' }}</p>
      </section>
    </div>
  </MasterDataTableView>
</template>
