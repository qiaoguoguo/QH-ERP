<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  costCollectionApi,
  type CostRecordSummaryRecord,
  type CostSourceDocumentType,
  type CostSourceType,
  type CostType,
} from '../../shared/api/costCollectionApi'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import CostSourceTypeTag from './CostSourceTypeTag.vue'
import CostTypeTag from './CostTypeTag.vue'
import {
  basisTypeLabel,
  costErrorMessage,
  costStatusLabel,
  formatCostAmount,
  formatCostDateTime,
  formatCostQuantity,
  sourceDocumentTypeLabel,
} from './costPageHelpers'

const router = useRouter()
const authStore = useAuthStore()
const filters = reactive<{
  keyword: string
  costType?: CostType
  sourceType?: CostSourceType
  sourceDocumentType?: CostSourceDocumentType
  sourceDocumentNo: string
  dateFrom: string
  dateTo: string
}>({
  keyword: '',
  costType: undefined,
  sourceType: undefined,
  sourceDocumentType: undefined,
  sourceDocumentNo: '',
  dateFrom: '',
  dateTo: '',
})
const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0,
})
const records = ref<CostRecordSummaryRecord[]>([])
const loading = ref(true)
const error = ref('')

const canCreate = computed(() => authStore.hasPermission('cost:record:create'))
const canUpdate = computed(() => authStore.hasPermission('cost:record:update'))

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await costCollectionApi.records.list({
      keyword: filters.keyword,
      costType: filters.costType,
      sourceType: filters.sourceType,
      sourceDocumentType: filters.sourceDocumentType,
      sourceDocumentNo: filters.sourceDocumentNo,
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = pageItems(page)
    pagination.total = Number(page.total)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = costErrorMessage(caught)
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
  filters.costType = undefined
  filters.sourceType = undefined
  filters.sourceDocumentType = undefined
  filters.sourceDocumentNo = ''
  filters.dateFrom = ''
  filters.dateTo = ''
  pagination.page = 1
  void loadRecords()
}

function changePage(page: number) {
  pagination.page = page
  void loadRecords()
}

function createRecord() {
  void router.push({ name: 'cost-record-create' })
}

function viewRecord(record: CostRecordSummaryRecord) {
  void router.push({ name: 'cost-record-detail', params: { id: String(record.id) } })
}

function editRecord(record: CostRecordSummaryRecord) {
  void router.push({ name: 'cost-record-edit', params: { id: String(record.id) } })
}

function canEditRecord(record: CostRecordSummaryRecord) {
  return canUpdate.value && record.sourceType === 'MANUAL_ENTRY'
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="成本记录" description="查询成本归集业务记录和来源追溯，不作为正式财务核算结果。">
    <template #actions>
      <el-button v-if="canCreate" data-test="create-cost-record" type="primary" @click="createRecord">
        新增手工成本
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="cost-record-keyword" clearable placeholder="记录号、工单、产品、来源" />
        </el-form-item>
        <el-form-item label="成本类型">
          <el-select v-model="filters.costType" clearable placeholder="全部类型" style="width: 140px">
            <el-option label="材料" value="MATERIAL" />
            <el-option label="人工" value="LABOR" />
            <el-option label="制造费用" value="MANUFACTURING_OVERHEAD" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源类型">
          <el-select v-model="filters.sourceType" clearable placeholder="全部来源" style="width: 150px">
            <el-option label="生产自动来源" value="AUTO_PRODUCTION" />
            <el-option label="手工记录" value="MANUAL_ENTRY" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源单据">
          <el-select v-model="filters.sourceDocumentType" clearable placeholder="全部单据" style="width: 150px">
            <el-option label="生产领料" value="PRODUCTION_MATERIAL_ISSUE" />
            <el-option label="生产报工" value="PRODUCTION_WORK_REPORT" />
            <el-option label="完工入库" value="PRODUCTION_COMPLETION_RECEIPT" />
            <el-option label="手工成本记录" value="MANUAL_COST_RECORD" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源号">
          <el-input v-model="filters.sourceDocumentNo" name="cost-source-document-no" clearable placeholder="来源单据号" style="width: 160px" />
        </el-form-item>
        <el-form-item label="业务日期">
          <el-input v-model="filters.dateFrom" name="cost-date-from" placeholder="起始日期" style="width: 130px" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="filters.dateTo" name="cost-date-to" placeholder="截止日期" style="width: 130px" />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-cost-records" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-cost-records" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="成本记录加载中" :closable="false" />
    </template>

    <el-empty v-if="!loading && records.length === 0" description="暂无成本业务记录" />
    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无成本业务记录'" stripe>
        <el-table-column prop="recordNo" label="记录编号" min-width="160" show-overflow-tooltip />
        <el-table-column label="成本类型" min-width="110">
          <template #default="{ row }"><CostTypeTag :type="row.costType" /></template>
        </el-table-column>
        <el-table-column label="来源类型" min-width="130">
          <template #default="{ row }"><CostSourceTypeTag :type="row.sourceType" /></template>
        </el-table-column>
        <el-table-column label="来源单据" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            {{ sourceDocumentTypeLabel(row.sourceDocumentType) }} {{ row.sourceDocumentNo || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="工单" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.workOrderNo }}</template>
        </el-table-column>
        <el-table-column label="产品" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.productMaterialCode }} {{ row.productMaterialName }}</template>
        </el-table-column>
        <el-table-column label="物料" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.materialCode ? `${row.materialCode} ${row.materialName || ''}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="数量" min-width="110" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatCostQuantity(row.quantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="金额" min-width="110" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatCostAmount(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="businessDate" label="业务日期" min-width="110" />
        <el-table-column prop="recordedByName" label="记录人" min-width="110" />
        <el-table-column label="记录时间" min-width="150">
          <template #default="{ row }">{{ formatCostDateTime(row.recordedAt) }}</template>
        </el-table-column>
        <el-table-column label="口径" min-width="140">
          <template #default="{ row }">{{ basisTypeLabel(row.basisType) }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="90">
          <template #default="{ row }">{{ costStatusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="132">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-cost-record" @click="viewRecord(row)">详情</el-button>
            <el-button
              v-if="canEditRecord(row)"
              size="small"
              text
              data-test="edit-cost-record"
              @click="editRecord(row)"
            >
              编辑
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
  </MasterDataTableView>
</template>

<style scoped>
.numeric-cell {
  display: inline-block;
  min-width: 76px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
</style>
