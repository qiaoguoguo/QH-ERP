<script setup lang="ts">
import type { PaymentStatus } from '../../shared/api/financeApi'
import { paymentStatusTagType, paymentStatusText } from './financePageHelpers'

defineProps<{ status: PaymentStatus }>()
</script>

<template>
  <el-tag :type="paymentStatusTagType[status]">
    {{ paymentStatusText[status] }}
  </el-tag>
</template>
