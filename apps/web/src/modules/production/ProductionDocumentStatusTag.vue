<script setup lang="ts">
import { productionDocumentStatusLabel, productionDocumentStatusType } from './productionPageHelpers'

defineProps<{
  status: string
}>()
</script>

<template>
  <el-tag :type="productionDocumentStatusType(status)" size="small">
    {{ productionDocumentStatusLabel(status) }}
  </el-tag>
</template>
