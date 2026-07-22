# 生产部署与运行手册

## 适用范围

本手册适用于 035 冻结的单机生产式候选：PostgreSQL、MinIO、API、Web/Nginx 统一由 `compose.production.yaml` 管理。只有 Web/Nginx 发布宿主端口并绑定 `127.0.0.1`；API、PostgreSQL 和 MinIO 仅在 Compose 内部网络可达。需要非本机访问时，必须另行提供受控 TLS 入口并使用 `-SecureCookie`，不得直接扩大当前端口绑定。

## 固定资源

- Compose 项目：`qherp035`。
- Web：`http://127.0.0.1:45173/`。
- API 健康：经 Web 反向代理访问 `http://127.0.0.1:45173/api/health`；API 不发布宿主端口。
- PostgreSQL：Compose 内部地址 `postgres:5432/qherp_035_candidate`，不发布宿主端口；运维检查使用受控脚本或 `docker compose exec`。
- MinIO：Compose 内部地址 `minio:9000/9001`，bucket 为 `qherp-035-candidate`，不发布宿主端口。
- DPAPI 密钥：`C:\Users\14567\.codex\secrets\qherp\035\runtime-secrets.clixml`。

## 首次初始化

1. 使用当前生产运行 Windows 身份登录；DPAPI 文件不能跨用户或跨机器直接复用。
2. 在权威工作区执行 `pwsh -NoProfile -File tools/production/initialize-secrets.ps1`，按提示输入超级管理员密码。数据库和 MinIO 密码由密码学安全随机数生成。
3. 确认脚本报告密钥版本 1，文件 ACL 关闭继承且只有当前身份一条完全控制规则。
4. 执行 `pwsh -NoProfile -File tools/production/tests/production-self-test.ps1`。
5. 执行 `pwsh -NoProfile -File tools/production/invoke-production.ps1 -Action Config`。缺少任何密钥或生产变量时必须失败，禁止补开发默认值。

## 构建与启动

```powershell
pwsh -NoProfile -File tools/production/invoke-production.ps1 -Action Build
pwsh -NoProfile -File tools/production/invoke-production.ps1 -Action Up
pwsh -NoProfile -File tools/production/verify-production.ps1
```

镜像标签由当前 Git 提交派生，API/Web OCI revision 必须等于来源提交。所有基础镜像均锁定 SHA256。启动通过的最低条件是四个容器健康、Web/API HTTP 200、Flyway V36、失败迁移 0、数据库 `AVAILABLE` 文件与 MinIO 对象数量和 SHA256 一致、超级管理员可登录。

当前运行基线固定为 PostgreSQL 18.4 Alpine 3.24、Temurin 21 JRE Alpine、Nginx 1.30.4 Alpine，以及既有 MinIO 开源版固定摘要。API 构建会安装 Alpine 当前安全更新，PostgreSQL JDBC 固定为 42.7.12。任何摘要或依赖版本变化都必须重新构建、扫描并执行恢复后验证，不能只修改标签。

## 供应链与残余风险

- API 与 Web 最终运行镜像必须对高危和严重漏洞保持 0；扫描对象是实际成品，而不是仅扫描 Dockerfile 基础镜像。
- PostgreSQL 使用官方当前 18.4 镜像。扫描器对仅在容器启动时使用的 `gosu` 及未触达的库代码仍可能报告高危项，升级官方摘要后需结合实际执行路径复核，不能直接忽略，也不能为清零统计改用非官方来源。
- MinIO 开源版已经停止维护，官方 2026 修复版本转为需要许可证的 AIStor。当前候选不得静默切换到商业镜像；在完成许可证或替代对象存储决策前，MinIO 不发布任何宿主端口，只允许 Compose 内部 API 访问，禁止配置 OIDC、LDAP、复制、S3 Select 或额外访问密钥，根凭据只允许通过 DPAPI 注入。
- 仓库密钥扫描必须为 0；开发与测试夹具不能当作生产凭据使用。实际生产密钥只允许存在于既定 DPAPI 文件和运行进程内存中。

## 日常操作

- 状态：`pwsh -NoProfile -File tools/production/invoke-production.ps1 -Action Status`。
- 最近日志：`pwsh -NoProfile -File tools/production/invoke-production.ps1 -Action Logs`。
- 整栈重启：`pwsh -NoProfile -File tools/production/invoke-production.ps1 -Action Restart`。
- 停止但保留数据卷：`pwsh -NoProfile -File tools/production/invoke-production.ps1 -Action Down`。
- 导出无凭据运行证据：`pwsh -NoProfile -File tools/production/export-runtime-evidence.ps1`。

禁止在普通停止中使用 `-RemoveVolumes`。该开关会删除候选数据卷，只能在已有已验证联合备份、明确重建环境且确认目标为 `qherp035` 后使用。

## 稳定性和故障处置

- 四服务均使用 `unless-stopped`、健康检查和 JSON 日志轮转；内存上限分别为 PostgreSQL 1GiB、MinIO 512MiB、API 1GiB、Web 256MiB。
- Web 502/504 时先检查 API 健康和日志；API 不健康时继续检查 PostgreSQL、MinIO、Flyway 与只读根文件系统的 `/tmp` 空间。
- 数据库或 bucket 不一致时立即停止 API/Web，不允许继续写入；按备份恢复手册先验证清单和哈希，再恢复。
- 缺少生产环境变量、DPAPI 解密失败、迁移失败、容器持续不健康或管理员无法登录均为阻断问题，不得绕过启动入口。
- Nginx 登录限流返回 429 属于无效登录突发保护；正常登录持续受限时先检查异常流量，不得关闭 CSRF、鉴权或安全响应头。

## 回滚

代码回滚与数据回滚必须分开决策。只有确认目标提交与数据库迁移兼容时才允许切换旧镜像；V1—V36 不执行 `repair`、降级 SQL 或手工修改 Flyway 历史。需要数据回滚时，先停止 API/Web，使用已经验证的联合备份恢复到新数据库和新 bucket，验证通过后再切换；不得直接覆盖来源资源。
