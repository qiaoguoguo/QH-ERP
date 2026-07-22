<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { financialCloseApi, type FinancialCloseCheckRunRecord } from '../../shared/api/financialCloseApi'
import { returnLocation } from '../../shared/navigation/navigationReturn'
import { confirmAction } from '../../shared/ui/confirmDialog'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import {
  canFinancialCloseAction,
  createFinancialCloseIdempotencyKey,
  financialCloseActionDisabledReason,
  financialCloseErrorMessage,
  financialClosePermissions,
  financialCloseSeverityText,
  financialCloseStatusText,
  formatFinancialCloseAmount,
  sourceVisibleText,
  sourceTypeText,
} from './financialClosePageHelpers'
import './FinancialCloseShared.css'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<FinancialCloseCheckRunRecord | null>(null)
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')

const closeRunId = computed(() => {
  const snapshot = record.value?.closeSnapshot as Record<string, unknown> | null | undefined
  return snapshot?.closeRunId ?? record.value?.id
})

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await financialCloseApi.checkRuns.get(String(route.params.runId))
  } catch (caught) {
    record.value = null
    error.value = financialCloseErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function closePeriod() {
  if (!record.value || actionLoading.value || !authStore.hasPermission(financialClosePermissions.periodClose)) {
    return
  }
  if (!(await confirmAction('确认关闭当前会计期间？关闭后本期间制证和记账写入将被阻断。', { title: '关闭会计期间', risk: 'danger' }))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    record.value = await financialCloseApi.checkRuns.close(record.value.id, {
      version: record.value.version,
      reason: '月末关闭',
      idempotencyKey: createFinancialCloseIdempotencyKey('financial-close-close'),
    })
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function requestReopen() {
  if (!record.value || actionLoading.value || !authStore.hasPermission(financialClosePermissions.periodReopen)) {
    return
  }
  if (!(await confirmAction('提交反结账申请？该动作将进入 FINANCIAL_PERIOD_REOPEN 双人审批。', { title: '申请反结账', risk: 'danger' }))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await financialCloseApi.closeRuns.requestReopen(closeRunId.value as string | number, {
      version: record.value.version,
      reason: '调整凭证',
      idempotencyKey: createFinancialCloseIdempotencyKey('financial-close-reopen'),
    })
    await loadRecord()
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function goBack() {
  void router.push(returnLocation(route, { name: 'gl-financial-close' }))
}

onMounted(loadRecord)
</script>

<template>
  <MasterDataTableView title="结账检查详情" description="查看关闭检查、来源追溯、关闭快照与反结账审批历史，所有关闭动作以后端复检为准。">
    <template #actions>
      <el-button data-test="return-from-close-run" @click="goBack">返回</el-button>
      <el-button
        type="danger"
        :loading="actionLoading"
        :disabled="!record || !canFinancialCloseAction(record, 'CLOSE') || !authStore.hasPermission(financialClosePermissions.periodClose)"
        @click="closePeriod"
      >
        关闭期间
      </el-button>
      <el-button
        type="warning"
        plain
        :loading="actionLoading"
        :disabled="!record || !canFinancialCloseAction(record, 'REOPEN') || !authStore.hasPermission(financialClosePermissions.periodReopen)"
        @click="requestReopen"
      >
        申请反结账
      </el-button>
    </template>
    <template #alerts>
      <el-alert type="info" title="反结账固定走 FINANCIAL_PERIOD_REOPEN 双人审批；检查 READY 仍需关闭事务内重新复检。" :closable="false" />
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" type="info" title="结账检查加载中" :closable="false" />
      <el-alert v-if="record && financialCloseActionDisabledReason(record, 'CLOSE')" type="warning" :title="financialCloseActionDisabledReason(record, 'CLOSE')" :closable="false" />
    </template>

    <div v-if="record" class="financial-close-summary-strip">
      <div><span>期间</span><strong>{{ record.periodCode || '-' }}</strong></div>
      <div><span>检查状态</span><strong>{{ financialCloseStatusText(record.status) }}</strong></div>
      <div><span>来源指纹</span><strong>{{ record.sourceFingerprint || '-' }}</strong></div>
      <div><span>关闭版本</span><strong>{{ record.closeVersion ?? '-' }}</strong></div>
    </div>

    <div class="table-scroll">
      <el-table :data="record?.checkItems ?? []" :empty-text="loading ? '加载中' : '暂无检查项'" stripe>
        <el-table-column prop="code" label="检查项" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" min-width="110"><template #default="{ row }">{{ financialCloseStatusText(row.status) }}</template></el-table-column>
        <el-table-column label="级别" min-width="100"><template #default="{ row }">{{ financialCloseSeverityText(row.severity) }}</template></el-table-column>
        <el-table-column prop="actualValue" label="实际值" min-width="120" />
        <el-table-column prop="expectedValue" label="期望值" min-width="120" />
        <el-table-column prop="conclusion" label="结论" min-width="220" show-overflow-tooltip />
        <el-table-column label="来源" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.sourceVisible === false ? '来源受限' : `${sourceTypeText(row.sourceType)} ${row.sourceNo || row.sourceId || ''}` }}
          </template>
        </el-table-column>
      </el-table>
    </div>

    <section class="financial-close-section">
      <h2>关闭快照</h2>
      <div class="financial-close-summary-strip">
        <div><span>试算借方</span><strong>{{ formatFinancialCloseAmount(record?.closeSnapshot?.trialBalanceDebitTotal as string | undefined) }}</strong></div>
        <div><span>试算贷方</span><strong>{{ formatFinancialCloseAmount(record?.closeSnapshot?.trialBalanceCreditTotal as string | undefined) }}</strong></div>
        <div><span>凭证数量</span><strong>{{ record?.closeSnapshot?.voucherCount ?? '-' }}</strong></div>
        <div><span>来源权限</span><strong>{{ sourceVisibleText(record?.sourceVisible) }}</strong></div>
      </div>
    </section>

    <section class="financial-close-section">
      <h2>反结账历史</h2>
      <div class="table-scroll">
        <el-table :data="record?.reopenRequests ?? []" empty-text="暂无反结账申请" stripe>
          <el-table-column prop="id" label="申请" min-width="90" />
          <el-table-column label="状态" min-width="120"><template #default="{ row }">{{ financialCloseStatusText(row.status) }}</template></el-table-column>
          <el-table-column prop="approvalSceneCode" label="审批场景" min-width="200" />
          <el-table-column prop="reason" label="原因" min-width="220" show-overflow-tooltip />
        </el-table>
      </div>
    </section>
  </MasterDataTableView>
</template>
