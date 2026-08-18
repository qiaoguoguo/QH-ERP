import type { LocationQueryRaw, RouteLocationRaw } from 'vue-router'

export const returnToQueryKey = 'returnTo'
export const approvalCenterPath = '/platform/approvals'
export const approvalIdQueryKey = 'approvalId'

interface RouteLike {
  fullPath?: string
  query: Record<string, unknown>
}

export function safeReturnTo(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null
  }
  const path = value.trim()
  if (!path.startsWith('/') || path.startsWith('//') || path.startsWith('/\\')) {
    return null
  }
  return path
}

export function routeReturnTo(route: Pick<RouteLike, 'query'>): string | null {
  return safeReturnTo(route.query[returnToQueryKey])
}

export function currentRouteReturnTo(route: RouteLike): string {
  return routeReturnTo(route) ?? safeReturnTo(route.fullPath) ?? '/'
}

export function activeMenuPath(currentPath: string, returnTo: unknown): string {
  const path = safeReturnTo(returnTo)
  if (!path) {
    return currentPath
  }
  return path.split(/[?#]/)[0] || currentPath
}

export function returnLocation(route: Pick<RouteLike, 'query'>, fallback: RouteLocationRaw): RouteLocationRaw {
  return routeReturnTo(route) ?? fallback
}

export function queryWithReturnTo(
  query: LocationQueryRaw | Record<string, unknown> | null | undefined,
  returnTo: unknown,
): LocationQueryRaw {
  const next = { ...(query ?? {}) } as LocationQueryRaw
  const path = safeReturnTo(returnTo)
  if (path) {
    next[returnToQueryKey] = path
  }
  return next
}

export function approvalId(value: unknown): number | null {
  const normalized = typeof value === 'number' ? String(value) : value
  if (typeof normalized !== 'string' || !/^[1-9]\d*$/.test(normalized)) {
    return null
  }
  const id = Number(normalized)
  return Number.isSafeInteger(id) ? id : null
}

export function approvalDetailReturnTo(value: unknown): string {
  const id = approvalId(value)
  return id === null ? approvalCenterPath : `${approvalCenterPath}?${approvalIdQueryKey}=${id}`
}

export function approvalIdFromReturnTo(value: unknown): number | null {
  const path = safeReturnTo(value)
  if (!path) {
    return null
  }
  const [pathname, search = ''] = path.split('?', 2)
  if (pathname !== approvalCenterPath) {
    return null
  }
  return approvalId(new URLSearchParams(search).get(approvalIdQueryKey))
}
