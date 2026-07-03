# 生产执行基础接口契约

## 目标

定义生产执行基础阶段的接口范围、字段、权限、错误码和联调验收规则，作为前后端实现与测试依据。本阶段打通生产工单、领料、报工和完工入库，使生产来源的库存变化可追溯到工单和 BOM 快照。

## 通用规则

- 所有接口使用统一 `ApiResponse<T>` 包装。
- 分页接口返回 `PageResponse<T>`。
- 时间字段使用后端生成的 ISO 8601 字符串。
- 写接口需要 CSRF token。
- 认证和权限沿用账号权限模块。
- 后端接口鉴权是最终安全边界，前端菜单和按钮权限只作为体验控制。
- 所有生产数量使用后端 `BigDecimal` 和数据库 `NUMERIC(18,6)`，禁止浮点数承载业务计算。
- 一期不启用库存占用锁定，领料过账时实时扣减库存，库存不得为负。
- 工单发布后生成 BOM 用料快照，后续 BOM 修改不得影响已发布工单。
- 领料和完工入库过账必须生成库存变动流水；报工不直接修改库存。
- 写操作成功后必须生成审计日志，至少记录操作人、动作、目标类型、目标标识和操作时间。
- `VALIDATION_ERROR`、`AUTH_FORBIDDEN`、`AUTH_UNAUTHORIZED` 沿用账号权限模块或通用错误码规则。

## 状态与枚举

### 生产工单状态

| 值 | 说明 |
|---|---|
| `DRAFT` | 草稿，可编辑，可发布或取消 |
| `RELEASED` | 已发布，已生成 BOM 快照，可领料、报工、完工入库 |
| `IN_PROGRESS` | 生产中，已有领料、报工或完工入库记录 |
| `COMPLETED` | 已完成，以查看和追溯为主 |
| `CANCELLED` | 已取消，只允许无已过账业务的工单取消 |

### 生产单据状态

| 值 | 说明 |
|---|---|
| `DRAFT` | 草稿，可编辑 |
| `POSTED` | 已过账，已生效，不可编辑 |

### 生产库存变动类型

| 值 | 说明 |
|---|---|
| `PRODUCTION_ISSUE` | 生产领料出库 |
| `PRODUCTION_RECEIPT` | 完工入库 |

### 生产库存来源类型

| 值 | 说明 |
|---|---|
| `PRODUCTION_MATERIAL_ISSUE` | 生产领料明细 |
| `PRODUCTION_COMPLETION_RECEIPT` | 完工入库明细 |

## 字段定义

### 生产工单汇总字段

| 字段 | 类型 | 请求必填 | 响应必返 | 说明 |
|---|---|---|---|---|
| id | number | 否 | 是 | 后端主键 |
| workOrderNo | string | 否 | 是 | 工单编号，后端生成，全局唯一 |
| productMaterialId | number | 是 | 是 | 产品物料标识 |
| productMaterialCode | string | 否 | 是 | 产品物料编码 |
| productMaterialName | string | 否 | 是 | 产品物料名称 |
| bomId | number | 是 | 是 | 引用 BOM 标识 |
| bomCode | string | 否 | 是 | BOM 编码 |
| bomVersionCode | string | 否 | 是 | BOM 版本 |
| plannedQuantity | number | 是 | 是 | 计划数量，必须大于 0 |
| reportedQuantity | number | 否 | 是 | 累计报工数量 |
| qualifiedQuantity | number | 否 | 是 | 累计合格数量 |
| defectiveQuantity | number | 否 | 是 | 累计不良数量 |
| receivedQuantity | number | 否 | 是 | 累计入库数量 |
| issueWarehouseId | number | 是 | 是 | 默认领料仓库 |
| receiptWarehouseId | number | 是 | 是 | 默认入库仓库 |
| plannedStartDate | string | 是 | 是 | 计划开工日期 |
| plannedFinishDate | string | 是 | 是 | 计划完工日期 |
| status | string | 否 | 是 | 工单状态 |
| remark | string | 否 | 否 | 备注 |
| createdByName | string | 否 | 是 | 创建人 |
| createdAt | string | 否 | 是 | 创建时间 |
| updatedAt | string | 否 | 是 | 更新时间 |
| releasedByName | string | 否 | 否 | 发布人 |
| releasedAt | string | 否 | 否 | 发布时间 |
| completedByName | string | 否 | 否 | 完成人 |
| completedAt | string | 否 | 否 | 完成时间 |

### 工单用料快照字段

| 字段 | 类型 | 请求必填 | 响应必返 | 说明 |
|---|---|---|---|---|
| id | number | 否 | 是 | 快照行主键 |
| lineNo | number | 否 | 是 | 行号，来自 BOM 明细 |
| bomItemId | number | 否 | 是 | 来源 BOM 明细标识 |
| materialId | number | 否 | 是 | 子项物料标识 |
| materialCode | string | 否 | 是 | 子项物料编码 |
| materialName | string | 否 | 是 | 子项物料名称 |
| materialType | string | 否 | 是 | 子项物料类型 |
| unitId | number | 否 | 是 | 单位标识 |
| unitName | string | 否 | 是 | 单位名称 |
| requiredQuantity | number | 否 | 是 | 应领数量 |
| issuedQuantity | number | 否 | 是 | 已领数量 |
| remainingQuantity | number | 否 | 是 | 未领数量 |
| lossRate | number | 否 | 是 | BOM 损耗率快照 |
| remark | string | 否 | 否 | 备注 |

### 领料单字段

| 字段 | 类型 | 请求必填 | 响应必返 | 说明 |
|---|---|---|---|---|
| id | number | 否 | 是 | 后端主键 |
| issueNo | string | 否 | 是 | 领料单号，后端生成 |
| workOrderId | number | 是 | 是 | 来源工单 |
| status | string | 否 | 是 | `DRAFT` 或 `POSTED` |
| businessDate | string | 是 | 是 | 业务日期 |
| reason | string | 是 | 是 | 领料原因 |
| remark | string | 否 | 否 | 备注 |
| createdByName | string | 否 | 是 | 创建人 |
| createdAt | string | 否 | 是 | 创建时间 |
| postedByName | string | 否 | 否 | 过账人 |
| postedAt | string | 否 | 否 | 过账时间 |
| lines | array | 是 | 是 | 领料明细 |

### 领料明细字段

| 字段 | 类型 | 请求必填 | 响应必返 | 说明 |
|---|---|---|---|---|
| id | number | 否 | 是 | 明细主键 |
| workOrderMaterialId | number | 是 | 是 | 工单用料快照行 |
| lineNo | number | 是 | 是 | 行号 |
| warehouseId | number | 是 | 是 | 出库仓库 |
| materialId | number | 否 | 是 | 物料标识 |
| unitId | number | 否 | 是 | 单位标识 |
| quantity | number | 是 | 是 | 本次领料数量，必须大于 0 |
| beforeQuantity | number | 否 | 否 | 过账前库存 |
| afterQuantity | number | 否 | 否 | 过账后库存 |
| remark | string | 否 | 否 | 备注 |

### 报工单字段

| 字段 | 类型 | 请求必填 | 响应必返 | 说明 |
|---|---|---|---|---|
| id | number | 否 | 是 | 后端主键 |
| reportNo | string | 否 | 是 | 报工单号，后端生成 |
| workOrderId | number | 是 | 是 | 来源工单 |
| status | string | 否 | 是 | `DRAFT` 或 `POSTED` |
| businessDate | string | 是 | 是 | 报工日期 |
| qualifiedQuantity | number | 是 | 是 | 本次合格数量，必须大于等于 0 |
| defectiveQuantity | number | 是 | 是 | 本次不良数量，必须大于等于 0 |
| totalQuantity | number | 否 | 是 | 本次报工总量，合格数量加不良数量 |
| reporterName | string | 否 | 是 | 报工人 |
| remark | string | 否 | 否 | 备注 |
| createdAt | string | 否 | 是 | 创建时间 |
| postedAt | string | 否 | 否 | 过账时间 |

### 完工入库单字段

| 字段 | 类型 | 请求必填 | 响应必返 | 说明 |
|---|---|---|---|---|
| id | number | 否 | 是 | 后端主键 |
| receiptNo | string | 否 | 是 | 完工入库单号，后端生成 |
| workOrderId | number | 是 | 是 | 来源工单 |
| status | string | 否 | 是 | `DRAFT` 或 `POSTED` |
| businessDate | string | 是 | 是 | 业务日期 |
| receiptWarehouseId | number | 是 | 是 | 入库仓库 |
| quantity | number | 是 | 是 | 本次入库数量，必须大于 0 |
| beforeQuantity | number | 否 | 否 | 过账前库存 |
| afterQuantity | number | 否 | 否 | 过账后库存 |
| remark | string | 否 | 否 | 备注 |
| createdAt | string | 否 | 是 | 创建时间 |
| postedAt | string | 否 | 否 | 过账时间 |

## 接口分组

| 资源 | 路径 | 能力 |
|---|---|---|
| 生产工单 | `/api/admin/production/work-orders` | list/get/create/update/release/complete/cancel |
| 领料单 | `/api/admin/production/work-orders/{workOrderId}/material-issues` | list/get/create/update/post |
| 报工单 | `/api/admin/production/work-orders/{workOrderId}/reports` | list/get/create/update/post |
| 完工入库单 | `/api/admin/production/work-orders/{workOrderId}/completion-receipts` | list/get/create/update/post |

## 接口定义

### 生产工单分页列表

- 方法：`GET`
- 路径：`/api/admin/production/work-orders`
- 权限：`production:work-order:view`
- 查询参数：
  - `page`、`pageSize`。
  - `keyword`：工单编号、产品编码或产品名称。
  - `status`：工单状态。
  - `productMaterialId`：产品物料标识。
  - `dateFrom`、`dateTo`：计划开工日期范围。
- 成功响应：`PageResponse<ProductionWorkOrderSummary>`。

### 生产工单详情

- 方法：`GET`
- 路径：`/api/admin/production/work-orders/{id}`
- 权限：`production:work-order:view`
- 成功响应：`ProductionWorkOrderDetail`，包含用料快照、领料记录、报工记录、完工入库记录和相关库存流水摘要。
- 不存在返回：`PRODUCTION_WORK_ORDER_NOT_FOUND`。

### 创建生产工单

- 方法：`POST`
- 路径：`/api/admin/production/work-orders`
- 权限：`production:work-order:create`
- 请求体：`ProductionWorkOrderPayload`。
- 规则：
  - 新建工单状态固定为 `DRAFT`。
  - 工单编号由后端生成。
  - 产品物料必须启用，且物料类型为成品或半成品。
  - BOM 必须启用，且 BOM 父项物料必须等于产品物料。
  - 计划数量必须大于 0。
  - 领料仓库和入库仓库必须启用。
- 成功响应：`ProductionWorkOrderDetail`。

### 更新生产工单

- 方法：`PUT`
- 路径：`/api/admin/production/work-orders/{id}`
- 权限：`production:work-order:update`
- 请求体：`ProductionWorkOrderPayload`。
- 规则：
  - 仅 `DRAFT` 工单可更新。
  - 已发布、生产中、已完成、已取消工单不可更新关键字段。
- 成功响应：`ProductionWorkOrderDetail`。

### 发布生产工单

- 方法：`PUT`
- 路径：`/api/admin/production/work-orders/{id}/release`
- 权限：`production:work-order:release`
- 规则：
  - 仅 `DRAFT` 工单可发布。
  - 发布时校验产品、BOM、BOM 明细、仓库和单位状态。
  - 发布时按 BOM 明细和工单计划数量生成用料快照。
  - 用料快照应领数量按 `计划数量 / BOM 基准数量 * BOM 明细数量 * (1 + 损耗率)` 计算。
- 成功响应：`ProductionWorkOrderDetail`。

### 完成生产工单

- 方法：`PUT`
- 路径：`/api/admin/production/work-orders/{id}/complete`
- 权限：`production:work-order:complete`
- 规则：
  - 仅 `RELEASED` 或 `IN_PROGRESS` 工单可完成。
  - 累计入库数量必须等于计划数量。
  - 存在未过账的生产单据时不可完成。
- 成功响应：`ProductionWorkOrderDetail`。

### 取消生产工单

- 方法：`PUT`
- 路径：`/api/admin/production/work-orders/{id}/cancel`
- 权限：`production:work-order:cancel`
- 规则：
  - 仅 `DRAFT` 或无已过账业务的 `RELEASED` 工单可取消。
  - 已领料、已报工或已入库的工单不可取消。
- 成功响应：`ProductionWorkOrderDetail`。

### 创建领料单

- 方法：`POST`
- 路径：`/api/admin/production/work-orders/{workOrderId}/material-issues`
- 权限：`production:issue:create`
- 请求体：`ProductionMaterialIssuePayload`。
- 规则：
  - 工单必须为 `RELEASED` 或 `IN_PROGRESS`。
  - 明细不能为空。
  - 明细必须引用工单用料快照行。
  - 本次领料数量必须大于 0，且累计领料不得超过应领数量。
  - 仓库和物料必须启用。
- 成功响应：`ProductionMaterialIssueDetail`。

### 更新领料单

- 方法：`PUT`
- 路径：`/api/admin/production/work-orders/{workOrderId}/material-issues/{id}`
- 权限：`production:issue:update`
- 规则：仅 `DRAFT` 领料单可更新。
- 成功响应：`ProductionMaterialIssueDetail`。

### 过账领料单

- 方法：`PUT`
- 路径：`/api/admin/production/work-orders/{workOrderId}/material-issues/{id}/post`
- 权限：`production:issue:post`
- 规则：
  - 仅 `DRAFT` 领料单可过账。
  - 过账在单个事务内锁定领料单、工单、工单用料快照和库存余额。
  - 库存不足返回 `PRODUCTION_STOCK_NOT_ENOUGH`，余额和流水不变化。
  - 过账成功后写入 `PRODUCTION_ISSUE` 库存流水，更新工单已领数量，并把工单状态推进为 `IN_PROGRESS`。
- 成功响应：`ProductionMaterialIssueDetail`。

### 创建报工单

- 方法：`POST`
- 路径：`/api/admin/production/work-orders/{workOrderId}/reports`
- 权限：`production:report:create`
- 请求体：`ProductionWorkReportPayload`。
- 规则：
  - 工单必须为 `RELEASED` 或 `IN_PROGRESS`。
  - 合格数量和不良数量必须大于等于 0，且合计必须大于 0。
  - 累计报工数量不得超过计划数量。
- 成功响应：`ProductionWorkReportDetail`。

### 更新报工单

- 方法：`PUT`
- 路径：`/api/admin/production/work-orders/{workOrderId}/reports/{id}`
- 权限：`production:report:update`
- 规则：仅 `DRAFT` 报工单可更新。
- 成功响应：`ProductionWorkReportDetail`。

### 过账报工单

- 方法：`PUT`
- 路径：`/api/admin/production/work-orders/{workOrderId}/reports/{id}/post`
- 权限：`production:report:post`
- 规则：
  - 仅 `DRAFT` 报工单可过账。
  - 过账更新工单累计报工、合格和不良数量。
  - 报工不生成库存流水。
  - 过账成功后工单状态推进为 `IN_PROGRESS`。
- 成功响应：`ProductionWorkReportDetail`。

### 创建完工入库单

- 方法：`POST`
- 路径：`/api/admin/production/work-orders/{workOrderId}/completion-receipts`
- 权限：`production:receipt:create`
- 请求体：`ProductionCompletionReceiptPayload`。
- 规则：
  - 工单必须为 `RELEASED` 或 `IN_PROGRESS`。
  - 入库数量必须大于 0。
  - 累计入库数量不得超过累计合格报工数量。
  - 入库仓库必须启用。
- 成功响应：`ProductionCompletionReceiptDetail`。

### 更新完工入库单

- 方法：`PUT`
- 路径：`/api/admin/production/work-orders/{workOrderId}/completion-receipts/{id}`
- 权限：`production:receipt:update`
- 规则：仅 `DRAFT` 完工入库单可更新。
- 成功响应：`ProductionCompletionReceiptDetail`。

### 过账完工入库单

- 方法：`PUT`
- 路径：`/api/admin/production/work-orders/{workOrderId}/completion-receipts/{id}/post`
- 权限：`production:receipt:post`
- 规则：
  - 仅 `DRAFT` 完工入库单可过账。
  - 过账在单个事务内锁定入库单、工单和库存余额。
  - 过账成功后写入 `PRODUCTION_RECEIPT` 库存流水，更新工单累计入库数量，并把工单状态推进为 `IN_PROGRESS`。
- 成功响应：`ProductionCompletionReceiptDetail`。

## 权限编码

| 权限编码 | 类型 | 说明 |
|---|---|---|
| `production` | 菜单 | 生产管理 |
| `production:work-order:view` | 操作 | 查看生产工单 |
| `production:work-order:create` | 操作 | 创建生产工单 |
| `production:work-order:update` | 操作 | 更新生产工单草稿 |
| `production:work-order:release` | 操作 | 发布生产工单 |
| `production:work-order:complete` | 操作 | 完成生产工单 |
| `production:work-order:cancel` | 操作 | 取消生产工单 |
| `production:issue:view` | 操作 | 查看生产领料 |
| `production:issue:create` | 操作 | 创建生产领料 |
| `production:issue:update` | 操作 | 更新生产领料草稿 |
| `production:issue:post` | 操作 | 过账生产领料 |
| `production:report:view` | 操作 | 查看生产报工 |
| `production:report:create` | 操作 | 创建生产报工 |
| `production:report:update` | 操作 | 更新生产报工草稿 |
| `production:report:post` | 操作 | 过账生产报工 |
| `production:receipt:view` | 操作 | 查看完工入库 |
| `production:receipt:create` | 操作 | 创建完工入库 |
| `production:receipt:update` | 操作 | 更新完工入库草稿 |
| `production:receipt:post` | 操作 | 过账完工入库 |

## 错误码

| 错误码 | HTTP | 说明 |
|---|---|---|
| `PRODUCTION_WORK_ORDER_NOT_FOUND` | 404 | 生产工单不存在 |
| `PRODUCTION_WORK_ORDER_STATUS_INVALID` | 409 | 工单状态不允许当前操作 |
| `PRODUCTION_WORK_ORDER_HAS_POSTED_BUSINESS` | 409 | 工单已有过账业务，不能取消 |
| `PRODUCTION_PRODUCT_MATERIAL_INVALID` | 400 | 产品物料不存在、停用或类型不允许 |
| `PRODUCTION_BOM_INVALID` | 400 | BOM 不存在、未启用或父项物料不匹配 |
| `PRODUCTION_BOM_EMPTY_ITEMS` | 400 | BOM 明细为空 |
| `PRODUCTION_WAREHOUSE_INVALID` | 400 | 仓库不存在或已停用 |
| `PRODUCTION_MATERIAL_INVALID` | 400 | 用料物料不存在或已停用 |
| `PRODUCTION_UNIT_INVALID` | 400 | 单位不存在或已停用 |
| `PRODUCTION_QUANTITY_INVALID` | 400 | 数量不合法 |
| `PRODUCTION_ISSUE_NOT_FOUND` | 404 | 领料单不存在 |
| `PRODUCTION_ISSUE_EMPTY_LINES` | 400 | 领料明细不能为空 |
| `PRODUCTION_ISSUE_EXCEEDS_REQUIRED` | 409 | 累计领料超过应领数量 |
| `PRODUCTION_STOCK_NOT_ENOUGH` | 409 | 库存不足 |
| `PRODUCTION_REPORT_NOT_FOUND` | 404 | 报工单不存在 |
| `PRODUCTION_REPORT_EXCEEDS_PLAN` | 409 | 累计报工超过计划数量 |
| `PRODUCTION_RECEIPT_NOT_FOUND` | 404 | 完工入库单不存在 |
| `PRODUCTION_RECEIPT_EXCEEDS_REPORTED` | 409 | 累计入库超过累计合格报工数量 |
| `PRODUCTION_DOCUMENT_POSTED_IMMUTABLE` | 409 | 已过账生产单据不可编辑 |
| `PRODUCTION_DUPLICATE_POST` | 409 | 生产单据已过账或重复过账 |
| `PRODUCTION_MOVEMENT_SOURCE_DUPLICATED` | 409 | 来源明细已生成库存变动 |

## 联调验收

- 管理员可以完成工单创建、更新、发布、领料、报工、完工入库、完成和追溯。
- 发布工单后生成 BOM 用料快照。
- 领料过账后库存减少，库存流水来源为生产领料明细。
- 报工过账后工单累计报工数量更新，但库存不变化。
- 完工入库过账后库存增加，库存流水来源为完工入库明细。
- 超领、超报、超入、库存不足、停用主数据、重复过账均返回明确业务错误，且失败操作不改变余额和流水。
- 只读用户可以查看工单和追溯信息，不能创建、更新、发布、过账或完成。
- 无权限用户访问生产接口返回明确认证或鉴权错误。
- 所有写操作都有审计日志。
