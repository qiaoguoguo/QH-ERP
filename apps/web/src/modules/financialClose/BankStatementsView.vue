<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { financialCloseApi, type BankStatementRecord } from '../../shared/api/financialCloseApi'
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

const filters = reactive({ keyword: '' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<BankStatementRecord[]>([])
const previewRows = ref<Array<Record<string, unknown>>>([])
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')

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
  actionLoading.value = true
  actionError.value = ''
  try {
    const result = await financialCloseApi.bankStatements.importPreview({
      fileName: 'bank-statement.csv',
      idempotencyKey: createFinancialCloseIdempotencyKey('bank-statement-preview'),
    }) as { rows?: Array<Record<string, unknown>> }
    previewRows.value = result.rows ?? []
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function ignoreLine(record: BankStatementRecord) {
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

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="银行流水" description="CSV 预览或手工录入银行流水，正式系统不保存可恢复原始 CSV 文件。">
    <template #actions>
      <el-button @click="loadRecords">刷新</el-button>
      <el-button data-test="preview-bank-statement-import" type="primary" :loading="actionLoading" @click="previewImport">CSV 预览</el-button>
    </template>
    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="流水号、银行或摘要" /></el-form-item>
        <el-form-item><el-button type="primary" @click="loadRecords">查询</el-button></el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="previewRows.length" type="warning" title="CSV 预览存在逐行错误，请修正后再确认导入。" :closable="false" />
    </template>

    <div v-if="previewRows.length" class="financial-close-section">
      <h2>导入预览</h2>
      <div class="table-scroll">
        <el-table :data="previewRows" empty-text="暂无预览错误" stripe>
          <el-table-column prop="rowNo" label="行号" min-width="90" />
          <el-table-column prop="message" label="错误" min-width="220" show-overflow-tooltip />
        </el-table>
      </div>
    </div>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无银行流水'" stripe>
        <el-table-column prop="statementNo" label="流水号" min-width="150" />
        <el-table-column prop="bankAccountName" label="银行账户" min-width="150" />
        <el-table-column prop="transactionDate" label="交易日期" min-width="120" />
        <el-table-column prop="direction" label="方向" min-width="100" />
        <el-table-column label="金额" min-width="120" align="right"><template #default="{ row }">{{ formatFinancialCloseAmount(row.amount, row.amountVisible === false ? row.restrictedReason : null) }}</template></el-table-column>
        <el-table-column label="状态" min-width="150"><template #default="{ row }">{{ financialCloseStatusText(row.status) }} / {{ row.status || '-' }}</template></el-table-column>
        <el-table-column label="重复" min-width="90"><template #default="{ row }">{{ row.duplicate ? '重复命中' : '-' }}</template></el-table-column>
        <el-table-column label="操作" fixed="right" min-width="100">
          <template #default="{ row }">
            <el-button data-test="ignore-bank-statement-line" text type="warning" @click="ignoreLine(row)">忽略</el-button>
          </template>
        </el-table-column>
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
  </MasterDataTableView>
</template>
