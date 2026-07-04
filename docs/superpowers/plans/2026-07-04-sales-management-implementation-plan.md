# 销售管理基础模块实施计划

> **给智能体执行者：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务逐项执行本计划。受本仓库固定角色规则约束，实施时只复用产品经理、UI 设计师、前端开发、后端开发、测试 5 个固定角色子代理，不创建第六类角色。步骤使用复选框（`- [ ]`）跟踪。

**目标：** 建立销售订单、销售出库、库存扣减和销售来源追溯闭环，使 QH ERP 能完成制造业销售出库基础链路。

**架构：** 销售后端作为 `com.qherp.api.system.sales` 独立领域实现，前端作为 `apps/web/src/modules/sales` 独立模块实现。销售出库过账通过库存域 `InventoryPostingService` 扣减余额和写入 `SALES_SHIPMENT` 库存流水，避免绕过库存余额、库存流水和来源唯一约束。

**技术栈：** Java 21、Spring Boot、Spring Security、JdbcTemplate、Flyway、PostgreSQL、JUnit/Testcontainers、Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、Vitest、Playwright。

---

## 固定执行规则

- 主代理只负责统筹、派发、等待结果、组织审查和验收判断。
- 任一实现任务必须通过 `/goal` 派发给固定五角色中的对应角色。
- 每个实现任务完成后先做规格审查，再做代码质量审查。
- 规格审查和代码质量审查只能由固定五角色中的合适角色承担，不创建规格审查、代码质量审查等新角色。
- 后端和前端实现必须遵循 TDD：先写预期失败测试，再实现，再运行测试通过。
- 本阶段只实现销售订单、销售出库、库存扣减、来源追溯、权限、审计和验收证据。
- 不实现应收、发票、收款、税务、收入确认、正式凭证、销售退货、换货、物流发运、报价、合同、价格体系、信用额度、审批、报表、多币种、多组织、多公司、多账套或多租户。
- `SALES_SHIPMENT`、`sales:shipment:*`、`/sales/shipments` 是固定命名，不使用 outbound 命名。
- 可销售物料固定为启用的 `FINISHED_GOOD` 和 `SEMI_FINISHED`；启用的 `RAW_MATERIAL`、`AUXILIARY` 本阶段不可销售。
- 销售订单创建、更新、确认时客户必须启用；订单确认后客户停用不阻止基于该订单创建、更新或过账销售出库。
- 阻断缺陷必须修复并复验后才能进入阶段验收。

## 文件结构

### 后端新增或修改

- 新建： `apps/api/src/main/resources/db/migration/V9__sales_management_schema.sql`
- 新建： `apps/api/src/main/java/com/qherp/api/system/sales/SalesAdminController.java`
- 新建： `apps/api/src/main/java/com/qherp/api/system/sales/SalesAdminService.java`
- 新建： `apps/api/src/main/java/com/qherp/api/system/sales/SalesOrderStatus.java`
- 新建： `apps/api/src/main/java/com/qherp/api/system/sales/SalesShipmentStatus.java`
- 修改： `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- 修改： `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryMovementType.java`
- 修改： `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryPostingService.java`
- 修改： `apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- 修改： `apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`
- 测试： `apps/api/src/test/java/com/qherp/api/system/sales/SalesAdminControllerTests.java`
- 测试： `apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`
- 测试： `apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`
- 测试： `apps/api/src/test/java/com/qherp/api/system/inventory/InventoryAdminControllerTests.java`

### 前端新增或修改

- 新建： `apps/web/src/shared/api/salesApi.ts`
- 测试： `apps/web/src/shared/api/salesApi.spec.ts`
- 新建： `apps/web/src/modules/sales/salesPageHelpers.ts`
- 新建： `apps/web/src/modules/sales/SalesOrderStatusTag.vue`
- 新建： `apps/web/src/modules/sales/SalesShipmentStatusTag.vue`
- 新建： `apps/web/src/modules/sales/SalesOrderLineEditor.vue`
- 新建： `apps/web/src/modules/sales/SalesOrderListView.vue`
- 新建： `apps/web/src/modules/sales/SalesOrderFormView.vue`
- 新建： `apps/web/src/modules/sales/SalesOrderDetailView.vue`
- 新建： `apps/web/src/modules/sales/SalesShipmentLineEditor.vue`
- 新建： `apps/web/src/modules/sales/SalesShipmentListView.vue`
- 新建： `apps/web/src/modules/sales/SalesShipmentFormView.vue`
- 新建： `apps/web/src/modules/sales/SalesShipmentDetailView.vue`
- 测试： `apps/web/src/modules/sales/SalesOrderListView.spec.ts`
- 测试： `apps/web/src/modules/sales/SalesOrderFormView.spec.ts`
- 测试： `apps/web/src/modules/sales/SalesOrderDetailView.spec.ts`
- 测试： `apps/web/src/modules/sales/SalesShipmentListView.spec.ts`
- 测试： `apps/web/src/modules/sales/SalesShipmentFormView.spec.ts`
- 测试： `apps/web/src/modules/sales/SalesShipmentDetailView.spec.ts`
- 修改： `apps/web/src/shared/api/inventoryApi.ts`
- 修改： `apps/web/src/shared/api/inventoryApi.spec.ts`
- 修改： `apps/web/src/modules/inventory/inventoryPageHelpers.ts`
- 修改： `apps/web/src/modules/inventory/InventoryMovementListView.vue`
- 修改： `apps/web/src/modules/inventory/InventoryMovementListView.spec.ts`
- 修改： `apps/web/src/router/index.ts`
- 修改： `apps/web/src/router/permissionGuard.spec.ts`
- 修改： `apps/web/src/App.vue`
- 修改： `apps/web/src/App.spec.ts`

### 文档和验收新增或修改

- 新建： `docs/tasks/012-sales-management-foundation.md`
- 新建： `docs/api/sales-management-api.md`
- 新建： `docs/testing/sales-management-test-plan.md`
- 新建： `docs/superpowers/specs/2026-07-04-sales-management-design.md`
- 新建： `docs/testing/sales-management-visual-audit/notes.md`
- 新建： `docs/testing/sales-management-visual-audit/*.png`
- 修改： `docs/product/product-decisions.md`
- 修改： `docs/product/business-flow.md`
- 修改： `docs/testing/acceptance-criteria.md`
- 修改： `docs/README.md`
- 更新： `docs/testing/sales-management-test-plan.md` 执行记录部分
- 新建或更新： `docs/testing/sales-management-defects.md`，仅在实现或审查中发现缺陷时使用

## Task 1: 文档基线审查与阶段确认

**角色：** 产品经理承担规格审查，测试承担验收标准复核，UI 设计师承担设计规格复核，后端开发承担接口契约复核，前端开发承担前端可行性复核。

**文件：**
- 审查： `docs/tasks/012-sales-management-foundation.md`
- 审查： `docs/superpowers/specs/2026-07-04-sales-management-design.md`
- 审查： `docs/api/sales-management-api.md`
- 审查： `docs/testing/sales-management-test-plan.md`

- [x] **步骤 1：主代理派发固定五角色阶段讨论**

使用 `/goal` 分别派发产品经理、UI 设计师、前端开发、后端开发和测试角色，确认阶段目标、范围边界、关键风险、验收标准和落地顺序。

- [x] **步骤 2：修正文档口径冲突**

已统一 `SALES_SHIPMENT`、`sales:shipment:*`、`/sales/shipments`、可销售物料、客户停用后出库、`SHIPPED` 中文状态等口径。

- [x] **步骤 3：验证文档基线**

运行：

```powershell
$oldSalesTerms = @('SALES_' + 'OUTBOUND', 'sales:' + 'outbound', '/sales/' + 'outbounds', '`SHIPPED` / 已完成', '状态.*已出库', 'TO' + 'DO', 'TB' + 'D', '待' + '定', '未' + '定') -join '|'
rg -n $oldSalesTerms docs/tasks/012-sales-management-foundation.md docs/api/sales-management-api.md docs/testing/sales-management-test-plan.md docs/superpowers/specs/2026-07-04-sales-management-design.md docs/product/product-decisions.md docs/product/business-flow.md docs/testing/acceptance-criteria.md docs/README.md
git diff --check
```

预期： no conflict or placeholder matches; `git diff --check` exits `0` with no whitespace errors.

## Task 2: 后端销售基础迁移、错误码、权限和库存来源支持

**角色：** 后端开发承担实现，测试承担规格审查，后端开发承担代码质量审查。

**文件：**
- 新建： `apps/api/src/main/resources/db/migration/V9__sales_management_schema.sql`
- 新建： `apps/api/src/main/java/com/qherp/api/system/sales/SalesOrderStatus.java`
- 新建： `apps/api/src/main/java/com/qherp/api/system/sales/SalesShipmentStatus.java`
- 修改： `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- 修改： `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryMovementType.java`
- 修改： `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryPostingService.java`
- 修改： `apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- 修改： `apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`
- 测试： `apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`
- 测试： `apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`
- 测试： `apps/api/src/test/java/com/qherp/api/system/inventory/InventoryAdminControllerTests.java`

- [x] **步骤 1：编写预期失败的基础后端测试**

新增或更新测试，覆盖以下预期：

- 管理员权限初始化包含 `sales`、`sales:order:view/create/update/confirm/cancel/close`、`sales:shipment:view/create/update/post`。
- 受保护路径覆盖 `/api/admin/sales/orders/**` 和 `/api/admin/sales/shipments/**`。
- `PermissionAuthorizationManager` 将销售订单和销售出库路径映射到对应 `sales:*` 权限，未授权账号返回 `AUTH_FORBIDDEN`。
- `InventoryMovementType` 支持 `SALES_SHIPMENT`。
- `InventoryPostingService` 对 `sourceType=SALES_SHIPMENT` 的库存不足返回 `SALES_STOCK_NOT_ENOUGH`，来源重复返回 `SALES_MOVEMENT_SOURCE_DUPLICATED`。

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
  mvn -q -Dtest=AccountPermissionInitializerTests,PermissionAuthorizationTests,InventoryAdminControllerTests test
```

预期：新增销售断言失败，原因是销售权限、库存类型或销售错误码尚不存在。

- [x] **步骤 2：新增销售迁移和状态枚举**

新增 `V9__sales_management_schema.sql`，包含 `sal_sales_order`、`sal_sales_order_line`、`sal_sales_shipment`、`sal_sales_shipment_line`，并修改 `inv_stock_movement` 类型约束加入 `SALES_SHIPMENT`。

迁移必须包含以下约束和索引：

- `sal_sales_order.order_no` 唯一，状态 check 限制为 `DRAFT`、`CONFIRMED`、`PARTIALLY_SHIPPED`、`SHIPPED`、`CLOSED`、`CANCELLED`。
- `sal_sales_order_line` 外键关联订单、物料、单位；同一订单内 `line_no` 唯一；同一订单内 `material_id` 唯一；`quantity > 0`；`unit_price >= 0`；`0 <= shipped_quantity <= quantity`。
- `sal_sales_shipment.shipment_no` 唯一，状态 check 限制为 `DRAFT`、`POSTED`；外键关联订单、客户、仓库。
- `sal_sales_shipment_line` 外键关联出库单、来源订单行、物料、单位；同一出库单内 `line_no` 唯一；同一出库单内 `order_line_id` 唯一；`ordered_quantity > 0`；`remaining_quantity_before >= 0`；`quantity > 0`；过账前后库存字段为空或非负。
- 常用索引覆盖订单客户、订单状态日期、订单预计出库日期、订单明细物料、出库订单、出库客户、出库仓库、出库状态日期、出库明细来源订单行。

状态枚举固定为：

```java
public enum SalesOrderStatus {
	DRAFT, CONFIRMED, PARTIALLY_SHIPPED, SHIPPED, CLOSED, CANCELLED
}

public enum SalesShipmentStatus {
	DRAFT, POSTED
}
```

- [x] **步骤 3：新增错误码、库存类型和权限种子**

在 `ApiErrorCode` 增加 `SALES_*` 错误码，至少包含接口契约中的订单不存在、出库不存在、状态非法、空明细、客户/仓库/物料/单位非法、不可销售物料、数量非法、单价非法、重复行、超出库、来源行不匹配、库存不足、已过账不可编辑、重复过账、来源重复。

在 `InventoryMovementType` 增加：

```java
SALES_SHIPMENT
```

在 `AccountPermissionInitializer` 增加销售菜单和操作权限，排序应位于采购之后、生产之前或与现有菜单顺序保持可读。

在 `PermissionAuthorizationManager` 增加销售路径映射：

- `GET /api/admin/sales/orders/**` -> `sales:order:view`
- `POST /api/admin/sales/orders` -> `sales:order:create`
- `PUT /api/admin/sales/orders/{id}` -> `sales:order:update`
- `PUT /api/admin/sales/orders/{id}/confirm` -> `sales:order:confirm`
- `PUT /api/admin/sales/orders/{id}/cancel` -> `sales:order:cancel`
- `PUT /api/admin/sales/orders/{id}/close` -> `sales:order:close`
- `GET /api/admin/sales/shipments/**` -> `sales:shipment:view`
- `POST /api/admin/sales/orders/{id}/shipments` -> `sales:shipment:create`
- `PUT /api/admin/sales/shipments/{id}` -> `sales:shipment:update`
- `PUT /api/admin/sales/shipments/{id}/post` -> `sales:shipment:post`

- [x] **步骤 4：更新库存过账服务销售来源错误映射**

`InventoryPostingService` 识别 `sourceType=SALES_SHIPMENT`：

- 库存不足返回 `SALES_STOCK_NOT_ENOUGH`。
- 来源重复返回 `SALES_MOVEMENT_SOURCE_DUPLICATED`。
- 流水号前缀建议使用 `SAL-SHP-MOV`，不得复用采购或库存单据前缀。

- [x] **步骤 5：验证定向基础后端测试**

运行步骤 1 中的 Docker Maven 定向测试命令。

预期： targeted tests pass.

## Task 3: 后端销售订单和销售出库接口

**角色：** 后端开发承担实现，产品经理承担规格审查，后端开发承担代码质量审查。

**文件：**
- 新建： `apps/api/src/main/java/com/qherp/api/system/sales/SalesAdminController.java`
- 新建： `apps/api/src/main/java/com/qherp/api/system/sales/SalesAdminService.java`
- 测试： `apps/api/src/test/java/com/qherp/api/system/sales/SalesAdminControllerTests.java`

- [x] **步骤 1：编写预期失败的销售集成测试**

创建 `SalesAdminControllerTests`，覆盖：

- 管理员创建、更新、确认、取消、关闭销售订单。
- 销售订单只允许 `FINISHED_GOOD`、`SEMI_FINISHED`，拒绝 `RAW_MATERIAL`、`AUXILIARY`。
- 销售单价使用 decimal，负数、超精度、非法格式返回受控错误。
- 基于已确认订单创建销售出库草稿、更新草稿、过账。
- 两次出库后订单状态从 `CONFIRMED` 到 `PARTIALLY_SHIPPED` 再到 `SHIPPED`。
- 过账扣减库存余额，写 `SALES_SHIPMENT`、`OUT`、`sourceType=SALES_SHIPMENT` 流水。
- 重复过账、已过账编辑、超订单未出库、库存不足、来源订单行不匹配、停用仓库/物料/单位失败时无部分写入。
- 订单确认后客户停用不阻止销售出库创建和过账。
- 管理员、销售员、仓库角色、只读、无权限、未登录和 CSRF 路径符合测试计划。
- 销售写操作写审计日志。

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
  mvn -q -Dtest=SalesAdminControllerTests test
```

预期：失败，原因是 sales schema and endpoints do not exist.

- [x] **步骤 2：实现销售控制器**

`SalesAdminController` 路径固定为 `/api/admin/sales`，接口固定为：

- `GET /orders`
- `GET /orders/{id}`
- `POST /orders`
- `PUT /orders/{id}`
- `PUT /orders/{id}/confirm`
- `PUT /orders/{id}/cancel`
- `PUT /orders/{id}/close`
- `GET /shipments`
- `POST /orders/{id}/shipments`
- `GET /shipments/{id}`
- `PUT /shipments/{id}`
- `PUT /shipments/{id}/post`

- [x] **步骤 3：实现销售服务和校验**

`SalesAdminService` 可参考 `ProcurementAdminService` 的结构，但必须保留销售差异：

- `validateSellableMaterial` 只允许启用 `FINISHED_GOOD`、`SEMI_FINISHED`。
- 销售订单确认时重新校验客户、物料、单位、数量和单价。
- 销售出库创建、更新和过账时不因客户后续停用阻断，但必须校验订单状态、来源行、物料、单位、仓库和库存。
- 过账必须在单事务内锁定出库单、订单、订单行和库存余额。
- 过账通过 `InventoryPostingService.post` 传入 `InventoryMovementType.SALES_SHIPMENT`、`InventoryDirection.OUT`、`sourceType=SALES_SHIPMENT`。
- 任一过账明细失败时整单回滚。

- [x] **步骤 4：验证定向销售后端测试**

运行步骤 1 中的 Docker Maven 命令。

预期： `SalesAdminControllerTests` pass.

## Task 4: 后端全量回归

**角色：** 测试承担执行，后端开发承担修复，后端开发承担代码质量复审。

**文件：**
- Task 2 和 Task 3 涉及的全部后端文件

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

## Task 5: 前端销售 API、路由、菜单和库存来源跳转

**角色：** 前端开发承担实现，产品经理承担规格审查，前端开发承担代码质量审查。

**文件：**
- 新建： `apps/web/src/shared/api/salesApi.ts`
- 测试： `apps/web/src/shared/api/salesApi.spec.ts`
- 修改： `apps/web/src/shared/api/inventoryApi.ts`
- 修改： `apps/web/src/shared/api/inventoryApi.spec.ts`
- 修改： `apps/web/src/modules/inventory/inventoryPageHelpers.ts`
- 修改： `apps/web/src/router/index.ts`
- 修改： `apps/web/src/router/permissionGuard.spec.ts`
- 修改： `apps/web/src/App.vue`
- 修改： `apps/web/src/App.spec.ts`
- 修改： `apps/web/src/modules/inventory/InventoryMovementListView.vue`
- 修改： `apps/web/src/modules/inventory/InventoryMovementListView.spec.ts`

- [ ] **步骤 1：编写预期失败的 API、路由和菜单测试**

新增测试覆盖：

- `salesApi` 的订单/出库列表查询、详情、创建、更新、状态操作、过账、CSRF 和错误信封。
- `inventoryApi` 的 `InventoryMovementType` 包含 `SALES_SHIPMENT`。
- 路由 `/sales`、`/sales/orders`、`/sales/orders/create`、`/sales/orders/:id`、`/sales/orders/:id/edit`、`/sales/orders/:id/shipments/create`、`/sales/shipments`、`/sales/shipments/:id`、`/sales/shipments/:id/edit` 权限正确。
- `App.vue` 销售菜单按 `sales:order:view`、`sales:shipment:view` 显示，缺权限时递归移除。
- 库存流水 `SALES_SHIPMENT` 在 `inventoryPageHelpers.ts` 和 `InventoryMovementListView.vue` 中显示“销售出库”，筛选项传 `SALES_SHIPMENT`，来源跳转到 `sales-shipment-detail`，无 `sales:shipment:view` 时隐藏来源跳转。

运行：

```powershell
cd apps/web
npm test -- salesApi.spec.ts inventoryApi.spec.ts permissionGuard.spec.ts App.spec.ts InventoryMovementListView.spec.ts
```

预期：失败，原因是 sales API, routes, menu, movement mapping are not implemented.

- [ ] **步骤 2：实现销售 API 客户端**

创建 `salesApi.ts`，接口分组为 `orders` 和 `shipments`。销售数量、出库数量和销售单价 payload 使用字符串。

状态类型固定为：

```ts
export type SalesOrderStatus = 'DRAFT' | 'CONFIRMED' | 'PARTIALLY_SHIPPED' | 'SHIPPED' | 'CLOSED' | 'CANCELLED'
export type SalesShipmentStatus = 'DRAFT' | 'POSTED'
```

- [ ] **步骤 3：新增路由、菜单和库存来源跳转**

新增销售路由，修改 `App.vue` 补齐销售菜单。修改库存流水页面的来源类型映射，确保 `SALES_SHIPMENT` 不跳到库存单据或采购入库。

- [ ] **步骤 4：验证定向前端基础测试**

运行步骤 1 中的命令。

预期： targeted tests pass.

- [ ] **步骤 5：验证前端类型检查**

运行：

```powershell
cd apps/web
npm run typecheck
```

预期： typecheck exits `0`.

## Task 6: 前端销售订单页面

**角色：** 前端开发承担实现，UI 设计师承担规格审查，前端开发承担代码质量审查。

**文件：**
- 新建： `apps/web/src/modules/sales/SalesOrderStatusTag.vue`
- 新建： `apps/web/src/modules/sales/salesPageHelpers.ts`
- 新建： `apps/web/src/modules/sales/SalesOrderLineEditor.vue`
- 新建： `apps/web/src/modules/sales/SalesOrderListView.vue`
- 新建： `apps/web/src/modules/sales/SalesOrderFormView.vue`
- 新建： `apps/web/src/modules/sales/SalesOrderDetailView.vue`
- 修改： `apps/web/src/router/index.ts`
- 修改： `apps/web/src/router/permissionGuard.spec.ts`
- 测试： `apps/web/src/modules/sales/SalesOrderListView.spec.ts`
- 测试： `apps/web/src/modules/sales/SalesOrderFormView.spec.ts`
- 测试： `apps/web/src/modules/sales/SalesOrderDetailView.spec.ts`

- [ ] **步骤 1：编写预期失败的销售订单页面测试**

覆盖列表筛选、空态、错误态、权限按钮、状态标签、表单校验、成品/半成品过滤、原材料/辅料不可销售提示、数量和单价字符串 payload、确认/取消/关闭、出库记录追溯、创建出库入口，并验证销售订单路由加载真实 `SalesOrder*View.vue` 组件而非占位页。

运行：

```powershell
cd apps/web
npm test -- SalesOrderListView.spec.ts SalesOrderFormView.spec.ts SalesOrderDetailView.spec.ts permissionGuard.spec.ts
```

预期：失败，原因是 sales order views do not exist.

- [ ] **步骤 2：实现销售订单列表、表单和详情**

复用采购订单页面模式。页面必须使用后台 ERP 信息密度，不新增看板、CRM、报价、合同、应收、发票、收款、退货、物流或审批界面。若 Task 5 为销售订单路由使用占位组件，本任务必须把 `/sales/orders`、`/sales/orders/create`、`/sales/orders/:id`、`/sales/orders/:id/edit` 替换为真实销售订单页面组件。

- [ ] **步骤 3：验证销售订单定向测试**

运行步骤 1 中的命令。

预期： targeted tests pass.

## Task 7: 前端销售出库页面

**角色：** 前端开发承担实现，UI 设计师承担规格审查，前端开发承担代码质量审查。

**文件：**
- 新建： `apps/web/src/modules/sales/SalesShipmentStatusTag.vue`
- 新建： `apps/web/src/modules/sales/SalesShipmentLineEditor.vue`
- 新建： `apps/web/src/modules/sales/SalesShipmentListView.vue`
- 新建： `apps/web/src/modules/sales/SalesShipmentFormView.vue`
- 新建： `apps/web/src/modules/sales/SalesShipmentDetailView.vue`
- 修改： `apps/web/src/router/index.ts`
- 修改： `apps/web/src/router/permissionGuard.spec.ts`
- 测试： `apps/web/src/modules/sales/SalesShipmentListView.spec.ts`
- 测试： `apps/web/src/modules/sales/SalesShipmentFormView.spec.ts`
- 测试： `apps/web/src/modules/sales/SalesShipmentDetailView.spec.ts`

- [ ] **步骤 1：编写预期失败的销售出库页面测试**

覆盖销售出库列表筛选、来源订单摘要、明细候选、未出库数量、本次出库数量、超出库提示、库存不足提示、客户停用后不阻断出库、过账二次确认、已过账只读、库存流水追溯、权限按钮，并验证销售出库路由加载真实 `SalesShipment*View.vue` 组件而非占位页。

运行：

```powershell
cd apps/web
npm test -- SalesShipmentListView.spec.ts SalesShipmentFormView.spec.ts SalesShipmentDetailView.spec.ts permissionGuard.spec.ts
```

预期：失败，原因是 sales shipment views do not exist.

- [ ] **步骤 2：实现销售出库列表、表单和详情**

复用采购入库页面模式。页面文案必须统一“销售出库”，不得把销售出库描述成库存单据、物流发运、发票、收款或应收确认。若 Task 5 为销售出库路由使用占位组件，本任务必须把 `/sales/shipments`、`/sales/orders/:id/shipments/create`、`/sales/shipments/:id`、`/sales/shipments/:id/edit` 替换为真实销售出库页面组件。

- [ ] **步骤 3：验证销售出库定向测试**

运行步骤 1 中的命令。

预期： targeted tests pass.

## Task 8: 前端全量回归和构建

**角色：** 测试承担执行，前端开发承担修复，前端开发承担代码质量复审。

**文件：**
- Task 5、Task 6 和 Task 7 涉及的全部前端文件

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
- 更新： `docs/testing/sales-management-test-plan.md`
- 新建或更新： `docs/testing/sales-management-defects.md`，仅在发现问题时使用

- [ ] **步骤 1：启动本地服务**

按本地开发文档启动 PostgreSQL、后端和前端。后端使用 `18080`，前端使用可用 Vite 端口。

- [ ] **步骤 2：验证健康检查**

运行：

```powershell
Invoke-WebRequest -Uri 'http://127.0.0.1:18080/api/health' -UseBasicParsing
```

预期： response contains `{"status":"UP","service":"qherp-api"}`.

- [ ] **步骤 3：执行浏览器主路径**

使用真实浏览器自动化完成销售订单创建、编辑、确认、部分销售出库过账、库存余额扣减、`SALES_SHIPMENT` 库存流水追溯、第二次销售出库过账、订单进入 `SHIPPED`、出库到订单追溯和订单到出库追溯。

- [ ] **步骤 4：执行权限路径**

验证管理员、销售员、仓库角色、只读用户、无权限用户和未登录用户，确认前后端权限一致。

- [ ] **步骤 5：执行异常路径**

验证停用客户创建/更新/确认订单、不可销售物料、停用物料、停用仓库、未确认订单出库、已取消/已关闭/已全部出库订单出库、零或负数量、超精度、超出库、库存不足、重复过账、已过账编辑和来源行不匹配。

- [ ] **步骤 6：记录结果**

将执行结果、账号、数据标识、命令输出和缺陷记录追加到 `docs/testing/sales-management-test-plan.md`，如创建独立执行记录则同步记录。

## Task 10: 浏览器视觉分析

**角色：** UI 设计师承担视觉审查，测试承担截图执行和记录，前端开发承担修复。

**文件：**
- 新建： `docs/testing/sales-management-visual-audit/notes.md`
- 新建： `docs/testing/sales-management-visual-audit/*.png`
- 更新： `docs/testing/sales-management-test-plan.md`

- [ ] **步骤 1：采集视觉证据**

采集 `docs/testing/sales-management-test-plan.md` 中列出的截图，覆盖桌面和窄屏视口状态。

- [ ] **步骤 2：编写视觉记录**

创建 `notes.md`，记录截图文件名、视口尺寸、页面、账号、发现问题、处理结果和最终结论。

- [ ] **步骤 3：验证视觉资产**

运行：

```powershell
Get-ChildItem -LiteralPath 'docs/testing/sales-management-visual-audit' -Filter '*.png' | Select-Object Name,Length
```

预期：

- `docs/testing/sales-management-test-plan.md` 截图清单中的全部 PNG 文件均存在且非 0 长度。
- 截图清单必须与 `docs/superpowers/specs/2026-07-04-sales-management-design.md` 和 `docs/testing/sales-management-test-plan.md` 保持一致；如文件名、页面状态或权限账号不一致，必须先修正记录口径或重新采集。
- 每张截图保存后必须做视觉检查，确认不是空白、加载中、错页、错误窗口、错误账号、错误权限状态或裁切失真。
- 每张截图必须确认页面、账号、业务状态与截图清单一致；不一致时重新截图，并在 `notes.md` 记录处理结果。

## Task 11: 最终质量门和阶段复审

**角色：** 测试执行最终质量门，产品经理做规格复审，后端开发做后端代码质量审查，前端开发做前端代码质量审查，UI 设计师做视觉复审，主代理做最终验收判断。

**文件：**
- 销售实现期间变更的全部文件
- 更新： `docs/testing/sales-management-test-plan.md`
- 更新： `docs/testing/sales-management-defects.md` if applicable

- [ ] **步骤 1：运行最终后端测试**

Run backend full test command from Task 4.

- [ ] **步骤 2：运行最终前端测试、类型检查和构建**

运行 Task 8 中的前端命令。

- [ ] **步骤 3：运行空白检查**

运行：

```powershell
git diff --check
```

预期： exits `0` with no whitespace errors.

- [ ] **步骤 4：确认没有未解决阻断缺陷**

检查销售缺陷记录。阻断缺陷必须修复并复验，严重缺陷必须修复或形成明确风险决策。

- [ ] **步骤 5：固定角色最终复审**

Use `/goal` to dispatch:

- 产品经理：规格复审。
- 后端开发：后端代码质量审查。
- 前端开发：前端代码质量审查。
- UI 设计师：视觉分析复审。
- 测试：最终验收证据复核。

- [ ] **步骤 6：主代理交付判断**

主代理复核当前证据后，再判断销售阶段是否可合入或交付浏览器验收。没有新鲜命令输出和当前浏览器证据时，不得声称完成。

## 自检记录

- 规格覆盖： this plan covers sales order, sales shipment, inventory posting, permissions, audit, frontend views, inventory movement source routing, tests, browser acceptance, visual analysis, and final quality gates.
- 范围检查： this plan excludes receivable, invoice, payment, tax, revenue recognition, formal voucher, sales return, logistics, quotation, contract, price policy, credit limit, approval, reporting, multi-currency, multi-organization, multi-company, multi-ledger, and multi-tenant capabilities.
- 类型一致性： route paths, permission codes, status names, sellable material rules, customer disabled rules, and inventory movement type match `docs/api/sales-management-api.md` and `docs/superpowers/specs/2026-07-04-sales-management-design.md`.
- 审查边界： implementation tasks are assigned to fixed project roles; main agent remains responsible for coordination and acceptance judgment.
