<script setup lang="ts">
import type { PayableStatus } from '../../shared/api/financeApi'
import { payableStatusLabel, payableStatusType } from './financePageHelpers'

defineProps<{ status: PayableStatus | string | null | undefined }>()
</script>

<template>
    <el-tag :type="payableStatusType(status)">
      {{ payableStatusLabel(status) }}
    </el-tag>
  </template>
