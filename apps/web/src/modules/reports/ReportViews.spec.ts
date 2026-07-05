import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { Component } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reportPermissions, reportRouteConfigs } from './reportPageHelpers'
import ReportOverviewView from './ReportOverviewView.vue'
import SalesReportView from './SalesReportView.vue'
import ProcurementReportView from './ProcurementReportView.vue'
import InventoryReportView from './InventoryReportView.vue'
import ProductionReportView from './ProductionReportView.vue'
import CostReportView from './CostReportView.vue'
import SettlementReportView from './SettlementReportView.vue'
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
  pageSize: 10,
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
      { path: '/sales/returns/:id', name: 'sales-return-detail', component: { render: () => null } },
      { path: '/procurement/returns/:id', name: 'procurement-return-detail', component: { render: () => null } },
      { path: '/production/material-returns/:id', name: 'production-material-return-detail', component: { render: () => null } },
      { path: '/production/material-supplements/:id', name: 'production-material-supplement-detail', component: { render: () => null } },
      { path: '/finance/settlement-adjustments/:id', name: 'finance-settlement-adjustment-detail', component: { render: () => null } },
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
      salesOriginalAmount: '12000.00',
      salesReturnAmount: '2400.00',
      salesNetAmount: '9600.00',
      salesOriginalQuantity: '10.000',
      salesReturnQuantity: '2.000',
      salesNetQuantity: '8.000',
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
        salesOriginalAmount: '12000.00',
        salesReturnAmount: '0.00',
        salesNetAmount: '12000.00',
        salesOriginalQuantity: '10.000',
        salesReturnQuantity: '0.000',
        salesNetQuantity: '10.000',
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
          sourceType: 'SALES_RETURN',
          sourceId: 5001,
          sourceNo: 'SR202607050001',
          sourceLineId: null,
          businessDate: '2026-07-05',
          status: 'POSTED',
          quantity: '2.000',
          amount: '2400.00',
          resourceRouteName: 'sales-return-detail',
          resourceRouteParams: { id: 5001 },
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
      pageSize: 10,
      total: 2,
      totalPages: 1,
    })
    businessReportingApiMock.procurement.list.mockResolvedValue(reportPage({
      receiptQuantity: '9.000',
      receiptAmount: '9000.00',
      purchaseOriginalAmount: '9000.00',
      purchaseReturnAmount: '1000.00',
      purchaseNetAmount: '8000.00',
      purchaseOriginalQuantity: '9.000',
      purchaseReturnQuantity: '1.000',
      purchaseNetQuantity: '8.000',
      payableAmount: '9000.00',
      paidAmount: '3000.00',
      unpaidAmount: '6000.00',
      sourceCount: 2,
    }, [
      {
        sourceType: 'PURCHASE_RETURN',
        sourceId: 6001,
        sourceNo: 'PR202607050001',
        purchaseOrderNo: 'PO202607010001',
        supplierName: '示例供应商',
        materialName: '示例原料',
        businessDate: '2026-07-05',
        quantity: '1.000',
        amount: '1000.00',
        purchaseOriginalAmount: '0.00',
        purchaseReturnAmount: '1000.00',
        purchaseNetAmount: '-1000.00',
        purchaseOriginalQuantity: '0.000',
        purchaseReturnQuantity: '1.000',
        purchaseNetQuantity: '-1.000',
        sourceCount: 1,
        traceKey: 'procurement-summary:PURCHASE_RETURN:6001',
      },
    ]))
    businessReportingApiMock.inventory.list.mockResolvedValue(reportPage({
      openingQuantity: '100.000',
      inQuantity: '50.000',
      outQuantity: '30.000',
      adjustQuantity: '0.000',
      closingQuantity: '120.000',
      inboundOriginalQuantity: '50.000',
      inboundReverseQuantity: '3.000',
      inboundNetQuantity: '53.000',
      outboundOriginalQuantity: '30.000',
      outboundReverseQuantity: '4.000',
      outboundNetQuantity: '34.000',
      inventoryNetChangeQuantity: '19.000',
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
        inboundOriginalQuantity: '50.000',
        inboundReverseQuantity: '3.000',
        inboundNetQuantity: '53.000',
        outboundOriginalQuantity: '30.000',
        outboundReverseQuantity: '4.000',
        outboundNetQuantity: '34.000',
        inventoryNetChangeQuantity: '19.000',
        sourceCount: 8,
        traceKey: 'inventory-stock-flow:1:31',
      },
    ]))
    businessReportingApiMock.inventory.traces.list.mockResolvedValue({
      items: [
        {
          sourceType: 'PRODUCTION_MATERIAL_RETURN',
          sourceId: 7001,
          sourceNo: 'PMR202607050001',
          sourceLineId: null,
          businessDate: '2026-07-05',
          status: 'POSTED',
          quantity: '3.000',
          amount: null,
          resourceRouteName: 'production-material-return-detail',
          resourceRouteParams: { id: 7001 },
          resourceRouteQuery: null,
          canViewResource: true,
          restricted: false,
          restrictedMessage: null,
        },
        {
          sourceType: 'PRODUCTION_MATERIAL_SUPPLEMENT',
          sourceId: 7002,
          sourceNo: 'PMS202607050001',
          sourceLineId: null,
          businessDate: '2026-07-05',
          status: 'POSTED',
          quantity: '4.000',
          amount: null,
          resourceRouteName: 'production-material-supplement-detail',
          resourceRouteParams: { id: 7002 },
          resourceRouteQuery: null,
          canViewResource: true,
          restricted: false,
          restrictedMessage: null,
        },
      ],
      page: 1,
      pageSize: 10,
      total: 2,
      totalPages: 1,
    })
    businessReportingApiMock.production.list.mockResolvedValue(reportPage({
      workOrderCount: 1,
      plannedQuantity: '20.000',
      issuedQuantity: '12.000',
      issuedOriginalQuantity: '12.000',
      materialReturnQuantity: '3.000',
      materialSupplementQuantity: '4.000',
      issuedNetQuantity: '13.000',
      completedQuantity: '8.000',
      reportedQuantity: '8.000',
      qualifiedQuantity: '8.000',
      defectiveQuantity: '0.000',
      completionReceiptQuantity: '8.000',
      completionRate: '40.00%',
      sourceCount: 3,
    }, [
      {
        workOrderId: 9001,
        workOrderNo: 'WO202607010001',
        productMaterialId: 31,
        productMaterialName: '示例成品',
        plannedQuantity: '20.000',
        issuedQuantity: '12.000',
        issuedOriginalQuantity: '12.000',
        materialReturnQuantity: '3.000',
        materialSupplementQuantity: '4.000',
        issuedNetQuantity: '13.000',
        completedQuantity: '8.000',
        reportedQuantity: '8.000',
        qualifiedQuantity: '8.000',
        defectiveQuantity: '0.000',
        completionReceiptQuantity: '8.000',
        completionRate: '40.00%',
        status: 'IN_PROGRESS',
        sourceCount: 3,
        traceKey: 'production-execution:WORK_ORDER:9001',
      },
    ]))
    businessReportingApiMock.cost.list.mockResolvedValue(reportPage({
      materialCostAmount: '5000.00',
      laborCostAmount: '1200.00',
      manufacturingOverheadAmount: '800.00',
      otherCostAmount: '0.00',
      totalCostAmount: '7000.00',
      materialOriginalCost: '5000.00',
      materialReturnCost: '900.00',
      materialSupplementCost: '1100.00',
      materialNetCost: '5200.00',
      totalNetCost: '7200.00',
      sourceCount: 3,
      formalAccounting: false,
    }, [
      {
        costRecordId: 3001,
        recordNo: 'COST202607050001',
        workOrderId: 9001,
        workOrderNo: 'WO202607010001',
        productMaterialId: 31,
        productMaterialName: '示例成品',
        costType: 'MATERIAL',
        sourceType: 'PRODUCTION_MATERIAL_SUPPLEMENT',
        sourceDocumentType: 'PRODUCTION_MATERIAL_SUPPLEMENT',
        sourceDocumentId: 7002,
        sourceDocumentNo: 'PMS202607050001',
        businessDate: '2026-07-05',
        quantity: '4.000',
        amount: '1100.00',
        materialOriginalCost: '0.00',
        materialReturnCost: '0.00',
        materialSupplementCost: '1100.00',
        materialNetCost: '1100.00',
        totalNetCost: '1100.00',
        basisType: 'BUSINESS',
        formalAccounting: false,
        sourceCount: 1,
        traceKey: 'cost-collection:COST_RECORD:3001',
      },
    ]))
    businessReportingApiMock.settlement.list.mockResolvedValue(reportPage({
      receivableAmount: '500.00',
      receivedAmount: '0.00',
      unreceivedAmount: '440.00',
      payableAmount: '0.00',
      paidAmount: '0.00',
      unpaidAmount: '0.00',
      receivableOriginalAmount: '500.00',
      receivableAdjustmentAmount: '60.00',
      receivableNetAmount: '440.00',
      payableOriginalAmount: '0.00',
      payableAdjustmentAmount: '0.00',
      payableNetAmount: '0.00',
      settlementRemainingAmount: '440.00',
      sourceCount: 1,
    }, [
      {
        settlementType: 'SETTLEMENT_ADJUSTMENT',
        sourceType: 'SETTLEMENT_ADJUSTMENT',
        sourceId: 8001,
        sourceNo: 'SA202607050001',
        partyType: 'CUSTOMER',
        partyId: 8,
        partyName: '示例客户',
        businessDate: '2026-07-05',
        totalAmount: '60.00',
        settledAmount: '60.00',
        unsettledAmount: '0.00',
        receivableOriginalAmount: '500.00',
        receivableAdjustmentAmount: '60.00',
        receivableNetAmount: '440.00',
        payableOriginalAmount: '0.00',
        payableAdjustmentAmount: '0.00',
        payableNetAmount: '0.00',
        settlementRemainingAmount: '440.00',
        status: 'POSTED',
        sourceCount: 1,
        traceKey: 'settlement-summary:SETTLEMENT_ADJUSTMENT:8001',
      },
    ]))
    businessReportingApiMock.settlement.traces.list.mockResolvedValue({
      items: [
        {
          sourceType: 'SETTLEMENT_ADJUSTMENT',
          sourceId: 8001,
          sourceNo: 'SA202607050001',
          sourceLineId: null,
          businessDate: '2026-07-05',
          status: 'POSTED',
          quantity: null,
          amount: '60.00',
          resourceRouteName: 'finance-settlement-adjustment-detail',
          resourceRouteParams: { id: 8001 },
          resourceRouteQuery: null,
          canViewResource: true,
          restricted: false,
          restrictedMessage: null,
        },
      ],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
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
      pageSize: 10,
      total: 0,
      totalPages: 0,
    })
  })

  it('经营概览展示核心指标、期间、经营口径提示、空态和错误态', async () => {
    const { wrapper } = await mountReport(ReportOverviewView, '/reports/overview')

    expect(wrapper.text()).toContain('经营概览')
    expect(wrapper.find('[data-test="report-date-picker-report-date-from"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="report-date-picker-report-date-to"]').exists()).toBe(true)
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
    expect(wrapper.text()).toContain('原发生金额')
    expect(wrapper.text()).toContain('退货金额')
    expect(wrapper.text()).toContain('销售净额')
    expect(wrapper.text()).toContain('原发生数量')
    expect(wrapper.text()).toContain('退货数量')
    expect(wrapper.text()).toContain('净数量')
    expect(wrapper.text()).toContain('9600.00')
    expect(wrapper.text()).toContain('销售出库')
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
    expect(wrapper.find('[data-test="report-trace-drawer"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('来源追溯')
    expect(wrapper.text()).toContain('SS202607040001')
    expect(wrapper.text()).toContain('销售退货')
    expect(wrapper.text()).toContain('SR202607050001')
    expect(wrapper.text()).toContain('当前账号没有查看来源详情的权限')
    expect(wrapper.text()).not.toContain('RECEIPT')
    expect(wrapper.text()).not.toContain('2001')

    await wrapper.find('[data-test="view-trace-source"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('sales-shipment-detail')
    expect(router.currentRoute.value.params.id).toBe('1001')
    expect(router.currentRoute.value.query.returnTo).toBe('/reports/sales')
    await wrapper.findAll('[data-test="view-trace-source"]')[1].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('sales-return-detail')
    expect(router.currentRoute.value.params.id).toBe('5001')
    expect(router.currentRoute.value.query.returnTo).toBe('/reports/sales')

    businessReportingApiMock.sales.list.mockResolvedValueOnce(reportPage({
      shipmentQuantity: '0.000',
      shipmentAmount: '0.00',
      salesOriginalAmount: '0.00',
      salesReturnAmount: '0.00',
      salesNetAmount: '0.00',
      salesOriginalQuantity: '0.000',
      salesReturnQuantity: '0.000',
      salesNetQuantity: '0.000',
      sourceCount: 0,
    }, [], 0))
    await wrapper.find('[data-test="reset-report"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('暂无销售经营数据')
  })

  it('库存收发存打开追溯时携带当前期间', async () => {
    const { wrapper, router } = await mountReport(InventoryReportView, '/reports/inventory')

    expect(wrapper.text()).toContain('入库原发生')
    expect(wrapper.text()).toContain('入库反向发生')
    expect(wrapper.text()).toContain('入库净额')
    expect(wrapper.text()).toContain('出库原发生')
    expect(wrapper.text()).toContain('出库反向发生')
    expect(wrapper.text()).toContain('出库净额')
    expect(wrapper.text()).toContain('库存净变化')
    expect(wrapper.text()).toContain('19.000')

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
    expect(wrapper.text()).toContain('生产退料')
    expect(wrapper.text()).toContain('生产补料')
    expect(wrapper.text()).toContain('PMR202607050001')
    expect(wrapper.text()).toContain('PMS202607050001')
    expect(wrapper.find('[data-test="report-trace-drawer"]').exists()).toBe(true)

    await wrapper.findAll('[data-test="view-trace-source"]')[0].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('production-material-return-detail')
    expect(router.currentRoute.value.params.id).toBe('7001')
    expect(router.currentRoute.value.query.returnTo).toBe('/reports/inventory')
    await wrapper.findAll('[data-test="view-trace-source"]')[1].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('production-material-supplement-detail')
    expect(router.currentRoute.value.params.id).toBe('7002')
    expect(router.currentRoute.value.query.returnTo).toBe('/reports/inventory')
  })

  it('采购、生产、成本报表展示原发生、反向发生和净额口径', async () => {
    const { wrapper: procurementWrapper } = await mountReport(ProcurementReportView, '/reports/procurement', [reportPermissions.procurementView])
    expect(procurementWrapper.text()).toContain('采购经营')
    expect(procurementWrapper.text()).toContain('采购原发生')
    expect(procurementWrapper.text()).toContain('采购退货')
    expect(procurementWrapper.text()).toContain('采购净额')
    expect(procurementWrapper.text()).toContain('8000.00')

    const { wrapper: productionWrapper } = await mountReport(ProductionReportView, '/reports/production', [reportPermissions.productionView])
    expect(productionWrapper.text()).toContain('生产执行')
    expect(productionWrapper.text()).toContain('领料原发生')
    expect(productionWrapper.text()).toContain('退料数量')
    expect(productionWrapper.text()).toContain('补料数量')
    expect(productionWrapper.text()).toContain('领料净数量')
    expect(productionWrapper.text()).toContain('13.000')

    const { wrapper: costWrapper } = await mountReport(CostReportView, '/reports/cost', [reportPermissions.costView])
    expect(costWrapper.text()).toContain('成本归集')
    expect(costWrapper.text()).toContain('材料原发生')
    expect(costWrapper.text()).toContain('退料成本')
    expect(costWrapper.text()).toContain('补料成本')
    expect(costWrapper.text()).toContain('材料净成本')
    expect(costWrapper.text()).toContain('5200.00')
    expect(costWrapper.find('[data-test="report-cost-type"]').text()).toBe('材料')
    expect(costWrapper.text()).not.toContain('MATERIAL')
  })

  it('往来收付报表展示往来冲减净额并按冲减来源追溯', async () => {
    const { wrapper, router } = await mountReport(SettlementReportView, '/reports/settlement', [reportPermissions.settlementView])

    expect(wrapper.text()).toContain('往来收付')
    expect(wrapper.text()).toContain('应收原发生')
    expect(wrapper.text()).toContain('应收反向发生')
    expect(wrapper.text()).toContain('应收净额')
    expect(wrapper.text()).toContain('应付原发生')
    expect(wrapper.text()).toContain('应付反向发生')
    expect(wrapper.text()).toContain('应付净额')
    expect(wrapper.text()).toContain('往来剩余')
    expect(wrapper.text()).toContain('往来冲减')
    expect(wrapper.text()).toContain('SA202607050001')
    expect(wrapper.text()).not.toContain('收款SA202607050001')
    expect(wrapper.text()).not.toContain('付款SA202607050001')

    await wrapper.find('[data-test="open-report-trace"]').trigger('click')
    await flushPromises()
    expect(businessReportingApiMock.settlement.traces.list).toHaveBeenCalledWith(expect.objectContaining({
      traceKey: 'settlement-summary:SETTLEMENT_ADJUSTMENT:8001',
    }))
    expect(wrapper.find('[data-test="report-trace-drawer"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('往来冲减')
    await wrapper.find('[data-test="view-trace-source"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('finance-settlement-adjustment-detail')
    expect(router.currentRoute.value.params.id).toBe('8001')
    expect(router.currentRoute.value.query.returnTo).toBe('/reports/settlement')
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
