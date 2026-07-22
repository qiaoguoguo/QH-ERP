<script setup lang="ts">
import type { ReceiptStatus } from '../../shared/api/financeApi'
import { receiptStatusLabel, receiptStatusType } from './financePageHelpers'

defineProps<{ status: ReceiptStatus | string | null | undefined }>()
</script>

<template>
  <el-tag :type="receiptStatusType(status)">
    {{ receiptStatusLabel(status) }}
  </el-tag>
</template>
