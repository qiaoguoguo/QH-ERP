# 037 任务六丁前端报告

## 基线核对

- 工作区：`F:\zhangqiao\AI-study\qherp-037-page-governance`
- 分支：`codex/037-page-standards-chinese-status`
- 起始 HEAD：`84b5ffade648a613ac8ad0759b71f79d25062502`
- 起始状态：`git status --short --branch` 显示仅分支信息，跟踪文件干净。

## 修改范围

- `apps/web/src/modules/planning/**`
  - MRP 运行、建议、建议类型、覆盖、原因、供给、归属统一中文兜底。
  - 计划列表与详情表格移除旧 inline 查询与右固定操作列。
- `apps/web/src/modules/production/**`
  - 生产工单状态统一为“已下达/生产中/已完工”，外协状态统一为“已下达/加工中/已完成”。
  - 生产与外协单据 `POSTED` 保持“已过账”，取消状态使用中性色。
  - 生产与外协列表、执行宽表、成本记录表移除右固定操作列；列表筛选改为标准查询结构。
- `apps/web/src/modules/reversal/**`
  - 新增 `reversalPageHelpers.ts`，集中处理反向单据、来源类型、追溯方向、冲减类型、来源状态和目标状态中文兜底。
  - 追溯面板、结算调整、生产退料、生产补料改为 helper 输出，不再裸露原码。
  - 反向业务列表/候选表单移除旧 inline 查询与右固定操作列。
- 新增目录内治理测试：
  - `apps/web/src/modules/planning/planningStatusGovernance.spec.ts`
  - `apps/web/src/modules/production/productionStatusGovernance.spec.ts`
  - `apps/web/src/modules/reversal/reversalStatusGovernance.spec.ts`

## 红绿证据

- 红测：`npm test -- src/modules/planning/planningStatusGovernance.spec.ts src/modules/production/productionStatusGovernance.spec.ts src/modules/reversal/reversalStatusGovernance.spec.ts`
  - 首次失败：旧 inline 查询、右固定操作列、MRP 原码兜底、生产旧状态语义、取消失败色、reversal helper 缺失。
- 转绿：同命令最终 `3 passed / 17 passed`。
- 受影响目录测试：`npm test -- src/modules/planning src/modules/production src/modules/reversal`
  - 最终 `13 passed / 184 passed`。
- 类型检查：`npm run typecheck`
  - 最终通过。
- 有限静态复扫：`rg -n -S -e 'fixed="right"' -e '<el-form[^\n>]*class="query-form"[^\n>]*inline' -e '已发布|执行中|进行中' -e "CANCELLED:\s*'danger'" -e 'labels\[[^\]]+\]\s*\?\?\s*(value|sourceType|status|type)' apps/web/src/modules/planning apps/web/src/modules/production apps/web/src/modules/reversal --glob '!**/*.spec.ts'`
  - 无命中。
- `git diff --check`
  - 通过；仅 Windows 行尾提示。

## 状态门禁

- 目标目录状态门禁：通过新增三组治理规格，`planning`、`production`、`reversal` 命中为 0。
- 全局状态语言门禁：`npm test -- src/test/statusLanguageScan.spec.ts` 仍失败，剩余 33 条均在本轮允许范围外：
  - `cost`
  - `financialClose`
  - `gl`
  - `periodClose`
  - `platform`
  - `reports`

## 剩余风险

- 未执行浏览器真实桌面复验；本轮按 brief 只做前端测试、类型检查和静态门禁。
- 全局门禁仍有 33 条非本任务目录残留，需要后续对应业务域工作包处理。
- 本轮未触碰接口、权限判断、状态机、后端、迁移、服务、数据库或对象存储。
