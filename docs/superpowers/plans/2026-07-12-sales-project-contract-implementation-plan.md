# 销售项目与合同管理基础 Implementation Plan

> **给智能体执行者：** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按工作包执行本计划。受本仓库固定五角色规则约束，实施时只复用产品经理、UI 设计师、前端开发、后端开发、测试 5 个固定角色会话，不创建第六类角色。步骤使用复选框（`- [ ]`）跟踪。

**目标：** 建立 020 销售项目与合同管理基础，让销售项目、主合同、补充合同和草稿销售订单关联形成稳定、可权限控制、可审计、可被后续阶段引用的 `projectId` 主维度。

**架构：** 后端新增独立 `salesproject` 领域，承载项目、合同、候选、权限受限摘要、状态动作、审计和并发控制；现有 `SalesAdminService` 只做销售订单项目合同字段、筛选、确认校验和审计接入。前端在销售菜单下新增销售项目工作台，销售订单表单只消费专用最小候选接口，不自行推导项目合同有效性。

**技术栈：** Java 21、Spring Boot、Spring JDBC、Flyway、PostgreSQL、JUnit/Testcontainers、Vue 3、TypeScript、Element Plus、Pinia、Vitest、Playwright 辅助浏览器验收。

## 全局约束

- 所有沟通、文档、界面文案、提交信息、议题和拉取请求说明使用中文；技术标识、命令和路径可保留原文。
- 主代理只负责统筹、计划、派发、审查汇总和交付判断，不直接实现业务代码。
- 固定角色只有产品经理、UI 设计师、前端开发、后端开发、测试；不得新增第六类角色，不反复关闭和重建角色会话。
- 规格审核和代码质量审核必须交叉进行，承担实现的角色不得自审自己的实现质量结论。
- 阶段只保留三个完整业务工作包，测试包含在工作包内，不把红测、编码、转绿、提交或审核拆成独立任务。
- 工作包 1 与工作包 3 在接口契约冻结后可并行；工作包 2 的后端部分可并入后端推进，前端部分与项目工作台并行，但必须消费同一 `salesProjectApi` 类型定义。
- 开发期只运行受影响后端测试、前端定向测试和必要类型检查；全量后端、全量前端、迁移、构建、浏览器和视觉验收统一放入唯一交付前验证窗口。
- 020 不使用 `BusinessPeriodGuard` 管控项目和合同；销售订单既有业务期间规则保持原样。
- 020 只改造销售订单项目合同关联；采购订单、生产工单、库存、成本、发票、收入确认、总账、月结和通用流程引擎不在本计划范围内。
- 历史销售订单允许 `projectId` 和 `contractId` 同时为空，不迁移回填，不根据客户、日期或金额伪造项目归属。
- 审计基于现有 `sys_audit_log`，只保证 `action`、目标、操作人、时间、`reason` 或变更字段摘要进入 `targetSummary`，不扩展审计表，不建立结构化 before/after。

---

## 文件结构

- 新建 `apps/api/src/main/resources/db/migration/V17__sales_project_contract_schema.sql`：项目、合同、销售订单兼容字段、权限数据依赖的数据库约束、索引和外键。
- 新建 `apps/api/src/main/java/com/qherp/api/system/salesproject/SalesProjectStatus.java`：项目状态枚举。
- 新建 `apps/api/src/main/java/com/qherp/api/system/salesproject/SalesProjectContractStatus.java`：合同状态枚举。
- 新建 `apps/api/src/main/java/com/qherp/api/system/salesproject/SalesProjectContractType.java`：合同类型枚举。
- 新建 `apps/api/src/main/java/com/qherp/api/system/salesproject/SalesProjectAdminController.java`：项目 CRUD、状态动作、负责人候选入口。
- 新建 `apps/api/src/main/java/com/qherp/api/system/salesproject/SalesProjectAdminService.java`：项目写入、查询、权限受限摘要、操作记录和并发控制。
- 新建 `apps/api/src/main/java/com/qherp/api/system/salesproject/SalesProjectContractAdminController.java`：合同详情、更新和状态动作入口。
- 新建 `apps/api/src/main/java/com/qherp/api/system/salesproject/SalesProjectContractService.java`：合同创建、更新、激活、关闭、终止、取消和主补合同校验。
- 新建 `apps/api/src/main/java/com/qherp/api/system/salesproject/SalesOrderProjectLinkController.java`：销售订单项目合同候选和项目关联订单列表入口。
- 新建 `apps/api/src/main/java/com/qherp/api/system/salesproject/SalesOrderProjectLinkService.java`：销售订单关联校验、候选查询、摘要、订单关联审计。
- 修改 `apps/api/src/main/java/com/qherp/api/system/sales/SalesAdminController.java`：销售订单列表增加 `projectId`、`contractId`、`projectLinked` 筛选参数。
- 修改 `apps/api/src/main/java/com/qherp/api/system/sales/SalesAdminService.java`：销售订单请求/响应增加项目合同字段，创建、更新、确认时调用关联校验和审计。
- 修改 `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`：新增项目、合同和销售订单关联错误码。
- 修改 `apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`：新增销售项目、合同和状态动作权限。
- 修改 `apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`：增加 `/api/admin/sales-projects/**` 和 `/api/admin/sales-project-contracts/**` 路径映射，候选接口按最小权限放行。
- 新建 `apps/api/src/test/java/com/qherp/api/system/salesproject/SalesProjectAdminControllerTests.java`：项目、合同、权限、审计、并发、迁移定向测试。
- 新建 `apps/api/src/test/java/com/qherp/api/system/salesproject/SalesProjectOrderLinkControllerTests.java`：销售订单项目合同候选、摘要、列表和权限定向测试。
- 修改 `apps/api/src/test/java/com/qherp/api/system/sales/SalesAdminControllerTests.java`：销售订单关联、解除、确认再校验和历史兼容测试。
- 修改 `apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`：权限初始化测试。
- 修改 `apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`：项目、合同和候选路径权限测试。
- 新建 `apps/web/src/shared/api/salesProjectApi.ts`：销售项目、合同、负责人候选、销售订单项目合同候选和关联订单摘要 API 客户端。
- 新建 `apps/web/src/shared/api/salesProjectApi.spec.ts`：销售项目 API 客户端测试。
- 修改 `apps/web/src/shared/api/salesApi.ts` 和 `apps/web/src/shared/api/salesApi.spec.ts`：销售订单项目合同请求、响应和筛选字段。
- 新建 `apps/web/src/modules/sales/projects/SalesProjectStatusTag.vue`：项目状态展示。
- 新建 `apps/web/src/modules/sales/projects/SalesProjectContractStatusTag.vue`：合同状态展示。
- 新建 `apps/web/src/modules/sales/projects/salesProjectPageHelpers.ts`：金额、日期、状态、权限受限和错误文案辅助函数。
- 新建 `apps/web/src/modules/sales/projects/SalesProjectListView.vue`：销售项目列表。
- 新建 `apps/web/src/modules/sales/projects/SalesProjectFormView.vue`：项目创建和编辑页面。
- 新建 `apps/web/src/modules/sales/projects/SalesProjectDetailView.vue`：项目详情工作台。
- 新建 `apps/web/src/modules/sales/projects/SalesProjectContractDrawer.vue`：合同抽屉，包含创建、编辑、查看和状态动作。
- 新建 `apps/web/src/modules/sales/projects/SalesProjectOperationsPanel.vue`：操作记录展示。
- 新建 `apps/web/src/modules/sales/projects/SalesProjectOrderSummaryPanel.vue`：关联订单摘要和受限态展示。
- 新建对应前端规格测试：`SalesProjectListView.spec.ts`、`SalesProjectFormView.spec.ts`、`SalesProjectDetailView.spec.ts`、`SalesProjectContractDrawer.spec.ts`。
- 修改 `apps/web/src/modules/sales/SalesOrderFormView.vue`、`SalesOrderListView.vue`、`SalesOrderDetailView.vue` 及其规格测试：草稿订单关联项目合同、列表筛选、详情回显和只读状态。
- 修改 `apps/web/src/modules/sales/salesPageHelpers.ts`：销售项目状态、合同状态和关联展示辅助函数。
- 修改 `apps/web/src/router/index.ts` 和 `apps/web/src/router/permissionGuard.spec.ts`：销售项目固定路由和 `/sales` 首个可见子页面跳转。
- 修改 `apps/web/src/App.vue` 和 `apps/web/src/App.spec.ts`：销售菜单补齐“销售项目”入口。

## 并行边界

- 工作包 1 后端项目合同能力与工作包 3 前端工作台可在 `docs/api/sales-project-contract-api.md` 冻结后并行推进。
- 工作包 1 先创建 `salesProjectApi` 所需接口形状和错误码；工作包 3 可按接口契约和 mock 响应实现页面，不等待后端全部完成。
- 工作包 2 后端订单关联可与工作包 1 同一后端角色连续推进，避免 `SalesAdminService`、迁移和错误码冲突。
- 工作包 2 前端销售订单关联与工作包 3 并行时，`apps/web/src/shared/api/salesProjectApi.ts` 由工作包 3 负责统一类型；工作包 2 只消费 `listOrderLinkCandidates`，不复制候选请求逻辑。
- 三个工作包功能完整后只进入一轮集中审查、一轮集中整改和一次差异复审；不安排实现者自审。

## 工作包 1：项目与合同后端能力

**负责人：** 后端开发。

**交叉审核：** 产品经理审规格覆盖，测试审迁移和用例覆盖，前端开发审接口字段可消费性；后端开发不自审后端实现质量结论。

**文件：**
- Create: `apps/api/src/main/resources/db/migration/V17__sales_project_contract_schema.sql`
- Create: `apps/api/src/main/java/com/qherp/api/system/salesproject/SalesProjectStatus.java`
- Create: `apps/api/src/main/java/com/qherp/api/system/salesproject/SalesProjectContractStatus.java`
- Create: `apps/api/src/main/java/com/qherp/api/system/salesproject/SalesProjectContractType.java`
- Create: `apps/api/src/main/java/com/qherp/api/system/salesproject/SalesProjectAdminController.java`
- Create: `apps/api/src/main/java/com/qherp/api/system/salesproject/SalesProjectAdminService.java`
- Create: `apps/api/src/main/java/com/qherp/api/system/salesproject/SalesProjectContractAdminController.java`
- Create: `apps/api/src/main/java/com/qherp/api/system/salesproject/SalesProjectContractService.java`
- Modify: `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- Modify: `apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- Modify: `apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`
- Test: `apps/api/src/test/java/com/qherp/api/system/salesproject/SalesProjectAdminControllerTests.java`
- Test: `apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`
- Test: `apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`

**接口与产出：**
- `GET /api/admin/sales-projects`
- `GET /api/admin/sales-projects/{id}`
- `POST /api/admin/sales-projects`
- `PUT /api/admin/sales-projects/{id}`
- `PUT /api/admin/sales-projects/{id}/activate`
- `PUT /api/admin/sales-projects/{id}/close`
- `PUT /api/admin/sales-projects/{id}/cancel`
- `GET /api/admin/sales-projects/owner-candidates`
- `GET /api/admin/sales-projects/{projectId}/contracts`
- `POST /api/admin/sales-projects/{projectId}/contracts`
- `GET /api/admin/sales-project-contracts/{id}`
- `PUT /api/admin/sales-project-contracts/{id}`
- `PUT /api/admin/sales-project-contracts/{id}/activate`
- `PUT /api/admin/sales-project-contracts/{id}/close`
- `PUT /api/admin/sales-project-contracts/{id}/terminate`
- `PUT /api/admin/sales-project-contracts/{id}/cancel`
- 数据库必须包含 `sal_project`、`sal_project_contract`、`sal_sales_order.project_id`、`sal_sales_order.contract_id`、主合同部分唯一索引、销售订单项目合同成对约束和外部纸质合同号普通索引。
- 权限必须包含 `sales:project:*` 和 `sales:contract:*`，候选接口按最小权限鉴权。
- 操作记录来自 `sys_audit_log`，`targetSummary` 包含原因或变更字段摘要。

**实现顺序：**
- [ ] 建立 `V17__sales_project_contract_schema.sql`，一次性包含项目表、合同表、销售订单兼容字段、外键、枚举检查、金额检查、日期检查、原因长度检查、主合同部分唯一索引和销售订单项目合同成对约束。
- [ ] 在 `ApiErrorCode` 增加 `PROJECT_*`、`CONTRACT_*`、`SALES_ORDER_PROJECT_*` 错误码，保持 HTTP 状态与接口契约一致。
- [ ] 在 `AccountPermissionInitializer` 初始化销售项目菜单、项目动作、合同动作权限，并分配给系统管理员。
- [ ] 在 `PermissionAuthorizationManager` 映射项目、合同、负责人候选路径；项目负责人候选只要求 `sales:project:create` 或 `sales:project:update`。
- [ ] 新增项目和合同枚举，避免在服务中散落字符串状态判断。
- [ ] 实现项目创建、列表、详情、更新、激活、关闭、取消；更新和状态动作按 `version` 校验并递增。
- [ ] 实现合同创建、列表、详情、更新、激活、关闭、终止、取消；补充合同创建和激活均校验项目 `ACTIVE` 且主合同 `EFFECTIVE`。
- [ ] 实现权限受限响应：无 `sales:contract:view` 时项目列表和详情合同字段为 `null` 或空数组，`contractSummaryRestricted=true`，不得返回合同编号、金额、状态或数量。
- [ ] 实现项目详情 `operations`：读取本项目 `SALES_PROJECT` 审计和项目下合同 `SALES_PROJECT_CONTRACT` 审计，返回 `action`、`targetType`、`targetId`、`targetSummary`、`operatorUsername`、`createdAt`。
- [ ] 增加后端定向测试，覆盖主合同唯一、补充合同项目和主合同状态、原因 1-200、外部纸质合同号重复、`ACTIVE` 项目可更新字段、`CLOSED/CANCELLED` 不可更新、合同权限受限、审计摘要和并发版本冲突。

**开发期定向验证命令：**

从仓库根执行，命令块结束后必须回到仓库根。

```powershell
$repoRoot = (Resolve-Path '.').Path
Push-Location (Join-Path $repoRoot 'apps/api')
try {
  $env:JAVA_HOME = 'C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  .\mvnw.cmd -Dtest=SalesProjectAdminControllerTests,AccountPermissionInitializerTests,PermissionAuthorizationTests,QherpApiApplicationTests test
}
finally {
  Pop-Location
}
```

**验收证据：**
- 测试输出显示 `SalesProjectAdminControllerTests`、`AccountPermissionInitializerTests`、`PermissionAuthorizationTests`、`QherpApiApplicationTests` 通过。
- 数据库迁移日志显示 `V17__sales_project_contract_schema.sql` 可在空库应用。
- 审计断言能查询到项目和合同写入、状态动作的 `targetSummary` 摘要。
- 并发版本冲突返回项目或合同并发错误码，不覆盖已有修改。

## 工作包 2：销售订单关联闭环

**负责人：** 后端开发负责订单模型、接口、校验和审计；前端开发负责销售订单表单、列表、详情关联体验；测试覆盖后端和前端定向路径。

**交叉审核：** 前端开发审后端响应可消费性，后端开发审前端请求载荷一致性但不审自己后端实现，测试审覆盖和历史兼容。

**文件：**
- Create: `apps/api/src/main/java/com/qherp/api/system/salesproject/SalesOrderProjectLinkController.java`
- Create: `apps/api/src/main/java/com/qherp/api/system/salesproject/SalesOrderProjectLinkService.java`
- Modify: `apps/api/src/main/java/com/qherp/api/system/sales/SalesAdminController.java`
- Modify: `apps/api/src/main/java/com/qherp/api/system/sales/SalesAdminService.java`
- Modify: `apps/api/src/test/java/com/qherp/api/system/sales/SalesAdminControllerTests.java`
- Test: `apps/api/src/test/java/com/qherp/api/system/salesproject/SalesProjectOrderLinkControllerTests.java`
- Modify: `apps/web/src/shared/api/salesApi.ts`
- Modify: `apps/web/src/shared/api/salesApi.spec.ts`
- Consume: `apps/web/src/shared/api/salesProjectApi.ts`
- Modify: `apps/web/src/modules/sales/SalesOrderFormView.vue`
- Modify: `apps/web/src/modules/sales/SalesOrderFormView.spec.ts`
- Modify: `apps/web/src/modules/sales/SalesOrderListView.vue`
- Modify: `apps/web/src/modules/sales/SalesOrderListView.spec.ts`
- Modify: `apps/web/src/modules/sales/SalesOrderDetailView.vue`
- Modify: `apps/web/src/modules/sales/SalesOrderDetailView.spec.ts`
- Modify: `apps/web/src/modules/sales/salesPageHelpers.ts`

**接口与产出：**
- `GET /api/admin/sales-projects/order-link-candidates`
- `GET /api/admin/sales-projects/{projectId}/sales-orders`
- `POST /api/admin/sales/orders` 请求和响应增加 `projectId`、`contractId`。
- `PUT /api/admin/sales/orders/{id}` 请求和响应增加 `projectId`、`contractId`。
- `GET /api/admin/sales/orders` 增加 `projectId`、`contractId`、`projectLinked` 筛选。
- 销售订单确认时再次校验项目 `ACTIVE`、合同 `EFFECTIVE`、合同属于项目、客户一致。
- 销售订单关联、解除或切换写 `target_type = SALES_PROJECT` 审计，摘要包含订单号及旧/新关联。
- 项目关联订单摘要受 `sales:order:view` 控制；无权限时 `salesOrderCount=null`、摘要为空或 `null`、`salesOrderSummaryRestricted=true`。

**实现顺序：**
- [ ] 后端实现 `SalesOrderProjectLinkService`，提供 `validateForDraftSave`、`validateForConfirm`、`listOrderLinkCandidates`、`listProjectSalesOrders`、`salesOrderSummary`、`recordProjectLinkAudit` 能力。
- [ ] 后端扩展 `SalesAdminService.SalesOrderRequest` 和响应 record，保存、更新、列表、详情均返回项目合同字段；历史订单字段为空时不推断项目。
- [ ] 后端扩展销售订单列表查询，`projectLinked` 与 `projectId` 或 `contractId` 同时出现返回 `VALIDATION_ERROR`。
- [ ] 后端在创建和更新草稿订单时允许关联或解除项目合同；非草稿订单仍拒绝修改项目合同关联。
- [ ] 后端在确认订单前再次校验项目和合同有效性；校验失败不得确认订单、不得预留库存、不得写错误审计。
- [ ] 后端实现 `order-link-candidates`，只返回项目 `ACTIVE` 且合同 `EFFECTIVE` 的最小组合，权限由 `sales:order:create` 或 `sales:order:update` 控制，不要求完整项目或合同查看权限。
- [ ] 后端实现项目关联订单列表和摘要，服务层叠加 `sales:order:view` 权限，避免泄露订单号、金额、客户、状态、日期或路由参数。
- [ ] 增加并发草稿订单关联测试：同一草稿订单并发关联、解除和切换项目合同时，服务层必须锁定订单并保证 `projectId` 与 `contractId` 同时写入或同时清空；如另一个事务已使订单不再是草稿，返回 `SALES_ORDER_PROJECT_IMMUTABLE`；如并发变更导致项目、合同或客户不再满足规则，返回 `SALES_ORDER_PROJECT_INVALID`、`SALES_ORDER_CONTRACT_INVALID` 或 `SALES_ORDER_PROJECT_CUSTOMER_MISMATCH`。
- [ ] 增加确认时并发变化测试：草稿订单确认过程中项目被关闭、合同被关闭/终止/取消、合同被切换项目或客户不一致时，确认必须返回稳定业务错误码，订单状态保持 `DRAFT`，不产生库存预留、出库、错误审计或 `projectId`/`contractId` 半写。
- [ ] 前端扩展 `salesApi.ts` 请求、响应和筛选类型；列表增加项目合同筛选展示，禁止含糊筛选组合提交。
- [ ] 前端销售订单草稿表单增加项目合同联动选择，使用 `salesProjectApi.listOrderLinkCandidates`，项目和合同必须同选同清。
- [ ] 前端销售订单详情和非草稿编辑态只读展示项目合同，已确认、部分出库、全部出库、已关闭、已取消状态不可解除或切换。
- [ ] 增加后端定向测试，覆盖成对字段、客户不一致、项目非 `ACTIVE`、合同非 `EFFECTIVE`、合同不属于项目、确认再校验、历史订单空关联、并发关联/解除/切换、确认时项目或合同并发变化、关联审计和事务回滚。
- [ ] 增加前端定向测试，覆盖候选权限、草稿联动选择、只读态、列表筛选互斥、详情回显和错误提示。

**开发期定向验证命令：**

从仓库根执行，命令块结束后必须回到仓库根。

```powershell
$repoRoot = (Resolve-Path '.').Path
Push-Location (Join-Path $repoRoot 'apps/api')
try {
  $env:JAVA_HOME = 'C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  .\mvnw.cmd -Dtest=SalesAdminControllerTests,SalesProjectOrderLinkControllerTests,PermissionAuthorizationTests test
}
finally {
  Pop-Location
}
```

```powershell
$repoRoot = (Resolve-Path '.').Path
$webRoot = Join-Path $repoRoot 'apps/web'
npm --prefix $webRoot test -- salesApi.spec.ts salesProjectApi.spec.ts SalesOrderFormView.spec.ts SalesOrderListView.spec.ts SalesOrderDetailView.spec.ts
npm --prefix $webRoot run typecheck
```

**验收证据：**
- 后端测试证明销售订单成对字段、确认再校验、并发关联/解除/切换、确认时项目或合同并发变化、历史兼容、候选权限、订单摘要受限和项目关闭终态数据可被服务层正确消费。
- 并发失败路径必须断言订单状态、项目合同字段、库存预留、出库和审计均无部分写入。
- 前端测试证明销售订单草稿可选项目合同，非草稿只读，`projectLinked` 与项目或合同筛选互斥，错误信息可见。
- 审计断言证明关联、解除、切换项目合同时目标为 `SALES_PROJECT`，摘要包含订单号及旧/新关联。

## 工作包 3：项目合同前端工作台

**负责人：** 前端开发。

**交叉审核：** UI 设计师审页面结构、权限态、窄屏和抽屉体验；后端开发审接口消费一致性但不审自己编写的 API 契约；测试审前端覆盖和浏览器路径。

**文件：**
- Create: `apps/web/src/shared/api/salesProjectApi.ts`
- Create: `apps/web/src/shared/api/salesProjectApi.spec.ts`
- Create: `apps/web/src/modules/sales/projects/SalesProjectStatusTag.vue`
- Create: `apps/web/src/modules/sales/projects/SalesProjectContractStatusTag.vue`
- Create: `apps/web/src/modules/sales/projects/salesProjectPageHelpers.ts`
- Create: `apps/web/src/modules/sales/projects/SalesProjectListView.vue`
- Create: `apps/web/src/modules/sales/projects/SalesProjectListView.spec.ts`
- Create: `apps/web/src/modules/sales/projects/SalesProjectFormView.vue`
- Create: `apps/web/src/modules/sales/projects/SalesProjectFormView.spec.ts`
- Create: `apps/web/src/modules/sales/projects/SalesProjectDetailView.vue`
- Create: `apps/web/src/modules/sales/projects/SalesProjectDetailView.spec.ts`
- Create: `apps/web/src/modules/sales/projects/SalesProjectContractDrawer.vue`
- Create: `apps/web/src/modules/sales/projects/SalesProjectContractDrawer.spec.ts`
- Create: `apps/web/src/modules/sales/projects/SalesProjectOperationsPanel.vue`
- Create: `apps/web/src/modules/sales/projects/SalesProjectOrderSummaryPanel.vue`
- Modify: `apps/web/src/router/index.ts`
- Modify: `apps/web/src/router/permissionGuard.spec.ts`
- Modify: `apps/web/src/App.vue`
- Modify: `apps/web/src/App.spec.ts`

**接口与产出：**
- `salesProjectApi.ts` 覆盖项目、合同、负责人候选、销售订单项目合同候选、关联订单列表和状态动作。
- 固定路由：`/sales/projects`、`/sales/projects/create`、`/sales/projects/:id`、`/sales/projects/:id/edit`。
- `/sales` 根路径进入当前用户第一个可见销售子页面，顺序按销售项目、销售订单、销售出库、销售退货中当前用户可见项计算。
- 销售菜单补齐“销售项目”，权限为 `sales:project:view`。
- 项目列表支持关键词、客户、负责人、状态、计划日期筛选，合同和订单受限态不显示为 0。
- 项目表单区分创建和更新 payload，更新 payload 必须携带 `version`。
- 项目详情包含基础信息、合同摘要、合同抽屉、关联订单摘要、操作记录、状态动作和权限受限态。
- 合同抽屉区分摘要和详情字段，状态动作使用 `version`，终态操作弹出 1-200 字原因确认。

**实现顺序：**
- [ ] 实现 `salesProjectApi.ts` 的类型和方法，字段名与接口契约一致，金额以字符串传递，状态动作统一携带 `version`。
- [ ] 实现销售项目路由和 `/sales` 首个可见子页面跳转，补齐 `permissionGuard.spec.ts`。
- [ ] 更新 `App.vue` 销售菜单补齐逻辑，加入“销售项目”入口并覆盖后端菜单缺失、无权限和混合权限场景。
- [ ] 实现项目列表，展示项目编号、名称、客户、负责人、状态、目标收入、目标成本、计划周期、合同受限标记、订单受限标记和行操作。
- [ ] 实现项目创建和编辑页面，创建可选客户和负责人，编辑锁定客户和项目编号，`ACTIVE` 只开放负责人、计划日期、目标收入、目标成本和备注。
- [ ] 实现项目详情工作台，基础信息、合同、关联订单摘要和操作记录分区展示。
- [ ] 实现合同抽屉，支持主合同草稿、补充合同草稿、编辑草稿、激活、关闭、终止、取消；补充合同金额允许正负但不得为 0。
- [ ] 实现原因确认弹窗，项目关闭、项目取消、合同关闭、合同终止、合同取消均要求 1-200 字原因，错误区域固定可见。
- [ ] 实现权限受限态：无 `sales:contract:view` 时合同编号、金额、状态和数量不可见；无 `sales:order:view` 时订单数量和摘要为空或 `null` 展示为受限态。
- [ ] 实现 390px 窄屏布局，项目和合同表格横向滚动，状态动作按钮、抽屉提交按钮和错误信息可达。
- [ ] 增加前端定向测试，覆盖 API 客户端、路由、菜单、列表筛选、创建、编辑、详情、合同抽屉、状态动作、权限受限态、空态和窄屏关键类名。

**开发期定向验证命令：**

从仓库根执行。

```powershell
$repoRoot = (Resolve-Path '.').Path
$webRoot = Join-Path $repoRoot 'apps/web'
npm --prefix $webRoot test -- salesProjectApi.spec.ts SalesProjectListView.spec.ts SalesProjectFormView.spec.ts SalesProjectDetailView.spec.ts SalesProjectContractDrawer.spec.ts App.spec.ts permissionGuard.spec.ts
npm --prefix $webRoot run typecheck
```

**验收证据：**
- 前端测试证明销售项目路由、菜单、API 客户端、表单、详情、合同抽屉、权限受限态和窄屏关键状态均可用。
- 类型检查证明项目、合同、状态动作、候选接口和销售订单关联字段类型一致。
- 浏览器验收窗口中保存 `/sales/projects`、创建页、详情页、合同抽屉、原因弹窗、合同受限态、订单受限态、只读用户、无权限用户和 390px 权限态截图。

## 唯一交付前验证窗口

以下命令只在三个工作包功能完整、集中整改完成并准备阶段交付前执行。所有 PowerShell 命令块均从仓库根执行，进入子目录的命令必须使用 `Push-Location`/`Pop-Location` 恢复目录。

```powershell
$repoRoot = (Resolve-Path '.').Path
Push-Location (Join-Path $repoRoot 'apps/api')
try {
  $env:JAVA_HOME = 'C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  .\mvnw.cmd test
}
finally {
  Pop-Location
}
```

```powershell
$repoRoot = (Resolve-Path '.').Path
$webRoot = Join-Path $repoRoot 'apps/web'
npm --prefix $webRoot test
npm --prefix $webRoot run typecheck
npm --prefix $webRoot run build
git diff --check
```

空库迁移验证使用当前 `compose.yaml` 的 PostgreSQL 服务和默认数据库账号，创建独立验证库 `qherp_020_empty`，按迁移序号应用全部 SQL：

```powershell
$repoRoot = (Resolve-Path '.').Path
docker compose --project-directory $repoRoot up -d postgres
$env:PGPASSWORD = 'qherp_dev_password'
docker compose --project-directory $repoRoot exec -T postgres psql -U qherp -d postgres -v ON_ERROR_STOP=1 -c "drop database if exists qherp_020_empty with (force);" -c "create database qherp_020_empty owner qherp;"

$migrationDir = Join-Path $repoRoot 'apps/api/src/main/resources/db/migration'
Get-ChildItem $migrationDir -Filter 'V*.sql' |
  Sort-Object { [int]([regex]::Match($_.BaseName, '^V(\d+)__').Groups[1].Value) } |
  ForEach-Object {
    Get-Content -Raw $_.FullName |
      docker compose --project-directory $repoRoot exec -T postgres psql -U qherp -d qherp_020_empty -v ON_ERROR_STOP=1
  }

@'
select count(*) as project_count from sal_project;
select count(*) as contract_count from sal_project_contract;
select count(*) as sales_order_count from sal_sales_order;
'@ | docker compose --project-directory $repoRoot exec -T postgres psql -U qherp -d qherp_020_empty -v ON_ERROR_STOP=1
```

019 存量库升级到 `V17` 验证使用独立验证库 `qherp_020_v16_upgrade`：先应用 `V1` 至 `V16`，插入一条历史无项目销售订单，再应用 `V17` 并执行兼容断言。

```powershell
$repoRoot = (Resolve-Path '.').Path
docker compose --project-directory $repoRoot up -d postgres
$env:PGPASSWORD = 'qherp_dev_password'
docker compose --project-directory $repoRoot exec -T postgres psql -U qherp -d postgres -v ON_ERROR_STOP=1 -c "drop database if exists qherp_020_v16_upgrade with (force);" -c "create database qherp_020_v16_upgrade owner qherp;"

$migrationDir = Join-Path $repoRoot 'apps/api/src/main/resources/db/migration'
$migrations = Get-ChildItem $migrationDir -Filter 'V*.sql' | Sort-Object { [int]([regex]::Match($_.BaseName, '^V(\d+)__').Groups[1].Value) }
$migrations |
  Where-Object { [int]([regex]::Match($_.BaseName, '^V(\d+)__').Groups[1].Value) -le 16 } |
  ForEach-Object {
    Get-Content -Raw $_.FullName |
      docker compose --project-directory $repoRoot exec -T postgres psql -U qherp -d qherp_020_v16_upgrade -v ON_ERROR_STOP=1
  }

@'
insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
values ('C020-HISTORY', '020 历史客户', 'ENABLED', 'migration-check', now(), 'migration-check', now());

insert into sal_sales_order (order_no, customer_id, order_date, status, created_by, created_at, updated_by, updated_at)
select 'SO-020-HISTORY', id, current_date, 'DRAFT', 'migration-check', now(), 'migration-check', now()
from mst_customer
where code = 'C020-HISTORY';
'@ | docker compose --project-directory $repoRoot exec -T postgres psql -U qherp -d qherp_020_v16_upgrade -v ON_ERROR_STOP=1

$migrations |
  Where-Object { [int]([regex]::Match($_.BaseName, '^V(\d+)__').Groups[1].Value) -eq 17 } |
  ForEach-Object {
    Get-Content -Raw $_.FullName |
      docker compose --project-directory $repoRoot exec -T postgres psql -U qherp -d qherp_020_v16_upgrade -v ON_ERROR_STOP=1
  }

@'
do $$
begin
  if exists (select 1 from sal_sales_order where project_id is not null or contract_id is not null) then
    raise exception '历史销售订单 project_id/contract_id 必须保持为空';
  end if;

  if exists (
    select 1
    from information_schema.columns
    where table_schema = 'public'
      and table_name in ('proc_purchase_order', 'proc_purchase_order_line', 'mfg_work_order', 'mfg_work_order_material')
      and column_name in ('project_id', 'contract_id')
  ) then
    raise exception '020 不得向采购单或生产工单增加项目字段';
  end if;
end $$;

select count(*) as historical_orders from sal_sales_order where project_id is null and contract_id is null;
select count(*) as sales_shipment_query_ok from sal_sales_shipment sh join sal_sales_order o on o.id = sh.order_id;
select count(*) as finance_receivable_query_ok from fin_receivable;
select count(*) as finance_payable_query_ok from fin_payable;
select count(*) as reporting_sales_query_ok from sal_sales_order o join mst_customer c on c.id = o.customer_id;
select count(*) as trace_batch_query_ok from inv_batch;
select count(*) as trace_serial_query_ok from inv_serial;
select count(*) as trace_allocation_query_ok from inv_stock_tracking_allocation;
'@ | docker compose --project-directory $repoRoot exec -T postgres psql -U qherp -d qherp_020_v16_upgrade -v ON_ERROR_STOP=1
```

```powershell
$repoRoot = (Resolve-Path '.').Path
docker compose --project-directory $repoRoot up -d postgres
Push-Location (Join-Path $repoRoot 'apps/api')
try {
  $env:JAVA_HOME = 'C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  .\mvnw.cmd -DskipTests spring-boot:run
}
finally {
  Pop-Location
}
```

前端浏览器验收另开终端：

```powershell
$repoRoot = (Resolve-Path '.').Path
$webRoot = Join-Path $repoRoot 'apps/web'
npm --prefix $webRoot run dev -- --host 0.0.0.0 --port 5173
```

浏览器访问：

```text
http://127.0.0.1:5173
```

交付前必须形成以下证据：
- 后端全量测试通过。
- 前端全量测试、类型检查、生产构建通过。
- 空库迁移可应用；019 存量库升级到 `V17` 后历史 `sal_sales_order.project_id` 和 `contract_id` 均为空，采购单和生产工单未增加项目字段，既有销售出库、往来、报表、追踪关键查询仍可执行。
- 浏览器主路径可操作：创建项目、创建并激活主合同、激活项目、创建补充合同、草稿销售订单关联项目合同、项目详情查看合同和订单摘要。
- 浏览器异常路径可操作：无主合同激活项目失败、补充合同项目或主合同状态不满足失败、原因缺失失败、版本冲突失败、销售订单关联校验失败、`projectLinked` 互斥筛选失败。
- 浏览器历史兼容路径可操作：历史无项目销售订单可查询，既有草稿确认、销售出库创建/过账、来源追溯、往来和报表入口不因项目字段为空失败。
- 浏览器权限路径可操作：合同受限态、订单受限态、只读用户、无项目权限用户、无合同权限用户、无订单查看权限用户，以及 390px 权限态均不泄露编号、金额、状态、数量或订单明细。
- 视觉分析目录 `docs/testing/020-sales-project-contract-visual-audit/` 包含桌面和必要 390px 截图及结论，截图清单合并覆盖：项目列表、项目创建、项目编辑、项目详情、合同抽屉、原因弹窗、合同受限态、订单受限态、空项目列表、项目详情无合同、项目详情无关联订单、合同抽屉保存错误、原因缺失错误、列表加载失败和详情加载失败。

## 阶段集中审查

全部工作包功能完整后，只组织一轮阶段集中审查：

- 产品经理：核对 020 任务记录、设计规格和业务验收矩阵，确认范围没有缩水或越界。
- UI 设计师：审查销售项目列表、项目表单、详情、合同抽屉、原因弹窗、权限受限态、空态、异常态和 390px 视觉证据。
- 后端开发：审查前端接口消费、请求载荷、权限态处理和类型一致性，不审自己实现的后端代码。
- 前端开发：审查后端接口行为、错误码、字段稳定性和权限受限响应，不审自己实现的前端代码。
- 测试：审查覆盖、异常路径、历史兼容、权限、审计、并发、迁移和回归风险。

主代理合并去重审查意见，一次性交给对应角色整改。阻断和严重问题必须修复；一般问题默认进入后续清单，除非影响当前验收或用户明确要求，否则不反复阻断阶段交付。

## 计划自检清单

- [x] 工作包总数固定为 3 个，且均按完整业务能力划分。
- [x] 每个工作包均包含准确文件路径、接口或产出、实现顺序、受影响测试命令和验收证据。
- [x] 工作包 1 与工作包 3 的并行边界明确，工作包 2 的后端和前端依赖明确。
- [x] 开发期只安排定向测试和必要类型检查，全量验证统一放入唯一交付前窗口。
- [x] 计划未新增第六类角色，未安排实现者自审，未设计重复审核返工循环。
- [x] 计划未扩大到采购、生产、库存计价、成本核算、发票、收入确认、总账、月结或通用流程引擎。
- [x] 计划未要求修改当前工作区已有的无关文档改动。
