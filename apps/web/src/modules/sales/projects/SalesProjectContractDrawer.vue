<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  salesProjectApi,
  type SalesProjectContractDetail,
  type SalesProjectContractType,
  type SalesProjectDetail,
} from '../../../shared/api/salesProjectApi'
import type { ResourceId } from '../../../shared/api/salesApi'
import { useAuthStore } from '../../../stores/authStore'
import SalesProjectContractStatusTag from './SalesProjectContractStatusTag.vue'
import {
  formatProjectDateTime,
  projectApiErrorMessage,
  salesProjectContractTypeLabel,
  validateProjectReason,
} from './salesProjectPageHelpers'

type DrawerMode = 'create' | 'edit' | 'view'

const props = withDefaults(defineProps<{
  modelValue: boolean
  mode?: DrawerMode
  project: SalesProjectDetail
  contractId?: ResourceId
  defaultContractType?: SalesProjectContractType
}>(), {
  mode: 'create',
  contractId: undefined,
  defaultContractType: 'MAIN',
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const authStore = useAuthStore()
const detail = ref<SalesProjectContractDetail | null>(null)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const formSnapshot = ref('')
const form = reactive({
  contractType: 'MAIN' as SalesProjectContractType,
  mainContractId: '' as ResourceId | '',
  externalContractNo: '',
  name: '',
  signedDate: '',
  effectiveStartDate: '',
  effectiveEndDate: '',
  amount: '',
  remark: '',
})
const actionDialog = reactive<{
  visible: boolean
  action: 'activate' | 'close' | 'terminate' | 'cancel' | ''
  title: string
  reason: string
  error: string
}>({
  visible: false,
  action: '',
  title: '',
  reason: '',
  error: '',
})

const isCreate = computed(() => props.mode === 'create')
const isView = computed(() => props.mode === 'view')
const drawerTitle = computed(() => (isCreate.value ? '新增项目合同' : '项目合同'))
const drawerSize = 'min(720px, calc(100vw - 24px))'
const canCreateContract = computed(() => authStore.hasPermission('sales:contract:create'))
const canUpdateContract = computed(() => authStore.hasPermission('sales:contract:update'))
const canEditFields = computed(() => !isView.value && (
  (isCreate.value && canCreateContract.value)
  || (!isCreate.value && canUpdateContract.value && detail.value?.status === 'DRAFT')
))
const canSave = computed(() => canEditFields.value)
const canActivate = computed(() => !isView.value && detail.value?.status === 'DRAFT'
  && authStore.hasPermission('sales:contract:activate'))
const canCancel = computed(() => !isView.value && detail.value?.status === 'DRAFT'
  && authStore.hasPermission('sales:contract:cancel'))
const canClose = computed(() => !isView.value && detail.value?.status === 'EFFECTIVE'
  && authStore.hasPermission('sales:contract:close'))
const canTerminate = computed(() => !isView.value && detail.value?.status === 'EFFECTIVE'
  && authStore.hasPermission('sales:contract:terminate'))
const formDirty = computed(() => Boolean(detail.value) && detail.value?.status === 'DRAFT'
  && formSnapshot.value !== contractFormSnapshot())

function contractFormSnapshot() {
  return JSON.stringify({
    contractType: form.contractType,
    mainContractId: form.mainContractId,
    externalContractNo: form.externalContractNo,
    name: form.name,
    signedDate: form.signedDate,
    effectiveStartDate: form.effectiveStartDate,
    effectiveEndDate: form.effectiveEndDate,
    amount: form.amount,
    remark: form.remark,
  })
}

function resetForm() {
  detail.value = null
  form.contractType = props.defaultContractType
  form.mainContractId = props.defaultContractType === 'SUPPLEMENT' ? props.project.mainContractId ?? '' : ''
  form.externalContractNo = ''
  form.name = ''
  form.signedDate = ''
  form.effectiveStartDate = ''
  form.effectiveEndDate = ''
  form.amount = ''
  form.remark = ''
  error.value = ''
  formSnapshot.value = contractFormSnapshot()
}

function fillForm(contract: SalesProjectContractDetail) {
  form.contractType = contract.contractType
  form.mainContractId = contract.mainContractId ?? ''
  form.externalContractNo = contract.externalContractNo ?? ''
  form.name = contract.name
  form.signedDate = contract.signedDate
  form.effectiveStartDate = contract.effectiveStartDate ?? ''
  form.effectiveEndDate = contract.effectiveEndDate ?? ''
  form.amount = contract.amount
  form.remark = contract.remark ?? ''
  formSnapshot.value = contractFormSnapshot()
}

async function loadContract() {
  resetForm()
  if (!props.modelValue || isCreate.value || props.contractId === undefined) {
    return
  }
  loading.value = true
  try {
    const contract = await salesProjectApi.contracts.get(props.contractId)
    detail.value = contract
    fillForm(contract)
  } catch (caught) {
    error.value = projectApiErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function validateForm() {
  if (!form.name.trim() || !form.signedDate || !form.amount.trim()) {
    error.value = '请完整填写合同名称、签订日期和金额'
    return false
  }
  const amount = Number(form.amount)
  if (!Number.isFinite(amount)) {
    error.value = '合同金额必须是普通十进制数字'
    return false
  }
  if (form.contractType === 'MAIN' && amount <= 0) {
    error.value = '主合同金额必须大于 0'
    return false
  }
  if (form.contractType === 'SUPPLEMENT' && amount === 0) {
    error.value = '补充合同金额不得为 0'
    return false
  }
  error.value = ''
  return true
}

async function saveContract() {
  if (saving.value || !validateForm()) {
    return
  }
  saving.value = true
  try {
    const common = {
      externalContractNo: form.externalContractNo.trim(),
      name: form.name.trim(),
      signedDate: form.signedDate,
      ...(form.effectiveStartDate ? { effectiveStartDate: form.effectiveStartDate } : {}),
      ...(form.effectiveEndDate ? { effectiveEndDate: form.effectiveEndDate } : {}),
      amount: form.amount.trim(),
      ...(form.remark.trim() ? { remark: form.remark.trim() } : {}),
    }
    if (isCreate.value) {
      await salesProjectApi.contracts.create(props.project.id, {
        contractType: form.contractType,
        mainContractId: form.contractType === 'SUPPLEMENT' ? props.project.mainContractId ?? form.mainContractId : null,
        ...common,
      })
    } else if (detail.value) {
      await salesProjectApi.contracts.update(detail.value.id, {
        version: detail.value.version,
        ...common,
      })
    }
    emit('saved')
    emit('update:modelValue', false)
  } catch (caught) {
    error.value = projectApiErrorMessage(caught)
  } finally {
    saving.value = false
  }
}

function openContractAction(action: 'activate' | 'close' | 'terminate' | 'cancel') {
  if (action === 'activate' && formDirty.value) {
    error.value = '请先保存合同草稿后再生效'
    return
  }
  const titles = {
    activate: '合同生效',
    close: '关闭合同',
    terminate: '终止合同',
    cancel: '取消合同',
  }
  actionDialog.visible = true
  actionDialog.action = action
  actionDialog.title = titles[action]
  actionDialog.reason = ''
  actionDialog.error = ''
}

async function confirmContractAction() {
  if (!detail.value || !actionDialog.action || saving.value) {
    return
  }
  const reasonRequired = actionDialog.action !== 'activate'
  const reasonError = reasonRequired ? validateProjectReason(actionDialog.reason) : ''
  if (reasonError) {
    actionDialog.error = reasonError
    return
  }
  saving.value = true
  try {
    const payload = {
      version: detail.value.version,
      ...(reasonRequired ? { reason: actionDialog.reason.trim() } : {}),
    }
    if (actionDialog.action === 'activate') {
      await salesProjectApi.contracts.activate(detail.value.id, payload)
    } else if (actionDialog.action === 'close') {
      await salesProjectApi.contracts.close(detail.value.id, payload)
    } else if (actionDialog.action === 'terminate') {
      await salesProjectApi.contracts.terminate(detail.value.id, payload)
    } else {
      await salesProjectApi.contracts.cancel(detail.value.id, payload)
    }
    actionDialog.visible = false
    emit('saved')
    emit('update:modelValue', false)
  } catch (caught) {
    actionDialog.error = projectApiErrorMessage(caught)
  } finally {
    saving.value = false
  }
}

watch(() => [props.modelValue, props.mode, props.contractId, props.defaultContractType], () => {
  void loadContract()
}, { immediate: true })
</script>

<template>
  <el-drawer :model-value="modelValue" :title="drawerTitle" :teleported="false" :size="drawerSize" @update:model-value="$emit('update:modelValue', $event)">
    <div class="contract-drawer-body">
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="合同加载中" :closable="false" />
      <div v-if="detail" class="contract-status-line">
        <span>{{ detail.contractNo }}</span>
        <SalesProjectContractStatusTag :status="detail.status" />
        <span>版本 {{ detail.version }}</span>
      </div>
      <el-form label-position="top" class="contract-form">
        <div class="contract-form-grid">
          <el-form-item label="合同类型">
            <el-select v-model="form.contractType" :disabled="!isCreate || !canEditFields">
              <el-option label="主合同" value="MAIN" />
              <el-option label="补充合同" value="SUPPLEMENT" />
            </el-select>
          </el-form-item>
          <el-form-item label="引用主合同">
            <el-input :model-value="form.contractType === 'SUPPLEMENT' ? (project.mainContractNo || '主合同') : '-'" disabled />
          </el-form-item>
          <el-form-item label="合同名称">
            <el-input v-model="form.name" name="contract-name" :disabled="!canEditFields" />
          </el-form-item>
          <el-form-item label="外部纸质合同号">
            <el-input v-model="form.externalContractNo" name="contract-external-no" :disabled="!canEditFields" />
          </el-form-item>
          <el-form-item label="签订日期">
            <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="form.signedDate" name="contract-signed-date" :disabled="!canEditFields" />
          </el-form-item>
          <el-form-item label="合同金额">
            <el-input v-model="form.amount" name="contract-amount" :disabled="!canEditFields" />
          </el-form-item>
          <el-form-item label="履约开始">
            <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="form.effectiveStartDate" name="contract-effective-start-date" :disabled="!canEditFields" />
          </el-form-item>
          <el-form-item label="履约结束">
            <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="form.effectiveEndDate" name="contract-effective-end-date" :disabled="!canEditFields" />
          </el-form-item>
        </div>
        <el-form-item label="备注">
          <el-input v-model="form.remark" name="contract-remark" type="textarea" :rows="3" :disabled="!canEditFields" />
        </el-form-item>
      </el-form>
      <dl v-if="detail" class="contract-lifecycle">
        <dt>创建</dt>
        <dd>{{ detail.createdByName || '-' }} {{ formatProjectDateTime(detail.createdAt) }}</dd>
        <template v-if="detail.activatedByName || detail.activatedAt">
          <dt>生效</dt>
          <dd>{{ detail.activatedByName || '-' }} {{ formatProjectDateTime(detail.activatedAt) }}</dd>
        </template>
        <template v-if="detail.closedByName || detail.closedAt || detail.closedReason">
          <dt>关闭</dt>
          <dd>{{ detail.closedByName || '-' }} {{ formatProjectDateTime(detail.closedAt) }} {{ detail.closedReason || '' }}</dd>
        </template>
        <template v-if="detail.terminatedByName || detail.terminatedAt || detail.terminatedReason">
          <dt>终止</dt>
          <dd>{{ detail.terminatedByName || '-' }} {{ formatProjectDateTime(detail.terminatedAt) }} {{ detail.terminatedReason || '' }}</dd>
        </template>
        <template v-if="detail.cancelledByName || detail.cancelledAt || detail.cancelledReason">
          <dt>取消</dt>
          <dd>{{ detail.cancelledByName || '-' }} {{ formatProjectDateTime(detail.cancelledAt) }} {{ detail.cancelledReason || '' }}</dd>
        </template>
      </dl>
    </div>
    <template #footer>
      <div class="drawer-footer">
        <div class="drawer-actions">
          <el-button v-if="canActivate" data-test="contract-action-activate" type="success" plain @click="openContractAction('activate')">生效</el-button>
          <el-button v-if="canCancel" data-test="contract-action-cancel" type="danger" plain @click="openContractAction('cancel')">取消</el-button>
          <el-button v-if="canClose" data-test="contract-action-close" type="warning" plain @click="openContractAction('close')">关闭</el-button>
          <el-button v-if="canTerminate" data-test="contract-action-terminate" type="danger" plain @click="openContractAction('terminate')">终止</el-button>
        </div>
        <div class="drawer-actions">
          <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
          <el-button v-if="canSave" data-test="save-sales-project-contract" type="primary" :loading="saving" :disabled="saving || !canEditFields" @click="saveContract">保存</el-button>
        </div>
      </div>
    </template>

    <el-dialog v-model="actionDialog.visible" :title="actionDialog.title" :teleported="false" width="420px">
      <el-alert v-if="actionDialog.error" class="state-alert" type="error" :title="actionDialog.error" :closable="false" />
      <el-input
        v-if="actionDialog.action !== 'activate'"
        v-model="actionDialog.reason"
        name="contract-action-reason"
        type="textarea"
        :rows="4"
        maxlength="200"
        show-word-limit
        placeholder="请输入 1-200 字原因"
      />
      <p v-else>确认{{ salesProjectContractTypeLabel(form.contractType) }}生效？</p>
      <template #footer>
        <el-button @click="actionDialog.visible = false">取消</el-button>
        <el-button data-test="confirm-contract-action" type="primary" :loading="saving" @click="confirmContractAction">确认</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<style scoped>
.contract-status-line {
  align-items: center;
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.contract-drawer-body {
  max-height: calc(100vh - 178px);
  overflow: auto;
  padding-right: 2px;
}

.contract-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.contract-lifecycle {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 8px 12px;
  margin: 12px 0 0;
}

.contract-lifecycle dt {
  color: var(--qherp-muted);
}

.contract-lifecycle dd {
  margin: 0;
  min-width: 0;
  overflow-wrap: anywhere;
}

.drawer-footer {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.drawer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 760px) {
  .contract-form-grid {
    grid-template-columns: 1fr;
  }

  .drawer-footer {
    align-items: stretch;
    flex-direction: column;
  }
}

@media (max-width: 390px) {
  .drawer-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
