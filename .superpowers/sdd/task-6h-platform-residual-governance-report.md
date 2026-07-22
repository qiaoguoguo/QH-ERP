# 037 任务六辛：平台治理残留闭合报告

## 基线核对

- 工作区：`F:\zhangqiao\AI-study\qherp-037-page-governance`
- 分支：`codex/037-page-standards-chinese-status`
- 开始 HEAD：`4444716865d738907ca562c0e5c92689799a169e`
- 开始状态：`git status --short --branch` 显示工作树干净，分支相对 `origin/main` ahead 11。

## 已读输入

- `AGENTS.md`
- `docs/tasks/037-full-page-standards-chinese-status-governance.md`
- `docs/ui/page-standards.md`
- `.superpowers/sdd/task-1-product-semantic-report.md`
- `.superpowers/sdd/task-2-ui-audit-report.md`
- `.superpowers/sdd/task-4-backend-contract-report.md`
- `.superpowers/sdd/task-5-qa-inventory-report.md`
- `.superpowers/sdd/task-7-qa-governance-gates-report.md`
- `.superpowers/sdd/task-6a-frontend-core-governance-report.md`
- `.superpowers/sdd/task-6g-reports-governance-report.md`
- `.superpowers/sdd/task-6h-platform-residual-governance-brief.md`
- `apps/web/src/test/statusLanguageScan.ts`
- `apps/web/src/test/statusLanguageScan.spec.ts`

## 红测证据

- 新增 `apps/web/src/modules/platform/platformResidualGovernance.spec.ts` 后先运行：
  - `npm test -- src/modules/platform/platformResidualGovernance.spec.ts`
  - 结果：失败，1 个文件 4 个测试中 3 个失败。
- 红测精确暴露：
  - 全局状态语言门禁剩余 4 条 platform 命中：`DataRepairDetailView.vue` 的 `stage`/`status` 直接列、`row.code` 摘要回退、`HistoryImportDetailView.vue` 的 `row.errorCode || row.code` 回退。
  - platform 目录 7 个 `fixed="right"` 操作列。
  - `DocumentTaskCenterView.vue` 未知业务域回退原始 `domain`。

## 修改文件与闭合映射

- `apps/web/src/modules/platform/platformResidualGovernance.spec.ts`
  - 覆盖全部 14 个 platform Vue 表面。
  - 断言 platform 目录状态语言扫描为 0。
  - 断言 platform 表格不保留右固定操作列，不直接绑定状态语义列。
  - 断言错误码和未知业务域不作为用户主文案回退。
- `apps/web/src/modules/platform/dataRepairs/DataRepairDetailView.vue`
  - 预检与验证表格不再以 `prop="stage"`、`prop="status"` 直接渲染。
  - 阶段、结果改为中文 label 字段输出。
  - 摘要无后端中文文本时使用 `governanceErrorLabel`，未知码显示中文未知错误。
- `apps/web/src/modules/platform/historyImports/HistoryImportDetailView.vue`
  - 历史导入错误明细中错误码主列改为中文错误说明。
  - 无可识别错误码时使用中文未知错误，不把原始码作为主文案。
- `apps/web/src/modules/platform/documentTasks/DocumentTaskCenterView.vue`
  - 未知业务域显示 `未知业务域`。
  - 操作列取消右固定，保留原列序、动作按钮、权限和状态条件。
- `apps/web/src/modules/platform/messages/MessageCenterView.vue`
- `apps/web/src/modules/platform/dataRepairs/DataRepairListView.vue`
- `apps/web/src/modules/platform/approvals/ApprovalCenterView.vue`
- `apps/web/src/modules/platform/components/AttachmentPanel.vue`
- `apps/web/src/modules/platform/historyImports/HistoryImportListView.vue`
  - 取消右固定操作列，改为表格内部横向滚动中的非固定最小宽度列。
  - 保留原有按钮、链接、上传、下载、删除、查看详情和权限判断。

## 设计取舍

- 不改接口字段、权限判断、动作条件、路由和状态机。
- 技术码仍只在适配器码、模板码、权限码等既有技术识别语境中保留；状态、阶段、结果、动作和错误主文案不回退原码。
- 右固定列统一移除 `fixed="right"`，使用 `min-width` 保持操作可达，并继续依赖页面已有 `.table-scroll` 的表格内部横向滚动。

## 验证结果

- `npm test -- src/modules/platform/platformResidualGovernance.spec.ts`
  - 红：失败，3 个断言命中当前残留。
  - 绿：通过，1 个文件 4 个测试通过。
- `npm test -- src/test/statusLanguageScan.spec.ts`
  - 通过，1 个文件 4 个测试通过，全局状态语言门禁 0 命中。
- `npm test -- src/modules/platform`
  - 通过，7 个文件 63 个测试通过。
- 窄范围静态扫描：
  - `rg -n 'fixed="right"|fixed=''right''' apps\web\src\modules\platform`
  - `rg -n 'row\.message\s*\?\?\s*row\.summary\s*\?\?\s*row\.code|row\.errorCode\s*\|\|\s*row\.code|label\s*\?\?\s*domain\s*\?\?\s*''-''' apps\web\src\modules\platform`
  - `rg -n '<el-table-column[^\n]*(prop|property)="(action|reasonCode|resultStatus|sourceType|stage|status|type)"' apps\web\src\modules\platform`
  - 结果：均无命中。
- `npm run typecheck`
  - 通过，`vue-tsc --noEmit` 退出码 0。
- `git diff --check`
  - 退出码 0；输出仅包含 Windows 工作区行尾提示。

## 剩余风险

- 本轮按 brief 禁止浏览器验收，未做真实桌面 1280×720 视觉复验；右固定列已静态清零，仍建议后续由测试角色在交付窗口核对操作列可达性和页面横向溢出。
- 未运行全量前端测试、生产构建或浏览器检查，符合本 brief 禁止范围。
