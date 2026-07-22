import { describe, expect, it } from 'vitest'
import {
  formatStatusRiskList,
  scanStatusLanguage,
  statusLanguageWhitelist,
  validateStatusLanguageWhitelist,
  type StatusLanguageWhitelistEntry,
} from './statusLanguageScan'

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

  it('非白名单用户可见状态原值回退必须清零', () => {
    const result = scanStatusLanguage()

    expect(result.risks, formatStatusRiskList(result.risks)).toHaveLength(0)
  })
})
