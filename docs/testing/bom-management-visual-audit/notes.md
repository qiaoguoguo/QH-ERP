# BOM 管理浏览器验收视觉分析

## 验收环境

- 日期：2026-07-03
- 分支：`codex/bom-management-foundation`
- 前端地址：`http://127.0.0.1:5173/materials/boms`
- 后端健康检查：`http://127.0.0.1:18080/api/health`，返回 `{"service":"qherp-api","status":"UP"}`
- 截图方式：Playwright 真实浏览器截图，桌面视口 `1440x920`，移动视口 `390x844`
- 管理员账号：`admin`
- 只读账号：`bva171ik_readonly`

## 截图清单

| 文件 | 视口 | 验收内容 | 结论 |
|---|---|---|---|
| `01-bom-list-desktop.png` | 1440x920 | BOM 列表默认状态 | 通过 |
| `02-bom-filter-result-desktop.png` | 1440x920 | BOM 查询筛选结果 | 通过 |
| `03-bom-empty-state-desktop.png` | 1440x920 | BOM 空状态 | 通过 |
| `04-bom-create-form-desktop.png` | 1440x920 | BOM 创建表单 | 通过 |
| `05-bom-line-editor-desktop.png` | 1440x920 | BOM 明细编辑表格 | 通过 |
| `06-bom-form-error-desktop.png` | 1440x920 | BOM 表单必填错误 | 通过 |
| `07-bom-detail-readonly-desktop.png` | 1440x920 | BOM 详情只读状态 | 通过 |
| `08-bom-copy-version-desktop.png` | 1440x920 | BOM 复制版本弹窗 | 通过 |
| `09-bom-enable-conflict-desktop.png` | 1440x920 | 同父项启用版本唯一错误 | 通过 |
| `10-bom-cycle-error-desktop.png` | 1440x920 | BOM 循环引用错误 | 通过 |
| `11-bom-readonly-permission-desktop.png` | 1440x920 | 只读账号列表和详情 | 通过 |
| `12-bom-list-mobile.png` | 390x844 | 移动端 BOM 列表 | 通过 |
| `13-bom-form-mobile.png` | 390x844 | 移动端 BOM 表单 | 通过 |
| `14-bom-line-editor-mobile.png` | 390x844 | 移动端 BOM 明细编辑器 | 通过 |

## 功能验收结果

- 管理员登录、进入 BOM 管理、查询、空状态、详情、新建草稿、添加三条明细、启用、复制版本、停用旧版本、启用新版本均通过。
- 重复版本返回 `BOM_VERSION_EXISTS`。
- 重复子项返回 `BOM_DUPLICATE_ITEM`。
- 用量为 0 返回 `BOM_QUANTITY_INVALID`。
- 父项等于子项返回 `BOM_SELF_REFERENCE`。
- 停用物料引用返回 `BOM_CHILD_MATERIAL_INVALID`。
- 可检测循环引用返回 `BOM_CYCLE_DETECTED`。
- 只读账号可进入列表和详情，新增、编辑、复制、启用、停用按钮不可见。
- 只读账号直接调用写接口返回 `AUTH_FORBIDDEN`。

## 视觉分析结论

- 布局稳定性：列表、筛选区、分页、弹窗、抽屉和错误提示在桌面视口下结构稳定；移动端保持单列布局和横向表格滚动，未发现页面级错位。
- 信息密度：桌面端保持 ERP 后台高密度列表风格，筛选区和表格行高适合重复维护；明细编辑器能同时展示行号、物料、用量、单位、损耗率、备注和删除操作。
- 关键操作可见性：新增、查询、重置、详情、编辑、复制、启用、停用、保存、取消等操作在有权限时可识别；只读账号写操作按钮不可见。
- 文案溢出：长 BOM 编码和物料名称在表格列中使用省略显示，未穿出容器；表单、错误提示和状态标签未遮挡相邻内容。
- 控件重叠：弹窗底部按钮、明细表格输入框、下拉控件、错误提示、固定操作列均未互相遮挡。桌面列表依赖横向空间展示高密度字段，固定操作列保持关键动作可见。
- 响应式适配：移动端侧边栏转为顶部区域，查询区和表单纵向排列，表格可横向滚动，核心操作仍可触达。
- 业务状态识别：草稿、启用、停用状态标签区分清楚；启用冲突、循环引用和必填错误提示位置明确。

## 缺陷处理

- 发现问题：桌面列表操作列原宽度不足，草稿行的启用、停用按钮显示不完整。
- 处理方式：将 BOM 列表操作列 `min-width` 从 `250` 调整为 `330`，重新执行前端定向测试、构建和浏览器截图。
- 处理结果：操作按钮完整可见，未发现新的阻断视觉问题。

## 最终结论

BOM 管理达到本阶段功能、权限、异常、本地部署和浏览器视觉验收标准，可进入功能分支提交和主分支阶段验收准备。
