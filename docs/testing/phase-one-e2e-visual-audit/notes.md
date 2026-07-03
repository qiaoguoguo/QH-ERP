# 一期生产主链路端到端视觉分析

## 验收信息

- 验收日期：2026-07-03。
- 分支：`codex/phase-one-e2e-stabilization`。
- 提交号：待阶段提交。
- 后端地址：`http://127.0.0.1:18080`，`/api/health` 返回 `UP`。
- 前端地址：`http://127.0.0.1:5178/`，阶段分支 worktree 启动的 Vite 服务。
- 截图工具：Playwright Chromium。
- 视口策略：
  - 桌面主视口：`1366x768`。
  - 窄屏视口：`390x844` 或 `375x812`。
  - 密集表格、长详情和固定侧边栏页面使用可信视口截图，不使用失真全页截图作为主证据。

## 测试账号

| 角色 | 账号 | 用途 |
|---|---|---|
| 管理员 | `admin` | 完成主链路和截图主证据 |
| 基础资料维护 | `e2e20260703124523_master` | 检查基础资料权限 |
| 库存角色 | `e2e20260703124523_inventory` | 检查库存权限 |
| 生产角色 | `e2e20260703124523_production` | 检查生产权限 |
| 成本角色 | `e2e20260703124523_cost` | 检查成本权限 |
| 只读用户 | `e2e20260703124523_readonly` | 检查只读状态 |
| 无权限用户 | `e2e20260703124523_none` | 检查无权限状态 |

## 端到端数据集

| 类型 | 编号或名称 |
|---|---|
| 单位 | `E2E20260703124523U` |
| 原料仓 | `E2E20260703124523RAWWH` |
| 成品仓 | `E2E20260703124523FGWH` |
| 物料分类 | `E2E20260703124523CAT` |
| 原材料 | `E2E20260703124523RAW` |
| 辅料 | `E2E20260703124523AUX` |
| 成品 | `E2E20260703124523FG` |
| BOM | `E2E20260703124523BOM` |
| 期初库存单 | ID `12` |
| 生产工单 | `MFG-WO-20260703124525205-000` |
| 领料单 | `MFG-ISS-20260703124525429-000` |
| 报工单 | `MFG-RPT-20260703124525590-001` |
| 完工入库单 | `MFG-RCP-20260703124525686-002` |
| 成本记录 | 材料 `COST-20260703124525543-002`、`COST-20260703124525520-001`；人工 `COST-20260703124525635-003`；手工 `COST-20260703124525809-001` |
| 库存不足异常调整单 | `INV-ADJ-20260703130053057-002`，ID `14` |
| 成本不反写验证工单 | ID `19` |
| 浏览器补证工单 | `MFG-WO-20260703132127628-006`，ID `23` |
| 浏览器补证领料单 | `MFG-ISS-20260703132129071-001`，ID `14` |
| 浏览器补证报工单 | `MFG-RPT-20260703132130382-002`，ID `17` |
| 浏览器补证完工入库单 | `MFG-RCP-20260703132131536-003`，ID `9` |
| 浏览器补证手工成本 | `COST-20260703132133678-003`，ID `21` |
| 页面补拍附加前缀 | `E2EUI20260703132030`，仅作为基础资料、BOM、期初库存和工单发布页面可操作附加证据 |

## 截图清单

| 文件名 | 视口 | 账号角色 | 页面路径 | 场景 | 结果 |
|---|---:|---|---|---|---|
| `01-admin-menu-overview.png` | 1366x768 | 管理员 | `/inventory/balances` | 菜单入口总览，含库存子菜单 | 通过 |
| `02-material-target-items.png` | 1366x768 | 管理员 | `/materials/items?keyword=E2E20260703124523` | 目标物料档案 | 通过 |
| `03-bom-enabled-detail.png` | 1366x768 | 管理员 | `/materials/boms?keyword=E2E20260703124523` | 启用 BOM 明细 | 通过 |
| `04-inventory-opening-balance.png` | 1366x768 | 管理员 | `/inventory/balances?keyword=E2E20260703124523` | 期初后库存余额 | 通过 |
| `05-inventory-opening-movement.png` | 1366x768 | 管理员 | `/inventory/movements?keyword=E2E20260703124523` | 期初和生产来源库存流水 | 通过 |
| `06-work-order-list.png` | 1366x768 | 管理员 | `/production/work-orders?keyword=E2E20260703124523` | 工单列表 | 通过 |
| `07-work-order-form.png` | 1366x768 | 管理员 | `/production/work-orders/create` | 工单表单 | 通过 |
| `08-work-order-released-detail.png` | 1366x768 | 管理员 | `/production/work-orders/17` | 发布后工单详情 | 通过 |
| `09-material-issue-posted.png` | 1366x768 | 管理员 | `/production/work-orders/17/material-issues` | 领料过账后已领和未领口径 | 通过 |
| `10-work-report-posted.png` | 1366x768 | 管理员 | `/production/work-orders/17/reports` | 报工过账后剩余可报工口径 | 通过 |
| `11-completion-receipt-posted.png` | 1366x768 | 管理员 | `/production/work-orders/17/completion-receipts` | 完工入库过账后剩余可入库口径 | 通过 |
| `12-inventory-after-production.png` | 1366x768 | 管理员 | `/inventory/balances?keyword=E2E20260703124523` | 生产后库存余额 | 通过 |
| `13-cost-record-query.png` | 1366x768 | 管理员 | `/cost/records?keyword=E2E20260703124523` | 成本记录查询 | 通过 |
| `14-cost-record-detail.png` | 1366x768 | 管理员 | `/cost/records/16` | 成本记录详情 | 通过 |
| `15-work-order-cost-section.png` | 1366x768 | 管理员 | `/production/work-orders/17` | 工单成本分区 | 通过 |
| `16-exception-stock-not-enough.png` | 1366x768 | 管理员 | `/inventory/documents/14` | 库存调整调减超过可用库存异常 | 通过 |
| `17-role-cost-permission.png` | 1366x768 | 成本角色 | `/cost/records` | 成本角色权限状态 | 通过 |
| `18-role-readonly-permission.png` | 1366x768 | 只读用户 | `/cost/records` | 只读权限状态 | 通过 |
| `19-role-forbidden.png` | 1366x768 | 无权限用户 | `/forbidden?from=/cost/records` | 无权限状态 | 通过 |
| `20-mobile-work-order-detail.png` | 390x844 | 管理员 | `/production/work-orders/17` | 窄屏工单详情 | 通过 |
| `21-mobile-production-form.png` | 390x844 | 管理员 | `/production/work-orders/17/material-issues` | 窄屏生产执行表单 | 通过 |
| `22-mobile-cost-detail.png` | 390x844 | 管理员 | `/cost/records/16` | 窄屏成本详情 | 通过 |
| `23-role-master-permission.png` | 1366x768 | 基础资料维护 | `/materials/items?keyword=E2E20260703124523` | 基础资料维护权限状态 | 通过 |
| `24-role-inventory-permission.png` | 1366x768 | 库存角色 | `/inventory/documents` | 库存角色权限状态 | 通过 |
| `25-role-production-permission.png` | 1366x768 | 生产角色 | `/production/work-orders?keyword=E2E20260703124523` | 生产角色权限状态 | 通过 |
| `25-ui-unit-created.png` | 1366x768 | 管理员 | `/master/units` | 页面创建计量单位附加证据 | 通过 |
| `26-ui-category-created.png` | 1366x768 | 管理员 | `/materials/categories` | 页面创建物料分类附加证据 | 通过 |
| `27-ui-raw-warehouse-created.png` | 1366x768 | 管理员 | `/master/warehouses` | 页面创建原料仓附加证据 | 通过 |
| `28-ui-fg-warehouse-created.png` | 1366x768 | 管理员 | `/master/warehouses` | 页面创建成品仓附加证据 | 通过 |
| `27-ui-raw-material-created.png` | 1366x768 | 管理员 | `/materials/items` | 页面创建原材料附加证据 | 通过 |
| `28-ui-fg-material-created.png` | 1366x768 | 管理员 | `/materials/items` | 页面创建成品附加证据 | 通过 |
| `29-ui-bom-created-draft.png` | 1366x768 | 管理员 | `/materials/boms` | 页面创建 BOM 草稿附加证据 | 通过 |
| `26-ui-work-order-form-filled.png` | 1365x768 | 管理员 | `/production/work-orders/create` | 浏览器填写工单表单 | 通过 |
| `27-ui-work-order-created-detail.png` | 1365x768 | 管理员 | `/production/work-orders/23` | 浏览器保存工单草稿后详情 | 通过 |
| `28-ui-work-order-released-detail.png` | 1365x768 | 管理员 | `/production/work-orders/23` | 浏览器发布工单后详情 | 通过 |
| `29-ui-material-issue-form-filled.png` | 1365x768 | 管理员 | `/production/work-orders/23/material-issues` | 浏览器填写领料草稿 | 通过 |
| `30-ui-material-issue-posted-detail.png` | 1365x768 | 管理员 | `/production/work-orders/23` | 工单详情页浏览器过账领料 | 通过 |
| `31-ui-work-report-form-filled.png` | 1365x768 | 管理员 | `/production/work-orders/23/reports` | 浏览器填写报工草稿 | 通过 |
| `32-ui-work-report-posted-detail.png` | 1365x768 | 管理员 | `/production/work-orders/23` | 工单详情页浏览器过账报工 | 通过 |
| `33-ui-completion-receipt-form-filled.png` | 1365x768 | 管理员 | `/production/work-orders/23/completion-receipts` | 浏览器填写完工入库草稿 | 通过 |
| `34-ui-completion-receipt-posted-detail.png` | 1365x768 | 管理员 | `/production/work-orders/23` | 工单详情页浏览器过账完工入库 | 通过 |
| `35-ui-cost-record-query.png` | 1365x768 | 管理员 | `/cost/records` | 浏览器按工单查询成本记录 | 通过 |
| `36-ui-cost-form-filled.png` | 1365x768 | 管理员 | `/cost/records/create?workOrderId=23` | 浏览器填写手工成本表单 | 通过 |
| `37-ui-cost-detail-created.png` | 1365x768 | 管理员 | `/cost/records/21` | 浏览器保存手工成本后详情 | 通过 |
| `38-ui-final-work-order-cost-section.png` | 1365x768 | 管理员 | `/production/work-orders/23` | 浏览器补证工单成本分区最终追溯 | 通过 |
| `45-ui-e2eui-bom-enabled-check.png` | 1366x768 | 管理员 | `/materials/boms` | 页面补拍 BOM 启用状态确认 | 通过 |
| `46-ui-e2eui-opening-draft-detail.png` | 1366x768 | 管理员 | `/inventory/documents` | 页面补拍期初库存草稿详情 | 通过 |
| `47-ui-e2eui-opening-posted-detail.png` | 1366x768 | 管理员 | `/inventory/documents` | 页面补拍期初库存过账详情 | 通过 |
| `48-ui-e2eui-work-order-form-filled.png` | 1366x768 | 管理员 | `/production/work-orders/create` | 页面补拍工单表单填写 | 通过 |
| `49-ui-e2eui-work-order-created-detail.png` | 1366x768 | 管理员 | `/production/work-orders` | 页面补拍工单草稿详情 | 通过 |
| `50-ui-e2eui-work-order-released-detail.png` | 1366x768 | 管理员 | `/production/work-orders` | 页面补拍工单发布详情 | 通过 |

## 端到端路径摘要

浏览器证据以同一数据集 `E2E20260703124523` 贯穿。管理员截图覆盖菜单入口、物料档案、BOM、库存余额、库存流水、生产工单表单、生产工单详情、领料、报工、完工入库、成本记录、成本详情、工单成本分区、库存不足异常状态和窄屏页面。

本轮追加浏览器写操作补证：在复用已启用主数据和库存余额的基础上，通过页面创建工单 `MFG-WO-20260703132127628-006`，在工单详情页发布并过账领料、报工、完工入库，通过成本页面查询并创建手工成本记录，最终工单详情成本分区显示自动和手工成本追溯。截图 `26-ui-work-order-form-filled.png` 至 `38-ui-final-work-order-cost-section.png` 对应这一路径。

测试子代理独立补拍的 `E2EUI20260703132030` 前缀页面证据已达到 BOM 启用确认、期初库存草稿与过账、工单创建与发布，截图 `45-ui-e2eui-bom-enabled-check.png` 至 `50-ui-e2eui-work-order-released-detail.png` 已纳入清单。该子流程未继续到领料、报工、完工入库和成本归集，不作为最终闭环数据集；最终闭环仍以工单 ID `23` 的浏览器补证和接口断言为准。

API 级数据断言已证明期初、领料、报工、完工入库、自动成本和手工成本贯通；本轮截图用于证明这些数据在当前页面可见、可检索和可追溯。

权限截图覆盖基础资料维护、库存角色、生产角色、成本角色、只读用户和无权限用户。基础资料维护角色仅显示基础资料和物料管理入口，并可维护物料；库存角色仅显示库存管理入口，并可维护库存单据；生产角色仅显示生产管理入口，并可维护生产工单；成本角色显示成本记录、新增手工成本和手工记录编辑入口；只读用户页面仅显示查询和详情入口；无权限用户直接访问成本记录页面时进入无权限页。

## 视觉结论

当前截图结论：

- 页面布局：桌面视口下左侧菜单、筛选区、表格、详情分区和操作区稳定；未见空白页、错页或加载中截图。
- 信息密度：物料、BOM、库存、工单和成本页面保持后台 ERP 密集信息展示，字段较多但可扫描。
- 关键操作可见性：管理员页面的查询、详情、成本入口、工单执行入口可见；领料、报工、入库页面在已执行完毕后显示剩余口径为 0。
- 写操作可见性：工单创建、发布、领料草稿保存、报工草稿保存、完工入库草稿保存、三类执行单据过账、成本查询和手工成本创建均有浏览器截图证据。
- 文案溢出：长编码和单号未造成关键列遮挡。
- 控件重叠：桌面截图未见按钮、表格、告警或详情分区互相遮挡。
- 响应式适配：390x844 窄屏下工单详情、生产执行表单和成本详情可访问；密集表格仍需横向滚动。
- 权限状态识别：基础资料维护、库存角色、生产角色、成本角色、只读用户和无权限用户截图能清晰区分各自模块权限、可写、只读和禁止访问状态。
- 业务状态识别：库存、工单、生产执行和成本页面均能通过文字识别业务状态与来源。
- 异常状态识别：库存调整过账库存不足时，页面顶部红色提示“库存不足，调减后库存不能小于 0”明确；单据仍显示草稿状态，过账失败未造成状态误判。
- 成本业务记录边界：成本详情和工单成本分区未出现正式财务核算、凭证或利润结论文案。

## 问题与处理

- 当前 51 张截图未发现阻断视觉问题。
- 权限角色截图已补齐基础资料维护、库存角色、生产角色、成本角色、只读用户和无权限用户。
- 脚本调整记录：领料、报工、完工入库页面是工单上下文表单页，不是历史单据列表；工单表单截图等待条件按实际页面保存按钮调整，库存不足截图等待 Element Plus 消息容器后采集。
- 附加截图说明：`25-ui-unit-created.png` 至 `29-ui-bom-created-draft.png`、`45-ui-e2eui-bom-enabled-check.png` 至 `50-ui-e2eui-work-order-released-detail.png` 是测试子代理页面创建基础资料、BOM、期初库存和工单发布时留下的补证截图；该子流程未作为最终闭环数据集，最终闭环以 `E2E20260703124523` 和工单 ID `23` 为准。失败过程截图已移出证据目录，不纳入视觉结论。

## 证据限制

- 截图不替代自动化测试、接口校验、数据一致性核查或完整无障碍合规测试。
- 截图只证明当前环境、当前数据集和当前视口下的可见页面状态。

## 最终结论

阶段视觉分析已覆盖管理员主路径、浏览器写操作补证、基础资料维护、库存角色、生产角色、成本权限差异、无权限状态、库存不足异常和窄屏页面。当前截图未发现阻断视觉问题。
