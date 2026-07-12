# 020 销售项目与合同管理基础交付前验证记录

验证基线：`9ce79b98c8827c0858520873e55873d8f3dbba87`

验证结论：PASS。020 最终修复后，CLI 全量门禁、服务重启、权限脱敏接口断言、桌面权限复验和移动证据补齐均已通过；原合同泄露阻断和两项移动证据缺口已关闭。

## CLI 全量门禁

| 顺序 | 命令 | 结果 |
| --- | --- | --- |
| 1 | `cd apps/api; $env:JAVA_HOME='C:\Users\14567\.codex\jdks\jdk-21.0.11+10'; .\mvnw.cmd test` | PASS；Tests run: 309, Failures: 0, Errors: 0, Skipped: 0；退出码 0；耗时 00:03:37.1606390 |
| 2 | `npm --prefix apps/web test` | PASS；Test Files 79 passed；Tests 596 passed；退出码 0；耗时 00:01:40.5073012 |
| 3 | `npm --prefix apps/web run typecheck` | PASS；退出码 0；耗时 00:00:13.2267666 |
| 4 | `npm --prefix apps/web run build` | PASS；`vite build` built in 10.44s；退出码 0；耗时 00:00:24.6301340；存在 chunk size 警告 |
| 5 | `git diff --check` | PASS；退出码 0；耗时 00:00:00.1302594 |

警告记录：后端全量测试存在 SpringDoc 默认开放 `/v3/api-docs` 和 `/swagger-ui.html` 提示、Mockito/ByteBuddy 动态 agent 提示，以及测试容器关闭后的 Hikari 连接池后台校验日志；均未导致失败，最终 Surefire 汇总为 0 failures/0 errors。前端构建存在 Vite chunk size 警告，退出码为 0。

## 迁移验证

| 场景 | 数据库 | 命令摘要 | 断言 |
| --- | --- | --- | --- |
| 空库 V1-V17 | `qherp_020_empty` | 按文件名顺序应用 `apps/api/src/main/resources/db/migration/V*.sql` | `sales_project`、`sales_project_contract`、`sales_order` 均为空；V17 应用成功 |
| 019 存量升级 | `qherp_020_v16_upgrade` | 应用 V1-V16，插入历史无项目销售订单 `SO-020-HISTORY`，再应用 V17 | 历史订单 `project_id`/`contract_id` 仍为空；采购/生产表未新增项目字段；销售出库、往来、报表、追踪关键查询可执行 |

两个迁移验证库均已执行 `DROP DATABASE` 清理。

本轮基于 9ce79b9 的最终修复未修改 V17 迁移文件；空库 V1-V17 与 V1-V16 插入历史无项目销售订单后升级 V17 的迁移验证沿用同一最终窗口已通过结果，未重复创建验证库。

## 本地服务

PostgreSQL：Docker 容器 `qherp-postgres`，`pg_isready` 返回 accepting connections。

旧服务处理：确认并停止旧 API Java PID `26736`、旧 Web Node PID `29640`；最终日志切换时再次确认并停止 API Java PID `22868`、Web Node PID `31568`，二者分别监听 `18080` 和 `5173` 且命令行指向当前 qherp 工作区。

当前服务：

| 服务 | URL | PID | 日志 |
| --- | --- | --- | --- |
| API | `http://localhost:18080/api/health` | Java PID `28168` | 归档证据：`service-api-9ce79b9.stdout.log`、`service-api-9ce79b9.stderr.log`；当前活动日志：`.superpowers/runtime/020/service-api.stdout.log`、`.superpowers/runtime/020/service-api.stderr.log` |
| Web | `http://127.0.0.1:5173/` | Node PID `15816` | 归档证据：`service-web-9ce79b9.stdout.log`、`service-web-9ce79b9.stderr.log`；当前活动日志：`.superpowers/runtime/020/service-web.stdout.log`、`.superpowers/runtime/020/service-web.stderr.log` |

API health 返回 `{"service":"qherp-api","status":"UP"}`；前端 HTTP 200。最终保持服务运行。

## 测试数据

数据文件：`browser-test-data.json`

关键标识：

| 类型 | 标识 |
| --- | --- |
| 项目 | `SP20260712143256646001` / `020全量验收项目0712143048` |
| 主合同 | `SC20260712143336172001` / `020主合同0712143048` |
| 补充合同 | `SC20260712143452942002` / `020补充合同0712143048` |
| 关联销售订单 | `SO-20260712143720316-001` |
| 历史无项目订单 | `SO-20260712144307780-002`，确认后全部出库，`projectId=null`、`contractId=null` |
| 历史无项目出库 | `SS-20260712144727406-000`，已过账 |
| 库存调整 | `INV-ADJ-20260712144727248-000`，已过账 |

## 浏览器验收覆盖

已通过真实前端操作覆盖：

- 管理员主路径：登录、项目列表、新建项目、主合同草稿创建、主合同生效、项目激活、补充合同创建与生效、销售订单创建并关联项目合同、订单列表与详情回显、项目详情合同/订单状态分布/订单明细/操作记录。
- 异常路径：空项目创建校验、合同保存错误、原因缺失校验、无生效主合同激活受限、补充合同前置受限、version 冲突、非终态订单阻止项目关闭、非法销售订单关联、筛选互斥。
- 历史兼容：无项目订单查询、确认、销售出库过账、销售出库详情、库存变动追踪入口、应收/应付台账、销售经营报表。
- 权限：合同受限、订单受限、只读、无项目权限、无合同权限、无订单查看权限。
- 空态/错态：空项目列表、详情无合同/无关联订单、合同保存错误、原因缺失、无效项目详情错误。

API 异常证据：`api-negative-checks.json`

权限脱敏复验证据：`permission-redaction-checks-9ce79b9.json`

| 场景 | 断言 |
| --- | --- |
| 合同受限账号 `t020cr0712143048` 直接请求 `/api/admin/sales-projects/1/sales-orders` | HTTP 200；订单号 `SO-20260712143720316-001` 与状态可见；`contractId`、`contractNo`、`externalContractNo` 均为 null；响应正文不含 `SC20260712143336172001` 或 `EXT-0712143048` |

| 场景 | HTTP | 错误码 |
| --- | --- | --- |
| 无已生效主合同激活项目 | 409 | `PROJECT_MAIN_CONTRACT_REQUIRED` |
| 补充合同前置失败 | 409 | `CONTRACT_PROJECT_NOT_ACTIVE` |
| 项目旧版本更新 | 409 | `PROJECT_CONCURRENT_MODIFICATION` |
| 项目存在非终态订单关闭 | 409 | `PROJECT_HAS_OPEN_BUSINESS` |
| 销售订单客户与项目客户不一致 | 409 | `SALES_ORDER_PROJECT_CUSTOMER_MISMATCH` |
| `projectLinked` 与 `projectId` 筛选互斥 | 400 | `VALIDATION_ERROR` |
| 原因 201 字 | 400 | `PROJECT_REASON_REQUIRED` |

## 截图清单

| 文件 | 视口 | 页面/场景 | 账号 | 结论 |
| --- | --- | --- | --- | --- |
| `desktop-01-empty-project-list.png` | 1440x900 | 项目列表空态 | admin | 空态可信 |
| `desktop-02-project-create-validation.png` | 1440x900 | 项目创建必填错误 | admin | 错误提示可信 |
| `desktop-03-project-detail-empty-contract-orders.png` | 1440x900 | 项目详情无合同/无订单 | admin | 空态可信 |
| `desktop-04-contract-drawer-empty.png` | 1440x900 | 合同抽屉初始态 | admin | 抽屉可信 |
| `desktop-05-contract-save-error.png` | 1440x900 | 合同保存错误 | admin | 错误提示可信 |
| `desktop-06-contract-effective-drawer.png` | 1440x900 | 主合同生效后 | admin | 状态可信 |
| `desktop-07-project-active-detail.png` | 1440x900 | 项目激活后 | admin | 状态可信 |
| `desktop-08-supplement-contract-effective.png` | 1440x900 | 补充合同生效后 | admin | 状态可信 |
| `desktop-09-sales-order-project-contract-select.png` | 1440x900 | 销售订单项目合同候选 | admin | 候选可信 |
| `desktop-10-sales-order-detail-linked.png` | 1440x900 | 关联订单详情 | admin | 回显可信 |
| `desktop-11-sales-order-list-linked-and-history.png` | 1440x900 | 订单列表含历史无项目订单 | admin | 查询可信 |
| `desktop-12-project-detail-with-order-operations.png` | 1440x900 | 项目详情含合同/订单/操作记录 | admin | 业务回显可信 |
| `desktop-13-reason-dialog-validation.png` | 1440x900 | 原因缺失校验 | admin | 错误区可信 |
| `desktop-14-contract-restricted-project-detail.png` | 1440x900 | 合同受限 | 合同受限账号 | 旧证据，已由 9ce79b9 复验截图替代 |
| `desktop-14-contract-restricted-project-detail-9ce79b9.png` | 1440x900 | 合同受限 | 合同受限账号 | 合同区域和关联订单合同信息均受限，订单号/状态可见，无合同编号泄露 |
| `desktop-15-order-restricted-project-detail.png` | 1440x900 | 订单受限 | 订单受限账号 | 订单摘要受限 |
| `desktop-16-readonly-project-detail.png` | 1440x900 | 只读项目详情 | 只读账号 | 无编辑/状态动作 |
| `desktop-17-no-project-permission.png` | 1440x900 | 无项目权限 | 无项目账号 | forbidden |
| `desktop-18-no-contract-permission.png` | 1440x900 | 无合同权限 | 无合同账号 | 旧证据，已由 9ce79b9 复验截图替代 |
| `desktop-18-no-contract-permission-9ce79b9.png` | 1440x900 | 无合同权限 | 无合同账号 | 合同区域和关联订单合同信息均受限，订单号/状态可见，无合同编号泄露 |
| `desktop-19-no-order-view-permission.png` | 1440x900 | 无订单查看权限 | 无订单账号 | 订单摘要受限 |
| `desktop-20-history-unlinked-order-confirmed.png` | 1440x900 | 历史无项目订单全部出库 | admin | 兼容可信 |
| `desktop-21-sales-shipment-list.png` | 1440x900 | 销售出库列表 | admin | 查询可信 |
| `desktop-21b-history-shipment-detail.png` | 1440x900 | 历史无项目出库详情 | admin | 出库可信 |
| `desktop-22-inventory-movements-trace-entry.png` | 1440x900 | 库存变动/追踪入口 | admin | 查询可信 |
| `desktop-23-finance-receivables-list.png` | 1440x900 | 应收台账 | admin | 查询可信 |
| `desktop-24-finance-payables-list.png` | 1440x900 | 应付台账 | admin | 查询可信 |
| `desktop-25-sales-report.png` | 1440x900 | 销售经营报表 | admin | 查询可信 |
| `desktop-26-invalid-project-detail-error.png` | 1440x900 | 无效项目详情 | admin | 错误态可信 |
| `mobile-01-project-list.png` | 390x844 | 移动项目列表 | admin | 旧证据，仅证明可访问 |
| `mobile-01-project-list-data-row-9ce79b9.png` | 390x844 | 移动项目列表 | admin | 真实项目行和行操作可见 |
| `mobile-01-project-list-data-row-actions-9ce79b9.png` | 390x844 | 移动项目列表 | admin | 替代移动列表证据，直接显示多条项目行和“详情/编辑”操作 |
| `mobile-02-project-detail.png` | 390x844 | 移动项目详情 | admin | 可访问；首屏被菜单下推 |
| `mobile-03-contract-drawer.png` | 390x844 | 移动合同抽屉 | admin | 可滚动；底部按钮标签有歧义 |
| `mobile-04-order-restricted-permission.png` | 390x844 | 移动订单受限 | 无订单账号 | 旧证据，目标区域不够直接 |
| `mobile-04-order-restricted-permission-target-9ce79b9.png` | 390x844 | 移动订单受限 | 无订单账号 | 替代移动订单受限证据，直接显示“订单摘要受限” |

## 发现问题

### 已关闭

1. 合同受限权限泄露合同编号：已关闭。
   - 接口证据：`permission-redaction-checks-9ce79b9.json`
   - 浏览器证据：`desktop-14-contract-restricted-project-detail-9ce79b9.png`、`desktop-18-no-contract-permission-9ce79b9.png`
   - 结论：无 `sales:contract:view` 时，项目合同区域和关联销售订单合同信息均显示受限文案；订单号、订单状态、数量和业务金额仍可见；未出现真实内部合同号或外部合同号。

2. 390px 移动项目列表证据缺口：已关闭。
   - 证据：`mobile-01-project-list-data-row-actions-9ce79b9.png`
   - 结论：截图直接显示真实项目行和“详情/编辑”操作按钮。

3. 390px 无订单查看权限证据缺口：已关闭。
   - 证据：`mobile-04-order-restricted-permission-target-9ce79b9.png`
   - 结论：截图直接显示“订单摘要受限”。

### 阻断

无。

### 严重

无。

### 一般

1. 操作记录多条记录在页面中连续拼接，缺少分隔或换行，扫描困难。
   - 证据：`desktop-12-project-detail-with-order-operations.png`

2. 390px 移动视口下侧边菜单仍占据首屏大量高度，项目详情内容被明显下推。
   - 证据：`mobile-02-project-detail.png`

3. 390px 合同抽屉底部同时出现合同状态动作“关闭”和抽屉“关闭”按钮，标签重复，存在误读风险。
   - 证据：`mobile-03-contract-drawer.png`

4. 部分空态表达重复，信息密度略高。
   - 证据：`desktop-03-project-detail-empty-contract-orders.png`

## 视觉审计结论

桌面 1440x900 下，列表、详情、抽屉、弹窗、权限态和异常态均为可信视口截图，未使用失真全页截图。主要业务字段、关键操作、状态标签和横向表格可识别。

移动 390x844 下，详情和抽屉可访问，控件未出现不可点击遮挡；但首屏空间被导航占用明显，合同抽屉底部按钮文案存在歧义，建议整改或纳入后续视觉优化。

9ce79b9 最终复验后，原权限阻断已关闭；本目录内补充截图均为可信视口截图，未使用失真全页截图。当前仅保留一般后续项：操作记录可读性、移动侧栏首屏占用、移动抽屉按钮文案歧义、空态重复。
