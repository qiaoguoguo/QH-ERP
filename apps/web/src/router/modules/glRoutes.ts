import { h } from 'vue'
import type { RouteRecordRaw } from 'vue-router'

export const glRouteOrder = [
  { name: 'gl-accounting-periods', permission: 'gl:period:view' },
  { name: 'gl-accounts', permission: 'gl:account:view' },
  { name: 'gl-auxiliaries', permission: 'gl:auxiliary:view' },
  { name: 'gl-posting-rules', permission: 'gl:rule:view' },
  { name: 'gl-vouchers', permission: 'gl:voucher:view' },
  { name: 'gl-ledger-general', permission: 'gl:ledger:view' },
  { name: 'gl-ledger-detail', permission: 'gl:ledger:view' },
  { name: 'gl-account-balances', permission: 'gl:balance:view' },
  { name: 'gl-trial-balance', permission: 'gl:balance:view' },
] as const

const glPlaceholder = {
  render: () => h('section', [h('h1', '会计核算'), h('p', '会计科目、正式凭证与总账基础入口。')]),
}

export const glRoutes: RouteRecordRaw[] = [
  {
    path: '/gl',
    name: 'gl-root',
    meta: { requiresAuth: true },
    component: glPlaceholder,
  },
  {
    path: '/gl/accounting-periods',
    name: 'gl-accounting-periods',
    meta: { requiresAuth: true, requiredPermission: 'gl:period:view' },
    component: () => import('../../modules/gl/GlAccountingPeriodsView.vue'),
  },
  {
    path: '/gl/accounts',
    name: 'gl-accounts',
    meta: { requiresAuth: true, requiredPermission: 'gl:account:view' },
    component: () => import('../../modules/gl/GlAccountsView.vue'),
  },
  {
    path: '/gl/auxiliaries',
    name: 'gl-auxiliaries',
    meta: { requiresAuth: true, requiredPermission: 'gl:auxiliary:view' },
    component: () => import('../../modules/gl/GlAuxiliariesView.vue'),
  },
  {
    path: '/gl/posting-rules',
    name: 'gl-posting-rules',
    meta: { requiresAuth: true, requiredPermission: 'gl:rule:view' },
    component: () => import('../../modules/gl/GlPostingRulesView.vue'),
  },
  {
    path: '/gl/vouchers',
    name: 'gl-vouchers',
    meta: { requiresAuth: true, requiredPermission: 'gl:voucher:view' },
    component: () => import('../../modules/gl/GlVoucherWorkbenchView.vue'),
  },
  {
    path: '/gl/vouchers/create',
    name: 'gl-voucher-create',
    meta: { requiresAuth: true, requiredPermission: 'gl:voucher:create' },
    component: () => import('../../modules/gl/GlVoucherFormView.vue'),
  },
  {
    path: '/gl/vouchers/:id',
    name: 'gl-voucher-detail',
    meta: { requiresAuth: true, requiredPermission: 'gl:voucher:view' },
    component: () => import('../../modules/gl/GlVoucherDetailView.vue'),
  },
  {
    path: '/gl/vouchers/:id/edit',
    name: 'gl-voucher-edit',
    meta: { requiresAuth: true, requiredPermission: 'gl:voucher:update' },
    component: () => import('../../modules/gl/GlVoucherFormView.vue'),
  },
  {
    path: '/gl/ledgers/general',
    name: 'gl-ledger-general',
    meta: { requiresAuth: true, requiredPermission: 'gl:ledger:view' },
    component: () => import('../../modules/gl/GlGeneralLedgerView.vue'),
  },
  {
    path: '/gl/ledgers/detail',
    name: 'gl-ledger-detail',
    meta: { requiresAuth: true, requiredPermission: 'gl:ledger:view' },
    component: () => import('../../modules/gl/GlDetailLedgerView.vue'),
  },
  {
    path: '/gl/account-balances',
    name: 'gl-account-balances',
    meta: { requiresAuth: true, requiredPermission: 'gl:balance:view' },
    component: () => import('../../modules/gl/GlAccountBalanceView.vue'),
  },
  {
    path: '/gl/trial-balance',
    name: 'gl-trial-balance',
    meta: { requiresAuth: true, requiredPermission: 'gl:balance:view' },
    component: () => import('../../modules/gl/GlTrialBalanceView.vue'),
  },
]

export function firstGlRouteByPermission(hasPermission: (permission: string) => boolean) {
  const route = glRouteOrder.find((item) => hasPermission(item.permission))
  return route ? { name: route.name } : null
}
