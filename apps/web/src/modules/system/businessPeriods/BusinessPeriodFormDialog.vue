<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { BusinessPeriodPayload, BusinessPeriodRecord } from '../../../shared/api/businessPeriodApi'

const props = withDefaults(defineProps<{
  modelValue: boolean
  period?: BusinessPeriodRecord | null
  submitting?: boolean
  error?: string
}>(), {
  period: null,
  submitting: false,
  error: '',
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [payload: BusinessPeriodPayload]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})
const form = reactive<BusinessPeriodPayload>({
  periodCode: '',
  periodName: '',
  startDate: '',
  endDate: '',
})
const localError = ref('')

function resetForm() {
  Object.assign(form, {
    periodCode: props.period?.periodCode ?? '',
    periodName: props.period?.periodName ?? '',
    startDate: props.period?.startDate ?? '',
    endDate: props.period?.endDate ?? '',
  })
  localError.value = ''
}

function submit() {
  localError.value = ''
  if (!form.periodCode.trim() || !form.periodName.trim() || !form.startDate || !form.endDate) {
    localError.value = '请完整填写期间编码、名称和日期范围'
    return
  }
  if (form.startDate > form.endDate) {
    localError.value = '开始日期不能晚于结束日期'
    return
  }
  emit('submit', {
    periodCode: form.periodCode.trim(),
    periodName: form.periodName.trim(),
    startDate: form.startDate,
    endDate: form.endDate,
  })
}

watch(() => props.modelValue, (value) => {
  if (value) {
    resetForm()
  }
})
watch(() => props.period, () => {
  if (props.modelValue) {
    resetForm()
  }
})
</script>

<template>
  <el-dialog v-model="visible" :title="props.period ? '编辑业务期间' : '新增业务期间'" width="560px">
    <el-alert
      v-if="localError || props.error"
      class="form-alert"
      type="error"
      :title="localError || props.error"
      :closable="false"
    />
    <el-form label-position="top">
      <el-form-item label="期间编码">
        <el-input v-model="form.periodCode" name="period-code" :disabled="Boolean(props.period)" placeholder="例如 2026-07" />
      </el-form-item>
      <el-form-item label="期间名称">
        <el-input v-model="form.periodName" name="period-name" placeholder="例如 2026年07月" />
      </el-form-item>
      <el-form-item label="开始日期">
        <el-date-picker
          v-model="form.startDate"
          name="period-start-date"
          type="date"
          value-on-clear=""
          value-format="YYYY-MM-DD"
          placeholder="选择开始日期"
        />
      </el-form-item>
      <el-form-item label="结束日期">
        <el-date-picker
          v-model="form.endDate"
          name="period-end-date"
          type="date"
          value-on-clear=""
          value-format="YYYY-MM-DD"
          placeholder="选择结束日期"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        data-test="submit-business-period"
        type="primary"
        :loading="props.submitting"
        :disabled="props.submitting"
        @click="submit"
      >
        保存
      </el-button>
    </template>
  </el-dialog>
</template>
