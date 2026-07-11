# 018 库存可用量、占用、冻结与预留视觉审计记录

## 2026-07-11 重新执行交付前全量验证窗口

### 执行结论

- 状态：DONE（质量检验阻断已修复并完成补充验证）
- 自动化验证：补充后端质量定向测试、后端全量、前端全量、类型检查、生产构建和 `git diff --check` 均通过。
- 本地服务：后端使用 `http://localhost:18081`，前端使用 `http://127.0.0.1:5174`。默认 18080/5173 被旧路径服务占用，本轮未中断旧服务。
- 历史阻断与处理结果：质量确认列表 `/quality/inspections` 曾显示“系统异常”，后端接口 `/api/admin/quality/inspections?page=1&pageSize=10` 曾返回 `SYSTEM_ERROR`，疑似 `QualityAdminService.sourceDocumentNo` 对 `PRODUCTION_COMPLETION` 来源单号字段使用错误。后端修复后，本轮浏览器补验确认页面可访问，完工入库来源单号显示为 `MFG-RCP-...`，未再出现系统异常。
- 交付判断：018 可交付用户验收；浏览器补验只更新质量页面和必要记录，未重跑全部 28 张截图。

### 自动化命令

| 项目 | 命令 | 结果 |
|---|---|---|
| 后端质量定向测试 | `cd apps/api; .\mvnw.cmd "-Dtest=QualityAdminControllerTests" test` | 通过，14 个测试，0 失败，0 错误 |
| 后端全量测试 | `cd apps/api; .\mvnw.cmd test` | 通过，246 个测试，0 失败，0 错误 |
| 前端全量测试 | `npm --prefix apps/web test` | 通过，73 个文件，526 个测试 |
| 前端类型检查 | `npm --prefix apps/web run typecheck` | 通过 |
| 前端生产构建 | `npm --prefix apps/web run build` | 通过，有 Vite 大 chunk 警告，非阻断 |
| 空白检查 | `git diff --check` | 通过 |

### 本轮截图清单

| 序号 | 文件名 | 视口 | 页面/状态 | 结论 |
|---|---|---|---|---|
| 01 | `01-inventory-balances-desktop-left-1440x900.png` | 1440x900 | 库存余额左侧字段 | 页面可访问，筛选区和左侧字段可读 |
| 02 | `02-inventory-balances-desktop-right-1440x900.png` | 1440x900 | 库存余额右侧数量字段 | 采购在途参考不叠加仓库 ATP，净需求缺口字段可见 |
| 03 | `03-inventory-reservation-drawer-1440x900.png` | 1440x900 | 占用预留抽屉 | 生产预留台账可见 |
| 04 | `04-inventory-in-transit-drawer-1440x900.png` | 1440x900 | 采购在途参考摘要 | 提示在途为物料级参考，不代表当前仓库现货可用 |
| 05 | `05-inventory-balances-mobile-390x844.png` | 390x844 | 库存余额移动首屏 | 无明显重叠，宽表需要横向/纵向滚动 |
| 06 | `06-inventory-balances-mobile-table-390x844.png` | 390x844 | 库存余额移动表格 | 操作按钮可见，未见按钮文字溢出 |
| 07 | `07-inventory-balance-no-reservation-permission-1440x900.png` | 1440x900 | 无预留权限用户 | 占用预留入口隐藏，余额主页面可进入 |
| 08 | `08-sales-order-detail-reservation-warehouse-1440x900.png` | 1440x900 | 销售订单详情 | 订单行预留仓库显示正确 |
| 09 | `09-sales-order-confirm-in-transit-shortage-error-1440x900.png` | 1440x900 | 销售确认异常 | 仅有在途、现货不足时确认失败并保持草稿 |
| 10 | `10-sales-shipment-form-reservation-warehouse-1440x900.png` | 1440x900 | 销售出库表单 | 出库字段和本次最多出库列可见 |
| 11 | `11-sales-shipment-line-selected-max-warehouse-1440x900.png` | 1440x900 | 销售出库明细选中 | 预留仓库、现货净可用、可承诺和本次最多出库带出 |
| 12 | `12-sales-shipment-cross-warehouse-error-1440x900.png` | 1440x900 | 销售出库跨仓 | 跨仓保存被阻断 |
| 13 | `13-sales-shipment-over-max-error-1440x900.png` | 1440x900 | 销售出库超量 | 超过本次最多出库保存被阻断 |
| 14 | `14-production-work-order-released-reservation-1440x900.png` | 1440x900 | 已发布生产工单 | 领料仓库和 BOM 用料快照可见 |
| 15 | `15-production-release-shortage-error-1440x900.png` | 1440x900 | 生产发布库存不足 | 发布失败并保持草稿 |
| 16 | `16-production-material-issue-warehouse-locked-1440x900.png` | 1440x900 | 生产领料表单 | 领料仓库锁定为工单领料仓库 |
| 17 | `17-production-material-issue-over-max-error-1440x900.png` | 1440x900 | 生产领料超量 | 超量保存被阻断 |
| 18 | `18-purchase-order-detail-in-transit-1440x900.png` | 1440x900 | 采购订单详情 | 采购在途参考和行在途参考可见 |
| 19 | `19-purchase-order-list-in-transit-1440x900.png` | 1440x900 | 采购订单列表 | 采购在途参考列可见 |
| 20 | `20-purchase-receipt-create-mobile-390x844.png` | 390x844 | 采购入库移动表单 | 移动表单无明显溢出 |
| 21 | `21-purchase-receipt-source-dropdown-mobile-390x844.png` | 390x844 | 采购入库来源下拉 | 来源候选显示未入库和采购在途参考，未见下拉溢出 |
| 22 | `22-reports-exceptions-shortage-1440x900.png` | 1440x900 | 异常报表短缺 | 同物料有采购在途仍显示仓库级库存不足 |
| 23 | `23-reports-exceptions-shortage-trace-1440x900.png` | 1440x900 | 异常报表追溯 | 追溯来源为库存余额，短缺数量为 5.000 |
| 24 | `24-reports-shortage-jump-inventory-balance-1440x900.png` | 1440x900 | 报表跳转库存余额 | 跳转到 `/inventory/balances`，未出现 403 |
| 25 | `25-business-period-locked-1440x900.png` | 1440x900 | 业务期间锁定 | 锁定期间状态可识别 |
| 26 | `26-quality-inspections-freeze-context-1440x900.png` | 1440x900 | 质量确认列表 | 已更新截图；页面可访问，完工入库来源单号和质量冻结数量可见，未见“系统异常” |
| 27 | `27-procurement-receipts-readonly-state-1440x900.png` | 1440x900 | 采购入库列表只读态 | 已过账记录仅显示详情 |
| 28 | `28-procurement-receipt-posted-readonly-detail-1440x900.png` | 1440x900 | 已过账采购入库详情 | 页面显示“已过账采购入库只读” |

### 视觉审计结论

- 库存余额宽表在桌面和 390px 下均可浏览，但桌面需横向滚动才能看到全部数量字段，移动端信息密度偏高，当前未见文字重叠或按钮溢出。
- 无 `inventory:reservation:view` 用户下占用预留入口隐藏，符合权限要求；该用户页面同时出现“无访问权限”提示，疑似缺少主数据引用权限导致筛选引用加载失败，作为一般风险记录。
- 销售、生产、采购和报表关键业务状态均完成可信截图。
- 质量确认页面阻断已修复，本轮已重新截图质量冻结关键状态；页面布局、信息密度、关键操作可见性、文案截断、控件重叠和业务状态识别未见新增阻断问题。

### 测试数据说明

- 本轮浏览器验收数据文件：`seed-data-181200.json`。
- 为让异常报表出现“仅在途不抵扣仓库级现货短缺”的可截图行，本地测试数据库中将 `sal_sales_order.id = 6` 从草稿标记为已确认。该操作只用于本轮本地浏览器验收数据，不涉及业务代码修改。

## 2026-07-11 交付前全量验证窗口阻断记录

### 执行结论

- 状态：BLOCKED
- 触发阶段：全量后端测试
- 阻断原因：后端全量测试 `.\mvnw.cmd test` 失败，`CostAdminControllerTests` 共 16 个用例在创建并发布生产工单时返回 `409 CONFLICT`，无法进入可信浏览器验收与视觉截图阶段。
- 处理原则：未启动本地服务进行视觉截图；未使用失真、历史或失败状态截图替代本次验收证据。

### 本轮截图清单

本轮未生成截图。原因是交付前全量验证在后端自动化阶段已阻断，继续进行浏览器验收会把未通过全量自动化的业务状态当作交付证据，不符合阶段验收规则。

### 待恢复后截图清单

后端阻断修复并重新通过全量自动化后，仍需按以下清单采集可信浏览器截图：

| 序号 | 文件名 | 视口 | 页面/状态 | 视觉重点 |
|---|---|---|---|---|
| 01 | `01-inventory-availability-overview-desktop.png` | 1366x768 | `/inventory/balances` 库存余额总览 | 账面、合格、占用、预留、冻结、现货净可用、采购在途参考、可承诺、净需求缺口 |
| 02 | `02-inventory-reservation-drawer-desktop.png` | 1366x768 | 占用预留追溯抽屉 | 来源、类型、状态、数量、审计可读性 |
| 03 | `03-inventory-in-transit-drawer-desktop.png` | 1366x768 | 采购在途参考摘要抽屉 | 在途仅为物料级参考，不表现为仓库现货可用 |
| 04 | `04-inventory-availability-mobile-390x844.png` | 390x844 | 库存余额窄屏 | 宽表滚动、字段不重叠、关键数量可识别 |
| 05 | `05-sales-order-reservation-desktop.png` | 1366x768 | 销售订单创建/编辑/详情 | 订单行预留仓库必填，确认按预留仓库 |
| 06 | `06-sales-shipment-shortage-desktop.png` | 1366x768 | 销售出库异常态 | 出库仓库与预留仓库不一致、超过本次最多出库的错误态 |
| 07 | `07-production-work-order-release-desktop.png` | 1366x768 | 生产工单发布 | 库存不足不静默成功，发布后用料预留 |
| 08 | `08-production-issue-warehouse-desktop.png` | 1366x768 | 生产领料 | 锁定工单领料仓库，跨仓领料阻断，超过本次最多领料错误态 |
| 09 | `09-procurement-in-transit-desktop.png` | 1366x768 | 采购订单列表/详情/采购入库 | 采购在途参考不表现为当前仓库现货可用 |
| 10 | `10-procurement-receipt-mobile-390x844.png` | 390x844 | 采购入库来源下拉 | 窄屏不溢出、不遮挡提交操作 |
| 11 | `11-report-shortage-desktop.png` | 1366x768 | 报表短缺与跳转 | 短缺不被采购在途抵扣，跳转库存余额权限一致 |
| 12 | `12-quality-freeze-period-permission-desktop.png` | 1366x768 | 质量冻结/无权限/只读/期间锁定关键状态 | 权限、期间、冻结状态识别清晰 |

### 最终结论

本轮未完成视觉审计，原因是后端全量测试存在阻断缺陷。修复后必须重新进入交付前全量验证窗口，并重新执行本目录截图清单与视觉分析记录。
