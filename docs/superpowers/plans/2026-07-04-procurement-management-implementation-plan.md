# 采购管理基础模块实施计划

> **给智能体执行者：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务逐项执行本计划。步骤使用复选框（`- [ ]`）跟踪。

**目标：** 建立采购管理基础能力，使 QH ERP 能创建采购订单、过账采购入库、增加库存，并追溯采购订单、采购入库和库存流水来源。

**架构：** 采购后端作为 `com.qherp.api.system.procurement` 独立领域实现，前端作为 `apps/web/src/modules/procurement` 独立模块实现。采购入库过账通过库存域过账服务写余额和流水，避免绕过库存余额、库存流水和来源唯一约束。

**技术栈：** Java 21、Spring Boot、Spring Security、Spring Data/JdbcTemplate、Flyway、PostgreSQL、JUnit/Testcontainers、Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、Vitest、Playwright。

---

## 固定执行规则

- 主代理只负责统筹、派发、等待结果、组织审查和验收判断。
- 任何实现任务必须通过 `/goal` 派发给固定五角色中的对应角色。
- 每个实现任务完成后先做规格审查，再做代码质量审查。
- 规格审查和代码质量审查只能由固定五角色中的合适角色承担，不创建第六类角色。
- 任何实现不得扩大到应付、发票、付款、税务、审批、MRP、采购退货、质检入库、多币种、多组织、正式财务凭证或报表分析。
- 阻断缺陷必须修复并复验后才能进入下一阶段验收。

## 文件结构

### 后端新增或修改

- 新建： `apps/api/src/main/resources/db/migration/V8__procurement_management_schema.sql`
- 新建： `apps/api/src/main/java/com/qherp/api/system/procurement/ProcurementAdminController.java`
- 新建： `apps/api/src/main/java/com/qherp/api/system/procurement/ProcurementAdminService.java`
- 新建： `apps/api/src/main/java/com/qherp/api/system/procurement/PurchaseOrderStatus.java`
- 新建： `apps/api/src/main/java/com/qherp/api/system/procurement/PurchaseReceiptStatus.java`
- 新建： `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryPostingService.java`
- 修改： `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- 修改： `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryMovementType.java`
- 修改： `apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- 修改： `apps/api/src/main/java/com/qherp/api/system/production/ProductionAdminService.java`
- 测试： `apps/api/src/test/java/com/qherp/api/system/procurement/ProcurementAdminControllerTests.java`
- 测试： `apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`
- 测试： `apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`
- 测试： `apps/api/src/test/java/com/qherp/api/system/inventory/InventoryAdminControllerTests.java`
- 测试： `apps/api/src/test/java/com/qherp/api/system/production/ProductionAdminControllerTests.java`

### 前端新增或修改

- 新建： `apps/web/src/shared/api/procurementApi.ts`
- 测试： `apps/web/src/shared/api/procurementApi.spec.ts`
- 新建： `apps/web/src/modules/procurement/PurchaseOrderStatusTag.vue`
- 新建： `apps/web/src/modules/procurement/PurchaseReceiptStatusTag.vue`
- 新建： `apps/web/src/modules/procurement/procurementPageHelpers.ts`
- 新建： `apps/web/src/modules/procurement/PurchaseOrderLineEditor.vue`
- 新建： `apps/web/src/modules/procurement/PurchaseReceiptLineEditor.vue`
- 新建： `apps/web/src/modules/procurement/PurchaseOrderListView.vue`
- 新建： `apps/web/src/modules/procurement/PurchaseOrderFormView.vue`
- 新建： `apps/web/src/modules/procurement/PurchaseOrderDetailView.vue`
- 新建： `apps/web/src/modules/procurement/PurchaseReceiptListView.vue`
- 新建： `apps/web/src/modules/procurement/PurchaseReceiptFormView.vue`
- 新建： `apps/web/src/modules/procurement/PurchaseReceiptDetailView.vue`
- 测试： `apps/web/src/modules/procurement/PurchaseOrderListView.spec.ts`
- 测试： `apps/web/src/modules/procurement/PurchaseOrderFormView.spec.ts`
- 测试： `apps/web/src/modules/procurement/PurchaseOrderDetailView.spec.ts`
- 测试： `apps/web/src/modules/procurement/PurchaseReceiptListView.spec.ts`
- 测试： `apps/web/src/modules/procurement/PurchaseReceiptFormView.spec.ts`
- 测试： `apps/web/src/modules/procurement/PurchaseReceiptDetailView.spec.ts`
- 修改： `apps/web/src/router/index.ts`
- 修改： `apps/web/src/router/permissionGuard.spec.ts`
- 修改： `apps/web/src/App.vue`

### 文档和验收新增或修改

- 修改： `docs/product/product-decisions.md`
- 修改： `docs/product/business-flow.md`
- 修改： `docs/testing/acceptance-criteria.md`
- 修改： `docs/README.md`
- 更新： `docs/tasks/011-procurement-management-foundation.md`
- 更新： `docs/api/procurement-management-api.md`
- 更新： `docs/testing/procurement-management-test-plan.md`
- 新建： `docs/testing/procurement-management-visual-audit/notes.md`
- 新建： `docs/testing/procurement-management-visual-audit/*.png`
- 更新： `docs/testing/procurement-management-test-plan.md` 执行记录部分
- 新建或更新： `docs/testing/procurement-management-defects.md`，仅在实现或审查中发现缺陷时使用

## Task 1: 文档基线审查与阶段确认

**角色：** 产品经理承担规格审查，测试承担验收标准复核。

**文件：**
- 审查： `docs/tasks/011-procurement-management-foundation.md`
- 审查： `docs/superpowers/specs/2026-07-04-procurement-management-design.md`
- 审查： `docs/api/procurement-management-api.md`
- 审查： `docs/testing/procurement-management-test-plan.md`

- [ ] **步骤 1：主代理派发产品经理规格审查**

使用 `/goal` 派发给固定产品经理角色，要求对照交接文档和五角色讨论结论审查范围、业务规则、状态机、排除项和验收标准。

- [ ] **步骤 2：主代理派发测试验收标准复核**

使用 `/goal` 派发给固定测试角色，要求审查测试计划是否覆盖主路径、权限路径、异常路径、数据断言、视觉分析和质量门。

- [ ] **步骤 3：按审查结果修正文档**

仅修改上述文档。本任务不得开始实现代码。

- [ ] **步骤 4：验证文档基线**

运行：

```powershell
rg -n "TBD|TODO|待定|后续补充|占位" docs/tasks/011-procurement-management-foundation.md docs/superpowers/specs/2026-07-04-procurement-management-design.md docs/api/procurement-management-api.md docs/testing/procurement-management-test-plan.md
git diff --check
```

预期： no placeholder matches; `git diff --check` exits `0` with no whitespace errors.

## Task 2: 后端库存过账复用端口

**角色：** 后端开发承担实现，测试承担规格审查，后端开发或前端开发承担代码质量审查。

**文件：**
- 新建： `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryPostingService.java`
- 修改： `apps/api/src/main/java/com/qherp/api/system/production/ProductionAdminService.java`
- 修改： `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAdminService.java` only if shared helpers need relocation
- 测试： `apps/api/src/test/java/com/qherp/api/system/inventory/InventoryAdminControllerTests.java`
- 测试： `apps/api/src/test/java/com/qherp/api/system/production/ProductionAdminControllerTests.java`

- [ ] **步骤 1：编写预期失败的后端测试**

新增或更新测试，证明库存单据过账、生产领料过账和完工入库过账在迁移到 `InventoryPostingService` 后仍只生成一条库存流水、正确更新余额，并拒绝重复来源流水。

运行定向测试：

```powershell
docker run --rm `
  -e TESTCONTAINERS_RYUK_DISABLED=true `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal `
  -v "${PWD}:/workspace" `
  -v /var/run/docker.sock:/var/run/docker.sock `
  -v qherp-maven-repo:/root/.m2 `
  -w /workspace/apps/api `
  maven:3.9-eclipse-temurin-21 `
  mvn -q -Dtest=InventoryAdminControllerTests,ProductionAdminControllerTests test
```

实现前预期： tests fail if they assert the new shared service behavior directly, or existing tests pass while no new service exists. Continue only after the missing shared contract is explicit in tests.

- [ ] **步骤 2：实现库存过账服务**

创建 `InventoryPostingService`，提供库存域内部过账方法，接收变动类型、方向、仓库、物料、单位、数量、来源类型、来源标识、来源行标识、业务日期、原因、备注和操作人。它必须锁定或创建库存余额，写入 `inv_stock_movement`，更新 `inv_stock_balance`，并返回过账前后数量。

- [ ] **步骤 3：改造库存和生产调用方**

更新库存单据过账、生产领料过账和完工入库过账，使其调用该服务，并保持现有来源类型和错误语义。

- [ ] **步骤 4：验证定向后端测试**

运行步骤 1 中的 Docker Maven 定向测试命令。

预期： targeted tests pass with no failures.

## Task 3: 后端采购迁移、权限和领域接口

**角色：** 后端开发承担实现，产品经理承担规格审查，后端开发承担代码质量审查。

**文件：**
- 新建： `apps/api/src/main/resources/db/migration/V8__procurement_management_schema.sql`
- 新建： `apps/api/src/main/java/com/qherp/api/system/procurement/ProcurementAdminController.java`
- 新建： `apps/api/src/main/java/com/qherp/api/system/procurement/ProcurementAdminService.java`
- 新建： `apps/api/src/main/java/com/qherp/api/system/procurement/PurchaseOrderStatus.java`
- 新建： `apps/api/src/main/java/com/qherp/api/system/procurement/PurchaseReceiptStatus.java`
- 修改： `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- 修改： `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryMovementType.java`
- 修改： `apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- 测试： `apps/api/src/test/java/com/qherp/api/system/procurement/ProcurementAdminControllerTests.java`
- 测试： `apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`
- 测试： `apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`

- [ ] **步骤 1：编写预期失败的采购集成测试**

Create `ProcurementAdminControllerTests` covering purchase order create/update/confirm/cancel/close, purchase receipt create/update/post, inventory balance increase, `PURCHASE_RECEIPT` movement, order received quantities, duplicate post, status errors, disabled master data, permission errors, and audit records.

运行：

```powershell
docker run --rm `
  -e TESTCONTAINERS_RYUK_DISABLED=true `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal `
  -v "${PWD}:/workspace" `
  -v /var/run/docker.sock:/var/run/docker.sock `
  -v qherp-maven-repo:/root/.m2 `
  -w /workspace/apps/api `
  maven:3.9-eclipse-temurin-21 `
  mvn -q -Dtest=ProcurementAdminControllerTests test
```

预期： 失败，原因是 procurement schema and endpoints do not exist.

- [ ] **步骤 2：新增迁移和枚举**

新增 `V8__procurement_management_schema.sql`、采购状态枚举、`PURCHASE_RECEIPT` 库存变动类型和 `PROCUREMENT_*` 错误码。

- [ ] **步骤 3：新增权限**

Seed `procurement`, `procurement:order:*`, and `procurement:receipt:*` permissions in `AccountPermissionInitializer`. Update permission tests so admin receives new permissions and API paths are protected.

- [ ] **步骤 4：实现采购服务和控制器**

实现 `docs/api/procurement-management-api.md` 中的全部接口。采购入库过账使用 `InventoryPostingService`，不得写入成本记录或生产状态。

- [ ] **步骤 5：验证定向后端测试**

运行步骤 1 中的 Docker Maven 命令。

预期： `ProcurementAdminControllerTests` 通过.

## Task 4: 后端全量回归

**角色：** 测试承担执行，后端开发承担修复，后端开发承担代码质量复审。

**文件：**
- 任务 2 和任务 3 涉及的全部后端文件

- [ ] **步骤 1：运行后端全量测试**

运行：

```powershell
docker run --rm `
  -e TESTCONTAINERS_RYUK_DISABLED=true `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal `
  -v "${PWD}:/workspace" `
  -v /var/run/docker.sock:/var/run/docker.sock `
  -v qherp-maven-repo:/root/.m2 `
  -w /workspace/apps/api `
  maven:3.9-eclipse-temurin-21 `
  mvn -q test
```

预期： all backend tests pass.

- [ ] **步骤 2：检查 Testcontainers 残留**

运行：

```powershell
docker ps -a --filter "label=org.testcontainers" --format "{{.ID}} {{.Image}} {{.Status}} {{.Names}}"
```

预期： no stale containers relevant to the test run remain.

## Task 5: 前端 API、路由、菜单和权限

**角色：** 前端开发承担实现，产品经理承担规格审查，前端开发承担代码质量审查。

**文件：**
- 新建： `apps/web/src/shared/api/procurementApi.ts`
- 测试： `apps/web/src/shared/api/procurementApi.spec.ts`
- 修改： `apps/web/src/router/index.ts`
- 修改： `apps/web/src/router/permissionGuard.spec.ts`
- 修改： `apps/web/src/App.vue`

- [ ] **步骤 1：编写预期失败的 API 和路由测试**

新增测试覆盖采购 API 路径、查询参数、写操作 CSRF、错误处理、路由守卫和菜单可见性。

运行：

```powershell
cd apps/web
npm test -- procurementApi.spec.ts permissionGuard.spec.ts App.spec.ts
```

预期： 失败，原因是 procurement API, routes, and menu are not implemented.

- [ ] **步骤 2：实现采购 API 客户端**

创建 `procurementApi.ts`，按接口契约提供 `orders` 和 `receipts` 分组。数量和单价使用字符串载荷。

- [ ] **步骤 3：新增路由和菜单**

新增带权限要求的 `/procurement` 路由。在 `App.vue` 中基于当前认证状态权限新增采购菜单入口。

- [ ] **步骤 4：验证定向前端测试**

运行步骤 1 中的命令。

预期： targeted tests pass.

## Task 6: 前端采购订单页面

**角色：** 前端开发承担实现，UI 设计师承担规格审查，前端开发承担代码质量审查。

**文件：**
- 新建： `apps/web/src/modules/procurement/PurchaseOrderStatusTag.vue`
- 新建： `apps/web/src/modules/procurement/procurementPageHelpers.ts`
- 新建： `apps/web/src/modules/procurement/PurchaseOrderLineEditor.vue`
- 新建： `apps/web/src/modules/procurement/PurchaseOrderListView.vue`
- 新建： `apps/web/src/modules/procurement/PurchaseOrderFormView.vue`
- 新建： `apps/web/src/modules/procurement/PurchaseOrderDetailView.vue`
- 测试： `apps/web/src/modules/procurement/PurchaseOrderListView.spec.ts`
- 测试： `apps/web/src/modules/procurement/PurchaseOrderFormView.spec.ts`
- 测试： `apps/web/src/modules/procurement/PurchaseOrderDetailView.spec.ts`

- [ ] **步骤 1：编写预期失败的采购订单页面测试**

Cover list filtering, empty state, error state, form validation, line editing, status operations, permission buttons, and receipt trace section.

运行：

```powershell
cd apps/web
npm test -- PurchaseOrderListView.spec.ts PurchaseOrderFormView.spec.ts PurchaseOrderDetailView.spec.ts
```

预期： 失败，原因是 views do not exist.

- [ ] **步骤 2：实现采购订单列表、表单和详情**

遵循现有库存、生产和成本页面模式。不得新增看板、审批、应付、发票、付款、退货或质检界面。

- [ ] **步骤 3：验证采购订单定向测试**

运行步骤 1 中的命令。

预期： targeted tests pass.

## Task 7: 前端采购入库页面

**角色：** 前端开发承担实现，UI 设计师承担规格审查，前端开发承担代码质量审查。

**文件：**
- 新建： `apps/web/src/modules/procurement/PurchaseReceiptStatusTag.vue`
- 新建： `apps/web/src/modules/procurement/PurchaseReceiptLineEditor.vue`
- 新建： `apps/web/src/modules/procurement/PurchaseReceiptListView.vue`
- 新建： `apps/web/src/modules/procurement/PurchaseReceiptFormView.vue`
- 新建： `apps/web/src/modules/procurement/PurchaseReceiptDetailView.vue`
- 测试： `apps/web/src/modules/procurement/PurchaseReceiptListView.spec.ts`
- 测试： `apps/web/src/modules/procurement/PurchaseReceiptFormView.spec.ts`
- 测试： `apps/web/src/modules/procurement/PurchaseReceiptDetailView.spec.ts`

- [ ] **步骤 1：编写预期失败的采购入库页面测试**

Cover receipt list filtering, source order summary, line validation, over-receipt prevention messaging, post action, posted immutable state, inventory movement trace, and permission buttons.

运行：

```powershell
cd apps/web
npm test -- PurchaseReceiptListView.spec.ts PurchaseReceiptFormView.spec.ts PurchaseReceiptDetailView.spec.ts
```

预期： 失败，原因是 views do not exist.

- [ ] **步骤 2：实现采购入库列表、表单和详情**

使用整页表单和详情分区。页面文案必须明确“采购入库”，避免与库存单据混淆。

- [ ] **步骤 3：验证采购入库定向测试**

运行步骤 1 中的命令。

预期： targeted tests pass.

## Task 8: 前端全量回归和构建

**角色：** 测试承担执行，前端开发承担修复，前端开发承担代码质量复审。

**文件：**
- 任务 5、任务 6 和任务 7 涉及的全部前端文件

- [ ] **步骤 1：运行前端全量测试**

运行：

```powershell
cd apps/web
npm test
```

预期： all frontend tests pass.

- [ ] **步骤 2：运行类型检查**

运行：

```powershell
cd apps/web
npm run typecheck
```

预期： typecheck exits `0`.

- [ ] **步骤 3：运行前端构建**

运行：

```powershell
cd apps/web
npm run build
```

预期： build exits `0`.

## Task 9: 本地部署、浏览器主路径和权限验收

**角色：** 测试承担执行，前端开发和后端开发承担修复，产品经理承担规格复审。

**文件：**
- 更新： `docs/testing/procurement-management-test-plan.md`
- 新建或更新： `docs/testing/procurement-management-defects.md`，仅在发现问题时使用

- [ ] **步骤 1：启动本地服务**

按本地开发文档启动 PostgreSQL、后端和前端。后端使用 `18080`，前端使用可用 Vite 端口。

- [ ] **步骤 2：验证健康检查**

运行：

```powershell
Invoke-WebRequest -Uri 'http://127.0.0.1:18080/api/health' -UseBasicParsing
```

预期： response contains `{"status":"UP","service":"qherp-api"}`.

- [ ] **步骤 3：执行浏览器主路径**

使用真实浏览器自动化完成采购订单创建、确认、部分入库过账、剩余入库过账、库存余额检查、库存流水追溯、入库到订单追溯和订单到入库追溯。

- [ ] **步骤 4：执行权限路径**

验证管理员、采购员、仓库角色、只读用户、无权限用户和未登录用户，确认前后端权限一致。

- [ ] **步骤 5：执行异常路径**

验证停用供应商、停用物料、停用仓库、未确认订单入库、已取消或已关闭订单入库、零或负数量、超入库、重复过账、已过账编辑和来源行不匹配。

- [ ] **步骤 6：记录结果**

将执行结果、账号、数据标识、命令输出和缺陷记录追加到 `docs/testing/procurement-management-test-plan.md`，如创建独立执行记录则同步记录。

## Task 10: 浏览器视觉分析

**角色：** UI 设计师承担视觉审查，测试承担截图执行和记录，前端开发承担修复。

**文件：**
- 新建： `docs/testing/procurement-management-visual-audit/notes.md`
- 新建： `docs/testing/procurement-management-visual-audit/*.png`
- 更新： `docs/testing/procurement-management-test-plan.md`

- [ ] **步骤 1：采集视觉证据**

采集 `docs/testing/procurement-management-test-plan.md` 中列出的截图，覆盖桌面和窄屏视口状态。

- [ ] **步骤 2：编写视觉记录**

创建 `notes.md`，记录截图文件名、视口尺寸、页面、发现问题、处理结果和最终结论。

- [ ] **步骤 3：验证视觉资产**

运行：

```powershell
Get-ChildItem -LiteralPath 'docs/testing/procurement-management-visual-audit' -Filter '*.png' | Select-Object Name,Length
```

预期： all expected PNG files exist and have nonzero length.

## Task 11: 最终质量门和阶段复审

**角色：** 测试执行最终质量门，产品经理做规格复审，后端开发或前端开发做代码质量审查，主代理做最终验收判断。

**文件：**
- 采购实现期间变更的全部文件
- 更新： `docs/testing/procurement-management-test-plan.md`
- 更新： `docs/testing/procurement-management-defects.md` if applicable

- [ ] **步骤 1：运行最终后端测试**

Run backend full test command from Task 4.

- [ ] **步骤 2：运行最终前端测试、类型检查和构建**

运行任务 8 中的前端命令。

- [ ] **步骤 3：运行空白检查**

运行：

```powershell
git diff --check
```

预期： exits `0` with no whitespace errors.

- [ ] **步骤 4：确认没有未解决阻断缺陷**

检查采购缺陷记录。阻断缺陷必须修复并复验，严重缺陷必须修复或形成明确风险决策。

- [ ] **步骤 5：固定角色最终复审**

Use `/goal` to dispatch:

- 产品经理：规格复审。
- 后端开发：后端代码质量审查.
- 前端开发：前端代码质量审查.
- UI 设计师：视觉分析复审.
- 测试：最终验收证据复核.

- [ ] **步骤 6：主代理交付判断**

主代理复核当前证据后，再判断采购阶段是否可合入或交付浏览器验收。没有新鲜命令输出和当前浏览器证据时，不得声称完成。

## 自检记录

- 规格覆盖： this plan covers procurement order, procurement receipt, inventory posting, permissions, audit, frontend views, tests, browser acceptance, visual analysis, and final quality gates.
- 范围检查： this plan excludes payable, invoice, payment, tax, approval, inquiry, supplier scoring, MRP, purchase return, inspection receipt, multi-currency, multi-organization, formal financial voucher, and reporting.
- 类型一致性： route paths, permission codes, status names, and inventory movement type match `docs/api/procurement-management-api.md` and `docs/superpowers/specs/2026-07-04-procurement-management-design.md`.
- 审查边界： implementation tasks are assigned to fixed project roles; main agent remains responsible for coordination and acceptance judgment.
