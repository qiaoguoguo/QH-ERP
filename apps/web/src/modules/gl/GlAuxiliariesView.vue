<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { glApi, type GlAuxDimensionRecord } from '../../shared/api/glApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { glErrorMessage, glPageItems, glPageSizes, glPageTotal } from './glPageHelpers'
import './GlShared.css'

const filters = reactive({ keyword: '', enabled: '' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<GlAuxDimensionRecord[]>([])
const loading = ref(false)
const error = ref('')

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await glApi.auxDimensions.list({
      keyword: filters.keyword,
      enabled: filters.enabled || undefined,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = glPageItems(page)
    pagination.total = glPageTotal(page)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = glErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function search() {
  pagination.page = 1
  void loadRecords()
}

function changePage(page: number) {
  pagination.page = page
  void loadRecords()
}

function changePageSize(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1
  void loadRecords()
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="辅助核算" description="配置客户、供应商、项目等辅助核算维度；候选不受主列表分页限制。">
    <template #actions>
      <el-button @click="loadRecords">刷新</el-button>
    </template>
    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="维度编码或名称" /></el-form-item>
        <el-form-item label="启用">
          <el-select v-model="filters.enabled" clearable placeholder="全部">
            <el-option label="启用" value="true" />
            <el-option label="停用" value="false" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button></el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert type="info" title="客户、供应商、项目候选不受主列表分页限制，按维度接口独立查询。" :closable="false" />
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" type="info" title="辅助核算加载中" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无辅助核算维度'" stripe>
        <el-table-column prop="code" label="维度编码" min-width="130" />
        <el-table-column prop="name" label="维度名称" min-width="160" />
        <el-table-column prop="dimensionType" label="维度类型" min-width="120" />
        <el-table-column prop="itemCount" label="候选数量" min-width="100" align="right" />
        <el-table-column label="状态" min-width="90"><template #default="{ row }">{{ row.enabled ? '启用' : '停用' }}</template></el-table-column>
      </el-table>
    </div>
    <el-pagination
      class="table-pagination"
      layout="total, sizes, prev, pager, next"
      :page-sizes="glPageSizes"
      :total="pagination.total"
      :page-size="pagination.pageSize"
      :current-page="pagination.page"
      @current-change="changePage"
      @size-change="changePageSize"
    />
  </MasterDataTableView>
</template>
