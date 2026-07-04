<script setup lang="ts">
import type { PurchaseOrderStatus } from '../../shared/api/procurementApi'
import { purchaseOrderStatusLabel, purchaseOrderStatusTagType } from './procurementPageHelpers'

defineProps<{
  status: PurchaseOrderStatus
}>()
</script>

<template>
  <el-tag :type="purchaseOrderStatusTagType(status)" size="small">
    {{ purchaseOrderStatusLabel(status) }}
  </el-tag>
</template>
