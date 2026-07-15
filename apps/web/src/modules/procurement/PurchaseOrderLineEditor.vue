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
  readOnly?: boolean
  errors?: Record<number, string>
}>(), {
  procurementMode: null,
  projectCode: null,
  projectName: null,
  requisitionLineOptions: () => [],
  quoteLineOptions: () => [],
  priceAgreementLineOptions: () => [],
  readOnly: false,
  errors: () => ({}),
})

const emit = defineEmits<{
  'update:lines': [lines: PurchaseOrderLineDraft[]]
  'source-selected': [option: PurchaseOrderSourceOption]
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
    currency: option.currency ?? 'CNY',
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
    ...(option ? patchFromSource(option) : {}),
  })
  if (option) {
    emit('source-selected', option)
  }
}

function selectQuoteLine(index: number, value: ResourceId | '') {
  const option = props.quoteLineOptions.find((item) => String(item.id) === String(value))
  updateLine(index, {
    quoteLineId: value === '' ? null : value,
    quoteSourceLabel: option?.label ?? '',
    ...(option ? patchFromSource(option) : {}),
  })
  if (option) {
    emit('source-selected', option)
  }
}

function selectPriceAgreementLine(index: number, value: ResourceId | '') {
  const option = props.priceAgreementLineOptions.find((item) => String(item.id) === String(value))
  updateLine(index, {
    priceAgreementLineId: value === '' ? null : value,
    priceAgreementSourceLabel: option?.label ?? '',
    ...(option ? patchFromSource(option) : {}),
  })
  if (option) {
    emit('source-selected', option)
  }
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
        <el-table-column label="来源" min-width="360">
          <template #default="{ row, $index }">
            <div class="source-cell">
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
                  v-for="option in requisitionLineOptions"
                  :key="option.id"
                  :label="option.label"
                  :value="option.id"
                />
              </el-select>
              <span>请购来源：{{ row.requisitionSourceLabel || '未选择' }}</span>
              <el-select
                :model-value="valueOrEmpty(row.quoteLineId)"
                :data-test="`purchase-order-line-quote-line-id-${$index}`"
                clearable
                filterable
                placeholder="选择报价行"
                style="width: 100%"
                :disabled="readOnly"
                @update:model-value="selectQuoteLine($index, $event)"
              >
                <el-option
                  v-for="option in quoteLineOptions"
                  :key="option.id"
                  :label="option.label"
                  :value="option.id"
                />
              </el-select>
              <span>报价来源：{{ row.quoteSourceLabel || quoteLineOptions[0]?.label || '未选择' }}</span>
              <el-select
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
                  v-for="option in priceAgreementLineOptions"
                  :key="option.id"
                  :label="option.label"
                  :value="option.id"
                />
              </el-select>
              <span>协议来源：{{ row.priceAgreementSourceLabel || priceAgreementLineOptions[0]?.label || '未选择' }}</span>
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
        <el-table-column label="税价" min-width="300" align="right">
          <template #default="{ row, $index }">
            <div class="tax-cell">
              <el-input
                :model-value="row.taxExcludedUnitPrice"
                :name="`purchase-order-line-tax-excluded-unit-price-${$index}`"
                inputmode="decimal"
                placeholder="未税单价"
                :disabled="readOnly"
                @update:model-value="updateDecimalText($index, 'taxExcludedUnitPrice', $event)"
              />
              <el-input
                :model-value="row.taxIncludedUnitPrice"
                :name="`purchase-order-line-tax-included-unit-price-${$index}`"
                inputmode="decimal"
                placeholder="含税单价"
                :disabled="readOnly"
                @update:model-value="updateDecimalText($index, 'taxIncludedUnitPrice', $event)"
              />
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
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ $index }">
            <el-button
              data-test="remove-purchase-order-line"
              text
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
    <div v-for="line in lines" :key="line.lineNo" class="field-error">
      {{ errors[line.lineNo] }}
    </div>
    <el-button data-test="add-purchase-order-line" :disabled="readOnly" @click="addLine">
      新增明细
    </el-button>
  </div>
</template>

<style scoped>
.purchase-order-line-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.line-option-meta {
  color: var(--qherp-muted);
  float: right;
  font-size: 12px;
  margin-left: 12px;
}

.source-cell,
.tax-cell {
  display: grid;
  gap: 6px;
}

.source-cell span,
.tax-cell span {
  color: var(--qherp-muted);
  font-size: 12px;
  text-align: left;
}
</style>
