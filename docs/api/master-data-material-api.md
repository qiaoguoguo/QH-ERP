# 基础资料与物料管理接口契约

## 目标

定义基础资料与物料管理阶段的接口范围、字段、权限、错误码和联调验收规则，作为前后端实现与测试依据。

## 通用规则

- 所有接口使用统一 `ApiResponse<T>` 包装。
- 分页接口返回 `PageResponse<T>`。
- 时间字段使用后端生成的 ISO 8601 字符串。
- 写接口需要 CSRF token。
- 认证和权限沿用账号权限模块。
- 后端接口鉴权是最终安全边界，前端菜单和按钮权限只作为体验控制。
- 列表接口默认支持 `page`、`pageSize`、`keyword`、`status` 查询参数。
- 写操作成功后必须生成审计日志，至少记录操作人、动作、目标类型、目标标识和操作时间。

## 通用基础资料字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | number | 是 | 后端主键 |
| code | string | 是 | 同类主数据内唯一 |
| name | string | 是 | 名称 |
| status | string | 是 | `ENABLED` 或 `DISABLED` |
| remark | string | 否 | 备注 |
| createdAt | string | 是 | 创建时间 |
| updatedAt | string | 是 | 更新时间 |

## 扩展字段

### 计量单位

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| precisionScale | number | 是 | 数量精度，非负整数 |
| sortOrder | number | 是 | 排序值 |

### 仓库

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| warehouseType | string | 否 | 仓库类型 |
| managerName | string | 否 | 负责人 |
| address | string | 否 | 地址 |

### 供应商与客户

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| contactName | string | 否 | 联系人 |
| contactPhone | string | 否 | 联系电话 |

### 物料分类

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| parentId | number | 否 | 上级分类标识，顶级分类为空 |
| sortOrder | number | 是 | 排序值 |

### 物料档案

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| specification | string | 否 | 规格型号 |
| materialType | string | 是 | `RAW_MATERIAL`、`SEMI_FINISHED`、`FINISHED_GOOD`、`AUXILIARY` |
| sourceType | string | 是 | `PURCHASED`、`SELF_MADE`、`OUTSOURCED` |
| categoryId | number | 是 | 已启用物料分类标识 |
| categoryName | string | 是 | 物料分类名称，响应字段 |
| unitId | number | 是 | 已启用计量单位标识 |
| unitName | string | 是 | 计量单位名称，响应字段 |

## 接口分组

| 资源 | 路径 | 能力 |
|---|---|---|
| 计量单位 | `/api/admin/master/units` | list/get/create/update/enable/disable |
| 仓库 | `/api/admin/master/warehouses` | list/get/create/update/enable/disable |
| 供应商 | `/api/admin/master/suppliers` | list/get/create/update/enable/disable |
| 客户 | `/api/admin/master/customers` | list/get/create/update/enable/disable |
| 物料分类 | `/api/admin/master/material-categories` | list/get/create/update/enable/disable |
| 物料档案 | `/api/admin/master/materials` | list/get/create/update/enable/disable |

## 通用基础资料接口

适用于计量单位、仓库、供应商、客户。不同资源按各自扩展字段补充请求和响应。

### 分页列表

- 方法：`GET`
- 路径：`/api/admin/master/{resource}`
- 权限：对应资源 `view` 权限
- 查询参数：
  - `keyword`：按编码或名称模糊查询。
  - `status`：`ENABLED` 或 `DISABLED`。
  - `page`：页码。
  - `pageSize`：每页数量。
- 响应：`ApiResponse<PageResponse<ResourceSummary>>`。

### 详情

- 方法：`GET`
- 路径：`/api/admin/master/{resource}/{id}`
- 权限：对应资源 `view` 权限
- 响应：`ApiResponse<ResourceDetail>`。

### 创建

- 方法：`POST`
- 路径：`/api/admin/master/{resource}`
- 权限：对应资源 `create` 权限
- 请求：通用基础资料字段中的 `code`、`name`、`status`、`remark` 以及资源扩展字段。
- 响应：`ApiResponse<ResourceDetail>`。
- 失败：`VALIDATION_ERROR`、`MASTER_DATA_CODE_EXISTS`、`MASTER_DATA_INVALID_STATUS`。

### 更新

- 方法：`PUT`
- 路径：`/api/admin/master/{resource}/{id}`
- 权限：对应资源 `update` 权限
- 请求：通用基础资料字段中的 `code`、`name`、`status`、`remark` 以及资源扩展字段。
- 响应：`ApiResponse<ResourceDetail>`。
- 失败：`VALIDATION_ERROR`、`MASTER_DATA_NOT_FOUND`、`MASTER_DATA_CODE_EXISTS`、`MASTER_DATA_INVALID_STATUS`。

### 启用

- 方法：`PUT`
- 路径：`/api/admin/master/{resource}/{id}/enable`
- 权限：对应资源 `update` 权限
- 响应：`ApiResponse<ResourceDetail>`。
- 失败：`MASTER_DATA_NOT_FOUND`。

### 停用

- 方法：`PUT`
- 路径：`/api/admin/master/{resource}/{id}/disable`
- 权限：对应资源 `update` 权限
- 响应：`ApiResponse<ResourceDetail>`。
- 失败：`MASTER_DATA_NOT_FOUND`。

## 物料分类接口

### 分类列表

- 方法：`GET`
- 路径：`/api/admin/master/material-categories`
- 权限：`master:material-category:view`
- 查询参数：
  - `keyword`
  - `status`
  - `page`
  - `pageSize`
- 响应：`ApiResponse<PageResponse<MaterialCategorySummary>>`。
- 说明：后端返回平铺列表和 `parentId`，前端负责树形展示。

### 分类详情

- 方法：`GET`
- 路径：`/api/admin/master/material-categories/{id}`
- 权限：`master:material-category:view`

### 创建分类

- 方法：`POST`
- 路径：`/api/admin/master/material-categories`
- 权限：`master:material-category:create`
- 请求字段：
  - `code`
  - `name`
  - `parentId`
  - `status`
  - `sortOrder`
  - `remark`
- 失败：`VALIDATION_ERROR`、`MASTER_DATA_CODE_EXISTS`、`MASTER_DATA_NOT_FOUND`、`MASTER_DATA_INVALID_STATUS`。

### 更新分类

- 方法：`PUT`
- 路径：`/api/admin/master/material-categories/{id}`
- 权限：`master:material-category:update`
- 请求字段同创建分类。
- 失败：`VALIDATION_ERROR`、`MASTER_DATA_NOT_FOUND`、`MASTER_DATA_CODE_EXISTS`、`MASTER_DATA_INVALID_STATUS`。

### 启用分类

- 方法：`PUT`
- 路径：`/api/admin/master/material-categories/{id}/enable`
- 权限：`master:material-category:update`

### 停用分类

- 方法：`PUT`
- 路径：`/api/admin/master/material-categories/{id}/disable`
- 权限：`master:material-category:update`
- 失败：`MASTER_DATA_NOT_FOUND`、`MASTER_DATA_CATEGORY_IN_USE`。

## 物料档案接口

### 物料列表

- 方法：`GET`
- 路径：`/api/admin/master/materials`
- 权限：`master:material:view`
- 查询参数：
  - `keyword`
  - `status`
  - `categoryId`
  - `materialType`
  - `sourceType`
  - `page`
  - `pageSize`
- 响应：`ApiResponse<PageResponse<MaterialSummary>>`。

### 物料详情

- 方法：`GET`
- 路径：`/api/admin/master/materials/{id}`
- 权限：`master:material:view`
- 响应：`ApiResponse<MaterialDetail>`。

### 创建物料

- 方法：`POST`
- 路径：`/api/admin/master/materials`
- 权限：`master:material:create`
- 请求字段：
  - `code`
  - `name`
  - `specification`
  - `materialType`
  - `sourceType`
  - `categoryId`
  - `unitId`
  - `status`
  - `remark`
- 失败：`VALIDATION_ERROR`、`MASTER_DATA_CODE_EXISTS`、`MASTER_DATA_INVALID_STATUS`、`MASTER_DATA_REFERENCE_INVALID`。

### 更新物料

- 方法：`PUT`
- 路径：`/api/admin/master/materials/{id}`
- 权限：`master:material:update`
- 请求字段同创建物料。
- 失败：`VALIDATION_ERROR`、`MASTER_DATA_NOT_FOUND`、`MASTER_DATA_CODE_EXISTS`、`MASTER_DATA_INVALID_STATUS`、`MASTER_DATA_REFERENCE_INVALID`。

### 启用物料

- 方法：`PUT`
- 路径：`/api/admin/master/materials/{id}/enable`
- 权限：`master:material:update`

### 停用物料

- 方法：`PUT`
- 路径：`/api/admin/master/materials/{id}/disable`
- 权限：`master:material:update`

## 权限编码

| 编码 | 含义 |
|---|---|
| `master:unit:view` | 查看计量单位 |
| `master:unit:create` | 创建计量单位 |
| `master:unit:update` | 更新、启用、停用计量单位 |
| `master:warehouse:view` | 查看仓库 |
| `master:warehouse:create` | 创建仓库 |
| `master:warehouse:update` | 更新、启用、停用仓库 |
| `master:supplier:view` | 查看供应商 |
| `master:supplier:create` | 创建供应商 |
| `master:supplier:update` | 更新、启用、停用供应商 |
| `master:customer:view` | 查看客户 |
| `master:customer:create` | 创建客户 |
| `master:customer:update` | 更新、启用、停用客户 |
| `master:material-category:view` | 查看物料分类 |
| `master:material-category:create` | 创建物料分类 |
| `master:material-category:update` | 更新、启用、停用物料分类 |
| `master:material:view` | 查看物料档案 |
| `master:material:create` | 创建物料档案 |
| `master:material:update` | 更新、启用、停用物料档案 |

## 错误码

| 错误码 | HTTP | 场景 |
|---|---:|---|
| MASTER_DATA_CODE_EXISTS | 409 | 同类编码重复 |
| MASTER_DATA_NOT_FOUND | 404 | 主数据不存在 |
| MASTER_DATA_INVALID_STATUS | 400 | 状态非法 |
| MASTER_DATA_REFERENCE_INVALID | 400 | 物料引用不存在或停用的单位/分类 |
| MASTER_DATA_CATEGORY_IN_USE | 409 | 分类存在启用子项或启用物料，不能停用 |

## 联调验收

- 管理员可通过六组接口完成列表、详情、新增、编辑、启用、停用。
- 只读角色调用列表和详情成功，直接调用写接口返回 `AUTH_FORBIDDEN`。
- 创建或更新物料时，引用不存在或停用的单位、分类返回 `MASTER_DATA_REFERENCE_INVALID`。
- 重复编码返回 `MASTER_DATA_CODE_EXISTS`。
- 停用存在启用子分类或启用物料的分类返回 `MASTER_DATA_CATEGORY_IN_USE`。
- 写操作可在审计日志中查询到对应记录。
