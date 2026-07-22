import { buildRouteInventory } from './pageRouteInventory'

type RawSourceMap = Record<string, string>

export type SurfaceKind =
  | 'date-picker'
  | 'dialog'
  | 'drawer'
  | 'empty'
  | 'fixed-right-column'
  | 'operation-column'
  | 'pagination'
  | 'query-form-inline'
  | 'return-context'
  | 'select'
  | 'table'
  | 'table-column'
  | 'table-scroll'
  | 'tooltip'

export interface SurfaceOccurrence {
  kind: SurfaceKind
  sourceFile: string
  line: number
  text: string
}

export interface TableSurfaceOccurrence extends SurfaceOccurrence {
  scrollEvidenceLine: number | null
  hasInstanceScrollEvidence: boolean
  hasScrollEvidenceInFile: boolean
}

export interface NonScrollableTableConclusion {
  sourceFile: string
  line: number
  reason: string
}

export interface PaginationRouteRequirement {
  path: string
  name: string | null
  componentSourceFile: string
  actualCount: number
  requiredCount: number
  exemptionReason: string | null
}

export type OperationColumnViolationKind =
  | 'missing-fixed-right'
  | 'missing-more-dropdown'
  | 'min-width-disallowed'
  | 'too-many-direct-actions'
  | 'wrong-width'

export interface OperationColumnOccurrence extends SurfaceOccurrence {
  fixedValue: string | null
  widthValue: string | null
  minWidthValue: string | null
  directActionCount: number
  hasMoreDropdown: boolean
}

export interface OperationColumnViolation {
  kind: OperationColumnViolationKind
  sourceFile: string
  line: number
  evidence: string
  detail: string
}

export interface PageSurfaceInventory {
  moduleVueFiles: string[]
  sharedVueFiles: string[]
  viewFiles: string[]
  viewModuleCounts: Record<string, number>
  tables: TableSurfaceOccurrence[]
  tablesMissingScrollEvidence: TableSurfaceOccurrence[]
  tableColumns: SurfaceOccurrence[]
  paginations: SurfaceOccurrence[]
  paginationRequirements: PaginationRouteRequirement[]
  paginationViolations: PaginationRouteRequirement[]
  paginationExemptions: PaginationRouteRequirement[]
  dialogs: SurfaceOccurrence[]
  drawers: SurfaceOccurrence[]
  empties: SurfaceOccurrence[]
  datePickers: SurfaceOccurrence[]
  selects: SurfaceOccurrence[]
  inlineQueryForms: SurfaceOccurrence[]
  tableScrolls: SurfaceOccurrence[]
  fixedRightColumns: SurfaceOccurrence[]
  operationColumns: OperationColumnOccurrence[]
  operationColumnViolations: OperationColumnViolation[]
  tooltips: SurfaceOccurrence[]
  returnContexts: SurfaceOccurrence[]
  namedStatusTags: string[]
  namedDrawers: string[]
  namedPanels: string[]
  namedTraces: string[]
  namedEditorsOrPickers: string[]
}

const moduleVueSources = import.meta.glob<string>('../modules/**/*.vue', {
  eager: true,
  import: 'default',
  query: '?raw',
}) as RawSourceMap

const sharedVueSources = import.meta.glob<string>('../shared/**/*.vue', {
  eager: true,
  import: 'default',
  query: '?raw',
}) as RawSourceMap

export const nonScrollableTableConclusions: NonScrollableTableConclusion[] = []

export const paginationRouteExemptions: Array<{ path: string; reason: string }> = [
  { path: '/gl/trial-balance', reason: '阶段说明记录：试算结果为计算型宽表，无分页接口' },
  { path: '/materials/categories', reason: '阶段说明记录：物料分类为树形维护表面，无分页接口' },
  { path: '/platform/delivery-assets', reason: '阶段说明记录：交付资料为固定清单表面，无分页接口' },
]

function sourceLabel(key: string): string {
  return `apps/web/src/${key.replace(/^\.\.\//, '')}`
}

function lineNumberAt(text: string, index: number): number {
  return text.slice(0, index).split(/\r?\n/).length
}

function lineTextAt(text: string, line: number): string {
  return text.split(/\r?\n/)[line - 1]?.trim() ?? ''
}

function basename(file: string): string {
  return file.split('/').at(-1) ?? file
}

function moduleName(file: string): string {
  if (!file.startsWith('../modules/')) {
    return 'shared'
  }
  return file.replace('../modules/', '').split('/')[0]
}

function collectOccurrences(kind: SurfaceKind, sourceKey: string, sourceText: string, pattern: RegExp): SurfaceOccurrence[] {
  return Array.from(sourceText.matchAll(pattern)).map((match) => {
    const line = lineNumberAt(sourceText, match.index ?? 0)
    return {
      kind,
      sourceFile: sourceLabel(sourceKey),
      line,
      text: lineTextAt(sourceText, line),
    }
  })
}

function collectTableOccurrences(sourceKey: string, sourceText: string): TableSurfaceOccurrence[] {
  const scrollLines = collectOccurrences(
    'table-scroll',
    sourceKey,
    sourceText,
    /\b(?:table-scroll|report-table-scroll)\b/g,
  ).map((item) => item.line)

  return collectOccurrences('table', sourceKey, sourceText, /<el-table(?:\s|>)/g).map((item) => {
    const nearestScrollBefore = scrollLines
      .filter((line) => line <= item.line && item.line - line <= 6)
      .sort((left, right) => right - left)[0] ?? null
    return {
      ...item,
      scrollEvidenceLine: nearestScrollBefore,
      hasInstanceScrollEvidence: nearestScrollBefore !== null,
      hasScrollEvidenceInFile: scrollLines.length > 0,
    }
  })
}

function attributeValue(openingTag: string, name: string): string | null {
  const escapedName = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = openingTag.match(new RegExp(`(?:^|\\s)${escapedName}\\s*=\\s*["']([^"']+)["']`))
  return match?.[1] ?? null
}

function countDirectActions(blockText: string): number {
  const withoutDropdowns = blockText.replace(/<el-dropdown\b[\s\S]*?<\/el-dropdown>/g, '')
  return Array.from(withoutDropdowns.matchAll(/<(?:el-button|el-link|RouterLink|router-link)\b/g)).length
}

function collectOperationColumns(sourceKey: string, sourceText: string): OperationColumnOccurrence[] {
  return Array.from(sourceText.matchAll(/<el-table-column\b(?=[^>]*\blabel\s*=\s*["']操作["'])[^>]*(?:\/>|>[\s\S]*?<\/el-table-column>)/g)).map((match) => {
    const blockText = match[0]
    const line = lineNumberAt(sourceText, match.index ?? 0)
    const openingTag = blockText.match(/^<el-table-column\b[^>]*>/)?.[0] ?? blockText.split(/\r?\n/)[0] ?? blockText
    return {
      kind: 'operation-column',
      sourceFile: sourceLabel(sourceKey),
      line,
      text: lineTextAt(sourceText, line),
      fixedValue: attributeValue(openingTag, 'fixed'),
      widthValue: attributeValue(openingTag, 'width'),
      minWidthValue: attributeValue(openingTag, 'min-width'),
      directActionCount: countDirectActions(blockText),
      hasMoreDropdown: /<el-dropdown\b[\s\S]*?(?:更多|more)/i.test(blockText) || /<(?:MoreActions|OperationMore)\b/.test(blockText),
    }
  })
}

function operationColumnViolations(column: OperationColumnOccurrence): OperationColumnViolation[] {
  const violations: OperationColumnViolation[] = []
  const base = {
    sourceFile: column.sourceFile,
    line: column.line,
    evidence: column.text,
  }
  if (column.fixedValue !== 'right') {
    violations.push({
      ...base,
      kind: 'missing-fixed-right',
      detail: `fixed=${column.fixedValue ?? '缺失'}，要求 fixed="right"`,
    })
  }
  if (column.widthValue !== '184') {
    violations.push({
      ...base,
      kind: 'wrong-width',
      detail: `width=${column.widthValue ?? '缺失'}，要求 width="184"`,
    })
  }
  if (column.minWidthValue !== null) {
    violations.push({
      ...base,
      kind: 'min-width-disallowed',
      detail: `min-width=${column.minWidthValue}，操作列不得用 min-width 替代固定宽度`,
    })
  }
  if (column.directActionCount > 2) {
    violations.push({
      ...base,
      kind: 'too-many-direct-actions',
      detail: `直显动作 ${column.directActionCount} 个，要求最多 2 个`,
    })
    if (!column.hasMoreDropdown) {
      violations.push({
        ...base,
        kind: 'missing-more-dropdown',
        detail: '存在第三个及以后直显动作时缺少“更多”下拉承载',
      })
    }
  }
  return violations
}

function isNonScrollableTableConcluded(table: TableSurfaceOccurrence): boolean {
  return nonScrollableTableConclusions.some((item) => item.sourceFile === table.sourceFile && item.line === table.line)
}

function collectInlineQueryForms(sourceKey: string, sourceText: string): SurfaceOccurrence[] {
  return collectOccurrences(
    'query-form-inline',
    sourceKey,
    sourceText,
    /<el-form\b(?=[^>]*\bclass=["'][^"']*\bquery-form\b[^"']*["'])(?=[^>]*\binline(?:\s|=|>|$))[^>]*>/g,
  )
}

function collectPaginationRequirements(
  tables: TableSurfaceOccurrence[],
  paginations: SurfaceOccurrence[],
): PaginationRouteRequirement[] {
  const tableFiles = new Set(tables.map((table) => table.sourceFile))
  const paginationCounts = paginations.reduce<Record<string, number>>((counts, pagination) => {
    counts[pagination.sourceFile] = (counts[pagination.sourceFile] ?? 0) + 1
    return counts
  }, {})
  const exemptionReasons = new Map(paginationRouteExemptions.map((item) => [item.path, item.reason]))

  return buildRouteInventory().routes
    .filter((route) => (
      Boolean(route.componentSourceFile)
      && tableFiles.has(route.componentSourceFile ?? '')
      && (route.routeType === 'list' || route.routeType === 'report')
    ))
    .map((route) => {
      const exemptionReason = exemptionReasons.get(route.path) ?? null
      return {
        path: route.path,
        name: route.name,
        componentSourceFile: route.componentSourceFile ?? '',
        actualCount: paginationCounts[route.componentSourceFile ?? ''] ?? 0,
        requiredCount: exemptionReason ? 0 : route.path === '/gl/tax-settings' ? 2 : 1,
        exemptionReason,
      }
    })
    .sort((left, right) => left.path.localeCompare(right.path))
}

function sortedKeys(sourceMap: RawSourceMap): string[] {
  return Object.keys(sourceMap).sort((left, right) => sourceLabel(left).localeCompare(sourceLabel(right)))
}

export function buildPageSurfaceInventory(): PageSurfaceInventory {
  const moduleVueFiles = sortedKeys(moduleVueSources)
  const sharedVueFiles = sortedKeys(sharedVueSources)
  const allVueEntries = [...moduleVueFiles.map((key) => [key, moduleVueSources[key]] as const), ...sharedVueFiles.map((key) => [key, sharedVueSources[key]] as const)]
  const viewFiles = moduleVueFiles.filter((file) => basename(file).endsWith('View.vue'))
  const viewModuleCounts = viewFiles.reduce<Record<string, number>>((counts, file) => {
    const key = moduleName(file)
    counts[key] = (counts[key] ?? 0) + 1
    return counts
  }, {})
  const sourceFiles = allVueEntries.map(([key]) => sourceLabel(key))

  const tables = allVueEntries.flatMap(([key, text]) => collectTableOccurrences(key, text))
  const paginations = allVueEntries.flatMap(([key, text]) => collectOccurrences('pagination', key, text, /<el-pagination(?:\s|>)/g))
  const paginationRequirements = collectPaginationRequirements(tables, paginations)
  const operationColumns = allVueEntries.flatMap(([key, text]) => collectOperationColumns(key, text))

  return {
    moduleVueFiles: moduleVueFiles.map(sourceLabel),
    sharedVueFiles: sharedVueFiles.map(sourceLabel),
    viewFiles: viewFiles.map(sourceLabel),
    viewModuleCounts: Object.fromEntries(Object.entries(viewModuleCounts).sort(([left], [right]) => left.localeCompare(right))),
    tables,
    tablesMissingScrollEvidence: tables.filter((table) => !table.hasInstanceScrollEvidence && !isNonScrollableTableConcluded(table)),
    tableColumns: allVueEntries.flatMap(([key, text]) => collectOccurrences('table-column', key, text, /<el-table-column(?:\s|>)/g)),
    paginations,
    paginationRequirements,
    paginationViolations: paginationRequirements.filter((item) => item.actualCount < item.requiredCount),
    paginationExemptions: paginationRequirements.filter((item) => item.exemptionReason),
    dialogs: allVueEntries.flatMap(([key, text]) => collectOccurrences('dialog', key, text, /<el-dialog(?:\s|>)/g)),
    drawers: allVueEntries.flatMap(([key, text]) => collectOccurrences('drawer', key, text, /<el-drawer(?:\s|>)/g)),
    empties: allVueEntries.flatMap(([key, text]) => collectOccurrences('empty', key, text, /<el-empty(?:\s|>)/g)),
    datePickers: allVueEntries.flatMap(([key, text]) => collectOccurrences('date-picker', key, text, /<el-date-picker(?:\s|>)/g)),
    selects: allVueEntries.flatMap(([key, text]) => collectOccurrences('select', key, text, /<el-select(?:\s|>)/g)),
    inlineQueryForms: allVueEntries.flatMap(([key, text]) => collectInlineQueryForms(key, text)),
    tableScrolls: allVueEntries.flatMap(([key, text]) => collectOccurrences('table-scroll', key, text, /\b(?:table-scroll|report-table-scroll)\b/g)),
    fixedRightColumns: allVueEntries.flatMap(([key, text]) => collectOccurrences('fixed-right-column', key, text, /fixed\s*=\s*["']right["']/g)),
    operationColumns,
    operationColumnViolations: operationColumns.flatMap(operationColumnViolations)
      .sort((left, right) => `${left.sourceFile}:${left.line}:${left.kind}`.localeCompare(`${right.sourceFile}:${right.line}:${right.kind}`)),
    tooltips: allVueEntries.flatMap(([key, text]) => collectOccurrences('tooltip', key, text, /\bshow-overflow-tooltip\b/g)),
    returnContexts: allVueEntries.flatMap(([key, text]) => collectOccurrences('return-context', key, text, /\b(?:returnTo|returnQuery|returnRoute|fromRoute|backTo|goBack)\b/g)),
    namedStatusTags: sourceFiles.filter((file) => /(Status|Severity|Stage|Source|Completeness|Category|Direction|Type)Tag\.vue$/.test(file)),
    namedDrawers: sourceFiles.filter((file) => /Drawer\.vue$/.test(file)),
    namedPanels: sourceFiles.filter((file) => /Panel\.vue$/.test(file)),
    namedTraces: sourceFiles.filter((file) => /Trace.*\.vue$|.*TracePanel\.vue$/.test(file)),
    namedEditorsOrPickers: sourceFiles.filter((file) => /(Editor|Picker)\.vue$/.test(file)),
  }
}

export function summarizePageSurfaceInventory(inventory: PageSurfaceInventory): string {
  return [
    `模块 Vue ${inventory.moduleVueFiles.length} 个`,
    `View ${inventory.viewFiles.length} 个`,
    `表格 ${inventory.tables.length} 个`,
    `分页 ${inventory.paginations.length} 个`,
    `操作列 ${inventory.operationColumns.length} 个`,
    `操作列违规 ${inventory.operationColumnViolations.length} 条`,
    `弹窗 ${inventory.dialogs.length} 个`,
    `抽屉 ${inventory.drawers.length} 个`,
  ].join('；')
}

export function formatSurfaceOccurrences(items: SurfaceOccurrence[], limit = 40): string {
  const visible = items.slice(0, limit).map((item) => `${item.sourceFile}:${item.line} [${item.kind}] ${item.text}`)
  const omitted = items.length > limit ? [`... 另有 ${items.length - limit} 条未列出`] : []
  return visible.concat(omitted).join('\n')
}

export function formatPaginationRequirements(items: PaginationRouteRequirement[], limit = 40): string {
  const visible = items.slice(0, limit).map((item) => (
    `${item.path} -> ${item.componentSourceFile}：分页 ${item.actualCount}/${item.requiredCount}${item.exemptionReason ? `；例外：${item.exemptionReason}` : ''}`
  ))
  const omitted = items.length > limit ? [`... 另有 ${items.length - limit} 条未列出`] : []
  return visible.concat(omitted).join('\n')
}

export function formatOperationColumnViolations(violations: OperationColumnViolation[], limit = 80): string {
  const summary = violations.reduce<Record<string, number>>((counts, item) => {
    counts[item.kind] = (counts[item.kind] ?? 0) + 1
    return counts
  }, {})
  const visible = violations.slice(0, limit).map((item) => (
    `${item.sourceFile}:${item.line} [${item.kind}] ${item.detail} - ${item.evidence}`
  ))
  const omitted = violations.length > limit ? [`... 另有 ${violations.length - limit} 条未列出`] : []
  return [
    `规则分布：${Object.entries(summary).map(([kind, count]) => `${kind}=${count}`).join('，')}`,
    ...visible,
    ...omitted,
  ].join('\n')
}
