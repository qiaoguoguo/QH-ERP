type RawSourceMap = Record<string, string>

export type StatusLanguageRiskKind =
  | 'direct-status-column'
  | 'direct-template-output'
  | 'label-map-original-fallback'
  | 'return-original-fallback'
  | 'service-name-original-fallback'

export interface StatusLanguageRisk {
  kind: StatusLanguageRiskKind
  sourceFile: string
  line: number
  field: string
  context: string
  evidence: string
  classification: 'unclassified-user-visible-risk'
}

export interface StatusLanguageWhitelistEntry {
  value: string
  context: string
  file: string
  rule: string
  reason: string
  userVisible: boolean
}

export interface StatusLanguageScanResult {
  scannedFiles: string[]
  risks: StatusLanguageRisk[]
  summary: Record<StatusLanguageRiskKind, number>
  whitelist: StatusLanguageWhitelistEntry[]
  whitelistErrors: string[]
}

const moduleVueSources = import.meta.glob<string>('../modules/**/*.vue', {
  eager: true,
  import: 'default',
  query: '?raw',
}) as RawSourceMap

const moduleTsSources = import.meta.glob<string>('../modules/**/*.ts', {
  eager: true,
  import: 'default',
  query: '?raw',
}) as RawSourceMap

const sharedVueSources = import.meta.glob<string>('../shared/**/*.vue', {
  eager: true,
  import: 'default',
  query: '?raw',
}) as RawSourceMap

const sharedTsSources = import.meta.glob<string>('../shared/**/*.ts', {
  eager: true,
  import: 'default',
  query: '?raw',
}) as RawSourceMap

export const statusLanguageWhitelist: StatusLanguageWhitelistEntry[] = []

const emptySummary: Record<StatusLanguageRiskKind, number> = {
  'direct-status-column': 0,
  'direct-template-output': 0,
  'label-map-original-fallback': 0,
  'return-original-fallback': 0,
  'service-name-original-fallback': 0,
}

function sourceLabel(key: string): string {
  return `apps/web/src/${key.replace(/^\.\.\//, '')}`
}

function lineNumberAt(text: string, index: number): number {
  return text.slice(0, index).split(/\r?\n/).length
}

function lineTextAt(text: string, line: number): string {
  return text.split(/\r?\n/)[line - 1]?.trim() ?? ''
}

function risk(
  kind: StatusLanguageRiskKind,
  sourceKey: string,
  sourceText: string,
  index: number,
  field: string,
  context: string,
): StatusLanguageRisk {
  const line = lineNumberAt(sourceText, index)
  return {
    kind,
    sourceFile: sourceLabel(sourceKey),
    line,
    field,
    context,
    evidence: lineTextAt(sourceText, line),
    classification: 'unclassified-user-visible-risk',
  }
}

function dedupeRisks(risks: StatusLanguageRisk[]): StatusLanguageRisk[] {
  const seen = new Set<string>()
  return risks.filter((item) => {
    const key = `${item.kind}|${item.sourceFile}|${item.line}|${item.field}|${item.context}`
    if (seen.has(key)) {
      return false
    }
    seen.add(key)
    return true
  })
}

function collectLinePatternRisks(
  sourceKey: string,
  sourceText: string,
  kind: StatusLanguageRiskKind,
  pattern: RegExp,
  fieldGroup: number,
  context: string,
): StatusLanguageRisk[] {
  return Array.from(sourceText.matchAll(pattern)).map((match) => risk(
    kind,
    sourceKey,
    sourceText,
    match.index ?? 0,
    match[fieldGroup]?.trim() ?? 'unknown',
    context,
  ))
}

const exactRawStatusFields = new Set([
  'action',
  'approvalStatus',
  'calculationStatus',
  'completenessStatus',
  'direction',
  'finalityStatus',
  'freshnessStatus',
  'matchStatus',
  'mode',
  'projectStatus',
  'quantityBasis',
  'reasonCode',
  'reconciliationStatus',
  'sourceType',
  'stage',
  'status',
  'suggestionType',
  'type',
  'validationStatus',
])

function leafField(field: string): string {
  return field.trim().replace(/[^\w.$].*$/, '').split('.').at(-1) ?? field.trim()
}

function isRawStatusField(field: string): boolean {
  const leaf = leafField(field)
  if (!leaf || /(Label|Name|Text|Message|Reason)$/.test(leaf)) {
    return false
  }
  return exactRawStatusFields.has(leaf) || /(Status|Stage|Direction|SourceType|ReasonCode)$/.test(leaf)
}

function isRawFallbackExpression(field: string): boolean {
  const normalized = field.trim()
  if (/(Label|Name|Text)\s*\(/.test(normalized)) {
    return false
  }
  return isRawStatusField(normalized) || /^(?:value|code|type|status|stage|sourceType|reasonCode)$/.test(leafField(normalized))
}

function collectStatusRisks(sourceKey: string, sourceText: string): StatusLanguageRisk[] {
  const directStatusColumns = collectLinePatternRisks(
    sourceKey,
    sourceText,
    'direct-status-column',
    /<el-table-column\b[^>]*(?:prop|property)\s*=\s*["']([^"']+)["'][^>]*>/g,
    1,
    '表格列直接绑定状态、类型、阶段、方向或原因字段，用户可见时需要中文显示函数',
  ).filter((item) => isRawStatusField(item.field))

  const directTemplateOutputs = collectLinePatternRisks(
    sourceKey,
    sourceText,
    'direct-template-output',
    /{{\s*([A-Za-z_$][\w.$]*(?:status|Status|sourceType|SourceType|direction|Direction|stage|Stage|type|Type|quantityBasis|reasonCode)[\w.$]*)\s*}}/g,
    1,
    '模板插值直接输出状态类字段',
  ).filter((item) => isRawStatusField(item.field))

  const serviceNameFallbacks = collectLinePatternRisks(
    sourceKey,
    sourceText,
    'service-name-original-fallback',
    /\b([A-Za-z_$][\w.$]*(?:statusName|typeName|sourceTypeName|qualityStatusName|trackingMethodName|reasonMessage|disabledReason|actionDisabledReason|summary))\b\s*(?:\|\||\?\?)\s*([A-Za-z_$][\w.$]*(?:status|type|stage|direction|reasonCode|code|quantityBasis)[\w.$]*)/gi,
    2,
    '服务端显示名为空时回退原始状态或类型编码',
  ).filter((item) => isRawFallbackExpression(item.field))

  const labelMapFallbacks = collectLinePatternRisks(
    sourceKey,
    sourceText,
    'label-map-original-fallback',
    /\b[A-Za-z_$][\w$]*(?:Labels|labels|Names|names|Texts|texts|Map|map)\s*\[[^\]]+\]\s*\?\?\s*([^,;:)}\]\n]+)/g,
    1,
    '标签字典未命中时回退原始值',
  ).filter((item) => isRawFallbackExpression(item.field) || /String\(/.test(item.field))

  const returnFallbacks = collectLinePatternRisks(
    sourceKey,
    sourceText,
    'return-original-fallback',
    /return\s+[^;\n]*(?:\?\?|\|\|)\s*([A-Za-z_$][\w.$]*(?:status|type|stage|direction|reasonCode|code|sourceType|quantityBasis|value)[\w.$]*)(?:\s|$|[),;])/gi,
    1,
    '格式化函数返回原始状态或类型编码',
  ).filter((item) => leafField(item.field) !== 'value' && !/[=!]==?/.test(item.evidence) && isRawFallbackExpression(item.field))

  return dedupeRisks([
    ...directStatusColumns,
    ...directTemplateOutputs,
    ...serviceNameFallbacks,
    ...labelMapFallbacks,
    ...returnFallbacks,
  ])
}

export function validateStatusLanguageWhitelist(entries: StatusLanguageWhitelistEntry[]): string[] {
  return entries.flatMap((entry, index) => {
    const prefix = `白名单第 ${index + 1} 项`
    const errors: string[] = []
    if (!entry.value.trim()) {
      errors.push(`${prefix} 缺少精确值`)
    }
    if (!entry.context.trim()) {
      errors.push(`${prefix} 缺少语境`)
    }
    if (!entry.file.trim() || /[*?]|\*\*/.test(entry.file)) {
      errors.push(`${prefix} 文件必须精确到单个文件，禁止通配`)
    }
    if (!entry.rule.trim() || /all|全部|所有|通配|目录/i.test(entry.rule)) {
      errors.push(`${prefix} 规则不能是目录级或通配级豁免`)
    }
    if (!entry.reason.trim()) {
      errors.push(`${prefix} 缺少允许原因`)
    }
    return errors
  })
}

export function scanStatusLanguage(): StatusLanguageScanResult {
  const sources = {
    ...moduleVueSources,
    ...moduleTsSources,
    ...sharedVueSources,
    ...sharedTsSources,
  }
  const entries = Object.entries(sources)
    .filter(([key]) => !key.endsWith('.spec.ts') && !key.endsWith('.d.ts'))
    .sort(([left], [right]) => sourceLabel(left).localeCompare(sourceLabel(right)))
  const risks = entries.flatMap(([key, text]) => collectStatusRisks(key, text))
    .sort((left, right) => `${left.sourceFile}:${left.line}:${left.kind}`.localeCompare(`${right.sourceFile}:${right.line}:${right.kind}`))
  const summary = risks.reduce<Record<StatusLanguageRiskKind, number>>((counts, item) => {
    counts[item.kind] += 1
    return counts
  }, { ...emptySummary })

  return {
    scannedFiles: entries.map(([key]) => sourceLabel(key)),
    risks,
    summary,
    whitelist: statusLanguageWhitelist,
    whitelistErrors: validateStatusLanguageWhitelist(statusLanguageWhitelist),
  }
}

export function formatStatusRiskList(risks: StatusLanguageRisk[], limit = 40): string {
  const summary = risks.reduce<Record<string, number>>((counts, item) => {
    counts[item.kind] = (counts[item.kind] ?? 0) + 1
    return counts
  }, {})
  const visible = risks.slice(0, limit).map((item) => (
    `${item.sourceFile}:${item.line} [${item.kind}] ${item.field} - ${item.evidence}`
  ))
  const omitted = risks.length > limit ? [`... 另有 ${risks.length - limit} 条未列出`] : []
  return [
    `剩余未分类用户可见状态风险 ${risks.length} 条。`,
    `规则分布：${Object.entries(summary).map(([kind, count]) => `${kind}=${count}`).join('，')}`,
    ...visible,
    ...omitted,
  ].join('\n')
}
