<script setup lang="ts">
import type { ProcurementMode, ResourceId } from '../../shared/api/procurementApi'
import type { MaterialRecord } from '../../shared/api/masterDataApi'
import {
  type PurchaseOrderLineDraft,
  type PurchaseOrderSourceOption,
  newPurchaseOrderLine,
  nextPurchaseOrderLineNo,
  procurementModeDisplay,
} from './procurementPageHelpers'

const props = withDefaults(defineProps<{
  lines: PurchaseOrderLineDraft[]
  materials: MaterialRecord[]
  procurementMode?: ProcurementMode | null
  projectCode?: string | null
  projectName?: string | null
  requisitionLineOptions?: PurchaseOrderSourceOption[]
  quoteLineOptions?: PurchaseOrderSourceOption[]
  priceAgreementLineOptions?: PurchaseOrderSourceOption[]
  supplierId?: ResourceId | ''
  readOnly?: boolean
  errors?: Record<number, string>
}>(), {
  procurementMode: null,
  projectCode: null,
  projectName: null,
  requisitionLineOptions: () => [],
  quoteLineOptions: () => [],
  priceAgreementLineOptions: () => [],
  supplierId: '',
  readOnly: false,
  errors: () => ({}),
})

const emit = defineEmits<{
  'update:lines': [lines: PurchaseOrderLineDraft[]]
  'source-selected': [selection: { kind: 'REQUISITION' | 'QUOTE' | 'AGREEMENT', option: PurchaseOrderSourceOption }]
}>()

function valueOrEmpty(value: ResourceId | '' | null | undefined) {
  return value === null || value === undefined ? '' : value
}

function updateLine(index: number, patch: Partial<PurchaseOrderLineDraft>) {
  emit('update:lines', props.lines.map((line, currentIndex) => (
    currentIndex === index ? { ...line, ...patch } : line
  )))
}

function updateMaterial(index: number, value: ResourceId) {
  const material = props.materials.find((item) => String(item.id) === String(value))
  updateLine(index, {
    materialId: value,
    unitId: material?.unitId ?? '',
    unitName: material?.unitName ?? '',
  })
}

function updateText(index: number, key: 'quantity' | 'unitPrice' | 'expectedArrivalDate' | 'remark', value: string | number) {
  updateLine(index, { [key]: String(value) })
}

function updateDecimalText(
  index: number,
  key: 'taxRate' | 'taxExcludedUnitPrice' | 'taxIncludedUnitPrice',
  value: string | number,
) {
  updateLine(index, { [key]: String(value) })
}

function patchFromSource(option: PurchaseOrderSourceOption) {
  const patch: Partial<PurchaseOrderLineDraft> = {
    procurementMode: option.procurementMode ?? null,
    projectId: option.projectId ?? null,
    projectCode: option.projectCode ?? null,
    projectName: option.projectName ?? null,
    taxRate: option.taxRate ?? '',
    taxExcludedUnitPrice: option.taxExcludedUnitPrice ?? '',
    taxIncludedUnitPrice: option.taxIncludedUnitPrice ?? '',
    unitPrice: option.taxExcludedUnitPrice ?? '',
    currency: option.currency ?? 'CNY',
    expectedArrivalDate: option.requiredDate ?? '',
  }
  if (option.materialId !== undefined && option.materialId !== null) {
    patch.materialId = option.materialId
  }
  if (option.unitId !== undefined && option.unitId !== null) {
    patch.unitId = option.unitId
  }
  if (option.unitName !== undefined && option.unitName !== null) {
    patch.unitName = option.unitName
  }
  if (option.quantity !== undefined && option.quantity !== null) {
    patch.quantity = option.quantity
  }
  return patch
}

function selectRequisitionLine(index: number, value: ResourceId | '') {
  const option = props.requisitionLineOptions.find((item) => String(item.id) === String(value))
  updateLine(index, {
    requisitionLineId: value === '' ? null : value,
    requisitionSourceLabel: option?.label ?? '',
    quoteLineId: null,
    quoteSourceLabel: '',
    priceAgreementLineId: null,
    priceAgreementSourceLabel: '',
    priceSourceType: '',
    sourceSupplierId: null,
    sourceSupplierName: '',
    ...(option ? patchFromSource(option) : {}),
  })
  if (option) {
    emit('source-selected', { kind: 'REQUISITION', option })
  }
}

function selectQuoteLine(index: number, value: ResourceId | '') {
  const option = props.quoteLineOptions.find((item) => String(item.id) === String(value))
  updateLine(index, {
    quoteLineId: value === '' ? null : value,
    quoteSourceLabel: option?.label ?? '',
    priceAgreementLineId: null,
    priceAgreementSourceLabel: '',
    priceSourceType: 'QUOTE',
    sourceSupplierId: option?.supplierId ?? null,
    sourceSupplierName: option?.supplierName ?? '',
    ...(option ? patchFromSource(option) : {}),
  })
  if (option) {
    emit('source-selected', { kind: 'QUOTE', option })
  }
}

function selectPriceAgreementLine(index: number, value: ResourceId | '') {
  const option = props.priceAgreementLineOptions.find((item) => String(item.id) === String(value))
  updateLine(index, {
    priceAgreementLineId: value === '' ? null : value,
    priceAgreementSourceLabel: option?.label ?? '',
    quoteLineId: null,
    quoteSourceLabel: '',
    priceSourceType: 'AGREEMENT',
    sourceSupplierId: option?.supplierId ?? null,
    sourceSupplierName: option?.supplierName ?? '',
    ...(option ? patchFromSource(option) : {}),
  })
  if (option) {
    emit('source-selected', { kind: 'AGREEMENT', option })
  }
}

function priceSourceType(line: PurchaseOrderLineDraft): '' | 'QUOTE' | 'AGREEMENT' {
  if (line.priceSourceType) {
    return line.priceSourceType
  }
  if (line.quoteLineId !== null && line.quoteLineId !== undefined && line.quoteLineId !== '') {
    return 'QUOTE'
  }
  if (line.priceAgreementLineId !== null && line.priceAgreementLineId !== undefined && line.priceAgreementLineId !== '') {
    return 'AGREEMENT'
  }
  return ''
}

function selectPriceSourceType(index: number, value: '' | 'QUOTE' | 'AGREEMENT') {
  const currentLine = props.lines[index]
  if (currentLine && priceSourceType(currentLine) === value) {
    return
  }
  const clearPrice = {
    taxRate: '',
    taxExcludedUnitPrice: '',
    taxIncludedUnitPrice: '',
  }
  if (value === 'QUOTE') {
    updateLine(index, {
      quoteLineId: null,
      quoteSourceLabel: '',
      priceAgreementLineId: null,
      priceAgreementSourceLabel: '',
      priceSourceType: 'QUOTE',
      sourceSupplierId: null,
      sourceSupplierName: '',
      ...clearPrice,
    })
    return
  }
  if (value === 'AGREEMENT') {
    updateLine(index, {
      quoteLineId: null,
      quoteSourceLabel: '',
      priceAgreementLineId: null,
      priceAgreementSourceLabel: '',
      priceSourceType: 'AGREEMENT',
      sourceSupplierId: null,
      sourceSupplierName: '',
      ...clearPrice,
    })
    return
  }
  updateLine(index, {
    quoteLineId: null,
    quoteSourceLabel: '',
    priceAgreementLineId: null,
    priceAgreementSourceLabel: '',
    priceSourceType: '',
    sourceSupplierId: null,
    sourceSupplierName: '',
    ...clearPrice,
  })
}

function sameId(left: ResourceId | '' | null | undefined, right: ResourceId | '' | null | undefined): boolean {
  return String(left ?? '') === String(right ?? '')
}

function compatibleOwnership(option: PurchaseOrderSourceOption, line: PurchaseOrderLineDraft): boolean {
  const mode = line.procurementMode ?? props.procurementMode
  if (mode && option.procurementMode && mode !== option.procurementMode) {
    return false
  }
  if (mode === 'PROJECT' && !sameId(line.projectId, option.projectId)) {
    return false
  }
  return true
}

function requisitionOptionsForLine(line: PurchaseOrderLineDraft, index: number): PurchaseOrderSourceOption[] {
  const usedIds = new Set(props.lines
    .filter((_, currentIndex) => currentIndex !== index)
    .map((item) => String(item.requisitionLineId ?? '')))
  return props.requisitionLineOptions.filter((option) => (
    !usedIds.has(String(option.id)) && compatibleOwnership(option, line)
  ))
}

function priceOptionsForLine(options: PurchaseOrderSourceOption[], line: PurchaseOrderLineDraft): PurchaseOrderSourceOption[] {
  return options.filter((option) => {
    if (line.materialId && option.materialId && !sameId(line.materialId, option.materialId)) {
      return false
    }
    if (!compatibleOwnership(option, line)) {
      return false
    }
    return !props.supplierId || !option.supplierId || sameId(props.supplierId, option.supplierId)
  })
}

function lineModeText(line: PurchaseOrderLineDraft) {
  return procurementModeDisplay(
    line.procurementMode ?? props.procurementMode,
    line.projectCode ?? props.projectCode,
    line.projectName ?? props.projectName,
  )
}

function addLine() {
  emit('update:lines', [...props.lines, newPurchaseOrderLine(nextPurchaseOrderLineNo(props.lines))])
}

function removeLine(index: number) {
  emit('update:lines', props.lines.filter((_, currentIndex) => currentIndex !== index))
}
</script>

<template>
  <div class="purchase-order-line-editor">
    <div class="table-scroll">
      <el-table :data="lines" empty-text="暂无采购明细" stripe>
        <el-table-column label="行号" width="78">
          <template #default="{ row }">
            {{ row.lineNo }}
          </template>
        </el-table-column>
        <el-table-column label="物料" min-width="230">
          <template #default="{ row, $index }">
            <el-select
              :model-value="valueOrEmpty(row.materialId)"
              :data-test="`purchase-order-line-material-id-${$index}`"
              filterable
              placeholder="选择可采购物料"
              style="width: 100%"
              :disabled="readOnly"
              @update:model-value="updateMaterial($index, $event)"
            >
              <el-option
                v-for="material in materials"
                :key="material.id"
                :label="`${material.code} ${material.name}`"
                :value="material.id"
              >
                <span>{{ material.code }} {{ material.name }}</span>
                <span class="line-option-meta">{{ material.specification || material.unitName || '-' }}</span>
              </el-option>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="模式/项目" min-width="210" show-overflow-tooltip>
          <template #default="{ row }">
            {{ lineModeText(row) }}
          </template>
        </el-table-column>
        <el-table-column label="请购来源" min-width="300">
          <template #default="{ row, $index }">
            <div class="source-cell requisition-source-cell">
              <el-select
                :model-value="valueOrEmpty(row.requisitionLineId)"
                :data-test="`purchase-order-line-requisition-line-id-${$index}`"
                clearable
                filterable
                placeholder="选择请购行"
                style="width: 100%"
                :disabled="readOnly"
                @update:model-value="selectRequisitionLine($index, $event)"
              >
                <el-option
                  v-for="option in requisitionOptionsForLine(row, $index)"
                  :key="option.id"
                  :label="option.label"
                  :value="option.id"
                />
              </el-select>
              <span class="source-hint">用于确定需求、数量和项目归属</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="价格来源" min-width="330">
          <template #default="{ row, $index }">
            <div class="source-cell price-source-cell">
              <el-select
                :model-value="priceSourceType(row)"
                :data-test="`purchase-order-line-price-source-type-${$index}`"
                clearable
                placeholder="选择价格来源类型"
                style="width: 100%"
                :disabled="readOnly"
                @update:model-value="selectPriceSourceType($index, $event)"
              >
                <el-option label="中选报价" value="QUOTE" :disabled="priceOptionsForLine(quoteLineOptions, row).length === 0" />
                <el-option label="价格协议" value="AGREEMENT" :disabled="priceOptionsForLine(priceAgreementLineOptions, row).length === 0" />
              </el-select>
              <el-select
                v-if="priceSourceType(row) === 'QUOTE'"
                :model-value="valueOrEmpty(row.quoteLineId)"
                :data-test="`purchase-order-line-quote-line-id-${$index}`"
                clearable
                filterable
                placeholder="选择中选报价"
                style="width: 100%"
                :disabled="readOnly"
                @update:model-value="selectQuoteLine($index, $event)"
              >
                <el-option
                  v-for="option in priceOptionsForLine(quoteLineOptions, row)"
                  :key="option.id"
                  :label="option.label"
                  :value="option.id"
                />
              </el-select>
              <el-select
                v-else-if="priceSourceType(row) === 'AGREEMENT'"
                :model-value="valueOrEmpty(row.priceAgreementLineId)"
                :data-test="`purchase-order-line-price-agreement-line-id-${$index}`"
                clearable
                filterable
                placeholder="选择协议行"
                style="width: 100%"
                :disabled="readOnly"
                @update:model-value="selectPriceAgreementLine($index, $event)"
              >
                <el-option
                  v-for="option in priceOptionsForLine(priceAgreementLineOptions, row)"
                  :key="option.id"
                  :label="option.label"
                  :value="option.id"
                />
              </el-select>
              <span v-else class="source-placeholder">请先选择中选报价或价格协议</span>
              <span v-if="row.sourceSupplierName" class="source-hint">供应商：{{ row.sourceSupplierName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="单位" width="100">
          <template #default="{ row }">
            {{ row.unitName || '基本单位' }}
          </template>
        </el-table-column>
        <el-table-column label="数量" width="140" align="right">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.quantity"
              :name="`purchase-order-line-quantity-${$index}`"
              inputmode="decimal"
              placeholder="> 0"
              :disabled="readOnly"
              @update:model-value="updateText($index, 'quantity', $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="采购单价" width="140" align="right">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.unitPrice"
              :name="`purchase-order-line-unit-price-${$index}`"
              inputmode="decimal"
              placeholder=">= 0"
              :disabled="readOnly"
              @update:model-value="updateText($index, 'unitPrice', $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="未税单价" width="150" align="right">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.taxExcludedUnitPrice"
              :name="`purchase-order-line-tax-excluded-unit-price-${$index}`"
              inputmode="decimal"
              placeholder="未税单价"
              :disabled="readOnly"
              @update:model-value="updateDecimalText($index, 'taxExcludedUnitPrice', $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="含税单价" width="150" align="right">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.taxIncludedUnitPrice"
              :name="`purchase-order-line-tax-included-unit-price-${$index}`"
              inputmode="decimal"
              placeholder="含税单价"
              :disabled="readOnly"
              @update:model-value="updateDecimalText($index, 'taxIncludedUnitPrice', $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="税率/币种" width="170" align="right">
          <template #default="{ row, $index }">
            <div class="tax-rate-cell">
              <el-input
                :model-value="row.taxRate"
                :name="`purchase-order-line-tax-rate-${$index}`"
                inputmode="decimal"
                placeholder="税率"
                :disabled="readOnly"
                @update:model-value="updateDecimalText($index, 'taxRate', $event)"
              />
              <span>{{ row.currency || 'CNY' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="预计到货" width="140">
          <template #default="{ row, $index }">
            <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD"
              :model-value="row.expectedArrivalDate"
              :name="`purchase-order-line-expected-date-${$index}`"
              placeholder="选择日期"
              :disabled="readOnly"
              @update:model-value="updateText($index, 'expectedArrivalDate', $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="150">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.remark"
              :name="`purchase-order-line-remark-${$index}`"
              placeholder="可选"
              :disabled="readOnly"
              @update:model-value="updateText($index, 'remark', $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ $index }">
            <el-button
              data-test="remove-purchase-order-line"
              size="small"
              plain
              type="danger"
              :disabled="readOnly || lines.length <= 1"
              @click="removeLine($index)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div v-for="line in lines" :key="line.lineNo">
      <div v-if="errors[line.lineNo]" class="field-error">{{ errors[line.lineNo] }}</div>
    </div>
    <div class="line-toolbar">
      <span>共 {{ lines.length }} 条采购明细</span>
      <el-button data-test="add-purchase-order-line" plain :disabled="readOnly" @click="addLine">
        新增直接采购明细
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.purchase-order-line-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  width: 100%;
}

.table-scroll {
  border: 1px solid var(--qherp-border);
  border-radius: 6px;
  overflow-x: auto;
}

.line-option-meta {
  color: var(--qherp-muted);
  float: right;
  font-size: 12px;
  margin-left: 12px;
}

.source-cell {
  display: grid;
  gap: 8px;
}

.source-hint,
.source-placeholder,
.tax-rate-cell span {
  color: var(--qherp-muted);
  font-size: 12px;
  text-align: left;
}

.tax-rate-cell {
  align-items: center;
  display: grid;
  gap: 8px;
  grid-template-columns: minmax(90px, 1fr) auto;
}

.source-placeholder {
  align-items: center;
  background: var(--qherp-surface-soft, #f5f7fa);
  border: 1px dashed var(--qherp-border);
  border-radius: 4px;
  box-sizing: border-box;
  display: flex;
  min-height: 32px;
  padding: 6px 10px;
}

.line-toolbar {
  align-items: center;
  display: flex;
  justify-content: space-between;
}

.line-toolbar span {
  color: var(--qherp-muted);
  font-size: 13px;
}

:deep(.el-table__cell) {
  vertical-align: top;
}

:deep(.el-date-editor.el-input) {
  width: 100%;
}
</style>
