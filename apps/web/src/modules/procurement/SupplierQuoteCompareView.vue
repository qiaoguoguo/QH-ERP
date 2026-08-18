<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { createIdempotencyKey } from '../../shared/api/documentPlatformApi'
import {
  procurementApi,
  type ProcurementInquiryStatus,
  type ResourceId,
  type SupplierQuoteRecord,
} from '../../shared/api/procurementApi'
import { useAuthStore } from '../../stores/authStore'
import { pageItems } from '../system/shared/pageHelpers'
import {
  formatProcurementAmount,
  formatProcurementQuantity,
  procurementErrorMessage,
} from './procurementPageHelpers'

const props = withDefaults(defineProps<{
  inquiryId: ResourceId
  inquiryStatus: ProcurementInquiryStatus
  refreshKey?: number
}>(), {
  refreshKey: 0,
})

const emit = defineEmits<{
  edit: [quote: SupplierQuoteRecord]
  changed: []
}>()

const authStore = useAuthStore()
const router = useRouter()
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')
const quotes = ref<SupplierQuoteRecord[]>([])
const selectReasons = reactive<Record<string, string>>({})
const commonSelectReasons = [
  '交期更优，能够满足生产计划',
  '质量稳定，历史合格率更高',
  '技术参数或品牌更符合要求',
  '付款条件更优',
  '售后服务及质保更优',
  '最低价供应商无法满足采购数量',
  '供应商资质或项目指定',
]

const canSelectPermission = computed(() => authStore.hasPermission('procurement:quote:select'))
const canUpdatePermission = computed(() => authStore.hasPermission('procurement:quote:update'))
const canCreateAgreementPermission = computed(() => authStore.hasPermission('procurement:price-agreement:create'))

function quoteKey(id: ResourceId): string {
  return String(id)
}

function syncSelectReasons() {
  quotes.value.forEach((quote) => {
    const key = quoteKey(quote.id)
    if (selectReasons[key] === undefined) {
      selectReasons[key] = quote.selectedReason || (quote.lowestEffectiveQuote ? '最低有效报价' : '')
    }
  })
}

async function loadQuotes() {
  loading.value = true
  error.value = ''
  try {
    const page = await procurementApi.quotes.list(props.inquiryId, { page: 1, pageSize: 50 })
    quotes.value = pageItems(page)
    syncSelectReasons()
  } catch (caught) {
    quotes.value = []
    error.value = procurementErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function canSelect(quote: SupplierQuoteRecord): boolean {
  return props.inquiryStatus === 'COMPLETED'
    && canSelectPermission.value
    && Boolean(quote.allowedActions?.includes('SELECT'))
}

function canEdit(quote: SupplierQuoteRecord): boolean {
  return props.inquiryStatus === 'RELEASED'
    && canUpdatePermission.value
    && Boolean(quote.allowedActions?.includes('UPDATE'))
}

function canCreateAgreement(quote: SupplierQuoteRecord): boolean {
  return props.inquiryStatus === 'AWARDED'
    && quote.status === 'SELECTED'
    && canCreateAgreementPermission.value
}

function createPriceAgreement(quote: SupplierQuoteRecord) {
  void router.push({
    name: 'procurement-price-agreement-create',
    query: {
      sourceInquiryId: String(quote.inquiryId),
      sourceQuoteId: String(quote.id),
    },
  })
}

function priceSourceText(quote: SupplierQuoteRecord): string {
  if (quote.entrySourceType === 'IMPORT') {
    return '报价导入'
  }
  if (quote.entrySourceType === 'MANUAL') {
    return '手工录入'
  }
  return '来源未记录'
}

function selectReasonText(quote: SupplierQuoteRecord): string {
  if (canSelect(quote)) {
    return '-'
  }
  if (quote.status !== 'SELECTED') {
    return '-'
  }
  return quote.selectedReason?.trim() || '最低价中选'
}

async function selectQuote(quote: SupplierQuoteRecord) {
  if (actionLoading.value || !canSelect(quote)) {
    return
  }
  const reason = selectReasons[quoteKey(quote.id)]?.trim()
  if (!reason) {
    actionError.value = '请选择或填写选价原因'
    return
  }

  actionError.value = ''
  actionLoading.value = true
  try {
    await procurementApi.quotes.select(props.inquiryId, quote.id, {
      version: quote.version,
      reason,
      idempotencyKey: createIdempotencyKey('quote-select'),
    })
    await loadQuotes()
    emit('changed')
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
    await loadQuotes()
  } finally {
    actionLoading.value = false
  }
}

watch(() => [props.inquiryId, props.refreshKey], () => {
  void loadQuotes()
})

onMounted(() => {
  void loadQuotes()
})
</script>

<template>
  <section class="quote-compare-view" v-loading="loading">
    <div class="section-heading">
      <div>
        <h2>供应商报价比较</h2>
        <p v-if="inquiryStatus === 'RELEASED'">报价收集中，可新增、编辑或导入有效报价。</p>
        <p v-else-if="inquiryStatus === 'COMPLETED'">报价收集已结束，请比较后选择最终报价。</p>
        <p v-else>当前状态仅展示已记录的供应商报价。</p>
      </div>
    </div>
    <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
    <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
    <div class="table-scroll">
      <el-table :data="quotes" empty-text="暂无供应商报价" stripe>
        <el-table-column label="供应商" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.supplierName }}</template>
        </el-table-column>
        <el-table-column label="报价物料" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template>
        </el-table-column>
        <el-table-column label="数量" min-width="100" align="right">
          <template #default="{ row }">{{ formatProcurementQuantity(row.quantity) }}</template>
        </el-table-column>
        <el-table-column label="价格来源" min-width="140">
          <template #default="{ row }">{{ priceSourceText(row) }}</template>
        </el-table-column>
        <el-table-column label="税价" min-width="280">
          <template #default="{ row }">
            <div>未税单价 {{ formatProcurementAmount(row.taxExcludedUnitPrice) }}</div>
            <div>含税单价 {{ formatProcurementAmount(row.taxIncludedUnitPrice) }}</div>
            <div>税率 {{ formatProcurementAmount(row.taxRate) }} / {{ row.currency || 'CNY' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="有效期/交期" min-width="220">
          <template #default="{ row }">
            <div>有效期：{{ row.validFrom || '-' }} 至 {{ row.validTo || '-' }}</div>
            <div>交期：{{ row.deliveryDate || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="选价原因" min-width="320">
          <template #default="{ row }">
            <el-select
              v-if="canSelect(row)"
              v-model="selectReasons[quoteKey(row.id)]"
              :name="`select-reason-${row.id}`"
              :data-test="`select-reason-${row.id}`"
              :disabled="actionLoading"
              allow-create
              clearable
              default-first-option
              filterable
              placeholder="选择或输入选价原因"
              style="width: 100%"
            >
              <el-option
                v-for="reason in commonSelectReasons"
                :key="reason"
                :label="reason"
                :value="reason"
              />
            </el-select>
            <span v-else>{{ selectReasonText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="260">
          <template #default="{ row }">
            <el-button
              v-if="canEdit(row)"
              :data-test="`edit-quote-${row.id}`"
              size="small"
              plain
              type="primary"
              @click="emit('edit', row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="canSelect(row)"
              :data-test="`select-quote-${row.id}`"
              size="small"
              type="primary"
              :loading="actionLoading"
              :disabled="actionLoading"
              @click="selectQuote(row)"
            >
              选择
            </el-button>
            <el-button
              v-if="canCreateAgreement(row)"
              :data-test="`create-agreement-from-quote-${row.id}`"
              size="small"
              type="primary"
              @click="createPriceAgreement(row)"
            >
              生成价格协议
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>

<style scoped>
.quote-compare-view {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.section-heading {
  align-items: flex-start;
  display: flex;
  justify-content: space-between;
}

.section-heading h2 {
  color: #101828;
  font-size: 17px;
  margin: 0;
}

.section-heading p {
  color: #667085;
  font-size: 13px;
  margin: 5px 0 0;
}

.table-scroll {
  overflow-x: auto;
}
</style>
