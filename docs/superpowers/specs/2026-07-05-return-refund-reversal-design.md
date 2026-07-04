# 退货退款与业务反冲基础模块设计规格

## 目标

015 阶段补齐 QH ERP 的反向业务基础能力。系统必须支持销售退货、采购退货、生产退料、生产补料、往来冲减和经营报表净额同步，并保证已过账原单保持为不可变业务事实。

## 设计原则

- 不改写历史：已过账原单不可被退货、退款或反冲直接修改。
- 反向建模：所有修正都用独立反向单据和追溯链接表达。
- 单事务过账：库存、往来、成本、追溯和审计必须同事务完成。
- 后端可信：可退数量、可冲金额、库存可用量和权限以后端计算为准。
- 来源可追溯：原单能看到反向影响，反向单能回到原单。
- 权限不泄露：来源受限时返回脱敏信息。
- 报表可解释：固定经营报表必须展示原发生、反向发生和净额。

## 后端设计

新增 `com.qherp.api.system.reversal` 领域，集中承载销售退货、采购退货、生产退料补料、往来冲减和统一追溯查询。该模块调用库存、财务、生产和报表相关数据，但不把反向规则分散到既有控制器中。

V11 迁移新增：

- `biz_reversal_link`
- `sal_sales_return`
- `sal_sales_return_line`
- `proc_purchase_return`
- `proc_purchase_return_line`
- `mfg_material_return`
- `mfg_material_return_line`
- `mfg_material_supplement`
- `mfg_material_supplement_line`
- `fin_settlement_adjustment`

库存流水新增反向类型：

- `SALES_RETURN_IN`
- `PURCHASE_RETURN_OUT`
- `PRODUCTION_MATERIAL_RETURN_IN`
- `PRODUCTION_MATERIAL_SUPPLEMENT_OUT`
- `BUSINESS_REVERSAL` 仅保留为预留枚举，015 不单独生成泛化反冲库存流水。

服务层以 `JdbcTemplate` 和事务边界实现。过账时使用行级锁或条件更新重新校验累计数量、累计金额和库存可用数量，避免并发超退、超冲和重复影响。

创建接口支持 `clientRequestId` 幂等请求标识。同一来源和同一 `clientRequestId` 重复提交时返回已有详情；若核心字段不一致返回 `REVERSAL_DUPLICATED`。过账接口必须在事务内锁定反向单据、来源行、库存余额和往来台账，任何库存、往来、成本或追溯写入失败都整体回滚。

015 不建设泛化“业务反冲中心”或独立 `BUSINESS_REVERSAL` 单据。已过账业务反冲通过销售退货、采购退货、生产退料、生产补料和往来冲减这些具体反向单据表达，统一追溯接口只负责聚合查询。

## 前端设计

新增 `apps/web/src/modules/reversal`，并在现有销售、采购、生产、财务和报表页面中加入轻量入口。页面保持现有 ERP 后台模式：

- 列表：筛选、状态、来源单号、数量、金额、影响范围。
- 表单：来源选择、可退数量或可冲金额、明细数量、原因、业务日期。
- 详情：状态、原单、反向单、库存流水、往来冲减、成本影响、报表影响。
- 追溯面板：复用 014 的来源追溯思想，支持来源受限态。

路由和菜单映射：

| 功能 | 路由名 | 路径 | 菜单权限 | 操作权限 |
|---|---|---|---|---|
| 销售退货列表 | `sales-returns` | `/sales/returns` | `sales:return:view` | create/update/post/cancel 按按钮权限控制 |
| 销售退货创建 | `sales-return-create` | `/sales/returns/create` | `sales:return:create` | 无权限跳转无权限页 |
| 销售退货详情 | `sales-return-detail` | `/sales/returns/:id` | `sales:return:view` | 详情内按状态和权限展示操作 |
| 销售退货编辑 | `sales-return-edit` | `/sales/returns/:id/edit` | `sales:return:update` | 仅草稿可进入 |
| 采购退货列表 | `procurement-returns` | `/procurement/returns` | `procurement:return:view` | create/update/post/cancel 按按钮权限控制 |
| 采购退货创建 | `procurement-return-create` | `/procurement/returns/create` | `procurement:return:create` | 无权限跳转无权限页 |
| 采购退货详情 | `procurement-return-detail` | `/procurement/returns/:id` | `procurement:return:view` | 详情内按状态和权限展示操作 |
| 采购退货编辑 | `procurement-return-edit` | `/procurement/returns/:id/edit` | `procurement:return:update` | 仅草稿可进入 |
| 生产退料列表 | `production-material-returns` | `/production/material-returns` | `production:material-return:view` | create/update/post/cancel 按按钮权限控制 |
| 生产退料创建 | `production-material-return-create` | `/production/material-returns/create` | `production:material-return:create` | 无权限跳转无权限页 |
| 生产退料详情 | `production-material-return-detail` | `/production/material-returns/:id` | `production:material-return:view` | 详情内按状态和权限展示操作 |
| 生产退料编辑 | `production-material-return-edit` | `/production/material-returns/:id/edit` | `production:material-return:update` | 仅草稿可进入 |
| 生产补料列表 | `production-material-supplements` | `/production/material-supplements` | `production:material-supplement:view` | create/update/post/cancel 按按钮权限控制 |
| 生产补料创建 | `production-material-supplement-create` | `/production/material-supplements/create` | `production:material-supplement:create` | 无权限跳转无权限页 |
| 生产补料详情 | `production-material-supplement-detail` | `/production/material-supplements/:id` | `production:material-supplement:view` | 详情内按状态和权限展示操作 |
| 生产补料编辑 | `production-material-supplement-edit` | `/production/material-supplements/:id/edit` | `production:material-supplement:update` | 仅草稿可进入 |
| 往来冲减列表 | `finance-settlement-adjustments` | `/finance/settlement-adjustments` | `finance:settlement-adjustment:view` | create/update/post/cancel 按按钮权限控制 |
| 往来冲减创建 | `finance-settlement-adjustment-create` | `/finance/settlement-adjustments/create` | `finance:settlement-adjustment:create` | 无权限跳转无权限页 |
| 往来冲减详情 | `finance-settlement-adjustment-detail` | `/finance/settlement-adjustments/:id` | `finance:settlement-adjustment:view` | 详情内按状态和权限展示操作 |
| 往来冲减编辑 | `finance-settlement-adjustment-edit` | `/finance/settlement-adjustments/:id/edit` | `finance:settlement-adjustment:update` | 仅草稿可进入 |
| 追溯面板 | 组件内嵌 | 无独立路径 | `business:reversal:view` | 来源资源二次权限校验 |

未登录访问跳转登录页；已登录但无菜单权限访问路由时进入无权限页；有反向资源权限但无来源权限时，页面展示“来源无查看权限”受限态，不展示来源单号、主键、数量、金额、日期、状态或跳转链接。

建议前端文件：

- `apps/web/src/shared/api/returnRefundReversalApi.ts`
- `apps/web/src/modules/reversal/ReversalStatusTag.vue`
- `apps/web/src/modules/reversal/ReversalTracePanel.vue`
- `apps/web/src/modules/reversal/SalesReturnListView.vue`
- `apps/web/src/modules/reversal/SalesReturnFormView.vue`
- `apps/web/src/modules/reversal/SalesReturnDetailView.vue`
- `apps/web/src/modules/reversal/PurchaseReturnListView.vue`
- `apps/web/src/modules/reversal/PurchaseReturnFormView.vue`
- `apps/web/src/modules/reversal/PurchaseReturnDetailView.vue`
- `apps/web/src/modules/reversal/SettlementAdjustmentListView.vue`
- `apps/web/src/modules/reversal/SettlementAdjustmentFormView.vue`
- `apps/web/src/modules/reversal/SettlementAdjustmentDetailView.vue`
- `apps/web/src/modules/reversal/ProductionMaterialReturnListView.vue`
- `apps/web/src/modules/reversal/ProductionMaterialReturnFormView.vue`
- `apps/web/src/modules/reversal/ProductionMaterialReturnDetailView.vue`
- `apps/web/src/modules/reversal/ProductionMaterialSupplementListView.vue`
- `apps/web/src/modules/reversal/ProductionMaterialSupplementFormView.vue`
- `apps/web/src/modules/reversal/ProductionMaterialSupplementDetailView.vue`

## 数据流

销售退货：

```text
销售出库行 -> 销售退货草稿 -> 过账 -> 销售退货入库流水 -> 应收冲减 -> 报表销售净额 -> 追溯链
```

采购退货：

```text
采购入库行 -> 采购退货草稿 -> 过账 -> 采购退货出库流水 -> 应付冲减 -> 报表采购净额 -> 追溯链
```

生产退料：

```text
生产领料行 -> 退料草稿 -> 过账 -> 退料入库流水 -> 工单材料消耗净额 -> 成本净额 -> 追溯链
```

生产补料：

```text
生产工单物料 -> 补料草稿 -> 过账 -> 补料出库流水 -> 工单材料消耗增加 -> 成本净额 -> 追溯链
```

往来冲减：

```text
销售退货/采购退货/收付款冲减来源 -> 往来冲减草稿 -> 过账 -> 锁定应收或应付台账 -> 冲减可冲余额 -> 更新往来状态 -> 报表往来净额 -> 追溯链
```

## 错误处理

所有业务错误返回稳定错误码。页面显示业务语义明确的提示，不展示底层 SQL 或堆栈信息。关键错误包括来源不存在、来源状态非法、超退、超冲、库存不足、重复反冲、已过账不可编辑、权限不足和来源受限。

## 权限设计

反向业务按动作拆权限。菜单只展示用户具备的入口；按钮按操作权限显示；接口仍必须校验权限。来源详情必须二次校验来源模块权限，不得通过反向业务绕过销售、采购、生产、财务或报表权限。

## 测试设计

- 后端以 Testcontainers 覆盖事务、并发、库存、往来、成本和报表净额。
- 前端以 Vitest 覆盖 API、路由、菜单、页面状态和来源脱敏。
- 浏览器验收覆盖销售退货、采购退货、生产退料补料、往来冲减、报表净额和权限路径。
- 视觉分析覆盖桌面和窄屏。

## 范围控制

本设计不包含审批流、正式红字发票、税务、总账、凭证、结账、多组织、多币种、售后维修、质量检验体系、经营大屏或可配置 BI。

## 自检结论

- 无占位内容。
- 目标、接口契约、任务文档和测试计划范围一致。
- 本阶段可拆成独立实现任务。
- 反向业务不会被设计成直接修改历史原单。
