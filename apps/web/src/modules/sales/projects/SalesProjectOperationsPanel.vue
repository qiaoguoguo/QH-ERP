<script setup lang="ts">
import type { SalesProjectOperation } from '../../../shared/api/salesProjectApi'
import { formatProjectDateTime } from './salesProjectPageHelpers'

defineProps<{
  operations: SalesProjectOperation[]
}>()

type OperationTone = 'primary' | 'success' | 'warning' | 'danger' | 'info'

const targetTypeLabels: Record<string, string> = {
  SALES_PROJECT: '项目',
  SALES_PROJECT_CONTRACT: '项目合同',
  SALES_ORDER: '销售订单',
}

const actionDisplays: Record<string, { label: string, tone: OperationTone }> = {
  SALES_PROJECT_CREATE: { label: '创建', tone: 'primary' },
  SALES_PROJECT_UPDATE: { label: '更新', tone: 'primary' },
  SALES_PROJECT_ACTIVATE: { label: '激活', tone: 'success' },
  SALES_PROJECT_CLOSE: { label: '关闭', tone: 'warning' },
  SALES_PROJECT_CANCEL: { label: '取消', tone: 'danger' },
  SALES_PROJECT_CONTRACT_CREATE: { label: '创建', tone: 'primary' },
  SALES_PROJECT_CONTRACT_UPDATE: { label: '更新', tone: 'primary' },
  SALES_PROJECT_CONTRACT_ACTIVATE: { label: '生效', tone: 'success' },
  SALES_PROJECT_CONTRACT_CLOSE: { label: '关闭', tone: 'warning' },
  SALES_PROJECT_CONTRACT_TERMINATE: { label: '终止', tone: 'danger' },
  SALES_PROJECT_CONTRACT_CANCEL: { label: '取消', tone: 'danger' },
  SALES_ORDER_PROJECT_LINK: { label: '关联项目合同', tone: 'primary' },
  SALES_ORDER_PROJECT_UNLINK: { label: '解除项目合同', tone: 'warning' },
}

function operationObjectLabel(operation: SalesProjectOperation) {
  if (operation.action.startsWith('SALES_ORDER_PROJECT_')) {
    return '销售订单'
  }
  return targetTypeLabels[operation.targetType] ?? '业务对象'
}

function operationActionLabel(action: string) {
  return actionDisplays[action]?.label ?? '其他操作'
}

function operationTone(action: string): OperationTone {
  return actionDisplays[action]?.tone ?? 'info'
}

function normalizeOperationText(value: string) {
  return value.trim().replace(/[\s｜|/\\:：,，、.。;；\-—_]+/g, '')
}

function shouldShowSummary(operation: SalesProjectOperation) {
  const summary = operation.targetSummary.trim()
  const objectLabel = operationObjectLabel(operation)
  const actionLabel = operationActionLabel(operation.action)
  const normalizedSummary = normalizeOperationText(summary)
  const duplicateSummaries = [
    actionLabel,
    `${objectLabel}${actionLabel}`,
    ...(operation.targetType === 'SALES_PROJECT_CONTRACT' ? [`合同${actionLabel}`] : []),
  ].map(normalizeOperationText)

  return Boolean(summary) && !duplicateSummaries.includes(normalizedSummary)
}

function parseProjectContractLink(value: string) {
  if (value === '未关联') {
    return '无'
  }
  const parts = value.split('/')
  if (parts.length !== 2 || !parts[0]?.trim() || !parts[1]?.trim()) {
    return null
  }
  return `项目 ${parts[0].trim()}，合同 ${parts[1].trim()}`
}

function formatOrderProjectLinkSummary(operation: SalesProjectOperation) {
  if (operation.action !== 'SALES_ORDER_PROJECT_LINK' && operation.action !== 'SALES_ORDER_PROJECT_UNLINK') {
    return operation.targetSummary
  }
  const matched = operation.targetSummary.match(/^订单\s+(.+?)\s+项目合同关联\s+(.+?)\s*->\s*(.+)$/)
  if (!matched) {
    return operation.targetSummary
  }
  const [, orderNo, oldLink, newLink] = matched
  const oldText = parseProjectContractLink(oldLink.trim())
  const newText = parseProjectContractLink(newLink.trim())
  if (!oldText || !newText) {
    return operation.targetSummary
  }
  return `订单 ${orderNo.trim()}：原关联：${oldText}；新关联：${newText}`
}
</script>

<template>
  <section class="section-block">
    <div class="section-title">项目操作记录</div>
    <el-empty v-if="operations.length === 0" description="暂无操作记录" />
    <el-timeline v-else class="project-operation-timeline" data-test="project-operation-timeline">
      <el-timeline-item
        v-for="operation in operations"
        :key="`${operation.action}-${operation.targetType}-${operation.targetId}-${operation.createdAt}`"
        :type="operationTone(operation.action)"
        class="project-operation-item"
        data-test="project-operation-item"
      >
        <article class="project-operation-content">
          <div class="project-operation-main">
            <div class="project-operation-heading">
              <span class="project-operation-object" data-test="project-operation-object">{{ operationObjectLabel(operation) }}</span>
              <strong class="project-operation-action" data-test="project-operation-action">{{ operationActionLabel(operation.action) }}</strong>
            </div>
            <span class="project-operation-meta" data-test="project-operation-meta">
              {{ formatProjectDateTime(operation.createdAt) }} · {{ operation.operatorUsername }}
            </span>
          </div>
          <p v-if="shouldShowSummary(operation)" class="project-operation-summary" data-test="project-operation-summary">{{ formatOrderProjectLinkSummary(operation) }}</p>
        </article>
      </el-timeline-item>
    </el-timeline>
  </section>
</template>

<style scoped>
.project-operation-timeline {
  margin: 0;
  padding-left: 4px;
}

.project-operation-item {
  min-width: 0;
}

.project-operation-content {
  min-width: 0;
  padding-bottom: 6px;
}

.project-operation-main {
  align-items: flex-start;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  min-width: 0;
}

.project-operation-heading {
  align-items: baseline;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.project-operation-object {
  color: var(--qherp-muted);
  font-size: 13px;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.project-operation-action {
  color: var(--qherp-text);
  font-size: 14px;
  line-height: 1.4;
  min-width: 0;
  overflow-wrap: anywhere;
}

.project-operation-meta {
  color: var(--qherp-stone);
  flex: 0 1 auto;
  font-size: 12px;
  line-height: 1.4;
  min-width: 0;
  overflow-wrap: anywhere;
  text-align: right;
}

.project-operation-summary {
  color: var(--qherp-text);
  line-height: 1.6;
  margin: 8px 0;
  min-width: 0;
  overflow-wrap: anywhere;
}

@media (max-width: 390px) {
  .project-operation-main {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .project-operation-meta {
    text-align: left;
  }
}
</style>
