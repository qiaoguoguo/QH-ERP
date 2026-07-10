# 业务期间与历史数据保护基础实施计划

> **给智能体执行者：** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务逐项执行本计划。受本仓库固定角色规则约束，实施时只复用产品经理、UI 设计师、前端开发、后端开发、测试 5 个固定角色子代理，不创建第六类角色。步骤使用复选框（`- [ ]`）跟踪。

**目标：** 建立轻量业务期间与历史数据保护能力，使锁定期间内的库存、采购、销售、生产、成本、财务往来、反向业务和经营报表历史口径不再被后续写入污染。

**架构：** 后端新增 `com.qherp.api.system.period` 领域、V13 迁移、业务期间接口、权限种子和统一 `BusinessPeriodGuard`，所有影响经营口径的写路径在服务层调用同一校验。前端新增业务期间管理页面和 API 封装，复用现有系统管理、统一确认弹窗、分页、权限守卫和页面治理规范。

**技术栈：** Java 21、Spring Boot、Spring Security、JdbcTemplate、Flyway、PostgreSQL、JUnit/Testcontainers、Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、Vitest、Playwright。

## Global Constraints

- 本阶段不是正式财务关账，不做会计期间、总账、凭证、税务、银行、期间快照、审批流、多组织、多公司、多账套或完整 BI。
- 第一版以自然月为默认期间粒度，期间编码使用 `YYYY-MM`，允许手动维护不重叠日期范围。
- 期间状态只包含 `OPEN` 和 `LOCKED`，界面文案为“开放”和“已锁定”。
- 业务日期落入已锁定期间时，后端必须拒绝影响经营口径的写入动作。
- 已锁定期间仍允许查询、来源追溯和经营报表查看。
- 开放期间允许创建反向业务引用锁定期间历史原单，但反向业务自身业务日期必须落入开放期间。
- 前端禁用只做体验，后端 `BusinessPeriodGuard` 是最终安全边界。
- 锁定和解锁必须具备独立权限、填写原因并写入审计日志。
- 页面必须遵守 `docs/ui/page-standards.md`。
- 开发期间只运行受影响验证；阶段交付前统一进入全量验证窗口。

---

## 文件结构

### 后端新增或修改

- 新建：`apps/api/src/main/resources/db/migration/V13__business_period_protection_schema.sql`
- 新建：`apps/api/src/main/java/com/qherp/api/system/period/BusinessPeriodStatus.java`
- 新建：`apps/api/src/main/java/com/qherp/api/system/period/BusinessPeriodOperation.java`
- 新建：`apps/api/src/main/java/com/qherp/api/system/period/BusinessPeriodGuard.java`
- 新建：`apps/api/src/main/java/com/qherp/api/system/period/BusinessPeriodAdminService.java`
- 新建：`apps/api/src/main/java/com/qherp/api/system/period/BusinessPeriodAdminController.java`
- 修改：`apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- 修改：`apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAdminService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/procurement/ProcurementAdminService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/sales/SalesAdminService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/production/ProductionAdminService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/cost/CostAdminService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/finance/FinanceAdminService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/reversal/ReversalAdminService.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/period/BusinessPeriodAdminControllerTests.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`
- 测试：现有模块测试文件按接入范围扩展，例如 `InventoryAdminControllerTests`、`ProcurementAdminControllerTests`、`SalesAdminControllerTests`、`ProductionAdminControllerTests`、`CostAdminControllerTests`、`FinanceAdminControllerTests`、`ReversalAdminControllerTests`、`ReportingAdminControllerTests`。

### 前端新增或修改

- 新建：`apps/web/src/shared/api/businessPeriodApi.ts`
- 测试：`apps/web/src/shared/api/businessPeriodApi.spec.ts`
- 新建：`apps/web/src/modules/system/businessPeriods/BusinessPeriodStatusTag.vue`
- 新建：`apps/web/src/modules/system/businessPeriods/BusinessPeriodFormDialog.vue`
- 新建：`apps/web/src/modules/system/businessPeriods/BusinessPeriodListView.vue`
- 测试：`apps/web/src/modules/system/businessPeriods/BusinessPeriodListView.spec.ts`
- 修改：`apps/web/src/router/index.ts`
- 修改：`apps/web/src/router/permissionGuard.spec.ts`
- 修改：`apps/web/src/App.vue`
- 修改：`apps/web/src/App.spec.ts`
- 修改：相关业务页面错误提示和只读说明，优先通过共享错误处理而不是逐页复制。

### 文档和验收

- 新建：`docs/tasks/016-business-period-protection-foundation.md`
- 新建：`docs/api/business-period-protection-api.md`
- 新建：`docs/testing/business-period-protection-test-plan.md`
- 新建：`docs/testing/business-period-protection-visual-audit/notes.md`
- 修改：`docs/product/product-decisions.md`
- 修改：`docs/product/business-flow.md`
- 修改：`docs/testing/acceptance-criteria.md`
- 修改：`docs/README.md`
- 修改：`docs/manual/system-operation-manual.md`
- 修改：`docs/handoffs/2026-07-06-project-handoff-current.md`

## Task 1: 阶段文档、接口契约和测试矩阵基线

**角色：** 产品经理负责业务范围和任务文档，后端开发负责接口契约，测试负责测试计划，UI 设计师负责视觉验收范围，主代理合并去重。

**Files:**
- Create: `docs/tasks/016-business-period-protection-foundation.md`
- Create: `docs/api/business-period-protection-api.md`
- Create: `docs/testing/business-period-protection-test-plan.md`
- Modify: `docs/product/product-decisions.md`
- Modify: `docs/product/business-flow.md`
- Modify: `docs/testing/acceptance-criteria.md`
- Modify: `docs/README.md`

**Interfaces:**
- Consumes: `docs/superpowers/specs/2026-07-10-business-period-protection-design.md`
- Produces: 阶段任务范围、接口字段、错误码、权限点、验收矩阵；后续 Task 2-4 必须以这些文档为准。

- [ ] **Step 1: 编写任务文档**

在 `docs/tasks/016-business-period-protection-foundation.md` 写入阶段目标、范围、排除项、业务口径、角色分工、验收标准、风险和变更控制。任务文档必须明确本阶段只做“业务期间锁定与历史写入保护”，不做正式财务关账。

必须包含的验收场景：

```markdown
- 锁定业务期间后，业务日期落入该期间的库存、采购、销售、生产、成本、往来和反向业务写入被拒绝。
- 锁定期间仍可查询、追溯和报表查看。
- 开放期间允许创建反向业务引用锁定期间历史原单。
- 无权限用户不能锁定、解锁或绕过接口校验。
- 视觉分析覆盖期间列表、锁定确认、写入失败、只读权限、无权限和 390px 窄屏。
```

- [ ] **Step 2: 编写接口契约**

在 `docs/api/business-period-protection-api.md` 写入接口契约。核心响应类型必须包含：

```json
{
  "id": 1,
  "periodCode": "2026-07",
  "periodName": "2026年07月",
  "startDate": "2026-07-01",
  "endDate": "2026-07-31",
  "status": "OPEN",
  "statusName": "开放",
  "lockedBy": null,
  "lockedAt": null,
  "lockReason": null,
  "unlockedBy": null,
  "unlockedAt": null,
  "unlockReason": null
}
```

必须记录这些接口：

```text
GET /api/admin/system/business-periods
POST /api/admin/system/business-periods
PUT /api/admin/system/business-periods/{id}
POST /api/admin/system/business-periods/generate-monthly
POST /api/admin/system/business-periods/{id}/lock
POST /api/admin/system/business-periods/{id}/unlock
GET /api/admin/system/business-periods/resolve?businessDate=YYYY-MM-DD
```

- [ ] **Step 3: 编写测试计划**

在 `docs/testing/business-period-protection-test-plan.md` 写入自动化、浏览器和视觉验收矩阵。必须列出后端定向测试命令：

```powershell
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=BusinessPeriodAdminControllerTests,PermissionAuthorizationTests,AccountPermissionInitializerTests test
```

必须列出前端定向测试命令：

```powershell
npm --prefix apps/web test -- businessPeriodApi.spec.ts BusinessPeriodListView.spec.ts permissionGuard.spec.ts App.spec.ts
npm --prefix apps/web run typecheck
```

- [ ] **Step 4: 更新索引和产品决策**

更新 `docs/product/product-decisions.md`、`docs/product/business-flow.md`、`docs/testing/acceptance-criteria.md`、`docs/README.md`，记录 016 阶段为业务期间与历史数据保护基础。文案必须避免“结账”“凭证”“总账”等正式财务交付语义，除非写在排除项中。

- [ ] **Step 5: 执行文档自检**

运行：

```powershell
$terms = @('TO' + 'DO', 'TB' + 'D', '待' + '定', '未' + '定', '占' + '位', 'implement ' + 'later', 'fill in ' + 'details') -join '|'
rg -n $terms docs/tasks/016-business-period-protection-foundation.md docs/api/business-period-protection-api.md docs/testing/business-period-protection-test-plan.md docs/product/product-decisions.md docs/product/business-flow.md docs/testing/acceptance-criteria.md docs/README.md
git diff --check -- docs/tasks/016-business-period-protection-foundation.md docs/api/business-period-protection-api.md docs/testing/business-period-protection-test-plan.md docs/product/product-decisions.md docs/product/business-flow.md docs/testing/acceptance-criteria.md docs/README.md
```

Expected: `rg` 无输出；`git diff --check` 退出码为 `0`。

- [ ] **Step 6: 提交文档基线**

```powershell
git add docs/tasks/016-business-period-protection-foundation.md docs/api/business-period-protection-api.md docs/testing/business-period-protection-test-plan.md docs/product/product-decisions.md docs/product/business-flow.md docs/testing/acceptance-criteria.md docs/README.md
git commit -m "建立业务期间保护阶段文档基线"
```

## Task 2: 后端业务期间模型、权限、接口和统一保护服务

**角色：** 后端开发负责实现，产品经理做规格审查，测试做代码质量审查。

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V13__business_period_protection_schema.sql`
- Create: `apps/api/src/main/java/com/qherp/api/system/period/BusinessPeriodStatus.java`
- Create: `apps/api/src/main/java/com/qherp/api/system/period/BusinessPeriodOperation.java`
- Create: `apps/api/src/main/java/com/qherp/api/system/period/BusinessPeriodGuard.java`
- Create: `apps/api/src/main/java/com/qherp/api/system/period/BusinessPeriodAdminService.java`
- Create: `apps/api/src/main/java/com/qherp/api/system/period/BusinessPeriodAdminController.java`
- Modify: `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- Modify: `apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`
- Modify: `apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- Test: `apps/api/src/test/java/com/qherp/api/system/period/BusinessPeriodAdminControllerTests.java`
- Test: `apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`
- Test: `apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`

**Interfaces:**
- Produces: `BusinessPeriodGuard.assertWritable(LocalDate businessDate, BusinessPeriodOperation operation, String sourceType, Long sourceId)`
- Produces: `BusinessPeriodOperation` enum with `CREATE`, `UPDATE`, `CONFIRM`, `POST`, `CANCEL`, `REVERSE`, `ADJUST`
- Produces: period admin endpoints under `/api/admin/system/business-periods`
- Consumes: existing `BusinessException`, `ApiErrorCode`, `CurrentUser`, `AuditService`, `JdbcTemplate`

- [ ] **Step 1: 编写预期失败的后端测试**

在 `BusinessPeriodAdminControllerTests` 新增测试，覆盖迁移、创建、重叠拒绝、按月生成、锁定、解锁和锁定期间写保护。

测试样例必须包含：

```java
@Test
void lockPeriodRejectsWritableBusinessDate() {
	createPeriod("2026-07", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
	lockPeriod("2026-07", "月末业务保护");

	assertThatThrownBy(() -> this.businessPeriodGuard.assertWritable(
			LocalDate.of(2026, 7, 10),
			BusinessPeriodOperation.POST,
			"INVENTORY_DOCUMENT",
			1L))
		.isInstanceOf(BusinessException.class)
		.extracting("errorCode")
		.isEqualTo(ApiErrorCode.BUSINESS_PERIOD_LOCKED);
}
```

- [ ] **Step 2: 运行测试确认失败**

运行：

```powershell
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=BusinessPeriodAdminControllerTests test
```

Expected: 编译失败或测试失败，原因是 `BusinessPeriodGuard`、`BusinessPeriodOperation`、期间接口或 V13 表不存在。

- [ ] **Step 3: 实现 V13 迁移**

创建 `V13__business_period_protection_schema.sql`，包含：

```sql
create table biz_business_period (
    id bigserial primary key,
    period_code varchar(20) not null,
    period_name varchar(80) not null,
    start_date date not null,
    end_date date not null,
    status varchar(20) not null,
    locked_by varchar(80),
    locked_at timestamptz,
    lock_reason varchar(300),
    unlocked_by varchar(80),
    unlocked_at timestamptz,
    unlock_reason varchar(300),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_biz_business_period_code unique (period_code),
    constraint ck_biz_business_period_status check (status in ('OPEN', 'LOCKED')),
    constraint ck_biz_business_period_date_range check (start_date <= end_date)
);

create index idx_biz_business_period_date_range
    on biz_business_period (start_date, end_date);

create table biz_business_period_audit (
    id bigserial primary key,
    period_id bigint references biz_business_period(id),
    period_code varchar(20),
    action varchar(40) not null,
    business_date date,
    source_type varchar(80),
    source_id bigint,
    reason varchar(300),
    operator_username varchar(80) not null,
    created_at timestamptz not null
);

create index idx_biz_business_period_audit_period
    on biz_business_period_audit (period_id, created_at desc);
```

日期范围不重叠校验在服务层实现，并通过测试覆盖。

- [ ] **Step 4: 实现错误码、权限种子和鉴权映射**

在 `ApiErrorCode` 添加：

```java
BUSINESS_PERIOD_LOCKED(HttpStatus.CONFLICT, "业务期间已锁定"),
BUSINESS_PERIOD_OVERLAPPED(HttpStatus.CONFLICT, "业务期间日期范围重叠"),
BUSINESS_PERIOD_DATE_RANGE_INVALID(HttpStatus.BAD_REQUEST, "业务期间日期范围不合法"),
BUSINESS_PERIOD_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "业务期间操作原因必填"),
BUSINESS_PERIOD_NOT_FOUND(HttpStatus.NOT_FOUND, "业务期间不存在"),
BUSINESS_PERIOD_STATUS_INVALID(HttpStatus.CONFLICT, "业务期间状态不允许当前操作"),
```

在 `AccountPermissionInitializer` 添加菜单和权限：

```java
new PermissionSeed("system:business-period", "业务期间", SystemPermissionType.MENU, "system",
		"/system/business-periods", null, null, 50),
new PermissionSeed("system:business-period:view", "查看业务期间", SystemPermissionType.ACTION,
		"system:business-period", "/system/business-periods", "GET",
		"/api/admin/system/business-periods/**", 51),
new PermissionSeed("system:business-period:create", "创建业务期间", SystemPermissionType.ACTION,
		"system:business-period", "/system/business-periods", "POST",
		"/api/admin/system/business-periods", 52),
new PermissionSeed("system:business-period:update", "更新业务期间", SystemPermissionType.ACTION,
		"system:business-period", "/system/business-periods", "PUT",
		"/api/admin/system/business-periods/{id}", 53),
new PermissionSeed("system:business-period:lock", "锁定业务期间", SystemPermissionType.ACTION,
		"system:business-period", "/system/business-periods", "POST",
		"/api/admin/system/business-periods/{id}/lock", 54),
new PermissionSeed("system:business-period:unlock", "解锁业务期间", SystemPermissionType.ACTION,
		"system:business-period", "/system/business-periods", "POST",
		"/api/admin/system/business-periods/{id}/unlock", 55),
```

在 `PermissionAuthorizationManager.permissionCode` 增加 `businessPeriodPermissionCode(method, path)`，映射 `/api/admin/system/business-periods`。

- [ ] **Step 5: 实现期间服务、控制器和 Guard**

实现 `BusinessPeriodAdminService` 和 `BusinessPeriodAdminController`。`BusinessPeriodGuard.assertWritable` 逻辑：

```java
public void assertWritable(LocalDate businessDate, BusinessPeriodOperation operation, String sourceType, Long sourceId) {
	if (businessDate == null) {
		throw new BusinessException(ApiErrorCode.BUSINESS_PERIOD_DATE_RANGE_INVALID);
	}
	BusinessPeriodRow period = findPeriodByBusinessDate(businessDate).orElse(null);
	if (period == null || period.status() == BusinessPeriodStatus.OPEN) {
		return;
	}
	writeGuardAudit(period, operation, businessDate, sourceType, sourceId);
	throw new BusinessException(ApiErrorCode.BUSINESS_PERIOD_LOCKED,
			"业务日期 " + businessDate + " 所属期间 " + period.periodCode() + " 已锁定");
}
```

创建和编辑期间时执行重叠校验：

```sql
select count(*)
from biz_business_period
where id <> coalesce(?, -1)
  and start_date <= ?
  and end_date >= ?
```

- [ ] **Step 6: 运行后端定向测试**

运行：

```powershell
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=BusinessPeriodAdminControllerTests,PermissionAuthorizationTests,AccountPermissionInitializerTests test
```

Expected: 期间基础、权限和鉴权测试通过。

- [ ] **Step 7: 提交后端基础能力**

```powershell
git add apps/api/src/main/resources/db/migration/V13__business_period_protection_schema.sql apps/api/src/main/java/com/qherp/api/system/period apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java apps/api/src/test/java/com/qherp/api/system/period/BusinessPeriodAdminControllerTests.java apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java
git commit -m "实现业务期间基础能力"
```

## Task 3: 跨模块历史写入保护接入

**角色：** 后端开发负责实现，产品经理做规格审查，测试做代码质量审查。

**Files:**
- Modify: `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAdminService.java`
- Modify: `apps/api/src/main/java/com/qherp/api/system/procurement/ProcurementAdminService.java`
- Modify: `apps/api/src/main/java/com/qherp/api/system/sales/SalesAdminService.java`
- Modify: `apps/api/src/main/java/com/qherp/api/system/production/ProductionAdminService.java`
- Modify: `apps/api/src/main/java/com/qherp/api/system/cost/CostAdminService.java`
- Modify: `apps/api/src/main/java/com/qherp/api/system/finance/FinanceAdminService.java`
- Modify: `apps/api/src/main/java/com/qherp/api/system/reversal/ReversalAdminService.java`
- Test: existing controller tests for inventory, procurement, sales, production, cost, finance, reversal, reporting.

**Interfaces:**
- Consumes: `BusinessPeriodGuard.assertWritable(LocalDate, BusinessPeriodOperation, String, Long)`
- Produces: all business write paths consistently throw `BUSINESS_PERIOD_LOCKED` when business date is in a locked period.

- [ ] **Step 1: 编写跨模块失败测试**

在现有模块控制器测试中补充锁定期间拒绝写入。每个测试先创建并锁定 `2026-07`，再尝试写入 `2026-07-10` 业务日期。

库存测试样例：

```java
@Test
void lockedPeriodRejectsInventoryDocumentPost() {
	createAndLockPeriod("2026-07", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
	Long documentId = createInventoryDocument(LocalDate.of(2026, 7, 10));

	putJson("/api/admin/inventory/documents/" + documentId + "/post", null)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BUSINESS_PERIOD_LOCKED"));
}
```

反向业务测试样例：

```java
@Test
void openPeriodAllowsSalesReturnForLockedHistoricalShipment() {
	createAndLockPeriod("2026-06", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
	createOpenPeriod("2026-07", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
	Long shipmentLineId = postedShipmentLine(LocalDate.of(2026, 6, 20));
	Long salesReturnId = createSalesReturn(shipmentLineId, LocalDate.of(2026, 7, 10));

	putJson("/api/admin/sales/returns/" + salesReturnId + "/post", null)
			.andExpect(status().isOk());
}
```

- [ ] **Step 2: 运行测试确认失败**

运行：

```powershell
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=InventoryAdminControllerTests,ProcurementAdminControllerTests,SalesAdminControllerTests,ProductionAdminControllerTests,CostAdminControllerTests,FinanceAdminControllerTests,ReversalAdminControllerTests test
```

Expected: 新增锁定期间测试失败，原因是现有业务服务尚未调用 `BusinessPeriodGuard`。

- [ ] **Step 3: 接入库存、采购和销售写路径**

给 `InventoryAdminService`、`ProcurementAdminService`、`SalesAdminService` 注入 `BusinessPeriodGuard`。接入点：

```java
this.businessPeriodGuard.assertWritable(document.businessDate(), BusinessPeriodOperation.POST,
		"INVENTORY_DOCUMENT", document.id());
this.businessPeriodGuard.assertWritable(order.orderDate(), BusinessPeriodOperation.CONFIRM,
		"PURCHASE_ORDER", order.id());
this.businessPeriodGuard.assertWritable(receipt.businessDate(), BusinessPeriodOperation.POST,
		"PURCHASE_RECEIPT", receipt.id());
this.businessPeriodGuard.assertWritable(order.orderDate(), BusinessPeriodOperation.CONFIRM,
		"SALES_ORDER", order.id());
this.businessPeriodGuard.assertWritable(shipment.businessDate(), BusinessPeriodOperation.POST,
		"SALES_SHIPMENT", shipment.id());
```

草稿创建和更新中也要对 `request.businessDate()` 或 `request.orderDate()` 调用 `assertWritable`，防止保存到锁定期间。

- [ ] **Step 4: 接入生产和成本写路径**

给 `ProductionAdminService` 和 `CostAdminService` 注入 `BusinessPeriodGuard`。接入点：

```java
this.businessPeriodGuard.assertWritable(issue.businessDate(), BusinessPeriodOperation.POST,
		"PRODUCTION_MATERIAL_ISSUE", issue.id());
this.businessPeriodGuard.assertWritable(report.businessDate(), BusinessPeriodOperation.POST,
		"PRODUCTION_WORK_REPORT", report.id());
this.businessPeriodGuard.assertWritable(receipt.businessDate(), BusinessPeriodOperation.POST,
		"PRODUCTION_COMPLETION_RECEIPT", receipt.id());
this.businessPeriodGuard.assertWritable(validated.businessDate(), BusinessPeriodOperation.CREATE,
		"COST_RECORD", null);
this.businessPeriodGuard.assertWritable(row.businessDate(), BusinessPeriodOperation.UPDATE,
		"COST_RECORD", row.id());
```

生产工单本身没有业务日期字段时不新增日期语义；只保护工单下的领料、报工和完工入库业务日期。

- [ ] **Step 5: 接入财务往来和反向业务写路径**

给 `FinanceAdminService` 和 `ReversalAdminService` 注入 `BusinessPeriodGuard`。接入点：

```java
this.businessPeriodGuard.assertWritable(receivable.businessDate(), BusinessPeriodOperation.CONFIRM,
		"FINANCE_RECEIVABLE", receivable.id());
this.businessPeriodGuard.assertWritable(receipt.receiptDate(), BusinessPeriodOperation.POST,
		"FINANCE_RECEIPT", receipt.id());
this.businessPeriodGuard.assertWritable(payable.businessDate(), BusinessPeriodOperation.CONFIRM,
		"FINANCE_PAYABLE", payable.id());
this.businessPeriodGuard.assertWritable(payment.paymentDate(), BusinessPeriodOperation.POST,
		"FINANCE_PAYMENT", payment.id());
this.businessPeriodGuard.assertWritable(document.businessDate(), BusinessPeriodOperation.REVERSE,
		document.sourceType(), document.id());
this.businessPeriodGuard.assertWritable(adjustment.businessDate(), BusinessPeriodOperation.ADJUST,
		"FINANCE_SETTLEMENT_ADJUSTMENT", adjustment.id());
```

保持跨期反向规则：来源原单日期可以在锁定期间，但反向单据自身业务日期必须开放。

- [ ] **Step 6: 运行跨模块定向测试**

运行：

```powershell
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=InventoryAdminControllerTests,ProcurementAdminControllerTests,SalesAdminControllerTests,ProductionAdminControllerTests,CostAdminControllerTests,FinanceAdminControllerTests,ReversalAdminControllerTests,ReportingAdminControllerTests test
```

Expected: 锁定期间拒绝写入、开放期间正常、跨期反向业务和报表查询测试通过。

- [ ] **Step 7: 提交跨模块写保护**

```powershell
git add apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAdminService.java apps/api/src/main/java/com/qherp/api/system/procurement/ProcurementAdminService.java apps/api/src/main/java/com/qherp/api/system/sales/SalesAdminService.java apps/api/src/main/java/com/qherp/api/system/production/ProductionAdminService.java apps/api/src/main/java/com/qherp/api/system/cost/CostAdminService.java apps/api/src/main/java/com/qherp/api/system/finance/FinanceAdminService.java apps/api/src/main/java/com/qherp/api/system/reversal/ReversalAdminService.java apps/api/src/test/java/com/qherp/api/system/inventory/InventoryAdminControllerTests.java apps/api/src/test/java/com/qherp/api/system/procurement/ProcurementAdminControllerTests.java apps/api/src/test/java/com/qherp/api/system/sales/SalesAdminControllerTests.java apps/api/src/test/java/com/qherp/api/system/production/ProductionAdminControllerTests.java apps/api/src/test/java/com/qherp/api/system/cost/CostAdminControllerTests.java apps/api/src/test/java/com/qherp/api/system/finance/FinanceAdminControllerTests.java apps/api/src/test/java/com/qherp/api/system/reversal/ReversalAdminControllerTests.java apps/api/src/test/java/com/qherp/api/system/reporting/ReportingAdminControllerTests.java
git commit -m "接入业务期间历史写入保护"
```

## Task 4: 前端期间管理、浏览器验收和阶段交付

**角色：** 前端开发负责页面实现，UI 设计师做视觉规格审查，测试负责验收执行，主代理负责最终交付判断。

**Files:**
- Create: `apps/web/src/shared/api/businessPeriodApi.ts`
- Test: `apps/web/src/shared/api/businessPeriodApi.spec.ts`
- Create: `apps/web/src/modules/system/businessPeriods/BusinessPeriodStatusTag.vue`
- Create: `apps/web/src/modules/system/businessPeriods/BusinessPeriodFormDialog.vue`
- Create: `apps/web/src/modules/system/businessPeriods/BusinessPeriodListView.vue`
- Test: `apps/web/src/modules/system/businessPeriods/BusinessPeriodListView.spec.ts`
- Modify: `apps/web/src/router/index.ts`
- Modify: `apps/web/src/router/permissionGuard.spec.ts`
- Modify: `apps/web/src/App.vue`
- Modify: `apps/web/src/App.spec.ts`
- Modify: `docs/manual/system-operation-manual.md`
- Create: `docs/testing/business-period-protection-visual-audit/notes.md`

**Interfaces:**
- Consumes: `/api/admin/system/business-periods` endpoints from Task 2.
- Produces: route `system-business-periods`, path `/system/business-periods`, permission `system:business-period:view`.
- Produces: browser and visual evidence for final stage gate.

- [ ] **Step 1: 编写前端 API 失败测试**

在 `businessPeriodApi.spec.ts` 覆盖列表、创建、更新、按月生成、锁定、解锁、resolve 和错误信封。

测试样例：

```ts
it('locks a business period with reason', async () => {
  const calls: Array<{ input: string; init: RequestInit }> = []
  const api = createBusinessPeriodApi({
    fetcher: async (input, init) => {
      calls.push({ input, init })
      if (String(input).endsWith('/api/auth/csrf')) {
        return jsonResponse({ success: true, code: 'OK', message: '成功', data: { token: 'csrf', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' } })
      }
      return jsonResponse({ success: true, code: 'OK', message: '成功', data: businessPeriodRecord({ status: 'LOCKED' }) })
    },
  })

  await api.lock(12, { reason: '月末保护' })

  expect(calls[1].input).toBe('/api/admin/system/business-periods/12/lock')
  expect(calls[1].init.method).toBe('POST')
  expect(calls[1].init.body).toBe(JSON.stringify({ reason: '月末保护' }))
})
```

- [ ] **Step 2: 实现前端 API 和状态标签**

实现 `businessPeriodApi.ts`，导出：

```ts
export type BusinessPeriodStatus = 'OPEN' | 'LOCKED'

export interface BusinessPeriodRecord {
  id: string | number
  periodCode: string
  periodName: string
  startDate: string
  endDate: string
  status: BusinessPeriodStatus
  statusName: string
  lockedBy?: string | null
  lockedAt?: string | null
  lockReason?: string | null
  unlockedBy?: string | null
  unlockedAt?: string | null
  unlockReason?: string | null
}
```

`BusinessPeriodStatusTag.vue` 将 `OPEN` 显示为“开放”，`LOCKED` 显示为“已锁定”。

- [ ] **Step 3: 编写业务期间页面失败测试**

在 `BusinessPeriodListView.spec.ts` 覆盖筛选、分页、新增、编辑、按月生成、锁定、解锁、无权限按钮隐藏和后端错误提示。

必须断言：

```ts
expect(wrapper.text()).toContain('业务期间')
expect(wrapper.text()).toContain('2026-07')
expect(wrapper.text()).toContain('已锁定')
expect(wrapper.text()).toContain('锁定')
expect(wrapper.text()).toContain('解锁')
```

- [ ] **Step 4: 实现业务期间页面、路由和菜单**

新增 `/system/business-periods` 路由：

```ts
{
  path: '/system/business-periods',
  name: 'system-business-periods',
  meta: { requiresAuth: true, requiredPermission: 'system:business-period:view' },
  component: () => import('../modules/system/businessPeriods/BusinessPeriodListView.vue'),
}
```

在 `App.vue` 系统管理菜单加入“业务期间”，图标使用已有系统图标映射，权限为 `system:business-period:view`。

页面必须使用标签置顶搜索栏、横向滚动表格、默认每页 10 条、`10/20/50/100` 分页、统一确认弹窗和可见错误提示。

- [ ] **Step 5: 运行前端定向验证**

运行：

```powershell
npm --prefix apps/web test -- businessPeriodApi.spec.ts BusinessPeriodListView.spec.ts permissionGuard.spec.ts App.spec.ts
npm --prefix apps/web run typecheck
```

Expected: 业务期间 API、页面、路由、菜单和类型检查通过。

- [ ] **Step 6: 更新操作手册和视觉审计记录**

在 `docs/manual/system-operation-manual.md` 增加“业务期间”章节，说明期间创建、锁定、解锁、已锁期间写入失败和跨期反向业务规则。

创建 `docs/testing/business-period-protection-visual-audit/notes.md`，记录至少这些截图：

```text
01-business-period-list-desktop.png
02-business-period-lock-confirm-desktop.png
03-locked-period-write-error-desktop.png
04-business-period-readonly-desktop.png
05-business-period-forbidden-desktop.png
06-business-period-list-mobile-390x844.png
07-report-locked-period-trace-desktop.png
```

- [ ] **Step 7: 执行阶段全量验证窗口**

按本地环境启动 PostgreSQL、后端和前端后执行：

```powershell
docker compose up -d postgres
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd test
npm --prefix apps/web test
npm --prefix apps/web run typecheck
npm --prefix apps/web run build
git diff --check
```

浏览器验收必须覆盖管理员主路径、只读权限、无权限、锁定期间写入失败、开放期间反向业务引用历史原单、报表追溯和 390px 窄屏。

- [ ] **Step 8: 固定五角色集中审查和定向复审**

集中审查只进行一轮：

- 产品经理核对任务文档和验收矩阵。
- UI 设计师检查业务期间列表、确认弹窗、错误态、无权限态和移动端截图。
- 前端开发审查 API、路由、权限、页面和错误处理。
- 后端开发审查 V13、Guard 接入、事务和权限边界。
- 测试审查自动化、浏览器验收、视觉证据和缺陷状态。

阻断和严重问题必须修复；一般问题默认进入后续清单，除非影响当前验收。

- [ ] **Step 9: 提交前端与交付材料**

```powershell
git add apps/web/src/shared/api/businessPeriodApi.ts apps/web/src/shared/api/businessPeriodApi.spec.ts apps/web/src/modules/system/businessPeriods apps/web/src/router/index.ts apps/web/src/router/permissionGuard.spec.ts apps/web/src/App.vue apps/web/src/App.spec.ts docs/manual/system-operation-manual.md docs/testing/business-period-protection-visual-audit
git commit -m "完成业务期间前端与验收材料"
```

## 计划自检清单

- [x] 规格中的期间维护、锁定、解锁、写入保护、查询不受限、跨期反向业务、权限、审计、浏览器验收均有对应任务。
- [x] 每个工作包均包含测试、实现和验证步骤。
- [x] 工作包总数为 4 个，符合阶段整包协作规则。
- [x] 本计划未引入正式财务关账、期间快照、审批流、质量状态或 MRP。
- [x] 本计划没有要求提交当前工作区已有的无关未提交文档改动。
