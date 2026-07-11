# 批次、序列号与来源去向追溯测试计划

## 测试目标

验证 019 批次、序列号与来源去向追溯基础满足追踪身份采集、库存底账维度、质量状态联动、可用量校验、退货继承、来源去向追溯、权限、期间保护、浏览器操作和视觉证据要求。

测试重点是确保批次/序列不是普通文本字段，而是参与库存余额、库存流水、占用预留、质量确认、冻结解冻和业务过账的可信身份。

## 测试数据基线

固定准备三类物料：

- 不追踪物料：验证 018 兼容性和存量数据不被误要求批次/序列。
- 批次追踪物料：验证一行拆多批、部分质量确认、批次库存可用量和批次追溯。
- 序列号追踪物料：验证单件序列、重复序列号、跨仓库、冻结状态、已出库状态和序列追溯。

基础链路数据覆盖采购入库、生产完工、质量确认、销售出库、生产领料、销售退货、采购退货、生产退料、生产补料、冻结解冻、期间锁定和权限差异。

## 范围

### 本次测试

- 物料追踪方式保存、查询、筛选和不可变更约束。
- 批次主档、序列号主档、库存追踪分配明细的创建、查询和唯一性。
- 入库类单据生成或继承批次/序列身份。
- 出库类单据选择可用批次/序列并精确扣减库存。
- 销售退货和生产退料继承来源追踪身份。
- 质量确认、冻结和解冻后追踪身份不变，质量状态影响可用量。
- 库存余额、库存流水和占用预留按批次/序列维度正确展示和筛选。
- 批次追溯和序列号追溯返回来源、去向、当前结存、质量事件和权限脱敏信息。
- 权限、只读、期间锁定、异常提示、并发和幂等。
- 桌面和移动端视觉审计。

### 本次不测试

- 完整 WMS、库位、容器、托盘、波次、拣货和盘点。
- 条码、二维码、RFID、PDA、扫码枪和标签打印。
- MES、工序级追溯、设备采集和工艺路线。
- 法规级召回、复杂图谱追溯、批次成本和序列号成本。
- MRP、排产、计划订单和正式财务。

## 自动化验收矩阵

| 范围 | 后端覆盖重点 | 前端覆盖重点 | 阻断风险 |
|---|---|---|---|
| 物料追踪方式 | 默认值、保存、筛选、存量物料不可直接切换 | 字段展示、保存载荷、不可编辑原因 | 存量物料被错误强制追踪 |
| 入库生成 | 采购入库、完工入库、销售退货、生产退料生成或继承追踪身份 | 批次录入、序列录入、数量校验 | 批次/序列丢失、重复、数量不一致 |
| 出库选择 | 销售出库、生产领料、采购退货、生产补料只能选择有效可用身份 | 候选选择抽屉、不可用禁选、错误提示 | 超发、选错仓库、选冻结或待检库存 |
| 退货继承 | 销售退货继承原出库，生产退料继承原领料 | 来源批次/序列只读回显 | 退货可篡改来源身份 |
| 质量与冻结 | 质量确认、冻结、解冻保留身份并影响可用量 | 状态变化后身份持续显示 | 非合格或冻结库存被普通出库 |
| 库存余额/流水 | 按追踪维度汇总，流水方向、数量、来源正确 | 批次/序列筛选、追溯入口、横向滚动 | 负库存、序列号重复在库、流水方向错误 |
| 追溯接口 | 来源、去向、质量事件、反向业务、权限脱敏 | 追溯抽屉加载、空态、错误态、受限态 | 来源去向断链或越权 |
| 权限与期间 | 写接口鉴权，锁定期间拒绝写入 | 按权限隐藏、禁用或显示无权状态 | 权限或期间保护绕过 |
| 并发与幂等 | 重复提交、并发出库、并发冻结不造成重复或负库存 | 错误后刷新状态一致 | 同一序列被重复出库或重复在库 |

## 后端定向测试范围

新增或扩展：

- `MaterialAdminControllerTests`
- `InventoryAdminControllerTests`
- `InventoryTraceControllerTests`
- `QualityAdminControllerTests`
- `ProcurementAdminControllerTests`
- `SalesAdminControllerTests`
- `ProductionAdminControllerTests`
- `ReversalAdminControllerTests`
- `PermissionAuthorizationTests`
- `AccountPermissionInitializerTests`
- `BusinessPeriodAdminControllerTests`

重点用例：

- 存量物料默认 `NONE`，库存和流水兼容 018 数据。
- 有正库存、库存流水或活动预留时，物料追踪方式不可切换。
- 批次物料采购入库一行拆多批，分配数量合计不一致被拒绝。
- 序列号物料采购入库数量必须为整数，序列数量必须等于业务数量，重复序列号被拒绝。
- 完工入库生成成品批次/序列，并关联生产工单。
- 销售出库和生产领料只能选择当前仓库、合格、未冻结、未占用的批次/序列。
- 采购退货和生产补料作为出库类动作受追踪可用量校验。
- 销售退货只能继承原销售出库批次/序列；生产退料只能继承原领料批次/序列。
- 质量确认对同一批次可拆分质量状态；序列号每个序列只能处于一个质量状态。
- 冻结已占用或已预留的追踪库存被拒绝。
- 批次和序列追溯接口返回来源、质量事件、库存流水、去向和当前结存。
- 无来源详情权限时追溯接口返回受限摘要，不泄露越权详情。
- 锁定期间内影响库存事实的追踪写入返回 `BUSINESS_PERIOD_LOCKED`。
- 并发抢占同一序列号时只能成功一次，失败事务不产生部分写入。

推荐定向命令：

```powershell
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=MaterialAdminControllerTests,InventoryAdminControllerTests,InventoryTraceControllerTests,QualityAdminControllerTests,ProcurementAdminControllerTests,SalesAdminControllerTests,ProductionAdminControllerTests,ReversalAdminControllerTests,PermissionAuthorizationTests,AccountPermissionInitializerTests,BusinessPeriodAdminControllerTests test
```

## 前端定向测试范围

新增或扩展：

- `apps/web/src/shared/api/masterDataApi.spec.ts`
- `apps/web/src/shared/api/inventoryApi.spec.ts`
- `apps/web/src/shared/api/procurementApi.spec.ts`
- `apps/web/src/shared/api/salesApi.spec.ts`
- `apps/web/src/shared/api/productionApi.spec.ts`
- `apps/web/src/shared/api/returnRefundReversalApi.spec.ts`
- `apps/web/src/shared/api/qualityInventoryStatusApi.spec.ts`
- `apps/web/src/modules/materials/items/MaterialItemListView.spec.ts`
- `apps/web/src/modules/inventory/InventoryBalanceListView.spec.ts`
- `apps/web/src/modules/inventory/InventoryMovementListView.spec.ts`
- `apps/web/src/modules/procurement/PurchaseReceiptFormView.spec.ts`
- `apps/web/src/modules/sales/SalesShipmentFormView.spec.ts`
- `apps/web/src/modules/production/ProductionExecutionForms.spec.ts`
- `apps/web/src/modules/reversal/SalesReturnViews.spec.ts`
- `apps/web/src/modules/reversal/PurchaseReturnViews.spec.ts`
- `apps/web/src/modules/reversal/ProductionMaterialReversalViews.spec.ts`
- `apps/web/src/modules/quality/QualityInspectionViews.spec.ts`

重点用例：

- 物料追踪方式字段展示、保存、筛选和不可编辑原因。
- 批次分配数量合计校验、序列号去重、序列数量一致校验。
- 非追踪物料不显示追踪分配编辑区。
- 出库候选抽屉禁用不可用批次/序列，并展示后端原因。
- 详情页只读回显批次/序列分配。
- 销售退货、生产退料继承来源追踪身份，不允许改选。
- 库存余额和流水支持批次/序列筛选，跳转追溯抽屉带正确参数。
- 追溯抽屉覆盖加载、错误、空态、权限受限状态。
- 400、403、409、期间锁定错误映射到明确页面提示。

推荐定向命令：

```powershell
npm --prefix apps/web test -- masterDataApi.spec.ts inventoryApi.spec.ts procurementApi.spec.ts salesApi.spec.ts productionApi.spec.ts returnRefundReversalApi.spec.ts qualityInventoryStatusApi.spec.ts MaterialItemListView.spec.ts InventoryBalanceListView.spec.ts InventoryMovementListView.spec.ts PurchaseReceiptFormView.spec.ts SalesShipmentFormView.spec.ts ProductionExecutionForms.spec.ts SalesReturnViews.spec.ts PurchaseReturnViews.spec.ts ProductionMaterialReversalViews.spec.ts QualityInspectionViews.spec.ts
npm --prefix apps/web run typecheck
```

## 浏览器验收矩阵

| 场景 | 步骤重点 | 预期结果 |
|---|---|---|
| 物料追踪方式 | 创建不追踪、批次、序列号三类物料 | 列表和详情展示追踪方式，存量受限原因清晰 |
| 批次采购入库 | 批次物料采购入库拆分两个批次 | 入库后批次库存、流水和追溯可查 |
| 序列采购入库 | 序列号物料录入多个序列 | 序列数量等于入库数量，重复项被拦截 |
| 完工入库 | 成品按批次或序列完工入库 | 成品追溯关联生产工单 |
| 质量确认 | 对批次/序列执行合格、不合格、冻结 | 质量状态变化不丢追踪身份 |
| 销售出库 | 选择可用批次/序列并过账 | 对应追踪库存减少，去向出现销售出库 |
| 生产领料 | 选择可用批次/序列并过账 | 追踪库存减少，去向出现生产工单 |
| 销售退货 | 从原销售出库创建退货 | 退回身份与原出库一致 |
| 生产退料 | 从原领料创建退料 | 退回身份与原领料一致 |
| 采购退货 | 选择当前可用批次/序列退回供应商 | 库存扣减，追溯显示退回供应商 |
| 冻结解冻 | 按批次/序列冻结和解冻 | 冻结影响可用量，解冻恢复 |
| 异常路径 | 缺批次、重复序列、超可用量、跨仓选择 | 前后端拦截并显示明确错误 |
| 权限路径 | 无追溯权限用户访问追溯 | 前端无权状态和后端 403 一致 |
| 只读路径 | 已过账、已取消单据详情查看 | 批次/序列可看不可改 |
| 期间锁定 | 锁定期间内执行追踪库存写入 | 写入被拒绝，历史追溯仍可查 |

## 视觉验收矩阵

视觉分析目录：`docs/testing/019-batch-serial-trace-visual-audit/`。

桌面视口建议 `1440x900`，移动端视口建议 `390x844`。

截图清单：

- `01-material-tracking-method-desktop.png`：物料追踪方式字段。
- `02-purchase-receipt-batch-allocation-desktop.png`：采购入库批次分配。
- `03-purchase-receipt-serial-allocation-desktop.png`：采购入库序列号录入。
- `04-completion-receipt-tracking-desktop.png`：完工入库追踪分配。
- `05-sales-shipment-tracking-picker-desktop.png`：销售出库批次/序列选择。
- `06-production-issue-tracking-picker-desktop.png`：生产领料批次/序列选择。
- `07-sales-return-source-tracking-desktop.png`：销售退货来源继承。
- `08-production-return-source-tracking-desktop.png`：生产退料来源继承。
- `09-quality-tracking-process-desktop.png`：质量确认保留追踪身份。
- `10-freeze-tracking-error-desktop.png`：冻结不可用追踪库存错误。
- `11-inventory-balance-tracking-desktop.png`：库存余额追踪维度。
- `12-inventory-movement-tracking-desktop.png`：库存流水追踪筛选。
- `13-batch-trace-drawer-desktop.png`：批次追溯抽屉。
- `14-serial-trace-drawer-desktop.png`：序列追溯抽屉。
- `15-trace-permission-restricted-desktop.png`：追溯权限受限。
- `16-period-locked-tracking-error-desktop.png`：期间锁定错误。
- `17-inventory-balance-tracking-mobile-390x844.png`：移动端库存余额。
- `18-trace-drawer-mobile-390x844.png`：移动端追溯抽屉。
- `19-tracking-picker-mobile-390x844.png`：移动端选择器查看路径。

视觉结论必须明确：布局、信息密度、关键操作可见性、批次/序列长文本溢出、表格横向滚动、弹窗/抽屉遮挡、移动端选择器可操作性、业务状态识别和权限状态识别是否达标。

## 阻断缺陷

- 系统无法启动或核心页面不可访问。
- 库存相关 Flyway 迁移失败。
- 批次/序列生成、选择或详情回显不可用。
- 库存余额错误、负库存、序列号重复在库。
- 追踪身份丢失、串链或退货可篡改来源身份。
- 冻结、待检、不合格或已占用库存可被普通出库。
- 期间锁定、后端鉴权或追溯权限可绕过。
- 追溯来源去向关键链路错误。
- 视觉证据缺失，或视觉问题影响关键数据识别、核心操作或权限状态判断。

## 严重缺陷

- 非主路径追踪展示缺失但底账正确。
- 错误提示不明确但后端已正确拦截。
- 移动端可操作但布局拥挤。
- 追溯抽屉缺少非关键元数据。
- 筛选条件组合存在边界问题。
- 性能明显变慢但未影响当前核心验收。

严重缺陷需评估是否影响当前验收；阻断缺陷必须修复后再进入交付。

## 验证窗口

开发期只运行受影响范围：后端定向测试、前端组件/API/页面测试、必要类型检查和主路径集成冒烟。不得把项目全量测试作为每个工作包重复门禁。

阶段交付前统一执行：

- 后端全量测试。
- 前端全量测试。
- 前端类型检查。
- 前端生产构建。
- 数据库迁移验证。
- 浏览器主路径、异常路径、权限路径、只读路径和期间锁定验收。
- 视觉审计截图与结论。

全量窗口发现问题时，先用定向测试定位和修复，再完成最终全量确认。
