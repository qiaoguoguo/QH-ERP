<script setup lang="ts">
import type { SalesProjectStatus } from '../../../shared/api/salesProjectApi'
import { salesProjectStatusLabel, salesProjectStatusTagType } from './salesProjectPageHelpers'

defineProps<{
  status: SalesProjectStatus
}>()
</script>

<template>
  <el-tag :type="salesProjectStatusTagType(status)" size="small">
    {{ salesProjectStatusLabel(status) }}
  </el-tag>
</template>
