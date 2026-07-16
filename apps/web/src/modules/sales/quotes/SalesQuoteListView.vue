<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type DocumentTaskRecord,
} from '../../../shared/api/documentPlatformApi'
import {
  salesFulfillmentApi,
  type SalesApprovalStatus,
  type SalesQuoteAction,
  type SalesQuoteStatus,
  type SalesQuoteSummaryRecord,
} from '../../../shared/api/salesFulfillmentApi'
import {
  salesProjectApi,
  type SalesOrderProjectContractCandidate,
  type SalesProjectContractType,
} from '../../../shared/api/salesProjectApi'
import { useAuthStore } from '../../../stores/authStore'
import { pageItems } from '../../system/shared/pageHelpers'
import SalesDocumentTaskPanel from '../SalesDocumentTaskPanel.vue'
import {
  approvalStatusLabel,
  formatSalesDecimal,
  optionalSalesId,
  projectSalesLabel,
  quoteTaxAmount,
  quoteTaxIncludedAmount,
  quoteUntaxedAmount,
  quoteStatusLabel,
  salesFulfillmentErrorMessage,
} from '../salesFulfillmentPageHelpers'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const records = ref<SalesQuoteSummaryRecord[]>([])
const latestDocumentTask = ref<DocumentTaskRecord | null>(null)
const conversionDialog = reactive({
  visible: false,
  action: 'order' as 'order' | 'contract',
  quote: null as SalesQuoteSummaryRecord | null,
  contractType: 'MAIN' as SalesProjectContractType,
  selectedProjectContractKey: '',
  candidates: [] as SalesOrderProjectContractCandidate[],
  loading: false,
  error: '',
})
const filters = reactive({
  keyword: '',
  customerId: '' as string | number | '',
  projectId: '' as string | number | '',
  status: undefined as SalesQuoteStatus | undefined,
  approvalStatus: undefined as SalesApprovalStatus | undefined,
  validFrom: '',
  validTo: '',
  page: 1,
  pageSize: 10,
})

const canCreate = computed(() => authStore.hasPermission('sales:quote:create'))
const canSubmit = computed(() => authStore.hasPermission('sales:quote:submit'))
const canCancel = computed(() => authStore.hasPermission('sales:quote:cancel'))
const canConvert = computed(() => authStore.hasPermission('sales:quote:convert'))
const canExport = computed(() => (
  authStore.hasPermission('sales:quote:view')
  && authStore.hasPermission('platform:document-task:create')
  && authStore.hasPermission('sales:document:export')
))

function allowed(record: SalesQuoteSummaryRecord, action: SalesQuoteAction) {
  return (record.allowedActions ?? []).includes(action)
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

async function loadConversionCandidates(record: SalesQuoteSummaryRecord) {
  conversionDialog.loading = true
  conversionDialog.error = ''
  try {
    const page = await salesProjectApi.listOrderLinkCandidates({
      customerId: record.customerId,
      keyword: '',
      page: 1,
      pageSize: 20,
    })
    conversionDialog.candidates = pageItems(page)
    const matching = conversionDialog.candidates.find((candidate) => (
      record.projectId ? String(candidate.projectId) === String(record.projectId) : true
    ))
    conversionDialog.selectedProjectContractKey = matching ? projectContractKey(matching) : ''
  } catch (caught) {
    conversionDialog.candidates = []
    conversionDialog.error = salesFulfillmentErrorMessage(caught)
  } finally {
    conversionDialog.loading = false
  }
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await salesFulfillmentApi.quotes.list({
      keyword: filters.keyword,
      customerId: optionalSalesId(filters.customerId),
      projectId: optionalSalesId(filters.projectId),
      status: filters.status,
      approvalStatus: filters.approvalStatus,
      validFrom: filters.validFrom,
      validTo: filters.validTo,
      page: filters.page,
      pageSize: filters.pageSize,
    })
    records.value = pageItems(page)
  } catch (caught) {
    records.value = []
    error.value = salesFulfillmentErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function submitQuote(record: SalesQuoteSummaryRecord) {
  if (actionLoading.value) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await salesFulfillmentApi.quotes.submitApproval(record.id, {
      version: record.version,
      reason: '报价确认',
      idempotencyKey: createIdempotencyKey('sales-quote-submit'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = salesFulfillmentErrorMessage(caught)
    await loadRecords()
  } finally {
    actionLoading.value = false
  }
}

async function cancelQuote(record: SalesQuoteSummaryRecord) {
  if (actionLoading.value) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await salesFulfillmentApi.quotes.cancel(record.id, {
      version: record.version,
      reason: '客户取消',
      idempotencyKey: createIdempotencyKey('sales-quote-cancel'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = salesFulfillmentErrorMessage(caught)
    await loadRecords()
  } finally {
    actionLoading.value = false
  }
}

async function convertQuoteOrder(record: SalesQuoteSummaryRecord) {
  if (actionLoading.value) {
    return
  }
  if (record.projectId) {
    conversionDialog.visible = true
    conversionDialog.action = 'order'
    conversionDialog.quote = record
    conversionDialog.contractType = 'MAIN'
    conversionDialog.selectedProjectContractKey = ''
    await loadConversionCandidates(record)
    return
  }
  await submitQuoteOrderConversion(record, null, null)
}

async function submitQuoteOrderConversion(
  record: SalesQuoteSummaryRecord,
  projectId: SalesOrderProjectContractCandidate['projectId'] | null,
  contractId: SalesOrderProjectContractCandidate['contractId'] | null,
) {
  actionLoading.value = true
  actionError.value = ''
  try {
    await salesFulfillmentApi.quotes.convertOrder(record.id, {
      version: record.version,
      projectId,
      contractId,
      idempotencyKey: createIdempotencyKey('sales-quote-convert-order'),
    })
    conversionDialog.visible = false
    await loadRecords()
  } catch (caught) {
    actionError.value = salesFulfillmentErrorMessage(caught)
    await loadRecords()
  } finally {
    actionLoading.value = false
  }
}

async function convertQuoteContract(record: SalesQuoteSummaryRecord) {
  if (actionLoading.value || !record.projectId) {
    return
  }
  conversionDialog.visible = true
  conversionDialog.action = 'contract'
  conversionDialog.quote = record
  conversionDialog.contractType = 'MAIN'
  conversionDialog.selectedProjectContractKey = ''
  await loadConversionCandidates(record)
}

async function submitQuoteContractConversion(
  record: SalesQuoteSummaryRecord,
  payload: {
    projectId: SalesOrderProjectContractCandidate['projectId']
    contractType: SalesProjectContractType
    mainContractId?: SalesOrderProjectContractCandidate['contractId'] | null
  },
) {
  actionLoading.value = true
  actionError.value = ''
  try {
    await salesFulfillmentApi.quotes.convertContract(record.id, {
      version: record.version,
      ...payload,
      idempotencyKey: createIdempotencyKey('sales-quote-convert-contract'),
    })
    conversionDialog.visible = false
    await loadRecords()
  } catch (caught) {
    actionError.value = salesFulfillmentErrorMessage(caught)
    await loadRecords()
  } finally {
    actionLoading.value = false
  }
}

async function confirmQuoteConversion() {
  const quote = conversionDialog.quote
  if (!quote || actionLoading.value) {
    return
  }
  const selected = selectedConversionCandidate()
  if (conversionDialog.action === 'order') {
    if (!selected) {
      conversionDialog.error = '项目报价转订单必须选择有效项目和同客户有效合同'
      return
    }
    await submitQuoteOrderConversion(quote, selected.projectId, selected.contractId)
    return
  }
  if (conversionDialog.contractType === 'SUPPLEMENT' && !selected) {
    conversionDialog.error = '补充合同必须选择主合同'
    return
  }
  const projectId = selected?.projectId ?? quote.projectId
  if (!projectId) {
    conversionDialog.error = '项目报价转合同必须有项目'
    return
  }
  await submitQuoteContractConversion(quote, {
    projectId,
    contractType: conversionDialog.contractType,
    ...(conversionDialog.contractType === 'SUPPLEMENT' ? { mainContractId: selected?.contractId ?? null } : {}),
  })
}

async function exportQuotes() {
  if (actionLoading.value) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    latestDocumentTask.value = await documentPlatformApi.exports.createSalesQuotes({
      keyword: filters.keyword,
      customerId: optionalSalesId(filters.customerId),
      projectId: optionalSalesId(filters.projectId),
      status: filters.status,
      approvalStatus: filters.approvalStatus,
      validFrom: filters.validFrom,
      validTo: filters.validTo,
      idempotencyKey: createIdempotencyKey('sales-quote-export'),
    })
  } catch (caught) {
    actionError.value = salesFulfillmentErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function viewQuote(record: SalesQuoteSummaryRecord) {
  void router.push({ name: 'sales-quote-detail', params: { id: String(record.id) } })
}

function editQuote(record: SalesQuoteSummaryRecord) {
  void router.push({ name: 'sales-quote-edit', params: { id: String(record.id) } })
}

onMounted(loadRecords)
</script>

<template>
  <section class="sales-page sales-list-page">
    <header class="page-header">
      <div>
        <h1>销售报价</h1>
        <p>报价审批、税价快照、转换来源和文档任务统一入口。</p>
      </div>
      <div class="header-actions">
        <el-button v-if="canCreate" data-test="create-sales-quote" type="primary" @click="router.push({ name: 'sales-quote-create' })">
          新建报价
        </el-button>
        <el-button v-if="canExport" data-test="export-sales-quotes" :loading="actionLoading" @click="exportQuotes">
          当前筛选导出
        </el-button>
      </div>
    </header>

    <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
    <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
    <SalesDocumentTaskPanel :task="latestDocumentTask" />

    <div class="filter-strip">
      <el-input v-model="filters.keyword" placeholder="报价号、客户或物料" clearable />
      <el-input v-model="filters.customerId" placeholder="客户 ID" clearable />
      <el-input v-model="filters.projectId" placeholder="项目 ID" clearable />
      <el-select v-model="filters.status" clearable placeholder="业务状态">
        <el-option label="草稿" value="DRAFT" />
        <el-option label="已批准" value="APPROVED" />
        <el-option label="已转换" value="CONVERTED" />
        <el-option label="已过期" value="EXPIRED" />
        <el-option label="已取消" value="CANCELLED" />
      </el-select>
      <el-button data-test="search-sales-quotes" type="primary" @click="loadRecords">查询</el-button>
    </div>

    <el-empty v-if="!loading && records.length === 0" description="暂无销售报价" />
    <div class="sales-record-grid" v-loading="loading">
      <article v-for="record in records" :key="record.id" class="sales-record-row">
        <div class="decision-column">
          <strong>{{ record.quoteNo }}</strong>
          <span>{{ projectSalesLabel(record) }}</span>
          <span>{{ record.customerCode }} {{ record.customerName }}</span>
        </div>
        <div class="state-column">
          <span>业务状态：{{ quoteStatusLabel(record.status) }}</span>
          <span>审批状态：{{ approvalStatusLabel(record.approvalStatus) }}</span>
          <span>有效期：{{ record.validUntil }}</span>
        </div>
        <div class="money-column">
          <span>未税 {{ formatSalesDecimal(quoteUntaxedAmount(record)) }} / 税额 {{ formatSalesDecimal(quoteTaxAmount(record)) }}</span>
          <span>含税 {{ formatSalesDecimal(quoteTaxIncludedAmount(record)) }} {{ record.currency }}</span>
          <span v-if="record.amountRestricted">金额受限</span>
        </div>
        <div class="action-column">
          <el-button text type="primary" @click="viewQuote(record)">详情</el-button>
          <el-button v-if="allowed(record, 'UPDATE') && authStore.hasPermission('sales:quote:update')" text @click="editQuote(record)">
            编辑
          </el-button>
          <el-button
            v-if="allowed(record, 'SUBMIT_APPROVAL') && canSubmit"
            :data-test="`submit-sales-quote-${record.id}`"
            text
            type="success"
            :disabled="actionLoading"
            @click="submitQuote(record)"
          >
            提交审批
          </el-button>
          <el-button
            v-if="allowed(record, 'CANCEL') && canCancel"
            :data-test="`cancel-sales-quote-${record.id}`"
            text
            type="danger"
            :disabled="actionLoading"
            @click="cancelQuote(record)"
          >
            取消
          </el-button>
          <el-button
            v-if="allowed(record, 'CONVERT_ORDER') && canConvert"
            :data-test="`convert-sales-quote-order-${record.id}`"
            text
            type="primary"
            :disabled="actionLoading"
            @click="convertQuoteOrder(record)"
          >
            转订单
          </el-button>
          <el-button
            v-if="allowed(record, 'CONVERT_CONTRACT') && canConvert && record.projectId"
            :data-test="`convert-sales-quote-contract-${record.id}`"
            text
            :disabled="actionLoading"
            @click="convertQuoteContract(record)"
          >
            转合同
          </el-button>
          <span v-if="record.actionDisabledReason">{{ record.actionDisabledReason }}</span>
        </div>
      </article>
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
  </section>
</template>

<style scoped>
.sales-list-page,
.sales-record-grid {
  display: grid;
  gap: 12px;
}

.page-header,
.header-actions,
.filter-strip {
  align-items: flex-start;
  display: flex;
  gap: 12px;
}

.page-header {
  justify-content: space-between;
}

.page-header h1 {
  font-size: 22px;
  margin: 0 0 6px;
}

.page-header p {
  color: #606266;
  margin: 0;
}

.filter-strip {
  flex-wrap: wrap;
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

.sales-record-row {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(250px, 1.25fr) minmax(170px, 0.85fr) minmax(230px, 1fr) minmax(190px, auto);
  padding: 12px;
}

.decision-column,
.state-column,
.money-column,
.action-column {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.action-column {
  align-items: flex-end;
  position: sticky;
  right: 0;
}
</style>
