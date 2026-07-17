import { h } from 'vue'
import type { RouteLocationRaw, RouteRecordRaw } from 'vue-router'

const placeholder = (title: string, description: string) => ({
  render: () => h('section', [h('h1', title), h('p', description)]),
})

export const planningRouteOrder = [
  { name: 'planning-material-requirements', permission: 'planning:material-requirement:view' },
] as const

export function firstPlanningRouteByPermission(hasPermission: (permission: string) => boolean): RouteLocationRaw | null {
  const route = planningRouteOrder.find((item) => hasPermission(item.permission))
  return route ? { name: route.name } : null
}

export const planningRoutes: RouteRecordRaw[] = [
  {
    path: '/planning',
    name: 'planning-root',
    meta: { requiresAuth: true, requiredPermission: 'planning:material-requirement:view' },
    component: placeholder('计划管理', '订单缺料分析入口。'),
  },
  {
    path: '/planning/material-requirements',
    name: 'planning-material-requirements',
    meta: { requiresAuth: true, requiredPermission: 'planning:material-requirement:view' },
    component: () => import('../../modules/planning/material-requirements/MaterialRequirementRunListView.vue'),
  },
  {
    path: '/planning/material-requirements/:id',
    name: 'planning-material-requirement-detail',
    meta: { requiresAuth: true, requiredPermission: 'planning:material-requirement:view' },
    component: () => import('../../modules/planning/material-requirements/MaterialRequirementRunDetailView.vue'),
  },
]
