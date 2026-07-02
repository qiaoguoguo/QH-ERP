# 账号与权限接口契约

## 目标

定义账号与权限基础模块的接口范围、权限要求、主要字段和错误码，作为前后端联调与测试验收依据。

## 通用约定

- 所有接口遵循 [接口契约基线](api-contract-baseline.md)。
- 非公开接口必须登录。
- 后端接口权限是最终安全边界。
- 列表接口统一使用 `page`、`pageSize` 和分页响应结构。
- 时间字段使用带时区的 ISO 8601 格式。

## 认证接口

### 登录

- 方法：`POST`
- 路径：`/api/auth/login`
- 权限：匿名
- 请求字段：
  - `username`：登录账号。
  - `password`：登录密码。
- 响应 `data`：
  - `user`：当前用户基础信息。
  - `roles`：角色列表。
  - `menus`：菜单树。
  - `permissions`：按钮和操作权限编码列表。
- 失败：
  - `AUTH_UNAUTHORIZED`：账号或密码错误。
  - `AUTH_ACCOUNT_DISABLED`：账号已停用。

### 退出

- 方法：`POST`
- 路径：`/api/auth/logout`
- 权限：登录用户
- 响应 `data`：空对象。

### 当前用户

- 方法：`GET`
- 路径：`/api/auth/me`
- 权限：登录用户
- 响应 `data`：
  - `id`
  - `username`
  - `displayName`
  - `status`
  - `roles`
  - `menus`
  - `permissions`

## 用户接口

### 用户分页

- 方法：`GET`
- 路径：`/api/admin/users`
- 权限：`system:user:view`
- 查询参数：
  - `keyword`
  - `status`
  - `page`
  - `pageSize`
- 响应：分页用户列表。

### 创建用户

- 方法：`POST`
- 路径：`/api/admin/users`
- 权限：`system:user:create`
- 请求字段：
  - `username`
  - `displayName`
  - `phone`
  - `email`
  - `initialPassword`
  - `status`
  - `roleIds`
- 失败：
  - `VALIDATION_ERROR`
  - `AUTH_USERNAME_EXISTS`

### 用户详情

- 方法：`GET`
- 路径：`/api/admin/users/{id}`
- 权限：`system:user:view`

### 更新用户

- 方法：`PUT`
- 路径：`/api/admin/users/{id}`
- 权限：`system:user:update`
- 请求字段：
  - `displayName`
  - `phone`
  - `email`
  - `status`
  - `roleIds`

### 重置密码

- 方法：`PUT`
- 路径：`/api/admin/users/{id}/password`
- 权限：`system:user:reset-password`
- 请求字段：
  - `newPassword`

### 启用用户

- 方法：`PUT`
- 路径：`/api/admin/users/{id}/enable`
- 权限：`system:user:update`

### 停用用户

- 方法：`PUT`
- 路径：`/api/admin/users/{id}/disable`
- 权限：`system:user:update`

## 角色接口

### 角色分页

- 方法：`GET`
- 路径：`/api/admin/roles`
- 权限：`system:role:view`
- 查询参数：
  - `keyword`
  - `status`
  - `page`
  - `pageSize`

### 创建角色

- 方法：`POST`
- 路径：`/api/admin/roles`
- 权限：`system:role:create`
- 请求字段：
  - `code`
  - `name`
  - `description`
  - `status`

### 角色详情

- 方法：`GET`
- 路径：`/api/admin/roles/{id}`
- 权限：`system:role:view`

### 更新角色

- 方法：`PUT`
- 路径：`/api/admin/roles/{id}`
- 权限：`system:role:update`
- 请求字段：
  - `name`
  - `description`
  - `status`

### 保存角色权限

- 方法：`PUT`
- 路径：`/api/admin/roles/{id}/permissions`
- 权限：`system:role:assign-permission`
- 请求字段：
  - `permissionIds`：明确权限点标识集合。
- 说明：
  - 半选状态仅用于前端展示，后端不保存半选。
  - 保存后后端接口鉴权实时按最新权限判断。

### 启用角色

- 方法：`PUT`
- 路径：`/api/admin/roles/{id}/enable`
- 权限：`system:role:update`

### 停用角色

- 方法：`PUT`
- 路径：`/api/admin/roles/{id}/disable`
- 权限：`system:role:update`

## 权限接口

### 权限树

- 方法：`GET`
- 路径：`/api/admin/permissions/tree`
- 权限：`system:permission:view`
- 响应节点字段：
  - `id`
  - `code`
  - `name`
  - `type`
  - `parentId`
  - `routePath`
  - `sortOrder`
  - `children`

## 审计接口

### 审计日志分页

- 方法：`GET`
- 路径：`/api/admin/audit-logs`
- 权限：`system:audit:view`
- 查询参数：
  - `operatorKeyword`
  - `targetType`
  - `action`
  - `startAt`
  - `endAt`
  - `page`
  - `pageSize`

## 权限编码

| 编码 | 含义 |
|---|---|
| `system:user:view` | 查看用户 |
| `system:user:create` | 创建用户 |
| `system:user:update` | 更新、启用、停用用户 |
| `system:user:reset-password` | 重置用户密码 |
| `system:role:view` | 查看角色 |
| `system:role:create` | 创建角色 |
| `system:role:update` | 更新、启用、停用角色 |
| `system:role:assign-permission` | 分配角色权限 |
| `system:permission:view` | 查看权限树 |
| `system:audit:view` | 查看账号权限审计 |

## 模块错误码

| 错误码 | 含义 |
|---|---|
| `AUTH_ACCOUNT_DISABLED` | 账号已停用 |
| `AUTH_USERNAME_EXISTS` | 用户名已存在 |
| `AUTH_ROLE_CODE_EXISTS` | 角色编码已存在 |
| `AUTH_INVALID_PASSWORD_RULE` | 密码不符合规则 |
| `AUTH_ROLE_DISABLED` | 角色已停用 |
| `AUTH_PERMISSION_NOT_FOUND` | 权限点不存在 |
| `AUTH_INIT_ADMIN_FAILED` | 初始管理员初始化失败 |

## 联调验收

- 未登录访问用户、角色、权限和审计接口返回认证错误。
- 无权限访问接口返回权限错误。
- 停用账号访问受保护接口返回账号停用错误。
- 用户和角色列表返回分页结构。
- 角色权限保存后，后端接口鉴权按最新授权判断。
