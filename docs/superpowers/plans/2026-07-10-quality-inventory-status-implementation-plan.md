# 质量检验与库存质量状态基础实施计划

> **给智能体执行者：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务逐项执行本计划。受本仓库固定角色规则约束，实施时只复用产品经理、UI 设计师、前端开发、后端开发、测试 5 个固定角色子代理，不创建第六类角色，不按任务反复创建和关闭临时子代理。步骤使用复选框（`- [ ]`）跟踪。

**目标：** 建立 017 质量检验与库存质量状态基础能力，使库存余额、库存流水、入库、出库、质量确认、权限、期间保护和页面验收都以“合格库存才可用”为可信口径。

**架构：** 后端以库存服务为唯一余额边界，将 `inv_stock_balance` 和 `inv_stock_movement` 扩展到质量状态维度，并新增 `com.qherp.api.system.quality` 轻量质量确认领域。前端新增质量确认页面和质量状态组件，改造库存余额展示与消耗候选交互；采购、销售、生产和反向业务通过既有服务入口接入质量状态。

**技术栈：** Java 21、Spring Boot、Spring Security、JdbcTemplate、Flyway、PostgreSQL、JUnit/Testcontainers、Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、Vitest、Playwright。

## Global Constraints

- 017 阶段只做质量检验与库存质量状态基础，不做完整 QMS、检验标准库、抽样方案、SPC、供应商质量评分。
- 不做批次、序列号、库位、条码、拣配、MRP、排产、产能计划。
- 不做审批流、正式财务、凭证、总账、税务、银行对账、成本重算、质量成本归集。
- 库存质量状态固定为 `PENDING_INSPECTION`、`QUALIFIED`、`REJECTED`、`FROZEN`。
- 历史库存和历史库存流水默认迁移为 `QUALIFIED`。
- 采购入库、生产完工入库、销售退货、生产退料默认形成待检库存。
- 销售出库、生产领料、生产补料只能消耗合格库存。
- 采购退货允许显式选择待检、不合格或合格库存；冻结库存需先解冻。
- `availableQuantity` 只代表合格可用库存，前端不得用总现存数量自行推导可用库存。
- 所有质量确认、冻结、解冻和质量状态相关库存写入必须接入 016 `BusinessPeriodGuard`。
- 页面必须遵守 `docs/ui/page-standards.md`。
- 开发期间只运行受影响验证；阶段交付前统一进入全量验证窗口。

---

## 固定执行规则

- 主代理只负责统筹、派发、等待结果、审查汇总和交付判断，不直接编写业务代码。
- 阶段执行最多拆为 4 个工作包，测试与实现属于同一工作包。
- 每个工作包以 `/goal` 派发给固定角色，后续增量信息复用同一角色会话。
- 阶段中只运行受影响测试、类型检查和必要构建；全量验证集中放在交付前窗口。
- 全部工作包功能完整后，只组织一轮阶段集中审查和一次定向复审。
- 阻断问题必须修复；一般问题默认进入后续清单，除非影响当前验收。
- 不提交 `work/` 清理备份目录，不混入与 017 无关的工作区改动。

## 文件结构

### 文档和验收新增或修改

- 新建：`docs/tasks/017-quality-inventory-status-foundation.md`
- 新建：`docs/api/quality-inventory-status-api.md`
- 新建：`docs/testing/quality-inventory-status-test-plan.md`
- 新建：`docs/testing/quality-inventory-status-visual-audit/notes.md`
- 修改：`docs/product/business-flow.md`
- 修改：`docs/product/product-decisions.md`
- 修改：`docs/testing/acceptance-criteria.md`
- 修改：`docs/README.md`
- 修改：`docs/manual/system-operation-manual.md`
- 修改：`docs/handoffs/2026-07-06-project-handoff-current.md`

### 后端新增或修改

- 新建：`apps/api/src/main/resources/db/migration/V14__quality_inventory_status_schema.sql`
- 新建：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryQualityStatus.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryMovementType.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryPostingService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAdminService.java`
- 新建：`apps/api/src/main/java/com/qherp/api/system/quality/QualityInspectionStatus.java`
- 新建：`apps/api/src/main/java/com/qherp/api/system/quality/QualityInspectionSourceType.java`
- 新建：`apps/api/src/main/java/com/qherp/api/system/quality/QualityAdminController.java`
- 新建：`apps/api/src/main/java/com/qherp/api/system/quality/QualityAdminService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- 修改：`apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/procurement/ProcurementAdminService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/production/ProductionAdminService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/sales/SalesAdminService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/reversal/ReversalAdminService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/reporting/ReportingAdminService.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/quality/QualityAdminControllerTests.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/inventory/InventoryAdminControllerTests.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/procurement/ProcurementAdminControllerTests.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/sales/SalesAdminControllerTests.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/production/ProductionAdminControllerTests.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/reversal/ReversalAdminControllerTests.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`

### 前端新增或修改

- 新建：`apps/web/src/shared/api/qualityInventoryStatusApi.ts`
- 测试：`apps/web/src/shared/api/qualityInventoryStatusApi.spec.ts`
- 修改：`apps/web/src/shared/api/inventoryApi.ts`
- 测试：`apps/web/src/shared/api/inventoryApi.spec.ts`
- 新建：`apps/web/src/modules/quality/QualityStatusTag.vue`
- 新建：`apps/web/src/modules/quality/QualityInspectionListView.vue`
- 新建：`apps/web/src/modules/quality/QualityInspectionProcessDrawer.vue`
- 测试：`apps/web/src/modules/quality/QualityInspectionViews.spec.ts`
- 修改：`apps/web/src/modules/inventory/InventoryBalanceListView.vue`
- 测试：`apps/web/src/modules/inventory/InventoryBalanceListView.spec.ts`
- 修改：`apps/web/src/modules/sales/SalesShipmentFormView.vue`
- 修改：`apps/web/src/modules/production/ProductionMaterialIssueView.vue`
- 修改：`apps/web/src/modules/reversal/PurchaseReturnFormView.vue`
- 修改：`apps/web/src/modules/reversal/ProductionMaterialSupplementFormView.vue`
- 修改：`apps/web/src/router/index.ts`
- 修改：`apps/web/src/router/permissionGuard.spec.ts`
- 修改：`apps/web/src/App.vue`
- 修改：`apps/web/src/App.spec.ts`

## Task 1: 阶段文档、接口契约和测试矩阵基线

**角色：** 产品经理负责任务文档，后端开发负责接口契约，测试负责测试计划，UI 设计师负责视觉验收范围，主代理合并去重。

**Files:**
- Create: `docs/tasks/017-quality-inventory-status-foundation.md`
- Create: `docs/api/quality-inventory-status-api.md`
- Create: `docs/testing/quality-inventory-status-test-plan.md`
- Modify: `docs/product/business-flow.md`
- Modify: `docs/product/product-decisions.md`
- Modify: `docs/testing/acceptance-criteria.md`
- Modify: `docs/README.md`

**Interfaces:**
- Consumes: `docs/superpowers/specs/2026-07-10-quality-inventory-status-design.md`
- Produces: 017 阶段范围、排除项、质量状态枚举、接口路径、错误码、权限点、验收矩阵；Task 2-4 必须以这些文档为准。

- [ ] **Step 1: 编写任务文档**

在 `docs/tasks/017-quality-inventory-status-foundation.md` 写入阶段目标、范围、排除项、业务口径、角色分工、验收标准、风险和变更控制。任务目标必须包含：

```markdown
建立库存质量状态基础能力，将库存余额从仓库+物料升级为仓库+物料+质量状态，并让合格库存成为销售出库、生产领料、生产补料和后续计划净算的唯一可用库存口径。
```

必须包含的验收标准：

```markdown
- 历史库存默认迁移为合格库存。
- 库存余额按仓库、物料和质量状态分层，汇总数量一致。
- `availableQuantity` 只代表合格可用库存。
- 采购入库、完工入库、销售退货和生产退料默认形成待检库存。
- 质量确认可以把待检库存转为合格、不合格或冻结，且转换前后总库存不变。
- 销售出库、生产领料和生产补料不能消耗非合格库存。
- 采购退货按显式质量状态退货，冻结库存需先解冻。
- 质量状态写入受权限、原因、审计和业务期间保护约束。
```

- [ ] **Step 2: 编写接口契约**

在 `docs/api/quality-inventory-status-api.md` 写入接口契约。必须定义质量状态枚举：

```json
{
  "qualityStatus": "PENDING_INSPECTION",
  "qualityStatusName": "待检"
}
```

必须记录核心接口：

```text
GET /api/admin/quality/inspections
GET /api/admin/quality/inspections/{id}
POST /api/admin/quality/inspections/{id}/process
POST /api/admin/inventory/quality-transfers/freeze
POST /api/admin/inventory/quality-transfers/unfreeze
GET /api/admin/inventory/balances?qualityStatus=QUALIFIED
GET /api/admin/inventory/movements?qualityStatus=PENDING_INSPECTION
```

质量确认处理请求体必须使用字符串数量，避免前端浮点误差：

```json
{
  "businessDate": "2026-07-10",
  "qualifiedQuantity": "8.000000",
  "rejectedQuantity": "1.000000",
  "frozenQuantity": "1.000000",
  "reason": "来料检验完成",
  "remark": "外观轻微瑕疵 1 件"
}
```

- [ ] **Step 3: 编写测试计划**

在 `docs/testing/quality-inventory-status-test-plan.md` 写入自动化、浏览器和视觉验收矩阵。后端定向测试命令：

```powershell
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=QualityAdminControllerTests,InventoryAdminControllerTests,ProcurementAdminControllerTests,SalesAdminControllerTests,ProductionAdminControllerTests,ReversalAdminControllerTests,PermissionAuthorizationTests,AccountPermissionInitializerTests test
```

前端定向测试命令：

```powershell
npm --prefix apps/web test -- qualityInventoryStatusApi.spec.ts inventoryApi.spec.ts QualityInspectionViews.spec.ts InventoryBalanceListView.spec.ts permissionGuard.spec.ts App.spec.ts
npm --prefix apps/web run typecheck
```

- [ ] **Step 4: 更新产品流和索引**

更新 `docs/product/business-flow.md`，新增质量状态流程：

```text
采购入库/完工入库/销售退货/生产退料 -> 待检库存 -> 质量确认 -> 合格/不合格/冻结库存 -> 合格库存参与销售出库和生产领料
```

更新 `docs/product/product-decisions.md`、`docs/testing/acceptance-criteria.md`、`docs/README.md`，记录 017 阶段为质量检验与库存质量状态基础。文案必须把完整 QMS、SPC、批次、库位、MRP 和正式财务写入排除项。

- [ ] **Step 5: 执行文档自检**

运行：

```powershell
$terms = @('TO' + 'DO', 'TB' + 'D', '待' + '定', '未' + '定', '占' + '位', 'implement ' + 'later', 'fill in ' + 'details') -join '|'
rg -n $terms docs/tasks/017-quality-inventory-status-foundation.md docs/api/quality-inventory-status-api.md docs/testing/quality-inventory-status-test-plan.md docs/product/business-flow.md docs/product/product-decisions.md docs/testing/acceptance-criteria.md docs/README.md
git diff --check -- docs/tasks/017-quality-inventory-status-foundation.md docs/api/quality-inventory-status-api.md docs/testing/quality-inventory-status-test-plan.md docs/product/business-flow.md docs/product/product-decisions.md docs/testing/acceptance-criteria.md docs/README.md
```

Expected: `rg` 无输出；`git diff --check` 退出码为 `0`。

- [ ] **Step 6: 提交文档基线**

```powershell
git add docs/tasks/017-quality-inventory-status-foundation.md docs/api/quality-inventory-status-api.md docs/testing/quality-inventory-status-test-plan.md docs/product/business-flow.md docs/product/product-decisions.md docs/testing/acceptance-criteria.md docs/README.md
git commit -m "建立质量库存状态阶段文档基线"
```

## Task 2: 后端库存质量状态底座

**角色：** 后端开发负责实现，测试负责定向验证，产品经理负责规格审查。

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V14__quality_inventory_status_schema.sql`
- Create: `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryQualityStatus.java`
- Modify: `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryMovementType.java`
- Modify: `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryPostingService.java`
- Modify: `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAdminService.java`
- Modify: `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- Test: `apps/api/src/test/java/com/qherp/api/system/inventory/InventoryAdminControllerTests.java`

**Interfaces:**
- Produces: `InventoryQualityStatus` enum with `PENDING_INSPECTION`, `QUALIFIED`, `REJECTED`, `FROZEN`
- Produces: `InventoryPostingService.PostingRequest(..., InventoryQualityStatus qualityStatus, ...)`
- Produces: `InventoryPostingService.transferQualityStatus(...)`
- Consumes: existing inventory posting calls from procurement, sales, production and reversal services.

- [ ] **Step 1: 编写库存质量状态后端失败测试**

在 `InventoryAdminControllerTests` 增加测试：

```java
@Test
void inventoryBalanceSeparatesQualityStatusAndAvailableQuantityUsesQualifiedOnly() {
	Long warehouseId = seedWarehouse();
	Long materialId = seedMaterial();
	Long unitId = seedUnit();

	postInventory(warehouseId, materialId, unitId, new BigDecimal("10.000000"), InventoryQualityStatus.QUALIFIED);
	postInventory(warehouseId, materialId, unitId, new BigDecimal("3.000000"), InventoryQualityStatus.PENDING_INSPECTION);
	postInventory(warehouseId, materialId, unitId, new BigDecimal("2.000000"), InventoryQualityStatus.REJECTED);

	getJson("/api/admin/inventory/balances?warehouseId=" + warehouseId + "&materialId=" + materialId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[0].quantityOnHand").value(15.000000))
			.andExpect(jsonPath("$.data.items[0].availableQuantity").value(10.000000))
			.andExpect(jsonPath("$.data.items[0].pendingInspectionQuantity").value(3.000000))
			.andExpect(jsonPath("$.data.items[0].rejectedQuantity").value(2.000000));
}
```

在同一测试类增加普通出库禁止非合格库存测试：

```java
@Test
void postingServiceRejectsNonQualifiedOrdinaryOutbound() {
	InventoryPostingService.PostingRequest request = new InventoryPostingService.PostingRequest(
			InventoryMovementType.SALES_SHIPMENT,
			InventoryDirection.OUT,
			warehouseId,
			materialId,
			unitId,
			new BigDecimal("1.000000"),
			InventoryQualityStatus.PENDING_INSPECTION,
			"SALES_SHIPMENT",
			1L,
			1L,
			LocalDate.of(2026, 7, 10),
			"销售出库",
			null,
			"admin");

	assertThatThrownBy(() -> inventoryPostingService.post(request))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ApiErrorCode.INVENTORY_NON_QUALIFIED_NOT_AVAILABLE);
}
```

- [ ] **Step 2: 运行测试确认失败**

运行：

```powershell
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=InventoryAdminControllerTests test
```

Expected: 编译失败或测试失败，原因是 `InventoryQualityStatus`、质量状态字段、响应字段和质量状态校验尚不存在。

- [ ] **Step 3: 实现 V14 迁移**

创建 `V14__quality_inventory_status_schema.sql`。迁移必须先为旧数据补合格状态，再调整约束：

```sql
alter table inv_stock_balance add column quality_status varchar(32);
update inv_stock_balance set quality_status = 'QUALIFIED' where quality_status is null;
alter table inv_stock_balance alter column quality_status set not null;
alter table inv_stock_balance drop constraint uk_inv_stock_balance_warehouse_material;
alter table inv_stock_balance add constraint uk_inv_stock_balance_warehouse_material_quality
	unique (warehouse_id, material_id, quality_status);
alter table inv_stock_balance add constraint ck_inv_stock_balance_quality_status
	check (quality_status in ('PENDING_INSPECTION', 'QUALIFIED', 'REJECTED', 'FROZEN'));

alter table inv_stock_movement add column quality_status varchar(32);
update inv_stock_movement set quality_status = 'QUALIFIED' where quality_status is null;
alter table inv_stock_movement alter column quality_status set not null;
alter table inv_stock_movement add constraint ck_inv_stock_movement_quality_status
	check (quality_status in ('PENDING_INSPECTION', 'QUALIFIED', 'REJECTED', 'FROZEN'));

create index idx_inv_stock_balance_quality_status on inv_stock_balance (quality_status);
create index idx_inv_stock_movement_quality_status on inv_stock_movement (quality_status);
```

调整期初唯一索引，确保同一仓库物料只允许一次合格期初或按质量状态明确控制。017 采用历史兼容口径：

```sql
drop index if exists uk_inv_stock_movement_opening_once;
create unique index uk_inv_stock_movement_opening_once
	on inv_stock_movement (warehouse_id, material_id, quality_status)
	where movement_type = 'OPENING';
```

- [ ] **Step 4: 添加质量状态枚举和错误码**

新建 `InventoryQualityStatus.java`：

```java
package com.qherp.api.system.inventory;

public enum InventoryQualityStatus {
	PENDING_INSPECTION,
	QUALIFIED,
	REJECTED,
	FROZEN
}
```

在 `ApiErrorCode` 添加：

```java
INVENTORY_QUALITY_STATUS_REQUIRED(HttpStatus.BAD_REQUEST, "库存质量状态必填"),
INVENTORY_NON_QUALIFIED_NOT_AVAILABLE(HttpStatus.CONFLICT, "非合格库存不可用于当前业务"),
INVENTORY_QUALITY_STATUS_BALANCE_NOT_ENOUGH(HttpStatus.CONFLICT, "指定质量状态库存不足"),
```

- [ ] **Step 5: 改造 InventoryPostingService**

将 `PostingRequest` 扩展为包含质量状态：

```java
public record PostingRequest(InventoryMovementType movementType, InventoryDirection direction, Long warehouseId,
		Long materialId, Long unitId, BigDecimal quantity, InventoryQualityStatus qualityStatus, String sourceType,
		Long sourceId, Long sourceLineId, LocalDate businessDate, String reason, String remark, String operatorName) {
}
```

普通出库前校验：

```java
private void validateOutboundQualityStatus(PostingRequest request) {
	if (request.direction() == InventoryDirection.OUT
			&& ordinaryOutboundSource(request.sourceType())
			&& request.qualityStatus() != InventoryQualityStatus.QUALIFIED) {
		throw new BusinessException(ApiErrorCode.INVENTORY_NON_QUALIFIED_NOT_AVAILABLE);
	}
}
```

锁定余额时使用三维条件：

```sql
select id, quantity_on_hand
from inv_stock_balance
where warehouse_id = ?
and material_id = ?
and quality_status = ?
for update
```

插入流水时写入 `quality_status`。

新增 `transferQualityStatus` 方法，先扣减来源状态，再增加目标状态。转换流水的 `source_line_id` 必须能唯一防重，可使用来源行和目标状态组合的派生键，或在质量确认表中保证重复提交被拒绝后再写流水。

- [ ] **Step 6: 改造库存余额和流水接口**

`InventoryAdminService.balances` 支持 `qualityStatus` 查询。汇总响应必须包含：

```java
BigDecimal quantityOnHand,
BigDecimal availableQuantity,
BigDecimal pendingInspectionQuantity,
BigDecimal qualifiedQuantity,
BigDecimal rejectedQuantity,
BigDecimal frozenQuantity
```

`availableQuantity` 必须等于 `qualifiedQuantity` 减已锁定合格数量；017 没有占用预留时可直接等于合格数量。

`InventoryAdminService.movements` 响应增加 `qualityStatus` 和 `qualityStatusName`。

- [ ] **Step 7: 运行库存底座定向测试**

运行：

```powershell
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=InventoryAdminControllerTests test
```

Expected: 库存余额分层、历史合格迁移、质量状态流水、非合格普通出库拒绝测试通过。

- [ ] **Step 8: 提交库存质量状态底座**

```powershell
git add apps/api/src/main/resources/db/migration/V14__quality_inventory_status_schema.sql apps/api/src/main/java/com/qherp/api/system/inventory/InventoryQualityStatus.java apps/api/src/main/java/com/qherp/api/system/inventory/InventoryMovementType.java apps/api/src/main/java/com/qherp/api/system/inventory/InventoryPostingService.java apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAdminService.java apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java apps/api/src/test/java/com/qherp/api/system/inventory/InventoryAdminControllerTests.java
git commit -m "实现库存质量状态底座"
```

## Task 3: 质量确认、权限、期间保护和业务入口接入

**角色：** 后端开发负责实现，测试负责回归路径，产品经理负责业务规则审查。

**Files:**
- Create: `apps/api/src/main/java/com/qherp/api/system/quality/QualityInspectionStatus.java`
- Create: `apps/api/src/main/java/com/qherp/api/system/quality/QualityInspectionSourceType.java`
- Create: `apps/api/src/main/java/com/qherp/api/system/quality/QualityAdminService.java`
- Create: `apps/api/src/main/java/com/qherp/api/system/quality/QualityAdminController.java`
- Modify: `apps/api/src/main/resources/db/migration/V14__quality_inventory_status_schema.sql`
- Modify: `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- Modify: `apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`
- Modify: `apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- Modify: `apps/api/src/main/java/com/qherp/api/system/procurement/ProcurementAdminService.java`
- Modify: `apps/api/src/main/java/com/qherp/api/system/production/ProductionAdminService.java`
- Modify: `apps/api/src/main/java/com/qherp/api/system/sales/SalesAdminService.java`
- Modify: `apps/api/src/main/java/com/qherp/api/system/reversal/ReversalAdminService.java`
- Test: `apps/api/src/test/java/com/qherp/api/system/quality/QualityAdminControllerTests.java`
- Test: existing procurement, production, sales, reversal, permission and initializer tests.

**Interfaces:**
- Produces: `/api/admin/quality/inspections`
- Produces: `/api/admin/quality/inspections/{id}/process`
- Produces: `/api/admin/inventory/quality-transfers/freeze`
- Produces: `/api/admin/inventory/quality-transfers/unfreeze`
- Consumes: `InventoryPostingService.post` and `transferQualityStatus`
- Consumes: `BusinessPeriodGuard.assertWritable`

- [ ] **Step 1: 编写质量确认失败测试**

在 `QualityAdminControllerTests` 增加测试：

```java
@Test
void processInspectionTransfersPendingToQualifiedAndRejected() {
	Long inspectionId = seedPendingInspection("PURCHASE_RECEIPT_LINE", sourceId, sourceLineId,
			warehouseId, materialId, unitId, new BigDecimal("10.000000"));

	postJson("/api/admin/quality/inspections/" + inspectionId + "/process", """
			{
			  "businessDate": "2026-07-10",
			  "qualifiedQuantity": "8.000000",
			  "rejectedQuantity": "2.000000",
			  "frozenQuantity": "0.000000",
			  "reason": "来料检验完成"
			}
			""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("COMPLETED"))
			.andExpect(jsonPath("$.data.qualifiedQuantity").value("8.000000"));

	assertQualityBalance(warehouseId, materialId, InventoryQualityStatus.PENDING_INSPECTION, "0.000000");
	assertQualityBalance(warehouseId, materialId, InventoryQualityStatus.QUALIFIED, "8.000000");
	assertQualityBalance(warehouseId, materialId, InventoryQualityStatus.REJECTED, "2.000000");
}
```

增加期间保护测试：

```java
@Test
void lockedPeriodRejectsInspectionProcessing() {
	createAndLockPeriod("2026-07", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
	Long inspectionId = seedPendingInspection(...);

	postJson("/api/admin/quality/inspections/" + inspectionId + "/process", payloadWithDate("2026-07-10"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BUSINESS_PERIOD_LOCKED"));
}
```

- [ ] **Step 2: 运行测试确认失败**

运行：

```powershell
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=QualityAdminControllerTests test
```

Expected: 编译失败或测试失败，原因是质量领域、接口、权限和质量确认表尚不存在。

- [ ] **Step 3: 完成质量确认表迁移和枚举**

在 V14 迁移追加：

```sql
create table qua_quality_inspection (
	id bigserial primary key,
	inspection_no varchar(64) not null,
	source_type varchar(64) not null,
	source_id bigint not null,
	source_line_id bigint not null,
	warehouse_id bigint not null references mst_warehouse(id),
	material_id bigint not null references mst_material(id),
	unit_id bigint not null references mst_unit(id),
	business_date date not null,
	inspection_quantity numeric(18, 6) not null,
	qualified_quantity numeric(18, 6) not null default 0,
	rejected_quantity numeric(18, 6) not null default 0,
	frozen_quantity numeric(18, 6) not null default 0,
	status varchar(32) not null,
	reason varchar(200),
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	completed_by varchar(64),
	completed_at timestamptz,
	version bigint not null default 0,
	constraint uk_qua_quality_inspection_no unique (inspection_no),
	constraint uk_qua_quality_inspection_source unique (source_type, source_line_id),
	constraint ck_qua_quality_inspection_status check (status in ('PENDING', 'COMPLETED')),
	constraint ck_qua_quality_inspection_quantity_positive check (inspection_quantity > 0),
	constraint ck_qua_quality_inspection_result_non_negative check (
		qualified_quantity >= 0 and rejected_quantity >= 0 and frozen_quantity >= 0
	)
);

create index idx_qua_quality_inspection_status_date on qua_quality_inspection (status, business_date desc, id desc);
create index idx_qua_quality_inspection_source on qua_quality_inspection (source_type, source_id, source_line_id);
```

新建 `QualityInspectionStatus`：

```java
public enum QualityInspectionStatus {
	PENDING,
	COMPLETED
}
```

- [ ] **Step 4: 添加质量权限、鉴权映射和错误码**

权限种子：

```java
new PermissionSeed("quality", "质量管理", SystemPermissionType.MENU, null, "/quality/inspections", null, null, 700),
new PermissionSeed("quality:inspection:view", "查看质量确认", SystemPermissionType.ACTION, "quality",
		"/quality/inspections", "GET", "/api/admin/quality/inspections/**", 701),
new PermissionSeed("quality:inspection:process", "处理质量确认", SystemPermissionType.ACTION, "quality",
		"/quality/inspections", "POST", "/api/admin/quality/inspections/*/process", 702),
new PermissionSeed("quality:status:freeze", "冻结库存质量状态", SystemPermissionType.ACTION, "quality",
		"/quality/inspections", "POST", "/api/admin/inventory/quality-transfers/freeze", 703),
new PermissionSeed("quality:status:unfreeze", "解冻库存质量状态", SystemPermissionType.ACTION, "quality",
		"/quality/inspections", "POST", "/api/admin/inventory/quality-transfers/unfreeze", 704),
```

错误码：

```java
QUALITY_INSPECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "质量确认记录不存在"),
QUALITY_INSPECTION_SOURCE_INVALID(HttpStatus.CONFLICT, "质量确认来源不合法"),
QUALITY_INSPECTION_QUANTITY_MISMATCH(HttpStatus.BAD_REQUEST, "质量确认数量不匹配"),
QUALITY_INSPECTION_STATUS_INVALID(HttpStatus.CONFLICT, "质量确认状态不允许当前操作"),
QUALITY_STATUS_TRANSITION_INVALID(HttpStatus.CONFLICT, "质量状态转换不允许"),
```

- [ ] **Step 5: 实现 QualityAdminService**

`processInspection` 必须执行：

```java
this.businessPeriodGuard.assertWritable(request.businessDate(), BusinessPeriodOperation.UPDATE,
		"QUALITY_INSPECTION", inspection.id());
validateProcessQuantities(inspection.inspectionQuantity(), request);
this.inventoryPostingService.transferQualityStatus(... PENDING_INSPECTION -> QUALIFIED ...);
this.inventoryPostingService.transferQualityStatus(... PENDING_INSPECTION -> REJECTED ...);
this.inventoryPostingService.transferQualityStatus(... PENDING_INSPECTION -> FROZEN ...);
updateInspectionCompleted(...);
this.auditService.record(operator, "QUALITY_INSPECTION_PROCESS", "QUALITY_INSPECTION", inspection.id(),
		inspection.inspectionNo(), servletRequest);
```

数量合计校验：

```java
BigDecimal total = request.qualifiedQuantity()
		.add(request.rejectedQuantity())
		.add(request.frozenQuantity());
if (total.compareTo(inspection.inspectionQuantity()) != 0) {
	throw new BusinessException(ApiErrorCode.QUALITY_INSPECTION_QUANTITY_MISMATCH);
}
```

- [ ] **Step 6: 接入入库来源和消耗来源**

修改业务服务：

```java
// 采购入库、生产完工入库、销售退货、生产退料
new InventoryPostingService.PostingRequest(..., InventoryQualityStatus.PENDING_INSPECTION, ...);
createPendingInspectionForPostedInbound(...);

// 销售出库、生产领料、生产补料
new InventoryPostingService.PostingRequest(..., InventoryQualityStatus.QUALIFIED, ...);
```

采购退货请求和候选来源必须显式携带质量状态。采购退货过账传入请求质量状态；如果是 `FROZEN`，返回 `QUALITY_STATUS_TRANSITION_INVALID`。

- [ ] **Step 7: 运行后端业务入口定向测试**

运行：

```powershell
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=QualityAdminControllerTests,ProcurementAdminControllerTests,SalesAdminControllerTests,ProductionAdminControllerTests,ReversalAdminControllerTests,PermissionAuthorizationTests,AccountPermissionInitializerTests test
```

Expected: 质量确认、入库待检、合格消耗、采购退货质量状态、权限和期间保护测试通过。

- [ ] **Step 8: 提交质量确认与业务入口接入**

```powershell
git add apps/api/src/main/resources/db/migration/V14__quality_inventory_status_schema.sql apps/api/src/main/java/com/qherp/api/system/quality apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java apps/api/src/main/java/com/qherp/api/system/procurement/ProcurementAdminService.java apps/api/src/main/java/com/qherp/api/system/production/ProductionAdminService.java apps/api/src/main/java/com/qherp/api/system/sales/SalesAdminService.java apps/api/src/main/java/com/qherp/api/system/reversal/ReversalAdminService.java apps/api/src/test/java/com/qherp/api/system/quality/QualityAdminControllerTests.java apps/api/src/test/java/com/qherp/api/system/procurement/ProcurementAdminControllerTests.java apps/api/src/test/java/com/qherp/api/system/sales/SalesAdminControllerTests.java apps/api/src/test/java/com/qherp/api/system/production/ProductionAdminControllerTests.java apps/api/src/test/java/com/qherp/api/system/reversal/ReversalAdminControllerTests.java apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java
git commit -m "接入质量确认与库存质量控制"
```

## Task 4: 前端页面、浏览器验收和阶段交付

**角色：** 前端开发负责前端实现，UI 设计师负责视觉复审，测试负责验收执行，主代理负责最终交付判断。

**Files:**
- Create: `apps/web/src/shared/api/qualityInventoryStatusApi.ts`
- Test: `apps/web/src/shared/api/qualityInventoryStatusApi.spec.ts`
- Modify: `apps/web/src/shared/api/inventoryApi.ts`
- Test: `apps/web/src/shared/api/inventoryApi.spec.ts`
- Create: `apps/web/src/modules/quality/QualityStatusTag.vue`
- Create: `apps/web/src/modules/quality/QualityInspectionListView.vue`
- Create: `apps/web/src/modules/quality/QualityInspectionProcessDrawer.vue`
- Test: `apps/web/src/modules/quality/QualityInspectionViews.spec.ts`
- Modify: `apps/web/src/modules/inventory/InventoryBalanceListView.vue`
- Test: `apps/web/src/modules/inventory/InventoryBalanceListView.spec.ts`
- Modify: `apps/web/src/router/index.ts`
- Modify: `apps/web/src/router/permissionGuard.spec.ts`
- Modify: `apps/web/src/App.vue`
- Modify: `apps/web/src/App.spec.ts`
- Modify: candidate stock forms in sales, production and reversal modules.
- Modify: `docs/manual/system-operation-manual.md`
- Create: `docs/testing/quality-inventory-status-visual-audit/notes.md`

**Interfaces:**
- Consumes: quality inspection endpoints from Task 3.
- Consumes: inventory balance response fields from Task 2.
- Produces: route `quality-inspections`, path `/quality/inspections`, permission `quality:inspection:view`.
- Produces: browser and visual evidence for final stage gate.

- [ ] **Step 1: 编写前端 API 失败测试**

在 `qualityInventoryStatusApi.spec.ts` 覆盖列表、详情、处理、冻结、解冻和错误信封。样例：

```ts
it('processes a quality inspection with split quantities', async () => {
  const calls: Array<{ input: string; init: RequestInit }> = []
  const api = createQualityInventoryStatusApi({
    fetcher: async (input, init) => {
      calls.push({ input, init })
      if (String(input).endsWith('/api/auth/csrf')) {
        return jsonResponse({ success: true, code: 'OK', message: '成功', data: { token: 'csrf', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' } })
      }
      return jsonResponse({ success: true, code: 'OK', message: '成功', data: inspectionRecord({ status: 'COMPLETED' }) })
    },
  })

  await api.inspections.process(9, {
    businessDate: '2026-07-10',
    qualifiedQuantity: '8.000000',
    rejectedQuantity: '1.000000',
    frozenQuantity: '1.000000',
    reason: '检验完成',
  })

  expect(calls[1].input).toBe('/api/admin/quality/inspections/9/process')
  expect(calls[1].init.method).toBe('POST')
})
```

- [ ] **Step 2: 实现前端 API 和状态标签**

`qualityInventoryStatusApi.ts` 导出：

```ts
export type InventoryQualityStatus = 'PENDING_INSPECTION' | 'QUALIFIED' | 'REJECTED' | 'FROZEN'
export type QualityInspectionStatus = 'PENDING' | 'COMPLETED'

export interface QualityInspectionRecord {
  id: ResourceId
  inspectionNo: string
  sourceType: string
  sourceNo: string
  warehouseId: ResourceId
  warehouseName: string
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitName: string
  businessDate: string
  inspectionQuantity: string
  qualifiedQuantity: string
  rejectedQuantity: string
  frozenQuantity: string
  status: QualityInspectionStatus
  statusName: string
  reason?: string | null
  remark?: string | null
}
```

`QualityStatusTag.vue` 显示：

```text
PENDING_INSPECTION -> 待检
QUALIFIED -> 合格
REJECTED -> 不合格
FROZEN -> 冻结
```

- [ ] **Step 3: 编写质量确认页面失败测试**

在 `QualityInspectionViews.spec.ts` 覆盖列表筛选、状态标签、处理抽屉、数量不平错误、权限按钮和后端错误提示。必须断言：

```ts
expect(wrapper.text()).toContain('质量确认')
expect(wrapper.text()).toContain('待检')
expect(wrapper.text()).toContain('合格数量')
expect(wrapper.text()).toContain('不合格数量')
expect(wrapper.text()).toContain('冻结数量')
```

- [ ] **Step 4: 实现质量确认页面、路由和菜单**

新增路由：

```ts
{
  path: '/quality/inspections',
  name: 'quality-inspections',
  meta: { requiresAuth: true, requiredPermission: 'quality:inspection:view' },
  component: () => import('../modules/quality/QualityInspectionListView.vue'),
}
```

在 `App.vue` 增加“质量管理”一级菜单和“质量确认”入口。处理按钮需要 `quality:inspection:process` 权限。

处理抽屉数量校验：

```ts
const total = toDecimal(form.qualifiedQuantity)
  .plus(toDecimal(form.rejectedQuantity))
  .plus(toDecimal(form.frozenQuantity))
if (!total.eq(toDecimal(currentInspection.inspectionQuantity))) {
  formError.value = '合格、不合格和冻结数量合计必须等于待检数量'
  return
}
```

- [ ] **Step 5: 改造库存余额页和候选禁用展示**

`inventoryApi.ts` 为 `InventoryBalanceRecord` 增加：

```ts
pendingInspectionQuantity: number
qualifiedQuantity: number
rejectedQuantity: number
frozenQuantity: number
```

`InventoryBalanceListView.vue` 新增质量状态分布列。页面必须同时展示总现存和合格可用，文案为：

```text
可用数量仅包含合格库存，待检、不合格和冻结库存不参与出库或领料。
```

销售出库、生产领料、采购退货、生产补料候选区域展示禁用原因。最少支持这些文案：

```text
待检库存不可领料
不合格库存不可销售出库
冻结库存不可参与可用量
冻结库存需先解冻后退货
```

- [ ] **Step 6: 运行前端定向验证**

运行：

```powershell
npm --prefix apps/web test -- qualityInventoryStatusApi.spec.ts inventoryApi.spec.ts QualityInspectionViews.spec.ts InventoryBalanceListView.spec.ts permissionGuard.spec.ts App.spec.ts
npm --prefix apps/web run typecheck
```

Expected: 质量 API、质量确认页面、库存余额质量状态、路由、菜单和类型检查通过。

- [ ] **Step 7: 更新操作手册和视觉审计记录**

在 `docs/manual/system-operation-manual.md` 增加“质量管理”章节，说明质量确认、库存质量状态、可用量口径、非合格不可用、冻结解冻和期间保护。

创建 `docs/testing/quality-inventory-status-visual-audit/notes.md`，记录截图清单：

```text
01-quality-inspection-list-desktop.png
02-quality-inspection-process-drawer-desktop.png
03-quality-inspection-quantity-error-desktop.png
04-inventory-quality-balance-desktop.png
05-sales-shipment-non-qualified-disabled-desktop.png
06-production-issue-non-qualified-disabled-desktop.png
07-quality-readonly-permission-desktop.png
08-quality-forbidden-desktop.png
09-quality-period-locked-error-desktop.png
10-quality-inspection-mobile-390x844.png
```

- [ ] **Step 8: 执行阶段集中审查**

固定五角色只进行一轮集中审查：

- 产品经理核对 017 任务文档、验收矩阵和范围排除项。
- UI 设计师检查质量确认页、库存余额页、候选禁用原因、异常态和窄屏截图。
- 前端开发审查 API、路由、权限、页面状态和错误处理。
- 后端开发审查 V14 迁移、库存服务、质量确认事务、业务入口接入和期间保护。
- 测试检查自动化覆盖、浏览器验收、视觉证据和阻断风险。

主代理合并去重审查意见，一次性派发整改；整改后只复审差异和受影响路径。

- [ ] **Step 9: 执行阶段全量验证窗口**

运行：

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

浏览器验收必须覆盖管理员主路径、只读权限、无权限、采购入库待检到合格、完工入库待检到不合格、销售退货待检、生产退料待检、非合格不可出库或领料、冻结解冻、期间保护和 390px 窄屏。

- [ ] **Step 10: 提交前端与验收材料**

```powershell
git add apps/web/src/shared/api/qualityInventoryStatusApi.ts apps/web/src/shared/api/qualityInventoryStatusApi.spec.ts apps/web/src/shared/api/inventoryApi.ts apps/web/src/shared/api/inventoryApi.spec.ts apps/web/src/modules/quality apps/web/src/modules/inventory/InventoryBalanceListView.vue apps/web/src/modules/inventory/InventoryBalanceListView.spec.ts apps/web/src/modules/sales/SalesShipmentFormView.vue apps/web/src/modules/production/ProductionMaterialIssueView.vue apps/web/src/modules/reversal/PurchaseReturnFormView.vue apps/web/src/modules/reversal/ProductionMaterialSupplementFormView.vue apps/web/src/router/index.ts apps/web/src/router/permissionGuard.spec.ts apps/web/src/App.vue apps/web/src/App.spec.ts docs/manual/system-operation-manual.md docs/testing/quality-inventory-status-visual-audit
git commit -m "完成质量库存状态前端与验收材料"
```

## 计划自检清单

- [x] 规格中的历史库存合格迁移、质量状态余额、质量状态流水、入库待检、轻量质量确认、冻结解冻、合格消耗、采购退货质量状态、权限、期间保护、前端页面和视觉验收均有对应任务。
- [x] 工作包总数为 4 个，符合阶段整包协作规则。
- [x] 每个工作包均包含测试、实现、验证和提交边界。
- [x] 本计划未引入完整 QMS、SPC、批次、库位、MRP、审批流、正式财务或质量成本。
- [x] 本计划明确执行时复用固定五角色，不创建第六类角色。
- [x] 本计划没有要求提交当前工作区已有的 `AGENTS.md` 和 `work/` 无关改动。
