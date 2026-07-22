<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type PrintPreviewRecord,
  type PrintTemplateRecord,
  type ResourceId,
} from '../../../shared/api/documentPlatformApi'
import { useAuthStore } from '../../../stores/authStore'
import { platformErrorMessage } from '../platformPageHelpers'

const props = defineProps<{
  objectType: string
  objectId?: ResourceId | null
  objectNo?: string | null
  objectStatus?: string | null
  allowedObjectStatuses?: string[]
  title: string
}>()

const authStore = useAuthStore()
const canGeneratePrint = computed(() => authStore.hasPermission('platform:print:generate'))
const isObjectStatusAllowed = computed(() => (
  !props.objectStatus
  || !props.allowedObjectStatuses?.length
  || props.allowedObjectStatuses.includes(props.objectStatus)
))

function objectStatusText(status: string | null | undefined) {
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    SUBMITTED: '已提交',
    APPROVED: '已批准',
    CONFIRMED: '已确认',
    POSTED: '已过账',
    CLOSED: '已关闭',
    CANCELLED: '已取消',
    DISABLED: '已停用',
  }
  const code = String(status ?? '').trim()
  return labels[code] ?? '当前状态'
}

const objectStatusMessage = computed(() => (
  isObjectStatusAllowed.value
    ? ''
    : `对象状态为${objectStatusText(props.objectStatus)}，不允许固定打印`
))
const templates = ref<PrintTemplateRecord[]>([])
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const latestTaskNo = ref('')
const preview = ref<PrintPreviewRecord | null>(null)
const previewTemplate = ref<PrintTemplateRecord | null>(null)
const previewedTemplateCode = ref('')
const previewVisible = ref(false)

async function loadTemplates() {
  if (!canGeneratePrint.value) {
    templates.value = []
    return
  }
  loading.value = true
  error.value = ''
  preview.value = null
  previewTemplate.value = null
  previewedTemplateCode.value = ''
  try {
    templates.value = await documentPlatformApi.printTemplates.list({ objectType: props.objectType })
  } catch (caught) {
    templates.value = []
    error.value = platformErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function previewPrint(template: PrintTemplateRecord) {
  if (!props.objectId || actionLoading.value || !canGeneratePrint.value || !isObjectStatusAllowed.value) {
    return
  }
  actionLoading.value = true
  error.value = ''
  latestTaskNo.value = ''
  try {
    preview.value = await documentPlatformApi.printPreviews.previewObject({
      objectType: props.objectType,
      objectId: props.objectId,
      templateCode: template.templateCode,
    })
    previewTemplate.value = template
    previewedTemplateCode.value = template.templateCode
    previewVisible.value = true
  } catch (caught) {
    error.value = platformErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function createPrintTask(template: PrintTemplateRecord) {
  if (!props.objectId || actionLoading.value || !canGeneratePrint.value || !isObjectStatusAllowed.value) {
    return
  }
  if (previewedTemplateCode.value !== template.templateCode) {
    error.value = '请先预览固定模板，再生成 PDF 任务'
    return
  }
  actionLoading.value = true
  error.value = ''
  try {
    const task = await documentPlatformApi.printTasks.create({
      objectType: props.objectType,
      objectId: props.objectId,
      templateCode: template.templateCode,
      idempotencyKey: createIdempotencyKey('fixed-print'),
    })
    latestTaskNo.value = task.taskNo
  } catch (caught) {
    error.value = platformErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

watch(() => [props.objectType, props.objectId], () => {
  void loadTemplates()
})

onMounted(() => {
  void loadTemplates()
})
</script>

<template>
  <section class="platform-panel">
    <h3>{{ title }}</h3>
    <el-alert v-if="error" class="form-alert" type="error" :title="error" :closable="false" />
    <el-alert v-if="latestTaskNo" class="form-alert" type="success" :title="`已创建打印任务 ${latestTaskNo}`" :closable="false" />
    <el-alert v-if="!canGeneratePrint" class="form-alert" type="info" title="无固定打印权限" :closable="false" />
    <el-alert v-if="objectStatusMessage" class="form-alert" type="warning" :title="objectStatusMessage" :closable="false" />

    <div v-if="canGeneratePrint" class="print-template-actions">
      <template v-for="template in templates" :key="template.templateCode">
        <el-button
          data-test="preview-fixed-print"
          :loading="actionLoading"
          :disabled="!objectId || actionLoading || !isObjectStatusAllowed"
          @click="previewPrint(template)"
        >
          预览 {{ template.name }}
        </el-button>
        <el-button
          data-test="create-fixed-print-task"
          type="primary"
          :loading="actionLoading"
          :disabled="!objectId || actionLoading || !isObjectStatusAllowed || previewedTemplateCode !== template.templateCode"
          @click="createPrintTask(template)"
        >
          生成 PDF
        </el-button>
      </template>
      <span v-if="!templates.length && !loading">暂无固定模板</span>
    </div>

    <el-drawer v-model="previewVisible" title="固定模板预览" size="min(720px, 92vw)" :teleported="false">
      <template v-if="preview">
        <section class="print-preview-section">
          <dl class="platform-panel-list">
            <dt>对象编号：</dt>
            <dd>{{ objectNo || '-' }}</dd>
            <dt>模板：</dt>
            <dd>{{ previewTemplate?.name || '-' }}</dd>
            <dt>模板代码：</dt>
            <dd>{{ previewTemplate?.templateCode || preview.templateCode }}</dd>
            <dt>模板版本：</dt>
            <dd>V{{ previewTemplate?.templateVersion ?? preview.templateVersion }}</dd>
          </dl>
        </section>
        <section v-for="section in preview.sections" :key="section.title" class="print-preview-section">
          <h4>{{ section.title }}</h4>
          <dl class="platform-panel-list">
            <template v-for="field in section.fields" :key="`${section.title}-${field.label}`">
              <dt>{{ field.label }}</dt>
              <dd>{{ field.value || '-' }}</dd>
            </template>
          </dl>
        </section>
      </template>
    </el-drawer>
  </section>
</template>
