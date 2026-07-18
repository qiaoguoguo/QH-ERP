<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  financeInvoiceApi,
  type InvoiceType,
  type PurchaseInvoiceCandidateLine,
  type PurchaseInvoicePayload,
  type PurchaseInvoiceRecord,
  type PurchaseInvoiceSourceType,
} from '../../shared/api/financeInvoiceApi'
import type { OwnershipType } from '../../shared/api/financeStage028ApiCore'
import { masterDataApi, type PartnerRecord } from '../../shared/api/masterDataApi'
import { salesProjectApi, type SalesProjectSummary } from '../../shared/api/salesProjectApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import { financeErrorMessage, formatFinanceAmount, normalizeOptionalId, ownershipTypeText } from './financePageHelpers'
import './Finance028Shared.css'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => route.name === 'finance-purchase-invoice-edit')
const suppliers = ref<PartnerRecord[]>([])
const projects = ref<SalesProjectSummary[]>([])
const candidates = ref<PurchaseInvoiceCandidateLine[]>([])
const selected = ref<PurchaseInvoiceCandidateLine[]>([])
const detail = ref<PurchaseInvoiceRecord | null>(null)
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
  supplierId: '' as string | number | '',
  sourceType: 'PURCHASE_RECEIPT' as PurchaseInvoiceSourceType,
  ownershipType: 'PUBLIC' as OwnershipType,
  projectId: '' as string | number | '',
  remark: '',
})

const selectedSupplierName = computed(() => (
  suppliers.value.find((item) => String(item.id) === String(form.supplierId))?.name
  ?? selected.value[0]?.supplierName
  ?? candidates.value.find((item) => String(item.supplierId) === String(form.supplierId))?.supplierName
  ?? ''
))
const selectedProjectName = computed(() => projects.value.find((item) => String(item.id) === String(form.projectId))?.name ?? '')
const selectedCandidateBalance = computed(() => selected.value[0]?.availableAmount ?? selected.value[0]?.totalAmount ?? candidates.value[0]?.availableAmount ?? candidates.value[0]?.totalAmount ?? '0.00')
const readOnlyReason = computed(() => {
  if (!isEdit.value || !detail.value) return ''
  if (detail.value.status === 'CONFIRMED') return '确认后不可普通编辑'
  if (!(detail.value.allowedActions ?? []).includes('UPDATE')) return '当前采购发票不可编辑'
  return ''
})
const formReadOnly = computed(() => Boolean(readOnlyReason.value))
const saveDisabledReason = computed(() => {
  if (readOnlyReason.value) return readOnlyReason.value
  if (!form.invoiceDate) return '请选择发票日期'
  if (!form.supplierId) return '请选择供应商'
  if (form.ownershipType === 'PROJECT' && !form.projectId) return '请选择项目'
  if (selected.value.length === 0) return '请选择采购或外协来源'
  return ''
})

async function loadMasterData() {
  const [supplierPage, projectPage] = await Promise.all([
    masterDataApi.suppliers.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
    salesProjectApi.projects.list({ keyword: '', status: 'ACTIVE', page: 1, pageSize: 200 }),
  ])
  suppliers.value = pageItems(supplierPage)
  projects.value = pageItems(projectPage)
}

function fillFromCandidate(line: PurchaseInvoiceCandidateLine) {
  if (line.supplierId !== undefined) {
    form.supplierId = line.supplierId
  }
  if (line.sourceType === 'PURCHASE_RECEIPT' || line.sourceType === 'OUTSOURCING_RECEIPT') {
    form.sourceType = line.sourceType
  }
  if (line.ownershipType) {
    form.ownershipType = line.ownershipType
  }
  form.projectId = line.ownershipType === 'PROJECT' ? (line.projectId ?? '') : ''
}

function restoreSelectedLines(record: PurchaseInvoiceRecord) {
  const recordLines = record.lines ?? []
  selected.value = recordLines.map((line, index) => ({
    sourceLineId: line.sourceLineId,
    sourceType: record.sourceType,
    sourceId: record.sourceId ?? undefined,
    supplierId: record.partyId ?? record.supplierId,
    supplierName: record.supplierName,
    ownershipType: record.ownershipType,
    projectId: record.projectId ?? null,
    projectName: record.projectName ?? null,
    sourceNo: record.sourceNo ?? record.sources?.[0]?.sourceNo ?? record.invoiceNo,
    lineNo: line.lineNo ?? index + 1,
    orderLineId: line.orderLineId ?? line.purchaseOrderLineId ?? null,
    receiptLineId: line.receiptLineId ?? (record.sourceType === 'PURCHASE_RECEIPT' ? line.sourceLineId : null),
    outsourcingReceiptLineId: line.outsourcingReceiptLineId ?? (record.sourceType === 'OUTSOURCING_RECEIPT' ? line.sourceLineId : null),
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
    const page = await financeInvoiceApi.purchaseInvoiceCandidates.list({
      keyword: candidateFilters.keyword,
      sourceId: selected.value[0]?.sourceId ?? detail.value?.sourceId ?? undefined,
      supplierId: normalizeOptionalId(form.supplierId),
      ownershipType: form.ownershipType,
      projectId: form.ownershipType === 'PROJECT' ? normalizeOptionalId(form.projectId) : undefined,
      sourceType: form.sourceType,
      page: candidatePagination.page,
      pageSize: candidatePagination.pageSize,
    })
    candidates.value = pageItems(page)
    candidatePagination.total = Number(page.total)
    if (!form.supplierId && candidates.value[0]) {
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
      detail.value = await financeInvoiceApi.purchaseInvoices.get(route.params.id as string)
      form.invoiceDate = detail.value.invoiceDate
      form.invoiceType = (detail.value as PurchaseInvoiceRecord & { invoiceType?: InvoiceType }).invoiceType ?? 'GENERAL_VAT'
      form.externalInvoiceNo = detail.value.externalInvoiceNo ?? ''
      form.supplierId = detail.value.partyId ?? detail.value.supplierId ?? ''
      form.sourceType = detail.value.sourceType
      form.ownershipType = detail.value.ownershipType
      form.projectId = detail.value.projectId ?? ''
      restoreSelectedLines(detail.value)
    }
    await loadCandidates()
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function selectLine(line: PurchaseInvoiceCandidateLine) {
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
    const payload: PurchaseInvoicePayload = {
      settlementKind: form.sourceType === 'PURCHASE_RECEIPT' ? 'STANDARD_PURCHASE' : 'OUTSOURCING',
      sourceId: first.sourceId ?? first.receiptLineId ?? first.outsourcingReceiptLineId ?? first.sourceLineId,
      invoiceDate: form.invoiceDate,
      invoiceType: form.invoiceType,
      externalInvoiceNo: form.externalInvoiceNo,
      supplierInvoiceNo: form.externalInvoiceNo,
      supplierId: form.supplierId,
      sourceType: form.sourceType,
      ownershipType: form.ownershipType,
      projectId: form.ownershipType === 'PROJECT' ? normalizeOptionalId(form.projectId) ?? null : null,
      sourceLines: selected.value.map((line) => ({
        sourceLineId: line.sourceLineId,
        orderLineId: line.orderLineId ?? null,
        receiptLineId: line.receiptLineId ?? (form.sourceType === 'PURCHASE_RECEIPT' ? line.sourceLineId : null),
        outsourcingReceiptLineId: line.outsourcingReceiptLineId ?? (form.sourceType === 'OUTSOURCING_RECEIPT' ? line.sourceLineId : null),
        invoiceQuantity: String(line.invoiceQuantity),
        taxRate: String(line.taxRate ?? ''),
      })),
      remark: form.remark,
      version: detail.value?.version ?? 0,
      idempotencyKey: `purchase-invoice-${Date.now()}`,
    }
    const result = detail.value
      ? await financeInvoiceApi.purchaseInvoices.update(route.params.id as string, payload)
      : await financeInvoiceApi.purchaseInvoices.create(payload)
    await router.push({ name: 'finance-purchase-invoice-detail', params: { id: String(result.id) } })
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <MasterDataTableView :title="isEdit ? '编辑采购发票草稿' : '新增采购发票'" description="标准采购发票执行零容差三单匹配；外协结算只追溯外协来源和暂估差异。">
    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" type="info" title="采购发票表单加载中" :closable="false" />
      <el-alert v-if="saveDisabledReason" type="warning" :title="saveDisabledReason" :closable="false" />
    </template>
    <div class="finance-summary-strip">
      <div><span>标准采购发票</span><strong>采购订单、入库和发票逐行匹配</strong></div>
      <div><span>外协结算</span><strong>外协收货实际结算不回写库存价值</strong></div>
      <div><span>供应商</span><strong>{{ selectedSupplierName || '待选择' }}</strong></div>
      <div><span>项目/公共</span><strong>{{ ownershipTypeText(form.ownershipType) }} {{ selectedProjectName }}</strong></div>
      <div><span>净可开余额</span><strong>{{ formatFinanceAmount(selectedCandidateBalance) }}</strong></div>
    </div>
    <el-form label-position="top" class="finance-form">
      <div class="finance-form-grid">
        <el-form-item label="供应商">
          <el-select data-test="purchase-invoice-supplier" v-model="form.supplierId" filterable clearable :disabled="formReadOnly" placeholder="选择供应商" @change="searchCandidates">
            <el-option v-for="supplier in suppliers" :key="supplier.id" :label="supplier.name" :value="supplier.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源类型">
          <el-select data-test="purchase-invoice-source-type" v-model="form.sourceType" :disabled="formReadOnly" placeholder="选择来源类型" @change="searchCandidates">
            <el-option label="标准采购发票" value="PURCHASE_RECEIPT" />
            <el-option label="外协结算" value="OUTSOURCING_RECEIPT" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目/公共归属">
          <el-select v-model="form.ownershipType" :disabled="formReadOnly" placeholder="选择归属" @change="searchCandidates">
            <el-option label="公共" value="PUBLIC" />
            <el-option label="项目" value="PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目">
          <el-select v-model="form.projectId" filterable clearable :disabled="formReadOnly || form.ownershipType === 'PUBLIC'" placeholder="选择项目" @change="searchCandidates">
            <el-option v-for="project in projects" :key="project.id" :label="`${project.projectNo} ${project.name}`" :value="project.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="发票日期"><el-date-picker v-model="form.invoiceDate" name="purchase-invoice-date" :disabled="formReadOnly" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="选择发票日期" /></el-form-item>
        <el-form-item label="外部发票号码"><el-input v-model="form.externalInvoiceNo" :disabled="formReadOnly" clearable placeholder="填写外部发票号码" /></el-form-item>
        <el-form-item label="发票类型">
          <el-select v-model="form.invoiceType" :disabled="formReadOnly" placeholder="选择发票类型">
            <el-option label="增值税普通发票" value="GENERAL_VAT" />
            <el-option label="增值税专用发票" value="SPECIAL_VAT" />
            <el-option label="无票" value="NONE" />
          </el-select>
        </el-form-item>
      </div>
    </el-form>
    <el-form class="query-form" inline>
      <el-form-item label="来源关键词"><el-input data-test="purchase-invoice-source-keyword" v-model="candidateFilters.keyword" :disabled="formReadOnly" clearable placeholder="入库、外协收货或物料" /></el-form-item>
      <el-form-item><el-button type="primary" :disabled="formReadOnly" @click="searchCandidates">查询来源</el-button></el-form-item>
    </el-form>
    <div class="table-scroll">
      <el-table :data="candidates" :empty-text="candidatesLoading ? '来源加载中' : '当前条件下无可用采购或外协来源'">
        <el-table-column prop="sourceNo" label="来源单号" min-width="150" />
        <el-table-column prop="supplierName" label="供应商" min-width="150" show-overflow-tooltip />
        <el-table-column prop="lineNo" label="行号" min-width="80" />
        <el-table-column prop="materialName" label="物料" min-width="160" show-overflow-tooltip />
        <el-table-column prop="availableQuantity" label="可开票数量" min-width="130" align="right" />
        <el-table-column prop="invoicedQuantity" label="已开票数量" min-width="130" align="right" />
        <el-table-column prop="invoiceQuantity" label="发票数量" min-width="120" align="right" />
        <el-table-column prop="pretaxUnitPrice" label="未税单价" min-width="120" align="right" />
        <el-table-column prop="taxRate" label="税率" min-width="100" align="right" />
        <el-table-column label="净可开余额" min-width="120" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.availableAmount ?? row.totalAmount) }}</template></el-table-column>
        <el-table-column prop="totalAmount" label="含税金额" min-width="120" align="right" />
        <el-table-column label="操作" fixed="right" min-width="90"><template #default="{ row }"><el-button data-test="select-purchase-source-line" text :disabled="formReadOnly" :type="selected.some((item) => item.sourceLineId === row.sourceLineId) ? 'primary' : undefined" @click="selectLine(row)">{{ selected.some((item) => item.sourceLineId === row.sourceLineId) ? '已选' : '选择' }}</el-button></template></el-table-column>
      </el-table>
    </div>
    <el-pagination class="table-pagination" layout="total, prev, pager, next" :total="candidatePagination.total" :page-size="candidatePagination.pageSize" :current-page="candidatePagination.page" @current-change="changeCandidatePage" />
    <div class="finance-form-footer">
      <el-button @click="router.back()">取消</el-button>
      <el-button data-test="save-purchase-invoice" type="primary" :loading="submitting" :disabled="submitting || Boolean(saveDisabledReason)" @click="save">保存草稿</el-button>
    </div>
  </MasterDataTableView>
</template>
