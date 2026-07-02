# BOM 管理实施计划

> 面向代理执行者：必须使用 `superpowers:subagent-driven-development` 或 `superpowers:executing-plans` 按任务逐项实施。每个步骤使用复选框跟踪，完成后保留验证证据。

**目标：** 实现 BOM 管理的后端接口、前端页面、权限控制、自动化测试、本地部署和浏览器视觉分析，使 BOM 成为后续生产工单可引用的稳定主数据。

**架构：** 后端沿用 Spring Boot、JdbcTemplate、PostgreSQL、Flyway、统一 `ApiResponse` 和接口鉴权模式；BOM 作为制造主数据放在 `com.qherp.api.system.bom` 包，避免挤入现有基础资料服务。前端沿用 Vue 3、Pinia、Vue Router、Element Plus 和现有后台布局，BOM 页面放在物料管理菜单下的 `/materials/boms`。

**技术栈：** Java 21、Spring Boot、PostgreSQL、Flyway、JUnit、Testcontainers、Vue 3、TypeScript、Vitest、Element Plus、Playwright 或内置浏览器截图。

---

## 文件结构

- 新建 `apps/api/src/main/resources/db/migration/V4__bom_management_schema.sql`：BOM 主表、明细表、唯一约束和部分唯一索引。
- 修改 `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`：增加 BOM 错误码。
- 修改 `apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`：增加 BOM 菜单与操作权限。
- 修改 `apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`：增加 `/api/admin/boms` 接口鉴权映射。
- 新建 `apps/api/src/main/java/com/qherp/api/system/bom/BomStatus.java`：BOM 状态枚举。
- 新建 `apps/api/src/main/java/com/qherp/api/system/bom/BomAdminService.java`：BOM 查询、创建、更新、复制、启停、校验、审计。
- 新建 `apps/api/src/main/java/com/qherp/api/system/bom/BomAdminController.java`：BOM 管理接口。
- 新建 `apps/api/src/test/java/com/qherp/api/system/bom/BomAdminControllerTests.java`：BOM 后端集成测试。
- 新建 `apps/web/src/shared/api/bomApi.ts`：BOM 前端 API 类型与请求封装。
- 新建 `apps/web/src/shared/api/bomApi.spec.ts`：BOM API 封装测试。
- 修改 `apps/web/src/router/index.ts`：增加 `/materials/boms` 路由和 `material:bom:view` 权限。
- 修改 `apps/web/src/router/permissionGuard.spec.ts`：增加 BOM 路由守卫断言。
- 修改 `apps/web/src/App.vue`：增加支持菜单路径 `/materials/boms`。
- 新建 `apps/web/src/modules/materials/boms/BomListView.vue`：BOM 列表、查询、详情、表单入口和状态操作。
- 新建 `apps/web/src/modules/materials/boms/BomLineEditor.vue`：BOM 明细编辑表格。
- 新建 `apps/web/src/modules/materials/boms/BomStatusTag.vue`：BOM 状态标签。
- 新建 `apps/web/src/modules/materials/boms/bomPageHelpers.ts`：状态文案、数字校验和表单转换。
- 新建 `apps/web/src/modules/materials/boms/BomListView.spec.ts`：BOM 页面组件测试。
- 新建 `docs/testing/bom-management-visual-audit/notes.md`：浏览器视觉分析记录，阶段验收时创建。

## 实施约束

- 严格按本计划和 `docs/tasks/006-bom-management-foundation.md` 执行，不扩大到库存、工单、成本、采购销售、工艺路线、替代料、选配 BOM、工程变更审批或复杂多级展开 UI。
- 所有写接口必须后端鉴权，前端按钮隐藏不能替代接口鉴权。
- 保存和启用都必须拒绝可检测循环引用。
- 启用版本不可直接编辑明细，必须复制新版本调整。
- 每个任务完成后运行对应验证命令，失败时先定位和修复，不跳过验证。

## 任务 1：后端迁移、错误码和权限种子

**文件：**

- 创建：`apps/api/src/main/resources/db/migration/V4__bom_management_schema.sql`
- 修改：`apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- 修改：`apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`

- [ ] **步骤 1：创建 BOM 数据库迁移**

写入 `V4__bom_management_schema.sql`：

```sql
create table mfg_bom (
	id bigserial primary key,
	bom_code varchar(64) not null,
	parent_material_id bigint not null,
	version_code varchar(32) not null,
	name varchar(128) not null,
	base_quantity numeric(18, 6) not null,
	base_unit_id bigint not null,
	status varchar(32) not null,
	effective_from date,
	effective_to date,
	remark varchar(500),
	enabled_by varchar(64),
	enabled_at timestamp with time zone,
	created_by varchar(64) not null,
	created_at timestamp with time zone not null,
	updated_by varchar(64) not null,
	updated_at timestamp with time zone not null,
	version bigint not null default 0,
	constraint uk_mfg_bom_code unique (bom_code),
	constraint uk_mfg_bom_parent_version unique (parent_material_id, version_code),
	constraint fk_mfg_bom_parent_material foreign key (parent_material_id) references mst_material (id),
	constraint fk_mfg_bom_base_unit foreign key (base_unit_id) references mst_unit (id),
	constraint ck_mfg_bom_base_quantity_positive check (base_quantity > 0),
	constraint ck_mfg_bom_status check (status in ('DRAFT', 'ENABLED', 'DISABLED'))
);

create unique index uk_mfg_bom_enabled_parent
	on mfg_bom (parent_material_id)
	where status = 'ENABLED';

create table mfg_bom_item (
	id bigserial primary key,
	bom_id bigint not null,
	line_no integer not null,
	child_material_id bigint not null,
	unit_id bigint not null,
	quantity numeric(18, 6) not null,
	loss_rate numeric(9, 6) not null default 0,
	remark varchar(500),
	created_at timestamp with time zone not null,
	updated_at timestamp with time zone not null,
	constraint fk_mfg_bom_item_bom foreign key (bom_id) references mfg_bom (id) on delete cascade,
	constraint fk_mfg_bom_item_child_material foreign key (child_material_id) references mst_material (id),
	constraint fk_mfg_bom_item_unit foreign key (unit_id) references mst_unit (id),
	constraint uk_mfg_bom_item_line unique (bom_id, line_no),
	constraint uk_mfg_bom_item_material unique (bom_id, child_material_id),
	constraint ck_mfg_bom_item_quantity_positive check (quantity > 0),
	constraint ck_mfg_bom_item_loss_rate_range check (loss_rate >= 0 and loss_rate < 1)
);
```

- [ ] **步骤 2：增加 BOM 错误码**

在 `ApiErrorCode` 中 `MASTER_DATA_UNIT_IN_USE` 之后加入：

```java
BOM_NOT_FOUND(HttpStatus.NOT_FOUND, "BOM 不存在"),

BOM_CODE_EXISTS(HttpStatus.CONFLICT, "BOM 编码已存在"),

BOM_VERSION_EXISTS(HttpStatus.CONFLICT, "同一父项物料的 BOM 版本已存在"),

BOM_ENABLED_VERSION_EXISTS(HttpStatus.CONFLICT, "同一父项物料已存在启用 BOM"),

BOM_STATUS_NOT_EDITABLE(HttpStatus.CONFLICT, "当前 BOM 状态不可编辑"),

BOM_PARENT_MATERIAL_INVALID(HttpStatus.BAD_REQUEST, "BOM 父项物料不正确"),

BOM_CHILD_MATERIAL_INVALID(HttpStatus.BAD_REQUEST, "BOM 子项物料不正确"),

BOM_UNIT_INVALID(HttpStatus.BAD_REQUEST, "BOM 单位不正确"),

BOM_EMPTY_ITEMS(HttpStatus.BAD_REQUEST, "BOM 明细不能为空"),

BOM_QUANTITY_INVALID(HttpStatus.BAD_REQUEST, "BOM 数量或损耗率不正确"),

BOM_DUPLICATE_ITEM(HttpStatus.CONFLICT, "BOM 明细子项重复"),

BOM_SELF_REFERENCE(HttpStatus.BAD_REQUEST, "BOM 父项物料不能作为子项"),

BOM_CYCLE_DETECTED(HttpStatus.CONFLICT, "BOM 存在循环引用"),
```

- [ ] **步骤 3：增加权限种子**

在 `AccountPermissionInitializer.PERMISSION_SEEDS` 中 `master:material:update` 后追加：

```java
new PermissionSeed("material:bom", "BOM 管理", SystemPermissionType.MENU, "material", "/materials/boms",
		null, null, 230),
new PermissionSeed("material:bom:view", "查看 BOM", SystemPermissionType.ACTION, "material:bom",
		"/materials/boms", "GET", "/api/admin/boms/**", 231),
new PermissionSeed("material:bom:create", "创建 BOM", SystemPermissionType.ACTION, "material:bom",
		"/materials/boms", "POST", "/api/admin/boms", 232),
new PermissionSeed("material:bom:update", "更新 BOM", SystemPermissionType.ACTION, "material:bom",
		"/materials/boms", "PUT", "/api/admin/boms/{id}", 233),
new PermissionSeed("material:bom:copy", "复制 BOM 版本", SystemPermissionType.ACTION, "material:bom",
		"/materials/boms", "POST", "/api/admin/boms/{id}/copy", 234),
new PermissionSeed("material:bom:enable", "启用 BOM", SystemPermissionType.ACTION, "material:bom",
		"/materials/boms", "PUT", "/api/admin/boms/{id}/enable", 235),
new PermissionSeed("material:bom:disable", "停用 BOM", SystemPermissionType.ACTION, "material:bom",
		"/materials/boms", "PUT", "/api/admin/boms/{id}/disable", 236)
```

如果列表结尾需要分号，保持最后一项后是 `));`。

- [ ] **步骤 4：增加 BOM 接口鉴权映射**

在 `PermissionAuthorizationManager.permissionCode` 中，在主数据映射之后、系统用户映射之前加入：

```java
String bomPermissionCode = bomPermissionCode(method, path);
if (bomPermissionCode != null) {
	return bomPermissionCode;
}
```

新增方法：

```java
private String bomPermissionCode(String method, String path) {
	String basePath = "/api/admin/boms";
	if (!matchesBasePath(path, basePath)) {
		return null;
	}
	if ("GET".equals(method) && (basePath.equals(path) || matchesIdPath(path, basePath))) {
		return "material:bom:view";
	}
	if ("POST".equals(method) && basePath.equals(path)) {
		return "material:bom:create";
	}
	if ("PUT".equals(method) && matchesIdPath(path, basePath)) {
		return "material:bom:update";
	}
	if ("POST".equals(method) && path.matches(Pattern.quote(basePath) + "/\\d+/copy")) {
		return "material:bom:copy";
	}
	if ("PUT".equals(method) && path.matches(Pattern.quote(basePath) + "/\\d+/enable")) {
		return "material:bom:enable";
	}
	if ("PUT".equals(method) && path.matches(Pattern.quote(basePath) + "/\\d+/disable")) {
		return "material:bom:disable";
	}
	return null;
}
```

该步骤是阻断项；未配置时 `/api/admin/boms` 会被当前默认拒绝策略拦截。

- [ ] **步骤 5：运行后端迁移验证**

运行：

```powershell
cd apps/api
.\mvnw.cmd test -Dtest=AccountPermissionInitializerTests,QherpApiApplicationTests
```

预期：命令退出码为 0，Flyway 能从空库执行 V1 到 V4，权限初始化测试通过。

## 任务 2：后端 BOM 状态、接口和服务

**文件：**

- 创建：`apps/api/src/main/java/com/qherp/api/system/bom/BomStatus.java`
- 创建：`apps/api/src/main/java/com/qherp/api/system/bom/BomAdminController.java`
- 创建：`apps/api/src/main/java/com/qherp/api/system/bom/BomAdminService.java`

- [ ] **步骤 1：创建状态枚举**

`BomStatus.java`：

```java
package com.qherp.api.system.bom;

public enum BomStatus {

	DRAFT,

	ENABLED,

	DISABLED

}
```

- [ ] **步骤 2：创建控制器**

`BomAdminController.java` 必须提供以下方法和路径：

```java
@RestController
@RequestMapping("/api/admin/boms")
public class BomAdminController {

	private final BomAdminService bomAdminService;

	public BomAdminController(BomAdminService bomAdminService) {
		this.bomAdminService = bomAdminService;
	}

	@GetMapping
	public ApiResponse<PageResponse<BomAdminService.BomSummaryResponse>> list(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
			@RequestParam(required = false) Long parentMaterialId, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.bomAdminService.list(keyword, status, parentMaterialId, page, pageSize));
	}

	@GetMapping("/{id}")
	public ApiResponse<BomAdminService.BomDetailResponse> get(@PathVariable Long id) {
		return ApiResponse.ok(this.bomAdminService.get(id));
	}

	@PostMapping
	public ApiResponse<BomAdminService.BomDetailResponse> create(
			@Valid @RequestBody BomAdminService.BomRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.bomAdminService.create(request, currentUser, servletRequest));
	}

	@PutMapping("/{id}")
	public ApiResponse<BomAdminService.BomDetailResponse> update(@PathVariable Long id,
			@Valid @RequestBody BomAdminService.BomRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.bomAdminService.update(id, request, currentUser, servletRequest));
	}

	@PostMapping("/{id}/copy")
	public ApiResponse<BomAdminService.BomDetailResponse> copy(@PathVariable Long id,
			@Valid @RequestBody BomAdminService.BomCopyRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.bomAdminService.copy(id, request, currentUser, servletRequest));
	}

	@PutMapping("/{id}/enable")
	public ApiResponse<BomAdminService.BomDetailResponse> enable(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.bomAdminService.enable(id, currentUser, servletRequest));
	}

	@PutMapping("/{id}/disable")
	public ApiResponse<BomAdminService.BomDetailResponse> disable(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.bomAdminService.disable(id, currentUser, servletRequest));
	}

}
```

- [ ] **步骤 3：实现服务记录类型**

`BomAdminService.java` 至少包含以下请求和响应记录类型：

```java
public record BomItemRequest(Integer lineNo, Long childMaterialId, Long unitId, BigDecimal quantity,
		BigDecimal lossRate, String remark) {
}

public record BomRequest(@NotBlank String bomCode, Long parentMaterialId, @NotBlank String versionCode,
		@NotBlank String name, BigDecimal baseQuantity, Long baseUnitId, String status, LocalDate effectiveFrom,
		LocalDate effectiveTo, String remark, List<BomItemRequest> items) {
}

public record BomCopyRequest(@NotBlank String bomCode, @NotBlank String versionCode, String name,
		LocalDate effectiveFrom, LocalDate effectiveTo, String remark) {
}

public record BomSummaryResponse(Long id, String bomCode, Long parentMaterialId, String parentMaterialCode,
		String parentMaterialName, String versionCode, String name, BigDecimal baseQuantity, Long baseUnitId,
		String baseUnitName, String status, int itemCount, LocalDate effectiveFrom, LocalDate effectiveTo,
		String remark, OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime enabledAt) {
}

public record BomItemResponse(Long id, Integer lineNo, Long childMaterialId, String childMaterialCode,
		String childMaterialName, String childMaterialType, Long unitId, String unitName, BigDecimal quantity,
		BigDecimal lossRate, String remark) {
}

public record BomDetailResponse(Long id, String bomCode, Long parentMaterialId, String parentMaterialCode,
		String parentMaterialName, String versionCode, String name, BigDecimal baseQuantity, Long baseUnitId,
		String baseUnitName, String status, LocalDate effectiveFrom, LocalDate effectiveTo, String remark,
		OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime enabledAt, List<BomItemResponse> items) {
}
```

- [ ] **步骤 4：实现核心业务规则**

服务必须实现以下方法：

```java
public PageResponse<BomSummaryResponse> list(String keyword, String status, Long parentMaterialId, int page,
		int pageSize)
public BomDetailResponse get(Long id)
public BomDetailResponse create(BomRequest request, CurrentUser operator, HttpServletRequest servletRequest)
public BomDetailResponse update(Long id, BomRequest request, CurrentUser operator, HttpServletRequest servletRequest)
public BomDetailResponse copy(Long id, BomCopyRequest request, CurrentUser operator, HttpServletRequest servletRequest)
public BomDetailResponse enable(Long id, CurrentUser operator, HttpServletRequest servletRequest)
public BomDetailResponse disable(Long id, CurrentUser operator, HttpServletRequest servletRequest)
```

关键校验函数命名固定为：

```java
private MaterialRef validateParentMaterial(Long parentMaterialId)
private MaterialRef validateChildMaterial(Long childMaterialId)
private void validateItems(Long parentMaterialId, Long baseUnitId, List<BomItemRequest> items)
private void validateNoCycle(Long currentBomId, Long parentMaterialId, List<BomItemRequest> items)
private void validateEnabledVersionUnique(Long bomId, Long parentMaterialId)
private BomStatus statusOrDraftForCreate(String status)
private BomStatus statusOrCurrentDraftForUpdate(String status, BomStatus currentStatus)
private void validateEnabledUnit(Long unitId)
```

循环检测用递归查询或 Java 队列实现。Java 队列实现时，从当前明细子项开始向下查找 `DRAFT` 和 `ENABLED` BOM 的子项；遇到当前父项即抛出 `BOM_CYCLE_DETECTED`。

创建状态规则：未传 `status` 时保存为 `DRAFT`，传入非 `DRAFT` 状态时返回 `BOM_STATUS_NOT_EDITABLE`。

更新状态规则：只允许当前状态为 `DRAFT` 的 BOM 更新；请求未传 `status` 时保持 `DRAFT`；请求传入非 `DRAFT` 状态时返回 `BOM_STATUS_NOT_EDITABLE`。启用和停用只能走专用接口。

单位规则：`baseUnitId` 为空时使用父项物料基本单位；明细 `unitId` 为空时使用子项物料基本单位；所有基准单位和明细单位都必须存在且启用，否则返回 `BOM_UNIT_INVALID`。

并发启用规则：启用事务中先执行服务层唯一启用版本校验，再执行状态更新；如果数据库部分唯一索引 `uk_mfg_bom_enabled_parent` 在并发场景抛出 `DuplicateKeyException`，必须转换为 `BOM_ENABLED_VERSION_EXISTS`。

- [ ] **步骤 5：实现审计动作**

写操作记录以下动作：

```text
BOM_CREATE
BOM_UPDATE
BOM_COPY
BOM_ENABLE
BOM_DISABLE
```

`target_type` 固定为 `BOM`，`target_id` 使用 BOM 主键，`target_summary` 使用 `bomCode`。

- [ ] **步骤 6：运行后端编译测试**

运行：

```powershell
cd apps/api
.\mvnw.cmd test -Dtest=QherpApiApplicationTests
```

预期：命令退出码为 0，Spring 上下文能加载 BOM 控制器和服务。

## 任务 3：后端 BOM 集成测试

**文件：**

- 创建：`apps/api/src/test/java/com/qherp/api/system/bom/BomAdminControllerTests.java`

- [ ] **步骤 1：创建测试类结构**

测试类使用现有 `PostgresIntegrationTest`、`TestRestTemplate`、`JdbcTemplate` 和 CSRF 登录辅助风格。测试类属性：

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=task6-bom-admin")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BomAdminControllerTests extends PostgresIntegrationTest {

	private static final String UNITS = "/api/admin/master/units";

	private static final String CATEGORIES = "/api/admin/master/material-categories";

	private static final String MATERIALS = "/api/admin/master/materials";

	private static final String BOMS = "/api/admin/boms";

}
```

- [ ] **步骤 2：覆盖管理员生命周期**

新增测试方法 `adminCanManageBomLifecycle`，断言：

```text
管理员创建单位、分类、成品、半成品、原材料和辅料
POST /api/admin/boms 创建草稿返回 200
GET /api/admin/boms?keyword=T6_LIFE_BOM 返回 total = 1
GET /api/admin/boms/{id} 返回明细数量 = 3
PUT /api/admin/boms/{id} 更新草稿明细返回 200
PUT /api/admin/boms/{id}/enable 返回 status = ENABLED
PUT /api/admin/boms/{id} 返回 BOM_STATUS_NOT_EDITABLE
POST /api/admin/boms/{id}/copy 返回 status = DRAFT 且明细完整复制
PUT /api/admin/boms/{id}/disable 返回 status = DISABLED
审计日志包含 BOM_CREATE、BOM_UPDATE、BOM_ENABLE、BOM_COPY、BOM_DISABLE
```

- [ ] **步骤 3：覆盖唯一约束和启用版本唯一**

新增测试方法 `bomCodeVersionAndEnabledVersionMustBeUnique`，断言：

```text
重复 bomCode 返回 BOM_CODE_EXISTS
同父项重复 versionCode 返回 BOM_VERSION_EXISTS
同父项已有 ENABLED 时启用另一版本返回 BOM_ENABLED_VERSION_EXISTS
停用旧启用版本后可启用新版本
```

- [ ] **步骤 4：覆盖父子物料和明细校验**

新增测试方法 `bomReferencesAndItemsMustBeValid`，断言：

```text
父项为原材料返回 BOM_PARENT_MATERIAL_INVALID
父项停用返回 BOM_PARENT_MATERIAL_INVALID
子项停用返回 BOM_CHILD_MATERIAL_INVALID
基准单位不存在或停用返回 BOM_UNIT_INVALID
明细单位不存在或停用返回 BOM_UNIT_INVALID
明细为空返回 BOM_EMPTY_ITEMS
用量为 0 返回 BOM_QUANTITY_INVALID
损耗率为 1 返回 BOM_QUANTITY_INVALID
父项等于子项返回 BOM_SELF_REFERENCE
同一 BOM 重复子项返回 BOM_DUPLICATE_ITEM
```

- [ ] **步骤 5：覆盖循环引用**

新增测试方法 `detectableBomCycleMustBeRejected`，断言：

```text
半成品 B 的 BOM 明细引用原材料 X，保存成功
成品 A 的 BOM 明细引用半成品 B，保存成功
尝试把半成品 B 的 BOM 更新为引用成品 A，返回 BOM_CYCLE_DETECTED
尝试创建父项等于成品 A、子项间接回到成品 A 的 BOM，返回 BOM_CYCLE_DETECTED
```

- [ ] **步骤 6：覆盖未登录、无权限、只读权限和 CSRF**

新增测试方法 `bomAuthorizationMustBeEnforced`，断言：

```text
未登录 GET /api/admin/boms 返回 401 和 AUTH_UNAUTHORIZED
未登录 GET /api/admin/boms/{id} 返回 401 和 AUTH_UNAUTHORIZED
无 BOM 查看权限用户 GET /api/admin/boms 返回 403 和 AUTH_FORBIDDEN
无 BOM 查看权限用户 GET /api/admin/boms/{id} 返回 403 和 AUTH_FORBIDDEN
只读角色具备 material:bom:view
只读用户 GET /api/admin/boms 返回 200
只读用户 POST /api/admin/boms 返回 403 和 AUTH_FORBIDDEN
只读用户 PUT /api/admin/boms/{id}/enable 返回 403 和 AUTH_FORBIDDEN
不带 CSRF 的写请求返回 403
```

- [ ] **步骤 7：运行后端 BOM 测试**

运行：

```powershell
cd apps/api
.\mvnw.cmd test -Dtest=BomAdminControllerTests
```

预期：命令退出码为 0，新增测试全部通过。

## 任务 4：前端 API、路由和菜单入口

**文件：**

- 创建：`apps/web/src/shared/api/bomApi.ts`
- 创建：`apps/web/src/shared/api/bomApi.spec.ts`
- 修改：`apps/web/src/router/index.ts`
- 修改：`apps/web/src/router/permissionGuard.spec.ts`
- 修改：`apps/web/src/App.vue`

- [ ] **步骤 1：创建 BOM API 类型和请求封装**

`bomApi.ts` 导出以下类型：

```ts
export type BomStatus = 'DRAFT' | 'ENABLED' | 'DISABLED'

export interface BomListQuery {
  keyword?: string
  status?: BomStatus
  parentMaterialId?: string | number
  page: number
  pageSize: number
}

export interface BomItemPayload {
  lineNo: number
  childMaterialId: string | number
  unitId?: string | number
  quantity: number
  lossRate?: number
  remark?: string
}

export interface BomPayload {
  bomCode: string
  parentMaterialId: string | number
  versionCode: string
  name: string
  baseQuantity: number
  baseUnitId?: string | number
  effectiveFrom?: string | null
  effectiveTo?: string | null
  remark?: string
  items: BomItemPayload[]
}

export interface BomCopyPayload {
  bomCode: string
  versionCode: string
  name?: string
  effectiveFrom?: string | null
  effectiveTo?: string | null
  remark?: string
}
```

API 必须提供：

```ts
list(query: BomListQuery)
get(id: ResourceId)
create(payload: BomPayload)
update(id: ResourceId, payload: BomPayload)
copy(id: ResourceId, payload: BomCopyPayload)
enable(id: ResourceId)
disable(id: ResourceId)
```

- [ ] **步骤 2：创建 API 测试**

`bomApi.spec.ts` 覆盖：

```text
list 会拼接 keyword、status、parentMaterialId、page、pageSize
create 会先请求 /api/auth/csrf 再 POST /api/admin/boms
update 会 PUT /api/admin/boms/{id}
copy 会 POST /api/admin/boms/{id}/copy
enable 会 PUT /api/admin/boms/{id}/enable
disable 会 PUT /api/admin/boms/{id}/disable
接口错误会抛出 AccountPermissionApiError
```

- [ ] **步骤 3：增加前端路由**

在 `router/index.ts` routes 中加入：

```ts
{
  path: '/materials/boms',
  name: 'material-boms',
  meta: { requiresAuth: true, requiredPermission: 'material:bom:view' },
  component: () => import('../modules/materials/boms/BomListView.vue'),
}
```

- [ ] **步骤 4：更新菜单白名单**

在 `App.vue` 的 `supportedMenuPaths` 加入：

```ts
'/materials/boms',
```

- [ ] **步骤 5：更新路由守卫测试**

在 `permissionGuard.spec.ts` 增加 BOM 组件导入和断言：

```ts
import BomListView from '../modules/materials/boms/BomListView.vue'

expect(router.getRoutes().find((item) => item.name === 'material-boms')?.meta.requiredPermission)
  .toBe('material:bom:view')
```

- [ ] **步骤 6：运行前端 API 和路由测试**

运行：

```powershell
cd apps/web
npm test -- --run src/shared/api/bomApi.spec.ts src/router/permissionGuard.spec.ts
```

预期：命令退出码为 0，BOM API 和路由权限测试通过。

## 任务 5：前端 BOM 页面和组件测试

**文件：**

- 创建：`apps/web/src/modules/materials/boms/BomStatusTag.vue`
- 创建：`apps/web/src/modules/materials/boms/bomPageHelpers.ts`
- 创建：`apps/web/src/modules/materials/boms/BomLineEditor.vue`
- 创建：`apps/web/src/modules/materials/boms/BomListView.vue`
- 创建：`apps/web/src/modules/materials/boms/BomListView.spec.ts`

- [ ] **步骤 1：创建状态标签组件**

`BomStatusTag.vue` 接收 `status: BomStatus`，文案和颜色：

```text
DRAFT -> 草稿 -> info
ENABLED -> 启用 -> success
DISABLED -> 停用 -> warning
```

- [ ] **步骤 2：创建页面辅助函数**

`bomPageHelpers.ts` 导出：

```ts
export function bomStatusLabel(status: BomStatus): string
export function bomStatusTagType(status: BomStatus): 'info' | 'success' | 'warning'
export function positiveNumber(value: unknown): number | null
export function lossRateNumber(value: unknown): number | null
export function nextLineNo(lines: Array<{ lineNo: number }>): number
```

规则：

```text
positiveNumber 只接受大于 0 的有限数字
lossRateNumber 只接受大于等于 0 且小于 1 的有限数字
nextLineNo 按现有最大行号加 10 返回
```

- [ ] **步骤 3：创建明细编辑组件**

`BomLineEditor.vue` 负责：

```text
显示行号、子项物料、用量、单位、损耗率、备注和删除按钮
新增行时自动使用 nextLineNo
删除行不重排已有行号
子项物料下拉只展示启用物料
单位下拉只展示启用单位
无编辑权限或只读状态下禁用所有输入和删除按钮
行级错误展示在对应行下方
```

- [ ] **步骤 4：创建 BOM 列表视图**

`BomListView.vue` 负责：

```text
加载 BOM 列表、启用物料列表和启用单位列表
查询条件包含 keyword、status、parentMaterialId
表格列包含 BOM 编码、父项物料、版本、明细数量、状态、更新时间、操作
创建和编辑使用大抽屉或页面内表单
详情以抽屉展示主表和明细
草稿显示编辑、复制、启用、停用
启用显示详情、复制、停用
停用显示详情、复制
只读用户不显示创建、编辑、复制、启用、停用按钮
接口错误使用页面级 alert 或表单错误展示
保存失败不关闭表单
```

- [ ] **步骤 5：创建组件测试**

`BomListView.spec.ts` 覆盖：

```text
加载列表后显示 BOM 编码、父项物料、版本和状态
点击查询会按 keyword、status、parentMaterialId 调用 list
无数据时显示空状态
有 create 权限时显示新建按钮，无 create 权限时隐藏
草稿行显示编辑和启用按钮，启用行不显示编辑按钮
表单必填为空显示错误
明细用量为 0 显示错误
重复子项显示错误
保存失败时表单仍然可见
点击复制调用 copy
点击启用调用 enable
点击停用调用 disable
```

- [ ] **步骤 6：运行前端组件测试**

运行：

```powershell
cd apps/web
npm test -- --run src/modules/materials/boms/BomListView.spec.ts
```

预期：命令退出码为 0，BOM 页面组件测试通过。

## 任务 6：全量自动化验证

**文件：**

- 修改：实现中涉及的所有后端和前端文件。

- [ ] **步骤 1：运行前端单元测试**

运行：

```powershell
cd apps/web
npm test -- --run
```

预期：命令退出码为 0，所有前端测试通过。

- [ ] **步骤 2：运行前端类型检查**

运行：

```powershell
cd apps/web
npm run typecheck
```

预期：命令退出码为 0，无 TypeScript 类型错误。

- [ ] **步骤 3：运行前端构建**

运行：

```powershell
cd apps/web
npm run build
```

预期：命令退出码为 0，Vite 构建成功。

- [ ] **步骤 4：运行后端集成测试**

运行：

```powershell
cd apps/api
.\mvnw.cmd test
```

预期：命令退出码为 0，后端全部测试通过。

- [ ] **步骤 5：运行 Git 空白检查**

运行：

```powershell
git diff --check
```

预期：命令退出码为 0，无空白错误。

## 任务 7：本地部署、浏览器验收和视觉分析

**文件：**

- 创建：`docs/testing/bom-management-visual-audit/notes.md`
- 创建：`docs/testing/bom-management-visual-audit/*.png`
- 修改：`docs/tasks/006-bom-management-foundation.md`
- 修改：`docs/testing/bom-management-test-plan.md`

- [ ] **步骤 1：启动本地服务**

按现有本地部署方式启动 PostgreSQL、后端和前端。前端验收地址固定记录为：

```text
http://127.0.0.1:5173/materials/boms
```

后端健康检查地址固定记录为：

```text
http://127.0.0.1:18080/api/health
```

- [ ] **步骤 2：执行管理员浏览器验收**

在浏览器中完成：

```text
管理员登录
进入 BOM 管理
新建成品 A 的 V1.0 BOM 草稿
添加原材料 X、半成品 B、辅料 Z 三条明细
保存草稿
查看详情
启用 V1.0
复制 V1.0 为 V1.1
验证同父项启用版本唯一错误
停用 V1.0
启用 V1.1
```

- [ ] **步骤 3：执行异常和权限浏览器验收**

在浏览器中完成：

```text
必填为空
重复版本
重复子项
用量为 0
父项等于子项
停用物料引用
可检测循环引用
只读账号进入列表和详情
只读账号写按钮不可见
只读账号直接调用写接口返回 AUTH_FORBIDDEN
```

- [ ] **步骤 4：保存视觉截图**

截图保存到 `docs/testing/bom-management-visual-audit/`，至少包含：

```text
01-bom-list-desktop.png
02-bom-filter-result-desktop.png
03-bom-empty-state-desktop.png
04-bom-create-form-desktop.png
05-bom-line-editor-desktop.png
06-bom-form-error-desktop.png
07-bom-detail-readonly-desktop.png
08-bom-copy-version-desktop.png
09-bom-enable-conflict-desktop.png
10-bom-cycle-error-desktop.png
11-bom-readonly-permission-desktop.png
12-bom-list-mobile.png
13-bom-form-mobile.png
14-bom-line-editor-mobile.png
```

- [ ] **步骤 5：写视觉分析记录**

`notes.md` 必须包含：

```markdown
# BOM 管理浏览器验收视觉分析

## 验收环境

- 日期：
- 分支：
- 前端地址：
- 后端健康检查：
- 截图方式：
- 管理员账号：
- 只读账号：

## 截图清单

| 文件 | 视口 | 验收内容 | 结论 |
|---|---|---|---|

## 功能验收结果

## 视觉分析结论

## 缺陷处理

## 最终结论
```

视觉分析必须说明布局稳定性、信息密度、关键操作可见性、文案溢出、控件重叠、响应式适配和业务状态识别。

- [ ] **步骤 6：更新任务和测试执行记录**

在 `docs/tasks/006-bom-management-foundation.md` 和 `docs/testing/bom-management-test-plan.md` 追加实际执行结果，至少记录：

```text
自动化测试命令和结果
后端健康检查结果
管理员浏览器验收路径结果
只读和越权验证结果
视觉分析记录位置
缺陷处理结论
是否达到阶段验收标准
```

## 任务 8：提交功能分支成果

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
git add AGENTS.md docs apps
git commit -m "实现BOM管理基础能力"
```

预期：提交成功，提交内容只包含 BOM 管理实现、BOM 文档和浏览器视觉分析记录。

- [ ] **步骤 3：主分支阶段验收准备**

只有在任务 1 到任务 7 全部通过后，才允许合入或推送主分支。合入主分支后必须重新启动本地服务，并向用户提供：

```text
浏览器访问地址
验收模块和范围
已完成内容
自动化验证结果
视觉分析记录和截图位置
已知问题和风险
建议验收路径
```

## 自检清单

- BOM 管理阶段没有引入库存、工单、成本、采购销售、工艺路线、替代料、选配 BOM、工程变更审批或复杂多级展开 UI。
- 任务文档、设计规格、接口契约、测试计划和实施计划范围一致。
- 后端和前端权限编码一致。
- 保存和启用都包含循环引用校验。
- 同父项启用版本唯一由服务层和数据库约束共同保证。
- 启用版本不可直接编辑。
- 浏览器视觉分析有独立目录、截图清单和结论。
