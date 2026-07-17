import { h } from 'vue'
import type { RouteLocationRaw, RouteRecordRaw } from 'vue-router'

const placeholder = (title: string, description: string) => ({
  render: () => h('section', [h('h1', title), h('p', description)]),
})

export const productionRouteOrder = [
  { name: 'production-work-orders', permission: 'production:work-order:view' },
  { name: 'production-material-returns', permission: 'production:material-return:view' },
  { name: 'production-material-supplements', permission: 'production:material-supplement:view' },
  { name: 'production-outsourcing-orders', permission: 'production:outsourcing:view' },
] as const

export function firstProductionRouteByPermission(hasPermission: (permission: string) => boolean): RouteLocationRaw | null {
  const route = productionRouteOrder.find((item) => hasPermission(item.permission))
  return route ? { name: route.name } : null
}

export const productionRoutes: RouteRecordRaw[] = [
  {
    path: '/production',
    name: 'production-root',
    meta: { requiresAuth: true },
    component: placeholder('生产管理', '生产工单、生产执行和外协执行入口。'),
  },
  {
    path: '/production/work-orders',
    name: 'production-work-orders',
    meta: { requiresAuth: true, requiredPermission: 'production:work-order:view' },
    component: () => import('../../modules/production/ProductionWorkOrderListView.vue'),
  },
  {
    path: '/production/work-orders/create',
    name: 'production-work-order-create',
    meta: { requiresAuth: true, requiredPermission: 'production:work-order:create' },
    component: () => import('../../modules/production/ProductionWorkOrderFormView.vue'),
  },
  {
    path: '/production/work-orders/:id',
    name: 'production-work-order-detail',
    meta: { requiresAuth: true, requiredPermission: 'production:work-order:view' },
    component: () => import('../../modules/production/ProductionWorkOrderDetailView.vue'),
  },
  {
    path: '/production/work-orders/:id/edit',
    name: 'production-work-order-edit',
    meta: { requiresAuth: true, requiredPermission: 'production:work-order:update' },
    component: () => import('../../modules/production/ProductionWorkOrderFormView.vue'),
  },
  {
    path: '/production/work-orders/:id/material-issues',
    name: 'production-work-order-material-issues',
    meta: { requiresAuth: true, requiredPermission: 'production:issue:view' },
    component: () => import('../../modules/production/ProductionMaterialIssueView.vue'),
  },
  {
    path: '/production/work-orders/:id/reports',
    name: 'production-work-order-reports',
    meta: { requiresAuth: true, requiredPermission: 'production:report:view' },
    component: () => import('../../modules/production/ProductionWorkReportView.vue'),
  },
  {
    path: '/production/work-orders/:id/completion-receipts',
    name: 'production-work-order-completion-receipts',
    meta: { requiresAuth: true, requiredPermission: 'production:receipt:view' },
    component: () => import('../../modules/production/ProductionCompletionReceiptView.vue'),
  },
  {
    path: '/production/material-returns',
    name: 'production-material-returns',
    meta: { requiresAuth: true, requiredPermission: 'production:material-return:view' },
    component: () => import('../../modules/reversal/ProductionMaterialReturnListView.vue'),
  },
  {
    path: '/production/material-returns/create',
    name: 'production-material-return-create',
    meta: { requiresAuth: true, requiredPermission: 'production:material-return:create' },
    component: () => import('../../modules/reversal/ProductionMaterialReturnFormView.vue'),
  },
  {
    path: '/production/material-returns/:id',
    name: 'production-material-return-detail',
    meta: { requiresAuth: true, requiredPermission: 'production:material-return:view' },
    component: () => import('../../modules/reversal/ProductionMaterialReturnDetailView.vue'),
  },
  {
    path: '/production/material-returns/:id/edit',
    name: 'production-material-return-edit',
    meta: { requiresAuth: true, requiredPermission: 'production:material-return:update' },
    component: () => import('../../modules/reversal/ProductionMaterialReturnFormView.vue'),
  },
  {
    path: '/production/material-supplements',
    name: 'production-material-supplements',
    meta: { requiresAuth: true, requiredPermission: 'production:material-supplement:view' },
    component: () => import('../../modules/reversal/ProductionMaterialSupplementListView.vue'),
  },
  {
    path: '/production/material-supplements/create',
    name: 'production-material-supplement-create',
    meta: { requiresAuth: true, requiredPermission: 'production:material-supplement:create' },
    component: () => import('../../modules/reversal/ProductionMaterialSupplementFormView.vue'),
  },
  {
    path: '/production/material-supplements/:id',
    name: 'production-material-supplement-detail',
    meta: { requiresAuth: true, requiredPermission: 'production:material-supplement:view' },
    component: () => import('../../modules/reversal/ProductionMaterialSupplementDetailView.vue'),
  },
  {
    path: '/production/material-supplements/:id/edit',
    name: 'production-material-supplement-edit',
    meta: { requiresAuth: true, requiredPermission: 'production:material-supplement:update' },
    component: () => import('../../modules/reversal/ProductionMaterialSupplementFormView.vue'),
  },
  {
    path: '/production/outsourcing-orders',
    name: 'production-outsourcing-orders',
    meta: { requiresAuth: true, requiredPermission: 'production:outsourcing:view' },
    component: () => import('../../modules/production/outsourcing/ProductionOutsourcingOrderListView.vue'),
  },
  {
    path: '/production/outsourcing-orders/create',
    name: 'production-outsourcing-order-create',
    meta: { requiresAuth: true, requiredPermission: 'production:outsourcing:create' },
    component: () => import('../../modules/production/outsourcing/ProductionOutsourcingOrderFormView.vue'),
  },
  {
    path: '/production/outsourcing-orders/:id',
    name: 'production-outsourcing-order-detail',
    meta: { requiresAuth: true, requiredPermission: 'production:outsourcing:view' },
    component: () => import('../../modules/production/outsourcing/ProductionOutsourcingOrderDetailView.vue'),
  },
  {
    path: '/production/outsourcing-orders/:id/edit',
    name: 'production-outsourcing-order-edit',
    meta: { requiresAuth: true, requiredPermission: 'production:outsourcing:update' },
    component: () => import('../../modules/production/outsourcing/ProductionOutsourcingOrderFormView.vue'),
  },
  {
    path: '/production/outsourcing-orders/:id/material-issues',
    name: 'production-outsourcing-order-material-issues',
    meta: { requiresAuth: true, requiredPermission: 'production:outsourcing:view' },
    component: () => import('../../modules/production/outsourcing/ProductionOutsourcingIssueView.vue'),
  },
  {
    path: '/production/outsourcing-orders/:id/receipts',
    name: 'production-outsourcing-order-receipts',
    meta: { requiresAuth: true, requiredPermission: 'production:outsourcing:view' },
    component: () => import('../../modules/production/outsourcing/ProductionOutsourcingReceiptView.vue'),
  },
]
