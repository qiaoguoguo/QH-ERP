<script setup lang="ts">
import { computed } from 'vue'
import type { InventoryTraceDetailRecord, InventoryTraceNodeRecord } from '../../../shared/api/inventoryApi'
import { trackingMethodLabel } from '../../master/shared/masterPageHelpers'
import { formatQuantity, inventoryTraceNodeTypeLabel, qualityStatusLabel } from '../inventoryPageHelpers'

const props = withDefaults(defineProps<{
  modelValue: boolean
  detail?: InventoryTraceDetailRecord | null
  loading?: boolean
  error?: string
}>(), {
  detail: null,
  loading: false,
  error: '',
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const traceNo = computed(() => {
  const subject = props.detail?.subject
  return subject?.batchNo || subject?.serialNo || '-'
})

const documentRows = computed<InventoryTraceNodeRecord[]>(() => {
  const detail = props.detail
  if (!detail) {
    return []
  }
  return [
    ...detail.sourceRecords,
    ...detail.qualityEvents,
    ...detail.outboundRecords,
    ...detail.returnRecords,
  ]
})

function formatDate(value?: string | null) {
  return value || '-'
}
</script>

<template>
  <el-drawer v-model="visible" title="来源去向追溯" size="min(720px, 96vw)">
    <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
    <el-skeleton v-if="loading" :rows="8" animated />
    <template v-else-if="detail">
      <section class="trace-section">
        <h3>基础信息</h3>
        <dl class="trace-detail-list">
          <dt>追踪方式</dt>
          <dd>{{ trackingMethodLabel(detail.subject.trackingMethod) }}</dd>
          <dt>追踪身份</dt>
          <dd>{{ traceNo }}</dd>
          <dt>物料</dt>
          <dd>{{ detail.subject.materialCode || '-' }} {{ detail.subject.materialName || '' }}</dd>
        </dl>
      </section>

      <section class="trace-section">
        <h3>当前库存</h3>
        <el-table class="table-scroll" :data="detail.currentBalances" empty-text="暂无当前库存" stripe>
          <el-table-column prop="warehouseName" label="仓库" min-width="130" show-overflow-tooltip />
          <el-table-column label="质量状态" min-width="100">
            <template #default="{ row }">
              {{ qualityStatusLabel(row.qualityStatus, row.qualityStatusName) }}
            </template>
          </el-table-column>
          <el-table-column label="现存" min-width="110" align="right">
            <template #default="{ row }">
              <span class="numeric-cell">{{ formatQuantity(row.quantityOnHand) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="可用" min-width="110" align="right">
            <template #default="{ row }">
              <span class="numeric-cell">{{ formatQuantity(row.availableQuantity) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="trace-section">
        <h3>来源去向</h3>
        <el-table class="table-scroll" :data="documentRows" empty-text="暂无来源去向记录" stripe>
          <el-table-column label="节点" min-width="110" show-overflow-tooltip>
            <template #default="{ row }">
              {{ inventoryTraceNodeTypeLabel(row.nodeType, row.nodeTypeName) }}
            </template>
          </el-table-column>
          <el-table-column prop="documentNo" label="单号" min-width="160" show-overflow-tooltip />
          <el-table-column label="业务日期" min-width="110">
            <template #default="{ row }">
              {{ formatDate(row.businessDate) }}
            </template>
          </el-table-column>
          <el-table-column prop="warehouseName" label="仓库" min-width="120" show-overflow-tooltip />
          <el-table-column label="数量" min-width="100" align="right">
            <template #default="{ row }">
              <span class="numeric-cell">{{ formatQuantity(row.quantity) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="trace-section">
        <h3>库存流水</h3>
        <el-table class="table-scroll" :data="detail.movements" empty-text="暂无关联库存流水" stripe>
          <el-table-column prop="documentNo" label="流水或单号" min-width="160" show-overflow-tooltip />
          <el-table-column label="类型" min-width="120">
            <template #default="{ row }">
              {{ inventoryTraceNodeTypeLabel(row.nodeType, row.nodeTypeName) }}
            </template>
          </el-table-column>
          <el-table-column prop="warehouseName" label="仓库" min-width="120" show-overflow-tooltip />
          <el-table-column label="数量" min-width="100" align="right">
            <template #default="{ row }">
              <span class="numeric-cell">{{ formatQuantity(row.quantity) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section v-if="detail.activeReservations.length > 0" class="trace-section">
        <h3>活动预留和占用</h3>
        <el-table class="table-scroll" :data="detail.activeReservations" stripe>
          <el-table-column prop="documentNo" label="来源单号" min-width="160" show-overflow-tooltip />
          <el-table-column label="类型" min-width="120">
            <template #default="{ row }">
              {{ inventoryTraceNodeTypeLabel(row.nodeType, row.nodeTypeName) }}
            </template>
          </el-table-column>
          <el-table-column label="数量" min-width="100" align="right">
            <template #default="{ row }">
              <span class="numeric-cell">{{ formatQuantity(row.quantity) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section v-if="detail.restrictedSources.length > 0" class="trace-section">
        <el-alert type="warning" title="权限受限" :closable="false" />
        <el-table class="restricted-source-table table-scroll" :data="detail.restrictedSources" stripe>
          <el-table-column label="受限来源" min-width="120">
            <template #default="{ row }">
              {{ inventoryTraceNodeTypeLabel(row.nodeType, row.nodeTypeName) }}
            </template>
          </el-table-column>
          <el-table-column prop="documentNo" label="摘要单号" min-width="160" show-overflow-tooltip />
          <el-table-column label="业务日期" min-width="110">
            <template #default="{ row }">
              {{ formatDate(row.businessDate) }}
            </template>
          </el-table-column>
        </el-table>
      </section>
    </template>
    <el-empty v-else description="暂无追溯数据" />
  </el-drawer>
</template>

<style scoped>
.state-alert,
.trace-section {
  margin-bottom: 14px;
}

.trace-section h3 {
  margin: 0 0 10px;
  font-size: 15px;
}

.trace-detail-list {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 8px 12px;
  margin: 0;
}

.trace-detail-list dt {
  color: var(--qherp-muted);
}

.trace-detail-list dd {
  min-width: 0;
  margin: 0;
  word-break: break-word;
}

.numeric-cell {
  display: inline-block;
  min-width: 64px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.restricted-source-table {
  margin-top: 10px;
}
</style>
