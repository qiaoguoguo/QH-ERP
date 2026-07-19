<script setup lang="ts">
import { periodCloseSeverityLabel, periodCloseSeverityTagType } from './periodClosePageHelpers'

const props = defineProps<{
  severity?: string | null
}>()
</script>

<template>
  <el-tag :type="periodCloseSeverityTagType(props.severity)" effect="plain">
    {{ periodCloseSeverityLabel(props.severity) }}
  </el-tag>
</template>
