<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { inventoryApi, type InventoryDocumentDetailRecord, type ResourceId } from '../../shared/api/inventoryApi'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { errorMessage } from '../system/shared/pageHelpers'
import InventoryStatusTag from './InventoryStatusTag.vue'
import { adjustmentDirectionLabel, documentTypeLabel, formatQuantity } from './inventoryPageHelpers'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<InventoryDocumentDetailRecord | null>(null)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const canEdit = computed(() => (
  record.value?.status === 'DRAFT' && authStore.hasPermission('inventory:document:update')
))
const canPost = computed(() => (
  record.value?.status === 'DRAFT' && authStore.hasPermission('inventory:document:post')
))

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await inventoryApi.documents.get(route.params.id as ResourceId)
  } catch (caught) {
    record.value = null
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

function editRecord() {
  if (!record.value) {
    return
  }
  void router.push({ name: 'inventory-document-edit', params: { id: String(record.value.id) } })
}

async function postRecord() {
  if (!record.value || actionLoading.value) {
    return
  }
  if (!window.confirm(`确认过账库存单据“${record.value.documentNo}”？过账会影响库存余额且不可撤销。`)) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    record.value = await inventoryApi.documents.post(record.value.id)
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

onMounted(loadRecord)
</script>

<template>
  <MasterDataTableView title="库存单据详情" description="查看库存单据主表、明细和过账结果。">
    <template #actions>
      <el-button v-if="canEdit" data-test="edit-inventory-document-detail" type="primary" @click="editRecord">
        编辑
      </el-button>
      <el-button
        v-if="canPost"
        data-test="post-inventory-document-detail"
        type="success"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="postRecord"
      >
        过账
      </el-button>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="库存单据详情加载中" :closable="false" />
      <el-alert
        v-if="record?.status === 'POSTED'"
        class="state-alert"
        type="success"
        title="已过账，不可编辑"
        :closable="false"
      />
    </template>

    <div v-if="record" class="inventory-detail">
      <dl class="inventory-detail-list">
        <dt>单据编号</dt>
        <dd>{{ record.documentNo }}</dd>
        <dt>类型</dt>
        <dd>{{ documentTypeLabel(record.documentType) }}</dd>
        <dt>状态</dt>
        <dd><InventoryStatusTag :status="record.status" /></dd>
        <dt>业务日期</dt>
        <dd>{{ record.businessDate }}</dd>
        <dt>原因</dt>
        <dd>{{ record.reason }}</dd>
        <dt>创建人</dt>
        <dd>{{ record.createdByName }}</dd>
        <dt>创建时间</dt>
        <dd>{{ formatDateTime(record.createdAt) }}</dd>
        <dt>过账人</dt>
        <dd>{{ record.postedByName || '-' }}</dd>
        <dt>过账时间</dt>
        <dd>{{ formatDateTime(record.postedAt) }}</dd>
        <dt>备注</dt>
        <dd>{{ record.remark || '未填写' }}</dd>
      </dl>

      <div class="table-scroll">
        <el-table :data="record.lines" empty-text="暂无库存明细" stripe>
          <el-table-column prop="lineNo" label="行号" width="78" />
          <el-table-column prop="warehouseName" label="仓库" min-width="150" show-overflow-tooltip />
          <el-table-column label="物料" min-width="190" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.materialCode }} {{ row.materialName }}
            </template>
          </el-table-column>
          <el-table-column prop="unitName" label="单位" min-width="90" />
          <el-table-column label="调整方向" min-width="100">
            <template #default="{ row }">
              {{ adjustmentDirectionLabel(row.adjustmentDirection) }}
            </template>
          </el-table-column>
          <el-table-column label="数量" min-width="110" align="right">
            <template #default="{ row }">
              <span class="numeric-cell">{{ formatQuantity(row.quantity) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="变动前" min-width="110" align="right">
            <template #default="{ row }">
              <span class="numeric-cell">{{ formatQuantity(row.beforeQuantity) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="变动后" min-width="110" align="right">
            <template #default="{ row }">
              <span class="numeric-cell">{{ formatQuantity(row.afterQuantity) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        </el-table>
      </div>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.inventory-detail {
  padding: 14px;
}

.inventory-detail-list {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) 96px minmax(0, 1fr);
  gap: 10px 14px;
  margin: 0 0 16px;
}

.inventory-detail-list dt {
  color: var(--qherp-muted);
}

.inventory-detail-list dd {
  min-width: 0;
  margin: 0;
  word-break: break-word;
}

.numeric-cell {
  display: inline-block;
  min-width: 72px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 760px) {
  .inventory-detail-list {
    grid-template-columns: 88px minmax(0, 1fr);
  }
}
</style>
