<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type DocumentTaskRecord,
} from '../../../shared/api/documentPlatformApi'
import { salesFulfillmentApi, type SalesQuoteDetailRecord } from '../../../shared/api/salesFulfillmentApi'
import {
  salesProjectApi,
  type SalesOrderProjectContractCandidate,
  type SalesProjectContractType,
} from '../../../shared/api/salesProjectApi'
import { useAuthStore } from '../../../stores/authStore'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { pageItems } from '../../system/shared/pageHelpers'
import SalesDocumentTaskPanel from '../SalesDocumentTaskPanel.vue'
import {
  approvalStatusLabel,
  formatSalesDecimal,
  normalizeSalesId,
  projectSalesLabel,
  quoteLineRequiredDate,
  quoteLineUntaxedUnitPrice,
  quoteTaxIncludedAmount,
  quoteStatusLabel,
  salesFulfillmentErrorMessage,
  salesSourceChainLabel,
} from '../salesFulfillmentPageHelpers'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<SalesQuoteDetailRecord | null>(null)
const latestDocumentTask = ref<DocumentTaskRecord | null>(null)
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const conversionDialog = reactive({
  visible: false,
  action: 'order' as 'order' | 'contract',
  contractType: 'MAIN' as SalesProjectContractType,
  selectedProjectContractKey: '',
  keyword: '',
  candidates: [] as SalesOrderProjectContractCandidate[],
  loading: false,
  error: '',
})

const routeId = computed(() => normalizeSalesId(route.params.id))
const canSubmit = computed(() => Boolean(record.value?.allowedActions.includes('SUBMIT_APPROVAL')) && authStore.hasPermission('sales:quote:submit'))
const canCancel = computed(() => Boolean(record.value?.allowedActions.includes('CANCEL')) && authStore.hasPermission('sales:quote:cancel'))
const canConvert = computed(() => authStore.hasPermission('sales:quote:convert'))
const canConvertOrder = computed(() => Boolean(record.value?.allowedActions.includes('CONVERT_ORDER')) && canConvert.value)
const canConvertContract = computed(() => Boolean(record.value?.allowedActions.includes('CONVERT_CONTRACT')) && canConvert.value)
const canPrint = computed(() => (
  Boolean(record.value?.allowedActions.includes('PRINT'))
  && authStore.hasPermission('platform:document-task:create')
  && authStore.hasPermission('sales:document:print')
))

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await salesFulfillmentApi.quotes.get(routeId.value)
  } catch (caught) {
    record.value = null
    error.value = salesFulfillmentErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function projectContractKey(candidate: SalesOrderProjectContractCandidate) {
  return `${candidate.projectId}:${candidate.contractId}`
}

function projectContractLabel(candidate: SalesOrderProjectContractCandidate) {
  return [
    candidate.projectNo,
    candidate.projectName,
    '/',
    candidate.contractNo,
    candidate.contractName,
    candidate.contractType === 'SUPPLEMENT' ? '补充合同' : '主合同',
  ].filter(Boolean).join(' ')
}

function selectedConversionCandidate() {
  return conversionDialog.candidates.find((candidate) => (
    projectContractKey(candidate) === conversionDialog.selectedProjectContractKey
  ))
}

async function loadConversionCandidates(current: SalesQuoteDetailRecord, keyword = '') {
  conversionDialog.loading = true
  conversionDialog.error = ''
  try {
    const page = await salesProjectApi.listOrderLinkCandidates({
      customerId: current.customerId,
      keyword,
      page: 1,
      pageSize: 50,
    })
    conversionDialog.candidates = pageItems(page)
    const matching = conversionDialog.candidates.find((candidate) => (
      current.projectId ? String(candidate.projectId) === String(current.projectId) : true
    ))
    conversionDialog.selectedProjectContractKey = matching ? projectContractKey(matching) : ''
  } catch (caught) {
    conversionDialog.candidates = []
    conversionDialog.error = salesFulfillmentErrorMessage(caught)
  } finally {
    conversionDialog.loading = false
  }
}

async function searchConversionCandidates() {
  if (!record.value) {
    return
  }
  await loadConversionCandidates(record.value, conversionDialog.keyword)
}

async function submitApproval() {
  if (!record.value || actionLoading.value) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await salesFulfillmentApi.quotes.submitApproval(record.value.id, {
      version: record.value.version,
      reason: '报价确认',
      idempotencyKey: createIdempotencyKey('sales-quote-submit'),
    })
    await loadRecord()
  } catch (caught) {
    actionError.value = salesFulfillmentErrorMessage(caught)
    await loadRecord()
  } finally {
    actionLoading.value = false
  }
}

async function cancelQuote() {
  if (!record.value || actionLoading.value) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await salesFulfillmentApi.quotes.cancel(record.value.id, {
      version: record.value.version,
      reason: '客户取消',
      idempotencyKey: createIdempotencyKey('sales-quote-cancel'),
    })
    await loadRecord()
  } catch (caught) {
    actionError.value = salesFulfillmentErrorMessage(caught)
    await loadRecord()
  } finally {
    actionLoading.value = false
  }
}

async function convertOrder() {
  if (!record.value || actionLoading.value) {
    return
  }
  if (record.value.projectId) {
    conversionDialog.visible = true
    conversionDialog.action = 'order'
    conversionDialog.contractType = 'MAIN'
    conversionDialog.selectedProjectContractKey = ''
    conversionDialog.keyword = ''
    await loadConversionCandidates(record.value)
    return
  }
  await submitOrderConversion(null, null)
}

async function submitOrderConversion(
  projectId: SalesOrderProjectContractCandidate['projectId'] | null,
  contractId: SalesOrderProjectContractCandidate['contractId'] | null,
) {
  if (!record.value) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await salesFulfillmentApi.quotes.convertOrder(record.value.id, {
      version: record.value.version,
      projectId,
      contractId,
      idempotencyKey: createIdempotencyKey('sales-quote-convert-order'),
    })
    conversionDialog.visible = false
    await loadRecord()
  } catch (caught) {
    actionError.value = salesFulfillmentErrorMessage(caught)
    await loadRecord()
  } finally {
    actionLoading.value = false
  }
}

async function convertContract() {
  if (!record.value || !record.value.projectId || actionLoading.value) {
    return
  }
  conversionDialog.visible = true
  conversionDialog.action = 'contract'
  conversionDialog.contractType = 'MAIN'
  conversionDialog.selectedProjectContractKey = ''
  conversionDialog.keyword = ''
  await loadConversionCandidates(record.value)
}

async function submitContractConversion(payload: {
  projectId: SalesOrderProjectContractCandidate['projectId']
  contractType: SalesProjectContractType
  mainContractId?: SalesOrderProjectContractCandidate['contractId'] | null
}) {
  if (!record.value) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await salesFulfillmentApi.quotes.convertContract(record.value.id, {
      version: record.value.version,
      ...payload,
      idempotencyKey: createIdempotencyKey('sales-quote-convert-contract'),
    })
    conversionDialog.visible = false
    await loadRecord()
  } catch (caught) {
    actionError.value = salesFulfillmentErrorMessage(caught)
    await loadRecord()
  } finally {
    actionLoading.value = false
  }
}

async function confirmQuoteConversion() {
  if (!record.value || actionLoading.value) {
    return
  }
  const selected = selectedConversionCandidate()
  if (conversionDialog.action === 'order') {
    if (!selected) {
      conversionDialog.error = '项目报价转订单必须选择有效项目和同客户有效合同'
      return
    }
    await submitOrderConversion(selected.projectId, selected.contractId)
    return
  }
  if (conversionDialog.contractType === 'SUPPLEMENT' && !selected) {
    conversionDialog.error = '补充合同必须选择主合同'
    return
  }
  const projectId = selected?.projectId ?? record.value.projectId
  if (!projectId) {
    conversionDialog.error = '项目报价转合同必须有项目'
    return
  }
  await submitContractConversion({
    projectId,
    contractType: conversionDialog.contractType,
    ...(conversionDialog.contractType === 'SUPPLEMENT' ? { mainContractId: selected?.contractId ?? null } : {}),
  })
}

async function printQuote() {
  if (!record.value || actionLoading.value) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    latestDocumentTask.value = await documentPlatformApi.printTasks.createSalesQuote(record.value.id, {
      idempotencyKey: createIdempotencyKey('sales-quote-print'),
    })
  } catch (caught) {
    actionError.value = salesFulfillmentErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(loadRecord)
</script>

<template>
  <MasterDataTableView
    title="销售报价详情"
    description="审批状态、转换来源、税价和来源链分开展示。"
  >
    <template #actions>
      <div class="header-actions">
        <el-button @click="router.push({ name: 'sales-quotes' })">返回列表</el-button>
        <el-button v-if="canSubmit" data-test="submit-sales-quote-detail" type="success" :loading="actionLoading" @click="submitApproval">
          提交审批
        </el-button>
        <el-button v-if="canCancel" data-test="cancel-sales-quote-detail" type="danger" :loading="actionLoading" @click="cancelQuote">
          取消
        </el-button>
        <el-button v-if="canConvertOrder" data-test="convert-sales-quote-order" type="primary" :loading="actionLoading" @click="convertOrder">
          转销售订单
        </el-button>
        <el-button v-if="canConvertContract" data-test="convert-sales-quote-contract" :loading="actionLoading" @click="convertContract">
          转合同
        </el-button>
        <el-button v-if="canPrint" data-test="print-sales-quote" :loading="actionLoading" @click="printQuote">
          打印
        </el-button>
      </div>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
      <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
      <el-alert v-if="loading" class="page-alert" type="info" title="销售报价详情加载中" :closable="false" />
      <SalesDocumentTaskPanel :task="latestDocumentTask" />
    </template>

    <div v-if="record" class="detail-body">
      <section class="summary-strip">
        <div><span>报价号</span><strong>{{ record.quoteNo }}</strong></div>
        <div><span>销售类型</span><strong>{{ projectSalesLabel(record) }}</strong></div>
        <div><span>业务状态</span><strong>{{ quoteStatusLabel(record.status) }}</strong></div>
        <div><span>审批状态</span><strong>{{ approvalStatusLabel(record.approvalStatus) }}</strong></div>
        <div><span>含税金额</span><strong>{{ formatSalesDecimal(quoteTaxIncludedAmount(record)) }} {{ record.currency }}</strong></div>
      </section>

      <dl class="detail-list">
        <dt>客户</dt><dd>{{ record.customerCode }} {{ record.customerName }}</dd>
        <dt>来源链</dt><dd>{{ salesSourceChainLabel(true) }}</dd>
        <dt>结算</dt><dd>{{ record.settlementMethod || '-' }} / {{ record.paymentTerms || '未填写' }}</dd>
        <dt>交付承诺</dt><dd>{{ record.deliveryCommitment || '-' }}</dd>
      </dl>

      <section class="section-block">
        <h2>报价明细</h2>
        <div class="table-scroll">
          <el-table :data="record.lines" row-key="id">
            <el-table-column label="物料" min-width="220">
              <template #default="{ row }">
                <strong>{{ row.materialCode }} {{ row.materialName }}</strong>
              </template>
            </el-table-column>
            <el-table-column label="数量" min-width="150">
              <template #default="{ row }">
                数量 {{ formatSalesDecimal(row.quantity) }} {{ row.unitName }}
              </template>
            </el-table-column>
            <el-table-column label="未税单价" min-width="150">
              <template #default="{ row }">
                未税单价 {{ formatSalesDecimal(quoteLineUntaxedUnitPrice(row)) }}
              </template>
            </el-table-column>
            <el-table-column label="含税单价" min-width="150">
              <template #default="{ row }">
                含税单价 {{ formatSalesDecimal(row.taxIncludedUnitPrice) }}
              </template>
            </el-table-column>
            <el-table-column label="税率" min-width="140">
              <template #default="{ row }">
                税率 {{ formatSalesDecimal(row.taxRate) }}
              </template>
            </el-table-column>
            <el-table-column label="含税金额" min-width="170">
              <template #default="{ row }">
                含税金额 {{ formatSalesDecimal(row.taxIncludedAmount) }} CNY
              </template>
            </el-table-column>
            <el-table-column label="承诺日期" min-width="150">
              <template #default="{ row }">
                承诺日期 {{ quoteLineRequiredDate(row) || '-' }}
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section class="section-block">
        <h2>附件 / 审计 / 来源追溯</h2>
        <p>附件沿用统一文档平台；审计记录报价创建、审批、取消、失效和转换；来源链固定追踪到合同/订单。</p>
      </section>
    </div>

    <el-dialog
      v-model="conversionDialog.visible"
      title="报价转换"
      :teleported="false"
      width="520px"
    >
      <el-alert
        v-if="conversionDialog.error"
        class="page-alert"
        type="error"
        :title="conversionDialog.error"
        show-icon
        :closable="false"
      />
      <div class="conversion-panel">
        <label>
          候选搜索
          <input
            v-model="conversionDialog.keyword"
            data-test="quote-detail-convert-candidate-search"
            autocomplete="off"
            placeholder="项目、合同编号或名称"
            @input="searchConversionCandidates"
          >
        </label>
        <label v-if="conversionDialog.action === 'contract'">
          合同类型
          <select v-model="conversionDialog.contractType" data-test="quote-convert-contract-type">
            <option value="MAIN">主合同</option>
            <option value="SUPPLEMENT">补充合同</option>
          </select>
        </label>
        <label v-if="conversionDialog.action === 'order'">
          项目与合同
          <select
            v-model="conversionDialog.selectedProjectContractKey"
            data-test="quote-convert-project-contract"
            :disabled="conversionDialog.loading"
          >
            <option value="">请选择 ACTIVE 项目与同客户 EFFECTIVE 合同</option>
            <option
              v-for="candidate in conversionDialog.candidates"
              :key="projectContractKey(candidate)"
              :value="projectContractKey(candidate)"
            >
              {{ projectContractLabel(candidate) }}
            </option>
          </select>
        </label>
        <label v-if="conversionDialog.action === 'contract' && conversionDialog.contractType === 'SUPPLEMENT'">
          主合同
          <select
            v-model="conversionDialog.selectedProjectContractKey"
            data-test="quote-convert-main-contract"
            :disabled="conversionDialog.loading"
          >
            <option value="">请选择同客户有效主合同</option>
            <option
              v-for="candidate in conversionDialog.candidates"
              :key="projectContractKey(candidate)"
              :value="projectContractKey(candidate)"
            >
              {{ projectContractLabel(candidate) }}
            </option>
          </select>
        </label>
        <p v-if="conversionDialog.loading">项目合同候选加载中</p>
        <p v-if="!conversionDialog.loading && conversionDialog.candidates.length === 0">
          暂无可用项目合同候选
        </p>
      </div>
      <template #footer>
        <el-button @click="conversionDialog.visible = false">取消</el-button>
        <el-button
          data-test="confirm-sales-quote-conversion"
          type="primary"
          :loading="actionLoading"
          @click="confirmQuoteConversion"
        >
          确认转换
        </el-button>
      </template>
    </el-dialog>
  </MasterDataTableView>
</template>

<style scoped>
.detail-body {
  display: grid;
  gap: 14px;
}

.header-actions {
  align-items: flex-start;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.summary-strip {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.summary-strip div,
.section-block {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  padding: 10px 12px;
}

.summary-strip span,
.detail-list dt {
  color: #606266;
  display: block;
  font-size: 12px;
}

.detail-list {
  display: grid;
  gap: 10px 14px;
  grid-template-columns: 96px minmax(0, 1fr) 96px minmax(0, 1fr);
  margin: 0;
}

.detail-list dd {
  margin: 0;
}

.section-block h2 {
  font-size: 16px;
  margin: 0 0 10px;
}

.conversion-panel {
  display: grid;
  gap: 12px;
}

.conversion-panel label {
  display: grid;
  gap: 6px;
}

.conversion-panel select {
  min-height: 34px;
}
</style>
