import { describe, expect, it } from 'vitest'
import { formatStatusRiskList, scanStatusLanguage } from '../../test/statusLanguageScan'
import {
  financeMethodText,
  financeSourceTypeText,
  invoiceStatusText,
  matchStatusText,
  payableStatusTagType,
  paymentStatusTagType,
  receiptStatusTagType,
  receivableStatusTagType,
  settlementStatusText,
  voucherBusinessCategoryText,
  voucherDraftStatusText,
} from './financePageHelpers'

const financeVueSources = import.meta.glob<string>('./**/*.vue', {
  eager: true,
  import: 'default',
  query: '?raw',
})

const inlineQueryFormPattern = /<el-form\b[^>]*class="query-form"[^>]*\binline\b/g
const actionColumnPattern = /<el-table-column\b(?=[^>]*label="操作")[^>]*>/g

function fileLabel(key: string): string {
  return `apps/web/src/modules/finance/${key.replace(/^\.\//, '')}`
}

function lineNumberAt(text: string, index: number): number {
  return text.slice(0, index).split(/\r?\n/).length
}

function vuePatternMatches(pattern: RegExp): string[] {
  return Object.entries(financeVueSources)
    .flatMap(([key, text]) => Array.from(text.matchAll(pattern)).map((match) => {
      const line = lineNumberAt(text, match.index ?? 0)
      const evidence = text.split(/\r?\n/)[line - 1]?.trim() ?? ''
      return `${fileLabel(key)}:${line} ${evidence}`
    }))
}

describe('财务页面状态语言治理', () => {
  it('财务目录的全局状态语言门禁命中为 0', () => {
    const risks = scanStatusLanguage().risks
      .filter((item) => item.sourceFile.startsWith('apps/web/src/modules/finance/'))

    expect(risks, formatStatusRiskList(risks)).toHaveLength(0)
  })

  it('财务查询栏不保留旧 inline 结构', () => {
    expect(vuePatternMatches(inlineQueryFormPattern)).toEqual([])
  })

  it('财务操作列统一固定在右侧、宽度 184 且不保留 min-width', () => {
    const actionColumns = vuePatternMatches(actionColumnPattern)

    expect(actionColumns.length).toBeGreaterThan(0)
    expect(actionColumns.filter((column) => (
      !column.includes('fixed="right"')
      || !column.includes('width="184"')
      || column.includes('min-width')
    ))).toEqual([])
  })

  it('财务状态色遵守取消中性、过账完成为成功的语义', () => {
    expect(receivableStatusTagType.CANCELLED).toBe('info')
    expect(payableStatusTagType.CANCELLED).toBe('info')
    expect(receiptStatusTagType.POSTED).toBe('success')
    expect(receiptStatusTagType.CANCELLED).toBe('info')
    expect(paymentStatusTagType.POSTED).toBe('success')
    expect(paymentStatusTagType.CANCELLED).toBe('info')
  })

  it('财务来源、发票、匹配、结算、凭证草稿和方式使用中文语义与未知兜底', () => {
    expect(financeSourceTypeText('SALES_SHIPMENT')).toBe('销售出库')
    expect(financeSourceTypeText('LEGACY_SOURCE')).toBe('未知来源')
    expect(invoiceStatusText('CONFIRMED')).toBe('已确认')
    expect(invoiceStatusText('LEGACY_STATUS')).toBe('未知状态')
    expect(settlementStatusText('POSTED')).toBe('已过账')
    expect(settlementStatusText('LEGACY_STATUS')).toBe('未知结算状态')
    expect(matchStatusText('EXCEPTION')).toBe('存在差异')
    expect(matchStatusText('LEGACY_STATUS')).toBe('未知匹配状态')
    expect(voucherDraftStatusText('READY')).toBe('待正式制证')
    expect(voucherDraftStatusText('LEGACY_STATUS')).toBe('未知凭证草稿状态')
    expect(voucherBusinessCategoryText('RECEIPT_DRAFT')).toBe('收款草稿')
    expect(voucherBusinessCategoryText('LEGACY_CATEGORY')).toBe('未知凭证业务类别')
    expect(financeMethodText('BANK_TRANSFER')).toBe('银行转账')
    expect(financeMethodText('LEGACY_METHOD')).toBe('未知结算方式')
  })
})
