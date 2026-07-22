import { describe, expect, it } from 'vitest'

import { formatStatusRiskList, scanStatusLanguage } from '../../test/statusLanguageScan'
import { governanceErrorLabel } from './platformGovernanceLabels'
import {
  documentTaskStageLabel,
  documentTaskStatusLabel,
} from './platformPageHelpers'

const platformVueSources = import.meta.glob<string>('./**/*.vue', {
  eager: true,
  import: 'default',
  query: '?raw',
}) as Record<string, string>

const expectedPlatformSurfaces = [
  './approvals/ApprovalCenterView.vue',
  './components/ApprovalStatusPanel.vue',
  './components/AttachmentPanel.vue',
  './components/BatchStatusToolPanel.vue',
  './components/FixedPrintAction.vue',
  './components/PrintAction.vue',
  './dataRepairs/DataRepairCreateView.vue',
  './dataRepairs/DataRepairDetailView.vue',
  './dataRepairs/DataRepairListView.vue',
  './deliveryAssets/DeliveryAssetsView.vue',
  './documentTasks/DocumentTaskCenterView.vue',
  './historyImports/HistoryImportDetailView.vue',
  './historyImports/HistoryImportListView.vue',
  './messages/MessageCenterView.vue',
]

function sourceLabel(path: string): string {
  return `apps/web/src/modules/platform/${path.replace(/^\.\//, '')}`
}

function lineNumber(source: string, index: number): number {
  return source.slice(0, index).split('\n').length
}

function patternMatches(pattern: RegExp): string[] {
  const matches: string[] = []
  for (const [path, source] of Object.entries(platformVueSources)) {
    for (const match of source.matchAll(pattern)) {
      matches.push(`${sourceLabel(path)}:${lineNumber(source, match.index ?? 0)} ${match[0].trim()}`)
    }
  }
  return matches.sort()
}

describe('平台残留治理静态守卫', () => {
  it('覆盖全部十四个 platform Vue 表面', () => {
    expect(Object.keys(platformVueSources).sort()).toEqual(expectedPlatformSurfaces)
  })

  it('platform 目录状态语言扫描归零', () => {
    const platformRisks = scanStatusLanguage().risks.filter((risk) =>
      risk.sourceFile.startsWith('apps/web/src/modules/platform/'))

    expect(platformRisks, formatStatusRiskList(platformRisks)).toEqual([])
  })

  it('platform 表格操作列统一遵守右固定 184px 契约且不直接绑定状态语义列', () => {
    const actionColumns = patternMatches(
      /<el-table-column\b(?=[^>]*\blabel=["']操作["'])[^>]*>/g,
    )
    const directSemanticColumns = patternMatches(
      /<el-table-column\b[^>]*(?:prop|property)=["'](?:action|reasonCode|resultStatus|sourceType|stage|status|type)["'][^>]*>/g,
    )

    expect(actionColumns).toHaveLength(10)
    expect(actionColumns.filter((column) => !/\bfixed=["']right["']/.test(column))).toEqual([])
    expect(actionColumns.filter((column) => !/\bwidth=["']184["']/.test(column))).toEqual([])
    expect(actionColumns.filter((column) => /\bmin-width=/.test(column))).toEqual([])
    expect(directSemanticColumns).toEqual([])
  })

  it('平台错误码和未知业务域不作为用户主文案回退', () => {
    const rawCodeFallbacks = patternMatches(
      /row\.message\s*\?\?\s*row\.summary\s*\?\?\s*row\.code|row\.errorCode\s*\|\|\s*row\.code|label\s*\?\?\s*domain\s*\?\?\s*'-'/g,
    )

    expect(rawCodeFallbacks).toEqual([])
    expect(governanceErrorLabel('HISTORY_IMPORT_ALREADY_EXISTS')).toBe('历史导入记录已存在')
    expect(governanceErrorLabel('UNKNOWN_PLATFORM_CODE')).toBe('未知错误')
    expect(documentTaskStageLabel('VALIDATE')).toBe('校验')
    expect(documentTaskStatusLabel('READY_TO_COMMIT')).toBe('待确认')
  })
})
