# 经营报表基础模块实施计划

> **给智能体执行者：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务逐项执行本计划。受本仓库固定角色规则约束，实施时只复用产品经理、UI 设计师、前端开发、后端开发、测试 5 个固定角色子代理，不创建第六类角色。步骤使用复选框（`- [ ]`）跟踪。

**目标：** 建立固定口径经营报表基础能力，使 QH ERP 能查看采购、销售、库存、生产、成本和财务往来的经营汇总、来源追溯和异常清单。

**架构：** 后端新增 `com.qherp.api.system.reporting` 只读报表领域，使用固定 SQL 聚合查询已有业务表，不写业务数据。前端新增 `apps/web/src/modules/reports` 模块，复用现有路由权限、API 封装、Element Plus 表格表单和视觉验收模式。

**技术栈：** Java 21、Spring Boot、Spring Security、JdbcTemplate、Flyway、PostgreSQL、JUnit/Testcontainers、Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、Vitest、Playwright。

---

## 固定执行规则

- 主代理只负责统筹、派发、等待结果、组织审查和验收判断。
- 任一实现任务必须通过 `/goal` 派发给固定五角色中的对应角色。
- 每个实现任务完成后先做规格审查，再做代码质量审查。
- 规格审查和代码质量审查只能由固定五角色中的合适角色承担。
- 后端和前端实现必须遵循 TDD：先写预期失败测试，再实现，再运行测试通过。
- 本阶段最终交付必须覆盖经营概览、销售、采购、库存、生产、成本、往来收付和异常清单。
- 不实现可配置 BI、总账、凭证、结账、正式财务报表、退货退款、反冲、多组织、多公司、多账套或多租户。
- 报表接口只读，不得生成、修改或删除业务单据。

## 文件结构

### 后端新增或修改

- 新建：`apps/api/src/main/java/com/qherp/api/system/reporting/ReportingAdminController.java`
- 新建：`apps/api/src/main/java/com/qherp/api/system/reporting/ReportingAdminService.java`
- 新建：`apps/api/src/main/java/com/qherp/api/system/reporting/ReportTraceSourceType.java`
- 修改：`apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- 修改：`apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/reporting/ReportingAdminControllerTests.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`

### 前端新增或修改

- 新建：`apps/web/src/shared/api/businessReportingApi.ts`
- 测试：`apps/web/src/shared/api/businessReportingApi.spec.ts`
- 新建：`apps/web/src/modules/reports/reportPageHelpers.ts`
- 新建：`apps/web/src/modules/reports/ReportMetricStrip.vue`
- 新建：`apps/web/src/modules/reports/ReportFilterBar.vue`
- 新建：`apps/web/src/modules/reports/ReportTracePanel.vue`
- 新建：`apps/web/src/modules/reports/ReportOverviewView.vue`
- 新建：`apps/web/src/modules/reports/SalesReportView.vue`
- 新建：`apps/web/src/modules/reports/ProcurementReportView.vue`
- 新建：`apps/web/src/modules/reports/InventoryReportView.vue`
- 新建：`apps/web/src/modules/reports/ProductionReportView.vue`
- 新建：`apps/web/src/modules/reports/CostReportView.vue`
- 新建：`apps/web/src/modules/reports/SettlementReportView.vue`
- 新建：`apps/web/src/modules/reports/ExceptionReportView.vue`
- 测试：`apps/web/src/modules/reports/*.spec.ts`
- 修改：`apps/web/src/router/index.ts`
- 修改：`apps/web/src/router/permissionGuard.spec.ts`
- 修改：`apps/web/src/App.vue`
- 修改：`apps/web/src/App.spec.ts`

### 文档和验收新增或修改

- 新建：`docs/tasks/014-business-reporting-foundation.md`
- 新建：`docs/api/business-reporting-api.md`
- 新建：`docs/testing/business-reporting-test-plan.md`
- 新建：`docs/superpowers/specs/2026-07-04-business-reporting-design.md`
- 新建：`docs/superpowers/plans/2026-07-04-business-reporting-implementation-plan.md`
- 新建：`docs/testing/business-reporting-visual-audit/notes.md`
- 修改：`docs/product/product-decisions.md`
- 修改：`docs/product/business-flow.md`
- 修改：`docs/testing/acceptance-criteria.md`
- 修改：`docs/README.md`
- 新建或更新：`docs/testing/business-reporting-defects.md`，仅在发现缺陷时使用。

## Task 1: 文档基线审查与阶段确认

**角色：** 产品经理承担规格审查，UI 设计师承担设计复核，后端开发承担接口契约复核，前端开发承担前端可行性复核，测试承担测试计划复核。

**文件：**
- 审查：`docs/tasks/014-business-reporting-foundation.md`
- 审查：`docs/superpowers/specs/2026-07-04-business-reporting-design.md`
- 审查：`docs/api/business-reporting-api.md`
- 审查：`docs/testing/business-reporting-test-plan.md`

- [x] **步骤 1：主代理派发固定五角色阶段讨论**

使用 `/goal` 分别派发产品经理、UI 设计师、前端开发、后端开发和测试角色，确认阶段目标、范围边界、关键风险、验收标准和落地顺序。

- [x] **步骤 2：修正文档口径冲突**

统一 `report:*` 权限、`/reports` 路由、`/api/admin/reports` 接口、固定报表清单、视觉目录和阶段排除项。

- [x] **步骤 3：验证文档基线**

运行：

```powershell
$terms = @('TO' + 'DO', 'TB' + 'D', '待' + '定', '未' + '定', '占' + '位') -join '|'
rg -n $terms docs/tasks/014-business-reporting-foundation.md docs/api/business-reporting-api.md docs/testing/business-reporting-test-plan.md docs/superpowers/specs/2026-07-04-business-reporting-design.md docs/superpowers/plans/2026-07-04-business-reporting-implementation-plan.md
git diff --check
```

预期：无未完成标记；`git diff --check` 退出码为 `0`。

## Task 2: 后端报表权限、错误码和接口骨架

**角色：** 后端开发承担实现，产品经理承担规格审查，测试承担代码质量审查。

**文件：**
- 新建：`apps/api/src/main/java/com/qherp/api/system/reporting/ReportingAdminController.java`
- 新建：`apps/api/src/main/java/com/qherp/api/system/reporting/ReportingAdminService.java`
- 新建：`apps/api/src/main/java/com/qherp/api/system/reporting/ReportTraceSourceType.java`
- 修改：`apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- 修改：`apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- 修改：`apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/reporting/ReportingAdminControllerTests.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`
- 测试：`apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`

- [x] **步骤 1：编写预期失败的后端权限和骨架测试**

覆盖报表权限初始化、报表路径鉴权、无权限访问、日期范围非法、空数据零值、接口只读响应骨架。

- [x] **步骤 2：实现权限、错误码和 Controller 骨架**

新增 `REPORT_*` 错误码、`report:*` 权限种子、`/api/admin/reports/**` 鉴权映射和 `ReportingAdminController` 只读接口。

- [x] **步骤 3：实现空数据安全响应**

在 `ReportingAdminService` 中先实现无数据时的稳定零值响应，保证前端可联调空态。

- [x] **步骤 4：运行定向测试**

运行后端定向测试，预期新增测试通过。

## Task 3: 后端固定报表聚合与追溯

**角色：** 后端开发承担实现，产品经理承担规格审查，测试承担代码质量审查。

**文件：**
- 修改：`apps/api/src/main/java/com/qherp/api/system/reporting/ReportingAdminService.java`
- 修改：`apps/api/src/test/java/com/qherp/api/system/reporting/ReportingAdminControllerTests.java`

- [x] **步骤 1：编写销售、采购、往来聚合失败测试**

构造已过账销售出库、采购入库、应收、收款、应付和付款，断言列表接口返回 `summary + items`，`summary` 统计完整筛选结果集，汇总金额、余额和追溯明细合计一致。

- [x] **步骤 2：实现销售、采购、往来固定 SQL 聚合**

使用 `JdbcTemplate` 或现有查询模式实现固定 SQL 聚合，不通过 JPA 实体全量加载后循环统计。

- [x] **步骤 3：编写库存、生产、成本聚合失败测试**

构造库存流水、生产工单、领料、报工、完工入库和成本记录，断言列表接口返回 `summary + items`，库存数量、生产进度、成本业务金额和追溯明细合计一致。

- [x] **步骤 4：实现库存、生产、成本固定 SQL 聚合**

按业务日期和状态过滤，返回字符串金额、数量和 `formalAccounting=false`。

- [x] **步骤 5：实现追溯分页接口**

为销售、采购、库存、生产、成本、往来和经营异常清单实现追溯分页接口，返回来源类型、来源编号、业务日期、状态、数量、金额和可跳转资源。来源详情权限不足时必须返回脱敏行，不得泄露来源主键、单号、行号、数量、金额、业务日期、状态或路由参数。

- [x] **步骤 6：运行后端报表定向测试**

运行 `ReportingAdminControllerTests`，预期通过。

## Task 4: 前端报表 API、路由和菜单

**角色：** 前端开发承担实现，产品经理承担规格审查，测试承担代码质量审查。

**文件：**
- 新建：`apps/web/src/shared/api/businessReportingApi.ts`
- 测试：`apps/web/src/shared/api/businessReportingApi.spec.ts`
- 新建：`apps/web/src/modules/reports/reportPageHelpers.ts`
- 修改：`apps/web/src/router/index.ts`
- 修改：`apps/web/src/router/permissionGuard.spec.ts`
- 修改：`apps/web/src/App.vue`
- 修改：`apps/web/src/App.spec.ts`

- [x] **步骤 1：编写前端 API 和路由失败测试**

覆盖 API 路径、查询参数、`/reports` 权限重定向、菜单显示和无权限状态。

- [x] **步骤 2：实现报表 API 封装**

新增经营概览、固定报表、异常清单和追溯接口方法。固定报表类型必须包含 `summary + items`，金额和数量按字符串类型处理。

- [x] **步骤 3：实现路由、菜单和 helper**

新增 `/reports` 路由组、报表菜单入口、首个报表权限重定向和权限 helper。

- [x] **步骤 4：运行前端定向测试**

运行 API、路由和菜单相关测试，预期通过。

## Task 5: 前端经营概览和固定报表页面

**角色：** 前端开发承担实现，UI 设计师承担规格审查，测试承担代码质量审查。

**文件：**
- 新建：`apps/web/src/modules/reports/ReportMetricStrip.vue`
- 新建：`apps/web/src/modules/reports/ReportFilterBar.vue`
- 新建：`apps/web/src/modules/reports/ReportTracePanel.vue`
- 新建：`apps/web/src/modules/reports/ReportOverviewView.vue`
- 新建：`apps/web/src/modules/reports/SalesReportView.vue`
- 新建：`apps/web/src/modules/reports/ProcurementReportView.vue`
- 新建：`apps/web/src/modules/reports/InventoryReportView.vue`
- 新建：`apps/web/src/modules/reports/ProductionReportView.vue`
- 新建：`apps/web/src/modules/reports/CostReportView.vue`
- 新建：`apps/web/src/modules/reports/SettlementReportView.vue`
- 新建：`apps/web/src/modules/reports/ExceptionReportView.vue`
- 测试：`apps/web/src/modules/reports/*.spec.ts`

- [x] **步骤 1：编写页面失败测试**

覆盖概览指标、固定报表筛选、汇总指标、空态、错误态、口径说明、来源追溯、经营异常追溯和 `canViewResource=false` 脱敏状态。

- [x] **步骤 2：实现共用报表组件**

实现指标摘要、筛选栏、追溯面板和统一空态异常态。

- [x] **步骤 3：实现经营概览和七类固定报表页**

接入 API、筛选、分页、汇总指标、明细表格、来源追溯、经营异常追溯和权限状态。

- [x] **步骤 4：运行前端报表页面定向测试**

运行报表模块测试，预期通过。

## Task 6: 自动化回归、浏览器验收和视觉分析

**角色：** 测试承担执行，UI 设计师承担视觉复审，主代理承担证据复核。

**文件：**
- 新建：`docs/testing/business-reporting-visual-audit/notes.md`
- 新建：`docs/testing/business-reporting-visual-audit/*.png`
- 更新：`docs/testing/business-reporting-test-plan.md`

- [x] **步骤 1：执行后端全量测试**

运行 Docker Maven 全量测试，预期 0 失败、0 错误。

- [x] **步骤 2：执行前端全量测试、类型检查和构建**

运行 `npm test`、`npm run typecheck`、`npm run build`，预期全部通过。

- [x] **步骤 3：启动本地服务并执行浏览器验收**

完成管理员主路径、部分报表权限、来源受限、无权限和未登录路径。管理员路径必须分别打开销售、采购、库存、生产、成本、往来和经营异常追溯并核对来源。

- [x] **步骤 4：采集视觉证据并复审**

保存桌面和窄屏截图，记录视觉分析结论。UI 设计师复审通过后才能进入最终质量门。

- [x] **步骤 5：最终质量门**

运行 `git diff --check`、后端健康检查、前端访问检查、缺陷状态检查和工作区状态检查。

## Task 7: 阶段交付判断

**角色：** 产品经理、UI 设计师、前端开发、后端开发、测试分别复审，主代理最终判断。

- [x] **步骤 1：固定五角色最终复审**

产品经理复审规格，UI 设计师复审视觉，前端开发提交实现交接，后端开发提交实现交接，测试复核质量门和验收证据。

- [x] **步骤 2：主代理完成交付判断**

确认无阻断缺陷、无未处理严重缺陷、验收证据完整，再准备合入主分支和本地验收地址。
