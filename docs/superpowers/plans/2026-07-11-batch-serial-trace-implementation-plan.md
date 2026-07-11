# 批次、序列号与来源去向追溯基础实施计划

> 给执行智能体：本计划由固定五角色分包执行。任务状态使用复选框维护；实现必须由对应固定角色子代理承担，主代理只做统筹、上下文补充、审查汇总和交付判断。规格审核和代码质量审核必须交叉进行，开发者不得批准自己的实现。

**目标：** 建立 019 批次、序列号与来源去向追溯基础，让核心库存单据链路能够采集、校验、展示和追溯批次/序列身份。

**架构：** 在 `mst_material` 增加追踪方式，在库存余额、库存流水、库存预留和通用追踪分配明细中引入批次/序列维度。后端统一负责追踪可用量、过账校验、来源继承和追溯链路；前端只消费后端返回的候选、禁用原因和追溯节点，不自行推导追踪库存。

**技术栈：** Java 21、Spring Boot、Spring JDBC、Flyway、PostgreSQL、JUnit/Testcontainers、Vue 3、TypeScript、Element Plus、Vitest、Playwright 辅助浏览器验收。

## 全局约束

- 所有沟通、文档、界面文案、提交信息、议题和拉取请求说明使用中文；技术标识、命令和路径可保留原文。
- 主代理只负责统筹、计划、派发、审查汇总和交付判断，不直接实现业务代码。
- 固定角色只有产品经理、UI 设计师、前端开发、后端开发、测试；不得新增第六类角色。
- 不进入完整 WMS、库位、容器、托盘、扫码设备、标签打印、MES、工序级追溯、法规召回、批次成本、MRP、排产或正式财务范围。
- 旧库存和旧流水默认不追踪；已有正库存、库存流水或活动预留的物料不得直接切换为批次或序列号追踪。
- 序列号代表单件，数量固定为 1；批次物料可拆多批，分配数量合计必须等于业务行数量。
- 销售订单和生产工单预留保持物料、仓库、质量状态维度；执行出库、领料、补料和采购退货时精确到批次或序列号。
- 销售退货必须继承原销售出库批次/序列；生产退料必须继承原生产领料批次/序列。
- 后端是批次/序列候选、可用量、禁用原因、追溯链路和错误码的最终来源。
- 开发期只运行受影响测试和必要类型检查；阶段交付前统一执行后端全量、前端全量、类型检查、生产构建、迁移验证、浏览器验收和视觉审计。

---

## 文件结构

- 新建 `apps/api/src/main/resources/db/migration/V16__batch_serial_trace_schema.sql`：追踪方式、批次、序列、追踪分配、余额/流水/预留维度扩展、索引和约束。
- 新建 `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryTrackingMethod.java`：物料追踪方式枚举。
- 新建 `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryTrackingAllocationType.java`：追踪分配类型枚举。
- 新建 `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryTrackingService.java`：批次/序列创建、候选校验、分配保存、过账前校验和来源继承。
- 新建 `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryTraceService.java`：批次/序列追溯聚合和权限脱敏。
- 修改 `apps/api/src/main/java/com/qherp/api/system/master/MaterialAdminService.java`：物料追踪方式保存、查询和不可变更校验。
- 修改 `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryPostingService.java`：过账请求接收追踪上下文并按追踪维度锁定余额。
- 修改 `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAvailabilityService.java`：活动预留和占用支持批次/序列维度。
- 修改 `apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAdminService.java` 和 `InventoryAdminController.java`：批次/序列列表、余额、流水、追溯接口。
- 修改 `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`：新增追踪相关受控错误码。
- 修改权限初始化和鉴权相关文件，新增 `inventory:batch:view`、`inventory:serial:view`、`inventory:trace:view`。
- 修改采购、销售、生产、质量、退货反冲服务，使其调用统一追踪服务并保存分配。
- 新建前端通用组件：`apps/web/src/modules/inventory/tracking/TrackingAllocationEditor.vue`、`TrackingAllocationReadonlyTable.vue`、`TrackingPickerDrawer.vue`、`InventoryTraceDrawer.vue`。
- 修改前端 API 类型：`masterDataApi.ts`、`inventoryApi.ts`、`procurementApi.ts`、`salesApi.ts`、`productionApi.ts`、`returnRefundReversalApi.ts`、`qualityInventoryStatusApi.ts`。
- 修改前端页面：物料档案、库存余额、库存流水、采购入库、销售出库、生产领料、完工入库、销售退货、采购退货、生产退料、生产补料、质量确认。
- 新建视觉审计目录：`docs/testing/019-batch-serial-trace-visual-audit/`。

## 工作包 1：后端追踪模型、迁移和物料追踪方式

**负责人：** 后端开发

**审核：** 产品经理做规格审核；测试做迁移和用例覆盖审核；前端开发只审接口字段是否满足页面消费。

**文件：**
- 新建：`apps/api/src/main/resources/db/migration/V16__batch_serial_trace_schema.sql`
- 新建：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryTrackingMethod.java`
- 新建：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryTrackingAllocationType.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/master/MaterialAdminService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/master/MaterialAdminController.java`
- 修改：`apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/master/MaterialAdminControllerTests.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/inventory/InventoryAdminControllerTests.java`

**产出接口：**
- `trackingMethod`、`trackingMethodName` 出现在物料列表、详情、创建和更新响应。
- 物料查询支持 `trackingMethod`。
- 有正库存、库存流水或活动预留时，更新追踪方式返回 `INVENTORY_TRACKING_METHOD_IMMUTABLE`。

- [ ] 增加物料追踪方式测试：存量默认 `NONE`，创建批次物料和序列物料成功，查询可按追踪方式筛选。
- [ ] 增加不可变更测试：存在库存余额、库存流水或活动预留的物料，从 `NONE` 改为 `BATCH` 或 `SERIAL` 被拒绝。
- [ ] 编写 `V16__batch_serial_trace_schema.sql`，包含 `tracking_method`、`inv_batch`、`inv_serial`、`inv_stock_tracking_allocation`，以及 `inv_stock_balance`、`inv_stock_movement`、`inv_stock_reservation` 的追踪维度扩展。
- [ ] 为非追踪、批次、序列余额建立部分唯一索引，保证空值维度不造成唯一约束误判。
- [ ] 新增追踪错误码和权限初始化数据。
- [ ] 执行定向验证：

```powershell
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=MaterialAdminControllerTests,InventoryAdminControllerTests,AccountPermissionInitializerTests test
```

预期：追踪方式、迁移兼容和权限初始化相关测试通过。

## 工作包 2：后端追踪过账底座、入库、质量确认和冻结解冻

**负责人：** 后端开发

**审核：** 测试做规格覆盖审核；前端开发审响应结构；UI 设计师审详情展示字段是否足够支撑页面。

**文件：**
- 新建：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryTrackingService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryPostingService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/procurement/ProcurementAdminService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/production/ProductionAdminService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/quality/QualityAdminService.java`
- 测试：`ProcurementAdminControllerTests`、`ProductionAdminControllerTests`、`QualityAdminControllerTests`

**产出接口：**
- 入库类业务行支持 `trackingAllocations`。
- 采购入库、完工入库、质量确认、冻结、解冻详情回显追踪身份。
- 批次物料支持拆批；序列号物料逐件校验。

- [ ] 增加采购入库批次拆分测试，断言批次主档、库存余额、库存流水和追踪分配明细全部生成。
- [ ] 增加采购入库序列号测试，断言数量必须为整数、序列数量必须等于入库数量、重复序列号被拒绝。
- [ ] 增加完工入库生成成品批次/序列并关联工单的测试。
- [ ] 增加质量确认测试，断言批次可拆分质量状态，序列号每个序列只能处于一个质量状态。
- [ ] 增加冻结和解冻测试，断言追踪身份不变，冻结后影响可用量，解冻回到同一身份的合格库存。
- [ ] 实现 `InventoryTrackingService` 的批次/序列创建、分配校验、来源继承和过账前校验。
- [ ] 扩展 `InventoryPostingService.PostingRequest`，让库存过账按追踪维度锁定和更新余额。
- [ ] 接入采购入库、完工入库、质量确认、冻结和解冻。
- [ ] 执行定向验证：

```powershell
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=ProcurementAdminControllerTests,ProductionAdminControllerTests,QualityAdminControllerTests,InventoryAdminControllerTests test
```

预期：入库、质量和冻结追踪身份相关测试通过。

## 工作包 3：后端出库、预留占用、退货反冲和追溯接口

**负责人：** 后端开发

**审核：** 产品经理审业务链路；测试审异常和并发覆盖；前端开发审候选和追溯响应字段。

**文件：**
- 新建：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryTraceService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAvailabilityService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAdminService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryAdminController.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/sales/SalesAdminService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/production/ProductionAdminService.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/reversal/ReversalAdminService.java`
- 测试：`SalesAdminControllerTests`、`ProductionAdminControllerTests`、`ReversalAdminControllerTests`、`InventoryAdminControllerTests`、`PermissionAuthorizationTests`

**产出接口：**
- `GET /api/admin/inventory/batches`
- `GET /api/admin/inventory/serials`
- `GET /api/admin/inventory/traces/batches/{id}`
- `GET /api/admin/inventory/traces/serials/{id}`
- 销售、生产、退货反冲候选和详情回显追踪分配。

- [ ] 增加销售出库选择批次/序列测试，断言只能选择当前仓库、合格、未冻结、可用库存。
- [ ] 增加生产领料、生产补料和采购退货选择批次/序列测试，断言超可用量、跨仓、错误物料和冻结库存被拒绝。
- [ ] 增加销售退货继承原销售出库身份测试，断言篡改来源批次/序列返回 `INVENTORY_TRACKING_SOURCE_MISMATCH`。
- [ ] 增加生产退料继承原生产领料身份测试。
- [ ] 增加物料级预留转执行批次/序列占用测试，断言父预留消费或释放正确。
- [ ] 增加并发抢同一序列号测试，断言只能成功一次，失败事务不产生部分写入。
- [ ] 实现批次/序列列表、候选查询和追溯接口。
- [ ] 实现追溯权限脱敏：无来源详情权限时返回受限摘要，不泄露越权详情。
- [ ] 接入销售、生产、采购退货、销售退货、生产退料和生产补料。
- [ ] 执行定向验证：

```powershell
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=SalesAdminControllerTests,ProductionAdminControllerTests,ReversalAdminControllerTests,InventoryAdminControllerTests,PermissionAuthorizationTests test
```

预期：出库、退货、追溯、权限和并发核心用例通过。

## 工作包 4：前端 API 类型、通用追踪组件、库存余额和追溯抽屉

**负责人：** 前端开发

**审核：** UI 设计师审交互和视觉结构；测试审组件覆盖；后端开发审字段消费是否与接口契约一致。

**文件：**
- 修改：`apps/web/src/shared/api/masterDataApi.ts`
- 修改：`apps/web/src/shared/api/inventoryApi.ts`
- 新建：`apps/web/src/modules/inventory/tracking/TrackingAllocationEditor.vue`
- 新建：`apps/web/src/modules/inventory/tracking/TrackingAllocationReadonlyTable.vue`
- 新建：`apps/web/src/modules/inventory/tracking/TrackingPickerDrawer.vue`
- 新建：`apps/web/src/modules/inventory/tracking/InventoryTraceDrawer.vue`
- 修改：`apps/web/src/modules/materials/items/MaterialItemListView.vue`
- 修改：`apps/web/src/modules/inventory/InventoryBalanceListView.vue`
- 修改：`apps/web/src/modules/inventory/InventoryMovementListView.vue`
- 测试：`masterDataApi.spec.ts`、`inventoryApi.spec.ts`、`MaterialItemListView.spec.ts`、`InventoryBalanceListView.spec.ts`、`InventoryMovementListView.spec.ts`

**产出：**
- 通用追踪分配编辑器、只读表、候选选择抽屉和追溯抽屉。
- 物料追踪方式字段。
- 库存余额和库存流水按追踪身份筛选、展示和跳转追溯。

- [ ] 扩展前端 API 类型和测试，覆盖追踪方式、批次/序列列表、余额/流水追踪字段和追溯响应。
- [ ] 物料档案展示追踪方式；存在不可变更原因时禁用并显示提示。
- [ ] 实现批次分配编辑器：批次号、数量、合计校验、行内错误。
- [ ] 实现序列号录入：逐条录入、批量粘贴、去重、数量一致校验。
- [ ] 实现候选选择抽屉：不可用候选禁用，显示质量状态、冻结状态、可用量和后端禁用原因。
- [ ] 实现追溯抽屉：基础信息、当前库存、来源去向、质量事件、库存流水、权限受限摘要。
- [ ] 库存余额和库存流水增加追踪筛选、追踪列、追溯入口和移动端可读布局。
- [ ] 执行定向验证：

```powershell
npm --prefix apps/web test -- masterDataApi.spec.ts inventoryApi.spec.ts MaterialItemListView.spec.ts InventoryBalanceListView.spec.ts InventoryMovementListView.spec.ts
npm --prefix apps/web run typecheck
```

预期：通用追踪组件和库存页面相关测试通过，类型检查通过。

## 工作包 5：前端采购、销售、生产、质量和退货反冲页面接入

**负责人：** 前端开发

**审核：** UI 设计师审页面密度、抽屉和移动端；测试审业务路径覆盖；后端开发审请求载荷。

**文件：**
- 修改：`apps/web/src/shared/api/procurementApi.ts`
- 修改：`apps/web/src/shared/api/salesApi.ts`
- 修改：`apps/web/src/shared/api/productionApi.ts`
- 修改：`apps/web/src/shared/api/returnRefundReversalApi.ts`
- 修改：`apps/web/src/shared/api/qualityInventoryStatusApi.ts`
- 修改：采购入库表单和详情组件。
- 修改：销售出库表单和详情组件。
- 修改：生产领料、完工入库、生产补料表单和详情组件。
- 修改：销售退货、采购退货、生产退料表单和详情组件。
- 修改：质量确认和冻结解冻相关组件。
- 测试：对应 API 和页面规格测试。

**产出：**
- 入库类页面可以录入批次/序列。
- 出库类页面可以选择可用批次/序列。
- 退货和退料页面继承来源追踪身份。
- 已过账、只读和无权限状态正确回显。

- [ ] 扩展采购、销售、生产、反向业务和质量 API 类型与测试。
- [ ] 采购入库和完工入库接入追踪分配编辑器，提交 `trackingAllocations` 并回显。
- [ ] 销售出库、生产领料、采购退货、生产补料接入候选选择抽屉，使用后端可用候选和禁用原因。
- [ ] 销售退货和生产退料按来源只读继承追踪身份，不允许任意改选。
- [ ] 质量确认、冻结、解冻显示批次/序列身份和状态变化。
- [ ] 已过账、已取消、只读权限下追踪信息可查看但不可编辑。
- [ ] 执行定向验证：

```powershell
npm --prefix apps/web test -- procurementApi.spec.ts salesApi.spec.ts productionApi.spec.ts returnRefundReversalApi.spec.ts qualityInventoryStatusApi.spec.ts PurchaseReceiptFormView.spec.ts SalesShipmentFormView.spec.ts ProductionExecutionForms.spec.ts SalesReturnViews.spec.ts PurchaseReturnViews.spec.ts ProductionMaterialReversalViews.spec.ts QualityInspectionViews.spec.ts
npm --prefix apps/web run typecheck
```

预期：核心业务表单和详情追踪接入测试通过，类型检查通过。

## 工作包 6：阶段验证、文档、浏览器验收和视觉审计

**负责人：** 测试

**审核：** 产品经理审业务验收矩阵；UI 设计师审视觉结论；前端和后端分别审受影响实现质量。测试角色不审自己编写的自动化代码质量。

**文件：**
- 修改：`docs/testing/acceptance-criteria.md`
- 修改：`docs/manual/system-operation-manual.md`
- 新建：`docs/testing/019-batch-serial-trace-visual-audit/notes.md`
- 新增截图：`docs/testing/019-batch-serial-trace-visual-audit/*.png`
- 修改：`docs/handoffs/2026-07-06-project-handoff-current.md`

**产出：**
- 019 自动化验证记录。
- 浏览器验收路径和视觉审计证据。
- 操作手册和交接文档更新。

- [ ] 更新验收标准和操作手册，覆盖物料追踪方式、入库追踪分配、出库选择、退货继承、质量冻结和追溯抽屉。
- [ ] 执行浏览器主路径：物料设置追踪方式、采购入库批次/序列、完工入库、质量确认、销售出库、生产领料、销售退货、生产退料、库存余额、库存流水、追溯抽屉。
- [ ] 执行异常路径：缺少批次、重复序列、数量不一致、跨仓选择、冻结库存选择、退货篡改来源、期间锁定和无权限。
- [ ] 保存测试计划要求的桌面和移动端截图。
- [ ] 编写视觉审计结论，明确布局、信息密度、关键操作可见性、长文本溢出、表格横向滚动、弹窗/抽屉遮挡、移动端可操作性和权限状态识别。
- [ ] 进入唯一交付前全量验证窗口：

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

预期：后端全量、前端全量、类型检查、生产构建和空白检查通过；浏览器验收和视觉审计无阻断问题。

## 阶段集中审查

全部工作包功能完整后，只组织一轮阶段集中审查：

- 产品经理：核对 019 任务记录和业务验收矩阵，确认范围没有缩水或越界。
- UI 设计师：审查关键页面、追踪抽屉、候选选择、只读/无权限/移动端和视觉证据。
- 后端开发：审查前端实现质量和接口消费一致性，不审自己写的后端代码。
- 前端开发：审查后端接口行为、字段稳定性和错误码可消费性，不审自己写的前端代码。
- 测试：审查覆盖、异常路径、期间锁定、权限、迁移和回归风险。

主代理合并去重审查意见，一次性交给对应角色整改。阻断和严重问题必须修复；一般问题默认进入后续清单，除非影响当前验收或用户明确要求，否则不得反复阻断阶段交付。

## 自检

- 规格覆盖：覆盖物料追踪方式、批次/序列模型、库存余额、库存流水、预留占用、入库、出库、质量、冻结、退货、追溯、权限、错误码、前端页面、测试和视觉审计。
- 范围控制：不包含完整 WMS、扫码、库位、MES、法规召回、批次成本、MRP 或正式财务。
- 工作包粒度：6 个工作包按完整业务能力划分，不把红测、编码、转绿、文档或审查拆成微任务。
- 审查约束：规格审核和代码质量审核由不同角色承担，开发者不得自审。
