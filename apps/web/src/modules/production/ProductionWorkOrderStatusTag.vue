<script setup lang="ts">
import type { ProductionWorkOrderStatus } from '../../shared/api/productionApi'
import { workOrderStatusLabel, workOrderStatusType } from './productionPageHelpers'

defineProps<{
  status: ProductionWorkOrderStatus
}>()
</script>

<template>
  <el-tag :type="workOrderStatusType(status)" size="small">
    {{ workOrderStatusLabel(status) }}
  </el-tag>
</template>
