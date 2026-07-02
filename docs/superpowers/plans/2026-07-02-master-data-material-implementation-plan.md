# 基础资料与物料管理实施计划

> **执行说明：** 按任务逐项执行本计划；推荐使用 `superpowers:subagent-driven-development`，也可使用 `superpowers:executing-plans`。使用复选框语法跟踪进度。

**目标：** 建立计量单位、仓库、供应商、客户、物料分类、物料档案的主数据维护能力，为后续 BOM、库存和生产工单提供稳定数据底座。

**架构：** 后端沿用当前 Spring Boot + JdbcTemplate + Flyway + 集中权限过滤器模式，新增 `master` 领域的迁移、服务、控制器、权限种子和集成测试。前端沿用 Vue 3 + Element Plus + Pinia 认证状态 + 路由守卫模式，新增基础资料与物料管理页面，页面状态保持局部化，通用表格/表单模式从账号权限页面提炼。

**技术栈：** Java 21、Spring Boot、JdbcTemplate、Flyway、PostgreSQL、JUnit/Testcontainers、Vue 3、TypeScript、Element Plus、Vitest、Vite。

---

## 文件结构

后端新增：

- `apps/api/src/main/resources/db/migration/V3__master_data_material_schema.sql`：基础资料与物料主数据表。
- `apps/api/src/main/java/com/qherp/api/system/master/MasterDataStatus.java`：主数据启停状态。
- `apps/api/src/main/java/com/qherp/api/system/master/MaterialType.java`：物料类型枚举。
- `apps/api/src/main/java/com/qherp/api/system/master/MaterialSourceType.java`：物料来源属性枚举。
- `apps/api/src/main/java/com/qherp/api/system/master/MasterDataAdminService.java`：单位、仓库、供应商、客户的通用主数据服务。
- `apps/api/src/main/java/com/qherp/api/system/master/MaterialCategoryAdminService.java`：物料分类服务。
- `apps/api/src/main/java/com/qherp/api/system/master/MaterialAdminService.java`：物料档案服务。
- `apps/api/src/main/java/com/qherp/api/system/master/*AdminController.java`：六组管理接口。
- `apps/api/src/test/java/com/qherp/api/system/master/MasterDataAdminControllerTests.java`：基础资料接口集成测试。
- `apps/api/src/test/java/com/qherp/api/system/master/MaterialAdminControllerTests.java`：物料分类和物料接口集成测试。

后端修改：

- `apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`：补充基础资料与物料菜单/权限种子。
- `apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`：补充 `/api/admin/master/**` 权限映射。
- `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`：补充主数据重复、引用、状态、分类父级和启停引用相关错误码。

前端新增：

- `apps/web/src/shared/api/masterDataApi.ts`：基础资料与物料接口封装。
- `apps/web/src/modules/master/shared/masterPageHelpers.ts`：主数据页面通用状态、校验、类型文案。
- `apps/web/src/modules/master/shared/MasterDataTableView.vue`：单位、仓库、供应商、客户列表页通用壳。
- `apps/web/src/modules/master/units/UnitListView.vue` 与 `.spec.ts`。
- `apps/web/src/modules/master/warehouses/WarehouseListView.vue` 与 `.spec.ts`。
- `apps/web/src/modules/master/suppliers/SupplierListView.vue` 与 `.spec.ts`。
- `apps/web/src/modules/master/customers/CustomerListView.vue` 与 `.spec.ts`。
- `apps/web/src/modules/materials/categories/MaterialCategoryView.vue` 与 `.spec.ts`。
- `apps/web/src/modules/materials/items/MaterialItemListView.vue` 与 `.spec.ts`。

前端修改：

- `apps/web/src/router/index.ts`：新增路由与权限 meta。
- `apps/web/src/App.vue`：把后端菜单白名单加入基础资料和物料路由。
- `apps/web/src/main.ts`：按需注册新增 Element Plus 组件。

文档新增/修改：

- `docs/tasks/005-master-data-material-foundation.md`：任务文档。
- `docs/api/master-data-material-api.md`：接口契约。
- `docs/testing/master-data-material-test-plan.md`：测试计划。
- `docs/testing/master-data-material-visual-audit/notes.md`：浏览器视觉验收记录，实施末期创建。

---

### 任务 1：任务文档、接口契约和测试计划

**文件：**
- 创建：`docs/tasks/005-master-data-material-foundation.md`
- 创建：`docs/api/master-data-material-api.md`
- 创建：`docs/testing/master-data-material-test-plan.md`
- 修改：`docs/product/product-decisions.md`

- [ ] **步骤 1：写任务文档**

创建 `docs/tasks/005-master-data-material-foundation.md`，结构如下：

```markdown
# 任务记录：基础资料与物料管理

## 任务目标

建立制造业 ERP 一期生产主链路所需的主数据底座，覆盖计量单位、仓库、供应商、客户、物料分类和物料档案，为后续 BOM、库存和生产工单提供可引用资料。

## 范围

### 本次包含

- 计量单位：列表、查询、新增、编辑、启用、停用。
- 仓库：列表、查询、新增、编辑、启用、停用。
- 供应商：列表、查询、新增、编辑、启用、停用。
- 客户：列表、查询、新增、编辑、启用、停用。
- 物料分类：平铺接口、前端树形展示、新增、编辑、启用、停用。
- 物料档案：列表、查询、详情、新增、编辑、启用、停用。
- 后端权限点、菜单入口、接口鉴权和审计记录。
- 前端菜单、路由守卫、按钮权限、错误反馈和浏览器视觉验收。

### 本次不包含

- BOM 结构、BOM 版本、替代料、损耗率。
- 库存数量、库存流水、锁定库存、批次、序列号、条码。
- 采购订单、销售订单、价格、应收应付、财务核算。
- 复杂审批、多组织、多租户、附件图片、复杂导入导出。
- 单位换算规则。

## 验收标准

- 管理员可完成六类主数据的新增、查询、编辑、启用、停用。
- 物料可引用已启用单位和已启用分类，不能引用不存在或停用的单位/分类。
- 单位、仓库、供应商、客户、分类、物料编码各自唯一。
- 存在启用子分类或启用物料时，分类不能停用。
- 只读角色只能查看，不能新增、编辑、启用、停用。
- 无权限用户直接调用后端写接口返回 `AUTH_FORBIDDEN`。
- 写操作生成审计日志。
- 前端 `npm test`、`npm run typecheck`、`npm run build` 通过。
- 后端 Docker 化 Maven/Testcontainers 测试通过。
- 本地部署后浏览器完成核心验收路径，并保存视觉分析记录。
```

- [ ] **步骤 2：写接口契约**

创建 `docs/api/master-data-material-api.md`，接口分组如下：

```markdown
# 基础资料与物料管理接口契约

## 通用规则

- 所有接口使用统一 `ApiResponse<T>` 包装。
- 分页接口返回 `PageResponse<T>`。
- 时间字段使用后端生成的 ISO 8601 字符串。
- 写接口需要 CSRF token。
- 认证和权限沿用账号权限模块。

## 通用基础资料字段

| 字段 | 类型 | 请求必填 | 响应必返 | 说明 |
|---|---|---|---|---|
| id | number | 否 | 是 | 后端主键 |
| code | string | 是 | 是 | 同类主数据内唯一 |
| name | string | 是 | 是 | 名称 |
| status | string | 否 | 是 | `ENABLED` 或 `DISABLED` |
| remark | string | 否 | 否 | 备注 |
| createdAt | string | 否 | 是 | 创建时间 |
| updatedAt | string | 否 | 是 | 更新时间 |

状态口径：创建请求中 `status` 可选，未传时后端默认 `ENABLED`；更新请求中 `status` 可选，未传时保留原状态，传入非法值返回 `MASTER_DATA_INVALID_STATUS`。

## 接口分组

| 资源 | 路径 | 能力 |
|---|---|---|
| 计量单位 | `/api/admin/master/units` | list/get/create/update/enable/disable |
| 仓库 | `/api/admin/master/warehouses` | list/get/create/update/enable/disable |
| 供应商 | `/api/admin/master/suppliers` | list/get/create/update/enable/disable |
| 客户 | `/api/admin/master/customers` | list/get/create/update/enable/disable |
| 物料分类 | `/api/admin/master/material-categories` | list/get/create/update/enable/disable |
| 物料档案 | `/api/admin/master/materials` | list/get/create/update/enable/disable |

## 错误码

| 错误码 | HTTP | 场景 |
|---|---:|---|
| MASTER_DATA_CODE_EXISTS | 409 | 同类编码重复 |
| MASTER_DATA_NOT_FOUND | 404 | 主数据不存在 |
| MASTER_DATA_INVALID_STATUS | 400 | 状态非法 |
| MASTER_DATA_REFERENCE_INVALID | 400 | 物料引用不存在或停用的单位/分类 |
| MASTER_DATA_CATEGORY_IN_USE | 409 | 分类存在启用子项或启用物料，不能停用 |
| MASTER_DATA_CATEGORY_PARENT_INVALID | 400 | 分类父级不存在、停用、自引用、挂到自身子级或形成环 |
| MASTER_DATA_UNIT_IN_USE | 409 | 计量单位存在启用物料引用，不能停用 |
```

- [ ] **步骤 3：写测试计划**

创建 `docs/testing/master-data-material-test-plan.md`:

```markdown
# 基础资料与物料管理测试计划

## 准入标准

- 任务文档、接口契约、权限编码和验收路径已确定。
- 账号权限模块在主分支可用。
- 本地 PostgreSQL、后端、前端启动方式可用。

## 准出标准

- 前端 `npm test`、`npm run typecheck`、`npm run build` 通过。
- 后端 Docker 化 Maven/Testcontainers 测试通过。
- 管理员浏览器验收路径通过。
- 只读与越权接口验证通过。
- 视觉分析截图与结论已保存。

## 核心用例

- 六类主数据列表、查询、分页、详情、新增、编辑、启用、停用。
- 编码重复、必填为空、状态非法、ID 不存在。
- 物料引用不存在或停用单位/分类失败。
- 分类存在启用子分类或启用物料时停用失败。
- 只读角色按钮不可见，后端越权返回 `AUTH_FORBIDDEN`。
- 写操作审计记录可查询。

## 浏览器验收路径

1. 管理员登录。
2. 新增单位、仓库、供应商、客户。
3. 新增物料分类。
4. 新增原材料、半成品、成品和辅料 `AUXILIARY`，最小覆盖 `PURCHASED`、`SELF_MADE`、`OUTSOURCED` 来源属性。
5. 查询、编辑、停用物料。
6. 验证重复编码和停用引用错误。
7. 只读用户登录验证权限表现。
8. 保存桌面和窄屏视觉截图。
```

- [ ] **步骤 4：记录产品决策**

修改 `docs/product/product-decisions.md`, add:

```markdown
### 第二个业务模块

第二个业务模块确认为基础资料与物料管理，采用轻量基础资料 + 物料管理范围。

纳入计量单位、仓库、供应商、客户、物料分类和物料档案。该阶段只做主数据维护，不进入 BOM、库存流水、采购销售单据或财务核算。

原因：

- 物料、BOM、库存和生产工单都依赖稳定主数据。
- 仓库、供应商、客户是常规 ERP 后续采购、销售、库存模块的前置资料。
- 提前完成轻量主数据可以避免后续模块反复补字段和补权限。
```

- [ ] **步骤 5：提交文档**

运行：

```powershell
git add docs/tasks/005-master-data-material-foundation.md docs/api/master-data-material-api.md docs/testing/master-data-material-test-plan.md docs/product/product-decisions.md
git commit -m "明确基础资料与物料管理任务范围"
```

预期：提交成功。

---

### 任务 2：后端迁移、枚举和权限种子

**文件：**
- 创建：`apps/api/src/main/resources/db/migration/V3__master_data_material_schema.sql`
- 创建：`apps/api/src/main/java/com/qherp/api/system/master/MasterDataStatus.java`
- 创建：`apps/api/src/main/java/com/qherp/api/system/master/MaterialType.java`
- 创建：`apps/api/src/main/java/com/qherp/api/system/master/MaterialSourceType.java`
- 修改：`apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- 修改：`apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`

- [ ] **步骤 1：写迁移测试期望**

修改 `apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`，在现有初始化测试中补充断言：

```java
assertThat(this.permissionRepository.findByCode("master:unit:view")).isPresent();
assertThat(this.permissionRepository.findByCode("master:warehouse:view")).isPresent();
assertThat(this.permissionRepository.findByCode("master:supplier:view")).isPresent();
assertThat(this.permissionRepository.findByCode("master:customer:view")).isPresent();
assertThat(this.permissionRepository.findByCode("master:material-category:view")).isPresent();
assertThat(this.permissionRepository.findByCode("master:material:view")).isPresent();
```

- [ ] **步骤 2：运行测试确认失败**

运行：

```powershell
$api=(Resolve-Path 'apps/api').Path.Replace('\','/')
docker run --rm -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -v "${api}:/workspace" -v qherp-maven-repo:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -q -Dtest=AccountPermissionInitializerTests test
```

预期：由于新权限尚未初始化，测试失败。

- [ ] **步骤 3：新增数据库迁移**

创建 `apps/api/src/main/resources/db/migration/V3__master_data_material_schema.sql`:

```sql
create table mst_unit (
	id bigint generated by default as identity primary key,
	code varchar(64) not null,
	name varchar(100) not null,
	precision_scale integer not null,
	status varchar(32) not null,
	sort_order integer not null,
	remark varchar(255),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint uk_mst_unit_code unique (code)
);

create table mst_warehouse (
	id bigint generated by default as identity primary key,
	code varchar(64) not null,
	name varchar(100) not null,
	warehouse_type varchar(32),
	manager_name varchar(100),
	address varchar(255),
	status varchar(32) not null,
	remark varchar(255),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint uk_mst_warehouse_code unique (code)
);

create table mst_supplier (
	id bigint generated by default as identity primary key,
	code varchar(64) not null,
	name varchar(100) not null,
	contact_name varchar(100),
	contact_phone varchar(32),
	status varchar(32) not null,
	remark varchar(255),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint uk_mst_supplier_code unique (code)
);

create table mst_customer (
	id bigint generated by default as identity primary key,
	code varchar(64) not null,
	name varchar(100) not null,
	contact_name varchar(100),
	contact_phone varchar(32),
	status varchar(32) not null,
	remark varchar(255),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint uk_mst_customer_code unique (code)
);

create table mst_material_category (
	id bigint generated by default as identity primary key,
	code varchar(64) not null,
	name varchar(100) not null,
	parent_id bigint,
	status varchar(32) not null,
	sort_order integer not null,
	remark varchar(255),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint uk_mst_material_category_code unique (code),
	constraint fk_mst_material_category_parent foreign key (parent_id) references mst_material_category (id)
);

create table mst_material (
	id bigint generated by default as identity primary key,
	code varchar(64) not null,
	name varchar(100) not null,
	specification varchar(255),
	material_type varchar(32) not null,
	source_type varchar(32) not null,
	category_id bigint not null,
	unit_id bigint not null,
	status varchar(32) not null,
	remark varchar(255),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint uk_mst_material_code unique (code),
	constraint fk_mst_material_category foreign key (category_id) references mst_material_category (id),
	constraint fk_mst_material_unit foreign key (unit_id) references mst_unit (id)
);

create index idx_mst_material_category_parent on mst_material_category (parent_id);
create index idx_mst_material_category_status on mst_material_category (status);
create index idx_mst_material_category_unit on mst_material (category_id, unit_id);
create index idx_mst_material_status on mst_material (status);
```

- [ ] **步骤 4：新增枚举**

创建 `apps/api/src/main/java/com/qherp/api/system/master/MasterDataStatus.java`:

```java
package com.qherp.api.system.master;

public enum MasterDataStatus {
	ENABLED,
	DISABLED
}
```

创建 `apps/api/src/main/java/com/qherp/api/system/master/MaterialType.java`:

```java
package com.qherp.api.system.master;

public enum MaterialType {
	RAW_MATERIAL,
	SEMI_FINISHED,
	FINISHED_GOOD,
	AUXILIARY
}
```

创建 `apps/api/src/main/java/com/qherp/api/system/master/MaterialSourceType.java`:

```java
package com.qherp.api.system.master;

public enum MaterialSourceType {
	PURCHASED,
	SELF_MADE,
	OUTSOURCED
}
```

- [ ] **步骤 5：补错误码**

修改 `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`, add enum values:

```java
MASTER_DATA_CODE_EXISTS(HttpStatus.CONFLICT, "主数据编码已存在"),
MASTER_DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "主数据不存在"),
MASTER_DATA_INVALID_STATUS(HttpStatus.BAD_REQUEST, "主数据状态不正确"),
MASTER_DATA_REFERENCE_INVALID(HttpStatus.BAD_REQUEST, "主数据引用不正确"),
MASTER_DATA_CATEGORY_IN_USE(HttpStatus.CONFLICT, "物料分类已被启用数据引用"),
MASTER_DATA_CATEGORY_PARENT_INVALID(HttpStatus.BAD_REQUEST, "物料分类父级不正确"),
MASTER_DATA_UNIT_IN_USE(HttpStatus.CONFLICT, "计量单位已被启用物料引用"),
```

- [ ] **步骤 6：补权限种子**

修改 `apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`，在 `system:audit:view` 后追加权限种子：

```java
new PermissionSeed("master", "基础资料", SystemPermissionType.MENU, null, "/master", null, null, 100),
new PermissionSeed("master:unit", "计量单位", SystemPermissionType.MENU, "master", "/master/units", null, null, 110),
new PermissionSeed("master:unit:view", "查看计量单位", SystemPermissionType.ACTION, "master:unit", "/master/units", "GET", "/api/admin/master/units/**", 111),
new PermissionSeed("master:unit:create", "创建计量单位", SystemPermissionType.ACTION, "master:unit", "/master/units", "POST", "/api/admin/master/units", 112),
new PermissionSeed("master:unit:update", "更新、启用、停用计量单位", SystemPermissionType.ACTION, "master:unit", "/master/units", "PUT", "/api/admin/master/units/**", 113),
new PermissionSeed("master:warehouse", "仓库管理", SystemPermissionType.MENU, "master", "/master/warehouses", null, null, 120),
new PermissionSeed("master:warehouse:view", "查看仓库", SystemPermissionType.ACTION, "master:warehouse", "/master/warehouses", "GET", "/api/admin/master/warehouses/**", 121),
new PermissionSeed("master:warehouse:create", "创建仓库", SystemPermissionType.ACTION, "master:warehouse", "/master/warehouses", "POST", "/api/admin/master/warehouses", 122),
new PermissionSeed("master:warehouse:update", "更新、启用、停用仓库", SystemPermissionType.ACTION, "master:warehouse", "/master/warehouses", "PUT", "/api/admin/master/warehouses/**", 123),
new PermissionSeed("master:supplier", "供应商管理", SystemPermissionType.MENU, "master", "/master/suppliers", null, null, 130),
new PermissionSeed("master:supplier:view", "查看供应商", SystemPermissionType.ACTION, "master:supplier", "/master/suppliers", "GET", "/api/admin/master/suppliers/**", 131),
new PermissionSeed("master:supplier:create", "创建供应商", SystemPermissionType.ACTION, "master:supplier", "/master/suppliers", "POST", "/api/admin/master/suppliers", 132),
new PermissionSeed("master:supplier:update", "更新、启用、停用供应商", SystemPermissionType.ACTION, "master:supplier", "/master/suppliers", "PUT", "/api/admin/master/suppliers/**", 133),
new PermissionSeed("master:customer", "客户管理", SystemPermissionType.MENU, "master", "/master/customers", null, null, 140),
new PermissionSeed("master:customer:view", "查看客户", SystemPermissionType.ACTION, "master:customer", "/master/customers", "GET", "/api/admin/master/customers/**", 141),
new PermissionSeed("master:customer:create", "创建客户", SystemPermissionType.ACTION, "master:customer", "/master/customers", "POST", "/api/admin/master/customers", 142),
new PermissionSeed("master:customer:update", "更新、启用、停用客户", SystemPermissionType.ACTION, "master:customer", "/master/customers", "PUT", "/api/admin/master/customers/**", 143),
new PermissionSeed("material", "物料管理", SystemPermissionType.MENU, null, "/materials", null, null, 200),
new PermissionSeed("master:material-category", "物料分类", SystemPermissionType.MENU, "material", "/materials/categories", null, null, 210),
new PermissionSeed("master:material-category:view", "查看物料分类", SystemPermissionType.ACTION, "master:material-category", "/materials/categories", "GET", "/api/admin/master/material-categories/**", 211),
new PermissionSeed("master:material-category:create", "创建物料分类", SystemPermissionType.ACTION, "master:material-category", "/materials/categories", "POST", "/api/admin/master/material-categories", 212),
new PermissionSeed("master:material-category:update", "更新、启用、停用物料分类", SystemPermissionType.ACTION, "master:material-category", "/materials/categories", "PUT", "/api/admin/master/material-categories/**", 213),
new PermissionSeed("master:material", "物料档案", SystemPermissionType.MENU, "material", "/materials/items", null, null, 220),
new PermissionSeed("master:material:view", "查看物料", SystemPermissionType.ACTION, "master:material", "/materials/items", "GET", "/api/admin/master/materials/**", 221),
new PermissionSeed("master:material:create", "创建物料", SystemPermissionType.ACTION, "master:material", "/materials/items", "POST", "/api/admin/master/materials", 222),
new PermissionSeed("master:material:update", "更新、启用、停用物料", SystemPermissionType.ACTION, "master:material", "/materials/items", "PUT", "/api/admin/master/materials/**", 223)
```

- [ ] **步骤 7：运行初始化测试**

再次运行步骤 2 的命令。

预期：通过。

- [ ] **步骤 8：提交后端迁移和权限种子**

运行：

```powershell
git add apps/api/src/main/resources/db/migration/V3__master_data_material_schema.sql apps/api/src/main/java/com/qherp/api/system/master apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java
git commit -m "建立基础资料与物料数据结构"
```

预期：提交成功。

---

### 任务 3：后端基础资料通用接口

**文件：**
- 创建：`apps/api/src/main/java/com/qherp/api/system/master/MasterDataAdminService.java`
- 创建：`apps/api/src/main/java/com/qherp/api/system/master/UnitAdminController.java`
- 创建：`apps/api/src/main/java/com/qherp/api/system/master/WarehouseAdminController.java`
- 创建：`apps/api/src/main/java/com/qherp/api/system/master/SupplierAdminController.java`
- 创建：`apps/api/src/main/java/com/qherp/api/system/master/CustomerAdminController.java`
- 创建：`apps/api/src/test/java/com/qherp/api/system/master/MasterDataAdminControllerTests.java`
- 修改：`apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`

- [ ] **步骤 1：写失败集成测试**

创建 `apps/api/src/test/java/com/qherp/api/system/master/MasterDataAdminControllerTests.java`，包含以下测试：

```java
package com.qherp.api.system.master;

import com.qherp.api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=master-data-admin")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MasterDataAdminControllerTests extends PostgresIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void adminCanCreateQueryUpdateDisableAndEnableUnitWarehouseSupplierCustomer() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		assertMasterDataLifecycle("/api/admin/master/units",
				Map.of("code", "PCS", "name", "件", "precisionScale", 0, "status", "ENABLED", "sortOrder", 1));
		assertMasterDataLifecycle("/api/admin/master/warehouses",
				Map.of("code", "WH-A", "name", "一号仓", "warehouseType", "RAW", "managerName", "仓管员", "status", "ENABLED"));
		assertMasterDataLifecycle("/api/admin/master/suppliers",
				Map.of("code", "SUP-A", "name", "原料供应商", "contactName", "张三", "contactPhone", "13800000000", "status", "ENABLED"));
		assertMasterDataLifecycle("/api/admin/master/customers",
				Map.of("code", "CUS-A", "name", "成品客户", "contactName", "李四", "contactPhone", "13900000000", "status", "ENABLED"));
	}

	private void assertMasterDataLifecycle(String basePath, Map<String, Object> request) throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		ResponseEntity<String> create = exchange(HttpMethod.POST, basePath, request, admin);
		assertThat(create.getStatusCode()).isEqualTo(HttpStatus.OK);
		long id = data(create).get("id").longValue();
		assertThat(data(create).get("code").asText()).isEqualTo(request.get("code"));

		ResponseEntity<String> list = get(basePath + "?keyword=" + request.get("code") + "&page=1&pageSize=20", admin);
		assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(list.getBody()).contains("\"code\":\"" + request.get("code") + "\"");

		ResponseEntity<String> detail = get(basePath + "/" + id, admin);
		assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);

		ResponseEntity<String> update = exchange(HttpMethod.PUT, basePath + "/" + id,
				new java.util.LinkedHashMap<>(request) {{ put("name", request.get("name") + "改"); }}, admin);
		assertThat(update.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(update.getBody()).contains(String.valueOf(request.get("name")) + "改");

		assertThat(exchange(HttpMethod.PUT, basePath + "/" + id + "/disable", Map.of(), admin).getStatusCode())
			.isEqualTo(HttpStatus.OK);
		assertThat(exchange(HttpMethod.PUT, basePath + "/" + id + "/enable", Map.of(), admin).getStatusCode())
			.isEqualTo(HttpStatus.OK);
	}

	@Test
	void duplicateCodeReturnsConflict() {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		Map<String, Object> request = Map.of("code", "DUP-UNIT", "name", "重复单位",
				"precisionScale", 0, "sortOrder", 1, "status", "ENABLED");
		assertThat(exchange(HttpMethod.POST, "/api/admin/master/units", request, admin).getStatusCode()).isEqualTo(HttpStatus.OK);
		ResponseEntity<String> duplicate = exchange(HttpMethod.POST, "/api/admin/master/units", request, admin);
		assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(duplicate.getBody()).contains("\"code\":\"MASTER_DATA_CODE_EXISTS\"");
	}

	@Test
	void unauthorizedUserCannotCreateMasterData() {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		exchange(HttpMethod.POST, "/api/admin/users",
				Map.of("username", "master-readonly", "displayName", "只读", "initialPassword", "Qherp@2026!", "status", "ENABLED", "roleIds", java.util.List.of()),
				admin);
		AuthenticatedSession readonly = login("master-readonly", "Qherp@2026!");
		ResponseEntity<String> forbidden = exchange(HttpMethod.POST, "/api/admin/master/units",
				Map.of("code", "NO-AUTH", "name", "无权限", "precisionScale", 0, "sortOrder", 1,
						"status", "ENABLED"), readonly);
		assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(forbidden.getBody()).contains("\"code\":\"AUTH_FORBIDDEN\"");
	}

	private AuthenticatedSession login(String username, String password) {
		CsrfSession csrf = csrfSession();
		ResponseEntity<String> response = this.restTemplate.postForEntity("/api/auth/login",
				entity(Map.of("username", username, "password", password), csrf.sessionCookie(), csrf), String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return new AuthenticatedSession(sessionCookie(response), csrf);
	}

	private CsrfSession csrfSession() {
		ResponseEntity<String> response = this.restTemplate.getForEntity("/api/auth/csrf", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		try {
			JsonNode data = data(response);
			return new CsrfSession(sessionCookie(response), data.get("token").asText(), data.get("headerName").asText());
		}
		catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}

	private ResponseEntity<String> get(String path, AuthenticatedSession session) {
		return this.restTemplate.exchange(path, HttpMethod.GET, entity(null, session.sessionCookie(), null), String.class);
	}

	private ResponseEntity<String> exchange(HttpMethod method, String path, Object body, AuthenticatedSession session) {
		return this.restTemplate.exchange(path, method, entity(body, session.sessionCookie(), session.csrfSession()), String.class);
	}

	private HttpEntity<Object> entity(Object body, String cookie, CsrfSession csrf) {
		HttpHeaders headers = new HttpHeaders();
		if (cookie != null) {
			headers.add(HttpHeaders.COOKIE, cookie);
		}
		if (csrf != null) {
			headers.add(csrf.headerName(), csrf.token());
		}
		return new HttpEntity<>(body, headers);
	}

	private JsonNode data(ResponseEntity<String> response) throws Exception {
		return this.objectMapper.readTree(response.getBody()).get("data");
	}

	private String sessionCookie(ResponseEntity<String> response) {
		return response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
			.filter((cookie) -> cookie.startsWith("JSESSIONID="))
			.findFirst()
			.map((cookie) -> cookie.split(";", 2)[0])
			.orElseThrow();
	}

	private record CsrfSession(String sessionCookie, String token, String headerName) {
	}

	private record AuthenticatedSession(String sessionCookie, CsrfSession csrfSession) {
	}
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：

```powershell
$api=(Resolve-Path 'apps/api').Path.Replace('\','/')
docker run --rm -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -v "${api}:/workspace" -v qherp-maven-repo:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -q -Dtest=MasterDataAdminControllerTests test
```

预期：由于控制器尚不存在，测试失败。

- [ ] **步骤 3：实现基础资料服务**

创建 `apps/api/src/main/java/com/qherp/api/system/master/MasterDataAdminService.java`，内容如下：

```java
package com.qherp.api.system.master;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MasterDataAdminService {
	private final JdbcTemplate jdbcTemplate;
	private final AuditService auditService;

	public MasterDataAdminService(JdbcTemplate jdbcTemplate, AuditService auditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public PageResponse<MasterDataResponse> list(Resource resource, String keyword, String status, int page, int pageSize) {
		QueryParts queryParts = queryParts(keyword, status);
		long total = this.jdbcTemplate.queryForObject("select count(*) from " + resource.table + " " + queryParts.where,
				Long.class, queryParts.args.toArray());
		List<Object> args = new ArrayList<>(queryParts.args);
		args.add(Math.min(Math.max(pageSize, 1), 100));
		args.add((Math.max(page, 1) - 1) * Math.min(Math.max(pageSize, 1), 100));
		List<MasterDataResponse> items = this.jdbcTemplate.query(resource.selectSql(queryParts.where),
				(rs, rowNum) -> resource.map(rs), args.toArray());
		return PageResponse.of(items, page, Math.min(Math.max(pageSize, 1), 100), total);
	}

	@Transactional(readOnly = true)
	public MasterDataResponse get(Resource resource, Long id) {
		return this.jdbcTemplate.query(resource.selectOneSql(), (rs, rowNum) -> resource.map(rs), id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.MASTER_DATA_NOT_FOUND));
	}

	@Transactional
	public MasterDataResponse create(Resource resource, MasterDataRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateRequestResource(resource, request);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			Long id = this.jdbcTemplate.queryForObject(resource.insertSql(), Long.class,
					resource.insertArgs(request, statusOrEnabled(request.status()), operator.username(), now));
			this.auditService.record(operator, resource.auditCreate, resource.auditTarget, id, request.code(), servletRequest);
			return get(resource, id);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.MASTER_DATA_CODE_EXISTS);
		}
	}

	@Transactional
	public MasterDataResponse update(Resource resource, Long id, MasterDataRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateRequestResource(resource, request);
		validateExists(resource, id);
		try {
			MasterDataStatus status = statusOrExisting(resource, id, request.status());
			int updated = this.jdbcTemplate.update(resource.updateSql(),
					resource.updateArgs(request, status, operator.username(), OffsetDateTime.now(), id));
			if (updated == 0) {
				throw new BusinessException(ApiErrorCode.MASTER_DATA_NOT_FOUND);
			}
			this.auditService.record(operator, resource.auditUpdate, resource.auditTarget, id, code(resource, id), servletRequest);
			return get(resource, id);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.MASTER_DATA_CODE_EXISTS);
		}
	}

	@Transactional
	public MasterDataResponse enable(Resource resource, Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		return changeStatus(resource, id, MasterDataStatus.ENABLED, operator, servletRequest);
	}

	@Transactional
	public MasterDataResponse disable(Resource resource, Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		if (resource == Resource.UNIT) {
			validateUnitNotUsedByEnabledMaterial(id);
		}
		return changeStatus(resource, id, MasterDataStatus.DISABLED, operator, servletRequest);
	}

	private MasterDataResponse changeStatus(Resource resource, Long id, MasterDataStatus status, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateExists(resource, id);
		this.jdbcTemplate.update("update " + resource.table + " set status = ?, updated_by = ?, updated_at = ?, version = version + 1 where id = ?",
				status.name(), operator.username(), OffsetDateTime.now(), id);
		this.auditService.record(operator, status == MasterDataStatus.ENABLED ? resource.auditEnable : resource.auditDisable,
				resource.auditTarget, id, code(resource, id), servletRequest);
		return get(resource, id);
	}

	private QueryParts queryParts(String keyword, String status) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (keyword != null && !keyword.isBlank()) {
			conditions.add("(code ilike ? or name ilike ?)");
			args.add("%" + keyword + "%");
			args.add("%" + keyword + "%");
		}
		if (status != null && !status.isBlank()) {
			conditions.add("status = ?");
			args.add(parseStatus(status).name());
		}
		return new QueryParts(conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions), args);
	}

	private MasterDataStatus statusOrEnabled(String status) {
		return status == null || status.isBlank() ? MasterDataStatus.ENABLED : parseStatus(status);
	}

	private MasterDataStatus statusOrExisting(Resource resource, Long id, String status) {
		if (status != null && !status.isBlank()) {
			return parseStatus(status);
		}
		String existing = this.jdbcTemplate.queryForObject("select status from " + resource.table + " where id = ?", String.class, id);
		return MasterDataStatus.valueOf(existing);
	}

	private MasterDataStatus parseStatus(String status) {
		try {
			return MasterDataStatus.valueOf(status);
		}
		catch (IllegalArgumentException exception) {
			throw new BusinessException(ApiErrorCode.MASTER_DATA_INVALID_STATUS);
		}
	}

	private void validateExists(Resource resource, Long id) {
		Integer count = this.jdbcTemplate.queryForObject("select count(*) from " + resource.table + " where id = ?", Integer.class, id);
		if (count == null || count == 0) {
			throw new BusinessException(ApiErrorCode.MASTER_DATA_NOT_FOUND);
		}
	}

	private void validateUnitNotUsedByEnabledMaterial(Long unitId) {
		Integer count = this.jdbcTemplate.queryForObject(
				"select count(*) from mst_material where unit_id = ? and status = 'ENABLED'", Integer.class, unitId);
		if (count != null && count > 0) {
			throw new BusinessException(ApiErrorCode.MASTER_DATA_UNIT_IN_USE);
		}
	}

	private String code(Resource resource, Long id) {
		return this.jdbcTemplate.queryForObject("select code from " + resource.table + " where id = ?", String.class, id);
	}

	private void validateRequestResource(Resource resource, MasterDataRequest request) {
		if (!resource.requestType.isInstance(request)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

	private record QueryParts(String where, List<Object> args) {
	}

	public enum Resource {
		UNIT("mst_unit", UnitRequest.class, "UNIT", "UNIT_CREATE", "UNIT_UPDATE", "UNIT_ENABLE", "UNIT_DISABLE"),
		WAREHOUSE("mst_warehouse", WarehouseRequest.class, "WAREHOUSE", "WAREHOUSE_CREATE", "WAREHOUSE_UPDATE", "WAREHOUSE_ENABLE", "WAREHOUSE_DISABLE"),
		SUPPLIER("mst_supplier", PartnerRequest.class, "SUPPLIER", "SUPPLIER_CREATE", "SUPPLIER_UPDATE", "SUPPLIER_ENABLE", "SUPPLIER_DISABLE"),
		CUSTOMER("mst_customer", PartnerRequest.class, "CUSTOMER", "CUSTOMER_CREATE", "CUSTOMER_UPDATE", "CUSTOMER_ENABLE", "CUSTOMER_DISABLE");

		private final String table;
		private final Class<? extends MasterDataRequest> requestType;
		private final String auditTarget;
		private final String auditCreate;
		private final String auditUpdate;
		private final String auditEnable;
		private final String auditDisable;

		Resource(String table, Class<? extends MasterDataRequest> requestType, String auditTarget,
				String auditCreate, String auditUpdate, String auditEnable, String auditDisable) {
			this.table = table;
			this.requestType = requestType;
			this.auditTarget = auditTarget;
			this.auditCreate = auditCreate;
			this.auditUpdate = auditUpdate;
			this.auditEnable = auditEnable;
			this.auditDisable = auditDisable;
		}

		private String selectSql(String where) {
			return selectColumns() + " " + where + " order by id desc limit ? offset ?";
		}

		private String selectOneSql() {
			return selectColumns() + " where id = ?";
		}

		private String selectColumns() {
			return switch (this) {
				case UNIT -> "select id, code, name, precision_scale, sort_order, status, remark, created_at, updated_at from mst_unit";
				case WAREHOUSE -> "select id, code, name, warehouse_type, manager_name, address, status, remark, created_at, updated_at from mst_warehouse";
				case SUPPLIER -> "select id, code, name, contact_name, contact_phone, status, remark, created_at, updated_at from mst_supplier";
				case CUSTOMER -> "select id, code, name, contact_name, contact_phone, status, remark, created_at, updated_at from mst_customer";
			};
		}

		private MasterDataResponse map(ResultSet rs) throws SQLException {
			return switch (this) {
				case UNIT -> new UnitResponse(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
						rs.getString("status"), rs.getString("remark"), rs.getObject("created_at", OffsetDateTime.class),
						rs.getObject("updated_at", OffsetDateTime.class), nullableInt(rs, "precision_scale"),
						nullableInt(rs, "sort_order"));
				case WAREHOUSE -> new WarehouseResponse(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
						rs.getString("status"), rs.getString("remark"), rs.getObject("created_at", OffsetDateTime.class),
						rs.getObject("updated_at", OffsetDateTime.class), rs.getString("warehouse_type"),
						rs.getString("manager_name"), rs.getString("address"));
				case SUPPLIER, CUSTOMER -> new PartnerResponse(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
						rs.getString("status"), rs.getString("remark"), rs.getObject("created_at", OffsetDateTime.class),
						rs.getObject("updated_at", OffsetDateTime.class), rs.getString("contact_name"),
						rs.getString("contact_phone"));
			};
		}

		private String insertSql() {
			return switch (this) {
				case UNIT -> "insert into mst_unit (code, name, precision_scale, sort_order, remark, status, created_by, created_at, updated_by, updated_at) values (?, ?, cast(? as integer), cast(? as integer), ?, ?, ?, ?, ?, ?) returning id";
				case WAREHOUSE -> "insert into mst_warehouse (code, name, warehouse_type, manager_name, address, status, remark, created_by, created_at, updated_by, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id";
				case SUPPLIER -> "insert into mst_supplier (code, name, contact_name, contact_phone, status, remark, created_by, created_at, updated_by, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id";
				case CUSTOMER -> "insert into mst_customer (code, name, contact_name, contact_phone, status, remark, created_by, created_at, updated_by, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id";
			};
		}

		private String updateSql() {
			return switch (this) {
				case UNIT -> "update mst_unit set code = ?, name = ?, precision_scale = cast(? as integer), sort_order = cast(? as integer), remark = ?, status = ?, updated_by = ?, updated_at = ?, version = version + 1 where id = ?";
				case WAREHOUSE -> "update mst_warehouse set code = ?, name = ?, warehouse_type = ?, manager_name = ?, address = ?, status = ?, remark = ?, updated_by = ?, updated_at = ?, version = version + 1 where id = ?";
				case SUPPLIER -> "update mst_supplier set code = ?, name = ?, contact_name = ?, contact_phone = ?, status = ?, remark = ?, updated_by = ?, updated_at = ?, version = version + 1 where id = ?";
				case CUSTOMER -> "update mst_customer set code = ?, name = ?, contact_name = ?, contact_phone = ?, status = ?, remark = ?, updated_by = ?, updated_at = ?, version = version + 1 where id = ?";
			};
		}

		private Object[] insertArgs(MasterDataRequest request, MasterDataStatus status, String operator, OffsetDateTime now) {
			return switch (this) {
				case UNIT -> {
					UnitRequest r = (UnitRequest) request;
					yield new Object[] { r.code(), r.name(), r.requiredPrecisionScale(), r.requiredSortOrder(),
							r.remark(), status.name(), operator, now, operator, now };
				}
				case WAREHOUSE -> {
					WarehouseRequest r = (WarehouseRequest) request;
					yield new Object[] { r.code(), r.name(), r.warehouseType(), r.managerName(), r.address(),
							status.name(), r.remark(), operator, now, operator, now };
				}
				case SUPPLIER, CUSTOMER -> {
					PartnerRequest r = (PartnerRequest) request;
					yield new Object[] { r.code(), r.name(), r.contactName(), r.contactPhone(), status.name(),
							r.remark(), operator, now, operator, now };
				}
			};
		}

		private Object[] updateArgs(MasterDataRequest request, MasterDataStatus status, String operator, OffsetDateTime now, Long id) {
			return switch (this) {
				case UNIT -> {
					UnitRequest r = (UnitRequest) request;
					yield new Object[] { r.code(), r.name(), r.requiredPrecisionScale(), r.requiredSortOrder(),
							r.remark(), status.name(), operator, now, id };
				}
				case WAREHOUSE -> {
					WarehouseRequest r = (WarehouseRequest) request;
					yield new Object[] { r.code(), r.name(), r.warehouseType(), r.managerName(), r.address(), status.name(),
							r.remark(), operator, now, id };
				}
				case SUPPLIER, CUSTOMER -> {
					PartnerRequest r = (PartnerRequest) request;
					yield new Object[] { r.code(), r.name(), r.contactName(), r.contactPhone(), status.name(), r.remark(),
							operator, now, id };
				}
			};
		}
	}

	public sealed interface MasterDataRequest permits UnitRequest, WarehouseRequest, PartnerRequest {
		String code();
		String name();
		String status();
		String remark();
	}

	public record UnitRequest(@NotBlank String code, @NotBlank String name, Integer precisionScale, Integer sortOrder,
			String status, String remark) implements MasterDataRequest {
		int requiredPrecisionScale() {
			if (precisionScale == null || precisionScale < 0) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			return precisionScale;
		}

		int requiredSortOrder() {
			if (sortOrder == null) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			return sortOrder;
		}
	}

	public record WarehouseRequest(@NotBlank String code, @NotBlank String name, String warehouseType,
			String managerName, String address, String status, String remark) implements MasterDataRequest {
	}

	public record PartnerRequest(@NotBlank String code, @NotBlank String name, String contactName,
			String contactPhone, String status, String remark) implements MasterDataRequest {
	}

	public sealed interface MasterDataResponse permits UnitResponse, WarehouseResponse, PartnerResponse {
		Long id();
		String code();
		String name();
		String status();
		String remark();
		OffsetDateTime createdAt();
		OffsetDateTime updatedAt();
	}

	public record UnitResponse(Long id, String code, String name, String status, String remark,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, Integer precisionScale, Integer sortOrder)
			implements MasterDataResponse {
	}

	public record WarehouseResponse(Long id, String code, String name, String status, String remark,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, String warehouseType, String managerName, String address)
			implements MasterDataResponse {
	}

	public record PartnerResponse(Long id, String code, String name, String status, String remark,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, String contactName, String contactPhone)
			implements MasterDataResponse {
	}
}
```

接口 DTO 必须使用上述业务字段名称，不允许把内部列映射占位字段暴露给请求或响应。
`UnitRequest.precisionScale` 和 `UnitRequest.sortOrder` 为创建/更新请求必填字段；缺失时返回 `VALIDATION_ERROR`，服务层不得补 0。
状态处理必须统一：创建请求中 `status` 可选，未传时默认 `ENABLED`；更新请求中 `status` 可选，未传时保留原状态，传入非法值返回 `MASTER_DATA_INVALID_STATUS`。

- [ ] **步骤 4：创建四个控制器**

创建 `UnitAdminController.java`:

```java
package com.qherp.api.system.master;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/master/units")
public class UnitAdminController {
	private final MasterDataAdminService service;
	public UnitAdminController(MasterDataAdminService service) { this.service = service; }
	@GetMapping
	public ApiResponse<PageResponse<MasterDataAdminService.MasterDataResponse>> list(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.service.list(MasterDataAdminService.Resource.UNIT, keyword, status, page, pageSize));
	}
	@GetMapping("/{id}")
	public ApiResponse<MasterDataAdminService.UnitResponse> get(@PathVariable Long id) {
		return ApiResponse.ok((MasterDataAdminService.UnitResponse) this.service.get(MasterDataAdminService.Resource.UNIT, id));
	}
	@PostMapping
	public ApiResponse<MasterDataAdminService.UnitResponse> create(@Valid @RequestBody MasterDataAdminService.UnitRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok((MasterDataAdminService.UnitResponse) this.service.create(MasterDataAdminService.Resource.UNIT, request, currentUser, servletRequest));
	}
	@PutMapping("/{id}")
	public ApiResponse<MasterDataAdminService.UnitResponse> update(@PathVariable Long id,
			@Valid @RequestBody MasterDataAdminService.UnitRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok((MasterDataAdminService.UnitResponse) this.service.update(MasterDataAdminService.Resource.UNIT, id, request, currentUser, servletRequest));
	}
	@PutMapping("/{id}/enable")
	public ApiResponse<MasterDataAdminService.UnitResponse> enable(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok((MasterDataAdminService.UnitResponse) this.service.enable(MasterDataAdminService.Resource.UNIT, id, currentUser, servletRequest));
	}
	@PutMapping("/{id}/disable")
	public ApiResponse<MasterDataAdminService.UnitResponse> disable(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok((MasterDataAdminService.UnitResponse) this.service.disable(MasterDataAdminService.Resource.UNIT, id, currentUser, servletRequest));
	}
}
```

创建其他控制器，公开方法名与 `UnitAdminController` 保持一致，但资源映射必须显式：

| 文件 | `@RequestMapping` | `MasterDataAdminService.Resource` | 请求 DTO | 响应 DTO |
|---|---|---|---|---|
| `WarehouseAdminController.java` | `/api/admin/master/warehouses` | `WAREHOUSE` | `WarehouseRequest` | `WarehouseResponse` |
| `SupplierAdminController.java` | `/api/admin/master/suppliers` | `SUPPLIER` | `PartnerRequest` | `PartnerResponse` |
| `CustomerAdminController.java` | `/api/admin/master/customers` | `CUSTOMER` | `PartnerRequest` | `PartnerResponse` |

每个控制器必须暴露这些方法并委托给自身资源枚举：`list(keyword,status,page,pageSize)`、`get(id)`、`create(request,currentUser,servletRequest)`、`update(id,request,currentUser,servletRequest)`、`enable(id,currentUser,servletRequest)`、`disable(id,currentUser,servletRequest)`。请求和响应体必须使用接口契约中的业务字段名，不能使用内部映射占位字段。创建后运行 `rg -n "Resource\.(UNIT|WAREHOUSE|SUPPLIER|CUSTOMER)" apps/api/src/main/java/com/qherp/api/system/master`，确认每个资源只出现在一个控制器中。

- [ ] **步骤 5：补权限映射**

修改 `PermissionAuthorizationManager.permissionCode`，放在权限树映射之前：

```java
String masterPermission = masterPermission(method, path);
if (masterPermission != null) {
	return masterPermission;
}
```

新增辅助方法：

```java
private String masterPermission(String method, String path) {
	Map<String, String> resources = Map.of(
			"/api/admin/master/units", "master:unit",
			"/api/admin/master/warehouses", "master:warehouse",
			"/api/admin/master/suppliers", "master:supplier",
			"/api/admin/master/customers", "master:customer",
			"/api/admin/master/material-categories", "master:material-category",
			"/api/admin/master/materials", "master:material");
	for (Map.Entry<String, String> entry : resources.entrySet()) {
		String base = entry.getKey();
		String permissionBase = entry.getValue();
		if ("GET".equals(method) && (base.equals(path) || path.matches(base + "/\\d+"))) {
			return permissionBase + ":view";
		}
		if ("POST".equals(method) && base.equals(path)) {
			return permissionBase + ":create";
		}
		if ("PUT".equals(method) && (path.matches(base + "/\\d+") || path.matches(base + "/\\d+/(enable|disable)"))) {
			return permissionBase + ":update";
		}
	}
	return null;
}
```

同时添加 `import java.util.Map;`.

- [ ] **步骤 6：运行基础资料接口测试**

再次运行步骤 2 的命令。

预期：通过。

- [ ] **步骤 7：提交基础资料后端接口**

运行：

```powershell
git add apps/api/src/main/java/com/qherp/api/system/master apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java apps/api/src/test/java/com/qherp/api/system/master/MasterDataAdminControllerTests.java
git commit -m "实现基础资料管理接口"
```

预期：提交成功。

---

### 任务 4：后端物料分类与物料档案接口

**文件：**
- 创建：`apps/api/src/main/java/com/qherp/api/system/master/MaterialCategoryAdminService.java`
- 创建：`apps/api/src/main/java/com/qherp/api/system/master/MaterialCategoryAdminController.java`
- 创建：`apps/api/src/main/java/com/qherp/api/system/master/MaterialAdminService.java`
- 创建：`apps/api/src/main/java/com/qherp/api/system/master/MaterialAdminController.java`
- 创建：`apps/api/src/test/java/com/qherp/api/system/master/MaterialAdminControllerTests.java`

- [ ] **步骤 1：写失败测试**

创建 `apps/api/src/test/java/com/qherp/api/system/master/MaterialAdminControllerTests.java`:

```java
package com.qherp.api.system.master;

import com.qherp.api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=material-admin")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MaterialAdminControllerTests extends PostgresIntegrationTest {
	@Autowired TestRestTemplate restTemplate;
	@Autowired ObjectMapper objectMapper;

	@Test
	void adminCanManageCategoryAndMaterial() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long unitId = createUnit(admin, "KG", "千克");
		long categoryId = createCategory(admin, "RAW", "原材料", null);

		ResponseEntity<String> material = exchange(HttpMethod.POST, "/api/admin/master/materials",
				Map.of("code", "MAT-RAW-001", "name", "冷轧钢板", "specification", "1.0mm",
						"materialType", "RAW_MATERIAL", "sourceType", "PURCHASED",
						"categoryId", categoryId, "unitId", unitId, "status", "ENABLED"), admin);
		assertThat(material.getStatusCode()).isEqualTo(HttpStatus.OK);
		long materialId = data(material).get("id").longValue();

		ResponseEntity<String> list = get("/api/admin/master/materials?keyword=冷轧&page=1&pageSize=20", admin);
		assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(list.getBody()).contains("MAT-RAW-001");

		ResponseEntity<String> detail = get("/api/admin/master/materials/" + materialId, admin);
		assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(detail.getBody()).contains("冷轧钢板");

		ResponseEntity<String> update = exchange(HttpMethod.PUT, "/api/admin/master/materials/" + materialId,
				Map.of("name", "冷轧钢板改", "specification", "1.2mm", "materialType", "RAW_MATERIAL",
						"sourceType", "PURCHASED", "categoryId", categoryId, "unitId", unitId, "status", "ENABLED"),
				admin);
		assertThat(update.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(update.getBody()).contains("冷轧钢板改");

		assertThat(exchange(HttpMethod.PUT, "/api/admin/master/materials/" + materialId + "/disable", Map.of(), admin).getStatusCode())
			.isEqualTo(HttpStatus.OK);
	}

	@Test
	void materialRejectsDisabledUnitOrCategory() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long unitId = createUnit(admin, "PCS-MAT", "件");
		long categoryId = createCategory(admin, "FINISHED", "成品", null);
		exchange(HttpMethod.PUT, "/api/admin/master/units/" + unitId + "/disable", Map.of(), admin);
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/master/materials",
				Map.of("code", "MAT-FIN-001", "name", "整机", "materialType", "FINISHED_GOOD",
						"sourceType", "SELF_MADE", "categoryId", categoryId, "unitId", unitId, "status", "ENABLED"),
				admin);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"MASTER_DATA_REFERENCE_INVALID\"");
	}

	@Test
	void categoryWithEnabledMaterialCannotBeDisabled() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long unitId = createUnit(admin, "SET", "套");
		long categoryId = createCategory(admin, "SEMI", "半成品", null);
		exchange(HttpMethod.POST, "/api/admin/master/materials",
				Map.of("code", "MAT-SEMI-001", "name", "半成品件", "materialType", "SEMI_FINISHED",
						"sourceType", "SELF_MADE", "categoryId", categoryId, "unitId", unitId, "status", "ENABLED"),
				admin);
		ResponseEntity<String> response = exchange(HttpMethod.PUT, "/api/admin/master/material-categories/" + categoryId + "/disable", Map.of(), admin);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).contains("\"code\":\"MASTER_DATA_CATEGORY_IN_USE\"");
	}

	@Test
	void categoryRejectsInvalidParentOnUpdateAndEnable() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long parentId = createCategory(admin, "ROOT", "根分类", null);
		long childId = createCategory(admin, "CHILD", "子分类", parentId);

		ResponseEntity<String> cycle = exchange(HttpMethod.PUT, "/api/admin/master/material-categories/" + parentId,
				Map.of("code", "ROOT", "name", "根分类", "parentId", childId,
						"sortOrder", 1, "status", "ENABLED"), admin);
		assertThat(cycle.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(cycle.getBody()).contains("\"code\":\"MASTER_DATA_CATEGORY_PARENT_INVALID\"");

		exchange(HttpMethod.PUT, "/api/admin/master/material-categories/" + childId + "/disable", Map.of(), admin);
		exchange(HttpMethod.PUT, "/api/admin/master/material-categories/" + parentId + "/disable", Map.of(), admin);
		ResponseEntity<String> enable = exchange(HttpMethod.PUT, "/api/admin/master/material-categories/" + childId + "/enable",
				Map.of(), admin);
		assertThat(enable.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(enable.getBody()).contains("\"code\":\"MASTER_DATA_CATEGORY_PARENT_INVALID\"");
	}

	@Test
	void materialEnableRejectsDisabledReference() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long unitId = createUnit(admin, "PCS-ENABLE", "件");
		long categoryId = createCategory(admin, "AUX", "辅料", null);
		ResponseEntity<String> material = exchange(HttpMethod.POST, "/api/admin/master/materials",
				Map.of("code", "MAT-AUX-001", "name", "外协辅料", "materialType", "AUXILIARY",
						"sourceType", "OUTSOURCED", "categoryId", categoryId, "unitId", unitId, "status", "DISABLED"),
				admin);
		long materialId = data(material).get("id").longValue();
		exchange(HttpMethod.PUT, "/api/admin/master/units/" + unitId + "/disable", Map.of(), admin);

		ResponseEntity<String> response = exchange(HttpMethod.PUT, "/api/admin/master/materials/" + materialId + "/enable",
				Map.of(), admin);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"MASTER_DATA_REFERENCE_INVALID\"");
	}

	@Test
	void unitWithEnabledMaterialCannotBeDisabled() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long unitId = createUnit(admin, "PCS-USED", "件");
		long categoryId = createCategory(admin, "RAW-USED", "原材料", null);
		exchange(HttpMethod.POST, "/api/admin/master/materials",
				Map.of("code", "MAT-RAW-USED", "name", "已启用原料", "materialType", "RAW_MATERIAL",
						"sourceType", "PURCHASED", "categoryId", categoryId, "unitId", unitId, "status", "ENABLED"),
				admin);

		ResponseEntity<String> response = exchange(HttpMethod.PUT, "/api/admin/master/units/" + unitId + "/disable", Map.of(), admin);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).contains("\"code\":\"MASTER_DATA_UNIT_IN_USE\"");
	}

	private long createUnit(AuthenticatedSession admin, String code, String name) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/master/units",
				Map.of("code", code, "name", name, "precisionScale", 0, "sortOrder", 1,
						"status", "ENABLED"), admin);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return data(response).get("id").longValue();
	}

	private long createCategory(AuthenticatedSession admin, String code, String name, Long parentId) throws Exception {
		Map<String, Object> request = new HashMap<>();
		request.put("code", code);
		request.put("name", name);
		request.put("sortOrder", 1);
		request.put("status", "ENABLED");
		if (parentId != null) {
			request.put("parentId", parentId);
		}
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/master/material-categories", request, admin);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return data(response).get("id").longValue();
	}

	private AuthenticatedSession login(String username, String password) {
		CsrfSession csrf = csrfSession();
		ResponseEntity<String> response = this.restTemplate.postForEntity("/api/auth/login",
				entity(Map.of("username", username, "password", password), csrf.sessionCookie(), csrf), String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return new AuthenticatedSession(sessionCookie(response), csrf);
	}

	private CsrfSession csrfSession() {
		ResponseEntity<String> response = this.restTemplate.getForEntity("/api/auth/csrf", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		try {
			JsonNode data = data(response);
			return new CsrfSession(sessionCookie(response), data.get("token").asText(), data.get("headerName").asText());
		}
		catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}

	private ResponseEntity<String> get(String path, AuthenticatedSession session) {
		return this.restTemplate.exchange(path, HttpMethod.GET, entity(null, session.sessionCookie(), null), String.class);
	}
	private ResponseEntity<String> exchange(HttpMethod method, String path, Object body, AuthenticatedSession session) {
		return this.restTemplate.exchange(path, method, entity(body, session.sessionCookie(), session.csrfSession()), String.class);
	}
	private HttpEntity<Object> entity(Object body, String cookie, CsrfSession csrf) {
		HttpHeaders headers = new HttpHeaders();
		if (cookie != null) headers.add(HttpHeaders.COOKIE, cookie);
		if (csrf != null) headers.add(csrf.headerName(), csrf.token());
		return new HttpEntity<>(body, headers);
	}
	private JsonNode data(ResponseEntity<String> response) throws Exception {
		return this.objectMapper.readTree(response.getBody()).get("data");
	}
	private String sessionCookie(ResponseEntity<String> response) {
		return response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
			.filter((cookie) -> cookie.startsWith("JSESSIONID=")).findFirst()
			.map((cookie) -> cookie.split(";", 2)[0]).orElseThrow();
	}
	private record CsrfSession(String sessionCookie, String token, String headerName) {}
	private record AuthenticatedSession(String sessionCookie, CsrfSession csrfSession) {}
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：

```powershell
$api=(Resolve-Path 'apps/api').Path.Replace('\','/')
docker run --rm -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -v "${api}:/workspace" -v qherp-maven-repo:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -q -Dtest=MaterialAdminControllerTests test
```

预期：由于物料控制器尚不存在，测试失败。

- [ ] **步骤 3：实现物料分类服务与控制器**

创建 `MaterialCategoryAdminService.java`，实现以下能力：

必要行为：

| 方法 | 行为 |
|---|---|
| `list(keyword,status,page,pageSize)` | 查询 `mst_material_category`，支持编码/名称关键词、状态、分页，按 `sort_order asc, id desc` 排序。 |
| `get(id)` | 返回单条分类；不存在抛出 `MASTER_DATA_NOT_FOUND`。 |
| `create(request,operator,servletRequest)` | 校验 `sortOrder` 必填，校验父级存在且启用，插入分类，记录 `CATEGORY_CREATE` 审计；`sortOrder` 缺失返回 `VALIDATION_ERROR`，父级无效返回 `MASTER_DATA_CATEGORY_PARENT_INVALID`。 |
| `update(id,request,operator,servletRequest)` | 校验自身存在，校验 `sortOrder` 必填，禁止父级等于自身，禁止挂到自身子级或形成环，校验父级存在且启用，更新分类，记录 `CATEGORY_UPDATE` 审计；`sortOrder` 缺失返回 `VALIDATION_ERROR`，父级无效返回 `MASTER_DATA_CATEGORY_PARENT_INVALID`。 |
| `enable(id,operator,servletRequest)` | 校验分类存在，校验父级仍存在且启用，且不存在自引用、挂到自身子级或父子环；校验通过后设置 `ENABLED`，记录 `CATEGORY_ENABLE` 审计；父级无效返回 `MASTER_DATA_CATEGORY_PARENT_INVALID`。 |
| `disable(id,operator,servletRequest)` | 若存在启用子分类或启用物料，抛出 `MASTER_DATA_CATEGORY_IN_USE`；否则设置 `DISABLED` 并记录 `CATEGORY_DISABLE` 审计。 |

使用以下请求/响应记录：

```java
public record CategoryRequest(@NotBlank String code, @NotBlank String name, Long parentId, String status,
		Integer sortOrder, String remark) {
	public CategoryRequest {
		if (sortOrder == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}
}

public record CategoryResponse(Long id, String code, String name, Long parentId, String status, Integer sortOrder,
		String remark, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
```

`CategoryRequest.sortOrder` 为创建/更新请求必填字段；缺失时返回 `VALIDATION_ERROR`，服务层不得补 0。

创建 `MaterialCategoryAdminController.java`，使用 `@RequestMapping("/api/admin/master/material-categories")`。控制器必须暴露 `GET /`、`GET /{id}`、`POST /`、`PUT /{id}`、`PUT /{id}/enable`、`PUT /{id}/disable`，并委托给 `MaterialCategoryAdminService`。

- [ ] **步骤 4：实现物料档案服务与控制器**

创建 `MaterialAdminService.java`，实现以下能力：

必要行为：

| 方法 | 行为 |
|---|---|
| `list(keyword,status,categoryId,materialType,sourceType,page,pageSize)` | 查询 `mst_material` 并左连接分类、单位名称，支持关键词、状态、分类、物料类型、来源属性筛选。 |
| `get(id)` | 返回物料详情；不存在抛出 `MASTER_DATA_NOT_FOUND`。 |
| `create(request,operator,servletRequest)` | 校验物料编码唯一、分类启用、单位启用、枚举合法，插入物料，记录 `MATERIAL_CREATE` 审计。 |
| `update(id,request,operator,servletRequest)` | 校验物料存在、分类启用、单位启用、枚举合法，更新物料，记录 `MATERIAL_UPDATE` 审计。 |
| `enable(id,operator,servletRequest)` | 校验物料存在，校验引用单位和分类仍存在且启用；校验通过后设置 `ENABLED`，记录 `MATERIAL_ENABLE` 审计；引用无效返回 `MASTER_DATA_REFERENCE_INVALID`。 |
| `disable(id,operator,servletRequest)` | 设置 `DISABLED`，记录 `MATERIAL_DISABLE` 审计。 |

校验规则：

```java
private MaterialType parseMaterialType(String value) {
	try { return MaterialType.valueOf(value); }
	catch (IllegalArgumentException exception) { throw new BusinessException(ApiErrorCode.VALIDATION_ERROR); }
}

private MaterialSourceType parseSourceType(String value) {
	try { return MaterialSourceType.valueOf(value); }
	catch (IllegalArgumentException exception) { throw new BusinessException(ApiErrorCode.VALIDATION_ERROR); }
}

private void validateEnabledUnit(Long unitId) {
	Integer count = jdbcTemplate.queryForObject("select count(*) from mst_unit where id = ? and status = 'ENABLED'", Integer.class, unitId);
	if (count == null || count == 0) throw new BusinessException(ApiErrorCode.MASTER_DATA_REFERENCE_INVALID);
}

private void validateEnabledCategory(Long categoryId) {
	Integer count = jdbcTemplate.queryForObject("select count(*) from mst_material_category where id = ? and status = 'ENABLED'", Integer.class, categoryId);
	if (count == null || count == 0) throw new BusinessException(ApiErrorCode.MASTER_DATA_REFERENCE_INVALID);
}
```

Use request/response records:

```java
public record MaterialRequest(@NotBlank String code, @NotBlank String name, String specification,
		@NotBlank String materialType, @NotBlank String sourceType, Long categoryId, Long unitId,
		String status, String remark) {}

public record MaterialResponse(Long id, String code, String name, String specification, String materialType,
		String sourceType, Long categoryId, String categoryName, Long unitId, String unitName, String status,
		String remark, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
```

创建 `MaterialAdminController.java`，使用 `@RequestMapping("/api/admin/master/materials")`。

- [ ] **步骤 5：运行物料测试**

再次运行步骤 2 的命令。

预期：通过。

- [ ] **步骤 6：运行后端全量测试**

运行：

```powershell
$api=(Resolve-Path 'apps/api').Path.Replace('\','/')
docker run --rm -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -v "${api}:/workspace" -v qherp-maven-repo:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -q test
```

预期：通过。

- [ ] **步骤 7：提交物料后端接口**

运行：

```powershell
git add apps/api/src/main/java/com/qherp/api/system/master apps/api/src/test/java/com/qherp/api/system/master/MaterialAdminControllerTests.java
git commit -m "实现物料分类与物料档案接口"
```

预期：提交成功。

---

### 任务 5：前端 API、路由和通用主数据页面能力

**文件：**
- 创建：`apps/web/src/shared/api/masterDataApi.ts`
- 创建：`apps/web/src/modules/master/shared/masterPageHelpers.ts`
- 创建：`apps/web/src/modules/master/shared/MasterDataTableView.vue`
- 修改：`apps/web/src/router/index.ts`
- 修改：`apps/web/src/App.vue`
- 修改：`apps/web/src/main.ts`
- 创建：`apps/web/src/shared/api/masterDataApi.spec.ts`

- [ ] **步骤 1：写 API 单元测试**

创建 `apps/web/src/shared/api/masterDataApi.spec.ts`:

```ts
import { describe, expect, it, vi } from 'vitest'
import { createMasterDataApi } from './masterDataApi'

describe('masterDataApi', () => {
  it('按资源路径请求基础资料列表和创建接口', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, data: { token: 'csrf', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }, code: 'OK', message: '成功' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, data: { id: 1, code: 'PCS', name: '件', precisionScale: 0, sortOrder: 1, status: 'ENABLED' }, code: 'OK', message: '成功' })))
    const api = createMasterDataApi({ fetcher })

    await api.units.create({ code: 'PCS', name: '件', precisionScale: 0, sortOrder: 1, status: 'ENABLED' })

    expect(fetcher).toHaveBeenLastCalledWith('/api/admin/master/units', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ code: 'PCS', name: '件', precisionScale: 0, sortOrder: 1, status: 'ENABLED' }),
    }))
  })
})
```

- [ ] **步骤 2：运行测试确认失败**

运行：

```powershell
npm test -- masterDataApi.spec.ts
```

在 `apps/web` 目录下运行。

预期：由于 `masterDataApi.ts` 尚不存在，测试失败。

- [ ] **步骤 3：实现 API 封装**

创建 `apps/web/src/shared/api/masterDataApi.ts`:

```ts
import type { ApiEnvelope, CsrfToken, PageResult } from './accountPermissionApi'
import { AccountPermissionApiError } from './accountPermissionApi'

type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type MasterDataStatus = 'ENABLED' | 'DISABLED'
export type MaterialType = 'RAW_MATERIAL' | 'SEMI_FINISHED' | 'FINISHED_GOOD' | 'AUXILIARY'
export type MaterialSourceType = 'PURCHASED' | 'SELF_MADE' | 'OUTSOURCED'

export interface MasterBaseRecord {
  id: string | number
  code: string
  name: string
  status: MasterDataStatus
  remark?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface UnitRecord extends MasterBaseRecord { precisionScale: number; sortOrder: number }
export interface WarehouseRecord extends MasterBaseRecord { warehouseType?: string | null; managerName?: string | null; address?: string | null }
export interface PartnerRecord extends MasterBaseRecord { contactName?: string | null; contactPhone?: string | null }
export interface CategoryRecord extends MasterBaseRecord { parentId?: string | number | null; sortOrder: number }
export interface MaterialRecord extends MasterBaseRecord {
  specification?: string | null
  materialType: MaterialType
  sourceType: MaterialSourceType
  categoryId: string | number
  categoryName: string
  unitId: string | number
  unitName: string
}

export type MasterRecord = UnitRecord | WarehouseRecord | PartnerRecord | CategoryRecord | MaterialRecord

export interface MasterListQuery {
  keyword?: string
  status?: MasterDataStatus
  page: number
  pageSize: number
}

export interface UnitPayload { code: string; name: string; precisionScale: number; sortOrder: number; status?: MasterDataStatus; remark?: string }
export interface WarehousePayload { code: string; name: string; warehouseType?: string; managerName?: string; address?: string; status?: MasterDataStatus; remark?: string }
export interface PartnerPayload { code: string; name: string; contactName?: string; contactPhone?: string; status?: MasterDataStatus; remark?: string }
export interface CategoryPayload { code: string; name: string; parentId?: string | number | null; sortOrder: number; status?: MasterDataStatus; remark?: string }
export interface MaterialPayload {
  code: string
  name: string
  specification?: string
  materialType: MaterialType
  sourceType: MaterialSourceType
  categoryId: string | number
  unitId: string | number
  status?: MasterDataStatus
  remark?: string
}

interface Options { baseUrl?: string; fetcher?: Fetcher }

function createResource<TPayload extends object, TRecord extends MasterBaseRecord>(basePath: string, request: <T>(path: string, init: RequestInit) => Promise<T>, get: <T>(path: string, query?: object) => Promise<T>, write: <T>(method: 'POST' | 'PUT', path: string, body?: unknown) => Promise<T>) {
  return {
    list: (query: MasterListQuery) => get<PageResult<TRecord>>(basePath, query),
    get: (id: string | number) => get<TRecord>(`${basePath}/${encodeURIComponent(String(id))}`),
    create: (payload: TPayload) => write<TRecord>('POST', basePath, payload),
    update: (id: string | number, payload: TPayload) => write<TRecord>('PUT', `${basePath}/${encodeURIComponent(String(id))}`, payload),
    enable: (id: string | number) => write<TRecord>('PUT', `${basePath}/${encodeURIComponent(String(id))}/enable`),
    disable: (id: string | number) => write<TRecord>('PUT', `${basePath}/${encodeURIComponent(String(id))}/disable`),
  }
}

export function createMasterDataApi(options: Options = {}) {
  const fetcher = options.fetcher ?? ((input: string, init: RequestInit) => fetch(input, init))
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '')
  const buildUrl = (path: string, query?: object) => {
    const search = new URLSearchParams()
    Object.entries(query ?? {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
    })
    const queryString = search.toString()
    return `${baseUrl}${path}${queryString ? `?${queryString}` : ''}`
  }
  const request = async <T>(path: string, init: RequestInit): Promise<T> => {
    const response = await fetcher(buildUrl(path), {
      credentials: 'include',
      ...init,
      headers: { Accept: 'application/json', ...(init.headers ?? {}) },
    })
    const envelope = (await response.json()) as ApiEnvelope<T>
    if (!response.ok || !envelope.success) {
      throw new AccountPermissionApiError(envelope.message || `请求失败：${response.status}`, envelope.code || 'HTTP_ERROR', response.status, envelope.traceId)
    }
    return envelope.data
  }
  const get = <T>(path: string, query?: object) => request<T>(buildUrl(path, query).replace(baseUrl, ''), { method: 'GET' })
  const getCsrf = () => request<CsrfToken>('/api/auth/csrf', { method: 'GET' })
  const write = async <T>(method: 'POST' | 'PUT', path: string, body?: unknown): Promise<T> => {
    const csrf = await getCsrf()
    return request<T>(path, { method, body: body === undefined ? undefined : JSON.stringify(body), headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token } })
  }

  return {
    units: createResource<UnitPayload, UnitRecord>('/api/admin/master/units', request, get, write),
    warehouses: createResource<WarehousePayload, WarehouseRecord>('/api/admin/master/warehouses', request, get, write),
    suppliers: createResource<PartnerPayload, PartnerRecord>('/api/admin/master/suppliers', request, get, write),
    customers: createResource<PartnerPayload, PartnerRecord>('/api/admin/master/customers', request, get, write),
    categories: createResource<CategoryPayload, CategoryRecord>('/api/admin/master/material-categories', request, get, write),
    materials: createResource<MaterialPayload, MaterialRecord>('/api/admin/master/materials', request, get, write),
  }
}

export const masterDataApi = createMasterDataApi({ baseUrl: import.meta.env.VITE_API_BASE_URL ?? '' })
```

- [ ] **步骤 4：实现页面 helpers 和通用表格壳**

创建 `apps/web/src/modules/master/shared/masterPageHelpers.ts`:

```ts
import type { MasterDataStatus, MaterialSourceType, MaterialType } from '../../../shared/api/masterDataApi'

export function masterStatusLabel(status?: MasterDataStatus) {
  return status === 'DISABLED' ? '停用' : '启用'
}

export function materialTypeLabel(type?: MaterialType) {
  return {
    RAW_MATERIAL: '原材料',
    SEMI_FINISHED: '半成品',
    FINISHED_GOOD: '成品',
    AUXILIARY: '辅料',
  }[type ?? 'RAW_MATERIAL']
}

export function sourceTypeLabel(type?: MaterialSourceType) {
  return {
    PURCHASED: '外购',
    SELF_MADE: '自制',
    OUTSOURCED: '委外',
  }[type ?? 'PURCHASED']
}
```

创建 `MasterDataTableView.vue`，作为带插槽的轻量组件：

```vue
<template>
  <section class="module-page">
    <header class="page-heading">
      <div>
        <h1>{{ title }}</h1>
        <p>{{ description }}</p>
      </div>
      <slot name="actions" />
    </header>
    <slot name="filters" />
    <slot name="alerts" />
    <slot />
  </section>
</template>

<script setup lang="ts">
defineProps<{ title: string; description: string }>()
</script>
```

- [ ] **步骤 5：路由和菜单白名单**

修改 `apps/web/src/router/index.ts`, add routes:

```ts
{
  path: '/master/units',
  name: 'master-units',
  meta: { requiresAuth: true, requiredPermission: 'master:unit:view' },
  component: () => import('../modules/master/units/UnitListView.vue'),
},
{
  path: '/master/warehouses',
  name: 'master-warehouses',
  meta: { requiresAuth: true, requiredPermission: 'master:warehouse:view' },
  component: () => import('../modules/master/warehouses/WarehouseListView.vue'),
},
{
  path: '/master/suppliers',
  name: 'master-suppliers',
  meta: { requiresAuth: true, requiredPermission: 'master:supplier:view' },
  component: () => import('../modules/master/suppliers/SupplierListView.vue'),
},
{
  path: '/master/customers',
  name: 'master-customers',
  meta: { requiresAuth: true, requiredPermission: 'master:customer:view' },
  component: () => import('../modules/master/customers/CustomerListView.vue'),
},
{
  path: '/materials/categories',
  name: 'material-categories',
  meta: { requiresAuth: true, requiredPermission: 'master:material-category:view' },
  component: () => import('../modules/materials/categories/MaterialCategoryView.vue'),
},
{
  path: '/materials/items',
  name: 'material-items',
  meta: { requiresAuth: true, requiredPermission: 'master:material:view' },
  component: () => import('../modules/materials/items/MaterialItemListView.vue'),
},
```

修改 `apps/web/src/App.vue`, extend `supportedMenuPaths`:

```ts
const supportedMenuPaths = new Set([
  '/accounts/users',
  '/system/users',
  '/accounts/roles',
  '/system/roles',
  '/master/units',
  '/master/warehouses',
  '/master/suppliers',
  '/master/customers',
  '/materials/categories',
  '/materials/items',
])
```

- [ ] **步骤 6：注册新增 Element Plus 组件**

修改 `apps/web/src/main.ts` to add components used by new pages:

```ts
import { ElDrawer, ElTree } from 'element-plus'
import 'element-plus/theme-chalk/el-drawer.css'
import 'element-plus/theme-chalk/el-tree.css'
.use(ElDrawer)
.use(ElTree)
```

- [ ] **步骤 7：运行 API 测试**

运行：

```powershell
npm test -- masterDataApi.spec.ts
```

预期：通过。

- [ ] **步骤 8：提交前端基础能力**

运行：

```powershell
git add apps/web/src/shared/api/masterDataApi.ts apps/web/src/shared/api/masterDataApi.spec.ts apps/web/src/modules/master/shared apps/web/src/router/index.ts apps/web/src/App.vue apps/web/src/main.ts
git commit -m "接入基础资料与物料前端基础能力"
```

预期：提交成功。

---

### 任务 6：前端基础资料列表页面

**文件：**
- 创建/修改：
  - `apps/web/src/modules/master/units/UnitListView.vue`
  - `apps/web/src/modules/master/warehouses/WarehouseListView.vue`
  - `apps/web/src/modules/master/suppliers/SupplierListView.vue`
  - `apps/web/src/modules/master/customers/CustomerListView.vue`
  - corresponding `.spec.ts` files

- [ ] **步骤 1：写单位页面失败测试**

创建 `UnitListView.spec.ts`，内容如下：

```ts
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import UnitListView from './UnitListView.vue'
import { useAuthStore } from '../../../stores/authStore'

const apiMock = vi.hoisted(() => ({
  units: { list: vi.fn(), create: vi.fn(), update: vi.fn(), enable: vi.fn(), disable: vi.fn() },
}))

vi.mock('../../../shared/api/masterDataApi', () => ({ masterDataApi: apiMock }))

function mountPage(permissions = ['master:unit:view', 'master:unit:create', 'master:unit:update']) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({ user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' }, menus: [], permissions })
  return mount(UnitListView, { global: { plugins: [pinia, ElementPlus] } })
}

describe('计量单位页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMock.units.list.mockResolvedValue({ items: [], page: 1, pageSize: 20, total: 0 })
    apiMock.units.create.mockResolvedValue({ id: 1, code: 'PCS', name: '件', precisionScale: 0, sortOrder: 1, status: 'ENABLED' })
    apiMock.units.update.mockResolvedValue({ id: 1, code: 'PCS', name: '件', precisionScale: 0, sortOrder: 1, status: 'ENABLED' })
    apiMock.units.enable.mockResolvedValue({ id: 1, code: 'PCS', name: '件', precisionScale: 0, sortOrder: 1, status: 'ENABLED' })
    apiMock.units.disable.mockResolvedValue({ id: 1, code: 'PCS', name: '件', precisionScale: 0, sortOrder: 1, status: 'DISABLED' })
  })

  it('支持查询、创建和无权限隐藏新增按钮', async () => {
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('计量单位')
    await wrapper.find('[data-test="create-record"]').trigger('click')
    await wrapper.find('input[name="record-code"]').setValue('PCS')
    await wrapper.find('input[name="record-name"]').setValue('件')
    await wrapper.find('[data-test="submit-record"]').trigger('click')
    await flushPromises()
    expect(apiMock.units.create).toHaveBeenCalledWith(expect.objectContaining({ code: 'PCS', name: '件', precisionScale: 0, sortOrder: 1 }))

    const readonly = mountPage(['master:unit:view'])
    await flushPromises()
    expect(readonly.find('[data-test="create-record"]').exists()).toBe(false)
  })
})
```

- [ ] **步骤 2：运行测试确认失败**

运行：

```powershell
npm test -- UnitListView.spec.ts
```

预期：由于页面尚不存在，测试失败。

- [ ] **步骤 3：实现单位页面**

创建 `UnitListView.vue`，沿用 `UserListView.vue` 的本地状态模式。必要行为：

```ts
const canCreate = computed(() => authStore.hasPermission('master:unit:create'))
const canUpdate = computed(() => authStore.hasPermission('master:unit:update'))
const filters = reactive({ keyword: '', status: undefined as MasterDataStatus | undefined })
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })
const records = ref<MasterRecord[]>([])
const form = reactive({ code: '', name: '', precisionScale: 0, sortOrder: 1, status: 'ENABLED' as MasterDataStatus, remark: '' })
```

模板必须包含：

```vue
<MasterDataTableView title="计量单位" description="维护物料、BOM 和生产数量使用的基本单位。">
  <template #actions>
    <el-button v-if="canCreate" data-test="create-record" type="primary" @click="openCreate">新增单位</el-button>
  </template>
  <!-- filters, table, pagination, dialog -->
</MasterDataTableView>
```

提交时调用 `masterDataApi.units.create()` 或 `update()`；保存成功后关闭弹窗并刷新列表；保存失败时展示 `errorMessage(caught)`，并保持弹窗打开。

- [ ] **步骤 4：复制并调整仓库、供应商、客户页面**

基于单位页面改造创建 `WarehouseListView.vue`、`SupplierListView.vue`、`CustomerListView.vue`：

| 页面 | API | 创建权限 | 更新权限 |
|---|---|---|---|
| `WarehouseListView.vue` | `masterDataApi.warehouses` | `master:warehouse:create` | `master:warehouse:update` |
| `SupplierListView.vue` | `masterDataApi.suppliers` | `master:supplier:create` | `master:supplier:update` |
| `CustomerListView.vue` | `masterDataApi.customers` | `master:customer:create` | `master:customer:update` |

每个页面需要一个聚焦的测试文件，参照单位页面测试，验证创建载荷和无权限按钮隐藏。

- [ ] **步骤 5：运行四个页面测试**

运行：

```powershell
npm test -- UnitListView.spec.ts WarehouseListView.spec.ts SupplierListView.spec.ts CustomerListView.spec.ts
```

预期：通过。

- [ ] **步骤 6：提交基础资料页面**

运行：

```powershell
git add apps/web/src/modules/master
git commit -m "实现基础资料前端页面"
```

预期：提交成功。

---

### 任务 7：前端物料分类和物料档案页面

**文件：**
- 创建：`apps/web/src/modules/materials/categories/MaterialCategoryView.vue`
- 创建：`apps/web/src/modules/materials/categories/MaterialCategoryView.spec.ts`
- 创建：`apps/web/src/modules/materials/items/MaterialItemListView.vue`
- 创建：`apps/web/src/modules/materials/items/MaterialItemListView.spec.ts`

- [ ] **步骤 1：写物料分类测试**

创建 `MaterialCategoryView.spec.ts`，包含明确断言：

```ts
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MaterialCategoryView from './MaterialCategoryView.vue'
import { useAuthStore } from '../../../stores/authStore'

const apiMock = vi.hoisted(() => ({
  categories: { list: vi.fn(), create: vi.fn(), update: vi.fn(), enable: vi.fn(), disable: vi.fn() },
}))

vi.mock('../../../shared/api/masterDataApi', () => ({ masterDataApi: apiMock }))

function mountPage(permissions = ['master:material-category:view', 'master:material-category:create', 'master:material-category:update']) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({ user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' }, menus: [], permissions })
  return mount(MaterialCategoryView, { global: { plugins: [pinia, ElementPlus] } })
}

describe('物料分类页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMock.categories.list.mockResolvedValue({ items: [{ id: 1, code: 'RAW', name: '原材料', parentId: null, sortOrder: 1, status: 'ENABLED' }], page: 1, pageSize: 100, total: 1 })
    apiMock.categories.create.mockResolvedValue({ id: 2, code: 'SEMI', name: '半成品', parentId: null, sortOrder: 1, status: 'ENABLED' })
  })

  it('展示分类并提交新增分类', async () => {
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('原材料')
    await wrapper.find('[data-test="create-category"]').trigger('click')
    await wrapper.find('input[name="category-code"]').setValue('SEMI')
    await wrapper.find('input[name="category-name"]').setValue('半成品')
    await wrapper.find('[data-test="submit-category"]').trigger('click')
    await flushPromises()
    expect(apiMock.categories.create).toHaveBeenCalledWith(expect.objectContaining({ code: 'SEMI', name: '半成品', parentId: null, sortOrder: 1, status: 'ENABLED' }))
  })

  it('没有创建权限时隐藏新增按钮', async () => {
    const wrapper = mountPage(['master:material-category:view'])
    await flushPromises()
    expect(wrapper.find('[data-test="create-category"]').exists()).toBe(false)
  })
})
```

- [ ] **步骤 2：写物料档案测试**

创建 `MaterialItemListView.spec.ts`，覆盖物料新增流程：模拟 `materials`、`units` 和 `categories`，打开新增弹窗，填写 code/name/type/source/category/unit/status，提交表单，并断言以下请求载荷：

```ts
expect(apiMock.materials.create).toHaveBeenCalledWith(expect.objectContaining({
  code: 'MAT-RAW-001',
  name: '冷轧钢板',
  materialType: 'RAW_MATERIAL',
  sourceType: 'PURCHASED',
  categoryId: 1,
  unitId: 1,
  status: 'ENABLED',
}))
```

- [ ] **步骤 3：运行测试确认失败**

运行：

```powershell
npm test -- MaterialCategoryView.spec.ts MaterialItemListView.spec.ts
```

预期：由于页面尚不存在，测试失败。

- [ ] **步骤 4：实现物料分类页**

创建 `MaterialCategoryView.vue`:

- 使用 `masterDataApi.categories.list({ page: 1, pageSize: 100, keyword, status })` 加载分类。
- 根据 `parentId` 在前端组装树形结构。
- 展示左侧分类树和右侧表格。
- 表单字段：code、name、parentId、sortOrder、status、remark，其中 sortOrder 必填。
- 权限：`master:material-category:create`、`master:material-category:update`。
- 停用操作使用 `window.confirm('确认停用物料分类“名称”？')` 二次确认。

- [ ] **步骤 5：实现物料档案页**

创建 `MaterialItemListView.vue`:

- 通过 `masterDataApi.materials.list` 加载物料。
- 加载已启用单位和已启用分类供下拉选择。
- 筛选字段：keyword、status、categoryId、materialType、sourceType。
- 表单字段：code、name、specification、materialType、sourceType、categoryId、unitId、status、remark。
- 详情抽屉展示 code、name、specification、categoryName、unitName、materialType 文案、sourceType 文案、status。
- 权限：`master:material:create`、`master:material:update`。
- 保存失败时保持表单打开，并恢复按钮状态。

- [ ] **步骤 6：运行物料页面测试**

运行：

```powershell
npm test -- MaterialCategoryView.spec.ts MaterialItemListView.spec.ts
```

预期：通过。

- [ ] **步骤 7：前端全量验证**

运行：

```powershell
npm test
npm run typecheck
npm run build
```

预期：全部通过。

- [ ] **步骤 8：提交物料前端页面**

运行：

```powershell
git add apps/web/src/modules/materials
git commit -m "实现物料分类与物料档案页面"
```

预期：提交成功。

---

### 任务 8：本地部署、浏览器验收和视觉分析

**文件：**
- 创建：`docs/testing/master-data-material-visual-audit/notes.md`
- 在 `docs/testing/master-data-material-visual-audit/` 下新增截图。
- 修改：`docs/tasks/005-master-data-material-foundation.md`
- 修改：`docs/testing/master-data-material-test-plan.md`

- [ ] **步骤 1：全量自动化验证**

运行后端测试：

```powershell
$api=(Resolve-Path 'apps/api').Path.Replace('\','/')
docker run --rm -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -v "${api}:/workspace" -v qherp-maven-repo:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -q test
```

运行前端验证：

```powershell
cd apps/web
npm test
npm run typecheck
npm run build
```

预期：全部通过。

- [ ] **步骤 2：启动本地服务**

启动 PostgreSQL：

```powershell
docker compose up -d postgres
```

启动后端服务：

```powershell
$api=(Resolve-Path 'apps/api').Path.Replace('\','/')
docker run --rm --name qherp-api-local -p 18080:8080 -e QHERP_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:15432/qherp -e QHERP_DATASOURCE_USERNAME=qherp -e QHERP_DATASOURCE_PASSWORD=qherp_dev_password -v "${api}:/workspace" -v qherp-maven-repo:/root/.m2 -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -q spring-boot:run
```

启动前端服务：

```powershell
cd apps/web
npx vite --host 0.0.0.0 --port 5173
```

- [ ] **步骤 3：健康检查**

运行：

```powershell
Invoke-RestMethod -Uri 'http://localhost:18080/api/health'
Invoke-WebRequest -Uri 'http://127.0.0.1:5173/' -UseBasicParsing
```

预期：API 返回 `{"service":"qherp-api","status":"UP"}`，前端返回 HTTP 200。

- [ ] **步骤 4：浏览器功能验收**

使用内置浏览器：

1. 使用 `admin / Qherp@2026!` 登录。
2. 打开 `/master/units`，创建 `PCS / 件`。
3. 打开 `/master/warehouses`，创建 `WH-A / 一号仓`。
4. 打开 `/master/suppliers`，创建 `SUP-A / 原料供应商`。
5. 打开 `/master/customers`，创建 `CUS-A / 成品客户`。
6. 打开 `/materials/categories`，创建 `RAW / 原材料`、`SEMI / 半成品`、`FIN / 成品`、`AUX / 辅料`，并分别填写排序值。
7. 打开 `/materials/items`，创建：
   - `MAT-RAW-001 / 冷轧钢板 / RAW_MATERIAL / PURCHASED`
   - `MAT-SEMI-001 / 半成品件 / SEMI_FINISHED / SELF_MADE`
   - `MAT-FIN-001 / 整机 / FINISHED_GOOD / SELF_MADE`
   - `MAT-AUX-001 / 外协辅料 / AUXILIARY / OUTSOURCED`
8. 查询并编辑 `MAT-RAW-001`。
9. 停用 `MAT-SEMI-001`。
10. 尝试重复创建 `MAT-RAW-001`，预期展示清晰错误。
11. 使用只读用户登录，或创建 `master-data-readonly` 角色，验证写按钮隐藏且后端写接口返回 `AUTH_FORBIDDEN`。

- [ ] **步骤 5：视觉截图**

保存截图：

```text
docs/testing/master-data-material-visual-audit/00-master-menu.png
docs/testing/master-data-material-visual-audit/01-unit-list.png
docs/testing/master-data-material-visual-audit/02-warehouse-form.png
docs/testing/master-data-material-visual-audit/03-supplier-list.png
docs/testing/master-data-material-visual-audit/04-customer-list.png
docs/testing/master-data-material-visual-audit/05-category-tree.png
docs/testing/master-data-material-visual-audit/06-material-list.png
docs/testing/master-data-material-visual-audit/07-material-form-error.png
docs/testing/master-data-material-visual-audit/08-readonly-view.png
docs/testing/master-data-material-visual-audit/09-mobile-material-list.png
```

- [ ] **步骤 6：写视觉分析记录**

创建 `docs/testing/master-data-material-visual-audit/notes.md`:

```markdown
# 基础资料与物料管理视觉分析记录

## 结论

基础资料与物料管理阶段浏览器视觉验收通过。桌面视口下菜单、查询区、表格、弹窗和抽屉可用；窄屏视口下主内容完整显示，表格可横向滚动，关键按钮不重叠。

## 截图清单

| 文件 | 页面 | 结论 |
|---|---|---|
| 00-master-menu.png | 菜单展开 | 基础资料和物料管理入口可见 |
| 01-unit-list.png | 计量单位 | 列表和状态标签清晰 |
| 02-warehouse-form.png | 仓库表单 | 必填和扩展字段排列清晰 |
| 03-supplier-list.png | 供应商 | 联系字段不过度挤压 |
| 04-customer-list.png | 客户 | 列表信息密度符合后台场景 |
| 05-category-tree.png | 物料分类 | 树和右侧内容联动清楚 |
| 06-material-list.png | 物料档案 | 编码、分类、单位、状态可扫描 |
| 07-material-form-error.png | 表单错误 | 重复编码或停用引用错误可见 |
| 08-readonly-view.png | 只读视图 | 写按钮不可见 |
| 09-mobile-material-list.png | 窄屏物料列表 | 主内容完整，表格横向滚动可用 |
```

- [ ] **步骤 7：更新任务和测试记录**

向 `docs/tasks/005-master-data-material-foundation.md` 和 `docs/testing/master-data-material-test-plan.md` 追加执行记录：

```markdown
## 执行记录

- 本地部署：PostgreSQL、后端 `http://localhost:18080`、前端 `http://127.0.0.1:5173`。
- 自动化验证：前端测试、类型检查、构建通过；后端 Docker 化 Maven/Testcontainers 测试通过。
- 浏览器验收：管理员完成单位、仓库、供应商、客户、分类、物料新增与维护；只读权限和后端越权验证通过。
- 视觉分析：截图和结论见 `docs/testing/master-data-material-visual-audit/notes.md`。
- 结论：达到阶段验收标准，可合入主分支准备用户验收。
```

- [ ] **步骤 8：提交验收记录**

运行：

```powershell
git add docs/tasks/005-master-data-material-foundation.md docs/testing/master-data-material-test-plan.md docs/testing/master-data-material-visual-audit
git commit -m "记录基础资料与物料管理验收结果"
```

预期：提交成功。

---

### 任务 9：合入主分支并推送阶段验收

**文件：** 除非验证发现缺陷，否则不修改源码。

- [ ] **步骤 1：确认工作区干净**

运行：

```powershell
git status --short --branch
```

预期：当前功能分支工作区干净。

- [ ] **步骤 2：合入主分支**

运行：

```powershell
git checkout main
git merge --ff-only codex/master-data-material-design
```


预期：快进合并成功。

- [ ] **步骤 3：推送主分支**

运行：

```powershell
git push origin main
```

预期：推送成功。

- [ ] **步骤 4：保持本地部署可验收**

运行最终检查：

```powershell
Invoke-RestMethod -Uri 'http://localhost:18080/api/health'
Invoke-WebRequest -Uri 'http://127.0.0.1:5173/' -UseBasicParsing
```

预期：两个端点均健康。

- [ ] **步骤 5：通知用户验收**

最终回复必须包含：

```text
爸爸，基础资料与物料管理阶段已完成并推送 main。本地验收地址：http://127.0.0.1:5173
管理员账号：admin / Qherp@2026!
本阶段可验收：计量单位、仓库、供应商、客户、物料分类、物料档案。
```

同时总结自动化测试、浏览器验证、视觉分析路径和明确排除范围。

---

## 自检清单

- 设计规格范围已覆盖：计量单位、仓库、供应商、客户、物料分类、物料档案。
- 明确排除：BOM、库存、采购销售单据、财务、审批、多组织、多租户、单位换算。
- 后端任务覆盖：迁移、枚举、权限种子、权限映射、基础资料接口、物料接口、审计、集成测试。
- 前端任务覆盖：API、路由、菜单白名单、通用页面壳、基础资料页面、物料页面、权限按钮、单元测试。
- 验收任务覆盖：自动化测试、本地部署、浏览器路径、后端越权、视觉截图、主分支推送。
