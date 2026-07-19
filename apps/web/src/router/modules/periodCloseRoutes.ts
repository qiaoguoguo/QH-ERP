import { h } from 'vue'
import type { RouteLocationRaw, RouteRecordRaw } from 'vue-router'

const placeholder = (title: string, description: string) => ({
  render: () => h('section', [h('h1', title), h('p', description)]),
})

export const periodCloseRouteOrder = [
  { name: 'period-close-runs', permission: 'system:business-period-close:view' },
] as const

export function firstPeriodCloseRouteByPermission(hasPermission: (permission: string) => boolean): RouteLocationRaw | null {
  const route = periodCloseRouteOrder.find((item) => hasPermission(item.permission))
  return route ? { name: route.name } : null
}

export const periodCloseRoutes: RouteRecordRaw[] = [
  {
    path: '/period-close',
    name: 'period-close-root',
    meta: { requiresAuth: true },
    component: placeholder('业务月结', '业务期间月结、检查、关闭、重开和期间快照入口。'),
  },
  {
    path: '/period-close/runs',
    name: 'period-close-runs',
    meta: { requiresAuth: true, requiredPermission: 'system:business-period-close:view' },
    component: () => import('../../modules/periodClose/PeriodCloseWorkbenchView.vue'),
  },
  {
    path: '/period-close/runs/:runId',
    name: 'period-close-run-detail',
    meta: { requiresAuth: true, requiredPermission: 'system:business-period-close:view' },
    component: () => import('../../modules/periodClose/PeriodCloseRunDetailView.vue'),
  },
  {
    path: '/period-close/runs/:runId/checks/:checkId',
    name: 'period-close-check-detail',
    meta: { requiresAuth: true, requiredPermission: 'system:business-period-close:view' },
    component: () => import('../../modules/periodClose/PeriodCloseCheckDetailView.vue'),
  },
  {
    path: '/period-close/runs/:runId/snapshot',
    name: 'period-close-run-snapshot',
    meta: { requiresAuth: true, requiredPermission: 'system:business-period-close:snapshot-view' },
    component: () => import('../../modules/periodClose/PeriodCloseSnapshotView.vue'),
  },
]
