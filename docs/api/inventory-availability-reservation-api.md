# 库存可用量、占用、冻结与预留接口契约

## 目标

定义 018 库存可用量、占用、冻结与预留阶段的接口范围、字段、权限、错误码和联调验收规则。本契约承接 017 质量状态接口，扩展库存可用量、占用预留、物料级采购在途参考、仓库级现货可承诺量和计划净需求缺口口径。

## 通用规则

- 所有接口使用统一 `ApiResponse<T>` 包装。
- 分页接口返回 `PageResponse<T>`。
- 写接口需要 CSRF token。
- 日期字段使用 `YYYY-MM-DD`，时间字段使用带时区的 ISO 8601 字符串。
- 数量字段后端使用 `BigDecimal` 和数据库 `NUMERIC(18,6)`；前端请求体中的业务数量必须使用字符串。
- 后端接口鉴权是最终安全边界，前端按钮和菜单控制只作为体验控制。
- 预留、占用、释放、消费和采购在途参考口径变更必须记录审计。
- 影响经营口径的写入必须接入 `BusinessPeriodGuard`。
- 前端不得自行推导 `availableQuantity`、`availableToPromiseQuantity` 或 `maxSelectableQuantity`。
- 018 起 `availableQuantity` 在库存余额和业务候选中表示合格现货净可用；物料级采购在途通过 `inTransitQuantity` 单列返回，仅供参考。

## 固定枚举

### 库存保留类型

| 值 | 中文名 | 说明 |
|---|---|---|
| `RESERVATION` | 预留 | 确认需求形成的软预留 |
| `OCCUPATION` | 占用 | 执行单据形成的硬占用 |

### 库存保留状态

| 值 | 中文名 | 说明 |
|---|---|---|
| `ACTIVE` | 生效中 | 仍占用或预留可用量 |
| `RELEASED` | 已释放 | 来源取消、关闭或数量减少后释放 |
| `CONSUMED` | 已消耗 | 来源过账后转为实际库存扣减 |
| `CANCELLED` | 已取消 | 来源作废或异常终止 |

### 库存保留来源

| 值 | 中文名 | 类型 |
|---|---|---|
| `SALES_ORDER` | 销售订单 | 预留 |
| `SALES_SHIPMENT` | 销售出库 | 占用 |
| `PRODUCTION_WORK_ORDER` | 生产工单 | 预留 |
| `PRODUCTION_MATERIAL_ISSUE` | 生产领料 | 占用 |
| `PRODUCTION_MATERIAL_SUPPLEMENT` | 生产补料 | 占用 |
| `PURCHASE_RETURN` | 采购退货 | 占用 |
| `INVENTORY_ADJUSTMENT` | 库存调减 | 占用 |

## 接口分组

| 资源 | 路径 | 能力 |
|---|---|---|
| 库存余额 | `/api/admin/inventory/balances` | 展示可用量、占用、预留、冻结、在途和可承诺 |
| 库存占用预留 | `/api/admin/inventory/reservations` | list/get |
| 库存流水 | `/api/admin/inventory/movements` | 保留质量状态和来源追溯 |
| 销售订单和出库 | `/api/admin/sales/*` | 生成、释放、消费销售预留和占用 |
| 生产工单和领料 | `/api/admin/production/*` | 生成、释放、消费生产用料预留和占用 |
| 采购订单和入库 | `/api/admin/procurement/*` | 展示和消减物料级采购在途参考 |

## 库存余额扩展字段

`GET /api/admin/inventory/balances` 在 017 字段基础上新增或明确以下字段：

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| `bookQuantity` | string | 是 | 账面库存，所有质量状态现存合计 |
| `quantityOnHand` | string | 是 | 兼容字段；不传质量状态时等同账面库存，传质量状态时为指定状态现存 |
| `qualifiedQuantity` | string | 是 | 合格现存 |
| `reservedQuantity` | string | 是 | 生效中的预留数量 |
| `occupiedQuantity` | string | 是 | 生效中的占用数量 |
| `lockedQuantity` | string | 是 | 兼容字段；等于预留加占用汇总 |
| `availableQuantity` | string | 是 | 现货净可用，合格现存减预留和占用 |
| `frozenQuantity` | string | 是 | 冻结库存，质量状态为 `FROZEN` 的现存 |
| `pendingInspectionQuantity` | string | 是 | 待检库存 |
| `rejectedQuantity` | string | 是 | 不合格库存 |
| `inTransitQuantity` | string | 是 | 物料级有效采购在途参考，不按仓库计算 |
| `availableToPromiseQuantity` | string | 是 | 仓库级现货可承诺量，018 等于现货净可用 |
| `netRequirementShortageQuantity` | string | 是 | 计划净需求前置非负缺口，`max(需求数量 - 可覆盖供给数量, 0)` |
| `unavailableReason` | string | 否 | 不可用说明 |

字段规则：

- `availableQuantity` 不得包含待检、不合格、冻结、已预留或已占用库存。
- `inTransitQuantity` 不进入 `bookQuantity`。
- `inTransitQuantity` 是物料级参考，不进入仓库级现货可承诺量、销售确认门禁、出库上限或领料上限。
- `availableToPromiseQuantity` 在 018 表示仓库级现货可承诺量，不叠加物料级采购在途参考。
- 前端不得用 `bookQuantity` 或 `qualifiedQuantity` 自行推导出库或领料上限。

## 占用预留分页列表

- 方法：`GET`
- 路径：`/api/admin/inventory/reservations`
- 权限：`inventory:reservation:view`
- 查询参数：
  - `page`、`pageSize`。
  - `keyword`：物料、仓库、来源单号。
  - `warehouseId`、`materialId`。
  - `reservationType`：`RESERVATION` 或 `OCCUPATION`。
  - `status`：`ACTIVE`、`RELEASED`、`CONSUMED`、`CANCELLED`。
  - `sourceType`、`sourceId`、`sourceLineId`。
  - `businessDateFrom`、`businessDateTo`。
- 成功响应：`PageResponse<InventoryReservationSummary>`。

### `InventoryReservationSummary`

| 字段 | 类型 | 响应必返 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 记录标识 |
| `reservationNo` | string | 是 | 编号 |
| `reservationType` | string | 是 | 类型 |
| `reservationTypeName` | string | 是 | 类型中文名 |
| `status` | string | 是 | 状态 |
| `statusName` | string | 是 | 状态中文名 |
| `warehouseId`、`warehouseName` | number/string | 是 | 仓库 |
| `materialId`、`materialCode`、`materialName` | number/string/string | 是 | 物料 |
| `unitId`、`unitName` | number/string | 是 | 单位 |
| `qualityStatus`、`qualityStatusName` | string/string | 是 | 质量状态 |
| `quantity` | string | 是 | 原始数量 |
| `remainingQuantity` | string | 是 | 当前仍占用或预留数量 |
| `releasedQuantity` | string | 是 | 已释放数量 |
| `consumedQuantity` | string | 是 | 已消耗数量 |
| `sourceType`、`sourceTypeName` | string/string | 是 | 来源类型 |
| `sourceId`、`sourceLineId` | number/number | 是 | 来源标识 |
| `sourceDocumentNo` | string | 是 | 来源单号 |
| `businessDate` | string | 是 | 业务日期 |
| `reason`、`remark` | string | 否 | 原因和备注 |
| `createdByName`、`createdAt` | string/string | 是 | 创建信息 |
| `releasedByName`、`releasedAt` | string/string | 否 | 释放信息 |

## 占用预留详情

- 方法：`GET`
- 路径：`/api/admin/inventory/reservations/{id}`
- 权限：`inventory:reservation:view`
- 成功响应：`InventoryReservationDetail`。
- 不存在返回：`INVENTORY_RESERVATION_NOT_FOUND`。

详情必须包含来源摘要 `sourceSummary` 和审计记录 `auditRecords`。

## 销售接口扩展

销售订单行新增字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `reservationWarehouseId` | number | 预留仓库，销售订单确认前必填 |
| `reservationWarehouseName` | string | 预留仓库名称 |

销售订单确认：

- `POST/PUT` 现有确认接口成功前，必须校验每个销售订单行存在 `reservationWarehouseId`。
- 确认成功后，必须按订单行预留仓库为未出库数量生成 `SALES_ORDER + RESERVATION`。
- 销售确认门禁是订单行预留仓库的现货净可用是否足够；物料级采购在途只做参考，不允许预留在途。
- 现货不足但物料级采购在途足够时，销售订单保持草稿并返回可展示的现货不足提示。
- 同一销售订单行不能重复生成活跃预留。
- 取消、关闭或数量减少必须释放剩余预留。
- 销售出库仓库必须与来源订单行 `reservationWarehouseId` 一致；不一致时必须拒绝，不自动跨仓释放、改仓或分配。

销售出库候选字段新增：

| 字段 | 类型 | 说明 |
|---|---|---|
| `reservedQuantity` | string | 其他来源预留数量 |
| `occupiedQuantity` | string | 其他执行单据占用数量 |
| `availableQuantity` | string | 当前来源可使用的现货净可用 |
| `availableToPromiseQuantity` | string | 仓库级现货可承诺量 |
| `maxSelectableQuantity` | string | 本次最多出库，唯一输入上限 |
| `disabledReasonCode` | string | 不可选原因码 |
| `disabledReason` | string | 不可选原因 |

销售出库过账必须消费对应销售订单预留或销售出库占用，再扣减合格库存。

## 生产接口扩展

生产工单发布后，按 BOM 快照、领料仓库和未领用量生成 `PRODUCTION_WORK_ORDER + RESERVATION`。取消、完成或用料减少释放剩余预留。工单发布不得直接生成 `OCCUPATION`。

生产领料草稿如建立执行锁定，则生成或转换为 `PRODUCTION_MATERIAL_ISSUE + OCCUPATION`；草稿取消或删除时释放占用并恢复剩余预留。生产领料候选字段与销售一致，但 `maxSelectableQuantity` 展示为“本次最多领料”。生产领料过账必须消费对应工单预留或领料占用，再扣减合格库存。

## 采购接口扩展

采购订单列表、详情和来源候选新增：

| 字段 | 类型 | 说明 |
|---|---|---|
| `inTransitQuantity` | string | 当前物料级有效在途数量 |
| `expectedArrivalDate` | string | 预计到货日期 |
| `inTransitStatus` | string | `NORMAL`、`DUE_SOON`、`OVERDUE`、`NOT_COUNTED` |
| `inTransitStatusName` | string | 中文状态 |

有效在途只包含已确认或部分入库且未关闭、未取消的采购订单未入库数量。本阶段不在采购订单行增加仓库字段，不按仓库计算采购在途，不把采购在途写入库存余额或仓库级现货可承诺量。采购入库过账选择具体入库仓库后，减少物料级在途并形成该仓库待检库存。

## 错误码

| 错误码 | HTTP | 场景 |
|---|---|---|
| `INVENTORY_AVAILABLE_NOT_ENOUGH` | 409 | 现货净可用不足 |
| `INVENTORY_ATP_NOT_ENOUGH` | 409 | 仓库级现货可承诺量不足 |
| `INVENTORY_RESERVATION_NOT_FOUND` | 404 | 占用预留记录不存在 |
| `INVENTORY_RESERVATION_SOURCE_DUPLICATED` | 409 | 来源行重复生成活跃占用或预留 |
| `INVENTORY_RESERVATION_STATUS_INVALID` | 409 | 当前状态不允许释放或消费 |
| `INVENTORY_RESERVATION_CONCURRENT_MODIFICATION` | 409 | 并发占用或释放冲突 |
| `INVENTORY_RESERVED_OR_OCCUPIED_NOT_AVAILABLE` | 409 | 库存已被占用或预留导致不可用 |
| `INVENTORY_IN_TRANSIT_NOT_AVAILABLE` | 409 | 在途库存不可直接出库或领料 |
| `BUSINESS_PERIOD_LOCKED` | 409 | 写入业务日期落入已锁定期间 |
| `AUTH_FORBIDDEN` | 403 | 无接口权限 |

## 权限编码

| 权限编码 | 类型 | 说明 |
|---|---|---|
| `inventory:availability:view` | 操作 | 查看库存可用量口径 |
| `inventory:reservation:view` | 操作 | 查看占用预留台账 |
| `inventory:balance:view` | 操作 | 查看库存余额 |
| `inventory:movement:view` | 操作 | 查看库存流水 |

本阶段不开放手工预留和手工释放页面，不新增 `create/release` 前端入口；后端内部释放和消费由销售、生产、采购等授权动作触发。

## 联调验收

- 库存余额能同时返回账面、合格、占用、预留、冻结、现货净可用、采购在途和可承诺量。
- 销售订单行确认前必须填写预留仓库；销售订单确认按预留仓库生成预留，取消、关闭、出库过账能释放或消费预留；销售出库仓库与来源订单行预留仓库不一致必须被拒绝。
- 生产工单发布生成 `PRODUCTION_WORK_ORDER + RESERVATION`，取消、完成、领料过账能释放或消费预留；工单发布直接生成占用属于错误。
- 采购订单确认形成物料级采购在途参考，采购入库后在途减少并进入具体仓库待检库存。
- 冻结已预留或已占用库存被拒绝。
- 账面库存充足但现货净可用不足时返回稳定错误码，不使用普通库存不足掩盖。
- 现货不足但物料级采购在途足够时，销售订单不得确认并预留在途。
- 只读用户可查看口径字段，不可执行写动作。
- 锁定期间内影响占用、预留、在途或库存事实的写入被拒绝，历史查询和追溯仍可访问。
