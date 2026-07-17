<script setup lang="ts">
import type { ProjectProductionDocumentSummaryRecord, ProjectProductionWorkOrderDetailRecord } from '../../shared/api/projectProductionApi'
import ProductionDocumentStatusTag from './ProductionDocumentStatusTag.vue'
import { formatProductionQuantity } from './productionPageHelpers'

defineProps<{
  record: ProjectProductionWorkOrderDetailRecord
  actionLoading: boolean
  canPostIssue: boolean
  canPostReport: boolean
  canPostReceipt: boolean
}>()

const emit = defineEmits<{
  post: [
    action: 'materialIssue' | 'report' | 'completionReceipt',
    document: ProjectProductionDocumentSummaryRecord,
    documentNo: string,
  ]
}>()
</script>

<template>
  <section class="section-block">
    <h2>领料记录</h2>
    <div class="table-scroll">
      <el-table :data="record.materialIssues" empty-text="暂无领料记录" stripe>
        <el-table-column prop="issueNo" label="领料单号" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" min-width="90">
          <template #default="{ row }"><ProductionDocumentStatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column prop="businessDate" label="业务日期" min-width="110" />
        <el-table-column prop="reason" label="原因" min-width="160" show-overflow-tooltip />
        <el-table-column prop="lineCount" label="明细数" min-width="90" />
        <el-table-column prop="postedByName" label="过账人" min-width="110">
          <template #default="{ row }">{{ row.postedByName || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="96" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'DRAFT' && canPostIssue"
              data-test="post-production-material-issue"
              size="small"
              text
              :loading="actionLoading"
              :disabled="actionLoading"
              @click="emit('post', 'materialIssue', row, row.issueNo || row.documentNo || '')"
            >
              过账
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>

  <section class="section-block">
    <h2>报工记录</h2>
    <div class="table-scroll">
      <el-table :data="record.reports" empty-text="暂无报工记录" stripe>
        <el-table-column prop="reportNo" label="报工单号" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" min-width="90">
          <template #default="{ row }"><ProductionDocumentStatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column prop="businessDate" label="报工日期" min-width="110" />
        <el-table-column label="合格" min-width="100" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ formatProductionQuantity(row.qualifiedQuantity) }}</span></template>
        </el-table-column>
        <el-table-column label="不良" min-width="100" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ formatProductionQuantity(row.defectiveQuantity) }}</span></template>
        </el-table-column>
        <el-table-column prop="reporterName" label="报工人" min-width="110" />
        <el-table-column label="操作" width="96" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'DRAFT' && canPostReport"
              data-test="post-production-work-report"
              size="small"
              text
              :loading="actionLoading"
              :disabled="actionLoading"
              @click="emit('post', 'report', row, row.reportNo || row.documentNo || '')"
            >
              过账
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>

  <section class="section-block">
    <h2>完工入库记录</h2>
    <div class="table-scroll">
      <el-table :data="record.completionReceipts" empty-text="暂无完工入库记录" stripe>
        <el-table-column prop="receiptNo" label="入库单号" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" min-width="90">
          <template #default="{ row }"><ProductionDocumentStatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column prop="businessDate" label="业务日期" min-width="110" />
        <el-table-column prop="receiptWarehouseName" label="入库仓库" min-width="140" show-overflow-tooltip />
        <el-table-column label="入库数量" min-width="110" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ formatProductionQuantity(row.quantity) }}</span></template>
        </el-table-column>
        <el-table-column prop="postedByName" label="过账人" min-width="110">
          <template #default="{ row }">{{ row.postedByName || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="96" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'DRAFT' && canPostReceipt"
              data-test="post-production-completion-receipt"
              size="small"
              text
              :loading="actionLoading"
              :disabled="actionLoading"
              @click="emit('post', 'completionReceipt', row, row.receiptNo || row.documentNo || '')"
            >
              过账
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>
