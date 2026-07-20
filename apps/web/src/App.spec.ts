import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App.vue'
import PayableStatusTag from './modules/finance/PayableStatusTag.vue'
import PaymentStatusTag from './modules/finance/PaymentStatusTag.vue'
import ReceivableStatusTag from './modules/finance/ReceivableStatusTag.vue'
import ReceiptStatusTag from './modules/finance/ReceiptStatusTag.vue'
import { financePermissions, formatFinanceAmount, financeSourceTypeText } from './modules/finance/financePageHelpers'
import { reportPermissions } from './modules/reports/reportPageHelpers'
import productionMenuSource from './navigation/productionMenu.ts?raw'
import planningMenuSource from './navigation/planningMenu.ts?raw'
import costMenuSource from './navigation/costMenu.ts?raw'
import periodCloseMenuSource from './navigation/periodCloseMenu.ts?raw'
import glMenuSource from './navigation/glMenu.ts?raw'
import appMenuRegistrySource from './navigation/appMenuRegistry.ts?raw'
import appSource from './App.vue?raw'
import { createQhErpRouter } from './router'
import type { AuthSession, CsrfToken } from './shared/api/accountPermissionApi'
import { useAuthStore } from './stores/authStore'

const adminLoginSession: AuthSession = {
  user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
  menus: [],
  permissions: [],
  roles: [{ id: 1, code: 'SYSTEM_ADMIN', name: '系统管理员', status: 'ENABLED' }],
}

const csrfToken: CsrfToken = {
  token: 'csrf-token',
  headerName: 'X-CSRF-TOKEN',
  parameterName: '_csrf',
}

function apiResponse<T>(data: T) {
  return {
    ok: true,
    status: 200,
    json: async () => ({
      success: true,
      code: 'OK',
      message: '成功',
      data,
    }),
  } as Response
}

function apiFailure() {
  return {
    ok: false,
    status: 401,
    json: async () => ({
      success: false,
      code: 'AUTH_UNAUTHORIZED',
      message: '未登录或会话已失效',
      data: null,
    }),
  } as Response
}

describe('ERP 应用骨架', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('展示制造业 ERP 后台框架和当前用户菜单入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
      menus: [
        {
          id: 1,
          code: 'system',
          name: '系统管理',
          routePath: '/system',
          children: [
            { id: 2, code: 'system:user', name: '用户管理', routePath: '/system/users' },
            { id: 3, code: 'system:role', name: '角色管理', routePath: '/system/roles' },
            { id: 8, code: 'system:business-period:view', name: '业务期间', routePath: '/system/business-periods' },
          ],
        },
        {
          id: 4,
          code: 'inventory',
          name: '库存管理',
          routePath: '/inventory/balances',
          children: [
            { id: 5, code: 'inventory:balance:view', name: '库存余额', routePath: '/inventory/balances' },
            { id: 6, code: 'inventory:movement:view', name: '库存变动', routePath: '/inventory/movements' },
            { id: 7, code: 'inventory:document:view', name: '库存单据', routePath: '/inventory/documents' },
          ],
        },
      ],
      permissions: [],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('QH ERP')
    expect(wrapper.text()).toContain('制造业生产管理 ERP')
    expect(wrapper.text()).toContain('工作台')
    expect(wrapper.text()).toContain('管理员')
    expect(wrapper.text()).toContain('用户管理')
    expect(wrapper.text()).toContain('角色管理')
    expect(wrapper.text()).toContain('业务期间')
    expect(wrapper.text()).toContain('库存管理')
    expect(wrapper.text()).toContain('库存余额与价值')
    expect(wrapper.text()).toContain('库存流水与价值')
    expect(wrapper.text()).toContain('库存单据')
    expect(wrapper.find('[data-test="app-logo"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="app-logo"]').attributes('src')).toContain('logo.ico')
    expect(wrapper.find('[data-test="main-menu-icon-home"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="main-menu-icon-system"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="main-menu-icon-inventory"]').exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'ElMenu' }).props('uniqueOpened')).toBe(true)
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/inventory')
  })

  it('支持收起和展开左侧菜单', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
      menus: [
        {
          id: 4,
          code: 'inventory',
          name: '库存管理',
          routePath: '/inventory/balances',
          children: [
            { id: 5, code: 'inventory:balance:view', name: '库存余额', routePath: '/inventory/balances' },
          ],
        },
      ],
      permissions: [],
    })
    const router = createQhErpRouter()
    router.push('/finance/payables')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    const menu = () => wrapper.findComponent({ name: 'ElMenu' })
    const sidebar = () => wrapper.find('.app-sidebar')
    const toggle = () => wrapper.find('[data-test="sidebar-toggle-button"]')

    expect(toggle().exists()).toBe(true)
    expect(menu().props('collapse')).toBe(false)
    expect(sidebar().classes()).not.toContain('is-collapsed')

    await toggle().trigger('click')

    expect(menu().props('collapse')).toBe(true)
    expect(sidebar().classes()).toContain('is-collapsed')
    expect(toggle().attributes('aria-label')).toBe('展开菜单')

    await toggle().trigger('click')

    expect(menu().props('collapse')).toBe(false)
    expect(sidebar().classes()).not.toContain('is-collapsed')
    expect(toggle().attributes('aria-label')).toBe('收起菜单')
  })

  it('390px 窄屏初始使用收起菜单，避免常驻侧栏占据首屏', async () => {
    vi.stubGlobal('matchMedia', vi.fn((query: string) => ({
      matches: query === '(max-width: 390px)',
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })))
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
      menus: [
        {
          id: 4,
          code: 'inventory',
          name: '库存管理',
          routePath: '/inventory/balances',
          children: [
            { id: 5, code: 'inventory:balance:view', name: '库存余额', routePath: '/inventory/balances' },
          ],
        },
      ],
      permissions: [],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.find('.app-sidebar').classes()).toContain('is-collapsed')
    expect(wrapper.findComponent({ name: 'ElMenu' }).props('collapse')).toBe(true)
    expect(wrapper.find('[data-test="sidebar-toggle-button"]').attributes('aria-label')).toBe('展开菜单')
  })

  it('登录成功进入工作台后顶部显示当前用户而不是未登录', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiFailure())
      .mockResolvedValueOnce(apiResponse(csrfToken))
      .mockResolvedValueOnce(apiResponse(adminLoginSession))
    vi.stubGlobal('fetch', fetcher)
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createQhErpRouter()
    router.push('/login')
    await router.isReady()
    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    await wrapper.find('input[name="username"]').setValue('admin')
    await wrapper.find('input[name="password"]').setValue('Qherp@2026!')
    await wrapper.find('[data-test="login-submit"]').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('home')
    expect(useAuthStore().currentUser?.username).toBe('admin')
    expect(wrapper.find('.header-user').text()).toContain('管理员')
    expect(wrapper.find('.header-user').text()).not.toContain('未登录')
  })

  it('已有有效登录会话进入工作台时从当前用户接口恢复顶部用户', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse(adminLoginSession))
    vi.stubGlobal('fetch', fetcher)
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()
    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    await flushPromises()

    expect(fetcher).toHaveBeenCalledWith('/api/auth/me', expect.objectContaining({ method: 'GET' }))
    expect(useAuthStore().currentUser?.username).toBe('admin')
    expect(wrapper.find('.header-user').text()).toContain('管理员')
    expect(wrapper.find('.header-user').text()).not.toContain('未登录')
  })

  it('有库存查看权限但后端只返回库存一级菜单时补齐库存子菜单入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'inventory_admin', displayName: '库存管理员', status: 'ENABLED' },
      menus: [
        {
          id: 4,
          code: 'inventory',
          name: '库存管理',
          routePath: '/inventory/balances',
          children: [],
        },
      ],
      permissions: [
        'inventory:balance:view',
        'inventory:movement:view',
        'inventory:document:view',
        'inventory:warehouse-transfer:view',
        'inventory:ownership-conversion:view',
        'inventory:stocktake:view',
        'inventory:valuation-adjustment:view',
      ],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('库存管理')
    expect(wrapper.text()).toContain('库存余额与价值')
    expect(wrapper.text()).toContain('库存流水与价值')
    expect(wrapper.text()).toContain('库存单据')
    expect(wrapper.text()).toContain('仓库调拨')
    expect(wrapper.text()).toContain('所有权转换')
    expect(wrapper.text()).toContain('库存盘点')
    expect(wrapper.text()).toContain('估值调整')
    expect(wrapper.findAllComponents({ name: 'ElMenuItem' }).map((item) => item.props('index')))
      .toEqual(expect.arrayContaining([
        '/inventory/warehouse-transfers',
        '/inventory/ownership-conversions',
        '/inventory/stocktakes',
        '/inventory/valuation-adjustments',
      ]))
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/inventory')
  })

  it('有项目成本权限但后端菜单缺失时补齐 029 成本管理入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'cost_admin', displayName: '成本管理员', status: 'ENABLED' },
      menus: [],
      permissions: [
        'cost:project-cost:view',
        'cost:project-cost-adjustment:view',
        'cost:project-cost-variance:view',
      ],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(costMenuSource).toContain('/cost/project-costs')
    expect(costMenuSource).toContain('cost:project-cost:view')
    expect(wrapper.text()).toContain('成本管理')
    expect(wrapper.text()).toContain('项目成本核算')
    expect(wrapper.text()).toContain('成本调整/分配')
    expect(wrapper.text()).toContain('项目成本差异')
    expect(wrapper.text()).not.toContain('成本记录')
    expect(wrapper.findAllComponents({ name: 'ElMenuItem' }).map((item) => item.props('index')))
      .toEqual(expect.arrayContaining([
        '/cost/project-costs',
        '/cost/project-cost-adjustments',
        '/cost/project-cost-variances',
      ]))
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/cost')
  })

  it('只有 009 成本记录查看权限时仍补齐成本记录入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'cost_record_user', displayName: '成本记录员', status: 'ENABLED' },
      menus: [],
      permissions: ['cost:record:view'],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('成本管理')
    expect(wrapper.text()).toContain('成本记录')
    expect(wrapper.text()).not.toContain('项目成本核算')
    expect(wrapper.findAllComponents({ name: 'ElMenuItem' }).map((item) => item.props('index')))
      .toContain('/cost/records')
  })

  it('有业务月结权限但后端菜单缺失时通过轻量菜单注册补齐独立入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'period_close_user', displayName: '月结用户', status: 'ENABLED' },
      menus: [],
      permissions: ['system:business-period-close:view'],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(periodCloseMenuSource).toContain('/period-close/runs')
    expect(periodCloseMenuSource).toContain('system:business-period-close:view')
    expect(appMenuRegistrySource).toContain('applyRegisteredModuleMenus')
    expect(appMenuRegistrySource).toContain('periodCloseChildren')
    expect(appSource).not.toContain('ensurePeriodCloseMenu')
    expect(appSource).not.toContain('removePeriodCloseMenus')
    expect(wrapper.text()).toContain('业务月结')
    expect(wrapper.text()).toContain('月结工作台')
    expect(wrapper.find('[data-test="main-menu-icon-period-close"]').exists()).toBe(true)
    expect(wrapper.findAllComponents({ name: 'ElMenuItem' }).map((item) => item.props('index')))
      .toContain('/period-close/runs')
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/period-close')
  })

  it('有会计核算权限但后端菜单缺失时通过轻量菜单注册补齐 GL 入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'gl_user', displayName: '会计用户', status: 'ENABLED' },
      menus: [],
      permissions: ['gl:voucher:view', 'gl:ledger:view'],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(glMenuSource).toContain('/gl/vouchers')
    expect(glMenuSource).toContain('gl:voucher:view')
    expect(appMenuRegistrySource).toContain('glChildren')
    expect(appSource).not.toContain('ensureGlMenu')
    expect(appSource).not.toContain('removeGlMenus')
    expect(wrapper.text()).toContain('会计核算')
    expect(wrapper.text()).toContain('正式凭证')
    expect(wrapper.text()).toContain('总账')
    expect(wrapper.find('[data-test="main-menu-icon-gl"]').exists()).toBe(true)
    expect(wrapper.findAllComponents({ name: 'ElMenuItem' }).map((item) => item.props('index')))
      .toEqual(expect.arrayContaining(['/gl/vouchers', '/gl/ledgers/general']))
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/gl')
  })

  it('有业务期间查看权限但后端菜单缺失时补齐系统管理入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'period_admin', displayName: '期间管理员', status: 'ENABLED' },
      menus: [],
      permissions: ['system:business-period:view'],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('系统管理')
    expect(wrapper.text()).toContain('业务期间')
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/system')
  })

  it('有单位换算和编码规则权限但后端菜单缺失时补齐基础资料入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'master_user', displayName: '主数据用户', status: 'ENABLED' },
      menus: [],
      permissions: ['master:unit-conversion:view', 'master:coding-rule:view'],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('基础资料')
    expect(wrapper.text()).toContain('物料单位换算')
    expect(wrapper.text()).toContain('编码规则')
    expect(wrapper.findAllComponents({ name: 'ElMenuItem' }).map((item) => item.props('index')))
      .toEqual(expect.arrayContaining(['/master/unit-conversions', '/master/coding-rules']))
  })

  it('有平台权限但后端菜单缺失时补齐平台工作台入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'platform_user', displayName: '平台用户', status: 'ENABLED' },
      menus: [],
      permissions: ['platform:todo:view', 'platform:message:view', 'platform:document-task:view'],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('平台工作台')
    expect(wrapper.text()).toContain('审批待办')
    expect(wrapper.text()).toContain('消息中心')
    expect(wrapper.text()).toContain('任务中心')
    expect(wrapper.findAllComponents({ name: 'ElMenuItem' }).map((item) => item.props('index')))
      .toEqual(expect.arrayContaining(['/platform/approvals', '/platform/messages', '/platform/document-tasks']))
  })

  it('有质量确认查看权限但后端菜单缺失时补齐质量管理入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'quality_user', displayName: '质量员', status: 'ENABLED' },
      menus: [],
      permissions: ['quality:inspection:view'],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('质量管理')
    expect(wrapper.text()).toContain('质量确认')
    expect(wrapper.find('[data-test="main-menu-icon-quality"]').exists()).toBe(true)
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/quality')
  })

  it('有财务查看权限但后端菜单缺失时补齐财务往来入口并按权限显示子项', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'finance_user', displayName: '财务用户', status: 'ENABLED' },
      menus: [],
      permissions: [
        financePermissions.salesInvoiceView,
        financePermissions.purchaseInvoiceView,
        financePermissions.expenseView,
        financePermissions.advanceReceiptView,
        financePermissions.prepaymentView,
        financePermissions.settlementAllocationView,
        financePermissions.voucherDraftView,
        'finance:receivable:view',
        'finance:receipt:view',
        'finance:payable:view',
      ],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('财务往来')
    expect(wrapper.text()).toContain('销售发票')
    expect(wrapper.text()).toContain('采购发票')
    expect(wrapper.text()).toContain('费用单')
    expect(wrapper.text()).toContain('预收款')
    expect(wrapper.text()).toContain('预付款')
    expect(wrapper.text()).toContain('对账核销')
    expect(wrapper.text()).toContain('凭证草稿')
    expect(wrapper.text()).toContain('应收台账')
    expect(wrapper.text()).toContain('收款记录')
    expect(wrapper.text()).toContain('应付台账')
    expect(wrapper.text()).not.toContain('付款记录')
    expect(wrapper.text()).not.toContain('往来冲减')
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/finance')
  })

  it('只有往来冲减查看权限且后端菜单缺失时补齐往来冲减入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'settlement_user', displayName: '往来用户', status: 'ENABLED' },
      menus: [],
      permissions: [financePermissions.settlementAdjustmentView],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('财务往来')
    expect(wrapper.text()).toContain('往来冲减')
    expect(wrapper.text()).not.toContain('应收台账')
    expect(wrapper.text()).not.toContain('收款记录')
    expect(wrapper.text()).not.toContain('应付台账')
    expect(wrapper.text()).not.toContain('付款记录')
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/finance')
  })

  it('有报表查看权限但后端菜单缺失时补齐经营报表入口并按权限显示子项', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'report_user', displayName: '报表用户', status: 'ENABLED' },
      menus: [],
      permissions: [reportPermissions.overviewView, reportPermissions.salesView, reportPermissions.exceptionView],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('经营报表')
    expect(wrapper.text()).toContain('经营概览')
    expect(wrapper.text()).toContain('销售经营')
    expect(wrapper.text()).toContain('异常清单')
    expect(wrapper.text()).not.toContain('采购经营')
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/report')
  })

  it('有生产退料补料查看权限但后端菜单缺失时补齐生产管理入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'production_reversal_user', displayName: '生产反冲员', status: 'ENABLED' },
      menus: [],
      permissions: ['production:material-return:view', 'production:material-supplement:view'],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('生产管理')
    expect(wrapper.text()).toContain('生产退料')
    expect(wrapper.text()).toContain('生产补料')
    expect(wrapper.text()).not.toContain('生产工单')
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/production')
  })

  it('生产与计划菜单从触达模块接线，并按外协权限补齐外协执行入口', async () => {
    expect(productionMenuSource).toContain('/production/outsourcing-orders')
    expect(productionMenuSource).toContain('production:outsourcing:view')
    expect(planningMenuSource).toContain('/planning/material-requirements')
    expect(planningMenuSource).toContain('planning:material-requirement:view')

    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'outsourcing_user', displayName: '外协员', status: 'ENABLED' },
      menus: [],
      permissions: ['production:outsourcing:view'],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('生产管理')
    expect(wrapper.text()).toContain('外协执行')
    expect(wrapper.text()).not.toContain('生产工单')
    expect(wrapper.findAllComponents({ name: 'ElMenuItem' }).map((item) => item.props('index')))
      .toContain('/production/outsourcing-orders')
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/production')
  })

  it('有 026 计划查看权限但后端菜单缺失时补齐订单缺料分析入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'planner', displayName: '计划员', status: 'ENABLED' },
      menus: [],
      permissions: ['planning:material-requirement:view'],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('计划管理')
    expect(wrapper.text()).toContain('订单缺料分析')
    expect(wrapper.find('[data-test="main-menu-icon-planning"]').exists()).toBe(true)
    expect(wrapper.findAllComponents({ name: 'ElMenuItem' }).map((item) => item.props('index')))
      .toContain('/planning/material-requirements')
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/planning')
  })

  it('无 026 计划查看权限时递归移除计划菜单', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'no_planning', displayName: '无计划权限', status: 'ENABLED' },
      menus: [
        {
          id: 95,
          code: 'business',
          name: '业务管理',
          routePath: null,
          children: [
            {
              id: 96,
              code: 'planning:material-requirement:view',
              name: '订单缺料分析',
              routePath: '/planning/material-requirements',
            },
          ],
        },
      ],
      permissions: [],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).not.toContain('计划管理')
    expect(wrapper.text()).not.toContain('订单缺料分析')
    expect(wrapper.text()).not.toContain('业务管理')
  })

  it('无生产查看权限时递归移除生产菜单', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'no_production', displayName: '无生产权限', status: 'ENABLED' },
      menus: [
        {
          id: 90,
          code: 'business',
          name: '业务管理',
          routePath: null,
          children: [
            { id: 91, code: 'production:work-order:view', name: '生产工单', routePath: '/production/work-orders' },
            { id: 92, code: 'production:material-return:view', name: '生产退料', routePath: '/production/material-returns' },
            { id: 93, code: 'production:material-supplement:view', name: '生产补料', routePath: '/production/material-supplements' },
            { id: 94, code: 'production:outsourcing:view', name: '外协执行', routePath: '/production/outsourcing-orders' },
          ],
        },
      ],
      permissions: [],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).not.toContain('生产工单')
    expect(wrapper.text()).not.toContain('生产退料')
    expect(wrapper.text()).not.toContain('生产补料')
    expect(wrapper.text()).not.toContain('外协执行')
    expect(wrapper.text()).not.toContain('业务管理')
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .not.toContain('/menu/production')
  })

  it('无报表查看权限时不显示经营报表入口并递归移除报表菜单', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'no_report', displayName: '无报表权限', status: 'ENABLED' },
      menus: [
        {
          id: 80,
          code: 'business',
          name: '业务管理',
          routePath: null,
          children: [
            { id: 81, code: reportPermissions.salesView, name: '销售经营', routePath: '/reports/sales' },
            { id: 82, code: reportPermissions.exceptionView, name: '异常清单', routePath: '/reports/exceptions' },
          ],
        },
      ],
      permissions: [],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).not.toContain('经营报表')
    expect(wrapper.text()).not.toContain('销售经营')
    expect(wrapper.text()).not.toContain('异常清单')
    expect(wrapper.text()).not.toContain('业务管理')
  })

  it('无财务查看权限时递归移除挂在其他父级下的财务菜单', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'no_finance', displayName: '无财务权限', status: 'ENABLED' },
      menus: [
        {
          id: 70,
          code: 'business',
          name: '业务管理',
          routePath: null,
          children: [
            { id: 71, code: 'finance:receivable:view', name: '应收台账', routePath: '/finance/receivables' },
            { id: 72, code: 'finance:payment:view', name: '付款记录', routePath: '/finance/payments' },
            { id: 73, code: 'finance:settlement-adjustment:view', name: '往来冲减', routePath: '/finance/settlement-adjustments' },
          ],
        },
      ],
      permissions: [],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).not.toContain('财务往来')
    expect(wrapper.text()).not.toContain('应收台账')
    expect(wrapper.text()).not.toContain('付款记录')
    expect(wrapper.text()).not.toContain('往来冲减')
    expect(wrapper.text()).not.toContain('业务管理')
  })

  it('后端返回采购菜单且用户有采购查看权限时展示采购入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'procurement_user', displayName: '采购用户', status: 'ENABLED' },
      menus: [
        {
          id: 20,
          code: 'procurement',
          name: '采购管理',
          routePath: '/procurement/orders',
          children: [
            { id: 21, code: 'procurement:order:view', name: '采购订单', routePath: '/procurement/orders' },
            { id: 22, code: 'procurement:receipt:view', name: '采购入库', routePath: '/procurement/receipts' },
          ],
        },
      ],
      permissions: ['procurement:order:view', 'procurement:receipt:view'],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('采购管理')
    expect(wrapper.text()).toContain('采购订单')
    expect(wrapper.text()).toContain('采购入库')
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/procurement')
  })

  it('有采购查看权限但后端菜单缺失时补齐采购管理入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'procurement_admin', displayName: '采购管理员', status: 'ENABLED' },
      menus: [],
      permissions: ['procurement:order:view', 'procurement:receipt:view', 'procurement:return:view'],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('采购管理')
    expect(wrapper.text()).toContain('采购订单')
    expect(wrapper.text()).toContain('采购入库')
    expect(wrapper.text()).toContain('采购退货')
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/procurement')
  })

  it('024 采购深化权限补齐请购、询价、价格协议和有效供给入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'procurement_project_user', displayName: '项目采购员', status: 'ENABLED' },
      menus: [],
      permissions: [
        'procurement:requisition:view',
        'procurement:inquiry:view',
        'procurement:price-agreement:view',
        'procurement:supply:view',
      ],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('采购管理')
    expect(wrapper.text()).toContain('采购请购')
    expect(wrapper.text()).toContain('询价比价')
    expect(wrapper.text()).toContain('价格协议')
    expect(wrapper.text()).toContain('有效采购供给')
    expect(wrapper.text()).not.toContain('采购订单')
    expect(wrapper.text()).not.toContain('采购入库')
    expect(wrapper.findAllComponents({ name: 'ElMenuItem' }).map((item) => item.props('index')))
      .toEqual(expect.arrayContaining([
        '/procurement/requisitions',
        '/procurement/inquiries',
        '/procurement/price-agreements',
        '/procurement/effective-supplies',
      ]))
  })

  it('只有采购退货查看权限且后端菜单缺失时补齐采购退货入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'procurement_return_user', displayName: '采购退货员', status: 'ENABLED' },
      menus: [],
      permissions: ['procurement:return:view'],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('采购管理')
    expect(wrapper.text()).toContain('采购退货')
    expect(wrapper.text()).not.toContain('采购订单')
    expect(wrapper.text()).not.toContain('采购入库')
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/procurement')
  })

  it('无采购查看权限时不显示采购管理入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'no_procurement', displayName: '无采购权限', status: 'ENABLED' },
      menus: [
        {
          id: 20,
          code: 'procurement',
          name: '采购管理',
          routePath: null,
          children: [
            { id: 21, code: 'procurement:order:view', name: '采购订单', routePath: '/procurement/orders' },
            { id: 22, code: 'procurement:receipt:view', name: '采购入库', routePath: '/procurement/receipts' },
            { id: 23, code: 'procurement:return:view', name: '采购退货', routePath: '/procurement/returns' },
          ],
        },
      ],
      permissions: [],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).not.toContain('采购管理')
    expect(wrapper.text()).not.toContain('采购订单')
    expect(wrapper.text()).not.toContain('采购入库')
    expect(wrapper.text()).not.toContain('采购退货')
  })

  it('无采购查看权限时递归移除挂在其他父级下的采购菜单', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'business_user', displayName: '业务用户', status: 'ENABLED' },
      menus: [
        {
          id: 30,
          code: 'business',
          name: '业务管理',
          routePath: null,
          children: [
            { id: 31, code: 'procurement:order:view', name: '采购订单', routePath: '/procurement/orders' },
            { id: 32, code: 'procurement:receipt:view', name: '采购入库', routePath: '/procurement/receipts' },
            { id: 33, code: 'procurement:return:view', name: '采购退货', routePath: '/procurement/returns' },
          ],
        },
      ],
      permissions: [],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).not.toContain('采购管理')
    expect(wrapper.text()).not.toContain('采购订单')
    expect(wrapper.text()).not.toContain('采购入库')
    expect(wrapper.text()).not.toContain('采购退货')
    expect(wrapper.text()).not.toContain('业务管理')
  })

  it('后端返回销售菜单且用户有销售查看权限时展示销售入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'sales_user', displayName: '销售用户', status: 'ENABLED' },
      menus: [
        {
          id: 40,
          code: 'sales',
          name: '销售管理',
          routePath: '/sales/orders',
          children: [
            { id: 41, code: 'sales:order:view', name: '销售订单', routePath: '/sales/orders' },
            { id: 42, code: 'sales:shipment:view', name: '销售出库', routePath: '/sales/shipments' },
          ],
        },
      ],
      permissions: ['sales:order:view', 'sales:shipment:view'],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('销售管理')
    expect(wrapper.text()).toContain('销售订单')
    expect(wrapper.text()).toContain('销售出库')
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/sales')
  })

  it('有销售项目和 025 销售深化权限时在销售菜单补齐固定入口并优先显示', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'sales_project_user', displayName: '项目用户', status: 'ENABLED' },
      menus: [],
      permissions: [
        'sales:project:view',
        'sales:quote:view',
        'sales:order:view',
        'sales:delivery-plan:view',
        'sales:credit:view',
        'sales:effective-demand:view',
      ],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('销售管理')
    expect(wrapper.text()).toContain('销售项目')
    expect(wrapper.text()).toContain('销售报价')
    expect(wrapper.text()).toContain('销售订单')
    expect(wrapper.text()).toContain('交付计划')
    expect(wrapper.text()).toContain('信用档案')
    expect(wrapper.text()).toContain('有效销售需求')
    expect(wrapper.findAllComponents({ name: 'ElMenuItem' }).map((item) => item.props('index')))
      .toEqual(expect.arrayContaining([
        '/sales/projects',
        '/sales/quotes',
        '/sales/orders',
        '/sales/delivery-plans',
        '/sales/credit-profiles',
        '/sales/effective-demands',
      ]))
  })

  it('系统管理员无显式信用查看权限时仍补齐信用档案入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
      menus: [],
      permissions: [],
      roles: [{ id: 1, code: 'SYSTEM_ADMIN', name: '系统管理员', status: 'ENABLED' }],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('销售管理')
    expect(wrapper.text()).toContain('信用档案')
    expect(wrapper.findAllComponents({ name: 'ElMenuItem' }).map((item) => item.props('index')))
      .toContain('/sales/credit-profiles')
  })

  it('有销售查看权限但后端菜单缺失时补齐销售管理入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'sales_admin', displayName: '销售管理员', status: 'ENABLED' },
      menus: [],
      permissions: ['sales:order:view', 'sales:shipment:view'],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('销售管理')
    expect(wrapper.text()).toContain('销售订单')
    expect(wrapper.text()).toContain('销售出库')
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/sales')
  })

  it('只有销售退货查看权限且后端菜单缺失时补齐销售退货入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'sales_return_user', displayName: '销售退货员', status: 'ENABLED' },
      menus: [],
      permissions: ['sales:return:view'],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('销售管理')
    expect(wrapper.text()).toContain('销售退货')
    expect(wrapper.text()).not.toContain('销售订单')
    expect(wrapper.text()).not.toContain('销售出库')
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/sales')
  })

  it('无销售查看权限时递归移除挂在其他父级下的销售菜单', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'business_user', displayName: '业务用户', status: 'ENABLED' },
      menus: [
        {
          id: 50,
          code: 'business',
          name: '业务管理',
          routePath: null,
          children: [
            { id: 51, code: 'sales:order:view', name: '销售订单', routePath: '/sales/orders' },
            { id: 52, code: 'sales:shipment:view', name: '销售出库', routePath: '/sales/shipments' },
            { id: 53, code: 'sales:return:view', name: '销售退货', routePath: '/sales/returns' },
          ],
        },
      ],
      permissions: [],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).not.toContain('销售管理')
    expect(wrapper.text()).not.toContain('销售订单')
    expect(wrapper.text()).not.toContain('销售出库')
    expect(wrapper.text()).not.toContain('销售退货')
    expect(wrapper.text()).not.toContain('业务管理')
  })

  it('只有销售订单查看权限时递归移除无权限销售出库菜单并补齐允许入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'sales_order_user', displayName: '销售订单用户', status: 'ENABLED' },
      menus: [
        {
          id: 60,
          code: 'business',
          name: '业务管理',
          routePath: null,
          children: [
            { id: 61, code: 'sales:shipment:view', name: '销售出库', routePath: '/sales/shipments' },
          ],
        },
      ],
      permissions: ['sales:order:view'],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('销售管理')
    expect(wrapper.text()).toContain('销售订单')
    expect(wrapper.text()).not.toContain('销售出库')
    expect(wrapper.text()).not.toContain('业务管理')
  })

  it('无成本查看权限时不显示成本管理入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'readonly', displayName: '只读用户', status: 'ENABLED' },
      menus: [
        {
          id: 10,
          code: 'cost',
          name: '成本管理',
          routePath: null,
          children: [{ id: 11, code: 'cost:record:view', name: '成本记录', routePath: '/cost/records' }],
        },
      ],
      permissions: [],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).not.toContain('成本管理')
    expect(wrapper.text()).not.toContain('成本记录')
  })

  it('退出失败时保留当前会话和路由并显示错误提示', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useAuthStore()
    store.setSession({
      user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
      menus: [],
      permissions: [],
    })
    vi.spyOn(store, 'logout').mockRejectedValue(new Error('退出接口失败'))
    const router = createQhErpRouter()
    const replaceSpy = vi.spyOn(router, 'replace')
    router.push('/')
    await router.isReady()
    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    await wrapper.find('[data-test="logout-button"]').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(store.currentUser?.username).toBe('admin')
    expect(router.currentRoute.value.name).toBe('home')
    expect(replaceSpy).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('退出接口失败')
    expect(wrapper.find('[data-test="logout-button"]').attributes('disabled')).toBeUndefined()
  })

  it('退出提交中禁用退出入口并显示加载态', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useAuthStore()
    store.setSession({
      user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
      menus: [],
      permissions: [],
    })
    let resolveLogout!: () => void
    vi.spyOn(store, 'logout').mockImplementation(() => new Promise<void>((resolve) => {
      resolveLogout = () => {
        store.clearSession()
        resolve()
      }
    }))
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()
    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    await wrapper.find('[data-test="logout-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="logout-button"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('退出中')

    resolveLogout()
    await flushPromises()
  })

  it('财务状态标签展示中文状态', () => {
    const receivable = mount(ReceivableStatusTag, { props: { status: 'PARTIALLY_RECEIVED' }, global: { plugins: [ElementPlus] } })
    const receipt = mount(ReceiptStatusTag, { props: { status: 'POSTED' }, global: { plugins: [ElementPlus] } })
    const payable = mount(PayableStatusTag, { props: { status: 'PAID' }, global: { plugins: [ElementPlus] } })
    const payment = mount(PaymentStatusTag, { props: { status: 'CANCELLED' }, global: { plugins: [ElementPlus] } })

    expect(receivable.text()).toContain('部分收款')
    expect(receipt.text()).toContain('已过账')
    expect(payable.text()).toContain('已付清')
    expect(payment.text()).toContain('已取消')
  })

  it('财务页面 helper 提供金额格式、来源文案和权限常量', () => {
    expect(formatFinanceAmount('1234.5')).toBe('1,234.50')
    expect(formatFinanceAmount(0)).toBe('0.00')
    expect(financeSourceTypeText('SALES_SHIPMENT')).toBe('销售出库')
    expect(financeSourceTypeText('PURCHASE_RECEIPT')).toBe('采购入库')
    expect(financePermissions.receivableView).toBe('finance:receivable:view')
    expect(financePermissions.paymentView).toBe('finance:payment:view')
    expect(financePermissions.settlementAdjustmentView).toBe('finance:settlement-adjustment:view')
  })
})
