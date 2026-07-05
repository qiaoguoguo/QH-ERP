<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  returnRefundReversalApi,
  type ResourceId,
  type ReversalSourceView,
  type ReversalStatus,
  type SettlementAdjustmentDetail,
  type SettlementAdjustmentPayload,
  type SettlementAdjustmentSource,
  type SettlementAdjustmentSourceType,
  type SettlementAdjustmentType,
  type SettlementAdjustmentUpdatePayload,
  type SettlementSide,
} from '../../shared/api/returnRefundReversalApi'
import {
  compareFinanceAmount,
  financeErrorMessage,
  formatFinanceAmount,
  isPositiveFinanceAmount,
} from '../finance/financePageHelpers'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'

const allowedSourceTypes: Array<{ label: string; value: SettlementAdjustmentSourceType }> = [
  { label: '销售退货', value: 'SALES_RETURN' },
  { label: '采购退货', value: 'PURCHASE_RETURN' },
  { label: '收款记录', value: 'RECEIPT' },
  { label: '付款记录', value: 'PAYMENT' },
  { label: '往来冲减', value: 'SETTLEMENT_ADJUSTMENT' },
]

const route = useRoute()
const router = useRouter()
const routeId = computed(() => route.params.id as string | undefined)
const isEdit = computed(() => Boolean(routeId.value))
const title = computed(() => (isEdit.value ? '编辑往来冲减' : '新建往来冲减'))
const description = computed(() => (isEdit.value ? '更新草稿往来冲减的业务日期、金额和备注。' : '从已过账来源选择可冲目标创建往来冲减或退款记录草稿。'))
const form = reactive({
  settlementSide: 'RECEIVABLE' as SettlementSide,
  adjustmentType: 'REFUND' as SettlementAdjustmentType,
  sourceType: 'RECEIPT' as SettlementAdjustmentSourceType,
  sourceId: '' as ResourceId | '',
  targetId: '' as ResourceId | '',
  businessDate: '',
  amount: '',
  clientRequestId: '',
  remark: '',
})
const sourceFilters = reactive<{
  keyword: string
  settlementSide?: SettlementSide
  sourceType?: SettlementAdjustmentSourceType
}>({
  keyword: '',
  settlementSide: 'RECEIVABLE',
  sourceType: 'RECEIPT',
})
const sources = ref<SettlementAdjustmentSource[]>([])
const editDetail = ref<SettlementAdjustmentDetail | null>(null)
const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const submitError = ref('')
const nonEditableStatus = ref<ReversalStatus | null>(null)

const canEditForm = computed(() => !isEdit.value || nonEditableStatus.value === null)
const selectedSource = computed(() => sources.value.find((source) =>
  source.sourceType === form.sourceType
    && String(source.sourceId) === String(form.sourceId)
    && String(source.targetId) === String(form.targetId)))
const nonEditableStatusText = computed(() => {
  if (nonEditableStatus.value === 'POSTED') {
    return '已过账'
  }
  if (nonEditableStatus.value === 'CANCELLED') {
    return '已取消'
  }
  return ''
})
const sourceDisplayText = computed(() => {
  if (isEdit.value && sourceRestricted(editDetail.value?.source)) {
    return editDetail.value?.source.restrictedMessage || '来源无查看权限'
  }
  return selectedSource.value?.sourceNo || editDetail.value?.source.sourceNo || '-'
})
const targetDisplayText = computed(() => selectedSource.value?.targetNo || editDetail.value?.targetNo || '-')
const adjustableAmount = computed(() => selectedSource.value?.adjustableAmount || editDetail.value?.targetAdjustableAmountBefore || '')

function sourceRestricted(source?: ReversalSourceView | null) {
  return !source || source.restricted || !source.canViewSource
}

function settlementSideText(value: SettlementSide | string) {
  return value === 'PAYABLE' ? '应付冲减' : '应收冲减'
}

function adjustmentTypeText(value: SettlementAdjustmentType | string) {
  const labels: Record<string, string> = {
    RETURN_OFFSET: '退货冲减',
    REFUND: '退款记录',
    PAYMENT_OFFSET: '付款冲减',
  }
  return labels[value] ?? value
}

function sourceTypeText(value: SettlementAdjustmentSourceType | string) {
  return allowedSourceTypes.find((item) => item.value === value)?.label ?? value
}

async function loadSources() {
  const page = await returnRefundReversalApi.settlementAdjustmentSources.list({
    keyword: sourceFilters.keyword,
    settlementSide: sourceFilters.settlementSide,
    sourceType: sourceFilters.sourceType,
    page: 1,
    pageSize: 20,
  })
  sources.value = pageItems(page)
  if (!isEdit.value) {
    syncSourceSelection()
  }
}

async function loadDetail() {
  if (!routeId.value) {
    return
  }
  const detail = await returnRefundReversalApi.settlementAdjustments.get(routeId.value)
  editDetail.value = detail
  form.settlementSide = detail.settlementSide
  form.adjustmentType = detail.adjustmentType
  form.sourceType = detail.source.sourceType as SettlementAdjustmentSourceType
  form.sourceId = sourceRestricted(detail.source) ? '' : detail.source.sourceId ?? ''
  form.targetId = detail.targetId
  form.businessDate = detail.businessDate
  form.amount = detail.amount
  form.clientRequestId = detail.clientRequestId ?? `settlement-adjustment-${detail.id}`
  form.remark = detail.remark ?? ''
  if (detail.status !== 'DRAFT') {
    nonEditableStatus.value = detail.status
    return
  }
  nonEditableStatus.value = null
}

async function loadForm() {
  loading.value = true
  error.value = ''
  nonEditableStatus.value = null
  try {
    if (isEdit.value) {
      await loadDetail()
    } else {
      form.businessDate = new Date().toISOString().slice(0, 10)
      form.clientRequestId = `settlement-adjustment-${Date.now()}`
      await loadSources()
    }
  } catch (caught) {
    sources.value = []
    error.value = financeErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function chooseSource(source: SettlementAdjustmentSource) {
  form.settlementSide = source.settlementSide
  form.sourceType = source.sourceType
  form.sourceId = source.sourceId
  form.targetId = source.targetId
  form.businessDate = source.businessDate
}

function clearSourceSelection() {
  form.sourceId = ''
  form.targetId = ''
}

function matchesCurrentSource(source: SettlementAdjustmentSource) {
  return source.sourceType === form.sourceType
    && String(source.sourceId) === String(form.sourceId)
    && String(source.targetId) === String(form.targetId)
}

function syncSourceSelection() {
  const currentVisibleSource = sources.value.find(matchesCurrentSource)
  if (currentVisibleSource) {
    chooseSource(currentVisibleSource)
    return
  }
  const firstSource = sources.value[0]
  if (firstSource) {
    chooseSource(firstSource)
    return
  }
  clearSourceSelection()
}

async function searchSources() {
  if (isEdit.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    await loadSources()
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function validateAmount() {
  if (!isPositiveFinanceAmount(form.amount)) {
    return '冲减金额必须大于 0，且最多 2 位小数'
  }
  if (adjustableAmount.value) {
    const compare = compareFinanceAmount(form.amount, adjustableAmount.value)
    if (compare !== null && compare > 0) {
      return '冲减金额不能超过可冲金额'
    }
  }
  return null
}

function buildPayload(): SettlementAdjustmentUpdatePayload | null {
  if (!canEditForm.value) {
    submitError.value = `当前往来冲减${nonEditableStatusText.value}，不可编辑`
    return null
  }
  if (!form.businessDate) {
    submitError.value = '业务日期不能为空'
    return null
  }
  const amountError = validateAmount()
  if (amountError) {
    submitError.value = amountError
    return null
  }
  const currentSource = isEdit.value ? null : selectedSource.value
  if (!isEdit.value && !currentSource) {
    submitError.value = '请选择候选来源'
    return null
  }

  const payload: SettlementAdjustmentUpdatePayload = {
    businessDate: form.businessDate,
    amount: form.amount,
    clientRequestId: form.clientRequestId || `settlement-adjustment-${Date.now()}`,
    remark: form.remark,
  }

  if (!isEdit.value && currentSource) {
    payload.settlementSide = currentSource.settlementSide
    payload.adjustmentType = form.adjustmentType
    payload.sourceType = currentSource.sourceType
    payload.sourceId = currentSource.sourceId
    payload.targetId = currentSource.targetId
  }
  return payload
}

async function submit() {
  if (submitting.value) {
    return
  }
  submitError.value = ''
  const payload = buildPayload()
  if (!payload) {
    return
  }

  submitting.value = true
  try {
    const detail = isEdit.value && routeId.value
      ? await returnRefundReversalApi.settlementAdjustments.update(routeId.value, payload)
      : await returnRefundReversalApi.settlementAdjustments.create(payload as SettlementAdjustmentPayload)
    await router.push({ name: 'finance-settlement-adjustment-detail', params: { id: String(detail.id) } })
  } catch (caught) {
    submitError.value = financeErrorMessage(caught)
  } finally {
    submitting.value = false
  }
}

function backToList() {
  void router.push({ name: 'finance-settlement-adjustments' })
}

function returnToDetail() {
  if (!routeId.value) {
    return
  }
  void router.push({ name: 'finance-settlement-adjustment-detail', params: { id: routeId.value } })
}

onMounted(() => {
  void loadForm()
})
</script>

<template>
  <MasterDataTableView :title="title" :description="description">
    <template #actions>
      <el-button @click="backToList">返回列表</el-button>
    </template>

    <template v-if="!isEdit" #filters>
      <el-form class="query-form" inline>
        <el-form-item label="候选来源">
          <el-input
            v-model="sourceFilters.keyword"
            name="settlement-adjustment-source-keyword"
            clearable
            placeholder="来源单号或目标单号"
          />
        </el-form-item>
        <el-form-item label="往来方向">
          <el-select v-model="sourceFilters.settlementSide" clearable placeholder="全部方向" style="width: 130px">
            <el-option label="应收冲减" value="RECEIVABLE" />
            <el-option label="应付冲减" value="PAYABLE" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源类型">
          <el-select v-model="sourceFilters.sourceType" clearable placeholder="全部来源" style="width: 140px">
            <el-option
              v-for="item in allowedSourceTypes"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-settlement-adjustment-sources" type="primary" @click="searchSources">查询来源</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="submitError" class="state-alert" type="error" :title="submitError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="往来冲减表单加载中" :closable="false" />
    </template>

    <el-result
      v-if="!canEditForm"
      icon="warning"
      :title="`当前往来冲减${nonEditableStatusText}，不可编辑`"
      sub-title="已过账或已取消的往来冲减只能查看，不能继续修改草稿内容。"
    >
      <template #extra>
        <el-button data-test="return-settlement-adjustment-detail" type="primary" @click="returnToDetail">返回详情</el-button>
      </template>
    </el-result>

    <div v-else class="form-layout">
      <el-card v-if="!isEdit" class="section-card" shadow="never">
        <template #header>候选来源</template>
        <el-empty v-if="!loading && sources.length === 0" description="暂无可冲减来源" />
        <div v-else class="table-scroll">
          <el-table :data="sources" :empty-text="loading ? '加载中' : '暂无可冲减来源'" stripe>
            <el-table-column label="选择" width="80">
              <template #default="{ row }">
                <el-radio
                  :model-value="`${form.sourceType}-${form.sourceId}-${form.targetId}`"
                  :label="`${row.sourceType}-${row.sourceId}-${row.targetId}`"
                  @change="chooseSource(row)"
                >
                  选择
                </el-radio>
              </template>
            </el-table-column>
            <el-table-column label="来源类型" min-width="110">
              <template #default="{ row }">{{ sourceTypeText(row.sourceType) }}</template>
            </el-table-column>
            <el-table-column prop="sourceNo" label="来源单号" min-width="160" show-overflow-tooltip />
            <el-table-column prop="targetNo" label="目标单号" min-width="150" show-overflow-tooltip />
            <el-table-column label="可冲金额" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatFinanceAmount(row.adjustableAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="businessDate" label="业务日期" min-width="110" />
          </el-table>
        </div>
      </el-card>

      <el-card class="section-card" shadow="never">
        <template #header>冲减信息</template>
        <el-form class="document-form" label-width="110px">
          <el-form-item label="来源">
            <span>{{ sourceDisplayText }}</span>
          </el-form-item>
          <el-form-item label="目标单号">
            <span>{{ targetDisplayText }}</span>
          </el-form-item>
          <el-form-item label="往来方向">
            <span>{{ settlementSideText(form.settlementSide) }}</span>
          </el-form-item>
          <el-form-item label="冲减类型">
            <el-select v-if="!isEdit" v-model="form.adjustmentType" style="width: 160px">
              <el-option label="退货冲减" value="RETURN_OFFSET" />
              <el-option label="退款记录" value="REFUND" />
              <el-option label="付款冲减" value="PAYMENT_OFFSET" />
            </el-select>
            <span v-else>{{ adjustmentTypeText(form.adjustmentType) }}</span>
          </el-form-item>
          <el-form-item label="原金额">
            <span>{{ formatFinanceAmount(selectedSource?.originalAmount || editDetail?.targetOriginalAmount) }}</span>
          </el-form-item>
          <el-form-item label="已冲减金额">
            <span>{{ formatFinanceAmount(selectedSource?.adjustedAmount || editDetail?.targetAdjustedAmountBefore) }}</span>
          </el-form-item>
          <el-form-item label="可冲金额">
            <span>{{ formatFinanceAmount(adjustableAmount) }}</span>
          </el-form-item>
          <el-form-item label="业务日期">
            <el-input
              v-model="form.businessDate"
              name="settlement-adjustment-business-date"
              placeholder="YYYY-MM-DD"
              style="width: 180px"
            />
          </el-form-item>
          <el-form-item label="冲减金额">
            <el-input
              v-model="form.amount"
              name="settlement-adjustment-amount"
              placeholder="0.00"
              style="width: 180px"
            />
          </el-form-item>
          <el-form-item label="备注">
            <el-input
              v-model="form.remark"
              name="settlement-adjustment-remark"
              type="textarea"
              :rows="2"
              placeholder="往来冲减说明"
            />
          </el-form-item>
        </el-form>
      </el-card>

      <div class="form-actions">
        <el-button @click="backToList">取消</el-button>
        <el-button
          data-test="submit-settlement-adjustment"
          type="primary"
          :loading="submitting"
          :disabled="submitting"
          @click="submit"
        >
          保存草稿
        </el-button>
      </div>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.form-layout {
  display: grid;
  gap: 14px;
}

.section-card {
  border-radius: 6px;
}

.document-form {
  max-width: 720px;
}

.table-scroll {
  overflow-x: auto;
}

.numeric-cell {
  display: inline-block;
  min-width: 72px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.form-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}
</style>
