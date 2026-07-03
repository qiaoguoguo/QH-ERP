# 生产执行视觉分析记录

## 基本信息

- 验收日期：2026-07-03 15:04:12 +08:00
- 分支：`codex/production-execution-planning`
- 截图基准提交：`71be3b1`
- 视觉分析目录：`docs/testing/production-execution-visual-audit/`
- 捕获工具：Playwright / Chromium，固定桌面视口 `1366x768` 和窄屏视口 `390x844`
- 捕获方式说明：本轮需要批量保存固定视口截图并校验文件大小，因此使用 Playwright/Chromium 作为可信浏览器截图工具；截图均来自本轮实际打开的本地系统。
- 截图策略：表格和滚动容器较多，全页截图容易拉长页面并弱化固定操作列状态，因此本轮使用可信视口截图。
- 服务地址：前端 `http://127.0.0.1:5174/`，后端 `http://127.0.0.1:18080`，PostgreSQL `127.0.0.1:15432`
- 测试账号：管理员 `admin / Qherp@2026!`；只读用户 `t8_read_20260703135941 / Qherp@2026!`
- 预检说明：产品设计插件用户上下文预检已执行，返回内容为 GoalCard 相关偏好，与本制造业 ERP 项目无关；本轮视觉分析未采用该上下文，依据仓库任务文档、测试计划和本地系统真实页面执行。

## 截图清单

| 文件 | 视口 | 页面路径 | 场景 | 文件大小 |
|---|---:|---|---|---:|
| `01-production-work-order-list-desktop.png` | 1366x768 | `/production/work-orders` | 工单列表默认状态 | 79223 |
| `02-production-work-order-result-desktop.png` | 1366x768 | `/production/work-orders` | 工单查询结果 | 37553 |
| `03-production-work-order-empty-desktop.png` | 1366x768 | `/production/work-orders` | 工单空状态 | 42762 |
| `04-production-work-order-form-desktop.png` | 1366x768 | `/production/work-orders/create` | 新建工单表单 | 47542 |
| `05-production-work-order-form-error-desktop.png` | 1366x768 | `/production/work-orders/create` | 表单必填校验错误 | 41594 |
| `06-production-bom-invalid-desktop.png` | 1366x768 | `/production/work-orders/10` | BOM 不可用发布错误 | 65025 |
| `07-production-work-order-detail-desktop.png` | 1366x768 | `/production/work-orders/7` | 工单详情和 BOM 用料快照 | 67348 |
| `08-production-material-issue-desktop.png` | 1366x768 | `/production/work-orders/7/material-issues` | 领料表单 | 45451 |
| `09-production-stock-not-enough-desktop.png` | 1366x768 | `/production/work-orders/9` | 领料过账库存不足错误 | 71650 |
| `10-production-work-report-desktop.png` | 1366x768 | `/production/work-orders/7/reports` | 报工表单 | 36497 |
| `11-production-completion-receipt-desktop.png` | 1366x768 | `/production/work-orders/7/completion-receipts` | 完工入库表单 | 38910 |
| `12-production-readonly-permission-desktop.png` | 1366x768 | `/production/work-orders/7` | 只读权限状态 | 60535 |
| `13-production-traceability-desktop.png` | 1366x768 | `/production/work-orders/8` | 完成后追溯视图 | 61590 |
| `14-production-work-order-mobile.png` | 390x844 | `/production/work-orders` | 窄屏工单列表 | 26224 |
| `15-production-detail-mobile.png` | 390x844 | `/production/work-orders/8` | 窄屏详情和明细横向滚动 | 34702 |

## 功能路径摘要

- 管理员视角覆盖工单列表、查询、空状态、新建表单、必填错误、BOM 不可用错误、详情、领料、报工、完工入库和完成后追溯。
- 库存不足场景使用工单 `MFG-WO-20260703065454560-004` 的草稿领料单触发过账，页面显示“生产领料库存不足”。
- 只读权限场景使用 `t8_read_20260703135941` 访问工单详情，页面可查看详情和用料快照，但不显示编辑、发布、领料、报工、完工入库、完成、取消或过账按钮。
- 完成后追溯场景使用工单 `MFG-WO-20260703065454056-003`，页面显示已过账领料、报工、完工入库和库存流水摘要。
- 窄屏场景覆盖 `390x844` 工单列表和工单详情明细，表格在窄屏下通过横向滚动承载更多列。

## 视觉结论

- 布局稳定性：桌面端列表、表单、详情和执行表单布局稳定，未发现加载残留、错误页、空白页或明显错场景截图。
- 信息密度：页面信息密度符合后台 ERP 风格，摘要指标、详情字段和分区表格可快速扫描。
- 关键操作可见性：桌面端新建、查询、重置、保存、发布、领料、报工、完工入库、完成、取消和过账按钮可见；只读用户写操作不可见。
- 数量列对齐：计划数量、报工数量、入库数量、应领、已领、未领和库存流水数量采用右对齐或固定数值显示，扫描性可接受。
- 状态可识别：草稿、已发布、生产中、已完成、已过账等状态标签颜色和文本清晰；异常提示使用红色警告条。
- 文案溢出：长工单号、物料编码和仓库名称在桌面表格中有截断或提示策略，未遮挡关键列；窄屏下长编码依赖表格横向滚动。
- 控件重叠：桌面端未发现控件重叠；固定操作列有轻微阴影覆盖感，但操作文本仍可识别。
- 响应式适配：390px 窄屏下侧栏转为顶部纵向区域，筛选区和表单控件纵向排列；明细表格需要横向滚动，未发现核心信息完全不可达。
- 权限状态识别：只读用户页面标题、用户标识和详情内容可见，写按钮消失，权限差异可以通过截图识别。

## 发现问题与处理结果

- 未发现阻断视觉问题。
- 低风险建议：窄屏详情中的明细表格需要横向滚动，且截图中只能看到部分列；当前不影响核心详情识别和任务 9 验收证据，但后续可以评估增加移动端表格列优先级或摘要化展示。
- 低风险建议：桌面列表和详情中的固定操作列阴影较明显，当前按钮文本可见，未构成阻断。
- 本轮按职责边界只记录视觉分析，不修改业务代码、测试代码或 UI。

## 最终结论

本轮共保存 15 张 PNG 截图，文件均存在且大小非 0。视觉分析覆盖任务 9 要求的列表、查询结果、空状态、表单、错误状态、详情、领料、报工、完工入库、只读权限、追溯和窄屏视口。未发现导致核心数据、关键按钮或权限状态无法识别的阻断视觉问题。该结论表示任务 9 浏览器视觉分析证据已归档，不代表阶段总验收完成；阶段总验收准备仍需后续审查判断。
