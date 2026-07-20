import type { RouteRecordRaw } from 'vue-router'

export const financialCloseRouteOrder = [
  { name: 'gl-financial-close', permission: 'financial-close:period:view' },
  { name: 'gl-profit-loss-carryforward', permission: 'financial-close:profit-loss:view' },
  { name: 'gl-bank-accounts', permission: 'financial-close:bank-account:view' },
  { name: 'gl-bank-statements', permission: 'financial-close:bank-reconciliation:import' },
  { name: 'gl-bank-reconciliation', permission: 'financial-close:bank-reconciliation:view' },
  { name: 'gl-tax-settings', permission: 'financial-close:tax-profile:view' },
  { name: 'gl-tax-summary', permission: 'financial-close:tax-summary:view' },
  { name: 'gl-tax-payments', permission: 'financial-close:tax-payment:view' },
] as const

export const financialCloseRoutes: RouteRecordRaw[] = [
  {
    path: '/gl/financial-close',
    name: 'gl-financial-close',
    meta: { requiresAuth: true, requiredPermission: 'financial-close:period:view' },
    component: () => import('../../modules/financialClose/FinancialCloseWorkbenchView.vue'),
  },
  {
    path: '/gl/financial-close/:runId',
    name: 'gl-financial-close-run-detail',
    meta: { requiresAuth: true, requiredPermission: 'financial-close:period:view' },
    component: () => import('../../modules/financialClose/FinancialCloseRunDetailView.vue'),
  },
  {
    path: '/gl/profit-loss-carryforward',
    name: 'gl-profit-loss-carryforward',
    meta: { requiresAuth: true, requiredPermission: 'financial-close:profit-loss:view' },
    component: () => import('../../modules/financialClose/ProfitLossCarryforwardView.vue'),
  },
  {
    path: '/gl/bank-accounts',
    name: 'gl-bank-accounts',
    meta: { requiresAuth: true, requiredPermission: 'financial-close:bank-account:view' },
    component: () => import('../../modules/financialClose/BankAccountsView.vue'),
  },
  {
    path: '/gl/bank-statements',
    name: 'gl-bank-statements',
    meta: { requiresAuth: true, requiredPermission: 'financial-close:bank-reconciliation:import' },
    component: () => import('../../modules/financialClose/BankStatementsView.vue'),
  },
  {
    path: '/gl/bank-reconciliation',
    name: 'gl-bank-reconciliation',
    meta: { requiresAuth: true, requiredPermission: 'financial-close:bank-reconciliation:view' },
    component: () => import('../../modules/financialClose/BankReconciliationView.vue'),
  },
  {
    path: '/gl/tax-settings',
    name: 'gl-tax-settings',
    meta: { requiresAuth: true, requiredPermission: 'financial-close:tax-profile:view' },
    component: () => import('../../modules/financialClose/TaxSettingsView.vue'),
  },
  {
    path: '/gl/tax-summary',
    name: 'gl-tax-summary',
    meta: { requiresAuth: true, requiredPermission: 'financial-close:tax-summary:view' },
    component: () => import('../../modules/financialClose/TaxSummaryView.vue'),
  },
  {
    path: '/gl/tax-payments',
    name: 'gl-tax-payments',
    meta: { requiresAuth: true, requiredPermission: 'financial-close:tax-payment:view' },
    component: () => import('../../modules/financialClose/TaxPaymentsView.vue'),
  },
]

export function firstFinancialCloseRouteByPermission(hasPermission: (permission: string) => boolean) {
  const route = financialCloseRouteOrder.find((item) => hasPermission(item.permission))
  return route ? { name: route.name } : null
}
