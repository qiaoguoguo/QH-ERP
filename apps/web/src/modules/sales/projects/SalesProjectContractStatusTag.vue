<script setup lang="ts">
import type { SalesProjectContractStatus } from '../../../shared/api/salesProjectApi'
import { salesProjectContractStatusLabel, salesProjectContractStatusTagType } from './salesProjectPageHelpers'

defineProps<{
  status: SalesProjectContractStatus
}>()
</script>

<template>
  <el-tag :type="salesProjectContractStatusTagType(status)" size="small">
    {{ salesProjectContractStatusLabel(status) }}
  </el-tag>
</template>
