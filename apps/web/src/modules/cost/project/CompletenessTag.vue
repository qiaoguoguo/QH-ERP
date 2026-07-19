<script setup lang="ts">
import { computed } from 'vue'
import { projectCostCompletenessLabel, tagTypeForStatus } from './projectCostPageHelpers'

const props = defineProps<{
  status?: string | null
}>()

const label = computed(() => projectCostCompletenessLabel(props.status))
const type = computed(() => tagTypeForStatus(props.status))
</script>

<template>
  <el-tag :type="type" effect="plain">{{ label }}</el-tag>
</template>
