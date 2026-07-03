# 生产执行基础能力实施计划

> **面向代理执行者：** 必须使用 `superpowers:subagent-driven-development` 或 `superpowers:executing-plans` 按任务逐项实施。步骤使用复选框语法跟踪，实施过程中严格对照 `docs/tasks/008-production-execution-foundation.md`、`docs/api/production-execution-api.md`、`docs/testing/production-execution-test-plan.md` 和本计划，不得自行扩大范围。

**目标：** 实现生产工单、领料、报工和完工入库基础能力，使生产执行过程可追溯、可鉴权、可审计，并与库存余额和库存变动流水保持一致。

**架构：** 后端沿用 Spring Boot、JdbcTemplate、PostgreSQL、Flyway、统一 `ApiResponse`、接口鉴权和审计模式，在 `com.qherp.api.system.production` 包内实现生产执行业务。前端沿用 Vue 3、TypeScript、Vue Router、Element Plus 和既有后台页面模式，在 `/production/work-orders` 下建立工单上下文页面。库存变动继续通过 `inv_stock_balance` 和 `inv_stock_movement` 记录，生产领料和完工入库只扩展库存流水来源类型，不旁路修改库存。

**技术栈：** Java 21、Spring Boot、JdbcTemplate、PostgreSQL、Flyway、JUnit、Testcontainers、Vue 3、TypeScript、Vitest、Element Plus、Codex 内置浏览器视觉验收。

---

## 文件结构

### 后端新增文件

- `apps/api/src/main/resources/db/migration/V6__production_execution_schema.sql`：生产工单、用料快照、领料、报工、完工入库表结构，并扩展库存流水类型约束。
- `apps/api/src/main/java/com/qherp/api/system/production/ProductionWorkOrderStatus.java`：生产工单状态枚举。
- `apps/api/src/main/java/com/qherp/api/system/production/ProductionDocumentStatus.java`：生产单据状态枚举。
- `apps/api/src/main/java/com/qherp/api/system/production/ProductionAdminController.java`：生产执行接口入口。
- `apps/api/src/main/java/com/qherp/api/system/production/ProductionAdminService.java`：工单、BOM 快照、领料、报工、完工入库、库存过账、审计和查询。
- `apps/api/src/test/java/com/qherp/api/system/production/ProductionAdminControllerTests.java`：生产执行后端集成测试。

### 后端修改文件

- `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`：增加生产执行错误码。
- `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryMovementType.java`：增加 `PRODUCTION_ISSUE`、`PRODUCTION_RECEIPT`。
- `apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`：增加生产菜单和操作权限种子。
- `apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`：增加生产接口权限映射。
- `apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`：补生产权限种子断言。
- `apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`：补生产接口鉴权断言。

### 前端新增文件

- `apps/web/src/shared/api/productionApi.ts`：生产执行 API 类型与请求封装。
- `apps/web/src/shared/api/productionApi.spec.ts`：生产执行 API 封装测试。
- `apps/web/src/modules/production/productionPageHelpers.ts`：状态、类型、数量和错误文案辅助函数。
- `apps/web/src/modules/production/ProductionWorkOrderStatusTag.vue`：工单状态标签。
- `apps/web/src/modules/production/ProductionDocumentStatusTag.vue`：生产单据状态标签。
- `apps/web/src/modules/production/ProductionWorkOrderListView.vue`：生产工单列表页。
- `apps/web/src/modules/production/ProductionWorkOrderFormView.vue`：生产工单创建和编辑页。
- `apps/web/src/modules/production/ProductionWorkOrderDetailView.vue`：生产工单详情和追溯页。
- `apps/web/src/modules/production/ProductionMaterialIssueView.vue`：工单领料页。
- `apps/web/src/modules/production/ProductionWorkReportView.vue`：工单报工页。
- `apps/web/src/modules/production/ProductionCompletionReceiptView.vue`：工单完工入库页。
- `apps/web/src/modules/production/ProductionWorkOrderListView.spec.ts`：工单列表页面测试。
- `apps/web/src/modules/production/ProductionWorkOrderFormView.spec.ts`：工单表单页面测试。
- `apps/web/src/modules/production/ProductionWorkOrderDetailView.spec.ts`：工单详情页面测试。
- `apps/web/src/modules/production/ProductionExecutionForms.spec.ts`：领料、报工和完工入库表单测试。

### 前端修改文件

- `apps/web/src/router/index.ts`：增加生产路由和权限元信息。
- `apps/web/src/router/permissionGuard.spec.ts`：增加生产路由守卫断言。
- `apps/web/src/App.vue`：增加生产管理菜单入口。

### 文档与验收文件

- `docs/testing/production-execution-visual-audit/notes.md`：浏览器视觉分析记录。
- `docs/testing/production-execution-visual-audit/*.png`：浏览器截图证据。
- `docs/tasks/008-production-execution-foundation.md`：阶段实现后追加执行记录。
- `docs/testing/production-execution-test-plan.md`：阶段实现后追加执行记录。

## 实施约束

- 严格按生产执行任务文档、接口契约、测试计划和本计划执行。
- 本阶段只实现生产工单、工单 BOM 快照、领料、报工和完工入库。
- 不实现高级排产、工艺路线、工序流转、批次、序列号、库位、条码、倒冲、退料、补料、返工、委外、采购销售联动、审批流、正式成本核算、多单位换算、多组织、多公司或多租户。
- 工单发布时必须生成 BOM 用料快照，已发布工单不回读可变 BOM 明细作为执行依据。
- 一期不启用库存占用锁定；领料过账实时扣减库存且库存不得为负。
- 一期不允许超领、超报、超入。
- 报工不直接修改库存；完工入库数量不得超过累计合格报工数量。
- 所有写接口必须后端鉴权，前端按钮隐藏不能替代接口鉴权。
- 每个阶段提交前必须运行对应验证命令；测试、构建、启动、浏览器访问或视觉分析失败时先定位并修复，不跳过验证。
- 浏览器验收阶段必须引入视觉分析。没有截图证据、没有分析结论、截图失真未复拍、视觉问题影响核心操作或数据识别时，不得进入主分支阶段验收。

## 任务 1：后端迁移、枚举、错误码和权限

**文件：**

- 创建：`apps/api/src/main/resources/db/migration/V6__production_execution_schema.sql`
- 创建：`apps/api/src/main/java/com/qherp/api/system/production/ProductionWorkOrderStatus.java`
- 创建：`apps/api/src/main/java/com/qherp/api/system/production/ProductionDocumentStatus.java`
- 修改：`apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryMovementType.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- 修改：`apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`

- [ ] **步骤 1：创建生产执行数据库迁移**

写入 `apps/api/src/main/resources/db/migration/V6__production_execution_schema.sql`，必须包含：

```sql
alter table inv_stock_movement drop constraint ck_inv_stock_movement_type;

alter table inv_stock_movement add constraint ck_inv_stock_movement_type
	check (movement_type in (
		'OPENING',
		'ADJUSTMENT_INCREASE',
		'ADJUSTMENT_DECREASE',
		'PRODUCTION_ISSUE',
		'PRODUCTION_RECEIPT'
	));

create table mfg_work_order (
	id bigserial primary key,
	work_order_no varchar(64) not null,
	product_material_id bigint not null,
	bom_id bigint not null,
	planned_quantity numeric(18, 6) not null,
	reported_quantity numeric(18, 6) not null default 0,
	qualified_quantity numeric(18, 6) not null default 0,
	defective_quantity numeric(18, 6) not null default 0,
	received_quantity numeric(18, 6) not null default 0,
	issue_warehouse_id bigint not null,
	receipt_warehouse_id bigint not null,
	planned_start_date date not null,
	planned_finish_date date not null,
	status varchar(32) not null,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	released_by varchar(64),
	released_at timestamptz,
	completed_by varchar(64),
	completed_at timestamptz,
	cancelled_by varchar(64),
	cancelled_at timestamptz,
	version bigint not null default 0,
	constraint uk_mfg_work_order_no unique (work_order_no),
	constraint fk_mfg_work_order_product foreign key (product_material_id) references mst_material (id),
	constraint fk_mfg_work_order_bom foreign key (bom_id) references mfg_bom (id),
	constraint fk_mfg_work_order_issue_warehouse foreign key (issue_warehouse_id) references mst_warehouse (id),
	constraint fk_mfg_work_order_receipt_warehouse foreign key (receipt_warehouse_id) references mst_warehouse (id),
	constraint ck_mfg_work_order_status check (status in ('DRAFT', 'RELEASED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
	constraint ck_mfg_work_order_planned_quantity_positive check (planned_quantity > 0),
	constraint ck_mfg_work_order_reported_non_negative check (reported_quantity >= 0),
	constraint ck_mfg_work_order_qualified_non_negative check (qualified_quantity >= 0),
	constraint ck_mfg_work_order_defective_non_negative check (defective_quantity >= 0),
	constraint ck_mfg_work_order_received_non_negative check (received_quantity >= 0)
);
```

同一迁移继续写入：

```sql
create table mfg_work_order_material (
	id bigserial primary key,
	work_order_id bigint not null,
	line_no integer not null,
	bom_item_id bigint not null,
	material_id bigint not null,
	unit_id bigint not null,
	required_quantity numeric(18, 6) not null,
	issued_quantity numeric(18, 6) not null default 0,
	loss_rate numeric(9, 6) not null default 0,
	remark varchar(500),
	created_at timestamptz not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint fk_mfg_work_order_material_order foreign key (work_order_id) references mfg_work_order (id) on delete cascade,
	constraint fk_mfg_work_order_material_bom_item foreign key (bom_item_id) references mfg_bom_item (id),
	constraint fk_mfg_work_order_material_material foreign key (material_id) references mst_material (id),
	constraint fk_mfg_work_order_material_unit foreign key (unit_id) references mst_unit (id),
	constraint uk_mfg_work_order_material_line unique (work_order_id, line_no),
	constraint uk_mfg_work_order_material_bom_item unique (work_order_id, bom_item_id),
	constraint ck_mfg_work_order_material_required_positive check (required_quantity > 0),
	constraint ck_mfg_work_order_material_issued_non_negative check (issued_quantity >= 0),
	constraint ck_mfg_work_order_material_loss_rate_range check (loss_rate >= 0 and loss_rate < 1)
);

create table mfg_material_issue (
	id bigserial primary key,
	issue_no varchar(64) not null,
	work_order_id bigint not null,
	status varchar(32) not null,
	business_date date not null,
	reason varchar(200) not null,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	posted_by varchar(64),
	posted_at timestamptz,
	version bigint not null default 0,
	constraint uk_mfg_material_issue_no unique (issue_no),
	constraint fk_mfg_material_issue_order foreign key (work_order_id) references mfg_work_order (id),
	constraint ck_mfg_material_issue_status check (status in ('DRAFT', 'POSTED'))
);

create table mfg_material_issue_line (
	id bigserial primary key,
	issue_id bigint not null,
	work_order_material_id bigint not null,
	line_no integer not null,
	warehouse_id bigint not null,
	material_id bigint not null,
	unit_id bigint not null,
	quantity numeric(18, 6) not null,
	before_quantity numeric(18, 6),
	after_quantity numeric(18, 6),
	remark varchar(500),
	created_at timestamptz not null,
	updated_at timestamptz not null,
	constraint fk_mfg_material_issue_line_issue foreign key (issue_id) references mfg_material_issue (id) on delete cascade,
	constraint fk_mfg_material_issue_line_order_material foreign key (work_order_material_id) references mfg_work_order_material (id),
	constraint fk_mfg_material_issue_line_warehouse foreign key (warehouse_id) references mst_warehouse (id),
	constraint fk_mfg_material_issue_line_material foreign key (material_id) references mst_material (id),
	constraint fk_mfg_material_issue_line_unit foreign key (unit_id) references mst_unit (id),
	constraint uk_mfg_material_issue_line_no unique (issue_id, line_no),
	constraint uk_mfg_material_issue_line_order_material unique (issue_id, work_order_material_id),
	constraint ck_mfg_material_issue_line_quantity_positive check (quantity > 0),
	constraint ck_mfg_material_issue_line_before_non_negative check (before_quantity is null or before_quantity >= 0),
	constraint ck_mfg_material_issue_line_after_non_negative check (after_quantity is null or after_quantity >= 0)
);

create table mfg_work_report (
	id bigserial primary key,
	report_no varchar(64) not null,
	work_order_id bigint not null,
	status varchar(32) not null,
	business_date date not null,
	qualified_quantity numeric(18, 6) not null,
	defective_quantity numeric(18, 6) not null,
	reporter_name varchar(64) not null,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	posted_by varchar(64),
	posted_at timestamptz,
	version bigint not null default 0,
	constraint uk_mfg_work_report_no unique (report_no),
	constraint fk_mfg_work_report_order foreign key (work_order_id) references mfg_work_order (id),
	constraint ck_mfg_work_report_status check (status in ('DRAFT', 'POSTED')),
	constraint ck_mfg_work_report_qualified_non_negative check (qualified_quantity >= 0),
	constraint ck_mfg_work_report_defective_non_negative check (defective_quantity >= 0),
	constraint ck_mfg_work_report_total_positive check ((qualified_quantity + defective_quantity) > 0)
);

create table mfg_completion_receipt (
	id bigserial primary key,
	receipt_no varchar(64) not null,
	work_order_id bigint not null,
	status varchar(32) not null,
	business_date date not null,
	receipt_warehouse_id bigint not null,
	quantity numeric(18, 6) not null,
	before_quantity numeric(18, 6),
	after_quantity numeric(18, 6),
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	posted_by varchar(64),
	posted_at timestamptz,
	version bigint not null default 0,
	constraint uk_mfg_completion_receipt_no unique (receipt_no),
	constraint fk_mfg_completion_receipt_order foreign key (work_order_id) references mfg_work_order (id),
	constraint fk_mfg_completion_receipt_warehouse foreign key (receipt_warehouse_id) references mst_warehouse (id),
	constraint ck_mfg_completion_receipt_status check (status in ('DRAFT', 'POSTED')),
	constraint ck_mfg_completion_receipt_quantity_positive check (quantity > 0),
	constraint ck_mfg_completion_receipt_before_non_negative check (before_quantity is null or before_quantity >= 0),
	constraint ck_mfg_completion_receipt_after_non_negative check (after_quantity is null or after_quantity >= 0)
);

create index idx_mfg_work_order_status on mfg_work_order (status, updated_at desc);
create index idx_mfg_work_order_product on mfg_work_order (product_material_id);
create index idx_mfg_work_order_material_order on mfg_work_order_material (work_order_id);
create index idx_mfg_material_issue_order on mfg_material_issue (work_order_id, updated_at desc);
create index idx_mfg_work_report_order on mfg_work_report (work_order_id, updated_at desc);
create index idx_mfg_completion_receipt_order on mfg_completion_receipt (work_order_id, updated_at desc);
```

- [ ] **步骤 2：创建枚举**

创建 `ProductionWorkOrderStatus.java`：

```java
package com.qherp.api.system.production;

public enum ProductionWorkOrderStatus {
	DRAFT,
	RELEASED,
	IN_PROGRESS,
	COMPLETED,
	CANCELLED
}
```

创建 `ProductionDocumentStatus.java`：

```java
package com.qherp.api.system.production;

public enum ProductionDocumentStatus {
	DRAFT,
	POSTED
}
```

- [ ] **步骤 3：扩展库存变动枚举**

修改 `InventoryMovementType.java`，加入：

```java
PRODUCTION_ISSUE,
PRODUCTION_RECEIPT
```

保留已有 `OPENING`、`ADJUSTMENT_INCREASE`、`ADJUSTMENT_DECREASE`。

- [ ] **步骤 4：增加生产错误码**

在 `ApiErrorCode.java` 的库存错误码之后、`CONFLICT` 之前增加：

```java
PRODUCTION_WORK_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "生产工单不存在"),
PRODUCTION_WORK_ORDER_STATUS_INVALID(HttpStatus.CONFLICT, "生产工单状态不允许当前操作"),
PRODUCTION_WORK_ORDER_HAS_POSTED_BUSINESS(HttpStatus.CONFLICT, "生产工单已有过账业务，不能取消"),
PRODUCTION_PRODUCT_MATERIAL_INVALID(HttpStatus.BAD_REQUEST, "产品物料不存在、停用或类型不允许"),
PRODUCTION_BOM_INVALID(HttpStatus.BAD_REQUEST, "生产 BOM 不存在、未启用或父项物料不匹配"),
PRODUCTION_BOM_EMPTY_ITEMS(HttpStatus.BAD_REQUEST, "生产 BOM 明细不能为空"),
PRODUCTION_WAREHOUSE_INVALID(HttpStatus.BAD_REQUEST, "生产仓库不存在或已停用"),
PRODUCTION_MATERIAL_INVALID(HttpStatus.BAD_REQUEST, "生产用料物料不存在或已停用"),
PRODUCTION_UNIT_INVALID(HttpStatus.BAD_REQUEST, "生产单位不存在或已停用"),
PRODUCTION_QUANTITY_INVALID(HttpStatus.BAD_REQUEST, "生产数量不正确"),
PRODUCTION_ISSUE_NOT_FOUND(HttpStatus.NOT_FOUND, "生产领料单不存在"),
PRODUCTION_ISSUE_EMPTY_LINES(HttpStatus.BAD_REQUEST, "生产领料明细不能为空"),
PRODUCTION_ISSUE_EXCEEDS_REQUIRED(HttpStatus.CONFLICT, "累计领料超过应领数量"),
PRODUCTION_STOCK_NOT_ENOUGH(HttpStatus.CONFLICT, "生产领料库存不足"),
PRODUCTION_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "生产报工单不存在"),
PRODUCTION_REPORT_EXCEEDS_PLAN(HttpStatus.CONFLICT, "累计报工超过计划数量"),
PRODUCTION_RECEIPT_NOT_FOUND(HttpStatus.NOT_FOUND, "完工入库单不存在"),
PRODUCTION_RECEIPT_EXCEEDS_REPORTED(HttpStatus.CONFLICT, "累计入库超过累计合格报工数量"),
PRODUCTION_DOCUMENT_POSTED_IMMUTABLE(HttpStatus.CONFLICT, "已过账生产单据不可编辑"),
PRODUCTION_DUPLICATE_POST(HttpStatus.CONFLICT, "生产单据已过账或重复过账"),
PRODUCTION_MOVEMENT_SOURCE_DUPLICATED(HttpStatus.CONFLICT, "生产来源明细已生成库存变动"),
```

- [ ] **步骤 5：增加生产权限种子**

在 `AccountPermissionInitializer.java` 的库存权限之后增加：

```java
new PermissionSeed("production", "生产管理", SystemPermissionType.MENU, null, "/production/work-orders", null, null, 400),
new PermissionSeed("production:work-order:view", "查看生产工单", SystemPermissionType.ACTION, "production", "/production/work-orders", "GET", "/api/admin/production/work-orders/**", 401),
new PermissionSeed("production:work-order:create", "创建生产工单", SystemPermissionType.ACTION, "production", "/production/work-orders", "POST", "/api/admin/production/work-orders", 402),
new PermissionSeed("production:work-order:update", "更新生产工单", SystemPermissionType.ACTION, "production", "/production/work-orders", "PUT", "/api/admin/production/work-orders/{id}", 403),
new PermissionSeed("production:work-order:release", "发布生产工单", SystemPermissionType.ACTION, "production", "/production/work-orders", "PUT", "/api/admin/production/work-orders/{id}/release", 404),
new PermissionSeed("production:work-order:complete", "完成生产工单", SystemPermissionType.ACTION, "production", "/production/work-orders", "PUT", "/api/admin/production/work-orders/{id}/complete", 405),
new PermissionSeed("production:work-order:cancel", "取消生产工单", SystemPermissionType.ACTION, "production", "/production/work-orders", "PUT", "/api/admin/production/work-orders/{id}/cancel", 406),
new PermissionSeed("production:issue:view", "查看生产领料", SystemPermissionType.ACTION, "production", "/production/work-orders", "GET", "/api/admin/production/work-orders/{id}/material-issues/**", 407),
new PermissionSeed("production:issue:create", "创建生产领料", SystemPermissionType.ACTION, "production", "/production/work-orders", "POST", "/api/admin/production/work-orders/{id}/material-issues", 408),
new PermissionSeed("production:issue:update", "更新生产领料", SystemPermissionType.ACTION, "production", "/production/work-orders", "PUT", "/api/admin/production/work-orders/{id}/material-issues/{issueId}", 409),
new PermissionSeed("production:issue:post", "过账生产领料", SystemPermissionType.ACTION, "production", "/production/work-orders", "PUT", "/api/admin/production/work-orders/{id}/material-issues/{issueId}/post", 410),
new PermissionSeed("production:report:view", "查看生产报工", SystemPermissionType.ACTION, "production", "/production/work-orders", "GET", "/api/admin/production/work-orders/{id}/reports/**", 411),
new PermissionSeed("production:report:create", "创建生产报工", SystemPermissionType.ACTION, "production", "/production/work-orders", "POST", "/api/admin/production/work-orders/{id}/reports", 412),
new PermissionSeed("production:report:update", "更新生产报工", SystemPermissionType.ACTION, "production", "/production/work-orders", "PUT", "/api/admin/production/work-orders/{id}/reports/{reportId}", 413),
new PermissionSeed("production:report:post", "过账生产报工", SystemPermissionType.ACTION, "production", "/production/work-orders", "PUT", "/api/admin/production/work-orders/{id}/reports/{reportId}/post", 414),
new PermissionSeed("production:receipt:view", "查看完工入库", SystemPermissionType.ACTION, "production", "/production/work-orders", "GET", "/api/admin/production/work-orders/{id}/completion-receipts/**", 415),
new PermissionSeed("production:receipt:create", "创建完工入库", SystemPermissionType.ACTION, "production", "/production/work-orders", "POST", "/api/admin/production/work-orders/{id}/completion-receipts", 416),
new PermissionSeed("production:receipt:update", "更新完工入库", SystemPermissionType.ACTION, "production", "/production/work-orders", "PUT", "/api/admin/production/work-orders/{id}/completion-receipts/{receiptId}", 417),
new PermissionSeed("production:receipt:post", "过账完工入库", SystemPermissionType.ACTION, "production", "/production/work-orders", "PUT", "/api/admin/production/work-orders/{id}/completion-receipts/{receiptId}/post", 418)
```

- [ ] **步骤 6：增加生产接口权限映射**

在 `PermissionAuthorizationManager.java` 中新增 `productionPermissionCode(method, path)`，并在 `permissionCode` 中库存映射之后调用。映射规则必须覆盖：

- `GET /api/admin/production/work-orders` 和 `GET /api/admin/production/work-orders/{id}` -> `production:work-order:view`
- `POST /api/admin/production/work-orders` -> `production:work-order:create`
- `PUT /api/admin/production/work-orders/{id}` -> `production:work-order:update`
- `PUT /api/admin/production/work-orders/{id}/release` -> `production:work-order:release`
- `PUT /api/admin/production/work-orders/{id}/complete` -> `production:work-order:complete`
- `PUT /api/admin/production/work-orders/{id}/cancel` -> `production:work-order:cancel`
- 工单内 `material-issues`、`reports`、`completion-receipts` 的 `GET/POST/PUT/post` 分别映射到接口契约中的权限点。

- [ ] **步骤 7：补权限测试**

在 `AccountPermissionInitializerTests.java` 中断言系统管理员拥有 `production` 菜单和所有生产操作权限。

在 `PermissionAuthorizationTests.java` 中新增生产接口样例，至少覆盖：

- 生产工单列表需要 `production:work-order:view`。
- 发布工单需要 `production:work-order:release`。
- 领料过账需要 `production:issue:post`。
- 报工过账需要 `production:report:post`。
- 完工入库过账需要 `production:receipt:post`。

- [ ] **步骤 8：运行后端权限与迁移相关测试**

Run:

```powershell
cd apps/api
.\mvnw.cmd -q -Dtest=AccountPermissionInitializerTests,PermissionAuthorizationTests test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **步骤 9：提交任务 1**

Run:

```powershell
git add apps/api/src/main/resources/db/migration/V6__production_execution_schema.sql apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java apps/api/src/main/java/com/qherp/api/system/inventory/InventoryMovementType.java apps/api/src/main/java/com/qherp/api/system/production apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java
git commit -m "建立生产执行数据模型与权限"
```

## 任务 2：后端生产执行服务和控制器

**文件：**

- 创建：`apps/api/src/main/java/com/qherp/api/system/production/ProductionAdminController.java`
- 创建：`apps/api/src/main/java/com/qherp/api/system/production/ProductionAdminService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryMovementType.java`

- [ ] **步骤 1：创建控制器接口**

`ProductionAdminController` 使用 `@RestController` 和 `@RequestMapping("/api/admin/production")`，暴露：

- `GET /work-orders`
- `GET /work-orders/{id}`
- `POST /work-orders`
- `PUT /work-orders/{id}`
- `PUT /work-orders/{id}/release`
- `PUT /work-orders/{id}/complete`
- `PUT /work-orders/{id}/cancel`
- `GET /work-orders/{workOrderId}/material-issues`
- `GET /work-orders/{workOrderId}/material-issues/{id}`
- `POST /work-orders/{workOrderId}/material-issues`
- `PUT /work-orders/{workOrderId}/material-issues/{id}`
- `PUT /work-orders/{workOrderId}/material-issues/{id}/post`
- `GET /work-orders/{workOrderId}/reports`
- `GET /work-orders/{workOrderId}/reports/{id}`
- `POST /work-orders/{workOrderId}/reports`
- `PUT /work-orders/{workOrderId}/reports/{id}`
- `PUT /work-orders/{workOrderId}/reports/{id}/post`
- `GET /work-orders/{workOrderId}/completion-receipts`
- `GET /work-orders/{workOrderId}/completion-receipts/{id}`
- `POST /work-orders/{workOrderId}/completion-receipts`
- `PUT /work-orders/{workOrderId}/completion-receipts/{id}`
- `PUT /work-orders/{workOrderId}/completion-receipts/{id}/post`

所有写接口传入 `@AuthenticationPrincipal CurrentUser currentUser` 和 `HttpServletRequest servletRequest`，返回 `ApiResponse.ok(...)`。

- [ ] **步骤 2：创建服务查询能力**

`ProductionAdminService` 构造函数注入 `JdbcTemplate` 和 `AuditService`。先实现只读查询：

- `workOrders(keyword, status, productMaterialId, dateFrom, dateTo, page, pageSize)`
- `workOrder(id)`
- `materialIssues(workOrderId)`
- `materialIssue(workOrderId, id)`
- `reports(workOrderId)`
- `report(workOrderId, id)`
- `completionReceipts(workOrderId)`
- `completionReceipt(workOrderId, id)`

所有分页统一使用现有 `PageResponse.of(items, page, limit(pageSize), total)` 模式。

- [ ] **步骤 3：实现工单草稿创建和更新**

实现 `createWorkOrder` 和 `updateWorkOrder`：

- 创建状态固定为 `DRAFT`。
- 工单号使用 `MFG-WO-yyyyMMddHHmmssSSS-序号`，最多重试 3 次。
- 校验产品物料启用，类型为 `FINISHED_GOOD` 或 `SEMI_FINISHED`。
- 校验 BOM 为 `ENABLED`，且 `parent_material_id` 等于产品物料。
- 校验计划数量大于 0、精度不超过 6、小数整数位不超过 12。
- 校验计划开工日期和计划完工日期存在，且完工日期不早于开工日期。
- 校验领料仓库和入库仓库启用。
- 只有 `DRAFT` 工单允许更新。

- [ ] **步骤 4：实现工单发布和 BOM 快照**

实现 `releaseWorkOrder`：

- 锁定工单行。
- 只允许 `DRAFT` 发布。
- 重新校验产品、BOM、BOM 明细、仓库、单位和子项物料状态。
- 从 `mfg_bom_item` 读取明细，生成 `mfg_work_order_material`。
- 应领数量按 `plannedQuantity / baseQuantity * itemQuantity * (1 + lossRate)` 计算，结果保留数据库数值精度。
- 更新工单状态为 `RELEASED`、记录发布人和发布时间。
- 审计动作：`MFG_WORK_ORDER_RELEASE`。

- [ ] **步骤 5：实现领料草稿和过账**

实现 `createMaterialIssue`、`updateMaterialIssue`、`postMaterialIssue`：

- 工单必须为 `RELEASED` 或 `IN_PROGRESS`。
- 领料明细必须引用当前工单的用料快照行。
- 本次领料数量大于 0。
- 本次领料加已领数量不得超过应领数量。
- 过账时锁定领料单、工单、用料快照和库存余额。
- 库存不足返回 `PRODUCTION_STOCK_NOT_ENOUGH`。
- 写 `inv_stock_movement`：
  - `movement_type = PRODUCTION_ISSUE`
  - `direction = OUT`
  - `source_type = PRODUCTION_MATERIAL_ISSUE`
  - `source_id = 领料单 id`
  - `source_line_id = 领料明细 id`
- 更新库存余额、用料快照已领数量、领料单状态和工单状态。
- 审计动作：`MFG_MATERIAL_ISSUE_POST`。

- [ ] **步骤 6：实现报工草稿和过账**

实现 `createReport`、`updateReport`、`postReport`：

- 工单必须为 `RELEASED` 或 `IN_PROGRESS`。
- 合格数量和不良数量必须大于等于 0，合计必须大于 0。
- 累计报工数量不得超过计划数量。
- 过账时锁定报工单和工单。
- 更新工单 `reported_quantity`、`qualified_quantity`、`defective_quantity`。
- 不写库存流水。
- 审计动作：`MFG_WORK_REPORT_POST`。

- [ ] **步骤 7：实现完工入库草稿和过账**

实现 `createCompletionReceipt`、`updateCompletionReceipt`、`postCompletionReceipt`：

- 工单必须为 `RELEASED` 或 `IN_PROGRESS`。
- 入库仓库必须启用。
- 入库数量大于 0。
- 累计入库不得超过累计合格报工数量。
- 过账时锁定入库单、工单和库存余额。
- 写 `inv_stock_movement`：
  - `movement_type = PRODUCTION_RECEIPT`
  - `direction = IN`
  - `source_type = PRODUCTION_COMPLETION_RECEIPT`
  - `source_id = 入库单 id`
  - `source_line_id = 入库单 id`
- 更新库存余额、工单累计入库数量、入库单状态和工单状态。
- 审计动作：`MFG_COMPLETION_RECEIPT_POST`。

- [ ] **步骤 8：实现完成和取消**

实现 `completeWorkOrder`：

- 只允许 `RELEASED` 或 `IN_PROGRESS`。
- 累计入库数量必须等于计划数量。
- 不存在未过账的领料、报工或完工入库草稿。
- 更新状态为 `COMPLETED`，写审计 `MFG_WORK_ORDER_COMPLETE`。

实现 `cancelWorkOrder`：

- 只允许 `DRAFT` 或无已过账业务的 `RELEASED`。
- 有已过账领料、报工或入库时返回 `PRODUCTION_WORK_ORDER_HAS_POSTED_BUSINESS`。
- 更新状态为 `CANCELLED`，写审计 `MFG_WORK_ORDER_CANCEL`。

- [ ] **步骤 9：运行编译**

Run:

```powershell
cd apps/api
.\mvnw.cmd -q -DskipTests compile
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **步骤 10：提交任务 2**

Run:

```powershell
git add apps/api/src/main/java/com/qherp/api/system/production
git commit -m "实现生产执行后端服务"
```

## 任务 3：后端集成测试

**文件：**

- 创建：`apps/api/src/test/java/com/qherp/api/system/production/ProductionAdminControllerTests.java`

- [ ] **步骤 1：创建测试类骨架**

测试类继承 `PostgresIntegrationTest`，使用 `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "qherp.test.context=production-admin")`。复用现有测试中的 `login`、`csrf`、`exchange`、`get` 辅助模式。

- [ ] **步骤 2：准备测试数据辅助方法**

在测试类中创建辅助方法：

- `createUnit(admin, code, name)`
- `createWarehouse(admin, code, name)`
- `createCategory(admin, code, name)`
- `createMaterial(admin, code, name, materialType, sourceType, categoryId, unitId, status)`
- `createBom(admin, productId, unitId, rawMaterialId, auxiliaryId)`
- `createOpeningStock(admin, warehouseId, materialId, quantity)`
- `createWorkOrder(admin, productId, bomId, issueWarehouseId, receiptWarehouseId, plannedQuantity)`

这些辅助方法必须调用真实 HTTP 接口，不直接写数据库，除非现有测试模式已经使用 `JdbcTemplate` 校验结果。

- [ ] **步骤 3：测试管理员主路径**

新增 `adminCanRunProductionExecutionLifecycle`：

1. 创建单位、仓库、成品、原材料、辅料、启用 BOM 和原材料期初库存。
2. 创建生产工单草稿。
3. 发布工单并断言返回用料快照。
4. 创建并过账领料单。
5. 查询库存余额，断言原材料库存减少。
6. 查询库存流水，断言 `PRODUCTION_ISSUE`、`sourceType=PRODUCTION_MATERIAL_ISSUE`。
7. 创建并过账报工单，断言工单累计报工数量更新。
8. 创建并过账完工入库单。
9. 查询库存余额，断言成品库存增加。
10. 完成工单并断言状态 `COMPLETED`。

- [ ] **步骤 4：测试业务异常**

新增用例覆盖：

- 未启用 BOM 不能发布工单，返回 `PRODUCTION_BOM_INVALID`。
- 库存不足领料返回 `PRODUCTION_STOCK_NOT_ENOUGH`，余额和流水不变化。
- 超领返回 `PRODUCTION_ISSUE_EXCEEDS_REQUIRED`。
- 超报返回 `PRODUCTION_REPORT_EXCEEDS_PLAN`。
- 超入返回 `PRODUCTION_RECEIPT_EXCEEDS_REPORTED`。
- 已过账生产单据不可编辑，返回 `PRODUCTION_DOCUMENT_POSTED_IMMUTABLE`。
- 重复过账返回 `PRODUCTION_DUPLICATE_POST` 或来源重复对应错误。
- 已有过账业务的工单不能取消，返回 `PRODUCTION_WORK_ORDER_HAS_POSTED_BUSINESS`。

- [ ] **步骤 5：测试权限**

新增 `authorizationRulesProtectProductionExecution`：

- 无权限用户访问工单接口返回 `AUTH_FORBIDDEN`。
- 只读角色可查看工单，不能创建、发布、领料、报工或入库。
- 仓库角色可领料和完工入库，不可发布工单。
- 生产角色可报工，不可过账库存单据。
- 缺 CSRF 的生产写接口被拒绝。

- [ ] **步骤 6：测试并发领料**

新增并发用例：

- 期初库存小于两个领料单合计数量。
- 并发过账两个领料单。
- 最终只允许满足库存的一部分成功，库存余额不小于 0，库存流水数量与最终余额一致。

- [ ] **步骤 7：运行生产后端定向测试**

Run:

```powershell
cd apps/api
.\mvnw.cmd -q -Dtest=ProductionAdminControllerTests test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **步骤 8：运行后端全量测试**

Run:

```powershell
cd apps/api
.\mvnw.cmd -q test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **步骤 9：提交任务 3**

Run:

```powershell
git add apps/api/src/test/java/com/qherp/api/system/production/ProductionAdminControllerTests.java
git commit -m "覆盖生产执行后端集成测试"
```

## 任务 4：前端 API、路由和菜单

**文件：**

- 创建：`apps/web/src/shared/api/productionApi.ts`
- 创建：`apps/web/src/shared/api/productionApi.spec.ts`
- 修改：`apps/web/src/router/index.ts`
- 修改：`apps/web/src/router/permissionGuard.spec.ts`
- 修改：`apps/web/src/App.vue`

- [ ] **步骤 1：创建生产 API 类型**

在 `productionApi.ts` 中定义：

- `ProductionWorkOrderStatus`
- `ProductionDocumentStatus`
- `ProductionWorkOrderListParams`
- `ProductionWorkOrderSummaryRecord`
- `ProductionWorkOrderMaterialRecord`
- `ProductionMaterialIssueRecord`
- `ProductionWorkReportRecord`
- `ProductionCompletionReceiptRecord`
- `ProductionWorkOrderDetailRecord`
- `ProductionWorkOrderPayload`
- `ProductionMaterialIssuePayload`
- `ProductionWorkReportPayload`
- `ProductionCompletionReceiptPayload`

数量 payload 使用字符串类型，沿用库存前端精度策略。

- [ ] **步骤 2：创建 API 客户端**

实现 `createProductionApi(options)`，结构沿用 `createInventoryApi`：

- 支持 `baseUrl` 和 `fetcher`。
- 读接口使用 `GET`。
- 写接口先请求 `/api/auth/csrf`。
- 错误抛出 `AccountPermissionApiError`。
- 路径与 `docs/api/production-execution-api.md` 一致。

- [ ] **步骤 3：创建 API 测试**

`productionApi.spec.ts` 必须覆盖：

- 工单列表查询参数构造。
- 创建工单写接口会先取 CSRF。
- 发布、完成、取消工单路径正确。
- 领料、报工、完工入库创建和过账路径正确。
- 错误响应抛出 `AccountPermissionApiError`。

- [ ] **步骤 4：增加路由**

在 `router/index.ts` 中把 `/production` 从占位页改为重定向到 `/production/work-orders`，并增加：

- `/production/work-orders`
- `/production/work-orders/create`
- `/production/work-orders/:id`
- `/production/work-orders/:id/edit`
- `/production/work-orders/:id/material-issues`
- `/production/work-orders/:id/reports`
- `/production/work-orders/:id/completion-receipts`

每条路由配置 `requiresAuth` 和对应 `requiredPermission`。

- [ ] **步骤 5：增加菜单入口**

在 `App.vue` 中增加生产管理菜单，按 `production:work-order:view` 控制显示。菜单文案使用：

- 生产管理
- 生产工单

不增加独立领料、报工、完工入库菜单。

- [ ] **步骤 6：增加路由守卫测试**

在 `permissionGuard.spec.ts` 中增加：

- 有 `production:work-order:view` 可访问工单列表。
- 无权限访问生产工单跳转无权限页。
- 未登录访问生产工单跳转登录页并保留 redirect。

- [ ] **步骤 7：运行前端 API 与路由测试**

Run:

```powershell
cd apps/web
npm test -- productionApi permissionGuard
```

Expected:

```text
Test Files  2 passed
```

- [ ] **步骤 8：提交任务 4**

Run:

```powershell
git add apps/web/src/shared/api/productionApi.ts apps/web/src/shared/api/productionApi.spec.ts apps/web/src/router/index.ts apps/web/src/router/permissionGuard.spec.ts apps/web/src/App.vue
git commit -m "接入生产执行前端路由与接口"
```

## 任务 5：前端页面与组件

**文件：**

- 创建：`apps/web/src/modules/production/productionPageHelpers.ts`
- 创建：`apps/web/src/modules/production/ProductionWorkOrderStatusTag.vue`
- 创建：`apps/web/src/modules/production/ProductionDocumentStatusTag.vue`
- 创建：`apps/web/src/modules/production/ProductionWorkOrderListView.vue`
- 创建：`apps/web/src/modules/production/ProductionWorkOrderFormView.vue`
- 创建：`apps/web/src/modules/production/ProductionWorkOrderDetailView.vue`
- 创建：`apps/web/src/modules/production/ProductionMaterialIssueView.vue`
- 创建：`apps/web/src/modules/production/ProductionWorkReportView.vue`
- 创建：`apps/web/src/modules/production/ProductionCompletionReceiptView.vue`

- [ ] **步骤 1：创建页面辅助函数和状态标签**

`productionPageHelpers.ts` 提供：

- `workOrderStatusLabel(status)`
- `workOrderStatusType(status)`
- `productionDocumentStatusLabel(status)`
- `productionDocumentStatusType(status)`
- `formatProductionQuantity(value)`
- `productionErrorMessage(error)`

状态标签组件只负责接收 `status` 并渲染 `el-tag`。

- [ ] **步骤 2：实现工单列表页**

`ProductionWorkOrderListView.vue` 必须包含：

- 筛选：关键词、状态、计划日期范围。
- 表格：工单号、产品、BOM 版本、计划数量、已报工、已入库、计划日期、状态、操作。
- 操作：详情、编辑、发布、领料、报工、完工入库、完成、取消。
- 操作按钮按权限和状态显示。
- 加载、错误、空状态和分页。
- 操作列设置稳定宽度，避免按钮被截断。

- [ ] **步骤 3：实现工单表单页**

`ProductionWorkOrderFormView.vue` 必须包含：

- 产品物料、BOM、计划数量、计划开工日期、计划完工日期、领料仓库、入库仓库、备注。
- 选择 BOM 后展示 BOM 明细预览。
- 必填、数量、日期校验。
- 保存失败保留输入并显示错误。
- 编辑模式只允许 `DRAFT` 工单进入。

- [ ] **步骤 4：实现工单详情页**

`ProductionWorkOrderDetailView.vue` 必须包含：

- 工单基础信息、状态摘要、计划数量、报工数量、入库数量。
- BOM 用料快照表格。
- 领料记录表格。
- 报工记录表格。
- 完工入库记录表格。
- 库存流水摘要表格。
- 操作区：发布、编辑、领料、报工、完工入库、完成、取消。

- [ ] **步骤 5：实现领料页面**

`ProductionMaterialIssueView.vue` 必须从工单详情加载用料快照，展示：

- 工单摘要。
- 领料日期、原因、备注。
- 明细表格：物料、应领、已领、未领、本次领料、仓库、备注。
- 数量不能大于未领数量。
- 提交成功后返回工单详情。

- [ ] **步骤 6：实现报工页面**

`ProductionWorkReportView.vue` 必须展示：

- 工单摘要。
- 报工日期、合格数量、不良数量、备注。
- 累计报工不能超过计划数量。
- 提交成功后返回工单详情。

- [ ] **步骤 7：实现完工入库页面**

`ProductionCompletionReceiptView.vue` 必须展示：

- 工单摘要。
- 入库日期、入库仓库、入库数量、备注。
- 入库数量不能超过累计合格报工减已入库数量。
- 提交成功后返回工单详情。

- [ ] **步骤 8：运行前端类型检查**

Run:

```powershell
cd apps/web
npm run typecheck
```

Expected:

```text
vue-tsc --noEmit
```

命令退出码必须为 0。

- [ ] **步骤 9：提交任务 5**

Run:

```powershell
git add apps/web/src/modules/production
git commit -m "实现生产执行前端页面"
```

## 任务 6：前端页面测试

**文件：**

- 创建：`apps/web/src/modules/production/ProductionWorkOrderListView.spec.ts`
- 创建：`apps/web/src/modules/production/ProductionWorkOrderFormView.spec.ts`
- 创建：`apps/web/src/modules/production/ProductionWorkOrderDetailView.spec.ts`
- 创建：`apps/web/src/modules/production/ProductionExecutionForms.spec.ts`

- [ ] **步骤 1：测试工单列表**

覆盖：

- 初始加载请求参数。
- 筛选和重置。
- 空状态。
- 加载失败错误提示。
- 不同权限下按钮可见性。
- 不同状态下行操作可见性。

- [ ] **步骤 2：测试工单表单**

覆盖：

- 必填校验。
- 计划数量必须大于 0。
- 计划完工日期不得早于计划开工日期。
- BOM 明细预览。
- 保存失败后错误可见且输入保留。

- [ ] **步骤 3：测试工单详情**

覆盖：

- 状态标签和数量摘要展示。
- BOM 用料快照展示。
- 领料、报工、完工入库记录展示。
- 只读用户操作按钮不可见。
- 已完成工单不展示写操作。

- [ ] **步骤 4：测试领料、报工、完工入库表单**

覆盖：

- 领料数量不能超过未领数量。
- 报工累计不能超过计划数量。
- 入库数量不能超过累计合格报工减已入库数量。
- 后端错误消息可见。
- 提交期间按钮禁用，失败后恢复。

- [ ] **步骤 5：运行生产前端定向测试**

Run:

```powershell
cd apps/web
npm test -- Production productionApi
```

Expected:

```text
Test Files passed
```

- [ ] **步骤 6：运行前端全量测试和构建**

Run:

```powershell
cd apps/web
npm test
npm run build
```

Expected:

```text
Tests passed
built
```

- [ ] **步骤 7：提交任务 6**

Run:

```powershell
git add apps/web/src/modules/production apps/web/src/shared/api/productionApi.spec.ts
git commit -m "覆盖生产执行前端测试"
```

## 任务 7：全量验证与代码质量检查

**文件：**

- 修改：`docs/tasks/008-production-execution-foundation.md`
- 修改：`docs/testing/production-execution-test-plan.md`

- [ ] **步骤 1：运行后端全量测试**

Run:

```powershell
docker run --rm -e TESTCONTAINERS_RYUK_DISABLED=true -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -v "${PWD}:/workspace" -v /var/run/docker.sock:/var/run/docker.sock -w /workspace/apps/api maven:3.9-eclipse-temurin-21 mvn -q test
```

Expected:

```text
Process exited with code 0
```

- [ ] **步骤 2：确认 Testcontainers 无遗留容器**

Run:

```powershell
docker ps -a --filter "label=org.testcontainers" --format "{{.ID}} {{.Image}} {{.Status}}"
```

Expected: 无输出。

- [ ] **步骤 3：运行前端全量测试**

Run:

```powershell
cd apps/web
npm test
```

Expected: 所有测试文件通过。

- [ ] **步骤 4：运行前端构建**

Run:

```powershell
cd apps/web
npm run build
```

Expected: `vue-tsc --noEmit && vite build` 退出码为 0。

- [ ] **步骤 5：运行空白检查**

Run:

```powershell
git diff --check
```

Expected: 无输出。

- [ ] **步骤 6：更新执行记录**

在任务文档和测试计划中追加实际验证记录：

- 后端全量测试结果。
- 前端全量测试结果。
- 前端构建结果。
- 空白检查结果。

- [ ] **步骤 7：提交任务 7**

Run:

```powershell
git add docs/tasks/008-production-execution-foundation.md docs/testing/production-execution-test-plan.md
git commit -m "记录生产执行自动化验证"
```

## 任务 8：本地部署和浏览器功能验收

**文件：**

- 修改：`docs/tasks/008-production-execution-foundation.md`
- 修改：`docs/testing/production-execution-test-plan.md`

- [ ] **步骤 1：启动或确认 PostgreSQL**

Run:

```powershell
docker ps --filter "name=qherp-postgres" --format "{{.Names}} {{.Status}} {{.Ports}}"
```

Expected: `qherp-postgres` 状态 healthy 或运行中。若不存在，使用项目现有本地开发说明启动。

- [ ] **步骤 2：重启后端服务**

若 `qherp-api-local` 已存在，先停止：

```powershell
docker stop qherp-api-local
```

再启动：

```powershell
$api=(Resolve-Path 'apps/api').Path.Replace('\','/')
docker run --rm --name qherp-api-local -p 18080:8080 -e QHERP_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:15432/qherp -e QHERP_DATASOURCE_USERNAME=qherp -e QHERP_DATASOURCE_PASSWORD=qherp_dev_password -v "${api}:/workspace" -v qherp-maven-repo:/root/.m2 -w /workspace maven:3.9.9-eclipse-temurin-21 mvn spring-boot:run
```

Expected: 日志出现 `Started QherpApiApplication`。

- [ ] **步骤 3：后端健康检查**

Run:

```powershell
Invoke-RestMethod http://127.0.0.1:18080/api/health
```

Expected:

```text
status service
------ -------
UP     qherp-api
```

- [ ] **步骤 4：启动前端服务**

Run:

```powershell
cd apps/web
npx vite --host 127.0.0.1
```

Expected: `http://127.0.0.1:5173/` 可访问。

- [ ] **步骤 5：浏览器验收管理员主路径**

使用 `admin / Qherp@2026!` 登录，在浏览器完成：

1. 进入 `/production/work-orders`。
2. 创建成品 A 工单草稿。
3. 发布工单并确认 BOM 快照。
4. 创建并过账领料，确认原材料库存扣减。
5. 创建并过账报工，确认累计报工更新。
6. 创建并过账完工入库，确认成品库存增加。
7. 完成工单。
8. 在库存流水页面确认 `PRODUCTION_ISSUE` 和 `PRODUCTION_RECEIPT` 来源。

- [ ] **步骤 6：浏览器验收异常和权限**

覆盖：

- 库存不足领料。
- 超领、超报、超入。
- 停用 BOM、停用物料或停用仓库。
- 只读用户页面和接口权限。
- 无权限用户接口拒绝。

- [ ] **步骤 7：更新执行记录**

在任务文档和测试计划中记录：

- 本地服务地址。
- 健康检查结果。
- 管理员主路径结果。
- 异常和权限验收结果。

- [ ] **步骤 8：提交任务 8**

Run:

```powershell
git add docs/tasks/008-production-execution-foundation.md docs/testing/production-execution-test-plan.md
git commit -m "记录生产执行浏览器功能验收"
```

## 任务 9：浏览器视觉分析与阶段验收准备

**文件：**

- 创建：`docs/testing/production-execution-visual-audit/notes.md`
- 创建：`docs/testing/production-execution-visual-audit/*.png`
- 修改：`docs/tasks/008-production-execution-foundation.md`
- 修改：`docs/testing/production-execution-test-plan.md`

- [ ] **步骤 1：建立视觉分析目录**

Run:

```powershell
New-Item -ItemType Directory -Force docs/testing/production-execution-visual-audit
```

- [ ] **步骤 2：采集桌面截图**

使用 Codex 内置浏览器或可信浏览器截图，采集并保存：

- `01-production-work-order-list-desktop.png`
- `02-production-work-order-result-desktop.png`
- `03-production-work-order-empty-desktop.png`
- `04-production-work-order-form-desktop.png`
- `05-production-work-order-form-error-desktop.png`
- `06-production-bom-invalid-desktop.png`
- `07-production-work-order-detail-desktop.png`
- `08-production-material-issue-desktop.png`
- `09-production-stock-not-enough-desktop.png`
- `10-production-work-report-desktop.png`
- `11-production-completion-receipt-desktop.png`
- `12-production-readonly-permission-desktop.png`
- `13-production-traceability-desktop.png`

- [ ] **步骤 3：采集窄屏截图**

使用 `390x844` 视口采集：

- `14-production-work-order-mobile.png`
- `15-production-detail-mobile.png`

如果表格、弹层或滚动容器导致全页截图失真，改用可信视口截图并在 `notes.md` 记录原因。

- [ ] **步骤 4：编写视觉分析记录**

`notes.md` 必须包含：

- 验收日期。
- 分支和提交号。
- 浏览器、服务地址和测试账号。
- 截图清单、视口尺寸、页面路径和场景。
- 功能路径摘要。
- 视觉分析结论：布局稳定性、信息密度、关键操作可见性、数量列对齐、状态可识别、文案溢出、控件重叠、响应式适配和权限状态识别。
- 已发现问题、影响范围、处理结果。
- 最终结论。

- [ ] **步骤 5：检查截图文件**

Run:

```powershell
Get-ChildItem docs/testing/production-execution-visual-audit -Filter *.png | Select-Object Name,Length
```

Expected: 至少 15 张 PNG 文件，文件大小非 0。

- [ ] **步骤 6：更新任务和测试记录**

在任务文档与测试计划中追加：

- 视觉分析目录。
- 截图数量。
- 视觉结论。
- 是否存在阻断问题。

- [ ] **步骤 7：最终验证**

Run:

```powershell
git diff --check
git status --short --branch
```

Expected: `git diff --check` 无输出；只有本任务文档和截图相关变更。

- [ ] **步骤 8：提交任务 9**

Run:

```powershell
git add docs/testing/production-execution-visual-audit docs/tasks/008-production-execution-foundation.md docs/testing/production-execution-test-plan.md
git commit -m "完成生产执行视觉分析"
```

## 阶段合入和主分支验收准备

生产执行功能分支满足以下全部条件后，主代理才能准备合入主分支：

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

合入主分支后必须重新运行关键验证、推送 `main`，并保持本地服务可用，再通知用户进行浏览器成果查验。

## 自检记录

- 本计划覆盖生产执行任务文档中的工单、BOM 快照、领料、报工、完工入库、权限、审计、库存追溯和视觉分析要求。
- 本计划明确排除了高级排产、工艺路线、工序流转、批次、序列号、库位、条码、倒冲、退料、补料、返工、委外、采购销售联动、审批流、正式成本核算、多单位换算、多组织、多公司和多租户。
- 本计划使用的状态、权限、错误码、视觉目录与生产执行接口契约和测试计划一致。
- 本计划未要求修改主分支；阶段实现和验收完成后才允许进入主分支合入流程。
