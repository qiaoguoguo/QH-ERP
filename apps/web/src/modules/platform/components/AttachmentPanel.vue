<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type AttachmentObjectType,
  type AttachmentRecord,
  type ResourceId,
} from '../../../shared/api/documentPlatformApi'
import { downloadFile } from '../../../shared/file/download'
import { platformErrorMessage, formatPlatformDateTime } from '../platformPageHelpers'

const props = withDefaults(defineProps<{
  objectType: AttachmentObjectType
  objectId?: ResourceId | null
  title?: string
  readonly?: boolean
}>(), {
  title: '附件',
  objectId: null,
  readonly: false,
})

const records = ref<AttachmentRecord[]>([])
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const selectedFile = ref<File | null>(null)
const description = ref('')
const latestTaskText = ref('')

async function loadRecords() {
  if (props.objectId === null || props.objectId === undefined) {
    records.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    records.value = await documentPlatformApi.attachments.list({
      objectType: props.objectType,
      objectId: props.objectId,
    })
  } catch (caught) {
    records.value = []
    error.value = platformErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function selectFile(event: Event) {
  const input = event.target as HTMLInputElement
  selectedFile.value = input.files?.[0] ?? null
}

async function uploadFile() {
  if (!props.objectId || !selectedFile.value || actionLoading.value) {
    return
  }
  actionLoading.value = true
  error.value = ''
  try {
    const attachment = await documentPlatformApi.attachments.upload({
      objectType: props.objectType,
      objectId: props.objectId,
      file: selectedFile.value,
      description: description.value.trim(),
      idempotencyKey: createIdempotencyKey('attachment'),
    })
    latestTaskText.value = `已上传 ${attachment.fileName}`
    selectedFile.value = null
    description.value = ''
    await loadRecords()
  } catch (caught) {
    error.value = platformErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function downloadAttachment(record: AttachmentRecord) {
  actionLoading.value = true
  error.value = ''
  try {
    downloadFile(await documentPlatformApi.attachments.download(record.id))
  } catch (caught) {
    error.value = platformErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function deleteAttachment(record: AttachmentRecord) {
  actionLoading.value = true
  error.value = ''
  try {
    await documentPlatformApi.attachments.delete(record.id, {
      version: record.version,
      reason: '用户删除附件',
    })
    await loadRecords()
  } catch (caught) {
    error.value = platformErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

watch(() => [props.objectType, props.objectId], () => {
  void loadRecords()
})

onMounted(() => {
  void loadRecords()
})
</script>

<template>
  <section class="platform-panel">
    <h3>{{ title }}</h3>
    <el-alert v-if="error" class="form-alert" type="error" :title="error" :closable="false" />
    <el-alert v-if="latestTaskText" class="form-alert" type="success" :title="latestTaskText" :closable="false" />
    <div v-if="!readonly" class="attachment-upload-row">
      <input data-test="attachment-file" type="file" @change="selectFile">
      <el-input v-model="description" placeholder="附件说明" />
      <el-button data-test="upload-attachment" :loading="actionLoading" :disabled="!selectedFile || actionLoading" @click="uploadFile">
        上传附件
      </el-button>
    </div>
    <el-table :data="records" :empty-text="loading ? '加载中' : '暂无附件'" stripe>
      <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
      <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
      <el-table-column label="上传时间" width="160">
        <template #default="{ row }">{{ formatPlatformDateTime(row.uploadedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="160">
        <template #default="{ row }">
          <el-button v-if="row.canDownload !== false" data-test="download-attachment" size="small" text @click="downloadAttachment(row)">下载</el-button>
          <el-button v-if="!readonly && row.canDelete !== false" data-test="delete-attachment" size="small" text type="danger" @click="deleteAttachment(row)">删除</el-button>
          <el-tag v-if="row.restricted" type="warning" size="small">{{ row.restrictedMessage || '受限' }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

