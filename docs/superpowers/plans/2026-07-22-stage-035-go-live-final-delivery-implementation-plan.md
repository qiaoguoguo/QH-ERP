# 035 上线准备与最终生产交付实施计划

> **执行说明：** 本计划只描述文件落点、实施顺序和验证命令；目标、范围、指标、阻断规则和验收矩阵唯一以 `docs/tasks/035-go-live-final-delivery.md` 为准，不形成第二份业务规格。执行采用测试先行，阻断问题立即修复，非阻断问题登记后集中闭合；任何代码修改在最终验证前交固定测试角色独立评审。

**目标：** 建立可重复、隔离、可恢复、可观测的完整生产式候选拓扑，以 034 V36 全事实冻结 035 候选，完成安全、权限、迁移、备份恢复、性能、全链路、月结财务、桌面浏览器和交付包验证，并闭合已登记的一般遗留项。

**架构：** 使用 `compose.production.yaml` 编排 PostgreSQL 18、固定版本 MinIO、Java 21 API 与 Nginx 静态 Web；公网入口只绑定 `127.0.0.1`，Nginx 统一代理 API、登录限流和安全响应头。敏感值只保存在当前 Windows 用户 DPAPI 加密文件中，由 PowerShell 启停入口按进程注入。PostgreSQL 自定义格式备份与 MinIO 文件镜像共同生成 SHA256 清单，恢复到全新资源后执行一致性验证。性能与交付验证由可重复 PowerShell 工具输出结构化 JSON 证据。

**技术栈：** Docker Compose、PostgreSQL 18、MinIO、Java 21、Spring Boot 4、Vue 3/Vite、Nginx、PowerShell 7、Playwright。

---

## 工作包一：生产拓扑、密钥与安全基线

**文件：**

- 新增：`compose.production.yaml`
- 新增：`apps/api/Dockerfile`
- 新增：`apps/api/.dockerignore`
- 新增：`apps/api/src/main/resources/application-production.properties`
- 新增：`apps/web/Dockerfile`
- 新增：`apps/web/.dockerignore`
- 新增：`apps/web/nginx.production.conf`
- 新增：`tools/production/tests/production-self-test.ps1`
- 新增：`tools/production/lib/production-common.ps1`
- 新增：`tools/production/initialize-secrets.ps1`
- 新增：`tools/production/invoke-production.ps1`
- 修改：`.env.example`

### 1.1 建立失败基线

先编写 `production-self-test.ps1`，断言：生产 Compose 不允许敏感默认值、四个端口只绑定本机、四服务都有健康/重启/日志限制、镜像版本固定、API 使用 production profile、Web 代理和登录限流存在、生产属性要求敏感环境变量、DPAPI 文件权限与密钥字段完整。运行：

```powershell
pwsh -NoProfile -File tools/production/tests/production-self-test.ps1
```

预期：因生产文件和函数尚不存在而失败，记录失败原因。

### 1.2 最小实现并转绿

实现完整四服务拓扑、Java 21/Node/Nginx 多阶段镜像、生产属性、Nginx 安全头与登录限流、DPAPI 初始化及受控启停。密钥初始化不回显明文；启动前校验工作区、密钥字段、端口占用和 `docker compose config`。再次运行自测直至通过。

### 1.3 真实构建与缺密钥反向验证

```powershell
pwsh -NoProfile -File tools/production/invoke-production.ps1 -Action Config
pwsh -NoProfile -File tools/production/invoke-production.ps1 -Action Build
```

另在没有 DPAPI 密钥文件时验证入口必须失败；生产 profile 缺少任一敏感变量时 API 必须拒绝启动。确认仓库、镜像层和生成日志不写入明文密钥。

## 工作包二：联合备份、恢复与迁移演练

**文件：**

- 修改：`tools/production/tests/production-self-test.ps1`
- 修改：`tools/production/lib/production-common.ps1`
- 新增：`tools/production/backup-production.ps1`
- 新增：`tools/production/restore-production.ps1`
- 新增：`tools/production/invoke-migration-rehearsal.ps1`
- 新增：`tools/production/verify-production.ps1`

### 2.1 为清单、哈希与恢复前置条件建立失败测试

先断言备份清单必须含源码提交、Compose 项目、数据库版本、Flyway 版本、对象键集合、每个文件 SHA256、开始/结束时间和结果；恢复必须在目标隔离、哈希一致、服务停止和显式确认后才能执行。运行自测并确认因函数缺失而失败。

### 2.2 实现联合备份与全新资源恢复

使用容器内 `pg_dump -Fc` 和 MinIO 文件镜像生成同一备份目录及 JSON 清单；恢复前复算全部哈希，停止 API/Web，重建目标数据库和 bucket，执行 `pg_restore`、对象镜像恢复及恢复后验证。不得写入 034 来源环境。

### 2.3 三类迁移演练

依次执行并保存证据：

1. 空 PostgreSQL 18 数据库从 V1 自然迁移到 V36；
2. 正式 V34 数据库副本恢复后自然迁移到 V36；
3. 034 V36 全事实联合备份恢复到 035 候选，新 API 启动后保持 V36 无失败迁移。

每类都核对 Flyway schema history、失败迁移数、关键表数量和 API 健康；候选额外核对 41/41 文件对象与 MinIO 键集合。

## 工作包三：候选冻结、性能与运维证据

**文件：**

- 修改：`tools/production/tests/production-self-test.ps1`
- 修改：`tools/production/lib/production-common.ps1`
- 新增：`tools/production/invoke-performance-check.ps1`
- 新增：`tools/production/export-runtime-evidence.ps1`
- 新增：`docs/ops/production-deployment.md`
- 新增：`docs/ops/production-backup-recovery.md`

### 3.1 为百分位、阈值和证据结构建立失败测试

先测试百分位计算、HTTP 失败分类、阈值判定、报告中不得包含凭据，以及运行证据必须记录提交、镜像、容器健康、资源限制、数据库/对象状态和时间。确认测试因实现缺失而失败。

### 3.2 实现并执行性能基线

对 `45173` 统一入口执行阶段说明中的健康、有效登录、无效登录突发、认证读和固定报表五组负载；输出样本数、成功率、状态码分布、平均值、p50/p95/p99 和阈值结论。无效登录必须触发限流且不得破坏随后有效登录。

### 3.3 运维与稳定性验证

记录冷启动到全健康耗时；核对重启策略、日志轮转和资源上限；分别重启 API、Web、PostgreSQL、MinIO 并验证恢复；模拟 API 不可用时 Nginx 返回可诊断失败。编写从密钥初始化、构建、启动、备份、恢复、验证到回滚的中文运维手册。

## 工作包四：冻结候选全链路与桌面交付验证

**文件：**

- 修改：`tools/production/verify-production.ps1`
- 新增：`docs/testing/035-go-live-final-delivery-execution-record.md`
- 修改：`docs/tasks/035-go-live-final-delivery.md`

### 4.1 冻结候选

将候选提交、镜像摘要、数据库备份清单、Flyway V36、对象键哈希和 DPAPI 密钥版本写入证据；冻结后不再写入演示事实。重跑 034 FullFacts 211/211 及其权限、反向、幂等、文件对象验证，并确认超级管理员使用用户指定的新密码可登录。

### 4.2 全链路、月结财务与安全验证

在冻结候选上执行生产主链、采购、销售、库存、生产/外协、往来、项目成本、业务月结、凭证/总账、财务结账、资金税务和经营财务分析的既有正向与反向验证；核对越权 403、匿名 401、CSRF、上传下载授权、审计和备份敏感信息。不得修改冻结来源，只允许在受控验证副本写入。

### 4.3 真实桌面浏览器验证

通过 `http://127.0.0.1:45173/` 在真实桌面浏览器核对登录、首页、核心模块、034 平台治理、报表、打印/下载和错误状态；不截图，不扩展移动端。记录控制台、网络错误、布局、关键操作可见性和业务状态识别结论。

## 工作包五：集中闭合一般遗留项

**文件：**

- 修改：`apps/web/src/modules/platform/historyImports/HistoryImportListView.vue`
- 修改：`apps/web/src/modules/platform/PlatformGovernanceViews.spec.ts`
- 修改：034 权威阶段说明所列受影响页面及对应测试（以实际清点结果为准）
- 修改：`apps/web/src/modules/platform/components/BatchStatusToolPanel.vue`
- 修改：对应平台视图测试
- 修改：`tools/demo-data/rebuild-acceptance.ps1`
- 修改：`tools/demo-data/lib/demo-data-self-test.ps1`
- 修改：`apps/web/vite.config.ts`
- 修改：前端路由拆分或模块加载相关测试（仅在真实构建分析支持时）

### 5.1 六项逐项红测

按 N01—N06 分别建立失败证据：带 `adapterCode` 入口预置；034 受影响旧式查询区规范映射；批量抽屉底部动作始终可达；FullFacts 稳定状态样本；失败清理不写只读 `$PID`；生产构建不再产生大 chunk 警告且路由行为不变。

### 5.2 集中修复并定向转绿

只修改已登记范围。先运行各自定向测试，再运行平台视图测试、演示数据自测、前端类型检查和生产构建。一般问题如经真实清点证明不应改动，必须在阶段说明记录原因、影响和明确处置，不得静默删除。

### 5.3 重新冻结差异

一般问题代码变更完成后重建 API/Web 镜像，生成新的候选提交与镜像摘要；只复验变更路径、受影响权限和生产构建，并重跑候选健康与交付包一致性。

## 工作包六：固定测试角色代码评审与整改

**文件：**

- 修改：`docs/testing/035-go-live-final-delivery-execution-record.md`
- 修改：`docs/tasks/035-go-live-final-delivery.md`

### 6.1 派发独立评审

复用交接中登记的固定测试角色长期线程，明确权威工作区、分支、基准和最终提交，只允许只读评审：生产 Compose/Dockerfile、production profile、Nginx、密钥、备份恢复、迁移、性能工具、前端/脚本修复及测试证据。要求按阻断、严重、一般分级并给出文件/行号和可复现依据。

### 6.2 闭合评审意见

阻断和严重问题立即修复；一般问题纳入同一集中清单并在交付前闭合。每个修复先补失败测试，再实现、定向复验；固定测试角色只复审差异和受影响路径。

## 工作包七：唯一全量验证与正式交付

**文件：**

- 修改：`docs/testing/035-go-live-final-delivery-execution-record.md`
- 修改：`docs/tasks/035-go-live-final-delivery.md`
- 修改：`docs/handoffs/2026-07-12-project-handoff-current.md`
- 修改：`README.md`
- 修改：`docs/README.md`

### 7.1 唯一全量验证窗口

按顺序一次性执行：

```powershell
$env:JAVA_HOME='C:\Users\14567\.jdks\temurin-21-extract\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
Set-Location apps/api
.\mvnw.cmd test
Set-Location ..\web
npm ci
npm test -- --run
npm run typecheck
npm run build
Set-Location ..\..
pwsh -NoProfile -File tools/production/tests/production-self-test.ps1
pwsh -NoProfile -File tools/production/verify-production.ps1
pwsh -NoProfile -File tools/production/invoke-performance-check.ps1
```

随后完成三类迁移回归、联合备份恢复、真实桌面浏览器功能与视觉检验、环境健康、仓库空白/敏感信息扫描和 `git diff --check`。必须先执行完计划内可执行项并统一汇总，不在窗口中零散返工。

### 7.2 窗口缺陷集中整改与差异复验

汇总去重后一次性修复；只复验缺陷差异和受影响路径。阻断/严重为 0、A01—A26 全部有证据后，更新阶段说明和交接为正式完成。

### 7.3 合入与主分支候选核验

提交功能分支；确认正式 `main` 只含已知 `business-flow.md` 用户行尾状态及本阶段可控提交，采用安全快进合入并推送 `origin/main`。从正式主分支按同一密钥版本重建/启动候选，核对本地、远端哈希一致、`45173` 与 `48080` 健康且浏览器可验收，最后才宣告第一版路线完成。
