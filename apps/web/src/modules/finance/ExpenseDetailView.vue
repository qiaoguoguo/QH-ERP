<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { financeExpenseApi, type ExpenseRecord } from '../../shared/api/financeExpenseApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { financeErrorMessage, financeSourceTypeText, formatFinanceAmount, invoiceStatusText, ownershipTypeText, settlementStatusText, voucherDraftStatusText } from './financePageHelpers'
import './Finance028Shared.css'

const route = useRoute()
const router = useRouter()
const record = ref<ExpenseRecord | null>(null)
const error = ref('')

async function loadRecord() {
  try {
    record.value = await financeExpenseApi.expenses.get(route.params.id as string)
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  }
}

onMounted(loadRecord)
</script>

<template>
  <MasterDataTableView title="费用单详情" description="查看费用行、来源快照、应付链接、付款核销和非正式成本提示。">
    <template #actions><el-button @click="router.push({ name: 'finance-expenses' })">返回列表</el-button></template>
    <template #alerts><el-alert v-if="error" type="error" :title="error" :closable="false" /></template>
    <div v-if="record" class="finance-summary-strip">
      <div><span>费用状态</span><strong>{{ invoiceStatusText(record.status) }}</strong></div>
      <div><span>供应商</span><strong>{{ record.supplierName }}</strong></div>
      <div><span>项目/公共</span><strong>{{ ownershipTypeText(record.ownershipType) }} {{ record.projectName ?? '' }}</strong></div>
      <div><span>费用分类</span><strong>{{ record.categoryName }}</strong></div>
      <div><span>业务日期</span><strong>{{ record.businessDate }}</strong></div>
      <div><span>价税合计</span><strong>{{ formatFinanceAmount(record.totalAmount) }}</strong></div>
      <div><span>结算状态</span><strong>{{ settlementStatusText(record.settlementStatus) }}</strong></div>
    </div>
    <div v-if="record" class="finance-section-grid">
      <section class="finance-section"><span class="finance-section-title">费用行</span><p v-for="line in record.lines" :key="line.categoryName">{{ line.categoryName }} {{ formatFinanceAmount(line.totalAmount) }}</p></section>
      <section class="finance-section"><span class="finance-section-title">来源快照</span><p>{{ financeSourceTypeText(record.sourceType ?? 'NONE') }} {{ record.sourceNo ?? '无来源普通供应商费用' }}</p></section>
      <section class="finance-section"><span class="finance-section-title">应付链接</span><p v-for="link in record.payableLinks" :key="link.payableNo">{{ link.payableNo }} {{ formatFinanceAmount(link.amount) }}</p></section>
      <section class="finance-section"><span class="finance-section-title">付款/预付核销</span><p v-if="!record.settlements?.length">暂无核销记录</p><p v-for="item in record.settlements" :key="item.documentNo">{{ item.documentNo }} {{ formatFinanceAmount(item.amount) }}</p></section>
      <section class="finance-section"><span class="finance-section-title">非正式成本提示</span><p>028 不写库存价值、工单成本或项目利润。</p></section>
      <section class="finance-section"><span class="finance-section-title">凭证草稿</span><p v-if="!record.voucherDrafts?.length">暂无凭证草稿</p><p v-for="draft in record.voucherDrafts" :key="draft.draftNo">{{ draft.draftNo }} {{ voucherDraftStatusText(draft.status) }}</p></section>
    </div>
  </MasterDataTableView>
</template>
