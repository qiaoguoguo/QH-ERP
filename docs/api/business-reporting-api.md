# 经营报表基础接口契约

## 目标

定义经营报表基础模块的后端只读接口，统一前后端对固定经营报表、筛选条件、汇总指标、来源追溯、权限和异常的协作口径。

本接口契约只覆盖经营业务报表，不覆盖总账、凭证、结账、正式财务报表、税务、银行对账、可配置 BI、多组织或多账套。

## 基础约定

- 接口统一前缀：`/api/admin/reports`。
- 所有接口均为 `GET`。
- 所有接口返回统一 `ApiResponse` 信封。
- 金额、数量和百分比响应字段使用字符串，避免浮点误差进入前端展示。
- 日期参数使用 `YYYY-MM-DD`。
- 默认日期范围为当前月。
- 最大查询跨度第一版限制为 12 个月。
- 分页接口使用统一分页响应结构。
- 无数据不是错误，返回零值、空数组和 `sourceCount=0`。
- 报表接口只读，不得新增、修改或删除业务单据。

## 权限

| 权限 | 含义 |
|---|---|
| `report` | 经营报表菜单 |
| `report:overview:view` | 查看经营概览 |
| `report:sales:view` | 查看销售经营报表 |
| `report:procurement:view` | 查看采购经营报表 |
| `report:inventory:view` | 查看库存收发存报表 |
| `report:production:view` | 查看生产执行报表 |
| `report:cost:view` | 查看成本归集报表 |
| `report:settlement:view` | 查看往来收付报表 |
| `report:exception:view` | 查看经营异常清单 |

追溯接口使用对应报表查看权限。来源详情跳转仍由来源模块权限控制，例如销售出库详情继续要求 `sales:shipment:view`。

## 通用查询参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `dateFrom` | date | 否 | 开始业务日期 |
| `dateTo` | date | 否 | 结束业务日期 |
| `customerId` | number | 否 | 客户 |
| `supplierId` | number | 否 | 供应商 |
| `materialId` | number | 否 | 物料 |
| `warehouseId` | number | 否 | 仓库 |
| `workOrderId` | number | 否 | 生产工单 |
| `status` | string | 否 | 状态 |
| `keyword` | string | 否 | 单号、名称或来源关键字 |
| `page` | number | 否 | 页码，从 1 开始 |
| `pageSize` | number | 否 | 每页数量 |

参数处理规则：

- 各接口只使用与自身报表相关的参数。
- 请求带入未知参数或与当前报表无关的通用参数时，后端忽略该参数，不影响响应。
- 已支持参数的类型、格式、范围或枚举值非法时，后端返回确定错误。
- `dateFrom > dateTo` 或查询跨度超过 12 个月时返回 `REPORT_DATE_RANGE_INVALID`。
- `page < 1`、`pageSize < 1`、`pageSize` 超过系统分页上限、数字参数无法解析、日期格式非法或状态不属于当前报表状态集合时返回 `REPORT_PARAMETER_INVALID` 或统一校验错误 `VALIDATION_ERROR`。

## 错误码

| 错误码 | 含义 |
|---|---|
| `REPORT_DATE_RANGE_INVALID` | 日期范围非法或超过最大跨度 |
| `REPORT_PARAMETER_INVALID` | 报表查询参数非法 |
| `REPORT_TRACE_KEY_INVALID` | 来源追溯键非法 |
| `AUTH_UNAUTHORIZED` | 未登录或登录失效 |
| `AUTH_FORBIDDEN` | 无对应报表权限 |
| `VALIDATION_ERROR` | 请求参数校验失败 |

## 分页报表响应结构

除经营概览外，固定报表列表接口均返回以下结构。`summary` 统计当前筛选条件下的完整结果集，不只统计当前页。

```json
{
  "summary": {
    "recordCount": 1,
    "sourceCount": 1
  },
  "items": [],
  "page": 1,
  "pageSize": 20,
  "total": 0,
  "totalPages": 0
}
```

## 经营概览

### `GET /api/admin/reports/overview`

权限：`report:overview:view`

查询参数：

- `dateFrom`
- `dateTo`

响应 `data`：

```json
{
  "period": {
    "dateFrom": "2026-07-01",
    "dateTo": "2026-07-31"
  },
  "salesShipmentAmount": "120000.00",
  "purchaseReceiptAmount": "80000.00",
  "inventoryInQuantity": "500.000",
  "inventoryOutQuantity": "320.000",
  "productionPlannedQuantity": "200.000",
  "productionCompletedQuantity": "160.000",
  "costAmount": "35000.00",
  "receivableBalance": "45000.00",
  "payableBalance": "30000.00",
  "receivedAmount": "75000.00",
  "paidAmount": "50000.00",
  "exceptionCount": 6,
  "formalAccounting": false
}
```

## 销售经营汇总

### `GET /api/admin/reports/sales-summary`

权限：`report:sales:view`

查询参数：

- `dateFrom`
- `dateTo`
- `customerId`
- `materialId`
- `status`
- `keyword`
- `page`
- `pageSize`

响应 `data`：

```json
{
  "summary": {
    "shipmentQuantity": "10.000",
    "shipmentAmount": "12000.00",
    "receivableAmount": "12000.00",
    "receivedAmount": "5000.00",
    "unreceivedAmount": "7000.00",
    "sourceCount": 1
  },
  "items": [
    {
      "sourceType": "SALES_SHIPMENT",
      "sourceId": 1001,
      "sourceNo": "SS202607040001",
      "salesOrderId": 501,
      "salesOrderNo": "SO202607040001",
      "customerId": 12,
      "customerName": "示例客户",
      "materialId": 31,
      "materialName": "示例成品",
      "businessDate": "2026-07-04",
      "quantity": "10.000",
      "unitPrice": "1200.00",
      "amount": "12000.00",
      "receivableAmount": "12000.00",
      "receivedAmount": "5000.00",
      "unreceivedAmount": "7000.00",
      "sourceCount": 1,
      "traceKey": "sales-summary:SALES_SHIPMENT:1001"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1,
  "totalPages": 1
}
```

### `GET /api/admin/reports/sales-summary/traces`

权限：`report:sales:view`

查询参数：

- `traceKey`
- `page`
- `pageSize`

追溯销售出库、销售订单、销售出库行、物料、客户、数量、单价、金额和可跳转资源。

## 采购经营汇总

### `GET /api/admin/reports/procurement-summary`

权限：`report:procurement:view`

查询参数：

- `dateFrom`
- `dateTo`
- `supplierId`
- `materialId`
- `status`
- `keyword`
- `page`
- `pageSize`

采购响应结构与销售响应对称。`summary` 至少包含入库数量、入库金额、应付金额、已付金额、未付金额和来源数量。来源类型为 `PURCHASE_RECEIPT`，金额按采购入库数量和采购订单单价形成。

### `GET /api/admin/reports/procurement-summary/traces`

权限：`report:procurement:view`

查询参数：

- `traceKey`
- `page`
- `pageSize`

追溯采购入库、采购订单、采购入库行、物料、供应商、数量、单价、金额和可跳转资源。

## 库存收发存

### `GET /api/admin/reports/inventory-stock-flow`

权限：`report:inventory:view`

查询参数：

- `dateFrom`
- `dateTo`
- `warehouseId`
- `materialId`
- `keyword`
- `page`
- `pageSize`

响应行字段：

```json
{
  "summary": {
    "openingQuantity": "100.000",
    "inQuantity": "50.000",
    "outQuantity": "30.000",
    "adjustQuantity": "0.000",
    "closingQuantity": "120.000",
    "sourceCount": 8
  },
  "items": [
    {
      "warehouseId": 1,
      "warehouseName": "主仓",
      "materialId": 31,
      "materialName": "示例物料",
      "openingQuantity": "100.000",
      "inQuantity": "50.000",
      "outQuantity": "30.000",
      "adjustQuantity": "0.000",
      "closingQuantity": "120.000",
      "sourceCount": 8,
      "traceKey": "inventory-stock-flow:1:31"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1,
  "totalPages": 1
}
```

### `GET /api/admin/reports/inventory-stock-flow/traces`

权限：`report:inventory:view`

查询参数：

- `traceKey`
- `dateFrom`
- `dateTo`
- `page`
- `pageSize`

追溯库存流水，覆盖期初、调整、采购入库、生产领料、完工入库和销售出库形成的库存变动。该追溯属于期间敏感追溯，前端打开追溯时必须传递当前报表筛选的 `dateFrom` 和 `dateTo`；缺省时使用当前月默认期间。

## 生产执行

### `GET /api/admin/reports/production-execution`

权限：`report:production:view`

查询参数：

- `dateFrom`
- `dateTo`
- `workOrderId`
- `materialId`
- `status`
- `keyword`
- `page`
- `pageSize`

响应 `data`：

```json
{
  "summary": {
    "workOrderCount": 1,
    "plannedQuantity": "200.000",
    "issuedQuantity": "120.000",
    "reportedQuantity": "160.000",
    "qualifiedQuantity": "150.000",
    "defectiveQuantity": "10.000",
    "completionReceiptQuantity": "140.000",
    "completionRate": "70.00",
    "sourceCount": 4
  },
  "items": [
    {
      "workOrderId": 1001,
      "workOrderNo": "WO202607040001",
      "productMaterialId": 31,
      "productMaterialName": "示例成品",
      "plannedQuantity": "200.000",
      "issuedQuantity": "120.000",
      "reportedQuantity": "160.000",
      "qualifiedQuantity": "150.000",
      "defectiveQuantity": "10.000",
      "completionReceiptQuantity": "140.000",
      "completionRate": "70.00",
      "status": "IN_PROGRESS",
      "plannedStartDate": "2026-07-01",
      "plannedFinishDate": "2026-07-10",
      "sourceCount": 4,
      "traceKey": "production-execution:WORK_ORDER:1001"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1,
  "totalPages": 1
}
```

### `GET /api/admin/reports/production-execution/traces`

权限：`report:production:view`

查询参数：

- `traceKey`
- `dateFrom`
- `dateTo`
- `page`
- `pageSize`

追溯生产工单、领料单、报工单和完工入库单。该追溯属于期间敏感追溯，前端打开追溯时必须传递当前报表筛选的 `dateFrom` 和 `dateTo`；缺省时使用当前月默认期间。

## 成本归集

### `GET /api/admin/reports/cost-collection`

权限：`report:cost:view`

查询参数：

- `dateFrom`
- `dateTo`
- `workOrderId`
- `materialId`
- `status`
- `keyword`
- `page`
- `pageSize`

响应 `data`：

```json
{
  "summary": {
    "materialCostAmount": "18000.00",
    "laborCostAmount": "9000.00",
    "manufacturingOverheadAmount": "6000.00",
    "otherCostAmount": "2000.00",
    "totalCostAmount": "35000.00",
    "sourceCount": 4,
    "formalAccounting": false
  },
  "items": [
    {
      "costRecordId": 3001,
      "recordNo": "CR202607040001",
      "workOrderId": 1001,
      "workOrderNo": "WO202607040001",
      "productMaterialId": 31,
      "productMaterialName": "示例成品",
      "costType": "MATERIAL",
      "sourceType": "AUTO_PRODUCTION",
      "sourceDocumentType": "PRODUCTION_MATERIAL_ISSUE",
      "sourceDocumentId": 2001,
      "sourceDocumentNo": "MI202607040001",
      "businessDate": "2026-07-04",
      "quantity": "10.000",
      "unitPrice": "120.00",
      "amount": "1200.00",
      "basisType": "MANUAL_UNIT_PRICE_QUANTITY",
      "formalAccounting": false,
      "sourceCount": 1,
      "traceKey": "cost-collection:COST_RECORD:3001"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1,
  "totalPages": 1
}
```

### `GET /api/admin/reports/cost-collection/traces`

权限：`report:cost:view`

查询参数：

- `traceKey`
- `dateFrom`
- `dateTo`
- `page`
- `pageSize`

追溯成本记录、生产工单、生产领料、报工、完工入库和手工业务成本记录。该追溯属于期间敏感追溯，前端打开追溯时必须传递当前报表筛选的 `dateFrom` 和 `dateTo`；缺省时使用当前月默认期间。该接口不得返回凭证、科目或总账字段。

## 往来收付

### `GET /api/admin/reports/settlement-summary`

权限：`report:settlement:view`

查询参数：

- `dateFrom`
- `dateTo`
- `customerId`
- `supplierId`
- `status`
- `keyword`
- `page`
- `pageSize`

响应 `data`：

```json
{
  "summary": {
    "receivableAmount": "12000.00",
    "receivedAmount": "5000.00",
    "unreceivedAmount": "7000.00",
    "payableAmount": "8000.00",
    "paidAmount": "3000.00",
    "unpaidAmount": "5000.00",
    "sourceCount": 4
  },
  "items": [
    {
      "settlementType": "RECEIVABLE",
      "sourceId": 1001,
      "sourceNo": "AR202607040001",
      "partyType": "CUSTOMER",
      "partyId": 12,
      "partyName": "示例客户",
      "businessDate": "2026-07-04",
      "dueDate": "2026-07-31",
      "totalAmount": "12000.00",
      "settledAmount": "5000.00",
      "unsettledAmount": "7000.00",
      "overdueDays": 0,
      "agingBucket": "NOT_DUE",
      "status": "PARTIALLY_RECEIVED",
      "sourceCount": 2,
      "traceKey": "settlement-summary:RECEIVABLE:1001"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1,
  "totalPages": 1
}
```

### `GET /api/admin/reports/settlement-summary/traces`

权限：`report:settlement:view`

查询参数：

- `traceKey`
- `page`
- `pageSize`

追溯应收、收款、应付、付款，以及应收应付来源的销售出库和采购入库。

## 经营异常清单

### `GET /api/admin/reports/exceptions`

权限：`report:exception:view`

查询参数：

- `dateFrom`
- `dateTo`
- `type`
- `keyword`
- `page`
- `pageSize`

异常类型：

- `SALES_DELIVERY_OVERDUE`
- `PROCUREMENT_RECEIPT_OVERDUE`
- `INVENTORY_SHORTAGE`
- `PRODUCTION_OVERDUE`
- `COST_MISSING`
- `RECEIVABLE_OVERDUE`
- `PAYABLE_DUE_SOON`

响应 `data`：

```json
{
  "summary": {
    "exceptionCount": 6,
    "criticalCount": 2,
    "warningCount": 4,
    "countsByType": {
      "SALES_DELIVERY_OVERDUE": 1,
      "PROCUREMENT_RECEIPT_OVERDUE": 1,
      "INVENTORY_SHORTAGE": 1,
      "PRODUCTION_OVERDUE": 1,
      "COST_MISSING": 1,
      "RECEIVABLE_OVERDUE": 1,
      "PAYABLE_DUE_SOON": 0
    }
  },
  "items": [
    {
      "exceptionType": "SALES_DELIVERY_OVERDUE",
      "severity": "WARNING",
      "sourceType": "SALES_ORDER",
      "sourceId": 501,
      "sourceNo": "SO202607040001",
      "businessDate": "2026-07-04",
      "objectName": "示例客户 / 示例成品",
      "description": "销售订单存在逾期未发数量",
      "sourceCount": 1,
      "canViewResource": true,
      "traceKey": "exceptions:SALES_DELIVERY_OVERDUE:SALES_ORDER:501"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1,
  "totalPages": 1
}
```

### `GET /api/admin/reports/exceptions/traces`

权限：`report:exception:view`

查询参数：

- `traceKey`
- `dateFrom`
- `dateTo`
- `page`
- `pageSize`

追溯异常来源对象。该追溯属于期间敏感追溯，前端打开异常来源时必须传递与异常列表一致的 `dateFrom` 和 `dateTo`；缺省时使用当前月默认期间，`dateFrom > dateTo` 或查询跨度超过 12 个月时返回 `REPORT_DATE_RANGE_INVALID`。异常来源权限不足时必须按来源追溯脱敏规则返回，不得通过异常清单绕过来源模块权限。

异常列表也必须遵守来源权限。用户没有来源详情权限时，列表保留 `exceptionType`、`severity`、`description`、`sourceCount` 和 `canViewResource=false`，但 `sourceId`、`sourceNo`、`businessDate`、`objectName` 和 `traceKey` 必须为 `null`。

### 异常判定口径

| 异常类型 | 固定判定口径 |
|---|---|
| `SALES_DELIVERY_OVERDUE` | 销售订单状态为 `CONFIRMED` 或 `PARTIALLY_SHIPPED`，订单行未发数量大于 0，订单行预计发货日期优先、为空时使用订单预计发货日期，早于查询截止日期和当前日期中的较早日期。 |
| `PROCUREMENT_RECEIPT_OVERDUE` | 采购订单状态为 `CONFIRMED` 或 `PARTIALLY_RECEIVED`，订单行未收数量大于 0，订单行预计到货日期优先、为空时使用订单预计到货日期，早于查询截止日期和当前日期中的较早日期。 |
| `INVENTORY_SHORTAGE` | 物料在启用仓库的现有库存小于已确认销售未发数量与已下达或执行中生产未领数量之和。无销售未发或生产未领需求时不生成库存不足异常。库存不足来源类型为 `INVENTORY_BALANCE`，表示仓库和物料的库存余额状态；不得因为没有库存流水而漏报。 |
| `PRODUCTION_OVERDUE` | 生产工单状态为 `RELEASED` 或 `IN_PROGRESS`，完工入库数量小于计划数量，计划完成日期早于查询截止日期和当前日期中的较早日期。 |
| `COST_MISSING` | 生产工单存在已过账领料、已过账报工或已过账完工入库，但未形成对应有效成本记录。 |
| `RECEIVABLE_OVERDUE` | 应收状态为 `CONFIRMED` 或 `PARTIALLY_RECEIVED`，未收金额大于 0，到期日早于查询截止日期和当前日期中的较早日期。 |
| `PAYABLE_DUE_SOON` | 应付状态为 `CONFIRMED` 或 `PARTIALLY_PAID`，未付金额大于 0，到期日在当前日期到当前日期后 7 天之间。 |

## 来源追溯契约

### 追溯端点与追溯键

| 报表 | 端点 | `traceKey` 格式 |
|---|---|---|
| 销售经营汇总 | `/sales-summary/traces` | `sales-summary:SALES_SHIPMENT:<shipmentId>` |
| 采购经营汇总 | `/procurement-summary/traces` | `procurement-summary:PURCHASE_RECEIPT:<receiptId>` |
| 库存收发存 | `/inventory-stock-flow/traces` | `inventory-stock-flow:<warehouseId>:<materialId>` |
| 生产执行 | `/production-execution/traces` | `production-execution:WORK_ORDER:<workOrderId>` |
| 成本归集 | `/cost-collection/traces` | `cost-collection:WORK_ORDER:<workOrderId>` 或 `cost-collection:COST_RECORD:<costRecordId>` |
| 往来收付 | `/settlement-summary/traces` | `settlement-summary:RECEIVABLE:<id>`、`settlement-summary:PAYABLE:<id>`、`settlement-summary:RECEIPT:<id>` 或 `settlement-summary:PAYMENT:<id>` |
| 经营异常清单 | `/exceptions/traces` | `exceptions:<exceptionType>:<sourceType>:<sourceId>`；库存不足使用 `exceptions:INVENTORY_SHORTAGE:INVENTORY_BALANCE:<warehouseId>:<materialId>` |

`traceKey` 解析失败、来源对象不存在或来源对象与报表不匹配时返回 `REPORT_TRACE_KEY_INVALID`。

库存收发存、生产执行、成本归集和经营异常清单追溯必须保留当前报表期间上下文，调用时应携带与列表一致的 `dateFrom` 和 `dateTo`。日期缺省时按当前月默认期间处理；`dateFrom > dateTo` 或查询跨度超过 12 个月时返回 `REPORT_DATE_RANGE_INVALID`。

### 追溯响应

追溯接口响应 `data` 使用分页结构，`items` 行统一包含：

```json
{
  "sourceType": "SALES_SHIPMENT",
  "sourceId": 1001,
  "sourceNo": "SS202607040001",
  "sourceLineId": 2001,
  "businessDate": "2026-07-04",
  "status": "POSTED",
  "quantity": "10.000",
  "amount": "12000.00",
  "resourceRouteName": "sales-shipment-detail",
  "resourceRouteParams": {
    "id": 1001
  },
  "resourceRouteQuery": null,
  "canViewResource": true,
  "restricted": false,
  "restrictedMessage": null
}
```

### 来源路由映射

| 来源类型 | 来源权限 | `resourceRouteName` | `resourceRouteParams` | `resourceRouteQuery` |
|---|---|---|---|---|
| `SALES_ORDER` | `sales:order:view` | `sales-order-detail` | `{ "id": sourceId }` | `null` |
| `SALES_SHIPMENT` | `sales:shipment:view` | `sales-shipment-detail` | `{ "id": sourceId }` | `null` |
| `PURCHASE_ORDER` | `procurement:order:view` | `procurement-order-detail` | `{ "id": sourceId }` | `null` |
| `PURCHASE_RECEIPT` | `procurement:receipt:view` | `procurement-receipt-detail` | `{ "id": sourceId }` | `null` |
| `INVENTORY_DOCUMENT` | `inventory:document:view` | `inventory-document-detail` | `{ "id": sourceId }` | `null` |
| `INVENTORY_MOVEMENT` | `inventory:movement:view` | `inventory-movements` | `{}` | `{ "sourceId": sourceId }` |
| `INVENTORY_BALANCE` | `inventory:balance:view` | `inventory-balances` | `{}` | `{ "warehouseId": warehouseId, "materialId": materialId }` |
| `PRODUCTION_WORK_ORDER` | `production:work-order:view` | `production-work-order-detail` | `{ "id": sourceId }` | `null` |
| `PRODUCTION_MATERIAL_ISSUE` | `production:issue:view` | `production-work-order-material-issues` | `{ "id": workOrderId }` | `null` |
| `PRODUCTION_WORK_REPORT` | `production:report:view` | `production-work-order-reports` | `{ "id": workOrderId }` | `null` |
| `PRODUCTION_COMPLETION_RECEIPT` | `production:receipt:view` | `production-work-order-completion-receipts` | `{ "id": workOrderId }` | `null` |
| `COST_RECORD` | `cost:record:view` | `cost-record-detail` | `{ "id": sourceId }` | `null` |
| `RECEIVABLE` | `finance:receivable:view` | `finance-receivable-detail` | `{ "id": sourceId }` | `null` |
| `RECEIPT` | `finance:receipt:view` | `finance-receipt-detail` | `{ "id": sourceId }` | `null` |
| `PAYABLE` | `finance:payable:view` | `finance-payable-detail` | `{ "id": sourceId }` | `null` |
| `PAYMENT` | `finance:payment:view` | `finance-payment-detail` | `{ "id": sourceId }` | `null` |

### 来源权限脱敏

当用户拥有报表查看权限但没有来源详情权限时，追溯接口仍可返回行数，便于解释汇总来源数量，但必须脱敏敏感字段：

```json
{
  "sourceType": "SALES_SHIPMENT",
  "sourceId": null,
  "sourceNo": null,
  "sourceLineId": null,
  "businessDate": null,
  "status": null,
  "quantity": null,
  "amount": null,
  "resourceRouteName": null,
  "resourceRouteParams": null,
  "resourceRouteQuery": null,
  "canViewResource": false,
  "restricted": true,
  "restrictedMessage": "当前账号没有查看来源详情的权限"
}
```

前端在 `canViewResource=false` 时不得渲染可点击详情跳转，也不得从脱敏字段拼接来源链接。

## 验收约束

- 报表接口不得写业务数据。
- 报表接口不得返回凭证号、科目、总账余额、利润表或现金流量表语义。
- 固定报表列表必须返回 `summary + items`。
- `summary` 必须统计完整筛选结果集。
- 汇总值必须可由追溯明细解释。
- 全部固定报表和经营异常清单必须具备追溯接口。
- 来源权限不足时必须返回脱敏追溯行，不能泄露来源单号、来源主键、数量、金额、业务日期或路由参数。
- 权限不足必须返回 `AUTH_FORBIDDEN`。
- 日期范围非法必须返回 `REPORT_DATE_RANGE_INVALID`。
