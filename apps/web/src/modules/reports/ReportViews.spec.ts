import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { Component } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reportPermissions, reportRouteConfigs } from './reportPageHelpers'
import ReportOverviewView from './ReportOverviewView.vue'
import SalesReportView from './SalesReportView.vue'
import InventoryReportView from './InventoryReportView.vue'
import ExceptionReportView from './ExceptionReportView.vue'
import { useAuthStore } from '../../stores/authStore'

const businessReportingApiMock = vi.hoisted(() => ({
  overview: { get: vi.fn() },
  sales: {
    list: vi.fn(),
    traces: { list: vi.fn() },
  },
  procurement: {
    list: vi.fn(),
    traces: { list: vi.fn() },
  },
  inventory: {
    list: vi.fn(),
    traces: { list: vi.fn() },
  },
  production: {
    list: vi.fn(),
    traces: { list: vi.fn() },
  },
  cost: {
    list: vi.fn(),
    traces: { list: vi.fn() },
  },
  settlement: {
    list: vi.fn(),
    traces: { list: vi.fn() },
  },
  exceptions: {
    list: vi.fn(),
    traces: { list: vi.fn() },
  },
}))

vi.mock('../../shared/api/businessReportingApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/businessReportingApi')>()),
  businessReportingApi: businessReportingApiMock,
}))

const reportPage = <T>(summary: T, items: unknown[], total = items.length) => ({
  summary,
  items,
  page: 1,
  pageSize: 20,
  total,
  totalPages: total > 0 ? 1 : 0,
})

const defaultReportPermissions: string[] = [
  reportPermissions.overviewView,
  reportPermissions.salesView,
  reportPermissions.inventoryView,
  reportPermissions.exceptionView,
]

async function mountReport(component: Component, path = '/reports/sales', permissions: string[] = defaultReportPermissions) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'report-admin', displayName: '报表管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { render: () => null } },
      { path: '/reports/overview', name: 'reports-overview', component },
      { path: '/reports/sales', name: 'reports-sales', component },
      { path: '/reports/procurement', name: 'reports-procurement', component },
      { path: '/reports/inventory', name: 'reports-inventory', component },
      { path: '/reports/production', name: 'reports-production', component },
      { path: '/reports/cost', name: 'reports-cost', component },
      { path: '/reports/settlement', name: 'reports-settlement', component },
      { path: '/reports/exceptions', name: 'reports-exceptions', component },
      { path: '/sales/shipments/:id', name: 'sales-shipment-detail', component: { render: () => null } },
      { path: '/inventory/movements', name: 'inventory-movements', component: { render: () => null } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(component, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('经营报表页面', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    businessReportingApiMock.overview.get.mockResolvedValue({
      period: { dateFrom: '2026-07-01', dateTo: '2026-07-31' },
      salesShipmentAmount: '120000.00',
      purchaseReceiptAmount: '80000.00',
      inventoryInQuantity: '500.000',
      inventoryOutQuantity: '320.000',
      productionPlannedQuantity: '200.000',
      productionCompletedQuantity: '160.000',
      costAmount: '35000.00',
      receivableBalance: '45000.00',
      payableBalance: '30000.00',
      receivedAmount: '75000.00',
      paidAmount: '50000.00',
      exceptionCount: 6,
      formalAccounting: false,
    })
    businessReportingApiMock.sales.list.mockResolvedValue(reportPage({
      shipmentQuantity: '10.000',
      shipmentAmount: '12000.00',
      receivableAmount: '12000.00',
      receivedAmount: '5000.00',
      unreceivedAmount: '7000.00',
      sourceCount: 1,
    }, [
      {
        sourceType: 'SALES_SHIPMENT',
        sourceId: 1001,
        sourceNo: 'SS202607040001',
        salesOrderNo: 'SO202607040001',
        customerName: '示例客户',
        materialName: '示例成品',
        businessDate: '2026-07-04',
        quantity: '10.000',
        amount: '12000.00',
        sourceCount: 1,
        traceKey: 'sales-summary:SALES_SHIPMENT:1001',
      },
    ]))
    businessReportingApiMock.sales.traces.list.mockResolvedValue({
      items: [
        {
          sourceType: 'SALES_SHIPMENT',
          sourceId: 1001,
          sourceNo: 'SS202607040001',
          sourceLineId: 2001,
          businessDate: '2026-07-04',
          status: 'POSTED',
          quantity: '10.000',
          amount: '12000.00',
          resourceRouteName: 'sales-shipment-detail',
          resourceRouteParams: { id: 1001 },
          resourceRouteQuery: null,
          canViewResource: true,
          restricted: false,
          restrictedMessage: null,
        },
        {
          sourceType: 'RECEIPT',
          sourceId: null,
          sourceNo: null,
          sourceLineId: null,
          businessDate: null,
          status: null,
          quantity: null,
          amount: null,
          resourceRouteName: null,
          resourceRouteParams: null,
          resourceRouteQuery: null,
          canViewResource: false,
          restricted: true,
          restrictedMessage: '当前账号没有查看来源详情的权限',
        },
      ],
      page: 1,
      pageSize: 20,
      total: 2,
      totalPages: 1,
    })
    businessReportingApiMock.inventory.list.mockResolvedValue(reportPage({
      openingQuantity: '100.000',
      inQuantity: '50.000',
      outQuantity: '30.000',
      adjustQuantity: '0.000',
      closingQuantity: '120.000',
      sourceCount: 8,
    }, [
      {
        warehouseId: 1,
        warehouseName: '主仓',
        materialId: 31,
        materialName: '示例物料',
        openingQuantity: '100.000',
        inQuantity: '50.000',
        outQuantity: '30.000',
        adjustQuantity: '0.000',
        closingQuantity: '120.000',
        sourceCount: 8,
        traceKey: 'inventory-stock-flow:1:31',
      },
    ]))
    businessReportingApiMock.inventory.traces.list.mockResolvedValue({
      items: [],
      page: 1,
      pageSize: 20,
      total: 0,
      totalPages: 0,
    })
    businessReportingApiMock.exceptions.list.mockResolvedValue(reportPage({
      exceptionCount: 1,
      criticalCount: 0,
      warningCount: 1,
      countsByType: { INVENTORY_SHORTAGE: 1 },
    }, [
      {
        exceptionType: 'INVENTORY_SHORTAGE',
        severity: 'WARNING',
        sourceType: 'INVENTORY_BALANCE',
        sourceId: 31,
        sourceNo: 'WH-1 / MAT-31',
        businessDate: '2026-07-04',
        objectName: '主仓 / 示例物料',
        description: '库存不足',
        sourceCount: 1,
        canViewResource: true,
        traceKey: 'exceptions:INVENTORY_SHORTAGE:INVENTORY_BALANCE:1:31',
      },
      {
        exceptionType: 'RECEIVABLE_OVERDUE',
        severity: 'WARNING',
        sourceType: null,
        sourceId: null,
        sourceNo: null,
        businessDate: null,
        objectName: null,
        description: '应收逾期',
        sourceCount: 1,
        canViewResource: false,
        traceKey: null,
      },
    ]))
    businessReportingApiMock.exceptions.traces.list.mockResolvedValue({
      items: [],
      page: 1,
      pageSize: 20,
      total: 0,
      totalPages: 0,
    })
  })

  it('经营概览展示核心指标、期间、经营口径提示、空态和错误态', async () => {
    const { wrapper } = await mountReport(ReportOverviewView, '/reports/overview')

    expect(wrapper.text()).toContain('经营概览')
    expect(wrapper.text()).toContain('2026-07-01')
    expect(wrapper.text()).toContain('2026-07-31')
    expect(wrapper.text()).toContain('120000.00')
    expect(wrapper.text()).toContain('业务经营口径')
    expect(wrapper.text()).toContain('不等同正式财务入账')

    businessReportingApiMock.overview.get.mockResolvedValueOnce({
      period: { dateFrom: '2026-08-01', dateTo: '2026-08-31' },
      salesShipmentAmount: '0.00',
      purchaseReceiptAmount: '0.00',
      inventoryInQuantity: '0.000',
      inventoryOutQuantity: '0.000',
      productionPlannedQuantity: '0.000',
      productionCompletedQuantity: '0.000',
      costAmount: '0.00',
      receivableBalance: '0.00',
      payableBalance: '0.00',
      receivedAmount: '0.00',
      paidAmount: '0.00',
      exceptionCount: 0,
      formalAccounting: false,
    })
    await wrapper.find('input[name="report-date-from"]').setValue('2026-08-01')
    await wrapper.find('input[name="report-date-to"]').setValue('2026-08-31')
    await wrapper.find('[data-test="search-report"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('暂无经营概览数据')

    businessReportingApiMock.overview.get.mockRejectedValueOnce(new Error('概览加载失败'))
    await wrapper.find('[data-test="search-report"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('概览加载失败')
  })

  it('经营概览只展示当前账号有权限的固定报表入口并支持页面内跳转', async () => {
    const { wrapper, router } = await mountReport(ReportOverviewView, '/reports/overview')
    const visiblePermissions: string[] = [reportPermissions.salesView, reportPermissions.inventoryView, reportPermissions.exceptionView]
    const visibleReportRoutes = reportRouteConfigs.filter((item) =>
      visiblePermissions.includes(item.permission),
    )

    const entries = wrapper.findAll('[data-test="fixed-report-entry"]')
    const entryTexts = entries.map((item) => item.text())
    expect(entries).toHaveLength(3)
    expect(entryTexts).toEqual(['销售经营', '库存收发存', '异常清单'])
    expect(entryTexts).not.toContain('采购经营')
    expect(entryTexts).not.toContain('生产执行')
    expect(entryTexts).not.toContain('成本归集')
    expect(entryTexts).not.toContain('往来收付')
    visibleReportRoutes.forEach((route) => {
      const entry = entries.find((item) => item.text().includes(route.menuName))
      expect(entry?.attributes('href')).toBe(route.path)
    })

    await entries.find((item) => item.text().includes('销售经营'))?.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('reports-sales')
  })

  it('经营概览在全报表权限会话下展示七类固定报表入口', async () => {
    const permissions = reportRouteConfigs.map((item) => item.permission)
    const { wrapper } = await mountReport(ReportOverviewView, '/reports/overview', permissions)
    const fixedReportRoutes = reportRouteConfigs.filter((item) => item.routeName !== 'reports-overview')

    const entries = wrapper.findAll('[data-test="fixed-report-entry"]')
    expect(entries).toHaveLength(7)
    expect(entries.map((item) => item.text())).toEqual(fixedReportRoutes.map((item) => item.menuName))
  })

  it('销售经营报表支持筛选、重置、分页、汇总指标、口径说明和来源追溯脱敏', async () => {
    const { wrapper, router } = await mountReport(SalesReportView)

    expect(wrapper.text()).toContain('销售经营')
    expect(wrapper.text()).toContain('已过账销售出库')
    expect(wrapper.text()).toContain('12000.00')
    expect(wrapper.text()).toContain('SS202607040001')

    await wrapper.find('input[name="report-keyword"]').setValue('SS')
    await wrapper.find('input[name="report-date-from"]').setValue('2026-07-01')
    await wrapper.find('input[name="report-date-to"]').setValue('2026-07-31')
    await wrapper.find('input[name="report-status"]').setValue('POSTED')
    await wrapper.find('input[name="report-customer-id"]').setValue('12')
    await wrapper.find('input[name="report-material-id"]').setValue('31')
    await wrapper.find('[data-test="search-report"]').trigger('click')
    await flushPromises()

    expect(businessReportingApiMock.sales.list).toHaveBeenLastCalledWith(expect.objectContaining({
      customerId: '12',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-31',
      keyword: 'SS',
      materialId: '31',
      page: 1,
      status: 'POSTED',
    }))

    await wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 2)
    await flushPromises()
    expect(businessReportingApiMock.sales.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 }))

    await wrapper.find('[data-test="open-report-trace"]').trigger('click')
    await flushPromises()
    expect(businessReportingApiMock.sales.traces.list).toHaveBeenCalledWith(expect.objectContaining({
      traceKey: 'sales-summary:SALES_SHIPMENT:1001',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-31',
    }))
    expect(wrapper.text()).toContain('来源追溯')
    expect(wrapper.text()).toContain('SS202607040001')
    expect(wrapper.text()).toContain('当前账号没有查看来源详情的权限')
    expect(wrapper.text()).not.toContain('RECEIPT')
    expect(wrapper.text()).not.toContain('2001')

    await wrapper.find('[data-test="view-trace-source"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('sales-shipment-detail')
    expect(router.currentRoute.value.params.id).toBe('1001')

    businessReportingApiMock.sales.list.mockResolvedValueOnce(reportPage({
      shipmentQuantity: '0.000',
      shipmentAmount: '0.00',
      sourceCount: 0,
    }, [], 0))
    await wrapper.find('[data-test="reset-report"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('暂无销售经营数据')
  })

  it('库存收发存打开追溯时携带当前期间', async () => {
    const { wrapper } = await mountReport(InventoryReportView, '/reports/inventory')

    await wrapper.find('input[name="report-date-from"]').setValue('2026-07-01')
    await wrapper.find('input[name="report-date-to"]').setValue('2026-07-31')
    await wrapper.find('[data-test="search-report"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="open-report-trace"]').trigger('click')
    await flushPromises()

    expect(businessReportingApiMock.inventory.traces.list).toHaveBeenCalledWith(expect.objectContaining({
      traceKey: 'inventory-stock-flow:1:31',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-31',
    }))
  })

  it('经营异常清单支持异常追溯，来源受限行不展示追溯入口和敏感来源字段', async () => {
    const { wrapper } = await mountReport(ExceptionReportView, '/reports/exceptions')

    expect(wrapper.text()).toContain('异常清单')
    expect(wrapper.text()).toContain('库存不足')
    expect(wrapper.text()).toContain('主仓 / 示例物料')
    expect(wrapper.text()).not.toContain('exceptions:INVENTORY_SHORTAGE')

    await wrapper.find('input[name="report-date-from"]').setValue('2026-07-01')
    await wrapper.find('input[name="report-date-to"]').setValue('2026-07-31')
    await wrapper.find('[data-test="open-report-trace"]').trigger('click')
    await flushPromises()

    expect(businessReportingApiMock.exceptions.traces.list).toHaveBeenCalledWith(expect.objectContaining({
      traceKey: 'exceptions:INVENTORY_SHORTAGE:INVENTORY_BALANCE:1:31',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-31',
    }))
    expect(wrapper.findAll('[data-test="open-report-trace"]')).toHaveLength(1)
    expect(wrapper.text()).toContain('应收逾期')
    expect(wrapper.text()).not.toContain('RECEIVABLE_OVERDUE:')
  })
})
