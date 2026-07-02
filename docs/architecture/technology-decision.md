# 技术选型决策记录

## 决策目标

为 QH ERP 建立第一版工程骨架选型，支撑账号与权限基础模块，并为后续物料、BOM、库存、生产工单、领料、报工、完工入库、成本归集等模块提供稳定扩展基础。

## 当前环境证据

- Node.js：`v22.17.0`
- npm：`10.9.2`
- Java：`1.8.0_321`
- Maven：`3.6.1`
- Docker：`28.2.2`
- Gradle：未安装全局命令

当前 Java 环境不足以支撑现代后端工程骨架。后端工程初始化前必须升级到 JDK 21 或更高版本，并通过项目内 Maven Wrapper 固化 Maven 版本，不能为迁就本机 Java 8 降级技术方案。

## 总体架构决策

采用前后端分离的分层单体架构：

- 前端应用：`apps/web`
- 后端服务：`apps/api`
- 数据库：PostgreSQL
- 文档：`docs`
- 本地验收：优先通过 Docker Compose 或明确的本地启动命令完成

一期不采用微服务。制造业单公司 ERP 当前核心风险是业务建模、数据一致性、权限控制和可追溯性，过早微服务化会增加复杂度，不利于阶段性验收。

## 前端选型

推荐：

- Vue 3
- TypeScript
- Vite
- Vue Router
- Pinia
- Element Plus
- Vitest
- Playwright

选择理由：

- ERP 前端以列表、表单、详情、弹窗、权限入口、状态反馈为主，Vue 3 与 Element Plus 适合中后台高频业务界面。
- Vite 与 TypeScript 适合建立轻量、可维护、可检查的工程骨架。
- Vue Router 和 Pinia 能清晰承载菜单权限、路由守卫、登录态和用户信息。
- Vitest 与 Playwright 分别覆盖组件/逻辑测试和浏览器端到端验收。

## 后端选型

推荐：

- JDK 21 作为最低开发基线，后续可评估升级到 JDK 25。
- Spring Boot 稳定版本线。
- Spring Security。
- Spring Data JPA。
- Flyway。
- PostgreSQL。
- OpenAPI 3.1 契约。
- JUnit 与 Spring Boot Test。
- Testcontainers 用于数据库集成测试。

选择理由：

- Spring Boot 适合构建可独立运行的生产级 Java 应用，适合企业内部 ERP 的长期维护。
- Spring Security 提供认证、授权和常见攻击防护能力，适合作为账号权限模块基础。
- PostgreSQL 适合承载关系复杂、事务一致性要求高的 ERP 数据。
- Flyway 将数据库变更脚本纳入版本管理，保证多环境结构一致。
- Spring Data JPA 可以减少基础 CRUD 样板；复杂报表或统计后续可通过专用查询层处理。
- Testcontainers 能让集成测试使用真实 PostgreSQL，减少测试数据库与生产数据库差异。

## 认证与权限决策

一期采用服务端可信权限模型：

- 前端负责菜单、路由、按钮和字段级显示控制。
- 后端负责接口鉴权、角色权限判断和关键操作拦截。
- 权限不能只依赖前端隐藏。
- 登录会话优先采用 HttpOnly Cookie 或等效安全方案，具体实现随工程初始化时与 Spring Security 配置一起确认。

## 数据与事务决策

- 数据库结构必须由迁移脚本管理。
- 库存、生产、成本、财务金额相关操作必须在后端定义事务边界。
- 核心单据必须有状态、创建人、更新时间和必要审计记录。
- 所有库存变化后续必须能追溯来源单据。

## 工程骨架最小内容

前端：

- 登录页占位。
- 主布局与导航。
- 基础路由。
- 路由守卫。
- 权限菜单模型。
- 请求封装。
- 错误提示和空状态示例。
- 列表、表单、详情基础页面示例。
- 构建、检查、测试、预览命令。

后端：

- 分层目录结构。
- 健康检查接口。
- 统一响应与错误码。
- 认证鉴权基础结构。
- 用户、角色、权限的基础领域模型。
- 数据库迁移目录。
- 审计字段基类或等效机制。
- 日志配置。
- 单元测试和集成测试示例。
- OpenAPI 契约输出能力。

部署验收：

- `.env.example` 或等效环境配置说明。
- 本地启动命令。
- Docker Compose 基线。
- 浏览器验收地址。
- 账号权限模块最短验收路径。

## 主要风险与处理

- 本机 Java 版本过低：先升级 JDK，再初始化后端，禁止降级到过时后端技术。
- 权限只做前端控制：后端接口必须强制鉴权。
- 数据库迁移缺失：从第一张表开始使用 Flyway。
- 接口契约不稳定：账号权限模块开始前先确定统一响应、错误码和分页结构。
- 组件库表格能力不足：工程骨架阶段必须验证列表、筛选、分页、批量选择、行内操作和状态展示。

## 官方资料依据

- Vue 快速开始：https://vuejs.org/guide/quick-start
- Vue TypeScript：https://vuejs.org/guide/typescript/overview
- Vite 指南：https://vite.dev/guide/
- Vue Router：https://router.vuejs.org/
- Pinia：https://pinia.vuejs.org/
- Element Plus：https://element-plus.org/
- Playwright：https://playwright.dev/
- Vitest：https://vitest.dev/guide/
- Spring Boot：https://docs.spring.io/spring-boot/index.html
- Spring Security：https://docs.spring.io/spring-security/reference/index.html
- Spring Data JPA：https://docs.spring.io/spring-data/jpa/reference/index.html
- PostgreSQL 文档：https://www.postgresql.org/docs/
- Flyway 迁移说明：https://documentation.red-gate.com/fd/migrations-271585107.html
- OpenAPI 规范：https://swagger.io/specification/
- Testcontainers PostgreSQL：https://testcontainers.com/modules/postgresql/

