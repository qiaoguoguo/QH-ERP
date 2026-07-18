<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  financeSettlementApi,
  type AdvanceFundRecord,
  type SettlementDirection,
  type SettlementTargetRecord,
  type TargetType,
} from '../../shared/api/financeSettlementApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import {
  compareFinanceAmount,
  financeErrorMessage,
  financeSourceTypeText,
  formatFinanceAmount,
  ownershipTypeText,
  settlementStatusText,
} from './financePageHelpers'
import './Finance028Shared.css'

const filters = reactive<{ direction: SettlementDirection; partnerId: string; ownershipType: 'PROJECT' | 'PUBLIC'; projectId: string }>({
  direction: 'CUSTOMER',
  partnerId: '8',
  ownershipType: 'PROJECT',
  projectId: '18',
})
const fundsPagination = reactive({ page: 1, pageSize: 50, total: 0 })
const targetsPagination = reactive({ page: 1, pageSize: 50, total: 0 })
const funds = ref<AdvanceFundRecord[]>([])
const targets = ref<SettlementTargetRecord[]>([])
const selectedFund = ref<AdvanceFundRecord | null>(null)
const selectedTarget = ref<SettlementTargetRecord | null>(null)
const allocationAmount = ref('')
const loading = ref(false)
const error = ref('')
const submitting = ref(false)

const amountError = computed(() => {
  if (!allocationAmount.value.trim() || !selectedFund.value || !selectedTarget.value) {
    return ''
  }
  if (!/^\d+(\.\d{1,2})?$/.test(allocationAmount.value.trim())) {
    return '本次核销金额最多保留两位小数'
  }
  if (compareFinanceAmount(allocationAmount.value, '0.00') !== 1) {
    return '本次核销金额必须大于 0'
  }
  const fundCompared = compareFinanceAmount(allocationAmount.value, selectedFund.value.availableAmount)
  const targetCompared = compareFinanceAmount(allocationAmount.value, selectedTarget.value.unsettledAmount)
  if (fundCompared === null || targetCompared === null) {
    return '本次核销金额格式不正确'
  }
  if (fundCompared > 0 || targetCompared > 0) {
    return '本次核销金额不能超过资金可用余额或目标未结余额'
  }
  return ''
})
const submitDisabledReason = computed(() => {
  if (!selectedFund.value || !selectedTarget.value) {
    return '请选择资金和核销目标'
  }
  if (!allocationAmount.value.trim()) {
    return '请填写本次核销金额'
  }
  return amountError.value
})
const canSubmit = computed(() => !submitting.value && !submitDisabledReason.value)

function basePoolParams(page: number, pageSize: number) {
  return {
    direction: filters.direction,
    partnerId: filters.partnerId ? filters.partnerId : undefined,
    ownershipType: filters.ownershipType,
    projectId: filters.ownershipType === 'PROJECT' && filters.projectId ? filters.projectId : undefined,
    page,
    pageSize,
  }
}

async function loadPools() {
  loading.value = true
  error.value = ''
  try {
    const [fundPage, targetPage] = await Promise.all([
      financeSettlementApi.settlementWorkbench.funds(basePoolParams(fundsPagination.page, fundsPagination.pageSize)),
      financeSettlementApi.settlementWorkbench.targets(basePoolParams(targetsPagination.page, targetsPagination.pageSize)),
    ])
    funds.value = pageItems(fundPage)
    targets.value = pageItems(targetPage)
    fundsPagination.total = Number(fundPage.total)
    targetsPagination.total = Number(targetPage.total)
  } catch (caught) {
    funds.value = []
    targets.value = []
    error.value = financeErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function searchPools() {
  fundsPagination.page = 1
  targetsPagination.page = 1
  selectedFund.value = null
  selectedTarget.value = null
  allocationAmount.value = ''
  void loadPools()
}

function selectFund(record: AdvanceFundRecord) {
  selectedFund.value = record
  if (!allocationAmount.value.trim()) {
    allocationAmount.value = String(record.availableAmount)
  }
}

function selectTarget(record: SettlementTargetRecord) {
  selectedTarget.value = record
  allocationAmount.value = String(record.unsettledAmount)
}

function changeFundsPage(page: number) {
  fundsPagination.page = page
  void loadPools()
}

function changeTargetsPage(page: number) {
  targetsPagination.page = page
  void loadPools()
}

async function submitAllocation() {
  if (!canSubmit.value || !selectedFund.value || !selectedTarget.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await financeSettlementApi.settlementWorkbench.create({
      direction: filters.direction,
      partnerId: filters.partnerId,
      ownershipType: filters.ownershipType,
      projectId: filters.ownershipType === 'PROJECT' ? filters.projectId : undefined,
      funds: [{
        fundType: filters.direction === 'CUSTOMER' ? 'ADVANCE_RECEIPT' : 'PREPAYMENT',
        fundId: selectedFund.value.id,
        version: selectedFund.value.version,
        amount: allocationAmount.value.trim(),
      }],
      targets: [{
        targetType: selectedTarget.value.targetType as TargetType,
        targetId: selectedTarget.value.targetId,
        version: selectedTarget.value.version,
        amount: allocationAmount.value.trim(),
      }],
      idempotencyKey: `settlement-allocation-${Date.now()}`,
    })
    await loadPools()
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  } finally {
    submitting.value = false
  }
}

onMounted(loadPools)
</script>

<template>
  <MasterDataTableView title="对账核销工作台" description="按往来方和项目/公共归属核销资金余额与应收应付，不新增现金发生额。">
    <template #actions>
      <el-button data-test="post-settlement-allocation" type="primary" :loading="submitting" :disabled="!canSubmit" @click="submitAllocation">保存核销草稿</el-button>
    </template>
    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="方向">
          <el-segmented v-model="filters.direction" :options="[{ label: '客户', value: 'CUSTOMER' }, { label: '供应商', value: 'SUPPLIER' }]" @change="searchPools" />
        </el-form-item>
        <el-form-item label="往来方"><el-input v-model="filters.partnerId" placeholder="选择往来方" /></el-form-item>
        <el-form-item label="项目/公共">
          <el-select v-model="filters.ownershipType" placeholder="选择归属" @change="searchPools">
            <el-option label="项目" value="PROJECT" />
            <el-option label="公共" value="PUBLIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目"><el-input v-model="filters.projectId" :disabled="filters.ownershipType === 'PUBLIC'" placeholder="选择项目" /></el-form-item>
        <el-form-item><el-button type="primary" @click="searchPools">查询</el-button></el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" type="info" title="候选池加载中" :closable="false" />
      <el-alert v-if="submitDisabledReason" type="warning" :title="submitDisabledReason" :closable="false" />
    </template>

    <div class="finance-two-column-workbench">
      <section class="finance-section">
        <span class="finance-section-title">可用资金池</span>
        <el-empty v-if="!loading && funds.length === 0" description="无可用资金" />
        <div class="table-scroll">
          <el-table :data="funds" :empty-text="loading ? '加载中' : '无可用资金'" height="260">
            <el-table-column prop="fundNo" label="资金单号" min-width="140" show-overflow-tooltip />
            <el-table-column label="资金类型" min-width="110"><template #default>{{ filters.direction === 'CUSTOMER' ? '预收款' : '预付款' }}</template></el-table-column>
            <el-table-column prop="businessDate" label="业务日期" min-width="110" />
            <el-table-column label="原金额" min-width="110" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.amount) }}</template></el-table-column>
            <el-table-column label="已核销金额" min-width="120" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.allocatedAmount) }}</template></el-table-column>
            <el-table-column label="可用余额" min-width="120" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.availableAmount) }}</template></el-table-column>
            <el-table-column label="项目/公共" min-width="130"><template #default="{ row }">{{ ownershipTypeText(row.ownershipType) }} {{ row.projectName ?? '' }}</template></el-table-column>
            <el-table-column label="状态" min-width="100"><template #default="{ row }">{{ settlementStatusText(row.status) }}</template></el-table-column>
            <el-table-column label="受限原因" min-width="130"><template #default="{ row }">{{ row.restrictedReason ?? '无' }}</template></el-table-column>
            <el-table-column label="操作" fixed="right" min-width="100">
              <template #default="{ row }">
                <el-button data-test="select-settlement-fund" size="small" text :type="selectedFund?.id === row.id ? 'primary' : undefined" @click="selectFund(row)">
                  {{ selectedFund?.id === row.id ? '已选' : '选择' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <el-pagination class="table-pagination" layout="total, prev, pager, next" :total="fundsPagination.total" :page-size="fundsPagination.pageSize" :current-page="fundsPagination.page" @current-change="changeFundsPage" />
      </section>

      <section class="finance-section">
        <span class="finance-section-title">可核销目标池</span>
        <el-empty v-if="!loading && targets.length === 0" description="无可核销目标" />
        <div class="table-scroll">
          <el-table :data="targets" :empty-text="loading ? '加载中' : '无可核销目标'" height="260">
            <el-table-column label="目标类型" min-width="110"><template #default="{ row }">{{ financeSourceTypeText(row.targetType) }}</template></el-table-column>
            <el-table-column prop="targetNo" label="目标单号" min-width="140" show-overflow-tooltip />
            <el-table-column prop="sourceSummary" label="来源摘要" min-width="160" show-overflow-tooltip />
            <el-table-column prop="businessDate" label="业务日期" min-width="110" />
            <el-table-column label="原金额" min-width="110" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.originalAmount) }}</template></el-table-column>
            <el-table-column label="已收/已付" min-width="110" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.settledAmount) }}</template></el-table-column>
            <el-table-column label="已冲减" min-width="110" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.adjustedAmount) }}</template></el-table-column>
            <el-table-column label="已核销" min-width="110" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.allocatedAmount) }}</template></el-table-column>
            <el-table-column label="未结余额" min-width="120" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.unsettledAmount) }}</template></el-table-column>
            <el-table-column label="状态" min-width="100"><template #default="{ row }">{{ settlementStatusText(row.status) }}</template></el-table-column>
            <el-table-column label="受限原因" min-width="130"><template #default="{ row }">{{ row.restrictedReason ?? '无' }}</template></el-table-column>
            <el-table-column label="操作" fixed="right" min-width="100">
              <template #default="{ row }">
                <el-button data-test="select-settlement-target" size="small" text :type="selectedTarget?.targetId === row.targetId ? 'primary' : undefined" @click="selectTarget(row)">
                  {{ selectedTarget?.targetId === row.targetId ? '已选' : '选择' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <el-pagination class="table-pagination" layout="total, prev, pager, next" :total="targetsPagination.total" :page-size="targetsPagination.pageSize" :current-page="targetsPagination.page" @current-change="changeTargetsPage" />
      </section>
    </div>

    <div class="finance-workbench-summary">
      <span>资金：{{ selectedFund?.fundNo ?? '未选择' }}</span>
      <span>目标：{{ selectedTarget?.targetNo ?? '未选择' }}</span>
      <span>剩余可分配：{{ selectedFund ? formatFinanceAmount(selectedFund.availableAmount) : '0.00' }}</span>
      <span>目标未结：{{ selectedTarget ? formatFinanceAmount(selectedTarget.unsettledAmount) : '0.00' }}</span>
      <el-input v-model="allocationAmount" name="settlement-allocation-amount" placeholder="本次核销金额" style="max-width: 180px" />
      <span v-if="amountError" class="finance-danger-note">{{ amountError }}</span>
    </div>
  </MasterDataTableView>
</template>
