# 成本归集基础记录实施计划

> **面向代理执行者：** 必须使用 `superpowers:subagent-driven-development` 或 `superpowers:executing-plans` 按任务逐项实施。步骤使用复选框语法跟踪，实施过程中严格对照 `docs/tasks/009-cost-collection-foundation.md`、`docs/api/cost-collection-api.md`、`docs/testing/cost-collection-test-plan.md` 和本计划，不得自行扩大范围。

**目标：** 实现成本业务记录、自动来源记录、手工记录和工单成本追溯，使生产执行结果可归集、可查询、可鉴权、可审计。

**架构：** 后端新增独立成本模块，生产领料和报工过账在同一事务内调用成本写入组件生成自动来源记录，完工入库作为工单成本汇总的产出追溯。前端新增成本管理模块，提供成本记录列表、详情、手工记录表单，并在生产工单详情展示成本归集分区。

**技术栈：** Java 21、Spring Boot、JdbcTemplate、PostgreSQL、Flyway、JUnit、Testcontainers、Vue 3、TypeScript、Vitest、Element Plus、Playwright / Chromium 浏览器视觉验收。

---

## 文件结构

### 后端新增文件

- `apps/api/src/main/resources/db/migration/V7__cost_collection_schema.sql`：成本记录表结构、约束和索引。
- `apps/api/src/main/java/com/qherp/api/system/cost/CostType.java`：成本类型枚举。
- `apps/api/src/main/java/com/qherp/api/system/cost/CostSourceType.java`：来源类型枚举。
- `apps/api/src/main/java/com/qherp/api/system/cost/CostSourceDocumentType.java`：来源单据类型枚举。
- `apps/api/src/main/java/com/qherp/api/system/cost/CostBasisType.java`：口径类型枚举。
- `apps/api/src/main/java/com/qherp/api/system/cost/CostRecordStatus.java`：成本记录状态枚举。
- `apps/api/src/main/java/com/qherp/api/system/cost/CostAdminController.java`：成本管理接口入口。
- `apps/api/src/main/java/com/qherp/api/system/cost/CostAdminService.java`：成本记录列表、详情、手工记录和工单成本汇总。
- `apps/api/src/main/java/com/qherp/api/system/cost/CostRecordWriter.java`：生产过账事务调用的自动来源记录写入组件。
- `apps/api/src/test/java/com/qherp/api/system/cost/CostAdminControllerTests.java`：成本归集后端集成测试。

### 后端修改文件

- `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`：增加成本错误码。
- `apps/api/src/main/java/com/qherp/api/system/production/ProductionAdminService.java`：在领料、报工、完工入库过账点调用成本写入或追溯组件。
- `apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`：增加成本菜单和权限种子。
- `apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`：增加成本接口权限映射。
- `apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`：补成本权限种子断言。
- `apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`：补成本接口鉴权断言。

### 前端新增文件

- `apps/web/src/shared/api/costCollectionApi.ts`：成本归集 API 类型与请求封装。
- `apps/web/src/shared/api/costCollectionApi.spec.ts`：成本归集 API 封装测试。
- `apps/web/src/modules/cost/costPageHelpers.ts`：成本类型、来源类型、口径、金额和错误文案辅助函数。
- `apps/web/src/modules/cost/CostTypeTag.vue`：成本类型标签。
- `apps/web/src/modules/cost/CostSourceTypeTag.vue`：来源类型标签。
- `apps/web/src/modules/cost/CostRecordListView.vue`：成本记录列表页。
- `apps/web/src/modules/cost/CostRecordDetailView.vue`：成本记录详情页。
- `apps/web/src/modules/cost/CostRecordFormView.vue`：手工成本记录新建和编辑表单页。
- `apps/web/src/modules/cost/CostRecordListView.spec.ts`：成本记录列表测试。
- `apps/web/src/modules/cost/CostRecordDetailView.spec.ts`：成本记录详情测试。
- `apps/web/src/modules/cost/CostRecordFormView.spec.ts`：手工成本记录表单测试。

### 前端修改文件

- `apps/web/src/router/index.ts`：增加成本路由和权限元信息。
- `apps/web/src/router/permissionGuard.spec.ts`：增加成本路由守卫断言。
- `apps/web/src/App.vue`：增加成本管理菜单入口。
- `apps/web/src/modules/production/ProductionWorkOrderDetailView.vue`：增加成本归集记录分区。
- `apps/web/src/modules/production/ProductionWorkOrderDetailView.spec.ts`：补成本分区测试。

### 文档与验收文件

- `docs/testing/cost-collection-visual-audit/notes.md`：浏览器视觉分析记录。
- `docs/testing/cost-collection-visual-audit/*.png`：浏览器截图证据。
- `docs/tasks/009-cost-collection-foundation.md`：阶段实现后追加执行记录。
- `docs/testing/cost-collection-test-plan.md`：阶段实现后追加执行记录。

## 实施约束

- 严格按成本归集任务文档、接口契约、测试计划和本计划执行。
- 本阶段只实现成本业务记录和来源追溯。
- 不实现正式财务成本核算、总账、凭证、标准成本、移动加权、成本差异、制造费用自动分配、采购销售联动、多组织、多公司、多租户或 BI 报表。
- 自动材料和人工记录只做数量来源口径，不自动计算金额。
- 完工入库只作为产出数量追溯，不作为成本金额来源。
- 所有写接口必须后端鉴权，前端按钮隐藏不能替代接口鉴权。
- 浏览器验收阶段必须引入视觉分析。没有截图证据、没有分析结论、截图失真未复拍、视觉问题影响核心操作或数据识别时，不得进入主分支阶段验收。

## 任务 1：后端迁移、枚举、错误码和权限

**角色：后端开发。**

**文件：**

- 创建：`apps/api/src/main/resources/db/migration/V7__cost_collection_schema.sql`
- 创建：`apps/api/src/main/java/com/qherp/api/system/cost/*.java`
- 修改：`apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- 修改：`apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`

- [ ] **步骤 1：编写失败测试**

  - 增加成本权限种子断言：`cost`、`cost:record:view`、`cost:record:create`、`cost:record:update`。
  - 增加成本接口权限映射断言：列表和详情需要查看权限，创建需要创建权限，更新需要更新权限。

- [ ] **步骤 2：运行定向测试确认失败**

  ```powershell
  cd apps/api
  .\mvnw.cmd -q -Dtest=AccountPermissionInitializerTests,PermissionAuthorizationTests test
  ```

  预期：因成本权限和映射尚未实现而失败。

- [ ] **步骤 3：实现数据库迁移和枚举**

  - 创建 `mfg_cost_record`。
  - 增加成本类型、来源类型、来源单据类型、口径类型和状态约束。
  - 增加自动来源唯一索引。
  - 增加工单、产品、来源单据、业务日期和成本类型查询索引。

- [ ] **步骤 4：实现错误码、权限种子和鉴权映射**

  - 增加 `COST_RECORD_NOT_FOUND`、`COST_WORK_ORDER_NOT_FOUND`、`COST_WORK_ORDER_STATUS_INVALID`、`COST_SOURCE_DOCUMENT_NOT_FOUND`、`COST_SOURCE_DOCUMENT_STATUS_INVALID`、`COST_SOURCE_DUPLICATED`、`COST_TYPE_INVALID`、`COST_BASIS_INVALID`、`COST_QUANTITY_INVALID`、`COST_AMOUNT_INVALID`、`COST_GENERATED_RECORD_IMMUTABLE`。
  - 权限种子排序放在生产之后。
  - `/api/admin/cost/records` 读写接口按契约映射权限。

- [ ] **步骤 5：运行定向测试确认通过**

  ```powershell
  cd apps/api
  .\mvnw.cmd -q -Dtest=AccountPermissionInitializerTests,PermissionAuthorizationTests test
  ```

- [ ] **步骤 6：提交任务 1**

  ```powershell
  git add apps/api/src/main/resources/db/migration/V7__cost_collection_schema.sql apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java apps/api/src/main/java/com/qherp/api/system/cost apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java
  git commit -m "建立成本归集后端基础结构"
  ```

## 任务 2：后端成本记录服务和自动来源写入

**角色：后端开发。**

**文件：**

- 创建：`apps/api/src/main/java/com/qherp/api/system/cost/CostAdminController.java`
- 创建：`apps/api/src/main/java/com/qherp/api/system/cost/CostAdminService.java`
- 创建：`apps/api/src/main/java/com/qherp/api/system/cost/CostRecordWriter.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/production/ProductionAdminService.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/cost/CostAdminControllerTests.java`

- [ ] **步骤 1：编写失败测试**

  - 领料过账生成材料成本记录。
  - 报工过账生成人工数量口径记录。
  - 完工入库在工单成本汇总中可追溯。
  - 手工记录创建成功。
  - 重复来源返回 `COST_SOURCE_DUPLICATED`。
  - 自动生成记录不可修改来源字段。

- [ ] **步骤 2：运行成本后端测试确认失败**

  ```powershell
  cd apps/api
  .\mvnw.cmd -q -Dtest=CostAdminControllerTests test
  ```

- [ ] **步骤 3：实现成本查询、详情、手工记录和工单汇总**

  - 列表支持接口契约中的筛选字段。
  - 详情返回工单、产品、来源单据、金额或数量口径、记录人和记录时间。
  - 手工记录校验金额、数量、口径和工单状态。
  - 工单成本汇总返回成本记录列表、金额口径汇总和完工入库产出追溯。

- [ ] **步骤 4：实现自动来源写入**

  - 领料过账同事务写入材料来源记录。
  - 报工过账同事务写入人工数量记录。
  - 自动记录使用生产过账操作人作为记录人。
  - 自动写入失败必须回滚生产过账。

- [ ] **步骤 5：补审计**

  - 手工创建写 `MFG_COST_RECORD_CREATE`。
  - 手工更新写 `MFG_COST_RECORD_UPDATE`。
  - 自动生成写 `MFG_COST_RECORD_AUTO_CREATE`。

- [ ] **步骤 6：运行成本后端测试确认通过**

  ```powershell
  cd apps/api
  .\mvnw.cmd -q -Dtest=CostAdminControllerTests test
  ```

- [ ] **步骤 7：提交任务 2**

  ```powershell
  git add apps/api/src/main/java/com/qherp/api/system/cost apps/api/src/main/java/com/qherp/api/system/production/ProductionAdminService.java apps/api/src/test/java/com/qherp/api/system/cost/CostAdminControllerTests.java
  git commit -m "实现成本归集后端服务"
  ```

## 任务 3：前端 API、路由和菜单

**角色：前端开发。**

**文件：**

- 创建：`apps/web/src/shared/api/costCollectionApi.ts`
- 创建：`apps/web/src/shared/api/costCollectionApi.spec.ts`
- 修改：`apps/web/src/router/index.ts`
- 修改：`apps/web/src/router/permissionGuard.spec.ts`
- 修改：`apps/web/src/App.vue`

- [ ] **步骤 1：编写失败测试**

  - API 查询参数构造。
  - 详情路径。
  - 创建和更新写接口先取 CSRF。
  - 成本路由守卫权限。
  - 成本菜单按权限显示。

- [ ] **步骤 2：运行前端定向测试确认失败**

  ```powershell
  cd apps/web
  npm test -- costCollectionApi permissionGuard App
  ```

- [ ] **步骤 3：实现成本 API 封装**

  - 定义成本类型、来源类型、口径类型、记录状态。
  - 实现 `records.list`、`records.get`、`records.create`、`records.update`、`workOrders.summary`。
  - 写接口沿用 CSRF 和 `AccountPermissionApiError` 处理。

- [ ] **步骤 4：实现路由和菜单**

  - 增加 `/cost/records`、`/cost/records/create`、`/cost/records/:id`、`/cost/records/:id/edit`。
  - 增加 `cost:record:view`、`cost:record:create`、`cost:record:update` 权限元信息。
  - `App.vue` 增加成本管理菜单补齐逻辑和 `supportedMenuPaths`。

- [ ] **步骤 5：运行前端定向测试确认通过**

  ```powershell
  cd apps/web
  npm test -- costCollectionApi permissionGuard App
  ```

- [ ] **步骤 6：提交任务 3**

  ```powershell
  git add apps/web/src/shared/api/costCollectionApi.ts apps/web/src/shared/api/costCollectionApi.spec.ts apps/web/src/router/index.ts apps/web/src/router/permissionGuard.spec.ts apps/web/src/App.vue
  git commit -m "接入成本归集前端路由与接口"
  ```

## 任务 4：前端成本页面和工单成本分区

**角色：前端开发。**

**文件：**

- 创建：`apps/web/src/modules/cost/costPageHelpers.ts`
- 创建：`apps/web/src/modules/cost/CostTypeTag.vue`
- 创建：`apps/web/src/modules/cost/CostSourceTypeTag.vue`
- 创建：`apps/web/src/modules/cost/CostRecordListView.vue`
- 创建：`apps/web/src/modules/cost/CostRecordDetailView.vue`
- 创建：`apps/web/src/modules/cost/CostRecordFormView.vue`
- 修改：`apps/web/src/modules/production/ProductionWorkOrderDetailView.vue`

- [ ] **步骤 1：实现页面辅助和标签**

  - 成本类型中文化。
  - 来源类型中文化。
  - 口径类型中文化。
  - 金额、数量格式化。
  - 后端业务错误展示。

- [ ] **步骤 2：实现成本记录列表**

  - 筛选：关键词、工单号、产品、成本类型、来源类型、来源单号、日期范围。
  - 表格：记录编号、成本类型、来源类型、来源单据、工单、产品、数量、金额、业务日期、记录人、记录时间、状态、操作。
  - 加载、错误、空状态和分页。

- [ ] **步骤 3：实现成本记录详情**

  - 成本摘要、关联对象、来源追溯、审计信息。
  - 提供返回生产工单详情的入口。
  - 无来源权限或来源缺失时显示明确提示。

- [ ] **步骤 4：实现手工成本记录表单**

  - 工单、成本类型、口径类型、业务日期、数量、单位、单价、金额、来源说明和备注。
  - 新建模式必须关联有效生产工单，产品从工单带出。
  - 编辑模式只允许手工记录进入，不允许修改自动记录来源字段。
  - 校验金额和数量。
  - 保存失败保留输入并显示错误。

- [ ] **步骤 5：实现生产工单详情成本分区**

  - 调用工单成本汇总接口。
  - 展示成本记录摘要和完工产出追溯。
  - 只读用户不显示写入口。

- [ ] **步骤 6：运行前端类型检查**

  ```powershell
  cd apps/web
  npm run typecheck
  ```

- [ ] **步骤 7：提交任务 4**

  ```powershell
  git add apps/web/src/modules/cost apps/web/src/modules/production/ProductionWorkOrderDetailView.vue
  git commit -m "实现成本归集前端页面"
  ```

## 任务 5：前后端测试补齐

**角色：测试。**

**文件：**

- 创建或修改：`apps/web/src/modules/cost/*.spec.ts`
- 修改：`apps/web/src/modules/production/ProductionWorkOrderDetailView.spec.ts`
- 修改：`apps/api/src/test/java/com/qherp/api/system/cost/CostAdminControllerTests.java`

- [ ] **步骤 1：补后端异常和权限测试**

  - 无效工单、已取消工单、草稿来源、重复来源。
  - 金额和数量非法。
  - 手工记录编辑成功和自动来源记录不可编辑。
  - 只读和无权限接口拒绝。
  - 成本管理角色可维护成本记录但不能执行无关生产和库存写操作。
  - 成本记录不改变库存和生产状态。

- [ ] **步骤 2：补前端页面测试**

  - 列表筛选、重置、分页、空状态、错误状态。
  - 详情展示和追溯入口。
  - 表单校验、保存失败、防重复提交。
  - 工单成本分区和只读权限。

- [ ] **步骤 3：运行定向测试**

  ```powershell
  cd apps/api
  .\mvnw.cmd -q -Dtest=CostAdminControllerTests test
  cd ..\web
  npm test -- Cost costCollectionApi ProductionWorkOrderDetailView
  ```

- [ ] **步骤 4：提交任务 5**

  ```powershell
  git add apps/api/src/test/java/com/qherp/api/system/cost/CostAdminControllerTests.java apps/web/src/modules/cost apps/web/src/modules/production/ProductionWorkOrderDetailView.spec.ts
  git commit -m "覆盖成本归集自动化测试"
  ```

## 任务 6：全量验证和代码质量检查

**角色：测试。**

**文件：**

- 修改：`docs/tasks/009-cost-collection-foundation.md`
- 修改：`docs/testing/cost-collection-test-plan.md`

- [ ] **步骤 1：运行后端全量测试**

  ```powershell
  docker run --rm -e TESTCONTAINERS_RYUK_DISABLED=true -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -v "${PWD}:/workspace" -v /var/run/docker.sock:/var/run/docker.sock -v qherp-maven-repo:/root/.m2 -w /workspace/apps/api maven:3.9-eclipse-temurin-21 mvn -q test
  ```

- [ ] **步骤 2：确认 Testcontainers 无遗留容器**

  ```powershell
  docker ps -a --filter "label=org.testcontainers" --format "{{.ID}} {{.Image}} {{.Status}}"
  ```

- [ ] **步骤 3：运行前端全量测试**

  ```powershell
  cd apps/web
  npm test
  ```

- [ ] **步骤 4：运行前端构建**

  ```powershell
  cd apps/web
  npm run build
  ```

- [ ] **步骤 5：运行空白检查**

  ```powershell
  git diff --check
  ```

- [ ] **步骤 6：更新执行记录并提交**

  ```powershell
  git add docs/tasks/009-cost-collection-foundation.md docs/testing/cost-collection-test-plan.md
  git commit -m "记录成本归集自动化验证"
  ```

## 任务 7：本地部署和浏览器功能验收

**角色：测试。**

**文件：**

- 修改：`docs/tasks/009-cost-collection-foundation.md`
- 修改：`docs/testing/cost-collection-test-plan.md`

- [ ] **步骤 1：启动或确认 PostgreSQL、后端和前端**

  - PostgreSQL 必须 healthy。
  - 后端健康检查必须返回 `UP`。
  - 前端成本记录页面必须返回 HTTP 200。

- [ ] **步骤 2：浏览器验收管理员主路径**

  - 创建并发布生产工单。
  - 过账生产领料，确认材料成本来源。
  - 过账生产报工，确认人工数量口径。
  - 过账完工入库，确认产出追溯。
  - 查询成本记录列表。
  - 查看成本记录详情。
  - 从成本记录追溯回生产工单。
  - 从生产工单详情进入成本记录。
  - 手工录入制造费用或其他成本。
  - 编辑该手工成本记录，确认金额、数量、业务日期和说明可受控更新。

- [ ] **步骤 3：浏览器验收异常和权限**

  - 重复来源。
  - 非法金额和非法数量。
  - 无效工单。
  - 成本管理角色。
  - 只读用户。
  - 无权限用户。

- [ ] **步骤 4：更新执行记录并提交**

  ```powershell
  git add docs/tasks/009-cost-collection-foundation.md docs/testing/cost-collection-test-plan.md
  git commit -m "记录成本归集浏览器功能验收"
  ```

## 任务 8：浏览器视觉分析与阶段验收准备

**角色：测试。**

**文件：**

- 创建：`docs/testing/cost-collection-visual-audit/notes.md`
- 创建：`docs/testing/cost-collection-visual-audit/*.png`
- 修改：`docs/tasks/009-cost-collection-foundation.md`
- 修改：`docs/testing/cost-collection-test-plan.md`

- [ ] **步骤 1：建立视觉分析目录**

  ```powershell
  New-Item -ItemType Directory -Force docs/testing/cost-collection-visual-audit
  ```

- [ ] **步骤 2：采集桌面截图**

  - `01-cost-record-list-desktop.png`
  - `02-cost-record-query-result-desktop.png`
  - `03-cost-record-empty-desktop.png`
  - `04-cost-record-form-desktop.png`
  - `05-cost-record-form-error-desktop.png`
  - `06-cost-record-duplicate-error-desktop.png`
  - `07-cost-record-detail-desktop.png`
  - `08-cost-source-trace-desktop.png`
  - `09-work-order-cost-entry-desktop.png`
  - `10-cost-manager-permission-desktop.png`
  - `11-cost-readonly-permission-desktop.png`
  - `12-cost-forbidden-desktop.png`

- [ ] **步骤 3：采集窄屏截图**

  - `13-cost-record-mobile-list.png`
  - `14-cost-record-mobile-detail.png`
  - `15-cost-record-mobile-form.png`

- [ ] **步骤 4：编写视觉分析记录**

  `notes.md` 必须包含验收日期、分支、提交号、服务地址、测试账号、截图清单、功能路径摘要、视觉结论、问题处理结果和最终结论。

- [ ] **步骤 5：检查截图文件**

  ```powershell
  Get-ChildItem docs/testing/cost-collection-visual-audit -Filter *.png | Select-Object Name,Length
  ```

- [ ] **步骤 6：更新任务和测试记录并提交**

  ```powershell
  git add docs/testing/cost-collection-visual-audit docs/tasks/009-cost-collection-foundation.md docs/testing/cost-collection-test-plan.md
  git commit -m "完成成本归集视觉分析"
  ```

## 审查和主分支验收准备

每个实现任务完成后必须按顺序执行：

1. 规格审查：由既定五角色中的合适角色对照任务文档、接口契约、测试计划和实施计划审查。
2. 代码质量审查：由既定五角色中的合适角色检查实现质量、风险、缺陷和可维护性。
3. 审查未通过时派发修复任务并复审。

成本归集功能分支满足以下全部条件后，主代理才能准备合入主分支：

- 后端全量测试通过。
- 前端全量测试通过。
- 前端构建通过。
- 本地部署健康检查通过。
- 浏览器管理员主路径通过。
- 权限与异常路径通过。
- 视觉分析截图和结论归档。
- 无阻断缺陷。
- `git diff --check` 通过。
- 任务文档和测试计划记录完整。
- 规格审查和代码质量审查通过。

合入主分支后必须重新运行关键验证、推送 `main`，并保持本地服务可用，再通知用户进行浏览器成果查验。

## 自检记录

- 本计划覆盖成本记录、自动来源、手工记录、工单成本汇总、权限、审计、异常和视觉分析要求。
- 本计划明确排除了正式财务核算、总账、凭证、标准成本、移动加权、成本差异、制造费用自动分配、采购销售联动、多组织、多公司、多租户和 BI 报表。
- 本计划使用的状态、权限、错误码、视觉目录与成本归集接口契约和测试计划一致。
- 本计划未要求修改主分支；阶段实现和验收完成后才允许进入主分支合入流程。
