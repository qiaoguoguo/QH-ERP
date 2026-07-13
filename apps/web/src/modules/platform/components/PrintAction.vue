<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type PrintPreviewRecord,
  type PrintTemplateRecord,
  type ResourceId,
} from '../../../shared/api/documentPlatformApi'
import { platformErrorMessage } from '../platformPageHelpers'

const props = defineProps<{
  sceneCode: string
  approvalInstanceId?: ResourceId | null
  title: string
}>()

const templates = ref<PrintTemplateRecord[]>([])
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const latestTaskNo = ref('')
const preview = ref<PrintPreviewRecord | null>(null)
const previewTemplate = ref<PrintTemplateRecord | null>(null)
const previewVisible = ref(false)
const previewedTemplateCode = ref('')

async function loadTemplates() {
  loading.value = true
  error.value = ''
  preview.value = null
  previewTemplate.value = null
  previewedTemplateCode.value = ''
  try {
    templates.value = await documentPlatformApi.printTemplates.list({ sceneCode: props.sceneCode })
  } catch (caught) {
    templates.value = []
    error.value = platformErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function previewPrint(template: PrintTemplateRecord) {
  if (!props.approvalInstanceId || actionLoading.value) {
    return
  }
  actionLoading.value = true
  error.value = ''
  try {
    preview.value = await documentPlatformApi.printPreviews.get(props.approvalInstanceId)
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
  if (!props.approvalInstanceId || actionLoading.value) {
    return
  }
  if (previewedTemplateCode.value !== template.templateCode) {
    error.value = '请先预览审批单，再生成 PDF 任务'
    return
  }
  actionLoading.value = true
  error.value = ''
  try {
    const task = await documentPlatformApi.printTasks.create({
      approvalInstanceId: props.approvalInstanceId,
      templateCode: template.templateCode,
      idempotencyKey: createIdempotencyKey('print'),
    })
    latestTaskNo.value = task.taskNo
  } catch (caught) {
    error.value = platformErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

watch(() => [props.sceneCode, props.approvalInstanceId], () => {
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
    <div class="print-template-actions">
      <template v-for="template in templates" :key="template.templateCode">
        <el-button
          data-test="preview-print"
          :loading="actionLoading"
          :disabled="!approvalInstanceId || actionLoading"
          @click="previewPrint(template)"
        >
          预览 {{ template.name }}
        </el-button>
        <el-button
          data-test="create-print-task"
          type="primary"
          :loading="actionLoading"
          :disabled="!approvalInstanceId || actionLoading || previewedTemplateCode !== template.templateCode"
          @click="createPrintTask(template)"
        >
          生成 PDF
        </el-button>
      </template>
      <span v-if="!templates.length && !loading">暂无固定模板</span>
    </div>
    <el-drawer v-model="previewVisible" title="审批单预览" size="min(640px, 92vw)" :teleported="false">
      <template v-if="preview">
        <section class="print-preview-section">
          <dl class="platform-panel-list">
            <dt>模板：</dt>
            <dd>{{ previewTemplate?.name || '-' }}</dd>
            <dt>模板代码：</dt>
            <dd>{{ previewTemplate?.sceneCode || sceneCode }}</dd>
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
