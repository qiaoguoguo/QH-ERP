<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { financialCloseApi, type BankReconciliationRecord } from '../../shared/api/financialCloseApi'
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
} from './financialClosePageHelpers'
import './FinancialCloseShared.css'

const authStore = useAuthStore()
const filters = reactive({ periodCode: '2026-07' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<BankReconciliationRecord[]>([])
const selected = ref<BankReconciliationRecord | null>(null)
const statementCandidates = ref<Array<Record<string, unknown>>>([])
const ledgerCandidates = ref<Array<Record<string, unknown>>>([])
const selectedStatementIds = ref<string[]>([])
const selectedLedgerIds = ref<string[]>([])
const matchAmounts = reactive<Record<string, string>>({})
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')

const canMatch = computed(() => authStore.hasPermission(financialClosePermissions.bankReconciliationMatch))
const canConfirm = computed(() => authStore.hasPermission(financialClosePermissions.bankReconciliationConfirm))
const canReopen = computed(() => authStore.hasPermission(financialClosePermissions.bankReconciliationReopen))

async function loadCandidates(record: BankReconciliationRecord) {
  const detail = await financialCloseApi.bankReconciliations.get(record.id)
  selected.value = detail
  const candidates = await financialCloseApi.bankReconciliations.candidates(record.id, {
    page: 1,
    pageSize: 20,
  }) as { statementLines?: Array<Record<string, unknown>>, ledgerEntries?: Array<Record<string, unknown>> }
  statementCandidates.value = candidates.statementLines ?? []
  ledgerCandidates.value = candidates.ledgerEntries ?? []
  selectedStatementIds.value = []
  selectedLedgerIds.value = []
  Object.keys(matchAmounts).forEach((key) => delete matchAmounts[key])
  statementCandidates.value.forEach((item) => {
    const id = String(item.id)
    matchAmounts[id] = String(item.remainingAmount ?? item.amount ?? '0.00')
  })
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
  const state = reconciliationAction('MATCH', canMatch.value, '无银行对账匹配权限')
  if (!state.allowed) {
    actionError.value = state.reason
    return
  }
  if (!selectedStatementIds.value.length || !selectedLedgerIds.value.length) {
    actionError.value = '请选择银行流水和总账分录'
    return
  }
  const pairCount = Math.min(selectedStatementIds.value.length, selectedLedgerIds.value.length)
  const matches = Array.from({ length: pairCount }, (_, index) => ({
    statementLineId: idValue(selectedStatementIds.value[index]),
    ledgerEntryId: idValue(selectedLedgerIds.value[index]),
    amount: matchAmounts[selectedStatementIds.value[index]] || '0.00',
  }))
  actionLoading.value = true
  actionError.value = ''
  try {
    await financialCloseApi.bankReconciliations.createMatch(selected.value.id, {
      matches,
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

async function cancelMatch(matchGroupNo: string) {
  if (!selected.value) {
    return
  }
  const state = reconciliationAction('MATCH', canMatch.value, '无银行对账匹配权限')
  if (!state.allowed) {
    actionError.value = state.reason
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await financialCloseApi.bankReconciliations.deleteMatch(selected.value.id, matchGroupNo, {
      version: selected.value.version,
      reason: '取消匹配',
      idempotencyKey: createFinancialCloseIdempotencyKey('bank-reconciliation-cancel-match'),
    })
    await loadCandidates(selected.value)
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function classifyException(exceptionType: string) {
  if (!selected.value) {
    return
  }
  const state = reconciliationAction('MATCH', canMatch.value, '无银行对账未达分类权限')
  if (!state.allowed) {
    actionError.value = state.reason
    return
  }
  const statementLineId = selectedStatementIds.value[0] ? idValue(selectedStatementIds.value[0]) : idValue(String(statementCandidates.value[0]?.id ?? ''))
  const ledgerEntryId = selectedLedgerIds.value[0] ? idValue(selectedLedgerIds.value[0]) : idValue(String(ledgerCandidates.value[0]?.id ?? ''))
  actionLoading.value = true
  actionError.value = ''
  try {
    await financialCloseApi.bankReconciliations.createException(selected.value.id, {
      version: selected.value.version,
      exceptionType,
      statementLineId: exceptionType.startsWith('BANK_ONLY') ? statementLineId : null,
      ledgerEntryId: exceptionType.startsWith('BOOK_ONLY') ? ledgerEntryId : null,
      amount: matchAmounts[String(statementLineId)] || '0.20',
      reason: `分类未达 ${exceptionType}`,
      idempotencyKey: createFinancialCloseIdempotencyKey('bank-reconciliation-exception'),
    })
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function calculateReconciliation() {
  if (!selected.value) {
    return
  }
  const state = reconciliationAction('CALCULATE', canMatch.value, '无银行对账重算权限')
  if (!state.allowed) {
    actionError.value = state.reason
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await financialCloseApi.bankReconciliations.calculate(selected.value.id, {
      version: selected.value.version,
      reason: '重算银行余额',
      idempotencyKey: createFinancialCloseIdempotencyKey('bank-reconciliation-calculate'),
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
  const state = reconciliationAction('CONFIRM', canConfirm.value, '无银行对账确认权限')
  if (!state.allowed) {
    actionError.value = state.reason
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

async function reopenReconciliation() {
  if (!selected.value) {
    return
  }
  const state = reconciliationAction('REOPEN', canReopen.value, '无银行对账重开权限')
  if (!state.allowed) {
    actionError.value = state.reason
    return
  }
  if (!(await confirmAction('重开银行对账版本？旧确认版本保持只读。', { title: '重开银行对账', risk: 'warning' }))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    selected.value = await financialCloseApi.bankReconciliations.reopen(selected.value.id, {
      version: selected.value.version,
      reason: '重开对账',
      idempotencyKey: createFinancialCloseIdempotencyKey('bank-reconciliation-reopen'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function reconciliationAction(action: string, permission: boolean, noPermissionReason: string) {
  return selected.value
    ? financialCloseActionState(selected.value, action, permission, noPermissionReason)
    : { allowed: false, reason: '请先选择对账版本' }
}

function idValue(value: string) {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : value
}

function matches() {
  return Array.isArray(selected.value?.matches) ? selected.value.matches as Array<Record<string, unknown>> : []
}

function changePage(page: number) {
  pagination.page = page
  void loadRecords()
}

function changePageSize(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1
  void loadRecords()
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="银行对账工作台" description="按会计期间进行银行流水与已记账银行科目分录多对多匹配，候选池不受主列表十条分页限制。">
    <template #actions>
      <el-button @click="loadRecords">刷新</el-button>
      <el-button data-test="create-bank-match" :disabled="!reconciliationAction('MATCH', canMatch, '无银行对账匹配权限').allowed" :loading="actionLoading" @click="createMatch">创建匹配</el-button>
      <el-button data-test="calculate-bank-reconciliation" :disabled="!reconciliationAction('CALCULATE', canMatch, '无银行对账重算权限').allowed" :loading="actionLoading" @click="calculateReconciliation">余额重算</el-button>
      <el-button data-test="confirm-bank-reconciliation" type="primary" :disabled="!reconciliationAction('CONFIRM', canConfirm, '无银行对账确认权限').allowed" :loading="actionLoading" @click="confirmReconciliation">确认对账</el-button>
      <el-button data-test="reopen-bank-reconciliation" type="warning" plain :disabled="!reconciliationAction('REOPEN', canReopen, '无银行对账重开权限').allowed" :loading="actionLoading" @click="reopenReconciliation">重开</el-button>
    </template>
    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="会计期间"><el-input v-model="filters.periodCode" clearable placeholder="2026-07" /></el-form-item>
        <el-form-item><el-button type="primary" @click="loadRecords">查询</el-button></el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert type="info" title="确认对账允许已解释未达项，不允许未解释差额；确认版本不可修改。" :closable="false" />
      <el-alert v-if="selected && reconciliationAction('CONFIRM', canConfirm, '无银行对账确认权限').reason" type="warning" :title="reconciliationAction('CONFIRM', canConfirm, '无银行对账确认权限').reason" :closable="false" />
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
            <el-table-column label="选择" width="70">
              <template #default="{ row }">
                <input data-test="select-statement-candidate" v-model="selectedStatementIds" type="checkbox" :value="String(row.id)" />
              </template>
            </el-table-column>
            <el-table-column prop="statementNo" label="流水号" min-width="150" />
            <el-table-column prop="amount" label="金额" min-width="120" align="right" />
            <el-table-column label="匹配金额" min-width="130" align="right">
              <template #default="{ row }">
                <input v-model="matchAmounts[String(row.id)]" name="bank-match-amount" class="financial-close-inline-input" />
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>
      <section class="financial-close-state-card">
        <h2>总账分录候选</h2>
        <div class="table-scroll">
          <el-table :data="ledgerCandidates" empty-text="暂无总账分录候选" stripe>
            <el-table-column label="选择" width="70">
              <template #default="{ row }">
                <input data-test="select-ledger-candidate" v-model="selectedLedgerIds" type="checkbox" :value="String(row.id)" />
              </template>
            </el-table-column>
            <el-table-column prop="voucherNo" label="凭证" min-width="150" />
            <el-table-column prop="amount" label="金额" min-width="120" align="right" />
          </el-table>
        </div>
      </section>
    </div>

    <section class="financial-close-section">
      <h2>匹配与未达项</h2>
      <div class="financial-close-table-actions">
        <el-button data-test="cancel-bank-match" :disabled="!matches().length" @click="cancelMatch(String(matches()[0]?.matchGroupNo || ''))">取消匹配</el-button>
        <el-button data-test="classify-bank-exception-BANK_ONLY_CREDIT" @click="classifyException('BANK_ONLY_CREDIT')">银行已收企业未收</el-button>
        <el-button data-test="classify-bank-exception-BANK_ONLY_DEBIT" @click="classifyException('BANK_ONLY_DEBIT')">银行已付企业未付</el-button>
        <el-button data-test="classify-bank-exception-BOOK_ONLY_DEBIT" @click="classifyException('BOOK_ONLY_DEBIT')">企业已收银行未收</el-button>
        <el-button data-test="classify-bank-exception-BOOK_ONLY_CREDIT" @click="classifyException('BOOK_ONLY_CREDIT')">企业已付银行未付</el-button>
      </div>
      <div class="table-scroll">
        <el-table :data="selected?.exceptions ?? []" empty-text="暂无未达项" stripe>
          <el-table-column label="类型" min-width="180"><template #default="{ row }">{{ row.exceptionType || row.category }}</template></el-table-column>
          <el-table-column prop="amount" label="金额" min-width="120" align="right" />
          <el-table-column prop="reason" label="原因" min-width="220" show-overflow-tooltip />
        </el-table>
      </div>
    </section>

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
        @current-change="changePage"
        @size-change="changePageSize"
      />
    </section>
  </MasterDataTableView>
</template>
