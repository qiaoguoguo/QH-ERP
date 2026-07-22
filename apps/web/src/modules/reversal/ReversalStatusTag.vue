<script setup lang="ts">
import type { ReversalStatus } from '../../shared/api/returnRefundReversalApi'
import { reversalDocumentStatusLabel, reversalDocumentStatusType } from './reversalPageHelpers'

defineProps<{
  status: ReversalStatus
}>()

</script>

<template>
  <el-tag :type="reversalDocumentStatusType(status)" size="small">
    {{ reversalDocumentStatusLabel(status) }}
  </el-tag>
</template>
