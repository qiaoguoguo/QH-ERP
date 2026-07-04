<script setup lang="ts">
import type { SalesOrderStatus } from '../../shared/api/salesApi'
import { salesOrderStatusLabel, salesOrderStatusTagType } from './salesPageHelpers'

defineProps<{
  status: SalesOrderStatus
}>()
</script>

<template>
  <el-tag :type="salesOrderStatusTagType(status)" size="small">
    {{ salesOrderStatusLabel(status) }}
  </el-tag>
</template>
