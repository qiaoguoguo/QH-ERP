<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { glApi, type GlAccountingPeriodRecord, type GlLedgerRecord } from '../../shared/api/glApi'
import { confirmAction } from '../../shared/ui/confirmDialog'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import {
  createGlIdempotencyKey,
  glErrorMessage,
  glPageItems,
  glPageSizes,
  glPageTotal,
  glPeriodStatusText,
  glPermissions,
} from './glPageHelpers'
import './GlShared.css'

const authStore = useAuthStore()
const ledger = ref<GlLedgerRecord | null>(null)
const records = ref<GlAccountingPeriodRecord[]>([])
const filters = reactive({ year: '2026' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')
const startYearMonth = ref('2026-07')

const nextPeriodCode = computed(() => {
  const latest = [...records.value]
    .map((item) => item.periodCode)
    .filter((item) => /^\d{4}-\d{2}$/.test(item))
    .sort()
    .at(-1)
  if (!latest) {
    return ledger.value?.startPeriodCode || startYearMonth.value
  }
  const [year, month] = latest.split('-').map(Number)
  const nextMonth = month === 12 ? 1 : month + 1
  const nextYear = month === 12 ? year + 1 : year
  return `${nextYear}-${String(nextMonth).padStart(2, '0')}`
})

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const [ledgerRecord, page] = await Promise.all([
      glApi.ledger.get(),
      glApi.accountingPeriods.list({
        year: filters.year,
        page: pagination.page,
        pageSize: pagination.pageSize,
      }),
    ])
    ledger.value = ledgerRecord
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

async function initializeLedger() {
  if (actionLoading.value || !authStore.hasPermission(glPermissions.periodInitialize)) {
    return
  }
  if (!(await confirmAction(`从 ${startYearMonth.value} 启用总账？`))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    ledger.value = await glApi.ledger.initialize({
      startYearMonth: startYearMonth.value,
      idempotencyKey: createGlIdempotencyKey('gl-ledger-init'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = glErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function createNextPeriod() {
  if (actionLoading.value || !authStore.hasPermission(glPermissions.periodCreate)) {
    return
  }
  if (!(await confirmAction(`新增会计期间 ${nextPeriodCode.value}？`))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await glApi.accountingPeriods.create({
      version: ledger.value?.version ?? 0,
      periodCode: nextPeriodCode.value,
      idempotencyKey: createGlIdempotencyKey('gl-period-create'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = glErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
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
  <MasterDataTableView title="会计期间" description="单公司、单账簿、人民币总账期间；031 仅提供 OPEN 记账归属，不提供 032 财务关账能力。">
    <template #actions>
      <el-button @click="loadRecords">刷新</el-button>
      <el-button
        v-if="!ledger?.initialized"
        data-test="initialize-gl-ledger"
        type="primary"
        :loading="actionLoading"
        :disabled="actionLoading || !authStore.hasPermission(glPermissions.periodInitialize)"
        @click="initializeLedger"
      >
        初始化总账
      </el-button>
      <el-button
        v-if="ledger?.initialized"
        data-test="create-next-gl-period"
        type="primary"
        plain
        :loading="actionLoading"
        :disabled="actionLoading || !authStore.hasPermission(glPermissions.periodCreate)"
        @click="createNextPeriod"
      >
        新增下一期间 {{ nextPeriodCode }}
      </el-button>
    </template>
    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="会计年度">
          <el-input v-model="filters.year" name="gl-period-year" clearable placeholder="2026" />
        </el-form-item>
        <el-form-item v-if="!ledger?.initialized" label="启用首月">
          <el-input v-model="startYearMonth" name="gl-ledger-start-month" clearable placeholder="2026-07" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
        </el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert v-if="!ledger?.initialized" type="warning" title="总账未启用，请先初始化总账" :closable="false" />
      <el-alert type="info" title="会计期间不同于业务月结；031 不提供财务关账相关能力" :closable="false" />
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" type="info" title="会计期间加载中" :closable="false" />
    </template>

    <div class="gl-summary-strip">
      <div><span>账簿</span><strong>{{ ledger?.ledgerName || '总账' }}</strong></div>
      <div><span>本位币</span><strong>{{ ledger?.baseCurrency || 'CNY' }}</strong></div>
      <div><span>启用期间</span><strong>{{ ledger?.startPeriodCode || '-' }}</strong></div>
      <div><span>状态</span><strong>{{ ledger?.initialized ? '已启用' : '总账未启用' }}</strong></div>
    </div>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无会计期间'" stripe>
        <el-table-column prop="periodCode" label="期间" min-width="120" />
        <el-table-column prop="startDate" label="开始日期" min-width="120" />
        <el-table-column prop="endDate" label="结束日期" min-width="120" />
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">{{ glPeriodStatusText(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="voucherCount" label="凭证数" min-width="100" align="right" />
        <el-table-column prop="lastPostedAt" label="最近记账时间" min-width="180" show-overflow-tooltip />
      </el-table>
    </div>
    <el-pagination
      class="table-pagination"
      layout="total, sizes, prev, pager, next"
      :page-sizes="glPageSizes"
      :total="pagination.total"
      :page-size="pagination.pageSize"
      :current-page="pagination.page"
      @current-change="changePage"
      @size-change="changePageSize"
    />
  </MasterDataTableView>
</template>
