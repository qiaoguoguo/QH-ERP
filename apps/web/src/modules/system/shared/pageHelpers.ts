import type { PageResult } from '../../../shared/api/accountPermissionApi'

export function pageItems<T>(page: PageResult<T>): T[] {
  return page.items ?? page.records ?? page.content ?? []
}

export function errorMessage(error: unknown): string {
  if (error instanceof Error && error.message) {
    return error.message
  }

  return '操作失败，请稍后重试'
}

export function statusLabel(status?: string): string {
  return status === 'DISABLED' ? '停用' : '启用'
}

export function statusTagType(status?: string): 'success' | 'info' {
  return status === 'DISABLED' ? 'info' : 'success'
}
