import { describe, expect, it } from 'vitest'
import { formatStatusRiskList, scanStatusLanguage } from '../../test/statusLanguageScan'
import {
  bankReconciliationExceptionText,
  bankAccountTypeText,
  bankDirectionText,
  financialCloseBalanceDirectionText,
  financialCloseSeverityText,
  financialCloseStatusText,
  sourceTypeText,
  taxpayerTypeText,
  taxTypeText,
} from './financialClosePageHelpers'

const financialCloseVueSources = import.meta.glob<string>('./**/*.vue', {
  eager: true,
  import: 'default',
  query: '?raw',
})

const inlineQueryFormPattern = /<el-form\b[^>]*class="query-form"[^>]*\binline\b/g
const rightFixedActionColumnPattern = /<el-table-column\b(?=[^>]*label="操作")(?=[^>]*fixed="right")[^>]*>/g

function fileLabel(key: string): string {
  return `apps/web/src/modules/financialClose/${key.replace(/^\.\//, '')}`
}

function lineNumberAt(text: string, index: number): number {
  return text.slice(0, index).split(/\r?\n/).length
}

function vuePatternMatches(pattern: RegExp): string[] {
  return Object.entries(financialCloseVueSources)
    .flatMap(([key, text]) => Array.from(text.matchAll(pattern)).map((match) => {
      const line = lineNumberAt(text, match.index ?? 0)
      const evidence = text.split(/\r?\n/)[line - 1]?.trim() ?? ''
      return `${fileLabel(key)}:${line} ${evidence}`
    }))
}

describe('财务关账页面状态语言治理', () => {
  it('财务关账目录的全局状态语言门禁命中为 0', () => {
    const risks = scanStatusLanguage().risks
      .filter((item) => item.sourceFile.startsWith('apps/web/src/modules/financialClose/'))

    expect(risks, formatStatusRiskList(risks)).toHaveLength(0)
  })

  it('财务关账查询栏不保留旧 inline 结构', () => {
    expect(vuePatternMatches(inlineQueryFormPattern)).toEqual([])
  })

  it('财务关账宽表操作列通过表格内部横向滚动可达，不保留右固定列遮挡风险', () => {
    expect(vuePatternMatches(rightFixedActionColumnPattern)).toEqual([])
  })

  it('关账、银行、税务和来源状态使用中文语义与未知兜底', () => {
    expect(financialCloseStatusText('READY')).toBe('可结账')
    expect(financialCloseStatusText('STALE')).toBe('已失效')
    expect(financialCloseStatusText('POSTED')).toBe('已记账')
    expect(financialCloseStatusText('LEGACY_STATUS')).toBe('未知状态')
    expect(bankDirectionText('CREDIT')).toBe('银行入账')
    expect(bankDirectionText('LEGACY_DIRECTION')).toBe('未知方向')
    expect(financialCloseBalanceDirectionText('DEBIT')).toBe('借方')
    expect(financialCloseBalanceDirectionText('LEGACY_DIRECTION')).toBe('未知方向')
    expect(bankAccountTypeText('BASIC')).toBe('基本户')
    expect(bankAccountTypeText('LEGACY_TYPE')).toBe('未知账户类型')
    expect(taxTypeText('VAT')).toBe('增值税')
    expect(taxTypeText('LEGACY_TAX')).toBe('未知税种')
    expect(taxpayerTypeText('GENERAL')).toBe('一般纳税人')
    expect(taxpayerTypeText('LEGACY_TAXPAYER')).toBe('未知纳税人类型')
    expect(sourceTypeText('FIN_VOUCHER_DRAFT')).toBe('财务凭证草稿')
    expect(sourceTypeText('LEGACY_SOURCE')).toBe('未知来源')
    expect(financialCloseSeverityText('BLOCKER')).toBe('阻断')
    expect(financialCloseSeverityText('LEGACY_SEVERITY')).toBe('未知级别')
    expect(bankReconciliationExceptionText('BANK_ONLY_CREDIT')).toBe('银行已收企业未收')
    expect(bankReconciliationExceptionText('LEGACY_EXCEPTION')).toBe('未知未达类型')
  })
})
