<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { financialCloseApi, type BankStatementRecord } from '../../shared/api/financialCloseApi'
import { confirmAction } from '../../shared/ui/confirmDialog'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import {
  bankDirectionText,
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
const filters = reactive({ keyword: '' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<BankStatementRecord[]>([])
const previewRows = ref<Array<Record<string, unknown>>>([])
const statementDialogVisible = ref(false)
const detailVisible = ref(false)
const detail = ref<Record<string, unknown> | null>(null)
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')
const csvImportForm = reactive({
  bankAccountId: '101',
  fileName: 'bank-statement.csv',
  csvContent: '交易日期,入账日期,方向,金额,对方名称,摘要,银行交易标识,参考号\n2026-07-20,2026-07-20,CREDIT,120.00,齐辉客户,银行收款,BTX-001,REF-001',
})
const statementForm = reactive({
  bankAccountId: '101',
  transactionDate: '2026-07-20',
  postingDate: '2026-07-20',
  direction: 'CREDIT',
  amount: '88.00',
  counterpartyName: '齐辉客户',
  summary: '手工银行流水',
  bankTransactionId: 'BTX-MANUAL-001',
  referenceNo: 'REF-MANUAL-001',
})

const canWriteStatements = computed(() => authStore.hasPermission(financialClosePermissions.bankReconciliationImport))

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await financialCloseApi.bankStatements.list({
      keyword: filters.keyword,
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

async function previewImport() {
  if (!canWriteStatements.value) {
    actionError.value = '无银行流水导入或录入权限'
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    const result = await financialCloseApi.bankStatements.importPreview({
      bankAccountId: Number(csvImportForm.bankAccountId),
      fileName: csvImportForm.fileName,
      csvContent: csvImportForm.csvContent,
      idempotencyKey: createFinancialCloseIdempotencyKey('bank-statement-preview'),
    }) as { rows?: Array<Record<string, unknown>>, lines?: Array<Record<string, unknown>> }
    previewRows.value = result.lines ?? result.rows ?? []
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function confirmImport() {
  if (!canWriteStatements.value) {
    actionError.value = '无银行流水导入或录入权限'
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await financialCloseApi.bankStatements.importConfirm({
      bankAccountId: Number(csvImportForm.bankAccountId),
      fileName: csvImportForm.fileName,
      csvContent: csvImportForm.csvContent,
      idempotencyKey: createFinancialCloseIdempotencyKey('bank-statement-confirm'),
    })
    previewRows.value = []
    await loadRecords()
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function openStatementCreate() {
  if (!canWriteStatements.value) {
    actionError.value = '无银行流水导入或录入权限'
    return
  }
  statementDialogVisible.value = true
}

async function saveStatement() {
  if (!canWriteStatements.value) {
    actionError.value = '无银行流水导入或录入权限'
    return
  }
  if (!statementForm.bankAccountId || !statementForm.amount) {
    actionError.value = '银行账户和金额必填'
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await financialCloseApi.bankStatements.create({
      bankAccountId: Number(statementForm.bankAccountId),
      transactionDate: statementForm.transactionDate,
      postingDate: statementForm.postingDate,
      direction: statementForm.direction,
      amount: statementForm.amount,
      counterpartyName: statementForm.counterpartyName,
      summary: statementForm.summary,
      bankTransactionId: statementForm.bankTransactionId,
      referenceNo: statementForm.referenceNo,
      idempotencyKey: createFinancialCloseIdempotencyKey('bank-statement-create'),
    })
    statementDialogVisible.value = false
    await loadRecords()
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function openDetail(record: BankStatementRecord) {
  actionError.value = ''
  try {
    detail.value = await financialCloseApi.bankStatements.get(record.id) as unknown as Record<string, unknown>
    detailVisible.value = true
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  }
}

function statementAction(record: BankStatementRecord, action: string) {
  return financialCloseActionState(record, action, canWriteStatements.value, '无银行流水写入权限')
}

async function ignoreLine(record: BankStatementRecord) {
  const state = statementAction(record, 'IGNORE')
  if (!state.allowed) {
    actionError.value = state.reason
    return
  }
  if (!(await confirmAction(`忽略银行流水 ${record.statementNo || record.id}？`, { title: '忽略银行流水', risk: 'warning' }))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await financialCloseApi.bankStatements.ignoreLine(record.id, {
      version: record.version,
      reason: '忽略无法对账流水',
      idempotencyKey: createFinancialCloseIdempotencyKey('bank-statement-ignore'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function previewErrorText(row: Record<string, unknown>) {
  if (Array.isArray(row.errors)) {
    return row.errors.join('；')
  }
  return String(row.message ?? row.errorMessage ?? '-')
}

function changePagination(page: number, pageSize: number) {
  pagination.page = page
  pagination.pageSize = pageSize
  void loadRecords()
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="银行流水" description="CSV 预览或手工录入银行流水，正式系统不保存可恢复原始 CSV 文件。">
    <template #actions>
      <el-button @click="loadRecords">刷新</el-button>
      <el-button data-test="open-bank-statement-create" :disabled="!canWriteStatements" @click="openStatementCreate">手工录入</el-button>
      <el-button data-test="preview-bank-statement-import" type="primary" :disabled="!canWriteStatements" :loading="actionLoading" @click="previewImport">CSV 预览</el-button>
    </template>
    <template #filters>
      <el-form class="query-form">
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="流水号、银行或摘要" /></el-form-item>
        <el-form-item><el-button type="primary" @click="loadRecords">查询</el-button></el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="!canWriteStatements" type="warning" title="无银行流水导入或录入权限，当前仅可查看流水。" :closable="false" />
      <el-alert v-if="previewRows.length" type="warning" title="CSV 预览存在逐行错误，请修正后再确认导入。" :closable="false" />
    </template>

    <div v-if="previewRows.length" class="financial-close-section">
      <h2>导入预览</h2>
      <div class="table-scroll">
        <el-table :data="previewRows" empty-text="暂无预览错误" stripe>
          <el-table-column prop="rowNo" label="行号" min-width="90" />
          <el-table-column label="错误" min-width="220" show-overflow-tooltip><template #default="{ row }">{{ previewErrorText(row) }}</template></el-table-column>
        </el-table>
      </div>
      <el-button data-test="confirm-bank-statement-import" type="primary" :loading="actionLoading" @click="confirmImport">确认导入</el-button>
    </div>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无银行流水'" stripe>
        <el-table-column prop="statementNo" label="流水号" min-width="150" />
        <el-table-column prop="bankAccountName" label="银行账户" min-width="150" />
        <el-table-column prop="transactionDate" label="交易日期" min-width="120" />
        <el-table-column label="方向" min-width="100"><template #default="{ row }">{{ bankDirectionText(row.direction) }}</template></el-table-column>
        <el-table-column label="金额" min-width="120" align="right"><template #default="{ row }">{{ formatFinancialCloseAmount(row.amount, row.amountVisible === false ? row.restrictedReason : null) }}</template></el-table-column>
        <el-table-column label="状态" min-width="120"><template #default="{ row }"><el-tag size="small">{{ financialCloseStatusText(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="重复" min-width="90"><template #default="{ row }">{{ row.duplicate ? '重复命中' : '-' }}</template></el-table-column>
        <el-table-column label="操作" min-width="150">
          <template #default="{ row }">
            <div class="financial-close-table-actions">
              <el-button data-test="open-bank-statement-detail" text @click="openDetail(row)">详情</el-button>
              <el-button data-test="ignore-bank-statement-line" text type="warning" :disabled="!statementAction(row, 'IGNORE').allowed" @click="ignoreLine(row)">忽略</el-button>
              <span v-if="statementAction(row, 'IGNORE').reason" class="financial-close-disabled-reason">{{ statementAction(row, 'IGNORE').reason }}</span>
            </div>
          </template>
        </el-table-column>
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
    <el-dialog v-model="statementDialogVisible" title="手工录入银行流水" width="min(640px, 92vw)">
      <el-form label-position="top">
        <el-form-item label="银行账户 ID"><el-input v-model="statementForm.bankAccountId" name="bank-statement-bank-account-id" /></el-form-item>
        <el-form-item label="交易日期">
          <el-date-picker
            v-model="statementForm.transactionDate"
            name="bank-statement-transaction-date"
            type="date"
            value-format="YYYY-MM-DD"
            value-on-clear=""
          />
        </el-form-item>
        <el-form-item label="入账日期">
          <el-date-picker
            v-model="statementForm.postingDate"
            name="bank-statement-posting-date"
            type="date"
            value-format="YYYY-MM-DD"
            value-on-clear=""
          />
        </el-form-item>
        <el-form-item label="方向">
          <el-select v-model="statementForm.direction">
            <el-option label="银行入账" value="CREDIT" />
            <el-option label="银行出账" value="DEBIT" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额"><el-input v-model="statementForm.amount" name="bank-statement-amount" /></el-form-item>
        <el-form-item label="对方名称"><el-input v-model="statementForm.counterpartyName" name="bank-statement-counterparty" /></el-form-item>
        <el-form-item label="摘要"><el-input v-model="statementForm.summary" name="bank-statement-summary" /></el-form-item>
        <el-form-item label="银行交易标识"><el-input v-model="statementForm.bankTransactionId" name="bank-statement-transaction-id" /></el-form-item>
        <el-form-item label="参考号"><el-input v-model="statementForm.referenceNo" name="bank-statement-reference-no" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statementDialogVisible = false">取消</el-button>
        <el-button data-test="save-bank-statement" type="primary" :loading="actionLoading" @click="saveStatement">保存</el-button>
      </template>
    </el-dialog>
    <el-drawer v-model="detailVisible" title="银行流水详情" size="min(640px, 92vw)">
      <dl v-if="detail" class="financial-close-detail-list">
        <dt>流水号</dt><dd>{{ detail.statementNo || detail.id }}</dd>
        <dt>银行账户</dt><dd>{{ detail.bankAccountName || '-' }}</dd>
        <dt>方向</dt><dd>{{ bankDirectionText(detail.direction as string) }}</dd>
        <dt>金额</dt><dd>{{ formatFinancialCloseAmount(detail.amount as string) }}</dd>
        <dt>对方</dt><dd>{{ detail.counterpartyName || '-' }}</dd>
        <dt>摘要</dt><dd>{{ detail.summary || '-' }}</dd>
        <dt>银行交易标识</dt><dd>{{ detail.bankTransactionId || '-' }}</dd>
        <dt>参考号</dt><dd>{{ detail.referenceNo || '-' }}</dd>
      </dl>
    </el-drawer>
  </MasterDataTableView>
</template>
