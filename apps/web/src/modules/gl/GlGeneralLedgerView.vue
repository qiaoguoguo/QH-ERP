<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { glApi, type GlLedgerRow } from '../../shared/api/glApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { formatGlAmount, glErrorMessage, glPageItems, glPageSizes, glPageTotal } from './glPageHelpers'
import './GlShared.css'

const filters = reactive({ periodCode: '2026-07', accountKeyword: '' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<GlLedgerRow[]>([])
const loading = ref(false)
const error = ref('')

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await glApi.ledgers.general({
      periodCode: filters.periodCode,
      accountKeyword: filters.accountKeyword,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = glPageItems(page)
    pagination.total = glPageTotal(page)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = glErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function amountText(row: GlLedgerRow, value: string | null | undefined) {
  return row.restricted ? (row.restrictedReason || '无权查看GL金额') : formatGlAmount(value)
}

function search() {
  pagination.page = 1
  void loadRecords()
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
  <MasterDataTableView title="总账" description="按会计期间和科目汇总已记账凭证，仅读取不可编辑账簿。">
    <template #actions><el-button @click="loadRecords">刷新</el-button></template>
    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="会计期间"><el-input v-model="filters.periodCode" clearable placeholder="2026-07" /></el-form-item>
        <el-form-item label="科目"><el-input v-model="filters.accountKeyword" clearable placeholder="编码或名称" /></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button></el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" type="info" title="总账加载中" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无总账记录'" stripe>
        <el-table-column prop="periodCode" label="期间" min-width="110" />
        <el-table-column prop="accountCode" label="科目编码" min-width="120" />
        <el-table-column prop="accountName" label="科目名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="期初借方" min-width="130" align="right"><template #default="{ row }">{{ amountText(row, row.openingDebit) }}</template></el-table-column>
        <el-table-column label="期初贷方" min-width="130" align="right"><template #default="{ row }">{{ amountText(row, row.openingCredit) }}</template></el-table-column>
        <el-table-column label="本期借方" min-width="130" align="right"><template #default="{ row }">{{ amountText(row, row.periodDebit) }}</template></el-table-column>
        <el-table-column label="本期贷方" min-width="130" align="right"><template #default="{ row }">{{ amountText(row, row.periodCredit) }}</template></el-table-column>
        <el-table-column label="期末借方" min-width="130" align="right"><template #default="{ row }">{{ amountText(row, row.endingDebit) }}</template></el-table-column>
        <el-table-column label="期末贷方" min-width="130" align="right"><template #default="{ row }">{{ amountText(row, row.endingCredit) }}</template></el-table-column>
      </el-table>
    </div>
    <el-pagination class="table-pagination" layout="total, sizes, prev, pager, next" :page-sizes="glPageSizes" :total="pagination.total" :page-size="pagination.pageSize" :current-page="pagination.page" @current-change="changePage" @size-change="changePageSize" />
  </MasterDataTableView>
</template>
