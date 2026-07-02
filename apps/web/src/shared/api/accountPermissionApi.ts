type Fetcher = (input: string, init: RequestInit) => Promise<Response>

export type UserStatus = 'ENABLED' | 'DISABLED'
export type RoleStatus = 'ENABLED' | 'DISABLED'
export type PermissionType = 'MENU' | 'BUTTON' | 'API'

export interface ApiEnvelope<T> {
  success: boolean
  code: string
  message: string
  data: T
  details?: unknown[]
  traceId?: string
  timestamp?: string
}

export interface PageResult<T> {
  records?: T[]
  content?: T[]
  items?: T[]
  total: number
  page: number
  pageSize: number
}

export interface CsrfToken {
  token: string
  headerName: string
  parameterName: string
}

export interface UserProfile {
  id: string | number
  username: string
  displayName: string
  status: UserStatus
}

export interface RoleSummary {
  id: string | number
  code: string
  name: string
  status?: RoleStatus
}

export interface MenuNode {
  id: string | number
  code: string
  name: string
  type?: PermissionType
  parentId?: string | number | null
  routePath?: string | null
  sortOrder?: number
  children?: MenuNode[]
}

export interface AuthSession {
  user: UserProfile
  roles?: RoleSummary[]
  menus: MenuNode[]
  permissions: string[]
}

interface CurrentUserResponse extends UserProfile {
  roles?: RoleSummary[]
  menus?: MenuNode[]
  permissions?: string[]
}

export interface LoginCredentials {
  username: string
  password: string
}

export interface LoginResult {
  session: AuthSession
  csrf: CsrfToken
}

export interface UserListQuery {
  keyword?: string
  status?: UserStatus
  page: number
  pageSize: number
}

export interface UserRecord extends UserProfile {
  phone?: string | null
  email?: string | null
  roles?: RoleSummary[]
  lastLoginAt?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface CreateUserPayload {
  username: string
  displayName: string
  phone?: string
  email?: string
  initialPassword: string
  status: UserStatus
  roleIds: Array<string | number>
}

export type UpdateUserPayload = Omit<CreateUserPayload, 'username' | 'initialPassword'>

export interface RoleListQuery {
  keyword?: string
  status?: RoleStatus
  page: number
  pageSize: number
}

export interface RoleRecord extends RoleSummary {
  description?: string | null
  permissionIds?: Array<string | number>
  createdAt?: string
  updatedAt?: string
}

export interface CreateRolePayload {
  code: string
  name: string
  description?: string
  status: RoleStatus
}

export type UpdateRolePayload = Omit<CreateRolePayload, 'code'>

export interface PermissionNode extends MenuNode {
  type: PermissionType
  children?: PermissionNode[]
}

export interface AuditLogQuery {
  operatorKeyword?: string
  targetType?: string
  action?: string
  startAt?: string
  endAt?: string
  page: number
  pageSize: number
}

export interface AuditLogRecord {
  id: string | number
  operatorUsername?: string
  targetType: string
  action: string
  createdAt: string
}

interface AccountPermissionApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export class AccountPermissionApiError extends Error {
  code: string
  status: number
  traceId?: string

  constructor(message: string, code: string, status: number, traceId?: string) {
    super(message)
    this.name = 'AccountPermissionApiError'
    this.code = code
    this.status = status
    this.traceId = traceId
  }
}

export interface AccountPermissionApi {
  getCsrf(): Promise<CsrfToken>
  login(credentials: LoginCredentials): Promise<LoginResult>
  logout(): Promise<void>
  fetchCurrentUser(): Promise<AuthSession>
  users: {
    list(query: UserListQuery): Promise<PageResult<UserRecord>>
    get(id: string | number): Promise<UserRecord>
    create(payload: CreateUserPayload): Promise<UserRecord>
    update(id: string | number, payload: UpdateUserPayload): Promise<UserRecord>
    resetPassword(id: string | number, payload: { newPassword: string }): Promise<void>
    enable(id: string | number): Promise<void>
    disable(id: string | number): Promise<void>
  }
  roles: {
    list(query: RoleListQuery): Promise<PageResult<RoleRecord>>
    get(id: string | number): Promise<RoleRecord>
    create(payload: CreateRolePayload): Promise<RoleRecord>
    update(id: string | number, payload: UpdateRolePayload): Promise<RoleRecord>
    savePermissions(id: string | number, payload: { permissionIds: Array<string | number> }): Promise<void>
    enable(id: string | number): Promise<void>
    disable(id: string | number): Promise<void>
  }
  permissions: {
    tree(): Promise<PermissionNode[]>
  }
  audit: {
    list(query: AuditLogQuery): Promise<PageResult<AuditLogRecord>>
  }
}

export function createAccountPermissionApi(options: AccountPermissionApiOptions = {}): AccountPermissionApi {
  const fetcher = options.fetcher ?? ((input: string, init: RequestInit) => fetch(input, init))
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '')

  const buildUrl = (path: string, query?: object) => {
    const search = new URLSearchParams()
    Object.entries(query ?? {}).forEach(([key, value]: [string, unknown]) => {
      if (value !== undefined && value !== null && value !== '') {
        search.set(key, String(value))
      }
    })
    const queryString = search.toString()
    return `${baseUrl}${path}${queryString ? `?${queryString}` : ''}`
  }

  const request = async <T>(path: string, init: RequestInit): Promise<T> => {
    const response = await fetcher(buildUrl(path), {
      credentials: 'include',
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init.headers ?? {}),
      },
    })
    const envelope = (await response.json()) as ApiEnvelope<T>

    if (!response.ok || !envelope.success) {
      throw new AccountPermissionApiError(
        envelope.message || `请求失败：${response.status}`,
        envelope.code || 'HTTP_ERROR',
        response.status,
        envelope.traceId,
      )
    }

    return envelope.data
  }

  const get = <T>(path: string, query?: object) =>
    request<T>(buildUrl(path, query).replace(baseUrl, ''), { method: 'GET' })

  const write = async <T>(method: 'POST' | 'PUT', path: string, body?: unknown): Promise<T> => {
    const csrf = await api.getCsrf()
    return request<T>(path, {
      body: body === undefined ? undefined : JSON.stringify(body),
      headers: {
        'Content-Type': 'application/json',
        [csrf.headerName]: csrf.token,
      },
      method,
    })
  }

  const writeVoid = async (method: 'POST' | 'PUT', path: string, body?: unknown): Promise<void> => {
    await write<Record<string, never>>(method, path, body)
  }

  const api: AccountPermissionApi = {
    async getCsrf() {
      return request<CsrfToken>('/api/auth/csrf', { method: 'GET' })
    },
    async login(credentials) {
      const csrf = await api.getCsrf()
      const session = await request<AuthSession>('/api/auth/login', {
        body: JSON.stringify(credentials),
        headers: {
          'Content-Type': 'application/json',
          [csrf.headerName]: csrf.token,
        },
        method: 'POST',
      })
      return { session: normalizeSession(session), csrf }
    },
    async logout() {
      await write<Record<string, never>>('POST', '/api/auth/logout')
    },
    async fetchCurrentUser() {
      return normalizeSession(await request<AuthSession | CurrentUserResponse>('/api/auth/me', { method: 'GET' }))
    },
    users: {
      list: (query) => get<PageResult<UserRecord>>('/api/admin/users', query),
      get: (id) => get<UserRecord>(`/api/admin/users/${encodeURIComponent(String(id))}`),
      create: (payload) => write<UserRecord>('POST', '/api/admin/users', payload),
      update: (id, payload) => write<UserRecord>('PUT', `/api/admin/users/${encodeURIComponent(String(id))}`, payload),
      resetPassword: (id, payload) => writeVoid('PUT', `/api/admin/users/${encodeURIComponent(String(id))}/password`, payload),
      enable: (id) => writeVoid('PUT', `/api/admin/users/${encodeURIComponent(String(id))}/enable`),
      disable: (id) => writeVoid('PUT', `/api/admin/users/${encodeURIComponent(String(id))}/disable`),
    },
    roles: {
      list: (query) => get<PageResult<RoleRecord>>('/api/admin/roles', query),
      get: (id) => get<RoleRecord>(`/api/admin/roles/${encodeURIComponent(String(id))}`),
      create: (payload) => write<RoleRecord>('POST', '/api/admin/roles', payload),
      update: (id, payload) => write<RoleRecord>('PUT', `/api/admin/roles/${encodeURIComponent(String(id))}`, payload),
      savePermissions: (id, payload) =>
        writeVoid('PUT', `/api/admin/roles/${encodeURIComponent(String(id))}/permissions`, payload),
      enable: (id) => writeVoid('PUT', `/api/admin/roles/${encodeURIComponent(String(id))}/enable`),
      disable: (id) => writeVoid('PUT', `/api/admin/roles/${encodeURIComponent(String(id))}/disable`),
    },
    permissions: {
      tree: () => get<PermissionNode[]>('/api/admin/permissions/tree'),
    },
    audit: {
      list: (query) => get<PageResult<AuditLogRecord>>('/api/admin/audit-logs', query),
    },
  }

  return api
}

function normalizeSession(response: AuthSession | CurrentUserResponse): AuthSession {
  if ('user' in response) {
    return {
      user: response.user,
      roles: response.roles ?? [],
      menus: response.menus ?? [],
      permissions: response.permissions ?? [],
    }
  }

  return {
    user: {
      id: response.id,
      username: response.username,
      displayName: response.displayName,
      status: response.status,
    },
    roles: response.roles ?? [],
    menus: response.menus ?? [],
    permissions: response.permissions ?? [],
  }
}

export const accountPermissionApi = createAccountPermissionApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
