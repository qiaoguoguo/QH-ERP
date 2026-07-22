# 037 任务六己前端治理报告

## 基线核对

- 工作区：`F:\zhangqiao\AI-study\qherp-037-page-governance`
- 分支：`codex/037-page-standards-chinese-status`
- 起始 HEAD：`dd9dc8e0e3aee8b8ffab0a6228eb03838a2d5e24`
- 起始状态：`git status --short --branch` 显示 `## codex/037-page-standards-chinese-status...origin/main [ahead 9]`，无文件变更。
- 基准祖先核对：`git merge-base --is-ancestor dd9dc8e0e3aee8b8ffab0a6228eb03838a2d5e24 HEAD` 通过。

## 已读输入

- `AGENTS.md`
- `docs/tasks/037-full-page-standards-chinese-status-governance.md`
- `docs/ui/page-standards.md`
- `.superpowers/sdd/task-1-product-semantic-report.md`
- `.superpowers/sdd/task-2-ui-audit-report.md`
- `.superpowers/sdd/task-4-backend-contract-report.md`
- `.superpowers/sdd/task-5-qa-inventory-report.md`
- `.superpowers/sdd/task-7-qa-governance-gates-report.md`
- `.superpowers/sdd/task-6e-cost-period-close-gl-governance-report.md`
- `apps/web/src/test/statusLanguageScan.ts` 与 `apps/web/src/test/statusLanguageScan.spec.ts`

## 修改范围

仅修改 `apps/web/src/modules/finance/**`、`apps/web/src/modules/financialClose/**` 与本报告。

- 财务：`financePageHelpers.ts` 增加本域中文未知兜底和状态标签辅助函数，保留收付款 `POSTED=已过账`、凭证草稿 `READY=待正式制证`；取消态标签色改为中性；四个财务状态标签组件改用 helper 渲染未知值。
- 财务页面结构：应收、应付、收款、付款、销售/采购发票、费用、预收、预付、结算工作台、凭证草稿等列表/候选表移除旧 `inline` 查询和右固定操作列，保留原字段、按钮、分页与权限条件。
- 财务关账：`financialClosePageHelpers.ts` 增加关账状态、银行方向、借贷方向、账户类型、税种、纳税人类型、来源、检查级别、银行未达类型中文兜底。
- 财务关账页面结构：工作台、损益结转、银行账户、银行流水、银行对账、税款缴纳、税额汇总移除旧 `inline` 查询和右固定操作列。
- 财务关账状态显示：运行详情的检查级别、反结账申请状态、来源类型，损益结转预览方向，银行对账未达类型改用本域 helper。
- 新增定向测试：`financeStatusGovernance.spec.ts`、`financialCloseStatusGovernance.spec.ts`。

## 设计选择

- 只改展示层；接口原始编码、请求参数、权限码、动作条件、状态机和金额计算保持不变。
- 不做跨域全局翻译。财务收付款 `POSTED` 仍为“已过账”，财务关账/总账相关 `POSTED` 在关账域为“已记账”；损益结转预览的 `DEBIT/CREDIT` 用借贷方向，不复用银行方向。
- 未知枚举主界面显示中文兜底，如“未知来源”“未知结算状态”“未知税种”；不把原英文码作为主文案。
- 操作列取消 `fixed="right"`，继续放在 `.table-scroll` 内部横向滚动内容中，避免桌面宽表右固定列遮挡关键金额、状态或主字段。

## TDD 证据

红灯：

- `npm test -- src/modules/finance/financeStatusGovernance.spec.ts src/modules/financialClose/financialCloseStatusGovernance.spec.ts`
- 结果：失败，2 个测试文件 8 项失败。命中财务 16 个旧 `inline` 查询、17 个右固定操作列、取消态为 `danger`、helper 未知值回退原码；命中财务关账 2 条直接状态/方向列、7 个旧 `inline` 查询、6 个右固定操作列、helper 未知值回退原码。

绿灯：

- `npm test -- src/modules/finance/financeStatusGovernance.spec.ts src/modules/financialClose/financialCloseStatusGovernance.spec.ts`
- 结果：通过，2 个测试文件，9 项通过。

## 验证结果

- `npm test -- src/modules/finance src/modules/financialClose`
  - 结果：通过，17 个测试文件，77 项通过。
- `npm test -- src/test/statusLanguageScan.spec.ts`
  - 结果：仍失败，剩余 7 条均在非本轮目录：`platform` 4 条、`reports` 3 条；本轮 `finance`、`financialClose` 目标目录命中为 0。
- `npm run typecheck`
  - 结果：通过，`vue-tsc --noEmit` 无错误。
- 生产文件静态复核：
  - `rg -n 'class="query-form" inline|fixed="right"' -g '!*.spec.ts' apps\web\src\modules\finance apps\web\src\modules\financialClose`
  - 结果：无命中。

## 剩余风险

- 阻断：未发现本轮目标目录自动化阻断；目标目录状态门禁、定向测试和类型检查通过。
- 严重：真实 1280×720 桌面浏览器宽表最大右滚、弹窗/抽屉 footer 和操作命中仍需后续 UI/测试角色复核，本轮按 brief 未运行浏览器验收。
- 一般：全局状态语言门禁仍有 7 条非本轮目录风险，位于 `platform` 与 `reports`，不在本轮允许修改范围内。
