import type { RouteRecordRaw } from 'vue-router'

export const knowledgeRoutes: RouteRecordRaw[] = [
  {
    path: '/ai-assistant',
    name: 'ai-assistant',
    meta: { requiresAuth: true },
    redirect: '/',
  },
  {
    path: '/help',
    name: 'help-center',
    meta: { requiresAuth: true },
    component: () => import('../../modules/help/HelpCenterView.vue'),
  },
  {
    path: '/help/articles/:id',
    name: 'help-article',
    meta: { requiresAuth: true },
    component: () => import('../../modules/help/KnowledgeArticleView.vue'),
  },
  {
    path: '/system/knowledge',
    name: 'system-knowledge',
    meta: { requiresAuth: true, requiredPermission: 'system:knowledge:manage' },
    component: () => import('../../modules/system/knowledge/KnowledgeManagementView.vue'),
  },
  {
    path: '/system/knowledge/create',
    name: 'system-knowledge-create',
    meta: { requiresAuth: true, requiredPermission: 'system:knowledge:manage' },
    component: () => import('../../modules/system/knowledge/KnowledgeEditorView.vue'),
  },
  {
    path: '/system/knowledge/:id/edit',
    name: 'system-knowledge-edit',
    meta: { requiresAuth: true, requiredPermission: 'system:knowledge:manage' },
    component: () => import('../../modules/system/knowledge/KnowledgeEditorView.vue'),
  },
]
