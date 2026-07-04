<script setup lang="ts">
import type { PayableSourceRecord, ReceivableSourceRecord, ResourceId } from '../../shared/api/financeApi'
import { formatFinanceAmount } from './financePageHelpers'

defineProps<{
  sources: Array<ReceivableSourceRecord | PayableSourceRecord>
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
</script>

<template>
  <section class="section-block">
    <div class="section-title">来源追溯</div>
    <el-empty v-if="sources.length === 0" description="暂无来源追溯" />
    <div v-else class="table-scroll">
      <el-table :data="sources" empty-text="暂无来源追溯" stripe>
        <el-table-column prop="sourceLineNo" label="行号" width="76" />
        <el-table-column label="来源单据" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button
              v-if="row.sourceType === 'SALES_SHIPMENT' && canViewSalesShipment"
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
            <span v-else>{{ row.sourceType === 'PURCHASE_RECEIPT' ? row.purchaseOrderNo : row.salesOrderNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sourceBusinessDate" label="业务日期" min-width="110" />
        <el-table-column label="物料" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.materialCode }} {{ row.materialName }}
          </template>
        </el-table-column>
        <el-table-column prop="unitName" label="单位" min-width="80" />
        <el-table-column label="数量" min-width="100" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ row.quantity }}</span>
          </template>
        </el-table-column>
        <el-table-column label="单价" min-width="120" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatFinanceAmount(row.unitPrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来源金额" min-width="130" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatFinanceAmount(row.sourceAmount) }}</span>
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
