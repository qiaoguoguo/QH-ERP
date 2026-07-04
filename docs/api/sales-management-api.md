# 销售管理基础接口契约

## 目标

定义销售管理基础阶段的接口范围、字段、权限、错误码、库存过账口径和联调验收规则，作为前后端实现与测试依据。本阶段只建立销售订单、销售出库、库存扣减和来源追溯，不提供应收、发票、收款、税务、凭证、收入确认、销售退货、物流发运、价格体系、审批、多币种、多组织、多公司、多账套、多租户或正式财务核算能力。

## 通用规则

- 所有接口使用统一 `ApiResponse<T>` 包装。
- 分页接口返回 `PageResponse<T>`。
- 时间字段使用后端生成的 ISO 8601 字符串。
- 写接口需要 CSRF token。
- 认证和权限沿用账号权限模块。
- 后端接口鉴权是最终安全边界，前端菜单和按钮权限只作为体验控制。
- 销售数量、出库数量和销售单价使用后端 `BigDecimal` 和数据库 `NUMERIC(18,6)`，禁止使用浮点数承载业务计算。
- 本阶段销售单价只表示销售业务字段，不代表应收、税额、收款、收入确认、凭证或正式成本结转结果。
- 本阶段不新增“可销售”主数据字段，销售订单明细只允许启用且 `material_type` 为 `FINISHED_GOOD` 或 `SEMI_FINISHED` 的物料；启用的 `RAW_MATERIAL`、`AUXILIARY` 视为不可销售。
- 创建、更新和确认销售订单必须校验客户启用；销售订单确认后客户后续停用，不阻止基于该订单创建、更新或过账销售出库。
- 销售出库过账必须调用库存过账复用服务，不允许绕过 `InventoryPostingService` 直接修改库存余额或库存流水。
- 销售订单、销售出库和库存流水必须可通过来源字段追溯。
- 写操作成功后必须生成审计日志，至少记录操作人、动作、目标类型、目标标识和操作时间。
- `VALIDATION_ERROR`、`AUTH_FORBIDDEN`、`AUTH_UNAUTHORIZED` 沿用账号权限模块或通用错误码规则。

## 状态与枚举

### 销售订单状态

| 值 | 说明 |
|---|---|
| `DRAFT` | 草稿，可编辑、可确认、可取消，不允许出库 |
| `CONFIRMED` | 已确认，可创建出库，可关闭 |
| `PARTIALLY_SHIPPED` | 部分出库，可继续出库，可关闭 |
| `SHIPPED` | 全部出库，不允许继续出库 |
| `CLOSED` | 已关闭，不允许出库 |
| `CANCELLED` | 已取消，不允许出库 |

### 销售出库状态

| 值 | 说明 |
|---|---|
| `DRAFT` | 草稿，可编辑、可过账 |
| `POSTED` | 已过账，已扣减库存并写库存流水，不可编辑 |

### 销售库存变动类型

| 字段 | 值 | 说明 |
|---|---|---|
| `movementType` | `SALES_SHIPMENT` | 销售出库 |
| `direction` | `OUT` | 库存扣减 |
| `sourceType` | `SALES_SHIPMENT` | 来源类型为销售出库 |
| `sourceId` | 销售出库主表 `id` | 来源单据 |
| `sourceLineId` | 销售出库明细 `id` | 来源行 |

## 主要表草案

### `sal_sales_order`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigserial` | 主键 |
| `order_no` | `varchar(64)` | 销售订单号，唯一 |
| `customer_id` | `bigint` | 客户标识，引用客户基础资料 |
| `order_date` | `date` | 订单日期 |
| `expected_ship_date` | `date` | 默认预计出库日期 |
| `status` | `varchar(32)` | 销售订单状态 |
| `remark` | `varchar(500)` | 备注 |
| `created_by`、`created_at` | | 创建审计字段 |
| `updated_by`、`updated_at` | | 更新审计字段 |
| `confirmed_by`、`confirmed_at` | | 确认信息 |
| `cancelled_by`、`cancelled_at` | | 取消信息 |
| `closed_by`、`closed_at` | | 关闭信息 |
| `version` | `bigint` | 乐观版本 |

### `sal_sales_order_line`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigserial` | 主键 |
| `order_id` | `bigint` | 销售订单标识 |
| `line_no` | `integer` | 行号，同一订单内唯一 |
| `material_id` | `bigint` | 物料标识 |
| `unit_id` | `bigint` | 单位标识，一期使用物料基本单位 |
| `quantity` | `numeric(18,6)` | 销售数量，必须大于 0 |
| `shipped_quantity` | `numeric(18,6)` | 已出库数量，范围为 0 到销售数量 |
| `unit_price` | `numeric(18,6)` | 销售单价，必须大于等于 0 |
| `expected_ship_date` | `date` | 行预计出库日期 |
| `remark` | `varchar(500)` | 备注 |
| `created_at`、`updated_at` | | 时间字段 |
| `version` | `bigint` | 乐观版本 |

### `sal_sales_shipment`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigserial` | 主键 |
| `shipment_no` | `varchar(64)` | 销售出库单号，唯一 |
| `order_id` | `bigint` | 来源销售订单 |
| `customer_id` | `bigint` | 客户标识 |
| `warehouse_id` | `bigint` | 出库仓库 |
| `business_date` | `date` | 出库业务日期 |
| `status` | `varchar(32)` | 销售出库状态 |
| `remark` | `varchar(500)` | 备注 |
| `created_by`、`created_at` | | 创建审计字段 |
| `updated_by`、`updated_at` | | 更新审计字段 |
| `posted_by`、`posted_at` | | 过账信息 |
| `version` | `bigint` | 乐观版本 |

### `sal_sales_shipment_line`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigserial` | 主键 |
| `shipment_id` | `bigint` | 销售出库标识 |
| `line_no` | `integer` | 行号，同一出库单内唯一 |
| `order_line_id` | `bigint` | 来源销售订单明细 |
| `material_id` | `bigint` | 物料标识 |
| `unit_id` | `bigint` | 单位标识 |
| `ordered_quantity` | `numeric(18,6)` | 订单数量 |
| `shipped_quantity_before` | `numeric(18,6)` | 本次出库前订单行已出库数量 |
| `remaining_quantity_before` | `numeric(18,6)` | 本次出库前订单行未出库数量 |
| `quantity` | `numeric(18,6)` | 本次出库数量，必须大于 0 |
| `before_quantity` | `numeric(18,6)` | 过账前库存 |
| `after_quantity` | `numeric(18,6)` | 过账后库存 |
| `remark` | `varchar(500)` | 备注 |
| `created_at`、`updated_at` | | 时间字段 |

## 字段定义

### 销售订单汇总字段

| 字段 | 类型 | 请求必填 | 响应必返 | 说明 |
|---|---|---|---|---|
| id | number | 否 | 是 | 后端主键 |
| orderNo | string | 否 | 是 | 销售订单号，后端生成 |
| customerId | number | 是 | 是 | 客户标识 |
| customerCode | string | 否 | 是 | 客户编码 |
| customerName | string | 否 | 是 | 客户名称 |
| orderDate | string | 是 | 是 | 订单日期 |
| expectedShipDate | string | 否 | 否 | 默认预计出库日期 |
| status | string | 否 | 是 | 销售订单状态 |
| lineCount | number | 否 | 是 | 明细行数量 |
| totalQuantity | number | 否 | 是 | 销售总数量 |
| shippedQuantity | number | 否 | 是 | 已出库总数量 |
| remainingQuantity | number | 否 | 是 | 未出库总数量 |
| remark | string | 否 | 否 | 备注 |
| createdByName | string | 否 | 是 | 创建人 |
| createdAt | string | 否 | 是 | 创建时间 |
| updatedAt | string | 否 | 是 | 更新时间 |
| confirmedByName | string | 否 | 否 | 确认人 |
| confirmedAt | string | 否 | 否 | 确认时间 |
| cancelledByName | string | 否 | 否 | 取消人 |
| cancelledAt | string | 否 | 否 | 取消时间 |
| closedByName | string | 否 | 否 | 关闭人 |
| closedAt | string | 否 | 否 | 关闭时间 |

### 销售订单明细字段

| 字段 | 类型 | 请求必填 | 响应必返 | 说明 |
|---|---|---|---|---|
| id | number | 否 | 是 | 后端主键 |
| lineNo | number | 是 | 是 | 行号，同一订单内唯一 |
| materialId | number | 是 | 是 | 物料标识 |
| materialCode | string | 否 | 是 | 物料编码 |
| materialName | string | 否 | 是 | 物料名称 |
| materialSpec | string | 否 | 否 | 规格型号 |
| unitId | number | 否 | 是 | 单位标识，一期使用物料基本单位 |
| unitName | string | 否 | 是 | 单位名称 |
| quantity | string | 是 | 是 | 销售数量，必须大于 0 |
| shippedQuantity | number | 否 | 是 | 已出库数量 |
| remainingQuantity | number | 否 | 是 | 未出库数量 |
| unitPrice | string | 是 | 是 | 销售单价，必须大于等于 0 |
| expectedShipDate | string | 否 | 否 | 行预计出库日期 |
| remark | string | 否 | 否 | 备注 |

### 销售订单详情字段

详情响应在销售订单汇总字段基础上增加：

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| lines | array | 是 | 销售订单明细 |
| shipments | array | 是 | 销售出库记录摘要 |
| inventoryMovements | array | 否 | 销售来源库存流水摘要 |
| auditSummary | array | 否 | 审计摘要 |

### 销售出库汇总字段

| 字段 | 类型 | 请求必填 | 响应必返 | 说明 |
|---|---|---|---|---|
| id | number | 否 | 是 | 后端主键 |
| shipmentNo | string | 否 | 是 | 销售出库单号，后端生成 |
| orderId | number | 是 | 是 | 来源销售订单 |
| orderNo | string | 否 | 是 | 来源销售订单号 |
| customerId | number | 否 | 是 | 客户标识 |
| customerName | string | 否 | 是 | 客户名称 |
| warehouseId | number | 是 | 是 | 出库仓库 |
| warehouseName | string | 否 | 是 | 出库仓库名称 |
| businessDate | string | 是 | 是 | 出库业务日期 |
| status | string | 否 | 是 | 销售出库状态 |
| lineCount | number | 否 | 是 | 明细行数量 |
| totalQuantity | number | 否 | 是 | 出库总数量 |
| remark | string | 否 | 否 | 备注 |
| createdByName | string | 否 | 是 | 创建人 |
| createdAt | string | 否 | 是 | 创建时间 |
| updatedAt | string | 否 | 是 | 更新时间 |
| postedByName | string | 否 | 否 | 过账人 |
| postedAt | string | 否 | 否 | 过账时间 |

### 销售出库明细字段

| 字段 | 类型 | 请求必填 | 响应必返 | 说明 |
|---|---|---|---|---|
| id | number | 否 | 是 | 后端主键 |
| lineNo | number | 是 | 是 | 行号 |
| orderLineId | number | 是 | 是 | 来源销售订单明细 |
| materialId | number | 否 | 是 | 物料标识，从订单明细带出 |
| materialCode | string | 否 | 是 | 物料编码 |
| materialName | string | 否 | 是 | 物料名称 |
| unitId | number | 否 | 是 | 单位标识 |
| unitName | string | 否 | 是 | 单位名称 |
| orderedQuantity | number | 否 | 是 | 订单数量 |
| shippedQuantityBefore | number | 否 | 是 | 本次出库前订单行已出库数量 |
| remainingQuantityBefore | number | 否 | 是 | 本次出库前订单行未出库数量 |
| quantity | string | 是 | 是 | 本次出库数量，必须大于 0 |
| beforeQuantity | number | 否 | 否 | 过账前库存 |
| afterQuantity | number | 否 | 否 | 过账后库存 |
| remark | string | 否 | 否 | 备注 |

### 销售出库详情字段

详情响应在销售出库汇总字段基础上增加：

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| lines | array | 是 | 销售出库明细 |
| orderSummary | object | 是 | 来源销售订单摘要 |
| inventoryMovements | array | 是 | 出库产生的库存流水摘要，草稿返回空数组 |
| auditSummary | array | 否 | 审计摘要 |

### 库存流水摘要字段

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| id | number | 是 | 库存流水主键 |
| movementNo | string | 是 | 库存流水号 |
| movementType | string | 是 | 固定为 `SALES_SHIPMENT` |
| direction | string | 是 | 固定为 `OUT` |
| warehouseName | string | 是 | 仓库名称 |
| materialCode | string | 是 | 物料编码 |
| materialName | string | 是 | 物料名称 |
| quantity | number | 是 | 出库数量 |
| beforeQuantity | number | 是 | 过账前库存 |
| afterQuantity | number | 是 | 过账后库存 |
| businessDate | string | 是 | 业务日期 |
| operatorName | string | 是 | 过账操作人 |
| occurredAt | string | 是 | 库存流水发生时间 |

## 接口分组

| 资源 | 路径 | 能力 |
|---|---|---|
| 销售订单 | `/api/admin/sales/orders` | list/get/create/update/confirm/cancel/close |
| 销售出库 | `/api/admin/sales/shipments` | list/get/update/post |
| 订单出库 | `/api/admin/sales/orders/{id}/shipments` | create |

## 接口定义

### 销售订单分页列表

- 方法：`GET`
- 路径：`/api/admin/sales/orders`
- 权限：`sales:order:view`
- 查询参数：
  - `page`、`pageSize`。
  - `keyword`：订单号、客户编码、客户名称、物料编码或物料名称。
  - `customerId`：客户标识。
  - `status`：销售订单状态。
  - `dateFrom`、`dateTo`：订单日期范围。
  - `expectedDateFrom`、`expectedDateTo`：预计出库日期范围。
- 成功响应：`PageResponse<SalesOrderSummary>`。

### 销售订单详情

- 方法：`GET`
- 路径：`/api/admin/sales/orders/{id}`
- 权限：`sales:order:view`
- 成功响应：`SalesOrderDetail`。
- 不存在返回：`SALES_ORDER_NOT_FOUND`。

### 创建销售订单

- 方法：`POST`
- 路径：`/api/admin/sales/orders`
- 权限：`sales:order:create`
- 请求体：`SalesOrderPayload`。
- 规则：
  - 新建状态固定为 `DRAFT`。
  - 明细不能为空。
  - 客户必须存在且启用。
  - 物料必须存在且启用。
  - 物料类型必须为 `FINISHED_GOOD` 或 `SEMI_FINISHED`；`RAW_MATERIAL`、`AUXILIARY` 在本阶段不可销售。
  - 单位使用物料基本单位。
  - 销售数量必须大于 0。
  - 销售单价必须大于等于 0。
  - 同一订单内不允许重复物料行。
- 成功响应：`SalesOrderDetail`。

### 更新销售订单

- 方法：`PUT`
- 路径：`/api/admin/sales/orders/{id}`
- 权限：`sales:order:update`
- 请求体：`SalesOrderPayload`。
- 规则：
  - 仅 `DRAFT` 订单可更新。
  - 更新时替换明细，以请求体为准。
- 成功响应：`SalesOrderDetail`。

### 确认销售订单

- 方法：`PUT`
- 路径：`/api/admin/sales/orders/{id}/confirm`
- 权限：`sales:order:confirm`
- 规则：
  - 仅 `DRAFT` 订单可确认。
  - 确认时重新校验客户、物料、可销售物料类型、单位、数量和销售单价。
  - 明细不能为空。
- 成功响应：`SalesOrderDetail`。

### 取消销售订单

- 方法：`PUT`
- 路径：`/api/admin/sales/orders/{id}/cancel`
- 权限：`sales:order:cancel`
- 规则：
  - `DRAFT` 订单可取消。
  - 无已过账出库的 `CONFIRMED` 订单可取消。
  - 已发生出库的订单不可取消，可关闭未出库余量。
- 成功响应：`SalesOrderDetail`。

### 关闭销售订单

- 方法：`PUT`
- 路径：`/api/admin/sales/orders/{id}/close`
- 权限：`sales:order:close`
- 规则：
  - `CONFIRMED`、`PARTIALLY_SHIPPED` 或 `SHIPPED` 订单可关闭。
  - `DRAFT`、`CANCELLED` 订单不可关闭。
  - 关闭后不允许继续出库。
- 成功响应：`SalesOrderDetail`。

### 销售出库分页列表

- 方法：`GET`
- 路径：`/api/admin/sales/shipments`
- 权限：`sales:shipment:view`
- 查询参数：
  - `page`、`pageSize`。
  - `keyword`：出库单号、订单号、客户编码、客户名称、物料编码或物料名称。
  - `customerId`：客户标识。
  - `warehouseId`：仓库标识。
  - `status`：销售出库状态。
  - `dateFrom`、`dateTo`：业务日期范围。
  - `orderId`：来源销售订单标识。
- 成功响应：`PageResponse<SalesShipmentSummary>`。

### 基于销售订单创建销售出库

- 方法：`POST`
- 路径：`/api/admin/sales/orders/{id}/shipments`
- 权限：`sales:shipment:create`
- 请求体：`SalesShipmentPayload`。
- 规则：
  - 销售订单必须为 `CONFIRMED` 或 `PARTIALLY_SHIPPED`。
  - 出库仓库必须存在且启用。
  - 明细必须引用来源销售订单明细。
  - 出库数量必须大于 0，且不得超过订单行未出库数量。
  - 来源订单行、物料和单位必须一致。
  - 客户后续停用不阻止已确认订单创建出库，但物料必须当前启用且仍属于可销售类型，单位和仓库当前有效性也必须重新校验。
- 成功响应：`SalesShipmentDetail`。

### 销售出库详情

- 方法：`GET`
- 路径：`/api/admin/sales/shipments/{id}`
- 权限：`sales:shipment:view`
- 成功响应：`SalesShipmentDetail`。
- 不存在返回：`SALES_SHIPMENT_NOT_FOUND`。

### 更新销售出库

- 方法：`PUT`
- 路径：`/api/admin/sales/shipments/{id}`
- 权限：`sales:shipment:update`
- 请求体：`SalesShipmentPayload`。
- 规则：
  - 仅 `DRAFT` 出库单可更新。
  - 已过账出库单返回 `SALES_SHIPMENT_POSTED_IMMUTABLE`。
  - 更新时重新校验订单状态、仓库、来源订单行、物料当前启用且可销售、单位和出库数量。
- 成功响应：`SalesShipmentDetail`。

### 过账销售出库

- 方法：`PUT`
- 路径：`/api/admin/sales/shipments/{id}/post`
- 权限：`sales:shipment:post`
- 规则：
  - 仅 `DRAFT` 出库单可过账。
  - 过账必须在单个数据库事务内完成。
  - 过账时锁定出库单、销售订单、订单行和库存余额。
  - 过账必须调用 `InventoryPostingService`，写入 `SALES_SHIPMENT` 库存流水。
  - 库存不足返回受控错误，且不得产生部分库存余额、部分流水、错误累计出库数量或错误出库状态。
  - 更新出库明细 `beforeQuantity`、`afterQuantity`。
  - 更新订单行已出库数量。
  - 推进销售订单状态为 `PARTIALLY_SHIPPED` 或 `SHIPPED`。
  - 重复过账返回 `SALES_DUPLICATE_POST` 或来源重复错误。
- 成功响应：`SalesShipmentDetail`。

## 权限编码

| 权限编码 | 类型 | 说明 |
|---|---|---|
| `sales` | 菜单 | 销售管理 |
| `sales:order:view` | 操作 | 查看销售订单 |
| `sales:order:create` | 操作 | 创建销售订单 |
| `sales:order:update` | 操作 | 更新销售订单草稿 |
| `sales:order:confirm` | 操作 | 确认销售订单 |
| `sales:order:cancel` | 操作 | 取消销售订单 |
| `sales:order:close` | 操作 | 关闭销售订单 |
| `sales:shipment:view` | 操作 | 查看销售出库 |
| `sales:shipment:create` | 操作 | 创建销售出库 |
| `sales:shipment:update` | 操作 | 更新销售出库草稿 |
| `sales:shipment:post` | 操作 | 过账销售出库 |
| `inventory:movement:view` | 操作 | 查看库存流水，销售详情内的来源流水摘要可随销售出库详情返回 |

## 错误码

| 错误码 | HTTP | 说明 |
|---|---|---|
| `SALES_ORDER_NOT_FOUND` | 404 | 销售订单不存在 |
| `SALES_SHIPMENT_NOT_FOUND` | 404 | 销售出库单不存在 |
| `SALES_ORDER_STATUS_INVALID` | 409 | 销售订单状态不允许当前操作 |
| `SALES_SHIPMENT_STATUS_INVALID` | 409 | 销售出库状态不允许当前操作 |
| `SALES_ORDER_EMPTY_LINES` | 400 | 销售订单明细不能为空 |
| `SALES_SHIPMENT_EMPTY_LINES` | 400 | 销售出库明细不能为空 |
| `SALES_CUSTOMER_INVALID` | 400 | 客户不存在或已停用 |
| `SALES_WAREHOUSE_INVALID` | 400 | 仓库不存在或已停用 |
| `SALES_MATERIAL_INVALID` | 400 | 物料不存在或已停用 |
| `SALES_MATERIAL_NOT_SELLABLE` | 400 | 物料类型不是 `FINISHED_GOOD` 或 `SEMI_FINISHED`，本阶段不可销售 |
| `SALES_UNIT_INVALID` | 400 | 单位不存在、已停用或不是物料基本单位 |
| `SALES_QUANTITY_INVALID` | 400 | 销售或出库数量不合法 |
| `SALES_UNIT_PRICE_INVALID` | 400 | 销售单价不合法 |
| `SALES_ORDER_DUPLICATE_LINE` | 409 | 销售订单明细重复 |
| `SALES_SHIPMENT_DUPLICATE_LINE` | 409 | 销售出库明细重复 |
| `SALES_SHIPMENT_EXCEEDS_ORDER` | 409 | 出库数量超过订单未出库数量 |
| `SALES_SHIPMENT_LINE_SOURCE_INVALID` | 409 | 出库明细来源订单行不匹配 |
| `SALES_STOCK_NOT_ENOUGH` | 409 | 销售出库库存不足 |
| `SALES_SHIPMENT_POSTED_IMMUTABLE` | 409 | 已过账销售出库不可编辑 |
| `SALES_DUPLICATE_POST` | 409 | 销售出库已过账或重复过账 |
| `SALES_MOVEMENT_SOURCE_DUPLICATED` | 409 | 来源明细已生成库存变动 |

## 库存过账与事务要求

- 销售出库过账必须调用 `InventoryPostingService.post`。
- 过账请求必须传入：
  - `movementType = SALES_SHIPMENT`
  - `direction = OUT`
  - `sourceType = SALES_SHIPMENT`
  - `sourceId = sal_sales_shipment.id`
  - `sourceLineId = sal_sales_shipment_line.id`
  - `businessDate = sal_sales_shipment.business_date`
  - `reason = 销售出库`
  - `operatorName = 当前操作人`
- `InventoryPostingService` 负责锁定或创建库存余额、校验扣减后库存不得为负、写入 `inv_stock_movement`、更新 `inv_stock_balance` 并返回过账前后数量。
- 过账必须单事务完成以下动作：
  - 锁定销售出库单。
  - 锁定来源销售订单。
  - 锁定来源销售订单明细。
  - 锁定库存余额。
  - 写库存流水。
  - 更新库存余额。
  - 回写销售出库明细 `before_quantity`、`after_quantity`。
  - 更新销售订单明细 `shipped_quantity`。
  - 推进销售订单状态。
  - 更新销售出库状态为 `POSTED`。
  - 写审计日志。
- 任一环节失败必须整体回滚，不得留下部分库存余额、部分库存流水、部分订单累计数量或错误单据状态。

## 约束与索引建议

- 销售订单号唯一。
- 销售出库单号唯一。
- 同一销售订单内行号唯一。
- 同一销售订单内物料不重复。
- 同一销售出库内行号唯一。
- 同一销售出库内来源订单行不重复。
- 销售数量和出库数量必须大于 0。
- 销售单价必须大于等于 0。
- 已出库数量必须满足 `0 <= shipped_quantity <= quantity`。
- 销售订单明细物料类型必须为 `FINISHED_GOOD` 或 `SEMI_FINISHED`，不新增单独可销售字段。
- 状态字段必须使用 check 约束限制枚举值。
- `inv_stock_movement` 的 `movement_type` check 需要加入 `SALES_SHIPMENT`，并保留既有类型。
- 常用索引至少覆盖：订单客户、订单状态日期、订单预计出库日期、订单明细物料、出库订单、出库客户、出库仓库、出库状态日期、出库明细来源订单行。

## 联调验收

- 管理员可以完成销售订单创建、编辑、确认、取消、关闭。
- 管理员可以基于已确认订单创建销售出库草稿并过账。
- 销售出库过账后库存余额扣减，库存流水来源为 `SALES_SHIPMENT`。
- 销售订单详情显示出库记录、已出库数量和未出库数量。
- 销售出库详情可追溯来源销售订单。
- 销售出库详情返回库存流水摘要；草稿出库返回空数组。
- 库存流水可追溯销售出库来源。
- 启用的 `RAW_MATERIAL`、`AUXILIARY` 创建或确认销售订单时返回 `SALES_MATERIAL_NOT_SELLABLE`，不生成销售订单有效状态变更。
- 已确认订单后客户停用不阻止销售出库创建或过账。
- 超出库、未确认订单出库、停用主数据、库存不足、重复过账、已过账编辑均返回明确业务错误，且失败操作不改变余额和流水。
- 销售角色、仓库角色、只读用户和无权限用户的菜单、按钮、路由和接口权限一致。
- 所有写操作都有审计日志。

## 明确排除范围

- 不实现应收、发票、收款、税务、正式凭证、收入确认、销售退货、物流发运、价格体系、审批。
- 不实现正式成本结转、利润核算或财务报表。
- 不新增物料主数据“可销售”字段，不扩大可销售物料范围到 `RAW_MATERIAL` 或 `AUXILIARY`。
- 不实现多币种、多组织、多公司、多账套、多租户。
- 不改变采购、生产、库存既有接口路径、响应结构、错误语义和权限语义。
