type RawSourceMap = Record<string, string>

export type SurfaceKind =
  | 'date-picker'
  | 'dialog'
  | 'drawer'
  | 'empty'
  | 'fixed-right-column'
  | 'pagination'
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
  hasScrollEvidenceInFile: boolean
}

export interface PageSurfaceInventory {
  moduleVueFiles: string[]
  sharedVueFiles: string[]
  viewFiles: string[]
  viewModuleCounts: Record<string, number>
  tables: TableSurfaceOccurrence[]
  tableColumns: SurfaceOccurrence[]
  paginations: SurfaceOccurrence[]
  dialogs: SurfaceOccurrence[]
  drawers: SurfaceOccurrence[]
  empties: SurfaceOccurrence[]
  datePickers: SurfaceOccurrence[]
  selects: SurfaceOccurrence[]
  tableScrolls: SurfaceOccurrence[]
  fixedRightColumns: SurfaceOccurrence[]
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
      .filter((line) => line <= item.line)
      .sort((left, right) => right - left)[0] ?? null
    return {
      ...item,
      scrollEvidenceLine: nearestScrollBefore,
      hasScrollEvidenceInFile: scrollLines.length > 0,
    }
  })
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

  return {
    moduleVueFiles: moduleVueFiles.map(sourceLabel),
    sharedVueFiles: sharedVueFiles.map(sourceLabel),
    viewFiles: viewFiles.map(sourceLabel),
    viewModuleCounts: Object.fromEntries(Object.entries(viewModuleCounts).sort(([left], [right]) => left.localeCompare(right))),
    tables: allVueEntries.flatMap(([key, text]) => collectTableOccurrences(key, text)),
    tableColumns: allVueEntries.flatMap(([key, text]) => collectOccurrences('table-column', key, text, /<el-table-column(?:\s|>)/g)),
    paginations: allVueEntries.flatMap(([key, text]) => collectOccurrences('pagination', key, text, /<el-pagination(?:\s|>)/g)),
    dialogs: allVueEntries.flatMap(([key, text]) => collectOccurrences('dialog', key, text, /<el-dialog(?:\s|>)/g)),
    drawers: allVueEntries.flatMap(([key, text]) => collectOccurrences('drawer', key, text, /<el-drawer(?:\s|>)/g)),
    empties: allVueEntries.flatMap(([key, text]) => collectOccurrences('empty', key, text, /<el-empty(?:\s|>)/g)),
    datePickers: allVueEntries.flatMap(([key, text]) => collectOccurrences('date-picker', key, text, /<el-date-picker(?:\s|>)/g)),
    selects: allVueEntries.flatMap(([key, text]) => collectOccurrences('select', key, text, /<el-select(?:\s|>)/g)),
    tableScrolls: allVueEntries.flatMap(([key, text]) => collectOccurrences('table-scroll', key, text, /\b(?:table-scroll|report-table-scroll)\b/g)),
    fixedRightColumns: allVueEntries.flatMap(([key, text]) => collectOccurrences('fixed-right-column', key, text, /fixed\s*=\s*["']right["']/g)),
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
    `弹窗 ${inventory.dialogs.length} 个`,
    `抽屉 ${inventory.drawers.length} 个`,
  ].join('；')
}
