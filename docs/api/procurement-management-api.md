# 采购管理基础接口契约

## 目标

定义采购管理基础阶段的接口范围、字段、权限、错误码和联调验收规则，作为前后端实现与测试依据。本阶段只建立采购订单、采购入库、库存增加和来源追溯，不提供应付、发票、付款、审批、MRP、质检入库、采购退货或正式财务凭证能力。

## 通用规则

- 所有接口使用统一 `ApiResponse<T>` 包装。
- 分页接口返回 `PageResponse<T>`。
- 时间字段使用后端生成的 ISO 8601 字符串。
- 写接口需要 CSRF token。
- 认证和权限沿用账号权限模块。
- 后端接口鉴权是最终安全边界，前端菜单和按钮权限只作为体验控制。
- 采购数量、入库数量、采购单价使用后端 `BigDecimal` 和数据库 `NUMERIC(18,6)`，禁止浮点数承载业务计算。
- 本阶段采购单价只表示采购业务字段，不代表应付、税额、付款、凭证或正式成本核算结果。
- 采购入库过账必须生成库存流水，不允许直接修改库存余额后缺失流水。
- 采购订单、采购入库和库存流水必须可通过来源字段追溯。
- 写操作成功后必须生成审计日志，至少记录操作人、动作、目标类型、目标标识和操作时间。
- `VALIDATION_ERROR`、`AUTH_FORBIDDEN`、`AUTH_UNAUTHORIZED` 沿用账号权限模块或通用错误码规则。

## 状态与枚举

### 采购订单状态

| 值 | 说明 |
|---|---|
| `DRAFT` | 草稿，可编辑、可确认、可取消，不允许入库 |
| `CONFIRMED` | 已确认，可创建入库，可关闭 |
| `PARTIALLY_RECEIVED` | 部分入库，可继续入库，可关闭 |
| `RECEIVED` | 全部入库，不允许继续入库 |
| `CLOSED` | 已关闭，不允许入库 |
| `CANCELLED` | 已取消，不允许入库 |

### 采购入库状态

| 值 | 说明 |
|---|---|
| `DRAFT` | 草稿，可编辑、可过账 |
| `POSTED` | 已过账，已写库存余额和库存流水，不可编辑 |

### 采购库存变动类型

| 值 | 说明 |
|---|---|
| `PURCHASE_RECEIPT` | 采购入库 |

## 字段定义

### 采购订单汇总字段

| 字段 | 类型 | 请求必填 | 响应必返 | 说明 |
|---|---|---|---|---|
| id | number | 否 | 是 | 后端主键 |
| orderNo | string | 否 | 是 | 采购订单号，后端生成 |
| supplierId | number | 是 | 是 | 供应商标识 |
| supplierCode | string | 否 | 是 | 供应商编码 |
| supplierName | string | 否 | 是 | 供应商名称 |
| orderDate | string | 是 | 是 | 订单日期 |
| expectedArrivalDate | string | 否 | 否 | 默认预计到货日期 |
| status | string | 否 | 是 | 采购订单状态 |
| lineCount | number | 否 | 是 | 明细行数量 |
| totalQuantity | number | 否 | 是 | 采购总数量 |
| receivedQuantity | number | 否 | 是 | 已入库总数量 |
| remainingQuantity | number | 否 | 是 | 未入库总数量 |
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

### 采购订单明细字段

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
| quantity | string | 是 | 是 | 采购数量，必须大于 0 |
| receivedQuantity | number | 否 | 是 | 已入库数量 |
| remainingQuantity | number | 否 | 是 | 未入库数量 |
| unitPrice | string | 是 | 是 | 采购单价，必须大于等于 0 |
| expectedArrivalDate | string | 否 | 否 | 行预计到货日期 |
| remark | string | 否 | 否 | 备注 |

### 采购订单详情字段

详情响应在采购订单汇总字段基础上增加：

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| lines | array | 是 | 采购订单明细 |
| receipts | array | 是 | 采购入库记录摘要 |
| inventoryMovements | array | 否 | 采购来源库存流水摘要 |
| auditSummary | array | 否 | 审计摘要 |

### 采购入库汇总字段

| 字段 | 类型 | 请求必填 | 响应必返 | 说明 |
|---|---|---|---|---|
| id | number | 否 | 是 | 后端主键 |
| receiptNo | string | 否 | 是 | 采购入库单号，后端生成 |
| orderId | number | 是 | 是 | 来源采购订单 |
| orderNo | string | 否 | 是 | 来源采购订单号 |
| supplierId | number | 否 | 是 | 供应商标识 |
| supplierName | string | 否 | 是 | 供应商名称 |
| warehouseId | number | 是 | 是 | 入库仓库 |
| warehouseName | string | 否 | 是 | 入库仓库名称 |
| businessDate | string | 是 | 是 | 入库业务日期 |
| status | string | 否 | 是 | 采购入库状态 |
| lineCount | number | 否 | 是 | 明细行数量 |
| totalQuantity | number | 否 | 是 | 入库总数量 |
| remark | string | 否 | 否 | 备注 |
| createdByName | string | 否 | 是 | 创建人 |
| createdAt | string | 否 | 是 | 创建时间 |
| updatedAt | string | 否 | 是 | 更新时间 |
| postedByName | string | 否 | 否 | 过账人 |
| postedAt | string | 否 | 否 | 过账时间 |

### 采购入库明细字段

| 字段 | 类型 | 请求必填 | 响应必返 | 说明 |
|---|---|---|---|---|
| id | number | 否 | 是 | 后端主键 |
| lineNo | number | 是 | 是 | 行号 |
| orderLineId | number | 是 | 是 | 来源采购订单明细 |
| materialId | number | 否 | 是 | 物料标识，从订单明细带出 |
| materialCode | string | 否 | 是 | 物料编码 |
| materialName | string | 否 | 是 | 物料名称 |
| unitId | number | 否 | 是 | 单位标识 |
| unitName | string | 否 | 是 | 单位名称 |
| orderedQuantity | number | 否 | 是 | 订单数量 |
| receivedQuantityBefore | number | 否 | 是 | 本次入库前订单行已入库数量 |
| remainingQuantityBefore | number | 否 | 是 | 本次入库前订单行未入库数量 |
| quantity | string | 是 | 是 | 本次入库数量，必须大于 0 |
| beforeQuantity | number | 否 | 否 | 过账前库存 |
| afterQuantity | number | 否 | 否 | 过账后库存 |
| remark | string | 否 | 否 | 备注 |

### 采购入库详情字段

详情响应在采购入库汇总字段基础上增加：

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| lines | array | 是 | 采购入库明细 |
| orderSummary | object | 是 | 来源采购订单摘要 |
| inventoryMovements | array | 否 | 入库产生的库存流水摘要 |
| auditSummary | array | 否 | 审计摘要 |

## 接口分组

| 资源 | 路径 | 能力 |
|---|---|---|
| 采购订单 | `/api/admin/procurement/orders` | list/get/create/update/confirm/cancel/close |
| 采购入库 | `/api/admin/procurement/receipts` | list/get/update/post |
| 订单入库 | `/api/admin/procurement/orders/{id}/receipts` | create |

## 接口定义

### 采购订单分页列表

- 方法：`GET`
- 路径：`/api/admin/procurement/orders`
- 权限：`procurement:order:view`
- 查询参数：
  - `page`、`pageSize`。
  - `keyword`：订单号、供应商编码、供应商名称、物料编码或物料名称。
  - `supplierId`：供应商标识。
  - `status`：采购订单状态。
  - `dateFrom`、`dateTo`：订单日期范围。
  - `expectedDateFrom`、`expectedDateTo`：预计到货日期范围。
- 成功响应：`PageResponse<PurchaseOrderSummary>`。

### 采购订单详情

- 方法：`GET`
- 路径：`/api/admin/procurement/orders/{id}`
- 权限：`procurement:order:view`
- 成功响应：`PurchaseOrderDetail`。
- 不存在返回：`PROCUREMENT_ORDER_NOT_FOUND`。

### 创建采购订单

- 方法：`POST`
- 路径：`/api/admin/procurement/orders`
- 权限：`procurement:order:create`
- 请求体：`PurchaseOrderPayload`。
- 规则：
  - 新建状态固定为 `DRAFT`。
  - 明细不能为空。
  - 供应商必须存在且启用。
  - 物料必须存在、启用且可采购。
  - 单位使用物料基本单位。
  - 采购数量必须大于 0。
  - 采购单价必须大于等于 0。
  - 同一订单内不允许重复物料行。
- 成功响应：`PurchaseOrderDetail`。

### 更新采购订单

- 方法：`PUT`
- 路径：`/api/admin/procurement/orders/{id}`
- 权限：`procurement:order:update`
- 请求体：`PurchaseOrderPayload`。
- 规则：
  - 仅 `DRAFT` 订单可更新。
  - 更新时替换明细，以请求体为准。
- 成功响应：`PurchaseOrderDetail`。

### 确认采购订单

- 方法：`PUT`
- 路径：`/api/admin/procurement/orders/{id}/confirm`
- 权限：`procurement:order:confirm`
- 规则：
  - 仅 `DRAFT` 订单可确认。
  - 确认时重新校验供应商、物料、单位、数量和采购单价。
  - 明细不能为空。
- 成功响应：`PurchaseOrderDetail`。

### 取消采购订单

- 方法：`PUT`
- 路径：`/api/admin/procurement/orders/{id}/cancel`
- 权限：`procurement:order:cancel`
- 规则：
  - `DRAFT` 订单可取消。
  - 无已过账入库的 `CONFIRMED` 订单可取消。
  - 已发生入库的订单不可取消，可关闭未入库余量。
- 成功响应：`PurchaseOrderDetail`。

### 关闭采购订单

- 方法：`PUT`
- 路径：`/api/admin/procurement/orders/{id}/close`
- 权限：`procurement:order:close`
- 规则：
  - `CONFIRMED`、`PARTIALLY_RECEIVED` 或 `RECEIVED` 订单可关闭。
  - `DRAFT`、`CANCELLED` 订单不可关闭。
  - 关闭后不允许继续入库。
- 成功响应：`PurchaseOrderDetail`。

### 采购入库分页列表

- 方法：`GET`
- 路径：`/api/admin/procurement/receipts`
- 权限：`procurement:receipt:view`
- 查询参数：
  - `page`、`pageSize`。
  - `keyword`：入库单号、订单号、供应商编码、供应商名称、物料编码或物料名称。
  - `supplierId`：供应商标识。
  - `warehouseId`：仓库标识。
  - `status`：采购入库状态。
  - `dateFrom`、`dateTo`：业务日期范围。
  - `orderId`：来源采购订单标识。
- 成功响应：`PageResponse<PurchaseReceiptSummary>`。

### 基于采购订单创建采购入库

- 方法：`POST`
- 路径：`/api/admin/procurement/orders/{id}/receipts`
- 权限：`procurement:receipt:create`
- 请求体：`PurchaseReceiptPayload`。
- 规则：
  - 采购订单必须为 `CONFIRMED` 或 `PARTIALLY_RECEIVED`。
  - 入库仓库必须存在且启用。
  - 明细必须引用来源采购订单明细。
  - 入库数量必须大于 0，且不得超过订单行未入库数量。
  - 来源订单行、物料和单位必须一致。
- 成功响应：`PurchaseReceiptDetail`。

### 采购入库详情

- 方法：`GET`
- 路径：`/api/admin/procurement/receipts/{id}`
- 权限：`procurement:receipt:view`
- 成功响应：`PurchaseReceiptDetail`。
- 不存在返回：`PROCUREMENT_RECEIPT_NOT_FOUND`。

### 更新采购入库

- 方法：`PUT`
- 路径：`/api/admin/procurement/receipts/{id}`
- 权限：`procurement:receipt:update`
- 请求体：`PurchaseReceiptPayload`。
- 规则：
  - 仅 `DRAFT` 入库单可更新。
  - 已过账入库单返回 `PROCUREMENT_RECEIPT_POSTED_IMMUTABLE`。
  - 更新时重新校验订单状态、仓库、来源订单行和入库数量。
- 成功响应：`PurchaseReceiptDetail`。

### 过账采购入库

- 方法：`PUT`
- 路径：`/api/admin/procurement/receipts/{id}/post`
- 权限：`procurement:receipt:post`
- 规则：
  - 仅 `DRAFT` 入库单可过账。
  - 过账必须在单个数据库事务内完成。
  - 过账时锁定入库单、采购订单、订单行和库存余额。
  - 过账成功后生成 `PURCHASE_RECEIPT` 库存流水。
  - 更新订单行已入库数量。
  - 推进采购订单状态为 `PARTIALLY_RECEIVED` 或 `RECEIVED`。
  - 重复过账返回 `PROCUREMENT_DUPLICATE_POST` 或来源重复错误。
- 成功响应：`PurchaseReceiptDetail`。

## 权限编码

| 权限编码 | 类型 | 说明 |
|---|---|---|
| `procurement` | 菜单 | 采购管理 |
| `procurement:order:view` | 操作 | 查看采购订单 |
| `procurement:order:create` | 操作 | 创建采购订单 |
| `procurement:order:update` | 操作 | 更新采购订单草稿 |
| `procurement:order:confirm` | 操作 | 确认采购订单 |
| `procurement:order:cancel` | 操作 | 取消采购订单 |
| `procurement:order:close` | 操作 | 关闭采购订单 |
| `procurement:receipt:view` | 操作 | 查看采购入库 |
| `procurement:receipt:create` | 操作 | 创建采购入库 |
| `procurement:receipt:update` | 操作 | 更新采购入库草稿 |
| `procurement:receipt:post` | 操作 | 过账采购入库 |

## 错误码

| 错误码 | HTTP | 说明 |
|---|---|---|
| `PROCUREMENT_ORDER_NOT_FOUND` | 404 | 采购订单不存在 |
| `PROCUREMENT_RECEIPT_NOT_FOUND` | 404 | 采购入库单不存在 |
| `PROCUREMENT_ORDER_STATUS_INVALID` | 409 | 采购订单状态不允许当前操作 |
| `PROCUREMENT_RECEIPT_STATUS_INVALID` | 409 | 采购入库状态不允许当前操作 |
| `PROCUREMENT_ORDER_EMPTY_LINES` | 400 | 采购订单明细不能为空 |
| `PROCUREMENT_RECEIPT_EMPTY_LINES` | 400 | 采购入库明细不能为空 |
| `PROCUREMENT_SUPPLIER_INVALID` | 400 | 供应商不存在或已停用 |
| `PROCUREMENT_WAREHOUSE_INVALID` | 400 | 仓库不存在或已停用 |
| `PROCUREMENT_MATERIAL_INVALID` | 400 | 物料不存在、已停用或不可采购 |
| `PROCUREMENT_UNIT_INVALID` | 400 | 单位不存在、已停用或不是物料基本单位 |
| `PROCUREMENT_QUANTITY_INVALID` | 400 | 采购或入库数量不合法 |
| `PROCUREMENT_UNIT_PRICE_INVALID` | 400 | 采购单价不合法 |
| `PROCUREMENT_ORDER_DUPLICATE_LINE` | 409 | 采购订单明细重复 |
| `PROCUREMENT_RECEIPT_DUPLICATE_LINE` | 409 | 采购入库明细重复 |
| `PROCUREMENT_RECEIPT_EXCEEDS_ORDER` | 409 | 入库数量超过订单未入库数量 |
| `PROCUREMENT_RECEIPT_LINE_SOURCE_INVALID` | 409 | 入库明细来源订单行不匹配 |
| `PROCUREMENT_RECEIPT_POSTED_IMMUTABLE` | 409 | 已过账采购入库不可编辑 |
| `PROCUREMENT_DUPLICATE_POST` | 409 | 采购入库已过账或重复过账 |
| `PROCUREMENT_MOVEMENT_SOURCE_DUPLICATED` | 409 | 来源明细已生成库存变动 |

## 联调验收

- 管理员可以完成采购订单创建、编辑、确认、取消、关闭。
- 管理员可以基于已确认订单创建采购入库草稿并过账。
- 采购入库过账后库存余额增加，库存流水来源为 `PURCHASE_RECEIPT`。
- 采购订单详情显示入库记录、已入库数量和未入库数量。
- 采购入库详情可追溯来源采购订单。
- 库存流水可追溯采购入库来源。
- 超入库、未确认订单入库、停用主数据、重复过账、已过账编辑均返回明确业务错误，且失败操作不改变余额和流水。
- 采购员、仓库角色、只读用户和无权限用户的菜单、按钮、路由和接口权限一致。
- 所有写操作都有审计日志。
