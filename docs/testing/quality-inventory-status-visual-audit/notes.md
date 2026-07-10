# 质量检验与库存质量状态基础视觉审计记录

## 验收环境

- 阶段：017 质量检验与库存质量状态基础。
- 本地地址：`http://127.0.0.1:5175/`，后端 `http://127.0.0.1:18080/`。
- 验收账号：管理员 `admin`、只读质量用户 `quality_read_017_visual`、无质量权限用户 `quality_none_017_visual`。
- 验收数据：`QI-017-VISUAL-001` 待处理待检记录、`QI-017-VISUAL-002` 已处理记录、`SO-017-VISUAL-001` 和 `SH-017-VISUAL-001` 销售不可用候选、待检/不合格/冻结库存余额。
- 质量口径：可用数量仅包含合格库存，待检、不合格和冻结库存不参与销售出库、生产领料或生产补料。

## 截图清单

| 截图 | 视口 | 页面路径或场景 | 当前状态 | 验收重点 |
|---|---:|---|---|---|
| `01-quality-inspection-list-desktop.png` | 1440x920 | `/quality/inspections` 质量确认列表 | 已截图 | 标题说明、筛选、待检标签、已处理禁用原因、固定操作列 |
| `02-quality-inspection-process-drawer-desktop.png` | 1440x920 | 质量确认处理抽屉 | 已截图 | 字段顺序、日期选择器、数量右对齐、底部按钮可见 |
| `03-quality-inspection-quantity-error-desktop.png` | 1440x920 | 处理抽屉合计不平 | 已截图 | 固定错误文案、输入保留、抽屉不关闭 |
| `04-inventory-quality-balance-desktop.png` | 1440x920 | `/inventory/balances` 库存余额质量分布，横向滚动到分布列 | 已截图 | 现存、锁定、合格可用、待检、合格、不合格、冻结分布 |
| `05-sales-shipment-non-qualified-disabled-desktop.png` | 1440x920 | `/sales/shipments/2/edit` 销售出库草稿不可用候选，横向滚动到控制列 | 已截图 | 合格口径、现存为 0、合格可用为 0、最大可选为 0、禁用原因、数量输入禁用 |
| `06-production-issue-non-qualified-disabled-desktop.png` | 1440x920 | `/production/work-orders/1/material-issues` 生产领料不可用候选 | 已截图 | 生产领料只看合格可用，最大可选为 0，禁用原因直接可见 |
| `07-quality-readonly-permission-desktop.png` | 1440x920 | 只读质量用户 | 已截图 | 可查看列表，不显示处理按钮 |
| `08-quality-forbidden-desktop.png` | 1440x920 | 无权限直访 `/quality/inspections` | 已截图 | 进入无权限页，质量菜单隐藏 |
| `09-quality-period-locked-error-desktop.png` | 1440x920 | 锁定期间提交质量确认 | 已截图 | 后端期间锁定错误可见，抽屉保留 |
| `10-quality-inspection-mobile-390x844.png` | 390x844 | 质量确认列表与处理抽屉窄屏 | 已截图 | 主容器不截断，抽屉底部按钮可触达 |
| `11-purchase-return-frozen-disabled-desktop.png` | 1440x920 | 采购退货冻结库存候选，横向滚动到质量控制列 | 已截图 | 冻结质量状态、合格可用为 0、最大可选为 0、禁用原因、退货数量禁用 |
| `12-production-supplement-non-qualified-disabled-desktop.png` | 1440x920 | 生产补料合格可用不足候选，横向滚动到质量控制列 | 已截图 | 合格口径、现存为 0、合格可用为 0、最大可选为 0、禁用原因、补料数量禁用 |

## 视觉结论

- 质量确认列表、处理抽屉、数量校验错误和期间锁定错误均能在可信浏览器视口中稳定复现。
- 库存余额页能展示质量状态维度的现存、合格可用、待检、不合格和冻结分布；宽表通过横向滚动截图取证，未使用失真全页截图。
- 销售出库、生产领料、采购退货和生产补料候选区域均展示质量状态、现存数量、合格可用、最大可选数量和禁用原因；不可用候选数量输入处于禁用状态。
- 只读质量用户可查看列表但没有处理入口；无质量权限用户直访质量确认会进入无权限页且侧边栏不显示质量菜单。
- 移动端 390x844 下处理抽屉内容可读，底部取消和提交按钮可触达。
- 未发现影响当前阶段验收的布局重叠、关键文案溢出、核心操作不可见或权限状态识别问题。
