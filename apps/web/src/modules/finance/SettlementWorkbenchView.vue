<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  financeSettlementApi,
  type AdvanceFundRecord,
  type FundType,
  type SettlementDirection,
  type SettlementTargetRecord,
  type TargetType,
} from '../../shared/api/financeSettlementApi'
import type { OwnershipType, ResourceId } from '../../shared/api/financeStage028ApiCore'
import { useAuthStore } from '../../stores/authStore'
import { confirmAction } from '../../shared/ui/confirmDialog'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import {
  addFinanceAmounts,
  compareFinanceAmount,
  financeErrorMessage,
  financePermissions,
  financeSourceTypeText,
  formatFinanceAmount,
  normalizeOptionalId,
  ownershipTypeText,
  settlementStatusText,
  subtractFinanceAmounts,
} from './financePageHelpers'
import './Finance028Shared.css'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const queryDirection = route.query.direction === 'SUPPLIER' ? 'SUPPLIER' : 'CUSTOMER'
const queryOwnershipType = route.query.ownershipType === 'PROJECT' || route.query.ownershipType === 'PUBLIC'
  ? route.query.ownershipType
  : ''
const queryFundType = isFundType(route.query.fundType) ? route.query.fundType : ''

const filters = reactive<{
  direction: SettlementDirection
  fundType: FundType | ''
  fundId: string
  partnerId: string
  ownershipType: OwnershipType | ''
  projectId: string
}>({
  direction: queryDirection,
  fundType: queryFundType,
  fundId: String(route.query.fundId ?? ''),
  partnerId: String(route.query.partnerId ?? ''),
  ownershipType: queryOwnershipType,
  projectId: String(route.query.projectId ?? ''),
})
const fundsPagination = reactive({ page: 1, pageSize: 50, total: 0 })
const targetsPagination = reactive({ page: 1, pageSize: 50, total: 0 })
const funds = ref<AdvanceFundRecord[]>([])
const targets = ref<SettlementTargetRecord[]>([])
const selectedFund = ref<AdvanceFundRecord | null>(null)
const selectedTargets = ref<SettlementTargetRecord[]>([])
const targetAmounts = reactive<Record<string, string>>({})
const loading = ref(false)
const error = ref('')
const submitting = ref(false)

const selectedTargetTotal = computed(() => {
  const total = addFinanceAmounts(selectedTargets.value.map((target) => targetAmounts[targetKey(target)] || '0.00'))
  return total ?? '0.00'
})
const remainingAmount = computed(() => {
  if (!selectedFund.value) {
    return '0.00'
  }
  return subtractFinanceAmounts(selectedFund.value.availableAmount, selectedTargetTotal.value) ?? '0.00'
})
const amountError = computed(() => {
  if (!selectedFund.value || selectedTargets.value.length === 0) {
    return ''
  }
  for (const target of selectedTargets.value) {
    const amount = (targetAmounts[targetKey(target)] || '').trim()
    if (!/^\d+(\.\d{1,2})?$/.test(amount)) {
      return '本次核销金额最多保留两位小数'
    }
    const positive = compareFinanceAmount(amount, '0.00')
    if (positive === null || positive !== 1) {
      return '每个核销目标金额必须大于 0'
    }
    const targetCompared = compareFinanceAmount(amount, target.unsettledAmount)
    if (targetCompared === null || targetCompared > 0) {
      return '本次核销金额不能超过资金可用余额或目标未结余额'
    }
  }
  const fundCompared = compareFinanceAmount(selectedTargetTotal.value, selectedFund.value.availableAmount)
  if (fundCompared === null || fundCompared > 0) {
    return '本次核销金额不能超过资金可用余额或目标未结余额'
  }
  return ''
})
const submitDisabledReason = computed(() => {
  if (!authStore.hasPermission(financePermissions.settlementAllocationCreate)) {
    return '缺少核销保存权限，仅可查看候选池'
  }
  if (!selectedFund.value || selectedTargets.value.length === 0) {
    return '请选择资金和核销目标'
  }
  return amountError.value
})
const canSubmit = computed(() => !submitting.value && !submitDisabledReason.value)
const selectedFundType = computed<FundType>(() => (
  filters.fundType || (filters.direction === 'CUSTOMER' ? 'ADVANCE_RECEIPT' : 'PREPAYMENT')
) as FundType)
const selectedPartnerText = computed(() => selectedFund.value?.partnerName || '选择资金后带入')
const selectedOwnershipText = computed(() => {
  const ownership = filters.ownershipType || selectedFund.value?.ownershipType
  if (!ownership) {
    return '选择资金后带入'
  }
  const projectName = selectedFund.value?.projectName ?? ''
  return ownership === 'PROJECT' ? `${ownershipTypeText(ownership)} ${projectName || '待选择项目'}` : ownershipTypeText(ownership)
})

function isFundType(value: unknown): value is FundType {
  return value === 'ADVANCE_RECEIPT' || value === 'PREPAYMENT' || value === 'RECEIPT' || value === 'PAYMENT'
}

function targetKey(record: SettlementTargetRecord) {
  return `${record.targetType}-${record.targetId}`
}

function isTargetSelected(record: SettlementTargetRecord) {
  return selectedTargets.value.some((item) => targetKey(item) === targetKey(record))
}

function poolParams(page: number, pageSize: number) {
  return {
    direction: filters.direction,
    fundType: filters.fundType || undefined,
    fundId: normalizeOptionalId(filters.fundId),
    partnerId: normalizeOptionalId(filters.partnerId),
    ownershipType: filters.ownershipType || undefined,
    projectId: filters.ownershipType === 'PROJECT' ? normalizeOptionalId(filters.projectId) : undefined,
    page,
    pageSize,
  }
}

async function loadPools() {
  loading.value = true
  error.value = ''
  try {
    const [fundPage, targetPage] = await Promise.all([
      financeSettlementApi.settlementWorkbench.funds(poolParams(fundsPagination.page, fundsPagination.pageSize)),
      financeSettlementApi.settlementWorkbench.targets(poolParams(targetsPagination.page, targetsPagination.pageSize)),
    ])
    funds.value = pageItems(fundPage)
    targets.value = pageItems(targetPage)
    fundsPagination.total = Number(fundPage.total)
    targetsPagination.total = Number(targetPage.total)
    keepVisibleSelections()
  } catch (caught) {
    funds.value = []
    targets.value = []
    error.value = financeErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function keepVisibleSelections() {
  if (filters.fundId && !selectedFund.value) {
    const fund = funds.value.find((item) => String(item.id) === filters.fundId)
    if (fund) {
      selectFund(fund)
    }
  }
  selectedTargets.value = selectedTargets.value.map((selected) => (
    targets.value.find((target) => targetKey(target) === targetKey(selected)) ?? selected
  ))
}

function searchPools() {
  fundsPagination.page = 1
  targetsPagination.page = 1
  selectedFund.value = null
  selectedTargets.value = []
  Object.keys(targetAmounts).forEach((key) => delete targetAmounts[key])
  void loadPools()
}

function selectFund(record: AdvanceFundRecord) {
  selectedFund.value = record
  filters.fundId = String(record.id)
  if (!filters.partnerId) {
    const partnerId = (record as AdvanceFundRecord & { partnerId?: ResourceId }).partnerId
    if (partnerId !== undefined && partnerId !== null) {
      filters.partnerId = String(partnerId)
    }
  }
  if (!filters.ownershipType) {
    filters.ownershipType = record.ownershipType
  }
  const projectId = (record as AdvanceFundRecord & { projectId?: ResourceId | null }).projectId
  if (!filters.projectId && projectId !== undefined && projectId !== null) {
    filters.projectId = String(projectId)
  }
}

function selectTarget(record: SettlementTargetRecord) {
  const key = targetKey(record)
  if (isTargetSelected(record)) {
    selectedTargets.value = selectedTargets.value.filter((item) => targetKey(item) !== key)
    delete targetAmounts[key]
    return
  }
  selectedTargets.value = [...selectedTargets.value, record]
  targetAmounts[key] = targetAmounts[key] || String(record.unsettledAmount)
}

function changeTargetAmount(target: SettlementTargetRecord, value: unknown) {
  targetAmounts[targetKey(target)] = String(value)
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
  if (!canSubmit.value || !selectedFund.value) {
    return
  }
  if (!(await confirmAction('保存核销草稿？'))) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    const fundPartnerId = selectedFund.value.partnerId
      ?? selectedFund.value.customerId
      ?? selectedFund.value.supplierId
      ?? normalizeOptionalId(filters.partnerId)
    if (fundPartnerId === undefined) {
      error.value = '资金缺少往来方摘要，无法保存核销'
      return
    }
    const ownership = filters.ownershipType || selectedFund.value.ownershipType
    const projectId = ownership === 'PROJECT'
      ? normalizeOptionalId(filters.projectId) ?? selectedFund.value.projectId ?? null
      : null
    const settlementLines = selectedTargets.value.map((target) => ({
      targetType: target.targetType as TargetType,
      targetId: target.targetId,
      amount: targetAmounts[targetKey(target)].trim(),
    }))
    const result = await financeSettlementApi.settlementWorkbench.create({
      version: 0,
      settlementSide: filters.direction === 'CUSTOMER' ? 'RECEIVABLE' : 'PAYABLE',
      cashSourceType: filters.direction === 'CUSTOMER' ? 'RECEIPT' : 'PAYMENT',
      cashSourceId: selectedFund.value.id,
      businessDate: selectedFund.value.businessDate,
      direction: filters.direction,
      partnerId: fundPartnerId,
      ownershipType: ownership,
      projectId,
      funds: [{
        fundType: selectedFundType.value,
        fundId: selectedFund.value.id,
        version: selectedFund.value.version,
        amount: selectedTargetTotal.value,
      }],
      targets: selectedTargets.value.map((target) => ({
        targetType: target.targetType as TargetType,
        targetId: target.targetId,
        version: target.version,
        amount: targetAmounts[targetKey(target)].trim(),
      })),
      lines: settlementLines,
      idempotencyKey: `settlement-allocation-${Date.now()}`,
    })
    await router.push({
      name: 'finance-settlement-allocation-detail',
      params: { id: String(result.id) },
      query: route.query.returnTo ? { returnTo: String(route.query.returnTo) } : undefined,
    })
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
      <el-button data-test="save-settlement-allocation" type="primary" :loading="submitting" :disabled="!canSubmit" @click="submitAllocation">保存核销草稿</el-button>
    </template>
    <template #filters>
      <el-form class="query-form">
        <el-form-item label="方向">
          <el-segmented v-model="filters.direction" :options="[{ label: '客户', value: 'CUSTOMER' }, { label: '供应商', value: 'SUPPLIER' }]" @change="searchPools" />
        </el-form-item>
        <el-form-item label="资金类型">
          <el-select v-model="filters.fundType" clearable placeholder="全部资金类型" @change="searchPools">
            <el-option label="预收款" value="ADVANCE_RECEIPT" />
            <el-option label="预付款" value="PREPAYMENT" />
            <el-option label="收款" value="RECEIPT" />
            <el-option label="付款" value="PAYMENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="往来方"><el-input :model-value="selectedPartnerText" disabled placeholder="选择资金后带入往来方" /></el-form-item>
        <el-form-item label="项目/公共">
          <el-select v-model="filters.ownershipType" clearable placeholder="全部归属" @change="searchPools">
            <el-option label="项目" value="PROJECT" />
            <el-option label="公共" value="PUBLIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="归属摘要"><el-input :model-value="selectedOwnershipText" disabled placeholder="选择资金后带入项目/公共" /></el-form-item>
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
            <el-table-column label="资金类型" min-width="110"><template #default>{{ selectedFundType === 'ADVANCE_RECEIPT' ? '预收款' : selectedFundType === 'PREPAYMENT' ? '预付款' : financeSourceTypeText(selectedFundType) }}</template></el-table-column>
            <el-table-column prop="partnerName" label="往来方" min-width="150" show-overflow-tooltip />
            <el-table-column prop="businessDate" label="业务日期" min-width="110" />
            <el-table-column label="原金额" min-width="110" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.amount) }}</template></el-table-column>
            <el-table-column label="已核销金额" min-width="120" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.allocatedAmount) }}</template></el-table-column>
            <el-table-column label="可用余额" min-width="120" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.availableAmount) }}</template></el-table-column>
            <el-table-column label="项目/公共" min-width="130"><template #default="{ row }">{{ ownershipTypeText(row.ownershipType) }} {{ row.projectName ?? '' }}</template></el-table-column>
            <el-table-column label="状态" min-width="100"><template #default="{ row }">{{ settlementStatusText(row.status) }}</template></el-table-column>
            <el-table-column label="受限原因" min-width="130"><template #default="{ row }">{{ row.restrictedReason ?? '无' }}</template></el-table-column>
            <el-table-column label="操作" min-width="100">
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
            <el-table-column label="本次分配" min-width="150" align="right">
              <template #default="{ row }">
                <el-input
                  v-if="isTargetSelected(row)"
                  :name="`settlement-target-amount-${row.targetId}`"
                  :model-value="targetAmounts[targetKey(row)]"
                  placeholder="0.00"
                  @update:model-value="changeTargetAmount(row, $event)"
                />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" min-width="100"><template #default="{ row }">{{ settlementStatusText(row.status) }}</template></el-table-column>
            <el-table-column label="受限原因" min-width="130"><template #default="{ row }">{{ row.restrictedReason ?? '无' }}</template></el-table-column>
            <el-table-column label="操作" min-width="100">
              <template #default="{ row }">
                <el-button data-test="select-settlement-target" size="small" text :type="isTargetSelected(row) ? 'primary' : undefined" @click="selectTarget(row)">
                  {{ isTargetSelected(row) ? '已选' : '选择' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <el-pagination class="table-pagination" layout="total, prev, pager, next" :total="targetsPagination.total" :page-size="targetsPagination.pageSize" :current-page="targetsPagination.page" @current-change="changeTargetsPage" />
      </section>
    </div>

    <div class="finance-workbench-summary">
      <span>来源资金：{{ selectedFund?.fundNo ?? '未选择' }}</span>
      <span>已选目标 {{ selectedTargets.length }} 个</span>
      <span>本次核销：{{ formatFinanceAmount(selectedTargetTotal) }}</span>
      <span>剩余可分配 {{ formatFinanceAmount(remainingAmount) }}</span>
      <span v-if="amountError" class="finance-danger-note">{{ amountError }}</span>
    </div>
  </MasterDataTableView>
</template>
