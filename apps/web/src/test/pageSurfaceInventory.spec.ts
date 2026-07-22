import { describe, expect, it } from 'vitest'
import { buildPageSurfaceInventory, summarizePageSurfaceInventory } from './pageSurfaceInventory'

describe('页面治理表面清单门禁', () => {
  it('统计 175 个 View 页面和模块分布', () => {
    const inventory = buildPageSurfaceInventory()

    expect(inventory.moduleVueFiles, summarizePageSurfaceInventory(inventory)).toHaveLength(241)
    expect(inventory.viewFiles, summarizePageSurfaceInventory(inventory)).toHaveLength(175)
    expect(inventory.viewModuleCounts).toEqual({
      auth: 1,
      cost: 10,
      finance: 33,
      financialClose: 9,
      gl: 11,
      inventory: 6,
      master: 7,
      materials: 3,
      periodClose: 4,
      planning: 2,
      platform: 9,
      procurement: 18,
      production: 11,
      quality: 1,
      reports: 16,
      reversal: 15,
      sales: 15,
      system: 4,
    })
  })

  it('记录全部表格实例且不以文件级滚动命中替代实例清单', () => {
    const inventory = buildPageSurfaceInventory()

    expect(inventory.tables, summarizePageSurfaceInventory(inventory)).toHaveLength(245)
    expect(inventory.tableColumns).toHaveLength(2036)
    inventory.tables.forEach((table) => {
      expect(table.sourceFile).toMatch(/^apps\/web\/src\//)
      expect(table.line).toBeGreaterThan(0)
      expect(table).toHaveProperty('hasScrollEvidenceInFile')
      expect(table).toHaveProperty('scrollEvidenceLine')
    })
  })

  it('分页、弹窗、抽屉、returnTo 表面全部有矩阵项', () => {
    const inventory = buildPageSurfaceInventory()

    expect(inventory.paginations).toHaveLength(106)
    expect(inventory.dialogs).toHaveLength(45)
    expect(inventory.drawers).toHaveLength(34)
    expect(inventory.returnContexts.length).toBeGreaterThan(0)
    expect(inventory.fixedRightColumns.length).toBeGreaterThan(0)
    expect(inventory.tooltips.length).toBeGreaterThan(0)
  })

  it('非路由状态标签、抽屉、面板、追溯、编辑器和选择器进入清单', () => {
    const inventory = buildPageSurfaceInventory()

    expect(inventory.namedStatusTags).toHaveLength(28)
    expect(inventory.namedDrawers).toHaveLength(7)
    expect(inventory.namedPanels).toHaveLength(11)
    expect(inventory.namedTraces).toHaveLength(7)
    expect(inventory.namedEditorsOrPickers).toHaveLength(7)
  })
})
