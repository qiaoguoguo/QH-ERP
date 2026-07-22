import { describe, expect, it } from 'vitest'
import { formatStatusRiskList, scanStatusLanguage } from '../../test/statusLanguageScan'
import {
  productionSourceStatusLabel,
  reversalDocumentStatusLabel,
  reversalDocumentStatusType,
  reversalSourceTypeLabel,
  reversalTraceDirectionLabel,
  reversalTraceStatusLabel,
  settlementAdjustmentSourceTypeLabel,
  settlementAdjustmentTypeLabel,
  targetSettlementStatusLabel,
} from './reversalPageHelpers'

const reversalVueSources = import.meta.glob<string>('./**/*.vue', {
  eager: true,
  import: 'default',
  query: '?raw',
})

const reversalTsSources = import.meta.glob<string>('./**/*.ts', {
  eager: true,
  import: 'default',
  query: '?raw',
})

const inlineQueryFormPattern = /<el-form\b[^>]*class="query-form"[^>]*\binline\b/g
const actionColumnPattern = /<el-table-column\b(?=[^>]*label="操作")[^>]*>/g
const cancelledDangerTagPattern = /CANCELLED:\s*['"]danger['"]/g
const labelMapOriginalFallbackPattern = /\blabels\[[^\]]+\]\s*\?\?\s*(?:value|sourceType|status|type)/g
const legacyProductionStatusTextPattern = /已发布|执行中|进行中/g

function fileLabel(key: string): string {
  return `apps/web/src/modules/reversal/${key.replace(/^\.\//, '')}`
}

function lineNumberAt(text: string, index: number): number {
  return text.slice(0, index).split(/\r?\n/).length
}

function vuePatternMatches(pattern: RegExp): string[] {
  return Object.entries(reversalVueSources)
    .flatMap(([key, text]) => Array.from(text.matchAll(pattern)).map((match) => {
      const line = lineNumberAt(text, match.index ?? 0)
      const evidence = text.split(/\r?\n/)[line - 1]?.trim() ?? ''
      return `${fileLabel(key)}:${line} ${evidence}`
    }))
}

function sourcePatternMatches(pattern: RegExp): string[] {
  return Object.entries({
    ...reversalVueSources,
    ...reversalTsSources,
  })
    .filter(([key]) => !key.endsWith('.spec.ts'))
    .flatMap(([key, text]) => Array.from(text.matchAll(pattern)).map((match) => {
      const line = lineNumberAt(text, match.index ?? 0)
      const evidence = text.split(/\r?\n/)[line - 1]?.trim() ?? ''
      return `${fileLabel(key)}:${line} ${evidence}`
    }))
}

describe('反向业务页面状态语言治理', () => {
  it('反向业务目录的全局状态语言门禁命中为 0', () => {
    const risks = scanStatusLanguage().risks
      .filter((item) => item.sourceFile.startsWith('apps/web/src/modules/reversal/'))

    expect(risks, formatStatusRiskList(risks)).toHaveLength(0)
  })

  it('反向业务查询栏不保留旧 inline 结构', () => {
    expect(vuePatternMatches(inlineQueryFormPattern)).toEqual([])
  })

  it('反向业务操作列统一固定在右侧、宽度 184 且不保留 min-width', () => {
    const actionColumns = vuePatternMatches(actionColumnPattern)

    expect(actionColumns.length).toBeGreaterThan(0)
    expect(actionColumns.filter((column) => (
      !column.includes('fixed="right"')
      || !column.includes('width="184"')
      || column.includes('min-width')
    ))).toEqual([])
  })

  it('反向业务取消状态不使用失败色', () => {
    expect(sourcePatternMatches(cancelledDangerTagPattern)).toEqual([])
  })

  it('反向业务页面不保留局部字典原码兜底或生产旧状态文案', () => {
    expect(sourcePatternMatches(labelMapOriginalFallbackPattern)).toEqual([])
    expect(sourcePatternMatches(legacyProductionStatusTextPattern)).toEqual([])
  })

  it('反向单据、来源、方向、冲减类型和来源状态使用中文语义与未知兜底', () => {
    expect(reversalDocumentStatusLabel('CANCELLED')).toBe('已取消')
    expect(reversalDocumentStatusLabel('LEGACY_STATUS')).toBe('未知状态')
    expect(reversalDocumentStatusType('CANCELLED')).toBe('info')
    expect(settlementAdjustmentTypeLabel('RETURN_OFFSET')).toBe('退货冲抵')
    expect(settlementAdjustmentTypeLabel('LEGACY_TYPE')).toBe('未知调整类型')
    expect(settlementAdjustmentSourceTypeLabel('RECEIPT')).toBe('收款')
    expect(settlementAdjustmentSourceTypeLabel('PAYMENT')).toBe('付款')
    expect(settlementAdjustmentSourceTypeLabel('LEGACY_SOURCE')).toBe('未知来源')
    expect(reversalSourceTypeLabel('PRODUCTION_MATERIAL_ISSUE_LINE')).toBe('生产领料行')
    expect(reversalSourceTypeLabel('LEGACY_SOURCE')).toBe('未知来源')
    expect(reversalTraceDirectionLabel('SOURCE_TO_REVERSE')).toBe('原单到反向单')
    expect(reversalTraceDirectionLabel('REVERSE_TO_SOURCE')).toBe('反向单到原单')
    expect(reversalTraceDirectionLabel('LEGACY_DIRECTION')).toBe('未知方向')
    expect(productionSourceStatusLabel('RELEASED')).toBe('已下达')
    expect(productionSourceStatusLabel('IN_PROGRESS')).toBe('生产中')
    expect(targetSettlementStatusLabel('LEGACY_STATUS')).toBe('未知状态')
    expect(reversalTraceStatusLabel({ sourceType: 'PRODUCTION_WORK_ORDER', status: 'IN_PROGRESS' })).toBe('生产中')
    expect(reversalTraceStatusLabel({ sourceType: 'PRODUCTION_MATERIAL_RETURN', status: 'POSTED' })).toBe('已过账')
    expect(reversalTraceStatusLabel({ sourceType: 'LEGACY_SOURCE', status: 'LEGACY_STATUS' })).toBe('未知状态')
  })
})
