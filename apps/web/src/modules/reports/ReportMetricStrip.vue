<script setup lang="ts">
defineProps<{
  metrics: Array<{
    label: string
    value: string | number | null | undefined
    hint?: string
  }>
}>()
</script>

<template>
  <div class="report-metric-strip">
    <div v-for="metric in metrics" :key="metric.label" class="report-metric">
      <span class="report-metric__label">{{ metric.label }}</span>
      <strong class="report-metric__value">{{ metric.value ?? '-' }}</strong>
      <small v-if="metric.hint" class="report-metric__hint">{{ metric.hint }}</small>
    </div>
  </div>
</template>

<style scoped>
.report-metric-strip {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  margin: 14px 0;
}

.report-metric {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  padding: 12px;
  min-width: 0;
}

.report-metric__label {
  color: #606266;
  display: block;
  font-size: 13px;
  margin-bottom: 6px;
}

.report-metric__value {
  color: #1f2d3d;
  display: block;
  font-size: 18px;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
  overflow-wrap: anywhere;
}

.report-metric__hint {
  color: #909399;
  display: block;
  margin-top: 4px;
}
</style>
