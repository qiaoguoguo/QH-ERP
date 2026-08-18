import type { RouteLocationNormalizedLoaded, RouteLocationRaw } from 'vue-router'

const pageHelpReturnKey = 'qherp-page-help-return'

export function normalizedHelpRoutePath(route: RouteLocationNormalizedLoaded) {
  return route.matched.at(-1)?.path || route.path
}

export function createPageHelpLocation(route: RouteLocationNormalizedLoaded, keyword = ''): RouteLocationRaw {
  if (typeof window !== 'undefined') {
    window.sessionStorage.setItem(pageHelpReturnKey, route.fullPath)
  }

  return {
    name: 'help-center',
    query: {
      routePath: normalizedHelpRoutePath(route),
      keyword: keyword || String(route.name ?? ''),
      fromPage: '1',
    },
  }
}

export function currentPageHelpReturnPath() {
  if (typeof window === 'undefined') {
    return ''
  }
  return window.sessionStorage.getItem(pageHelpReturnKey) || ''
}
