<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  masterDataApi,
  type CategoryRecord,
  type MasterDataStatus,
  type MaterialPayload,
  type MaterialRecord,
  type MaterialSourceType,
  type MaterialType,
  type ResourceId,
  type UnitRecord,
} from '../../../shared/api/masterDataApi'
import { useAuthStore } from '../../../stores/authStore'
import { errorMessage, pageItems, statusTagType } from '../../system/shared/pageHelpers'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { masterStatusLabel, materialTypeLabel, sourceTypeLabel } from '../../master/shared/masterPageHelpers'

const materialTypeOptions: Array<{ label: string; value: MaterialType }> = [
  { label: '原材料', value: 'RAW_MATERIAL' },
  { label: '半成品', value: 'SEMI_FINISHED' },
  { label: '成品', value: 'FINISHED_GOOD' },
  { label: '辅料', value: 'AUXILIARY' },
]
const sourceTypeOptions: Array<{ label: string; value: MaterialSourceType }> = [
  { label: '外购', value: 'PURCHASED' },
  { label: '自制', value: 'SELF_MADE' },
  { label: '委外', value: 'OUTSOURCED' },
]

const authStore = useAuthStore()
const filters = reactive<{
  keyword: string
  status?: MasterDataStatus
  categoryId: ResourceId | ''
  materialType?: MaterialType
  sourceType?: MaterialSourceType
}>({
  keyword: '',
  status: undefined,
  categoryId: '',
  materialType: undefined,
  sourceType: undefined,
})
const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0,
})
const records = ref<MaterialRecord[]>([])
const categories = ref<CategoryRecord[]>([])
const units = ref<UnitRecord[]>([])
const loading = ref(true)
const referenceLoading = ref(true)
const error = ref('')
const referenceError = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const formVisible = ref(false)
const formSubmitting = ref(false)
const formError = ref('')
const editingRecord = ref<MaterialRecord | null>(null)
const detailVisible = ref(false)
const detailRecord = ref<MaterialRecord | null>(null)
const form = reactive<{
  code: string
  name: string
  specification: string
  materialType: MaterialType | ''
  sourceType: MaterialSourceType | ''
  categoryId: ResourceId | ''
  unitId: ResourceId | ''
  status: MasterDataStatus
  remark: string
}>({
  code: '',
  name: '',
  specification: '',
  materialType: '',
  sourceType: '',
  categoryId: '',
  unitId: '',
  status: 'ENABLED',
  remark: '',
})

const canCreate = computed(() => authStore.hasPermission('master:material:create'))
const canUpdate = computed(() => authStore.hasPermission('master:material:update'))

async function loadReferences() {
  referenceLoading.value = true
  referenceError.value = ''
  try {
    const [unitPage, categoryPage] = await Promise.all([
      masterDataApi.units.list({ page: 1, pageSize: 100, status: 'ENABLED', keyword: '' }),
      masterDataApi.categories.list({ page: 1, pageSize: 100, status: 'ENABLED', keyword: '' }),
    ])
    units.value = pageItems(unitPage)
    categories.value = pageItems(categoryPage)
  } catch (caught) {
    units.value = []
    categories.value = []
    referenceError.value = errorMessage(caught)
  } finally {
    referenceLoading.value = false
  }
}

function normalizeOptionalId(value: ResourceId | ''): ResourceId | undefined {
  if (value === '' || value === null || value === undefined) {
    return undefined
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : undefined
  }

  const trimmedValue = String(value).trim()
  if (!trimmedValue) {
    return undefined
  }
  const numericValue = Number(trimmedValue)
  return Number.isFinite(numericValue) ? numericValue : trimmedValue
}

function normalizeRequiredId(value: ResourceId | ''): ResourceId | null {
  return normalizeOptionalId(value) ?? null
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await masterDataApi.materials.list({
      keyword: filters.keyword,
      status: filters.status,
      page: pagination.page,
      pageSize: pagination.pageSize,
      categoryId: normalizeOptionalId(filters.categoryId),
      materialType: filters.materialType,
      sourceType: filters.sourceType,
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
  filters.status = undefined
  filters.categoryId = ''
  filters.materialType = undefined
  filters.sourceType = undefined
  pagination.page = 1
  void loadRecords()
}

function changePage(page: number) {
  pagination.page = page
  void loadRecords()
}

function resetForm(record?: MaterialRecord) {
  Object.assign(form, {
    code: record?.code ?? '',
    name: record?.name ?? '',
    specification: record?.specification ?? '',
    materialType: record?.materialType ?? '',
    sourceType: record?.sourceType ?? '',
    categoryId: record?.categoryId ?? '',
    unitId: record?.unitId ?? '',
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

function openEdit(record: MaterialRecord) {
  editingRecord.value = record
  resetForm(record)
  formVisible.value = true
}

function openDetail(record: MaterialRecord) {
  detailRecord.value = record
  detailVisible.value = true
}

function validateMaterialForm(): {
  materialType: MaterialType
  sourceType: MaterialSourceType
  categoryId: ResourceId
  unitId: ResourceId
} | null {
  if (!form.code.trim() || !form.name.trim()) {
    formError.value = '请完整填写编码和名称'
    return null
  }
  if (!form.materialType || !form.sourceType) {
    formError.value = '物料类型和来源属性为必填'
    return null
  }

  const categoryId = normalizeRequiredId(form.categoryId)
  const unitId = normalizeRequiredId(form.unitId)
  if (categoryId === null || unitId === null) {
    formError.value = '分类和单位为必填'
    return null
  }

  formError.value = ''
  return {
    materialType: form.materialType,
    sourceType: form.sourceType,
    categoryId,
    unitId,
  }
}

async function saveRecord() {
  if (formSubmitting.value) {
    return
  }
  const validatedForm = validateMaterialForm()
  if (!validatedForm) {
    return
  }

  const payload: MaterialPayload = {
    code: form.code.trim(),
    name: form.name.trim(),
    materialType: validatedForm.materialType,
    sourceType: validatedForm.sourceType,
    categoryId: validatedForm.categoryId,
    unitId: validatedForm.unitId,
    status: form.status,
  }
  const specification = form.specification.trim()
  const remark = form.remark.trim()
  if (specification) {
    payload.specification = specification
  }
  if (remark) {
    payload.remark = remark
  }

  formSubmitting.value = true
  try {
    if (editingRecord.value) {
      await masterDataApi.materials.update(editingRecord.value.id, payload)
    } else {
      await masterDataApi.materials.create(payload)
    }
    formVisible.value = false
    await loadRecords()
  } catch (caught) {
    formError.value = errorMessage(caught)
  } finally {
    formSubmitting.value = false
  }
}

async function changeStatus(record: MaterialRecord) {
  const nextAction = record.status === 'DISABLED' ? '启用' : '停用'
  if (!window.confirm(`确认${nextAction}物料“${record.name}”？`)) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    if (record.status === 'DISABLED') {
      await masterDataApi.materials.enable(record.id)
    } else {
      await masterDataApi.materials.disable(record.id)
    }
    await loadRecords()
  } catch (caught) {
    actionError.value = errorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(() => {
  void loadReferences()
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="物料档案" description="维护生产、采购、库存和 BOM 使用的物料主数据。">
    <template #actions>
      <el-button v-if="canCreate" data-test="create-material" type="primary" @click="openCreate">
        新增物料
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="material-keyword" clearable placeholder="编码或名称" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 140px">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select
            v-model="filters.categoryId"
            data-test="filter-material-category-id"
            clearable
            placeholder="全部分类"
            style="width: 160px"
          >
            <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="物料类型">
          <el-select
            v-model="filters.materialType"
            data-test="filter-material-type"
            clearable
            placeholder="全部类型"
            style="width: 140px"
          >
            <el-option
              v-for="option in materialTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="来源属性">
          <el-select
            v-model="filters.sourceType"
            data-test="filter-source-type"
            clearable
            placeholder="全部来源"
            style="width: 140px"
          >
            <el-option
              v-for="option in sourceTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-material" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-material" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="referenceError" class="state-alert" type="error" :title="referenceError" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert
        v-if="loading || referenceLoading"
        class="state-alert"
        type="info"
        title="物料档案数据加载中"
        :closable="false"
      />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无物料档案数据'" stripe>
        <el-table-column prop="code" label="编码" min-width="150" show-overflow-tooltip />
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="specification" label="规格型号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="categoryName" label="分类" min-width="120" show-overflow-tooltip />
        <el-table-column prop="unitName" label="单位" min-width="90" show-overflow-tooltip />
        <el-table-column label="物料类型" min-width="110">
          <template #default="{ row }">
            {{ materialTypeLabel(row.materialType) }}
          </template>
        </el-table-column>
        <el-table-column label="来源属性" min-width="110">
          <template #default="{ row }">
            {{ sourceTypeLabel(row.sourceType) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ masterStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" fixed="right" min-width="210">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-material" @click="openDetail(row)">详情</el-button>
            <el-button v-if="canUpdate" size="small" text data-test="edit-material" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button
              v-if="canUpdate"
              size="small"
              text
              :disabled="actionLoading"
              :type="row.status === 'DISABLED' ? 'success' : 'danger'"
              :data-test="row.status === 'DISABLED' ? 'enable-material' : 'disable-material'"
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

    <el-dialog v-model="formVisible" :title="editingRecord ? '编辑物料' : '新增物料'" width="640px">
      <el-alert v-if="formError" class="form-alert" type="error" :title="formError" :closable="false" />
      <el-form label-position="top">
        <el-form-item label="物料编码">
          <el-input v-model="form.code" name="material-code" :disabled="Boolean(editingRecord)" />
        </el-form-item>
        <el-form-item label="物料名称">
          <el-input v-model="form.name" name="material-name" />
        </el-form-item>
        <el-form-item label="规格型号">
          <el-input v-model="form.specification" name="material-specification" />
        </el-form-item>
        <el-form-item label="物料类型">
          <el-select v-model="form.materialType" data-test="material-type" placeholder="请选择物料类型" style="width: 100%">
            <el-option
              v-for="option in materialTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="来源属性">
          <el-select
            v-model="form.sourceType"
            data-test="material-source-type"
            placeholder="请选择来源属性"
            style="width: 100%"
          >
            <el-option
              v-for="option in sourceTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="物料分类">
          <el-select
            v-model="form.categoryId"
            data-test="material-category-id"
            placeholder="请选择物料分类"
            style="width: 100%"
          >
            <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="计量单位">
          <el-select
            v-model="form.unitId"
            data-test="material-unit-id"
            placeholder="请选择计量单位"
            style="width: 100%"
          >
            <el-option v-for="unit in units" :key="unit.id" :label="unit.name" :value="unit.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" data-test="material-status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" name="material-remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button
          data-test="submit-material"
          type="primary"
          :loading="formSubmitting"
          :disabled="formSubmitting"
          @click="saveRecord"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="物料详情" size="420px">
      <dl v-if="detailRecord" class="material-detail-list">
        <dt>编码</dt>
        <dd>{{ detailRecord.code }}</dd>
        <dt>名称</dt>
        <dd>{{ detailRecord.name }}</dd>
        <dt>规格型号</dt>
        <dd>{{ detailRecord.specification || '未填写' }}</dd>
        <dt>分类</dt>
        <dd>{{ detailRecord.categoryName || '未匹配' }}</dd>
        <dt>单位</dt>
        <dd>{{ detailRecord.unitName || '未匹配' }}</dd>
        <dt>物料类型</dt>
        <dd>{{ materialTypeLabel(detailRecord.materialType) }}</dd>
        <dt>来源属性</dt>
        <dd>{{ sourceTypeLabel(detailRecord.sourceType) }}</dd>
        <dt>状态</dt>
        <dd>{{ masterStatusLabel(detailRecord.status) }}</dd>
        <dt>备注</dt>
        <dd>{{ detailRecord.remark || '未填写' }}</dd>
      </dl>
    </el-drawer>
  </MasterDataTableView>
</template>

<style scoped>
.material-detail-list {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 10px 14px;
  margin: 0;
}

.material-detail-list dt {
  color: var(--qherp-muted);
}

.material-detail-list dd {
  min-width: 0;
  margin: 0;
  word-break: break-word;
}
</style>
