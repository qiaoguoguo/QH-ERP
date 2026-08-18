<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { PartnerRecord } from '../../shared/api/masterDataApi'
import {
  procurementApi,
  type ProcurementInquiryLineRecord,
  type ResourceId,
  type SupplierQuotePayload,
  type SupplierQuoteRecord,
} from '../../shared/api/procurementApi'
import { procurementErrorMessage } from './procurementPageHelpers'

const props = defineProps<{
  visible: boolean
  inquiryId: ResourceId
  lines: ProcurementInquiryLineRecord[]
  suppliers: PartnerRecord[]
  quote?: SupplierQuoteRecord | null
}>()

const emit = defineEmits<{
  'update:visible': [visible: boolean]
  saved: []
}>()

const submitting = ref(false)
const formError = ref('')
const form = reactive({
  supplierId: '' as ResourceId | '',
  materialId: '' as ResourceId | '',
  quantity: '',
  minPurchaseQuantity: '',
  taxRate: '0.13',
  taxExcludedUnitPrice: '',
  taxIncludedUnitPrice: '',
  validFrom: '',
  validTo: '',
  deliveryDate: '',
  remark: '',
})

let priceInputBasis: 'TAX_EXCLUDED' | 'TAX_INCLUDED' = 'TAX_EXCLUDED'

function decimalNumber(value: unknown) {
  if (String(value ?? '').trim() === '') return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function formatUnitPrice(value: number) {
  return value.toFixed(6).replace(/\.?0+$/, '')
}

function currentTaxRate() {
  const taxRate = decimalNumber(form.taxRate)
  return taxRate !== null && taxRate >= 0 && taxRate <= 1 ? taxRate : null
}

function syncTaxIncludedUnitPrice(value: unknown) {
  priceInputBasis = 'TAX_EXCLUDED'
  const unitPrice = decimalNumber(value)
  if (unitPrice === null) {
    form.taxIncludedUnitPrice = ''
    return
  }
  const taxRate = currentTaxRate()
  if (taxRate === null) return
  form.taxIncludedUnitPrice = formatUnitPrice(unitPrice * (1 + taxRate))
}

function syncTaxExcludedUnitPrice(value: unknown) {
  priceInputBasis = 'TAX_INCLUDED'
  const unitPrice = decimalNumber(value)
  if (unitPrice === null) {
    form.taxExcludedUnitPrice = ''
    return
  }
  const taxRate = currentTaxRate()
  if (taxRate === null) return
  form.taxExcludedUnitPrice = formatUnitPrice(unitPrice / (1 + taxRate))
}

function syncPriceByTaxRate() {
  if (priceInputBasis === 'TAX_INCLUDED') {
    syncTaxExcludedUnitPrice(form.taxIncludedUnitPrice)
    return
  }
  syncTaxIncludedUnitPrice(form.taxExcludedUnitPrice)
}

const dialogTitle = computed(() => props.quote ? '编辑供应商报价' : '新增供应商报价')

function resetForm() {
  priceInputBasis = 'TAX_EXCLUDED'
  const quote = props.quote
  const firstLine = props.lines[0]
  form.supplierId = quote?.supplierId ?? ''
  form.materialId = quote?.materialId ?? firstLine?.materialId ?? ''
  form.quantity = quote?.quantity ?? firstLine?.quantity ?? ''
  form.minPurchaseQuantity = quote?.minPurchaseQuantity ?? ''
  form.taxRate = quote?.taxRate ?? '0.13'
  form.taxExcludedUnitPrice = quote?.taxExcludedUnitPrice ?? ''
  form.taxIncludedUnitPrice = quote?.taxIncludedUnitPrice ?? ''
  form.validFrom = quote?.validFrom ?? ''
  form.validTo = quote?.validTo ?? ''
  form.deliveryDate = quote?.deliveryDate ?? ''
  form.remark = ''
  formError.value = ''
}

function closeDialog() {
  emit('update:visible', false)
}

function decimalValue(value: unknown, label: string, options: { allowZero?: boolean; maxOne?: boolean } = {}): string | null {
  const normalized = String(value ?? '').trim()
  if (!/^\d{1,18}(\.\d{1,6})?$/.test(normalized)) {
    formError.value = `${label}必须是最多 6 位小数的数字`
    return null
  }
  const numeric = Number(normalized)
  if (!Number.isFinite(numeric) || numeric < 0 || (!options.allowZero && numeric === 0)) {
    formError.value = `${label}${options.allowZero ? '不能小于 0' : '必须大于 0'}`
    return null
  }
  if (options.maxOne && numeric > 1) {
    formError.value = `${label}必须在 0 至 1 之间，例如 13% 填写 0.13`
    return null
  }
  return normalized
}

function multiplyDecimal(left: string, right: string): string {
  return (Number(left) * Number(right)).toFixed(6)
}

function buildPayload(): SupplierQuotePayload | null {
  if (form.supplierId === '' || form.materialId === '') {
    formError.value = '请选择供应商和报价物料'
    return null
  }
  const quantity = decimalValue(form.quantity, '报价数量')
  const taxRate = decimalValue(form.taxRate, '税率', { allowZero: true, maxOne: true })
  const taxExcludedUnitPrice = decimalValue(form.taxExcludedUnitPrice, '未税单价', { allowZero: true })
  const taxIncludedUnitPrice = decimalValue(form.taxIncludedUnitPrice, '含税单价', { allowZero: true })
  const hasMinPurchaseQuantity = Boolean(form.minPurchaseQuantity.trim())
  const minPurchaseQuantity = hasMinPurchaseQuantity
    ? decimalValue(form.minPurchaseQuantity, '最小采购量')
    : null
  if (!quantity || taxRate === null || taxExcludedUnitPrice === null || taxIncludedUnitPrice === null) {
    return null
  }
  if (hasMinPurchaseQuantity && minPurchaseQuantity === null) {
    return null
  }
  if (form.validFrom && form.validTo && form.validFrom > form.validTo) {
    formError.value = '报价有效期开始日期不能晚于结束日期'
    return null
  }
  return {
    supplierId: form.supplierId,
    materialId: form.materialId,
    quantity,
    taxRate,
    taxExcludedUnitPrice,
    taxIncludedUnitPrice,
    taxExcludedAmount: multiplyDecimal(quantity, taxExcludedUnitPrice),
    taxIncludedAmount: multiplyDecimal(quantity, taxIncludedUnitPrice),
    currency: 'CNY',
    ...(minPurchaseQuantity ? { minPurchaseQuantity } : {}),
    ...(form.deliveryDate ? { deliveryDate: form.deliveryDate } : {}),
    ...(form.validFrom ? { validFrom: form.validFrom } : {}),
    ...(form.validTo ? { validTo: form.validTo } : {}),
    ...(form.remark.trim() ? { remark: form.remark.trim() } : {}),
  }
}

async function submitQuote() {
  if (submitting.value) {
    return
  }
  formError.value = ''
  const payload = buildPayload()
  if (!payload) {
    return
  }
  submitting.value = true
  try {
    if (props.quote) {
      await procurementApi.quotes.update(props.inquiryId, props.quote.id, {
        ...payload,
        version: props.quote.version,
      })
    } else {
      await procurementApi.quotes.create(props.inquiryId, payload)
    }
    emit('saved')
    closeDialog()
  } catch (caught) {
    formError.value = procurementErrorMessage(caught)
  } finally {
    submitting.value = false
  }
}

watch(() => props.visible, (visible) => {
  if (visible) {
    resetForm()
  }
})
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="dialogTitle"
    width="min(900px, 92vw)"
    :close-on-click-modal="false"
    :close-on-press-escape="!submitting"
    :teleported="false"
    @close="closeDialog"
  >
    <el-alert v-if="formError" class="form-alert" type="error" :title="formError" show-icon :closable="false" />
    <el-form class="quote-form-grid" label-position="top">
      <el-form-item label="供应商" required>
        <el-select v-model="form.supplierId" data-test="quote-supplier" filterable placeholder="选择供应商">
          <el-option
            v-for="supplier in suppliers"
            :key="supplier.id"
            :label="`${supplier.code} ${supplier.name}`"
            :value="supplier.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="报价物料" required>
        <el-select v-model="form.materialId" data-test="quote-material" placeholder="选择询价物料">
          <el-option
            v-for="line in lines"
            :key="line.id"
            :label="`${line.materialCode} ${line.materialName}`"
            :value="line.materialId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="报价数量" required>
        <el-input v-model="form.quantity" data-test="quote-quantity" placeholder="例如 100" />
      </el-form-item>
      <el-form-item label="最小采购量">
        <el-input v-model="form.minPurchaseQuantity" data-test="quote-min-quantity" placeholder="可选" />
      </el-form-item>
      <el-form-item label="税率" required>
        <el-input v-model="form.taxRate" data-test="quote-tax-rate" placeholder="13% 填写 0.13" @input="syncPriceByTaxRate" />
      </el-form-item>
      <el-form-item label="未税单价" required>
        <el-input v-model="form.taxExcludedUnitPrice" data-test="quote-tax-excluded-price" placeholder="输入后自动计算含税价" @input="syncTaxIncludedUnitPrice" />
      </el-form-item>
      <el-form-item label="含税单价" required>
        <el-input v-model="form.taxIncludedUnitPrice" data-test="quote-tax-included-price" placeholder="输入后自动反算未税价" @input="syncTaxExcludedUnitPrice" />
      </el-form-item>
      <el-form-item label="币种">
        <el-input model-value="CNY" disabled />
      </el-form-item>
      <el-form-item label="有效期开始">
        <el-date-picker v-model="form.validFrom" value-format="YYYY-MM-DD" format="YYYY-MM-DD" placeholder="选择日期" />
      </el-form-item>
      <el-form-item label="有效期结束">
        <el-date-picker v-model="form.validTo" value-format="YYYY-MM-DD" format="YYYY-MM-DD" placeholder="选择日期" />
      </el-form-item>
      <el-form-item label="承诺交期">
        <el-date-picker v-model="form.deliveryDate" value-format="YYYY-MM-DD" format="YYYY-MM-DD" placeholder="选择日期" />
      </el-form-item>
      <el-form-item class="quote-remark" label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="submitting" @click="closeDialog">取消</el-button>
      <el-button data-test="save-supplier-quote" type="primary" :loading="submitting" @click="submitQuote">
        保存报价
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.quote-form-grid {
  display: grid;
  gap: 0 16px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.quote-form-grid :deep(.el-select),
.quote-form-grid :deep(.el-date-editor) {
  width: 100%;
}

.quote-remark {
  grid-column: 1 / -1;
}

@media (max-width: 760px) {
  .quote-form-grid {
    grid-template-columns: 1fr;
  }

  .quote-remark {
    grid-column: auto;
  }
}
</style>
