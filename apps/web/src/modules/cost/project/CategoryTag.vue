<script setup lang="ts">
import { computed } from 'vue'
import { projectCostCategoryLabel } from './projectCostPageHelpers'

const props = defineProps<{
  category?: string | null
}>()

const label = computed(() => projectCostCategoryLabel(props.category))
</script>

<template>
  <el-tag effect="plain">{{ label }}</el-tag>
</template>
