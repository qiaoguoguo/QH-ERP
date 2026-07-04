# QH ERP 项目交接报告（一期完成版）

## 交接目的

本报告用于让新的项目主代理在接手 QH ERP 后，能够通过探索当前仓库和阅读本文，快速理解项目要求、当前结构、已完成进度、固定工作模式、质量门、运行方式、验收证据和下一阶段开发目标。

接手后不能只依赖本报告推进，必须以当前仓库文件、命令输出、运行结果和用户最新指令为准。本报告是最新交接索引和上下文压缩材料，不替代 `AGENTS.md`、任务文档、接口契约、测试计划、执行记录、视觉分析和实际验证。

## 当前状态摘要

- 当前日期：2026-07-04。
- 当前工作区：`C:\Users\14567\.codex\worktrees\ffe7\qherp`。
- 当前分支：`codex/phase-one-e2e-stabilization`，当前分支与 `origin/main` 对齐。
- 当前最新提交：`5d53155 完成一期端到端稳定化验收`。
- 当前阶段：一期目标已完成，生产主链路端到端闭环验收与稳定化已收口。
- 已完成模块：账号与权限、基础资料与物料、BOM、库存基础、生产执行、成本归集基础记录、一期端到端稳定化。
- 当前后端健康检查：`http://127.0.0.1:18080/api/health` 返回 `{"status":"UP","service":"qherp-api"}`。
- 当前前端验收地址：`http://127.0.0.1:5178/` 返回 HTTP `200`。
- 当前本地验收库中有一套电气制造业样例数据：5 个用户、5 个角色、81 个权限点、10 个物料、1 个 BOM、10 条库存余额、1 张已完成生产工单、13 条成本记录。该数据是验收环境数据，不是数据库迁移种子。

## 项目定位与核心要求

QH ERP 是面向制造业单公司内部使用的 Web ERP 系统，定位不是零售业、门店收银、电商或通用轻量进销存。

长期方向需要覆盖常规 ERP 能力：

- 基础资料。
- 采购。
- 销售。
- 库存。
- 生产。
- 财务。
- 报表。

当前产品重心是生产管理，尤其重视以下业务链路：

- 物料管理。
- BOM。
- 库存。
- 生产工单。
- 领料。
- 报工。
- 完工入库。
- 成本归集。

一期目标已经完成。下一步不是继续扩展一期内部小功能，而是进入一期后的第一个新业务模块：采购管理基础模块。

## 固定工作模式

新主代理必须先阅读并遵守根目录 `AGENTS.md`。以下约束是高优先级交接重点。

### 对用户沟通

- 每次对用户可见回复开头必须先称呼“爸爸”。
- 所有智能体与用户沟通必须使用中文。
- 除路径、命令、接口名、库名、协议名等不可翻译技术标识外，不使用非中文表达。

### 主代理职责

主代理是项目负责人，只负责：

- 项目统筹。
- 任务拆解。
- 上下文提供。
- 任务派发。
- 等待结果。
- 组织审查。
- 验收判断。
- 向用户汇报。

主代理默认不直接承担产品设计、UI 设计、前端开发、后端开发或测试执行工作。涉及具体设计、开发、测试或审查时，应优先派发给对应角色子代理。

### 固定 5 个角色子代理

项目固定只有 5 个角色：

- 产品经理。
- UI 设计师。
- 前端开发。
- 后端开发。
- 测试。

线程 ID 可以变化，职责边界以角色为准。不得为每个任务随意创建第 6 类角色，不得额外创建“规格审查代理”“代码质量审查代理”等新角色。规格审查和代码质量审查必须由 5 个既定角色中的合适角色承担。

向子代理派发任务必须使用 `/goal`，并明确：

- 角色。
- 任务目标。
- 输入上下文。
- 职责边界。
- 预期成果。
- 验收标准。
- 禁止发散范围。

### 新阶段推进流程

任何新阶段开始前，必须先执行：

1. 组织 5 个固定角色分别从产品、UI、前端、后端、测试视角讨论。
2. 主代理汇总 5 个角色结论。
3. 形成阶段目标、范围边界、关键风险、验收标准和实施计划。
4. 计划未形成前不得进入实现。
5. 计划确认后再拆分任务，逐项派发给对应实现角色。
6. 每个实现任务完成后先做规格审查。
7. 规格审查通过后再做代码质量审查。
8. 审查失败必须派发修复任务并复审。
9. 主代理最终复核当前证据，再判断是否进入主分支阶段验收。

### 分支与验收规则

- 主分支用于用户阶段验收，应保持稳定、可部署、可浏览器访问。
- 未完成、未验证或不满足验收标准的功能不得合入或推送主分支。
- 阶段成果必须能在浏览器中实际查看和操作。
- 浏览器验收必须包含视觉分析，截图必须来自真实浏览器。
- 视觉分析记录目录必须按模块建立，例如 `docs/testing/<模块标识>-visual-audit/`。
- 存在阻断缺陷时，不得合入主分支，不得通知用户验收。

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
- `apps/api/src/main/java/com/qherp/api/system/user`：用户管理。
- `apps/api/src/main/java/com/qherp/api/system/role`：角色管理。
- `apps/api/src/main/java/com/qherp/api/system/permission`：权限管理。
- `apps/api/src/main/java/com/qherp/api/system/master`：基础资料。
- `apps/api/src/main/java/com/qherp/api/system/bom`：BOM 管理。
- `apps/api/src/main/java/com/qherp/api/system/inventory`：库存管理。
- `apps/api/src/main/java/com/qherp/api/system/production`：生产执行。
- `apps/api/src/main/java/com/qherp/api/system/cost`：成本归集。
- `apps/api/src/main/resources/db/migration`：Flyway 数据库迁移，当前从 `V1` 到 `V7`。
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
- Flyway。
- PostgreSQL。
- OpenAPI。
- JUnit。
- Testcontainers。

常用运行方式：

```powershell
docker compose up -d postgres
```

后端本地容器运行示例：

```powershell
$api=(Resolve-Path 'apps/api').Path.Replace('\','/')
docker run --rm `
  --name qherp-api-local `
  -p 18080:8080 `
  -e QHERP_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:15432/qherp `
  -e QHERP_DATASOURCE_USERNAME=qherp `
  -e QHERP_DATASOURCE_PASSWORD=qherp_dev_password `
  -v "${api}:/workspace" `
  -v qherp-maven-repo:/root/.m2 `
  -w /workspace `
  maven:3.9.9-eclipse-temurin-21 `
  mvn spring-boot:run
```

前端运行示例：

```powershell
cd apps/web
npx vite --host 127.0.0.1
```

默认管理员账号：

- 用户名：`admin`
- 密码：`Qherp@2026!`

## 验证命令

前端：

```powershell
cd apps/web
npm test
npm run typecheck
npm run build
```

后端建议使用 Docker 化 Maven 和 JDK 21：

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

其他常用检查：

```powershell
git status --short --branch
git diff --check
docker ps -a --filter "label=org.testcontainers" --format "{{.ID}} {{.Image}} {{.Status}} {{.Names}}"
```

注意：

- 本机 Java 版本可能不满足后端要求，后端测试优先使用 Docker JDK 21。
- Maven 依赖下载曾遇到网络波动，建议继续挂载 `qherp-maven-repo` 缓存卷。
- 遇到环境或测试失败，应先诊断和修复，不得跳过验证。

## 已完成进度

### 0. 项目启动、技术和工程骨架

已完成：

- 项目启动基线。
- 技术选型决策。
- 前后端工程骨架。
- 本地 PostgreSQL、后端健康检查、前端测试和构建基础。
- 项目开工准备决策。

关键文档：

- `docs/tasks/000-project-startup-baseline.md`
- `docs/tasks/001-technical-foundation.md`
- `docs/tasks/002-engineering-skeleton.md`
- `docs/tasks/003-project-readiness-decisions.md`
- `docs/architecture/technology-decision.md`
- `docs/ops/local-development.md`

### 1. 账号与权限基础模块

已完成：

- 登录、退出、当前用户。
- 用户管理。
- 角色管理。
- 权限分配。
- 菜单、路由、按钮和接口鉴权。
- 审计日志。
- 只读用户与无权限用户验收。
- 浏览器视觉分析。

关键文档：

- `docs/tasks/004-account-permission-foundation.md`
- `docs/api/account-permission-api.md`
- `docs/testing/account-permission-test-plan.md`
- `docs/testing/account-permission-visual-audit/notes.md`

### 2. 基础资料与物料管理

已完成：

- 计量单位。
- 仓库。
- 供应商。
- 客户。
- 物料分类。
- 物料档案。
- 启用、停用、引用校验。
- 权限验收和浏览器视觉分析。

关键文档：

- `docs/tasks/005-master-data-material-foundation.md`
- `docs/api/master-data-material-api.md`
- `docs/testing/master-data-material-test-plan.md`
- `docs/testing/master-data-material-visual-audit/notes.md`

### 3. BOM 管理

已完成：

- BOM 列表、详情、新增、编辑草稿。
- 复制版本。
- 启用、停用。
- 明细用料维护。
- 同一父项启用版本唯一。
- 物料引用校验。
- 可检测循环引用校验。
- 权限控制和审计记录。
- 浏览器视觉分析。

关键文档：

- `docs/tasks/006-bom-management-foundation.md`
- `docs/api/bom-management-api.md`
- `docs/testing/bom-management-test-plan.md`
- `docs/testing/bom-management-visual-audit/notes.md`

### 4. 仓库与库存基础能力

已完成：

- 库存余额查询。
- 库存变动流水查询。
- 期初库存草稿与过账。
- 库存调整草稿与过账。
- 库存数量不可为负。
- 同一仓库物料期初只允许过账一次。
- 库存变动追溯。
- 权限控制和审计记录。
- 浏览器视觉分析。

关键文档：

- `docs/tasks/007-inventory-management-foundation.md`
- `docs/api/inventory-management-api.md`
- `docs/testing/inventory-management-test-plan.md`
- `docs/testing/inventory-management-visual-audit/notes.md`

重要补充：

- 库存管理子菜单显示缺陷已在提交 `b37b403 修复库存管理子菜单显示` 修复。
- 当前权限点包含库存余额、库存变动、库存单据三个菜单节点。

### 5. 生产执行基础能力

已完成：

- 生产工单列表、详情、新增、编辑草稿。
- 工单发布、完成、取消。
- 发布时生成 BOM 用料快照。
- 基于工单创建、编辑和过账生产领料。
- 基于工单创建、编辑和过账生产报工。
- 基于工单创建、编辑和过账完工入库。
- 领料扣减库存。
- 完工入库增加库存。
- 生产来源库存流水追溯。
- 角色权限控制、后端鉴权和审计记录。
- 浏览器功能验收和视觉分析。

关键文档：

- `docs/tasks/008-production-execution-foundation.md`
- `docs/api/production-execution-api.md`
- `docs/testing/production-execution-test-plan.md`
- `docs/testing/production-execution-visual-audit/notes.md`

### 6. 成本归集基础记录

已完成：

- 成本记录列表、查询、详情。
- 手工成本记录创建和编辑。
- 生产领料过账后自动生成材料数量口径成本来源记录。
- 生产报工过账后自动生成人工数量口径成本来源记录。
- 完工入库作为产出数量追溯。
- 生产工单详情成本归集分区。
- 成本记录权限控制和审计。
- 浏览器功能验收和视觉分析。

已明确边界：

- 成本归集一期只做业务记录与来源追溯，不承诺正式财务核算结果。
- 自动材料来源记录不自动计算材料金额。
- 自动人工来源记录不自动计算人工金额。
- 手工记录用于人工金额、制造费用、其他成本或必要材料金额补录。
- 成本记录不得反写库存余额、生产工单状态或生产单据状态。

关键文档：

- `docs/tasks/009-cost-collection-foundation.md`
- `docs/api/cost-collection-api.md`
- `docs/testing/cost-collection-test-plan.md`
- `docs/testing/cost-collection-visual-audit/notes.md`
- `docs/superpowers/specs/2026-07-03-cost-collection-design.md`
- `docs/superpowers/plans/2026-07-03-cost-collection-implementation-plan.md`

关键验证结果：

- 后端全量测试通过。
- 前端全量测试通过，31 个测试文件、207 个用例通过。
- 前端构建通过。
- 本地部署和浏览器功能验收通过。
- 成本视觉分析覆盖 15 张截图，无阻断视觉问题。

### 7. 一期生产主链路端到端闭环验收与稳定化

已完成：

- 从基础资料、物料、BOM、期初库存、生产工单、领料、报工、完工入库到成本归集的完整闭环。
- 管理员浏览器写操作补证。
- 基础资料维护、库存、生产、成本、只读、无权限角色的菜单、路由、按钮和接口一致性验证。
- 未登录、BOM 不可用、库存不足、超领、超报、超完工入库、重复过账、非法成本金额或数量、无效工单手工成本异常验证。
- 库存流水、工单、成本记录、审计记录可追溯验证。
- 浏览器视觉分析。
- 最终质量门验证。

关键文档：

- `docs/tasks/010-phase-one-e2e-stabilization.md`
- `docs/testing/phase-one-e2e-test-plan.md`
- `docs/testing/phase-one-e2e-execution-record.md`
- `docs/testing/phase-one-e2e-defects.md`
- `docs/testing/phase-one-e2e-visual-audit/notes.md`
- `docs/superpowers/plans/2026-07-03-phase-one-e2e-stabilization-implementation-plan.md`

最终验证证据：

- 后端健康检查返回 `UP`。
- 前端核心路由返回 HTTP `200`。
- 前端全量测试：31 个测试文件、208 个用例通过。
- 前端类型检查通过。
- 前端构建通过，Vite 构建 1734 个模块。
- 后端全量测试通过，Surefire 汇总 14 个报告文件、102 个测试、0 失败、0 错误、0 跳过。
- Testcontainers 残留容器检查无输出。
- 视觉目录 `docs/testing/phase-one-e2e-visual-audit/` 包含 51 张 PNG，清单一致、空文件 0、缺失 0、额外 0。
- 缺陷清单当前无阻断、严重或一般缺陷。

## 当前验收环境数据说明

2026-07-04 本地验收库曾按电气制造业样例重新清理并造数，用于让用户通过真实数据理解当前系统业务。当前数据不是迁移脚本或产品种子数据，重建数据库后不会自动存在。

当前样例账号，密码均为 `Qherp@2026!`：

- `demo_readonly`：样例经营只读。
- `demo_warehouse`：样例仓库主管。
- `demo_production`：样例生产班组长。
- `demo_cost`：样例成本会计。

当前样例业务数据摘要：

- 成品：`FG-PLC-CTRL-075 / QH-DK-075 智能电机控制柜`。
- BOM：`BOM-FG-PLC-CTRL-075 / V2026.07`，9 条用料。
- 期初库存单：`INV-OPEN-20260704014215081-004`，已过账。
- 生产工单：`MFG-WO-20260704014215319-008`，状态 `COMPLETED`。
- 领料单：`MFG-ISS-20260704014215571-002`，已过账。
- 报工单：`MFG-RPT-20260704014215839-003`，合格 5 套。
- 完工入库单：`MFG-RCP-20260704014215933-004`，成品仓入库 5 套。
- 成本记录：13 条，含材料自动归集、报工自动归集、人工费、制造费用、质检包装费用。

接手后如要继续使用验收环境，必须先重新运行健康检查和数据检查，不得把上述运行态当作永久状态。

## 当前已知问题和注意事项

- 当前一期功能已收口，交接后不应再把成本归集作为下一阶段目标。
- `docs/handoffs/2026-07-03-project-handoff.md` 已过时，其中“下一阶段是成本归集”的判断不再适用。
- 一期端到端视觉分析记录中，窄屏密集表格需要横向滚动，这是已记录的非阻断体验关注点。
- 成本模块文案和后续设计必须继续避免暗示正式财务核算结果。
- 采购、销售、应收、应付、正式财务、报表、审批、MRP、多组织等均未实现。
- 当前采购阶段尚未启动文档基线；如要推进，必须先走 5 角色讨论和阶段计划。
- 主分支用于阶段验收，不得合入未验证的新模块。

## 下一阶段开发目标

### 推荐阶段名称

建议下一阶段命名为：采购管理基础模块。

建议任务编号：`011-procurement-management-foundation`。

### 为什么下一阶段应做采购

一期已经完成从库存到生产执行再到成本归集的内部闭环，但原材料库存来源仍主要依赖期初和库存调整。对制造业 ERP 来说，这个链路还不够真实。采购管理基础模块可以补齐原材料进入企业的业务入口，使链路变为：

```text
供应商 -> 采购订单 -> 采购入库 -> 库存增加 -> 生产领料 -> 报工 -> 完工入库 -> 成本归集
```

采购先于销售推进的理由：

- 当前最大数据断点是原材料来源，而不是成品出货。
- 生产领料和库存变动需要更真实的采购入库来源。
- 采购入库可以复用已有库存过账与库存流水能力，技术边界清晰。
- 后续销售、应收、应付、正式财务都可以在采购基础稳定后逐步接入。

### 阶段核心目标

建立制造业原材料采购基础能力，让有权限用户能够维护采购订单，并基于采购订单完成采购入库。采购入库过账后必须增加库存余额、生成库存流水，并能从采购订单、采购入库和库存流水之间追溯来源。

本阶段只做采购业务基础和库存入库追溯，不做应付、发票、付款、税务、审批、MRP 或正式财务凭证。

### 建议纳入范围

采购订单：

- 采购订单列表。
- 采购订单详情。
- 新建采购订单草稿。
- 编辑采购订单草稿。
- 确认采购订单。
- 取消采购订单。
- 关闭采购订单。
- 采购订单明细维护。

采购订单明细：

- 供应商。
- 物料。
- 数量。
- 单位。
- 采购单价。
- 预计到货日期。
- 备注。
- 已入库数量和未入库数量。

采购入库：

- 基于采购订单创建采购入库单。
- 采购入库单草稿。
- 采购入库单明细引用采购订单明细。
- 采购入库过账。
- 过账后增加库存余额。
- 过账后生成库存流水。
- 采购订单累计已入库数量更新或可查询。

追溯：

- 采购订单详情可查看入库记录。
- 采购入库详情可查看来源采购订单。
- 库存流水可追溯到采购入库来源。

权限：

- 采购管理菜单。
- 采购订单查看、新建、编辑、确认、取消、关闭。
- 采购入库查看、新建、过账。
- 采购员角色。
- 仓库角色。
- 只读角色。
- 无权限角色。
- 后端接口鉴权必须作为最终安全边界。

审计：

- 采购订单创建、更新、确认、取消、关闭。
- 采购入库创建、更新、过账。
- 权限相关写操作均写审计。

浏览器验收和视觉分析：

- 采购订单列表。
- 采购订单详情。
- 采购订单表单。
- 采购入库表单。
- 采购入库详情。
- 采购订单入库追溯。
- 库存流水采购来源追溯。
- 权限差异。
- 异常和空状态。
- 桌面和窄屏视口。

### 明确排除范围

本阶段不得纳入：

- 应付账款。
- 发票。
- 付款。
- 税务。
- 采购审批流。
- 询价。
- 比价。
- 供应商评分。
- MRP 自动采购建议。
- 多币种。
- 多组织。
- 多公司。
- 多账套。
- 正式财务凭证。
- 总账联动。
- 采购退货。
- 质检入库。
- 到货检验。
- 暂估入库。
- 采购价格策略和价格历史分析。

如用户要求加入上述能力，必须先记录变更原因、影响范围、验收影响和最终决策，不得直接扩大阶段范围。

### 建议状态机

采购订单建议状态：

- `DRAFT`：草稿，可编辑、可取消。
- `CONFIRMED`：已确认，可创建入库，可关闭，不可改关键字段。
- `PARTIALLY_RECEIVED`：部分入库，可继续入库，可关闭。
- `RECEIVED`：全部入库，可关闭。
- `CLOSED`：已关闭，不再允许入库。
- `CANCELLED`：已取消，不允许入库。

采购入库单建议状态：

- `DRAFT`：草稿，可编辑、可删除或取消。
- `POSTED`：已过账，不可编辑，已写库存余额和库存流水。

是否单独区分 `PARTIALLY_RECEIVED` 和 `RECEIVED` 可以在 5 角色讨论中确认。若为降低复杂度，也可让订单保留 `CONFIRMED`，通过已入库数量判断进度，最后手工关闭。

### 建议接口和数据模型方向

后端建议新增独立采购域，避免把采购逻辑塞入库存服务或生产服务：

- `proc_purchase_order`
- `proc_purchase_order_line`
- `proc_purchase_receipt`
- `proc_purchase_receipt_line`

库存流水建议新增来源类型：

- `PURCHASE_RECEIPT`

采购入库过账必须复用库存余额和库存流水的事务规则，不得旁路直接改余额后缺失流水。

建议接口前缀：

- `/api/admin/procurement/orders`
- `/api/admin/procurement/orders/{id}`
- `/api/admin/procurement/orders/{id}/confirm`
- `/api/admin/procurement/orders/{id}/cancel`
- `/api/admin/procurement/orders/{id}/close`
- `/api/admin/procurement/orders/{id}/receipts`
- `/api/admin/procurement/receipts/{id}`
- `/api/admin/procurement/receipts/{id}/post`

具体接口契约应在新阶段文档中正式定义。

### 建议验收主路径

建议使用电气制造业场景：

1. 使用已有供应商、仓库、计量单位、采购型原材料。
2. 创建采购订单，采购铜排、线缆、PLC、变频器或辅料。
3. 确认采购订单。
4. 基于采购订单创建采购入库单。
5. 采购入库过账。
6. 验证库存余额增加。
7. 验证库存流水出现 `PURCHASE_RECEIPT` 来源。
8. 从库存流水追溯到采购入库。
9. 从采购入库追溯到采购订单。
10. 从采购订单查看已入库数量和未入库数量。
11. 用入库后的材料继续创建生产工单并领料，证明采购入库进入生产消耗链路。
12. 验证采购员、仓库、只读、无权限角色的菜单、按钮和接口权限。
13. 验证异常场景。
14. 完成浏览器视觉分析并保存截图证据。

### 建议异常场景

至少覆盖：

- 未登录访问采购接口。
- 无权限访问采购页面和接口。
- 停用供应商不能确认或创建采购订单。
- 停用物料不能创建采购订单明细。
- 停用仓库不能创建或过账采购入库。
- 采购订单不存在。
- 采购订单未确认时不能入库。
- 采购订单已取消或已关闭时不能入库。
- 入库数量为 0 或负数。
- 入库数量超过采购订单未入库数量。
- 重复过账采购入库不得重复增加库存。
- 已过账采购入库不可编辑。
- 采购单价金额精度不合法。
- 来源订单行与入库物料不一致。

### 建议文档产物

启动阶段后建议新增：

- `docs/tasks/011-procurement-management-foundation.md`
- `docs/api/procurement-management-api.md`
- `docs/testing/procurement-management-test-plan.md`
- `docs/superpowers/specs/2026-07-04-procurement-management-design.md`
- `docs/superpowers/plans/2026-07-04-procurement-management-implementation-plan.md`

同时建议更新：

- `docs/README.md`
- `docs/product/product-decisions.md`
- `docs/product/business-flow.md`
- `docs/testing/acceptance-criteria.md`
- 必要时更新 `docs/testing/test-plan.md`

### 下一阶段质量门

采购阶段合入主分支前至少必须满足：

- 5 个固定角色讨论完成并记录结论。
- 阶段任务文档、设计规格、接口契约、测试计划、实施计划完整。
- 后端迁移和全量测试通过。
- 前端全量测试、类型检查和构建通过。
- 本地部署健康检查通过。
- 管理员浏览器主路径通过。
- 权限路径通过。
- 异常路径通过。
- 库存余额和库存流水数据断言通过。
- 采购订单、采购入库、库存流水之间可追溯。
- 视觉分析截图和结论归档。
- `git diff --check` 通过。
- 无阻断缺陷。
- 规格审查和代码质量审查通过。

## 新主代理接手后的建议动作

1. 读取 `AGENTS.md`，确认固定 5 角色工作模式。
2. 执行 `git status --short --branch`，确认当前分支和工作区是否干净。
3. 读取本报告。
4. 读取 `docs/README.md`、`docs/product/phase-one-scope.md`、`docs/product/product-decisions.md`、`docs/product/business-flow.md`。
5. 读取 `docs/tasks/010-phase-one-e2e-stabilization.md` 和 `docs/testing/phase-one-e2e-execution-record.md`，确认一期已完成证据。
6. 读取 `docs/testing/phase-one-e2e-defects.md`，确认当前缺陷清单为空且复审发现项已关闭。
7. 重新运行后端健康检查和前端可访问检查。
8. 启动 `011-procurement-management-foundation` 阶段前，先组织 5 个固定角色讨论。
9. 汇总讨论结论，形成采购阶段目标、边界、风险、验收标准和实施计划。
10. 先产出文档基线，再进入实现。
11. 每个实现任务严格按 `/goal` 派发给对应角色。
12. 每个实现任务完成后先规格审查，再代码质量审查。
13. 阶段验收必须包含自动化测试、本地部署、浏览器主路径、权限路径、异常路径、视觉分析和最终质量门。

## 不得做的事

- 不得忘记对用户可见回复开头称呼“爸爸”。
- 不得跳过 5 个角色的新阶段讨论直接编码。
- 不得让主代理自行实现采购模块。
- 不得把采购阶段扩大到应付、发票、付款、税务或总账。
- 不得把审批流、询价比价、供应商评分、MRP 自动采购建议纳入采购基础阶段。
- 不得绕过库存过账机制直接改库存余额。
- 不得只用接口通过替代浏览器验收。
- 不得省略视觉分析。
- 不得把当前本地验收库样例数据当作迁移种子。
- 不得在有阻断缺陷时合入或推送主分支。
- 不得把旧截图、旧测试结果或旧交接报告当作当前证据。

## 关键参考文档

- `AGENTS.md`
- `docs/README.md`
- `docs/product/phase-one-scope.md`
- `docs/product/product-decisions.md`
- `docs/product/business-flow.md`
- `docs/tasks/009-cost-collection-foundation.md`
- `docs/tasks/010-phase-one-e2e-stabilization.md`
- `docs/api/cost-collection-api.md`
- `docs/testing/phase-one-e2e-test-plan.md`
- `docs/testing/phase-one-e2e-execution-record.md`
- `docs/testing/phase-one-e2e-defects.md`
- `docs/testing/phase-one-e2e-visual-audit/notes.md`
- `docs/architecture/technology-decision.md`
- `docs/ops/local-development.md`

## 交接结论

QH ERP 一期目标已经完成。系统已经具备账号权限、基础资料、物料、BOM、库存、生产执行、成本归集和一期端到端闭环验收证据。当前主链路能够从物料和 BOM 出发，经库存、工单、领料、报工、完工入库到成本业务记录完成追溯；权限、异常、审计和视觉分析也已完成阶段验证。

下一阶段应重点推进 `011 采购管理基础模块`。该阶段的业务意义是补齐制造业原材料进入企业的真实入口，把当前“期初和调整驱动库存”的测试型来源升级为“供应商和采购入库驱动库存”的业务型来源。阶段边界必须保持清晰：只做采购订单、采购入库、库存增加和追溯，不做应付、发票、付款、审批、MRP 或正式财务。

新主代理接手后，应先组织 5 个固定角色讨论采购阶段，形成文档基线和实施计划，再进入开发。只有完成自动化测试、本地部署、浏览器功能验收、权限验收、异常验收、视觉分析和质量审查后，才能判断是否合入主分支并通知用户验收。
