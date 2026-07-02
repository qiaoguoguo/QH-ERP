# BOM 管理接口契约

## 目标

定义 BOM 管理阶段的接口范围、字段、权限、错误码和联调验收规则，作为前后端实现与测试依据。

## 通用规则

- 所有接口使用统一 `ApiResponse<T>` 包装。
- 分页接口返回 `PageResponse<T>`。
- 时间字段使用后端生成的 ISO 8601 字符串。
- 写接口需要 CSRF token。
- 认证和权限沿用账号权限模块。
- 后端接口鉴权是最终安全边界，前端菜单和按钮权限只作为体验控制。
- 列表接口默认支持 `page`、`pageSize`、`keyword`、`status` 查询参数。
- 写操作成功后必须生成审计日志，至少记录操作人、动作、目标类型、目标标识和操作时间。
- `VALIDATION_ERROR`、`AUTH_FORBIDDEN`、`AUTH_UNAUTHORIZED` 沿用账号权限模块或通用错误码规则。

## 状态枚举

| 值 | 说明 |
|---|---|
| `DRAFT` | 草稿，可编辑 |
| `ENABLED` | 启用，可被后续生产工单引用，不可直接编辑明细 |
| `DISABLED` | 停用，保留历史追溯，不作为默认可选 |

## 字段定义

### BOM 汇总字段

| 字段 | 类型 | 请求必填 | 响应必返 | 说明 |
|---|---|---|---|---|
| id | number | 否 | 是 | 后端主键 |
| bomCode | string | 是 | 是 | BOM 编码，全局唯一 |
| parentMaterialId | number | 是 | 是 | 父项物料标识 |
| parentMaterialCode | string | 否 | 是 | 父项物料编码 |
| parentMaterialName | string | 否 | 是 | 父项物料名称 |
| versionCode | string | 是 | 是 | 版本号，同一父项物料内唯一 |
| name | string | 是 | 是 | BOM 名称 |
| baseQuantity | number | 是 | 是 | 基准数量，必须大于 0 |
| baseUnitId | number | 否 | 是 | 基准单位，默认父项物料基本单位 |
| baseUnitName | string | 否 | 是 | 基准单位名称 |
| status | string | 否 | 是 | `DRAFT`、`ENABLED` 或 `DISABLED` |
| itemCount | number | 否 | 是 | 明细行数量 |
| effectiveFrom | string | 否 | 否 | 生效日期 |
| effectiveTo | string | 否 | 否 | 失效日期 |
| remark | string | 否 | 否 | 备注 |
| createdAt | string | 否 | 是 | 创建时间 |
| updatedAt | string | 否 | 是 | 更新时间 |
| enabledAt | string | 否 | 否 | 启用时间 |

### BOM 明细字段

| 字段 | 类型 | 请求必填 | 响应必返 | 说明 |
|---|---|---|---|---|
| id | number | 否 | 是 | 明细主键 |
| lineNo | number | 是 | 是 | 行号，同一 BOM 内唯一 |
| childMaterialId | number | 是 | 是 | 子项物料标识 |
| childMaterialCode | string | 否 | 是 | 子项物料编码 |
| childMaterialName | string | 否 | 是 | 子项物料名称 |
| childMaterialType | string | 否 | 是 | 子项物料类型 |
| unitId | number | 否 | 是 | 用量单位，默认子项物料基本单位 |
| unitName | string | 否 | 是 | 单位名称 |
| quantity | number | 是 | 是 | 用量，必须大于 0 |
| lossRate | number | 否 | 是 | 损耗率，默认 0，范围 `0 <= lossRate < 1` |
| remark | string | 否 | 否 | 备注 |

## 接口分组

| 资源 | 路径 | 能力 |
|---|---|---|
| BOM | `/api/admin/boms` | list/get/create/update/copy/enable/disable |

## 接口定义

### 分页列表

- 方法：`GET`
- 路径：`/api/admin/boms`
- 权限：`material:bom:view`
- 查询参数：
  - `keyword`：按 BOM 编码、版本、名称、父项物料编码或父项物料名称模糊查询。
  - `status`：`DRAFT`、`ENABLED` 或 `DISABLED`。
  - `parentMaterialId`：父项物料标识。
  - `page`：页码。
  - `pageSize`：每页数量。
- 响应：`ApiResponse<PageResponse<BomSummary>>`。

### 详情

- 方法：`GET`
- 路径：`/api/admin/boms/{id}`
- 权限：`material:bom:view`
- 响应：`ApiResponse<BomDetail>`，其中 `BomDetail.items` 按 `lineNo` 升序返回。
- 失败：ID 不存在返回 `BOM_NOT_FOUND`。

### 创建

- 方法：`POST`
- 路径：`/api/admin/boms`
- 权限：`material:bom:create`
- 请求：`BomPayload`。
- 响应：`ApiResponse<BomDetail>`。
- 说明：
  - 未传 `status` 时后端默认 `DRAFT`；如传入 `status`，只允许 `DRAFT`。
  - `baseUnitId` 未传时后端使用父项物料基本单位。
  - 明细 `unitId` 未传时后端使用子项物料基本单位。
- 失败：
  - `VALIDATION_ERROR`
  - `BOM_CODE_EXISTS`
  - `BOM_VERSION_EXISTS`
  - `BOM_PARENT_MATERIAL_INVALID`
  - `BOM_CHILD_MATERIAL_INVALID`
  - `BOM_UNIT_INVALID`
  - `BOM_EMPTY_ITEMS`
  - `BOM_QUANTITY_INVALID`
  - `BOM_DUPLICATE_ITEM`
  - `BOM_SELF_REFERENCE`
  - `BOM_CYCLE_DETECTED`

### 更新

- 方法：`PUT`
- 路径：`/api/admin/boms/{id}`
- 权限：`material:bom:update`
- 请求：`BomPayload`。
- 响应：`ApiResponse<BomDetail>`。
- 说明：
  - 只有 `DRAFT` 状态允许更新主表和明细。
  - 更新采用整单替换明细：请求中的 `items` 为更新后的完整明细集合。
  - 未传 `status` 时保留原状态；如传入 `status`，只允许与当前状态一致的 `DRAFT`。
  - 不允许通过更新接口把草稿改为启用或停用，启停必须走专用接口。
- 失败：
  - `BOM_NOT_FOUND`
  - `BOM_STATUS_NOT_EDITABLE`
  - `VALIDATION_ERROR`
  - `BOM_CODE_EXISTS`
  - `BOM_VERSION_EXISTS`
  - `BOM_PARENT_MATERIAL_INVALID`
  - `BOM_CHILD_MATERIAL_INVALID`
  - `BOM_UNIT_INVALID`
  - `BOM_EMPTY_ITEMS`
  - `BOM_QUANTITY_INVALID`
  - `BOM_DUPLICATE_ITEM`
  - `BOM_SELF_REFERENCE`
  - `BOM_CYCLE_DETECTED`

### 复制为新版本

- 方法：`POST`
- 路径：`/api/admin/boms/{id}/copy`
- 权限：`material:bom:copy`
- 请求字段：
  - `bomCode`：新 BOM 编码，必填。
  - `versionCode`：新版本号，必填。
  - `name`：新 BOM 名称，可选；不传时沿用原名称。
  - `effectiveFrom`：新生效日期，可选。
  - `effectiveTo`：新失效日期，可选。
  - `remark`：备注，可选。
- 响应：`ApiResponse<BomDetail>`。
- 说明：复制结果固定为 `DRAFT`，复制原 BOM 主表父项、基准数量、基准单位和全部明细。
- 失败：
  - `BOM_NOT_FOUND`
  - `BOM_CODE_EXISTS`
  - `BOM_VERSION_EXISTS`
  - `BOM_PARENT_MATERIAL_INVALID`
  - `BOM_CHILD_MATERIAL_INVALID`
  - `BOM_UNIT_INVALID`
  - `BOM_CYCLE_DETECTED`

### 启用

- 方法：`PUT`
- 路径：`/api/admin/boms/{id}/enable`
- 权限：`material:bom:enable`
- 响应：`ApiResponse<BomDetail>`。
- 说明：
  - 启用前再次校验明细、父项物料、子项物料、唯一启用版本和循环引用。
  - 同一父项已有启用版本时返回 `BOM_ENABLED_VERSION_EXISTS`。
- 失败：
  - `BOM_NOT_FOUND`
  - `BOM_ENABLED_VERSION_EXISTS`
  - `BOM_PARENT_MATERIAL_INVALID`
  - `BOM_CHILD_MATERIAL_INVALID`
  - `BOM_UNIT_INVALID`
  - `BOM_EMPTY_ITEMS`
  - `BOM_QUANTITY_INVALID`
  - `BOM_DUPLICATE_ITEM`
  - `BOM_SELF_REFERENCE`
  - `BOM_CYCLE_DETECTED`

### 停用

- 方法：`PUT`
- 路径：`/api/admin/boms/{id}/disable`
- 权限：`material:bom:disable`
- 响应：`ApiResponse<BomDetail>`。
- 说明：一期没有生产工单引用，因此停用只改变状态；后续工单模块接入后需要增加引用校验。
- 失败：`BOM_NOT_FOUND`。

## 请求示例

### BomPayload

```json
{
  "bomCode": "BOM-FG-001",
  "parentMaterialId": 101,
  "versionCode": "V1.0",
  "name": "成品A标准BOM",
  "baseQuantity": 1,
  "baseUnitId": 1,
  "effectiveFrom": "2026-07-03",
  "effectiveTo": null,
  "remark": "首版标准用料",
  "items": [
    {
      "lineNo": 10,
      "childMaterialId": 201,
      "unitId": 2,
      "quantity": 2.5,
      "lossRate": 0.02,
      "remark": "主材"
    },
    {
      "lineNo": 20,
      "childMaterialId": 202,
      "unitId": 1,
      "quantity": 1,
      "lossRate": 0,
      "remark": "包装辅料"
    }
  ]
}
```

## 权限编码

| 权限编码 | 类型 | 路由或接口 | 说明 |
|---|---|---|---|
| `material:bom` | MENU | `/materials/boms` | BOM 管理菜单，归属物料管理 |
| `material:bom:view` | ACTION | `GET /api/admin/boms/**` | 查看 BOM |
| `material:bom:create` | ACTION | `POST /api/admin/boms` | 创建 BOM |
| `material:bom:update` | ACTION | `PUT /api/admin/boms/{id}` | 更新草稿 BOM |
| `material:bom:copy` | ACTION | `POST /api/admin/boms/{id}/copy` | 复制 BOM 版本 |
| `material:bom:enable` | ACTION | `PUT /api/admin/boms/{id}/enable` | 启用 BOM |
| `material:bom:disable` | ACTION | `PUT /api/admin/boms/{id}/disable` | 停用 BOM |

## 错误码

| 错误码 | HTTP 状态 | 说明 |
|---|---|---|
| `BOM_NOT_FOUND` | 404 | BOM 不存在 |
| `BOM_CODE_EXISTS` | 409 | BOM 编码已存在 |
| `BOM_VERSION_EXISTS` | 409 | 同父项物料版本已存在 |
| `BOM_ENABLED_VERSION_EXISTS` | 409 | 同父项物料已存在启用版本 |
| `BOM_STATUS_NOT_EDITABLE` | 409 | 当前状态不可编辑 |
| `BOM_PARENT_MATERIAL_INVALID` | 400 | 父项物料不存在、停用或类型不允许 |
| `BOM_CHILD_MATERIAL_INVALID` | 400 | 子项物料不存在、停用或不允许引用 |
| `BOM_UNIT_INVALID` | 400 | 基准单位或明细单位不存在、停用或不允许引用 |
| `BOM_EMPTY_ITEMS` | 400 | BOM 明细不能为空 |
| `BOM_QUANTITY_INVALID` | 400 | 数量或损耗率不合法 |
| `BOM_DUPLICATE_ITEM` | 409 | 同一 BOM 内子项物料重复 |
| `BOM_SELF_REFERENCE` | 400 | 父项物料不能作为子项 |
| `BOM_CYCLE_DETECTED` | 409 | 检测到循环引用 |

## 联调验收

- 未登录访问返回 `AUTH_UNAUTHORIZED`。
- 无权限访问写接口返回 `AUTH_FORBIDDEN`。
- 列表接口返回 `PageResponse<T>`。
- 详情接口在 ID 不存在时返回 `BOM_NOT_FOUND`。
- 创建、更新、复制、启用和停用写接口必须携带 CSRF token。
- 创建和更新接口校验必填、编码唯一、版本唯一、父子物料状态、数量、损耗率、重复子项和循环引用。
- 创建、更新、复制和启用接口校验基准单位、明细单位存在且启用。
- 启用接口校验同父项唯一启用版本。
- 写接口生成审计日志。
- 前端能够正确展示接口错误信息，不关闭未保存表单。
