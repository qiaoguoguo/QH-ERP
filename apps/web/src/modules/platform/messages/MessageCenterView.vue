<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { documentPlatformApi, type MessageRecord } from '../../../shared/api/documentPlatformApi'
import { pageItems } from '../../system/shared/pageHelpers'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { formatPlatformDateTime, messageStatusLabel, platformErrorMessage } from '../platformPageHelpers'

const filters = reactive({ unreadOnly: false, keyword: '' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<MessageRecord[]>([])
const unreadCount = ref(0)
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await documentPlatformApi.messages.listMine({
      unreadOnly: filters.unreadOnly,
      keyword: filters.keyword,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = pageItems(page)
    pagination.total = Number(page.total)
    unreadCount.value = Number(page.unreadCount ?? records.value.filter((item) => item.status === 'UNREAD').length)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = platformErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function toggleUnread() {
  filters.unreadOnly = !filters.unreadOnly
  pagination.page = 1
  void loadRecords()
}

function search() {
  pagination.page = 1
  void loadRecords()
}

async function markRead(record: MessageRecord) {
  actionLoading.value = true
  actionError.value = ''
  try {
    await documentPlatformApi.messages.markRead(record.id, { version: record.version })
    await loadRecords()
  } catch (caught) {
    actionError.value = platformErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function whitelistedBusinessRoute(record: MessageRecord): string | null {
  if (!record.relatedObjectType || record.relatedObjectId === null || record.relatedObjectId === undefined) {
    return null
  }
  const objectId = encodeURIComponent(String(record.relatedObjectId))
  if (record.relatedObjectType === 'SALES_PROJECT_CONTRACT') {
    return `/sales/projects?contractId=${objectId}`
  }
  if (record.relatedObjectType === 'BOM_ENGINEERING_CHANGE') {
    return `/materials/boms?ecoId=${objectId}`
  }
  if (record.relatedObjectType === 'DOCUMENT_TASK') {
    return `/platform/document-tasks?taskId=${objectId}`
  }
  if (record.relatedObjectType === 'DATA_REPAIR_REQUEST') {
    return `/platform/data-repairs/${objectId}?returnTo=${encodeURIComponent('/platform/messages')}`
  }
  if (record.relatedObjectType === 'HISTORY_IMPORT_TASK') {
    return `/platform/history-imports/${objectId}?returnTo=${encodeURIComponent('/platform/messages')}`
  }
  if (record.relatedObjectType === 'BATCH_OPERATION') {
    return `/platform/document-tasks?batchOperationId=${objectId}&returnTo=${encodeURIComponent('/platform/messages')}`
  }
  return null
}

async function markAllRead() {
  actionLoading.value = true
  actionError.value = ''
  try {
    await documentPlatformApi.messages.markAllRead()
    await loadRecords()
  } catch (caught) {
    actionError.value = platformErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function changePage(page: number) {
  pagination.page = page
  void loadRecords()
}

function changePageSize(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1
  void loadRecords()
}

onMounted(() => {
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="消息中心" description="查看审批和文档任务消息。">
    <template #actions>
      <el-button data-test="mark-all-messages-read" :loading="actionLoading" :disabled="actionLoading" @click="markAllRead">
        全部已读
      </el-button>
    </template>
    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="未读">
          <el-badge :value="unreadCount" :hidden="unreadCount === 0">
            <el-button data-test="filter-unread-messages" :type="filters.unreadOnly ? 'primary' : 'default'" @click="toggleUnread">
              未读 {{ unreadCount }}
            </el-button>
          </el-badge>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="message-keyword" clearable placeholder="标题或内容" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="操作">
          <el-button data-test="search-messages" type="primary" @click="search">查询</el-button>
        </el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="消息加载中" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无消息'" stripe>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'UNREAD' ? 'warning' : 'info'" size="small">{{ messageStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ formatPlatformDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="200">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'UNREAD'"
              data-test="mark-message-read"
              size="small"
              text
              :disabled="actionLoading"
              @click="markRead(row)"
            >
              标记已读
            </el-button>
            <RouterLink v-if="whitelistedBusinessRoute(row)" :to="whitelistedBusinessRoute(row) || ''">查看业务</RouterLink>
            <span v-else>业务受限</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination
      class="table-pagination"
      layout="total, sizes, prev, pager, next"
      :page-sizes="[10, 20, 50, 100]"
      :total="pagination.total"
      :page-size="pagination.pageSize"
      :current-page="pagination.page"
      @current-change="changePage"
      @size-change="changePageSize"
    />
  </MasterDataTableView>
</template>
