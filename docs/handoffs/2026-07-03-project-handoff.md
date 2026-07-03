# QH ERP 项目交接报告

## 交接目的

本报告用于帮助新的项目主代理在接手后，通过探索仓库和阅读本文，快速理解项目要求、当前结构、已完成进度、固定工作模式、质量门和下一阶段开发目标。

接手后不得只依赖本文推进，必须以当前仓库文件、命令输出、运行结果和用户最新指令为准。本文是交接索引和上下文压缩材料，不替代 `AGENTS.md`、任务文档、接口契约、测试计划和实际验证。

## 当前状态摘要

- 当前主分支：`main`
- 当前远端状态：`main` 已推送到 `origin/main`
- 当前最新提交：`2c7eba5 修正生产执行视觉分析预检说明`
- 当前阶段：一期生产主链路已推进到“生产执行基础能力”完成并进入主分支。
- 已完成业务模块：账号与权限、基础资料与物料管理、BOM 管理、仓库与库存基础、生产执行基础。
- 下一阶段重点：成本归集基础记录。
- 下一阶段不应直接编码，必须先组织 5 个固定角色子代理讨论并形成阶段计划。

## 项目要求

### 产品定位

本项目是面向制造业单公司内部使用的 Web ERP 系统，以生产管理为核心，不是零售业、门店收银、电商或通用轻量进销存。

长期方向需要覆盖常规 ERP 能力，包括：

- 基础资料
- 采购
- 销售
- 库存
- 生产
- 财务
- 报表

当前一期优先打通生产主链路，重点是：

- 物料管理
- BOM
- 库存余额与库存流水
- 生产工单
- 领料
- 报工
- 完工入库
- 成本归集基础记录

### 一期范围

一期目标见 `docs/product/phase-one-scope.md`。

已确认一期里程碑：

1. 项目工程骨架与账号权限基础。
2. 基础资料与物料管理。
3. BOM 管理。
4. 仓库与库存基础能力。
5. 生产工单、领料、报工、完工入库。
6. 成本归集基础记录。

当前第 1 到第 5 项已完成并进入 `main`。第 6 项是下一阶段目标。

### 成本归集一期深度

产品决策已明确：一期成本归集只做到业务记录，不承诺正式财务核算结果。

成本归集基础记录至少应能说明：

- 成本来源。
- 关联工单或产品。
- 成本类型。
- 成本金额或数量口径。
- 记录人和记录时间。
- 后续可追溯的来源单据。

正式成本核算、总账、凭证、标准成本、移动加权、成本差异分析等不属于下一阶段一期目标，除非用户后续明确变更范围。

## 最高优先级工作规范

接手后必须先阅读并遵守 `AGENTS.md`。其中最重要的约束如下。

### 对用户沟通

- 每次对用户可见回复开头必须先叫“爸爸”。
- 所有智能体与用户沟通必须使用中文。
- 除技术标识、路径、命令、接口名、库名等不可翻译内容外，不使用非中文表达。

### 主代理职责

主代理是项目负责人，只负责：

- 项目统筹
- 任务拆解
- 上下文提供
- 任务派发
- 等待结果
- 组织审查
- 验收判断
- 向用户汇报

主代理默认不直接承担产品设计、UI 设计、前端开发、后端开发或测试执行工作。涉及具体设计、开发、测试或审查时，必须派发给对应角色子代理。

### 固定 5 个角色子代理

项目固定只有 5 个角色子代理：

- 产品经理
- UI 设计师
- 前端开发
- 后端开发
- 测试

子代理线程 ID 可变，职责边界以角色为准，不以线程 ID 为准。不得按任务无限创建临时代理，不得创建第 6 类角色。规格审查、代码质量审查、修复复审等职责必须由这 5 个角色中的合适角色承担。

如果某个角色线程失效，只能创建同角色替代，并记录原因。

### `/goal` 派发要求

向任何子代理派发任务必须使用 `/goal` 模式，并明确：

- 角色
- 任务目标
- 输入上下文
- 职责边界
- 预期成果
- 验收标准
- 禁止发散范围

主代理派发后必须等待子代理完成并返回结果，不得中断、绕过或自行替代执行。除非子代理进程故障、长时间无响应或工具明确异常。

### 新阶段推进流程

推进新阶段前必须按以下顺序执行：

1. 组织 5 个子代理分别从产品经理、UI 设计师、前端开发、后端开发、测试视角讨论。
2. 主代理汇总 5 个角色结论。
3. 形成阶段目标、范围边界、关键风险、验收标准和实施计划。
4. 计划形成前不得进入实现。
5. 按计划拆分任务，逐项派发给对应实现子代理。
6. 每个实现任务先做规格审查，通过后再做代码质量审查。
7. 审查失败必须派发修复并复审。
8. 主代理最终复核当前证据，再判断是否进入主分支阶段验收。

## 项目结构

仓库根目录：

- `AGENTS.md`：最高优先级项目规范和工作模式。
- `README.md`：项目入口说明。
- `compose.yaml`：本地 PostgreSQL 等依赖服务。
- `.env.example`：本地环境变量示例。
- `apps/api`：后端 Spring Boot 服务。
- `apps/web`：前端 Vue 应用。
- `docs`：产品、技术、接口、测试、部署、任务和视觉验收文档。

后端主要结构：

- `apps/api/pom.xml`：后端 Maven 配置，Java 21，Spring Boot 4.0.7。
- `apps/api/src/main/java/com/qherp/api`：后端应用代码。
- `apps/api/src/main/java/com/qherp/api/system`：系统、账号、权限、角色、用户、初始化等。
- `apps/api/src/main/java/com/qherp/api/master`：基础资料相关。
- `apps/api/src/main/java/com/qherp/api/materials`：物料相关。
- `apps/api/src/main/java/com/qherp/api/bom`：BOM 相关。
- `apps/api/src/main/java/com/qherp/api/inventory`：库存相关。
- `apps/api/src/main/java/com/qherp/api/production`：生产执行相关。
- `apps/api/src/main/resources/db/migration`：Flyway 数据库迁移，当前包含 `V1` 到 `V6`。
- `apps/api/src/test`：后端测试，使用 JUnit、Spring Boot Test、Testcontainers。

前端主要结构：

- `apps/web/package.json`：前端脚本与依赖。
- `apps/web/src/main.ts`：前端入口。
- `apps/web/src/App.vue`：主布局与导航。
- `apps/web/src/router`：路由和权限守卫。
- `apps/web/src/stores`：状态管理。
- `apps/web/src/shared/api`：前端接口封装。
- `apps/web/src/modules/auth`：登录与认证。
- `apps/web/src/modules/master`：基础资料页面。
- `apps/web/src/modules/materials`：物料页面。
- `apps/web/src/modules/bom`：BOM 页面。
- `apps/web/src/modules/inventory`：库存页面。
- `apps/web/src/modules/production`：生产执行页面。

文档主要结构：

- `docs/README.md`：文档索引。
- `docs/product`：产品范围、决策、流程、权限草案。
- `docs/tasks`：阶段任务文档与执行记录。
- `docs/api`：接口契约。
- `docs/testing`：测试计划、验收标准、视觉分析记录。
- `docs/superpowers/specs`：阶段设计规格。
- `docs/superpowers/plans`：阶段实施计划。
- `docs/architecture`：技术基线与技术选型。
- `docs/frontend`：前端基线。
- `docs/ui`：UI 与交互基线。
- `docs/ops`：本地开发、部署和验收说明。
- `docs/handoffs`：交接报告。

## 技术栈与运行方式

### 技术栈

前端：

- Vue 3
- TypeScript
- Vite
- Vue Router
- Pinia
- Element Plus
- Vitest
- Playwright / Chromium 用于浏览器视觉证据

后端：

- Java 21
- Spring Boot 4.0.7
- Spring Security
- Spring Data JPA
- Flyway
- PostgreSQL
- OpenAPI
- JUnit
- Testcontainers

### 本地服务

本地 PostgreSQL：

```powershell
docker compose up -d postgres
```

后端 Docker 启动：

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

前端启动：

```powershell
cd apps/web
npx vite --host 127.0.0.1
```

常用访问地址：

- 前端：`http://127.0.0.1:5173/` 或实际 Vite 输出端口。最近一次验收使用 `http://127.0.0.1:5174/`。
- 后端健康检查：`http://127.0.0.1:18080/api/health`
- PostgreSQL：`127.0.0.1:15432`

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

后端推荐使用 Docker 化 Maven 和 JDK 21：

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

空白检查：

```powershell
git diff --check
```

Testcontainers 遗留容器检查：

```powershell
docker ps -a --filter "label=org.testcontainers" --format "{{.ID}} {{.Image}} {{.Status}} {{.Names}}"
```

视觉分析截图检查：

```powershell
Get-ChildItem docs/testing/<模块标识>-visual-audit -Filter *.png | Select-Object Name,Length
```

注意：后端测试曾遇到 Maven 中央仓库 TLS 握手中断，改用 `qherp-maven-repo` 缓存卷后重跑通过。遇到类似依赖下载失败时，应先诊断环境和网络，不得把该问题写成测试失败或跳过后端验证。

## 已完成模块与证据

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

已完成能力：

- 登录。
- 用户管理。
- 角色管理。
- 权限分配。
- 菜单、路由、按钮和接口鉴权。
- 只读用户与无权限用户验收。
- 浏览器视觉分析。

关键文档：

- `docs/tasks/004-account-permission-foundation.md`
- `docs/api/account-permission-api.md`
- `docs/testing/account-permission-test-plan.md`
- `docs/testing/account-permission-visual-audit/notes.md`

阶段结论：已达到本阶段功能、权限、接口、本地部署和浏览器视觉验收要求。

### 2. 基础资料与物料管理

已完成能力：

- 计量单位。
- 仓库。
- 供应商。
- 客户。
- 物料分类。
- 物料档案。
- 启用、停用、引用校验和权限验收。
- 浏览器视觉分析。

关键文档：

- `docs/tasks/005-master-data-material-foundation.md`
- `docs/api/master-data-material-api.md`
- `docs/testing/master-data-material-test-plan.md`
- `docs/testing/master-data-material-visual-audit/notes.md`

阶段结论：已具备进入主分支阶段验收的条件，并已进入主分支。

### 3. BOM 管理

已完成能力：

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

阶段结论：已达到自动化测试、本地部署、浏览器功能验收和视觉分析要求。

### 4. 仓库与库存基础能力

已完成能力：

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

阶段结论：已完成自动化验证和浏览器视觉验收。

### 5. 生产执行基础能力

已完成能力：

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

生产执行阶段关键验收结果：

- 管理员主路径完成：创建工单、发布、领料过账、报工过账、完工入库过账、完成工单。
- 权限覆盖：管理员、仓库人员、生产人员、只读用户、无权限用户。
- 异常覆盖：库存不足、超领、超报、超入、停用 BOM、停用物料、停用仓库。
- 库存流水类型显示缺陷已修复并复验。
- 视觉分析保存 15 张 PNG，目录为 `docs/testing/production-execution-visual-audit/`。
- 低风险视觉关注：窄屏详情明细需要横向滚动，桌面固定操作列阴影略明显；均未构成阻断。

主分支合入后验证结果：

- 后端全量测试：13 个测试套件、84 个用例、0 失败、0 错误、0 跳过。
- 前端全量测试：27 个测试文件、173 个用例通过。
- 前端构建：`vue-tsc --noEmit && vite build` 通过。
- `git diff --check` 无输出。
- `main` 已推送到 `origin/main`。
- 后端健康检查返回 `UP`。
- 生产工单页面入口返回 HTTP 200。

## 当前分支与服务状态

交接时的仓库状态：

- 当前分支：`main`
- 当前 `main` 与 `origin/main` 对齐。
- 最新提交：`2c7eba5`
- 工作区应保持干净。

交接前最后一次可访问状态：

- 后端：`http://127.0.0.1:18080/api/health`
- 前端：`http://127.0.0.1:5174/production/work-orders`

接手后必须重新运行 `git status --short --branch` 和健康检查确认当前状态，不得把交接时状态当成最新事实。

## 当前已知问题和注意事项

- 生产执行视觉分析记录中有两个低风险 UI 建议：窄屏明细表格横向滚动、桌面固定操作列阴影略明显。当前不阻断阶段验收。
- 生产执行阶段不包含排产、工艺路线、工序流转、批次、序列号、库位、条码、倒冲、退料、补料、返工、委外、采购销售联动、审批流、正式成本核算、多单位换算、多组织或多租户。
- 本机 Java 曾记录为 Java 8，不满足后端要求；后端测试优先使用 Docker 化 JDK 21。
- 后端 Docker 测试建议挂载 `qherp-maven-repo`，减少 Maven 中央仓库网络波动。
- 视觉分析必须用真实浏览器截图；不得用伪造图片或旧截图替代。
- 主分支是用户阶段验收分支，不得合入未完成、未验证或存在阻断缺陷的功能。

## 下一阶段开发目标

### 阶段名称

建议下一阶段命名为：成本归集基础记录。

建议任务编号：`009-cost-collection-foundation`。

### 核心目标

在已完成物料、BOM、库存和生产执行闭环的基础上，建立一期成本归集业务记录能力，使生产执行产生的领料、报工、完工入库等来源能够沉淀为可追溯的成本记录，为后续财务核算、成本查询、报表和经营分析提供数据基础。

### 下一阶段必须坚持的范围

应纳入：

- 成本记录列表与查询。
- 按工单、产品、成本类型、来源单据、日期范围查询。
- 成本记录详情。
- 能够记录材料、人工、制造费用或其他成本来源。
- 能够关联工单、产品、来源单据和记录人。
- 能够说明金额或数量口径。
- 能追溯来源单据，例如生产领料、报工、完工入库。
- 权限控制、后端鉴权和审计记录。
- 浏览器功能验收和视觉分析。

必须排除，除非用户明确变更：

- 正式财务核算结果。
- 总账。
- 凭证。
- 标准成本体系。
- 移动加权平均。
- 成本差异分析。
- 完整财务报表。
- 多币种。
- 多组织、多公司、多租户。
- 采购、销售和财务深度联动。

### 下一阶段建议讨论重点

推进前必须先组织 5 个子代理讨论，建议让各角色重点回答：

产品经理：

- 成本归集一期的业务记录范围。
- 成本类型分类：材料、人工、制造费用、其他。
- 哪些成本记录自动来自现有生产单据，哪些允许手工录入。
- 成本金额口径是否允许手填，是否需要单价。
- 成本记录与工单、产品、来源单据的追溯路径。
- 阶段验收主路径。

UI 设计师：

- 成本记录列表、详情、筛选、空状态、异常状态和权限差异展示。
- 成本数据表格的信息密度和扫描效率。
- 来源追溯入口如何在生产工单详情和成本记录详情中展示。
- 视觉分析截图清单。

前端开发：

- 成本模块路由、菜单、权限按钮。
- 成本记录 API 封装。
- 列表、详情、表单或生成动作页面。
- 与生产工单详情的入口关系。
- 前端测试覆盖。

后端开发：

- 成本记录数据模型和迁移脚本。
- 成本来源类型、成本类型、来源单据类型枚举。
- 从生产领料、报工、完工入库读取或生成成本记录的事务边界。
- 金额精度、数量精度、审计字段。
- 权限编码、接口契约、错误码。
- 集成测试覆盖。

测试：

- 成本记录测试计划。
- 自动化测试、接口测试、权限测试、浏览器主路径、异常路径、视觉分析。
- 测试数据准备：工单、领料、报工、完工入库、成本来源。
- 阻断标准。

### 下一阶段建议产物

新阶段开始后，先不要编码。建议先形成以下文档：

- `docs/tasks/009-cost-collection-foundation.md`
- `docs/api/cost-collection-api.md`
- `docs/testing/cost-collection-test-plan.md`
- `docs/superpowers/specs/2026-07-03-cost-collection-design.md`
- `docs/superpowers/plans/2026-07-03-cost-collection-implementation-plan.md`

同时应更新：

- `docs/README.md`
- `docs/product/product-decisions.md`
- 必要时更新 `docs/product/business-flow.md`
- 必要时更新 `docs/testing/acceptance-criteria.md`

### 下一阶段建议验收主路径

建议主路径先围绕一个已发布或可新建的生产工单展开：

1. 准备启用物料、BOM、仓库和库存。
2. 创建并发布生产工单。
3. 过账生产领料，形成材料成本来源。
4. 过账报工，形成工时或人工成本来源。
5. 过账完工入库，形成完工归集关联。
6. 生成或记录该工单的成本归集业务记录。
7. 在成本记录列表中按工单、产品、成本类型查询。
8. 进入成本记录详情，查看来源单据、成本口径、记录人和时间。
9. 从成本记录追溯回生产工单或来源单据。
10. 验证只读用户、无权限用户和成本相关角色权限。
11. 验证异常场景，例如来源单据不存在、工单不存在、停用物料、重复归集或金额/数量不合法。
12. 完成浏览器视觉分析并保存截图证据。

### 下一阶段质量门

下一阶段在合入主分支前至少必须满足：

- 任务文档、接口契约、测试计划、设计规格和实施计划完整。
- 后端全量测试通过。
- 前端全量测试通过。
- 前端构建通过。
- 本地部署健康检查通过。
- 浏览器管理员主路径通过。
- 权限与异常路径通过。
- 视觉分析截图和结论归档。
- 无阻断缺陷。
- `git diff --check` 通过。
- 主代理组织规格审查和代码质量审查通过。

## 接手后的第一组动作

新的主代理接手后建议按以下顺序执行：

1. 读取 `AGENTS.md`，确认固定 5 角色工作模式。
2. 执行 `git status --short --branch`，确认工作区状态。
3. 读取本交接报告。
4. 读取 `docs/README.md`、`docs/product/phase-one-scope.md`、`docs/product/product-decisions.md`。
5. 重点读取 `docs/tasks/008-production-execution-foundation.md` 和 `docs/testing/production-execution-test-plan.md`，理解生产执行输出数据。
6. 组织 5 个子代理对“成本归集基础记录”进行新阶段讨论。
7. 汇总讨论结论，形成 `009` 阶段目标、边界、风险和验收标准。
8. 先写文档和计划，再拆分实现任务。
9. 每个实现任务严格按 `/goal` 派发给对应实现角色。
10. 每个任务完成后先规格审查，再质量审查。

## 不得做的事

- 不得跳过 5 个角色的新阶段讨论直接编码。
- 不得主代理自行实现成本模块。
- 不得创建第 6 类子代理。
- 不得把“规格审查代理”“质量审查代理”作为新角色。
- 不得把正式财务核算纳入成本归集一期。
- 不得因成本目标复杂而降级为无追溯的备注字段。
- 不得只用接口通过替代浏览器验收。
- 不得省略视觉分析。
- 不得在有阻断缺陷时合入或推送主分支。
- 不得把旧截图、旧测试结果或子代理口头结果当作最终证据，主代理必须复核当前状态。

## 重要参考文档

- `AGENTS.md`
- `docs/README.md`
- `docs/product/phase-one-scope.md`
- `docs/product/product-decisions.md`
- `docs/product/business-flow.md`
- `docs/tasks/008-production-execution-foundation.md`
- `docs/api/production-execution-api.md`
- `docs/testing/production-execution-test-plan.md`
- `docs/testing/production-execution-visual-audit/notes.md`
- `docs/architecture/technology-decision.md`
- `docs/ops/local-development.md`

## 交接结论

项目已经完成制造业 ERP 一期生产主链路的关键前置能力和生产执行闭环。下一步不应继续扩展生产执行细节，而应按照一期里程碑推进“成本归集基础记录”，把领料、报工、完工入库等生产执行结果沉淀为可追溯的成本业务记录。

成本归集阶段是从生产管理走向财务和报表能力的桥梁，但一期必须保持边界清晰：只做业务记录和追溯，不承诺正式核算结果。新主代理应先组织 5 个角色讨论并形成文档基线，再进入实现。
