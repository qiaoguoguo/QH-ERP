<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { financialCloseApi, type TaxSummaryRecord } from '../../shared/api/financialCloseApi'
import { confirmAction } from '../../shared/ui/confirmDialog'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import {
  createFinancialCloseIdempotencyKey,
  financialCloseActionState,
  financialCloseErrorMessage,
  financialClosePageItems,
  financialClosePageSizes,
  financialClosePageTotal,
  financialClosePermissions,
  financialCloseStatusText,
  formatFinancialCloseAmount,
  sourceVisibleText,
  taxFoundationDisclaimer,
  taxTypeText,
} from './financialClosePageHelpers'
import './FinancialCloseShared.css'

const authStore = useAuthStore()
const filters = reactive({ periodCode: '2026-07' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<TaxSummaryRecord[]>([])
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')
const taxType = ref('VAT')
const adjustmentForm = reactive({
  adjustmentType: 'VAT_INCREASE',
  amount: '1.00',
  reason: '调整税额',
})

const canCalculate = computed(() => authStore.hasPermission(financialClosePermissions.taxSummaryCalculate))
const canConfirm = computed(() => authStore.hasPermission(financialClosePermissions.taxSummaryConfirm))
const canGenerateVoucher = computed(() => authStore.hasPermission(financialClosePermissions.taxSummaryGenerateVoucher))

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await financialCloseApi.taxSummaries.list({
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

async function calculateSummary(record: TaxSummaryRecord) {
  const state = taxSummaryAction(record, 'CALCULATE', canCalculate.value, '无税额计算权限')
  if (!state.allowed) {
    actionError.value = state.reason
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await financialCloseApi.taxSummaries.calculate(record.id, {
      version: record.version,
      idempotencyKey: createFinancialCloseIdempotencyKey('tax-summary-calculate'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function confirmSummary(record: TaxSummaryRecord) {
  const state = taxSummaryAction(record, 'CONFIRM', canConfirm.value, '无税额确认权限')
  if (!state.allowed) {
    actionError.value = state.reason
    return
  }
  if (!(await confirmAction('确认税额基础汇总？确认后该版本不可修改。', { title: '确认税额基础汇总', risk: 'danger' }))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await financialCloseApi.taxSummaries.confirm(record.id, {
      version: record.version,
      reason: '确认税额基础汇总',
      idempotencyKey: createFinancialCloseIdempotencyKey('tax-summary-confirm'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function createVoucherDraft(record: TaxSummaryRecord) {
  const state = taxSummaryAction(record, 'GENERATE_VOUCHER', canGenerateVoucher.value, '无生成税务凭证权限')
  if (!state.allowed) {
    actionError.value = state.reason
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await financialCloseApi.taxSummaries.createVoucherDraft(record.id, {
      version: record.version,
      reason: '生成税务凭证草稿',
      idempotencyKey: createFinancialCloseIdempotencyKey('tax-voucher-draft'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function createTaxSummary() {
  if (!canCalculate.value) {
    actionError.value = '无税额汇总创建权限'
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await financialCloseApi.taxSummaries.create({
      periodCode: filters.periodCode,
      taxType: taxType.value,
      idempotencyKey: createFinancialCloseIdempotencyKey('tax-summary-create'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function addAdjustment(record: TaxSummaryRecord) {
  const state = taxSummaryAction(record, 'ADJUST', canCalculate.value, '无税额调整权限')
  if (!state.allowed) {
    actionError.value = state.reason
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await financialCloseApi.taxSummaries.addAdjustment(record.id, {
      version: record.version,
      adjustmentType: adjustmentForm.adjustmentType,
      amount: adjustmentForm.amount,
      reason: adjustmentForm.reason,
      idempotencyKey: createFinancialCloseIdempotencyKey('tax-summary-adjust'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function taxSummaryAction(record: TaxSummaryRecord, action: string, permission: boolean, noPermissionReason: string) {
  return financialCloseActionState(record, action, permission, noPermissionReason)
}

function taxSummaryActionReasons(record: TaxSummaryRecord) {
  return [
    taxSummaryAction(record, 'CALCULATE', canCalculate.value, '无税额计算权限').reason,
    taxSummaryAction(record, 'ADJUST', canCalculate.value, '无税额调整权限').reason,
    taxSummaryAction(record, 'CONFIRM', canConfirm.value, '无税额确认权限').reason,
    taxSummaryAction(record, 'GENERATE_VOUCHER', canGenerateVoucher.value, '无生成税务凭证权限').reason,
  ].filter(Boolean).filter((reason, index, list) => list.indexOf(reason) === index).join('；')
}

function changePagination(page: number, pageSize: number) {
  pagination.page = page
  pagination.pageSize = pageSize
  void loadRecords()
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="税额汇总" description="汇总增值税、附加税费建议和企业所得税估算；固定标注基础汇总/估算，非正式申报。">
    <template #actions>
      <el-button @click="loadRecords">刷新</el-button>
      <el-button data-test="create-tax-summary" type="primary" :disabled="!canCalculate" :loading="actionLoading" @click="createTaxSummary">创建汇总</el-button>
    </template>
    <template #filters>
      <el-form class="query-form">
        <el-form-item label="会计期间"><el-input v-model="filters.periodCode" clearable placeholder="2026-07" /></el-form-item>
        <el-form-item label="税种"><el-input v-model="taxType" clearable placeholder="VAT" /></el-form-item>
        <el-form-item><el-button type="primary" @click="loadRecords">查询</el-button></el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert type="warning" :title="taxFoundationDisclaimer" :closable="false" />
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无税额汇总'" stripe>
        <el-table-column prop="periodCode" label="期间" min-width="110" />
        <el-table-column label="税种" min-width="110"><template #default="{ row }">{{ taxTypeText(row.taxType) }}</template></el-table-column>
        <el-table-column label="状态" min-width="110"><template #default="{ row }">{{ financialCloseStatusText(row.status) }}</template></el-table-column>
        <el-table-column label="销项税额" min-width="130" align="right"><template #default="{ row }">{{ formatFinancialCloseAmount(row.outputTaxAmount, row.amountVisible === false ? row.restrictedReason : null) }}</template></el-table-column>
        <el-table-column label="进项税额" min-width="130" align="right"><template #default="{ row }">{{ formatFinancialCloseAmount(row.inputTaxAmount, row.amountVisible === false ? row.restrictedReason : null) }}</template></el-table-column>
        <el-table-column label="应纳税额" min-width="130" align="right"><template #default="{ row }">{{ formatFinancialCloseAmount(row.payableTaxAmount, row.amountVisible === false ? row.restrictedReason : null) }}</template></el-table-column>
        <el-table-column label="所得税估算" min-width="130" align="right"><template #default="{ row }">{{ formatFinancialCloseAmount(row.estimatedIncomeTaxAmount, row.amountVisible === false ? row.restrictedReason : null) }}</template></el-table-column>
        <el-table-column label="来源" min-width="120"><template #default="{ row }">{{ sourceVisibleText(row.sourceVisible) }}</template></el-table-column>
        <el-table-column label="说明" min-width="210" show-overflow-tooltip><template #default="{ row }">{{ row.disclaimer || taxFoundationDisclaimer }}</template></el-table-column>
        <el-table-column label="操作" min-width="260">
          <template #default="{ row }">
            <div class="financial-close-table-actions">
              <el-button data-test="calculate-tax-summary" size="small" :disabled="!taxSummaryAction(row, 'CALCULATE', canCalculate, '无税额计算权限').allowed" @click="calculateSummary(row)">计算</el-button>
              <el-button data-test="add-tax-summary-adjustment" size="small" :disabled="!taxSummaryAction(row, 'ADJUST', canCalculate, '无税额调整权限').allowed" @click="addAdjustment(row)">调整</el-button>
              <el-button data-test="confirm-tax-summary" size="small" type="primary" plain :disabled="!taxSummaryAction(row, 'CONFIRM', canConfirm, '无税额确认权限').allowed" @click="confirmSummary(row)">确认</el-button>
              <el-button data-test="create-tax-voucher-draft" size="small" :disabled="!taxSummaryAction(row, 'GENERATE_VOUCHER', canGenerateVoucher, '无生成税务凭证权限').allowed" @click="createVoucherDraft(row)">生成凭证</el-button>
              <span class="financial-close-disabled-reason">
                {{ taxSummaryActionReasons(row) }}
              </span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-empty v-if="!loading && !records.length" description="暂无税额汇总" />
    <el-pagination
      class="table-pagination"
      layout="total, sizes, prev, pager, next"
      :page-sizes="financialClosePageSizes"
      :total="pagination.total"
      v-model:page-size="pagination.pageSize"
      v-model:current-page="pagination.page"
      @change="changePagination"
    />
  </MasterDataTableView>
</template>
