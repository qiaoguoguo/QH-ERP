<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { financeSettlementApi, type SettlementAllocationRecord } from '../../shared/api/financeSettlementApi'
import { useAuthStore } from '../../stores/authStore'
import { confirmAction } from '../../shared/ui/confirmDialog'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import {
  financeErrorMessage,
  financePermissions,
  financeSourceTypeText,
  formatFinanceAmount,
  ownershipTypeText,
  settlementStatusText,
} from './financePageHelpers'
import './Finance028Shared.css'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<SettlementAllocationRecord | null>(null)
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')

const canPost = computed(() => record.value?.allowedActions?.includes('POST') && authStore.hasPermission(financePermissions.settlementAllocationPost))
const canCancel = computed(() => record.value?.allowedActions?.includes('CANCEL') && authStore.hasPermission(financePermissions.settlementAllocationCancel))
const displayAmount = computed(() => record.value?.totalAmount ?? record.value?.amount ?? '0.00')
const returnTarget = computed(() => String(route.query.returnTo || '/finance/settlement-workbench'))

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await financeSettlementApi.settlementWorkbench.get(route.params.id as string)
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function runAction(action: 'post' | 'cancel') {
  if (!record.value || actionLoading.value) {
    return
  }
  const label = action === 'post' ? '过账' : '取消'
  if (!(await confirmAction(`${label}核销“${record.value.allocationNo ?? record.value.id}”？`))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    const payload = {
      version: record.value.version,
      idempotencyKey: `settlement-allocation-${action}-${record.value.id}-${Date.now()}`,
    }
    if (action === 'post') {
      await financeSettlementApi.settlementWorkbench.post(record.value.id, payload)
    } else {
      await financeSettlementApi.settlementWorkbench.cancel(record.value.id, payload)
    }
    await loadRecord()
  } catch (caught) {
    actionError.value = financeErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(loadRecord)
</script>

<template>
  <MasterDataTableView title="核销详情" description="查看多目标核销事实、资金来源、目标分配和非现金发生边界。">
    <template #actions>
      <el-button @click="router.push(returnTarget)">返回</el-button>
      <el-button v-if="canPost" data-test="post-settlement-allocation" type="success" :loading="actionLoading" :disabled="actionLoading" @click="runAction('post')">过账核销</el-button>
      <el-button v-if="canCancel" data-test="cancel-settlement-allocation" type="danger" :loading="actionLoading" :disabled="actionLoading" @click="runAction('cancel')">取消核销</el-button>
    </template>
    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" type="info" title="核销详情加载中" :closable="false" />
    </template>

    <div v-if="record" class="finance-summary-strip">
      <div><span>核销单号</span><strong>{{ record.allocationNo ?? record.id }}</strong></div>
      <div><span>状态</span><strong>{{ settlementStatusText(record.status) }}</strong></div>
      <div><span>资金来源</span><strong>{{ financeSourceTypeText(record.cashSourceType ?? '') }} {{ record.fundNo ?? '' }}</strong></div>
      <div><span>往来方</span><strong>{{ record.partnerName ?? '-' }}</strong></div>
      <div><span>项目/公共</span><strong>{{ ownershipTypeText(record.ownershipType) }} {{ record.projectName ?? '' }}</strong></div>
      <div><span>核销金额</span><strong>{{ formatFinanceAmount(displayAmount) }}</strong></div>
    </div>

    <div v-if="record" class="finance-section-grid">
      <section class="finance-section">
        <span class="finance-section-title">多目标分配</span>
        <div class="table-scroll">
          <el-table :data="record.lines ?? []" empty-text="暂无核销目标" stripe>
            <el-table-column label="目标类型" min-width="130"><template #default="{ row }">{{ financeSourceTypeText(row.targetType) }}</template></el-table-column>
            <el-table-column prop="targetNo" label="目标单号" min-width="160" show-overflow-tooltip />
            <el-table-column prop="sourceSummary" label="来源摘要" min-width="180" show-overflow-tooltip />
            <el-table-column label="分配金额" min-width="120" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.amount) }}</template></el-table-column>
            <el-table-column label="受限原因" min-width="160"><template #default="{ row }">{{ row.restrictedReason ?? '无' }}</template></el-table-column>
          </el-table>
        </div>
      </section>
      <section class="finance-section">
        <span class="finance-section-title">边界说明</span>
        <p>核销只连接既有资金与应收/应付目标，不新增现金发生额，不回写历史业务单据。</p>
      </section>
      <section class="finance-section">
        <span class="finance-section-title">审计</span>
        <p>{{ record.auditSummary?.length ? '已有审计摘要' : '暂无审计摘要' }}</p>
      </section>
    </div>
  </MasterDataTableView>
</template>
