import { describe, expect, it } from 'vitest'
import { formatStatusRiskList, scanStatusLanguage } from '../../test/statusLanguageScan'
import {
  outsourcingOrderStatusLabel,
  outsourcingOrderStatusType,
  productionDocumentStatusLabel,
  productionDocumentStatusType,
  workOrderStatusLabel,
  workOrderStatusType,
} from './productionPageHelpers'

const productionVueSources = import.meta.glob<string>('./**/*.vue', {
  eager: true,
  import: 'default',
  query: '?raw',
})

const productionTsSources = import.meta.glob<string>('./**/*.ts', {
  eager: true,
  import: 'default',
  query: '?raw',
})

const inlineQueryFormPattern = /<el-form\b[^>]*class="query-form"[^>]*\binline\b/g
const rightFixedActionColumnPattern = /<el-table-column\b(?=[^>]*label="操作")(?=[^>]*fixed="right")[^>]*>/g
const cancelledDangerTagPattern = /CANCELLED:\s*['"]danger['"]/g
const legacyProductionStatusTextPattern = /已发布|执行中|进行中/g

function fileLabel(key: string): string {
  return `apps/web/src/modules/production/${key.replace(/^\.\//, '')}`
}

function lineNumberAt(text: string, index: number): number {
  return text.slice(0, index).split(/\r?\n/).length
}

function vuePatternMatches(pattern: RegExp): string[] {
  return Object.entries(productionVueSources)
    .flatMap(([key, text]) => Array.from(text.matchAll(pattern)).map((match) => {
      const line = lineNumberAt(text, match.index ?? 0)
      const evidence = text.split(/\r?\n/)[line - 1]?.trim() ?? ''
      return `${fileLabel(key)}:${line} ${evidence}`
    }))
}

function sourcePatternMatches(pattern: RegExp): string[] {
  return Object.entries({
    ...productionVueSources,
    ...productionTsSources,
  })
    .filter(([key]) => !key.endsWith('.spec.ts'))
    .flatMap(([key, text]) => Array.from(text.matchAll(pattern)).map((match) => {
      const line = lineNumberAt(text, match.index ?? 0)
      const evidence = text.split(/\r?\n/)[line - 1]?.trim() ?? ''
      return `${fileLabel(key)}:${line} ${evidence}`
    }))
}

describe('生产页面状态语言治理', () => {
  it('生产目录的全局状态语言门禁命中为 0', () => {
    const risks = scanStatusLanguage().risks
      .filter((item) => item.sourceFile.startsWith('apps/web/src/modules/production/'))

    expect(risks, formatStatusRiskList(risks)).toHaveLength(0)
  })

  it('生产列表查询栏不保留旧 inline 结构', () => {
    expect(vuePatternMatches(inlineQueryFormPattern)).toEqual([])
  })

  it('生产宽表操作列通过表格内部横向滚动可达，不保留右固定列遮挡风险', () => {
    expect(vuePatternMatches(rightFixedActionColumnPattern)).toEqual([])
  })

  it('生产取消状态不使用失败色', () => {
    expect(sourcePatternMatches(cancelledDangerTagPattern)).toEqual([])
  })

  it('生产页面不再展示已发布、执行中或进行中的旧状态文案', () => {
    expect(sourcePatternMatches(legacyProductionStatusTextPattern)).toEqual([])
  })

  it('生产工单、生产单据与外协订单使用阶段冻结中文语义和未知兜底', () => {
    expect(workOrderStatusLabel('RELEASED')).toBe('已下达')
    expect(workOrderStatusLabel('IN_PROGRESS')).toBe('生产中')
    expect(workOrderStatusLabel('COMPLETED')).toBe('已完工')
    expect(workOrderStatusLabel('LEGACY_STATUS')).toBe('未知状态')
    expect(workOrderStatusType('CANCELLED')).toBe('info')
    expect(productionDocumentStatusLabel('POSTED')).toBe('已过账')
    expect(productionDocumentStatusLabel('LEGACY_STATUS')).toBe('未知状态')
    expect(productionDocumentStatusType('CANCELLED')).toBe('info')
    expect(outsourcingOrderStatusLabel('RELEASED', '已发布')).toBe('已下达')
    expect(outsourcingOrderStatusLabel('IN_PROGRESS', '进行中')).toBe('加工中')
    expect(outsourcingOrderStatusLabel('COMPLETED')).toBe('已完成')
    expect(outsourcingOrderStatusLabel('LEGACY_STATUS', 'LEGACY_STATUS')).toBe('未知状态')
    expect(outsourcingOrderStatusType('CANCELLED')).toBe('info')
  })
})
