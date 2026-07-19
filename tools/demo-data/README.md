# 演示数据生成、重建与验证工具

本目录用于 000-030 全量电气制造业演示数据的安全重建、真实 API 生成和独立验收。`validate-demo-data.ps1` 只读 PostgreSQL、后端健康接口和 MinIO bucket；`rebuild-acceptance.ps1` 是唯一允许正式清理/重建的入口；`generate-demo-data.ps1` 的正式模式不能脱离重建入口独立运行。029 还提供面向正式 V30 副本的 `Stage029Only` 隔离增量模式；030 的完整状态夹具只在隔离真实 API 环境准备，正式自然前迁不得为验收补造关闭、重开、快照或专用角色。

生成器和重建入口依赖 PowerShell 7；验证器同时兼容 Windows PowerShell 5.1 与 PowerShell 7。所有密码只允许通过当前进程环境变量或受控本地配置注入，不写入仓库、manifest 或重建摘要；下面的占位符不得替换成真实密码后留存在命令历史中。

## 安全重建入口

临时验证只允许 `qherp_demo_build_*` 数据库、`qherp-demo-build-*` bucket 和非 18080 端口：

```powershell
$env:QHERP_INITIAL_ADMIN_PASSWORD = "<本地环境提供>"
$env:QHERP_DEMO_USER_PASSWORD = "<本次演示账号密码>"
pwsh -NoProfile -File .\tools\demo-data\rebuild-acceptance.ps1 `
  -Mode Temporary `
  -ApiBaseUrl "http://127.0.0.1:18081" `
  -Database "qherp_demo_build_20260715" `
  -MinioBucket "qherp-demo-build-20260715" `
  -RecreateTarget `
  -RunGeneratorAndValidate
```

正式验收重建必须同时满足：权威工作树干净、`ExpectedGitCommit` 与 `HEAD` 一致、18080 没有旧监听、目标固定为 `qherp/qherp-private`、地址为本机 loopback、PostgreSQL owner 为 `qherp`。入口先备份 PostgreSQL 与 MinIO，再重建目标；随后从显式 `RepositoryRoot` 依次启动由本次运行管理的 worker-disabled 和 worker-enabled 两个隐藏 API 阶段。第一阶段在文档任务 worker 关闭时生成包含取消任务在内的稳定业务事实并停止；第二阶段开启 worker，完成导入、导出、打印任务和独立验证。两个阶段都必须在健康接口通过后继续等待初始管理员可以真实登录，避免把健康状态误当作业务初始化完成。建议把 `OutputDirectory` 放在仓库外的备份目录：

```powershell
$env:QHERP_INITIAL_ADMIN_PASSWORD = "<本地环境提供>"
$env:QHERP_DEMO_USER_PASSWORD = "<本次演示账号密码>"
$commit = git -C "F:\zhangqiao\AI-study\qherp" rev-parse HEAD
pwsh -NoProfile -File .\tools\demo-data\rebuild-acceptance.ps1 `
  -Mode Acceptance `
  -ApiBaseUrl "http://127.0.0.1:18080" `
  -Database "qherp" `
  -MinioBucket "qherp-private" `
  -ConfirmPhrase "REBUILD qherp/qherp-private ON 18080" `
  -RepositoryRoot "F:\zhangqiao\AI-study\qherp" `
  -ExpectedGitCommit $commit `
  -JavaHome $env:JAVA_HOME `
  -OutputDirectory "C:\Users\14567\.codex\backups\qherp\<时间戳>" `
  -RecreateTarget `
  -RunGeneratorAndValidate
```

正式运行前应先停止已知的 QH ERP 18080 进程；脚本不会接管或复用来源不明的旧进程。成功摘要中的 `workerDisabledApi` 和 `workerEnabledApi` 分别记录实际监听 PID、启动器 PID、启动/停止时间、仓库、提交、数据库、bucket、worker 模式、日志、健康结果和登录就绪结果；摘要还分别记录两阶段 manifest 及最终验证文件。`generatedAndValidated=true` 只在 worker-disabled 生成成功、该阶段干净停止、worker-enabled 生成成功且验证器达到当前精确门禁 127/127 后写入。任一步失败都会清理当前受管进程并保留错误摘要，不得把正式环境声明为已重建。

## 独立验证入口

Windows 宿主机支持以下两个入口，二选一即可。脚本文件以 UTF-8 BOM 保存，保证 Windows PowerShell 5.1 和 PowerShell 7 都按 UTF-8 解析中文输出。

Windows PowerShell 5.1：

```powershell
$env:QHERP_POSTGRES_USER = "qherp"
$env:QHERP_POSTGRES_PASSWORD = "<本地环境提供>"
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\demo-data\validate-demo-data.ps1 `
  -ApiBaseUrl "http://127.0.0.1:18080" `
  -PostgresContainer "qherp-postgres" `
  -Database "qherp" `
  -MinioContainer "qherp-minio" `
  -MinioBucket "qherp-private"
```

PowerShell 7：

```powershell
$env:QHERP_POSTGRES_USER = "qherp"
$env:QHERP_POSTGRES_PASSWORD = "<本地环境提供>"
pwsh -NoProfile -ExecutionPolicy Bypass -File .\tools\demo-data\validate-demo-data.ps1 `
  -ApiBaseUrl "http://127.0.0.1:18080" `
  -PostgresContainer "qherp-postgres" `
  -Database "qherp" `
  -MinioContainer "qherp-minio" `
  -MinioBucket "qherp-private"
```

`DbPassword` 也可以通过参数传入，但不得写入仓库文件。当前 Docker 容器内本地 `psql` 可能不需要密码；脚本仍保留参数和环境变量入口，供后续隔离运行器使用。

## 退出码

- `0`：全部规则通过。
- `1`：连接和脚本正常，但至少一条演示数据、环境或对象存储规则失败。
- `2`：SQL 文件、Docker/psql 调用或 JSON 解析失败，不能作为有效红色基线。

## 覆盖范围

规则位于 `tools/demo-data/sql/validate-demo-data.sql` 和 `tools/demo-data/validate-demo-data.ps1`，覆盖：

- Flyway V29/V30/V31/V32 精确版本与 checksum（`774334682`、`2130342893`、`-2074547591`、`249406902`）以及失败迁移检查。
- 账号、角色、权限、系统管理员和停用账号。
- 单位、仓库、客户、供应商、物料、追踪方式、单位换算、结算税务资料和编码规则。
- BOM、BOM 明细、替代料、ECO 当前/未来/历史治理样例。
- 业务期间、采购、质量、库存余额、追踪、预留、公共移动平均池、项目成本层、价值流水。
- 调拨、权属转换、盘点分页、0 与未盘、盘盈盘亏、估值调整。
- 生产工单、BOM 快照、领料、退料、补料、报工、完工、成本记录。
- 销售项目、合同、销售订单、出库、退货、财务往来和反向来源。
- 029 项目成本运行、来源指纹、分类/阶段分录、差异、调整审批、金额脱敏、经营毛利和上游零回写。
- 030 权限、检查运行、当前关闭唯一、已有关闭/重开版本的锁期/审计/八报表/来源指纹/快照不变和阻断失败关闭；自然前迁允许尚无关闭版本或专用脱敏角色。
- 固定审批、消息、附件、审批快照、打印模板、文档任务、导入失败明细和审计。
- PostgreSQL/MinIO 容器健康、后端健康接口，以及 MinIO bucket 对象总数与数据库 `AVAILABLE` 文件对象数严格一致且不少于 8。

## 红色基线说明

工作包一要求在当前非全量验收库上先取得预期失败。有效红灯必须满足：

- SQL 连接、Flyway 查询、JSON 摘要和脚本执行正常。
- 失败来自演示数据覆盖不足，例如销售、财务、生产完工、批次、序列、盘点分页或状态分布不足。
- 不能把连接失败、表名错误、语法错误、脚本崩溃或 MinIO/API 不可达误判为数据规则红灯。
