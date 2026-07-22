import { describe, expect, it } from 'vitest'
import { formatStatusRiskList, scanStatusLanguage } from '../../test/statusLanguageScan'

const salesVueSources = import.meta.glob<string>('./**/*.vue', {
  eager: true,
  import: 'default',
  query: '?raw',
})

const salesTsSources = import.meta.glob<string>('./**/*.ts', {
  eager: true,
  import: 'default',
  query: '?raw',
})

const inlineQueryFormPattern = /<el-form\b[^>]*class="query-form"[^>]*\binline\b/g
const rightFixedActionColumnPattern = /<el-table-column\b(?=[^>]*label="操作")(?=[^>]*fixed="right")[^>]*>/g
const directStatusLabelMapPattern = /\b[A-Za-z_$][\w$]*(?:Labels|labels)\[[^\]]*\bstatus\b[^\]]*\]/g
const cancelledDangerTagPattern = /CANCELLED:\s*['"]danger['"]/g

function fileLabel(key: string): string {
  return `apps/web/src/modules/sales/${key.replace(/^\.\//, '')}`
}

function lineNumberAt(text: string, index: number): number {
  return text.slice(0, index).split(/\r?\n/).length
}

function vuePatternMatches(pattern: RegExp): string[] {
  return Object.entries(salesVueSources)
    .flatMap(([key, text]) => Array.from(text.matchAll(pattern)).map((match) => {
      const line = lineNumberAt(text, match.index ?? 0)
      const evidence = text.split(/\r?\n/)[line - 1]?.trim() ?? ''
      return `${fileLabel(key)}:${line} ${evidence}`
    }))
}

function productionPatternMatches(pattern: RegExp): string[] {
  return Object.entries({
    ...salesVueSources,
    ...salesTsSources,
  })
    .filter(([key]) => !key.endsWith('.spec.ts'))
    .flatMap(([key, text]) => Array.from(text.matchAll(pattern)).map((match) => {
      const line = lineNumberAt(text, match.index ?? 0)
      const evidence = text.split(/\r?\n/)[line - 1]?.trim() ?? ''
      return `${fileLabel(key)}:${line} ${evidence}`
    }))
}

describe('销售页面状态语言治理', () => {
  it('销售目录的全局状态语言门禁命中为 0', () => {
    const risks = scanStatusLanguage().risks
      .filter((item) => item.sourceFile.startsWith('apps/web/src/modules/sales/'))

    expect(risks, formatStatusRiskList(risks)).toHaveLength(0)
  })

  it('销售列表查询栏不保留旧 inline 结构', () => {
    expect(vuePatternMatches(inlineQueryFormPattern)).toEqual([])
  })

  it('销售宽表操作列通过表格内部横向滚动可达，不保留右固定列遮挡风险', () => {
    expect(vuePatternMatches(rightFixedActionColumnPattern)).toEqual([])
  })

  it('销售页面状态摘要不使用局部字典直出，必须复用本域 helper 的未知兜底', () => {
    expect(vuePatternMatches(directStatusLabelMapPattern)).toEqual([])
  })

  it('销售取消状态不使用失败色', () => {
    expect(productionPatternMatches(cancelledDangerTagPattern)).toEqual([])
  })
})
