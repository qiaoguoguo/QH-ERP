<script setup lang="ts">
import type { ReversalTraceRecord } from '../../shared/api/returnRefundReversalApi'
import ReversalTracePanel from './ReversalTracePanel.vue'

defineProps<{
  visible: boolean
  rows: ReversalTraceRecord[]
  dataTest: string
  loading?: boolean
  error?: string
}>()

const emit = defineEmits<{
  close: []
}>()
</script>

<template>
  <el-drawer
    v-if="visible"
    :model-value="visible"
    class="production-reversal-trace-drawer"
    title="反向追溯"
    direction="rtl"
    size="min(960px, 92vw)"
    @close="emit('close')"
  >
    <div :data-test="dataTest" class="production-reversal-trace-drawer__surface">
      <ReversalTracePanel
        content-only
        :visible="true"
        :rows="rows"
        :loading="loading"
        :error="error"
        @close="emit('close')"
      />
      <div class="production-reversal-trace-drawer__footer">
        <el-button data-test="close-production-reversal-trace" @click="emit('close')">关闭</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped>
.production-reversal-trace-drawer__surface {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.production-reversal-trace-drawer__footer {
  border-top: 1px solid #dcdfe6;
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}
</style>
