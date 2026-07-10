# 业务期间与历史数据保护视觉分析记录

## 验收环境

- 日期：2026-07-10
- 前端：`http://localhost:5173`
- 后端：`http://127.0.0.1:18080`
- 浏览器：Playwright Chromium
- 管理员账号：`admin`
- 只读账号：`period_read_20260710085321`
- 无权限账号：`period_none_20260710085321`

## 截图清单

| 文件名 | 视口 | 账号 | 页面 | 场景 | 结果 |
| --- | --- | --- | --- | --- | --- |
| `01-business-period-list-desktop.png` | 1440x900 | admin | `/system/business-periods` | 业务期间列表、筛选、开放状态和锁定入口 | 通过 |
| `02-business-period-lock-confirm-desktop.png` | 1440x900 | admin | `/system/business-periods` | 锁定弹窗、原因输入和确认按钮 | 通过 |
| `03-locked-period-write-error-desktop.png` | 1440x900 | admin | `/cost/records/create` | 锁定 2026-07 后以 2026-07-16 写入成本记录失败 | 通过 |
| `04-business-period-readonly-desktop.png` | 1440x900 | 只读账号 | `/system/business-periods` | 只读权限可查看列表，写操作入口隐藏 | 通过 |
| `05-business-period-forbidden-desktop.png` | 1440x900 | 无权限账号 | `/system/business-periods` | 无权限账号访问业务期间页面 | 通过 |
| `06-business-period-list-mobile-390x844.png` | 390x844 | admin | `/system/business-periods` | 窄屏折叠菜单后的筛选和列表 | 通过 |
| `07-report-locked-period-trace-desktop.png` | 1440x900 | admin | `/reports/cost` | 锁定期间仍可查看报表和来源追溯 | 通过 |

## 分析结论

- 页面布局：业务期间列表、筛选区、分页和操作列在桌面视口中结构稳定；移动端使用现有侧栏折叠按钮后，筛选控件纵向排列，列表可通过横向区域识别关键列。
- 信息密度：列表字段覆盖期间编码、名称、日期范围、状态、锁定人、锁定时间和锁定原因，桌面视口下可扫描；移动端保留核心查询和状态识别。
- 关键操作可见性：管理员视角下新增、按月生成、锁定和解锁入口可见；只读账号无写操作入口；无权限账号进入统一无权限页。
- 文案溢出和控件重叠：桌面关键文案没有遮挡；长锁定原因和锁定时间由表格截断承载，不影响期间状态和操作判断。
- 响应式适配：390x844 视口下页面可操作。移动截图采用折叠菜单后的可信视口截图，避免全页截图把侧栏和主内容拼接后降低业务区域可读性。
- 业务状态识别：开放、已锁定、写入失败、只读、无权限和报表追溯状态均有明确视觉信号。
- 阻断问题：未发现影响核心操作、权限状态判断、关键数据识别或浏览器验收的视觉问题。
