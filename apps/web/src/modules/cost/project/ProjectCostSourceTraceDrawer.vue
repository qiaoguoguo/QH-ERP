<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  projectCostApi,
  type ProjectCostCategory,
  type ProjectCostSourceListParams,
  type ProjectCostSourceRecord,
  type ProjectCostSourceStatus,
  type ProjectCostStage,
  type ResourceId,
} from '../../../shared/api/projectCostApi'
import { currentRouteReturnTo } from '../../../shared/navigation/navigationReturn'
import { pageItems, pageTotal } from '../../system/shared/pageHelpers'
import CategoryTag from './CategoryTag.vue'
import SourceStatusTag from './SourceStatusTag.vue'
import StageTag from './StageTag.vue'
import {
  formatProjectCostAmount,
  formatProjectCostQuantity,
  projectCostErrorMessage,
  projectCostMessages,
  projectCostSourceTypeLabel,
  projectCostSourceTypeOptions,
  restrictedMoneyReason,
  restrictedSourceReason,
  sourceRouteLocation,
} from './projectCostPageHelpers'

const props = defineProps<{
  modelValue: boolean
  calculationId?: ResourceId | null
  initialFilters?: Partial<ProjectCostSourceListParams>
  title?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const route = useRoute()
const router = useRouter()
const records = ref<ProjectCostSourceRecord[]>([])
const loading = ref(false)
const error = ref('')
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const filters = reactive<{
  category?: ProjectCostCategory
  stage?: ProjectCostStage
  sourceStatus?: ProjectCostSourceStatus
  sourceType: string
  businessDateFrom: string
  businessDateTo: string
  sourceRestricted?: boolean
}>({
  category: undefined,
  stage: undefined,
  sourceStatus: undefined,
  sourceType: '',
  businessDateFrom: '',
  businessDateTo: '',
  sourceRestricted: undefined,
})

const drawerVisible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

function applyInitialFilters() {
  filters.category = props.initialFilters?.category ?? undefined
  filters.stage = props.initialFilters?.stage ?? undefined
  filters.sourceStatus = props.initialFilters?.sourceStatus ?? undefined
  filters.sourceType = props.initialFilters?.sourceType ?? ''
  filters.businessDateFrom = props.initialFilters?.businessDateFrom ?? ''
  filters.businessDateTo = props.initialFilters?.businessDateTo ?? ''
  filters.sourceRestricted = props.initialFilters?.sourceRestricted ?? undefined
}

async function loadSources() {
  if (!props.calculationId) {
    records.value = []
    pagination.total = 0
    return
  }
  loading.value = true
  error.value = ''
  try {
    const page = await projectCostApi.calculations.sources(props.calculationId, {
      category: filters.category,
      stage: filters.stage,
      sourceStatus: filters.sourceStatus,
      sourceType: filters.sourceType,
      businessDateFrom: filters.businessDateFrom,
      businessDateTo: filters.businessDateTo,
      sourceRestricted: filters.sourceRestricted,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = pageItems(page)
    pagination.total = pageTotal(page)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = projectCostErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function search() {
  pagination.page = 1
  void loadSources()
}

function resetSearch() {
  applyInitialFilters()
  pagination.page = 1
  void loadSources()
}

function changePage(page: number) {
  pagination.page = page
  void loadSources()
}

function changePageSize(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1
  void loadSources()
}

function sourceNoText(row: ProjectCostSourceRecord) {
  const restrictedReason = restrictedSourceReason(row)
  if (restrictedReason) {
    return restrictedReason
  }
  return row.sourceNo || row.sourceSummary || '-'
}

function sourceAmountText(row: ProjectCostSourceRecord) {
  return formatProjectCostAmount(row.sourceAmount, row.amountVisible === false ? projectCostMessages.amountForbidden : restrictedMoneyReason(row) || undefined)
}

function rowMaterialText(row: ProjectCostSourceRecord) {
  return `${row.materialCode ?? ''} ${row.materialName ?? ''}`.trim() || row.sourceSummary || '-'
}

function viewSource(row: ProjectCostSourceRecord) {
  const target = sourceRouteLocation(row.sourceRoute, currentRouteReturnTo(route))
  if (target) {
    void router.push(target)
  }
}

watch(() => props.modelValue, (visible) => {
  if (visible) {
    applyInitialFilters()
    pagination.page = 1
    void loadSources()
  }
})
</script>

<template>
  <el-drawer v-model="drawerVisible" :title="title || '来源追溯'" size="min(880px, 94vw)" :append-to-body="false">
    <div class="project-cost-drawer-body">
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-form class="query-form project-cost-drawer-filters" label-position="top">
        <el-form-item label="分类">
          <el-select v-model="filters.category" clearable placeholder="全部分类">
            <el-option label="材料" value="MATERIAL" />
            <el-option label="人工" value="LABOR" />
            <el-option label="外协" value="OUTSOURCING" />
            <el-option label="制造费用" value="MANUFACTURING_OVERHEAD" />
            <el-option label="项目费用" value="PROJECT_EXPENSE" />
            <el-option label="调整" value="ADJUSTMENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="阶段">
          <el-select v-model="filters.stage" clearable placeholder="全部阶段">
            <el-option label="在制" value="WIP" />
            <el-option label="完工" value="FINISHED" />
            <el-option label="已交付" value="DELIVERED" />
            <el-option label="直接项目" value="DIRECT_PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源类型">
          <el-select v-model="filters.sourceType" clearable placeholder="全部来源">
            <el-option v-for="option in projectCostSourceTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源状态">
          <el-select v-model="filters.sourceStatus" clearable placeholder="全部状态">
            <el-option label="实际" value="ACTUAL" />
            <el-option label="暂估" value="PROVISIONAL" />
            <el-option label="未定价" value="UNPRICED" />
            <el-option label="已调整" value="ADJUSTED" />
            <el-option label="来源受限" value="RESTRICTED" />
            <el-option label="已排除" value="EXCLUDED" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务日期起">
          <el-date-picker v-model="filters.businessDateFrom" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="起始日期" />
        </el-form-item>
        <el-form-item label="业务日期止">
          <el-date-picker v-model="filters.businessDateTo" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="截止日期" />
        </el-form-item>
        <el-form-item label="受限">
          <el-select v-model="filters.sourceRestricted" clearable placeholder="全部">
            <el-option label="来源受限" :value="true" />
            <el-option label="来源可见" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作">
          <el-button data-test="search-project-cost-sources" type="primary" @click="search">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-scroll project-cost-source-table-scroll">
        <el-table :data="records" :empty-text="loading ? '加载中' : '暂无来源追溯'" stripe>
          <el-table-column label="分类" min-width="100">
            <template #default="{ row }"><CategoryTag :category="row.category" /></template>
          </el-table-column>
          <el-table-column label="阶段" min-width="100">
            <template #default="{ row }"><StageTag :stage="row.stage" /></template>
          </el-table-column>
          <el-table-column label="来源状态" min-width="110">
            <template #default="{ row }"><SourceStatusTag :status="row.sourceStatus" /></template>
          </el-table-column>
          <el-table-column label="来源类型" min-width="120">
            <template #default="{ row }">{{ projectCostSourceTypeLabel(row.sourceType) }}</template>
          </el-table-column>
          <el-table-column label="来源单据" min-width="190" show-overflow-tooltip>
            <template #default="{ row }">
              <el-button
                v-if="row.sourceVisible !== false && row.sourceRoute"
                link
                type="primary"
                data-test="view-project-cost-source"
                @click="viewSource(row)"
              >
                {{ sourceNoText(row) }}
              </el-button>
              <span v-else>{{ sourceNoText(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="业务日期" min-width="110">
            <template #default="{ row }">{{ row.businessDate || '-' }}</template>
          </el-table-column>
          <el-table-column label="物料" min-width="190" show-overflow-tooltip>
            <template #default="{ row }">{{ row.sourceVisible === false ? projectCostMessages.sourceRestricted : rowMaterialText(row) }}</template>
          </el-table-column>
          <el-table-column label="数量" min-width="120" align="right">
            <template #default="{ row }"><span class="numeric-cell">{{ formatProjectCostQuantity(row.quantity) }}</span></template>
          </el-table-column>
          <el-table-column label="单价" min-width="130" align="right">
            <template #default="{ row }"><span class="numeric-cell">{{ formatProjectCostAmount(row.unitPrice, row.amountVisible === false ? projectCostMessages.amountForbidden : restrictedMoneyReason(row) || undefined) }}</span></template>
          </el-table-column>
          <el-table-column label="来源金额" min-width="140" align="right">
            <template #default="{ row }"><span class="numeric-cell">{{ sourceAmountText(row) }}</span></template>
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
    </div>
    <template #footer>
      <div class="project-cost-drawer-footer">
        <el-button @click="drawerVisible = false">关闭</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.project-cost-drawer-body {
  max-height: calc(100vh - 150px);
  overflow: auto;
}

.project-cost-source-table-scroll {
  max-width: 100%;
}

.project-cost-drawer-footer {
  display: flex;
  justify-content: flex-end;
}
</style>
