# 037 任务六戊前端治理报告

## 基线核对

- 工作区：`F:\zhangqiao\AI-study\qherp-037-page-governance`
- 分支：`codex/037-page-standards-chinese-status`
- 起始 HEAD：`2101de8d85f666ddb130ce52b5916f3429a4cd02`
- 起始状态：`git status --short --branch` 显示 `## codex/037-page-standards-chinese-status...origin/main [ahead 8]`，无文件变更。

## 修改范围

仅修改 `apps/web/src/modules/cost/**`、`apps/web/src/modules/periodClose/**`、`apps/web/src/modules/gl/**` 和本报告。

- 成本：`costPageHelpers.ts`、`project/projectCostPageHelpers.ts` 增加本域中文兜底；成本记录详情/表单、项目成本工作台、调整表单/列表/详情和项目详情改用 helper；成本目录新增 `costStatusGovernance.spec.ts`。
- 业务月结：`periodClosePageHelpers.ts` 增加月结、期间、检查结果、领域、级别、动作、快照阶段中文兜底；工作台/详情/检查详情/快照/状态标签改用 helper；新增 `periodCloseStatusGovernance.spec.ts`。
- 总账：`glPageHelpers.ts` 增加来源、凭证类型、审批、借贷方向、期间、财务结账、制证规则、校验状态中文兜底；GL 查询区去除旧 `inline`；规则、凭证、科目、辅助、账簿、余额、试算页面去除右固定操作列；新增 `glStatusGovernance.spec.ts`。

## 设计选择

- 保留接口原始编码、筛选提交值、权限动作和状态机不变，只调整用户可见展示。
- 已知枚举优先使用本域冻结中文；未知枚举显示中文未知标签。服务端返回中文显示名时允许作为未知值显示文案，服务端返回原码时不裸露。
- 项目成本 `STALE` 调整为“来源已变化”；总账凭证 `POSTED` 保持“已记账”，并移除凭证详情中的 `POSTED 凭证` 可见文案。
- 按既有页面整改策略取消右固定操作列，依赖表格内部横向滚动保持操作可达，避免 1280×720 下固定列遮挡关键状态或字段。

## TDD 证据

红灯：

- `npm test -- src/modules/cost/costStatusGovernance.spec.ts src/modules/periodClose/periodCloseStatusGovernance.spec.ts src/modules/gl/glStatusGovernance.spec.ts`
- 结果：失败，3 个测试文件 13 项失败。成本目录 14 条状态风险、1 个旧查询、8 个右固定操作列；业务月结目录 4 条状态风险、2 个右固定入口；总账目录 6 条状态风险、12 个旧查询、6 个右固定操作列，并命中 `POSTED 凭证`。

绿灯：

- `npm test -- src/modules/cost/costStatusGovernance.spec.ts src/modules/periodClose/periodCloseStatusGovernance.spec.ts src/modules/gl/glStatusGovernance.spec.ts`
- 结果：通过，3 个测试文件，14 项通过。
- `npm test -- src/modules/cost src/modules/periodClose src/modules/gl`
- 结果：通过，9 个测试文件，61 项通过。
- `npm run typecheck`
- 结果：通过，`vue-tsc --noEmit` 无错误。

## 状态门禁

- 目标目录状态门禁：通过新增治理测试过滤 `apps/web/src/modules/cost/`、`apps/web/src/modules/periodClose/`、`apps/web/src/modules/gl/`，命中为 0。
- 全局状态语言门禁命令：`npm test -- src/test/statusLanguageScan.spec.ts`
- 全局结果：仍失败，剩余 9 条均在非本轮目录：`financialClose` 2 条、`platform` 4 条、`reports` 3 条；本轮目标目录无剩余命中。

## 页面规范项

- 目标三目录旧式 `inline` 查询表单已清零。
- 目标三目录右固定操作列已清零，操作列仍保留在表格内部横向滚动内容中。
- 直接状态/阶段/来源/校验/方向表格列已改为 helper 渲染。
- 未运行真实 1280×720 浏览器复核，按 brief 属后续 UI/测试角色真实桌面核验项。

## 风险分级

- 阻断：未发现目标目录内自动化阻断；目标目录状态门禁、定向测试和类型检查通过。
- 严重：目标三目录的真实桌面宽表最大右滚、抽屉和操作命中仍需独立 UI/测试复核，本报告不替代浏览器验收。
- 一般：全局状态门禁仍有 9 条非本轮目录风险，等待后续 `financialClose`、`platform`、`reports` 批次治理。
