<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  businessPeriodCloseApi,
  type BusinessPeriodCloseCheckItem,
  type BusinessPeriodCloseRunDetail,
} from '../../shared/api/businessPeriodCloseApi'
import { returnLocation } from '../../shared/navigation/navigationReturn'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems, pageTotal } from '../system/shared/pageHelpers'
import PeriodCloseSeverityTag from './PeriodCloseSeverityTag.vue'
import PeriodCloseSourceTraceDrawer from './PeriodCloseSourceTraceDrawer.vue'
import {
  canTracePeriodCloseSource,
  periodCloseDomainLabel,
  periodCloseErrorMessage,
  restrictedSourceReason,
} from './periodClosePageHelpers'
import './PeriodCloseShared.css'

const route = useRoute()
const router = useRouter()
const run = ref<BusinessPeriodCloseRunDetail | null>(null)
const items = ref<BusinessPeriodCloseCheckItem[]>([])
const loading = ref(false)
const error = ref('')
const drawerOpen = ref(false)
const selectedItem = ref<BusinessPeriodCloseCheckItem | null>(null)
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })

const runId = computed(() => route.params.runId as string)
const checkId = computed(() => route.params.checkId as string)
const domainSummaries = computed(() => {
  const domains = Array.from(new Set(items.value.map((item) => item.domain)))
  return domains.map((domain) => ({
    domain,
    label: periodCloseDomainLabel(domain),
    blockingCount: items.value.filter((item) => item.domain === domain && item.severity === 'BLOCKING').length,
    warningCount: items.value.filter((item) => item.domain === domain && item.severity === 'WARNING').length,
  }))
})

async function loadRun() {
  try {
    run.value = await businessPeriodCloseApi.runs.get(runId.value)
  } catch {
    run.value = null
  }
}

async function loadItems() {
  loading.value = true
  error.value = ''
  try {
    const page = await businessPeriodCloseApi.checks.items(runId.value, checkId.value, {
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    items.value = pageItems(page)
    pagination.total = pageTotal(page)
  } catch (caught) {
    items.value = []
    pagination.total = 0
    error.value = periodCloseErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function loadAll() {
  await Promise.all([loadRun(), loadItems()])
}

function back() {
  void router.push(returnLocation(route, { name: 'period-close-run-detail', params: { runId: runId.value } }))
}

function openSource(item: BusinessPeriodCloseCheckItem) {
  selectedItem.value = item
  drawerOpen.value = true
}

function changePage(page: number) {
  pagination.page = page
  void loadItems()
}

function changePageSize(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1
  void loadItems()
}

onMounted(loadAll)
</script>

<template>
  <MasterDataTableView title="检查运行详情" description="查看一次不可变期间检查的分区指纹、阻断、警告、建议和受权限保护的来源追溯。">
    <template #actions>
      <el-button @click="back">返回</el-button>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="检查详情加载中" :closable="false" />
    </template>

    <section class="period-close-summary-strip">
      <div><span>期间</span><strong>{{ run?.periodCode || '-' }}</strong></div>
      <div><span>检查运行</span><strong>{{ checkId }}</strong></div>
      <div><span>月结版本</span><strong>{{ run?.revisionNo ?? '-' }}</strong></div>
      <div><span>来源指纹</span><strong>{{ run?.sourceFingerprint || '-' }}</strong></div>
    </section>

    <section class="period-close-summary-strip">
      <div v-for="domain in domainSummaries" :key="domain.domain">
        <span>{{ domain.label }}</span>
        <strong>阻断 {{ domain.blockingCount }} / 警告 {{ domain.warningCount }}</strong>
      </div>
    </section>

    <div class="table-scroll">
      <el-table :data="items" :empty-text="loading ? '加载中' : '暂无检查项'" stripe>
        <el-table-column label="领域" min-width="110">
          <template #default="{ row }">{{ periodCloseDomainLabel(row.domain) }}</template>
        </el-table-column>
        <el-table-column label="级别" min-width="90">
          <template #default="{ row }"><PeriodCloseSeverityTag :severity="row.severity" /></template>
        </el-table-column>
        <el-table-column prop="title" label="中文结论" min-width="190" show-overflow-tooltip />
        <el-table-column label="业务对象" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ restrictedSourceReason(row) || row.objectNo || row.objectType || '-' }}</template>
        </el-table-column>
        <el-table-column prop="businessImpact" label="影响口径" min-width="180" show-overflow-tooltip />
        <el-table-column prop="suggestion" label="处理建议" min-width="220" show-overflow-tooltip />
        <el-table-column label="来源" fixed="right" min-width="120">
          <template #default="{ row }">
            <el-button v-if="canTracePeriodCloseSource(row)" size="small" text data-test="period-close-source-trace" @click="openSource(row)">来源追溯</el-button>
            <span v-else class="period-close-muted">{{ restrictedSourceReason(row) || '无来源入口' }}</span>
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

    <PeriodCloseSourceTraceDrawer v-model="drawerOpen" :item="selectedItem" />
  </MasterDataTableView>
</template>
