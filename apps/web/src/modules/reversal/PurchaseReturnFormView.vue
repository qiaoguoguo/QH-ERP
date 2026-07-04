<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  returnRefundReversalApi,
  type PurchaseReturnDetail,
  type PurchaseReturnPayload,
  type PurchaseReturnSource,
  type PurchaseReturnSourceLine,
  type ReversalDocumentLine,
  type ReversalSourceView,
  type ReversalStatus,
} from '../../shared/api/returnRefundReversalApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import {
  formatSalesAmount,
  formatSalesQuantity,
  salesErrorMessage,
  validateSalesQuantity,
} from '../sales/salesPageHelpers'

interface PurchaseReturnLineDraft {
  sourceReceiptLineId: string | number
  lineNo: number
  materialCode: string
  materialName: string
  unitName: string
  receivedQuantity: string
  returnedQuantity: string
  returnableQuantity: string
  availableStockQuantity: string
  unitPrice: string
  returnableAmount: string
  quantity: string
  reason: string
}

const route = useRoute()
const router = useRouter()
const routeId = computed(() => route.params.id as string | undefined)
const isEdit = computed(() => Boolean(routeId.value))
const title = computed(() => (isEdit.value ? '编辑采购退货' : '新建采购退货'))
const description = computed(() => (isEdit.value ? '更新草稿采购退货的退货数量和原因。' : '从已过账采购入库选择可退明细创建采购退货草稿。'))
const form = reactive({
  sourceReceiptId: '' as string | number | '',
  businessDate: '',
  remark: '',
  clientRequestId: '',
})
const sourceFilters = reactive({
  keyword: '',
})
const sources = ref<PurchaseReturnSource[]>([])
const editDetail = ref<PurchaseReturnDetail | null>(null)
const lines = ref<PurchaseReturnLineDraft[]>([])
const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const submitError = ref('')
const nonEditableStatus = ref<ReversalStatus | null>(null)

const selectedSource = computed(() => sources.value.find((source) => String(source.receiptId) === String(form.sourceReceiptId)))
const canEditForm = computed(() => !isEdit.value || nonEditableStatus.value === null)
const nonEditableStatusText = computed(() => {
  if (nonEditableStatus.value === 'POSTED') {
    return '已过账'
  }
  if (nonEditableStatus.value === 'CANCELLED') {
    return '已取消'
  }
  return ''
})
const sourceDisplayText = computed(() => {
  if (isEdit.value && sourceRestricted(editDetail.value?.source)) {
    return editDetail.value?.source.restrictedMessage || '来源无查看权限'
  }
  return selectedSource.value?.receiptNo || editDetail.value?.source.sourceNo || '-'
})

function sourceRestricted(source?: ReversalSourceView | null) {
  return !source || source.restricted || !source.canViewSource
}

function lineDraftFromSource(line: PurchaseReturnSourceLine): PurchaseReturnLineDraft {
  return {
    sourceReceiptLineId: line.receiptLineId,
    lineNo: line.lineNo,
    materialCode: line.materialCode,
    materialName: line.materialName,
    unitName: line.unitName,
    receivedQuantity: line.receivedQuantity,
    returnedQuantity: line.returnedQuantity,
    returnableQuantity: line.returnableQuantity,
    availableStockQuantity: line.availableStockQuantity,
    unitPrice: line.unitPrice,
    returnableAmount: line.returnableAmount,
    quantity: '',
    reason: '',
  }
}

function lineDraftFromDetail(line: ReversalDocumentLine): PurchaseReturnLineDraft {
  const restricted = sourceRestricted(line.source)
  return {
    sourceReceiptLineId: line.sourceLineId ?? line.id,
    lineNo: line.lineNo,
    materialCode: line.materialCode,
    materialName: line.materialName,
    unitName: line.unitName,
    receivedQuantity: restricted ? '' : line.source.quantity ?? '',
    returnedQuantity: line.returnedQuantityBefore ?? '',
    returnableQuantity: line.returnableQuantityBefore ?? '',
    availableStockQuantity: '',
    unitPrice: line.unitPrice ?? '',
    returnableAmount: line.amount ?? '',
    quantity: line.quantity,
    reason: line.reason ?? '',
  }
}

async function loadSources() {
  const page = await returnRefundReversalApi.purchaseReturnSources.list({
    keyword: sourceFilters.keyword,
    page: 1,
    pageSize: 20,
  })
  sources.value = pageItems(page)
  if (!isEdit.value && sources.value.length > 0 && form.sourceReceiptId === '') {
    chooseSource(sources.value[0])
  }
}

async function loadDetail() {
  if (!routeId.value) {
    return
  }
  const detail = await returnRefundReversalApi.purchaseReturns.get(routeId.value)
  editDetail.value = detail
  if (detail.status !== 'DRAFT') {
    nonEditableStatus.value = detail.status
    form.sourceReceiptId = detail.source.sourceId ?? ''
    form.businessDate = detail.businessDate
    form.remark = detail.remark ?? ''
    form.clientRequestId = detail.clientRequestId ?? `purchase-return-${detail.id}`
    lines.value = []
    return
  }
  nonEditableStatus.value = null
  form.sourceReceiptId = detail.source.sourceId ?? ''
  form.businessDate = detail.businessDate
  form.remark = detail.remark ?? ''
  form.clientRequestId = detail.clientRequestId ?? `purchase-return-${detail.id}`
  lines.value = detail.lines.map(lineDraftFromDetail)
}

async function loadForm() {
  loading.value = true
  error.value = ''
  nonEditableStatus.value = null
  try {
    if (isEdit.value) {
      await loadDetail()
    } else {
      form.businessDate = new Date().toISOString().slice(0, 10)
      form.clientRequestId = `purchase-return-${Date.now()}`
      await loadSources()
    }
  } catch (caught) {
    sources.value = []
    lines.value = []
    error.value = salesErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function chooseSource(source: PurchaseReturnSource) {
  form.sourceReceiptId = source.receiptId
  form.businessDate = source.businessDate
  lines.value = source.lines.map(lineDraftFromSource)
}

async function searchSources() {
  if (isEdit.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    await loadSources()
  } catch (caught) {
    error.value = salesErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function buildPayload(): PurchaseReturnPayload | null {
  if (!canEditForm.value) {
    submitError.value = `当前采购退货${nonEditableStatusText.value}，不可编辑`
    return null
  }
  const payloadLines = []
  for (const line of lines.value) {
    if (!line.quantity) {
      continue
    }
    const quantity = validateSalesQuantity(line.quantity)
    if (quantity.message || !quantity.payloadValue) {
      submitError.value = `${line.materialName}：${quantity.message}`
      return null
    }
    payloadLines.push({
      sourceReceiptLineId: line.sourceReceiptLineId,
      quantity: quantity.payloadValue,
      reason: line.reason,
    })
  }
  if (!form.sourceReceiptId) {
    submitError.value = '请选择来源采购入库'
    return null
  }
  if (!form.businessDate) {
    submitError.value = '业务日期不能为空'
    return null
  }
  if (!payloadLines.length) {
    submitError.value = '至少填写一行退货数量'
    return null
  }

  return {
    sourceReceiptId: form.sourceReceiptId,
    businessDate: form.businessDate,
    clientRequestId: form.clientRequestId || `purchase-return-${Date.now()}`,
    remark: form.remark,
    lines: payloadLines,
  }
}

async function submit() {
  if (submitting.value) {
    return
  }
  submitError.value = ''
  const payload = buildPayload()
  if (!payload) {
    return
  }

  submitting.value = true
  try {
    const detail = isEdit.value && routeId.value
      ? await returnRefundReversalApi.purchaseReturns.update(routeId.value, payload)
      : await returnRefundReversalApi.purchaseReturns.create(payload)
    await router.push({ name: 'procurement-return-detail', params: { id: String(detail.id) } })
  } catch (caught) {
    submitError.value = salesErrorMessage(caught)
  } finally {
    submitting.value = false
  }
}

function backToList() {
  void router.push({ name: 'procurement-returns' })
}

function returnToDetail() {
  if (!routeId.value) {
    return
  }
  void router.push({ name: 'procurement-return-detail', params: { id: routeId.value } })
}

onMounted(() => {
  void loadForm()
})
</script>

<template>
  <MasterDataTableView :title="title" :description="description">
    <template #actions>
      <el-button @click="backToList">返回列表</el-button>
    </template>

    <template v-if="!isEdit" #filters>
      <el-form class="query-form" inline>
        <el-form-item label="候选来源">
          <el-input
            v-model="sourceFilters.keyword"
            name="purchase-return-source-keyword"
            clearable
            placeholder="入库单号或供应商"
          />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-purchase-return-sources" type="primary" @click="searchSources">查询来源</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="submitError" class="state-alert" type="error" :title="submitError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="采购退货表单加载中" :closable="false" />
    </template>

    <el-result
      v-if="!canEditForm"
      icon="warning"
      :title="`当前采购退货${nonEditableStatusText}，不可编辑`"
      sub-title="已过账或已取消的采购退货只能查看，不能继续修改草稿内容。"
    >
      <template #extra>
        <el-button data-test="return-purchase-return-detail" type="primary" @click="returnToDetail">返回详情</el-button>
      </template>
    </el-result>

    <div v-else class="form-layout">
      <el-card v-if="!isEdit" class="section-card" shadow="never">
        <template #header>可退来源</template>
        <el-empty v-if="!loading && sources.length === 0" description="暂无可退采购入库" />
        <div v-else class="table-scroll">
          <el-table :data="sources" :empty-text="loading ? '加载中' : '暂无可退采购入库'" stripe>
            <el-table-column label="选择" width="80">
              <template #default="{ row }">
                <el-radio
                  :model-value="String(form.sourceReceiptId)"
                  :label="String(row.receiptId)"
                  @change="chooseSource(row)"
                >
                  选择
                </el-radio>
              </template>
            </el-table-column>
            <el-table-column prop="receiptNo" label="入库单号" min-width="170" show-overflow-tooltip />
            <el-table-column prop="supplierName" label="供应商" min-width="150" show-overflow-tooltip />
            <el-table-column prop="warehouseName" label="仓库" min-width="130" show-overflow-tooltip />
            <el-table-column prop="businessDate" label="业务日期" min-width="110" />
          </el-table>
        </div>
      </el-card>

      <el-card class="section-card" shadow="never">
        <template #header>退货信息</template>
        <el-form class="document-form" label-width="96px">
          <el-form-item label="来源入库">
            <span>{{ sourceDisplayText }}</span>
          </el-form-item>
          <el-form-item label="业务日期">
            <el-input
              v-model="form.businessDate"
              name="purchase-return-business-date"
              placeholder="YYYY-MM-DD"
              style="width: 180px"
            />
          </el-form-item>
          <el-form-item label="备注">
            <el-input
              v-model="form.remark"
              name="purchase-return-remark"
              type="textarea"
              :rows="2"
              placeholder="退货说明"
            />
          </el-form-item>
        </el-form>
      </el-card>

      <el-card class="section-card" shadow="never">
        <template #header>退货明细</template>
        <div class="table-scroll">
          <el-table :data="lines" :empty-text="loading ? '加载中' : '暂无可退明细'" stripe>
            <el-table-column prop="lineNo" label="行号" width="80" />
            <el-table-column label="物料" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.materialCode }} {{ row.materialName }}
              </template>
            </el-table-column>
            <el-table-column prop="unitName" label="单位" width="80" />
            <el-table-column label="入库数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesQuantity(row.receivedQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="已退数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesQuantity(row.returnedQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="可退数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesQuantity(row.returnableQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="可用库存" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesQuantity(row.availableStockQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="可退金额" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesAmount(row.returnableAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="退货数量" min-width="150">
              <template #default="{ row }">
                <el-input
                  v-model="row.quantity"
                  :name="`purchase-return-line-quantity-${row.sourceReceiptLineId}`"
                  placeholder="0.000000"
                />
              </template>
            </el-table-column>
            <el-table-column label="退货原因" min-width="170">
              <template #default="{ row }">
                <el-input
                  v-model="row.reason"
                  :name="`purchase-return-line-reason-${row.sourceReceiptLineId}`"
                  placeholder="原因"
                />
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-card>

      <div class="form-actions">
        <el-button @click="backToList">取消</el-button>
        <el-button
          data-test="submit-purchase-return"
          type="primary"
          :loading="submitting"
          :disabled="submitting"
          @click="submit"
        >
          保存草稿
        </el-button>
      </div>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.form-layout {
  display: grid;
  gap: 14px;
}

.section-card {
  border-radius: 6px;
}

.document-form {
  max-width: 720px;
}

.table-scroll {
  overflow-x: auto;
}

.numeric-cell {
  display: inline-block;
  min-width: 72px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.form-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}
</style>
