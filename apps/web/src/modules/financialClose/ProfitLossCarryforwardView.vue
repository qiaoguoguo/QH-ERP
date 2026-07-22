<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { financialCloseApi, type FinancialClosePeriodRecord, type ProfitLossTransferRecord } from '../../shared/api/financialCloseApi'
import { confirmAction } from '../../shared/ui/confirmDialog'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import {
  canFinancialCloseAction,
  createFinancialCloseIdempotencyKey,
  financialCloseActionDisabledReason,
  financialCloseBalanceDirectionText,
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
const filters = reactive({ periodCode: '2026-07' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const periods = ref<FinancialClosePeriodRecord[]>([])
const records = ref<ProfitLossTransferRecord[]>([])
const previewRecord = ref<ProfitLossTransferRecord | null>(null)
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')

const currentPeriod = computed(() => periods.value[0] ?? null)

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const periodPage = await financialCloseApi.periods.list({ page: 1, pageSize: 10, periodCode: filters.periodCode })
    periods.value = financialClosePageItems(periodPage)
    if (!currentPeriod.value) {
      records.value = []
      pagination.total = 0
      return
    }
    const page = await financialCloseApi.profitLoss.list(currentPeriod.value.id, {
      page: pagination.page,
      pageSize: pagination.pageSize,
      periodCode: filters.periodCode,
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

async function previewTransfer() {
  if (!currentPeriod.value || actionLoading.value || !authStore.hasPermission(financialClosePermissions.profitLossGenerate)) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    previewRecord.value = await financialCloseApi.profitLoss.preview(currentPeriod.value.id, {
      version: currentPeriod.value.version,
      idempotencyKey: createFinancialCloseIdempotencyKey('profit-loss-preview'),
    })
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function generateTransfer() {
  if (!currentPeriod.value || actionLoading.value || !authStore.hasPermission(financialClosePermissions.profitLossGenerate)) {
    return
  }
  if (!(await confirmAction('生成期末损益结转正式凭证草稿？该凭证仍需 GL_VOUCHER_POST 审批记账。'))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await financialCloseApi.profitLoss.generate(currentPeriod.value.id, {
      version: currentPeriod.value.version,
      reason: '生成期末损益结转凭证草稿',
      idempotencyKey: createFinancialCloseIdempotencyKey('profit-loss-generate'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function openVoucher(record: ProfitLossTransferRecord) {
  if (!record.voucherId) {
    return
  }
  void router.push({
    name: 'gl-voucher-detail',
    params: { id: record.voucherId },
    query: { returnTo: route.fullPath || '/gl/profit-loss-carryforward' },
  })
}

function changePagination(page: number, pageSize: number) {
  pagination.page = page
  pagination.pageSize = pageSize
  void loadRecords()
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="期末损益结转" description="将当期损益科目余额结转至 4103 本年利润，只生成正式凭证草稿并继续走 GL_VOUCHER_POST 审批记账。">
    <template #actions>
      <el-button @click="loadRecords">刷新</el-button>
      <el-button data-test="preview-profit-loss" :loading="actionLoading" @click="previewTransfer">预览结转</el-button>
      <el-button data-test="generate-profit-loss" type="primary" :loading="actionLoading" @click="generateTransfer">生成草稿</el-button>
    </template>
    <template #filters>
      <el-form class="query-form">
        <el-form-item label="会计期间"><el-input v-model="filters.periodCode" clearable placeholder="2026-07" /></el-form-item>
        <el-form-item><el-button type="primary" @click="loadRecords">查询</el-button></el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert type="info" title="来源变化或反结账后必须重新生成版本；重复生成由后端幂等控制。" :closable="false" />
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
    </template>

    <div v-if="previewRecord" class="financial-close-section" data-test="profit-loss-preview">
      <h2>结转预览</h2>
      <div class="table-scroll">
        <el-table :data="previewRecord.lines ?? []" empty-text="无余额需要结转" stripe>
          <el-table-column prop="accountCode" label="科目" min-width="120" />
          <el-table-column prop="accountName" label="名称" min-width="180" />
          <el-table-column label="方向" min-width="100"><template #default="{ row }">{{ financialCloseBalanceDirectionText(row.direction) }}</template></el-table-column>
          <el-table-column label="结转金额" min-width="130" align="right">
            <template #default="{ row }"><span class="financial-close-amount">{{ formatFinancialCloseAmount(row.transferAmount as string) }}</span></template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无损益结转记录'" stripe>
        <el-table-column prop="periodCode" label="期间" min-width="110" />
        <el-table-column label="状态" min-width="110"><template #default="{ row }">{{ financialCloseStatusText(row.status) }}</template></el-table-column>
        <el-table-column label="借方" min-width="130" align="right"><template #default="{ row }">{{ formatFinancialCloseAmount(row.debitTotal, row.amountVisible === false ? row.restrictedReason : null) }}</template></el-table-column>
        <el-table-column label="贷方" min-width="130" align="right"><template #default="{ row }">{{ formatFinancialCloseAmount(row.creditTotal, row.amountVisible === false ? row.restrictedReason : null) }}</template></el-table-column>
        <el-table-column prop="sourceFingerprint" label="来源指纹" min-width="180" show-overflow-tooltip />
        <el-table-column label="审批/凭证" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.voucherNo || '等待生成 GL_VOUCHER_POST 凭证草稿' }}</template>
        </el-table-column>
        <el-table-column label="禁用原因" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ financialCloseActionDisabledReason(row, 'GENERATE') || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ row }">
            <el-button
              data-test="profit-loss-voucher-link"
              text
              :disabled="!row.voucherId || !canFinancialCloseAction(row, 'VIEW_VOUCHER')"
              @click="openVoucher(row)"
            >
              查看凭证
            </el-button>
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
