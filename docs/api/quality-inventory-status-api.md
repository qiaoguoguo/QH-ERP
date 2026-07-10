# 质量检验与库存质量状态基础接口契约

## 目标

定义 017 质量检验与库存质量状态基础阶段的接口范围、字段、权限、错误码和联调验收规则，作为 Task 2 至 Task 4 的后端、前端和测试共同契约。

本阶段只建立库存质量状态基础能力：库存余额和库存流水持久化质量状态，`availableQuantity` 明确表示合格可用库存，待检、不合格和冻结库存不得被销售出库、生产领料、生产补料或后续计划净算当作可用库存。本阶段不实现完整 QMS、SPC、批次、库位、MRP、审批流或正式财务。

## 通用规则

- 所有接口使用统一 `ApiResponse<T>` 包装。
- 分页接口返回 `PageResponse<T>`。
- 写接口需要 CSRF token。
- 认证和权限沿用账号权限模块，后端接口鉴权是最终安全边界。
- 日期字段使用 `YYYY-MM-DD`，时间字段使用带时区的 ISO 8601 字符串。
- 质量确认、冻结、解冻和质量状态库存写入必须接入 016 `BusinessPeriodGuard` 写入保护。
- 质量相关写接口的数量请求字段必须使用字符串，后端按 `BigDecimal` 和数据库 `NUMERIC(18,6)` 解析，禁止前端用浮点数承载业务计算。
- 质量状态变更必须通过库存服务生成库存流水，不允许接口或质量领域直接改库存余额。
- 质量确认和质量状态转换必须在单个数据库事务内完成余额、流水、单据状态和审计写入。
- 影响库存事实的写操作成功后必须生成审计日志，至少记录操作人、动作、目标类型、目标标识、业务日期、原因和操作时间。

## 固定枚举

### 库存质量状态

| 值 | 中文名 | 是否普通可用 | 说明 |
|---|---|---|---|
| `PENDING_INSPECTION` | 待检 | 否 | 入库后尚未质量确认的受控库存 |
| `QUALIFIED` | 合格 | 是 | 销售出库、生产领料、生产补料和后续计划净算唯一可用库存口径 |
| `REJECTED` | 不合格 | 否 | 已确认不可用于普通消耗的隔离库存 |
| `FROZEN` | 冻结 | 否 | 人工冻结或质量原因冻结的库存，需先解冻后才能参与允许路径 |

前端展示状态时使用后端返回的 `qualityStatusName`，不得只依赖颜色表达状态。

```json
{
  "qualityStatus": "PENDING_INSPECTION",
  "qualityStatusName": "待检"
}
```

### 质量确认状态

| 值 | 中文名 | 说明 |
|---|---|---|
| `PENDING` | 待处理 | 已形成待检库存，尚未提交质量确认 |
| `COMPLETED` | 已处理 | 已完成待检转合格、不合格或冻结 |

### 质量来源类型

| 值 | 中文名 | 默认入库质量状态 |
|---|---|---|
| `PURCHASE_RECEIPT` | 采购入库 | `PENDING_INSPECTION` |
| `PRODUCTION_COMPLETION` | 完工入库 | `PENDING_INSPECTION` |
| `SALES_RETURN` | 销售退货 | `PENDING_INSPECTION` |
| `PRODUCTION_RETURN` | 生产退料 | `PENDING_INSPECTION` |

## 接口分组

| 资源 | 路径 | 能力 |
|---|---|---|
| 质量确认 | `/api/admin/quality/inspections` | list/get/process |
| 质量状态转换 | `/api/admin/inventory/quality-transfers` | freeze/unfreeze |
| 库存余额 | `/api/admin/inventory/balances` | 按质量状态查询和展示合格可用量 |
| 库存流水 | `/api/admin/inventory/movements` | 按质量状态查询和追溯来源流水 |

## 核心接口清单

```text
GET /api/admin/quality/inspections
GET /api/admin/quality/inspections/{id}
POST /api/admin/quality/inspections/{id}/process
POST /api/admin/inventory/quality-transfers/freeze
POST /api/admin/inventory/quality-transfers/unfreeze
GET /api/admin/inventory/balances?qualityStatus=QUALIFIED
GET /api/admin/inventory/movements?qualityStatus=PENDING_INSPECTION
```

## 字段定义

### 质量确认摘要字段

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 质量确认记录标识 |
| `inspectionNo` | string | 是 | 质量确认单号 |
| `sourceType` | string | 是 | 来源类型 |
| `sourceTypeName` | string | 是 | 来源类型中文名 |
| `sourceId` | number | 是 | 来源单据标识 |
| `sourceLineId` | number | 否 | 来源行标识 |
| `sourceDocumentNo` | string | 是 | 来源单据编号 |
| `warehouseId` | number | 是 | 仓库标识 |
| `warehouseCode` | string | 是 | 仓库编码 |
| `warehouseName` | string | 是 | 仓库名称 |
| `materialId` | number | 是 | 物料标识 |
| `materialCode` | string | 是 | 物料编码 |
| `materialName` | string | 是 | 物料名称 |
| `materialSpec` | string | 否 | 规格型号 |
| `unitId` | number | 是 | 单位标识 |
| `unitName` | string | 是 | 单位名称 |
| `inspectionQuantity` | string | 是 | 待检总数量 |
| `remainingQuantity` | string | 是 | 当前仍待处理数量 |
| `qualifiedQuantity` | string | 是 | 已确认合格数量 |
| `rejectedQuantity` | string | 是 | 已确认不合格数量 |
| `frozenQuantity` | string | 是 | 已确认冻结数量 |
| `status` | string | 是 | `PENDING` 或 `COMPLETED` |
| `statusName` | string | 是 | 状态中文名 |
| `businessDate` | string | 是 | 入库或质量确认业务日期 |
| `createdByName` | string | 是 | 创建人 |
| `createdAt` | string | 是 | 创建时间 |
| `completedByName` | string | 否 | 处理人 |
| `completedAt` | string | 否 | 处理时间 |
| `reason` | string | 否 | 处理原因 |
| `remark` | string | 否 | 备注 |
| `version` | number | 是 | 乐观版本 |
| `canProcess` | boolean | 是 | 当前用户和状态下是否可处理 |
| `disabledReason` | string | 否 | 不可处理原因，供前端展示 |

### 质量确认详情扩展字段

详情响应除摘要字段外，还必须返回来源上下文和审计字段：

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| `sourceSummary` | object | 是 | 来源单据摘要，包含业务单号、供应商或客户、生产工单等可展示字段 |
| `currentQualityStatus` | string | 是 | 待处理库存当前状态，质量确认固定为 `PENDING_INSPECTION` |
| `currentQualityStatusName` | string | 是 | 当前质量状态中文名 |
| `auditRecords` | array | 是 | 创建、处理、冻结或解冻等审计记录 |

`auditRecords` 元素字段：

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| `action` | string | 是 | 动作编码 |
| `actionName` | string | 是 | 动作中文名 |
| `operatorName` | string | 是 | 操作人 |
| `operatedAt` | string | 是 | 操作时间 |
| `businessDate` | string | 否 | 业务日期 |
| `reason` | string | 否 | 原因 |
| `remark` | string | 否 | 备注 |

### 库存余额质量状态字段

`GET /api/admin/inventory/balances` 在原库存余额字段基础上扩展以下字段：

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| `qualityStatus` | string | 是 | 当前余额行的质量状态 |
| `qualityStatusName` | string | 是 | 质量状态中文名 |
| `quantityOnHand` | string | 是 | 当前质量状态现存数量 |
| `availableQuantity` | string | 是 | 合格可用数量；当 `qualityStatus` 不是 `QUALIFIED` 时必须为 `0.000000` |
| `totalQuantityOnHand` | string | 是 | 同仓库、物料所有质量状态现存数量合计 |
| `pendingInspectionQuantity` | string | 是 | 待检数量 |
| `qualifiedQuantity` | string | 是 | 合格数量 |
| `rejectedQuantity` | string | 是 | 不合格数量 |
| `frozenQuantity` | string | 是 | 冻结数量 |
| `unavailableReason` | string | 否 | 非合格状态不可用原因 |

当不传 `qualityStatus` 时，接口可以按仓库、物料聚合返回一行，但必须包含各质量状态数量和合格可用量；当传入 `qualityStatus` 时，返回指定质量状态维度的余额行。

### 库存流水质量状态字段

`GET /api/admin/inventory/movements` 在原库存流水字段基础上扩展以下字段：

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| `qualityStatus` | string | 是 | 该笔流水影响的质量状态 |
| `qualityStatusName` | string | 是 | 质量状态中文名 |
| `quantity` | string | 是 | 变动数量，始终为正数 |
| `beforeQuantity` | string | 是 | 当前质量状态变动前数量 |
| `afterQuantity` | string | 是 | 当前质量状态变动后数量 |
| `sourceType` | string | 是 | 来源类型 |
| `sourceId` | number | 是 | 来源单据标识 |
| `sourceLineId` | number | 否 | 来源行标识 |
| `sourceDocumentNo` | string | 否 | 来源单据编号 |
| `relatedMovementId` | number | 否 | 质量状态转换的对应流水标识 |

## 接口定义

### 质量确认分页列表

- 方法：`GET`
- 路径：`/api/admin/quality/inspections`
- 权限：`quality:inspection:view`
- 查询参数：
  - `page`：页码，从 1 开始。
  - `pageSize`：每页条数。
  - `keyword`：质量确认单号、来源单号、物料编码或物料名称。
  - `sourceType`：来源类型。
  - `status`：`PENDING` 或 `COMPLETED`。
  - `warehouseId`：仓库标识。
  - `materialId`：物料标识。
  - `businessDateFrom`、`businessDateTo`：业务日期范围。
  - `qualityStatus`：可选，质量确认列表默认关注 `PENDING_INSPECTION`。
- 成功响应：`PageResponse<QualityInspectionSummary>`。

成功响应示例：

```json
{
  "success": true,
  "code": "OK",
  "message": "成功",
  "data": {
    "items": [
      {
        "id": 101,
        "inspectionNo": "QI202607100001",
        "sourceType": "PURCHASE_RECEIPT",
        "sourceTypeName": "采购入库",
        "sourceId": 301,
        "sourceLineId": 30101,
        "sourceDocumentNo": "PR202607100001",
        "warehouseId": 1,
        "warehouseCode": "RAW",
        "warehouseName": "原料仓",
        "materialId": 11,
        "materialCode": "MAT-001",
        "materialName": "铝型材",
        "materialSpec": "6061",
        "unitId": 1,
        "unitName": "件",
        "inspectionQuantity": "10.000000",
        "remainingQuantity": "10.000000",
        "qualifiedQuantity": "0.000000",
        "rejectedQuantity": "0.000000",
        "frozenQuantity": "0.000000",
        "status": "PENDING",
        "statusName": "待处理",
        "businessDate": "2026-07-10",
        "createdByName": "管理员",
        "createdAt": "2026-07-10T10:00:00+08:00",
        "completedByName": null,
        "completedAt": null,
        "reason": null,
        "remark": null,
        "version": 1,
        "canProcess": true,
        "disabledReason": null
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "totalPages": 1
  },
  "traceId": "trace-017",
  "timestamp": "2026-07-10T10:01:00+08:00"
}
```

### 质量确认详情

- 方法：`GET`
- 路径：`/api/admin/quality/inspections/{id}`
- 权限：`quality:inspection:view`
- 成功响应：`QualityInspectionDetail`。
- 不存在返回：`QUALITY_INSPECTION_NOT_FOUND`。

### 提交质量确认

- 方法：`POST`
- 路径：`/api/admin/quality/inspections/{id}/process`
- 权限：`quality:inspection:process`
- 请求体：`QualityInspectionProcessPayload`。
- 成功响应：`QualityInspectionDetail`。

请求体：

```json
{
  "businessDate": "2026-07-10",
  "qualifiedQuantity": "8.000000",
  "rejectedQuantity": "1.000000",
  "frozenQuantity": "1.000000",
  "reason": "来料检验完成",
  "remark": "外观轻微瑕疵 1 件"
}
```

规则：

- `businessDate` 必填，并受 `BusinessPeriodGuard` 写入保护。
- `qualifiedQuantity`、`rejectedQuantity`、`frozenQuantity` 必填，必须为大于等于 0 的字符串数量，最多 6 位小数。
- 三类结果数量合计必须等于本次待处理数量。
- 当前确认记录状态必须为 `PENDING`。
- 当前待检库存必须足够，且来源仓库、物料和单位必须与确认记录一致。
- 提交后在同一事务内完成待检库存减少、合格或不合格或冻结库存增加、库存流水、确认记录状态和审计日志。
- 重复提交已完成记录必须返回 `QUALITY_INSPECTION_STATUS_INVALID`，不得重复转移库存。

### 冻结合格库存

- 方法：`POST`
- 路径：`/api/admin/inventory/quality-transfers/freeze`
- 权限：`quality:status:freeze`
- 请求体：`QualityStatusFreezePayload`。
- 成功响应：`QualityStatusTransferResult`。

请求体：

```json
{
  "businessDate": "2026-07-10",
  "warehouseId": 1,
  "materialId": 11,
  "unitId": 1,
  "quantity": "2.000000",
  "reason": "客户投诉批次临时隔离",
  "remark": "冻结后等待后续质量处理"
}
```

规则：

- 只允许从 `QUALIFIED` 转为 `FROZEN`。
- `businessDate` 受 `BusinessPeriodGuard` 写入保护。
- `quantity` 必须为大于 0 的字符串数量，最多 6 位小数。
- 合格库存不足时返回 `INVENTORY_QUALITY_STATUS_BALANCE_NOT_ENOUGH`。
- 成功后必须生成两笔质量状态转换流水，分别记录合格减少和冻结增加，并通过 `relatedMovementId` 建立关联。

### 解冻冻结库存

- 方法：`POST`
- 路径：`/api/admin/inventory/quality-transfers/unfreeze`
- 权限：`quality:status:unfreeze`
- 请求体：`QualityStatusUnfreezePayload`。
- 成功响应：`QualityStatusTransferResult`。

请求体：

```json
{
  "businessDate": "2026-07-10",
  "warehouseId": 1,
  "materialId": 11,
  "unitId": 1,
  "quantity": "2.000000",
  "reason": "复核通过解除冻结",
  "remark": "恢复为合格可用库存"
}
```

规则：

- 只允许从 `FROZEN` 转为 `QUALIFIED`。
- 冻结库存不足时返回 `INVENTORY_QUALITY_STATUS_BALANCE_NOT_ENOUGH`。
- 解冻成功后 `availableQuantity` 必须随合格库存增加同步更新。

### 库存余额分页列表质量状态扩展

- 方法：`GET`
- 路径：`/api/admin/inventory/balances?qualityStatus=QUALIFIED`
- 权限：`inventory:balance:view`
- 新增查询参数：
  - `qualityStatus`：质量状态，可选值为 `PENDING_INSPECTION`、`QUALIFIED`、`REJECTED`、`FROZEN`。
  - `includeZeroQualityStatuses`：是否返回数量为 0 的质量状态行，默认 `false`。
- 成功响应：`PageResponse<InventoryBalanceQualitySummary>`。

成功响应字段必须支持前端展示总现存、合格可用量和质量状态分布。`availableQuantity` 只代表合格可用库存，不得等同于总现存数量。

### 库存流水分页列表质量状态扩展

- 方法：`GET`
- 路径：`/api/admin/inventory/movements?qualityStatus=PENDING_INSPECTION`
- 权限：`inventory:movement:view`
- 新增查询参数：
  - `qualityStatus`：质量状态。
  - `sourceType`：支持质量确认和质量状态转换来源类型筛选。
  - `sourceId`：来源单据标识。
  - `sourceLineId`：来源行标识。
- 成功响应：`PageResponse<InventoryMovementQualitySummary>`。

流水响应必须保留原有来源追溯字段，并新增质量状态字段。历史流水迁移后默认返回 `qualityStatus = QUALIFIED` 和 `qualityStatusName = 合格`。

## 采购退货和普通消耗约束

- 销售出库、生产领料和生产补料调用库存过账时必须使用 `QUALIFIED`，后端集中拒绝非合格库存。
- 当总库存充足但合格库存不足时，返回 `INVENTORY_NON_QUALIFIED_NOT_AVAILABLE` 或 `INVENTORY_QUALITY_STATUS_BALANCE_NOT_ENOUGH`，不得只返回普通库存不足。
- 采购退货必须显式传入退货质量状态，允许 `PENDING_INSPECTION`、`REJECTED` 或 `QUALIFIED`，不允许直接退 `FROZEN`。
- 冻结库存退货前必须先调用解冻接口，解冻受权限、原因、审计和期间保护约束。
- 前端候选库存响应固定使用 `selectable` 表示当前行是否可选，固定使用 `disabledReasonCode` 和 `disabledReason` 表示不可选原因，固定使用 `maxSelectableQuantity` 表示当前业务动作最多可选数量。
- 017 统一采用 `selectable`，不得同时返回或依赖 `canUse`，避免前后端字段分歧。
- 前端候选库存响应需要保留 `qualityStatus`、`qualityStatusName` 和 `availableQuantity`，用于区分无库存和有库存但无合格可用库存。

候选库存字段：

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| `warehouseId` | number | 是 | 仓库标识 |
| `warehouseName` | string | 是 | 仓库名称 |
| `materialId` | number | 是 | 物料标识 |
| `materialCode` | string | 是 | 物料编码 |
| `materialName` | string | 是 | 物料名称 |
| `qualityStatus` | string | 是 | 候选库存质量状态 |
| `qualityStatusName` | string | 是 | 候选库存质量状态中文名 |
| `quantityOnHand` | string | 是 | 当前质量状态现存数量 |
| `availableQuantity` | string | 是 | 合格可用数量，非合格状态返回 `0.000000` |
| `selectable` | boolean | 是 | 当前业务动作是否允许选择该库存 |
| `disabledReasonCode` | string | 否 | 不可选原因码，可选时为 `null` |
| `disabledReason` | string | 否 | 不可选原因文案，可选时为 `null` |
| `maxSelectableQuantity` | string | 是 | 当前业务动作最多可选数量，不可选时为 `0.000000` |

常用不可选原因码：

| 值 | 说明 |
|---|---|
| `NON_QUALIFIED_NOT_AVAILABLE` | 待检或不合格库存不能用于普通消耗 |
| `FROZEN_NOT_AVAILABLE` | 冻结库存不能用于普通消耗或直接采购退货 |
| `QUALIFIED_BALANCE_NOT_ENOUGH` | 合格可用库存不足 |
| `BUSINESS_RULE_NOT_ALLOWED` | 当前业务动作不允许选择该质量状态 |

候选库存字段示例：

```json
{
  "warehouseId": 1,
  "warehouseName": "原料仓",
  "materialId": 11,
  "materialCode": "MAT-001",
  "materialName": "铝型材",
  "qualityStatus": "PENDING_INSPECTION",
  "qualityStatusName": "待检",
  "quantityOnHand": "10.000000",
  "availableQuantity": "0.000000",
  "selectable": false,
  "disabledReasonCode": "NON_QUALIFIED_NOT_AVAILABLE",
  "disabledReason": "待检库存不可领料",
  "maxSelectableQuantity": "0.000000"
}
```

合格库存可选示例：

```json
{
  "warehouseId": 1,
  "warehouseName": "原料仓",
  "materialId": 11,
  "materialCode": "MAT-001",
  "materialName": "铝型材",
  "qualityStatus": "QUALIFIED",
  "qualityStatusName": "合格",
  "quantityOnHand": "8.000000",
  "availableQuantity": "8.000000",
  "selectable": true,
  "disabledReasonCode": null,
  "disabledReason": null,
  "maxSelectableQuantity": "8.000000"
}
```

## 权限编码

| 权限编码 | 类型 | 说明 |
|---|---|---|
| `quality` | 菜单 | 质量管理 |
| `quality:inspection:view` | 操作 | 查看质量确认列表、详情和来源上下文 |
| `quality:inspection:process` | 操作 | 提交质量确认 |
| `quality:status:freeze` | 操作 | 冻结合格库存 |
| `quality:status:unfreeze` | 操作 | 解冻冻结库存 |
| `inventory:balance:view` | 操作 | 查看库存余额和质量状态分布 |
| `inventory:movement:view` | 操作 | 查看库存流水和质量状态来源追溯 |

无权限用户访问接口返回 `AUTH_FORBIDDEN`。前端按钮隐藏或禁用只作为体验控制，后端必须独立鉴权。

## 错误码

| 错误码 | HTTP | 场景 |
|---|---|---|
| `INVENTORY_QUALITY_STATUS_REQUIRED` | 400 | 库存过账或查询需要质量状态但未提供 |
| `INVENTORY_QUALITY_STATUS_INVALID` | 400 | 质量状态不在固定枚举内 |
| `INVENTORY_NON_QUALIFIED_NOT_AVAILABLE` | 409 | 非合格库存不能用于销售出库、生产领料、生产补料或计划可用量 |
| `INVENTORY_QUALITY_STATUS_BALANCE_NOT_ENOUGH` | 409 | 指定质量状态库存不足 |
| `QUALITY_INSPECTION_NOT_FOUND` | 404 | 质量确认记录不存在 |
| `QUALITY_INSPECTION_SOURCE_INVALID` | 409 | 来源不支持、来源状态不合法或来源已完成质量确认 |
| `QUALITY_INSPECTION_QUANTITY_MISMATCH` | 400 | 合格、不合格、冻结数量合计不等于待处理数量 |
| `QUALITY_INSPECTION_STATUS_INVALID` | 409 | 当前质量确认状态不允许处理 |
| `QUALITY_STATUS_TRANSITION_INVALID` | 409 | 冻结、解冻或质量转换路径不允许 |
| `QUALITY_STATUS_REASON_REQUIRED` | 400 | 质量确认、冻结或解冻未填写原因 |
| `BUSINESS_PERIOD_LOCKED` | 409 | 写入业务日期落入已锁定期间 |
| `VALIDATION_ERROR` | 400 | 字段格式或必填校验失败 |
| `AUTH_UNAUTHORIZED` | 401 | 未登录或登录失效 |
| `AUTH_FORBIDDEN` | 403 | 已登录但无接口权限 |

错误响应示例：

```json
{
  "success": false,
  "code": "INVENTORY_NON_QUALIFIED_NOT_AVAILABLE",
  "message": "非合格库存不能用于普通出库",
  "details": [
    {
      "field": "qualityStatus",
      "message": "当前质量状态为待检，不能用于生产领料"
    }
  ],
  "traceId": "trace-017",
  "timestamp": "2026-07-10T10:10:00+08:00"
}
```

`BUSINESS_PERIOD_LOCKED` 的错误详情沿用 016 契约，必须包含 `periodCode`、`periodName`、`businessDate` 和可展示的业务提示。

## 期间保护约束

以下动作必须在业务写入前调用 `BusinessPeriodGuard.assertWritable(businessDate, operation, sourceType, sourceId)`：

- 采购入库、完工入库、销售退货和生产退料形成待检库存。
- 提交质量确认。
- 冻结合格库存。
- 解冻冻结库存。
- 销售出库、生产领料、生产补料按质量状态扣减。
- 采购退货按显式质量状态扣减。

锁定期间内历史质量确认记录、库存余额、库存流水和来源追溯仍可查询。不得通过质量接口绕过 016 期间保护。

## 审计字段和来源追溯

质量确认、冻结、解冻和质量状态库存写入必须记录：

- `createdByName`、`createdAt`。
- `completedByName`、`completedAt`，适用于质量确认。
- `operatorName`、`operatedAt`，适用于状态转换和库存流水。
- `businessDate`。
- `reason`，写操作必填。
- `remark`。
- `sourceType`、`sourceId`、`sourceLineId`、`sourceDocumentNo`。
- `movementNo`、`relatedMovementId`，适用于库存流水和质量状态转换。

现有已过账来源追溯字段必须保留。历史库存迁移为合格库存后，历史余额和历史流水仍可按来源单据查询。

## 前端联调字段要求

前端页面和测试至少依赖以下响应字段：

- 质量状态标签：`qualityStatus`、`qualityStatusName`。
- 质量确认列表：`inspectionNo`、`sourceTypeName`、`sourceDocumentNo`、`warehouseName`、`materialCode`、`materialName`、`inspectionQuantity`、`remainingQuantity`、`statusName`、`businessDate`、`canProcess`、`disabledReason`。
- 质量处理抽屉：`sourceSummary`、`inspectionQuantity`、`remainingQuantity`、`qualifiedQuantity`、`rejectedQuantity`、`frozenQuantity`、`reason`、`remark`、`version`。
- 库存余额页：`totalQuantityOnHand`、`availableQuantity`、`pendingInspectionQuantity`、`qualifiedQuantity`、`rejectedQuantity`、`frozenQuantity`。
- 候选库存：`qualityStatus`、`qualityStatusName`、`availableQuantity`、`selectable`、`disabledReasonCode`、`disabledReason`、`maxSelectableQuantity`。
- 错误提示：稳定 `code`、可展示 `message`、字段级 `details`、`traceId`。

## 联调验收

- 入库来源形成待检库存后，质量确认列表可查询到对应记录。
- 提交质量确认时，字符串数量解析正确，数量合计不平被拒绝。
- 质量确认成功后，待检库存减少，合格、不合格或冻结库存增加，总库存不变，流水成对可追溯。
- `GET /api/admin/inventory/balances?qualityStatus=QUALIFIED` 返回合格库存，`availableQuantity` 等于合格可用库存。
- `GET /api/admin/inventory/movements?qualityStatus=PENDING_INSPECTION` 返回待检相关流水，并保留来源追溯。
- 销售出库、生产领料和生产补料不能消耗待检、不合格或冻结库存。
- 采购退货按显式质量状态退货，冻结库存需先解冻。
- 冻结和解冻均需要权限、原因、审计和开放业务期间。
- 锁定业务期间内，质量确认、冻结、解冻和质量状态库存扣减均返回 `BUSINESS_PERIOD_LOCKED`。
- 只读用户可以查看质量确认、余额和流水，不能处理质量确认、冻结或解冻。
