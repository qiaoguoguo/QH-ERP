# 库存可用量、占用、冻结与预留基础设计规格

## 目标

018 阶段为 QH ERP 建立库存可用量口径底座和轻量业务占用、预留控制。系统需要在 017 质量状态基础上进一步区分账面库存、合格现存、冻结库存、占用库存、预留库存、现货净可用、物料级采购在途参考、仓库级现货可承诺量和计划净需求缺口，为销售可承诺量和计划净需求建立可信口径。

本阶段不是完整 MRP、复杂 ATP、排产或仓储深化。交付重点是让业务单据对库存可用性的影响可记录、可追溯、可校验、可展示。

## 现状

当前库存余额表已经按仓库、物料、质量状态维度存储，`availableQuantity` 在 017 后代表合格库存减锁定量。实际业务尚未维护 `locked_quantity`，销售出库、生产领料等候选库存基本按合格现存计算，无法防止多张销售订单或生产工单重复承诺同一批合格库存。

采购订单有确认、部分入库、已入库、关闭、取消状态，采购订单未入库数量可作为物料级采购在途参考。采购订单行当前没有仓库字段，采购入库单才有入库仓库，因此本阶段不做仓库级采购在途。销售订单行需要补充预留仓库后才能确认，生产工单已有领料仓库，这两类需求应进入仓库级预留口径。

## 核心决策

- 新增库存占用预留台账，记录来源和状态；不把占用预留隐藏在单据行字段里。
- `inv_stock_balance.locked_quantity` 作为合格库存占用和预留汇总缓存，所有更新由统一服务完成。
- 冻结库存继续使用 017 质量状态 `FROZEN`，不新增另一套冻结字段。
- 采购在途由采购订单未入库数量按物料计算，不写入库存余额，不参与立即出库、领料上限或销售确认门禁。
- `availableQuantity` 调整为现货净可用：合格现存减合格占用和合格预留。
- 来源专属候选接口需要把当前来源自己的预留或占用算回可选量，防止自己的预留把自己锁死。
- 计划净需求只输出非负缺口 `netRequirementShortageQuantity`，不生成 MRP 建议或计划订单。
- 销售订单确认只校验订单行预留仓库的现货净可用，不允许预留在途；现货不足但物料级在途足够时仍保持草稿并提示在途仅供参考。

## 数据模型

新增表 `inv_stock_reservation`：

- `id`：主键。
- `reservation_no`：占用预留编号。
- `reservation_type`：`RESERVATION` 或 `OCCUPATION`。
- `status`：`ACTIVE`、`RELEASED`、`CONSUMED`、`CANCELLED`。
- `warehouse_id`、`material_id`、`unit_id`、`quality_status`。
- `quantity`、`released_quantity`、`consumed_quantity`。
- `source_type`、`source_id`、`source_line_id`、`source_document_no`。
- `business_date`、`reason`、`remark`。
- `created_by`、`created_at`、`updated_by`、`updated_at`、`released_by`、`released_at`、`version`。

活跃来源唯一性：同一 `reservation_type + source_type + source_line_id + status = ACTIVE` 只能有一条记录。

余额缓存：`inv_stock_balance.locked_quantity` 等于对应仓库、物料、质量状态下所有活跃占用和预留剩余数量之和。迁移时历史数据默认没有占用预留，`locked_quantity` 保持现有值；如存在非零历史锁定，应在迁移校验中记录并纳入台账修复。

## 业务状态流转

销售：

- 草稿销售订单不预留。
- 销售订单行必须填写预留仓库后才能确认。
- 确认销售订单为未出库数量生成 `SALES_ORDER + RESERVATION`，仓库取订单行预留仓库。
- 销售出库仓库必须与来源订单行预留仓库一致，不自动跨仓释放、改仓或分配。
- 创建销售出库草稿时可将本来源预留转为 `OCCUPATION`；如不持久化草稿占用，过账时必须消费来源预留并校验现货净可用。
- 销售出库过账后消费占用或预留，并扣减合格库存。
- 销售订单取消、关闭、数量减少释放剩余预留。

生产：

- 草稿生产工单不预留。
- 发布生产工单后按 BOM 快照和领料仓库为未领用料生成 `PRODUCTION_WORK_ORDER + RESERVATION`。
- 生产领料草稿可将本来源预留转为 `OCCUPATION`；领料过账后消费占用或预留，并扣减合格库存。
- 工单取消、完成、用料减少释放剩余预留。
- 不得把工单发布直接记为占用。

采购：

- 草稿采购订单不进入在途。
- 确认或部分入库采购订单的未入库数量形成物料级采购在途参考。
- 本阶段不在采购订单行增加仓库字段，也不按仓库计算采购在途。
- 采购入库过账选择具体入库仓库，减少物料级在途并形成该仓库待检库存。
- 采购关闭或取消后剩余未入库数量不再计入在途。

质量与库存：

- 待检、不合格、冻结库存不进入现货净可用和可承诺量。
- 冻结只能冻结未占用、未预留的合格现货净可用库存。
- 解冻后回到合格现存，不自动生成新预留。
- 库存调减、采购退货、生产补料必须检查对应质量状态和现货净可用，不得绕过台账。

## 接口设计

库存余额接口扩展字段：

- `bookQuantity`：账面库存。
- `qualifiedQuantity`：合格现存。
- `reservedQuantity`：预留库存。
- `occupiedQuantity`：占用库存。
- `frozenQuantity`：冻结库存。
- `availableQuantity`：现货净可用。
- `inTransitQuantity`：物料级采购在途参考。
- `availableToPromiseQuantity`：仓库级现货可承诺量，018 等于现货净可用，不叠加物料级采购在途。
- `netRequirementShortageQuantity`：计划净需求前置缺口，公式为 `max(需求数量 - 可覆盖供给数量, 0)`。

新增或扩展追溯接口：

- `/api/admin/inventory/reservations` 查询占用预留台账。
- `/api/admin/inventory/reservations/{id}` 查询来源上下文。
- 库存余额行可以跳转到对应物料仓库的占用、预留和在途抽屉。

销售、生产候选库存接口扩展字段：

- `reservedQuantity`、`occupiedQuantity`、`availableQuantity`、`availableToPromiseQuantity`、`maxSelectableQuantity`、`disabledReasonCode`、`disabledReason`。
- 销售订单和销售出库相关响应需返回订单行预留仓库，销售出库候选必须校验出库仓库与来源订单行预留仓库一致。

采购订单和入库接口扩展字段：

- `inTransitQuantity`、`expectedArrivalDate`、`inTransitStatus`。
- 在途字段为物料级参考，不返回或暗示仓库级在途可用量。

## 权限和审计

新增权限建议：

- `inventory:availability:view`：查看库存可用量口径。
- `inventory:reservation:view`：查看占用预留台账。

本阶段不提供手工预留和手工释放页面，预留、占用、释放和消费由销售、生产、采购业务动作触发。后端仍需记录审计动作：

- `INVENTORY_RESERVATION_CREATE`
- `INVENTORY_RESERVATION_RELEASE`
- `INVENTORY_RESERVATION_CONSUME`
- `INVENTORY_OCCUPATION_CREATE`
- `INVENTORY_OCCUPATION_RELEASE`
- `INVENTORY_OCCUPATION_CONSUME`

影响经营口径的写入必须调用 `BusinessPeriodGuard`。

## 前端设计

库存余额页维持统一筛选栏和宽表横向滚动，字段按口径分组展示：基础信息、质量分布、可用量口径、物料级在途参考和操作。列名必须使用明确中文：账面库存、合格现存、占用库存、预留库存、冻结库存、现货净可用、物料级采购在途参考、仓库级现货可承诺量。

销售出库候选和生产领料候选保留 `maxSelectableQuantity` 作为输入上限，但展示名称使用“本次最多出库”或“本次最多领料”，避免与可承诺量混淆。

销售订单行确认前必须选择预留仓库；销售出库页面默认显示来源订单行预留仓库，出库仓库不一致时显示受控错误，不提供自动改仓。

采购订单列表和详情展示“物料级采购在途参考”而不是泛称“未入库”，草稿、取消、关闭不计入在途。

占用、预留和在途追溯使用抽屉或弹层，不在列表下方展开长表。

## 测试和验收

后端重点覆盖迁移、台账唯一性、并发占用、释放消费、销售订单行预留仓库、销售预留、生产预留、物料级采购在途参考、冻结联动、权限和期间保护。

前端重点覆盖库存余额字段展示、候选库存禁用原因、在途展示、宽表滚动、权限态和 390px 窄屏。

浏览器验收必须覆盖物料级采购在途参考、质量确认、销售预留仓库、销售出库消费、生产工单预留、生产领料消费、冻结拒绝、期间锁定、只读和无权限。

## 交付边界

本阶段完成后，系统可以回答“当前有多少账面库存、多少现货净可用、多少已被销售和生产占用预留、多少采购在途参考，以及当前仓库现货能承诺多少”。系统仍不会自动生成采购建议、生产建议、排产计划或正式 MRP 结果，也不会预留在途或自动跨仓承诺。
