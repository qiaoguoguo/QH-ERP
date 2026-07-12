import type { SalesProjectContractStatus, SalesProjectContractType, SalesProjectStatus } from '../../../shared/api/salesProjectApi'

const projectStatusLabels: Record<SalesProjectStatus, string> = {
  DRAFT: '草稿',
  ACTIVE: '执行中',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
}

const projectStatusTypes: Record<SalesProjectStatus, 'info' | 'success' | 'warning' | 'danger'> = {
  DRAFT: 'info',
  ACTIVE: 'success',
  CLOSED: 'info',
  CANCELLED: 'danger',
}

const contractStatusLabels: Record<SalesProjectContractStatus, string> = {
  DRAFT: '草稿',
  EFFECTIVE: '已生效',
  CLOSED: '已关闭',
  TERMINATED: '已终止',
  CANCELLED: '已取消',
}

const contractStatusTypes: Record<SalesProjectContractStatus, 'info' | 'success' | 'warning' | 'danger'> = {
  DRAFT: 'info',
  EFFECTIVE: 'success',
  CLOSED: 'info',
  TERMINATED: 'warning',
  CANCELLED: 'danger',
}

export function salesProjectStatusLabel(status: SalesProjectStatus): string {
  return projectStatusLabels[status] ?? status
}

export function salesProjectStatusTagType(status: SalesProjectStatus): 'info' | 'success' | 'warning' | 'danger' {
  return projectStatusTypes[status] ?? 'info'
}

export function salesProjectContractStatusLabel(status: SalesProjectContractStatus): string {
  return contractStatusLabels[status] ?? status
}

export function salesProjectContractStatusTagType(
  status: SalesProjectContractStatus,
): 'info' | 'success' | 'warning' | 'danger' {
  return contractStatusTypes[status] ?? 'info'
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
