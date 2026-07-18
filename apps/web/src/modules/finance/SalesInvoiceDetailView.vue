<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { financeInvoiceApi, type SalesInvoiceRecord } from '../../shared/api/financeInvoiceApi'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { financeErrorMessage, financePermissions, formatFinanceAmount, invoiceStatusText, settlementStatusText, voucherDraftStatusText } from './financePageHelpers'
import { confirmAction } from '../../shared/ui/confirmDialog'
import './Finance028Shared.css'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<SalesInvoiceRecord | null>(null)
const error = ref('')
const actionError = ref('')
const loading = ref(false)
const actionLoading = ref(false)
const canConfirm = computed(() => Boolean(record.value?.allowedActions?.includes('CONFIRM')) && authStore.hasPermission(financePermissions.salesInvoiceConfirm))

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await financeInvoiceApi.salesInvoices.get(route.params.id as string)
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function confirmInvoice() {
  if (!record.value || actionLoading.value) {
    return
  }
  if (!(await confirmAction(`确认销售发票“${record.value.invoiceNo}”？`))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await financeInvoiceApi.salesInvoices.confirm(record.value.id, {
      version: record.value.version,
      idempotencyKey: `confirm-sales-invoice-${record.value.id}-${Date.now()}`,
    })
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
  <MasterDataTableView title="销售发票详情" description="查看销售发票、应收链接、收款核销、来源追溯和凭证草稿。">
    <template #actions>
      <el-button @click="router.push({ name: 'finance-sales-invoices' })">返回列表</el-button>
      <el-button v-if="canConfirm" data-test="confirm-sales-invoice" type="success" :loading="actionLoading" :disabled="actionLoading" @click="confirmInvoice">确认</el-button>
    </template>
    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" type="info" title="销售发票详情加载中" :closable="false" />
    </template>

    <div v-if="record" class="finance-summary-strip">
      <div><span>发票状态</span><strong>{{ invoiceStatusText(record.status) }}</strong></div>
      <div><span>结算状态</span><strong>{{ settlementStatusText(record.settlementStatus) }}</strong></div>
      <div><span>客户</span><strong>{{ record.customerName }}</strong></div>
      <div><span>价税合计</span><strong>{{ formatFinanceAmount(record.totalAmount) }}</strong></div>
      <div><span>未结余额</span><strong>{{ formatFinanceAmount(record.unsettledAmount) }}</strong></div>
      <div><span>只读原因</span><strong>确认后不可普通编辑</strong></div>
    </div>
    <div v-if="record" class="finance-section-grid">
      <section class="finance-section"><span class="finance-section-title">来源摘要</span><p v-for="source in record.sources" :key="source.sourceNo">{{ source.restricted ? '无权查看来源详情' : source.summary ?? source.sourceNo }}</p></section>
      <section class="finance-section"><span class="finance-section-title">应收链接</span><p v-for="link in record.receivableLinks" :key="link.receivableNo">{{ link.receivableNo }} {{ formatFinanceAmount(link.amount) }}</p></section>
      <section class="finance-section"><span class="finance-section-title">收款/预收核销</span><p v-for="item in record.settlements" :key="item.documentNo">{{ item.documentNo }} {{ formatFinanceAmount(item.amount) }}</p></section>
      <section class="finance-section"><span class="finance-section-title">凭证草稿</span><p v-for="draft in record.voucherDrafts" :key="draft.draftNo">{{ draft.draftNo }} {{ voucherDraftStatusText(draft.status) }}</p></section>
    </div>
  </MasterDataTableView>
</template>
