<script setup lang="ts">
import { periodCloseStatusLabel, periodCloseStatusTagType } from './periodClosePageHelpers'

const props = defineProps<{
  status?: string | null
  label?: string | null
}>()
</script>

<template>
  <el-tag :type="periodCloseStatusTagType(props.status)" effect="plain">
    {{ props.label || periodCloseStatusLabel(props.status) }}
  </el-tag>
</template>
