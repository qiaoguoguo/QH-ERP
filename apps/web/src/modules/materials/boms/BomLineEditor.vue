<script setup lang="ts">
import type { CandidateItem, ResourceId } from '../../../shared/api/bomApi'
import { candidateMetaText, type BomLineDraft, newBomLine, nextLineNo } from './bomPageHelpers'

const props = withDefaults(defineProps<{
  lines: BomLineDraft[]
  materials: CandidateItem[]
  units: CandidateItem[]
  loadMaterialCandidates?: (keyword: string, selectedIds: ResourceId[]) => void
  loadUnitCandidates?: (keyword: string, selectedIds: ResourceId[]) => void
  readOnly?: boolean
  errors?: Record<number, string>
}>(), {
  readOnly: false,
  errors: () => ({}),
})

const emit = defineEmits<{
  'update:lines': [lines: BomLineDraft[]]
}>()

function updateLine(index: number, patch: Partial<BomLineDraft>) {
  emit('update:lines', props.lines.map((line, currentIndex) => (
    currentIndex === index ? { ...line, ...patch } : line
  )))
}

function updateChildMaterial(index: number, value: ResourceId) {
  updateLine(index, { childMaterialId: value })
}

function updateUnit(index: number, value: ResourceId | '') {
  updateLine(index, { businessUnitId: value || '' })
}

function updateQuantity(index: number, value: string | number) {
  updateLine(index, { businessQuantity: String(value) })
}

function updateLossRate(index: number, value: string | number) {
  updateLine(index, { lossRate: String(value) })
}

function updateRemark(index: number, value: string | number) {
  updateLine(index, { remark: String(value) })
}

function addLine() {
  emit('update:lines', [...props.lines, newBomLine(nextLineNo(props.lines))])
}

function removeLine(index: number) {
  emit('update:lines', props.lines.filter((_, currentIndex) => currentIndex !== index))
}

function valueOrEmpty(value: ResourceId | '') {
  return value === null || value === undefined ? '' : value
}

function selectedIds(value: ResourceId | ''): ResourceId[] {
  return value === '' || value === null || value === undefined ? [] : [value]
}

function searchMaterials(keyword: string, currentValue: ResourceId | '') {
  props.loadMaterialCandidates?.(keyword, selectedIds(currentValue))
}

function searchUnits(keyword: string, currentValue: ResourceId | '') {
  props.loadUnitCandidates?.(keyword, selectedIds(currentValue))
}
</script>

<template>
  <div class="bom-line-editor">
    <div data-test="bom-line-scroll" class="table-scroll bom-line-scroll">
      <el-table class="bom-line-table" :data="lines" empty-text="暂无 BOM 明细" stripe>
        <el-table-column label="行号" width="82">
          <template #default="{ row }">
            {{ row.lineNo }}
          </template>
        </el-table-column>
        <el-table-column label="子项物料" min-width="210">
          <template #default="{ row, $index }">
            <el-select
              :model-value="valueOrEmpty(row.childMaterialId)"
              :data-test="`bom-line-child-material-id-${$index}`"
              filterable
              remote
              placeholder="选择子项物料"
              style="width: 100%"
              :disabled="readOnly"
              :remote-method="(keyword: string) => searchMaterials(keyword, row.childMaterialId)"
              @visible-change="(visible: boolean) => visible && searchMaterials('', row.childMaterialId)"
              @update:model-value="updateChildMaterial($index, $event)"
            >
              <el-option
                v-for="material in materials"
                :key="material.id"
                :label="`${material.code} ${material.name}`"
                :value="material.id"
                :disabled="Boolean(material.disabled)"
              >
                <span>{{ material.code }} {{ material.name }}</span>
                <span class="line-option-meta">{{ candidateMetaText(material) }}</span>
              </el-option>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="业务数量" width="130">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.businessQuantity"
              :name="`bom-line-quantity-${$index}`"
              :disabled="readOnly"
              @update:model-value="updateQuantity($index, $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="业务单位" width="150">
          <template #default="{ row, $index }">
            <el-select
              :model-value="valueOrEmpty(row.businessUnitId)"
              :data-test="`bom-line-unit-id-${$index}`"
              clearable
              filterable
              remote
              placeholder="选择业务单位"
              style="width: 100%"
              :disabled="readOnly"
              :remote-method="(keyword: string) => searchUnits(keyword, row.businessUnitId)"
              @visible-change="(visible: boolean) => visible && searchUnits('', row.businessUnitId)"
              @update:model-value="updateUnit($index, $event || '')"
            >
              <el-option
                v-for="unit in units"
                :key="unit.id"
                :label="`${unit.code} ${unit.name}`"
                :value="unit.id"
                :disabled="Boolean(unit.disabled)"
              >
                <span>{{ unit.code }} {{ unit.name }}</span>
                <span class="line-option-meta">{{ candidateMetaText(unit) }}</span>
              </el-option>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="损耗率" width="130">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.lossRate"
              :name="`bom-line-loss-rate-${$index}`"
              :disabled="readOnly"
              @update:model-value="updateLossRate($index, $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="150">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.remark"
              :name="`bom-line-remark-${$index}`"
              :disabled="readOnly"
              @update:model-value="updateRemark($index, $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ $index }">
            <el-button
              data-test="remove-bom-line"
              text
              type="danger"
              :disabled="readOnly"
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
    <el-button data-test="add-bom-line" :disabled="readOnly" @click="addLine">
      新增明细
    </el-button>
  </div>
</template>

<style scoped>
.bom-line-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  width: 100%;
}

.bom-line-scroll {
  min-width: 0;
  width: 100%;
}

.bom-line-table {
  min-width: 980px;
}

.line-option-meta {
  color: var(--qherp-muted);
  float: right;
  font-size: 12px;
  margin-left: 12px;
}
</style>
