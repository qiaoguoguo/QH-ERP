<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { financeInvoiceApi, type PurchaseInvoiceMatchingResult } from '../../shared/api/financeInvoiceApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { financeErrorMessage, formatFinanceAmount, matchStatusText } from './financePageHelpers'
import './Finance028Shared.css'

const route = useRoute()
const router = useRouter()
const matching = ref<PurchaseInvoiceMatchingResult | null>(null)
const error = ref('')

async function loadMatching() {
  try {
    matching.value = await financeInvoiceApi.purchaseInvoiceMatching.get(route.params.id as string)
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  }
}

function rowValue(row: Record<string, unknown> | null | undefined, key: string) {
  const value = row?.[key]
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return String(value)
}

function rowAmount(row: Record<string, unknown> | null | undefined, key: string) {
  return formatFinanceAmount(rowValue(row, key))
}

function rowDifferenceText(row: { differences?: Array<{ message: string }> }) {
  return row.differences?.map((item) => item.message).join('；') || '无差异'
}

onMounted(loadMatching)
</script>

<template>
  <MasterDataTableView title="三单匹配工作台" description="逐行对齐订单事实、入库事实和发票事实，零容差差异阻断确认。">
    <template #actions><el-button @click="router.push({ name: 'finance-purchase-invoice-detail', params: { id: route.params.id } })">返回发票</el-button></template>
    <template #alerts><el-alert v-if="error" type="error" :title="error" :closable="false" /></template>
    <div class="finance-summary-strip">
      <div><span>订单事实</span><strong>供应商、项目、数量和价格</strong></div>
      <div><span>入库事实</span><strong>已过账入库数量和税价快照</strong></div>
      <div><span>发票事实</span><strong>本次发票数量、税率和价税</strong></div>
      <div><span>差异结果</span><strong>{{ matchStatusText(matching?.status) }}</strong></div>
    </div>
    <section class="finance-section">
      <span class="finance-section-title">逐行三单事实</span>
      <div class="table-scroll">
        <el-table :data="matching?.rows ?? []" empty-text="暂无逐行匹配事实" stripe>
          <el-table-column prop="lineNo" label="行号" min-width="80" />
          <el-table-column label="物料" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
          <el-table-column label="订单数量" min-width="110" align="right"><template #default="{ row }">{{ rowValue(row.order, 'quantity') }}</template></el-table-column>
          <el-table-column label="订单税率" min-width="110" align="right"><template #default="{ row }">{{ rowValue(row.order, 'taxRate') }}</template></el-table-column>
          <el-table-column label="订单含税金额" min-width="130" align="right"><template #default="{ row }">{{ rowAmount(row.order, 'totalAmount') }}</template></el-table-column>
          <el-table-column label="入库数量" min-width="110" align="right"><template #default="{ row }">{{ rowValue(row.receipt, 'quantity') }}</template></el-table-column>
          <el-table-column label="入库税率" min-width="110" align="right"><template #default="{ row }">{{ rowValue(row.receipt, 'taxRate') }}</template></el-table-column>
          <el-table-column label="入库含税金额" min-width="130" align="right"><template #default="{ row }">{{ rowAmount(row.receipt, 'totalAmount') }}</template></el-table-column>
          <el-table-column label="发票数量" min-width="110" align="right"><template #default="{ row }">{{ rowValue(row.invoice, 'quantity') }}</template></el-table-column>
          <el-table-column label="发票税率" min-width="110" align="right"><template #default="{ row }">{{ rowValue(row.invoice, 'taxRate') }}</template></el-table-column>
          <el-table-column label="发票含税金额" min-width="130" align="right"><template #default="{ row }">{{ rowAmount(row.invoice, 'totalAmount') }}</template></el-table-column>
          <el-table-column label="结构化差异" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ rowDifferenceText(row) }}</template></el-table-column>
        </el-table>
      </div>
    </section>
    <div class="table-scroll">
      <el-table :data="matching?.differences ?? []" empty-text="无三单差异">
        <el-table-column prop="message" label="差异类型" min-width="180" />
        <el-table-column prop="orderValue" label="订单值" min-width="140" />
        <el-table-column prop="receiptValue" label="入库值" min-width="140" />
        <el-table-column prop="invoiceValue" label="发票值" min-width="140" />
        <el-table-column label="阻断动作" min-width="180"><template #default>存在差异时禁止确认</template></el-table-column>
      </el-table>
    </div>
  </MasterDataTableView>
</template>
