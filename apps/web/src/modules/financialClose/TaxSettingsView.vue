<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { financialCloseApi, type TaxProfileRecord } from '../../shared/api/financialCloseApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import {
  financialCloseErrorMessage,
  financialClosePageItems,
  taxFoundationDisclaimer,
} from './financialClosePageHelpers'
import './FinancialCloseShared.css'

const profile = ref<TaxProfileRecord | null>(null)
const rateRules = ref<Array<Record<string, unknown>>>([])
const invoiceTypes = ref<Array<Record<string, unknown>>>([])
const loading = ref(false)
const error = ref('')

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const [profileRecord, ratePage, invoicePage] = await Promise.all([
      financialCloseApi.taxProfiles.current(),
      financialCloseApi.taxRateRules.list({ page: 1, pageSize: 10 }),
      financialCloseApi.taxInvoiceTypes.list({ page: 1, pageSize: 10 }),
    ])
    profile.value = profileRecord
    rateRules.value = financialClosePageItems(ratePage)
    invoiceTypes.value = financialClosePageItems(invoicePage)
  } catch (caught) {
    profile.value = null
    rateRules.value = []
    invoiceTypes.value = []
    error.value = financialCloseErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="税务基础设置" description="维护单公司税务档案、有效期税率和票种；所有税务结果均为基础汇总/估算，非正式申报。">
    <template #actions>
      <el-button @click="loadRecords">刷新</el-button>
      <el-button type="primary">维护税务基础</el-button>
    </template>
    <template #alerts>
      <el-alert type="warning" :title="taxFoundationDisclaimer" :closable="false" />
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" type="info" title="税务基础加载中" :closable="false" />
    </template>

    <div class="financial-close-summary-strip">
      <div><span>公司</span><strong>{{ profile?.companyName || '-' }}</strong></div>
      <div><span>纳税人类型</span><strong>{{ profile?.taxpayerType || '-' }}</strong></div>
      <div><span>统一社会信用代码</span><strong>{{ profile?.unifiedSocialCreditCodeMasked || '-' }}</strong></div>
      <div><span>城建税率</span><strong>{{ profile?.cityMaintenanceTaxRate || '-' }}</strong></div>
    </div>

    <section class="financial-close-section">
      <h2>有效期税率/征收率</h2>
      <div class="table-scroll">
        <el-table :data="rateRules" empty-text="暂无税率规则" stripe>
          <el-table-column prop="taxType" label="税种" min-width="120" />
          <el-table-column prop="rate" label="税率" min-width="100" />
          <el-table-column prop="effectiveFrom" label="生效日期" min-width="120" />
          <el-table-column prop="enabled" label="状态" min-width="100" />
        </el-table>
      </div>
    </section>

    <section class="financial-close-section">
      <h2>票种</h2>
      <div class="table-scroll">
        <el-table :data="invoiceTypes" empty-text="暂无票种" stripe>
          <el-table-column prop="code" label="编码" min-width="180" />
          <el-table-column prop="name" label="名称" min-width="180" />
          <el-table-column prop="enabled" label="状态" min-width="100" />
        </el-table>
      </div>
    </section>
  </MasterDataTableView>
</template>
