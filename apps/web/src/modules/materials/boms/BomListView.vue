<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  bomApi,
  type BomCopyPayload,
  type BomDetailRecord,
  type BomPayload,
  type BomStatus,
  type BomSummaryRecord,
  type ResourceId,
} from '../../../shared/api/bomApi'
import { masterDataApi, type MaterialRecord, type UnitRecord } from '../../../shared/api/masterDataApi'
import { useAuthStore } from '../../../stores/authStore'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { materialTypeLabel } from '../../master/shared/masterPageHelpers'
import { errorMessage, pageItems } from '../../system/shared/pageHelpers'
import BomLineEditor from './BomLineEditor.vue'
import BomStatusTag from './BomStatusTag.vue'
import { type BomLineDraft, lossRateNumber, newBomLine, positiveNumber } from './bomPageHelpers'
import { confirmAction } from '../../../shared/ui/confirmDialog'

const authStore = useAuthStore()
const filters = reactive<{
  keyword: string
  status?: BomStatus
  parentMaterialId: ResourceId | ''
}>({
  keyword: '',
  status: undefined,
  parentMaterialId: '',
})
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
})
const records = ref<BomSummaryRecord[]>([])
const materials = ref<MaterialRecord[]>([])
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
const lineErrors = ref<Record<number, string>>({})
const editingRecord = ref<BomDetailRecord | null>(null)
const form = reactive({
  bomCode: '',
  parentMaterialId: '' as ResourceId | '',
  versionCode: '',
  name: '',
  baseQuantity: '1',
  baseUnitId: '' as ResourceId | '',
  effectiveFrom: '',
  effectiveTo: '',
  remark: '',
})
const lines = ref<BomLineDraft[]>([newBomLine()])

const detailVisible = ref(false)
const detailRecord = ref<BomDetailRecord | null>(null)

const copyVisible = ref(false)
const copySubmitting = ref(false)
const copyError = ref('')
const copyTarget = ref<BomSummaryRecord | null>(null)
const copyForm = reactive({
  bomCode: '',
  versionCode: '',
  name: '',
})

const canCreate = computed(() => authStore.hasPermission('material:bom:create'))
const canUpdate = computed(() => authStore.hasPermission('material:bom:update'))
const canCopy = computed(() => authStore.hasPermission('material:bom:copy'))
const canEnable = computed(() => authStore.hasPermission('material:bom:enable'))
const canDisable = computed(() => authStore.hasPermission('material:bom:disable'))
const parentMaterials = computed(() => materials.value.filter((material) => (
  material.materialType === 'FINISHED_GOOD' || material.materialType === 'SEMI_FINISHED'
)))

async function loadReferences() {
  referenceLoading.value = true
  referenceError.value = ''
  try {
    const [materialPage, unitPage] = await Promise.all([
      masterDataApi.materials.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 100 }),
      masterDataApi.units.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 100 }),
    ])
    materials.value = pageItems(materialPage)
    units.value = pageItems(unitPage)
  } catch (caught) {
    materials.value = []
    units.value = []
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
    const page = await bomApi.list({
      keyword: filters.keyword,
      status: filters.status,
      parentMaterialId: normalizeOptionalId(filters.parentMaterialId),
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
  filters.status = undefined
  filters.parentMaterialId = ''
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

function resetForm(record?: BomDetailRecord) {
  Object.assign(form, {
    bomCode: record?.bomCode ?? '',
    parentMaterialId: record?.parentMaterialId ?? '',
    versionCode: record?.versionCode ?? '',
    name: record?.name ?? '',
    baseQuantity: record ? String(record.baseQuantity) : '1',
    baseUnitId: record?.baseUnitId ?? '',
    effectiveFrom: record?.effectiveFrom ?? '',
    effectiveTo: record?.effectiveTo ?? '',
    remark: record?.remark ?? '',
  })
  lines.value = record?.items.map((item) => ({
    lineNo: item.lineNo,
    childMaterialId: item.childMaterialId,
    unitId: item.unitId,
    quantity: String(item.quantity),
    lossRate: String(item.lossRate ?? 0),
    remark: item.remark ?? '',
  })) ?? [newBomLine()]
  formError.value = ''
  lineErrors.value = {}
}

function openCreate() {
  editingRecord.value = null
  resetForm()
  formVisible.value = true
}

async function openEdit(record: BomSummaryRecord) {
  actionError.value = ''
  try {
    const detail = await bomApi.get(record.id)
    editingRecord.value = detail
    resetForm(detail)
    formVisible.value = true
  } catch (caught) {
    actionError.value = errorMessage(caught)
  }
}

async function openDetail(record: BomSummaryRecord) {
  actionError.value = ''
  try {
    detailRecord.value = await bomApi.get(record.id)
    detailVisible.value = true
  } catch (caught) {
    actionError.value = errorMessage(caught)
  }
}

function validateBomForm(): BomPayload | null {
  const parentMaterialId = normalizeRequiredId(form.parentMaterialId)
  if (!form.bomCode.trim() || !form.versionCode.trim() || !form.name.trim() || parentMaterialId === null) {
    formError.value = '请完整填写 BOM 编码、版本、名称和父项物料'
    return null
  }

  const baseQuantity = positiveNumber(form.baseQuantity)
  if (baseQuantity === null) {
    formError.value = '基准数量必须大于 0'
    return null
  }

  if (lines.value.length === 0) {
    formError.value = 'BOM 明细不能为空'
    return null
  }

  const nextLineErrors: Record<number, string> = {}
  const childIds = new Set<string>()
  const items = []
  for (const line of lines.value) {
    const childMaterialId = normalizeRequiredId(line.childMaterialId)
    if (childMaterialId === null) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行请选择子项物料`
      continue
    }
    const quantity = positiveNumber(line.quantity)
    if (quantity === null) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行用量必须大于 0`
      continue
    }
    const lossRate = lossRateNumber(line.lossRate)
    if (lossRate === null) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行损耗率需大于等于 0 且小于 1`
      continue
    }
    const childKey = String(childMaterialId)
    if (childIds.has(childKey)) {
      formError.value = 'BOM 明细子项不能重复'
      lineErrors.value = {}
      return null
    }
    childIds.add(childKey)
    const unitId = normalizeOptionalId(line.unitId)
    items.push({
      lineNo: line.lineNo,
      childMaterialId,
      ...(unitId !== undefined ? { unitId } : {}),
      quantity,
      lossRate,
      ...(line.remark.trim() ? { remark: line.remark.trim() } : {}),
    })
  }

  lineErrors.value = nextLineErrors
  if (Object.keys(nextLineErrors).length > 0) {
    formError.value = ''
    return null
  }

  const baseUnitId = normalizeOptionalId(form.baseUnitId)
  const payload: BomPayload = {
    bomCode: form.bomCode.trim(),
    parentMaterialId,
    versionCode: form.versionCode.trim(),
    name: form.name.trim(),
    baseQuantity,
    ...(baseUnitId !== undefined ? { baseUnitId } : {}),
    ...(form.effectiveFrom ? { effectiveFrom: form.effectiveFrom } : {}),
    ...(form.effectiveTo ? { effectiveTo: form.effectiveTo } : {}),
    ...(form.remark.trim() ? { remark: form.remark.trim() } : {}),
    items,
  }
  formError.value = ''
  return payload
}

async function saveRecord() {
  if (formSubmitting.value) {
    return
  }
  const payload = validateBomForm()
  if (!payload) {
    return
  }

  formSubmitting.value = true
  try {
    if (editingRecord.value) {
      await bomApi.update(editingRecord.value.id, payload)
    } else {
      await bomApi.create(payload)
    }
    formVisible.value = false
    await loadRecords()
  } catch (caught) {
    formError.value = errorMessage(caught)
  } finally {
    formSubmitting.value = false
  }
}

function openCopy(record: BomSummaryRecord) {
  copyTarget.value = record
  copyForm.bomCode = ''
  copyForm.versionCode = ''
  copyForm.name = record.name
  copyError.value = ''
  copyVisible.value = true
}

async function submitCopy() {
  if (!copyTarget.value || copySubmitting.value) {
    return
  }
  if (!copyForm.bomCode.trim() || !copyForm.versionCode.trim()) {
    copyError.value = '请填写新 BOM 编码和版本'
    return
  }
  const payload: BomCopyPayload = {
    bomCode: copyForm.bomCode.trim(),
    versionCode: copyForm.versionCode.trim(),
  }
  if (copyForm.name.trim()) {
    payload.name = copyForm.name.trim()
  }

  copySubmitting.value = true
  try {
    await bomApi.copy(copyTarget.value.id, payload)
    copyVisible.value = false
    await loadRecords()
  } catch (caught) {
    copyError.value = errorMessage(caught)
  } finally {
    copySubmitting.value = false
  }
}

async function enableRecord(record: BomSummaryRecord) {
  if (!(await confirmAction(`确认启用 BOM“${record.bomCode}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await bomApi.enable(record.id)
    await loadRecords()
  } catch (caught) {
    actionError.value = errorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function disableRecord(record: BomSummaryRecord) {
  if (!(await confirmAction(`确认停用 BOM“${record.bomCode}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await bomApi.disable(record.id)
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

onMounted(() => {
  void loadReferences()
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="BOM 管理" description="维护成品和半成品的标准用料结构、版本和启停状态。">
    <template #actions>
      <el-button v-if="canCreate" data-test="create-bom" type="primary" @click="openCreate">
        新增 BOM
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="bom-keyword" clearable placeholder="BOM 编码、版本或物料" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="filters.status"
            data-test="filter-bom-status"
            clearable
            placeholder="全部状态"
          >
            <el-option label="草稿" value="DRAFT" />
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="父项物料">
          <el-select
            v-model="filters.parentMaterialId"
            data-test="filter-bom-parent-material-id"
            filterable
            clearable
            placeholder="全部父项"
          >
            <el-option
              v-for="material in parentMaterials"
              :key="material.id"
              :label="`${material.code} ${material.name}`"
              :value="material.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-bom" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-bom" @click="resetSearch">重置</el-button>
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
        title="BOM 数据加载中"
        :closable="false"
      />
    </template>

    <el-empty v-if="!loading && records.length === 0" description="暂无 BOM 数据" />
    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无 BOM 数据'" stripe>
        <el-table-column prop="bomCode" label="BOM 编码" min-width="150" show-overflow-tooltip />
        <el-table-column label="父项物料" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.parentMaterialCode }} {{ row.parentMaterialName }}
          </template>
        </el-table-column>
        <el-table-column prop="versionCode" label="版本" min-width="100" show-overflow-tooltip />
        <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="baseQuantity" label="基准数量" min-width="100" />
        <el-table-column prop="baseUnitName" label="单位" min-width="90" />
        <el-table-column prop="itemCount" label="明细数" min-width="90" />
        <el-table-column label="状态" min-width="90">
          <template #default="{ row }">
            <BomStatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="150">
          <template #default="{ row }">
            {{ formatDateTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="330">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-bom" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="canUpdate && row.status === 'DRAFT'"
              size="small"
              text
              data-test="edit-bom"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button v-if="canCopy" size="small" text data-test="copy-bom" @click="openCopy(row)">
              复制
            </el-button>
            <el-button
              v-if="canEnable && row.status === 'DRAFT'"
              size="small"
              text
              type="success"
              data-test="enable-bom"
              :disabled="actionLoading"
              @click="enableRecord(row)"
            >
              启用
            </el-button>
            <el-button
              v-if="canDisable && row.status !== 'DISABLED'"
              size="small"
              text
              type="danger"
              data-test="disable-bom"
              :disabled="actionLoading"
              @click="disableRecord(row)"
            >
              停用
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

    <el-dialog
      v-model="formVisible"
      :title="editingRecord ? '编辑 BOM' : '新增 BOM'"
      width="min(1120px, calc(100vw - 48px))"
    >
      <el-alert v-if="formError" class="form-alert" type="error" :title="formError" :closable="false" />
      <el-form label-position="top">
        <div class="bom-form-grid">
          <el-form-item label="BOM 编码">
            <el-input v-model="form.bomCode" name="bom-code" :disabled="Boolean(editingRecord)" />
          </el-form-item>
          <el-form-item label="版本">
            <el-input v-model="form.versionCode" name="bom-version-code" />
          </el-form-item>
          <el-form-item label="BOM 名称">
            <el-input v-model="form.name" name="bom-name" />
          </el-form-item>
          <el-form-item label="父项物料">
            <el-select
              v-model="form.parentMaterialId"
              data-test="bom-parent-material-id"
              filterable
              placeholder="请选择父项物料"
              style="width: 100%"
            >
              <el-option
                v-for="material in parentMaterials"
                :key="material.id"
                :label="`${material.code} ${material.name}`"
                :value="material.id"
              >
                <span>{{ material.code }} {{ material.name }}</span>
                <span class="line-option-meta">{{ materialTypeLabel(material.materialType) }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="基准数量">
            <el-input v-model="form.baseQuantity" name="bom-base-quantity" />
          </el-form-item>
          <el-form-item label="基准单位">
            <el-select
              v-model="form.baseUnitId"
              data-test="bom-base-unit-id"
              clearable
              placeholder="默认父项单位"
              style="width: 100%"
            >
              <el-option v-for="unit in units" :key="unit.id" :label="unit.name" :value="unit.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="生效日期">
            <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="form.effectiveFrom" name="bom-effective-from" placeholder="选择日期" />
          </el-form-item>
          <el-form-item label="失效日期">
            <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="form.effectiveTo" name="bom-effective-to" placeholder="选择日期" />
          </el-form-item>
        </div>
        <el-form-item label="备注">
          <el-input v-model="form.remark" name="bom-remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="BOM 明细">
          <BomLineEditor
            v-model:lines="lines"
            :materials="materials"
            :units="units"
            :errors="lineErrors"
            :read-only="false"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button
          data-test="submit-bom"
          type="primary"
          :loading="formSubmitting"
          :disabled="formSubmitting"
          @click="saveRecord"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="copyVisible" title="复制 BOM 版本" width="520px">
      <el-alert v-if="copyError" class="form-alert" type="error" :title="copyError" :closable="false" />
      <el-form label-position="top">
        <el-form-item label="新 BOM 编码">
          <el-input v-model="copyForm.bomCode" name="copy-bom-code" />
        </el-form-item>
        <el-form-item label="新版本">
          <el-input v-model="copyForm.versionCode" name="copy-bom-version-code" />
        </el-form-item>
        <el-form-item label="新名称">
          <el-input v-model="copyForm.name" name="copy-bom-name" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="copyVisible = false">取消</el-button>
        <el-button
          data-test="submit-copy-bom"
          type="primary"
          :loading="copySubmitting"
          :disabled="copySubmitting"
          @click="submitCopy"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="BOM 详情" size="560px">
      <dl v-if="detailRecord" class="bom-detail-list">
        <dt>BOM 编码</dt>
        <dd>{{ detailRecord.bomCode }}</dd>
        <dt>父项物料</dt>
        <dd>{{ detailRecord.parentMaterialCode }} {{ detailRecord.parentMaterialName }}</dd>
        <dt>版本</dt>
        <dd>{{ detailRecord.versionCode }}</dd>
        <dt>状态</dt>
        <dd><BomStatusTag :status="detailRecord.status" /></dd>
        <dt>基准数量</dt>
        <dd>{{ detailRecord.baseQuantity }} {{ detailRecord.baseUnitName }}</dd>
        <dt>备注</dt>
        <dd>{{ detailRecord.remark || '未填写' }}</dd>
      </dl>
      <el-table v-if="detailRecord" :data="detailRecord.items" stripe>
        <el-table-column prop="lineNo" label="行号" width="80" />
        <el-table-column label="子项物料" min-width="180">
          <template #default="{ row }">
            {{ row.childMaterialCode }} {{ row.childMaterialName }}
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="用量" width="90" />
        <el-table-column prop="unitName" label="单位" width="90" />
        <el-table-column prop="lossRate" label="损耗率" width="90" />
      </el-table>
    </el-drawer>
  </MasterDataTableView>
</template>

<style scoped>
.bom-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.bom-detail-list {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 10px 14px;
  margin: 0 0 16px;
}

.bom-detail-list dt {
  color: var(--qherp-muted);
}

.bom-detail-list dd {
  min-width: 0;
  margin: 0;
  word-break: break-word;
}

.line-option-meta {
  color: var(--qherp-muted);
  float: right;
  font-size: 12px;
  margin-left: 12px;
}

@media (max-width: 760px) {
  .bom-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
