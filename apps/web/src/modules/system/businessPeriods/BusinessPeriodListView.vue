<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  businessPeriodApi,
  type BusinessPeriodPayload,
  type BusinessPeriodRecord,
  type BusinessPeriodStatus,
} from '../../../shared/api/businessPeriodApi'
import { useAuthStore } from '../../../stores/authStore'
import { errorMessage, pageItems } from '../shared/pageHelpers'
import { confirmAction } from '../../../shared/ui/confirmDialog'
import BusinessPeriodFormDialog from './BusinessPeriodFormDialog.vue'
import BusinessPeriodStatusTag from './BusinessPeriodStatusTag.vue'

type PeriodAction = 'lock' | 'unlock'

const authStore = useAuthStore()
const filters = reactive<{
  periodCode: string
  status?: BusinessPeriodStatus
  startDate: string
  endDate: string
}>({
  periodCode: '',
  status: undefined,
  startDate: '',
  endDate: '',
})
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
})
const periods = ref<BusinessPeriodRecord[]>([])
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const formVisible = ref(false)
const formSubmitting = ref(false)
const formError = ref('')
const editingPeriod = ref<BusinessPeriodRecord | null>(null)
const generateVisible = ref(false)
const generateSubmitting = ref(false)
const generateError = ref('')
const generateForm = reactive({
  startMonth: '',
  endMonth: '',
})
const actionVisible = ref(false)
const actionSubmitting = ref(false)
const actionTarget = ref<BusinessPeriodRecord | null>(null)
const actionType = ref<PeriodAction>('lock')
const actionForm = reactive({
  reason: '',
})

const canCreate = computed(() => authStore.hasPermission('system:business-period:create'))
const canUpdate = computed(() => authStore.hasPermission('system:business-period:update'))
const canLock = computed(() => authStore.hasPermission('system:business-period:lock'))
const canUnlock = computed(() => authStore.hasPermission('system:business-period:unlock'))
const actionTitle = computed(() => actionType.value === 'lock' ? '锁定业务期间' : '解锁业务期间')

async function loadPeriods() {
  loading.value = true
  error.value = ''
  try {
    const page = await businessPeriodApi.list({
      periodCode: filters.periodCode,
      status: filters.status,
      startDate: filters.startDate,
      endDate: filters.endDate,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    periods.value = pageItems(page)
    pagination.total = Number(page.total)
  } catch (caught) {
    periods.value = []
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

function search() {
  pagination.page = 1
  void loadPeriods()
}

function resetSearch() {
  filters.periodCode = ''
  filters.status = undefined
  filters.startDate = ''
  filters.endDate = ''
  pagination.page = 1
  void loadPeriods()
}

function changePage(page: number) {
  pagination.page = page
  void loadPeriods()
}

function changePageSize(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1
  void loadPeriods()
}

function openCreate() {
  editingPeriod.value = null
  formError.value = ''
  formVisible.value = true
}

function openEdit(period: BusinessPeriodRecord) {
  editingPeriod.value = period
  formError.value = ''
  formVisible.value = true
}

async function savePeriod(payload: BusinessPeriodPayload) {
  if (formSubmitting.value) {
    return
  }
  formSubmitting.value = true
  formError.value = ''
  try {
    if (editingPeriod.value) {
      await businessPeriodApi.update(editingPeriod.value.id, payload)
    } else {
      await businessPeriodApi.create(payload)
    }
    formVisible.value = false
    await loadPeriods()
  } catch (caught) {
    formError.value = errorMessage(caught)
  } finally {
    formSubmitting.value = false
  }
}

function openGenerate() {
  generateForm.startMonth = ''
  generateForm.endMonth = ''
  generateError.value = ''
  generateVisible.value = true
}

async function submitGenerate() {
  generateError.value = ''
  if (!generateForm.startMonth || !generateForm.endMonth) {
    generateError.value = '请完整选择起止月份'
    return
  }
  if (generateForm.startMonth > generateForm.endMonth) {
    generateError.value = '开始月份不能晚于结束月份'
    return
  }
  generateSubmitting.value = true
  try {
    await businessPeriodApi.generateMonthly({
      startMonth: generateForm.startMonth,
      endMonth: generateForm.endMonth,
    })
    generateVisible.value = false
    await loadPeriods()
  } catch (caught) {
    generateError.value = errorMessage(caught)
  } finally {
    generateSubmitting.value = false
  }
}

function openAction(period: BusinessPeriodRecord, type: PeriodAction) {
  actionTarget.value = period
  actionType.value = type
  actionForm.reason = ''
  actionError.value = ''
  actionVisible.value = true
}

async function submitAction() {
  if (!actionTarget.value || actionSubmitting.value) {
    return
  }
  actionError.value = ''
  const reason = actionForm.reason.trim()
  if (!reason) {
    actionError.value = '请填写操作原因'
    return
  }
  const confirmMessage = actionType.value === 'lock'
    ? `确认锁定业务期间“${actionTarget.value.periodCode}”？锁定后该期间业务日期的写入会被拒绝。`
    : `确认解锁业务期间“${actionTarget.value.periodCode}”？解锁后该期间允许符合规则的业务写入。`
  if (!(await confirmAction(confirmMessage))) {
    return
  }

  actionSubmitting.value = true
  try {
    if (actionType.value === 'lock') {
      await businessPeriodApi.lock(actionTarget.value.id, { reason })
    } else {
      await businessPeriodApi.unlock(actionTarget.value.id, { reason })
    }
    actionVisible.value = false
    await loadPeriods()
  } catch (caught) {
    actionError.value = errorMessage(caught)
  } finally {
    actionSubmitting.value = false
  }
}

onMounted(loadPeriods)
</script>

<template>
  <section class="module-page">
    <header class="page-heading">
      <div>
        <h1>业务期间</h1>
        <p>维护业务日期所属期间，并控制锁定期间内影响经营口径的写入。</p>
      </div>
      <div class="page-heading-actions">
        <el-button v-if="canCreate" data-test="generate-business-periods" @click="openGenerate">按月生成</el-button>
        <el-button v-if="canCreate" data-test="create-business-period" type="primary" @click="openCreate">
          新增业务期间
        </el-button>
      </div>
    </header>

    <el-card class="query-card" shadow="never">
      <el-form class="query-form" inline>
        <el-form-item label="期间编码">
          <el-input v-model="filters.periodCode" name="business-period-code" clearable placeholder="例如 2026-07" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="开放" value="OPEN" />
            <el-option label="已锁定" value="LOCKED" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker
            v-model="filters.startDate"
            name="business-period-start-date"
            type="date"
            value-on-clear=""
            value-format="YYYY-MM-DD"
            placeholder="起始日期"
          />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker
            v-model="filters.endDate"
            name="business-period-end-date"
            type="date"
            value-on-clear=""
            value-format="YYYY-MM-DD"
            placeholder="截止日期"
          />
        </el-form-item>
        <el-form-item>
          <el-button data-test="business-period-search" type="primary" @click="search">查询</el-button>
          <el-button data-test="business-period-reset" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
    <el-alert v-if="loading" class="state-alert" type="info" title="业务期间数据加载中" :closable="false" />

    <el-card class="table-card" shadow="never">
      <div class="table-scroll">
        <el-table :data="periods" :empty-text="loading ? '加载中' : '暂无业务期间数据'" stripe>
          <el-table-column prop="periodCode" label="期间编码" min-width="120" />
          <el-table-column prop="periodName" label="期间名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="startDate" label="开始日期" min-width="120" />
          <el-table-column prop="endDate" label="结束日期" min-width="120" />
          <el-table-column label="状态" min-width="100">
            <template #default="{ row }">
              <BusinessPeriodStatusTag :status="row.status" :status-name="row.statusName" />
            </template>
          </el-table-column>
          <el-table-column prop="lockedBy" label="锁定人" min-width="110" show-overflow-tooltip />
          <el-table-column prop="lockedAt" label="锁定时间" min-width="180" show-overflow-tooltip />
          <el-table-column prop="lockReason" label="锁定原因" min-width="220" show-overflow-tooltip />
          <el-table-column label="操作" fixed="right" min-width="210">
            <template #default="{ row }">
              <el-button
                v-if="canUpdate && row.status === 'OPEN'"
                size="small"
                text
                data-test="edit-business-period"
                @click="openEdit(row)"
              >
                编辑
              </el-button>
              <el-button
                v-if="canLock && row.status === 'OPEN'"
                size="small"
                text
                type="warning"
                data-test="lock-business-period"
                @click="openAction(row, 'lock')"
              >
                锁定
              </el-button>
              <el-button
                v-if="canUnlock && row.status === 'LOCKED'"
                size="small"
                text
                type="primary"
                data-test="unlock-business-period"
                @click="openAction(row, 'unlock')"
              >
                解锁
              </el-button>
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
    </el-card>

    <BusinessPeriodFormDialog
      v-model="formVisible"
      :period="editingPeriod"
      :submitting="formSubmitting"
      :error="formError"
      @submit="savePeriod"
    />

    <el-dialog v-model="generateVisible" title="按月生成业务期间" width="520px">
      <el-alert v-if="generateError" class="form-alert" type="error" :title="generateError" :closable="false" />
      <el-form label-position="top">
        <el-form-item label="开始月份">
          <el-date-picker
            v-model="generateForm.startMonth"
            name="generate-start-month"
            type="month"
            value-on-clear=""
            value-format="YYYY-MM"
            placeholder="选择开始月份"
          />
        </el-form-item>
        <el-form-item label="结束月份">
          <el-date-picker
            v-model="generateForm.endMonth"
            name="generate-end-month"
            type="month"
            value-on-clear=""
            value-format="YYYY-MM"
            placeholder="选择结束月份"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateVisible = false">取消</el-button>
        <el-button
          data-test="submit-generate-business-periods"
          type="primary"
          :loading="generateSubmitting"
          :disabled="generateSubmitting"
          @click="submitGenerate"
        >
          生成
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="actionVisible" :title="actionTitle" width="520px">
      <el-alert v-if="actionError" class="form-alert" type="error" :title="actionError" :closable="false" />
      <p class="dialog-summary">当前期间：{{ actionTarget?.periodCode }} {{ actionTarget?.periodName }}</p>
      <el-form label-position="top">
        <el-form-item label="操作原因">
          <el-input
            v-model="actionForm.reason"
            name="period-action-reason"
            type="textarea"
            :rows="4"
            maxlength="300"
            show-word-limit
            placeholder="请填写锁定或解锁原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionVisible = false">取消</el-button>
        <el-button
          data-test="submit-period-action"
          type="primary"
          :loading="actionSubmitting"
          :disabled="actionSubmitting"
          @click="submitAction"
        >
          确定
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>
