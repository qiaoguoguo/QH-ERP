<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { MenuNode } from './shared/api/accountPermissionApi'
import { useAuthStore } from './stores/authStore'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const isLogin = computed(() => route.name === 'login')
const supportedMenuPaths = new Set(['/accounts/users', '/system/users', '/accounts/roles', '/system/roles'])
const menuTree = computed<MenuNode[]>(() => filterSupportedMenus(authStore.menus ?? []))
const displayName = computed(() => authStore.currentUser?.displayName ?? authStore.currentUser?.username ?? '未登录')

function filterSupportedMenus(menus: MenuNode[]): MenuNode[] {
  return menus
    .map((menu) => ({
      ...menu,
      children: filterSupportedMenus(menu.children ?? []),
    }))
    .filter((menu) => (menu.routePath ? supportedMenuPaths.has(menu.routePath) : false) || Boolean(menu.children?.length))
}

function hasChildren(menu: MenuNode) {
  return Boolean(menu.children?.length)
}

function menuIndex(menu: MenuNode) {
  return menu.routePath || `/menu/${menu.code}`
}

async function logout() {
  await authStore.logout()
  await router.replace('/login')
}
</script>

<template>
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
          <el-button link type="primary" @click="logout">退出</el-button>
        </div>
      </el-header>

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
