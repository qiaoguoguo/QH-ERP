<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { createIdempotencyKey } from '../../shared/api/documentPlatformApi'
import { procurementApi, type ResourceId, type SupplierQuoteRecord } from '../../shared/api/procurementApi'
import { useAuthStore } from '../../stores/authStore'
import { pageItems } from '../system/shared/pageHelpers'
import {
  formatProcurementAmount,
  formatProcurementQuantity,
  procurementErrorMessage,
  procurementPriceSourceDisplay,
} from './procurementPageHelpers'

const props = defineProps<{
  inquiryId: ResourceId
}>()

const authStore = useAuthStore()
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')
const quotes = ref<SupplierQuoteRecord[]>([])
const selectReasons = reactive<Record<string, string>>({})

const canSelectPermission = computed(() => authStore.hasPermission('procurement:quote:select'))

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
  return canSelectPermission.value && Boolean(quote.allowedActions?.includes('SELECT'))
}

function priceSourceText(quote: SupplierQuoteRecord): string {
  if (quote.lowestEffectiveQuote) {
    return '最低有效报价'
  }
  return procurementPriceSourceDisplay(quote, '非最低选价')
}

function selectReasonText(quote: SupplierQuoteRecord): string {
  return quote.selectedReason || selectReasons[quoteKey(quote.id)] || priceSourceText(quote)
}

async function selectQuote(quote: SupplierQuoteRecord) {
  if (actionLoading.value) {
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
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
    await loadQuotes()
  } finally {
    actionLoading.value = false
  }
}

watch(() => props.inquiryId, () => {
  void loadQuotes()
})

onMounted(() => {
  void loadQuotes()
})
</script>

<template>
  <section class="quote-compare-view" v-loading="loading">
    <div class="section-title">供应商报价比较</div>
    <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
    <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
    <div class="table-scroll">
      <el-table :data="quotes" empty-text="暂无供应商报价" stripe>
        <el-table-column label="供应商" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.supplierName }}
          </template>
        </el-table-column>
        <el-table-column label="报价物料" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.materialCode }} {{ row.materialName }}
          </template>
        </el-table-column>
        <el-table-column label="数量" min-width="100" align="right">
          <template #default="{ row }">
            {{ formatProcurementQuantity(row.quantity) }}
          </template>
        </el-table-column>
        <el-table-column label="价格来源" min-width="140">
          <template #default="{ row }">
            {{ priceSourceText(row) }}
          </template>
        </el-table-column>
        <el-table-column label="税价" min-width="260">
          <template #default="{ row }">
            未税单价 {{ formatProcurementAmount(row.taxExcludedUnitPrice) }} /
            含税单价 {{ formatProcurementAmount(row.taxIncludedUnitPrice) }} /
            税率 {{ formatProcurementAmount(row.taxRate) }} /
            {{ row.currency || 'CNY' }}
          </template>
        </el-table-column>
        <el-table-column label="有效期/交期" min-width="220">
          <template #default="{ row }">
            <div>有效期：{{ row.validFrom || '-' }} 至 {{ row.validTo || '-' }}</div>
            <div>交期：{{ row.deliveryDate || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="选价原因" min-width="230">
          <template #default="{ row }">
            <div>{{ selectReasonText(row) }}</div>
            <el-input
              v-if="canSelect(row)"
              v-model="selectReasons[quoteKey(row.id)]"
              :name="`select-reason-${row.id}`"
              :data-test="`select-reason-${row.id}`"
              :disabled="actionLoading"
              placeholder="选择报价原因"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ row }">
            <el-button
              v-if="canSelect(row)"
              :data-test="`select-quote-${row.id}`"
              size="small"
              text
              type="primary"
              :loading="actionLoading"
              :disabled="actionLoading"
              @click="selectQuote(row)"
            >
              选择
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

.section-title {
  font-size: 18px;
  font-weight: 600;
}

.table-scroll {
  overflow-x: auto;
}
</style>
