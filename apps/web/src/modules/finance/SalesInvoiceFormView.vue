<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { financeInvoiceApi, type InvoiceType, type SalesInvoiceCandidateLine, type SalesInvoicePayload, type SalesInvoiceRecord } from '../../shared/api/financeInvoiceApi'
import { masterDataApi, type PartnerRecord } from '../../shared/api/masterDataApi'
import { salesProjectApi, type SalesProjectSummary } from '../../shared/api/salesProjectApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import { financeErrorMessage, formatFinanceAmount, normalizeOptionalId, ownershipTypeText } from './financePageHelpers'
import './Finance028Shared.css'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => route.name === 'finance-sales-invoice-edit')
const customers = ref<PartnerRecord[]>([])
const projects = ref<SalesProjectSummary[]>([])
const candidates = ref<SalesInvoiceCandidateLine[]>([])
const selected = ref<SalesInvoiceCandidateLine[]>([])
const detail = ref<SalesInvoiceRecord | null>(null)
const loading = ref(false)
const candidatesLoading = ref(false)
const error = ref('')
const submitting = ref(false)
const candidatePagination = reactive({ page: 1, pageSize: 10, total: 0 })
const candidateFilters = reactive({ keyword: '' })
const form = reactive({
  invoiceDate: '',
  invoiceType: 'GENERAL_VAT' as InvoiceType,
  externalInvoiceNo: '',
  customerId: '' as string | number | '',
  ownershipType: 'PUBLIC' as 'PROJECT' | 'PUBLIC',
  projectId: '' as string | number | '',
  remark: '',
})

const selectedCustomerName = computed(() => customers.value.find((item) => String(item.id) === String(form.customerId))?.name ?? '')
const selectedProjectName = computed(() => projects.value.find((item) => String(item.id) === String(form.projectId))?.name ?? '')
const selectedCandidateBalance = computed(() => selected.value[0]?.availableAmount ?? selected.value[0]?.totalAmount ?? candidates.value[0]?.availableAmount ?? candidates.value[0]?.totalAmount ?? '0.00')
const selectedTotalText = computed(() => selected.value.length ? formatFinanceAmount(selected.value[0].totalAmount) : '0.00')
const readOnlyReason = computed(() => {
  if (!isEdit.value || !detail.value) return ''
  if (detail.value.status === 'CONFIRMED') return '确认后不可普通编辑'
  if (!(detail.value.allowedActions ?? []).includes('UPDATE')) return '当前销售发票不可编辑'
  return ''
})
const formReadOnly = computed(() => Boolean(readOnlyReason.value))
const saveDisabledReason = computed(() => {
  if (readOnlyReason.value) return readOnlyReason.value
  if (!form.invoiceDate) return '请选择开票日期'
  if (!form.customerId) return '请选择客户'
  if (form.ownershipType === 'PROJECT' && !form.projectId) return '请选择项目'
  if (selected.value.length === 0) return '请选择销售出库来源'
  return ''
})

async function loadMasterData() {
  const [customerPage, projectPage] = await Promise.all([
    masterDataApi.customers.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
    salesProjectApi.projects.list({ keyword: '', status: 'ACTIVE', page: 1, pageSize: 200 }),
  ])
  customers.value = pageItems(customerPage)
  projects.value = pageItems(projectPage)
}

function fillFromCandidate(line: SalesInvoiceCandidateLine) {
  if (line.customerId !== undefined) {
    form.customerId = line.customerId
  }
  if (line.ownershipType) {
    form.ownershipType = line.ownershipType
  }
  form.projectId = line.ownershipType === 'PROJECT' ? (line.projectId ?? '') : ''
}

function restoreSelectedLines(record: SalesInvoiceRecord) {
  const recordLines = record.lines ?? []
  selected.value = recordLines.map((line, index) => ({
    sourceLineId: line.sourceLineId,
    sourceType: record.sourceType ?? 'SALES_SHIPMENT',
    sourceId: record.sourceId ?? undefined,
    customerId: record.partyId ?? record.customerId,
    customerName: record.customerName,
    ownershipType: record.ownershipType,
    projectId: record.projectId ?? null,
    projectName: record.projectName ?? null,
    sourceNo: record.sourceNo ?? record.sources?.[0]?.sourceNo ?? record.invoiceNo,
    lineNo: line.lineNo ?? index + 1,
    materialCode: line.materialCode ?? undefined,
    materialName: line.materialName ?? undefined,
    unitName: line.unitName ?? undefined,
    availableQuantity: String(line.quantity ?? line.invoiceQuantity ?? ''),
    invoicedQuantity: '0.000000',
    invoiceQuantity: String(line.invoiceQuantity ?? line.quantity ?? ''),
    pretaxUnitPrice: line.pretaxUnitPrice ?? line.taxExcludedUnitPrice,
    taxRate: line.taxRate,
    pretaxAmount: line.pretaxAmount ?? line.taxExcludedAmount,
    taxAmount: line.taxAmount,
    totalAmount: line.totalAmount ?? line.taxIncludedAmount,
    availableAmount: line.totalAmount ?? line.taxIncludedAmount,
  }))
}

async function loadCandidates() {
  if (formReadOnly.value) {
    candidates.value = selected.value
    candidatePagination.total = selected.value.length
    candidatesLoading.value = false
    return
  }
  candidatesLoading.value = true
  try {
    const page = await financeInvoiceApi.salesInvoiceCandidates.list({
      keyword: candidateFilters.keyword,
      sourceId: selected.value[0]?.sourceId ?? detail.value?.sourceId ?? undefined,
      customerId: normalizeOptionalId(form.customerId),
      ownershipType: form.ownershipType,
      projectId: form.ownershipType === 'PROJECT' ? normalizeOptionalId(form.projectId) : undefined,
      page: candidatePagination.page,
      pageSize: candidatePagination.pageSize,
    })
    candidates.value = pageItems(page)
    candidatePagination.total = Number(page.total)
    if (!form.customerId && candidates.value[0]) {
      fillFromCandidate(candidates.value[0])
    }
  } finally {
    candidatesLoading.value = false
  }
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    await loadMasterData()
    if (isEdit.value) {
      detail.value = await financeInvoiceApi.salesInvoices.get(route.params.id as string)
      form.invoiceDate = detail.value.invoiceDate
      form.invoiceType = detail.value.invoiceType
      form.externalInvoiceNo = detail.value.externalInvoiceNo ?? ''
      form.customerId = detail.value.partyId ?? detail.value.customerId ?? ''
      form.ownershipType = detail.value.ownershipType
      form.projectId = detail.value.projectId ?? ''
      form.remark = ''
      restoreSelectedLines(detail.value)
    }
    await loadCandidates()
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function selectSourceLine(line: SalesInvoiceCandidateLine) {
  if (formReadOnly.value) {
    return
  }
  if (!selected.value.some((item) => item.sourceLineId === line.sourceLineId)) {
    selected.value = [...selected.value, line]
  }
  fillFromCandidate(line)
}

function searchCandidates() {
  if (formReadOnly.value) {
    return
  }
  candidatePagination.page = 1
  void loadCandidates()
}

function changeCandidatePage(page: number) {
  if (formReadOnly.value) {
    return
  }
  candidatePagination.page = page
  void loadCandidates()
}

async function save() {
  if (submitting.value || saveDisabledReason.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    const first = selected.value[0]
    const payload: SalesInvoicePayload = {
      sourceType: 'SALES_SHIPMENT',
      sourceId: first.sourceId ?? null,
      invoiceDate: form.invoiceDate,
      invoiceType: form.invoiceType,
      externalInvoiceNo: form.externalInvoiceNo,
      customerId: form.customerId,
      ownershipType: form.ownershipType,
      projectId: form.ownershipType === 'PROJECT' ? normalizeOptionalId(form.projectId) ?? null : null,
      sourceLines: selected.value.map((line) => ({
        sourceLineId: line.sourceLineId,
        invoiceQuantity: String(line.invoiceQuantity),
      })),
      remark: form.remark,
      version: detail.value?.version ?? 0,
      idempotencyKey: `sales-invoice-${Date.now()}`,
    }
    const result = isEdit.value
      ? await financeInvoiceApi.salesInvoices.update(route.params.id as string, payload)
      : await financeInvoiceApi.salesInvoices.create(payload)
    await router.push({ name: 'finance-sales-invoice-detail', params: { id: String(result.id) } })
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <MasterDataTableView :title="isEdit ? '编辑销售发票草稿' : '新增销售发票'" description="选择已过账销售出库来源，按税价快照形成销售发票草稿。">
    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" type="info" title="销售发票表单加载中" :closable="false" />
      <el-alert v-if="saveDisabledReason" type="warning" :title="saveDisabledReason" :closable="false" />
    </template>

    <div class="finance-summary-strip">
      <div><span>客户</span><strong>{{ selectedCustomerName || '待选择' }}</strong></div>
      <div><span>项目/公共归属</span><strong>{{ ownershipTypeText(form.ownershipType) }} {{ selectedProjectName }}</strong></div>
      <div><span>来源出库净可开票余额</span><strong>{{ formatFinanceAmount(selectedCandidateBalance) }}</strong></div>
      <div><span>本次价税合计</span><strong>{{ selectedTotalText }}</strong></div>
    </div>

    <el-form label-position="top" class="finance-form">
      <div class="finance-form-grid">
        <el-form-item label="客户">
          <el-select data-test="sales-invoice-customer" v-model="form.customerId" filterable clearable :disabled="formReadOnly" placeholder="选择客户" @change="searchCandidates">
            <el-option v-for="customer in customers" :key="customer.id" :label="customer.name" :value="customer.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目/公共归属">
          <el-select v-model="form.ownershipType" :disabled="formReadOnly" placeholder="选择项目或公共" @change="searchCandidates">
            <el-option label="公共" value="PUBLIC" />
            <el-option label="项目" value="PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目">
          <el-select v-model="form.projectId" filterable clearable :disabled="formReadOnly || form.ownershipType === 'PUBLIC'" placeholder="选择项目" @change="searchCandidates">
            <el-option v-for="project in projects" :key="project.id" :label="`${project.projectNo} ${project.name}`" :value="project.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="开票日期">
          <el-date-picker v-model="form.invoiceDate" name="sales-invoice-date" :disabled="formReadOnly" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="选择开票日期" />
        </el-form-item>
        <el-form-item label="外部发票号码">
          <el-input v-model="form.externalInvoiceNo" name="sales-invoice-external-no" :disabled="formReadOnly" clearable placeholder="填写外部发票号码" />
        </el-form-item>
        <el-form-item label="发票类型">
          <el-select v-model="form.invoiceType" :disabled="formReadOnly" placeholder="选择发票类型">
            <el-option label="增值税普通发票" value="GENERAL_VAT" />
            <el-option label="增值税专用发票" value="SPECIAL_VAT" />
            <el-option label="无票" value="NONE" />
          </el-select>
        </el-form-item>
      </div>
    </el-form>

    <el-form class="query-form">
      <el-form-item label="来源关键词"><el-input data-test="sales-invoice-source-keyword" v-model="candidateFilters.keyword" :disabled="formReadOnly" clearable placeholder="出库号、物料或客户" /></el-form-item>
      <el-form-item><el-button type="primary" :disabled="formReadOnly" @click="searchCandidates">查询来源</el-button></el-form-item>
    </el-form>

    <div class="table-scroll">
      <el-table :data="candidates" :empty-text="candidatesLoading ? '来源加载中' : '当前条件下无可用销售出库来源'" stripe>
        <el-table-column prop="sourceNo" label="出库号" min-width="150" />
        <el-table-column prop="lineNo" label="行号" min-width="80" />
        <el-table-column prop="materialName" label="物料" min-width="160" show-overflow-tooltip />
        <el-table-column prop="unitName" label="单位" min-width="80" />
        <el-table-column prop="availableQuantity" label="可开票数量" min-width="130" align="right" />
        <el-table-column prop="invoicedQuantity" label="已开票数量" min-width="130" align="right" />
        <el-table-column prop="invoiceQuantity" label="本次数量" min-width="120" align="right" />
        <el-table-column prop="pretaxUnitPrice" label="未税单价" min-width="120" align="right" />
        <el-table-column prop="taxRate" label="税率" min-width="100" align="right" />
        <el-table-column label="净可开余额" min-width="120" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.availableAmount ?? row.totalAmount) }}</template></el-table-column>
        <el-table-column prop="totalAmount" label="含税金额" min-width="120" align="right" />
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ row }">
            <el-button data-test="select-source-line" text :disabled="formReadOnly" :type="selected.some((item) => item.sourceLineId === row.sourceLineId) ? 'primary' : undefined" @click="selectSourceLine(row)">
              {{ selected.some((item) => item.sourceLineId === row.sourceLineId) ? '已选' : '选择' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination class="table-pagination" layout="total, prev, pager, next" :total="candidatePagination.total" :page-size="candidatePagination.pageSize" :current-page="candidatePagination.page" @current-change="changeCandidatePage" />
    <div class="finance-form-footer">
      <el-button @click="router.back()">取消</el-button>
      <el-button data-test="save-sales-invoice" type="primary" :loading="submitting" :disabled="submitting || Boolean(saveDisabledReason)" @click="save">保存草稿</el-button>
    </div>
  </MasterDataTableView>
</template>
