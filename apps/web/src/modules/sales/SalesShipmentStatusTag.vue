<script setup lang="ts">
import type { SalesShipmentStatus } from '../../shared/api/salesApi'
import { salesShipmentStatusLabel, salesShipmentStatusTagType } from './salesPageHelpers'

defineProps<{
  status: SalesShipmentStatus
}>()
</script>

<template>
  <el-tag :type="salesShipmentStatusTagType(status)" size="small">
    {{ salesShipmentStatusLabel(status) }}
  </el-tag>
</template>
