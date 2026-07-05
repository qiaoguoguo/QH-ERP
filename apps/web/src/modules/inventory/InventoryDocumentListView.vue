<script setup lang="ts">
import { computed, reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  inventoryApi,
  type InventoryDocumentStatus,
  type InventoryDocumentSummaryRecord,
  type InventoryDocumentType,
} from '../../shared/api/inventoryApi'
import { currentRouteReturnTo, queryWithReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { errorMessage, pageItems } from '../system/shared/pageHelpers'
import InventoryStatusTag from './InventoryStatusTag.vue'
import { documentTypeLabel } from './inventoryPageHelpers'
import { confirmAction } from '../../shared/ui/confirmDialog'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const filters = reactive<{
  keyword: string
  documentType?: InventoryDocumentType
  status?: InventoryDocumentStatus
  dateFrom: string
  dateTo: string
}>({
  keyword: '',
  documentType: undefined,
  status: undefined,
  dateFrom: '',
  dateTo: '',
})
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
})
const records = ref<InventoryDocumentSummaryRecord[]>([])
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const canCreate = computed(() => authStore.hasPermission('inventory:document:create'))
const canUpdate = computed(() => authStore.hasPermission('inventory:document:update'))
const canPost = computed(() => authStore.hasPermission('inventory:document:post'))

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await inventoryApi.documents.list({
      keyword: filters.keyword,
      documentType: filters.documentType,
      status: filters.status,
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = pageItems(page)
    pagination.total = Number(page.total)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

function search() {
  pagination.page = 1
  void loadRecords()
}

function resetSearch() {
  filters.keyword = ''
  filters.documentType = undefined
  filters.status = undefined
  filters.dateFrom = ''
  filters.dateTo = ''
  pagination.page = 1
  void loadRecords()
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

function createDocument(type: InventoryDocumentType) {
  void router.push({ name: 'inventory-document-create', query: { type } })
}

function viewDocument(record: InventoryDocumentSummaryRecord) {
  void router.push({
    name: 'inventory-document-detail',
    params: { id: String(record.id) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function editDocument(record: InventoryDocumentSummaryRecord) {
  void router.push({ name: 'inventory-document-edit', params: { id: String(record.id) } })
}

async function postDocument(record: InventoryDocumentSummaryRecord) {
  if (actionLoading.value) {
    return
  }
  if (!(await confirmAction(`确认过账库存单据“${record.documentNo}”？过账会影响库存余额且不可撤销。`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await inventoryApi.documents.post(record.id)
    await loadRecords()
  } catch (caught) {
    actionError.value = errorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 16)
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="库存单据" description="维护期初库存和库存调整草稿，并在确认后过账影响库存余额。">
    <template #actions>
      <el-button
        v-if="canCreate"
        data-test="create-opening-document"
        type="primary"
        @click="createDocument('OPENING')"
      >
        新增期初
      </el-button>
      <el-button
        v-if="canCreate"
        data-test="create-adjustment-document"
        type="primary"
        plain
        @click="createDocument('ADJUSTMENT')"
      >
        新增调整
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            name="inventory-document-keyword"
            clearable
            placeholder="单据编号或原因"
          />
        </el-form-item>
        <el-form-item label="单据类型">
          <el-select
            v-model="filters.documentType"
            data-test="inventory-document-type-filter"
            clearable
            placeholder="全部类型"
          >
            <el-option label="期初库存" value="OPENING" />
            <el-option label="库存调整" value="ADJUSTMENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="filters.status"
            data-test="inventory-document-status-filter"
            clearable
            placeholder="全部状态"
          >
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已过账" value="POSTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD"
            v-model="filters.dateFrom"
            name="inventory-document-date-from"
            placeholder="起始日期"
          />
        </el-form-item>
        <el-form-item>
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD"
            v-model="filters.dateTo"
            name="inventory-document-date-to"
            placeholder="截止日期"
          />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-inventory-documents" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-inventory-documents" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="库存单据加载中" :closable="false" />
    </template>

    <el-empty v-if="!loading && records.length === 0" description="暂无库存单据" />
    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无库存单据'" stripe>
        <el-table-column prop="documentNo" label="单据编号" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" min-width="110">
          <template #default="{ row }">
            {{ documentTypeLabel(row.documentType) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="90">
          <template #default="{ row }">
            <InventoryStatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="businessDate" label="业务日期" min-width="110" />
        <el-table-column prop="lineCount" label="明细数" min-width="90" />
        <el-table-column prop="createdByName" label="创建人" min-width="110" />
        <el-table-column prop="postedByName" label="过账人" min-width="110">
          <template #default="{ row }">
            {{ row.postedByName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="150">
          <template #default="{ row }">
            {{ formatDateTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="210">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-inventory-document" @click="viewDocument(row)">详情</el-button>
            <el-button
              v-if="canUpdate && row.status === 'DRAFT'"
              size="small"
              text
              data-test="edit-inventory-document"
              @click="editDocument(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="canPost && row.status === 'DRAFT'"
              size="small"
              text
              type="success"
              data-test="post-inventory-document"
              :disabled="actionLoading"
              @click="postDocument(row)"
            >
              过账
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination
      class="table-pagination"
      layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]"
      :total="pagination.total"
      :page-size="pagination.pageSize"
      :current-page="pagination.page"
      @current-change="changePage" @size-change="changePageSize"
    />
  </MasterDataTableView>
</template>
