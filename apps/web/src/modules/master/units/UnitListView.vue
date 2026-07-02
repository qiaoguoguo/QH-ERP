<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { masterDataApi, type MasterDataStatus, type UnitPayload, type UnitRecord } from '../../../shared/api/masterDataApi'
import { useAuthStore } from '../../../stores/authStore'
import { errorMessage, pageItems, statusTagType } from '../../system/shared/pageHelpers'
import MasterDataTableView from '../shared/MasterDataTableView.vue'
import { masterStatusLabel } from '../shared/masterPageHelpers'

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
  pageSize: 20,
  total: 0,
})
const records = ref<UnitRecord[]>([])
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const formVisible = ref(false)
const formSubmitting = ref(false)
const formError = ref('')
const editingRecord = ref<UnitRecord | null>(null)
const form = reactive({
  code: '',
  name: '',
  precisionScale: '',
  sortOrder: '',
  status: 'ENABLED' as MasterDataStatus,
  remark: '',
})

const canCreate = computed(() => authStore.hasPermission('master:unit:create'))
const canUpdate = computed(() => authStore.hasPermission('master:unit:update'))

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await masterDataApi.units.list({
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

function resetForm(record?: UnitRecord) {
  Object.assign(form, {
    code: record?.code ?? '',
    name: record?.name ?? '',
    precisionScale: record ? String(record.precisionScale) : '',
    sortOrder: record ? String(record.sortOrder) : '',
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

function openEdit(record: UnitRecord) {
  editingRecord.value = record
  resetForm(record)
  formVisible.value = true
}

function isBlank(value: string) {
  return value.trim() === ''
}

function validateUnitForm() {
  if (!form.code.trim() || !form.name.trim()) {
    formError.value = '请完整填写编码和名称'
    return false
  }
  if (isBlank(form.precisionScale) || isBlank(form.sortOrder)) {
    formError.value = '精度和排序为必填'
    return false
  }
  formError.value = ''
  return true
}

async function saveRecord() {
  if (formSubmitting.value) {
    return
  }
  if (!validateUnitForm()) {
    return
  }

  const payload: UnitPayload = {
    code: form.code.trim(),
    name: form.name.trim(),
    precisionScale: Number(form.precisionScale),
    sortOrder: Number(form.sortOrder),
    status: form.status,
    remark: form.remark.trim(),
  }

  formSubmitting.value = true
  try {
    if (editingRecord.value) {
      await masterDataApi.units.update(editingRecord.value.id, payload)
    } else {
      await masterDataApi.units.create(payload)
    }
    formVisible.value = false
    await loadRecords()
  } catch (caught) {
    formError.value = errorMessage(caught)
  } finally {
    formSubmitting.value = false
  }
}

async function changeStatus(record: UnitRecord) {
  const nextAction = record.status === 'DISABLED' ? '启用' : '停用'
  if (!window.confirm(`确认${nextAction}单位“${record.name}”？`)) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    if (record.status === 'DISABLED') {
      await masterDataApi.units.enable(record.id)
    } else {
      await masterDataApi.units.disable(record.id)
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
  <MasterDataTableView title="计量单位" description="维护物料、BOM 和生产数量使用的基本单位。">
    <template #actions>
      <el-button v-if="canCreate" data-test="create-record" type="primary" @click="openCreate">新增单位</el-button>
    </template>

    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="record-keyword" clearable placeholder="编码或名称" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 140px">
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
      <el-alert v-if="loading" class="state-alert" type="info" title="计量单位数据加载中" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无计量单位数据'" stripe>
        <el-table-column prop="code" label="编码" min-width="140" show-overflow-tooltip />
        <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="precisionScale" label="精度" min-width="90" />
        <el-table-column prop="sortOrder" label="排序" min-width="90" />
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
              :type="row.status === 'DISABLED' ? 'success' : 'danger'"
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
      layout="total, prev, pager, next"
      :total="pagination.total"
      :page-size="pagination.pageSize"
      :current-page="pagination.page"
      @current-change="changePage"
    />

    <el-dialog v-model="formVisible" :title="editingRecord ? '编辑单位' : '新增单位'" width="560px">
      <el-alert v-if="formError" class="form-alert" type="error" :title="formError" :closable="false" />
      <el-form label-position="top">
        <el-form-item label="单位编码">
          <el-input v-model="form.code" name="record-code" :disabled="Boolean(editingRecord)" />
        </el-form-item>
        <el-form-item label="单位名称">
          <el-input v-model="form.name" name="record-name" />
        </el-form-item>
        <el-form-item label="精度">
          <el-input v-model="form.precisionScale" name="record-precision-scale" type="number" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input v-model="form.sortOrder" name="record-sort-order" type="number" />
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
