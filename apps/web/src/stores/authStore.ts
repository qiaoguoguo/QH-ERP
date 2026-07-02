import { defineStore } from 'pinia'
import {
  accountPermissionApi,
  type AuthSession,
  type CsrfToken,
  type LoginCredentials,
  type MenuNode,
  type RoleSummary,
  type UserProfile,
} from '../shared/api/accountPermissionApi'

interface AuthState {
  currentUser: UserProfile | null
  roles: RoleSummary[]
  menus: MenuNode[]
  permissions: string[]
  csrfToken: string | null
  csrfHeaderName: string | null
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    currentUser: null,
    roles: [],
    menus: [],
    permissions: [],
    csrfToken: null,
    csrfHeaderName: null,
  }),
  getters: {
    isAuthenticated: (state) => state.currentUser !== null,
  },
  actions: {
    async login(credentials: LoginCredentials) {
      const result = await accountPermissionApi.login(credentials)
      this.setSession(result.session)
      this.setCsrf(result.csrf)
    },
    async logout() {
      try {
        await accountPermissionApi.logout()
      } finally {
        this.clearSession()
      }
    },
    async fetchCurrentUser() {
      this.setSession(await accountPermissionApi.fetchCurrentUser())
    },
    hasPermission(permission: string) {
      return this.permissions.includes(permission)
    },
    setSession(session: AuthSession) {
      this.currentUser = session.user
      this.roles = session.roles ?? []
      this.menus = session.menus
      this.permissions = session.permissions
    },
    clearSession() {
      this.currentUser = null
      this.roles = []
      this.menus = []
      this.permissions = []
      this.csrfToken = null
      this.csrfHeaderName = null
    },
    setCsrf(csrf: CsrfToken) {
      this.csrfToken = csrf.token
      this.csrfHeaderName = csrf.headerName
    },
  },
})
