import type { PageResult } from '../../../shared/api/accountPermissionApi'

export function pageItems<T>(page: PageResult<T>): T[] {
  return page.items ?? page.records ?? page.content ?? []
}

export function pageTotal<T>(page: PageResult<T>): number {
  const normalizedPage = page as PageResult<T> & { totalElements?: number }
  return normalizedPage.total ?? normalizedPage.totalElements ?? pageItems(page).length
}

export function errorMessage(error: unknown): string {
  if (error instanceof Error && error.message) {
    return error.message
  }

  return '操作失败，请稍后重试'
}

export function statusLabel(status?: string | null): string {
  if (status === 'ENABLED') {
    return '启用'
  }
  if (status === 'DISABLED') {
    return '停用'
  }
  return '未知状态'
}

export function statusTagType(status?: string | null): 'success' | 'info' | 'warning' {
  if (status === 'ENABLED') {
    return 'success'
  }
  if (status === 'DISABLED') {
    return 'info'
  }
  return 'warning'
}
