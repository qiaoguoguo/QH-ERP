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
import { masterDataApi, type PartnerRecord } from '../../../shared/api/masterDataApi'
import {
  salesProjectApi,
  type SalesOrderProjectContractCandidate,
  type SalesProjectContractType,
  type SalesProjectSummary,
} from '../../../shared/api/salesProjectApi'
import { useAuthStore } from '../../../stores/authStore'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import BusinessReferenceSelect from '../../system/shared/BusinessReferenceSelect.vue'
import type { BusinessReferenceOption } from '../../system/shared/businessReferenceSelectTypes'
import { pageItems, pageTotal } from '../../system/shared/pageHelpers'
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
const total = ref(0)
const latestDocumentTask = ref<DocumentTaskRecord | null>(null)
const conversionDialog = reactive({
  visible: false,
  action: 'order' as 'order' | 'contract',
  quote: null as SalesQuoteSummaryRecord | null,
  contractType: 'MAIN' as SalesProjectContractType,
  selectedProjectContractKey: '',
  keyword: '',
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

function customerOption(customer: PartnerRecord): BusinessReferenceOption {
  return { id: customer.id, label: `${customer.code} ${customer.name}` }
}

function projectOption(project: SalesProjectSummary): BusinessReferenceOption {
  return { id: project.id, label: `${project.projectNo} ${project.name}` }
}

async function loadCustomerOptions(keyword: string) {
  const page = await masterDataApi.customers.list({
    keyword,
    status: 'ENABLED',
    page: 1,
    pageSize: 50,
  })
  return pageItems(page).map(customerOption)
}

async function loadProjectOptions(keyword: string) {
  const page = await salesProjectApi.projects.list({
    keyword,
    status: 'ACTIVE',
    page: 1,
    pageSize: 50,
  })
  return pageItems(page).map(projectOption)
}

function selectedConversionCandidate() {
  return conversionDialog.candidates.find((candidate) => (
    projectContractKey(candidate) === conversionDialog.selectedProjectContractKey
  ))
}

async function loadConversionCandidates(record: SalesQuoteSummaryRecord, keyword = '') {
  conversionDialog.loading = true
  conversionDialog.error = ''
  try {
    const page = await salesProjectApi.listOrderLinkCandidates({
      customerId: record.customerId,
      keyword,
      page: 1,
      pageSize: 50,
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

async function searchConversionCandidates() {
  const quote = conversionDialog.quote
  if (!quote) {
    return
  }
  await loadConversionCandidates(quote, conversionDialog.keyword)
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
    total.value = pageTotal(page)
  } catch (caught) {
    records.value = []
    total.value = 0
    error.value = salesFulfillmentErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function searchRecords() {
  filters.page = 1
  await loadRecords()
}

async function resetFilters() {
  filters.keyword = ''
  filters.customerId = ''
  filters.projectId = ''
  filters.status = undefined
  filters.approvalStatus = undefined
  filters.validFrom = ''
  filters.validTo = ''
  filters.page = 1
  await loadRecords()
}

async function changePage(page: number) {
  filters.page = page
  await loadRecords()
}

async function changePageSize(pageSize: number) {
  filters.pageSize = pageSize
  filters.page = 1
  await loadRecords()
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
    conversionDialog.keyword = ''
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
  conversionDialog.keyword = ''
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
  <MasterDataTableView
    title="销售报价"
    description="报价审批、税价快照、转换来源和文档任务统一入口。"
  >
    <template #actions>
      <div class="header-actions">
        <el-button v-if="canCreate" data-test="create-sales-quote" type="primary" @click="router.push({ name: 'sales-quote-create' })">
          新建报价
        </el-button>
        <el-button v-if="canExport" data-test="export-sales-quotes" :loading="actionLoading" @click="exportQuotes">
          当前筛选导出
        </el-button>
      </div>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
      <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
      <SalesDocumentTaskPanel :task="latestDocumentTask" />
    </template>

    <template #filters>
    <el-form class="query-form" label-position="top">
      <el-form-item label="关键词">
        <el-input v-model="filters.keyword" placeholder="报价号、客户或物料" clearable />
      </el-form-item>
      <el-form-item label="客户">
        <BusinessReferenceSelect
          v-model="filters.customerId"
          placeholder="搜索客户编码或名称"
          :load-options="loadCustomerOptions"
          data-test="sales-quote-customer-filter"
        />
      </el-form-item>
      <el-form-item label="项目">
        <BusinessReferenceSelect
          v-model="filters.projectId"
          placeholder="搜索项目编号或名称"
          :load-options="loadProjectOptions"
          data-test="sales-quote-project-filter"
        />
      </el-form-item>
      <el-form-item label="业务状态">
        <el-select v-model="filters.status" clearable placeholder="业务状态">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已批准" value="APPROVED" />
          <el-option label="已转换" value="CONVERTED" />
          <el-option label="已过期" value="EXPIRED" />
          <el-option label="已取消" value="CANCELLED" />
        </el-select>
      </el-form-item>
      <el-form-item label="审批状态">
        <el-select v-model="filters.approvalStatus" clearable placeholder="审批状态">
          <el-option label="未提交" value="NONE" />
          <el-option label="审批中" value="SUBMITTED" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已拒绝" value="REJECTED" />
        </el-select>
      </el-form-item>
      <el-form-item label="有效期起">
        <el-date-picker
          v-model="filters.validFrom"
          type="date"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          value-on-clear=""
          placeholder="有效期起"
        />
      </el-form-item>
      <el-form-item label="有效期止">
        <el-date-picker
          v-model="filters.validTo"
          type="date"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          value-on-clear=""
          placeholder="有效期止"
        />
      </el-form-item>
      <el-form-item class="query-actions" label="操作">
        <el-button data-test="search-sales-quotes" type="primary" @click="searchRecords">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>
    </template>

    <div class="table-scroll">
      <el-table v-loading="loading" :data="records" row-key="id" :empty-text="loading ? '加载中' : '暂无销售报价'">
        <el-table-column label="报价与来源" min-width="260">
          <template #default="{ row }">
            <strong>{{ row.quoteNo }}</strong>
            <span>{{ projectSalesLabel(row) }}</span>
            <span>{{ row.customerCode }} {{ row.customerName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="220">
          <template #default="{ row }">
            <span>业务状态：{{ quoteStatusLabel(row.status) }}</span>
            <span>审批状态：{{ approvalStatusLabel(row.approvalStatus) }}</span>
            <span>有效期：{{ row.validUntil }}</span>
          </template>
        </el-table-column>
        <el-table-column label="税价金额" min-width="260">
          <template #default="{ row }">
            <span>未税 {{ formatSalesDecimal(quoteUntaxedAmount(row)) }} / 税额 {{ formatSalesDecimal(quoteTaxAmount(row)) }}</span>
            <span>含税 {{ formatSalesDecimal(quoteTaxIncludedAmount(row)) }} {{ row.currency }}</span>
            <span v-if="row.amountRestricted">金额受限</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="260">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button text type="primary" @click="viewQuote(row)">详情</el-button>
              <el-button v-if="allowed(row, 'UPDATE') && authStore.hasPermission('sales:quote:update')" text @click="editQuote(row)">
                编辑
              </el-button>
              <el-button
                v-if="allowed(row, 'SUBMIT_APPROVAL') && canSubmit"
                :data-test="`submit-sales-quote-${row.id}`"
                text
                type="success"
                :disabled="actionLoading"
                @click="submitQuote(row)"
              >
                提交审批
              </el-button>
              <el-button
                v-if="allowed(row, 'CANCEL') && canCancel"
                :data-test="`cancel-sales-quote-${row.id}`"
                text
                type="danger"
                :disabled="actionLoading"
                @click="cancelQuote(row)"
              >
                取消
              </el-button>
              <el-button
                v-if="allowed(row, 'CONVERT_ORDER') && canConvert"
                :data-test="`convert-sales-quote-order-${row.id}`"
                text
                type="primary"
                :disabled="actionLoading"
                @click="convertQuoteOrder(row)"
              >
                转订单
              </el-button>
              <el-button
                v-if="allowed(row, 'CONVERT_CONTRACT') && canConvert && row.projectId"
                :data-test="`convert-sales-quote-contract-${row.id}`"
                text
                :disabled="actionLoading"
                @click="convertQuoteContract(row)"
              >
                转合同
              </el-button>
              <span v-if="row.actionDisabledReason">{{ row.actionDisabledReason }}</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-pagination
      class="table-pagination"
      layout="total, sizes, prev, pager, next"
      :total="total"
      :current-page="filters.page"
      :page-size="filters.pageSize"
      :page-sizes="[10, 20, 50, 100]"
      @current-change="changePage"
      @size-change="changePageSize"
    />

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
            data-test="quote-convert-candidate-search"
            placeholder="搜索项目、合同编号或名称"
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
.header-actions {
  align-items: flex-start;
  display: flex;
  gap: 12px;
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

.row-actions {
  display: grid;
  gap: 12px;
  justify-items: end;
}

.table-scroll span {
  display: block;
}
</style>
