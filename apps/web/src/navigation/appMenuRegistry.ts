import type { MenuNode } from '../shared/api/accountPermissionApi'
import { periodCloseChildren, periodCloseMenuPaths } from './periodCloseMenu'

interface RegisteredModuleMenuGroup {
  id: string
  code: string
  name: string
  children: MenuNode[]
  paths: Set<string | null | undefined>
}

export const registeredModuleMenuGroups: RegisteredModuleMenuGroup[] = [
  {
    id: 'period-close',
    code: 'period-close',
    name: '业务月结',
    children: periodCloseChildren,
    paths: periodCloseMenuPaths,
  },
]

export const registeredModuleMenuPaths = new Set<string>(
  registeredModuleMenuGroups.flatMap((group) =>
    group.children
      .map((child) => child.routePath)
      .filter((path): path is string => typeof path === 'string' && path.length > 0),
  ),
)

export function applyRegisteredModuleMenus(
  menus: MenuNode[],
  hasPermission: (permission: string) => boolean,
  supportedMenuPaths: Set<string>,
): MenuNode[] {
  return registeredModuleMenuGroups.reduce(
    (nextMenus, group) => ensureRegisteredModuleMenu(nextMenus, group, hasPermission, supportedMenuPaths),
    menus,
  )
}

function ensureRegisteredModuleMenu(
  menus: MenuNode[],
  group: RegisteredModuleMenuGroup,
  hasPermission: (permission: string) => boolean,
  supportedMenuPaths: Set<string>,
): MenuNode[] {
  const allowedChildren = group.children.filter((child) => hasPermission(String(child.code)))
  const cleanedMenus = removeRegisteredModuleMenus(menus, group, supportedMenuPaths)
  if (!allowedChildren.length) {
    return cleanedMenus
  }

  return [
    ...cleanedMenus,
    {
      id: group.id,
      code: group.code,
      name: group.name,
      routePath: null,
      children: allowedChildren,
    },
  ]
}

function removeRegisteredModuleMenus(
  menus: MenuNode[],
  group: RegisteredModuleMenuGroup,
  supportedMenuPaths: Set<string>,
): MenuNode[] {
  return menus
    .map((menu) => ({
      ...menu,
      children: removeRegisteredModuleMenus(menu.children ?? [], group, supportedMenuPaths),
    }))
    .filter((menu) => !isRegisteredModuleMenu(menu, group) && (
      (menu.routePath ? supportedMenuPaths.has(menu.routePath) : false) || Boolean(menu.children?.length)
    ))
}

function isRegisteredModuleMenu(menu: MenuNode, group: RegisteredModuleMenuGroup): boolean {
  return String(menu.code ?? '') === group.code
    || (menu.routePath ? group.paths.has(menu.routePath) : false)
}
