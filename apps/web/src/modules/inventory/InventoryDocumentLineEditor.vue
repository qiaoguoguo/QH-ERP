<script setup lang="ts">
import type { InventoryAdjustmentDirection, InventoryDocumentType, ResourceId } from '../../shared/api/inventoryApi'
import type { MaterialRecord, WarehouseRecord } from '../../shared/api/masterDataApi'
import { materialTypeLabel } from '../master/shared/masterPageHelpers'
import { type InventoryLineDraft, newInventoryLine, nextLineNo } from './inventoryPageHelpers'

const props = withDefaults(defineProps<{
  lines: InventoryLineDraft[]
  documentType: InventoryDocumentType
  warehouses: WarehouseRecord[]
  materials: MaterialRecord[]
  readOnly?: boolean
  errors?: Record<number, string>
}>(), {
  readOnly: false,
  errors: () => ({}),
})

const emit = defineEmits<{
  'update:lines': [lines: InventoryLineDraft[]]
}>()

function valueOrEmpty(value: ResourceId | '') {
  return value === null || value === undefined ? '' : value
}

function updateLine(index: number, patch: Partial<InventoryLineDraft>) {
  emit('update:lines', props.lines.map((line, currentIndex) => (
    currentIndex === index ? { ...line, ...patch } : line
  )))
}

function updateWarehouse(index: number, value: ResourceId) {
  updateLine(index, { warehouseId: value })
}

function updateMaterial(index: number, value: ResourceId) {
  const material = props.materials.find((item) => String(item.id) === String(value))
  updateLine(index, {
    materialId: value,
    unitId: material?.unitId ?? '',
    unitName: material?.unitName ?? '',
  })
}

function updateAdjustmentDirection(index: number, value: InventoryAdjustmentDirection | '') {
  updateLine(index, { adjustmentDirection: value || '' })
}

function updateQuantity(index: number, value: string | number) {
  updateLine(index, { quantity: String(value) })
}

function updateRemark(index: number, value: string | number) {
  updateLine(index, { remark: String(value) })
}

function addLine() {
  emit('update:lines', [...props.lines, newInventoryLine(nextLineNo(props.lines))])
}

function removeLine(index: number) {
  emit('update:lines', props.lines.filter((_, currentIndex) => currentIndex !== index))
}
</script>

<template>
  <div class="inventory-line-editor">
    <div class="table-scroll">
      <el-table :data="lines" empty-text="暂无库存明细" stripe>
        <el-table-column label="行号" width="78">
          <template #default="{ row }">
            {{ row.lineNo }}
          </template>
        </el-table-column>
        <el-table-column label="仓库" min-width="170">
          <template #default="{ row, $index }">
            <el-select
              :model-value="valueOrEmpty(row.warehouseId)"
              :data-test="`inventory-line-warehouse-id-${$index}`"
              filterable
              placeholder="选择仓库"
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
        <el-table-column label="物料" min-width="220">
          <template #default="{ row, $index }">
            <el-select
              :model-value="valueOrEmpty(row.materialId)"
              :data-test="`inventory-line-material-id-${$index}`"
              filterable
              placeholder="选择物料"
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
                <span class="line-option-meta">{{ materialTypeLabel(material.materialType) }}</span>
              </el-option>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="单位" width="110">
          <template #default="{ row }">
            {{ row.unitName || '基本单位' }}
          </template>
        </el-table-column>
        <el-table-column v-if="documentType === 'ADJUSTMENT'" label="调整方向" width="130">
          <template #default="{ row, $index }">
            <el-select
              :model-value="row.adjustmentDirection"
              :data-test="`inventory-line-adjustment-direction-${$index}`"
              placeholder="选择方向"
              style="width: 100%"
              :disabled="readOnly"
              @update:model-value="updateAdjustmentDirection($index, $event || '')"
            >
              <el-option label="调增" value="INCREASE" />
              <el-option label="调减" value="DECREASE" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="130" align="right">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.quantity"
              :name="`inventory-line-quantity-${$index}`"
              inputmode="decimal"
              :disabled="readOnly"
              @update:model-value="updateQuantity($index, $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="150">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.remark"
              :name="`inventory-line-remark-${$index}`"
              :disabled="readOnly"
              @update:model-value="updateRemark($index, $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ $index }">
            <el-button
              data-test="remove-inventory-line"
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
    <el-button data-test="add-inventory-line" :disabled="readOnly" @click="addLine">
      新增明细
    </el-button>
  </div>
</template>

<style scoped>
.inventory-line-editor {
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
