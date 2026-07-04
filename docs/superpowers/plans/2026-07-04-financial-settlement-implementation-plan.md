# 财务往来基础模块实施计划

> **给智能体执行者：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务逐项执行本计划。受本仓库固定角色规则约束，实施时只复用产品经理、UI 设计师、前端开发、后端开发、测试 5 个固定角色子代理，不创建第六类角色。步骤使用复选框（`- [ ]`）跟踪。

**目标：** 建立应收、收款、应付、付款、余额核销和来源追溯闭环，使 QH ERP 能完成采购入库和销售出库之后的基础财务往来管理。

**架构：** 财务往来后端作为 `com.qherp.api.system.finance` 独立领域实现，前端作为 `apps/web/src/modules/finance` 独立模块实现。应收来源读取已过账销售出库和销售订单金额快照，应付来源读取已过账采购入库和采购订单金额快照，收付款过账在财务域事务内更新核销明细、累计金额、未结余额和状态。

**技术栈：** Java 21、Spring Boot、Spring Security、JdbcTemplate、Flyway、PostgreSQL、JUnit/Testcontainers、Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、Vitest、Playwright。

---

## 固定执行规则

- 主代理只负责统筹、派发、等待结果、组织审查和验收判断。
- 任一实现任务必须通过 `/goal` 派发给固定五角色中的对应角色。
- 每个实现任务完成后先做规格审查，再做代码质量审查。
- 规格审查和代码质量审查只能由固定五角色中的合适角色承担，不创建规格审查、代码质量审查等新角色。
- 后端和前端实现必须遵循 TDD：先写预期失败测试，再实现，再运行测试通过。
- 本阶段最终交付必须覆盖应收、收款、应付、付款四条业务闭环；实施顺序可以先应收收款、后应付付款，但不得把阶段目标降级为只交付应收收款。
- 不实现总账、凭证、会计科目、会计期间、结账、税务申报、完整发票生命周期、银行对账、正式收入确认、正式成本结转、BI 报表、多币种、多组织、多公司、多账套、多租户。
- 应收来源固定为已过账销售出库；应付来源固定为已过账采购入库。
- 收付款过账必须保证金额事务一致性，阻断缺陷必须修复并复验后才能进入阶段验收。

## 文件结构

### 后端新增或修改

- 新建： `apps/api/src/main/resources/db/migration/V10__financial_settlement_schema.sql`
- 新建： `apps/api/src/main/java/com/qherp/api/system/finance/FinanceAdminController.java`
- 新建： `apps/api/src/main/java/com/qherp/api/system/finance/FinanceAdminService.java`
- 新建： `apps/api/src/main/java/com/qherp/api/system/finance/ReceivableStatus.java`
- 新建： `apps/api/src/main/java/com/qherp/api/system/finance/ReceiptStatus.java`
- 新建： `apps/api/src/main/java/com/qherp/api/system/finance/PayableStatus.java`
- 新建： `apps/api/src/main/java/com/qherp/api/system/finance/PaymentStatus.java`
- 修改： `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- 修改： `apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- 修改： `apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`
- 测试： `apps/api/src/test/java/com/qherp/api/system/finance/FinanceAdminControllerTests.java`
- 测试： `apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`
- 测试： `apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`

### 前端新增或修改

- 新建： `apps/web/src/shared/api/financeApi.ts`
- 测试： `apps/web/src/shared/api/financeApi.spec.ts`
- 新建： `apps/web/src/modules/finance/financePageHelpers.ts`
- 新建： `apps/web/src/modules/finance/ReceivableStatusTag.vue`
- 新建： `apps/web/src/modules/finance/ReceiptStatusTag.vue`
- 新建： `apps/web/src/modules/finance/PayableStatusTag.vue`
- 新建： `apps/web/src/modules/finance/PaymentStatusTag.vue`
- 新建： `apps/web/src/modules/finance/FinanceSourceTracePanel.vue`
- 新建： `apps/web/src/modules/finance/ReceivableListView.vue`
- 新建： `apps/web/src/modules/finance/ReceivableFormView.vue`
- 新建： `apps/web/src/modules/finance/ReceivableDetailView.vue`
- 新建： `apps/web/src/modules/finance/ReceiptListView.vue`
- 新建： `apps/web/src/modules/finance/ReceiptFormView.vue`
- 新建： `apps/web/src/modules/finance/ReceiptDetailView.vue`
- 新建： `apps/web/src/modules/finance/PayableListView.vue`
- 新建： `apps/web/src/modules/finance/PayableFormView.vue`
- 新建： `apps/web/src/modules/finance/PayableDetailView.vue`
- 新建： `apps/web/src/modules/finance/PaymentListView.vue`
- 新建： `apps/web/src/modules/finance/PaymentFormView.vue`
- 新建： `apps/web/src/modules/finance/PaymentDetailView.vue`
- 测试： `apps/web/src/modules/finance/*.spec.ts`
- 修改： `apps/web/src/router/index.ts`
- 修改： `apps/web/src/router/permissionGuard.spec.ts`
- 修改： `apps/web/src/App.vue`
- 修改： `apps/web/src/App.spec.ts`

### 文档和验收新增或修改

- 新建： `docs/tasks/013-financial-settlement-foundation.md`
- 新建： `docs/api/financial-settlement-api.md`
- 新建： `docs/testing/financial-settlement-test-plan.md`
- 新建： `docs/superpowers/specs/2026-07-04-financial-settlement-design.md`
- 新建： `docs/superpowers/plans/2026-07-04-financial-settlement-implementation-plan.md`
- 新建： `docs/testing/financial-settlement-visual-audit/notes.md`
- 新建： `docs/testing/financial-settlement-visual-audit/*.png`
- 修改： `docs/product/product-decisions.md`
- 修改： `docs/product/business-flow.md`
- 修改： `docs/testing/acceptance-criteria.md`
- 修改： `docs/README.md`
- 新建或更新： `docs/testing/financial-settlement-defects.md`，仅在实现或审查中发现缺陷时使用

## Task 1: 文档基线审查与阶段确认

**角色：** 产品经理承担规格审查，测试承担验收标准复核，UI 设计师承担设计规格复核，后端开发承担接口契约复核，前端开发承担前端可行性复核。

**文件：**
- 审查： `docs/tasks/013-financial-settlement-foundation.md`
- 审查： `docs/superpowers/specs/2026-07-04-financial-settlement-design.md`
- 审查： `docs/api/financial-settlement-api.md`
- 审查： `docs/testing/financial-settlement-test-plan.md`

- [x] **步骤 1：主代理派发固定五角色阶段讨论**

使用 `/goal` 分别派发产品经理、UI 设计师、前端开发、后端开发和测试角色，确认阶段目标、范围边界、关键风险、验收标准和落地顺序。

- [x] **步骤 2：修正文档口径冲突**

统一 `finance:*` 权限、`/finance` 路由、`/api/admin/finance` 接口、应收/收款/应付/付款状态、来源类型、视觉目录和阶段排除项。

- [x] **步骤 3：验证文档基线**

运行：

```powershell
$terms = @('TO' + 'DO', 'TB' + 'D', '待' + '定', '未' + '定', '占' + '位', '总账', '凭证', '会计科目', '税务申报', 'BI 报表') -join '|'
rg -n $terms docs/tasks/013-financial-settlement-foundation.md docs/api/financial-settlement-api.md docs/testing/financial-settlement-test-plan.md docs/superpowers/specs/2026-07-04-financial-settlement-design.md docs/product/product-decisions.md docs/product/business-flow.md docs/testing/acceptance-criteria.md docs/README.md
git diff --check
```

预期：排除范围内命中的正式财务能力只出现在“不包含、排除、不得出现、阻断”语境；无未完成标记；`git diff --check` 退出码为 `0`。

## Task 2: 后端财务往来基础迁移、错误码和权限

**角色：** 后端开发承担实现，测试承担规格审查，后端开发承担代码质量审查。

**文件：**
- 新建： `apps/api/src/main/resources/db/migration/V10__financial_settlement_schema.sql`
- 新建： `apps/api/src/main/java/com/qherp/api/system/finance/ReceivableStatus.java`
- 新建： `apps/api/src/main/java/com/qherp/api/system/finance/ReceiptStatus.java`
- 新建： `apps/api/src/main/java/com/qherp/api/system/finance/PayableStatus.java`
- 新建： `apps/api/src/main/java/com/qherp/api/system/finance/PaymentStatus.java`
- 修改： `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- 修改： `apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- 修改： `apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`
- 测试： `apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`
- 测试： `apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`

- [x] **步骤 1：编写预期失败的基础后端测试**

新增或更新测试，覆盖以下预期：

- 管理员权限初始化包含 `finance` 和全部 `finance:*` 权限。
- 受保护路径覆盖 `/api/admin/finance/receivables/**`、`/api/admin/finance/receipts/**`、`/api/admin/finance/payables/**`、`/api/admin/finance/payments/**`、`/api/admin/finance/receivable-sources`、`/api/admin/finance/payable-sources`。
- `PermissionAuthorizationManager` 将财务路径映射到对应权限，未授权账号返回 `AUTH_FORBIDDEN`。
- 财务状态枚举和 `FINANCE_*` 错误码存在。

运行：

```powershell
docker run --rm `
  -e TESTCONTAINERS_RYUK_DISABLED=true `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal `
  -v "${PWD}:/workspace" `
  -v /var/run/docker.sock:/var/run/docker.sock `
  -v qherp-maven-repo:/root/.m2 `
  -w /workspace/apps/api `
  maven:3.9-eclipse-temurin-21 `
  mvn -q -Dtest=AccountPermissionInitializerTests,PermissionAuthorizationTests test
```

预期：新增财务断言失败，原因是财务权限、路径或错误码尚不存在。

- [x] **步骤 2：新增财务迁移和状态枚举**

新增 `V10__financial_settlement_schema.sql`，包含 `fin_receivable`、`fin_receivable_source`、`fin_receipt`、`fin_receipt_allocation`、`fin_payable`、`fin_payable_source`、`fin_payment`、`fin_payment_allocation`。

迁移必须包含以下约束和索引：

- 应收、收款、应付、付款单号唯一。
- 状态字段 check 约束限制为接口契约枚举。
- 金额字段 `numeric(18,2)`，总金额、已结金额、未结余额非负。
- 应收余额恒等式 check：`total_amount = received_amount + unreceived_amount`。
- 应付余额恒等式 check：`total_amount = paid_amount + unpaid_amount`。
- 来源明细唯一约束防止重复生成应收或应付。
- 核销明细金额必须大于 0。
- 本阶段一笔收款只核销一笔应收，`fin_receipt_allocation(receipt_id)` 必须唯一，且过账时收款金额等于唯一核销金额。
- 本阶段一笔付款只核销一笔应付，`fin_payment_allocation(payment_id)` 必须唯一，且过账时付款金额等于唯一核销金额。
- 常用索引覆盖客户、供应商、状态日期、到期日期、来源单号和核销关联。

- [x] **步骤 3：新增错误码和权限种子**

在 `ApiErrorCode` 增加 `FINANCE_*` 错误码，至少包含接口契约中的台账不存在、收付款不存在、来源不存在、来源状态非法、来源重复、金额非法、超额核销、状态不允许、已过账不可编辑、并发冲突。

在 `AccountPermissionInitializer` 增加财务菜单和操作权限。

在 `PermissionAuthorizationManager` 增加财务路径映射，确保 broad GET 规则不会吞掉写操作路径。候选来源接口映射为：

- `GET /api/admin/finance/receivable-sources` -> `finance:receivable:create`
- `GET /api/admin/finance/payable-sources` -> `finance:payable:create`

- [x] **步骤 4：验证定向基础后端测试**

运行步骤 1 中的 Docker Maven 定向测试命令。

预期：定向测试通过。

## Task 3: 后端应收和收款接口

**角色：** 后端开发承担实现，产品经理承担规格审查，测试承担代码质量审查。

**文件：**
- 新建： `apps/api/src/main/java/com/qherp/api/system/finance/FinanceAdminController.java`
- 新建： `apps/api/src/main/java/com/qherp/api/system/finance/FinanceAdminService.java`
- 测试： `apps/api/src/test/java/com/qherp/api/system/finance/FinanceAdminControllerTests.java`

- [x] **步骤 1：编写预期失败的应收收款集成测试**

覆盖管理员或财务角色查询应收候选来源，基于已过账销售出库生成应收、更新草稿、确认、取消、关闭、创建收款、更新收款、过账部分收款、过账全额收款、来源追溯、权限、审计和异常路径。候选来源测试必须断言只返回已过账销售出库，`settlementGenerated=false` 时过滤任一明细已生成应收的来源，并覆盖候选来源接口权限。

运行：

```powershell
docker run --rm `
  -e TESTCONTAINERS_RYUK_DISABLED=true `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal `
  -v "${PWD}:/workspace" `
  -v /var/run/docker.sock:/var/run/docker.sock `
  -v qherp-maven-repo:/root/.m2 `
  -w /workspace/apps/api `
  maven:3.9-eclipse-temurin-21 `
  mvn -q -Dtest=FinanceAdminControllerTests test
```

预期：失败，原因是财务数据表和应收收款接口尚不存在。

- [x] **步骤 2：实现应收和收款接口**

`FinanceAdminController` 路径固定为 `/api/admin/finance`，应收和收款接口固定为：

- `GET /receivables`
- `GET /receivable-sources`
- `GET /receivables/{id}`
- `POST /receivables`
- `PUT /receivables/{id}`
- `PUT /receivables/{id}/confirm`
- `PUT /receivables/{id}/cancel`
- `PUT /receivables/{id}/close`
- `GET /receipts`
- `POST /receivables/{id}/receipts`
- `GET /receipts/{id}`
- `PUT /receipts/{id}`
- `PUT /receipts/{id}/post`
- `PUT /receipts/{id}/cancel`

- [x] **步骤 3：实现应收和收款服务校验**

必须保证：

- 来源销售出库已过账。
- 候选来源接口只返回已过账销售出库。
- `settlementGenerated=false` 时，候选来源接口过滤任一明细已生成应收的销售出库。
- 来源明细唯一防重。
- 同一来源销售出库任一明细已生成应收时，整单生成必须失败并回滚。
- 应收金额由后端计算。
- 收款过账事务内锁定应收并校验余额。
- 收款金额必须等于唯一核销金额。
- 超额、零金额、负金额、超精度、已收清、已关闭、已过账编辑等异常受控失败。
- 失败不得产生部分核销、错误余额或错误状态。

- [x] **步骤 4：验证应收收款定向测试**

运行步骤 1 中的 Docker Maven 命令。

预期：定向测试通过。

## Task 4: 后端应付和付款接口

**角色：** 后端开发承担实现，产品经理承担规格审查，测试承担代码质量审查。

**文件：**
- 更新： `apps/api/src/main/java/com/qherp/api/system/finance/FinanceAdminController.java`
- 更新： `apps/api/src/main/java/com/qherp/api/system/finance/FinanceAdminService.java`
- 更新： `apps/api/src/test/java/com/qherp/api/system/finance/FinanceAdminControllerTests.java`

- [x] **步骤 1：编写预期失败的应付付款集成测试**

覆盖管理员或财务角色查询应付候选来源，基于已过账采购入库生成应付、更新草稿、确认、取消、关闭、创建付款、更新付款、过账部分付款、过账全额付款、来源追溯、权限、审计和异常路径。候选来源测试必须断言只返回已过账采购入库，`settlementGenerated=false` 时过滤任一明细已生成应付的来源，并覆盖候选来源接口权限。

- [x] **步骤 2：实现应付和付款接口**

接口固定为：

- `GET /payables`
- `GET /payable-sources`
- `GET /payables/{id}`
- `POST /payables`
- `PUT /payables/{id}`
- `PUT /payables/{id}/confirm`
- `PUT /payables/{id}/cancel`
- `PUT /payables/{id}/close`
- `GET /payments`
- `POST /payables/{id}/payments`
- `GET /payments/{id}`
- `PUT /payments/{id}`
- `PUT /payments/{id}/post`
- `PUT /payments/{id}/cancel`

- [x] **步骤 3：实现应付和付款服务校验**

与应收收款对称，来源固定为已过账采购入库；候选来源接口只返回已过账采购入库；`settlementGenerated=false` 时，候选来源接口过滤任一明细已生成应付的采购入库；同一来源采购入库任一明细已生成应付时，整单生成必须失败并回滚；付款金额必须等于唯一核销金额；付款过账必须保证应付余额、状态和核销明细事务一致。

- [x] **步骤 4：验证财务后端定向测试**

运行 Task 3 的 Docker Maven 命令。

预期：财务后端定向测试通过。

## Task 5: 后端全量回归

**角色：** 测试承担执行，后端开发承担修复，测试承担代码质量复审。

- [x] **步骤 1：运行后端全量测试**

运行：

```powershell
docker run --rm `
  -e TESTCONTAINERS_RYUK_DISABLED=true `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal `
  -v "${PWD}:/workspace" `
  -v /var/run/docker.sock:/var/run/docker.sock `
  -v qherp-maven-repo:/root/.m2 `
  -w /workspace/apps/api `
  maven:3.9-eclipse-temurin-21 `
  mvn -q test
```

预期：后端全量测试通过。

- [x] **步骤 2：检查 Testcontainers 残留**

运行：

```powershell
docker ps -a --filter "label=org.testcontainers" --format "{{.ID}} {{.Image}} {{.Status}} {{.Names}}"
```

预期：没有本轮测试残留容器。

## Task 6: 前端财务 API、路由、菜单和状态基础

**角色：** 前端开发承担实现，产品经理承担规格审查，测试承担代码质量审查。

**文件：**
- 新建： `apps/web/src/shared/api/financeApi.ts`
- 测试： `apps/web/src/shared/api/financeApi.spec.ts`
- 新建： `apps/web/src/modules/finance/financePageHelpers.ts`
- 新建： `apps/web/src/modules/finance/*StatusTag.vue`
- 修改： `apps/web/src/router/index.ts`
- 修改： `apps/web/src/router/permissionGuard.spec.ts`
- 修改： `apps/web/src/App.vue`
- 修改： `apps/web/src/App.spec.ts`

- [x] **步骤 1：编写预期失败的 API、路由和菜单测试**

覆盖 `financeApi` 的应收、收款、应付、付款查询和写操作，候选来源查询、CSRF、错误信封、状态标签、路由权限和财务菜单显示。

`permissionGuard.spec.ts` 必须覆盖 `/finance` 根路由动态重定向：有应收查看权限时进入 `/finance/receivables`，仅有应付查看权限时进入 `/finance/payables`，无任一财务查看权限时进入无权限页。

运行：

```powershell
cd apps/web
npm test -- financeApi.spec.ts permissionGuard.spec.ts App.spec.ts
```

预期：失败，原因是财务 API、路由和菜单尚未实现。

- [x] **步骤 2：实现财务 API 客户端、路由和菜单**

创建 `financeApi.ts`，接口分组为 `receivables`、`receipts`、`payables`、`payments`、`sources`。金额 payload 使用字符串。新增财务路由和菜单，按权限递归显示；`/finance` 根路由使用前端守卫或轻量重定向组件按权限动态进入首个可访问财务页面。

- [x] **步骤 3：验证定向前端基础测试和类型检查**

运行步骤 1 命令和：

```powershell
cd apps/web
npm run typecheck
```

预期：定向测试和类型检查通过。

## Task 7: 前端应收和收款页面

**角色：** 前端开发承担实现，UI 设计师承担规格审查，测试承担代码质量审查。

**文件：**
- 新建： `apps/web/src/modules/finance/FinanceSourceTracePanel.vue`
- 新建： `apps/web/src/modules/finance/ReceivableListView.vue`
- 新建： `apps/web/src/modules/finance/ReceivableFormView.vue`
- 新建： `apps/web/src/modules/finance/ReceivableDetailView.vue`
- 新建： `apps/web/src/modules/finance/ReceiptListView.vue`
- 新建： `apps/web/src/modules/finance/ReceiptFormView.vue`
- 新建： `apps/web/src/modules/finance/ReceiptDetailView.vue`
- 测试：对应 `*.spec.ts`

- [x] **步骤 1：编写预期失败的应收收款页面测试**

覆盖应收列表筛选、空态、错误态、权限按钮、生成表单、详情来源追溯、收款记录、收款表单、超额提示、已过账收款只读、状态标签和路由加载真实组件。

运行：

```powershell
cd apps/web
npm test -- ReceivableListView.spec.ts ReceivableFormView.spec.ts ReceivableDetailView.spec.ts ReceiptListView.spec.ts ReceiptFormView.spec.ts ReceiptDetailView.spec.ts permissionGuard.spec.ts
```

预期：失败，原因是应收和收款页面尚不存在。

- [x] **步骤 2：实现应收和收款页面**

复用采购、销售和成本页面模式。页面必须使用后台 ERP 信息密度，不新增总账、凭证、发票、税务、银行对账或报表界面。

- [x] **步骤 3：验证应收收款定向测试**

运行步骤 1 中的命令。

预期：定向测试通过。

## Task 8: 前端应付和付款页面

**角色：** 前端开发承担实现，UI 设计师承担规格审查，测试承担代码质量审查。

**文件：**
- 新建： `apps/web/src/modules/finance/PayableListView.vue`
- 新建： `apps/web/src/modules/finance/PayableFormView.vue`
- 新建： `apps/web/src/modules/finance/PayableDetailView.vue`
- 新建： `apps/web/src/modules/finance/PaymentListView.vue`
- 新建： `apps/web/src/modules/finance/PaymentFormView.vue`
- 新建： `apps/web/src/modules/finance/PaymentDetailView.vue`
- 测试：对应 `*.spec.ts`

- [x] **步骤 1：编写预期失败的应付付款页面测试**

覆盖应付列表筛选、空态、错误态、权限按钮、生成表单、详情来源追溯、付款记录、付款表单、超额提示、已过账付款只读、状态标签和路由加载真实组件。

- [x] **步骤 2：实现应付和付款页面**

与应收收款页面对称，来源文案必须统一为采购入库和采购订单，不得混入总账、凭证、税务或发票生命周期。

- [x] **步骤 3：验证财务前端定向测试**

运行 Task 7 定向测试加本任务新增测试。

预期：定向测试通过。

## Task 9: 前端全量回归和构建

**角色：** 测试承担执行，前端开发承担修复，测试承担代码质量复审。

- [x] **步骤 1：运行前端全量测试**

```powershell
cd apps/web
npm test
```

- [x] **步骤 2：运行类型检查**

```powershell
cd apps/web
npm run typecheck
```

- [x] **步骤 3：运行前端构建**

```powershell
cd apps/web
npm run build
```

预期：前端全量测试、类型检查和构建均通过。

## Task 10: 本地部署、浏览器主路径和权限验收

**角色：** 测试承担执行，前端开发和后端开发承担修复，产品经理承担规格复审。

**文件：**
- 更新： `docs/testing/financial-settlement-test-plan.md`
- 新建或更新： `docs/testing/financial-settlement-defects.md`，仅在发现问题时使用

- [x] **步骤 1：启动本地服务**

按本地开发文档启动 PostgreSQL、后端和前端。后端使用 `18080`，前端使用可用 Vite 端口。

- [x] **步骤 2：验证健康检查**

```powershell
Invoke-WebRequest -Uri 'http://127.0.0.1:18080/api/health' -UseBasicParsing
```

预期：响应包含 `{"status":"UP","service":"qherp-api"}`。

- [x] **步骤 3：执行浏览器主路径**

使用真实浏览器自动化完成应收生成、确认、部分收款、收清、来源追溯、应付生成、确认、部分付款、付清和来源追溯。

- [x] **步骤 4：执行权限路径**

验证管理员、财务角色、销售角色、采购角色、只读用户、无权限用户和未登录用户，确认前后端权限一致。

- [x] **步骤 5：执行异常路径**

验证未过账来源、重复来源、零金额、负金额、超精度、超额收付款、已结清继续登记、已关闭继续登记、已过账收付款编辑或取消、并发过账和事务回滚。

并发过账使用两个线程同时提交同一应收或应付的收付款请求；事务回滚通过重复来源、超额核销或受控异常路径验证无已过账收付款、无核销明细残留且台账余额不变。

- [x] **步骤 6：记录结果**

将执行结果、账号、数据标识、命令输出和缺陷记录追加到 `docs/testing/financial-settlement-test-plan.md`。

## Task 11: 浏览器视觉分析

**角色：** UI 设计师承担视觉审查，测试承担截图执行和记录，前端开发承担修复。

**文件：**
- 新建： `docs/testing/financial-settlement-visual-audit/notes.md`
- 新建： `docs/testing/financial-settlement-visual-audit/*.png`
- 更新： `docs/testing/financial-settlement-test-plan.md`

- [x] **步骤 1：采集视觉证据**

采集 `docs/testing/financial-settlement-test-plan.md` 中列出的截图，覆盖桌面和窄屏视口状态。

- [x] **步骤 2：编写视觉记录**

创建 `notes.md`，记录截图文件名、视口尺寸、页面、账号、发现问题、处理结果和最终结论。

- [x] **步骤 3：验证视觉资产**

```powershell
Get-ChildItem -LiteralPath 'docs/testing/financial-settlement-visual-audit' -Filter '*.png' | Select-Object Name,Length
```

预期：截图清单中的全部 PNG 文件均存在且非 0 长度，且每张截图不是空白、加载中、错页、错误账号、错误权限状态或裁切失真。

## Task 12: 最终质量门和阶段复审

**角色：** 测试执行最终质量门，产品经理做规格复审，后端开发做后端代码质量审查，测试做前端独立代码质量审查，UI 设计师做视觉复审，主代理做最终验收判断。

- [x] **步骤 1：运行最终后端测试**

运行 Task 5 后端全量测试命令。

- [x] **步骤 2：运行最终前端测试、类型检查和构建**

运行 Task 9 中的前端命令。

- [x] **步骤 3：运行空白检查**

```powershell
git diff --check
```

预期：退出码为 `0`，无空白错误。

- [x] **步骤 4：确认最终服务健康和工作区状态**

运行：

```powershell
Invoke-WebRequest -Uri 'http://127.0.0.1:18080/api/health' -UseBasicParsing
Invoke-WebRequest -Uri 'http://127.0.0.1:5188' -UseBasicParsing
git status --short
```

预期：后端健康检查返回 `UP`，前端当前 Vite 地址返回可访问状态，工作区只包含本阶段预期变更或已完成提交后的干净状态。

- [x] **步骤 5：确认没有未解决阻断缺陷**

检查财务往来缺陷记录。阻断缺陷必须修复并复验，严重缺陷必须修复或形成明确风险处理结论。

- [x] **步骤 6：固定角色最终复审**

使用 `/goal` 派发：

- 产品经理：规格复审。
- 后端开发：后端代码质量审查。
- 测试：前端独立代码质量审查和最终验收证据复核。
- UI 设计师：视觉分析复审。
- 前端开发：实现交接和残余风险说明，不单独作为前端代码质量审查结论。

- [x] **步骤 7：主代理交付判断**

主代理复核当前证据后，再判断财务往来阶段是否可合入或交付浏览器验收。没有新鲜命令输出和当前浏览器证据时，不得声称完成。

## 自检记录

- 规格覆盖：本计划覆盖应收、收款、应付、付款、来源追溯、权限、审计、前端页面、测试、浏览器验收、视觉分析和最终质量门。
- 范围检查：本计划排除总账、凭证、科目、税务、完整发票、银行对账、正式收入确认、正式成本结转、BI 报表、多币种、多组织、多公司、多账套和多租户。
- 类型一致性：路由路径、接口路径、权限编码、状态名称、来源类型和视觉目录与接口契约和设计规格一致。
- 审查边界：实现任务派发给固定项目角色；主代理只负责统筹和验收判断。
