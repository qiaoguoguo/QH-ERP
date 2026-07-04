# QH ERP 项目交接报告（销售管理完成版）

## 交接目的

本报告用于让新的项目主代理在接手 QH ERP 后，能够通过探索仓库和阅读本文，快速理解项目要求、项目结构、当前进度、固定协作模式、质量门、运行方式、验收证据、残余风险和下一阶段开发目标。

接手后不能只依赖本报告推进。新主代理必须以当前仓库、`AGENTS.md`、任务文档、接口契约、测试计划、实际命令输出、浏览器运行结果和用户最新指令为准。本报告是当前进度的上下文索引和决策摘要，不替代真实验证。

## 当前状态摘要

- 当前日期：2026-07-04。
- 当前最新远端主分支：`origin/main`。
- 当前最新提交：`f4d442a 记录销售主分支验收部署`。
- 当前工作区：`C:\Users\14567\.codex\worktrees\fa06\qherp`。
- 当前工作区分支：`codex/sales-management-foundation`，当前 HEAD 与 `origin/main` 对齐。
- 本地主分支工作区：`F:\zhangqiao\AI-study\qherp`。
- 当前阶段：采购管理基础模块和销售管理基础模块均已完成并推送主分支。
- 当前本地验收服务：
  - 后端：`http://127.0.0.1:18080/api/health` 返回 `{"service":"qherp-api","status":"UP"}`。
  - 前端：`http://127.0.0.1:5188` 返回 HTTP `200`。
- 当前最新可验收范围：账号权限、基础资料、BOM、库存、生产执行、成本归集、采购管理基础、销售管理基础。
- 当前推荐下一阶段：`013 财务往来基础模块`，重点是应收、应付、收款、付款的业务台账与来源追溯，不是正式总账或完整财务核算。

## 最高优先级项目要求

新主代理接手后第一件事必须阅读根目录 `AGENTS.md`，并遵守其中高优先级规则。

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
| 测试 | Tesla | `019f2b05-14b0-7ea3-b77d-9f3f0e490f07` |
| 前端开发 | Lovelace | `019f2b08-ce65-77a1-a680-6e844a6ee893` |
| 产品经理 | Galileo | `019f2b1f-a5aa-7ee0-84aa-90c376730162` |
| UI 设计师 | Lagrange | `019f2b52-8381-7f11-9e8c-a96f76b99e27` |
| 后端开发 | Godel | `019f2bdf-05c0-7980-aa85-2c11d4cbba49` |

必须强调：

- 不得为每个任务反复关闭和新建子代理。
- 不得创建第 6 类角色。
- 不得创建“规格审查代理”“代码质量审查代理”等临时新角色。
- 规格审查和代码质量审查必须由 5 个既定角色中的合适角色承担。
- 如果某个角色线程失效，只能创建同角色替代子代理，并记录替代原因。
- 前端开发 Lovelace 曾出现“自己开发 Task5-7 又自己审查”的不合理情况，后续前端代码质量审查必须由 Tesla 或其他合适固定角色独立承担，不能让实现者自己的审查替代独立审查。

### `/goal` 派发规则

向子代理派发任务必须使用 `/goal`，并明确写入：

- 使用哪个固定角色。
- 该角色承担实现、讨论、审查还是复审职责。
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

- `apps/api/pom.xml`：后端 Maven 配置，Java 21，Spring Boot。
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
- `apps/api/src/main/resources/db/migration`：Flyway 数据库迁移，当前已到 `V9__sales_management_schema.sql`。
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
- Spring Boot。
- Spring Security。
- Spring Data JPA。
- JdbcTemplate。
- Flyway。
- PostgreSQL。
- OpenAPI。
- JUnit。
- Testcontainers。

启动 PostgreSQL：

```powershell
docker compose up -d postgres
```

后端本地容器运行示例：

```powershell
docker run --rm `
  --name qherp-api-local `
  -p 18080:8080 `
  -e QHERP_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:15432/qherp `
  -e QHERP_DATASOURCE_USERNAME=qherp `
  -e QHERP_DATASOURCE_PASSWORD=qherp_dev_password `
  -v "${PWD}/apps/api:/workspace" `
  -v qherp-maven-repo:/root/.m2 `
  -w /workspace `
  maven:3.9.9-eclipse-temurin-21 `
  mvn spring-boot:run
```

前端运行示例：

```powershell
cd apps/web
npm run dev -- --host 0.0.0.0 --port 5188 --strictPort --force
```

默认管理员账号：

- 用户名：`admin`
- 密码：`Qherp@2026!`

常用验证命令：

```powershell
git status --short --branch
git diff --check
```

```powershell
cd apps/web
npm test
npm run typecheck
npm run build
```

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

```powershell
docker ps -a --filter "label=org.testcontainers" --format "{{.ID}} {{.Image}} {{.Status}} {{.Names}}"
```

注意：

- 本机 Java 版本可能不满足后端要求，后端测试优先使用 Docker JDK 21。
- Maven 依赖下载曾遇到网络波动，建议继续挂载 `qherp-maven-repo` 缓存卷。
- `mvn -q test` 期间可能出现 Ryuk 禁用、SpringDoc、Mockito 动态 agent、已关闭 Hikari 连接池相关 warning；最终判断以 Maven 退出码和 Surefire 汇总为准。

## 已完成进度

### 0. 项目启动、技术和工程骨架

已完成项目启动基线、技术选型、前后端工程骨架、本地 PostgreSQL、后端健康检查、前端测试和构建基础。

关键文档：

- `docs/tasks/000-project-startup-baseline.md`
- `docs/tasks/001-technical-foundation.md`
- `docs/tasks/002-engineering-skeleton.md`
- `docs/tasks/003-project-readiness-decisions.md`
- `docs/architecture/technology-decision.md`
- `docs/ops/local-development.md`

### 1. 账号与权限基础模块

已完成登录、退出、当前用户、用户管理、角色管理、权限分配、菜单、路由、按钮、接口鉴权、审计日志、只读和无权限用户验收、浏览器视觉分析。

关键文档：

- `docs/tasks/004-account-permission-foundation.md`
- `docs/api/account-permission-api.md`
- `docs/testing/account-permission-test-plan.md`
- `docs/testing/account-permission-visual-audit/notes.md`

### 2. 基础资料与物料管理

已完成计量单位、仓库、供应商、客户、物料分类、物料档案、启停用、引用校验、权限验收和浏览器视觉分析。

关键文档：

- `docs/tasks/005-master-data-material-foundation.md`
- `docs/api/master-data-material-api.md`
- `docs/testing/master-data-material-test-plan.md`
- `docs/testing/master-data-material-visual-audit/notes.md`

### 3. BOM 管理

已完成 BOM 列表、详情、新增、编辑草稿、复制版本、启用、停用、明细用料维护、启用版本唯一、物料引用校验、循环引用校验、权限控制、审计记录和浏览器视觉分析。

关键文档：

- `docs/tasks/006-bom-management-foundation.md`
- `docs/api/bom-management-api.md`
- `docs/testing/bom-management-test-plan.md`
- `docs/testing/bom-management-visual-audit/notes.md`

### 4. 仓库与库存基础能力

已完成库存余额查询、库存变动流水查询、期初库存草稿与过账、库存调整草稿与过账、库存数量不可为负、同一仓库物料期初只允许过账一次、库存变动追溯、权限控制、审计记录和浏览器视觉分析。

关键文档：

- `docs/tasks/007-inventory-management-foundation.md`
- `docs/api/inventory-management-api.md`
- `docs/testing/inventory-management-test-plan.md`
- `docs/testing/inventory-management-visual-audit/notes.md`

### 5. 生产执行基础能力

已完成生产工单列表、详情、新增、编辑草稿、发布、完成、取消、BOM 用料快照、生产领料、生产报工、完工入库、领料扣减库存、完工入库增加库存、生产来源库存流水追溯、角色权限控制、后端鉴权、审计记录、浏览器功能验收和视觉分析。

关键文档：

- `docs/tasks/008-production-execution-foundation.md`
- `docs/api/production-execution-api.md`
- `docs/testing/production-execution-test-plan.md`
- `docs/testing/production-execution-visual-audit/notes.md`

### 6. 成本归集基础记录

已完成成本记录列表、查询、详情、手工成本记录、生产领料自动材料数量口径成本来源记录、生产报工自动人工数量口径成本来源记录、完工入库产出数量追溯、生产工单详情成本归集分区、权限控制、审计和浏览器视觉分析。

已确认边界：

- 成本归集一期只做业务记录与来源追溯。
- 不承诺正式财务核算结果。
- 不做总账、凭证、会计期间、结账、标准成本、移动加权、先进先出、成本差异分析或制造费用自动分配。

关键文档：

- `docs/tasks/009-cost-collection-foundation.md`
- `docs/api/cost-collection-api.md`
- `docs/testing/cost-collection-test-plan.md`
- `docs/testing/cost-collection-visual-audit/notes.md`
- `docs/superpowers/specs/2026-07-03-cost-collection-design.md`
- `docs/superpowers/plans/2026-07-03-cost-collection-implementation-plan.md`

### 7. 一期生产主链路端到端闭环验收与稳定化

已完成从基础资料、物料、BOM、期初库存、生产工单、领料、报工、完工入库到成本归集的完整闭环，并集中验证权限一致、数据追溯、异常受控和视觉分析。

关键文档：

- `docs/tasks/010-phase-one-e2e-stabilization.md`
- `docs/testing/phase-one-e2e-test-plan.md`
- `docs/testing/phase-one-e2e-execution-record.md`
- `docs/testing/phase-one-e2e-defects.md`
- `docs/testing/phase-one-e2e-visual-audit/notes.md`
- `docs/superpowers/plans/2026-07-03-phase-one-e2e-stabilization-implementation-plan.md`

### 8. 采购管理基础模块

已完成采购订单、采购入库、库存增加、`PURCHASE_RECEIPT` 库存流水、采购订单/采购入库/库存流水三方追溯、权限控制、审计、浏览器功能验收和视觉分析。

已确认边界：

- 本阶段只做采购业务基础和库存入库追溯。
- 不做应付、发票、付款、税务、审批、MRP、采购退货、质检入库、暂估入库或正式财务凭证。

关键文档：

- `docs/tasks/011-procurement-management-foundation.md`
- `docs/api/procurement-management-api.md`
- `docs/testing/procurement-management-test-plan.md`
- `docs/testing/procurement-management-visual-audit/notes.md`
- `docs/superpowers/specs/2026-07-04-procurement-management-design.md`
- `docs/superpowers/plans/2026-07-04-procurement-management-implementation-plan.md`

### 9. 销售管理基础模块

已完成销售订单、销售出库、库存扣减、`SALES_SHIPMENT` 库存流水、销售订单/销售出库/库存流水三方追溯、权限控制、审计、浏览器功能验收和视觉分析。

已确认边界：

- 本阶段只做销售订单、销售出库、库存扣减和来源追溯。
- 不做应收、发票、收款、税务、正式收入确认、审批、MRP、销售退货、发运物流、报表看板或正式财务凭证。
- 销售订单明细只允许启用的 `FINISHED_GOOD` 和 `SEMI_FINISHED`。
- 启用的 `RAW_MATERIAL` 和 `AUXILIARY` 本阶段不可销售。
- 销售单价只作为销售业务记录字段，不形成应收、税额、收入确认或正式财务凭证。

关键文档：

- `docs/tasks/012-sales-management-foundation.md`
- `docs/api/sales-management-api.md`
- `docs/testing/sales-management-test-plan.md`
- `docs/testing/sales-management-visual-audit/notes.md`
- `docs/superpowers/specs/2026-07-04-sales-management-design.md`
- `docs/superpowers/plans/2026-07-04-sales-management-implementation-plan.md`

销售阶段重要事实：

- 用户指出 Task5-7 前端实现和代码质量审查都由 Lovelace 完成，不合理。
- 主代理已改派固定测试角色 Tesla 对 Task5-7 前端代码做独立复审。
- Tesla 首轮发现库存流水页面未从 `movementType=SALES_SHIPMENT` 查询参数初始化筛选。
- Lovelace 修复后，Tesla 复审 `APPROVED`。
- Task11 最终阶段中，前端独立代码质量最终复核仍由 Tesla 承担，Lovelace 只做实现交接，不做自己的最终质量结论。

销售阶段最终验证：

- 后端 Docker Maven/Testcontainers 全量 `mvn -q test` 退出码 `0`。
- Surefire 汇总 16 个报告文件、125 个测试、0 失败、0 错误、0 跳过。
- 前端 `npm test` 45 个测试文件、317 个测试通过。
- `npm run typecheck` 通过。
- `npm run build` 通过，只有既有 Vite chunk size warning。
- `git diff --check` 无输出。
- Testcontainers 残留检查无输出。
- 19 张销售视觉截图已保存到 `docs/testing/sales-management-visual-audit/`。
- 原 `19-sales-shipment-mobile-form.png` 窄屏裁切严重问题已修复并复审通过。
- 本地验收服务已从主分支启动，前端 `http://127.0.0.1:5188`，后端 `http://127.0.0.1:18080/api/health`。

## 当前已知问题和残余风险

当前没有未解决的阻断缺陷或严重缺陷。

已记录但不阻断的后续关注项：

- 窄屏侧栏未折叠会导致业务区下推，采购和销售视觉分析均记录为一般体验优化。
- 库存流水宽表列密度较高，桌面窄视口下依赖横向滚动或较宽视口截图支撑来源追溯。
- Vite 构建存在既有 chunk size warning，当前不影响构建成功。
- 后端测试中可能出现已关闭测试数据库连接池的 Hikari warning，当前以 Maven 退出码和 Surefire 汇总为准。
- `GlobalExceptionHandler` 将非法 JSON 或 decimal 解析失败统一映射为 `400 VALIDATION_ERROR`，当前全量测试通过且规格接受；后续如果某模块依赖更细错误语义，需要重新评估。
- 销售 GET 权限中 `/api/admin/sales/orders/**` 是 broad GET 匹配，当前没有可利用控制器路由；后续如果新增订单下级 GET 路由，必须重新校准权限。
- 采购、销售均未做真实并发压力测试；现有事务锁和库存域 `for update` 设计经功能测试可接受，后续并发要求提高时应补压测。
- 当前本地验收数据库中的浏览器验收数据是运行态数据，不是迁移种子；重建数据库后不会自动存在。

## 下一阶段开发目标

### 推荐阶段名称

建议下一阶段命名为：财务往来基础模块。

建议任务编号：`013-financial-settlement-foundation`。

### 为什么下一阶段应做财务往来基础

当前系统已经完成：

```text
供应商 -> 采购订单 -> 采购入库 -> 库存增加 -> 生产领料 -> 报工 -> 完工入库 -> 成本归集
```

以及：

```text
客户 -> 销售订单 -> 销售出库 -> 库存扣减 -> 销售来源库存流水 -> 来源追溯
```

采购和销售基础完成后，系统已经有可靠的采购来源和销售来源。下一步最自然的业务断点是财务往来：采购入库之后企业会形成对供应商的应付业务，销售出库之后企业会形成对客户的应收业务。

推荐先做财务往来基础，而不是直接做报表或总账，理由是：

- 报表需要稳定的业务来源和往来口径，否则只能做浅层统计。
- 总账、正式凭证、税务和结账规则复杂度高，当前基础还不适合一次性引入。
- 应收、应付、收款、付款是采购和销售之后的自然闭环，可以先作为业务台账与来源追溯沉淀。
- 该阶段可以继续复用已有权限、审计、列表、表单、详情、来源追溯、浏览器验收和视觉分析模式。

### 下一阶段核心目标

建立制造业 ERP 财务往来基础能力，让有权限用户能够基于销售出库和采购入库形成应收、应付业务记录，并记录收款、付款结算过程。

本阶段目标不是正式财务核算，而是建立可信的往来业务台账：

- 销售出库可追溯到应收记录。
- 采购入库可追溯到应付记录。
- 应收可记录收款并更新未收金额。
- 应付可记录付款并更新未付金额。
- 应收、应付、收款、付款都具备权限、审计、异常控制和浏览器验收证据。

### 建议纳入范围

应收基础：

- 应收记录列表、详情、创建和编辑草稿。
- 基于已过账销售出库生成或创建应收记录。
- 应收记录确认。
- 应收记录取消或关闭。
- 应收金额、已收金额、未收金额。
- 来源销售出库和来源销售订单追溯。
- 客户、业务日期、到期日期、币种口径预留但一期可固定人民币。

收款基础：

- 基于已确认应收记录创建收款记录。
- 收款草稿、确认或过账。
- 收款后更新应收已收金额和未收金额。
- 支持部分收款和全部收款。
- 收款记录可追溯到应收记录、销售出库和销售订单。

应付基础：

- 应付记录列表、详情、创建和编辑草稿。
- 基于已过账采购入库生成或创建应付记录。
- 应付记录确认。
- 应付记录取消或关闭。
- 应付金额、已付金额、未付金额。
- 来源采购入库和来源采购订单追溯。
- 供应商、业务日期、到期日期、币种口径预留但一期可固定人民币。

付款基础：

- 基于已确认应付记录创建付款记录。
- 付款草稿、确认或过账。
- 付款后更新应付已付金额和未付金额。
- 支持部分付款和全部付款。
- 付款记录可追溯到应付记录、采购入库和采购订单。

权限：

- 财务往来菜单。
- 应收查看、新建、编辑、确认、取消、关闭。
- 收款查看、新建、编辑、确认或过账。
- 应付查看、新建、编辑、确认、取消、关闭。
- 付款查看、新建、编辑、确认或过账。
- 管理员、财务角色、销售角色、采购角色、只读用户、无权限用户的权限差异。
- 后端接口鉴权必须作为最终安全边界。

审计：

- 应收创建、更新、确认、取消、关闭。
- 收款创建、更新、确认或过账。
- 应付创建、更新、确认、取消、关闭。
- 付款创建、更新、确认或过账。

浏览器验收和视觉分析：

- 应收列表、表单、详情。
- 收款表单、详情。
- 应付列表、表单、详情。
- 付款表单、详情。
- 销售出库到应收追溯。
- 采购入库到应付追溯。
- 应收收款进度和应付付款进度。
- 权限差异。
- 异常提示。
- 空状态。
- 桌面和窄屏视口。

### 下一阶段明确排除范围

本阶段不得纳入：

- 正式总账。
- 会计凭证。
- 会计科目体系。
- 会计期间。
- 结账。
- 税务申报。
- 增值税专用发票或发票生命周期完整管理。
- 发票认证、红冲、作废。
- 银行对账。
- 现金日记账。
- 费用报销。
- 固定资产。
- 工资薪酬。
- 成本结转。
- 收入确认准则。
- 多币种汇兑损益。
- 多组织、多公司、多账套、多租户。
- 复杂信用额度和账期风控。
- BI 报表或经营看板。

如果用户要求加入上述能力，必须先记录变更原因、影响范围、涉及文件、验收影响和最终决策，不得直接扩大阶段范围。

### 建议状态机

应收记录建议状态：

- `DRAFT`：草稿，可编辑、可取消。
- `CONFIRMED`：已确认，可收款。
- `PARTIALLY_RECEIVED`：部分收款。
- `RECEIVED`：已收清。
- `CLOSED`：已关闭。
- `CANCELLED`：已取消。

收款记录建议状态：

- `DRAFT`：草稿，可编辑。
- `POSTED`：已过账或已确认，不可编辑。
- `CANCELLED`：已取消，若本阶段不做反过账可不支持。

应付记录建议状态：

- `DRAFT`：草稿，可编辑、可取消。
- `CONFIRMED`：已确认，可付款。
- `PARTIALLY_PAID`：部分付款。
- `PAID`：已付清。
- `CLOSED`：已关闭。
- `CANCELLED`：已取消。

付款记录建议状态：

- `DRAFT`：草稿，可编辑。
- `POSTED`：已过账或已确认，不可编辑。
- `CANCELLED`：已取消，若本阶段不做反过账可不支持。

状态机必须在五角色讨论中最终确认。若范围过大，可以把阶段拆成：

- `013 应收与收款基础模块`。
- `014 应付与付款基础模块`。

但默认推荐仍是先以“财务往来基础模块”组织讨论，再由五角色判断是否拆分。

### 建议接口和数据模型方向

后端建议新增独立财务往来域，避免把应收应付逻辑塞入销售、采购或库存服务：

- `fin_receivable`
- `fin_receivable_line` 或 `fin_receivable_source`
- `fin_receipt`
- `fin_receipt_line`
- `fin_payable`
- `fin_payable_line` 或 `fin_payable_source`
- `fin_payment`
- `fin_payment_line`

建议接口前缀：

- `/api/admin/finance/receivables`
- `/api/admin/finance/receivables/{id}`
- `/api/admin/finance/receivables/{id}/confirm`
- `/api/admin/finance/receivables/{id}/cancel`
- `/api/admin/finance/receivables/{id}/close`
- `/api/admin/finance/receivables/{id}/receipts`
- `/api/admin/finance/receipts/{id}`
- `/api/admin/finance/receipts/{id}/post`
- `/api/admin/finance/payables`
- `/api/admin/finance/payables/{id}`
- `/api/admin/finance/payables/{id}/confirm`
- `/api/admin/finance/payables/{id}/cancel`
- `/api/admin/finance/payables/{id}/close`
- `/api/admin/finance/payables/{id}/payments`
- `/api/admin/finance/payments/{id}`
- `/api/admin/finance/payments/{id}/post`

权限编码建议：

- `finance:receivable:view/create/update/confirm/cancel/close`
- `finance:receipt:view/create/update/post`
- `finance:payable:view/create/update/confirm/cancel/close`
- `finance:payment:view/create/update/post`

具体接口契约必须在新阶段文档中正式定义。

### 建议验收主路径

应收路径：

1. 管理员登录。
2. 准备已过账销售出库和销售订单。
3. 基于销售出库创建应收草稿。
4. 编辑应收草稿。
5. 确认应收。
6. 创建第一笔部分收款并过账。
7. 确认应收已收金额和未收金额正确。
8. 创建第二笔收款完成收清。
9. 确认应收状态进入已收清。
10. 从应收追溯销售出库和销售订单。
11. 从销售出库详情查看应收记录。

应付路径：

1. 准备已过账采购入库和采购订单。
2. 基于采购入库创建应付草稿。
3. 编辑应付草稿。
4. 确认应付。
5. 创建第一笔部分付款并过账。
6. 确认应付已付金额和未付金额正确。
7. 创建第二笔付款完成付清。
8. 确认应付状态进入已付清。
9. 从应付追溯采购入库和采购订单。
10. 从采购入库详情查看应付记录。

权限路径：

- 财务角色可维护应收、收款、应付、付款。
- 销售角色可查看销售相关应收，但不允许确认收款，具体口径需五角色讨论确认。
- 采购角色可查看采购相关应付，但不允许确认付款，具体口径需五角色讨论确认。
- 仓库角色不应拥有财务确认或收付款权限。
- 只读用户只能查看。
- 无权限用户无菜单、无路由、接口拒绝。
- 未登录用户跳转登录或返回未认证。

### 建议异常矩阵

至少覆盖：

- 未登录访问财务往来页面或接口。
- 无权限访问页面或接口。
- 基于未过账销售出库创建应收。
- 基于未过账采购入库创建应付。
- 重复基于同一来源创建应收或应付。
- 应收金额为 0、负数或超精度。
- 应付金额为 0、负数或超精度。
- 收款金额为 0、负数或超出未收金额。
- 付款金额为 0、负数或超出未付金额。
- 已收清应收继续收款。
- 已付清应付继续付款。
- 已确认或已收款应收非法编辑。
- 已确认或已付款应付非法编辑。
- 已过账收款或付款非法编辑。
- 异常操作不得产生部分写入。
- 业务来源被删除或状态变化时必须受控失败，不能产生孤儿记录。

### 建议文档产物

启动阶段后建议新增：

- `docs/tasks/013-financial-settlement-foundation.md`
- `docs/api/financial-settlement-api.md`
- `docs/testing/financial-settlement-test-plan.md`
- `docs/superpowers/specs/2026-07-04-financial-settlement-design.md`
- `docs/superpowers/plans/2026-07-04-financial-settlement-implementation-plan.md`

同时建议更新：

- `docs/README.md`
- `docs/product/product-decisions.md`
- `docs/product/business-flow.md`
- `docs/testing/acceptance-criteria.md`
- 必要时更新 `docs/testing/test-plan.md`

### 下一阶段质量门

财务往来基础模块合入主分支前至少必须满足：

- 5 个固定角色讨论完成并记录结论。
- 阶段任务文档、设计规格、接口契约、测试计划、实施计划完整。
- 后端迁移和全量测试通过。
- 前端全量测试、类型检查和构建通过。
- 本地部署健康检查通过。
- 管理员浏览器主路径通过。
- 权限路径通过。
- 异常路径通过。
- 应收、应付、收款、付款金额累计数据断言通过。
- 销售出库、应收、收款之间可追溯。
- 采购入库、应付、付款之间可追溯。
- 视觉分析截图和结论归档。
- `git diff --check` 通过。
- 无阻断缺陷。
- 规格审查和代码质量审查通过。

## 新主代理接手后的建议动作

1. 读取 `AGENTS.md`，确认固定 5 角色工作模式。
2. 执行 `git status --short --branch`，确认当前分支和工作区是否干净。
3. 读取本报告，并确认这是最新交接报告。
4. 读取 `docs/README.md`、`docs/product/phase-one-scope.md`、`docs/product/product-decisions.md`、`docs/product/business-flow.md`。
5. 读取 `docs/tasks/011-procurement-management-foundation.md` 和 `docs/tasks/012-sales-management-foundation.md`，确认采购和销售已完成。
6. 读取 `docs/testing/procurement-management-test-plan.md` 和 `docs/testing/sales-management-test-plan.md`，确认最终质量门和主分支验收部署记录。
7. 重新运行后端健康检查和前端可访问检查。
8. 启动 `013-financial-settlement-foundation` 前，先组织 5 个固定角色讨论。
9. 汇总讨论结论，形成财务往来基础阶段目标、边界、风险、验收标准和实施计划。
10. 先产出文档基线，再进入实现。
11. 每个实现任务严格按 `/goal` 派发给对应角色。
12. 每个实现任务完成后先规格审查，再代码质量审查。
13. 前端实现任务的代码质量审查不得由 Lovelace 自审替代，应由 Tesla 或其他合适固定角色独立复核。
14. 阶段验收必须包含自动化测试、本地部署、浏览器主路径、权限路径、异常路径、视觉分析和最终质量门。

## 不得做的事

- 不得忘记对用户可见回复开头称呼“爸爸”。
- 不得跳过 5 个角色的新阶段讨论直接编码。
- 不得让主代理自行实现财务往来模块。
- 不得把财务往来基础阶段扩大到总账、凭证、税务、完整发票、结账、多账套或 BI 报表。
- 不得在未确认拆分策略时一次性实现完整财务系统。
- 不得把销售单价或采购单价直接解释为正式收入、成本、税额或会计凭证。
- 不得绕过后端接口鉴权，只依赖前端按钮隐藏。
- 不得只用接口通过替代浏览器验收。
- 不得省略视觉分析。
- 不得把当前本地验收库样例数据当作迁移种子。
- 不得在有阻断缺陷时合入或推送主分支。
- 不得把旧交接报告中“下一阶段是采购”的结论当作当前依据。
- 不得把旧截图、旧测试结果或旧服务状态当作当前证据。

## 关键参考文档

- `AGENTS.md`
- `docs/README.md`
- `docs/product/phase-one-scope.md`
- `docs/product/product-decisions.md`
- `docs/product/business-flow.md`
- `docs/tasks/010-phase-one-e2e-stabilization.md`
- `docs/tasks/011-procurement-management-foundation.md`
- `docs/tasks/012-sales-management-foundation.md`
- `docs/api/procurement-management-api.md`
- `docs/api/sales-management-api.md`
- `docs/testing/procurement-management-test-plan.md`
- `docs/testing/sales-management-test-plan.md`
- `docs/testing/procurement-management-visual-audit/notes.md`
- `docs/testing/sales-management-visual-audit/notes.md`
- `docs/superpowers/plans/2026-07-04-procurement-management-implementation-plan.md`
- `docs/superpowers/plans/2026-07-04-sales-management-implementation-plan.md`
- `docs/architecture/technology-decision.md`
- `docs/ops/local-development.md`
- `docs/ops/deployment-acceptance.md`

## 交接结论

QH ERP 当前已经完成一期生产主链路，以及一期后的采购管理基础和销售管理基础。系统已经具备账号权限、基础资料、物料、BOM、库存、生产执行、成本归集、采购入库和销售出库的基础闭环。库存变动已经可以从期初/调整、生产领料、完工入库、采购入库、销售出库等多个来源追溯到业务单据。

当前主分支 `origin/main` 已更新到 `f4d442a`，销售管理基础模块已完成主分支合入、推送和本地验收服务启动。后端健康检查和前端访问均可用。

下一阶段推荐推进 `013 财务往来基础模块`。该阶段应把采购和销售已经形成的业务来源继续向财务往来延伸，建立应收、应付、收款、付款的业务台账和来源追溯。必须保持边界清晰：只做往来业务记录、结算进度、权限、审计和追溯，不做正式总账、凭证、税务、结账、多账套或完整财务核算。

新主代理接手后，应先组织 5 个固定角色讨论财务往来阶段，形成文档基线和实施计划，再进入开发。只有完成自动化测试、本地部署、浏览器功能验收、权限验收、异常验收、视觉分析和质量审查后，才能判断是否合入主分支并通知用户验收。
