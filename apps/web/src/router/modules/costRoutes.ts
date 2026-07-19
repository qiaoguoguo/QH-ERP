import { h } from 'vue'
import type { RouteLocationRaw, RouteRecordRaw } from 'vue-router'

const placeholder = (title: string, description: string) => ({
  render: () => h('section', [h('h1', title), h('p', description)]),
})

export const costRouteOrder = [
  { name: 'cost-project-costs', permission: 'cost:project-cost:view' },
  { name: 'cost-project-cost-adjustments', permission: 'cost:project-cost-adjustment:view' },
  { name: 'cost-project-cost-variances', permission: 'cost:project-cost-variance:view' },
  { name: 'cost-records', permission: 'cost:record:view' },
] as const

export function firstCostRouteByPermission(hasPermission: (permission: string) => boolean): RouteLocationRaw | null {
  const route = costRouteOrder.find((item) => hasPermission(item.permission))
  return route ? { name: route.name } : null
}

export const costRoutes: RouteRecordRaw[] = [
  {
    path: '/cost',
    name: 'cost-root',
    meta: { requiresAuth: true },
    component: placeholder('成本管理', '项目成本核算、调整分配、差异和成本业务记录入口。'),
  },
  {
    path: '/cost/project-costs',
    name: 'cost-project-costs',
    meta: { requiresAuth: true, requiredPermission: 'cost:project-cost:view' },
    component: () => import('../../modules/cost/project/ProjectCostWorkbenchView.vue'),
  },
  {
    path: '/cost/project-costs/:projectId',
    name: 'cost-project-cost-detail',
    meta: { requiresAuth: true, requiredPermission: 'cost:project-cost:view' },
    component: () => import('../../modules/cost/project/ProjectCostProjectDetailView.vue'),
  },
  {
    path: '/cost/project-cost-calculations/:id',
    name: 'cost-project-cost-calculation-detail',
    meta: { requiresAuth: true, requiredPermission: 'cost:project-cost:view' },
    component: () => import('../../modules/cost/project/ProjectCostCalculationDetailView.vue'),
  },
  {
    path: '/cost/project-cost-adjustments',
    name: 'cost-project-cost-adjustments',
    meta: { requiresAuth: true, requiredPermission: 'cost:project-cost-adjustment:view' },
    component: () => import('../../modules/cost/project/ProjectCostAdjustmentListView.vue'),
  },
  {
    path: '/cost/project-cost-adjustments/create',
    name: 'cost-project-cost-adjustment-create',
    meta: { requiresAuth: true, requiredPermission: 'cost:project-cost-adjustment:create' },
    component: () => import('../../modules/cost/project/ProjectCostAdjustmentFormView.vue'),
  },
  {
    path: '/cost/project-cost-adjustments/:id',
    name: 'cost-project-cost-adjustment-detail',
    meta: { requiresAuth: true, requiredPermission: 'cost:project-cost-adjustment:view' },
    component: () => import('../../modules/cost/project/ProjectCostAdjustmentDetailView.vue'),
  },
  {
    path: '/cost/project-cost-adjustments/:id/edit',
    name: 'cost-project-cost-adjustment-edit',
    meta: { requiresAuth: true, requiredPermission: 'cost:project-cost-adjustment:update' },
    component: () => import('../../modules/cost/project/ProjectCostAdjustmentFormView.vue'),
  },
  {
    path: '/cost/project-cost-variances',
    name: 'cost-project-cost-variances',
    meta: { requiresAuth: true, requiredPermission: 'cost:project-cost-variance:view' },
    component: () => import('../../modules/cost/project/ProjectCostVarianceListView.vue'),
  },
  {
    path: '/cost/records',
    name: 'cost-records',
    meta: { requiresAuth: true, requiredPermission: 'cost:record:view' },
    component: () => import('../../modules/cost/CostRecordListView.vue'),
  },
  {
    path: '/cost/records/create',
    name: 'cost-record-create',
    meta: { requiresAuth: true, requiredPermission: 'cost:record:create' },
    component: () => import('../../modules/cost/CostRecordFormView.vue'),
  },
  {
    path: '/cost/records/:id',
    name: 'cost-record-detail',
    meta: { requiresAuth: true, requiredPermission: 'cost:record:view' },
    component: () => import('../../modules/cost/CostRecordDetailView.vue'),
  },
  {
    path: '/cost/records/:id/edit',
    name: 'cost-record-edit',
    meta: { requiresAuth: true, requiredPermission: 'cost:record:update' },
    component: () => import('../../modules/cost/CostRecordFormView.vue'),
  },
]
