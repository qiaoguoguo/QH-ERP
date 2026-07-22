export type StatusTone = 'success' | 'warning' | 'danger' | 'info'

export interface StatusDisplayContext {
  domain: string
  field: string
}

export interface UnknownStatusDisplayInput extends StatusDisplayContext {
  code: unknown
  statusName?: unknown
  tone?: StatusTone
  includeOriginalCode?: boolean
}

export interface StatusDisplay {
  label: string
  tone: StatusTone
  context: StatusDisplayContext
  originalCode?: string
  diagnostic?: string
}

const unknownStatusLabel = '未知状态'
const chineseTextPattern = /[\u4e00-\u9fff]/

function normalizeDisplayText(value: unknown): string {
  if (typeof value === 'string') {
    return value.trim()
  }
  if (value === null || value === undefined) {
    return ''
  }
  return String(value).trim()
}

function isChineseDisplayText(value: string): boolean {
  return chineseTextPattern.test(value)
}

export function createUnknownStatusDisplay(input: UnknownStatusDisplayInput): StatusDisplay {
  const code = normalizeDisplayText(input.code)
  const serverName = normalizeDisplayText(input.statusName)
  const canUseServerName = serverName.length > 0 && serverName !== code && isChineseDisplayText(serverName)
  const display: StatusDisplay = {
    label: canUseServerName ? serverName : unknownStatusLabel,
    tone: input.tone ?? 'warning',
    context: {
      domain: input.domain,
      field: input.field,
    },
  }

  if (input.includeOriginalCode && code.length > 0) {
    display.originalCode = code
    display.diagnostic = `原编码：${code}`
  }

  return display
}
