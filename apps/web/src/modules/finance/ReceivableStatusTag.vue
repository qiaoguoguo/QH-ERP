<script setup lang="ts">
import type { ReceivableStatus } from '../../shared/api/financeApi'
import { receivableStatusLabel, receivableStatusType } from './financePageHelpers'

defineProps<{ status: ReceivableStatus | string | null | undefined }>()
</script>

<template>
    <el-tag :type="receivableStatusType(status)">
      {{ receivableStatusLabel(status) }}
    </el-tag>
  </template>
