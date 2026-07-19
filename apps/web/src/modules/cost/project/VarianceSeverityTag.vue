<script setup lang="ts">
import { computed } from 'vue'
import { projectCostVarianceSeverityLabel, tagTypeForStatus } from './projectCostPageHelpers'

const props = defineProps<{
  severity?: string | null
}>()

const label = computed(() => projectCostVarianceSeverityLabel(props.severity))
const type = computed(() => tagTypeForStatus(props.severity))
</script>

<template>
  <el-tag :type="type" effect="plain">{{ label }}</el-tag>
</template>
