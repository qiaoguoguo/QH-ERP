import type { MenuNode } from '../shared/api/accountPermissionApi'

export const glChildren: MenuNode[] = [
  { id: 'gl-accounting-periods', code: 'gl:period:view', name: '会计期间', routePath: '/gl/accounting-periods' },
  { id: 'gl-accounts', code: 'gl:account:view', name: '会计科目', routePath: '/gl/accounts' },
  { id: 'gl-auxiliaries', code: 'gl:auxiliary:view', name: '辅助核算', routePath: '/gl/auxiliaries' },
  { id: 'gl-posting-rules', code: 'gl:rule:view', name: '自动制证规则', routePath: '/gl/posting-rules' },
  { id: 'gl-vouchers', code: 'gl:voucher:view', name: '正式凭证', routePath: '/gl/vouchers' },
  { id: 'gl-ledger-general', code: 'gl:ledger:view', name: '总账', routePath: '/gl/ledgers/general' },
  { id: 'gl-ledger-detail', code: 'gl:ledger:view', name: '明细账', routePath: '/gl/ledgers/detail' },
  { id: 'gl-account-balances', code: 'gl:balance:view', name: '科目余额', routePath: '/gl/account-balances' },
  { id: 'gl-trial-balance', code: 'gl:balance:view', name: '试算平衡', routePath: '/gl/trial-balance' },
]

export const glMenuPaths = new Set(glChildren.map((child) => child.routePath))
