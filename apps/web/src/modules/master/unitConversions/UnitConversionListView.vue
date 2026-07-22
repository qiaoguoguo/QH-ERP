<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  masterDataApi,
  type CandidateItem,
  type MasterDataStatus,
  type ResourceId,
  type RoundingMode,
  type UnitConversionPayload,
  type UnitConversionPreviewResult,
  type UnitConversionRecord,
} from '../../../shared/api/masterDataApi'
import { useAuthStore } from '../../../stores/authStore'
import { errorMessage, pageItems, statusTagType } from '../../system/shared/pageHelpers'
import MasterDataTableView from '../shared/MasterDataTableView.vue'
import { masterStatusLabel, roundingModeLabel } from '../shared/masterPageHelpers'
import { confirmAction } from '../../../shared/ui/confirmDialog'

const authStore = useAuthStore()

const roundingOptions: Array<{ label: string; value: RoundingMode }> = [
  { label: '四舍五入', value: 'HALF_UP' },
  { label: '向上取整', value: 'UP' },
  { label: '向下取整', value: 'DOWN' },
]

const filters = reactive<{
  keyword: string
  materialId: ResourceId | ''
  businessUnitId: ResourceId | ''
  status?: MasterDataStatus
  effectiveDate: string
}>({
  keyword: '',
  materialId: '',
  businessUnitId: '',
  status: undefined,
  effectiveDate: '',
})
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<UnitConversionRecord[]>([])
const materialCandidates = ref<CandidateItem[]>([])
const unitCandidates = ref<CandidateItem[]>([])
const loading = ref(true)
const candidateLoading = ref(false)
const error = ref('')
const candidateError = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const formVisible = ref(false)
const formSubmitting = ref(false)
const formError = ref('')
const editingRecord = ref<UnitConversionRecord | null>(null)
const detailVisible = ref(false)
const detailRecord = ref<UnitConversionRecord | null>(null)
const previewVisible = ref(false)
const previewSubmitting = ref(false)
const previewError = ref('')
const previewTarget = ref<UnitConversionRecord | null>(null)
const previewResult = ref<UnitConversionPreviewResult | null>(null)
const previewForm = reactive({ businessQuantity: '', businessDate: '' })

const form = reactive<{
  materialId: ResourceId | ''
  businessUnitId: ResourceId | ''
  conversionRate: string
  quantityScale: string
  roundingMode: RoundingMode
  effectiveFrom: string
  effectiveTo: string
  remark: string
}>({
  materialId: '',
  businessUnitId: '',
  conversionRate: '',
  quantityScale: '',
  roundingMode: 'HALF_UP',
  effectiveFrom: '',
  effectiveTo: '',
  remark: '',
})

const canCreate = computed(() => authStore.hasPermission('master:unit-conversion:create'))
const canUpdate = computed(() => authStore.hasPermission('master:unit-conversion:update'))
const canEnable = computed(() => authStore.hasPermission('master:unit-conversion:enable'))
const canDisable = computed(() => authStore.hasPermission('master:unit-conversion:disable'))

function mergeCandidateItems(page: { items?: CandidateItem[]; selectedItems?: CandidateItem[] }): CandidateItem[] {
  const merged = new Map<string, CandidateItem>()
  ;[...(page.selectedItems ?? []), ...(page.items ?? [])].forEach((item) => {
    merged.set(String(item.id), item)
  })
  return Array.from(merged.values())
}

function normalizeOptionalId(value: ResourceId | ''): ResourceId | undefined {
  if (value === '' || value === null || value === undefined) {
    return undefined
  }
  const text = String(value).trim()
  if (!text) {
    return undefined
  }
  const numberValue = Number(text)
  return Number.isFinite(numberValue) ? numberValue : text
}

function normalizeRequiredId(value: ResourceId | ''): ResourceId | null {
  return normalizeOptionalId(value) ?? null
}

function selectedIds(value: ResourceId | ''): ResourceId[] {
  const id = normalizeOptionalId(value)
  return id === undefined ? [] : [id]
}

async function loadMaterialCandidates(keyword = '', selectedMaterialIds: ResourceId[] = []) {
  candidateLoading.value = true
  candidateError.value = ''
  try {
    const materials = await masterDataApi.unitConversions.materialCandidates({
      keyword,
      page: 1,
      pageSize: 20,
      selectedIds: selectedMaterialIds,
    })
    materialCandidates.value = mergeCandidateItems(materials)
  } catch (caught) {
    materialCandidates.value = []
    candidateError.value = errorMessage(caught)
  } finally {
    candidateLoading.value = false
  }
}

async function loadUnitCandidates(keyword = '', selectedUnitIds: ResourceId[] = []) {
  candidateLoading.value = true
  candidateError.value = ''
  try {
    const units = await masterDataApi.unitConversions.unitCandidates({
      keyword,
      page: 1,
      pageSize: 20,
      selectedIds: selectedUnitIds,
    })
    unitCandidates.value = mergeCandidateItems(units)
  } catch (caught) {
    unitCandidates.value = []
    candidateError.value = errorMessage(caught)
  } finally {
    candidateLoading.value = false
  }
}

async function loadCandidates(selectedMaterialIds: ResourceId[] = [], selectedUnitIds: ResourceId[] = []) {
  candidateLoading.value = true
  candidateError.value = ''
  try {
    const [materials, units] = await Promise.all([
      masterDataApi.unitConversions.materialCandidates({
        keyword: '',
        page: 1,
        pageSize: 20,
        selectedIds: selectedMaterialIds,
      }),
      masterDataApi.unitConversions.unitCandidates({
        keyword: '',
        page: 1,
        pageSize: 20,
        selectedIds: selectedUnitIds,
      }),
    ])
    materialCandidates.value = mergeCandidateItems({ items: pageItems(materials), selectedItems: materials.selectedItems })
    unitCandidates.value = mergeCandidateItems({ items: pageItems(units), selectedItems: units.selectedItems })
  } catch (caught) {
    materialCandidates.value = []
    unitCandidates.value = []
    candidateError.value = errorMessage(caught)
  } finally {
    candidateLoading.value = false
  }
}

function searchFormMaterials(keyword: string) {
  void loadMaterialCandidates(keyword, selectedIds(form.materialId))
}

function searchFormUnits(keyword: string) {
  void loadUnitCandidates(keyword, selectedIds(form.businessUnitId))
}

function searchFilterMaterials(keyword: string) {
  void loadMaterialCandidates(keyword, selectedIds(filters.materialId))
}

function searchFilterUnits(keyword: string) {
  void loadUnitCandidates(keyword, selectedIds(filters.businessUnitId))
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await masterDataApi.unitConversions.list({
      keyword: filters.keyword,
      materialId: normalizeOptionalId(filters.materialId),
      businessUnitId: normalizeOptionalId(filters.businessUnitId),
      status: filters.status,
      effectiveDate: filters.effectiveDate || undefined,
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
  filters.materialId = ''
  filters.businessUnitId = ''
  filters.status = undefined
  filters.effectiveDate = ''
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

function resetForm(record?: UnitConversionRecord) {
  Object.assign(form, {
    materialId: record?.materialId ?? '',
    businessUnitId: record?.businessUnitId ?? '',
    conversionRate: record?.conversionRate ?? '',
    quantityScale: record ? String(record.quantityScale) : '',
    roundingMode: record?.roundingMode ?? 'HALF_UP',
    effectiveFrom: record?.effectiveFrom ?? '',
    effectiveTo: record?.effectiveTo ?? '',
    remark: record?.remark ?? '',
  })
  formError.value = ''
}

async function openCreate() {
  editingRecord.value = null
  resetForm()
  await loadCandidates()
  formVisible.value = true
}

async function openEdit(record: UnitConversionRecord) {
  editingRecord.value = record
  resetForm(record)
  await loadCandidates([record.materialId], [record.businessUnitId])
  formVisible.value = true
}

function openDetail(record: UnitConversionRecord) {
  detailRecord.value = record
  detailVisible.value = true
}

function validateForm(): UnitConversionPayload | null {
  const materialId = normalizeRequiredId(form.materialId)
  const businessUnitId = normalizeRequiredId(form.businessUnitId)
  if (materialId === null || businessUnitId === null) {
    formError.value = '请选择物料和业务单位'
    return null
  }
  const rate = form.conversionRate.trim()
  if (!rate || !Number.isFinite(Number(rate)) || Number(rate) <= 0) {
    formError.value = '换算比例必须大于 0'
    return null
  }
  const quantityScale = Number(form.quantityScale)
  if (!Number.isInteger(quantityScale) || quantityScale < 0) {
    formError.value = '数量精度必须为非负整数'
    return null
  }
  if (form.effectiveFrom && form.effectiveTo && form.effectiveFrom > form.effectiveTo) {
    formError.value = '开始日期不能晚于结束日期'
    return null
  }
  const payload: UnitConversionPayload = {
    materialId,
    businessUnitId,
    conversionRate: rate,
    quantityScale,
    roundingMode: form.roundingMode,
    effectiveFrom: form.effectiveFrom || null,
    effectiveTo: form.effectiveTo || null,
    remark: form.remark.trim() || null,
  }
  if (editingRecord.value) {
    payload.version = editingRecord.value.version
  }
  formError.value = ''
  return payload
}

async function saveRecord() {
  if (formSubmitting.value) {
    return
  }
  const payload = validateForm()
  if (!payload) {
    return
  }
  formSubmitting.value = true
  try {
    if (editingRecord.value) {
      await masterDataApi.unitConversions.update(editingRecord.value.id, payload)
    } else {
      await masterDataApi.unitConversions.create(payload)
    }
    formVisible.value = false
    await loadRecords()
  } catch (caught) {
    formError.value = errorMessage(caught)
  } finally {
    formSubmitting.value = false
  }
}

async function enableRecord(record: UnitConversionRecord) {
  if (!(await confirmAction(`确认启用换算关系“${record.materialCode} / ${record.businessUnitName}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await masterDataApi.unitConversions.enable(record.id, { version: record.version })
    await loadRecords()
  } catch (caught) {
    actionError.value = errorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function disableRecord(record: UnitConversionRecord) {
  if (!(await confirmAction(`确认停用换算关系“${record.materialCode} / ${record.businessUnitName}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await masterDataApi.unitConversions.disable(record.id, { version: record.version })
    await loadRecords()
  } catch (caught) {
    actionError.value = errorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function openPreview(record: UnitConversionRecord) {
  previewTarget.value = record
  previewForm.businessQuantity = ''
  previewForm.businessDate = ''
  previewResult.value = null
  previewError.value = ''
  previewVisible.value = true
}

async function submitPreview() {
  if (!previewTarget.value || previewSubmitting.value) {
    return
  }
  const quantity = previewForm.businessQuantity.trim()
  if (!quantity || !Number.isFinite(Number(quantity)) || Number(quantity) <= 0) {
    previewError.value = '业务数量必须大于 0'
    return
  }
  previewSubmitting.value = true
  previewError.value = ''
  try {
    previewResult.value = await masterDataApi.unitConversions.convert({
      materialId: previewTarget.value.materialId,
      businessUnitId: previewTarget.value.businessUnitId,
      businessQuantity: quantity,
      businessDate: previewForm.businessDate || undefined,
    })
  } catch (caught) {
    previewError.value = errorMessage(caught)
  } finally {
    previewSubmitting.value = false
  }
}

function formatDate(value?: string | null) {
  return value || '-'
}

function formatDateTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}

onMounted(() => {
  void loadCandidates()
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="物料单位换算" description="维护物料业务单位到基本单位的受控换算关系。">
    <template #actions>
      <el-button v-if="canCreate" data-test="create-unit-conversion" type="primary" @click="openCreate">
        新增换算关系
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="unit-conversion-keyword" clearable placeholder="物料编码或名称" />
        </el-form-item>
        <el-form-item label="物料">
          <el-select
            v-model="filters.materialId"
            clearable
            filterable
            remote
            :remote-method="searchFilterMaterials"
            placeholder="全部物料"
          >
            <el-option
              v-for="item in materialCandidates"
              :key="item.id"
              :label="`${item.code} ${item.name}`"
              :value="item.id"
              :disabled="Boolean(item.disabled)"
            >
              <span>{{ item.code }} {{ item.name }}</span>
              <span v-if="item.disabledReason" class="candidate-disabled-reason">{{ item.disabledReason }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="业务单位">
          <el-select
            v-model="filters.businessUnitId"
            clearable
            filterable
            remote
            :remote-method="searchFilterUnits"
            placeholder="全部单位"
          >
            <el-option
              v-for="item in unitCandidates"
              :key="item.id"
              :label="`${item.code} ${item.name}`"
              :value="item.id"
              :disabled="Boolean(item.disabled)"
            >
              <span>{{ item.code }} {{ item.name }}</span>
              <span v-if="item.disabledReason" class="candidate-disabled-reason">{{ item.disabledReason }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="有效日期">
          <el-date-picker
            v-model="filters.effectiveDate"
            value-on-clear=""
            type="date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
          />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-unit-conversion" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-unit-conversion" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="candidateError" class="state-alert" type="error" :title="candidateError" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert
        v-if="loading || candidateLoading"
        class="state-alert"
        type="info"
        title="物料单位换算加载中"
        :closable="false"
      />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无物料单位换算关系'" stripe>
        <el-table-column prop="materialCode" label="物料编码" min-width="140" show-overflow-tooltip />
        <el-table-column prop="materialName" label="物料名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="baseUnitName" label="基本单位" min-width="100" />
        <el-table-column prop="businessUnitName" label="业务单位" min-width="100" />
        <el-table-column prop="conversionRate" label="换算比例" min-width="120" align="right" />
        <el-table-column prop="quantityScale" label="数量精度" min-width="100" />
        <el-table-column label="舍入方式" min-width="110">
          <template #default="{ row }">{{ roundingModeLabel(row.roundingMode) }}</template>
        </el-table-column>
        <el-table-column label="有效期" min-width="180">
          <template #default="{ row }">{{ formatDate(row.effectiveFrom) }} 至 {{ formatDate(row.effectiveTo) }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ masterStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lockedReason" label="锁定原因" min-width="160" show-overflow-tooltip />
        <el-table-column label="更新时间" min-width="150">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-unit-conversion" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="canUpdate"
              size="small"
              text
              data-test="edit-unit-conversion"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-dropdown trigger="click" class="table-actions-more">
              <el-button size="small" text>更多</el-button>
              <template #dropdown>
                <el-dropdown-menu class="table-actions-more-menu">
                  <el-button size="small" text data-test="preview-unit-conversion" @click="openPreview(row)">换算预览</el-button>
                  <el-button
                    v-if="canEnable && row.status === 'DISABLED'"
                    size="small"
                    text
                    type="success"
                    :disabled="actionLoading"
                    data-test="enable-unit-conversion"
                    @click="enableRecord(row)"
                  >
                    启用
                  </el-button>
                  <el-button
                    v-if="canDisable && row.status === 'ENABLED'"
                    size="small"
                    text
                    type="danger"
                    :disabled="actionLoading"
                    data-test="disable-unit-conversion"
                    @click="disableRecord(row)"
                  >
                    停用
                  </el-button>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
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

    <el-dialog v-model="formVisible" :title="editingRecord ? '编辑换算关系' : '新增换算关系'" width="min(720px, 96vw)">
      <el-alert v-if="formError" class="form-alert" type="error" :title="formError" :closable="false" />
      <el-form label-position="top">
        <div class="conversion-form-grid">
          <el-form-item label="物料">
            <el-select
              v-model="form.materialId"
              data-test="unit-conversion-material-id"
              filterable
              remote
              :remote-method="searchFormMaterials"
              placeholder="请选择物料"
              style="width: 100%"
              :disabled="Boolean(editingRecord)"
            >
              <el-option
                v-for="item in materialCandidates"
                :key="item.id"
                :label="`${item.code} ${item.name}`"
                :value="item.id"
                :disabled="Boolean(item.disabled)"
              >
                <span>{{ item.code }} {{ item.name }}</span>
                <span v-if="item.disabledReason" class="candidate-disabled-reason">{{ item.disabledReason }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="业务单位">
            <el-select
              v-model="form.businessUnitId"
              data-test="unit-conversion-business-unit-id"
              filterable
              remote
              :remote-method="searchFormUnits"
              placeholder="请选择业务单位"
              style="width: 100%"
            >
              <el-option
                v-for="item in unitCandidates"
                :key="item.id"
                :label="`${item.code} ${item.name}`"
                :value="item.id"
                :disabled="Boolean(item.disabled)"
              >
                <span>{{ item.code }} {{ item.name }}</span>
                <span v-if="item.disabledReason" class="candidate-disabled-reason">{{ item.disabledReason }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="换算比例">
            <el-input v-model="form.conversionRate" name="unit-conversion-rate" class="numeric-input" />
          </el-form-item>
          <el-form-item label="数量精度">
            <el-input v-model="form.quantityScale" name="unit-conversion-scale" inputmode="numeric" />
          </el-form-item>
          <el-form-item label="舍入方式">
            <el-select
              v-model="form.roundingMode"
              data-test="unit-conversion-rounding-mode"
              style="width: 100%"
            >
              <el-option v-for="option in roundingOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="生效日期">
            <el-date-picker
              v-model="form.effectiveFrom"
              name="unit-conversion-effective-from"
              value-on-clear=""
              type="date"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
            />
          </el-form-item>
          <el-form-item label="失效日期">
            <el-date-picker
              v-model="form.effectiveTo"
              name="unit-conversion-effective-to"
              value-on-clear=""
              type="date"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
            />
          </el-form-item>
        </div>
        <el-form-item label="备注">
          <el-input v-model="form.remark" name="unit-conversion-remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button
          data-test="submit-unit-conversion"
          type="primary"
          :loading="formSubmitting"
          :disabled="formSubmitting"
          @click="saveRecord"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewVisible" title="换算预览" width="min(560px, 94vw)">
      <el-alert v-if="previewError" class="form-alert" type="error" :title="previewError" :closable="false" />
      <el-form label-position="top">
        <el-form-item label="业务数量">
          <el-input v-model="previewForm.businessQuantity" name="unit-conversion-preview-quantity" class="numeric-input" />
        </el-form-item>
        <el-form-item label="业务日期">
          <el-date-picker
            v-model="previewForm.businessDate"
            value-on-clear=""
            type="date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            placeholder="默认当前日期"
          />
        </el-form-item>
      </el-form>
      <dl v-if="previewResult" class="conversion-preview-result">
        <dt>基本单位数量</dt>
        <dd>{{ previewResult.baseQuantity }}</dd>
        <dt>换算比例快照</dt>
        <dd>{{ previewResult.conversionRateSnapshot }}</dd>
        <dt>精度和舍入</dt>
        <dd>{{ previewResult.quantityScaleSnapshot }} / {{ roundingModeLabel(previewResult.roundingModeSnapshot) }}</dd>
      </dl>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button
          data-test="submit-unit-conversion-preview"
          type="primary"
          :loading="previewSubmitting"
          :disabled="previewSubmitting"
          @click="submitPreview"
        >
          预览
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="换算关系详情" size="min(520px, 92vw)">
      <dl v-if="detailRecord" class="conversion-detail-list">
        <dt>物料</dt>
        <dd>{{ detailRecord.materialCode }} {{ detailRecord.materialName }}</dd>
        <dt>基本单位</dt>
        <dd>{{ detailRecord.baseUnitName }}</dd>
        <dt>业务单位</dt>
        <dd>{{ detailRecord.businessUnitName }}</dd>
        <dt>换算比例</dt>
        <dd>{{ detailRecord.conversionRate }}</dd>
        <dt>数量精度</dt>
        <dd>{{ detailRecord.quantityScale }}</dd>
        <dt>舍入方式</dt>
        <dd>{{ roundingModeLabel(detailRecord.roundingMode) }}</dd>
        <dt>有效期</dt>
        <dd>{{ formatDate(detailRecord.effectiveFrom) }} 至 {{ formatDate(detailRecord.effectiveTo) }}</dd>
        <dt>状态</dt>
        <dd>{{ masterStatusLabel(detailRecord.status) }}</dd>
        <dt>版本</dt>
        <dd>{{ detailRecord.version }}</dd>
        <dt>备注</dt>
        <dd>{{ detailRecord.remark || '未填写' }}</dd>
      </dl>
    </el-drawer>
  </MasterDataTableView>
</template>

<style scoped>
.conversion-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.numeric-input :deep(input) {
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.candidate-disabled-reason {
  color: var(--qherp-muted);
  float: right;
  font-size: 12px;
  margin-left: 12px;
}

.conversion-preview-result,
.conversion-detail-list {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  gap: 10px 14px;
  margin: 0;
}

.conversion-preview-result {
  margin-top: 16px;
}

.conversion-preview-result dt,
.conversion-detail-list dt {
  color: var(--qherp-muted);
}

.conversion-preview-result dd,
.conversion-detail-list dd {
  min-width: 0;
  margin: 0;
  word-break: break-word;
}

@media (max-width: 760px) {
  .conversion-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
