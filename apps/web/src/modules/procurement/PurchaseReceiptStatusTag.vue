<script setup lang="ts">
import type { PurchaseReceiptStatus } from '../../shared/api/procurementApi'
import { purchaseReceiptStatusLabel, purchaseReceiptStatusTagType } from './procurementPageHelpers'

defineProps<{
  status: PurchaseReceiptStatus
}>()
</script>

<template>
  <el-tag :type="purchaseReceiptStatusTagType(status)" size="small">
    {{ purchaseReceiptStatusLabel(status) }}
  </el-tag>
</template>
