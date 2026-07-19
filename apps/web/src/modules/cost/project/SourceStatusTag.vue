<script setup lang="ts">
import { computed } from 'vue'
import { projectCostSourceStatusLabel, tagTypeForStatus } from './projectCostPageHelpers'

const props = defineProps<{
  status?: string | null
}>()

const label = computed(() => projectCostSourceStatusLabel(props.status))
const type = computed(() => tagTypeForStatus(props.status))
</script>

<template>
  <el-tag :type="type" effect="plain">{{ label }}</el-tag>
</template>
