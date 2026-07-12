# 销售项目与合同管理基础接口契约

## 目标

定义 `020 销售项目与合同管理基础` 的后端接口、数据模型、权限、错误码、审计、事务和历史兼容口径，作为前后端实现、联调和测试依据。

本阶段建立销售项目作为一级成本、收入和利润对象的统一业务标识，并支持一个项目下最多一个非取消主合同、多个补充合同和销售订单可选关联。020 不交付项目实际成本、库存计价、发票、收入确认、总账、月结或采购、生产、库存、成本、财务接口。

## 通用规则

- 所有接口使用统一 `ApiResponse<T>` 包装。
- 分页接口返回 `PageResponse<T>`。
- 写接口需要 CSRF token。
- 认证和权限沿用账号权限模块，后端接口鉴权是最终安全边界。
- 接口基路径采用独立销售项目域：`/api/admin/sales-projects`。
- 合同独立资源路径采用：`/api/admin/sales-project-contracts`。
- 项目编号、内部合同编号由后端生成，不允许前端传入或修改。
- 外部纸质合同号可选、可重复，用于记录线下合同编号，不参与后端自动编号，不建立唯一约束，可按关键词查询。
- 金额使用后端 `BigDecimal` 和数据库 `numeric(18,2)`，禁止使用浮点数承载业务计算。
- 日期使用 `LocalDate`，时间戳使用带时区 ISO 8601 字符串。
- 020 不使用 `BusinessPeriodGuard`。项目和合同属于商业主数据和管理事实，不作为库存、成本、往来或财务期间写入。
- 所有写入和状态动作必须记录系统审计，审计基于现有 `sys_audit_log`，不扩展审计表，不要求结构化 before/after。
- 生效前可编辑草稿；生效后关键商业字段不可直接覆盖，商业增减通过补充合同表达。
- 历史销售订单允许 `projectId` 和 `contractId` 为空，不回填、不伪造项目归属。

## 明确排除范围

- 不设计采购、生产、库存、成本或财务接口。
- 不新增采购订单、生产工单、库存流水、成本记录、应收应付或凭证的项目字段。
- 不实现项目专属库存、库存计价、项目专采、报价、信用控制、缺料净算、工单项目成本、发票、收入确认、凭证、总账或月结。
- 不实现复杂审批、工作流设计器、工艺路线、工作中心、精细工序或复杂质量管理。
- 不建立泛化合同版本模型，不建立独立合同变更业务表。
- 不拆分税额，不把合同金额作为收入确认、开票或财务入账依据。

## 数据模型与落库约束

以下为 020 后端迁移和接口实现必须遵守的模型和约束。本文不创建迁移；后续迁移必须把能由数据库稳定表达的唯一、外键、枚举、金额、日期和空值组合约束落库，跨表状态、客户一致性、权限和审计摘要由服务层保证。

### `sal_project`

销售项目主档。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `bigserial` | 主键 | 项目标识 |
| `project_no` | `varchar(64)` | 唯一，非空 | 后端生成项目编号 |
| `name` | `varchar(100)` | 非空 | 项目名称 |
| `customer_id` | `bigint` | 外键 `mst_customer(id)`，非空 | 客户 |
| `owner_user_id` | `bigint` | 外键 `sys_user(id)`，非空 | 项目负责人，引用现有用户 |
| `planned_start_date` | `date` | 可空 | 计划开始日期 |
| `planned_finish_date` | `date` | 可空 | 计划结束日期 |
| `status` | `varchar(32)` | 非空，枚举约束 | 项目状态 |
| `target_revenue` | `numeric(18,2)` | 非空，默认 0，非负 | 目标收入，不等同实际收入 |
| `target_cost` | `numeric(18,2)` | 非空，默认 0，非负 | 目标成本，不等同实际成本 |
| `remark` | `varchar(500)` | 可空 | 备注 |
| `created_by` | `varchar(64)` | 非空 | 创建人 |
| `created_at` | `timestamptz` | 非空 | 创建时间 |
| `updated_by` | `varchar(64)` | 非空 | 更新人 |
| `updated_at` | `timestamptz` | 非空 | 更新时间 |
| `activated_by` | `varchar(64)` | 可空 | 激活人 |
| `activated_at` | `timestamptz` | 可空 | 激活时间 |
| `closed_by` | `varchar(64)` | 可空 | 关闭人 |
| `closed_at` | `timestamptz` | 可空 | 关闭时间 |
| `closed_reason` | `varchar(200)` | 可空 | 关闭原因，关闭动作必填，长度 1-200 |
| `cancelled_by` | `varchar(64)` | 可空 | 取消人 |
| `cancelled_at` | `timestamptz` | 可空 | 取消时间 |
| `cancelled_reason` | `varchar(200)` | 可空 | 取消原因，取消动作必填，长度 1-200 |
| `version` | `bigint` | 非空，默认 0 | 并发版本 |

必须落库约束和索引：

- `uk_sal_project_no`：`project_no` 唯一。
- `ck_sal_project_status`：`DRAFT`、`ACTIVE`、`CLOSED`、`CANCELLED`。
- `ck_sal_project_target_revenue`：`target_revenue >= 0`。
- `ck_sal_project_target_cost`：`target_cost >= 0`。
- `ck_sal_project_plan_date_range`：计划结束日期为空，或计划开始日期为空，或计划结束日期不早于计划开始日期。
- `ck_sal_project_close_reason_len`：关闭原因为空，或长度 1-200。
- `ck_sal_project_cancel_reason_len`：取消原因为空，或长度 1-200。
- `idx_sal_project_customer`：`customer_id`。
- `idx_sal_project_owner`：`owner_user_id`。
- `idx_sal_project_status_updated`：`status, updated_at desc, id desc`。
- `idx_sal_project_plan_dates`：`planned_start_date, planned_finish_date`。

### `sal_project_contract`

销售项目合同。主合同和补充合同使用同一张表区分 `contract_type`。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `bigserial` | 主键 | 合同标识 |
| `contract_no` | `varchar(64)` | 唯一，非空 | 后端生成内部合同编号 |
| `external_contract_no` | `varchar(100)` | 可空，可重复 | 外部纸质合同号 |
| `project_id` | `bigint` | 外键 `sal_project(id)`，非空 | 所属项目 |
| `contract_type` | `varchar(32)` | 非空，枚举约束 | `MAIN` 或 `SUPPLEMENT` |
| `main_contract_id` | `bigint` | 外键 `sal_project_contract(id)`，可空 | 补充合同引用主合同 |
| `name` | `varchar(100)` | 非空 | 合同名称 |
| `signed_date` | `date` | 非空 | 签订日期 |
| `effective_start_date` | `date` | 可空 | 履约开始日期 |
| `effective_end_date` | `date` | 可空 | 履约结束日期 |
| `amount` | `numeric(18,2)` | 非空 | 主合同金额大于 0；补充合同调整金额允许正负但不得为 0 |
| `status` | `varchar(32)` | 非空，枚举约束 | 合同状态 |
| `remark` | `varchar(500)` | 可空 | 备注 |
| `created_by` | `varchar(64)` | 非空 | 创建人 |
| `created_at` | `timestamptz` | 非空 | 创建时间 |
| `updated_by` | `varchar(64)` | 非空 | 更新人 |
| `updated_at` | `timestamptz` | 非空 | 更新时间 |
| `activated_by` | `varchar(64)` | 可空 | 激活操作人 |
| `activated_at` | `timestamptz` | 可空 | 激活时间 |
| `closed_by` | `varchar(64)` | 可空 | 正常关闭人 |
| `closed_at` | `timestamptz` | 可空 | 正常关闭时间 |
| `closed_reason` | `varchar(200)` | 可空 | 关闭原因，关闭动作必填，长度 1-200 |
| `terminated_by` | `varchar(64)` | 可空 | 提前终止人 |
| `terminated_at` | `timestamptz` | 可空 | 提前终止时间 |
| `terminated_reason` | `varchar(200)` | 可空 | 终止原因，终止动作必填，长度 1-200 |
| `cancelled_by` | `varchar(64)` | 可空 | 取消人 |
| `cancelled_at` | `timestamptz` | 可空 | 取消时间 |
| `cancelled_reason` | `varchar(200)` | 可空 | 取消原因，取消动作必填，长度 1-200 |
| `version` | `bigint` | 非空，默认 0 | 并发版本 |

必须落库约束：

- `uk_sal_project_contract_no`：`contract_no` 唯一。
- `ck_sal_project_contract_type`：`MAIN`、`SUPPLEMENT`。
- `ck_sal_project_contract_status`：`DRAFT`、`EFFECTIVE`、`CLOSED`、`TERMINATED`、`CANCELLED`。
- `ck_sal_project_contract_amount`：主合同金额 `> 0`；补充合同金额 `<> 0`。
- `ck_sal_project_contract_main_ref`：主合同 `main_contract_id is null`；补充合同 `main_contract_id is not null`。
- `ck_sal_project_contract_date_range`：履约结束日期为空，或履约开始日期为空，或履约结束日期不早于履约开始日期。
- `ck_sal_project_contract_close_reason_len`：关闭原因为空，或长度 1-200。
- `ck_sal_project_contract_terminate_reason_len`：终止原因为空，或长度 1-200。
- `ck_sal_project_contract_cancel_reason_len`：取消原因为空，或长度 1-200。
- 部分唯一索引：同一项目最多一个 `contract_type = 'MAIN'` 且 `status <> 'CANCELLED'` 的主合同。

必须索引：

- `idx_sal_project_contract_project`：`project_id, status, id desc`。
- `idx_sal_project_contract_main`：`main_contract_id`。
- `idx_sal_project_contract_signed_date`：`signed_date desc, id desc`。
- `idx_sal_project_contract_external_no`：`external_contract_no` 普通索引，禁止唯一索引。

必须由服务层保证：

- 合同客户必须与项目客户一致。
- 主合同创建可在 `DRAFT` 项目下进行；`CLOSED`、`CANCELLED` 项目不得创建合同。
- 补充合同仅允许在项目 `ACTIVE` 且引用主合同为 `EFFECTIVE` 时创建。
- 合同激活时必须再次校验项目状态、客户、合同类型、金额、日期和主合同关系；补充合同引用主合同必须仍为同项目 `EFFECTIVE` 主合同。
- 合同日期允许超出项目计划周期，020 不做强制校验。

### 销售订单兼容字段

020 只改造销售订单，不改采购订单和生产工单。

必须在 `sal_sales_order` 增加：

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `project_id` | `bigint` | 可空，外键 `sal_project(id)` | 关联销售项目 |
| `contract_id` | `bigint` | 可空，外键 `sal_project_contract(id)` | 关联生效合同 |

兼容规则：

- 现有历史销售订单两个字段保持 `null`。
- `project_id` 和 `contract_id` 必须同时为空或同时有值。
- 不允许迁移脚本根据客户、日期或金额猜测历史项目归属。
- 销售订单创建、更新、确认时执行项目合同一致性校验。
- 销售订单列表筛选中，`projectLinked` 与 `projectId` 或 `contractId` 同时出现时返回 `VALIDATION_ERROR`。

必须落库约束和索引：

- `ck_sal_sales_order_project_pair`：`project_id` 和 `contract_id` 必须同时为空或同时有值。
- `idx_sal_sales_order_project`：`project_id, status, order_date desc, id desc`。
- `idx_sal_sales_order_contract`：`contract_id`。

## 状态与枚举

### 项目状态

| 值 | 说明 |
|---|---|
| `DRAFT` | 草稿，可编辑，可维护合同，可取消 |
| `ACTIVE` | 已激活，可关联销售订单，可维护补充合同，可更新负责人、计划日期、目标收入、目标成本和备注 |
| `CLOSED` | 已关闭，不允许新增合同或关联销售订单 |
| `CANCELLED` | 已取消，不允许后续业务 |

状态动作规则：

- 创建项目后状态为 `DRAFT`。
- `DRAFT -> ACTIVE`：项目必须存在已生效主合同。
- `ACTIVE -> CLOSED`：项目下合同均为终态，关联销售订单均为 `SHIPPED`、`CLOSED`、`CANCELLED`。
- `DRAFT -> CANCELLED`：仅限未发生有效合同和销售订单的项目。
- 项目关闭、项目取消必须填写 `reason`，长度 1-200。
- `CLOSED`、`CANCELLED` 为终态。

项目终态定义：

- 项目终态：`CLOSED`、`CANCELLED`。
- 合同终态：`CLOSED`、`TERMINATED`、`CANCELLED`。
- 销售订单终态沿用销售管理：`SHIPPED`、`CLOSED`、`CANCELLED`。

### 合同类型

| 值 | 说明 |
|---|---|
| `MAIN` | 主合同，同一项目最多一个非取消主合同 |
| `SUPPLEMENT` | 补充合同，必须引用同项目主合同 |

### 合同状态

| 值 | 说明 |
|---|---|
| `DRAFT` | 草稿，可编辑、可取消、可生效 |
| `EFFECTIVE` | 已生效，关键商业字段不可直接覆盖 |
| `CLOSED` | 正常履约结束，终态 |
| `TERMINATED` | 提前终止，终态 |
| `CANCELLED` | 草稿取消，终态 |

状态动作规则：

- 创建合同后状态为 `DRAFT`。
- `DRAFT -> EFFECTIVE`：通过 `activate` 动作进入 `EFFECTIVE`，激活前重新校验项目、客户、合同类型、金额、日期和主合同关系。
- `DRAFT -> CANCELLED`：草稿可取消，必须填写取消原因。
- `EFFECTIVE -> CLOSED`：正常履约结束，必须填写关闭原因。
- `EFFECTIVE -> TERMINATED`：提前终止，必须填写终止原因。
- 合同关闭、终止、取消原因长度 1-200。
- 生效合同不得通过更新接口修改 `contractType`、`projectId`、`mainContractId`、`amount`、`signedDate`、`effectiveStartDate`、`effectiveEndDate` 等关键商业字段。
- 已生效合同的商业增减通过新增补充合同表达。

## 字段精度

| 字段类别 | 数据库类型 | 接口建议 | 说明 |
|---|---|---|---|
| 合同金额 | `numeric(18,2)` | 字符串或定点数值 | 商业金额，不拆税、不入账 |
| 目标收入 | `numeric(18,2)` | 字符串或定点数值 | 非负管理目标 |
| 目标成本 | `numeric(18,2)` | 字符串或定点数值 | 非负管理目标 |
| 销售订单数量 | 沿用 `numeric(18,6)` | 沿用销售接口 | 020 不改变数量精度 |
| 销售单价 | 沿用 `numeric(18,6)` | 沿用销售接口 | 020 不改变销售金额计算口径 |

金额校验：

- `targetRevenue >= 0`。
- `targetCost >= 0`。
- 主合同 `amount > 0`。
- 补充合同 `amount <> 0`，允许负数表达商业调减。
- 金额整数位不得超过 16 位，小数位不得超过 2 位。

## 接口分组

| 资源 | 路径 | 能力 |
|---|---|---|
| 销售项目 | `/api/admin/sales-projects` | list/get/create/update/activate/close/cancel |
| 项目负责人候选 | `/api/admin/sales-projects/owner-candidates` | list |
| 销售订单项目合同候选 | `/api/admin/sales-projects/order-link-candidates` | list |
| 项目合同集合 | `/api/admin/sales-projects/{projectId}/contracts` | list/create |
| 项目关联销售订单 | `/api/admin/sales-projects/{projectId}/sales-orders` | list |
| 销售项目合同 | `/api/admin/sales-project-contracts/{id}` | get/update/activate/close/terminate/cancel |
| 销售订单 | `/api/admin/sales/orders` | 增加可选项目合同关联字段和筛选 |

## 项目字段

### 项目创建请求字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | string | 是 | 项目名称，最长 100 |
| `customerId` | number | 是 | 客户，必须存在且启用 |
| `ownerUserId` | number | 是 | 项目负责人，必须引用启用用户 |
| `plannedStartDate` | string | 否 | 计划开始日期 |
| `plannedFinishDate` | string | 否 | 计划结束日期，不得早于开始日期 |
| `targetRevenue` | string | 否 | 目标收入，默认 0，非负 |
| `targetCost` | string | 否 | 目标成本，默认 0，非负 |
| `remark` | string | 否 | 备注，最长 500 |

### 项目更新请求字段

更新请求必须与创建请求拆分，且显式携带 `version`。

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `version` | number | 是 | 当前项目版本 |
| `name` | string | 条件允许 | 仅 `DRAFT` 项目可更新，最长 100 |
| `ownerUserId` | number | 否 | 项目负责人，必须引用启用用户；`DRAFT`、`ACTIVE` 可更新 |
| `plannedStartDate` | string | 否 | 计划开始日期；`DRAFT`、`ACTIVE` 可更新 |
| `plannedFinishDate` | string | 否 | 计划结束日期，不得早于开始日期；`DRAFT`、`ACTIVE` 可更新 |
| `targetRevenue` | string | 否 | 目标收入，非负；`DRAFT`、`ACTIVE` 可更新 |
| `targetCost` | string | 否 | 目标成本，非负；`DRAFT`、`ACTIVE` 可更新 |
| `remark` | string | 否 | 备注，最长 500；`DRAFT`、`ACTIVE` 可更新 |

`projectNo`、`customerId` 均不可通过更新接口修改；`CLOSED`、`CANCELLED` 项目不可更新。

### 项目汇总响应字段

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 项目标识 |
| `projectNo` | string | 是 | 后端生成项目编号 |
| `name` | string | 是 | 项目名称 |
| `customerId` | number | 是 | 客户标识 |
| `customerCode` | string | 是 | 客户编码 |
| `customerName` | string | 是 | 客户名称 |
| `ownerUserId` | number | 是 | 负责人用户标识 |
| `ownerUsername` | string | 是 | 负责人用户名 |
| `ownerDisplayName` | string | 是 | 负责人显示名 |
| `plannedStartDate` | string | 否 | 计划开始日期 |
| `plannedFinishDate` | string | 否 | 计划结束日期 |
| `status` | string | 是 | 项目状态 |
| `targetRevenue` | string | 是 | 目标收入 |
| `targetCost` | string | 是 | 目标成本 |
| `contractSummaryRestricted` | boolean | 是 | 无 `sales:contract:view` 时为 `true` |
| `mainContractId` | number | 否 | 有合同权限时返回当前非取消主合同标识；受限时为 `null` |
| `mainContractNo` | string | 否 | 有合同权限时返回当前非取消主合同编号；受限时为 `null` |
| `mainContractStatus` | string | 否 | 有合同权限时返回当前非取消主合同状态；受限时为 `null` |
| `effectiveContractAmount` | string | 否 | 有合同权限时返回已生效合同金额汇总，主合同加补充合同；受限时为 `null` |
| `contractCount` | number | 否 | 有合同权限时返回合同数量；受限时为 `null` |
| `supplementContractCount` | number | 否 | 有合同权限时返回补充合同数量；受限时为 `null` |
| `salesOrderCount` | number | 否 | 有 `sales:order:view` 时返回关联销售订单数量；无权限返回 `null` |
| `salesOrderSummaryRestricted` | boolean | 是 | 无 `sales:order:view` 时为 `true` |
| `remark` | string | 否 | 备注 |
| `createdByName` | string | 是 | 创建人 |
| `createdAt` | string | 是 | 创建时间 |
| `updatedAt` | string | 是 | 更新时间 |
| `version` | number | 是 | 并发版本 |

### 项目详情响应字段

详情响应在项目汇总字段基础上增加：

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| `contracts` | array | 是 | 有 `sales:contract:view` 时返回合同摘要；无权限返回空数组 |
| `salesOrderSummary` | object/null | 是 | 有 `sales:order:view` 时返回关联订单摘要；无权限返回 `null` |
| `operations` | array | 是 | 项目、合同和销售订单关联相关审计操作摘要 |
| `activatedByName` | string | 否 | 激活人 |
| `activatedAt` | string | 否 | 激活时间 |
| `closedByName` | string | 否 | 关闭人 |
| `closedAt` | string | 否 | 关闭时间 |
| `closedReason` | string | 否 | 关闭原因 |
| `cancelledByName` | string | 否 | 取消人 |
| `cancelledAt` | string | 否 | 取消时间 |
| `cancelledReason` | string | 否 | 取消原因 |

## 合同字段

### 合同创建请求字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `contractType` | string | 是 | `MAIN` 或 `SUPPLEMENT` |
| `mainContractId` | number | 条件必填 | 补充合同必填，主合同必须为空 |
| `externalContractNo` | string | 否 | 外部纸质合同号，最长 100 |
| `name` | string | 是 | 合同名称，最长 100 |
| `signedDate` | string | 是 | 签订日期 |
| `effectiveStartDate` | string | 否 | 履约开始日期 |
| `effectiveEndDate` | string | 否 | 履约结束日期，不得早于开始日期 |
| `amount` | string | 是 | 主合同大于 0；补充合同不等于 0 |
| `remark` | string | 否 | 备注，最长 500 |

### 合同更新请求字段

更新请求必须与创建请求拆分，且显式携带 `version`。仅 `DRAFT` 合同可更新。

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `version` | number | 是 | 当前合同版本 |
| `externalContractNo` | string | 否 | 外部纸质合同号，最长 100，可空、可重复 |
| `name` | string | 否 | 合同名称，最长 100 |
| `signedDate` | string | 否 | 签订日期 |
| `effectiveStartDate` | string | 否 | 履约开始日期，可超出项目计划周期 |
| `effectiveEndDate` | string | 否 | 履约结束日期，不得早于开始日期，可超出项目计划周期 |
| `amount` | string | 否 | 主合同大于 0；补充合同不等于 0 |
| `remark` | string | 否 | 备注，最长 500 |

`contractType`、`projectId`、`mainContractId` 和内部合同编号不可通过更新接口修改。

### 合同摘要响应字段

摘要用于合同列表、项目详情合同区和状态动作后的最小展示，必须包含状态动作所需 `version`。

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 合同标识 |
| `contractNo` | string | 是 | 后端生成内部合同编号 |
| `externalContractNo` | string | 否 | 外部纸质合同号 |
| `projectId` | number | 是 | 所属项目 |
| `contractType` | string | 是 | 合同类型 |
| `mainContractId` | number | 否 | 补充合同引用的主合同 |
| `mainContractNo` | string | 否 | 补充合同引用的主合同编号 |
| `name` | string | 是 | 合同名称 |
| `signedDate` | string | 是 | 签订日期 |
| `effectiveStartDate` | string | 否 | 履约开始日期 |
| `effectiveEndDate` | string | 否 | 履约结束日期 |
| `amount` | string | 是 | 合同金额或补充调整金额 |
| `status` | string | 是 | 合同状态 |
| `updatedAt` | string | 是 | 更新时间 |
| `version` | number | 是 | 并发版本，供状态动作使用 |

### 合同详情响应字段

详情响应在合同摘要字段基础上增加：

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| `projectNo` | string | 是 | 项目编号 |
| `projectName` | string | 是 | 项目名称 |
| `remark` | string | 否 | 备注 |
| `createdByName` | string | 是 | 创建人 |
| `createdAt` | string | 是 | 创建时间 |
| `activatedByName` | string | 否 | 激活人 |
| `activatedAt` | string | 否 | 激活时间 |
| `closedByName` | string | 否 | 关闭人 |
| `closedAt` | string | 否 | 关闭时间 |
| `closedReason` | string | 否 | 关闭原因 |
| `terminatedByName` | string | 否 | 终止人 |
| `terminatedAt` | string | 否 | 终止时间 |
| `terminatedReason` | string | 否 | 终止原因 |
| `cancelledByName` | string | 否 | 取消人 |
| `cancelledAt` | string | 否 | 取消时间 |
| `cancelledReason` | string | 否 | 取消原因 |

## 状态动作请求字段

项目和合同状态动作统一使用以下请求体，所有状态动作必须显式携带 `version`。项目关闭、项目取消、合同关闭、合同终止、合同取消必须填写 `reason`，长度 1-200；项目激活、合同激活可省略 `reason`。

```json
{
  "version": 3,
  "reason": "关闭或终止原因"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `version` | number | 是 | 客户端看到的当前版本 |
| `reason` | string | 条件必填 | 项目关闭、项目取消、合同关闭、合同终止、合同取消必填，长度 1-200 |

并发规则：

- 更新接口和状态动作必须传入 `version`。
- 后端写入时按 `id + version` 或锁定后校验版本执行。
- 版本不一致返回并发错误，不得覆盖其他人的修改。
- 写入成功后 `version = version + 1`。

## 项目接口

### 项目负责人候选

- 方法：`GET`
- 路径：`/api/admin/sales-projects/owner-candidates`
- 权限：`sales:project:view`、`sales:project:create` 或 `sales:project:update`
- 查询参数：
  - `page`、`pageSize`。
  - `keyword`：用户名或显示名。
- 成功响应：`PageResponse<ProjectOwnerCandidate>`。
- 最小响应字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `userId` | number | 用户标识 |
| `username` | string | 用户名 |
| `displayName` | string | 显示名 |

候选仅返回启用用户，不要求调用方具备系统用户列表查看权限。

### 销售订单项目合同候选

- 方法：`GET`
- 路径：`/api/admin/sales-projects/order-link-candidates`
- 权限：销售订单创建场景需要 `sales:order:create`；销售订单更新场景需要 `sales:order:update`。
- 查询参数：
  - `page`、`pageSize`。
  - `customerId`：客户标识，建议销售订单表单选择客户后传入。
  - `keyword`：项目编号、项目名称、内部合同编号、外部纸质合同号或合同名称。
- 成功响应：`PageResponse<SalesOrderProjectContractCandidate>`。
- 最小响应字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `projectId` | number | 项目标识 |
| `projectNo` | string | 项目编号 |
| `projectName` | string | 项目名称 |
| `customerId` | number | 客户标识 |
| `customerName` | string | 客户名称 |
| `contractId` | number | 合同标识 |
| `contractNo` | string | 内部合同编号 |
| `externalContractNo` | string | 外部纸质合同号 |
| `contractName` | string | 合同名称 |
| `contractType` | string | 合同类型 |

候选接口只返回项目 `ACTIVE` 且合同 `EFFECTIVE` 的可关联组合，不返回合同金额、状态历史或完整合同详情，不要求调用方具备完整项目或合同查看权限。

### 项目分页列表

- 方法：`GET`
- 路径：`/api/admin/sales-projects`
- 权限：`sales:project:view`
- 查询参数：
  - `page`、`pageSize`。
  - `keyword`：项目编号、项目名称、客户编码、客户名称、负责人用户名或负责人显示名。
  - `customerId`：客户标识。
  - `ownerUserId`：项目负责人。
  - `status`：项目状态。
  - `plannedStartFrom`、`plannedStartTo`：计划开始日期范围。
  - `plannedFinishFrom`、`plannedFinishTo`：计划结束日期范围。
- 排序：`updatedAt desc, id desc`。
- 成功响应：`PageResponse<SalesProjectSummary>`。

### 项目详情

- 方法：`GET`
- 路径：`/api/admin/sales-projects/{id}`
- 权限：`sales:project:view`
- 成功响应：`SalesProjectDetail`。
- 规则：
  - 合同摘要受 `sales:contract:view` 约束；无权限时 `contracts` 返回空数组，所有合同编号、金额、状态和数量字段返回 `null`，`contractSummaryRestricted = true`。
  - 关联销售订单摘要受 `sales:order:view` 约束；当前用户没有该权限时，`salesOrderCount` 和 `salesOrderSummary` 返回 `null`，`salesOrderSummaryRestricted = true`。
- 不存在返回：`PROJECT_NOT_FOUND`。

### 创建项目

- 方法：`POST`
- 路径：`/api/admin/sales-projects`
- 权限：`sales:project:create`
- 请求体：`SalesProjectCreatePayload`。
- 规则：
  - 项目编号由后端生成。
  - 新建状态固定为 `DRAFT`。
  - 客户必须存在且启用。
  - 项目负责人必须引用现有启用用户。
  - 目标收入、目标成本必须非负。
  - 计划结束日期不得早于计划开始日期。
- 成功响应：`SalesProjectDetail`。

### 更新项目

- 方法：`PUT`
- 路径：`/api/admin/sales-projects/{id}`
- 权限：`sales:project:update`
- 请求体：`SalesProjectUpdatePayload`。
- 规则：
  - `DRAFT` 项目可更新项目名称、负责人、计划日期、目标收入、目标成本和备注。
  - `ACTIVE` 项目仅可更新负责人、计划日期、目标收入、目标成本和备注。
  - `CLOSED`、`CANCELLED` 项目不可更新。
  - 项目编号、客户不可修改。
  - 更新必须写入审计摘要，摘要至少包含变更字段名。
- 成功响应：`SalesProjectDetail`。

### 激活项目

- 方法：`PUT`
- 路径：`/api/admin/sales-projects/{id}/activate`
- 权限：`sales:project:activate`
- 请求体：状态动作请求。
- 规则：
  - 仅 `DRAFT` 项目可激活。
  - 项目必须存在已生效主合同。
  - 激活后销售订单才允许关联该项目。
- 成功响应：`SalesProjectDetail`。

### 关闭项目

- 方法：`PUT`
- 路径：`/api/admin/sales-projects/{id}/close`
- 权限：`sales:project:close`
- 请求体：状态动作请求，`reason` 必填。
- 规则：
  - 仅 `ACTIVE` 项目可关闭。
  - 项目下所有合同必须为终态。
  - 项目关联销售订单必须均为 `SHIPPED`、`CLOSED`、`CANCELLED`；`DRAFT`、`CONFIRMED`、`PARTIALLY_SHIPPED` 等其他状态均阻止关闭。
  - 关闭后不允许新增合同或新增销售订单关联。
- 成功响应：`SalesProjectDetail`。

### 取消项目

- 方法：`PUT`
- 路径：`/api/admin/sales-projects/{id}/cancel`
- 权限：`sales:project:cancel`
- 请求体：状态动作请求，`reason` 必填。
- 规则：
  - 仅 `DRAFT` 项目可取消。
  - 项目不得存在已生效、已关闭或已终止合同。
  - 项目不得存在关联销售订单。
  - 取消后不允许恢复。
- 成功响应：`SalesProjectDetail`。

## 合同接口

### 项目合同分页列表

- 方法：`GET`
- 路径：`/api/admin/sales-projects/{projectId}/contracts`
- 权限：`sales:contract:view`
- 查询参数：
  - `page`、`pageSize`。
  - `keyword`：内部合同编号、外部纸质合同号、合同名称。
  - `contractType`：合同类型。
  - `status`：合同状态。
  - `signedDateFrom`、`signedDateTo`：签订日期范围。
- 排序：主合同优先，其次 `createdAt desc, id desc`。
- 成功响应：`PageResponse<SalesProjectContractSummary>`。

### 创建合同

- 方法：`POST`
- 路径：`/api/admin/sales-projects/{projectId}/contracts`
- 权限：`sales:contract:create`
- 请求体：`SalesProjectContractCreatePayload`。
- 规则：
  - 合同编号由后端生成。
  - 新建状态固定为 `DRAFT`。
  - 主合同可在 `DRAFT` 项目下创建；`CLOSED`、`CANCELLED` 项目不得创建合同。
  - 补充合同仅允许在项目 `ACTIVE` 且引用主合同为 `EFFECTIVE` 时创建。
  - 同一项目最多一个非取消主合同。
  - 主合同 `mainContractId` 必须为空，金额必须大于 0。
  - 补充合同必须引用同项目主合同，金额允许正负但不得为 0。
  - 履约结束日期不得早于履约开始日期。
  - 合同日期允许超出项目计划周期。
- 成功响应：`SalesProjectContractDetail`。

### 合同详情

- 方法：`GET`
- 路径：`/api/admin/sales-project-contracts/{id}`
- 权限：`sales:contract:view`
- 成功响应：`SalesProjectContractDetail`。
- 不存在返回：`CONTRACT_NOT_FOUND`。

### 更新合同

- 方法：`PUT`
- 路径：`/api/admin/sales-project-contracts/{id}`
- 权限：`sales:contract:update`
- 请求体：`SalesProjectContractUpdatePayload`。
- 规则：
  - 仅 `DRAFT` 合同可更新关键商业字段。
  - `EFFECTIVE`、`CLOSED`、`TERMINATED`、`CANCELLED` 合同不可通过更新接口覆盖关键商业字段。
  - 内部合同编号不可修改。
  - 合同所属项目不可修改。
- 成功响应：`SalesProjectContractDetail`。

### 合同激活

- 方法：`PUT`
- 路径：`/api/admin/sales-project-contracts/{id}/activate`
- 权限：`sales:contract:activate`
- 请求体：状态动作请求。
- 规则：
  - 仅 `DRAFT` 合同可激活为 `EFFECTIVE`。
  - 激活前重新校验项目状态、客户、合同类型、金额、日期和主合同关系。
  - 主合同激活后可用于项目激活。
  - 补充合同激活时，其项目必须仍为 `ACTIVE`，引用主合同必须仍属于同一项目且状态为 `EFFECTIVE`。
- 成功响应：`SalesProjectContractDetail`。

### 合同关闭

- 方法：`PUT`
- 路径：`/api/admin/sales-project-contracts/{id}/close`
- 权限：`sales:contract:close`
- 请求体：状态动作请求，`reason` 必填。
- 规则：
  - 仅 `EFFECTIVE` 合同可关闭。
  - 关闭表示正常结束，合同进入终态。
- 成功响应：`SalesProjectContractDetail`。

### 合同终止

- 方法：`PUT`
- 路径：`/api/admin/sales-project-contracts/{id}/terminate`
- 权限：`sales:contract:terminate`
- 请求体：状态动作请求，`reason` 必填。
- 规则：
  - 仅 `EFFECTIVE` 合同可终止。
  - 终止表示提前结束，必须填写终止原因。
  - 终止不删除合同，不回滚历史关联。
- 成功响应：`SalesProjectContractDetail`。

### 合同取消

- 方法：`PUT`
- 路径：`/api/admin/sales-project-contracts/{id}/cancel`
- 权限：`sales:contract:cancel`
- 请求体：状态动作请求，`reason` 必填。
- 规则：
  - 仅 `DRAFT` 合同可取消。
  - 主合同存在非取消补充合同时不得取消。
  - 已被销售订单引用的合同不得取消。
- 成功响应：`SalesProjectContractDetail`。

## 关联销售订单接口

### 项目关联销售订单列表

- 方法：`GET`
- 路径：`/api/admin/sales-projects/{projectId}/sales-orders`
- 权限：`sales:project:view`，服务层必须同时校验 `sales:order:view`。
- 查询参数：
  - `page`、`pageSize`。
  - `keyword`：销售订单号、物料编码、物料名称。
  - `contractId`：合同标识。
  - `status`：销售订单状态。
  - `dateFrom`、`dateTo`：订单日期范围。
- 成功响应：`PageResponse<ProjectSalesOrderSummary>`。
- 无 `sales:order:view` 返回：`AUTH_FORBIDDEN`，不得返回销售订单行、金额或订单号。
- 有 `sales:order:view` 但无 `sales:contract:view` 时仍返回允许查看的订单字段，但行内
  `contractId`、`contractNo`、`externalContractNo` 必须为 `null`，不得泄露真实合同标识或编号。

### 关联销售订单摘要字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `orderCount` | number | 关联订单数量 |
| `draftCount` | number | 草稿数量 |
| `confirmedCount` | number | 已确认数量 |
| `partiallyShippedCount` | number | 部分出库数量 |
| `shippedCount` | number | 全部出库数量 |
| `closedCount` | number | 已关闭数量 |
| `cancelledCount` | number | 已取消数量 |
| `businessAmount` | string | 销售订单明细 `quantity * unitPrice` 汇总，仅为销售业务金额 |
| `latestOrderDate` | string | 最近订单日期 |

`businessAmount` 不等同收入确认、开票金额、回款金额、项目成本或项目利润。

### 关联销售订单行字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 销售订单标识 |
| `orderNo` | string | 销售订单号 |
| `projectId` | number | 项目标识 |
| `projectNo` | string | 项目编号 |
| `projectName` | string | 项目名称 |
| `contractId` | number \| null | 合同标识；无 `sales:contract:view` 时为 `null` |
| `contractNo` | string \| null | 内部合同编号；无 `sales:contract:view` 时为 `null` |
| `externalContractNo` | string \| null | 外部纸质合同号；无 `sales:contract:view` 时为 `null` |
| `customerId` | number | 客户标识 |
| `customerName` | string | 客户名称 |
| `orderDate` | string | 订单日期 |
| `expectedShipDate` | string | 预计出库日期 |
| `status` | string | 销售订单状态 |
| `lineCount` | number | 明细行数 |
| `totalQuantity` | string | 销售数量汇总 |
| `businessAmount` | string | 销售业务金额 |
| `createdAt` | string | 创建时间 |
| `updatedAt` | string | 更新时间 |

## 销售订单接口增量

020 只在现有销售订单接口中增加项目合同关联字段，不改变销售出库、库存预留、库存扣减和来源追溯语义。

### 销售订单请求增量

适用于：

- `POST /api/admin/sales/orders`
- `PUT /api/admin/sales/orders/{id}`

新增字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `projectId` | number | 条件必填 | 与 `contractId` 必须同时为空或同时有值 |
| `contractId` | number | 条件必填 | 与 `projectId` 必须同时为空或同时有值 |

规则：

- 仅销售订单草稿创建、草稿更新允许关联或解除项目合同。
- `projectId` 和 `contractId` 同时为空时表示不关联项目，兼容历史订单。
- `projectId` 和 `contractId` 同时有值时：
  - 项目必须存在且状态为 `ACTIVE`。
  - 合同必须存在且状态为 `EFFECTIVE`。
  - 合同必须属于该项目。
  - 销售订单客户必须与项目客户一致。
- 销售订单确认时必须再次校验项目和合同状态、客户一致性及归属关系。
- 已确认或已发生出库的销售订单不得新增、修改或解除项目合同关联。

### 销售订单响应增量

适用于销售订单列表、详情和项目关联订单列表。

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| `projectId` | number | 否 | 历史订单可为空 |
| `projectNo` | string | 否 | 项目编号 |
| `projectName` | string | 否 | 项目名称 |
| `contractId` | number | 否 | 历史订单可为空 |
| `contractNo` | string | 否 | 内部合同编号 |
| `externalContractNo` | string | 否 | 外部纸质合同号 |

### 销售订单列表筛选增量

`GET /api/admin/sales/orders` 增加查询参数：

| 参数 | 类型 | 说明 |
|---|---|---|
| `projectId` | number | 按销售项目筛选 |
| `contractId` | number | 按合同筛选 |
| `projectLinked` | boolean | `true` 仅查有关联项目订单；`false` 仅查历史未关联订单 |

筛选规则：

- `projectLinked` 与 `projectId` 或 `contractId` 不得同时出现；同时出现返回 `VALIDATION_ERROR`。
- `projectId` 与 `contractId` 可同时出现，用于查询指定项目下指定合同订单；只传其中一个时按单字段筛选。

## 权限编码

必须在现有 `sales` 菜单下增加销售项目菜单和动作权限。

| 权限编码 | 类型 | 说明 |
|---|---|---|
| `sales:project` | 菜单 | 销售项目 |
| `sales:project:view` | 操作 | 查看销售项目 |
| `sales:project:create` | 操作 | 创建销售项目 |
| `sales:project:update` | 操作 | 更新销售项目 |
| `sales:project:activate` | 操作 | 激活销售项目 |
| `sales:project:close` | 操作 | 关闭销售项目 |
| `sales:project:cancel` | 操作 | 取消销售项目 |
| `sales:contract:view` | 操作 | 查看项目合同 |
| `sales:contract:create` | 操作 | 创建项目合同 |
| `sales:contract:update` | 操作 | 更新项目合同草稿 |
| `sales:contract:activate` | 操作 | 激活项目合同，使状态进入 `EFFECTIVE` |
| `sales:contract:close` | 操作 | 关闭项目合同 |
| `sales:contract:terminate` | 操作 | 终止项目合同 |
| `sales:contract:cancel` | 操作 | 取消项目合同草稿 |
| `sales:order:view` | 操作 | 查看关联销售订单摘要和列表 |
| `sales:order:create` | 操作 | 创建带项目合同关联的销售订单 |
| `sales:order:update` | 操作 | 更新或解除草稿销售订单项目合同关联 |
| `sales:order:confirm` | 操作 | 确认时重新校验项目合同关联 |

权限实现要求：

- `AccountPermissionInitializer` 必须初始化新权限并分配给系统管理员。
- `PermissionAuthorizationManager` 必须增加 `/api/admin/sales-projects/**` 和 `/api/admin/sales-project-contracts/**` 的路径映射。
- 项目列表和详情必须在服务层检查 `sales:contract:view`，无权限时不得返回合同编号、金额、状态或数量，并设置 `contractSummaryRestricted = true`。
- 项目详情中的销售订单摘要必须在服务层检查 `sales:order:view`，无权限时 `salesOrderCount`、订单摘要和订单列表为空或 `null`，不得返回订单号、金额、行明细或状态分布，并设置 `salesOrderSummaryRestricted = true`。
- 项目负责人候选接口只要求 `sales:project:view`、`sales:project:create` 或 `sales:project:update` 任一权限。
- 销售订单项目合同候选接口只要求对应销售订单创建或更新权限，不要求完整项目或合同查看权限。

## 审计动作

所有写入和状态动作必须写入 `sys_audit_log`。020 只保证 `action`、目标、操作人、时间和 `target_summary` 中的 reason 或变更字段摘要，不要求结构化 before/after，不扩展审计表。

| 动作 | `action` | `target_type` | `target_id` | `target_summary` |
|---|---|---|---|---|
| 创建项目 | `SALES_PROJECT_CREATE` | `SALES_PROJECT` | 项目 ID | 项目编号 |
| 更新项目 | `SALES_PROJECT_UPDATE` | `SALES_PROJECT` | 项目 ID | 项目编号 |
| 激活项目 | `SALES_PROJECT_ACTIVATE` | `SALES_PROJECT` | 项目 ID | 项目编号 |
| 关闭项目 | `SALES_PROJECT_CLOSE` | `SALES_PROJECT` | 项目 ID | 项目编号 |
| 取消项目 | `SALES_PROJECT_CANCEL` | `SALES_PROJECT` | 项目 ID | 项目编号 |
| 创建合同 | `SALES_PROJECT_CONTRACT_CREATE` | `SALES_PROJECT_CONTRACT` | 合同 ID | 内部合同编号 |
| 更新合同 | `SALES_PROJECT_CONTRACT_UPDATE` | `SALES_PROJECT_CONTRACT` | 合同 ID | 内部合同编号 |
| 激活合同 | `SALES_PROJECT_CONTRACT_ACTIVATE` | `SALES_PROJECT_CONTRACT` | 合同 ID | 内部合同编号 |
| 关闭合同 | `SALES_PROJECT_CONTRACT_CLOSE` | `SALES_PROJECT_CONTRACT` | 合同 ID | 内部合同编号 |
| 终止合同 | `SALES_PROJECT_CONTRACT_TERMINATE` | `SALES_PROJECT_CONTRACT` | 合同 ID | 内部合同编号 |
| 取消合同 | `SALES_PROJECT_CONTRACT_CANCEL` | `SALES_PROJECT_CONTRACT` | 合同 ID | 内部合同编号 |
| 销售订单关联项目 | `SALES_ORDER_PROJECT_LINK` | `SALES_PROJECT` | 项目 ID | 销售订单号及旧/新项目合同关联 |
| 销售订单解除项目 | `SALES_ORDER_PROJECT_UNLINK` | `SALES_PROJECT` | 项目 ID | 销售订单号及旧/新项目合同关联 |

销售订单从一个项目合同切换到另一个项目合同时，旧项目和新项目各写一条 `target_type = SALES_PROJECT` 的审计记录，确保两个项目详情的 `operations` 均可查询。

项目详情操作记录：

- `operations` 从 `sys_audit_log` 读取本项目 `target_type = 'SALES_PROJECT'` 的记录，以及项目下合同 `target_type = 'SALES_PROJECT_CONTRACT'` 的记录。
- 至少返回 `action`、`targetType`、`targetId`、`targetSummary`、`operatorUsername`、`createdAt`。
- `targetSummary` 必须包含状态动作原因或更新字段摘要；销售订单关联/解除摘要必须包含订单号及旧/新项目合同关联。
- 操作记录用于查看，不替代业务变更表；020 不建立独立合同变更业务表。

## 错误码

| 错误码 | HTTP | 说明 |
|---|---|---|
| `PROJECT_NOT_FOUND` | 404 | 销售项目不存在 |
| `PROJECT_NO_EXISTS` | 409 | 项目编号重复 |
| `PROJECT_STATUS_INVALID` | 409 | 项目状态不允许当前操作 |
| `PROJECT_CUSTOMER_INVALID` | 400 | 项目客户不存在或已停用 |
| `PROJECT_CUSTOMER_IMMUTABLE` | 409 | 项目客户创建后不可修改 |
| `PROJECT_OWNER_INVALID` | 400 | 项目负责人不存在或已停用 |
| `PROJECT_DATE_RANGE_INVALID` | 400 | 项目计划日期范围不合法 |
| `PROJECT_AMOUNT_INVALID` | 400 | 目标收入或目标成本不合法 |
| `PROJECT_REASON_REQUIRED` | 400 | 项目状态动作原因必填，长度 1-200 |
| `PROJECT_MAIN_CONTRACT_REQUIRED` | 409 | 项目激活缺少已生效主合同 |
| `PROJECT_HAS_EFFECTIVE_BUSINESS` | 409 | 项目已有有效合同或销售订单，不能取消 |
| `PROJECT_HAS_OPEN_BUSINESS` | 409 | 项目仍有关联合同或销售订单未终态，不能关闭 |
| `PROJECT_CONCURRENT_MODIFICATION` | 409 | 项目并发更新冲突 |
| `CONTRACT_NOT_FOUND` | 404 | 合同不存在 |
| `CONTRACT_NO_EXISTS` | 409 | 内部合同编号重复 |
| `CONTRACT_MAIN_EXISTS` | 409 | 同一项目已存在非取消主合同 |
| `CONTRACT_STATUS_INVALID` | 409 | 合同状态不允许当前操作 |
| `CONTRACT_PROJECT_MISMATCH` | 409 | 合同与项目不匹配 |
| `CONTRACT_PROJECT_NOT_ACTIVE` | 409 | 补充合同创建或激活时项目不是 `ACTIVE` |
| `CONTRACT_MAIN_REQUIRED` | 400 | 补充合同缺少主合同 |
| `CONTRACT_MAIN_INVALID` | 409 | 补充合同引用的主合同不合法 |
| `CONTRACT_MAIN_NOT_EFFECTIVE` | 409 | 补充合同创建或激活时主合同不是 `EFFECTIVE` |
| `CONTRACT_AMOUNT_INVALID` | 400 | 合同金额不合法 |
| `CONTRACT_DATE_RANGE_INVALID` | 400 | 合同日期范围不合法 |
| `CONTRACT_REASON_REQUIRED` | 400 | 合同状态动作原因必填，长度 1-200 |
| `CONTRACT_REFERENCED_BY_ORDER` | 409 | 合同已被销售订单引用，不允许取消 |
| `CONTRACT_CONCURRENT_MODIFICATION` | 409 | 合同并发更新冲突 |
| `SALES_ORDER_PROJECT_PAIR_REQUIRED` | 400 | 销售订单项目与合同必须同时为空或同时有值 |
| `SALES_ORDER_PROJECT_INVALID` | 409 | 销售订单关联项目不合法 |
| `SALES_ORDER_CONTRACT_INVALID` | 409 | 销售订单关联合同不合法 |
| `SALES_ORDER_PROJECT_CUSTOMER_MISMATCH` | 409 | 销售订单客户与项目客户不一致 |
| `SALES_ORDER_PROJECT_IMMUTABLE` | 409 | 非草稿销售订单不可修改项目合同关联 |

通用错误码继续沿用：

- `VALIDATION_ERROR`
- `AUTH_UNAUTHORIZED`
- `AUTH_FORBIDDEN`
- `CONFLICT`
- `SYSTEM_ERROR`
- 现有销售订单错误码，如 `SALES_ORDER_NOT_FOUND`、`SALES_ORDER_STATUS_INVALID`

## 事务和一致性

项目写入：

- 创建、更新、激活、关闭、取消必须在单个事务内完成。
- 更新和状态动作必须校验 `version`。
- 激活时必须锁定项目并确认存在已生效主合同。
- 关闭时必须锁定项目，并检查合同和销售订单终态；销售订单终态固定为 `SHIPPED`、`CLOSED`、`CANCELLED`。
- 取消时必须锁定项目，并检查不存在有效合同和销售订单。
- 更新 `ACTIVE` 项目时只允许变更负责人、计划日期、目标收入、目标成本和备注，并写入变更字段审计摘要。

合同写入：

- 创建、更新、激活、关闭、终止、取消必须在单个事务内完成。
- 写入必须锁定所属项目。
- 更新和状态动作必须校验 `version`。
- 创建主合同时依赖唯一约束防止并发创建多个非取消主合同。
- 创建补充合同时必须校验项目为 `ACTIVE` 且主合同为同项目 `EFFECTIVE` 主合同。
- 激活补充合同时必须再次校验项目为 `ACTIVE` 且主合同仍属于同项目且状态为 `EFFECTIVE`。
- 合同终止、关闭、取消必须记录原因字段和审计。

销售订单关联：

- 销售订单创建、更新、确认仍由销售订单服务负责。
- 创建、更新草稿订单时，如传入项目合同关联，必须在同一事务内校验项目和合同。
- 确认销售订单时必须再次校验项目为 `ACTIVE`、合同为 `EFFECTIVE`、客户一致且合同属于项目。
- 关联、解除或切换项目合同时，必须写 `target_type = SALES_PROJECT` 的审计记录，摘要包含订单号及旧/新关联。
- 校验失败不得产生订单状态变更、库存预留、出库或审计误记录。

## 历史兼容

- 020 迁移不得回填历史销售订单项目归属。
- 历史销售订单 `projectId = null`、`contractId = null` 时仍可列表、详情、确认、出库、关闭和取消，保持原销售管理规则。
- 销售订单响应中项目合同字段为空时，前端应展示为未关联项目，不得推断客户项目。
- 项目列表和详情不得把未关联历史订单计入项目销售订单摘要。
- 采购订单、生产工单、库存、成本和财务数据在 020 不增加项目字段，不做兼容迁移。

## 联调验收

- 管理员可以创建、编辑、激活、关闭和取消销售项目。
- 项目激活时没有已生效主合同返回 `PROJECT_MAIN_CONTRACT_REQUIRED`。
- 同一项目并发创建两个非取消主合同，只允许一个成功。
- 主合同金额为 0 或负数返回 `CONTRACT_AMOUNT_INVALID`。
- 补充合同金额为 0 返回 `CONTRACT_AMOUNT_INVALID`，正负金额均可保存。
- 补充合同缺少主合同或引用非本项目主合同返回明确错误。
- 激活合同后，关键商业字段不可通过更新接口覆盖。
- 项目关闭、项目取消、合同关闭、合同终止、合同取消必须填写 1-200 字原因。
- 补充合同仅允许在项目 `ACTIVE` 且主合同 `EFFECTIVE` 时创建，激活时再次校验。
- 销售订单草稿可以关联或解除项目合同；非草稿订单不可修改项目合同关联。
- 销售订单关联项目合同时，项目非 `ACTIVE`、合同非 `EFFECTIVE`、客户不一致或合同不属于项目均应拒绝。
- 历史未关联项目销售订单继续可查询和执行原有销售流程。
- 无 `sales:contract:view` 的用户查看项目列表和详情不得获得合同编号、金额、状态或数量。
- 无 `sales:order:view` 的用户查看项目详情不得获得关联销售订单数量、摘要和明细。
- 所有写动作均产生审计日志。

## 前端协作提示

- 前端菜单位置为销售菜单下“销售项目”。
- 固定路由：`/sales/projects`、`/sales/projects/create`、`/sales/projects/:id`、`/sales/projects/:id/edit`。
- `/sales` 根路径进入当前用户第一个可见销售子页面。
- 合同在项目详情内通过抽屉维护。
- 项目详情包含合同、关联订单和操作记录。
- 项目和合同终态操作使用原因确认弹窗，含 1-200 字文本域、错误区域、确认和取消按钮。
- 390px 视口下项目和合同表格保持横向滚动，不压缩到字段重叠。
