<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  masterDataApi,
  type CodingDatePattern,
  type CodingObjectType,
  type CodingResetCycle,
  type CodingRulePayload,
  type CodingRuleRecord,
  type MasterDataStatus,
} from '../../../shared/api/masterDataApi'
import { useAuthStore } from '../../../stores/authStore'
import { errorMessage, pageItems, statusTagType } from '../../system/shared/pageHelpers'
import MasterDataTableView from '../shared/MasterDataTableView.vue'
import {
  codingDatePatternLabel,
  codingObjectTypeLabel,
  codingResetCycleLabel,
  masterStatusLabel,
} from '../shared/masterPageHelpers'
import { confirmAction } from '../../../shared/ui/confirmDialog'

const authStore = useAuthStore()

const objectTypeOptions: Array<{ label: string; value: CodingObjectType }> = [
  { label: '物料', value: 'MATERIAL' },
  { label: '客户', value: 'CUSTOMER' },
  { label: '供应商', value: 'SUPPLIER' },
  { label: 'BOM', value: 'BOM' },
  { label: 'BOM 工程变更', value: 'BOM_ECO' },
]
const datePatternOptions: Array<{ label: string; value: CodingDatePattern }> = [
  { label: '无日期段', value: 'NONE' },
  { label: 'YYYY', value: 'YYYY' },
  { label: 'YYYYMM', value: 'YYYYMM' },
  { label: 'YYYYMMDD', value: 'YYYYMMDD' },
]
const resetCycleOptions: Array<{ label: string; value: CodingResetCycle }> = [
  { label: '永不重置', value: 'NEVER' },
  { label: '按年', value: 'YEAR' },
  { label: '按月', value: 'MONTH' },
  { label: '按日', value: 'DAY' },
]

const filters = reactive<{
  keyword: string
  objectType?: CodingObjectType
  status?: MasterDataStatus
}>({
  keyword: '',
  objectType: undefined,
  status: undefined,
})
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<CodingRuleRecord[]>([])
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const formVisible = ref(false)
const formSubmitting = ref(false)
const formError = ref('')
const editingRecord = ref<CodingRuleRecord | null>(null)
const detailVisible = ref(false)
const detailRecord = ref<CodingRuleRecord | null>(null)
const form = reactive<{
  ruleCode: string
  name: string
  objectType: CodingObjectType | ''
  prefix: string
  datePattern: CodingDatePattern
  serialLength: string
  resetCycle: CodingResetCycle
  nextSerialNo: string
  status: MasterDataStatus
  remark: string
}>({
  ruleCode: '',
  name: '',
  objectType: '',
  prefix: '',
  datePattern: 'NONE',
  serialLength: '',
  resetCycle: 'NEVER',
  nextSerialNo: '',
  status: 'ENABLED',
  remark: '',
})

const canCreate = computed(() => authStore.hasPermission('master:coding-rule:create'))
const canUpdate = computed(() => authStore.hasPermission('master:coding-rule:update'))
const canEnable = computed(() => authStore.hasPermission('master:coding-rule:enable'))
const canDisable = computed(() => authStore.hasPermission('master:coding-rule:disable'))

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await masterDataApi.codingRules.list({
      keyword: filters.keyword,
      objectType: filters.objectType,
      status: filters.status,
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
  filters.objectType = undefined
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

function resetForm(record?: CodingRuleRecord) {
  Object.assign(form, {
    ruleCode: record?.ruleCode ?? '',
    name: record?.name ?? '',
    objectType: record?.objectType ?? '',
    prefix: record?.prefix ?? '',
    datePattern: record?.datePattern ?? 'NONE',
    serialLength: record ? String(record.serialLength) : '',
    resetCycle: record?.resetCycle ?? 'NEVER',
    nextSerialNo: record ? String(record.nextSerialNo) : '',
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

function openEdit(record: CodingRuleRecord) {
  editingRecord.value = record
  resetForm(record)
  formVisible.value = true
}

function openDetail(record: CodingRuleRecord) {
  detailRecord.value = record
  detailVisible.value = true
}

function validateForm(): CodingRulePayload | null {
  if (!form.ruleCode.trim() || !form.name.trim() || !form.objectType) {
    formError.value = '请完整填写规则编码、名称和对象类型'
    return null
  }
  const serialLength = Number(form.serialLength)
  if (!Number.isInteger(serialLength) || serialLength <= 0) {
    formError.value = '流水长度必须为正整数'
    return null
  }
  const nextSerialNo = Number(form.nextSerialNo)
  if (!Number.isInteger(nextSerialNo) || nextSerialNo <= 0) {
    formError.value = '下一个流水号必须为正整数'
    return null
  }
  const payload: CodingRulePayload = {
    ruleCode: form.ruleCode.trim(),
    name: form.name.trim(),
    objectType: form.objectType,
    prefix: form.prefix.trim(),
    datePattern: form.datePattern,
    serialLength,
    resetCycle: form.resetCycle,
    nextSerialNo,
    status: form.status,
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
      await masterDataApi.codingRules.update(editingRecord.value.id, payload)
    } else {
      await masterDataApi.codingRules.create(payload)
    }
    formVisible.value = false
    await loadRecords()
  } catch (caught) {
    formError.value = errorMessage(caught)
  } finally {
    formSubmitting.value = false
  }
}

async function enableRecord(record: CodingRuleRecord) {
  if (!(await confirmAction(`确认启用编码规则“${record.name}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await masterDataApi.codingRules.enable(record.id, { version: record.version })
    await loadRecords()
  } catch (caught) {
    actionError.value = errorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function disableRecord(record: CodingRuleRecord) {
  if (!(await confirmAction(`确认停用编码规则“${record.name}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await masterDataApi.codingRules.disable(record.id, { version: record.version })
    await loadRecords()
  } catch (caught) {
    actionError.value = errorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function formatDateTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="编码规则" description="维护固定对象的后端编码生成规则和状态。">
    <template #actions>
      <el-button v-if="canCreate" data-test="create-coding-rule" type="primary" @click="openCreate">
        新增编码规则
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="coding-rule-keyword" clearable placeholder="规则编码或名称" />
        </el-form-item>
        <el-form-item label="对象类型">
          <el-select v-model="filters.objectType" clearable placeholder="全部对象">
            <el-option v-for="option in objectTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-coding-rule" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-coding-rule" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="编码规则加载中" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无编码规则'" stripe>
        <el-table-column prop="ruleCode" label="规则编码" min-width="140" show-overflow-tooltip />
        <el-table-column prop="name" label="规则名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="对象类型" min-width="130">
          <template #default="{ row }">{{ codingObjectTypeLabel(row.objectType) }}</template>
        </el-table-column>
        <el-table-column prop="prefix" label="前缀" min-width="100" show-overflow-tooltip />
        <el-table-column label="日期段" min-width="110">
          <template #default="{ row }">{{ codingDatePatternLabel(row.datePattern) }}</template>
        </el-table-column>
        <el-table-column prop="serialLength" label="流水长度" min-width="100" />
        <el-table-column label="重置周期" min-width="110">
          <template #default="{ row }">{{ codingResetCycleLabel(row.resetCycle) }}</template>
        </el-table-column>
        <el-table-column prop="nextSerialNo" label="下一个流水号" min-width="120" />
        <el-table-column label="状态" min-width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ masterStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastGeneratedCode" label="最后生成编码" min-width="160" show-overflow-tooltip />
        <el-table-column label="最后生成时间" min-width="150">
          <template #default="{ row }">{{ formatDateTime(row.lastGeneratedAt) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="150">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-coding-rule" @click="openDetail(row)">详情</el-button>
            <el-button v-if="canUpdate" size="small" text data-test="edit-coding-rule" @click="openEdit(row)">
              编辑
            </el-button>
            <el-dropdown trigger="click" class="table-actions-more" v-if="(canEnable && row.status === 'DISABLED') || (canDisable && row.status === 'ENABLED')">
              <el-button size="small" text>更多</el-button>
              <template #dropdown>
                <el-dropdown-menu class="table-actions-more-menu">
                  <el-button
                    v-if="canEnable && row.status === 'DISABLED'"
                    size="small"
                    text
                    type="success"
                    :disabled="actionLoading"
                    data-test="enable-coding-rule"
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
                    data-test="disable-coding-rule"
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

    <el-dialog v-model="formVisible" :title="editingRecord ? '编辑编码规则' : '新增编码规则'" width="min(760px, 96vw)">
      <el-alert v-if="formError" class="form-alert" type="error" :title="formError" :closable="false" />
      <div class="coding-rule-option-summary">可选对象：物料、客户、供应商、BOM、BOM 工程变更</div>
      <el-form label-position="top">
        <div class="coding-rule-form-grid">
          <el-form-item label="规则编码">
            <el-input v-model="form.ruleCode" name="coding-rule-code" :disabled="Boolean(editingRecord)" />
          </el-form-item>
          <el-form-item label="规则名称">
            <el-input v-model="form.name" name="coding-rule-name" />
          </el-form-item>
          <el-form-item label="对象类型">
            <el-select
              v-model="form.objectType"
              data-test="coding-rule-object-type"
              placeholder="请选择对象类型"
              style="width: 100%"
            >
              <el-option v-for="option in objectTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="前缀">
            <el-input v-model="form.prefix" name="coding-rule-prefix" />
          </el-form-item>
          <el-form-item label="日期段">
            <el-select v-model="form.datePattern" data-test="coding-rule-date-pattern" style="width: 100%">
              <el-option v-for="option in datePatternOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="流水长度">
            <el-input v-model="form.serialLength" name="coding-rule-serial-length" inputmode="numeric" />
          </el-form-item>
          <el-form-item label="重置周期">
            <el-select v-model="form.resetCycle" data-test="coding-rule-reset-cycle" style="width: 100%">
              <el-option v-for="option in resetCycleOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="下一个流水号">
            <el-input v-model="form.nextSerialNo" name="coding-rule-next-serial-no" inputmode="numeric" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="form.status" style="width: 100%">
              <el-option label="启用" value="ENABLED" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="备注">
          <el-input v-model="form.remark" name="coding-rule-remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button
          data-test="submit-coding-rule"
          type="primary"
          :loading="formSubmitting"
          :disabled="formSubmitting"
          @click="saveRecord"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="编码规则详情" size="min(520px, 92vw)">
      <dl v-if="detailRecord" class="coding-rule-detail-list">
        <dt>规则编码</dt>
        <dd>{{ detailRecord.ruleCode }}</dd>
        <dt>规则名称</dt>
        <dd>{{ detailRecord.name }}</dd>
        <dt>对象类型</dt>
        <dd>{{ codingObjectTypeLabel(detailRecord.objectType) }}</dd>
        <dt>规则组成</dt>
        <dd>{{ detailRecord.prefix || '无前缀' }} / {{ codingDatePatternLabel(detailRecord.datePattern) }} / {{ detailRecord.serialLength }} 位流水</dd>
        <dt>重置周期</dt>
        <dd>{{ codingResetCycleLabel(detailRecord.resetCycle) }}</dd>
        <dt>下一个流水号</dt>
        <dd>{{ detailRecord.nextSerialNo }}</dd>
        <dt>最后生成</dt>
        <dd>{{ detailRecord.lastGeneratedCode || '未生成' }}</dd>
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
.coding-rule-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.coding-rule-option-summary {
  color: var(--qherp-muted);
  font-size: 13px;
  margin-bottom: 10px;
}

.coding-rule-detail-list {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  gap: 10px 14px;
  margin: 0;
}

.coding-rule-detail-list dt {
  color: var(--qherp-muted);
}

.coding-rule-detail-list dd {
  min-width: 0;
  margin: 0;
  word-break: break-word;
}

@media (max-width: 760px) {
  .coding-rule-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
