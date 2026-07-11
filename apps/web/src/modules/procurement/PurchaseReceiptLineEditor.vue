<script setup lang="ts">
import type { ResourceId } from '../../shared/api/procurementApi'
import {
  formatProcurementQuantity,
  type PurchaseReceiptLineDraft,
  type PurchaseReceiptSourceLine,
  newPurchaseReceiptLine,
  nextPurchaseReceiptLineNo,
} from './procurementPageHelpers'

const props = withDefaults(defineProps<{
  lines: PurchaseReceiptLineDraft[]
  sourceLines: PurchaseReceiptSourceLine[]
  readOnly?: boolean
  errors?: Record<number, string>
}>(), {
  readOnly: false,
  errors: () => ({}),
})

const emit = defineEmits<{
  'update:lines': [lines: PurchaseReceiptLineDraft[]]
}>()

function valueOrEmpty(value: ResourceId | '') {
  return value === null || value === undefined ? '' : value
}

function updateLine(index: number, patch: Partial<PurchaseReceiptLineDraft>) {
  emit('update:lines', props.lines.map((line, currentIndex) => (
    currentIndex === index ? { ...line, ...patch } : line
  )))
}

function updateSourceLine(index: number, value: ResourceId) {
  const sourceLine = props.sourceLines.find((line) => String(line.id) === String(value))
  updateLine(index, {
    orderLineId: value,
    materialId: sourceLine?.materialId ?? '',
    materialCode: sourceLine?.materialCode ?? '',
    materialName: sourceLine?.materialName ?? '',
    unitId: sourceLine?.unitId ?? '',
    unitName: sourceLine?.unitName ?? '',
    orderedQuantity: sourceLine?.orderedQuantity ?? 0,
    receivedQuantityBefore: sourceLine?.receivedQuantityBefore ?? 0,
    remainingQuantityBefore: sourceLine?.remainingQuantityBefore ?? 0,
    inTransitQuantity: sourceLine?.inTransitQuantity ?? null,
    inTransitStatus: sourceLine?.inTransitStatus ?? null,
    inTransitStatusName: sourceLine?.inTransitStatusName ?? null,
  })
}

function updateText(index: number, key: 'quantity' | 'remark', value: string | number) {
  updateLine(index, { [key]: String(value) })
}

function addLine() {
  emit('update:lines', [...props.lines, newPurchaseReceiptLine(nextPurchaseReceiptLineNo(props.lines))])
}

function removeLine(index: number) {
  emit('update:lines', props.lines.filter((_, currentIndex) => currentIndex !== index))
}
</script>

<template>
  <div class="purchase-receipt-line-editor">
    <div class="table-scroll">
      <el-table :data="lines" empty-text="暂无采购入库明细" stripe>
        <el-table-column label="行号" width="78">
          <template #default="{ row }">
            {{ row.lineNo }}
          </template>
        </el-table-column>
        <el-table-column label="来源订单行" min-width="180">
          <template #default="{ row, $index }">
            <el-select
              :model-value="valueOrEmpty(row.orderLineId)"
              :data-test="`purchase-receipt-line-order-line-id-${$index}`"
              filterable
              placeholder="选择来源订单行"
              style="width: 100%"
              :disabled="readOnly"
              @update:model-value="updateSourceLine($index, $event)"
            >
              <el-option
                v-for="sourceLine in sourceLines"
                :key="sourceLine.id"
                :label="`${sourceLine.lineNo} ${sourceLine.materialCode} ${sourceLine.materialName}`"
                :value="sourceLine.id"
              >
                <span>{{ sourceLine.lineNo }} {{ sourceLine.materialCode }} {{ sourceLine.materialName }}</span>
                <span class="line-option-meta">
                  未入库 {{ formatProcurementQuantity(sourceLine.remainingQuantityBefore) }} /
                  采购在途参考 {{ formatProcurementQuantity(sourceLine.inTransitQuantity) }} /
                  {{ sourceLine.inTransitStatusName || '-' }}
                </span>
              </el-option>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="物料" min-width="210" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.materialCode && row.materialName ? `${row.materialCode} ${row.materialName}` : '请选择来源订单行' }}
          </template>
        </el-table-column>
        <el-table-column label="单位" width="90">
          <template #default="{ row }">
            {{ row.unitName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="订单数量" width="110" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatProcurementQuantity(row.orderedQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="已入库" width="110" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatProcurementQuantity(row.receivedQuantityBefore) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="未入库" width="110" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatProcurementQuantity(row.remainingQuantityBefore) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="采购在途参考" width="130" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatProcurementQuantity(row.inTransitQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="在途状态" width="120">
          <template #default="{ row }">
            {{ row.inTransitStatusName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="本次入库数量" width="150" align="right">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.quantity"
              :name="`purchase-receipt-line-quantity-${$index}`"
              inputmode="decimal"
              placeholder="> 0"
              :disabled="readOnly"
              @update:model-value="updateText($index, 'quantity', $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="150">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.remark"
              :name="`purchase-receipt-line-remark-${$index}`"
              placeholder="可选"
              :disabled="readOnly"
              @update:model-value="updateText($index, 'remark', $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ $index }">
            <el-button
              data-test="remove-purchase-receipt-line"
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
    <el-button data-test="add-purchase-receipt-line" :disabled="readOnly" @click="addLine">
      新增采购入库明细
    </el-button>
  </div>
</template>

<style scoped>
.purchase-receipt-line-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.line-option-meta {
  color: var(--qherp-muted);
  font-size: 12px;
  margin-left: 12px;
  white-space: normal;
  word-break: break-word;
}

@media (max-width: 760px) {
  .line-option-meta {
    display: block;
    margin-left: 0;
  }
}

.numeric-cell {
  display: inline-block;
  min-width: 72px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
</style>
