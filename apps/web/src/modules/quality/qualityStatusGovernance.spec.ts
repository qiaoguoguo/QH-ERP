import { describe, expect, it } from 'vitest'
import { formatStatusRiskList, scanStatusLanguage } from '../../test/statusLanguageScan'

const qualityVueSources = import.meta.glob<string>('./**/*.vue', {
  eager: true,
  import: 'default',
  query: '?raw',
}) as Record<string, string>

const directServerNameColumnPattern =
  /<el-table-column\b[^>]*(?:prop|property)=["'](?:statusName|qualityStatusName|sourceTypeName)["'][^>]*>/g

describe('质量目录状态语言治理', () => {
  it('质量模块用户可见状态不再存在原码主文案回退', () => {
    const targetRisks = scanStatusLanguage().risks.filter((item) =>
      item.sourceFile.startsWith('apps/web/src/modules/quality/'))

    expect(targetRisks, formatStatusRiskList(targetRisks)).toHaveLength(0)
  })

  it('质量模块状态类服务端名称列必须经过中文兜底 helper', () => {
    const directColumns = Object.entries(qualityVueSources).flatMap(([file, source]) =>
      Array.from(source.matchAll(directServerNameColumnPattern)).map((match) => `${file}: ${match[0]}`))

    expect(directColumns).toEqual([])
  })
})
