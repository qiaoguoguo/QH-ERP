<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { glApi, type GlVoucherRecord } from '../../shared/api/glApi'
import { confirmAction } from '../../shared/ui/confirmDialog'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import {
  createGlIdempotencyKey,
  formatGlAmount,
  formatGlDateTime,
  glActionAllowed,
  glActionDisabledReason,
  glBusinessSourceMetaText,
  glBusinessSourceText,
  glErrorMessage,
  glFormalSourceText,
  glVoucherDisplayNo,
  glVoucherStatusText,
} from './glPageHelpers'
import './GlShared.css'

const route = useRoute()
const router = useRouter()
const record = ref<GlVoucherRecord | null>(null)
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')
const sourceDrawerVisible = ref(false)
const auditDrawerVisible = ref(false)
const returnTo = computed(() => typeof route.query.returnTo === 'string' ? route.query.returnTo : '/gl/vouchers')

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await glApi.vouchers.get(route.params.id as string)
  } catch (caught) {
    error.value = glErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function amountText(value: string | null | undefined) {
  if (record.value?.amountVisible === false) {
    return record.value.restrictedReason || '无权查看GL金额'
  }
  return formatGlAmount(value)
}

function sourceText(voucher: GlVoucherRecord) {
  return `${glFormalSourceText(voucher)} / ${glBusinessSourceText(voucher)} / ${glBusinessSourceMetaText(voucher)}`
}

async function runVersionedAction(action: 'submit' | 'withdraw' | 'cancel') {
  if (!record.value || actionLoading.value) {
    return
  }
  const label = action === 'submit' ? '提交审批' : action === 'withdraw' ? '撤回' : '取消'
  if (!(await confirmAction(`${label}正式凭证“${glVoucherDisplayNo(record.value)}”？`))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    const payload = { version: record.value.version, idempotencyKey: createGlIdempotencyKey(`gl-voucher-${action}`) }
    if (action === 'submit') {
      record.value = await glApi.vouchers.submit(record.value.id, payload)
    } else if (action === 'withdraw') {
      record.value = await glApi.vouchers.withdraw(record.value.id, payload)
    } else {
      record.value = await glApi.vouchers.cancel(record.value.id, payload)
    }
  } catch (caught) {
    actionError.value = glErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function reverseVoucher() {
  if (!record.value || actionLoading.value) {
    return
  }
  if (!(await confirmAction(`冲销正式凭证“${glVoucherDisplayNo(record.value)}”？`))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    const reversed = await glApi.vouchers.createReversal(record.value.id, {
      version: record.value.version,
      reason: `冲销 ${glVoucherDisplayNo(record.value)}`,
      idempotencyKey: createGlIdempotencyKey('gl-reversal'),
    })
    await router.push({ name: 'gl-voucher-detail', params: { id: reversed.id }, query: { returnTo: returnTo.value } })
  } catch (caught) {
    actionError.value = glErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(loadRecord)
</script>

<template>
  <MasterDataTableView title="凭证详情" description="查看正式凭证、来源追溯、审批摘要和操作日志；已记账凭证不可删除、插入或修改。">
    <template #actions>
      <el-button @click="router.push(returnTo)">返回</el-button>
      <el-button @click="loadRecord">刷新</el-button>
      <el-button v-if="record && glActionAllowed(record, 'UPDATE')" @click="router.push({ name: 'gl-voucher-edit', params: { id: record.id }, query: { returnTo } })">编辑凭证</el-button>
      <el-button v-if="record && glActionAllowed(record, 'SUBMIT')" data-test="submit-gl-voucher" type="primary" :loading="actionLoading" @click="runVersionedAction('submit')">提交审批</el-button>
      <el-button v-if="record && glActionAllowed(record, 'WITHDRAW')" data-test="withdraw-gl-voucher" :loading="actionLoading" @click="runVersionedAction('withdraw')">撤回</el-button>
      <el-button v-if="record && glActionAllowed(record, 'CANCEL')" data-test="cancel-gl-voucher" type="danger" plain :loading="actionLoading" @click="runVersionedAction('cancel')">取消</el-button>
      <el-button v-if="record && glActionAllowed(record, 'REVERSE')" data-test="reverse-gl-voucher" type="danger" :loading="actionLoading" @click="reverseVoucher">冲销</el-button>
    </template>
    <template #alerts>
      <el-alert v-if="record?.status === 'POSTED'" type="warning" title="POSTED 凭证不可删除、插入或修改；冲销通过新凭证完成。" :closable="false" />
      <el-alert v-if="record && glActionDisabledReason(record, 'SUBMIT')" type="info" :title="glActionDisabledReason(record, 'SUBMIT')" :closable="false" />
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" type="info" title="凭证详情加载中" :closable="false" />
    </template>

    <div v-if="record" class="gl-summary-strip">
      <div><span>凭证号</span><strong>{{ glVoucherDisplayNo(record) }}</strong></div>
      <div><span>状态</span><strong>{{ glVoucherStatusText(record.status) }}</strong></div>
      <div><span>会计期间</span><strong>{{ record.accountingPeriodCode || '-' }}</strong></div>
      <div><span>凭证日期</span><strong>{{ record.voucherDate || '-' }}</strong></div>
      <div><span>借方合计</span><strong>{{ amountText(record.debitTotal) }}</strong></div>
      <div><span>贷方合计</span><strong>{{ amountText(record.creditTotal) }}</strong></div>
    </div>

    <div v-if="record" class="gl-section-grid">
      <section class="gl-section">
        <span class="gl-section-title">审批摘要</span>
        <p>{{ record.approvalSummary?.sceneCode || '无审批' }} {{ record.approvalSummary?.status || '' }}</p>
        <p class="gl-muted">{{ formatGlDateTime(record.approvalSummary?.submittedAt) }}</p>
      </section>
      <section class="gl-section">
        <span class="gl-section-title">来源追溯</span>
        <p>{{ sourceText(record) }}</p>
        <el-button v-if="record.sourceVisible !== false" text @click="sourceDrawerVisible = true">查看来源追溯</el-button>
      </section>
      <section class="gl-section">
        <span class="gl-section-title">操作日志</span>
        <p>{{ record.auditSummary?.length ? `共 ${record.auditSummary.length} 条` : '暂无操作日志' }}</p>
        <el-button text @click="auditDrawerVisible = true">查看操作日志</el-button>
      </section>
    </div>

    <div class="table-scroll">
      <el-table :data="record?.lines ?? []" :empty-text="loading ? '加载中' : '暂无凭证分录'" stripe>
        <el-table-column prop="lineNo" label="行号" min-width="80" align="right" />
        <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
        <el-table-column label="科目" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ row.accountCode }} {{ row.accountName }}</template></el-table-column>
        <el-table-column label="借方金额" min-width="130" align="right"><template #default="{ row }">{{ amountText(row.debitAmount) }}</template></el-table-column>
        <el-table-column label="贷方金额" min-width="130" align="right"><template #default="{ row }">{{ amountText(row.creditAmount) }}</template></el-table-column>
        <el-table-column label="辅助核算" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.auxiliaryItems?.map((item: any) => item.objectName || item.objectCode || item.dimensionCode).join('、') || '-' }}</template>
        </el-table-column>
      </el-table>
    </div>
    <el-drawer v-model="sourceDrawerVisible" title="来源追溯" size="min(640px, 92vw)">
      <dl v-if="record" class="gl-drawer-list">
        <dt>正式来源</dt><dd>{{ record.sourceType || 'MANUAL' }}</dd>
        <dt>正式来源号</dt><dd>{{ record.sourceNo || '-' }}</dd>
        <dt>正式来源 ID</dt><dd>{{ record.sourceId || '-' }}</dd>
        <dt>业务来源</dt><dd>{{ record.sourceOriginalType || record.businessSourceType || '-' }}</dd>
        <dt>业务来源 ID</dt><dd>{{ record.sourceOriginalId || record.businessSourceId || '-' }}</dd>
        <dt>业务单号</dt><dd>{{ record.sourceOriginalNo || record.businessSourceNo || '-' }}</dd>
        <dt>业务来源版本</dt><dd>{{ record.sourceOriginalVersion ?? record.businessSourceVersion ?? '-' }}</dd>
        <dt>业务来源指纹</dt><dd>{{ record.sourceOriginalFingerprint || record.businessSourceFingerprint || '-' }}</dd>
      </dl>
    </el-drawer>
    <el-drawer v-model="auditDrawerVisible" title="操作日志" size="min(640px, 92vw)">
      <el-timeline>
        <el-timeline-item v-for="(item, index) in record?.auditSummary ?? []" :key="index">
          {{ item }}
        </el-timeline-item>
      </el-timeline>
    </el-drawer>
  </MasterDataTableView>
</template>
