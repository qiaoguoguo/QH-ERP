<script setup lang="ts">
import type { ResourceId } from '../../shared/api/procurementApi'
import type { MaterialRecord } from '../../shared/api/masterDataApi'
import { type PurchaseOrderLineDraft, newPurchaseOrderLine, nextPurchaseOrderLineNo } from './procurementPageHelpers'

const props = withDefaults(defineProps<{
  lines: PurchaseOrderLineDraft[]
  materials: MaterialRecord[]
  readOnly?: boolean
  errors?: Record<number, string>
}>(), {
  readOnly: false,
  errors: () => ({}),
})

const emit = defineEmits<{
  'update:lines': [lines: PurchaseOrderLineDraft[]]
}>()

function valueOrEmpty(value: ResourceId | '') {
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
        <el-table-column label="预计到货" width="140">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.expectedArrivalDate"
              :name="`purchase-order-line-expected-date-${$index}`"
              placeholder="YYYY-MM-DD"
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
</style>
