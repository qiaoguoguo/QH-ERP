<script setup lang="ts">
import type { Component } from 'vue'
import { computed, onMounted, ref, watch } from 'vue'
import {
  Box,
  Coin,
  Collection,
  Cpu,
  Expand,
  Fold,
  Grid,
  House,
  Money,
  OfficeBuilding,
  Sell,
  Setting,
  ShoppingCart,
  TrendCharts,
} from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import type { MenuNode } from './shared/api/accountPermissionApi'
import { financePermissions } from './modules/finance/financePageHelpers'
import { reportMenuChildren, reportRouteConfigs } from './modules/reports/reportPageHelpers'
import { activeMenuPath } from './shared/navigation/navigationReturn'
import { useAuthStore } from './stores/authStore'
import qhLogoUrl from './assets/logo.ico'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const isLogin = computed(() => route.name === 'login')
const sideMenuActivePath = computed(() => activeMenuPath(route.path, route.query.returnTo))
const businessPeriodPath = '/system/business-periods'
const inventoryBalancePath = '/inventory/balances'
const inventoryMovementPath = '/inventory/movements'
const inventoryDocumentPath = '/inventory/documents'
const procurementOrderPath = '/procurement/orders'
const procurementReceiptPath = '/procurement/receipts'
const procurementReturnPath = '/procurement/returns'
const salesOrderPath = '/sales/orders'
const salesShipmentPath = '/sales/shipments'
const salesReturnPath = '/sales/returns'
const productionWorkOrderPath = '/production/work-orders'
const productionMaterialReturnPath = '/production/material-returns'
const productionMaterialSupplementPath = '/production/material-supplements'
const qualityInspectionPath = '/quality/inspections'
const costRecordPath = '/cost/records'
const financeReceivablePath = '/finance/receivables'
const financeReceiptPath = '/finance/receipts'
const financePayablePath = '/finance/payables'
const financePaymentPath = '/finance/payments'
const financeSettlementAdjustmentPath = '/finance/settlement-adjustments'
const reportMenuPaths = new Set(reportRouteConfigs.map((item) => item.path))
const supportedMenuPaths = new Set([
  '/accounts/users',
  '/system/users',
  '/accounts/roles',
  '/system/roles',
  businessPeriodPath,
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
  procurementReturnPath,
  salesOrderPath,
  salesShipmentPath,
  salesReturnPath,
  productionWorkOrderPath,
  productionMaterialReturnPath,
  productionMaterialSupplementPath,
  qualityInspectionPath,
  costRecordPath,
  financeReceivablePath,
  financeReceiptPath,
  financePayablePath,
  financePaymentPath,
  financeSettlementAdjustmentPath,
  ...reportMenuPaths,
])
const systemChildren: MenuNode[] = [
  {
    id: 'system-business-periods',
    code: 'system:business-period:view',
    name: '业务期间',
    routePath: businessPeriodPath,
  },
]
const systemMenuPaths = new Set(systemChildren.map((child) => child.routePath))
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
  {
    id: 'procurement-returns',
    code: 'procurement:return:view',
    name: '采购退货',
    routePath: procurementReturnPath,
  },
]
const procurementMenuPaths = new Set(procurementChildren.map((child) => child.routePath))
const salesChildren: MenuNode[] = [
  {
    id: 'sales-orders',
    code: 'sales:order:view',
    name: '销售订单',
    routePath: salesOrderPath,
  },
  {
    id: 'sales-shipments',
    code: 'sales:shipment:view',
    name: '销售出库',
    routePath: salesShipmentPath,
  },
  {
    id: 'sales-returns',
    code: 'sales:return:view',
    name: '销售退货',
    routePath: salesReturnPath,
  },
]
const salesMenuPaths = new Set(salesChildren.map((child) => child.routePath))
const productionChildren: MenuNode[] = [
  {
    id: 'production-work-orders',
    code: 'production:work-order:view',
    name: '生产工单',
    routePath: productionWorkOrderPath,
  },
  {
    id: 'production-material-returns',
    code: 'production:material-return:view',
    name: '生产退料',
    routePath: productionMaterialReturnPath,
  },
  {
    id: 'production-material-supplements',
    code: 'production:material-supplement:view',
    name: '生产补料',
    routePath: productionMaterialSupplementPath,
  },
]
const productionMenuPaths = new Set(productionChildren.map((child) => child.routePath))
const qualityChildren: MenuNode[] = [
  {
    id: 'quality-inspections',
    code: 'quality:inspection:view',
    name: '质量确认',
    routePath: qualityInspectionPath,
  },
]
const qualityMenuPaths = new Set(qualityChildren.map((child) => child.routePath))
const financeChildren: MenuNode[] = [
  {
    id: 'finance-receivables',
    code: financePermissions.receivableView,
    name: '应收台账',
    routePath: financeReceivablePath,
  },
  {
    id: 'finance-receipts',
    code: financePermissions.receiptView,
    name: '收款记录',
    routePath: financeReceiptPath,
  },
  {
    id: 'finance-payables',
    code: financePermissions.payableView,
    name: '应付台账',
    routePath: financePayablePath,
  },
  {
    id: 'finance-payments',
    code: financePermissions.paymentView,
    name: '付款记录',
    routePath: financePaymentPath,
  },
  {
    id: 'finance-settlement-adjustments',
    code: financePermissions.settlementAdjustmentView,
    name: '往来冲减',
    routePath: financeSettlementAdjustmentPath,
  },
]
const financeMenuPaths = new Set(financeChildren.map((child) => child.routePath))
const fallbackMainMenuIcon = Grid
const mainMenuIconRules: Array<[RegExp, Component]> = [
  [/system|系统/, Setting],
  [/master|基础/, OfficeBuilding],
  [/material|物料|bom/i, Collection],
  [/inventory|库存/, Box],
  [/quality|质量/, Collection],
  [/procurement|purchase|采购/, ShoppingCart],
  [/sales|销售/, Sell],
  [/production|生产/, Cpu],
  [/cost|成本/, Coin],
  [/finance|财务|往来|应收|应付/, Money],
  [/report|报表|经营/, TrendCharts],
]
const menuTree = computed<MenuNode[]>(() => {
  const supportedMenus = filterSupportedMenus(authStore.menus ?? [])
  const systemMenus = ensureSystemMenu(supportedMenus)
  const inventoryMenus = ensureInventoryMenu(systemMenus)
  const procurementMenus = ensureProcurementMenu(inventoryMenus)
  const salesMenus = ensureSalesMenu(procurementMenus)
  const productionMenus = ensureProductionMenu(salesMenus)
  const qualityMenus = ensureQualityMenu(productionMenus)
  const costMenus = ensureCostMenu(qualityMenus)
  const financeMenus = ensureFinanceMenu(costMenus)
  return ensureReportsMenu(financeMenus)
})
const displayName = computed(() => authStore.currentUser?.displayName ?? authStore.currentUser?.username ?? '未登录')
const logoutError = ref('')
const logoutLoading = ref(false)
const sessionHydrating = ref(false)
const sidebarCollapsed = ref(false)
const sidebarToggleLabel = computed(() => sidebarCollapsed.value ? '展开菜单' : '收起菜单')
const sidebarToggleIcon = computed(() => sidebarCollapsed.value ? Expand : Fold)

async function hydrateCurrentUser() {
  if (isLogin.value || authStore.currentUser || sessionHydrating.value) {
    return
  }
  sessionHydrating.value = true
  try {
    await authStore.fetchCurrentUser()
  } catch {
    // 未登录访问公开入口时保持未登录展示；有效后端会话会在这里恢复顶部用户。
  } finally {
    sessionHydrating.value = false
  }
}

onMounted(() => {
  void hydrateCurrentUser()
})

watch(isLogin, () => {
  void hydrateCurrentUser()
})

function filterSupportedMenus(menus: MenuNode[]): MenuNode[] {
  return menus
    .map((menu) => ({
      ...menu,
      children: filterSupportedMenus(menu.children ?? []),
    }))
    .filter((menu) => (menu.routePath ? supportedMenuPaths.has(menu.routePath) : false) || Boolean(menu.children?.length))
}

function ensureSystemMenu(menus: MenuNode[]): MenuNode[] {
  const allowedChildren = systemChildren.filter((child) => authStore.hasPermission(String(child.code)))
  if (!allowedChildren.length) {
    return menus
  }

  const systemIndex = menus.findIndex((menu) =>
    menu.code === 'system' || (menu.routePath ? systemMenuPaths.has(menu.routePath) : false))
  if (systemIndex === -1) {
    return [
      ...menus,
      {
        id: 'system',
        code: 'system',
        name: '系统管理',
        routePath: null,
        children: allowedChildren,
      },
    ]
  }

  return menus.map((menu, index) => {
    if (index !== systemIndex) {
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
      name: '系统管理',
      children,
    }
  })
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

function ensureSalesMenu(menus: MenuNode[]): MenuNode[] {
  const allowedChildren = salesChildren.filter((child) => authStore.hasPermission(String(child.code)))
  const cleanedMenus = removeSalesMenus(menus)
  if (!allowedChildren.length) {
    return cleanedMenus
  }

  return [
    ...cleanedMenus,
    {
      id: 'sales',
      code: 'sales',
      name: '销售管理',
      routePath: null,
      children: allowedChildren,
    },
  ]
}

function isSalesMenu(menu: MenuNode): boolean {
  const code = String(menu.code ?? '')
  return code === 'sales'
    || code.startsWith('sales:')
    || (menu.routePath ? salesMenuPaths.has(menu.routePath) : false)
}

function removeSalesMenus(menus: MenuNode[]): MenuNode[] {
  return menus
    .map((menu) => ({
      ...menu,
      children: removeSalesMenus(menu.children ?? []),
    }))
    .filter((menu) => !isSalesMenu(menu) && (
      (menu.routePath ? supportedMenuPaths.has(menu.routePath) : false) || Boolean(menu.children?.length)
    ))
}

function ensureProductionMenu(menus: MenuNode[]): MenuNode[] {
  const allowedChildren = productionChildren.filter((child) => authStore.hasPermission(String(child.code)))
  const cleanedMenus = removeProductionMenus(menus)
  if (!allowedChildren.length) {
    return cleanedMenus
  }

  return [
    ...cleanedMenus,
    {
      id: 'production',
      code: 'production',
      name: '生产管理',
      routePath: null,
      children: allowedChildren,
    },
  ]
}

function isProductionMenu(menu: MenuNode): boolean {
  const code = String(menu.code ?? '')
  return code === 'production'
    || code.startsWith('production:')
    || (menu.routePath ? productionMenuPaths.has(menu.routePath) : false)
}

function removeProductionMenus(menus: MenuNode[]): MenuNode[] {
  return menus
    .map((menu) => ({
      ...menu,
      children: removeProductionMenus(menu.children ?? []),
    }))
    .filter((menu) => !isProductionMenu(menu) && (
      (menu.routePath ? supportedMenuPaths.has(menu.routePath) : false) || Boolean(menu.children?.length)
    ))
}

function ensureQualityMenu(menus: MenuNode[]): MenuNode[] {
  const allowedChildren = qualityChildren.filter((child) => authStore.hasPermission(String(child.code)))
  const cleanedMenus = removeQualityMenus(menus)
  if (!allowedChildren.length) {
    return cleanedMenus
  }

  return [
    ...cleanedMenus,
    {
      id: 'quality',
      code: 'quality',
      name: '质量管理',
      routePath: null,
      children: allowedChildren,
    },
  ]
}

function isQualityMenu(menu: MenuNode): boolean {
  const code = String(menu.code ?? '')
  return code === 'quality'
    || code.startsWith('quality:')
    || (menu.routePath ? qualityMenuPaths.has(menu.routePath) : false)
}

function removeQualityMenus(menus: MenuNode[]): MenuNode[] {
  return menus
    .map((menu) => ({
      ...menu,
      children: removeQualityMenus(menu.children ?? []),
    }))
    .filter((menu) => !isQualityMenu(menu) && (
      (menu.routePath ? supportedMenuPaths.has(menu.routePath) : false) || Boolean(menu.children?.length)
    ))
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

function ensureFinanceMenu(menus: MenuNode[]): MenuNode[] {
  const allowedChildren = financeChildren.filter((child) => authStore.hasPermission(String(child.code)))
  const cleanedMenus = removeFinanceMenus(menus)
  if (!allowedChildren.length) {
    return cleanedMenus
  }

  return [
    ...cleanedMenus,
    {
      id: 'finance',
      code: 'finance',
      name: '财务往来',
      routePath: null,
      children: allowedChildren,
    },
  ]
}

function isFinanceMenu(menu: MenuNode): boolean {
  const code = String(menu.code ?? '')
  return code === 'finance'
    || code.startsWith('finance:')
    || (menu.routePath ? financeMenuPaths.has(menu.routePath) : false)
}

function removeFinanceMenus(menus: MenuNode[]): MenuNode[] {
  return menus
    .map((menu) => ({
      ...menu,
      children: removeFinanceMenus(menu.children ?? []),
    }))
    .filter((menu) => !isFinanceMenu(menu) && (
      (menu.routePath ? supportedMenuPaths.has(menu.routePath) : false) || Boolean(menu.children?.length)
    ))
}

function ensureReportsMenu(menus: MenuNode[]): MenuNode[] {
  const allowedChildren = reportMenuChildren.filter((child) => authStore.hasPermission(String(child.code)))
  const cleanedMenus = removeReportMenus(menus)
  if (!allowedChildren.length) {
    return cleanedMenus
  }

  return [
    ...cleanedMenus,
    {
      id: 'reports',
      code: 'report',
      name: '经营报表',
      routePath: null,
      children: allowedChildren,
    },
  ]
}

function isReportMenu(menu: MenuNode): boolean {
  const code = String(menu.code ?? '')
  return code === 'report'
    || code.startsWith('report:')
    || (menu.routePath ? reportMenuPaths.has(menu.routePath) : false)
}

function removeReportMenus(menus: MenuNode[]): MenuNode[] {
  return menus
    .map((menu) => ({
      ...menu,
      children: removeReportMenus(menu.children ?? []),
    }))
    .filter((menu) => !isReportMenu(menu) && (
      (menu.routePath ? supportedMenuPaths.has(menu.routePath) : false) || Boolean(menu.children?.length)
    ))
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

function mainMenuIcon(menu: MenuNode): Component {
  const signature = `${String(menu.code ?? '')} ${menu.name} ${String(menu.routePath ?? '')}`
  return mainMenuIconRules.find(([pattern]) => pattern.test(signature))?.[1] ?? fallbackMainMenuIcon
}

function mainMenuIconKey(menu: MenuNode): string {
  const source = String(menu.code ?? menu.routePath ?? menu.name)
  return source
    .toLowerCase()
    .replace(/[^a-z0-9-]+/g, '-')
    .replace(/(^-)|(-$)/g, '') || 'default'
}

function toggleSidebarCollapsed() {
  sidebarCollapsed.value = !sidebarCollapsed.value
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
    <el-aside
      :class="['app-sidebar', { 'is-collapsed': sidebarCollapsed }]"
      :width="sidebarCollapsed ? '72px' : '232px'"
    >
      <div class="brand">
        <img data-test="app-logo" class="brand-logo" :src="qhLogoUrl" alt="QH ERP 系统标识">
        <div class="brand-copy">
          <strong>QH ERP</strong>
          <span>制造业生产管理 ERP</span>
        </div>
        <el-tooltip :content="sidebarToggleLabel" placement="right" :show-after="250">
          <button
            data-test="sidebar-toggle-button"
            class="sidebar-toggle"
            type="button"
            :aria-label="sidebarToggleLabel"
            :title="sidebarToggleLabel"
            @click="toggleSidebarCollapsed"
          >
            <component
              :is="sidebarToggleIcon"
              class="sidebar-toggle-icon"
              :data-test="sidebarCollapsed ? 'sidebar-toggle-icon-expand' : 'sidebar-toggle-icon-fold'"
            />
          </button>
        </el-tooltip>
      </div>
      <el-scrollbar class="side-menu-scroll">
        <el-menu
          :default-active="sideMenuActivePath"
          :collapse="sidebarCollapsed"
          :collapse-transition="false"
          router
          class="side-menu"
          unique-opened
        >
          <el-menu-item index="/">
            <House class="side-menu-icon" data-test="main-menu-icon-home" />
            <span>工作台</span>
          </el-menu-item>
          <template v-for="menu in menuTree" :key="menu.code">
            <el-sub-menu v-if="hasChildren(menu)" :index="menuIndex(menu)">
              <template #title>
                <component
                  :is="mainMenuIcon(menu)"
                  class="side-menu-icon"
                  :data-test="`main-menu-icon-${mainMenuIconKey(menu)}`"
                />
                <span>{{ menu.name }}</span>
              </template>
              <el-menu-item
                v-for="child in menu.children"
                :key="child.code"
                :index="menuIndex(child)"
              >
                {{ child.name }}
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else-if="menu.routePath" :index="menu.routePath">
              <component
                :is="mainMenuIcon(menu)"
                class="side-menu-icon"
                :data-test="`main-menu-icon-${mainMenuIconKey(menu)}`"
              />
              <span>{{ menu.name }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>
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
