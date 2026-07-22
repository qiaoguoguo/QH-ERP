import { describe, expect, it } from 'vitest'
import { formatStatusRiskList, scanStatusLanguage } from '../../test/statusLanguageScan'
import {
  basisTypeLabel,
  costAuditActionLabel,
  costSourceStatusLabel,
  costSourceTypeLabel,
  costStatusLabel,
  costTypeLabel,
  costWorkOrderStatusLabel,
  sourceDocumentTypeLabel,
} from './costPageHelpers'
import {
  projectCostAdjustmentDirectionLabel,
  projectCostAdjustmentStatusLabel,
  projectCostAdjustmentTypeLabel,
  projectCostCalculationStatusLabel,
  projectCostCategoryLabel,
  projectCostCompletenessLabel,
  projectCostFreshnessLabel,
  projectCostProjectStatusLabel,
  projectCostSourceStatusLabel,
  projectCostSourceTypeLabel,
  projectCostStageLabel,
  projectCostVarianceSeverityLabel,
  projectCostVarianceStatusLabel,
  projectCostVarianceTypeLabel,
} from './project/projectCostPageHelpers'

const costVueSources = import.meta.glob<string>('./**/*.vue', {
  eager: true,
  import: 'default',
  query: '?raw',
})

const inlineQueryFormPattern = /<el-form\b[^>]*class="query-form"[^>]*\binline\b/g
const rightFixedActionColumnPattern = /<el-table-column\b(?=[^>]*label="操作")(?=[^>]*fixed="right")[^>]*>/g

function fileLabel(key: string): string {
  return `apps/web/src/modules/cost/${key.replace(/^\.\//, '')}`
}

function lineNumberAt(text: string, index: number): number {
  return text.slice(0, index).split(/\r?\n/).length
}

function vuePatternMatches(pattern: RegExp): string[] {
  return Object.entries(costVueSources)
    .flatMap(([key, text]) => Array.from(text.matchAll(pattern)).map((match) => {
      const line = lineNumberAt(text, match.index ?? 0)
      const evidence = text.split(/\r?\n/)[line - 1]?.trim() ?? ''
      return `${fileLabel(key)}:${line} ${evidence}`
    }))
}

describe('成本页面状态语言治理', () => {
  it('成本目录的全局状态语言门禁命中为 0', () => {
    const risks = scanStatusLanguage().risks
      .filter((item) => item.sourceFile.startsWith('apps/web/src/modules/cost/'))

    expect(risks, formatStatusRiskList(risks)).toHaveLength(0)
  })

  it('成本查询栏不保留旧 inline 结构', () => {
    expect(vuePatternMatches(inlineQueryFormPattern)).toEqual([])
  })

  it('成本宽表操作列通过表格内部横向滚动可达，不保留右固定列遮挡风险', () => {
    expect(vuePatternMatches(rightFixedActionColumnPattern)).toEqual([])
  })

  it('成本归集状态、来源、口径、工单和审计动作使用中文语义与未知兜底', () => {
    expect(costTypeLabel('MANUFACTURING_OVERHEAD')).toBe('制造费用')
    expect(costTypeLabel('LEGACY_TYPE' as never)).toBe('未知成本类型')
    expect(costSourceTypeLabel('AUTO_PRODUCTION')).toBe('生产自动来源')
    expect(costSourceTypeLabel('LEGACY_SOURCE' as never)).toBe('未知来源')
    expect(sourceDocumentTypeLabel('PRODUCTION_COMPLETION_RECEIPT')).toBe('完工入库')
    expect(sourceDocumentTypeLabel('LEGACY_DOCUMENT' as never)).toBe('未知来源单据')
    expect(basisTypeLabel('MANUAL_UNIT_PRICE_QUANTITY')).toBe('手工单价数量')
    expect(basisTypeLabel('LEGACY_BASIS' as never)).toBe('未知口径')
    expect(costStatusLabel('ACTIVE')).toBe('有效')
    expect(costStatusLabel('LEGACY_STATUS' as never)).toBe('未知状态')
    expect(costWorkOrderStatusLabel('IN_PROGRESS')).toBe('生产中')
    expect(costWorkOrderStatusLabel('LEGACY_STATUS')).toBe('未知工单状态')
    expect(costSourceStatusLabel('VOIDED')).toBe('已作废')
    expect(costSourceStatusLabel('LEGACY_STATUS')).toBe('未知来源状态')
    expect(costAuditActionLabel('MFG_COST_RECORD_UPDATE')).toBe('成本记录更新')
    expect(costAuditActionLabel('LEGACY_ACTION')).toBe('未知动作')
  })

  it('项目成本状态、阶段、来源、差异和调整使用业务域中文语义与未知兜底', () => {
    expect(projectCostProjectStatusLabel('ACTIVE')).toBe('执行中')
    expect(projectCostProjectStatusLabel('LEGACY_STATUS')).toBe('未知项目状态')
    expect(projectCostCalculationStatusLabel('CALCULATED')).toBe('已计算')
    expect(projectCostCalculationStatusLabel('LEGACY_STATUS' as never)).toBe('未知状态')
    expect(projectCostCompletenessLabel('LEGACY_STATUS' as never)).toBe('未知完整性')
    expect(projectCostFreshnessLabel('STALE')).toBe('来源已变化')
    expect(projectCostFreshnessLabel('LEGACY_STATUS' as never)).toBe('未知当前性')
    expect(projectCostCategoryLabel('PROJECT_EXPENSE')).toBe('项目费用')
    expect(projectCostCategoryLabel('LEGACY_CATEGORY' as never)).toBe('未知分类')
    expect(projectCostStageLabel('DIRECT_PROJECT')).toBe('直接项目')
    expect(projectCostStageLabel('LEGACY_STAGE' as never)).toBe('未知阶段')
    expect(projectCostSourceStatusLabel('RESTRICTED')).toBe('来源受限')
    expect(projectCostSourceStatusLabel('LEGACY_STATUS' as never)).toBe('未知来源状态')
    expect(projectCostVarianceSeverityLabel('BLOCKING')).toBe('阻断')
    expect(projectCostVarianceSeverityLabel('LEGACY_SEVERITY' as never)).toBe('未知严重级别')
    expect(projectCostVarianceStatusLabel('OPEN')).toBe('待处理')
    expect(projectCostVarianceStatusLabel('LEGACY_STATUS' as never)).toBe('未知差异状态')
    expect(projectCostAdjustmentStatusLabel('CONFIRMED')).toBe('已确认')
    expect(projectCostAdjustmentStatusLabel('LEGACY_STATUS' as never)).toBe('未知调整状态')
    expect(projectCostAdjustmentTypeLabel('PUBLIC_EXPENSE_ALLOCATION')).toBe('公共费用分配')
    expect(projectCostAdjustmentTypeLabel('LEGACY_TYPE' as never)).toBe('未知调整类型')
    expect(projectCostVarianceTypeLabel('SOURCE_CHANGED')).toBe('来源变化')
    expect(projectCostVarianceTypeLabel('LEGACY_TYPE')).toBe('未知差异类型')
    expect(projectCostSourceTypeLabel('SALES_SHIPMENT')).toBe('销售出库')
    expect(projectCostSourceTypeLabel('LEGACY_SOURCE')).toBe('未知来源')
    expect(projectCostAdjustmentDirectionLabel('DECREASE')).toBe('减少')
    expect(projectCostAdjustmentDirectionLabel('LEGACY_DIRECTION' as never)).toBe('未知方向')
  })
})
