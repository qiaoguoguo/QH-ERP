<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  returnRefundReversalApi,
  type ProductionMaterialReturnDetail,
  type ProductionMaterialReturnPayload,
  type ProductionMaterialReturnSource,
  type ProductionMaterialReturnSourceLine,
  type ProductionMaterialReturnUpdatePayload,
  type ProductionMaterialReturnUpdatePayloadLine,
  type ResourceId,
  type ReversalDocumentLine,
  type ReversalSourceView,
  type ReversalStatus,
} from '../../shared/api/returnRefundReversalApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import { formatSalesAmount } from '../sales/salesPageHelpers'
import {
  formatProductionQuantity,
  productionErrorMessage,
  todayText,
  validateProductionQuantity,
} from '../production/productionPageHelpers'

interface MaterialReturnLineDraft {
  id?: ResourceId
  sourceIssueLineId?: ResourceId
  lineNo: number
  materialCode: string
  materialName: string
  unitName: string
  issuedQuantity: string
  returnedQuantity: string
  returnableQuantity: string
  unitPrice: string
  returnableAmount: string
  quantity: string
  reason: string
}

const route = useRoute()
const router = useRouter()
const routeId = computed(() => route.params.id as string | undefined)
const isEdit = computed(() => Boolean(routeId.value))
const title = computed(() => (isEdit.value ? '编辑生产退料' : '新建生产退料'))
const description = computed(() => (isEdit.value ? '更新草稿生产退料的退料数量和原因。' : '从已过账生产领料选择可退明细创建退料草稿。'))
const form = reactive({
  sourceIssueId: '' as string | number | '',
  businessDate: '',
  remark: '',
  clientRequestId: '',
})
const sourceFilters = reactive({
  keyword: '',
})
const sources = ref<ProductionMaterialReturnSource[]>([])
const editDetail = ref<ProductionMaterialReturnDetail | null>(null)
const lines = ref<MaterialReturnLineDraft[]>([])
const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const submitError = ref('')
const nonEditableStatus = ref<ReversalStatus | null>(null)

const selectedSource = computed(() => sources.value.find((source) => String(source.issueId) === String(form.sourceIssueId)))
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
  return selectedSource.value?.issueNo || editDetail.value?.source.sourceNo || '-'
})

function sourceRestricted(source?: ReversalSourceView | null) {
  return !source || source.restricted || !source.canViewSource
}

function lineInputKey(line: MaterialReturnLineDraft) {
  return line.sourceIssueLineId ?? line.id ?? line.lineNo
}

function lineDraftFromSource(line: ProductionMaterialReturnSourceLine): MaterialReturnLineDraft {
  return {
    sourceIssueLineId: line.issueLineId,
    lineNo: line.lineNo,
    materialCode: line.materialCode,
    materialName: line.materialName,
    unitName: line.unitName,
    issuedQuantity: line.issuedQuantity,
    returnedQuantity: line.returnedQuantity,
    returnableQuantity: line.returnableQuantity,
    unitPrice: line.unitPrice,
    returnableAmount: line.returnableAmount,
    quantity: '',
    reason: '',
  }
}

function lineDraftFromDetail(line: ReversalDocumentLine): MaterialReturnLineDraft {
  const restricted = sourceRestricted(line.source)
  return {
    id: line.id,
    sourceIssueLineId: restricted ? undefined : line.sourceLineId,
    lineNo: line.lineNo,
    materialCode: line.materialCode,
    materialName: line.materialName,
    unitName: line.unitName,
    issuedQuantity: '',
    returnedQuantity: line.returnedQuantityBefore ?? '',
    returnableQuantity: line.returnableQuantityBefore ?? '',
    unitPrice: line.unitPrice ?? '',
    returnableAmount: line.amount ?? '',
    quantity: line.quantity,
    reason: line.reason ?? '',
  }
}

async function loadSources() {
  const page = await returnRefundReversalApi.productionMaterialReturnSources.list({
    keyword: sourceFilters.keyword,
    page: 1,
    pageSize: 20,
  })
  sources.value = pageItems(page)
  if (!isEdit.value && sources.value.length > 0 && form.sourceIssueId === '') {
    chooseSource(sources.value[0])
  }
}

async function loadDetail() {
  if (!routeId.value) {
    return
  }
  const detail = await returnRefundReversalApi.productionMaterialReturns.get(routeId.value)
  editDetail.value = detail
  form.sourceIssueId = sourceRestricted(detail.source) ? '' : detail.source.sourceId ?? ''
  form.businessDate = detail.businessDate
  form.remark = detail.remark ?? ''
  form.clientRequestId = detail.clientRequestId ?? `material-return-${detail.id}`
  if (detail.status !== 'DRAFT') {
    nonEditableStatus.value = detail.status
    lines.value = []
    return
  }
  nonEditableStatus.value = null
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
      form.businessDate = todayText()
      form.clientRequestId = `material-return-${Date.now()}`
      await loadSources()
    }
  } catch (caught) {
    sources.value = []
    lines.value = []
    error.value = productionErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function chooseSource(source: ProductionMaterialReturnSource) {
  form.sourceIssueId = source.issueId
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
    error.value = productionErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function buildPayload(): ProductionMaterialReturnUpdatePayload | null {
  if (!canEditForm.value) {
    submitError.value = `当前生产退料${nonEditableStatusText.value}，不可编辑`
    return null
  }
  const payloadLines: ProductionMaterialReturnUpdatePayloadLine[] = []
  for (const line of lines.value) {
    if (!line.quantity) {
      continue
    }
    const quantity = validateProductionQuantity(line.quantity)
    if (quantity.message || !quantity.payloadValue) {
      submitError.value = `${line.materialName}：${quantity.message}`
      return null
    }
    const payloadLine: ProductionMaterialReturnUpdatePayloadLine = {
      quantity: quantity.payloadValue,
      reason: line.reason,
    }
    if (isEdit.value && line.id !== undefined) {
      payloadLine.id = line.id
    }
    if (line.sourceIssueLineId !== undefined) {
      payloadLine.sourceIssueLineId = line.sourceIssueLineId
    }
    if (!isEdit.value && payloadLine.sourceIssueLineId === undefined) {
      submitError.value = `${line.materialName}：来源生产领料行缺失`
      return null
    }
    if (isEdit.value && payloadLine.id === undefined && payloadLine.sourceIssueLineId === undefined) {
      submitError.value = `${line.materialName}：退料行标识缺失`
      return null
    }
    payloadLines.push(payloadLine)
  }
  if (!isEdit.value && !form.sourceIssueId) {
    submitError.value = '请选择来源生产领料'
    return null
  }
  if (!form.businessDate) {
    submitError.value = '业务日期不能为空'
    return null
  }
  if (!payloadLines.length) {
    submitError.value = '至少填写一行退料数量'
    return null
  }

  const payload: ProductionMaterialReturnUpdatePayload = {
    businessDate: form.businessDate,
    clientRequestId: form.clientRequestId || `material-return-${Date.now()}`,
    remark: form.remark,
    lines: payloadLines,
  }
  if (form.sourceIssueId) {
    payload.sourceIssueId = form.sourceIssueId
  }
  return payload
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
      ? await returnRefundReversalApi.productionMaterialReturns.update(routeId.value, payload)
      : await returnRefundReversalApi.productionMaterialReturns.create(payload as ProductionMaterialReturnPayload)
    await router.push({ name: 'production-material-return-detail', params: { id: String(detail.id) } })
  } catch (caught) {
    submitError.value = productionErrorMessage(caught)
  } finally {
    submitting.value = false
  }
}

function backToList() {
  void router.push({ name: 'production-material-returns' })
}

function returnToDetail() {
  if (!routeId.value) {
    return
  }
  void router.push({ name: 'production-material-return-detail', params: { id: routeId.value } })
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
            name="material-return-source-keyword"
            clearable
            placeholder="领料单号或工单"
          />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-material-return-sources" type="primary" @click="searchSources">查询来源</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="submitError" class="state-alert" type="error" :title="submitError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="生产退料表单加载中" :closable="false" />
    </template>

    <el-result
      v-if="!canEditForm"
      icon="warning"
      :title="`当前生产退料${nonEditableStatusText}，不可编辑`"
      sub-title="已过账或已取消的生产退料只能查看，不能继续修改草稿内容。"
    >
      <template #extra>
        <el-button data-test="return-material-return-detail" type="primary" @click="returnToDetail">返回详情</el-button>
      </template>
    </el-result>

    <div v-else class="form-layout">
      <el-card v-if="!isEdit" class="section-card" shadow="never">
        <template #header>可退来源</template>
        <el-empty v-if="!loading && sources.length === 0" description="暂无可退生产领料" />
        <div v-else class="table-scroll">
          <el-table :data="sources" :empty-text="loading ? '加载中' : '暂无可退生产领料'" stripe>
            <el-table-column label="选择" width="80">
              <template #default="{ row }">
                <el-radio
                  :model-value="String(form.sourceIssueId)"
                  :label="String(row.issueId)"
                  @change="chooseSource(row)"
                >
                  选择
                </el-radio>
              </template>
            </el-table-column>
            <el-table-column prop="issueNo" label="领料单号" min-width="170" show-overflow-tooltip />
            <el-table-column prop="workOrderNo" label="生产工单" min-width="150" show-overflow-tooltip />
            <el-table-column prop="warehouseName" label="仓库" min-width="130" show-overflow-tooltip />
            <el-table-column prop="businessDate" label="业务日期" min-width="110" />
          </el-table>
        </div>
      </el-card>

      <el-card class="section-card" shadow="never">
        <template #header>退料信息</template>
        <el-form class="document-form" label-width="96px">
          <el-form-item label="来源领料">
            <span>{{ sourceDisplayText }}</span>
          </el-form-item>
          <el-form-item label="业务日期">
            <el-input
              v-model="form.businessDate"
              name="material-return-business-date"
              placeholder="YYYY-MM-DD"
              style="width: 180px"
            />
          </el-form-item>
          <el-form-item label="备注">
            <el-input
              v-model="form.remark"
              name="material-return-remark"
              type="textarea"
              :rows="2"
              placeholder="退料说明"
            />
          </el-form-item>
        </el-form>
      </el-card>

      <el-card class="section-card" shadow="never">
        <template #header>退料明细</template>
        <div class="table-scroll">
          <el-table :data="lines" :empty-text="loading ? '加载中' : '暂无可退明细'" stripe>
            <el-table-column prop="lineNo" label="行号" width="80" />
            <el-table-column label="物料" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.materialCode }} {{ row.materialName }}
              </template>
            </el-table-column>
            <el-table-column prop="unitName" label="单位" width="80" />
            <el-table-column label="已领数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProductionQuantity(row.issuedQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="已退数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProductionQuantity(row.returnedQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="可退数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProductionQuantity(row.returnableQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="可退金额" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesAmount(row.returnableAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="退料数量" min-width="150">
              <template #default="{ row }">
                <el-input
                  v-model="row.quantity"
                  :name="`material-return-line-quantity-${lineInputKey(row)}`"
                  placeholder="0.000000"
                />
              </template>
            </el-table-column>
            <el-table-column label="退料原因" min-width="170">
              <template #default="{ row }">
                <el-input
                  v-model="row.reason"
                  :name="`material-return-line-reason-${lineInputKey(row)}`"
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
          data-test="submit-material-return"
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
