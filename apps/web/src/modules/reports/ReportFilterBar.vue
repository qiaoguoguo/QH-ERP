<script setup lang="ts">
export interface ReportFilterField {
  key: string
  label: string
  name: string
  placeholder?: string
  type?: 'text' | 'date'
}

const props = defineProps<{
  modelValue: Record<string, string | number | undefined>
  fields: ReportFilterField[]
  loading?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, string | number | undefined>]
  search: []
  reset: []
}>()

function updateField(key: string, value: string | number | null | undefined) {
  emit('update:modelValue', {
    ...props.modelValue,
    [key]: value ?? '',
  })
}
</script>

<template>
  <el-form class="query-form report-filter-bar" label-position="top">
    <el-form-item v-for="field in fields" :key="field.key" :label="field.label">
      <div
        v-if="field.type === 'date'"
        class="report-date-field"
        :data-test="`report-date-picker-${field.name}`"
      >
        <el-date-picker value-on-clear=""
          class="report-date-picker"
          type="date"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          :name="field.name"
          :placeholder="field.placeholder ?? field.label"
          :model-value="modelValue[field.key] ? String(modelValue[field.key]) : ''"
          :disabled="loading"
          clearable
          @update:model-value="updateField(field.key, $event)"
        />
      </div>
      <el-input
        v-else
        :name="field.name"
        :placeholder="field.placeholder"
        :model-value="modelValue[field.key]"
        :disabled="loading"
        clearable
        @update:model-value="updateField(field.key, $event)"
      />
    </el-form-item>
    <div class="report-filter-bar__actions">
      <el-button data-test="search-report" type="primary" :loading="loading" @click="emit('search')">查询</el-button>
      <el-button data-test="reset-report" :disabled="loading" @click="emit('reset')">重置</el-button>
    </div>
  </el-form>
</template>

<style scoped>
.report-filter-bar {
  align-items: end;
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(168px, 1fr));
  margin: 0;
}

.report-filter-bar :deep(.el-form-item) {
  margin-bottom: 0;
}

.report-filter-bar :deep(.el-form-item__label) {
  color: var(--qherp-slate);
  font-size: 13px;
  line-height: 1.3;
  margin-bottom: 6px;
}

.report-filter-bar :deep(.el-input),
.report-date-field,
.report-date-picker {
  width: 100%;
}

.report-filter-bar__actions {
  align-self: end;
  align-items: center;
  display: flex;
  gap: 8px;
  height: 40px;
  min-width: 136px;
}

@media (max-width: 760px) {
  .report-filter-bar {
    grid-template-columns: 1fr;
  }

  .report-filter-bar__actions {
    width: 100%;
  }

  .report-filter-bar__actions .el-button {
    flex: 1 1 0;
  }
}
</style>
