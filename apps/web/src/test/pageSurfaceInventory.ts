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
  | 'bare-operation-link'
  | 'missing-fixed-right'
  | 'missing-more-dropdown'
  | 'min-width-disallowed'
  | 'operation-column-full-panel'
  | 'too-many-direct-actions'
  | 'unregistered-composite-action'
  | 'unexpected-more-direct-actions'
  | 'wrong-width'

export interface OperationColumnOccurrence extends SurfaceOccurrence {
  blockText: string
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

export interface OperationMoreDropdownDirectActionException {
  sourceFile: string
  directActionCount: number
  reason: string
}

export type ActionControlViolationKind =
  | 'missing-page-action-link'
  | 'ordinary-page-action-link'

export interface ActionControlViolation {
  kind: ActionControlViolationKind
  sourceFile: string
  line: number
  testId: string
  evidence: string
  detail: string
}

export interface PageActionLinkRequirement {
  sourceFile: string
  testId: string
  reason: string
}

export interface PageActionLinkCandidateExemption {
  sourceFile: string
  text: string
  reason: string
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
  actionControlViolations: ActionControlViolation[]
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

export const operationMoreDropdownDirectActionExceptions: OperationMoreDropdownDirectActionException[] = [
  {
    sourceFile: 'apps/web/src/modules/finance/VoucherDraftListView.vue',
    directActionCount: 1,
    reason: '详情 + 互斥长文案更多',
  },
]

export const pageActionLinkRequirements: PageActionLinkRequirement[] = [
  { sourceFile: 'apps/web/src/modules/platform/dataRepairs/DataRepairListView.vue', testId: 'create-data-repair', reason: '039 B06 数据修复新增修复申请入口' },
  { sourceFile: 'apps/web/src/modules/platform/dataRepairs/DataRepairListView.vue', testId: 'data-repair-detail-link', reason: '039 B06 数据修复列表查看详情入口' },
  { sourceFile: 'apps/web/src/modules/platform/dataRepairs/DataRepairCreateView.vue', testId: 'back-data-repair-list', reason: '039 B06 数据修复新建返回列表入口' },
  { sourceFile: 'apps/web/src/modules/master/customers/CustomerListView.vue', testId: 'customer-history-import-entry', reason: '039 B07 客户历史导入入口' },
  { sourceFile: 'apps/web/src/modules/master/suppliers/SupplierListView.vue', testId: 'supplier-history-import-entry', reason: '039 B07 供应商历史导入入口' },
  { sourceFile: 'apps/web/src/modules/materials/items/MaterialItemListView.vue', testId: 'material-history-import-entry', reason: '039 B07 物料历史导入入口' },
  { sourceFile: 'apps/web/src/modules/materials/boms/BomListView.vue', testId: 'bom-history-import-entry', reason: '039 B07 BOM 历史导入入口' },
  { sourceFile: 'apps/web/src/modules/sales/projects/SalesProjectListView.vue', testId: 'sales-project-history-import-entry', reason: '039 B07 项目历史导入入口' },
  { sourceFile: 'apps/web/src/modules/platform/historyImports/HistoryImportListView.vue', testId: 'history-import-detail-link', reason: '039 B08 历史导入批次查看详情入口' },
  { sourceFile: 'apps/web/src/modules/platform/historyImports/HistoryImportDetailView.vue', testId: 'history-import-task-link', reason: '039 B08 历史导入详情查看文档任务入口' },
  { sourceFile: 'apps/web/src/modules/platform/messages/MessageCenterView.vue', testId: 'message-business-link', reason: '039 B09 消息中心查看业务入口' },
  { sourceFile: 'apps/web/src/modules/procurement/PurchaseInquiryListView.vue', testId: 'purchase-inquiry-detail-link', reason: '039 B10 采购询价列表详情入口' },
  { sourceFile: 'apps/web/src/modules/procurement/PriceAgreementListView.vue', testId: 'price-agreement-detail-link', reason: '039 B10 价格协议列表详情入口' },
  { sourceFile: 'apps/web/src/modules/platform/approvals/ApprovalCenterView.vue', testId: 'approval-business-link', reason: '039 B11 审批列表查看业务单据入口' },
  { sourceFile: 'apps/web/src/modules/platform/approvals/ApprovalCenterView.vue', testId: 'approval-detail-business-link', reason: '039 B11 审批详情查看业务单据入口' },
]

export const pageActionLinkCandidateExemptions: PageActionLinkCandidateExemption[] = []

function sourceLabel(key: string): string {
  return `apps/web/src/${key.replace(/^\.\.\//, '')}`
}

function sourceKeyFromSourceFile(sourceFile: string): string {
  const normalized = sourceFile.replace(/\\/g, '/').replace(/^apps\/web\/src\//, '')
  return `../${normalized}`
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

function countMatches(text: string, pattern: RegExp): number {
  return Array.from(text.matchAll(pattern)).length
}

function stripCompliantActionButtonLinks(text: string): string {
  return text
    .replace(/<(?:RouterLink|router-link)\b[\s\S]*?<a\b(?=[^>]*\bclass\s*=\s*["'][^"']*\baction-button-link\b[^"']*["'])[\s\S]*?<el-button\b[\s\S]*?<\/a>[\s\S]*?<\/(?:RouterLink|router-link)>/g, '')
    .replace(/<a\b(?=[^>]*\bclass\s*=\s*["'][^"']*\baction-button-link\b[^"']*["'])[\s\S]*?<el-button\b[\s\S]*?<\/a>/g, '')
}

function countDirectActions(blockText: string): number {
  const withoutDropdowns = blockText.replace(/<el-dropdown\b[\s\S]*?<\/el-dropdown>/g, '')
  const compliantActionLinkCount = countMatches(
    withoutDropdowns,
    /<a\b(?=[^>]*\bclass\s*=\s*["'][^"']*\baction-button-link\b[^"']*["'])[\s\S]*?<el-button\b[\s\S]*?<\/a>/g,
  )
  const withoutActionButtonLinks = stripCompliantActionButtonLinks(withoutDropdowns)
  const compactCompositeActionCount = countMatches(
    withoutActionButtonLinks,
    /<FixedPrintAction\b(?=[^>]*\bvariant\s*=\s*["']compact["'])/g,
  )
  return compliantActionLinkCount
    + compactCompositeActionCount
    + countMatches(withoutActionButtonLinks, /<(?:el-button|el-link|RouterLink|router-link|a)\b/g)
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
      blockText,
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
  const operationBlock = column.blockText
  const blockWithoutCompliantLinks = stripCompliantActionButtonLinks(operationBlock)
  if (/<(?:RouterLink|router-link|a)\b/.test(blockWithoutCompliantLinks)) {
    violations.push({
      ...base,
      kind: 'bare-operation-link',
      detail: '操作列内存在未使用 action-button-link + ElButton 承载的链接式动作',
    })
  }
  if (/<(?:section|div)\b(?=[^>]*\bclass\s*=\s*["'][^"']*\b(?:platform-panel|fixed-print-action|fixed-print-panel)\b[^"']*["'])/.test(operationBlock) || /<h[1-4]\b/.test(operationBlock)) {
    violations.push({
      ...base,
      kind: 'operation-column-full-panel',
      detail: '操作列内不得嵌入标题、说明或完整面板',
    })
  }
  if (Array.from(operationBlock.matchAll(/<FixedPrintAction\b[^>]*>/g)).some((match) => !/\bvariant\s*=\s*["']compact["']/.test(match[0]))) {
    violations.push({
      ...base,
      kind: 'unregistered-composite-action',
      detail: '操作列内 FixedPrintAction 必须以 variant="compact" 登记为单个复合动作',
    })
  }
  if (column.hasMoreDropdown && column.directActionCount < 2 && !operationMoreDropdownDirectActionExceptions.some((item) => (
    item.sourceFile === column.sourceFile && item.directActionCount === column.directActionCount
  ))) {
    violations.push({
      ...base,
      kind: 'unexpected-more-direct-actions',
      detail: `含“更多”的操作列直显动作 ${column.directActionCount} 个，默认要求 2 个；唯一 1+更多例外为 VoucherDraftListView.vue`,
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

export function collectOperationColumnsFromSource(sourceFile: string, sourceText: string): OperationColumnOccurrence[] {
  return collectOperationColumns(`../${sourceFile.replace(/^apps\/web\/src\//, '')}`, sourceText)
}

export function collectOperationColumnViolations(columns: OperationColumnOccurrence[]): OperationColumnViolation[] {
  return columns.flatMap(operationColumnViolations)
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

function snippetAround(sourceText: string, index: number, radius = 520): string {
  return sourceText.slice(Math.max(0, index - radius), Math.min(sourceText.length, index + radius))
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

interface VueElementBlock {
  tagName: string
  start: number
  end: number
  openingTag: string
  blockText: string
}

function elementOpeningAt(sourceText: string, start: number): { tagName: string; openingTag: string } | null {
  const match = sourceText.slice(start).match(/^<([A-Za-z][\w.-]*)\b[^>]*>/)
  if (!match) {
    return null
  }
  return {
    tagName: match[1],
    openingTag: match[0],
  }
}

function elementBlockFromOpening(sourceText: string, start: number): VueElementBlock | null {
  const opening = elementOpeningAt(sourceText, start)
  if (!opening) {
    return null
  }
  if (/\/\s*>$/.test(opening.openingTag)) {
    return {
      ...opening,
      start,
      end: start + opening.openingTag.length,
      blockText: opening.openingTag,
    }
  }

  const tagPattern = new RegExp(`</?${escapeRegExp(opening.tagName)}\\b[^>]*>`, 'g')
  tagPattern.lastIndex = start
  let depth = 0
  for (const match of sourceText.matchAll(tagPattern)) {
    const tagText = match[0]
    const tagStart = match.index ?? 0
    if (tagStart < start) {
      continue
    }
    if (tagText.startsWith('</')) {
      depth -= 1
    } else if (!/\/\s*>$/.test(tagText)) {
      depth += 1
    }
    if (depth === 0) {
      const end = tagStart + tagText.length
      return {
        ...opening,
        start,
        end,
        blockText: sourceText.slice(start, end),
      }
    }
  }

  return {
    ...opening,
    start,
    end: start + opening.openingTag.length,
    blockText: opening.openingTag,
  }
}

function elementBlockContaining(sourceText: string, index: number): VueElementBlock | null {
  let start = sourceText.lastIndexOf('<', index)
  while (start >= 0) {
    if (!sourceText.startsWith('</', start)) {
      const block = elementBlockFromOpening(sourceText, start)
      if (block && block.start <= index && block.end >= index) {
        return block
      }
    }
    start = sourceText.lastIndexOf('<', start - 1)
  }
  return null
}

function attributeFromOpening(openingTag: string, name: string): string | null {
  return attributeValue(openingTag, name)
}

function hasButtonizedActionLink(blockText: string): boolean {
  return /\bclass\s*=\s*["'][^"']*\baction-button-link\b[^"']*["']/.test(blockText)
    && /<el-button\b(?=[^>]*\btag\s*=\s*["']span["'])/.test(blockText)
}

function visibleText(blockText: string): string {
  return blockText
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/<script\b[\s\S]*?<\/script>/gi, '')
    .replace(/<style\b[\s\S]*?<\/style>/gi, '')
    .replace(/<[^>]+>/g, ' ')
    .replace(/{{[\s\S]*?}}/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

const actionCandidateWords = ['新增', '返回', '查看', '历史导入', '下载', '上传', '打印', '生成', '提交', '处理']
const actionCandidateContextPattern = /\b(?:page-header|page-actions|detail-actions|section-title-row|header-actions|form-actions|drawer-actions|toolbar|action-bar|template\s+#actions)\b/

function isCandidateExempted(sourceFile: string, text: string): boolean {
  return pageActionLinkCandidateExemptions.some((item) =>
    item.sourceFile === sourceFile && text.includes(item.text),
  )
}

function collectLinkBlocks(sourceText: string): VueElementBlock[] {
  const seen = new Set<number>()
  return Array.from(sourceText.matchAll(/<(?:RouterLink|router-link|a)\b/g)).flatMap((match) => {
    const start = match.index ?? 0
    if (seen.has(start)) {
      return []
    }
    const block = elementBlockFromOpening(sourceText, start)
    if (!block) {
      return []
    }
    seen.add(start)
    return [block]
  })
}

function collectActionControlViolations(sourceKey: string, sourceText: string): ActionControlViolation[] {
  const sourceFile = sourceLabel(sourceKey)
  const violations: ActionControlViolation[] = []
  pageActionLinkRequirements.filter((item) => item.sourceFile === sourceFile).forEach((requirement) => {
    const escapedTestId = escapeRegExp(requirement.testId)
    const matches = Array.from(sourceText.matchAll(new RegExp(`data-test\\s*=\\s*["']${escapedTestId}["']`, 'g')))
    if (matches.length === 0) {
      violations.push({
        kind: 'missing-page-action-link',
        sourceFile,
        line: 1,
        testId: requirement.testId,
        evidence: requirement.testId,
        detail: `${requirement.reason} 缺少已登记动作入口`,
      })
      return
    }
    matches.forEach((match) => {
      const index = match.index ?? 0
      const line = lineNumberAt(sourceText, index)
      const block = elementBlockContaining(sourceText, index)
      if (!block || !hasButtonizedActionLink(block.blockText)) {
        violations.push({
          kind: 'ordinary-page-action-link',
          sourceFile,
          line,
          testId: requirement.testId,
          evidence: lineTextAt(sourceText, line),
          detail: `${requirement.reason} 必须使用 action-button-link + ElButton tag="span" 承载`,
        })
      }
    })
  })
  collectLinkBlocks(sourceText).forEach((block) => {
    if (hasButtonizedActionLink(block.blockText)) {
      return
    }
    const text = visibleText(block.blockText)
    if (!text || !actionCandidateWords.some((word) => text.includes(word))) {
      return
    }
    if (!actionCandidateContextPattern.test(snippetAround(sourceText, block.start))) {
      return
    }
    if (isCandidateExempted(sourceFile, text)) {
      return
    }
    const line = lineNumberAt(sourceText, block.start)
    violations.push({
      kind: 'ordinary-page-action-link',
      sourceFile,
      line,
      testId: attributeFromOpening(block.openingTag, 'data-test') ?? `${block.tagName}:${text}`,
      evidence: lineTextAt(sourceText, line),
      detail: `页面标题区或详情动作区的动作式链接“${text}”必须使用 action-button-link + ElButton tag="span" 承载，或登记精确排除原因`,
    })
  })
  return violations
}

export function collectActionControlViolationsFromSource(sourceFile: string, sourceText: string): ActionControlViolation[] {
  return collectActionControlViolations(sourceKeyFromSourceFile(sourceFile), sourceText)
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
  const actionControlViolations = allVueEntries.flatMap(([key, text]) => collectActionControlViolations(key, text))

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
    actionControlViolations: actionControlViolations
      .sort((left, right) => `${left.sourceFile}:${left.line}:${left.testId}`.localeCompare(`${right.sourceFile}:${right.line}:${right.testId}`)),
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

export function formatOperationColumns(columns: OperationColumnOccurrence[], limit = 80): string {
  const visible = columns.slice(0, limit).map((item) => (
    `${item.sourceFile}:${item.line} directActionCount=${item.directActionCount} hasMoreDropdown=${item.hasMoreDropdown} - ${item.text}`
  ))
  const omitted = columns.length > limit ? [`... 另有 ${columns.length - limit} 条未列出`] : []
  return visible.concat(omitted).join('\n')
}
