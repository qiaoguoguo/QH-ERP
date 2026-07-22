# 035、036 阶段合并实施计划

> **供智能体执行：** 必须使用 `superpowers:executing-plans` 在当前会话逐项执行；本任务属于主代理可直接处理的仓库治理与只读文档调整，不派发业务固定角色，不修改业务代码。

**目标：** 将原 035 与原 036 正式合并为唯一 `035 上线准备与最终生产交付`，删除当前路线中的独立 036，并消除当前有效文档之间的路线冲突。

**架构：** 以当前交接为唯一路线真相，README 和产品决策同步当前状态，操作手册与 034 阶段说明只修正面向未来的阶段边界。保留 000—034 历史交付事实和纯历史编号记录，035 内部固定为“上线准备与候选冻结、最终生产交付验证”两个顺序窗口。

**技术栈：** Markdown、PowerShell、Git、`rg`、`git diff --check`

## 全局约束

- 合并后的唯一阶段编号为 `035`，名称为“上线准备与最终生产交付”。
- 原 `036` 正式废止，不保留占位阶段，不再作为当前路线、依赖或后续能力引用。
- 035 不新增业务功能，不改变 000—034 的业务规则、状态机、接口语义、数据口径或历史验收结论。
- 035 内部保留两个连续窗口；窗口一未通过不得进入窗口二，两个窗口均通过才允许标记 035 完成。
- 不修改业务代码、迁移、配置、数据库、对象存储或运行环境。
- `docs/product/business-flow.md` 当前存在本任务开始前的未提交行尾状态，本计划不得编辑、暂存、覆盖或提交该文件。
- 所有提交只包含本计划明确列出的文件；不得使用破坏性 Git 命令清理现有工作区。

---

### 任务一：同步当前路线与项目状态真相

**文件：**

- 修改：`docs/handoffs/2026-07-12-project-handoff-current.md`
- 修改：`README.md`
- 修改：`docs/product/product-decisions.md`

**接口：**

- 输入：`docs/superpowers/specs/2026-07-22-stage-035-036-merge-design.md` 的已确认决策。
- 输出：当前路线只包含 020—035；034 为已交付，035 为唯一未启动最终阶段；README、产品决策与交接口径一致。

- [ ] **步骤一：记录修改前基线并确认无覆盖风险**

运行：

```powershell
git status --short --branch
git rev-parse HEAD
git diff -- docs/product/business-flow.md
```

预期：分支为 `main`；只显示本任务已有设计/计划提交之后的状态和既有 `docs/product/business-flow.md` 修改；该文件内容差异为空或仅为行尾状态，本任务不处理。

- [ ] **步骤二：修改当前交接的路线和后续阶段表述**

在 `docs/handoffs/2026-07-12-project-handoff-current.md` 完成以下精确语义变更：

```text
重新编排后的 020 至 035 阶段路线

| 035 | 上线准备与最终生产交付 | 权限矩阵、安全、性能、备份恢复、迁移演练、部署和运维基线；冻结候选上的全链路浏览器验收、相关桌面视觉检验、月结与财务闭环、完整交付包 | 020 至 034 功能完整 |
```

删除原独立 036 表格行，并把当前有效的“035 至 036”“034 至 036”“启动 035/036”分别收敛为“035”“034 至 035”“启动 035”。在交接顶部新增 2026-07-22 用户决策：原 035、036 合并，036 废止，035 保留两个顺序窗口且不新增业务功能。

- [ ] **步骤三：同步 README 当前阶段和下一阶段**

将 README 中“034 在功能分支推进”的陈旧状态替换为以下事实：

```text
034 已于 2026-07-22 正式交付；当前稳定正式阶段为 034。下一且唯一剩余阶段为 035 上线准备与最终生产交付，原 036 已合并进入 035 并废止独立编号。
```

同时把 034 范围条目改为已交付，并明确合并后的 035 只承担上线准备、候选冻结和最终生产交付验证，不新增业务功能。

- [ ] **步骤四：在产品决策顶部新增合并决策**

在 `docs/product/product-decisions.md` 顶部加入新章节：

```markdown
## 2026-07-22：035、036 合并为唯一最终阶段

- 路线决策：保留 035，名称调整为“上线准备与最终生产交付”；原 036 正式废止，不保留占位编号。
- 阶段结构：035 内部按“上线准备与候选冻结”“最终生产交付验证”两个顺序窗口执行，窗口一未通过不得进入窗口二。
- 范围边界：035 不新增业务功能；允许上线加固和阻断、严重缺陷修复。涉及业务契约、数据模型或财务口径变化时必须另建业务阶段或经用户确认调整范围，并重新冻结候选。
- 完成口径：两个窗口均通过、阻断和严重问题为 0、交付包完整后，035 才能完成；035 完成即代表当前第一版路线完成生产交付。
```

旧日期下的历史路线记录保留，不把历史事实改写成当时已经采用合并路线。

- [ ] **步骤五：验证任务一差异**

运行：

```powershell
rg -n -S '035|036|上线准备与非功能验证|最终生产交付验证|上线准备与最终生产交付' README.md docs/handoffs/2026-07-12-project-handoff-current.md docs/product/product-decisions.md
git diff --check -- README.md docs/handoffs/2026-07-12-project-handoff-current.md docs/product/product-decisions.md
git diff -- README.md docs/handoffs/2026-07-12-project-handoff-current.md docs/product/product-decisions.md
```

预期：当前交接和 README 不再把 036 作为独立后续阶段；产品决策顶部存在新决策，旧日期历史内容仍可识别为历史；`git diff --check` 无输出。

- [ ] **步骤六：提交当前路线同步**

```powershell
git add -- README.md docs/handoffs/2026-07-12-project-handoff-current.md docs/product/product-decisions.md
git diff --cached --check
git commit -m "合并035与036最终交付路线"
```

预期：提交成功，提交仅包含上述三个文件。

### 任务二：同步 034 边界与操作手册

**文件：**

- 修改：`docs/tasks/034-platform-delivery-governance.md`
- 修改：`docs/manual/system-operation-manual.md`

**接口：**

- 输入：任务一确定的唯一 035 名称与两个顺序窗口。
- 输出：034 和操作手册不再把原 035、036表述为两个未来阶段，但仍明确 034 未实现这些能力。

- [ ] **步骤一：修正 034 面向后续阶段的边界**

把 `docs/tasks/034-platform-delivery-governance.md` 中面向未来的表述统一为：

```text
034 只完成平台交付治理，不前移合并后 035 的安全加固、性能压测、备份恢复、部署运维、上线演练、候选冻结或最终生产全链路验收。
```

把“不把 035/036 写成当前能力”改为“不把合并后 035 写成当前能力”；把“把 035/036、排除项写成已交付”改为“把合并后 035、排除项写成已交付”。保留 034 管理员凭据必须在 035 轮换的记录。

- [ ] **步骤二：修正操作手册的阶段边界**

将 `docs/manual/system-operation-manual.md` 对 034 的末尾说明更新为：

```text
034 不处理合并后 035 的安全加固、性能压测、备份恢复、部署运维、上线演练、候选冻结或最终生产全链路验收。
```

不得把 035 写成已经可操作的当前页面或当前能力。

- [ ] **步骤三：验证任务二差异**

运行：

```powershell
rg -n -S '035|036|最终生产全链路|上线准备与最终生产交付' docs/tasks/034-platform-delivery-governance.md docs/manual/system-operation-manual.md
git diff --check -- docs/tasks/034-platform-delivery-governance.md docs/manual/system-operation-manual.md
git diff -- docs/tasks/034-platform-delivery-governance.md docs/manual/system-operation-manual.md
```

预期：两个文件只把合并后 035 作为未来边界；不再把 036 表述为独立未来阶段；历史 034 交付事实不变；`git diff --check` 无输出。

- [ ] **步骤四：提交边界同步**

```powershell
git add -- docs/tasks/034-platform-delivery-governance.md docs/manual/system-operation-manual.md
git diff --cached --check
git commit -m "同步034与合并后035边界"
```

预期：提交成功，提交仅包含上述两个文件。

### 任务三：执行全仓路线一致性验证

**文件：**

- 验证：`README.md`
- 验证：`docs/handoffs/2026-07-12-project-handoff-current.md`
- 验证：`docs/product/product-decisions.md`
- 验证：`docs/manual/system-operation-manual.md`
- 验证：`docs/tasks/034-platform-delivery-governance.md`
- 验证：`docs/superpowers/specs/2026-07-22-stage-035-036-merge-design.md`
- 验证：`docs/superpowers/plans/2026-07-22-stage-035-036-merge-implementation-plan.md`

**接口：**

- 输入：任务一、任务二的文档提交。
- 输出：当前路线唯一、历史可解释、无业务文件混入、工作区原有修改得到保留。

- [ ] **步骤一：搜索全部阶段引用并分类**

运行：

```powershell
rg -n -S '035|036|020 至 036|034 至 036|035 至 036|上线准备与非功能验证|最终生产交付验证|上线准备与最终生产交付' README.md docs AGENTS.md --glob '*.md'
```

预期：当前有效路线只出现合并后 035；036 只允许出现在本次合并设计/计划或明确标记为旧路线、已废止的历史上下文中。若发现仍会被理解为当前路线的 036 引用，只修正对应文档并重新运行本步骤。

- [ ] **步骤二：验证没有业务实现和无关修改混入**

运行：

```powershell
git status --short --branch
git diff --name-only origin/main..HEAD
git diff -- docs/product/business-flow.md
```

预期：本次提交只包含设计、计划和五个治理文档；`docs/product/business-flow.md` 仍保持任务开始前的未提交状态且未进入任何提交；没有 `apps/`、迁移、配置或工具文件变化。

- [ ] **步骤三：执行最终格式验证**

运行：

```powershell
git diff --check origin/main..HEAD
git log --oneline --decorate -5
```

预期：`git diff --check` 无输出；日志包含设计提交、计划提交和两次治理同步提交。

- [ ] **步骤四：记录最终结论**

在最终汇报中明确：

```text
当前第一版路线为 020—035；034 已交付；035 是唯一剩余阶段，名称为“上线准备与最终生产交付”；原 036 已废止。035 尚未启动或交付，本次仅完成目标与路线治理。
```

不得宣称 035 已实施、生产已上线或最终全链路已通过。
