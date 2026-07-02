import { createMemoryHistory, createWebHistory, createRouter } from 'vue-router'

const history = import.meta.env.MODE === 'test' ? createMemoryHistory() : createWebHistory()

export const router = createRouter({
  history,
  routes: [
    {
      path: '/',
      name: 'home',
      component: {
        template: '<section><h1>工作台</h1><p>工程骨架已就绪，等待接入账号权限模块。</p></section>',
      },
    },
    {
      path: '/accounts',
      name: 'accounts',
      component: { template: '<section><h1>账号权限</h1><p>账号、角色、菜单和操作权限基础入口。</p></section>' },
    },
    {
      path: '/materials',
      name: 'materials',
      component: { template: '<section><h1>物料管理</h1><p>物料、单位、分类和 BOM 前置资料入口。</p></section>' },
    },
    {
      path: '/production',
      name: 'production',
      component: { template: '<section><h1>生产管理</h1><p>生产工单、领料、报工和完工入库入口。</p></section>' },
    },
  ],
})
