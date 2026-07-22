<script setup lang="ts">
import type { PaymentStatus } from '../../shared/api/financeApi'
import { paymentStatusLabel, paymentStatusType } from './financePageHelpers'

defineProps<{ status: PaymentStatus | string | null | undefined }>()
</script>

<template>
  <el-tag :type="paymentStatusType(status)">
    {{ paymentStatusLabel(status) }}
  </el-tag>
</template>
