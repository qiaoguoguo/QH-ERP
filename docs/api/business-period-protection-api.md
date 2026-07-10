# 业务期间与历史数据保护接口契约

## 通用规则

- 本阶段管理接口以 `/api/admin/system/business-periods` 为权威前缀，所有响应使用统一 `ApiResponse<T>` 包装。
- 写接口需要 CSRF token，后端独立权限校验是最终边界。
- 期间锁定时，影响经营口径的服务层写操作返回稳定错误码；页面禁用只用于体验提示。
- 无匹配期间按开放处理，并在解析结果中返回未配置状态。

## 核心响应类型

```json
{
  "id": 1,
  "periodCode": "2026-07",
  "periodName": "2026年07月",
  "startDate": "2026-07-01",
  "endDate": "2026-07-31",
  "status": "OPEN",
  "statusName": "开放",
  "lockedBy": null,
  "lockedAt": null,
  "lockReason": null,
  "unlockedBy": null,
  "unlockedAt": null,
  "unlockReason": null
}
```

状态仅为 `OPEN` 或 `LOCKED`。日期使用 `YYYY-MM-DD`，时间使用 ISO 8601 格式。

## 权限点

| 权限点 | 用途 |
|---|---|
| `system:business-period:view` | 查看列表、详情与日期解析结果 |
| `system:business-period:create` | 新增期间与按月生成 |
| `system:business-period:update` | 编辑开放期间 |
| `system:business-period:lock` | 锁定开放期间 |
| `system:business-period:unlock` | 解锁已锁定期间 |

## 接口

### `GET /api/admin/system/business-periods`

查询期间列表。支持 `periodCode`、`status`、`startDate`、`endDate`、`page`、`size` 筛选和分页，要求 `system:business-period:view`。

### `POST /api/admin/system/business-periods`

创建开放期间，要求 `system:business-period:create`。

```json
{
  "periodCode": "2026-07",
  "periodName": "2026年07月",
  "startDate": "2026-07-01",
  "endDate": "2026-07-31"
}
```

### `PUT /api/admin/system/business-periods/{id}`

编辑开放期间，要求 `system:business-period:update`。已锁定期间不得修改日期范围或编码。

### `POST /api/admin/system/business-periods/generate-monthly`

按自然月生成开放期间，要求 `system:business-period:create`。

```json
{
  "startMonth": "2026-07",
  "endMonth": "2026-12"
}
```

### `POST /api/admin/system/business-periods/{id}/lock`

锁定开放期间，要求 `system:business-period:lock`。请求体中的 `reason` 必填，成功后返回核心响应类型并写入审计记录。

```json
{ "reason": "月度经营数据核对完成" }
```

### `POST /api/admin/system/business-periods/{id}/unlock`

解锁已锁定期间，要求 `system:business-period:unlock`。请求体中的 `reason` 必填，成功后返回核心响应类型并写入审计记录。

### `GET /api/admin/system/business-periods/resolve?businessDate=YYYY-MM-DD`

按业务日期解析期间，要求 `system:business-period:view`。匹配时返回核心响应类型；未匹配时返回 `configured: false`、`businessDate` 与开放处理提示，不返回错误。

## 错误码

| 错误码 | 触发条件 |
|---|---|
| `BUSINESS_PERIOD_LOCKED` | 写入业务日期落入已锁定期间 |
| `BUSINESS_PERIOD_OVERLAPPED` | 创建、编辑或生成导致日期范围重叠 |
| `BUSINESS_PERIOD_DATE_RANGE_INVALID` | 开始日期晚于结束日期或月份范围非法 |
| `BUSINESS_PERIOD_REASON_REQUIRED` | 锁定或解锁未提供原因 |
| `BUSINESS_PERIOD_NOT_FOUND` | 目标期间不存在 |
| `BUSINESS_PERIOD_STATUS_INVALID` | 当前状态不允许锁定、解锁或编辑 |

`BUSINESS_PERIOD_LOCKED` 的错误详情必须包含 `periodCode`、`periodName`、`businessDate` 和可展示的业务提示；不得暴露内部 SQL 或堆栈。无权限请求使用现有认证、鉴权错误信封，不得通过接口绕过期间校验。

## 写入保护约束

业务服务调用 `BusinessPeriodGuard.assertWritable(businessDate, operation, sourceType, sourceId)`。库存、采购、销售、生产、成本、往来和反向业务的创建、日期修改、确认、发布、过账、取消或冲减等影响经营口径的动作必须在原业务写入前校验。报表与来源追溯为只读接口，锁定期间仍可访问。
