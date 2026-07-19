# 029 项目成本核算

> **固定角色执行要求：** 本文件是 029 唯一权威阶段说明，同时承载设计、实现计划、工作包、验收矩阵、审查记录和交付证据。执行时只复用已登记的产品经理、UI 设计师、前端开发、后端开发、测试五个固定长期会话，不创建额外角色。实现角色必须采用 `superpowers:test-driven-development`；集中审查、整改和交付验证按本文件与仓库规则执行。

**目标：** 建立以销售项目为一级成本对象的正式项目成本核算子账，可靠消费库存价值、生产、外协、费用和销售履约事实，形成材料、人工、外协、制造费用、项目费用、在制、完工、交付、调整、差异和项目经营毛利的可追溯结果。

**架构：** 029 前向新增独立 `projectcost` 领域和 V31 数据模型，保存核算运行、来源快照、成本分录、调整分配和差异，不改写 009、023、027、028 的业务事实。后端负责全部十进制计算、来源版本校验、权限脱敏、状态动作和审计；前端只展示后端结果、提交受控动作并提供来源追溯。

**技术栈：** Java 21、Spring Boot 4.0.7、JdbcTemplate、PostgreSQL、Flyway、Vue 3、TypeScript、Element Plus、Vite、Vitest。

## 全局约束

- 权威开发基线：`origin/main` 提交 `4b01992d0b132ab956ce9eb681eef5fbc7d102d9`。
- 权威开发工作区：`C:\Users\14567\.codex\worktrees\adcb\qherp`。
- 权威开发分支：`codex/029-project-cost-accounting`。
- V31 只能前向追加；不得修改 V1—V30，必须保持 V29 checksum `774334682`、V30 checksum `2130342893`。
- 029 只做项目成本核算和经营口径毛利；不得实现 030 月结、031 总账/正式凭证、032 关账或正式收入确认。
- 所有金额、数量、单价、毛利和毛利率在 API 中使用十进制字符串；前端不得使用浮点数计算业务结果。
- 029 对 009、023、027、028、销售和冲销事实只读；不得回写库存余额、价值流水、项目成本层、生产/外协单据、发票、费用或往来余额。
- 009 `mfg_cost_record` 继续保持 `formalAccounting=false` 的业务记录语义，不得改名或升级为正式项目成本子账。
- 新增和受影响页面必须完整遵守 `docs/ui/page-standards.md`；只验收真实桌面页面，不截图、不测试移动端，但实现不得阻碍后续响应式适配。
- 开发期只运行受影响的定向测试；集中审查和差异复审通过后，才进入唯一交付前全量验证窗口。
- 业务实现、UI、前后端和测试执行必须由固定角色完成；主代理只负责冻结本文件、统筹、审查汇总、Git、交付和交接。

---

## 阶段状态

- 状态：设计与工作包已冻结，等待整包开发。
- 冻结日期：2026-07-19。
- 五角色目标讨论：已完成唯一一轮。
- 开发基线：正式 API/Web 正常；Flyway V30；V29/V30 checksum 分别为 `774334682`、`2130342893`；失败迁移 0；数据库可用文件与 MinIO 对象 18/18。
- 用户授权：用户明确要求主代理自主推进、自主决策。主代理在不改变正式收入确认和总账规则的前提下，冻结“多口径展示、发货经营收入为主口径”的方案。

## 背景与权威输入

### 已交付基础

- 009 已提供工单级材料、人工、制造费用和其他成本业务记录，但不是正式项目成本。
- 023 已提供不可变 `inv_value_movement`、公共库存计价池、项目实际成本层和金额权限。
- 027 已提供项目工单、领料、退料、补料、报工、完工、外协发料和外协收货事实，并保存项目、成本层和价值流水来源。
- 028 已提供销售/采购发票、外协结算、项目/公共费用及往来来源；外协发票和费用不回写库存价值。
- 销售履约已在发货行冻结未税单价、税额和未税金额；销售退货能够回溯原发货行。
- 销售项目已具备项目负责人、目标收入、目标成本和状态，是合同、订单、生产、采购、费用和经营分析的一级对象。

### 当前缺口

- 没有项目级正式成本核算运行、来源快照和可复算结果。
- 没有材料、人工、外协、制造费用和项目费用的统一去重口径。
- 没有生产成本在在制、完工和交付之间的可追溯成本分录。
- 没有外协暂估转实际、未定价人工、库存暂估与计算成本之间的结构化差异。
- 没有受审批的项目成本调整和公共制造费用手工分配。
- 没有明确标识口径、权限和完整性的项目经营毛利。

### 权威输入

- `AGENTS.md`
- `docs/handoffs/2026-07-12-project-handoff-current.md`
- `docs/product/product-decisions.md`
- `docs/product/business-flow.md`
- `docs/data/core-data-glossary.md`
- `docs/ui/page-standards.md`
- `docs/tasks/009-cost-collection-foundation.md`
- `docs/tasks/023-inventory-valuation-stock-integrity.md`
- `docs/tasks/027-project-production-outsourcing-foundation.md`
- `docs/tasks/028-invoice-expense-settlement-deepening.md`

## 方案比较与采用结论

### 方案一：多口径展示，发货经营收入为主口径（采用）

- 主口径：已过账销售发货行未税金额减已过账销售退货对应的原发货未税金额。
- 辅助口径：已确认销售发票未税金额。
- 计划口径：销售项目目标收入。
- 优点：主收入与实际交付更匹配；开票提前或滞后不会改变主口径；合同目标、开票和交付差异均可见。
- 限制：只是项目经营分析，不是正式收入确认；存在在制、暂估或未定价来源时，毛利必须标记“不完整”。

### 方案二：仅用已确认开票收入（不采用）

- 优点：028 数据最直接，对账简单。
- 不采用原因：开票可早于或晚于交付，会把开票节奏误当项目履约节奏，影响项目经营判断。

### 方案三：多口径并列但不指定主口径（不采用）

- 优点：不预设经营判断。
- 不采用原因：工作台缺少统一主指标，030 和 033 仍需再次决定；不利于形成稳定的项目经营口径。

## 阶段目标

029 完成后，授权用户必须能够：

1. 按销售项目查看当前项目成本总额、分类、阶段、差异和完整性。
2. 创建截至指定业务日期的项目成本核算运行，并获得稳定来源快照。
3. 从项目成本行追溯到库存价值流水、工单领退补料、报工、完工、外协订单/收货/发票、费用和销售履约来源。
4. 区分材料、人工、外协、制造费用、项目费用和调整；区分实际、暂估、未定价和受限来源。
5. 查看生产相关成本在在制、完工和交付之间的分布，且分布变化不重复增加项目总成本。
6. 对项目成本调整和公共制造费用分配提交固定审批；确认后形成独立成本分录，不改写来源单据。
7. 查看外协暂估转实际、未定价、来源断链、项目串用和库存暂估差异。
8. 查看发货主口径、开票辅助口径、目标计划口径及对应项目经营毛利，并明确识别“不完整”状态。
9. 在来源变化、并发、重复提交、版本冲突和权限不足时得到稳定、可审计的结果。

## 范围

### 纳入范围

- V31 项目成本核算数据模型、权限和固定审批场景。
- 项目成本核算运行、来源收集、来源指纹、重算、确认、取消和历史快照。
- 材料、人工、外协、制造费用、项目费用和调整成本。
- 在制、完工、交付和项目直接费用阶段结果。
- 外协暂估与实际差异、人工未定价、库存暂估、来源断链和分配尾差。
- 项目成本调整和公共制造费用显式手工分配。
- 发货、开票、目标三种收入与项目累计经营毛利。
- 成本工作台、项目详情、核算运行详情、来源追溯抽屉、调整/分配页面、差异清单。
- 销售项目详情的成本摘要和有权跳转。
- 009 成本记录入口的历史语义回归保护。
- 迁移、权限、审计、并发、幂等、十进制、真实数据对账和真实桌面页面验证。

### 排除范围

- 030 会计期间、月结检查、月末冻结、成本月结或反结。
- 031 科目、正式会计凭证、凭证号、记账和总账。
- 032 财务关账。
- 正式收入确认准则、履约进度计量、税务申报和收入递延。
- 多币种、汇兑损益和税务成本。
- 工资率、工资核算、复杂工时工资、设备折旧或机器工时引擎。
- 自动公共费用分摊、跨项目自动挪料和可配置通用分摊引擎。
- 重算历史库存出库价值、回写完工入库暂估价值或修改项目成本层。
- 按销售订单、客户、合同或文本猜测历史项目归属。
- 将销售发票、费用、凭证草稿或应收应付当作正式会计结果。
- 移动端、窄屏和触屏测试或验收。

## 业务对象与定义

### 一级成本对象

- 一级成本对象固定为 `sal_project` 销售项目。
- 项目工单、外协订单、费用、发票、库存价值流水、成本记录和销售履约是来源或下钻对象，不建立第二套项目主数据。
- 柜体序列号、批次和成本层只用于来源追溯，不作为一级成本对象。

### 成本分类

- `MATERIAL`：生产领料、补料、退料和外协发料形成的真实库存价值净额。
- `LABOR`：项目工单下具有明确金额的有效人工成本记录。
- `OUTSOURCING`：外协服务实际未税结算；尚未结算时为收货暂估。
- `MANUFACTURING_OVERHEAD`：项目工单下具有明确金额的有效制造费用记录，及经审批分配的公共制造费用。
- `PROJECT_EXPENSE`：已确认且明确归属项目的费用行，以及项目工单有效 `OTHER` 成本记录。
- `ADJUSTMENT`：经审批确认的增加或减少调整；调整必须保留原因和来源，不覆盖原成本分类事实。

### 成本阶段

- `WIP`：项目生产或外协已发生、尚未按完工/收货比例转出的成本。
- `FINISHED`：已经按已过账完工入库或外协合格收货比例转出的生产相关成本。
- `DELIVERED`：已完工成本中按同项目、同成品物料已过账净发货数量比例转出的成本。
- `DIRECT_PROJECT`：无法也不应按产出数量分配的项目费用和项目级调整。
- 阶段转移只改变分布，不改变项目成本总额。

### 来源状态

- `ACTUAL`：来源金额已由不可变库存价值、已确认费用或已确认外协发票证明。
- `PROVISIONAL`：外协已收货但尚无已确认实际结算，或库存来源仍为合法暂估。
- `UNPRICED`：只有数量，没有可计入金额；必须显示异常，不得按 `0` 计入。
- `ADJUSTED`：来自已确认项目成本调整或公共制造费用分配。
- `RESTRICTED`：当前用户无权查看来源或金额；接口不返回可反推金额的数据。
- `EXCLUDED`：草稿、取消、作废、公共且未分配、其他项目、历史未估值或重复来源。

## 核算口径

### 通用规则

- 核算范围为一个项目和一个 `cutoffDate`；只读取 `business_date <= cutoffDate` 或对应确认日期不晚于截止日的事实。
- 新建核算只允许 `ACTIVE` 或 `CLOSED` 项目；`DRAFT`、`CANCELLED` 项目不得新建核算。
- 计算使用 `BigDecimal`，中间值保持来源精度；对外金额统一四舍五入到 2 位，数量和单价最多 6 位。
- 每个来源行保存来源类型、头 ID、行 ID、业务编号、来源版本/更新时间、来源金额、计算金额和来源快照摘要。
- 来源唯一键固定为 `sourceType + sourceId + sourceLineId + costCategory + entryType`，同一运行内不得重复。
- 计算运行保存稳定排序后的来源摘要 SHA-256 `sourceFingerprint`；确认前重新收集并比较。
- 来源变化时返回 `PROJECT_COST_SOURCE_CHANGED` 和 HTTP 409；不自动覆盖已计算快照。

### 材料成本

- 金额真相只取 `inv_value_movement.inventory_amount`；`mfg_cost_record` 的自动材料记录只作追溯，不再次计额。
- 生产领料、生产补料、外协发料为正向材料成本；生产退料和合法冲销为负向材料成本。
- 项目工单显式消耗公共库存时，成本归属按工单项目进入项目成本；库存所有权事实仍保持公共，不回写项目层。
- 项目工单不得消费其他项目库存；发现跨项目来源即生成阻断差异，核算不得确认。
- `LEGACY_UNVALUED`、金额为空或来源断链的价值流水不得按零计入，必须生成阻断差异。

### 人工和制造费用

- 只读取项目工单下状态 `ACTIVE`、金额非空的 `mfg_cost_record`。
- `LABOR` 映射人工；`MANUFACTURING_OVERHEAD` 映射制造费用；`OTHER` 映射项目费用。
- 只有报工数量、没有金额的 `SOURCE_QUANTITY_ONLY` 记录进入 `UNPRICED` 差异，不计入金额、不阻止保存草稿，但阻止确认。
- `VOIDED` 记录、公共工单记录和其他项目工单记录全部排除。

### 外协成本

- 实际成本优先取 `fin_purchase_invoice` 中 `settlement_kind=OUTSOURCING`、`status=CONFIRMED`、项目一致的发票行未税金额。
- 同一外协收货行有实际发票时，不再计入收货暂估；实际金额与已保存暂估金额的差额进入 `OUTSOURCING_ACTUAL_VARIANCE`。
- 尚无实际发票时，取已过账外协合格收货的暂估金额并标记 `PROVISIONAL`。
- 外协发料的材料价值属于 `MATERIAL`，不得再次并入外协服务费。
- 外协发票、应付和收货库存价值均保持原值；029 只记录来源快照和差异。

### 项目费用和公共制造费用

- 项目费用只取 `fin_expense.status=CONFIRMED`、`ownership_type=PROJECT` 且 `project_id` 一致的费用行未税金额。
- 公共费用不自动进入任何项目；只有经 `PUBLIC_EXPENSE_ALLOCATION` 调整单显式分配并审批确认后进入制造费用。
- 同一公共费用行所有已确认分配金额之和不得超过该行未税金额；并发确认必须在同一锁和事务内校验。
- 取消或冲回已确认分配必须新增反向调整，不删除历史分录。

### 在制、完工和交付

- 生产/外协来源成本先进入 `WIP`。
- 工单完工转出比例为 `min(累计已过账完工数量 / 计划数量, 1)`；外协完工转出比例为 `min(累计已过账合格收货数量 / 计划数量, 1)`。
- 同一运行按项目、工单/外协订单、成品物料稳定分组，按来源金额比例把生产相关成本从 `WIP` 转入 `FINISHED`；分配尾差落在分组最后一行并生成可追溯尾差摘要。
- 交付转出比例按项目和成品物料计算：`min(已过账发货数量 - 已过账退货数量, 已完工数量) / 已完工数量`；已完工数量为 0 时不得转出。
- 发货和退货只作为阶段转移与收入来源，不再次增加项目成本。
- 发货数量超过可匹配完工数量时生成 `DELIVERY_WITHOUT_FINISHED_COST` 差异；允许查看草稿结果，但阻止确认。
- 项目费用和项目级调整保持 `DIRECT_PROJECT`，不参与产出数量自动分配。

### 项目经营毛利

- `shipmentRevenue`：项目订单的已过账发货行未税金额减已过账销售退货按原发货行未税单价计算的未税金额。
- `invoiceRevenue`：项目已确认销售发票未税金额。
- `targetRevenue`：销售项目目标收入，只作计划对照。
- `projectCostTotal`：材料、人工、外协、制造费用、项目费用和调整的净额；阶段转移不重复计入。
- `shipmentGrossMargin = shipmentRevenue - projectCostTotal`，为默认“累计经营毛利”。
- `invoiceGrossMargin = invoiceRevenue - projectCostTotal`，只作开票辅助口径。
- `targetGrossMargin = targetRevenue - projectCostTotal`，只作计划对照。
- 毛利率在对应收入大于 0 时为 `grossMargin / revenue * 100`；收入为 0 时返回 `null`，不得返回无穷、NaN 或 `0` 伪装。
- 存在 `PROVISIONAL`、`UNPRICED`、阻断差异、来源受限或 `WIP > 0` 时，返回 `marginCompleteness=INCOMPLETE` 和中文原因；不得宣称最终利润。

## 状态机与动作

### 项目成本核算运行

- `DRAFT -> CALCULATED`：收集来源、计算分录、差异和三种收入口径。
- `CALCULATED -> CALCULATED`：重算；版本递增并替换未确认快照。
- `CALCULATED -> CONFIRMED`：来源指纹未变化、阻断差异为 0、无未定价来源时确认。
- `DRAFT/CALCULATED -> CANCELLED`：取消未确认运行。
- `CONFIRMED` 为只读历史快照；不得取消、编辑或覆盖。
- 项目同一时刻最多一个未确认运行；新确认运行成为当前快照，旧确认运行仍保留并标记 `isCurrent=false`。
- `freshnessStatus` 独立为 `CURRENT/STALE`；来源变化只改变当前性判断，不改写历史确认状态。

### 项目成本调整/分配

- 类型：`PROJECT_ADJUSTMENT`、`PUBLIC_EXPENSE_ALLOCATION`、`VARIANCE_SETTLEMENT`。
- 状态：`DRAFT -> SUBMITTED -> CONFIRMED`；审批拒绝进入 `REJECTED`；草稿可 `CANCELLED`。
- `SUBMITTED` 必须创建 022 固定审批场景 `PROJECT_COST_ADJUSTMENT_CONFIRM`。
- 审批通过后由业务处理器原子确认调整、写成本分录并更新差异关联；审批拒绝只改变状态，不写成本分录。
- 已确认调整不可编辑或删除；冲回必须创建引用原调整行的反向调整。

### 差异

- 状态：`OPEN/RESOLVED/SUPERSEDED`。
- 差异通过来源补齐、重算或已确认调整解决；禁止直接修改差异金额或手工改状态。
- 阻断差异至少包括：历史未估值、跨项目来源、来源断链、人工未定价、发货无可匹配完工成本、来源重复、公共分配超额。
- 非阻断差异至少包括：外协暂估与实际、库存暂估与计算成本、分配尾差。

## V31 数据模型

V31 文件固定为 `apps/api/src/main/resources/db/migration/V31__project_cost_accounting_schema.sql`，前向新增下列表及约束：

### `prj_cost_calculation`

- 核算号、项目、截止日期、状态、当前标记、来源指纹、来源统计、三种收入、成本总额、阶段总额、三种毛利、完整性、操作审计、版本。
- 同一项目仅允许一个 `DRAFT/CALCULATED` 活动运行。
- 金额 `numeric(18,2)`，数量 `numeric(18,6)`；状态和金额完整性使用检查约束。

### `prj_cost_source_line`

- 运行、来源类型、来源头/行、业务编号、项目、工单/外协订单、成品物料、成本分类、阶段、来源状态、数量、来源金额、计算金额、来源版本、来源时间、来源摘要、纳入原因。
- 运行内来源唯一键必须覆盖来源、分类和分录类型，防止 V7 材料记录与价值流水重复计额。
- 受限字段不通过查询 DTO 暴露；数据库仍保存完整审计来源。

### `prj_cost_entry` 与 `prj_cost_entry_line`

- 成本分录不是会计借贷分录，不包含科目、会计期间、凭证号或记账状态。
- 分录类型：`SOURCE_TO_WIP`、`WIP_TO_FINISHED`、`FINISHED_TO_DELIVERED`、`PROJECT_DIRECT`、`PROJECT_ADJUSTMENT`、`COST_VARIANCE`。
- 分录行保存来源成本桶、目标成本桶、分类、金额、数量和来源行；每张分录借方意义金额与贷方意义金额必须平衡，但接口使用“转入/转出”业务文案。

### `prj_cost_adjustment` 与 `prj_cost_adjustment_line`

- 头保存类型、业务日期、状态、原因、审批实例、操作审计、幂等键和版本。
- 行保存目标项目、可选目标工单/外协订单、分类、增减方向、金额、可选公共费用来源或原调整来源。
- 公共费用分配按来源费用行建立索引和确认金额总额校验。

### `prj_cost_variance`

- 保存运行、项目、差异类型、严重级别、来源、预计金额、实际金额、差额、状态、解决调整行和中文说明。

### `prj_cost_action_idempotency`

- 保存作用域、对象、动作、操作者、幂等键、请求指纹、响应对象和创建时间。
- 同键同载荷返回原结果；同键异载荷返回 HTTP 409。

### 迁移兼容

- V31 不回填 V7 成本记录、不补造 V20 历史未估值金额、不推导 V29 历史公共工单项目、不补造 V30 发票或费用。
- 运行时可以消费既有且本身具备合法项目归属、状态和金额的历史事实；“不回填”不等于排除合法历史来源。
- V31 同时新增权限、角色授权和固定审批场景种子；管理员角色获得全部 029 权限，普通角色不自动获得成本金额权限。

## 后端接口契约

统一前缀为 `/api/admin/cost`。

### 项目成本和核算运行

- `GET /project-costs`：分页项目成本工作台；支持项目、负责人、项目状态、当前性、差异、完整性和截止日期筛选。
- `GET /project-costs/projects/{projectId}`：项目当前成本详情和历史运行摘要。
- `POST /project-costs/projects/{projectId}/calculations`：创建并计算运行，请求包含 `cutoffDate`、`idempotencyKey`。
- `GET /project-cost-calculations/{id}`：运行详情。
- `GET /project-cost-calculations/{id}/sources`：按分类、阶段、状态、来源类型分页查询来源。
- `GET /project-cost-calculations/{id}/entries`：分页查询成本分录。
- `GET /project-cost-calculations/{id}/variances`：分页查询差异。
- `GET /project-cost-variances`：全局分页差异清单；支持项目、严重级别、类型、状态和来源受限筛选。
- `PUT /project-cost-calculations/{id}/recalculate`：携带 `version`、`idempotencyKey` 重算。
- `PUT /project-cost-calculations/{id}/confirm`：携带 `version`、`sourceFingerprint`、`idempotencyKey` 确认。
- `PUT /project-cost-calculations/{id}/cancel`：携带 `version`、`idempotencyKey` 取消。

### 调整与分配

- `GET/POST /project-cost-adjustments`
- `GET/PUT /project-cost-adjustments/{id}`
- `PUT /project-cost-adjustments/{id}/submit`
- `PUT /project-cost-adjustments/{id}/cancel`
- 审批通过/拒绝由 022 固定审批处理器调用领域服务，不暴露绕过审批的公共确认接口。
- `GET /project-cost-adjustments/candidates/public-expenses`：独立分页查询可分配公共费用行及剩余可分配金额。

### 响应约束

- 金额、数量、单价、毛利和比例字段全部为十进制字符串或 `null`。
- 每个详情响应必须包含 `version`、`allowedActions`、`actionDisabledReasons`、`amountVisible`、`sourceVisible`、`restrictedReason`。
- 来源响应包含稳定 `sourceType`、可见时的来源 ID/编号、`sourceRoute` 和 `returnTo`；无原模块权限时只返回脱敏中文摘要。
- 无金额权限不得返回 `0`、汇总差额、毛利率或可反推金额的分项。

### 锁、幂等和错误码

- 锁顺序：按 `projectId` 升序获取项目成本事务级 advisory lock → 锁 029 自身运行/调整头 → 锁调整行或活动运行 → 按稳定来源键重新校验来源版本。
- 029 不锁或写公共计价池、项目成本层、库存余额、028 往来余额，避免与上游写入互锁。
- 稳定错误码至少覆盖：`PROJECT_COST_PROJECT_INVALID`、`PROJECT_COST_SOURCE_CHANGED`、`PROJECT_COST_SOURCE_UNVALUED`、`PROJECT_COST_SOURCE_CROSS_PROJECT`、`PROJECT_COST_SOURCE_BROKEN`、`PROJECT_COST_LABOR_UNPRICED`、`PROJECT_COST_DELIVERY_UNMATCHED`、`PROJECT_COST_ADJUSTMENT_OVER_ALLOCATED`、`PROJECT_COST_VERSION_CONFLICT`、`PROJECT_COST_IDEMPOTENCY_CONFLICT`、`PROJECT_COST_ACTION_NOT_ALLOWED`、`PROJECT_COST_AMOUNT_FORBIDDEN`。

## 权限与审计

### 权限组

- `cost:project-cost:view`
- `cost:project-cost:source-view`
- `cost:project-cost:amount-view`
- `cost:project-cost:calculate`
- `cost:project-cost:confirm`
- `cost:project-cost:cancel`
- `cost:project-cost-adjustment:view`
- `cost:project-cost-adjustment:create`
- `cost:project-cost-adjustment:update`
- `cost:project-cost-adjustment:submit`
- `cost:project-cost-adjustment:cancel`
- `cost:project-cost-variance:view`

### 组合权限

- 查看项目不等于查看成本；项目详情只在拥有 `cost:project-cost:view` 时显示成本摘要。
- 查看材料来源金额同时要求 `cost:project-cost:amount-view` 和 `inventory:valuation:view`。
- 查看外协结算、费用或发票原始来源仍要求对应 028 查看权限。
- 无来源模块权限时允许显示“已纳入一条受限来源”，但不得返回内部 ID、金额或可反推字段。
- 导出、来源抽屉、审计摘要和项目详情摘要与主接口使用同一后端权限规则。

### 审计要求

- 记录创建核算、重算、确认、取消、来源变化冲突、权限拒绝、调整创建/编辑/提交/取消、审批确认/拒绝、差异解决和幂等重放。
- 审计保存项目、运行/调整、来源指纹、版本、幂等键、金额摘要、操作者和时间。
- 无金额权限用户查询审计时，金额摘要必须脱敏。

## 页面与 `page-standards.md` 强制矩阵

### 页面范围

- `/cost/project-costs`：项目成本工作台。
- `/cost/project-costs/:projectId`：项目成本详情。
- `/cost/project-cost-calculations/:id`：核算运行详情。
- `/cost/project-cost-adjustments`：调整/分配列表。
- `/cost/project-cost-adjustments/create`、`/:id/edit`、`/:id`：调整/分配表单与详情。
- `/cost/project-cost-variances`：差异清单。
- 来源追溯抽屉：由核算详情和差异清单复用。
- 受影响页面：销售项目详情增加成本摘要和返回链；009 成本记录继续显示“业务记录与追溯，不是正式项目成本核算”。

### 工作台

- 页头显示“项目成本核算”和一句业务说明，不使用宣传性文案。
- 首屏最多四个主指标：当前项目成本、在制成本、未解决差异、发货口径经营毛利；无金额权限时整组脱敏。
- 筛选标签置顶；日期范围使用两个日期控件；项目、负责人、当前性、差异和完整性筛选独立。
- 主表默认 10 条分页；金额右对齐并使用等宽数字；宽表只在内部容器横向滚动。
- 项目状态、核算状态、完整性、暂估和权限状态全部中文化，不只依赖颜色。

### 项目成本详情

- 顶部优先展示项目、截止日期、核算状态、当前性和完整性，再展示金额。
- 成本分类、阶段、三种收入口径、毛利和差异采用清晰分区，不嵌套卡片。
- 毛利主指标明确标注“发货经营口径”；开票和目标口径不得暗示正式财务利润。
- 来源详情使用抽屉；审计信息独立；返回链使用 `returnTo`。
- 核算、重算、确认和取消按钮完全由 `allowedActions` 驱动，禁用原因可见。

### 核算运行详情

- 分类来源、阶段分录、收入毛利和差异分别分页或分区；不得一次加载全部来源。
- 显示来源指纹摘要、版本、截止日期、计算人/确认人和时间。
- 确认和取消使用项目统一中文确认弹窗；409 冲突不自动重试，提示用户重算。
- 已确认运行只读；旧确认运行明确标识“历史快照”。

### 来源抽屉和差异清单

- 来源类型、项目、业务日期、状态筛选独立；来源候选池不受主列表 10 条分页限制。
- 来源受限和金额受限使用明确中文状态，不显示 `0.00` 或空白伪装。
- 差异显示严重级别、类型、来源、预计/实际/差额、解决状态和关联调整。
- 宽表内部滚动，抽屉限制最大宽高，内容区滚动且底部动作可达。

### 调整/分配页面

- 项目和公共费用候选使用独立后端搜索分页；不得只从当前主列表选择。
- 明确显示剩余可分配金额、各项目分配金额和总分配；确认前给出高风险中文说明。
- 草稿可编辑，提交后只读；拒绝原因、审批状态、原调整和冲回关系可见。
- 金额和数量错误在字段附近显示；内部码全部中文化。

### 受影响页面

- 销售项目详情只增加成本摘要、有权动作和跳转，不复制完整核算表格。
- 生产工单、外协、费用、发票和库存来源页面不新增 029 写动作；来源追溯通过既有详情与返回链完成。
- `App.vue` 和 `router/index.ts` 不继续堆积成本路由与菜单；成本路由、菜单必须触达式拆分。

### 桌面验收

- 真实 Chromium 1280×720；不截图、不测试移动端。
- 覆盖加载、空态、错误、无权限、金额受限、来源受限、正常数据、筛选清空、分页、宽表滚动、抽屉、返回链、只读态、冲突和审批态。
- 控制台错误、未解析组件、资源失败、页面级横向溢出、控件重叠和关键动作遮挡均为 0。

## 文件结构与职责

### 后端

- 创建 `apps/api/src/main/resources/db/migration/V31__project_cost_accounting_schema.sql`：V31 表、约束、索引、权限和审批场景。
- 创建 `apps/api/src/main/java/com/qherp/api/system/projectcost/ProjectCostAdminController.java`：HTTP 契约、请求响应记录和权限入口。
- 创建 `apps/api/src/main/java/com/qherp/api/system/projectcost/ProjectCostCalculationService.java`：运行状态、重算、确认、指纹和幂等协调。
- 创建 `apps/api/src/main/java/com/qherp/api/system/projectcost/ProjectCostSourceCollector.java`：按来源真相矩阵只读收集和去重。
- 创建 `apps/api/src/main/java/com/qherp/api/system/projectcost/ProjectCostEntryBuilder.java`：分类、阶段转移、分录平衡、尾差和毛利计算。
- 创建 `apps/api/src/main/java/com/qherp/api/system/projectcost/ProjectCostQueryService.java`：工作台、详情、来源、分录、差异和权限脱敏。
- 创建 `apps/api/src/main/java/com/qherp/api/system/projectcost/ProjectCostAdjustmentService.java`：调整、公共费用分配、审批处理和反向调整。
- 创建 `apps/api/src/main/java/com/qherp/api/system/projectcost/ProjectCostErrorCode.java`：稳定错误码。
- 不修改 `FinanceStage028Service` 承载 029 计算；只允许在必要时增加稳定只读来源查询或复用现有表。

### 后端测试

- 创建 `apps/api/src/test/java/com/qherp/api/system/projectcost/ProjectCostV31MigrationRegressionTests.java`。
- 创建 `apps/api/src/test/java/com/qherp/api/system/projectcost/ProjectCostAdminControllerTests.java`。
- 创建 `apps/api/src/test/java/com/qherp/api/system/projectcost/ProjectCostStage029Tests.java`，覆盖架构边界、权限种子、十进制和不回写门禁。
- 修改受影响的最新 Flyway 版本断言，使 V31 成为合法最新版本，同时保留 V29/V30 checksum 和历史语义断言。

### 交付验证工具

- 修改 `tools/demo-data/sql/validate-demo-data.sql`：最新迁移精确为 V31，固化 V31 checksum，并继续严格校验 V29 `774334682`、V30 `2130342893` 和失败迁移 0。
- 修改 `tools/demo-data/lib/demo-data-self-test.ps1`：防止最新版本规则弱化为 `>=31`，并覆盖 V29/V30/V31 精确校验。
- 若 029 最小真实数据纳入演示数据生成器，修改 `tools/demo-data/generate-demo-data.ps1` 通过真实 API/状态机生成；禁止 SQL 直灌 029 业务事实。

### 前端

- 创建 `apps/web/src/shared/api/projectCostApi.ts` 与 `projectCostApi.spec.ts`：字符串十进制、分页、动作、权限和来源契约。
- 创建 `apps/web/src/modules/cost/project/` 页面族和聚焦组件：工作台、详情、运行详情、来源抽屉、调整列表/表单/详情、差异列表、分类/阶段/完整性标签。
- 创建 `apps/web/src/modules/cost/project/ProjectCostViews.spec.ts`：页面规范、权限、动作、十进制和来源追溯规格。
- 创建 `apps/web/src/router/modules/costRoutes.ts`：迁出既有 `/cost/records` 与新增 029 路由。
- 创建 `apps/web/src/navigation/costMenu.ts`：迁出成本菜单和首个有权路径选择。
- 修改 `apps/web/src/router/index.ts` 和 `apps/web/src/App.vue`，只保留模块接线。
- 创建 `apps/web/src/modules/sales/projects/SalesProjectCostSummaryPanel.vue` 及规格，并修改销售项目详情接入。
- 保留 009 成本记录页面和 API；只补“非正式项目成本”语义及回归，不改旧数据模型为字符串成本核算模型。

## 完整业务工作包

### 工作包一：项目成本核算运行与材料成本底座

**完整能力：** V31 基础表、项目核算运行、材料来源收集、来源指纹、分录、权限、项目工作台/详情/运行详情和销售项目摘要。

**后端交付：** `ProjectCostCalculationService`、材料 `ProjectCostSourceCollector`、查询服务、控制器、迁移和定向测试；证明公共库存被项目工单显式消耗时归项目成本，V7 材料记录不重复计额。

**前端交付：** API 客户端、成本路由/菜单拆分、工作台、项目详情、运行详情、材料来源抽屉、销售项目成本摘要和定向规格。

**定向验证：** 材料领料 48、公共料显式领用 110、退料 -12、补料 22，材料净额必须为 168；跨项目、历史未估值、来源断链、重复来源和无金额权限均按矩阵返回。

### 工作包二：人工、制造费用、项目费用与调整分配

**完整能力：** 有金额人工/制造费用/其他记录归集、无金额报工差异、项目费用、公共制造费用显式分配、项目调整、022 审批和差异解决。

**后端交付：** 调整/分配表和服务、审批处理器、公共费用剩余额度并发校验、反向调整、费用/人工来源收集、权限审计和定向测试。

**前端交付：** 调整/分配列表、表单、详情、独立候选池、审批态、差异清单和定向规格。

**定向验证：** 项目人工 300、项目费用 200、公共制造费用分配 50；只有数量的报工进入未定价差异；公共费用不得自动进入项目或超额分配。

### 工作包三：外协、成本阶段与项目经营毛利

**完整能力：** 外协暂估/实际切换、差异、在制/完工/交付转移、发货/退货净收入、开票和目标辅助口径、完整性与项目毛利。

**后端交付：** 外协来源收集、阶段分录、发货与退货收入、三种毛利、差异、幂等/并发和定向测试；证明 029 不回写外协库存价值或 028 往来事实。

**前端交付：** 成本阶段、外协、三种收入口径、毛利和完整性展示，来源跳转、冲突提示和定向规格。

**定向验证：** 外协实际 120 替换暂估并形成差异；示例项目总成本 838、发货未税收入 10000、发货口径累计经营毛利 9162；存在暂估、未定价、在制或阻断差异时必须标记不完整。

## 最小真实测试数据集

- 项目 P1：合法销售项目、项目订单和已过账发货，未税收入 10000。
- 项目 P1 材料：项目工单领料 48、公共库存显式领用 110、退料 -12、补料 22，净额 168。
- 项目 P1 人工：一条金额 300 的有效人工记录；一条只有数量的报工记录。
- 项目 P1 外协：已过账外协收货及暂估，后续已确认外协采购发票未税 120。
- 项目 P1 费用：已确认项目费用未税 200；公共制造费用经审批分配 50。
- 预期 P1 项目成本 838；发货口径累计经营毛利 9162。
- 项目 P2：至少一套材料或费用来源，用于证明不串入 P1。
- 公共事实：一条未分配公共费用、一条历史公共工单，不得自动进入 P1。
- 历史异常：V20 历史未估值来源、V7 无项目成本记录、V29 公共工单、V30 公共费用，均不得静默推导或写零。
- 权限账号：管理员、项目成本只读、无金额、无库存估值、无财务来源、无项目成本权限至少六种组合。
- 所有业务事实必须通过真实 API 和合法状态机建立；禁止直接插入业务表代替验收链路。

## 验收矩阵

- 数值：分类合计等于项目成本总额；阶段合计与直接项目费用可对账；阶段转移不改变总成本；三种毛利公式一致。
- 来源：每条成本行能追到唯一来源；材料不被 V7 与价值流水重复计额；外协暂估被实际替换但历史可追溯。
- 项目隔离：P2、公共、其他项目成本层和历史公共工单不进入 P1。
- 状态：只允许合法核算、调整和审批动作；已确认快照只读；来源变化确认返回 409。
- 权限：无项目成本权限不可访问；无金额权限不能从汇总、来源、导出、审计或毛利反推成本。
- 并发：同项目单活动运行；同公共费用并发分配不超额；同幂等键同载荷返回原结果、异载荷 409。
- 迁移：空库和 V30 存量到 V31 通过；V29/V30 checksum 不变；失败迁移 0。
- 历史：不补造项目、不把未估值写零、不修改 009 正式核算标识、不改写来源金额。
- 回归：023 库存价值、027 生产/外协、028 发票/费用/往来和 009 成本记录核心路径保持通过。
- 页面：全部新增和受影响页面逐项满足页面规范、权限态、异常态、返回链和桌面视觉门禁。

## 开发期定向验证命令

后端受影响范围：

```powershell
Set-Location apps/api
.\mvnw.cmd -Dtest=ProjectCostStage029Tests,ProjectCostV31MigrationRegressionTests,ProjectCostAdminControllerTests,AccountPermissionInitializerTests,CostAdminControllerTests,ProjectProductionStage027Tests,FinanceStage028ControllerTests test
```

前端受影响范围：

```powershell
Set-Location apps/web
npm test -- ProjectCostViews.spec.ts projectCostApi.spec.ts SalesProjectDetailView.spec.ts CostRecordListView.spec.ts App.spec.ts permissionGuard.spec.ts
```

开发期不运行前端全量、后端全量、生产构建或完整浏览器验收。

## 集中审查分工

- 产品经理：核对成本对象、分类、来源真相、阶段、调整、毛利口径、排除项和业务验收矩阵。
- UI 设计师：检查本阶段真实桌面页面的逐页规范映射、状态、信息密度、权限、异常、抽屉和返回链。
- 前端开发：审查后端非本人实现的数据模型、接口、十进制、权限、幂等、错误码和服务边界。
- 后端开发：审查前端非本人实现的 API 消费、权限态、动作态、十进制、路由菜单拆分和来源追溯。
- 测试：检查覆盖、最小真实数据、迁移、数值对账、项目隔离、并发、历史兼容、异常和回归风险。
- 主代理：合并去重，阻断/严重问题一次性派回原角色；一般项进入后续清单。

## 唯一交付前全量验证窗口

集中审查、一次集中整改和差异复审通过后，测试固定角色一次性执行并统一记录：

1. 后端全量测试。
2. 前端全量测试。
3. 前端类型检查和生产构建。
4. V1 到 V31 空库迁移、V30 存量升级和正式库临时副本升级。
5. 精确 V31 独立验证器，继续校验 V29/V30 checksum、失败迁移 0 和历史语义。
6. 真实数据库成本来源与项目成本行 SQL 对账，证明库存、生产、财务来源未被改写。
7. 临时数据库、临时 MinIO、隔离 API/Web 健康和资源一致性。
8. 真实 Chromium 1280×720 功能、权限和逐页视觉检验，不截图、不测试移动端。
9. 正式环境健康、数据库可用文件与 MinIO 对象一致性只读核对。

全量窗口必须先执行全部计划内项目再统一汇总；发现缺陷不得逐个中断窗口。汇总后一次性整改，只复验缺陷差异和受影响路径。

## 阻断与严重规则

### 阻断

- V31 迁移失败、修改 V1—V30、V29/V30 checksum 改变或正式资源不一致。
- 项目成本重复、漏算、串项目、未估值写零或金额不平。
- 029 回写库存价值、生产/外协、发票、费用、应收应付或凭证草稿。
- 来源变化后仍确认、并发产生重复活动运行或公共费用超额分配。
- 无权限用户获得成本金额、毛利、来源 ID 或可反推数据。
- 核心 API、页面或合法状态机不可执行。

### 严重

- 暂估与实际混淆、未定价人工被当零成本、毛利无口径或完整性标签。
- 在制、完工、交付转移错误但项目总成本尚可对账。
- 审批、幂等、版本、审计或稳定错误码不完整。
- 来源抽屉、权限态、异常态、候选池或返回链影响关键业务判断。
- 页面级溢出、关键动作遮挡、内部码裸露或违反相关页面规范。

### 一般

- 不影响当前验收、权限、数据可信度或关键扫描效率的文案、微小间距和非关键易用性问题。
- 一般项默认登记后续清单，不重复开启全阶段审查或阻断交付。

## 变更控制

- 任何成本分类、来源、阶段、毛利、数据迁移、页面或验收口径变化，必须先在本文件记录原因、影响文件、数据兼容、验收变化和主代理决策。
- 重大产品方向改变、扩大到 030/031/032、无法满足原目标或需要降低标准时，必须提交用户确认。
- 实现细节可由主代理在本文件边界内自主决策，但仍必须派发给对应固定角色实现。

## 完成定义

只有同时满足以下条件，029 才可宣称完成：

1. 三个完整业务工作包功能完整，定向测试通过。
2. 唯一集中审查完成，阻断和严重问题一次性整改并通过差异复审。
3. 唯一交付前全量验证窗口全部计划内项目完成并通过，或缺陷按规则完成一次性整改和差异复验。
4. V31 空库、存量和正式副本迁移通过，V29/V30 checksum 与历史语义保持不变。
5. 最小真实数据通过合法 API/状态机建立，项目成本 838、发货经营毛利 9162 真实对账。
6. 真实桌面页面逐项符合 `page-standards.md`，控制台和页面级错误为 0。
7. 功能分支提交干净，纯快进合入 `main` 并推送；本地 `main`、`origin/main`、实际远端哈希一致。
8. 从主分支完成部署，正式 API/Web 可访问，正式数据库和 MinIO 一致，当前交接同步完成。

## 执行记录

- 2026-07-19：核对正式 `main`、`origin/main`、实际远端均为 `4b01992d0b132ab956ce9eb681eef5fbc7d102d9`；正式 API/Web、V30、checksum、数据库/MinIO 18/18 基线正常。
- 2026-07-19：重建产品经理、UI 设计师、前端开发、后端开发、测试五个固定 `gpt-5.5/xhigh` 长期会话，并登记当前交接。
- 2026-07-19：五角色完成唯一一轮目标讨论。共同确认新增独立 V31 项目成本核算子账、保留 009 历史语义、只读消费 023/027/028、独立权限脱敏和真实桌面页面规范门禁。
- 2026-07-19：用户明确授权主代理自主推进、自主决策。主代理采用“多口径展示、发货未税收入为经营毛利主口径、开票辅助、目标计划对照”，不扩展为正式收入确认。
- 2026-07-19：从最新 `origin/main` 在当前平台隔离工作树建立 `codex/029-project-cost-accounting` 分支；本文件冻结为 029 唯一阶段说明。
- 2026-07-19：测试实现预检发现权限初始化和精确 V31 验证器属于 029 必要受影响范围。主代理补充 `AccountPermissionInitializerTests` 定向回归及 `tools/demo-data` 精确 V31/V29/V30 校验，不改变业务范围、状态机或验收口径。
- 2026-07-19：前端实现预检发现阶段已冻结全局差异页面，但接口清单仅列按运行查询。主代理补充 `GET /api/admin/cost/project-cost-variances` 全局分页接口；按运行查询继续保留。该修正只闭合既有页面可达性，不新增业务范围。
- 2026-07-19：唯一一轮五角色集中审查完成。真实 `1280×720` 临时桌面环境复现工作台字段空显、项目详情崩溃和调整详情缺行；交叉审查同时发现分录/来源/差异脱敏、阶段比例、外协发料、销售退货、公共费用并发、未知外协成本和核算动作幂等缺口。主代理判定这些均为既有冻结契约的阻断或严重实现偏差，不改变 029 产品范围，进入一次集中整改。
- 2026-07-19：集中整改统一采用阶段领域名作为后端契约：工作台、项目详情和运行详情使用各自专用 DTO，明确 `calculationStatus`、`freshnessStatus`、`marginCompleteness`、`projectCostTotal`、`shipmentRevenue` 及分类/阶段/历史/审计摘要；前端 API 层显式适配，不再把同一个计算响应冒充三个页面模型。调整行统一为 `costCategory`、`costStage`、`publicExpenseLineId`、`reason`；调整类型保持 `PROJECT_ADJUSTMENT/PUBLIC_EXPENSE_ALLOCATION/VARIANCE_SETTLEMENT`；差异严重级别统一为 `INFO/WARNING/BLOCKING`，状态保持 `OPEN/RESOLVED/SUPERSEDED`；来源状态统一为 `ACTUAL/PROVISIONAL/UNPRICED/ADJUSTED/RESTRICTED/EXCLUDED`。
- 2026-07-19：V31 尚未进入 `main` 或正式数据库，允许在本功能分支内修正 V31 检查约束、枚举和并发索引，不新建补丁迁移；影响仅限临时审查库，正式 V30 与 V29/V30 checksum 不变。V31 稳定后必须重新计算 checksum、更新精确验证器并从正式 V30 副本重建临时库；差异复审只覆盖整改差异和受影响路径。
- 2026-07-19：集中整改完成后，后端受影响定向集合 `107/107`、前端定向集合 `148/148`、V1→V31/V30→V31 迁移和正式 V30 副本验证器 `117/117` 通过；V31 最终 checksum 固化为 `-2074547591`，V29/V30 checksum 与失败迁移 0 保持不变。
- 2026-07-19：五个固定角色完成整改差异复审。测试覆盖门禁通过，但真实 `1280×720` 页面和交叉契约审查仍发现既有冻结契约未闭合：工作台包含详情接口拒绝的草稿项目、合法未核算项目缺少首次 `CALCULATE`、净发货超出可匹配完工量未生成 `DELIVERY_WITHOUT_FINISHED_COST/BLOCKING`、公共费用候选未按财务来源权限脱敏；另有动作态未反映已知阻断/未定价风险、调整类型与行字段矩阵不严、材料金额未叠加库存估值权限，以及前端把受限调整金额 `null` 转为 `0`。主代理判定这些为 4 个后端阻断、3 个后端严重和 1 个前端严重，不改变 029 范围，继续回派原固定前后端角色做最小闭合后只复验这些差异。
- 2026-07-19：差异复审的一般项登记后续清单，不阻断当前修复：来源响应由前端注入 `returnTo`、差异 `expectedAmount/actualAmount` 尚无稳定来源值、受限来源前端防御性清空、旧字段别名淘汰和调整候选选中态。除非其在后续复验中升级为数据、权限或核心路径风险，本阶段不再为这些一般项开启审核循环。
