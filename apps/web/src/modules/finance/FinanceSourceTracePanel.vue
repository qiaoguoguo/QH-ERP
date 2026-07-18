<script setup lang="ts">
import type { PayableSourceRecord, ReceivableSourceRecord, ResourceId } from '../../shared/api/financeApi'
import { financeSourceTypeText, formatFinanceAmount } from './financePageHelpers'

type FinanceGenericTraceSource = {
  sourceType: string
  sourceId?: ResourceId
  sourceNo: string
  sourceSummary?: string | null
  sourceLineNo?: number
  sourceBusinessDate?: string | null
  businessDate?: string | null
  materialCode?: string | null
  materialName?: string | null
  unitName?: string | null
  quantity?: string | number | null
  unitPrice?: string | number | null
  sourceAmount?: string | number | null
  amount?: string | number | null
  restricted?: boolean
  restrictedReason?: string | null
  restrictedReasons?: string[]
  summary?: string | null
  relatedNo?: string | null
}
type FinanceTraceSource = ReceivableSourceRecord | PayableSourceRecord | FinanceGenericTraceSource

defineProps<{
  sources: FinanceTraceSource[]
  canViewSalesOrder?: boolean
  canViewSalesShipment?: boolean
  canViewPurchaseOrder?: boolean
  canViewPurchaseReceipt?: boolean
}>()

const emit = defineEmits<{
  viewSalesOrder: [id: ResourceId]
  viewSalesShipment: [id: ResourceId]
  viewPurchaseOrder: [id: ResourceId]
  viewPurchaseReceipt: [id: ResourceId]
}>()

function rowOrderNo(row: FinanceTraceSource) {
  if ('purchaseOrderNo' in row) {
    return row.purchaseOrderNo
  }
  if ('salesOrderNo' in row) {
    return row.salesOrderNo
  }
  return row.relatedNo ?? '-'
}

function rowBusinessDate(row: FinanceTraceSource) {
  return row.sourceBusinessDate ?? ('businessDate' in row ? row.businessDate : undefined) ?? '-'
}

function rowMaterialText(row: FinanceTraceSource) {
  const code = 'materialCode' in row ? row.materialCode : ''
  const name = 'materialName' in row ? row.materialName : ''
  const summary = 'summary' in row ? row.summary : ''
  const sourceSummary = 'sourceSummary' in row ? row.sourceSummary : ''
  return `${code} ${name}`.trim() || summary || sourceSummary || '来源受限或无物料明细'
}

function rowRestrictedReason(row: FinanceTraceSource) {
  if ('restrictedReason' in row && row.restrictedReason) {
    return row.restrictedReason
  }
  if ('restrictedReasons' in row && row.restrictedReasons?.length) {
    return row.restrictedReasons.join('；')
  }
  return '来源权限受限'
}

function rowMoney(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return formatFinanceAmount(value)
}

function rowSourceAmount(row: FinanceTraceSource) {
  return row.sourceAmount ?? ('amount' in row ? row.amount : undefined)
}
</script>

<template>
  <section class="section-block">
    <div class="section-title">来源追溯</div>
    <el-empty v-if="sources.length === 0" description="暂无来源追溯" />
    <div v-else class="table-scroll">
      <el-table :data="sources" empty-text="暂无来源追溯" stripe>
        <el-table-column label="来源类型" min-width="120">
          <template #default="{ row }">{{ financeSourceTypeText(row.sourceType) }}</template>
        </el-table-column>
        <el-table-column prop="sourceLineNo" label="行号" width="76" />
        <el-table-column label="来源单据" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.restricted">{{ rowRestrictedReason(row) }}</span>
            <el-button
              v-else-if="row.sourceType === 'SALES_SHIPMENT' && canViewSalesShipment"
              data-test="view-source-shipment"
              link
              type="primary"
              @click="emit('viewSalesShipment', row.sourceId)"
            >
              {{ row.sourceNo }}
            </el-button>
            <el-button
              v-else-if="row.sourceType === 'PURCHASE_RECEIPT' && canViewPurchaseReceipt"
              data-test="view-source-purchase-receipt"
              link
              type="primary"
              @click="emit('viewPurchaseReceipt', row.sourceId)"
            >
              {{ row.sourceNo }}
            </el-button>
            <span v-else>{{ row.sourceNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="订单单号" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button
              v-if="row.sourceType === 'SALES_SHIPMENT' && canViewSalesOrder"
              data-test="view-source-sales-order"
              link
              type="primary"
              @click="emit('viewSalesOrder', row.salesOrderId)"
            >
              {{ row.salesOrderNo }}
            </el-button>
            <el-button
              v-else-if="row.sourceType === 'PURCHASE_RECEIPT' && canViewPurchaseOrder"
              data-test="view-source-purchase-order"
              link
              type="primary"
              @click="emit('viewPurchaseOrder', row.purchaseOrderId)"
            >
              {{ row.purchaseOrderNo }}
            </el-button>
            <span v-else>{{ rowOrderNo(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="业务日期" min-width="110">
          <template #default="{ row }">{{ rowBusinessDate(row) }}</template>
        </el-table-column>
        <el-table-column label="物料" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            {{ rowMaterialText(row) }}
          </template>
        </el-table-column>
        <el-table-column label="单位" min-width="80"><template #default="{ row }">{{ row.unitName ?? '-' }}</template></el-table-column>
        <el-table-column label="数量" min-width="100" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ row.quantity ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="单价" min-width="120" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ row.restricted ? '金额权限受限' : rowMoney(row.unitPrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来源金额" min-width="130" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ row.restricted ? '金额权限受限' : rowMoney(rowSourceAmount(row)) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>

<style scoped>
.section-block {
  margin-top: 16px;
}

.section-title {
  font-weight: 600;
  margin-bottom: 10px;
}

.numeric-cell {
  display: inline-block;
  min-width: 72px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
</style>
