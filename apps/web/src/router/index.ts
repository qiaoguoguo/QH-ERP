import { h } from 'vue'
import { createMemoryHistory, createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../stores/authStore'

const history = import.meta.env.MODE === 'test' ? createMemoryHistory() : createWebHistory()

declare module 'vue-router' {
  interface RouteMeta {
    guestOnly?: boolean
    requiresAuth?: boolean
    requiredPermission?: string
    requiredPermissions?: string[]
  }
}

const placeholder = (title: string, description: string) => ({
  render: () => h('section', [h('h1', title), h('p', description)]),
})

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    meta: { guestOnly: true },
    component: () => import('../modules/auth/LoginView.vue'),
  },
  {
    path: '/forbidden',
    name: 'forbidden',
    component: placeholder('无权限访问', '当前账号没有访问该功能的权限。'),
  },
  {
    path: '/',
    name: 'home',
    component: placeholder('工作台', '账号与权限基础已接入，后续承接物料、BOM、库存和生产模块。'),
  },
  {
    path: '/accounts',
    name: 'accounts',
    meta: { requiresAuth: true },
    component: placeholder('账号权限', '账号、角色、菜单和操作权限基础入口。'),
  },
  {
    path: '/accounts/users',
    name: 'system-users',
    alias: '/system/users',
    meta: { requiresAuth: true, requiredPermission: 'system:user:view' },
    component: () => import('../modules/system/users/UserListView.vue'),
  },
  {
    path: '/accounts/roles',
    name: 'system-roles',
    alias: '/system/roles',
    meta: { requiresAuth: true, requiredPermission: 'system:role:view' },
    component: () => import('../modules/system/roles/RoleListView.vue'),
  },
  {
    path: '/accounts/roles/:id/permissions',
    name: 'system-role-permissions',
    alias: '/system/roles/:id/permissions',
    meta: {
      requiresAuth: true,
      requiredPermissions: ['system:role:view', 'system:permission:view', 'system:role:assign-permission'],
    },
    component: () => import('../modules/system/roles/RolePermissionView.vue'),
  },
  {
    path: '/materials',
    name: 'materials',
    component: placeholder('物料管理', '物料、单位、分类和 BOM 前置资料入口。'),
  },
  {
    path: '/production',
    name: 'production',
    component: placeholder('生产管理', '生产工单、领料、报工和完工入库入口。'),
  },
]

export function createQhErpRouter() {
  const appRouter = createRouter({
    history: import.meta.env.MODE === 'test' ? createMemoryHistory() : history,
    routes,
  })

  appRouter.beforeEach(async (to) => {
    if (!to.meta.requiresAuth && !to.meta.guestOnly) {
      return true
    }

    const authStore = useAuthStore()
    if (!authStore.currentUser) {
      try {
        await authStore.fetchCurrentUser()
      } catch {
        if (to.meta.requiresAuth) {
          return { name: 'login', query: { redirect: to.fullPath } }
        }

        return true
      }
    }

    if (to.meta.guestOnly && authStore.isAuthenticated) {
      return { name: 'home' }
    }

    if (!to.meta.requiresAuth) {
      return true
    }

    if (!authStore.isAuthenticated) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }

    const requiredPermissions = [
      ...(to.meta.requiredPermission ? [to.meta.requiredPermission] : []),
      ...(to.meta.requiredPermissions ?? []),
    ]
    if (requiredPermissions.some((permission) => !authStore.hasPermission(permission))) {
      return { name: 'forbidden', query: { from: to.fullPath } }
    }

    return true
  })

  return appRouter
}

export const router = createQhErpRouter()
