import { describe, expect, it } from 'vitest'
import { formatStatusRiskList, scanStatusLanguage } from '../../test/statusLanguageScan'
import {
  businessPeriodStatusLabel,
  periodCloseAuditActionLabel,
  periodCloseCheckResultLabel,
  periodCloseDomainLabel,
  periodCloseSeverityLabel,
  periodCloseSnapshotStageLabel,
  periodCloseStatusLabel,
} from './periodClosePageHelpers'

const periodCloseVueSources = import.meta.glob<string>('./**/*.vue', {
  eager: true,
  import: 'default',
  query: '?raw',
})

const inlineQueryFormPattern = /<el-form\b[^>]*class="query-form"[^>]*\binline\b/g
const actionColumnPattern = /<el-table-column\b(?=[^>]*label="操作")[^>]*>/g

function fileLabel(key: string): string {
  return `apps/web/src/modules/periodClose/${key.replace(/^\.\//, '')}`
}

function lineNumberAt(text: string, index: number): number {
  return text.slice(0, index).split(/\r?\n/).length
}

function vuePatternMatches(pattern: RegExp): string[] {
  return Object.entries(periodCloseVueSources)
    .flatMap(([key, text]) => Array.from(text.matchAll(pattern)).map((match) => {
      const line = lineNumberAt(text, match.index ?? 0)
      const evidence = text.split(/\r?\n/)[line - 1]?.trim() ?? ''
      return `${fileLabel(key)}:${line} ${evidence}`
    }))
}

describe('业务月结页面状态语言治理', () => {
  it('业务月结目录的全局状态语言门禁命中为 0', () => {
    const risks = scanStatusLanguage().risks
      .filter((item) => item.sourceFile.startsWith('apps/web/src/modules/periodClose/'))

    expect(risks, formatStatusRiskList(risks)).toHaveLength(0)
  })

  it('业务月结查询栏不保留旧 inline 结构', () => {
    expect(vuePatternMatches(inlineQueryFormPattern)).toEqual([])
  })

  it('业务月结宽表操作入口统一遵守右固定 184px 契约', () => {
    const actionColumns = vuePatternMatches(actionColumnPattern)

    expect(actionColumns).toHaveLength(1)
    expect(actionColumns.filter((column) => !/\bfixed=["']right["']/.test(column))).toEqual([])
    expect(actionColumns.filter((column) => !/\bwidth=["']184["']/.test(column))).toEqual([])
    expect(actionColumns.filter((column) => /\bmin-width=/.test(column))).toEqual([])
  })

  it('月结状态、期间、检查、领域、级别、动作和快照阶段使用中文语义与未知兜底', () => {
    expect(periodCloseStatusLabel('PENDING_CHECK')).toBe('待检查')
    expect(periodCloseStatusLabel('BLOCKED')).toBe('检查未通过')
    expect(periodCloseStatusLabel('READY')).toBe('可月结')
    expect(periodCloseStatusLabel('CLOSED')).toBe('已月结')
    expect(periodCloseStatusLabel('REOPENED')).toBe('已重开')
    expect(periodCloseStatusLabel('LEGACY_STATUS')).toBe('未知状态')
    expect(periodCloseStatusLabel('LEGACY_STATUS', '历史关闭')).toBe('历史关闭')
    expect(periodCloseStatusLabel('LEGACY_STATUS', 'LEGACY_STATUS')).toBe('未知状态')
    expect(businessPeriodStatusLabel('LOCKED')).toBe('已锁定')
    expect(businessPeriodStatusLabel('LEGACY_STATUS')).toBe('未知期间状态')
    expect(periodCloseCheckResultLabel('PASSED')).toBe('通过')
    expect(periodCloseCheckResultLabel('LEGACY_RESULT')).toBe('未知检查结果')
    expect(periodCloseAuditActionLabel('PERIOD_CLOSE_REOPEN')).toBe('重开期间')
    expect(periodCloseAuditActionLabel('LEGACY_ACTION')).toBe('未知动作')
    expect(periodCloseDomainLabel('PROJECT_COST')).toBe('项目成本')
    expect(periodCloseDomainLabel('LEGACY_DOMAIN' as never)).toBe('未知领域')
    expect(periodCloseSeverityLabel('WARNING')).toBe('警告')
    expect(periodCloseSeverityLabel('LEGACY_SEVERITY' as never)).toBe('未知级别')
    expect(periodCloseSnapshotStageLabel('WIP')).toBe('在制')
    expect(periodCloseSnapshotStageLabel('在制')).toBe('在制')
    expect(periodCloseSnapshotStageLabel('LEGACY_STAGE')).toBe('未知阶段')
  })
})
