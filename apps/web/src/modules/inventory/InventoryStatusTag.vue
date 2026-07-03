<script setup lang="ts">
import type { InventoryDocumentStatus } from '../../shared/api/inventoryApi'
import { documentStatusLabel, documentStatusTagType } from './inventoryPageHelpers'

defineProps<{
  status: InventoryDocumentStatus
}>()
</script>

<template>
  <el-tag :type="documentStatusTagType(status)" size="small">
    {{ documentStatusLabel(status) }}
  </el-tag>
</template>
