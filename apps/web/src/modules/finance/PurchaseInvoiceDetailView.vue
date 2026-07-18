<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { financeInvoiceApi, type PurchaseInvoiceRecord } from '../../shared/api/financeInvoiceApi'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { financeErrorMessage, financePermissions, financeSourceTypeText, formatFinanceAmount, invoiceStatusText, matchStatusText, settlementStatusText, voucherDraftStatusText } from './financePageHelpers'
import { confirmAction } from '../../shared/ui/confirmDialog'
import FinanceSourceTracePanel from './FinanceSourceTracePanel.vue'
import './Finance028Shared.css'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<PurchaseInvoiceRecord | null>(null)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const confirmDisabledReason = computed(() => record.value?.matchStatus === 'EXCEPTION' ? '存在三单差异，零容差规则禁止确认' : '')
const canMatch = computed(() => Boolean(record.value?.allowedActions?.includes('MATCH')) && authStore.hasPermission(financePermissions.purchaseInvoiceMatch))
const canConfirm = computed(() => Boolean(record.value?.allowedActions?.includes('CONFIRM')) && authStore.hasPermission(financePermissions.purchaseInvoiceConfirm))
const canCancel = computed(() => Boolean(record.value?.allowedActions?.includes('CANCEL')) && authStore.hasPermission(financePermissions.purchaseInvoiceCancel))

function partyName(record: PurchaseInvoiceRecord) {
  return record.partyName || record.partnerName || record.supplierName || '-'
}

function payableLinkText(link: NonNullable<PurchaseInvoiceRecord['payableLinks']>[number]) {
  const parts = [link.payableNo]
  const totalAmount = link.totalAmount ?? link.amount
  if (totalAmount !== undefined && totalAmount !== null && totalAmount !== '') {
    parts.push(`总额 ${formatFinanceAmount(totalAmount)}`)
  }
  if (link.unpaidAmount !== undefined && link.unpaidAmount !== null && link.unpaidAmount !== '') {
    parts.push(`未付 ${formatFinanceAmount(link.unpaidAmount)}`)
  }
  return parts.join(' ')
}

async function loadRecord() {
  try {
    record.value = await financeInvoiceApi.purchaseInvoices.get(route.params.id as string)
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  }
}

async function runMatch() {
  if (!record.value || actionLoading.value) {
    return
  }
  if (!(await confirmAction(`执行采购发票“${record.value.invoiceNo}”三单匹配？`))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await financeInvoiceApi.purchaseInvoices.match(record.value.id, {
      version: record.value.version,
      idempotencyKey: `match-purchase-invoice-${record.value.id}-${Date.now()}`,
    })
    await loadRecord()
  } catch (caught) {
    actionError.value = financeErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function runAction(action: 'confirm' | 'cancel') {
  if (!record.value || actionLoading.value) {
    return
  }
  if (action === 'confirm' && confirmDisabledReason.value) {
    return
  }
  const label = action === 'confirm' ? '确认' : '取消'
  if (!(await confirmAction(`${label}采购发票“${record.value.invoiceNo}”？`))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    const payload = {
      version: record.value.version,
      idempotencyKey: `${action}-purchase-invoice-${record.value.id}-${Date.now()}`,
    }
    if (action === 'confirm') {
      await financeInvoiceApi.purchaseInvoices.confirm(record.value.id, payload)
    } else {
      await financeInvoiceApi.purchaseInvoices.cancel(record.value.id, payload)
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
  <MasterDataTableView title="采购发票详情" description="查看采购发票、匹配摘要、应付链接、付款核销和凭证草稿。">
    <template #actions>
      <el-button @click="router.push({ name: 'finance-purchase-invoices' })">返回列表</el-button>
      <el-button v-if="canMatch" data-test="match-purchase-invoice" :loading="actionLoading" :disabled="actionLoading" @click="runMatch">执行匹配</el-button>
      <el-button v-if="canConfirm" data-test="confirm-purchase-invoice" type="success" :loading="actionLoading" :disabled="actionLoading || Boolean(confirmDisabledReason)" @click="runAction('confirm')">确认</el-button>
      <el-button v-if="canCancel" data-test="cancel-purchase-invoice" type="danger" :loading="actionLoading" :disabled="actionLoading" @click="runAction('cancel')">取消</el-button>
    </template>
    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="confirmDisabledReason" type="warning" :title="confirmDisabledReason" :closable="false" />
    </template>
    <div v-if="record" class="finance-summary-strip">
      <div><span>发票状态</span><strong>{{ invoiceStatusText(record.status) }}</strong></div>
      <div><span>匹配状态</span><strong>{{ matchStatusText(record.matchStatus) }}</strong></div>
      <div><span>结算状态</span><strong>{{ settlementStatusText(record.settlementStatus) }}</strong></div>
      <div><span>往来方</span><strong>{{ partyName(record) }}</strong></div>
      <div><span>来源类型</span><strong>{{ financeSourceTypeText(record.sourceType) }}</strong></div>
      <div><span>含税金额</span><strong>{{ formatFinanceAmount(record.totalAmount) }}</strong></div>
      <div><span>未结余额</span><strong>{{ formatFinanceAmount(record.unsettledAmount) }}</strong></div>
    </div>
    <div v-if="record" class="finance-section-grid">
      <section class="finance-section"><span class="finance-section-title">匹配摘要</span><p>{{ matchStatusText(record.matchStatus) }}，差异数 {{ record.differenceCount ?? 0 }}</p></section>
      <section class="finance-section"><span class="finance-section-title">三单明细</span><p v-for="diff in record.matching?.differences" :key="diff.message">{{ diff.message }}</p></section>
      <section class="finance-section">
        <span class="finance-section-title">应付链接</span>
        <p v-if="!record.payableLinks?.length">暂无应付链接</p>
        <p v-for="link in record.payableLinks" :key="link.payableNo">{{ payableLinkText(link) }}</p>
      </section>
      <section class="finance-section"><span class="finance-section-title">付款/预付核销</span><p v-for="item in record.settlements" :key="item.documentNo">{{ item.documentNo }} {{ formatFinanceAmount(item.amount) }}</p></section>
      <section class="finance-section"><span class="finance-section-title">凭证草稿</span><p v-if="!record.voucherDrafts?.length">暂无凭证草稿</p><p v-for="draft in record.voucherDrafts" :key="draft.draftNo">{{ draft.draftNo }} {{ voucherDraftStatusText(draft.status) }}</p></section>
    </div>
    <FinanceSourceTracePanel v-if="record" :sources="record.sources ?? []" />
  </MasterDataTableView>
</template>
