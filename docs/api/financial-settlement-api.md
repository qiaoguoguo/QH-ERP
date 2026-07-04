# 财务往来基础接口契约

## 目标

定义财务往来基础阶段的接口范围、字段、权限、错误码、来源追溯、金额事务一致性和联调验收规则，作为前后端实现与测试依据。本阶段只建立应收台账、收款记录、应付台账、付款记录、来源追溯和余额核销，不提供总账、凭证、会计科目、会计期间、结账、税务申报、完整发票生命周期、银行对账、正式收入确认、正式成本结转、BI 报表、多币种、多组织、多公司、多账套、多租户或正式财务核算能力。

## 通用规则

- 所有接口使用统一 `ApiResponse<T>` 包装。
- 分页接口返回 `PageResponse<T>`。
- 时间字段使用后端生成的 ISO 8601 字符串。
- 写接口需要 CSRF token。
- 认证和权限沿用账号权限模块。
- 后端接口鉴权是最终安全边界，前端菜单和按钮权限只作为体验控制。
- 金额字段使用后端 `BigDecimal` 和数据库 `numeric(18,2)`，前端 payload 使用字符串，禁止使用浮点数承载业务计算。
- 来源数量继续沿用采购和销售阶段的数量精度；生成往来金额时按来源单价和来源数量计算后保留两位小数。
- 应收和应付金额由后端根据来源单据生成，前端不得传入 `totalAmount`、`receivedAmount`、`unreceivedAmount`、`paidAmount` 或 `unpaidAmount` 的最终值。
- 收付款过账必须在单个数据库事务内完成单据状态、核销明细、台账累计金额、未结余额和状态更新。
- 写操作成功后必须生成审计日志，至少记录操作人、动作、目标类型、目标标识、金额变化、状态变化和操作时间。
- `VALIDATION_ERROR`、`AUTH_FORBIDDEN`、`AUTH_UNAUTHORIZED` 沿用账号权限模块或通用错误码规则。

## 状态与枚举

### 应收状态

| 值 | 说明 |
|---|---|
| `DRAFT` | 草稿，来源已生成，可编辑到期日期和备注，可确认、可取消 |
| `CONFIRMED` | 待收款，可登记收款，可关闭 |
| `PARTIALLY_RECEIVED` | 部分收款，可继续登记收款，可关闭 |
| `RECEIVED` | 已收清，不允许继续收款 |
| `CLOSED` | 已关闭，不允许继续收款 |
| `CANCELLED` | 已取消，不允许确认或收款 |

### 收款状态

| 值 | 说明 |
|---|---|
| `DRAFT` | 草稿，可编辑、可过账、可取消 |
| `POSTED` | 已过账，已更新应收余额，不可编辑、不可取消 |
| `CANCELLED` | 已取消，仅草稿可取消 |

### 应付状态

| 值 | 说明 |
|---|---|
| `DRAFT` | 草稿，来源已生成，可编辑到期日期和备注，可确认、可取消 |
| `CONFIRMED` | 待付款，可登记付款，可关闭 |
| `PARTIALLY_PAID` | 部分付款，可继续登记付款，可关闭 |
| `PAID` | 已付清，不允许继续付款 |
| `CLOSED` | 已关闭，不允许继续付款 |
| `CANCELLED` | 已取消，不允许确认或付款 |

### 付款状态

| 值 | 说明 |
|---|---|
| `DRAFT` | 草稿，可编辑、可过账、可取消 |
| `POSTED` | 已过账，已更新应付余额，不可编辑、不可取消 |
| `CANCELLED` | 已取消，仅草稿可取消 |

### 来源类型

| 值 | 说明 |
|---|---|
| `SALES_SHIPMENT` | 已过账销售出库，生成应收来源 |
| `PURCHASE_RECEIPT` | 已过账采购入库，生成应付来源 |

## 主要表草案

### `fin_receivable`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigserial` | 主键 |
| `receivable_no` | `varchar(64)` | 应收单号，唯一 |
| `customer_id` | `bigint` | 客户标识 |
| `source_type` | `varchar(32)` | 来源类型，固定 `SALES_SHIPMENT` |
| `source_id` | `bigint` | 来源销售出库标识 |
| `source_no` | `varchar(64)` | 来源销售出库单号 |
| `business_date` | `date` | 业务日期 |
| `due_date` | `date` | 到期日期 |
| `total_amount` | `numeric(18,2)` | 应收金额 |
| `received_amount` | `numeric(18,2)` | 已收金额 |
| `unreceived_amount` | `numeric(18,2)` | 未收金额 |
| `status` | `varchar(32)` | 应收状态 |
| `remark` | `varchar(500)` | 备注 |
| `created_by`、`created_at` | | 创建审计字段 |
| `updated_by`、`updated_at` | | 更新审计字段 |
| `confirmed_by`、`confirmed_at` | | 确认信息 |
| `closed_by`、`closed_at` | | 关闭信息 |
| `cancelled_by`、`cancelled_at` | | 取消信息 |
| `version` | `bigint` | 乐观版本 |

### `fin_receivable_source`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigserial` | 主键 |
| `receivable_id` | `bigint` | 应收标识 |
| `source_type` | `varchar(32)` | 来源类型 |
| `source_id` | `bigint` | 来源单据标识 |
| `source_no` | `varchar(64)` | 来源单据号 |
| `source_line_id` | `bigint` | 来源明细标识 |
| `source_line_no` | `integer` | 来源行号 |
| `source_amount` | `numeric(18,2)` | 来源金额 |

### `fin_receipt`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigserial` | 主键 |
| `receipt_no` | `varchar(64)` | 收款单号，唯一 |
| `customer_id` | `bigint` | 客户标识 |
| `receipt_date` | `date` | 收款日期 |
| `amount` | `numeric(18,2)` | 收款金额 |
| `method` | `varchar(32)` | 收款方式 |
| `status` | `varchar(32)` | 收款状态 |
| `remark` | `varchar(500)` | 备注 |
| `created_by`、`created_at` | | 创建审计字段 |
| `updated_by`、`updated_at` | | 更新审计字段 |
| `posted_by`、`posted_at` | | 过账信息 |
| `cancelled_by`、`cancelled_at` | | 取消信息 |
| `version` | `bigint` | 乐观版本 |

### `fin_receipt_allocation`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigserial` | 主键 |
| `receipt_id` | `bigint` | 收款标识 |
| `receivable_id` | `bigint` | 应收标识 |
| `allocated_amount` | `numeric(18,2)` | 本次核销金额 |

本阶段一笔收款只核销一笔应收，`receipt_id` 必须唯一，且过账时 `fin_receipt.amount = fin_receipt_allocation.allocated_amount`。保留核销明细表是为了统一追溯结构，不开放多应收批量核销。

### `fin_payable`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigserial` | 主键 |
| `payable_no` | `varchar(64)` | 应付单号，唯一 |
| `supplier_id` | `bigint` | 供应商标识 |
| `source_type` | `varchar(32)` | 来源类型，固定 `PURCHASE_RECEIPT` |
| `source_id` | `bigint` | 来源采购入库标识 |
| `source_no` | `varchar(64)` | 来源采购入库单号 |
| `business_date` | `date` | 业务日期 |
| `due_date` | `date` | 到期日期 |
| `total_amount` | `numeric(18,2)` | 应付金额 |
| `paid_amount` | `numeric(18,2)` | 已付金额 |
| `unpaid_amount` | `numeric(18,2)` | 未付金额 |
| `status` | `varchar(32)` | 应付状态 |
| `remark` | `varchar(500)` | 备注 |
| 审计字段和 `version` | | 与应收一致 |

### `fin_payable_source`

字段与 `fin_receivable_source` 对称，用于记录采购入库和采购订单来源。

### `fin_payment`

字段与 `fin_receipt` 对称，`payment_no`、`supplier_id`、`payment_date`、`amount`、`method`、`status` 等用于付款记录。

### `fin_payment_allocation`

字段与 `fin_receipt_allocation` 对称，用于记录付款核销应付金额。

本阶段一笔付款只核销一笔应付，`payment_id` 必须唯一，且过账时 `fin_payment.amount = fin_payment_allocation.allocated_amount`。不开放多应付批量核销。

## 字段定义

### 应收汇总字段

| 字段 | 类型 | 请求必填 | 响应必返 | 说明 |
|---|---|---|---|---|
| id | number | 否 | 是 | 后端主键 |
| receivableNo | string | 否 | 是 | 应收单号 |
| customerId | number | 否 | 是 | 客户标识 |
| customerCode | string | 否 | 是 | 客户编码 |
| customerName | string | 否 | 是 | 客户名称 |
| sourceType | string | 否 | 是 | 来源类型 |
| sourceId | number | 是 | 是 | 来源销售出库标识 |
| sourceNo | string | 否 | 是 | 来源销售出库单号 |
| salesOrderId | number | 否 | 是 | 来源销售订单标识 |
| salesOrderNo | string | 否 | 是 | 来源销售订单号 |
| businessDate | string | 否 | 是 | 业务日期 |
| dueDate | string | 是 | 是 | 到期日期 |
| totalAmount | string | 否 | 是 | 应收金额 |
| receivedAmount | string | 否 | 是 | 已收金额 |
| unreceivedAmount | string | 否 | 是 | 未收金额 |
| status | string | 否 | 是 | 应收状态 |
| remark | string | 否 | 否 | 备注 |
| createdByName | string | 否 | 是 | 创建人 |
| createdAt | string | 否 | 是 | 创建时间 |
| updatedAt | string | 否 | 是 | 更新时间 |

### 应收详情字段

详情响应在应收汇总字段基础上增加：

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| sources | array | 是 | 来源销售出库明细 |
| receipts | array | 是 | 收款记录摘要 |
| auditSummary | array | 否 | 审计摘要 |

### 应收来源明细字段

`sources` 数组元素使用 `ReceivableSourceRecord`：

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| id | number | 是 | 来源记录标识 |
| sourceType | string | 是 | 固定为 `SALES_SHIPMENT` |
| sourceId | number | 是 | 销售出库标识 |
| sourceNo | string | 是 | 销售出库单号 |
| sourceLineId | number | 是 | 销售出库明细标识 |
| sourceLineNo | number | 是 | 销售出库明细行号 |
| sourceBusinessDate | string | 是 | 销售出库业务日期 |
| salesOrderId | number | 是 | 来源销售订单标识 |
| salesOrderNo | string | 是 | 来源销售订单号 |
| salesOrderLineId | number | 是 | 来源销售订单明细标识 |
| materialId | number | 是 | 物料标识 |
| materialCode | string | 是 | 物料编码 |
| materialName | string | 是 | 物料名称 |
| unitName | string | 是 | 单位名称 |
| quantity | string | 是 | 来源出库数量 |
| unitPrice | string | 是 | 来源销售单价 |
| sourceAmount | string | 是 | 来源金额 |

### 收款核销字段

`ReceiptDetail` 中的 `allocations` 数组元素使用 `ReceiptAllocationRecord`：

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| id | number | 是 | 核销记录标识 |
| receiptId | number | 是 | 收款标识 |
| receiptNo | string | 是 | 收款单号 |
| receivableId | number | 是 | 应收标识 |
| receivableNo | string | 是 | 应收单号 |
| customerId | number | 是 | 客户标识 |
| customerName | string | 是 | 客户名称 |
| allocatedAmount | string | 是 | 核销金额 |

本阶段 `allocations` 固定为一条记录，`allocatedAmount` 必须等于收款金额。

### 收款汇总字段

| 字段 | 类型 | 请求必填 | 响应必返 | 说明 |
|---|---|---|---|---|
| id | number | 否 | 是 | 后端主键 |
| receiptNo | string | 否 | 是 | 收款单号 |
| receivableId | number | 是 | 是 | 应收标识 |
| receivableNo | string | 否 | 是 | 应收单号 |
| customerId | number | 否 | 是 | 客户标识 |
| customerName | string | 否 | 是 | 客户名称 |
| receiptDate | string | 是 | 是 | 收款日期 |
| amount | string | 是 | 是 | 收款金额 |
| method | string | 是 | 是 | 收款方式 |
| status | string | 否 | 是 | 收款状态 |
| remark | string | 否 | 否 | 备注 |
| createdByName | string | 否 | 是 | 创建人 |
| postedByName | string | 否 | 否 | 过账人 |
| postedAt | string | 否 | 否 | 过账时间 |

### 应付来源明细字段

`PayableDetail` 的 `sources` 数组元素使用 `PayableSourceRecord`：

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| id | number | 是 | 来源记录标识 |
| sourceType | string | 是 | 固定为 `PURCHASE_RECEIPT` |
| sourceId | number | 是 | 采购入库标识 |
| sourceNo | string | 是 | 采购入库单号 |
| sourceLineId | number | 是 | 采购入库明细标识 |
| sourceLineNo | number | 是 | 采购入库明细行号 |
| sourceBusinessDate | string | 是 | 采购入库业务日期 |
| purchaseOrderId | number | 是 | 来源采购订单标识 |
| purchaseOrderNo | string | 是 | 来源采购订单号 |
| purchaseOrderLineId | number | 是 | 来源采购订单明细标识 |
| materialId | number | 是 | 物料标识 |
| materialCode | string | 是 | 物料编码 |
| materialName | string | 是 | 物料名称 |
| unitName | string | 是 | 单位名称 |
| quantity | string | 是 | 来源入库数量 |
| unitPrice | string | 是 | 来源采购单价 |
| sourceAmount | string | 是 | 来源金额 |

### 付款核销字段

`PaymentDetail` 中的 `allocations` 数组元素使用 `PaymentAllocationRecord`：

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| id | number | 是 | 核销记录标识 |
| paymentId | number | 是 | 付款标识 |
| paymentNo | string | 是 | 付款单号 |
| payableId | number | 是 | 应付标识 |
| payableNo | string | 是 | 应付单号 |
| supplierId | number | 是 | 供应商标识 |
| supplierName | string | 是 | 供应商名称 |
| allocatedAmount | string | 是 | 核销金额 |

本阶段 `allocations` 固定为一条记录，`allocatedAmount` 必须等于付款金额。

### 应付与付款字段

应付字段与应收对称，客户字段替换为供应商字段，来源替换为采购订单和采购入库；付款字段与收款对称，`receipt` 命名替换为 `payment`。

## 接口分组

| 资源 | 路径 | 能力 |
|---|---|---|
| 应收台账 | `/api/admin/finance/receivables` | list/get/create/update/confirm/cancel/close |
| 收款记录 | `/api/admin/finance/receipts` | list/get/create/update/post/cancel |
| 应付台账 | `/api/admin/finance/payables` | list/get/create/update/confirm/cancel/close |
| 付款记录 | `/api/admin/finance/payments` | list/get/create/update/post/cancel |
| 应收候选来源 | `/api/admin/finance/receivable-sources` | list |
| 应付候选来源 | `/api/admin/finance/payable-sources` | list |

## 接口定义

### 应收候选来源分页列表

- 方法：`GET`
- 路径：`/api/admin/finance/receivable-sources`
- 权限：`finance:receivable:create`
- 查询参数：
  - `page`、`pageSize`。
  - `keyword`：销售出库单号、销售订单号、客户编码、客户名称。
  - `customerId`：客户标识。
  - `dateFrom`、`dateTo`：销售出库业务日期范围。
  - `settlementGenerated`：是否已生成应收；生成表单默认传 `false`。
- 规则：
  - 只返回状态为 `POSTED` 的销售出库。
  - `settlementGenerated=false` 时，任一来源明细已生成应收的销售出库不返回。
- 成功响应：`PageResponse<ReceivableCandidateSource>`。

`ReceivableCandidateSource` 字段：

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| sourceType | string | 是 | 固定为 `SALES_SHIPMENT` |
| sourceId | number | 是 | 销售出库标识 |
| sourceNo | string | 是 | 销售出库单号 |
| salesOrderId | number | 是 | 来源销售订单标识 |
| salesOrderNo | string | 是 | 来源销售订单号 |
| customerId | number | 是 | 客户标识 |
| customerCode | string | 是 | 客户编码 |
| customerName | string | 是 | 客户名称 |
| businessDate | string | 是 | 出库业务日期 |
| totalAmount | string | 是 | 按来源明细计算的应收候选金额 |
| lineCount | number | 是 | 来源明细行数 |
| settlementGenerated | boolean | 是 | 是否已生成应收 |

### 应付候选来源分页列表

- 方法：`GET`
- 路径：`/api/admin/finance/payable-sources`
- 权限：`finance:payable:create`
- 查询参数：
  - `page`、`pageSize`。
  - `keyword`：采购入库单号、采购订单号、供应商编码、供应商名称。
  - `supplierId`：供应商标识。
  - `dateFrom`、`dateTo`：采购入库业务日期范围。
  - `settlementGenerated`：是否已生成应付；生成表单默认传 `false`。
- 规则：
  - 只返回状态为 `POSTED` 的采购入库。
  - `settlementGenerated=false` 时，任一来源明细已生成应付的采购入库不返回。
- 成功响应：`PageResponse<PayableCandidateSource>`。

`PayableCandidateSource` 字段：

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| sourceType | string | 是 | 固定为 `PURCHASE_RECEIPT` |
| sourceId | number | 是 | 采购入库标识 |
| sourceNo | string | 是 | 采购入库单号 |
| purchaseOrderId | number | 是 | 来源采购订单标识 |
| purchaseOrderNo | string | 是 | 来源采购订单号 |
| supplierId | number | 是 | 供应商标识 |
| supplierCode | string | 是 | 供应商编码 |
| supplierName | string | 是 | 供应商名称 |
| businessDate | string | 是 | 入库业务日期 |
| totalAmount | string | 是 | 按来源明细计算的应付候选金额 |
| lineCount | number | 是 | 来源明细行数 |
| settlementGenerated | boolean | 是 | 是否已生成应付 |

### 应收分页列表

- 方法：`GET`
- 路径：`/api/admin/finance/receivables`
- 权限：`finance:receivable:view`
- 查询参数：
  - `page`、`pageSize`。
  - `keyword`：应收单号、客户编码、客户名称、来源销售订单号或来源销售出库单号。
  - `customerId`：客户标识。
  - `status`：应收状态。
  - `dateFrom`、`dateTo`：业务日期范围。
  - `dueDateFrom`、`dueDateTo`：到期日期范围。
  - `sourceNo`：来源单号。
- 成功响应：`PageResponse<ReceivableSummary>`。

### 应收详情

- 方法：`GET`
- 路径：`/api/admin/finance/receivables/{id}`
- 权限：`finance:receivable:view`
- 成功响应：`ReceivableDetail`。
- 不存在返回：`FINANCE_RECEIVABLE_NOT_FOUND`。

### 基于销售出库生成应收

- 方法：`POST`
- 路径：`/api/admin/finance/receivables`
- 权限：`finance:receivable:create`
- 请求体：`{ sourceType: "SALES_SHIPMENT", sourceId: number, dueDate: string, remark?: string }`。
- 规则：
  - 来源销售出库必须存在且状态为 `POSTED`。
  - 来源销售出库必须至少有一条明细。
  - 同一销售出库明细不得重复生成应收来源。
  - 同一来源销售出库任一明细已生成应收时，整单生成失败并返回 `FINANCE_SOURCE_DUPLICATED`，不得创建新的应收主表或部分来源明细。
  - 应收金额由后端按来源销售订单单价和销售出库数量计算，前端不得传入金额。
  - 新建状态固定为 `DRAFT`。
- 成功响应：`ReceivableDetail`。

### 更新应收草稿

- 方法：`PUT`
- 路径：`/api/admin/finance/receivables/{id}`
- 权限：`finance:receivable:update`
- 请求体：`{ dueDate: string, remark?: string }`。
- 规则：仅 `DRAFT` 应收可更新，到期日期和备注可改，来源和金额不可改。
- 成功响应：`ReceivableDetail`。

### 确认应收

- 方法：`PUT`
- 路径：`/api/admin/finance/receivables/{id}/confirm`
- 权限：`finance:receivable:confirm`
- 规则：仅 `DRAFT` 应收可确认，确认时重新校验来源仍存在且已过账、金额大于 0、来源未重复。
- 成功响应：`ReceivableDetail`。

### 取消应收

- 方法：`PUT`
- 路径：`/api/admin/finance/receivables/{id}/cancel`
- 权限：`finance:receivable:cancel`
- 规则：仅 `DRAFT` 或未发生收款的 `CONFIRMED` 应收可取消。
- 成功响应：`ReceivableDetail`。

### 关闭应收

- 方法：`PUT`
- 路径：`/api/admin/finance/receivables/{id}/close`
- 权限：`finance:receivable:close`
- 规则：`CONFIRMED` 或 `PARTIALLY_RECEIVED` 应收可关闭，关闭后不可继续登记收款。
- 成功响应：`ReceivableDetail`。

### 收款分页列表

- 方法：`GET`
- 路径：`/api/admin/finance/receipts`
- 权限：`finance:receipt:view`
- 查询参数：`page`、`pageSize`、`keyword`、`customerId`、`status`、`dateFrom`、`dateTo`、`receivableId`。
- 成功响应：`PageResponse<ReceiptSummary>`。

### 创建收款草稿

- 方法：`POST`
- 路径：`/api/admin/finance/receivables/{id}/receipts`
- 权限：`finance:receipt:create`
- 请求体：`{ receiptDate: string, amount: string, method: string, remark?: string }`。
- 规则：
  - 应收必须为 `CONFIRMED` 或 `PARTIALLY_RECEIVED`。
  - 收款金额必须大于 0 且不得超过当前未收金额。
  - 新建状态固定为 `DRAFT`，不更新应收余额。
- 成功响应：`ReceiptDetail`。

### 收款详情

- 方法：`GET`
- 路径：`/api/admin/finance/receipts/{id}`
- 权限：`finance:receipt:view`
- 成功响应：`ReceiptDetail`。

### 更新收款草稿

- 方法：`PUT`
- 路径：`/api/admin/finance/receipts/{id}`
- 权限：`finance:receipt:update`
- 规则：仅 `DRAFT` 收款可更新；更新时重新校验目标应收状态和未收金额。
- 成功响应：`ReceiptDetail`。

### 过账收款

- 方法：`PUT`
- 路径：`/api/admin/finance/receipts/{id}/post`
- 权限：`finance:receipt:post`
- 规则：
  - 仅 `DRAFT` 收款可过账。
  - 目标应收必须为 `CONFIRMED` 或 `PARTIALLY_RECEIVED`。
  - 过账时锁定收款和目标应收，校验收款金额不超过未收金额。
  - 插入核销明细、更新收款状态、更新应收累计金额和状态必须同事务完成。
  - 更新后未收金额为 0 时应收状态为 `RECEIVED`，否则为 `PARTIALLY_RECEIVED`。
- 成功响应：`ReceiptDetail`。

### 取消收款草稿

- 方法：`PUT`
- 路径：`/api/admin/finance/receipts/{id}/cancel`
- 权限：`finance:receipt:cancel`
- 规则：仅 `DRAFT` 收款可取消；`POSTED` 收款不可取消。
- 成功响应：`ReceiptDetail`。

### 应付和付款接口

应付和付款接口与应收、收款对称：

- `GET /api/admin/finance/payables`
- `GET /api/admin/finance/payables/{id}`
- `POST /api/admin/finance/payables`，请求体 `{ sourceType: "PURCHASE_RECEIPT", sourceId: number, dueDate: string, remark?: string }`
- `PUT /api/admin/finance/payables/{id}`
- `PUT /api/admin/finance/payables/{id}/confirm`
- `PUT /api/admin/finance/payables/{id}/cancel`
- `PUT /api/admin/finance/payables/{id}/close`
- `GET /api/admin/finance/payments`
- `POST /api/admin/finance/payables/{id}/payments`
- `GET /api/admin/finance/payments/{id}`
- `PUT /api/admin/finance/payments/{id}`
- `PUT /api/admin/finance/payments/{id}/post`
- `PUT /api/admin/finance/payments/{id}/cancel`

付款过账规则与收款对称：目标应付必须为 `CONFIRMED` 或 `PARTIALLY_PAID`，付款金额不得超过未付金额，过账后未付金额为 0 时应付状态为 `PAID`，否则为 `PARTIALLY_PAID`。

生成应付规则与生成应收对称：来源采购入库必须存在且状态为 `POSTED`；同一来源采购入库任一明细已生成应付时，整单生成失败并返回 `FINANCE_SOURCE_DUPLICATED`，不得创建新的应付主表或部分来源明细。

## 权限编码

| 权限编码 | 类型 | 说明 |
|---|---|---|
| `finance` | 菜单 | 财务往来 |
| `finance:receivable:view` | 操作 | 查看应收 |
| `finance:receivable:create` | 操作 | 生成应收 |
| `finance:receivable:update` | 操作 | 更新应收草稿 |
| `finance:receivable:confirm` | 操作 | 确认应收 |
| `finance:receivable:cancel` | 操作 | 取消应收 |
| `finance:receivable:close` | 操作 | 关闭应收 |
| `finance:receipt:view` | 操作 | 查看收款 |
| `finance:receipt:create` | 操作 | 创建收款草稿 |
| `finance:receipt:update` | 操作 | 更新收款草稿 |
| `finance:receipt:post` | 操作 | 过账收款 |
| `finance:receipt:cancel` | 操作 | 取消收款草稿 |
| `finance:payable:view` | 操作 | 查看应付 |
| `finance:payable:create` | 操作 | 生成应付 |
| `finance:payable:update` | 操作 | 更新应付草稿 |
| `finance:payable:confirm` | 操作 | 确认应付 |
| `finance:payable:cancel` | 操作 | 取消应付 |
| `finance:payable:close` | 操作 | 关闭应付 |
| `finance:payment:view` | 操作 | 查看付款 |
| `finance:payment:create` | 操作 | 创建付款草稿 |
| `finance:payment:update` | 操作 | 更新付款草稿 |
| `finance:payment:post` | 操作 | 过账付款 |
| `finance:payment:cancel` | 操作 | 取消付款草稿 |

## 错误码

| 错误码 | HTTP | 说明 |
|---|---|---|
| `FINANCE_RECEIVABLE_NOT_FOUND` | 404 | 应收不存在 |
| `FINANCE_RECEIPT_NOT_FOUND` | 404 | 收款不存在 |
| `FINANCE_PAYABLE_NOT_FOUND` | 404 | 应付不存在 |
| `FINANCE_PAYMENT_NOT_FOUND` | 404 | 付款不存在 |
| `FINANCE_SOURCE_NOT_FOUND` | 404 | 来源单据不存在 |
| `FINANCE_SOURCE_STATUS_INVALID` | 409 | 来源单据状态不允许生成往来 |
| `FINANCE_SOURCE_DUPLICATED` | 409 | 来源明细已生成往来 |
| `FINANCE_AMOUNT_INVALID` | 400 | 金额不合法 |
| `FINANCE_ALLOCATION_EXCEEDS_BALANCE` | 409 | 收付款金额超过未结余额 |
| `FINANCE_STATUS_NOT_ALLOWED` | 409 | 当前状态不允许操作 |
| `FINANCE_POSTED_IMMUTABLE` | 409 | 已过账收付款不可编辑或取消 |
| `FINANCE_CONCURRENT_MODIFICATION` | 409 | 并发更新冲突 |
| `FINANCE_DUE_DATE_INVALID` | 400 | 到期日期不合法 |
| `FINANCE_METHOD_INVALID` | 400 | 收付款方式不合法 |

## 事务与金额要求

- 生成应收或应付必须在单事务内完成来源校验、主表插入、来源明细插入和审计日志。
- 确认应收或应付必须重新校验来源和金额快照，不得因来源重复产生双台账。
- 收款或付款过账必须在单事务内完成单据锁定、目标台账锁定、余额校验、核销明细写入、累计金额更新、状态更新和审计日志。
- 任一明细失败必须整体回滚，不得留下已过账收付款但台账余额未更新的状态。
- 任何金额写入前必须校验为大于 0，且小数位不超过 2 位。
- 任何余额更新后必须满足：`totalAmount = receivedAmount + unreceivedAmount` 或 `totalAmount = paidAmount + unpaidAmount`。
- 本阶段收款或付款只允许核销一笔目标台账，收付款金额必须等于唯一核销金额。
- 并发过账同一应收或应付时，必须通过行锁、乐观锁或条件更新阻止超收或超付。

## 约束与索引建议

- 应收单号、收款单号、应付单号、付款单号唯一。
- 来源明细唯一约束：
  - `fin_receivable_source(source_type, source_id, source_line_id)` 唯一。
  - `fin_payable_source(source_type, source_id, source_line_id)` 唯一。
- 状态字段必须使用 check 约束限制枚举值。
- 金额字段必须满足总额、已结金额和未结余额非负。
- 收付款核销明细金额必须大于 0。
- 本阶段 `fin_receipt_allocation(receipt_id)` 唯一，`fin_payment_allocation(payment_id)` 唯一。
- 应收余额恒等式必须落到数据库 check：`total_amount = received_amount + unreceived_amount`。
- 应付余额恒等式必须落到数据库 check：`total_amount = paid_amount + unpaid_amount`。
- 同一来源单据任一明细已生成往来台账时，再次按来源单据头生成必须整单失败并返回 `FINANCE_SOURCE_DUPLICATED`。
- 常用索引至少覆盖客户、供应商、状态日期、到期日期、来源单号、应收收款关联和应付付款关联。

## 联调验收

- 管理员或财务角色可以从已过账销售出库生成应收，确认应收，登记部分收款并过账，再登记剩余收款并过账至已收清。
- 管理员或财务角色可以从已过账采购入库生成应付，确认应付，登记部分付款并过账，再登记剩余付款并过账至已付清。
- 重复来源生成应收或应付返回 `FINANCE_SOURCE_DUPLICATED`。
- 未过账销售出库或采购入库不能生成应收或应付。
- 收款金额超过未收金额返回 `FINANCE_ALLOCATION_EXCEEDS_BALANCE`，应收余额不变。
- 付款金额超过未付金额返回 `FINANCE_ALLOCATION_EXCEEDS_BALANCE`，应付余额不变。
- 已收清应收不可继续收款，已付清应付不可继续付款。
- 已过账收款或付款不可编辑、不可取消。
- 财务角色、销售角色、采购角色、只读用户和无权限用户的菜单、按钮、路由和接口权限一致。
- 所有写操作都有审计日志。

## 明确排除范围

- 不实现总账、凭证、会计科目、会计期间、结账、税务申报、完整发票生命周期、银行对账、报销、固定资产、工资、正式收入确认、正式成本结转、BI 报表。
- 不实现多币种、多组织、多公司、多账套、多租户。
- 不实现销售退货、采购退货、退款、退票、已过账收付款反冲或调整单。
- 不改变采购、销售、库存、生产或成本既有接口路径、响应结构、错误语义和权限语义。
