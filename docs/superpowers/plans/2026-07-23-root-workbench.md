# 根工作台实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 将根路由 `/` 的历史占位页替换为权限安全、数据真实、层级清晰且符合 QH ERP 页面规范的综合工作台。

**架构：** 前端按当前账号权限并行编排现有审批、文档任务、经营概览、经营财务状态和异常清单只读接口。纯格式化、排序、分组与路由构造放入工作台辅助模块，页面组件只负责权限决策、加载编排和展示；任一分区失败不影响其他成功分区。

**技术栈：** Vue 3、TypeScript、Pinia、Vue Router、Element Plus、Vitest、Vue Test Utils、Vite。

## 全局约束

- 权威规格：`docs/superpowers/specs/2026-07-23-root-workbench-design.md`。
- 强制页面规范：`docs/ui/page-standards.md`。
- 只修改根工作台及其直接依赖，不新增业务模块、状态机、权限点、后端接口或业务写操作。
- 视觉结构采用用户选定的第三套方案：常用入口、我的工作流、本月经营概况、关键状态、业务关注。
- 状态和任务类型必须显示中文；未知枚举使用中文兜底，不直接展示英文值。
- 所有接口调用必须先通过 `authStore.hasPermission`，无权限不请求、不显示敏感数量、不用 `0` 伪装真实结果。
- `1280×720` 为主要桌面验收视口；页面不得出现关键内容重叠、按钮裁切或页面级横向滚动。
- 主代理独立完成实现和自验；全部代码完成后才派固定测试角色进行独立代码评审。

---

### 任务一：建立工作台纯数据适配层

**文件：**

- 新建：`apps/web/src/modules/workbench/workbenchPageHelpers.ts`
- 新建：`apps/web/src/modules/workbench/workbenchPageHelpers.spec.ts`

**接口：**

- 输入：现有 `ApprovalTaskRecord`、`DocumentTaskRecord`、`ExceptionReportRow` 和 `PageResult<T>`。
- 输出：
  - `formatWorkbenchMoney(value: string | number | null | undefined): string`
  - `formatWorkbenchNumber(value: string | number | null | undefined, suffix?: string): string`
  - `formatWorkbenchDateTime(value: string | null | undefined): string`
  - `clampProgress(value: number | null | undefined): number | null`
  - `taskDisplayName(record: DocumentTaskRecord): string`
  - `taskStatusText(status: string | null | undefined): string`
  - `exceptionTypeText(type: string | null | undefined): string`
  - `exceptionSeverityText(severity: string | null | undefined): string`
  - `mergeTaskPages(pages: PageResult<DocumentTaskRecord>[], limit: number): { total: number; items: DocumentTaskRecord[] }`
  - `approvalTitle(record: ApprovalTaskRecord): string`
  - `documentTaskRoute(id: string | number, status?: string): string`

- [ ] **步骤 1：先写金额、数字、时间和进度的失败测试**

```ts
expect(formatWorkbenchMoney('1286400.00')).toBe('¥1,286,400.00')
expect(formatWorkbenchMoney(null)).toBe('—')
expect(formatWorkbenchNumber('386.000', '件')).toBe('386 件')
expect(formatWorkbenchDateTime('2026-07-23T10:28:00+08:00')).toBe('2026-07-23 10:28')
expect(clampProgress(130)).toBe(100)
expect(clampProgress(-1)).toBe(0)
expect(clampProgress(undefined)).toBeNull()
```

- [ ] **步骤 2：运行辅助模块测试并确认因模块不存在而失败**

运行：

```powershell
npm test -- src/modules/workbench/workbenchPageHelpers.spec.ts
```

预期：失败，原因是 `workbenchPageHelpers.ts` 尚不存在。

- [ ] **步骤 3：实现最小格式化函数**

实现规则：

```ts
const numberFormatter = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 3 })
const moneyFormatter = new Intl.NumberFormat('zh-CN', {
  style: 'currency',
  currency: 'CNY',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})
```

非法数值统一返回 `—`；时间沿用平台页面的 `YYYY-MM-DD HH:mm` 口径；进度只允许 `0..100`。

- [ ] **步骤 4：运行测试并确认第一组转绿**

运行：

```powershell
npm test -- src/modules/workbench/workbenchPageHelpers.spec.ts
```

预期：第一组测试通过。

- [ ] **步骤 5：先写中文字典、排序、汇总和路由的失败测试**

```ts
expect(taskStatusText('RUNNING')).toBe('执行中')
expect(taskStatusText('VALIDATION_FAILED')).toBe('校验失败')
expect(taskStatusText('FUTURE_STATUS')).toBe('未知任务状态')
expect(taskDisplayName(materialImportTask)).toBe('物料主数据导入')
expect(exceptionTypeText('INVENTORY_SHORTAGE')).toBe('库存不足')
expect(exceptionSeverityText('CRITICAL')).toBe('严重')

const merged = mergeTaskPages([
  { items: [olderTask], total: 4, page: 1, pageSize: 10 },
  { items: [newerTask], total: 2, page: 1, pageSize: 10 },
], 1)
expect(merged.total).toBe(6)
expect(merged.items).toEqual([newerTask])

expect(approvalTitle(approvalWithName)).toBe('主合同')
expect(approvalTitle(approvalWithOnlyNo)).toBe('SC-001')
expect(documentTaskRoute(91, 'FAILED')).toBe('/platform/document-tasks?taskId=91&status=FAILED&returnTo=%2F')
```

- [ ] **步骤 6：运行测试并确认因函数缺失而失败**

运行：

```powershell
npm test -- src/modules/workbench/workbenchPageHelpers.spec.ts
```

预期：新增断言失败，且失败原因是目标函数尚未实现。

- [ ] **步骤 7：实现中文字典、合并排序和安全路由**

实现要求：

- 任务类型和状态优先复用 `platformPageHelpers.ts` 的现有中文映射。
- 异常类型与严重程度使用经营报表已有中文口径。
- 任务按 `completedAt ?? createdAt ?? ''` 倒序。
- `total` 为各查询页 `total` 的数值和，不以当前返回条数代替。
- 所有查询参数使用 `URLSearchParams` 构造，固定携带 `returnTo=/`。

- [ ] **步骤 8：运行辅助模块测试并确认全部通过**

运行：

```powershell
npm test -- src/modules/workbench/workbenchPageHelpers.spec.ts
```

预期：全部通过，无告警。

- [ ] **步骤 9：提交纯数据适配层**

```powershell
git add apps/web/src/modules/workbench/workbenchPageHelpers.ts apps/web/src/modules/workbench/workbenchPageHelpers.spec.ts
git commit -m "功能：新增工作台数据适配"
```

---

### 任务二：实现权限安全的根工作台页面

**文件：**

- 新建：`apps/web/src/modules/workbench/RootWorkbenchView.vue`
- 新建：`apps/web/src/modules/workbench/RootWorkbenchView.spec.ts`

**接口：**

- 消费任务一导出的格式化、中文字典、合并排序和路由函数。
- 消费 `useAuthStore()`、`documentPlatformApi`、`businessReportingApi`。
- 对外仅提供 Vue 页面组件，不导出业务写操作。

- [ ] **步骤 1：先写全权限加载与页面结构的失败测试**

测试准备：

```ts
const documentPlatformApiMock = vi.hoisted(() => ({
  approvalTasks: { list: vi.fn() },
  documentTasks: { list: vi.fn() },
}))
const businessReportingApiMock = vi.hoisted(() => ({
  overview: { get: vi.fn() },
  operatingFinanceOverview: { get: vi.fn() },
  exceptions: { list: vi.fn() },
}))
```

挂载全权限会话后断言：

```ts
expect(wrapper.get('[data-test="workbench-title"]').text()).toContain('工作台')
expect(wrapper.findAll('[data-test="workbench-quick-entry"]')).toHaveLength(5)
expect(documentPlatformApiMock.approvalTasks.list).toHaveBeenCalledWith({
  scope: 'TODO',
  page: 1,
  pageSize: 10,
})
expect(documentPlatformApiMock.documentTasks.list).toHaveBeenCalledTimes(5)
expect(businessReportingApiMock.overview.get).toHaveBeenCalledOnce()
expect(businessReportingApiMock.operatingFinanceOverview.get).toHaveBeenCalledWith({ analysisMode: 'LIVE' })
expect(businessReportingApiMock.exceptions.list).toHaveBeenCalledWith({ page: 1, pageSize: 10 })
expect(wrapper.text()).toContain('我的工作流')
expect(wrapper.text()).toContain('本月经营概况')
expect(wrapper.text()).toContain('关键状态')
expect(wrapper.text()).toContain('业务关注')
expect(wrapper.text()).not.toContain('RUNNING')
expect(wrapper.text()).not.toContain('CRITICAL')
```

- [ ] **步骤 2：运行页面测试并确认因组件不存在而失败**

运行：

```powershell
npm test -- src/modules/workbench/RootWorkbenchView.spec.ts
```

预期：失败，原因是 `RootWorkbenchView.vue` 尚不存在。

- [ ] **步骤 3：实现页面状态和权限化并行加载**

页面脚本使用明确的分区状态：

```ts
interface SectionState<T> {
  data: T | null
  loading: boolean
  error: string
}
```

加载规则：

- 审批权限存在时查询一次待办。
- 文档任务权限存在时并行查询五个状态。
- 经营概览、经营财务、经营异常分别按自身权限查询。
- 使用独立 `try/catch/finally` 或等价的 `Promise.allSettled` 隔离分区。
- 刷新时保留旧数据，只更新加载标识。
- 每轮至少一个授权分区完成后更新 `lastUpdatedAt`。

- [ ] **步骤 4：实现选定方案的页面结构与作用域样式**

根节点：

```html
<section class="module-page workbench-page">
```

固定结构：

```html
<header class="page-heading">...</header>
<section v-if="quickEntries.length" class="workbench-section workbench-quick">...</section>
<div class="workbench-primary-grid">
  <section class="workbench-section workbench-flow">...</section>
  <aside class="workbench-side">...</aside>
</div>
<section v-if="canViewExceptions" class="workbench-section workbench-attention">...</section>
```

样式约束：

- 使用 `var(--qherp-*)`。
- 主网格使用 `minmax(0, 1.7fr) minmax(320px, 1fr)`。
- 外层区块为白色、1px 边框、12px 圆角、无嵌套卡片阴影。
- 快捷入口使用可换行按钮网格。
- 工作流分组用标题行、细分隔线和连续列表。
- 数字使用 `font-variant-numeric: tabular-nums`。
- 断点小于等于 `1100px` 时主网格转单列，避免 `1280×720` 在侧栏存在时发生挤压。
- 所有按钮保持单行，最小点击高度不低于 32px。

- [ ] **步骤 5：运行页面测试并确认第一组转绿**

运行：

```powershell
npm test -- src/modules/workbench/RootWorkbenchView.spec.ts
```

预期：全权限结构与数据加载测试通过。

- [ ] **步骤 6：先写部分权限、空态和失败隔离的失败测试**

部分权限会话只授予 `platform:todo:view` 与 `sales:project:create`：

```ts
expect(documentPlatformApiMock.approvalTasks.list).toHaveBeenCalledOnce()
expect(documentPlatformApiMock.documentTasks.list).not.toHaveBeenCalled()
expect(businessReportingApiMock.overview.get).not.toHaveBeenCalled()
expect(wrapper.findAll('[data-test="workbench-quick-entry"]')).toHaveLength(1)
expect(wrapper.text()).not.toContain('本月经营概况')
expect(wrapper.text()).not.toContain('业务关注')
```

经营概览失败、审批成功时：

```ts
expect(wrapper.text()).toContain('经营概况加载失败')
expect(wrapper.text()).toContain('主合同')
expect(wrapper.text()).not.toContain('¥0.00')
```

全分区空数据时：

```ts
expect(wrapper.text()).toContain('当前没有需要处理的事项')
expect(wrapper.text()).toContain('当前期间暂无经营概况')
expect(wrapper.text()).toContain('当前没有需要关注的经营异常')
```

- [ ] **步骤 7：运行页面测试并确认新增场景失败**

运行：

```powershell
npm test -- src/modules/workbench/RootWorkbenchView.spec.ts
```

预期：至少一个部分权限、失败隔离或空态断言失败。

- [ ] **步骤 8：补齐部分权限、失败隔离、空态和刷新交互**

实现要求：

- 无权限分区不渲染且不发请求。
- 工作流没有任何数据权限时显示权限说明，不显示虚假计数。
- 分区错误使用紧凑错误条和 `重新加载` 按钮。
- 点击页面 `刷新` 后，各授权接口调用次数增加一次。
- 单分区 `重新加载` 只重试对应分区。

- [ ] **步骤 9：运行页面测试并确认全部通过**

运行：

```powershell
npm test -- src/modules/workbench/RootWorkbenchView.spec.ts
```

预期：全部通过，无未处理 Promise 或控制台告警。

- [ ] **步骤 10：提交工作台页面**

```powershell
git add apps/web/src/modules/workbench/RootWorkbenchView.vue apps/web/src/modules/workbench/RootWorkbenchView.spec.ts
git commit -m "功能：实现综合工作台页面"
```

---

### 任务三：接入根路由并锁定登录门禁

**文件：**

- 修改：`apps/web/src/router/index.ts`
- 修改：`apps/web/src/router/permissionGuard.spec.ts`

**接口：**

- 根路由 `name=home`。
- 根路由 `meta.requiresAuth=true`。
- 根路由组件懒加载 `../modules/workbench/RootWorkbenchView.vue`。

- [ ] **步骤 1：先把旧占位测试改为真实组件失败测试**

测试内容：

```ts
it('根路径加载真实工作台并要求登录', async () => {
  const router = createQhErpRouter()
  const route = router.getRoutes().find((item) => item.name === 'home')
  const component = route?.components?.default as (() => Promise<unknown>) | undefined

  expect(route?.path).toBe('/')
  expect(route?.meta.requiresAuth).toBe(true)
  expect(component).toBeTypeOf('function')
  await expect(component?.()).resolves.toHaveProperty('default', RootWorkbenchView)
})
```

- [ ] **步骤 2：运行路由测试并确认仍为占位组件而失败**

运行：

```powershell
npm test -- src/router/permissionGuard.spec.ts
```

预期：失败，原因是根路由仍是同步占位渲染函数且没有 `requiresAuth`。

- [ ] **步骤 3：替换根路由配置**

目标配置：

```ts
{
  path: '/',
  name: 'home',
  meta: { requiresAuth: true },
  component: () => import('../modules/workbench/RootWorkbenchView.vue'),
},
```

- [ ] **步骤 4：运行路由与工作台定向测试**

运行：

```powershell
npm test -- src/modules/workbench/workbenchPageHelpers.spec.ts src/modules/workbench/RootWorkbenchView.spec.ts src/router/permissionGuard.spec.ts src/App.spec.ts
```

预期：全部通过。

- [ ] **步骤 5：提交路由接入**

```powershell
git add apps/web/src/router/index.ts apps/web/src/router/permissionGuard.spec.ts
git commit -m "功能：将根路由接入综合工作台"
```

---

### 任务四：完成设计对比、真实浏览器检查和阶段验证

**文件：**

- 新建或更新：`design-qa.md`
- 按发现差异修改：`apps/web/src/modules/workbench/RootWorkbenchView.vue`
- 如行为发生变化，先修改：`apps/web/src/modules/workbench/RootWorkbenchView.spec.ts`
- 更新：`docs/superpowers/plans/2026-07-23-root-workbench.md`

- [ ] **步骤 1：运行受影响前端验证**

```powershell
npm test -- src/modules/workbench/workbenchPageHelpers.spec.ts src/modules/workbench/RootWorkbenchView.spec.ts src/router/permissionGuard.spec.ts src/App.spec.ts
npm run typecheck
npm run build
```

预期：全部通过。

- [ ] **步骤 2：从当前分支启动前后端验收服务**

使用仓库现有启动方式和现有数据库，不创建演示性替代页面。确认：

- 后端健康检查正常。
- 前端根路径可访问。
- 使用真实超级管理员会话进入 `/`。

- [ ] **步骤 3：按选定视觉方案执行第一次同视口对比**

在 `1440×900` 和 `1280×720` 打开真实工作台，逐项比较：

- 常用入口位置与密度。
- 工作流为主视觉焦点。
- 经营概况和关键状态位于右侧或窄宽度下自然下移。
- 业务关注为整宽连续清单。
- 黑白中性色为主，状态色克制。
- 无卡片套卡片、无营销大留白、无按钮裁切、无页面级横向滚动。

将差异、严重级别和结论写入 `design-qa.md`。

- [ ] **步骤 4：对设计差异进行一次集中修正**

- 行为或权限差异：先补失败测试，再修代码。
- 纯排版收敛：在现有通过测试保护下调整作用域样式。
- 修复 P0、P1、P2；P3 只记录，不反复阻断。

- [ ] **步骤 5：执行差异复验并将设计结论改为通过**

重新打开相同视口和相同数据状态，对比选定视觉方案。只有 `design-qa.md` 明确包含 `final result: passed` 才继续。

- [ ] **步骤 6：执行唯一交付前全量验证窗口**

按仓库既有命令一次性完成：

- 全量后端测试。
- 全量前端测试。
- 前端类型检查。
- 前端生产构建。
- 数据库迁移回归。
- 真实桌面浏览器功能与视觉检查。
- 服务健康、控制台错误和空白检查。

先统一记录全部结果；如发现缺陷，汇总后集中修复，只复验差异和受影响路径。

- [ ] **步骤 7：提交设计验证和必要修正**

```powershell
git add design-qa.md docs/superpowers/plans/2026-07-23-root-workbench.md apps/web/src/modules/workbench/RootWorkbenchView.vue apps/web/src/modules/workbench/RootWorkbenchView.spec.ts
git commit -m "验证：完成工作台设计与交付检查"
```

只暂存实际发生变化的文件。

---

### 任务五：固定测试角色代码评审与交付

**文件：**

- 按评审结论修改本阶段文件。
- 更新当前交接文档中的分支、提交、验证、服务和遗留项。

- [ ] **步骤 1：向固定测试角色派发独立代码评审**

派发内容必须包含：

- 角色：固定测试。
- 权威工作区：`C:\Users\14567\.codex\worktrees\00e8\qherp`。
- 分支：`codex/040-root-workbench`。
- 基准：`origin/main`。
- 审查范围：本分支相对 `origin/main` 的全部代码差异。
- 重点：权限调用、只读边界、错误隔离、中文状态、路由安全、测试覆盖和回归风险。
- 禁止：编辑文件、运行数据库写操作、提交、推送或扩展范围。

- [ ] **步骤 2：汇总评审意见并处理阻断与严重问题**

每个需要修改的代码问题都先添加或调整失败测试，确认失败后再修复。一般问题登记后续清单，不反复阻断。

- [ ] **步骤 3：请固定测试角色只复审修复差异**

只有差异复审仍存在阻断或严重问题时才继续返工。

- [ ] **步骤 4：更新交接、提交、合入并推送**

确认：

- 功能分支干净。
- 全量验证结论仍有效。
- 测试角色评审已闭合。
- 主分支没有混入用户未提交文件。
- `main` 与 `origin/main` 最终哈希一致。

- [ ] **步骤 5：从主分支启动验收服务并交用户验收**

提供：

- 可点击本地浏览器地址。
- 验收路由 `/`。
- 已实现功能摘要。
- 主要权限、空态和失败态说明。
- 测试与代码评审结论。
