<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { glApi, type GlAuxCandidateRecord, type GlAuxDimensionRecord } from '../../shared/api/glApi'
import { confirmAction } from '../../shared/ui/confirmDialog'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { createGlIdempotencyKey, glActionAllowed, glActionDisabledReason, glAuxDimensionTypeText, glErrorMessage, glPageItems, glPageSizes, glPageTotal } from './glPageHelpers'
import './GlShared.css'

const filters = reactive({ keyword: '', enabled: '' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<GlAuxDimensionRecord[]>([])
const candidates = ref<GlAuxCandidateRecord[]>([])
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')
const actionMessage = ref('')
const candidateDrawerVisible = ref(false)
const dimensionDialogVisible = ref(false)
const itemDialogVisible = ref(false)
const selectedDimension = ref<GlAuxDimensionRecord | null>(null)
const selectedItem = ref<GlAuxCandidateRecord | null>(null)
const candidateKeyword = ref('')
const candidatePagination = reactive({ page: 1, pageSize: 20, total: 0 })
const dimensionForm = reactive({ code: '', name: '', enabled: true, version: 0 })
const itemForm = reactive({ code: '', name: '', enabled: true, version: 0 })
const selectedDimensionIsCustom = computed(() => selectedDimension.value?.dimensionType === 'CUSTOM')
const dimensionFormIsSystem = computed(() => selectedDimension.value?.dimensionType === 'SYSTEM')

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await glApi.auxDimensions.list({
      keyword: filters.keyword,
      enabled: filters.enabled || undefined,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = glPageItems(page)
    pagination.total = glPageTotal(page)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = glErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function openCandidates(row: GlAuxDimensionRecord) {
  selectedDimension.value = row
  candidateDrawerVisible.value = true
  candidateKeyword.value = ''
  candidatePagination.page = 1
  await loadCandidates()
}

async function loadCandidates(append = false) {
  if (!selectedDimension.value) {
    return
  }
  actionError.value = ''
  actionMessage.value = ''
  try {
    const page = selectedDimensionIsCustom.value
      ? await glApi.auxDimensions.items(selectedDimension.value.id, {
          keyword: candidateKeyword.value,
          page: candidatePagination.page,
          pageSize: candidatePagination.pageSize,
        })
      : await glApi.auxDimensions.candidates(selectedDimension.value.code, {
          keyword: candidateKeyword.value,
          page: candidatePagination.page,
          pageSize: candidatePagination.pageSize,
        })
    const items = glPageItems(page)
    candidates.value = append ? [...candidates.value, ...items] : items
    candidatePagination.total = glPageTotal(page)
  } catch (caught) {
    if (!append) {
      candidates.value = []
      candidatePagination.total = 0
    }
    actionError.value = glErrorMessage(caught)
  }
}

async function searchCandidates() {
  candidatePagination.page = 1
  await loadCandidates()
}

async function loadMoreCandidates() {
  if (candidates.value.length >= candidatePagination.total) {
    return
  }
  candidatePagination.page += 1
  await loadCandidates(true)
}

function openCreateDimension() {
  selectedDimension.value = null
  dimensionForm.code = ''
  dimensionForm.name = ''
  dimensionForm.enabled = true
  dimensionForm.version = 0
  dimensionDialogVisible.value = true
}

function openEditDimension(row: GlAuxDimensionRecord) {
  selectedDimension.value = row
  dimensionForm.code = row.code
  dimensionForm.name = row.name
  dimensionForm.enabled = row.enabled
  dimensionForm.version = row.version
  dimensionDialogVisible.value = true
}

function openCreateItem() {
  if (!selectedDimensionIsCustom.value) {
    actionError.value = '系统维度不可维护自定义项目'
    return
  }
  selectedItem.value = null
  itemForm.code = ''
  itemForm.name = ''
  itemForm.enabled = true
  itemForm.version = 0
  itemDialogVisible.value = true
}

function openEditItem(row: GlAuxCandidateRecord) {
  selectedItem.value = row
  itemForm.code = row.objectCode || ''
  itemForm.name = row.objectName || ''
  itemForm.enabled = row.enabled !== false
  itemForm.version = row.version ?? 0
  itemDialogVisible.value = true
}

function candidateItemId(row: GlAuxCandidateRecord) {
  return row.auxItemId ?? row.objectId ?? row.id
}

async function saveCustomItem() {
  if (!selectedDimension.value || !selectedDimensionIsCustom.value || actionLoading.value) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  actionMessage.value = ''
  try {
    const payload = {
      code: itemForm.code,
      name: itemForm.name,
      enabled: itemForm.enabled,
      version: itemForm.version,
      idempotencyKey: createGlIdempotencyKey('gl-aux-item-save'),
    }
    const itemId = selectedItem.value ? candidateItemId(selectedItem.value) : null
    if (itemId) {
      await glApi.auxDimensions.updateItem(selectedDimension.value.id, itemId, payload)
      actionMessage.value = '自定义辅助项目已更新'
    } else {
      await glApi.auxDimensions.createItem(selectedDimension.value.id, payload)
      actionMessage.value = '自定义辅助项目已新增'
    }
    itemDialogVisible.value = false
    await loadCandidates()
    await loadRecords()
  } catch (caught) {
    actionError.value = glErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function disableCustomItem(row: GlAuxCandidateRecord) {
  if (!selectedDimension.value || !candidateItemId(row) || actionLoading.value) {
    return
  }
  const reason = glActionDisabledReason(row, 'DISABLE')
  if (reason && !glActionAllowed(row, 'DISABLE')) {
    actionError.value = reason
    return
  }
  if (!(await confirmAction(`停用辅助项目“${row.objectCode || ''} ${row.objectName || ''}”？`))) {
    return
  }
  selectedItem.value = row
  itemForm.code = row.objectCode || ''
  itemForm.name = row.objectName || ''
  itemForm.enabled = false
  itemForm.version = row.version ?? 0
  await saveCustomItem()
}

async function saveDimension() {
  if (actionLoading.value) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    const payload = {
      code: dimensionForm.code,
      name: dimensionForm.name,
      enabled: dimensionForm.enabled,
      version: dimensionForm.version,
      idempotencyKey: createGlIdempotencyKey('gl-aux-save'),
    }
    if (selectedDimension.value && dimensionForm.version > 0) {
      if (dimensionFormIsSystem.value) {
        throw new Error('系统维度不可非法维护')
      }
      await glApi.auxDimensions.update(selectedDimension.value.id, payload)
    } else {
      await glApi.auxDimensions.create(payload)
    }
    dimensionDialogVisible.value = false
    await loadRecords()
  } catch (caught) {
    actionError.value = glErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function search() {
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

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="辅助核算" description="配置客户、供应商、项目等辅助核算维度；候选不受主列表分页限制。">
    <template #actions>
      <el-button @click="loadRecords">刷新</el-button>
      <el-button data-test="create-aux-dimension" type="primary" @click="openCreateDimension">新增自定义维度</el-button>
    </template>
    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="维度编码或名称" /></el-form-item>
        <el-form-item label="启用">
          <el-select v-model="filters.enabled" clearable placeholder="全部">
            <el-option label="启用" value="true" />
            <el-option label="停用" value="false" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button></el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert type="info" title="客户、供应商、项目候选不受主列表分页限制，按维度接口独立查询。" :closable="false" />
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="actionMessage" type="success" :title="actionMessage" :closable="false" />
      <el-alert v-if="loading" type="info" title="辅助核算加载中" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无辅助核算维度'" stripe>
        <el-table-column prop="code" label="维度编码" min-width="130" />
        <el-table-column prop="name" label="维度名称" min-width="160" />
        <el-table-column label="维度类型" min-width="120">
          <template #default="{ row }">{{ glAuxDimensionTypeText(row.dimensionType) }}</template>
        </el-table-column>
        <el-table-column prop="itemCount" label="候选数量" min-width="100" align="right" />
        <el-table-column label="状态" min-width="90"><template #default="{ row }">{{ row.enabled ? '启用' : '停用' }}</template></el-table-column>
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ row }">
            <el-button data-test="view-aux-candidates" text @click="openCandidates(row)">候选详情</el-button>
            <el-button data-test="edit-aux-dimension" text @click="openEditDimension(row)">维护</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination
      class="table-pagination"
      layout="total, sizes, prev, pager, next"
      :page-sizes="glPageSizes"
      :total="pagination.total"
      :page-size="pagination.pageSize"
      :current-page="pagination.page"
      @current-change="changePage"
      @size-change="changePageSize"
    />
    <el-drawer v-model="candidateDrawerVisible" title="辅助候选详情" size="min(720px, 92vw)" :teleported="false">
      <el-alert type="info" title="候选池独立查询，已选辅助项按对象编码与名称回显。" :closable="false" />
      <el-alert v-if="!selectedDimensionIsCustom" type="warning" title="系统维度不可维护自定义项目，仅允许查看真实业务候选。" :closable="false" />
      <el-form class="query-form" label-position="top">
        <el-form-item label="候选关键词">
          <el-input v-model="candidateKeyword" name="gl-aux-candidate-keyword" clearable placeholder="编码或名称" />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-aux-candidates" type="primary" @click="searchCandidates">查询候选</el-button>
          <el-button
            v-if="selectedDimensionIsCustom"
            data-test="create-custom-aux-item"
            type="primary"
            @click="openCreateItem"
          >
            新增自定义项目
          </el-button>
        </el-form-item>
      </el-form>
      <div class="table-scroll">
        <el-table :data="candidates" empty-text="暂无候选，请调整筛选或检查权限">
          <el-table-column prop="objectCode" label="候选编码" min-width="140" />
          <el-table-column prop="objectName" label="候选名称" min-width="180" show-overflow-tooltip />
          <el-table-column label="启用" min-width="90"><template #default="{ row }">{{ row.enabled === false ? '停用' : '启用' }}</template></el-table-column>
          <el-table-column label="权限状态" min-width="140">
            <template #default="{ row }">{{ row.restricted ? (row.restrictedReason || '无权查看候选') : '可选' }}</template>
          </el-table-column>
          <el-table-column v-if="selectedDimensionIsCustom" label="操作" fixed="right" width="184">
            <template #default="{ row }">
              <el-button data-test="edit-custom-aux-item" text :disabled="!glActionAllowed(row, 'UPDATE')" @click="openEditItem(row)">编辑</el-button>
              <el-button data-test="disable-custom-aux-item" text type="danger" :disabled="!glActionAllowed(row, 'DISABLE')" @click="disableCustomItem(row)">停用</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <el-button v-if="candidates.length < candidatePagination.total" data-test="load-more-aux-candidates" @click="loadMoreCandidates">继续加载候选</el-button>
    </el-drawer>
    <el-drawer v-model="dimensionDialogVisible" title="自定义维度维护" size="min(720px, 92vw)" :teleported="false">
      <el-form label-position="top">
        <el-alert v-if="dimensionFormIsSystem" type="warning" title="系统维度不可非法维护，请只查看候选或停用受允许的自定义项目。" :closable="false" />
        <el-form-item label="维度编码">
          <el-input v-model="dimensionForm.code" name="gl-aux-code" clearable placeholder="REGION" :disabled="dimensionFormIsSystem" />
        </el-form-item>
        <el-form-item label="维度名称">
          <el-input v-model="dimensionForm.name" name="gl-aux-name" clearable placeholder="区域" :disabled="dimensionFormIsSystem" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="dimensionForm.enabled" active-text="启用" inactive-text="停用" :disabled="dimensionFormIsSystem" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dimensionDialogVisible = false">取消</el-button>
        <el-button data-test="save-aux-dimension" type="primary" :loading="actionLoading" :disabled="dimensionFormIsSystem" @click="saveDimension">保存</el-button>
      </template>
    </el-drawer>
    <el-drawer v-model="itemDialogVisible" title="自定义辅助项目" size="min(720px, 92vw)" :teleported="false">
      <el-form label-position="top">
        <el-form-item label="项目编码">
          <el-input v-model="itemForm.code" name="gl-aux-item-code" clearable placeholder="REG-001" />
        </el-form-item>
        <el-form-item label="项目名称">
          <el-input v-model="itemForm.name" name="gl-aux-item-name" clearable placeholder="华东区" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="itemForm.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button data-test="save-custom-aux-item" type="primary" :loading="actionLoading" @click="saveCustomItem">保存</el-button>
      </template>
    </el-drawer>
  </MasterDataTableView>
</template>
