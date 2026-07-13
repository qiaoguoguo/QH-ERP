# 本地开发与验证说明

## 目标

提供工程骨架阶段可重复执行的本地启动、测试和浏览器验收路径。

## 环境要求

- Node.js 22 或更高版本。
- npm 10 或更高版本。
- Docker Desktop 可用。
- 后端最低 JDK 21。当前本机 Java 8 不满足后端要求，因此后端测试优先使用 Docker 化 JDK 21。

## 环境变量

复制 `.env.example` 为 `.env` 后可按需调整本地数据库配置。默认值仅用于本地开发。

## 启动本地 PostgreSQL

```powershell
docker compose up -d postgres
docker compose ps
```

## 前端验证

```powershell
cd apps/web
npm install
npm test
npm run typecheck
npm run build
```

前端本地预览：

```powershell
cd apps/web
npm run dev
```

账号权限模块浏览器验收建议显式绑定本机可访问地址：

```powershell
cd apps/web
npx vite --host 0.0.0.0 --port 5173
```

前端 Vite 已配置 `/api` 代理到 `http://localhost:18080`。本地验收访问：

```text
http://127.0.0.1:5173
```

## 后端测试

当前建议使用 Docker 化 Maven 和 JDK 21 运行测试，并挂载 Docker socket 供 Testcontainers 创建 PostgreSQL 容器。

```powershell
$api=(Resolve-Path 'apps/api').Path.Replace('\','/')
docker run --rm `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal `
  -v "${api}:/workspace" `
  -v qherp-maven-repo:/root/.m2 `
  -v /var/run/docker.sock:/var/run/docker.sock `
  -w /workspace `
  maven:3.9.9-eclipse-temurin-21 `
  mvn test
```

`TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` 用于解决 Docker Desktop 场景下 Maven 容器回连 Testcontainers 容器端口的问题。

## 后端本地启动

如果本机已安装 JDK 21，可直接使用：

```powershell
cd apps/api
.\mvnw.cmd spring-boot:run
```

如果继续使用 Docker 化 JDK 21，可先启动 PostgreSQL，再运行：

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

健康检查地址：

```text
http://localhost:18080/api/health
```

预期响应包含：

```json
{"service":"qherp-api","status":"UP"}
```

如果本机 `8080` 被 Docker Desktop 或其他进程占用，仍按上面的 `18080:8080` 映射启动后端，前端代理无需调整。

## 阶段验收限制

阶段验收必须完成自动化测试、本地部署、浏览器功能验收和本阶段相关页面改动的桌面端视觉检验后，才通知用户进行业务成果验收。视觉检验不要求采集或保存截图，不测试或验收移动端、窄屏及响应式兼容性；该验收边界不取消设计和开发阶段的响应式适配要求。
