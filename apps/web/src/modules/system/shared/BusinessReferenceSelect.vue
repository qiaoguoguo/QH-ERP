<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import type { BusinessReferenceId, BusinessReferenceOption } from './businessReferenceSelectTypes'

const props = withDefaults(defineProps<{
  modelValue: BusinessReferenceId | ''
  placeholder: string
  loadOptions: (keyword: string, selectedValue?: BusinessReferenceId | '') => Promise<BusinessReferenceOption[]>
  disabled?: boolean
  clearable?: boolean
  dataTest?: string
}>(), {
  clearable: true,
  disabled: false,
  dataTest: undefined,
})

const emit = defineEmits<{
  'update:modelValue': [value: BusinessReferenceId | '']
}>()

const options = ref<BusinessReferenceOption[]>([])
const loading = ref(false)
let requestSeq = 0

async function remoteSearch(keyword = '') {
  const seq = ++requestSeq
  loading.value = true
  try {
    const loaded = await props.loadOptions(keyword, props.modelValue)
    if (seq === requestSeq) {
      options.value = loaded
    }
  } finally {
    if (seq === requestSeq) {
      loading.value = false
    }
  }
}

function updateValue(value: BusinessReferenceId | '' | null | undefined) {
  emit('update:modelValue', value ?? '')
}

onMounted(() => {
  void remoteSearch('')
})

watch(() => props.modelValue, () => {
  void remoteSearch('')
})

defineExpose({ remoteSearch })
</script>

<template>
  <el-select
    :model-value="modelValue"
    :data-test="dataTest"
    filterable
    remote
    reserve-keyword
    :remote-method="remoteSearch"
    :loading="loading"
    :clearable="clearable"
    :disabled="disabled"
    :placeholder="placeholder"
    @update:model-value="updateValue"
    @visible-change="(visible: boolean) => visible && remoteSearch('')"
  >
    <el-option
      v-for="option in options"
      :key="String(option.id)"
      :label="option.label"
      :value="option.id"
      :disabled="option.disabled"
    >
      <span>{{ option.label }}</span>
      <small v-if="option.disabledReason"> · {{ option.disabledReason }}</small>
    </el-option>
  </el-select>
</template>
