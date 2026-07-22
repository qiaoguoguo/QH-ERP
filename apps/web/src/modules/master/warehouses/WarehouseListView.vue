<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { masterDataApi, type MasterDataStatus, type WarehousePayload, type WarehouseRecord } from '../../../shared/api/masterDataApi'
import { useAuthStore } from '../../../stores/authStore'
import { errorMessage, pageItems, statusTagType } from '../../system/shared/pageHelpers'
import MasterDataTableView from '../shared/MasterDataTableView.vue'
import { masterStatusLabel } from '../shared/masterPageHelpers'
import { confirmAction } from '../../../shared/ui/confirmDialog'

const authStore = useAuthStore()

const filters = reactive<{
  keyword: string
  status?: MasterDataStatus
}>({
  keyword: '',
  status: undefined,
})
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
})
const records = ref<WarehouseRecord[]>([])
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const formVisible = ref(false)
const formSubmitting = ref(false)
const formError = ref('')
const editingRecord = ref<WarehouseRecord | null>(null)
const form = reactive({
  code: '',
  name: '',
  warehouseType: '',
  managerName: '',
  address: '',
  status: 'ENABLED' as MasterDataStatus,
  remark: '',
})

const canCreate = computed(() => authStore.hasPermission('master:warehouse:create'))
const canUpdate = computed(() => authStore.hasPermission('master:warehouse:update'))

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await masterDataApi.warehouses.list({
      keyword: filters.keyword,
      status: filters.status,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = pageItems(page)
    pagination.total = Number(page.total)
  } catch (caught) {
    records.value = []
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
  filters.status = undefined
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

function resetForm(record?: WarehouseRecord) {
  Object.assign(form, {
    code: record?.code ?? '',
    name: record?.name ?? '',
    warehouseType: record?.warehouseType ?? '',
    managerName: record?.managerName ?? '',
    address: record?.address ?? '',
    status: record?.status ?? 'ENABLED',
    remark: record?.remark ?? '',
  })
  formError.value = ''
}

function openCreate() {
  editingRecord.value = null
  resetForm()
  formVisible.value = true
}

function openEdit(record: WarehouseRecord) {
  editingRecord.value = record
  resetForm(record)
  formVisible.value = true
}

function validateForm() {
  if (!form.code.trim() || !form.name.trim()) {
    formError.value = '请完整填写编码和名称'
    return false
  }
  formError.value = ''
  return true
}

async function saveRecord() {
  if (formSubmitting.value) {
    return
  }
  if (!validateForm()) {
    return
  }

  const payload: WarehousePayload = {
    code: form.code.trim(),
    name: form.name.trim(),
    warehouseType: form.warehouseType.trim(),
    managerName: form.managerName.trim(),
    address: form.address.trim(),
    status: form.status,
    remark: form.remark.trim(),
  }

  formSubmitting.value = true
  try {
    if (editingRecord.value) {
      await masterDataApi.warehouses.update(editingRecord.value.id, payload)
    } else {
      await masterDataApi.warehouses.create(payload)
    }
    formVisible.value = false
    await loadRecords()
  } catch (caught) {
    formError.value = errorMessage(caught)
  } finally {
    formSubmitting.value = false
  }
}

async function changeStatus(record: WarehouseRecord) {
  const nextAction = record.status === 'DISABLED' ? '启用' : '停用'
  if (!(await confirmAction(`确认${nextAction}仓库“${record.name}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    if (record.status === 'DISABLED') {
      await masterDataApi.warehouses.enable(record.id)
    } else {
      await masterDataApi.warehouses.disable(record.id)
    }
    await loadRecords()
  } catch (caught) {
    actionError.value = errorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="仓库" description="维护生产领料、完工入库和库存核算使用的仓库资料。">
    <template #actions>
      <el-button v-if="canCreate" data-test="create-record" type="primary" @click="openCreate">新增仓库</el-button>
    </template>

    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="record-keyword" clearable placeholder="编码或名称" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-record" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-record" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="仓库数据加载中" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无仓库数据'" stripe>
        <el-table-column prop="code" label="编码" min-width="140" show-overflow-tooltip />
        <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="warehouseType" label="类型" min-width="120" show-overflow-tooltip />
        <el-table-column prop="managerName" label="负责人" min-width="120" show-overflow-tooltip />
        <el-table-column prop="address" label="地址" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" min-width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ masterStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" fixed="right" min-width="160">
          <template #default="{ row }">
            <el-button v-if="canUpdate" size="small" text data-test="edit-record" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="canUpdate"
              size="small"
              text
              :disabled="actionLoading"
              :type="row.status === 'DISABLED' ? 'success' : 'warning'"
              :data-test="row.status === 'DISABLED' ? 'enable-record' : 'disable-record'"
              @click="changeStatus(row)"
            >
              {{ row.status === 'DISABLED' ? '启用' : '停用' }}
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

    <el-dialog v-model="formVisible" :title="editingRecord ? '编辑仓库' : '新增仓库'" width="560px">
      <el-alert v-if="formError" class="form-alert" type="error" :title="formError" :closable="false" />
      <el-form label-position="top">
        <el-form-item label="仓库编码">
          <el-input v-model="form.code" name="record-code" :disabled="Boolean(editingRecord)" />
        </el-form-item>
        <el-form-item label="仓库名称">
          <el-input v-model="form.name" name="record-name" />
        </el-form-item>
        <el-form-item label="仓库类型">
          <el-input v-model="form.warehouseType" name="record-warehouse-type" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.managerName" name="record-manager-name" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" name="record-address" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" name="record-remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button
          data-test="submit-record"
          type="primary"
          :loading="formSubmitting"
          :disabled="formSubmitting"
          @click="saveRecord"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </MasterDataTableView>
</template>
