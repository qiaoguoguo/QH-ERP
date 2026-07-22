# 生产联合备份与恢复手册

## 目标与边界

联合备份同时覆盖 PostgreSQL custom dump、MinIO 全量对象、逐文件 SHA256、对象 key 清单、Flyway 版本、来源提交和开始/结束时间。当前约 30MB 数据规模的门槛是备份不高于 10 分钟、恢复不高于 15 分钟；日备份对应 RPO 不高于 24 小时，完整处置 RTO 不高于 30 分钟。

备份默认写入 `C:\Users\14567\.codex\backups\qherp\035-*`，不进入 Git。备份目录中的 `manifest.json` 与 `manifest.sha256` 是一个整体；缺失、路径越界、大小变化或任一 SHA256 不一致时，恢复入口必须失败关闭。

## 生产联合备份

```powershell
pwsh -NoProfile -File tools/production/backup-production.ps1 -QuiesceApplications
```

脚本先停止 Web/API 写入口，再导出 PostgreSQL 和镜像 MinIO，最后恢复应用容器。备份失败也会尝试恢复原写入口，但不得把缺少 `COMPLETED` 清单的目录当作有效备份。备份完成后运行：

```powershell
pwsh -NoProfile -File tools/production/verify-production.ps1
```

外部来源已经由调用方可靠停止写入时才允许使用 `-SourceWriteStopped`；该开关不是跳过停写的便利选项。034 固定来源使用 `backup-stage034-source.ps1`，脚本会核对 PID、Java、JAR 和端口，并在 `finally` 中按容器环境与 DPAPI 参数恢复 API。

## 恢复前检查

1. 确认备份目录、来源提交、Flyway 版本、对象数和完成时间符合目标恢复点。
2. 确认目标容器、数据库和 bucket 是隔离目标，不是来源资源。
3. 确认当前 DPAPI 密钥与目标 Compose 项目匹配。
4. 先运行 `Test-QherpBackupManifest`（恢复脚本会再次执行），不得手工编辑清单或摘要。
5. 通知停止业务写入；恢复期间 Web/API 将保持停止。

## 联合恢复

```powershell
pwsh -NoProfile -File tools/production/restore-production.ps1 `
  -BackupDirectory "C:\Users\14567\.codex\backups\qherp\035-..." `
  -ConfirmRestore
```

恢复入口会复算全部哈希、拒绝来源覆盖、停止 API/Web、重建目标数据库和 bucket、执行 `pg_restore`、恢复对象，并把文件对象的基础设施 bucket 定位映射到目标 bucket。对象 key、内容、大小、SHA256 和业务关系不得变化。恢复后自动验证 V29—V36 checksum、失败迁移 0、数据库/MinIO 对象一致、健康和管理员登录。

灾难恢复确需在原容器和同名资源上重建时，除 `-ConfirmRestore` 外还需 `-AllowSourceReplacement`。只有原来源已经客观丢失或明确废弃、且操作人重新核对容器和数据库/bucket 名时才允许使用；日常演练禁止使用。

## 迁移演练

- 空库：`pwsh -NoProfile -File tools/production/invoke-migration-rehearsal.ps1 -Mode Empty`。
- 正式 V34 副本：先从停止的正式卷制作只读克隆和 custom dump，再执行 `invoke-migration-rehearsal.ps1 -Mode V34 -SourceDumpPath <dump>`。
- V36 全事实：使用 034 联合备份恢复到 035 新数据库和新 bucket，再运行 `verify-production.ps1 -BackupDirectory <目录>`。

演练使用一次性容器和网络，结束后清理。禁止把正式 V34 卷直接挂给会执行 V35/V36 的 API，也禁止在 034 数据库上运行迁移、`repair` 或恢复命令。

## 失败处置

- 哈希失败：隔离备份目录，重新从可信来源停写备份；不得修改摘要迎合载荷。
- `pg_restore` 失败：API/Web 保持停止，保留日志，销毁不完整目标后重新恢复。
- 对象数或 SHA256 不一致：不启动业务写入，比较 manifest、数据库 `platform_file_object` 与 MinIO key；禁止用删除数据库记录掩盖缺失对象。
- Flyway checksum 或版本不符：停止恢复，核对来源和镜像提交；禁止 `repair`。
- 恢复后登录失败：核对 DPAPI 密钥版本与来源账号事实，不得启用开发默认密码。

每次成功备份和恢复都应保存目录、耗时、来源提交、对象数、V36、验证结果和操作者时间；不得记录任何明文密码。
