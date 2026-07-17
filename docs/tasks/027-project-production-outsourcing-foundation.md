# 027 项目生产执行与外协基础

> 固定角色执行要求：本文件同时承载 027 的目标、业务规则、状态机、权限、接口、数据迁移、工作包、验收矩阵、阻断规则和执行记录，是本阶段唯一权威说明。实现必须复用产品经理、UI 设计师、前端开发、后端开发、测试五个固定长期角色；不得另建重复规格、实施计划或第六类角色。

## 阶段状态

- 权威工作区：`C:\Users\14567\.codex\worktrees\304d\qherp`。
- 讨论与实现基线：`origin/main@484db3f39ae0e674ebaba9cff619d1a0129bce7e`。
- 实现分支：`codex/027-project-production-outsourcing`，从上述基线建立并跟踪 `origin/main`。
- 目标数据库迁移版本：V29，只允许从 V28 前向追加，不修改 V1 至 V28。
- 产品经理、UI 设计师、前端开发、后端开发、测试唯一一轮阶段目标讨论已完成；用户已授权主代理自主推进至验收，本说明按讨论推荐口径冻结。
- 阶段开始时唯一已知工作树差异为 `docs/handoffs/2026-07-12-project-handoff-current.md` 的固定角色线程登记，属于主代理治理修改，不属于业务实现产物。
- 三个整包工作包、唯一一轮集中审查、一次集中整改、差异复审和唯一交付前全量验证均已完成；阻断和严重问题为 0，当前进入交付文档、Git 合入、正式迁移与主分支验收服务阶段。

## 背景与权威输入

### 上游已交付事实

- 020 已确立销售项目为一级成本、收入和利润对象；工单作为项目生产事实明细，序列号用于单台追溯和后续成本下钻。
- 021 已交付 BOM 版本、发布快照、工程变更、单位换算、损耗率、物料来源类型和替代料提示；已发布对象不得被后续 BOM/ECO 倒改。
- 022 已交付固定审批、审计、附件、幂等、失败关闭和文档任务底座。
- 023 已交付公共库存移动平均池、项目库存实际来源成本层、不可变价值流水、所有权边界、期间锁定和批次/序列追踪；027 必须复用统一库存过账和估值，不得另建平行库存或成本体系。
- 024 已交付公共采购、项目专采、项目采购入库成本层和有效采购供给。
- 025 已交付有效销售需求与项目履约边界；025A 已统一桌面页面规范。
- 026 已交付物料需求运行、短缺、供给分配及采购、生产、公共库存使用建议。生产和外协建议只读、不可转换；027 只能承接已确认建议，不得重算、覆盖或修正 026 历史快照。
- 当前生产已有工单、BOM 用料快照、领料、退料、补料、简单报工、完工入库及批次/序列追踪；现有工单没有项目归属和 MRP 来源，领补料与完工默认公共库存语义，系统没有外协执行对象。

### 权威输入文件

- `AGENTS.md`
- `docs/handoffs/2026-07-12-project-handoff-current.md`
- `docs/ops/stage-collaboration-delivery-process.md`
- `docs/product/product-decisions.md`
- `docs/tasks/020-sales-project-contract-foundation.md`
- `docs/tasks/021-cost-financial-master-data-bom-governance.md`
- `docs/tasks/023-inventory-valuation-stock-integrity.md`
- `docs/tasks/024-procurement-project-sourcing.md`
- `docs/tasks/025-sales-project-fulfillment.md`
- `docs/tasks/025a-page-governance-remediation.md`
- `docs/tasks/026-order-driven-shortage-supply-advice.md`
- `docs/ui/page-standards.md`
- `docs/api/api-contract-baseline.md`

## 2026-07-17 决策记录

### 阶段单一目标

建立“项目生产执行与外协基础”闭环，使 026 已确认的生产或外协建议能够受控、幂等地承接为项目生产工单或外协订单，并在项目归属下完成领料、退料、补料、简单报工、完工入库、外协发料、外协收货和批次/序列追溯，为后续项目成本归集提供可信业务事实；027 不计算正式在制成本、完工成本、制造费用、外协结算或财务凭证。

业务闭环固定为：

`销售项目需求 → 026 生产/外协建议 → 项目工单或外协订单 → 领退补料或外协发料 → 报工/完工或外协收货 → 项目库存、库存价值事实与批序追溯`

### 方案比较与采用结论

1. 外协复用生产工单类型：实现面较小，但供应商、发料、收货、完成和关闭语义会污染内部生产工单，不采用。
2. 外协建立独立执行对象并复用项目归属、库存过账、估值和追溯底座：领域边界清晰，且不重复建设库存体系，采用。
3. 同期实现完整外协采购、对账、应付和发票：超出 027 且与后续财务阶段重叠，不采用。

### 已冻结业务与技术决策

- 026 建议转换只允许 `CONFIRMED` 且父运行当前有效；`OPEN`、`DISMISSED`、`CONVERTED`、`STALE`、`EXPIRED`、来源变化或版本不匹配均拒绝。
- 一条 026 建议在 027 只允许整单转换为一个目标对象，不做部分转换、拆单或多目标分配。
- 自制建议转换为 `DRAFT` 项目/公共生产工单；外协建议转换为 `DRAFT` 外协订单。转换不自动发布、预留、发料、领料或过账。
- 转换后只把建议置为 `CONVERTED` 并写目标引用；目标取消不自动重开旧建议，需要新一轮计划或受控人工事实处理。
- 项目归属、来源建议和 BOM 快照在生产工单或外协订单发布后冻结。
- 项目工单或外协订单可以显式消耗同项目库存，也可显式消耗公共库存；禁止消耗其他项目库存。公共库存消耗仍保留公共所有权来源，并在生产/外协事实中记录目标项目，不伪装成库存所有权转换。
- 退料只能基于原已过账领料，退回原所有权、原项目、原成本层和原批次/序列来源；不得重新选择归属或按当前均价重估。
- 补料按领料的项目/公共来源规则处理，不自动替代物料、不自动跨项目挪料。
- 项目生产完工和项目外协收货进入同项目库存；公共对象进入公共库存。027 沿用现有暂估能力，价值物料缺少可靠成本时强制录入暂估单价，不宣称正式项目成本。
- 简单报工只记录合格、不合格和进度，不增加工序、工作中心、派工、工时或制造费用。
- 历史无项目工单保持公共/未绑定语义，不回填或伪造项目。
- 所有副作用写接口必须提供 `version` 与 `idempotencyKey`；同键同载荷返回同一结果，同键异载荷和版本冲突返回 409，前端不得自动重放 409。
- 数量使用 `NUMERIC(18,6)` 并以十进制字符串传输；前端不得用浮点运算形成可信数量或价值事实。
- 触达式增量拆分是 027 的强制架构约束：冻结巨型文件继续膨胀，只拆真实触达路径，不做全仓重构。

## 范围

### 本次包含

- 生产工单项目/公共归属、项目筛选、026 来源追溯和历史无项目工单兼容。
- 026 自制建议幂等转换为草稿生产工单。
- 项目感知的工单发布、领料、退料、补料、简单报工和完工入库。
- 项目/公共库存来源显式选择、跨项目隔离、项目成本层、批次/序列和价值流水一致性。
- 独立外协订单、外协用料快照、外协发料、外协收货、完成/关闭、供应商、项目与批序追溯。
- 026 外协建议幂等转换为草稿外协订单。
- 新权限、后端 `allowedActions`、字段脱敏、审计、稳定错误码、并发和期间保护。
- V29 空库、V28 存量升级和历史兼容迁移。
- 生产菜单、规划转换入口、项目工单页面、外协页面族、销售项目详情生产/外协摘要与跳转。
- 027 真实触达的后端生产、规划转换、生产退补料及前端菜单、路由、API、页面增量拆分。
- 受影响后端/前端测试、集中审查、差异复审、唯一交付前全量验证及 1280×720 桌面浏览器功能与视觉检验。

### 本次不包含

- 工艺路线、工作中心、工序派工、工序转移、有限产能、APS 或完整 MES。
- 自动替代料、自动跨项目挪料、自动所有权转换或自动公共库存转项目。
- 完整 QMS、外协检验流程、SPC、让步放行或质量成本。
- 完整外协采购、供应商价格、对账、发票、应付、付款、凭证或总账。
- 正式在制成本、完工成本、制造费用、项目成本结转、月结或财务结账。
- 外协余料退回、外协拆单、多供应商分配、建议部分转换或目标取消后自动重开建议。
- 反向业务服务的销售、采购、结算等非生产路径拆分。
- 全仓后端服务、前端路由、菜单、页面或 API 的一次性重构。
- 移动端、窄屏或响应式验收；实现结构仍不得阻碍后续响应式开发。

## 角色分工

- 产品经理：集中审查业务闭环、状态、上下游边界和验收矩阵；不承担实现。
- UI 设计师：集中审查真实桌面页面的信息结构、状态表达、关键动作、权限态和可访问性；不编写业务代码。
- 前端开发：实现生产/规划菜单路由拆分、项目工单、外协页面族、来源追溯、API 客户端和前端定向测试。
- 后端开发：实现 V29、建议转换、项目生产执行、外协执行、库存价值事务、权限、审计、稳定错误码、触达式拆分和后端定向测试。
- 测试：独立执行迁移、集成、权限、异常、并发、幂等、历史兼容、桌面浏览器和交付前全量验证；不代替业务实现。
- 主代理：负责范围、工作包、依赖、审查去重、文档、Git、交接和交付决策，不参与业务实现或测试执行。

## 业务对象与状态机

### 生产工单

- 沿用 `DRAFT → RELEASED → IN_PROGRESS → COMPLETED`，以及受控 `CANCELLED`。
- `DRAFT` 且无已过账业务时允许修改项目、仓库、数量、日期和 BOM；发布后项目、来源和 BOM 用料快照冻结。
- 已发生领料、退料、补料、报工或完工过账后不得删除、退回草稿或取消。
- 项目工单 `ownershipType=PROJECT` 且 `projectId` 必填；公共工单 `ownershipType=PUBLIC` 且 `projectId` 必须为空。

### 生产执行单据

- 领料、报工、完工入库沿用 `DRAFT/POSTED`。
- 退料、补料沿用 `DRAFT/POSTED/CANCELLED`。
- 领料与补料行必须显式记录本次库存来源 `ownershipType/projectId/costLayerId`；退料从原领料继承，不允许编辑。
- 报工不产生库存或价值流水；完工入库产生库存数量、追踪分配和暂估价值流水。

### 外协订单

- 状态固定为 `DRAFT/RELEASED/IN_PROGRESS/COMPLETED/CLOSED/CANCELLED`。
- `DRAFT` 可编辑；`RELEASED` 冻结项目、供应商、物料、BOM 和来源建议并允许发料/收货；首次发料或收货进入 `IN_PROGRESS`。
- 累计合格收货达到计划数量后进入 `COMPLETED`；`COMPLETED` 可显式关闭为 `CLOSED`。
- 仅无已过账发料或收货的 `DRAFT/RELEASED` 可取消；取消不重开来源建议。

### 外协发料与收货

- 单据状态固定为 `DRAFT/POSTED/CANCELLED`；只有草稿可编辑或取消，已过账不可取消或修改。
- 发料按同项目/公共库存规则扣减并记录价值来源；不得消耗其他项目库存。
- 收货数量不得导致累计合格数量超过计划数量；项目外协收货进入同项目库存，公共外协收货进入公共库存。
- 批次数量合计必须等于业务数量；序列物料每个序列数量为 1 且全局唯一。

## V29 数据模型与历史兼容

### 扩展 `mfg_work_order`

- 新增 `ownership_type`：`PUBLIC/PROJECT`，历史数据默认 `PUBLIC`。
- 新增可空 `project_id`，外键到 `sal_project`。
- 新增可空 `source_mrp_run_id/source_mrp_suggestion_id/source_mrp_requirement_line_id`。
- 约束：`PUBLIC` 时项目为空，`PROJECT` 时项目非空；`source_mrp_suggestion_id` 非空唯一。
- 索引：`project_id,status,planned_finish_date`、`source_mrp_run_id`。
- 不修改历史工单编号、状态、数量或 BOM 快照。

### 扩展生产执行行

- `mfg_material_issue_line`、`mfg_material_supplement_line` 持久化出库 `ownership_type/project_id/cost_layer_id`。
- `mfg_material_return_line` 持久化继承的 `ownership_type/project_id/cost_layer_id`，并继续保留原领料行关联。
- `mfg_completion_receipt` 或其业务行持久化 `ownership_type/project_id`；项目归属由工单派生，不允许表单另选。
- 单据行到库存和值流水仍使用既有 `source_type/source_id/source_line_id` 追溯；批次/序列拆分时不强制单个移动 ID 表达全部结果。

### 新增外协表

- `mfg_outsourcing_order`：编号、所有权、项目、供应商、成品物料、单位、BOM、计划数量、计划日期、发料/收货仓库、MRP 来源、状态、累计发料/收货、版本、审计字段。
- `mfg_outsourcing_order_material`：订单、行号、BOM 行、材料、单位、需求量、已发量、损耗率和快照字段。
- `mfg_outsourcing_issue` 与 `mfg_outsourcing_issue_line`：草稿/过账、仓库、业务日期、库存来源、成本层、批序分配和库存移动来源。
- `mfg_outsourcing_receipt` 与 `mfg_outsourcing_receipt_line`：草稿/过账、收货仓库、业务日期、合格/不合格数量、暂估单价、批序分配和库存移动来源。
- `mfg_outsourcing_order.source_mrp_suggestion_id` 非空唯一；外协订单号唯一；订单材料行号唯一；发料/收货 `client_request_id` 在各自业务对象内唯一。

### 迁移原则

- V29 只能使用前向 `ALTER/CREATE/INSERT`，不得改写 V1 至 V28 文件或 checksum。
- 空库必须直接迁移至 V29；V28 存量库必须保留历史工单、退补料、库存价值和 026 快照。
- 迁移后历史工单统一为公共/未绑定；不得根据销售订单、BOM、备注或编号猜测项目。
- 权限种子与管理员默认授权进入 V29，同时同步 `AccountPermissionInitializer`，保证新库、旧库和运行时初始化一致。

## 接口契约

### 026 建议转换

- `POST /api/admin/planning/material-requirement-suggestions/{id}/convert-work-order`
- `POST /api/admin/planning/material-requirement-suggestions/{id}/convert-outsourcing-order`
- 请求：`version`、`idempotencyKey`；转换目标所需仓库、计划日期、BOM 或供应商在创建草稿后由目标对象编辑，不由规划页面猜算。
- 响应：`suggestionId/status/targetObjectType/targetObjectId/targetObjectNo/targetRoute/version`。
- 转换事务：锁定建议 → 校验父运行与来源指纹 → 校验建议状态、物料来源和版本 → 创建目标草稿 → 写回目标引用及转换人/时间 → 记录审计；任何一步失败全部回滚。

### 生产工单与执行

- `GET /api/admin/production/work-orders` 增加 `projectId/ownershipType/sourceMrpSuggestionId` 筛选。
- 生产工单请求和响应增加项目、所有权、MRP 来源、`allowedActions` 与 `version`；历史公共工单项目字段为空。
- 领料和补料请求行增加 `ownershipType/projectId/costLayerId`；后端返回可选来源摘要和稳定禁用原因。
- 退料请求不接受所有权、项目或成本层覆盖，只接受原领料行、数量、批序和原因。
- 完工入库归属由工单派生；暂估单价只在后端返回 `requiresManualProvisionalUnitCost=true` 时必填。

### 外协执行

- `GET/POST /api/admin/production/outsourcing-orders`
- `GET/PUT /api/admin/production/outsourcing-orders/{id}`
- `PUT /api/admin/production/outsourcing-orders/{id}/release|close|cancel`
- `GET/POST/PUT /api/admin/production/outsourcing-orders/{id}/material-issues[/{documentId}]`
- `PUT /api/admin/production/outsourcing-orders/{id}/material-issues/{documentId}/post|cancel`
- `GET/POST/PUT /api/admin/production/outsourcing-orders/{id}/receipts[/{documentId}]`
- `PUT /api/admin/production/outsourcing-orders/{id}/receipts/{documentId}/post|cancel`
- 列表支持项目、供应商、物料、状态、计划日期和关键字筛选；响应必须含项目、来源建议、进度、`allowedActions`、`version` 和权限脱敏状态。

### 稳定错误语义

- 新增或冻结：`PRODUCTION_PROJECT_REQUIRED`、`PRODUCTION_PROJECT_INVALID`、`PRODUCTION_PROJECT_MISMATCH`、`PRODUCTION_PLANNING_SUGGESTION_INVALID`、`PRODUCTION_PLANNING_SUGGESTION_ALREADY_CONVERTED`、`PRODUCTION_OWNERSHIP_SOURCE_INVALID`、`PRODUCTION_OUTSOURCING_STATUS_INVALID`、`PRODUCTION_OUTSOURCING_SUPPLIER_REQUIRED`。
- 优先复用：`INVENTORY_OWNERSHIP_PROJECT_MISMATCH`、`INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT`、库存不足、期间锁定、追踪分配和幂等冲突的既有错误码。
- 校验错误使用 400，未认证 401，无权限 403，不存在 404，版本、幂等、状态、来源变化和并发冲突使用 409。
- 前端显示后端中文业务信息，不拼接内部异常；409 不自动重试。

## 权限、脱敏与审计

### 权限

- `planning:material-requirement:convert-production`
- `planning:material-requirement:convert-outsourcing`
- 生产工单继续使用既有查看、创建、更新、发布、完成和取消权限。
- `production:outsourcing:view/create/update/release/close/cancel`
- `production:outsourcing-issue:view/create/update/post/cancel`
- `production:outsourcing-receipt:view/create/update/post/cancel`
- 规划转换同时要求对应转换权限和目标对象创建权限。
- 来源详情跳转继续要求销售项目、规划、生产、库存或供应商来源权限；追溯页面不得绕过来源模块鉴权。
- 成本层及金额继续要求 `inventory:valuation:view`；无权限返回 `costVisible=false` 和脱敏摘要，不得返回 0 伪装金额。

### 审计

- 必须覆盖建议转换、重复转换、项目绑定和变更、发布、取消、领料、退料、补料、报工、完工、外协发料、外协收货、关闭、期间拒绝、权限拒绝、幂等冲突和序列分配。
- 审计摘要保存来源建议、目标对象、项目、所有权来源、成本层引用、批序摘要、操作者和时间；无估值权限的响应不得因审计查询泄露成本金额。

## 并发、事务与锁顺序

- 写动作统一使用数据库事务、行级锁、版本和幂等记录；库存数量、追踪分配、成本层和值流水必须同事务提交。
- 建议转换锁顺序：`mrp_suggestion → 目标草稿`。
- 生产/外协过账统一锁顺序：`建议或订单/工单 → 单据头 → 单据行/用料快照 → 预留与追踪分配 → 成本层或估值池 → 库存余额`。
- 项目库存只能选择同项目成本层；公共库存只能选择公共池；任何跨项目、冻结、待检、不合格、已占用或不足资源均拒绝。
- 退料按原领料来源锁定和回冲，不按当前可用库存或当前均价重新选择。

## 页面与交互契约

### 页面族

- 生产菜单保留“生产工单、生产退料、生产补料”，新增“外协执行”。
- 生产工单列表增加项目、所有权、来源建议、执行进度筛选和列；历史公共工单明确显示“公共/未绑定”。
- 工单详情作为总览枢纽，分为摘要、项目与建议来源、BOM/用料、领退补料、报工、完工、库存流水和追溯入口；重表格进入分区或抽屉，不继续堆成单一长页面。
- 外协采用独立 `/production/outsourcing-orders` 页面族：列表、创建/编辑、详情、发料和收货。
- 026 详情只新增“转生产工单/转外协订单”动作、禁用原因和目标链接，不改变快照、建议状态或净算展示语义。
- 销售项目详情增加生产/外协摘要和有权跳转，不创建第二套生产数据。

### 状态与权限表达

- 所有页面使用后端 `allowedActions/actionDisabledReason` 作为动作真相；菜单和按钮隐藏不能替代后端鉴权。
- `STALE/EXPIRED` 来源建议必须禁用转换并提示重新计算；不能只显示灰色而缺少原因。
- 项目、公共所有权、跨项目拒绝、成本受限、批序必填、期间锁定和版本冲突必须有明确中文状态。
- 1280×720 桌面端必须能识别项目、单号、状态、来源、进度和主要动作；不得出现页面级横向溢出、底部动作不可达或多层抽屉遮挡。

## 触达式增量拆分与文件所有权

### 后端

- `ProductionAdminService` 保留既有查询聚合和兼容门面；027 新增项目/所有权策略进入 `ProductionOwnershipPolicy`，建议转换进入 `ProductionPlanningConversionService`，生产库存编排进入 `ProductionInventoryPostingCoordinator`。
- `MaterialRequirementPlanningService` 不修改净算主流程，只把生产/外协转换委托给转换服务；现有采购建议转换行为保持不变。
- `ReversalAdminService` 只把生产退料/补料过账与项目估值上下文抽到 `ProductionMaterialReversalService`；销售、采购、结算反向路径不移动。
- 外协控制器、服务、状态和请求/响应模型放入 `apps/api/src/main/java/com/qherp/api/system/production/outsourcing/`，不继续塞入 `ProductionAdminService`。
- 统一库存过账继续调用 `InventoryPostingService`；不得直接写库存余额、成本层或价值流水。

### 前端

- 从 `apps/web/src/router/index.ts` 抽出 `apps/web/src/router/modules/productionRoutes.ts` 和 `planningRoutes.ts`；生产根路由跳转到当前用户首个有权页面，不固定跳到工单。
- 从 `apps/web/src/App.vue` 抽出 `apps/web/src/navigation/productionMenu.ts` 和 `planningMenu.ts`；027 菜单不继续直接写入应用壳。
- 冻结 `apps/web/src/shared/api/productionApi.ts` 继续膨胀；兼容接口可保留，新项目生产契约进入 `apps/web/src/shared/api/projectProductionApi.ts`，外协进入 `productionOutsourcingApi.ts`。
- 工单详情拆出项目来源摘要、执行记录、追溯和动作栏组件；外协页面放入 `apps/web/src/modules/production/outsourcing/`。
- 规划详情只增加转换动作和目标链接，不复制目标对象表单或状态机。

### 架构门禁

- `ProductionAdminService`、`MaterialRequirementPlanningService`、`ReversalAdminService`、`App.vue`、`router/index.ts`、`productionApi.ts` 只允许必要的委托、导入、兼容映射和接线修改，禁止新增整段 027 核心业务逻辑。
- 门禁以行为等价、事务边界、锁顺序、权限、错误语义、定向测试和回归结果为准，不以机械行数下降作为唯一指标。

## 整包工作计划

### 工作包一：建议承接与项目生产工单

**完整能力**：V29 生产工单扩展、权限种子、026 自制建议转换、项目/公共工单创建与发布冻结、列表/详情/表单/规划入口和来源追溯。

**主要文件**：

- 新建 `apps/api/src/main/resources/db/migration/V29__project_production_outsourcing_foundation.sql`。
- 新建 `apps/api/src/main/java/com/qherp/api/system/production/ProductionPlanningConversionService.java`。
- 新建 `apps/api/src/main/java/com/qherp/api/system/production/ProductionOwnershipPolicy.java`。
- 修改生产、规划控制器与服务、权限初始化器及对应权限测试。
- 新建 `apps/api/src/test/java/com/qherp/api/system/production/ProjectProductionStage027Tests.java`。
- 新建前端项目生产 API、生产/规划路由与菜单模块；修改工单与规划页面及对应规格测试。

**定向测试**：先写失败测试并确认因缺少项目/转换能力失败，再实现；覆盖有效转换、重复幂等、异载荷冲突、过期/陈旧/状态/版本拒绝、历史公共工单、项目发布冻结和权限。

### 工作包二：项目生产执行

**完整能力**：项目/公共来源领料、原路退料、补料、简单报工、项目完工入库、暂估、批序追溯和库存价值同事务。

**主要文件**：

- 新建 `apps/api/src/main/java/com/qherp/api/system/production/ProductionInventoryPostingCoordinator.java`。
- 新建 `apps/api/src/main/java/com/qherp/api/system/reversal/ProductionMaterialReversalService.java`。
- 修改生产/反向控制器与服务、库存过账调用和对应测试。
- 修改生产领料、报工、完工及生产退补料前端页面、API 和规格测试。

**定向测试**：先写跨项目拒绝、公共来源显式领用、退料原路回冲、项目成本层不足、期间锁定、批序不匹配、数量价值原子性和项目完工入库失败测试，再实现并保持既有公共工单路径转绿。

### 工作包三：外协执行闭环

**完整能力**：外协建议转换、外协订单、用料快照、供应商、发料、收货、完成/关闭、项目库存、暂估和批序追溯。

**主要文件**：

- 新建 `apps/api/src/main/java/com/qherp/api/system/production/outsourcing/` 下的控制器、服务、状态、请求/响应和过账编排文件。
- 新建 `apps/api/src/test/java/com/qherp/api/system/production/outsourcing/ProductionOutsourcingStage027Tests.java`。
- 新建 `apps/web/src/shared/api/productionOutsourcingApi.ts` 及规格测试。
- 新建 `apps/web/src/modules/production/outsourcing/` 下的列表、表单、详情、发料、收货、状态组件和规格测试。
- 修改销售项目详情生产/外协摘要与规格测试。

**定向测试**：先写外协转换、状态动作、供应商必填、跨项目发料、过量收货、暂估必填、序列唯一、权限和关闭后拒绝测试，再实现完整闭环。

### 并行与依赖

- 前端与后端在本说明冻结后并行；后端先固定字段和接口命名，前端可先以显式类型和受控桩完成组件红测，不得猜算业务规则。
- 工作包一的数据与来源契约是工作包二、三的真实依赖；同一后端角色可在一个长期线程内按一、二、三顺序实现，前端按共同契约并行推进页面族。
- 测试角色在前后端定向测试通过后执行独立集成，不提前制造全量验证门禁。
- 每个工作包包含实现与受影响测试，不单独拆红测、编码、转绿、审查或返工作为流程任务。

## 开发期定向验证命令

- 后端生产/外协：`cd apps/api && .\mvnw.cmd -Dtest=ProjectProductionStage027Tests,ProductionOutsourcingStage027Tests,ProductionAdminControllerTests,MaterialRequirementPlanningStage026Tests,ReversalAdminControllerTests test`。
- 后端权限：`cd apps/api && .\mvnw.cmd -Dtest=PermissionAuthorizationTests,AccountPermissionInitializerTests test`。
- 前端项目生产与外协：`cd apps/web && npm test -- ProductionWorkOrderListView.spec.ts ProductionWorkOrderFormView.spec.ts ProductionWorkOrderDetailView.spec.ts ProductionExecutionForms.spec.ts ProductionMaterialReversalViews.spec.ts MaterialRequirementViews.spec.ts projectProductionApi.spec.ts productionOutsourcingApi.spec.ts`。
- 前端路由菜单：`cd apps/web && npm test -- permissionGuard.spec.ts App.spec.ts`，以仓库实际应用壳规格文件为准补充 027 断言，不为命令不存在另建无意义文件。
- 开发期不运行全量后端、全量前端、完整构建或完整浏览器验收。

## 集中审查矩阵

- 产品经理：核对建议转换、项目/公共库存、工单与外协状态、上下游边界、正式成本排除和业务验收矩阵。
- UI 设计师：核对本阶段真实桌面页面的信息结构、项目/来源/状态表达、关键动作、禁用原因、溢出和可达性。
- 前端开发：交叉审查后端接口契约、权限、稳定错误码、历史兼容和触达式拆分，不审查自己的前端实现。
- 后端开发：交叉审查前端是否依赖后端真相、是否泄露权限字段、是否正确处理 409/脱敏/历史公共工单和触达式拆分，不审查自己的后端实现。
- 测试：核对覆盖、异常、并发、幂等、期间、库存价值、批序、迁移、历史兼容和回归风险。
- 主代理合并去重后一次性派发阻断和严重问题；一般问题默认登记后续清单，不重复开启全阶段审核。

## 验收矩阵

### 功能与数据

- 已确认、有效的自制建议只能生成一个草稿项目/公共工单；外协建议只能生成一个草稿外协订单。
- 重复同键请求返回同一目标；同键异载荷、重复目标、过期、陈旧、无效状态、版本冲突均稳定拒绝。
- 项目工单发布后冻结项目、来源和 BOM 快照；历史公共工单保持既有可查可执行路径。
- 项目工单领料/补料不得消耗其他项目库存；同项目和显式公共库存路径分别正确。
- 退料继承原领料所有权、项目、成本层、价值来源和批序身份，不重估、不改项目。
- 报工只累计合格/不合格；完工不得超过可完工数量，项目完工进入项目库存，公共完工进入公共库存。
- 外协订单可发布、发料、收货、完成和关闭；供应商、项目、物料、BOM、批序和库存流水可追溯。
- 序列物料每个序列数量为 1 且唯一；批次数量合计等于单据数量。
- 库存数量、成本层、追踪分配和值流水同事务成功或失败，不允许部分提交。
- 027 不修改 026 历史快照、不回填历史工单项目、不修改已发布 BOM 快照。

### 权限、审计与错误

- 无对应权限不能转换、创建、发布、发料、收货、领退补料、完工或查看受限来源。
- 无估值权限看不到成本层金额，不以 0 伪装；无来源权限只返回脱敏摘要。
- 所有关键动作、拒绝、幂等和并发冲突均有审计，且来源与目标可双向追溯。
- 400/401/403/404/409 与冻结错误码语义一致，前端不自动重放 409。

### 迁移与历史兼容

- V29 空库迁移、V28 存量升级、含历史生产/退补料/库存价值/026 快照数据的升级全部通过。
- V1 至 V28 checksum 不变，失败迁移为 0；历史工单默认公共且项目为空。
- 测试使用 Testcontainers、临时数据库和临时 bucket；失败关闭对象存储测试不得污染正式数据库或 `qherp-private`。

### 前端与桌面浏览器

- 生产根路由跳转当前用户首个有权子页面；菜单、路由、按钮和后端权限一致。
- 工单列表/详情能清楚识别项目、公共归属、来源建议、状态、进度和主要动作。
- 规划详情能正确转换、显示禁用原因并跳转唯一目标，不改变 026 快照表达。
- 外协列表、详情、发料和收货在 1280×720 下无页面级横向溢出，关键动作可见可达。
- 项目、所有权、成本受限、期间、批序和并发冲突不产生误导；控制台无归因于 027 的错误。
- 视觉检验基于真实运行页面，不截图、不建立视觉目录、不测试移动端。

### 架构与回归

- 027 核心逻辑进入冻结的独立服务、路由、菜单、API 和页面边界；巨型文件只保留必要委托和兼容接线。
- 026 采购建议转换和净算主流程行为不变；既有公共生产、生产退补料、库存追踪和权限路径不回归。
- 结构拆分前后错误码、权限、锁顺序、事务和响应语义保持可验证一致。

## 缺陷与阻断

### 阻断缺陷

- V29 迁移失败、V1 至 V28 checksum 改变或历史生产数据损坏。
- 同一建议生成多个目标，或 027 倒改 026/021 历史快照。
- 项目工单或外协单能消耗其他项目库存。
- 退料丢失原所有权、项目、成本层或批序来源。
- 库存数量成功但价值/追踪失败，或价值/追踪成功但数量失败。
- 权限绕过或项目、供应商、成本数据泄露。
- 生产/外协核心页面不可访问、关键动作不可执行或项目归属误导。
- 触达式拆分导致既有生产、退补料、规划或库存关键路径回归。

### 严重缺陷

- 状态与允许动作不一致，来源或审计链不完整，期间保护错误，暂估口径误称正式成本。
- 外协发料/收货数量或批序统计错误，026 有效供给读取项目归属错误。
- 桌面页面明显降低项目、库存归属或权限状态判断准确性。

## 唯一交付前全量验证窗口

集中审查、一次整改和差异复审通过后，统一执行并记录：

1. 显式 JDK 21 下全量后端测试。
2. 全量前端测试。
3. 前端类型检查和生产构建。
4. V29 空库、V28 存量、正式只读副本前迁和 checksum 回归。
5. 正式数据库、对象存储、进程归属、端口和环境健康核验。
6. 1280×720 真实桌面浏览器功能与视觉检验，覆盖本阶段新增和修改页面；不截图、不测试移动端。
7. 全量结果一次性汇总去重；若发现缺陷，统一派发整改并只复验差异和受影响路径。

## 执行记录

- 2026-07-17：从 `origin/main@484db3f39ae0e674ebaba9cff619d1a0129bce7e` 建立 `codex/027-project-production-outsourcing`。
- 2026-07-17：复用当前交接登记的产品经理、UI 设计师、前端开发、后端开发、测试五个固定长期线程完成唯一一轮目标讨论；五角色均核对权威工作区、分支、HEAD 和 `origin/main`，且未产生业务修改。
- 2026-07-17：用户授权主代理按推荐口径自主推进至验收；主代理合并去重并冻结独立外协对象、建议单目标草稿转换、项目/公共库存显式来源、项目完工暂估和触达式增量拆分。
- 2026-07-17：三个整包工作包完成。后端将建议转换、所有权策略、库存过账协调、动作幂等、生产反向业务和外协执行拆为独立协作服务，保留既有公共生产兼容入口；前端将计划/生产菜单、路由和工单详情部件按业务边界拆分，并新增独立外协页面族，未继续扩大原有大文件职责。
- 2026-07-17：开发期定向测试完成后组织唯一一轮集中审查。产品经理核对业务验收矩阵，UI 设计师检查真实桌面页面，前后端交叉审查非本人实现，测试角色检查覆盖、异常路径和回归风险；首轮合并去重后没有范围偏移，发现并一次性整改两项严重差异：建议类型统一为规范 `PRODUCTION_WORK_ORDER`/`OUTSOURCING_ORDER`，外协发料/收货编辑重新加载时保留备注。
- 2026-07-17：集中整改后只复验差异和受影响路径。后端受影响及相邻回归 15 个报告、92/92 通过；前端受影响 10 个文件、125/125 通过；外协备注专项 2 个文件、17/17 通过；独立类型检查和生产构建通过，构建 2129 个模块，只有既有非阻断分块大小警告。产品、前端交叉后端、测试复审结论均为阻断 0、严重 0。
- 2026-07-17：唯一交付前全量窗口按“全部执行后统一整改”完成。首次结果为后端 569 项中 42 项旧契约/夹具失败、前端 106 个文件 918/918 通过、类型检查/构建存在 17 个类型错误；一次性整改旧强写请求版本与幂等键、V29 迁移期望、026 建议状态/类型夹具、历史项目空值期望及前端可空值/响应类型后，完成上述差异复验，没有再次开启全阶段审查。
- 2026-07-17：V1 空库直迁 V29、V28 存量副本前迁 V29、历史工单/退补料/库存价值/026 快照兼容均通过；V29 checksum 为 `774334682`、失败迁移 0，历史工单保持公共且项目为空。验证期间正式库保持 V28、失败迁移 0，数据库 `AVAILABLE` 文件与 `qherp-private` 对象保持 18/18；临时数据库、bucket、18082/5175 服务和过程脚本已清理。
- 2026-07-17：交付收口发现独立演示数据验证器仍精确要求 V28，固定测试角色按最小差异同步为精确 V29 与 checksum `774334682`，保留失败迁移为 0 和文件对象一致性规则，并增加防 V28 回退、防 `>=29` 放宽自测。自测 1/1、SQL 正式 V28 只读解析和 `git diff --check` 通过；V28 库按预期只失败 V29 规则，正式 114/114 留待主分支自然前迁 V29 后执行。
- 2026-07-17：1280×720 真实 Chromium 桌面功能与视觉检验完成，不截图、不测试移动端。隔离真实链路覆盖建议 7 转工单 9、建议 8 转外协订单 5、重复转换 409、无估值/仅工单/仅外协/只读权限、工单新建/编辑/详情/执行/退补料、外协订单 2 及发料/收货草稿 3 的编辑过账关闭和非空备注保存回读；相关控制台、页面异常和网络错误为 0，计划内未执行项为 0。
- 合入、推送和主分支部署：尚未开始。
- 一般后续：服务边界直接调用在缺少 `CurrentUser` 时可进一步统一显式返回 `AUTH_FORBIDDEN`；一个前端测试挂载辅助仍使用 `component as never`。两项不影响已认证 HTTP 链路、运行时类型、权限、数据或当前验收，不阻断 027 交付。
- 结论：027 功能、集中审查整改和全量验证已完成，允许进入交付文档、Git 与主分支部署阶段。

## 完成定义

只有同时满足以下条件，027 才可标记完成：

- 本文件全部纳入范围已实现，未提前实现排除项。
- 三个完整业务工作包及受影响定向测试通过。
- 唯一集中审查、一次集中整改和差异复审完成，阻断/严重问题为 0。
- 唯一交付前全量验证窗口全部通过。
- V29 空库/存量/正式只读副本迁移、历史兼容、数据库和对象存储隔离通过。
- 真实桌面浏览器功能与视觉通过，未截图、未验收移动端。
- 功能分支提交并按项目约定合入、推送 `origin/main`。
- 从主分支启动验收服务，核对本地/远端哈希并更新本文件、交接、必要业务流程、数据词汇和操作手册。
