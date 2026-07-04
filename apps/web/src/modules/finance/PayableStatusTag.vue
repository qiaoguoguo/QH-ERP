<script setup lang="ts">
import type { PayableStatus } from '../../shared/api/financeApi'
import { payableStatusTagType, payableStatusText } from './financePageHelpers'

defineProps<{ status: PayableStatus }>()
</script>

<template>
  <el-tag :type="payableStatusTagType[status]">
    {{ payableStatusText[status] }}
  </el-tag>
</template>
