import { describe, expect, it } from 'vitest'
import { buildPageSurfaceInventory } from '../../test/pageSurfaceInventory'
import { formatStatusRiskList, scanStatusLanguage } from '../../test/statusLanguageScan'
import {
  glAccountCategoryText,
  glAllowedActionsText,
  glApprovalStatusText,
  glBalanceDirectionText,
  glFinancialCloseStatusText,
  glPeriodStatusText,
  glPostingRuleStatusText,
  glPostingRuleValidationStatusText,
  glSourceTypeText,
  glVoucherStatusText,
  glVoucherTypeText,
} from './glPageHelpers'

const glVueSources = import.meta.glob<string>('./**/*.vue', {
  eager: true,
  import: 'default',
  query: '?raw',
})

const inlineQueryFormPattern = /<el-form\b[^>]*class="query-form"[^>]*\binline\b/g
const postedVoucherRawTextPattern = /POSTED\s+凭证/g
const voucherPostedLegacyTextPattern = /已过账凭证/g

function fileLabel(key: string): string {
  return `apps/web/src/modules/gl/${key.replace(/^\.\//, '')}`
}

function lineNumberAt(text: string, index: number): number {
  return text.slice(0, index).split(/\r?\n/).length
}

function vuePatternMatches(pattern: RegExp): string[] {
  return Object.entries(glVueSources)
    .flatMap(([key, text]) => Array.from(text.matchAll(pattern)).map((match) => {
      const line = lineNumberAt(text, match.index ?? 0)
      const evidence = text.split(/\r?\n/)[line - 1]?.trim() ?? ''
      return `${fileLabel(key)}:${line} ${evidence}`
    }))
}

describe('总账页面状态语言治理', () => {
  it('总账目录的全局状态语言门禁命中为 0', () => {
    const risks = scanStatusLanguage().risks
      .filter((item) => item.sourceFile.startsWith('apps/web/src/modules/gl/'))

    expect(risks, formatStatusRiskList(risks)).toHaveLength(0)
  })

  it('总账查询栏不保留旧 inline 结构', () => {
    expect(vuePatternMatches(inlineQueryFormPattern)).toEqual([])
  })

  it('总账操作列纳入全局 fixed right 与更多下拉契约门禁', () => {
    const glOperationColumns = buildPageSurfaceInventory().operationColumns
      .filter((column) => column.sourceFile.startsWith('apps/web/src/modules/gl/'))

    expect(glOperationColumns.length).toBeGreaterThan(0)
  })

  it('总账凭证 POSTED 语义固定为已记账，页面不显示 POSTED 凭证或已过账凭证', () => {
    expect(glVoucherStatusText('POSTED')).toBe('已记账')
    expect(vuePatternMatches(postedVoucherRawTextPattern)).toEqual([])
    expect(vuePatternMatches(voucherPostedLegacyTextPattern)).toEqual([])
  })

  it('总账来源、凭证、审批、方向、期间、规则和校验状态使用中文语义与未知兜底', () => {
    expect(glAccountCategoryText('PROFIT_LOSS')).toBe('损益')
    expect(glAccountCategoryText('LEGACY_CATEGORY')).toBe('未知科目类别')
    expect(glBalanceDirectionText('DEBIT')).toBe('借方')
    expect(glBalanceDirectionText('CREDIT')).toBe('贷方')
    expect(glBalanceDirectionText('LEGACY_DIRECTION')).toBe('未知方向')
    expect(glVoucherTypeText('GENERAL')).toBe('普通凭证')
    expect(glVoucherTypeText('LEGACY_TYPE')).toBe('未知凭证类型')
    expect(glVoucherStatusText('LEGACY_STATUS')).toBe('未知状态')
    expect(glApprovalStatusText('SUBMITTED')).toBe('审批中')
    expect(glApprovalStatusText('LEGACY_STATUS')).toBe('未知审批状态')
    expect(glPeriodStatusText('CLOSED')).toBe('已关闭')
    expect(glPeriodStatusText('LEGACY_STATUS')).toBe('未知期间状态')
    expect(glFinancialCloseStatusText('STALE')).toBe('已失效')
    expect(glFinancialCloseStatusText('LEGACY_STATUS')).toBe('未知财务结账状态')
    expect(glSourceTypeText('SALES_INVOICE')).toBe('销售发票')
    expect(glSourceTypeText('LEGACY_SOURCE')).toBe('未知来源')
    expect(glPostingRuleStatusText('ACTIVE')).toBe('已启用')
    expect(glPostingRuleStatusText('LEGACY_STATUS')).toBe('未知规则状态')
    expect(glPostingRuleValidationStatusText('VALID')).toBe('校验通过')
    expect(glPostingRuleValidationStatusText('LEGACY_STATUS')).toBe('未知校验状态')
    expect(glAllowedActionsText(['CREATE_CHILD', 'UPDATE', 'LEGACY_ACTION'])).toBe('可新增下级、可编辑、未知动作')
  })
})
