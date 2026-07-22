import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { Component } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from '../../shared/api/accountPermissionApi'
import { useAuthStore } from '../../stores/authStore'
import ReportOverviewView from './ReportOverviewView.vue'
import ProjectProfitReportView from './ProjectProfitReportView.vue'
import ProjectProfitDetailView from './ProjectProfitDetailView.vue'
import ContractCollectionReportView from './ContractCollectionReportView.vue'
import ProcurementVarianceReportView from './ProcurementVarianceReportView.vue'
import InventoryCapitalReportView from './InventoryCapitalReportView.vue'
import ReceivablePayableReportView from './ReceivablePayableReportView.vue'
import OperatingAccountingReconciliationReportView from './OperatingAccountingReconciliationReportView.vue'
import FinancialSummaryReportView from './FinancialSummaryReportView.vue'
import BusinessReferenceSelect from '../system/shared/BusinessReferenceSelect.vue'
import { reportPermissions, reportSourceTypeText } from './reportPageHelpers'

const businessReportingApiMock = vi.hoisted(() => ({
  overview: { get: vi.fn() },
  operatingFinanceOverview: { get: vi.fn() },
  projectProfit: {
    list: vi.fn(),
    detail: { get: vi.fn() },
    traces: { list: vi.fn() },
  },
  contractCollection: {
    list: vi.fn(),
    traces: { list: vi.fn() },
  },
  procurementVariance: {
    list: vi.fn(),
    traces: { list: vi.fn() },
  },
  inventoryCapital: {
    list: vi.fn(),
    traces: { list: vi.fn() },
  },
  receivablePayable: {
    list: vi.fn(),
    traces: { list: vi.fn() },
  },
  operatingAccountingReconciliation: {
    list: vi.fn(),
    traces: { list: vi.fn() },
  },
  financialSummary: {
    get: vi.fn(),
    traces: { list: vi.fn() },
  },
}))

const masterDataApiMock = vi.hoisted(() => ({
  customers: { list: vi.fn(), get: vi.fn() },
  suppliers: { list: vi.fn(), get: vi.fn() },
  warehouses: { list: vi.fn(), get: vi.fn() },
  materials: { list: vi.fn(), get: vi.fn() },
}))

const salesProjectApiMock = vi.hoisted(() => ({
  projects: { list: vi.fn(), get: vi.fn() },
  contracts: { get: vi.fn() },
  listOrderLinkCandidates: vi.fn(),
}))

vi.mock('../../shared/api/businessReportingApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/businessReportingApi')>()),
  businessReportingApi: businessReportingApiMock,
}))

vi.mock('../../shared/api/masterDataApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/masterDataApi')>()),
  masterDataApi: masterDataApiMock,
}))

vi.mock('../../shared/api/salesProjectApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/salesProjectApi')>()),
  salesProjectApi: salesProjectApiMock,
}))

const reportPage = <T>(summary: T, items: unknown[], total = items.length) => ({
  summary,
  items,
  page: 1,
  pageSize: 10,
  total,
  totalPages: total > 0 ? 1 : 0,
})

const candidatePage = (items: unknown[]) => ({
  items,
  page: 1,
  pageSize: 20,
  total: items.length,
  totalPages: items.length > 0 ? 1 : 0,
})

const all033Permissions = [
  reportPermissions.overviewView,
  reportPermissions.operatingFinanceView,
  reportPermissions.projectProfitView,
  reportPermissions.contractCollectionView,
  reportPermissions.procurementVarianceView,
  reportPermissions.inventoryCapitalView,
  reportPermissions.receivablePayableView,
  reportPermissions.operatingAccountingView,
  reportPermissions.financialSummaryView,
]

async function mountReport(component: Component, path: string, permissions: string[] = all033Permissions) {
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
      { path: '/reports/project-profit', name: 'reports-project-profit', component },
      { path: '/reports/project-profit/:projectId', name: 'reports-project-profit-detail', component },
      { path: '/reports/contract-collection', name: 'reports-contract-collection', component },
      { path: '/reports/procurement-variance', name: 'reports-procurement-variance', component },
      { path: '/reports/inventory-capital', name: 'reports-inventory-capital', component },
      { path: '/reports/receivable-payable', name: 'reports-receivable-payable', component },
      { path: '/reports/operating-accounting-reconciliation', name: 'reports-operating-accounting', component },
      { path: '/reports/financial-summary', name: 'reports-financial-summary', component },
      { path: '/cost/project-costs/:projectId', name: 'cost-project-cost-detail', component: { render: () => null } },
      { path: '/finance/receipts/:id', name: 'finance-receipt-detail', component: { render: () => null } },
      { path: '/procurement/receipts/:id', name: 'procurement-receipt-detail', component: { render: () => null } },
      { path: '/inventory/movements', name: 'inventory-movements', component: { render: () => null } },
      { path: '/gl/vouchers/:id', name: 'gl-voucher-detail', component: { render: () => null } },
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

describe('033 项目利润与经营财务分析页面', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    businessReportingApiMock.overview.get.mockResolvedValue({
      period: { dateFrom: '2026-07-01', dateTo: '2026-07-31' },
      salesShipmentAmount: '1600.00',
      purchaseReceiptAmount: '800.00',
      inventoryInQuantity: '5.000',
      inventoryOutQuantity: '3.000',
      productionPlannedQuantity: '2.000',
      productionCompletedQuantity: '1.000',
      costAmount: '0.00',
      receivableBalance: '300.00',
      payableBalance: '250.00',
      receivedAmount: '900.00',
      paidAmount: '400.00',
      exceptionCount: 1,
      formalAccounting: false,
    })
    businessReportingApiMock.operatingFinanceOverview.get.mockResolvedValue({
      periodCode: '2026-07',
      analysisMode: 'LIVE',
      businessPeriodStatus: 'OPEN',
      accountingPeriodStatus: 'OPEN',
      financialCloseStatus: 'OPEN',
      finalityStatus: 'PREVIEW',
      freshnessStatus: 'CURRENT',
      projectProfitAmount: '1600.00',
      contractUnreceivedAmount: '300.00',
      procurementVarianceAmount: null,
      inventoryCapitalAmount: '12000.00',
      receivablePayableBalanceAmount: '500.00',
      accountingDifferenceAmount: null,
      restrictedReason: '缺少采购差异金额权限',
      sourceCount: 7,
    })
    businessReportingApiMock.projectProfit.list.mockResolvedValue(reportPage({
      projectCount: 1,
      shipmentRevenueAmount: '1600.00',
      projectCostAmount: '0.00',
      operatingGrossProfitAmount: '1600.00',
      differenceAmount: null,
      sourceCount: 1,
    }, [
      {
        projectId: 1,
        projectNo: 'PRJ-001',
        projectName: '示例项目',
        customerName: '示例客户',
        shipmentRevenueAmount: '1600.00',
        invoiceRevenueAmount: '1200.00',
        targetRevenueAmount: '2000.00',
        projectCostAmount: '0.00',
        operatingGrossProfitAmount: '1600.00',
        operatingGrossProfitRate: null,
        accountingRevenueAmount: null,
        accountingProfitAmount: null,
        differenceAmount: null,
        completenessStatus: 'INCOMPLETE',
        freshnessStatus: 'STALE',
        reconciliationStatus: 'UNAVAILABLE',
        finalityStatus: 'UNAVAILABLE',
        sourceCount: 1,
        traceKey: 'project-profit:PROJECT:1',
      },
    ]))
    businessReportingApiMock.projectProfit.detail.get.mockResolvedValue({
      projectId: 1,
      projectNo: 'PRJ-001',
      projectName: '示例项目',
      customerName: '示例客户',
      shipmentRevenueAmount: '1600.00',
      invoiceRevenueAmount: '1200.00',
      targetRevenueAmount: '2000.00',
      projectCostAmount: '0.00',
      operatingGrossProfitAmount: '1600.00',
      operatingGrossProfitRate: null,
      accountingRevenueAmount: null,
      accountingProfitAmount: null,
      differenceAmount: null,
      completenessStatus: 'INCOMPLETE',
      freshnessStatus: 'STALE',
      reconciliationStatus: 'UNAVAILABLE',
      finalityStatus: 'UNAVAILABLE',
      sourceCount: 1,
      traceKey: 'project-profit:PROJECT:1',
      costStageEntries: [{ stage: 'DELIVERED', amount: '0.00', status: 'INCOMPLETE' }],
      revenueEntries: [{ basis: 'SHIPMENT', amount: '1600.00', description: '发货经营收入' }],
      accountingEntries: [],
      varianceReasons: [{ reasonCode: 'NO_ACCOUNTING_FACT', description: '无会计事实', amount: null }],
    })
    businessReportingApiMock.projectProfit.traces.list.mockResolvedValue({
      items: [
        {
          sourceType: 'PROJECT_COST',
          sourceId: 1,
          sourceNo: 'PRJ-001',
          sourceLineId: null,
          businessDate: '2026-07-31',
          status: 'EFFECTIVE',
          quantity: null,
          amount: '1600.00',
          resourceRouteName: 'cost-project-cost-detail',
          resourceRouteParams: { projectId: 1 },
          resourceRouteQuery: null,
          canViewResource: true,
          restricted: false,
          restrictedMessage: null,
        },
        {
          sourceType: 'SALES_RECEIPT',
          sourceId: 2,
          sourceNo: 'RC-001',
          sourceLineId: null,
          businessDate: '2026-07-20',
          status: 'PARTIALLY_RECEIVED',
          quantity: null,
          amount: '900.00',
          resourceRouteName: 'finance-receipt-detail',
          resourceRouteParams: { id: 2 },
          resourceRouteQuery: null,
          canViewResource: true,
          restricted: false,
          restrictedMessage: null,
        },
        {
          sourceType: 'GL_LEDGER_ENTRY',
          sourceId: 3,
          sourceNo: 'GL-001',
          sourceLineId: null,
          businessDate: '2026-07-31',
          status: 'POSTED',
          quantity: null,
          amount: null,
          resourceRouteName: 'gl-voucher-detail',
          resourceRouteParams: { id: 3 },
          resourceRouteQuery: null,
          canViewResource: true,
          restricted: false,
          restrictedMessage: null,
        },
        {
          sourceType: 'RECEIPT',
          sourceId: 4,
          sourceNo: 'REC-001',
          sourceLineId: null,
          businessDate: '2026-07-21',
          status: 'RECEIVED',
          quantity: null,
          amount: '100.00',
          resourceRouteName: 'finance-receipt-detail',
          resourceRouteParams: { id: 4 },
          resourceRouteQuery: null,
          canViewResource: true,
          restricted: false,
          restrictedMessage: null,
        },
        {
          sourceType: 'PAYMENT',
          sourceId: 5,
          sourceNo: 'PAY-001',
          sourceLineId: null,
          businessDate: '2026-07-22',
          status: 'PARTIALLY_PAID',
          quantity: null,
          amount: '250.00',
          resourceRouteName: null,
          resourceRouteParams: null,
          resourceRouteQuery: null,
          canViewResource: true,
          restricted: false,
          restrictedMessage: null,
        },
        {
          sourceType: 'INVENTORY_BALANCE',
          sourceId: 6,
          sourceNo: 'INV-001',
          sourceLineId: null,
          businessDate: '2026-07-31',
          status: 'VALUED',
          quantity: '5.000',
          amount: '500.00',
          resourceRouteName: null,
          resourceRouteParams: null,
          resourceRouteQuery: null,
          canViewResource: true,
          restricted: false,
          restrictedMessage: null,
        },
        {
          sourceType: 'PROJECT_COST_CALCULATION',
          sourceId: 7,
          sourceNo: 'PCC-001',
          sourceLineId: null,
          businessDate: '2026-07-31',
          status: 'CALCULATED',
          quantity: null,
          amount: '600.00',
          resourceRouteName: null,
          resourceRouteParams: null,
          resourceRouteQuery: null,
          canViewResource: true,
          restricted: false,
          restrictedMessage: null,
        },
        {
          sourceType: 'FIN_CLOSE_RUN',
          sourceId: 8,
          sourceNo: 'FCR-001',
          sourceLineId: null,
          businessDate: '2026-07-31',
          status: 'CONFIRMED',
          quantity: null,
          amount: null,
          resourceRouteName: null,
          resourceRouteParams: null,
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
    businessReportingApiMock.contractCollection.list.mockResolvedValue(reportPage({
      contractAmount: '2000.00',
      receivedAmount: '900.00',
      unreceivedAmount: '300.00',
      overdueAmount: '300.00',
      advanceReceiptAmount: '100.00',
      sourceCount: 1,
    }, [
      {
        projectId: 1,
        projectNo: 'PRJ-001',
        contractId: 2,
        contractNo: 'CT-001',
        customerName: '示例客户',
        contractAmount: '2000.00',
        invoiceAmount: '1200.00',
        receivedAmount: '900.00',
        allocatedAmount: '900.00',
        unreceivedAmount: '300.00',
        advanceReceiptAmount: '100.00',
        overdueAmount: '300.00',
        collectionRate: '75.00%',
        status: 'OVERDUE',
        sourceCount: 3,
        traceKey: 'contract-collection:CONTRACT:2',
      },
    ]))
    businessReportingApiMock.procurementVariance.list.mockResolvedValue(reportPage({
      orderAmount: '800.00',
      receiptAmount: '700.00',
      invoiceAmount: '650.00',
      paidAmount: '400.00',
      matchVarianceAmount: '50.00',
      sourceCount: 1,
    }, [
      {
        sourceNo: 'PO-001',
        supplierName: '示例供应商',
        projectNo: 'PRJ-001',
        basis: 'PROJECT',
        orderAmount: '800.00',
        receiptAmount: '700.00',
        invoiceAmount: '650.00',
        paidAmount: '400.00',
        unreceivedOrderAmount: '100.00',
        receivedUninvoicedAmount: '50.00',
        invoiceReceiptDifferenceAmount: '-50.00',
        unpaidAmount: '250.00',
        matchVarianceAmount: '50.00',
        outsourcingUnsettledAmount: '0.00',
        reconciliationStatus: 'DIFFERENT',
        sourceCount: 2,
        traceKey: 'procurement-variance:PURCHASE_ORDER:3',
      },
    ]))
    businessReportingApiMock.inventoryCapital.list.mockResolvedValue(reportPage({
      quantity: '5.000',
      amount: null,
      snapshotAmount: '500.00',
      differenceAmount: null,
      riskQuantity: '5.000',
      sourceCount: 1,
    }, [
      {
        ownerType: 'PROJECT',
        projectNo: 'PRJ-001',
        warehouseName: '主仓',
        materialName: '示例物料',
        qualityStatus: 'QUALIFIED',
        freezeStatus: 'AVAILABLE',
        valuationStatus: 'UNVALUED',
        quantity: '5.000',
        amount: null,
        snapshotAmount: '500.00',
        differenceAmount: null,
        riskQuantity: '5.000',
        freshnessStatus: 'LEGACY_NOT_INCLUDED',
        sourceCount: 1,
        traceKey: 'inventory-capital:INVENTORY_BALANCE:1:31',
      },
    ]))
    businessReportingApiMock.receivablePayable.list.mockResolvedValue(reportPage({
      receivableAmount: '1200.00',
      balanceAmount: '300.00',
      overdueAmount: '300.00',
      sourceCount: 1,
    }, [
      {
        partyType: 'CUSTOMER',
        partyName: '示例客户',
        projectNo: 'PRJ-001',
        receivableAmount: '1200.00',
        payableAmount: '0.00',
        advanceReceiptAmount: '0.00',
        prepaymentAmount: '0.00',
        settledAmount: '900.00',
        balanceAmount: '300.00',
        notDueAmount: '0.00',
        aging1To30Amount: '300.00',
        aging31To60Amount: '0.00',
        aging61To90Amount: '0.00',
        agingOver90Amount: '0.00',
        overdueAmount: '300.00',
        sourceCount: 2,
        traceKey: 'receivable-payable:CUSTOMER:8',
      },
    ]))
    businessReportingApiMock.operatingAccountingReconciliation.list.mockResolvedValue(reportPage({
      operatingProfitAmount: '1600.00',
      accountingProfitAmount: null,
      publicUnallocatedAmount: null,
      differenceAmount: null,
      sourceCount: 1,
    }, [
      {
        projectId: 1,
        projectNo: 'PRJ-001',
        projectName: '示例项目',
        operatingRevenueAmount: '1600.00',
        operatingCostAmount: '0.00',
        operatingProfitAmount: '1600.00',
        accountingRevenueAmount: null,
        accountingCostAmount: null,
        accountingProfitAmount: null,
        publicUnallocatedAmount: null,
        differenceAmount: null,
        reconciliationStatus: 'UNAVAILABLE',
        finalityStatus: 'PREVIEW',
        varianceReason: '无会计事实',
        sourceCount: 1,
        traceKey: 'operating-accounting:PROJECT:1',
      },
    ]))
    businessReportingApiMock.financialSummary.get.mockResolvedValue({
      periodCode: '2026-07',
      finalityStatus: 'FINAL',
      businessPeriodStatus: 'CLOSED',
      accountingPeriodStatus: 'CLOSED',
      financialCloseStatus: 'CLOSED',
      revenueAmount: '1600.00',
      mainCostAmount: '0.00',
      periodExpenseAmount: '0.00',
      otherProfitLossAmount: '0.00',
      incomeTaxExpenseAmount: '0.00',
      operatingResultAmount: '1600.00',
      assetBalanceAmount: '12000.00',
      liabilityBalanceAmount: '300.00',
      equityBalanceAmount: '11700.00',
      trialBalanceStatus: 'MATCHED',
      bankReconciliationStatus: 'UNAVAILABLE',
      taxSummaryStatus: 'UNAVAILABLE',
      sourceCount: 5,
      traceKey: 'financial-summary:PERIOD:2026-07',
    })
    salesProjectApiMock.projects.list.mockResolvedValue(candidatePage([
      { id: 1, projectNo: 'PRJ-001', name: '示例项目', customerName: '示例客户', status: 'ACTIVE' },
    ]))
    salesProjectApiMock.projects.get.mockResolvedValue({
      id: 1,
      projectNo: 'PRJ-001',
      name: '示例项目',
      customerName: '示例客户',
      status: 'ACTIVE',
    })
    salesProjectApiMock.contracts.get.mockResolvedValue({
      id: 2,
      contractNo: 'CT-001',
      contractName: '主合同',
      status: 'EFFECTIVE',
    })
    salesProjectApiMock.listOrderLinkCandidates.mockResolvedValue(candidatePage([
      { projectId: 1, projectNo: 'PRJ-001', projectName: '示例项目', contractId: 2, contractNo: 'CT-001', contractName: '主合同' },
    ]))
    masterDataApiMock.customers.list.mockResolvedValue(candidatePage([{ id: 5, code: 'C001', name: '示例客户' }]))
    masterDataApiMock.suppliers.list.mockResolvedValue(candidatePage([{ id: 6, code: 'S001', name: '示例供应商' }]))
    masterDataApiMock.warehouses.list.mockResolvedValue(candidatePage([{ id: 7, code: 'WH01', name: '主仓' }]))
    masterDataApiMock.materials.list.mockResolvedValue(candidatePage([{ id: 8, code: 'M001', name: '示例物料' }]))
  })

  it('经营概览在原 /reports 入口内展示 033 分组、状态和权限过滤入口', async () => {
    const { wrapper } = await mountReport(ReportOverviewView, '/reports/overview', [
      reportPermissions.overviewView,
      reportPermissions.operatingFinanceView,
      reportPermissions.projectProfitView,
      reportPermissions.financialSummaryView,
    ])

    expect(wrapper.text()).toContain('项目利润与经营财务分析')
    expect(wrapper.text()).toContain('项目利润')
    expect(wrapper.text()).toContain('固定经营财务摘要')
    expect(wrapper.findAll('[data-test="fixed-report-entry"]').map((item) => item.text())).not.toContain('采购差异')
    expect(wrapper.text()).toContain('经营/会计定稿')
    expect(wrapper.text()).toContain('预览')
    expect(wrapper.text()).toContain('1600.00')
    expect(wrapper.find('[data-test="fixed-report-entry"]').attributes('href')).toContain('/reports/')
  })

  it('项目利润列表复用筛选、指标、宽表、分页、详情跳转和追溯 returnTo', async () => {
    const { wrapper, router } = await mountReport(ProjectProfitReportView, '/reports/project-profit')

    expect(wrapper.text()).toContain('项目利润')
    expect(wrapper.text()).toContain('发货经营收入')
    expect(wrapper.text()).toContain('经营毛利')
    expect(wrapper.text()).toContain('PRJ-001')
    expect(wrapper.text()).toContain('示例项目')
    expect(wrapper.text()).toContain('不完整')
    expect(wrapper.text()).toContain('已过期')
    expect(wrapper.text()).toContain('无会计事实')
    expect(wrapper.find('[data-test="report-table-scroll"]').exists()).toBe(true)

    await wrapper.find('input[name="report-period-code"]').setValue('2026-07')
    await wrapper.find('input[name="report-analysis-mode"]').setValue('BUSINESS_SNAPSHOT')
    await wrapper.find('input[name="report-completeness-status"]').setValue('INCOMPLETE')
    wrapper.findComponent(BusinessReferenceSelect).vm.$emit('update:modelValue', 1)
    await wrapper.find('[data-test="search-report"]').trigger('click')
    await flushPromises()
    expect(businessReportingApiMock.projectProfit.list).toHaveBeenLastCalledWith(expect.objectContaining({
      analysisMode: 'BUSINESS_SNAPSHOT',
      completenessStatus: 'INCOMPLETE',
      page: 1,
      periodCode: '2026-07',
      projectId: 1,
    }))

    await wrapper.find('[data-test="report-project-detail"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('reports-project-profit-detail')
    expect(router.currentRoute.value.query.periodCode).toBe('2026-07')
    expect(router.currentRoute.value.query.analysisMode).toBe('BUSINESS_SNAPSHOT')
    expect(router.currentRoute.value.query.returnTo).toContain('/reports/project-profit?')
    expect(router.currentRoute.value.query.returnTo).toContain('periodCode=2026-07')
    expect(router.currentRoute.value.query.returnTo).toContain('analysisMode=BUSINESS_SNAPSHOT')
  })

  it('项目利润详情集中用追溯抽屉展示来源并保留 returnTo', async () => {
    const { wrapper, router } = await mountReport(ProjectProfitDetailView, '/reports/project-profit/1?periodCode=2026-07&analysisMode=LIVE&returnTo=/reports/project-profit%3FperiodCode%3D2026-07')

    expect(wrapper.text()).toContain('项目利润详情')
    expect(wrapper.text()).toContain('发货经营收入')
    expect(wrapper.text()).toContain('成本阶段')
    expect(wrapper.text()).toContain('差异原因')
    expect(wrapper.text()).toContain('无会计事实')

    await wrapper.find('[data-test="open-report-trace"]').trigger('click')
    await flushPromises()
    expect(businessReportingApiMock.projectProfit.traces.list).toHaveBeenCalledWith(expect.objectContaining({
      projectId: '1',
      periodCode: '2026-07',
      analysisMode: 'LIVE',
      traceKey: 'project-profit:PROJECT:1',
    }))
    expect(wrapper.find('[data-test="report-trace-drawer"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('已生效')
    expect(wrapper.text()).toContain('部分收款')
    expect(wrapper.text()).toContain('已过账')
    expect(wrapper.text()).toContain('已收款')
    expect(wrapper.text()).toContain('部分付款')
    expect(wrapper.text()).toContain('已估值')
    expect(wrapper.text()).toContain('已确认')
    expect(wrapper.text()).toContain('已计算')
    expect(wrapper.text()).not.toContain('EFFECTIVE')
    expect(wrapper.text()).not.toContain('PARTIALLY_RECEIVED')
    expect(wrapper.text()).not.toContain('POSTED')
    expect(wrapper.text()).not.toContain('RECEIVED')
    expect(wrapper.text()).not.toContain('PARTIALLY_PAID')
    expect(wrapper.text()).not.toContain('VALUED')
    expect(wrapper.text()).not.toContain('CONFIRMED')
    expect(wrapper.text()).not.toContain('CALCULATED')
    await wrapper.find('[data-test="view-trace-source"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('cost-project-cost-detail')
    expect(router.currentRoute.value.query.returnTo).toBe('/reports/project-profit/1?periodCode=2026-07&analysisMode=LIVE&returnTo=/reports/project-profit%3FperiodCode%3D2026-07')
  })

  it('项目利润详情口径、阶段和原因编码显示中文标签而不是业务码', async () => {
    businessReportingApiMock.projectProfit.detail.get.mockResolvedValueOnce({
      projectId: 1,
      projectNo: 'PRJ-001',
      projectName: '编码项目',
      customerName: '示例客户',
      shipmentRevenueAmount: '1600.00',
      invoiceRevenueAmount: '1200.00',
      targetRevenueAmount: '2000.00',
      projectCostAmount: '0.00',
      operatingGrossProfitAmount: '1600.00',
      operatingGrossProfitRate: null,
      accountingRevenueAmount: null,
      accountingProfitAmount: null,
      differenceAmount: null,
      completenessStatus: 'COMPLETE',
      freshnessStatus: 'CURRENT',
      reconciliationStatus: 'DIFFERENT',
      finalityStatus: 'PREVIEW',
      sourceCount: 1,
      traceKey: 'project-profit:PROJECT:1',
      costStageEntries: [
        { stage: 'WIP', amount: '10.00', status: 'COMPLETE' },
        { stage: 'FINISHED', amount: '20.00', status: 'COMPLETE' },
        { stage: 'DELIVERED', amount: '30.00', status: 'COMPLETE' },
        { stage: 'DIRECT_PROJECT', amount: '40.00', status: 'COMPLETE' },
        { stage: 'TOTAL', amount: '100.00', status: 'COMPLETE' },
      ],
      revenueEntries: [
        { basis: 'SHIPMENT', amount: '1600.00', description: '发货经营收入' },
        { basis: 'INVOICE', amount: '1200.00', description: '开票收入' },
        { basis: 'TARGET', amount: '2000.00', description: '目标收入' },
      ],
      accountingEntries: [],
      varianceReasons: [
        { reasonCode: 'MATCHED', description: '经营与会计一致', amount: '0.00' },
        { reasonCode: 'DIFFERENT', description: '经营与会计存在差异', amount: '10.00' },
        { reasonCode: 'UNAVAILABLE', description: '不可用', amount: null },
        { reasonCode: 'RESTRICTED', description: '权限受限', amount: null },
        { reasonCode: 'NO_ACCOUNTING_FACT', description: '无会计事实', amount: null },
      ],
    })

    const { wrapper } = await mountReport(ProjectProfitDetailView, '/reports/project-profit/1')
    const text = wrapper.text()

    expect(text).toContain('在制')
    expect(text).toContain('完工')
    expect(text).toContain('已交付')
    expect(text).toContain('直接项目')
    expect(text).toContain('合计')
    expect(text).toContain('发货经营收入')
    expect(text).toContain('开票收入')
    expect(text).toContain('目标收入')
    expect(text).toContain('已匹配')
    expect(text).toContain('存在差异')
    expect(text).toContain('不可用')
    expect(text).toContain('受限')
    expect(text).toContain('无会计事实')
    ;[
      'WIP',
      'FINISHED',
      'DELIVERED',
      'DIRECT_PROJECT',
      'TOTAL',
      'SHIPMENT',
      'INVOICE',
      'TARGET',
      'MATCHED',
      'DIFFERENT',
      'UNAVAILABLE',
      'RESTRICTED',
      'NO_ACCOUNTING_FACT',
    ].forEach((rawCode) => {
      expect(text).not.toContain(rawCode)
    })
  })

  it('项目利润详情在空态和错误态也显示安全返回动作并过滤非法 returnTo', async () => {
    businessReportingApiMock.projectProfit.detail.get.mockRejectedValueOnce(new Error('详情加载失败'))
    const { wrapper, router } = await mountReport(ProjectProfitDetailView, '/reports/project-profit/1?returnTo=//evil.example/reports')

    expect(wrapper.text()).toContain('详情加载失败')
    expect(wrapper.find('[data-test="return-project-profit-list"]').exists()).toBe(true)
    await wrapper.find('[data-test="return-project-profit-list"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/reports/project-profit')
  })

  it('合同回款指标展示预收汇总而不是只展示明细预收', async () => {
    const { wrapper } = await mountReport(ContractCollectionReportView, '/reports/contract-collection')
    const metricText = wrapper.find('.report-metric-strip').text()

    expect(metricText).toContain('预收')
    expect(metricText).toContain('100.00')
  })

  it('合同回款状态 COLLECTED 和未知状态都有稳定中文展示', async () => {
    businessReportingApiMock.contractCollection.list.mockResolvedValueOnce(reportPage({
      contractAmount: '3000.00',
      receivedAmount: '3000.00',
      unreceivedAmount: '0.00',
      overdueAmount: '0.00',
      advanceReceiptAmount: '0.00',
      sourceCount: 2,
    }, [
      {
        projectId: 1,
        projectNo: 'PRJ-001',
        contractId: 2,
        contractNo: 'CT-001',
        customerName: '示例客户',
        contractAmount: '2000.00',
        invoiceAmount: '2000.00',
        receivedAmount: '2000.00',
        allocatedAmount: '2000.00',
        unreceivedAmount: '0.00',
        advanceReceiptAmount: '0.00',
        overdueAmount: '0.00',
        collectionRate: '100.00%',
        status: 'COLLECTED',
        sourceCount: 1,
        traceKey: 'contract-collection:CONTRACT:2',
      },
      {
        projectId: 2,
        projectNo: 'PRJ-002',
        contractId: 3,
        contractNo: 'CT-002',
        customerName: '示例客户',
        contractAmount: '1000.00',
        invoiceAmount: '500.00',
        receivedAmount: '500.00',
        allocatedAmount: '500.00',
        unreceivedAmount: '500.00',
        advanceReceiptAmount: '0.00',
        overdueAmount: '0.00',
        collectionRate: '50.00%',
        status: 'CONTRACT_NEEDS_REVIEW',
        sourceCount: 1,
        traceKey: 'contract-collection:CONTRACT:3',
      },
    ], 2))

    const { wrapper } = await mountReport(ContractCollectionReportView, '/reports/contract-collection')
    const text = wrapper.text()

    expect(text).toContain('已收齐')
    expect(text).toContain('未知状态')
    expect(text).not.toContain('COLLECTED')
    expect(text).not.toContain('CONTRACT_NEEDS_REVIEW')
  })

  it('库存质量 PENDING_INSPECTION、REJECTED 和往来对象类型不裸露后端枚举', async () => {
    businessReportingApiMock.inventoryCapital.list.mockResolvedValueOnce(reportPage({
      quantity: '1.000000',
      amount: '100.00',
      knownValuationAmount: '100.00',
      snapshotAmount: null,
      differenceAmount: null,
      unknownValuationQuantity: '0.000000',
      riskQuantity: '0.000000',
      completenessStatus: 'COMPLETE',
      sourceCount: 1,
    }, [
      {
        ownerType: 'PUBLIC',
        projectNo: null,
        warehouseName: '待检仓',
        materialName: '待检物料',
        qualityStatus: 'PENDING_INSPECTION',
        freezeStatus: 'AVAILABLE',
        valuationStatus: 'VALUED',
        quantity: '1.000000',
        amount: '100.00',
        snapshotAmount: null,
        differenceAmount: null,
        unknownValuationQuantity: '0.000000',
        riskQuantity: '0.000000',
        completenessStatus: 'COMPLETE',
        freshnessStatus: 'CURRENT',
        sourceCount: 1,
        traceKey: 'inventory-capital:INVENTORY_BALANCE:4:31',
      },
      {
        ownerType: 'PUBLIC',
        projectNo: null,
        warehouseName: '不合格仓',
        materialName: '不合格物料',
        qualityStatus: 'REJECTED',
        freezeStatus: 'AVAILABLE',
        valuationStatus: 'VALUED',
        quantity: '1.000000',
        amount: '100.00',
        snapshotAmount: null,
        differenceAmount: null,
        unknownValuationQuantity: '0.000000',
        riskQuantity: '0.000000',
        completenessStatus: 'COMPLETE',
        freshnessStatus: 'CURRENT',
        sourceCount: 1,
        traceKey: 'inventory-capital:INVENTORY_BALANCE:5:31',
      },
    ]))
    businessReportingApiMock.receivablePayable.list.mockResolvedValueOnce(reportPage({
      receivableAmount: '100.00',
      payableAmount: '80.00',
      advanceReceiptAmount: '0.00',
      prepaymentAmount: '0.00',
      balanceAmount: '20.00',
      overdueAmount: '0.00',
      sourceCount: 2,
    }, [
      {
        partyType: 'CUSTOMER',
        partyName: '示例客户',
        projectNo: 'PRJ-001',
        receivableAmount: '100.00',
        payableAmount: '0.00',
        advanceReceiptAmount: '0.00',
        prepaymentAmount: '0.00',
        settledAmount: '100.00',
        balanceAmount: '0.00',
        notDueAmount: '0.00',
        aging1To30Amount: '0.00',
        aging31To60Amount: '0.00',
        aging61To90Amount: '0.00',
        agingOver90Amount: '0.00',
        overdueAmount: '0.00',
        sourceCount: 1,
        traceKey: 'receivable-payable:CUSTOMER:8',
      },
      {
        partyType: 'SUPPLIER',
        partyName: '示例供应商',
        projectNo: 'PRJ-002',
        receivableAmount: '0.00',
        payableAmount: '80.00',
        advanceReceiptAmount: '0.00',
        prepaymentAmount: '0.00',
        settledAmount: '60.00',
        balanceAmount: '20.00',
        notDueAmount: '20.00',
        aging1To30Amount: '0.00',
        aging31To60Amount: '0.00',
        aging61To90Amount: '0.00',
        agingOver90Amount: '0.00',
        overdueAmount: '0.00',
        sourceCount: 1,
        traceKey: 'receivable-payable:SUPPLIER:9',
      },
    ], 2))

    const { wrapper: inventoryWrapper } = await mountReport(InventoryCapitalReportView, '/reports/inventory-capital')
    const { wrapper: receivablePayableWrapper } = await mountReport(ReceivablePayableReportView, '/reports/receivable-payable')
    const inventoryText = inventoryWrapper.text()
    const receivablePayableText = receivablePayableWrapper.text()

    expect(inventoryText).toContain('待检')
    expect(inventoryText).toContain('不合格')
    expect(inventoryText).toContain('已估值')
    expect(inventoryText).not.toContain('PENDING_INSPECTION')
    expect(inventoryText).not.toContain('REJECTED')
    expect(receivablePayableText).toContain('客户')
    expect(receivablePayableText).toContain('供应商')
    expect(receivablePayableText).not.toContain('CUSTOMER')
    expect(receivablePayableText).not.toContain('SUPPLIER')
  })

  it('经营概览把 033 状态与指标放在 014 指标之前的首屏扫描区', async () => {
    const { wrapper } = await mountReport(ReportOverviewView, '/reports/overview')
    const html = wrapper.html()

    expect(html.indexOf('经营/会计定稿')).toBeGreaterThanOrEqual(0)
    expect(html.indexOf('经营/会计定稿')).toBeLessThan(html.indexOf('销售出库经营金额'))
  })

  it('033 筛选复用既有候选组件和基础资料 API，不要求输入内部 ID', async () => {
    const { wrapper } = await mountReport(ContractCollectionReportView, '/reports/contract-collection')
    const referenceSelects = wrapper.findAllComponents(BusinessReferenceSelect)

    expect(referenceSelects.length).toBeGreaterThanOrEqual(3)
    expect(wrapper.text()).not.toContain('合同 ID')
    expect(wrapper.text()).not.toContain('项目 ID')
    expect(salesProjectApiMock.projects.list).toHaveBeenCalledWith(expect.objectContaining({ keyword: '', page: 1, pageSize: 20 }))
    expect(salesProjectApiMock.listOrderLinkCandidates).toHaveBeenCalledWith(expect.objectContaining({ keyword: '', page: 1, pageSize: 20 }))
    expect(masterDataApiMock.customers.list).toHaveBeenCalledWith(expect.objectContaining({ keyword: '', status: 'ENABLED', page: 1, pageSize: 20 }))
  })

  it('五个经营侧报表遇到旧快照缺失错误时显示旧快照状态且清空实时结果', async () => {
    const { wrapper } = await mountReport(ProjectProfitReportView, '/reports/project-profit')
    businessReportingApiMock.projectProfit.list.mockRejectedValueOnce(
      new AccountPermissionApiError('报表快照未包含', 'REPORT_SNAPSHOT_NOT_INCLUDED', 400),
    )

    await wrapper.find('input[name="report-analysis-mode"]').setValue('BUSINESS_SNAPSHOT')
    await wrapper.find('[data-test="search-report"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('旧快照未包含')
    expect(wrapper.text()).not.toContain('PRJ-001')
  })

  it('033 追溯来源类型有中文映射', () => {
    expect(reportSourceTypeText('PROJECT_COST_CALCULATION')).toBe('项目成本计算')
    expect(reportSourceTypeText('PROJECT_COST_SOURCE_LINE')).toBe('项目成本来源行')
    expect(reportSourceTypeText('GL_LEDGER_ENTRY')).toBe('总账分录')
    expect(reportSourceTypeText('FIN_CLOSE_RUN')).toBe('财务关闭')
    expect(reportSourceTypeText('BANK_RECONCILIATION_RUN')).toBe('银行对账')
    expect(reportSourceTypeText('TAX_PERIOD_SUMMARY')).toBe('税务期间汇总')
    expect(reportSourceTypeText('SALES_PROJECT_CONTRACT')).toBe('销售项目合同')
    expect(reportSourceTypeText('SALES_INVOICE')).toBe('销售发票')
    expect(reportSourceTypeText('RECEIVABLE')).toBe('应收')
    expect(reportSourceTypeText('RECEIPT')).toBe('收款')
    expect(reportSourceTypeText('ADVANCE_RECEIPT')).toBe('预收款')
    expect(reportSourceTypeText('PROCUREMENT_ORDER')).toBe('采购订单')
    expect(reportSourceTypeText('PURCHASE_RECEIPT')).toBe('采购入库')
    expect(reportSourceTypeText('PURCHASE_RETURN')).toBe('采购退货')
    expect(reportSourceTypeText('PURCHASE_INVOICE')).toBe('采购发票')
    expect(reportSourceTypeText('PAYABLE')).toBe('应付')
    expect(reportSourceTypeText('PAYMENT')).toBe('付款')
    expect(reportSourceTypeText('SETTLEMENT_ALLOCATION')).toBe('核销单')
    expect(reportSourceTypeText('OUTSOURCING_ORDER')).toBe('外协订单')
    expect(reportSourceTypeText('OUTSOURCING_RECEIPT')).toBe('外协收货')
    expect(reportSourceTypeText('INVENTORY_DOCUMENT')).toBe('库存单据')
    expect(reportSourceTypeText('INVENTORY_BALANCE')).toBe('库存余额')
    expect(reportSourceTypeText('INVENTORY_MOVEMENT')).toBe('库存流水')
    expect(reportSourceTypeText('PRODUCTION_WORK_REPORT')).toBe('生产报工')
    expect(reportSourceTypeText('PRODUCTION_COMPLETION_RECEIPT')).toBe('完工入库')
    expect(reportSourceTypeText('PREPAYMENT')).toBe('预付款')
  })

  it('033 列表行 traceKey 为空时隐藏追溯请求入口并展示来源受限', async () => {
    businessReportingApiMock.projectProfit.list.mockResolvedValueOnce(reportPage({
      projectCount: 1,
      shipmentRevenueAmount: null,
      projectCostAmount: null,
      operatingGrossProfitAmount: null,
      differenceAmount: null,
      sourceCount: 0,
    }, [
      {
        projectId: 1,
        projectNo: 'PRJ-001',
        projectName: '示例项目',
        customerName: '示例客户',
        shipmentRevenueAmount: null,
        invoiceRevenueAmount: null,
        targetRevenueAmount: null,
        projectCostAmount: null,
        operatingGrossProfitAmount: null,
        operatingGrossProfitRate: null,
        accountingRevenueAmount: null,
        accountingProfitAmount: null,
        differenceAmount: null,
        completenessStatus: 'RESTRICTED',
        freshnessStatus: 'CURRENT',
        reconciliationStatus: 'RESTRICTED',
        finalityStatus: 'UNAVAILABLE',
        sourceCount: 0,
        traceKey: null,
      },
    ]))

    const { wrapper } = await mountReport(ProjectProfitReportView, '/reports/project-profit')

    expect(wrapper.text()).toContain('来源受限/不可用')
    expect(wrapper.find('[data-test="open-report-trace"]').exists()).toBe(false)
    expect(businessReportingApiMock.projectProfit.traces.list).not.toHaveBeenCalled()
  })

  it('项目利润 BUSINESS_SNAPSHOT 详情按冻结口径加载且受限时不请求追溯', async () => {
    businessReportingApiMock.projectProfit.detail.get.mockResolvedValueOnce({
      projectId: 1,
      projectNo: 'PRJ-001',
      projectName: '冻结项目',
      customerName: '示例客户',
      shipmentRevenueAmount: '1600.00',
      invoiceRevenueAmount: '1200.00',
      targetRevenueAmount: '2000.00',
      projectCostAmount: '0.00',
      operatingGrossProfitAmount: '1600.00',
      operatingGrossProfitRate: null,
      accountingRevenueAmount: null,
      accountingProfitAmount: null,
      differenceAmount: null,
      completenessStatus: 'COMPLETE',
      freshnessStatus: 'FROZEN',
      reconciliationStatus: 'UNAVAILABLE',
      finalityStatus: 'FINAL',
      sourceCount: 0,
      traceKey: 'project-profit:PROJECT:1',
      restrictedReason: '缺少上游金额权限',
      costStageEntries: [],
      revenueEntries: [],
      accountingEntries: [],
      varianceReasons: [],
    })

    const { wrapper, router } = await mountReport(
      ProjectProfitDetailView,
      '/reports/project-profit/1?periodCode=2055-09&analysisMode=BUSINESS_SNAPSHOT&returnTo=/reports/project-profit%3FperiodCode%3D2055-09%26analysisMode%3DBUSINESS_SNAPSHOT',
    )

    expect(businessReportingApiMock.projectProfit.detail.get).toHaveBeenCalledWith('1', expect.objectContaining({
      periodCode: '2055-09',
      analysisMode: 'BUSINESS_SNAPSHOT',
    }))
    expect(wrapper.text()).toContain('冻结快照')
    expect(wrapper.text()).toContain('缺少上游金额权限')
    const traceButton = wrapper.find('[data-test="open-report-trace"]')
    expect(traceButton.attributes('disabled')).toBeDefined()
    await traceButton.trigger('click')
    await flushPromises()
    expect(businessReportingApiMock.projectProfit.traces.list).not.toHaveBeenCalled()
    await wrapper.find('[data-test="return-project-profit-list"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/reports/project-profit?periodCode=2055-09&analysisMode=BUSINESS_SNAPSHOT')
  })

  it('库存资金用已知估值金额、未知估值数量和不完整状态表达非全量资金', async () => {
    businessReportingApiMock.inventoryCapital.list.mockResolvedValueOnce(reportPage({
      quantity: '8.000000',
      amount: '1000.00',
      snapshotAmount: null,
      differenceAmount: null,
      riskQuantity: '4.000000',
      knownValuationAmount: '1000.00',
      unknownValuationQuantity: '4.000000',
      completenessStatus: 'INCOMPLETE',
      sourceCount: 2,
    }, [
      {
        ownerType: 'PROJECT',
        projectNo: 'PRJ-001',
        warehouseName: '主仓',
        materialName: '已估值物料',
        qualityStatus: 'QUALIFIED',
        freezeStatus: 'LOCKED',
        valuationStatus: 'VALUED',
        quantity: '4.000000',
        amount: '1000.00',
        snapshotAmount: null,
        differenceAmount: null,
        riskQuantity: '0.000000',
        unknownValuationQuantity: '0.000000',
        completenessStatus: 'COMPLETE',
        freshnessStatus: 'CURRENT',
        sourceCount: 1,
        traceKey: 'inventory-capital:INVENTORY_BALANCE:1:31',
      },
      {
        ownerType: 'PUBLIC',
        projectNo: null,
        warehouseName: '主仓',
        materialName: '未估值物料',
        qualityStatus: 'QUALIFIED',
        freezeStatus: 'AVAILABLE',
        valuationStatus: 'LEGACY_UNVALUED',
        quantity: '4.000000',
        amount: null,
        snapshotAmount: null,
        differenceAmount: null,
        riskQuantity: '4.000000',
        unknownValuationQuantity: '4.000000',
        completenessStatus: 'INCOMPLETE',
        freshnessStatus: 'STALE',
        sourceCount: 0,
        traceKey: null,
      },
    ]))

    const { wrapper } = await mountReport(InventoryCapitalReportView, '/reports/inventory-capital')

    expect(wrapper.text()).toContain('已知估值金额')
    expect(wrapper.text()).toContain('未知估值数量')
    expect(wrapper.text()).toContain('风险数量')
    expect(wrapper.text()).toContain('估值不完整')
    expect(wrapper.text()).toContain('冻结')
    expect(wrapper.text()).toContain('已估值')
    expect(wrapper.text()).toContain('历史未估值')
    expect(wrapper.text()).toContain('来源受限/不可用')
    expect(wrapper.text()).not.toContain('实时金额')
  })

  it('库存资金已估值但有风险时用已知估值字段且不把风险数量当未知估值数量', async () => {
    businessReportingApiMock.inventoryCapital.list.mockResolvedValueOnce(reportPage({
      quantity: '2.000000',
      amount: '9999.99',
      knownValuationAmount: '2000.00',
      snapshotAmount: null,
      differenceAmount: null,
      unknownValuationQuantity: '0.000000',
      riskQuantity: '2.000000',
      completenessStatus: 'COMPLETE',
      sourceCount: 1,
    }, [
      {
        ownerType: 'PROJECT',
        projectNo: 'PRJ-RISK',
        warehouseName: '风险仓',
        materialName: '已估值风险物料',
        qualityStatus: 'QUALIFIED',
        freezeStatus: 'LOCKED',
        valuationStatus: 'VALUED',
        quantity: '2.000000',
        amount: '2000.00',
        snapshotAmount: null,
        differenceAmount: null,
        unknownValuationQuantity: '0.000000',
        riskQuantity: '2.000000',
        completenessStatus: 'COMPLETE',
        freshnessStatus: 'CURRENT',
        sourceCount: 1,
        traceKey: 'inventory-capital:INVENTORY_BALANCE:2:31',
      },
    ]))

    const { wrapper } = await mountReport(InventoryCapitalReportView, '/reports/inventory-capital')
    const metricTexts = wrapper.findAll('.report-metric').map((metric) => metric.text())

    expect(metricTexts).toContain('已知估值金额2000.00')
    expect(metricTexts).not.toContain('已知估值金额9999.99')
    expect(metricTexts).toContain('未知估值数量0.000000')
    expect(metricTexts).toContain('风险数量2.000000')
    expect(metricTexts).toContain('估值完整性估值完整')
  })

  it('库存资金未知估值时展示后端未知估值数量和估值不完整状态', async () => {
    businessReportingApiMock.inventoryCapital.list.mockResolvedValueOnce(reportPage({
      quantity: '6.000000',
      amount: '800.00',
      knownValuationAmount: '800.00',
      snapshotAmount: null,
      differenceAmount: null,
      unknownValuationQuantity: '3.500000',
      riskQuantity: '0.250000',
      completenessStatus: 'INCOMPLETE',
      sourceCount: 2,
    }, [
      {
        ownerType: 'PUBLIC',
        projectNo: null,
        warehouseName: '未知估值仓',
        materialName: '未知估值物料',
        qualityStatus: 'QUALIFIED',
        freezeStatus: 'AVAILABLE',
        valuationStatus: 'UNVALUED',
        quantity: '3.500000',
        amount: null,
        snapshotAmount: null,
        differenceAmount: null,
        unknownValuationQuantity: '3.500000',
        riskQuantity: '0.250000',
        completenessStatus: 'INCOMPLETE',
        freshnessStatus: 'CURRENT',
        sourceCount: 0,
        traceKey: null,
      },
    ]))

    const { wrapper } = await mountReport(InventoryCapitalReportView, '/reports/inventory-capital')
    const metricTexts = wrapper.findAll('.report-metric').map((metric) => metric.text())

    expect(metricTexts).toContain('未知估值数量3.500000')
    expect(metricTexts).toContain('风险数量0.250000')
    expect(metricTexts).toContain('估值完整性估值不完整')
  })

  it('库存资金行 valuationStatus NON_VALUED 显示稳定中文且不裸露枚举', async () => {
    businessReportingApiMock.inventoryCapital.list.mockResolvedValueOnce(reportPage({
      quantity: '3.000000',
      amount: null,
      knownValuationAmount: null,
      snapshotAmount: null,
      differenceAmount: null,
      unknownValuationQuantity: '3.000000',
      riskQuantity: '3.000000',
      completenessStatus: 'INCOMPLETE',
      sourceCount: 1,
    }, [
      {
        ownerType: 'PUBLIC',
        projectNo: null,
        warehouseName: '主仓',
        materialName: '未估值现存量',
        qualityStatus: 'QUALIFIED',
        freezeStatus: 'AVAILABLE',
        valuationStatus: 'NON_VALUED',
        quantity: '3.000000',
        amount: null,
        snapshotAmount: null,
        differenceAmount: null,
        unknownValuationQuantity: '3.000000',
        riskQuantity: '3.000000',
        completenessStatus: 'INCOMPLETE',
        freshnessStatus: 'CURRENT',
        sourceCount: 1,
        traceKey: 'inventory-capital:INVENTORY_BALANCE:3:31',
      },
    ]))

    const { wrapper } = await mountReport(InventoryCapitalReportView, '/reports/inventory-capital')
    const text = wrapper.text()

    expect(text).toContain('估值不完整')
    expect(text).not.toContain('NON_VALUED')
  })

  it('固定经营财务摘要 traceKey 为空时禁用追溯且不发请求', async () => {
    businessReportingApiMock.financialSummary.get.mockResolvedValueOnce({
      periodCode: '2026-07',
      analysisMode: 'LIVE',
      finalityStatus: 'FINAL',
      businessPeriodStatus: 'CLOSED',
      accountingPeriodStatus: 'CLOSED',
      financialCloseStatus: 'CLOSED',
      revenueAmount: '1600.00',
      mainCostAmount: '0.00',
      periodExpenseAmount: '0.00',
      otherProfitLossAmount: '0.00',
      incomeTaxExpenseAmount: '0.00',
      operatingResultAmount: '1600.00',
      assetBalanceAmount: '12000.00',
      liabilityBalanceAmount: '300.00',
      equityBalanceAmount: '11700.00',
      trialBalanceStatus: 'MATCHED',
      bankReconciliationStatus: 'UNAVAILABLE',
      taxSummaryStatus: 'UNAVAILABLE',
      sourceCount: 0,
      traceKey: null,
      legalReport: false,
      disclaimer: '固定经营财务摘要不是资产负债表、利润表或现金流量表。',
    })
    const { wrapper } = await mountReport(FinancialSummaryReportView, '/reports/financial-summary')

    expect(wrapper.text()).toContain('来源受限/不可用')
    const traceButton = wrapper.find('[data-test="open-report-trace"]')
    expect(traceButton.attributes('disabled')).toBeDefined()
    await traceButton.trigger('click')
    await flushPromises()
    expect(businessReportingApiMock.financialSummary.traces.list).not.toHaveBeenCalled()
  })

  it('五个经营侧固定报表支持 BUSINESS_SNAPSHOT、状态文本和宽表容器', async () => {
    const cases: Array<[Component, string, string, string]> = [
      [ContractCollectionReportView, '/reports/contract-collection', '合同回款', '75.00%'],
      [ProcurementVarianceReportView, '/reports/procurement-variance', '采购差异', '三单匹配差异'],
      [InventoryCapitalReportView, '/reports/inventory-capital', '库存资金', '旧快照未包含'],
      [ReceivablePayableReportView, '/reports/receivable-payable', '往来账龄', '1-30天'],
    ]

    for (const [component, path, title, expectedText] of cases) {
      const { wrapper } = await mountReport(component, path)
      expect(wrapper.text()).toContain(title)
      expect(wrapper.text()).toContain(expectedText)
      expect(wrapper.find('[data-test="report-table-scroll"]').exists()).toBe(true)
      await wrapper.find('input[name="report-analysis-mode"]').setValue('BUSINESS_SNAPSHOT')
      await wrapper.find('[data-test="search-report"]').trigger('click')
      await flushPromises()
      expect(wrapper.text()).not.toContain('快照口径不支持')
    }
  })

  it('BUSINESS_SNAPSHOT 当前性 FROZEN 显示为中文冻结状态而不是原始枚举', async () => {
    businessReportingApiMock.inventoryCapital.list.mockResolvedValueOnce(reportPage({
      quantity: '5.000',
      amount: '500.00',
      snapshotAmount: '500.00',
      differenceAmount: '0.00',
      riskQuantity: '0.000',
      sourceCount: 1,
      analysisMode: 'BUSINESS_SNAPSHOT',
      freshnessStatus: 'FROZEN',
    }, [
      {
        ownerType: 'PROJECT',
        projectNo: 'PRJ-001',
        warehouseName: '主仓',
        materialName: '示例物料',
        qualityStatus: 'QUALIFIED',
        freezeStatus: 'AVAILABLE',
        valuationStatus: 'AVAILABLE',
        quantity: '5.000',
        amount: '500.00',
        snapshotAmount: '500.00',
        differenceAmount: '0.00',
        riskQuantity: '0.000',
        freshnessStatus: 'FROZEN',
        sourceCount: 1,
        traceKey: 'inventory-capital:INVENTORY_BALANCE:1:31',
      },
    ]))

    const { wrapper } = await mountReport(InventoryCapitalReportView, '/reports/inventory-capital')

    expect(wrapper.text()).toContain('冻结快照')
    expect(wrapper.text()).not.toContain('FROZEN')
  })

  it('经营会计对照请求 BUSINESS_SNAPSHOT 时显示不可用且不冒充实时结果', async () => {
    const { wrapper } = await mountReport(OperatingAccountingReconciliationReportView, '/reports/operating-accounting-reconciliation')
    businessReportingApiMock.operatingAccountingReconciliation.list.mockRejectedValueOnce(
      new AccountPermissionApiError('经营/会计对照不进入业务月结快照', 'REPORT_BASIS_INVALID', 409),
    )

    await wrapper.find('input[name="report-analysis-mode"]').setValue('BUSINESS_SNAPSHOT')
    await wrapper.find('[data-test="search-report"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('经营/会计对照')
    expect(wrapper.text()).toContain('经营/会计对照不进入业务月结快照')
    expect(wrapper.text()).toContain('快照口径不可用')
    expect(wrapper.text()).not.toContain('PRJ-001')
  })

  it('固定经营财务摘要标题固定且 BUSINESS_SNAPSHOT 显示不可用', async () => {
    const { wrapper } = await mountReport(FinancialSummaryReportView, '/reports/financial-summary')
    businessReportingApiMock.financialSummary.get.mockRejectedValueOnce(
      new AccountPermissionApiError('固定经营财务摘要不进入业务月结快照', 'REPORT_BASIS_INVALID', 409),
    )

    expect(wrapper.text()).toContain('固定经营财务摘要')
    expect(wrapper.text()).toContain('不是资产负债表、利润表或现金流量表')
    expect(wrapper.text()).toContain('1600.00')

    await wrapper.find('input[name="report-analysis-mode"]').setValue('BUSINESS_SNAPSHOT')
    await wrapper.find('[data-test="search-report"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('固定经营财务摘要不进入业务月结快照')
    expect(wrapper.text()).toContain('快照口径不可用')
    expect(wrapper.text()).toContain('不是资产负债表、利润表或现金流量表')
  })
})
