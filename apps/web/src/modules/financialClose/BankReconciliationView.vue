<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { financialCloseApi, type BankReconciliationRecord } from '../../shared/api/financialCloseApi'
import { confirmAction } from '../../shared/ui/confirmDialog'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import {
  bankReconciliationExceptionText,
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
const matchDetails = ref<Array<{ key: string, statementLineId: string, ledgerEntryId: string, amount: string }>>([])
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')

const canMatch = computed(() => authStore.hasPermission(financialClosePermissions.bankReconciliationMatch))
const canConfirm = computed(() => authStore.hasPermission(financialClosePermissions.bankReconciliationConfirm))
const canReopen = computed(() => authStore.hasPermission(financialClosePermissions.bankReconciliationReopen))
const createMatchDisabledReason = computed(() => {
  const state = reconciliationAction('MATCH', canMatch.value, '无银行对账匹配权限')
  return state.allowed ? matchValidationReason() : state.reason
})
const bankOnlyExceptionReason = computed(() => exceptionDisabledReason('BANK_ONLY_CREDIT'))
const bookOnlyExceptionReason = computed(() => exceptionDisabledReason('BOOK_ONLY_DEBIT'))

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
  matchDetails.value = []
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
  if (createMatchDisabledReason.value) {
    actionError.value = createMatchDisabledReason.value
    return
  }
  const matches = matchDetails.value.map((detail) => ({
    statementLineId: idValue(detail.statementLineId),
    ledgerEntryId: idValue(detail.ledgerEntryId),
    amount: detail.amount,
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
  const disabledReason = exceptionDisabledReason(exceptionType)
  if (disabledReason) {
    actionError.value = disabledReason
    return
  }
  const bankOnly = exceptionType.startsWith('BANK_ONLY')
  const selectedId = bankOnly ? selectedStatementIds.value[0] : selectedLedgerIds.value[0]
  const candidate = bankOnly ? findStatementCandidate(selectedId) : findLedgerCandidate(selectedId)
  if (!selectedId || !candidate) {
    actionError.value = bankOnly ? '请选择银行流水候选' : '请选择总账分录候选'
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await financialCloseApi.bankReconciliations.createException(selected.value.id, {
      version: selected.value.version,
      exceptionType,
      statementLineId: bankOnly ? idValue(selectedId) : null,
      ledgerEntryId: bankOnly ? null : idValue(selectedId),
      amount: candidateRemainingAmount(candidate),
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

function candidateId(candidate: Record<string, unknown> | undefined) {
  return String(candidate?.id ?? '')
}

function candidateLabel(candidate: Record<string, unknown> | undefined, fallback: string) {
  return String(candidate?.statementNo ?? candidate?.voucherNo ?? candidate?.sourceNo ?? candidate?.id ?? fallback)
}

function findStatementCandidate(id: string | undefined) {
  return statementCandidates.value.find((candidate) => candidateId(candidate) === id)
}

function findLedgerCandidate(id: string | undefined) {
  return ledgerCandidates.value.find((candidate) => candidateId(candidate) === id)
}

function candidateRemainingAmount(candidate: Record<string, unknown> | undefined) {
  return candidate?.remainingAmount === null || candidate?.remainingAmount === undefined ? '' : String(candidate.remainingAmount)
}

function amountToCents(value: unknown) {
  const text = String(value ?? '').trim()
  const matched = /^(\d+)(?:\.(\d{1,2}))?$/.exec(text)
  if (!matched) {
    return null
  }
  return Number(matched[1]) * 100 + Number((matched[2] ?? '').padEnd(2, '0'))
}

function defaultMatchAmount(statementLineId: string, ledgerEntryId: string) {
  if (selectedStatementIds.value.length === 1 && selectedLedgerIds.value.length > 1) {
    return candidateRemainingAmount(findLedgerCandidate(ledgerEntryId))
  }
  return candidateRemainingAmount(findStatementCandidate(statementLineId))
}

function buildMatchPairs() {
  const statements = selectedStatementIds.value
  const ledgers = selectedLedgerIds.value
  if (!statements.length || !ledgers.length) {
    return []
  }
  if (statements.length === 1) {
    return ledgers.map((ledgerEntryId) => ({ statementLineId: statements[0], ledgerEntryId }))
  }
  if (ledgers.length === 1) {
    return statements.map((statementLineId) => ({ statementLineId, ledgerEntryId: ledgers[0] }))
  }
  if (statements.length === ledgers.length) {
    return statements.map((statementLineId, index) => ({ statementLineId, ledgerEntryId: ledgers[index] }))
  }
  return []
}

function syncMatchDetails() {
  const existing = new Map(matchDetails.value.map((detail) => [detail.key, detail]))
  matchDetails.value = buildMatchPairs().map(({ statementLineId, ledgerEntryId }) => {
    const key = `${statementLineId}:${ledgerEntryId}`
    return existing.get(key) ?? {
      key,
      statementLineId,
      ledgerEntryId,
      amount: defaultMatchAmount(statementLineId, ledgerEntryId),
    }
  })
}

function matchValidationReason() {
  if (!selectedStatementIds.value.length || !selectedLedgerIds.value.length) {
    return '请选择银行流水和总账分录'
  }
  if (!matchDetails.value.length) {
    return '请使用 1 对多、多对 1 或等行数明细表达匹配关系'
  }
  const statementTotals = new Map<string, number>()
  const ledgerTotals = new Map<string, number>()
  for (const detail of matchDetails.value) {
    if (!detail.statementLineId || !detail.ledgerEntryId) {
      return '匹配明细必须选择银行流水和总账分录'
    }
    const cents = amountToCents(detail.amount)
    if (cents === null || cents <= 0) {
      return '匹配金额必须大于 0，且最多保留两位小数'
    }
    statementTotals.set(detail.statementLineId, (statementTotals.get(detail.statementLineId) ?? 0) + cents)
    ledgerTotals.set(detail.ledgerEntryId, (ledgerTotals.get(detail.ledgerEntryId) ?? 0) + cents)
  }
  for (const [id, total] of statementTotals) {
    const candidate = findStatementCandidate(id)
    const remaining = amountToCents(candidateRemainingAmount(candidate))
    if (remaining !== null && total > remaining) {
      return `银行流水 ${candidateLabel(candidate, id)} 匹配金额超过剩余金额`
    }
  }
  for (const [id, total] of ledgerTotals) {
    const candidate = findLedgerCandidate(id)
    const remaining = amountToCents(candidateRemainingAmount(candidate))
    if (remaining !== null && total > remaining) {
      return `总账分录 ${candidateLabel(candidate, id)} 匹配金额超过剩余金额`
    }
  }
  return ''
}

function exceptionDisabledReason(exceptionType: string) {
  const state = reconciliationAction('MATCH', canMatch.value, '无银行对账未达分类权限')
  if (!state.allowed) {
    return state.reason
  }
  if (exceptionType.startsWith('BANK_ONLY')) {
    if (!selectedStatementIds.value.length) {
      return '请选择银行流水候选'
    }
    if (selectedStatementIds.value.length > 1) {
      return '一次只能选择一条银行流水候选'
    }
    if (!candidateRemainingAmount(findStatementCandidate(selectedStatementIds.value[0]))) {
      return '银行流水候选缺少剩余金额'
    }
  }
  if (exceptionType.startsWith('BOOK_ONLY')) {
    if (!selectedLedgerIds.value.length) {
      return '请选择总账分录候选'
    }
    if (selectedLedgerIds.value.length > 1) {
      return '一次只能选择一条总账分录候选'
    }
    if (!candidateRemainingAmount(findLedgerCandidate(selectedLedgerIds.value[0]))) {
      return '总账分录候选缺少剩余金额'
    }
  }
  return ''
}

function changePagination(page: number, pageSize: number) {
  pagination.page = page
  pagination.pageSize = pageSize
  void loadRecords()
}

onMounted(loadRecords)

watch([selectedStatementIds, selectedLedgerIds], syncMatchDetails, { deep: true })
</script>

<template>
  <MasterDataTableView title="银行对账工作台" description="按会计期间进行银行流水与已记账银行科目分录多对多匹配，候选池不受主列表十条分页限制。">
    <template #actions>
      <el-button @click="loadRecords">刷新</el-button>
      <el-button data-test="create-bank-match" :disabled="!!createMatchDisabledReason" :loading="actionLoading" @click="createMatch">创建匹配</el-button>
      <el-button data-test="calculate-bank-reconciliation" :disabled="!reconciliationAction('CALCULATE', canMatch, '无银行对账重算权限').allowed" :loading="actionLoading" @click="calculateReconciliation">余额重算</el-button>
      <el-button data-test="confirm-bank-reconciliation" type="primary" :disabled="!reconciliationAction('CONFIRM', canConfirm, '无银行对账确认权限').allowed" :loading="actionLoading" @click="confirmReconciliation">确认对账</el-button>
      <el-button data-test="reopen-bank-reconciliation" type="warning" plain :disabled="!reconciliationAction('REOPEN', canReopen, '无银行对账重开权限').allowed" :loading="actionLoading" @click="reopenReconciliation">重开</el-button>
    </template>
    <template #filters>
      <el-form class="query-form">
        <el-form-item label="会计期间"><el-input v-model="filters.periodCode" clearable placeholder="2026-07" /></el-form-item>
        <el-form-item><el-button type="primary" @click="loadRecords">查询</el-button></el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert type="info" title="确认对账允许已解释未达项，不允许未解释差额；确认版本不可修改。" :closable="false" />
      <el-alert v-if="selected && reconciliationAction('CONFIRM', canConfirm, '无银行对账确认权限').reason" type="warning" :title="reconciliationAction('CONFIRM', canConfirm, '无银行对账确认权限').reason" :closable="false" />
      <el-alert v-if="createMatchDisabledReason" type="warning" :title="createMatchDisabledReason" :closable="false" />
      <el-alert v-if="bankOnlyExceptionReason || bookOnlyExceptionReason" type="warning" :title="[bankOnlyExceptionReason, bookOnlyExceptionReason].filter(Boolean).join('；')" :closable="false" />
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
            <el-table-column prop="remainingAmount" label="剩余金额" min-width="120" align="right" />
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
      <h2>匹配明细</h2>
      <div class="table-scroll">
        <el-table :data="matchDetails" empty-text="请选择候选生成匹配明细" stripe>
          <el-table-column label="银行流水" min-width="190">
            <template #default="{ row }">
              <el-select v-model="row.statementLineId" filterable>
                <el-option
                  v-for="candidate in statementCandidates"
                  :key="String(candidate.id)"
                  :label="candidateLabel(candidate, String(candidate.id))"
                  :value="String(candidate.id)"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="总账分录" min-width="190">
            <template #default="{ row }">
              <el-select v-model="row.ledgerEntryId" filterable>
                <el-option
                  v-for="candidate in ledgerCandidates"
                  :key="String(candidate.id)"
                  :label="candidateLabel(candidate, String(candidate.id))"
                  :value="String(candidate.id)"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="匹配金额" min-width="130" align="right">
            <template #default="{ row }">
              <input v-model="row.amount" name="bank-match-detail-amount" class="financial-close-inline-input" />
            </template>
          </el-table-column>
        </el-table>
      </div>
      <p v-if="createMatchDisabledReason" class="financial-close-disabled-reason">{{ createMatchDisabledReason }}</p>
    </section>

    <section class="financial-close-section">
      <h2>匹配与未达项</h2>
      <div class="financial-close-table-actions">
        <el-button data-test="cancel-bank-match" :disabled="!matches().length" @click="cancelMatch(String(matches()[0]?.matchGroupNo || ''))">取消匹配</el-button>
        <el-button data-test="classify-bank-exception-BANK_ONLY_CREDIT" :disabled="!!exceptionDisabledReason('BANK_ONLY_CREDIT')" @click="classifyException('BANK_ONLY_CREDIT')">银行已收企业未收</el-button>
        <el-button data-test="classify-bank-exception-BANK_ONLY_DEBIT" :disabled="!!exceptionDisabledReason('BANK_ONLY_DEBIT')" @click="classifyException('BANK_ONLY_DEBIT')">银行已付企业未付</el-button>
        <el-button data-test="classify-bank-exception-BOOK_ONLY_DEBIT" :disabled="!!exceptionDisabledReason('BOOK_ONLY_DEBIT')" @click="classifyException('BOOK_ONLY_DEBIT')">企业已收银行未收</el-button>
        <el-button data-test="classify-bank-exception-BOOK_ONLY_CREDIT" :disabled="!!exceptionDisabledReason('BOOK_ONLY_CREDIT')" @click="classifyException('BOOK_ONLY_CREDIT')">企业已付银行未付</el-button>
      </div>
      <p v-if="bankOnlyExceptionReason || bookOnlyExceptionReason" class="financial-close-disabled-reason">
        {{ [bankOnlyExceptionReason, bookOnlyExceptionReason].filter(Boolean).join('；') }}
      </p>
      <div class="table-scroll">
        <el-table :data="selected?.exceptions ?? []" empty-text="暂无未达项" stripe>
          <el-table-column label="类型" min-width="180"><template #default="{ row }">{{ bankReconciliationExceptionText(row.exceptionType || row.category) }}</template></el-table-column>
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
        v-model:page-size="pagination.pageSize"
        v-model:current-page="pagination.page"
        @change="changePagination"
      />
    </section>
  </MasterDataTableView>
</template>
