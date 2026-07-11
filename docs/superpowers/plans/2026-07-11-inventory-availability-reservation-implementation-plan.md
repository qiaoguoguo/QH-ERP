# 库存可用量、占用、冻结与预留基础实施计划

> 给执行智能体：本计划按任务逐项执行。任务状态使用复选框维护；实现必须由固定角色子代理承担，主代理只做统筹、审查汇总和交付判断。

**目标：** 建立 018 库存可用量、预留、占用、冻结联动、物料级采购在途参考、仓库级现货可承诺量和计划净需求缺口基础。

**架构：** 新增库存预留占用台账和统一库存可用量服务。`inv_stock_balance.locked_quantity` 保留为该仓库、物料、质量状态下活跃预留和占用的汇总缓存，由统一服务维护。库存余额和业务候选接口只返回后端计算结果，前端不得自行推导最终可用量、现货可承诺量或本次最多数量。

**技术栈：** Java 21、Spring Boot、Spring JDBC/JPA、PostgreSQL/Flyway、Vue 3、TypeScript、Element Plus、Vitest。

## 全局约束

- 所有沟通、文档、界面文案、提交信息、议题和拉取请求说明使用中文；技术标识、命令和路径可保留原文。
- 主代理只负责统筹；实现必须派发给固定角色子代理。
- 不进入完整 MRP、复杂 ATP、批次、库位、序列号、审批流、正式财务、库存月结或 BI 范围。
- 后端是 `availableQuantity`、`availableToPromiseQuantity`、`netRequirementShortageQuantity`、`maxSelectableQuantity`、禁用原因和库存数量公式的最终来源。
- 018 起 `availableQuantity` 表示合格现货净可用；采购在途使用 `inTransitQuantity` 单列作为物料级参考。
- 采购在途不按仓库计算，不进入仓库级账面库存，也不进入仓库级现货可承诺量。
- 销售订单行新增预留仓库；销售确认门禁是该预留仓库的现货净可用是否足够。现货不足但物料级在途足够时，订单保持草稿并提示现货不足、在途仅供参考。
- 生产工单发布生成 `PRODUCTION_WORK_ORDER + RESERVATION`，不得直接记为 `OCCUPATION`。
- 开发期只运行受影响测试；后端全量、前端全量、类型检查、构建、迁移、浏览器和视觉验证统一放入交付前验证窗口。

---

## 文件结构

- 新建 `apps/api/src/main/resources/db/migration/V15__inventory_availability_reservation_schema.sql`：预留占用台账、索引和约束。
- 新建 `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryReservationType.java`。
- 新建 `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryReservationStatus.java`。
- 新建 `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAvailabilityService.java`。
- 修改 `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAdminService.java`：库存余额、预留占用查询和净需求缺口响应。
- 修改 `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAdminController.java`：预留占用查询接口。
- 修改 `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`：新增受控错误码。
- 修改 `apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java` 和 `apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`：新增权限。
- 修改销售、生产、采购、质量、反向业务和报表服务，使其调用统一库存可用量服务或消费其口径。
- 修改 `apps/web/src/shared/api/inventoryApi.ts`、`salesApi.ts`、`productionApi.ts`、`procurementApi.ts`：新增字段。
- 修改 `apps/web/src/modules/inventory/InventoryBalanceListView.vue`：可用量口径列和追溯入口。
- 修改销售、生产和采购页面以展示后端字段。
- 浏览器验证时创建 `docs/testing/inventory-availability-reservation-visual-audit/notes.md`。

## 任务 1：后端预留占用台账和库存可用量服务

**负责人：** 后端开发

**文件：**
- 新建：`apps/api/src/main/resources/db/migration/V15__inventory_availability_reservation_schema.sql`
- 新建：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryReservationType.java`
- 新建：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryReservationStatus.java`
- 新建：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAvailabilityService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/inventory/InventoryAdminControllerTests.java`

**接口：**
- 产出：
  - `InventoryAvailabilityService.reserve(...)`
  - `InventoryAvailabilityService.releaseBySource(...)`
  - `InventoryAvailabilityService.consumeBySource(...)`
  - `InventoryAvailabilityService.availabilityFor(...)`
  - `InventoryAvailabilityService.purchaseInTransitFor(...)`

- [ ] 增加预留创建、来源唯一、释放、消费和并发可用量保护的失败优先测试。
- [ ] 增加 `V15__inventory_availability_reservation_schema.sql`，包含 `inv_stock_reservation` 表、状态和类型约束、活跃来源唯一索引、仓库物料状态索引。
- [ ] 实现 `InventoryReservationType` 和 `InventoryReservationStatus`。
- [ ] 增加错误码：`INVENTORY_AVAILABLE_NOT_ENOUGH`、`INVENTORY_ATP_NOT_ENOUGH`、`INVENTORY_RESERVATION_NOT_FOUND`、`INVENTORY_RESERVATION_SOURCE_DUPLICATED`、`INVENTORY_RESERVATION_STATUS_INVALID`、`INVENTORY_RESERVATION_CONCURRENT_MODIFICATION`、`INVENTORY_RESERVED_OR_OCCUPIED_NOT_AVAILABLE`、`INVENTORY_IN_TRANSIT_NOT_AVAILABLE`。
- [ ] 实现 `InventoryAvailabilityService`，包含库存余额行锁、活跃台账写入、`locked_quantity` 缓存更新、审计记录和业务期间保护挂钩。
- [ ] 执行：

```powershell
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=InventoryAdminControllerTests test
```

预期：受影响库存测试通过。

## 任务 2：库存余额、预留占用查询和权限

**负责人：** 后端开发

**文件：**
- 修改：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAdminService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAdminController.java`
- 修改：`apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- 测试：`InventoryAdminControllerTests`、`PermissionAuthorizationTests`、`AccountPermissionInitializerTests`

**接口：**
- 消费：`InventoryAvailabilityService.availabilityFor(...)`
- 产出：
  - 库存余额字段：`bookQuantity`、`reservedQuantity`、`occupiedQuantity`、`inTransitQuantity`、`availableToPromiseQuantity`、`netRequirementShortageQuantity`
  - `GET /api/admin/inventory/reservations`
  - `GET /api/admin/inventory/reservations/{id}`

- [ ] 扩展库存余额测试，覆盖 018 字段和公式。
- [ ] 扩展库存余额响应和映射，使用库存可用量服务输出。
- [ ] 新增预留占用列表、详情、来源上下文和审计记录。
- [ ] 新增权限 `inventory:availability:view` 和 `inventory:reservation:view`。
- [ ] 执行受影响后端测试：

```powershell
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=InventoryAdminControllerTests,PermissionAuthorizationTests,AccountPermissionInitializerTests test
```

## 任务 3：销售预留、出库占用和同仓库消费

**负责人：** 后端开发

**文件：**
- 修改：`apps/api/src/main/java/com/qherp/api/system/sales/SalesAdminService.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/sales/SalesAdminControllerTests.java`

**接口：**
- 消费：`InventoryAvailabilityService.reserve/releaseBySource/consumeBySource/availabilityFor`。
- 产出：销售订单行预留仓库字段，销售订单和出库响应中的预留、占用、现货净可用、仓库级现货可承诺量和禁用原因字段。

- [ ] 增加销售订单行预留仓库必填、确认生成预留、取消/关闭释放、出库过账消费预留、出库仓库不一致拒绝、现货不足但物料级在途足够仍拒绝确认的测试。
- [ ] 销售订单行增加预留仓库；确认时按预留仓库校验现货净可用并生成 `SALES_ORDER + RESERVATION`。
- [ ] 销售出库创建、更新和过账时校验出库仓库与来源订单行预留仓库一致。
- [ ] 销售出库草稿如建立执行锁定则写 `SALES_SHIPMENT + OCCUPATION`；过账消费占用或预留并扣减合格库存。
- [ ] 更新销售候选映射，使用后端可用量输出和当前来源自有预留回算。
- [ ] 执行：

```powershell
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=SalesAdminControllerTests test
```

## 任务 4：生产预留和领料消费

**负责人：** 后端开发

**文件：**
- 修改：`apps/api/src/main/java/com/qherp/api/system/production/ProductionAdminService.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/production/ProductionAdminControllerTests.java`

**接口：**
- 消费：库存可用量服务。
- 产出：工单用料和领料响应中的预留、占用、现货净可用和禁用原因字段。

- [ ] 增加工单发布生成 `RESERVATION`、取消/完成释放剩余预留、领料草稿可转 `OCCUPATION`、领料过账消费占用或预留、工单发布不得生成占用的测试。
- [ ] 工单发布按 BOM 快照和 `issue_warehouse_id` 生成 `PRODUCTION_WORK_ORDER + RESERVATION`。
- [ ] 工单取消、完成和用料减少释放剩余预留。
- [ ] 生产领料草稿如建立执行锁定，则写 `PRODUCTION_MATERIAL_ISSUE + OCCUPATION`；过账消费占用或预留。
- [ ] 确保 BOM 用料预留基于工单用料快照，不回读实时 BOM。
- [ ] 执行：

```powershell
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=ProductionAdminControllerTests test
```

## 任务 5：物料级采购在途、质量和反向业务保护

**负责人：** 后端开发

**文件：**
- 修改：`apps/api/src/main/java/com/qherp/api/system/procurement/ProcurementAdminService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/quality/QualityAdminService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/reversal/ReversalAdminService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/reporting/ReportingAdminService.java`
- 测试：`ProcurementAdminControllerTests`、`QualityAdminControllerTests`、`ReversalAdminControllerTests`、`ReportingAdminControllerTests`

**接口：**
- 产出采购 `inTransitQuantity`、`inTransitStatus`，以及基于现货净可用和物料级在途参考的异常展示。

- [ ] 增加采购订单确认、部分入库、关闭、取消影响物料级在途参考的测试。
- [ ] 确认不在采购订单行增加仓库字段，不把在途计入仓库级账面库存或仓库级现货可承诺量。
- [ ] 增加冻结已预留或已占用合格库存的拒绝测试。
- [ ] 增加采购退货和生产补料遵守现货净可用的测试。
- [ ] 报表异常逻辑区分合格现货净可用和物料级采购在途参考。
- [ ] 执行受影响测试：

```powershell
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=ProcurementAdminControllerTests,QualityAdminControllerTests,ReversalAdminControllerTests,ReportingAdminControllerTests test
```

## 任务 6：前端接口类型和库存余额页面

**负责人：** 前端开发

**文件：**
- 修改：`apps/web/src/shared/api/inventoryApi.ts`
- 修改：`apps/web/src/shared/api/inventoryApi.spec.ts`
- 修改：`apps/web/src/modules/inventory/InventoryBalanceListView.vue`
- 修改：`apps/web/src/modules/inventory/InventoryBalanceListView.spec.ts`

**接口：**
- 消费任务 2 的后端库存字段。
- 产出账面、合格、预留、占用、冻结、现货净可用、物料级在途参考、仓库级现货可承诺和净需求缺口列。

- [ ] 更新接口类型和接口测试，覆盖新字段和预留占用端点。
- [ ] 更新库存余额页列和中文文案，明确采购在途为物料级参考。
- [ ] 增加库存流水、预留占用和在途参考的追溯入口；如接口暂不完整，页面不得伪造数据。
- [ ] 执行：

```powershell
npm --prefix apps/web test -- inventoryApi.spec.ts InventoryBalanceListView.spec.ts
npm --prefix apps/web run typecheck
```

## 任务 7：前端销售、生产和采购页面

**负责人：** 前端开发

**文件：**
- 修改：`apps/web/src/shared/api/salesApi.ts`
- 修改：`apps/web/src/modules/sales/SalesShipmentFormView.vue`
- 修改：`apps/web/src/modules/sales/SalesShipmentLineEditor.vue`
- 修改：`apps/web/src/shared/api/productionApi.ts`
- 修改：`apps/web/src/modules/production/ProductionMaterialIssueView.vue`
- 修改：`apps/web/src/shared/api/procurementApi.ts`
- 按需修改采购订单和入库页面。
- 测试受影响前端规格。

**接口：**
- 消费任务 3、任务 4 和任务 5 的后端字段。

- [ ] 更新接口类型，覆盖销售订单行预留仓库、预留、占用、现货净可用、仓库级现货可承诺量、物料级在途参考和禁用原因。
- [ ] 销售订单行新增预留仓库选择；销售出库展示并校验来源订单行预留仓库。
- [ ] 可见最大数量字段命名为“本次最多出库”和“本次最多领料”。
- [ ] 展示占用、预留、冻结、仓库不一致和在途不可直接使用的禁用原因。
- [ ] 采购订单列表和详情展示物料级采购在途参考，不做前端计算。
- [ ] 执行：

```powershell
npm --prefix apps/web test -- salesApi.spec.ts SalesShipmentFormView.spec.ts productionApi.spec.ts ProductionExecutionForms.spec.ts procurementApi.spec.ts
npm --prefix apps/web run typecheck
```

## 任务 8：文档、浏览器验证和视觉审计

**负责人：** 测试

**文件：**
- 修改：`docs/README.md`
- 修改：`docs/testing/acceptance-criteria.md`
- 新建：`docs/testing/inventory-availability-reservation-visual-audit/notes.md`
- 增加截图到 `docs/testing/inventory-availability-reservation-visual-audit/`
- 修改：`docs/manual/system-operation-manual.md`

**接口：**
- 消费所有已实现行为。

- [ ] 更新文档索引和验收标准。
- [ ] 更新操作手册，覆盖库存可用量、销售预留仓库、生产预留和物料级采购在途参考。
- [ ] 浏览器路径覆盖物料级采购在途参考、质量确认、销售预留仓库、销售出库消费、生产预留、生产领料、冻结拒绝、期间锁定、只读、无权限和移动端。
- [ ] 保存测试计划列出的截图并写视觉结论。
- [ ] 交付前验证窗口命令：

```powershell
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd test
npm --prefix apps/web test
npm --prefix apps/web run typecheck
npm --prefix apps/web run build
git diff --check
```

## 审查分工

- 产品经理审查业务范围、字段公式和验收矩阵。
- UI 设计师审查库存、销售、生产、采购和移动端截图。
- 前端开发在非本人实现范围内审查页面是否消费后端字段。
- 后端开发在非本人实现范围内审查后端服务、迁移和事务逻辑。
- 测试审查覆盖范围、浏览器证据和视觉审计。

实现审查必须交叉进行；实现者不得批准自己的规格或代码质量。

## 自检

- 规格覆盖：已覆盖数据模型、销售、生产、采购、质量、反向业务、前端、测试和视觉证据。
- 占位扫描：没有占位标记或未决范围；完整 MRP 和复杂 ATP 明确排除。
- 类型一致性：计划使用 `reservedQuantity`、`occupiedQuantity`、`availableQuantity`、`availableToPromiseQuantity`、`inTransitQuantity`、`netRequirementShortageQuantity`、`maxSelectableQuantity`，与接口契约一致。
