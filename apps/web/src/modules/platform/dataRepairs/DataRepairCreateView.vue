<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  platformGovernanceApi,
  type DataRepairAdapterRecord,
  type DataRepairDetail,
} from '../../../shared/api/platformGovernanceApi'
import { createIdempotencyKey } from '../../../shared/api/documentPlatformApi'
import { useAuthStore } from '../../../stores/authStore'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { masterStatusLabel } from '../../master/shared/masterPageHelpers'
import { statusTagType } from '../../system/shared/pageHelpers'
import { platformErrorMessage } from '../platformPageHelpers'
import {
  candidateMissingVersionMessage,
  hasStableCandidateVersion,
  listPlatformTargetCandidates,
  stableCandidateVersion,
  type PlatformTargetCandidate,
} from '../platformTargetCandidates'

const authStore = useAuthStore()
const adapters = ref<DataRepairAdapterRecord[]>([])
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const formError = ref('')
const createdDetail = ref<DataRepairDetail | null>(null)
const form = reactive({
  adapterCode: '',
  targetKeyword: '',
  title: '',
  reason: '',
  changes: {} as Record<string, string>,
})
const targetCandidates = ref<PlatformTargetCandidate[]>([])
const selectedTarget = ref<PlatformTargetCandidate | null>(null)
const targetLoading = ref(false)
const targetError = ref('')
const targetPagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0,
  totalPages: 0,
})

const canCreate = computed(() => authStore.hasPermission('platform:data-repair:create'))
const selectedAdapter = computed(() => adapters.value.find((item) => item.adapterCode === form.adapterCode) ?? null)
const allowedFields = computed(() => selectedAdapter.value?.allowedFields ?? [])
const canLoadMoreTargets = computed(() => (
  targetPagination.totalPages
    ? targetPagination.page < targetPagination.totalPages
    : targetCandidates.value.length < targetPagination.total
))

async function loadAdapters() {
  loading.value = true
  error.value = ''
  try {
    adapters.value = await platformGovernanceApi.dataRepairAdapters.list()
    if (!form.adapterCode && adapters.value.length > 0) {
      form.adapterCode = adapters.value[0].adapterCode
    }
  } catch (caught) {
    adapters.value = []
    error.value = platformErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function resetChangeFields() {
  const next: Record<string, string> = {}
  allowedFields.value.forEach((fieldCode) => {
    next[fieldCode] = form.changes[fieldCode] ?? ''
  })
  form.changes = next
}

function resetTargetSelection() {
  selectedTarget.value = null
  targetCandidates.value = []
  targetError.value = ''
  form.targetKeyword = ''
  targetPagination.page = 1
  targetPagination.total = 0
  targetPagination.totalPages = 0
}

async function loadTargetCandidates(page = 1, append = false) {
  if (!selectedAdapter.value) {
    targetError.value = '请选择数据修复适配器'
    return
  }
  targetLoading.value = true
  targetError.value = ''
  try {
    const result = await listPlatformTargetCandidates(selectedAdapter.value.targetObjectType, {
      keyword: form.targetKeyword.trim(),
      page,
      pageSize: targetPagination.pageSize,
    })
    targetCandidates.value = append
      ? [...targetCandidates.value, ...(result.items ?? [])]
      : (result.items ?? [])
    targetPagination.page = Number(result.page ?? page)
    targetPagination.pageSize = Number(result.pageSize ?? targetPagination.pageSize)
    targetPagination.total = Number(result.total ?? targetCandidates.value.length)
    targetPagination.totalPages = Number(result.totalPages ?? 0)
    const missingVersion = targetCandidates.value.find((candidate) => !hasStableCandidateVersion(candidate))
    if (missingVersion) {
      targetError.value = candidateMissingVersionMessage(missingVersion)
    }
  } catch (caught) {
    targetCandidates.value = append ? targetCandidates.value : []
    targetError.value = platformErrorMessage(caught)
  } finally {
    targetLoading.value = false
  }
}

function searchTargetCandidates() {
  selectedTarget.value = null
  void loadTargetCandidates(1, false)
}

function loadMoreTargetCandidates() {
  if (!canLoadMoreTargets.value || targetLoading.value) {
    return
  }
  void loadTargetCandidates(targetPagination.page + 1, true)
}

function selectTargetCandidate(candidate: PlatformTargetCandidate) {
  if (!hasStableCandidateVersion(candidate)) {
    targetError.value = candidateMissingVersionMessage(candidate)
    return
  }
  selectedTarget.value = candidate
  formError.value = ''
}

function validatePayload() {
  if (!canCreate.value) {
    formError.value = '缺少数据修复创建权限'
    return null
  }
  if (!selectedAdapter.value) {
    formError.value = '请选择数据修复适配器'
    return null
  }
  const targetObjectVersion = selectedTarget.value ? stableCandidateVersion(selectedTarget.value) : null
  if (!selectedTarget.value || targetObjectVersion === null) {
    formError.value = '请选择带稳定版本的目标对象'
    return null
  }
  if (!form.title.trim() || !form.reason.trim()) {
    formError.value = '请填写修复标题和原因'
    return null
  }
  const changes = allowedFields.value
    .map((fieldCode) => ({
      fieldName: fieldCode,
      afterValue: form.changes[fieldCode]?.trim() || null,
    }))
    .filter((item) => item.afterValue !== null)
  if (changes.length === 0) {
    formError.value = '请至少填写一个修复字段'
    return null
  }
  return {
    adapterCode: selectedAdapter.value.adapterCode,
    targetObjectType: selectedAdapter.value.targetObjectType,
    targetObjectId: selectedTarget.value.id,
    targetVersion: targetObjectVersion,
    riskSummary: form.title.trim(),
    reason: form.reason.trim(),
    changes,
    idempotencyKey: createIdempotencyKey('data-repair-create'),
  }
}

async function submitCreate() {
  const payload = validatePayload()
  if (!payload || submitting.value) {
    return
  }
  submitting.value = true
  formError.value = ''
  createdDetail.value = null
  try {
    createdDetail.value = await platformGovernanceApi.dataRepairs.create(payload)
  } catch (caught) {
    formError.value = platformErrorMessage(caught)
  } finally {
    submitting.value = false
  }
}

watch(allowedFields, resetChangeFields)
watch(() => form.adapterCode, resetTargetSelection)

onMounted(() => {
  void loadAdapters()
})
</script>

<template>
  <MasterDataTableView title="新增修复申请" description="按冻结适配器字段创建受控数据修复申请，后续由审批、执行和验证链路处理。">
    <template #actions>
      <RouterLink to="/platform/data-repairs" custom v-slot="{ navigate }">
        <a data-test="back-data-repair-list" class="action-button-link" href="/platform/data-repairs" @click="navigate">
          <el-button tag="span">返回列表</el-button>
        </a>
      </RouterLink>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="formError" class="state-alert" type="error" :title="formError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="数据修复适配器加载中" :closable="false" />
      <el-alert v-if="!canCreate" class="state-alert" type="warning" title="缺少数据修复创建权限，当前页面只读" :closable="false" />
      <el-alert
        v-if="createdDetail"
        class="state-alert"
        type="success"
        :title="`已创建数据修复申请 ${createdDetail.requestNo || createdDetail.repairNo || createdDetail.id}`"
        :closable="false"
      />
    </template>

    <section class="section-block">
      <h2>申请信息</h2>
      <el-form label-position="top" class="data-repair-form">
        <div class="data-repair-form-grid">
          <el-form-item label="适配器">
            <el-select
              v-model="form.adapterCode"
              data-test="data-repair-create-adapter-code"
              placeholder="选择数据修复适配器"
              style="width: 100%"
              :disabled="!canCreate || loading"
            >
              <el-option
                v-for="adapter in adapters"
                :key="adapter.adapterCode"
                :label="adapter.name"
                :value="adapter.adapterCode"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="目标对象类型">
            <el-input :model-value="selectedAdapter?.targetObjectType || '-'" name="data-repair-target-object-type" disabled />
          </el-form-item>
          <el-form-item class="target-picker-field" label="目标对象">
            <div class="target-search-row">
              <el-input
                v-model="form.targetKeyword"
                name="data-repair-target-keyword"
                clearable
                placeholder="按编码或名称搜索目标对象"
                :disabled="!canCreate || !selectedAdapter"
              />
              <el-button
                data-test="search-data-repair-targets"
                :loading="targetLoading"
                :disabled="!canCreate || !selectedAdapter || targetLoading"
                @click="searchTargetCandidates"
              >
                搜索
              </el-button>
            </div>
            <el-alert v-if="targetError" class="state-alert" type="error" :title="targetError" :closable="false" />
            <div v-if="selectedTarget" class="target-selected-summary">
              已选目标 {{ selectedTarget.code }} {{ selectedTarget.name }} / ID {{ selectedTarget.id }} / V{{ selectedTarget.version }}
            </div>
            <div v-else class="target-selected-summary muted">请选择目标对象</div>
            <div class="table-scroll target-candidate-table">
              <el-table :data="targetCandidates" empty-text="暂无目标候选" stripe>
                <el-table-column prop="code" label="编码" min-width="130" show-overflow-tooltip />
                <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
                <el-table-column label="状态" min-width="90">
                  <template #default="{ row }">
                    <el-tag :type="statusTagType(row.status)" size="small">{{ masterStatusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="版本" width="90">
                  <template #default="{ row }">{{ row.version ?? '-' }}</template>
                </el-table-column>
                <el-table-column label="操作" fixed="right" width="184">
                  <template #default="{ row }">
                    <el-button
                      v-if="hasStableCandidateVersion(row)"
                      size="small"
                      text
                      :data-test="`select-data-repair-target-${row.id}`"
                      @click="selectTargetCandidate(row)"
                    >
                      选择
                    </el-button>
                    <span v-else class="candidate-error">缺少版本</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
            <el-button
              v-if="canLoadMoreTargets"
              data-test="load-more-data-repair-targets"
              class="load-more-button"
              :loading="targetLoading"
              :disabled="targetLoading"
              @click="loadMoreTargetCandidates"
            >
              继续加载
            </el-button>
          </el-form-item>
          <el-form-item label="修复标题">
            <el-input v-model="form.title" name="data-repair-title" maxlength="80" show-word-limit :disabled="!canCreate" />
          </el-form-item>
          <el-form-item label="修复原因">
            <el-input
              v-model="form.reason"
              name="data-repair-reason"
              type="textarea"
              :rows="3"
              maxlength="300"
              show-word-limit
              :disabled="!canCreate"
            />
          </el-form-item>
        </div>
      </el-form>
    </section>

    <section class="section-block">
      <h2>修复字段</h2>
      <el-alert
        v-if="selectedAdapter && allowedFields.length === 0"
        class="state-alert"
        type="warning"
        title="当前适配器未返回可修复字段"
        :closable="false"
      />
      <el-form v-else label-position="top" class="data-repair-form">
        <div class="data-repair-form-grid">
          <el-form-item v-for="fieldCode in allowedFields" :key="fieldCode" :label="fieldCode">
            <el-input
              v-model="form.changes[fieldCode]"
              :name="`data-repair-change-${fieldCode}`"
              :disabled="!canCreate"
              placeholder="填写修复后目标值"
            />
          </el-form-item>
        </div>
      </el-form>
    </section>

    <section class="section-block">
      <h2>提交</h2>
      <div class="form-footer">
        <el-button data-test="submit-data-repair-create" type="primary" :loading="submitting" :disabled="!canCreate || submitting" @click="submitCreate">
          创建申请
        </el-button>
      </div>
    </section>
  </MasterDataTableView>
</template>

<style scoped>
.section-block {
  margin-top: 16px;
}

.section-block h2 {
  margin: 0 0 12px;
  font-size: 16px;
}

.data-repair-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.target-picker-field {
  grid-column: 1 / -1;
}

.target-search-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  width: 100%;
}

.target-selected-summary {
  border: 1px solid var(--qherp-border);
  border-radius: 6px;
  margin-top: 10px;
  padding: 8px 10px;
}

.target-selected-summary.muted {
  color: var(--qherp-muted);
}

.target-candidate-table {
  margin-top: 10px;
  width: 100%;
}

.candidate-error {
  color: var(--el-color-danger);
  font-size: 12px;
}

.load-more-button {
  margin-top: 8px;
}

.form-footer {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 760px) {
  .data-repair-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
