import { describe, expect, it } from 'vitest'
import { buildPageSurfaceInventory, formatOperationColumnViolations } from '../../test/pageSurfaceInventory'
import { formatStatusRiskList, scanStatusLanguage } from '../../test/statusLanguageScan'
import { procurementRequisitionStatusLabel } from './procurementPageHelpers'

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

  it('采购操作列遵循全局 fixed right 与更多下拉展示契约', () => {
    const procurementViolations = buildPageSurfaceInventory().operationColumnViolations
      .filter((item) => item.sourceFile.startsWith('apps/web/src/modules/procurement/'))

    expect(procurementViolations, formatOperationColumnViolations(procurementViolations)).toEqual([])
  })

  it('采购取消状态不使用失败色', () => {
    expect(productionPatternMatches(cancelledDangerTagPattern)).toEqual([])
  })

  it('采购请购状态名只采信中文，英文或原码回到确定性中文映射', () => {
    expect(procurementRequisitionStatusLabel('APPROVED', 'Approved')).toBe('已批准')
    expect(procurementRequisitionStatusLabel('APPROVED', 'APPROVED')).toBe('已批准')
    expect(procurementRequisitionStatusLabel('APPROVED', '服务端已批准')).toBe('服务端已批准')
  })
})
