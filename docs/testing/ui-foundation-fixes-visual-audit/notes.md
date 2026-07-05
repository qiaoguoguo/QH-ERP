# 页面基础体验治理最终视觉审计记录

## 检查范围

- 记录更新时间：2026-07-06
- 截图采集时间：2026-07-05 至 2026-07-06
- 分支：`codex/ui-foundation-fixes`
- 前端：`http://127.0.0.1:5175`
- 后端：`http://127.0.0.1:18080`
- 主要账号：`admin`；权限差异截图使用无权限账号或只读账号场景
- 证据目录：`docs/testing/ui-foundation-fixes-visual-audit/`

## 证据口径

- `01-inventory-movements-980x720.png`、`02-material-categories-980x720.png`、`03-settlement-adjustment-create-1280x720.png` 为早期问题定位过程截图，只保留为历史过程证据，不作为本阶段最终视觉验收结论依据。
- 本记录以 `B-01` 至 `B-25` 浏览器视口截图作为当前阶段最终视觉审计证据。
- 截图均为可信浏览器视口截图；宽表、弹窗、抽屉和移动端场景不使用失真的全页拼接截图。
- `B-18-sales-report-1366x768.png` 与 `B-18-sales-report-trace-drawer-1366x768.png` 文件内容同源，最终表以能表达追溯抽屉场景的 `B-18-sales-report-trace-drawer-1366x768.png` 为准。

## 最终截图证据表

| 编号 | 文件 | 视口 | 页面/路径 | 覆盖点 | 处理结论 |
| --- | --- | --- | --- | --- | --- |
| B-01 | `B-01-login-1366x768.png` | 1366x768 | `/login` | 登录页品牌、账号密码表单、主按钮、首屏布局 | 登录入口品牌和表单层级清晰，未见控件遮挡。 |
| B-02 | `B-02-workbench-1366x768.png` | 1366x768 | `/` | 工作台壳层、深色侧栏、logo、顶部账号区、主内容边界 | 工作台骨架可识别，侧栏与主内容分区清晰。 |
| B-03 | `B-03-inventory-movements-scroll-980x720.png` | 980x720 | `/inventory/movements` | 主内容滚动、侧栏固定、库存菜单展开、列表中段 | 主内容滚动不带动侧栏，导航和列表不互相遮挡。 |
| B-04 | `B-04-inventory-movements-date-picker-1366x768.png` | 1366x768 | `/inventory/movements` | 搜索栏标签置顶、日期选择器弹层、日期输入对齐 | 日期弹层可见，日期框与其他筛选控件高度和底边对齐。 |
| B-05 | `B-05-inventory-movements-horizontal-scroll-980x720.png` | 980x720 | `/inventory/movements` | 宽表横向滚动、右侧列访问、分页布局 | 表格横向滚动可用，分页位于表格信息组底部。 |
| B-06 | `B-06-material-categories-980x720.png` | 980x720 | `/materials/categories` | 物料分类列表、窄桌面布局、操作列可见 | 分类树和表格无重叠，行操作可识别。 |
| B-07 | `B-07-material-category-dialog-1366x768.png` | 1366x768 | `/materials/categories` | 分类新增/编辑弹窗、遮罩、表单、底部按钮 | 弹窗尺寸受视口限制，标题、字段和操作按钮可见。 |
| B-08 | `B-08-bom-create-dialog-1366x768.png` | 1366x768 | `/materials/boms` | BOM 新增弹窗、多行明细、日期控件、底部保存 | BOM 弹窗内容和底部操作未被遮挡，明细区保持可读。 |
| B-09 | `B-09-bom-create-dialog-mobile-390x844.png` | 390x844 | `/materials/boms` | 移动端 BOM 弹窗、纵向布局、明细区域、底部操作 | 移动视口下弹窗可滚动，主容器未截断关键操作。 |
| B-10 | `B-10-purchase-orders-list-1366x768.png` | 1366x768 | `/procurement/orders` | 采购订单列表、搜索栏、日期范围、状态标签、行操作 | 搜索栏为标签置顶网格，日期框水平对齐，行操作清晰。 |
| B-11 | `B-11-sales-orders-empty-1366x768.png` | 1366x768 | `/sales/orders` | 销售订单空查询、空态、分页和查询栏稳定性 | 空态文案明确，查询栏和分页未挤压。 |
| B-12 | `B-12-production-work-order-create-errors-1366x768.png` | 1366x768 | `/production/work-orders/create` | 工单新建表单、必填错误、日期字段、保存按钮 | 错误提示可见，日期字段使用日期选择器，表单不丢失上下文。 |
| B-13 | `B-13-production-work-order-detail-1366x768.png` | 1366x768 | `/production/work-orders/:id` | 工单详情、执行记录、成本分区、返回路径 | 详情分区清晰，状态和关键操作可扫描。 |
| B-14 | `B-14-cost-records-list-1366x768.png` | 1366x768 | `/cost/records` | 成本记录列表、金额列、来源类型、查询区、分页 | 金额和来源字段可读，列表密度符合工作台页面。 |
| B-15 | `B-15-finance-receivables-list-1366x768.png` | 1366x768 | `/finance/receivables` | 应收台账列表、日期范围、来源单号、行操作、分页 | 财务列表筛选和表格样式一致，金额和操作可识别。 |
| B-16 | `B-16-settlement-adjustment-create-1366x768.png` | 1366x768 | `/finance/settlement-adjustments/create` | 往来冲减来源候选、冲减信息、日期控件、保存按钮 | 候选池与冲减信息区同屏可读，未见错误权限条。 |
| B-17 | `B-17-settlement-adjustment-second-source-1366x768.png` | 1366x768 | `/finance/settlement-adjustments/create` | 往来冲减第二条候选、选中态、金额联动 | 当前候选选择状态可识别，冲减信息随候选切换。 |
| B-18 | `B-18-sales-report-trace-drawer-1366x768.png` | 1366x768 | `/reports/sales` | 销售经营报表、指标区、宽表、追溯抽屉 | 报表筛选、指标和追溯承载使用统一工作台样式。 |
| B-19 | `B-19-forbidden-cost-records-1366x768.png` | 1366x768 | `/forbidden?from=/cost/records` | 无权限页、壳层、权限状态文案 | 无权限状态可识别，不用空白页替代错误说明。 |
| B-20 | `B-20-users-readonly-1366x768.png` | 1366x768 | `/accounts/users` | 只读账号用户列表、写操作隐藏、列表可读 | 只读权限差异清晰，新增/编辑等写操作未误导用户。 |
| B-21 | `B-21-inventory-document-create-error-1366x768.png` | 1366x768 | `/inventory/documents/create` | 库存单据错误提示、表单保留、按钮恢复 | 异常态提示明确，表单和操作区域仍可访问。 |
| B-22 | `B-22-sales-returns-list-1366x768.png` | 1366x768 | `/sales/returns` | 销售退货列表、退货状态、行操作、净额业务入口 | 反向业务列表样式与主业务列表保持一致。 |
| B-23 | `B-23-production-material-return-create-1366x768.png` | 1366x768 | `/production/material-returns/create` | 生产退料表单、候选来源、数量输入、保存按钮 | 生产反冲表单信息层级清晰，候选与数量操作可见。 |
| B-24 | `B-24-report-overview-mobile-390x844.png` | 390x844 | `/reports/overview` | 移动端经营概览、侧栏堆叠、筛选栏、报表入口 | 移动端主容器未截断业务内容，筛选和入口可触达。 |
| B-25 | `B-25-finance-payments-mobile-390x844.png` | 390x844 | `/finance/payments` | 移动端付款记录、搜索栏、表格横向滚动、分页、行操作 | 移动端表格和分页可操作，未见按钮文字溢出。 |

## 覆盖结论

- 登录、工作台壳层、深色侧栏、顶部账号区和移动端堆叠已纳入最终证据。
- 搜索栏统一为标签置顶网格样式，日期、文本、下拉控件和按钮组对齐有桌面与移动端截图证据。
- 日期选择器、日期弹层、清空后的空值口径、表单日期字段和报表日期筛选均被列为后续页面规范硬约束。
- 主列表默认分页、每页条数选择、宽表横向滚动、固定/可访问操作列、空态、异常态和权限态均有对应截图记录。
- 弹窗、抽屉和确认/提示承载已在 BOM、物料分类、报表追溯、库存异常等场景覆盖。
- 往来冲减多候选选择状态已通过 B-16、B-17 记录，不再用单测或空态替代浏览器视觉证据。

## 最终结论

本目录已补齐当前 UI 治理阶段的 B-01 至 B-25 最终浏览器截图记录。旧 01/02/03 过程图不再作为最终验收证据，本记录与 `docs/ui/page-standards.md`、`docs/handoffs/2026-07-05-ui-governance-stage-summary.md` 一并作为后续提交审查和恢复业务开发前的 UI 规范依据。
