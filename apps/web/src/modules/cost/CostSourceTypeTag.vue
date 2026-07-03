<script setup lang="ts">
import type { CostSourceType } from '../../shared/api/costCollectionApi'
import { costSourceTypeLabel, costSourceTypeTagType } from './costPageHelpers'

defineProps<{
  type: CostSourceType
}>()
</script>

<template>
  <el-tag size="small" :type="costSourceTypeTagType(type)" effect="light">
    {{ costSourceTypeLabel(type) }}
  </el-tag>
</template>
