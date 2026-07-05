# 退货退款与业务反冲测试计划

## 目标

验证退货退款与业务反冲基础模块在功能、数据、权限、浏览器操作和视觉呈现上满足阶段任务要求。测试重点是已过账原单不可改写、反向业务可追溯、库存/往来/成本/报表净额一致、异常受控和权限不泄露。

## 范围

### 本次测试

- 销售退货主路径、部分退货、全量退货、超退、重复过账。
- 采购退货主路径、库存不足、超退、应付冲减。
- 生产退料、生产补料、工单来源和成本业务影响。
- 往来冲减、退款/付款冲减、超额冲减。
- 业务反冲追溯链。
- 014 经营报表净额口径和反向来源追溯。
- 权限、来源脱敏、空态、错误态、视觉分析。

### 本次不测试

- 总账、凭证、税务、红字发票、银行退款支付。
- 审批流、复杂售后维修、换货、质量检验体系。
- 多组织、多币种、多账套。
- 性能压测和安全专项。

## 自动化测试计划

### 后端测试

新增或扩展：

- `ReturnRefundReversalAdminControllerTests`
- `ReportingAdminControllerTests`
- `AccountPermissionInitializerTests`
- `PermissionAuthorizationTests`

覆盖：

- 权限初始化包含 `sales:return:*`、`procurement:return:*`、`production:material-return:*`、`production:material-supplement:*`、`finance:settlement-adjustment:*`。
- 销售退货：
  - 已过账销售出库可创建退货草稿。
  - 退货过账生成 `SALES_RETURN_IN` 库存流水。
  - 应收余额被冲减。
  - 部分退货后可退数量减少。
  - 超退、重复过账、来源未过账被拒绝。
- 采购退货：
  - 已过账采购入库可创建退货草稿。
  - 退货过账生成 `PURCHASE_RETURN_OUT` 库存流水。
  - 应付余额被冲减。
  - 库存不足时整体回滚。
- 生产退料/补料：
  - 退料入库生成生产退料流水。
  - 补料出库生成生产补料流水。
  - 工单材料消耗和成本业务净额正确。
- 往来冲减：
  - 冲减金额不能超过可冲金额。
  - 已过账收付款不被删除或改写。
  - 调整记录可追溯来源。
- 报表净额：
  - 销售、采购、库存、生产、成本和往来报表展示原发生、反向发生、净额。
  - 销售报表校验 `salesOriginalAmount`、`salesReturnAmount`、`salesNetAmount`、`salesOriginalQuantity`、`salesReturnQuantity`、`salesNetQuantity`。
  - 采购报表校验 `purchaseOriginalAmount`、`purchaseReturnAmount`、`purchaseNetAmount`、`purchaseOriginalQuantity`、`purchaseReturnQuantity`、`purchaseNetQuantity`。
  - 库存、生产、成本和往来报表分别校验接口契约定义的净额字段。
  - 追溯接口包含反向来源。
- 并发和幂等：
  - 同一来源行并发退货不能超退。
  - 同一草稿重复过账不能重复影响库存或往来。
  - 同一来源和同一 `clientRequestId` 重复创建时返回已有详情；请求体核心字段不一致时返回 `REVERSAL_DUPLICATED`。

命令：

```powershell
cd apps/api
$env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd test
```

### 前端测试

新增或扩展：

- `apps/web/src/shared/api/returnRefundReversalApi.spec.ts`
- `apps/web/src/modules/reversal/*.spec.ts`
- `apps/web/src/router/permissionGuard.spec.ts`
- `apps/web/src/App.spec.ts`
- `apps/web/src/modules/reports/ReportViews.spec.ts`

覆盖：

- API 路径、请求参数、响应类型和错误信封。
- `SalesReturnDetail`、`PurchaseReturnDetail`、`ProductionMaterialReturnDetail`、`ProductionMaterialSupplementDetail`、`SettlementAdjustmentDetail` 和 `ReversalTraceRecord` 字段与接口契约一致。
- 菜单和路由按权限显示。
- 销售退货列表、表单、详情和追溯。
- 采购退货列表、表单、详情和追溯。
- 往来冲减列表、表单、详情。
- 生产退料/补料列表、表单、详情。
- 来源受限时不展示单号、主键、数量、金额、业务日期、状态或详情链接。
- 已过账状态隐藏编辑和过账按钮。
- 报表净额字段和反向来源追溯入口。

命令：

```powershell
cd apps/web
npm test
npm run typecheck
npm run build
```

## 浏览器验收计划

### 最小验收数据集

| 数据 | 初始值 | 反向操作 | 预期结果 |
|---|---:|---|---|
| 销售出库 `SO-RET-001` 行 A | 出库 `10.000000`，单价 `50.00`，销售金额 `500.00`，应收 `500.00` | 销售退货 `2.000000` | 库存增加 `2.000000`，应收冲减 `100.00`，销售净数量 `8.000000`，销售净额 `400.00` |
| 采购入库 `PO-RET-001` 行 A | 入库 `20.000000`，单价 `30.00`，采购金额 `600.00`，应付 `600.00`，当前库存不少于 `20.000000` | 采购退货 `5.000000` | 库存减少 `5.000000`，应付冲减 `150.00`，采购净数量 `15.000000`，采购净额 `450.00` |
| 生产领料 `MI-RET-001` 行 A | 已领 `12.000000`，单位成本 `10.00`，材料原成本 `120.00` | 生产退料 `3.000000` | 库存增加 `3.000000`，工单材料净消耗 `9.000000`，`materialReturnCost=30.00`，退料后 `materialNetCost=90.00` |
| 生产工单 `WO-SUP-001` 物料 A | 计划 `15.000000`，已领 `12.000000`，单位成本 `10.00`，库存不少于 `2.000000` | 生产补料 `2.000000` | 库存减少 `2.000000`，工单材料净消耗 `14.000000`，`materialSupplementCost=20.00`，退料和补料后 `materialNetCost=110.00`，`totalNetCost` 包含该材料净成本 |
| 应收台账 `AR-RET-001` | 原金额 `500.00`，已冲 `100.00`，可冲 `400.00` | 往来冲减 `50.00` | 已冲 `150.00`，剩余 `350.00`，应收状态为 `PARTIALLY_RECEIVED` |

浏览器验收必须使用上述数据或等价数据，并在验收记录中写明实际单号、数量、金额和报表核对值。

### 主路径

1. 管理员登录。
2. 创建或选择已过账销售出库。
3. 发起销售退货并过账。
4. 查看销售退货详情，确认原销售出库、库存流水、应收冲减和报表追溯可见。
5. 创建或选择已过账采购入库。
6. 发起采购退货并过账。
7. 查看采购退货详情，确认原采购入库、库存流水、应付冲减和报表追溯可见。
8. 从生产领料创建退料并过账。
9. 从生产工单创建补料并过账。
10. 打开经营报表，确认销售净额、采购净额、库存净变动、成本净额和往来余额变化。

### 权限路径

- 管理员：全部可见可操作。
- 销售角色：可处理销售退货，不可处理采购退货和生产退料。
- 采购角色：可处理采购退货，不可处理销售退货。
- 生产角色：可处理生产退料和补料，不可处理销售/采购退货。
- 财务角色：可查看和处理往来冲减，不可绕过业务来源权限。
- 只读角色：可查看授权页面，不可创建、编辑、过账或取消草稿。
- 无权限用户：菜单、路由和接口均拒绝。
- 来源受限用户：能看到受限提示，不能看到敏感来源字段。

### 异常路径

- 来源未过账。
- 来源不存在。
- 退货数量为 0、负数、超精度或超过可退数量。
- 采购退货库存不足。
- 退款或冲减金额超过可冲金额。
- 同一来源重复反冲。
- 已过账反向单编辑、取消或再次过账。
- 报表追溯来源权限不足。
- 后端返回错误时页面不保留脏状态。

### 来源受限路径

- 用户具备 `business:reversal:view` 和当前反向资源查看权限，但不具备来源模块查看权限时，列表、详情和追溯面板必须返回并展示受限行。
- 受限行必须显示 `canViewSource=false`、`restricted=true` 和“来源无查看权限”提示。
- 受限行不得展示来源单号、来源主键、来源行号、数量、金额、业务日期、状态、路由名、路由参数或跳转链接。
- 用户不具备当前反向资源查看权限时，列表、详情和接口均返回无权限，不返回脱敏行。

## 视觉分析计划

目录：`docs/testing/return-refund-reversal-visual-audit/`。

必须保存截图并记录视口尺寸、页面、角色、发现问题和处理结果。

截图覆盖：

- 销售退货列表、表单、详情。
- 采购退货列表、表单、详情。
- 往来冲减表单和详情。
- 生产退料/补料表单和详情。
- 原单详情的反向影响区域。
- 反向来源追溯面板。
- 报表净额和反向来源追溯。
- 权限受限、无权限、空态、错误态。
- 桌面视口和窄屏视口。

视觉结论必须检查：

- 页面布局。
- 信息密度。
- 关键操作可见性。
- 文案溢出。
- 控件重叠。
- 响应式适配。
- 业务状态识别。
- 原发生、反向发生和净额是否易于区分。

## 阻断缺陷

- 系统无法启动。
- 核心页面无法浏览器访问。
- 数据库迁移失败。
- 已过账原单被直接改写或删除。
- 允许超退、超冲、重复反冲。
- 库存余额、往来余额、成本或报表净额错误。
- 反向来源不可追溯。
- 无权限可访问反向业务接口或来源敏感字段。
- 浏览器主路径不可执行。
- 视觉分析缺失或截图证据不可用。

## 执行记录

- 计划创建：2026-07-05。
- 自动化测试：Task 2 到 Task 7 已完成定向自动化验证。Task6 后端 `ReversalAdminControllerTests` 22 条通过，Task6 相关后端回归 `ReversalAdminControllerTests,FinanceAdminControllerTests,SalesAdminControllerTests,ProcurementAdminControllerTests,ProductionAdminControllerTests,ReportingAdminControllerTests` 共 65 条通过；Task6 前端定向测试 `returnRefundReversalApi.spec.ts`、`SettlementAdjustmentViews.spec.ts`、`permissionGuard.spec.ts`、`App.spec.ts` 共 4 个文件 99 条通过；Task7 后端定向测试 `ReportingAdminControllerTests` 21 条、`ReversalAdminControllerTests` 22 条通过；Task7 前端定向测试 `businessReportingApi.spec.ts`、`ReportViews.spec.ts` 共 2 个文件 14 条通过；相关 `npm run typecheck` 与 `git diff --check` 均通过。
- 缺陷复验：Task6 后端阻断项“生产退料/补料误作为往来冲减来源”已由 `5252c9f` 修复并通过产品经理、测试复审；Task6 前端 P1 “候选来源刷新后隐藏提交旧来源”已由 `7d5b0b0` 修复并通过测试复审；Task7 后端跨期反向发生口径和往来 item 本期冲减口径阻断分别由 `c7c5117`、`9346d24` 修复并通过产品经理、测试复审。
- 浏览器验收：已完成。前端 `http://127.0.0.1:5174`、后端 `http://127.0.0.1:18082` 在 Task8 验收期间可访问，健康检查返回 `status=UP`、`service=qherp-api`；主路径、权限路径、异常路径均通过，详细记录见 `docs/testing/return-refund-reversal-visual-audit/notes.md`。
- 视觉分析：已完成。`docs/testing/return-refund-reversal-visual-audit/` 保存 22 张 PNG 截图和 `notes.md`，覆盖销售退货、采购退货、生产退料/补料、往来冲减、报表净额、追溯、受限、无权限、未登录、只读、空态和窄屏。
- 缺陷状态：截至 Task8 完成，未登记未解决阻断缺陷；全阶段最终复审和交付判断待 Task9 执行。
