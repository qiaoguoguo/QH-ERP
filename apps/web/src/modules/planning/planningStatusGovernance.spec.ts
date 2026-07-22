import { describe, expect, it } from 'vitest'
import { formatStatusRiskList, scanStatusLanguage } from '../../test/statusLanguageScan'
import {
  coverageStatusLabel,
  materialRequirementReasonText,
  ownershipTypeLabel,
  reasonCodeLabel,
  runStatusLabel,
  suggestionStatusLabel,
  suggestionTypeLabel,
  supplyTypeLabel,
} from './material-requirements/materialRequirementPageHelpers'

const planningVueSources = import.meta.glob<string>('./**/*.vue', {
  eager: true,
  import: 'default',
  query: '?raw',
})

const inlineQueryFormPattern = /<el-form\b[^>]*class="query-form"[^>]*\binline\b/g
const actionColumnPattern = /<el-table-column\b(?=[^>]*label="操作")[^>]*>/g

function fileLabel(key: string): string {
  return `apps/web/src/modules/planning/${key.replace(/^\.\//, '')}`
}

function lineNumberAt(text: string, index: number): number {
  return text.slice(0, index).split(/\r?\n/).length
}

function vuePatternMatches(pattern: RegExp): string[] {
  return Object.entries(planningVueSources)
    .flatMap(([key, text]) => Array.from(text.matchAll(pattern)).map((match) => {
      const line = lineNumberAt(text, match.index ?? 0)
      const evidence = text.split(/\r?\n/)[line - 1]?.trim() ?? ''
      return `${fileLabel(key)}:${line} ${evidence}`
    }))
}

describe('计划页面状态语言治理', () => {
  it('计划目录的全局状态语言门禁命中为 0', () => {
    const risks = scanStatusLanguage().risks
      .filter((item) => item.sourceFile.startsWith('apps/web/src/modules/planning/'))

    expect(risks, formatStatusRiskList(risks)).toHaveLength(0)
  })

  it('计划列表查询栏不保留旧 inline 结构', () => {
    expect(vuePatternMatches(inlineQueryFormPattern)).toEqual([])
  })

  it('计划宽表操作列统一遵守右固定 184px 契约', () => {
    const actionColumns = vuePatternMatches(actionColumnPattern)

    expect(actionColumns).toHaveLength(3)
    expect(actionColumns.filter((column) => !/\bfixed=["']right["']/.test(column))).toEqual([])
    expect(actionColumns.filter((column) => !/\bwidth=["']184["']/.test(column))).toEqual([])
    expect(actionColumns.filter((column) => /\bmin-width=/.test(column))).toEqual([])
  })

  it('MRP 状态、建议、覆盖、原因、供给和归属未知码都有中文兜底', () => {
    expect(runStatusLabel('STALE', 'STALE')).toBe('来源已变化')
    expect(runStatusLabel('LEGACY_STATUS' as never)).toBe('未知状态')
    expect(suggestionStatusLabel('CONVERTED')).toBe('已转单')
    expect(suggestionStatusLabel('LEGACY_STATUS' as never)).toBe('未知状态')
    expect(suggestionTypeLabel('LEGACY_TYPE' as never)).toBe('未知建议类型')
    expect(coverageStatusLabel('LEGACY_COVERAGE')).toBe('未知覆盖状态')
    expect(reasonCodeLabel('LEGACY_REASON')).toBe('未知原因')
    expect(ownershipTypeLabel('LEGACY_OWNERSHIP')).toBe('未知归属')
    expect(supplyTypeLabel('LEGACY_SUPPLY')).toBe('未知供给类型')
  })

  it('MRP 动态原因优先展示服务端中文，服务端返回原码时回落到原因码中文', () => {
    expect(materialRequirementReasonText({
      actionDisabledReason: '人工锁定，暂不可转单',
      reasonMessage: 'SUPPLY_LATE',
      reasonCode: 'SUPPLY_LATE',
    })).toBe('人工锁定，暂不可转单')
    expect(materialRequirementReasonText({
      actionDisabledReason: 'SOURCE_CHANGED_SINCE_RUN',
      reasonMessage: 'SUPPLY_LATE',
      reasonCode: 'SUPPLY_LATE',
    })).toBe('供给晚于需求')
  })
})
