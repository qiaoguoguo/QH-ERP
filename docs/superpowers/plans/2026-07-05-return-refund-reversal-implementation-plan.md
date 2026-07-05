# 退货退款与业务反冲基础模块实施计划

> **给智能体执行者：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务逐项执行本计划。受本仓库固定角色规则约束，实施时只复用产品经理、UI 设计师、前端开发、后端开发、测试 5 个固定角色子代理，不创建第六类角色。步骤使用复选框（`- [ ]`）跟踪。

**目标：** 建立退货退款与业务反冲基础能力，使已过账业务可通过反向单据、库存反向流水、往来冲减、成本影响和报表净额形成可信闭环。

**架构：** 后端新增 `com.qherp.api.system.reversal` 领域和 V11 迁移，集中实现销售退货、采购退货、生产退料补料、往来冲减和统一追溯。前端新增 `apps/web/src/modules/reversal`，复用现有 API、路由权限、Element Plus 列表表单和报表追溯模式。

**技术栈：** Java 21、Spring Boot、Spring Security、JdbcTemplate、Flyway、PostgreSQL、JUnit/Testcontainers、Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、Vitest、Playwright。

---

## 固定执行规则

- 主代理只负责统筹、派发、等待结果、组织审查和验收判断。
- 任一实现任务必须通过 `/goal` 派发给固定五角色中的对应角色。
- 本项目不采用“每任务新建临时子代理”的通用模式；所有实现、审查和复审只复用固定五角色子代理。
- 每个实现任务完成后先做规格审查，再做代码质量审查。
- 规格审查和代码质量审查只能由固定五角色中的合适角色承担。
- 后端和前端实现必须遵循 TDD：先写预期失败测试，再实现，再运行测试通过。
- 已过账原单不得被直接修改或删除。
- 反向业务必须新增记录，并可追溯原单、原行、库存流水、往来冲减、成本影响和报表来源。
- V11 迁移必须包含非空约束、状态检查、数量金额检查、唯一索引、查询索引、`version` 字段和 `client_request_id` 幂等唯一约束。
- V11 迁移必须同步调整 `fin_receivable`、`fin_payable` 的 `adjusted_amount` 字段和余额约束，并扩展 `mfg_cost_record.source_document_type` 检查约束。
- 接口响应字段必须与 `docs/api/return-refund-reversal-api.md` 的 DTO 契约一致，数量和金额统一以字符串返回。
- `BUSINESS_REVERSAL` 仅为预留枚举，015 不建设泛化反冲中心或独立反冲单据。
- 不实现审批流、总账、凭证、税务、红字发票、多组织、多币种或正式成本结转。

## 文件结构

### 后端新增或修改

- 新建：`apps/api/src/main/resources/db/migration/V11__return_refund_reversal_schema.sql`
- 新建：`apps/api/src/main/java/com/qherp/api/system/reversal/ReversalAdminController.java`
- 新建：`apps/api/src/main/java/com/qherp/api/system/reversal/ReversalAdminService.java`
- 新建：`apps/api/src/main/java/com/qherp/api/system/reversal/ReversalStatus.java`
- 新建：`apps/api/src/main/java/com/qherp/api/system/reversal/ReversalSourceType.java`
- 修改：`apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- 修改：`apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryMovementType.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/reporting/ReportingAdminService.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/reversal/ReversalAdminControllerTests.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/reporting/ReportingAdminControllerTests.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`

### 前端新增或修改

- 新建：`apps/web/src/shared/api/returnRefundReversalApi.ts`
- 测试：`apps/web/src/shared/api/returnRefundReversalApi.spec.ts`
- 新建：`apps/web/src/modules/reversal/ReversalStatusTag.vue`
- 新建：`apps/web/src/modules/reversal/ReversalTracePanel.vue`
- 新建：`apps/web/src/modules/reversal/SalesReturnListView.vue`
- 新建：`apps/web/src/modules/reversal/SalesReturnFormView.vue`
- 新建：`apps/web/src/modules/reversal/SalesReturnDetailView.vue`
- 新建：`apps/web/src/modules/reversal/PurchaseReturnListView.vue`
- 新建：`apps/web/src/modules/reversal/PurchaseReturnFormView.vue`
- 新建：`apps/web/src/modules/reversal/PurchaseReturnDetailView.vue`
- 新建：`apps/web/src/modules/reversal/SettlementAdjustmentListView.vue`
- 新建：`apps/web/src/modules/reversal/SettlementAdjustmentFormView.vue`
- 新建：`apps/web/src/modules/reversal/SettlementAdjustmentDetailView.vue`
- 新建：`apps/web/src/modules/reversal/ProductionMaterialReturnListView.vue`
- 新建：`apps/web/src/modules/reversal/ProductionMaterialReturnFormView.vue`
- 新建：`apps/web/src/modules/reversal/ProductionMaterialReturnDetailView.vue`
- 新建：`apps/web/src/modules/reversal/ProductionMaterialSupplementListView.vue`
- 新建：`apps/web/src/modules/reversal/ProductionMaterialSupplementFormView.vue`
- 新建：`apps/web/src/modules/reversal/ProductionMaterialSupplementDetailView.vue`
- 测试：`apps/web/src/modules/reversal/*.spec.ts`
- 修改：`apps/web/src/router/index.ts`
- 修改：`apps/web/src/router/permissionGuard.spec.ts`
- 修改：`apps/web/src/App.vue`
- 修改：`apps/web/src/App.spec.ts`
- 修改：`apps/web/src/modules/reports/ReportViews.spec.ts`
- 修改：`apps/web/src/modules/reports/*.vue`

### 文档和验收

- 新建：`docs/tasks/015-return-refund-reversal-foundation.md`
- 新建：`docs/api/return-refund-reversal-api.md`
- 新建：`docs/testing/return-refund-reversal-test-plan.md`
- 新建：`docs/superpowers/specs/2026-07-05-return-refund-reversal-design.md`
- 新建：`docs/superpowers/plans/2026-07-05-return-refund-reversal-implementation-plan.md`
- 新建：`docs/testing/return-refund-reversal-visual-audit/notes.md`
- 修改：`docs/product/product-decisions.md`
- 修改：`docs/product/business-flow.md`
- 修改：`docs/testing/acceptance-criteria.md`
- 修改：`docs/README.md`

## Task 1: 文档基线审查与阶段确认

**角色：** 产品经理承担规格审查，UI 设计师承担设计复核，后端开发承担接口契约复核，前端开发承担前端可行性复核，测试承担测试计划复核。

- [x] **步骤 1：固定五角色阶段讨论**

使用 `/goal` 分别派发产品经理、UI 设计师、前端开发、后端开发和测试角色，确认阶段目标、范围边界、关键风险、验收标准和落地顺序。

- [x] **步骤 2：验证文档基线**

运行：

```powershell
$terms = @('TO' + 'DO', 'TB' + 'D', '待' + '定', '未' + '定', '占' + '位') -join '|'
rg -n $terms docs/tasks/015-return-refund-reversal-foundation.md docs/api/return-refund-reversal-api.md docs/testing/return-refund-reversal-test-plan.md docs/superpowers/specs/2026-07-05-return-refund-reversal-design.md docs/superpowers/plans/2026-07-05-return-refund-reversal-implementation-plan.md
git diff --check
```

结果：无未完成标记；旧退款来源枚举、旧状态名和旧 PascalCase 来源路由名扫描无输出；`git diff --check` 退出码为 `0`。

## Task 2: 后端基础模型、迁移、权限和错误码

**角色：** 后端开发承担实现，产品经理承担规格审查，测试承担代码质量审查。

- [x] **步骤 1：编写预期失败的权限和迁移测试**

覆盖 V11 表存在、反向权限种子、反向路径鉴权、库存反向类型和错误码。

- [x] **步骤 2：实现 V11 迁移、权限、错误码和 Controller 骨架**

新增反向业务表、状态枚举、权限种子、错误码、鉴权映射和只返回空数据的接口骨架。迁移必须包含 `biz_reversal_link`、销售退货、采购退货、生产退料、生产补料和往来冲减表的字段、非空约束、状态检查、数量金额检查、唯一索引、查询索引、`version` 字段和 `client_request_id` 幂等索引；同时为 `fin_receivable`、`fin_payable` 增加 `adjusted_amount` 并重建余额约束，为 `mfg_cost_record` 扩展 `PRODUCTION_MATERIAL_RETURN`、`PRODUCTION_MATERIAL_SUPPLEMENT` 来源约束。

- [x] **步骤 3：运行定向测试**

运行后端定向测试，预期新增测试通过。

## Task 3: 销售退货与应收冲减

**角色：** 后端开发承担后端实现，前端开发承担前端实现，产品经理承担规格审查，测试承担代码质量审查。

- [x] **步骤 1：编写销售退货后端失败测试**

覆盖创建草稿、过账、部分退、全退、超退、重复过账、应收冲减、库存入库和追溯。

- [x] **步骤 2：实现销售退货后端逻辑**

过账时锁定销售出库行，生成 `SALES_RETURN_IN` 库存流水、应收冲减和 `biz_reversal_link`。

- [x] **步骤 3：编写销售退货前端失败测试**

覆盖 API、菜单、路由、列表、表单、详情、来源受限和按钮权限。

- [x] **步骤 4：实现销售退货前端页面**

新增销售退货列表、表单、详情和追溯。

## Task 4: 采购退货与应付冲减

**角色：** 后端开发承担后端实现，前端开发承担前端实现，产品经理承担规格审查，测试承担代码质量审查。

- [x] **步骤 1：编写采购退货后端失败测试**

覆盖创建草稿、过账、部分退、全退、超退、库存不足、应付冲减和追溯。

- [x] **步骤 2：实现采购退货后端逻辑**

过账时锁定采购入库行，校验库存，生成 `PURCHASE_RETURN_OUT` 库存流水、应付冲减和追溯链接。

- [x] **步骤 3：编写采购退货前端失败测试**

覆盖 API、菜单、路由、列表、表单、详情、来源受限和按钮权限。

- [x] **步骤 4：实现采购退货前端页面**

新增采购退货列表、表单、详情和追溯。

## Task 5: 生产退料补料与成本业务影响

**角色：** 后端开发承担后端实现，前端开发承担前端实现，UI 设计师承担规格审查，测试承担代码质量审查。

- [x] **步骤 1：编写生产退料补料后端失败测试**

覆盖退料入库、补料出库、超退、库存不足、工单状态非法、成本净额影响和追溯。

- [x] **步骤 2：实现生产退料补料后端逻辑**

过账时生成生产反向或补充库存流水，并生成成本业务影响记录。

- [x] **步骤 3：编写生产退料补料前端失败测试**

覆盖列表、表单、详情、状态、来源追溯和异常提示。

- [x] **步骤 4：实现生产退料补料前端页面**

新增生产退料和补料页面。

## Task 6: 往来冲减、退款记录和统一追溯

**角色：** 后端开发承担后端实现，前端开发承担前端实现，产品经理承担规格审查，测试承担代码质量审查。

- [x] **步骤 1：编写往来冲减失败测试**

覆盖应收冲减、应付冲减、收款后退款记录、付款后冲减记录、超额拦截和状态变化。

- [x] **步骤 2：实现往来冲减后端逻辑**

新增 `fin_settlement_adjustment` 过账逻辑，维护业务余额和追溯。

- [x] **步骤 3：实现统一追溯接口**

原单详情和反向单详情都能查询反向影响，来源受限时脱敏。

- [x] **步骤 4：实现往来冲减前端页面**

新增列表、表单、详情和追溯。

## Task 7: 经营报表净额口径适配

**角色：** 后端开发承担后端实现，前端开发承担前端实现，UI 设计师承担规格审查，测试承担代码质量审查。

- [x] **步骤 1：编写报表净额后端失败测试**

覆盖销售、采购、库存、生产、成本和往来报表的原发生、反向发生、净额和追溯。

- [x] **步骤 2：实现报表净额查询**

调整固定 SQL 聚合，纳入 V11 反向业务来源。

- [x] **步骤 3：编写报表净额前端失败测试**

覆盖净额字段、口径说明、反向来源追溯和来源受限。

- [x] **步骤 4：实现报表页面适配**

在既有报表页补充原发生、反向发生、净额和追溯入口。

## Task 8: 自动化回归、浏览器验收和视觉分析

**角色：** 测试承担执行，UI 设计师承担视觉复审，主代理承担证据复核。

- [x] **步骤 1：执行后端全量测试**

运行 Maven 全量测试，预期 0 失败、0 错误。

- [x] **步骤 2：执行前端全量测试、类型检查和构建**

运行 `npm test`、`npm run typecheck`、`npm run build`，预期全部通过。

- [x] **步骤 3：启动本地服务并执行浏览器验收**

覆盖管理员主路径、只读权限、模块权限、来源受限、无权限和未登录路径。

- [x] **步骤 4：采集视觉证据并复审**

保存桌面和窄屏截图，记录视觉分析结论。

- [x] **步骤 5：最终质量门**

运行 `git diff --check`、后端健康检查、前端访问检查、缺陷状态检查和工作区状态检查。

## Task 9: 阶段交付判断

**角色：** 产品经理、UI 设计师、前端开发、后端开发、测试分别复审，主代理最终判断。

- [x] **步骤 1：固定五角色最终复审**

产品经理复审规格，UI 设计师复审视觉，前端开发提交实现交接，后端开发提交实现交接，测试复核质量门和验收证据。

- [x] **步骤 2：主代理完成交付判断**

确认无阻断缺陷、无未处理严重缺陷、验收证据完整，再准备合入主分支和本地验收地址。
