# QH ERP 项目交接报告（财务往来基础完成版）

## 交接目的

本报告用于让新的项目主代理在接手 QH ERP 后，能够通过探索仓库和阅读本文，快速理解项目要求、项目结构、当前进度、固定协作模式、质量门、运行方式、验收证据、残余风险和下一阶段开发目标。

接手后不能只依赖本报告推进。新主代理必须以当前仓库、`AGENTS.md`、任务文档、接口契约、测试计划、实际命令输出、浏览器运行结果和用户最新指令为准。本报告是当前进度的上下文索引和决策摘要，不替代真实验证。

## 当前状态摘要

- 当前日期：2026-07-04。
- 当前工作区：`C:\Users\14567\.codex\worktrees\e440\qherp`。
- 当前工作区分支：`codex/financial-settlement-foundation`。
- 当前 HEAD：`d9af3d0`。
- 当前阶段：`013 财务往来基础模块` 已完成开发、审查、自动化测试、本地浏览器验收、视觉分析和固定角色最终复审。
- 当前工作区状态：存在本阶段未提交变更，尚未提交、尚未合入、尚未推送主分支。
- 当前本地验收服务：
  - 后端：`http://127.0.0.1:18080/api/health` 返回 `{"service":"qherp-api","status":"UP"}`。
  - 前端：`http://127.0.0.1:5188` 返回 HTTP `200`。
- 当前最新可验收范围：账号权限、基础资料、BOM、库存、生产执行、成本归集、采购管理基础、销售管理基础、财务往来基础。
- 当前完成边界：应收、收款、应付、付款、余额核销和来源追溯闭环。
- 当前明确不包含：总账、凭证、会计科目、会计期间、结账、税务申报、完整发票生命周期、银行对账、正式收入确认、正式成本结转、BI 报表、多币种、多组织、多公司、多账套、多租户。
- 当前无缺陷文档：`docs/testing/financial-settlement-defects.md` 不存在，表示本阶段没有登记未解决阻断或严重缺陷。

## 新主代理接手第一优先级

接手后第一优先级不是立即扩展新功能，而是确认当前财务往来基础模块的交付状态，并根据用户指令决定是否提交、合入、推送和准备主分支阶段验收。

建议接手顺序：

1. 阅读根目录 `AGENTS.md`，确认必须叫用户“爸爸”、必须中文沟通、必须复用固定五角色、必须使用 `/goal` 派发任务。
2. 阅读本文。
3. 阅读当前阶段核心文档：
   - `docs/tasks/013-financial-settlement-foundation.md`
   - `docs/api/financial-settlement-api.md`
   - `docs/testing/financial-settlement-test-plan.md`
   - `docs/testing/financial-settlement-visual-audit/notes.md`
   - `docs/superpowers/plans/2026-07-04-financial-settlement-implementation-plan.md`
4. 运行或复核当前质量门，至少包括：
   - 后端健康检查。
   - 前端 HTTP 访问。
   - `git status --short`。
   - `git diff --check`。
5. 不要在未确认当前交付状态前启动下一业务模块。
6. 若用户要求阶段交付或合入，先处理当前分支提交、主分支合入或推送，再在主分支本地部署并提供浏览器验收地址。

## 最高优先级项目要求

新主代理接手后必须严格遵守根目录 `AGENTS.md`。以下是高优先级规则摘要。

### 对用户沟通

- 每次对用户可见回复开头必须先称呼“爸爸”。
- 所有智能体与用户沟通必须使用中文。
- 除路径、命令、接口名、库名、协议名等不可翻译技术标识外，不使用非中文表达。

### 项目定位

QH ERP 是面向制造业单公司内部使用的 Web ERP 系统。项目不是零售、门店收银、电商交易、门店会员或多租户平台。

长期方向需要覆盖常规 ERP 能力：

- 基础资料。
- 采购。
- 销售。
- 库存。
- 生产。
- 财务。
- 报表。

当前产品重心是制造业生产管理，尤其重视：

- 物料管理。
- BOM。
- 库存。
- 生产工单。
- 领料。
- 报工。
- 完工入库。
- 成本归集。
- 采购入库来源追溯。
- 销售出库来源追溯。
- 往来台账和收付款来源追溯。

### 主代理职责边界

主代理是项目负责人，只负责：

- 项目统筹。
- 任务拆解。
- 上下文提供。
- 任务派发。
- 等待结果。
- 组织审查。
- 验收判断。
- 向用户汇报。

主代理默认不直接承担产品设计、UI 设计、前端开发、后端开发或测试执行工作。涉及具体设计、开发、测试或审查时，应优先派发给对应固定角色子代理。

### 固定 5 个角色子代理

项目固定只有 5 个角色：

- 产品经理。
- UI 设计师。
- 前端开发。
- 后端开发。
- 测试。

当前上下文中可复用的固定角色线程如下。线程 ID 可能随工具环境变化，职责边界以角色为准，不以 ID 为准。

| 角色 | 当前名称 | 当前线程 ID |
|---|---|---|
| 产品经理 | Parfit | `019f2cdf-f72a-7133-af78-5f80db3b23f9` |
| UI 设计师 | Russell | `019f2ce0-382d-73d2-a7ab-94c064a7e664` |
| 前端开发 | Carver | `019f2ce0-6d84-7551-88e5-7bb146f1e18b` |
| 后端开发 | Kuhn | `019f2ce0-a3a3-7692-8be7-9f177ca75111` |
| 测试 | Cicero | `019f2ce0-e54b-7b62-bc2a-d3e0c04c661f` |

必须强调：

- 不得为每个任务反复关闭和新建子代理。
- 不得创建第 6 类角色。
- 不得创建“规格审查代理”“代码质量审查代理”等临时新角色。
- 规格审查和代码质量审查必须由 5 个既定角色中的合适角色承担。
- 如果某个角色线程失效，只能创建同角色替代子代理，并记录替代原因。
- 实现者不得替代独立代码质量审查结论。前端实现的代码质量审查优先由测试角色承担，后端实现的代码质量审查可由测试或后端以外合适固定角色承担，最终仍需主代理复核证据。

### `/goal` 派发规则

向子代理派发任务必须使用 `/goal`，并明确写入：

- 使用哪个固定角色。
- 该角色承担实现、讨论、审查、复审还是测试执行职责。
- 任务目标。
- 输入上下文。
- 职责边界。
- 预期成果。
- 验收标准。
- 禁止发散范围。

### 新阶段推进流程

任何新阶段开始前必须先执行：

1. 组织 5 个固定角色分别从产品、UI、前端、后端、测试视角讨论。
2. 主代理汇总 5 个角色结论。
3. 形成阶段目标、范围边界、关键风险、验收标准和实施计划。
4. 计划未形成前不得进入实现。
5. 计划确认后再拆分任务，逐项派发给对应实现角色。
6. 每个实现任务完成后先做规格审查。
7. 规格审查通过后再做代码质量审查。
8. 审查失败必须派发修复任务并复审。
9. 主代理最终复核当前证据，再判断是否进入主分支阶段验收。

### 分支、合入和验收规则

- 日常功能开发应在独立任务分支或功能分支中完成。
- 主分支用于用户阶段验收，应保持稳定、可部署、可浏览器访问。
- 未完成、未验证或不满足验收标准的功能不得合入或推送主分支。
- 阶段成果必须能在浏览器中实际查看和操作。
- 浏览器验收必须包含视觉分析，截图必须来自真实浏览器。
- 视觉分析记录目录必须按模块建立，例如 `docs/testing/<模块标识>-visual-audit/`。
- 存在阻断缺陷时，不得合入主分支，不得通知用户验收。
- 遇到测试、构建、部署或浏览器访问失败，应优先定位并修复，不得跳过验证改走替代路线。

## 项目结构

仓库根目录主要内容：

- `AGENTS.md`：项目最高优先级工作规范。
- `README.md`：项目入口说明。
- `compose.yaml`：本地 PostgreSQL 依赖服务。
- `.env.example`：本地环境变量示例。
- `apps/api`：后端 Spring Boot 服务。
- `apps/web`：前端 Vue 应用。
- `docs`：产品、技术、接口、测试、部署、任务、设计规格、计划和交接资料。

后端主要结构：

- `apps/api/pom.xml`：后端 Maven 配置，Java 21，Spring Boot 4.0.7。
- `apps/api/src/main/java/com/qherp/api`：后端应用代码。
- `apps/api/src/main/java/com/qherp/api/auth`：认证相关。
- `apps/api/src/main/java/com/qherp/api/security`：权限授权管理。
- `apps/api/src/main/java/com/qherp/api/system/user`：用户管理。
- `apps/api/src/main/java/com/qherp/api/system/role`：角色管理。
- `apps/api/src/main/java/com/qherp/api/system/permission`：权限管理。
- `apps/api/src/main/java/com/qherp/api/system/master`：基础资料。
- `apps/api/src/main/java/com/qherp/api/system/bom`：BOM 管理。
- `apps/api/src/main/java/com/qherp/api/system/inventory`：库存管理。
- `apps/api/src/main/java/com/qherp/api/system/production`：生产执行。
- `apps/api/src/main/java/com/qherp/api/system/cost`：成本归集。
- `apps/api/src/main/java/com/qherp/api/system/procurement`：采购管理基础。
- `apps/api/src/main/java/com/qherp/api/system/sales`：销售管理基础。
- `apps/api/src/main/java/com/qherp/api/system/finance`：财务往来基础，应收、收款、应付、付款接口和服务。
- `apps/api/src/main/resources/db/migration`：Flyway 数据库迁移，当前新增到 `V10__financial_settlement_schema.sql`。
- `apps/api/src/test`：后端测试，使用 JUnit、Spring Boot Test、Testcontainers。

前端主要结构：

- `apps/web/package.json`：前端脚本与依赖。
- `apps/web/src/main.ts`：前端入口。
- `apps/web/src/App.vue`：主布局与导航。
- `apps/web/src/router`：路由和权限守卫。
- `apps/web/src/stores`：状态管理。
- `apps/web/src/shared/api`：前端接口封装。
- `apps/web/src/modules/auth`：登录。
- `apps/web/src/modules/system`：用户、角色、权限相关页面。
- `apps/web/src/modules/master`：计量单位、仓库、供应商、客户。
- `apps/web/src/modules/materials`：物料分类、物料档案、BOM。
- `apps/web/src/modules/inventory`：库存余额、库存变动、库存单据。
- `apps/web/src/modules/production`：生产工单、领料、报工、完工入库。
- `apps/web/src/modules/cost`：成本记录、手工成本、成本详情。
- `apps/web/src/modules/procurement`：采购订单、采购入库。
- `apps/web/src/modules/sales`：销售订单、销售出库。
- `apps/web/src/modules/finance`：财务往来页面、状态标签、来源追溯和测试辅助。
- `apps/web/src/shared/api/financeApi.ts`：财务往来前端 API 封装。

文档主要结构：

- `docs/README.md`：文档索引。
- `docs/product`：产品范围、业务流程、产品决策。
- `docs/tasks`：阶段任务文档和执行记录。
- `docs/api`：接口契约。
- `docs/testing`：测试计划、执行记录、缺陷清单、视觉分析。
- `docs/superpowers/specs`：阶段设计规格。
- `docs/superpowers/plans`：阶段实施计划。
- `docs/architecture`：技术基线和技术选型。
- `docs/frontend`：前端基线。
- `docs/ui`：UI 与交互基线。
- `docs/ops`：本地开发、部署和验收说明。
- `docs/handoffs`：交接报告。本报告是当前最新交接报告。

## 技术栈与运行方式

前端：

- Vue 3。
- TypeScript。
- Vite。
- Vue Router。
- Pinia。
- Element Plus。
- Vitest。
- Playwright / Chromium 用于浏览器验收和视觉证据。

后端：

- Java 21。
- Spring Boot 4.0.7。
- Spring MVC。
- Spring Security。
- Spring Data JPA。
- Flyway。
- PostgreSQL。
- Testcontainers。
- Maven。

常用命令：

```powershell
# 前端
cd apps/web
npm test
npm run typecheck
npm run build
npm run dev

# 后端全量测试
docker run --rm `
  -e TESTCONTAINERS_RYUK_DISABLED=true `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal `
  -v "${PWD}:/workspace" `
  -v /var/run/docker.sock:/var/run/docker.sock `
  -v qherp-maven-repo:/root/.m2 `
  -w /workspace/apps/api `
  maven:3.9-eclipse-temurin-21 `
  mvn -q test

# 后端健康检查
Invoke-WebRequest -Uri 'http://127.0.0.1:18080/api/health' -UseBasicParsing

# 前端访问检查
Invoke-WebRequest -Uri 'http://127.0.0.1:5188' -UseBasicParsing

# 空白检查
git diff --check
```

本阶段最终验收时使用的本地服务：

- 后端：`http://127.0.0.1:18080`。
- 前端：`http://127.0.0.1:5188`。
- 管理员账号：`admin / Qherp@2026!`。

## 已完成模块进度

当前已经完成并形成阶段验收证据的模块：

1. 工程启动与技术基线。
2. 账号与权限基础。
3. 基础资料与物料管理。
4. BOM 管理。
5. 库存管理基础。
6. 生产执行基础。
7. 成本归集基础。
8. 采购管理基础。
9. 销售管理基础。
10. 财务往来基础。

财务往来基础完成后，系统已经具备从基础资料、BOM、库存、生产、成本、采购、销售到往来收付的核心业务闭环。仍未进入正式财务核算和报表阶段。

## 财务往来基础模块完成范围

本阶段任务文档：`docs/tasks/013-financial-settlement-foundation.md`。

本阶段目标是建立制造业 ERP 在采购入库和销售出库之后的基础往来台账能力，让有权限用户能够：

- 基于已过账销售出库生成应收台账。
- 确认应收。
- 登记收款草稿。
- 过账部分收款。
- 过账全额收款直至收清。
- 追溯应收来源销售出库和销售订单。
- 基于已过账采购入库生成应付台账。
- 确认应付。
- 登记付款草稿。
- 过账部分付款。
- 过账全额付款直至付清。
- 追溯应付来源采购入库和采购订单。

### 后端完成内容

核心文件：

- `apps/api/src/main/resources/db/migration/V10__financial_settlement_schema.sql`
- `apps/api/src/main/java/com/qherp/api/system/finance/FinanceAdminController.java`
- `apps/api/src/main/java/com/qherp/api/system/finance/FinanceAdminService.java`
- `apps/api/src/main/java/com/qherp/api/system/finance/ReceivableStatus.java`
- `apps/api/src/main/java/com/qherp/api/system/finance/ReceiptStatus.java`
- `apps/api/src/main/java/com/qherp/api/system/finance/PayableStatus.java`
- `apps/api/src/main/java/com/qherp/api/system/finance/PaymentStatus.java`
- `apps/api/src/test/java/com/qherp/api/system/finance/FinanceAdminControllerTests.java`

后端能力：

- 财务往来 V10 迁移。
- 应收、收款、应付、付款状态枚举。
- `FINANCE_*` 错误码。
- 财务权限种子。
- 财务路径鉴权映射。
- 应收候选来源：已过账销售出库。
- 应付候选来源：已过账采购入库。
- `settlementGenerated=false` 候选过滤。
- 来源明细唯一约束防重复生成。
- 应收、应付生成、编辑草稿、确认、取消、关闭。
- 收款、付款草稿创建、编辑、过账、取消草稿。
- 过账事务内锁定收付款单、核销行和目标台账。
- 余额、累计金额和状态一致性维护。
- 取消草稿收付款保留 allocation 以支持详情和列表追溯，台账详情排除取消记录。
- 权限、CSRF、审计和异常路径覆盖。
- 并发过账和事务回滚通过浏览器/接口验收。

### 前端完成内容

核心文件：

- `apps/web/src/shared/api/financeApi.ts`
- `apps/web/src/shared/api/financeApi.spec.ts`
- `apps/web/src/modules/finance/financePageHelpers.ts`
- `apps/web/src/modules/finance/FinanceSourceTracePanel.vue`
- `apps/web/src/modules/finance/ReceivableListView.vue`
- `apps/web/src/modules/finance/ReceivableFormView.vue`
- `apps/web/src/modules/finance/ReceivableDetailView.vue`
- `apps/web/src/modules/finance/ReceiptListView.vue`
- `apps/web/src/modules/finance/ReceiptFormView.vue`
- `apps/web/src/modules/finance/ReceiptDetailView.vue`
- `apps/web/src/modules/finance/PayableListView.vue`
- `apps/web/src/modules/finance/PayableFormView.vue`
- `apps/web/src/modules/finance/PayableDetailView.vue`
- `apps/web/src/modules/finance/PaymentListView.vue`
- `apps/web/src/modules/finance/PaymentFormView.vue`
- `apps/web/src/modules/finance/PaymentDetailView.vue`
- `apps/web/src/modules/finance/*StatusTag.vue`
- `apps/web/src/router/index.ts`
- `apps/web/src/router/permissionGuard.spec.ts`
- `apps/web/src/App.vue`
- `apps/web/src/App.spec.ts`

前端能力：

- 财务往来 API 客户端。
- 财务菜单。
- `/finance` 动态重定向。
- 应收、收款、应付、付款路由。
- 应收列表、生成表单、详情。
- 收款列表、登记/编辑表单、详情。
- 应付列表、生成表单、详情。
- 付款列表、登记/编辑表单、详情。
- 来源追溯组件，支持销售出库/销售订单和采购入库/采购订单。
- 状态标签。
- 金额字符串安全格式化和金额比较。
- 权限按钮显示。
- 非草稿只读。
- 分页、筛选、空态、错误态。
- 窄屏可访问的列表和表单布局。

## 财务往来质量门和证据

### 自动化测试

最终后端质量门：

- Docker Maven `mvn -q test` 通过。
- Surefire 汇总：17 个报告、138 个测试、0 失败、0 错误、0 跳过。
- Testcontainers 残留检查：无输出。

最终前端质量门：

- `npm test` 通过。
- 58 个测试文件、361 个测试通过。
- `npm run typecheck` 通过。
- `npm run build` 通过。
- 构建阶段存在既有 Vite chunk size warning，不是失败，不阻断当前交付。

其他质量门：

- `git diff --check` 退出码为 0，仅有 Git LF/CRLF 行尾提示。
- 后端健康检查返回 200，内容为 `{"service":"qherp-api","status":"UP"}`。
- 前端 `http://127.0.0.1:5188` 返回 200。
- `docs/testing/financial-settlement-defects.md` 不存在。

### 浏览器验收

记录位置：`docs/testing/financial-settlement-test-plan.md`。

Task 10 浏览器验收通过，运行标识：`T1072567899`。

结果摘要：

- 真实浏览器自动化脚本输出：`APPROVED_CANDIDATE`。
- 主路径统计：`main=6`。
- 权限路径统计：`permissions=7`。
- 异常路径统计：`exceptions=21`。
- 缺陷数组为空。

覆盖内容：

- 销售出库生成应收。
- 应收确认。
- 部分收款。
- 收清。
- 应收来源追溯到销售出库和销售订单。
- 采购入库生成应付。
- 应付确认。
- 部分付款。
- 付清。
- 应付来源追溯到采购入库和采购订单。
- 财务、销售、采购、只读、无权限和未登录用户权限路径。
- 未过账来源、重复来源、零金额、负金额、超精度、超额收付款、已结清继续登记、已关闭继续登记、已过账收付款编辑或取消、并发过账和事务回滚。

### 视觉分析

目录：`docs/testing/financial-settlement-visual-audit/`。

记录文件：`docs/testing/financial-settlement-visual-audit/notes.md`。

Task 11 视觉分析通过，运行标识：`T1173028729`。

资产结果：

- 28 张 PNG。
- `count=28`。
- `zero=0`。
- UI 设计师最终视觉复审：`APPROVED`。

截图覆盖：

- 财务菜单。
- 应收列表。
- 应收查询结果。
- 应收空态。
- 应收生成表单。
- 应收详情与来源追溯。
- 收款列表。
- 收款空态。
- 收款表单。
- 收款超额异常。
- 收款详情只读。
- 应付列表。
- 应付空态。
- 应付生成表单。
- 应付详情与来源追溯。
- 付款列表。
- 付款空态。
- 付款表单。
- 付款超额异常。
- 付款详情只读。
- 财务角色权限。
- 销售角色权限。
- 采购角色权限。
- 只读权限。
- 无权限状态。
- 窄屏应收列表。
- 窄屏收款列表。
- 窄屏付款表单。

非阻断视觉观察：

- 窄屏导航占用首屏，用户需要滚动后才能看到业务区域。
- 销售、采购、只读角色在财务台账页可能因客户或供应商筛选数据权限不足出现提示条。
- 这些问题不影响核心操作、核心数据识别和阶段交付，后续可优化移动端导航和按角色隐藏不可用筛选项。

### 固定角色最终复审

Task 12 固定角色最终复审结果：

| 角色 | 职责 | 结论 |
|---|---|---|
| 产品经理 Parfit | 最终规格复审 | `APPROVED` |
| 后端开发 Kuhn | 后端代码质量审查 | `APPROVED` |
| 测试 Cicero | 前端独立代码质量和验收证据复核 | `APPROVED` |
| UI 设计师 Russell | 最终视觉复审 | `APPROVED` |
| 前端开发 Carver | 实现交接和残余风险说明 | 未提出阻断风险 |

主代理交付判断：

- 财务往来基础模块满足任务文档、接口契约、测试计划、浏览器验收和视觉分析要求。
- 无未解决阻断缺陷或严重缺陷。
- 阶段成果可作为本地浏览器验收交付候选。

## 当前工作区变更概览

当前财务往来基础模块变更尚未提交。`git status --short` 显示本阶段相关修改和新增文件。

已修改的既有文件包括：

- `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- `apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`
- `apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- `apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`
- `apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`
- `apps/web/src/App.spec.ts`
- `apps/web/src/App.vue`
- `apps/web/src/router/index.ts`
- `apps/web/src/router/permissionGuard.spec.ts`
- `docs/README.md`
- `docs/product/business-flow.md`
- `docs/product/product-decisions.md`
- `docs/testing/acceptance-criteria.md`

新增或未跟踪的本阶段目录和文件包括：

- `apps/api/src/main/java/com/qherp/api/system/finance/`
- `apps/api/src/main/resources/db/migration/V10__financial_settlement_schema.sql`
- `apps/api/src/test/java/com/qherp/api/system/finance/`
- `apps/web/src/modules/finance/`
- `apps/web/src/shared/api/financeApi.ts`
- `apps/web/src/shared/api/financeApi.spec.ts`
- `docs/api/financial-settlement-api.md`
- `docs/superpowers/plans/2026-07-04-financial-settlement-implementation-plan.md`
- `docs/superpowers/specs/2026-07-04-financial-settlement-design.md`
- `docs/tasks/013-financial-settlement-foundation.md`
- `docs/testing/financial-settlement-test-plan.md`
- `docs/testing/financial-settlement-visual-audit/`

本报告新增后，还会有：

- `docs/handoffs/2026-07-04-project-handoff-financial-settlement-complete.md`

注意：视觉目录中包含 `task11-result.json`，它是 Task 11 视觉执行结果摘要，当前作为视觉证据辅助文件保留。若提交前决定只保留 Markdown 和 PNG，需要先确认是否影响证据完整性。

## 当前残余风险与非阻断事项

当前没有阻断缺陷。以下是已记录的非阻断观察：

- Vite 构建存在 chunk size warning，不影响本阶段构建成功。后续可按模块拆包优化。
- 窄屏导航占用首屏，移动端需要滚动才能看到业务内容。当前核心数据和操作仍可访问。
- 财务列表列数较多，后续新增字段时应谨慎控制列密度，尤其是应付/付款宽表和固定操作列。
- 销售、采购、只读角色在财务台账页可能出现客户或供应商筛选权限提示。当前不暴露越权数据，不阻断只读能力，后续可按角色隐藏不可用筛选项。
- 后端 `FINANCE_CONCURRENT_MODIFICATION` 是保留错误码，当前并发控制主要依赖数据库行锁和余额复校，浏览器/接口并发验收通过。
- 收付款 `method` 当前按非空和长度校验，没有收敛到固定枚举。若后续产品要求固定收付款方式，需要另行设计。
- 审计测试确认写操作有记录，但未逐条断言金额变化和状态变化摘要。后续若审计成为用户可查报表，需要加强审计摘要契约。
- 金额响应前后端目前兼容 `number|string`。前端金额展示已使用字符串安全格式化，新增金额逻辑不得回退到浮点数计算。
- 当前不支持已过账收付款反冲、退款、退票、调整单或冲销单。错误修正能力需要另行立项。

## 接手后的立即行动建议

### 如果目标是完成当前阶段交付

建议新主代理按以下顺序推进：

1. 复核 `git status --short`，确认变更均属于财务往来阶段。
2. 复跑最低质量门：
   - `git diff --check`
   - 后端健康检查。
   - 前端访问检查。
3. 如用户要求提交，先审阅是否包含不应提交的本地生成物。
4. 创建提交，建议提交信息：`完成财务往来基础模块`。
5. 合入或推送前，按项目规则确认主分支稳定。
6. 合入主分支后必须在主分支本地启动服务并提供浏览器验收地址。
7. 不得把未验证分支直接推给用户验收。

### 如果目标是开启下一阶段开发

必须先完成当前财务往来阶段的提交/合入/主分支验收决策，或至少得到用户明确授权可以在当前分支基础上继续新阶段。

接着按项目规范启动新阶段：

1. 组织 5 个固定角色阶段讨论。
2. 汇总形成阶段目标、范围边界、关键风险、验收标准和实施计划。
3. 编写任务文档、设计规格、接口契约、测试计划和实施计划。
4. 固定五角色复审文档基线。
5. 才能进入实现任务。

## 下一阶段开发目标（重点）

### 推荐下一阶段：经营报表基础模块

当前推荐的下一阶段开发目标是：`014 经营报表基础模块`。

推荐原因：

- 账号权限、基础资料、BOM、库存、生产、成本、采购、销售和财务往来基础均已完成。
- 系统已经积累从物料、库存、生产、采购、销售、成本到往来收付的可信业务数据。
- `docs/product/product-decisions.md` 已记录“报表一期按固定看板还是可配置报表推进”仍待后续模块确认。
- 财务往来阶段明确排除了 BI 报表和正式财务报表，因此下一阶段可以在不进入正式总账核算的前提下建立业务经营报表基础。

推荐目标描述：

建立制造业单公司 ERP 的一期经营报表基础能力，让管理者和有权限用户能够在浏览器中查看固定口径的采购、销售、库存、生产、成本和财务往来汇总指标，并能从汇总指标追溯到已有业务单据。

### 推荐纳入范围

建议下一阶段只做“固定经营报表基础”，不要直接做复杂 BI 或可配置报表平台。

建议纳入：

- 经营概览页：
  - 销售出库金额。
  - 采购入库金额。
  - 应收余额。
  - 应付余额。
  - 已收金额。
  - 已付金额。
  - 库存物料数量或库存金额基础指标。
  - 生产工单状态数量。
  - 成本归集金额。
- 固定报表列表：
  - 销售出库汇总。
  - 采购入库汇总。
  - 库存余额汇总。
  - 生产工单进度汇总。
  - 成本归集汇总。
  - 应收应付余额汇总。
  - 收付款流水汇总。
- 筛选条件：
  - 日期范围。
  - 客户。
  - 供应商。
  - 物料。
  - 仓库。
  - 状态。
- 权限：
  - 报表查看权限。
  - 报表导出权限如需要必须单独确认。
  - 销售、采购、财务等方向级查看边界。
- 追溯：
  - 汇总项可跳转到已有业务列表或详情。
  - 报表不复制业务编辑能力。
- 浏览器验收和视觉分析：
  - 报表列表。
  - 经营概览。
  - 空态。
  - 权限差异。
  - 窄屏可读性。

### 推荐排除范围

下一阶段即使做报表，也应继续排除：

- 可拖拽配置 BI。
- 自定义 SQL 报表。
- 多维自由透视。
- 经营大屏或营销式展示页。
- 总账报表。
- 资产负债表、利润表、现金流量表。
- 凭证、科目、期间、结账。
- 发票税务报表。
- 银行对账。
- 多币种、多组织、多公司、多账套。
- 数据导出审批或复杂订阅推送。

### 推荐核心验收路径

下一阶段报表基础模块建议验收：

1. 管理员登录，打开经营报表入口。
2. 查看经营概览，指标来自真实已过账或已确认业务数据。
3. 按日期筛选销售、采购、应收、应付、库存、生产和成本汇总。
4. 从报表指标跳转到已有销售出库、采购入库、库存余额、生产工单、成本记录、应收应付详情。
5. 只读用户能查看授权报表，不能修改业务。
6. 无权限用户不能访问报表页面或接口。
7. 报表口径与源业务单据数量、金额一致。
8. 空数据时展示清晰空态。
9. 浏览器视觉分析覆盖桌面和窄屏。

### 下一阶段启动前必须确认的问题

报表阶段存在产品决策点，主代理不得自行跳过。建议先组织五角色讨论并向用户确认：

- 报表一期采用固定报表，还是可配置报表。
- 是否需要导出能力；如果需要，导出格式、权限和字段范围是什么。
- 报表金额口径是否只用业务金额，不做正式财务核算金额。
- 库存金额是否按已有采购/成本数据估算，还是暂时只做数量报表。
- 是否允许跨模块汇总但不做跨组织、多公司、多账套。
- 是否需要图表；如果需要，使用轻量折线/柱状图还是只做表格和指标卡。

当前建议答案：

- 一期先做固定报表。
- 不做可配置 BI。
- 不做正式财务报表。
- 图表可以谨慎使用，但界面仍应保持后台 ERP 信息密度，不能变成营销页或经营大屏。

## 当前阶段不得误判为已完成的能力

财务往来基础完成后，以下能力仍未实现，不能对用户或下一代理宣称已经具备：

- 总账。
- 凭证。
- 会计科目。
- 会计期间。
- 结账。
- 正式收入确认。
- 正式成本结转。
- 发票生命周期。
- 税务处理。
- 银行对账。
- 付款审批。
- 报销。
- 固定资产。
- 工资。
- 多币种。
- 多组织。
- 多公司。
- 多账套。
- 可配置 BI。
- 正式财务报表。
- 销售退货。
- 采购退货。
- 退款。
- 退票。
- 已过账收付款反冲。

## 重要文档索引

接手后建议优先阅读：

- `AGENTS.md`
- `docs/README.md`
- `docs/product/product-decisions.md`
- `docs/product/business-flow.md`
- `docs/tasks/013-financial-settlement-foundation.md`
- `docs/api/financial-settlement-api.md`
- `docs/testing/financial-settlement-test-plan.md`
- `docs/testing/financial-settlement-visual-audit/notes.md`
- `docs/superpowers/specs/2026-07-04-financial-settlement-design.md`
- `docs/superpowers/plans/2026-07-04-financial-settlement-implementation-plan.md`
- `docs/testing/acceptance-criteria.md`

如准备启动下一阶段报表基础模块，还应阅读：

- `docs/product/product-decisions.md` 中“待后续模块确认”。
- `docs/product/business-flow.md` 中当前业务链路。
- 已完成模块的测试计划和视觉记录，用于复用验收结构。

## 最终交接结论

财务往来基础模块已经完成到本地浏览器验收候选状态。当前分支包含完整代码、文档、测试、浏览器验收和视觉证据，但尚未提交和合入主分支。

新主代理接手后的首要任务是保护当前已验证成果：先复核当前工作区和质量门，再按用户指令完成提交、合入、推送或主分支阶段验收。只有在当前阶段交付边界明确之后，才应启动下一阶段。

下一阶段开发建议聚焦 `014 经营报表基础模块`，目标是基于已完成的采购、销售、库存、生产、成本和财务往来数据，建立固定口径的经营汇总和追溯报表。该阶段必须继续排除正式总账、凭证、税务、完整发票、银行对账、多组织和可配置 BI，避免把报表基础误扩展为正式财务核算或 BI 平台。
