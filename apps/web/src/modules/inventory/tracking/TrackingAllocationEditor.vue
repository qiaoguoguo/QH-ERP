<script setup lang="ts">
import { computed, ref } from 'vue'
import type { InventoryTrackingMethod, ResourceId } from '../../../shared/api/inventoryApi'
import { formatQuantity, qualityStatusLabel, validateInventoryQuantity } from '../inventoryPageHelpers'

interface TrackingAllocationDraft {
  batchId?: ResourceId | null
  batchNo?: string | null
  serialId?: ResourceId | null
  serialNo?: string | null
  quantity?: string | number | null
  qualityStatus?: string | null
  qualityStatusName?: string | null
  disabledReason?: string | null
}

const props = withDefaults(defineProps<{
  modelValue: TrackingAllocationDraft[]
  trackingMethod: InventoryTrackingMethod
  expectedQuantity?: string | number | null
  disabled?: boolean
  disabledReason?: string
}>(), {
  expectedQuantity: null,
  disabled: false,
  disabledReason: '',
})

const emit = defineEmits<{
  'update:modelValue': [value: TrackingAllocationDraft[]]
}>()

const serialPasteText = ref('')

const rows = computed(() => props.modelValue ?? [])

function expectedNumber() {
  const value = props.expectedQuantity
  if (value === null || value === undefined || value === '') {
    return null
  }
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function updateRow(index: number, patch: Partial<TrackingAllocationDraft>) {
  const nextRows = rows.value.map((row, rowIndex) => (rowIndex === index ? { ...row, ...patch } : row))
  emit('update:modelValue', nextRows)
}

function addBatchRow() {
  emit('update:modelValue', [...rows.value, { batchNo: '', quantity: '' }])
}

function addSerialRow(serialNo = '') {
  emit('update:modelValue', [...rows.value, { serialNo, quantity: '1' }])
}

function removeRow(index: number) {
  emit('update:modelValue', rows.value.filter((_, rowIndex) => rowIndex !== index))
}

function applySerialPaste() {
  const serialNos = serialPasteText.value
    .split(/\r?\n|,|;|\s+/)
    .map((item) => item.trim())
    .filter(Boolean)
  if (serialNos.length === 0) {
    return
  }
  emit('update:modelValue', [
    ...rows.value,
    ...serialNos.map((serialNo) => ({ serialNo, quantity: '1' })),
  ])
  serialPasteText.value = ''
}

const validationMessages = computed(() => {
  if (props.trackingMethod === 'NONE') {
    return []
  }

  const messages: string[] = []
  const expected = expectedNumber()
  if (props.trackingMethod === 'BATCH') {
    let total = 0
    rows.value.forEach((row, index) => {
      if (!String(row.batchNo ?? '').trim()) {
        messages.push(`第 ${index + 1} 行批次号不能为空`)
      }
      const result = validateInventoryQuantity(row.quantity)
      if (result.message) {
        messages.push(`第 ${index + 1} 行数量${result.message.replace('数量', '')}`)
        return
      }
      total += result.value ?? 0
    })
    if (expected !== null && Math.abs(total - expected) > 0.000001) {
      messages.push(`批次数量合计 ${formatQuantity(total)} 与业务数量 ${formatQuantity(expected)} 不一致`)
    }
  }

  if (props.trackingMethod === 'SERIAL') {
    const seen = new Set<string>()
    const duplicated = new Set<string>()
    rows.value.forEach((row, index) => {
      const serialNo = String(row.serialNo ?? '').trim()
      if (!serialNo) {
        messages.push(`第 ${index + 1} 行序列号不能为空`)
        return
      }
      if (seen.has(serialNo)) {
        duplicated.add(serialNo)
      }
      seen.add(serialNo)
      if (String(row.quantity ?? '1') !== '1') {
        messages.push(`第 ${index + 1} 行序列号数量必须为 1`)
      }
    })
    if (duplicated.size > 0) {
      messages.push(`序列号重复：${Array.from(duplicated).join('、')}`)
    }
    if (expected !== null && rows.value.length !== expected) {
      messages.push(`序列号数量 ${rows.value.length} 与业务数量 ${formatQuantity(expected)} 不一致`)
    }
  }

  return messages
})
</script>

<template>
  <div class="tracking-allocation-editor">
    <el-alert
      v-if="trackingMethod === 'NONE'"
      type="info"
      title="不追踪物料无需录入批次或序列号"
      :closable="false"
    />

    <template v-else>
      <div class="tracking-editor-header">
        <div>
          <strong>{{ trackingMethod === 'BATCH' ? '批次分配' : '序列号分配' }}</strong>
          <span v-if="expectedQuantity !== null && expectedQuantity !== undefined" class="expected-quantity">
            业务数量 {{ formatQuantity(expectedQuantity) }}
          </span>
        </div>
        <el-button
          v-if="trackingMethod === 'BATCH'"
          size="small"
          :disabled="disabled"
          data-test="add-batch-allocation"
          @click="addBatchRow"
        >
          添加批次
        </el-button>
        <el-button
          v-else
          size="small"
          :disabled="disabled"
          data-test="add-serial-allocation"
          @click="addSerialRow()"
        >
          添加序列号
        </el-button>
      </div>

      <el-alert
        v-if="disabled && disabledReason"
        class="tracking-state-alert"
        type="warning"
        :title="disabledReason"
        :closable="false"
      />

      <el-alert
        v-if="validationMessages.length > 0"
        class="tracking-validation-alert"
        type="error"
        :title="`追踪分配存在 ${validationMessages.length} 项校验问题`"
        :closable="false"
      >
        <ul class="tracking-validation-list">
          <li
            v-for="message in validationMessages"
            :key="message"
            data-test="tracking-allocation-error"
          >
            {{ message }}
          </li>
        </ul>
      </el-alert>

      <el-table :data="rows" empty-text="暂无追踪分配" stripe>
        <el-table-column v-if="trackingMethod === 'BATCH'" label="批次号" min-width="180">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.batchNo"
              :disabled="disabled"
              placeholder="输入批次号"
              @update:model-value="updateRow($index, { batchNo: String($event) })"
            />
          </template>
        </el-table-column>
        <el-table-column v-if="trackingMethod === 'SERIAL'" label="序列号" min-width="220">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.serialNo"
              :disabled="disabled"
              placeholder="输入序列号"
              @update:model-value="updateRow($index, { serialNo: String($event), quantity: '1' })"
            />
          </template>
        </el-table-column>
        <el-table-column label="数量" min-width="130" align="right">
          <template #default="{ row, $index }">
            <el-input
              :model-value="trackingMethod === 'SERIAL' ? '1' : row.quantity"
              :disabled="disabled || trackingMethod === 'SERIAL'"
              class="quantity-input"
              @update:model-value="updateRow($index, { quantity: String($event) })"
            />
          </template>
        </el-table-column>
        <el-table-column label="质量状态" min-width="110">
          <template #default="{ row }">
            {{ qualityStatusLabel(row.qualityStatus, row.qualityStatusName) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="88">
          <template #default="{ $index }">
            <el-button size="small" text type="danger" :disabled="disabled" @click="removeRow($index)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="trackingMethod === 'SERIAL'" class="serial-paste-box">
        <el-input
          v-model="serialPasteText"
          :disabled="disabled"
          type="textarea"
          :rows="3"
          placeholder="可批量粘贴序列号，使用换行、空格、逗号或分号分隔"
        />
        <el-button size="small" :disabled="disabled" data-test="apply-serial-paste" @click="applySerialPaste">
          导入序列号
        </el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.tracking-editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.expected-quantity {
  margin-left: 10px;
  color: var(--qherp-muted);
}

.tracking-state-alert,
.tracking-validation-alert {
  margin-bottom: 10px;
}

.tracking-validation-list {
  margin: 6px 0 0;
  padding-left: 18px;
}

.tracking-validation-list li + li {
  margin-top: 4px;
}

.quantity-input {
  max-width: 128px;
}

.serial-paste-box {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}
</style>
