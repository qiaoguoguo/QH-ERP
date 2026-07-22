# 037 任务六庚前端报告

## 基线核对

- 工作区：`F:\zhangqiao\AI-study\qherp-037-page-governance`
- 分支：`codex/037-page-standards-chinese-status`
- 开始 HEAD：`4f20543a78dc20ea13b8ed8ec385edd89a528f8a`
- 开始状态：`## codex/037-page-standards-chinese-status...origin/main [ahead 10]`，工作树干净
- 基准包含性：`4f20543a78dc20ea13b8ed8ec385edd89a528f8a` 是当前 HEAD 祖先

## 已读输入

- `AGENTS.md`
- `docs/tasks/037-full-page-standards-chinese-status-governance.md`
- `docs/ui/page-standards.md`
- `.superpowers/sdd/task-6g-reports-governance-brief.md`
- brief 列出的产品、UI、后端、QA、门禁与 6f 报告输入
- `apps/web/src/test/statusLanguageScan.ts`
- `apps/web/src/test/statusLanguageScan.spec.ts`
- `apps/web/src/modules/reports/**` 当前实现与测试

## 变更范围

- 新增 `apps/web/src/modules/reports/reportStatusGovernance.spec.ts`，覆盖 16 个 reports 配置路由、19 个 reports Vue 表面、reports 状态扫描、右固定来源/操作列、直接状态语义列、helper 未知回退与口径模式中文占位。
- 调整 `Report033Views.spec.ts` 旧断言，未知状态不再要求显示原始枚举。
- `reportPageHelpers.ts` 新增 reports 局部 `reportDictionaryText`，统一未知来源/状态中文回退，并补齐 reports 可见生产、结清、核销等状态中文。
- `operatingFinanceReportHelpers.ts` 将口径模式占位改为中文，并移除未知口径/阶段/原因中的原始枚举拼接。
- `ProductionReportView.vue`、`SettlementReportView.vue`、`ExceptionReportView.vue` 将直接状态/严重程度列改为中文显示函数。
- `ContractCollectionReportView.vue`、`ProcurementVarianceReportView.vue`、`InventoryCapitalReportView.vue`、`ReceivablePayableReportView.vue`、`OperatingAccountingReconciliationReportView.vue`、`ProjectProfitReportView.vue` 移除来源列 `fixed="right"`，保留列顺序、追溯按钮和表格内部横向滚动。

## TDD 证据

- 红测：`npm test -- src/modules/reports/reportStatusGovernance.spec.ts`
  - 结果：失败，4 项中 3 项失败。
  - 命中：reports 状态扫描 3 条、右固定来源列 6 条、原码回退 3 条。
- 绿测：`npm test -- src/modules/reports/reportStatusGovernance.spec.ts`
  - 结果：通过，1 个测试文件，4 项通过。

## 验证结果

- `npm test -- src/modules/reports`
  - 结果：通过，3 个测试文件，35 项通过。
- `npm test -- src/test/statusLanguageScan.spec.ts`
  - 结果：按预期仍失败，剩余 4 条均在 `apps/web/src/modules/platform/**`，reports 目录无命中。
- reports 目录补充扫描：
  - 命令：`rg -n 'fixed="right"|prop="(severity|status|sourceType|type|stage|reasonCode)"|未知(状态|来源|口径|阶段|原因)：|\?\?\s*(type|status|sourceType|reasonCode|basis|stage)' apps\web\src\modules\reports`
  - 结果：无输出，命令退出码 1，表示 0 命中。
- `npm run typecheck`
  - 结果：通过，`vue-tsc --noEmit` 无错误。
- `git diff --check`
  - 结果：通过，仅出现 Git 换行转换提示，无空白错误。

## 关注点

- 本轮未运行全量前端测试、生产构建或浏览器验收，符合 brief 禁止范围。
- 全局状态门禁仍受 platform 目录既有 4 条风险阻断，未在本任务允许范围内处理。
- 未修改接口、权限、路由配置、后端、迁移、依赖、共享层或阶段文档。
