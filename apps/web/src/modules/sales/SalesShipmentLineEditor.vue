<script setup lang="ts">
import type { ResourceId } from '../../shared/api/salesApi'
import TrackingAllocationReadonlyTable from '../inventory/tracking/TrackingAllocationReadonlyTable.vue'
import QualityStatusTag from '../quality/QualityStatusTag.vue'
import {
  formatSalesQuantity,
  type SalesShipmentLineDraft,
  type SalesShipmentSourceLine,
  newSalesShipmentLine,
  nextSalesShipmentLineNo,
} from './salesPageHelpers'

const props = withDefaults(defineProps<{
  lines: SalesShipmentLineDraft[]
  sourceLines: SalesShipmentSourceLine[]
  readOnly?: boolean
  errors?: Record<number, string>
}>(), {
  readOnly: false,
  errors: () => ({}),
})

const emit = defineEmits<{
  'update:lines': [lines: SalesShipmentLineDraft[]]
  'open-tracking-picker': [index: number]
}>()

function valueOrEmpty(value: ResourceId | '') {
  return value === null || value === undefined ? '' : value
}

function updateLine(index: number, patch: Partial<SalesShipmentLineDraft>) {
  emit('update:lines', props.lines.map((line, currentIndex) => (
    currentIndex === index ? { ...line, ...patch } : line
  )))
}

function updateSourceLine(index: number, value: ResourceId) {
  const sourceLine = props.sourceLines.find((line) => String(line.id) === String(value))
  updateLine(index, {
    orderLineId: value,
    deliveryPlanId: sourceLine?.deliveryPlanId ?? '',
    deliveryPlanNo: sourceLine?.deliveryPlanNo ?? '',
    deliveryPlanDate: sourceLine?.deliveryPlanDate ?? '',
    materialId: sourceLine?.materialId ?? '',
    materialCode: sourceLine?.materialCode ?? '',
    materialName: sourceLine?.materialName ?? '',
    trackingMethod: sourceLine?.trackingMethod ?? 'NONE',
    trackingMethodName: sourceLine?.trackingMethodName ?? '不追踪',
    unitId: sourceLine?.unitId ?? '',
    unitName: sourceLine?.unitName ?? '',
    orderedQuantity: sourceLine?.orderedQuantity ?? 0,
    shippedQuantityBefore: sourceLine?.shippedQuantityBefore ?? 0,
    remainingQuantityBefore: sourceLine?.remainingQuantityBefore ?? 0,
    reservationWarehouseId: sourceLine?.reservationWarehouseId ?? null,
    reservationWarehouseName: sourceLine?.reservationWarehouseName ?? null,
    qualityStatus: sourceLine?.qualityStatus ?? null,
    qualityStatusName: sourceLine?.qualityStatusName ?? null,
    quantityOnHand: sourceLine?.quantityOnHand ?? null,
    reservedQuantity: sourceLine?.reservedQuantity ?? null,
    occupiedQuantity: sourceLine?.occupiedQuantity ?? null,
    availableQuantity: sourceLine?.availableQuantity ?? null,
    availableToPromiseQuantity: sourceLine?.availableToPromiseQuantity ?? null,
    selectable: sourceLine?.selectable ?? null,
    disabledReasonCode: sourceLine?.disabledReasonCode ?? null,
    disabledReason: sourceLine?.disabledReason ?? null,
    maxSelectableQuantity: sourceLine?.maxSelectableQuantity ?? null,
    trackingAllocations: [],
  })
}

function updateText(index: number, key: 'quantity' | 'remark', value: string | number) {
  updateLine(index, { [key]: String(value) })
}

function sourceLineFor(row: SalesShipmentLineDraft): SalesShipmentSourceLine | undefined {
  return props.sourceLines.find((line) => String(line.id) === String(row.orderLineId))
}

function candidateValue(
  row: SalesShipmentLineDraft,
  key:
    | 'quantityOnHand'
    | 'reservedQuantity'
    | 'occupiedQuantity'
    | 'availableQuantity'
    | 'availableToPromiseQuantity'
    | 'maxSelectableQuantity'
    | 'disabledReason'
    | 'reservationWarehouseName'
    | 'selectable',
) {
  return row[key] ?? sourceLineFor(row)?.[key] ?? null
}

function candidateText(row: SalesShipmentLineDraft, key: 'qualityStatus' | 'qualityStatusName') {
  return row[key] ?? sourceLineFor(row)?.[key] ?? null
}

function numericCandidateValue(row: SalesShipmentLineDraft, key: 'maxSelectableQuantity'): number | null {
  const value = candidateValue(row, key)
  if (value === null || value === undefined || value === '') {
    return null
  }
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : null
}

function isCandidateUnavailable(row: SalesShipmentLineDraft): boolean {
  const maxSelectableQuantity = numericCandidateValue(row, 'maxSelectableQuantity')
  return candidateValue(row, 'selectable') === false
    || (maxSelectableQuantity !== null && maxSelectableQuantity <= 0)
}

function addLine() {
  emit('update:lines', [...props.lines, newSalesShipmentLine(nextSalesShipmentLineNo(props.lines))])
}

function removeLine(index: number) {
  emit('update:lines', props.lines.filter((_, currentIndex) => currentIndex !== index))
}

function openTrackingPicker(index: number) {
  emit('open-tracking-picker', index)
}
</script>

<template>
  <div class="sales-shipment-line-editor">
    <div class="table-scroll sales-shipment-line-scroll" data-test="sales-shipment-line-scroll">
      <el-table :data="lines" empty-text="暂无销售出库明细" stripe>
        <el-table-column label="行号" width="78">
          <template #default="{ row }">
            {{ row.lineNo }}
          </template>
        </el-table-column>
        <el-table-column label="来源订单行" min-width="180">
          <template #default="{ row, $index }">
            <el-select
              :model-value="valueOrEmpty(row.orderLineId)"
              :data-test="`sales-shipment-line-order-line-id-${$index}`"
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
                :disabled="sourceLine.selectable === false"
              >
                <span>{{ sourceLine.lineNo }} {{ sourceLine.materialCode }} {{ sourceLine.materialName }}</span>
                <span class="line-option-meta">
                  未出库 {{ formatSalesQuantity(sourceLine.remainingQuantityBefore) }} /
                  预留仓库 {{ sourceLine.reservationWarehouseName || '-' }} /
                  {{ sourceLine.qualityStatusName || '-' }} /
                  现货净可用 {{ formatSalesQuantity(sourceLine.availableQuantity) }} /
                  本次最多出库 {{ formatSalesQuantity(sourceLine.maxSelectableQuantity) }}
                </span>
                <span v-if="sourceLine.disabledReason" class="line-option-reason">
                  {{ sourceLine.disabledReason }}
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
        <el-table-column label="交付计划" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.deliveryPlanId">
              交付计划 {{ row.deliveryPlanNo || row.deliveryPlanId }}
              <span v-if="row.deliveryPlanDate"> · {{ row.deliveryPlanDate }}</span>
            </span>
            <span v-else>未选择交付计划</span>
          </template>
        </el-table-column>
        <el-table-column label="订单数量" width="110" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatSalesQuantity(row.orderedQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="已出库" width="110" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatSalesQuantity(row.shippedQuantityBefore) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="未出库" width="110" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatSalesQuantity(row.remainingQuantityBefore) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="预留仓库" width="130" show-overflow-tooltip>
          <template #default="{ row }">
            {{ candidateValue(row, 'reservationWarehouseName') || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="质量状态" width="110">
          <template #default="{ row }">
            <QualityStatusTag
              :quality-status="candidateText(row, 'qualityStatus')"
              :quality-status-name="candidateText(row, 'qualityStatusName')"
            />
          </template>
        </el-table-column>
        <el-table-column label="现存数量" width="120" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatSalesQuantity(candidateValue(row, 'quantityOnHand')) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="占用库存" width="120" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatSalesQuantity(candidateValue(row, 'occupiedQuantity')) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="预留库存" width="120" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatSalesQuantity(candidateValue(row, 'reservedQuantity')) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="现货净可用" width="130" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatSalesQuantity(candidateValue(row, 'availableQuantity')) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="可承诺量" width="120" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatSalesQuantity(candidateValue(row, 'availableToPromiseQuantity')) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="本次最多出库" width="140" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatSalesQuantity(candidateValue(row, 'maxSelectableQuantity')) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="禁用原因" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="candidate-disabled-reason">{{ candidateValue(row, 'disabledReason') || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="本次出库数量" width="150" align="right">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.quantity"
              :name="`sales-shipment-line-quantity-${$index}`"
              inputmode="decimal"
              placeholder="> 0"
              :disabled="readOnly || isCandidateUnavailable(row)"
              @update:model-value="updateText($index, 'quantity', $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="批次/序列" min-width="260">
          <template #default="{ row, $index }">
            <template v-if="row.trackingMethod && row.trackingMethod !== 'NONE'">
              <el-button
                size="small"
                :data-test="`open-sales-shipment-tracking-${$index}`"
                :disabled="readOnly || isCandidateUnavailable(row)"
                @click="openTrackingPicker($index)"
              >
                {{ row.trackingMethod === 'BATCH' ? '选择批次' : '选择序列号' }}
              </el-button>
              <TrackingAllocationReadonlyTable
                class="line-tracking-readonly"
                :tracking-method="row.trackingMethod"
                :allocations="row.trackingAllocations"
              />
            </template>
            <span v-else class="tracking-empty-text">不追踪</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="150">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.remark"
              :name="`sales-shipment-line-remark-${$index}`"
              placeholder="可选"
              :disabled="readOnly"
              @update:model-value="updateText($index, 'remark', $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ $index }">
            <el-button
              data-test="remove-sales-shipment-line"
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
    <el-button data-test="add-sales-shipment-line" :disabled="readOnly" @click="addLine">
      新增销售出库明细
    </el-button>
  </div>
</template>

<style scoped>
.sales-shipment-line-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 100%;
  min-width: 0;
}

.sales-shipment-line-scroll {
  max-width: 100%;
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
}

.line-option-meta {
  color: var(--qherp-muted);
  float: right;
  font-size: 12px;
  margin-left: 12px;
}

.line-option-reason,
.candidate-disabled-reason {
  color: var(--el-color-danger);
  display: block;
  font-size: 12px;
}

.line-tracking-readonly {
  margin-top: 8px;
}

.tracking-empty-text {
  color: var(--qherp-muted);
}

.numeric-cell {
  display: inline-block;
  min-width: 72px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 760px) {
  .sales-shipment-line-editor {
    overflow: hidden;
  }

  .line-option-meta {
    display: block;
    float: none;
    margin-left: 0;
  }
}
</style>
