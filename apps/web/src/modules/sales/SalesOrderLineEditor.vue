<script setup lang="ts">
import type { ResourceId } from '../../shared/api/salesApi'
import type { MaterialRecord, WarehouseRecord } from '../../shared/api/masterDataApi'
import { type SalesOrderLineDraft, newSalesOrderLine, nextSalesOrderLineNo, salesPriceSourceLabel } from './salesPageHelpers'

const props = withDefaults(defineProps<{
  lines: SalesOrderLineDraft[]
  materials: MaterialRecord[]
  warehouses: WarehouseRecord[]
  readOnly?: boolean
  errors?: Record<number, string>
}>(), {
  readOnly: false,
  errors: () => ({}),
})

const emit = defineEmits<{
  'update:lines': [lines: SalesOrderLineDraft[]]
}>()

function valueOrEmpty(value: ResourceId | '') {
  return value === null || value === undefined ? '' : value
}

function updateLine(index: number, patch: Partial<SalesOrderLineDraft>) {
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

function updateWarehouse(index: number, value: ResourceId) {
  const warehouse = props.warehouses.find((item) => String(item.id) === String(value))
  updateLine(index, {
    reservationWarehouseId: value,
    reservationWarehouseName: warehouse?.name ?? '',
  })
}

function updateText(
  index: number,
  key: 'quantity' | 'unitPrice' | 'untaxedUnitPrice' | 'taxIncludedUnitPrice' | 'taxRate' | 'expectedShipDate' | 'remark',
  value: string | number,
) {
  updateLine(index, { [key]: String(value) })
}

function priceSourceLabel(line: SalesOrderLineDraft) {
  return salesPriceSourceLabel(line)
}

function addLine() {
  emit('update:lines', [...props.lines, newSalesOrderLine(nextSalesOrderLineNo(props.lines))])
}

function removeLine(index: number) {
  emit('update:lines', props.lines.filter((_, currentIndex) => currentIndex !== index))
}
</script>

<template>
  <div class="sales-order-line-editor">
    <div class="table-scroll">
      <el-table :data="lines" empty-text="暂无销售明细" stripe>
        <el-table-column label="行号" width="78">
          <template #default="{ row }">
            {{ row.lineNo }}
          </template>
        </el-table-column>
        <el-table-column label="物料" min-width="230">
          <template #default="{ row, $index }">
            <el-select
              :model-value="valueOrEmpty(row.materialId)"
              :data-test="`sales-order-line-material-id-${$index}`"
              filterable
              placeholder="选择成品或半成品"
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
        <el-table-column label="预留仓库" min-width="170">
          <template #default="{ row, $index }">
            <el-select
              :model-value="valueOrEmpty(row.reservationWarehouseId)"
              :data-test="`sales-order-line-reservation-warehouse-id-${$index}`"
              filterable
              placeholder="选择预留仓库"
              style="width: 100%"
              :disabled="readOnly"
              @update:model-value="updateWarehouse($index, $event)"
            >
              <el-option
                v-for="warehouse in warehouses"
                :key="warehouse.id"
                :label="`${warehouse.code} ${warehouse.name}`"
                :value="warehouse.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="销售数量" width="140" align="right">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.quantity"
              :name="`sales-order-line-quantity-${$index}`"
              inputmode="decimal"
              placeholder="> 0"
              :disabled="readOnly"
              @update:model-value="updateText($index, 'quantity', $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="销售单价" width="140" align="right">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.unitPrice"
              :name="`sales-order-line-unit-price-${$index}`"
              inputmode="decimal"
              placeholder=">= 0"
              :disabled="readOnly"
              @update:model-value="updateText($index, 'unitPrice', $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="价格来源" min-width="130">
          <template #default="{ row }">
            {{ priceSourceLabel(row) }}
          </template>
        </el-table-column>
        <el-table-column label="未税单价" width="140" align="right">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.untaxedUnitPrice"
              :name="`sales-order-line-untaxed-unit-price-${$index}`"
              inputmode="decimal"
              placeholder=">= 0"
              :disabled="readOnly"
              @update:model-value="updateText($index, 'untaxedUnitPrice', $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="含税单价" width="140" align="right">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.taxIncludedUnitPrice"
              :name="`sales-order-line-tax-included-unit-price-${$index}`"
              inputmode="decimal"
              placeholder=">= 0"
              :disabled="readOnly"
              @update:model-value="updateText($index, 'taxIncludedUnitPrice', $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="税率" width="120" align="right">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.taxRate"
              :name="`sales-order-line-tax-rate-${$index}`"
              inputmode="decimal"
              placeholder="0.13"
              :disabled="readOnly"
              @update:model-value="updateText($index, 'taxRate', $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="预计交付" width="140">
          <template #default="{ row, $index }">
            <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD"
              :model-value="row.expectedShipDate"
              :name="`sales-order-line-expected-date-${$index}`"
              placeholder="选择日期"
              :disabled="readOnly"
              @update:model-value="updateText($index, 'expectedShipDate', $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="150">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.remark"
              :name="`sales-order-line-remark-${$index}`"
              placeholder="可选"
              :disabled="readOnly"
              @update:model-value="updateText($index, 'remark', $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ $index }">
            <el-button
              data-test="remove-sales-order-line"
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
    <el-button data-test="add-sales-order-line" :disabled="readOnly" @click="addLine">
      新增明细
    </el-button>
  </div>
</template>

<style scoped>
.sales-order-line-editor {
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
