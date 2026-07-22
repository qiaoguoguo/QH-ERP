# 037 任务六乙：库存与质量页面治理报告

## 基线核对

- 工作区：`F:\zhangqiao\AI-study\qherp-037-page-governance`
- 分支：`codex/037-page-standards-chinese-status`
- 派发基准：`9efec20e800e7be8b093200c66daa8c73b1fcee1`
- 开始核对：
  - `git status --short --branch`：`## codex/037-page-standards-chinese-status...origin/main [ahead 5]`，开始时工作树干净。
  - `git rev-parse HEAD`：`9efec20e800e7be8b093200c66daa8c73b1fcee1`
  - `git merge-base --is-ancestor 9efec20e HEAD`：通过，基准包含于当前 HEAD。

## 已读输入

- `AGENTS.md`
- `docs/tasks/037-full-page-standards-chinese-status-governance.md`
- `docs/ui/page-standards.md`
- `.superpowers/sdd/task-1-product-semantic-report.md`
- `.superpowers/sdd/task-2-ui-audit-report.md`
- `.superpowers/sdd/task-4-backend-contract-report.md`
- `.superpowers/sdd/task-5-qa-inventory-report.md`
- `.superpowers/sdd/task-7-qa-governance-gates-report.md`
- `apps/web/src/test/statusLanguageScan.spec.ts`
- `apps/web/src/test/statusLanguageScan.ts`
- `apps/web/src/modules/inventory/**` 与 `apps/web/src/modules/quality/**` 相关实现和既有测试。

## 修改范围

- `apps/web/src/modules/inventory/inventoryPageHelpers.ts`
- `apps/web/src/modules/inventory/inventoryPageHelpers.spec.ts`
- `apps/web/src/modules/inventory/inventoryStatusGovernance.spec.ts`
- `apps/web/src/modules/inventory/InventoryBalanceListView.vue`
- `apps/web/src/modules/inventory/InventoryControlledDocumentView.vue`
- `apps/web/src/modules/inventory/InventoryCostLayerDrawer.vue`
- `apps/web/src/modules/inventory/InventoryMovementListView.vue`
- `apps/web/src/modules/inventory/tracking/InventoryTraceDrawer.vue`
- `apps/web/src/modules/inventory/tracking/TrackingAllocationEditor.vue`
- `apps/web/src/modules/inventory/tracking/TrackingAllocationReadonlyTable.vue`
- `apps/web/src/modules/inventory/tracking/TrackingPickerDrawer.vue`
- `apps/web/src/modules/inventory/tracking/trackingPayloadHelpers.ts`
- `apps/web/src/modules/quality/qualityPageHelpers.ts`
- `apps/web/src/modules/quality/qualityPageHelpers.spec.ts`
- `apps/web/src/modules/quality/qualityStatusGovernance.spec.ts`
- `apps/web/src/modules/quality/QualityInspectionListView.vue`
- `apps/web/src/modules/quality/QualityInspectionProcessDrawer.vue`
- `apps/web/src/modules/quality/QualityInspectionViews.spec.ts`
- `apps/web/src/modules/quality/QualityStatusTag.vue`
- `.superpowers/sdd/task-6b-inventory-quality-governance-report.md`

未修改静态门禁、共享公共层、后端、迁移、依赖、阶段文档和其他业务模块。

## 设计选择

- 在库存目录内集中扩展 `inventoryPageHelpers.ts`，覆盖库存单据、审批、调整、流水来源、质量状态、预约类型/状态、实物库存状态、追踪节点、成本层、估值状态与计价方式等中文展示。
- 在质量目录内新增 `qualityPageHelpers.ts`，只承载质量检验来源与处理状态的页面展示映射；质量库存状态标签复用库存目录的质量状态语义，避免复制状态机。
- 后端已提供的中文名称优先展示；当服务端名称为空、等于原始编码或未知编码时，统一落到确定性中文兜底，例如 `未知状态`、`未知类型`、`未知估值状态`，不把英文编码暴露给用户。
- 保持接口字段、业务编码、权限判断、状态机条件和库存/成本/质量计算不变；仅替换用户可见展示入口和日期控件格式。
- 页面规范只处理本目录内明确相关点：直接状态列、标签、表格 formatter、详情字段、抽屉与日期清空显示；未做跨模块视觉重构。

## TDD 红绿证据

- 先新增目录内状态治理断言和未知状态回归断言，初次运行：
  - `npm test -- src/modules/inventory/inventoryPageHelpers.spec.ts src/modules/inventory/inventoryStatusGovernance.spec.ts src/modules/quality/QualityInspectionViews.spec.ts src/modules/quality/qualityStatusGovernance.spec.ts`
  - 结果：失败，命中 `ARCHIVED`、`REVIEW_REQUIRED` 原码兜底，以及库存目录 4 处状态语言风险。
- 继续补直接服务端名称列扫描断言，初次运行：
  - `npm test -- src/modules/inventory/inventoryStatusGovernance.spec.ts src/modules/quality/qualityStatusGovernance.spec.ts`
  - 结果：失败，命中库存目录 14 处直接 `*Name` 状态列、质量目录 2 处直接 `*Name` 状态列。
- 实施后定向回归：
  - `npm test -- src/modules/inventory/inventoryPageHelpers.spec.ts src/modules/inventory/inventoryStatusGovernance.spec.ts src/modules/quality/qualityPageHelpers.spec.ts src/modules/quality/QualityInspectionViews.spec.ts src/modules/quality/qualityStatusGovernance.spec.ts`
  - 结果：通过，5 个测试文件、17 个测试。

## 目标目录门禁

- `apps/web/src/modules/inventory/inventoryStatusGovernance.spec.ts` 过滤 `scanStatusLanguage().risks` 到 `apps/web/src/modules/inventory/`，要求命中数为 0。
- `apps/web/src/modules/quality/qualityStatusGovernance.spec.ts` 过滤 `scanStatusLanguage().risks` 到 `apps/web/src/modules/quality/`，要求命中数为 0。
- 两个目录额外扫描直接 `prop="statusName"`、`prop="sourceTypeName"`、`prop="qualityStatusName"` 等用户可见状态名称列，要求命中数为 0。
- 全局 `npm test -- src/test/statusLanguageScan.spec.ts` 仍因本轮范围外模块失败，当前剩余 56 处风险属于成本、总账、结账、平台、采购等后续工作包；本报告不将其吸收入库存/质量任务。

## 验证结果

- `npm test -- src/modules/inventory src/modules/quality`：通过，10 个测试文件、96 个测试，最近一次耗时 31.34 秒。
- `npm run typecheck`：初次发现 `TrackingPickerDrawer.vue` 中 `qualityStatus` 类型过宽；已收窄为库存质量状态枚举后重新运行，退出码 0。
- `npm test -- src/test/statusLanguageScan.spec.ts`：失败，1 个测试失败、3 个测试通过；失败原因是本轮范围外模块仍有 56 处全局状态语言风险，库存/质量目录命中已由目录门禁断言归零。
- `git diff --check`：通过，退出码 0；仅输出 Windows 环境换行提示。

## 风险与待协同

- 阻断：无，库存/质量目录状态语言门禁已归零，定向测试与类型检查通过。
- 严重：无，未改变接口编码、状态机、权限或库存/质量业务计算。
- 一般：本轮未启动服务、未做真实桌面浏览器核对；库存/质量页面的最终真实桌面页面规范复验仍需由 UI/测试固定角色按阶段安排执行。
- 一般：全局状态语言门禁仍因其他模块 RED，不属于本任务允许修改范围。
- 一般：Git 在 Windows 环境对既有修改文件提示 `LF will be replaced by CRLF the next time Git touches it`，未专门调整换行策略。

## 提交记录口径

本报告随本轮允许范围代码一起提交。由于 Git 提交哈希由提交内容计算，不能在同一提交文件内自指写入最终哈希；最终提交哈希以完成后的固定角色回复为准。
