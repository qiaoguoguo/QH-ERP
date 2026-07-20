<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { financialCloseApi, type TaxProfileRecord } from '../../shared/api/financialCloseApi'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import {
  createFinancialCloseIdempotencyKey,
  financialCloseErrorMessage,
  financialClosePageItems,
  financialClosePermissions,
  financialCloseStatusText,
  taxpayerTypeText,
  taxTypeText,
  taxFoundationDisclaimer,
} from './financialClosePageHelpers'
import './FinancialCloseShared.css'

const authStore = useAuthStore()
const profile = ref<TaxProfileRecord | null>(null)
const rateRules = ref<Array<Record<string, unknown>>>([])
const invoiceTypes = ref<Array<Record<string, unknown>>>([])
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')
const maintenanceVisible = ref(false)
const profileForm = reactive({
  taxpayerType: 'GENERAL',
  creditCode: '91330000123456789X',
  taxAuthority: '杭州市税务局',
  vatPeriodicity: 'MONTHLY',
  incomeTaxRate: '0.2500',
  urbanMaintenanceRate: '0.0700',
  effectiveFrom: '2026-01-01',
  version: 0,
})
const rateForm = reactive({
  taxType: 'VAT',
  rateCode: 'VAT_13',
  rateValue: '0.1300',
  effectiveFrom: '2026-01-01',
  effectiveTo: '',
})
const invoiceTypeForm = reactive({
  code: 'DIGITAL_VAT_SPECIAL',
  name: '数电专票',
  direction: 'OUTPUT',
  deductible: true,
})

const canManageTaxProfile = computed(() => authStore.hasPermission(financialClosePermissions.taxProfileManage))

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
    Object.assign(profileForm, {
      taxpayerType: profileRecord.taxpayerType || 'GENERAL',
      creditCode: profileRecord.creditCode || profileRecord.unifiedSocialCreditCodeMasked || '',
      taxAuthority: profileRecord.taxAuthority || '',
      vatPeriodicity: profileRecord.vatPeriodicity || 'MONTHLY',
      incomeTaxRate: profileRecord.incomeTaxRate || '0.2500',
      urbanMaintenanceRate: profileRecord.urbanMaintenanceRate || profileRecord.cityMaintenanceTaxRate || '0.0700',
      effectiveFrom: profileRecord.effectiveFrom || '2026-01-01',
      version: profileRecord.version,
    })
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

function openMaintenance() {
  if (!canManageTaxProfile.value) {
    actionError.value = '无税务基础维护权限'
    return
  }
  maintenanceVisible.value = true
}

async function saveTaxProfile() {
  if (!canManageTaxProfile.value) {
    actionError.value = '无税务基础维护权限'
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await financialCloseApi.taxProfiles.update({
      taxpayerType: profileForm.taxpayerType,
      creditCode: profileForm.creditCode,
      taxAuthority: profileForm.taxAuthority,
      vatPeriodicity: profileForm.vatPeriodicity,
      incomeTaxRate: profileForm.incomeTaxRate,
      urbanMaintenanceRate: profileForm.urbanMaintenanceRate,
      effectiveFrom: profileForm.effectiveFrom,
      version: profileForm.version,
      idempotencyKey: createFinancialCloseIdempotencyKey('tax-profile-save'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function createTaxRateRule() {
  if (!canManageTaxProfile.value) {
    actionError.value = '无税率维护权限'
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await financialCloseApi.taxRateRules.create({
      taxType: rateForm.taxType,
      rateCode: rateForm.rateCode,
      rateValue: rateForm.rateValue,
      effectiveFrom: rateForm.effectiveFrom,
      ...(rateForm.effectiveTo ? { effectiveTo: rateForm.effectiveTo } : {}),
      idempotencyKey: createFinancialCloseIdempotencyKey('tax-rate-rule-create'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function createTaxInvoiceType() {
  if (!canManageTaxProfile.value) {
    actionError.value = '无票种维护权限'
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await financialCloseApi.taxInvoiceTypes.create({
      code: invoiceTypeForm.code,
      name: invoiceTypeForm.name,
      direction: invoiceTypeForm.direction,
      deductible: invoiceTypeForm.deductible,
      idempotencyKey: createFinancialCloseIdempotencyKey('tax-invoice-type-create'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="税务基础设置" description="维护单公司税务档案、有效期税率和票种；所有税务结果均为基础汇总/估算，非正式申报。">
    <template #actions>
      <el-button @click="loadRecords">刷新</el-button>
      <el-button data-test="open-tax-settings-maintenance" type="primary" :disabled="!canManageTaxProfile" @click="openMaintenance">维护税务基础</el-button>
    </template>
    <template #alerts>
      <el-alert type="warning" :title="taxFoundationDisclaimer" :closable="false" />
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" type="info" title="税务基础加载中" :closable="false" />
    </template>

    <div class="financial-close-summary-strip">
      <div><span>公司</span><strong>{{ profile?.companyName || '齐辉制造' }}</strong></div>
      <div><span>纳税人类型</span><strong>{{ taxpayerTypeText(profile?.taxpayerType) }}</strong></div>
      <div><span>统一社会信用代码</span><strong>{{ profile?.unifiedSocialCreditCodeMasked || profile?.creditCode || '-' }}</strong></div>
      <div><span>城建税率</span><strong>{{ profile?.cityMaintenanceTaxRate || profile?.urbanMaintenanceRate || '-' }}</strong></div>
    </div>

    <section class="financial-close-section">
      <h2>有效期税率/征收率</h2>
      <div class="table-scroll">
        <el-table :data="rateRules" empty-text="暂无税率规则" stripe>
          <el-table-column label="税种" min-width="120"><template #default="{ row }">{{ taxTypeText(row.taxType) }}</template></el-table-column>
          <el-table-column label="税率" min-width="100"><template #default="{ row }">{{ row.rate ?? row.rateValue ?? '-' }}</template></el-table-column>
          <el-table-column prop="effectiveFrom" label="生效日期" min-width="120" />
          <el-table-column label="状态" min-width="100"><template #default="{ row }">{{ row.enabled === false ? '停用' : financialCloseStatusText(row.status || 'ENABLED') }}</template></el-table-column>
        </el-table>
      </div>
    </section>

    <section class="financial-close-section">
      <h2>票种</h2>
      <div class="table-scroll">
        <el-table :data="invoiceTypes" empty-text="暂无票种" stripe>
          <el-table-column prop="code" label="编码" min-width="180" />
          <el-table-column prop="name" label="名称" min-width="180" />
          <el-table-column label="状态" min-width="100"><template #default="{ row }">{{ row.enabled === false ? '停用' : financialCloseStatusText(row.status || 'ENABLED') }}</template></el-table-column>
        </el-table>
      </div>
    </section>
    <el-drawer v-model="maintenanceVisible" title="税务基础维护" size="min(720px, 92vw)">
      <el-form label-position="top">
        <el-form-item label="纳税人类型">
          <el-select v-model="profileForm.taxpayerType">
            <el-option label="一般纳税人" value="GENERAL" />
            <el-option label="小规模纳税人" value="SMALL_SCALE" />
          </el-select>
        </el-form-item>
        <el-form-item label="统一社会信用代码"><el-input v-model="profileForm.creditCode" name="tax-profile-credit-code" /></el-form-item>
        <el-form-item label="主管税务机关"><el-input v-model="profileForm.taxAuthority" name="tax-profile-authority" /></el-form-item>
        <el-form-item label="增值税申报周期">
          <el-select v-model="profileForm.vatPeriodicity">
            <el-option label="月度" value="MONTHLY" />
            <el-option label="季度" value="QUARTERLY" />
          </el-select>
        </el-form-item>
        <el-form-item label="企业所得税税率"><el-input v-model="profileForm.incomeTaxRate" name="tax-profile-income-rate" /></el-form-item>
        <el-form-item label="城建税率"><el-input v-model="profileForm.urbanMaintenanceRate" name="tax-profile-urban-rate" /></el-form-item>
        <el-form-item label="生效日期">
          <el-date-picker
            v-model="profileForm.effectiveFrom"
            name="tax-profile-effective-from"
            type="date"
            value-format="YYYY-MM-DD"
            value-on-clear=""
          />
        </el-form-item>
        <el-button data-test="save-tax-profile" type="primary" :loading="actionLoading" @click="saveTaxProfile">保存税务档案</el-button>
      </el-form>

      <section class="financial-close-section">
        <h2>新增税率/征收率</h2>
        <el-form label-position="top">
          <el-form-item label="税种"><el-input v-model="rateForm.taxType" name="tax-rate-tax-type" /></el-form-item>
          <el-form-item label="税率编码"><el-input v-model="rateForm.rateCode" name="tax-rate-code" /></el-form-item>
          <el-form-item label="税率"><el-input v-model="rateForm.rateValue" name="tax-rate-value" /></el-form-item>
          <el-form-item label="生效日期">
            <el-date-picker
              v-model="rateForm.effectiveFrom"
              name="tax-rate-effective-from"
              type="date"
              value-format="YYYY-MM-DD"
              value-on-clear=""
            />
          </el-form-item>
          <el-form-item label="截止日期">
            <el-date-picker
              v-model="rateForm.effectiveTo"
              name="tax-rate-effective-to"
              type="date"
              value-format="YYYY-MM-DD"
              value-on-clear=""
            />
          </el-form-item>
          <el-button data-test="create-tax-rate-rule" :loading="actionLoading" @click="createTaxRateRule">新增税率</el-button>
        </el-form>
      </section>

      <section class="financial-close-section">
        <h2>新增票种</h2>
        <el-form label-position="top">
          <el-form-item label="编码"><el-input v-model="invoiceTypeForm.code" name="tax-invoice-type-code" /></el-form-item>
          <el-form-item label="名称"><el-input v-model="invoiceTypeForm.name" name="tax-invoice-type-name" /></el-form-item>
          <el-form-item label="方向">
            <el-select v-model="invoiceTypeForm.direction">
              <el-option label="销项" value="OUTPUT" />
              <el-option label="进项" value="INPUT" />
            </el-select>
          </el-form-item>
          <el-form-item label="可抵扣"><el-switch v-model="invoiceTypeForm.deductible" /></el-form-item>
          <el-button data-test="create-tax-invoice-type" :loading="actionLoading" @click="createTaxInvoiceType">新增票种</el-button>
        </el-form>
      </section>
    </el-drawer>
  </MasterDataTableView>
</template>
