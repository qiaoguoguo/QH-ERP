# Account Permission Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first business module for QH ERP: internal login, users, roles, permissions, backend authorization, frontend permission UI, and browser-verifiable local deployment.

**Architecture:** The backend owns authentication, authorization, audit, and data integrity using Spring Boot, Spring Security, JPA, Flyway, PostgreSQL, and Testcontainers. The frontend consumes the backend contract using Vue, Pinia, Vue Router, Element Plus, and Vitest; frontend permission checks are display controls only.

**Tech Stack:** Vue 3, TypeScript, Vite, Pinia, Vue Router, Element Plus, Vitest, JDK 21, Spring Boot 4.0.7, Spring Security, Spring Data JPA, Flyway, PostgreSQL, Testcontainers.

---

## File Structure

### Backend Files

- `apps/api/src/main/resources/db/migration/V2__account_permission_schema.sql` creates account, role, permission, relation, session-related audit tables and indexes.
- `apps/api/src/main/java/com/qherp/api/common/ApiResponse.java` defines the unified response shape.
- `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java` defines stable error codes.
- `apps/api/src/main/java/com/qherp/api/common/GlobalExceptionHandler.java` maps validation, authentication, authorization, conflict, and system errors.
- `apps/api/src/main/java/com/qherp/api/security/SecurityConfiguration.java` evolves from health-only security to Session/Cookie authentication and route permission enforcement.
- `apps/api/src/main/java/com/qherp/api/security/CurrentUser.java` represents the authenticated user snapshot used by controllers and permission checks.
- `apps/api/src/main/java/com/qherp/api/security/CurrentUserService.java` loads user, role, and permission state from the database.
- `apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java` checks request permission codes.
- `apps/api/src/main/java/com/qherp/api/auth/AuthController.java` exposes login, logout, and current-user endpoints.
- `apps/api/src/main/java/com/qherp/api/auth/AuthService.java` validates credentials, account status, session state, and login response.
- `apps/api/src/main/java/com/qherp/api/system/user/*` owns user entity, repository, service, DTOs, and controller.
- `apps/api/src/main/java/com/qherp/api/system/role/*` owns role entity, repository, service, DTOs, and controller.
- `apps/api/src/main/java/com/qherp/api/system/permission/*` owns permission entity, repository, tree DTOs, and controller.
- `apps/api/src/main/java/com/qherp/api/system/audit/*` owns audit entity, repository, service, and query endpoint.
- `apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java` creates initial admin, roles, menus, buttons, and API permissions idempotently.
- `apps/api/src/test/java/com/qherp/api/auth/AuthControllerTests.java` covers login, logout, current user, account disabled, and unauthorized responses.
- `apps/api/src/test/java/com/qherp/api/system/user/UserAdminControllerTests.java` covers user CRUD, reset password, role assignment, and status.
- `apps/api/src/test/java/com/qherp/api/system/role/RoleAdminControllerTests.java` covers role CRUD and permission assignment.
- `apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java` covers backend authorization and disabled account behavior.
- `apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java` covers Flyway + seed data idempotency.

### Frontend Files

- `apps/web/src/modules/auth/LoginView.vue` implements login.
- `apps/web/src/modules/system/users/UserListView.vue` implements user management.
- `apps/web/src/modules/system/roles/RoleListView.vue` implements role management.
- `apps/web/src/modules/system/roles/RolePermissionView.vue` implements permission tree assignment.
- `apps/web/src/modules/system/shared/UserRoleDialog.vue` implements user-role assignment.
- `apps/web/src/modules/system/shared/PermissionTree.vue` implements tree display and selection.
- `apps/web/src/shared/api/accountPermissionApi.ts` calls auth, users, roles, permissions, audit endpoints.
- `apps/web/src/stores/authStore.ts` stores current user, menus, and permission codes.
- `apps/web/src/router/index.ts` adds auth routes, protected admin routes, and permission metadata.
- `apps/web/src/App.vue` connects backend-provided menus to the shell.
- `apps/web/src/modules/auth/LoginView.spec.ts` tests login behavior.
- `apps/web/src/stores/authStore.spec.ts` tests login state and permission clearing.
- `apps/web/src/router/permissionGuard.spec.ts` tests route guard outcomes.
- `apps/web/src/modules/system/users/UserListView.spec.ts` tests user list states and permissions.
- `apps/web/src/modules/system/roles/RoleListView.spec.ts` tests role list and permission entry.

### Documentation Files

- `docs/tasks/004-account-permission-foundation.md` records implementation and verification.
- `docs/product/account-permission-requirements.md` updates any implementation-driven clarifications.
- `docs/api/account-permission-api.md` updates exact field names after implementation.
- `docs/testing/account-permission-test-plan.md` records executed verification and browser path.
- `docs/ops/local-development.md` adds account module startup and test-account instructions.

---

### Task 1: Backend Schema And Seed Test

**Files:**
- Create: `apps/api/src/test/java/com/qherp/api/system/init/AccountPermissionInitializerTests.java`
- Create: `apps/api/src/main/resources/db/migration/V2__account_permission_schema.sql`
- Create: `apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- Create: backend entity and repository files under `apps/api/src/main/java/com/qherp/api/system`

- [ ] **Step 1: Write failing seed-data integration test**

Create `AccountPermissionInitializerTests` that starts the Spring context with PostgreSQL Testcontainers and asserts initial admin, administrator role, and base permissions exist exactly once after restart.

```java
@Test
void initializesAdminRoleAndPermissionsIdempotently() {
    assertThat(userRepository.findByUsername("admin")).isPresent();
    assertThat(roleRepository.findByCode("SYSTEM_ADMIN")).isPresent();
    assertThat(permissionRepository.findByCode("system:user:view")).isPresent();

    initializer.run();

    assertThat(userRepository.countByUsername("admin")).isEqualTo(1);
    assertThat(roleRepository.countByCode("SYSTEM_ADMIN")).isEqualTo(1);
    assertThat(permissionRepository.countByCode("system:user:view")).isEqualTo(1);
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```powershell
$api=(Resolve-Path 'apps/api').Path.Replace('\','/')
docker run --rm -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -v "${api}:/workspace" -v qherp-maven-repo:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -q -Dtest=AccountPermissionInitializerTests test
```

Expected: compilation failure because repositories, entities, and initializer do not exist.

- [ ] **Step 3: Implement schema and seed support**

Create tables:

```sql
CREATE TABLE sys_user (
  id UUID PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(50) NOT NULL,
  phone VARCHAR(30),
  email VARCHAR(120),
  status VARCHAR(20) NOT NULL,
  last_login_at TIMESTAMPTZ,
  password_changed_at TIMESTAMPTZ,
  created_by UUID,
  created_at TIMESTAMPTZ NOT NULL,
  updated_by UUID,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL
);
```

Create matching `sys_role`, `sys_permission`, `sys_user_role`, `sys_role_permission`, and `sys_audit_log` tables with unique constraints for role code, permission code, user-role pair, and role-permission pair.

Create minimal JPA entities and repositories with `findByUsername`, `countByUsername`, `findByCode`, and `countByCode`.

Create `AccountPermissionInitializer` using `ApplicationRunner`, `PasswordEncoder`, and repositories. Seed:

- user `admin`
- role `SYSTEM_ADMIN`
- permissions from `docs/api/account-permission-api.md`
- all permissions assigned to `SYSTEM_ADMIN`
- `admin` assigned to `SYSTEM_ADMIN`

- [ ] **Step 4: Run seed test to verify GREEN**

Run the same `AccountPermissionInitializerTests` command.

Expected: test passes; Flyway applies V1 and V2; seed data is idempotent.

- [ ] **Step 5: Commit**

```powershell
git add apps/api/src/main/resources/db/migration apps/api/src/main/java/com/qherp/api/system apps/api/src/test/java/com/qherp/api/system/init
git commit -m "实现账号权限基础数据模型"
```

---

### Task 2: Unified API Response And Error Handling

**Files:**
- Create: `apps/api/src/main/java/com/qherp/api/common/ApiResponse.java`
- Create: `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- Create: `apps/api/src/main/java/com/qherp/api/common/BusinessException.java`
- Create: `apps/api/src/main/java/com/qherp/api/common/GlobalExceptionHandler.java`
- Create: `apps/api/src/test/java/com/qherp/api/common/ApiResponseTests.java`

- [ ] **Step 1: Write failing response test**

```java
@Test
void successResponseContainsStableEnvelopeFields() {
    ApiResponse<Map<String, String>> response = ApiResponse.ok(Map.of("status", "UP"));

    assertThat(response.success()).isTrue();
    assertThat(response.code()).isEqualTo("OK");
    assertThat(response.data()).containsEntry("status", "UP");
    assertThat(response.timestamp()).isNotNull();
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```powershell
docker run --rm -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -v "${api}:/workspace" -v qherp-maven-repo:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -q -Dtest=ApiResponseTests test
```

Expected: compilation failure because `ApiResponse` does not exist.

- [ ] **Step 3: Implement response envelope**

Implement immutable records:

```java
public record ApiResponse<T>(
    boolean success,
    String code,
    String message,
    T data,
    List<ApiErrorDetail> details,
    String traceId,
    OffsetDateTime timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", "成功", data, List.of(), null, OffsetDateTime.now());
    }
}
```

Add `BusinessException` and `GlobalExceptionHandler` for validation, authentication, authorization, business conflict, and system errors.

- [ ] **Step 4: Run response tests**

Expected: `ApiResponseTests` passes.

- [ ] **Step 5: Commit**

```powershell
git add apps/api/src/main/java/com/qherp/api/common apps/api/src/test/java/com/qherp/api/common
git commit -m "统一后端接口响应结构"
```

---

### Task 3: Authentication And Session

**Files:**
- Create: `apps/api/src/test/java/com/qherp/api/auth/AuthControllerTests.java`
- Create: `apps/api/src/main/java/com/qherp/api/auth/AuthController.java`
- Create: `apps/api/src/main/java/com/qherp/api/auth/AuthService.java`
- Modify: `apps/api/src/main/java/com/qherp/api/security/SecurityConfiguration.java`
- Create: `apps/api/src/main/java/com/qherp/api/security/CurrentUser.java`
- Create: `apps/api/src/main/java/com/qherp/api/security/CurrentUserService.java`

- [ ] **Step 1: Write failing login test**

```java
@Test
void loginReturnsCurrentUserAndSessionCookie() {
    ResponseEntity<String> response = restTemplate.postForEntity(
        "/api/auth/login",
        Map.of("username", "admin", "password", "Qherp@2026!"),
        String.class
    );

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNotEmpty();
    assertThat(response.getBody()).contains("\"username\":\"admin\"");
}
```

- [ ] **Step 2: Run test to verify RED**

Expected: 404 or authentication failure because auth endpoints are not implemented.

- [ ] **Step 3: Implement login, logout, and current user**

Implement:

- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`

Use `PasswordEncoder`, `AuthenticationManager` or explicit credential validation inside `AuthService`, `HttpSession` for server-side session, and HttpOnly session cookie from Spring Security.

- [ ] **Step 4: Add disabled account test**

```java
@Test
void disabledUserCannotLogin() {
    disableUser("disabled-user");

    ResponseEntity<String> response = restTemplate.postForEntity(
        "/api/auth/login",
        Map.of("username", "disabled-user", "password", "Qherp@2026!"),
        String.class
    );

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody()).contains("AUTH_ACCOUNT_DISABLED");
}
```

- [ ] **Step 5: Run auth tests**

Expected: login, logout, current-user, and disabled-account tests pass.

- [ ] **Step 6: Commit**

```powershell
git add apps/api/src/main/java/com/qherp/api/auth apps/api/src/main/java/com/qherp/api/security apps/api/src/test/java/com/qherp/api/auth
git commit -m "实现账号登录与会话管理"
```

---

### Task 4: Backend User Role Permission APIs

**Files:**
- Create tests under:
  - `apps/api/src/test/java/com/qherp/api/system/user/UserAdminControllerTests.java`
  - `apps/api/src/test/java/com/qherp/api/system/role/RoleAdminControllerTests.java`
  - `apps/api/src/test/java/com/qherp/api/system/permission/PermissionAuthorizationTests.java`
- Create controllers and services under:
  - `apps/api/src/main/java/com/qherp/api/system/user`
  - `apps/api/src/main/java/com/qherp/api/system/role`
  - `apps/api/src/main/java/com/qherp/api/system/permission`
  - `apps/api/src/main/java/com/qherp/api/system/audit`

- [ ] **Step 1: Write failing user-admin tests**

Cover:

- admin can create user
- duplicate username returns `AUTH_USERNAME_EXISTS`
- admin can disable user
- non-authorized user cannot create user

- [ ] **Step 2: Implement user APIs**

Implement endpoints from `docs/api/account-permission-api.md`:

- `GET /api/admin/users`
- `POST /api/admin/users`
- `GET /api/admin/users/{id}`
- `PUT /api/admin/users/{id}`
- `PUT /api/admin/users/{id}/password`
- `PUT /api/admin/users/{id}/enable`
- `PUT /api/admin/users/{id}/disable`

- [ ] **Step 3: Write failing role and permission tests**

Cover:

- admin can create role
- duplicate role code returns `AUTH_ROLE_CODE_EXISTS`
- admin can save role permissions
- permission tree returns menu and button nodes

- [ ] **Step 4: Implement role and permission APIs**

Implement:

- `GET /api/admin/roles`
- `POST /api/admin/roles`
- `GET /api/admin/roles/{id}`
- `PUT /api/admin/roles/{id}`
- `PUT /api/admin/roles/{id}/permissions`
- `PUT /api/admin/roles/{id}/enable`
- `PUT /api/admin/roles/{id}/disable`
- `GET /api/admin/permissions/tree`

- [ ] **Step 5: Implement authorization manager**

Map request method and path to permission code. Validate:

- authenticated user exists
- user status is enabled
- assigned roles are enabled
- permission code is granted

- [ ] **Step 6: Run backend module tests**

Expected: all account permission backend tests pass.

- [ ] **Step 7: Commit**

```powershell
git add apps/api/src/main/java/com/qherp/api/system apps/api/src/test/java/com/qherp/api/system
git commit -m "实现用户角色权限接口"
```

---

### Task 5: Frontend Auth Store And Route Guard

**Files:**
- Create: `apps/web/src/stores/authStore.ts`
- Create: `apps/web/src/stores/authStore.spec.ts`
- Create: `apps/web/src/shared/api/accountPermissionApi.ts`
- Modify: `apps/web/src/router/index.ts`
- Create: `apps/web/src/router/permissionGuard.spec.ts`

- [ ] **Step 1: Write failing auth store tests**

```ts
it('clears current user and permissions on logout', async () => {
  const store = useAuthStore()
  store.setSession({
    user: { id: '1', username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions: ['system:user:view'],
  })

  store.clearSession()

  expect(store.currentUser).toBeNull()
  expect(store.permissions).toEqual([])
})
```

- [ ] **Step 2: Implement auth store and API client methods**

Implement:

- `login`
- `logout`
- `fetchCurrentUser`
- `hasPermission`
- `setSession`
- `clearSession`

- [ ] **Step 3: Write failing route guard tests**

Cover:

- unauthenticated protected route redirects to login
- authenticated route without permission goes to forbidden
- authenticated route with permission resolves

- [ ] **Step 4: Implement route guard**

Update `router/index.ts` with auth route, forbidden route, and protected route metadata.

- [ ] **Step 5: Run frontend tests**

```powershell
cd apps/web
npm test
npm run typecheck
```

- [ ] **Step 6: Commit**

```powershell
git add apps/web/src/stores apps/web/src/shared/api apps/web/src/router
git commit -m "实现前端登录状态与路由守卫"
```

---

### Task 6: Frontend Pages

**Files:**
- Create: `apps/web/src/modules/auth/LoginView.vue`
- Create: `apps/web/src/modules/auth/LoginView.spec.ts`
- Create: `apps/web/src/modules/system/users/UserListView.vue`
- Create: `apps/web/src/modules/system/users/UserListView.spec.ts`
- Create: `apps/web/src/modules/system/roles/RoleListView.vue`
- Create: `apps/web/src/modules/system/roles/RoleListView.spec.ts`
- Create: `apps/web/src/modules/system/roles/RolePermissionView.vue`
- Create: shared permission UI components under `apps/web/src/modules/system/shared`
- Modify: `apps/web/src/App.vue`

- [ ] **Step 1: Write failing login page test**

Cover empty validation, failed login message, loading disabled state, and successful login calling the store.

- [ ] **Step 2: Implement login page**

Use Element Plus form controls and existing API/store.

- [ ] **Step 3: Write failing user list tests**

Cover loading, empty state, pagination, query reset, permission-hidden create button, disabled-user status tag.

- [ ] **Step 4: Implement user list**

Implement query area, table, status tags, create/edit dialog, reset password, enable/disable confirmation, and role assignment dialog.

- [ ] **Step 5: Write failing role list and permission tree tests**

Cover role pagination, role form validation, permission configuration entry, permission tree checked state, save success and failure.

- [ ] **Step 6: Implement role pages**

Implement role list, role form dialog, permission tree page/dialog, save/cancel flows, and unsaved-change confirmation.

- [ ] **Step 7: Run frontend verification**

```powershell
cd apps/web
npm test
npm run typecheck
npm run build
```

- [ ] **Step 8: Commit**

```powershell
git add apps/web/src/modules apps/web/src/App.vue
git commit -m "实现账号权限前端页面"
```

---

### Task 7: Local Deployment And Browser Acceptance

**Files:**
- Modify: `docs/testing/account-permission-test-plan.md`
- Modify: `docs/tasks/004-account-permission-foundation.md`
- Modify: `docs/ops/local-development.md`

- [ ] **Step 1: Run full automated verification**

```powershell
cd apps/web
npm test
npm run typecheck
npm run build
```

```powershell
$api=(Resolve-Path 'apps/api').Path.Replace('\','/')
docker run --rm -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -v "${api}:/workspace" -v qherp-maven-repo:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -q test
```

- [ ] **Step 2: Start local environment**

```powershell
docker compose up -d postgres
```

Start backend and frontend using documented commands.

- [ ] **Step 3: Browser acceptance**

Verify in browser:

- initial admin login
- create role
- create user
- assign role
- permission-limited account sees limited menu/buttons
- unauthorized API call is rejected
- disabled account cannot login
- audit entry exists for account/role changes

- [ ] **Step 4: Update task verification records**

Record commands, exit codes, browser URLs, and known issues in `docs/tasks/004-account-permission-foundation.md`.

- [ ] **Step 5: Commit**

```powershell
git add docs/testing/account-permission-test-plan.md docs/tasks/004-account-permission-foundation.md docs/ops/local-development.md
git commit -m "记录账号权限模块验收结果"
```

---

## Final Verification

Before merging or notifying the user:

```powershell
git status --short --branch
cd apps/web
npm test
npm run typecheck
npm run build
```

```powershell
$api=(Resolve-Path 'apps/api').Path.Replace('\','/')
docker run --rm -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -v "${api}:/workspace" -v qherp-maven-repo:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -q test
```

Then start local services and complete browser acceptance. Do not merge to main or notify user for stage acceptance until these checks pass and no blocking defects remain.

## Self-Review

- Spec coverage: tasks cover backend data model, authentication, authorization, frontend auth state, pages, deployment, and browser acceptance.
- Placeholder scan: no unresolved placeholders are intentionally left in the plan.
- Type consistency: permission codes, route names, and entity names match the requirements and API documents.
