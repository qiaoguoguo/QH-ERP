# 全系统操作知识库实施计划

> **固定角色执行要求：** 实施阶段必须复用项目现有产品经理、UI 设计师、前端开发、后端开发和测试五个固定长期会话。不得创建临时角色或额外审查代理。各角色只处理本计划分配给自己的工作包。

**目标：** 建设所有登录用户统一可用、超级管理员直接维护、保存后立即生效的全系统操作知识库和只读帮助中心，为后续只读 AI 助手提供唯一知识源。

**架构：** 后端新增独立知识库数据表、查询服务、管理服务和初始内容装载器；普通查询使用仅要求登录的 `/api/help/**`，维护使用受 `system:knowledge:manage` 保护的 `/api/admin/system/knowledge/**`。前端新增帮助中心、文章详情、知识管理和知识编辑页面，并在公共布局提供当前页面帮助入口；既有业务模块不调用知识库，也不被知识库反向修改。

**技术栈：** Java、Spring Boot、Spring Security、JdbcTemplate、Flyway、PostgreSQL、Vue 3、TypeScript、Vue Router、Pinia、Element Plus、Vite。

## 全局约束

- 权威设计：`docs/superpowers/specs/2026-08-14-system-knowledge-base-design.md`。
- 页面规范：`docs/ui/page-standards.md`。
- 所有登录用户统一浏览全部启用知识，不做角色过滤。
- 只有拥有 `system:knowledge:manage` 的系统管理员可维护知识；初始仅系统管理员角色获得该权限。
- 保存后立即生效，只使用“启用、停用”状态，不增加审核、发布或版本流转。
- 知识正文不包含真实业务数据，不读取当前单据内容。
- 不调用任何业务写接口，不修改业务状态机、审批规则、权限含义和数据口径。
- 不引入 AI、向量数据库、外部搜索引擎、重量级编辑器或内容管理框架。
- 正文只支持安全的受控文本结构，不执行正文中的 HTML、脚本或链接事件。
- 现有代码仅允许修改菜单、路由、公共布局、权限初始化、请求授权映射、错误码和迁移接入点。
- 未经用户明确允许，不修改测试文件，不运行测试、构建、数据库迁移或浏览器验证。
- 未经用户明确要求，不执行 Git 写操作。

---

## 文件结构与所有权

### 新增后端文件

- `apps/api/src/main/resources/db/migration/V41__system_knowledge_base.sql`：知识分类、知识文章、文章关联、索引和管理权限迁移。
- `apps/api/src/main/java/com/qherp/api/system/knowledge/KnowledgeModels.java`：请求、响应、分页筛选和枚举记录。
- `apps/api/src/main/java/com/qherp/api/system/knowledge/KnowledgeQueryService.java`：启用分类、文章搜索、文章详情、路由关联和相关文章只读查询。
- `apps/api/src/main/java/com/qherp/api/system/knowledge/KnowledgeAdminService.java`：分类和文章直接维护、启停、删除、校验和审计。
- `apps/api/src/main/java/com/qherp/api/system/knowledge/KnowledgeController.java`：所有登录用户统一使用的 `/api/help/**` 只读接口。
- `apps/api/src/main/java/com/qherp/api/system/knowledge/KnowledgeAdminController.java`：`/api/admin/system/knowledge/**` 管理接口。
- `apps/api/src/main/java/com/qherp/api/system/knowledge/KnowledgeSeedInitializer.java`：从内置知识资源装载不存在的分类和文章，不覆盖超级管理员已维护内容。
- `apps/api/src/main/resources/knowledge/categories.json`：初始知识分类。
- `apps/api/src/main/resources/knowledge/01-common-system.json`：工作台、账号权限、系统管理和通用操作知识。
- `apps/api/src/main/resources/knowledge/02-master-material-bom.json`：基础资料、物料和 BOM 知识。
- `apps/api/src/main/resources/knowledge/03-inventory-quality.json`：库存、仓库、追踪和质量知识。
- `apps/api/src/main/resources/knowledge/04-procurement.json`：采购全链路知识。
- `apps/api/src/main/resources/knowledge/05-sales.json`：销售全链路知识。
- `apps/api/src/main/resources/knowledge/06-planning-production.json`：计划、生产和委外知识。
- `apps/api/src/main/resources/knowledge/07-cost-finance-reports.json`：成本、财务、会计核算、月结和报表知识。
- `apps/api/src/main/resources/knowledge/08-platform-import-export.json`：审批待办、消息、任务、导入导出、附件、审计和来源追溯知识。

### 修改后端文件

- `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`：增加知识不存在、分类占用、编码重复和请求非法错误码。
- `apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`：将知识管理接口映射到 `system:knowledge:manage`，不拦截 `/api/help/**`。
- `apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`：注册知识管理菜单和管理权限，并自动授予系统管理员角色。

### 新增前端文件

- `apps/web/src/shared/api/knowledgeBaseApi.ts`：知识分类、搜索、详情、路由关联和管理接口类型及请求封装。
- `apps/web/src/router/modules/knowledgeRoutes.ts`：帮助中心、文章详情、知识管理和知识编辑路由。
- `apps/web/src/modules/help/HelpCenterView.vue`：统一帮助中心和搜索结果。
- `apps/web/src/modules/help/KnowledgeArticleView.vue`：知识文章详情、目录和关联知识。
- `apps/web/src/modules/help/KnowledgeContentRenderer.vue`：安全渲染标题、段落、有序列表和无序列表，不使用 `v-html`。
- `apps/web/src/modules/help/pageHelp.ts`：从当前 Vue Router 匹配记录提取规范化路由，并生成帮助查询地址。
- `apps/web/src/modules/system/knowledge/KnowledgeManagementView.vue`：知识管理筛选、表格、分页、分类维护和启停删除。
- `apps/web/src/modules/system/knowledge/KnowledgeEditorView.vue`：知识新增编辑表单。

### 修改前端文件

- `apps/web/src/router/index.ts`：注册并展开 `knowledgeRoutes`。
- `apps/web/src/App.vue`：加入所有登录用户可见的“系统帮助”入口、系统管理员知识管理菜单和当前页面帮助按钮。
- `apps/web/src/navigation/appMenuRegistry.ts`：注册知识管理菜单组所需路径，不改变其他模块菜单。

### 新增知识治理文件

- `docs/knowledge-base/coverage-matrix.md`：菜单、页面、流程、状态、字段、错误和知识文章的覆盖矩阵。
- `docs/knowledge-base/evaluation-questions.md`：真实用户问题、预期命中文章和答案要点。
- `docs/knowledge-base/content-style-guide.md`：知识标题、摘要、步骤、权限说明、状态、错误和跨模块影响写法。

### 验证文件

以下文件只在用户明确允许修改测试后创建或修改：

- `apps/api/src/test/java/com/qherp/api/system/knowledge/KnowledgeControllerTests.java`。
- `apps/api/src/test/java/com/qherp/api/system/knowledge/KnowledgeMigrationRegressionTests.java`。
- `apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`。
- `apps/web/src/shared/api/knowledgeBaseApi.spec.ts`。
- `apps/web/src/modules/help/HelpCenterView.spec.ts`。
- `apps/web/src/modules/help/KnowledgeArticleView.spec.ts`。
- `apps/web/src/modules/help/KnowledgeContentRenderer.spec.ts`。
- `apps/web/src/modules/system/knowledge/KnowledgeManagementView.spec.ts`。
- `apps/web/src/modules/system/knowledge/KnowledgeEditorView.spec.ts`。
- `apps/web/src/router/permissionGuard.spec.ts`。
- `apps/web/src/App.spec.ts`。

---

## 工作包一：知识模型、迁移和权限边界

**负责角色：** 后端开发。

**文件：**

- 新增：`apps/api/src/main/resources/db/migration/V41__system_knowledge_base.sql`
- 新增：`apps/api/src/main/java/com/qherp/api/system/knowledge/KnowledgeModels.java`
- 修改：`apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- 修改：`apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`

**产出接口：**

```java
enum KnowledgeStatus { ENABLED, DISABLED }
enum KnowledgeType { PAGE, PROCESS, FIELD, STATUS, ERROR, PERMISSION, IMPORT_EXPORT, CONCEPT }

record KnowledgeCategoryRequest(
    String code,
    String name,
    Long parentId,
    Integer sortOrder,
    KnowledgeStatus status
) {}

record KnowledgeArticleRequest(
    String slug,
    String title,
    String summary,
    Long categoryId,
    KnowledgeType knowledgeType,
    String content,
    String keywords,
    String routePaths,
    String pageNames,
    String permissionNote,
    List<Long> relatedArticleIds,
    Integer sortOrder,
    KnowledgeStatus status
) {}
```

- [ ] **步骤 1：建立知识分类表**

创建 `sys_knowledge_category`，包含 `id`、`code`、`name`、`parent_id`、`sort_order`、`status`、创建修改审计字段和 `version`。`code` 唯一，`status` 只允许 `ENABLED`、`DISABLED`，`parent_id` 引用本表。

- [ ] **步骤 2：建立知识文章表**

创建 `sys_knowledge_article`，包含 `slug`、`title`、`summary`、`category_id`、`knowledge_type`、`content`、`keywords`、`route_paths`、`page_names`、`permission_note`、`sort_order`、`status`、审计字段和 `version`。`slug` 唯一，正文使用 `text`，不保存业务对象 ID。

- [ ] **步骤 3：建立相关文章关系和检索索引**

创建 `sys_knowledge_article_relation(article_id, related_article_id)`，使用联合主键并禁止自关联。为文章状态、分类、类型、更新时间和分类层级建立普通索引；不安装 PostgreSQL 扩展。

- [ ] **步骤 4：注册知识管理权限**

在迁移和 `AccountPermissionInitializer` 中注册：

```text
system:knowledge          系统知识库管理菜单  /system/knowledge
system:knowledge:manage   维护系统知识库      /api/admin/system/knowledge/**
```

知识管理菜单归属“系统管理”，初始由现有系统管理员角色自动获得。普通帮助中心不依赖该权限。

- [ ] **步骤 5：映射管理接口授权**

在 `PermissionAuthorizationManager.permissionCode` 中增加独立的 `knowledgePermissionCode(method, path)`。凡匹配 `/api/admin/system/knowledge` 及其子路径均返回 `system:knowledge:manage`；`/api/help/**` 不进入管理接口映射，只受现有登录认证保护。

- [ ] **步骤 6：增加业务错误码**

在 `ApiErrorCode` 中增加：

```java
KNOWLEDGE_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "知识分类不存在"),
KNOWLEDGE_CATEGORY_IN_USE(HttpStatus.CONFLICT, "知识分类仍被文章或子分类使用"),
KNOWLEDGE_CATEGORY_CODE_EXISTS(HttpStatus.CONFLICT, "知识分类编码已存在"),
KNOWLEDGE_ARTICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "知识内容不存在或已停用"),
KNOWLEDGE_ARTICLE_SLUG_EXISTS(HttpStatus.CONFLICT, "知识标识已存在"),
KNOWLEDGE_RELATION_INVALID(HttpStatus.BAD_REQUEST, "关联知识不正确"),
KNOWLEDGE_REQUEST_INVALID(HttpStatus.BAD_REQUEST, "知识内容参数不正确"),
```

**完成标准：** 数据模型只依赖系统公共表和审计字段；普通帮助读取与管理权限明确分离；未触及任何业务表。

---

## 工作包二：后端查询、维护和初始装载

**负责角色：** 后端开发。

**依赖：** 工作包一的数据表、枚举和错误码。

**文件：**

- 新增：`apps/api/src/main/java/com/qherp/api/system/knowledge/KnowledgeQueryService.java`
- 新增：`apps/api/src/main/java/com/qherp/api/system/knowledge/KnowledgeAdminService.java`
- 新增：`apps/api/src/main/java/com/qherp/api/system/knowledge/KnowledgeController.java`
- 新增：`apps/api/src/main/java/com/qherp/api/system/knowledge/KnowledgeAdminController.java`
- 新增：`apps/api/src/main/java/com/qherp/api/system/knowledge/KnowledgeSeedInitializer.java`

**只读接口：**

```text
GET /api/help/categories
GET /api/help/articles?keyword=&categoryId=&knowledgeType=&page=1&pageSize=10
GET /api/help/articles/{id}
GET /api/help/articles/by-route?routePath=/procurement/orders/:id
GET /api/help/articles/{id}/related
```

**管理接口：**

```text
GET    /api/admin/system/knowledge/categories
POST   /api/admin/system/knowledge/categories
PUT    /api/admin/system/knowledge/categories/{id}
POST   /api/admin/system/knowledge/categories/{id}/enable
POST   /api/admin/system/knowledge/categories/{id}/disable
DELETE /api/admin/system/knowledge/categories/{id}
GET    /api/admin/system/knowledge/articles
GET    /api/admin/system/knowledge/articles/{id}
POST   /api/admin/system/knowledge/articles
PUT    /api/admin/system/knowledge/articles/{id}
POST   /api/admin/system/knowledge/articles/{id}/enable
POST   /api/admin/system/knowledge/articles/{id}/disable
DELETE /api/admin/system/knowledge/articles/{id}
```

- [ ] **步骤 1：实现统一只读查询**

分类接口只返回启用分类；文章列表只返回启用文章。关键词按标题、关键词、页面名称、摘要、正文顺序匹配，SQL 使用参数化 `ilike`，排序使用匹配优先级、`sort_order`、`updated_at desc`、`id desc`。

- [ ] **步骤 2：实现详情和关联查询**

普通详情必须同时满足文章启用且所属分类启用。路由查询使用完整规范化路由逐项匹配 `route_paths` 中按换行保存的路径，不接收当前单号、表单值和查询参数。

- [ ] **步骤 3：实现分类直接维护**

校验编码、名称、父级、两级目录上限和循环引用。删除分类前确认不存在子分类和文章；启停后立即影响普通查询。

- [ ] **步骤 4：实现文章直接维护**

校验标题、摘要、分类、类型、正文、状态、唯一 `slug`、关联文章存在且不自关联。创建和更新关系与文章在同一事务完成；启停后立即影响普通查询。

- [ ] **步骤 5：实现审计**

调用现有 `AuditService.record`，使用以下动作和目标类型：

```text
KNOWLEDGE_CATEGORY_CREATE / UPDATE / ENABLE / DISABLE / DELETE
KNOWLEDGE_ARTICLE_CREATE / UPDATE / ENABLE / DISABLE / DELETE
targetType: KNOWLEDGE_CATEGORY 或 KNOWLEDGE_ARTICLE
```

- [ ] **步骤 6：实现初始知识装载器**

`KnowledgeSeedInitializer` 读取 `classpath:/knowledge/categories.json` 和 `classpath:/knowledge/*.json`。分类按 `code`、文章按 `slug` 执行“仅不存在时插入”，不得覆盖超级管理员后续修改；相关文章使用 `relatedSlugs` 在所有文章插入后解析。

- [ ] **步骤 7：保证故障隔离**

知识查询和装载器不得调用任何采购、销售、库存、生产、成本、财务或审批服务。帮助接口失败只返回统一 API 错误，不影响其他控制器和业务事务。

**完成标准：** 已登录普通账号可以读取全部启用内容；无管理权限账号不能调用任何管理接口；系统管理员维护保存后立即可读。

---

## 工作包三：知识规范、覆盖矩阵和初始内容

**负责角色：** 产品经理负责内容口径，后端开发负责资源格式接入，测试角色后续独立核对覆盖。

**文件：**

- 新增：`docs/knowledge-base/content-style-guide.md`
- 新增：`docs/knowledge-base/coverage-matrix.md`
- 新增：`docs/knowledge-base/evaluation-questions.md`
- 新增：`apps/api/src/main/resources/knowledge/categories.json`
- 新增：`apps/api/src/main/resources/knowledge/01-common-system.json`
- 新增：`apps/api/src/main/resources/knowledge/02-master-material-bom.json`
- 新增：`apps/api/src/main/resources/knowledge/03-inventory-quality.json`
- 新增：`apps/api/src/main/resources/knowledge/04-procurement.json`
- 新增：`apps/api/src/main/resources/knowledge/05-sales.json`
- 新增：`apps/api/src/main/resources/knowledge/06-planning-production.json`
- 新增：`apps/api/src/main/resources/knowledge/07-cost-finance-reports.json`
- 新增：`apps/api/src/main/resources/knowledge/08-platform-import-export.json`

**文章资源格式：**

```json
{
  "slug": "procurement-price-agreement-purpose",
  "title": "价格协议有什么作用",
  "summary": "说明价格协议的来源、审批、生效条件及其对采购订单价格的影响。",
  "categoryCode": "PROCUREMENT",
  "knowledgeType": "CONCEPT",
  "content": "# 功能用途\n...\n# 操作前提\n...\n# 后续影响\n...",
  "keywords": "价格协议,协议价,采购报价,选价",
  "routePaths": "/procurement/price-agreements\n/procurement/price-agreements/:id",
  "pageNames": "价格协议\n价格协议详情",
  "permissionNote": "查看需要价格协议查看权限；维护和提交审批需要对应操作权限。",
  "relatedSlugs": ["procurement-inquiry-selection", "procurement-order-price-source"],
  "sortOrder": 100,
  "status": "ENABLED"
}
```

- [ ] **步骤 1：建立内容写作规范**

统一标题、摘要、页面入口、前置条件、步骤、字段、按钮、状态、权限、错误、上下游影响、关键词和别名的写法。正文只允许 `#`、`##`、普通段落、`-` 无序列表和 `1.` 有序列表，不写 HTML。

- [ ] **步骤 2：建立页面覆盖清单**

以当前菜单和路由为基线，每行记录模块、菜单、页面、路由、知识类型、目标文章、状态、字段规则、错误、上下游影响和验收状态。页面未实现或不适用必须明确标记，不能静默遗漏。

- [ ] **步骤 3：建立真实问题题集**

至少覆盖页面用途、按钮含义、状态原因、下一步操作、字段校验、权限、错误处理和跨模块影响。每个问题记录预期命中文章、答案要点和禁止误答内容。

- [ ] **步骤 4：编写基础、物料、库存和质量内容**

覆盖账号权限、编码规则、单位、客户供应商、物料分类与类型、物料、BOM、仓库、库存、批次序列号、质量状态、导入模板和常见错误。

- [ ] **步骤 5：编写采购、销售和审批内容**

覆盖请购、询价比价、供应商报价、选价、价格协议、采购订单、到货与入库、采购退货、销售项目、报价、合同、销售订单、信用档案、预留、交付、出库、退货和相关审批。

- [ ] **步骤 6：编写计划、生产、成本、财务和报表内容**

覆盖物料需求计划、生产工单、领退补料、报工、完工入库、委外、成本归集、应收应付、收付款、发票费用、凭证、期初、月结、固定资产现状说明和经营报表口径。

- [ ] **步骤 7：编写平台和通用治理内容**

覆盖审批待办、消息、任务、数据修复、历史导入、交付资料、附件、审计、来源链、导入导出、状态枚举和高频错误。

- [ ] **步骤 8：执行内容事实核对**

每篇文章至少绑定真实页面、路由、业务对象、状态或错误之一。当前系统不存在的能力必须明确描述为“当前未实现”，不得写成可操作功能。

**完成标准：** 覆盖矩阵不存在未说明的已上线页面；题集问题均能定位到明确文章；知识内容不依赖聊天历史和通用 ERP 猜测。

---

## 工作包四：前端帮助中心和安全内容展示

**负责角色：** UI 设计师冻结页面结构，前端开发实现。

**依赖：** 工作包二的只读接口契约。

**文件：**

- 新增：`apps/web/src/shared/api/knowledgeBaseApi.ts`
- 新增：`apps/web/src/router/modules/knowledgeRoutes.ts`
- 新增：`apps/web/src/modules/help/HelpCenterView.vue`
- 新增：`apps/web/src/modules/help/KnowledgeArticleView.vue`
- 新增：`apps/web/src/modules/help/KnowledgeContentRenderer.vue`
- 新增：`apps/web/src/modules/help/pageHelp.ts`
- 修改：`apps/web/src/router/index.ts`

**前端接口：**

```ts
export type KnowledgeStatus = 'ENABLED' | 'DISABLED'
export type KnowledgeType = 'PAGE' | 'PROCESS' | 'FIELD' | 'STATUS' | 'ERROR' | 'PERMISSION' | 'IMPORT_EXPORT' | 'CONCEPT'

export interface KnowledgeSearchParams {
  keyword?: string
  categoryId?: number | string
  knowledgeType?: KnowledgeType
  page: number
  pageSize: number
}

export interface KnowledgeArticleSummary {
  id: number | string
  slug: string
  title: string
  summary: string
  categoryId: number | string
  categoryName: string
  knowledgeType: KnowledgeType
  knowledgeTypeName: string
  pageNames: string
  updatedAt: string
}
```

- [ ] **步骤 1：封装知识 API**

复用项目现有 `ApiEnvelope`、`PageResult`、凭证和 CSRF 处理。普通 GET 调用 `/api/help/**`；管理写入调用 `/api/admin/system/knowledge/**`。

- [ ] **步骤 2：注册帮助路由**

```ts
export const knowledgeRoutes: RouteRecordRaw[] = [
  { path: '/help', name: 'help-center', meta: { requiresAuth: true }, component: () => import('../../modules/help/HelpCenterView.vue') },
  { path: '/help/articles/:id', name: 'help-article', meta: { requiresAuth: true }, component: () => import('../../modules/help/KnowledgeArticleView.vue') },
  { path: '/system/knowledge', name: 'system-knowledge', meta: { requiresAuth: true, requiredPermission: 'system:knowledge:manage' }, component: () => import('../../modules/system/knowledge/KnowledgeManagementView.vue') },
  { path: '/system/knowledge/create', name: 'system-knowledge-create', meta: { requiresAuth: true, requiredPermission: 'system:knowledge:manage' }, component: () => import('../../modules/system/knowledge/KnowledgeEditorView.vue') },
  { path: '/system/knowledge/:id/edit', name: 'system-knowledge-edit', meta: { requiresAuth: true, requiredPermission: 'system:knowledge:manage' }, component: () => import('../../modules/system/knowledge/KnowledgeEditorView.vue') },
]
```

- [ ] **步骤 3：实现安全正文渲染**

`KnowledgeContentRenderer` 按行解析 `#`、`##`、`-`、`1.` 和普通段落，使用 Vue 文本插值生成节点，不使用 `v-html`，不执行正文中的 HTML、脚本和事件属性。

- [ ] **步骤 4：实现帮助中心**

页面包含标题说明、统一搜索框、分类导航、类型筛选、搜索结果、分页、加载态、错误态和空结果提示。搜索和重置回到第一页，默认每页 10 条并支持 10、20、50、100。

- [ ] **步骤 5：实现文章详情**

展示标题、摘要、模块、类型、更新时间、正文目录和关联知识。返回动作保留帮助中心的关键词、分类、类型和分页查询参数。

- [ ] **步骤 6：实现当前页面路由提取**

`pageHelp.ts` 使用 `route.matched.at(-1)?.path` 获取 `/procurement/orders/:id` 这类规范化路由，不传递实际单号和查询参数。

**完成标准：** 所有登录用户不依赖业务权限即可访问启用知识；正文展示安全；帮助页面不提供业务动作。

---

## 工作包五：知识管理和公共布局最小接入

**负责角色：** UI 设计师冻结管理页面结构，前端开发实现。

**依赖：** 工作包二的管理接口和工作包四的 API 封装。

**文件：**

- 新增：`apps/web/src/modules/system/knowledge/KnowledgeManagementView.vue`
- 新增：`apps/web/src/modules/system/knowledge/KnowledgeEditorView.vue`
- 修改：`apps/web/src/App.vue`
- 修改：`apps/web/src/navigation/appMenuRegistry.ts`

- [ ] **步骤 1：增加统一系统帮助入口**

在所有已登录页面的公共顶部区域增加“系统帮助”入口，进入 `/help`。不得依赖任何知识权限，也不得改变原有账号、退出和侧栏行为。

- [ ] **步骤 2：增加当前页面帮助入口**

除登录页、帮助中心和知识管理页外，显示“页面帮助”。点击后进入 `/help?routePath=<规范化路由>&keyword=<页面标题>`；入口只导航，不调用业务接口。

- [ ] **步骤 3：增加知识管理菜单**

系统管理下仅对拥有 `system:knowledge:manage` 的账号显示“知识库管理”。普通用户仍通过顶部“系统帮助”访问统一知识库。

- [ ] **步骤 4：实现知识管理列表**

按页面规范实现标签置顶筛选、横向滚动表格、固定操作列和统一分页。操作提供编辑、启用、停用和删除；分类维护使用受视口限制的标准弹窗。

- [ ] **步骤 5：实现知识编辑页面**

实现标题、摘要、分类、类型、正文、关键词、页面名称、关联路由、权限说明、相关文章、排序和状态。正文旁明确展示受支持格式说明；保存中禁用重复提交，错误提示保留输入。

- [ ] **步骤 6：实现高风险确认和返回上下文**

删除、停用使用项目统一确认弹窗。编辑保存或取消返回来源列表时保留筛选和分页查询参数。

**完成标准：** 超级管理员直接维护并立即生效；普通用户无维护入口；公共布局和其他菜单行为不回归。

---

## 工作包六：验证与交付窗口

**负责角色：** 测试角色执行，产品经理、UI 设计师、前端开发和后端开发进行固定分工的集中审查。

**执行门禁：** 只有用户明确允许修改测试和执行验证后才能开始本工作包。

- [ ] **步骤 1：后端迁移与接口验证**

验证空库迁移、已有 `V40` 数据库升级、分类文章约束、普通账号统一只读、未登录拒绝、无管理权限拒绝、系统管理员 CRUD、启停立即生效、分类占用保护、审计和初始内容不覆盖人工修改。

- [ ] **步骤 2：前端 API 和组件验证**

验证查询参数、分页、CSRF、错误响应、安全正文渲染、帮助中心搜索、文章返回上下文、知识管理表单和高风险确认。

- [ ] **步骤 3：集中代码和业务审查**

产品核对知识口径与覆盖矩阵；UI 核对真实页面和页面规范；前端审查后端契约可消费性；后端审查前端未绕过权限或读取业务数据；测试核对覆盖与异常路径。

- [ ] **步骤 4：桌面浏览器检验**

在本地 `http://localhost:5174/` 真实登录会话下检查帮助中心、文章详情、当前页面帮助、管理列表、编辑页、空态、错误态、启停和普通账号统一可见。只验收桌面端，不采集截图。

- [ ] **步骤 5：知识题集验收**

逐条执行 `docs/knowledge-base/evaluation-questions.md`，确认问题能通过页面名、按钮名、字段名、状态、错误文案和口语关键词定位到正确文章，且没有把未实现功能写成已支持。

- [ ] **步骤 6：全量交付验证**

按项目阶段规则统一执行后端全量测试、前端全量测试、类型检查、生产构建、迁移回归、桌面功能与页面规范检验、环境健康和工作区范围检查。全量窗口结束后统一汇总缺陷，不在中途逐项打断。

**完成标准：** 设计验收矩阵全部闭合，无阻断或严重问题，知识库故障不影响业务模块，用户可在本地页面独立验收。

---

## 实施顺序

1. 后端开发完成工作包一的数据和权限契约。
2. 工作包二、工作包三和工作包四在冻结接口后并行推进。
3. 工作包四完成公共帮助能力后，执行工作包五的最小公共布局接入。
4. 所有内容和功能完成后，用户明确允许验证时进入工作包六。
5. 阻断和严重问题集中整改后，只复审差异和受影响路径。

## 边界检查

- 计划不存在对采购、销售、库存、生产、质量、成本、财务和审批业务服务的修改任务。
- 计划不存在 AI、向量检索、外部模型、代操作和业务数据读取任务。
- 计划不存在知识审核、发布审批、评论、工单、统计和复杂版本管理任务。
- 所有既有文件修改都有明确接入理由；页面与后端主体能力均使用新增独立文件。
- 测试和验证已经定义，但在用户明确授权前不修改、不执行。
