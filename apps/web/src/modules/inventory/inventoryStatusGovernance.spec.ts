import { describe, expect, it } from 'vitest'
import { formatStatusRiskList, scanStatusLanguage } from '../../test/statusLanguageScan'

const inventoryVueSources = import.meta.glob<string>('./**/*.vue', {
  eager: true,
  import: 'default',
  query: '?raw',
}) as Record<string, string>

const directServerNameColumnPattern =
  /<el-table-column\b[^>]*(?:prop|property)=["'](?:statusName|qualityStatusName|stockStatusName|sourceTypeName|nodeTypeName|reservationTypeName|trackingMethodName|valuationStateName|valuationMethodName)["'][^>]*>/g

describe('库存目录状态语言治理', () => {
  it('库存模块用户可见状态不再存在原码主文案回退', () => {
    const targetRisks = scanStatusLanguage().risks.filter((item) =>
      item.sourceFile.startsWith('apps/web/src/modules/inventory/'))

    expect(targetRisks, formatStatusRiskList(targetRisks)).toHaveLength(0)
  })

  it('库存模块状态类服务端名称列必须经过中文兜底 helper', () => {
    const directColumns = Object.entries(inventoryVueSources).flatMap(([file, source]) =>
      Array.from(source.matchAll(directServerNameColumnPattern)).map((match) => `${file}: ${match[0]}`))

    expect(directColumns).toEqual([])
  })
})
