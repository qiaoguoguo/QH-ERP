<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { financialCloseApi, type TaxPaymentRecord } from '../../shared/api/financialCloseApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import {
  bankSensitiveText,
  financialCloseErrorMessage,
  financialClosePageItems,
  financialClosePageSizes,
  financialClosePageTotal,
  formatFinancialCloseAmount,
  sourceVisibleText,
} from './financialClosePageHelpers'
import './FinancialCloseShared.css'

const filters = reactive({ periodCode: '2026-07' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<TaxPaymentRecord[]>([])
const loading = ref(false)
const error = ref('')

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await financialCloseApi.taxPayments.list({
      periodCode: filters.periodCode,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = financialClosePageItems(page)
    pagination.total = financialClosePageTotal(page)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = financialCloseErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="税款缴纳台账" description="只追溯已记账凭证或合法付款，登记税款缴纳事实，不重复创建资金支付动作。">
    <template #actions>
      <el-button @click="loadRecords">刷新</el-button>
      <el-button type="primary">登记缴纳</el-button>
    </template>
    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="会计期间"><el-input v-model="filters.periodCode" clearable placeholder="2026-07" /></el-form-item>
        <el-form-item><el-button type="primary" @click="loadRecords">查询</el-button></el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert type="info" title="税款缴纳台账不作为本期关闭前置；只登记可追溯的已记账凭证或合法付款。" :closable="false" />
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无税款缴纳记录'" stripe>
        <el-table-column prop="periodCode" label="期间" min-width="110" />
        <el-table-column prop="taxType" label="税种" min-width="120" />
        <el-table-column prop="paymentDate" label="缴纳日期" min-width="120" />
        <el-table-column label="金额" min-width="130" align="right"><template #default="{ row }">{{ formatFinancialCloseAmount(row.amount, row.amountVisible === false ? row.restrictedReason : null) }}</template></el-table-column>
        <el-table-column prop="voucherNo" label="凭证" min-width="160" />
        <el-table-column prop="paymentSourceType" label="来源" min-width="130" />
        <el-table-column prop="bankAccountMasked" label="账号" min-width="130" />
        <el-table-column label="权限" min-width="180">
          <template #default="{ row }">{{ sourceVisibleText(row.sourceVisible) }} / {{ bankSensitiveText(row.bankSensitiveVisible) }}</template>
        </el-table-column>
      </el-table>
    </div>
    <el-empty v-if="!loading && !records.length" description="暂无税款缴纳记录" />
    <el-pagination
      class="table-pagination"
      layout="total, sizes, prev, pager, next"
      :page-sizes="financialClosePageSizes"
      :total="pagination.total"
      :page-size="pagination.pageSize"
      :current-page="pagination.page"
    />
  </MasterDataTableView>
</template>
