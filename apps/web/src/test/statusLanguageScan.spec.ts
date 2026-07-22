import { describe, expect, it } from 'vitest'
import {
  formatStatusRiskList,
  scanStatusLanguage,
  scanStatusLanguageSources,
  statusLanguageWhitelist,
  validateStatusLanguageWhitelist,
  type StatusLanguageWhitelistEntry,
} from './statusLanguageScan'

const namedResidualSources = import.meta.glob<string>('../modules/{gl,reports,reversal,finance}/**/*.vue', {
  eager: true,
  import: 'default',
  query: '?raw',
}) as Record<string, string>

function sourceKey(file: string): string {
  return `../${file.replace(/^apps\/web\/src\//, '')}`
}

describe('页面治理状态语言静态门禁', () => {
  it('扫描生产 modules/shared 源文件并排除 spec', () => {
    const result = scanStatusLanguage()

    expect(result.scannedFiles.length).toBeGreaterThan(0)
    expect(result.scannedFiles.every((file) => file.startsWith('apps/web/src/modules/') || file.startsWith('apps/web/src/shared/'))).toBe(true)
    expect(result.scannedFiles.every((file) => !file.endsWith('.spec.ts'))).toBe(true)
    expect(result.whitelistErrors).toEqual([])
  })

  it('每条状态风险都能定位到文件、行、字段和语境', () => {
    const result = scanStatusLanguage()

    result.risks.forEach((item) => {
      expect(item.sourceFile).toMatch(/^apps\/web\/src\//)
      expect(item.line).toBeGreaterThan(0)
      expect(item.field).not.toBe('')
      expect(item.context).not.toBe('')
      expect(item.evidence).not.toBe('')
      expect(item.classification).toBe('unclassified-user-visible-risk')
    })
  })

  it('白名单契约禁止目录级、通配级和所有大写词级豁免', () => {
    const invalidWhitelist: StatusLanguageWhitelistEntry[] = [
      {
        value: 'ALL_CAPS',
        context: '所有大写词',
        file: 'apps/web/src/modules/**/*.vue',
        rule: '所有大写词全部豁免',
        reason: '',
        userVisible: true,
      },
    ]

    expect(statusLanguageWhitelist).toEqual([])
    expect(validateStatusLanguageWhitelist(invalidWhitelist)).toEqual([
      '白名单第 1 项 文件必须精确到单个文件，禁止通配',
      '白名单第 1 项 规则不能是目录级或通配级豁免',
      '白名单第 1 项 缺少允许原因',
    ])
  })

  it('扫描模板普通可见文本节点中的状态编码，但不误报业务技术缩写', () => {
    const result = scanStatusLanguageSources({
      '../modules/sales/quotes/VisibleTextFixture.vue': `
        <template>
          <select name="project">
            <option value="">请选择 ACTIVE 项目与同客户 EFFECTIVE 合同</option>
          </select>
          <p>BOM 与 SKU 编码由用户录入维护，API 文档另见接口说明</p>
        </template>
      `,
    })

    expect(result.risks).toEqual([
      expect.objectContaining({
        kind: 'template-visible-text-code',
        sourceFile: 'apps/web/src/modules/sales/quotes/VisibleTextFixture.vue',
        field: 'ACTIVE, EFFECTIVE',
      }),
    ])
  })

  it('扫描模板用户可见输出中 allowedActions 或 availableActions 的原始动作码直出', () => {
    const result = scanStatusLanguageSources({
      '../modules/gl/ActionOutputFixture.vue': `
        <template>
          <el-table-column label="动作状态">
            <template #default="{ row }">{{ row.allowedActions?.join('、') || '-' }}</template>
          </el-table-column>
          <span>{{ task.availableActions }}</span>
        </template>
        <script setup lang="ts">
        const hidden = model.allowedActions?.join(',')
        </script>
      `,
    })

    expect(result.risks).toEqual([
      expect.objectContaining({
        kind: 'user-visible-action-code-output',
        field: 'row.allowedActions',
      }),
      expect.objectContaining({
        kind: 'user-visible-action-code-output',
        field: 'task.availableActions',
      }),
    ])
  })

  it('集中审查点名的状态残留若仍存在，必须被扫描规则解释到风险清单', () => {
    const result = scanStatusLanguage()
    const namedResiduals = [
      { file: 'apps/web/src/modules/gl/GlVoucherWorkbenchView.vue', value: 'MANUAL / FIN_VOUCHER_DRAFT' },
      { file: 'apps/web/src/modules/gl/GlAccountsView.vue', value: 'row.allowedActions?.join' },
      { file: 'apps/web/src/modules/gl/GlPostingRulesView.vue', value: 'SALES_INVOICE' },
      { file: 'apps/web/src/modules/gl/GlPostingRulesView.vue', value: 'DEFAULT' },
      { file: 'apps/web/src/modules/reports/FinancialSummaryReportView.vue', value: 'LIVE' },
      { file: 'apps/web/src/modules/reports/ProcurementVarianceReportView.vue', value: 'PROJECT 或 PUBLIC' },
      { file: 'apps/web/src/modules/reversal/SalesReturnDetailView.vue', value: 'record.source.status ||' },
      { file: 'apps/web/src/modules/reversal/PurchaseReturnDetailView.vue', value: 'record.source.status ||' },
      { file: 'apps/web/src/modules/finance/PaymentDetailView.vue', value: 'record.prepaymentStatus ??' },
      { file: 'apps/web/src/modules/finance/ReceiptDetailView.vue', value: 'record.advanceReceiptStatus ??' },
      { file: 'apps/web/src/modules/sales/quotes/SalesQuoteListView.vue', value: '请选择 ACTIVE 项目与同客户 EFFECTIVE 合同' },
      { file: 'apps/web/src/modules/sales/quotes/SalesQuoteDetailView.vue', value: '请选择 ACTIVE 项目与同客户 EFFECTIVE 合同' },
    ]

    namedResiduals.forEach((residual) => {
      const source = namedResidualSources[sourceKey(residual.file)] ?? ''
      if (!source.includes(residual.value)) {
        return
      }
      expect(
        result.risks.some((item) => item.sourceFile === residual.file && item.evidence.includes(residual.value.split(' ')[0] ?? residual.value)),
        `${residual.file} 中仍存在 ${residual.value}，但状态语言扫描没有产生风险`,
      ).toBe(true)
    })
  })

  it('非白名单用户可见状态原值回退必须清零', () => {
    const result = scanStatusLanguage()

    expect(result.risks, formatStatusRiskList(result.risks)).toHaveLength(0)
  })
})
