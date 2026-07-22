<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { platformGovernanceApi, type DeliveryAssetRecord } from '../../../shared/api/platformGovernanceApi'
import { formatPlatformDateTime, platformErrorMessage } from '../platformPageHelpers'
import {
  deliveryAssetActionLabel,
  deliveryAssetObjectTypeLabel,
  deliveryAssetStatusLabel,
  deliveryAssetStatusTagType,
  demoDataStatusLabel,
} from '../platformGovernanceLabels'

const assets = ref<DeliveryAssetRecord | null>(null)
const loading = ref(false)
const error = ref('')
const historyImportAdapters = computed(() => assets.value?.historyImportAdapters ?? [])
const dataRepairAdapters = computed(() => assets.value?.dataRepairAdapters ?? [])
const batchTools = computed(() => assets.value?.batchTools ?? [])
const printTemplates = computed(() => assets.value?.printTemplates ?? [])
const staticAssets = computed(() => assets.value?.staticAssets ?? [])
const demoDataVerificationText = computed(() => {
  const demoData = assets.value?.demoData
  if (!demoData) {
    return '缺失：DeliveryAssetCatalog 未提供演示数据字段'
  }
  if (demoData.verifiedAt) {
    return formatPlatformDateTime(demoData.verifiedAt)
  }
  if (demoData.status) {
    return demoData.status === 'VERIFIED' ? '已验证，缺少校验时间' : `未验证：${demoDataStatusLabel(demoData.status)}`
  }
  return '缺失：DeliveryAssetCatalog 未提供校验结果字段'
})
const missingCriticalFields = computed(() => {
  const catalog = assets.value
  if (!catalog) {
    return []
  }
  const missing: string[] = []
  if (!catalog.stageCode) {
    missing.push('阶段编码缺失')
  }
  if (!catalog.generatedAt) {
    missing.push('目录生成时间缺失')
  }
  if (!catalog.environmentCode) {
    missing.push('环境标识缺失：DeliveryAssetCatalog 未提供环境字段')
  }
  if (!catalog.manual?.version) {
    missing.push('操作手册版本缺失：仅提供静态资料路径')
  }
  if (!catalog.demoData?.version) {
    missing.push('演示数据版本缺失：仅提供静态资料路径')
  }
  if (!catalog.demoData?.status && !catalog.demoData?.verifiedAt) {
    missing.push('演示数据校验状态缺失：DeliveryAssetCatalog 未提供校验结果字段')
  }
  return missing
})

async function loadAssets() {
  loading.value = true
  error.value = ''
  try {
    assets.value = await platformGovernanceApi.deliveryAssets.get()
  } catch (caught) {
    assets.value = null
    error.value = platformErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadAssets()
})
</script>

<template>
  <section class="module-page">
    <header class="page-heading">
      <div>
        <h1>交付资料</h1>
        <p>查看固定模板、历史导入适配器、批量工具、操作手册和演示数据版本。</p>
      </div>
    </header>

    <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
    <el-alert v-if="loading" class="state-alert" type="info" title="交付资料加载中" :closable="false" />
    <el-alert
      v-if="missingCriticalFields.length"
      class="state-alert"
      type="warning"
      :title="`交付目录关键字段缺失：${missingCriticalFields.join('；')}`"
      :closable="false"
    />

    <template v-if="assets">
      <section class="section-block">
        <h2>发布信息</h2>
        <dl class="platform-panel-list">
          <dt>阶段版本</dt><dd>{{ assets.stageCode || '缺失：阶段编码未返回' }}</dd>
          <dt>目录生成时间</dt><dd>{{ formatPlatformDateTime(assets.generatedAt) }}</dd>
          <dt>环境标识</dt><dd>{{ assets.environmentCode || '缺失：DeliveryAssetCatalog 未提供环境字段' }}</dd>
          <dt>操作手册版本</dt><dd>{{ assets.manual?.version || '缺失：请查看静态资料 OPERATION_MANUAL' }}</dd>
          <dt>手册更新时间</dt><dd>{{ assets.manual?.updatedAt ? formatPlatformDateTime(assets.manual.updatedAt) : '缺失：DeliveryAssetCatalog 未提供手册更新时间' }}</dd>
          <dt>演示数据版本</dt><dd>{{ assets.demoData?.version || '缺失：请查看静态资料 DEMO_DATA_TOOLS' }}</dd>
          <dt>演示数据状态</dt><dd>{{ assets.demoData?.status ? demoDataStatusLabel(assets.demoData.status) : '缺失：DeliveryAssetCatalog 未提供状态字段' }}</dd>
          <dt>演示数据校验</dt><dd>{{ demoDataVerificationText }}</dd>
        </dl>
      </section>

      <section class="section-block">
        <h2>固定打印模板（{{ printTemplates.length }}）</h2>
        <div class="table-scroll">
          <el-table :data="printTemplates" empty-text="暂无固定打印模板" stripe>
            <el-table-column prop="templateCode" label="模板代码" min-width="220" show-overflow-tooltip />
            <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
            <el-table-column label="对象类型" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ deliveryAssetObjectTypeLabel(row.objectType) }}</template>
            </el-table-column>
            <el-table-column prop="templateVersion" label="版本" width="90" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="deliveryAssetStatusTagType(row.status)" size="small">{{ deliveryAssetStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section class="section-block">
        <h2>历史导入适配器（{{ historyImportAdapters.length }}）</h2>
        <div class="table-scroll">
          <el-table :data="historyImportAdapters" empty-text="暂无历史导入适配器" stripe>
            <el-table-column prop="code" label="适配器代码" min-width="220" show-overflow-tooltip />
            <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
            <el-table-column label="对象类型" width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ deliveryAssetObjectTypeLabel(row.targetObjectType) }}</template>
            </el-table-column>
            <el-table-column prop="version" label="版本" width="90" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="deliveryAssetStatusTagType(row.status)" size="small">{{ deliveryAssetStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section class="section-block">
        <h2>数据修复适配器（{{ dataRepairAdapters.length }}）</h2>
        <div class="table-scroll">
          <el-table :data="dataRepairAdapters" empty-text="暂无数据修复适配器" stripe>
            <el-table-column prop="code" label="适配器代码" min-width="220" show-overflow-tooltip />
            <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
            <el-table-column label="对象类型" width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ deliveryAssetObjectTypeLabel(row.targetObjectType) }}</template>
            </el-table-column>
            <el-table-column prop="version" label="版本" width="90" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="deliveryAssetStatusTagType(row.status)" size="small">{{ deliveryAssetStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section class="section-block">
        <h2>批量工具（{{ batchTools.length }}）</h2>
        <div class="table-scroll">
          <el-table :data="batchTools" empty-text="暂无批量工具" stripe>
            <el-table-column prop="code" label="工具代码" min-width="220" show-overflow-tooltip />
            <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
            <el-table-column label="对象类型" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ deliveryAssetObjectTypeLabel(row.targetObjectType) }}</template>
            </el-table-column>
            <el-table-column label="动作" width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ deliveryAssetActionLabel(row.actionCode) }}</template>
            </el-table-column>
            <el-table-column prop="version" label="版本" width="90" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="deliveryAssetStatusTagType(row.status)" size="small">{{ deliveryAssetStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section class="section-block">
        <h2>静态资料（{{ staticAssets.length }}）</h2>
        <div class="table-scroll">
          <el-table :data="staticAssets" empty-text="暂无静态资料" stripe>
            <el-table-column prop="code" label="资料代码" min-width="180" show-overflow-tooltip />
            <el-table-column prop="path" label="路径" min-width="260" show-overflow-tooltip />
            <el-table-column prop="note" label="说明" min-width="260" show-overflow-tooltip />
          </el-table>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.section-block {
  padding: 16px;
  border: 1px solid var(--qherp-border);
  border-radius: var(--qherp-radius-lg);
  background: var(--qherp-surface);
}

.section-block h2 {
  margin-bottom: 12px;
  font-size: 18px;
  line-height: 1.4;
}
</style>
