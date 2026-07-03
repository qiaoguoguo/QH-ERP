# 仓库与库存基础接口契约

## 目标

定义仓库与库存基础阶段的接口范围、字段、权限、错误码和联调验收规则，作为前后端实现与测试依据。本阶段只建立库存余额、库存变动流水、期初库存和库存调整能力，为后续生产工单、领料、完工入库和成本归集提供可信库存底座。

## 通用规则

- 所有接口使用统一 `ApiResponse<T>` 包装。
- 分页接口返回 `PageResponse<T>`。
- 时间字段使用后端生成的 ISO 8601 字符串。
- 写接口需要 CSRF token。
- 认证和权限沿用账号权限模块。
- 后端接口鉴权是最终安全边界，前端菜单和按钮权限只作为体验控制。
- 库存数量使用后端 `BigDecimal` 和数据库 `NUMERIC(18,6)`，禁止浮点数承载业务计算。
- 所有影响库存余额的动作必须通过库存单据过账产生库存变动流水，不允许直接修改余额。
- 写操作成功后必须生成审计日志，至少记录操作人、动作、目标类型、目标标识和操作时间。
- `VALIDATION_ERROR`、`AUTH_FORBIDDEN`、`AUTH_UNAUTHORIZED` 沿用账号权限模块或通用错误码规则。

## 状态与枚举

### 库存单据状态

| 值 | 说明 |
|---|---|
| `DRAFT` | 草稿，可编辑 |
| `POSTED` | 已过账，已写入余额和流水，不可编辑 |

### 库存单据类型

| 值 | 说明 |
|---|---|
| `OPENING` | 期初库存 |
| `ADJUSTMENT` | 库存调整 |

### 库存变动类型

| 值 | 说明 |
|---|---|
| `OPENING` | 期初入账 |
| `ADJUSTMENT_INCREASE` | 调增 |
| `ADJUSTMENT_DECREASE` | 调减 |

### 库存变动方向

| 值 | 说明 |
|---|---|
| `IN` | 增加库存 |
| `OUT` | 减少库存 |

## 字段定义

### 库存余额字段

| 字段 | 类型 | 请求必填 | 响应必返 | 说明 |
|---|---|---|---|---|
| id | number | 否 | 是 | 后端主键 |
| warehouseId | number | 否 | 是 | 仓库标识 |
| warehouseCode | string | 否 | 是 | 仓库编码 |
| warehouseName | string | 否 | 是 | 仓库名称 |
| materialId | number | 否 | 是 | 物料标识 |
| materialCode | string | 否 | 是 | 物料编码 |
| materialName | string | 否 | 是 | 物料名称 |
| materialSpec | string | 否 | 否 | 规格型号 |
| materialType | string | 否 | 是 | 物料类型 |
| unitId | number | 否 | 是 | 基本单位标识 |
| unitName | string | 否 | 是 | 基本单位名称 |
| quantityOnHand | number | 否 | 是 | 现存数量 |
| lockedQuantity | number | 否 | 是 | 锁定数量，一期固定为 0 |
| availableQuantity | number | 否 | 是 | 可用数量，一期等于现存数量 |
| updatedAt | string | 否 | 是 | 更新时间 |

### 库存变动字段

| 字段 | 类型 | 请求必填 | 响应必返 | 说明 |
|---|---|---|---|---|
| id | number | 否 | 是 | 后端主键 |
| movementNo | string | 否 | 是 | 变动编号，全局唯一 |
| movementType | string | 否 | 是 | `OPENING`、`ADJUSTMENT_INCREASE`、`ADJUSTMENT_DECREASE` |
| direction | string | 否 | 是 | `IN` 或 `OUT` |
| warehouseId | number | 否 | 是 | 仓库标识 |
| warehouseName | string | 否 | 是 | 仓库名称 |
| materialId | number | 否 | 是 | 物料标识 |
| materialCode | string | 否 | 是 | 物料编码 |
| materialName | string | 否 | 是 | 物料名称 |
| unitId | number | 否 | 是 | 单位标识 |
| unitName | string | 否 | 是 | 单位名称 |
| quantity | number | 否 | 是 | 本次变动数量，始终为正数 |
| beforeQuantity | number | 否 | 是 | 变动前数量 |
| afterQuantity | number | 否 | 是 | 变动后数量 |
| sourceType | string | 否 | 是 | 一期固定为 `INVENTORY_DOCUMENT` |
| sourceId | number | 否 | 是 | 来源库存单据标识 |
| sourceLineId | number | 否 | 是 | 来源库存单据明细标识 |
| businessDate | string | 否 | 是 | 业务日期 |
| reason | string | 否 | 否 | 变动原因 |
| remark | string | 否 | 否 | 备注 |
| operatorName | string | 否 | 是 | 操作人 |
| occurredAt | string | 否 | 是 | 发生时间 |

### 库存单据字段

| 字段 | 类型 | 请求必填 | 响应必返 | 说明 |
|---|---|---|---|---|
| id | number | 否 | 是 | 后端主键 |
| documentNo | string | 否 | 是 | 单据编号，全局唯一 |
| documentType | string | 是 | 是 | `OPENING` 或 `ADJUSTMENT` |
| status | string | 否 | 是 | `DRAFT` 或 `POSTED` |
| businessDate | string | 是 | 是 | 业务日期 |
| reason | string | 是 | 是 | 期初或调整原因 |
| remark | string | 否 | 否 | 备注 |
| lineCount | number | 否 | 是 | 明细行数量 |
| createdByName | string | 否 | 是 | 创建人 |
| createdAt | string | 否 | 是 | 创建时间 |
| updatedAt | string | 否 | 是 | 更新时间 |
| postedByName | string | 否 | 否 | 过账人 |
| postedAt | string | 否 | 否 | 过账时间 |
| lines | array | 是 | 是 | 单据明细 |

### 库存单据明细字段

| 字段 | 类型 | 请求必填 | 响应必返 | 说明 |
|---|---|---|---|---|
| id | number | 否 | 是 | 明细主键 |
| lineNo | number | 是 | 是 | 行号，同一单据内唯一 |
| warehouseId | number | 是 | 是 | 仓库标识 |
| warehouseName | string | 否 | 是 | 仓库名称 |
| materialId | number | 是 | 是 | 物料标识 |
| materialCode | string | 否 | 是 | 物料编码 |
| materialName | string | 否 | 是 | 物料名称 |
| unitId | number | 否 | 是 | 单位标识，一期使用物料基本单位 |
| unitName | string | 否 | 是 | 单位名称 |
| quantity | number | 是 | 是 | 期初数量或调整数量，必须大于 0 |
| adjustmentDirection | string | 条件必填 | 否 | 调整单必填，`INCREASE` 或 `DECREASE` |
| beforeQuantity | number | 否 | 否 | 过账时生成 |
| afterQuantity | number | 否 | 否 | 过账时生成 |
| remark | string | 否 | 否 | 备注 |

## 接口分组

| 资源 | 路径 | 能力 |
|---|---|---|
| 库存余额 | `/api/admin/inventory/balances` | list |
| 库存变动 | `/api/admin/inventory/movements` | list |
| 库存单据 | `/api/admin/inventory/documents` | list/get/create/update/post |

## 接口定义

### 库存余额分页列表

- 方法：`GET`
- 路径：`/api/admin/inventory/balances`
- 权限：`inventory:balance:view`
- 查询参数：
  - `page`：页码，从 1 开始。
  - `pageSize`：每页条数。
  - `keyword`：物料编码、物料名称或仓库名称。
  - `warehouseId`：仓库标识。
  - `materialId`：物料标识。
  - `materialType`：物料类型。
  - `onlyPositive`：是否只看现存数量大于 0 的记录。
- 成功响应：`PageResponse<InventoryBalanceSummary>`。

### 库存变动分页列表

- 方法：`GET`
- 路径：`/api/admin/inventory/movements`
- 权限：`inventory:movement:view`
- 查询参数：
  - `page`、`pageSize`。
  - `keyword`：物料、仓库或变动编号关键词。
  - `warehouseId`、`materialId`。
  - `movementType`：变动类型。
  - `direction`：变动方向。
  - `dateFrom`、`dateTo`：业务日期范围。
- 成功响应：`PageResponse<InventoryMovementSummary>`。

### 库存单据分页列表

- 方法：`GET`
- 路径：`/api/admin/inventory/documents`
- 权限：`inventory:document:view`
- 查询参数：
  - `page`、`pageSize`。
  - `keyword`：单据编号、原因或备注关键词。
  - `documentType`：`OPENING` 或 `ADJUSTMENT`。
  - `status`：`DRAFT` 或 `POSTED`。
  - `dateFrom`、`dateTo`：业务日期范围。
- 成功响应：`PageResponse<InventoryDocumentSummary>`。

### 库存单据详情

- 方法：`GET`
- 路径：`/api/admin/inventory/documents/{id}`
- 权限：`inventory:document:view`
- 成功响应：`InventoryDocumentDetail`。
- 不存在返回：`INVENTORY_DOCUMENT_NOT_FOUND`。

### 创建库存单据

- 方法：`POST`
- 路径：`/api/admin/inventory/documents`
- 权限：`inventory:document:create`
- 请求体：`InventoryDocumentPayload`。
- 规则：
  - 新建单据状态固定为 `DRAFT`。
  - 明细不能为空。
  - 期初单明细数量必须大于 0。
  - 调整单明细数量必须大于 0，且必须指定 `adjustmentDirection`。
  - 同一单据内不允许重复的仓库和物料组合。
  - 仓库、物料和单位必须存在且启用。
- 成功响应：`InventoryDocumentDetail`。

### 更新库存单据

- 方法：`PUT`
- 路径：`/api/admin/inventory/documents/{id}`
- 权限：`inventory:document:update`
- 请求体：`InventoryDocumentPayload`。
- 规则：
  - 仅 `DRAFT` 单据可更新。
  - `POSTED` 单据返回 `INVENTORY_DOCUMENT_POSTED_IMMUTABLE`。
  - 更新时替换明细，以请求体为准。
- 成功响应：`InventoryDocumentDetail`。

### 过账库存单据

- 方法：`PUT`
- 路径：`/api/admin/inventory/documents/{id}/post`
- 权限：`inventory:document:post`
- 规则：
  - 仅 `DRAFT` 单据可过账。
  - 过账必须在单个数据库事务内完成：校验单据、锁定余额、写入变动流水、更新余额、写审计。
  - 期初单同一仓库物料只能过账一次，重复返回 `INVENTORY_OPENING_EXISTS`。
  - 调减后库存不得小于 0，失败返回 `INVENTORY_STOCK_NOT_ENOUGH`。
  - 同一单据明细只能生成一次变动流水，重复提交返回 `INVENTORY_DUPLICATE_POST` 或保持幂等拒绝。
- 成功响应：`InventoryDocumentDetail`。

## 权限编码

| 权限编码 | 类型 | 说明 |
|---|---|---|
| `inventory` | 菜单 | 库存管理 |
| `inventory:balance:view` | 操作 | 查看库存余额 |
| `inventory:movement:view` | 操作 | 查看库存变动 |
| `inventory:document:view` | 操作 | 查看库存单据 |
| `inventory:document:create` | 操作 | 创建库存单据 |
| `inventory:document:update` | 操作 | 更新库存单据 |
| `inventory:document:post` | 操作 | 过账库存单据 |

## 错误码

| 错误码 | HTTP | 说明 |
|---|---|---|
| `INVENTORY_DOCUMENT_NOT_FOUND` | 404 | 库存单据不存在 |
| `INVENTORY_DOCUMENT_TYPE_INVALID` | 400 | 库存单据类型不正确 |
| `INVENTORY_DOCUMENT_STATUS_INVALID` | 409 | 库存单据状态不允许当前操作 |
| `INVENTORY_DOCUMENT_POSTED_IMMUTABLE` | 409 | 已过账单据不可编辑 |
| `INVENTORY_DOCUMENT_EMPTY_LINES` | 400 | 库存单据明细不能为空 |
| `INVENTORY_DOCUMENT_DUPLICATE_LINE` | 409 | 库存单据明细重复 |
| `INVENTORY_QUANTITY_INVALID` | 400 | 库存数量不正确 |
| `INVENTORY_STOCK_NOT_ENOUGH` | 409 | 库存不足，调减后库存不能小于 0 |
| `INVENTORY_WAREHOUSE_INVALID` | 400 | 仓库不存在或已停用 |
| `INVENTORY_MATERIAL_INVALID` | 400 | 物料不存在或已停用 |
| `INVENTORY_UNIT_INVALID` | 400 | 单位不存在、已停用或不是物料基本单位 |
| `INVENTORY_OPENING_EXISTS` | 409 | 同一仓库物料已存在已过账期初 |
| `INVENTORY_DUPLICATE_POST` | 409 | 库存单据已过账或重复过账 |
| `INVENTORY_MOVEMENT_SOURCE_DUPLICATED` | 409 | 来源明细已生成库存变动 |

## 联调验收

- 管理员可以完成期初单创建、更新、过账、余额查询和变动查询。
- 管理员可以完成调增和调减调整，余额和流水同步变化。
- 调减超过现存数量时，后端拒绝并保持余额不变。
- 同一仓库物料重复期初过账被拒绝。
- 只读用户可以查看余额、流水和单据详情，不能创建、更新或过账。
- 无权限用户访问库存接口返回明确认证或鉴权错误。
- 所有过账动作都有库存变动流水和审计日志。
