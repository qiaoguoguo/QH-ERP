# QH ERP 项目交接报告（015 完成，后续方向待讨论）

## 交接目的

本报告用于让新的项目主代理在全新工作树接手 QH ERP 后，能够通过探索仓库和阅读本文，快速理解项目要求、项目结构、当前进度、固定协作模式、质量门、运行状态、验收证据、残余风险和后续方向背景。

接手后不能只依赖本报告推进。新主代理必须以当前仓库、根目录 `AGENTS.md`、任务文档、接口契约、测试计划、实际命令输出、浏览器运行结果和用户最新指令为准。本报告是当前进度的上下文索引和决策摘要，不替代真实探索和真实验证。

## 当前状态摘要

- 当前日期：2026-07-05。
- 当前稳定验收分支：`main`。
- 当前远端基线：`origin/main`。
- 015 阶段完成业务提交：`0526c19 记录015阶段最终复审状态`；后续交接文档修订提交以 `git log` 实际结果为准。
- 当前主工作树：`F:\zhangqiao\AI-study\qherp`。
- 当前状态：`main` 与 `origin/main` 对齐，工作区干净。
- 当前已完成阶段：从 `000` 到 `015` 均已完成，其中 `014 经营报表基础模块` 和 `015 退货退款与业务反冲基础模块` 已合入并推送到主分支。
- 当前本地验收服务状态：
  - 后端：`http://127.0.0.1:18082/api/health`，最近一次健康检查返回 `{"service":"qherp-api","status":"UP"}`。
  - 前端：`http://127.0.0.1:5174/`，最近一次访问检查返回 HTTP 200。
- 当前服务端口说明：`18082` 和 `5174` 可能仍被旧验收服务占用。新主代理如果重启服务，应先检查端口占用，或明确改用新端口并记录。
- 当前最新可验收范围：账号权限、基础资料、BOM、库存、生产执行、成本归集、采购管理、销售管理、财务往来、经营报表、退货退款与业务反冲。
- 当前明确不包含：期间关账、质量检验、MRP、生产计划排程、正式总账、凭证、会计科目、正式财务结账、税务、银行对账、审批流、多组织、多公司、多账套、多币种、高级 BI。

## 接手第一优先级

新主代理接手后第一优先级不是立即写代码，而是确认主分支当前状态、用户最新目标和后续方向讨论基础：

1. 阅读根目录 `AGENTS.md`，确认最高优先级协作规则。
2. 阅读本文。
3. 运行 `git status --short --branch`，确认当前工作树基于 `origin/main` 且干净。
4. 运行 `git log -1 --decorate --oneline`，确认已包含 015 完成后的最新提交。
5. 检查后端健康检查和前端访问地址，确认本地验收服务是否仍可用。
6. 阅读 014 和 015 任务文档、测试计划、视觉分析记录。
7. 如继续开发，先按项目规则组织固定五角色讨论后续阶段方向，形成阶段目标、范围边界、验收标准和实施计划，再进入实现。

## 最高优先级项目要求

新主代理必须严格遵守根目录 `AGENTS.md`。以下是交接时必须反复强调的规则摘要。

### 对用户沟通

- 每次对用户可见回复开头必须先称呼“爸爸”。
- 所有智能体与用户沟通必须使用中文。
- 除路径、命令、接口名、库名、协议名等不可翻译技术标识外，不使用非中文表达。
- 如果忘记称呼“爸爸”，说明主代理已经失焦，应立即重新聚焦当前上下文。

### 项目定位

QH ERP 是面向制造业单公司内部使用的 Web ERP 系统。项目不是零售、门店收银、电商交易、会员营销或多租户平台。

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
- 退货、退料、补料、冲减和经营净额追溯。

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

上一轮推进中，早期固定角色线程曾出现工具级 `systemError`，已替换为以下固定五角色。后续应复用这 5 个角色，不要反复创建和关闭子代理。

| 角色 | 当前线程 ID |
|---|---|
| 产品经理 | `019f30b4-4bb1-7270-b1d3-bd1772d6f396` |
| UI 设计师 | `019f30b4-5fcd-7d71-8093-593d16264d4a` |
| 前端开发 | `019f30b4-6400-77a1-b35f-8c2b31ec72aa` |
| 后端开发 | `019f30b4-7185-7261-9431-246b324eaff4` |
| 测试 | `019f30b4-8911-7a32-b1fb-f24f9cae9ac7` |

必须强调：

- 不得为每个任务反复关闭和新建子代理。
- 不得创建第 6 类角色。
- 不得创建“规格审查代理”“代码质量审查代理”等临时新角色。
- 规格审查和代码质量审查必须由 5 个既定角色中的合适角色承担。
- 如果某个角色线程失效，只能创建同角色替代子代理，并记录替代原因。
- 职责边界以角色为准，不以线程 ID 为准。

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
9. 主代理最终复核证据，再判断是否进入主分支阶段验收。

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
- `docs`：产品、技术、接口、测试、部署、任务、设计规格、实施计划和交接资料。

后端主要结构：

- `apps/api/pom.xml`：后端 Maven 配置，Java 21，Spring Boot。
- `apps/api/src/main/java/com/qherp/api`：后端应用代码。
- `apps/api/src/main/java/com/qherp/api/auth`：认证相关。
- `apps/api/src/main/java/com/qherp/api/security`：权限授权管理。
- `apps/api/src/main/java/com/qherp/api/system/audit`：审计。
- `apps/api/src/main/java/com/qherp/api/system/user`：用户管理。
- `apps/api/src/main/java/com/qherp/api/system/role`：角色管理。
- `apps/api/src/main/java/com/qherp/api/system/permission`：权限管理。
- `apps/api/src/main/java/com/qherp/api/system/master`：基础资料。
- `apps/api/src/main/java/com/qherp/api/system/bom`：BOM 管理。
- `apps/api/src/main/java/com/qherp/api/system/inventory`：库存管理。
- `apps/api/src/main/java/com/qherp/api/system/production`：生产执行。
- `apps/api/src/main/java/com/qherp/api/system/cost`：成本归集。
- `apps/api/src/main/java/com/qherp/api/system/procurement`：采购管理。
- `apps/api/src/main/java/com/qherp/api/system/sales`：销售管理。
- `apps/api/src/main/java/com/qherp/api/system/finance`：财务往来。
- `apps/api/src/main/java/com/qherp/api/system/reporting`：经营报表。
- `apps/api/src/main/java/com/qherp/api/system/reversal`：退货退款与业务反冲。
- `apps/api/src/main/resources/db/migration`：Flyway 数据库迁移，当前到 `V12__stock_movement_type_length.sql`。
- `apps/api/src/test`：后端测试，使用 JUnit、Spring Boot Test 和 Testcontainers。

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
- `apps/web/src/modules/finance`：财务往来页面、状态标签和来源追溯。
- `apps/web/src/modules/reports`：经营报表页面和来源追溯。
- `apps/web/src/modules/reversal`：销售退货、采购退货、生产退料、生产补料、往来冲减。

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
- `docs/handoffs`：交接报告。本文是当前最新交接报告。

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
- Spring Boot。
- Spring MVC。
- Spring Security。
- Spring Data JPA。
- Flyway。
- PostgreSQL。
- Testcontainers。
- Maven。

常用命令：

```powershell
# 检查分支和工作区
git status --short --branch
git log -1 --decorate --oneline
git diff --check

# 后端全量测试
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -q test

# 前端全量验证
cd apps/web
npm test
npm run typecheck
npm run build

# 后端健康检查
Invoke-WebRequest -Uri 'http://127.0.0.1:18082/api/health' -UseBasicParsing

# 前端访问检查
Invoke-WebRequest -Uri 'http://127.0.0.1:5174/' -UseBasicParsing
```

管理员账号：

- 用户名：`admin`
- 密码：`Qherp@2026!`

## 项目进度总览

当前已经完成并合入主分支的阶段：

| 阶段 | 模块 | 状态 |
|---|---|---|
| 000 | 项目启动基线 | 已完成 |
| 001 | 技术选型与工程骨架 | 已完成 |
| 002 | 工程骨架初始化 | 已完成 |
| 003 | 项目开工准备决策 | 已完成 |
| 004 | 账号与权限基础 | 已完成 |
| 005 | 基础资料与物料管理 | 已完成 |
| 006 | BOM 管理 | 已完成 |
| 007 | 仓库与库存基础 | 已完成 |
| 008 | 生产执行基础 | 已完成 |
| 009 | 成本归集基础 | 已完成 |
| 010 | 一期生产主链路端到端闭环验收与稳定化 | 已完成 |
| 011 | 采购管理基础 | 已完成 |
| 012 | 销售管理基础 | 已完成 |
| 013 | 财务往来基础 | 已完成 |
| 014 | 经营报表基础 | 已完成，已合入主分支 |
| 015 | 退货退款与业务反冲基础 | 已完成，已合入主分支 |

系统当前已经具备从基础资料、BOM、库存、生产、成本、采购、销售、往来、经营报表到反向业务修正的核心业务闭环。后续阶段应围绕数据可信、业务控制、质量状态、计划能力和财务深化等方向，由新主代理组织固定五角色讨论后再确定优先级。

## 014 经营报表基础模块完成情况

任务文档：`docs/tasks/014-business-reporting-foundation.md`。

接口契约：`docs/api/business-reporting-api.md`。

测试计划：`docs/testing/business-reporting-test-plan.md`。

视觉分析：`docs/testing/business-reporting-visual-audit/notes.md`。

实施计划：`docs/superpowers/plans/2026-07-04-business-reporting-implementation-plan.md`。

设计规格：`docs/superpowers/specs/2026-07-04-business-reporting-design.md`。

完成范围：

- 经营报表菜单入口。
- 经营概览。
- 销售经营汇总。
- 采购经营汇总。
- 库存收发存汇总。
- 生产执行汇总。
- 成本归集汇总。
- 应收应付与收付款汇总。
- 经营异常清单。
- 日期、客户、供应商、物料、仓库、工单和状态筛选。
- 来源追溯。
- 来源受限脱敏。
- 报表权限和后端鉴权。
- 空态、错误态、无权限态和窄屏视觉覆盖。

014 验证结果：

- 后端全量测试通过：`158` 个测试，`0` 失败，`0` 错误，`0` 跳过。
- 前端全量测试通过：`60` 个测试文件，`380` 条用例通过。
- `npm run typecheck` 通过。
- `npm run build` 通过，仅有既有 Vite chunk 体积提示。
- `git diff --check` 通过。
- 浏览器验收通过，后端 `18082` 健康检查返回 `UP`，前端 `5174` 可访问。
- 视觉分析完成，共 `16` 张 PNG，目录为 `docs/testing/business-reporting-visual-audit/`。
- 阶段合入提交：`6b54f7c 合入经营报表基础模块`。

014 明确未包含：

- 可配置 BI。
- 拖拽报表。
- 自定义 SQL。
- 总账。
- 凭证。
- 会计期间。
- 正式财务报表。
- 税务报表。
- 银行对账。
- 退货退款和业务反冲。

## 015 退货退款与业务反冲基础模块完成情况

任务文档：`docs/tasks/015-return-refund-reversal-foundation.md`。

接口契约：`docs/api/return-refund-reversal-api.md`。

测试计划：`docs/testing/return-refund-reversal-test-plan.md`。

视觉分析：`docs/testing/return-refund-reversal-visual-audit/notes.md`。

实施计划：`docs/superpowers/plans/2026-07-05-return-refund-reversal-implementation-plan.md`。

设计规格：`docs/superpowers/specs/2026-07-05-return-refund-reversal-design.md`。

核心原则：

- 已过账原单是业务事实，不得直接改写或删除。
- 退货、退款、退料、补料、冲减和反冲必须形成独立业务记录。
- 草稿反向单不影响库存、往来、成本和报表。
- 已过账反向单按自身业务日期进入经营数据，不回写原单期间。
- 报表同时展示原发生、反向发生和净额，不能只展示无法解释的净额。

完成范围：

- 销售退货：
  - 基于已过账销售出库行创建退货草稿。
  - 过账后生成销售退货入库库存流水。
  - 冲减关联应收业务余额。
  - 支持部分退货、全量退货、防超退、防重复过账和来源追溯。
- 采购退货：
  - 基于已过账采购入库行创建退货草稿。
  - 过账后生成采购退货出库库存流水。
  - 冲减关联应付业务余额。
  - 支持库存不足拦截、防超退、防重复过账和来源追溯。
- 生产退料与补料：
  - 基于已过账生产领料行创建退料入库。
  - 基于工单物料创建补料出库。
  - 同步生成生产反向或补充库存流水。
  - 影响工单材料消耗和成本归集业务口径。
- 往来冲减与退款记录：
  - 对应收、应付、收款和付款形成业务冲减记录。
  - 控制冲减金额不得超过可冲余额。
  - 区分余额冲减和实际退款业务记录。
- 统一追溯：
  - 原单、原行、反向单据、库存流水、往来冲减和成本影响之间可追溯。
  - 来源权限不足时脱敏，不泄露敏感字段或路由参数。
- 经营报表同步：
  - 销售、采购、库存、生产、成本和往来固定报表增加原发生、反向发生和净额口径。
  - 报表来源追溯可定位到退货、退料、补料、冲减和反冲记录。

015 关键修复和复审结论：

- 修复生产退料、生产补料误作为往来冲减来源的问题。当前生产退料和补料只影响库存与成本，不作为往来冲减来源。
- 修复往来冲减候选来源刷新后可能隐藏提交旧来源的问题。
- 修复报表跨期反向发生口径，已过账反向单按自身业务日期进入报表。
- 修复往来报表本期冲减 item 口径，本期冲减只由 `SETTLEMENT_ADJUSTMENT` 表达。
- 固定五角色最终复审均通过，未发现阻断合入主分支的问题。

015 验证结果：

- 后端全量测试通过：Surefire `19` 个报告文件，`186` tests，`0` failures，`0` errors，`0` skipped。
- 前端全量测试通过：`65` 个测试文件，`454` 条用例通过。
- `npm run typecheck` 通过。
- `npm run build` 通过，仅有既有 Vite chunk 体积提示。
- `git diff --check` 通过。
- 浏览器主路径、权限路径、异常路径通过。
- 视觉分析完成，共 `22` 张 PNG，目录为 `docs/testing/return-refund-reversal-visual-audit/`。
- 当前主分支最终业务提交：`0526c19 记录015阶段最终复审状态`。

015 明确未包含：

- 审批流。
- 正式红字发票。
- 税务处理。
- 银行退款支付通道。
- 总账凭证。
- 会计期间。
- 期间关账。
- 正式成本结转。
- 多组织、多公司、多账套、多币种。
- 高级质量检验。
- 售后维修、换货、返工流程。

## 当前验收证据索引

建议新主代理接手后优先阅读或复核：

- `docs/testing/business-reporting-visual-audit/notes.md`
- `docs/testing/return-refund-reversal-visual-audit/notes.md`
- `docs/testing/business-reporting-test-plan.md`
- `docs/testing/return-refund-reversal-test-plan.md`
- `docs/tasks/014-business-reporting-foundation.md`
- `docs/tasks/015-return-refund-reversal-foundation.md`
- `docs/api/business-reporting-api.md`
- `docs/api/return-refund-reversal-api.md`
- `docs/superpowers/specs/2026-07-04-business-reporting-design.md`
- `docs/superpowers/specs/2026-07-05-return-refund-reversal-design.md`
- `docs/superpowers/plans/2026-07-04-business-reporting-implementation-plan.md`
- `docs/superpowers/plans/2026-07-05-return-refund-reversal-implementation-plan.md`

视觉证据目录：

- `docs/testing/business-reporting-visual-audit/`：014 经营报表，16 张 PNG。
- `docs/testing/return-refund-reversal-visual-audit/`：015 退货退款与业务反冲，22 张 PNG。

## 当前未完成能力

以下能力仍未实现，不能对用户或下一代理宣称已经具备：

- 期间关账。
- 质量检验。
- MRP。
- 生产计划排程。
- 供应商质量评分。
- 高级质量体系。
- 正式总账。
- 凭证。
- 会计科目。
- 会计期间。
- 正式财务结账。
- 正式成本结转。
- 资产负债表。
- 利润表。
- 现金流量表。
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
- 经营大屏。
- 数据仓库。

## 后续工作方向

本报告只说明后续可能方向，不替新主代理和固定五角色子代理做阶段决策。具体下一阶段编号、优先级、范围、验收标准和实施方案，应由新主代理接手后重新探索当前仓库、确认用户最新意图，并组织产品经理、UI 设计师、前端开发、后端开发、测试五角色讨论后决定。

当前可供讨论的方向包括：

- 业务期间与历史数据保护：015 完成后，系统已经能产生、修正和汇总多类经营数据。后续可以讨论是否需要补齐期间级控制，避免历史期间在确认后继续被无约束写入或反向调整。
- 质量检验与库存质量状态：当前库存主要围绕数量和来源追溯。后续可以讨论采购、生产、销售退货等环节是否需要引入待检、合格、不合格等质量状态，为后续可用库存判断打基础。
- 生产计划与物料需求：当前已有 BOM、库存、采购、销售和生产基础数据。后续可以讨论是否进入生产计划或物料需求方向，但应先评估数据可信度、库存状态、需求来源和计划结果可解释性。
- 财务深化：当前已有应收、应付、收付款、往来冲减和经营报表。正式总账、凭证、会计科目、税务、银行对账等属于更深财务方向，是否启动应由用户确认并单独立项。
- 运营体验与验收数据：当前系统功能已经较多，后续也可以围绕业务演示数据、操作路径、空态异常态、权限差异和浏览器验收体验进行整理，但不得把临时验收数据误写入项目代码或文档，除非用户明确要求。

新主代理应避免直接照搬本文形成具体阶段任务。本文不定义“下一阶段必须做什么”，只提供候选方向和背景约束。

## 后续方向讨论原则

- 先确认用户最新目标，再确认仓库真实状态。
- 先组织固定五角色讨论，再决定下一阶段。
- 先定义业务问题，再定义模块名称。
- 先明确范围边界和排除项，再进入设计和实现。
- 不把长期方向直接写成当前阶段承诺。
- 不把候选方向中的任何一项默认视为已经确认。
- 不把本报告中的方向性描述替代任务文档、设计规格、接口契约、测试计划或实施计划。

## 后续阶段边界提醒

以下边界需要新主代理和子代理在后续讨论中持续确认：

- 若讨论期间级控制，应避免直接扩展成正式财务结账。
- 若讨论质量检验，应避免直接扩展成完整质量管理体系。
- 若讨论 MRP 或计划，应避免在库存、质量、需求来源和业务期间规则不清晰时强行生成不可解释的计划结果。
- 若讨论财务深化，应区分经营往来、经营报表与正式会计核算。
- 若讨论验收数据，应区分数据库临时数据和需要提交到仓库的项目资产。

## 接手后的建议行动清单

建议新主代理在全新工作树中按以下顺序执行：

1. 基于 `origin/main` 创建新工作树或新分支。
2. 阅读 `AGENTS.md`。
3. 阅读本文。
4. 运行 `git status --short --branch`。
5. 运行 `git log -1 --decorate --oneline`。
6. 检查 `docs/README.md` 和 `docs/tasks`、`docs/api`、`docs/testing`、`docs/superpowers`。
7. 确认 014 和 015 视觉证据目录存在且截图非空。
8. 如服务仍在运行，检查后端 `http://127.0.0.1:18082/api/health` 和前端 `http://127.0.0.1:5174/`。
9. 若准备启动新阶段，先组织固定五角色讨论，不要直接改代码。
10. 汇总五角色结论后，再创建对应任务文档、设计规格、接口契约、测试计划和实施计划。
11. 文档基线经固定五角色复审后，再拆分实现任务。
12. 每个实现任务完成后执行规格审查、代码质量审查、测试和复验。
13. 阶段完成后必须浏览器验收和视觉分析，再合入主分支。

## 可复制给新主代理的任务目标提示词

```text
你是 QH ERP 项目的新主代理。请先阅读根目录 AGENTS.md 和 docs/handoffs/2026-07-05-project-handoff-015-complete-next-period-close.md，再探索仓库当前结构、任务文档、接口契约、测试计划和视觉证据。当前 main/origin/main 已完成 014 经营报表基础模块和 015 退货退款与业务反冲基础模块。接下来不要直接进入实现，也不要直接照搬交接报告决定阶段方案；请先确认用户最新目标，并组织产品经理、UI 设计师、前端开发、后端开发、测试五个固定角色讨论后续阶段方向。你必须担任项目负责人，只做统筹、派发、等待结果、组织审查、验收判断和汇报；具体产品、UI、前端、后端、测试工作必须优先复用固定五角色子代理，不得反复创建和关闭子代理，不得创建第 6 类角色。后续可讨论的方向包括业务期间与历史数据保护、质量检验与库存质量状态、生产计划与物料需求、财务深化、验收数据和操作体验整理。具体后续阶段目标、范围、排除项、验收标准、任务文档、设计规格、接口契约、测试计划和实施计划，必须由新主代理与固定五角色基于当前仓库和用户最新指令重新形成。所有合入主分支前必须通过自动化测试、本地浏览器验收和视觉分析。
```

## 最终交接结论

QH ERP 当前已经完成到 `015 退货退款与业务反冲基础模块`，并已合入和推送主分支。主分支具备制造业 ERP 的基础业务闭环、固定经营报表和反向业务修正能力。

新主代理接手后的核心任务是保护当前已完成成果，先确认主分支、远端、工作区、验收证据和用户最新目标，再按固定五角色工作模式讨论并确定下一阶段。本文只提供后续方向背景，不替新主代理和子代理确定具体阶段方案。
