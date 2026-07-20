import type { MenuNode } from '../shared/api/accountPermissionApi'

export const financialCloseChildren: MenuNode[] = [
  { id: 'gl-financial-close', code: 'financial-close:period:view', name: '财务结账', routePath: '/gl/financial-close' },
  { id: 'gl-profit-loss-carryforward', code: 'financial-close:profit-loss:view', name: '期末损益结转', routePath: '/gl/profit-loss-carryforward' },
  { id: 'gl-bank-accounts', code: 'financial-close:bank-account:view', name: '银行账户', routePath: '/gl/bank-accounts' },
  { id: 'gl-bank-statements', code: 'financial-close:bank-reconciliation:view', name: '银行流水', routePath: '/gl/bank-statements' },
  { id: 'gl-bank-reconciliation', code: 'financial-close:bank-reconciliation:view', name: '银行对账', routePath: '/gl/bank-reconciliation' },
  { id: 'gl-tax-settings', code: 'financial-close:tax-profile:view', name: '税务基础设置', routePath: '/gl/tax-settings' },
  { id: 'gl-tax-summary', code: 'financial-close:tax-summary:view', name: '税额汇总', routePath: '/gl/tax-summary' },
  { id: 'gl-tax-payments', code: 'financial-close:tax-payment:view', name: '税款缴纳台账', routePath: '/gl/tax-payments' },
]

export const financialCloseMenuPaths = new Set(financialCloseChildren.map((child) => child.routePath))
