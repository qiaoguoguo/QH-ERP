# 一期生产主链路端到端闭环验收与稳定化实施计划

> **给智能体执行者：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务逐项执行本计划。步骤使用复选框语法跟踪。

**目标：** 验证并稳定一期制造业 ERP 主链路，从主数据准备贯穿到成本业务记录追溯。

**架构：** 本阶段不预设新增业务功能。它围绕既有模块组织文档基线、自动化验证、浏览器验收、视觉证据、缺陷修复、审查和最终验收记录。

**技术栈：** Spring Boot、PostgreSQL、Flyway、Vue 3、TypeScript、Vite、Vitest、Playwright/Chromium、Docker、Git。

---

## 文件结构

- 新建 `docs/tasks/010-phase-one-e2e-stabilization.md`：阶段范围、角色职责、验收标准和缺陷阻断规则。
- 新建 `docs/testing/phase-one-e2e-test-plan.md`：测试矩阵、角色矩阵、异常矩阵、执行顺序和截图清单。
- 新建 `docs/testing/phase-one-e2e-execution-record.md`：执行证据和最终结果。
- 新建 `docs/testing/phase-one-e2e-defects.md`：缺陷登记和修复状态。
- 新建 `docs/testing/phase-one-e2e-visual-audit/notes.md`：视觉分析结构和后续截图清单。
- 修改 `docs/README.md`：补充新阶段文档链接。
- 修改 `docs/product/phase-one-scope.md`：记录六个里程碑完成后的端到端稳定化阶段。
- 修改 `docs/product/product-decisions.md`：记录端到端稳定化阶段边界。
- 仅在缺陷被复现并明确归属后，才修改前端或后端代码文件。

## 任务 1：建立阶段文档

**文件：**
- 新建：`docs/tasks/010-phase-one-e2e-stabilization.md`
- 新建：`docs/testing/phase-one-e2e-test-plan.md`
- 新建：`docs/testing/phase-one-e2e-execution-record.md`
- 新建：`docs/testing/phase-one-e2e-defects.md`
- 新建：`docs/testing/phase-one-e2e-visual-audit/notes.md`
- 修改：`docs/README.md`
- 修改：`docs/product/phase-one-scope.md`
- 修改：`docs/product/product-decisions.md`

- [x] **步骤 1：创建阶段任务文档**

新增 `docs/tasks/010-phase-one-e2e-stabilization.md`，包含阶段目标、包含范围、排除范围、五角色职责、验收标准、缺陷阻断规则和执行记录占位。

- [x] **步骤 2：创建阶段测试计划**

新增 `docs/testing/phase-one-e2e-test-plan.md`，包含端到端矩阵、角色权限矩阵、异常矩阵、数据一致性断言、执行顺序、视觉截图清单和缺陷分级规则。

- [x] **步骤 3：创建执行和缺陷记录**

新增：
- `docs/testing/phase-one-e2e-execution-record.md`
- `docs/testing/phase-one-e2e-defects.md`
- `docs/testing/phase-one-e2e-visual-audit/notes.md`

预期：所有文件只包含后续真实证据的记录结构，不提前声明通过。

- [x] **步骤 4：更新文档索引和产品决策**

修改：
- `docs/README.md`
- `docs/product/phase-one-scope.md`
- `docs/product/product-decisions.md`

预期：文档索引能找到新阶段，并明确该阶段不是新业务模块。

- [x] **步骤 5：验证文档格式**

运行：

```powershell
git diff --check
```

预期：退出码为 `0`。

## 任务 2：执行自动化基线验证

**文件：**
- 修改：`docs/testing/phase-one-e2e-execution-record.md`
- 如出现失败，修改：`docs/testing/phase-one-e2e-defects.md`

- [x] **步骤 1：运行后端全量测试**

按项目约定运行 Docker 化 JDK 21 Maven 测试命令。

预期：Maven 退出码为 `0`；如失败，记录失败并分级。

- [x] **步骤 2：运行前端全量测试**

在 `apps/web` 运行：

```powershell
npm test
```

预期：所有 Vitest 测试通过。

- [x] **步骤 3：运行前端类型检查**

运行：

```powershell
npm run typecheck
```

预期：退出码为 `0`。

- [x] **步骤 4：运行前端构建**

运行：

```powershell
npm run build
```

预期：退出码为 `0`。

- [x] **步骤 5：更新执行记录**

在 `docs/testing/phase-one-e2e-execution-record.md` 记录命令、退出码和关键输出摘要。

## 任务 3：执行浏览器主路径

**文件：**
- 修改：`docs/testing/phase-one-e2e-execution-record.md`
- 如出现缺陷，修改：`docs/testing/phase-one-e2e-defects.md`
- 新增：`docs/testing/phase-one-e2e-visual-audit/` 下的截图

- [ ] **步骤 1：确认本地服务**

检查 PostgreSQL、后端健康检查和前端路由可访问性。

预期：后端 `/api/health` 返回 `UP`，前端核心路由返回 HTTP `200`。

- [ ] **步骤 2：执行管理员端到端浏览器路径**

使用带时间戳的数据集完成：

```text
登录 -> 基础资料 -> BOM -> 期初库存 -> 工单 -> 领料 -> 报工 -> 完工入库 -> 成本记录 -> 追溯
```

预期：所有页面可达，所有状态变化可见。

- [ ] **步骤 3：验证数据一致性**

检查：
- 期初后原料库存增加。
- 领料后原料库存减少。
- 完工入库后成品库存增加。
- 领料生成材料成本记录。
- 报工生成人工成本记录。
- 完工入库出现在产出追溯中。

- [ ] **步骤 4：保存主路径截图**

保存 `docs/testing/phase-one-e2e-test-plan.md` 中列出的截图。

预期：PNG 文件存在且非空。

## 任务 4：执行权限和异常路径

**文件：**
- 修改：`docs/testing/phase-one-e2e-execution-record.md`
- 如出现缺陷，修改：`docs/testing/phase-one-e2e-defects.md`
- 新增：`docs/testing/phase-one-e2e-visual-audit/` 下的权限和异常截图

- [ ] **步骤 1：验证角色矩阵**

使用或创建以下账号：
- 管理员。
- 基础资料角色。
- 库存角色。
- 生产角色。
- 成本角色。
- 只读角色。
- 无权限角色。

预期：菜单、按钮、直接 URL 和 API 写操作与权限一致。

- [ ] **步骤 2：验证高风险异常**

测试：
- 未登录。
- 无权限 URL 和 API。
- BOM 不可用。
- 库存不足。
- 超领。
- 超报。
- 超完工入库。
- 重复过账。
- 非法成本金额或数量。
- 手工成本关联无效工单。

预期：返回受控错误，不产生部分数据污染。

- [ ] **步骤 3：更新记录**

在执行记录和缺陷文件中记录具体账号、URL、API 响应和截图。

## 任务 5：修复并审查已确认缺陷

**文件：**
- 只修改复现缺陷所需文件。
- 修改：`docs/testing/phase-one-e2e-defects.md`
- 修改：`docs/testing/phase-one-e2e-execution-record.md`

- [ ] **步骤 1：为每个缺陷分配归属**

分配给：
- 前端开发：路由、菜单、按钮、视觉、表单、浏览器问题。
- 后端开发：数据、事务、权限、审计、API 问题。
- 测试：证据缺失或验证缺口。

- [ ] **步骤 2：代码缺陷使用测试先行**

任何代码修复都必须：
- 先编写或更新失败测试。
- 确认测试失败。
- 实现最小修复。
- 确认测试通过。
- 运行相关回归测试。

- [ ] **步骤 3：执行规格审查**

使用合适的固定角色对照本任务文档和测试计划审查修复是否满足规格。

- [ ] **步骤 4：执行代码质量审查**

使用合适的固定角色检查可维护性、风险和回归覆盖。

- [ ] **步骤 5：重新执行受影响端到端检查**

预期：原缺陷不再复现，且没有新增高风险回归。

## 任务 6：收口证据和阶段结论

**文件：**
- 修改：`docs/testing/phase-one-e2e-execution-record.md`
- 修改：`docs/testing/phase-one-e2e-defects.md`
- 修改：`docs/testing/phase-one-e2e-visual-audit/notes.md`
- 修改：`docs/tasks/010-phase-one-e2e-stabilization.md`

- [ ] **步骤 1：补齐视觉分析记录**

记录：
- 截图清单。
- 视口。
- 账号角色。
- 页面路径。
- 结果。
- 视觉结论。
- 证据限制。

- [ ] **步骤 2：补齐缺陷汇总**

预期：
- 阻断缺陷数量为 `0`。
- 严重缺陷已修复或有明确非阻断决策。
- 一般缺陷有清晰后续说明。

- [ ] **步骤 3：补齐最终执行结论**

只有证据充分时，才使用以下结论：

```text
一期生产主链路已具备浏览器可操作、数据可追溯、权限一致和异常受控的验收基础。
```

- [ ] **步骤 4：执行最终验证**

运行：
- 后端全量测试。
- 前端全量测试。
- 前端类型检查。
- 前端构建。
- `git diff --check`。

预期：全部通过。

- [ ] **步骤 5：准备合并判断**

如果所有质量门通过，准备合入主分支并进入用户验收；如果任一质量门失败，保持目标继续推进并继续缺陷修复。
