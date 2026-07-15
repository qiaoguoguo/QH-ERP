# 任务记录：024 采购深化与项目专采

## 实现计划要求

- 本文是 024 阶段唯一权威说明，同时承载业务目标、范围、状态机、权限、接口、数据迁移、工作包、验收矩阵、阻断规则、变更记录和执行记录；不得另建含义重复的规格、计划、测试计划或接口契约文档。
- 2026-07-15 用户已授权主代理自主决策并持续推进 024；固定五角色已完成唯一一轮 024/025 连续阶段目标讨论，主代理已合并五角色意见；一次交叉规格审核已完成并合并整改，本文为最终冻结口径。
- 024 与 025 分别独立交付。024 完成验收后才启动 025；024 不提前实现销售报价、销售信用、项目履约、缺料净算、自动供给建议或后续财务能力。
- 024 采用“阶段整包、并行工作包、集中审查、一轮集中整改、差异复审、唯一交付前全量验证窗口”。开发期间只运行受影响测试、定向接口测试、必要类型检查和定向迁移验证。
- 后端、前端和独立测试仍采用测试先行；测试与实现属于同一完整能力工作包，不把红测、编码、转绿、审查和返工机械拆成独立流程任务。
- 设计和开发必须保留响应式、窄屏和触屏结构能力；测试、集中审查和阶段验收只覆盖桌面端，不保存截图，不建立视觉截图目录。
- 所有项目沟通、文档、界面文案、审查意见和验收记录使用中文；接口、路径、枚举、命令和代码标识等技术标识可保留原文。
- 当前开发应基于独立分支或隔离工作树推进。未通过本文完成定义前不得合入或推送 `main`。

## 阶段状态

- 阶段编号：`024`
- 阶段名称：`采购深化与项目专采`
- 当前状态：整包开发、集中审查、集中整改、差异复审和唯一交付前全量验证均已完成，无遗留阻断或严重问题；待提交功能分支、合入并推送 `main`、从主分支启动验收服务。
- 权威说明基线：`origin/main@8582536939d274ca9e9d07a45a463be1ce2eda9f`；实现分支：`codex/024-procurement-project-sourcing`。
- 权威输入：根目录 `AGENTS.md`、当前交接、020 至 023 权威任务说明、产品决策记录、现有采购/库存/审批实现契约、固定五角色 024/025 目标讨论结论、一次交叉规格审核事实和用户最新指令。
- 当前工作区已有用户授权的交接文档治理修改；024 开发不得覆盖、整理或回退非本阶段文件改动。
- 固定五角色已按本文完成实现、集中审查、整改、差异复审和交付前验证；交付前不得新增业务范围或把 025/026 能力提前并入本阶段。

## 背景与输入

### 上游已交付能力

- 020 已提供销售项目和合同主维度，`ACTIVE` 项目可作为项目专采归属对象；历史业务允许无项目归属，不自动伪造项目。
- 021 已提供供应商结算税务资料、物料成本属性、库存计价分类、单位换算、后端编码规则和 BOM 治理基础。
- 022 已提供固定审批、待办、消息、附件、固定打印、导入导出文档任务、权限、审计和对象存储基础。
- 023 已提供公共移动加权平均池、项目真实来源成本层、不可变价值流水、公共/项目所有权、受控所有权转换、盘点、估值调整和成本权限脱敏。
- 现有采购基础已提供采购订单、采购入库、采购在途、来源追溯和应付来源；采购入库当前默认进入公共库存。采购退货真实入口已由 reversal 域提供，后端为 `ReversalAdminController`、`ReversalAdminService`，前端为 `returnRefundReversalApi` 和 `PurchaseReturn*Views`，024 只复用并闭合其所有权、项目、原成本层和值流水反冲契约。

### 当前缺口

- 项目专采没有可信业务入口，无法从项目需求、审批和选价链路直接建立项目成本层。
- 请购、询价比价、供应商报价和价格协议缺失，采购订单价格来源不可审计。
- 公共采购与项目专采没有明确互斥的订单所有权约束，后续 026 无法可靠读取采购供给。
- 采购订单确认缺少对已批准请购、已批准选价、有效协议和例外审批的统一校验。
- 到货计划只停留在订单或行的预计日期，不能表达多批预计到货。
- reversal 域采购退货尚未按 023 原所有权、原项目、原成本层和原价值流水完整反冲。
- 供应商报价导入、采购单据导出和采购订单固定打印尚未接入 022 文档任务样板。

## 2026-07-15 决策记录

- 024 与 025 分别独立交付；024 只补齐项目采购来源，不提前实现销售深化或缺料供给建议。
- 项目专采必须来自已批准请购；公共采购允许具备专门权限的用户直接创建采购订单，但必须填写原因并通过固定例外审批。
- 所有正式请购必须走固定审批；价格协议激活必须走固定审批。
- 采购订单严格承接已批准请购和已批准选价或有效协议时，确认不重复审批。
- 非最低有效报价选择、偏离有效协议价或公共直采必须走采购订单固定例外审批。
- 一个采购订单只能是公共采购，或同一个项目的项目专采，不允许混合公共与项目、不允许混合多个项目。
- 项目专采只允许引用 `ACTIVE` 项目；采购订单确认后，项目与所有权不可直接改写。
- 首版仅支持人民币 `CNY`；报价、价格协议和采购订单保存税率、含税/未税单价与金额业务快照；不建设多币种。
- 有效价格协议优先；非最低有效报价或偏离有效协议价必须记录原因并审批。
- 到货计划按订单行支持多批预计到货；允许部分到货；超收容差固定为 `0`，必须先受控变更采购订单才可增加。
- 采购入库必须调用 023 统一过账与估值服务：项目专采进入项目真实来源成本层，公共采购进入公共移动加权平均池。
- 采购退货必须复用 reversal 域既有入口，引用原入库行并反冲原所有权、原项目、原成本层和值流水；退货不自动重开采购订单，后续补购走受控变更或新订单。
- 未收数量可带原因结案；结案后不再计入有效供给。已确认和已过账事实不可直接改写。
- 项目专采余料转公共或跨项目转移不得在 024 新造旁路，只能调用或跳转 023 受控所有权转换。
- 026 只读消费 024 的有效采购供给视图；024 不计算缺料、不自动生成采购建议或生产建议。
- 024 复用 022 固定审批、附件、消息、审计和受控文档任务；固定采购订单打印必须扩展 022 当前偏审批实例的打印契约，支持采购订单业务对象并重新鉴权；供应商报价支持单个询价范围内受控导入，列表支持当前筛选导出。

## 阶段目标

在 020 项目主维度、021 供应商结算税务资料、022 固定审批与文档任务、023 库存数量价值双台账之上，建立请购、询价比价、价格协议、项目专采与公共采购、到货计划、采购入库估值和采购结案的可信来源链，使项目库存能够从真实采购来源直接建立项目成本层，公共采购继续进入公共移动加权平均池，并为 026 提供只读有效采购供给视图。

024 完成后必须能够回答：

- 某个项目的物料采购需求来自哪张已批准请购、哪个项目、哪个合同或业务说明。
- 采购价格来自有效协议、最低有效报价、非最低选价还是例外审批。
- 某张采购订单是公共采购还是项目专采，为什么允许确认，是否存在例外审批。
- 某批入库进入了项目真实来源成本层还是公共移动平均池，来源采购单据、行、价格和税率快照是什么。
- reversal 域采购退货如何引用原入库行并反冲原所有权、原项目和原成本来源。
- 未收数量为什么结案，结案后是否还进入后续有效供给。
- 026 能从哪些只读字段读取有效采购供给，且不会在 024 内获得缺料计算或自动建议。

## 范围

### 本次包含

- 采购请购：请购头行、项目专采归属、公共采购归属、正式请购固定审批、撤回、驳回后修订、取消、转订单、结案和审计。
- 询价比价：询价单、供应商报价、报价有效期、含税/未税口径、税率、报价比较、最低有效报价识别、非最低选价原因和审批条件。
- 价格协议：供应商、物料、价格、税率、有效期、最小采购量、项目适用范围、激活固定审批、停用和优先级。
- 采购订单深化：采购模式、所有权、项目、请购来源、选价来源、协议来源、例外审批、确认后锁定项目与所有权、受控变更和结案原因。
- 到货计划：按采购订单行维护多批预计到货日期与数量，支持部分到货、计划完成和未收原因。
- 采购入库估值：采购入库按订单所有权调用 023 统一过账与估值服务，项目专采建立项目成本层，公共采购进入公共池。
- 采购退货闭合：不在 procurement 域重建退货；复用 reversal 域采购退货入口，扩展原所有权、原项目、原成本层和原价值流水反冲契约；不自动重开订单。
- 有效采购供给视图：输出 026 只读消费的项目、物料、所有权、预计到货、剩余数量、状态和来源字段。
- 供应商报价受控导入：仅支持单个询价范围内导入报价，不做全系统采购导入。
- 当前筛选导出：请购、询价、报价、价格协议、采购订单、到货计划和有效供给列表按权限导出。
- 固定采购订单打印：使用 022 文档任务和固定模板生成采购订单预览与 PDF 任务。
- 权限、菜单、路由、稳定错误码、审计、附件证据、消息、历史兼容、迁移和桌面浏览器验收。

### 本次不包含

- 025 的销售报价、合同变更深化、销售信用控制、项目交付、销售结案和项目收入履约。
- 026 的缺料净算、供给建议、自动采购建议、自动生产建议、计划订单、MPS、MRP 和 APS。
- 027 的项目生产工单、外协、工序、工作中心、派工和生产执行深化。
- 028 至 032 的采购发票、销售发票、费用单、三单匹配、正式应付、凭证、月结、总账和财务结账。
- 029 的项目成本核算、在制、完工成本、制造费用分摊和项目毛利。
- 供应商门户、电子招投标、供应商评分、复杂供应链协同、多组织采购、通用审批规则引擎、采购 BI、价格预测和自动议价。
- 多币种、汇率、税务申报、红字发票、电子发票和银行支付。
- 新造库存所有权转换旁路、直接写库存余额、直接写公共池金额、直接写项目成本层或直接写价值流水。
- 移动端、窄屏和响应式兼容测试或验收；设计开发仍需保留结构能力。

## 角色分工

- 产品经理：维护 024 业务目标、范围边界、状态机、业务规则、验收矩阵、跨阶段契约和变更控制；不承担 UI、前端、后端或测试执行。
- UI 设计师：定义请购、询价、报价比较、价格协议、采购订单、到货计划、例外审批、采购入库、退货追溯、有效供给和固定打印的桌面信息结构、状态表达、权限态、空态、错误态和响应式开发约束。
- 后端开发：实现数据模型、迁移、状态机、审批适配器、采购服务、估值接入、供给视图、权限、错误码、审计、导入导出和后端定向测试。
- 前端开发：实现采购深化页面、接口类型、状态动作、权限态、错误态、导入导出任务、固定打印入口、桌面交互和前端定向测试。
- 测试：建立独立集成、迁移、权限、异常、并发、估值、审批、导入导出、历史兼容、浏览器功能与桌面视觉验收矩阵。
- 主代理：负责阶段统筹、工作包划分、并行协调、风险判断、审查意见去重、交付判断、Git 和向用户汇报；不承担业务实现。

固定角色使用当前交接登记的 Codex 后台线程；每个角色本阶段原则上只有一个主 `/goal`，审查、整改和复审通过原线程增量派发。

## 业务对象、状态机与规则

### 采购模式和所有权

- 采购模式固定为 `PUBLIC` 和 `PROJECT`。
- `PUBLIC` 表示公共采购，采购订单、入库和 reversal 采购退货扩展信息中的 `projectId` 必须为空。
- `PROJECT` 表示项目专采，采购订单、入库和 reversal 采购退货扩展信息中的 `projectId` 必须引用同一个 `ACTIVE` 销售项目。
- 一个采购订单只允许一种采购模式；项目专采订单只允许一个项目；订单确认后采购模式和项目不可通过普通编辑改变。
- 公共直采是例外路径，只允许具备 `procurement:order:public-direct` 的用户创建，并且确认前必须通过采购订单例外审批。
- 项目专采必须从已批准请购行创建或合并创建，不允许无请购直接创建项目采购订单。

### 请购状态

请购业务状态固定为：

| 状态 | 含义 | 允许操作 |
|---|---|---|
| `DRAFT` | 草稿请购，尚未形成正式采购需求 | 编辑、提交审批、取消 |
| `APPROVED` | 固定审批已通过，可转采购订单 | 创建询价、创建采购订单、结案 |
| `PARTIALLY_ORDERED` | 部分数量已转采购订单 | 继续转单、结案 |
| `ORDERED` | 全部有效数量已转采购订单 | 查询、结案 |
| `CLOSED` | 未转或未完全转单的数量带原因关闭 | 查询、追溯 |
| `CANCELLED` | 草稿或驳回后作废 | 查询、追溯 |

状态流转：

```text
DRAFT -> APPROVED -> PARTIALLY_ORDERED -> ORDERED -> CLOSED
DRAFT -> CANCELLED
APPROVED -> CLOSED
PARTIALLY_ORDERED -> CLOSED
```

审批状态由 022 审批实例独立表达。请购提交审批后锁定编辑；撤回或驳回后回到可修订草稿口径，重新提交生成新审批实例，不覆盖历史审批。

请购规则：

- 请购行必须明确采购模式、物料、业务单位、基本单位换算结果、需求数量、需求日期、用途说明和建议供应商；项目专采还必须引用 `ACTIVE` 项目。
- 正式请购必须通过固定审批场景 `PROCUREMENT_REQUISITION_APPROVAL`。
- 请购审批通过前不得转采购订单，不得进入有效采购供给视图。
- 请购行可拆分到多个采购订单行，但累计转单数量不得超过批准数量。
- 请购结案必须填写 1 至 200 字原因；结案后的未转数量不再允许转单。

### 询价、报价和选价

询价状态固定为：

| 状态 | 含义 | 允许操作 |
|---|---|---|
| `DRAFT` | 草稿询价 | 编辑、发布、取消 |
| `RELEASED` | 已发布，可录入或导入供应商报价 | 录入报价、导入报价、结束询价、取消 |
| `COMPLETED` | 报价收集结束，可形成选价结果 | 选价、查询 |
| `AWARDED` | 已形成采购订单或价格协议来源 | 查询、追溯 |
| `CANCELLED` | 询价作废 | 查询、追溯 |

供应商报价状态固定为 `DRAFT`、`VALID`、`SELECTED`、`REJECTED`、`EXPIRED`、`CANCELLED`。

询价与报价规则：

- 一个询价必须限定采购模式；项目询价必须限定同一个 `ACTIVE` 项目。
- 询价行可来源于一个或多个已批准请购行，也可用于公共采购比价；公共采购若无请购来源，后续订单确认必须走例外审批。
- 报价必须保存供应商、物料、数量阶梯、最小采购量、税率、含税单价、不含税单价、含税金额、不含税金额、有效期、交期和币种快照。
- 首版币种固定为 `CNY`，接口和数据库仍保存币种字段以便历史快照一致，但任何非 `CNY` 请求必须拒绝。
- 同一询价范围内供应商报价支持受控 XLSX 导入，导入只创建或更新该询价下报价草稿，不跨询价、不批量确认采购订单。
- 选择非最低有效报价必须填写原因并触发采购订单例外审批；最低价比较按同一物料、同一数量口径、同一含税/未税比较口径和有效期判断。

### 价格协议

价格协议状态固定为：

| 状态 | 含义 | 允许操作 |
|---|---|---|
| `DRAFT` | 草稿协议 | 编辑、提交激活审批、取消 |
| `ACTIVE` | 已激活，可被采购订单引用 | 停用、查询 |
| `DISABLED` | 人工停用 | 查询 |
| `EXPIRED` | 有效期结束 | 查询 |
| `CANCELLED` | 草稿作废 | 查询 |

价格协议规则：

- 价格协议必须限定供应商、物料、币种、税率、含税/未税单价、最小采购量、有效期和适用范围。
- 适用范围可为公共采购或单个项目；项目协议只能用于同一项目专采。
- 价格协议激活必须通过固定审批场景 `PROCUREMENT_PRICE_AGREEMENT_ACTIVATION`。
- 同一供应商、物料、采购模式、项目、有效期重叠时，不得存在多个同时有效且优先级相同的协议。
- 采购订单确认时存在有效价格协议的，默认优先使用协议价；偏离协议价必须填写原因并触发采购订单例外审批。

### 采购订单深化

采购订单沿用既有状态 `DRAFT`、`CONFIRMED`、`PARTIALLY_RECEIVED`、`RECEIVED`、`CLOSED`、`CANCELLED`，并扩展采购模式、项目、请购来源、选价来源、协议来源、例外审批和到货计划。

采购订单规则：

- 草稿订单可编辑；确认后供应商、采购模式、项目、物料、单位、数量、价格来源、税率和价格快照不可直接改写。
- 需要增加数量、改变价格、改变到货计划或改变供应商时，必须通过受控变更记录表达；已有入库事实不被改写。
- 项目专采订单所有行必须来源于同一项目的已批准请购行；公共订单可来源于公共请购、询价或公共直采例外。
- 严格承接已批准请购和已批准选价或有效价格协议的订单，确认时不重复审批。
- 非最低有效报价、偏离有效协议价、公共直采、无批准选价来源或价格来源失效的订单，确认必须提交固定例外审批场景 `PROCUREMENT_ORDER_EXCEPTION_CONFIRM`。
- 订单行不得重复相同物料、相同来源请购行、相同价格来源和相同到货计划组合；需要分批到货通过到货计划表达。
- 超收容差为 `0`；采购入库数量不得超过订单行未入库数量，若需增加数量必须先完成受控订单变更。
- 未收数量允许带原因结案；结案后订单和行均不再进入有效采购供给视图。

### 到货计划

- 到货计划归属于采购订单行，包含计划序号、预计到货日期、计划数量、已入库数量、剩余数量、状态和备注。
- 到货计划状态固定为 `PLANNED`、`PARTIALLY_RECEIVED`、`RECEIVED`、`CLOSED`、`CANCELLED`。
- 一条订单行可有多条到货计划；计划数量合计必须等于订单行数量。
- 采购入库可引用到货计划；未引用时后端按最早未完成计划分配，但响应必须返回实际分配结果。
- 关闭到货计划必须填写原因；关闭后的剩余数量不进入有效采购供给视图。

### 采购入库和估值

- 采购入库沿用 `DRAFT`、`POSTED`；过账后不可编辑。
- 入库业务日期受业务期间保护，锁定期间禁止过账。
- 入库行必须引用采购订单行，并继承采购模式、项目、价格快照、税率和来源单据。
- 入库过账必须在同一事务内锁定采购入库、采购订单、订单行、到货计划、库存余额、公共计价池或项目成本层。
- 公共采购入库调用 023 统一过账服务，估值上下文为 `PUBLIC`，入库金额取订单行未税金额快照，进入公共移动加权平均池。
- 项目专采入库调用 023 统一过账服务，估值上下文为 `PROJECT + projectId`，按真实采购来源建立项目成本层，成本层来源为采购入库行。
- 参与库存价值的物料不得以零成本或空金额进入项目成本层；非计价物料继续只写数量，价值字段为空。
- 入库过账必须写数量流水和值流水；任一侧失败整体回滚。

### 采购退货和结案

- 采购退货不在 procurement 域新建第二套退货；必须复用 reversal 域的 `ReversalAdminController`、`ReversalAdminService`、`returnRefundReversalApi`、`PurchaseReturnListView.vue`、`PurchaseReturnFormView.vue` 和 `PurchaseReturnDetailView.vue`。
- 采购退货必须引用原采购入库行，不允许无来源退货；来源行必须带出原采购模式、原项目、原成本层、原值流水、原单价金额快照和可退数量。
- 退货数量不得超过原入库行可退数量，且必须反冲原所有权、原项目、原成本层和原价值流水。
- 公共采购退货从公共池按原入库价值反向处理；项目专采退货从原项目成本层扣减或建立可追溯反向流水，不使用当前公共平均价替代。
- 退货不自动重开采购订单、不自动恢复到货计划；需要补购时走受控订单变更或新采购订单。
- 采购订单结案只关闭未收数量，不改写已确认和已过账事实，不删除来源链。

### 有效采购供给视图

024 必须提供 026 只读消费的有效采购供给视图。该视图只表达采购侧已经形成的有效供给，不计算缺料，不生成建议。

供给视图至少包含：

- `supplyType`：`PURCHASE_ORDER` 或 `PURCHASE_SCHEDULE`。
- 采购模式、项目、供应商、物料、基本单位、预计到货日期、剩余数量、已入库数量、关闭数量、状态。
- 来源采购订单、采购订单行、到货计划、请购行、报价或协议来源。
- 价格来源类型、未税单价、含税单价、税率、币种、成本可见标识。
- 是否计入 026 有效供给；只有 `CONFIRMED` 或 `PARTIALLY_RECEIVED` 且未关闭的订单行或到货计划可计入。

已批准但未转订单的请购行可在请购列表中查询，不作为有效供给计入 026。

## 权限、审批、附件、打印与审计

### 权限

新增权限必须进入权限种子、系统管理员授权、菜单/路由映射、`PermissionAuthorizationManager` 和权限测试。建议权限码如下：

- `procurement:requisition:view/create/update/submit/cancel/close`
- `procurement:requisition:approve`
- `procurement:inquiry:view/create/update/release/cancel/award`
- `procurement:quote:view/create/update/import/export/select`
- `procurement:price-agreement:view/create/update/submit/disable/cancel`
- `procurement:price-agreement:approve`
- `procurement:order:exception-submit`
- `procurement:order:exception-approve`
- `procurement:order:public-direct`
- `procurement:order:change`
- `procurement:order:print`
- `procurement:supply:view/export`

既有 `procurement:order:*` 和 `procurement:receipt:*` 权限继续保留并按新业务规则扩展。拥有文档任务下载权限不能替代具体采购业务查看或导出权限。

### 固定审批场景

024 新增固定审批场景：

| 场景代码 | 业务对象 | 触发动作 | 最终通过动作 |
|---|---|---|---|
| `PROCUREMENT_REQUISITION_APPROVAL` | 采购请购 | 请购提交 | 将请购置为 `APPROVED` |
| `PROCUREMENT_PRICE_AGREEMENT_ACTIVATION` | 价格协议 | 协议激活提交 | 将协议置为 `ACTIVE` |
| `PROCUREMENT_ORDER_EXCEPTION_CONFIRM` | 采购订单 | 例外确认提交 | 将订单置为 `CONFIRMED` |

审批规则：

- 提交审批前必须重新校验业务对象状态、版本、权限、项目、供应商、物料、价格和来源。
- 审批最终通过必须锁定审批实例、任务和业务对象，在同一事务内完成业务动作、审批终态、消息和审计。
- 自批限制、版本冲突、对象权限、撤回、驳回和平台取消沿用 022 规则。
- 历史采购订单、历史入库和历史退货不补造审批实例。

### 附件、打印和文档任务

- 采购请购、询价、供应商报价、价格协议、采购订单和例外审批均可复用 022 私有附件能力，附件继承业务对象权限。
- 固定采购订单打印模板代码为 `PROCUREMENT_ORDER_V1`，模板只打印已确认及以后状态的采购订单，不提供模板设计器。
- 采购订单打印必须扩展 022 当前偏 `APPROVAL_INSTANCE` 的打印契约，使 `platform_print_template.object_type` 支持采购订单业务对象；预览、创建任务、生成和下载均必须重新校验 `procurement:order:view`、`procurement:order:print` 和平台文档任务权限。
- 采购订单打印通过 `document_task` 生成 PDF，任务类型为 `PROCUREMENT_ORDER_PRINT`，结果下载重新鉴权并保留模板代码、模板版本、订单版本和生成人。
- 供应商报价导入任务类型为 `PROCUREMENT_QUOTE_IMPORT`，只允许在单个询价下导入报价。
- 当前筛选导出任务类型至少包含 `PROCUREMENT_REQUISITION_EXPORT`、`PROCUREMENT_INQUIRY_EXPORT`、`PROCUREMENT_QUOTE_EXPORT`、`PROCUREMENT_PRICE_AGREEMENT_EXPORT`、`PROCUREMENT_ORDER_EXPORT`、`PROCUREMENT_SCHEDULE_EXPORT`、`PROCUREMENT_SUPPLY_EXPORT`。
- 导入导出和打印任务沿用 022 的幂等键、租约、重试、过期、失败明细、结果下载和消息规则。

### 审计

必须审计：

- 请购创建、修改、提交、撤回、审批通过/驳回、取消、转单、结案。
- 询价创建、发布、报价录入、报价导入、结束询价、选价、取消。
- 价格协议创建、修改、提交、审批通过/驳回、激活、停用、取消。
- 采购订单创建、修改、例外提交、例外审批、确认、变更、取消、关闭、打印、导出。
- 到货计划创建、修改、入库分配、关闭和取消。
- 采购入库创建、修改、过账、估值失败、重复过账拒绝。
- reversal 域采购退货创建、修改、过账、取消、原所有权、原项目、原成本层和原价值流水反冲。
- 权限拒绝、附件上传/删除/下载、导入上传/确认/失败、导出创建/下载。

审计摘要不得泄露未授权成本金额、对象存储地址、敏感银行资料或文件正文。

## API 契约与错误语义

返回继续使用现有 JSON envelope、分页结构、CSRF 和稳定错误格式；十进制数量、单价、税率和金额在前端请求和响应中使用字符串，最终计算以后端为准。前端不得使用 `Number`、浮点加减乘除或隐式数值转换作为业务计算口径；展示格式化只能派生显示值，不得改变请求、响应和表单源十进制字符串。

### 建议接口分组

| 资源 | 路径 | 能力 |
|---|---|---|
| 采购请购 | `/api/admin/procurement/requisitions` | list/get/create/update/submit/withdraw/cancel/close |
| 询价单 | `/api/admin/procurement/inquiries` | list/get/create/update/release/complete/cancel/award |
| 供应商报价 | `/api/admin/procurement/inquiries/{id}/quotes` | list/create/update/import/select |
| 价格协议 | `/api/admin/procurement/price-agreements` | list/get/create/update/submit/disable/cancel |
| 采购订单 | `/api/admin/procurement/orders` | 扩展既有 list/get/create/update/confirm/cancel/close/change/print |
| 到货计划 | `/api/admin/procurement/orders/{id}/schedules` | list/update/close |
| 采购入库 | `/api/admin/procurement/receipts` | 扩展既有 list/get/create/update/post |
| 采购退货（reversal 域） | `/api/admin/procurement/returns`、`/api/admin/procurement/return-sources` | 复用 `ReversalAdminController`、`ReversalAdminService` 和既有 list/get/create/update/post/cancel/source list，扩展原所有权、项目、成本层和值流水反冲 |
| 采购供给 | `/api/admin/procurement/effective-supplies` | list/export |
| 采购文档任务 | `/api/admin/print-tasks`、`/api/admin/export-tasks`、`/api/admin/procurement/inquiries/{id}/quote-imports`、`/api/admin/document-tasks` | 复用 022 的打印、导出、报价导入以及任务查询、失败明细、下载和取消入口 |

### 关键响应字段

- 请购、询价、价格协议、采购订单、采购入库、reversal 采购退货和供给视图均必须返回或等价提供 `allowedActions`，前端复杂动作只按后端动作集展示按钮，不复制复杂业务规则。
- 项目专采对象必须返回 `projectId`、`projectCode`、`projectName`、`ownershipType='PROJECT'`；公共采购必须返回 `ownershipType='PUBLIC'` 且项目字段为空。
- 价格相关响应必须区分 `taxIncludedUnitPrice`、`taxExcludedUnitPrice`、`taxRate`、`taxIncludedAmount`、`taxExcludedAmount`、`currency='CNY'`。
- 成本敏感字段继续受 `inventory:valuation:view` 控制；无权限不得返回项目成本层金额、单位成本或价值流水金额。
- 采购供给视图必须返回 `countedAsEffectiveSupply` 和不计入原因。

### 稳定错误码

024 新增或扩展的稳定错误码至少覆盖：

- `PROCUREMENT_REQUISITION_NOT_FOUND`
- `PROCUREMENT_REQUISITION_STATUS_INVALID`
- `PROCUREMENT_REQUISITION_APPROVAL_REQUIRED`
- `PROCUREMENT_REQUISITION_PROJECT_REQUIRED`
- `PROCUREMENT_REQUISITION_PROJECT_INVALID`
- `PROCUREMENT_INQUIRY_NOT_FOUND`
- `PROCUREMENT_INQUIRY_STATUS_INVALID`
- `PROCUREMENT_QUOTE_NOT_FOUND`
- `PROCUREMENT_QUOTE_INVALID`
- `PROCUREMENT_QUOTE_IMPORT_SCOPE_INVALID`
- `PROCUREMENT_PRICE_AGREEMENT_NOT_FOUND`
- `PROCUREMENT_PRICE_AGREEMENT_STATUS_INVALID`
- `PROCUREMENT_PRICE_AGREEMENT_OVERLAP`
- `PROCUREMENT_PRICE_AGREEMENT_APPROVAL_REQUIRED`
- `PROCUREMENT_ORDER_OWNERSHIP_MIXED`
- `PROCUREMENT_ORDER_PROJECT_MISMATCH`
- `PROCUREMENT_ORDER_PROJECT_IMMUTABLE`
- `PROCUREMENT_ORDER_REQUISITION_REQUIRED`
- `PROCUREMENT_ORDER_PRICE_SOURCE_INVALID`
- `PROCUREMENT_ORDER_LOWEST_PRICE_REQUIRED`
- `PROCUREMENT_ORDER_AGREEMENT_DEVIATION`
- `PROCUREMENT_ORDER_PUBLIC_DIRECT_APPROVAL_REQUIRED`
- `PROCUREMENT_ORDER_EXCEPTION_APPROVAL_REQUIRED`
- `PROCUREMENT_ORDER_CHANGE_REQUIRED`
- `PROCUREMENT_SCHEDULE_QUANTITY_MISMATCH`
- `PROCUREMENT_RECEIPT_EXCEEDS_SCHEDULE`
- `PROCUREMENT_RECEIPT_VALUATION_FAILED`
- `PROCUREMENT_RETURN_SOURCE_REQUIRED`
- `PROCUREMENT_RETURN_SOURCE_EXCEEDED`
- `PROCUREMENT_EFFECTIVE_SUPPLY_NOT_FOUND`
- `PROCUREMENT_CURRENCY_UNSUPPORTED`
- `PROCUREMENT_TAX_RATE_INVALID`

错误语义使用既有 400、403、404、409 分类，不向客户端暴露 SQL、对象存储、审批内部锁或数据库约束细节。

## 数据模型、迁移与历史兼容

### 迁移原则

- 新增迁移从当前最新 V25 之后开始，建议为 `V26__procurement_project_sourcing_schema.sql`；不得修改已执行的 V1 至 V25。
- 迁移必须支持空库、V25 存量库和当前共享验收库升级。
- 现有 `proc_purchase_order`、`proc_purchase_order_line`、`proc_purchase_receipt` 和 `proc_purchase_receipt_line` 可增量扩展字段、索引和约束，不删除历史字段，不改写历史订单业务事实。
- V26 必须替换 V8 中 `proc_purchase_order_line` 的 `(order_id, material_id)` 唯一约束 `uk_proc_purchase_order_line_material`；该约束会阻断同一订单内同物料来自多请购行、多价格来源或多计划结构的合法场景。替代口径为保留 `(order_id, line_no)` 行号唯一，新增来源组合约束或索引约束同一订单内不可重复相同请购行与价格来源组合，新增到货计划 `(order_line_id, schedule_no)` 唯一；不得再按物料唯一阻断同物料多行。
- V26 替换约束必须保留历史数据：先增加新来源字段和计划表，再迁移历史订单为无请购、无价格来源的公共采购行，最后删除旧物料唯一约束；历史行号、物料、数量、价格和入库引用不得改写。
- 000 至 023 历史采购订单默认迁移为 `PUBLIC` 公共采购，`projectId` 为空；历史采购入库保持既有来源和估值结果，不反推项目专采。
- 历史价格、税率和含税/未税金额缺失时，只为新业务保存完整快照；不得伪造历史报价或协议。

### 主要模型

新增或扩展模型至少覆盖：

- `proc_purchase_requisition`、`proc_purchase_requisition_line`
- `proc_purchase_inquiry`、`proc_purchase_inquiry_line`
- `proc_supplier_quote`、`proc_supplier_quote_line`
- `proc_price_agreement`、`proc_price_agreement_line`
- `proc_purchase_order_schedule`
- `proc_purchase_order_change`
- `proc_purchase_price_selection`
- `proc_effective_purchase_supply` 只读视图或等价查询模型

扩展既有采购订单和入库：

- 采购订单头增加采购模式、项目、币种、价格来源摘要、例外审批状态、直采原因、结案原因和版本。
- 采购订单行增加请购行、询价行、报价行、协议行、价格选择、来源组合键、税率、含税/未税单价、含税/未税金额和价格来源类型。
- 采购入库头行继承采购模式、项目、税率与价格快照，并保存过账后的价值流水或成本层引用摘要。
- reversal 域采购退货模型扩展或关联保存原入库行、原价值流水、原成本层、原所有权和原项目引用。

### 兼容规则

- 历史采购订单和入库继续可查询、可退货、可追溯；没有请购、询价、协议来源时页面显示“历史采购来源”。
- 历史公共采购订单允许按既有规则关闭和追溯，不要求补审批。
- 新建项目专采必须满足 024 新规则，不允许借历史兼容口径绕过请购审批。
- 采购入库过账继续受业务期间保护；锁定期间禁止新增入库、退货和会影响库存价值的自动过账。
- 演示数据和操作手册只能在功能实现并验证后更新，不得提前把 024 写成当前已交付能力。

## 文件责任与实现映射

### 后端主要文件

- 迁移：`apps/api/src/main/resources/db/migration/V26__procurement_project_sourcing_schema.sql`，包含 024 新表、历史兼容、打印模板、权限种子、审批场景、采购订单行唯一约束替换和 reversal 退货扩展字段或关联表。
- 采购 controller/service 拆分：在 `apps/api/src/main/java/com/qherp/api/system/procurement/` 下保留既有 `ProcurementAdminController.java`、`ProcurementAdminService.java` 的兼容入口，并按资源拆出 `ProcurementRequisitionAdminController.java`、`ProcurementRequisitionAdminService.java`、`ProcurementInquiryAdminController.java`、`ProcurementInquiryAdminService.java`、`ProcurementPriceAgreementAdminController.java`、`ProcurementPriceAgreementAdminService.java`、`ProcurementOrderAdminController.java`、`ProcurementOrderAdminService.java`、`ProcurementReceiptAdminController.java`、`ProcurementReceiptAdminService.java`、`ProcurementSupplyAdminController.java` 和 `ProcurementSupplyAdminService.java`。
- reversal 退货复用：`apps/api/src/main/java/com/qherp/api/system/reversal/ReversalAdminController.java`、`apps/api/src/main/java/com/qherp/api/system/reversal/ReversalAdminService.java` 负责采购退货入口、来源查询、过账、取消和追溯扩展，不在 procurement 包内新建第二套采购退货 controller/service。
- 023 估值接入：`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryPostingService.java`、`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryValuationService.java` 负责公共移动平均池、项目真实来源成本层、数量流水和值流水。
- 022 平台复用：`apps/api/src/main/java/com/qherp/api/system/platform/PlatformApprovalService.java`、`apps/api/src/main/java/com/qherp/api/system/platform/PlatformDocumentTaskService.java`、`apps/api/src/main/java/com/qherp/api/system/platform/PlatformDocumentTaskWorker.java`、`apps/api/src/main/java/com/qherp/api/system/platform/PlatformAttachmentService.java` 负责固定审批、文档任务、固定打印、附件和任务结果。
- 权限：`apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`、`apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java` 负责权限种子、系统管理员授权和接口鉴权。
- 后端定向测试：`apps/api/src/test/java/com/qherp/api/system/stage024/Stage024ProcurementProjectSourcingTests.java`、`apps/api/src/test/java/com/qherp/api/system/stage024/Stage024MigrationRegressionTests.java`、`apps/api/src/test/java/com/qherp/api/system/procurement/ProcurementAdminControllerTests.java`、`apps/api/src/test/java/com/qherp/api/system/reversal/ReversalAdminControllerTests.java`、`apps/api/src/test/java/com/qherp/api/system/stage023/Stage023InventoryValuationIntegrationTests.java`、`apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`、`apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java` 和 022 文档任务相关 stage022 测试覆盖采购、退货、估值、权限和迁移。

### 前端主要文件

- API：扩展 `apps/web/src/shared/api/procurementApi.ts`、`apps/web/src/shared/api/procurementApi.spec.ts`；采购退货继续扩展 `apps/web/src/shared/api/returnRefundReversalApi.ts`、`apps/web/src/shared/api/returnRefundReversalApi.spec.ts`。
- 页面 helper：扩展 `apps/web/src/modules/procurement/procurementPageHelpers.ts`，统一状态、错误、十进制字符串展示和 `allowedActions` 映射。
- 既有采购页面：扩展 `apps/web/src/modules/procurement/PurchaseOrderListView.vue`、`PurchaseOrderFormView.vue`、`PurchaseOrderDetailView.vue`、`PurchaseOrderLineEditor.vue`、`PurchaseReceiptListView.vue`、`PurchaseReceiptFormView.vue`、`PurchaseReceiptDetailView.vue`、`PurchaseReceiptLineEditor.vue` 及同目录对应 spec。
- reversal 采购退货页面：扩展 `apps/web/src/modules/reversal/PurchaseReturnListView.vue`、`PurchaseReturnFormView.vue`、`PurchaseReturnDetailView.vue`、`PurchaseReturnViews.spec.ts`，只闭合来源、成本和动作契约，不迁移到 procurement 页面目录。
- 新增采购深化页面：在 `apps/web/src/modules/procurement/` 下新增 `PurchaseRequisitionListView.vue`、`PurchaseRequisitionFormView.vue`、`PurchaseRequisitionDetailView.vue`、`PurchaseInquiryListView.vue`、`PurchaseInquiryFormView.vue`、`PurchaseInquiryDetailView.vue`、`SupplierQuoteCompareView.vue`、`PriceAgreementListView.vue`、`PriceAgreementFormView.vue`、`PriceAgreementDetailView.vue`、`PurchaseScheduleView.vue`、`EffectivePurchaseSupplyView.vue` 及对应 `*.spec.ts`。
- 路由与导航：扩展 `apps/web/src/router/index.ts`、`apps/web/src/router/permissionGuard.spec.ts`、`apps/web/src/App.vue`、`apps/web/src/App.spec.ts`，采购根路由按用户拥有的首个采购子资源权限落地。

## 并发、幂等和事务边界

- 所有写动作必须携带对象版本；版本不匹配返回稳定 409。
- 文档任务创建和导入确认必须使用 `Idempotency-Key`，同一用户、任务类型和 key 重试返回既有任务；同 key 不同文件或请求返回冲突。
- 请购审批通过、协议激活、订单例外确认、采购入库过账和采购退货过账必须在单个事务内完成业务状态、审批状态、消息、审计和库存估值。
- 订单确认时必须按稳定顺序锁定订单、订单行、请购行、报价或协议、到货计划，避免多订单并发消耗同一请购余量。
- 入库过账必须按 023 锁顺序进入库存余额、公共池和项目成本层，不得自建不同锁顺序。
- 重复提交确认、入库过账、退货过账、审批通过或导入确认不得产生重复采购来源、重复库存流水、重复价值流水或重复成本层。
- 客户端不得自动重放有业务副作用的 409 冲突请求，只能重新加载最新对象后由用户确认。

## 前端信息架构与桌面交互

### 页面与入口

- 采购菜单下新增或深化：采购请购、询价比价、供应商报价、价格协议、采购订单、采购入库、采购退货、有效采购供给和文档任务入口。
- 采购根路由当前固定依赖 `procurement:order:view` 的口径必须移除；`/procurement` 进入时按用户拥有的首个采购子资源查看权限依次落地到请购、询价、报价、价格协议、采购订单、采购入库、reversal 采购退货或有效供给页面，若无任何采购子资源权限则进入 403。
- 请购列表首屏展示请购编号、采购模式、项目、物料摘要、需求日期、状态、审批状态、转单进度和结案状态。
- 询价页面展示询价范围、供应商报价横向比较、最低有效报价、有效期、交期和选择原因。
- 价格协议页面展示供应商、物料、适用范围、税率、价格、有效期、状态、审批状态和引用次数。
- 采购订单页面必须突出采购模式、项目、请购来源、价格来源、例外审批、到货计划、入库进度和结案原因。
- 有效供给页面为只读列表，支持按项目、物料、供应商、预计到货日期、采购模式和状态筛选。
- 固定采购订单打印和供应商报价导入导出入口使用 022 文档任务中心状态与下载体验。

### 交互规则

- 公共采购和项目专采必须使用中文标签和醒目状态，不只依赖颜色。
- 项目专采选择项目后，请购、询价、订单、到货计划和入库表单必须保持同一项目，不允许行级混用。
- 价格字段必须明确含税/未税，不得只显示“单价”造成误解。
- 数量、单价、税率和金额字段在 `procurementApi`、`returnRefundReversalApi`、表单草稿、校验和提交 payload 中必须保持十进制字符串；前端不得用 `Number` 作为业务计算口径，页面格式化只生成显示文本。
- 例外审批原因、非最低价原因、偏离协议价原因、公共直采原因和结案原因必须在页面可见并可追溯。
- 所有复杂动作按钮依据后端 `allowedActions` 展示；审批中对象锁定编辑。
- 桌面端首屏优先保证项目、物料、供应商、状态、价格来源、到货日期、剩余数量和关键动作可扫描；明细、审批、附件和审计进入抽屉或详情区。
- 错误提示必须落在表单或动作区域内，不能只依赖临时消息。

## 整包工作计划

### 工作包一：请购、询价比价、价格协议和固定审批

- 主要所有者：后端开发、前端开发；测试角色建立独立审批和异常覆盖；UI 设计师冻结桌面信息结构。
- 完成请购模型、状态机、固定审批、询价、供应商报价、受控报价导入、价格协议和协议激活审批。
- 完成最低有效报价、协议优先、非最低选价和偏离协议价规则。
- 主要文件：`apps/api/src/main/resources/db/migration/V26__procurement_project_sourcing_schema.sql`，`apps/api/src/main/java/com/qherp/api/system/procurement/` 下的 `ProcurementRequisitionAdminController.java`、`ProcurementRequisitionAdminService.java`、`ProcurementInquiryAdminController.java`、`ProcurementInquiryAdminService.java`、`ProcurementPriceAgreementAdminController.java`、`ProcurementPriceAgreementAdminService.java`，`apps/api/src/main/java/com/qherp/api/system/platform/PlatformApprovalService.java`、`PlatformAttachmentService.java`，`apps/web/src/shared/api/procurementApi.ts`，`apps/web/src/modules/procurement/` 下的 `PurchaseRequisition*View.vue`、`PurchaseInquiry*View.vue`、`SupplierQuoteCompareView.vue`、`PriceAgreement*View.vue` 和对应 spec。
- 定向验证覆盖请购审批、驳回重提、项目专采项目校验、报价导入、协议重叠、协议激活审批和权限拒绝。
- 可直接执行命令：

```powershell
Push-Location apps/api
.\mvnw.cmd -Dtest=Stage024ProcurementProjectSourcingTests,ProcurementAdminControllerTests,PermissionAuthorizationTests,AccountPermissionInitializerTests test
Pop-Location
Push-Location apps/web
npm run test -- procurementApi.spec.ts PurchaseRequisition PurchaseInquiry SupplierQuote PriceAgreement permissionGuard.spec.ts App.spec.ts
npm run typecheck
Pop-Location
```

### 工作包二：采购订单深化、到货计划、入库估值和采购退货

- 主要所有者：后端开发；前端开发并行消费稳定接口；测试角色覆盖跨模块事务。
- 扩展采购订单采购模式、项目、请购来源、选价来源、协议来源、例外审批和到货计划。
- 改造采购入库过账，按公共或项目调用 023 统一过账与估值服务。
- 复用并闭合 reversal 域采购退货，引用原入库行并反冲原所有权、原项目、原成本层和值流水来源。
- 替换 `proc_purchase_order_line` 的 `(order_id, material_id)` 唯一约束，支持同物料多请购来源、多价格来源和多计划结构。
- 主要文件：`apps/api/src/main/resources/db/migration/V26__procurement_project_sourcing_schema.sql`，`apps/api/src/main/java/com/qherp/api/system/procurement/` 下的 `ProcurementOrderAdminController.java`、`ProcurementOrderAdminService.java`、`ProcurementReceiptAdminController.java`、`ProcurementReceiptAdminService.java`、`ProcurementSupplyAdminController.java`、`ProcurementSupplyAdminService.java`，`apps/api/src/main/java/com/qherp/api/system/reversal/ReversalAdminController.java`、`ReversalAdminService.java`，`apps/api/src/main/java/com/qherp/api/system/inventory/InventoryPostingService.java`、`InventoryValuationService.java`，`apps/web/src/shared/api/procurementApi.ts`、`apps/web/src/shared/api/returnRefundReversalApi.ts`，`apps/web/src/modules/procurement/` 下的 `PurchaseOrder*View.vue`、`PurchaseReceipt*View.vue`、`PurchaseScheduleView.vue`、`EffectivePurchaseSupplyView.vue`，`apps/web/src/modules/reversal/PurchaseReturn*View.vue` 和对应 spec。
- 定向验证覆盖公共采购、项目专采、入库公共池、项目成本层、退货反冲、超收拒绝、订单变更、结案和并发。
- 可直接执行命令：

```powershell
Push-Location apps/api
.\mvnw.cmd -Dtest=Stage024ProcurementProjectSourcingTests,ProcurementAdminControllerTests,ReversalAdminControllerTests,Stage023InventoryValuationIntegrationTests test
Pop-Location
Push-Location apps/web
npm run test -- procurementApi.spec.ts returnRefundReversalApi.spec.ts PurchaseOrder PurchaseReceipt PurchaseReturn PurchaseSchedule EffectivePurchaseSupply
npm run typecheck
Pop-Location
```

### 工作包三：采购文档任务、固定打印、导出和桌面体验

- 主要所有者：前端开发、后端开发；UI 设计师检查真实桌面页面。
- 接入固定采购订单打印、当前筛选导出、报价导入任务、任务轮询、结果下载、失败明细和过期处理。
- 完成请购、询价、报价、协议、订单、到货计划、入库、退货和有效供给的桌面页面状态表达。
- 扩展 022 当前偏审批实例的打印契约，支持 `PROCUREMENT_ORDER_V1` 采购订单业务对象、预览、生成、下载和重新鉴权。
- 主要文件：`apps/api/src/main/java/com/qherp/api/system/platform/PlatformDocumentTaskService.java`、`PlatformDocumentTaskWorker.java`、`PlatformAttachmentService.java`，`apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`，`apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`，`apps/web/src/shared/api/documentPlatformApi.ts`，`apps/web/src/shared/composables/useDocumentTaskPolling.ts`，`apps/web/src/modules/procurement/procurementPageHelpers.ts`，`apps/web/src/App.vue`，`apps/web/src/router/index.ts`，`apps/web/src/modules/platform/documentTasks/DocumentTaskCenterView.vue` 和采购新增/既有页面。
- 定向验证覆盖文档任务幂等、导入失败明细、导出权限、打印重新鉴权、页面权限态和错误态。
- 可直接执行命令：

```powershell
Push-Location apps/api
.\mvnw.cmd -Dtest=Stage024ProcurementProjectSourcingTests,Stage022BackendControllerTests,Stage022SecurityRegressionTests,Stage022StorageFailureRegressionTests,PermissionAuthorizationTests test
Pop-Location
Push-Location apps/web
npm run test -- documentPlatformApi.spec.ts useDocumentTaskPolling.spec.ts procurementApi.spec.ts procurementPageHelpers PurchaseOrder PurchaseRequisition PurchaseInquiry PriceAgreement EffectivePurchaseSupply permissionGuard.spec.ts App.spec.ts
npm run typecheck
Pop-Location
```

### 工作包四：独立集成、迁移与回归保护

- 主要所有者：测试角色；实现角色配合修复。
- 覆盖 V1/V19/V20/V21/V22/V23/V24/V25 至 024 最新迁移、历史采购兼容、022 审批/附件/文档任务复用、023 估值接入和 026 只读供给视图。
- 开发期间只运行 stage024、采购、库存估值、审批、文档任务、权限和受影响前端测试；全量验证进入唯一交付窗口。
- 主要文件：`Stage024ProcurementProjectSourcingTests.java`、`Stage024MigrationRegressionTests.java`、`ProcurementAdminControllerTests.java`、`ReversalAdminControllerTests.java`、`Stage023InventoryValuationIntegrationTests.java`、`Stage022MigrationRegressionTests.java`、`PermissionAuthorizationTests.java`、`AccountPermissionInitializerTests.java`、`procurementApi.spec.ts`、`returnRefundReversalApi.spec.ts`、`PurchaseReturnViews.spec.ts`、`permissionGuard.spec.ts` 和本阶段新增页面 spec。
- 可直接执行命令：

```powershell
Push-Location apps/api
.\mvnw.cmd -Dtest=Stage024ProcurementProjectSourcingTests,Stage024MigrationRegressionTests,ProcurementAdminControllerTests,ReversalAdminControllerTests,Stage023InventoryValuationIntegrationTests,Stage022MigrationRegressionTests,PermissionAuthorizationTests,AccountPermissionInitializerTests test
Pop-Location
Push-Location apps/web
npm run test -- procurementApi.spec.ts returnRefundReversalApi.spec.ts PurchaseOrder PurchaseReceipt PurchaseReturn PurchaseRequisition PurchaseInquiry SupplierQuote PriceAgreement PurchaseSchedule EffectivePurchaseSupply permissionGuard.spec.ts App.spec.ts
npm run typecheck
Pop-Location
```

## 开发期定向验证

- 后端采购与退货定向命令：

```powershell
Push-Location apps/api
.\mvnw.cmd -Dtest=Stage024ProcurementProjectSourcingTests,ProcurementAdminControllerTests,ReversalAdminControllerTests test
Pop-Location
```

- 后端估值、审批、文档任务和权限定向命令：

```powershell
Push-Location apps/api
.\mvnw.cmd -Dtest=Stage023InventoryValuationIntegrationTests,Stage022BackendControllerTests,Stage022SecurityRegressionTests,Stage022StorageFailureRegressionTests,PermissionAuthorizationTests,AccountPermissionInitializerTests test
Pop-Location
```

- 后端迁移定向命令：

```powershell
Push-Location apps/api
.\mvnw.cmd -Dtest=Stage024MigrationRegressionTests test
Pop-Location
```

- 前端采购、reversal 退货、路由权限和文档任务定向命令：

```powershell
Push-Location apps/web
npm run test -- procurementApi.spec.ts returnRefundReversalApi.spec.ts documentPlatformApi.spec.ts useDocumentTaskPolling.spec.ts PurchaseOrder PurchaseReceipt PurchaseReturn PurchaseRequisition PurchaseInquiry SupplierQuote PriceAgreement PurchaseSchedule EffectivePurchaseSupply permissionGuard.spec.ts App.spec.ts
npm run typecheck
Pop-Location
```

- 数据库：V26 空库迁移和代表性 V25 存量升级定向验证；完整迁移组合留给交付前全量窗口。
- 浏览器：开发期仅由实现角色自查本阶段页面关键路径；正式桌面浏览器功能与视觉检验进入交付前窗口。

## 集中审查与整改

- 产品经理核对本文目标、采购模式、项目专采、价格来源、状态机、审批、供给视图和验收矩阵。
- UI 设计师基于真实桌面页面检查信息密度、状态识别、关键动作、原因字段、权限态、错误态和文案溢出，不截图。
- 前端开发审查非本人后端接口契约、错误语义、`allowedActions` 和前端可消费性。
- 后端开发审查非本人前端请求是否可能绕过后端规则、泄露成本或绕过审批。
- 测试核对请购、选价、协议、例外审批、估值、退货、并发、迁移、权限、导入导出和历史兼容覆盖。
- 主代理合并去重后一次性派发整改；阻断和严重问题必须修复，一般问题默认进入后续清单，不反复开启全阶段审查。

## 唯一交付前全量验证窗口

交付前必须统一执行：

- 后端全量测试。
- 前端全量 Vitest、类型检查和生产构建。
- 空库迁移、V1/V19/V20/V21/V22/V23/V24/V25 至最新迁移回归，以及共享验收库实际升级。
- PostgreSQL、MinIO、后端、前端真实运行健康检查。
- 后端全量测试前后核对共享验收 bucket 对象数与数据库 `AVAILABLE` 文件对象数一致，确认测试未污染共享 bucket。
- 采购请购、询价比价、价格协议、采购订单、到货计划、采购入库、采购退货、有效供给、审批待办、文档任务和固定打印的真实桌面浏览器功能与视觉检验。
- `git diff --check`、工作树范围和未提交文件检查。

全量窗口发现问题时，先定向定位和修复，再复验受影响范围，最后在同一窗口完成最终全量确认。

## 验收矩阵

### 主路径

- 管理员创建项目专采请购，关联 `ACTIVE` 项目，提交固定审批并由非提交人审批通过。
- 已批准项目请购发起询价，录入多个供应商报价，选择最低有效报价并生成项目专采采购订单。
- 已批准价格协议激活后，公共采购订单按协议价确认且不重复审批。
- 公共直采订单填写原因，提交采购订单例外审批，通过后确认。
- 项目专采订单确认后创建多批到货计划，部分入库后订单和计划状态推进。
- 项目专采入库过账后形成项目库存、项目成本层、数量流水和值流水。
- 公共采购入库过账后进入公共库存和公共移动平均池。
- 采购退货引用原项目专采入库行，反冲原项目和原成本层来源。
- 订单未收数量带原因结案，结案后不再进入有效采购供给视图。
- 有效采购供给页面能按项目、物料和预计到货日期查询 026 可消费供给。

### 异常路径

- 项目专采请购引用非 `ACTIVE` 项目被拒绝。
- 项目专采订单无已批准请购来源被拒绝。
- 同一订单混合公共和项目，或混合多个项目被拒绝。
- 订单确认后普通更新项目或采购模式被拒绝。
- 存在有效协议却偏离协议价，未提交例外审批时确认被拒绝。
- 非最低有效报价未填写原因或未通过例外审批时确认被拒绝。
- 入库数量超过订单行或到货计划剩余数量被拒绝。
- 锁定业务期间内确认、入库、退货或影响库存价值动作被拒绝。
- 采购退货无原入库行、超出可退数量或来源所有权不匹配被拒绝。
- 报价导入跨询价、文件超限、公式或隐藏业务列被拒绝且不产生部分写入。
- 无成本权限用户不能通过采购入库、退货或供给视图看到成本层金额和值流水金额。

### 数据和估值

- 项目专采 `100×10.00` 未税入库后，项目成本层原始数量 `100`、原始金额 `1000.00`、剩余数量和金额正确。
- 公共采购 `100×10.00` 后再入库 `50×13.00`，公共池数量、金额和平均价与 023 规则一致。
- 项目专采退货 `20` 必须从原成本层扣减 `200.00`，不得使用当前公共平均价。
- 公共采购退货按原入库价值反向处理，公共池数量和金额不得为负。
- 非计价物料采购入库只影响数量，价值字段为空且不写伪零成本。
- 有效供给数量等于已确认未关闭订单行或到货计划剩余数量，不包含草稿、审批中、已关闭、已取消或已全部入库数量。

### 权限和审批

- 无请购查看权限用户不能访问请购列表、详情或接口。
- 只读用户可查看授权范围内采购对象，但不能提交审批、确认、入库、退货、导入、导出或打印。
- 提交人不能审批自己提交的请购、价格协议或订单例外。
- 审批候选人失去业务对象查看权限后，待办和消息不得泄露对象摘要。
- 直接调用采购订单确认接口不能绕过例外审批。
- 文档任务下载必须同时具备平台下载权限和对应采购对象查看或导出权限。

### 桌面浏览器与视觉

- 只检验本阶段新增或修改页面：请购、询价、报价比较、价格协议、采购订单、到货计划、采购入库、采购退货、有效供给、审批待办、文档任务和采购订单打印。
- 检查桌面布局、信息密度、项目/公共标签、价格含税未税区分、审批状态、例外原因、关键动作可见性、文案溢出、控件重叠和错误反馈。
- 不测试移动端、窄屏或响应式兼容；不保存截图。

### 测试和部署

- 后端定向和全量测试通过，关键 stage024 集成测试覆盖主路径、异常、权限、并发、迁移、估值和审批。
- 前端受影响测试和全量 Vitest 通过，类型检查和生产构建通过。
- V26 及后续迁移从空库和代表性存量库升级通过。
- 主分支交付后，从主工作区启动 PostgreSQL、MinIO、后端和前端，浏览器可访问本阶段验收路径。

## 风险与处理

- 风险：024 范围滑向 025 销售履约或 026 缺料建议。处理：本文明确排除，供给视图只读且不计算缺料。
- 风险：项目专采绕过 023 估值服务直接写库存金额。处理：入库和退货必须通过统一过账与估值服务，直接写公共池或成本层列为阻断。
- 风险：价格协议、最低报价和例外审批规则互相冲突。处理：订单确认统一执行“有效协议优先、最低有效报价次之、例外审批兜底”的后端校验。
- 风险：公共直采变成常规绕过请购路径。处理：独立权限、必填原因、固定例外审批和审计共同约束。
- 风险：采购退货污染项目成本层或公共平均价。处理：复用 reversal 域采购退货入口，退货必须引用原入库行、原成本层和原价值来源，不能使用当前平均价替代。
- 风险：导入导出或打印污染共享对象存储。处理：沿用 022 文档任务失败关闭和对象一致性门禁，测试必须使用独立 MinIO Testcontainer。

## 变更控制

- 变更原因：用户要求在 024 实现前冻结唯一权威阶段说明，并明确 024 与 025 独立交付。
- 影响范围：采购请购、询价比价、价格协议、采购订单、采购入库、采购退货、库存估值、固定审批、文档任务、权限、审计、桌面页面和后续 026 只读输入。
- 涉及文件：本阶段新增或修改文件以实现阶段说明为准；本文是当前唯一已冻结文档成果。
- 验收影响：所有纳入范围均必须映射到本文验收矩阵，未通过验收矩阵和唯一交付前全量验证不得合入主分支。
- 决策依据：五角色唯一一轮 024/025 目标讨论、主代理自主决策、020 至 023 已交付契约、用户最新冻结决策。
- 2026-07-15 实现期契约澄清：接口分组表原写为不存在的 `/api/admin/platform/document-tasks`。为保持上游兼容，不新增平行平台入口；采购打印和任务查询、失败明细、下载、取消继续使用 022 已交付的 `/api/admin/print-tasks` 与 `/api/admin/document-tasks`，024 在同一 `PlatformDocumentTaskController`、`PlatformDocumentTaskService` 和任务表上扩展统一 `/api/admin/export-tasks` 及 `/api/admin/procurement/inquiries/{id}/quote-imports`。影响文件为本文、022 文档任务控制器/服务、权限映射及 024 前端调用方；不新建第二套任务状态机或对象存储目录，验收口径仍是完整复用 022 的幂等、租约、失败明细、重新鉴权和对象存储隔离。
- 2026-07-15 到货计划导出任务类型补正：阶段范围已明确包含到货计划当前筛选导出，但原任务类型清单遗漏对应类型。统一补充 `PROCUREMENT_SCHEDULE_EXPORT`，通过 `/api/admin/export-tasks` 创建，筛选快照携带采购订单上下文；不得复用 `PROCUREMENT_ORDER_EXPORT` 冒充计划导出，也不新增顶层到货计划导出 API。影响文件为本文、文档任务类型枚举、导出处理器和到货计划页面；无数据迁移兼容影响，验收新增任务类型、权限重校验和结果下载断言。

## 缺陷与阻断

### 阻断缺陷

- 系统无法启动、核心采购页面无法访问或主路径无法在桌面浏览器操作。
- 项目专采可绕过已批准请购创建或确认采购订单。
- 公共直采、非最低选价或偏离协议价可绕过固定例外审批确认订单。
- 一个采购订单混合公共和项目，或混合多个项目。
- 项目专采入库未建立项目真实来源成本层，或公共采购入库未进入公共平均池。
- 采购入库、退货、数量流水和值流水不在同一事务内一致成功或整体回滚。
- 采购退货未按原入库行、原所有权和原成本来源反冲。
- 锁定期间仍可确认、入库、退货或执行影响库存价值动作。
- 权限、审批待办、附件、导入导出、打印或供给视图泄露未授权对象、价格敏感字段或成本金额。
- 迁移失败、历史采购订单不可查询、历史采购入库或退货路径损坏。
- 024 页面或接口提前实现 025 销售深化、026 缺料净算或自动建议。

### 严重缺陷

- 价格含税/未税、税率或币种快照错误，可能污染后续发票或应付来源。
- 有效供给视图数量、预计到货日期或状态错误，影响 026 后续读取。
- 到货计划关闭、订单结案或采购变更原因不可追溯。
- 报价导入部分成功、失败明细不清晰或可绕过询价范围。
- 桌面页面关键信息密度不足，导致采购模式、项目、价格来源或审批状态容易误判。

### 既有风险边界

- 024 不交付采购发票、销售发票、正式应付、正式应收、付款、收款、凭证、月结或财务结算闭环；不得把 024 采购价格、入库事实或退货事实写成已完成正式应付、发票或财务结算闭环。
- 当前系统并非前后端开发、测试和验收全部容器化；024 交付记录必须按真实环境描述。

### 是否允许进入验收

存在任一阻断缺陷时不得进入主分支阶段验收。严重缺陷必须完成影响评估并修复或形成明确后续清单；影响当前验收矩阵的数据、权限、估值和审批问题不得带入验收。

## 执行记录

- 五角色目标讨论与范围冻结：已完成。2026-07-15 固定五角色围绕 024 与 025 连续阶段完成唯一一轮目标讨论；主代理确认 024 先独立交付项目采购来源，025 后续独立交付项目收入履约来源。
- 权威说明冻结：已完成。本文根据五角色讨论、主代理自主决策、用户冻结决策和一次交叉规格审核整改建立为 024 唯一权威阶段说明。
- 一次交叉规格审核：已完成。UI、前端、后端、测试四角色的规格审核事实已合并到本文，整改已完成。
- 整包开发与定向验证：已完成。请购、询价比价、供应商报价、价格协议、公共采购与项目专采、采购订单、到货计划、采购入库、采购退货、有效供给、固定审批和采购文档任务均已实现；实现期定向验证覆盖状态、权限、幂等、并发、来源、税价、估值、退货反冲、文档任务和历史兼容。
- 阶段集中审查：已完成。固定五角色完成唯一一轮集中审查，产品核对业务契约，UI 检查真实桌面页面，前后端交叉审查非本人实现，测试核对覆盖、异常路径和回归风险。
- 集中整改与差异复审：已完成。集中审查和后续真实运行验证发现的阻断、严重问题均由对应固定角色完成整改，并只复审修复差异和受影响路径；最终未遗留阻断或严重问题。
- 唯一交付前全量验证：已完成。JDK 21 后端全量 489/489 通过，0 失败、0 错误、0 跳过；其中 Stage024 主集 21/21、迁移集 2/2、采购后端流程自检 10/10。前端 98 个测试文件、806/806 通过，类型检查和生产构建通过；构建仅保留非阻断的 Vite chunk 大小提示。
- 数据与环境验证：已完成。V26 空库迁移、V25→V26 代表性存量升级和应用上下文验证通过；Testcontainers 临时资源已清理，未污染正式对象存储。
- 变更：024 实现严格保持阶段边界，没有启动 025/026，没有提前实现销售深化、缺料净算或后续财务能力，也没有改写 000 至 023 已交付事实。
- 桌面浏览器与视觉结论：已完成。真实桌面环境覆盖本阶段主路径、权限态、成本脱敏、导入导出、打印与任务、异常状态和关键数据识别；无成本权限的采购退货仍保留方向、仓库、物料、数量和已过账状态等普通追溯信息，同时隐藏内部标识、单价、金额和值流水。最终 1280×720 定向复验无溢出、重叠或控制台错误；未截图、未测试或验收移动端。
- 合入、推送和主分支部署：尚未执行；本记录收口后按项目规则提交功能分支、合入并推送 `main`，再从主分支工作区启动验收服务并把最终哈希和地址写入当前交接。
- 结论：024 纳入范围已经实现并通过集中审查、差异复审和唯一交付前全量验证，满足合入主分支和启动阶段验收的质量门禁。

## 完成定义

- 本文纳入范围全部实现，明确排除项未被提前实现。
- 请购、询价比价、价格协议、采购订单、到货计划、采购入库、采购退货和有效供给视图均有真实运行证据。
- 022 固定审批、附件、消息、打印和文档任务复用闭环通过。
- 023 公共移动平均和项目真实来源成本层接入通过，未出现绕过统一过账与估值服务的写入。
- 权限、审计、异常、并发、幂等、迁移、历史兼容和桌面浏览器验收矩阵通过。
- 固定五角色集中审查、集中整改和差异复审完成，未遗留阻断或严重问题。
- 唯一交付前全量验证窗口全部通过，相关页面完成桌面视觉检验且未保存截图。
- 更新当前交接和必要事实文档后，分支以可追溯方式合入并推送 `main`，从主分支工作区启动服务并向用户提供验收范围和地址。
