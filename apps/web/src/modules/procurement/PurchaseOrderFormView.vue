<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { queryWithReturnTo, routeReturnTo } from '../../shared/navigation/navigationReturn'
import { masterDataApi, type MaterialRecord, type PartnerRecord } from '../../shared/api/masterDataApi'
import {
  procurementApi,
  type PriceAgreementSummaryRecord,
  type ProcurementInquirySummaryRecord,
  type ProcurementMode,
  type ProcurementRequisitionDetailRecord,
  type ProcurementRequisitionLineRecord,
  type PurchaseOrderDetailRecord,
  type PurchaseOrderPayload,
  type ResourceId,
  type SupplierQuoteRecord,
} from '../../shared/api/procurementApi'
import { salesProjectApi, type SalesProjectSummary } from '../../shared/api/salesProjectApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import PurchaseOrderLineEditor from './PurchaseOrderLineEditor.vue'
import PurchaseOrderStatusTag from './PurchaseOrderStatusTag.vue'
import {
  type PurchaseOrderLineDraft,
  type PurchaseOrderSourceOption,
  formatProcurementAmount,
  newPurchaseOrderLine,
  normalizeOptionalId,
  normalizeRequiredId,
  procurementApprovalStatusLabel,
  procurementErrorMessage,
  procurementModeFrom,
  procurementModeDisplay,
  procurementPriceSourceDisplay,
  validateProcurementDecimal,
  validatePurchaseQuantity,
  validatePurchaseUnitPrice,
} from './procurementPageHelpers'

const route = useRoute()
const router = useRouter()
const suppliers = ref<PartnerRecord[]>([])
const materials = ref<MaterialRecord[]>([])
const projects = ref<SalesProjectSummary[]>([])
const requisitionLineOptions = ref<PurchaseOrderSourceOption[]>([])
const quoteLineOptions = ref<PurchaseOrderSourceOption[]>([])
const priceAgreementLineOptions = ref<PurchaseOrderSourceOption[]>([])
const editingRecord = ref<PurchaseOrderDetailRecord | null>(null)
const referenceLoading = ref(true)
const loading = ref(false)
const referenceError = ref('')
const formError = ref('')
const formSubmitting = ref(false)
const lineErrors = ref<Record<number, string>>({})
const form = reactive({
  supplierId: '' as ResourceId | '',
  procurementMode: 'PUBLIC' as ProcurementMode,
  projectId: '' as ResourceId | '',
  orderDate: '',
  expectedArrivalDate: '',
  remark: '',
})
const lines = ref<PurchaseOrderLineDraft[]>([newPurchaseOrderLine()])

const isEdit = computed(() => Boolean(route.params.id))
const isDraftRecord = computed(() => !editingRecord.value || editingRecord.value.status === 'DRAFT')
const canEditForm = computed(() => isDraftRecord.value && (!isEdit.value || Boolean(editingRecord.value)))
const canSubmit = computed(() => !formSubmitting.value && canEditForm.value)
const pageTitle = computed(() => (isEdit.value ? '编辑采购订单' : '新建采购订单'))
const selectedProject = computed(() => projects.value.find((project) => String(project.id) === String(form.projectId)))

function lineMaterialLabel(line: { materialCode?: string | null; materialName?: string | null }) {
  return [line.materialCode, line.materialName].filter(Boolean).join(' ')
}

function requisitionOption(requisition: ProcurementRequisitionDetailRecord, line: ProcurementRequisitionLineRecord): PurchaseOrderSourceOption {
  return {
    id: line.id,
    label: `${requisition.requisitionNo} / 行 ${line.lineNo} / ${lineMaterialLabel(line)}`,
    materialId: line.materialId,
    materialCode: line.materialCode,
    materialName: line.materialName,
    unitId: line.unitId,
    unitName: line.unitName,
    quantity: line.remainingQuantity || line.quantity,
    supplierId: line.suggestedSupplierId ?? null,
    procurementMode: procurementModeFrom(line) ?? procurementModeFrom(requisition) ?? null,
    projectId: line.projectId ?? requisition.projectId ?? null,
    projectCode: line.projectCode ?? requisition.projectCode ?? null,
    projectName: line.projectName ?? requisition.projectName ?? null,
    taxRate: line.taxRate ?? null,
    currency: 'CNY',
  }
}

function quoteOption(quote: SupplierQuoteRecord): PurchaseOrderSourceOption {
  return {
    id: quote.id,
    label: `${quote.quoteNo} / ${quote.supplierName} / ${lineMaterialLabel(quote)}`,
    materialId: quote.materialId,
    materialCode: quote.materialCode,
    materialName: quote.materialName,
    quantity: quote.quantity,
    supplierId: quote.supplierId,
    procurementMode: procurementModeFrom(quote) ?? null,
    projectId: quote.projectId ?? null,
    projectCode: quote.projectCode ?? null,
    projectName: quote.projectName ?? null,
    taxRate: quote.taxRate ?? null,
    taxIncludedUnitPrice: quote.taxIncludedUnitPrice ?? null,
    taxExcludedUnitPrice: quote.taxExcludedUnitPrice ?? null,
    currency: quote.currency ?? 'CNY',
  }
}

function agreementOption(agreement: PriceAgreementSummaryRecord): PurchaseOrderSourceOption {
  return {
    id: agreement.id,
    label: `${agreement.agreementNo} / ${agreement.supplierName} / ${lineMaterialLabel(agreement)}`,
    materialId: agreement.materialId,
    materialCode: agreement.materialCode,
    materialName: agreement.materialName,
    supplierId: agreement.supplierId,
    procurementMode: procurementModeFrom(agreement) ?? null,
    projectId: agreement.projectId ?? null,
    projectCode: agreement.projectCode ?? null,
    projectName: agreement.projectName ?? null,
    taxRate: agreement.taxRate ?? null,
    taxIncludedUnitPrice: agreement.taxIncludedUnitPrice ?? null,
    taxExcludedUnitPrice: agreement.taxExcludedUnitPrice ?? null,
    currency: agreement.currency ?? 'CNY',
  }
}

function applySourceContext(option: PurchaseOrderSourceOption) {
  if (option.supplierId !== undefined && option.supplierId !== null) {
    form.supplierId = option.supplierId
  }
  if (option.procurementMode) {
    form.procurementMode = option.procurementMode
  }
  const explicitProjectId = normalizeOptionalId(option.projectId ?? '')
  if (explicitProjectId !== undefined) {
    form.projectId = explicitProjectId
  } else if (option.procurementMode === 'PUBLIC') {
    form.projectId = ''
  }
}

async function loadReferences() {
  referenceLoading.value = true
  referenceError.value = ''
  try {
    const [supplierPage, materialPage, projectPage, inquiryPage, agreementPage] = await Promise.all([
      masterDataApi.suppliers.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
      masterDataApi.materials.list({
        keyword: '',
        status: 'ENABLED',
        sourceType: 'PURCHASED',
        page: 1,
        pageSize: 200,
      }),
      salesProjectApi.projects.list({ keyword: '', status: 'ACTIVE', page: 1, pageSize: 200 }),
      procurementApi.inquiries.list({ keyword: '', page: 1, pageSize: 50 }),
      procurementApi.priceAgreements.list({ status: 'ACTIVE', page: 1, pageSize: 50 }),
    ])
    suppliers.value = pageItems(supplierPage)
    materials.value = pageItems(materialPage)
    projects.value = pageItems(projectPage)
    const inquiries = pageItems(inquiryPage) as ProcurementInquirySummaryRecord[]
    const quotePages = await Promise.all(inquiries.map((inquiry) => (
      procurementApi.quotes.list(inquiry.id, { page: 1, pageSize: 50 })
    )))
    quoteLineOptions.value = quotePages.flatMap((page) => pageItems(page).map(quoteOption))
    priceAgreementLineOptions.value = pageItems(agreementPage).map(agreementOption)
  } catch (caught) {
    suppliers.value = []
    materials.value = []
    projects.value = []
    quoteLineOptions.value = []
    priceAgreementLineOptions.value = []
    referenceError.value = procurementErrorMessage(caught)
  } finally {
    referenceLoading.value = false
  }
}

async function loadRecord() {
  if (!route.params.id) {
    return
  }
  loading.value = true
  formError.value = ''
  try {
    const detail = await procurementApi.orders.get(route.params.id as ResourceId)
    editingRecord.value = detail
    form.supplierId = detail.supplierId
    form.procurementMode = procurementModeFrom(detail) ?? 'PUBLIC'
    form.projectId = normalizeOptionalId(detail.projectId ?? '') ?? ''
    form.orderDate = detail.orderDate
    form.expectedArrivalDate = detail.expectedArrivalDate ?? ''
    form.remark = detail.remark ?? ''
    lines.value = detail.lines.map((line) => ({
      lineNo: line.lineNo,
      materialId: line.materialId,
      unitId: line.unitId,
      unitName: line.unitName,
      quantity: String(line.quantity),
      unitPrice: String(line.unitPrice),
      procurementMode: procurementModeFrom(line) ?? procurementModeFrom(detail) ?? null,
      projectId: line.projectId ?? detail.projectId ?? null,
      projectCode: line.projectCode ?? detail.projectCode ?? null,
      projectName: line.projectName ?? detail.projectName ?? null,
      requisitionLineId: line.requisitionLineId ?? null,
      requisitionSourceLabel: line.requisitionNo
        ? `${line.requisitionNo} / 行 ${line.lineNo} / ${lineMaterialLabel(line)}`
        : '',
      quoteLineId: line.quoteLineId ?? line.sourceQuoteLineId ?? null,
      quoteSourceLabel: line.quoteNo ? `${line.quoteNo} / ${lineMaterialLabel(line)}` : '',
      priceAgreementLineId: line.priceAgreementLineId ?? null,
      priceAgreementSourceLabel: line.agreementNo ? `${line.agreementNo} / ${lineMaterialLabel(line)}` : '',
      taxRate: line.taxRate ?? '',
      taxExcludedUnitPrice: line.taxExcludedUnitPrice ?? '',
      taxIncludedUnitPrice: line.taxIncludedUnitPrice ?? '',
      currency: line.currency ?? 'CNY',
      expectedArrivalDate: line.expectedArrivalDate ?? '',
      remark: line.remark ?? '',
    }))
  } catch (caught) {
    formError.value = procurementErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function loadRequisitionSource() {
  if (isEdit.value || !route.query.requisitionId) {
    return
  }
  const requisition = await procurementApi.requisitions.get(route.query.requisitionId as ResourceId)
  if (requisition.status !== 'APPROVED' && requisition.status !== 'PARTIALLY_ORDERED') {
    formError.value = '仅审批通过且可转单的请购可创建采购订单'
    return
  }
  const options = requisition.lines.map((line) => requisitionOption(requisition, line))
  requisitionLineOptions.value = options
  const requisitionMode = procurementModeFrom(requisition) ?? 'PUBLIC'
  form.procurementMode = requisitionMode
  form.projectId = requisitionMode === 'PROJECT'
    ? normalizeOptionalId(requisition.projectId ?? '') ?? ''
    : ''
  const firstOption = options[0]
  if (firstOption?.supplierId !== undefined && firstOption.supplierId !== null) {
    form.supplierId = firstOption.supplierId
  }
  lines.value = options.map((option, index) => ({
    ...newPurchaseOrderLine((index + 1) * 10),
    materialId: option.materialId ?? '',
    unitId: option.unitId ?? '',
    unitName: option.unitName ?? '',
    quantity: option.quantity ?? '',
    procurementMode: option.procurementMode ?? null,
    projectId: option.projectId ?? null,
    projectCode: option.projectCode ?? null,
    projectName: option.projectName ?? null,
    requisitionLineId: option.id,
    requisitionSourceLabel: option.label,
    taxRate: option.taxRate ?? '',
    taxExcludedUnitPrice: option.taxExcludedUnitPrice ?? '',
    taxIncludedUnitPrice: option.taxIncludedUnitPrice ?? '',
    currency: option.currency ?? 'CNY',
    expectedArrivalDate: requisition.requiredDate,
  }))
}

function validateForm(): PurchaseOrderPayload | null {
  const supplierId = normalizeRequiredId(form.supplierId)
  const projectId = normalizeRequiredId(form.projectId)
  if (supplierId === null || !form.orderDate.trim() || lines.value.length === 0) {
    formError.value = '请完整填写供应商、订单日期和明细'
    lineErrors.value = {}
    return null
  }
  if (form.procurementMode === 'PROJECT' && projectId === null) {
    formError.value = '项目专采采购订单必须选择项目'
    lineErrors.value = {}
    return null
  }

  const nextLineErrors: Record<number, string> = {}
  const duplicateSourceCombinations = new Set<string>()
  const payloadLines = []

  for (const line of lines.value) {
    const materialId = normalizeRequiredId(line.materialId)
    if (materialId === null) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行请选择物料`
      continue
    }
    const quantityResult = validatePurchaseQuantity(line.quantity)
    if (quantityResult.payloadValue === null) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行${quantityResult.message ?? '数量不正确'}`
      continue
    }
    const unitPriceResult = validatePurchaseUnitPrice(line.unitPrice)
    if (unitPriceResult.payloadValue === null) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行${unitPriceResult.message ?? '单价不正确'}`
      continue
    }
    const duplicateKey = [
      String(materialId),
      String(line.requisitionLineId ?? ''),
      String(line.quoteLineId ?? ''),
      String(line.priceAgreementLineId ?? ''),
    ].join('|')
    if (duplicateSourceCombinations.has(duplicateKey)) {
      formError.value = '同一采购订单内来源组合不能重复'
      lineErrors.value = {}
      return null
    }
    duplicateSourceCombinations.add(duplicateKey)
    const unitId = normalizeRequiredId(line.unitId)
    const taxRateResult = line.taxRate.trim()
      ? validateProcurementDecimal(line.taxRate, { label: '税率', allowZero: true })
      : { payloadValue: null, message: null }
    if (taxRateResult.message) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行${taxRateResult.message}`
      continue
    }
    const taxExcludedUnitPriceResult = line.taxExcludedUnitPrice.trim()
      ? validateProcurementDecimal(line.taxExcludedUnitPrice, { label: '未税单价', allowZero: true })
      : { payloadValue: null, message: null }
    if (taxExcludedUnitPriceResult.message) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行${taxExcludedUnitPriceResult.message}`
      continue
    }
    const taxIncludedUnitPriceResult = line.taxIncludedUnitPrice.trim()
      ? validateProcurementDecimal(line.taxIncludedUnitPrice, { label: '含税单价', allowZero: true })
      : { payloadValue: null, message: null }
    if (taxIncludedUnitPriceResult.message) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行${taxIncludedUnitPriceResult.message}`
      continue
    }
    payloadLines.push({
      lineNo: line.lineNo,
      materialId,
      ...(unitId !== null ? { unitId } : {}),
      quantity: quantityResult.payloadValue,
      unitPrice: unitPriceResult.payloadValue,
      ...(taxRateResult.payloadValue ? { taxRate: taxRateResult.payloadValue } : {}),
      ...(taxExcludedUnitPriceResult.payloadValue ? { taxExcludedUnitPrice: taxExcludedUnitPriceResult.payloadValue } : {}),
      ...(taxIncludedUnitPriceResult.payloadValue ? { taxIncludedUnitPrice: taxIncludedUnitPriceResult.payloadValue } : {}),
      ...(line.requisitionLineId ? { requisitionLineId: line.requisitionLineId } : {}),
      ...(line.quoteLineId ? { quoteLineId: line.quoteLineId } : {}),
      ...(line.priceAgreementLineId ? { priceAgreementLineId: line.priceAgreementLineId } : {}),
      ...(line.expectedArrivalDate.trim() ? { expectedArrivalDate: line.expectedArrivalDate.trim() } : {}),
      ...(line.remark.trim() ? { remark: line.remark.trim() } : {}),
    })
  }

  lineErrors.value = nextLineErrors
  if (Object.keys(nextLineErrors).length > 0) {
    formError.value = ''
    return null
  }

  formError.value = ''
  return {
    supplierId,
    procurementMode: form.procurementMode,
    projectId: form.procurementMode === 'PROJECT' ? projectId : null,
    orderDate: form.orderDate.trim(),
    ...(form.expectedArrivalDate.trim() ? { expectedArrivalDate: form.expectedArrivalDate.trim() } : {}),
    ...(form.remark.trim() ? { remark: form.remark.trim() } : {}),
    lines: payloadLines,
  }
}

function exceptionApprovalText(record: PurchaseOrderDetailRecord) {
  if (record.exceptionApprovalStatus === 'NOT_REQUIRED') {
    return '不需要'
  }
  return record.exceptionApprovalStatus || record.exceptionReason || '未提交'
}

function sourceText(record: PurchaseOrderDetailRecord) {
  return [
    `请购 ${record.requisitionNo || '-'}`,
    `报价 ${record.quoteNo || '-'}`,
    `协议 ${record.agreementNo || '-'}`,
  ].join(' / ')
}

function priceSourceText(record: PurchaseOrderDetailRecord) {
  return procurementPriceSourceDisplay(record)
}

async function saveOrder() {
  if (formSubmitting.value) {
    return
  }
  const currentRecord = editingRecord.value
  if (isEdit.value && !currentRecord) {
    formError.value = '采购订单加载失败，不能保存'
    return
  }
  if (currentRecord && currentRecord.status !== 'DRAFT') {
    formError.value = '仅草稿采购订单可编辑'
    return
  }
  const payload = validateForm()
  if (!payload) {
    return
  }

  formSubmitting.value = true
  try {
    let result: PurchaseOrderDetailRecord
    if (isEdit.value) {
      if (!currentRecord) {
        formError.value = '采购订单加载失败，不能保存'
        return
      }
      result = await procurementApi.orders.update(currentRecord.id, { ...payload, version: currentRecord.version })
    } else {
      result = await procurementApi.orders.create(payload)
    }
    await router.push({
      name: 'procurement-order-detail',
      params: { id: String(result.id) },
      query: queryWithReturnTo({}, routeReturnTo(route)),
    })
  } catch (caught) {
    formError.value = procurementErrorMessage(caught)
  } finally {
    formSubmitting.value = false
  }
}

function cancel() {
  if (editingRecord.value) {
    void router.push({
      name: 'procurement-order-detail',
      params: { id: String(editingRecord.value.id) },
      query: queryWithReturnTo({}, routeReturnTo(route)),
    })
    return
  }
  void router.push({ name: 'procurement-orders' })
}

watch(() => form.procurementMode, (mode) => {
  if (mode === 'PUBLIC') {
    form.projectId = ''
  }
})

onMounted(async () => {
  await loadReferences()
  await loadRecord()
  await loadRequisitionSource()
})
</script>

<template>
  <MasterDataTableView :title="pageTitle" description="维护采购订单草稿，确认后可基于订单创建采购入库。">
    <template #alerts>
      <el-alert v-if="referenceError" class="state-alert" type="error" :title="referenceError" :closable="false" />
      <el-alert v-if="formError" class="state-alert" type="error" :title="formError" :closable="false" />
      <el-alert
        v-if="editingRecord && !isDraftRecord"
        class="state-alert"
        type="warning"
        title="仅草稿采购订单可编辑"
        :closable="false"
      />
      <el-alert
        v-if="loading || referenceLoading"
        class="state-alert"
        type="info"
        title="采购订单表单加载中"
        :closable="false"
      />
    </template>

    <el-form label-position="top" class="purchase-order-form">
      <div v-if="editingRecord" class="edit-status">
        <span>{{ editingRecord.orderNo }}</span>
        <PurchaseOrderStatusTag :status="editingRecord.status" />
      </div>
      <section v-if="editingRecord" class="order-context-summary">
        <div>
          <span>采购模式/项目</span>
          <strong>{{ procurementModeDisplay(editingRecord.procurementMode, editingRecord.projectCode, editingRecord.projectName) }}</strong>
        </div>
        <div>
          <span>来源组合</span>
          <strong>{{ sourceText(editingRecord) }}</strong>
        </div>
        <div>
          <span>价格来源</span>
          <strong>{{ priceSourceText(editingRecord) }}</strong>
        </div>
        <div>
          <span>审批/例外</span>
          <strong>审批状态：{{ procurementApprovalStatusLabel(editingRecord.approvalStatus, editingRecord.approvalStatusName) }} / 例外审批：{{ exceptionApprovalText(editingRecord) }}</strong>
        </div>
        <div>
          <span>税价</span>
          <strong>
            未税单价 {{ formatProcurementAmount(editingRecord.taxExcludedUnitPrice) }} /
            含税单价 {{ formatProcurementAmount(editingRecord.taxIncludedUnitPrice) }} /
            税率 {{ formatProcurementAmount(editingRecord.taxRate) }} /
            {{ editingRecord.currency || 'CNY' }}
          </strong>
        </div>
      </section>
      <div class="purchase-order-form-grid">
        <el-form-item label="采购模式">
          <el-select
            v-model="form.procurementMode"
            data-test="purchase-order-procurement-mode"
            style="width: 100%"
            :disabled="!canEditForm"
          >
            <el-option label="公共采购" value="PUBLIC" />
            <el-option label="项目专采" value="PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目">
          <el-select
            v-model="form.projectId"
            data-test="purchase-order-project-id"
            clearable
            filterable
            placeholder="选择项目"
            style="width: 100%"
            :disabled="!canEditForm || form.procurementMode === 'PUBLIC'"
          >
            <el-option
              v-for="project in projects"
              :key="project.id"
              :label="`${project.projectNo} ${project.name}`"
              :value="project.id"
            />
          </el-select>
          <span class="field-hint">
            {{ form.procurementMode === 'PROJECT' ? procurementModeDisplay('PROJECT', selectedProject?.projectNo, selectedProject?.name) : '公共采购' }}
          </span>
        </el-form-item>
        <el-form-item label="供应商">
          <el-select
            v-model="form.supplierId"
            data-test="purchase-order-supplier-id"
            filterable
            placeholder="请选择启用供应商"
            style="width: 100%"
            :disabled="!canEditForm"
          >
            <el-option
              v-for="supplier in suppliers"
              :key="supplier.id"
              :label="`${supplier.code} ${supplier.name}`"
              :value="supplier.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="订单日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD"
            v-model="form.orderDate"
            name="purchase-order-date"
            placeholder="选择日期"
            :disabled="!canEditForm"
          />
        </el-form-item>
        <el-form-item label="默认预计到货日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD"
            v-model="form.expectedArrivalDate"
            name="purchase-order-expected-date"
            placeholder="选择日期"
            :disabled="!canEditForm"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" name="purchase-order-remark" placeholder="可选" :disabled="!canEditForm" />
        </el-form-item>
      </div>
      <el-alert
        class="state-alert"
        type="info"
        title="例外审批根据价格来源与协议规则自动判断，确认后发起例外审批；表单不编辑审批状态。"
        :closable="false"
      />
      <el-form-item label="采购明细">
        <PurchaseOrderLineEditor
          v-model:lines="lines"
          :materials="materials"
          :procurement-mode="form.procurementMode"
          :project-code="selectedProject?.projectNo"
          :project-name="selectedProject?.name"
          :requisition-line-options="requisitionLineOptions"
          :quote-line-options="quoteLineOptions"
          :price-agreement-line-options="priceAgreementLineOptions"
          :errors="lineErrors"
          :read-only="!canEditForm"
          @source-selected="applySourceContext"
        />
      </el-form-item>
    </el-form>

    <div class="form-footer">
      <el-button @click="cancel">取消</el-button>
      <el-button
        data-test="save-purchase-order"
        type="primary"
        :loading="formSubmitting"
        :disabled="!canSubmit"
        @click="saveOrder"
      >
        保存
      </el-button>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.purchase-order-form {
  padding: 14px;
}

.purchase-order-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.edit-status {
  align-items: center;
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.edit-status span {
  color: var(--qherp-muted);
}

.order-context-summary {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-bottom: 12px;
}

.order-context-summary > div {
  border: 1px solid var(--qherp-border);
  border-radius: 6px;
  min-width: 0;
  padding: 10px 12px;
}

.order-context-summary span {
  color: var(--qherp-muted);
  display: block;
  font-size: 12px;
  margin-bottom: 6px;
}

.order-context-summary strong {
  font-size: 13px;
  font-weight: 600;
  word-break: break-word;
}

.field-hint {
  color: var(--qherp-muted);
  display: block;
  font-size: 12px;
  margin-top: 6px;
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 14px 14px;
  border-top: 1px solid var(--qherp-border);
}

@media (max-width: 760px) {
  .purchase-order-form-grid,
  .order-context-summary {
    grid-template-columns: 1fr;
  }

  .form-footer {
    align-items: stretch;
    flex-direction: column-reverse;
  }
}
</style>
