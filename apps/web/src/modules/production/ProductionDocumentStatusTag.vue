<script setup lang="ts">
import type { ProductionDocumentStatus } from '../../shared/api/productionApi'
import { productionDocumentStatusLabel, productionDocumentStatusType } from './productionPageHelpers'

defineProps<{
  status: ProductionDocumentStatus
}>()
</script>

<template>
  <el-tag :type="productionDocumentStatusType(status)" size="small">
    {{ productionDocumentStatusLabel(status) }}
  </el-tag>
</template>
