<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { financialCloseApi, type TaxPaymentRecord } from '../../shared/api/financialCloseApi'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import {
  bankSensitiveText,
  createFinancialCloseIdempotencyKey,
  financialCloseErrorMessage,
  financialCloseActionState,
  financialClosePageItems,
  financialClosePageSizes,
  financialClosePageTotal,
  financialClosePermissions,
  formatFinancialCloseAmount,
  sourceVisibleText,
  sourceTypeText,
  taxTypeText,
} from './financialClosePageHelpers'
import './FinancialCloseShared.css'

const authStore = useAuthStore()
const filters = reactive({ periodCode: '2026-07' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<TaxPaymentRecord[]>([])
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')
const paymentDialogVisible = ref(false)
const paymentForm = reactive({
  summaryId: '501',
  taxType: 'VAT',
  paymentDate: '2026-07-25',
  amount: '380.00',
  paymentMethod: 'BANK',
  referenceNo: 'TAX-PAY-001',
  voucherId: '94',
  bankAccountId: '101',
  reason: '登记税款缴纳',
})

const canManagePayments = computed(() => authStore.hasPermission(financialClosePermissions.taxPaymentManage))

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

function openPaymentCreate() {
  if (!canManagePayments.value) {
    actionError.value = '无税款缴纳维护权限'
    return
  }
  paymentDialogVisible.value = true
}

async function saveTaxPayment() {
  if (!canManagePayments.value) {
    actionError.value = '无税款缴纳维护权限'
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await financialCloseApi.taxPayments.create({
      summaryId: Number(paymentForm.summaryId),
      taxType: paymentForm.taxType,
      paymentDate: paymentForm.paymentDate,
      amount: paymentForm.amount,
      paymentMethod: paymentForm.paymentMethod,
      referenceNo: paymentForm.referenceNo,
      voucherId: Number(paymentForm.voucherId),
      bankAccountId: Number(paymentForm.bankAccountId),
      reason: paymentForm.reason,
      idempotencyKey: createFinancialCloseIdempotencyKey('tax-payment-create'),
    })
    paymentDialogVisible.value = false
    await loadRecords()
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function correctTaxPayment(record: TaxPaymentRecord) {
  const state = financialCloseActionState(record, 'CORRECT', canManagePayments.value, '无税款缴纳更正权限')
  if (!state.allowed) {
    actionError.value = state.reason
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await financialCloseApi.taxPayments.correct(record.id, {
      amount: '390.00',
      reason: '更正税款缴纳',
      idempotencyKey: createFinancialCloseIdempotencyKey('tax-payment-correct'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function bankAccountText(record: TaxPaymentRecord) {
  return record.bankAccountDisplay || record.bankAccountMasked || (record.bankSensitiveVisible === false ? '账号已脱敏' : '-')
}

function changePagination(page: number, pageSize: number) {
  pagination.page = page
  pagination.pageSize = pageSize
  void loadRecords()
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="税款缴纳台账" description="只追溯已记账凭证或合法付款，登记税款缴纳事实，不重复创建资金支付动作。">
    <template #actions>
      <el-button @click="loadRecords">刷新</el-button>
      <el-button data-test="open-tax-payment-create" type="primary" :disabled="!canManagePayments" @click="openPaymentCreate">登记缴纳</el-button>
    </template>
    <template #filters>
      <el-form class="query-form">
        <el-form-item label="会计期间"><el-input v-model="filters.periodCode" clearable placeholder="2026-07" /></el-form-item>
        <el-form-item><el-button type="primary" @click="loadRecords">查询</el-button></el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert type="info" title="税款缴纳台账不作为本期关闭前置；只登记可追溯的已记账凭证或合法付款。" :closable="false" />
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无税款缴纳记录'" stripe>
        <el-table-column prop="periodCode" label="期间" min-width="110" />
        <el-table-column label="税种" min-width="120"><template #default="{ row }">{{ taxTypeText(row.taxType) }}</template></el-table-column>
        <el-table-column prop="paymentDate" label="缴纳日期" min-width="120" />
        <el-table-column label="金额" min-width="130" align="right"><template #default="{ row }">{{ formatFinancialCloseAmount(row.amount, row.amountVisible === false ? row.restrictedReason : null) }}</template></el-table-column>
        <el-table-column prop="voucherNo" label="凭证" min-width="160" />
        <el-table-column label="来源" min-width="130"><template #default="{ row }">{{ sourceTypeText(row.paymentSourceType) }}</template></el-table-column>
        <el-table-column label="银行脱敏标识" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ bankAccountText(row) }}</template></el-table-column>
        <el-table-column label="权限" min-width="180">
          <template #default="{ row }">{{ sourceVisibleText(row.sourceVisible) }} / {{ bankSensitiveText(row.bankSensitiveVisible) }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ row }">
            <el-button data-test="correct-tax-payment" text :disabled="!financialCloseActionState(row, 'CORRECT', canManagePayments, '无税款缴纳更正权限').allowed" :loading="actionLoading" @click="correctTaxPayment(row)">更正</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-empty v-if="!loading && !records.length" description="暂无税款缴纳记录" />
    <el-pagination
      class="table-pagination"
      layout="total, sizes, prev, pager, next"
      :page-sizes="financialClosePageSizes"
      :total="pagination.total"
      v-model:page-size="pagination.pageSize"
      v-model:current-page="pagination.page"
      @change="changePagination"
    />
    <el-dialog v-model="paymentDialogVisible" title="登记税款缴纳" width="min(640px, 92vw)">
      <el-form label-position="top">
        <el-form-item label="税额汇总 ID"><el-input v-model="paymentForm.summaryId" name="tax-payment-summary-id" /></el-form-item>
        <el-form-item label="税种"><el-input v-model="paymentForm.taxType" name="tax-payment-tax-type" /></el-form-item>
        <el-form-item label="缴纳日期">
          <el-date-picker
            v-model="paymentForm.paymentDate"
            name="tax-payment-date"
            type="date"
            value-format="YYYY-MM-DD"
            value-on-clear=""
          />
        </el-form-item>
        <el-form-item label="金额"><el-input v-model="paymentForm.amount" name="tax-payment-amount" /></el-form-item>
        <el-form-item label="方式"><el-input v-model="paymentForm.paymentMethod" name="tax-payment-method" /></el-form-item>
        <el-form-item label="参考号"><el-input v-model="paymentForm.referenceNo" name="tax-payment-reference-no" /></el-form-item>
        <el-form-item label="凭证 ID"><el-input v-model="paymentForm.voucherId" name="tax-payment-voucher-id" /></el-form-item>
        <el-form-item label="银行账户 ID"><el-input v-model="paymentForm.bankAccountId" name="tax-payment-bank-account-id" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="paymentForm.reason" name="tax-payment-reason" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="paymentDialogVisible = false">取消</el-button>
        <el-button data-test="save-tax-payment" type="primary" :loading="actionLoading" @click="saveTaxPayment">保存</el-button>
      </template>
    </el-dialog>
  </MasterDataTableView>
</template>
