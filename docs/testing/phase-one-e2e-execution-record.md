# 一期生产主链路端到端执行记录

## 验收信息

- 验收日期：2026-07-03。
- 分支：`codex/phase-one-e2e-stabilization`。
- 提交号：待阶段提交。
- 后端地址：`http://127.0.0.1:18080`，`/api/health` 返回 `UP`。
- 前端地址：`http://127.0.0.1:5178/`，阶段分支 worktree 启动的 Vite 服务。
- 数据库：后端测试使用 Testcontainers PostgreSQL；浏览器主路径使用本地 PostgreSQL 服务数据。
- 浏览器工具：Playwright Chromium。

## 验收账号

| 角色 | 账号 | 结果 |
|---|---|---|
| 管理员 | `admin` | 通过，完成主链路、截图和接口核查 |
| 基础资料维护 | `e2e20260703124523_master` | 通过，基础资料与物料页面、接口拒绝核查完成 |
| 库存角色 | `e2e20260703124523_inventory` | 通过，库存页面、接口拒绝核查完成 |
| 生产角色 | `e2e20260703124523_production` | 通过，生产页面、接口拒绝核查完成 |
| 成本角色 | `e2e20260703124523_cost` | 通过，成本页面和接口拒绝核查完成 |
| 只读用户 | `e2e20260703124523_readonly` | 通过，只读页面和写接口拒绝核查完成 |
| 无权限用户 | `e2e20260703124523_none` | 通过，路由无权限页和接口拒绝核查完成 |

## 端到端数据集

| 数据 | 编号或名称 | 结果 |
|---|---|---|
| 单位 | `E2E20260703124523U`，ID `27` | 通过 |
| 原料仓 | `E2E20260703124523RAWWH`，ID `18` | 通过 |
| 成品仓 | `E2E20260703124523FGWH`，ID `19` | 通过 |
| 物料分类 | `E2E20260703124523CAT`，ID `21` | 通过 |
| 原材料 | `E2E20260703124523RAW`，ID `70` | 通过 |
| 辅料 | `E2E20260703124523AUX`，ID `71` | 通过 |
| 成品 | `E2E20260703124523FG`，ID `72` | 通过 |
| BOM | `E2E20260703124523BOM`，ID `27` | 通过 |
| 期初库存单 | ID `12` | 通过 |
| 生产工单 | `MFG-WO-20260703124525205-000`，ID `17` | 通过 |
| 领料单 | `MFG-ISS-20260703124525429-000`，ID `13` | 通过 |
| 报工单 | `MFG-RPT-20260703124525590-001`，ID `16` | 通过 |
| 完工入库单 | `MFG-RCP-20260703124525686-002`，ID `8` | 通过 |
| 材料成本记录 | `COST-20260703124525543-002`、`COST-20260703124525520-001` | 通过 |
| 人工成本记录 | `COST-20260703124525635-003` | 通过 |
| 手工成本记录 | `COST-20260703124525809-001`，ID `16` | 通过 |
| 库存不足异常调整单 | `INV-ADJ-20260703130053057-002`，ID `14` | 通过，用于库存不足异常截图和无部分写入核查 |
| 成本不反写验证工单 | ID `19` | 通过，用于手工成本新增/更新不反写库存、工单和生产单据状态核查 |
| 未启用 BOM | `E2E20260703124523DRAFT1783083974188`，ID `28` | 通过，用于 BOM 不可用异常核查 |
| 超领异常工单 | ID `20` | 通过，用于累计领料超过应领数量异常核查 |
| 超报异常工单 | ID `21` | 通过，用于累计报工超过计划数量异常核查 |
| 超完工入库异常工单 | ID `22` | 通过，用于累计入库超过累计合格报工数量异常核查 |

## 自动化验证

| 命令 | 结果 | 备注 |
|---|---|---|
| `git status --short --branch` | 通过，退出码 `0` | 当前分支为 `codex/phase-one-e2e-stabilization...origin/main`，存在本阶段文档变更和新增文档目录 |
| `git diff --check` | 通过，退出码 `0` | 仅 `docs/README.md`、`docs/product/phase-one-scope.md`、`docs/product/product-decisions.md` 的 LF/CRLF 提示；无空白错误 |
| `cd apps/web; npm test` | 通过，退出码 `0` | 31 个测试文件、208 条测试通过 |
| `cd apps/web; npm run typecheck` | 通过，退出码 `0` | `vue-tsc --noEmit` 通过 |
| `cd apps/web; npm run build` | 通过，退出码 `0` | `vue-tsc --noEmit && vite build` 通过，Vite 构建 1734 个模块 |
| Docker JDK 21 后端 `mvn -q test` | 通过，退出码 `0` | Testcontainers PostgreSQL 可启动；Flyway 7 个迁移多轮校验并迁移到 v7；Maven 退出码为 `0` |

已知非阻断提示：后端测试输出包含 Ryuk 禁用提示、SpringDoc 默认端点提示、Mockito 动态 agent 提示、已关闭测试库连接池的 Hikari 警告；最终 Maven 退出码为 `0`，未形成失败摘要。

自动化基线结论：当前自动化基线未发现阻断缺陷，可进入后续浏览器主路径验收。

## 本地部署验证

| 项目 | 结果 | 证据 |
|---|---|---|
| PostgreSQL | 通过 | `qherp-postgres` 容器 `Up 7 hours (healthy)`，端口 `15432->5432` |
| 后端健康检查 | 通过 | `http://127.0.0.1:18080/api/health` 返回 `{"status":"UP","service":"qherp-api"}` |
| 前端核心路由 | 通过 | `http://127.0.0.1:5178/production/work-orders` 返回 HTTP `200` |

## 管理员主路径

| 步骤 | 结果 | 证据 |
|---|---|---|
| 登录与菜单入口 | 通过 | 浏览器截图 `01-admin-menu-overview.png`，并断言库存子菜单显示 `库存余额 / 库存变动 / 库存单据` |
| 基础资料准备 | 通过 | 基线数据由接口预置并启用，截图 `02-material-target-items.png`；页面创建附加证据见 `25-ui-unit-created.png` 至 `28-ui-fg-material-created.png` |
| BOM 创建或启用 | 通过 | 基线 BOM 由接口预置并启用，截图 `03-bom-enabled-detail.png`；页面创建 BOM 草稿附加证据见 `29-ui-bom-created-draft.png` |
| 期初库存过账 | 通过 | 基线期初库存由接口预置并过账，截图 `04-inventory-opening-balance.png`、`05-inventory-opening-movement.png`；重复期初规则由异常矩阵逐项闭环记录 |
| 工单创建和发布 | 通过 | 基线工单截图 `06-work-order-list.png`、`08-work-order-released-detail.png`；浏览器创建和发布工单补证见下节工单 ID `23` |
| 工单表单可操作 | 通过 | 截图 `07-work-order-form.png` 显示新建生产工单表单、产品物料、BOM、计划数量、仓库和保存入口 |
| 领料创建和过账 | 通过 | 基线领料截图 `09-material-issue-posted.png` 显示已领和未领口径；浏览器保存草稿并在工单详情页过账补证见 `29-ui-material-issue-form-filled.png`、`30-ui-material-issue-posted-detail.png` |
| 报工创建和过账 | 通过 | 基线报工截图 `10-work-report-posted.png` 显示剩余可报工为 0；浏览器保存草稿并在工单详情页过账补证见 `31-ui-work-report-form-filled.png`、`32-ui-work-report-posted-detail.png` |
| 完工入库创建和过账 | 通过 | 基线完工入库截图 `11-completion-receipt-posted.png` 显示剩余可入库为 0；浏览器保存草稿并在工单详情页过账补证见 `33-ui-completion-receipt-form-filled.png`、`34-ui-completion-receipt-posted-detail.png` |
| 成本记录查询 | 通过 | API 断言材料成本 2 条、人工成本 1 条；截图 `13-cost-record-query.png` |
| 成本详情追溯 | 通过 | API 断言成本汇总含 4 条记录和 1 条产出追溯；截图 `14-cost-record-detail.png` |
| 工单成本分区 | 通过 | 截图 `15-work-order-cost-section.png` |
| 手工成本创建和编辑 | 通过 | 基线手工成本由接口创建并编辑金额为 `150.250000`；浏览器创建手工成本补证见 `36-ui-cost-form-filled.png`、`37-ui-cost-detail-created.png` |

## 管理员浏览器写操作补证

产品规格复审指出，上一轮管理员主路径中多项关键写操作以 API 创建或过账为主要证据，不能充分证明页面可完成写操作。本轮补充使用真实浏览器、当前前端 `http://127.0.0.1:5178/` 和当前后端服务执行，不修改业务代码。

### 生产主链路浏览器闭环

本轮复用已启用的 `E2E20260703124523` 主数据、BOM 和库存余额，避免因重新创建期初库存触发同仓库同物料期初唯一约束。浏览器实际新建生产工单 `MFG-WO-20260703132127628-006`，ID `23`，并在页面完成保存、发布、生产执行草稿保存、工单详情过账、成本查询和手工成本创建。

| 步骤 | 结果 | 浏览器证据 | 接口断言 |
|---|---|---|---|
| 工单表单填写 | 通过 | `26-ui-work-order-form-filled.png` 显示产品物料、BOM、计划数量、领料仓库、入库仓库和备注均通过页面填写 | - |
| 保存工单草稿 | 通过 | `27-ui-work-order-created-detail.png` 显示保存后进入工单详情 | POST `/api/admin/production/work-orders` 返回 `200`，工单 ID `23`，状态 `DRAFT` |
| 发布工单 | 通过 | `28-ui-work-order-released-detail.png` 显示详情页点击发布后状态更新 | PUT `/api/admin/production/work-orders/23/release` 返回 `200` |
| 保存领料草稿 | 通过 | `29-ui-material-issue-form-filled.png` 显示页面填写本次领料数量；保存后回到工单详情 | POST `/api/admin/production/work-orders/23/material-issues` 返回 `200`，领料单 ID `14`，状态 `DRAFT` |
| 工单详情页过账领料 | 通过 | `30-ui-material-issue-posted-detail.png` 显示工单详情页领料记录过账后状态 | PUT `/api/admin/production/work-orders/23/material-issues/14/post` 返回 `200` |
| 保存报工草稿 | 通过 | `31-ui-work-report-form-filled.png` 显示页面填写合格数量和不良数量；保存后回到工单详情 | POST `/api/admin/production/work-orders/23/reports` 返回 `200`，报工单 ID `17`，状态 `DRAFT` |
| 工单详情页过账报工 | 通过 | `32-ui-work-report-posted-detail.png` 显示工单详情页报工记录过账后状态 | PUT `/api/admin/production/work-orders/23/reports/17/post` 返回 `200` |
| 保存完工入库草稿 | 通过 | `33-ui-completion-receipt-form-filled.png` 显示页面填写入库数量；保存后回到工单详情 | POST `/api/admin/production/work-orders/23/completion-receipts` 返回 `200`，入库单 ID `9`，状态 `DRAFT` |
| 工单详情页过账完工入库 | 通过 | `34-ui-completion-receipt-posted-detail.png` 显示工单详情页完工入库记录过账后状态 | PUT `/api/admin/production/work-orders/23/completion-receipts/9/post` 返回 `200` |
| 成本记录查询 | 通过 | `35-ui-cost-record-query.png` 显示成本记录页面按工单查询 | 工单成本汇总接口后续断言记录数为 `4` |
| 手工成本表单填写 | 通过 | `36-ui-cost-form-filled.png` 显示生产工单带入、业务日期、金额、来源说明和备注通过页面填写 | - |
| 保存手工成本 | 通过 | `37-ui-cost-detail-created.png` 显示保存后进入成本详情 | POST `/api/admin/cost/records` 返回 `200`，成本记录 ID `21`，金额 `12.34` |
| 工单成本分区追溯 | 通过 | `38-ui-final-work-order-cost-section.png` 显示工单详情成本归集分区 | GET `/api/admin/cost/work-orders/23/summary` 返回记录数 `4`、产出追溯 `1` |

最终数据断言：工单 ID `23` 状态为 `IN_PROGRESS`，两行用料已领数量分别为 `2` 和 `1`、未领为 `0`；累计报工 `1`、合格 `1`、累计入库 `1`；领料、报工、完工入库单据状态均为 `POSTED`；成本记录来源类型包含 1 条 `MANUAL_ENTRY` 和 3 条 `AUTO_PRODUCTION`，产出追溯数量为 `1`。

### 基础资料页面写操作附加证据

测试子代理曾以独立 `E2EUI20260703132030` 前缀通过页面补拍基础资料、BOM、期初库存和工单发布过程，截图已落盘但该子流程未继续到领料、报工、完工入库和成本归集，未作为本轮最终闭环数据集。该组截图仅作为“基础资料、BOM、库存期初和生产工单页面存在可操作创建与发布路径”的附加视觉证据，最终闭环仍以 `E2E20260703124523` 数据集和工单 ID `23` 为准。

| 文件 | 场景 | 结论 |
|---|---|---|
| `25-ui-unit-created.png` | 页面创建计量单位并返回列表 | 附加证据 |
| `26-ui-category-created.png` | 页面创建物料分类并返回列表 | 附加证据 |
| `27-ui-raw-warehouse-created.png` | 页面创建原料仓并返回列表 | 附加证据 |
| `28-ui-fg-warehouse-created.png` | 页面创建成品仓并返回列表 | 附加证据 |
| `27-ui-raw-material-created.png` | 页面创建原材料并返回列表 | 附加证据 |
| `28-ui-fg-material-created.png` | 页面创建成品并返回列表 | 附加证据 |
| `29-ui-bom-created-draft.png` | 页面创建 BOM 草稿并返回列表 | 附加证据 |
| `45-ui-e2eui-bom-enabled-check.png` | 页面确认 BOM 已启用 | 附加证据 |
| `46-ui-e2eui-opening-draft-detail.png` | 页面创建期初库存草稿并进入详情 | 附加证据 |
| `47-ui-e2eui-opening-posted-detail.png` | 页面过账期初库存并进入详情 | 附加证据 |
| `48-ui-e2eui-work-order-form-filled.png` | 页面填写生产工单表单 | 附加证据 |
| `49-ui-e2eui-work-order-created-detail.png` | 页面保存生产工单草稿并进入详情 | 附加证据 |
| `50-ui-e2eui-work-order-released-detail.png` | 页面发布生产工单并进入详情 | 附加证据 |

## 数据一致性核查

| 断言 | 结果 | 证据 |
|---|---|---|
| 期初过账后原料库存增加 | 通过 | 原料库存从无记录增至 `40` |
| 领料过账后原料库存减少 | 通过 | 原料库存从 `40` 减至 `20` |
| 完工入库后成品库存增加 | 通过 | 成品库存增至 `10` |
| 库存流水可追溯来源 | 通过 | 同一数据集查询到 `OPENING`、`PRODUCTION_ISSUE`、`PRODUCTION_RECEIPT` 流水 |
| 工单 BOM 快照与 BOM 明细一致 | 通过 | 工单发布后包含 2 条用料快照 |
| 报工和入库数量符合规则 | 通过 | 工单合格数量 `10`，入库数量 `10` |
| 领料生成材料成本记录 | 通过 | 生成 2 条材料成本记录 |
| 报工生成人工成本记录 | 通过 | 生成 1 条人工成本记录 |
| 完工入库进入产出追溯 | 通过 | 工单成本汇总含 1 条产出追溯 |
| 成本记录不反写库存或生产状态 | 通过 | 对工单 ID `19` 新增并更新手工成本记录 ID `17`，成本记录数从 `0` 增至 `1`；工单状态保持 `RELEASED`，报工/合格/不合格/入库数量均保持 `0`，领料/报工/完工入库单据列表仍为空；库存余额保持 `FG=10`、`AUX=10`、`RAW=20` |
| 写操作审计记录存在 | 通过 | 成本记录 ID `17` 详情审计摘要包含 `MFG_COST_RECORD_CREATE` 和 `MFG_COST_RECORD_UPDATE`；审计日志接口 `/api/admin/audit-logs?targetType=MFG_COST_RECORD&page=1&pageSize=20` 返回总数 `23`，样例含审计 ID `445`、`446` |

## 权限验证

| 角色 | 菜单 | 路由 | 按钮 | 接口 | 结论 |
|---|---|---|---|---|---|
| 管理员 | 通过 | 通过 | 通过 | 通过 | 可完成主链路 |
| 基础资料维护 | 通过 | 通过 | 通过 | 通过 | 截图 `23-role-master-permission.png` 显示基础资料和物料管理入口、物料新增/编辑/停用入口；物料列表接口返回 `200`，库存/生产/成本写接口返回 `403 AUTH_FORBIDDEN` |
| 库存角色 | 通过 | 通过 | 通过 | 通过 | 截图 `24-role-inventory-permission.png` 显示库存余额、库存变动、库存单据和库存单据写入口；库存单据列表接口返回 `200`，生产/成本写接口返回 `403 AUTH_FORBIDDEN` |
| 生产角色 | 通过 | 通过 | 通过 | 通过 | 截图 `25-role-production-permission.png` 显示生产工单、新建工单和生产执行入口；生产工单列表接口返回 `200`，库存/成本写接口返回 `403 AUTH_FORBIDDEN` |
| 成本角色 | 通过 | 通过 | 通过 | 通过 | 截图 `17-role-cost-permission.png` 显示成本记录页、新增和手工记录编辑入口；GET `/api/admin/cost/records` 返回 `200 OK`，POST `/api/admin/inventory/documents` 和 POST `/api/admin/production/work-orders` 均返回 `403 AUTH_FORBIDDEN` |
| 只读用户 | 通过 | 通过 | 通过 | 通过 | 截图 `18-role-readonly-permission.png` 显示成本记录只读查询和详情入口，无新增/编辑；GET `/api/admin/cost/records` 返回 `200 OK`，POST `/api/admin/cost/records` 和 PUT `/api/admin/cost/records/17` 均返回 `403 AUTH_FORBIDDEN` |
| 无权限用户 | 通过 | 通过 | 通过 | 通过 | 截图 `19-role-forbidden.png` 显示 `/forbidden?from=/cost/records`；GET `/api/admin/cost/records`、GET `/api/admin/inventory/balances`、GET `/api/admin/production/work-orders` 均返回 `403 AUTH_FORBIDDEN` |

## 异常验证

| 异常 | 结果 | 证据 |
|---|---|---|
| 未登录访问业务页 | 通过 | 未登录 GET `/api/admin/production/work-orders?page=1&pageSize=5` 返回 `401 AUTH_UNAUTHORIZED`，提示“未认证或登录已过期” |
| 无权限访问 URL 或 API | 通过 | 无权限用户访问 `/cost/records` 跳转无权限页；GET `/api/admin/cost/records` 返回 `403 AUTH_FORBIDDEN` |
| BOM 不可用 | 通过 | 使用未启用 BOM ID `28` 创建生产工单返回 `400 PRODUCTION_BOM_INVALID`，提示“生产 BOM 不存在、未启用或父项物料不匹配” |
| 库存不足 | 通过 | 库存调整单 ID `14` 调减成品仓成品 `999999.000000`，PUT `/api/admin/inventory/documents/14/post` 返回 `409 INVENTORY_STOCK_NOT_ENOUGH`，提示“库存不足，调减后库存不能小于 0”；截图 `16-exception-stock-not-enough.png`；单据保持 `DRAFT`，目标库存 `quantityOnHand=10`、`lockedQuantity=0`、`availableQuantity=10` 前后一致 |
| 超领 | 通过 | 工单 ID `20` 领料数量大于剩余应领，POST `/api/admin/production/work-orders/20/material-issues` 返回 `409 PRODUCTION_ISSUE_EXCEEDS_REQUIRED`；领料单数量前后均为 `0` |
| 超报 | 通过 | 工单 ID `21` 报工合格数量 `2` 超过计划数量 `1`，POST `/api/admin/production/work-orders/21/reports` 返回 `409 PRODUCTION_REPORT_EXCEEDS_PLAN`；报工单数量前后均为 `0`，已报数量保持 `0` |
| 超完工入库 | 通过 | 工单 ID `22` 未有合格报工时创建完工入库数量 `1`，POST `/api/admin/production/work-orders/22/completion-receipts` 返回 `409 PRODUCTION_RECEIPT_EXCEEDS_REPORTED`；完工入库单数量前后均为 `0`，已入库数量保持 `0` |
| 重复过账 | 通过 | 对已过账领料单 ID `13` 再次 PUT `/api/admin/production/work-orders/17/material-issues/13/post` 返回 `409 PRODUCTION_DUPLICATE_POST`；库存余额前后保持 `FG=10`、`AUX=10`、`RAW=20` |
| 非法成本金额或数量 | 通过 | POST `/api/admin/cost/records` 使用金额 `-1` 返回 `400 COST_AMOUNT_INVALID`，提示“成本金额不合法” |
| 无效工单手工成本 | 通过 | POST `/api/admin/cost/records` 使用工单 ID `99999999` 返回 `404 COST_WORK_ORDER_NOT_FOUND`，提示“成本关联工单不存在” |

## 异常矩阵逐项闭环

本节逐项对齐 `docs/testing/phase-one-e2e-test-plan.md` 的异常路径矩阵。结论分为三类：本阶段实测、浏览器证据、既有自动化覆盖。自动化覆盖均来自本阶段已执行并通过的后端全量 `mvn -q test` 或前端全量 `npm test`，不是口头豁免。

| 计划项 | 闭环状态 | 证据来源 |
|---|---|---|
| 未登录访问业务页 | 本阶段实测通过 | GET `/api/admin/production/work-orders?page=1&pageSize=5` 返回 `401 AUTH_UNAUTHORIZED` |
| 无权限直接访问 URL 或 API | 本阶段实测通过 | 无权限用户访问 `/cost/records` 跳转 `forbidden`，截图 `19-role-forbidden.png`；成本、库存、生产查询接口均返回 `403 AUTH_FORBIDDEN` |
| 停用仓库或物料被后续业务引用 | 自动化覆盖通过 | `InventoryAdminControllerTests.invalidDocumentRequestsReturnControlledInventoryErrors` 覆盖停用仓库 `INVENTORY_WAREHOUSE_INVALID`、停用物料 `INVENTORY_MATERIAL_INVALID`；`MaterialAdminControllerTests` 覆盖停用单位、分类被物料引用时的 `MASTER_DATA_REFERENCE_INVALID`、`MASTER_DATA_CATEGORY_IN_USE`、`MASTER_DATA_UNIT_IN_USE` |
| 未启用 BOM 创建或发布工单 | 本阶段实测和自动化覆盖通过 | 本阶段未启用 BOM ID `28` 创建工单返回 `400 PRODUCTION_BOM_INVALID`；`ProductionAdminControllerTests.businessRulesReturnControlledProductionErrors` 覆盖发布前 BOM 被停用时返回 `PRODUCTION_BOM_INVALID` |
| 库存不足领料 | 本阶段实测和自动化覆盖通过 | 工单 ID `20` 超库存领料返回 `409 PRODUCTION_ISSUE_EXCEEDS_REQUIRED`；`ProductionAdminControllerTests.businessRulesReturnControlledProductionErrors` 覆盖领料过账库存不足 `PRODUCTION_STOCK_NOT_ENOUGH` 且库存流水不增加 |
| 重复期初 | 自动化覆盖通过 | `InventoryAdminControllerTests.postingStateErrorsKeepStockAndMovementsConsistent` 覆盖第二张同仓库同物料期初过账返回 `409 INVENTORY_OPENING_EXISTS` |
| 负数调整越界 | 本阶段实测和自动化覆盖通过 | 库存调整单 ID `14` 调减 `999999.000000` 返回 `409 INVENTORY_STOCK_NOT_ENOUGH`，截图 `16-exception-stock-not-enough.png`，库存不变；同一库存测试类覆盖过账失败不新增流水 |
| 草稿不可领料、报工、入库 | 自动化覆盖通过 | `ProductionAdminControllerTests.businessRulesReturnControlledProductionErrors` 覆盖含草稿领料、草稿报工、草稿完工入库时取消工单被 `PRODUCTION_WORK_ORDER_STATUS_INVALID` 拦截；页面层在草稿/不可执行状态会禁用对应保存入口 |
| 已取消不可执行 | 自动化覆盖通过 | `CostAdminControllerTests.manualCostRecordRejectsMissingOrCancelledWorkOrder` 覆盖已取消工单不能创建手工成本，返回 `COST_WORK_ORDER_STATUS_INVALID`；生产执行接口的状态拦截由 `ProductionAdminControllerTests.businessRulesReturnControlledProductionErrors` 覆盖 |
| 重复过账领料、报工、入库 | 本阶段实测和自动化覆盖通过 | 本阶段已过账领料单 ID `13` 重复过账返回 `409 PRODUCTION_DUPLICATE_POST` 且库存不变；`ProductionAdminControllerTests` 的 `assertProductionDuplicatePost` 接受 `PRODUCTION_DUPLICATE_POST` 或 `PRODUCTION_MOVEMENT_SOURCE_DUPLICATED`，覆盖重复来源防重 |
| 完工入库数量超过合格数量 | 本阶段实测和自动化覆盖通过 | 工单 ID `22` 未有合格报工时创建入库返回 `409 PRODUCTION_RECEIPT_EXCEEDS_REPORTED`；后端生产测试覆盖部分报工后超入同一错误码 |
| 自动来源重复归集 | 自动化覆盖通过 | `CostAdminControllerTests.duplicateAutomaticSourceReturnsCostSourceDuplicatedAndRollsBackPosting` 覆盖重复自动来源返回 `COST_SOURCE_DUPLICATED`，并断言库存回滚、领料单保持 `DRAFT` |
| 草稿来源归集 | 自动化覆盖通过 | `CostAdminControllerTests.draftAutomaticMaterialIssueSourceIsRejected` 和 `draftAutomaticWorkReportSourceIsRejected` 覆盖草稿领料、草稿报工来源返回 `COST_SOURCE_DOCUMENT_STATUS_INVALID`，不生成成本记录 |
| 无效工单 | 本阶段实测和自动化覆盖通过 | 本阶段工单 ID `99999999` 创建手工成本返回 `404 COST_WORK_ORDER_NOT_FOUND`；成本测试类同名用例覆盖不存在工单和已取消工单 |
| 非法金额 | 本阶段实测和自动化覆盖通过 | 本阶段金额 `-1` 返回 `400 COST_AMOUNT_INVALID`；`CostAdminControllerTests.manualCostRecordRejectsInvalidAmountQuantityAndUnitPrice` 覆盖负数、超精度、超整数位金额 |
| 非法数量和超精度 | 自动化覆盖通过 | `InventoryAdminControllerTests.validatesPrecisionLengthsAndGeneratesUniqueDocumentNumbersQuickly` 覆盖库存数量 `1.0000001` 和超整数位返回 `INVENTORY_QUANTITY_INVALID`；`CostAdminControllerTests.manualCostRecordRejectsInvalidAmountQuantityAndUnitPrice` 覆盖成本数量 `0`、负数、`1.0000001` 和超整数位返回 `COST_QUANTITY_INVALID` |

## 视觉分析

- 截图目录：`docs/testing/phase-one-e2e-visual-audit/`。
- 视觉记录：`docs/testing/phase-one-e2e-visual-audit/notes.md`。
- 结果：已保存 51 张 PNG 截图，覆盖管理员菜单、物料、BOM、库存、生产工单、领料、报工、完工入库、成本查询、成本详情、工单成本分区、权限角色、异常状态、窄屏视口、本轮浏览器写操作补证和测试子代理附加页面补拍。

## 最终复审与质量门

### 固定角色复审恢复记录

本阶段最终复审优先派发给固定产品经理、UI 设计师和测试线程。固定线程在本轮只记录到委派输入后返回 `systemError`，未形成可用复审结论；多智能体替代创建返回 `agent thread limit reached`。为遵守固定角色边界，主代理只创建同角色替代线程，不新增第六类角色。

| 角色 | 替代原因 | 复审结论 | 说明 |
|---|---|---|---|
| 产品经理 | 固定线程 `systemError`；第一产品替代线程卡在 `waitingOnApproval`，追加不执行命令的短指令后仍未返回 | 通过 | 第二产品经理同角色替代基于主代理已核验的当前证据摘要复审，结论为“通过” |
| UI 设计师 | 固定线程 `systemError`，多智能体替代创建达到线程上限 | 通过 | UI 同角色替代确认 51 张 PNG、0 空文件、清单一致、无失败截图；该线程受 Windows 沙箱限制，未能逐图肉眼打开 PNG，基于文件有效性、尺寸和记录一致性复审 |
| 测试 | 固定线程 `systemError`，多智能体替代创建达到线程上限 | 通过 | 测试同角色替代确认视觉目录一致、异常矩阵逐项闭环、权限追溯和成本边界证据自洽 |

### 最终验证命令

2026-07-03 最终收口前执行以下验证：

| 验证项 | 命令或路径 | 结果 |
|---|---|---|
| 视觉目录一致性 | `docs/testing/phase-one-e2e-visual-audit/` 与 `notes.md` 清单比对 | 51 张 PNG、清单 51 项、空文件 0、缺失 0、额外 0、无失败过程截图 |
| 旧缺口标记搜索 | 搜索旧截图计数、旧失败截图编号、旧 API 证据缺口表述、待办标记 | 无命中 |
| 空白检查 | `git diff --check` | 退出码 0；仅既有 LF/CRLF 提示，无空白错误 |
| 后端健康检查 | `http://127.0.0.1:18080/api/health` | 返回 `{"status":"UP","service":"qherp-api"}` |
| 前端核心路由 | `http://127.0.0.1:5178/production/work-orders` | HTTP `200` |
| 前端全量测试 | `npm test` | 31 个测试文件、208 个用例通过，退出码 0 |
| 前端类型检查 | `npm run typecheck` | `vue-tsc --noEmit` 通过，退出码 0 |
| 前端构建 | `npm run build` | `vue-tsc --noEmit && vite build` 通过，1734 个模块完成构建，退出码 0 |
| 后端全量测试 | Docker Maven JDK 21 执行 `mvn -q test` | 退出码 0；Surefire 汇总 14 个报告文件、102 个测试、0 失败、0 错误、0 跳过 |
| Testcontainers 残留容器 | `docker ps -a --filter "label=org.testcontainers"` | 无输出 |

## 缺陷汇总

| 等级 | 数量 | 处理结论 |
|---|---:|---|
| 阻断 | 0 | 自动化基线阶段未发现 |
| 严重 | 0 | 自动化基线阶段未发现 |
| 一般 | 0 | 自动化基线阶段未发现 |

## 最终结论

本阶段最终复审和质量门通过：一期生产主链路从基础资料、BOM、库存、生产工单、领料、报工、完工入库到成本归集均具备当前证据；权限路径、库存不足异常、BOM 不可用、超领、超报、超完工入库、重复过账、非法成本、无效工单成本、成本记录不反写和审计记录均有浏览器截图、接口证据或自动化测试覆盖。当前未发现阻断、严重或一般缺陷，可进入阶段收口判断。
