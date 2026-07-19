<script setup lang="ts">
import { computed } from 'vue'
import { projectCostStageLabel } from './projectCostPageHelpers'

const props = defineProps<{
  stage?: string | null
}>()

const label = computed(() => projectCostStageLabel(props.stage))
</script>

<template>
  <el-tag type="info" effect="plain">{{ label }}</el-tag>
</template>
