<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { glApi, type GlTrialBalanceRecord } from '../../shared/api/glApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { formatGlAmount, glErrorMessage } from './glPageHelpers'
import './GlShared.css'

const filters = reactive({ periodCode: '2026-07' })
const record = ref<GlTrialBalanceRecord | null>(null)
const loading = ref(false)
const error = ref('')

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await glApi.trialBalance.get({ periodCode: filters.periodCode })
  } catch (caught) {
    record.value = null
    error.value = glErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function amountText(value: string | null | undefined) {
  return record.value?.restricted ? (record.value.restrictedReason || '无权查看GL金额') : formatGlAmount(value)
}

onMounted(loadRecord)
</script>

<template>
  <MasterDataTableView title="试算平衡" description="按会计期间核对借贷发生额和余额；不平衡时失败关闭并列示差异。">
    <template #actions><el-button @click="loadRecord">刷新</el-button></template>
    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="会计期间"><el-input v-model="filters.periodCode" clearable placeholder="2026-07" /></el-form-item>
        <el-form-item><el-button type="primary" @click="loadRecord">查询</el-button></el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert v-if="record && !record.balanced" type="error" :title="`试算不平衡，差额 ${amountText(record.differenceAmount)}`" :closable="false" />
      <el-alert v-if="record?.balanced" type="success" title="试算平衡" :closable="false" />
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" type="info" title="试算平衡加载中" :closable="false" />
    </template>

    <div v-if="record" class="gl-summary-strip">
      <div><span>试算状态</span><strong>{{ record.balanced ? '试算平衡' : '试算不平衡' }}</strong></div>
      <div><span>期初借方</span><strong>{{ amountText(record.openingDebitTotal) }}</strong></div>
      <div><span>期初贷方</span><strong>{{ amountText(record.openingCreditTotal) }}</strong></div>
      <div><span>本期借方</span><strong>{{ amountText(record.periodDebitTotal) }}</strong></div>
      <div><span>本期贷方</span><strong>{{ amountText(record.periodCreditTotal) }}</strong></div>
      <div><span>差额</span><strong>差额 {{ amountText(record.differenceAmount) }}</strong></div>
    </div>
    <div class="table-scroll">
      <el-table :data="record?.differences ?? []" :empty-text="loading ? '加载中' : '暂无试算差异'" stripe>
        <el-table-column prop="accountCode" label="科目编码" min-width="120" />
        <el-table-column prop="accountName" label="科目名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="差额" min-width="130" align="right"><template #default="{ row }">{{ amountText(row.differenceAmount) }}</template></el-table-column>
      </el-table>
    </div>
  </MasterDataTableView>
</template>
