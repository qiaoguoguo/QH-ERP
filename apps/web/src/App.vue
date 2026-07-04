<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { MenuNode } from './shared/api/accountPermissionApi'
import { useAuthStore } from './stores/authStore'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const isLogin = computed(() => route.name === 'login')
const inventoryBalancePath = '/inventory/balances'
const inventoryMovementPath = '/inventory/movements'
const inventoryDocumentPath = '/inventory/documents'
const procurementOrderPath = '/procurement/orders'
const procurementReceiptPath = '/procurement/receipts'
const productionWorkOrderPath = '/production/work-orders'
const costRecordPath = '/cost/records'
const supportedMenuPaths = new Set([
  '/accounts/users',
  '/system/users',
  '/accounts/roles',
  '/system/roles',
  '/master/units',
  '/master/warehouses',
  '/master/suppliers',
  '/master/customers',
  '/materials/categories',
  '/materials/items',
  '/materials/boms',
  inventoryBalancePath,
  inventoryMovementPath,
  inventoryDocumentPath,
  procurementOrderPath,
  procurementReceiptPath,
  productionWorkOrderPath,
  costRecordPath,
])
const inventoryChildren: MenuNode[] = [
  {
    id: 'inventory-balances',
    code: 'inventory:balance:view',
    name: '库存余额',
    routePath: inventoryBalancePath,
  },
  {
    id: 'inventory-movements',
    code: 'inventory:movement:view',
    name: '库存变动',
    routePath: inventoryMovementPath,
  },
  {
    id: 'inventory-documents',
    code: 'inventory:document:view',
    name: '库存单据',
    routePath: inventoryDocumentPath,
  },
]
const inventoryMenuPaths = new Set(inventoryChildren.map((child) => child.routePath))
const procurementChildren: MenuNode[] = [
  {
    id: 'procurement-orders',
    code: 'procurement:order:view',
    name: '采购订单',
    routePath: procurementOrderPath,
  },
  {
    id: 'procurement-receipts',
    code: 'procurement:receipt:view',
    name: '采购入库',
    routePath: procurementReceiptPath,
  },
]
const procurementMenuPaths = new Set(procurementChildren.map((child) => child.routePath))
const menuTree = computed<MenuNode[]>(() => ensureCostMenu(
  ensureProductionMenu(ensureProcurementMenu(ensureInventoryMenu(filterSupportedMenus(authStore.menus ?? [])))),
))
const displayName = computed(() => authStore.currentUser?.displayName ?? authStore.currentUser?.username ?? '未登录')
const logoutError = ref('')
const logoutLoading = ref(false)

function filterSupportedMenus(menus: MenuNode[]): MenuNode[] {
  return menus
    .map((menu) => ({
      ...menu,
      children: filterSupportedMenus(menu.children ?? []),
    }))
    .filter((menu) => (menu.routePath ? supportedMenuPaths.has(menu.routePath) : false) || Boolean(menu.children?.length))
}

function ensureInventoryMenu(menus: MenuNode[]): MenuNode[] {
  const allowedChildren = inventoryChildren.filter((child) => authStore.hasPermission(String(child.code)))
  if (!allowedChildren.length) {
    return menus
  }

  const inventoryIndex = menus.findIndex((menu) =>
    menu.code === 'inventory' || (menu.routePath ? inventoryMenuPaths.has(menu.routePath) : false))
  if (inventoryIndex === -1) {
    return [
      ...menus,
      {
        id: 'inventory',
        code: 'inventory',
        name: '库存管理',
        routePath: null,
        children: allowedChildren,
      },
    ]
  }

  return menus.map((menu, index) => {
    if (index !== inventoryIndex) {
      return menu
    }
    const children = [...(menu.children ?? [])]
    for (const child of allowedChildren) {
      if (!children.some((existing) => existing.routePath === child.routePath)) {
        children.push(child)
      }
    }
    return {
      ...menu,
      name: '库存管理',
      children,
    }
  })
}

function ensureProcurementMenu(menus: MenuNode[]): MenuNode[] {
  const allowedChildren = procurementChildren.filter((child) => authStore.hasPermission(String(child.code)))
  if (!allowedChildren.length) {
    return removeProcurementMenus(menus)
  }

  const allowedMenuPaths = new Set(allowedChildren.map((child) => child.routePath))
  const procurementIndex = menus.findIndex((menu) =>
    menu.code === 'procurement' || (menu.routePath ? procurementMenuPaths.has(menu.routePath) : false))
  if (procurementIndex === -1) {
    return [
      ...menus,
      {
        id: 'procurement',
        code: 'procurement',
        name: '采购管理',
        routePath: null,
        children: allowedChildren,
      },
    ]
  }

  return menus.map((menu, index) => {
    if (index !== procurementIndex) {
      return menu
    }
    const children = (menu.children ?? []).filter((child) =>
      child.routePath ? allowedMenuPaths.has(child.routePath) : false)
    for (const child of allowedChildren) {
      if (!children.some((existing) => existing.routePath === child.routePath)) {
        children.push(child)
      }
    }
    return {
      ...menu,
      name: '采购管理',
      children,
    }
  })
}

function isProcurementMenu(menu: MenuNode): boolean {
  const code = String(menu.code ?? '')
  return code === 'procurement'
    || code.startsWith('procurement:')
    || (menu.routePath ? procurementMenuPaths.has(menu.routePath) : false)
}

function removeProcurementMenus(menus: MenuNode[]): MenuNode[] {
  return menus
    .map((menu) => ({
      ...menu,
      children: removeProcurementMenus(menu.children ?? []),
    }))
    .filter((menu) => !isProcurementMenu(menu) && (
      (menu.routePath ? supportedMenuPaths.has(menu.routePath) : false) || Boolean(menu.children?.length)
    ))
}

function ensureProductionMenu(menus: MenuNode[]): MenuNode[] {
  if (!authStore.hasPermission('production:work-order:view')) {
    return menus.filter((menu) => menu.code !== 'production' && menu.routePath !== productionWorkOrderPath)
  }

  const productionChild: MenuNode = {
    id: 'production-work-orders',
    code: 'production:work-order:view',
    name: '生产工单',
    routePath: productionWorkOrderPath,
  }
  const productionIndex = menus.findIndex((menu) => menu.code === 'production' || menu.routePath === productionWorkOrderPath)

  if (productionIndex === -1) {
    return [
      ...menus,
      {
        id: 'production',
        code: 'production',
        name: '生产管理',
        routePath: null,
        children: [productionChild],
      },
    ]
  }

  return menus.map((menu, index) => {
    if (index !== productionIndex) {
      return menu
    }
    const children = menu.children ?? []
    const hasWorkOrderChild = children.some((child) => child.routePath === productionWorkOrderPath)
    return {
      ...menu,
      name: '生产管理',
      children: hasWorkOrderChild ? children : [productionChild, ...children],
    }
  })
}

function ensureCostMenu(menus: MenuNode[]): MenuNode[] {
  if (!authStore.hasPermission('cost:record:view')) {
    return menus.filter((menu) => menu.code !== 'cost' && menu.routePath !== costRecordPath)
  }

  const costChild: MenuNode = {
    id: 'cost-records',
    code: 'cost:record:view',
    name: '成本记录',
    routePath: costRecordPath,
  }
  const costIndex = menus.findIndex((menu) => menu.code === 'cost' || menu.routePath === costRecordPath)

  if (costIndex === -1) {
    return [
      ...menus,
      {
        id: 'cost',
        code: 'cost',
        name: '成本管理',
        routePath: null,
        children: [costChild],
      },
    ]
  }

  return menus.map((menu, index) => {
    if (index !== costIndex) {
      return menu
    }
    const children = menu.children ?? []
    const hasCostRecordChild = children.some((child) => child.routePath === costRecordPath)
    return {
      ...menu,
      name: '成本管理',
      children: hasCostRecordChild ? children : [costChild, ...children],
    }
  })
}

function hasChildren(menu: MenuNode) {
  return Boolean(menu.children?.length)
}

function menuIndex(menu: MenuNode) {
  if (hasChildren(menu)) {
    return `/menu/${menu.code}`
  }
  return menu.routePath || `/menu/${menu.code}`
}

async function logout() {
  if (logoutLoading.value) {
    return
  }
  logoutError.value = ''
  logoutLoading.value = true
  try {
    await authStore.logout()
    await router.replace({ name: 'login' })
  } catch (error) {
    logoutError.value = error instanceof Error ? error.message : '退出失败，请重试'
  } finally {
    logoutLoading.value = false
  }
}
</script>

<template>
  <el-alert v-if="logoutError" class="global-alert" type="warning" :title="logoutError" show-icon :closable="false" />
  <router-view v-if="isLogin" />

  <el-container v-else class="app-shell">
    <el-aside class="app-sidebar" width="232px">
      <div class="brand">
        <strong>QH ERP</strong>
        <span>制造业生产管理 ERP</span>
      </div>
      <el-menu :default-active="$route.path" router class="side-menu">
        <el-menu-item index="/">工作台</el-menu-item>
        <template v-for="menu in menuTree" :key="menu.code">
          <el-sub-menu v-if="hasChildren(menu)" :index="menuIndex(menu)">
            <template #title>{{ menu.name }}</template>
            <el-menu-item
              v-for="child in menu.children"
              :key="child.code"
              :index="menuIndex(child)"
            >
              {{ child.name }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else-if="menu.routePath" :index="menu.routePath">
            {{ menu.name }}
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <div>
          <strong>QH ERP</strong>
          <span>账号与权限基础</span>
        </div>
        <div class="header-user">
          <span>{{ displayName }}</span>
          <el-button
            data-test="logout-button"
            link
            type="primary"
            :loading="logoutLoading"
            :disabled="logoutLoading"
            @click="logout"
          >
            {{ logoutLoading ? '退出中' : '退出' }}
          </el-button>
        </div>
      </el-header>

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
