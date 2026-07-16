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
const submitting = ref(false)
const form = reactive({
  customerId: '',
  projectId: '',
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
  materialId: '',
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

async function loadReferences() {
  const [customerPage, projectPage, materialPage] = await Promise.all([
    masterDataApi.customers.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
    salesProjectApi.projects.list({ keyword: '', status: 'ACTIVE', page: 1, pageSize: 200 }),
    masterDataApi.materials.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
  ])
  customers.value = pageItems(customerPage)
  projects.value = pageItems(projectPage)
  materials.value = pageItems(materialPage)
}

async function loadRecord() {
  if (!route.params.id) {
    return
  }
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
  <section class="sales-quote-form">
    <header class="page-header">
      <div>
        <h1>{{ pageTitle }}</h1>
        <p>客户、项目、税价和报价行均按冻结 DTO 提交，金额由后端形成可信结果。</p>
      </div>
    </header>
    <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
    <el-alert v-if="loading" class="page-alert" type="info" title="销售报价表单加载中" :closable="false" />

    <div class="form-grid">
      <label>
        客户
        <select v-model="form.customerId" data-test="quote-customer-select">
          <option value="">请选择客户</option>
          <option v-for="customer in customers" :key="customer.id" :value="String(customer.id)">
            {{ customer.code }} {{ customer.name }}
          </option>
        </select>
      </label>
      <label>
        项目
        <select v-model="form.projectId" data-test="quote-project-select">
          <option value="">普通报价可留空</option>
          <option v-for="project in projects" :key="project.id" :value="String(project.id)">
            {{ project.projectNo }} {{ project.name }}
          </option>
        </select>
      </label>
      <label>报价日期<input v-model="form.quoteDate" name="quote-date" /></label>
      <label>有效期<input v-model="form.validUntil" name="quote-valid-until" /></label>
      <label>默认税率<input v-model="form.defaultTaxRate" name="quote-default-tax-rate" /></label>
      <label>账期<input v-model="form.paymentTermDays" name="quote-payment-days" /></label>
    </div>

    <section class="line-editor">
      <h2>报价明细</h2>
      <div class="form-grid">
        <label>
          物料
          <select v-model="line.materialId" data-test="quote-line-material-select">
            <option value="">请选择物料</option>
            <option v-for="material in materials" :key="material.id" :value="String(material.id)">
              {{ material.code }} {{ material.name }} / {{ material.unitName }}
            </option>
          </select>
        </label>
        <label>数量<input v-model="line.quantity" name="quote-line-quantity" /></label>
        <label>未税单价<input v-model="line.untaxedUnitPrice" name="quote-line-untaxed-price" /></label>
        <label>含税单价<input v-model="line.taxIncludedUnitPrice" name="quote-line-tax-included-price" /></label>
        <label>税率<input v-model="line.taxRate" name="quote-line-tax-rate" /></label>
        <label>承诺日期<input v-model="line.promisedDate" name="quote-line-promised-date" /></label>
      </div>
      <p>当前行含税单价 {{ formatSalesDecimal(line.taxIncludedUnitPrice) }} CNY，税率 {{ formatSalesDecimal(line.taxRate) }}</p>
    </section>

    <div class="form-footer">
      <el-button @click="router.push({ name: 'sales-quotes' })">取消</el-button>
      <el-button data-test="save-sales-quote" type="primary" :loading="submitting" @click="saveQuote">保存</el-button>
    </div>
  </section>
</template>

<style scoped>
.sales-quote-form {
  display: grid;
  gap: 14px;
}

.page-header h1 {
  font-size: 22px;
  margin: 0 0 6px;
}

.page-header p {
  color: #606266;
  margin: 0;
}

.form-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

label {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

input,
select {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  min-height: 32px;
  padding: 0 10px;
}

.line-editor {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  padding: 12px;
}

.line-editor h2 {
  font-size: 16px;
  margin: 0 0 12px;
}

.form-footer {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
</style>
