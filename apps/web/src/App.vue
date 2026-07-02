<template>
  <el-container class="app-shell">
    <el-aside class="app-sidebar" width="220px">
      <div class="brand">QH ERP</div>
      <el-menu default-active="/" router>
        <el-menu-item index="/">工作台</el-menu-item>
        <el-menu-item index="/accounts">账号权限</el-menu-item>
        <el-menu-item index="/materials">物料管理</el-menu-item>
        <el-menu-item index="/production">生产管理</el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <span>制造业生产管理 ERP</span>
        <el-tag type="success">工程骨架已就绪</el-tag>
      </el-header>

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
