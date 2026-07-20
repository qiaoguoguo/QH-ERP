<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { glApi, type GlLedgerRow } from '../../shared/api/glApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { formatGlAmount, glErrorMessage, glPageItems, glPageSizes, glPageTotal } from './glPageHelpers'
import './GlShared.css'

const router = useRouter()
const route = useRoute()
const filters = reactive({ periodCode: '2026-07', accountKeyword: '' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<GlLedgerRow[]>([])
const loading = ref(false)
const error = ref('')

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await glApi.ledgers.detail({
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

function openVoucher(row: GlLedgerRow) {
  if (!row.voucherId) {
    return
  }
  return router.push({
    name: 'gl-voucher-detail',
    params: { id: row.voucherId },
    query: { returnTo: route.fullPath || '/gl/ledgers/detail' },
  })
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
  <MasterDataTableView title="明细账" description="按凭证分录展示科目明细，支持跳转正式凭证与来源追溯。">
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
      <el-alert v-if="loading" type="info" title="明细账加载中" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无明细账记录'" stripe>
        <el-table-column prop="voucherDate" label="日期" min-width="110" />
        <el-table-column label="凭证号" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.voucherId" data-test="ledger-voucher-link" text @click="openVoucher(row)">{{ row.voucherNo || row.voucherId }}</el-button>
            <span v-else>{{ row.voucherNo || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
        <el-table-column label="科目" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ row.accountCode }} {{ row.accountName }}</template></el-table-column>
        <el-table-column label="借方" min-width="130" align="right"><template #default="{ row }">{{ amountText(row, row.debitAmount) }}</template></el-table-column>
        <el-table-column label="贷方" min-width="130" align="right"><template #default="{ row }">{{ amountText(row, row.creditAmount) }}</template></el-table-column>
        <el-table-column label="余额" min-width="130" align="right"><template #default="{ row }">{{ amountText(row, row.runningBalance) }}</template></el-table-column>
        <el-table-column prop="sourceSummary" label="来源追溯" min-width="180" show-overflow-tooltip />
      </el-table>
    </div>
    <el-pagination class="table-pagination" layout="total, sizes, prev, pager, next" :page-sizes="glPageSizes" :total="pagination.total" :page-size="pagination.pageSize" :current-page="pagination.page" @current-change="changePage" @size-change="changePageSize" />
  </MasterDataTableView>
</template>
