import type { SalesProjectContractStatus, SalesProjectContractType, SalesProjectStatus } from '../../../shared/api/salesProjectApi'
import { createUnknownStatusDisplay, type StatusTone } from '../../../shared/status/statusDisplay'

const projectStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  ACTIVE: '执行中',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
}

const projectStatusTypes: Record<string, StatusTone> = {
  DRAFT: 'info',
  ACTIVE: 'success',
  CLOSED: 'info',
  CANCELLED: 'info',
}

const contractStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  EFFECTIVE: '已生效',
  CLOSED: '已关闭',
  TERMINATED: '已终止',
  CANCELLED: '已取消',
}

const contractStatusTypes: Record<string, StatusTone> = {
  DRAFT: 'info',
  EFFECTIVE: 'success',
  CLOSED: 'info',
  TERMINATED: 'warning',
  CANCELLED: 'info',
}

function normalizedStatusCode(value: unknown): string {
  return String(value ?? '').trim().toUpperCase()
}

function knownStatusLabel(status: unknown, labels: Record<string, string>, field: string): string {
  const code = normalizedStatusCode(status)
  if (!code) {
    return '-'
  }
  return labels[code] ?? createUnknownStatusDisplay({
    domain: '销售项目',
    field,
    code,
  }).label
}

export function salesProjectStatusLabel(status: SalesProjectStatus | string | null | undefined): string {
  return knownStatusLabel(status, projectStatusLabels, '项目状态')
}

export function salesProjectStatusTagType(status: SalesProjectStatus | string | null | undefined): StatusTone {
  return projectStatusTypes[normalizedStatusCode(status)] ?? 'warning'
}

export function salesProjectContractStatusLabel(status: SalesProjectContractStatus | string | null | undefined): string {
  return knownStatusLabel(status, contractStatusLabels, '项目合同状态')
}

export function salesProjectContractStatusTagType(
  status: SalesProjectContractStatus | string | null | undefined,
): StatusTone {
  return contractStatusTypes[normalizedStatusCode(status)] ?? 'warning'
}

export function salesProjectContractTypeLabel(type: SalesProjectContractType): string {
  return type === 'MAIN' ? '主合同' : '补充合同'
}

export function formatProjectAmount(value: unknown): string {
  if (value === null || value === undefined) {
    return '-'
  }
  const rawValue = String(value).trim()
  const match = rawValue.match(/^([+-]?)(\d+)(?:\.(\d+))?$/)
  if (!match) {
    return '-'
  }
  const sign = match[1] === '-' ? '-' : ''
  let integerPart = match[2].replace(/^0+(?=\d)/, '') || '0'
  const fraction = match[3] ?? ''
  const firstTwoDigits = fraction.slice(0, 2).padEnd(2, '0')
  const roundDigit = Number(fraction.charAt(2) || '0')
  let cents = Number(firstTwoDigits)
  if (roundDigit >= 5) {
    cents += 1
  }
  if (cents >= 100) {
    integerPart = incrementIntegerString(integerPart)
    cents = 0
  }
  const formatted = `${integerPart}.${String(cents).padStart(2, '0')}`
  return formatted === '0.00' ? formatted : `${sign}${formatted}`
}

function incrementIntegerString(value: string): string {
  const digits = value.split('')
  for (let index = digits.length - 1; index >= 0; index -= 1) {
    if (digits[index] !== '9') {
      digits[index] = String(Number(digits[index]) + 1)
      return digits.join('')
    }
    digits[index] = '0'
  }
  return `1${digits.join('')}`
}

export function formatProjectDateTime(value?: string | null): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 16)
}

export function projectApiErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) {
    return error.message
  }
  return '操作失败，请稍后重试'
}

export function normalizeProjectOptionalId(value: string | number | ''): string | number | undefined {
  if (value === '' || value === null || value === undefined) {
    return undefined
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : undefined
  }
  const trimmedValue = String(value).trim()
  if (!trimmedValue) {
    return undefined
  }
  const numericValue = Number(trimmedValue)
  return Number.isFinite(numericValue) ? numericValue : trimmedValue
}

export function validateProjectReason(value: string): string {
  const trimmedValue = value.trim()
  if (!trimmedValue) {
    return '请填写 1-200 字原因'
  }
  if (trimmedValue.length > 200) {
    return '原因最多 200 字'
  }
  return ''
}
