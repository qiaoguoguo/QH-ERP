<script setup lang="ts">
import type { ApprovalInstanceStatus, ResourceId } from '../../../shared/api/documentPlatformApi'
import { approvalStatusLabel, approvalStatusTagType, formatPlatformDateTime } from '../platformPageHelpers'

defineProps<{
  approvalInstanceId?: ResourceId | null
  approvalStatus?: ApprovalInstanceStatus | string | null
  submittedAt?: string | null
}>()
</script>

<template>
  <section class="platform-panel">
    <h3>审批状态</h3>
    <dl class="platform-panel-list">
      <dt>当前状态</dt>
      <dd>
        <el-tag v-if="approvalStatus" :type="approvalStatusTagType(String(approvalStatus))" size="small">
          {{ approvalStatusLabel(String(approvalStatus)) }}
        </el-tag>
        <span v-else>历史对象，无 022 审批记录</span>
      </dd>
      <dt>审批实例</dt>
      <dd>{{ approvalInstanceId ?? '-' }}</dd>
      <dt>提交时间</dt>
      <dd>{{ formatPlatformDateTime(submittedAt) }}</dd>
    </dl>
  </section>
</template>
