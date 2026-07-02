import { createMemoryHistory, createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../stores/authStore'

const history = import.meta.env.MODE === 'test' ? createMemoryHistory() : createWebHistory()

declare module 'vue-router' {
  interface RouteMeta {
    guestOnly?: boolean
    requiresAuth?: boolean
    requiredPermission?: string
  }
}

const placeholder = (title: string, description: string) => ({
  template: `<section><h1>${title}</h1><p>${description}</p></section>`,
})

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    meta: { guestOnly: true },
    component: placeholder('登录', '账号权限登录入口占位，完整登录页由后续任务实现。'),
  },
  {
    path: '/forbidden',
    name: 'forbidden',
    component: placeholder('无权限访问', '当前账号没有访问该功能的权限。'),
  },
  {
    path: '/',
    name: 'home',
    component: placeholder('工作台', '工程骨架已就绪，等待接入账号权限模块。'),
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
    meta: { requiresAuth: true, requiredPermission: 'system:user:view' },
    component: placeholder('用户管理', '用户管理页面占位，完整页面由后续任务实现。'),
  },
  {
    path: '/accounts/roles',
    name: 'system-roles',
    meta: { requiresAuth: true, requiredPermission: 'system:role:view' },
    component: placeholder('角色管理', '角色管理页面占位，完整页面由后续任务实现。'),
  },
  {
    path: '/accounts/roles/:id/permissions',
    name: 'system-role-permissions',
    meta: { requiresAuth: true, requiredPermission: 'system:role:assign-permission' },
    component: placeholder('角色权限配置', '角色权限配置页面占位，完整页面由后续任务实现。'),
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

    if (to.meta.requiredPermission && !authStore.hasPermission(to.meta.requiredPermission)) {
      return { name: 'forbidden', query: { from: to.fullPath } }
    }

    return true
  })

  return appRouter
}

export const router = createQhErpRouter()
