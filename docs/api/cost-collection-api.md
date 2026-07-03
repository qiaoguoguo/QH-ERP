# 成本归集基础接口契约

## 目标

定义成本归集基础阶段的接口范围、字段、权限、错误码和联调验收规则，作为前后端实现与测试依据。本阶段只建立成本业务记录与来源追溯，不提供正式财务成本核算结果。

## 通用规则

- 所有接口使用统一 `ApiResponse<T>` 包装。
- 分页接口返回 `PageResponse<T>`。
- 时间字段使用后端生成的 ISO 8601 字符串。
- 写接口需要 CSRF token。
- 认证和权限沿用账号权限模块。
- 后端接口鉴权是最终安全边界，前端菜单和按钮权限只作为体验控制。
- 成本数量、单价和金额使用后端 `BigDecimal` 和数据库 `NUMERIC(18,6)`，禁止浮点数承载业务计算。
- 本阶段的金额只表示业务录入或来源口径，不代表正式财务核算结果。
- 自动来源记录必须在生产过账事务中同步生成，不能异步补写造成来源单据和成本记录不一致。
- 成本记录不得反写库存余额、生产工单状态或生产单据状态。
- 写操作成功后必须生成审计日志，至少记录操作人、动作、目标类型、目标标识和操作时间。
- `VALIDATION_ERROR`、`AUTH_FORBIDDEN`、`AUTH_UNAUTHORIZED` 沿用账号权限模块或通用错误码规则。

## 状态与枚举

### 成本类型

| 值 | 说明 |
|---|---|
| `MATERIAL` | 材料成本来源，主要来自生产领料明细 |
| `LABOR` | 人工成本来源，主要来自生产报工或手工记录 |
| `MANUFACTURING_OVERHEAD` | 制造费用，手工记录 |
| `OTHER` | 其他成本，手工记录 |

### 来源类型

| 值 | 说明 |
|---|---|
| `AUTO_PRODUCTION` | 生产过账自动生成的来源记录 |
| `MANUAL_ENTRY` | 用户手工录入的业务记录 |

### 来源单据类型

| 值 | 说明 |
|---|---|
| `PRODUCTION_MATERIAL_ISSUE` | 生产领料明细 |
| `PRODUCTION_WORK_REPORT` | 生产报工单 |
| `PRODUCTION_COMPLETION_RECEIPT` | 完工入库单，仅用于产出数量追溯 |
| `MANUAL_COST_RECORD` | 手工成本记录 |

### 口径类型

| 值 | 说明 |
|---|---|
| `SOURCE_QUANTITY_ONLY` | 来源数量口径，不含金额 |
| `MANUAL_AMOUNT` | 手工金额 |
| `MANUAL_UNIT_PRICE_QUANTITY` | 手工单价乘数量 |
| `OUTPUT_QUANTITY_TRACE` | 完工产出数量追溯，不计入成本金额 |

### 记录状态

| 值 | 说明 |
|---|---|
| `ACTIVE` | 有效成本记录 |
| `VOIDED` | 已作废记录，本阶段仅预留，不作为必须操作 |

## 字段定义

### 成本记录汇总字段

| 字段 | 类型 | 请求必填 | 响应必返 | 说明 |
|---|---|---|---|---|
| id | number | 否 | 是 | 后端主键 |
| recordNo | string | 否 | 是 | 成本记录编号，后端生成 |
| workOrderId | number | 是 | 是 | 关联生产工单 |
| workOrderNo | string | 否 | 是 | 生产工单号 |
| productMaterialId | number | 否 | 是 | 产品物料标识，默认从工单带出 |
| productMaterialCode | string | 否 | 是 | 产品物料编码 |
| productMaterialName | string | 否 | 是 | 产品物料名称 |
| costType | string | 是 | 是 | 成本类型 |
| sourceType | string | 否 | 是 | 来源类型 |
| sourceDocumentType | string | 否 | 是 | 来源单据类型 |
| sourceDocumentNo | string | 否 | 否 | 来源单据号或手工说明 |
| sourceDocumentId | number | 否 | 否 | 来源单据标识 |
| sourceLineId | number | 否 | 否 | 来源明细标识 |
| basisType | string | 是 | 是 | 口径类型 |
| materialId | number | 否 | 否 | 来源物料标识 |
| materialCode | string | 否 | 否 | 来源物料编码 |
| materialName | string | 否 | 否 | 来源物料名称 |
| unitId | number | 否 | 否 | 单位标识 |
| unitName | string | 否 | 否 | 单位名称 |
| quantity | number | 否 | 否 | 数量口径 |
| unitPrice | number | 否 | 否 | 单价口径 |
| amount | number | 否 | 否 | 金额口径 |
| businessDate | string | 是 | 是 | 业务日期 |
| status | string | 否 | 是 | 记录状态 |
| remark | string | 否 | 否 | 备注 |
| recordedByName | string | 否 | 是 | 记录人 |
| recordedAt | string | 否 | 是 | 记录时间 |
| createdByName | string | 否 | 是 | 创建人 |
| createdAt | string | 否 | 是 | 创建时间 |
| updatedAt | string | 否 | 是 | 更新时间 |

### 成本记录详情字段

详情响应在汇总字段基础上增加：

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| workOrderStatus | string | 是 | 工单状态 |
| sourceStatus | string | 否 | 来源单据状态 |
| sourceSummary | object | 否 | 来源数量、物料、仓库或报工摘要 |
| outputTrace | object | 否 | 完工入库产出追溯 |
| auditSummary | array | 否 | 相关审计摘要 |

### 手工成本记录请求

| 字段 | 类型 | 请求必填 | 说明 |
|---|---|---|---|
| workOrderId | number | 是 | 关联生产工单 |
| costType | string | 是 | 允许 `LABOR`、`MANUFACTURING_OVERHEAD`、`OTHER`，材料金额补录可用 `MATERIAL` |
| basisType | string | 是 | `MANUAL_AMOUNT` 或 `MANUAL_UNIT_PRICE_QUANTITY` |
| businessDate | string | 是 | 业务日期 |
| quantity | string | 否 | 数量口径，必须大于 0 |
| unitId | number | 否 | 单位标识 |
| unitPrice | string | 否 | 单价口径，必须大于等于 0 |
| amount | string | 否 | 金额口径，必须大于等于 0 |
| sourceDocumentNo | string | 否 | 外部或内部说明性来源号 |
| remark | string | 是 | 手工记录说明 |

规则：

- `quantity` 或 `amount` 至少有一个有效值。
- `MANUAL_UNIT_PRICE_QUANTITY` 必须同时提供 `quantity` 和 `unitPrice`。
- `MANUAL_AMOUNT` 必须提供 `amount`。
- 负数金额、负数数量、超出精度的金额或数量必须拒绝。
- 手工记录必须关联有效生产工单，产品从工单带出。
- 本阶段不支持仅按产品录入成本记录。

## 接口分组

| 资源 | 路径 | 能力 |
|---|---|---|
| 成本记录 | `/api/admin/cost/records` | list/get/create/update |
| 工单成本汇总 | `/api/admin/cost/work-orders/{workOrderId}/summary` | get |

## 接口定义

### 成本记录分页列表

- 方法：`GET`
- 路径：`/api/admin/cost/records`
- 权限：`cost:record:view`
- 查询参数：
  - `page`、`pageSize`。
  - `keyword`：成本记录号、工单号、产品编码、产品名称、来源单号。
  - `workOrderId`：生产工单标识。
  - `productMaterialId`：产品物料标识。
  - `costType`：成本类型。
  - `sourceType`：来源类型。
  - `sourceDocumentType`：来源单据类型。
  - `sourceDocumentNo`：来源单据号。
  - `dateFrom`、`dateTo`：业务日期范围。
- 成功响应：`PageResponse<CostRecordSummary>`。

### 成本记录详情

- 方法：`GET`
- 路径：`/api/admin/cost/records/{id}`
- 权限：`cost:record:view`
- 成功响应：`CostRecordDetail`，包含工单、产品、来源单据、金额或数量口径、记录人、记录时间和追溯摘要。
- 不存在返回：`COST_RECORD_NOT_FOUND`。

### 创建手工成本记录

- 方法：`POST`
- 路径：`/api/admin/cost/records`
- 权限：`cost:record:create`
- 请求体：`CostRecordPayload`。
- 规则：
  - 工单必须存在，且不能为已取消。
  - 成本类型、口径类型、金额和数量必须合法。
  - 手工记录来源类型固定为 `MANUAL_ENTRY`，来源单据类型固定为 `MANUAL_COST_RECORD`。
  - 创建成功后写入审计日志。
- 成功响应：`CostRecordDetail`。

### 更新手工成本记录

- 方法：`PUT`
- 路径：`/api/admin/cost/records/{id}`
- 权限：`cost:record:update`
- 请求体：`CostRecordPayload`。
- 规则：
  - 仅手工记录可更新。
  - 更新后仍必须关联原有效生产工单，产品仍从工单带出。
  - 自动生成记录不得修改来源单据、工单、产品、物料、来源数量。
  - 已作废记录不可更新。
- 成功响应：`CostRecordDetail`。

### 工单成本汇总

- 方法：`GET`
- 路径：`/api/admin/cost/work-orders/{workOrderId}/summary`
- 权限：`cost:record:view`
- 成功响应：`WorkOrderCostSummary`，包含工单成本记录列表、按成本类型汇总的业务金额、来源数量口径和完工入库产出数量追溯。
- 规则：
  - 汇总金额只汇总已有金额口径的业务记录。
  - 自动材料数量记录如无金额，不参与金额合计。
  - 响应必须明确 `formalAccounting=false` 或等价字段，避免被理解为正式核算结果。

## 自动来源生成规则

### 生产领料过账

- 一条已过账领料明细生成一条 `MATERIAL` 成本记录。
- 来源类型为 `AUTO_PRODUCTION`。
- 来源单据类型为 `PRODUCTION_MATERIAL_ISSUE`。
- 来源明细为 `mfg_material_issue_line.id`。
- 数量、单位、物料、工单和产品从领料明细和工单快照带出。
- 口径类型为 `SOURCE_QUANTITY_ONLY`，金额和单价允许为空。
- 防重口径为 `source_document_type + source_line_id + cost_type`。

### 生产报工过账

- 一张已过账报工单生成一条 `LABOR` 成本记录。
- 来源类型为 `AUTO_PRODUCTION`。
- 来源单据类型为 `PRODUCTION_WORK_REPORT`。
- 来源单据为 `mfg_work_report.id`。
- 数量取本次报工总量，金额允许为空。
- 口径类型为 `SOURCE_QUANTITY_ONLY`。
- 报工没有来源明细行时，防重口径为 `source_document_type + source_document_id + cost_type`。

### 完工入库过账

- 完工入库不自动形成成本金额记录。
- 工单成本汇总必须展示完工入库数量和单据追溯。
- 如实现产出追溯记录，成本类型不得计入金额合计，口径类型必须为 `OUTPUT_QUANTITY_TRACE`。
- 如保存产出追溯记录，防重口径为 `source_document_type + source_document_id + basis_type`。

## 权限编码

| 权限编码 | 类型 | 说明 |
|---|---|---|
| `cost` | 菜单 | 成本管理 |
| `cost:record:view` | 操作 | 查看成本记录 |
| `cost:record:create` | 操作 | 创建成本记录 |
| `cost:record:update` | 操作 | 更新手工成本记录 |

## 错误码

| 错误码 | HTTP | 说明 |
|---|---|---|
| `COST_RECORD_NOT_FOUND` | 404 | 成本记录不存在 |
| `COST_WORK_ORDER_NOT_FOUND` | 404 | 成本关联工单不存在 |
| `COST_WORK_ORDER_STATUS_INVALID` | 409 | 工单状态不允许成本记录 |
| `COST_SOURCE_DOCUMENT_NOT_FOUND` | 404 | 成本来源单据不存在 |
| `COST_SOURCE_DOCUMENT_STATUS_INVALID` | 409 | 成本来源单据状态无效 |
| `COST_SOURCE_DUPLICATED` | 409 | 成本来源已归集 |
| `COST_TYPE_INVALID` | 400 | 成本类型不合法 |
| `COST_BASIS_INVALID` | 400 | 成本口径不合法 |
| `COST_QUANTITY_INVALID` | 400 | 成本数量不合法 |
| `COST_AMOUNT_INVALID` | 400 | 成本金额不合法 |
| `COST_GENERATED_RECORD_IMMUTABLE` | 409 | 自动生成记录来源字段不可修改 |

## 联调验收

- 管理员可以完成生产工单、领料、报工、完工入库后查看自动生成成本来源记录。
- 管理员可以手工录入制造费用或其他成本记录。
- 成本记录列表可按工单、产品、成本类型、来源类型、来源单号和日期范围查询。
- 成本记录详情可追溯到工单和来源单据。
- 生产工单详情可查看成本记录摘要和完工产出数量口径。
- 重复来源、草稿来源、无效工单、非法金额或数量均返回明确业务错误。
- 只读用户可以查看成本记录和追溯信息，不能创建或更新。
- 无权限用户访问成本接口返回明确认证或鉴权错误。
- 所有写操作都有审计日志。
