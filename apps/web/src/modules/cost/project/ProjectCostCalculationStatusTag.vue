<script setup lang="ts">
import { computed } from 'vue'
import { projectCostCalculationStatusLabel, tagTypeForStatus } from './projectCostPageHelpers'

const props = defineProps<{
  status?: string | null
}>()

const label = computed(() => projectCostCalculationStatusLabel(props.status))
const type = computed(() => tagTypeForStatus(props.status))
</script>

<template>
  <el-tag :type="type" effect="plain">{{ label }}</el-tag>
</template>
