<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type PrintTemplateRecord,
  type ResourceId,
} from '../../../shared/api/documentPlatformApi'
import { platformErrorMessage } from '../platformPageHelpers'

const props = defineProps<{
  objectType: string
  approvalInstanceId?: ResourceId | null
  title: string
}>()

const templates = ref<PrintTemplateRecord[]>([])
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const latestTaskNo = ref('')

async function loadTemplates() {
  loading.value = true
  error.value = ''
  try {
    templates.value = await documentPlatformApi.printTemplates.list({ objectType: props.objectType })
  } catch (caught) {
    templates.value = []
    error.value = platformErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function createPrintTask(template: PrintTemplateRecord) {
  if (!props.approvalInstanceId || actionLoading.value) {
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

watch(() => props.objectType, () => {
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
      <el-button
        v-for="template in templates"
        :key="template.templateCode"
        data-test="create-print-task"
        :loading="actionLoading"
        :disabled="!approvalInstanceId || actionLoading || !template.enabled"
        @click="createPrintTask(template)"
      >
        {{ template.templateName }}
      </el-button>
      <span v-if="!templates.length && !loading">暂无固定模板</span>
    </div>
  </section>
</template>
