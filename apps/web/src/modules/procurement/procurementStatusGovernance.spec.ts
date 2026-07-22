import { describe, expect, it } from 'vitest'
import { formatStatusRiskList, scanStatusLanguage } from '../../test/statusLanguageScan'

const procurementVueSources = import.meta.glob<string>('./**/*.vue', {
  eager: true,
  import: 'default',
  query: '?raw',
})

const procurementTsSources = import.meta.glob<string>('./**/*.ts', {
  eager: true,
  import: 'default',
  query: '?raw',
})

const directServerStatusNamePattern = /\{\{\s*[^}]*\b(?:statusName|approvalStatusName|inTransitStatusName)\b[^}]*\}\}/g
const inlineQueryFormPattern = /<el-form\b[^>]*class="query-form"[^>]*\binline\b/g
const rightFixedActionColumnPattern = /<el-table-column\b(?=[^>]*label="操作")(?=[^>]*fixed="right")[^>]*>/g
const cancelledDangerTagPattern = /CANCELLED:\s*['"]danger['"]/g

function fileLabel(key: string): string {
  return `apps/web/src/modules/procurement/${key.replace(/^\.\//, '')}`
}

function lineNumberAt(text: string, index: number): number {
  return text.slice(0, index).split(/\r?\n/).length
}

function directServerStatusNameDisplays(): string[] {
  return Object.entries({
    ...procurementVueSources,
    ...procurementTsSources,
  })
    .filter(([key]) => !key.endsWith('.spec.ts'))
    .flatMap(([key, text]) => Array.from(text.matchAll(directServerStatusNamePattern)).map((match) => {
      const line = lineNumberAt(text, match.index ?? 0)
      const evidence = text.split(/\r?\n/)[line - 1]?.trim() ?? ''
      return `${fileLabel(key)}:${line} ${evidence}`
    }))
    .filter((evidence) => !/(?:Label|Display)\s*\(/.test(evidence))
}

function vuePatternMatches(pattern: RegExp): string[] {
  return Object.entries(procurementVueSources)
    .flatMap(([key, text]) => Array.from(text.matchAll(pattern)).map((match) => {
      const line = lineNumberAt(text, match.index ?? 0)
      const evidence = text.split(/\r?\n/)[line - 1]?.trim() ?? ''
      return `${fileLabel(key)}:${line} ${evidence}`
    }))
}

function productionPatternMatches(pattern: RegExp): string[] {
  return Object.entries({
    ...procurementVueSources,
    ...procurementTsSources,
  })
    .filter(([key]) => !key.endsWith('.spec.ts'))
    .flatMap(([key, text]) => Array.from(text.matchAll(pattern)).map((match) => {
      const line = lineNumberAt(text, match.index ?? 0)
      const evidence = text.split(/\r?\n/)[line - 1]?.trim() ?? ''
      return `${fileLabel(key)}:${line} ${evidence}`
    }))
}

describe('采购页面状态语言治理', () => {
  it('采购目录的全局状态语言门禁命中为 0', () => {
    const risks = scanStatusLanguage().risks
      .filter((item) => item.sourceFile.startsWith('apps/web/src/modules/procurement/'))

    expect(risks, formatStatusRiskList(risks)).toHaveLength(0)
  })

  it('采购页面不直接输出服务端状态名，必须通过本域 helper 处理原码兜底', () => {
    expect(directServerStatusNameDisplays()).toEqual([])
  })

  it('采购列表查询栏不保留旧 inline 结构', () => {
    expect(vuePatternMatches(inlineQueryFormPattern)).toEqual([])
  })

  it('采购宽表操作列通过表格内部横向滚动可达，不保留右固定列遮挡风险', () => {
    expect(vuePatternMatches(rightFixedActionColumnPattern)).toEqual([])
  })

  it('采购取消状态不使用失败色', () => {
    expect(productionPatternMatches(cancelledDangerTagPattern)).toEqual([])
  })
})
