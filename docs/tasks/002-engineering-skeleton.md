# 任务记录：工程骨架初始化

## 任务目标

建立前后端分离的工程骨架，使项目具备可安装、可构建、可测试、可本地启动和可浏览器验收的基础能力。

## 输入

- [技术选型决策记录](../architecture/technology-decision.md)
- [前端工程基线](../frontend/frontend-baseline.md)
- [接口契约基线](../api/api-contract-baseline.md)
- [测试计划](../testing/test-plan.md)
- [部署与阶段验收](../ops/deployment-acceptance.md)

## 本次包含

- 初始化 `apps/web` 前端工程骨架。
- 初始化 `apps/api` 后端工程骨架。
- 建立最小健康检查能力。
- 建立最小前端页面与路由。
- 建立统一本地启动、构建和测试说明。
- 建立 Docker Compose 本地依赖基线。

## 本次不包含

- 不实现完整账号管理模块。
- 不实现完整角色权限配置界面。
- 不创建生产业务模块。
- 不合入主分支。
- 不通知用户进行业务成果验收。

## 角色分工

- 产品经理：确认骨架支撑账号权限作为首个模块。
- UI 设计师：确认骨架页面具备后台系统布局基础。
- 前端开发：实现前端工程、路由、布局、请求和最小测试。
- 后端开发：实现后端工程、健康检查、统一响应和最小测试。
- 测试：确认安装、构建、启动、健康访问和浏览器验收路径。
- 主代理：统筹任务、执行实现、运行验证、记录结果。

## 预期成果

- `apps/web` 前端工程。
- `apps/api` 后端工程。
- 项目级启动和验证说明。
- 可重复执行的基础验证命令。

## 验收标准

- 前端依赖可安装。
- 前端测试和构建可通过。
- 后端可通过容器化或本地 JDK 环境执行测试。
- 健康检查接口具备测试覆盖。
- 本地启动路径明确。
- 文档说明能支撑后续账号权限模块开发。

## 风险与处理

- 本机 Java 版本不足：优先使用 Docker 化 JDK 环境或安装 JDK 21 以上版本，不降低后端技术标准。
- 网络依赖安装失败：优先定位 npm、Maven 或镜像源问题，不改用弱实现绕过。
- Docker 不可用：先诊断 Docker 服务状态，再决定是否调整本地验证方式。

## 执行记录

- 计划：先确认环境，再初始化前端与后端，最后补充启动验收说明并运行验证。
- 变更：已初始化 `apps/web`、`apps/api`、健康检查接口、安全放行、Flyway 基线迁移、Docker Compose PostgreSQL 依赖和本地开发说明。
- 验证：
  - `apps/web` 执行 `npm test`，2 个测试文件、2 个测试用例通过。
  - `apps/web` 执行 `npm run typecheck`，通过。
  - `apps/web` 执行 `npm run build`，通过并生成 `dist`。
  - `apps/api` 通过 Docker 化 Maven/JDK 21 执行 `mvn -q test`，2 个后端测试通过。
  - `docker compose config` 通过。
  - `docker compose up -d postgres` 后 PostgreSQL 健康检查达到 `healthy`。
  - Docker 化后端启动后访问 `http://localhost:18080/api/health`，返回 `200` 和 `{"service":"qherp-api","status":"UP"}`。
- 结论：工程骨架已具备前端测试与构建、后端 PostgreSQL 集成测试、Flyway 基线迁移、本地 PostgreSQL 依赖、后端启动和健康检查验收路径。账号权限业务模块尚未开始，不作为业务成果验收。
