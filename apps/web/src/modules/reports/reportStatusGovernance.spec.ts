import { describe, expect, it } from 'vitest'
import { formatStatusRiskList, scanStatusLanguage } from '../../test/statusLanguageScan'
import {
  reportRouteConfigs,
  reportSourceTypeText,
  reportStatusText,
} from './reportPageHelpers'
import {
  operatingFinanceBaseFields,
  projectProfitCostStageText,
  projectProfitRevenueBasisText,
  projectProfitVarianceReasonText,
} from './operatingFinanceReportHelpers'

const vueSources = import.meta.glob<string>('./*.vue', {
  eager: true,
  import: 'default',
  query: '?raw',
}) as Record<string, string>

const tsSources = import.meta.glob<string>('./*.ts', {
  eager: true,
  import: 'default',
  query: '?raw',
}) as Record<string, string>

const productionSources = Object.fromEntries(
  Object.entries({ ...vueSources, ...tsSources })
    .filter(([path]) => !path.endsWith('.spec.ts')),
)

function sourceLabel(path: string) {
  return `apps/web/src/modules/reports/${path.replace(/^\.\//, '')}`
}

function lineNumber(source: string, index: number) {
  return source.slice(0, index).split(/\r?\n/).length
}

function patternMatches(pattern: RegExp, sources: Record<string, string>) {
  return Object.entries(sources).flatMap(([path, source]) =>
    Array.from(source.matchAll(pattern)).map((match) => {
      const line = lineNumber(source, match.index ?? 0)
      const evidence = source.split(/\r?\n/)[line - 1]?.trim() ?? ''
      return `${sourceLabel(path)}:${line} ${evidence}`
    }),
  )
}

describe('报表页面规范与中文状态治理', () => {
  it('覆盖报表根入口、十六个配置路由、项目利润详情深链和全部 reports 表面', () => {
    expect(reportRouteConfigs.map((item) => item.routeName)).toEqual([
      'reports-overview',
      'reports-sales',
      'reports-procurement',
      'reports-inventory',
      'reports-production',
      'reports-cost',
      'reports-settlement',
      'reports-exceptions',
      'reports-project-profit',
      'reports-project-profit-detail',
      'reports-contract-collection',
      'reports-procurement-variance',
      'reports-inventory-capital',
      'reports-receivable-payable',
      'reports-operating-accounting',
      'reports-financial-summary',
    ])
    expect(Object.keys(vueSources).sort()).toEqual([
      './ContractCollectionReportView.vue',
      './CostReportView.vue',
      './ExceptionReportView.vue',
      './FinancialSummaryReportView.vue',
      './InventoryCapitalReportView.vue',
      './InventoryReportView.vue',
      './OperatingAccountingReconciliationReportView.vue',
      './ProcurementReportView.vue',
      './ProcurementVarianceReportView.vue',
      './ProductionReportView.vue',
      './ProjectProfitDetailView.vue',
      './ProjectProfitReportView.vue',
      './ReceivablePayableReportView.vue',
      './ReportFilterBar.vue',
      './ReportMetricStrip.vue',
      './ReportOverviewView.vue',
      './ReportTracePanel.vue',
      './SalesReportView.vue',
      './SettlementReportView.vue',
    ])
  })

  it('reports 目录状态语言扫描归零且不靠白名单绕过', () => {
    const result = scanStatusLanguage()
    const reportRisks = result.risks.filter((item) =>
      item.sourceFile.startsWith('apps/web/src/modules/reports/'),
    )

    expect(result.whitelist).toEqual([])
    expect(result.whitelistErrors).toEqual([])
    expect(reportRisks, formatStatusRiskList(reportRisks)).toEqual([])
  })

  it('报表来源列不保留右固定遮挡风险，筛选区不回退旧式 inline 查询', () => {
    const rightFixedSourceColumns = patternMatches(
      /<el-table-column\b(?=[^>]*\blabel=["']来源["'])(?=[^>]*\bfixed=["']right["'])[^>]*>/g,
      vueSources,
    )
    const directSemanticColumns = patternMatches(
      /<el-table-column\b[^>]*(?:prop|property)=["'](?:severity|status|sourceType|type|stage|reasonCode)["'][^>]*>/g,
      vueSources,
    )
    const inlineQueryForms = patternMatches(
      /<el-form\b(?=[^>]*\binline(?:\s|=|>|$))(?=[^>]*\bclass=["'][^"']*\bquery-form\b[^"']*["'])[^>]*>/g,
      vueSources,
    )

    expect(rightFixedSourceColumns).toEqual([])
    expect(directSemanticColumns).toEqual([])
    expect(inlineQueryForms).toEqual([])
  })

  it('共享状态和来源 helper 不把未知枚举作为用户主文案', () => {
    const rawFallbacks = patternMatches(
      /\b(?:text|map|labels)\[[^\]]+\]\s*\?\?\s*(?:type|status|sourceType|reasonCode|basis|stage)\b/g,
      productionSources,
    )
    const rawUnknownFallbacks = patternMatches(
      /`未知(?:状态|来源|口径|阶段|原因)：\$\{(?:status|sourceType|basis|stage|reasonCode)\}`/g,
      productionSources,
    )

    expect(rawFallbacks).toEqual([])
    expect(rawUnknownFallbacks).toEqual([])
    expect(reportSourceTypeText('SALES_SHIPMENT')).toBe('销售出库')
    expect(reportSourceTypeText('UNKNOWN_SOURCE')).toBe('未知来源')
    expect(reportSourceTypeText(null)).toBe('-')
    expect(reportStatusText('LIVE')).toBe('实时经营口径')
    expect(reportStatusText('BUSINESS_SNAPSHOT')).toBe('业务月结快照')
    expect(reportStatusText('FROZEN')).toBe('冻结快照')
    expect(reportStatusText('STALE')).toBe('已过期')
    expect(reportStatusText('LEGACY_NOT_INCLUDED')).toBe('旧快照未包含')
    expect(reportStatusText('IN_PROGRESS')).toBe('生产中')
    expect(reportStatusText('UNKNOWN_STATUS')).toBe('未知状态')
    expect(projectProfitRevenueBasisText('UNKNOWN_BASIS')).toBe('未知口径')
    expect(projectProfitCostStageText('UNKNOWN_STAGE')).toBe('未知阶段')
    expect(projectProfitVarianceReasonText('UNKNOWN_REASON')).toBe('未知原因')
    expect(operatingFinanceBaseFields.find((item) => item.key === 'analysisMode')?.placeholder)
      .toBe('实时经营口径或业务月结快照')
  })
})
