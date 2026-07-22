# 037 任务六丙：采购与销售页面治理报告

## 基线核对

- 工作区：`F:\zhangqiao\AI-study\qherp-037-page-governance`
- 分支：`codex/037-page-standards-chinese-status`
- 起始 `HEAD`：`e2a262102ea720761fdd0db0b66a3af103c90890`
- 起始状态：`git status --short --branch` 为 `## codex/037-page-standards-chinese-status...origin/main [ahead 6]`，跟踪文件干净。
- 基准祖先关系：`git merge-base --is-ancestor e2a26210 HEAD` 通过。

## 修改范围

- 采购：`apps/web/src/modules/procurement/**`
- 销售：`apps/web/src/modules/sales/**`
- 新增/调整目录内定向测试：
  - `apps/web/src/modules/procurement/procurementStatusGovernance.spec.ts`
  - `apps/web/src/modules/sales/salesStatusGovernance.spec.ts`
  - `apps/web/src/modules/sales/salesPageHelpers.spec.ts`
  - `apps/web/src/modules/sales/salesFulfillmentPageHelpers.spec.ts`
  - 既有采购/销售 helper 与页面测试
- 未修改后端、共享门禁、阶段文档、依赖、迁移、服务配置或其他模块。

## 主要闭合项

- 采购状态中文治理：
  - 采购订单、请购、询价、报价、价格协议、到货计划、入库、在途、审批状态统一经过采购 helper。
  - `statusName || status`、`approvalStatusName || approvalStatus`、`inTransitStatusName || inTransitStatus`、`priceSourceTypeName || priceSourceType` 类用户可见原码兜底已清理。
  - `RELEASED` 在采购询价显示“已发布”；价格协议 `ACTIVE` 显示“生效中”；取消状态使用中性色。
- 销售状态中文治理：
  - 销售订单、出库、报价、审批、交付计划、订单变更、有效需求、销售项目、项目合同状态统一中文兜底。
  - 销售价格来源统一为“手工录入 / 报价带入 / 历史手工价”，未知值显示“未知价格来源”。
  - 销售项目 `ACTIVE` 显示“执行中”；取消状态使用中性色。
  - 有效需求排除原因在当前接口仅有原因码时显示“未知原因”，不猜测动态中文原因，不裸露原始编码。
- 页面规范治理：
  - 采购/销售目录旧式 `<el-form class="query-form" inline>` 已迁移为标签置顶查询表单。
  - 采购/销售目录右固定操作列已取消，操作列随表格内部横向滚动访问，避免固定列遮挡状态、金额和主字段。
  - 新增静态断言防止旧查询结构、右固定操作列、局部 `statusLabels[row.status]` 直出、取消状态失败色回归。

## TDD 证据

- 红灯命令：
  - `npm test -- src/modules/procurement/procurementStatusGovernance.spec.ts src/modules/sales/salesStatusGovernance.spec.ts src/modules/sales/salesPageHelpers.spec.ts src/modules/sales/salesFulfillmentPageHelpers.spec.ts src/modules/sales/projects/salesProjectPageHelpers.spec.ts`
  - 结果：失败，5 个测试文件失败，9 个断言失败；命中采购旧查询 7 处、采购右固定操作列 10 处、销售旧查询 3 处、销售右固定操作列 14 处、销售项目 helper 原码回退 2 处，以及销售价格来源/有效需求排除原因 helper 缺失。
- 绿灯命令：
  - `npm test -- src/modules/procurement/procurementStatusGovernance.spec.ts src/modules/sales/salesStatusGovernance.spec.ts src/modules/sales/salesPageHelpers.spec.ts src/modules/sales/salesFulfillmentPageHelpers.spec.ts src/modules/sales/projects/salesProjectPageHelpers.spec.ts`
  - 结果：5 个测试文件通过，17 个测试通过。
  - `npm test -- src/modules/procurement src/modules/sales`
  - 结果：32 个测试文件通过，245 个测试通过。
  - `npm run typecheck`
  - 结果：`vue-tsc --noEmit` 通过。

## 静态核对

- `rg -n 'fixed="right"|<el-form class="query-form" inline' apps/web/src/modules/procurement apps/web/src/modules/sales`
  - 结果：生产代码无命中；仅剩治理测试中的正则定义。
- `rg -n 'statusName\s*(\|\||\?\?)\s*status|approvalStatusName\s*(\|\||\?\?)\s*approvalStatus|inTransitStatusName\s*(\|\||\?\?)\s*inTransitStatus|priceSourceTypeName\s*(\|\||\?\?)\s*priceSourceType|labels\[[^\]]+\]\s*\?\?\s*(?:status|type|reason|code)|CANCELLED:\s*["'']danger["'']' apps/web/src/modules/procurement apps/web/src/modules/sales`
  - 结果：无命中。
- `git diff --check`
  - 结果：通过。

## 剩余风险

- 本轮未启动服务、未操作浏览器，未执行真实 1280×720 桌面复核；固定列遮挡与查询区视觉一致性仍需后续固定测试角色在真实桌面页面复验。
- `git diff --check` 输出了 Windows 换行提示，未发现空白错误；属于当前仓库换行策略提示。
- 未运行生产构建、后端测试、数据库迁移或服务相关验证，符合 brief 禁止范围。

## 提交口径

- 仅暂存并提交允许范围：`apps/web/src/modules/procurement/**`、`apps/web/src/modules/sales/**`、`.superpowers/sdd/task-6c-procurement-sales-governance-report.md`。
- 不推送、不合并、不变基、不清理。
