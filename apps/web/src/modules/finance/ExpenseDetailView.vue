<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { financeExpenseApi, type ExpenseRecord } from '../../shared/api/financeExpenseApi'
import { useAuthStore } from '../../stores/authStore'
import { confirmAction } from '../../shared/ui/confirmDialog'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { financeErrorMessage, financePermissions, financeSourceTypeText, formatFinanceAmount, formatFinanceDate, invoiceStatusText, ownershipTypeText, settlementStatusText, voucherDraftStatusText } from './financePageHelpers'
import FinanceSourceTracePanel from './FinanceSourceTracePanel.vue'
import './Finance028Shared.css'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<ExpenseRecord | null>(null)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const canConfirm = computed(() => record.value?.allowedActions?.includes('CONFIRM') && authStore.hasPermission(financePermissions.expenseConfirm))
const canCancel = computed(() => record.value?.allowedActions?.includes('CANCEL') && authStore.hasPermission(financePermissions.expenseCancel))

function firstDisplayText(...values: Array<string | null | undefined>) {
  return values.find((value) => Boolean(value?.trim())) ?? ''
}

function expenseSupplierName(expense: ExpenseRecord) {
  return firstDisplayText(expense.supplierName, expense.partyName, expense.partnerName) || '供应商待补全'
}

function expenseCategoryName(expense: ExpenseRecord) {
  return firstDisplayText(expense.categoryName, expense.categorySummary, expense.lines?.[0]?.categoryName) || '费用分类待补全'
}

function expenseSourceSnapshot(expense: ExpenseRecord) {
  const sourceSummary = firstDisplayText(expense.sourceSummary)
  if (sourceSummary) {
    return sourceSummary
  }
  const source = expense.sources?.[0]
  if (source) {
    if (source.restricted) {
      return source.restrictedReason ?? '来源权限受限'
    }
    return firstDisplayText(source.sourceSummary, source.summary, `${financeSourceTypeText(source.sourceType)} ${source.sourceNo}`)
  }
  return `${financeSourceTypeText(expense.sourceType ?? 'NONE')} ${expense.sourceNo ?? '无来源普通供应商费用'}`
}

async function loadRecord() {
  try {
    record.value = await financeExpenseApi.expenses.get(route.params.id as string)
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  }
}

async function runAction(action: 'confirm' | 'cancel') {
  if (!record.value || actionLoading.value) {
    return
  }
  const label = action === 'confirm' ? '确认' : '取消'
  if (!(await confirmAction(`${label}费用单“${record.value.expenseNo}”？`))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    const payload = {
      version: record.value.version,
      idempotencyKey: `${action}-expense-${record.value.id}-${Date.now()}`,
    }
    if (action === 'confirm') {
      await financeExpenseApi.expenses.confirm(record.value.id, payload)
    } else {
      await financeExpenseApi.expenses.cancel(record.value.id, payload)
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
  <MasterDataTableView title="费用单详情" description="查看费用行、来源快照、应付链接、付款核销和非正式成本提示。">
    <template #actions>
      <el-button @click="router.push({ name: 'finance-expenses' })">返回列表</el-button>
      <el-button v-if="canConfirm" data-test="confirm-expense" type="success" :loading="actionLoading" :disabled="actionLoading" @click="runAction('confirm')">确认</el-button>
      <el-button v-if="canCancel" data-test="cancel-expense" type="danger" :loading="actionLoading" :disabled="actionLoading" @click="runAction('cancel')">取消</el-button>
    </template>
    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
    </template>
    <div v-if="record" class="finance-summary-strip">
      <div><span>费用状态</span><strong>{{ invoiceStatusText(record.status) }}</strong></div>
      <div><span>供应商</span><strong>{{ expenseSupplierName(record) }}</strong></div>
      <div><span>项目/公共</span><strong>{{ ownershipTypeText(record.ownershipType) }} {{ record.projectName ?? '' }}</strong></div>
      <div><span>费用分类</span><strong>{{ expenseCategoryName(record) }}</strong></div>
      <div><span>业务日期</span><strong>{{ formatFinanceDate(record.businessDate) }}</strong></div>
      <div><span>价税合计</span><strong>{{ formatFinanceAmount(record.totalAmount) }}</strong></div>
      <div><span>结算状态</span><strong>{{ settlementStatusText(record.settlementStatus) }}</strong></div>
    </div>
    <div v-if="record" class="finance-section-grid">
      <section class="finance-section"><span class="finance-section-title">费用行</span><p v-for="line in record.lines" :key="line.categoryName">{{ line.categoryName }} {{ formatFinanceAmount(line.totalAmount) }}</p></section>
      <section class="finance-section"><span class="finance-section-title">来源快照</span><p>{{ expenseSourceSnapshot(record) }}</p></section>
      <section class="finance-section"><span class="finance-section-title">应付链接</span><p v-for="link in record.payableLinks" :key="link.payableNo">{{ link.payableNo }} {{ formatFinanceAmount(link.amount) }}</p></section>
      <section class="finance-section"><span class="finance-section-title">付款/预付核销</span><p v-if="!record.settlements?.length">暂无核销记录</p><p v-for="item in record.settlements" :key="item.documentNo">{{ item.documentNo }} {{ formatFinanceAmount(item.amount) }}</p></section>
      <section class="finance-section"><span class="finance-section-title">非正式成本提示</span><p>028 不写库存价值、工单成本或项目利润。</p></section>
      <section class="finance-section"><span class="finance-section-title">凭证草稿</span><p v-if="!record.voucherDrafts?.length">暂无凭证草稿</p><p v-for="draft in record.voucherDrafts" :key="draft.draftNo">{{ draft.draftNo }} {{ voucherDraftStatusText(draft.status) }}</p></section>
    </div>
    <FinanceSourceTracePanel v-if="record" :sources="record.sources ?? []" />
  </MasterDataTableView>
</template>
