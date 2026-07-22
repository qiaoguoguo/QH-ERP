type RawSourceMap = Record<string, string>

export type StatusLanguageRiskKind =
  | 'direct-status-column'
  | 'direct-template-output'
  | 'field-config-placeholder-code'
  | 'label-map-original-fallback'
  | 'return-original-fallback'
  | 'service-name-original-fallback'
  | 'template-visible-text-code'
  | 'template-raw-fallback'
  | 'user-visible-action-code-output'
  | 'user-visible-attribute-code'

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
  'field-config-placeholder-code': 0,
  'label-map-original-fallback': 0,
  'return-original-fallback': 0,
  'service-name-original-fallback': 0,
  'template-visible-text-code': 0,
  'template-raw-fallback': 0,
  'user-visible-action-code-output': 0,
  'user-visible-attribute-code': 0,
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
  'analysisMode',
  'advanceReceiptStatus',
  'basis',
  'calculationStatus',
  'category',
  'completenessStatus',
  'direction',
  'finalityStatus',
  'freshnessStatus',
  'matchStatus',
  'mode',
  'prepaymentStatus',
  'projectStatus',
  'quantityBasis',
  'reasonCode',
  'result',
  'reconciliationStatus',
  'severity',
  'sourceType',
  'sourceVariant',
  'stage',
  'status',
  'suggestionType',
  'taxpayerType',
  'type',
  'validationStatus',
])

const semanticFieldPattern = /(?:status|type|stage|mode|severity|category|result|reason|sourceType|sourceVariant|basis|direction|approval|finality|reconciliation|validation|match|freshness|completeness|quantityBasis|reasonCode|analysisMode|prepaymentStatus|advanceReceiptStatus|taxpayerType)/i
const semanticBindingAttributePattern = /\b(?:v-model(?::[\w-]+)?|:model-value|model-value|prop|property|name|key|field)\s*=\s*["'][^"']*(?:status|source[-.]?type|source[-.]?variant|stage|mode|severity|category|result|reason|basis|direction|approval|finality|reconciliation|validation|match|freshness|completeness|quantity[-.]?basis|reason[-.]?code|analysis[-.]?mode|prepayment[-.]?status|advance[-.]?receipt[-.]?status|taxpayer[-.]?type)[^"']*["']/i
const userVisibleAttributePattern = /(?:placeholder|label|title|description|empty-text|content|aria-label)\s*=\s*["']([^"']*\b[A-Z][A-Z0-9_]{2,}\b[^"']*)["']/gi
const enumLikeTokenPattern = /\b[A-Z][A-Z0-9_]{2,}\b/
const enumLikeTokenGlobalPattern = /\b[A-Z][A-Z0-9_]{2,}\b/g
const templateSemanticTokens = new Set([
  'ACTIVE',
  'ADJUSTED',
  'APPROVED',
  'ARCHIVED',
  'CANCELLED',
  'CLOSED',
  'COMPLETED',
  'CONFIRMED',
  'CURRENT',
  'DEFAULT',
  'DISABLED',
  'DRAFT',
  'EFFECTIVE',
  'ENABLED',
  'EXCLUDED',
  'EXPIRED',
  'FAILED',
  'FROZEN',
  'IN_PROGRESS',
  'LIVE',
  'LOCKED',
  'OPEN',
  'PARTIALLY_RECEIVED',
  'POSTED',
  'READY',
  'REJECTED',
  'RELEASED',
  'RESTRICTED',
  'STALE',
  'SUBMITTED',
  'UNPRICED',
])

function leafField(field: string): string {
  return field.trim().replace(/[^\w.$].*$/, '').split('.').at(-1) ?? field.trim()
}

function isRawStatusField(field: string): boolean {
  const leaf = leafField(field)
  if (!leaf || /(Label|Name|Text|Message|Reason)$/.test(leaf)) {
    return false
  }
  return exactRawStatusFields.has(leaf) || /(Status|Stage|Direction|SourceType|ReasonCode|Severity|Category|Result|Basis|Mode|Type)$/.test(leaf)
}

function isRawFallbackExpression(field: string): boolean {
  const normalized = field.trim()
  if (/(Label|Name|Text)\s*\(/.test(normalized)) {
    return false
  }
  return isRawStatusField(normalized) || /^(?:value|code|type|status|stage|sourceType|reasonCode)$/.test(leafField(normalized))
}

function hasEnumLikeUserVisibleValue(value: string): boolean {
  return enumLikeTokenPattern.test(value)
}

function hasSemanticContext(text: string): boolean {
  return semanticFieldPattern.test(text)
}

function hasSemanticBindingContext(tagText: string): boolean {
  return semanticBindingAttributePattern.test(tagText)
}

function templateRanges(sourceText: string): Array<{ start: number; text: string }> {
  const visibleText = sourceText.replace(/<(?:script|style)\b[\s\S]*?<\/(?:script|style)>/g, (match) => ' '.repeat(match.length))
  return [{ start: 0, text: visibleText }]
}

function visibleTemplateTokens(text: string): string[] {
  const tokens = Array.from(text.matchAll(enumLikeTokenGlobalPattern)).map((match) => match[0])
  return Array.from(new Set(tokens.filter((token) => templateSemanticTokens.has(token))))
}

function collectTemplateVisibleTextRisks(sourceKey: string, sourceText: string): StatusLanguageRisk[] {
  if (!sourceKey.endsWith('.vue')) {
    return []
  }
  const risks: StatusLanguageRisk[] = []
  for (const range of templateRanges(sourceText)) {
    for (const match of range.text.matchAll(/>([^<{}]+)</g)) {
      const visibleText = (match[1] ?? '').replace(/\s+/g, ' ').trim()
      if (!visibleText) {
        continue
      }
      const tokens = visibleTemplateTokens(visibleText)
      if (tokens.length === 0) {
        continue
      }
      risks.push(risk(
        'template-visible-text-code',
        sourceKey,
        sourceText,
        range.start + (match.index ?? 0) + 1,
        tokens.join(', '),
        '模板普通可见文本节点暴露英文状态、阶段、类型或动作语义',
      ))
    }
  }
  return risks
}

function collectVisibleActionCodeOutputRisks(sourceKey: string, sourceText: string): StatusLanguageRisk[] {
  if (!sourceKey.endsWith('.vue')) {
    return []
  }
  const risks: StatusLanguageRisk[] = []
  for (const range of templateRanges(sourceText)) {
    for (const match of range.text.matchAll(/{{\s*([^{}]+?)\s*}}/g)) {
      const expression = match[1] ?? ''
      if (expressionUsesDisplayHelper(expression)) {
        continue
      }
      const actionFieldMatches = Array.from(expression.matchAll(/([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*\.(?:allowedActions|availableActions))/g))
      actionFieldMatches.forEach((fieldMatch) => {
        risks.push(risk(
          'user-visible-action-code-output',
          sourceKey,
          sourceText,
          range.start + (match.index ?? 0),
          fieldMatch[1] ?? 'unknown',
          '模板用户可见输出直接插值或 join allowedActions/availableActions 原始动作码',
        ))
      })
    }
  }
  return risks
}

function collectUserVisibleAttributeRisks(sourceKey: string, sourceText: string): StatusLanguageRisk[] {
  const risks: StatusLanguageRisk[] = []
  for (const match of sourceText.matchAll(/<[^>]+>/g)) {
    const tagText = match[0]
    if (!hasSemanticBindingContext(tagText)) {
      continue
    }
    for (const attributeMatch of tagText.matchAll(userVisibleAttributePattern)) {
      const value = attributeMatch[1] ?? ''
      if (!hasEnumLikeUserVisibleValue(value)) {
        continue
      }
      risks.push(risk(
        'user-visible-attribute-code',
        sourceKey,
        sourceText,
        (match.index ?? 0) + (attributeMatch.index ?? 0),
        value,
        '用户可见属性在状态、来源、类型、阶段、口径或原因语境中暴露英文枚举值',
      ))
    }
  }
  return risks
}

function collectFieldConfigPlaceholderRisks(sourceKey: string, sourceText: string): StatusLanguageRisk[] {
  const risks: StatusLanguageRisk[] = []
  for (const lineMatch of sourceText.matchAll(/^.*$/gm)) {
    const line = lineMatch[0]
    const keyMatch = line.match(/\b(?:key|field|prop|name)\s*:\s*['"]([^'"]+)['"]/)
    const placeholderMatch = line.match(/\b(?:placeholder|label|title|description)\s*:\s*['"]([^'"]*\b[A-Z][A-Z0-9_]{2,}\b[^'"]*)['"]/)
    if (keyMatch && placeholderMatch && hasSemanticContext(keyMatch[1] ?? '')) {
      risks.push(risk(
        'field-config-placeholder-code',
        sourceKey,
        sourceText,
        (lineMatch.index ?? 0) + (placeholderMatch.index ?? 0),
        keyMatch[1] ?? 'unknown',
        'TypeScript 字段配置的用户可见文案暴露英文状态、来源、类型、阶段或口径枚举',
      ))
    }
  }
  return risks
}

function expressionUsesDisplayHelper(expression: string): boolean {
  return /\b[A-Za-z_$][\w$]*(?:Text|Label|Name|Display)\s*\(/.test(expression)
}

function collectTemplateFallbackRisks(sourceKey: string, sourceText: string): StatusLanguageRisk[] {
  const risks: StatusLanguageRisk[] = []
  for (const match of sourceText.matchAll(/{{\s*([^{}]*(?:\?\?|\|\|)[^{}]*)\s*}}/g)) {
    const expression = match[1] ?? ''
    const operatorMatch = expression.match(/\?\?|\|\|/)
    const leftExpression = operatorMatch ? expression.slice(0, operatorMatch.index) : expression
    if (expressionUsesDisplayHelper(leftExpression)) {
      continue
    }
    const rawField = Array.from(leftExpression.matchAll(/[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*/g))
      .map((fieldMatch) => fieldMatch[0])
      .find(isRawStatusField)
    if (!rawField) {
      continue
    }
    risks.push(risk(
      'template-raw-fallback',
      sourceKey,
      sourceText,
      match.index ?? 0,
      rawField,
      '模板通过 || 或 ?? 先输出原始状态、来源、类型、阶段、口径或原因字段',
    ))
  }
  return risks
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
    ...collectUserVisibleAttributeRisks(sourceKey, sourceText),
    ...collectTemplateVisibleTextRisks(sourceKey, sourceText),
    ...collectVisibleActionCodeOutputRisks(sourceKey, sourceText),
    ...collectFieldConfigPlaceholderRisks(sourceKey, sourceText),
    ...collectTemplateFallbackRisks(sourceKey, sourceText),
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

export function scanStatusLanguageSources(sources: RawSourceMap): StatusLanguageScanResult {
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

export function scanStatusLanguage(): StatusLanguageScanResult {
  return scanStatusLanguageSources({
    ...moduleVueSources,
    ...moduleTsSources,
    ...sharedVueSources,
    ...sharedTsSources,
  })
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
