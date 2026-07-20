<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { financialCloseApi, type BankReconciliationRecord } from '../../shared/api/financialCloseApi'
import { confirmAction } from '../../shared/ui/confirmDialog'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import {
  createFinancialCloseIdempotencyKey,
  financialCloseErrorMessage,
  financialClosePageItems,
  financialClosePageSizes,
  financialClosePageTotal,
  financialCloseStatusText,
  formatFinancialCloseAmount,
} from './financialClosePageHelpers'
import './FinancialCloseShared.css'

const filters = reactive({ periodCode: '2026-07' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<BankReconciliationRecord[]>([])
const selected = ref<BankReconciliationRecord | null>(null)
const statementCandidates = ref<Array<Record<string, unknown>>>([])
const ledgerCandidates = ref<Array<Record<string, unknown>>>([])
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')

async function loadCandidates(record: BankReconciliationRecord) {
  const detail = await financialCloseApi.bankReconciliations.get(record.id)
  selected.value = detail
  const candidates = await financialCloseApi.bankReconciliations.candidates(record.id, {
    page: 1,
    pageSize: 20,
  }) as { statementLines?: Array<Record<string, unknown>>, ledgerEntries?: Array<Record<string, unknown>> }
  statementCandidates.value = candidates.statementLines ?? []
  ledgerCandidates.value = candidates.ledgerEntries ?? []
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await financialCloseApi.bankReconciliations.list({
      periodCode: filters.periodCode,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = financialClosePageItems(page)
    pagination.total = financialClosePageTotal(page)
    if (records.value[0]) {
      await loadCandidates(records.value[0])
    }
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = financialCloseErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function createMatch() {
  if (!selected.value) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await financialCloseApi.bankReconciliations.createMatch(selected.value.id, {
      statementLineIds: statementCandidates.value.slice(0, 1).map((item) => item.id),
      ledgerEntryIds: ledgerCandidates.value.slice(0, 1).map((item) => item.id),
      version: selected.value.version,
      idempotencyKey: createFinancialCloseIdempotencyKey('bank-reconciliation-match'),
    })
    await loadCandidates(selected.value)
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function confirmReconciliation() {
  if (!selected.value) {
    return
  }
  if (!(await confirmAction('确认银行对账版本？确认后该版本只读，不允许未解释差额。', { title: '确认银行对账', risk: 'danger' }))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    selected.value = await financialCloseApi.bankReconciliations.confirm(selected.value.id, {
      version: selected.value.version,
      reason: '确认对账',
      idempotencyKey: createFinancialCloseIdempotencyKey('bank-reconciliation-confirm'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="银行对账工作台" description="按会计期间进行银行流水与已记账银行科目分录多对多匹配，候选池不受主列表十条分页限制。">
    <template #actions>
      <el-button @click="loadRecords">刷新</el-button>
      <el-button data-test="create-bank-match" :loading="actionLoading" @click="createMatch">创建匹配</el-button>
      <el-button data-test="confirm-bank-reconciliation" type="primary" :loading="actionLoading" @click="confirmReconciliation">确认对账</el-button>
    </template>
    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="会计期间"><el-input v-model="filters.periodCode" clearable placeholder="2026-07" /></el-form-item>
        <el-form-item><el-button type="primary" @click="loadRecords">查询</el-button></el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert type="info" title="确认对账允许已解释未达项，不允许未解释差额；确认版本不可修改。" :closable="false" />
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
    </template>

    <div class="financial-close-summary-strip">
      <div><span>银行余额</span><strong>{{ formatFinancialCloseAmount(selected?.bankEndingBalance) }}</strong></div>
      <div><span>账面余额</span><strong>{{ formatFinancialCloseAmount(selected?.glEndingBalance) }}</strong></div>
      <div><span>调整后银行</span><strong>{{ formatFinancialCloseAmount(selected?.adjustedBankBalance) }}</strong></div>
      <div><span>调整后账面</span><strong>{{ formatFinancialCloseAmount(selected?.adjustedBookBalance) }}</strong></div>
      <div><span>差额</span><strong>{{ formatFinancialCloseAmount(selected?.difference) }}</strong></div>
    </div>

    <div class="financial-close-candidate-grid">
      <section class="financial-close-state-card">
        <h2>银行流水候选</h2>
        <div class="table-scroll">
          <el-table :data="statementCandidates" empty-text="暂无银行流水候选" stripe>
            <el-table-column prop="statementNo" label="流水号" min-width="150" />
            <el-table-column prop="amount" label="金额" min-width="120" align="right" />
          </el-table>
        </div>
      </section>
      <section class="financial-close-state-card">
        <h2>总账分录候选</h2>
        <div class="table-scroll">
          <el-table :data="ledgerCandidates" empty-text="暂无总账分录候选" stripe>
            <el-table-column prop="voucherNo" label="凭证" min-width="150" />
            <el-table-column prop="amount" label="金额" min-width="120" align="right" />
          </el-table>
        </div>
      </section>
    </div>

    <section class="financial-close-section">
      <h2>对账记录</h2>
      <div class="table-scroll">
        <el-table :data="records" :empty-text="loading ? '加载中' : '暂无银行对账记录'" stripe>
          <el-table-column prop="reconciliationNo" label="对账号" min-width="150" />
          <el-table-column prop="periodCode" label="期间" min-width="110" />
          <el-table-column prop="bankAccountName" label="银行账户" min-width="140" />
          <el-table-column label="状态" min-width="110"><template #default="{ row }">{{ financialCloseStatusText(row.status) }}</template></el-table-column>
          <el-table-column prop="difference" label="差额" min-width="120" align="right" />
        </el-table>
      </div>
      <el-pagination
        class="table-pagination"
        layout="total, sizes, prev, pager, next"
        :page-sizes="financialClosePageSizes"
        :total="pagination.total"
        :page-size="pagination.pageSize"
        :current-page="pagination.page"
      />
    </section>
  </MasterDataTableView>
</template>
