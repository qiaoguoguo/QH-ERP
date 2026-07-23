import { describe, expect, it } from 'vitest'
import { buildRouteInventory, summarizeRouteInventory } from './pageRouteInventory'

describe('页面治理路由清单门禁', () => {
  it('生成 231 个生产唯一路由并解释排除 spec 后的口径', () => {
    const inventory = buildRouteInventory()

    expect(inventory.routes, summarizeRouteInventory(inventory)).toHaveLength(231)
    expect(inventory.duplicatePaths).toEqual([])
    expect(inventory.duplicateAliases).toEqual([])
    expect(inventory.topLevelCounts).toEqual({
      '/': 1,
      '/accounts': 4,
      '/cost': 13,
      '/finance': 46,
      '/forbidden': 1,
      '/gl': 22,
      '/inventory': 23,
      '/login': 1,
      '/master': 6,
      '/materials': 4,
      '/period-close': 5,
      '/planning': 3,
      '/platform': 9,
      '/procurement': 27,
      '/production': 22,
      '/quality': 2,
      '/reports': 17,
      '/sales': 24,
      '/system': 1,
    })
  })

  it('解释 system 兼容别名但不把别名计入 231 个生产主路由', () => {
    const inventory = buildRouteInventory()

    expect(inventory.aliasRoutes).toHaveLength(3)
    expect(inventory.systemCompatibilityAliases.map((route) => ({
      aliasPath: route.aliasPath,
      canonicalPath: route.canonicalPath,
      canonicalName: route.canonicalName,
      componentSourceFile: route.componentSourceFile,
      requiredPermissions: route.requiredPermissions,
    }))).toEqual([
      {
        aliasPath: '/system/roles',
        canonicalPath: '/accounts/roles',
        canonicalName: 'system-roles',
        componentSourceFile: 'apps/web/src/modules/system/roles/RoleListView.vue',
        requiredPermissions: ['system:role:view'],
      },
      {
        aliasPath: '/system/roles/:id/permissions',
        canonicalPath: '/accounts/roles/:id/permissions',
        canonicalName: 'system-role-permissions',
        componentSourceFile: 'apps/web/src/modules/system/roles/RolePermissionView.vue',
        requiredPermissions: ['system:role:view', 'system:permission:view', 'system:role:assign-permission'],
      },
      {
        aliasPath: '/system/users',
        canonicalPath: '/accounts/users',
        canonicalName: 'system-users',
        componentSourceFile: 'apps/web/src/modules/system/users/UserListView.vue',
        requiredPermissions: ['system:user:view'],
      },
    ])
    expect(inventory.topLevelCounts['/system']).toBe(1)
  })

  it('重定向路由目标存在且不计为独立页面组件', () => {
    const inventory = buildRouteInventory()

    expect(inventory.redirects.map((route) => `${route.path} -> ${route.redirect}`)).toEqual([
      '/inventory -> /inventory/balances',
      '/quality -> /quality/inspections',
    ])
    expect(inventory.missingRedirectTargets).toEqual([])
    expect(inventory.redirects.every((route) => route.routeType === 'redirect')).toBe(true)
    expect(inventory.redirects.every((route) => route.componentImport === null)).toBe(true)
  })

  it('动态 import 指向存在的 Vue 页面文件', () => {
    const inventory = buildRouteInventory()
    const directRouteComponentImports = inventory.routes.filter((route) => route.componentImport)

    expect(directRouteComponentImports, summarizeRouteInventory(inventory)).toHaveLength(202)
    expect(inventory.dynamicImports, summarizeRouteInventory(inventory)).toHaveLength(218)
    expect(inventory.missingDynamicImports).toEqual([])
  })

  it('16 个报表配置全部接入 reportPageComponent', () => {
    const inventory = buildRouteInventory()

    expect(inventory.reportConfigRoutes).toHaveLength(16)
    expect(inventory.reportConfigsMissingComponentCase).toEqual([])
  })
})
