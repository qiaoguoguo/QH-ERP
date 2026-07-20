<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { financialCloseApi, type FinancialClosePeriodRecord } from '../../shared/api/financialCloseApi'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import {
  createFinancialCloseIdempotencyKey,
  financialCloseActionDisabledReason,
  financialCloseErrorMessage,
  financialClosePageItems,
  financialClosePageSizes,
  financialClosePageTotal,
  financialClosePermissions,
  financialCloseStatusText,
  formatFinancialCloseAmount,
} from './financialClosePageHelpers'
import './FinancialCloseShared.css'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const filters = reactive({ year: '2026' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<FinancialClosePeriodRecord[]>([])
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await financialCloseApi.periods.list({
      year: filters.year,
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

async function startCheck(record: FinancialClosePeriodRecord) {
  if (actionLoading.value || !authStore.hasPermission(financialClosePermissions.periodCheck)) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await financialCloseApi.periods.startCheck(record.id, {
      version: record.version,
      idempotencyKey: createFinancialCloseIdempotencyKey('financial-close-check'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function openDetail(record: FinancialClosePeriodRecord) {
  if (!record.latestCheckRunId) {
    return
  }
  void router.push({
    name: 'gl-financial-close-run-detail',
    params: { runId: record.latestCheckRunId },
    query: { returnTo: route.fullPath || '/gl/financial-close' },
  })
}

function search() {
  pagination.page = 1
  void loadRecords()
}

function resetSearch() {
  filters.year = '2026'
  pagination.page = 1
  void loadRecords()
}

function changePagination(page: number, pageSize: number) {
  pagination.page = page
  pagination.pageSize = pageSize
  void loadRecords()
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="财务结账工作台" description="按会计期间管理 032 财务结账；不同于 030 业务月结，关闭只以正式总账、银行和税务事实为准。">
    <template #actions>
      <el-button @click="loadRecords">刷新</el-button>
    </template>
    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="会计年度">
          <el-input v-model="filters.year" name="financial-close-year" clearable placeholder="2026" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert type="info" title="财务结账关闭会计期间；030 业务月结只是同月强制前置，不合并状态。" :closable="false" />
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" type="info" title="财务期间加载中" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无财务结账期间'" stripe>
        <el-table-column prop="periodCode" label="会计期间" min-width="120" />
        <el-table-column label="期间状态" min-width="110">
          <template #default="{ row }">{{ financialCloseStatusText(row.status) }}</template>
        </el-table-column>
        <el-table-column label="结账状态" min-width="120">
          <template #default="{ row }">{{ financialCloseStatusText(row.closeStatus || row.latestCheckStatus) }}</template>
        </el-table-column>
        <el-table-column label="凭证数" min-width="100" align="right">
          <template #default="{ row }">{{ row.voucherCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="银行差额" min-width="130" align="right">
          <template #default="{ row }"><span class="financial-close-amount">{{ formatFinancialCloseAmount(row.bankDifference, row.amountVisible === false ? row.restrictedReason : null) }}</span></template>
        </el-table-column>
        <el-table-column label="税额基础" min-width="130" align="right">
          <template #default="{ row }"><span class="financial-close-amount">{{ formatFinancialCloseAmount(row.taxPayableAmount, row.amountVisible === false ? row.restrictedReason : null) }}</span></template>
        </el-table-column>
        <el-table-column label="禁用原因" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ financialCloseActionDisabledReason(row, 'REOPEN') || financialCloseActionDisabledReason(row, 'CLOSE') || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="210">
          <template #default="{ row }">
            <div class="financial-close-table-actions">
              <el-button
                data-test="start-close-check"
                size="small"
                :loading="actionLoading"
                :disabled="!authStore.hasPermission(financialClosePermissions.periodCheck)"
                @click="startCheck(row)"
              >
                发起检查
              </el-button>
              <el-button
                data-test="financial-close-detail"
                size="small"
                type="primary"
                plain
                :disabled="!row.latestCheckRunId"
                @click="openDetail(row)"
              >
                检查详情
              </el-button>
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
  </MasterDataTableView>
</template>
