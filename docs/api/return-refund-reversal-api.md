# 退货退款与业务反冲接口契约

## 目标

定义退货退款与业务反冲基础阶段的接口范围、字段、权限、错误码、来源追溯、事务一致性和联调验收规则。本阶段只建立业务反向单据、往来冲减、库存反向流水、成本业务影响和经营报表净额追溯，不提供审批流、正式红字发票、税务、总账凭证、结账、多组织、多币种或正式财务核算能力。

## 通用规则

- 所有接口使用统一 `ApiResponse<T>` 包装。
- 分页接口返回 `PageResponse<T>`。
- 写接口需要 CSRF token。
- 后端接口鉴权是最终安全边界。
- 数量字段使用字符串承载，后端使用 `BigDecimal` 和数据库 `numeric(18,6)`。
- 金额字段使用字符串承载，后端使用 `BigDecimal` 和数据库 `numeric(18,2)`。
- 草稿不影响库存、往来、成本和报表。
- 过账必须在单个数据库事务内完成来源锁定、反向单状态、库存流水、往来冲减、成本影响、追溯链接和审计日志。
- 已过账反向单据不可编辑、不可取消；如需修正，另建受控反向记录。
- 来源权限不足时，响应只能返回受限标记，不得返回来源单号、主键、行号、数量、金额、业务日期、状态或路由参数。

## 状态与枚举

### 反向单据状态

| 值 | 说明 |
|---|---|
| `DRAFT` | 草稿，可编辑、可取消、可过账，不影响业务数据 |
| `POSTED` | 已过账，已影响库存、往来、成本或报表，不可编辑 |
| `CANCELLED` | 已取消，仅草稿可取消，不影响业务数据 |

### 反向来源类型

| 值 | 说明 |
|---|---|
| `SALES_SHIPMENT` | 销售出库 |
| `SALES_SHIPMENT_LINE` | 销售出库行 |
| `PURCHASE_RECEIPT` | 采购入库 |
| `PURCHASE_RECEIPT_LINE` | 采购入库行 |
| `PRODUCTION_MATERIAL_ISSUE` | 生产领料 |
| `PRODUCTION_MATERIAL_ISSUE_LINE` | 生产领料行 |
| `RECEIVABLE` | 应收台账 |
| `RECEIPT` | 收款记录 |
| `PAYABLE` | 应付台账 |
| `PAYMENT` | 付款记录 |
| `COST_RECORD` | 成本记录 |
| `SALES_RETURN` | 销售退货 |
| `PURCHASE_RETURN` | 采购退货 |
| `PRODUCTION_MATERIAL_RETURN` | 生产退料 |
| `PRODUCTION_MATERIAL_SUPPLEMENT` | 生产补料 |
| `SETTLEMENT_ADJUSTMENT` | 往来冲减 |

### 库存反向流水类型

| 值 | 说明 |
|---|---|
| `SALES_RETURN_IN` | 销售退货入库 |
| `PURCHASE_RETURN_OUT` | 采购退货出库 |
| `PRODUCTION_MATERIAL_RETURN_IN` | 生产退料入库 |
| `PRODUCTION_MATERIAL_SUPPLEMENT_OUT` | 生产补料出库 |
| `BUSINESS_REVERSAL` | 预留值，015 不单独生成；第一版业务反冲由具体反向单据表达 |

## 主要表草案

### `biz_reversal_link`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigserial` | 主键 |
| `source_type` | `varchar(64)` | 原来源类型 |
| `source_id` | `bigint` | 原来源标识 |
| `source_line_id` | `bigint` | 原来源行标识；单据级追溯固定为 `0` |
| `reverse_type` | `varchar(64)` | 反向业务类型 |
| `reverse_id` | `bigint` | 反向单据标识 |
| `reverse_line_id` | `bigint` | 反向单据行标识；单据级追溯固定为 `0` |
| `business_date` | `date` | 反向业务日期 |
| `quantity` | `numeric(18,6)` | 反向数量 |
| `amount` | `numeric(18,2)` | 反向金额 |
| `created_by`、`created_at` | | 审计字段 |

用途：统一承载原业务和反向业务关系，供详情页、报表追溯和防重复校验使用。

约束与索引：

- `source_type`、`source_id`、`source_line_id`、`reverse_type`、`reverse_id`、`reverse_line_id`、`business_date` 非空。
- 单据级追溯使用 `source_line_id=0` 或 `reverse_line_id=0`，不得使用 `null` 表示单据级。
- `quantity` 和 `amount` 至少一个非空；非空时必须大于 0。
- 唯一索引 `uk_biz_reversal_link_reverse_line`：`reverse_type, reverse_id, reverse_line_id`。
- 唯一索引 `uk_biz_reversal_link_source_reverse`：`source_type, source_id, source_line_id, reverse_type, reverse_id, reverse_line_id`。
- 查询索引 `idx_biz_reversal_link_source`：`source_type, source_id, source_line_id`。
- 查询索引 `idx_biz_reversal_link_reverse`：`reverse_type, reverse_id`。

### `sal_sales_return` 与 `sal_sales_return_line`

主表字段包括 `return_no`、`customer_id`、`source_shipment_id`、`source_shipment_no`、`warehouse_id`、`business_date`、`status`、`total_amount`、`client_request_id`、`remark`、审计字段和 `version`。

明细字段包括 `return_id`、`source_shipment_line_id`、`sales_order_line_id`、`material_id`、`unit_id`、`line_no`、`returned_quantity_before`、`returnable_quantity_before`、`quantity`、`unit_price`、`amount`、`stock_movement_id`。

约束与索引：

- `return_no` 唯一。
- `status` 限定 `DRAFT`、`POSTED`、`CANCELLED`。
- `total_amount >= 0`，`quantity > 0`，`amount >= 0`。
- 明细唯一索引 `uk_sal_sales_return_line_source`：`return_id, source_shipment_line_id`。
- 幂等唯一索引 `uk_sal_sales_return_client_request`：`source_shipment_id, client_request_id`，其中 `client_request_id` 非空时生效。
- 查询索引覆盖 `customer_id`、`status, business_date desc, id desc`、`source_shipment_id`。

### `proc_purchase_return` 与 `proc_purchase_return_line`

主表字段包括 `return_no`、`supplier_id`、`source_receipt_id`、`source_receipt_no`、`warehouse_id`、`business_date`、`status`、`total_amount`、`client_request_id`、`remark`、审计字段和 `version`。

明细字段包括 `return_id`、`source_receipt_line_id`、`purchase_order_line_id`、`material_id`、`unit_id`、`line_no`、`returned_quantity_before`、`returnable_quantity_before`、`quantity`、`unit_price`、`amount`、`stock_movement_id`。

约束与索引：

- `return_no` 唯一。
- `status` 限定 `DRAFT`、`POSTED`、`CANCELLED`。
- `total_amount >= 0`，`quantity > 0`，`amount >= 0`。
- 明细唯一索引 `uk_proc_purchase_return_line_source`：`return_id, source_receipt_line_id`。
- 幂等唯一索引 `uk_proc_purchase_return_client_request`：`source_receipt_id, client_request_id`，其中 `client_request_id` 非空时生效。
- 查询索引覆盖 `supplier_id`、`status, business_date desc, id desc`、`source_receipt_id`。

### `mfg_material_return` 与 `mfg_material_return_line`

主表字段包括 `return_no`、`work_order_id`、`source_issue_id`、`warehouse_id`、`business_date`、`status`、`client_request_id`、`remark`、审计字段和 `version`。

明细字段包括 `return_id`、`source_issue_line_id`、`work_order_material_id`、`material_id`、`unit_id`、`line_no`、`returned_quantity_before`、`returnable_quantity_before`、`quantity`、`stock_movement_id`、`cost_record_id`。

约束与索引：

- `return_no` 唯一。
- `status` 限定 `DRAFT`、`POSTED`、`CANCELLED`。
- `quantity > 0`。
- 明细唯一索引 `uk_mfg_material_return_line_source`：`return_id, source_issue_line_id`。
- 幂等唯一索引 `uk_mfg_material_return_client_request`：`source_issue_id, client_request_id`，其中 `client_request_id` 非空时生效。
- 查询索引覆盖 `work_order_id`、`status, business_date desc, id desc`、`source_issue_id`。

### `mfg_material_supplement`

用于生产补料，方向为补料出库。补料来源必须关联有效工单和工单用料行，不依赖 `source_issue_id`。

主表字段包括 `supplement_no`、`work_order_id`、`warehouse_id`、`business_date`、`status`、`client_request_id`、`remark`、审计字段和 `version`。

明细字段包括 `supplement_id`、`work_order_material_id`、`material_id`、`unit_id`、`line_no`、`issued_quantity_before`、`supplemented_quantity_before`、`available_stock_quantity_before`、`quantity`、`stock_movement_id`、`cost_record_id`。

约束与索引：

- `supplement_no` 唯一。
- `work_order_id`、`warehouse_id`、`business_date`、`status` 非空。
- 明细 `supplement_id`、`work_order_material_id`、`material_id`、`unit_id`、`line_no`、`quantity` 非空。
- 外键：`work_order_id` 关联 `mfg_work_order`，`warehouse_id` 关联 `mst_warehouse`，`work_order_material_id` 关联 `mfg_work_order_material`，`material_id` 关联 `mst_material`，`unit_id` 关联 `mst_unit`，`stock_movement_id` 关联库存流水，`cost_record_id` 关联 `mfg_cost_record`。
- `status` 限定 `DRAFT`、`POSTED`、`CANCELLED`。
- `quantity > 0`。
- 明细唯一索引 `uk_mfg_material_supplement_line_material`：`supplement_id, work_order_material_id`。
- 幂等唯一索引 `uk_mfg_material_supplement_client_request`：`work_order_id, client_request_id`，其中 `client_request_id` 非空时生效。
- 查询索引覆盖 `work_order_id`、`status, business_date desc, id desc`、`warehouse_id`。

### `fin_settlement_adjustment`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigserial` | 主键 |
| `adjustment_no` | `varchar(64)` | 冲减单号 |
| `settlement_side` | `varchar(16)` | `RECEIVABLE` 或 `PAYABLE` |
| `adjustment_type` | `varchar(32)` | `RETURN_OFFSET`、`REFUND`、`PAYMENT_OFFSET` |
| `source_type` | `varchar(64)` | 来源类型 |
| `source_id` | `bigint` | 来源标识 |
| `target_id` | `bigint` | 应收或应付标识 |
| `business_date` | `date` | 业务日期 |
| `amount` | `numeric(18,2)` | 冲减金额 |
| `status` | `varchar(32)` | 状态 |
| `remark` | `varchar(500)` | 备注 |
| `client_request_id` | `varchar(64)` | 幂等请求标识，可空 |

约束与索引：

- `adjustment_no` 唯一。
- `settlement_side` 限定 `RECEIVABLE`、`PAYABLE`。
- `adjustment_type` 限定 `RETURN_OFFSET`、`REFUND`、`PAYMENT_OFFSET`。
- `status` 限定 `DRAFT`、`POSTED`、`CANCELLED`。
- `amount > 0`。
- 幂等唯一索引 `uk_fin_settlement_adjustment_client_request`：`source_type, source_id, target_id, client_request_id`，其中 `client_request_id` 非空时生效。
- 查询索引覆盖 `target_id`、`source_type, source_id`、`status, business_date desc, id desc`。

与 V10 往来台账兼容规则：

- V11 在 `fin_receivable` 新增 `adjusted_amount numeric(18,2) not null default 0`，并将余额约束改为 `total_amount = received_amount + adjusted_amount + unreceived_amount`。
- V11 在 `fin_payable` 新增 `adjusted_amount numeric(18,2) not null default 0`，并将余额约束改为 `total_amount = paid_amount + adjusted_amount + unpaid_amount`。
- 过账应收冲减时只增加 `fin_receivable.adjusted_amount`，同步减少 `unreceived_amount`，不降低 `total_amount`，不修改 `received_amount`。
- 过账应付冲减时只增加 `fin_payable.adjusted_amount`，同步减少 `unpaid_amount`，不降低 `total_amount`，不修改 `paid_amount`。
- `fin_settlement_adjustment.adjustment_type='REFUND'` 用于表达客户退款或供应商退款的业务冲减；退款不独立建表，来源使用原 `RECEIPT` 或 `PAYMENT`，并通过 `settlement_side` 区分应收或应付。

## 成本影响模型

015 不新增正式成本结转表。生产退料和生产补料复用 `mfg_cost_record` 表表达业务成本净额影响：

- V11 必须删除并重建 `ck_mfg_cost_record_source_document_type`，在现有 `PRODUCTION_MATERIAL_ISSUE`、`PRODUCTION_WORK_REPORT`、`PRODUCTION_COMPLETION_RECEIPT`、`MANUAL_COST_RECORD` 基础上增加 `PRODUCTION_MATERIAL_RETURN`、`PRODUCTION_MATERIAL_SUPPLEMENT`。
- Java 侧成本来源白名单、测试断言和报表 SQL 必须同步允许这两个 `source_document_type`。
- 退料过账生成 `mfg_cost_record`，`source_document_type='PRODUCTION_MATERIAL_RETURN'`，`cost_type='MATERIAL'`，`status='ACTIVE'`，数量为退料数量，金额为可解释业务金额；无稳定单价时金额为 `0.00`，数量仍参与净额口径。
- 补料过账生成 `mfg_cost_record`，`source_document_type='PRODUCTION_MATERIAL_SUPPLEMENT'`，`cost_type='MATERIAL'`，`status='ACTIVE'`。
- 原成本记录不被修改；报表通过来源类型区分正向材料消耗、退料冲减和补料增加。
- 成本记录来源唯一约束继续防止同一反向明细重复生成成本影响。

## 幂等与并发

- 所有创建接口可接受 `clientRequestId`，长度不超过 64。
- 同一来源和同一 `clientRequestId` 重复创建时，若已存在同状态草稿或已过账单据，返回已有详情；若请求体核心字段不一致，返回 `REVERSAL_DUPLICATED`。
- 过账接口对单据行和来源行加锁；重复过账同一 `DRAFT` 成功一次，后续请求返回 `REVERSAL_DUPLICATED` 或 `REVERSAL_POSTED_IMMUTABLE`，不得重复写库存、往来、成本或追溯。
- 并发超退、超冲和库存不足统一返回 `REVERSAL_CONCURRENT_MODIFICATION` 或对应业务错误码，事务整体回滚。

## 来源受限策略

- 当前资源本身无查看权限时，接口返回 `AUTH_FORBIDDEN`。
- 用户有当前反向资源查看权限，但无来源模块查看权限时，列表、详情和追溯仍返回反向记录；来源字段按脱敏结构返回：
  - `canViewSource=false`
  - `restricted=true`
  - `restrictedMessage='来源无查看权限'`
  - `sourceType` 保留
  - `sourceNo`、`sourceId`、`sourceLineId`、`quantity`、`amount`、`businessDate`、`status`、`resourceRouteName`、`resourceRouteParams`、`resourceRouteQuery` 不返回。
- `/api/admin/reversal-traces` 在用户有追溯入口权限时返回脱敏行；只有用户无追溯入口权限时才返回 403。
- `REVERSAL_TRACE_RESTRICTED` 仅用于直接请求无权限来源详情的特殊错误；普通追溯脱敏不使用该错误码。

## 通用响应结构

所有数量和金额字段均为字符串。来源字段必须复用 `ReversalSourceView`，避免不同页面自行拼接来源信息。

```ts
type ReversalRouteValue = string | number | boolean;

type ReversalSourceView = {
  sourceType: string;
  sourceId?: number;
  sourceLineId?: number;
  sourceNo?: string;
  lineNo?: number;
  businessDate?: string;
  status?: string;
  quantity?: string;
  amount?: string;
  canViewSource: boolean;
  restricted: boolean;
  restrictedMessage?: string;
  resourceRouteName?: string;
  resourceRouteParams?: Record<string, ReversalRouteValue>;
  resourceRouteQuery?: Record<string, ReversalRouteValue>;
};

type ReversalTraceRecord = {
  traceKey: string;
  direction: 'SOURCE_TO_REVERSE' | 'REVERSE_TO_SOURCE';
  source: ReversalSourceView;
  reverse: ReversalSourceView;
  inventoryMovementId?: number;
  settlementAdjustmentId?: number;
  costRecordId?: number;
  businessDate: string;
  quantity?: string;
  amount?: string;
  status: string;
  canViewResource: boolean;
  restricted: boolean;
  restrictedMessage?: string;
  resourceRouteName?: string;
  resourceRouteParams?: Record<string, ReversalRouteValue>;
  resourceRouteQuery?: Record<string, ReversalRouteValue>;
};

type ReversalDocumentLine = {
  id: number;
  lineNo: number;
  sourceLineId?: number;
  materialId: number;
  materialCode: string;
  materialName: string;
  unitId: number;
  unitName: string;
  returnedQuantityBefore?: string;
  returnableQuantityBefore?: string;
  quantity: string;
  unitPrice?: string;
  amount?: string;
  reason?: string;
  stockMovementId?: number;
  costRecordId?: number;
  source: ReversalSourceView;
};

type SalesReturnSummary = {
  id: number;
  returnNo: string;
  customerId: number;
  customerName: string;
  warehouseId: number;
  warehouseName: string;
  businessDate: string;
  status: 'DRAFT' | 'POSTED' | 'CANCELLED';
  totalQuantity: string;
  totalAmount: string;
  source: ReversalSourceView;
  createdAt: string;
  updatedAt: string;
};

type SalesReturnDetail = SalesReturnSummary & {
  clientRequestId?: string;
  remark?: string;
  lines: ReversalDocumentLine[];
  traces: ReversalTraceRecord[];
};
```

采购退货返回 `PurchaseReturnSummary`、`PurchaseReturnDetail`，字段与销售退货对称，将 `customerId/customerName` 替换为 `supplierId/supplierName`，来源主键和来源单号只通过 `source: ReversalSourceView` 返回。生产退料返回 `ProductionMaterialReturnSummary`、`ProductionMaterialReturnDetail`，字段包含 `workOrderId`、`workOrderNo`、`warehouseId`、`warehouseName`、`totalQuantity`、`source`、`lines`、`traces`。生产补料返回 `ProductionMaterialSupplementSummary`、`ProductionMaterialSupplementDetail`，字段包含 `supplementNo`、`workOrderId`、`workOrderNo`、`warehouseId`、`warehouseName`、`totalQuantity`、`source`、`lines`、`traces`。往来冲减返回 `SettlementAdjustmentSummary`、`SettlementAdjustmentDetail`，字段包含 `adjustmentNo`、`settlementSide`、`adjustmentType`、`source`、`targetId`、`targetNo`、`targetOriginalAmount`、`targetAdjustedAmountBefore`、`targetAdjustableAmountBefore`、`amount`、`targetRemainingAmountAfterPost`、`targetStatusAfterPost`、`status`、`traces`。

返回结构不得在顶层重复暴露来源主键、来源单号或来源行号；所有来源标识统一由 `source` 或 `lines[].source` 提供。来源受限时这些字段在 `ReversalSourceView` 内整体缺省，前端只能展示受限提示。

## 接口分组

| 资源 | 路径 | 能力 |
|---|---|---|
| 销售退货 | `/api/admin/sales/returns` | list/get/create/update/post/cancel |
| 销售退货候选来源 | `/api/admin/sales/return-sources` | list |
| 采购退货 | `/api/admin/procurement/returns` | list/get/create/update/post/cancel |
| 采购退货候选来源 | `/api/admin/procurement/return-sources` | list |
| 生产退料 | `/api/admin/production/material-returns` | list/get/create/update/post/cancel |
| 生产退料候选来源 | `/api/admin/production/material-return-sources` | list |
| 生产补料 | `/api/admin/production/material-supplements` | list/get/create/update/post/cancel |
| 生产补料候选来源 | `/api/admin/production/material-supplement-sources` | list |
| 往来冲减 | `/api/admin/finance/settlement-adjustments` | list/get/create/update/post/cancel |
| 往来冲减候选来源 | `/api/admin/finance/settlement-adjustment-sources` | list |
| 反向追溯 | `/api/admin/reversal-traces` | list |

## 关键接口定义

### 候选来源响应结构

```ts
type SalesReturnSource = {
  shipmentId: number;
  shipmentNo: string;
  customerId: number;
  customerName: string;
  warehouseId: number;
  warehouseName: string;
  businessDate: string;
  status: 'POSTED';
  lines: Array<{
    shipmentLineId: number;
    salesOrderLineId?: number;
    lineNo: number;
    materialId: number;
    materialCode: string;
    materialName: string;
    unitId: number;
    unitName: string;
    shippedQuantity: string;
    returnedQuantity: string;
    returnableQuantity: string;
    unitPrice: string;
    returnableAmount: string;
  }>;
};

type PurchaseReturnSource = {
  receiptId: number;
  receiptNo: string;
  supplierId: number;
  supplierName: string;
  warehouseId: number;
  warehouseName: string;
  businessDate: string;
  status: 'POSTED';
  lines: Array<{
    receiptLineId: number;
    purchaseOrderLineId?: number;
    lineNo: number;
    materialId: number;
    materialCode: string;
    materialName: string;
    unitId: number;
    unitName: string;
    receivedQuantity: string;
    returnedQuantity: string;
    returnableQuantity: string;
    availableStockQuantity: string;
    unitPrice: string;
    returnableAmount: string;
  }>;
};

type ProductionMaterialReturnSource = {
  issueId: number;
  issueNo: string;
  workOrderId: number;
  workOrderNo: string;
  warehouseId: number;
  warehouseName: string;
  businessDate: string;
  status: 'POSTED';
  lines: Array<{
    issueLineId: number;
    workOrderMaterialId: number;
    lineNo: number;
    materialId: number;
    materialCode: string;
    materialName: string;
    unitId: number;
    unitName: string;
    issuedQuantity: string;
    returnedQuantity: string;
    returnableQuantity: string;
    unitPrice: string;
    returnableAmount: string;
  }>;
};

type ProductionMaterialSupplementSource = {
  workOrderId: number;
  workOrderNo: string;
  workOrderStatus: string;
  warehouseId: number;
  warehouseName: string;
  materials: Array<{
    workOrderMaterialId: number;
    lineNo: number;
    materialId: number;
    materialCode: string;
    materialName: string;
    unitId: number;
    unitName: string;
    plannedQuantity: string;
    issuedQuantity: string;
    supplementedQuantity: string;
    availableStockQuantity: string;
    unitPrice: string;
  }>;
};

type SettlementAdjustmentSource = {
  sourceType: 'SALES_RETURN' | 'PURCHASE_RETURN' | 'RECEIPT' | 'PAYMENT' | 'SETTLEMENT_ADJUSTMENT';
  sourceId: number;
  sourceNo: string;
  settlementSide: 'RECEIVABLE' | 'PAYABLE';
  targetId: number;
  targetNo: string;
  businessDate: string;
  originalAmount: string;
  adjustedAmount: string;
  adjustableAmount: string;
  status: string;
};
```

### 销售退货候选来源

- 方法：`GET`
- 路径：`/api/admin/sales/return-sources`
- 权限：`sales:return:create`
- 查询参数：`page`、`pageSize`、`keyword`、`customerId`、`warehouseId`、`dateFrom`、`dateTo`。
- 规则：
  - 只返回状态为 `POSTED` 的销售出库。
  - 行级 `returnableQuantity` 必须等于原出库数量减已过账退货数量。
  - 无可退行的销售出库不作为候选。
- 成功响应：`PageResponse<SalesReturnSource>`。

### 创建销售退货草稿

- 方法：`POST`
- 路径：`/api/admin/sales/returns`
- 权限：`sales:return:create`
- 请求体：

```json
{
  "sourceShipmentId": 1,
  "businessDate": "2026-07-05",
  "clientRequestId": "sales-return-20260705-0001",
  "remark": "客户退货",
  "lines": [
    {
      "sourceShipmentLineId": 10,
      "quantity": "2.000000",
      "reason": "客户退回"
    }
  ]
}
```

- 规则：
  - 来源销售出库必须已过账。
  - 明细数量必须大于 0 且不超过当前可退数量。
  - 金额由后端按来源销售订单单价和退货数量计算。
  - 新建状态固定为 `DRAFT`。
  - `clientRequestId` 重复时按幂等规则返回已有详情或 `REVERSAL_DUPLICATED`。
- 成功响应：`SalesReturnDetail`。

### 销售退货列表、详情、更新和取消

- `GET /api/admin/sales/returns`：权限 `sales:return:view`；查询参数 `page`、`pageSize`、`keyword`、`customerId`、`warehouseId`、`status`、`dateFrom`、`dateTo`；成功响应 `PageResponse<SalesReturnSummary>`。
- `GET /api/admin/sales/returns/{id}`：权限 `sales:return:view`；成功响应 `SalesReturnDetail`。
- `PUT /api/admin/sales/returns/{id}`：权限 `sales:return:update`；仅 `DRAFT` 可更新；不允许改变来源销售出库；成功响应 `SalesReturnDetail`。
  - 来源可见时，请求体可继续传 `sourceShipmentId` 与明细 `sourceShipmentLineId`，后端必须校验与当前草稿来源一致。
  - 来源受限时，请求体允许省略 `sourceShipmentId`，明细允许只传当前销售退货明细 `id`、`quantity`、`reason`；后端用当前草稿行 `id` 映射既有 `sourceShipmentLineId`，不得要求前端回传受限来源主键。
  - 如果同一明细同时传 `id` 与 `sourceShipmentLineId`，两者必须对应当前草稿同一行，否则返回受控错误；如果 `id` 不属于当前草稿，也必须拒绝。
  - 创建接口仍必须提供 `sourceShipmentId` 与 `sourceShipmentLineId`。
- `PUT /api/admin/sales/returns/{id}/cancel`：权限 `sales:return:cancel`；仅 `DRAFT` 可取消；成功响应 `SalesReturnDetail`。

### 过账销售退货

- 方法：`PUT`
- 路径：`/api/admin/sales/returns/{id}/post`
- 权限：`sales:return:post`
- 规则：
  - 仅 `DRAFT` 可过账。
  - 事务内重新锁定来源销售出库行，防并发超退。
  - 生成 `SALES_RETURN_IN` 库存流水并增加库存余额。
  - 生成应收冲减记录，减少未收或可冲金额。
  - 写入 `biz_reversal_link`。
  - 更新状态为 `POSTED`。
- 成功响应：`SalesReturnDetail`。

### 采购退货与过账

- `GET /api/admin/procurement/return-sources`：权限 `procurement:return:create`；查询参数 `page`、`pageSize`、`keyword`、`supplierId`、`warehouseId`、`dateFrom`、`dateTo`；只返回已过账且存在可退行的采购入库；成功响应 `PageResponse<PurchaseReturnSource>`。
- `GET /api/admin/procurement/returns`：权限 `procurement:return:view`；查询参数 `page`、`pageSize`、`keyword`、`supplierId`、`warehouseId`、`status`、`dateFrom`、`dateTo`；成功响应 `PageResponse<PurchaseReturnSummary>`。
- `GET /api/admin/procurement/returns/{id}`：权限 `procurement:return:view`；成功响应 `PurchaseReturnDetail`。
- `POST /api/admin/procurement/returns`：权限 `procurement:return:create`；请求体：

```json
{
  "sourceReceiptId": 1,
  "businessDate": "2026-07-05",
  "clientRequestId": "purchase-return-20260705-0001",
  "remark": "供应商退货",
  "lines": [
    {
      "sourceReceiptLineId": 10,
      "quantity": "1.000000",
      "reason": "来料退回"
    }
  ]
}
```

成功响应 `PurchaseReturnDetail`。来源采购入库必须已过账，明细数量必须大于 0 且不超过当前可退数量，金额由后端按来源采购单价计算。

- `PUT /api/admin/procurement/returns/{id}`：权限 `procurement:return:update`；仅 `DRAFT` 可更新；不允许改变来源采购入库；成功响应 `PurchaseReturnDetail`。
  - 来源可见时，请求体可继续传 `sourceReceiptId` 与明细 `sourceReceiptLineId`，后端必须校验与当前草稿来源一致。
  - 来源受限时，请求体允许省略 `sourceReceiptId`，明细允许只传当前采购退货明细 `id`、`quantity`、`reason`；后端用当前草稿行 `id` 映射既有 `sourceReceiptLineId`，不得要求前端回传受限来源主键。
  - 如果同一明细同时传 `id` 与 `sourceReceiptLineId`，两者必须对应当前草稿同一行，否则返回受控错误；如果 `id` 不属于当前草稿，也必须拒绝。
  - 创建接口仍必须提供 `sourceReceiptId` 与 `sourceReceiptLineId`。
- `PUT /api/admin/procurement/returns/{id}/post`：权限 `procurement:return:post`；仅 `DRAFT` 可过账；事务内锁定来源采购入库行和库存余额，生成 `PURCHASE_RETURN_OUT` 库存流水并扣减库存余额，生成应付冲减，写入 `biz_reversal_link`；库存不足时返回 `REVERSAL_STOCK_INSUFFICIENT`，且不得产生部分写入；成功响应 `PurchaseReturnDetail`。
- `PUT /api/admin/procurement/returns/{id}/cancel`：权限 `procurement:return:cancel`；仅 `DRAFT` 可取消；成功响应 `PurchaseReturnDetail`。

### 生产退料与补料

生产退料接口：

- `GET /api/admin/production/material-return-sources`：权限 `production:material-return:create`；查询参数 `page`、`pageSize`、`keyword`、`workOrderId`、`warehouseId`、`dateFrom`、`dateTo`；只返回已过账且存在可退行的生产领料；成功响应 `PageResponse<ProductionMaterialReturnSource>`。
- `GET /api/admin/production/material-returns`：权限 `production:material-return:view`；查询参数 `page`、`pageSize`、`keyword`、`workOrderId`、`warehouseId`、`status`、`dateFrom`、`dateTo`；成功响应 `PageResponse<ProductionMaterialReturnSummary>`。
- `GET /api/admin/production/material-returns/{id}`：权限 `production:material-return:view`；成功响应 `ProductionMaterialReturnDetail`。
- `POST /api/admin/production/material-returns`：权限 `production:material-return:create`；请求体：

```json
{
  "sourceIssueId": 1,
  "businessDate": "2026-07-05",
  "clientRequestId": "material-return-20260705-0001",
  "remark": "工单退料",
  "lines": [
    {
      "sourceIssueLineId": 10,
      "quantity": "3.000000",
      "reason": "余料退回"
    }
  ]
}
```

成功响应 `ProductionMaterialReturnDetail`。来源固定为已过账生产领料行，数量不得超过已领数量减已退数量。

- `PUT /api/admin/production/material-returns/{id}`：权限 `production:material-return:update`；仅 `DRAFT` 可更新；不允许改变来源生产领料；成功响应 `ProductionMaterialReturnDetail`。
  - 来源可见时，请求体可继续传 `sourceIssueId` 与明细 `sourceIssueLineId`，后端必须校验与当前草稿来源一致。
  - 来源受限时，请求体允许省略 `sourceIssueId`，明细允许只传当前生产退料明细 `id`、`quantity`、`reason`；后端用当前草稿行 `id` 映射既有 `sourceIssueLineId`，不得要求前端回传受限来源主键。
  - 如果同一明细同时传 `id` 与 `sourceIssueLineId`，两者必须对应当前草稿同一行，否则返回受控错误；如果 `id` 不属于当前草稿，也必须拒绝。
  - 创建接口仍必须提供 `sourceIssueId` 与 `sourceIssueLineId`。
- `PUT /api/admin/production/material-returns/{id}/post`：权限 `production:material-return:post`；生成 `PRODUCTION_MATERIAL_RETURN_IN` 库存流水，生成 `mfg_cost_record` 成本业务冲减来源，写入 `biz_reversal_link`；成功响应 `ProductionMaterialReturnDetail`。
- `PUT /api/admin/production/material-returns/{id}/cancel`：权限 `production:material-return:cancel`；仅 `DRAFT` 可取消；成功响应 `ProductionMaterialReturnDetail`。

生产补料接口：

- `GET /api/admin/production/material-supplement-sources`：权限 `production:material-supplement:create`；查询参数 `page`、`pageSize`、`keyword`、`workOrderId`、`warehouseId`；只返回有效工单和工单物料行；成功响应 `PageResponse<ProductionMaterialSupplementSource>`。
- `GET /api/admin/production/material-supplements`：权限 `production:material-supplement:view`；查询参数 `page`、`pageSize`、`keyword`、`workOrderId`、`warehouseId`、`status`、`dateFrom`、`dateTo`；成功响应 `PageResponse<ProductionMaterialSupplementSummary>`。
- `GET /api/admin/production/material-supplements/{id}`：权限 `production:material-supplement:view`；成功响应 `ProductionMaterialSupplementDetail`。
- `POST /api/admin/production/material-supplements`：权限 `production:material-supplement:create`；请求体：

```json
{
  "workOrderId": 1,
  "warehouseId": 1,
  "businessDate": "2026-07-05",
  "clientRequestId": "material-supplement-20260705-0001",
  "remark": "工单补料",
  "lines": [
    {
      "workOrderMaterialId": 20,
      "quantity": "2.000000",
      "reason": "损耗补料"
    }
  ]
}
```

成功响应 `ProductionMaterialSupplementDetail`。补料来源固定为有效生产工单和工单物料行，过账时必须校验库存可用数量。

- `PUT /api/admin/production/material-supplements/{id}`：权限 `production:material-supplement:update`；仅 `DRAFT` 可更新；不允许改变来源工单或补料仓库；成功响应 `ProductionMaterialSupplementDetail`。
  - 来源可见时，请求体可继续传 `workOrderId`、`warehouseId` 与明细 `workOrderMaterialId`，后端必须校验与当前草稿来源一致。
  - 来源受限时，请求体允许省略 `workOrderId`、`warehouseId`，明细允许只传当前生产补料明细 `id`、`quantity`、`reason`；后端用当前草稿行 `id` 映射既有 `workOrderMaterialId`，不得要求前端回传受限来源主键。
  - 如果同一明细同时传 `id` 与 `workOrderMaterialId`，两者必须对应当前草稿同一行，否则返回受控错误；如果 `id` 不属于当前草稿，也必须拒绝。
  - 创建接口仍必须提供 `workOrderId`、`warehouseId` 与 `workOrderMaterialId`。
- `PUT /api/admin/production/material-supplements/{id}/post`：权限 `production:material-supplement:post`；生成 `PRODUCTION_MATERIAL_SUPPLEMENT_OUT` 库存流水，生成 `mfg_cost_record` 成本业务增加来源，写入 `biz_reversal_link`；库存不足返回 `REVERSAL_STOCK_INSUFFICIENT`；成功响应 `ProductionMaterialSupplementDetail`。
- `PUT /api/admin/production/material-supplements/{id}/cancel`：权限 `production:material-supplement:cancel`；仅 `DRAFT` 可取消；成功响应 `ProductionMaterialSupplementDetail`。

退料和补料的成本记录只作为业务成本口径，不作为正式成本结转。

### 往来冲减

- `GET /api/admin/finance/settlement-adjustment-sources`：权限 `finance:settlement-adjustment:create`；查询参数 `page`、`pageSize`、`keyword`、`settlementSide`、`sourceType`、`customerId`、`supplierId`、`dateFrom`、`dateTo`；成功响应 `PageResponse<SettlementAdjustmentSource>`。
- `GET /api/admin/finance/settlement-adjustments`：权限 `finance:settlement-adjustment:view`；查询参数 `page`、`pageSize`、`keyword`、`settlementSide`、`adjustmentType`、`sourceType`、`status`、`dateFrom`、`dateTo`；成功响应 `PageResponse<SettlementAdjustmentSummary>`。
- `GET /api/admin/finance/settlement-adjustments/{id}`：权限 `finance:settlement-adjustment:view`；成功响应 `SettlementAdjustmentDetail`。
- `POST /api/admin/finance/settlement-adjustments`：权限 `finance:settlement-adjustment:create`；请求体：

```json
{
  "settlementSide": "RECEIVABLE",
  "adjustmentType": "RETURN_OFFSET",
  "sourceType": "SALES_RETURN",
  "sourceId": 1,
  "targetId": 5,
  "businessDate": "2026-07-05",
  "amount": "120.00",
  "clientRequestId": "settlement-adjustment-20260705-0001",
  "remark": "销售退货冲减应收"
}
```

成功响应 `SettlementAdjustmentDetail`。来源可以是销售退货、采购退货、收款记录、付款记录或往来调整。生产退料和生产补料只影响库存与成本，不作为往来冲减来源。客户退款和供应商退款由 `adjustmentType='REFUND'` 的往来冲减记录表达，不独立作为 `sourceType`；接口不单独接受泛化 `BUSINESS_REVERSAL` 单据。

- `PUT /api/admin/finance/settlement-adjustments/{id}`：权限 `finance:settlement-adjustment:update`；仅 `DRAFT` 可更新；不允许改变 `settlementSide`、`sourceType`、`sourceId` 和 `targetId`；成功响应 `SettlementAdjustmentDetail`。
  - 来源可见时，请求体可继续传 `settlementSide`、`sourceType`、`sourceId` 与 `targetId`，后端必须校验与当前草稿一致。
  - 来源受限或前端无法回传来源字段时，请求体允许省略 `settlementSide`、`sourceType`、`sourceId` 与 `targetId`，后端使用当前草稿已有不可变字段，不得要求前端回传受限来源主键。
- `PUT /api/admin/finance/settlement-adjustments/{id}/post`：权限 `finance:settlement-adjustment:post`；仅 `DRAFT` 可过账；过账时锁定目标应收或应付台账，校验 `amount > 0` 且不超过 `adjustableAmountBefore`；成功响应 `SettlementAdjustmentDetail`。
- `PUT /api/admin/finance/settlement-adjustments/{id}/cancel`：权限 `finance:settlement-adjustment:cancel`；仅 `DRAFT` 可取消；成功响应 `SettlementAdjustmentDetail`。

往来余额公式：

- `targetAdjustedAmountAfter = targetAdjustedAmountBefore + amount`。
- 应收：`targetRemainingAmountAfterPost = max(total_amount - received_amount - targetAdjustedAmountAfter, 0)`，并写回 `unreceived_amount`。
- 应付：`targetRemainingAmountAfterPost = max(total_amount - paid_amount - targetAdjustedAmountAfter, 0)`，并写回 `unpaid_amount`。
- 应收 `targetStatusAfterPost`：剩余金额为 `0.00` 时为 `RECEIVED`；剩余金额大于 `0.00` 且 `received_amount + adjusted_amount > 0` 时为 `PARTIALLY_RECEIVED`；否则为 `CONFIRMED`。
- 应付 `targetStatusAfterPost`：剩余金额为 `0.00` 时为 `PAID`；剩余金额大于 `0.00` 且 `paid_amount + adjusted_amount > 0` 时为 `PARTIALLY_PAID`；否则为 `CONFIRMED`。
- 已过账收付款不直接删除，退款或付款冲减以独立调整记录表达。

### 反向追溯查询

- 方法：`GET`
- 路径：`/api/admin/reversal-traces`
- 权限：`business:reversal:view`
- 查询参数：
  - `sourceType`：必填，来源或反向资源类型。
  - `sourceId`：必填，来源或反向资源标识。
  - `sourceLineId`：可选，来源行标识。
  - `direction`：可选，`SOURCE_TO_REVERSE`、`REVERSE_TO_SOURCE`；默认双向。
  - `includeRestricted`：可选，默认 `true`。
- 成功响应：`ReversalTraceRecord[]`。
- 规则：
  - 用户同时具备来源和反向资源查看权限时，`traceKey` 使用 `${sourceType}:${sourceId}:${sourceLineId}:${reverseType}:${reverseId}:${reverseLineId}` 生成，前端用它做稳定行键。
  - 用户有追溯入口权限但无来源模块权限时返回脱敏行，`canViewResource=false`、`restricted=true`，且不返回路由参数。
  - 受限追溯行的 `traceKey` 不得包含无权限侧的来源单据主键或来源行主键；可使用当前用户可见侧坐标，或在两侧均不可见时使用服务端生成的稳定不透明键。
  - `resourceRouteName` 只能返回前端已注册路由名，使用当前仓库 kebab-case 命名，不使用 PascalCase。

来源路由映射：

| `sourceType` | `resourceRouteName` | `resourceRouteParams` | `resourceRouteQuery` |
|---|---|---|---|
| `SALES_SHIPMENT` | `sales-shipment-detail` | `{ "id": sourceId }` | 无 |
| `SALES_SHIPMENT_LINE` | `sales-shipment-detail` | `{ "id": sourceId }` | `{ "lineId": sourceLineId }` |
| `PURCHASE_RECEIPT` | `procurement-receipt-detail` | `{ "id": sourceId }` | 无 |
| `PURCHASE_RECEIPT_LINE` | `procurement-receipt-detail` | `{ "id": sourceId }` | `{ "lineId": sourceLineId }` |
| `PRODUCTION_MATERIAL_ISSUE` | `production-work-order-material-issues` | `{ "id": workOrderId }` | `{ "issueId": sourceId }` |
| `PRODUCTION_MATERIAL_ISSUE_LINE` | `production-work-order-material-issues` | `{ "id": workOrderId }` | `{ "issueId": sourceId, "lineId": sourceLineId }` |
| `RECEIVABLE` | `finance-receivable-detail` | `{ "id": sourceId }` | 无 |
| `RECEIPT` | `finance-receipt-detail` | `{ "id": sourceId }` | 无 |
| `PAYABLE` | `finance-payable-detail` | `{ "id": sourceId }` | 无 |
| `PAYMENT` | `finance-payment-detail` | `{ "id": sourceId }` | 无 |
| `COST_RECORD` | `cost-record-detail` | `{ "id": sourceId }` | 无 |
| `SALES_RETURN` | `sales-return-detail` | `{ "id": sourceId }` | 无 |
| `PURCHASE_RETURN` | `procurement-return-detail` | `{ "id": sourceId }` | 无 |
| `PRODUCTION_MATERIAL_RETURN` | `production-material-return-detail` | `{ "id": sourceId }` | 无 |
| `PRODUCTION_MATERIAL_SUPPLEMENT` | `production-material-supplement-detail` | `{ "id": sourceId }` | 无 |
| `SETTLEMENT_ADJUSTMENT` | `finance-settlement-adjustment-detail` | `{ "id": sourceId }` | 无 |
| 库存流水影响 | `inventory-movements` | 无 | `{ "sourceType": sourceType, "sourceId": sourceId }` |
| 销售报表追溯 | `reports-sales` | 无 | `{ "traceKey": traceKey }` |
| 采购报表追溯 | `reports-procurement` | 无 | `{ "traceKey": traceKey }` |
| 库存报表追溯 | `reports-inventory` | 无 | `{ "traceKey": traceKey }` |
| 生产报表追溯 | `reports-production` | 无 | `{ "traceKey": traceKey }` |
| 成本报表追溯 | `reports-cost` | 无 | `{ "traceKey": traceKey }` |
| 往来报表追溯 | `reports-settlement` | 无 | `{ "traceKey": traceKey }` |

## 报表净额字段契约

固定经营报表在原有字段基础上增加同名净额口径。每个报表响应都必须包含 `summary` 和 `items`，字段名保持一致，便于前端列表、导出和追溯复用。

```ts
type ReversalNetMetric = {
  originalAmount?: string;
  reverseAmount?: string;
  netAmount?: string;
  originalQuantity?: string;
  reverseQuantity?: string;
  netQuantity?: string;
  traceCount: number;
};

type ReversalReportPayload<TItem> = {
  summary: TItem & { reversalNet: ReversalNetMetric };
  items: Array<TItem & { reversalNet: ReversalNetMetric; traces: ReversalTraceRecord[] }>;
};
```

- 销售报表：`salesOriginalAmount`、`salesReturnAmount`、`salesNetAmount`、`salesOriginalQuantity`、`salesReturnQuantity`、`salesNetQuantity`。
- 采购报表：`purchaseOriginalAmount`、`purchaseReturnAmount`、`purchaseNetAmount`、`purchaseOriginalQuantity`、`purchaseReturnQuantity`、`purchaseNetQuantity`。
- 库存报表：`inboundOriginalQuantity`、`inboundReverseQuantity`、`inboundNetQuantity`、`outboundOriginalQuantity`、`outboundReverseQuantity`、`outboundNetQuantity`、`inventoryNetChangeQuantity`。
- 生产报表：`issuedOriginalQuantity`、`materialReturnQuantity`、`materialSupplementQuantity`、`issuedNetQuantity`、`completedQuantity`。
- 成本报表：`materialOriginalCost`、`materialReturnCost`、`materialSupplementCost`、`materialNetCost`、`totalNetCost`。
- 往来报表：`receivableOriginalAmount`、`receivableAdjustmentAmount`、`receivableNetAmount`、`payableOriginalAmount`、`payableAdjustmentAmount`、`payableNetAmount`、`settlementRemainingAmount`。

## 权限编码

| 权限编码 | 类型 | 说明 |
|---|---|---|
| `reversal` | 菜单 | 退货退款与反冲 |
| `sales:return:view` | 操作 | 查看销售退货 |
| `sales:return:create` | 操作 | 创建销售退货 |
| `sales:return:update` | 操作 | 更新销售退货草稿 |
| `sales:return:post` | 操作 | 过账销售退货 |
| `sales:return:cancel` | 操作 | 取消销售退货草稿 |
| `procurement:return:view` | 操作 | 查看采购退货 |
| `procurement:return:create` | 操作 | 创建采购退货 |
| `procurement:return:update` | 操作 | 更新采购退货草稿 |
| `procurement:return:post` | 操作 | 过账采购退货 |
| `procurement:return:cancel` | 操作 | 取消采购退货草稿 |
| `production:material-return:view` | 操作 | 查看生产退料 |
| `production:material-return:create` | 操作 | 创建生产退料 |
| `production:material-return:update` | 操作 | 更新生产退料草稿 |
| `production:material-return:post` | 操作 | 过账生产退料 |
| `production:material-return:cancel` | 操作 | 取消生产退料草稿 |
| `production:material-supplement:view` | 操作 | 查看生产补料 |
| `production:material-supplement:create` | 操作 | 创建生产补料 |
| `production:material-supplement:update` | 操作 | 更新生产补料草稿 |
| `production:material-supplement:post` | 操作 | 过账生产补料 |
| `production:material-supplement:cancel` | 操作 | 取消生产补料草稿 |
| `finance:settlement-adjustment:view` | 操作 | 查看往来冲减 |
| `finance:settlement-adjustment:create` | 操作 | 创建往来冲减 |
| `finance:settlement-adjustment:update` | 操作 | 更新往来冲减草稿 |
| `finance:settlement-adjustment:post` | 操作 | 过账往来冲减 |
| `finance:settlement-adjustment:cancel` | 操作 | 取消往来冲减草稿 |
| `business:reversal:view` | 操作 | 查看业务反冲追溯 |

## 错误码

| 错误码 | HTTP | 说明 |
|---|---|---|
| `REVERSAL_SOURCE_NOT_FOUND` | 404 | 来源不存在 |
| `REVERSAL_SOURCE_STATUS_INVALID` | 409 | 来源状态不允许反向业务 |
| `REVERSAL_STATUS_NOT_ALLOWED` | 409 | 当前状态不允许操作 |
| `REVERSAL_QUANTITY_INVALID` | 400 | 数量不合法 |
| `REVERSAL_AMOUNT_INVALID` | 400 | 金额不合法 |
| `REVERSAL_QUANTITY_EXCEEDS_AVAILABLE` | 409 | 退货或退料数量超过可退数量 |
| `REVERSAL_AMOUNT_EXCEEDS_AVAILABLE` | 409 | 冲减金额超过可冲金额 |
| `REVERSAL_STOCK_INSUFFICIENT` | 409 | 反向出库库存不足 |
| `REVERSAL_DUPLICATED` | 409 | 重复反冲或重复过账 |
| `REVERSAL_POSTED_IMMUTABLE` | 409 | 已过账反向单不可编辑 |
| `REVERSAL_TRACE_RESTRICTED` | 403 | 来源追溯受限 |
| `REVERSAL_CONCURRENT_MODIFICATION` | 409 | 并发更新冲突 |

## 联调验收

- 管理员能从已过账销售出库创建并过账销售退货，库存增加、应收冲减、报表销售净额减少。
- 管理员能从已过账采购入库创建并过账采购退货，库存减少、应付冲减、报表采购净额减少。
- 管理员能从已过账生产领料创建并过账生产退料，库存增加、工单材料消耗和成本净额减少。
- 管理员能创建生产补料，库存减少、工单材料消耗和成本净额增加。
- 超退、超冲、库存不足、来源未过账和重复过账返回受控错误。
- 只读用户可查看授权列表和详情，不可创建或过账。
- 来源受限用户不能看到无权限来源详情。
- 报表追溯能同时看到正向来源和反向来源。

## 明确排除范围

- 不实现审批流、工作流引擎、售后维修、换货、复杂质量体系。
- 不实现正式红字发票、税务申报、银行退款支付、总账凭证、结账。
- 不实现多组织、多公司、多账套、多币种、多租户。
- 不直接删除或改写已过账原单。
