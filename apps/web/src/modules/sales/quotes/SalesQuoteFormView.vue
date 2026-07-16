<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { masterDataApi, type MaterialRecord, type PartnerRecord } from '../../../shared/api/masterDataApi'
import { salesProjectApi, type SalesProjectSummary } from '../../../shared/api/salesProjectApi'
import {
  salesFulfillmentApi,
  type SalesQuoteDetailRecord,
  type SalesQuoteLinePayload,
} from '../../../shared/api/salesFulfillmentApi'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import BusinessReferenceSelect from '../../system/shared/BusinessReferenceSelect.vue'
import type { BusinessReferenceOption } from '../../system/shared/businessReferenceSelectTypes'
import { pageItems } from '../../system/shared/pageHelpers'
import {
  formatSalesDecimal,
  normalizeSalesId,
  optionalSalesId,
  quoteLineRequiredDate,
  quoteLineUntaxedAmount,
  quoteLineUntaxedUnitPrice,
  salesFulfillmentErrorMessage,
} from '../salesFulfillmentPageHelpers'

const route = useRoute()
const router = useRouter()
const customers = ref<PartnerRecord[]>([])
const projects = ref<SalesProjectSummary[]>([])
const materials = ref<MaterialRecord[]>([])
const editingRecord = ref<SalesQuoteDetailRecord | null>(null)
const loading = ref(false)
const error = ref('')
const editLoadFailed = ref(false)
const submitting = ref(false)
const form = reactive({
  customerId: '' as string | number | '',
  projectId: '' as string | number | '',
  quoteDate: '',
  validUntil: '',
  deliveryCommitment: '',
  defaultTaxRate: '0.130000',
  settlementMethod: '',
  paymentTermDays: '',
  paymentTerms: '',
  remark: '',
})
const line = reactive({
  materialId: '' as string | number | '',
  quantity: '',
  untaxedUnitPrice: '100.000000',
  taxIncludedUnitPrice: '',
  taxRate: '0.130000',
  untaxedAmount: '0.000000',
  taxAmount: '0.000000',
  taxIncludedAmount: '0.000000',
  promisedDate: '',
  remark: '',
})

const isEdit = computed(() => Boolean(route.params.id))
const pageTitle = computed(() => (isEdit.value ? '编辑销售报价' : '新建销售报价'))
const decimalScale = 1_000_000n
const quoteLines = computed(() => [line])

function mergeById<T extends { id: unknown }>(current: T[], incoming: T[]) {
  const merged = new Map(current.map((item) => [String(item.id), item]))
  incoming.forEach((item) => merged.set(String(item.id), item))
  return Array.from(merged.values())
}

function customerOption(customer: PartnerRecord): BusinessReferenceOption {
  return { id: customer.id, label: `${customer.code} ${customer.name}` }
}

function projectOption(project: SalesProjectSummary): BusinessReferenceOption {
  return { id: project.id, label: `${project.projectNo} ${project.name}` }
}

function materialOption(material: MaterialRecord): BusinessReferenceOption {
  return { id: material.id, label: `${material.code} ${material.name} / ${material.unitName}` }
}

async function loadCustomerOptions(keyword: string) {
  const page = await masterDataApi.customers.list({
    keyword,
    status: 'ENABLED',
    page: 1,
    pageSize: 50,
  })
  const items = pageItems(page)
  customers.value = mergeById(customers.value, items)
  return items.map(customerOption)
}

async function loadProjectOptions(keyword: string) {
  const page = await salesProjectApi.projects.list({
    keyword,
    status: 'ACTIVE',
    page: 1,
    pageSize: 50,
  })
  const items = pageItems(page)
  projects.value = mergeById(projects.value, items)
  return items.map(projectOption)
}

async function loadMaterialOptions(keyword: string) {
  const page = await masterDataApi.materials.list({
    keyword,
    status: 'ENABLED',
    page: 1,
    pageSize: 50,
  })
  const items = pageItems(page)
  materials.value = mergeById(materials.value, items)
  return items.map(materialOption)
}

async function loadReferences() {
  await Promise.all([
    loadCustomerOptions(''),
    loadProjectOptions(''),
    loadMaterialOptions(''),
  ])
}

async function loadRecord() {
  if (!route.params.id) {
    return
  }
  try {
    const detail = await salesFulfillmentApi.quotes.get(normalizeSalesId(route.params.id))
    editingRecord.value = detail
    form.customerId = String(detail.customerId)
    form.projectId = detail.projectId ? String(detail.projectId) : ''
    form.quoteDate = detail.quoteDate
    form.validUntil = detail.validUntil
    form.deliveryCommitment = detail.deliveryCommitment ?? ''
    form.defaultTaxRate = detail.defaultTaxRate
    form.settlementMethod = detail.settlementMethod ?? ''
    form.paymentTermDays = detail.paymentTermDays === null || detail.paymentTermDays === undefined ? '' : String(detail.paymentTermDays)
    form.paymentTerms = detail.paymentTerms ?? ''
    form.remark = detail.remark ?? ''
    const firstLine = detail.lines[0]
    if (firstLine) {
      line.materialId = String(firstLine.materialId)
      line.quantity = firstLine.quantity
      line.untaxedUnitPrice = quoteLineUntaxedUnitPrice(firstLine) ?? ''
      line.taxIncludedUnitPrice = firstLine.taxIncludedUnitPrice
      line.taxRate = firstLine.taxRate
      line.untaxedAmount = quoteLineUntaxedAmount(firstLine) ?? ''
      line.taxAmount = firstLine.taxAmount
      line.taxIncludedAmount = firstLine.taxIncludedAmount
      line.promisedDate = quoteLineRequiredDate(firstLine) ?? ''
      line.remark = firstLine.remark ?? ''
    }
  } catch (caught) {
    editLoadFailed.value = true
    error.value = salesFulfillmentErrorMessage(caught)
  }
}

function linePayload(): SalesQuoteLinePayload | null {
  const material = materials.value.find((item) => String(item.id) === String(line.materialId))
  if (!material || !line.quantity.trim() || !line.untaxedUnitPrice.trim() || !line.taxIncludedUnitPrice.trim() || !line.taxRate.trim()) {
    error.value = '请完整填写客户、报价日期、有效期、物料、数量、未税单价、含税单价和税率'
    return null
  }
  const untaxedAmount = multiplyDecimalStrings(line.quantity, line.untaxedUnitPrice)
  const taxIncludedAmount = multiplyDecimalStrings(line.quantity, line.taxIncludedUnitPrice)
  const taxAmount = subtractDecimalStrings(taxIncludedAmount, untaxedAmount)
  if (untaxedAmount === null || taxIncludedAmount === null || taxAmount === null) {
    error.value = '数量、单价和金额必须是最多 6 位小数的非负 decimal 字符串'
    return null
  }
  return {
    lineNo: 1,
    materialId: normalizeSalesId(line.materialId),
    unitId: material.unitId,
    quantity: line.quantity.trim(),
    untaxedUnitPrice: line.untaxedUnitPrice.trim(),
    taxIncludedUnitPrice: line.taxIncludedUnitPrice.trim(),
    taxRate: line.taxRate.trim(),
    untaxedAmount,
    taxAmount,
    taxIncludedAmount,
    ...(line.promisedDate.trim() ? { promisedDate: line.promisedDate.trim() } : {}),
    ...(line.remark.trim() ? { remark: line.remark.trim() } : {}),
  }
}

function decimalToMicro(value: string): bigint | null {
  const text = value.trim()
  if (!/^\d+(?:\.\d{1,6})?$/.test(text)) {
    return null
  }
  const [integerPart, decimalPart = ''] = text.split('.')
  return BigInt(integerPart) * decimalScale + BigInt(decimalPart.padEnd(6, '0'))
}

function microToDecimal(value: bigint): string {
  if (value < 0n) {
    return `-${microToDecimal(-value)}`
  }
  const integerPart = value / decimalScale
  const decimalPart = value % decimalScale
  return `${integerPart.toString()}.${decimalPart.toString().padStart(6, '0')}`
}

function multiplyDecimalStrings(left: string, right: string): string | null {
  const leftValue = decimalToMicro(left)
  const rightValue = decimalToMicro(right)
  if (leftValue === null || rightValue === null) {
    return null
  }
  return microToDecimal((leftValue * rightValue + decimalScale / 2n) / decimalScale)
}

function subtractDecimalStrings(left: string | null, right: string | null): string | null {
  if (left === null || right === null) {
    return null
  }
  const leftValue = decimalToMicro(left)
  const rightValue = decimalToMicro(right)
  if (leftValue === null || rightValue === null || leftValue < rightValue) {
    return null
  }
  return microToDecimal(leftValue - rightValue)
}

async function saveQuote() {
  if (submitting.value) {
    return
  }
  const payloadLine = linePayload()
  if (!payloadLine || !form.customerId || !form.quoteDate || !form.validUntil) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    const payload = {
      customerId: normalizeSalesId(form.customerId),
      projectId: optionalSalesId(form.projectId) ?? null,
      quoteDate: form.quoteDate,
      validUntil: form.validUntil,
      deliveryCommitment: form.deliveryCommitment,
      currency: 'CNY' as const,
      priceMode: 'TAX_INCLUDED' as const,
      defaultTaxRate: form.defaultTaxRate,
      settlementMethod: form.settlementMethod,
      paymentTermDays: form.paymentTermDays ? Number(form.paymentTermDays) : null,
      paymentTerms: form.paymentTerms,
      remark: form.remark,
      lines: [payloadLine],
    }
    const result = isEdit.value && editingRecord.value
      ? await salesFulfillmentApi.quotes.update(editingRecord.value.id, { ...payload, version: editingRecord.value.version })
      : await salesFulfillmentApi.quotes.create(payload)
    await router.push({ name: 'sales-quote-detail', params: { id: String(result.id) } })
  } catch (caught) {
    error.value = salesFulfillmentErrorMessage(caught)
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    await loadReferences()
    await loadRecord()
  } catch (caught) {
    error.value = salesFulfillmentErrorMessage(caught)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <MasterDataTableView
    :title="pageTitle"
    description="客户、项目、税价和报价行均按冻结 DTO 提交，金额由后端形成可信结果。"
  >
    <template #alerts>
      <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
      <el-alert v-if="loading" class="page-alert" type="info" title="销售报价表单加载中" :closable="false" />
    </template>

    <section v-if="editLoadFailed" class="section-block">
      <h2>无法编辑销售报价</h2>
      <p>{{ error || '销售报价不存在或无权编辑' }}</p>
      <el-button data-test="back-sales-quotes" type="primary" @click="router.push({ name: 'sales-quotes' })">
        返回销售报价列表
      </el-button>
    </section>

    <template v-else>
    <el-form label-position="top" class="sales-quote-form">
      <section class="section-block">
        <h2>报价主体</h2>
        <div class="sales-quote-form-grid">
          <el-form-item label="客户">
            <BusinessReferenceSelect
              v-model="form.customerId"
              placeholder="搜索客户编码或名称"
              :load-options="loadCustomerOptions"
              data-test="quote-customer-select"
            />
          </el-form-item>
          <el-form-item label="项目">
            <BusinessReferenceSelect
              v-model="form.projectId"
              placeholder="普通报价可留空，项目报价搜索项目编号或名称"
              :load-options="loadProjectOptions"
              data-test="quote-project-select"
            />
          </el-form-item>
          <el-form-item label="报价日期">
            <el-date-picker
              v-model="form.quoteDate"
              name="quote-date"
              type="date"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              value-on-clear=""
              placeholder="报价日期"
            />
          </el-form-item>
          <el-form-item label="有效期">
            <el-date-picker
              v-model="form.validUntil"
              name="quote-valid-until"
              type="date"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              value-on-clear=""
              placeholder="有效期"
            />
          </el-form-item>
          <el-form-item label="默认税率">
            <el-input
              v-model="form.defaultTaxRate"
              name="quote-default-tax-rate"
              inputmode="decimal"
              placeholder="0.130000"
            />
          </el-form-item>
          <el-form-item label="账期">
            <el-input
              v-model="form.paymentTermDays"
              name="quote-payment-days"
              inputmode="numeric"
              placeholder="可选，单位：天"
            />
          </el-form-item>
        </div>
      </section>

      <section class="section-block line-editor">
        <h2>报价明细</h2>
        <div class="sales-quote-line-editor">
          <div class="table-scroll">
            <el-table :data="quoteLines" empty-text="暂无报价明细" stripe>
              <el-table-column label="行号" width="72">
                <template #default>
                  1
                </template>
              </el-table-column>
              <el-table-column label="物料" min-width="240">
                <template #default>
                  <BusinessReferenceSelect
                    v-model="line.materialId"
                    placeholder="搜索物料编码或名称"
                    :load-options="loadMaterialOptions"
                    data-test="quote-line-material-select"
                  />
                </template>
              </el-table-column>
              <el-table-column label="数量" width="150" align="right">
                <template #default>
                  <el-input
                    v-model="line.quantity"
                    name="quote-line-quantity"
                    inputmode="decimal"
                    placeholder="> 0"
                  />
                </template>
              </el-table-column>
              <el-table-column label="未税单价" width="150" align="right">
                <template #default>
                  <el-input
                    v-model="line.untaxedUnitPrice"
                    name="quote-line-untaxed-price"
                    inputmode="decimal"
                    placeholder=">= 0"
                  />
                </template>
              </el-table-column>
              <el-table-column label="含税单价" width="150" align="right">
                <template #default>
                  <el-input
                    v-model="line.taxIncludedUnitPrice"
                    name="quote-line-tax-included-price"
                    inputmode="decimal"
                    placeholder=">= 0"
                  />
                </template>
              </el-table-column>
              <el-table-column label="税率" width="120" align="right">
                <template #default>
                  <el-input
                    v-model="line.taxRate"
                    name="quote-line-tax-rate"
                    inputmode="decimal"
                    placeholder="0.13"
                  />
                </template>
              </el-table-column>
              <el-table-column label="承诺日期" width="160">
                <template #default>
                  <el-date-picker
                    v-model="line.promisedDate"
                    name="quote-line-promised-date"
                    type="date"
                    format="YYYY-MM-DD"
                    value-format="YYYY-MM-DD"
                    value-on-clear=""
                    placeholder="承诺日期"
                  />
                </template>
              </el-table-column>
              <el-table-column label="金额摘要" min-width="190">
                <template #default>
                  <span class="quote-line-summary">
                    含税 {{ formatSalesDecimal(line.taxIncludedUnitPrice) }} CNY · 税率 {{ formatSalesDecimal(line.taxRate) }}
                  </span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </section>
    </el-form>

    <div class="form-footer">
      <el-button @click="router.push({ name: 'sales-quotes' })">取消</el-button>
      <el-button data-test="save-sales-quote" type="primary" :loading="submitting" @click="saveQuote">保存</el-button>
    </div>
    </template>
  </MasterDataTableView>
</template>

<style scoped>
.sales-quote-form {
  padding: 14px;
}

.sales-quote-form-grid {
  display: grid;
  gap: 0 14px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.line-editor {
  margin-top: 12px;
}

.line-editor h2 {
  font-size: 16px;
  margin: 0 0 12px;
}

.sales-quote-line-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.quote-line-summary {
  color: var(--qherp-muted);
  font-size: 13px;
  line-height: 32px;
}

.sales-quote-form :deep(.el-select),
.sales-quote-form :deep(.el-input),
.sales-quote-form :deep(.el-date-editor.el-input) {
  width: 100%;
}

.form-footer {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  padding: 12px 14px 14px;
  border-top: 1px solid var(--qherp-border);
}

@media (max-width: 760px) {
  .sales-quote-form-grid {
    grid-template-columns: 1fr;
  }

  .form-footer {
    align-items: stretch;
    flex-direction: column-reverse;
  }
}
</style>
