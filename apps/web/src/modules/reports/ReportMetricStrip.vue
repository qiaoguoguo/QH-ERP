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
  gap: 10px;
  grid-template-columns: repeat(auto-fit, minmax(148px, 1fr));
  margin: 0;
}

.report-metric {
  background: var(--qherp-surface);
  border: 1px solid var(--qherp-border);
  border-radius: 6px;
  min-height: 76px;
  min-width: 0;
  padding: 12px;
}

.report-metric__label {
  color: var(--qherp-steel);
  display: block;
  font-size: 13px;
  margin-bottom: 6px;
}

.report-metric__value {
  color: var(--qherp-text);
  display: block;
  font-size: 18px;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  line-height: 1.2;
  overflow-wrap: anywhere;
}

.report-metric__hint {
  color: var(--qherp-stone);
  display: block;
  margin-top: 4px;
}
</style>
