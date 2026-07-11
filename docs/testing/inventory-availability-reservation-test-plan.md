# 库存可用量、占用、冻结与预留测试计划

## 测试目标

验证 018 库存可用量、占用、冻结与预留基础满足数量口径、占用预留、物料级采购在途参考、销售现货可承诺、计划净需求缺口前置口径、权限、期间保护、来源追溯、浏览器操作和视觉证据要求。

测试重点是确保 `availableQuantity` 只代表现货净可用，待检、不合格、冻结、已占用和已预留库存不得被销售出库、生产领料、生产补料或计划口径误用。

## 范围

### 本次测试

- 库存余额返回账面、合格、占用、预留、冻结、现货净可用、物料级采购在途参考、仓库级现货可承诺量和净需求缺口。
- 占用预留台账来源、状态、数量、释放、消费和审计完整。
- 销售订单行预留仓库、确认、取消、关闭、出库过账正确生成、释放或消费预留；销售出库仓库必须与来源订单行预留仓库一致。
- 生产工单发布、取消、完成、领料草稿和领料过账正确生成、释放或消费用料预留；工单发布不得直接生成占用。
- 采购订单确认、部分入库、关闭、取消正确影响物料级采购在途参考。
- 质量冻结、解冻和质量确认后可用量重新计算正确。
- 库存调减、采购退货、生产补料不能绕过可用量、质量状态和占用预留校验。
- 权限、期间保护、异常路径、并发超占和来源追溯。
- 前端库存余额、销售候选、生产候选、采购在途参考、权限态、异常态、空态和窄屏视觉验收。

### 本次不测试

- 完整 MRP、计划订单生成、排产、产能计划。
- 批次、序列号、库位、条码、拣配、调拨和盘点。
- 正式财务、库存月结、成本结转。
- 复杂审批流、消息通知和 BI 平台。

## 自动化验收矩阵

| 范围 | 后端覆盖重点 | 前端覆盖重点 | 阻断风险 |
|---|---|---|---|
| 库存口径 | 账面、合格、冻结、占用、预留、现货净可用、物料级在途参考、仓库级现货可承诺、净需求缺口公式正确 | 库存余额页字段展示清晰，不自行推导 | 公式错误或重复计算 |
| 占用预留台账 | 来源唯一、状态流转、释放、消费、审计和并发 | 占用和预留追溯抽屉可读 | 残留占用或重复预留 |
| 销售承诺 | 订单行预留仓库必填，确认按预留仓库生成预留，出库仓库不一致被拒绝，取消关闭释放，出库消费 | 候选库存展示现货可承诺、最大出库和禁用原因 | 超卖、跨仓消耗或在途被预留 |
| 生产用料 | 发布生成预留，草稿领料可转占用，取消完成释放，领料消费 | 工单用料和领料候选展示当前可领 | 工单发布写成占用或领料绕过 |
| 采购在途 | 确认形成物料级在途参考，部分入库减少，关闭取消不计入 | 采购列表和详情展示物料级在途数量与状态 | 在途被当成现货或仓库级可承诺 |
| 质量联动 | 冻结已占用预留库存被拒绝，非合格不可承诺 | 冻结、待检、不合格禁用原因清晰 | 非合格或冻结被占用消耗 |
| 权限与期间 | 写接口鉴权和 `BUSINESS_PERIOD_LOCKED` | 只读、无权限、错误态明确 | 前端隐藏替代后端鉴权 |
| 并发与事务 | 并发预留不超占，失败无部分写入 | 错误后刷新状态一致 | 负库存、重复台账、缓存悬挂 |

## 后端定向测试范围

新增或扩展：

- `InventoryAdminControllerTests`
- `SalesAdminControllerTests`
- `ProductionAdminControllerTests`
- `ProcurementAdminControllerTests`
- `ReversalAdminControllerTests`
- `QualityAdminControllerTests`
- `PermissionAuthorizationTests`
- `AccountPermissionInitializerTests`
- `BusinessPeriodAdminControllerTests`

重点用例：

- 库存余额接口返回全部 018 口径字段，且现货净可用等于合格现存减占用和预留，仓库级现货可承诺量不叠加物料级采购在途参考。
- 同仓同料存在待检、合格、不合格、冻结、占用、预留和物料级在途参考时，账面库存不含在途，销售确认门禁不使用在途。
- 同一销售订单行不能重复生成活跃预留，且销售订单行确认前必须有预留仓库。
- 销售订单取消、关闭、数量减少释放预留；销售出库仓库与预留仓库不一致被拒绝；出库过账消费预留并扣减合格库存。
- 生产工单发布按 BOM 和领料仓库生成 `RESERVATION`；取消、完成释放剩余预留；领料草稿可转 `OCCUPATION`；领料过账消费占用或预留。
- 采购订单确认形成物料级在途参考；部分入库减少在途；入库过账形成具体仓库待检库存。
- 冻结已预留或已占用合格库存返回受控错误。
- 账面足够但现货净可用不足时返回 `INVENTORY_AVAILABLE_NOT_ENOUGH` 或模块映射错误。
- 并发预留同一合格库存时不得超占。
- 锁定期间内销售确认、生产发布、采购确认、释放或消费占用预留均返回 `BUSINESS_PERIOD_LOCKED`。

推荐命令：

```powershell
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=InventoryAdminControllerTests,SalesAdminControllerTests,ProductionAdminControllerTests,ProcurementAdminControllerTests,ReversalAdminControllerTests,QualityAdminControllerTests,PermissionAuthorizationTests,AccountPermissionInitializerTests,BusinessPeriodAdminControllerTests test
```

## 前端定向测试范围

新增或扩展：

- `apps/web/src/shared/api/inventoryApi.spec.ts`
- `apps/web/src/modules/inventory/InventoryBalanceListView.spec.ts`
- `apps/web/src/shared/api/salesApi.spec.ts`
- `apps/web/src/modules/sales/SalesShipmentFormView.spec.ts`
- `apps/web/src/shared/api/productionApi.spec.ts`
- `apps/web/src/modules/production/ProductionExecutionForms.spec.ts`
- `apps/web/src/shared/api/procurementApi.spec.ts`
- `apps/web/src/router/permissionGuard.spec.ts`
- `apps/web/src/App.spec.ts`

重点用例：

- 库存余额页展示账面、合格、占用、预留、冻结、现货净可用、物料级采购在途参考、仓库级现货可承诺量和净需求缺口。
- 销售候选行展示仓库级现货可承诺量、占用、预留、本次最多出库和禁用原因。
- 生产领料候选行展示当前可领、本次最多领料和禁用原因。
- 销售订单行展示和提交预留仓库。
- 采购订单或详情展示物料级采购在途参考、预计到货和在途状态。
- 前端只消费后端字段，不自行计算最终可用量。
- 只读权限隐藏或禁用写动作，无权限直访显示无权限状态。

推荐命令：

```powershell
npm --prefix apps/web test -- inventoryApi.spec.ts InventoryBalanceListView.spec.ts salesApi.spec.ts SalesShipmentFormView.spec.ts productionApi.spec.ts ProductionExecutionForms.spec.ts procurementApi.spec.ts permissionGuard.spec.ts App.spec.ts
npm --prefix apps/web run typecheck
```

## 浏览器验收矩阵

| 场景 | 步骤重点 | 预期结果 |
|---|---|---|
| 采购在途到待检 | 确认采购订单，查看物料级在途参考，采购入库选择仓库并过账 | 物料级在途减少，具体仓库待检库存增加，现货净可用不直接增加 |
| 质量确认到可用 | 将待检确认合格 | 合格现存和现货净可用增加 |
| 销售预留 | 销售订单行选择预留仓库并确认 | 预留增加，现货净可用减少，仓库级现货可承诺变化 |
| 销售出库消费 | 创建并过账同预留仓库的销售出库 | 预留或占用被消费，账面合格库存扣减 |
| 销售跨仓拦截 | 销售出库选择不同于来源订单行预留仓库的仓库 | 后端拒绝，不自动跨仓释放、改仓或分配 |
| 生产预留 | 发布生产工单 | BOM 用料按领料仓库生成预留，现货净可用减少，不直接记为占用 |
| 生产领料消费 | 过账生产领料 | 预留或占用被消费，合格库存扣减 |
| 冻结拒绝 | 尝试冻结已预留或占用库存 | 后端拒绝，错误原因清晰 |
| 可用不足 | 账面足够但已被占用 | 出库或领料被拒绝，不显示普通库存不足 |
| 期间保护 | 锁定期间内确认、发布或释放 | 返回 `BUSINESS_PERIOD_LOCKED`，历史查询可访问 |
| 权限差异 | 只读和无权限账号访问 | 只读可看，不可写；无权限被拒绝 |

## 视觉验收矩阵

视觉分析目录：`docs/testing/inventory-availability-reservation-visual-audit/`。

截图清单：

- `01-inventory-availability-overview-desktop.png`：库存余额口径总览。
- `02-inventory-reservation-drawer-desktop.png`：占用预留追溯抽屉。
- `03-inventory-in-transit-drawer-desktop.png`：采购在途追溯抽屉。
- `04-sales-order-reservation-desktop.png`：销售订单行预留仓库和预留后详情。
- `05-sales-shipment-available-shortage-desktop.png`：销售候选可用不足。
- `06-production-work-order-reservation-desktop.png`：生产工单用料预留。
- `07-production-issue-occupied-shortage-desktop.png`：生产领料候选禁用。
- `08-procurement-in-transit-desktop.png`：物料级采购在途参考。
- `09-quality-freeze-reserved-error-desktop.png`：冻结已预留库存错误。
- `10-period-locked-error-desktop.png`：期间锁定错误。
- `11-readonly-permission-desktop.png`：只读权限。
- `12-forbidden-desktop.png`：无权限。
- `13-empty-state-desktop.png`：空态。
- `14-inventory-availability-mobile-390x844.png`：库存余额窄屏。
- `15-sales-candidate-mobile-390x844.png`：销售候选窄屏。
- `16-production-candidate-mobile-390x844.png`：生产候选窄屏。

## 阻断缺陷

- 数量口径不守恒或重复计算。
- 非合格、冻结、已占用、已预留库存被承诺、占用或消耗。
- 采购在途被当成账面库存、立即可用库存或仓库级现货可承诺量。
- 销售订单确认绕过预留仓库，或现货不足但以物料级采购在途为依据确认并预留。
- 生产工单发布直接记为占用。
- 权限、期间保护或后端鉴权可绕过。
- 失败事务产生部分写入、残留占用或负库存。
- 核心页面无法访问。
- 视觉证据缺失，或视觉问题影响关键数据识别、核心操作或权限状态判断。

## 全量验证窗口

阶段交付前统一执行后端全量测试、前端全量测试、类型检查、生产构建、数据库迁移验证、浏览器验收和视觉审计。全量验证窗口发现问题时，先用定向测试定位和修复，再回到最终全量确认。
