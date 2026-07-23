import { describe, expect, it } from 'vitest'
import {
  buildPageSurfaceInventory,
  collectActionControlViolationsFromSource,
  collectOperationColumnViolations,
  collectOperationColumnsFromSource,
  formatOperationColumns,
  formatOperationColumnViolations,
  formatPaginationRequirements,
  formatSurfaceOccurrences,
  nonScrollableTableConclusions,
  paginationRouteExemptions,
  summarizePageSurfaceInventory,
} from './pageSurfaceInventory'

const productionReversalTraceDrawerSourceFile = 'apps/web/src/modules/reversal/ProductionReversalTraceDrawer.vue'
const outsourcingTraceLinksTableSourceFile = 'apps/web/src/modules/production/outsourcing/ProductionOutsourcingOrderDetailView.vue'

describe('页面治理表面清单门禁', () => {
  it('统计 175 个 View 页面和模块分布', () => {
    const inventory = buildPageSurfaceInventory()

    expect(inventory.moduleVueFiles, summarizePageSurfaceInventory(inventory)).toHaveLength(242)
    expect(inventory.moduleVueFiles).toContain(productionReversalTraceDrawerSourceFile)
    expect(inventory.viewFiles, summarizePageSurfaceInventory(inventory)).toHaveLength(175)
    expect(inventory.viewModuleCounts).toEqual({
      auth: 1,
      cost: 10,
      finance: 33,
      financialClose: 9,
      gl: 11,
      inventory: 6,
      master: 7,
      materials: 3,
      periodClose: 4,
      planning: 2,
      platform: 9,
      procurement: 18,
      production: 11,
      quality: 1,
      reports: 16,
      reversal: 15,
      sales: 15,
      system: 4,
    })
  })

  it('记录全部表格实例且不以文件级滚动命中替代实例清单', () => {
    const inventory = buildPageSurfaceInventory()

    expect(inventory.tables, summarizePageSurfaceInventory(inventory)).toHaveLength(246)
    expect(inventory.tables).toContainEqual(expect.objectContaining({
      sourceFile: outsourcingTraceLinksTableSourceFile,
      text: '<el-table :data="record.traceLinks ?? []" :empty-text="record.sourceSuggestionNo || \'暂无来源建议\'" stripe>',
    }))
    expect(inventory.tableColumns).toHaveLength(2038)
    inventory.tables.forEach((table) => {
      expect(table.sourceFile).toMatch(/^apps\/web\/src\//)
      expect(table.line).toBeGreaterThan(0)
      expect(table).toHaveProperty('hasInstanceScrollEvidence')
      expect(table).toHaveProperty('hasScrollEvidenceInFile')
      expect(table).toHaveProperty('scrollEvidenceLine')
    })
  })

  it('每个表格实例必须关联 table-scroll 或精确非滚动结论', () => {
    const inventory = buildPageSurfaceInventory()

    expect(nonScrollableTableConclusions).toEqual([])
    expect(
      inventory.tablesMissingScrollEvidence,
      formatSurfaceOccurrences(inventory.tablesMissingScrollEvidence),
    ).toHaveLength(0)
  })

  it('分页、弹窗、抽屉、returnTo 表面全部有矩阵项，税务基础设置新增两个分页器后分页总数为 108', () => {
    const inventory = buildPageSurfaceInventory()

    expect(inventory.paginations).toHaveLength(108)
    expect(inventory.dialogs).toHaveLength(45)
    expect(inventory.drawers).toHaveLength(35)
    expect(inventory.drawers).toContainEqual(expect.objectContaining({
      sourceFile: productionReversalTraceDrawerSourceFile,
      text: '<el-drawer',
    }))
    expect(inventory.returnContexts.length).toBeGreaterThan(0)
    expect(inventory.tooltips.length).toBeGreaterThan(0)
  })

  it('旧 query-form inline 结构必须迁移清零', () => {
    const inventory = buildPageSurfaceInventory()

    expect(inventory.inlineQueryForms, formatSurfaceOccurrences(inventory.inlineQueryForms)).toHaveLength(0)
  })

  it('分页只允许三个阶段说明精确例外且税务基础设置必须有两个分页器', () => {
    const inventory = buildPageSurfaceInventory()

    expect(paginationRouteExemptions.map((item) => item.path)).toEqual([
      '/gl/trial-balance',
      '/materials/categories',
      '/platform/delivery-assets',
    ])
    expect(inventory.paginationExemptions.map((item) => item.path)).toEqual([
      '/gl/trial-balance',
      '/materials/categories',
      '/platform/delivery-assets',
    ])
    expect(inventory.paginationRequirements.find((item) => item.path === '/gl/tax-settings')).toMatchObject({
      path: '/gl/tax-settings',
      componentSourceFile: 'apps/web/src/modules/financialClose/TaxSettingsView.vue',
      requiredCount: 2,
    })
    expect(
      inventory.paginationViolations,
      formatPaginationRequirements(inventory.paginationViolations),
    ).toHaveLength(0)
  })

  it('109 个操作列统一固定右侧、184 宽度、禁用 min-width 且第三个动作进入更多', () => {
    const inventory = buildPageSurfaceInventory()

    expect(inventory.operationColumns, summarizePageSurfaceInventory(inventory)).toHaveLength(109)
    expect(
      inventory.operationColumnViolations,
      formatOperationColumnViolations(inventory.operationColumnViolations),
    ).toHaveLength(0)
  })

  it('含更多的 38 个操作列必须默认两个直显，仅允许凭证草稿长文案 1+更多例外', () => {
    const inventory = buildPageSurfaceInventory()
    const moreColumns = inventory.operationColumns.filter((item) => item.hasMoreDropdown)
    const singleDirectMoreColumns = moreColumns.filter((item) => item.directActionCount === 1)

    expect(moreColumns, formatOperationColumns(moreColumns)).toHaveLength(38)
    expect(
      moreColumns.filter((item) => item.directActionCount === 2),
      formatOperationColumns(moreColumns),
    ).toHaveLength(37)
    expect(
      singleDirectMoreColumns.map((item) => item.sourceFile),
      formatOperationColumns(singleDirectMoreColumns),
    ).toEqual(['apps/web/src/modules/finance/VoucherDraftListView.vue'])
    expect(
      moreColumns.filter((item) => item.directActionCount === 0 || item.directActionCount > 2),
      formatOperationColumns(moreColumns),
    ).toHaveLength(0)
  })

  it('操作列门禁用 fixture 捕获未登记的 1+更多或 0+更多异常', () => {
    const columns = collectOperationColumnsFromSource('apps/web/src/modules/demo/DemoListView.vue', `
      <template>
        <el-table>
          <el-table-column label="操作" fixed="right" width="184">
            <template #default="{ row }">
              <el-button>详情</el-button>
              <el-dropdown trigger="click" class="table-actions-more">
                <el-dropdown-menu>
                  <el-dropdown-item>提交</el-dropdown-item>
                  <el-dropdown-item>取消</el-dropdown-item>
                </el-dropdown-menu>
              </el-dropdown>
            </template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="184">
            <template #default="{ row }">
              <el-dropdown trigger="click" class="table-actions-more">
                <el-dropdown-menu>
                  <el-dropdown-item>启用</el-dropdown-item>
                </el-dropdown-menu>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>
      </template>
    `)
    const violations = collectOperationColumnViolations(columns)

    expect(
      violations,
      formatOperationColumnViolations(violations),
    ).toHaveLength(2)
  })

  it('操作列门禁用 fixture 捕获裸动作链接、完整面板和未登记复合动作', () => {
    const columns = collectOperationColumnsFromSource('apps/web/src/modules/demo/DemoListView.vue', `
      <template>
        <el-table>
          <el-table-column label="操作" fixed="right" width="184">
            <template #default="{ row }">
              <RouterLink :to="detailRoute(row)" custom v-slot="{ navigate }">
                <a class="action-button-link" :href="detailRoute(row)" @click="navigate">
                  <el-button tag="span" size="small" text>详情</el-button>
                </a>
              </RouterLink>
              <FixedPrintAction variant="compact" :object-id="row.id" />
            </template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="184">
            <template #default="{ row }">
              <RouterLink :to="detailRoute(row)">详情</RouterLink>
            </template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="184">
            <template #default="{ row }">
              <a :href="detailRoute(row)">详情</a>
            </template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="184">
            <template #default="{ row }">
              <FixedPrintAction :object-id="row.id" />
            </template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="184">
            <template #default="{ row }">
              <section class="platform-panel">
                <h2>固定打印</h2>
                <el-button>预览</el-button>
              </section>
            </template>
          </el-table-column>
        </el-table>
      </template>
    `)
    const violations = collectOperationColumnViolations(columns)

    expect(columns[0]).toMatchObject({ directActionCount: 2 })
    expect(
      violations.map((item) => item.kind),
      formatOperationColumnViolations(violations),
    ).toEqual(expect.arrayContaining([
      'bare-operation-link',
      'operation-column-full-panel',
      'unregistered-composite-action',
    ]))
    expect(
      violations,
      formatOperationColumnViolations(violations),
    ).toHaveLength(4)
  })

  it('阶段纳入治理的页面动作链接必须全部使用按钮承载', () => {
    const inventory = buildPageSurfaceInventory() as ReturnType<typeof buildPageSurfaceInventory> & {
      actionControlViolations?: unknown[]
    }

    expect(
      inventory.actionControlViolations,
      JSON.stringify(inventory.actionControlViolations, null, 2),
    ).toHaveLength(0)
  })

  it('页面动作链接门禁用 fixture 捕获邻近合规按钮掩盖目标不合规入口', () => {
    const violations = collectActionControlViolationsFromSource('apps/web/src/modules/platform/dataRepairs/DataRepairCreateView.vue', `
      <template>
        <RouterLink to="/platform/data-repairs" custom v-slot="{ navigate }">
          <a class="action-button-link" href="/platform/data-repairs" @click="navigate">
            <el-button tag="span" size="small" text>返回列表</el-button>
          </a>
        </RouterLink>
        <RouterLink to="/platform/data-repairs" custom v-slot="{ navigate }">
          <a data-test="back-data-repair-list" href="/platform/data-repairs" @click="navigate">
            返回列表
          </a>
        </RouterLink>
      </template>
    `)

    expect(violations.map((item) => item.kind)).toEqual(['ordinary-page-action-link'])
    expect(violations[0]).toMatchObject({
      sourceFile: 'apps/web/src/modules/platform/dataRepairs/DataRepairCreateView.vue',
      testId: 'back-data-repair-list',
    })
  })

  it('页面动作候选门禁用 fixture 捕获标题区和详情区普通链接动作', () => {
    const violations = collectActionControlViolationsFromSource('apps/web/src/modules/demo/DemoView.vue', `
      <template>
        <header class="page-header">
          <RouterLink to="/demo/create">新增</RouterLink>
          <a href="/demo/export">下载</a>
        </header>
        <section class="detail-actions">
          <router-link to="/demo/submit">提交处理</router-link>
        </section>
        <RouterLink to="/reports/sales">销售报表</RouterLink>
      </template>
    `)

    expect(violations.map((item) => item.evidence)).toEqual([
      '<RouterLink to="/demo/create">新增</RouterLink>',
      '<a href="/demo/export">下载</a>',
      '<router-link to="/demo/submit">提交处理</router-link>',
    ])
  })

  it('非路由状态标签、抽屉、面板、追溯、编辑器和选择器进入清单', () => {
    const inventory = buildPageSurfaceInventory()

    expect(inventory.namedStatusTags).toHaveLength(28)
    expect(inventory.namedDrawers).toHaveLength(8)
    expect(inventory.namedDrawers).toContain(productionReversalTraceDrawerSourceFile)
    expect(inventory.namedPanels).toHaveLength(11)
    expect(inventory.namedTraces).toHaveLength(8)
    expect(inventory.namedTraces).toContain(productionReversalTraceDrawerSourceFile)
    expect(inventory.namedEditorsOrPickers).toHaveLength(7)
  })
})
