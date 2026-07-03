# 仓库与库存基础实施计划

> 面向代理执行者：必须使用 `superpowers:subagent-driven-development` 或 `superpowers:executing-plans` 按任务逐项实施。每个步骤使用复选框跟踪，完成后保留验证证据。

**目标：** 实现库存余额、库存变动流水、期初库存和库存调整基础能力，使后续生产工单、领料、完工入库和成本归集可以依赖可信库存数据。

**架构：** 后端沿用 Spring Boot、JdbcTemplate、PostgreSQL、Flyway、统一 `ApiResponse`、接口鉴权和审计模式；库存作为独立业务域放在 `com.qherp.api.system.inventory` 包。前端沿用 Vue 3、Pinia、Vue Router、Element Plus 和现有后台页面模式，在 `/inventory` 下提供余额、流水和单据页面。阶段验收必须包含本地部署、真实浏览器路径验证、可信截图和视觉分析结论。

**技术栈：** Java 21、Spring Boot、PostgreSQL、Flyway、JUnit、Testcontainers、Vue 3、TypeScript、Vitest、Element Plus、Playwright 或内置浏览器截图。

---

## 文件结构

- 新建 `apps/api/src/main/resources/db/migration/V5__inventory_management_schema.sql`：库存余额、库存变动流水、库存单据主表和明细表。
- 修改 `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`：增加库存错误码。
- 修改 `apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`：增加库存菜单和操作权限种子。
- 修改 `apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`：增加 `/api/admin/inventory/**` 接口鉴权映射。
- 新建 `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryDocumentStatus.java`：库存单据状态枚举。
- 新建 `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryDocumentType.java`：库存单据类型枚举。
- 新建 `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryMovementType.java`：库存变动类型枚举。
- 新建 `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryDirection.java`：库存变动方向枚举。
- 新建 `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAdjustmentDirection.java`：库存调整方向枚举。
- 新建 `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAdminController.java`：库存管理接口入口。
- 新建 `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAdminService.java`：库存查询、单据保存、过账、余额更新、流水和审计。
- 新建 `apps/api/src/test/java/com/qherp/api/system/inventory/InventoryAdminControllerTests.java`：库存后端集成测试。
- 修改 `apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`：验证库存权限种子。
- 修改 `apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`：验证库存接口鉴权。
- 新建 `apps/web/src/shared/api/inventoryApi.ts`：库存前端 API 类型与请求封装。
- 新建 `apps/web/src/shared/api/inventoryApi.spec.ts`：库存 API 封装测试。
- 修改 `apps/web/src/router/index.ts`：增加库存路由和权限元信息。
- 修改 `apps/web/src/router/permissionGuard.spec.ts`：增加库存路由守卫断言。
- 修改 `apps/web/src/App.vue`：增加库存管理菜单入口。
- 新建 `apps/web/src/modules/inventory/inventoryPageHelpers.ts`：库存状态、方向、类型文案和数字处理。
- 新建 `apps/web/src/modules/inventory/InventoryStatusTag.vue`：库存单据状态标签。
- 新建 `apps/web/src/modules/inventory/InventoryDirectionTag.vue`：库存变动方向标签。
- 新建 `apps/web/src/modules/inventory/InventoryBalanceListView.vue`：库存余额列表。
- 新建 `apps/web/src/modules/inventory/InventoryMovementListView.vue`：库存变动流水列表。
- 新建 `apps/web/src/modules/inventory/InventoryDocumentListView.vue`：库存单据列表。
- 新建 `apps/web/src/modules/inventory/InventoryDocumentFormView.vue`：期初和调整单据创建、编辑页面。
- 新建 `apps/web/src/modules/inventory/InventoryDocumentDetailView.vue`：库存单据详情页面。
- 新建 `apps/web/src/modules/inventory/InventoryDocumentLineEditor.vue`：库存单据明细编辑表格。
- 新建 `apps/web/src/modules/inventory/InventoryBalanceListView.spec.ts`：库存余额页面测试。
- 新建 `apps/web/src/modules/inventory/InventoryMovementListView.spec.ts`：库存流水页面测试。
- 新建 `apps/web/src/modules/inventory/InventoryDocumentListView.spec.ts`：库存单据列表测试。
- 新建 `apps/web/src/modules/inventory/InventoryDocumentFormView.spec.ts`：库存单据表单测试。
- 创建 `docs/testing/inventory-management-visual-audit/notes.md`：浏览器视觉分析记录，阶段验收时创建。
- 创建 `docs/testing/inventory-management-visual-audit/*.png`：浏览器截图证据，阶段验收时创建。
- 修改 `docs/tasks/007-inventory-management-foundation.md`：追加实际执行结果。
- 修改 `docs/testing/inventory-management-test-plan.md`：追加测试和视觉验收结果。

## 实施约束

- 严格按 `docs/tasks/007-inventory-management-foundation.md`、`docs/api/inventory-management-api.md`、`docs/testing/inventory-management-test-plan.md` 和本计划执行。
- 本阶段只实现库存余额、库存变动流水、期初库存和库存调整，不进入生产工单、领料、报工、完工入库、采购销售、成本、批次、库位、序列号、条码、调拨、盘点、库存占用和多单位换算。
- 库存维度固定为“仓库 + 物料”，库存单位固定使用物料基本单位。
- 一期不允许负库存；同一仓库物料只允许过账一次期初；已过账单据不可编辑。
- 所有影响库存余额的动作必须通过库存单据过账产生，过账必须在单事务内同时更新余额、写流水、写审计。
- 所有写接口必须后端鉴权，前端按钮隐藏不能替代接口鉴权。
- 每个任务完成后运行对应验证命令；测试、构建、启动、浏览器访问或视觉分析失败时先定位并修复，不跳过验证。
- 浏览器验收阶段必须引入视觉分析。没有截图证据、没有分析结论、截图失真未复拍、视觉问题影响核心操作或数据识别时，不得进入主分支阶段验收。

## 任务 1：后端迁移、错误码和权限

**文件：**

- 创建：`apps/api/src/main/resources/db/migration/V5__inventory_management_schema.sql`
- 修改：`apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- 修改：`apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`
- 修改：`apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`
- 修改：`apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`

- [ ] **步骤 1：创建库存数据库迁移**

写入 `V5__inventory_management_schema.sql`：

```sql
create table inv_stock_balance (
	id bigserial primary key,
	warehouse_id bigint not null,
	material_id bigint not null,
	unit_id bigint not null,
	quantity_on_hand numeric(18, 6) not null default 0,
	locked_quantity numeric(18, 6) not null default 0,
	created_at timestamp with time zone not null,
	updated_at timestamp with time zone not null,
	version bigint not null default 0,
	constraint fk_inv_stock_balance_warehouse foreign key (warehouse_id) references mst_warehouse (id),
	constraint fk_inv_stock_balance_material foreign key (material_id) references mst_material (id),
	constraint fk_inv_stock_balance_unit foreign key (unit_id) references mst_unit (id),
	constraint uk_inv_stock_balance_warehouse_material unique (warehouse_id, material_id),
	constraint ck_inv_stock_balance_quantity_non_negative check (quantity_on_hand >= 0),
	constraint ck_inv_stock_balance_locked_non_negative check (locked_quantity >= 0)
);

create table inv_inventory_document (
	id bigserial primary key,
	document_no varchar(64) not null,
	document_type varchar(32) not null,
	status varchar(32) not null,
	business_date date not null,
	reason varchar(200) not null,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamp with time zone not null,
	updated_by varchar(64) not null,
	updated_at timestamp with time zone not null,
	posted_by varchar(64),
	posted_at timestamp with time zone,
	version bigint not null default 0,
	constraint uk_inv_inventory_document_no unique (document_no),
	constraint ck_inv_inventory_document_type check (document_type in ('OPENING', 'ADJUSTMENT')),
	constraint ck_inv_inventory_document_status check (status in ('DRAFT', 'POSTED'))
);

create table inv_inventory_document_line (
	id bigserial primary key,
	document_id bigint not null,
	line_no integer not null,
	warehouse_id bigint not null,
	material_id bigint not null,
	unit_id bigint not null,
	quantity numeric(18, 6) not null,
	adjustment_direction varchar(32),
	before_quantity numeric(18, 6),
	after_quantity numeric(18, 6),
	remark varchar(500),
	created_at timestamp with time zone not null,
	updated_at timestamp with time zone not null,
	constraint fk_inv_document_line_document foreign key (document_id) references inv_inventory_document (id) on delete cascade,
	constraint fk_inv_document_line_warehouse foreign key (warehouse_id) references mst_warehouse (id),
	constraint fk_inv_document_line_material foreign key (material_id) references mst_material (id),
	constraint fk_inv_document_line_unit foreign key (unit_id) references mst_unit (id),
	constraint uk_inv_document_line_no unique (document_id, line_no),
	constraint uk_inv_document_line_material unique (document_id, warehouse_id, material_id),
	constraint ck_inv_document_line_quantity_positive check (quantity > 0),
	constraint ck_inv_document_line_adjustment_direction check (adjustment_direction is null or adjustment_direction in ('INCREASE', 'DECREASE'))
);

create table inv_stock_movement (
	id bigserial primary key,
	movement_no varchar(64) not null,
	movement_type varchar(32) not null,
	direction varchar(32) not null,
	warehouse_id bigint not null,
	material_id bigint not null,
	unit_id bigint not null,
	quantity numeric(18, 6) not null,
	before_quantity numeric(18, 6) not null,
	after_quantity numeric(18, 6) not null,
	source_type varchar(64) not null,
	source_id bigint not null,
	source_line_id bigint not null,
	business_date date not null,
	reason varchar(200) not null,
	remark varchar(500),
	operator_name varchar(64) not null,
	occurred_at timestamp with time zone not null,
	constraint uk_inv_stock_movement_no unique (movement_no),
	constraint uk_inv_stock_movement_source unique (source_type, source_line_id),
	constraint fk_inv_stock_movement_warehouse foreign key (warehouse_id) references mst_warehouse (id),
	constraint fk_inv_stock_movement_material foreign key (material_id) references mst_material (id),
	constraint fk_inv_stock_movement_unit foreign key (unit_id) references mst_unit (id),
	constraint ck_inv_stock_movement_type check (movement_type in ('OPENING', 'ADJUSTMENT_INCREASE', 'ADJUSTMENT_DECREASE')),
	constraint ck_inv_stock_movement_direction check (direction in ('IN', 'OUT')),
	constraint ck_inv_stock_movement_quantity_positive check (quantity > 0),
	constraint ck_inv_stock_movement_before_non_negative check (before_quantity >= 0),
	constraint ck_inv_stock_movement_after_non_negative check (after_quantity >= 0)
);

create index idx_inv_stock_balance_warehouse on inv_stock_balance (warehouse_id);
create index idx_inv_stock_balance_material on inv_stock_balance (material_id);
create index idx_inv_stock_movement_business_date on inv_stock_movement (business_date desc, id desc);
create index idx_inv_stock_movement_warehouse_material on inv_stock_movement (warehouse_id, material_id);
create index idx_inv_inventory_document_business_date on inv_inventory_document (business_date desc, id desc);
```

- [ ] **步骤 2：增加库存错误码**

在 `ApiErrorCode` 的 BOM 错误码之后加入：

```java
INVENTORY_DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "库存单据不存在"),

INVENTORY_DOCUMENT_TYPE_INVALID(HttpStatus.BAD_REQUEST, "库存单据类型不正确"),

INVENTORY_DOCUMENT_STATUS_INVALID(HttpStatus.CONFLICT, "库存单据状态不允许当前操作"),

INVENTORY_DOCUMENT_POSTED_IMMUTABLE(HttpStatus.CONFLICT, "已过账单据不可编辑"),

INVENTORY_DOCUMENT_EMPTY_LINES(HttpStatus.BAD_REQUEST, "库存单据明细不能为空"),

INVENTORY_DOCUMENT_DUPLICATE_LINE(HttpStatus.CONFLICT, "库存单据明细重复"),

INVENTORY_QUANTITY_INVALID(HttpStatus.BAD_REQUEST, "库存数量不正确"),

INVENTORY_STOCK_NOT_ENOUGH(HttpStatus.CONFLICT, "库存不足，调减后库存不能小于 0"),

INVENTORY_WAREHOUSE_INVALID(HttpStatus.BAD_REQUEST, "仓库不存在或已停用"),

INVENTORY_MATERIAL_INVALID(HttpStatus.BAD_REQUEST, "物料不存在或已停用"),

INVENTORY_UNIT_INVALID(HttpStatus.BAD_REQUEST, "单位不存在、已停用或不是物料基本单位"),

INVENTORY_OPENING_EXISTS(HttpStatus.CONFLICT, "同一仓库物料已存在已过账期初"),

INVENTORY_DUPLICATE_POST(HttpStatus.CONFLICT, "库存单据已过账或重复过账"),

INVENTORY_MOVEMENT_SOURCE_DUPLICATED(HttpStatus.CONFLICT, "来源明细已生成库存变动"),
```

- [ ] **步骤 3：增加库存权限种子**

在 `AccountPermissionInitializer.PERMISSION_SEEDS` 中 BOM 权限之后追加：

```java
new PermissionSeed("inventory", "库存管理", SystemPermissionType.MENU, null, "/inventory/balances",
		null, null, 300),
new PermissionSeed("inventory:balance:view", "查看库存余额", SystemPermissionType.ACTION, "inventory",
		"/inventory/balances", "GET", "/api/admin/inventory/balances", 301),
new PermissionSeed("inventory:movement:view", "查看库存变动", SystemPermissionType.ACTION, "inventory",
		"/inventory/movements", "GET", "/api/admin/inventory/movements", 302),
new PermissionSeed("inventory:document:view", "查看库存单据", SystemPermissionType.ACTION, "inventory",
		"/inventory/documents", "GET", "/api/admin/inventory/documents/**", 303),
new PermissionSeed("inventory:document:create", "创建库存单据", SystemPermissionType.ACTION, "inventory",
		"/inventory/documents", "POST", "/api/admin/inventory/documents", 304),
new PermissionSeed("inventory:document:update", "更新库存单据", SystemPermissionType.ACTION, "inventory",
		"/inventory/documents", "PUT", "/api/admin/inventory/documents/{id}", 305),
new PermissionSeed("inventory:document:post", "过账库存单据", SystemPermissionType.ACTION, "inventory",
		"/inventory/documents", "PUT", "/api/admin/inventory/documents/{id}/post", 306)
```

- [ ] **步骤 4：增加库存接口鉴权映射**

在 `PermissionAuthorizationManager.permissionCode` 中，在 BOM 映射之后、系统用户映射之前加入：

```java
String inventoryPermissionCode = inventoryPermissionCode(method, path);
if (inventoryPermissionCode != null) {
	return inventoryPermissionCode;
}
```

新增方法：

```java
private String inventoryPermissionCode(String method, String path) {
	String basePath = "/api/admin/inventory";
	if (!matchesBasePath(path, basePath)) {
		return null;
	}
	if ("GET".equals(method) && "/api/admin/inventory/balances".equals(path)) {
		return "inventory:balance:view";
	}
	if ("GET".equals(method) && "/api/admin/inventory/movements".equals(path)) {
		return "inventory:movement:view";
	}
	String documentPath = "/api/admin/inventory/documents";
	if ("GET".equals(method) && (documentPath.equals(path) || matchesIdPath(path, documentPath))) {
		return "inventory:document:view";
	}
	if ("POST".equals(method) && documentPath.equals(path)) {
		return "inventory:document:create";
	}
	if ("PUT".equals(method) && matchesIdPath(path, documentPath)) {
		return "inventory:document:update";
	}
	if ("PUT".equals(method) && path.matches(Pattern.quote(documentPath) + "/\\d+/post")) {
		return "inventory:document:post";
	}
	return null;
}
```

- [ ] **步骤 5：补充权限初始化和鉴权测试**

在权限初始化测试中断言 `inventory`、`inventory:balance:view`、`inventory:movement:view`、`inventory:document:view`、`inventory:document:create`、`inventory:document:update`、`inventory:document:post` 存在。

在鉴权测试中断言：

```text
GET /api/admin/inventory/balances -> inventory:balance:view
GET /api/admin/inventory/movements -> inventory:movement:view
GET /api/admin/inventory/documents -> inventory:document:view
GET /api/admin/inventory/documents/1 -> inventory:document:view
POST /api/admin/inventory/documents -> inventory:document:create
PUT /api/admin/inventory/documents/1 -> inventory:document:update
PUT /api/admin/inventory/documents/1/post -> inventory:document:post
```

- [ ] **步骤 6：运行后端迁移和权限验证**

运行：

```powershell
cd apps/api
.\mvnw.cmd test -Dtest=AccountPermissionInitializerTests,PermissionAuthorizationTests,QherpApiApplicationTests
```

预期：命令退出码为 0，Flyway 能从空库执行 V1 到 V5，库存权限种子和接口鉴权测试通过。

## 任务 2：后端库存服务和接口

**文件：**

- 创建：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryDocumentStatus.java`
- 创建：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryDocumentType.java`
- 创建：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryMovementType.java`
- 创建：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryDirection.java`
- 创建：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAdjustmentDirection.java`
- 创建：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAdminController.java`
- 创建：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAdminService.java`

- [ ] **步骤 1：创建库存枚举**

写入枚举文件：

```java
package com.qherp.api.system.inventory;

public enum InventoryDocumentStatus {
	DRAFT,
	POSTED
}
```

```java
package com.qherp.api.system.inventory;

public enum InventoryDocumentType {
	OPENING,
	ADJUSTMENT
}
```

```java
package com.qherp.api.system.inventory;

public enum InventoryMovementType {
	OPENING,
	ADJUSTMENT_INCREASE,
	ADJUSTMENT_DECREASE
}
```

```java
package com.qherp.api.system.inventory;

public enum InventoryDirection {
	IN,
	OUT
}
```

```java
package com.qherp.api.system.inventory;

public enum InventoryAdjustmentDirection {
	INCREASE,
	DECREASE
}
```

- [ ] **步骤 2：创建库存控制器**

`InventoryAdminController` 使用现有 BOM 控制器风格，接口固定为：

```java
@RestController
@RequestMapping("/api/admin/inventory")
public class InventoryAdminController {

	private final InventoryAdminService inventoryAdminService;

	public InventoryAdminController(InventoryAdminService inventoryAdminService) {
		this.inventoryAdminService = inventoryAdminService;
	}

	@GetMapping("/balances")
	public ApiResponse<PageResponse<InventoryAdminService.InventoryBalanceResponse>> balances(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long warehouseId,
			@RequestParam(required = false) Long materialId, @RequestParam(required = false) String materialType,
			@RequestParam(defaultValue = "false") boolean onlyPositive,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.inventoryAdminService.balances(keyword, warehouseId, materialId, materialType,
				onlyPositive, page, pageSize));
	}

	@GetMapping("/movements")
	public ApiResponse<PageResponse<InventoryAdminService.InventoryMovementResponse>> movements(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long warehouseId,
			@RequestParam(required = false) Long materialId, @RequestParam(required = false) String movementType,
			@RequestParam(required = false) String direction, @RequestParam(required = false) LocalDate dateFrom,
			@RequestParam(required = false) LocalDate dateTo, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.inventoryAdminService.movements(keyword, warehouseId, materialId, movementType,
				direction, dateFrom, dateTo, page, pageSize));
	}

	@GetMapping("/documents")
	public ApiResponse<PageResponse<InventoryAdminService.InventoryDocumentSummaryResponse>> documents(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String documentType,
			@RequestParam(required = false) String status, @RequestParam(required = false) LocalDate dateFrom,
			@RequestParam(required = false) LocalDate dateTo, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.inventoryAdminService.documents(keyword, documentType, status, dateFrom, dateTo,
				page, pageSize));
	}

	@GetMapping("/documents/{id}")
	public ApiResponse<InventoryAdminService.InventoryDocumentDetailResponse> document(@PathVariable Long id) {
		return ApiResponse.ok(this.inventoryAdminService.document(id));
	}

	@PostMapping("/documents")
	public ApiResponse<InventoryAdminService.InventoryDocumentDetailResponse> createDocument(
			@Valid @RequestBody InventoryAdminService.InventoryDocumentRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.inventoryAdminService.createDocument(request, currentUser, servletRequest));
	}

	@PutMapping("/documents/{id}")
	public ApiResponse<InventoryAdminService.InventoryDocumentDetailResponse> updateDocument(@PathVariable Long id,
			@Valid @RequestBody InventoryAdminService.InventoryDocumentRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.inventoryAdminService.updateDocument(id, request, currentUser, servletRequest));
	}

	@PutMapping("/documents/{id}/post")
	public ApiResponse<InventoryAdminService.InventoryDocumentDetailResponse> postDocument(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.inventoryAdminService.postDocument(id, currentUser, servletRequest));
	}
}
```

- [ ] **步骤 3：实现服务查询能力**

`InventoryAdminService` 使用 `JdbcTemplate` 和 `AuditService`，先实现只读查询：

```text
balances(keyword, warehouseId, materialId, materialType, onlyPositive, page, pageSize)
movements(keyword, warehouseId, materialId, movementType, direction, dateFrom, dateTo, page, pageSize)
documents(keyword, documentType, status, dateFrom, dateTo, page, pageSize)
document(id)
```

查询要求：

```text
余额列表 left join 仓库、物料、单位，并计算 availableQuantity = quantityOnHand - lockedQuantity。
流水列表 join 仓库、物料、单位，按 occurred_at desc, id desc 排序。
单据列表按 updated_at desc, id desc 排序，lineCount 使用子查询。
详情查询主表和明细，明细按 line_no asc, id asc 排序。
pageSize 使用 1 到 100 的限制，page 小于 1 时按 1 处理。
```

- [ ] **步骤 4：实现单据创建和更新**

新增请求与响应 record，字段与 `docs/api/inventory-management-api.md` 保持一致。创建和更新必须执行：

```text
documentType 必须是 OPENING 或 ADJUSTMENT。
businessDate、reason 必填。
lines 不能为空。
lineNo 必须大于 0 且同一单据内唯一。
同一单据内 warehouseId + materialId 不允许重复。
仓库、物料、单位必须存在且状态为 ENABLED。
unitId 必须等于物料基本单位；请求未传 unitId 时使用物料基本单位。
quantity 必须大于 0。
OPENING 明细不允许传 adjustmentDirection。
ADJUSTMENT 明细必须传 adjustmentDirection，且只能是 INCREASE 或 DECREASE。
POSTED 单据不可更新，返回 INVENTORY_DOCUMENT_POSTED_IMMUTABLE。
```

单据编号生成格式：

```text
OPENING 使用 INV-OPEN-yyyyMMddHHmmssSSS
ADJUSTMENT 使用 INV-ADJ-yyyyMMddHHmmssSSS
```

审计动作：

```text
INVENTORY_DOCUMENT_CREATE
INVENTORY_DOCUMENT_UPDATE
```

- [ ] **步骤 5：实现库存过账事务**

`postDocument` 标记 `@Transactional`，按以下顺序执行：

```text
读取单据主表并加锁，状态不是 DRAFT 时返回 INVENTORY_DUPLICATE_POST。
读取明细，明细为空返回 INVENTORY_DOCUMENT_EMPTY_LINES。
逐行校验仓库、物料、单位仍为启用状态。
OPENING 行先检查 inv_stock_movement 中同 warehouse_id + material_id + movement_type = OPENING 是否存在，存在返回 INVENTORY_OPENING_EXISTS。
按 warehouse_id + material_id 获取或创建 inv_stock_balance 记录。
对余额行执行 `select id from inv_stock_balance where warehouse_id = ? and material_id = ? for update`。
计算 beforeQuantity、changeQuantity、afterQuantity。
调减后 afterQuantity 小于 0 时返回 INVENTORY_STOCK_NOT_ENOUGH。
写 inv_stock_movement，source_type 固定为 INVENTORY_DOCUMENT。
更新 inv_stock_balance.quantity_on_hand、unit_id、updated_at、version。
更新 inv_inventory_document_line.before_quantity 和 after_quantity。
所有明细成功后更新单据状态为 POSTED、posted_by、posted_at、updated_by、updated_at。
写审计 INVENTORY_DOCUMENT_POST。
```

重复来源明细唯一约束 `uk_inv_stock_movement_source` 捕获后返回 `INVENTORY_MOVEMENT_SOURCE_DUPLICATED`。

- [ ] **步骤 6：运行后端编译验证**

运行：

```powershell
cd apps/api
.\mvnw.cmd test -Dtest=QherpApiApplicationTests
```

预期：命令退出码为 0，应用上下文可以加载库存控制器和服务。

## 任务 3：后端库存集成测试

**文件：**

- 创建：`apps/api/src/test/java/com/qherp/api/system/inventory/InventoryAdminControllerTests.java`

- [ ] **步骤 1：准备测试数据工具**

在测试类中沿用现有控制器测试登录、CSRF、权限和数据准备方式，准备：

```text
管理员账号。
只读账号。
原料仓、成品仓、停用仓。
成品 A、半成品 B、原材料 X、辅料 Z、停用物料 T。
个、千克、米三个启用单位。
```

- [ ] **步骤 2：覆盖期初库存主路径**

测试内容：

```text
管理员创建 OPENING 草稿成功。
更新 OPENING 草稿成功。
过账 OPENING 成功。
余额 quantityOnHand 增加。
availableQuantity 等于 quantityOnHand。
流水包含 OPENING、IN、beforeQuantity、quantity、afterQuantity、sourceLineId。
审计日志存在 INVENTORY_DOCUMENT_CREATE、INVENTORY_DOCUMENT_UPDATE、INVENTORY_DOCUMENT_POST。
```

- [ ] **步骤 3：覆盖库存调整主路径**

测试内容：

```text
先过账期初建立余额。
创建 ADJUSTMENT 调增并过账，余额增加，流水为 ADJUSTMENT_INCREASE 和 IN。
创建 ADJUSTMENT 调减并过账，余额减少，流水为 ADJUSTMENT_DECREASE 和 OUT。
```

- [ ] **步骤 4：覆盖核心异常**

测试内容：

```text
明细为空返回 INVENTORY_DOCUMENT_EMPTY_LINES。
数量为 0 或负数返回 INVENTORY_QUANTITY_INVALID。
同一单据重复仓库物料返回 INVENTORY_DOCUMENT_DUPLICATE_LINE。
停用仓库返回 INVENTORY_WAREHOUSE_INVALID。
停用物料返回 INVENTORY_MATERIAL_INVALID。
非物料基本单位返回 INVENTORY_UNIT_INVALID。
重复期初过账返回 INVENTORY_OPENING_EXISTS。
调减超过现存数量返回 INVENTORY_STOCK_NOT_ENOUGH，余额和流水不变化。
已过账单据更新返回 INVENTORY_DOCUMENT_POSTED_IMMUTABLE。
重复过账返回 INVENTORY_DUPLICATE_POST 或 INVENTORY_MOVEMENT_SOURCE_DUPLICATED。
```

- [ ] **步骤 5：覆盖权限和 CSRF**

测试内容：

```text
未登录访问余额、流水、单据列表和详情返回 AUTH_UNAUTHORIZED。
只读用户可以 GET 余额、流水、单据列表和详情。
只读用户 POST、PUT 和过账返回 AUTH_FORBIDDEN。
写接口缺少 CSRF token 被拒绝。
```

- [ ] **步骤 6：覆盖并发过账**

测试内容：

```text
两个调整单同时调减同一仓库物料。
最终余额不得小于 0。
成功单据的流水与余额一致。
失败单据返回受控库存不足或重复处理错误。
```

- [ ] **步骤 7：运行后端库存测试**

运行：

```powershell
cd apps/api
.\mvnw.cmd test -Dtest=InventoryAdminControllerTests
```

预期：命令退出码为 0，库存主路径、异常、权限、审计和并发测试全部通过。

## 任务 4：前端 API、路由和菜单

**文件：**

- 创建：`apps/web/src/shared/api/inventoryApi.ts`
- 创建：`apps/web/src/shared/api/inventoryApi.spec.ts`
- 修改：`apps/web/src/router/index.ts`
- 修改：`apps/web/src/router/permissionGuard.spec.ts`
- 修改：`apps/web/src/App.vue`

- [ ] **步骤 1：创建库存 API 类型与方法**

`inventoryApi.ts` 导出以下类型：

```text
InventoryDocumentStatus = 'DRAFT' | 'POSTED'
InventoryDocumentType = 'OPENING' | 'ADJUSTMENT'
InventoryMovementType = 'OPENING' | 'ADJUSTMENT_INCREASE' | 'ADJUSTMENT_DECREASE'
InventoryDirection = 'IN' | 'OUT'
InventoryAdjustmentDirection = 'INCREASE' | 'DECREASE'
InventoryBalanceRecord
InventoryMovementRecord
InventoryDocumentSummaryRecord
InventoryDocumentDetailRecord
InventoryDocumentLinePayload
InventoryDocumentPayload
```

导出方法：

```text
inventoryApi.balances.list(params)
inventoryApi.movements.list(params)
inventoryApi.documents.list(params)
inventoryApi.documents.get(id)
inventoryApi.documents.create(payload)
inventoryApi.documents.update(id, payload)
inventoryApi.documents.post(id)
```

- [ ] **步骤 2：编写 API 封装测试**

`inventoryApi.spec.ts` 断言请求路径和参数：

```text
GET /api/admin/inventory/balances
GET /api/admin/inventory/movements
GET /api/admin/inventory/documents
GET /api/admin/inventory/documents/1
POST /api/admin/inventory/documents
PUT /api/admin/inventory/documents/1
PUT /api/admin/inventory/documents/1/post
```

- [ ] **步骤 3：增加库存路由**

在 `router/index.ts` 增加：

```text
/inventory/balances -> InventoryBalanceListView，权限 inventory:balance:view
/inventory/movements -> InventoryMovementListView，权限 inventory:movement:view
/inventory/documents -> InventoryDocumentListView，权限 inventory:document:view
/inventory/documents/create -> InventoryDocumentFormView，权限 inventory:document:create
/inventory/documents/:id -> InventoryDocumentDetailView，权限 inventory:document:view
/inventory/documents/:id/edit -> InventoryDocumentFormView，权限 inventory:document:update
```

`/inventory` 重定向到 `/inventory/balances`。

- [ ] **步骤 4：增加菜单入口**

在 `App.vue` 的后台菜单中增加“库存管理”分组。权限显示规则：

```text
有 inventory:balance:view 时显示库存余额。
有 inventory:movement:view 时显示库存变动。
有 inventory:document:view 时显示库存单据。
```

- [ ] **步骤 5：运行前端路由和 API 测试**

运行：

```powershell
cd apps/web
npm test -- inventoryApi.spec.ts permissionGuard.spec.ts App.spec.ts
```

预期：命令退出码为 0，库存 API、路由守卫和菜单测试通过。

## 任务 5：前端库存页面

**文件：**

- 创建：`apps/web/src/modules/inventory/inventoryPageHelpers.ts`
- 创建：`apps/web/src/modules/inventory/InventoryStatusTag.vue`
- 创建：`apps/web/src/modules/inventory/InventoryDirectionTag.vue`
- 创建：`apps/web/src/modules/inventory/InventoryBalanceListView.vue`
- 创建：`apps/web/src/modules/inventory/InventoryMovementListView.vue`
- 创建：`apps/web/src/modules/inventory/InventoryDocumentListView.vue`
- 创建：`apps/web/src/modules/inventory/InventoryDocumentFormView.vue`
- 创建：`apps/web/src/modules/inventory/InventoryDocumentDetailView.vue`
- 创建：`apps/web/src/modules/inventory/InventoryDocumentLineEditor.vue`
- 创建：`apps/web/src/modules/inventory/InventoryBalanceListView.spec.ts`
- 创建：`apps/web/src/modules/inventory/InventoryMovementListView.spec.ts`
- 创建：`apps/web/src/modules/inventory/InventoryDocumentListView.spec.ts`
- 创建：`apps/web/src/modules/inventory/InventoryDocumentFormView.spec.ts`

- [ ] **步骤 1：创建页面辅助方法**

`inventoryPageHelpers.ts` 提供：

```text
documentTypeLabel('OPENING') -> 期初库存
documentTypeLabel('ADJUSTMENT') -> 库存调整
documentStatusLabel('DRAFT') -> 草稿
documentStatusLabel('POSTED') -> 已过账
movementTypeLabel('OPENING') -> 期初
movementTypeLabel('ADJUSTMENT_INCREASE') -> 调增
movementTypeLabel('ADJUSTMENT_DECREASE') -> 调减
directionLabel('IN') -> 入库
directionLabel('OUT') -> 出库
adjustmentDirectionLabel('INCREASE') -> 调增
adjustmentDirectionLabel('DECREASE') -> 调减
positiveQuantity(value) -> 大于 0 的数字或 null
formatQuantity(value) -> 保留最多 6 位小数并去掉尾随 0
newInventoryLine() -> 默认明细行
```

- [ ] **步骤 2：实现状态与方向标签**

`InventoryStatusTag.vue` 显示草稿、已过账。

`InventoryDirectionTag.vue` 显示入库、出库，并用文字区分方向，不只依赖颜色。

- [ ] **步骤 3：实现库存余额页**

页面要求：

```text
复用 MasterDataTableView。
查询区包含关键词、仓库、物料、物料类型、只看有库存。
表格列包含仓库、物料编码、物料名称、规格、物料类型、单位、现存数量、锁定数量、可用数量、更新时间。
数量列右对齐。
操作列提供“查看流水”，跳转到 /inventory/movements 并带仓库和物料筛选。
无数据时显示库存余额空状态。
错误和加载状态使用 el-alert。
窄屏下表格横向滚动。
```

- [ ] **步骤 4：实现库存变动流水页**

页面要求：

```text
查询区包含关键词、仓库、物料、变动类型、方向、业务日期范围。
表格列包含发生时间、业务日期、仓库、物料、变动类型、方向、变动前、变动数量、变动后、来源单据、原因、操作人。
变动前、变动数量、变动后成组右对齐。
来源单据可跳转到库存单据详情。
本页只读，不显示写操作。
```

- [ ] **步骤 5：实现库存单据列表**

页面要求：

```text
查询区包含关键词、单据类型、状态、业务日期范围。
有 inventory:document:create 权限时显示新增期初和新增调整入口。
表格列包含单据编号、类型、状态、业务日期、明细数、创建人、过账人、更新时间。
草稿且有 update 权限时显示编辑。
草稿且有 post 权限时显示过账。
已过账只显示详情。
过账前必须二次确认，确认文案说明会影响库存余额且不可撤销。
过账失败后列表不误刷新为空，错误提示可见。
```

- [ ] **步骤 6：实现库存单据表单和明细编辑器**

表单要求：

```text
主表包含单据类型、业务日期、原因、备注。
从新建期初入口进入时 documentType 固定为 OPENING。
从新建调整入口进入时 documentType 固定为 ADJUSTMENT。
编辑草稿时单据类型不可修改。
明细包含仓库、物料、单位、调整方向、数量、备注。
期初单不显示调整方向。
调整单必须显示调整方向。
单位默认物料基本单位，且不可改为非基本单位。
同一单据内仓库物料重复时前端阻止提交。
数量为 0、负数、空值或非数字时前端阻止提交。
保存失败后错误提示可见，表单数据保留。
窄屏下明细表可横向滚动，底部按钮不遮挡内容。
```

- [ ] **步骤 7：实现库存单据详情**

详情要求：

```text
展示主表信息、状态、创建人、过账人、时间和备注。
展示明细行、仓库、物料、单位、调整方向、数量、变动前、变动后。
已过账单据明确显示“已过账，不可编辑”。
草稿且有权限时提供编辑和过账入口。
```

- [ ] **步骤 8：编写前端页面测试**

测试覆盖：

```text
余额页加载、查询、重置、空状态、错误状态、查看流水跳转。
流水页加载、筛选、方向和变动类型标签。
单据列表创建入口权限、编辑入口权限、过账确认、过账错误反馈。
表单期初和调整两种模式、重复明细、数量校验、保存成功跳转、保存失败保留数据。
只读权限下写按钮不可见。
```

- [ ] **步骤 9：运行前端页面测试**

运行：

```powershell
cd apps/web
npm test -- InventoryBalanceListView.spec.ts InventoryMovementListView.spec.ts InventoryDocumentListView.spec.ts InventoryDocumentFormView.spec.ts
```

预期：命令退出码为 0，库存页面定向测试通过。

## 任务 6：自动化验证和构建

**文件：**

- 本阶段全部后端和前端变更文件。

- [ ] **步骤 1：运行后端定向测试**

运行：

```powershell
cd apps/api
.\mvnw.cmd test -Dtest=InventoryAdminControllerTests,AccountPermissionInitializerTests,PermissionAuthorizationTests
```

预期：命令退出码为 0。

- [ ] **步骤 2：运行后端全量测试**

运行：

```powershell
cd apps/api
.\mvnw.cmd test
```

预期：命令退出码为 0。

- [ ] **步骤 3：运行前端定向测试**

运行：

```powershell
cd apps/web
npm test -- inventoryApi.spec.ts InventoryBalanceListView.spec.ts InventoryMovementListView.spec.ts InventoryDocumentListView.spec.ts InventoryDocumentFormView.spec.ts permissionGuard.spec.ts App.spec.ts
```

预期：命令退出码为 0。

- [ ] **步骤 4：运行前端全量测试**

运行：

```powershell
cd apps/web
npm test
```

预期：命令退出码为 0。

- [ ] **步骤 5：运行前端构建**

运行：

```powershell
cd apps/web
npm run build
```

预期：命令退出码为 0，`vue-tsc` 和 `vite build` 均通过。

- [ ] **步骤 6：运行仓库空白和占位检查**

运行：

```powershell
git diff --check
$placeholderPattern = "T" + "BD|TO" + "DO|待" + "填写|填写" + "本次|implement " + "later"
rg -n $placeholderPattern docs apps
```

预期：`git diff --check` 退出码为 0；占位扫描不命中本阶段新增内容；人工复核本阶段新增文档不存在常见占位词和用省略号代替具体内容的段落。

## 任务 7：本地部署、浏览器验收和视觉分析

**文件：**

- 创建：`docs/testing/inventory-management-visual-audit/notes.md`
- 创建：`docs/testing/inventory-management-visual-audit/*.png`
- 修改：`docs/tasks/007-inventory-management-foundation.md`
- 修改：`docs/testing/inventory-management-test-plan.md`

- [ ] **步骤 1：启动本地服务**

按现有本地部署方式启动 PostgreSQL、后端和前端。前端验收地址固定记录为：

```text
http://127.0.0.1:5173/inventory/balances
```

后端健康检查地址固定记录为：

```text
http://127.0.0.1:18080/api/health
```

如果端口被占用，必须记录实际端口和原因，并确保浏览器验收使用同一地址。

- [ ] **步骤 2：执行管理员浏览器验收**

在浏览器中完成：

```text
管理员登录。
进入库存余额页面，查看默认状态和空状态。
进入库存单据页面，新建原材料 X 在原料仓的期初库存草稿。
保存期初草稿。
编辑期初草稿。
过账期初草稿。
查询库存余额，确认原材料 X 的现存数量、可用数量和单位正确。
查询库存变动流水，确认期初流水可追溯到来源单据。
新建调增调整并过账，确认余额增加和流水新增。
新建调减调整并过账，确认余额减少和流水新增。
查看已过账单据详情，确认不可编辑。
```

- [ ] **步骤 3：执行异常和权限浏览器验收**

在浏览器中完成：

```text
创建明细为空的库存单据，确认错误提示。
录入数量为 0 或负数，确认前端阻止提交。
录入重复仓库物料明细，确认前端阻止提交。
调减超过现存数量，确认库存不足错误且余额不变。
同一仓库物料重复期初过账，确认重复期初错误。
使用停用仓库或停用物料创建单据，确认被拒绝。
仓库人员登录，验证可执行的查询、创建和过账范围。
只读用户登录，验证余额、流水、详情可看，写按钮不可见。
只读用户直接调用创建、更新或过账接口，验证返回 AUTH_FORBIDDEN。
无权限用户登录，验证库存菜单不可见，路由和接口拒绝访问。
```

- [ ] **步骤 4：保存可信浏览器截图**

截图保存到 `docs/testing/inventory-management-visual-audit/`，至少包含：

```text
01-inventory-balance-list-desktop.png
02-inventory-balance-result-desktop.png
03-inventory-balance-empty-desktop.png
04-inventory-movement-list-desktop.png
05-inventory-opening-form-desktop.png
06-inventory-adjustment-form-desktop.png
07-inventory-form-error-desktop.png
08-inventory-stock-not-enough-desktop.png
09-inventory-opening-duplicate-desktop.png
10-inventory-readonly-permission-desktop.png
11-inventory-balance-mobile.png
12-inventory-document-form-mobile.png
```

截图要求：

```text
桌面视口建议使用 1440x920。
窄屏视口建议使用 390x844。
截图必须来自真实浏览器页面，不使用手工拼图。
弹窗、抽屉、固定列、滚动容器或全页截图导致失真时，必须记录原因并改用可信视口截图。
截图中关键数据、按钮、错误提示、权限状态必须可识别。
```

- [ ] **步骤 5：写视觉分析记录**

`notes.md` 必须包含以下结构，所有字段按实际执行结果填写且不得留空：

```markdown
# 仓库与库存基础浏览器验收视觉分析

## 验收环境

- 日期：2026-07-03
- 分支：codex/inventory-foundation-planning
- 前端地址：http://127.0.0.1:5173/inventory/balances
- 后端健康检查：http://127.0.0.1:18080/api/health
- 截图方式：Playwright 或内置浏览器真实截图
- 管理员账号：记录实际账号
- 仓库人员账号：记录实际账号
- 只读账号：记录实际账号

## 截图清单

| 文件 | 视口 | 验收内容 | 结论 |
|---|---|---|---|

## 功能验收结果

## 视觉分析结论

## 缺陷处理

## 最终结论
```

视觉分析结论必须逐项说明：

```text
页面布局是否稳定。
信息密度是否符合制造业 ERP 后台工作台。
关键操作是否可见且不易误触。
库存数量列是否右对齐且可比较。
正负变动、单据状态、权限状态是否能通过文字识别。
文案是否溢出。
控件是否重叠。
桌面和窄屏响应式适配是否可用。
异常和空状态是否能指导用户处理。
```

- [ ] **步骤 6：处理视觉问题并复验**

发现以下任一问题时必须修复并重新截图：

```text
关键操作按钮不可见、被遮挡或重叠。
库存数量、单位、状态或错误提示无法识别。
只读权限状态无法判断。
表格固定列或滚动区域截图失真影响判断。
移动端表单按钮遮挡内容。
页面视觉问题明显降低库存工作台扫描效率。
```

复验后在 `notes.md` 的“缺陷处理”中记录问题、修复文件、复验截图和结论。

- [ ] **步骤 7：更新任务和测试执行记录**

在 `docs/tasks/007-inventory-management-foundation.md` 和 `docs/testing/inventory-management-test-plan.md` 追加实际执行结果，至少记录：

```text
自动化测试命令和结果。
后端健康检查结果。
管理员浏览器验收路径结果。
仓库人员、只读和无权限用户验证结果。
后端越权接口验证结果。
视觉分析记录位置。
截图清单。
缺陷处理结论。
是否达到阶段验收标准。
```

## 任务 8：提交功能分支成果和主分支验收准备

**文件：**

- 所有本阶段变更文件。

- [ ] **步骤 1：最终检查**

运行：

```powershell
git status --short
git diff --check
```

预期：`git diff --check` 退出码为 0，`git status --short` 只包含本阶段相关文件。

- [ ] **步骤 2：提交功能分支**

运行：

```powershell
git add apps docs AGENTS.md
git commit -m "实现库存基础能力"
```

预期：提交成功，提交内容只包含库存基础实现、库存文档、测试记录和浏览器视觉分析记录。

- [ ] **步骤 3：主分支阶段验收准备**

只有在任务 1 到任务 7 全部通过后，才允许合入或推送主分支。合入主分支后必须重新启动本地服务，并向用户提供：

```text
浏览器访问地址。
验收模块和范围。
已完成内容。
自动化验证结果。
浏览器验收路径结果。
视觉分析记录和截图位置。
已知问题和风险。
建议验收路径。
```

## 自检清单

- 库存基础阶段没有引入生产工单、采购销售、成本、批次、库位、序列号、条码、调拨、盘点、库存占用或多单位换算。
- 任务文档、设计规格、接口契约、测试计划和实施计划范围一致。
- 后端和前端权限编码一致。
- 一期不允许负库存。
- 同一仓库物料只允许一次已过账期初。
- 已过账单据不可编辑。
- 余额、流水、单据明细和审计在同一过账事务中一致。
- 后端接口鉴权是最终安全边界。
- 自动化测试、本地部署、浏览器功能验收和后端越权验证都有记录。
- 浏览器视觉分析有独立目录、可信截图清单、分析结论、缺陷处理和复验结论。
