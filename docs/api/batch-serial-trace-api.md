# 批次、序列号与来源去向追溯接口契约

## 目标

定义 019 批次、序列号与来源去向追溯基础阶段的接口范围、字段、权限、错误码和联调验收规则。本契约承接 017 质量状态和 018 库存可用量、占用预留口径，要求批次/序列身份贯穿库存余额、库存流水、库存过账、质量确认、冻结解冻、退货反冲和追溯查询。

## 通用规则

- 所有接口使用统一 `ApiResponse<T>` 包装。
- 分页接口返回 `PageResponse<T>`。
- 写接口需要 CSRF token。
- 日期字段使用 `YYYY-MM-DD`，时间字段使用带时区的 ISO 8601 字符串。
- 数量字段后端使用 `BigDecimal` 和数据库 `NUMERIC(18,6)`；前端请求体中的业务数量必须使用字符串。
- 后端接口鉴权是最终安全边界，前端按钮和菜单控制只作为体验控制。
- 前端不得自行推导批次/序列可用量、可选上限或追溯链路。
- 追溯接口可返回来源摘要，但来源详情必须继续按采购、销售、生产、质量等原模块权限控制。
- 影响库存事实、质量状态、冻结状态、批次/序列身份的写入必须接入 `BusinessPeriodGuard`。

## 固定枚举

### 物料追踪方式

| 值 | 中文名 | 说明 |
|---|---|---|
| `NONE` | 不追踪 | 沿用仓库、物料、质量状态库存口径 |
| `BATCH` | 批次管理 | 同一物料按批次记录数量 |
| `SERIAL` | 序列号管理 | 每个序列号代表单件，数量固定为 1 |

### 追踪分配类型

| 值 | 中文名 | 说明 |
|---|---|---|
| `INBOUND` | 入库分配 | 采购入库、完工入库、退货入库、退料入库 |
| `OUTBOUND` | 出库分配 | 销售出库、生产领料、采购退货、生产补料 |
| `QUALITY_TRANSFER` | 质量转移 | 质量确认、冻结、解冻 |
| `SOURCE_INHERIT` | 来源继承 | 销售退货、生产退料继承原来源身份 |

### 追踪库存状态

| 值 | 中文名 | 说明 |
|---|---|---|
| `IN_STOCK` | 在库 | 当前存在库存余额 |
| `RESERVED` | 已预留 | 被物料级或追踪级预留覆盖 |
| `OCCUPIED` | 已占用 | 被执行单据占用 |
| `OUTBOUND` | 已出库 | 已销售出库、生产领用、采购退货或生产补料 |
| `CANCELLED` | 已作废 | 异常作废或来源取消 |

## 物料接口扩展

物料列表、详情、创建和更新扩展字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `trackingMethod` | string | 是 | `NONE`、`BATCH`、`SERIAL` |
| `trackingMethodName` | string | 响应必返 | 中文名 |

列表查询参数新增：

- `trackingMethod`

规则：

- 存量物料默认 `NONE`。
- 已有正库存、库存流水或活动预留的物料，不允许直接切换为 `BATCH` 或 `SERIAL`。
- 后端返回 `INVENTORY_TRACKING_METHOD_IMMUTABLE` 时，前端必须展示不可变更原因。

## 批次接口

### 批次分页

- 方法：`GET`
- 路径：`/api/admin/inventory/batches`
- 权限：`inventory:batch:view`
- 查询参数：
  - `keyword`
  - `materialId`
  - `warehouseId`
  - `qualityStatus`
  - `batchNo`
  - `sourceType`
  - `sourceId`
  - `onlyAvailable`
  - `page`
  - `pageSize`
- 成功响应：`PageResponse<InventoryBatchSummary>`。

### 批次详情

- 方法：`GET`
- 路径：`/api/admin/inventory/batches/{id}`
- 权限：`inventory:batch:view`
- 成功响应：`InventoryBatchDetail`。

### InventoryBatchSummary

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 批次标识 |
| `batchNo` | string | 批号 |
| `materialId`、`materialCode`、`materialName` | number/string/string | 物料 |
| `sourceType`、`sourceId`、`sourceLineId` | string/number/number | 来源 |
| `sourceDocumentNo` | string | 来源单号 |
| `businessDate` | string | 业务日期 |
| `quantityOnHand` | string | 当前现存 |
| `availableQuantity` | string | 当前可用 |
| `qualityStatusSummary` | array | 按质量状态汇总 |
| `updatedAt` | string | 更新时间 |

## 序列号接口

### 序列号分页

- 方法：`GET`
- 路径：`/api/admin/inventory/serials`
- 权限：`inventory:serial:view`
- 查询参数：
  - `keyword`
  - `materialId`
  - `warehouseId`
  - `qualityStatus`
  - `serialNo`
  - `batchId`
  - `sourceType`
  - `sourceId`
  - `onlyAvailable`
  - `page`
  - `pageSize`
- 成功响应：`PageResponse<InventorySerialSummary>`。

### 序列号详情

- 方法：`GET`
- 路径：`/api/admin/inventory/serials/{id}`
- 权限：`inventory:serial:view`
- 成功响应：`InventorySerialDetail`。

### InventorySerialSummary

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 序列标识 |
| `serialNo` | string | 序列号 |
| `materialId`、`materialCode`、`materialName` | number/string/string | 物料 |
| `batchId`、`batchNo` | number/string | 所属批次，可空 |
| `warehouseId`、`warehouseName` | number/string | 当前仓库 |
| `qualityStatus`、`qualityStatusName` | string/string | 当前质量状态 |
| `stockStatus`、`stockStatusName` | string/string | 当前库存状态 |
| `sourceType`、`sourceId`、`sourceLineId` | string/number/number | 来源 |
| `sourceDocumentNo` | string | 来源单号 |
| `updatedAt` | string | 更新时间 |

## 库存余额扩展

`GET /api/admin/inventory/balances` 查询参数新增：

- `trackingMethod`
- `batchId`
- `batchNo`
- `serialId`
- `serialNo`

响应新增或明确字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `trackingMethod` | string | 追踪方式 |
| `trackingMethodName` | string | 追踪方式中文名 |
| `batchId`、`batchNo` | number/string | 批次信息，可空 |
| `serialId`、`serialNo` | number/string | 序列号信息，可空 |
| `traceableQuantity` | string | 当前追踪身份数量 |
| `availableQuantity` | string | 仍表示现货净可用 |
| `reservedQuantity` | string | 活动预留剩余数量 |
| `occupiedQuantity` | string | 活动占用剩余数量 |

规则：

- `NONE` 物料不返回批次/序列字段。
- `BATCH` 物料可按批次展开或筛选。
- `SERIAL` 物料每个序列号行数量不得大于 1。
- `availableQuantity` 不得包含待检、不合格、冻结、已占用或已预留库存。

## 库存流水扩展

`GET /api/admin/inventory/movements` 查询参数新增：

- `trackingMethod`
- `batchId`
- `batchNo`
- `serialId`
- `serialNo`

响应新增：

- `trackingMethod`
- `batchId`
- `batchNo`
- `serialId`
- `serialNo`
- `sourceDocumentNo`
- `targetDocumentNo` 可空

一条业务行拆成多个批次或多个序列时，库存流水按追踪分配拆成多条，前端通过来源单据和来源行聚合展示。

## 追踪分配载荷

所有产生或消耗库存的业务行请求体可包含 `trackingAllocations`：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `batchId` | number | 批次出库时必填 | 批次标识 |
| `batchNo` | string | 批次入库时必填或由后端生成 | 批号 |
| `serialId` | number | 序列出库时必填 | 序列标识 |
| `serialNo` | string | 序列入库时必填 | 序列号 |
| `quantity` | string | 是 | 批次数量或序列数量 1 |
| `qualityStatus` | string | 否 | 默认按业务动作确定 |
| `sourceAllocationId` | number | 来源继承时必填 | 原出库或领料追踪分配 |

校验：

- 不追踪物料不得提交追踪分配。
- 批次物料必须提交批次分配，分配数量合计等于业务行数量。
- 序列号物料必须提交序列号分配，序列数量等于业务行数量，且每条数量为 1。
- 出库类分配必须来自当前仓库、当前物料、合格、未冻结且可用的追踪库存。
- 退货和退料的来源继承分配不得与原来源不一致。

## 追溯接口

### 批次追溯

- 方法：`GET`
- 路径：`/api/admin/inventory/traces/batches/{id}`
- 权限：`inventory:trace:view`
- 成功响应：`InventoryTraceDetail`。

### 序列追溯

- 方法：`GET`
- 路径：`/api/admin/inventory/traces/serials/{id}`
- 权限：`inventory:trace:view`
- 成功响应：`InventoryTraceDetail`。

### InventoryTraceDetail

| 字段 | 类型 | 说明 |
|---|---|---|
| `subject` | object | 批次或序列主档 |
| `currentBalances` | array | 当前结存，按仓库和质量状态 |
| `activeReservations` | array | 当前活动预留和占用 |
| `sourceRecords` | array | 来源入库、完工、退货或初始化 |
| `qualityEvents` | array | 质量确认、冻结、解冻 |
| `outboundRecords` | array | 销售出库、生产领料、采购退货、生产补料 |
| `returnRecords` | array | 销售退货、生产退料等反向业务 |
| `movements` | array | 关联库存流水 |
| `restrictedSources` | array | 因权限受限只展示摘要的来源 |

追溯节点字段建议：

- `nodeType`
- `nodeTypeName`
- `documentType`
- `documentId`
- `documentNo`
- `lineId`
- `businessDate`
- `direction`
- `quantity`
- `qualityStatus`
- `warehouseName`
- `operatorName`
- `routeName`
- `permissionRestricted`

## 权限编码

| 权限编码 | 类型 | 说明 |
|---|---|---|
| `inventory:batch:view` | 操作 | 查看批次列表和详情 |
| `inventory:serial:view` | 操作 | 查看序列号列表和详情 |
| `inventory:trace:view` | 操作 | 查看批次/序列来源去向追溯 |
| `inventory:balance:view` | 操作 | 查看库存余额 |
| `inventory:movement:view` | 操作 | 查看库存流水 |

批次/序列采集和修改沿用对应业务单据创建、更新、过账权限。

## 错误码

| 错误码 | HTTP | 场景 |
|---|---|---|
| `INVENTORY_TRACKING_METHOD_IMMUTABLE` | 409 | 已有库存、流水或活动预留的物料不允许切换追踪方式 |
| `INVENTORY_BATCH_REQUIRED` | 400 | 批次物料缺少批次分配 |
| `INVENTORY_SERIAL_REQUIRED` | 400 | 序列号物料缺少序列分配 |
| `INVENTORY_SERIAL_DUPLICATED` | 409 | 同一物料下序列号重复 |
| `INVENTORY_TRACKING_QUANTITY_MISMATCH` | 400 | 分配数量与业务行数量不一致 |
| `INVENTORY_TRACKING_STOCK_NOT_ENOUGH` | 409 | 指定批次或序列库存不足 |
| `INVENTORY_TRACKING_NOT_AVAILABLE` | 409 | 指定批次或序列不可用、非合格、冻结或已占用 |
| `INVENTORY_TRACKING_SOURCE_MISMATCH` | 409 | 退货或退料分配与原来源不一致 |
| `AUTH_FORBIDDEN` | 403 | 无权限 |
| `BUSINESS_PERIOD_LOCKED` | 409 | 业务日期所属期间已锁定 |

## 联调验收

- 物料追踪方式在列表、详情、创建和更新中一致。
- 批次入库后能在批次列表、库存余额、库存流水和批次追溯中查询。
- 序列号入库后每个序列号只有一条当前状态，重复序列号被拒绝。
- 销售出库和生产领料只能选择当前仓库可用批次/序列，过账后对应追踪余额减少。
- 销售退货和生产退料继承来源追踪身份，不允许任意改选。
- 质量确认、冻结、解冻不丢失批次/序列身份。
- 追溯接口展示来源、去向、质量状态变化和当前结存；无来源详情权限时脱敏但不报错。
- 锁定期间内写入被拒绝，追溯查询仍可访问。
