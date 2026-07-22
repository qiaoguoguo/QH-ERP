<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  returnRefundReversalApi,
  type ReversalRouteValue,
  type ReversalSourceView,
  type ReversalTraceRecord,
  type SettlementAdjustmentDetail,
} from '../../shared/api/returnRefundReversalApi'
import { currentRouteReturnTo, queryWithReturnTo, returnLocation, routeReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import { financeErrorMessage, financePermissions, formatFinanceAmount } from '../finance/financePageHelpers'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import ReversalStatusTag from './ReversalStatusTag.vue'
import ReversalTracePanel from './ReversalTracePanel.vue'
import {
  reversalTraceStatusLabel,
  settlementAdjustmentSourceTypeLabel,
  settlementAdjustmentTypeLabel,
  settlementSideLabel,
  targetSettlementStatusLabel,
} from './reversalPageHelpers'
import { confirmAction } from '../../shared/ui/confirmDialog'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<SettlementAdjustmentDetail | null>(null)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const traceRows = ref<ReversalTraceRecord[]>([])
const traceVisible = ref(false)
const traceLoading = ref(false)
const traceError = ref('')

const routeId = computed(() => route.params.id as string)
const canUpdate = computed(() => authStore.hasPermission(financePermissions.settlementAdjustmentUpdate))
const canPost = computed(() => authStore.hasPermission(financePermissions.settlementAdjustmentPost))
const canCancel = computed(() => authStore.hasPermission(financePermissions.settlementAdjustmentCancel))
const canTrace = computed(() => authStore.hasPermission('business:reversal:view'))
const isDraft = computed(() => record.value?.status === 'DRAFT')

function sourceRestricted(source?: ReversalSourceView | null) {
  return !source || source.restricted || !source.canViewSource
}

function routeValues(values?: Record<string, ReversalRouteValue>) {
  return Object.fromEntries(Object.entries(values ?? {}).map(([key, value]) => [key, String(value)]))
}

async function loadDetail() {
  loading.value = true
  error.value = ''
  try {
    record.value = await returnRefundReversalApi.settlementAdjustments.get(routeId.value)
  } catch (caught) {
    record.value = null
    error.value = financeErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function backToList() {
  void router.push(returnLocation(route, { name: 'finance-settlement-adjustments' }))
}

function editSettlementAdjustment() {
  if (!record.value) {
    return
  }
  void router.push({
    name: 'finance-settlement-adjustment-edit',
    params: { id: String(record.value.id) },
    query: queryWithReturnTo({}, routeReturnTo(route)),
  })
}

function viewSourceDocument(source: ReversalSourceView) {
  if (sourceRestricted(source) || !source.resourceRouteName) {
    return
  }
  void router.push({
    name: source.resourceRouteName,
    params: routeValues(source.resourceRouteParams),
    query: queryWithReturnTo(routeValues(source.resourceRouteQuery), currentRouteReturnTo(route)),
  })
}

async function postSettlementAdjustment() {
  if (!record.value || actionLoading.value || !(await confirmAction(`确认过账往来冲减“${record.value.adjustmentNo}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    record.value = await returnRefundReversalApi.settlementAdjustments.post(record.value.id)
  } catch (caught) {
    actionError.value = financeErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function cancelSettlementAdjustment() {
  if (!record.value || actionLoading.value || !(await confirmAction(`确认取消往来冲减“${record.value.adjustmentNo}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    record.value = await returnRefundReversalApi.settlementAdjustments.cancel(record.value.id)
  } catch (caught) {
    actionError.value = financeErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function openTrace() {
  if (!record.value || traceLoading.value) {
    return
  }
  traceVisible.value = true
  traceLoading.value = true
  traceError.value = ''
  try {
    traceRows.value = await returnRefundReversalApi.traces.list({
      sourceType: 'SETTLEMENT_ADJUSTMENT',
      sourceId: record.value.id,
      direction: 'REVERSE_TO_SOURCE',
      includeRestricted: true,
    })
  } catch (caught) {
    traceRows.value = []
    traceError.value = financeErrorMessage(caught)
  } finally {
    traceLoading.value = false
  }
}

function closeTrace() {
  traceVisible.value = false
}

onMounted(() => {
  void loadDetail()
})
</script>

<template>
  <MasterDataTableView title="往来冲减详情" description="查看退款记录、应收冲减或应付冲减的来源、目标余额和来源追溯。">
    <template #actions>
      <el-button @click="backToList">返回列表</el-button>
      <el-button
        v-if="record && canUpdate && isDraft"
        data-test="edit-settlement-adjustment-detail"
        @click="editSettlementAdjustment"
      >
        编辑
      </el-button>
      <el-button
        v-if="record && canPost && isDraft"
        data-test="post-settlement-adjustment-detail"
        type="success"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="postSettlementAdjustment"
      >
        过账
      </el-button>
      <el-button
        v-if="record && canCancel && isDraft"
        data-test="cancel-settlement-adjustment-detail"
        type="danger"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="cancelSettlementAdjustment"
      >
        取消
      </el-button>
      <el-button
        v-if="record && canTrace"
        data-test="open-reversal-trace"
        type="primary"
        plain
        @click="openTrace"
      >
        来源追溯
      </el-button>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="往来冲减详情加载中" :closable="false" />
    </template>

    <el-empty v-if="!loading && !record" description="往来冲减不存在或无权查看" />
    <div v-else-if="record" class="detail-layout">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="冲减单号">{{ record.adjustmentNo }}</el-descriptions-item>
        <el-descriptions-item label="往来方向">{{ settlementSideLabel(record.settlementSide) }}</el-descriptions-item>
        <el-descriptions-item label="冲减类型">{{ settlementAdjustmentTypeLabel(record.adjustmentType) }}</el-descriptions-item>
        <el-descriptions-item label="业务日期">{{ record.businessDate }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <ReversalStatusTag :status="record.status" />
        </el-descriptions-item>
        <el-descriptions-item label="冲减金额">
          <span class="numeric-cell">{{ formatFinanceAmount(record.amount) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ record.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-card class="section-card" shadow="never">
        <template #header>来源追溯</template>
        <el-alert
          v-if="sourceRestricted(record.source)"
          type="warning"
          :title="record.source.restrictedMessage || '来源无查看权限'"
          :closable="false"
        />
        <el-descriptions v-else :column="3" border>
          <el-descriptions-item label="来源类型">{{ settlementAdjustmentSourceTypeLabel(record.source.sourceType) }}</el-descriptions-item>
          <el-descriptions-item label="来源单号">
            <el-button
              v-if="record.source.resourceRouteName"
              data-test="view-source-document"
              type="primary"
              link
              @click="viewSourceDocument(record.source)"
            >
              {{ record.source.sourceNo || '-' }}
            </el-button>
            <span v-else>{{ record.source.sourceNo || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="来源状态">{{ reversalTraceStatusLabel({ sourceType: record.source.sourceType, status: record.source.status }) }}</el-descriptions-item>
          <el-descriptions-item label="来源日期">{{ record.source.businessDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源金额">
            <span class="numeric-cell">{{ formatFinanceAmount(record.source.amount) }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card class="section-card" shadow="never">
        <template #header>目标应收/应付</template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="目标单号">{{ record.targetNo }}</el-descriptions-item>
          <el-descriptions-item label="原金额">
            <span class="numeric-cell">{{ formatFinanceAmount(record.targetOriginalAmount) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="已冲减金额">
            <span class="numeric-cell">{{ formatFinanceAmount(record.targetAdjustedAmountBefore) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="冲减前可冲金额">
            <span class="numeric-cell">{{ formatFinanceAmount(record.targetAdjustableAmountBefore) }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card class="section-card" shadow="never">
        <template #header>冲减前后余额</template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="本次冲减">
            <span class="numeric-cell">{{ formatFinanceAmount(record.amount) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="过账后余额">
            <span class="numeric-cell">{{ formatFinanceAmount(record.targetRemainingAmountAfterPost) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="过账后目标状态">{{ targetSettlementStatusLabel(record.targetStatusAfterPost) }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <ReversalTracePanel
        :visible="traceVisible"
        :rows="traceRows"
        :loading="traceLoading"
        :error="traceError"
        @close="closeTrace"
      />
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.detail-layout {
  display: grid;
  gap: 14px;
}

.section-card {
  border-radius: 6px;
}

.numeric-cell {
  display: inline-block;
  min-width: 72px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
</style>
