<script setup lang="ts">
import { workOrderStatusLabel, workOrderStatusType } from './productionPageHelpers'

defineProps<{
  status: string
}>()
</script>

<template>
  <el-tag :type="workOrderStatusType(status)" size="small">
    {{ workOrderStatusLabel(status) }}
  </el-tag>
</template>
