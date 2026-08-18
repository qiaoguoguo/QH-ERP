# 系统操作知识库覆盖矩阵

本矩阵用于工作包三内容验收。状态“已覆盖”表示已有目标文章承接页面入口、核心操作、字段/状态、权限或边界说明；不表示功能验证通过。

| 模块 | 菜单/页面 | 路由 | 目标文章 | 覆盖重点 | 验收状态 |
|---|---|---|---|---|---|
| 通用 | 登录页 | `/login` | `common-login-navigation` | 登录账号、密码、失败原因、未登录跳转 | 已覆盖 |
| 通用 | 工作台与全局导航 | `/` | `common-login-navigation` | 侧栏、菜单权限、退出登录、返回审批 | 已覆盖 |
| 账号权限 | 用户管理 | `/accounts/users` `/system/users` | `common-account-users-roles` | 用户状态、角色分配、重置密码、权限影响 | 已覆盖 |
| 账号权限 | 角色管理 | `/accounts/roles` `/system/roles` | `common-account-users-roles` | 角色状态、权限配置、菜单和按钮授权 | 已覆盖 |
| 账号权限 | 角色权限 | `/accounts/roles/:id/permissions` `/system/roles/:id/permissions` | `common-account-users-roles` | 权限保存、重新登录、无权限表现 | 已覆盖 |
| 系统管理 | 业务期间 | `/system/business-periods` | `common-business-periods` | 开放、锁定、解锁、锁定期间写入限制 | 已覆盖 |
| 系统管理 | 知识库管理 | `/system/knowledge` `/system/knowledge/create` `/system/knowledge/:id/edit` | `system-knowledge-management` | 分类维护、文章新增编辑、关键词、关联路由、权限说明、关联知识、启停删除、保存失败 | 已覆盖 |
| 平台工作台 | 审批待办 | `/platform/approvals` | `common-approval-center` | 我的待办、已处理、我发起、审批详情、返回审批 | 已覆盖 |
| 平台工作台 | 消息中心 | `/platform/messages` | `common-messages-tasks` | 已读未读、业务通知、只读边界 | 已覆盖 |
| 平台工作台 | 任务中心 | `/platform/document-tasks` | `common-messages-tasks` | 导入导出、固定打印、任务状态、下载过期 | 已覆盖 |
| 基础资料 | 计量单位 | `/master/units` | `master-units-conversions` | 单位编码、启停、历史数据影响 | 已覆盖 |
| 基础资料 | 物料单位换算 | `/master/unit-conversions` | `master-units-conversions` | 主辅单位、换算比例、物料范围 | 已覆盖 |
| 基础资料 | 编码规则 | `/master/coding-rules` | `master-coding-rules` | 规则编码、对象类型、前缀、流水长度、日期模式 | 已覆盖 |
| 基础资料 | 仓库 | `/master/warehouses` | `master-warehouses` | 仓库类型、状态、库存单据可选范围 | 已覆盖 |
| 基础资料 | 供应商 | `/master/suppliers` | `master-suppliers-customers` | 供应商启停、采购和应付影响、历史导入入口 | 已覆盖 |
| 基础资料 | 客户 | `/master/customers` | `master-suppliers-customers` | 客户启停、销售和应收影响、历史导入入口 | 已覆盖 |
| 物料管理 | 物料分类 | `/materials/categories` | `material-categories-items` | 分类层级、启停、物料选择影响 | 已覆盖 |
| 物料管理 | 物料档案 | `/materials/items` | `material-categories-items` | 物料类型、来源属性、单位、状态、成本字段权限 | 已覆盖 |
| 物料管理 | 物料导入导出 | `/materials/items` | `material-import-export` | 中文模板、枚举下拉、导入失败处理 | 已覆盖 |
| 物料管理 | BOM | `/materials/boms` | `bom-draft-basic` | BOM 草稿、组件、导入导出、启停 | 已覆盖 |
| 物料管理 | BOM 工程变更和替代料 | `/materials/boms` | `bom-eco-substitute` | ECO 审批、应用、替代料范围、当前边界 | 已覆盖 |
| 库存管理 | 库存余额与价值 | `/inventory/balances` | `inventory-balances-availability` | 账面库存、合格现存、冻结、占用、可承诺 | 已覆盖 |
| 库存管理 | 库存流水与价值 | `/inventory/movements` | `inventory-movements-documents` | 来源追溯、收发方向、价值只读 | 已覆盖 |
| 库存管理 | 库存单据 | `/inventory/documents` | `inventory-movements-documents` | 单据查询、状态、来源链 | 已覆盖 |
| 库存管理 | 仓库调拨 | `/inventory/warehouse-transfers` | `inventory-warehouse-transfer` | 草稿、确认、出入库影响 | 已覆盖 |
| 库存管理 | 所有权转换 | `/inventory/ownership-conversions` | `inventory-ownership-conversion` | 公共/项目库存转换、审批、过账 | 已覆盖 |
| 库存管理 | 库存盘点 | `/inventory/stocktakes` | `inventory-stocktake` | 盘点、盘差、审批、过账 | 已覆盖 |
| 库存管理 | 估值调整 | `/inventory/valuation-adjustments` | `inventory-valuation-adjustment` | 成本价值调整、审批、权限 | 已覆盖 |
| 质量管理 | 质量确认 | `/quality/inspections` | `quality-inspections-status` | 待检、合格、不合格、冻结、解冻 | 已覆盖 |
| 采购管理 | 采购请购 | `/procurement/requisitions` | `procurement-requisition` | 请购提交审批、审批后询价、业务单据详情 | 已覆盖 |
| 采购管理 | 询价比价 | `/procurement/inquiries` | `procurement-inquiry-quotation` | 新建询价、供应商报价、报价导入导出、附件边界 | 已覆盖 |
| 采购管理 | 供应商报价选价 | `/procurement/inquiries/:id` | `procurement-selection-reason` | 选价、非最低原因、价格来源、结束询价 | 已覆盖 |
| 采购管理 | 价格协议 | `/procurement/price-agreements` | `procurement-price-agreement` | 从选价生成、提交审批、审批通过后作为采购价格来源 | 已覆盖 |
| 采购管理 | 采购订单 | `/procurement/orders` | `procurement-orders` | 来源选择、采购依据、履约进度、例外审批 | 已覆盖 |
| 采购管理 | 采购入库 | `/procurement/receipts` | `procurement-receipts` | 按订单入库、库存影响、到货计划 | 已覆盖 |
| 采购管理 | 采购退货 | `/procurement/returns` | `procurement-returns` | 退货草稿、确认、库存和应付影响 | 已覆盖 |
| 采购管理 | 采购在途供给 | `/procurement/effective-supplies` | `procurement-effective-supplies` | 未完成订单在途参考、计划/库存引用 | 已覆盖 |
| 销售管理 | 销售项目 | `/sales/projects` | `sales-projects-contract` | 项目草稿、合同、合同激活审批、历史导入入口 | 已覆盖 |
| 销售管理 | 销售报价 | `/sales/quotes` | `sales-quotes` | 报价草稿、提交审批、通过后使用 | 已覆盖 |
| 销售管理 | 销售订单 | `/sales/orders` | `sales-orders` | 订单确认、变更审批、信用例外、短交关闭 | 已覆盖 |
| 销售管理 | 交付计划 | `/sales/delivery-plans` | `sales-delivery-plans-shipments` | 订单交付安排、后续出库 | 已覆盖 |
| 销售管理 | 销售出库 | `/sales/shipments` | `sales-delivery-plans-shipments` | 出库、库存扣减、应收来源 | 已覆盖 |
| 销售管理 | 销售退货 | `/sales/returns` | `sales-returns` | 退货草稿、确认、库存和应收影响 | 已覆盖 |
| 销售管理 | 信用档案 | `/sales/credit-profiles` | `sales-credit-profiles` | 授信、额度、例外审批 | 已覆盖 |
| 销售管理 | 有效销售需求 | `/sales/effective-demands` | `sales-effective-demand` | 需求、预留、计划缺料引用 | 已覆盖 |
| 计划管理 | 订单缺料分析 | `/planning/material-requirements` | `planning-material-requirements` | 缺料口径、采购在途、不是完整 MRP | 已覆盖 |
| 生产管理 | 生产工单 | `/production/work-orders` | `production-work-orders` | 工单、BOM、发布、领料、报工、完工入口 | 已覆盖 |
| 生产管理 | 生产领料 | `/production/work-orders/:id/material-issues` | `production-material-issues` | 按工单领料、库存扣减 | 已覆盖 |
| 生产管理 | 生产报工 | `/production/work-orders/:id/reports` | `production-work-reports` | 工时、数量、成本来源 | 已覆盖 |
| 生产管理 | 完工入库 | `/production/work-orders/:id/completion-receipts` | `production-completion-receipts` | 完工数量、入库、质量状态 | 已覆盖 |
| 生产管理 | 生产退料 | `/production/material-returns` | `production-returns-supplements` | 退回库存、反向业务日期 | 已覆盖 |
| 生产管理 | 生产补料 | `/production/material-supplements` | `production-returns-supplements` | 追加领料、库存扣减 | 已覆盖 |
| 生产管理 | 外协执行 | `/production/outsourcing-orders` | `production-outsourcing` | 外协订单、发料、收货、当前边界 | 已覆盖 |
| 成本管理 | 项目成本核算 | `/cost/project-costs` | `cost-project-costs` | 项目成本归集、状态和来源 | 已覆盖 |
| 成本管理 | 成本调整/分配 | `/cost/project-cost-adjustments` | `cost-adjustments-variances` | 调整审批、分配、成本影响 | 已覆盖 |
| 成本管理 | 项目成本差异 | `/cost/project-cost-variances` | `cost-adjustments-variances` | 差异查询、只读分析 | 已覆盖 |
| 成本管理 | 成本记录 | `/cost/records` | `cost-records` | 成本事件记录、作废边界 | 已覆盖 |
| 财务往来 | 应收应付和收付款 | `/finance/receivables` `/finance/payables` `/finance/receipts` `/finance/payments` | `finance-ar-ap-overview` | 往来台账、收付款、结算状态 | 已覆盖 |
| 财务往来 | 发票和费用 | `/finance/sales-invoices` `/finance/purchase-invoices` `/finance/expenses` | `finance-invoices-expenses` | 发票确认、匹配、费用来源 | 已覆盖 |
| 财务往来 | 核销和凭证草稿 | `/finance/settlement-workbench` `/finance/voucher-drafts` | `finance-settlement-voucher-drafts` | 对账核销、凭证草稿、就绪和取消 | 已覆盖 |
| 业务月结 | 月结工作台 | `/period-close/runs` | `period-close-business-close` | 检查、关闭、重开、快照 | 已覆盖 |
| 会计核算 | 会计期间、科目、辅助、规则 | `/gl/accounting-periods` `/gl/accounts` `/gl/auxiliaries` `/gl/posting-rules` | `gl-basic-vouchers-ledgers` | 总账基础资料 | 已覆盖 |
| 会计核算 | 凭证、账簿、余额、试算 | `/gl/vouchers` `/gl/ledgers/general` `/gl/ledgers/detail` `/gl/account-balances` `/gl/trial-balance` | `gl-basic-vouchers-ledgers` | 凭证审批记账、不可变、账簿只读 | 已覆盖 |
| 会计核算 | 财务结账和资金税务 | `/gl/financial-close` `/gl/bank-accounts` `/gl/tax-summary` | `financial-close-funds-tax` | 结账检查、银行、税务、当前边界 | 已覆盖 |
| 经营报表 | 报表概览 | `/reports/overview` | `reports-overview` `report-permissions-data-boundary` | 实时口径、业务月结快照、脱敏、来源追溯 | 已覆盖 |
| 经营报表 | 销售、采购、库存、生产、成本、结算、异常报表 | `/reports/sales` `/reports/procurement` `/reports/inventory` `/reports/production` `/reports/cost` `/reports/settlement` `/reports/exceptions` | `reports-overview` `report-permissions-data-boundary` | 各经营口径、受限、不可用、不完整和快照差异 | 已覆盖 |
| 经营报表 | 项目利润和专项报表 | `/reports/project-profit` `/reports/project-profit/:projectId` `/reports/contract-collection` `/reports/procurement-variance` `/reports/inventory-capital` `/reports/receivable-payable` `/reports/operating-accounting` `/reports/financial-summary` | `reports-overview` `report-permissions-data-boundary` | 项目利润、合同回款、采购差异、库存资金、应收应付、经营会计对照、财务摘要 | 已覆盖 |
| 平台治理 | 数据修复 | `/platform/data-repairs` | `platform-data-repairs` | 申请、审批、执行、验证、不可复活 | 已覆盖 |
| 平台治理 | 历史导入 | `/platform/history-imports` | `platform-history-imports` | 固定适配器、预检、确认、全有全无 | 已覆盖 |
| 平台治理 | 交付资料 | `/platform/delivery-assets` | `platform-delivery-assets` | 固定目录、环境标识、只读边界 | 已覆盖 |
| 平台治理 | 导入导出通用规则 | 多页面入口 | `import-export-templates` | 模板、错误文件、文件限制、任务中心 | 已覆盖 |
| 平台治理 | 附件、审计、来源链 | 多页面详情 | `attachments-audit-source-chain` | 附件用途、审计、来源追溯只读 | 已覆盖 |
| 知识库边界 | AI 助手和外部模型 | 无当前业务路由 | `platform-current-boundaries` | 当前未实现 AI 助手，不代操作，不调用写接口 | 已覆盖 |
