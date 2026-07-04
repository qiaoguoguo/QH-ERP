<script setup lang="ts">
import type { ReceiptStatus } from '../../shared/api/financeApi'
import { receiptStatusTagType, receiptStatusText } from './financePageHelpers'

defineProps<{ status: ReceiptStatus }>()
</script>

<template>
  <el-tag :type="receiptStatusTagType[status]">
    {{ receiptStatusText[status] }}
  </el-tag>
</template>
