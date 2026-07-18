<script setup lang="ts">
import type { ReceivableStatus } from '../../shared/api/financeApi'
import { receivableStatusTagType, receivableStatusText } from './financePageHelpers'

defineProps<{ status: ReceivableStatus }>()
</script>

<template>
    <el-tag :type="receivableStatusTagType[status] || undefined">
      {{ receivableStatusText[status] }}
    </el-tag>
  </template>
