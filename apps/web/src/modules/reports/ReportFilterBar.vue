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

function updateField(key: string, value: string | number | undefined) {
  emit('update:modelValue', {
    ...props.modelValue,
    [key]: value,
  })
}
</script>

<template>
  <el-form class="report-filter-bar" label-position="top">
    <el-form-item v-for="field in fields" :key="field.key" :label="field.label">
      <el-input
        :name="field.name"
        :type="field.type ?? 'text'"
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
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  margin: 16px 0;
}

.report-filter-bar__actions {
  display: flex;
  gap: 8px;
  min-width: 136px;
}
</style>
